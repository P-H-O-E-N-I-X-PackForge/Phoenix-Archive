package net.phoenixvine.phoenix_archive.config;

import net.phoenixvine.phoenix_archive.PhoenixArchive;

import dev.toma.configuration.Configuration;
import dev.toma.configuration.config.Config;
import dev.toma.configuration.config.ConfigHolder;
import dev.toma.configuration.config.Configurable;
import dev.toma.configuration.config.format.ConfigFormats;

@Config(id = PhoenixArchive.MOD_ID)
public class ArchiveConfigs {

    public static ArchiveConfigs INSTANCE;
    public static ConfigHolder<ArchiveConfigs> CONFIG_HOLDER;

    public static void init() {
        CONFIG_HOLDER = Configuration.registerConfig(ArchiveConfigs.class, ConfigFormats.yaml());
        INSTANCE = CONFIG_HOLDER.getConfigInstance();
    }

    @Configurable
    public GeneralConfig general = new GeneralConfig();

    public static class GeneralConfig {

        @Configurable
        @Configurable.Comment({
                "The name shown in the main menu. Default Phoenix Archives" })
        public String mainMenuName = "PhoenixOS";
        @Configurable
        @Configurable.Comment({
                "The name shown in the Lore Tablet's tooltip. Default Phoenix Archives" })
        public String loreTabletTooltipName = "Phoenix Archives";
    }
}
