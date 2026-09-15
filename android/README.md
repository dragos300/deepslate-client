# Cobbled Deepslate Client

Android launcher for **Minecraft Java Edition**. Same mineral UI as desktop Deepslate, Microsoft login, version picking, Modrinth search, official game download, and a Pojav-based Java runtime on the phone.

## Install on a phone (no computer)

1. On the phone, open this file and download it:  
   [`setups/Cobbled-Deepslate-Client-0.2.0-debug.apk`](../setups/Cobbled-Deepslate-Client-0.2.0-debug.apk)
2. Open the downloaded APK. If Android blocks it, allow installs from **Chrome** / **Files** / **GitHub** (whichever you used).
3. If you already installed the 0.1.0 preview, uninstall it first (debug signing keys can differ between builds).
4. Open **Cobbled Deepslate**, sign in with a Microsoft account that **owns Java Edition**, then tap Play.

This is an unsigned **debug** build (`com.deepslate.cobbled.debug`). Android will warn that it is not from Play. That is expected.

First Play downloads Minecraft assets, a JRE, and Pojav native libraries (hundreds of MB). Later launches reuse those files.

## Requirements

- Android 8.0+ (API 26), **arm64** recommended
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

- Microsoft → Xbox Live → Minecraft Services login (on-screen WebView, Chrome Custom Tabs with `ms-xal-…` redirect, or a Microsoft login code)
- Release version list (Vanilla or Fabric), grouped like the desktop launcher
- Official client, libraries, assets, and Fabric loader jars in app storage
- Modrinth search and jar install into the instance `mods/` folder
- Play starts Java Edition through the Pojav OpenJDK + `libpojavexec` engine
- Saved Java server addresses
- RAM slider passed to the JVM as `-Xmx`

## Limits

- The engine is the official Pojav natives plus Cobbled's launch glue, not the full Pojav touch UI. Controls may be rough on a phone.
- First launch needs a solid network; the JRE + engine pack is large.
- Enhanced desktop client-pack sync is not automatic yet — install Fabric mods from the Mods tab.

Game files are downloaded from Mojang after login. Runtime binaries are downloaded from PojavLauncher (GPL-3.0) and Android OpenJDK. See [`THIRD_PARTY.md`](THIRD_PARTY.md). Cobbled is not affiliated with Mojang Studios or Microsoft.
