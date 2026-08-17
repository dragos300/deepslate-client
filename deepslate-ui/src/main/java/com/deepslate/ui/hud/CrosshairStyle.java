package com.deepslate.ui.hud;

/** Geometric crosshair presets. */
public enum CrosshairStyle {
    CROSS("Cross"),
    CROSS_DOT("Cross + Dot"),
    CIRCLE("Circle"),
    CIRCLE_DOT("Circle + Dot"),
    SQUARE("Square"),
    DOT("Dot"),
    T_SHAPE("T"),
    ARROW("Arrow"),
    PLUS("Plus");

    private final String label;

    CrosshairStyle(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static CrosshairStyle fromId(String id) {
        if (id == null || id.isBlank()) {
            return CROSS;
        }
        try {
            return CrosshairStyle.valueOf(id.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return CROSS;
        }
    }
}
