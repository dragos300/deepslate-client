"""Extract vanilla HUD/widget sprites and recolor them to a Lunar-like dark space palette.
Logo uses assets/brand_deepslate_logo.png; UI font stays vanilla Minecraft.
"""

from __future__ import annotations

import io
import json
import shutil
import urllib.request
import zipfile
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent
ASSETS = ROOT / "src" / "main" / "resources" / "assets"
PACK = ROOT / "src" / "main" / "resources" / "resourcepacks" / "deepslate_style" / "assets"
MC = PACK / "minecraft"
VANILLA_JAR = Path.home() / "AppData" / "Roaming" / ".minecraft" / "versions" / "1.21.11" / "1.21.11.jar"
BLOCK = ROOT.parent / "assets" / "deepslate_block.png"

# Lunar-inspired cool grey ramp (dark → light). Original palette, not copied assets.
RAMP = [
    (19, 20, 26),    # space-1
    (26, 27, 33),    # space-2
    (34, 35, 44),    # space-3
    (40, 42, 52),    # space-4
    (47, 49, 59),    # space-5
    (56, 58, 68),    # space-6
    (70, 71, 82),    # space-7
    (94, 96, 108),   # space-8
]


def lunar_ramp() -> list[tuple[int, int, int]]:
    return list(RAMP)


def recolor(im: Image.Image, ramp: list[tuple[int, int, int]]) -> Image.Image:
    im = im.convert("RGBA")
    px = im.load()
    assert px is not None
    w, h = im.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
            # Crush toward dark translucent client chrome
            lum = pow(lum, 1.2) * 0.78
            idx = min(len(ramp) - 1, int(lum * (len(ramp) - 1) + 0.5))
            nr, ng, nb = ramp[idx]
            # Slight cool blue bias on midtones
            if idx >= 3:
                nb = min(255, nb + 4)
            px[x, y] = (nr, ng, nb, a)
    return im


def extract_and_recolor(ramp: list[tuple[int, int, int]]) -> None:
    if not VANILLA_JAR.is_file():
        raise SystemExit(f"Missing vanilla jar: {VANILLA_JAR}")

    prefixes = (
        "assets/minecraft/textures/gui/sprites/hud/",
        "assets/minecraft/textures/gui/sprites/widget/button",
    )
    hud_out = MC / "textures" / "gui" / "sprites" / "hud"
    widget_out = MC / "textures" / "gui" / "sprites" / "widget"
    if hud_out.exists():
        shutil.rmtree(hud_out)
    widget_out.mkdir(parents=True, exist_ok=True)

    count = 0
    with zipfile.ZipFile(VANILLA_JAR, "r") as zf:
        for name in zf.namelist():
            if not name.startswith(prefixes[0]) and not name.startswith(prefixes[1]):
                continue
            if name.endswith("/"):
                continue
            data = zf.read(name)
            rel = name.removeprefix("assets/minecraft/")
            dest = MC / rel
            dest.parent.mkdir(parents=True, exist_ok=True)
            if name.endswith(".mcmeta"):
                dest.write_bytes(data)
                continue
            if not name.endswith(".png"):
                dest.write_bytes(data)
                continue
            im = Image.open(io.BytesIO(data))
            recolor(im, ramp).save(dest)
            count += 1
    print(f"recolored {count} sprites -> {MC}")

    pack_meta = ROOT / "src" / "main" / "resources" / "resourcepacks" / "deepslate_style" / "pack.mcmeta"
    pack_meta.parent.mkdir(parents=True, exist_ok=True)
    pack_meta.write_text(
        json.dumps(
            {"pack": {"pack_format": 75, "description": "Deepslate Client HUD"}},
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )


def install_font() -> None:
    # Vanilla Minecraft bitmap font only — remove any leftover TTF overrides from the style pack.
    font_dir = MC / "font"
    if font_dir.exists():
        shutil.rmtree(font_dir)
        print("removed style-pack font overrides (vanilla MC font)")
    title_dir = ASSETS / "deepslate_ui" / "font"
    for stale in ("dmsans.ttf", "outfit.ttf", "title.json"):
        p = title_dir / stale
        if p.exists():
            p.unlink()


def refresh_logo() -> None:
    logo_dir = ASSETS / "deepslate_ui" / "textures" / "gui"
    sprite_dir = logo_dir / "sprites"
    logo_dir.mkdir(parents=True, exist_ok=True)
    sprite_dir.mkdir(parents=True, exist_ok=True)

    # Prefer the designed wordmark if present.
    brand = ROOT.parent / "assets" / "brand_deepslate_logo.png"
    if brand.is_file():
        shutil.copy2(brand, logo_dir / "logo.png")
        shutil.copy2(brand, sprite_dir / "logo.png")
        print(f"logo copied from brand_deepslate_logo.png ({brand.stat().st_size} bytes)")
        return

    src = Image.open(BLOCK).convert("RGBA") if BLOCK.is_file() else None
    if src is None:
        return
    px = src.load()
    assert px is not None
    w, h = src.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a > 0 and r < 18 and g < 18 and b < 18:
                px[x, y] = (0, 0, 0, 0)
    canvas = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
    block = src.resize((400, 400), Image.Resampling.LANCZOS)
    canvas.paste(block, ((512 - 400) // 2, (512 - 400) // 2), block)
    canvas.save(logo_dir / "logo.png")
    canvas.save(sprite_dir / "logo.png")
    print("logo refreshed from deepslate block")


def main() -> None:
    ramp = lunar_ramp()
    print("ramp", ramp)
    extract_and_recolor(ramp)
    install_font()
    refresh_logo()


if __name__ == "__main__":
    main()
