package net.phoenix_archives.phoenix_archive.api;

import net.minecraft.server.level.ServerPlayer;

import dev.ftb.mods.ftbquests.api.FTBQuestsAPI;

public class QuestHelperServer {

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
