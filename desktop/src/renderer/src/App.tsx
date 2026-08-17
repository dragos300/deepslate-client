import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type {
  AccountData,
  AppConfig,
  LoginMode,
  ModHit,
  ModSource,
  ServerEntry,
  VersionGroup
} from '../../shared/types'

type Page = 'play' | 'mods' | 'settings'
type VersionStep = 'major' | 'patch' | 'loader'

function IconHome(): React.JSX.Element {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden>
      <path d="M4 10.5 12 4l8 6.5V20a1 1 0 0 1-1 1h-5v-6H10v6H5a1 1 0 0 1-1-1v-9.5Z" />
    </svg>
  )
}

function IconMods(): React.JSX.Element {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden>
      <rect x="3" y="3" width="7" height="7" rx="1.5" />
      <rect x="14" y="3" width="7" height="7" rx="1.5" />
      <rect x="3" y="14" width="7" height="7" rx="1.5" />
      <rect x="14" y="14" width="7" height="7" rx="1.5" />
    </svg>
  )
}

function IconSettings(): React.JSX.Element {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden>
      <circle cx="12" cy="12" r="3.2" />
      <path d="M12 3.5v2.2M12 18.3v2.2M4.9 6.5l1.6 1.6M17.5 15.9l1.6 1.6M3.5 12h2.2M18.3 12h2.2M4.9 17.5l1.6-1.6M17.5 8.1l1.6-1.6" />
    </svg>
  )
}

function IconPlay(): React.JSX.Element {
  return (
    <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden>
      <path d="M8 5.5v13l11-6.5L8 5.5Z" />
    </svg>
  )
}

function IconVersion(): React.JSX.Element {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden>
      <path d="M7 7h10v10H7z" />
      <path d="M4 10V4h6M20 14v6h-6" />
    </svg>
  )
}

function IconRam(): React.JSX.Element {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden>
      <rect x="3" y="7" width="18" height="10" rx="2" />
      <path d="M7 7V5M12 7V5M17 7V5M7 19v-2M12 19v-2M17 19v-2" />
    </svg>
  )
}

function IconMs(): React.JSX.Element {
  return (
    <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden>
      <path d="M3 3h8.5v8.5H3V3Zm9.5 0H21v8.5h-8.5V3ZM3 12.5H11.5V21H3v-8.5Zm9.5 0H21V21h-8.5v-8.5Z" />
    </svg>
  )
}

function IconUser(): React.JSX.Element {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden>
      <circle cx="12" cy="8" r="3.5" />
      <path d="M5 19.5c1.8-3.2 4.2-4.8 7-4.8s5.2 1.6 7 4.8" />
    </svg>
  )
}

function IconGuest(): React.JSX.Element {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden>
      <path d="M12 3 4 7v5c0 5 3.4 8.4 8 9 4.6-.6 8-4 8-9V7l-8-4Z" />
    </svg>
  )
}

function IconLogout(): React.JSX.Element {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden>
      <path d="M10 4H6a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h4" />
      <path d="M15 16l4-4-4-4M9 12h10" />
    </svg>
  )
}

