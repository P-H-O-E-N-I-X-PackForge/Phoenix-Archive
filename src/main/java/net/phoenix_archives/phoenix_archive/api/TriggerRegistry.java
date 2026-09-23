package net.phoenix_archives.phoenix_archive.api;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.phoenix_archives.phoenix_archive.common.LoreSavedData;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class TriggerRegistry {

    public static void fire(ServerPlayer player, String conditionType, @Nullable Object context) {
        if (context == null || player == null) return;

        String contextStr = (context instanceof ResourceLocation rl) ? rl.toString() : String.valueOf(context);
        LoreSavedData data = LoreSavedData.get(player.serverLevel());

        String signalKey = conditionType + ":" + contextStr;

        if (!data.isUnlocked(player.getUUID(), signalKey)) {
            data.unlock(player.getUUID(), signalKey);
            checkForNewCompletions(player, data);
        }
    }

    public static void fireItem(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) return;
        ResourceLocation itemKey = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (itemKey != null) {
            fire(player, "item", itemKey);
        }
    }

    public static void fireWearing(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) return;
        ResourceLocation itemKey = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (itemKey != null) {
            fire(player, "wearing", itemKey);
        }
    }

    public static void hardwareHandshake(ServerPlayer player) {
        fire(player, "dimension", player.level().dimension().location());

        player.level().getBiome(player.blockPosition()).unwrapKey().ifPresent(key -> {
            fire(player, "biome", key.location());
        });

        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty()) fireItem(player, stack);
        }

        player.getArmorSlots().forEach(stack -> {
            if (!stack.isEmpty()) fireWearing(player, stack);
        });
    }

    public static boolean checkForNewCompletions(ServerPlayer player, LoreSavedData data) {
        boolean anyChange = syncQuestLeafSignals(player, data);
        UUID uuid = player.getUUID();

        for (LoreEntry lore : LoreDataLoader.LORE_ENTRIES.values()) {
            String loreId = (lore.id() != null && !lore.id().isEmpty()) ? lore.id() :
                    lore.title().toLowerCase().replace(" ", "_");
            String loreUnlockKey = "lore_unlocked:" + loreId;

            boolean isPublicArchive = lore.conditionTree().isEmpty() && lore.questId() == 0;

            boolean alreadyUnlocked = data.isUnlocked(uuid, loreUnlockKey);

            if (!alreadyUnlocked) {

                if (isPublicArchive || isEntryCompleteServer(player, data, lore)) {
                    data.unlock(uuid, loreUnlockKey);
                    anyChange = true;

                    if (!isPublicArchive) {
                        player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.MASTER, 0.5f, 1.5f);
                        player.displayClientMessage(
                                Component
                                        .literal("§6[PHOENIX_OS] §fARCHIVE_DECRYPTED: §b" + lore.title().toUpperCase()),
                                true);
                    }
                }
            } else if (!isPublicArchive) {

                if (!isEntryCompleteServer(player, data, lore)) {
                    data.relock(uuid, loreUnlockKey);
                    anyChange = true;
                }

            }
        }

        if (anyChange) {
            ServerEvents.syncAllLore(player);
        }

        return anyChange;
    }

    public static void fireExternalCount(ServerPlayer player, String triggerId, int amount) {
        if (player == null || triggerId == null || triggerId.isBlank() || amount == 0) return;
        LoreSavedData data = LoreSavedData.get(player.serverLevel());
        data.incrementCount(player.getUUID(), "external_count:" + triggerId, amount);

        if (!checkForNewCompletions(player, data)) {
            ServerEvents.syncAllLore(player);
        }
    }

    public static void setExternalCount(ServerPlayer player, String triggerId, int value) {
        if (player == null || triggerId == null || triggerId.isBlank()) return;
        LoreSavedData data = LoreSavedData.get(player.serverLevel());
        data.setCount(player.getUUID(), "external_count:" + triggerId, value);
        if (!checkForNewCompletions(player, data)) {
            ServerEvents.syncAllLore(player);
        }
    }

    private static boolean syncQuestLeafSignals(ServerPlayer player, LoreSavedData data) {
        boolean anyChange = false;
        for (LoreEntry lore : LoreDataLoader.LORE_ENTRIES.values()) {
            for (ConditionNode.NegatableLeaf nl : lore.conditionTree().collectLeaves()) {
                String type = nl.leaf().type();
                String value = nl.leaf().value();
                if (value == null || value.isEmpty()) continue;
                if (!type.equals("chronicles_quest") && !type.equals("ftb_quest")) continue;

                String signalKey = type + ":" + value;
                if (data.isUnlocked(player.getUUID(), signalKey)) continue;

                boolean completedNow = type.equals("chronicles_quest") ?
                        (ChroniclesQuestBridge.isLoaded() && ChroniclesQuestBridge.isCompleted(player, value)) :
                        (isFtbQuestId(value) && QuestHelper.isQuestCompletedServer(player, Long.parseLong(value)));

                if (completedNow) {
                    data.unlock(player.getUUID(), signalKey);
                    anyChange = true;
                }
            }
        }
        return anyChange;
    }

    private static boolean isEntryCompleteServer(ServerPlayer player, LoreSavedData data, LoreEntry entry) {
        boolean logicMet = ConditionEvaluator.evaluate(entry.conditionTree(), (type, value) -> {
            if (type.equals("chronicles_quest")) {
                return ChroniclesQuestBridge.isLoaded() && ChroniclesQuestBridge.isCompleted(player, value);
            }
            if (type.equals("ftb_quest")) {
                return isFtbQuestId(value) && QuestHelper.isQuestCompletedServer(player, Long.parseLong(value));
            }
            if (type.equals("external")) {
                ExternalCondition.Parsed p = ExternalCondition.parse(value);
                int count = data.getCount(player.getUUID(), "external_count:" + p.id());
                return ExternalCondition.test(count, p.op(), p.threshold());
            }
            return data.isUnlocked(player.getUUID(), type + ":" + value);
        });
        if (!logicMet) return false;

        if (entry.questId() != 0) {
            if (!QuestHelper.isQuestCompletedServer(player, entry.questId())) {
                return false;
            }
        }

        return true;
    }

    private static boolean isFtbQuestId(String value) {
        try {
            Long.parseLong(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
