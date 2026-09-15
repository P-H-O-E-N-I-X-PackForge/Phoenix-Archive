package net.phoenix_archives.phoenix_archive;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import net.phoenix_archives.phoenix_archive.api.ExternalCondition;
import net.phoenix_archives.phoenix_archive.api.TriggerRegistry;
import net.phoenix_archives.phoenix_archive.common.LoreSavedData;
import net.phoenix_archives.phoenix_archive.network.PhoenixNetwork;
import net.phoenix_archives.phoenix_archive.network.SyncLorePacket;

public class ArchiveAPI {

    public static void unlockEntry(ServerPlayer player, String entryId) {
        
        LoreSavedData data = LoreSavedData.get(player.serverLevel());
        data.unlock(player.getUUID(), entryId);

        String hardwareKey = entryId.startsWith("unlocked_") ? entryId : "unlocked_" + entryId;

        PhoenixNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new SyncLorePacket(hardwareKey, true, true));

        player.getPersistentData().put("PhoenixArchive", data.getRawDataForPlayer(player.getUUID()));
    }

    public static void fireTrigger(ServerPlayer player, String type, Object value) {
        TriggerRegistry.fire(player, type, value);

    }

    public static void fireExternal(ServerPlayer player, String triggerId) {
        fireExternal(player, triggerId, 1);
    }

    public static void fireExternal(ServerPlayer player, String triggerId, int amount) {
        if (player == null) {
            PhoenixArchive.LOGGER.warn("[ArchiveAPI] fireExternal() called with a null player - ignored.");
            return;
        }
        if (triggerId == null || triggerId.isBlank()) {
            PhoenixArchive.LOGGER.warn(
                    "[ArchiveAPI] fireExternal() called with a null/blank triggerId for player {} - ignored. Pass " +
                            "the same id your \":::if external:<id>\" conditions (or entry conditions) use.",
                    player.getGameProfile().getName());
            return;
        }
        TriggerRegistry.fireExternalCount(player, triggerId, amount);
    }

    public static void setExternal(ServerPlayer player, String triggerId, int value) {
        if (player == null) {
            PhoenixArchive.LOGGER.warn("[ArchiveAPI] setExternal() called with a null player - ignored.");
            return;
        }
        if (triggerId == null || triggerId.isBlank()) {
            PhoenixArchive.LOGGER.warn(
                    "[ArchiveAPI] setExternal() called with a null/blank triggerId for player {} - ignored.",
                    player.getGameProfile().getName());
            return;
        }
        TriggerRegistry.setExternalCount(player, triggerId, value);
    }

    public static boolean isExternalTriggered(ServerPlayer player, String triggerId) {
        return getExternalCount(player, triggerId) >= 1;
    }

    public static int getExternalCount(ServerPlayer player, String triggerId) {
        if (player == null || triggerId == null || triggerId.isBlank()) return 0;
        LoreSavedData data = LoreSavedData.get(player.serverLevel());
        return data.getCount(player.getUUID(), "external_count:" + triggerId);
    }
}
