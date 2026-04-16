package net.phoenixvine.phoenix_archive.api;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

public record LoreEntry(
        String id, // New Field
        String title,
        String category,
        String content,
        String iconItem,
        int questId,
        String lockedContent,
        String voiceLine,
        Map<String, String> conditions,
        int order
) {
    public LoreEntry {
        if (category == null) category = "Uncategorized";
        // Process Hex and Color codes immediately upon creation
        content = processHex(content);
        iconItem = iconItem == null ? "minecraft:paper" : iconItem;
        lockedContent = processHex(lockedContent != null ? lockedContent : "This data is encrypted.");
        voiceLine = voiceLine == null ? "" : voiceLine; // Safeguard for older files
        if (conditions == null) conditions = Collections.emptyMap();
    }

    public Map<String, String> getConditions() { return conditions(); }

    public boolean hasCondition(String key, String value) {
        return value.equals(this.conditions.get(key));
    }

    public static String processHex(String text) {
        if (text == null) return "";
        String processed = text.replace("&", "§");
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("§#([A-Fa-f0-9]{6})").matcher(processed);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : hex.toCharArray()) {
                replacement.append("§").append(c);
            }
            matcher.appendReplacement(sb, replacement.toString());
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}