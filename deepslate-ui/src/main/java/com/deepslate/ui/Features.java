package com.deepslate.ui;

import java.util.Arrays;
import java.util.List;

public final class Features {
    public static final List<Feature> ALL = Arrays.asList(
            new Feature(
                    "custom_logo",
                    "Custom Logo",
                    "Replace the Minecraft logo with Deepslate Client branding.",
                    Feature.Category.UI,
                    c -> c.customLogo,
                    (c, v) -> c.customLogo = v
            ),
            new Feature(
                    "larger_logo",
                    "Larger Logo",
                    "Use a slightly larger brand block on the title and pause screens.",
                    Feature.Category.UI,
                    c -> c.largerLogo,
                    (c, v) -> c.largerLogo = v
            ),
            new Feature(
                    "styled_buttons",
                    "Styled Buttons",
                    "Rounded dark menu buttons instead of vanilla stone.",
                    Feature.Category.UI,
                    c -> c.styledButtons,
                    (c, v) -> c.styledButtons = v
            ),
            new Feature(
                    "cyan_accent",
                    "Mint Hover Accent",
                    "Highlight hovered buttons with the Deepslate mint border.",
                    Feature.Category.UI,
                    c -> c.cyanButtonAccent,
                    (c, v) -> c.cyanButtonAccent = v
            ),
            new Feature(
                    "pause_dim",
                    "Pause Dim",
                    "Dim the world behind the pause menu.",
                    Feature.Category.MENU,
                    c -> c.pauseDim,
                    (c, v) -> c.pauseDim = v
            ),
            new Feature(
                    "pause_branding",
                    "Pause Branding",
                    "Show Deepslate branding at the top of the pause menu.",
                    Feature.Category.MENU,
                    c -> c.pauseBranding,
                    (c, v) -> c.pauseBranding = v
            ),
            new Feature(
                    "mods_button",
                    "Mods Button",
                    "Add a Deepslate Mods button on the pause menu.",
                    Feature.Category.MENU,
                    c -> c.modsButtonOnPause,
                    (c, v) -> c.modsButtonOnPause = v
            ),
            new Feature(
                    "hud_fps",
                    "FPS Counter",
                    "Frames-per-second readout.",
                    "Inspired by Lunar Client info HUD modules.",
                    Feature.Category.HUD,
                    c -> c.hudFps,
                    (c, v) -> c.hudFps = v
            ),
            new Feature(
                    "hud_ping",
                    "Ping Counter",
                    "Network latency when on a server.",
                    "Inspired by Lunar Client info HUD modules.",
                    Feature.Category.HUD,
                    c -> c.hudPing,
                    (c, v) -> c.hudPing = v
            ),
            new Feature(
                    "hud_coords",
                    "Coordinates",
                    "XYZ position.",
                    "Inspired by Lunar Client info HUD modules.",
                    Feature.Category.HUD,
                    c -> c.hudCoords,
                    (c, v) -> c.hudCoords = v
            ),
            new Feature(
                    "hud_biome",
                    "Biome",
                    "Current biome name.",
                    Feature.Category.HUD,
                    c -> c.hudBiome,
                    (c, v) -> c.hudBiome = v
            ),
            new Feature(
                    "hud_day",
                    "Day Counter",
                    "World day number.",
                    "Inspired by Lunar Client info HUD modules.",
                    Feature.Category.HUD,
                    c -> c.hudDayCounter,
                    (c, v) -> c.hudDayCounter = v
            ),
            new Feature(
                    "hud_keystrokes",
                    "Keystrokes",
                    "Show WASD, jump, and mouse buttons while pressed.",
                    "Inspired by Lunar Client / common PvP keystroke HUDs.",
                    Feature.Category.HUD,
                    c -> c.hudKeystrokes,
                    (c, v) -> c.hudKeystrokes = v
            ),
            new Feature(
                    "custom_crosshair",
                    "Custom Crosshair",
                    "Replace the vanilla crosshair with a Deepslate cross.",
                    "Inspired by Lunar Client crosshair customization.",
                    Feature.Category.HUD,
                    c -> c.customCrosshair,
                    (c, v) -> c.customCrosshair = v
            ),
            new Feature(
                    "hud_armor",
                    "Armor Status",
                    "Vertical armor strip with durability bars and percent.",
                    Feature.Category.HUD,
                    c -> c.hudArmorStatus,
                    (c, v) -> c.hudArmorStatus = v
            ),
            new Feature(
                    "hud_effects",
                    "Status Effects",
                    "Vanilla-style effect icons with duration and amplifier overlays.",
                    "Inspired by Status Effect Timer (magicus).",
                    Feature.Category.HUD,
                    c -> c.hudEffects,
                    (c, v) -> c.hudEffects = v
            ),
            new Feature(
                    "hud_tools",
                    "Tool Durability",
                    "Show held tools with durability bars and low-durability flash.",
                    "Inspired by uku Client / durability HUD mods.",
                    Feature.Category.HUD,
                    c -> c.hudToolStatus,
                    (c, v) -> c.hudToolStatus = v
            ),
            new Feature(
                    "hud_item_count",
                    "Item Count",
                    "Total inventory count of the item in your main hand.",
                    "Inspired by Lunar Client item-count overlays.",
                    Feature.Category.HUD,
                    c -> c.hudItemCount,
                    (c, v) -> c.hudItemCount = v
            ),
            new Feature(
                    "easy_item_replace",
                    "Easy Item Replace",
                    "Auto-refill when a stack runs out. Auto-off on known PvP servers.",
                    "Inspired by Inventory Tweaks / Tweakeroo-style refill.",
                    Feature.Category.MISC,
                    c -> c.easyItemReplace,
                    (c, v) -> c.easyItemReplace = v
            ),
            new Feature(
                    "zoom",
                    "Zoom",
                    "Hold C to zoom (smooth). Scroll while holding to change strength.",
                    "Inspired by OptiFine / Zoomify zoom.",
                    Feature.Category.MISC,
                    c -> c.zoomEnabled,
                    (c, v) -> c.zoomEnabled = v
            ),
            new Feature(
                    "fullbright",
                    "Fullbright",
                    "Brighten the world as if gamma is maxed (caves, night, nether).",
                    Feature.Category.MISC,
                    c -> c.fullbright,
                    (c, v) -> c.fullbright = v
            )
    );

    private Features() {}
}
