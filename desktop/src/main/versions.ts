import fs from 'fs'
import path from 'path'
import type { VersionGroup } from '../shared/types'
import { loadConfig, minecraftDirectory } from './config'
import { resourceRoot } from './paths'
import {
  compareMcVersions,
  updateTitle,
  versionFamily
} from './versions-util'

const MANIFEST_URL = 'https://launchermeta.mojang.com/mc/game/version_manifest_v2.json'
const FABRIC_GAME_URL = 'https://meta.fabricmc.net/v2/versions/game'
const FABRIC_LOADER_URL = 'https://meta.fabricmc.net/v2/versions/loader'

export interface MojangManifest {
  latest: { release: string; snapshot: string }
  versions: Array<{ id: string; type: string; url: string; releaseTime: string }>
}

let manifestCache: MojangManifest | null = null

export async function fetchManifest(): Promise<MojangManifest> {
  if (manifestCache) return manifestCache
  const res = await fetch(MANIFEST_URL)
  if (!res.ok) throw new Error(`Failed to fetch version manifest (${res.status})`)
  manifestCache = (await res.json()) as MojangManifest
  return manifestCache
}

export async function getLatestRelease(): Promise<string> {
  const m = await fetchManifest()
  return m.latest.release
}

export async function listReleaseVersions(): Promise<string[]> {
  const m = await fetchManifest()
  return m.versions.filter((v) => v.type === 'release').map((v) => v.id)
}

export async function fabricSupportedVersions(stableOnly = true): Promise<string[]> {
  const res = await fetch(FABRIC_GAME_URL)
  if (!res.ok) throw new Error(`Fabric meta error (${res.status})`)
  const data = (await res.json()) as Array<{ version: string; stable: boolean }>
  return data.filter((r) => !stableOnly || r.stable).map((r) => r.version)
}

export async function latestFabricLoader(mc: string): Promise<string> {
  const res = await fetch(`${FABRIC_LOADER_URL}/${encodeURIComponent(mc)}`)
  if (!res.ok) throw new Error(`No Fabric loader for ${mc}`)
  const data = (await res.json()) as Array<{ loader: { version: string; stable: boolean } }>
  const stable = data.find((r) => r.loader?.stable)
  if (stable) return stable.loader.version
  if (!data.length) throw new Error(`No Fabric loader for ${mc}`)
  return data[0].loader.version
}

export function findInstalledFabric(mc: string, directory?: string): string | null {
  const dir = directory || minecraftDirectory()
  const versionsDir = path.join(dir, 'versions')
  if (!fs.existsSync(versionsDir)) return null
  const prefix = `fabric-loader-`
  const suffix = `-${mc}`
  const matches = fs
    .readdirSync(versionsDir)
    .filter((name) => name.startsWith(prefix) && name.endsWith(suffix))
    .sort()
  return matches.length ? matches[matches.length - 1] : null
}

export function listInstalledVersions(directory?: string): string[] {
  const dir = directory || minecraftDirectory()
  const versionsDir = path.join(dir, 'versions')
  if (!fs.existsSync(versionsDir)) return []
  return fs.readdirSync(versionsDir).filter((name) => {
    return fs.existsSync(path.join(versionsDir, name, `${name}.json`))
  })
}

export async function resolveVanillaId(ref: string): Promise<string> {
  if (!ref || ref === 'latest' || ref === 'latest-release') return getLatestRelease()
  if (ref === 'latest-snapshot') {
    const m = await fetchManifest()
    return m.latest.snapshot
  }
  return ref
}

export function updateArtFileUrl(family: string): string | undefined {
  const base = resourceRoot()
  const stem = family.replace(/\./g, '_')
  for (const ext of ['.img', '.jpg', '.jpeg', '.png', '.webp']) {
    const p = path.join(base, 'updates', `${stem}${ext}`)
    if (fs.existsSync(p)) {
      return `file://${p.replace(/\\/g, '/')}`
    }
  }
  return undefined
}

