package net.phoenix_archives.phoenix_archive.api;

public final class ConditionEvaluator {

    private ConditionEvaluator() {}

    @FunctionalInterface
    public interface LeafChecker {

        boolean isUnlocked(String type, String value);
    }

    public static boolean evaluate(ConditionNode node, LeafChecker checker) {
        if (node instanceof ConditionNode.Leaf l) {

            if (l.value() == null || l.value().isEmpty()) return true;
            return checker.isUnlocked(l.type(), l.value());
        }
        if (node instanceof ConditionNode.And a) {
            return a.children().stream().allMatch(c -> evaluate(c, checker));
        }
        if (node instanceof ConditionNode.Or o) {
            return o.children().stream().anyMatch(c -> evaluate(c, checker));
        }
        if (node instanceof ConditionNode.Not n) {
            return !evaluate(n.child(), checker);
        }
        throw new IllegalStateException("Unknown ConditionNode subtype: " + node);
    }
}
