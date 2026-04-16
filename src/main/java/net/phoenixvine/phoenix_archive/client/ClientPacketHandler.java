package net.phoenixvine.phoenix_archive.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

// Put this in a Client-Side only class or your Main class's client setup
public class ClientPacketHandler {
    public static void handleToast(String title, String iconId) {
        Minecraft mc = Minecraft.getInstance();

        // Get the icon item
        ResourceLocation iconRes = new ResourceLocation(iconId);
        ItemStack icon = new ItemStack(ForgeRegistries.ITEMS.getValue(iconRes));
        if (icon.isEmpty()) icon = new ItemStack(Items.PAPER);

        // Push the actual vanilla toast
        mc.getToasts().addToast(new SystemToast(
                SystemToast.SystemToastIds.PERIODIC_NOTIFICATION,
                Component.literal("§6§lPHOENIX_OS"), // Header
                Component.literal("§fDecrypted: " + title) // Subtitle
        ));
    }
}