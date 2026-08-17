"""Discord Rich Presence for Deepslate Launcher (optional, fails soft)."""

from __future__ import annotations

import threading
import time
from typing import Any

# Create an application at https://discord.com/developers/applications
# then put its Application ID in config.json as discord_rpc_client_id.
# Optional: Rich Presence → Art Assets → upload images named "deepslate" and "legacy4j".

_presence: Any = None
_lock = threading.Lock()
_enabled = False
_client_id = ""
_legacy4j = False
_start_ts: int | None = None
_last_payload: dict[str, Any] | None = None


def configure(config: dict[str, Any]) -> None:
    """Read settings from launcher config and (re)connect if needed."""
    global _enabled, _client_id, _legacy4j
    enabled = bool(config.get("discord_rpc", True))
    client_id = str(config.get("discord_rpc_client_id") or "").strip()
    _enabled = enabled and bool(client_id)
    _client_id = client_id
    _legacy4j = bool(config.get("legacy4j_enabled", False))
    if not _enabled:
        clear()
        return
    _ensure_connected()


def _ensure_connected() -> bool:
    global _presence, _start_ts
    if not _enabled or not _client_id:
        return False
    with _lock:
        if _presence is not None:
            return True
        try:
            from pypresence import Presence

            rpc = Presence(_client_id)
            rpc.connect()
            _presence = rpc
            if _start_ts is None:
                _start_ts = int(time.time())
            return True
        except Exception:  # noqa: BLE001
            _presence = None
            return False


def _with_legacy(**kwargs: Any) -> dict[str, Any]:
    out = dict(kwargs)
    if _legacy4j:
        state = out.get("state") or ""
        out["state"] = f"{state} · Legacy4J" if state else "Legacy4J"
        out.setdefault("small_image", "legacy4j")
        out.setdefault("small_text", "Legacy4J · Console Edition")
    return out


def _update(**kwargs: Any) -> None:
    global _last_payload, _presence
    if not _ensure_connected():
        return
    payload = {k: v for k, v in kwargs.items() if v is not None}
    if _start_ts is not None:
        payload.setdefault("start", _start_ts)
    payload.setdefault("large_image", "deepslate")
    payload.setdefault("large_text", "Deepslate Launcher")
    with _lock:
        if _presence is None:
            return
        try:
            _presence.update(**payload)
            _last_payload = payload
        except Exception:  # noqa: BLE001
            try:
                _presence.close()
            except Exception:  # noqa: BLE001
                pass
            _presence = None


def set_idle(*, version_label: str = "", page: str = "play") -> None:
    if page == "mods":
        details = "Browsing mods"
        state = version_label or "Deepslate Launcher"
    else:
        details = "In the launcher"
        state = version_label or "Ready to play"
    _update(**_with_legacy(details=details, state=state))


def set_preparing(version_label: str) -> None:
    _update(**_with_legacy(details="Preparing Minecraft", state=version_label or "…"))


def set_playing(
    *,
    version_label: str,
    username: str = "",
    server_name: str | None = None,
    legacy4j: bool | None = None,
) -> None:
    global _legacy4j
    if legacy4j is not None:
        _legacy4j = bool(legacy4j)
    if server_name:
        details = f"Playing on {server_name}"
    elif _legacy4j:
        details = "Playing · Console Edition"
    elif version_label:
        details = f"Playing {version_label}"
    else:
        details = "Playing Minecraft"
    if username and not server_name:
        state = f"as {username}"
    elif version_label and server_name:
        state = version_label
    else:
        state = version_label or "In game"
    _update(**_with_legacy(details=details, state=state))


def clear() -> None:
    global _presence, _last_payload
    with _lock:
        if _presence is not None:
            try:
                _presence.clear()
            except Exception:  # noqa: BLE001
                pass
            try:
                _presence.close()
            except Exception:  # noqa: BLE001
                pass
        _presence = None
        _last_payload = None


def shutdown() -> None:
    clear()
