import fs from 'fs'
import path from 'path'
import { resourceRoot } from './paths'

export const DEEPSLATE_UI_JAR = 'deepslate-ui.jar'

/** Resolve packaged UI jar for a Minecraft version (exact, then newest packaged fallback). */
export function deepslateUiJarFor(mc: string): string | null {
  const exact = path.join(resourceRoot(), 'deepslate-ui', mc, DEEPSLATE_UI_JAR)
  if (fs.existsSync(exact)) return exact

  const root = path.join(resourceRoot(), 'deepslate-ui')
  if (!fs.existsSync(root)) return null
  const versions = fs
    .readdirSync(root, { withFileTypes: true })
    .filter((d) => d.isDirectory())
    .map((d) => d.name)
    .filter((name) => fs.existsSync(path.join(root, name, DEEPSLATE_UI_JAR)))
    .sort((a, b) => b.localeCompare(a, undefined, { numeric: true }))
  if (versions.length === 0) return null
  const fallback = path.join(root, versions[0], DEEPSLATE_UI_JAR)
  console.warn(
    `[deepslate-ui] no jar for ${mc}; using ${versions[0]} (launch Fabric ${versions[0]} for a matching build)`
  )
  return fallback
}

export function isDeepslateUiJar(name: string): boolean {
  const n = name.toLowerCase()
  return n === DEEPSLATE_UI_JAR || n.startsWith('deepslate-ui')
}

/** @deprecated Use ensureHiddenClientMods from client-mods.ts */
export function ensureDeepslateUiMod(mc: string, _root?: string): string | null {
  const src = deepslateUiJarFor(mc)
  return src
}
