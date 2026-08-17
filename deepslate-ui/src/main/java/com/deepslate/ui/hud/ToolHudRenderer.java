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
import net.minecraft.world.item.ItemStack;

/**
 * Main-hand / off-hand tool icons in a glass card with low-durability flash.
 */
public final class ToolHudRenderer {
    private static final int ICON = 16;
    private static final int GAP = 6;
    private static final int PAD = 5;

    private ToolHudRenderer() {}

    public static int width() {
        return ICON * 2 + GAP + PAD * 2;
    }

    public static int height() {
        return ICON + PAD * 2;
    }

    public static void render(GuiGraphics graphics, int screenWidth, int screenHeight, boolean editMode) {
        if (LegacyCompat.isActive() || (!DeepslateConfig.get().hudToolStatus && !editMode)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (!editMode && (player == null || mc.options.hideGui || mc.gui.getDebugOverlay().showDebugScreen())) {
            return;
        }

        DeepslateConfig cfg = DeepslateConfig.get();
        HudLayout.ensureDefaults(cfg);
        int w = width();
        int h = height();
        int x = HudLayout.pixelX(cfg.posTools, screenWidth, w);
        int y = HudLayout.pixelY(cfg.posTools, screenHeight, h);

        ItemStack main = player != null ? player.getMainHandItem() : ItemStack.EMPTY;
        ItemStack off = player != null ? player.getOffhandItem() : ItemStack.EMPTY;
        boolean showMain = !main.isEmpty() && main.isDamageableItem();
        boolean showOff = !off.isEmpty() && off.isDamageableItem();
        if (!showMain && !showOff && !editMode) {
            return;
        }

        Branding.drawHudCard(graphics, x, y, w, h);
        if (editMode) {
            graphics.fill(x - 1, y - 1, x + w + 1, y + h + 1, Theme.ACCENT_GLOW);
        }

        Font font = mc.font;
        int cx = x + PAD;
        int cy = y + PAD;
        drawSlot(graphics, font, main, cx, cy, showMain, editMode, cfg);
        drawSlot(graphics, font, off, cx + ICON + GAP, cy, showOff, editMode, cfg);
    }

    private static void drawSlot(
            GuiGraphics graphics,
            Font font,
            ItemStack stack,
            int x,
            int y,
            boolean show,
            boolean editMode,
            DeepslateConfig cfg
    ) {
        if (show) {
            Branding.fillRounded(graphics, x - 1, y - 1, ICON + 2, ICON + 2, 3, Theme.KEY_IDLE);
            graphics.renderItem(stack, x, y);
            graphics.renderItemDecorations(font, stack, x, y);
            if (isLowDurability(stack, cfg.armorWarningPercent)) {
                long t = System.currentTimeMillis() % 1000L;
                int alpha = t < 500 ? 160 : 60;
                graphics.fill(x, y, x + ICON, y + ICON, ARGB.color(alpha, 255, 40, 40));
            }
        } else if (editMode) {
            Branding.fillRounded(graphics, x, y, ICON, ICON, 3, ARGB.color(80, 47, 49, 59));
            Branding.drawRoundedBorder(graphics, x, y, ICON, ICON, 3, Theme.SPACE_7);
        }
    }

    private static boolean isLowDurability(ItemStack stack, float threshold) {
        if (!stack.isDamageableItem()) {
            return false;
        }
        float left = 1.0f - stack.getDamageValue() / (float) Math.max(1, stack.getMaxDamage());
        int remaining = stack.getMaxDamage() - stack.getDamageValue();
        return remaining <= 5 || left <= Mth.clamp(threshold, 0.01f, 1.0f);
    }
}
