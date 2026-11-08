"""Generates the app icon set (assets/*.png and fastlane icon) from code: an isometric cube (game asset)
with a wrench on a San Andreas sunset gradient. Run: python scripts/make-icons.py"""
import math, os
from PIL import Image, ImageDraw, ImageFilter

S = 2048  # supersampled canvas (4x the largest output side / 2)
K = S / 4096  # geometry below is in 4096-px units
ROOT = os.path.join(os.path.dirname(__file__), '..')
TOP, BOTTOM = (255, 176, 32), (232, 41, 92)


def gradient(size):
    g = Image.new('RGB', (1, 256))
    for y in range(256):
        t = y / 255
        g.putpixel((0, y), tuple(round(a + (b - a) * t) for a, b in zip(TOP, BOTTOM)))
    return g.resize((size, size), Image.BICUBIC)


def glyph(scale=1.0, mono=False):
    """White cube + dark wrench on transparent, content scaled around the center."""
    scale *= K
    im = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    cx, cy, r = S / 2, S / 2 + 60 * scale, 1050 * scale
    top = (cx, cy - r); rt = (cx + r * math.cos(math.pi / 6), cy - r / 2); rb = (cx + r * math.cos(math.pi / 6), cy + r / 2)
    bot = (cx, cy + r); lb = (cx - r * math.cos(math.pi / 6), cy + r / 2); lt = (cx - r * math.cos(math.pi / 6), cy - r / 2)
    c = (cx, cy)
    shades = [(255, 255, 255, 255), (255, 255, 255, 215), (255, 255, 255, 165)] if not mono else [(255, 255, 255, 255)] * 3
    d.polygon([top, rt, c, lt], fill=shades[0])
    d.polygon([lt, c, bot, lb], fill=shades[1])
    d.polygon([c, rt, rb, bot], fill=shades[2])
    # Wrench, drawn horizontally then rotated: handle + open-end head.
    w = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    wd = ImageDraw.Draw(w)
    col = (40, 22, 30, 255) if not mono else (0, 0, 0, 255)
    hw = 170 * scale
    x0, x1 = cx - 1150 * scale, cx + 700 * scale
    wd.rounded_rectangle([x0, cy - hw, x1, cy + hw], radius=hw, fill=col)
    hr = 430 * scale
    hx = x1 + 120 * scale
    wd.ellipse([hx - hr, cy - hr, hx + hr, cy + hr], fill=col)
    solid = w.copy()  # silhouette without the jaw opening, for the gap around the wrench
    jaw = 190 * scale
    wd.rectangle([hx, cy - jaw, hx + hr + 10, cy + jaw], fill=(0, 0, 0, 0))
    wd.ellipse([x0 + 40 * scale, cy - 80 * scale, x0 + 200 * scale, cy + 80 * scale], fill=(0, 0, 0, 0))
    w = w.rotate(35, resample=Image.BICUBIC, center=(cx, cy))
    solid = solid.rotate(35, resample=Image.BICUBIC, center=(cx, cy))
    if mono:
        w = Image.merge('RGBA', [Image.new('L', (S, S), 255)] * 3 + [w.getchannel('A')])
    # Light halo around the wrench separates it from the cube.
    halo = solid.getchannel('A').filter(ImageFilter.GaussianBlur(40 * K)).point(lambda a: 255 if a > 8 else 0)
    out = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    out.alpha_composite(im)
    out.putalpha(Image.composite(Image.new('L', (S, S), 0), out.getchannel('A'), halo))
    out.alpha_composite(w)
    return out


def save(im, path, size):
    im.resize((size, size), Image.LANCZOS).save(os.path.join(ROOT, path))


bg = gradient(S).convert('RGBA')
fg = glyph(0.85)  # adaptive icons crop to the inner 66 %
full = bg.copy(); full.alpha_composite(glyph(1.2))
mask = Image.new('L', (S, S), 0); ImageDraw.Draw(mask).rounded_rectangle([0, 0, S, S], radius=S * 0.22, fill=255)
rounded = Image.new('RGBA', (S, S), (0, 0, 0, 0)); rounded.paste(full, mask=mask)

save(full, 'assets/icon.png', 1024)
save(bg, 'assets/android-icon-background.png', 1024)
save(fg, 'assets/android-icon-foreground.png', 1024)
save(glyph(0.85, mono=True), 'assets/android-icon-monochrome.png', 1024)
save(rounded, 'assets/splash-icon.png', 1024)
save(rounded, 'assets/favicon.png', 48)
os.makedirs(os.path.join(ROOT, 'fastlane/metadata/android/en-US/images'), exist_ok=True)
save(rounded, 'fastlane/metadata/android/en-US/images/icon.png', 512)
