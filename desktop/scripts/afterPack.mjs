import path from 'path'
import fs from 'fs'
import { rcedit } from 'rcedit'

/**
 * electron-builder afterPack: embed deepslate icon into the unpacked exe.
 */
export default async function afterPack(context) {
  if (context.electronPlatformName !== 'win32') return
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
}
