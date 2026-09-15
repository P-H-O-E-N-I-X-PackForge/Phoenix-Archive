package net.phoenix_archives.phoenix_archive.client;

import net.phoenixvine.wiki.theme.PhoenixTheme;

public class ArchivePalette {

    public static int BG, BG_SCRIM, PANEL, BORDER, BORDER_LIT;

    public static int TERM_BRIGHT, TERM, TERM_DIM, TERM_FAINT, TERM_HILITE;

    public static int TEXT, TEXT_DIM, TEXT_FAINT;

    public static int LOCKED, UNLOCKED;

    public static final int GOLD = 0xFFFFAA00;
    public static final int ALERT = 0xFFFF4444;
    public static final int ALERT_FILL = 0x22FF0000;
    
    public static final int WHITE = 0xFFFFFFFF;

    public static void refresh(PhoenixTheme t) {
        BG = t.bg.getColor();
        BG_SCRIM = (BG & 0x00FFFFFF) | 0xEE000000;
        PANEL = t.panel.getColor();
        BORDER = t.border.getColor();
        BORDER_LIT = t.accent.getColor();

        int accent = t.accent.getColor();
        TERM_BRIGHT = blendColor(accent, 0xFFFFFFFF, 0.35f);
        TERM = accent;
        TERM_DIM = blendColor(accent, 0xFF000000, 0.35f);
        TERM_FAINT = blendColor(accent, 0xFF000000, 0.6f);
        TERM_HILITE = blendColor(accent, 0xFFFFFFFF, 0.55f);

        TEXT = t.text.getColor();
        TEXT_DIM = t.textDim.getColor();
        TEXT_FAINT = t.textFaint.getColor();

        LOCKED = t.locked.getColor();
        UNLOCKED = t.done.getColor();
    }

    public static int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private static int blendColor(int color1, int color2, float ratio) {
        int a1 = (color1 >> 24) & 0xFF;
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;

        int a2 = (color2 >> 24) & 0xFF;
        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;

        int a = (int) (a1 + (a2 - a1) * ratio);
        int r = (int) (r1 + (r2 - r1) * ratio);
        int g = (int) (g1 + (g2 - g1) * ratio);
        int b = (int) (b1 + (b2 - b1) * ratio);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
