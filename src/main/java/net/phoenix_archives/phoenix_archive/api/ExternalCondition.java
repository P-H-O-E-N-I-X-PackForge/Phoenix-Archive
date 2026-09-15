package net.phoenix_archives.phoenix_archive.api;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ExternalCondition {

    private ExternalCondition() {}

    private static final Pattern COMPARISON = Pattern.compile("^(.*?)(>=|<=|==|!=|>|<)(-?\\d+)$");

    public record Parsed(String id, String op, long threshold) {}

    public static Parsed parse(String value) {
        Matcher m = COMPARISON.matcher(value);
        if (m.matches()) {
            return new Parsed(m.group(1), m.group(2), Long.parseLong(m.group(3)));
        }
        return new Parsed(value, ">=", 1);
    }

    public static boolean test(long count, String op, long threshold) {
        return switch (op) {
            case "<=" -> count <= threshold;
            case "==" -> count == threshold;
            case "!=" -> count != threshold;
            case ">" -> count > threshold;
            case "<" -> count < threshold;
            default -> count >= threshold; 
        };
    }
}
