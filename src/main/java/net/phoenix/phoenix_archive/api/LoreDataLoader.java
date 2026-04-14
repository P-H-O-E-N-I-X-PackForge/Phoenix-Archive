package net.phoenix.phoenix_archive.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.phoenix.phoenix_archive.PhoenixArchive;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileReader;
import java.util.LinkedHashMap;
import java.util.Map;

public class LoreDataLoader extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    // We use a volatile LinkedHashMap to ensure order is preserved and updates are thread-safe
    public static volatile Map<ResourceLocation, LoreEntry> LORE_ENTRIES = new LinkedHashMap<>();

    public LoreDataLoader() {
        super(GSON, "phoenix_lore");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> object, @NotNull ResourceManager resourceManager, @NotNull ProfilerFiller profiler) {
        // LinkedHashMap is CRITICAL here to maintain the order from files/reloading
        Map<ResourceLocation, LoreEntry> newEntries = new LinkedHashMap<>();

        // 1. Load internal lore (from datapacks/resources)
        object.forEach((location, element) -> {
            try {
                LoreEntry entry = GSON.fromJson(element, LoreEntry.class);
                if (isValid(entry)) {
                    newEntries.put(location, entry);
                }
            } catch (Exception e) {
                PhoenixArchive.LOGGER.error("Failed to parse internal lore: {}", location, e);
            }
        });

        // 2. Load external lore (from /config folder)
        loadExternalLore(newEntries);

        // 3. Atomic Swap: This ensures the TriggerRegistry never sees an empty map during reload
        LORE_ENTRIES = newEntries;
        PhoenixArchive.LOGGER.info("PHOENIX_OS // Loaded {} total archive records", LORE_ENTRIES.size());
    }

    private void loadExternalLore(Map<ResourceLocation, LoreEntry> map) {
        File configDir = new File("config/phoenix_archive/lore");
        if (!configDir.exists() && !configDir.mkdirs()) return;

        File[] files = configDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (files != null) {
            for (File file : files) {
                try (FileReader reader = new FileReader(file)) {
                    LoreEntry entry = GSON.fromJson(reader, LoreEntry.class);
                    if (isValid(entry)) {
                        // Use a consistent ID format for external entries
                        String nameId = file.getName().toLowerCase().replace(".json", "").replaceAll("[^a-z0-9]", "_");
                        ResourceLocation id = new ResourceLocation("phoenix_archive", "external_" + nameId);

                        // Config entries overwrite resource pack entries if they share the same ID logic
                        map.put(id, entry);
                    }
                } catch (Exception e) {
                    PhoenixArchive.LOGGER.error("Failed to load external lore file: {}", file.getName(), e);
                }
            }
        }
    }

    private boolean isValid(LoreEntry entry) {
        return entry != null && entry.title() != null && !entry.title().isEmpty();
    }
}