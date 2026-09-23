package net.phoenix_archives.phoenix_archive.api;

import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import dev.ftb.mods.ftbquests.api.FTBQuestsAPI;

public class QuestHelper {

    @OnlyIn(Dist.CLIENT)
    public static boolean isQuestCompleted(long id) {
        if (id == 0) return true;
        try {
            var file = FTBQuestsAPI.api().getQuestFile(true);
            if (file == null || Minecraft.getInstance().player == null) return false;

            var data = file.getOrCreateTeamData(Minecraft.getInstance().player);
            var quest = file.get(id);
            return quest != null && data.isCompleted(quest);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isQuestCompletedServer(ServerPlayer player, long id) {
        if (id == 0) return true;
        try {

            var file = FTBQuestsAPI.api().getQuestFile(false);
            if (file == null || player == null) return false;

            var data = file.getOrCreateTeamData(player);
            var quest = file.get(id);
            return quest != null && data.isCompleted(quest);
        } catch (Exception e) {
            return false;
        }
    }
}
