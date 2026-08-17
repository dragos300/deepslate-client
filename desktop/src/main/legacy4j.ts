import fs from 'fs'
import path from 'path'
import { clientModsDir } from './client-mods'

const USER_AGENT = 'DeepslateLauncher/1.0 (legacy4j)'
const MODRINTH = 'https://api.modrinth.com/v2'

/** Jars we manage for the Legacy4J optional pack (not including fabric-api). */
const MANAGED_PREFIXES = ['legacy4j', 'factoryapi', 'factory-api']

type ModrinthVersion = {
  version_number: string
  version_type: string
  date_published: string
  files: Array<{ url: string; filename: string; primary: boolean }>
  dependencies?: Array<{ project_id: string; dependency_type: string }>
}

function isManagedLegacyJar(name: string): boolean {
  const n = name.toLowerCase()
  if (!n.endsWith('.jar') || n.includes('-sources')) return false
  return MANAGED_PREFIXES.some((p) => n.startsWith(p) || n.includes(`-${p}-`) || n.includes(`_${p}_`))
}

async function bestVersion(slug: string, mc: string): Promise<ModrinthVersion | null> {
  const params = new URLSearchParams({
    game_versions: JSON.stringify([mc]),
    loaders: JSON.stringify(['fabric'])
  })
  const res = await fetch(`${MODRINTH}/project/${encodeURIComponent(slug)}/version?${params}`, {
    headers: { 'User-Agent': USER_AGENT, Accept: 'application/json' }
  })
  if (!res.ok) return null
  const versions = (await res.json()) as ModrinthVersion[]
  if (!versions?.length) return null
  const rank = (t: string) => (t === 'release' ? 0 : t === 'beta' ? 1 : 2)
  return [...versions].sort((a, b) => {
    const r = rank(a.version_type) - rank(b.version_type)
    if (r !== 0) return r
    return new Date(b.date_published).getTime() - new Date(a.date_published).getTime()
  })[0]
}

function primaryJar(ver: ModrinthVersion): { url: string; filename: string } | null {
  const files = (ver.files || []).filter((f) => !f.filename.toLowerCase().includes('-sources'))
  const primary = files.find((f) => f.primary) || files[0]
  return primary ? { url: primary.url, filename: primary.filename } : null
}

async function downloadJar(url: string, dest: string): Promise<void> {
  const res = await fetch(url, { headers: { 'User-Agent': USER_AGENT } })
  if (!res.ok) throw new Error(`Download failed (${res.status}) for ${path.basename(dest)}`)
  const buf = Buffer.from(await res.arrayBuffer())
  fs.writeFileSync(dest, buf)
}

function removeManaged(hidden: string): void {
  if (!fs.existsSync(hidden)) return
  for (const name of fs.readdirSync(hidden)) {
    if (!isManagedLegacyJar(name)) continue
    try {
      fs.unlinkSync(path.join(hidden, name))
    } catch {
      /* locked — leave */
    }
  }
}

/**
 * Install or remove Legacy4J (+ Factory API) in the hidden client-mods folder.
 */
export async function syncLegacy4j(
  mc: string,
  enabled: boolean,
  root?: string,
  onStatus?: (msg: string) => void
): Promise<void> {
  const hidden = clientModsDir(mc, root)

  if (!enabled) {
    onStatus?.('Removing Legacy4J…')
    removeManaged(hidden)
    return
  }

  onStatus?.('Fetching Legacy4J…')
  const legacy = await bestVersion('legacy4j', mc)
  if (!legacy) {
    throw new Error(`Legacy4J is not available for Fabric ${mc} yet.`)
  }
  const factory = await bestVersion('factory-api', mc)
  if (!factory) {
    throw new Error(`Factory API (required by Legacy4J) is not available for Fabric ${mc}.`)
  }

  for (const [label, ver] of [
    ['Factory API', factory],
    ['Legacy4J', legacy]
  ] as const) {
    const file = primaryJar(ver)
    if (!file) continue
    const dest = path.join(hidden, file.filename)
    if (fs.existsSync(dest) && fs.statSync(dest).size > 0) {
      continue
    }
    // Drop older managed jars for this mod before writing the new one
    for (const name of fs.readdirSync(hidden)) {
      const n = name.toLowerCase()
      const isLegacy = n.startsWith('legacy4j')
      const isFactory = n.startsWith('factoryapi') || n.startsWith('factory-api') || n.includes('factoryapi')
      if (label === 'Legacy4J' && isLegacy && name !== file.filename) {
        try {
          fs.unlinkSync(path.join(hidden, name))
        } catch {
          /* ignore */
        }
      }
      if (label === 'Factory API' && isFactory && name !== file.filename) {
        try {
          fs.unlinkSync(path.join(hidden, name))
        } catch {
          /* ignore */
        }
      }
    }
    onStatus?.(`Downloading ${label}…`)
    await downloadJar(file.url, dest)
  }
}
