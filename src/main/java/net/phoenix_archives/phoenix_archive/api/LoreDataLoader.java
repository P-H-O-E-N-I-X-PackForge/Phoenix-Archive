package net.phoenix_archives.phoenix_archive.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModList;
import net.phoenix_archives.phoenix_archive.PhoenixArchive;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileReader;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class LoreDataLoader extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = ConditionNodeAdapter.register(new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping())
            .create();

    public static volatile Map<ResourceLocation, LoreEntry> LORE_ENTRIES = new LinkedHashMap<>();

    public LoreDataLoader() {
        super(GSON, "phoenix_lore");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> object, @NotNull ResourceManager resourceManager,
                         @NotNull ProfilerFiller profiler) {
        Map<ResourceLocation, LoreEntry> newEntries = new LinkedHashMap<>();

        object.forEach((location, element) -> {
            try {
                LoreEntry entry = GSON.fromJson(element, LoreEntry.class);
                if (isValid(entry)) {

                    entry = ensureInternalId(entry, location.getPath());
                    newEntries.put(location, entry);
                }
            } catch (Exception e) {
                PhoenixArchive.LOGGER.error("Failed to parse internal lore: {}", location, e);
            }
        });

        loadExternalLore(newEntries);

        ArchiveRegisterEntriesEvent registerEvent = new ArchiveRegisterEntriesEvent();
        MinecraftForge.EVENT_BUS.post(registerEvent);
        registerEvent.getRegisteredEntries().forEach((id, entry) -> {
            if (isValid(entry)) {
                entry = ensureInternalId(entry, id.getPath());
                entry = autoOrganize(entry, id);
                newEntries.put(id, entry);
            } else {
                PhoenixArchive.LOGGER.warn("Ignored invalid lore entry registered by another mod: {}", id);
            }
        });

        var sortedList = newEntries.entrySet().stream()
                .sorted(Comparator.comparingInt(e -> e.getValue().order()))
                .toList();

        Map<ResourceLocation, LoreEntry> sortedMap = new LinkedHashMap<>();
        for (var entry : sortedList) {
            sortedMap.put(entry.getKey(), entry.getValue());
        }

        LORE_ENTRIES = sortedMap;
        PhoenixArchive.LOGGER.info("PHOENIX_OS // Handshake successful. {} phoenix_archives records indexed.",
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

                        entry = ensureInternalId(entry, nameId);

                        map.put(resId, entry);
                    }
                } catch (Exception e) {
                    PhoenixArchive.LOGGER.error("CRITICAL_ERROR // External Disk Read Failure: {}", file.getName());
                }
            }
        }
    }

    private LoreEntry ensureInternalId(LoreEntry entry, String fallback) {
        if (entry.id() == null || entry.id().isEmpty()) {
            return new LoreEntry(
                    fallback,
                    entry.title(), entry.category(), entry.content(), entry.iconItem(),
                    entry.questId(), entry.lockedContent(), entry.voiceLine(),
                    entry.conditionTree(), entry.order(), entry.backgroundShader(),
                    entry.hidden(), entry.hiddenUntilId());
        }
        return entry;
    }

    private boolean isValid(LoreEntry entry) {
        return entry != null && entry.title() != null && !entry.title().isEmpty();
    }

    private LoreEntry autoOrganize(LoreEntry entry, ResourceLocation id) {
        if (!"Uncategorized".equalsIgnoreCase(entry.category())) return entry;
        String namespace = id.getNamespace();
        if (namespace.equals(PhoenixArchive.MOD_ID)) return entry;

        String displayName = ModList.get().getModContainerById(namespace)
                .map(container -> container.getModInfo().getDisplayName())
                .orElse(namespace);
        String autoCategory = displayName.toUpperCase(Locale.ROOT);
        CategoryRegistry.register(autoCategory, "Contributed by " + displayName, 500, null);

        return new LoreEntry(entry.id(), entry.title(), autoCategory, entry.content(), entry.iconItem(),
                entry.questId(), entry.lockedContent(), entry.voiceLine(), entry.conditionTree(), entry.order(),
                entry.backgroundShader(), entry.hidden(), entry.hiddenUntilId());
    }
}
