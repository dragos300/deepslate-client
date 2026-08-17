package com.deepslate.ui.hud;

import com.deepslate.ui.Branding;
import com.deepslate.ui.DeepslateConfig;
import com.deepslate.ui.LegacyCompat;
import com.deepslate.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.ARGB;

/**
 * Modern-client HUD: glass pills for info lines + polished keystrokes.
 */
public final class HudOverlayRenderer {
    private static final int PAD_X = 7;
    private static final int PAD_Y = 4;
    private static final int TEXT_H = 8;
    private static final int KEY_SIZE = 20;
    private static final int KEY_GAP = 3;

    private HudOverlayRenderer() {}

    public static void render(GuiGraphics graphics, int screenWidth, int screenHeight) {
        render(graphics, screenWidth, screenHeight, false);
    }

    public static void render(GuiGraphics graphics, int screenWidth, int screenHeight, boolean editMode) {
        if (LegacyCompat.isActive()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!editMode && (mc.options.hideGui || mc.gui.getDebugOverlay().showDebugScreen())) {
            return;
        }
        if (!editMode && (!HudData.anyHudEnabled() || !HudData.hasPlayer)) {
            return;
        }

        DeepslateConfig cfg = DeepslateConfig.get();
        HudLayout.ensureDefaults(cfg);
        Font font = mc.font;

        if (cfg.hudFps || editMode) {
            renderLine(graphics, font, HudModule.FPS, "FPS", String.valueOf(HudData.fps), screenWidth, screenHeight, editMode);
        }
        if ((cfg.hudPing && HudData.ping >= 0) || (editMode && cfg.hudPing)) {
            String ping = HudData.ping >= 0 ? HudData.ping + "ms" : "—";
            renderLine(graphics, font, HudModule.PING, "PING", ping, screenWidth, screenHeight, editMode);
        }
        if (cfg.hudCoords || editMode) {
            String xyz = String.format("%.0f %.0f %.0f", HudData.x, HudData.y, HudData.z);
            renderLine(graphics, font, HudModule.COORDS, "XYZ", xyz, screenWidth, screenHeight, editMode);
        }
        if (cfg.hudBiome || editMode) {
            String b = HudData.biome == null || HudData.biome.isEmpty() ? "—" : HudData.biome;
            renderLine(graphics, font, HudModule.BIOME, "BIOME", b, screenWidth, screenHeight, editMode);
        }
        if (cfg.hudDayCounter || editMode) {
            renderLine(graphics, font, HudModule.DAY, "DAY", String.valueOf(HudData.day), screenWidth, screenHeight, editMode);
        }
        if (cfg.hudKeystrokes || editMode) {
            renderKeystrokes(graphics, font, screenWidth, screenHeight, editMode);
        }

        ArmorHudRenderer.render(graphics, screenWidth, screenHeight, editMode);
        EffectsHudRenderer.render(graphics, screenWidth, screenHeight, editMode);
        ToolHudRenderer.render(graphics, screenWidth, screenHeight, editMode);
        ItemCountHudRenderer.render(graphics, screenWidth, screenHeight, editMode);
    }

    /** Hitbox / layout size for a label+value pill. */
    public static int[] boxSize(Font font, String label, String value) {
        int inner = font.width(label) + 5 + font.width(value);
        return new int[] {inner + PAD_X * 2, TEXT_H + PAD_Y * 2};
    }

    private static void renderLine(
            GuiGraphics graphics,
            Font font,
            HudModule module,
            String label,
            String value,
            int screenW,
            int screenH,
            boolean editMode
    ) {
        DeepslateConfig cfg = DeepslateConfig.get();
        int[] size = boxSize(font, label, value);
        int w = size[0];
        int h = size[1];
        int x = HudLayout.pixelX(module.pos(cfg), screenW, w);
        int y = HudLayout.pixelY(module.pos(cfg), screenH, h);

        Branding.drawHudPill(graphics, x, y, w, h);

        if (editMode) {
            graphics.fill(x - 1, y - 1, x + w + 1, y + h + 1, Theme.ACCENT_GLOW);
        }

        int tx = x + PAD_X;
        int ty = y + PAD_Y;
        graphics.drawString(font, label, tx + 1, ty + 1, ARGB.color(140, 0, 0, 0), false);
        graphics.drawString(font, label, tx, ty, Theme.ACCENT, false);
        int vx = tx + font.width(label) + 5;
        graphics.drawString(font, value, vx + 1, ty + 1, ARGB.color(140, 0, 0, 0), false);
        graphics.drawString(font, value, vx, ty, Theme.TEXT, false);
    }

