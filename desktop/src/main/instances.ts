import fs from 'fs'
import path from 'path'
import { minecraftDirectory } from './config'

/** Per-Fabric game dir: versions/libraries stay shared; mods/saves are isolated. */
export function fabricInstanceDir(mc: string, root?: string): string {
  const base = root || minecraftDirectory()
  const dir = path.join(base, 'instances', `fabric-${mc}`)
  fs.mkdirSync(dir, { recursive: true })
  return dir
}

export function isFabricLoader(loader?: string): boolean {
  const raw = (loader || '').toLowerCase()
  return raw === 'fabric' || raw.startsWith('fabric')
}

/**
 * Mods folder for the active profile.
 * Fabric → instances/fabric-<mc>/mods
 * Vanilla / unknown → <root>/mods (legacy shared)
 */
export function modsDirectoryFor(opts?: { loader?: string; gameVersion?: string; root?: string }): string {
  const root = opts?.root || minecraftDirectory()
  const mc = (opts?.gameVersion || '').trim()
  if (mc && mc !== 'latest' && isFabricLoader(opts?.loader)) {
    const dir = path.join(fabricInstanceDir(mc, root), 'mods')
    fs.mkdirSync(dir, { recursive: true })
    return dir
  }
  const dir = path.join(root, 'mods')
  fs.mkdirSync(dir, { recursive: true })
  return dir
}

/** Game directory passed to Minecraft (--gameDir). Fabric uses an instance; vanilla uses root. */
export function gameDirectoryFor(opts: {
  loader: string
  gameVersion: string
  root?: string
}): string {
  const root = opts.root || minecraftDirectory()
  if (isFabricLoader(opts.loader)) {
    return fabricInstanceDir(opts.gameVersion, root)
  }
  return root
}

/** Restore guest mod stashes under the shared mods folder and every instance. */
export function listGameDirectories(root?: string): string[] {
  const base = root || minecraftDirectory()
  const dirs = [base]
  const instances = path.join(base, 'instances')
  if (!fs.existsSync(instances)) return dirs
  for (const name of fs.readdirSync(instances)) {
    const dir = path.join(instances, name)
    try {
      if (fs.statSync(dir).isDirectory()) dirs.push(dir)
    } catch {
      /* ignore */
    }
  }
  return dirs
}
