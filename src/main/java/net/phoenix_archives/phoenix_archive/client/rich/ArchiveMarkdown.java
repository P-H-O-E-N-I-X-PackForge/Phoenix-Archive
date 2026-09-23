package net.phoenix_archives.phoenix_archive.client.rich;

import net.phoenixvine.wiki.client.rich.RichBlock;
import net.phoenixvine.wiki.client.rich.WikiMarkdownParser;
import net.phoenixvine.wiki.client.rich.markdown.HeadingSectionGrouper;

import java.util.ArrayList;
import java.util.List;

/**
 * Archive's entry point for parsing lore entry markdown -- thin wrapper around the shared
 * {@link WikiMarkdownParser}: turns on soft-wrapped list continuation (Archive's one opt-in
 * deviation from the shared default) and fixes up heading-collapsing inside
 * {@link ArchiveConditionalSection} branches, which {@link HeadingSectionGrouper} can't see into
 * since that block type is local to Archive, not known to the shared engine.
 */
public final class ArchiveMarkdown {

    private ArchiveMarkdown() {}

    public static List<RichBlock> parse(String input) {
        return fixupConditionals(WikiMarkdownParser.parse(input, true));
    }

    private static List<RichBlock> fixupConditionals(List<RichBlock> blocks) {
        List<RichBlock> out = new ArrayList<>(blocks.size());
        for (RichBlock b : blocks) out.add(fixupOne(b));
        return out;
    }

    private static RichBlock fixupOne(RichBlock b) {
        if (b instanceof ArchiveConditionalSection cs) {
            return new ArchiveConditionalSection(cs.condition(),
                    fixupConditionals(HeadingSectionGrouper.group(cs.thenChildren())),
                    fixupConditionals(HeadingSectionGrouper.group(cs.elseChildren())));
        } else if (b instanceof RichBlock.Callout c) {
            return new RichBlock.Callout(c.type(), c.title(), fixupConditionals(c.children()));
        } else if (b instanceof RichBlock.Details d) {
            return new RichBlock.Details(d.expandKey(), d.title(), fixupConditionals(d.children()));
        } else if (b instanceof RichBlock.CollapsibleSection s) {
            return new RichBlock.CollapsibleSection(s.level(), s.headingSpans(), s.collapseKey(),
                    fixupConditionals(s.children()));
        }
        return b;
    }
}
