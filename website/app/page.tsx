import { getLatestDownloads, getReleaseList, GITHUB_REPO } from '../lib/release'
import Downloads from './components/Downloads'
import HeroVisual from './components/HeroVisual'
import AccentControl from './components/AccentControl'
import Versions from './components/Versions'

export const revalidate = 60

const features = [
  {
    title: 'Play your way',
    body: 'Microsoft for the full experience, Offline for a local name, or Guest for locked-down singleplayer.'
  },
  {
    title: 'Fabric mods',
    body: 'Search Modrinth from the launcher and install mods that match your Minecraft version.'
  },
  {
    title: 'Info HUD',
    body: 'FPS, ping, coordinates, biome, day counter, keystrokes, and a custom crosshair you can edit in-game.'
  },
  {
    title: 'Armor and tools',
    body: 'Durability bars, status effects with timers, held-item counts, and auto-refill when a stack runs out.'
  },
  {
    title: 'Zoom and fullbright',
    body: 'Hold C to zoom and scroll to change strength. Fullbright for caves, night, and the Nether.'
  },
  {
    title: 'Deepslate UI',
    body: 'Custom logo, hover on menus with customisable color using hex codes, pause branding, and a Mods button — all toggleable in-game.'
  }
]

function IconGitHub() {
  return (
    <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden>
      <path d="M12 2a10 10 0 0 0-3.16 19.49c.5.09.68-.22.68-.48v-1.7c-2.78.6-3.37-1.34-3.37-1.34-.45-1.16-1.1-1.47-1.1-1.47-.9-.62.07-.6.07-.6 1 .07 1.53 1.03 1.53 1.03.89 1.52 2.34 1.08 2.91.83.09-.65.35-1.08.63-1.33-2.22-.25-4.56-1.11-4.56-4.95 0-1.1.39-1.99 1.03-2.7-.1-.25-.45-1.27.1-2.64 0 0 .84-.27 2.75 1.02A9.56 9.56 0 0 1 12 6.8c.85 0 1.7.11 2.5.33 1.9-1.29 2.74-1.02 2.74-1.02.55 1.37.2 2.39.1 2.64.64.71 1.03 1.6 1.03 2.7 0 3.85-2.34 4.7-4.57 4.95.36.31.68.92.68 1.85v2.74c0 .27.18.58.69.48A10 10 0 0 0 12 2Z" />
    </svg>
  )
}

export default async function HomePage() {
  const [downloads, releases] = await Promise.all([getLatestDownloads(), getReleaseList()])

  return (
    <div className="page">
      <div className="atmosphere" aria-hidden />

      <header className="nav">
        <a className="nav-brand" href="#top">
          <img src="/icon.png" alt="" width={34} height={34} className="pixel" />
          <span>
            Deepslate
            <em>Client</em>
          </span>
        </a>
        <nav>
          <a href="#features">Features</a>
          <a href="#versions">Versions</a>
          <a href="#download">Download</a>
          <a className="nav-git" href={GITHUB_REPO} target="_blank" rel="noreferrer">
            <IconGitHub />
            GitHub
          </a>
        </nav>
      </header>

      <main id="top">
        <section className="hero">
          <div className="hero-copy">
            <p className="eyebrow">Minecraft Java launcher</p>
            <h1 className="brand-mark">
              Deepslate
              <em>isn&apos;t useless.</em>
            </h1>
            <p className="lede">
              Dark mineral UI, Fabric mods, and a HUD you can actually place. Windows, macOS, and Linux — Microsoft,
              Offline, or Guest.
            </p>
            <div className="pills">
              <span className="pill">
                <strong>Windows</strong> · macOS · Linux
              </span>
              <span className="pill">
                <strong>Java</strong> Edition
              </span>
              <span className="pill">
                <strong>Free</strong> to use
              </span>
            </div>
            <AccentControl />
          </div>

          <div className="hero-stage">
            <HeroVisual />
          </div>
        </section>

        <section className="section" id="features">
          <div className="section-head">
            <h2>What ships in the client</h2>
            <p>The launcher plus Deepslate UI — HUD modules, menu styling, zoom, and fullbright.</p>
          </div>
          <div className="feature-grid">
            {features.map((feature) => (
              <article className="feature-card" key={feature.title}>
                <h3>{feature.title}</h3>
                <p>{feature.body}</p>
              </article>
            ))}
          </div>
        </section>

        <Downloads downloads={downloads} />
        <Versions releases={releases} />
      </main>

      <footer className="footer">
        <p>
          Deepslate Client is not affiliated with Mojang Studios or Microsoft. Minecraft is a trademark of Mojang
          Studios.
        </p>
        <a href={GITHUB_REPO} target="_blank" rel="noreferrer">
          github.com/dragos300/deepslate-client
        </a>
      </footer>
    </div>
  )
}