export default function App(): React.JSX.Element {
  const [config, setConfig] = useState<AppConfig | null>(null)
  const [account, setAccount] = useState<AccountData | null>(null)
  const [page, setPage] = useState<Page>('play')
  const [wallpaper, setWallpaper] = useState<string>()
  const [brandIcon, setBrandIcon] = useState<string>()
  const [username, setUsername] = useState('Player')
  const [version, setVersion] = useState('latest')
  const [versionLabel, setVersionLabel] = useState('Latest release')
  const [ram, setRam] = useState(4)
  const [keepOpen, setKeepOpen] = useState(true)
  const [legacy4j, setLegacy4j] = useState(false)
  const [busy, setBusy] = useState(false)
  const [status, setStatus] = useState('')
  const [progress, setProgress] = useState(0)
  const [accountOpen, setAccountOpen] = useState(false)
  const [versionOpen, setVersionOpen] = useState(false)
  const [groups, setGroups] = useState<VersionGroup[]>([])
  const [step, setStep] = useState<VersionStep>('major')
  const [family, setFamily] = useState<VersionGroup | null>(null)
  const [pickMc, setPickMc] = useState('')
  const [servers, setServers] = useState<ServerEntry[]>([])
  const [serverName, setServerName] = useState('')
  const [serverAddress, setServerAddress] = useState('')
  const [serverPort, setServerPort] = useState('25565')
  const [pendingServer, setPendingServer] = useState<ServerEntry | null>(null)
  const [modSource, setModSource] = useState<ModSource>('modrinth')
  const [modQuery, setModQuery] = useState('')
  const [modHits, setModHits] = useState<ModHit[]>([])
  const [modStatus, setModStatus] = useState('Search Modrinth for mods matching your version.')
  const footRef = useRef<HTMLDivElement>(null)

  const mode: LoginMode = config?.login_mode || 'cracked'
  const canMods = mode === 'microsoft'
  const canServers = mode !== 'guest'
  const guestFabricOnly = mode === 'guest'

  const displayName = useMemo(() => {
    if (mode === 'guest') return 'Guest'
    if (mode === 'microsoft') return account?.name || username
    return username
  }, [mode, account, username])

  const modeLabel = useMemo(() => {
    if (mode === 'guest') return 'Guest'
    if (mode === 'microsoft') return 'Microsoft'
    return 'Offline'
  }, [mode])

  const refreshVersionLabel = useCallback(async (ref: string) => {
    const label = await window.deepslate.versionLabel(ref)
    setVersionLabel(label)
  }, [])

  useEffect(() => {
    void (async () => {
      const cfg = await window.deepslate.getConfig()
      setConfig(cfg)
      setUsername(cfg.login_mode === 'guest' ? 'Guest' : cfg.offline_username || 'Player')
      setVersion(cfg.last_version || 'latest')
      setKeepOpen(cfg.keep_launcher_open !== false)
      setLegacy4j(!!cfg.legacy4j_enabled)
      setRam(await window.deepslate.getRam())
      setAccount(await window.deepslate.getAccount())
      setWallpaper(await window.deepslate.wallpaper())
      setBrandIcon(await window.deepslate.brandIcon())
      setServers(await window.deepslate.listServers())
      await refreshVersionLabel(cfg.last_version || 'latest')
    })()
    const offProgress = window.deepslate.onProgress((ev) => {
      setStatus(ev.status)
      if (ev.maximum > 0) setProgress(Math.min(1, ev.value / ev.maximum))
      else setProgress(0.15)
    })
    const offExit = window.deepslate.onExit(() => {
      setBusy(false)
      setStatus('')
      setProgress(0)
    })
    return () => {
      offProgress()
      offExit()
    }
  }, [refreshVersionLabel])

  useEffect(() => {
    if (!accountOpen) return
    const onDown = (e: MouseEvent): void => {
      if (footRef.current && !footRef.current.contains(e.target as Node)) {
        setAccountOpen(false)
      }
    }
    document.addEventListener('mousedown', onDown)
    return () => document.removeEventListener('mousedown', onDown)
  }, [accountOpen])

  useEffect(() => {
    if (!status) return
    const t = window.setTimeout(() => {
      if (!busy) setStatus('')
    }, 5000)
    return () => window.clearTimeout(t)
  }, [status, busy])

  async function persistMode(next: LoginMode): Promise<void> {
    let nextVersion = version
    let nextUser = username
    if (next === 'guest') {
      nextUser = 'Guest'
      const allowed = await window.deepslate.guestVersions()
      const [, mc] = await window.deepslate.parseVersion(version)
      let pick = allowed[0] || '1.21.11'
      try {
        const latest = await window.deepslate.latestRelease()
        if (allowed.includes(latest)) pick = latest
        else if (allowed.includes(mc)) pick = mc
      } catch {
        if (allowed.includes(mc)) pick = mc
      }
      nextVersion = await window.deepslate.makeVersion('fabric', pick)
    } else if (next === 'cracked') {
      const [, mc] = await window.deepslate.parseVersion(version)
      nextVersion = await window.deepslate.makeVersion('vanilla', mc === 'latest' ? 'latest' : mc)
      nextUser = config?.offline_username || 'Player'
    } else {
      nextUser = account?.name || config?.offline_username || 'Player'
    }
    const cfg = await window.deepslate.saveConfig({
      login_mode: next,
      last_version: nextVersion,
      offline_username: next === 'guest' ? config?.offline_username : nextUser
    })
    setConfig(cfg)
    setUsername(nextUser)
    setVersion(nextVersion)
    await refreshVersionLabel(nextVersion)
    setAccountOpen(false)
    if (next !== 'microsoft' && page === 'mods') setPage('play')
    setStatus(
      next === 'guest'
        ? 'Guest — Fabric 1.20.1–1.21.x · singleplayer lock.'
        : next === 'microsoft'
          ? 'Microsoft account ready.'
          : 'Offline mode — vanilla only.'
    )
  }

  async function openVersionMenu(): Promise<void> {
    if (busy) return
    setVersionOpen(true)
    setStep('major')
    setFamily(null)
    const g = await window.deepslate.versionGroups(mode)
    setGroups(g)
  }

  async function selectVersion(loader: 'vanilla' | 'fabric', mc: string): Promise<void> {
    if (guestFabricOnly) loader = 'fabric'
    if (mode === 'cracked' && loader === 'fabric') return
    const ref = await window.deepslate.makeVersion(loader, mc)
    setVersion(ref)
    await refreshVersionLabel(ref)
    await window.deepslate.saveConfig({ last_version: ref, mod_loader: loader })
    setVersionOpen(false)
  }

  async function onPlay(server?: ServerEntry | null): Promise<void> {
    if (busy) return
    setBusy(true)
    setStatus('Preparing…')
    setProgress(0)
    const result = await window.deepslate.launch({
      version,
      username: mode === 'guest' ? 'Guest' : username.trim() || 'Player',
      ramGb: ram,
      keepOpen,
      loginMode: mode,
      server: server || pendingServer
    })
    setPendingServer(null)
    if (!result.ok) {
      setBusy(false)
      setStatus(result.error || 'Launch failed')
      setProgress(0)
    } else {
      setStatus(`Launched ${result.resolvedVersion}`)
    }
  }

  async function saveServer(): Promise<void> {
    if (!serverAddress.trim()) return
    const port = parseInt(serverPort || '25565', 10)
    const entry: ServerEntry = {
      name: serverName.trim() || serverAddress.trim(),
      address: serverAddress.trim(),
      port: Number.isFinite(port) ? port : 25565
    }
    const next = [...servers]
    const idx = next.findIndex(
      (s) => s.address.toLowerCase() === entry.address.toLowerCase() && s.port === entry.port
    )
    if (idx >= 0) next[idx] = entry
    else next.push(entry)
    setServers(await window.deepslate.saveServers(next))
    setServerName('')
    setServerAddress('')
    setServerPort('25565')
  }

  async function runModSearch(): Promise<void> {
    if (!canMods) return
    const [, mc] = await window.deepslate.parseVersion(version)
    const gameVersion = mc === 'latest' ? await window.deepslate.latestRelease() : mc
    const [loader] = await window.deepslate.parseVersion(version)
    setModStatus(`Searching ${modSource}…`)
    try {
      const hits = await window.deepslate.searchMods(
        modQuery,
        gameVersion,
        loader === 'vanilla' ? 'fabric' : loader,
        modSource
      )
      setModHits(hits)
      setModStatus(`${hits.length} mods · ${gameVersion} · ${modSource}`)
    } catch (err) {
      setModHits([])
      setModStatus(err instanceof Error ? err.message : String(err))
    }
  }

  const initial = (displayName || 'P').slice(0, 1).toUpperCase()
  const skinUrl =
    mode === 'microsoft' && account?.id
      ? `https://crafatar.com/avatars/${account.id.replace(/-/g, '')}?size=80&overlay`
      : undefined

  return (
    <div className="app">
      <div
        className="wallpaper"
        style={wallpaper ? { backgroundImage: `url("${wallpaper}")` } : undefined}
      />
      <div className="shell">
        <aside className="sidebar">
          <div className="brand" title="Deepslate Client">
            {brandIcon && <img className="brand-icon" src={brandIcon} alt="" />}
          </div>

          <nav className="side-nav">
            <button
              type="button"
              className={page === 'play' ? 'active' : ''}
              onClick={() => setPage('play')}
            >
              <IconHome />
              Home
            </button>
            <button
              type="button"
              className={page === 'mods' ? 'active' : ''}
              onClick={() => {
                if (!canMods) {
                  setStatus('Mods need a Microsoft account.')
                  setAccountOpen(true)
                  return
                }
                setPage('mods')
              }}
            >
              <IconMods />
              Mods
            </button>
            <button
              type="button"
              className={page === 'settings' ? 'active' : ''}
              onClick={() => setPage('settings')}
            >
              <IconSettings />
              Settings
            </button>
          </nav>

          <div className="side-foot" ref={footRef}>
            <button
              type="button"
              className="profile"
              title={`${displayName} · ${modeLabel}`}
              onClick={() => setAccountOpen((v) => !v)}
            >
              <span className="avatar">
                {skinUrl ? <img src={skinUrl} alt="" /> : initial}
              </span>
            </button>
            {accountOpen && (
              <div className="account-menu">
                <div className="menu-label">{displayName}</div>
                <button
                  type="button"
                  onClick={() =>
                    void (async () => {
                      try {
                        const acc = await window.deepslate.loginMicrosoft()
                        setAccount(acc)
                        await persistMode('microsoft')
                        setUsername(acc.name)
                      } catch (err) {
                        setStatus(err instanceof Error ? err.message : String(err))
                      }
                    })()
                  }
                >
                  <IconMs />
                  Microsoft login
                </button>
                <button type="button" onClick={() => void persistMode('cracked')}>
                  <IconUser />
                  Offline
                </button>
                <button type="button" onClick={() => void persistMode('guest')}>
                  <IconGuest />
                  Guest
                </button>
                {account && (
                  <button
                    type="button"
                    className="danger"
                    onClick={() => {
                      void window.deepslate.logoutMicrosoft()
                      setAccount(null)
                      void persistMode('cracked')
                    }}
                  >
                    <IconLogout />
                    Sign out
                  </button>
                )}
              </div>
            )}
          </div>
        </aside>

        <main className="content">
          {page === 'play' && (
            <div className="page home" key="play">
              <section className="hero">
                <h1 className="brand-mark">
                  Deepslate
                  <em>Client</em>
                </h1>
                <p className="hero-sub">
                  Welcome back, <strong>{displayName}</strong>
                  {mode === 'microsoft'
                    ? ' — signed in and ready to launch.'
                    : mode === 'guest'
                      ? ' — Fabric guest session.'
                      : ' — offline session.'}
                </p>

                {mode === 'cracked' && (
                  <div className="username-inline">
                    <label htmlFor="username">Username</label>
                    <input
                      id="username"
                      className="field"
                      type="text"
                      value={username}
                      disabled={busy}
                      onChange={(e) => setUsername(e.target.value)}
                    />
                  </div>
                )}

                <div className="launch-stack">
                  <button
                    type="button"
                    className="version-chip"
                    disabled={busy}
                    onClick={() => void openVersionMenu()}
                  >
                    <span className="chip-ico">
                      <IconVersion />
                    </span>
                    <span className="chip-meta">
                      <span>Version</span>
                      <strong>{versionLabel}</strong>
                    </span>
                    <span className="chip-action">Change</span>
                  </button>

                  <button
                    type="button"
                    className={`play-btn${busy ? ' launching' : ''}`}
                    disabled={busy}
                    onClick={() => void onPlay()}
                  >
                    {busy && (
                      <span
                        className="play-btn-fill"
                        style={{ width: `${Math.round(progress * 100)}%` }}
                      />
                    )}
                    <span className="play-btn-label">
                      {!busy && <IconPlay />}
                      {busy ? status || 'Launching…' : 'Play'}
                    </span>
                  </button>
                </div>

                <div className="quick-bar">
                  <span className="pill">
                    <IconRam />
                    RAM <strong>{ram} GB</strong>
                  </span>
                  <span className="pill">
                    Account <strong>{modeLabel}</strong>
                  </span>
                </div>
              </section>

              {canServers && (
                <section className="servers-dock">
                  <div className="dock-head">
                    <h2>Servers</h2>
                    <span>Save &amp; quick join</span>
                  </div>
                  <div className="server-form">
                    <input
                      placeholder="Name"
                      value={serverName}
                      onChange={(e) => setServerName(e.target.value)}
                    />
                    <input
                      placeholder="Address"
                      value={serverAddress}
                      onChange={(e) => setServerAddress(e.target.value)}
                    />
                    <input
                      placeholder="Port"
                      value={serverPort}
                      onChange={(e) => setServerPort(e.target.value)}
                    />
                    <button type="button" className="ghost-btn accent" onClick={() => void saveServer()}>
                      Save
                    </button>
                  </div>
                  <div className="chips">
                    {servers.map((s, i) => (
                      <div className="chip" key={`${s.address}:${s.port}`}>
                        <button
                          type="button"
                          onClick={() => {
                            setPendingServer(s)
                            void onPlay(s)
                          }}
                        >
                          {s.name}
                        </button>
                        <button
                          type="button"
                          className="remove"
                          aria-label={`Remove ${s.name}`}
                          onClick={() => {
                            const next = servers.filter((_, idx) => idx !== i)
                            void window.deepslate.saveServers(next).then(setServers)
                          }}
                        >
                          ×
                        </button>
                      </div>
                    ))}
                    {!servers.length && (
                      <span className="empty-hint">No saved servers yet — add one above.</span>
                    )}
                  </div>
                </section>
              )}
            </div>
          )}

          {page === 'mods' && (
            <section className="page mods-page" key="mods">
              <header className="mods-head">
                <div>
                  <h1>Mods</h1>
                  <p>{versionLabel}</p>
                </div>
                <button
                  type="button"
                  className="ghost-btn"
                  onClick={() =>
                    void (async () => {
                      const [loader, mc] = await window.deepslate.parseVersion(version)
                      const gameVersion =
                        mc === 'latest' ? await window.deepslate.latestRelease() : mc
                      await window.deepslate.openModsFolder(loader, gameVersion)
                    })()
                  }
                >
                  Open folder
                </button>
              </header>
              <div className="mods-toolbar">
                <div className="seg">
                  <button
                    type="button"
                    className={modSource === 'modrinth' ? 'active' : ''}
                    onClick={() => setModSource('modrinth')}
                  >
                    Modrinth
                  </button>
                  <button
                    type="button"
                    className={modSource === 'curseforge' ? 'active' : ''}
                    onClick={() => setModSource('curseforge')}
                  >
                    CurseForge
                  </button>
                </div>
                <input
                  className="search"
                  type="search"
                  placeholder="Search mods…"
                  value={modQuery}
                  onChange={(e) => setModQuery(e.target.value)}
                  onKeyDown={(e) => e.key === 'Enter' && void runModSearch()}
                />
                <button type="button" className="ghost-btn accent" onClick={() => void runModSearch()}>
                  Search
                </button>
              </div>
              <div className="status-line">{modStatus}</div>
              <div className="mod-list">
                {modHits.map((hit) => (
                  <div className="mod-row" key={`${hit.source}:${hit.projectId}`}>
                    {hit.iconUrl ? <img src={hit.iconUrl} alt="" /> : <div className="mod-ph" />}
                    <div>
                      <h3>{hit.title}</h3>
                      <p>
                        {hit.author} · {hit.downloads.toLocaleString()} downloads
                      </p>
                    </div>
                    <button
                      type="button"
                      className="ghost-btn accent"
                      onClick={() =>
                        void (async () => {
                          const [, mc] = await window.deepslate.parseVersion(version)
                          const gameVersion =
                            mc === 'latest' ? await window.deepslate.latestRelease() : mc
                          const [loader] = await window.deepslate.parseVersion(version)
                          setModStatus(`Installing ${hit.title}…`)
                          try {
                            const path = await window.deepslate.installMod(
                              hit,
                              gameVersion,
                              loader === 'vanilla' ? 'fabric' : loader
                            )
                            setModStatus(`Installed ${path}`)
                          } catch (err) {
                            setModStatus(err instanceof Error ? err.message : String(err))
                          }
                        })()
                      }
                    >
                      Install
                    </button>
                  </div>
                ))}
              </div>
            </section>
          )}

          {page === 'settings' && (
            <div className="page settings-page" key="settings">
              <header className="page-head">
                <h1>Settings</h1>
                <p>Tune memory, launcher behavior, and optional compatibility packs.</p>
              </header>
              <div className="settings-grid">
                <div className="setting-card">
                  <div className="setting-top">
                    <label>Allocated memory</label>
                    <span>{ram} GB</span>
                  </div>
                  <input
                    type="range"
                    min={1}
                    max={16}
                    value={ram}
                    disabled={busy}
                    onChange={(e) => setRam(Number(e.target.value))}
                  />
                </div>

                {mode === 'cracked' && (
                  <div className="setting-card">
                    <div className="setting-copy">
                      <h3>Offline username</h3>
                      <p>Shown in singleplayer and on cracked servers.</p>
                    </div>
                    <input
                      className="field"
                      type="text"
                      value={username}
                      disabled={busy}
                      onChange={(e) => setUsername(e.target.value)}
                    />
                  </div>
                )}

                <div className="setting-card row">
                  <div className="setting-copy">
                    <h3>Keep launcher open</h3>
                    <p>Stay in Deepslate after Minecraft starts.</p>
                  </div>
                  <label className="switch">
                    <input
                      type="checkbox"
                      checked={keepOpen}
                      disabled={busy}
                      onChange={(e) => {
                        setKeepOpen(e.target.checked)
                        void window.deepslate.saveConfig({ keep_launcher_open: e.target.checked })
                      }}
                    />
                    <span />
                  </label>
                </div>

                <div className="setting-card row">
                  <div className="setting-copy">
                    <h3>Legacy4J</h3>
                    <p>Console-edition style via Legacy4J. Disables some Deepslate UI polish.</p>
                  </div>
                  <label className="switch">
                    <input
                      type="checkbox"
                      checked={legacy4j}
                      disabled={busy}
                      onChange={(e) => {
                        setLegacy4j(e.target.checked)
                        void window.deepslate.saveConfig({ legacy4j_enabled: e.target.checked })
                      }}
                    />
                    <span />
                  </label>
                </div>
              </div>
            </div>
          )}

          {status && !busy && (
            <div className="toast">
              <div className="toast-inner">{status}</div>
            </div>
          )}
        </main>
      </div>

      {versionOpen && (
        <div className="modal-back" onClick={() => setVersionOpen(false)}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <header>
              <strong>Choose version</strong>
              <button type="button" className="ghost-btn" onClick={() => setVersionOpen(false)}>
                Close
              </button>
            </header>
            {step === 'major' && (
              <>
                <button
                  type="button"
                  className="family"
                  onClick={() => {
                    setPickMc('latest')
                    setStep('loader')
                  }}
                >
                  <div className="art">
                    <strong>Latest Release</strong>
                    <span>Always up to date</span>
                  </div>
                </button>
                {groups.map((g) => (
                  <button
                    type="button"
                    key={g.family}
                    className="family"
                    onClick={() => {
                      setFamily(g)
                      setStep('patch')
                    }}
                  >
                    <div
                      className="art"
                      style={g.artUrl ? { backgroundImage: `url("${g.artUrl}")` } : undefined}
                    >
                      <strong>{g.title}</strong>
                      <span>
                        {g.versions.length} versions · click to choose
                      </span>
                    </div>
                  </button>
                ))}
              </>
            )}
            {step === 'patch' && family && (
              <>
                <button type="button" className="ghost-btn" onClick={() => setStep('major')}>
                  ← All versions
                </button>
                <div className="patch-grid" style={{ marginTop: 10 }}>
                  {family.versions.map((vid) => (
                    <button
                      type="button"
                      key={vid}
                      onClick={() => {
                        setPickMc(vid)
                        setStep('loader')
                      }}
                    >
                      {vid}
                    </button>
                  ))}
                </div>
              </>
            )}
            {step === 'loader' && (
              <div className="loader-choice">
                <button
                  type="button"
                  className="ghost-btn"
                  onClick={() => setStep(family ? 'patch' : 'major')}
                >
                  ← Back
                </button>
                {!guestFabricOnly && mode !== 'guest' && (
                  <button type="button" onClick={() => void selectVersion('vanilla', pickMc)}>
                    Vanilla
                    <div className="sub">Official Minecraft</div>
                  </button>
                )}
                <button
                  type="button"
                  disabled={mode === 'cracked'}
                  onClick={() => void selectVersion('fabric', pickMc)}
                >
                  Fabric
                  <div className="sub">
                    {mode === 'cracked'
                      ? 'Microsoft account required'
                      : mode === 'guest'
                        ? 'Guest lock · singleplayer only'
                        : 'Mods supported'}
                  </div>
                </button>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  )
}
