/**
 * minecraft-launcher-core flashes a Windows console during launch because it
 * runs `java -version` via child.exec (no windowsHide) and spawns `java.exe`
 * (console subsystem). Patch both, and prefer javaw.exe when available.
 */
import { spawn, execFileSync } from 'child_process'
import fs from 'fs'
import path from 'path'

let patched = false

export function silenceMclcConsole(): void {
  if (patched || process.platform !== 'win32') return
  patched = true

  try {
    // Same module instance Client uses
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    const Handler = require('minecraft-launcher-core/components/handler') as {
      prototype: {
        checkJava: (java: string) => Promise<{ run: boolean; message?: unknown }>
        client: { emit: (event: string, msg: string) => void }
      }
    }

    Handler.prototype.checkJava = function (java: string) {
      return new Promise((resolve) => {
        const child = spawn(java, ['-version'], {
          windowsHide: true,
          stdio: ['ignore', 'pipe', 'pipe']
        })
        let stderr = ''
        child.stderr?.on('data', (chunk: Buffer) => {
          stderr += chunk.toString('utf8')
        })
        child.on('error', (error) => {
          resolve({ run: false, message: error })
        })
        child.on('close', (code) => {
          if (code !== 0 && !stderr) {
            resolve({ run: false, message: new Error(`Java exited with code ${code}`) })
            return
          }
          const match = stderr.match(/"(.*?)"/)
          if (match) {
            this.client.emit(
              'debug',
              `[MCLC]: Using Java version ${match.pop()} ${stderr.includes('64-Bit') ? '64-bit' : '32-Bit'}`
            )
          }
          resolve({ run: true })
        })
      })
    }
  } catch {
    /* package layout changed — ignore */
  }

  try {
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    const Launcher = require('minecraft-launcher-core/components/launcher') as {
      prototype: {
        startMinecraft: (this: {
          options: {
            javaPath?: string
            root: string
            overrides: { cwd?: string; detached?: boolean }
          }
          emit: (event: string, payload?: unknown) => void
        }, args: string[]) => ReturnType<typeof spawn>
      }
    }

    Launcher.prototype.startMinecraft = function (launchArguments: string[]) {
      const javaPath = this.options.javaPath || 'javaw'
      const minecraft = spawn(javaPath, launchArguments, {
        cwd: this.options.overrides.cwd || this.options.root,
        detached: this.options.overrides.detached,
        windowsHide: true,
        stdio: ['ignore', 'pipe', 'pipe']
      })
      minecraft.stdout?.on('data', (data: Buffer) => {
        this.emit('data', data.toString('utf-8'))
      })
      minecraft.stderr?.on('data', (data: Buffer) => {
        this.emit('data', data.toString('utf-8'))
      })
      minecraft.on('close', (code) => this.emit('close', code))
      return minecraft
    }
  } catch {
    /* ignore */
  }
}

/** Prefer windowless javaw.exe next to java on PATH. */
export function resolveJavaPath(): string | undefined {
  if (process.platform !== 'win32') return undefined
  try {
    const out = execFileSync('where.exe', ['java'], {
      windowsHide: true,
      encoding: 'utf8'
    })
    const javaExe = out
      .split(/\r?\n/)
      .map((l) => l.trim())
      .find((l) => l.toLowerCase().endsWith('java.exe'))
    if (!javaExe) return 'javaw'
    const javaw = path.join(path.dirname(javaExe), 'javaw.exe')
    if (fs.existsSync(javaw)) return javaw
  } catch {
    /* fall through */
  }
  return 'javaw'
}
