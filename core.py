"""Shared Minecraft launcher logic for CLI and GUI."""

from __future__ import annotations

import json
import os
import re
import shutil
import subprocess
import sys
import uuid
import webbrowser
from collections.abc import Callable
from pathlib import Path
from typing import Any, Literal

import minecraft_launcher_lib
from minecraft_launcher_lib.exceptions import (
    AccountNotOwnMinecraft,
    AzureAppNotPermitted,
    InvalidRefreshToken,
    UnsupportedVersion,
    VersionNotFound,
)

ModLoaderName = Literal["vanilla", "fabric"]
_FABRIC_INSTALLED_RE = re.compile(r"^fabric-loader-([\d.]+)-(.+)$")

APP_NAME = "my-mc-launcher"


def resource_root() -> Path:
    """Bundled read-only assets (PyInstaller _MEIPASS or source tree)."""
    if getattr(sys, "frozen", False) and hasattr(sys, "_MEIPASS"):
        return Path(sys._MEIPASS)  # type: ignore[attr-defined]
    return Path(__file__).resolve().parent


def app_root() -> Path:
    """Writable app directory (next to the .exe, or the project folder)."""
    if getattr(sys, "frozen", False):
        return Path(sys.executable).resolve().parent
    return Path(__file__).resolve().parent


ROOT = app_root()
CONFIG_PATH = ROOT / "config.json"
ACCOUNT_PATH = ROOT / "account.json"
EXAMPLE_CONFIG_PATH = resource_root() / "config.example.json"

ProgressCallback = Callable[[int, int, str], None]


def default_minecraft_dir() -> Path:
    if sys.platform == "win32":
        base = Path(os.environ.get("APPDATA", Path.home()))
        return base / APP_NAME
    if sys.platform == "darwin":
        return Path.home() / "Library" / "Application Support" / APP_NAME
    return Path.home() / f".{APP_NAME}"


def default_config() -> dict[str, Any]:
    return {
        "client_id": "",
        "client_secret": "",
        "redirect_uri": "https://login.microsoftonline.com/common/oauth2/nativeclient",
        "minecraft_directory": None,
        "jvm_arguments": ["-Xmx4G"],
        "offline_username": "Player",
        "last_version": "latest",
        "mod_loader": "vanilla",
        "curseforge_api_key": "",
        "discord_rpc": True,
        "discord_rpc_client_id": "",
        "login_mode": "cracked",
        "keep_launcher_open": True,
        "servers": [],
    }


def load_config() -> dict[str, Any]:
    cfg = default_config()
    if not CONFIG_PATH.exists():
        return cfg
    with CONFIG_PATH.open(encoding="utf-8") as f:
        loaded = json.load(f)
    cfg.update(loaded)
    return cfg


def save_config(config: dict[str, Any]) -> None:
    with CONFIG_PATH.open("w", encoding="utf-8") as f:
        json.dump(config, f, indent=2)
        f.write("\n")


def init_config() -> Path:
    """Create config.json from the example if missing. Returns the config path."""
    if CONFIG_PATH.exists():
        return CONFIG_PATH
    if EXAMPLE_CONFIG_PATH.exists():
        CONFIG_PATH.write_text(EXAMPLE_CONFIG_PATH.read_text(encoding="utf-8"), encoding="utf-8")
    else:
        save_config(default_config())
    return CONFIG_PATH


def minecraft_directory(config: dict[str, Any] | None = None) -> str:
    config = config or load_config()
    custom = config.get("minecraft_directory")
    path = Path(custom).expanduser() if custom else default_minecraft_dir()
    path.mkdir(parents=True, exist_ok=True)
    return str(path)


def load_account() -> dict[str, Any] | None:
    if not ACCOUNT_PATH.exists():
        return None
    with ACCOUNT_PATH.open(encoding="utf-8") as f:
        return json.load(f)


