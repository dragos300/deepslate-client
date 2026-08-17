package com.deepslate.ui.mixin;

import com.deepslate.ui.Fullbright;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LightTexture.class)
public class LightTextureMixin {
    @Redirect(
            method = "updateLightTexture",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;"
            )
    )
    private Object deepslate$fullbrightGamma(OptionInstance<?> instance) {
        Object value = instance.get();
        Minecraft mc = Minecraft.getInstance();
        if (Fullbright.shouldOverrideGamma(mc, instance)) {
            return Fullbright.gammaOverride();
        }
        return value;
    }
}
