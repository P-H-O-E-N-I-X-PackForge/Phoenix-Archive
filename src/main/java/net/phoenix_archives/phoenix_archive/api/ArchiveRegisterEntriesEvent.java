package net.phoenix_archives.phoenix_archive.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.Event;

import java.util.LinkedHashMap;
import java.util.Map;

public class ArchiveRegisterEntriesEvent extends Event {

    private final Map<ResourceLocation, LoreEntry> entries = new LinkedHashMap<>();

    public void register(ResourceLocation id, LoreEntry entry) {
        if (id == null || entry == null) return;
        entries.put(id, entry);
    }

    public Map<ResourceLocation, LoreEntry> getRegisteredEntries() {
        return entries;
    }
}
