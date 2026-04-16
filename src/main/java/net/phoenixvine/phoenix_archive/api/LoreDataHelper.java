package net.phoenixvine.phoenix_archive.api;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

public class LoreDataHelper {
    private static final String TAG_NAME = "PhoenixArchive";

    public static void saveUnlock(ServerPlayer player, String entryId) {
        CompoundTag forgeData = player.getPersistentData();
        CompoundTag phoenixData = forgeData.getCompound(TAG_NAME);

        // Store as "unlocked_entryname" to match your BulkSync check
        String key = "unlocked_" + entryId;

        if (!phoenixData.getBoolean(key)) {
            phoenixData.putBoolean(key, true);
            forgeData.put(TAG_NAME, phoenixData);
        }
    }

    public static CompoundTag getAllData(ServerPlayer player) {
        return player.getPersistentData().getCompound(TAG_NAME);
    }
}