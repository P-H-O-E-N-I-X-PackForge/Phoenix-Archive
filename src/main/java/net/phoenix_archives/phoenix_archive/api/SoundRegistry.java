package net.phoenix_archives.phoenix_archive.api;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(modid = "phoenix_archive", bus = EventBusSubscriber.Bus.MOD)
public class SoundRegistry {

    @SubscribeEvent
    public static void onRegisterSounds(RegisterEvent event) {
        event.register(Registries.SOUND_EVENT, helper -> {

            register(helper, "voice.microverse_log");
            register(helper, "voice.drift_log");

        });
    }

    private static void register(RegisterEvent.RegisterHelper<SoundEvent> helper, String path) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("phoenix_archive", path);

        helper.register(id, SoundEvent.createFixedRangeEvent(id, 16.0F));
    }
}
