import path from 'path'
import fs from 'fs'

/**
 * electron-builder afterPack: embed deepslate icon into the unpacked exe.
 * rcedit is Windows-only — import it lazily so macOS/Linux packaging can load this hook.
 */
export default async function afterPack(context) {
  if (context.electronPlatformName !== 'win32') return
  try {
    const { rcedit } = await import('rcedit')
    const exeName = `${context.packager.appInfo.productFilename}.exe`
    const exePath = path.join(context.appOutDir, exeName)
    const iconPath = path.join(context.packager.projectDir, '..', 'assets', 'icon.ico')
    if (!fs.existsSync(iconPath)) {
      console.warn('[afterPack] icon missing:', iconPath)
      return
    }
    fs.copyFileSync(iconPath, path.join(context.appOutDir, 'icon.ico'))
    console.log('[afterPack] stamping icon onto', exePath)
    await rcedit(exePath, {
      icon: iconPath,
      'version-string': {
        ProductName: 'Deepslate Launcher',
        FileDescription: 'Deepslate Launcher',
        CompanyName: 'Deepslate'
      }
    })
  } catch (err) {
    console.warn('[afterPack] skipped:', err?.message || err)
  }
}
