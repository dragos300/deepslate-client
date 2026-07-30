package com.deepslate.guestlock.mixin;

import com.deepslate.guestlock.GuestLock;
import com.mojang.realmsclient.RealmsMainScreen;
import com.mojang.realmsclient.gui.screens.RealmsGenericErrorScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DirectJoinServerScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Shadow
    public Screen screen;

    @Shadow
    public abstract void setScreen(Screen screen);

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void deepslate$blockMultiplayer(Screen screen, CallbackInfo ci) {
        if (!GuestLock.isActive() || screen == null) {
            return;
        }
        if (deepslate$isBlocked(screen)) {
            ci.cancel();
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void deepslate$closeBlockedScreen(CallbackInfo ci) {
        if (!GuestLock.isActive()) {
            return;
        }
        Screen current = this.screen;
        if (current != null && deepslate$isBlocked(current)) {
            this.setScreen(null);
        }
    }

    /**
     * Must use instanceof (remapped). Class.getName() at runtime is intermediary
     * (e.g. net.minecraft.class_500), so string checks for "Multiplayer"/"realms" fail.
     */
    @Unique
    private static boolean deepslate$isBlocked(Screen screen) {
        return screen instanceof JoinMultiplayerScreen
                || screen instanceof SafetyScreen
                || screen instanceof ConnectScreen
                || screen instanceof DirectJoinServerScreen
                || screen instanceof RealmsMainScreen
                || screen instanceof RealmsGenericErrorScreen;
    }
}
