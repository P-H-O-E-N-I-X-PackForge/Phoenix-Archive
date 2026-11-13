package net.phoenix_archives.phoenix_archive.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.phoenix_archives.phoenix_archive.common.ArchiveLang;

public class ClientPacketHandler {

    public static void handleToast(String title, String iconId) {
        Minecraft mc = Minecraft.getInstance();

        ResourceLocation iconRes = ResourceLocation.parse(iconId);
        ItemStack icon = new ItemStack(BuiltInRegistries.ITEM.get(iconRes));
        if (icon.isEmpty()) icon = new ItemStack(Items.PAPER);

        mc.getToasts().addToast(new SystemToast(
                SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                ArchiveLang.tr("phoenix_archive.client_packet_handler.phoenix_os"),
                ArchiveLang.tr("phoenix_archive.client_packet_handler.decrypted", title)));
    }
}
