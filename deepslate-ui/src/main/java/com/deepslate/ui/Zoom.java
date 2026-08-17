package com.deepslate.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/**
 * Hold-to-zoom with frame-interpolated FOV (Zoomify-style smooth transition).
 */
public final class Zoom {
    /** Exponential speeds (higher = snappier). Tuned for ~200ms in / ~150ms out. */
    private static final double ENTER_SPEED = 7.0;
    private static final double EXIT_SPEED = 9.5;

    private static final double MIN_FACTOR = 1.0;
    private static final double MAX_FACTOR = 10.0;
    private static final double DEFAULT_STRENGTH = 3.5;

    /** Zoom strength while held (1 = none, 3.5 ≈ OptiFine default feel). */
    private static double strength = DEFAULT_STRENGTH;

    /** Smoothed factor after last tick (1 = no zoom). */
    private static double factor = 1.0;
    /** Factor at previous tick — lerped with partialTick for buttery FOV. */
    private static double prevFactor = 1.0;

    private Zoom() {}

    public static boolean enabled() {
        return !LegacyCompat.isActive() && DeepslateConfig.get().zoomEnabled;
    }

    public static void tick(Minecraft mc) {
        prevFactor = factor;

        if (!enabled() || mc.player == null) {
            factor = approach(factor, 1.0, EXIT_SPEED);
            snapIfIdle();
            return;
        }

        // Menus / pause: ease out instead of hard cut
        boolean wantZoom =
                mc.screen == null
                        && DeepslateUiClient.ZOOM_KEY.isDown()
                        && !mc.options.hideGui;

        double target = wantZoom ? strength : 1.0;
        double speed = wantZoom ? ENTER_SPEED : EXIT_SPEED;
        factor = approach(factor, target, speed);
        snapIfIdle();
    }

    /** Frame-rate independent exponential approach (20 TPS ticks). */
    private static double approach(double current, double target, double speed) {
        double alpha = 1.0 - Math.exp(-speed / 20.0);
        return Mth.lerp(alpha, current, target);
    }

    private static void snapIfIdle() {
        if (Math.abs(factor - 1.0) < 0.002) {
            factor = 1.0;
        }
    }

    /** Scroll while zoom key is held to adjust strength (smooth, small steps). */
    public static boolean onScroll(double vertical) {
        if (!enabled() || !DeepslateUiClient.ZOOM_KEY.isDown()) {
            return false;
        }
        if (factor < 1.05 && !DeepslateUiClient.ZOOM_KEY.isDown()) {
            return false;
        }
        // Logarithmic-ish steps feel even across the range
        double step = 0.22 * Math.max(0.35, factor * 0.35);
        strength = Mth.clamp(strength - vertical * step, MIN_FACTOR + 0.25, MAX_FACTOR);
        return true;
    }

    public static boolean isActive() {
        return enabled() && smoothFactor(1f) > 1.002;
    }

    /** Interpolated zoom factor for the current frame. */
    public static double smoothFactor(float partialTick) {
        if (!enabled()) {
            return 1.0;
        }
        double f = Mth.lerp(partialTick, prevFactor, factor);
        return f < 1.002 ? 1.0 : f;
    }

    public static float applyFov(float baseFov, float partialTick) {
        double f = smoothFactor(partialTick);
        if (f <= 1.002) {
            return baseFov;
        }
        // Divide FOV (OptiFine-style). Clamp so wide FOVs don't go absurdly narrow.
        return (float) Mth.clamp(baseFov / f, 5.0, 170.0);
    }

    /** Mouse look scale using the latest tick factor (stable within a tick). */
    public static double lookScale() {
        if (!enabled() || factor <= 1.002) {
            return 1.0;
        }
        // Slightly less aggressive than 1/f so look doesn't feel sticky
        return 1.0 / Math.sqrt(factor);
    }
}
