package com.deepslate.ui;

import net.minecraft.client.Minecraft;

/**
 * Fullbright via lightmap gamma override (does not rewrite options.txt).
 */
public final class Fullbright {
    private Fullbright() {}

    public static boolean enabled() {
        return !LegacyCompat.isActive() && DeepslateConfig.get().fullbright;
    }

    /** Effective gamma used by the lightmap when fullbright is on. */
    public static double gammaOverride() {
        return 16.0;
    }

    public static boolean shouldOverrideGamma(Minecraft mc, Object optionInstance) {
        return enabled() && mc != null && optionInstance == mc.options.gamma();
    }
}
