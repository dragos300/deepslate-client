package com.deepslate.ui.hud;

import com.deepslate.ui.DeepslateConfig;

/** Identifiers for movable HUD modules. */
public enum HudModule {
    FPS("FPS"),
    PING("Ping"),
    COORDS("Coords"),
    BIOME("Biome"),
    DAY("Day"),
    KEYSTROKES("Keystrokes"),
    ARMOR("Armor"),
    EFFECTS("Effects"),
    TOOLS("Tools"),
    ITEM_COUNT("Item Count");

    private final String label;

    HudModule(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public HudPos pos(DeepslateConfig cfg) {
        return switch (this) {
            case FPS -> cfg.posFps;
            case PING -> cfg.posPing;
            case COORDS -> cfg.posCoords;
            case BIOME -> cfg.posBiome;
            case DAY -> cfg.posDay;
            case KEYSTROKES -> cfg.posKeystrokes;
            case ARMOR -> cfg.posArmor;
            case EFFECTS -> cfg.posEffects;
            case TOOLS -> cfg.posTools;
            case ITEM_COUNT -> cfg.posItemCount;
        };
    }

    public boolean enabled(DeepslateConfig cfg) {
        return switch (this) {
            case FPS -> cfg.hudFps;
            case PING -> cfg.hudPing;
            case COORDS -> cfg.hudCoords;
            case BIOME -> cfg.hudBiome;
            case DAY -> cfg.hudDayCounter;
            case KEYSTROKES -> cfg.hudKeystrokes;
            case ARMOR -> cfg.hudArmorStatus;
            case EFFECTS -> cfg.hudEffects;
            case TOOLS -> cfg.hudToolStatus;
            case ITEM_COUNT -> cfg.hudItemCount;
        };
    }
}
