export default function HeroVisual() {
  return (
    <div className="hero-visual" aria-hidden>
      <div className="hero-well">
        <img src="/icon.png" alt="" className="pixel" />
      </div>
      <span className="hud-chip hud-chip-a">
        <em>FPS</em>
        144
      </span>
      <span className="hud-chip hud-chip-b">
        <em>XYZ</em>
        128 12 −42
      </span>
      <span className="hud-chip hud-chip-c">
        <em>Biome</em>
        Deep Dark
      </span>
      <span className="hud-chip hud-chip-d">
        <em>Ping</em>
        18 ms
      </span>
      <div className="keys">
        <span />
        <span className="on">W</span>
        <span />
        <span>A</span>
        <span className="on">S</span>
        <span>D</span>
      </div>
    </div>
  )
}
