package net.phoenix.phoenix_archive.config;

import dev.toma.configuration.Configuration;
import dev.toma.configuration.config.Config;
import dev.toma.configuration.config.ConfigHolder;
import dev.toma.configuration.config.Configurable;
import dev.toma.configuration.config.format.ConfigFormats;

@Config(id = net.phoenix.phoenix_archive.PhoenixArchive.MOD_ID)
public class ModConfig {

    public static ModConfig INSTANCE;
    public static ConfigHolder<ModConfig> CONFIG_HOLDER;

    public static void init() {
        CONFIG_HOLDER = Configuration.registerConfig(ModConfig.class, ConfigFormats.yaml());
        INSTANCE = CONFIG_HOLDER.getConfigInstance();
    }

    @Configurable
    public ColorConfig colors = new ColorConfig();

    public static class ColorConfig {

    }
}