export function guestLockVersions(): string[] {
  const root = path.join(resourceRoot(), 'guest-lock')
  const found = new Set<string>()
  const manifestPath = path.join(root, 'manifest.json')
  try {
    if (fs.existsSync(manifestPath)) {
      const data = JSON.parse(fs.readFileSync(manifestPath, 'utf-8')) as { versions?: string[] }
      for (const v of data.versions || []) {
        if (v) found.add(v)
      }
    }
  } catch {
    /* ignore */
  }
  if (fs.existsSync(root)) {
    for (const child of fs.readdirSync(root, { withFileTypes: true })) {
      if (
        child.isDirectory() &&
        fs.existsSync(path.join(root, child.name, 'deepslate-guest-lock.jar'))
      ) {
        found.add(child.name)
      }
    }
  }
  return [...found].sort(compareMcVersions).reverse()
}

export function guestLockJarFor(mc: string): string | null {
  const root = path.join(resourceRoot(), 'guest-lock')
  for (const vid of [mc]) {
    const p = path.join(root, vid, 'deepslate-guest-lock.jar')
    if (fs.existsSync(p)) return p
  }
  return null
}

export async function versionsGrouped(opts?: {
  fabricOnly?: boolean
  guestOnly?: boolean
  limitPerFamily?: number
}): Promise<VersionGroup[]> {
  const limit = opts?.limitPerFamily ?? 12
  const releases = await listReleaseVersions()
  let fabricSet: Set<string> | null = null
  if (opts?.fabricOnly || opts?.guestOnly) {
    try {
      fabricSet = new Set(await fabricSupportedVersions(true))
    } catch {
      fabricSet = null
    }
  }
  let guestSet: Set<string> | null = null
  if (opts?.guestOnly) {
    guestSet = new Set(guestLockVersions())
  }

  const byFamily = new Map<string, string[]>()
  for (const id of releases) {
    if (fabricSet && !fabricSet.has(id)) continue
    if (guestSet && !guestSet.has(id)) continue
    const fam = versionFamily(id)
    const bucket = byFamily.get(fam) || []
    bucket.push(id)
    byFamily.set(fam, bucket)
  }

  const families = [...byFamily.keys()].sort((a, b) => {
    if (a === 'latest') return -1
    if (b === 'latest') return 1
    return compareMcVersions(b + '.0', a + '.0')
  })

  return families.map((family) => {
    const versions = (byFamily.get(family) || [])
      .sort(compareMcVersions)
      .reverse()
      .slice(0, limit)
    return {
      family,
      title: updateTitle(family),
      versions,
      artUrl: updateArtFileUrl(family)
    }
  })
}

function fileToDataUrl(file: string): string {
  const buf = fs.readFileSync(file)
  const ext = path.extname(file).toLowerCase()
  const mime =
    ext === '.jpg' || ext === '.jpeg'
      ? 'image/jpeg'
      : ext === '.webp'
        ? 'image/webp'
        : 'image/png'
  return `data:${mime};base64,${buf.toString('base64')}`
}

export function wallpaperUrl(): string | undefined {
  const dir = path.join(resourceRoot(), 'backgrounds')
  if (!fs.existsSync(dir)) return undefined
  const file = fs.readdirSync(dir).find((f) => /\.(png|jpg|jpeg|webp)$/i.test(f))
  if (!file) return undefined
  // data: works in Vite/Electron dev; file:// is blocked from http://localhost
  return fileToDataUrl(path.join(dir, file))
}

export function brandIconUrl(): string | undefined {
  const candidates = ['brand_deepslate.png', 'deepslate_block.png', 'icon.png']
  for (const name of candidates) {
    const file = path.join(resourceRoot(), name)
    if (fs.existsSync(file)) {
      return fileToDataUrl(file)
    }
  }
  return undefined
}

export function getConfigSnapshot() {
  return loadConfig()
}
