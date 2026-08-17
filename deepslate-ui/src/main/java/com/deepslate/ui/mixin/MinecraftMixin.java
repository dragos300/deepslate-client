package com.deepslate.ui.mixin;

import com.deepslate.ui.DeepslateUiClient;
import com.deepslate.ui.ItemReplace;
import com.deepslate.ui.PresenceReporter;
import com.deepslate.ui.hud.HudData;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void deepslate$modsMenuKey(CallbackInfo ci) {
        DeepslateUiClient.tickKeybind();
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void deepslate$hudData(CallbackInfo ci) {
        Minecraft mc = (Minecraft) (Object) this;
        HudData.tick(mc);
        PresenceReporter.tick(mc);
        ItemReplace.tick(mc);
    }
}
