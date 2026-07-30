# -*- mode: python ; coding: utf-8 -*-
"""PyInstaller spec for Deepslate Launcher."""

from pathlib import Path

from PyInstaller.utils.hooks import collect_all

block_cipher = None
project = Path(SPECPATH)

ctk_datas, ctk_binaries, ctk_hidden = collect_all("customtkinter")
mll_datas, mll_binaries, mll_hidden = collect_all("minecraft_launcher_lib")

a = Analysis(
    ["app.py"],
    pathex=[str(project)],
    binaries=ctk_binaries + mll_binaries,
    datas=[
        (str(project / "assets"), "assets"),
        (str(project / "config.example.json"), "."),
    ]
    + ctk_datas
    + mll_datas,
    hiddenimports=ctk_hidden + mll_hidden + ["PIL._tkinter_finder", "mods_catalog", "discord_rpc", "pypresence"],
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=[],
    win_no_prefer_redirects=False,
    win_private_assemblies=False,
    cipher=block_cipher,
    noarchive=False,
)

pyz = PYZ(a.pure, a.zipped_data, cipher=block_cipher)

exe = EXE(
    pyz,
    a.scripts,
    a.binaries,
    a.zipfiles,
    a.datas,
    [],
    name="DeepslateLauncher",
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=True,
    upx_exclude=[],
    runtime_tmpdir=None,
    console=False,
    disable_windowed_traceback=False,
    argv_emulation=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
    icon=str(project / "assets" / "icon.ico"),
)
