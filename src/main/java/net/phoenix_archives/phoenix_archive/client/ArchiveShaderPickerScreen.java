package net.phoenix_archives.phoenix_archive.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.network.chat.Component;
import net.phoenix_archives.phoenix_archive.client.render.shader.ArchiveShaderManager;
import net.phoenix_archives.phoenix_archive.client.render.shader.ArchiveShaderRenderUtil;
import net.phoenixvine.wiki.theme.PhoenixTheme;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ArchiveShaderPickerScreen extends Screen {

    private static final int THUMB = 56;
    private static final int THUMB_PAD = 8;
    private static final int HEADER_H = 42;
    private static final int FOOTER_H = 28;

    private final Screen parent;
    private final Consumer<String> onSelect;

    private EditBox searchBox;
    private String query = "";

    private List<String> allShaders = List.of();
    private List<String> filtered = List.of();

    private int scrollY = 0;
    private int hoveredIdx = -1;

    public ArchiveShaderPickerScreen(Screen parent, Consumer<String> onSelect) {
        super(Component.literal("Shader Browser"));
        this.parent = parent;
        this.onSelect = onSelect;
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();
        ArchivePalette.refresh(PhoenixTheme.current());

        allShaders = ArchiveShaderManager.listAvailable();
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
        g.drawCenteredString(font, "§fShader Browser", width / 2, 6, ArchivePalette.TERM_BRIGHT);
        g.drawString(font, "§8Search:", width / 2 - 100 - font.width("Search: "), HEADER_H / 2 - 3,
                ArchivePalette.TEXT_FAINT, false);

        int fy = height - FOOTER_H;
        g.fill(0, fy, width, height, ArchivePalette.PANEL);
        g.fill(0, fy, width, fy + 1, ArchivePalette.BORDER);
        g.drawString(font, "§8" + filtered.size() +
                " Shaders  ·  LMB to select  ·  RMB to copy id  ·  live preview, so a broken" +
                " shader shows §c⚠",
                8, fy + 10, ArchivePalette.TEXT_FAINT, false);

        g.enableScissor(0, HEADER_H, width, fy);
        int cols = Math.max(1, (width - 16) / (THUMB + THUMB_PAD));
        int startX = 8;
        int startY = HEADER_H + 4 - scrollY;

        float t = ArchiveShaderRenderUtil.wrappedSeconds(System.currentTimeMillis());

        hoveredIdx = -1;
        for (int i = 0; i < filtered.size(); i++) {
            int col = i % cols;
            int row = i / cols;
            int tx = startX + col * (THUMB + THUMB_PAD);
            int ty = startY + row * (THUMB + THUMB_PAD + 12);
            if (ty + THUMB + 12 < HEADER_H || ty > fy) continue;

            boolean hov = mx >= tx && mx < tx + THUMB && my >= ty && my < ty + THUMB;
            if (hov) hoveredIdx = i;

            String id = filtered.get(i);

            for (int cy = 0; cy < THUMB; cy += 8) {
                for (int cx = 0; cx < THUMB; cx += 8) {
                    boolean light = ((cx / 8) + (cy / 8)) % 2 == 0;
                    g.fill(tx + cx, ty + cy, tx + cx + 8, ty + cy + 8, light ? 0xFF666666 : 0xFF444444);
                }
            }

            ShaderInstance shader = ArchiveShaderManager.get(id);
            if (shader != null) {
                ArchiveShaderRenderUtil.drawDynamicShaderQuad(g, shader, tx, ty, THUMB, THUMB, t);
            } else {
                g.fill(tx, ty, tx + THUMB, ty + THUMB, 0xAA441111);
                g.drawCenteredString(font, "§c⚠", tx + THUMB / 2, ty + THUMB / 2 - 4, 0xFFFF5555);
            }

            if (hov) {
                g.fill(tx - 1, ty - 1, tx + THUMB + 1, ty, ArchivePalette.TERM_BRIGHT);
                g.fill(tx - 1, ty + THUMB, tx + THUMB + 1, ty + THUMB + 1, ArchivePalette.TERM_BRIGHT);
                g.fill(tx - 1, ty - 1, tx, ty + THUMB + 1, ArchivePalette.TERM_BRIGHT);
                g.fill(tx + THUMB, ty - 1, tx + THUMB + 1, ty + THUMB + 1, ArchivePalette.TERM_BRIGHT);
            }

            String name = id;
            int nameW = font.width(name);
            if (nameW > THUMB) name = font.plainSubstrByWidth(name, THUMB - 4) + "…";
            g.drawString(font, "§8" + name, tx, ty + THUMB + 2, ArchivePalette.TEXT_FAINT, false);

            if (hov) {
                Component tip = shader != null ? Component.literal("§f" + id) :
                        Component.literal("§c" + id + " §7(failed to compile -- check the log)");
                g.renderTooltip(font, tip, mx, my);
            }
        }

        g.disableScissor();

        if (filtered.isEmpty()) {
            g.drawCenteredString(font, "§8No .frag files in config/phoenix_archive/shaders/",
                    width / 2, HEADER_H + 20, ArchivePalette.TEXT_FAINT);
        }

        super.render(g, mx, my, partial);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (super.mouseClicked(mx, my, btn)) return true;
        if (hoveredIdx >= 0 && hoveredIdx < filtered.size()) {
            String id = filtered.get(hoveredIdx);
            if (btn == 0) {
                onSelect.accept(id);
                Minecraft.getInstance().setScreen(parent);
            } else if (btn == 1) {
                Minecraft.getInstance().keyboardHandler.setClipboard(id);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int cols = Math.max(1, (width - 16) / (THUMB + THUMB_PAD));
        int rows = (filtered.size() + cols - 1) / cols;
        int totalH = rows * (THUMB + THUMB_PAD + 12);
        int visible = height - HEADER_H - FOOTER_H;
        scrollY = (int) Math.max(0, Math.min(scrollY - delta * 20, totalH - visible));
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
            filtered = new ArrayList<>(allShaders);
        } else {
            filtered = allShaders.stream().filter(id -> id.toLowerCase().contains(query)).toList();
        }
    }
}
