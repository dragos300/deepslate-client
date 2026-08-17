# Installers

Built Windows, macOS, and Linux setups are copied here after a desktop build.

They are too large for GitHub’s git file limit (~100 MB), so binaries stay local / on [GitHub Releases](https://github.com/dragos300/deepslate-client/releases).

```bash
cd desktop
npm install
npm run dist                # Windows setup
npm run dist:linux          # Linux .tar.gz (works on Windows)
npm run dist:linux:appimage # Linux AppImage (Linux CI / Ubuntu)
npm run dist:mac            # macOS DMG (needs a Mac, or GitHub Action)
npm run copy-setups
```

Push a `v*` tag (or run **Build installers** in GitHub Actions) to attach all three platforms to the release.

## Install

**Windows** — run `Deepslate-Launcher-Setup-*.exe` and finish the wizard. If SmartScreen appears, choose More info → Run anyway.

**macOS** — open the `.dmg`, drag Deepslate Launcher into Applications. First launch: right-click the app → Open. If macOS still blocks it: System Settings → Privacy & Security → Open Anyway.

**Linux (AppImage)**

```bash
chmod +x Deepslate-Launcher-*.AppImage
./Deepslate-Launcher-*.AppImage
```

**Linux (archive)**

```bash
tar -xf Deepslate-Launcher-*.tar.gz
cd "Deepslate Launcher-1.1.0-beta.1"
./deepslate-launcher
```
