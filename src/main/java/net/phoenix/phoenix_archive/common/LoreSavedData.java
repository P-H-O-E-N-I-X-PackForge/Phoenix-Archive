package net.phoenix.phoenix_archive.common;

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

    // Player UUID -> The collection of all their unlocks (Dimensions, Biomes, Lore entries)
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
     * This is the "Truth Anchor."
     * No matter which dimension calls this, it forces the Overworld to be the storage location.
     */
    public static LoreSavedData get(ServerLevel level) {
        // We ALWAYS fetch the data from Level 0 (Overworld)
        ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
        return (overworld == null ? level : overworld).getDataStorage()
                .computeIfAbsent(LoreSavedData::load, LoreSavedData::new, DATA_NAME);
    }

    public void unlock(UUID playerUUID, String id) {
        CompoundTag tag = playerLoreMap.computeIfAbsent(playerUUID, k -> new CompoundTag());
        if (!tag.getBoolean(id)) {
            tag.putBoolean(id, true);
            this.setDirty();
        }
    }

    public boolean isUnlocked(UUID playerUUID, String id) {
        return playerLoreMap.containsKey(playerUUID) && playerLoreMap.get(playerUUID).getBoolean(id);
    }

    public CompoundTag getRawDataForPlayer(UUID playerUUID) {
        // Return a copy so the UI/Network can't accidentally modify the master file
        return playerLoreMap.getOrDefault(playerUUID, new CompoundTag()).copy();
    }
}