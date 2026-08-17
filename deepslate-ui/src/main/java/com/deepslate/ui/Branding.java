package com.deepslate.ui;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/**
 * Deepslate Client logo + smooth (non-pixel) rounded panels.
 * UI text uses vanilla Minecraft font.
 */
public final class Branding {
    private static final Identifier LOGO_TEXTURE =
            Identifier.fromNamespaceAndPath(DeepslateUiClient.MOD_ID, "textures/gui/logo_runtime");
    private static final String LOGO_CLASSPATH = "/assets/deepslate_ui/textures/gui/logo.png";

    private static boolean logoReady;
    private static boolean logoFailed;
    private static int logoTexW = 193;
    private static int logoTexH = 55;

    private Branding() {}

    public static void drawLogo(GuiGraphics graphics, int screenWidth, int topY, float alpha) {
        if (!DeepslateConfig.get().customLogo) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ensureLogo(mc);

        float a = Math.max(0.0f, Math.min(1.0f, alpha));
        if (a <= 0.01f) {
            return;
        }

        int drawH = DeepslateConfig.get().largerLogo ? 52 : 44;
        int drawW = Math.max(1, Math.round(drawH * (logoTexW / (float) logoTexH)));
        int x = screenWidth / 2 - drawW / 2;
        int y = topY;

        if (logoReady) {
            int tint = ARGB.white(a);
            graphics.blit(
                    RenderPipelines.GUI_TEXTURED,
                    LOGO_TEXTURE,
                    x,
                    y,
                    0.0f,
                    0.0f,
                    drawW,
                    drawH,
                    logoTexW,
                    logoTexH,
                    logoTexW,
                    logoTexH,
                    tint
            );
            if (a > 0.5f) {
                drawBrandCaption(graphics, screenWidth, y + drawH + 2);
            }
        } else {
            drawDarkPanel(graphics, x, y, drawW, drawH, false, true);
        }
    }

    public static void drawDimOverlay(GuiGraphics graphics, int width, int height) {
        graphics.fill(0, 0, width, height, Theme.OVERLAY_DIM);
    }

    private static NativeImage scaleBilinear(NativeImage src, int tw, int th) {
        int sw = src.getWidth();
        int sh = src.getHeight();
        NativeImage out = new NativeImage(tw, th, true);
        float xRatio = sw / (float) tw;
        float yRatio = sh / (float) th;
        for (int y = 0; y < th; y++) {
            for (int x = 0; x < tw; x++) {
                float sx = (x + 0.5f) * xRatio - 0.5f;
                float sy = (y + 0.5f) * yRatio - 0.5f;
                int x0 = Mth.clamp((int) Math.floor(sx), 0, sw - 1);
                int y0 = Mth.clamp((int) Math.floor(sy), 0, sh - 1);
                int x1 = Math.min(x0 + 1, sw - 1);
                int y1 = Math.min(y0 + 1, sh - 1);
                float fx = sx - x0;
                float fy = sy - y0;
                int c00 = src.getPixel(x0, y0);
                int c10 = src.getPixel(x1, y0);
                int c01 = src.getPixel(x0, y1);
                int c11 = src.getPixel(x1, y1);
                out.setPixel(x, y, lerpColor(lerpColor(c00, c10, fx), lerpColor(c01, c11, fx), fy));
            }
        }
        return out;
    }

    private static int lerpColor(int a, int b, float t) {
        t = Mth.clamp(t, 0f, 1f);
        int aa = ARGB.alpha(a);
        int ar = ARGB.red(a);
        int ag = ARGB.green(a);
        int ab = ARGB.blue(a);
        int ba = ARGB.alpha(b);
        int br = ARGB.red(b);
        int bg = ARGB.green(b);
        int bb = ARGB.blue(b);
        return ARGB.color(
                Math.round(aa + (ba - aa) * t),
                Math.round(ar + (br - ar) * t),
                Math.round(ag + (bg - ag) * t),
                Math.round(ab + (bb - ab) * t)
        );
    }

    private static void ensureLogo(Minecraft mc) {
        if (logoReady || logoFailed) {
            return;
        }
        try (InputStream in = Branding.class.getResourceAsStream(LOGO_CLASSPATH)) {
            if (in == null) {
                DeepslateUiClient.LOGGER.error("Deepslate logo missing from classpath: {}", LOGO_CLASSPATH);
                logoFailed = true;
                return;
            }
            NativeImage source = NativeImage.read(in);
            // Pre-downsample with bilinear — GUI blit is nearest-neighbor and looks blocky otherwise
            int targetH = 208; // ~4× on-screen logo height
            int targetW = Math.max(1, Math.round(targetH * (source.getWidth() / (float) source.getHeight())));
            NativeImage image = scaleBilinear(source, targetW, targetH);
            source.close();
            logoTexW = image.getWidth();
            logoTexH = image.getHeight();
            DynamicTexture texture = new DynamicTexture(() -> "deepslate_ui/logo", image);
            mc.getTextureManager().register(LOGO_TEXTURE, texture);
            logoReady = true;
            DeepslateUiClient.LOGGER.info("Registered Deepslate logo {}x{} (smooth)", logoTexW, logoTexH);
        } catch (Exception e) {
            DeepslateUiClient.LOGGER.error("Failed to register Deepslate logo texture", e);
            logoFailed = true;
        }
    }

