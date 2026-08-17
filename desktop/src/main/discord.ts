import fs from 'fs'
import path from 'path'
import RPC from 'discord-rpc'
import { defaultConfig, loadConfig } from './config'

let client: RPC.Client | null = null
let ready = false
let startTs = Date.now()
let playTs = Date.now()
let reconnectTimer: ReturnType<typeof setTimeout> | null = null
let watchTimer: ReturnType<typeof setInterval> | null = null
let watchCtx: GameWatchContext | null = null
let lastWatchKey = ''

const DEFAULT_CLIENT_ID = defaultConfig().discord_rpc_client_id
export const PRESENCE_FILE = 'deepslate-presence.json'

export type PlayMode = 'menu' | 'singleplayer' | 'multiplayer' | 'unknown'

export interface GameWatchContext {
  gameDir: string
  versionLabel: string
  username?: string
}

interface PresenceFile {
  mode?: string
  world?: string
  server?: string
  updatedAt?: number
}

function scheduleReconnect(): void {
  if (reconnectTimer) return
  reconnectTimer = setTimeout(() => {
    reconnectTimer = null
    void initDiscord()
  }, 5000)
}

function clientId(): string {
  const id = String(loadConfig().discord_rpc_client_id || '').trim()
  return id || DEFAULT_CLIENT_ID
}

function enabled(): boolean {
  return Boolean(loadConfig().discord_rpc) && Boolean(clientId())
}

function legacy4jOn(): boolean {
  return Boolean(loadConfig().legacy4j_enabled)
}

function withLegacy(activity: RPC.Presence): RPC.Presence {
  if (!legacy4jOn()) return activity
  return {
    ...activity,
    state: activity.state ? `${activity.state} · Legacy4J` : 'Legacy4J'
  }
}

async function setActivitySafe(activity: RPC.Presence): Promise<void> {
  if (!ready || !client) return
  try {
    await client.setActivity(activity)
  } catch (err) {
    console.warn('[discord] setActivity failed, retrying without images', err)
    try {
      const { largeImageKey: _l, largeImageText: _lt, smallImageKey: _s, smallImageText: _st, ...rest } =
        activity
      await client.setActivity(rest)
    } catch (err2) {
      console.warn('[discord] setActivity retry failed', err2)
    }
  }
}

function normalizeMode(raw?: string): PlayMode {
  const m = String(raw || '').toLowerCase()
  if (m === 'singleplayer' || m === 'multiplayer' || m === 'menu') return m
  return 'unknown'
}

function readPresenceFile(gameDir: string): PresenceFile | null {
  try {
    const p = path.join(gameDir, PRESENCE_FILE)
    if (!fs.existsSync(p)) return null
    return JSON.parse(fs.readFileSync(p, 'utf-8')) as PresenceFile
  } catch {
    return null
  }
}

function clearPresenceFile(gameDir: string): void {
  try {
    const p = path.join(gameDir, PRESENCE_FILE)
    if (fs.existsSync(p)) fs.unlinkSync(p)
  } catch {
    /* ignore */
  }
}

export async function initDiscord(): Promise<void> {
  if (!enabled()) {
    console.warn('[discord] RPC disabled or missing client id')
    return
  }
  try {
    client?.removeAllListeners()
    client?.destroy()
  } catch {
    /* ignore */
  }
  client = null
  ready = false

  const next = new RPC.Client({ transport: 'ipc' })
  client = next
  startTs = Date.now()

  next.on('ready', () => {
    if (client !== next) return
    ready = true
    console.log('[discord] ready')
    if (watchCtx) {
      applyGamePresenceFromWatch()
    } else {
      setDiscordIdle()
    }
  })

  next.on('disconnected', () => {
    if (client !== next) return
    console.warn('[discord] disconnected')
    ready = false
    client = null
    try {
      next.destroy()
    } catch {
      /* ignore */
    }
    scheduleReconnect()
  })

  try {
    await next.login({ clientId: clientId() })
  } catch (err) {
    console.warn('[discord] login failed', err)
    if (client === next) {
      client = null
      ready = false
      try {
        next.destroy()
      } catch {
        /* ignore */
      }
    }
    scheduleReconnect()
  }
}

