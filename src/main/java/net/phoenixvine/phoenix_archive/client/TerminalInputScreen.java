package net.phoenixvine.phoenix_archive.client;

import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultilineTextField;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class TerminalInputScreen extends Screen {

    private final Screen parent;
    private final String titleLabel;
    private final String initialValue;
    private final Consumer<String> onCommit;

    // Swapped to our custom text area component
    private CustomTextArea textArea;
    private String liveValue;

    public TerminalInputScreen(Screen parent, String titleLabel, String initialValue, Consumer<String> onCommit) {
        super(Component.literal("Terminal Input Unit"));
        this.parent = parent;
        this.titleLabel = titleLabel;
        this.initialValue = initialValue;
        this.onCommit = onCommit;
    }

    @Override
    protected void init() {
        String startingValue = (liveValue != null) ? liveValue : initialValue;

        // Instantiate our custom engine wrapper
        this.textArea = new CustomTextArea(20, 45, this.width - 40, this.height - 90, Component.empty());
        this.textArea.setValue(startingValue);
        this.addRenderableWidget(textArea);

        // --- 1. Standard Vanilla Colors & Formatting ---
        int startX = 20;
        int startY = 12;
        int btnW = 14;

        String colorCodes = "0123456789abcdef";
        for (char c : colorCodes.toCharArray()) {
            this.addRenderableWidget(Button.builder(Component.literal("§" + c + "█"), b -> insertCode("&" + c))
                    .bounds(startX, startY, btnW, 14).build());
            startX += btnW + 2;
        }

        startX += 4;

        String formatCodes = "lmnork";
        for (char c : formatCodes.toCharArray()) {
            String label = (c == 'r') ? "X" : (c == 'k') ? "?" : "§" + c + "█";
            this.addRenderableWidget(Button.builder(Component.literal(label), b -> insertCode("&" + c))
                    .bounds(startX, startY, btnW, 14).build());
            startX += btnW + 2;
        }

        // --- 2. Chromatic OS Trigger ---
        if (net.minecraftforge.fml.ModList.get().isLoaded("phoenix_chromatic_codes")) {
            this.addRenderableWidget(Button.builder(Component.literal("§z[ CHROMATIC_OS ]"), b -> {
                if (this.minecraft != null) {
                    liveValue = this.textArea.getValue();
                    this.minecraft.setScreen(new ChromaticSelectorScreen(this));
                }
            }).bounds(this.width - 110, 12, 100, 14).build());
        }

        // --- 3. Commit Changes ---
        this.addRenderableWidget(Button.builder(Component.literal("§2[ COMMIT_CHANGES ]"), b -> {
            this.onCommit.accept(this.textArea.getValue());
            if (this.minecraft != null) {
                this.minecraft.setScreen(parent);
            }
        }).bounds(this.width / 2 - 60, this.height - 35, 120, 20).build());

        this.setInitialFocus(textArea);
    }

    public void receiveCode(String code) {
        if (this.textArea != null) {
            this.insertCode(code);
            this.liveValue = this.textArea.getValue();
        } else {
            liveValue = (liveValue != null ? liveValue : initialValue) + code;
        }
    }

    /**
     * Safe filtering bypass insertion mechanism copied directly from Phantasia!
     */
    public void insertCode(String code) {
        if (this.textArea != null && code != null) {
            this.setInitialFocus(this.textArea);
            this.textArea.forceInsertBypassingFilters(code);
        }
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics);
        graphics.fill(10, 10, this.width - 10, this.height - 10, 0xEE050505);
        graphics.renderOutline(10, 10, this.width - 20, this.height - 20, 0xFF00FF00);
        graphics.drawString(this.font, titleLabel + " // ADDR: 0x7FFA", 20, 32, 0x00AA00);
        super.render(graphics, mouseX, mouseY, partialTicks);
    }

    /**
     * Bespoke CustomTextArea
     * Manages raw text slicing, selection logic, and color-code clicking metrics.
     */
    private class CustomTextArea extends AbstractWidget {

        private final MultilineTextField textField;
        private final List<LinePos> linesCache = new ArrayList<>();
        private static final int MAX_LENGTH = 10000;

        public CustomTextArea(int x, int y, int width, int height, Component message) {
            super(x, y, width, height, message);
            this.textField = new MultilineTextField(TerminalInputScreen.this.font, width - 12);
            this.textField.setCharacterLimit(MAX_LENGTH);
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
                String sel = this.textField.getSelectedText();
                start = fullText.indexOf(sel);
                if (start != -1) {
                    end = start + sel.length();
                } else {
                    start = currentCursor;
                }
            }

            StringBuilder builder = new StringBuilder(fullText);
            builder.replace(start, end, insertionText);

            String updatedText = builder.toString();
            if (updatedText.length() <= MAX_LENGTH) {
                this.textField.setValue(updatedText);
                int nextCursorPos = start + insertionText.length();
                this.textField.seekCursor(net.minecraft.client.gui.components.Whence.ABSOLUTE, nextCursorPos);
            }
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
            g.fill(getX(), getY(), getX() + width, getY() + height, 0xFF000000);
            g.renderOutline(getX(), getY(), width, height, isFocused() ? 0xFF00FF00 : 0xFF444444);

            int textX = getX() + 6;
            int textY = getY() + 6;

            int cursorIdx = this.textField.cursor();
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

            // Highlighting Tracker logic
            if (this.textField.hasSelection()) {
                String selectedText = this.textField.getSelectedText();
                int selectIdx = fullText.indexOf(selectedText);
                int selectEnd = selectIdx + selectedText.length();

                for (int i = 0; i < linesCache.size(); i++) {
                    LinePos line = linesCache.get(i);
                    int lineY = textY + (i * 9);

                    if (selectEnd > line.start && selectIdx < line.end) {
                        int selStartInLine = Math.max(selectIdx, line.start) - line.start;
                        int selEndInLine = Math.min(selectEnd, line.end) - line.start;

                        String beforeSel = line.text.substring(0, selStartInLine);
                        String selText = line.text.substring(selStartInLine, selEndInLine);

                        int hX1 = textX + TerminalInputScreen.this.font.width(beforeSel);
                        int hX2 = hX1 + TerminalInputScreen.this.font.width(selText);

                        g.fill(hX1, lineY, hX2, lineY + 9, 0xFF2244AA);
                    }
                }
            }

            // Draw line items and blinking carets
            for (int i = 0; i < linesCache.size(); i++) {
                LinePos line = linesCache.get(i);
                int lineY = textY + (i * 9);

                g.drawString(TerminalInputScreen.this.font, line.text, textX, lineY, 0xFFDDDDDD, false);

                if (isFocused() && cursorIdx >= line.start && cursorIdx <= line.end) {
                    if ((System.currentTimeMillis() / 500) % 2 == 0) {
                        int offset = cursorIdx - line.start;
                        String sub = line.text.substring(0, Math.min(offset, line.text.length()));
                        int cx = textX + TerminalInputScreen.this.font.width(sub);
                        g.fill(cx, lineY, cx + 1, lineY + 9, 0xFF00FF00);
                    }
                }
            }
        }

        @Override
        public boolean mouseClicked(double mx, double my, int btn) {
            if (mx >= getX() && mx < getX() + width && my >= getY() && my < getY() + height) {
                this.setFocused(true);

                if (!linesCache.isEmpty()) {
                    int clickedLineIdx = (int) ((my - (getY() + 6)) / 9);
                    clickedLineIdx = Math.max(0, Math.min(clickedLineIdx, linesCache.size() - 1));

                    LinePos clickedLine = linesCache.get(clickedLineIdx);
                    int localClickX = (int) (mx - (getX() + 6));

                    String lineText = clickedLine.text;
                    int rawCharOffset = 0;
                    int currentVisualWidth = 0;

                    // The accurate color-safe character stepping loop
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

                    int finalTargetCursor = clickedLine.start + rawCharOffset;
                    this.textField.seekCursor(net.minecraft.client.gui.components.Whence.ABSOLUTE, finalTargetCursor);
                }
                return true;
            }
            this.setFocused(false);
            return false;
        }

        @Override
        public boolean keyPressed(int kc, int sc, int mod) {
            if (!this.isFocused()) return false;

            if ((kc == GLFW.GLFW_KEY_ENTER || kc == GLFW.GLFW_KEY_KP_ENTER) && hasShiftDown()) {
                this.textField.insertText("\n");
                return true;
            }

            if (this.textField.keyPressed(kc)) {
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
