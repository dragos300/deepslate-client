package com.deepslate.ui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.storage.WorldData;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Writes lightweight presence status for the Deepslate launcher Discord RPC.
 * File: {@code <gameDir>/deepslate-presence.json}
 */
public final class PresenceReporter {
    public static final String FILE_NAME = "deepslate-presence.json";

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final int WRITE_INTERVAL_TICKS = 40; // ~2s

    private static int tickCounter;
    private static String lastPayload = "";

    private PresenceReporter() {}

    public static void tick(Minecraft mc) {
        if (mc == null) return;
        if (++tickCounter < WRITE_INTERVAL_TICKS && !lastPayload.isEmpty()) {
            return;
        }
        tickCounter = 0;

        JsonObject json = new JsonObject();
        String mode;
        String world = null;
        String server = null;

        if (mc.level == null || mc.player == null) {
            mode = "menu";
        } else if (mc.hasSingleplayerServer() || mc.isLocalServer()) {
            mode = "singleplayer";
            IntegratedServer integrated = mc.getSingleplayerServer();
            if (integrated != null) {
                try {
                    WorldData data = integrated.getWorldData();
                    if (data != null) {
                        String name = data.getLevelName();
                        if (name != null && !name.isBlank()) {
                            world = name;
                        }
                    }
                } catch (Throwable ignored) {
                    /* mapping / version drift — presence still works without world name */
                }
            }
        } else {
            mode = "multiplayer";
            ServerData remote = mc.getCurrentServer();
            if (remote != null) {
                if (remote.name != null && !remote.name.isBlank()) {
                    server = remote.name;
                } else if (remote.ip != null && !remote.ip.isBlank()) {
                    server = remote.ip;
                }
            }
        }

        json.addProperty("mode", mode);
        if (world != null) json.addProperty("world", world);
        if (server != null) json.addProperty("server", server);
        json.addProperty("updatedAt", System.currentTimeMillis());

        String payload = GSON.toJson(json);
        if (payload.equals(lastPayload)) {
            return;
        }
        lastPayload = payload;
        writeAtomic(mc.gameDirectory.toPath().resolve(FILE_NAME), payload);
    }

    private static void writeAtomic(Path path, String payload) {
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path tmp = path.resolveSibling(FILE_NAME + ".tmp");
            Files.writeString(tmp, payload, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFail) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            DeepslateUiClient.LOGGER.debug("Presence write failed: {}", e.toString());
        }
    }
}
