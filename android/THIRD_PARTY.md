# Third-party runtimes (Android)

Cobbled Deepslate Client downloads these on first Play. They are not bundled in the APK.

## PojavLauncher native engine

- Project: [PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher)
- License: GPL-3.0
- What Cobbled uses: `libpojavexec.so` and related `.so` files extracted from the official Gladiolus `PojavLauncher.apk`, plus JNI method names in `JREUtils` / `VMLauncher`.

## Android OpenJDK (JRE 17)

- Project: [android-openjdk-build-multiarch](https://github.com/PojavLauncherTeam/android-openjdk-build-multiarch)
- License: GPL-2.0 with Classpath Exception (OpenJDK)
- What Cobbled uses: the arm64 / arm32 / x86_64 JRE 17 tarball, unpacked into app storage.

Minecraft game files still come from Mojang after a Microsoft account that owns Java Edition signs in. Cobbled is not affiliated with Mojang Studios, Microsoft, or the PojavLauncher team.
