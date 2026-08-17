import { execFileSync } from 'child_process'
import fs from 'fs'
import path from 'path'
import { fileURLToPath } from 'url'

const desktop = path.join(path.dirname(fileURLToPath(import.meta.url)), '..')
const releaseDir = path.join(desktop, 'release')
const setupsDir = path.join(desktop, '..', 'setups')
const installDir = path.join(desktop, 'install')
const version = JSON.parse(fs.readFileSync(path.join(desktop, 'package.json'), 'utf8')).version

const notes = {
  windows: path.join(installDir, 'WINDOWS.txt'),
  macos: path.join(installDir, 'MACOS.txt'),
  linux: path.join(installDir, 'LINUX.txt')
}

const zipNames = {
  windows: `deepslate-windows-${version}.zip`,
  macos: `deepslate-mac-${version}.zip`,
  linux: `deepslate-ubuntu-${version}.zip`
}

function keep(name) {
  const lower = name.toLowerCase()
  if (lower.endsWith('.blockmap') || lower.endsWith('.yml') || lower.endsWith('.yaml')) return false
  if (lower.endsWith('.dmg')) return true
  if (lower.endsWith('.appimage') || lower.endsWith('.deb') || lower.endsWith('.tar.gz')) return true
  if (lower.endsWith('.exe') && (lower.includes('setup') || lower.includes('installer'))) return true
  return false
}

function platformOf(name) {
  const lower = name.toLowerCase()
  if (lower.endsWith('.exe')) return 'windows'
  if (lower.endsWith('.dmg')) return 'macos'
  return 'linux'
}

function zipFolder(folder, zipPath) {
  fs.rmSync(zipPath, { force: true })
  if (process.platform === 'win32') {
    execFileSync(
      'powershell.exe',
      ['-NoProfile', '-Command', `Compress-Archive -Path '${folder}\\*' -DestinationPath '${zipPath}' -Force`],
      { stdio: 'inherit' }
    )
    return
  }
  execFileSync('zip', ['-r', '-j', zipPath, '.'], { cwd: folder, stdio: 'inherit' })
}

fs.mkdirSync(setupsDir, { recursive: true })

if (!fs.existsSync(releaseDir)) {
  console.warn('[copy-setups] no desktop/release yet — run npm run dist / dist:linux / dist:mac first')
  process.exit(0)
}

const files = fs.readdirSync(releaseDir).filter(keep)
if (!files.length) {
  console.warn('[copy-setups] no installer files in desktop/release')
  process.exit(0)
}

const packed = new Set()

for (const name of files) {
  const platform = platformOf(name)
  const destDir = path.join(setupsDir, platform)
  fs.mkdirSync(destDir, { recursive: true })
  const destName = name.replace(/ /g, '-')
  fs.copyFileSync(path.join(releaseDir, name), path.join(destDir, destName))
  fs.copyFileSync(notes[platform], path.join(destDir, 'INSTALL.txt'))
  packed.add(platform)
  console.log('[copy-setups]', path.join(platform, destName))
}

for (const platform of packed) {
  const zipPath = path.join(setupsDir, zipNames[platform])
  zipFolder(path.join(setupsDir, platform), zipPath)
  console.log('[copy-setups]', path.basename(zipPath))
}
