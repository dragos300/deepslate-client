import fs from 'fs'
import path from 'path'
import type { ModHit, ModSource } from '../shared/types'
import { loadConfig } from './config'
import { modsDirectoryFor } from './instances'

const USER_AGENT = 'DeepslateLauncher/1.0 (electron; personal)'
const MODRINTH = 'https://api.modrinth.com/v2'
const CURSEFORGE = 'https://api.curseforge.com/v1'
const CF_GAME = 432
const CF_CLASS_MODS = 6

export function modsDirectory(opts?: { loader?: string; gameVersion?: string }): string {
  return modsDirectoryFor(opts)
}

function normalizeLoader(loader: string): string {
  const raw = (loader || 'fabric').toLowerCase()
  if (raw === 'vanilla' || !raw) return 'fabric'
  if (raw.startsWith('fabric')) return 'fabric'
  if (raw.includes('neo')) return 'neoforge'
  if (raw.startsWith('forge')) return 'forge'
  if (raw.startsWith('quilt')) return 'quilt'
  return raw
}

export async function searchMods(
  query: string,
  opts: { gameVersion: string; loader: string; source: ModSource; limit?: number }
): Promise<ModHit[]> {
  if (opts.source === 'curseforge') return searchCurseforge(query, opts)
  return searchModrinth(query, opts)
}

async function searchModrinth(
  query: string,
  opts: { gameVersion: string; loader: string; limit?: number }
): Promise<ModHit[]> {
  const loader = normalizeLoader(opts.loader)
  const facets = JSON.stringify([
    ['project_type:mod'],
    [`categories:${loader}`],
    [`versions:${opts.gameVersion}`]
  ])
  const params = new URLSearchParams({
    query: query.trim(),
    limit: String(Math.min(40, opts.limit ?? 20)),
    index: query.trim() ? 'relevance' : 'downloads',
    facets
  })
  const res = await fetch(`${MODRINTH}/search?${params}`, {
    headers: { 'User-Agent': USER_AGENT, Accept: 'application/json' }
  })
  if (!res.ok) throw new Error(`Modrinth search failed (${res.status})`)
  const data = (await res.json()) as {
    hits: Array<{
      project_id: string
      slug: string
      title: string
      description: string
      downloads: number
      icon_url?: string
      author?: string
    }>
  }
  return (data.hits || []).map((h) => ({
    source: 'modrinth' as const,
    projectId: h.project_id,
    slug: h.slug,
    title: h.title,
    description: h.description || '',
    downloads: h.downloads || 0,
    iconUrl: h.icon_url || null,
    author: h.author || '',
    url: `https://modrinth.com/mod/${h.slug}`
  }))
}

async function searchCurseforge(
  query: string,
  opts: { gameVersion: string; loader: string; limit?: number }
): Promise<ModHit[]> {
  const key = (loadConfig().curseforge_api_key || '').trim()
  if (!key) throw new Error('Add curseforge_api_key to config.json to use CurseForge.')
  const loader = normalizeLoader(opts.loader)
  const loaderId = loader === 'fabric' ? 4 : loader === 'neoforge' ? 6 : 1
  const params = new URLSearchParams({
    gameId: String(CF_GAME),
    classId: String(CF_CLASS_MODS),
    searchFilter: query.trim(),
    gameVersion: opts.gameVersion,
    modLoaderType: String(loaderId),
    pageSize: String(Math.min(40, opts.limit ?? 20)),
    sortField: '2',
    sortOrder: 'desc'
  })
  const res = await fetch(`${CURSEFORGE}/mods/search?${params}`, {
    headers: {
      'User-Agent': USER_AGENT,
      Accept: 'application/json',
      'x-api-key': key
    }
  })
  if (!res.ok) throw new Error(`CurseForge search failed (${res.status})`)
  const data = (await res.json()) as {
    data: Array<{
      id: number
      slug: string
      name: string
      summary: string
      downloadCount: number
      logo?: { url?: string }
      authors?: Array<{ name: string }>
      links?: { websiteUrl?: string }
    }>
  }
  return (data.data || []).map((m) => ({
    source: 'curseforge' as const,
    projectId: String(m.id),
    slug: m.slug,
    title: m.name,
    description: m.summary || '',
    downloads: m.downloadCount || 0,
    iconUrl: m.logo?.url || null,
    author: m.authors?.[0]?.name || '',
    url: m.links?.websiteUrl || `https://www.curseforge.com/minecraft/mc-mods/${m.slug}`
  }))
}

export async function installMod(
  hit: ModHit,
  opts: { gameVersion: string; loader: string }
): Promise<string> {
  const loader = normalizeLoader(opts.loader)
  const destDir = modsDirectory({ loader, gameVersion: opts.gameVersion })

  if (hit.source === 'modrinth') {
    const params = new URLSearchParams({
      loaders: JSON.stringify([loader]),
      game_versions: JSON.stringify([opts.gameVersion])
    })
    const res = await fetch(`${MODRINTH}/project/${hit.projectId}/version?${params}`, {
      headers: { 'User-Agent': USER_AGENT, Accept: 'application/json' }
    })
    if (!res.ok) throw new Error(`Modrinth versions failed (${res.status})`)
    const versions = (await res.json()) as Array<{
      id: string
      files: Array<{ url: string; filename: string; primary?: boolean }>
    }>
    if (!versions.length) throw new Error('No compatible Modrinth file.')
    const file = versions[0].files.find((f) => f.primary) || versions[0].files[0]
    const dest = path.join(destDir, file.filename)
    const buf = Buffer.from(await (await fetch(file.url, { headers: { 'User-Agent': USER_AGENT } })).arrayBuffer())
    fs.writeFileSync(dest, buf)
    return dest
  }

  const key = (loadConfig().curseforge_api_key || '').trim()
  if (!key) throw new Error('CurseForge API key required.')
  const loaderId = loader === 'fabric' ? 4 : 1
  const res = await fetch(
    `${CURSEFORGE}/mods/${hit.projectId}/files?gameVersion=${encodeURIComponent(opts.gameVersion)}&modLoaderType=${loaderId}&pageSize=5`,
    { headers: { 'User-Agent': USER_AGENT, Accept: 'application/json', 'x-api-key': key } }
  )
  if (!res.ok) throw new Error(`CurseForge files failed (${res.status})`)
  const data = (await res.json()) as {
    data: Array<{ id: number; fileName: string; downloadUrl?: string }>
  }
  const file = data.data?.[0]
  if (!file?.downloadUrl) throw new Error('No compatible CurseForge file.')
  const dest = path.join(destDir, file.fileName)
  const buf = Buffer.from(
    await (
      await fetch(file.downloadUrl, {
        headers: { 'User-Agent': USER_AGENT, 'x-api-key': key }
      })
    ).arrayBuffer()
  )
  fs.writeFileSync(dest, buf)
  return dest
}
