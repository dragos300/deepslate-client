#!/usr/bin/env python3
"""Cross-platform install / uninstall / run helpers for Deepslate Launcher."""

from __future__ import annotations

import argparse
import json
import os
import shutil
import subprocess
import sys
import venv
from pathlib import Path

ROOT = Path(__file__).resolve().parent
VENV = ROOT / ".venv"
REQUIREMENTS = ROOT / "requirements.txt"
APP = ROOT / "app.py"
ICON_PNG = ROOT / "assets" / "deepslate_block.png"
ICON_ICO = ROOT / "assets" / "icon.ico"
INSTALL_META_NAME = "install.json"


def is_windows() -> bool:
    return sys.platform == "win32"


def is_macos() -> bool:
    return sys.platform == "darwin"


def python_bin() -> Path:
    if is_windows():
        return VENV / "Scripts" / "python.exe"
    return VENV / "bin" / "python"


def pythonw_bin() -> Path:
    if is_windows():
        candidate = VENV / "Scripts" / "pythonw.exe"
        return candidate if candidate.is_file() else python_bin()
    return python_bin()


def find_system_python() -> str:
    for name in ("python3", "python"):
        path = shutil.which(name)
        if path:
            return path
    raise SystemExit("Python 3.10+ not found. Install Python and retry.")


def ensure_venv() -> Path:
    py = python_bin()
    if not py.is_file():
        print("Creating virtual environment…")
        venv.create(VENV, with_pip=True)
    if not py.is_file():
        raise SystemExit(f"Failed to create venv at {VENV}")
    return py


def ensure_deps(py: Path) -> None:
    print("Ensuring dependencies…")
    subprocess.check_call(
        [str(py), "-m", "pip", "install", "-q", "-r", str(REQUIREMENTS)],
        cwd=str(ROOT),
    )
    if not ICON_ICO.is_file() and (ROOT / "make_icon.py").is_file():
        subprocess.check_call([str(py), str(ROOT / "make_icon.py")], cwd=str(ROOT))


def meta_path() -> Path:
    if is_windows():
        base = Path(os.environ.get("LOCALAPPDATA", Path.home() / "AppData" / "Local"))
        return base / "DeepslateLauncher" / INSTALL_META_NAME
    if is_macos():
        return Path.home() / "Library" / "Application Support" / "DeepslateLauncher" / INSTALL_META_NAME
    return Path.home() / ".local" / "share" / "DeepslateLauncher" / INSTALL_META_NAME


def write_meta(data: dict) -> None:
    path = meta_path()
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def read_meta() -> dict | None:
    path = meta_path()
    if not path.is_file():
        return None
    return json.loads(path.read_text(encoding="utf-8"))


def desktop_dir() -> Path:
    if is_windows():
        return Path.home() / "Desktop"
    # XDG / macOS Desktop
    xdg = os.environ.get("XDG_DESKTOP_DIR")
    if xdg:
        return Path(xdg)
    return Path.home() / "Desktop"


def install_linux() -> None:
    apps = Path.home() / ".local" / "share" / "applications"
    apps.mkdir(parents=True, exist_ok=True)
    run_sh = ROOT / "run_app.sh"
    icon = ICON_PNG if ICON_PNG.is_file() else ICON_ICO
    desktop_file = apps / "deepslate-launcher.desktop"
    content = f"""[Desktop Entry]
Type=Application
Name=Deepslate Launcher
Comment=Personal Minecraft Java launcher
Exec="{run_sh}"
Icon={icon}
Path={ROOT}
Terminal=false
Categories=Game;
StartupWMClass=Deepslate Launcher
"""
    desktop_file.write_text(content, encoding="utf-8")
    os.chmod(run_sh, 0o755)
    os.chmod(desktop_file, 0o755)

    # Optional Desktop copy
    desk = desktop_dir() / "Deepslate Launcher.desktop"
    if desk.parent.is_dir():
        shutil.copy2(desktop_file, desk)
        os.chmod(desk, 0o755)

    write_meta(
        {
            "root": str(ROOT),
            "desktop_entry": str(desktop_file),
            "desktop_copy": str(desk) if desk.is_file() else None,
            "mode": "editable-source",
            "platform": "linux",
        }
    )
    print(f"Installed desktop entry: {desktop_file}")
    if desk.is_file():
        print(f"Desktop shortcut:     {desk}")


