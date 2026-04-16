package net.phoenixvine.phoenix_archive.api;

import dev.ftb.mods.ftbquests.api.FTBQuestsAPI;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

public class QuestHelper {

    /**
     * CLIENT SIDE ONLY
     * Used for rendering the Archive Screen.
     */
    @OnlyIn(Dist.CLIENT)
    public static boolean isQuestCompleted(long id) {
        if (id == 0) return true;
        try {
            var file = FTBQuestsAPI.api().getQuestFile(true); // true = client file
            if (file == null || Minecraft.getInstance().player == null) return false;

            var data = file.getOrCreateTeamData(Minecraft.getInstance().player);
            var quest = file.get(id);
            return quest != null && data.isCompleted(quest);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * SERVER SIDE ONLY
     * Used by TriggerRegistry to check for Toasts/Unlocks.
     */
    public static boolean isQuestCompletedServer(ServerPlayer player, long id) {
        if (id == 0) return true;
        try {
            // FTB Quests API handles the side check internally here
            var file = FTBQuestsAPI.api().getQuestFile(false); // false = server file
            if (file == null || player == null) return false;

            var data = file.getOrCreateTeamData(player);
            var quest = file.get(id);
            return quest != null && data.isCompleted(quest);
        } catch (Exception e) {
            return false;
        }
    }
}