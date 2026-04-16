package net.phoenixvine.phoenix_archive.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.phoenixvine.phoenix_archive.PhoenixArchive;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = PhoenixArchive.MOD_ID, value = Dist.CLIENT)
public class ClientEvents {
    public static final KeyMapping OPEN_ARCHIVE = new KeyMapping(
            "key.phoenix_archive.open",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J,
            "category.phoenix_archive"
    );

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_ARCHIVE);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            while (OPEN_ARCHIVE.consumeClick()) {
                Minecraft.getInstance().setScreen(new ArchiveScreen());
            }
        }
    }
}