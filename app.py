#!/usr/bin/env python3
"""Personal Minecraft desktop launcher (CustomTkinter)."""

from __future__ import annotations

import os
import random
import subprocess
import sys
import threading
import traceback
from pathlib import Path
from typing import Any, Literal

import customtkinter as ctk
from PIL import Image, ImageDraw, ImageEnhance, ImageFilter, ImageFont

import core
import discord_rpc
import mods_catalog

APP_TITLE = "Deepslate Launcher"
ROOT = core.app_root()
RESOURCE_ROOT = core.resource_root()
BACKGROUNDS_DIR = RESOURCE_ROOT / "assets" / "backgrounds"
FONT_CANDIDATES = [
    RESOURCE_ROOT / "assets" / "fonts" / "Minecraftia.ttf",
    RESOURCE_ROOT / "assets" / "fonts" / "Minecraftia-Regular.ttf",
    RESOURCE_ROOT / "assets" / "fonts" / "minecraftia.ttf",
    RESOURCE_ROOT / "assets" / "fonts" / "MinecraftSans.otf",
    RESOURCE_ROOT / "assets" / "fonts" / "MinecraftSans.ttf",
    ROOT / "assets" / "fonts" / "Minecraftia.ttf",
    ROOT / "assets" / "fonts" / "Minecraftia-Regular.ttf",
]

TEXT = "#f2f4f0"
MUTED = "#c5cdc0"
ACCENT = "#6dbc45"
ACCENT_HOVER = "#82d45a"
ACCENT_BORDER = "#3f7a28"
MS_BLUE = "#3b82f6"
MS_BLUE_HOVER = "#60a5fa"
GLASS_BORDER = "#9aa89a"

LoginMode = Literal["microsoft", "cracked", "guest"]


def _resolve_font_family() -> str:
    import tkinter.font as tkfont

    for path in FONT_CANDIDATES:
        if path.is_file():
            try:
                ctk.FontManager.load_font(str(path))
            except Exception:  # noqa: BLE001
                continue
            name = path.stem
            if "minecraftia" in name.lower():
                return "Minecraftia"
            if "minecraft" in name.lower():
                return path.stem
            return name

    try:
        families = {f.lower(): f for f in tkfont.families()}
        for wanted in ("minecraftia", "minecraft evenings", "minecraft ten", "mojangles"):
            if wanted in families:
                return families[wanted]
    except Exception:  # noqa: BLE001
        pass
    return "Segoe UI"


def _pick_background() -> Path | None:
    if not BACKGROUNDS_DIR.is_dir():
        return None
    files = sorted(BACKGROUNDS_DIR.glob("*.png")) + sorted(BACKGROUNDS_DIR.glob("*.jpg"))
    return random.choice(files) if files else None


