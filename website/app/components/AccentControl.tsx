'use client'

import { useEffect, useState, type CSSProperties } from 'react'

const PRESETS = [
  { name: 'Mint', hex: '#3fd4b8' },
  { name: 'Diamond', hex: '#4ec8e0' },
  { name: 'Redstone', hex: '#e06b6b' },
  { name: 'Gold', hex: '#e0b84e' },
  { name: 'Amethyst', hex: '#b07ee0' },
  { name: 'Lapis', hex: '#4f7ae0' }
]

const STORAGE_KEY = 'deepslate-accent'

function parseHex(value: string): string | null {
  const hex = value.trim().replace(/^#/, '')
  if (!/^[0-9a-fA-F]{6}$/.test(hex)) return null
  return `#${hex.toLowerCase()}`
}

function mix(hex: string, amount: number): string {
  const n = hex.slice(1)
  const r = Math.round(parseInt(n.slice(0, 2), 16) * amount)
  const g = Math.round(parseInt(n.slice(2, 4), 16) * amount)
  const b = Math.round(parseInt(n.slice(4, 6), 16) * amount)
  return `#${[r, g, b].map((c) => c.toString(16).padStart(2, '0')).join('')}`
}

export function applyAccent(hex: string) {
  const parsed = parseHex(hex)
  if (!parsed) return
  const n = parsed.slice(1)
  const r = parseInt(n.slice(0, 2), 16)
  const g = parseInt(n.slice(2, 4), 16)
  const b = parseInt(n.slice(4, 6), 16)
  const root = document.documentElement
  root.style.setProperty('--accent', parsed)
  root.style.setProperty('--accent-2', mix(parsed, 0.72))
  root.style.setProperty('--accent-soft', `rgba(${r}, ${g}, ${b}, 0.14)`)
  root.style.setProperty('--accent-glow', `rgba(${r}, ${g}, ${b}, 0.35)`)
  root.style.setProperty('--accent-border', `rgba(${r}, ${g}, ${b}, 0.42)`)
  root.style.setProperty('--play-top', `#${[
    Math.min(255, Math.round(r * 1.12)),
    Math.min(255, Math.round(g * 1.12)),
    Math.min(255, Math.round(b * 1.08))
  ]
    .map((c) => c.toString(16).padStart(2, '0'))
    .join('')}`)
  root.style.setProperty('--play-bot', mix(parsed, 0.68))
}

export default function AccentControl() {
  const [hex, setHex] = useState('#3fd4b8')

  useEffect(() => {
    const saved = parseHex(localStorage.getItem(STORAGE_KEY) ?? '')
    const next = saved ?? '#3fd4b8'
    setHex(next)
    applyAccent(next)
  }, [])

  function choose(next: string) {
    const parsed = parseHex(next)
    if (!parsed) {
      setHex(next)
      return
    }
    setHex(parsed)
    applyAccent(parsed)
    localStorage.setItem(STORAGE_KEY, parsed)
  }

  return (
    <div className="accent-control">
      <p>Try a colour</p>
      <div className="accent-swatches">
        {PRESETS.map((preset) => (
          <button
            key={preset.hex}
            type="button"
            className={hex === preset.hex ? 'on' : undefined}
            style={{ '--swatch': preset.hex } as CSSProperties}
            aria-label={preset.name}
            title={preset.name}
            onClick={() => choose(preset.hex)}
          />
        ))}
        <label>
          <span>Hex</span>
          <input
            value={hex}
            spellCheck={false}
            maxLength={7}
            aria-label="Accent hex colour"
            onChange={(event) => choose(event.target.value)}
          />
        </label>
      </div>
    </div>
  )
}
