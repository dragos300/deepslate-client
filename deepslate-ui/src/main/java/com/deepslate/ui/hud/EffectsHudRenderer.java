package com.deepslate.ui.hud;

import com.deepslate.ui.DeepslateConfig;
import com.deepslate.ui.LegacyCompat;
import com.deepslate.ui.Theme;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * Status-effect icons with overlaid duration / amplifier.
 * Layout inspired by Status Effect Timer (magicus) — original Deepslate styling.
 */
public final class EffectsHudRenderer {
    private static final int CELL = 24;
    private static final int ICON = 18;
    private static final int GAP = 1;
    private static final String[] ROMAN = {
        "", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"
    };

    private static final Identifier BG =
            Identifier.withDefaultNamespace("hud/effect_background");
    private static final Identifier BG_AMBIENT =
            Identifier.withDefaultNamespace("hud/effect_background_ambient");

    private EffectsHudRenderer() {}

    public static boolean shouldReplaceVanilla() {
        return !LegacyCompat.isActive() && DeepslateConfig.get().hudEffects;
    }

    public static int estimateWidth(Font font, List<MobEffectInstance> effects) {
        return CELL;
    }

    public static int estimateHeight(int count) {
        return Math.max(1, count) * (CELL + GAP) - GAP;
    }

    public static void render(GuiGraphics graphics, int screenWidth, int screenHeight, boolean editMode) {
        if (LegacyCompat.isActive() || (!DeepslateConfig.get().hudEffects && !editMode)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (!editMode && (player == null || mc.options.hideGui || mc.gui.getDebugOverlay().showDebugScreen())) {
            return;
        }

        Font font = mc.font;
        List<MobEffectInstance> effects =
                player == null ? List.of() : new ArrayList<>(player.getActiveEffects());
        if (effects.isEmpty() && !editMode) {
            return;
        }
        effects.sort(
                Comparator.comparing((MobEffectInstance e) -> !e.getEffect().value().isBeneficial())
                        .thenComparingInt(MobEffectInstance::getDuration)
        );

        DeepslateConfig cfg = DeepslateConfig.get();
        HudLayout.ensureDefaults(cfg);
        int panelW = CELL;
        int panelH = estimateHeight(Math.max(1, effects.size()));
        int x = HudLayout.pixelX(cfg.posEffects, screenWidth, panelW);
        int y = HudLayout.pixelY(cfg.posEffects, screenHeight, panelH);

        if (editMode && effects.isEmpty()) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BG, x, y, CELL, CELL);
            graphics.fill(x, y, x + CELL, y + 1, Theme.ACCENT);
            return;
        }

        int rowY = y;
        for (MobEffectInstance inst : effects) {
            drawEffect(graphics, font, inst, x, rowY);
            rowY += CELL + GAP;
        }
    }

    private static void drawEffect(GuiGraphics graphics, Font font, MobEffectInstance inst, int x, int y) {
        Holder<MobEffect> holder = inst.getEffect();
        Identifier bg = inst.isAmbient() ? BG_AMBIENT : BG;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, bg, x, y, CELL, CELL);

        Identifier sprite = Gui.getMobEffectSprite(holder);
        int iconX = x + (CELL - ICON) / 2;
        int iconY = y + (CELL - ICON) / 2;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, iconX, iconY, ICON, ICON);

        // Amplifier (top-left), Status Effect Timer style
        int amp = inst.getAmplifier();
        if (amp > 0) {
            String roman = amp + 1 < ROMAN.length ? ROMAN[amp + 1] : String.valueOf(amp + 1);
            drawOverlayText(graphics, font, roman, x + 3, y + 2, Theme.TEXT);
        }

        // Duration overlay at bottom center
        String time = formatDuration(inst);
        int tw = font.width(time);
        int tx = x + (CELL - tw) / 2;
        int ty = y + CELL - 10;
        int color = timerColor(inst);
        drawOverlayText(graphics, font, time, tx, ty, color);
    }

    private static void drawOverlayText(GuiGraphics graphics, Font font, String text, int x, int y, int color) {
        // Soft shadow so the label stays readable on bright icons
        graphics.drawString(font, text, x + 1, y + 1, ARGB.color(160, 0, 0, 0), false);
        graphics.drawString(font, text, x, y, color, false);
    }

    private static int timerColor(MobEffectInstance inst) {
        if (inst.isInfiniteDuration()) {
            return Theme.ACCENT;
        }
        int secs = inst.getDuration() / 20;
        if (secs <= 5) {
            // Pulse when almost expired
            long t = System.currentTimeMillis() % 600L;
            return t < 300 ? ARGB.color(255, 255, 70, 70) : Theme.TEXT;
        }
        if (secs <= 15) {
            return ARGB.color(255, 255, 180, 80);
        }
        return Theme.TEXT;
    }

    /** Status Effect Timer–style units: s / m / h / ∞ */
    private static String formatDuration(MobEffectInstance inst) {
        if (inst.isInfiniteDuration()) {
            return "∞";
        }
        int total = Math.max(0, inst.getDuration() / 20);
        if (total >= 3600) {
            return (total / 3600) + "h";
        }
        if (total >= 60) {
            return (total / 60) + "m";
        }
        return String.valueOf(total);
    }
}
