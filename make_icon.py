"""Generate a Windows-compatible assets/icon.ico from deepslate_block.png.

Pillow's default ICO writer stores every size as PNG. Explorer / shortcut
resolution often mishandles those entries, so small sizes are written as
classic BMP (BI_RGB) DIBs and only 256x256 stays PNG.
"""

from __future__ import annotations

import struct
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent
OUT = ROOT / "assets" / "icon.ico"
SRC_CANDIDATES = [
    ROOT / "assets" / "deepslate_block.png",
    ROOT / "assets" / "icon_source.png",
    ROOT / "assets" / "icon.png",
]


def _punch_black(img: Image.Image, threshold: int = 18) -> Image.Image:
    img = img.convert("RGBA")
    pixels = img.load()
    assert pixels is not None
    w, h = img.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = pixels[x, y]
            if a > 0 and r < threshold and g < threshold and b < threshold:
                pixels[x, y] = (0, 0, 0, 0)
    return img


def _bmp_dib_bgra(img: Image.Image) -> bytes:
    """ICO image payload: BITMAPINFOHEADER + BGRA XOR bitmap + 1bpp AND mask."""
    img = img.convert("RGBA")
    w, h = img.size
    # XOR bitmap: bottom-up BGRA
    xor = bytearray()
    pixels = img.load()
    assert pixels is not None
    row_pad = (4 - (w * 4) % 4) % 4
    for y in range(h - 1, -1, -1):
        for x in range(w):
            r, g, b, a = pixels[x, y]
            xor.extend((b, g, r, a))
        xor.extend(b"\x00" * row_pad)

    # AND mask: 1 bit/pixel, padded to 32-bit rows, bottom-up
    and_row_bytes = ((w + 31) // 32) * 4
    and_mask = bytearray()
    for y in range(h - 1, -1, -1):
        row = 0
        bit = 7
        row_bytes = bytearray(and_row_bytes)
        col = 0
        for x in range(w):
            _r, _g, _b, a = pixels[x, y]
            if a < 128:
                row_bytes[col] |= 1 << bit
            bit -= 1
            if bit < 0:
                bit = 7
                col += 1
        and_mask.extend(row_bytes)

    header = struct.pack(
        "<IiiHHIIiiII",
        40,  # biSize
        w,
        h * 2,  # height includes AND mask
        1,  # planes
        32,  # bit count
        0,  # BI_RGB
        len(xor) + len(and_mask),
        0,
        0,
        0,
        0,
    )
    return header + bytes(xor) + bytes(and_mask)


def _png_bytes(img: Image.Image) -> bytes:
    from io import BytesIO

    buf = BytesIO()
    img.convert("RGBA").save(buf, format="PNG")
    return buf.getvalue()


def write_ico(images: dict[int, Image.Image], path: Path) -> None:
    """Write ICO: BMP DIBs for sizes < 256, PNG for 256."""
    entries: list[tuple[int, bytes]] = []
    for size in sorted(images):
        im = images[size].resize((size, size), Image.Resampling.NEAREST)
        if size >= 256:
            payload = _png_bytes(im)
        else:
            payload = _bmp_dib_bgra(im)
        entries.append((size, payload))

    count = len(entries)
    offset = 6 + 16 * count
    parts = [struct.pack("<HHH", 0, 1, count)]
    blobs: list[bytes] = []
    for size, payload in entries:
        w_byte = 0 if size >= 256 else size
        h_byte = 0 if size >= 256 else size
        parts.append(
            struct.pack(
                "<BBBBHHII",
                w_byte,
                h_byte,
                0,
                0,
                1,
                32,
                len(payload),
                offset,
            )
        )
        blobs.append(payload)
        offset += len(payload)

    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(b"".join(parts) + b"".join(blobs))


def main() -> None:
    src = next((p for p in SRC_CANDIDATES if p.is_file()), None)
    if src is None:
        raise SystemExit("No deepslate source image found under assets/")
    base = _punch_black(Image.open(src))
    # Keep a clean square canvas with the block centered
    side = max(base.size)
    canvas = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    ox = (side - base.size[0]) // 2
    oy = (side - base.size[1]) // 2
    canvas.paste(base, (ox, oy), base)

    sizes = [16, 32, 48, 64, 128, 256]
    images = {s: canvas for s in sizes}
    write_ico(images, OUT)
    # Also refresh desktop/build copy source
    build_ico = ROOT / "desktop" / "build" / "icon.ico"
    if build_ico.parent.is_dir():
        build_ico.write_bytes(OUT.read_bytes())
    print(OUT, OUT.stat().st_size, "bytes")


if __name__ == "__main__":
    main()
