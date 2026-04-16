package net.phoenixvine.phoenix_archive.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;




@Mod.EventBusSubscriber(modid = "phoenix_archive", bus = Mod.EventBusSubscriber.Bus.MOD)
public class SoundRegistry {

    @SubscribeEvent
    public static void onRegisterSounds(RegisterEvent event) {
        event.register(ForgeRegistries.Keys.SOUND_EVENTS, helper -> {
            // Register each sound event by its sounds.json key
            register(helper, "voice.microverse_log");
            register(helper, "voice.drift_log");
            // Add new voice lines here as you create them
        });
    }

    private static void register(RegisterEvent.RegisterHelper<SoundEvent> helper, String path) {
        ResourceLocation id = new ResourceLocation("phoenix_archive", path);
        // Use the constructor directly or the helper method
        helper.register(id, SoundEvent.createFixedRangeEvent(id, 16.0F));
    }
}