# Deepslate Client

Minecraft Java launcher — Electron + React rewrite of the Deepslate launcher.

## Run (development)

```bash
cd desktop
npm install
npm run dev
```

## Build installers

```bash
cd desktop
npm install
npm run dist        # Windows
npm run dist:linux  # Linux AppImage
npm run dist:mac    # macOS DMG (macOS only)
npm run copy-setups # copy artifacts into setups/
```

Output goes to `desktop/release/`, then `setups/`. macOS builds need a Mac or the **Build installers** GitHub Action.

## Game data

Uses the same instance folder as the Python app:

- Windows: `%APPDATA%\my-mc-launcher`
- macOS: `~/Library/Application Support/my-mc-launcher`
- Linux: `~/.my-mc-launcher`
- Config / Microsoft account: project root `config.json` / `account.json` in dev, or Electron `userData` when packaged

## Account modes

| Mode | Loader | Mods | Servers |
|------|--------|------|---------|
| Microsoft | Vanilla + Fabric | Yes | Yes |
| Cracked | Vanilla | No | Yes |
| Guest | Fabric + guest-lock | No | No (singleplayer) |

## Enhanced client pack

On Fabric launch (when **Enhanced client pack** is on in Settings), Deepslate syncs a curated
Modrinth stack into `%APPDATA%\my-mc-launcher\client-mods\fabric-<version>\` (hidden from the
user `mods` folder, loaded via `-Dfabric.addMods`):

- **Playstyle profile** (PvP / Casual / Builder) filters which jars sync and syncs in-game templates
- **Performance** — Sodium, Lithium, Entity Culling, FerriteCore, ImmediatelyFast, …
- **QoL** — AppleSkin, Xaero’s maps, Zoomify, Jade, Litematica, REI, … (by profile)
- **PvP / cosmetics** — Wavey Capes, Ping Wheel, 3D Skin Layers, … (PvP profile)
- **Misc** — No Telemetry, Debugify, Voice Chat, Sound Physics, Ears, Auth Me, …

In-game **Right Shift → Profiles / Mods** controls Deepslate UI features (freelook, CPS, toggle
sprint, …). Mod Menu buttons are hidden; Deepslate Mods is the control surface.

Inspiration (not affiliation): Lunar, Feather, Badlion, Dawn, and the Modrinth Fabric ecosystem.
Full author credits: [`assets/client-mods/CREDITS.md`](assets/client-mods/CREDITS.md).

## Host World

Deepslate hosts are normal Minecraft Java endpoints (`host:port`) so anyone can join:

1. Open a singleplayer world → pause → **Host World** (Open to LAN).
2. **e4mc** (bundled in the client pack) publishes a public `.e4mc.link` address.
3. The launcher **Host World** panel shows the join address — **Copy** for anyone, or **Save for friends** into the servers list for one-click Deepslate join.

Guests do not need Deepslate or e4mc; matching Minecraft version is enough.

## Assets

Shared with the Python tree: `assets/` (wallpaper, update art, guest-lock jars, icon).

The Python UI (`app.py`) remains in the repo as reference; prefer `desktop/` going forward.
