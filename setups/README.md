# Installers

Built Windows, macOS, and Linux setups are copied here after a desktop build.

They are too large for GitHub’s git file limit (~100 MB), so binaries stay local / on [GitHub Releases](https://github.com/dragos300/deepslate-client/releases).

Each platform folder (`windows/`, `macos/`, `linux/`) includes the installer plus `INSTALL.txt`.

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

Install steps live in `INSTALL.txt` inside each platform folder (copied from `desktop/install/`).
