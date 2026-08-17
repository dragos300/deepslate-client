import { app } from 'electron'
import fs from 'fs'
import path from 'path'
import { APP_NAME } from '../shared/types'

export function resourceRoot(): string {
  if (app.isPackaged) {
    return path.join(process.resourcesPath, 'assets')
  }
  // electron-vite: getAppPath may be desktop/ or desktop/out/main
  const candidates = [
    path.join(app.getAppPath(), '..', 'assets'),
    path.join(app.getAppPath(), '..', '..', 'assets'),
    path.join(process.cwd(), '..', 'assets'),
    path.join(process.cwd(), 'assets'),
    path.join(__dirname, '..', '..', '..', 'assets')
  ]
  for (const dir of candidates) {
    if (fs.existsSync(dir)) return dir
  }
  return candidates[0]
}

export function configExamplePath(): string {
  if (app.isPackaged) {
    return path.join(process.resourcesPath, 'config.example.json')
  }
  return path.join(app.getAppPath(), '..', 'config.example.json')
}

/** Config + account live next to the project (dev) or in userData (packaged). */
export function appDataRoot(): string {
  if (app.isPackaged) {
    return app.getPath('userData')
  }
  return path.join(app.getAppPath(), '..')
}

export function configPath(): string {
  return path.join(appDataRoot(), 'config.json')
}

export function accountPath(): string {
  // Always use Electron userData so MS sessions work for both `npm run dev` and packaged builds
  return path.join(app.getPath('userData'), 'account.json')
}

/** One-time: copy a legacy project-root account into userData if needed. */
export function migrateAccountIfNeeded(): void {
  try {
    const dest = accountPath()
    if (fs.existsSync(dest)) return
    const legacy = path.join(app.getAppPath(), '..', 'account.json')
    if (fs.existsSync(legacy)) {
      ensureDir(path.dirname(dest))
      fs.copyFileSync(legacy, dest)
    }
  } catch {
    /* ignore */
  }
}

export function defaultMinecraftDir(): string {
  if (process.platform === 'win32') {
    return path.join(process.env.APPDATA || app.getPath('appData'), APP_NAME)
  }
  if (process.platform === 'darwin') {
    return path.join(app.getPath('appData'), APP_NAME)
  }
  return path.join(app.getPath('home'), `.${APP_NAME}`)
}

export function ensureDir(dir: string): void {
  fs.mkdirSync(dir, { recursive: true })
}

/** Window / taskbar icon (deepslate block). */
export function appIconPath(): string | undefined {
  const candidates = [
    path.join(resourceRoot(), 'icon.ico'),
    path.join(resourceRoot(), 'deepslate_block.png'),
    path.join(resourceRoot(), 'icon.png'),
    // Packaged install: icon copied next to the exe
    path.join(path.dirname(process.execPath), 'icon.ico'),
    // Dev: repo assets/
    path.join(app.getAppPath(), '..', 'assets', 'icon.ico'),
    path.join(app.getAppPath(), '..', 'assets', 'deepslate_block.png')
  ]
  for (const p of candidates) {
    if (fs.existsSync(p)) return p
  }
  return undefined
}
