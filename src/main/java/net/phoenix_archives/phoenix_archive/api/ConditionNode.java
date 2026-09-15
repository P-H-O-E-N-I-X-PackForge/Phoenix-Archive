package net.phoenix_archives.phoenix_archive.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public sealed interface ConditionNode permits ConditionNode.Leaf, ConditionNode.And, ConditionNode.Or,
                                      ConditionNode.Not {

    record Leaf(String type, String value) implements ConditionNode {}

    record And(List<ConditionNode> children) implements ConditionNode {

        public And {
            children = List.copyOf(children);
        }
    }

    record Or(List<ConditionNode> children) implements ConditionNode {

        public Or {
            children = List.copyOf(children);
        }
    }

    record Not(ConditionNode child) implements ConditionNode {}

    ConditionNode EMPTY = new And(List.of());

    default boolean isEmpty() {
        return this instanceof And a && a.children().isEmpty();
    }

    default Optional<String> findLeafValue(String type) {
        if (this instanceof Leaf l) return type.equals(l.type()) ? Optional.of(l.value()) : Optional.empty();
        if (this instanceof And a) {
            return a.children().stream().map(c -> c.findLeafValue(type))
                    .filter(Optional::isPresent).findFirst().orElseGet(Optional::empty);
        }
        if (this instanceof Or o) {
            return o.children().stream().map(c -> c.findLeafValue(type))
                    .filter(Optional::isPresent).findFirst().orElseGet(Optional::empty);
        }
        return Optional.empty(); 
    }

    default ConditionNode withTopLevelLeaf(String type, String value) {
        List<ConditionNode> siblings = new ArrayList<>(this instanceof And a ? a.children() : List.of(this));
        siblings.removeIf(c -> c instanceof Leaf l && l.type().equals(type));
        if (value != null && !value.isEmpty()) siblings.add(new Leaf(type, value));
        return new And(siblings);
    }

    record NegatableLeaf(Leaf leaf, boolean negated) {}

    default List<NegatableLeaf> collectLeaves() {
        List<NegatableLeaf> out = new ArrayList<>();
        collectLeavesInto(this, false, out);
        return out;
    }

    private static void collectLeavesInto(ConditionNode node, boolean negated, List<NegatableLeaf> out) {
        if (node instanceof Leaf l) {
            out.add(new NegatableLeaf(l, negated));
        } else if (node instanceof And a) {
            a.children().forEach(c -> collectLeavesInto(c, negated, out));
        } else if (node instanceof Or o) {
            o.children().forEach(c -> collectLeavesInto(c, negated, out));
        } else if (node instanceof Not n) {
            collectLeavesInto(n.child(), !negated, out);
        }
    }
}
