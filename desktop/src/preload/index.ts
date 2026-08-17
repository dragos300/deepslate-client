import { contextBridge, ipcRenderer } from 'electron'
import type {
  AppConfig,
  AccountData,
  LaunchRequest,
  LaunchResult,
  LoginMode,
  ModHit,
  ModSource,
  ProgressEvent,
  ServerEntry,
  VersionGroup
} from '../shared/types'

const api = {
  getConfig: (): Promise<AppConfig> => ipcRenderer.invoke('config:get'),
  saveConfig: (patch: Partial<AppConfig>): Promise<AppConfig> =>
    ipcRenderer.invoke('config:save', patch),
  getRam: (): Promise<number> => ipcRenderer.invoke('config:ram'),
  getAccount: (): Promise<AccountData | null> => ipcRenderer.invoke('account:get'),
  loginMicrosoft: (): Promise<AccountData> => ipcRenderer.invoke('account:login'),
  logoutMicrosoft: (): Promise<null> => ipcRenderer.invoke('account:logout'),
  versionGroups: (mode: LoginMode): Promise<VersionGroup[]> =>
    ipcRenderer.invoke('versions:groups', mode),
  guestVersions: (): Promise<string[]> => ipcRenderer.invoke('versions:guest'),
  fabricVersions: (): Promise<string[]> => ipcRenderer.invoke('versions:fabric'),
  latestRelease: (): Promise<string> => ipcRenderer.invoke('versions:latest'),
  versionLabel: (ref: string): Promise<string> => ipcRenderer.invoke('versions:label', ref),
  parseVersion: (ref: string): Promise<[string, string]> => ipcRenderer.invoke('versions:parse', ref),
  makeVersion: (loader: 'vanilla' | 'fabric', mc: string): Promise<string> =>
    ipcRenderer.invoke('versions:make', loader, mc),
  wallpaper: (): Promise<string | undefined> => ipcRenderer.invoke('assets:wallpaper'),
  brandIcon: (): Promise<string | undefined> => ipcRenderer.invoke('assets:brandIcon'),
  listServers: (): Promise<ServerEntry[]> => ipcRenderer.invoke('servers:list'),
  saveServers: (servers: ServerEntry[]): Promise<ServerEntry[]> =>
    ipcRenderer.invoke('servers:save', servers),
  searchMods: (
    query: string,
    gameVersion: string,
    loader: string,
    source: ModSource
  ): Promise<ModHit[]> => ipcRenderer.invoke('mods:search', query, gameVersion, loader, source),
  installMod: (hit: ModHit, gameVersion: string, loader: string): Promise<string> =>
    ipcRenderer.invoke('mods:install', hit, gameVersion, loader),
  openModsFolder: (loader?: string, gameVersion?: string): Promise<string> =>
    ipcRenderer.invoke('mods:openFolder', loader, gameVersion),
  launch: (req: LaunchRequest): Promise<LaunchResult> => ipcRenderer.invoke('game:launch', req),
  isRunning: (): Promise<boolean> => ipcRenderer.invoke('game:running'),
  onProgress: (cb: (ev: ProgressEvent) => void): (() => void) => {
    const listener = (_: unknown, ev: ProgressEvent): void => cb(ev)
    ipcRenderer.on('launch:progress', listener)
    return () => ipcRenderer.removeListener('launch:progress', listener)
  },
  onExit: (cb: (code: number | null) => void): (() => void) => {
    const listener = (_: unknown, payload: { code: number | null }): void => cb(payload.code)
    ipcRenderer.on('launch:exit', listener)
    return () => ipcRenderer.removeListener('launch:exit', listener)
  },
  openExternal: (url: string): Promise<void> => ipcRenderer.invoke('shell:openExternal', url)
}

contextBridge.exposeInMainWorld('deepslate', api)

export type DeepslateApi = typeof api
