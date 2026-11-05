package net.phoenix_archives.phoenix_archive.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.phoenixvine.wiki.theme.PhoenixTheme;

/**
 * The Phoenix Suite button: a slim flat plate with a thin border that lights up under the cursor, in place of the
 * stone-grey Minecraft button. It takes its colours from the suite theme, so the
 * theme editor restyles it with everything else.
 */
public class ArchiveButton extends Button {

    /** Buttons never get taller than this, so a screen asking for a chunky one still gets a slim plate. */
    private static final int MAX_HEIGHT = 14;

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
        int fill = !active ? 0x22000000 : hot ? ArchivePalette.withAlpha(accent, 0x38) :
                ArchivePalette.withAlpha(accent, 0x12);
        int border = !active ? ArchivePalette.withAlpha(ArchivePalette.BORDER, 0x44) :
                hot ? ArchivePalette.TERM_BRIGHT : ArchivePalette.withAlpha(ArchivePalette.BORDER, 0x99);

        g.fill(x0, y0, x1, y1, fill);
        g.renderOutline(x0, y0, width, height, border);

        Font font = Minecraft.getInstance().font;
        int textColor = !active ? ArchivePalette.TEXT_FAINT : hot ? ArchivePalette.TERM_BRIGHT : ArchivePalette.TEXT;
        int pad = 3;
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
            int h = Math.min(height, MAX_HEIGHT);
            ArchiveButton button = new ArchiveButton(x, y + (height - h) / 2, width, h, message, onPress);
            button.setTooltip(tooltip);
            return button;
        }
    }
}
