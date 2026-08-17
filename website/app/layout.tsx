import type { Metadata } from 'next'
import { Manrope, Sora } from 'next/font/google'
import './globals.css'

const manrope = Manrope({
  subsets: ['latin'],
  weight: ['400', '500', '600', '700', '800'],
  variable: '--font-body-face'
})

const sora = Sora({
  subsets: ['latin'],
  weight: ['500', '600', '700', '800'],
  variable: '--font-display-face'
})

export const metadata: Metadata = {
  metadataBase: new URL('https://deepslateclient.xyz'),
  title: 'Deepslate Client',
  description:
    'Minecraft Java launcher with Fabric mods, a custom HUD, and a mineral-mint UI. Download for Windows.',
  applicationName: 'Deepslate Client',
  icons: {
    icon: [{ url: '/favicon.ico' }, { url: '/icon.png', type: 'image/png' }],
    apple: '/icon.png'
  },
  openGraph: {
    title: 'Deepslate Client',
    description: 'Minecraft Java launcher — Fabric mods, custom HUD, mineral-mint UI.',
    url: 'https://deepslateclient.xyz',
    siteName: 'Deepslate Client',
    type: 'website',
    images: [{ url: '/logo.png' }]
  }
}

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" className={`${manrope.variable} ${sora.variable}`}>
      <body>{children}</body>
    </html>
  )
}
