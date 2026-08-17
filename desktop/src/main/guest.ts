import fs from 'fs'
import path from 'path'
import { ensureDeepslateUiInClientMods, ensureHiddenClientMods, isBundledClientMod } from './client-mods'
import { isDeepslateUiJar } from './deepslate-ui'
import { fabricInstanceDir, listGameDirectories } from './instances'
import { guestLockJarFor } from './versions'

const STASH = '.deepslate-stash'
const MARKER = '.deepslate-guest-session'
const JAR_NAME = 'deepslate-guest-lock.jar'
export const GUEST_JVM_FLAG = '-Ddeepslate.guest=true'

export function modsDir(gameDir: string): string {
  return path.join(gameDir, 'mods')
}

function restoreModsStashAt(gameDir: string): void {
  if (!gameDir) return
  const mods = modsDir(gameDir)
  const stash = path.join(mods, STASH)
  const marker = path.join(mods, MARKER)

  if (!fs.existsSync(stash) && !fs.existsSync(marker)) {
    return
  }

  fs.mkdirSync(mods, { recursive: true })

  for (const name of fs.existsSync(mods) ? fs.readdirSync(mods) : []) {
    if (!name.toLowerCase().endsWith('.jar')) continue
    if (name.toLowerCase().startsWith('deepslate-guest-lock') || name === JAR_NAME) {
      try {
        fs.unlinkSync(path.join(mods, name))
      } catch {
        /* ignore */
      }
    }
  }

  if (fs.existsSync(stash)) {
    for (const name of fs.readdirSync(stash)) {
      const src = path.join(stash, name)
      const dest = path.join(mods, name)
      if (fs.existsSync(dest)) continue
      try {
        fs.renameSync(src, dest)
      } catch {
        try {
          fs.copyFileSync(src, dest)
          fs.unlinkSync(src)
        } catch {
          /* ignore */
        }
      }
    }
    try {
      fs.rmdirSync(stash)
    } catch {
      /* leftover */
    }
  }

  try {
    fs.unlinkSync(marker)
  } catch {
    /* ignore */
  }
}

/** Restore guest stashes in the shared root and every Fabric instance. */
export function restoreModsStash(root?: string): void {
  for (const gameDir of listGameDirectories(root)) {
    restoreModsStashAt(gameDir)
  }
}

export function prepareGuestSession(mc: string, root: string): string[] {
  const jarSrc = guestLockJarFor(mc)
  if (!jarSrc) {
    throw new Error(
      `No guest-lock mod for Minecraft ${mc}.\nExpected assets/guest-lock/${mc}/${JAR_NAME}`
    )
  }

  const gameDir = fabricInstanceDir(mc, root)
  restoreModsStashAt(gameDir)

  const mods = modsDir(gameDir)
  const stash = path.join(mods, STASH)
  fs.mkdirSync(mods, { recursive: true })
  fs.mkdirSync(stash, { recursive: true })

  for (const name of fs.readdirSync(mods)) {
    if (!name.toLowerCase().endsWith('.jar')) continue
    if (name === STASH || name.startsWith('.')) continue
    // Bundled client stack lives outside mods/; skip if somehow present
    if (isDeepslateUiJar(name) || isBundledClientMod(name)) continue
    const src = path.join(mods, name)
    if (!fs.statSync(src).isFile()) continue
    fs.renameSync(src, path.join(stash, name))
  }

  fs.copyFileSync(jarSrc, path.join(mods, JAR_NAME))
  ensureDeepslateUiInClientMods(mc, root)
  fs.writeFileSync(path.join(mods, MARKER), 'guest', 'utf-8')
  return [GUEST_JVM_FLAG, ensureHiddenClientMods(mc, root)]
}
