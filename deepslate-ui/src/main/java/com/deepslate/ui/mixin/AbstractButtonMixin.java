package com.deepslate.ui.mixin;

import com.deepslate.ui.Branding;
import com.deepslate.ui.DeepslateConfig;
import com.deepslate.ui.LegacyCompat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractButton.class)
public abstract class AbstractButtonMixin {
    @Inject(method = "renderDefaultSprite", at = @At("HEAD"), cancellable = true)
    private void deepslate$darkButton(GuiGraphics graphics, CallbackInfo ci) {
        if (LegacyCompat.isActive() || !DeepslateConfig.get().styledButtons) {
            return;
        }
        AbstractButton self = (AbstractButton) (Object) this;
        Branding.drawDarkPanel(
                graphics,
                self.getX(),
                self.getY(),
                self.getWidth(),
                self.getHeight(),
                self.isHoveredOrFocused(),
                self.active
        );
        ci.cancel();
    }
}
