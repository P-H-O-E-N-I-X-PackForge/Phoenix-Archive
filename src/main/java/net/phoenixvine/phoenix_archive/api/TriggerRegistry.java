package net.phoenixvine.phoenix_archive.api;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.phoenixvine.phoenix_archive.common.LoreSavedData;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
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

    public static void checkForNewCompletions(ServerPlayer player, LoreSavedData data) {
        boolean anyChange = false;
        UUID uuid = player.getUUID();

        for (LoreEntry lore : LoreDataLoader.LORE_ENTRIES.values()) {
            String loreId = (lore.id() != null && !lore.id().isEmpty())
                    ? lore.id()
                    : lore.title().toLowerCase().replace(" ", "_");
            String loreUnlockKey = "lore_unlocked:" + loreId;

            boolean isPublicArchive = (lore.getConditions() == null || lore.getConditions().isEmpty())
                    && lore.questId() == 0;

            boolean alreadyUnlocked = data.isUnlocked(uuid, loreUnlockKey);

            if (!alreadyUnlocked) {
                // Not yet unlocked — check if it should be
                if (isPublicArchive || isEntryCompleteServer(player, data, lore)) {
                    data.unlock(uuid, loreUnlockKey);
                    anyChange = true;

                    // Only play sound/toast for gated entries being newly unlocked
                    if (!isPublicArchive) {
                        player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.MASTER, 0.5f, 1.5f);
                        player.displayClientMessage(
                                Component.literal("§6[PHOENIX_OS] §fARCHIVE_DECRYPTED: §b" + lore.title().toUpperCase()),
                                true
                        );
                    }
                }
            } else if (!isPublicArchive) {
                // FIX: Only relock if conditions are now UNMET (was inverted before — was relocking
                // entries whose conditions ARE met, causing infinite ding + toast loops)
                if (!isEntryCompleteServer(player, data, lore)) {
                    data.relock(uuid, loreUnlockKey);
                    anyChange = true;
                }
                // If conditions ARE still met, do nothing — no sound, no toast, no sync
            }
        }

        if (anyChange) {
            ServerEvents.syncAllLore(player);
        }
    }

    private static boolean isEntryCompleteServer(ServerPlayer player, LoreSavedData data, LoreEntry entry) {
        for (Map.Entry<String, String> condition : entry.getConditions().entrySet()) {
            if (condition.getValue() == null || condition.getValue().isEmpty()) continue;

            String requiredKey = condition.getKey() + ":" + condition.getValue();

            if (!data.isUnlocked(player.getUUID(), requiredKey)) {
                return false;
            }
        }

        if (entry.questId() != 0) {
            if (!QuestHelper.isQuestCompletedServer(player, entry.questId())) {
                return false;
            }
        }

        return true;
    }
}