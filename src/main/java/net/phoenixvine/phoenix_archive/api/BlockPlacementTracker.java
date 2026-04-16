package net.phoenixvine.phoenix_archive.api;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;

public class BlockPlacementTracker {

    private static final Map<ResourceKey<Level>, Map<BlockPos, Pair<UUID, Long>>> recentPlacements
            = new HashMap<>();

    private static final long EXPIRY_MS = 5 * 60 * 1000; // 5 minutes

    public static void track(ResourceKey<Level> dim, BlockPos pos, UUID playerUUID) {
        recentPlacements
                .computeIfAbsent(dim, k -> new HashMap<>())
                .put(pos, Pair.of(playerUUID, System.currentTimeMillis()));
    }

    public static Optional<UUID> findPlacer(ResourceKey<Level> dim,
                                            Collection<BlockPos> positions) {
        var dimMap = recentPlacements.get(dim);
        if (dimMap == null) return Optional.empty();

        long now = System.currentTimeMillis();
        UUID bestPlayer = null;
        long bestTime = 0;

        for (BlockPos pos : positions) {
            var entry = dimMap.get(pos);
            if (entry == null) continue;
            if (now - entry.getRight() > EXPIRY_MS) continue;
            if (entry.getRight() > bestTime) {
                bestTime = entry.getRight();
                bestPlayer = entry.getLeft();
            }
        }

        return Optional.ofNullable(bestPlayer);
    }

    public static void cleanup() {
        long now = System.currentTimeMillis();
        recentPlacements.values().forEach(map ->
                map.entrySet().removeIf(e -> now - e.getValue().getRight() > EXPIRY_MS));
        recentPlacements.entrySet().removeIf(e -> e.getValue().isEmpty());
    }
}