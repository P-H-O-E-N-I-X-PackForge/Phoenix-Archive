// net.phoenixvine.phoenix_archive.client.ClientHelper.java
package net.phoenixvine.phoenix_archive.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

@OnlyIn(Dist.CLIENT)
public class ClientHelper {

    public static void openArchiveScreen() {
        Minecraft.getInstance().setScreen(new ArchiveScreen());
    }

    // In ClientHelper.java
    @OnlyIn(Dist.CLIENT)
    public static String getPlayerName() {
        var player = Minecraft.getInstance().player;
        return player != null ? player.getScoreboardName().toUpperCase() : "USER";
    }

    // In ClientHelper.java
    @OnlyIn(Dist.CLIENT)
    public static void appendPlayerTooltip(List<Component> tooltip) {
        var player = Minecraft.getInstance().player;
        String user = player != null ? player.getScoreboardName().toUpperCase() : "USER";
        tooltip.add(Component.literal("§8[ §7AUTH_AS: §b" + user + " §8]"));
    }
}
