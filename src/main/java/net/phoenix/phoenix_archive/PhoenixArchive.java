package net.phoenix.phoenix_archive;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.phoenix.phoenix_archive.api.LoreDataLoader;
import net.phoenix.phoenix_archive.api.TriggerRegistry;
import net.phoenix.phoenix_archive.network.PhoenixNetwork; // NEW import
import net.phoenix.phoenix_archive.config.ModConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(PhoenixArchive.MOD_ID)
public class PhoenixArchive {

    public static final String MOD_ID = "phoenix_archive";
    public static final Logger LOGGER = LogManager.getLogger();

    public PhoenixArchive(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();

        ModConfig.init();

        // Setup listener
        modEventBus.addListener(this::commonSetup);

        // INITIALIZE NETWORK: Critical for the "Nuclear Option"
        PhoenixNetwork.init();

        // Register this class to the Forge Event Bus for the Reload Listener
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            LOGGER.info("It's Lore time! Initializing Phoenix Archive...");
        });
    }

    @SubscribeEvent
    public void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(new LoreDataLoader());
        LOGGER.info("Phoenix Archive: Lore Data Listener registered.");
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}