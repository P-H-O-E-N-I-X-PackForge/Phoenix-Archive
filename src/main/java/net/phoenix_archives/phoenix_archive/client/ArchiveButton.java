package net.phoenix_archives.phoenix_archive.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.phoenixvine.wiki.theme.PhoenixTheme;

/**
 * The Phoenix Suite button: a flat themed plate with a thin border, corner ticks and an accent bar that lights up
 * under the cursor, in place of the stone-grey Minecraft button. It takes its colours from the suite theme, so the
 * theme editor restyles it with everything else.
 */
public class ArchiveButton extends Button {

    protected ArchiveButton(int x, int y, int w, int h, Component message, OnPress onPress) {
        super(x, y, w, h, message, onPress, DEFAULT_NARRATION);
    }

    public static Builder create(Component message, OnPress onPress) {
        return new Builder(message, onPress);
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (ArchivePalette.BORDER_LIT == 0) ArchivePalette.refresh(PhoenixTheme.current());
        boolean hot = active && isHoveredOrFocused();
        int x0 = getX(), y0 = getY(), x1 = x0 + width, y1 = y0 + height;

        int accent = ArchivePalette.BORDER_LIT;
        int fill = active ? (hot ? ArchivePalette.withAlpha(accent, 0x4A) : ArchivePalette.withAlpha(accent, 0x1C)) :
                0x33000000;
        int border = !active ? ArchivePalette.withAlpha(ArchivePalette.BORDER, 0x66) :
                hot ? ArchivePalette.TERM_BRIGHT : ArchivePalette.BORDER;

        g.fill(x0, y0, x1, y1, ArchivePalette.PANEL);
        g.fill(x0, y0, x1, y1, fill);
        g.fill(x0, y0, x1, y0 + 1, border);
        g.fill(x0, y1 - 1, x1, y1, border);
        g.fill(x0, y0, x0 + 1, y1, border);
        g.fill(x1 - 1, y0, x1, y1, border);

        if (active) {
            int tick = hot ? ArchivePalette.TERM_HILITE : accent;
            int len = Math.min(3, Math.max(1, height / 4));
            g.fill(x0, y0, x0 + len + 1, y0 + 1, tick);
            g.fill(x0, y0, x0 + 1, y0 + len + 1, tick);
            g.fill(x1 - len - 1, y1 - 1, x1, y1, tick);
            g.fill(x1 - 1, y1 - len - 1, x1, y1, tick);
            if (hot) g.fill(x0 + 1, y0 + 1, x0 + 3, y1 - 1, accent);
        }

        Font font = Minecraft.getInstance().font;
        int textColor = !active ? ArchivePalette.TEXT_FAINT : hot ? ArchivePalette.TERM_BRIGHT : ArchivePalette.TEXT;
        int pad = hot ? 5 : 3;
        int maxW = width - pad * 2;
        Component label = getMessage();
        int labelW = font.width(label);
        int tx = x0 + (width - Math.min(labelW, maxW)) / 2;
        int ty = y0 + (height - font.lineHeight) / 2 + 1;
        if (labelW > maxW) {
            g.enableScissor(x0 + pad, y0, x1 - pad, y1);
            g.drawString(font, label, x0 + pad, ty, textColor, false);
            g.disableScissor();
        } else {
            g.drawString(font, label, tx, ty, textColor, false);
        }
    }

    /** The same chained calls as the vanilla builder, so screens only change the class they start from. */
    public static final class Builder {

        private final Component message;
        private final OnPress onPress;
        private int x, y, width = 150, height = 20;
        private Tooltip tooltip;

        private Builder(Component message, OnPress onPress) {
            this.message = message;
            this.onPress = onPress;
        }

        public Builder pos(int x, int y) {
            this.x = x;
            this.y = y;
            return this;
        }

        public Builder width(int width) {
            this.width = width;
            return this;
        }

        public Builder size(int width, int height) {
            this.width = width;
            this.height = height;
            return this;
        }

        public Builder bounds(int x, int y, int width, int height) {
            return pos(x, y).size(width, height);
        }

        public Builder tooltip(Tooltip tooltip) {
            this.tooltip = tooltip;
            return this;
        }

        public ArchiveButton build() {
            ArchiveButton button = new ArchiveButton(x, y, width, height, message, onPress);
            button.setTooltip(tooltip);
            return button;
        }
    }
}
