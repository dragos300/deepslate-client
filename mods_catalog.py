"""Mod browser helpers — Modrinth (default) and CurseForge (optional API key)."""

from __future__ import annotations

import json
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Literal

import core

ModSource = Literal["modrinth", "curseforge"]
USER_AGENT = "DeepslateLauncher/1.0 (personal launcher; contact: local)"
MODRINTH_API = "https://api.modrinth.com/v2"
CURSEFORGE_API = "https://api.curseforge.com/v1"
# CurseForge Minecraft game id / Mods class
CF_GAME_ID = 432
CF_CLASS_MODS = 6
CF_LOADER = {"forge": 1, "fabric": 4, "quilt": 5, "neoforge": 6}


@dataclass
class ModHit:
    source: ModSource
    project_id: str
    slug: str
    title: str
    description: str
    downloads: int
    icon_url: str | None
    author: str
    url: str


@dataclass
class ModFile:
    source: ModSource
    project_id: str
    version_id: str
    name: str
    filename: str
    download_url: str
    game_versions: list[str]
    loaders: list[str]


def mods_directory(config: dict[str, Any] | None = None) -> Path:
    path = Path(core.minecraft_directory(config)) / "mods"
    path.mkdir(parents=True, exist_ok=True)
    return path


def _http_json(url: str, *, headers: dict[str, str] | None = None, timeout: float = 30) -> Any:
    req_headers = {"User-Agent": USER_AGENT, "Accept": "application/json"}
    if headers:
        req_headers.update(headers)
    req = urllib.request.Request(url, headers=req_headers)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        body = exc.read().decode("utf-8", errors="replace")[:300]
        raise RuntimeError(f"HTTP {exc.code} from {url}: {body}") from exc
    except urllib.error.URLError as exc:
        raise RuntimeError(f"Network error: {exc.reason}") from exc


def _download_file(url: str, dest: Path, *, headers: dict[str, str] | None = None) -> Path:
    dest.parent.mkdir(parents=True, exist_ok=True)
    req_headers = {"User-Agent": USER_AGENT}
    if headers:
        req_headers.update(headers)
    req = urllib.request.Request(url, headers=req_headers)
    with urllib.request.urlopen(req, timeout=120) as resp:
        dest.write_bytes(resp.read())
    return dest


def normalize_loader(loader: str | None) -> str:
    raw = (loader or "fabric").strip().lower()
    if raw in ("vanilla", "latest", ""):
        return "fabric"
    if raw.startswith("fabric"):
        return "fabric"
    if raw.startswith("forge") and "neo" not in raw:
        return "forge"
    if "neo" in raw:
        return "neoforge"
    if raw.startswith("quilt"):
        return "quilt"
    return raw


# ── Modrinth ────────────────────────────────────────────────────────────────


def search_modrinth(
    query: str,
    *,
    game_version: str,
    loader: str = "fabric",
    limit: int = 20,
    offset: int = 0,
) -> list[ModHit]:
    loader = normalize_loader(loader)
    facets = [
        ["project_type:mod"],
        [f"categories:{loader}"],
        [f"versions:{game_version}"],
    ]
    params = {
        "query": query.strip(),
        "limit": str(max(1, min(100, limit))),
        "offset": str(max(0, offset)),
        "index": "relevance" if query.strip() else "downloads",
        "facets": json.dumps(facets),
    }
    url = f"{MODRINTH_API}/search?{urllib.parse.urlencode(params)}"
    data = _http_json(url)
    hits: list[ModHit] = []
    for h in data.get("hits") or []:
        slug = str(h.get("slug") or h.get("project_id") or "")
        hits.append(
            ModHit(
                source="modrinth",
                project_id=str(h.get("project_id") or ""),
                slug=slug,
                title=str(h.get("title") or slug),
                description=str(h.get("description") or ""),
                downloads=int(h.get("downloads") or 0),
                icon_url=(str(h["icon_url"]) if h.get("icon_url") else None),
                author=str(h.get("author") or ""),
                url=f"https://modrinth.com/mod/{slug}",
            )
        )
    return hits


def resolve_modrinth_file(project: str, *, game_version: str, loader: str = "fabric") -> ModFile:
    loader = normalize_loader(loader)
    params = {
        "game_versions": json.dumps([game_version]),
        "loaders": json.dumps([loader]),
    }
    url = f"{MODRINTH_API}/project/{urllib.parse.quote(project)}/version?{urllib.parse.urlencode(params)}"
    versions = _http_json(url)
    if not versions:
        raise RuntimeError(f"No Modrinth file for {project} on {game_version}/{loader}")
    ver = versions[0]
    files = ver.get("files") or []
    primary = next((f for f in files if f.get("primary")), files[0] if files else None)
    if not primary or not primary.get("url"):
        raise RuntimeError(f"No downloadable file for {project}")
    return ModFile(
        source="modrinth",
        project_id=str(ver.get("project_id") or project),
        version_id=str(ver.get("id") or ""),
        name=str(ver.get("name") or ver.get("version_number") or project),
        filename=str(primary.get("filename") or f"{project}.jar"),
        download_url=str(primary["url"]),
        game_versions=list(ver.get("game_versions") or []),
        loaders=list(ver.get("loaders") or []),
    )


# ── CurseForge ───────────────────────────────────────────────────────────────


def curseforge_api_key(config: dict[str, Any] | None = None) -> str:
    config = config or core.load_config()
    return str(config.get("curseforge_api_key") or "").strip()


def curseforge_configured(config: dict[str, Any] | None = None) -> bool:
    return bool(curseforge_api_key(config))


