package com.deepslate.ui.hud;

/** Normalized screen position (0–1) for the top-left of a HUD module. */
public final class HudPos {
    public float x = 0.01f;
    public float y = 0.01f;

    public HudPos() {}

    public HudPos(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public HudPos copy() {
        return new HudPos(x, y);
    }
}
