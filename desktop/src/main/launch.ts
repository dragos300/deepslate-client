import { Client } from 'minecraft-launcher-core'
import fs from 'fs'
import path from 'path'
import type { ChildProcess } from 'child_process'
import type { LaunchRequest, LaunchResult, ProgressEvent } from '../shared/types'
import { loadConfig, minecraftDirectory, ramToJvmArguments, saveConfig } from './config'
import { offlineUuid, parseVersionRef, makeVersionRef, displayVersionLabel } from './versions-util'
import {
  findInstalledFabric,
  getLatestRelease,
  latestFabricLoader,
  resolveVanillaId
} from './versions'
import { prepareGuestSession, restoreModsStash } from './guest'
import { setDiscordPlaying, setDiscordPreparing, setDiscordIdle, startDiscordGameWatch, stopDiscordGameWatch } from './discord'
import { microsoftLaunchAuth } from './auth'
import { resolveJavaPath, silenceMclcConsole } from './mclc-silence'
import { ensureHiddenClientMods } from './client-mods'
import { fabricInstanceDir, gameDirectoryFor } from './instances'
import { syncLegacy4j } from './legacy4j'

silenceMclcConsole()

type ProgressCb = (ev: ProgressEvent) => void

let gameProcess: ChildProcess | null = null

export function isGameRunning(): boolean {
  return gameProcess != null && gameProcess.exitCode == null
}

function mavenNameToPath(name: string): string {
  const parts = name.split(':')
  const group = parts[0]
  const artifact = parts[1]
  const version = parts[2]
  const classifier = parts[3]
  const g = group.replace(/\./g, '/')
  const file = classifier
    ? `${artifact}-${version}-${classifier}.jar`
    : `${artifact}-${version}.jar`
  return path.join(g, artifact, version, file)
}

async function installFromMojangManifest(
  id: string,
  root: string,
  onProgress?: ProgressCb
): Promise<void> {
  const versionJson = path.join(root, 'versions', id, `${id}.json`)
  const clientJar = path.join(root, 'versions', id, `${id}.jar`)
  if (fs.existsSync(versionJson) && fs.existsSync(clientJar)) return

  const manifestRes = await fetch('https://launchermeta.mojang.com/mc/game/version_manifest_v2.json')
  const manifest = (await manifestRes.json()) as {
    versions: Array<{ id: string; url: string }>
  }
  const entry = manifest.versions.find((v) => v.id === id)
  if (!entry) throw new Error(`Unknown version ${id}`)
  onProgress?.({ value: 0, maximum: 0, status: `Fetching ${id} metadata…` })
  const meta = (await (await fetch(entry.url)).json()) as {
    downloads: { client: { url: string } }
    libraries: Array<{
      name: string
      downloads?: { artifact?: { url: string; path: string } }
      rules?: unknown[]
    }>
    assetIndex: { id: string; url: string }
  }

  const verDir = path.join(root, 'versions', id)
  fs.mkdirSync(verDir, { recursive: true })
  fs.writeFileSync(path.join(verDir, `${id}.json`), JSON.stringify(meta, null, 2))

  if (!fs.existsSync(clientJar)) {
    onProgress?.({ value: 0, maximum: 0, status: `Downloading client ${id}…` })
    fs.writeFileSync(
      clientJar,
      Buffer.from(await (await fetch(meta.downloads.client.url)).arrayBuffer())
    )
  }

  const libDir = path.join(root, 'libraries')
  const libs = meta.libraries || []
  let i = 0
  for (const lib of libs) {
    i++
    if (lib.rules) continue
    const art = lib.downloads?.artifact
    if (!art?.url || !art.path) continue
    const dest = path.join(libDir, art.path)
    if (fs.existsSync(dest)) continue
    fs.mkdirSync(path.dirname(dest), { recursive: true })
    if (i % 10 === 0) {
      onProgress?.({ value: i, maximum: libs.length, status: `Libraries ${i}/${libs.length}` })
    }
    try {
      fs.writeFileSync(dest, Buffer.from(await (await fetch(art.url)).arrayBuffer()))
    } catch {
      /* skip */
    }
  }

  const assetsDir = path.join(root, 'assets')
  const indexesDir = path.join(assetsDir, 'indexes')
  fs.mkdirSync(indexesDir, { recursive: true })
  const indexPath = path.join(indexesDir, `${meta.assetIndex.id}.json`)
  if (!fs.existsSync(indexPath)) {
    onProgress?.({ value: 0, maximum: 0, status: 'Downloading asset index…' })
    fs.writeFileSync(
      indexPath,
      Buffer.from(await (await fetch(meta.assetIndex.url)).arrayBuffer())
    )
  }
  const index = JSON.parse(fs.readFileSync(indexPath, 'utf-8')) as {
    objects: Record<string, { hash: string }>
  }
  const objects = Object.entries(index.objects || {})
  let oi = 0
  for (const [, obj] of objects) {
    oi++
    const hash = obj.hash
    const sub = hash.slice(0, 2)
    const dest = path.join(assetsDir, 'objects', sub, hash)
    if (fs.existsSync(dest)) continue
    if (oi % 40 === 0) {
      onProgress?.({ value: oi, maximum: objects.length, status: `Assets ${oi}/${objects.length}` })
    }
    fs.mkdirSync(path.dirname(dest), { recursive: true })
    try {
      const url = `https://resources.download.minecraft.net/${sub}/${hash}`
      fs.writeFileSync(dest, Buffer.from(await (await fetch(url)).arrayBuffer()))
    } catch {
      /* continue */
    }
  }
}

