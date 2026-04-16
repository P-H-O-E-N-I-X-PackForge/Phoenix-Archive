package net.phoenixvine.phoenix_archive;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import net.phoenixvine.phoenix_archive.api.TriggerRegistry;
import net.phoenixvine.phoenix_archive.network.PhoenixNetwork;
import net.phoenixvine.phoenix_archive.network.SyncLorePacket;


import net.phoenixvine.phoenix_archive.common.LoreSavedData;

public class ArchiveAPI {

    /**
     * The primary method for PhoenixCore or DataItems to unlock an entry.
     * Use this for "One-Time" unlocks like using a disk or completing a quest.
     */
    public static void unlockEntry(ServerPlayer player, String entryId) {
        // 1. Update the Master Ledger (Persistent World Data)
        LoreSavedData data = LoreSavedData.get(player.serverLevel());
        data.unlock(player.getUUID(), entryId);

        // 2. Prepare the Key for the Client (matches your "unlocked_" prefix logic)
        String hardwareKey = entryId.startsWith("unlocked_") ? entryId : "unlocked_" + entryId;

        // 3. Send the Sync Packet with 'playSound' set to true
        PhoenixNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new SyncLorePacket(hardwareKey, true, true));

        // 4. Update the player's entity NBT (Used as a backup/cache)
        player.getPersistentData().put("PhoenixArchive", data.getRawDataForPlayer(player.getUUID()));
    }
    /**
     * Fire a generic trigger. Useful for PhoenixCore machines or custom logic.
     * Example: ArchiveAPI.fireTrigger(player, "voltage", new ResourceLocation("phoenix:hv"));
     */
    public static void fireTrigger(ServerPlayer player, String type, Object value) {
        TriggerRegistry.fire(player, type, value);
        // Note: syncAllLore is usually called inside the TriggerRegistry's logic
        // once a completion is detected.
    }
}