def save_account(data: dict[str, Any]) -> None:
    with ACCOUNT_PATH.open("w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def clear_account() -> None:
    if ACCOUNT_PATH.exists():
        ACCOUNT_PATH.unlink()


def get_latest_versions() -> dict[str, str]:
    return minecraft_launcher_lib.utils.get_latest_version()


def list_available_versions(*, release_only: bool = False, limit: int = 0) -> list[dict[str, str]]:
    versions = minecraft_launcher_lib.utils.get_version_list()
    out: list[dict[str, str]] = []
    for v in versions:
        if release_only and v["type"] != "release":
            continue
        out.append({"id": v["id"], "type": v["type"]})
        if limit and len(out) >= limit:
            break
    return out


def list_installed_versions(directory: str | None = None) -> list[str]:
    directory = directory or minecraft_directory()
    return [v["id"] for v in minecraft_launcher_lib.utils.get_installed_versions(directory)]


def resolve_version_id(version: str) -> str:
    if version in ("latest", "latest-release"):
        return get_latest_versions()["release"]
    if version == "latest-snapshot":
        return get_latest_versions()["snapshot"]
    return version


def parse_version_ref(version: str) -> tuple[ModLoaderName, str]:
    """
    Parse a UI/config version ref into (loader, minecraft_ref).

    Accepts:
      - vanilla: "1.21.1", "latest"
      - fabric:  "fabric:1.21.1", "fabric:latest"
      - installed fabric profile: "fabric-loader-0.19.3-1.21.1"
    """
    raw = (version or "").strip()
    if not raw:
        return "vanilla", "latest"
    lower = raw.lower()
    if lower.startswith("fabric:"):
        mc = raw.split(":", 1)[1].strip() or "latest"
        return "fabric", mc
    match = _FABRIC_INSTALLED_RE.match(raw)
    if match:
        return "fabric", match.group(2)
    return "vanilla", raw


def make_version_ref(loader: ModLoaderName, minecraft_version: str) -> str:
    mc = (minecraft_version or "latest").strip() or "latest"
    if loader == "fabric":
        return f"fabric:{mc}"
    return mc


def display_version_label(version: str) -> str:
    loader, mc = parse_version_ref(version)
    if loader == "fabric":
        return f"Fabric {mc}"
    return mc


def vanilla_minecraft_id(version: str) -> str:
    """Resolved vanilla Minecraft id for a version ref (no fabric-loader prefix)."""
    _, mc = parse_version_ref(version)
    return resolve_version_id(mc)


def fabric_mod_loader() -> Any:
    return minecraft_launcher_lib.mod_loader.get_mod_loader("fabric")


def fabric_supported_versions(*, stable_only: bool = True) -> list[str]:
    return fabric_mod_loader().get_minecraft_versions(stable_only)


def resolve_fabric_minecraft_id(minecraft_ref: str) -> str:
    """Resolve latest/vanilla id to a Fabric-supported Minecraft release when possible."""
    mc = resolve_version_id(minecraft_ref)
    fabric = fabric_mod_loader()
    if fabric.is_minecraft_version_supported(mc):
        return mc
    if minecraft_ref in ("latest", "latest-release"):
        supported = fabric.get_minecraft_versions(True)
        if supported:
            return supported[0]
    raise UnsupportedVersion(mc)


def find_installed_fabric(minecraft_version: str, directory: str | None = None) -> str | None:
    """Return an installed fabric-loader-* id for this Minecraft version, if any."""
    directory = directory or minecraft_directory()
    suffix = f"-{minecraft_version}"
    matches = [
        vid
        for vid in list_installed_versions(directory)
        if vid.startswith("fabric-loader-") and vid.endswith(suffix)
    ]
    if not matches:
        return None
    # Prefer the newest loader version string
    def loader_key(vid: str) -> tuple:
        match = _FABRIC_INSTALLED_RE.match(vid)
        if not match:
            return (0,)
        parts = match.group(1).split(".")
        try:
            return tuple(int(p) for p in parts)
        except ValueError:
            return (0,)

    return sorted(matches, key=loader_key)[-1]


def install_fabric(
    minecraft_version: str,
    *,
    directory: str | None = None,
    on_progress: ProgressCallback | None = None,
    prefer_existing: bool = True,
) -> str:
    """
    Install Fabric for a vanilla Minecraft version.
    Returns the installed profile id (fabric-loader-…-…).
    """
    directory = directory or minecraft_directory()
    mc = resolve_fabric_minecraft_id(minecraft_version)
    fabric = fabric_mod_loader()

    if prefer_existing:
        existing = find_installed_fabric(mc, directory)
        if existing:
            if on_progress:
                on_progress(0, 0, f"Using installed {existing}")
            return existing

    if on_progress:
        on_progress(0, 0, f"Installing Fabric for {mc}…")

    # Ensure vanilla + Mojang Java exist so the Fabric installer can run.
    install_version(mc, directory=directory, on_progress=on_progress)
    java_path = ensure_java(mc, directory, on_progress=on_progress) or "java"
    callback = _make_install_callback(on_progress)
    try:
        installed = fabric.install(
            mc,
            directory,
            callback=callback,
            java=java_path,
        )
    except UnsupportedVersion:
        raise
    except VersionNotFound:
        raise
    return installed


def ram_to_jvm_arguments(ram_gb: int) -> list[str]:
    ram_gb = max(1, min(32, int(ram_gb)))
    return [f"-Xmx{ram_gb}G"]


def parse_ram_gb(jvm_arguments: list[str] | None) -> int:
    for arg in jvm_arguments or []:
        if arg.startswith("-Xmx") and arg.endswith("G"):
            try:
                return int(arg[4:-1])
            except ValueError:
                pass
        if arg.startswith("-Xmx") and arg.endswith("M"):
            try:
                return max(1, int(arg[4:-1]) // 1024)
            except ValueError:
                pass
    return 4


def _make_install_callback(on_progress: ProgressCallback | None) -> dict[str, Callable[..., None]]:
    state = {"max": 0, "value": 0, "status": "Starting"}

    def set_status(status: str) -> None:
        state["status"] = status
        if on_progress:
            on_progress(state["value"], state["max"], status)

    def set_progress(value: int) -> None:
        state["value"] = value
        if on_progress:
            on_progress(state["value"], state["max"], state["status"])

    def set_max(maximum: int) -> None:
        state["max"] = maximum

    return {"setStatus": set_status, "setProgress": set_progress, "setMax": set_max}


def install_version(
    version: str,
    *,
    directory: str | None = None,
    on_progress: ProgressCallback | None = None,
) -> str:
    """
    Install a Minecraft version (vanilla or Fabric).

    Accepts plain ids ("1.21.1"), "latest", "fabric:1.21.1", or an installed
    fabric-loader profile id. Returns the launchable version id.
    """
    directory = directory or minecraft_directory()
    loader, mc_ref = parse_version_ref(version)
    if loader == "fabric":
        return install_fabric(mc_ref, directory=directory, on_progress=on_progress)

    version = resolve_version_id(mc_ref)
    callback = _make_install_callback(on_progress)
    try:
        minecraft_launcher_lib.install.install_minecraft_version(
            version, directory, callback=callback
        )
    except VersionNotFound:
        raise
    return version


def ensure_java(
    version: str,
    directory: str,
    *,
    on_progress: ProgressCallback | None = None,
) -> str | None:
    """Ensure Mojang Java for this version is installed. Returns java path or None."""
    runtime_info = minecraft_launcher_lib.runtime.get_version_runtime_information(
        version, directory
    )
    if not runtime_info:
        return None

    runtime_name = runtime_info["name"]
    java_path = minecraft_launcher_lib.runtime.get_executable_path(runtime_name, directory)
    if java_path:
        return java_path

    if on_progress:
        on_progress(0, 0, f"Installing Java runtime {runtime_name}...")
    callback = _make_install_callback(on_progress)
    minecraft_launcher_lib.runtime.install_jvm_runtime(
        runtime_name, directory, callback=callback
    )
    return minecraft_launcher_lib.runtime.get_executable_path(runtime_name, directory)


def offline_options(username: str, jvm_arguments: list[str] | None = None) -> dict[str, Any]:
    options: dict[str, Any] = {
        "username": username,
        "uuid": str(uuid.uuid3(uuid.NAMESPACE_DNS, f"OfflinePlayer:{username}")),
        "token": "0",
        "offline": True,
    }
    if jvm_arguments:
        options["jvmArguments"] = list(jvm_arguments)
    return options


def refresh_account(config: dict[str, Any], account: dict[str, Any]) -> dict[str, Any]:
    client_id = (config.get("client_id") or "").strip()
    client_secret = (config.get("client_secret") or "").strip() or None
    redirect_uri = (config.get("redirect_uri") or "").strip()
    refresh_token = account["refresh_token"]

    try:
        login_data = minecraft_launcher_lib.microsoft_account.complete_refresh(
            client_id, client_secret, redirect_uri, refresh_token
        )
    except TypeError:
        login_data = minecraft_launcher_lib.microsoft_account.complete_refresh(
            client_id, refresh_token
        )

    updated = {
        "id": login_data["id"],
        "name": login_data["name"],
        "access_token": login_data["access_token"],
        "refresh_token": login_data.get("refresh_token", refresh_token),
    }
    save_account(updated)
    return updated


def online_options(config: dict[str, Any] | None = None) -> dict[str, Any]:
    config = config or load_config()
    account = load_account()
    if not account:
        raise RuntimeError("Not logged in. Run: python launcher.py login")

    try:
        account = refresh_account(config, account)
    except InvalidRefreshToken as exc:
        raise RuntimeError("Session expired. Run: python launcher.py login") from exc
    except AzureAppNotPermitted as exc:
        raise RuntimeError("Azure app not permitted for Minecraft API.") from exc

    options: dict[str, Any] = {
        "username": account["name"],
        "uuid": account["id"],
        "token": account["access_token"],
    }
    jvm = config.get("jvm_arguments") or []
    if jvm:
        options["jvmArguments"] = list(jvm)
    return options


def build_launch_command(
    version: str,
    options: dict[str, Any],
    *,
    directory: str | None = None,
    on_progress: ProgressCallback | None = None,
    ensure_installed: bool = True,
) -> tuple[list[str], str, str]:
    """
    Prepare and return (command, resolved_version, directory).
    Installs the version/Java if needed.
    """
    config = load_config()
    directory = directory or minecraft_directory(config)
    loader, mc_ref = parse_version_ref(version)

    if loader == "fabric":
        mc = resolve_fabric_minecraft_id(mc_ref)
        if ensure_installed:
            version = install_fabric(mc, directory=directory, on_progress=on_progress)
        else:
            version = find_installed_fabric(mc, directory) or install_fabric(
                mc, directory=directory, on_progress=on_progress, prefer_existing=False
            )
        java_path = ensure_java(mc, directory, on_progress=on_progress)
    else:
        version = resolve_version_id(mc_ref)
        if ensure_installed:
            installed = set(list_installed_versions(directory))
            if version not in installed:
                if on_progress:
                    on_progress(0, 0, f"Installing {version}...")
                install_version(version, directory=directory, on_progress=on_progress)
        java_path = ensure_java(version, directory, on_progress=on_progress)

    if java_path:
        options["executablePath"] = java_path

    command = minecraft_launcher_lib.command.get_minecraft_command(version, directory, options)
    return command, version, directory


def launch_version(
    version: str,
    options: dict[str, Any],
    *,
    directory: str | None = None,
    on_progress: ProgressCallback | None = None,
    wait: bool = True,
) -> subprocess.Popen[Any] | int:
    """Launch Minecraft. If wait=True, blocks and returns exit code; else returns Popen."""
    command, _, directory = build_launch_command(
        version, options, directory=directory, on_progress=on_progress
    )
    if wait:
        completed = subprocess.run(command, cwd=directory, check=False)
        return completed.returncode
    return subprocess.Popen(command, cwd=directory)


def microsoft_redirect_uri(config: dict[str, Any] | None = None) -> str:
    config = config or load_config()
    uri = str(config.get("redirect_uri") or "").strip()
    if uri:
        return uri
    # localhost is allowed by Azure for public clients; 127.0.0.1 often is not.
    return "http://localhost:28562/"


NATIVE_CLIENT_REDIRECT = "https://login.microsoftonline.com/common/oauth2/nativeclient"


def microsoft_login_begin(
    config: dict[str, Any] | None = None,
    *,
    redirect_uri: str | None = None,
) -> tuple[str, str, str, str]:
    """
    Start Microsoft login.
    Returns (login_url, state, code_verifier, redirect_uri).
    """
    config = config or load_config()
    client_id = (config.get("client_id") or "").strip()
    redirect = (redirect_uri or microsoft_redirect_uri(config)).strip()
    if not client_id or client_id.startswith("YOUR_"):
        raise RuntimeError(
            "Set client_id in config.json first. "
            "Azure apps need Minecraft API approval: https://aka.ms/mce-reviewappid"
        )
    login_url, state, code_verifier = minecraft_launcher_lib.microsoft_account.get_secure_login_data(
        client_id, redirect
    )
    return login_url, state, code_verifier, redirect


def microsoft_login_finish(
    code_url: str,
    state: str,
    code_verifier: str,
    config: dict[str, Any] | None = None,
    *,
    redirect_uri: str | None = None,
) -> dict[str, Any]:
    """Complete Microsoft login from the browser redirect URL."""
    config = config or load_config()
    client_id = (config.get("client_id") or "").strip()
    client_secret = (config.get("client_secret") or "").strip() or None
    redirect = (redirect_uri or microsoft_redirect_uri(config)).strip()
    code_url = (code_url or "").strip().strip('"').strip("'")
    if not code_url:
        raise RuntimeError("No redirect URL received from Microsoft login.")

    if "code=" not in code_url:
        raise RuntimeError(
            "That text is not a login URL. Paste the FULL address-bar URL — "
            "it must contain code=… (the page itself can look broken)."
        )

    try:
        auth_code = minecraft_launcher_lib.microsoft_account.parse_auth_code_url(code_url, state)
    except AssertionError as exc:
        raise RuntimeError("State mismatch — close the browser tab and try Microsoft login again.") from exc
    except KeyError as exc:
        raise RuntimeError(
            "URL has no login code. Make sure you copied the full address bar, not the page text."
        ) from exc

    ms = minecraft_launcher_lib.microsoft_account
    token_request = ms.get_authorization_token(
        client_id, client_secret, redirect, auth_code, code_verifier
    )
    if "access_token" not in token_request:
        err = str(token_request.get("error_description") or token_request.get("error") or token_request)
        if "client_secret" in err.lower() or "AADSTS70002" in err:
            raise RuntimeError(
                "Azure says this app needs a client secret.\n\n"
                "Easiest fix (recommended for a launcher):\n"
                "1. Azure → your app → Authentication\n"
                "2. Allow public client flows = Yes\n"
                "3. Save, then try login again\n\n"
                "Or keep it as a web app:\n"
                "1. Azure → Certificates & secrets → New client secret\n"
                "2. Copy the Value into config.json as \"client_secret\"\n"
                "3. Try login again"
            )
        raise RuntimeError(
            f"Microsoft rejected the login code:\n{err}\n\n"
            "Azure → Authentication:\n"
            "• Platform: Mobile and desktop applications\n"
            f"• Tick: {NATIVE_CLIENT_REDIRECT}\n"
            "• Allow public client flows = Yes\n"
            "• Save, then try again (codes work only once)."
        )

    try:
        xbl_request = ms.authenticate_with_xbl(token_request["access_token"])
        xbl_token = xbl_request["Token"]
        userhash = xbl_request["DisplayClaims"]["xui"][0]["uhs"]
        xsts_request = ms.authenticate_with_xsts(xbl_token)
        xsts_token = xsts_request["Token"]
        account_request = ms.authenticate_with_minecraft(userhash, xsts_token)
    except Exception as exc:  # noqa: BLE001
        raise RuntimeError(f"Xbox / Minecraft auth failed: {exc}") from exc

    if "access_token" not in account_request:
        raise RuntimeError(
            "Your Azure app is not permitted to use the Minecraft API yet.\n"
            "Apply here (can take days): https://aka.ms/mce-reviewappid"
        )

    try:
        profile = ms.get_profile(account_request["access_token"])
    except Exception as exc:  # noqa: BLE001
        raise RuntimeError(f"Could not load Minecraft profile: {exc}") from exc

    if isinstance(profile, dict) and profile.get("error") == "NOT_FOUND":
        raise RuntimeError("This Microsoft account does not own Minecraft Java.")

    account = {
        "id": profile["id"],
        "name": profile["name"],
        "access_token": account_request["access_token"],
        "refresh_token": token_request["refresh_token"],
    }
    save_account(account)
    return account


def microsoft_login_with_local_server(
    config: dict[str, Any] | None = None,
    *,
    timeout: float = 180,
) -> dict[str, Any]:
    """
    Open the browser and capture the OAuth redirect on a local HTTP server.
    Prefer redirect_uri like http://localhost:28562/ in Azure + config.json.
    """
    import http.server
    import socketserver
    import threading
    import urllib.parse
    from urllib.parse import urlparse

    config = config or load_config()
    preferred = microsoft_redirect_uri(config)
    # Normalize 127.0.0.1 → localhost (Azure accepts localhost more reliably)
    preferred = preferred.replace("http://127.0.0.1", "http://localhost")
    parsed = urlparse(preferred)
    host = "127.0.0.1"  # bind all-loopback; URI still says localhost
    port = parsed.port or 28562
    path = parsed.path or "/"
    if not path.endswith("/"):
        path = path + "/"
    redirect_uri = f"http://localhost:{port}{path}"

    result: dict[str, Any] = {"url": None, "error": None}
    done = threading.Event()

    class Handler(http.server.BaseHTTPRequestHandler):
        def do_GET(self) -> None:  # noqa: N802
            full = f"http://localhost:{port}{self.path}"
            qs = urllib.parse.parse_qs(urllib.parse.urlparse(self.path).query)
            if "code" in qs:
                result["url"] = full
                body = (
                    b"<html><body style='font-family:sans-serif;background:#111;color:#eee;"
                    b"padding:2rem'><h2>Deepslate Launcher</h2>"
                    b"<p>Signed in. You can close this tab and return to the launcher.</p>"
                    b"</body></html>"
                )
                self.send_response(200)
                self.send_header("Content-Type", "text/html; charset=utf-8")
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)
                done.set()
            elif "error" in qs:
                result["error"] = qs.get("error_description", qs.get("error", ["unknown"]))[0]
                body = (
                    b"<html><body style='font-family:sans-serif;background:#111;color:#eee;"
                    b"padding:2rem'><h2>Login failed</h2><p>Return to the launcher.</p>"
                    b"</body></html>"
                )
                self.send_response(400)
                self.send_header("Content-Type", "text/html; charset=utf-8")
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)
                done.set()
            else:
                self.send_response(404)
                self.end_headers()

        def log_message(self, format: str, *args: Any) -> None:  # noqa: A003
            return

    try:
        httpd = socketserver.TCPServer((host, port), Handler)
    except OSError as exc:
        raise RuntimeError(
            f"Could not bind port {port} ({exc}). "
            "Close anything using that port, or change redirect_uri in config.json."
        ) from exc

    httpd.timeout = 1.0
    server_thread = threading.Thread(target=httpd.serve_forever, daemon=True)
    server_thread.start()

    try:
        login_url, state, code_verifier, redirect = microsoft_login_begin(
            config, redirect_uri=redirect_uri
        )
        webbrowser.open(login_url)
        if not done.wait(timeout):
            raise RuntimeError("Microsoft login timed out. Try again.")
        if result.get("error"):
            raise RuntimeError(str(result["error"]))
        if not result.get("url"):
            raise RuntimeError("No auth code received from Microsoft.")
        return microsoft_login_finish(
            str(result["url"]),
            state,
            code_verifier,
            config,
            redirect_uri=redirect,
        )
    finally:
        httpd.shutdown()
        httpd.server_close()