async function ensureVanilla(version: string, root: string, onProgress?: ProgressCb): Promise<string> {
  const id = await resolveVanillaId(version)
  await installFromMojangManifest(id, root, onProgress)
  return id
}

async function ensureFabric(
  mcRef: string,
  root: string,
  onProgress?: ProgressCb
): Promise<{ profileId: string; mc: string }> {
  const mc = mcRef === 'latest' ? await getLatestRelease() : mcRef
  const existing = findInstalledFabric(mc, root)
  if (existing) return { profileId: existing, mc }

  onProgress?.({ value: 0, maximum: 0, status: `Installing Fabric for ${mc}…` })
  await ensureVanilla(mc, root, onProgress)
  const loader = await latestFabricLoader(mc)
  const profileId = `fabric-loader-${loader}-${mc}`
  const url = `https://meta.fabricmc.net/v2/versions/loader/${encodeURIComponent(mc)}/${encodeURIComponent(loader)}/profile/json`
  const res = await fetch(url)
  if (!res.ok) throw new Error(`Fabric profile failed (${res.status})`)
  const profile = (await res.json()) as {
    libraries?: Array<{ name: string; url?: string }>
  }
  const verDir = path.join(root, 'versions', profileId)
  fs.mkdirSync(verDir, { recursive: true })
  fs.writeFileSync(path.join(verDir, `${profileId}.json`), JSON.stringify(profile, null, 2))

  const libs = profile.libraries || []
  let i = 0
  for (const lib of libs) {
    i++
    const mavenPath = mavenNameToPath(lib.name)
    const base = (lib.url || 'https://maven.fabricmc.net/').replace(/\/?$/, '/')
    const dest = path.join(root, 'libraries', mavenPath)
    if (fs.existsSync(dest)) continue
    fs.mkdirSync(path.dirname(dest), { recursive: true })
    onProgress?.({ value: i, maximum: libs.length, status: `Fabric ${lib.name}` })
    const candidates = [base + mavenPath.replace(/\\/g, '/'), 'https://repo1.maven.org/maven2/' + mavenPath.replace(/\\/g, '/'), 'https://libraries.minecraft.net/' + mavenPath.replace(/\\/g, '/')]
    let ok = false
    for (const u of candidates) {
      try {
        fs.writeFileSync(dest, Buffer.from(await (await fetch(u)).arrayBuffer()))
        ok = true
        break
      } catch {
        /* try next */
      }
    }
    if (!ok) {
      /* optional */
    }
  }
  return { profileId, mc }
}

