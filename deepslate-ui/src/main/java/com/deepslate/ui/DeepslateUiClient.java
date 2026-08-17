package com.deepslate.ui;

import com.deepslate.ui.screen.DeepslateModsScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeepslateUiClient implements ClientModInitializer {
    public static final String MOD_ID = "deepslate_ui";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final KeyMapping.Category KEY_CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));

    public static final KeyMapping OPEN_MODS_MENU = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.deepslate_ui.mods_menu",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_RSHIFT,
            KEY_CATEGORY
    ));

    public static final KeyMapping ZOOM_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.deepslate_ui.zoom",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_C,
            KEY_CATEGORY
    ));

    @Override
    public void onInitializeClient() {
        DeepslateConfig.load();

        FabricLoader.getInstance()
                .getModContainer(MOD_ID)
                .ifPresent(container -> {
                    if (!LegacyCompat.isActive()) {
                        registerPack(
                                container,
                                "deepslate_style",
                                "Deepslate Style",
                                ResourcePackActivationType.ALWAYS_ENABLED
                        );
                    } else {
                        LOGGER.info("Legacy4J mode — Deepslate style pack disabled");
                    }
                });

        if (!LegacyCompat.isActive()) {
            LOGGER.info("Deepslate UI loaded — Right Shift mods · hold C to zoom");
        }
    }

    private static void registerPack(
            ModContainer container, String path, String label, ResourcePackActivationType type
    ) {
        boolean ok = ResourceManagerHelper.registerBuiltinResourcePack(
                Identifier.fromNamespaceAndPath(MOD_ID, path),
                container,
                Component.literal(label),
                type
        );
        if (!ok) {
            LOGGER.warn("Failed to register resource pack {}", path);
        }
    }

    public static void tickKeybind() {
        Minecraft mc = Minecraft.getInstance();
        Zoom.tick(mc);
        if (LegacyCompat.isActive()) {
            return;
        }
        while (OPEN_MODS_MENU.consumeClick()) {
            if (mc.screen instanceof DeepslateModsScreen) {
                mc.screen.onClose();
            } else {
                DeepslateModsScreen.open();
            }
        }
    }
}
