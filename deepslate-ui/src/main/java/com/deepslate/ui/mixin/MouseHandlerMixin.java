package com.deepslate.ui.mixin;

import com.deepslate.ui.Zoom;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
    @Shadow
    private double accumulatedDX;

    @Shadow
    private double accumulatedDY;

    @Inject(method = "handleAccumulatedMovement", at = @At("HEAD"))
    private void deepslate$zoomSensitivity(CallbackInfo ci) {
        if (!Zoom.isActive()) {
            return;
        }
        double scale = Zoom.lookScale();
        this.accumulatedDX *= scale;
        this.accumulatedDY *= scale;
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void deepslate$zoomScroll(long window, double scrollX, double scrollY, CallbackInfo ci) {
        if (Zoom.onScroll(scrollY)) {
            ci.cancel();
        }
    }
}
