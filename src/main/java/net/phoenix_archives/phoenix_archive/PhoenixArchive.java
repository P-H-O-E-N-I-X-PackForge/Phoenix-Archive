package net.phoenix_archives.phoenix_archive;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
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

    public PhoenixArchive(FMLJavaModLoadingContext context) {
        ArchiveConfigs.init();
        IEventBus modEventBus = context.getModEventBus();

        ArchiveItems.ITEMS.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::buildCreativeTab);

        if (FMLEnvironment.dist.isClient()) {
            PROXY = new ClientProxy();
            modEventBus.addListener(ArchiveClient::onClientSetup);
        } else {
            PROXY = new ServerProxy();
        }

        PhoenixNetwork.init();

        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            LOGGER.info("PHOENIX_OS // Initializing Archives...");

            CategoryRegistry.loadFromDisk();

            LOGGER.info("PHOENIX_OS // Category Metadata indexed.");
        });
    }

    /**
     * The Lore Tablet was never added to any creative tab -- JEI (and the creative inventory itself)
     * builds its ingredient list from tab contents, not the raw item registry, so it was silently
     * invisible in both despite being fully registered and obtainable via /give.
     */
    private void buildCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ArchiveItems.LORE_TABLET);
        }
    }

    @SubscribeEvent
    public void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(new LoreDataLoader());
        LOGGER.info("PHOENIX_OS // Lore Data Listener online.");
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
