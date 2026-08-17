const REPO = 'dragos300/deepslate-client'
export const RELEASES_PAGE = `https://github.com/${REPO}/releases/latest`
export const GITHUB_REPO = `https://github.com/${REPO}`

type GithubAsset = {
  name: string
  browser_download_url: string
}

type GithubRelease = {
  tag_name?: string
  name?: string
  assets?: GithubAsset[]
}

export type DownloadInfo = {
  href: string
  version: string | null
  filename: string | null
}

function scoreAsset(name: string): number {
  const lower = name.toLowerCase()
  if (!lower.endsWith('.exe')) return -1
  if (lower.includes('setup') || lower.includes('installer')) return 3
  if (lower.includes('portable')) return 1
  return 2
}

export async function getLatestWindowsDownload(): Promise<DownloadInfo> {
  try {
    const res = await fetch(`https://api.github.com/repos/${REPO}/releases/latest`, {
      headers: {
        Accept: 'application/vnd.github+json',
        'User-Agent': 'deepslateclient.xyz'
      },
      next: { revalidate: 300 }
    })

    if (!res.ok) {
      return { href: RELEASES_PAGE, version: null, filename: null }
    }

    const data = (await res.json()) as GithubRelease
    const assets = data.assets ?? []
    const exe = [...assets]
      .filter((asset) => scoreAsset(asset.name) >= 0)
      .sort((a, b) => scoreAsset(b.name) - scoreAsset(a.name))[0]

    if (!exe) {
      return { href: RELEASES_PAGE, version: data.tag_name ?? null, filename: null }
    }

    return {
      href: exe.browser_download_url,
      version: data.tag_name ?? data.name ?? null,
      filename: exe.name
    }
  } catch {
    return { href: RELEASES_PAGE, version: null, filename: null }
  }
}
