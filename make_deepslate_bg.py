"""Build launcher background from real Minecraft deepslate textures."""

from __future__ import annotations

import random
from pathlib import Path

from PIL import Image, ImageDraw, ImageEnhance, ImageFilter

ROOT = Path(__file__).resolve().parent
TEX = ROOT / "assets" / "_mc_textures"
OUT = ROOT / "assets" / "backgrounds" / "bg_deepslate_ores.png"

# Side-face deepslate only (same layered orientation as the ore textures).
# Do not use deepslate_top / cobbled / tuff — different orientation or look.
FILLERS = ["deepslate.png"]

ORES = [
    ("deepslate_diamond_ore.png", (70, 220, 230)),
    ("deepslate_emerald_ore.png", (40, 200, 90)),
    ("deepslate_redstone_ore.png", (230, 50, 40)),
    ("deepslate_lapis_ore.png", (50, 90, 220)),
    ("deepslate_gold_ore.png", (240, 200, 60)),
    ("deepslate_copper_ore.png", (220, 120, 70)),
    ("deepslate_iron_ore.png", (200, 180, 160)),
    ("deepslate_coal_ore.png", (90, 90, 95)),
]


def _load(name: str, size: int) -> Image.Image:
    img = Image.open(TEX / name).convert("RGBA")
    # Minecraft textures are 16x16 — nearest-neighbor keeps the real pixel look
    return img.resize((size, size), Image.Resampling.NEAREST)


def generate(width: int = 1920, height: int = 1080, block: int = 64, seed: int = 11) -> Image.Image:
    rng = random.Random(seed)
    cols = (width + block - 1) // block
    rows = (height + block - 1) // block

    fillers = [_load(n, block) for n in FILLERS if (TEX / n).is_file()]
    ores = [(_load(n, block), glow) for n, glow in ORES if (TEX / n).is_file()]
    if not ores:
        raise FileNotFoundError(f"Missing ore textures in {TEX}. Extract from a Minecraft jar first.")
    if not fillers:
        raise FileNotFoundError(f"Missing deepslate.png in {TEX} (side face, same orientation as ores).")

    wall = Image.new("RGBA", (cols * block, rows * block), (30, 30, 35, 255))
    ore_chance = 0.28

    for row in range(rows):
        for col in range(cols):
            x, y = col * block, row * block
            if rng.random() < ore_chance:
                tile, _glow_c = ores[rng.randrange(len(ores))]
                tile = tile.copy()
                if rng.random() < 0.35:
                    tile = ImageEnhance.Brightness(tile).enhance(rng.uniform(0.88, 1.1))
                wall.paste(tile, (x, y), tile)
                # Occasional vein clusters of the same ore
                if rng.random() < 0.4:
                    for _ in range(rng.randint(1, 3)):
                        nc = min(cols - 1, max(0, col + rng.choice([-1, 0, 1])))
                        nr = min(rows - 1, max(0, row + rng.choice([-1, 0, 1])))
                        if (nc, nr) == (col, row):
                            continue
                        wall.paste(tile, (nc * block, nr * block), tile)
            else:
                tile = fillers[rng.randrange(len(fillers))].copy()
                if rng.random() < 0.4:
                    tile = ImageEnhance.Brightness(tile).enhance(rng.uniform(0.88, 1.12))
                wall.paste(tile, (x, y), tile)

    # Light vignette for UI readability
    vignette = Image.new("L", wall.size, 0)
    ImageDraw.Draw(vignette).ellipse(
        (-int(wall.width * 0.1), -int(wall.height * 0.15), int(wall.width * 1.1), int(wall.height * 1.15)),
        fill=255,
    )
    vignette = vignette.filter(ImageFilter.GaussianBlur(radius=min(wall.size) // 6))
    dark = Image.new("RGB", wall.size, (6, 6, 8))
    result = Image.composite(wall.convert("RGB"), dark, vignette)
    return result.crop((0, 0, width, height))


def main() -> None:
    OUT.parent.mkdir(parents=True, exist_ok=True)
    img = generate()
    img.save(OUT, format="PNG", optimize=True)
    print(OUT, img.size)


if __name__ == "__main__":
    main()