def microsoft_login_with_paste(
    config: dict[str, Any] | None = None,
    *,
    code_url_provider: Any = None,
) -> dict[str, Any]:
    """
    Login using Azure's built-in native-client redirect (no custom URI needed).
    code_url_provider() should return the full browser redirect URL string.
    """
    config = config or load_config()
    redirect = NATIVE_CLIENT_REDIRECT
    login_url, state, code_verifier, _redirect = microsoft_login_begin(
        config, redirect_uri=redirect
    )
    webbrowser.open(login_url)
    if code_url_provider is None:
        print("Opening browser for Microsoft login...")
        print(
            "You will likely see a page that says this is not the right page — that is normal.\n"
            "Copy the FULL URL from the address bar (must contain code=) and paste it below.\n"
        )
        code_url = input("Redirect URL: ").strip()
    else:
        code_url = code_url_provider(login_url)
    if not code_url:
        raise RuntimeError("Login cancelled.")
    return microsoft_login_finish(
        str(code_url), state, code_verifier, config, redirect_uri=redirect
    )


def microsoft_login_interactive(config: dict[str, Any] | None = None) -> dict[str, Any]:
    """Browser-based Microsoft login (CLI). Prefer localhost; fall back to native paste."""
    config = config or load_config()
    redirect = microsoft_redirect_uri(config).replace("http://127.0.0.1", "http://localhost")
    if redirect.startswith("http://localhost"):
        try:
            return microsoft_login_with_local_server(config)
        except Exception as exc:  # noqa: BLE001
            print(f"Localhost login failed ({exc}); falling back to paste login…")
    return microsoft_login_with_paste(config)


