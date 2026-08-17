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
import net.minecraft.world.item.ItemStack;

/**
 * Held-item inventory count inside a glass pill.
 */
public final class ItemCountHudRenderer {
    private static final int ICON = 16;
    private static final int GAP = 4;
    private static final int PAD = 5;

    private ItemCountHudRenderer() {}

    public static int width(Font font, int count) {
        return ICON + GAP + font.width("x" + count) + PAD * 2;
    }

    public static int height() {
        return ICON + PAD * 2;
    }

    public static void render(GuiGraphics graphics, int screenWidth, int screenHeight, boolean editMode) {
        if (LegacyCompat.isActive() || (!DeepslateConfig.get().hudItemCount && !editMode)) {
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

        ItemStack held = player != null ? player.getMainHandItem() : ItemStack.EMPTY;
        int count = player != null ? countMatching(player, held) : 0;
        if ((held.isEmpty() || count <= 0) && !editMode) {
            return;
        }
        if (editMode && held.isEmpty()) {
            count = 0;
        }

        int w = width(font, Math.max(count, 0));
        int h = height();
        int x = HudLayout.pixelX(cfg.posItemCount, screenWidth, w);
        int y = HudLayout.pixelY(cfg.posItemCount, screenHeight, h);

        Branding.drawHudPill(graphics, x, y, w, h);
        if (editMode) {
            graphics.fill(x - 1, y - 1, x + w + 1, y + h + 1, Theme.ACCENT_GLOW);
        }

        int ix = x + PAD;
        int iy = y + PAD;
        if (!held.isEmpty()) {
            graphics.renderItem(held, ix, iy);
        } else {
            Branding.fillRounded(graphics, ix, iy, ICON, ICON, 3, ARGB.color(70, 47, 49, 59));
        }

        String text = "x" + count;
        int tx = ix + ICON + GAP;
        int ty = iy + (ICON - 8) / 2;
        graphics.drawString(font, text, tx + 1, ty + 1, ARGB.color(140, 0, 0, 0), false);
        graphics.drawString(font, text, tx, ty, Theme.TEXT, false);
    }

    public static int countMatching(LocalPlayer player, ItemStack template) {
        if (template == null || template.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (ItemStack.isSameItemSameComponents(stack, template)) {
                total += stack.getCount();
            }
        }
        return total;
    }
}
