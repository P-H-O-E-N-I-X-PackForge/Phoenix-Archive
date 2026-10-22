package net.phoenix_archives.phoenix_archive;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.phoenix_archives.phoenix_archive.api.CategoryRegistry;
import net.phoenix_archives.phoenix_archive.api.LoreDataLoader;
import net.phoenix_archives.phoenix_archive.client.ArchiveClient;
import net.phoenix_archives.phoenix_archive.common.ArchiveItems;
import net.phoenix_archives.phoenix_archive.config.ArchiveConfigs;
import net.phoenix_archives.phoenix_archive.network.PhoenixNetwork;
import net.phoenix_archives.phoenix_archive.proxy.ClientProxy;
import net.phoenix_archives.phoenix_archive.proxy.IProxy;
import net.phoenix_archives.phoenix_archive.proxy.ServerProxy;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(PhoenixArchive.MOD_ID)
public class PhoenixArchive {

    public static final String MOD_ID = "phoenix_archive";
    public static final Logger LOGGER = LogManager.getLogger();
    public static IProxy PROXY;

    public PhoenixArchive(IEventBus modEventBus, ModContainer container) {
        ArchiveConfigs.init();

        ArchiveItems.ITEMS.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::buildCreativeTab);

        if (FMLEnvironment.dist.isClient()) {
            PROXY = new ClientProxy();
            modEventBus.addListener(ArchiveClient::onClientSetup);
        } else {
            PROXY = new ServerProxy();
        }

        modEventBus.addListener(PhoenixNetwork::register);

        NeoForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            LOGGER.info("PHOENIX_OS // Initializing Archives...");

            CategoryRegistry.loadFromDisk();

            LOGGER.info("PHOENIX_OS // Category Metadata indexed.");
        });
    }

    private void buildCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ArchiveItems.LORE_TABLET.get());
        }
    }

    @SubscribeEvent
    public void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(new LoreDataLoader());
        LOGGER.info("PHOENIX_OS // Lore Data Listener online.");
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
