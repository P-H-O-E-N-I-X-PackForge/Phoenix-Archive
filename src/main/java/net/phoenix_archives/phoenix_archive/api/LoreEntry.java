package net.phoenix_archives.phoenix_archive.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record LoreEntry(
                        String id, 
                        String title,
                        String category,
                        String content,
                        String iconItem,
                        int questId,
                        String lockedContent,
                        String voiceLine,
                        ConditionNode conditionTree,
                        int order,
                        String backgroundShader) {

    public LoreEntry(String id, String title, String category, String content, String iconItem, int questId,
                     String lockedContent, String voiceLine, ConditionNode conditionTree, int order) {
        this(id, title, category, content, iconItem, questId, lockedContent, voiceLine, conditionTree, order, "");
    }

    public LoreEntry(String id, String title, String category, String content, String iconItem, int questId,
                     String lockedContent, String voiceLine, Map<String, String> conditions, int order,
                     String backgroundShader) {
        this(id, title, category, content, iconItem, questId, lockedContent, voiceLine, fromMap(conditions), order,
                backgroundShader);
    }

    public LoreEntry(String id, String title, String category, String content, String iconItem, int questId,
                     String lockedContent, String voiceLine, Map<String, String> conditions, int order) {
        this(id, title, category, content, iconItem, questId, lockedContent, voiceLine, fromMap(conditions), order,
                "");
    }

    private static ConditionNode fromMap(Map<String, String> conditions) {
        if (conditions == null || conditions.isEmpty()) return ConditionNode.EMPTY;
        List<ConditionNode> leaves = new ArrayList<>();
        conditions.forEach((k, v) -> leaves.add(new ConditionNode.Leaf(k, v)));
        return new ConditionNode.And(leaves);
    }

    public LoreEntry {
        if (category == null) category = "Uncategorized";
        
        content = processHex(content);
        iconItem = iconItem == null ? "minecraft:paper" : iconItem;
        lockedContent = processHex(lockedContent != null ? lockedContent : "This data is encrypted.");
        voiceLine = voiceLine == null ? "" : voiceLine; 
        if (conditionTree == null) conditionTree = ConditionNode.EMPTY;
        backgroundShader = backgroundShader == null ? "" : backgroundShader;
    }

    public boolean hasCondition(String type, String value) {
        return conditionTree.findLeafValue(type).map(value::equals).orElse(false);
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