def versions_for_ui() -> list[str]:
    """Version choices for the GUI dropdown: latest + installed + recent releases."""
    latest = get_latest_versions()["release"]
    installed = list_installed_versions()
    releases = [v["id"] for v in list_available_versions(release_only=True, limit=40)]

    choices: list[str] = ["latest"]
    seen = {"latest"}
    for vid in [latest, *installed, *releases]:
        if vid not in seen:
            choices.append(vid)
            seen.add(vid)
    return choices


def version_family(version_id: str) -> str:
    """Map 1.21.11 -> 1.21, 26.2 -> 26, latest -> latest (Fabric refs OK)."""
    _, mc = parse_version_ref(version_id)
    if mc in ("latest", "latest-release", "latest-snapshot"):
        return "latest"
    parts = mc.split(".")
    if not parts:
        return mc
    # New calendar scheme: 26.2, 26.1.2
    if parts[0].isdigit() and len(parts[0]) == 2 and int(parts[0]) >= 25:
        return parts[0]
    # Classic: 1.21.11 -> 1.21
    if len(parts) >= 2:
        return f"{parts[0]}.{parts[1]}"
    return mc


UPDATE_TITLES: dict[str, str] = {
    "latest": "Latest",
    "26": "2026 Drops",
    "1.21": "Tricky Trials",
    "1.20": "Trails & Tales",
    "1.19": "The Wild",
    "1.18": "Caves & Cliffs II",
    "1.17": "Caves & Cliffs I",
    "1.16": "Nether Update",
    "1.15": "Buzzy Bees",
    "1.14": "Village & Pillage",
    "1.13": "Update Aquatic",
    "1.12": "World of Color",
    "1.11": "Exploration",
    "1.10": "Frostburn",
    "1.9": "Combat Update",
    "1.8": "Bountiful Update",
    "1.7": "Changed the World",
    "1.6": "Horse Update",
    "1.5": "Redstone Update",
    "1.4": "Pretty Scary",
    "1.3": "Minecraft 1.3",
    "1.2": "Minecraft 1.2",
    "1.1": "Minecraft 1.1",
    "1.0": "Adventure Update",
}


