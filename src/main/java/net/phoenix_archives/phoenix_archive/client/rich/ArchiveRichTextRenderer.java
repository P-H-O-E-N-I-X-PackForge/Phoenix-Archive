package net.phoenix_archives.phoenix_archive.client.rich;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import net.phoenix_archives.phoenix_archive.client.ArchivePalette;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ArchiveRichTextRenderer {

    public static final int LINE_H = 10;

    private static final int GAP_PARAGRAPH = 4;
    private static final int GAP_LIST_ITEM = 2;
    private static final int GAP_HEADING_BEFORE = 8;
    private static final int GAP_HEADING_AFTER = 2;
    private static final int GAP_BLANK = 4;

    private static final float H1_SCALE = 1.35f;
    private static final float H2_SCALE = 1.2f;
    private static final float H3_SCALE = 1.08f;
    private static final float BOLD_SCALE = 1.15f;
    public static final float DEFAULT_SCALE = 1.0f;

    private static float headingScale(int level) {
        return switch (Math.min(Math.max(level, 1), 3)) {
            case 1 -> H1_SCALE;
            case 2 -> H2_SCALE;
            default -> H3_SCALE;
        };
    }

    private static float boldScale(float baseScale, boolean bold) {
        return bold ? baseScale * BOLD_SCALE : baseScale;
    }

    public static java.util.function.UnaryOperator<ResourceLocation> imageResolver = java.util.function.UnaryOperator
            .identity();

    private ArchiveRichTextRenderer() {}

    public static List<RichSpan.Region> render(
                                               GuiGraphics g, Font font,
                                               List<RichSpan> spans,
                                               int x, int y, int maxW,
                                               int scrollY, int clipTop, int clipBot) {
        return render(g, font, spans, x, y, maxW, scrollY, clipTop, clipBot, DEFAULT_SCALE);
    }

    public static List<RichSpan.Region> render(
                                               GuiGraphics g, Font font,
                                               List<RichSpan> spans,
                                               int x, int y, int maxW,
                                               int scrollY, int clipTop, int clipBot, float scale) {
        List<RichSpan.Region> regions = new ArrayList<>();
        int[] endY = { y - scrollY };
        renderSpanList(g, font, spans, x, endY, x, maxW, clipTop, clipBot, regions, scale);
        return regions;
    }

    public static int measureHeight(Font font, List<RichSpan> spans, int maxW) {
        return measureHeight(font, spans, maxW, DEFAULT_SCALE);
    }

    private static final int HEIGHT_CACHE_SLOTS = 6;
    private static final List<RichSpan>[] hcSpans = new List[HEIGHT_CACHE_SLOTS];
    private static final int[] hcWidth = new int[HEIGHT_CACHE_SLOTS];
    private static final float[] hcScale = new float[HEIGHT_CACHE_SLOTS];
    private static final int[] hcHeight = new int[HEIGHT_CACHE_SLOTS];
    private static int hcNext = 0;

    public static int measureHeight(Font font, List<RichSpan> spans, int maxW, float scale) {
        for (int i = 0; i < HEIGHT_CACHE_SLOTS; i++) {
            if (hcSpans[i] == spans && hcWidth[i] == maxW && hcScale[i] == scale) {
                return hcHeight[i];
            }
        }
        int height = measureSpanList(font, spans, maxW, scale);
        hcSpans[hcNext] = spans;
        hcWidth[hcNext] = maxW;
        hcScale[hcNext] = scale;
        hcHeight[hcNext] = height;
        hcNext = (hcNext + 1) % HEIGHT_CACHE_SLOTS;
        return height;
    }

    public static List<RichSpan.Region> renderBlocks(
                                                     GuiGraphics g, Font font,
                                                     List<RichBlock> blocks,
                                                     int x, int y, int maxW,
                                                     int scrollY, int clipTop, int clipBot) {
        return renderBlocks(g, font, blocks, x, y, maxW, scrollY, clipTop, clipBot, DEFAULT_SCALE,
                ArchivePalette.TERM, Set.of());
    }

    public static List<RichSpan.Region> renderBlocks(
                                                     GuiGraphics g, Font font,
                                                     List<RichBlock> blocks,
                                                     int x, int y, int maxW,
                                                     int scrollY, int clipTop, int clipBot, int accentColor) {
        return renderBlocks(g, font, blocks, x, y, maxW, scrollY, clipTop, clipBot, DEFAULT_SCALE, accentColor,
                Set.of());
    }

    public static List<RichSpan.Region> renderBlocks(
                                                     GuiGraphics g, Font font,
                                                     List<RichBlock> blocks,
                                                     int x, int y, int maxW,
                                                     int scrollY, int clipTop, int clipBot, int accentColor,
                                                     Set<String> expandedKeys) {
        return renderBlocks(g, font, blocks, x, y, maxW, scrollY, clipTop, clipBot, DEFAULT_SCALE, accentColor,
                expandedKeys);
    }

    public static List<RichSpan.Region> renderBlocks(
                                                     GuiGraphics g, Font font,
                                                     List<RichBlock> blocks,
                                                     int x, int y, int maxW,
                                                     int scrollY, int clipTop, int clipBot, float scale,
                                                     int accentColor, Set<String> expandedKeys) {
        List<RichSpan.Region> regions = new ArrayList<>();
        renderBlockList(g, font, blocks, x, y - scrollY, maxW, clipTop, clipBot, regions, scale, accentColor,
                expandedKeys);
        return regions;
    }

    public static int measureBlocksHeight(Font font, List<RichBlock> blocks, int maxW) {
        return measureBlocksHeight(font, blocks, maxW, DEFAULT_SCALE, Set.of());
    }

    public static int measureBlocksHeight(Font font, List<RichBlock> blocks, int maxW, Set<String> expandedKeys) {
        return measureBlocksHeight(font, blocks, maxW, DEFAULT_SCALE, expandedKeys);
    }

    public static int measureBlocksHeight(Font font, List<RichBlock> blocks, int maxW, float scale,
                                          Set<String> expandedKeys) {
        return measureBlockList(font, blocks, maxW, 0, scale, expandedKeys);
    }

    public record HeadingInfo(int level, String text, int y) {}

    public static List<HeadingInfo> computeHeadingOffsets(Font font, List<RichBlock> blocks, int maxW) {
        return computeHeadingOffsets(font, blocks, maxW, DEFAULT_SCALE);
    }

    public static List<HeadingInfo> computeHeadingOffsets(Font font, List<RichBlock> blocks, int maxW, float scale) {
        List<HeadingInfo> out = new ArrayList<>();
        computeHeadingOffsetsInto(font, blocks, maxW, new int[] { 0 }, true, scale, out);
        return out;
    }

    private static void computeHeadingOffsetsInto(Font font, List<RichBlock> blocks, int maxW, int[] y,
                                                  boolean first, float scale, List<HeadingInfo> out) {
        for (RichBlock block : blocks) {
            if (block instanceof RichBlock.Blank) {
                y[0] += GAP_BLANK;
                first = false;
                continue;
            }
            if (!first) y[0] += gapBefore(block);
            first = false;
            if (block instanceof RichBlock.Heading h) {
                out.add(new HeadingInfo(h.level(), plainText(h.spans()), y[0]));
                y[0] = measureOneBlock(font, block, maxW, y[0], scale, Set.of());
            } else if (block instanceof RichBlock.CollapsibleSection s) {
                out.add(new HeadingInfo(s.level(), plainText(s.headingSpans()), y[0]));
                y[0] = measureOneBlock(font, new RichBlock.Heading(s.level(), s.headingSpans()), maxW, y[0], scale,
                        Set.of());
                computeHeadingOffsetsInto(font, s.children(), maxW, y, true, scale, out);
            } else {
                y[0] = measureOneBlock(font, block, maxW, y[0], scale, Set.of());
            }
        }
    }

    private static String plainText(List<RichSpan> spans) {
        StringBuilder sb = new StringBuilder();
        for (RichSpan s : spans) {
            if (s instanceof RichSpan.Text t) sb.append(t.text());
            else if (s instanceof RichSpan.Link l) sb.append(l.label());
            else if (s instanceof RichSpan.Tip t) sb.append(t.label());
        }
        return sb.toString();
    }

    private static int renderBlockList(GuiGraphics g, Font font, List<RichBlock> blocks, int x, int y, int maxW,
                                       int clipTop, int clipBot, List<RichSpan.Region> regions, float scale,
                                       int accentColor, Set<String> expandedKeys) {
        int[] curY = { y };
        boolean first = true;
        float pendingScaleMult = 1f;
        for (RichBlock block : blocks) {
            if (block instanceof RichBlock.ScaleDirective sd) {
                pendingScaleMult = sd.multiplier();
                continue;
            }
            if (block instanceof RichBlock.Blank) {
                curY[0] += GAP_BLANK;
                first = false;
                continue;
            }
            if (!first) curY[0] += gapBefore(block);
            first = false;
            renderOneBlock(g, font, block, x, curY, maxW, clipTop, clipBot, regions, scale * pendingScaleMult,
                    accentColor, expandedKeys);
            pendingScaleMult = 1f;
        }
        return curY[0];
    }

    private static void renderOneBlock(GuiGraphics g, Font font, RichBlock block, int x, int[] curY, int maxW,
                                       int clipTop, int clipBot, List<RichSpan.Region> regions, float scale,
                                       int accentColor, Set<String> expandedKeys) {
        if (block instanceof RichBlock.Heading h) {
            renderHeadingLike(g, font, h.level(), h.spans(), x, curY, maxW, clipTop, clipBot, regions, scale,
                    accentColor);
        } else if (block instanceof RichBlock.CollapsibleSection s) {
            curY[0] = renderCollapsibleSection(g, font, s, x, curY[0], maxW, clipTop, clipBot, regions, scale,
                    accentColor, expandedKeys);
        } else if (block instanceof RichBlock.ListItem li) {
            if (curY[0] >= clipTop && curY[0] + 8 <= clipBot) {
                g.drawString(font, li.marker(), x, curY[0], ArchivePalette.TEXT_DIM, false);
            }
            renderSpanList(g, font, li.spans(), x + li.indent(), curY, x + li.indent(),
                    maxW - li.indent(), clipTop, clipBot, regions, scale);
        } else if (block instanceof RichBlock.Checklist cl) {
            boolean checked = expandedKeys.contains("CL1:" + cl.checkKey()) ? true :
                    !expandedKeys.contains("CL0:" + cl.checkKey()) && cl.checkedDefault();
            String glyph = checked ? "☑" : "☐";
            if (curY[0] >= clipTop && curY[0] + 8 <= clipBot) {
                g.drawString(font, glyph, x, curY[0], checked ? ArchivePalette.TERM_HILITE : ArchivePalette.TEXT_DIM,
                        false);
            }
            regions.add(new RichSpan.Region(x, curY[0], x + cl.indent(), curY[0] + 10,
                    new RichSpan.ChecklistToggle(cl.checkKey(), cl.checkedDefault())));
            List<RichSpan> spans = checked ? withStrikethroughStyle(cl.spans()) : cl.spans();
            renderSpanList(g, font, spans, x + cl.indent(), curY, x + cl.indent(),
                    maxW - cl.indent(), clipTop, clipBot, regions, scale);
        } else if (block instanceof RichBlock.Paragraph p) {
            renderSpanList(g, font, p.spans(), x, curY, x, maxW, clipTop, clipBot, regions, scale);
        } else if (block instanceof RichBlock.Rule) {
            if (curY[0] + 1 >= clipTop && curY[0] <= clipBot) {
                g.fill(x, curY[0] + 3, x + maxW, curY[0] + 4, ArchivePalette.BORDER);
            }
            curY[0] += 8;
        } else if (block instanceof RichBlock.CodeBlock cb) {
            curY[0] = renderCodeBlock(g, font, cb.lang(), cb.code(), x, curY[0], maxW, clipTop, clipBot, regions);
        } else if (block instanceof RichBlock.Quote q) {
            int quoteY = curY[0];
            int barX = x + 2;
            int textX = x + 10;
            renderSpanList(g, font, q.spans(), textX, curY, textX, maxW - 10, clipTop, clipBot, regions, scale);
            if (curY[0] > quoteY && quoteY <= clipBot && curY[0] >= clipTop) {
                g.fill(barX, quoteY, barX + 2, curY[0] - 1, ArchivePalette.TERM_DIM);
            }
        } else if (block instanceof RichBlock.Table t) {
            curY[0] = renderTable(g, font, t, x, curY[0], maxW, clipTop, clipBot, regions, scale);
        } else if (block instanceof RichBlock.Callout c) {
            curY[0] = renderCallout(g, font, c, x, curY[0], maxW, clipTop, clipBot, regions, scale, expandedKeys);
        } else if (block instanceof RichBlock.Details d) {
            curY[0] = renderDetails(g, font, d, x, curY[0], maxW, clipTop, clipBot, regions, scale, accentColor,
                    expandedKeys);
        } else if (block instanceof RichBlock.HotspotImage hi) {
            curY[0] = renderHotspotImage(g, hi, x, curY[0], clipTop, clipBot, regions);
        }
    }

    private static int renderHotspotImage(GuiGraphics g, RichBlock.HotspotImage hi, int x, int y, int clipTop,
                                          int clipBot, List<RichSpan.Region> regions) {
        if (y + hi.height() >= clipTop && y <= clipBot) {
            g.blit(imageResolver.apply(hi.image()), x, y, 0, 0, hi.width(), hi.height(), hi.width(), hi.height());
            for (RichBlock.Hotspot spot : hi.hotspots()) {
                int hx = x + spot.x();
                int hy = y + spot.y();
                g.fill(hx - 3, hy - 3, hx + 3, hy + 3, ArchivePalette.withAlpha(ArchivePalette.TERM_BRIGHT, 0xAA));
                g.renderOutline(hx - 3, hy - 3, 6, 6, ArchivePalette.TERM_BRIGHT);
                regions.add(new RichSpan.Region(hx - 5, hy - 5, hx + 5, hy + 5,
                        new RichSpan.Tip(spot.tooltip(), Style.EMPTY, spot.tooltip())));
            }
        }
        return y + hi.height();
    }

    private static int renderCallout(GuiGraphics g, Font font, RichBlock.Callout c, int x, int y, int maxW,
                                     int clipTop, int clipBot, List<RichSpan.Region> regions, float scale,
                                     Set<String> expandedKeys) {
        int color = calloutColor(c.type());
        String icon = calloutIcon(c.type());
        String title = c.title().isEmpty() ? capitalize(c.type()) : c.title();

        int innerX = x + 10;
        int innerMaxW = maxW - 16;
        int headY = y + 6;
        int bodyY = measureBlockList(font, c.children(), innerMaxW, headY + 12, scale, expandedKeys);
        int boxH = (bodyY - y) + 6;

        if (y + boxH >= clipTop && y <= clipBot) {
            int fillTop = Math.max(y, clipTop);
            int fillBot = Math.min(y + boxH, clipBot);
            g.fill(x, fillTop, x + maxW, fillBot, (color & 0xFFFFFF) | 0x18000000);
            g.fill(x, fillTop, x + 3, fillBot, color);
            if (headY >= clipTop && headY <= clipBot)
                g.drawString(font, icon + " §l" + title, innerX, headY, color, false);
        }
        renderBlockList(g, font, c.children(), innerX, headY + 12, innerMaxW, clipTop, clipBot, regions, scale,
                color, expandedKeys);
        return y + boxH;
    }

    private static void renderHeadingLike(GuiGraphics g, Font font, int level, List<RichSpan> spans, int x,
                                          int[] curY, int maxW, int clipTop, int clipBot,
                                          List<RichSpan.Region> regions, float scale, int accentColor) {
        int headY = curY[0];
        int lvl = Math.min(level, 3);
        List<RichSpan> styled = switch (lvl) {
            case 1 -> withHeadingStyle(spans);
            case 2 -> withSubheadingStyle(spans);
            default -> withH3Style(spans);
        };
        float hScale = scale * headingScale(level);
        renderSpanList(g, font, styled, x, curY, x, maxW, clipTop, clipBot, regions, hScale);
        if (level <= 1) {

            int barY = curY[0] + 3;
            if (barY + 1 >= clipTop && headY <= clipBot) {
                g.fill(x, barY, x + maxW, barY + 1, accentColor);
            }
        }
        curY[0] += GAP_HEADING_AFTER;
    }

    private static String collapseTrackingKey(String collapseKey) {
        return "HCOL:" + collapseKey;
    }

    private static int renderCollapsibleSection(GuiGraphics g, Font font, RichBlock.CollapsibleSection s, int x,
                                                int y, int maxW, int clipTop, int clipBot,
                                                List<RichSpan.Region> regions, float scale, int accentColor,
                                                Set<String> expandedKeys) {
        boolean collapsed = expandedKeys.contains(collapseTrackingKey(s.collapseKey()));
        int headY = y;
        int[] curY = { y };
        List<RichSpan> withArrow = new ArrayList<>(s.headingSpans().size() + 1);
        withArrow.add(new RichSpan.Text(collapsed ? "▸ " : "▾ ", Style.EMPTY));
        withArrow.addAll(s.headingSpans());
        renderHeadingLike(g, font, s.level(), withArrow, x, curY, maxW, clipTop, clipBot, regions, scale,
                accentColor);
        regions.add(new RichSpan.Region(x, headY, x + maxW, curY[0],
                new RichSpan.DetailsToggle(collapseTrackingKey(s.collapseKey()))));
        if (!collapsed) {
            curY[0] = renderBlockList(g, font, s.children(), x, curY[0], maxW, clipTop, clipBot, regions, scale,
                    accentColor, expandedKeys);
        }
        return curY[0];
    }

    private static int renderDetails(GuiGraphics g, Font font, RichBlock.Details d, int x, int y, int maxW,
                                     int clipTop, int clipBot, List<RichSpan.Region> regions, float scale,
                                     int accentColor, Set<String> expandedKeys) {
        boolean expanded = expandedKeys.contains(d.expandKey());
        int headH = 14;

        if (y + headH >= clipTop && y <= clipBot) {
            g.fill(x, Math.max(y, clipTop), x + maxW, Math.min(y + headH, clipBot), ArchivePalette.PANEL);
            if (y + 3 >= clipTop && y + 3 <= clipBot) {
                g.drawString(font, (expanded ? "§f▾ " : "§7▸ ") + "§l" + d.title(), x + 4, y + 3,
                        ArchivePalette.TERM_BRIGHT, false);
            }
        }
        regions.add(new RichSpan.Region(x, y, x + maxW, y + headH, new RichSpan.DetailsToggle(d.expandKey())));
        int curY = y + headH + (expanded ? 3 : 0);

        if (expanded) {
            curY = renderBlockList(g, font, d.children(), x + 10, curY, maxW - 10, clipTop, clipBot, regions, scale,
                    accentColor, expandedKeys);
            curY += 3;
        }
        return curY;
    }

    private static int renderTable(GuiGraphics g, Font font, RichBlock.Table t, int x, int y, int maxW,
                                   int clipTop, int clipBot, List<RichSpan.Region> regions, float scale) {
        int cols = t.header().size();
        for (List<List<RichSpan>> row : t.rows()) cols = Math.max(cols, row.size());
        if (cols == 0) return y;
        int colW = maxW / cols;

        int headerH = tableRowHeight(font, t.header(), colW, scale);
        if (y + headerH >= clipTop && y <= clipBot) {
            g.fill(x, Math.max(y, clipTop), x + maxW, Math.min(y + headerH, clipBot), ArchivePalette.PANEL);
        }
        for (int c = 0; c < t.header().size(); c++) {
            int[] cellY = { y + 3 };
            renderSpanList(g, font, t.header().get(c), x + c * colW + 4, cellY, x + c * colW + 4, colW - 8,
                    clipTop, clipBot, regions, scale);
        }
        int rowY = y + headerH;
        if (rowY >= clipTop && rowY <= clipBot) {
            g.fill(x, rowY, x + maxW, rowY + 1, ArchivePalette.BORDER);
        }
        rowY += 1;

        for (int r = 0; r < t.rows().size(); r++) {
            List<List<RichSpan>> row = t.rows().get(r);
            int rowH = tableRowHeight(font, row, colW, scale);
            if (r % 2 == 1 && rowY + rowH >= clipTop && rowY <= clipBot) {
                g.fill(x, Math.max(rowY, clipTop), x + maxW, Math.min(rowY + rowH, clipBot),
                        ArchivePalette.withAlpha(ArchivePalette.TERM, 0x14));
            }
            for (int c = 0; c < row.size(); c++) {
                int[] cellY = { rowY + 3 };
                renderSpanList(g, font, row.get(c), x + c * colW + 4, cellY, x + c * colW + 4, colW - 8,
                        clipTop, clipBot, regions, scale);
            }
            rowY += rowH;
        }
        return rowY;
    }

    private static int tableRowHeight(Font font, List<List<RichSpan>> cells, int colW, float scale) {
        int maxH = Math.round(LINE_H * scale);
        for (List<RichSpan> cell : cells) {
            int h = measureSpanListFrom(font, cell, Math.max(1, colW - 8), 0, scale);
            maxH = Math.max(maxH, h);
        }
        return maxH + 6;
    }

    private static int calloutColor(String type) {
        return switch (type) {
            case "warning", "warn" -> 0xFFE0A030;
            case "danger", "error" -> ArchivePalette.ALERT;
            case "tip", "success" -> ArchivePalette.TERM_HILITE;
            case "note", "info" -> ArchivePalette.TERM_BRIGHT;
            default -> ArchivePalette.TERM;
        };
    }

    private static String calloutIcon(String type) {
        return switch (type) {
            case "warning", "warn" -> "⚠";
            case "danger", "error" -> "⛔";
            case "tip", "success" -> "💡";
            case "note", "info" -> "ℹ";
            default -> "●";
        };
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private record HToken(String text, int color, RichSpan interactive) {

        HToken(String text, int color) {
            this(text, color, null);
        }
    }

    private static final Pattern CODE_ANNOTATION = Pattern
            .compile("\\[([^\\[\\]]+)]\\((wiki:[^()]+|quest:[^()]+|tip:[^()]+|https?://[^()\\s]+)\\)");

    private static final Set<String> JAVA_KEYWORDS = Set.of(
            "public", "private", "protected", "class", "interface", "extends", "implements", "static", "final",
            "void", "new", "return", "if", "else", "for", "while", "do", "switch", "case", "break", "continue",
            "try", "catch", "finally", "throw", "throws", "import", "package", "this", "super", "true", "false",
            "null", "default", "enum", "record", "sealed", "permits", "yield", "abstract", "instanceof",
            "int", "long", "double", "float", "boolean", "char", "byte", "short", "synchronized", "volatile",
            "transient", "native");

    private static final Set<String> JS_KEYWORDS = Set.of(
            "function", "const", "let", "var", "typeof", "instanceof", "in", "of", "new", "return", "if", "else",
            "for", "while", "do", "switch", "case", "break", "continue", "try", "catch", "finally", "throw",
            "import", "export", "from", "default", "class", "extends", "super", "this", "true", "false", "null",
            "undefined", "async", "await", "yield", "static", "get", "set", "delete", "void");

    private static final Set<String> TS_KEYWORDS = Set.of(
            "function", "const", "let", "var", "typeof", "instanceof", "in", "of", "new", "return", "if", "else",
            "for", "while", "do", "switch", "case", "break", "continue", "try", "catch", "finally", "throw",
            "import", "export", "from", "default", "class", "extends", "implements", "interface", "type",
            "super", "this", "true", "false", "null", "undefined", "async", "await", "yield", "static",
            "public", "private", "protected", "readonly", "enum", "namespace", "declare", "as");

    private static final Set<String> KOTLIN_KEYWORDS = Set.of(
            "fun", "val", "var", "class", "interface", "object", "companion", "override", "private", "public",
            "protected", "internal", "return", "if", "else", "for", "while", "do", "when", "is", "in", "true",
            "false", "null", "this", "super", "import", "package", "data", "sealed", "abstract", "open", "final",
            "suspend", "inline", "reified", "vararg", "lateinit");

    private static final Set<String> JSON_KEYWORDS = Set.of("true", "false", "null");

    private static Set<String> keywordsFor(String lang) {
        return switch (lang == null ? "" : lang.toLowerCase()) {
            case "java" -> JAVA_KEYWORDS;
            case "js", "javascript" -> JS_KEYWORDS;
            case "ts", "typescript" -> TS_KEYWORDS;
            case "kotlin" -> KOTLIN_KEYWORDS;
            case "json" -> JSON_KEYWORDS;
            default -> Set.of();
        };
    }

    private static boolean usesTemplateLiterals(String lang) {
        String l = lang == null ? "" : lang.toLowerCase();
        return l.equals("js") || l.equals("javascript") || l.equals("ts") || l.equals("typescript");
    }

    private static boolean usesAnnotations(String lang) {
        String l = lang == null ? "" : lang.toLowerCase();
        return l.equals("java") || l.equals("kotlin");
    }

    private static final Set<String> HIGHLIGHT_LANGS = Set.of("java", "js", "javascript", "json", "kotlin", "ts",
            "typescript");

    private static List<HToken> highlightLine(String lang, String line) {
        List<HToken> out = new ArrayList<>();
        Matcher m = CODE_ANNOTATION.matcher(line);
        int last = 0;
        while (m.find()) {
            if (m.start() > last) out.addAll(highlightPlain(lang, line.substring(last, m.start())));
            String label = m.group(1);
            String target = m.group(2);
            if (target.startsWith("tip:")) {
                out.add(new HToken(label, ArchivePalette.TERM_HILITE,
                        new RichSpan.Tip(label, Style.EMPTY, target.substring(4))));
            } else {
                out.add(new HToken(label, ArchivePalette.TERM_BRIGHT,
                        new RichSpan.Link(label, Style.EMPTY, target)));
            }
            last = m.end();
        }
        if (last < line.length()) out.addAll(highlightPlain(lang, line.substring(last)));
        return out;
    }

    private static List<HToken> highlightPlain(String lang, String line) {
        List<HToken> out = new ArrayList<>();
        if (lang == null || !HIGHLIGHT_LANGS.contains(lang.toLowerCase())) {
            out.add(new HToken(line, 0xFFE0E0E0));
            return out;
        }

        Set<String> keywords = keywordsFor(lang);
        boolean templateLiterals = usesTemplateLiterals(lang);
        boolean annotations = usesAnnotations(lang);

        int i = 0, len = line.length();
        StringBuilder buf = new StringBuilder();
        while (i < len) {
            char c = line.charAt(i);
            if (c == '/' && i + 1 < len && line.charAt(i + 1) == '/') {
                flushPlain(buf, out);
                out.add(new HToken(line.substring(i), 0xFF6A9955));
                break;
            }
            if (c == '"' || c == '\'' || (c == '`' && templateLiterals)) {
                flushPlain(buf, out);
                int end = i + 1;
                while (end < len && line.charAt(end) != c) end++;
                end = Math.min(end + 1, len);
                out.add(new HToken(line.substring(i, end), 0xFFCE9178));
                i = end;
                continue;
            }
            if (c == '@' && annotations && i + 1 < len &&
                    (Character.isLetter(line.charAt(i + 1)) || line.charAt(i + 1) == '_')) {
                flushPlain(buf, out);
                int start = i;
                i++;
                while (i < len && (Character.isLetterOrDigit(line.charAt(i)) || line.charAt(i) == '_')) i++;
                out.add(new HToken(line.substring(start, i), 0xFFDCDCAA));
                continue;
            }
            if (Character.isLetter(c) || c == '_') {
                int start = i;
                while (i < len && (Character.isLetterOrDigit(line.charAt(i)) || line.charAt(i) == '_')) i++;
                String word = line.substring(start, i);
                if (keywords.contains(word)) {
                    flushPlain(buf, out);
                    out.add(new HToken(word, 0xFF569CD6));
                } else {
                    buf.append(word);
                }
                continue;
            }
            if (Character.isDigit(c)) {
                int start = i;
                while (i < len && (Character.isDigit(line.charAt(i)) || line.charAt(i) == '.')) i++;
                flushPlain(buf, out);
                out.add(new HToken(line.substring(start, i), 0xFFB5CEA8));
                continue;
            }
            buf.append(c);
            i++;
        }
        flushPlain(buf, out);
        return out;
    }

    private static void flushPlain(StringBuilder buf, List<HToken> out) {
        if (!buf.isEmpty()) {
            out.add(new HToken(buf.toString(), 0xFFE0E0E0));
            buf.setLength(0);
        }
    }

    private static int renderCodeBlock(GuiGraphics g, Font font, String lang, String code, int x, int y, int maxW,
                                       int clipTop, int clipBot, List<RichSpan.Region> regions) {
        List<List<HToken>> visualLines = wrapCodeLines(font, lang, code, maxW);
        int lineH = 10;
        int boxH = visualLines.size() * lineH + 6;
        int btnW = font.width("⎘") + 8;

        if (y + boxH >= clipTop && y <= clipBot) {
            g.fill(x, y, x + maxW, y + boxH, ArchivePalette.BG);
            g.fill(x, y, x + 1, y + boxH, ArchivePalette.BORDER);
            g.fill(x + maxW - btnW - 2, y + 1, x + maxW - 2, y + 1 + font.lineHeight + 2,
                    ArchivePalette.withAlpha(ArchivePalette.TERM_BRIGHT, 0x22));
            g.drawString(font, "§7⎘", x + maxW - btnW - 2 + 4, y + 3, ArchivePalette.TERM_BRIGHT, false);
            for (int li = 0; li < visualLines.size(); li++) {
                int ly = y + 3 + li * lineH;
                int cx = x + 4;
                for (HToken tok : visualLines.get(li)) {
                    int tokW = font.width(tok.text());
                    if (ly >= clipTop && ly + 8 <= clipBot) {
                        g.drawString(font, tok.text(), cx, ly, tok.color(), false);
                        if (tok.interactive() != null) {
                            g.fill(cx, ly + 9, cx + tokW, ly + 10, tok.color());
                        }
                    }
                    if (tok.interactive() != null) {
                        regions.add(new RichSpan.Region(cx, ly, cx + tokW, ly + lineH, tok.interactive()));
                    }
                    cx += tokW;
                }
            }
        } else {

            for (int li = 0; li < visualLines.size(); li++) {
                int ly = y + 3 + li * lineH;
                int cx = x + 4;
                for (HToken tok : visualLines.get(li)) {
                    int tokW = font.width(tok.text());
                    if (tok.interactive() != null) {
                        regions.add(new RichSpan.Region(cx, ly, cx + tokW, ly + lineH, tok.interactive()));
                    }
                    cx += tokW;
                }
            }
        }
        regions.add(new RichSpan.Region(x + maxW - btnW - 2, y + 1, x + maxW - 2, y + 1 + font.lineHeight + 2,
                new RichSpan.CodeCopy(code)));
        return y + boxH;
    }

    private static List<List<HToken>> wrapCodeLines(Font font, String lang, String code, int maxW) {
        int innerW = Math.max(8, maxW - 8);

        int btnReserve = font.width("⎘") + 8 + 10;
        int firstLineW = Math.max(8, innerW - btnReserve);
        List<List<HToken>> visualLines = new ArrayList<>();
        String[] rawLines = code.split("\n", -1);
        for (int r = 0; r < rawLines.length; r++) {
            int flw = (r == 0) ? firstLineW : -1;
            visualLines.addAll(wrapHighlightedLine(font, highlightLine(lang, rawLines[r]), innerW, flw));
        }
        return visualLines;
    }

    private static List<List<HToken>> wrapHighlightedLine(Font font, List<HToken> tokens, int maxW,
                                                          int firstLineMaxW) {
        List<List<HToken>> lines = new ArrayList<>();
        List<HToken> current = new ArrayList<>();
        int curW = 0;
        int curMaxW = firstLineMaxW > 0 ? firstLineMaxW : maxW;
        for (HToken tok : tokens) {
            String remaining = tok.text();
            while (!remaining.isEmpty()) {
                int w = font.width(remaining);
                if (curW + w <= curMaxW) {
                    current.add(new HToken(remaining, tok.color(), tok.interactive()));
                    curW += w;
                    remaining = "";
                } else if (curW == 0) {
                    int fitLen = Math.max(1, maxFitLength(font, remaining, curMaxW));
                    current.add(new HToken(remaining.substring(0, fitLen), tok.color(), tok.interactive()));
                    lines.add(current);
                    current = new ArrayList<>();
                    curW = 0;
                    curMaxW = maxW;
                    remaining = remaining.substring(fitLen);
                } else {
                    lines.add(current);
                    current = new ArrayList<>();
                    curW = 0;
                    curMaxW = maxW;
                }
            }
        }
        lines.add(current);
        return lines;
    }

    private static int maxFitLength(Font font, String text, int maxW) {
        int lo = 0, hi = text.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            if (font.width(text.substring(0, mid)) <= maxW) lo = mid;
            else hi = mid - 1;
        }
        return lo;
    }

    private static int measureBlockList(Font font, List<RichBlock> blocks, int maxW, int y, float scale,
                                        Set<String> expandedKeys) {
        boolean first = true;
        float pendingScaleMult = 1f;
        for (RichBlock block : blocks) {
            if (block instanceof RichBlock.ScaleDirective sd) {
                pendingScaleMult = sd.multiplier();
                continue;
            }
            if (block instanceof RichBlock.Blank) {
                y += GAP_BLANK;
                first = false;
                continue;
            }
            if (!first) y += gapBefore(block);
            first = false;
            y = measureOneBlock(font, block, maxW, y, scale * pendingScaleMult, expandedKeys);
            pendingScaleMult = 1f;
        }
        return y;
    }

    private static int measureOneBlock(Font font, RichBlock block, int maxW, int y, float scale,
                                       Set<String> expandedKeys) {
        if (block instanceof RichBlock.Heading h) {
            List<RichSpan> styled = switch (Math.min(h.level(), 3)) {
                case 1 -> withHeadingStyle(h.spans());
                case 2 -> withSubheadingStyle(h.spans());
                default -> withH3Style(h.spans());
            };
            y = measureSpanListFrom(font, styled, maxW, y, scale * headingScale(h.level()));
            y += GAP_HEADING_AFTER;
        } else if (block instanceof RichBlock.CollapsibleSection s) {
            List<RichSpan> styled = switch (Math.min(s.level(), 3)) {
                case 1 -> withHeadingStyle(s.headingSpans());
                case 2 -> withSubheadingStyle(s.headingSpans());
                default -> withH3Style(s.headingSpans());
            };
            y = measureSpanListFrom(font, styled, maxW, y, scale * headingScale(s.level()));
            y += GAP_HEADING_AFTER;
            if (!expandedKeys.contains(collapseTrackingKey(s.collapseKey()))) {
                y = measureBlockList(font, s.children(), maxW, y, scale, expandedKeys);
            }
        } else if (block instanceof RichBlock.ListItem li) {
            y = measureSpanListFrom(font, li.spans(), maxW - li.indent(), y, scale);
        } else if (block instanceof RichBlock.Checklist cl) {
            y = measureSpanListFrom(font, cl.spans(), maxW - cl.indent(), y, scale);
        } else if (block instanceof RichBlock.Paragraph p) {
            y = measureSpanListFrom(font, p.spans(), maxW, y, scale);
        } else if (block instanceof RichBlock.Rule) {
            y += 8;
        } else if (block instanceof RichBlock.CodeBlock cb) {
            y += wrapCodeLines(font, cb.lang(), cb.code(), maxW).size() * 10 + 6;
        } else if (block instanceof RichBlock.Quote q) {
            y = measureSpanListFrom(font, q.spans(), maxW - 10, y, scale);
        } else if (block instanceof RichBlock.Table t) {
            int cols = t.header().size();
            for (List<List<RichSpan>> row : t.rows()) cols = Math.max(cols, row.size());
            int colW = cols > 0 ? Math.max(1, maxW / cols) : maxW;
            y += tableRowHeight(font, t.header(), colW, scale) + 1;
            for (List<List<RichSpan>> row : t.rows()) {
                y += tableRowHeight(font, row, colW, scale);
            }
        } else if (block instanceof RichBlock.Callout c) {
            int inner = measureBlockList(font, c.children(), maxW - 16, y + 12, scale, expandedKeys);
            y = inner + 6;
        } else if (block instanceof RichBlock.Details d) {
            y += 14;
            if (expandedKeys.contains(d.expandKey())) {
                y += 3;
                y = measureBlockList(font, d.children(), maxW - 10, y, scale, expandedKeys);
                y += 3;
            }
        } else if (block instanceof RichBlock.HotspotImage hi) {
            y += hi.height();
        }
        return y;
    }

    private static int gapBefore(RichBlock block) {
        if (block instanceof RichBlock.Heading h) return h.level() <= 2 ? GAP_HEADING_BEFORE : GAP_HEADING_BEFORE - 3;
        if (block instanceof RichBlock.CollapsibleSection s)
            return s.level() <= 2 ? GAP_HEADING_BEFORE : GAP_HEADING_BEFORE - 3;
        if (block instanceof RichBlock.ListItem) return GAP_LIST_ITEM;
        if (block instanceof RichBlock.Rule) return 0;
        if (block instanceof RichBlock.ScaleDirective) return 0;
        return GAP_PARAGRAPH;
    }

    private static List<RichSpan> withHeadingStyle(List<RichSpan> spans) {
        List<RichSpan> out = new ArrayList<>(spans.size());
        for (RichSpan s : spans) {
            if (s instanceof RichSpan.Text t) {
                out.add(new RichSpan.Text(t.text(), t.style().withBold(true).withColor(
                        net.minecraft.network.chat.TextColor.fromRgb(ArchivePalette.TERM_BRIGHT & 0xFFFFFF)),
                        t.background(), t.copyText(), t.scale()));
            } else {
                out.add(s);
            }
        }
        return out;
    }

    private static List<RichSpan> withSubheadingStyle(List<RichSpan> spans) {
        List<RichSpan> out = new ArrayList<>(spans.size());
        for (RichSpan s : spans) {
            if (s instanceof RichSpan.Text t) {
                out.add(new RichSpan.Text(t.text(), t.style().withBold(true).withColor(
                        net.minecraft.network.chat.TextColor.fromRgb(ArchivePalette.TERM & 0xFFFFFF)),
                        t.background(), t.copyText(), t.scale()));
            } else {
                out.add(s);
            }
        }
        return out;
    }

    private static List<RichSpan> withStrikethroughStyle(List<RichSpan> spans) {
        List<RichSpan> out = new ArrayList<>(spans.size());
        for (RichSpan s : spans) {
            if (s instanceof RichSpan.Text t) {
                out.add(new RichSpan.Text(t.text(), t.style().withStrikethrough(true)
                        .withColor(net.minecraft.network.chat.TextColor.fromRgb(ArchivePalette.TEXT_FAINT &
                                0xFFFFFF)),
                        t.background(), t.copyText(), t.scale()));
            } else {
                out.add(s);
            }
        }
        return out;
    }

    private static List<RichSpan> withH3Style(List<RichSpan> spans) {
        List<RichSpan> out = new ArrayList<>(spans.size());
        for (RichSpan s : spans) {
            if (s instanceof RichSpan.Text t) {
                out.add(new RichSpan.Text(t.text(), t.style().withBold(false).withColor(
                        net.minecraft.network.chat.TextColor.fromRgb(ArchivePalette.TERM_DIM & 0xFFFFFF)),
                        t.background(), t.copyText(), t.scale()));
            } else {
                out.add(s);
            }
        }
        return out;
    }

    private static void renderSpanList(GuiGraphics g, Font font, List<RichSpan> spans,
                                       int x, int[] curY, int originX, int maxW,
                                       int clipTop, int clipBot, List<RichSpan.Region> regions, float scale) {
        int lineH = Math.round(LINE_H * scale);
        int curX = x;
        for (RichSpan span : spans) {
            if (span instanceof RichSpan.Image img) {
                if (curX > originX) {
                    curX = originX;
                    curY[0] += lineH;
                }
                if (curY[0] >= clipTop && curY[0] + img.h() <= clipBot)
                    g.blit(imageResolver.apply(img.texture()),
                            curX, curY[0], 0, 0, img.w(), img.h(), img.w(), img.h());
                regions.add(new RichSpan.Region(curX, curY[0], curX + img.w(), curY[0] + img.h(), img));
                curY[0] += img.h() + 2;
                curX = originX;
            } else if (span instanceof RichSpan.ItemIcon icon) {
                if (curX + 18 > originX + maxW && curX > originX) {
                    curX = originX;
                    curY[0] += lineH;
                }
                int iconY = curY[0] - (16 - lineH) / 2;
                if (iconY >= clipTop && iconY + 16 <= clipBot) {
                    Item item = ForgeRegistries.ITEMS.getValue(icon.itemId());
                    if (item != null) {
                        try {
                            g.renderItem(new ItemStack(item), curX, iconY);
                        } catch (Exception ignored) {}
                    } else {

                        g.fill(curX, iconY, curX + 16, iconY + 16, ArchivePalette.withAlpha(ArchivePalette.ALERT,
                                0x33));
                        g.renderOutline(curX, iconY, 16, 16, ArchivePalette.ALERT);
                        g.drawCenteredString(font, "?", curX + 8, iconY + 4, ArchivePalette.ALERT);
                    }
                }
                regions.add(new RichSpan.Region(curX, iconY, curX + 16, iconY + 16, icon));
                curX += 18;
            } else if (span instanceof RichSpan.Text t) {
                int[] pos = renderWords(g, font, t.text(), t.style(), ArchivePalette.WHITE,
                        curX, curY[0], originX, maxW, clipTop, clipBot, regions, t, scale);
                curX = pos[0];
                curY[0] = pos[1];
            } else if (span instanceof RichSpan.Link l) {
                Style ls = l.style().withColor(ArchivePalette.TERM_BRIGHT).withUnderlined(true);
                int[] pos = renderWords(g, font, l.label(), ls, ArchivePalette.TERM_BRIGHT,
                        curX, curY[0], originX, maxW, clipTop, clipBot, regions, l, scale);
                curX = pos[0];
                curY[0] = pos[1];
            } else if (span instanceof RichSpan.Tip t) {
                Style ts = t.style().withColor(ArchivePalette.TERM_HILITE).withUnderlined(true);
                int[] pos = renderWords(g, font, t.label(), ts, ArchivePalette.TERM_HILITE,
                        curX, curY[0], originX, maxW, clipTop, clipBot, regions, t, scale);
                curX = pos[0];
                curY[0] = pos[1];
            }
        }
        if (curX > originX) curY[0] += lineH;
    }

    private static int measureSpanList(Font font, List<RichSpan> spans, int maxW, float scale) {
        return measureSpanListFrom(font, spans, maxW, 0, scale);
    }

    private static int measureSpanListFrom(Font font, List<RichSpan> spans, int maxW, int startY, float scale) {
        int lineH = Math.round(LINE_H * scale);
        int curX = 0, curY = startY;
        for (RichSpan span : spans) {
            if (span instanceof RichSpan.Image img) {
                if (curX > 0) curY += lineH;
                curY += img.h() + 2;
                curX = 0;
            } else if (span instanceof RichSpan.ItemIcon) {
                if (curX + 18 > maxW && curX > 0) {
                    curX = 0;
                    curY += lineH;
                }
                curX += 18;
            } else if (span instanceof RichSpan.Text t) {
                int[] p = measureWords(font, t.text(), t.style(), curX, curY, 0, maxW, scale * t.scale());
                curX = p[0];
                curY = p[1];
            } else if (span instanceof RichSpan.Link l) {
                int[] p = measureWords(font, l.label(), l.style(), curX, curY, 0, maxW, scale);
                curX = p[0];
                curY = p[1];
            } else if (span instanceof RichSpan.Tip t) {
                int[] p = measureWords(font, t.label(), t.style(), curX, curY, 0, maxW, scale);
                curX = p[0];
                curY = p[1];
            }
        }
        return curY + (curX > 0 ? lineH : 0);
    }

    private record LegacyStyle(Style style, boolean themed) {}

    private static int[] renderWords(
                                     GuiGraphics g, Font font,
                                     String text, Style style, int fallbackColor,
                                     int curX, int curY,
                                     int originX, int maxW,
                                     int clipTop, int clipBot,
                                     List<RichSpan.Region> regions, RichSpan source, float scale) {
        if (text == null || text.isEmpty()) return new int[] { curX, curY };

        if (source instanceof RichSpan.Text t) scale *= t.scale();

        int lineH = Math.round(LINE_H * scale);
        String inlineCodeText = source instanceof RichSpan.Text t ? t.copyText() : null;
        boolean interactive = source instanceof RichSpan.Link || source instanceof RichSpan.Tip ||
                inlineCodeText != null;
        RichSpan regionPayload = inlineCodeText != null ? new RichSpan.CodeCopy(inlineCodeText) : source;
        int background = source instanceof RichSpan.Text t ? t.background() : 0;

        String[] lines = text.split("\n", -1);
        LegacyStyle running = new LegacyStyle(style, false);
        for (int li = 0; li < lines.length; li++) {
            if (li > 0) {
                curX = originX;
                curY += lineH;
            }
            String line = lines[li];
            String[] tokens = tokenize(line);

            StringBuilder run = new StringBuilder();
            LegacyStyle runStyle = running;
            int runStartX = curX;

            for (String token : tokens) {
                if (token.isBlank() && curX == originX) continue;
                LegacyStyle newStyle = applyLegacyCodes(running, token);
                int tokW = Math.round(font.width(Component.literal(token).withStyle(newStyle.style())) *
                        boldScale(scale, newStyle.style().isBold()));
                if (curX + tokW > originX + maxW && curX > originX) {
                    flushRun(g, font, run, runStyle, fallbackColor, runStartX, curY, clipTop, clipBot,
                            boldScale(scale, runStyle.style().isBold()), background);
                    curX = originX;
                    curY += lineH;
                    runStartX = curX;
                }

                if (!newStyle.equals(runStyle) && !run.isEmpty()) {
                    flushRun(g, font, run, runStyle, fallbackColor, runStartX, curY, clipTop, clipBot,
                            boldScale(scale, runStyle.style().isBold()), background);
                    runStartX = curX;
                }
                running = newStyle;
                runStyle = newStyle;
                run.append(token);

                if (interactive && !token.isBlank()) {
                    regions.add(new RichSpan.Region(curX, curY, curX + tokW, curY + lineH, regionPayload));
                }
                curX += tokW;
            }
            flushRun(g, font, run, runStyle, fallbackColor, runStartX, curY, clipTop, clipBot,
                    boldScale(scale, runStyle.style().isBold()), background);
        }
        return new int[] { curX, curY };
    }

    private static void flushRun(GuiGraphics g, Font font, StringBuilder run, LegacyStyle runStyle,
                                 int fallbackColor, int runStartX, int curY, int clipTop, int clipBot, float scale,
                                 int background) {
        if (run.isEmpty()) return;
        if (curY >= clipTop && curY + 8 <= clipBot) {
            MutableComponent comp = Component.literal(run.toString()).withStyle(runStyle.style());
            int color;
            if (runStyle.style().getColor() != null) {
                color = 0xFF000000 | runStyle.style().getColor().getValue();
            } else if (runStyle.themed()) {

                color = 0xFF000000 | (ArchivePalette.TERM & 0xFFFFFF);
            } else {
                color = fallbackColor;
            }
            int w = Math.round(font.width(comp) * scale);
            if (background != 0) {
                g.fill(runStartX - 1, curY - 1, runStartX + w + 1, curY + 9, background);
            }
            if (scale == 1.0f) {
                g.drawString(font, comp, runStartX, curY, color, false);
            } else {
                g.pose().pushPose();
                g.pose().translate(runStartX, curY, 0);
                g.pose().scale(scale, scale, 1f);
                g.drawString(font, comp, 0, 0, color, false);
                g.pose().popPose();
            }
        }
        run.setLength(0);
    }

    private static int[] measureWords(Font font, String text, Style style, int curX, int curY, int originX, int maxW,
                                      float scale) {
        if (text == null || text.isEmpty()) return new int[] { curX, curY };
        int lineH = Math.round(LINE_H * scale);
        String[] lines = text.split("\n", -1);
        LegacyStyle running = new LegacyStyle(style, false);
        for (int li = 0; li < lines.length; li++) {
            if (li > 0) {
                curX = originX;
                curY += lineH;
            }
            for (String token : tokenize(lines[li])) {
                if (token.isBlank() && curX == originX) continue;
                running = applyLegacyCodes(running, token);
                int tokW = Math.round(font.width(Component.literal(token).withStyle(running.style())) *
                        boldScale(scale, running.style().isBold()));
                if (curX + tokW > originX + maxW && curX > originX) {
                    curX = originX;
                    curY += lineH;
                }
                curX += tokW;
            }
        }
        return new int[] { curX, curY };
    }

    private static LegacyStyle applyLegacyCodes(LegacyStyle base, String token) {
        Style style = base.style();
        boolean themed = base.themed();
        int len = token.length();
        for (int i = 0; i < len - 1; i++) {
            if (token.charAt(i) != '§') continue;
            char code = Character.toLowerCase(token.charAt(i + 1));
            if (code == 't') {
                themed = true;
                continue;
            }
            net.minecraft.ChatFormatting fmt = net.minecraft.ChatFormatting.getByCode(token.charAt(i + 1));
            if (fmt == null) continue;
            if (fmt == net.minecraft.ChatFormatting.RESET) {
                style = Style.EMPTY;
                themed = false;
            } else if (fmt.isColor()) {
                style = style.withColor(fmt);
                themed = false;
            } else {
                style = switch (fmt) {
                    case BOLD -> style.withBold(true);
                    case ITALIC -> style.withItalic(true);
                    case UNDERLINE -> style.withUnderlined(true);
                    case STRIKETHROUGH -> style.withStrikethrough(true);
                    case OBFUSCATED -> style.withObfuscated(true);
                    default -> style;
                };
            }
        }
        return new LegacyStyle(style, themed);
    }

    private static String[] tokenize(String s) {
        if (s.isEmpty()) return new String[] { "" };
        List<String> tokens = new ArrayList<>();
        int i = 0, len = s.length();
        while (i < len) {
            int start = i;
            while (i < len && s.charAt(i) != ' ') i++;
            while (i < len && s.charAt(i) == ' ') i++;
            tokens.add(s.substring(start, i));
        }
        return tokens.toArray(String[]::new);
    }
}
