#!/usr/bin/env python3
"""Download official Mojang update key art from Minecraft Wiki (not AI)."""

from __future__ import annotations

import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent
OUT = ROOT / "assets" / "updates"

# Major family -> (title, wiki filename candidates)
# Filenames from https://minecraft.wiki/w/Category:Update_promo_art
ART: dict[str, tuple[str, list[str]]] = {
    "26": (
        "2026 Drops",
        [
            "Chase_the_Skies_Key_Art.jpg",
            "Chase_the_Skies_Key_Art.png",
            "Spring_to_Life_Key_Art.jpg",
        ],
    ),
    "1.21": (
        "Tricky Trials",
        ["Tricky_Trials_Key_Art.png"],
    ),
    "1.20": (
        "Trails & Tales",
        [
            "Trails_%26_Tales_key_art.png",
            "Trails_%26_Tales_key_art.jpg",
        ],
    ),
    "1.19": (
        "The Wild",
        ["Wild_key_art.png", "WildUpdateKeyArt.jpg"],
    ),
    "1.18": (
        "Caves & Cliffs II",
        [
            "Caves_%26_Cliffs_Part_II.png",
            "Caves_%26_Cliffs_(Part_II)_Artwork.jpg",
        ],
    ),
    "1.17": (
        "Caves & Cliffs I",
        [
            "Caves_%26_Cliffs_cover_art.png",
            "Caves_%26_Cliffs_cover_art_2.png",
        ],
    ),
    "1.16": (
        "Nether Update",
        ["NetherUpdateArtwork.png", "Nether_Update.jpg"],
    ),
    "1.15": (
        "Buzzy Bees",
        ["Buzzy_Bees.png", "BuzzyBeesArtwork.jpg"],
    ),
    "1.14": (
        "Village & Pillage",
        [
            "Village_%26_Pillage_banner.png",
            "Village_%26_Pillage_art.jpg",
        ],
    ),
    "1.13": (
        "Update Aquatic",
        ["Update_Aquatic.png", "Update_Aquatic.jpg", "Update_Aquatic.jpeg"],
    ),
    "1.12": (
        "World of Color",
        ["World_of_Color_Update.png"],
    ),
    "1.11": (
        "Exploration Update",
        ["ExplorationUpdateFull.jpg", "Exploration_Update_Artwork.jpg"],
    ),
    "1.10": (
        "Frostburn Update",
        ["Frostburn_Update.png", "Frostburn_Update.jpeg"],
    ),
    "1.9": (
        "Combat Update",
        ["Combat_Update.png"],
    ),
    "1.8": (
        "Bountiful Update",
        ["Boss_Update_artwork.png"],
    ),
    "1.7": (
        "Changed the World",
        [
            "1.7.10_Banner.png",
            "Java_Edition_1.7.png",
            "Java_Edition_1.7.10.png",
            "Java_Edition_1.7.2.png",
        ],
    ),
    "1.6": (
        "Horse Update",
        ["Horse_Update_image.png", "Horse_Update_Wallpaper.jpg", "Java_Edition_1.6.1.png"],
    ),
    "1.5": (
        "Redstone Update",
        ["Java_Edition_1.5.png", "Java_Edition_1.5.2.png"],
    ),
    "1.4": (
        "Pretty Scary",
        [
            "Pretty_Scary_Update_poster.png",
            "Pretty_Scary_Update_Logo.png",
            "Java_Edition_1.4.2.png",
        ],
    ),
    "1.3": (
        "Minecraft 1.3",
        ["Java_Edition_1.3.2.png", "Java_Edition_1.3.1.png"],
    ),
    "1.2": (
        "Minecraft 1.2",
        ["Java_Edition_1.2.5.png", "Java_Edition_1.2.1.png"],
    ),
    "1.1": (
        "Minecraft 1.1",
        ["Java_Edition_1.1.png"],
    ),
    "1.0": (
        "Adventure Update",
        [
            "Adventure_Update.png",
            "Java_Edition_1.0.0.png",
            "Beta_1.8.png",
            "EnderUpdate.jpg",
        ],
    ),
}

MIN_BYTES = 50_000


def _download(filename: str, dest: Path) -> bool:
    url = f"https://minecraft.wiki/wiki/Special:FilePath/{filename}"
    req = urllib.request.Request(
        url,
        headers={"User-Agent": "DeepslateLauncher/1.0 (personal launcher; update art cache)"},
    )
    try:
        with urllib.request.urlopen(req, timeout=90) as resp:
            data = resp.read()
        if len(data) < MIN_BYTES:
            print(f"SKIP {filename}: too small ({len(data)} bytes)")
            return False
        dest.write_bytes(data)
        print(f"OK {dest.name} ({len(data)} bytes) <- {filename}")
        return True
    except Exception as exc:  # noqa: BLE001
        print(f"FAIL {filename}: {exc}")
        return False


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    missing: list[str] = []
    for family, (title, candidates) in ART.items():
        ok = False
        for name in candidates:
            ext = Path(name.split("?")[0]).suffix.lower()
            if ext not in {".png", ".jpg", ".jpeg", ".webp"}:
                ext = ".jpg"
            if ext == ".jpeg":
                ext = ".jpg"
            dest = OUT / f"{family.replace('.', '_')}{ext}"
            if _download(name, dest):
                stable = OUT / f"{family.replace('.', '_')}.img"
                stable.write_bytes(dest.read_bytes())
                ok = True
                break
        if not ok:
            print(f"MISSING art for {family} ({title})")
            missing.append(family)

    credits = OUT / "CREDITS.txt"
    credits.write_text(
        "Update key art (official Mojang promotional images via Minecraft Wiki)\n"
        "\n"
        "Cached under assets/updates/ as {family}.img\n"
        "\n"
        "Refresh / download more:\n"
        "  .\\.venv\\Scripts\\python.exe fetch_update_art.py\n"
        "\n"
        f"Cached families: {', '.join(ART.keys())}\n"
        "Missing families still show clean dark version buttons without art.\n",
        encoding="utf-8",
    )
    if missing:
        raise SystemExit(f"Missing: {', '.join(missing)}")
    print("All update art cached.")


if __name__ == "__main__":
    main()
