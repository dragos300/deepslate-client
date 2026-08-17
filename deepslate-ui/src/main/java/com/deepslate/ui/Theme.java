package com.deepslate.ui;

import net.minecraft.util.ARGB;

/**
 * Deepslate Client palette — matches the launcher (mineral mint on deep slate).
 */
public final class Theme {
    public static final int SPACE_1 = 0xFF06080A;
    public static final int SPACE_2 = 0xFF0C1114;
    public static final int SPACE_3 = 0xFF12181C;
    public static final int SPACE_4 = 0xFF181F24;
    public static final int SPACE_5 = 0xFF222A30;
    public static final int SPACE_7 = 0xFF3A454C;
    public static final int SPACE_12 = 0xFFF2F5F3;

    /** Mineral mint — same accent as the Electron launcher. */
    public static final int ACCENT = 0xFF3FD4B8;
    public static final int ACCENT_DIM = 0xFF2BB89E;
    public static final int ACCENT_SOFT = ARGB.color(180, 63, 212, 184);
    public static final int ACCENT_GLOW = ARGB.color(55, 63, 212, 184);
    public static final int ACCENT_WASH = ARGB.color(28, 63, 212, 184);

    public static final int TEXT_MUTED = ARGB.color(255, 139, 150, 143);
    public static final int TEXT_FAINT = ARGB.color(255, 92, 102, 96);

    public static final int PANEL_FILL = ARGB.color(235, 12, 17, 20);
    public static final int PANEL_FILL_HOVER = ARGB.color(245, 38, 48, 54);
    public static final int PANEL_FILL_DISABLED = ARGB.color(160, 6, 8, 10);

    public static final int PANEL_BORDER = ARGB.color(210, 64, 76, 84);
    public static final int PANEL_BORDER_HOVER = ACCENT;
    public static final int PANEL_BORDER_DISABLED = ARGB.color(120, 42, 50, 56);

    public static final int HUD_PILL = ARGB.color(165, 8, 12, 14);
    public static final int HUD_PILL_BORDER = ARGB.color(140, 63, 212, 184);
    public static final int HUD_PILL_STRONG = ARGB.color(190, 10, 15, 18);

    public static final int SHADOW = ARGB.color(80, 0, 0, 0);
    public static final int SHINE = ARGB.color(36, 255, 255, 255);

    public static final int OVERLAY_DIM = ARGB.color(130, 0, 0, 0);
    public static final int KEY_IDLE = ARGB.color(175, 10, 14, 16);
    public static final int KEY_BORDER = ARGB.color(160, 58, 69, 76);

    public static final int TEXT = SPACE_12;
    public static final int BUTTON_RADIUS = 9;

    public static final int LOGO_BLOCK_SIZE = 80;

    private Theme() {}
}
