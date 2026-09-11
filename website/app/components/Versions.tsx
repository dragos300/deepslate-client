import { CHANGELOG, type ChangelogEntry } from '../../lib/changelog'
import { RELEASES_PAGE, type ListedRelease } from '../../lib/release'

export default function Versions({ releases }: { releases: ListedRelease[] }) {
  const byTag = new Map(releases.map((release) => [release.tag, release]))

  return (
    <section className="section versions" id="versions">
      <div className="section-head">
        <h2>Versions</h2>
        <p>What shipped in each public build. The latest is highlighted.</p>
      </div>
      <ol className="version-list">
        {CHANGELOG.map((entry) => {
          const remote = byTag.get(entry.tag)
          return (
            <li className={`version-card${entry.latest ? ' latest' : ''}`} key={entry.tag}>
              <header>
                <p className="eyebrow">{entry.latest ? 'Latest' : entry.date}</p>
                <h3>{entry.title}</h3>
                <p className="version-meta">
                  <span>{entry.tag}</span>
                  {entry.latest ? null : <span>{entry.date}</span>}
                </p>
              </header>
              <p className="version-summary">{entry.summary}</p>
              <FeatureBlock title="New" items={entry.added} />
              <FeatureBlock title="Changes" items={entry.changed} />
              <a
                className="ghost-btn version-link"
                href={remote?.href ?? `${RELEASES_PAGE}/tag/${entry.tag}`}
                target="_blank"
                rel="noreferrer"
              >
                GitHub release
              </a>
            </li>
          )
        })}
      </ol>
    </section>
  )
}

function FeatureBlock({ title, items }: { title: string; items: string[] }) {
  if (!items.length) return null
  return (
    <div className="version-block">
      <h4>{title}</h4>
      <ul>
        {items.map((item) => (
          <li key={item}>{item}</li>
        ))}
      </ul>
    </div>
  )
}

export type { ChangelogEntry }
