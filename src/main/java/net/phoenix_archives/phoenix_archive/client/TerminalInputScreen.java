package net.phoenix_archives.phoenix_archive.client;

import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultilineTextField;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.phoenix_archives.phoenix_archive.client.rich.ArchiveMarkdown;
import net.phoenixvine.wiki.client.rich.RichBlock;
import net.phoenixvine.wiki.client.rich.RichSpan;
import net.phoenixvine.wiki.client.rich.WikiRichTextRenderer;
import net.phoenixvine.wiki.theme.PhoenixTheme;

import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class TerminalInputScreen extends Screen {

    private final Screen parent;
    private final String titleLabel;
    private final String initialValue;
    private final Consumer<String> onCommit;

    private CustomTextArea textArea;
    private String liveValue;

    /**
     * Live markdown preview shown alongside the editor -- re-parsed only when the text actually
     * changes (checked once per render), not every frame, since parsing runs on the render thread.
     */
    private List<RichBlock> previewBlocks = List.of();
    private String previewSourceText = null;
    private int previewX, previewY, previewW, previewH;
    private List<RichSpan.Region> previewRegions = List.of();
    private final Set<String> previewExpandedKeys = new HashSet<>();

    public TerminalInputScreen(Screen parent, String titleLabel, String initialValue, Consumer<String> onCommit) {
        super(Component.literal("Terminal Input Unit"));
        this.parent = parent;
        this.titleLabel = titleLabel;
        this.initialValue = initialValue;
        this.onCommit = onCommit;
    }

    @Override
    protected void init() {
        this.clearWidgets();
        ArchivePalette.refresh(PhoenixTheme.current());
        String startingValue = (liveValue != null) ? liveValue : initialValue;

        // Split the body left/right: editable text area on the left, a live-rendered markdown
        // preview of the same text on the right, so formatting is visible while still typing
        // instead of only after committing and reopening the entry.
        int bodyY = 59, bodyH = this.height - 104;
        int totalBodyW = this.width - 40;
        int editorW = totalBodyW / 2 - 4;
        this.textArea = new CustomTextArea(20, bodyY, editorW, bodyH, Component.empty());
        this.textArea.setValue(startingValue);
        this.addRenderableWidget(textArea);

        previewX = 20 + editorW + 8;
        previewY = bodyY;
        previewW = totalBodyW - editorW - 8;
        previewH = bodyH;
        previewSourceText = null;

        int startX = 20;
        int startY = 12;
        int btnW = 14;

        String colorCodes = "0123456789abcdef";
        String[] colorNames = { "Black", "Dark Blue", "Dark Green", "Dark Aqua", "Dark Red", "Dark Purple",
                "Gold", "Gray", "Dark Gray", "Blue", "Green", "Aqua", "Red", "Light Purple", "Yellow", "White" };
        for (int idx = 0; idx < colorCodes.length(); idx++) {
            char c = colorCodes.charAt(idx);
            this.addRenderableWidget(Button.builder(Component.literal("§" + c + "█"), b -> insertCode("&" + c))
                    .bounds(startX, startY, btnW, 14)
                    .tooltip(Tooltip.create(Component.literal(colorNames[idx])))
                    .build());
            startX += btnW + 2;
        }

        startX += 4;

        this.addRenderableWidget(Button.builder(
                Component.literal("T").withStyle(Style.EMPTY.withColor(
                        TextColor.fromRgb(ArchivePalette.TERM & 0xFFFFFF))),
                b -> insertCode("&t"))
                .bounds(startX, startY, btnW, 14)
                .tooltip(Tooltip.create(Component.literal(
                        "Theme Color -- live, tracks the suite's current theme (animated themes too), " +
                                "unlike the fixed colors to the left")))
                .build());
        startX += btnW + 4;

        String formatCodes = "lmnork";
        String[] formatNames = { "Bold", "Strikethrough", "Underline", "Italic", "Reset formatting",
                "Obfuscated (scrambles the text)" };
        for (int idx = 0; idx < formatCodes.length(); idx++) {
            char c = formatCodes.charAt(idx);
            String label = (c == 'r') ? "X" : (c == 'k') ? "?" : "§" + c + "█";
            this.addRenderableWidget(Button.builder(Component.literal(label), b -> insertCode("&" + c))
                    .bounds(startX, startY, btnW, 14)
                    .tooltip(Tooltip.create(Component.literal(formatNames[idx])))
                    .build());
            startX += btnW + 2;
        }

        startX += 4;
        this.addRenderableWidget(Button.builder(Component.literal("§b[THEME]"), b -> applyThemeToColors())
                .bounds(startX, startY, 46, 14)
                .tooltip(Tooltip.create(Component.literal(
                        "Recolor every &0-&f code above to the suite's current theme (dark codes -> theme " +
                                "dim accent, bright codes -> theme bright accent). Formatting codes and " +
                                "existing &#hex codes are left alone.")))
                .build());

        int mdY = startY + 16;
        int mdX = 20;

        String[][] mdSnippets = {
                { "H1", "# ", "Heading" },
                { "-", "- ", "Bullet list item" },
                { "1.", "1. ", "Numbered list item" },
                { "[x]", "- [ ] ", "Checklist item" },
                { ">", "> ", "Blockquote" },
                { "---", "\n---\n", "Horizontal rule" },
        };
        for (String[] entry : mdSnippets) {
            String label = entry[0];
            String snippet = entry[1];
            String tooltip = entry[2];
            int w = this.font.width(label) + 8;
            this.addRenderableWidget(Button.builder(Component.literal(label), b -> insertCode(snippet))
                    .bounds(mdX, mdY, w, 14)
                    .tooltip(Tooltip.create(Component.literal(tooltip)))
                    .build());
            mdX += w + 2;
        }

        mdX += 4;
        this.addRenderableWidget(Button.builder(Component.literal("`c`"), b -> insertMarkdownWrap("`", "`"))
                .bounds(mdX, mdY, 26, 14)
                .tooltip(Tooltip.create(Component.literal("Inline code")))
                .build());
        mdX += 28;
        this.addRenderableWidget(Button.builder(Component.literal("```"), b -> insertMarkdownWrap("\n```\n", "\n```\n"))
                .bounds(mdX, mdY, 26, 14)
                .tooltip(Tooltip.create(Component.literal("Code block")))
                .build());
        mdX += 28;
        this.addRenderableWidget(Button
                .builder(Component.literal(":::"), b -> insertMarkdownWrap(":::spoiler Title\n", "\n:::\n"))
                .bounds(mdX, mdY, 26, 14)
                .tooltip(Tooltip.create(Component.literal("Spoiler / details block")))
                .build());
        mdX += 28;
        this.addRenderableWidget(
                Button.builder(Component.literal("!"), b -> insertMarkdownWrap(":::warning\n", "\n:::\n"))
                        .bounds(mdX, mdY, 18, 14)
                        .tooltip(Tooltip.create(Component.literal("Warning callout")))
                        .build());
        mdX += 20;
        this.addRenderableWidget(
                Button.builder(Component.literal("[url]"), b -> insertMarkdownWrap("[", "](https://)"))
                        .bounds(mdX, mdY, 34, 14)
                        .tooltip(Tooltip.create(Component.literal(
                                "Link -- the target must start with http://, https://, wiki:, or quest: or it " +
                                        "won't render as clickable")))
                        .build());

        if (net.minecraftforge.fml.ModList.get().isLoaded("phoenix_chromatic_codes")) {
            this.addRenderableWidget(Button.builder(Component.literal("§z[ CHROMATIC_OS ]"), b -> {
                if (this.minecraft != null) {
                    liveValue = this.textArea.getValue();
                    this.minecraft.setScreen(new ChromaticSelectorScreen(this));
                }
            }).bounds(this.width - 110, 12, 100, 14).build());
        }

        this.addRenderableWidget(Button.builder(Component.literal("§2[ COMMIT_CHANGES ]"), b -> {
            this.onCommit.accept(this.textArea.getValue());
            if (this.minecraft != null) {
                this.minecraft.setScreen(parent);
            }
        }).bounds(this.width / 2 - 60, this.height - 35, 120, 20).build());

        this.setInitialFocus(textArea);
    }

    public void applyThemeToColors() {
        if (this.textArea == null) return;
        String text = this.textArea.getValue();
        String brightHex = String.format("%06X", ArchivePalette.TERM_BRIGHT & 0xFFFFFF);
        String dimHex = String.format("%06X", ArchivePalette.TERM_DIM & 0xFFFFFF);

        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            char next = (i + 1 < text.length()) ? Character.toLowerCase(text.charAt(i + 1)) : '\0';
            if (c == '&' && "89abcdef".indexOf(next) >= 0) {
                out.append("&#").append(brightHex);
                i++;
            } else if (c == '&' && "01234567".indexOf(next) >= 0) {
                out.append("&#").append(dimHex);
                i++;
            } else {
                out.append(c);
            }
        }

        String updated = out.toString();
        if (updated.length() <= CustomTextArea.MAX_LENGTH) {
            this.setInitialFocus(this.textArea);
            this.textArea.setValue(updated);
            this.liveValue = updated;
        }
    }

    @Override
    protected void repositionElements() {
        if (this.textArea != null) this.liveValue = this.textArea.getValue();
        this.init();
    }

    public void receiveCode(String code) {
        if (this.textArea != null) {
            this.insertCode(code);
            this.liveValue = this.textArea.getValue();
        } else {
            liveValue = (liveValue != null ? liveValue : initialValue) + code;
        }
    }

    public void insertCode(String code) {
        if (this.textArea != null && code != null) {
            this.setInitialFocus(this.textArea);
            this.textArea.forceInsertBypassingFilters(code);
        }
    }

    public void insertMarkdownWrap(String prefix, String suffix) {
        if (this.textArea != null) {
            this.setInitialFocus(this.textArea);
            this.textArea.insertWrap(prefix, suffix);
            this.liveValue = this.textArea.getValue();
        }
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        ArchivePalette.refresh(PhoenixTheme.current());
        this.renderBackground(graphics);
        graphics.fill(10, 10, this.width - 10, this.height - 10, ArchivePalette.BG_SCRIM);
        graphics.renderOutline(10, 10, this.width - 20, this.height - 20, ArchivePalette.TERM_BRIGHT);
        graphics.drawString(this.font, titleLabel + " // ADDR: 0x7FFA", 20, 46, ArchivePalette.TERM);
        renderPreview(graphics);
        super.render(graphics, mouseX, mouseY, partialTicks);
    }

    private void renderPreview(GuiGraphics g) {
        if (this.textArea == null) return;
        String text = this.textArea.getValue();
        if (!text.equals(previewSourceText)) {
            previewBlocks = text.isEmpty() ? List.of() : ArchiveMarkdown.parse(text);
            previewSourceText = text;
        }

        g.fill(previewX - 2, previewY - 2, previewX + previewW + 2, previewY + previewH + 2, ArchivePalette.BG);
        g.renderOutline(previewX - 2, previewY - 2, previewW + 4, previewH + 4, ArchivePalette.BORDER);
        g.drawString(this.font, "§8PREVIEW", previewX, previewY - 11, ArchivePalette.TEXT_FAINT, false);

        g.enableScissor(previewX, previewY, previewX + previewW, previewY + previewH);
        previewRegions = WikiRichTextRenderer.renderBlocks(g, this.font, previewBlocks, previewX + 4,
                previewY + 4, previewW - 8, 0, previewY, previewY + previewH,
                WikiRichTextRenderer.DEFAULT_SCALE, ArchivePalette.TERM, previewExpandedKeys);
        g.disableScissor();
    }

    /**
     * Mirrors ArchiveScreen#handleContentClick/openLink so the preview panel's links, code-copy
     * buttons, and collapsible/checklist toggles behave the same as the real entry viewer.
     */
    private boolean handlePreviewClick(double mouseX, double mouseY) {
        for (RichSpan.Region region : previewRegions) {
            if (!region.contains(mouseX, mouseY)) continue;
            RichSpan span = region.span();
            if (span instanceof RichSpan.Link l) {
                openLink(l.url());
                return true;
            } else if (span instanceof RichSpan.CodeCopy cc) {
                if (this.minecraft != null) this.minecraft.keyboardHandler.setClipboard(cc.code());
                return true;
            } else if (span instanceof RichSpan.DetailsToggle dt) {
                if (!previewExpandedKeys.remove(dt.key())) previewExpandedKeys.add(dt.key());
                return true;
            } else if (span instanceof RichSpan.ChecklistToggle ct) {
                boolean current = previewExpandedKeys.contains("CL1:" + ct.key()) ||
                        (!previewExpandedKeys.contains("CL0:" + ct.key()) && ct.checkedDefault());
                boolean next = !current;
                previewExpandedKeys.remove("CL1:" + ct.key());
                previewExpandedKeys.remove("CL0:" + ct.key());
                previewExpandedKeys.add((next ? "CL1:" : "CL0:") + ct.key());
                return true;
            }
        }
        return false;
    }

    private void openLink(String url) {
        if (url == null || url.isEmpty()) return;
        if (url.startsWith("wiki:") || url.startsWith("quest:")) return;
        try {
            Util.getPlatform().openUri(URI.create(url));
        } catch (Exception e) {
            net.phoenix_archives.phoenix_archive.PhoenixArchive.LOGGER.warn("Failed to open link '{}'", url, e);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && handlePreviewClick(mouseX, mouseY)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private class CustomTextArea extends AbstractWidget {

        private final MultilineTextField textField;
        private final List<LinePos> linesCache = new ArrayList<>();
        private static final int MAX_LENGTH = 10000;
        private static final int LINE_H = 9;

        private int scrollLine = 0;

        private boolean dragging = false;

        private int selectionAnchor = 0;

        private int selectionStart() {
            return Math.min(selectionAnchor, this.textField.cursor());
        }

        private int selectionEnd() {
            return Math.max(selectionAnchor, this.textField.cursor());
        }

        public CustomTextArea(int x, int y, int width, int height, Component message) {
            super(x, y, width, height, message);
            this.textField = new MultilineTextField(TerminalInputScreen.this.font, width - 12);
            this.textField.setCharacterLimit(MAX_LENGTH);
            this.setTooltip(Tooltip.create(Component.literal(
                    "Click+drag to select. Ctrl+C copy, Ctrl+X cut, Ctrl+V paste, Ctrl+A select all. " +
                            "Scroll to see more.")));
        }

        public void setValue(String val) {
            this.textField.setValue(val);
        }

        public String getValue() {
            return this.textField.value();
        }

        public void forceInsertBypassingFilters(String insertionText) {
            String fullText = this.textField.value();
            int currentCursor = this.textField.cursor();

            int start = currentCursor;
            int end = currentCursor;

            if (this.textField.hasSelection()) {
                start = selectionStart();
                end = selectionEnd();
            }

            StringBuilder builder = new StringBuilder(fullText);
            builder.replace(start, end, insertionText);

            String updatedText = builder.toString();
            if (updatedText.length() <= MAX_LENGTH) {
                this.textField.setValue(updatedText);
                int nextCursorPos = start + insertionText.length();
                this.textField.setSelecting(false);
                this.textField.seekCursor(net.minecraft.client.gui.components.Whence.ABSOLUTE, nextCursorPos);
                selectionAnchor = nextCursorPos;
            }
        }

        public void insertWrap(String prefix, String suffix) {
            String fullText = this.textField.value();
            int currentCursor = this.textField.cursor();

            int start = currentCursor;
            int end = currentCursor;
            String middle = "";

            if (this.textField.hasSelection()) {
                start = selectionStart();
                end = selectionEnd();
                middle = fullText.substring(start, end);
            }

            String insertionText = prefix + middle + suffix;
            StringBuilder builder = new StringBuilder(fullText);
            builder.replace(start, end, insertionText);

            String updatedText = builder.toString();
            if (updatedText.length() <= MAX_LENGTH) {
                this.textField.setValue(updatedText);
                int nextCursorPos = middle.isEmpty() ? start + prefix.length() : start + insertionText.length();
                this.textField.setSelecting(false);
                this.textField.seekCursor(net.minecraft.client.gui.components.Whence.ABSOLUTE, nextCursorPos);
                selectionAnchor = nextCursorPos;
            }
        }

        private int visibleLines() {
            return Math.max(1, (height - 12) / LINE_H);
        }

        private void rebuildLinesCache() {
            String fullText = this.textField.value();
            linesCache.clear();
            if (fullText.isEmpty()) {
                linesCache.add(new LinePos(0, 0, ""));
            } else {
                TerminalInputScreen.this.font.getSplitter().splitLines(fullText, width - 12, Style.EMPTY, false,
                        (style, start, end) -> linesCache.add(new LinePos(start, end, fullText.substring(start, end))));
                if (fullText.endsWith("\n")) {
                    linesCache.add(new LinePos(fullText.length(), fullText.length(), ""));
                }
            }
        }

        private void followCursorAndClamp() {
            int visible = visibleLines();
            int maxScroll = Math.max(0, linesCache.size() - visible);

            int cursorIdx = this.textField.cursor();
            int cursorLine = 0;
            for (int i = 0; i < linesCache.size(); i++) {
                if (cursorIdx >= linesCache.get(i).start && cursorIdx <= linesCache.get(i).end) {
                    cursorLine = i;
                    break;
                }
            }

            if (!dragging) {
                if (cursorLine < scrollLine) scrollLine = cursorLine;
                else if (cursorLine >= scrollLine + visible) scrollLine = cursorLine - visible + 1;
            }
            scrollLine = Math.max(0, Math.min(maxScroll, scrollLine));
        }

        private int cursorIndexAt(double mx, double my) {
            if (linesCache.isEmpty()) return 0;

            int row = (int) ((my - (getY() + 6)) / LINE_H) + scrollLine;
            row = Math.max(0, Math.min(row, linesCache.size() - 1));
            LinePos line = linesCache.get(row);
            int localClickX = (int) (mx - (getX() + 6));

            String lineText = line.text;
            int rawCharOffset = 0;
            int currentVisualWidth;

            while (rawCharOffset < lineText.length()) {
                if (lineText.charAt(rawCharOffset) == '§' && rawCharOffset + 1 < lineText.length()) {
                    rawCharOffset += 2;
                    continue;
                }

                String visualSubstring = lineText.substring(0, rawCharOffset + 1);
                currentVisualWidth = TerminalInputScreen.this.font.width(visualSubstring);

                if (currentVisualWidth > localClickX) {
                    break;
                }
                rawCharOffset++;
            }

            return line.start + rawCharOffset;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
            g.fill(getX(), getY(), getX() + width, getY() + height, ArchivePalette.BG);
            boolean hovered = mx >= getX() && mx < getX() + width && my >= getY() && my < getY() + height;
            g.renderOutline(getX(), getY(), width, height,
                    isFocused() ? ArchivePalette.TERM_BRIGHT : (hovered ? ArchivePalette.TERM : ArchivePalette.BORDER));

            int textX = getX() + 6;
            int textY = getY() + 6;

            rebuildLinesCache();
            followCursorAndClamp();

            int visible = visibleLines();
            int cursorIdx = this.textField.cursor();
            String fullText = this.textField.value();

            g.enableScissor(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1);

            if (this.textField.hasSelection()) {
                int selectIdx = selectionStart();
                int selectEnd = selectionEnd();

                for (int i = scrollLine; i < Math.min(linesCache.size(), scrollLine + visible + 1); i++) {
                    LinePos line = linesCache.get(i);
                    int lineY = textY + ((i - scrollLine) * LINE_H);

                    if (selectEnd > line.start && selectIdx < line.end) {
                        int selStartInLine = Math.max(selectIdx, line.start) - line.start;
                        int selEndInLine = Math.min(selectEnd, line.end) - line.start;

                        String beforeSel = line.text.substring(0, selStartInLine);
                        String selText = line.text.substring(selStartInLine, selEndInLine);

                        int hX1 = textX + TerminalInputScreen.this.font.width(beforeSel);
                        int hX2 = hX1 + TerminalInputScreen.this.font.width(selText);

                        int alpha = hovered ? 0xAA : 0x88;
                        g.fill(hX1, lineY, hX2, lineY + LINE_H, ArchivePalette.withAlpha(ArchivePalette.TERM, alpha));
                    }
                }
            }

            for (int i = scrollLine; i < Math.min(linesCache.size(), scrollLine + visible + 1); i++) {
                LinePos line = linesCache.get(i);
                int lineY = textY + ((i - scrollLine) * LINE_H);

                g.drawString(TerminalInputScreen.this.font, line.text, textX, lineY, ArchivePalette.TERM_BRIGHT,
                        false);

                if (isFocused() && cursorIdx >= line.start && cursorIdx <= line.end) {
                    if ((System.currentTimeMillis() / 500) % 2 == 0) {
                        int offset = cursorIdx - line.start;
                        String sub = line.text.substring(0, Math.min(offset, line.text.length()));
                        int cx = textX + TerminalInputScreen.this.font.width(sub);
                        g.fill(cx, lineY, cx + 1, lineY + LINE_H, ArchivePalette.TERM_BRIGHT);
                    }
                }
            }

            g.disableScissor();

            if (linesCache.size() > visible) {
                int trackX = getX() + width - 4;
                g.fill(trackX, getY() + 2, trackX + 2, getY() + height - 2,
                        ArchivePalette.withAlpha(ArchivePalette.BORDER, 0x88));
                int maxScroll = linesCache.size() - visible;
                int trackH = height - 4;
                int thumbH = Math.max(8, trackH * visible / linesCache.size());
                int thumbY = getY() + 2 + (maxScroll == 0 ? 0 : (trackH - thumbH) * scrollLine / maxScroll);
                g.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, ArchivePalette.TERM_BRIGHT);
            }
        }

        @Override
        public boolean mouseClicked(double mx, double my, int btn) {
            if (mx >= getX() && mx < getX() + width && my >= getY() && my < getY() + height) {
                this.setFocused(true);
                dragging = true;

                int idx = cursorIndexAt(mx, my);
                this.textField.setSelecting(false);
                this.textField.seekCursor(net.minecraft.client.gui.components.Whence.ABSOLUTE, idx);
                selectionAnchor = idx;
                this.textField.setSelecting(true);
                return true;
            }
            this.setFocused(false);
            return false;
        }

        @Override
        public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
            if (!dragging || btn != 0) return false;

            int visible = visibleLines();
            int maxScroll = Math.max(0, linesCache.size() - visible);
            if (my < getY() + 10) scrollLine = Math.max(0, scrollLine - 1);
            else if (my > getY() + height - 10) scrollLine = Math.min(maxScroll, scrollLine + 1);

            int idx = cursorIndexAt(mx, my);
            this.textField.seekCursor(net.minecraft.client.gui.components.Whence.ABSOLUTE, idx);
            return true;
        }

        @Override
        public boolean mouseReleased(double mx, double my, int btn) {
            if (dragging) {
                dragging = false;
                this.textField.setSelecting(false);
                return true;
            }
            return false;
        }

        @Override
        public boolean mouseScrolled(double mx, double my, double delta) {
            if (mx >= getX() && mx < getX() + width && my >= getY() && my < getY() + height) {
                int maxScroll = Math.max(0, linesCache.size() - visibleLines());
                scrollLine = Math.max(0, Math.min(maxScroll, scrollLine - (int) Math.signum(delta) * 3));
                return true;
            }
            return false;
        }

        @Override
        public boolean keyPressed(int kc, int sc, int mod) {
            if (!this.isFocused()) return false;

            if ((kc == GLFW.GLFW_KEY_ENTER || kc == GLFW.GLFW_KEY_KP_ENTER) && hasShiftDown()) {
                this.textField.insertText("\n");
                return true;
            }

            if (hasControlDown()) {
                if (kc == GLFW.GLFW_KEY_C || kc == GLFW.GLFW_KEY_X) {
                    if (this.textField.hasSelection()) {
                        TerminalInputScreen.this.minecraft.keyboardHandler
                                .setClipboard(this.textField.getSelectedText());
                        if (kc == GLFW.GLFW_KEY_X) this.forceInsertBypassingFilters("");
                    }
                    return true;
                }
                if (kc == GLFW.GLFW_KEY_V) {
                    String clip = TerminalInputScreen.this.minecraft.keyboardHandler.getClipboard();
                    if (clip != null && !clip.isEmpty()) this.forceInsertBypassingFilters(clip);
                    return true;
                }
                if (kc == GLFW.GLFW_KEY_A) {
                    this.textField.setSelecting(false);
                    this.textField.seekCursor(net.minecraft.client.gui.components.Whence.ABSOLUTE, 0);
                    selectionAnchor = 0;
                    this.textField.setSelecting(true);
                    this.textField.seekCursor(net.minecraft.client.gui.components.Whence.ABSOLUTE,
                            this.textField.value().length());
                    return true;
                }
            }

            boolean hadSelectionBefore = this.textField.hasSelection();
            int cursorBefore = this.textField.cursor();
            if (this.textField.keyPressed(kc)) {
                if (!hadSelectionBefore && this.textField.hasSelection()) {
                    selectionAnchor = cursorBefore;
                }
                return true;
            }
            return super.keyPressed(kc, sc, mod);
        }

        @Override
        public boolean charTyped(char codePoint, int modifiers) {
            if (this.isFocused() && SharedConstants.isAllowedChatCharacter(codePoint)) {
                this.textField.insertText(Character.toString(codePoint));
                return true;
            }
            return false;
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {}

        private static class LinePos {

            final int start;
            final int end;
            final String text;

            LinePos(int start, int end, String text) {
                this.start = start;
                this.end = end;
                this.text = text;
            }
        }
    }
}
