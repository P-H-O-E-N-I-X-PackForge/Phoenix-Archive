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

// filename embeds a "/" so the "Configuration" library (dev.toma.configuration) writes into
// config/phoenix_archive/phoenix_archive.yaml instead of its default config/phoenix_archive.yaml --
// it resolves filename+extension as a bare relative File and mkdirs() the parent before writing, so
// a path separator here is enough; there's no dedicated "subdirectory" option in the library's API.
@Config(id = PhoenixArchive.MOD_ID, filename = "phoenix_archive/phoenix_archive")
public class ArchiveConfigs {

    public static ArchiveConfigs INSTANCE;
    public static ConfigHolder<ArchiveConfigs> CONFIG_HOLDER;

    public static void init() {
        migrateLegacyFile();
        CONFIG_HOLDER = Configuration.registerConfig(ArchiveConfigs.class, ConfigFormats.yaml());
        INSTANCE = CONFIG_HOLDER.getConfigInstance();
    }

    /**
     * The "Configuration" library resolves a config's filename as a bare relative {@link File} (see
     * the {@code @Config} annotation above), so before this moved into its own subfolder it lived at
     * {@code phoenix_archive.yaml} relative to wherever that resolves (empirically config/) --
     * carry an existing one over once, so packs that already set mainMenuName/loreTabletTooltipName
     * don't see them silently reset to defaults.
     */
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
