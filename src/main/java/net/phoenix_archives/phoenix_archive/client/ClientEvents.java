package net.phoenix_archives.phoenix_archive.client;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.phoenix_archives.phoenix_archive.PhoenixArchive;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = PhoenixArchive.MOD_ID, value = Dist.CLIENT)
public class ClientEvents {

    public static final KeyMapping OPEN_ARCHIVE = new KeyMapping(
            "key.phoenix_archive.open",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J,
            "category.phoenix_archive");

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_ARCHIVE);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        {
            while (OPEN_ARCHIVE.consumeClick()) {
                Minecraft.getInstance().setScreen(new ArchiveScreen());
            }
        }
    }
}
