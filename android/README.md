# Cobbled Deepslate Client

Android launcher for **Minecraft Java Edition**. Same mineral UI as desktop Deepslate, Microsoft login, version picking, Modrinth search, and instance file download.

## Install on a phone (no computer)

1. On the phone, open this file and download it:  
   [`setups/Cobbled-Deepslate-Client-0.1.0-debug.apk`](../setups/Cobbled-Deepslate-Client-0.1.0-debug.apk)
2. Open the downloaded APK. If Android blocks it, allow installs from **Chrome** / **Files** / **GitHub** (whichever you used).
3. Open **Cobbled Deepslate**, sign in with a Microsoft account that **owns Java Edition**, then tap Play.

The game will not start yet. Play downloads the official client; the on-device Java runtime is still the next slice.

This is an unsigned **debug** build (`com.deepslate.cobbled.debug`). Android will warn that it is not from Play. That is expected.

The on-device Java / OpenGL runtime is not in this slice yet. Play will sign you in, resolve a version, and download the official client jar. Starting the game comes next.

## Requirements

- Android 8.0+ (API 26)
- A Microsoft account that **owns Minecraft Java Edition**
- Android Studio Ladybug+ or JDK 17 and the Android SDK

Cobbled does not support cracked or offline play.

## Open in Android Studio

1. Open the `android/` folder as a Gradle project (not the repo root).
2. Let Gradle sync. SDK 35 is requested.
3. Run the `app` configuration on a phone or emulator.

Command line, from `android/`:

```bash
./gradlew :core:test
./gradlew :app:assembleDebug
```

The debug APK is `app/build/outputs/apk/debug/`. The debug application id is `com.deepslate.cobbled.debug`.

## What works today

- Microsoft → Xbox Live → Minecraft Services login
- Release version list (Vanilla or Fabric), grouped like the desktop launcher
- Download of the official client jar into app storage
- Modrinth search and jar install into the instance `mods/` folder
- Saved Java server addresses
- RAM and enhanced-pack toggles stored for the future runtime

## What does not work yet

- Actually starting Minecraft on the phone (needs a Pojav-style JVM + GL layer)
- Syncing the desktop Deepslate UI / client pack automatically
- Joining a saved server from the launcher

Game files are downloaded from Mojang after login. Cobbled is not affiliated with Mojang Studios or Microsoft.