def update_title(family: str) -> str:
    return UPDATE_TITLES.get(family, f"Minecraft {family}")


def update_art_path(family: str) -> Path | None:
    """Local cached official key art for a major version family."""
    base = resource_root() / "assets" / "updates"
    if not base.is_dir():
        return None
    stem = family.replace(".", "_")
    for ext in (".img", ".jpg", ".jpeg", ".png", ".webp"):
        path = base / f"{stem}{ext}"
        if path.is_file():
            return path
    return None


def versions_grouped(
    *,
    release_only: bool = True,
    limit_per_family: int = 12,
    loader: ModLoaderName = "vanilla",
) -> list[dict[str, Any]]:
    """
    Group release versions into major families for the UI.
    Returns [{family, title, versions: [id, ...]}, ...] newest-first.

    When loader="fabric", only Fabric-supported Minecraft versions are listed.
    """
    latest = get_latest_versions()["release"]
    installed = set(list_installed_versions())
    releases = list_available_versions(release_only=release_only, limit=0)

    fabric_supported: set[str] | None = None
    if loader == "fabric":
        fabric_supported = set(fabric_supported_versions(stable_only=release_only))

    by_family: dict[str, list[str]] = {}
    for v in releases:
        vid = v["id"]
        if fabric_supported is not None and vid not in fabric_supported:
            continue
        fam = version_family(vid)
        by_family.setdefault(fam, []).append(vid)

    # Ensure latest + installed vanilla ids appear (skip bare fabric-loader profiles)
    extra_ids: list[str] = []
    if fabric_supported is None or latest in fabric_supported:
        extra_ids.append(latest)
    for vid in sorted(installed):
        if vid.startswith("fabric-loader-"):
            match = _FABRIC_INSTALLED_RE.match(vid)
            if match and loader == "fabric":
                extra_ids.append(match.group(2))
            continue
        if fabric_supported is not None and vid not in fabric_supported:
            continue
        extra_ids.append(vid)

    for vid in extra_ids:
        fam = version_family(vid)
        bucket = by_family.setdefault(fam, [])
        if vid not in bucket:
            bucket.insert(0, vid)

    def family_sort_key(fam: str) -> tuple:
        if fam == "latest":
            return (9999, 9999)
        parts = fam.split(".")
        try:
            nums = tuple(int(p) for p in parts)
        except ValueError:
            return (0, 0)
        # Calendar years (26) before classic 1.x when sorting newest-first via reverse
        if len(nums) == 1 and nums[0] >= 25:
            return (2000 + nums[0], 0)
        if len(nums) >= 2:
            return (nums[0], nums[1])
        return (nums[0], 0)

    groups: list[dict[str, Any]] = []
    for fam in sorted(by_family.keys(), key=family_sort_key, reverse=True):
        versions = by_family[fam]
        # Prefer newest patch first — manifest is usually newest-first already
        trimmed = versions[:limit_per_family]
        groups.append(
            {
                "family": fam,
                "title": update_title(fam),
                "versions": trimmed,
                "art": str(update_art_path(fam) or ""),
            }
        )
    return groups


