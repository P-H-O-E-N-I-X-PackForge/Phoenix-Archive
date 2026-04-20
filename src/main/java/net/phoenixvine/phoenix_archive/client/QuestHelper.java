package net.phoenixvine.phoenix_archive.client;

import dev.ftb.mods.ftbquests.api.FTBQuestsAPI;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class QuestHelper {

    /**
     * CLIENT SIDE ONLY
     * Used for rendering the Archive Screen.
     */
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
}
