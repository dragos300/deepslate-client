package com.deepslate.ui.hud;

import com.deepslate.ui.DeepslateConfig;
import com.deepslate.ui.LegacyCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

/**
 * Per-tick cache for HUD overlays.
 */
public final class HudData {
    public static int fps;
    public static int ping = -1;
    public static double x;
    public static double y;
    public static double z;
    public static String biome = "";
    public static long day;
    public static boolean hasPlayer;

    public static boolean keyForward;
    public static boolean keyLeft;
    public static boolean keyBack;
    public static boolean keyRight;
    public static boolean keyJump;
    public static boolean mouseLeft;
    public static boolean mouseRight;

    private HudData() {}

    public static boolean anyHudEnabled() {
        if (LegacyCompat.isActive()) {
            return false;
        }
        DeepslateConfig c = DeepslateConfig.get();
        return c.hudFps
                || c.hudPing
                || c.hudCoords
                || c.hudBiome
                || c.hudDayCounter
                || c.hudKeystrokes
                || c.hudArmorStatus
                || c.hudEffects
                || c.hudToolStatus
                || c.hudItemCount;
    }

    public static void tick(Minecraft mc) {
        if (LegacyCompat.isActive() || mc.level == null || mc.player == null) {
            hasPlayer = false;
            ping = -1;
            return;
        }

        DeepslateConfig cfg = DeepslateConfig.get();
        HudLayout.ensureDefaults(cfg);
        hasPlayer = true;
        fps = mc.getFps();

        LocalPlayer player = mc.player;
        if (cfg.hudCoords || cfg.hudDayCounter || cfg.hudBiome) {
            x = player.getX();
            y = player.getY();
            z = player.getZ();
            day = player.level().getDayTime() / 24000L;
        }
        if (cfg.hudBiome) {
            biome = resolveBiomeName(player);
        }

        if (cfg.hudPing) {
            ping = -1;
            ClientPacketListener connection = mc.getConnection();
            if (connection != null) {
                PlayerInfo info = connection.getPlayerInfo(player.getUUID());
                if (info != null) {
                    ping = info.getLatency();
                }
            }
        }

        if (cfg.hudKeystrokes) {
            keyForward = mc.options.keyUp.isDown();
            keyLeft = mc.options.keyLeft.isDown();
            keyBack = mc.options.keyDown.isDown();
            keyRight = mc.options.keyRight.isDown();
            keyJump = mc.options.keyJump.isDown();
            mouseLeft = mc.mouseHandler.isLeftPressed();
            mouseRight = mc.mouseHandler.isRightPressed();
        }
    }

    private static String resolveBiomeName(LocalPlayer player) {
        Holder<Biome> holder = player.level().getBiomeManager().getBiome(player.blockPosition());
        return holder
                .unwrapKey()
                .map(HudData::biomeTranslation)
                .orElseGet(() -> {
                    String reg = holder.getRegisteredName();
                    int slash = reg.indexOf(':');
                    return slash >= 0 ? pretty(reg.substring(slash + 1)) : pretty(reg);
                });
    }

    private static String biomeTranslation(ResourceKey<Biome> key) {
        String path = key.identifier().getPath();
        String ns = key.identifier().getNamespace();
        String lang = "biome." + ns + "." + path;
        if (I18n.exists(lang)) {
            return I18n.get(lang);
        }
        return pretty(path);
    }

    private static String pretty(String path) {
        String[] parts = path.split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0)));
            if (p.length() > 1) sb.append(p.substring(1));
        }
        return sb.toString();
    }
}
