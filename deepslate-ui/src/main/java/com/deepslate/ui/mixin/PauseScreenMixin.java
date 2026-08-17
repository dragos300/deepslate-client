package com.deepslate.ui.mixin;

import com.deepslate.ui.Branding;
import com.deepslate.ui.DeepslateConfig;
import com.deepslate.ui.LegacyCompat;
import com.deepslate.ui.Theme;
import com.deepslate.ui.screen.DeepslateModsScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {
    protected PauseScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void deepslate$modsButton(CallbackInfo ci) {
        if (LegacyCompat.isActive() || !DeepslateConfig.get().modsButtonOnPause) {
            return;
        }
        this.addRenderableWidget(
                Button.builder(Component.literal("Deepslate Mods"), b -> DeepslateModsScreen.open())
                        .bounds(this.width / 2 - 70, this.height - 40, 140, 24)
                        .build()
        );
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void deepslate$dim(
            GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci
    ) {
        if (LegacyCompat.isActive() || !DeepslateConfig.get().pauseDim) {
            return;
        }
        Branding.drawDimOverlay(graphics, this.width, this.height);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void deepslate$drawLogo(
            GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci
    ) {
        if (LegacyCompat.isActive() || !DeepslateConfig.get().pauseBranding) {
            return;
        }
        int cardW = 260;
        int cardH = DeepslateConfig.get().customLogo ? 78 : 36;
        int cardX = this.width / 2 - cardW / 2;
        Branding.drawElevatedPanel(graphics, cardX, 6, cardW, cardH, 12, Theme.PANEL_FILL, Theme.PANEL_BORDER);
        if (DeepslateConfig.get().customLogo) {
            Branding.drawLogo(graphics, this.width, 12, 1.0f);
        } else {
            Branding.drawBrandCaption(graphics, this.width, 18);
        }
    }
}