def install_macos() -> None:
    """Create a minimal .app bundle in ~/Applications that runs the project source."""
    apps_dir = Path.home() / "Applications"
    apps_dir.mkdir(parents=True, exist_ok=True)
    app_bundle = apps_dir / "Deepslate Launcher.app"
    contents = app_bundle / "Contents"
    macos_dir = contents / "MacOS"
    resources = contents / "Resources"
    macos_dir.mkdir(parents=True, exist_ok=True)
    resources.mkdir(parents=True, exist_ok=True)

    # Icon: copy PNG; macOS prefers icns but PNG often works in Info.plist via pythonw path
    if ICON_PNG.is_file():
        shutil.copy2(ICON_PNG, resources / "AppIcon.png")

    launcher = macos_dir / "DeepslateLauncher"
    launcher.write_text(
        f"""#!/bin/bash
cd "{ROOT}"
exec "{pythonw_bin()}" "{APP}"
""",
        encoding="utf-8",
    )
    os.chmod(launcher, 0o755)
    os.chmod(ROOT / "run_app.sh", 0o755)

    plist = contents / "Info.plist"
    plist.write_text(
        f"""<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>CFBundleName</key><string>Deepslate Launcher</string>
  <key>CFBundleDisplayName</key><string>Deepslate Launcher</string>
  <key>CFBundleIdentifier</key><string>local.deepslate.launcher</string>
  <key>CFBundleVersion</key><string>1.0</string>
  <key>CFBundlePackageType</key><string>APPL</string>
  <key>CFBundleExecutable</key><string>DeepslateLauncher</string>
  <key>LSMinimumSystemVersion</key><string>10.13</string>
  <key>NSHighResolutionCapable</key><true/>
</dict>
</plist>
""",
        encoding="utf-8",
    )

    # Also a Desktop alias via .command for convenience
    command = desktop_dir() / "Deepslate Launcher.command"
    if desktop_dir().is_dir():
        command.write_text(
            f"""#!/bin/bash
cd "{ROOT}"
exec "{pythonw_bin()}" "{APP}"
""",
            encoding="utf-8",
        )
        os.chmod(command, 0o755)

    write_meta(
        {
            "root": str(ROOT),
            "app_bundle": str(app_bundle),
            "desktop_command": str(command) if command.is_file() else None,
            "mode": "editable-source",
            "platform": "macos",
        }
    )
    print(f"Installed app bundle: {app_bundle}")
    if command.is_file():
        print(f"Desktop launcher:   {command}")


def install_windows() -> None:
    # Delegate to existing PowerShell installer for shortcuts/.lnk fidelity
    ps1 = ROOT / "install_app.ps1"
    if ps1.is_file():
        subprocess.check_call(
            [
                "powershell",
                "-NoProfile",
                "-ExecutionPolicy",
                "Bypass",
                "-File",
                str(ps1),
            ],
            cwd=str(ROOT),
        )
        return
    raise SystemExit("install_app.ps1 missing on Windows.")


def cmd_install(_: argparse.Namespace) -> int:
    py = ensure_venv()
    ensure_deps(py)
    for script in ("run_app.sh", "install_app.sh", "uninstall_app.sh", "build_app.sh"):
        path = ROOT / script
        if path.is_file() and not is_windows():
            os.chmod(path, 0o755)

    if is_windows():
        install_windows()
    elif is_macos():
        install_macos()
    else:
        install_linux()

    print()
    print(f"Source folder: {ROOT}")
    print("Edit app.py / core.py anytime, then relaunch to see changes.")
    return 0


def cmd_uninstall(_: argparse.Namespace) -> int:
    meta = read_meta() or {}
    removed = False

    if is_windows():
        ps1 = ROOT / "uninstall_app.ps1"
        if ps1.is_file():
            subprocess.check_call(
                [
                    "powershell",
                    "-NoProfile",
                    "-ExecutionPolicy",
                    "Bypass",
                    "-File",
                    str(ps1),
                ],
                cwd=str(ROOT),
            )
            return 0

    for key in ("desktop_entry", "desktop_copy", "app_bundle", "desktop_command"):
        path = meta.get(key)
        if path and Path(path).exists():
            p = Path(path)
            if p.is_dir():
                shutil.rmtree(p)
            else:
                p.unlink(missing_ok=True)
            print(f"Removed {p}")
            removed = True

    # Fallback linux desktop name
    linux_desk = Path.home() / ".local" / "share" / "applications" / "deepslate-launcher.desktop"
    if linux_desk.is_file():
        linux_desk.unlink()
        print(f"Removed {linux_desk}")
        removed = True

    meta_file = meta_path()
    if meta_file.is_file():
        meta_file.unlink()
        removed = True

    if not removed:
        print("Nothing to uninstall (or already clean).")
    else:
        print("Uninstall complete. Project files were left untouched.")
    return 0


def cmd_run(_: argparse.Namespace) -> int:
    py = ensure_venv()
    ensure_deps(py)
    runner = pythonw_bin()
    os.chdir(ROOT)
    return subprocess.call([str(runner), str(APP)])


def cmd_setup(_: argparse.Namespace) -> int:
    py = ensure_venv()
    ensure_deps(py)
    print(f"Ready. Run with: {python_bin()} app.py")
    print("Or: python desktop_install.py run")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="Deepslate Launcher cross-platform helpers")
    sub = parser.add_subparsers(dest="command", required=True)

    p = sub.add_parser("setup", help="Create venv and install Python deps")
    p.set_defaults(func=cmd_setup)

    p = sub.add_parser("run", help="Run the GUI launcher")
    p.set_defaults(func=cmd_run)

    p = sub.add_parser("install", help="Install desktop / Start Menu / Applications shortcuts")
    p.set_defaults(func=cmd_install)

    p = sub.add_parser("uninstall", help="Remove installed shortcuts / app bundle")
    p.set_defaults(func=cmd_uninstall)

    args = parser.parse_args()
    return args.func(args)


if __name__ == "__main__":
    # Allow running even before venv exists by using system python for this file.
    raise SystemExit(main())
