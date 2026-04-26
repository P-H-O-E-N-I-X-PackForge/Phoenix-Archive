package net.phoenixvine.phoenix_archive.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.phoenixvine.phoenix_archive.PhoenixArchive;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileReader;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

public class LoreDataLoader extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    public static volatile Map<ResourceLocation, LoreEntry> LORE_ENTRIES = new LinkedHashMap<>();

    public LoreDataLoader() {
        super(GSON, "phoenix_lore"); // Looks for assets/modid/phoenix_lore/*.json
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> object, @NotNull ResourceManager resourceManager,
                         @NotNull ProfilerFiller profiler) {
        Map<ResourceLocation, LoreEntry> newEntries = new LinkedHashMap<>();

        // 1. Load Internal Data
        object.forEach((location, element) -> {
            try {
                LoreEntry entry = GSON.fromJson(element, LoreEntry.class);
                if (isValid(entry)) {
                    // Ensure the entry has a fallback ID if the 'id' field is missing in JSON
                    entry = ensureInternalId(entry, location.getPath());
                    newEntries.put(location, entry);
                }
            } catch (Exception e) {
                PhoenixArchive.LOGGER.error("Failed to parse internal lore: {}", location, e);
            }
        });

        // 2. Load External Data
        loadExternalLore(newEntries);

        // 3. Sequential Sorting
        var sortedList = newEntries.entrySet().stream()
                .sorted(Comparator.comparingInt(e -> e.getValue().order()))
                .toList();

        Map<ResourceLocation, LoreEntry> sortedMap = new LinkedHashMap<>();
        for (var entry : sortedList) {
            sortedMap.put(entry.getKey(), entry.getValue());
        }

        LORE_ENTRIES = sortedMap;
        PhoenixArchive.LOGGER.info("PHOENIX_OS // Handshake successful. {} archive records indexed.",
                LORE_ENTRIES.size());
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
                        String nameId = file.getName().toLowerCase().replace(".json", "");
                        ResourceLocation resId = new ResourceLocation("phoenix_archive", nameId);

                        // Ensure internal ID is set
                        entry = ensureInternalId(entry, nameId);

                        map.put(resId, entry);
                    }
                } catch (Exception e) {
                    PhoenixArchive.LOGGER.error("CRITICAL_ERROR // External Disk Read Failure: {}", file.getName());
                }
            }
        }
    }

    /**
     * If the JSON doesn't define an "id", we generate one based on the filename.
     * This prevents 'null' IDs from breaking the unlock logic.
     */
    private LoreEntry ensureInternalId(LoreEntry entry, String fallback) {
        if (entry.id() == null || entry.id().isEmpty()) {
            return new LoreEntry(
                    fallback, // Use filename as ID if missing
                    entry.title(), entry.category(), entry.content(), entry.iconItem(),
                    entry.questId(), entry.lockedContent(), entry.voiceLine(),
                    entry.getConditions(), entry.order());
        }
        return entry;
    }

    private boolean isValid(LoreEntry entry) {
        return entry != null && entry.title() != null && !entry.title().isEmpty();
    }
}
