package net.phoenix_archives.phoenix_archive.api;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.phoenix_archives.phoenix_archive.api.ChroniclesQuestBridge.QuestRef;
import net.phoenixvine.chronicles.QuestAPI;
import net.phoenixvine.chronicles.client.screen.ChronicleOverviewScreen;
import net.phoenixvine.chronicles.common.model.QuestNode;
import net.phoenixvine.chronicles.common.registry.QuestTreeRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * Only ever called from {@link ChroniclesQuestBridge} behind an {@code isLoaded()} check --
 * package-private so nothing outside the bridge can reach a Chronicles type directly.
 */
final class ChroniclesQuestBridgeImpl {

    private ChroniclesQuestBridgeImpl() {}

    static boolean isCompleted(ServerPlayer player, String questId) {
        return QuestAPI.isCompleted(player, questId);
    }

    static boolean isCompleted(Player player, String questId) {
        return QuestAPI.isCompleted(player, questId);
    }

    static void openQuest(Screen returnTo, String questId) {
        ResourceLocation id = ResourceLocation.tryParse(questId);
        if (id == null) return;
        QuestNode node = QuestTreeRegistry.getQuest(id);
        if (node == null) return;

        ChronicleOverviewScreen screen = new ChronicleOverviewScreen(returnTo);
        Minecraft.getInstance().setScreen(screen);
        screen.navigateToNode(node);
    }

    static String titleOf(String questId) {
        try {
            for (QuestNode node : QuestTreeRegistry.getAllQuests().values()) {
                if (node.getId().toString().equals(questId)) return node.getTitle().getString();
            }
        } catch (Exception ignored) {}
        return "";
    }

    static List<QuestRef> allQuests() {
        List<QuestRef> out = new ArrayList<>();
        try {
            for (QuestNode node : QuestTreeRegistry.getAllQuests().values()) {
                out.add(new QuestRef(node.getId().toString(), node.getTitle().getString()));
            }
        } catch (Exception ignored) {}
        return out;
    }
}
