package net.phoenix_archives.phoenix_archive.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.phoenix_archives.phoenix_archive.api.LoreDataLoader;
import net.phoenix_archives.phoenix_archive.api.LoreEntry;
import net.phoenixvine.wiki.theme.PhoenixTheme;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Lists existing lore entries by title/id for picking an "unlock check" target, mirroring
 * ArchiveShaderPickerScreen's browse-then-select pattern for the background-shader field.
 */
public class ArchiveEntryPickerScreen extends Screen {

    private static final int HEADER_H = 42;
    private static final int FOOTER_H = 20;
    private static final int ROW_H = 16;

    private final Screen parent;
    private final String excludeId;
    private final Consumer<String> onSelect;

    private EditBox searchBox;
    private String query = "";

    private List<LoreEntry> allEntries = List.of();
    private List<LoreEntry> filtered = List.of();

    private int scrollY = 0;
    private int hoveredIdx = -1;

    public ArchiveEntryPickerScreen(Screen parent, String excludeId, Consumer<String> onSelect) {
        super(Component.literal("Entry Browser"));
        this.parent = parent;
        this.excludeId = excludeId == null ? "" : excludeId;
        this.onSelect = onSelect;
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();
        ArchivePalette.refresh(PhoenixTheme.current());

        allEntries = LoreDataLoader.LORE_ENTRIES.values().stream()
                .filter(e -> !e.id().equals(excludeId))
                .toList();
        applyFilter();

        searchBox = new EditBox(font, width / 2 - 100, HEADER_H / 2 - 5, 200, 14, Component.empty());
        searchBox.setHint(Component.literal("§8Search…"));
        searchBox.setValue(query);
        searchBox.setResponder(q -> {
            query = q.toLowerCase();
            applyFilter();
            scrollY = 0;
        });
        addRenderableWidget(searchBox);
    }

    @Override
    protected void repositionElements() {
        this.init();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        ArchivePalette.refresh(PhoenixTheme.current());
        g.fill(0, 0, width, height, ArchivePalette.BG);

        g.fill(0, 0, width, HEADER_H, ArchivePalette.PANEL);
        g.fill(0, HEADER_H - 1, width, HEADER_H, ArchivePalette.BORDER);
        g.drawCenteredString(font, "§fEntry Browser", width / 2, 6, ArchivePalette.TERM_BRIGHT);
        g.drawString(font, "§8Search:", width / 2 - 100 - font.width("Search: "), HEADER_H / 2 - 3,
                ArchivePalette.TEXT_FAINT, false);

        int fy = height - FOOTER_H;
        g.fill(0, fy, width, height, ArchivePalette.PANEL);
        g.fill(0, fy, width, fy + 1, ArchivePalette.BORDER);
        g.drawString(font, "§8" + filtered.size() + " Entries  ·  LMB to select",
                8, fy + 6, ArchivePalette.TEXT_FAINT, false);

        g.enableScissor(0, HEADER_H, width, fy);
        int startY = HEADER_H + 4 - scrollY;

        hoveredIdx = -1;
        for (int i = 0; i < filtered.size(); i++) {
            int ty = startY + i * ROW_H;
            if (ty + ROW_H < HEADER_H || ty > fy) continue;

            boolean hov = mx >= 8 && mx < width - 8 && my >= ty && my < ty + ROW_H;
            if (hov) {
                hoveredIdx = i;
                g.fill(8, ty, width - 8, ty + ROW_H, ArchivePalette.withAlpha(ArchivePalette.TERM_BRIGHT, 0x33));
            }

            LoreEntry entry = filtered.get(i);
            g.drawString(font, "§f" + entry.title() + " §8(" + entry.id() + ")", 12, ty + 4,
                    ArchivePalette.TERM_BRIGHT, false);
        }

        g.disableScissor();

        if (filtered.isEmpty()) {
            g.drawCenteredString(font, "§8No lore entries found", width / 2, HEADER_H + 20,
                    ArchivePalette.TEXT_FAINT);
        }

        super.render(g, mx, my, partial);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (super.mouseClicked(mx, my, btn)) return true;
        if (btn == 0 && hoveredIdx >= 0 && hoveredIdx < filtered.size()) {
            onSelect.accept(filtered.get(hoveredIdx).id());
            Minecraft.getInstance().setScreen(parent);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int totalH = filtered.size() * ROW_H;
        int visible = height - HEADER_H - FOOTER_H;
        scrollY = (int) Math.max(0, Math.min(scrollY - delta * 20, Math.max(0, totalH - visible)));
        return true;
    }

    @Override
    public boolean keyPressed(int kc, int sc, int mod) {
        if (kc == 256) {
            Minecraft.getInstance().setScreen(parent);
            return true;
        }
        return super.keyPressed(kc, sc, mod);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void applyFilter() {
        if (query.isBlank()) {
            filtered = new ArrayList<>(allEntries);
        } else {
            filtered = allEntries.stream()
                    .filter(e -> e.title().toLowerCase().contains(query) || e.id().toLowerCase().contains(query))
                    .toList();
        }
    }
}
