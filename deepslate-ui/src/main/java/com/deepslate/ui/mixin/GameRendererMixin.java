package com.deepslate.ui.mixin;

import com.deepslate.ui.Zoom;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void deepslate$zoomFov(
            Camera camera, float partialTick, boolean useFovSetting, CallbackInfoReturnable<Float> cir
    ) {
        if (!Zoom.enabled()) {
            return;
        }
        float zoomed = Zoom.applyFov(cir.getReturnValueF(), partialTick);
        if (zoomed != cir.getReturnValueF()) {
            cir.setReturnValue(zoomed);
        }
    }
}
