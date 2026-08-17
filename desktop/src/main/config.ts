import fs from 'fs'
import type { AppConfig, AccountData, ServerEntry } from '../shared/types'
import { NATIVE_CLIENT_REDIRECT } from '../shared/types'
import { accountPath, configExamplePath, configPath, defaultMinecraftDir, ensureDir, migrateAccountIfNeeded } from './paths'
import path from 'path'

export function defaultConfig(): AppConfig {
  return {
    client_id: '7458628c-7134-44d6-a6c6-a2d2927711ba',
    client_secret: '',
    redirect_uri: NATIVE_CLIENT_REDIRECT,
    minecraft_directory: null,
    jvm_arguments: ['-Xmx4G'],
    offline_username: 'Player',
    last_version: 'latest',
    mod_loader: 'vanilla',
    curseforge_api_key: '',
    discord_rpc: true,
    discord_rpc_client_id: '1532090720989483128',
    login_mode: 'cracked',
    keep_launcher_open: true,
    legacy4j_enabled: false,
    servers: []
  }
}

export function loadConfig(): AppConfig {
  const p = configPath()
  const defaults = defaultConfig()
  try {
    if (fs.existsSync(p)) {
      const raw = JSON.parse(fs.readFileSync(p, 'utf-8')) as Partial<AppConfig>
      const merged = { ...defaults, ...raw, servers: Array.isArray(raw.servers) ? raw.servers : [] }
      // Empty string in saved config used to wipe the default Discord app id
      if (!String(merged.discord_rpc_client_id || '').trim()) {
        merged.discord_rpc_client_id = defaults.discord_rpc_client_id
      }
      return merged
    }
  } catch {
    /* fall through */
  }
  // seed from example if present
  try {
    const ex = configExamplePath()
    if (fs.existsSync(ex)) {
      const raw = JSON.parse(fs.readFileSync(ex, 'utf-8')) as Partial<AppConfig>
      const merged = { ...defaults, ...raw }
      if (!merged.client_id || merged.client_id.startsWith('YOUR_')) {
        merged.client_id = defaults.client_id
      }
      if (!String(merged.discord_rpc_client_id || '').trim()) {
        merged.discord_rpc_client_id = defaults.discord_rpc_client_id
      }
      saveConfig(merged)
      return merged
    }
  } catch {
    /* ignore */
  }
  saveConfig(defaults)
  return defaults
}

export function saveConfig(config: AppConfig): void {
  ensureDir(path.dirname(configPath()))
  if (!String(config.discord_rpc_client_id || '').trim()) {
    config.discord_rpc_client_id = defaultConfig().discord_rpc_client_id
  }
  fs.writeFileSync(configPath(), JSON.stringify(config, null, 2), 'utf-8')
}

export function loadAccount(): AccountData | null {
  try {
    migrateAccountIfNeeded()
    const p = accountPath()
    if (!fs.existsSync(p)) return null
    return JSON.parse(fs.readFileSync(p, 'utf-8')) as AccountData
  } catch {
    return null
  }
}

export function saveAccount(data: AccountData): void {
  ensureDir(path.dirname(accountPath()))
  fs.writeFileSync(accountPath(), JSON.stringify(data, null, 2), 'utf-8')
}

export function clearAccount(): void {
  try {
    fs.unlinkSync(accountPath())
  } catch {
    /* ignore */
  }
}

export function minecraftDirectory(config?: AppConfig): string {
  const cfg = config || loadConfig()
  const custom = cfg.minecraft_directory
  if (custom && custom.trim()) return custom.trim()
  return defaultMinecraftDir()
}

export function parseRamGb(jvmArguments: string[] | undefined): number {
  for (const arg of jvmArguments || []) {
    const m = /-Xmx(\d+)([GgMm])?/.exec(arg)
    if (m) {
      const n = parseInt(m[1], 10)
      if ((m[2] || 'G').toUpperCase() === 'M') return Math.max(1, Math.round(n / 1024))
      return Math.max(1, n)
    }
  }
  return 4
}

export function ramToJvmArguments(ramGb: number): string[] {
  return [`-Xmx${Math.max(1, Math.min(32, Math.round(ramGb)))}G`]
}

export function loadServers(config?: AppConfig): ServerEntry[] {
  return (config || loadConfig()).servers || []
}

export function saveServers(servers: ServerEntry[], config?: AppConfig): AppConfig {
  const cfg = { ...(config || loadConfig()), servers }
  saveConfig(cfg)
  return cfg
}
