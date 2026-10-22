package net.phoenix_archives.phoenix_archive.config;

import net.phoenix_archives.phoenix_archive.PhoenixArchive;

import dev.toma.configuration.Configuration;
import dev.toma.configuration.config.Config;
import dev.toma.configuration.config.ConfigHolder;
import dev.toma.configuration.config.Configurable;
import dev.toma.configuration.config.format.ConfigFormats;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

@Config(id = PhoenixArchive.MOD_ID, filename = "phoenix_archive/phoenix_archive")
public class ArchiveConfigs {

    public static ArchiveConfigs INSTANCE;
    public static ConfigHolder<ArchiveConfigs> CONFIG_HOLDER;

    public static void init() {
        migrateLegacyFile();
        CONFIG_HOLDER = Configuration.registerConfig(ArchiveConfigs.class, ConfigFormats.yaml());
        INSTANCE = CONFIG_HOLDER.getConfigInstance();
    }

    private static void migrateLegacyFile() {
        File legacy = new File("phoenix_archive.yaml");
        File current = new File("phoenix_archive/phoenix_archive.yaml");
        if (!legacy.exists() || current.exists()) return;
        try {
            Files.createDirectories(current.getParentFile().toPath());
            Files.move(legacy.toPath(), current.toPath());
        } catch (IOException ignored) {}
    }

    @Configurable
    public GeneralConfig general = new GeneralConfig();

    public static class GeneralConfig {

        @Configurable
        @Configurable.Comment({
                "The name shown in the main menu. Default Phoenix Archives" })
        public String mainMenuName = "Phoenix Archives";
        @Configurable
        @Configurable.Comment({
                "The name shown in the Lore Tablet's tooltip. Default Phoenix Archives" })
        public String loreTabletTooltipName = "Phoenix Archives";
    }
}