    public static int keystrokesWidth() {
        return KEY_SIZE * 3 + KEY_GAP * 2 + 8;
    }

    public static int keystrokesHeight() {
        return KEY_SIZE * 3 + KEY_GAP * 2 + 6 + KEY_SIZE + 8;
    }

    private static void renderKeystrokes(
            GuiGraphics graphics, Font font, int screenW, int screenH, boolean editMode
    ) {
        DeepslateConfig cfg = DeepslateConfig.get();
        int clusterW = keystrokesWidth();
        int clusterH = keystrokesHeight();
        int originX = HudLayout.pixelX(cfg.posKeystrokes, screenW, clusterW);
        int originY = HudLayout.pixelY(cfg.posKeystrokes, screenH, clusterH);

        Branding.drawHudCard(graphics, originX, originY, clusterW, clusterH);
        if (editMode) {
            graphics.fill(originX - 1, originY - 1, originX + clusterW + 1, originY + clusterH + 1, Theme.ACCENT_GLOW);
        }

        int ox = originX + 4;
        int oy = originY + 4;
        int innerW = clusterW - 8;

        drawKey(graphics, font, ox + KEY_SIZE + KEY_GAP, oy, "W", HudData.keyForward);
        drawKey(graphics, font, ox, oy + KEY_SIZE + KEY_GAP, "A", HudData.keyLeft);
        drawKey(graphics, font, ox + KEY_SIZE + KEY_GAP, oy + KEY_SIZE + KEY_GAP, "S", HudData.keyBack);
        drawKey(graphics, font, ox + (KEY_SIZE + KEY_GAP) * 2, oy + KEY_SIZE + KEY_GAP, "D", HudData.keyRight);

        int spaceY = oy + (KEY_SIZE + KEY_GAP) * 2;
        drawKeyWide(graphics, font, ox, spaceY, innerW, "SPACE", HudData.keyJump);

        int mouseY = spaceY + KEY_SIZE + 4;
        int half = (innerW - KEY_GAP) / 2;
        drawKeyWide(graphics, font, ox, mouseY, half, "LMB", HudData.mouseLeft);
        drawKeyWide(graphics, font, ox + half + KEY_GAP, mouseY, half, "RMB", HudData.mouseRight);
    }

    private static void drawKey(GuiGraphics graphics, Font font, int x, int y, String label, boolean down) {
        int fill = down ? Theme.ACCENT : Theme.KEY_IDLE;
        int text = down ? Theme.SPACE_1 : Theme.TEXT;
        graphics.fill(x, y, x + KEY_SIZE, y + KEY_SIZE, fill);
        graphics.fill(x, y, x + KEY_SIZE, y + 1, down ? Theme.ACCENT : Theme.KEY_BORDER);
        graphics.fill(x, y + KEY_SIZE - 1, x + KEY_SIZE, y + KEY_SIZE, down ? Theme.ACCENT : Theme.KEY_BORDER);
        graphics.fill(x, y, x + 1, y + KEY_SIZE, down ? Theme.ACCENT : Theme.KEY_BORDER);
        graphics.fill(x + KEY_SIZE - 1, y, x + KEY_SIZE, y + KEY_SIZE, down ? Theme.ACCENT : Theme.KEY_BORDER);
        int tw = font.width(label);
        graphics.drawString(font, label, x + (KEY_SIZE - tw) / 2, y + (KEY_SIZE - 8) / 2, text, false);
    }

    private static void drawKeyWide(
            GuiGraphics graphics, Font font, int x, int y, int w, String label, boolean down
    ) {
        int fill = down ? Theme.ACCENT : Theme.KEY_IDLE;
        int text = down ? Theme.SPACE_1 : Theme.TEXT;
        graphics.fill(x, y, x + w, y + KEY_SIZE, fill);
        graphics.fill(x, y, x + w, y + 1, down ? Theme.ACCENT : Theme.KEY_BORDER);
        graphics.fill(x, y + KEY_SIZE - 1, x + w, y + KEY_SIZE, down ? Theme.ACCENT : Theme.KEY_BORDER);
        graphics.fill(x, y, x + 1, y + KEY_SIZE, down ? Theme.ACCENT : Theme.KEY_BORDER);
        graphics.fill(x + w - 1, y, x + w, y + KEY_SIZE, down ? Theme.ACCENT : Theme.KEY_BORDER);
        int tw = font.width(label);
        graphics.drawString(font, label, x + (w - tw) / 2, y + (KEY_SIZE - 8) / 2, text, false);
    }
}
