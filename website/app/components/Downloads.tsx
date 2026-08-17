'use client'

import { useEffect, useState, type ReactNode } from 'react'
import type { PlatformDownloads } from '../../lib/release'
import { RELEASES_PAGE } from '../../lib/release'

type Os = 'windows' | 'mac' | 'linux'

const steps: Record<Os, string[]> = {
  windows: [
    'Download the Windows setup and run it.',
    'If SmartScreen appears, choose More info → Run anyway.',
    'Finish the wizard, then open Deepslate Launcher from the Start menu.'
  ],
  mac: [
    'Download the macOS disk image and open it.',
    'Drag Deepslate Launcher into Applications.',
    'First launch: right-click the app → Open. If macOS still blocks it, go to System Settings → Privacy & Security → Open Anyway.'
  ],
  linux: [
    'Download the AppImage (or the .tar.gz if that is what you have).',
    'AppImage: chmod +x Deepslate-Launcher-*.AppImage then run it.',
    'Archive: tar -xf Deepslate-Launcher-*.tar.gz then run ./deepslate-launcher inside the extracted folder.'
  ]
}

function IconWin() {
  return (
    <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden>
      <path d="M3 5.5 10.5 4.4v7.1H3V5.5Zm8.3-1.3L21 3v8.5h-9.7V4.2ZM3 13.5h7.5v7.1L3 19.5v-6Zm8.3 0H21V21l-9.7-1.4v-6.1Z" />
    </svg>
  )
}

function IconMac() {
  return (
    <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden>
      <path d="M16.5 6.2c-.9.1-2-.6-2.6-1.4-.6-.8-.9-1.8-.8-2.8 1 .1 2.1.6 2.7 1.4.6.8.9 1.9.7 2.8ZM19.8 17.2c-.5 1.1-.7 1.6-1.4 2.5-1 1.3-2.3 2.9-4 2.9-1.6 0-2-.9-3.8-.9-1.8 0-2.4.9-3.9.9s-2.8-1.4-3.8-2.8c-2.8-3.9-3.1-8.5-1.4-10.9 1.2-1.7 3.1-2.7 4.9-2.7 1.8 0 3 .9 3.8.9.8 0 2.4-1.1 4.1-1 1.7.1 3.2.9 4.1 2.3-3.6 2-3 7.2.4 8.8Z" />
    </svg>
  )
}

function IconLinux() {
  return (
    <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden>
      <path d="M12.4 2.4c.8 0 1.7.8 2.1 2.1.3 1 .2 2.1-.2 2.8-.2-1.2-.8-2.2-1.7-2.2-.7 0-1.1.6-1.3 1.4-.5-1.5.1-4.1 1.1-4.1Zm-2.6 5c.8-.7 1.9-1.1 3.1-1.1 1.5 0 2.8.6 3.6 1.6.9 1.1 1.2 2.6.8 4.1l-.4 1.6c1.4.6 2.3 1.6 2.3 2.9 0 1.1-.6 2-1.6 2.6.6 1.4.4 3.1-.7 4.1-.8.7-1.9.9-2.9.6-.6 1.2-1.8 2-3.2 2-1.1 0-2.1-.5-2.7-1.3-.8.4-1.8.4-2.6-.1-1.2-.8-1.6-2.4-.9-3.7-1.2-.8-1.8-2.2-1.5-3.6.3-1.4 1.4-2.5 2.8-2.8L8 12c-.3-1.5 0-3 1.8-4.6Z" />
    </svg>
  )
}

function detectOs(): Os {
  if (typeof navigator === 'undefined') return 'windows'
  const ua = navigator.userAgent.toLowerCase()
  if (ua.includes('mac')) return 'mac'
  if (ua.includes('linux')) return 'linux'
  return 'windows'
}

export default function Downloads({ downloads }: { downloads: PlatformDownloads }) {
  const [os, setOs] = useState<Os>('windows')

  useEffect(() => {
    setOs(detectOs())
  }, [])

  const platforms: Array<{ id: Os; label: string; file: string; href: string; icon: ReactNode }> = [
    {
      id: 'windows',
      label: 'Windows',
      file: 'Setup .exe',
      href: downloads.windows.href,
      icon: <IconWin />
    },
    {
      id: 'mac',
      label: 'macOS',
      file: 'Disk image .dmg',
      href: downloads.mac.href,
      icon: <IconMac />
    },
    {
      id: 'linux',
      label: 'Linux',
      file: 'AppImage / tar.gz',
      href: downloads.linux.href,
      icon: <IconLinux />
    }
  ]

  const current = platforms.find((p) => p.id === os) ?? platforms[0]

  return (
    <section className="section download" id="download">
      <div className="download-card">
        <div>
          <p className="eyebrow">Get Deepslate.</p>
          <h2>Get the installer</h2>
        </div>
        <div className="download-actions">
          <a className="play-btn" href={current.href}>
            {current.icon}
            Download for {current.label}
          </a>
          <a className="ghost-btn" href={RELEASES_PAGE} target="_blank" rel="noreferrer">
            All releases
          </a>
        </div>
      </div>

      <div className="platform-grid">
        {platforms.map((platform) => (
          <article
            className={`platform-card${platform.id === os ? ' current' : ''}`}
            key={platform.id}
          >
            <h3>
              {platform.icon}
              {platform.label}
            </h3>
            <p className="platform-file">{platform.file}</p>
            <ol>
              {steps[platform.id].map((step) => (
                <li key={step}>{step}</li>
              ))}
            </ol>
            <a className="ghost-btn accent" href={platform.href}>
              Download {platform.label}
            </a>
          </article>
        ))}
      </div>
    </section>
  )
}
