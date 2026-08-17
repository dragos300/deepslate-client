"""Extract vanilla HUD sprites and darken slightly for PvP readability."""
from __future__ import annotations

import io
import zipfile
from pathlib import Path

from PIL import Image

JAR = Path(r"C:\Users\Dragos\AppData\Roaming\my-mc-launcher\versions\1.21.11\1.21.11.jar")
OUT = Path(
    r"C:\Users\Dragos\.cursor\projects\minecraft thing\deepslate-ui\src\main\resources"
    r"\resourcepacks\deepslate_style\assets\minecraft\textures\gui\sprites\hud"
)

# Multiplicative brightness — keep hue, just a bit darker than vanilla
FACTOR = 0.78
PREFIX = "assets/minecraft/textures/gui/sprites/hud/"


def darken(img: Image.Image, factor: float) -> Image.Image:
    img = img.convert("RGBA")
    px = img.load()
    w, h = img.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            px[x, y] = (
                max(0, min(255, int(r * factor))),
                max(0, min(255, int(g * factor))),
                max(0, min(255, int(b * factor))),
                a,
            )
    return img


def wanted(name: str) -> bool:
    rel = name[len(PREFIX) :]
    if rel.startswith("heart/"):
        return True
    base = rel.split("/")[-1]
    return base.startswith("food_") or base.startswith("armor_")


def main() -> None:
    if not JAR.is_file():
        raise SystemExit(f"Missing jar: {JAR}")
    count = 0
    with zipfile.ZipFile(JAR) as zf:
        for info in zf.infolist():
            name = info.filename.replace("\\", "/")
            if not name.startswith(PREFIX) or not name.endswith(".png"):
                continue
            if not wanted(name):
                continue
            rel = name[len(PREFIX) :]
            dest = OUT / rel
            dest.parent.mkdir(parents=True, exist_ok=True)
            raw = zf.read(info)
            img = Image.open(io.BytesIO(raw))
            darkened = darken(img, FACTOR)
            darkened.save(dest, format="PNG")
            count += 1
            print(f"wrote {rel} {darkened.size}")
    print(f"done {count} sprites @ factor={FACTOR}")


if __name__ == "__main__":
    main()
