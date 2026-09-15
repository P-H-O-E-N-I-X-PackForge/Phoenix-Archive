package net.phoenix_archives.phoenix_archive.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.phoenix_archives.phoenix_archive.api.CategoryRegistry;
import net.phoenixvine.wiki.theme.PhoenixTheme;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ParentPickerScreen extends Screen {

    private static final int ROW_H = 14;
    private static final int LIST_PADDING = 4;
    private static final int SCROLL_BAR_W = 4;

    private final Screen returnTo;
    private final String excludeId;   
    private final Consumer<String> onPicked;    

    private final List<String> options = new ArrayList<>();

    private int scrollOffset = 0;
    private int hoveredIndex = -1;

    private int listX, listY, listW, listH, visibleRows;

    public ParentPickerScreen(Screen returnTo, String currentId,
                              String excludeId, Consumer<String> onPicked) {
        super(Component.literal("Select Parent Category"));
        this.returnTo = returnTo;
        this.excludeId = excludeId;
        this.onPicked = onPicked;

        buildOptions(currentId);
    }

    private void buildOptions(String currentId) {
        options.clear();
        options.add(null); 

        for (String id : CategoryRegistry.getRegisteredIds()) {
            if (excludeId != null && isDescendantOrSelf(id, excludeId)) continue;
            options.add(id);
        }

        options.subList(1, options.size()).sort(String::compareToIgnoreCase);

        int selectedIdx = options.indexOf(currentId); 
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

    @Override
    protected void init() {
        this.clearWidgets();
        ArchivePalette.refresh(PhoenixTheme.current());
        
        listW = Math.min(260, this.width - 40);
        visibleRows = Math.min(12, (this.height - 80) / ROW_H);
        listH = visibleRows * ROW_H + LIST_PADDING * 2;
        listX = (this.width - listW) / 2;
        listY = (this.height - listH) / 2 - 10;

        this.addRenderableWidget(
                Button.builder(Component.literal("CANCEL"),
                        b -> this.minecraft.setScreen(returnTo))
                        .bounds(this.width / 2 - 60, listY + listH + 8, 120, 16)
                        .build());
    }

    @Override
    protected void repositionElements() {
        this.init();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        ArchivePalette.refresh(PhoenixTheme.current());
        this.renderBackground(g);

        g.drawCenteredString(this.font, "§6SELECT_PARENT_CATEGORY",
                this.width / 2, listY - 18, ArchivePalette.TERM_BRIGHT);
        g.drawCenteredString(this.font, "§8Click to select, §7ENTER §8or double-click to confirm",
                this.width / 2, listY - 8, ArchivePalette.TEXT_FAINT);

        g.fill(listX - 1, listY - 1,
                listX + listW + 1, listY + listH + 1, ArchivePalette.BORDER);
        g.fill(listX, listY,
                listX + listW, listY + listH, ArchivePalette.PANEL);

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
                g.fill(rowX, rowY, rowX + rowW, rowY + ROW_H - 1, ArchivePalette.withAlpha(ArchivePalette.TERM, 0x33));
            }

            String color = (opt == null) ? "§8" : "§7";
            g.drawString(this.font, color + label, rowX + 4, rowY + 2, ArchivePalette.TERM_BRIGHT, false);
        }

        renderScrollbar(g);

        super.render(g, mouseX, mouseY, partial);
    }

    private void renderScrollbar(GuiGraphics g) {
        if (options.size() <= visibleRows) return;

        int trackX = listX + listW - SCROLL_BAR_W - 1;
        int trackY = listY + LIST_PADDING;
        int trackH = visibleRows * ROW_H;

        g.fill(trackX, trackY, trackX + SCROLL_BAR_W, trackY + trackH, ArchivePalette.BORDER);

        int maxScroll = options.size() - visibleRows;
        float frac = (float) scrollOffset / maxScroll;
        int thumbH = Math.max(8, trackH * visibleRows / options.size());
        int thumbY = trackY + (int) (frac * (trackH - thumbH));

        g.fill(trackX, thumbY, trackX + SCROLL_BAR_W, thumbY + thumbH, ArchivePalette.TEXT_FAINT);
    }

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
        
        if ((keyCode == 257 || keyCode == 335) && hoveredIndex >= 0) {
            confirm(options.get(hoveredIndex));
            return true;
        }
        
        if (keyCode == 264 ) {
            scrollOffset = Math.min(scrollOffset + 1, Math.max(0, options.size() - visibleRows));
            return true;
        }
        if (keyCode == 265 ) {
            scrollOffset = Math.max(0, scrollOffset - 1);
            return true;
        }
        
        if (keyCode == 256) {
            this.minecraft.setScreen(returnTo);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void confirm(String chosen) {
        onPicked.accept(chosen);               
        this.minecraft.setScreen(returnTo);    
    }
}
