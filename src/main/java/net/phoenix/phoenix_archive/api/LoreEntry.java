package net.phoenix.phoenix_archive.api;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

public record LoreEntry(
        String title,
        String category,
        String content,
        String iconItem,
        long questId,
        String lockedContent,
        Map<String, String> conditions
) {
    public LoreEntry {
        if (category == null) category = "Uncategorized";
        if (content == null) content = "";
        if (iconItem == null) iconItem = "minecraft:paper";
        if (lockedContent == null) lockedContent = "This data is encrypted.";
        if (conditions == null) {
            conditions = Collections.emptyMap();
        }
    }

    // Helper for the Registry
    public Map<String, String> getConditions() {
        return conditions();
    }

    public String getMachineId() {
        // Matches the "gt_machine" key used in TriggerRegistry
        return Optional.ofNullable(conditions.get("gt_machine")).orElse("");
    }

    public String getDimensionId() {
        return Optional.ofNullable(conditions.get("dimension")).orElse("");
    }

    public String getBiomeId() {
        return Optional.ofNullable(conditions.get("biome")).orElse("");
    }

    public String getEntityId() {
        return Optional.ofNullable(conditions.get("entity")).orElse("");
    }
}