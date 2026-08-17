package com.deepslate.ui.mixin;

import com.deepslate.ui.hud.EffectsHudRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class GuiEffectsMixin {
    @Inject(method = "renderEffects", at = @At("HEAD"), cancellable = true)
    private void deepslate$replaceEffects(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (EffectsHudRenderer.shouldReplaceVanilla()) {
            ci.cancel();
        }
    }
}
