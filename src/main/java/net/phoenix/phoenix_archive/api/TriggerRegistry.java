package net.phoenix.phoenix_archive.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import net.phoenix.phoenix_archive.common.LoreSavedData;
import net.phoenix.phoenix_archive.network.PhoenixNetwork;
import net.phoenix.phoenix_archive.network.SyncLorePacket;
import org.jetbrains.annotations.Nullable;

import static net.phoenix.phoenix_archive.api.ServerEvents.syncAllLore;

public class TriggerRegistry {
    public static void fire(ServerPlayer player, String type, @Nullable Object context) {
        if (context == null || player == null) return;
        String contextStr = (context instanceof ResourceLocation rl) ? rl.toString() : String.valueOf(context);
        if (contextStr.equals("null")) return;

        for (LoreEntry lore : LoreDataLoader.LORE_ENTRIES.values()) {
            String requiredId = switch (type) {
                case "gt_machine", "phoenix:multiblock_formed" -> lore.getMachineId();
                case "dimension" -> lore.getDimensionId();
                case "biome" -> lore.getBiomeId();
                case "entity" -> lore.getEntityId();
                default -> "";
            };

            if (!requiredId.isEmpty() && requiredId.equals(contextStr)) {
                unlockHardware(player, requiredId);
            }
        }
    }
    private static void unlockHardware(ServerPlayer player, String hardwareId) {
        LoreSavedData data = LoreSavedData.get(player.serverLevel());

        if (data.isUnlocked(player.getUUID(), hardwareId)) return;

        // 1. Update the Absolute Source of Truth (The Ledger)
        data.unlock(player.getUUID(), hardwareId);

        // 2. We skip individual SyncLorePackets here because checkForNewCompletions
        // will trigger syncAllLore(player) if any actual archive entry unlocks.
        // If you want a toast for the hardware itself, keep the packet,
        // but the Archive Screen needs the Bulk Sync.

        // 3. Audio feedback for the requirement unlock
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.8f, 1.5f);

        // 4. Check for completions.
        // If a lore entry is completed, this calls syncAllLore() which pushes the Truth to the Client.
        checkForNewCompletions(player, data);
    }

    public static void checkForNewCompletions(ServerPlayer player, LoreSavedData data) {
        boolean newlyUnlocked = false;
        for (LoreEntry entry : LoreDataLoader.LORE_ENTRIES.values()) {
            String entryKey = "unlocked_" + entry.title().toLowerCase().replaceAll("[^a-z0-9]", "_");

            if (!data.isUnlocked(player.getUUID(), entryKey)) {
                if (isEntryCompleteServer(player, data, entry)) {
                    data.unlock(player.getUUID(), entryKey);
                    newlyUnlocked = true;

                    // We don't send individual packets here anymore.
                    // We let syncAllLore handle the batch update and the Toast comparison logic.
                    player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.MASTER, 0.5f, 1.5f);
                }
            }
        }

        // CRITICAL: If anything changed, or even just to be safe,
        // push the Ledger data to the player's persistent NBT and Client cache.
        if (newlyUnlocked) {
            ServerEvents.syncAllLore(player);
        }
    }

    private static boolean isEntryCompleteServer(ServerPlayer player, LoreSavedData data, LoreEntry entry) {
        // 1. DIMENSION CHECK: Does the entry require a dim? If so, has the player VISITED it?
        if (!entry.getDimensionId().isEmpty()) {
            if (!data.isUnlocked(player.getUUID(), entry.getDimensionId())) return false;
        }

        // 2. BIOME CHECK: Does the entry require a biome? If so, has the player SCANNED it?
        if (!entry.getBiomeId().isEmpty()) {
            if (!data.isUnlocked(player.getUUID(), entry.getBiomeId())) return false;
        }

        // 3. MACHINE CHECK
        if (!entry.getMachineId().isEmpty()) {
            if (!data.isUnlocked(player.getUUID(), entry.getMachineId())) return false;
        }

        // 4. QUEST CHECK
        if (entry.questId() != 0 && !QuestHelper.isQuestCompletedServer(player, entry.questId())) return false;

        return true; // All requirements found in the Overworld Master File!
    }
}