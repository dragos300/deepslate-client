import fs from 'fs'
import path from 'path'
import { minecraftDirectory } from './config'
import { deepslateUiJarFor, DEEPSLATE_UI_JAR, isDeepslateUiJar } from './deepslate-ui'
import { fabricInstanceDir } from './instances'

/**
 * Hidden client-mod stack (Sodium, Lithium, Deepslate UI, …).
 * Loaded via -Dfabric.addMods so the user-facing mods/ folder stays clean.
 */
export function clientModsDir(mc: string, root?: string): string {
  const base = root || minecraftDirectory()
  const dir = path.join(base, 'client-mods', `fabric-${mc}`)
  fs.mkdirSync(dir, { recursive: true })
  return dir
}

/** Filename prefixes / exact names treated as launcher-bundled (not user mods). */
const BUNDLED_PREFIXES = [
  'deepslate-ui',
  'fabric-api',
  'sodium',
  'lithium',
  'entityculling',
  'ferritecore',
  'immediatelyfast',
  'moreculling',
  'krypton',
  'reeses-sodium',
  'modmenu',
  'cloth-config',
  'cloth_config',
  'legacy4j',
  'factoryapi',
  'factory-api',
  'iris',
  'indium',
  'lazydfu',
  'modernfix',
  'fabric-language-kotlin'
]

export function isBundledClientMod(name: string): boolean {
  const n = name.toLowerCase()
  if (!n.endsWith('.jar')) return false
  if (isDeepslateUiJar(name)) return true
  return BUNDLED_PREFIXES.some((p) => n === `${p}.jar` || n.startsWith(`${p}-`) || n.startsWith(p))
}

function copyIfNewer(src: string, dest: string): void {
  if (fs.existsSync(dest)) {
    try {
      const s = fs.statSync(src)
      const d = fs.statSync(dest)
      if (d.size === s.size && d.mtimeMs >= s.mtimeMs) return
    } catch {
      /* replace */
    }
  }
  fs.copyFileSync(src, dest)
}

/** Ensure Deepslate UI lives in the hidden client-mods folder. */
export function ensureDeepslateUiInClientMods(mc: string, root?: string): string | null {
  const src = deepslateUiJarFor(mc)
  if (!src) return null
  const dest = path.join(clientModsDir(mc, root), DEEPSLATE_UI_JAR)
  copyIfNewer(src, dest)
  return dest
}

/**
 * Move known bundled jars out of the visible mods folder into client-mods,
 * install Deepslate UI there, and return the JVM flag to load them.
 */
export function ensureHiddenClientMods(mc: string, root?: string): string {
  const hidden = clientModsDir(mc, root)
  const visible = path.join(fabricInstanceDir(mc, root), 'mods')
  fs.mkdirSync(visible, { recursive: true })

  ensureDeepslateUiInClientMods(mc, root)

  // Migrate bundled jars from the user-visible mods folder
  if (fs.existsSync(visible)) {
    for (const name of fs.readdirSync(visible)) {
      if (!isBundledClientMod(name)) continue
      const src = path.join(visible, name)
      try {
        if (!fs.statSync(src).isFile()) continue
      } catch {
        continue
      }
      const dest = path.join(hidden, name)
      try {
        if (fs.existsSync(dest)) {
          fs.unlinkSync(src)
        } else {
          fs.renameSync(src, dest)
        }
      } catch {
        try {
          fs.copyFileSync(src, dest)
          fs.unlinkSync(src)
        } catch {
          /* leave in place if locked */
        }
      }
    }
  }

  // Remove stray deepslate-ui from visible mods if still present
  const strayUi = path.join(visible, DEEPSLATE_UI_JAR)
  if (fs.existsSync(strayUi)) {
    try {
      fs.unlinkSync(strayUi)
    } catch {
      /* ignore */
    }
  }

  return `-Dfabric.addMods=${hidden}`
}
