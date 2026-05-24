package net.phoenixvine.phoenix_archive.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.phoenixvine.phoenix_archive.api.CategoryRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A simple full-screen picker that lets the user choose a parent category
 * (or "(none — root)") and returns the result via a callback.
 *
 * <p>
 * Open it like:
 * 
 * <pre>
 * minecraft.setScreen(new ParentPickerScreen(
 *         this,                       // returnTo
 *         currentParentId,            // pre-selected value, or null
 *         editingId,                  // excluded from the list to prevent cycles
 *         chosen -> this.selectedParentId = chosen));
 * </pre>
 * </p>
 */
public class ParentPickerScreen extends Screen {

    private static final int ROW_H = 14;
    private static final int LIST_PADDING = 4;
    private static final int SCROLL_BAR_W = 4;

    private final Screen returnTo;
    private final String excludeId;   // the category being edited (excluded to prevent cycles)
    private final Consumer<String> onPicked;    // receives the chosen ID, or null for root

    /** All pickable options. Index 0 is always null (= "no parent / root"). */
    private final List<String> options = new ArrayList<>();

    private int scrollOffset = 0;
    private int hoveredIndex = -1;

    // Layout — computed in init()
    private int listX, listY, listW, listH, visibleRows;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    /**
     * @param returnTo  The screen to return to on confirm or cancel.
     * @param currentId The currently selected parent ID (may be null = root).
     * @param excludeId The category being edited; it and its descendants are hidden.
     * @param onPicked  Callback invoked with the chosen ID (null = root) on confirm.
     */
    public ParentPickerScreen(Screen returnTo, String currentId,
                              String excludeId, Consumer<String> onPicked) {
        super(Component.literal("Select Parent Category"));
        this.returnTo = returnTo;
        this.excludeId = excludeId;
        this.onPicked = onPicked;

        buildOptions(currentId);
    }

    // -------------------------------------------------------------------------
    // Option list
    // -------------------------------------------------------------------------

    private void buildOptions(String currentId) {
        options.clear();
        options.add(null); // sentinel: "(none — root)"

        for (String id : CategoryRegistry.getRegisteredIds()) {
            if (excludeId != null && isDescendantOrSelf(id, excludeId)) continue;
            options.add(id);
        }

        options.subList(1, options.size()).sort(String::compareToIgnoreCase);

        // Pre-scroll so the current selection is visible
        int selectedIdx = options.indexOf(currentId); // indexOf(null) finds the sentinel correctly
        if (selectedIdx < 0) selectedIdx = 0;
        scrollOffset = Math.max(0, selectedIdx - 3);
    }

    private boolean isDescendantOrSelf(String candidate, String root) {
        if (candidate.equalsIgnoreCase(root)) return true;
        String parent = CategoryRegistry.getParentId(candidate);
        int guard = 0;
        while (parent != null && guard++ < 20) {
            if (parent.equalsIgnoreCase(root)) return true;
            parent = CategoryRegistry.getParentId(parent);
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Init
    // -------------------------------------------------------------------------

    @Override
    protected void init() {
        // List occupies a centred panel
        listW = Math.min(260, this.width - 40);
        visibleRows = Math.min(12, (this.height - 80) / ROW_H);
        listH = visibleRows * ROW_H + LIST_PADDING * 2;
        listX = (this.width - listW) / 2;
        listY = (this.height - listH) / 2 - 10;

        // Cancel button
        this.addRenderableWidget(
                Button.builder(Component.literal("CANCEL"),
                        b -> this.minecraft.setScreen(returnTo))
                        .bounds(this.width / 2 - 60, listY + listH + 8, 120, 16)
                        .build());
    }

    // -------------------------------------------------------------------------
    // Render
    // -------------------------------------------------------------------------

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        this.renderBackground(g);

        // Title
        g.drawCenteredString(this.font, "§6SELECT_PARENT_CATEGORY",
                this.width / 2, listY - 18, 0xFFFFFF);
        g.drawCenteredString(this.font, "§8Click to select, §7ENTER §8or double-click to confirm",
                this.width / 2, listY - 8, 0x888888);

        // Panel background + border
        g.fill(listX - 1, listY - 1,
                listX + listW + 1, listY + listH + 1, 0xFF555555);
        g.fill(listX, listY,
                listX + listW, listY + listH, 0xFF1A1A1A);

        hoveredIndex = -1;

        for (int i = 0; i < visibleRows; i++) {
            int optIdx = i + scrollOffset;
            if (optIdx >= options.size()) break;

            String opt = options.get(optIdx);
            String label = (opt == null) ? "(none — root)" : opt;

            int rowX = listX;
            int rowY = listY + LIST_PADDING + i * ROW_H;
            int rowW = listW - SCROLL_BAR_W - 2;

            boolean hovered = mouseX >= rowX && mouseX < rowX + rowW && mouseY >= rowY && mouseY < rowY + ROW_H;
            if (hovered) hoveredIndex = optIdx;

            if (hovered) {
                g.fill(rowX, rowY, rowX + rowW, rowY + ROW_H - 1, 0xFF2A3A4A);
            }

            String color = (opt == null) ? "§8" : "§7";
            g.drawString(this.font, color + label, rowX + 4, rowY + 2, 0xFFFFFF, false);
        }

        renderScrollbar(g);

        super.render(g, mouseX, mouseY, partial);
    }

    private void renderScrollbar(GuiGraphics g) {
        if (options.size() <= visibleRows) return;

        int trackX = listX + listW - SCROLL_BAR_W - 1;
        int trackY = listY + LIST_PADDING;
        int trackH = visibleRows * ROW_H;

        g.fill(trackX, trackY, trackX + SCROLL_BAR_W, trackY + trackH, 0xFF333333);

        int maxScroll = options.size() - visibleRows;
        float frac = (float) scrollOffset / maxScroll;
        int thumbH = Math.max(8, trackH * visibleRows / options.size());
        int thumbY = trackY + (int) (frac * (trackH - thumbH));

        g.fill(trackX, thumbY, trackX + SCROLL_BAR_W, thumbY + thumbH, 0xFF888888);
    }

    // -------------------------------------------------------------------------
    // Input
    // -------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (hoveredIndex >= 0 && hoveredIndex < options.size()) {
            confirm(options.get(hoveredIndex));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int maxScroll = Math.max(0, options.size() - visibleRows);
        scrollOffset = (int) Math.max(0, Math.min(scrollOffset - (int) delta, maxScroll));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // ENTER / RETURN confirms the hovered row (if any)
        if ((keyCode == 257 || keyCode == 335) && hoveredIndex >= 0) {
            confirm(options.get(hoveredIndex));
            return true;
        }
        // Arrow keys scroll
        if (keyCode == 264 /* DOWN */) {
            scrollOffset = Math.min(scrollOffset + 1, Math.max(0, options.size() - visibleRows));
            return true;
        }
        if (keyCode == 265 /* UP */) {
            scrollOffset = Math.max(0, scrollOffset - 1);
            return true;
        }
        // ESC cancels
        if (keyCode == 256) {
            this.minecraft.setScreen(returnTo);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // -------------------------------------------------------------------------
    // Confirm
    // -------------------------------------------------------------------------

    private void confirm(String chosen) {
        onPicked.accept(chosen);               // write result back into parent screen
        this.minecraft.setScreen(returnTo);    // return to the editor
    }
}
