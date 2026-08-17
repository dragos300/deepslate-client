package com.deepslate.ui.hud;

import com.deepslate.ui.DeepslateConfig;
import com.deepslate.ui.LegacyCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Configurable geometric Deepslate crosshair with style presets.
 */
public final class CrosshairRenderer {
    private CrosshairRenderer() {}

    public static boolean shouldReplace() {
        if (LegacyCompat.isActive() || !DeepslateConfig.get().customCrosshair) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        return !mc.options.hideGui && !mc.gui.getDebugOverlay().showDebugScreen();
    }

    public static void render(GuiGraphics graphics, int screenWidth, int screenHeight) {
        renderAt(graphics, screenWidth / 2, screenHeight / 2, DeepslateConfig.get());
    }

    public static void renderPreview(GuiGraphics graphics, int centerX, int centerY) {
        renderAt(graphics, centerX, centerY, DeepslateConfig.get());
    }

    private static void renderAt(GuiGraphics graphics, int centerX, int centerY, DeepslateConfig c) {
        CrosshairStyle style = CrosshairStyle.fromId(c.crosshairStyle);
        int arm = Math.max(1, Math.min(20, c.crosshairSize));
        int gap = Math.max(0, Math.min(12, c.crosshairGap));
        int thick = Math.max(1, Math.min(8, c.crosshairThickness));
        int outlineT = c.crosshairOutline ? Math.max(1, Math.min(4, c.crosshairOutlineThickness)) : 0;
        int color = c.crosshairColor;
        int outline = c.crosshairOutlineColor;
        boolean forceDot = c.crosshairDot
                || style == CrosshairStyle.CROSS_DOT
                || style == CrosshairStyle.CIRCLE_DOT
                || style == CrosshairStyle.DOT;
        int ds = Math.max(1, Math.min(6, c.crosshairDotSize));

        switch (style) {
            case CIRCLE, CIRCLE_DOT -> drawCircle(graphics, centerX, centerY, arm + gap, thick, color, outline, outlineT);
            case SQUARE -> drawSquare(graphics, centerX, centerY, arm + gap, thick, color, outline, outlineT);
            case DOT -> {
                /* only center dot */
            }
            case T_SHAPE -> drawT(graphics, centerX, centerY, arm, gap, thick, color, outline, outlineT);
            case ARROW -> drawArrow(graphics, centerX, centerY, arm, gap, thick, color, outline, outlineT);
            case PLUS -> drawCross(graphics, centerX, centerY, arm, 0, thick, color, outline, outlineT);
            case CROSS_DOT, CROSS -> drawCross(graphics, centerX, centerY, arm, gap, thick, color, outline, outlineT);
        }

        if (forceDot && style != CrosshairStyle.DOT) {
            fillOutlined(graphics, centerX - ds / 2, centerY - ds / 2, ds, ds, color, outline, outlineT);
        } else if (style == CrosshairStyle.DOT) {
            int size = Math.max(ds, thick + 1);
            fillOutlined(graphics, centerX - size / 2, centerY - size / 2, size, size, color, outline, outlineT);
        }
    }

    private static void drawCross(
            GuiGraphics g, int cx, int cy, int arm, int gap, int thick, int color, int outline, int ot
    ) {
        int hx = cx - thick / 2;
        int hy = cy - thick / 2;
        fillOutlined(g, hx, hy - gap - arm, thick, arm, color, outline, ot);
        fillOutlined(g, hx, hy + gap + thick, thick, arm, color, outline, ot);
        fillOutlined(g, hx - gap - arm, hy, arm, thick, color, outline, ot);
        fillOutlined(g, hx + gap + thick, hy, arm, thick, color, outline, ot);
    }

    private static void drawT(
            GuiGraphics g, int cx, int cy, int arm, int gap, int thick, int color, int outline, int ot
    ) {
        int hx = cx - thick / 2;
        int hy = cy - thick / 2;
        fillOutlined(g, hx - gap - arm, hy - gap - thick, arm * 2 + gap * 2 + thick, thick, color, outline, ot);
        fillOutlined(g, hx, hy + gap, thick, arm, color, outline, ot);
    }

    private static void drawArrow(
            GuiGraphics g, int cx, int cy, int arm, int gap, int thick, int color, int outline, int ot
    ) {
        int hx = cx - thick / 2;
        int hy = cy - thick / 2;
        fillOutlined(g, hx, hy - gap - arm, thick, arm, color, outline, ot);
        fillOutlined(g, hx - arm / 2 - gap, hy - gap - thick, arm / 2 + gap, thick, color, outline, ot);
        fillOutlined(g, hx + thick, hy - gap - thick, arm / 2 + gap, thick, color, outline, ot);
        fillOutlined(g, hx, hy + gap + thick, thick, arm / 2, color, outline, ot);
    }

    private static void drawCircle(
            GuiGraphics g, int cx, int cy, int radius, int thick, int color, int outline, int ot
    ) {
        int r = Math.max(2, radius);
        for (int a = 0; a < 360; a += 8) {
            double rad = Math.toRadians(a);
            int x = cx + (int) Math.round(Math.cos(rad) * r) - thick / 2;
            int y = cy + (int) Math.round(Math.sin(rad) * r) - thick / 2;
            fillOutlined(g, x, y, thick, thick, color, outline, ot);
        }
    }

    private static void drawSquare(
            GuiGraphics g, int cx, int cy, int half, int thick, int color, int outline, int ot
    ) {
        int s = Math.max(2, half);
        // top / bottom
        fillOutlined(g, cx - s, cy - s, s * 2, thick, color, outline, ot);
        fillOutlined(g, cx - s, cy + s - thick, s * 2, thick, color, outline, ot);
        // left / right
        fillOutlined(g, cx - s, cy - s, thick, s * 2, color, outline, ot);
        fillOutlined(g, cx + s - thick, cy - s, thick, s * 2, color, outline, ot);
    }

    private static void fillOutlined(
            GuiGraphics graphics, int x, int y, int w, int h, int fill, int outline, int outlineT
    ) {
        w = Math.max(1, w);
        h = Math.max(1, h);
        if (outlineT > 0) {
            graphics.fill(x - outlineT, y - outlineT, x + w + outlineT, y + h + outlineT, outline);
        }
        graphics.fill(x, y, x + w, y + h, fill);
    }
}
