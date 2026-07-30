package com.deepslate.guestlock;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GuestLockClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("deepslate_guest_lock");

    @Override
    public void onInitializeClient() {
        if (GuestLock.isActive()) {
            LOGGER.info("Guest lock active — multiplayer screens are blocked.");
        } else {
            LOGGER.info("Guest lock inactive (no -Ddeepslate.guest=true).");
        }
    }
}