export async function launchGame(
  req: LaunchRequest,
  onProgress?: ProgressCb,
  onExit?: (code: number | null) => void
): Promise<LaunchResult> {
  try {
    const config = loadConfig()
    const root = minecraftDirectory(config)
    fs.mkdirSync(root, { recursive: true })

    config.jvm_arguments = ramToJvmArguments(req.ramGb)
    config.last_version = req.version
    config.login_mode = req.loginMode
    config.keep_launcher_open = req.keepOpen
    if (req.loginMode !== 'guest') config.offline_username = req.username
    saveConfig(config)

    if (req.loginMode !== 'guest') restoreModsStash(root)

    const [loader, mcRef] = parseVersionRef(req.version)
    let extraJvm: string[] = []
    let mcId = mcRef
    let customVersion: string | undefined

    setDiscordPreparing(displayVersionLabel(req.version))

    let useFabricInstance = false
    if (req.loginMode === 'guest' || loader === 'fabric') {
      const mc = mcRef === 'latest' ? await getLatestRelease() : mcRef
      if (req.loginMode === 'guest') extraJvm = prepareGuestSession(mc, root)
      const fabric = await ensureFabric(mc, root, onProgress)
      mcId = fabric.mc
      customVersion = fabric.profileId
      useFabricInstance = true
      // Ensure the per-version mods folder exists before launch
      fs.mkdirSync(path.join(fabricInstanceDir(mcId, root), 'mods'), { recursive: true })
      // Bundled stack (Sodium, UI, …) loads from a hidden folder — keeps mods/ clean
      const clientModsFlag = ensureHiddenClientMods(mcId, root)
      if (!extraJvm.includes(clientModsFlag)) extraJvm.push(clientModsFlag)
      if (config.legacy4j_enabled) {
        const flag = '-Ddeepslate.legacy4j=true'
        if (!extraJvm.includes(flag)) extraJvm.push(flag)
      }
      onProgress?.({ value: 0, maximum: 0, status: 'Client mods ready…' })
      try {
        await syncLegacy4j(mcId, !!config.legacy4j_enabled, root, (msg) =>
          onProgress?.({ value: 0, maximum: 0, status: msg })
        )
      } catch (e) {
        const msg = e instanceof Error ? e.message : String(e)
        return { ok: false, error: msg }
      }
    } else {
      mcId = await ensureVanilla(mcRef, root, onProgress)
    }

    let username = req.username
    let uuid = offlineUuid(username)
    let authorization: Record<string, unknown> = {
      access_token: '0',
      client_token: '0',
      uuid: uuid.replace(/-/g, ''),
      name: username,
      user_properties: '{}',
      meta: {
        type: 'mojang',
        offline: true
      }
    }

    if (req.loginMode === 'microsoft') {
      const { account, authorization: msa } = await microsoftLaunchAuth()
      username = account.name
      uuid = account.id
      authorization = { ...msa }
    } else if (req.loginMode === 'guest') {
      username = 'Guest'
      uuid = offlineUuid('Guest')
      authorization = {
        access_token: '0',
        client_token: '0',
        uuid: uuid.replace(/-/g, ''),
        name: username,
        user_properties: '{}',
        meta: {
          type: 'mojang',
          offline: true
        }
      }
    } else {
      // cracked
      authorization = {
        access_token: '0',
        client_token: '0',
        uuid: uuid.replace(/-/g, ''),
        name: username,
        user_properties: '{}',
        meta: {
          type: 'mojang',
          offline: true
        }
      }
    }

    onProgress?.({ value: 0, maximum: 0, status: 'Starting game…' })

    const launcher = new Client()
    launcher.on('progress', (e: { task?: number; total?: number; type?: string }) => {
      onProgress?.({
        value: e?.task ?? 0,
        maximum: e?.total ?? 0,
        status: `Preparing ${e?.type || ''}…`
      })
    })

    const javaPath = resolveJavaPath()
    const gameDirectory = useFabricInstance
      ? gameDirectoryFor({ loader: 'fabric', gameVersion: mcId, root })
      : root
    const launchOpts: Record<string, unknown> = {
      root,
      version: {
        number: mcId,
        type: 'release',
        ...(customVersion ? { custom: customVersion } : {})
      },
      memory: { max: `${req.ramGb}G`, min: '512M' },
      authorization,
      customArgs: [...(config.jvm_arguments || []), ...extraJvm],
      ...(javaPath ? { javaPath } : {}),
      overrides: {
        detached: true,
        gameDirectory
      }
    }

    if (req.server?.address) {
      launchOpts.server = {
        host: req.server.address,
        port: String(req.server.port || 25565)
      }
    }

    const proc = (await launcher.launch(launchOpts as never)) as unknown as ChildProcess
    gameProcess = proc

    const label = displayVersionLabel(
      makeVersionRef(
        loader === 'fabric' || req.loginMode === 'guest' ? 'fabric' : 'vanilla',
        mcId
      )
    )
    startDiscordGameWatch({
      gameDir: gameDirectory,
      versionLabel: label,
      username
    })
    // Immediate hint when joining a server from the launcher (before the mod reports)
    if (req.server?.address) {
      setDiscordPlaying({
        versionLabel: label,
        username,
        mode: 'multiplayer',
        server: req.server.name || req.server.address
      })
    }

    if (proc && typeof proc.on === 'function') {
      proc.on('close', (code) => {
        gameProcess = null
        if (req.loginMode === 'guest') restoreModsStash(root)
        stopDiscordGameWatch()
        setDiscordIdle(displayVersionLabel(config.last_version))
        onExit?.(code)
      })
    }

    return { ok: true, resolvedVersion: customVersion || mcId }
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err)
    try {
      if (req.loginMode === 'guest') restoreModsStash(minecraftDirectory())
    } catch {
      /* ignore */
    }
    stopDiscordGameWatch()
    setDiscordIdle()
    return { ok: false, error: message }
  }
}
