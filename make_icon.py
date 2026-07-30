# Generate assets/icon.ico + deepslate_block.png for shortcuts and the window.
from pathlib import Path

from PIL import Image

root = Path(__file__).resolve().parent
out = root / "assets" / "icon.ico"
png_out = root / "assets" / "deepslate_block.png"
src_candidates = [
    root / "assets" / "icon_source.png",
    root / "assets" / "deepslate_block.png",
]
out.parent.mkdir(parents=True, exist_ok=True)

src = next((p for p in src_candidates if p.is_file()), None)
if src is None:
    img = Image.new("RGBA", (256, 256), (50, 50, 55, 255))
else:
    img = Image.open(src).convert("RGBA")

# Punch near-black studio backdrop to transparent
pixels = img.load()
assert pixels is not None
w, h = img.size
for y in range(h):
    for x in range(w):
        r, g, b, a = pixels[x, y]
        if a > 0 and r < 18 and g < 18 and b < 18:
            pixels[x, y] = (0, 0, 0, 0)

# Square cover crop
side = min(img.size)
left = (img.width - side) // 2
top = (img.height - side) // 2
img = img.crop((left, top, left + side, top + side)).resize((256, 256), Image.Resampling.LANCZOS)

img.save(png_out, format="PNG")

sizes = [(256, 256), (128, 128), (64, 64), (48, 48), (32, 32), (16, 16)]
imgs = [img.resize(s, Image.Resampling.LANCZOS) for s in sizes]
imgs[0].save(
    out,
    format="ICO",
    sizes=[(im.width, im.height) for im in imgs],
    append_images=imgs[1:],
)
print(out)
print(png_out)
