package com.deepslate.ui.mixin;

import com.deepslate.ui.Branding;
import com.deepslate.ui.DeepslateConfig;
import com.deepslate.ui.LegacyCompat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.LogoRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LogoRenderer.class)
public class LogoRendererMixin {
    @Inject(method = "renderLogo(Lnet/minecraft/client/gui/GuiGraphics;IFI)V", at = @At("HEAD"), cancellable = true)
    private void deepslate$replaceLogo(
            GuiGraphics graphics, int screenWidth, float alpha, int y, CallbackInfo ci
    ) {
        if (LegacyCompat.isActive() || !DeepslateConfig.get().customLogo) {
            return;
        }
        Branding.drawLogo(graphics, screenWidth, y, alpha);
        ci.cancel();
    }

    @Inject(method = "renderLogo(Lnet/minecraft/client/gui/GuiGraphics;IF)V", at = @At("HEAD"), cancellable = true)
    private void deepslate$replaceLogoDefaultY(
            GuiGraphics graphics, int screenWidth, float alpha, CallbackInfo ci
    ) {
        if (LegacyCompat.isActive() || !DeepslateConfig.get().customLogo) {
            return;
        }
        Branding.drawLogo(graphics, screenWidth, LogoRenderer.DEFAULT_HEIGHT_OFFSET, alpha);
        ci.cancel();
    }
}
