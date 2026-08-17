/**
 * Stamp assets/icon.ico onto packaged / installed Windows executables.
 * Needed when electron-builder's signAndEditExecutable is false.
 */
import path from 'path'
import fs from 'fs'
import { fileURLToPath } from 'url'
import { rcedit } from 'rcedit'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const desktop = path.join(__dirname, '..')
const iconPath = path.join(desktop, '..', 'assets', 'icon.ico')

async function stamp(exePath) {
  if (!fs.existsSync(exePath)) {
    console.warn('[stamp-icon] missing exe:', exePath)
    return false
  }
  if (!fs.existsSync(iconPath)) {
    throw new Error(`[stamp-icon] missing icon: ${iconPath}`)
  }
  console.log('[stamp-icon]', exePath)
  await rcedit(exePath, {
    icon: iconPath,
    'version-string': {
      ProductName: 'Deepslate Launcher',
      FileDescription: 'Deepslate Launcher',
      CompanyName: 'Deepslate',
      LegalCopyright: 'MIT'
    },
    'product-version': '1.0.0',
    'file-version': '1.0.0'
  })
  return true
}

const targets = [
  path.join(desktop, 'release', 'win-unpacked', 'Deepslate Launcher.exe'),
  path.join(desktop, 'release', 'Deepslate Launcher 1.0.0.exe'),
  path.join(process.env.LOCALAPPDATA || '', 'Programs', 'Deepslate Launcher', 'Deepslate Launcher.exe')
]

for (const exe of targets) {
  try {
    await stamp(exe)
  } catch (err) {
    console.warn('[stamp-icon] failed for', exe, '-', err?.message || err)
  }
}

const installDir = path.join(process.env.LOCALAPPDATA || '', 'Programs', 'Deepslate Launcher')
const installedExe = path.join(installDir, 'Deepslate Launcher.exe')
if (fs.existsSync(installDir) && fs.existsSync(iconPath)) {
  fs.copyFileSync(iconPath, path.join(installDir, 'icon.ico'))
  console.log('[stamp-icon] copied icon.ico to install dir')
}

// Point Desktop / Start Menu shortcuts at the stamped exe icon (index 0).
// Loose .ico files with PNG-compressed entries often fail in Explorer.
if (process.platform === 'win32' && fs.existsSync(installedExe)) {
  try {
    const { execFileSync } = await import('child_process')
    const ps = `
$ErrorActionPreference = 'Stop'
$sh = New-Object -ComObject WScript.Shell
$exe = ${JSON.stringify(installedExe)}
$links = @(
  (Join-Path $env:USERPROFILE 'Desktop\\Deepslate Launcher.lnk'),
  (Join-Path $env:APPDATA 'Microsoft\\Windows\\Start Menu\\Programs\\Deepslate Launcher.lnk'),
  (Join-Path $env:APPDATA 'Microsoft\\Windows\\Start Menu\\Programs\\Deepslate Launcher\\Deepslate Launcher.lnk')
)
foreach ($p in $links) {
  if (-not (Test-Path $p)) { continue }
  $l = $sh.CreateShortcut($p)
  $l.TargetPath = $exe
  $l.WorkingDirectory = Split-Path $exe
  $l.IconLocation = "$exe,0"
  $l.Save()
  Write-Output "updated $p"
}
`
    const out = execFileSync(
      'powershell.exe',
      ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-Command', ps],
      { encoding: 'utf8' }
    )
    if (out.trim()) console.log('[stamp-icon]', out.trim())
  } catch (err) {
    console.warn('[stamp-icon] shortcut update failed:', err?.message || err)
  }
}
