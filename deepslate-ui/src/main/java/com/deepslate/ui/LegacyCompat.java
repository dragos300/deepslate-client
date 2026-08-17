package com.deepslate.ui;

/**
 * When the launcher enables Legacy4J it passes {@code -Ddeepslate.legacy4j=true}.
 * In that mode we keep vanilla fonts/textures and skip Deepslate visual mixins.
 */
public final class LegacyCompat {
    private static final boolean ACTIVE = Boolean.parseBoolean(System.getProperty("deepslate.legacy4j", "false"));

    private LegacyCompat() {}

    public static boolean isActive() {
        return ACTIVE;
    }
}