    public static void drawDarkPanel(
            GuiGraphics graphics, int x, int y, int w, int h, boolean hovered, boolean active
    ) {
        int fill;
        int border;
        if (!active) {
            fill = Theme.PANEL_FILL_DISABLED;
            border = Theme.PANEL_BORDER_DISABLED;
        } else if (hovered) {
            fill = Theme.PANEL_FILL_HOVER;
            border = DeepslateConfig.get().cyanButtonAccent ? Theme.PANEL_BORDER_HOVER : Theme.PANEL_BORDER;
        } else {
            fill = Theme.PANEL_FILL;
            border = Theme.PANEL_BORDER;
        }
        int r = Math.min(Theme.BUTTON_RADIUS, Math.min(w, h) / 2);
        fillRounded(graphics, x + 1, y + 2, w, h, r, Theme.SHADOW);
        fillRounded(graphics, x, y, w, h, r, fill);
        drawRoundedBorder(graphics, x, y, w, h, r, border);
        if (active && w > r * 2) {
            int shine = hovered ? ARGB.color(55, 255, 255, 255) : Theme.SHINE;
            graphics.fill(x + r, y + 1, x + w - r, y + 2, shine);
            if (hovered && DeepslateConfig.get().cyanButtonAccent) {
                graphics.fill(x + 4, y + h - 3, x + w - 4, y + h - 1, Theme.ACCENT_SOFT);
            }
        }
    }

    /** Glass panel used by Deepslate menus (fill + soft border). */
    public static void drawGlassPanel(GuiGraphics graphics, int x, int y, int w, int h, int radius) {
        drawElevatedPanel(graphics, x, y, w, h, radius, Theme.PANEL_FILL, Theme.PANEL_BORDER);
    }

    /** Elevated panel with drop shadow + top shine. */
    public static void drawElevatedPanel(
            GuiGraphics graphics, int x, int y, int w, int h, int radius, int fill, int border
    ) {
        int r = Math.min(radius, Math.min(w, h) / 2);
        fillRounded(graphics, x + 2, y + 3, w, h, r, Theme.SHADOW);
        fillRounded(graphics, x, y, w, h, r, fill);
        drawRoundedBorder(graphics, x, y, w, h, r, border);
        if (w > r * 2) {
            graphics.fill(x + r, y + 1, x + w - r, y + 2, Theme.SHINE);
        }
    }

