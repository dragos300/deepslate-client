import { NextResponse } from 'next/server'
import { getLatestDownloads, RELEASES_PAGE, type Platform } from '../../../lib/release'

export const revalidate = 60

const platforms = new Set<Platform>(['windows', 'mac', 'linux'])

type RouteContext = {
  params: Promise<{ platform: string }>
}

export async function GET(_request: Request, context: RouteContext) {
  const { platform } = await context.params
  if (!platforms.has(platform as Platform)) {
    return NextResponse.redirect(RELEASES_PAGE, 302)
  }

  const downloads = await getLatestDownloads()
  const info = downloads[platform as Platform]
  if (!info.filename) {
    return NextResponse.redirect(RELEASES_PAGE, 302)
  }

  return NextResponse.redirect(info.href, 302)
}
