package net.phoenix_archives.phoenix_archive.api;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;

import java.util.List;

/**
 * Every direct reference to a Chronicles class lives in {@link ChroniclesQuestBridgeImpl} --
 * this class itself imports nothing from Chronicles, so it's safe to reference from anywhere in
 * Archive (screens included) without pulling Chronicles types into their imports. Every method
 * here gates on {@link #isLoaded()} before touching the impl, matching the Compat/CompatImpl
 * split Chronicles itself uses for its own optional integrations (see e.g. AE2Compat).
 */
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

    /**
     * Opens the given Chronicles quest id directly in the quest-tree canvas. Does nothing if
     * Chronicles isn't loaded or the id doesn't resolve to a real quest.
     */
    public static void openQuest(Screen returnTo, String questId) {
        if (isLoaded()) ChroniclesQuestBridgeImpl.openQuest(returnTo, questId);
    }

    /**
     * The display title of a Chronicles quest by id, or "" if Chronicles isn't loaded or the id
     * doesn't resolve.
     */
    public static String titleOf(String questId) {
        return isLoaded() ? ChroniclesQuestBridgeImpl.titleOf(questId) : "";
    }

    /**
     * Every currently-registered Chronicles quest as (id, title) pairs -- empty if Chronicles
     * isn't loaded.
     */
    public static List<QuestRef> allQuests() {
        return isLoaded() ? ChroniclesQuestBridgeImpl.allQuests() : List.of();
    }
}
