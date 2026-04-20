package net.phoenixvine.phoenix_archive;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.phoenixvine.phoenix_archive.api.CategoryRegistry; // New Import
import net.phoenixvine.phoenix_archive.api.LoreDataLoader;
import net.phoenixvine.phoenix_archive.common.ArchiveItems;
import net.phoenixvine.phoenix_archive.config.ArchiveConfigs;
import net.phoenixvine.phoenix_archive.network.PhoenixNetwork;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(PhoenixArchive.MOD_ID)
public class PhoenixArchive {

    public static final String MOD_ID = "phoenix_archive";
    public static final Logger LOGGER = LogManager.getLogger();



    public PhoenixArchive(FMLJavaModLoadingContext context) {
        ArchiveConfigs.init();
        IEventBus modEventBus = context.getModEventBus();

        ArchiveItems.ITEMS.register(modEventBus);

        // Setup listener
        modEventBus.addListener(this::commonSetup);

        // INITIALIZE NETWORK
        PhoenixNetwork.init();

        // Register this class to the Forge Event Bus
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            LOGGER.info("PHOENIX_OS // Initializing Archives...");

            // CRITICAL: Load Category Metadata from config/phoenix_archive/categories/
            // This ensures your weights and descriptions are ready before the GUI opens
            CategoryRegistry.loadFromDisk();

            LOGGER.info("PHOENIX_OS // Category Metadata indexed.");
        });
    }

    @SubscribeEvent
    public void onAddReloadListener(AddReloadListenerEvent event) {
        // This handles the Lore Entries (.json files in data packs)
        event.addListener(new LoreDataLoader());
        LOGGER.info("PHOENIX_OS // Lore Data Listener online.");
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}