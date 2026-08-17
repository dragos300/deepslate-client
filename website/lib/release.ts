const REPO = 'dragos300/deepslate-client'
export const RELEASES_PAGE = `https://github.com/${REPO}/releases`
export const GITHUB_REPO = `https://github.com/${REPO}`

type GithubAsset = {
  name: string
  browser_download_url: string
}

type GithubRelease = {
  draft?: boolean
  tag_name?: string
  name?: string
  assets?: GithubAsset[]
}

export type DownloadInfo = {
  href: string
  version: string | null
  filename: string | null
}

export type Platform = 'windows' | 'mac' | 'linux'

export type PlatformDownloads = {
  version: string | null
  windows: DownloadInfo
  mac: DownloadInfo
  linux: DownloadInfo
}

const empty: DownloadInfo = { href: RELEASES_PAGE, version: null, filename: null }

function pickAsset(assets: GithubAsset[] | undefined, test: (name: string) => boolean): GithubAsset | undefined {
  return (assets ?? []).find((asset) => test(asset.name.toLowerCase()))
}

function toInfo(release: GithubRelease, asset: GithubAsset | undefined): DownloadInfo {
  if (!asset) return { ...empty, version: release.tag_name ?? release.name ?? null }
  return {
    href: asset.browser_download_url,
    version: release.tag_name ?? release.name ?? null,
    filename: asset.name
  }
}

function pickWindows(assets: GithubAsset[] | undefined): GithubAsset | undefined {
  return (
    pickAsset(assets, (name) => name.includes('windows') && (name.endsWith('.zip') || name.endsWith('.exe'))) ??
    pickAsset(assets, (name) => name.endsWith('.exe') && (name.includes('setup') || name.includes('installer'))) ??
    pickAsset(assets, (name) => name.endsWith('.exe'))
  )
}

function pickMac(assets: GithubAsset[] | undefined): GithubAsset | undefined {
  return (
    pickAsset(assets, (name) => (name.includes('mac') || name.includes('darwin') || name.includes('osx')) && !name.includes('windows')) ??
    pickAsset(assets, (name) => name.endsWith('.dmg'))
  )
}

function pickLinux(assets: GithubAsset[] | undefined): GithubAsset | undefined {
  return (
    pickAsset(assets, (name) => name.includes('linux') || name.includes('ubuntu')) ??
    pickAsset(assets, (name) => name.endsWith('.appimage')) ??
    pickAsset(assets, (name) => name.endsWith('.tar.gz') || name.endsWith('.gz')) ??
    pickAsset(assets, (name) => name.endsWith('.deb'))
  )
}

export async function getLatestDownloads(): Promise<PlatformDownloads> {
  try {
    const res = await fetch(`https://api.github.com/repos/${REPO}/releases?per_page=10`, {
      headers: {
        Accept: 'application/vnd.github+json',
        'User-Agent': 'deepslateclient.xyz'
      },
      next: { revalidate: 60 }
    })

    if (!res.ok) {
      return { version: null, windows: empty, mac: empty, linux: empty }
    }

    const releases = (await res.json()) as GithubRelease[]
    const published = releases.filter((release) => !release.draft)
    const withAnySetup = published.find((release) => (release.assets ?? []).length > 0) ?? published[0]

    if (!withAnySetup) {
      return { version: null, windows: empty, mac: empty, linux: empty }
    }

    const assets = withAnySetup.assets
    return {
      version: withAnySetup.tag_name ?? withAnySetup.name ?? null,
      windows: toInfo(withAnySetup, pickWindows(assets)),
      mac: toInfo(withAnySetup, pickMac(assets)),
      linux: toInfo(withAnySetup, pickLinux(assets))
    }
  } catch {
    return { version: null, windows: empty, mac: empty, linux: empty }
  }
}
