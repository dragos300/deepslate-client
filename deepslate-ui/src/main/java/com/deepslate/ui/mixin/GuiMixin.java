package com.deepslate.ui.mixin;

import com.deepslate.ui.hud.HudOverlayRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class GuiMixin {
    @Inject(method = "render", at = @At("RETURN"))
    private void deepslate$renderHudOverlays(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        HudOverlayRenderer.render(graphics, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
    }
}
