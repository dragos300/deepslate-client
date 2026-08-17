package com.deepslate.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

/**
 * Known competitive / PvP networks where inventory auto-refill is disabled.
 * Matching is by hostname (and common aliases), not by MOTD.
 */
public final class PvpServers {
    /**
     * Host fragments matched against the joined server address (lowercase).
     * Prefer stable domain roots so subdomains like {@code play.} still match.
     */
    private static final String[] HOST_MARKERS = {
            // Crystal / pot / sword networks
            "minemen.club",
            "minemenclub",
            "mmc.gg",
            "pvplegacy.net",
            "pvplegacy",
            "crystalpvp.cc",
            "crystalpvp",
            "potpvp.com",
            "potpvp.net",
            "potpvp",
            "vibe.gg",
            "vibegg",
            "eris.gg",
            "ivy.gg",
            "stray.gg",
            "rival.gg",
            "rivals.gg",
            "pikanetwork.net",
            "pika-network",
            "pika.network",
            "blocksmc.com",
            "jartexnetwork.com",
            "jartex.fun",
            "manacube.com",
            "manacube",
            "cubecraft.net",
            "hypixel.net",
            "hypixel",
            "minehut.gg",
            "minehut.com",
            "complexgaming.com",
            "complex.gg",
            "lifesteal.net",
            "lifestealsmp",
            "donutsmp.net",
            "donut.smp",
            "faithfulmc",
            "vanillapvp",
            "antiknock",
            "antiknockback",
            // Common EU/NA pot practice
            "eu.pvplegacy",
            "na.pvplegacy",
            "as.pvplegacy"
    };

    private PvpServers() {}

    /** True when currently connected to a listed PvP multiplayer host. */
    public static boolean isOnKnownPvpServer(Minecraft mc) {
        if (mc == null || mc.level == null) {
            return false;
        }
        if (mc.hasSingleplayerServer() || mc.isLocalServer()) {
            return false;
        }
        ServerData data = mc.getCurrentServer();
        if (data == null) {
            return false;
        }
        return matches(data.ip) || matches(data.name);
    }

    public static boolean matches(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String host = normalizeHost(raw);
        if (host.isEmpty()) {
            return false;
        }
        for (String marker : HOST_MARKERS) {
            if (host.equals(marker) || host.endsWith("." + marker) || host.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    /** Strip scheme, path, and port; lowercase. */
    static String normalizeHost(String raw) {
        String s = raw.trim().toLowerCase();
        int scheme = s.indexOf("://");
        if (scheme >= 0) {
            s = s.substring(scheme + 3);
        }
        int slash = s.indexOf('/');
        if (slash >= 0) {
            s = s.substring(0, slash);
        }
        // drop trailing :port (but keep IPv6 carefully — PvP lists are hostnames)
        int colon = s.lastIndexOf(':');
        if (colon > 0 && s.indexOf(']') < 0) {
            String maybePort = s.substring(colon + 1);
            if (maybePort.chars().allMatch(Character::isDigit)) {
                s = s.substring(0, colon);
            }
        }
        // server list entries sometimes look like "Name (host)"
        int paren = s.indexOf('(');
        if (paren >= 0) {
            int end = s.indexOf(')', paren);
            if (end > paren) {
                s = s.substring(paren + 1, end).trim();
            }
        }
        return s.trim();
    }
}
