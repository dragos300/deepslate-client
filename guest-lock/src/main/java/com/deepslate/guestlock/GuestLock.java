package com.deepslate.guestlock;

/**
 * Active only when the Deepslate launcher starts the game with {@code -Ddeepslate.guest=true}.
 */
public final class GuestLock {
    private GuestLock() {}

    public static boolean isActive() {
        return Boolean.getBoolean("deepslate.guest");
    }
}
