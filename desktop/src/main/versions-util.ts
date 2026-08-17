import crypto from 'crypto'
import type { ModLoaderName } from '../shared/types'
import { UPDATE_TITLES } from '../shared/types'

export function parseVersionRef(version: string): [ModLoaderName, string] {
  const v = (version || 'latest').trim()
  if (v.startsWith('fabric:')) return ['fabric', v.slice('fabric:'.length) || 'latest']
  if (v.startsWith('vanilla:')) return ['vanilla', v.slice('vanilla:'.length) || 'latest']
  return ['vanilla', v || 'latest']
}

export function makeVersionRef(loader: ModLoaderName, minecraftVersion: string): string {
  if (loader === 'fabric') return `fabric:${minecraftVersion}`
  return minecraftVersion
}

export function displayVersionLabel(version: string): string {
  const [loader, mc] = parseVersionRef(version)
  if (loader === 'fabric') return `Fabric ${mc}`
  return mc === 'latest' ? 'Latest release' : mc
}

export function versionFamily(versionId: string): string {
  const [, mc] = parseVersionRef(versionId)
  if (mc === 'latest' || mc === 'latest-release' || mc === 'latest-snapshot') return 'latest'
  const parts = mc.split('.')
  if (!parts.length) return mc
  if (parts[0].match(/^\d+$/) && parts[0].length === 2 && parseInt(parts[0], 10) >= 25) {
    return parts[0]
  }
  if (parts.length >= 2) return `${parts[0]}.${parts[1]}`
  return mc
}

export function updateTitle(family: string): string {
  return UPDATE_TITLES[family] || `Minecraft ${family}`
}

export function offlineUuid(username: string): string {
  // uuid3 OfflinePlayer:name — Node crypto MD5 namespace DNS style matching Python uuid3
  const ns = Buffer.from('6ba7b8109dad11d180b400c04fd430c8', 'hex') // NAMESPACE_DNS
  const name = Buffer.from(`OfflinePlayer:${username}`, 'utf8')
  const hash = crypto.createHash('md5').update(Buffer.concat([ns, name])).digest()
  hash[6] = (hash[6] & 0x0f) | 0x30
  hash[8] = (hash[8] & 0x3f) | 0x80
  const hex = hash.toString('hex')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}

export function mcVersionSortKey(version: string): number[] {
  return version.split('.').map((bit) => {
    const n = parseInt(bit.replace(/\D/g, '') || '0', 10)
    return Number.isFinite(n) ? n : 0
  })
}

export function compareMcVersions(a: string, b: string): number {
  const aa = mcVersionSortKey(a)
  const bb = mcVersionSortKey(b)
  const len = Math.max(aa.length, bb.length)
  for (let i = 0; i < len; i++) {
    const d = (aa[i] || 0) - (bb[i] || 0)
    if (d) return d
  }
  return 0
}
