package com.deepslate.ui.mixin;

import com.deepslate.ui.DeepslateConfig;
import com.deepslate.ui.LegacyCompat;
import com.deepslate.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public class TitleScreenMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void deepslate$watermark(
            GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci
    ) {
        if (LegacyCompat.isActive() || !DeepslateConfig.get().customLogo) {
            return;
        }
        TitleScreen self = (TitleScreen) (Object) this;
        graphics.fill(Math.max(0, self.width - 140), 0, self.width, 100, Theme.ACCENT_WASH);
        String mark = "Deepslate Client";
        var font = Minecraft.getInstance().font;
        int tw = font.width(mark);
        int x = 12;
        int y = self.height - 18;
        graphics.fill(x - 4, y - 4, x + tw + 10, y + 12, Theme.HUD_PILL);
        graphics.fill(x - 4, y - 4, x + tw + 10, y - 3, Theme.HUD_PILL_BORDER);
        graphics.fill(x - 4, y + 11, x + tw + 10, y + 12, Theme.HUD_PILL_BORDER);
        graphics.fill(x - 4, y - 4, x - 3, y + 12, Theme.HUD_PILL_BORDER);
        graphics.fill(x + tw + 9, y - 4, x + tw + 10, y + 12, Theme.HUD_PILL_BORDER);
        graphics.drawString(font, mark, x, y, Theme.ACCENT, false);
    }
}
