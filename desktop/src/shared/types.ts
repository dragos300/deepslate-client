export type LoginMode = 'microsoft' | 'cracked' | 'guest'
export type ModLoaderName = 'vanilla' | 'fabric'
export type ModSource = 'modrinth' | 'curseforge'

export interface ServerEntry {
  name: string
  address: string
  port: number
}

export interface AppConfig {
  client_id: string
  client_secret: string
  redirect_uri: string
  minecraft_directory: string | null
  jvm_arguments: string[]
  offline_username: string
  last_version: string
  mod_loader: ModLoaderName
  curseforge_api_key: string
  discord_rpc: boolean
  discord_rpc_client_id: string
  login_mode: LoginMode
  keep_launcher_open: boolean
  /** When true, Legacy4J (+ Factory API) are installed into hidden client-mods on launch. */
  legacy4j_enabled: boolean
  servers: ServerEntry[]
}

export interface MclcAuth {
  access_token: string
  client_token?: string
  uuid: string
  name?: string
  meta?: {
    refresh?: string
    exp?: number
    type: 'mojang' | 'msa' | 'legacy'
    xuid?: string
    demo?: boolean
  }
  user_properties?: unknown
}

export interface AccountData {
  id: string
  name: string
  access_token: string
  refresh_token: string
  /** Full minecraft-launcher-core authorization (required for Realms / online-mode). */
  mclc?: MclcAuth
}

export interface VersionGroup {
  family: string
  title: string
  versions: string[]
  artUrl?: string
}

export interface ProgressEvent {
  value: number
  maximum: number
  status: string
}

export interface ModHit {
  source: ModSource
  projectId: string
  slug: string
  title: string
  description: string
  downloads: number
  iconUrl: string | null
  author: string
  url: string
}

export interface LaunchRequest {
  version: string
  username: string
  ramGb: number
  keepOpen: boolean
  loginMode: LoginMode
  server?: ServerEntry | null
}

export interface LaunchResult {
  ok: boolean
  resolvedVersion?: string
  error?: string
}

export const UPDATE_TITLES: Record<string, string> = {
  latest: 'Latest',
  '26': '2026 Drops',
  '1.21': 'Tricky Trials',
  '1.20': 'Trails & Tales',
  '1.19': 'The Wild',
  '1.18': 'Caves & Cliffs II',
  '1.17': 'Caves & Cliffs I',
  '1.16': 'Nether Update',
  '1.15': 'Buzzy Bees',
  '1.14': 'Village & Pillage',
  '1.13': 'Update Aquatic',
  '1.12': 'World of Color',
  '1.11': 'Exploration',
  '1.10': 'Frostburn',
  '1.9': 'Combat Update',
  '1.8': 'Bountiful Update',
  '1.7': 'Changed the World',
  '1.6': 'Horse Update',
  '1.5': 'Redstone Update',
  '1.4': 'Pretty Scary',
  '1.3': 'Minecraft 1.3',
  '1.2': 'Minecraft 1.2',
  '1.1': 'Minecraft 1.1',
  '1.0': 'Adventure Update'
}

export const NATIVE_CLIENT_REDIRECT =
  'https://login.microsoftonline.com/common/oauth2/nativeclient'

export const APP_NAME = 'my-mc-launcher'
