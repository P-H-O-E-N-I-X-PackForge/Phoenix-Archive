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

    /**
     * The primary entry point. Call this when an action happens in the world.
     * Example: fire(player, "suit_event", "first_rebirth")
     */
    public static void fire(ServerPlayer player, String conditionType, @Nullable Object context) {
        if (context == null || player == null) return;

        String contextStr = (context instanceof ResourceLocation rl) ? rl.toString() : String.valueOf(context);
        LoreSavedData data = LoreSavedData.get(player.serverLevel());

        // 1. Record the "Hardware/Requirement" unlock in the SavedData
        // This is the specific signal (e.g., machine:electric_furnace)
        String signalKey = conditionType + ":" + contextStr;

        if (!data.isUnlocked(player.getUUID(), signalKey)) {
            data.unlock(player.getUUID(), signalKey);

            // Audio feedback that a requirement was met
       //     player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
        //            SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.8f, 1.5f);

            // 2. Check if this newly met requirement completes any Lore Entries
            checkForNewCompletions(player, data);
        }
    }

    public static void hardwareHandshake(ServerPlayer player) {
        // Current Environment
        fire(player, "dimension", player.level().dimension().location());
        player.level().getBiome(player.blockPosition()).unwrapKey().ifPresent(key -> {
            fire(player, "biome", key.location());
        });

        // CHECK INVENTORY (For "Have Item" requirements)
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty()) {
                fire(player, "item", stack.getItem().toString());
            }
        }

        // CHECK EQUIPMENT (For "Wearing Armor" requirements)
        player.getArmorSlots().forEach(stack -> {
            if (!stack.isEmpty()) {
                fire(player, "wearing", stack.getItem().toString());
            }
        });
    }

    public static void checkForNewCompletions(ServerPlayer player, LoreSavedData data) {
        boolean newlyUnlocked = false;
        UUID uuid = player.getUUID();

        for (LoreEntry lore : LoreDataLoader.LORE_ENTRIES.values()) {
            String loreId = (lore.id() != null && !lore.id().isEmpty()) ? lore.id() : lore.title().toLowerCase().replace(" ", "_");
            String loreUnlockKey = "lore_unlocked:" + loreId;

            if (!data.isUnlocked(uuid, loreUnlockKey)) {
                // Check if it's "Free" (No conditions AND no Quest)
                boolean isPublicArchive = (lore.getConditions() == null || lore.getConditions().isEmpty()) && lore.questId() == 0;

                if (isPublicArchive || isEntryCompleteServer(player, data, lore)) {
                    data.unlock(uuid, loreUnlockKey);
                    newlyUnlocked = true;

                    // Only show the toast/sound if it WASN'T a public archive
                    // We don't want to spam the player with 10 toasts the first time they join
                    if (!isPublicArchive) {
                        player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.MASTER, 0.5f, 1.5f);
                        player.displayClientMessage(
                                Component.literal("§6[PHOENIX_OS] §fARCHIVE_DECRYPTED: §b" + lore.title().toUpperCase()),
                                true
                        );
                    }
                }
            }
        }

        if (newlyUnlocked) {
            ServerEvents.syncAllLore(player);
        }
    }

    /**
     * Logic: An entry is complete ONLY IF every condition in its Map
     * is found in the Player's LoreSavedData.
     */
    private static boolean isEntryCompleteServer(ServerPlayer player, LoreSavedData data, LoreEntry entry) {
        // 1. Check Dynamic Conditions Map
        for (Map.Entry<String, String> condition : entry.getConditions().entrySet()) {
            String requiredKey = condition.getKey() + ":" + condition.getValue();

            // If even ONE requirement is missing from the saved data, the lore is locked
            if (!data.isUnlocked(player.getUUID(), requiredKey)) {
                return false;
            }
        }

        // 2. Check Quest (Still a separate field in your LoreEntry record)
        if (entry.questId() != 0) {
            if (!QuestHelper.isQuestCompletedServer(player, entry.questId())) {
                return false;
            }
        }

        return true;
    }
}