import fs from 'fs'
import path from 'path'
import { fileURLToPath } from 'url'

const desktop = path.join(path.dirname(fileURLToPath(import.meta.url)), '..')
const releaseDir = path.join(desktop, 'release')
const setupsDir = path.join(desktop, '..', 'setups')

function keep(name) {
  const lower = name.toLowerCase()
  if (lower.endsWith('.blockmap') || lower.endsWith('.yml') || lower.endsWith('.yaml')) return false
  if (lower.endsWith('.dmg')) return true
  if (lower.endsWith('.appimage') || lower.endsWith('.deb') || lower.endsWith('.tar.gz')) return true
  if (lower.endsWith('.exe') && (lower.includes('setup') || lower.includes('installer'))) return true
  return false
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

for (const name of files) {
  const from = path.join(releaseDir, name)
  const to = path.join(setupsDir, name.replace(/ /g, '-'))
  fs.copyFileSync(from, to)
  console.log('[copy-setups]', path.basename(to))
}
