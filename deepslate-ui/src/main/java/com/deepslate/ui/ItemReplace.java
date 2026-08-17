package com.deepslate.ui;

import com.deepslate.ui.hud.ItemCountHudRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;

/**
 * When the held stack runs out, pull a matching stack from the rest of the inventory.
 */
public final class ItemReplace {
    private static ItemStack lastMainTemplate = ItemStack.EMPTY;
    private static ItemStack lastOffTemplate = ItemStack.EMPTY;

    private ItemReplace() {}

    public static void tick(Minecraft mc) {
        if (LegacyCompat.isActive()
                || !DeepslateConfig.get().easyItemReplace
                || PvpServers.isOnKnownPvpServer(mc)) {
            lastMainTemplate = ItemStack.EMPTY;
            lastOffTemplate = ItemStack.EMPTY;
            return;
        }
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null || mc.level == null) {
            return;
        }
        if (player.isSpectator() || player.isCreative()) {
            return;
        }

        ItemStack main = player.getMainHandItem();
        if (!main.isEmpty()) {
            lastMainTemplate = main.copyWithCount(1);
        } else if (!lastMainTemplate.isEmpty()) {
            if (tryRefillHotbar(mc, player, lastMainTemplate)) {
                lastMainTemplate = ItemStack.EMPTY;
            }
        }

        ItemStack off = player.getOffhandItem();
        if (!off.isEmpty()) {
            lastOffTemplate = off.copyWithCount(1);
        } else if (!lastOffTemplate.isEmpty()) {
            if (tryRefillOffhand(mc, player, lastOffTemplate)) {
                lastOffTemplate = ItemStack.EMPTY;
            }
        }
    }

    private static boolean tryRefillHotbar(Minecraft mc, LocalPlayer player, ItemStack template) {
        int hotbar = player.getInventory().getSelectedSlot();
        int destSlot = 36 + hotbar;
        return swapFromInventory(mc, player, template, destSlot, hotbar);
    }

    private static boolean tryRefillOffhand(Minecraft mc, LocalPlayer player, ItemStack template) {
        // Offhand is container slot 45; SWAP button 40 is the offhand swap convention in vanilla
        return swapFromInventory(mc, player, template, 45, 40);
    }

    private static boolean swapFromInventory(
            Minecraft mc, LocalPlayer player, ItemStack template, int destContainerSlot, int swapButton
    ) {
        var menu = player.containerMenu;
        // Prefer non-hotbar inventory, then other hotbar slots
        int[] order = new int[36];
        int n = 0;
        for (int i = 9; i <= 35; i++) order[n++] = i;
        for (int i = 36; i <= 44; i++) order[n++] = i;

        for (int i = 0; i < n; i++) {
            int slot = order[i];
            if (slot == destContainerSlot) continue;
            ItemStack stack = menu.getSlot(slot).getItem();
            if (stack.isEmpty()) continue;
            if (!ItemStack.isSameItemSameComponents(stack, template)) continue;
            mc.gameMode.handleInventoryMouseClick(menu.containerId, slot, swapButton, ClickType.SWAP, player);
            return true;
        }
        return false;
    }

    /** Kept for potential HUD helpers. */
    public static int matchingCount(LocalPlayer player, ItemStack template) {
        return ItemCountHudRenderer.countMatching(player, template);
    }
}