def search_curseforge(
    query: str,
    *,
    game_version: str,
    loader: str = "fabric",
    limit: int = 20,
    offset: int = 0,
    api_key: str | None = None,
) -> list[ModHit]:
    key = (api_key or curseforge_api_key()).strip()
    if not key:
        raise RuntimeError(
            "CurseForge needs an API key. Add curseforge_api_key to config.json "
            "(free at https://console.curseforge.com/)."
        )
    loader = normalize_loader(loader)
    params: dict[str, str] = {
        "gameId": str(CF_GAME_ID),
        "classId": str(CF_CLASS_MODS),
        "searchFilter": query.strip(),
        "gameVersion": game_version,
        "pageSize": str(max(1, min(50, limit))),
        "index": str(max(0, offset)),
        "sortField": "2",  # Popularity
        "sortOrder": "desc",
    }
    loader_id = CF_LOADER.get(loader)
    if loader_id:
        params["modLoaderType"] = str(loader_id)
    url = f"{CURSEFORGE_API}/mods/search?{urllib.parse.urlencode(params)}"
    data = _http_json(url, headers={"x-api-key": key})
    hits: list[ModHit] = []
    for h in data.get("data") or []:
        slug = str(h.get("slug") or h.get("id") or "")
        authors = h.get("authors") or []
        author = str(authors[0].get("name") if authors else "")
        logo = (h.get("logo") or {}).get("thumbnailUrl") or (h.get("logo") or {}).get("url")
        hits.append(
            ModHit(
                source="curseforge",
                project_id=str(h.get("id") or ""),
                slug=slug,
                title=str(h.get("name") or slug),
                description=str(h.get("summary") or ""),
                downloads=int(h.get("downloadCount") or 0),
                icon_url=str(logo) if logo else None,
                author=author,
                url=f"https://www.curseforge.com/minecraft/mc-mods/{slug}",
            )
        )
    return hits


def resolve_curseforge_file(
    project_id: str,
    *,
    game_version: str,
    loader: str = "fabric",
    api_key: str | None = None,
) -> ModFile:
    key = (api_key or curseforge_api_key()).strip()
    if not key:
        raise RuntimeError("CurseForge API key missing.")
    loader = normalize_loader(loader)
    params: dict[str, str] = {
        "gameVersion": game_version,
        "pageSize": "20",
    }
    loader_id = CF_LOADER.get(loader)
    if loader_id:
        params["modLoaderType"] = str(loader_id)
    url = (
        f"{CURSEFORGE_API}/mods/{urllib.parse.quote(str(project_id))}/files"
        f"?{urllib.parse.urlencode(params)}"
    )
    data = _http_json(url, headers={"x-api-key": key})
    files = data.get("data") or []
    if not files:
        raise RuntimeError(f"No CurseForge file for {project_id} on {game_version}/{loader}")
    f = files[0]
    file_id = f.get("id")
    download_url = f.get("downloadUrl")
    if not download_url and file_id is not None:
        meta = _http_json(
            f"{CURSEFORGE_API}/mods/{urllib.parse.quote(str(project_id))}/files/{file_id}/download-url",
            headers={"x-api-key": key},
        )
        download_url = meta.get("data")
    if not download_url:
        raise RuntimeError(f"No download URL for CurseForge file {file_id}")
    return ModFile(
        source="curseforge",
        project_id=str(project_id),
        version_id=str(file_id or ""),
        name=str(f.get("displayName") or f.get("fileName") or project_id),
        filename=str(f.get("fileName") or f"{project_id}.jar"),
        download_url=str(download_url),
        game_versions=list(f.get("gameVersions") or []),
        loaders=[loader],
    )


# ── Unified ──────────────────────────────────────────────────────────────────


def search_mods(
    query: str,
    *,
    source: ModSource,
    game_version: str,
    loader: str = "fabric",
    limit: int = 20,
    offset: int = 0,
    config: dict[str, Any] | None = None,
) -> list[ModHit]:
    if source == "curseforge":
        return search_curseforge(
            query,
            game_version=game_version,
            loader=loader,
            limit=limit,
            offset=offset,
            api_key=curseforge_api_key(config),
        )
    return search_modrinth(
        query, game_version=game_version, loader=loader, limit=limit, offset=offset
    )


def resolve_mod_file(
    hit: ModHit,
    *,
    game_version: str,
    loader: str = "fabric",
    config: dict[str, Any] | None = None,
) -> ModFile:
    if hit.source == "curseforge":
        return resolve_curseforge_file(
            hit.project_id,
            game_version=game_version,
            loader=loader,
            api_key=curseforge_api_key(config),
        )
    return resolve_modrinth_file(hit.slug or hit.project_id, game_version=game_version, loader=loader)


def install_mod(
    hit: ModHit,
    *,
    game_version: str,
    loader: str = "fabric",
    config: dict[str, Any] | None = None,
    directory: Path | None = None,
) -> Path:
    """Download the best matching mod jar into the instance mods folder."""
    config = config or core.load_config()
    mods_dir = directory or mods_directory(config)
    mod_file = resolve_mod_file(hit, game_version=game_version, loader=loader, config=config)
    dest = mods_dir / mod_file.filename
    headers: dict[str, str] = {}
    if hit.source == "curseforge":
        key = curseforge_api_key(config)
        if key:
            headers["x-api-key"] = key
    _download_file(mod_file.download_url, dest, headers=headers or None)
    return dest


def format_downloads(n: int) -> str:
    if n >= 1_000_000:
        return f"{n / 1_000_000:.1f}M"
    if n >= 1_000:
        return f"{n / 1_000:.1f}K"
    return str(n)
