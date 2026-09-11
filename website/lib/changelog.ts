export type ChangelogEntry = {
  tag: string
  title: string
  date: string
  latest?: boolean
  summary: string
  added: string[]
  changed: string[]
}

export const CHANGELOG: ChangelogEntry[] = [
  {
    tag: 'v1.1.0-beta.2',
    title: '1.1.0 Beta 2',
    date: '2026-09-11',
    latest: true,
    summary:
      'Playstyle profiles, a curated Fabric pack, Host World, cosmetics, and a much deeper HUD — plus a public version list on the site.',
    added: [
      'Playstyle profiles (PvP, Casual, Builder) that filter the client pack and in-game UI',
      'Enhanced Fabric client pack synced from Modrinth (Sodium, Lithium, maps, and more)',
      'Host World — Open to LAN with an e4mc join address anyone can use',
      'Freelook, toggle sprint, CPS, combo counter, totem count, and saturation overlay',
      'Capes, skin editor, and hex HUD / menu accent colours',
      'Manrope UI font, smoother shapes, and a dedicated Deepslate Mods menu'
    ],
    changed: [
      'Vanilla Mod Menu buttons are hidden; Deepslate Mods is the control surface',
      'Downloads still start immediately from the latest GitHub Release zip',
      'Launcher wallpaper stays on the app only — the website is wallpaper-free'
    ]
  },
  {
    tag: 'v1.1.0-beta.1',
    title: '1.1.0 Beta 1',
    date: '2026-08-17',
    summary: 'First public beta of the Electron launcher and Fabric Deepslate UI.',
    added: [
      'Windows, macOS, and Linux installers',
      'Microsoft, Offline, and Guest login',
      'Info HUD (FPS, ping, coordinates, biome, day, keystrokes)',
      'Armor, tools, effects, and custom crosshair',
      'Zoom, fullbright, and pause branding'
    ],
    changed: []
  }
]