def default_servers() -> list[dict[str, Any]]:
    return []


def load_servers(config: dict[str, Any] | None = None) -> list[dict[str, Any]]:
    config = config or load_config()
    servers = config.get("servers")
    if isinstance(servers, list):
        return [s for s in servers if isinstance(s, dict) and s.get("address")]
    return []


def save_servers(servers: list[dict[str, Any]], config: dict[str, Any] | None = None) -> dict[str, Any]:
    config = config or load_config()
    cleaned: list[dict[str, Any]] = []
    for s in servers:
        if not isinstance(s, dict):
            continue
        address = str(s.get("address") or "").strip()
        if not address:
            continue
        name = str(s.get("name") or address).strip() or address
        try:
            port = int(s.get("port") or 25565)
        except (TypeError, ValueError):
            port = 25565
        cleaned.append({"name": name, "address": address, "port": port})
    config["servers"] = cleaned
    save_config(config)
    return config


# --- Guest Fabric lock (loader + client mod only) ---

GUEST_LOCK_MOD_ID = "deepslate_guest_lock"
GUEST_LOCK_JAR_NAME = "deepslate-guest-lock.jar"
GUEST_LOCK_JVM_FLAG = "-Ddeepslate.guest=true"
MODS_STASH_DIRNAME = ".deepslate-stash"
GUEST_SESSION_MARKER = ".deepslate-guest-session"


