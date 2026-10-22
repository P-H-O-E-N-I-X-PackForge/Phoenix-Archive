package net.phoenix_archives.phoenix_archive.api;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;

import java.util.List;

public final class ChroniclesQuestBridge {

    private ChroniclesQuestBridge() {}

    private static final String MOD_ID = "phoenix_chronicles";

    public record QuestRef(String id, String title) {}

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    public static boolean isCompleted(ServerPlayer player, String questId) {
        return isLoaded() && ChroniclesQuestBridgeImpl.isCompleted(player, questId);
    }

    public static boolean isCompleted(Player player, String questId) {
        return isLoaded() && ChroniclesQuestBridgeImpl.isCompleted(player, questId);
    }

    public static void openQuest(Screen returnTo, String questId) {
        if (isLoaded()) ChroniclesQuestBridgeImpl.openQuest(returnTo, questId);
    }

    public static String titleOf(String questId) {
        return isLoaded() ? ChroniclesQuestBridgeImpl.titleOf(questId) : "";
    }

    public static List<QuestRef> allQuests() {
        return isLoaded() ? ChroniclesQuestBridgeImpl.allQuests() : List.of();
    }
}
