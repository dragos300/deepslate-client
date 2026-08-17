import { app, BrowserWindow, ipcMain, shell, dialog } from 'electron'
import path from 'path'
import { loadConfig, saveConfig, loadAccount, parseRamGb, saveServers, loadServers } from './config'
import { restoreModsStash } from './guest'
import { minecraftDirectory } from './config'
import { appIconPath, migrateAccountIfNeeded } from './paths'
import {
  versionsGrouped,
  wallpaperUrl,
  brandIconUrl,
  guestLockVersions,
  getLatestRelease,
  fabricSupportedVersions
} from './versions'
import { launchGame, isGameRunning } from './launch'
import { microsoftLoginInteractive, microsoftLogout, currentMicrosoftAccount } from './auth'
import { searchMods, installMod, modsDirectory } from './mods'
import { initDiscord, destroyDiscord, setDiscordIdle, refreshDiscordPresence } from './discord'
import { displayVersionLabel, parseVersionRef, makeVersionRef } from './versions-util'
import type { AppConfig, LaunchRequest, LoginMode, ModSource, ServerEntry } from '../shared/types'

let mainWindow: BrowserWindow | null = null

function createWindow(): void {
  const icon = appIconPath()
  mainWindow = new BrowserWindow({
    width: 1280,
    height: 800,
    minWidth: 960,
    minHeight: 640,
    title: 'Deepslate Launcher',
    backgroundColor: '#070a08',
    ...(icon ? { icon } : {}),
    webPreferences: {
      preload: path.join(__dirname, '../preload/index.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false
    },
    autoHideMenuBar: true
  })

  if (process.env.ELECTRON_RENDERER_URL) {
    void mainWindow.loadURL(process.env.ELECTRON_RENDERER_URL)
  } else {
    void mainWindow.loadFile(path.join(__dirname, '../renderer/index.html'))
  }

  mainWindow.on('closed', () => {
    mainWindow = null
  })
}

function sendProgress(value: number, maximum: number, status: string): void {
  mainWindow?.webContents.send('launch:progress', { value, maximum, status })
}

function registerIpc(): void {
  ipcMain.handle('config:get', () => loadConfig())
  ipcMain.handle('config:save', (_e, patch: Partial<AppConfig>) => {
    const prev = loadConfig()
    const next = { ...prev, ...patch }
    saveConfig(next)
    if (patch.legacy4j_enabled !== undefined && patch.legacy4j_enabled !== prev.legacy4j_enabled) {
      refreshDiscordPresence(displayVersionLabel(next.last_version))
    }
    return next
  })
  ipcMain.handle('config:ram', () => parseRamGb(loadConfig().jvm_arguments))
  ipcMain.handle('account:get', () => currentMicrosoftAccount())
  ipcMain.handle('account:login', async () => microsoftLoginInteractive())
  ipcMain.handle('account:logout', () => {
    microsoftLogout()
    return null
  })
  ipcMain.handle('versions:groups', async (_e, mode: LoginMode) => {
    return versionsGrouped({
      fabricOnly: mode === 'guest' || mode === 'microsoft',
      guestOnly: mode === 'guest',
      limitPerFamily: 12
    })
  })
  ipcMain.handle('versions:guest', () => guestLockVersions())
  ipcMain.handle('versions:fabric', () => fabricSupportedVersions(true))
  ipcMain.handle('versions:latest', () => getLatestRelease())
  ipcMain.handle('versions:label', (_e, ref: string) => displayVersionLabel(ref))
  ipcMain.handle('versions:parse', (_e, ref: string) => parseVersionRef(ref))
  ipcMain.handle('versions:make', (_e, loader: 'vanilla' | 'fabric', mc: string) =>
    makeVersionRef(loader, mc)
  )
  ipcMain.handle('assets:wallpaper', () => wallpaperUrl())
  ipcMain.handle('assets:brandIcon', () => brandIconUrl())
  ipcMain.handle('servers:list', () => loadServers())
  ipcMain.handle('servers:save', (_e, servers: ServerEntry[]) => saveServers(servers).servers)
  ipcMain.handle('mods:search', async (_e, query: string, gameVersion: string, loader: string, source: ModSource) =>
    searchMods(query, { gameVersion, loader, source })
  )
  ipcMain.handle('mods:install', async (_e, hit, gameVersion: string, loader: string) =>
    installMod(hit, { gameVersion, loader })
  )
  ipcMain.handle(
    'mods:openFolder',
    async (_e, loader?: string, gameVersion?: string) => {
      let mc = gameVersion || ''
      let load = loader || 'fabric'
      if (!mc || mc === 'latest') {
        const cfg = loadConfig()
        const [parsedLoader, parsedMc] = parseVersionRef(cfg.last_version || 'latest')
        load = loader || parsedLoader
        mc = parsedMc === 'latest' ? await getLatestRelease() : parsedMc
      }
      const dir = modsDirectory({ loader: load, gameVersion: mc })
      await shell.openPath(dir)
      return dir
    }
  )
  ipcMain.handle('game:launch', async (_e, req: LaunchRequest) => {
    const result = await launchGame(
      req,
      (ev) => sendProgress(ev.value, ev.maximum, ev.status),
      (code) => {
        mainWindow?.webContents.send('launch:exit', { code })
        if (!req.keepOpen) {
          app.quit()
        } else {
          mainWindow?.show()
          mainWindow?.restore()
        }
      }
    )
    if (result.ok && req.keepOpen) {
      mainWindow?.minimize()
    }
    return result
  })
  ipcMain.handle('game:running', () => isGameRunning())
  ipcMain.handle('dialog:error', (_e, message: string, title?: string) => {
    if (!mainWindow) return
    return dialog.showMessageBox(mainWindow, {
      type: 'error',
      title: title || 'Error',
      message
    })
  })
  ipcMain.handle('shell:openExternal', (_e, url: string) => shell.openExternal(url))
}

app.whenReady().then(() => {
  if (process.platform === 'win32') {
    app.setAppUserModelId('com.deepslate.client')
  }
  try {
    migrateAccountIfNeeded()
  } catch {
    /* ignore */
  }
  try {
    restoreModsStash(minecraftDirectory())
  } catch {
    /* ignore */
  }
  registerIpc()
  createWindow()
  void initDiscord().then(() => setDiscordIdle(displayVersionLabel(loadConfig().last_version)))

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow()
  })
})

app.on('window-all-closed', () => {
  destroyDiscord()
  if (process.platform !== 'darwin') app.quit()
})
