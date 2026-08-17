package com.deepslate.ui.hud;

import com.deepslate.ui.DeepslateConfig;
import net.minecraft.util.Mth;

public final class HudLayout {
    private HudLayout() {}

    public static int pixelX(HudPos pos, int screenW, int moduleW) {
        float x = pos == null ? 0.01f : pos.x;
        return Mth.clamp(Math.round(x * screenW), 0, Math.max(0, screenW - moduleW));
    }

    public static int pixelY(HudPos pos, int screenH, int moduleH) {
        float y = pos == null ? 0.01f : pos.y;
        return Mth.clamp(Math.round(y * screenH), 0, Math.max(0, screenH - moduleH));
    }

    public static void setFromPixels(HudPos pos, int px, int py, int screenW, int screenH, int moduleW, int moduleH) {
        if (pos == null) {
            return;
        }
        int maxX = Math.max(1, screenW - moduleW);
        int maxY = Math.max(1, screenH - moduleH);
        pos.x = Mth.clamp(px, 0, maxX) / (float) screenW;
        pos.y = Mth.clamp(py, 0, maxY) / (float) screenH;
    }

    public static void ensureDefaults(DeepslateConfig cfg) {
        if (cfg.posFps == null) cfg.posFps = new HudPos(0.01f, 0.02f);
        if (cfg.posPing == null) cfg.posPing = new HudPos(0.01f, 0.07f);
        if (cfg.posCoords == null) cfg.posCoords = new HudPos(0.01f, 0.12f);
        if (cfg.posBiome == null) cfg.posBiome = new HudPos(0.01f, 0.17f);
        if (cfg.posDay == null) cfg.posDay = new HudPos(0.01f, 0.22f);
        if (cfg.posKeystrokes == null) cfg.posKeystrokes = new HudPos(0.01f, 0.72f);
        if (cfg.posArmor == null) cfg.posArmor = new HudPos(0.01f, 0.55f);
        if (cfg.posEffects == null) cfg.posEffects = new HudPos(0.78f, 0.02f);
        if (cfg.posTools == null) cfg.posTools = new HudPos(0.55f, 0.86f);
        if (cfg.posItemCount == null) cfg.posItemCount = new HudPos(0.70f, 0.86f);
    }
}
