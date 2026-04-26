package net.phoenixvine.phoenix_archive.common;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LoreSavedData extends SavedData {

    private static final String DATA_NAME = "phoenix_archive_master_ledger";

    private final Map<UUID, CompoundTag> playerLoreMap = new HashMap<>();

    public LoreSavedData() {}

    public static LoreSavedData load(CompoundTag nbt) {
        LoreSavedData data = new LoreSavedData();
        CompoundTag list = nbt.getCompound("MasterLedger");
        for (String uuidStr : list.getAllKeys()) {
            data.playerLoreMap.put(UUID.fromString(uuidStr), list.getCompound(uuidStr));
        }
        return data;
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag nbt) {
        CompoundTag list = new CompoundTag();
        playerLoreMap.forEach((uuid, tag) -> list.put(uuid.toString(), tag));
        nbt.put("MasterLedger", list);
        return nbt;
    }

    /**
     * Always fetches from the Overworld so data is consistent across dimensions.
     */
    public static LoreSavedData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
        return (overworld == null ? level : overworld).getDataStorage()
                .computeIfAbsent(LoreSavedData::load, LoreSavedData::new, DATA_NAME);
    }

    public boolean isUnlocked(UUID playerUUID, String id) {
        CompoundTag playerData = playerLoreMap.get(playerUUID);
        if (playerData == null) return false;
        return playerData.getBoolean(id);
    }

    public void unlock(UUID playerUUID, String id) {
        CompoundTag tag = playerLoreMap.computeIfAbsent(playerUUID, k -> new CompoundTag());
        if (!tag.getBoolean(id)) {
            tag.putBoolean(id, true);
            this.setDirty();
        }
    }

    /**
     * FIX #8: Remove an unlock key so a modified entry can be re-locked.
     * Called by TriggerRegistry when a previously-unlocked entry no longer meets its conditions.
     */
    public void relock(UUID playerUUID, String id) {
        CompoundTag tag = playerLoreMap.get(playerUUID);
        if (tag != null && tag.contains(id)) {
            tag.remove(id);
            this.setDirty();
        }
    }

    public CompoundTag getRawDataForPlayer(UUID playerUUID) {
        return playerLoreMap.getOrDefault(playerUUID, new CompoundTag()).copy();
    }
}
