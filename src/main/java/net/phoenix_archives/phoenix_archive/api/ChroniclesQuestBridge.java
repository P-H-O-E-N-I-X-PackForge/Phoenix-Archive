package net.phoenix_archives.phoenix_archive.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;
import net.phoenixvine.chronicles.QuestAPI;

public final class ChroniclesQuestBridge {

    private ChroniclesQuestBridge() {}

    public static boolean isLoaded() {
        return ModList.get().isLoaded("phoenix_chronicles");
    }

    public static boolean isCompleted(ServerPlayer player, String questId) {
        return QuestAPI.isCompleted(player, questId);
    }

    public static boolean isCompleted(Player player, String questId) {
        return QuestAPI.isCompleted(player, questId);
    }
}