    /** Compact HUD capsule — cheap rects only (called every frame). */
    public static void drawHudPill(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, Theme.HUD_PILL);
        graphics.fill(x, y, x + w, y + 1, Theme.HUD_PILL_BORDER);
        graphics.fill(x, y + h - 1, x + w, y + h, Theme.HUD_PILL_BORDER);
        graphics.fill(x, y, x + 1, y + h, Theme.HUD_PILL_BORDER);
        graphics.fill(x + w - 1, y, x + w, y + h, Theme.HUD_PILL_BORDER);
    }

    /** Stronger HUD card — cheap rects only (called every frame). */
    public static void drawHudCard(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x + 1, y + 2, x + w + 1, y + h + 2, ARGB.color(50, 0, 0, 0));
        graphics.fill(x, y, x + w, y + h, Theme.HUD_PILL_STRONG);
        graphics.fill(x, y, x + w, y + 1, Theme.HUD_PILL_BORDER);
        graphics.fill(x, y + h - 1, x + w, y + h, Theme.HUD_PILL_BORDER);
        graphics.fill(x, y, x + 1, y + h, Theme.HUD_PILL_BORDER);
        graphics.fill(x + w - 1, y, x + w, y + h, Theme.HUD_PILL_BORDER);
        if (w > 12) {
            graphics.fill(x + 4, y + 1, x + w - 4, y + 2, Theme.SHINE);
        }
    }

    /** Thin accent indicator bar (sidebar / active rows). */
    public static void drawAccentBar(GuiGraphics graphics, int x, int y, int h) {
        graphics.fill(x, y, x + 3, y + Math.max(4, h), Theme.ACCENT);
    }

    /** Soft mint washes + vignette behind Deepslate menus (no AA — cheap fills). */
    public static void drawMenuBackdrop(GuiGraphics graphics, int width, int height) {
        graphics.fill(0, 0, width, height, Theme.SPACE_1);
        graphics.fill(Math.max(0, width - 280), 0, width, 160, Theme.ACCENT_WASH);
        graphics.fill(0, Math.max(0, height - 180), 200, height, ARGB.color(24, 40, 70, 80));
        graphics.fill(0, 0, width, 36, ARGB.color(110, 0, 0, 0));
        graphics.fill(0, height - 48, width, height, ARGB.color(100, 0, 0, 0));
    }

    /** Pause / title brand strip under the logo. */
    public static void drawBrandCaption(GuiGraphics graphics, int screenWidth, int y) {
        Minecraft mc = Minecraft.getInstance();
        String line = "DEEPSLATE CLIENT";
        int tw = mc.font.width(line);
        int x = screenWidth / 2 - tw / 2;
        graphics.drawString(mc.font, line, x + 1, y + 1, ARGB.color(140, 0, 0, 0), false);
        graphics.drawString(mc.font, line, x, y, Theme.ACCENT, false);
        int barW = Math.min(72, tw);
        graphics.fill(screenWidth / 2 - barW / 2, y + 11, screenWidth / 2 + barW / 2, y + 13, Theme.ACCENT_SOFT);
    }

    /**
     * Fast rounded rect using a few batch fills + scanline corners (no per-pixel AA).
     * Safe to call every frame / with large radii.
     */
    public static void fillRounded(GuiGraphics graphics, int x, int y, int w, int h, int radius, int color) {
        if (w <= 0 || h <= 0) {
            return;
        }
        int r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
        if (r <= 0) {
            graphics.fill(x, y, x + w, y + h, color);
            return;
        }

        // Center slabs
        graphics.fill(x + r, y, x + w - r, y + h, color);
        graphics.fill(x, y + r, x + r, y + h - r, color);
        graphics.fill(x + w - r, y + r, x + w, y + h - r, color);

        // Corners as one horizontal span per row (O(r), not O(r²) draw calls)
        fillCornerScan(graphics, x, y, r, color, true, true);
        fillCornerScan(graphics, x + w - r, y, r, color, false, true);
        fillCornerScan(graphics, x, y + h - r, r, color, true, false);
        fillCornerScan(graphics, x + w - r, y + h - r, r, color, false, false);
    }

    private static void fillCornerScan(
            GuiGraphics graphics, int ox, int oy, int r, int color, boolean left, boolean top
    ) {
        int r2 = r * r;
        for (int row = 0; row < r; row++) {
            int dy = top ? (r - 1 - row) : row;
            int dx = (int) Math.floor(Math.sqrt(r2 - (double) dy * dy));
            if (dx <= 0) {
                continue;
            }
            int py = oy + row;
            if (left) {
                graphics.fill(ox + r - dx, py, ox + r, py + 1, color);
            } else {
                graphics.fill(ox, py, ox + dx, py + 1, color);
            }
        }
    }

    public static void drawRoundedBorder(GuiGraphics graphics, int x, int y, int w, int h, int radius, int color) {
        if (w <= 0 || h <= 0) {
            return;
        }
        int r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
        if (r <= 0) {
            graphics.fill(x, y, x + w, y + 1, color);
            graphics.fill(x, y + h - 1, x + w, y + h, color);
            graphics.fill(x, y, x + 1, y + h, color);
            graphics.fill(x + w - 1, y, x + w, y + h, color);
            return;
        }

        graphics.fill(x + r, y, x + w - r, y + 1, color);
        graphics.fill(x + r, y + h - 1, x + w - r, y + h, color);
        graphics.fill(x, y + r, x + 1, y + h - r, color);
        graphics.fill(x + w - 1, y + r, x + w, y + h - r, color);

        ringCornerScan(graphics, x, y, r, color, true, true);
        ringCornerScan(graphics, x + w - r, y, r, color, false, true);
        ringCornerScan(graphics, x, y + h - r, r, color, true, false);
        ringCornerScan(graphics, x + w - r, y + h - r, r, color, false, false);
    }

    private static void ringCornerScan(
            GuiGraphics graphics, int ox, int oy, int r, int color, boolean left, boolean top
    ) {
        int outer2 = r * r;
        int inner = Math.max(0, r - 1);
        int inner2 = inner * inner;
        for (int row = 0; row < r; row++) {
            int dy = top ? (r - 1 - row) : row;
            int dy2 = dy * dy;
            if (dy2 > outer2) {
                continue;
            }
            int dxOuter = (int) Math.floor(Math.sqrt(outer2 - dy2));
            int dxInner = dy2 >= inner2 ? 0 : (int) Math.floor(Math.sqrt(inner2 - dy2));
            if (dxOuter <= dxInner) {
                continue;
            }
            int py = oy + row;
            if (left) {
                graphics.fill(ox + r - dxOuter, py, ox + r - dxInner, py + 1, color);
            } else {
                graphics.fill(ox + dxInner, py, ox + dxOuter, py + 1, color);
            }
        }
    }
}
