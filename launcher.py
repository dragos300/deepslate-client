#!/usr/bin/env python3
"""Personal Minecraft CLI launcher (Java Edition)."""

from __future__ import annotations

import argparse
import subprocess
import sys

import core
from minecraft_launcher_lib.exceptions import VersionNotFound


def _print_progress(value: int, maximum: int, status: str) -> None:
    if maximum:
        pct = int(value / maximum * 100)
        print(f"\r[{pct:3d}%] {status}{' ' * 20}", end="", flush=True)
    else:
        print(f"\r{status}{' ' * 20}", end="", flush=True)


def cmd_init(_: argparse.Namespace) -> int:
    existed = core.CONFIG_PATH.exists()
    path = core.init_config()
    if existed:
        print(f"Config already exists: {path}")
        return 0
    print(f"Created {path}")
    print("1. Create an Azure app (public client / native).")
    print("2. Set redirect URI to the value in config.json.")
    print("3. Apply for Minecraft API access: https://aka.ms/mce-reviewappid")
    print("4. Put your Client ID into config.json, then run: python launcher.py login")
    return 0


def cmd_versions(args: argparse.Namespace) -> int:
    latest = core.get_latest_versions()
    print(f"Latest release:  {latest['release']}")
    print(f"Latest snapshot: {latest['snapshot']}")
    print()
    for v in core.list_available_versions(release_only=args.release_only, limit=args.limit):
        print(f"{v['id']:<24} {v['type']}")
    return 0


def cmd_installed(_: argparse.Namespace) -> int:
    directory = core.minecraft_directory()
    installed = core.list_installed_versions(directory)
    if not installed:
        print(f"No versions installed in {directory}")
        return 0
    print(f"Installed in {directory}:")
    for vid in installed:
        print(f"  {vid}")
    return 0


def cmd_install(args: argparse.Namespace) -> int:
    directory = core.minecraft_directory()
    version_ref = f"fabric:{args.version}" if getattr(args, "fabric", False) else args.version
    print(f"Installing {version_ref} into {directory}")
    try:
        version = core.install_version(version_ref, directory=directory, on_progress=_print_progress)
    except VersionNotFound:
        print(f"\nVersion not found: {args.version}")
        return 1
    except Exception as exc:  # noqa: BLE001
        print(f"\nInstall failed: {exc}")
        return 1
    print(f"\nInstalled {version}")
    return 0


def cmd_login(_: argparse.Namespace) -> int:
    try:
        account = core.microsoft_login_interactive()
    except RuntimeError as exc:
        print(exc)
        return 1
    print(f"Logged in as {account['name']} ({account['id']})")
    return 0


def cmd_logout(_: argparse.Namespace) -> int:
    core.clear_account()
    print("Logged out.")
    return 0


def cmd_whoami(_: argparse.Namespace) -> int:
    account = core.load_account()
    if not account:
        print("Not logged in. Run: python launcher.py login")
        print("Or launch offline: python launcher.py launch <version> --offline Player")
        return 1
    print(f"{account['name']} ({account['id']})")
    return 0


def cmd_launch(args: argparse.Namespace) -> int:
    config = core.load_config()
    try:
        if args.offline:
            options = core.offline_options(args.offline, config.get("jvm_arguments"))
            mode = "offline"
        else:
            options = core.online_options(config)
            mode = "online"
    except RuntimeError as exc:
        print(exc)
        if not args.offline:
            print("Or use: python launcher.py launch <version> --offline Player")
        return 1

    def on_progress(value: int, maximum: int, status: str) -> None:
        _print_progress(value, maximum, status)

    try:
        version_ref = f"fabric:{args.version}" if getattr(args, "fabric", False) else args.version
        command, version, directory = core.build_launch_command(
            version_ref,
            options,
            on_progress=on_progress,
        )
    except Exception as exc:  # noqa: BLE001
        print(f"\nFailed to prepare launch: {exc}")
        return 1

    java = options.get("executablePath")
    if java:
        print(f"\nUsing Java: {java}")
    else:
        print(f"\nUsing Java: {command[0]}")
    print(f"Launching {version} as {options['username']} ({mode})")

    if args.print_command:
        print(subprocess.list2cmdline(command) if sys.platform == "win32" else " ".join(command))
        return 0

    completed = subprocess.run(command, cwd=directory, check=False)
    return completed.returncode


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Personal Minecraft CLI launcher (Java Edition)",
    )
    sub = parser.add_subparsers(dest="command", required=True)

    p_init = sub.add_parser("init", help="Create config.json from the example")
    p_init.set_defaults(func=cmd_init)

    p_versions = sub.add_parser("versions", help="List available Minecraft versions")
    p_versions.add_argument("--limit", type=int, default=30, help="How many to show (default 30)")
    p_versions.add_argument("--all", action="store_true", help="Show all (ignores --limit)")
    p_versions.add_argument("--release-only", action="store_true", help="Only release versions")
    p_versions.set_defaults(func=cmd_versions)

    p_installed = sub.add_parser("installed", help="List installed versions")
    p_installed.set_defaults(func=cmd_installed)

    p_install = sub.add_parser("install", help="Download and install a version")
    p_install.add_argument("version", help="Version id, or latest / latest-snapshot")
    p_install.add_argument("--fabric", action="store_true", help="Install Fabric for this Minecraft version")
    p_install.set_defaults(func=cmd_install)

    p_login = sub.add_parser("login", help="Log in with Microsoft account")
    p_login.set_defaults(func=cmd_login)

    p_logout = sub.add_parser("logout", help="Forget saved account")
    p_logout.set_defaults(func=cmd_logout)

    p_whoami = sub.add_parser("whoami", help="Show saved account")
    p_whoami.set_defaults(func=cmd_whoami)

    p_launch = sub.add_parser("launch", help="Launch Minecraft")
    p_launch.add_argument("version", help="Version id, or latest / latest-snapshot")
    p_launch.add_argument(
        "--fabric",
        action="store_true",
        help="Launch with Fabric for this Minecraft version",
    )
    p_launch.add_argument(
        "--offline",
        metavar="NAME",
        help="Launch offline with this username (singleplayer / offline servers)",
    )
    p_launch.add_argument(
        "--print-command",
        action="store_true",
        help="Print the Java command instead of running it",
    )
    p_launch.set_defaults(func=cmd_launch)

    return parser


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()
    if args.command == "versions" and args.all:
        args.limit = 0
    return args.func(args)


if __name__ == "__main__":
    raise SystemExit(main())
