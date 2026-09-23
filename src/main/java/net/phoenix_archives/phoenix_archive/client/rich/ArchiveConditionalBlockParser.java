package net.phoenix_archives.phoenix_archive.client.rich;

import net.minecraft.network.chat.Style;
import net.phoenix_archives.phoenix_archive.api.ConditionExprParser;
import net.phoenix_archives.phoenix_archive.api.ConditionNode;
import net.phoenix_archives.phoenix_archive.api.ConditionSyntaxException;
import net.phoenixvine.wiki.client.rich.RichBlock;
import net.phoenixvine.wiki.client.rich.RichSpan;
import net.phoenixvine.wiki.client.rich.markdown.BlockParser;
import net.phoenixvine.wiki.client.rich.markdown.MarkdownPatterns;
import net.phoenixvine.wiki.client.rich.markdown.ParseContext;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Recognizes {@code :::if condition} ... {@code :::else} ... {@code :::} -- registered via
 * {@link net.phoenixvine.wiki.client.rich.markdown.BlockParserRegistry#registerFirst}, ahead of
 * the generic container parser, so {@code :::if} isn't swallowed as a plain callout of type "if".
 * Any other {@code :::type} is declined (returns -1) and falls through to the generic parser.
 */
public final class ArchiveConditionalBlockParser implements BlockParser {

    private static final Pattern CONTAINER_ELSE = Pattern.compile("^:::else\\s*$");

    @Override
    public int tryParse(String[] lines, int i, List<RichBlock> out, ParseContext ctx) {
        Matcher open = MarkdownPatterns.CONTAINER_OPEN.matcher(lines[i].trim());
        if (!open.matches() || !open.group(1).equalsIgnoreCase("if")) return -1;

        String title = open.group(2).trim();
        int depth = 1;
        int start = i + 1;
        int j = start;
        int elseIdx = -1;
        while (j < lines.length && depth > 0) {
            String t = lines[j].trim();
            if (MarkdownPatterns.CONTAINER_CLOSE.matcher(t).matches()) {
                depth--;
                if (depth == 0) break;
            } else if (depth == 1 && elseIdx < 0 && CONTAINER_ELSE.matcher(t).matches()) {
                elseIdx = j;
            } else if (MarkdownPatterns.CONTAINER_OPEN.matcher(t).matches()) {
                depth++;
            }
            j++;
        }

        String[] thenLines, elseLines;
        if (elseIdx >= 0) {
            thenLines = Arrays.copyOfRange(lines, start, elseIdx);
            elseLines = Arrays.copyOfRange(lines, elseIdx + 1, Math.min(j, lines.length));
        } else {
            thenLines = Arrays.copyOfRange(lines, start, Math.min(j, lines.length));
            elseLines = new String[0];
        }

        try {
            ConditionNode condition = ConditionExprParser.parse(title);
            out.add(new ArchiveConditionalSection(condition, ctx.parseLines(thenLines), ctx.parseLines(elseLines)));
        } catch (ConditionSyntaxException ex) {
            out.add(new RichBlock.Callout("warning", "Bad :::if condition", List.of(
                    new RichBlock.Paragraph(List.of(new RichSpan.Text("§c" + ex.getMessage(), Style.EMPTY))))));
        }
        return j + 1;
    }
}
