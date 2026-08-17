# Deepslate Client

Minecraft Java launcher — Electron + React rewrite of the Deepslate launcher.

## Run (development)

```bash
cd desktop
npm install
npm run dev
```

## Build Windows installer

```bash
cd desktop
npm install
npm run dist
```

Output goes to `desktop/release/`.

## Game data

Uses the same instance folder as the Python app:

- Windows: `%APPDATA%\my-mc-launcher`
- Config / Microsoft account: project root `config.json` / `account.json` in dev, or Electron `userData` when packaged

## Account modes

| Mode | Loader | Mods | Servers |
|------|--------|------|---------|
| Microsoft | Vanilla + Fabric | Yes | Yes |
| Cracked | Vanilla | No | Yes |
| Guest | Fabric + guest-lock | No | No (singleplayer) |

## Assets

Shared with the Python tree: `assets/` (wallpaper, update art, guest-lock jars, icon).

The Python UI (`app.py`) remains in the repo as reference; prefer `desktop/` going forward.