def _prepare_wallpaper(path: Path, size: tuple[int, int]) -> Image.Image:
    """Cover-fit wallpaper with a soft vignette so UI stays readable."""
    w, h = max(size[0], 640), max(size[1], 360)
    img = Image.open(path).convert("RGB")
    src_w, src_h = img.size
    scale = max(w / src_w, h / src_h)
    new_size = (max(1, int(src_w * scale)), max(1, int(src_h * scale)))
    img = img.resize(new_size, Image.Resampling.LANCZOS)
    left = (img.width - w) // 2
    top = (img.height - h) // 2
    img = img.crop((left, top, left + w, top + h))

    # Gentle darken + vignette for contrast with glass panels
    img = ImageEnhance.Brightness(img).enhance(0.78)
    vignette = Image.new("L", (w, h), 0)
    draw = ImageDraw.Draw(vignette)
    draw.ellipse((-w * 0.2, -h * 0.3, w * 1.2, h * 1.3), fill=255)
    vignette = vignette.filter(ImageFilter.GaussianBlur(radius=min(w, h) // 6))
    dark = Image.new("RGB", (w, h), (8, 12, 10))
    img = Image.composite(img, dark, vignette)
    return img


def _make_glass_texture(
    size: tuple[int, int],
    wallpaper: Image.Image | None,
    *,
    backdrop: Image.Image | None = None,
) -> Image.Image:
    """Frosted glass panel clipped to rounded corners — scenery shows through."""
    w, h = max(size[0], 8), max(size[1], 8)
    radius = max(22, min(w, h) // 14)

    if backdrop is not None and backdrop.size[0] > 1 and backdrop.size[1] > 1:
        clear = backdrop.convert("RGB").resize((w, h), Image.Resampling.LANCZOS)
    elif wallpaper is not None:
        clear = wallpaper.copy().resize((w, h), Image.Resampling.LANCZOS)
    else:
        clear = Image.new("RGB", (w, h), (32, 48, 40))

    # Strong frost so panels read as glass, not a black slab
    frosted = clear.filter(ImageFilter.GaussianBlur(radius=max(16, min(w, h) // 18)))
    frosted = ImageEnhance.Brightness(frosted).enhance(1.05)
    frosted = ImageEnhance.Color(frosted).enhance(1.15)
    mist = Image.new("RGB", (w, h), (120, 150, 130))
    frosted = Image.blend(frosted, mist, 0.22)
    highlight = Image.new("RGB", (w, h), (230, 240, 232))
    frosted = Image.blend(frosted, highlight, 0.10)

    # Soft top sheen
    sheen = Image.new("L", (w, h), 0)
    ImageDraw.Draw(sheen).rectangle((0, 0, w, max(8, h // 5)), fill=70)
    sheen = sheen.filter(ImageFilter.GaussianBlur(radius=max(8, h // 12)))
    frosted = Image.composite(
        ImageEnhance.Brightness(frosted).enhance(1.18),
        frosted,
        sheen,
    )

    outside = clear  # sharp wallpaper outside the rounded clip
    mask = Image.new("L", (w, h), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, w - 1, h - 1), radius=radius, fill=255)
    panel = Image.composite(frosted, outside, mask)

    draw = ImageDraw.Draw(panel)
    draw.rounded_rectangle(
        (1, 1, w - 2, h - 2),
        radius=max(1, radius - 1),
        outline=(235, 242, 235),
        width=2,
    )
    draw.rounded_rectangle(
        (4, 4, w - 5, h - 5),
        radius=max(1, radius - 4),
        outline=(180, 200, 185),
        width=1,
    )
    return panel.convert("RGBA")


class LauncherApp(ctk.CTk):
    def __init__(self) -> None:
        super().__init__()
        self.title(APP_TITLE)
        self._set_window_icon()
        ctk.set_appearance_mode("dark")
        ctk.set_default_color_theme("dark-blue")

        self.font_family = _resolve_font_family()
        self._font = lambda size, bold=False: ctk.CTkFont(
            family=self.font_family,
            size=size,
            weight="bold" if bold else "normal",
        )

        core.init_config()
        self.config_data = core.load_config()
        self._busy = False
        self._game_proc: Any = None
        self._progress_visible = False
        self._account_menu_open = False
        self._versions_loaded = False
        self._resize_after: str | None = None
        self._bg_path = _pick_background()
        self._wallpaper: Image.Image | None = None
        self._bg_image: ctk.CTkImage | None = None
        self._glass_images: dict[str, ctk.CTkImage] = {}
        self._version_art_images: dict[str, ctk.CTkImage] = {}
        self._version_buttons: list[ctk.CTkButton] = []
        self._version_groups: list[dict[str, Any]] = []
        self._server_buttons: list[ctk.CTkButton] = []
        self._mod_result_widgets: list[Any] = []
        self._mod_icon_images: dict[str, ctk.CTkImage] = {}
        self._page: Literal["play", "mods"] = "play"
        self._mod_source: mods_catalog.ModSource = "modrinth"
        self._mod_hits: list[mods_catalog.ModHit] = []
        self._mod_search_after: str | None = None
        self._selected_version = self.config_data.get("last_version") or "latest"
        loader, _mc = core.parse_version_ref(self._selected_version)
        self._mod_loader: core.ModLoaderName = loader if loader in ("vanilla", "fabric") else "vanilla"
        self._fabric_supported: set[str] = set()
        self._loader_choice_family: str | None = None
        self._version_dialog_open = False
        self._version_dialog_mode: Literal["browse"] = "browse"
        self._pending_server: dict[str, Any] | None = None

        mode = self.config_data.get("login_mode") or "cracked"
        self._login_mode: LoginMode = mode if mode in ("microsoft", "cracked", "guest") else "cracked"

        self._init_window_size()
        self._build_ui()
        self._apply_saved_account()
        # Undo any leftover guest mods isolation from a previous crash / force-quit
        try:
            core.restore_mods_stash()
        except Exception:  # noqa: BLE001
            pass
        self._apply_account_restrictions()
        self._load_versions_async()
        self._init_discord_rpc()

        self.protocol("WM_DELETE_WINDOW", self._on_close)
        self.bind("<Escape>", self._on_escape)
        self.bind("<Configure>", self._on_configure)
        self.after(50, self._refresh_background)
        self.after(1000, self._poll_game)

    def _set_window_icon(self) -> None:
        """Taskbar / title-bar / dock icon (deepslate block)."""
        icon_ico = RESOURCE_ROOT / "assets" / "icon.ico"
        icon_png = RESOURCE_ROOT / "assets" / "deepslate_block.png"
        if not icon_ico.is_file():
            icon_ico = ROOT / "assets" / "icon.ico"
        if not icon_png.is_file():
            icon_png = ROOT / "assets" / "deepslate_block.png"

        # .ico is mainly reliable on Windows
        if sys.platform == "win32":
            try:
                if icon_ico.is_file():
                    self.iconbitmap(default=str(icon_ico))
                    self.iconbitmap(str(icon_ico))
            except Exception:  # noqa: BLE001
                pass

        try:
            src = icon_png if icon_png.is_file() else None
            if src is None and icon_ico.is_file():
                src = icon_ico
            if src is None:
                return
            img = Image.open(src).convert("RGBA")
            side = min(img.size)
            left = (img.width - side) // 2
            top = (img.height - side) // 2
            img = img.crop((left, top, left + side, top + side)).resize(
                (256, 256), Image.Resampling.NEAREST
            )
            self._window_icon = ctk.CTkImage(light_image=img, dark_image=img, size=(256, 256))
            from PIL import ImageTk

            self._window_icon_photo = ImageTk.PhotoImage(img)
            self.iconphoto(True, self._window_icon_photo)
        except Exception:  # noqa: BLE001
            pass

    def _init_window_size(self) -> None:
        self.update_idletasks()
        sw = self.winfo_screenwidth()
        sh = self.winfo_screenheight()
        # Comfortable windowed size — not forced fullscreen (that was stretching the UI oddly)
        width = min(1360, max(1100, int(sw * 0.78)))
        height = min(800, max(680, int(sh * 0.78)))
        x = (sw - width) // 2
        y = max(20, (sh - height) // 2)
        self.geometry(f"{width}x{height}+{x}+{y}")
        self.minsize(1024, 640)
        self.configure(fg_color="#070a08")

    def _on_escape(self, _event: Any = None) -> None:
        if self.alert_card.winfo_ismapped():
            self._hide_alert()
            return
        if self.ms_login_card.winfo_ismapped():
            self._hide_ms_login_dialog()
            return
        if self._version_dialog_open:
            self._hide_version_dialog()
            return
        if self._account_menu_open:
            self._hide_account_menu()
            return
        try:
            if sys.platform == "win32":
                if str(self.state()) == "zoomed":
                    self.state("normal")
                    self._init_window_size()
                else:
                    self.state("zoomed")
            else:
                current = bool(self.attributes("-fullscreen"))
                self.attributes("-fullscreen", not current)
                if current:
                    self._init_window_size()
        except Exception:  # noqa: BLE001
            pass

    def _mc_button(
        self,
        master: Any,
        text: str,
        command: Any,
        *,
        fg: str = ACCENT,
        hover: str = ACCENT_HOVER,
        border: str = ACCENT_BORDER,
        height: int = 52,
        text_color: str = "#0b1208",
        font_size: int = 16,
    ) -> ctk.CTkButton:
        return ctk.CTkButton(
            master,
            text=text,
            command=command,
            height=height,
            corner_radius=8,
            border_width=2,
            border_color=border,
            fg_color=fg,
            hover_color=hover,
            text_color=text_color,
            font=self._font(font_size, bold=True),
        )

    def _build_ui(self) -> None:
        # Full-bleed wallpaper (tk Label so PhotoImage scaling is simple)
        self.bg_label = ctk.CTkLabel(self, text="", fg_color="#070a08")
        self.bg_label.place(x=0, y=0, relwidth=1, relheight=1)

        # Top bar
        self.top_bar = ctk.CTkFrame(self, fg_color="transparent")
        self.top_bar.place(relx=0, rely=0, relwidth=1, relheight=0.11)

        self.brand = ctk.CTkLabel(
            self.top_bar,
            text=APP_TITLE.upper(),
            font=self._font(26, bold=True),
            text_color=TEXT,
            fg_color="transparent",
        )
        self.brand.place(relx=0.04, rely=0.5, anchor="w")

        nav = ctk.CTkFrame(self.top_bar, fg_color="transparent")
        nav.place(relx=0.42, rely=0.5, anchor="center")
        self.nav_play_btn = ctk.CTkButton(
            nav,
            text="Play",
            width=90,
            height=34,
            corner_radius=8,
            font=self._font(13, bold=True),
            command=lambda: self._show_page("play"),
        )
        self.nav_play_btn.pack(side="left", padx=4)
        self.nav_mods_btn = ctk.CTkButton(
            nav,
            text="Mods",
            width=90,
            height=34,
            corner_radius=8,
            font=self._font(13, bold=True),
            command=self._on_nav_mods,
        )
        self.nav_mods_btn.pack(side="left", padx=4)

        self.account_btn = ctk.CTkButton(
            self.top_bar,
            text="Account  ▾",
            width=150,
            height=40,
            corner_radius=20,
            border_width=1,
            border_color=GLASS_BORDER,
            fg_color="#1a2420",
            hover_color="#27332d",
            text_color=TEXT,
            font=self._font(14, bold=True),
            command=self._toggle_account_menu,
        )
        self.account_btn.place(relx=0.96, rely=0.5, anchor="e")

        self.account_badge = ctk.CTkLabel(
            self.top_bar,
            text="",
            font=self._font(12),
            text_color=MUTED,
            fg_color="transparent",
        )
        self.account_badge.place(relx=0.96, rely=0.5, anchor="e", x=-165)

        # Centered play card (version chooser lives in the Play dialog)
        self.play_card = ctk.CTkFrame(self, fg_color="transparent", width=420, height=460)
        self.play_card.place(relx=0.5, rely=0.48, anchor="center")
        self.play_card.pack_propagate(False)

        self.play_glass = ctk.CTkLabel(self.play_card, text="", fg_color="transparent")
        self.play_glass.place(x=0, y=0, relwidth=1, relheight=1)

        self.play_inner = ctk.CTkFrame(self.play_card, fg_color="transparent")
        self.play_inner.place(relx=0.5, rely=0.5, anchor="center", relwidth=0.86, relheight=0.90)
        self.play_inner.lift()

        ctk.CTkLabel(
            self.play_inner,
            text="READY TO PLAY",
            font=self._font(18, bold=True),
            text_color=TEXT,
            fg_color="transparent",
        ).pack(anchor="w", pady=(4, 14))

        ctk.CTkLabel(
            self.play_inner, text="Username", anchor="w", font=self._font(12), text_color=MUTED, fg_color="transparent"
        ).pack(fill="x")
        self.username = ctk.CTkEntry(
            self.play_inner,
            placeholder_text="Offline username",
            height=40,
            font=self._font(15),
            fg_color="#2a3a32",
            border_color="#9ab8a0",
            text_color=TEXT,
            corner_radius=8,
        )
        self.username.pack(fill="x", pady=(4, 12))

        ctk.CTkLabel(
            self.play_inner, text="Version", anchor="w", font=self._font(12), text_color=MUTED, fg_color="transparent"
        ).pack(fill="x")
        self.version_label = ctk.CTkLabel(
            self.play_inner,
            text=core.display_version_label(self._selected_version),
            anchor="w",
            font=self._font(15, bold=True),
            text_color=TEXT,
            fg_color="transparent",
        )
        self.version_label.pack(fill="x", pady=(2, 12))

        ram_row = ctk.CTkFrame(self.play_inner, fg_color="transparent")
        ram_row.pack(fill="x")
        ctk.CTkLabel(ram_row, text="RAM", font=self._font(12), text_color=MUTED, fg_color="transparent").pack(
            side="left"
        )
        self.ram_label = ctk.CTkLabel(ram_row, text="4 GB", font=self._font(12), text_color=TEXT, fg_color="transparent")
        self.ram_label.pack(side="right")

        self.ram = ctk.CTkSlider(
            self.play_inner,
            from_=1,
            to=16,
            number_of_steps=15,
            command=self._on_ram,
            progress_color=ACCENT,
            button_color="#d7f5c4",
            button_hover_color="#ffffff",
            fg_color="#4d5c55",
        )
        self.ram.pack(fill="x", pady=(6, 10))
        self.ram.set(core.parse_ram_gb(self.config_data.get("jvm_arguments")))
        self._on_ram(self.ram.get())

        keep_row = ctk.CTkFrame(self.play_inner, fg_color="transparent")
        keep_row.pack(fill="x", pady=(0, 10))
        ctk.CTkLabel(
            keep_row,
            text="Keep launcher open",
            font=self._font(12),
            text_color=MUTED,
            fg_color="transparent",
        ).pack(side="left")
        self.keep_open = ctk.CTkSwitch(
            keep_row,
            text="",
            width=48,
            progress_color=ACCENT,
            button_color="#e8f5e0",
            button_hover_color="#ffffff",
            fg_color="#3a4540",
            command=self._on_keep_open_toggle,
        )
        self.keep_open.pack(side="right")
        if self.config_data.get("keep_launcher_open", True):
            self.keep_open.select()
        else:
            self.keep_open.deselect()

        play_row = ctk.CTkFrame(self.play_inner, fg_color="transparent")
        play_row.pack(fill="x", pady=(4, 8))
        self.play = self._mc_button(play_row, "PLAY", self._on_play, height=54, font_size=20)
        self.play.pack(side="left", fill="x", expand=True, padx=(0, 6))
        self.play_menu = ctk.CTkButton(
            play_row,
            text="▾",
            width=54,
            height=54,
            corner_radius=8,
            border_width=2,
            border_color=ACCENT_BORDER,
            fg_color=ACCENT,
            hover_color=ACCENT_HOVER,
            text_color="#0b1208",
            font=self._font(22, bold=True),
            command=self._open_version_menu,
        )
        self.play_menu.pack(side="right")

        self.progress_wrap = ctk.CTkFrame(self.play_inner, fg_color="transparent")
        self.progress = ctk.CTkProgressBar(
            self.progress_wrap,
            progress_color=ACCENT,
            fg_color="#0b100d",
            height=12,
            corner_radius=6,
        )
        self.progress.pack(fill="x")
        self.progress.set(0)

        self.status = ctk.CTkLabel(
            self.play_inner,
            text="",
            anchor="w",
            font=self._font(12),
            text_color=MUTED,
            fg_color="transparent",
        )
        self._status_visible = False

        # Version chooser dialog (opened from PLAY / selected version)
        self.version_dim = ctk.CTkLabel(self, text="", fg_color="#000000")
        self._version_dim_image: ctk.CTkImage | None = None

        self.version_card = ctk.CTkFrame(self, fg_color="transparent", width=620, height=560)
        self.version_card.pack_propagate(False)

        self.version_glass = ctk.CTkLabel(self.version_card, text="", fg_color="transparent")
        self.version_glass.place(x=0, y=0, relwidth=1, relheight=1)

        version_inner = ctk.CTkFrame(self.version_card, fg_color="transparent")
        version_inner.place(relx=0.5, rely=0.5, anchor="center", relwidth=0.92, relheight=0.92)

        hdr = ctk.CTkFrame(version_inner, fg_color="transparent")
        hdr.pack(fill="x", pady=(0, 8))
        ctk.CTkLabel(
            hdr,
            text="CHOOSE VERSION",
            font=self._font(16, bold=True),
            text_color=TEXT,
            fg_color="transparent",
        ).pack(side="left")
        self.version_dialog_close = ctk.CTkButton(
            hdr,
            text="✕",
            width=36,
            height=32,
            corner_radius=8,
            fg_color="#2a3530",
            hover_color="#3a453e",
            text_color=TEXT,
            font=self._font(14, bold=True),
            command=self._hide_version_dialog,
        )
        self.version_dialog_close.pack(side="right")

        self.version_scroll = ctk.CTkScrollableFrame(
            version_inner,
            fg_color="#24352e",
            corner_radius=12,
            border_width=1,
            border_color="#8aa090",
        )
        self.version_scroll.pack(fill="both", expand=True, pady=(0, 0))
        self.version_loading = ctk.CTkLabel(
            self.version_scroll,
            text="Loading versions…",
            font=self._font(13),
            text_color=MUTED,
            fg_color="transparent",
        )
        self.version_loading.pack(pady=20)

        # Bottom: servers strip (saved by the user)
        self.servers_card = ctk.CTkFrame(self, fg_color="transparent", width=1120, height=150)
        self.servers_card.place(relx=0.5, rely=0.905, anchor="center")
        self.servers_card.pack_propagate(False)

        self.servers_glass = ctk.CTkLabel(self.servers_card, text="", fg_color="transparent")
        self.servers_glass.place(x=0, y=0, relwidth=1, relheight=1)

        servers_inner = ctk.CTkFrame(self.servers_card, fg_color="transparent")
        servers_inner.place(relx=0.5, rely=0.5, anchor="center", relwidth=0.96, relheight=0.88)

        ctk.CTkLabel(
            servers_inner,
            text="SERVERS",
            font=self._font(13, bold=True),
            text_color=TEXT,
            fg_color="transparent",
        ).pack(anchor="w")

        add_row = ctk.CTkFrame(servers_inner, fg_color="transparent")
        add_row.pack(fill="x", pady=(4, 4))
        self.server_name = ctk.CTkEntry(add_row, placeholder_text="Name", width=140, height=32, font=self._font(12))
        self.server_name.pack(side="left", padx=(0, 6))
        self.server_address = ctk.CTkEntry(
            add_row, placeholder_text="Address (play.example.com)", width=260, height=32, font=self._font(12)
        )
        self.server_address.pack(side="left", padx=(0, 6))
        self.server_port = ctk.CTkEntry(add_row, placeholder_text="Port", width=70, height=32, font=self._font(12))
        self.server_port.insert(0, "25565")
        self.server_port.pack(side="left", padx=(0, 6))
        ctk.CTkButton(
            add_row,
            text="Save",
            width=70,
            height=32,
            fg_color=ACCENT,
            hover_color=ACCENT_HOVER,
            text_color="#0b1208",
            font=self._font(12, bold=True),
            command=self._on_save_server,
        ).pack(side="left", padx=(0, 6))

        self.servers_row = ctk.CTkFrame(servers_inner, fg_color="transparent")
        self.servers_row.pack(fill="both", expand=True, pady=(2, 0))
        self._build_server_buttons()

        # Account dropdown (hidden until Account is pressed)
        self.account_menu = ctk.CTkFrame(self, fg_color="transparent", width=280, height=290)
        self.account_menu_glass = ctk.CTkLabel(self.account_menu, text="", fg_color="transparent")
        self.account_menu_glass.place(x=0, y=0, relwidth=1, relheight=1)

        menu_inner = ctk.CTkFrame(self.account_menu, fg_color="transparent")
        menu_inner.place(relx=0.5, rely=0.5, anchor="center", relwidth=0.88, relheight=0.88)

        ctk.CTkLabel(
            menu_inner,
            text="SIGN IN",
            font=self._font(14, bold=True),
            text_color=TEXT,
            fg_color="transparent",
        ).pack(anchor="w", pady=(0, 12))

        self._mc_button(
            menu_inner,
            "Microsoft Account",
            self._login_microsoft,
            fg=MS_BLUE,
            hover=MS_BLUE_HOVER,
            border="#1d4bb8",
            text_color="#ffffff",
            height=44,
            font_size=13,
        ).pack(fill="x", pady=5)

        self._mc_button(
            menu_inner,
            "Cracked Account",
            self._login_cracked,
            height=44,
            font_size=13,
        ).pack(fill="x", pady=5)

        self._mc_button(
            menu_inner,
            "Continue as Guest",
            self._login_guest,
            fg="#2a322e",
            hover="#3a453e",
            border="#6d7c6d",
            text_color=TEXT,
            height=44,
            font_size=13,
        ).pack(fill="x", pady=5)

        self.login_status = ctk.CTkLabel(
            menu_inner,
            text="",
            font=self._font(11),
            text_color=MUTED,
            wraplength=220,
            justify="left",
            fg_color="transparent",
        )
        self.login_status.pack(anchor="w", pady=(12, 0))

        # Microsoft paste-login dialog (address-bar URL)
        self._ms_pending: dict[str, str] | None = None
        self.ms_login_card = ctk.CTkFrame(self, fg_color="transparent", width=560, height=340)
        self.ms_login_card.pack_propagate(False)
        self.ms_login_glass = ctk.CTkLabel(self.ms_login_card, text="", fg_color="transparent")
        self.ms_login_glass.place(x=0, y=0, relwidth=1, relheight=1)
        ms_inner = ctk.CTkFrame(self.ms_login_card, fg_color="transparent")
        ms_inner.place(relx=0.5, rely=0.5, anchor="center", relwidth=0.9, relheight=0.88)
        ctk.CTkLabel(
            ms_inner,
            text="MICROSOFT LOGIN",
            font=self._font(16, bold=True),
            text_color=TEXT,
            fg_color="transparent",
        ).pack(anchor="w")
        ctk.CTkLabel(
            ms_inner,
            text=(
                "1. Sign in in the browser (the next page may look wrong — ignore it)\n"
                "2. Copy the FULL URL from the address bar (must contain code=)\n"
                "3. Paste it below and press Finish"
            ),
            font=self._font(12),
            text_color=MUTED,
            justify="left",
            anchor="w",
            fg_color="transparent",
        ).pack(fill="x", pady=(8, 10))
        self.ms_url_entry = ctk.CTkEntry(
            ms_inner,
            placeholder_text="https://login.microsoftonline.com/common/oauth2/nativeclient?code=...",
            height=40,
            font=self._font(12),
            fg_color="#2a3a32",
            border_color="#9ab8a0",
            text_color=TEXT,
        )
        self.ms_url_entry.pack(fill="x", pady=(0, 10))
        ms_btns = ctk.CTkFrame(ms_inner, fg_color="transparent")
        ms_btns.pack(fill="x")
        ctk.CTkButton(
            ms_btns,
            text="Open browser again",
            height=36,
            fg_color="#2a3530",
            hover_color="#3a453e",
            text_color=TEXT,
            font=self._font(12, bold=True),
            command=self._ms_reopen_browser,
        ).pack(side="left")
        ctk.CTkButton(
            ms_btns,
            text="Cancel",
            width=90,
            height=36,
            fg_color="#2a3530",
            hover_color="#3a453e",
            text_color=TEXT,
            font=self._font(12, bold=True),
            command=self._hide_ms_login_dialog,
        ).pack(side="right", padx=(6, 0))
        ctk.CTkButton(
            ms_btns,
            text="Finish login",
            width=120,
            height=36,
            fg_color=MS_BLUE,
            hover_color=MS_BLUE_HOVER,
            text_color="#ffffff",
            font=self._font(12, bold=True),
            command=self._ms_finish_login,
        ).pack(side="right")
        self.ms_login_status = ctk.CTkLabel(
            ms_inner,
            text="",
            font=self._font(11),
            text_color="#f0c24b",
            wraplength=480,
            justify="left",
            anchor="w",
            fg_color="transparent",
        )
        self.ms_login_status.pack(fill="x", pady=(12, 0))

        # Centered alert popup (errors / info)
        self.alert_dim = ctk.CTkLabel(self, text="", fg_color="#000000")
        self._alert_dim_image: ctk.CTkImage | None = None
        self.alert_card = ctk.CTkFrame(self, fg_color="transparent", width=520, height=280)
        self.alert_card.pack_propagate(False)
        self.alert_glass = ctk.CTkLabel(self.alert_card, text="", fg_color="transparent")
        self.alert_glass.place(x=0, y=0, relwidth=1, relheight=1)
        alert_inner = ctk.CTkFrame(self.alert_card, fg_color="transparent")
        alert_inner.place(relx=0.5, rely=0.5, anchor="center", relwidth=0.88, relheight=0.86)
        self.alert_title = ctk.CTkLabel(
            alert_inner,
            text="Something went wrong",
            font=self._font(18, bold=True),
            text_color=TEXT,
            anchor="w",
            fg_color="transparent",
        )
        self.alert_title.pack(fill="x", pady=(0, 10))
        self.alert_body = ctk.CTkTextbox(
            alert_inner,
            height=140,
            font=self._font(13),
            fg_color="#24352e",
            text_color=TEXT,
            border_width=1,
            border_color="#8aa090",
            corner_radius=10,
            wrap="word",
            activate_scrollbars=True,
        )
        self.alert_body.pack(fill="both", expand=True, pady=(0, 12))
        self.alert_body.configure(state="disabled")
        self.alert_ok = ctk.CTkButton(
            alert_inner,
            text="OK",
            height=40,
            fg_color=ACCENT,
            hover_color=ACCENT_HOVER,
            text_color="#0b1208",
            font=self._font(14, bold=True),
            command=self._hide_alert,
        )
        self.alert_ok.pack(fill="x")

        # Mods page (hidden until Mods nav is pressed)
        self.mods_card = ctk.CTkFrame(self, fg_color="transparent", width=1120, height=620)
        self.mods_card.pack_propagate(False)
        self.mods_glass = ctk.CTkLabel(self.mods_card, text="", fg_color="transparent")
        self.mods_glass.place(x=0, y=0, relwidth=1, relheight=1)

        mods_inner = ctk.CTkFrame(self.mods_card, fg_color="transparent")
        mods_inner.place(relx=0.5, rely=0.5, anchor="center", relwidth=0.94, relheight=0.92)

        header = ctk.CTkFrame(mods_inner, fg_color="transparent")
        header.pack(fill="x", pady=(0, 8))
        ctk.CTkLabel(
            header,
            text="MODS",
            font=self._font(18, bold=True),
            text_color=TEXT,
            fg_color="transparent",
        ).pack(side="left")

        self.mods_context = ctk.CTkLabel(
            header,
            text="",
            font=self._font(12),
            text_color=MUTED,
            fg_color="transparent",
        )
        self.mods_context.pack(side="right")

        source_row = ctk.CTkFrame(mods_inner, fg_color="transparent")
        source_row.pack(fill="x", pady=(0, 8))
        self.mod_src_modrinth = ctk.CTkButton(
            source_row,
            text="Modrinth",
            width=110,
            height=32,
            corner_radius=8,
            font=self._font(12, bold=True),
            command=lambda: self._set_mod_source("modrinth"),
        )
        self.mod_src_modrinth.pack(side="left", padx=(0, 6))
        self.mod_src_curseforge = ctk.CTkButton(
            source_row,
            text="CurseForge",
            width=120,
            height=32,
            corner_radius=8,
            font=self._font(12, bold=True),
            command=lambda: self._set_mod_source("curseforge"),
        )
        self.mod_src_curseforge.pack(side="left", padx=(0, 6))
        ctk.CTkButton(
            source_row,
            text="Open mods folder",
            width=150,
            height=32,
            corner_radius=8,
            fg_color="#2a3530",
            hover_color="#3a453e",
            border_width=1,
            border_color="#5a6a62",
            text_color=TEXT,
            font=self._font(12, bold=True),
            command=self._open_mods_folder,
        ).pack(side="right")

        search_row = ctk.CTkFrame(mods_inner, fg_color="transparent")
        search_row.pack(fill="x", pady=(0, 8))
        self.mod_search = ctk.CTkEntry(
            search_row,
            placeholder_text="Search mods…",
            height=38,
            font=self._font(14),
            fg_color="#121a16",
            border_color="#6d7c6d",
            text_color=TEXT,
            corner_radius=8,
        )
        self.mod_search.pack(side="left", fill="x", expand=True, padx=(0, 8))
        self.mod_search.bind("<Return>", lambda _e: self._search_mods())
        self.mod_search.bind("<KeyRelease>", self._on_mod_search_typed)
        ctk.CTkButton(
            search_row,
            text="Search",
            width=100,
            height=38,
            fg_color=ACCENT,
            hover_color=ACCENT_HOVER,
            text_color="#0b1208",
            font=self._font(13, bold=True),
            command=self._search_mods,
        ).pack(side="left")

        self.mods_status = ctk.CTkLabel(
            mods_inner,
            text="Search Modrinth for mods matching your selected version.",
            anchor="w",
            font=self._font(12),
            text_color=MUTED,
            fg_color="transparent",
        )
        self.mods_status.pack(fill="x", pady=(0, 6))

        self.mods_scroll = ctk.CTkScrollableFrame(
            mods_inner,
            fg_color="#24352e",
            corner_radius=12,
            border_width=1,
            border_color="#8aa090",
        )
        self.mods_scroll.pack(fill="both", expand=True)

        self._refresh_nav_style()
        self._refresh_mod_source_style()
        self._update_mods_context()

    def _is_microsoft(self) -> bool:
        return self._login_mode == "microsoft"

    def _can_use_fabric_mods(self) -> bool:
        """Mods catalog / community jars — Microsoft only."""
        return self._is_microsoft()

    def _can_use_fabric_loader(self) -> bool:
        """Fabric loader allowed for Microsoft (full) and Guest (lock mod only)."""
        return self._login_mode in ("microsoft", "guest")

    def _guest_uses_fabric(self) -> bool:
        return self._login_mode == "guest"

    def _guest_allowed_versions(self) -> set[str]:
        return set(core.guest_lock_versions())

    def _version_groups_visible(self) -> list[dict[str, Any]]:
        groups = getattr(self, "_version_groups", []) or []
        if not self._guest_uses_fabric():
            return groups
        return core.filter_version_groups(groups, self._guest_allowed_versions())

    def _clamp_guest_version(self, *, save: bool = True) -> None:
        """Force the selected version onto one that has a guest-lock jar."""
        if not self._guest_uses_fabric():
            return
        allowed = core.guest_lock_versions()
        if not allowed:
            return
        allowed_set = set(allowed)
        _, mc = core.parse_version_ref(self._selected_version)
        if mc in allowed_set:
            self._force_fabric_selection(save=save)
            return
        pick = allowed[0]
        try:
            latest = core.get_latest_versions().get("release")
            if latest in allowed_set:
                pick = latest
        except Exception:  # noqa: BLE001
            pass
        self._mod_loader = "fabric"
        self._selected_version = core.make_version_ref("fabric", pick)
        if hasattr(self, "version_label"):
            self.version_label.configure(text=core.display_version_label(self._selected_version))
        self.config_data["mod_loader"] = "fabric"
        self.config_data["last_version"] = self._selected_version
        if save:
            core.save_config(self.config_data)

    def _can_use_multiplayer(self) -> bool:
        """Guests are singleplayer-only from the launcher."""
        return self._login_mode != "guest"

    def _force_vanilla_selection(self, *, save: bool = True) -> None:
        loader, mc = core.parse_version_ref(self._selected_version)
        if loader == "vanilla":
            self._mod_loader = "vanilla"
            return
        self._mod_loader = "vanilla"
        self._selected_version = core.make_version_ref("vanilla", mc)
        if hasattr(self, "version_label"):
            self.version_label.configure(text=core.display_version_label(self._selected_version))
        self.config_data["mod_loader"] = "vanilla"
        self.config_data["last_version"] = self._selected_version
        if save:
            core.save_config(self.config_data)
        self._update_mods_context()

    def _force_fabric_selection(self, *, save: bool = True) -> None:
        loader, mc = core.parse_version_ref(self._selected_version)
        if loader == "fabric":
            self._mod_loader = "fabric"
            return
        self._mod_loader = "fabric"
        self._selected_version = core.make_version_ref("fabric", mc)
        if hasattr(self, "version_label"):
            self.version_label.configure(text=core.display_version_label(self._selected_version))
        self.config_data["mod_loader"] = "fabric"
        self.config_data["last_version"] = self._selected_version
        if save:
            core.save_config(self.config_data)
        self._update_mods_context()

    def _place_servers_card(self) -> None:
        if self._page != "play" or not self._can_use_multiplayer():
            self.servers_card.place_forget()
            return
        self.servers_card.place(relx=0.5, rely=0.905, anchor="center")
        self.after(40, lambda: self._paint_glass(self.servers_card, self.servers_glass, "servers"))

    def _apply_account_restrictions(self) -> None:
        """Microsoft: Fabric + mods. Cracked: vanilla. Guest: Fabric loader + lock mod, singleplayer."""
        if self._guest_uses_fabric():
            self._clamp_guest_version(save=True)
        elif not self._can_use_fabric_loader():
            self._force_vanilla_selection(save=True)
        if not self._can_use_fabric_mods() and self._page == "mods":
            self._show_page("play")
        if not self._can_use_multiplayer():
            self._pending_server = None
        self._refresh_nav_style()
        self._place_servers_card()
        self._update_mods_context()
        self._sync_discord_rpc()
        if self._version_dialog_open and self._versions_loaded:
            self._show_major_versions()

    def _on_nav_mods(self) -> None:
        if not self._can_use_fabric_mods():
            self._show_alert(
                "The Mods catalog needs a Microsoft account.\n"
                "Guests only get the Fabric loader + singleplayer lock mod.",
                title="Microsoft required",
                kind="info",
            )
            return
        self._show_page("mods")

    def _apply_saved_account(self) -> None:
        if self._login_mode == "guest":
            self.username.configure(state="normal")
            self.username.delete(0, "end")
            self.username.insert(0, "Guest")
            self.username.configure(state="disabled")
        elif self._login_mode == "microsoft":
            account = core.load_account()
            name = (account or {}).get("name") or self.config_data.get("offline_username") or "Player"
            self.username.configure(state="normal")
            self.username.delete(0, "end")
            self.username.insert(0, name)
            self.username.configure(state="disabled")
        else:
            self._login_mode = "cracked"
            self.username.configure(state="normal")
            self.username.delete(0, "end")
            self.username.insert(0, self.config_data.get("offline_username") or "Player")
        self._update_account_badge()

    def _on_configure(self, event: Any) -> None:
        if event.widget is not self:
            return
        if self._resize_after is not None:
            self.after_cancel(self._resize_after)
        self._resize_after = self.after(80, self._refresh_background)

    def _refresh_background(self) -> None:
        self._resize_after = None
        self.update_idletasks()
        w = max(self.winfo_width(), 640)
        h = max(self.winfo_height(), 360)

        if self._bg_path and self._bg_path.is_file():
            self._wallpaper = _prepare_wallpaper(self._bg_path, (w, h))
        else:
            self._wallpaper = Image.new("RGB", (w, h), (10, 14, 12))

        self._bg_image = ctk.CTkImage(light_image=self._wallpaper, dark_image=self._wallpaper, size=(w, h))
        self.bg_label.configure(image=self._bg_image)

        self._paint_glass(self.play_card, self.play_glass, "play")
        if self._page == "play" and self._can_use_multiplayer() and self.servers_card.winfo_ismapped():
            self._paint_glass(self.servers_card, self.servers_glass, "servers")
        if self._page == "mods":
            self._paint_glass(self.mods_card, self.mods_glass, "mods")
        if self._version_dialog_open:
            self._paint_version_dim()
            self._paint_glass(self.version_card, self.version_glass, "versions")
        if self._account_menu_open:
            self._paint_glass(self.account_menu, self.account_menu_glass, "menu")
            self._place_account_menu()
        if self.ms_login_card.winfo_ismapped():
            self._paint_glass(self.ms_login_card, self.ms_login_glass, "mslogin")
        if self.alert_card.winfo_ismapped():
            self._paint_alert_dim()
            self._paint_glass(self.alert_card, self.alert_glass, "alert")

    def _paint_glass(self, host: ctk.CTkFrame, label: ctk.CTkLabel, key: str) -> None:
        host.update_idletasks()
        w = max(host.winfo_width(), 100)
        h = max(host.winfo_height(), 100)
        # Fixed-size cards report 1x1 until mapped — fall back to requested size
        if w < 80 or h < 80:
            try:
                w = max(w, int(host.cget("width") or 100))
                h = max(h, int(host.cget("height") or 100))
            except Exception:  # noqa: BLE001
                w, h = max(w, 100), max(h, 100)

        backdrop: Image.Image | None = None
        if self._wallpaper is not None:
            try:
                x = max(0, int(host.winfo_rootx() - self.winfo_rootx()))
                y = max(0, int(host.winfo_rooty() - self.winfo_rooty()))
                ww, wh = self._wallpaper.size
                x2 = min(ww, x + w)
                y2 = min(wh, y + h)
                if x2 > x and y2 > y:
                    backdrop = self._wallpaper.crop((x, y, x2, y2))
            except Exception:  # noqa: BLE001
                backdrop = None

        texture = _make_glass_texture((w, h), self._wallpaper, backdrop=backdrop)
        image = ctk.CTkImage(light_image=texture, dark_image=texture, size=(w, h))
        self._glass_images[key] = image
        label.configure(image=image, fg_color="transparent")
        label.place(x=0, y=0, relwidth=1, relheight=1)
        label.lower()
        # Keep interactive content above the glass plate
        for child in host.winfo_children():
            if child is not label:
                try:
                    child.lift()
                except Exception:  # noqa: BLE001
                    pass

    def _toggle_account_menu(self) -> None:
        if self._account_menu_open:
            self._hide_account_menu()
        else:
            self._show_account_menu()

    def _show_account_menu(self) -> None:
        self._account_menu_open = True
        self.login_status.configure(text="")
        self.account_btn.configure(text="Account  ▴")
        self._place_account_menu()
        self._paint_glass(self.account_menu, self.account_menu_glass, "menu")
        self.account_menu.lift()

    def _place_account_menu(self) -> None:
        # Anchored under the top-right Account button
        self.account_menu.place(relx=0.96, rely=0.11, anchor="ne")

    def _hide_account_menu(self) -> None:
        self._account_menu_open = False
        self.account_menu.place_forget()
        self.account_btn.configure(text="Account  ▾")

    def _update_account_badge(self) -> None:
        mode = self._login_mode or "cracked"
        name = self.username.get().strip() or "—"
        labels = {
            "microsoft": f"Microsoft · {name}",
            "cracked": f"Cracked · {name}",
            "guest": f"Guest · {name}",
        }
        self.account_badge.configure(text=labels.get(mode, name))
        self.account_btn.configure(text="Account  ▾" if not self._account_menu_open else "Account  ▴")

    def _paint_alert_dim(self) -> None:
        self.update_idletasks()
        w = max(self.winfo_width(), 640)
        h = max(self.winfo_height(), 360)
        if self._wallpaper is not None:
            base = self._wallpaper.resize((w, h), Image.Resampling.LANCZOS).convert("RGB")
        else:
            base = Image.new("RGB", (w, h), (8, 12, 10))
        dim = Image.blend(base, Image.new("RGB", (w, h), (0, 0, 0)), 0.6)
        img = ctk.CTkImage(light_image=dim, dark_image=dim, size=(w, h))
        self._alert_dim_image = img
        self.alert_dim.configure(image=img)

    def _show_alert(self, message: str, *, title: str = "Something went wrong", kind: str = "error") -> None:
        """Show a centered glass popup with a clear message."""
        titles = {
            "error": title or "Something went wrong",
            "info": title if title != "Something went wrong" else "Heads up",
            "success": title if title != "Something went wrong" else "Done",
        }
        self.alert_title.configure(text=titles.get(kind, title))
        self.alert_body.configure(state="normal")
        self.alert_body.delete("1.0", "end")
        self.alert_body.insert("1.0", (message or "").strip() or "Unknown error.")
        self.alert_body.configure(state="disabled")
        if kind == "success":
            self.alert_ok.configure(fg_color=ACCENT, hover_color=ACCENT_HOVER, text_color="#0b1208")
        elif kind == "info":
            self.alert_ok.configure(fg_color=MS_BLUE, hover_color=MS_BLUE_HOVER, text_color="#ffffff")
        else:
            self.alert_ok.configure(fg_color="#c45c4a", hover_color="#d47363", text_color="#ffffff")
        self._paint_alert_dim()
        self.alert_dim.place(x=0, y=0, relwidth=1, relheight=1)
        self.alert_card.place(relx=0.5, rely=0.48, anchor="center")
        self._paint_glass(self.alert_card, self.alert_glass, "alert")
        self.alert_dim.lift()
        self.alert_card.lift()

    def _hide_alert(self) -> None:
        self.alert_card.place_forget()
        self.alert_dim.place_forget()

    def _show_error(self, message: str, *, title: str = "Something went wrong") -> None:
        self._show_alert(message, title=title, kind="error")

    def _login_microsoft(self) -> None:
        self._login_mode = "microsoft"
        account = core.load_account()
        if account and account.get("name") and account.get("refresh_token"):
            self.username.configure(state="normal")
            self.username.delete(0, "end")
            self.username.insert(0, account["name"])
            self.username.configure(state="disabled")
            self.config_data["login_mode"] = "microsoft"
            core.save_config(self.config_data)
            self._hide_account_menu()
            self._update_account_badge()
            self._set_status(f"Signed in as {account['name']} (Microsoft).")
            return

        self.config_data["redirect_uri"] = core.NATIVE_CLIENT_REDIRECT
        core.save_config(self.config_data)

        try:
            login_url, state, code_verifier, redirect = core.microsoft_login_begin(
                self.config_data, redirect_uri=core.NATIVE_CLIENT_REDIRECT
            )
        except Exception as exc:  # noqa: BLE001
            self._show_error(str(exc), title="Microsoft login")
            return

        self._ms_pending = {
            "login_url": login_url,
            "state": state,
            "code_verifier": code_verifier,
            "redirect": redirect,
        }
        import webbrowser

        webbrowser.open(login_url)
        self._hide_account_menu()
        self._show_ms_login_dialog()

    def _show_ms_login_dialog(self) -> None:
        self.ms_url_entry.delete(0, "end")
        self.ms_login_status.configure(text="")
        self.ms_login_card.place(relx=0.5, rely=0.48, anchor="center")
        self._paint_glass(self.ms_login_card, self.ms_login_glass, "mslogin")
        self.ms_login_card.lift()

    def _hide_ms_login_dialog(self) -> None:
        self.ms_login_card.place_forget()
        self._ms_pending = None

    def _ms_reopen_browser(self) -> None:
        if not self._ms_pending:
            return
        import webbrowser

        webbrowser.open(self._ms_pending["login_url"])

    def _ms_finish_login(self) -> None:
        pending = self._ms_pending
        if not pending:
            self._show_error("Start Microsoft login again from Account → Microsoft Account.", title="Login expired")
            return
        code_url = self.ms_url_entry.get().strip()
        if not code_url:
            self._show_error(
                "Paste the full URL from your browser address bar first.\n\n"
                "It should look like:\n"
                "https://login.microsoftonline.com/…/nativeclient?code=…",
                title="Missing login URL",
            )
            return
        self.ms_login_status.configure(text="Finishing login…", text_color=MUTED)

        def work() -> None:
            try:
                account = core.microsoft_login_finish(
                    code_url,
                    pending["state"],
                    pending["code_verifier"],
                    self.config_data,
                    redirect_uri=pending["redirect"],
                )

                def done() -> None:
                    self._login_mode = "microsoft"
                    self.config_data["login_mode"] = "microsoft"
                    core.save_config(self.config_data)
                    try:
                        core.restore_mods_stash()
                    except Exception:  # noqa: BLE001
                        pass
                    self.username.configure(state="normal")
                    self.username.delete(0, "end")
                    self.username.insert(0, account["name"])
                    self.username.configure(state="disabled")
                    self._hide_ms_login_dialog()
                    self._update_account_badge()
                    self._apply_account_restrictions()
                    self._set_status(f"Signed in as {account['name']}.")
                    self._show_alert(
                        f"Signed in as {account['name']}.\nYou can play online now.",
                        title="Microsoft login",
                        kind="success",
                    )

                self.after(0, done)
            except Exception as exc:  # noqa: BLE001
                err = str(exc)
                self.after(
                    0,
                    lambda: (
                        self.ms_login_status.configure(text="Login failed — see popup.", text_color="#f0c24b"),
                        self._show_error(err, title="Microsoft login failed"),
                    ),
                )

        threading.Thread(target=work, daemon=True).start()

    def _login_cracked(self) -> None:
        self._login_mode = "cracked"
        self.config_data["login_mode"] = "cracked"
        core.save_config(self.config_data)
        try:
            core.restore_mods_stash()
        except Exception:  # noqa: BLE001
            pass
        self.username.configure(state="normal")
        self.username.delete(0, "end")
        self.username.insert(0, self.config_data.get("offline_username") or "Player")
        self._hide_account_menu()
        self._update_account_badge()
        self._apply_account_restrictions()
        self._set_status("Cracked — vanilla only. Sign in with Microsoft for Fabric & mods.")

    def _login_guest(self) -> None:
        self._login_mode = "guest"
        self.config_data["login_mode"] = "guest"
        core.save_config(self.config_data)
        self.username.configure(state="normal")
        self.username.delete(0, "end")
        self.username.insert(0, "Guest")
        self.username.configure(state="disabled")
        self._hide_account_menu()
        self._update_account_badge()
        self._apply_account_restrictions()
        self._set_status("Guest — Fabric 1.20.1–1.21.x + singleplayer lock only.")

    def _show_progress(self) -> None:
        if not self._progress_visible:
            if self._status_visible:
                self.progress_wrap.pack(fill="x", pady=(4, 0), before=self.status)
            else:
                self.progress_wrap.pack(fill="x", pady=(4, 0))
            self._progress_visible = True
        self.progress.set(0)

    def _hide_progress(self) -> None:
        if self._progress_visible:
            self.progress_wrap.pack_forget()
            self._progress_visible = False
        self.progress.set(0)

    def _on_ram(self, value: float) -> None:
        self.ram_label.configure(text=f"{int(round(float(value)))} GB")

    def _set_status(self, text: str) -> None:
        text = (text or "").strip()
        # Keep the card clean — hide idle / Ready noise.
        if not text or text.lower() == "ready":
            self.status.configure(text="")
            if self._status_visible:
                self.status.pack_forget()
                self._status_visible = False
            return
        self.status.configure(text=text)
        if not self._status_visible:
            self.status.pack(fill="x", pady=(10, 0))
            self._status_visible = True

    def _set_busy(self, busy: bool, status: str | None = None) -> None:
        self._busy = busy
        state = "disabled" if busy else "normal"
        self.play.configure(state=state)
        if hasattr(self, "play_menu"):
            self.play_menu.configure(state=state)
        if self._login_mode in ("guest", "microsoft"):
            self.username.configure(state="disabled")
        else:
            self.username.configure(state="disabled" if busy else "normal")
        self.ram.configure(state=state)
        self.account_btn.configure(state=state)
        self.keep_open.configure(state=state)
        for btn in self._version_buttons:
            btn.configure(state=state)
        for btn in self._server_buttons:
            btn.configure(state=state)
        if status is not None:
            self._set_status(status)

    def _family_card_image(
        self,
        family: str,
        title: str,
        *,
        subtitle: str = "",
        size: tuple[int, int] = (540, 78),
    ) -> ctk.CTkImage:
        """Bake official art + label into one image (CTk has no real button backgrounds)."""
        key = f"card:{family}:{title}:{subtitle}:{size[0]}x{size[1]}"
        if key in self._version_art_images:
            return self._version_art_images[key]

        tw, th = size
        path = core.update_art_path(family)
        if path and path.is_file():
            try:
                img = Image.open(path).convert("RGB")
                scale = max(tw / img.width, th / img.height)
                resized = img.resize(
                    (max(1, int(img.width * scale)), max(1, int(img.height * scale))),
                    Image.Resampling.LANCZOS,
                )
                left = (resized.width - tw) // 2
                top = (resized.height - th) // 2
                card = resized.crop((left, top, left + tw, top + th))
                card = ImageEnhance.Brightness(card).enhance(0.55)
                card = Image.blend(card, Image.new("RGB", card.size, (0, 0, 0)), 0.18)
            except Exception:  # noqa: BLE001
                card = Image.new("RGB", (tw, th), (22, 28, 26))
        else:
            card = Image.new("RGB", (tw, th), (22, 28, 26))
            draw = ImageDraw.Draw(card)
            draw.rectangle((0, th - 4, tw, th), fill=(93, 155, 60))

        # Soft left shade so labels stay readable over bright key art
        rgba = card.convert("RGBA")
        shade = Image.new("RGBA", (tw, th), (0, 0, 0, 0))
        shade_draw = ImageDraw.Draw(shade)
        fade_w = min(300, tw)
        for x in range(fade_w):
            a = int(170 * (1 - x / fade_w))
            shade_draw.line([(x, 0), (x, th)], fill=(0, 0, 0, a))
        card = Image.alpha_composite(rgba, shade).convert("RGB")
        draw = ImageDraw.Draw(card)

        label = f"{family}  {title}".strip()
        try:
            font_big = ImageFont.truetype("arial.ttf", 22)
            font_small = ImageFont.truetype("arial.ttf", 13)
        except Exception:  # noqa: BLE001
            font_big = ImageFont.load_default()
            font_small = font_big

        draw.text((16, 16 if subtitle else 26), label, fill=(242, 244, 240), font=font_big)
        if subtitle:
            draw.text((16, 46), subtitle, fill=(190, 200, 190), font=font_small)

        cimg = ctk.CTkImage(light_image=card, dark_image=card, size=size)
        self._version_art_images[key] = cimg
        return cimg

    def _refresh_nav_style(self) -> None:
        active = {"fg_color": ACCENT, "hover_color": ACCENT_HOVER, "text_color": "#0b1208", "border_width": 0}
        idle = {
            "fg_color": "#1a2220",
            "hover_color": "#2a3530",
            "text_color": TEXT,
            "border_width": 1,
            "border_color": "#5a6a62",
        }
        locked = {
            "fg_color": "#121816",
            "hover_color": "#121816",
            "text_color": MUTED,
            "border_width": 1,
            "border_color": "#3a4540",
        }
        if self._page == "mods" and self._can_use_fabric_mods():
            self.nav_mods_btn.configure(**active)
            self.nav_play_btn.configure(**idle)
        else:
            self.nav_play_btn.configure(**active)
            if self._can_use_fabric_mods():
                self.nav_mods_btn.configure(**idle)
            else:
                self.nav_mods_btn.configure(**locked)

    def _refresh_mod_source_style(self) -> None:
        active = {"fg_color": ACCENT, "hover_color": ACCENT_HOVER, "text_color": "#0b1208", "border_width": 0}
        idle = {
            "fg_color": "#1a2220",
            "hover_color": "#2a3530",
            "text_color": TEXT,
            "border_width": 1,
            "border_color": "#5a6a62",
        }
        if self._mod_source == "curseforge":
            self.mod_src_curseforge.configure(**active)
            self.mod_src_modrinth.configure(**idle)
        else:
            self.mod_src_modrinth.configure(**active)
            self.mod_src_curseforge.configure(**idle)

    def _show_page(self, page: Literal["play", "mods"]) -> None:
        if page == "mods" and not self._can_use_fabric_mods():
            self._on_nav_mods()
            return
        if page == self._page:
            return
        self._page = page
        self._hide_account_menu()
        self._refresh_nav_style()
        if page == "mods":
            self._hide_version_dialog()
            self.play_card.place_forget()
            self.servers_card.place_forget()
            self.mods_card.place(relx=0.5, rely=0.52, anchor="center")
            self._update_mods_context()
            self.after(40, lambda: self._paint_glass(self.mods_card, self.mods_glass, "mods"))
            if not self._mod_hits:
                self._search_mods()
            self._sync_discord_rpc()
        else:
            self.mods_card.place_forget()
            self.play_card.place(relx=0.5, rely=0.48, anchor="center")
            self._place_servers_card()
            self.after(40, self._refresh_background)
            self._sync_discord_rpc()

    def _mods_game_version(self) -> str:
        try:
            return core.vanilla_minecraft_id(self._selected_version)
        except Exception:  # noqa: BLE001
            return "1.21.1"

    def _mods_loader(self) -> str:
        loader, _ = core.parse_version_ref(self._selected_version)
        return mods_catalog.normalize_loader(loader if loader != "vanilla" else "fabric")

    def _update_mods_context(self) -> None:
        if not hasattr(self, "mods_context"):
            return
        if not self._can_use_fabric_mods():
            self.mods_context.configure(text="Microsoft account required for mods")
            return
        mc = self._mods_game_version()
        loader = self._mods_loader()
        tip = ""
        if core.parse_version_ref(self._selected_version)[0] == "vanilla":
            tip = "  ·  pick Fabric on a version to install mods for it"
        self.mods_context.configure(text=f"{mc}  ·  {loader.title()}{tip}")

    def _open_mods_folder(self) -> None:
        """Reveal the live mods/ directory in the system file manager."""
        try:
            path = core.mods_dir(core.minecraft_directory(self.config_data))
            path.mkdir(parents=True, exist_ok=True)
            if sys.platform == "win32":
                os.startfile(path)  # type: ignore[attr-defined]
            elif sys.platform == "darwin":
                subprocess.Popen(["open", str(path)])
            else:
                subprocess.Popen(["xdg-open", str(path)])
            self.mods_status.configure(text=f"Opened {path}")
        except Exception as exc:  # noqa: BLE001
            self._show_error(str(exc), title="Could not open mods folder")

    def _set_mod_source(self, source: mods_catalog.ModSource) -> None:
        if source == self._mod_source:
            return
        if source == "curseforge" and not mods_catalog.curseforge_configured(self.config_data):
            self.mods_status.configure(
                text="CurseForge needs curseforge_api_key in config.json "
                "(free key: console.curseforge.com)."
            )
            self._mod_source = source
            self._refresh_mod_source_style()
            self._clear_mod_results()
            return
        self._mod_source = source
        self._refresh_mod_source_style()
        self._search_mods()

    def _on_mod_search_typed(self, _event: Any = None) -> None:
        if self._mod_search_after is not None:
            self.after_cancel(self._mod_search_after)
        self._mod_search_after = self.after(450, self._search_mods)

    def _clear_mod_results(self) -> None:
        for child in self.mods_scroll.winfo_children():
            child.destroy()
        self._mod_result_widgets.clear()
        self._mod_hits.clear()

    def _search_mods(self) -> None:
        if self._page != "mods" or not self._can_use_fabric_mods():
            return
        query = self.mod_search.get().strip()
        mc = self._mods_game_version()
        loader = self._mods_loader()
        source = self._mod_source
        self.mods_status.configure(text=f"Searching {source}…")

        def work() -> None:
            try:
                hits = mods_catalog.search_mods(
                    query,
                    source=source,
                    game_version=mc,
                    loader=loader,
                    limit=24,
                    config=self.config_data,
                )
                self.after(0, lambda: self._apply_mod_hits(hits, source, mc, loader, query))
            except Exception as exc:  # noqa: BLE001
                err = str(exc)
                self.after(0, lambda: self.mods_status.configure(text=err))

        threading.Thread(target=work, daemon=True).start()

    def _apply_mod_hits(
        self,
        hits: list[mods_catalog.ModHit],
        source: mods_catalog.ModSource,
        mc: str,
        loader: str,
        query: str,
    ) -> None:
        if source != self._mod_source:
            return
        self._clear_mod_results()
        self._mod_hits = hits
        if not hits:
            q = f" for “{query}”" if query else ""
            self.mods_status.configure(text=f"No mods found{q} on {source} for {mc}/{loader}.")
            return
        self.mods_status.configure(text=f"{len(hits)} mods · {mc} · {loader} · {source}")
        for hit in hits:
            self._add_mod_row(hit)

    def _add_mod_row(self, hit: mods_catalog.ModHit) -> None:
        row = ctk.CTkFrame(
            self.mods_scroll,
            fg_color="#141c18",
            corner_radius=10,
            border_width=1,
            border_color="#3a4540",
        )
        row.pack(fill="x", pady=5, padx=4)

        inner = ctk.CTkFrame(row, fg_color="transparent")
        inner.pack(fill="x", padx=12, pady=10)

        icon_lbl = ctk.CTkLabel(inner, text="", width=42, height=42, fg_color="#1a2220", corner_radius=8)
        icon_lbl.pack(side="left", padx=(0, 12))
        if hit.icon_url:
            self._load_mod_icon_async(hit.icon_url, icon_lbl)

        text_col = ctk.CTkFrame(inner, fg_color="transparent")
        text_col.pack(side="left", fill="x", expand=True)
        title = hit.title
        if hit.author:
            title = f"{hit.title}  ·  {hit.author}"
        ctk.CTkLabel(
            text_col,
            text=title,
            anchor="w",
            font=self._font(14, bold=True),
            text_color=TEXT,
            fg_color="transparent",
        ).pack(fill="x")
        desc = (hit.description or "").replace("\n", " ").strip()
        if len(desc) > 110:
            desc = desc[:107] + "…"
        ctk.CTkLabel(
            text_col,
            text=desc or "No description",
            anchor="w",
            font=self._font(11),
            text_color=MUTED,
            fg_color="transparent",
        ).pack(fill="x", pady=(2, 0))
        ctk.CTkLabel(
            text_col,
            text=f"{mods_catalog.format_downloads(hit.downloads)} downloads",
            anchor="w",
            font=self._font(11),
            text_color="#8aa08a",
            fg_color="transparent",
        ).pack(fill="x", pady=(2, 0))

        ctk.CTkButton(
            inner,
            text="Install",
            width=90,
            height=36,
            corner_radius=8,
            fg_color=ACCENT,
            hover_color=ACCENT_HOVER,
            text_color="#0b1208",
            font=self._font(12, bold=True),
            command=lambda h=hit: self._install_mod(h),
        ).pack(side="right", padx=(10, 0))
        self._mod_result_widgets.append(row)

    def _load_mod_icon_async(self, url: str, label: ctk.CTkLabel) -> None:
        if url in self._mod_icon_images:
            label.configure(image=self._mod_icon_images[url], text="")
            return

        def work() -> None:
            try:
                import io
                import urllib.request

                req = urllib.request.Request(url, headers={"User-Agent": mods_catalog.USER_AGENT})
                with urllib.request.urlopen(req, timeout=20) as resp:
                    raw = resp.read()
                img = Image.open(io.BytesIO(raw)).convert("RGBA")
                img = img.resize((42, 42), Image.Resampling.LANCZOS)
                cimg = ctk.CTkImage(light_image=img, dark_image=img, size=(42, 42))

                def apply() -> None:
                    self._mod_icon_images[url] = cimg
                    try:
                        label.configure(image=cimg, text="")
                    except Exception:  # noqa: BLE001
                        pass

                self.after(0, apply)
            except Exception:  # noqa: BLE001
                pass

        threading.Thread(target=work, daemon=True).start()

    def _install_mod(self, hit: mods_catalog.ModHit) -> None:
        if not self._can_use_fabric_mods():
            self._show_alert(
                "Mods need a Microsoft account.\nSign in under Account to install mods.",
                title="Microsoft required",
                kind="info",
            )
            return
        mc = self._mods_game_version()
        loader = self._mods_loader()
        self.mods_status.configure(text=f"Installing {hit.title}…")

        def work() -> None:
            try:
                path = mods_catalog.install_mod(
                    hit,
                    game_version=mc,
                    loader=loader,
                    config=self.config_data,
                )
                self.after(
                    0,
                    lambda: self.mods_status.configure(text=f"Installed {path.name} → mods/"),
                )
            except Exception as exc:  # noqa: BLE001
                err = str(exc)
                self.after(
                    0,
                    lambda: (
                        self.mods_status.configure(text="Install failed — see popup."),
                        self._show_error(err, title="Mod install failed"),
                    ),
                )

        threading.Thread(target=work, daemon=True).start()

    def _select_version(self, version_id: str, loader: core.ModLoaderName = "vanilla") -> None:
        """Commit a Minecraft version + Vanilla/Fabric choice."""
        if self._guest_uses_fabric():
            loader = "fabric"
            allowed = self._guest_allowed_versions()
            resolved = version_id
            if version_id == "latest":
                try:
                    resolved = core.get_latest_versions().get("release") or version_id
                except Exception:  # noqa: BLE001
                    resolved = version_id
            if resolved not in allowed:
                self._show_alert(
                    "Guest mode only supports versions with the guest-lock mod "
                    "(Fabric 1.20.1–1.21.x builds we ship).",
                    title="Version not available",
                    kind="info",
                )
                return
            version_id = resolved
        elif loader == "fabric" and not self._can_use_fabric_loader():
            self._show_alert(
                "Fabric needs a Microsoft account (or Guest for the lock mod only).\n"
                "Cracked accounts are vanilla only.",
                title="Fabric not available",
                kind="info",
            )
            return
        if loader == "fabric" and self._login_mode == "cracked":
            self._show_alert(
                "Cracked accounts can only play vanilla.\nSign in with Microsoft for Fabric & mods.",
                title="Vanilla only",
                kind="info",
            )
            return
        self._mod_loader = loader
        self._selected_version = core.make_version_ref(loader, version_id)
        self.version_label.configure(text=core.display_version_label(self._selected_version))
        self._pending_server = None
        self.config_data["mod_loader"] = loader
        self.config_data["last_version"] = self._selected_version
        core.save_config(self.config_data)
        self._update_mods_context()
        self._sync_discord_rpc()
        self._hide_version_dialog()

    def _paint_version_dim(self) -> None:
        self.update_idletasks()
        w = max(self.winfo_width(), 640)
        h = max(self.winfo_height(), 360)
        if self._wallpaper is not None:
            base = self._wallpaper.resize((w, h), Image.Resampling.LANCZOS).convert("RGB")
        else:
            base = Image.new("RGB", (w, h), (8, 12, 10))
        dim = Image.blend(base, Image.new("RGB", (w, h), (0, 0, 0)), 0.55)
        img = ctk.CTkImage(light_image=dim, dark_image=dim, size=(w, h))
        self._version_dim_image = img
        self.version_dim.configure(image=img)

    def _open_version_menu(self) -> None:
        """Arrow control: pick a version without launching."""
        if self._busy:
            return
        self._open_version_dialog()

    def _open_version_dialog(self, mode: Literal["browse"] = "browse") -> None:
        if self._busy:
            return
        self._hide_account_menu()
        self._version_dialog_mode = mode
        self._version_dialog_open = True
        self._paint_version_dim()
        self.version_dim.place(x=0, y=0, relwidth=1, relheight=1)
        self.version_dim.bind("<Button-1>", lambda _e: self._hide_version_dialog())
        self.version_card.place(relx=0.5, rely=0.48, anchor="center")
        self._paint_glass(self.version_card, self.version_glass, "versions")
        self.version_dim.lift()
        self.version_card.lift()
        if self._versions_loaded:
            self._show_major_versions()
        else:
            self._load_versions_async()

    def _hide_version_dialog(self) -> None:
        self._version_dialog_open = False
        self.version_card.place_forget()
        self.version_dim.place_forget()
        try:
            self.version_dim.unbind("<Button-1>")
        except Exception:  # noqa: BLE001
            pass

    def _open_loader_choice(self, version_id: str, *, family: str | None = None) -> None:
        """After picking a patch (or latest), choose Vanilla or Fabric."""
        self._loader_choice_family = family
        self._show_loader_choice(version_id)

    def _show_loader_choice(self, version_id: str) -> None:
        self._clear_version_scroll()
        family = self._loader_choice_family

        def go_back() -> None:
            if family:
                self._show_patch_versions(family)
            else:
                self._show_major_versions()

        back = ctk.CTkButton(
            self.version_scroll,
            text="←  Back",
            height=36,
            corner_radius=8,
            fg_color="#2a322e",
            hover_color="#3a453e",
            border_width=1,
            border_color="#5a6a62",
            text_color=TEXT,
            font=self._font(12, bold=True),
            command=go_back,
        )
        back.pack(fill="x", pady=(0, 10))

        label = "latest release" if version_id == "latest" else version_id
        ctk.CTkLabel(
            self.version_scroll,
            text=f"Play {label} as",
            anchor="w",
            font=self._font(15, bold=True),
            text_color=TEXT,
            fg_color="transparent",
        ).pack(fill="x", pady=(0, 8))

        # Resolve fabric support for concrete ids (latest always allowed)
        fabric_allowed = self._can_use_fabric_loader()
        fabric_ok = fabric_allowed
        if fabric_ok and version_id != "latest":
            fabric_ok = (
                not self._fabric_supported
                or version_id in self._fabric_supported
            )
        if fabric_ok and self._guest_uses_fabric():
            allowed = self._guest_allowed_versions()
            if version_id == "latest":
                try:
                    latest = core.get_latest_versions().get("release")
                    fabric_ok = bool(latest) and latest in allowed
                except Exception:  # noqa: BLE001
                    fabric_ok = False
            else:
                fabric_ok = version_id in allowed

        show_vanilla = not self._guest_uses_fabric()
        if show_vanilla:
            vanilla_btn = ctk.CTkButton(
                self.version_scroll,
                text="Vanilla\nOfficial Minecraft",
                height=72,
                corner_radius=10,
                border_width=2 if self._mod_loader == "vanilla" and core.parse_version_ref(self._selected_version)[1] == version_id else 1,
                border_color=ACCENT if self._mod_loader == "vanilla" and core.parse_version_ref(self._selected_version)[1] == version_id else "#5a6a62",
                fg_color="#1a2220",
                hover_color="#2a3530",
                text_color=TEXT,
                font=self._font(14, bold=True),
                command=lambda: self._select_version(version_id, "vanilla"),
            )
            vanilla_btn._version_id = version_id  # type: ignore[attr-defined]
            vanilla_btn.pack(fill="x", pady=5)
            self._version_buttons.append(vanilla_btn)

        if self._guest_uses_fabric():
            fabric_text = (
                "Fabric\nGuest lock · singleplayer only"
                if fabric_ok
                else "Fabric\nNot available for this version"
            )
        elif fabric_allowed:
            fabric_text = (
                "Fabric\nMods supported  ·  installs on Play"
                if fabric_ok
                else "Fabric\nNot available for this version"
            )
        else:
            fabric_text = "Fabric\nMicrosoft account required"
            fabric_ok = False

        fabric_kwargs: dict[str, Any] = {
            "text": fabric_text,
            "height": 72,
            "corner_radius": 10,
            "border_width": 2 if self._mod_loader == "fabric" and core.parse_version_ref(self._selected_version)[1] == version_id else 1,
            "border_color": ACCENT if self._mod_loader == "fabric" and core.parse_version_ref(self._selected_version)[1] == version_id else "#5a6a62",
            "fg_color": "#1a2220",
            "hover_color": "#2a3530",
            "text_color": TEXT if fabric_ok else MUTED,
            "font": self._font(14, bold=True),
        }
        if fabric_ok:
            fabric_kwargs["command"] = lambda: self._select_version(version_id, "fabric")
        else:
            fabric_kwargs["state"] = "disabled"
        fabric_btn = ctk.CTkButton(self.version_scroll, **fabric_kwargs)
        fabric_btn._version_id = version_id  # type: ignore[attr-defined]
        fabric_btn.pack(fill="x", pady=5)
        self._version_buttons.append(fabric_btn)

    def _highlight_version_buttons(self) -> None:
        _, selected_mc = core.parse_version_ref(self._selected_version)
        for btn in self._version_buttons:
            vid = getattr(btn, "_version_id", "")
            if vid == selected_mc:
                btn.configure(border_color=ACCENT, border_width=2)
            else:
                btn.configure(border_color="#5a6a62", border_width=1)

    def _clear_version_scroll(self) -> None:
        for child in self.version_scroll.winfo_children():
            child.destroy()
        self._version_buttons.clear()

    def _show_major_versions(self) -> None:
        """Only big families (1.21, 1.20, …) — click one to pick a patch."""
        self._clear_version_scroll()
        groups = self._version_groups_visible()

        guest = self._guest_uses_fabric()
        show_latest = True
        if guest:
            allowed = self._guest_allowed_versions()
            try:
                latest_id = core.get_latest_versions().get("release")
            except Exception:  # noqa: BLE001
                latest_id = None
            show_latest = bool(latest_id) and latest_id in allowed

        if show_latest:
            latest_btn = ctk.CTkButton(
                self.version_scroll,
                text="latest  ·  always newest release",
                height=48,
                corner_radius=8,
                fg_color=ACCENT,
                hover_color=ACCENT_HOVER,
                text_color="#0b1208",
                font=self._font(14, bold=True),
                command=lambda: self._open_loader_choice("latest"),
            )
            latest_btn._version_id = "latest"  # type: ignore[attr-defined]
            latest_btn.pack(fill="x", pady=(0, 10))
            self._version_buttons.append(latest_btn)

        if guest and not groups:
            ctk.CTkLabel(
                self.version_scroll,
                text="No guest-lock jars found.\nBuild them with guest-lock/build_all.py",
                font=self._font(12),
                text_color=MUTED,
                fg_color="transparent",
            ).pack(anchor="w", pady=12)
            return

        for group in groups:
            family = group["family"]
            title = group["title"]
            versions: list[str] = group["versions"]
            if not versions:
                continue

            card = self._family_card_image(
                family,
                title,
                subtitle=f"{len(versions)} versions  ·  click to choose",
                size=(540, 78),
            )
            btn = ctk.CTkButton(
                self.version_scroll,
                text="",
                image=card,
                width=540,
                height=78,
                corner_radius=10,
                border_width=1,
                border_color="#5a6a62",
                fg_color="#121816",
                hover_color="#1c2620",
                compound="left",
                command=lambda f=family: self._show_patch_versions(f),
            )
            btn._version_id = family  # type: ignore[attr-defined]
            btn._is_family = True  # type: ignore[attr-defined]
            btn.pack(fill="x", pady=5)
            self._version_buttons.append(btn)

        self._highlight_major_buttons()

    def _highlight_major_buttons(self) -> None:
        selected_fam = core.version_family(self._selected_version)
        _, selected_mc = core.parse_version_ref(self._selected_version)
        for btn in self._version_buttons:
            if getattr(btn, "_is_family", False):
                fam = getattr(btn, "_version_id", "")
                if fam == selected_fam:
                    btn.configure(border_color=ACCENT, border_width=2)
                else:
                    btn.configure(border_color="#5a6a62", border_width=1)
            elif getattr(btn, "_version_id", "") == selected_mc:
                btn.configure(border_color=ACCENT, border_width=2)

    def _show_patch_versions(self, family: str) -> None:
        """Second step: pick 1.21.11 / 1.21.10 / … inside a major line."""
        groups = self._version_groups_visible()
        group = next((g for g in groups if g["family"] == family), None)
        if not group:
            return

        self._clear_version_scroll()
        title = group["title"]
        versions: list[str] = group["versions"]
        card = self._family_card_image(family, title, size=(540, 64))

        back = ctk.CTkButton(
            self.version_scroll,
            text="←  All versions",
            height=36,
            corner_radius=8,
            fg_color="#2a322e",
            hover_color="#3a453e",
            border_width=1,
            border_color="#5a6a62",
            text_color=TEXT,
            font=self._font(12, bold=True),
            command=self._show_major_versions,
        )
        back.pack(fill="x", pady=(0, 8))

        header = ctk.CTkButton(
            self.version_scroll,
            text="",
            image=card,
            width=540,
            height=64,
            corner_radius=10,
            border_width=0,
            fg_color="#121816",
            hover_color="#121816",
            compound="left",
            state="disabled",
        )
        header.pack(fill="x", pady=(0, 10))

        tip = ctk.CTkLabel(
            self.version_scroll,
            text="Choose a patch, then Vanilla or Fabric",
            anchor="w",
            font=self._font(12),
            text_color=MUTED,
            fg_color="transparent",
        )
        tip.pack(fill="x", pady=(0, 6))

        grid = ctk.CTkFrame(self.version_scroll, fg_color="transparent")
        grid.pack(fill="x")
        cols = 3
        _, selected_mc = core.parse_version_ref(self._selected_version)
        for i, vid in enumerate(versions):
            r, c = divmod(i, cols)
            selected = vid == selected_mc
            btn = ctk.CTkButton(
                grid,
                text=vid,
                height=44,
                width=170,
                corner_radius=8,
                border_width=2 if selected else 1,
                border_color=ACCENT if selected else "#5a6a62",
                fg_color="#1a2220",
                hover_color="#2a3530",
                text_color=TEXT,
                font=self._font(12, bold=True),
                command=lambda v=vid, f=family: self._open_loader_choice(v, family=f),
            )
            btn._version_id = vid  # type: ignore[attr-defined]
            btn.grid(row=r, column=c, padx=4, pady=4, sticky="ew")
            self._version_buttons.append(btn)
        for c in range(cols):
            grid.grid_columnconfigure(c, weight=1)

    def _build_version_sections(self, groups: list[dict[str, Any]]) -> None:
        self._version_groups = groups
        self._show_major_versions()

    def _apply_version_groups(
        self,
        groups: list[dict[str, Any]],
        fabric_supported: set[str] | None = None,
    ) -> None:
        if fabric_supported is not None:
            self._fabric_supported = fabric_supported
        self._build_version_sections(groups)
        self._versions_loaded = True
        if not self._busy:
            self._set_status("")

    def _build_server_buttons(self) -> None:
        for child in self.servers_row.winfo_children():
            child.destroy()
        self._server_buttons.clear()
        servers = core.load_servers(self.config_data)
        if not servers:
            ctk.CTkLabel(
                self.servers_row,
                text="No saved servers yet — add one above.",
                font=self._font(12),
                text_color=MUTED,
                fg_color="transparent",
            ).pack(side="left", padx=4)
            return

        for index, server in enumerate(servers):
            name = str(server.get("name") or server.get("address") or "Server")
            chip = ctk.CTkFrame(self.servers_row, fg_color="transparent")
            chip.pack(side="left", padx=(0, 8))
            join = ctk.CTkButton(
                chip,
                text=name,
                width=140,
                height=40,
                corner_radius=8,
                border_width=1,
                border_color="#5a6a62",
                fg_color="#1a2220",
                hover_color="#2a3530",
                text_color=TEXT,
                font=self._font(12, bold=True),
                command=lambda s=server: self._on_join_server(s),
            )
            join.pack(side="left")
            remove = ctk.CTkButton(
                chip,
                text="×",
                width=34,
                height=40,
                corner_radius=8,
                border_width=1,
                border_color="#5a6a62",
                fg_color="#2a1a1a",
                hover_color="#4a2222",
                text_color="#f0c0c0",
                font=self._font(14, bold=True),
                command=lambda i=index: self._on_delete_server(i),
            )
            remove.pack(side="left", padx=(4, 0))
            self._server_buttons.extend([join, remove])

    def _on_save_server(self) -> None:
        if not self._can_use_multiplayer():
            self._show_alert(
                "Guests can only play singleplayer.\nSign in as Cracked or Microsoft to use servers.",
                title="Singleplayer only",
                kind="info",
            )
            return
        name = self.server_name.get().strip()
        address = self.server_address.get().strip()
        port_raw = self.server_port.get().strip() or "25565"
        if not address:
            self._set_status("Enter a server address.")
            return
        try:
            port = int(port_raw)
        except ValueError:
            self._set_status("Port must be a number.")
            return
        if not name:
            name = address

        servers = core.load_servers(self.config_data)
        # Update existing entry with same address+port, otherwise append
        updated = False
        for s in servers:
            if str(s.get("address") or "").lower() == address.lower() and int(s.get("port") or 25565) == port:
                s["name"] = name
                s["address"] = address
                s["port"] = port
                updated = True
                break
        if not updated:
            servers.append({"name": name, "address": address, "port": port})

        self.config_data = core.save_servers(servers, self.config_data)
        self.server_name.delete(0, "end")
        self.server_address.delete(0, "end")
        self.server_port.delete(0, "end")
        self.server_port.insert(0, "25565")
        self._build_server_buttons()
        self._set_status(f"Saved {name}.")

    def _on_delete_server(self, index: int) -> None:
        servers = core.load_servers(self.config_data)
        if index < 0 or index >= len(servers):
            return
        removed = servers.pop(index)
        self.config_data = core.save_servers(servers, self.config_data)
        self._build_server_buttons()
        label = removed.get("name") or removed.get("address") or "server"
        self._set_status(f"Removed {label}.")

    def _on_join_server(self, server: dict[str, Any]) -> None:
        if not self._can_use_multiplayer():
            self._show_alert(
                "Guests can only play singleplayer.\nSign in as Cracked or Microsoft to join servers.",
                title="Singleplayer only",
                kind="info",
            )
            return
        self._pending_server = server
        name = server.get("name") or server.get("address")
        self._set_status(f"Joining {name}…")
        self._on_play()

    def _load_versions_async(self) -> None:
        self._versions_loaded = False
        self._set_status("Loading versions…")

        def work() -> None:
            try:
                groups = core.versions_grouped(
                    release_only=True,
                    limit_per_family=9,
                    loader="vanilla",
                )
                fabric_supported: set[str] = set()
                try:
                    fabric_supported = set(core.fabric_supported_versions(stable_only=True))
                except Exception:  # noqa: BLE001
                    fabric_supported = set()
                self.after(0, lambda: self._apply_version_groups(groups, fabric_supported))
            except Exception as exc:  # noqa: BLE001
                self.after(
                    0,
                    lambda: self._show_error(str(exc), title="Could not load versions"),
                )

        threading.Thread(target=work, daemon=True).start()

    def _ui_progress(self, value: int, maximum: int, status: str) -> None:
        def update() -> None:
            self._set_status(status)
            self._show_progress()
            if maximum > 0:
                self.progress.set(min(1.0, value / maximum))
            elif status:
                self.progress.set(0.15)

        self.after(0, update)

    def _on_keep_open_toggle(self) -> None:
        self.config_data["keep_launcher_open"] = bool(self.keep_open.get())
        core.save_config(self.config_data)

    def _persist_prefs(self, username: str, version: str, ram_gb: int) -> None:
        if self._login_mode != "guest":
            self.config_data["offline_username"] = username
        self.config_data["last_version"] = version
        self.config_data["mod_loader"] = self._mod_loader
        self.config_data["jvm_arguments"] = core.ram_to_jvm_arguments(ram_gb)
        self.config_data["login_mode"] = self._login_mode or "cracked"
        self.config_data["keep_launcher_open"] = bool(self.keep_open.get())
        self.config_data.setdefault("servers", [])
        core.save_config(self.config_data)

    def _on_play(self) -> None:
        if self._busy:
            return
        self._hide_account_menu()

        username = self.username.get().strip()
        if not username:
            self._set_status("Enter a username.")
            return

        if self._guest_uses_fabric():
            self._clamp_guest_version(save=True)
        elif not self._can_use_fabric_loader():
            self._force_vanilla_selection(save=True)

        version = (self._selected_version or "latest").strip() or "latest"
        if self._guest_uses_fabric():
            allowed = self._guest_allowed_versions()
            _, mc_ref = core.parse_version_ref(version)
            if mc_ref == "latest":
                try:
                    mc_ref = core.get_latest_versions().get("release") or mc_ref
                except Exception:  # noqa: BLE001
                    pass
            if mc_ref not in allowed or not allowed:
                self._show_alert(
                    "Guest mode only supports versions with the guest-lock mod.",
                    title="Version not available",
                    kind="info",
                )
                return
            version = core.make_version_ref("fabric", mc_ref)
            self._selected_version = version
        elif core.parse_version_ref(version)[0] == "fabric" and not self._can_use_fabric_loader():
            self._force_vanilla_selection(save=True)
            version = self._selected_version

        ram_gb = int(round(float(self.ram.get())))
        keep_open = bool(self.keep_open.get())
        server = self._pending_server if self._can_use_multiplayer() else None
        if not self._can_use_multiplayer():
            self._pending_server = None
        login_mode = self._login_mode
        self._persist_prefs(username, version, ram_gb)

        self._show_progress()
        self._set_busy(True, "Preparing…")
        self.progress.set(0)
        discord_rpc.set_preparing(core.display_version_label(version))

        def work() -> None:
            try:
                # Always clear a leftover guest mods stash before non-guest launches
                if login_mode != "guest":
                    core.restore_mods_stash()

                if login_mode == "microsoft":
                    options = core.online_options(self.config_data)
                    options["jvmArguments"] = core.ram_to_jvm_arguments(ram_gb)
                else:
                    options = core.offline_options(username, core.ram_to_jvm_arguments(ram_gb))

                if login_mode == "guest":
                    _, mc_ref = core.parse_version_ref(version)
                    extra_jvm = core.prepare_guest_session(mc_ref)
                    jvm = list(options.get("jvmArguments") or [])
                    for flag in extra_jvm:
                        if flag not in jvm:
                            jvm.append(flag)
                    options["jvmArguments"] = jvm

                if server:
                    options["server"] = str(server.get("address") or "")
                    options["port"] = str(server.get("port") or 25565)
                command, resolved, directory = core.build_launch_command(
                    version,
                    options,
                    on_progress=self._ui_progress,
                )
                # Game should keep running even if the launcher window is closed.
                proc = subprocess.Popen(command, cwd=directory)

                def started() -> None:
                    self._pending_server = None
                    server_name = None
                    if server:
                        server_name = str(server.get("name") or server.get("address") or "")
                    discord_rpc.set_playing(
                        version_label=core.display_version_label(resolved)
                        if not str(resolved).startswith("fabric-loader-")
                        else core.display_version_label(version),
                        username=username,
                        server_name=server_name,
                    )
                    if not keep_open:
                        self._game_proc = None
                        # Keep RPC alive briefly is not possible after destroy; clear cleanly
                        self.destroy()
                        return

                    self._game_proc = proc
                    self.progress.set(1)
                    target = f"{resolved} as {username}"
                    if server:
                        target += f" → {server.get('name') or server.get('address')}"
                    self._set_busy(True, f"Playing {target}")
                    try:
                        self.iconify()
                    except Exception:  # noqa: BLE001
                        pass

                self.after(0, started)
            except Exception as exc:  # noqa: BLE001
                err = f"{exc}"
                traceback.print_exc()

                def failed() -> None:
                    self._pending_server = None
                    if login_mode == "guest":
                        try:
                            core.restore_mods_stash()
                        except Exception:  # noqa: BLE001
                            pass
                    self._hide_progress()
                    self._set_busy(False, "Launch failed — see popup.")
                    self._sync_discord_rpc()
                    self._show_error(err, title="Launch failed")

                self.after(0, failed)

        threading.Thread(target=work, daemon=True).start()

    def _poll_game(self) -> None:
        if self._game_proc is not None:
            code = self._game_proc.poll()
            if code is not None:
                self._game_proc = None
                if self._login_mode == "guest":
                    try:
                        core.restore_mods_stash()
                    except Exception:  # noqa: BLE001
                        pass
                self._hide_progress()
                self._set_busy(False, "" if code == 0 else f"Game exited ({code})")
                self._sync_discord_rpc()
                try:
                    self.deiconify()
                    self.lift()
                except Exception:  # noqa: BLE001
                    pass
        self.after(1000, self._poll_game)

    def _init_discord_rpc(self) -> None:
        def work() -> None:
            try:
                discord_rpc.configure(self.config_data)
                self.after(0, self._sync_discord_rpc)
            except Exception:  # noqa: BLE001
                pass

        threading.Thread(target=work, daemon=True).start()

    def _sync_discord_rpc(self) -> None:
        if self._game_proc is not None:
            return
        label = core.display_version_label(self._selected_version)
        discord_rpc.set_idle(version_label=label, page=self._page)

    def _on_close(self) -> None:
        try:
            core.restore_mods_stash()
        except Exception:  # noqa: BLE001
            pass
        discord_rpc.shutdown()
        self.destroy()


def main() -> None:
    app = LauncherApp()
    app.mainloop()


if __name__ == "__main__":
    main()
