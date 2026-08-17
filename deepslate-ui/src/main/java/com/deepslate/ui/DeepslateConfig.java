package com.deepslate.ui;

import com.deepslate.ui.hud.HudLayout;
import com.deepslate.ui.hud.HudPos;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;

/**
 * Persisted feature toggles and HUD layout for the Deepslate mods menu.
 */
public final class DeepslateConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "deepslate_ui.json";

    private static DeepslateConfig instance = new DeepslateConfig();

    public boolean customLogo = true;
    public boolean styledButtons = true;
    public boolean cyanButtonAccent = true;
    public boolean pauseDim = true;
    public boolean pauseBranding = true;
    public boolean modsButtonOnPause = true;
    public boolean largerLogo = true;

    public boolean hudFps = true;
    public boolean hudPing = true;
    public boolean hudCoords = true;
    public boolean hudBiome = true;
    public boolean hudDayCounter = true;
    public boolean hudKeystrokes = true;
    public boolean customCrosshair = true;
    public boolean hudArmorStatus = true;
    public boolean hudEffects = true;
    public boolean hudToolStatus = true;
    public boolean hudItemCount = true;
    public boolean easyItemReplace = true;
    public boolean zoomEnabled = true;
    public boolean fullbright = false;

    public HudPos posFps = new HudPos(0.01f, 0.02f);
    public HudPos posPing = new HudPos(0.01f, 0.07f);
    public HudPos posCoords = new HudPos(0.01f, 0.12f);
    public HudPos posBiome = new HudPos(0.01f, 0.17f);
    public HudPos posDay = new HudPos(0.01f, 0.22f);
    public HudPos posKeystrokes = new HudPos(0.01f, 0.72f);
    public HudPos posArmor = new HudPos(0.01f, 0.55f);
    public HudPos posEffects = new HudPos(0.78f, 0.02f);
    public HudPos posTools = new HudPos(0.55f, 0.86f);
    public HudPos posItemCount = new HudPos(0.70f, 0.86f);

    /** Crosshair style id (see CrosshairStyle). */
    public String crosshairStyle = "CROSS";
    public int crosshairSize = 5;
    public int crosshairGap = 2;
    public int crosshairThickness = 2;
    public int crosshairColor = 0xFF3FD4B8;
    public boolean crosshairOutline = true;
    public int crosshairOutlineColor = 0xFF06080A;
    public int crosshairOutlineThickness = 1;
    public boolean crosshairDot = false;
    public int crosshairDotSize = 2;

    /** Low-durability warning threshold for armor HUD (0–1). */
    public float armorWarningPercent = 0.05f;

    private DeepslateConfig() {}

    public static DeepslateConfig get() {
        return instance;
    }

    public static Path configPath() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve(FILE_NAME);
    }

    public static void load() {
        Path path = configPath();
        if (!Files.isRegularFile(path)) {
            instance = new DeepslateConfig();
            HudLayout.ensureDefaults(instance);
            save();
            return;
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            DeepslateConfig loaded = GSON.fromJson(reader, DeepslateConfig.class);
            if (loaded != null) {
                instance = loaded;
            }
        } catch (Exception e) {
            DeepslateUiClient.LOGGER.error("Failed to load Deepslate config; using defaults", e);
            instance = new DeepslateConfig();
        }
        HudLayout.ensureDefaults(instance);
    }

    public static void save() {
        Path path = configPath();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(instance, writer);
            }
        } catch (IOException e) {
            DeepslateUiClient.LOGGER.error("Failed to save Deepslate config", e);
        }
    }
}
