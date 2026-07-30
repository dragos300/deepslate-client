"""Build deepslate-guest-lock for Fabric-supported MC versions (1.20.1–1.21.x).

Writes jars to ../assets/guest-lock/<mc_version>/deepslate-guest-lock.jar
and a manifest at ../assets/guest-lock/manifest.json.
"""

from __future__ import annotations

import json
import re
import shutil
import subprocess
import sys
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent
LAUNCHER_ROOT = ROOT.parent
OUT_ROOT = LAUNCHER_ROOT / "assets" / "guest-lock"
PROPS = ROOT / "gradle.properties"
MIN_VERSION = (1, 20, 1)
LOADER_META = "https://meta.fabricmc.net/v2/versions/loader/{mc}"


def parse_mc(version: str) -> tuple[int, ...]:
    parts = []
    for bit in version.split("."):
        m = re.match(r"(\d+)", bit)
        if not m:
            break
        parts.append(int(m.group(1)))
    return tuple(parts)


def version_ok(version: str) -> bool:
    parsed = parse_mc(version)
    if len(parsed) < 2:
        return False
    if parsed[0] != 1 or parsed[1] < 20 or parsed[1] > 21:
        return False
    padded = (parsed + (0, 0, 0))[:3]
    return padded >= MIN_VERSION


def fabric_game_versions() -> list[str]:
    try:
        sys.path.insert(0, str(LAUNCHER_ROOT))
        venv_site = LAUNCHER_ROOT / ".venv" / "Lib" / "site-packages"
        if venv_site.is_dir():
            sys.path.insert(0, str(venv_site))
        import minecraft_launcher_lib

        fabric = minecraft_launcher_lib.mod_loader.get_mod_loader("fabric")
        versions = list(fabric.get_minecraft_versions(True))
    except Exception:
        url = "https://meta.fabricmc.net/v2/versions/game"
        with urllib.request.urlopen(url, timeout=60) as resp:
            data = json.load(resp)
        versions = [row["version"] for row in data if row.get("stable")]
    return [v for v in versions if version_ok(v)]


def latest_loader(mc: str) -> str:
    url = LOADER_META.format(mc=mc)
    with urllib.request.urlopen(url, timeout=60) as resp:
        data = json.load(resp)
    for row in data:
        loader = row.get("loader") or {}
        if loader.get("stable"):
            return str(loader["version"])
    if not data:
        raise RuntimeError(f"No Fabric loader for {mc}")
    return str(data[0]["loader"]["version"])


def write_props(mc: str, loader: str) -> None:
    text = (
        "org.gradle.jvmargs=-Xmx2G\n"
        "org.gradle.parallel=true\n"
        "org.gradle.configuration-cache=false\n"
        "\n"
        f"minecraft_version={mc}\n"
        f"loader_version={loader}\n"
        "loom_version=1.17-SNAPSHOT\n"
        "\n"
        "mod_version=1.0.0\n"
        "maven_group=com.deepslate\n"
        "archives_base_name=deepslate-guest-lock\n"
    )
    PROPS.write_text(text, encoding="utf-8")


def gradle_cmd() -> list[str]:
    if sys.platform == "win32":
        wrapper = ROOT / "gradlew.bat"
        if wrapper.is_file():
            return [str(wrapper)]
    wrapper = ROOT / "gradlew"
    if wrapper.is_file():
        return [str(wrapper)]
    return ["gradle"]


def build_one(mc: str) -> Path:
    loader = latest_loader(mc)
    print(f"==> Building guest-lock for Minecraft {mc} (loader {loader})", flush=True)
    write_props(mc, loader)
    cmd = gradle_cmd() + ["--no-daemon", "clean", "remapJar"]
    subprocess.run(cmd, cwd=ROOT, check=True)

    libs = ROOT / "build" / "libs"
    jars = sorted(libs.glob("deepslate-guest-lock-*.jar"))
    jars = [j for j in jars if "sources" not in j.name and "dev" not in j.name]
    if not jars:
        raise RuntimeError(f"No output jar for {mc}")
    jar = jars[-1]
    if jar.stat().st_size < 1000:
        raise RuntimeError(f"Output jar too small for {mc}: {jar} ({jar.stat().st_size} bytes)")
    names = zipfile.ZipFile(jar).namelist()
    if "com/deepslate/guestlock/GuestLockClient.class" not in names:
        raise RuntimeError(f"Remapped jar missing classes for {mc}")
    dest_dir = OUT_ROOT / mc
    dest_dir.mkdir(parents=True, exist_ok=True)
    dest = dest_dir / "deepslate-guest-lock.jar"
    shutil.copy2(jar, dest)
    print(f"    -> {dest} ({dest.stat().st_size} bytes)", flush=True)
    return dest


def main() -> int:
    only = sys.argv[1:]
    versions = only if only else fabric_game_versions()
    versions = sorted(versions, key=parse_mc, reverse=True)
    OUT_ROOT.mkdir(parents=True, exist_ok=True)
    built: list[str] = []
    failed: list[str] = []
    for mc in versions:
        try:
            build_one(mc)
            built.append(mc)
        except Exception as exc:  # noqa: BLE001
            print(f"FAILED {mc}: {exc}", file=sys.stderr, flush=True)
            failed.append(mc)

    manifest = {
        "mod_id": "deepslate_guest_lock",
        "jvm_flag": "-Ddeepslate.guest=true",
        "versions": sorted(built, key=parse_mc, reverse=True),
        "failed": failed,
    }
    (OUT_ROOT / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print(f"Built {len(built)} version(s); failed {len(failed)}", flush=True)
    return 1 if not built else 0


if __name__ == "__main__":
    raise SystemExit(main())