def guest_lock_assets_root() -> Path:
    return resource_root() / "assets" / "guest-lock"


def guest_lock_manifest() -> dict[str, Any]:
    path = guest_lock_assets_root() / "manifest.json"
    if not path.is_file():
        return {"versions": []}
    try:
        with path.open(encoding="utf-8") as f:
            data = json.load(f)
        return data if isinstance(data, dict) else {"versions": []}
    except (OSError, json.JSONDecodeError):
        return {"versions": []}


def _mc_version_sort_key(version: str) -> tuple[int, ...]:
    parts: list[int] = []
    for bit in str(version).split("."):
        try:
            parts.append(int(bit))
        except ValueError:
            digits = "".join(ch for ch in bit if ch.isdigit())
            parts.append(int(digits) if digits else 0)
    return tuple(parts) if parts else (0,)


def guest_lock_versions() -> list[str]:
    """Minecraft versions that ship a guest-lock jar (manifest + on-disk folders)."""
    found: set[str] = set()
    for vid in guest_lock_manifest().get("versions") or []:
        if isinstance(vid, str) and vid.strip():
            found.add(vid.strip())
    root = guest_lock_assets_root()
    if root.is_dir():
        for child in root.iterdir():
            if child.is_dir() and (child / GUEST_LOCK_JAR_NAME).is_file():
                found.add(child.name)
    return sorted(found, key=_mc_version_sort_key, reverse=True)


