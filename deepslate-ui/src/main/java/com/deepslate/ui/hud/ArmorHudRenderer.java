package com.deepslate.ui.hud;

import com.deepslate.ui.Branding;
import com.deepslate.ui.DeepslateConfig;
import com.deepslate.ui.LegacyCompat;
import com.deepslate.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Vertical armor strip: helmet → boots, glass card + durability bar + percent.
 */
public final class ArmorHudRenderer {
    private static final EquipmentSlot[] SLOTS = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private static final int ICON = 16;
    private static final int ROW = 18;
    private static final int BAR_W = 40;
    private static final int BAR_H = 3;
    private static final int GAP_ICON_BAR = 3;
    private static final int GAP_BAR_TEXT = 4;
    private static final int PAD = 6;

    private ArmorHudRenderer() {}

    public static int width(Font font) {
        return ICON + GAP_ICON_BAR + BAR_W + GAP_BAR_TEXT + font.width("100%") + PAD * 2;
    }

    public static int width() {
        return ICON + GAP_ICON_BAR + BAR_W + GAP_BAR_TEXT + 24 + PAD * 2;
    }

    public static int height() {
        return SLOTS.length * ROW + PAD * 2;
    }

    public static void render(GuiGraphics graphics, int screenWidth, int screenHeight, boolean editMode) {
        if (LegacyCompat.isActive() || (!DeepslateConfig.get().hudArmorStatus && !editMode)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (!editMode && (player == null || mc.options.hideGui || mc.gui.getDebugOverlay().showDebugScreen())) {
            return;
        }

        DeepslateConfig cfg = DeepslateConfig.get();
        HudLayout.ensureDefaults(cfg);
        Font font = mc.font;
        int w = width(font);
        int h = height();
        int x = HudLayout.pixelX(cfg.posArmor, screenWidth, w);
        int y = HudLayout.pixelY(cfg.posArmor, screenHeight, h);

        boolean any = false;
        if (player != null) {
            for (EquipmentSlot slot : SLOTS) {
                if (!player.getItemBySlot(slot).isEmpty()) {
                    any = true;
                    break;
                }
            }
        }
        if (!any && !editMode) {
            return;
        }

        if (editMode) {
            graphics.fill(x - 2, y - 2, x + w + 2, y + h + 2, Theme.ACCENT_GLOW);
        }

        Branding.drawHudCard(graphics, x, y, w, h);

        int rowY = y + PAD;
        int contentX = x + PAD;
        for (EquipmentSlot slot : SLOTS) {
            ItemStack stack = player != null ? player.getItemBySlot(slot) : ItemStack.EMPTY;
            drawRow(graphics, font, stack, contentX, rowY, editMode, cfg);
            rowY += ROW;
        }
    }

    private static void drawRow(
            GuiGraphics graphics,
            Font font,
            ItemStack stack,
            int x,
            int y,
            boolean editMode,
            DeepslateConfig cfg
    ) {
        int iconY = y + (ROW - ICON) / 2;

        if (!stack.isEmpty()) {
            graphics.renderItem(stack, x, iconY);
            // Skip vanilla durability overlay — we draw our own bar
        } else if (editMode) {
            graphics.fill(x, iconY, x + ICON, iconY + ICON, ARGB.color(70, 47, 49, 59));
        } else {
            return;
        }

        float pct = durabilityPercent(stack);
        boolean low = !stack.isEmpty() && isLowDurability(stack, cfg.armorWarningPercent);
        int barColor = barColor(pct, low);

        int barX = x + ICON + GAP_ICON_BAR;
        int barY = y + (ROW - BAR_H) / 2;

        // Track
        graphics.fill(barX, barY, barX + BAR_W, barY + BAR_H, ARGB.color(200, 6, 8, 10));
        int fillW = stack.isEmpty() ? 0 : Math.max(0, Math.round(BAR_W * Mth.clamp(pct, 0f, 1f)));
        if (fillW > 0) {
            graphics.fill(barX, barY, barX + fillW, barY + BAR_H, barColor);
        }

        if (!stack.isEmpty()) {
            String text = Math.round(pct * 100f) + "%";
            if (low) {
                long t = System.currentTimeMillis() % 800L;
                if (t < 400) {
                    text = "!";
                }
            }
            int tx = barX + BAR_W + GAP_BAR_TEXT;
            int ty = y + (ROW - 8) / 2;
            int textColor = low ? ARGB.color(255, 255, 80, 80) : Theme.TEXT;
            graphics.drawString(font, text, tx + 1, ty + 1, ARGB.color(140, 0, 0, 0), false);
            graphics.drawString(font, text, tx, ty, textColor, false);
        }
    }

    private static float durabilityPercent(ItemStack stack) {
        if (stack.isEmpty() || !stack.isDamageableItem()) {
            return 1f;
        }
        int max = Math.max(1, stack.getMaxDamage());
        return 1f - stack.getDamageValue() / (float) max;
    }

    private static int barColor(float pct, boolean low) {
        if (low) {
            long t = System.currentTimeMillis() % 800L;
            return t < 400 ? ARGB.color(255, 255, 60, 60) : ARGB.color(255, 200, 40, 40);
        }
        // Green → yellow → red
        if (pct > 0.5f) {
            float t = (pct - 0.5f) * 2f;
            return lerpRgb(ARGB.color(255, 220, 180, 40), Theme.ACCENT, t);
        }
        float t = pct * 2f;
        return lerpRgb(ARGB.color(255, 230, 60, 60), ARGB.color(255, 220, 180, 40), t);
    }

    private static int lerpRgb(int a, int b, float t) {
        t = Mth.clamp(t, 0f, 1f);
        int ar = ARGB.red(a), ag = ARGB.green(a), ab = ARGB.blue(a);
        int br = ARGB.red(b), bg = ARGB.green(b), bb = ARGB.blue(b);
        return ARGB.color(
                255,
                Math.round(ar + (br - ar) * t),
                Math.round(ag + (bg - ag) * t),
                Math.round(ab + (bb - ab) * t)
        );
    }

    private static boolean isLowDurability(ItemStack stack, float threshold) {
        if (!stack.isDamageableItem()) {
            return false;
        }
        float left = durabilityPercent(stack);
        int remaining = stack.getMaxDamage() - stack.getDamageValue();
        return remaining <= 5 || left <= Mth.clamp(threshold, 0.01f, 1.0f);
    }
}