export function setDiscordIdle(versionLabel?: string, page?: string): void {
  stopDiscordGameWatch()
  void setActivitySafe(
    withLegacy({
      details: page === 'mods' ? 'Browsing mods' : 'In launcher',
      state: versionLabel ? `Ready · ${versionLabel}` : 'Ready',
      largeImageKey: 'deepslate',
      largeImageText: 'Deepslate Launcher',
      startTimestamp: startTs
    })
  )
}

export function setDiscordPreparing(versionLabel: string): void {
  stopDiscordGameWatch()
  void setActivitySafe(
    withLegacy({
      details: 'Preparing…',
      state: versionLabel,
      largeImageKey: 'deepslate',
      largeImageText: 'Deepslate Launcher',
      startTimestamp: startTs
    })
  )
}

export function setDiscordPlaying(opts: {
  versionLabel: string
  username?: string
  mode?: PlayMode
  world?: string
  server?: string
}): void {
  const mode = opts.mode || 'unknown'
  let details: string
  if (mode === 'singleplayer') {
    details = opts.world ? `Singleplayer · ${opts.world}` : 'Playing Singleplayer'
  } else if (mode === 'multiplayer') {
    details = opts.server ? `Multiplayer · ${opts.server}` : 'Playing Multiplayer'
  } else if (mode === 'menu') {
    details = legacy4jOn() ? 'In menus · Console Edition' : 'In menus'
  } else if (opts.server) {
    details = `Multiplayer · ${opts.server}`
  } else {
    details = legacy4jOn() ? 'Playing · Console Edition' : 'In game'
  }

  const state = [opts.versionLabel, opts.username].filter(Boolean).join(' · ')
  void setActivitySafe(
    withLegacy({
      details,
      state,
      largeImageKey: 'deepslate',
      largeImageText: 'Deepslate Launcher',
      startTimestamp: playTs
    })
  )
}

function applyGamePresenceFromWatch(): void {
  if (!watchCtx) return
  const file = readPresenceFile(watchCtx.gameDir)
  const mode = normalizeMode(file?.mode)
  const world = file?.world?.trim() || undefined
  const server = file?.server?.trim() || undefined
  const key = `${mode}|${world || ''}|${server || ''}|${watchCtx.versionLabel}|${watchCtx.username || ''}`
  if (key === lastWatchKey) return
  lastWatchKey = key
  setDiscordPlaying({
    versionLabel: watchCtx.versionLabel,
    username: watchCtx.username,
    mode: mode === 'unknown' && !file ? 'unknown' : mode,
    world,
    server
  })
}

/** Poll the Fabric mod presence file while Minecraft is running. */
export function startDiscordGameWatch(ctx: GameWatchContext): void {
  stopDiscordGameWatch(false)
  watchCtx = ctx
  playTs = Date.now()
  lastWatchKey = ''
  setDiscordPlaying({
    versionLabel: ctx.versionLabel,
    username: ctx.username,
    mode: 'unknown'
  })
  watchTimer = setInterval(() => applyGamePresenceFromWatch(), 1500)
}

export function stopDiscordGameWatch(clearFile = true): void {
  if (watchTimer) {
    clearInterval(watchTimer)
    watchTimer = null
  }
  if (clearFile && watchCtx?.gameDir) {
    clearPresenceFile(watchCtx.gameDir)
  }
  watchCtx = null
  lastWatchKey = ''
}

/** Refresh presence after Legacy4J (or other) config changes. */
export function refreshDiscordPresence(versionLabel?: string, page?: string): void {
  if (watchCtx) {
    applyGamePresenceFromWatch()
    return
  }
  setDiscordIdle(versionLabel, page)
}

export function destroyDiscord(): void {
  stopDiscordGameWatch()
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
  try {
    client?.removeAllListeners()
    client?.destroy()
  } catch {
    /* ignore */
  }
  client = null
  ready = false
}