def filter_version_groups(
    groups: list[dict[str, Any]],
    allowed: set[str] | list[str],
) -> list[dict[str, Any]]:
    """Keep only Minecraft ids present in ``allowed`` (drops empty families)."""
    allow = set(allowed)
    out: list[dict[str, Any]] = []
    for group in groups:
        versions = [v for v in (group.get("versions") or []) if v in allow]
        if versions:
            out.append({**group, "versions": versions})
    return out


def guest_lock_jar_for(minecraft_version: str) -> Path | None:
    """Return path to the guest-lock jar for this Minecraft version, if present."""
    try:
        mc = resolve_fabric_minecraft_id(minecraft_version)
    except Exception:  # noqa: BLE001
        mc = str(minecraft_version or "").strip()
    root = guest_lock_assets_root()
    for vid in (mc, str(minecraft_version or "").strip()):
        if not vid:
            continue
        path = root / vid / GUEST_LOCK_JAR_NAME
        if path.is_file():
            return path
    return None


def mods_dir(directory: str | None = None) -> Path:
    return Path(directory or minecraft_directory()) / "mods"


def mods_stash_dir(directory: str | None = None) -> Path:
    return mods_dir(directory) / MODS_STASH_DIRNAME


def guest_session_marker(directory: str | None = None) -> Path:
    return mods_dir(directory) / GUEST_SESSION_MARKER


def restore_mods_stash(directory: str | None = None) -> None:
    """Restore user mods after a guest session (safe to call anytime)."""
    directory = directory or minecraft_directory()
    mods = mods_dir(directory)
    stash = mods_stash_dir(directory)
    marker = guest_session_marker(directory)

    if not stash.is_dir() and not marker.exists():
        # Still remove any leftover guest-lock jar if a previous run died oddly
        return

    mods.mkdir(parents=True, exist_ok=True)

    # Remove guest-lock jars from the live mods folder
    for jar in mods.glob("*.jar"):
        name = jar.name.lower()
        if name.startswith("deepslate-guest-lock") or name == GUEST_LOCK_JAR_NAME.lower():
            try:
                jar.unlink()
            except OSError:
                pass

    if stash.is_dir():
        for item in stash.iterdir():
            dest = mods / item.name
            if dest.exists():
                continue
            try:
                shutil.move(str(item), str(dest))
            except OSError:
                try:
                    shutil.copy2(item, dest)
                    if item.is_file():
                        item.unlink(missing_ok=True)
                except OSError:
                    pass
        try:
            stash.rmdir()
        except OSError:
            # leftover files — ignore
            pass

    try:
        marker.unlink(missing_ok=True)
    except OSError:
        pass


def prepare_guest_session(
    minecraft_version: str,
    directory: str | None = None,
) -> list[str]:
    """
    Prepare mods/ for a guest Fabric launch: stash user jars, install guest-lock only.
    Returns extra JVM arguments (includes -Ddeepslate.guest=true).
    """
    directory = directory or minecraft_directory()
    mc = resolve_fabric_minecraft_id(minecraft_version)
    jar_src = guest_lock_jar_for(mc)
    if jar_src is None:
        raise RuntimeError(
            f"No guest-lock mod for Minecraft {mc}.\n"
            "Build it with: py -3 guest-lock/build_all.py {mc}\n"
            f"Expected: assets/guest-lock/{mc}/{GUEST_LOCK_JAR_NAME}"
        )

    # Ensure we never stack stashes — restore any previous guest session first
    restore_mods_stash(directory)

    mods = mods_dir(directory)
    mods.mkdir(parents=True, exist_ok=True)
    stash = mods_stash_dir(directory)
    stash.mkdir(parents=True, exist_ok=True)

    for item in list(mods.iterdir()):
        if item.name in (MODS_STASH_DIRNAME, GUEST_SESSION_MARKER):
            continue
        if item.is_file() and item.suffix.lower() == ".jar":
            dest = stash / item.name
            if dest.exists():
                dest.unlink()
            shutil.move(str(item), str(dest))

    dest_jar = mods / GUEST_LOCK_JAR_NAME
    shutil.copy2(jar_src, dest_jar)
    guest_session_marker(directory).write_text(mc + "\n", encoding="utf-8")
    return [GUEST_LOCK_JVM_FLAG]
