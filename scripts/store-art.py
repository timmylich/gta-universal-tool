"""Store and promo art from the raw screenshots in art/screens: per language one wide panorama
(art/promo/<lang>.png, 4320x1920) and its four 1080x1920 slices as store screenshots
(fastlane/metadata/android/<lang>/images/phoneScreenshots/1-4.png). The background runs across the slices,
so they read as one picture side by side. Needs Windows fonts (Segoe UI). Run: python scripts/store-art.py"""
import math, os
from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.join(os.path.dirname(__file__), '..')
W, H, N = 1080, 1920, 4
TOP, BOTTOM = (255, 176, 32), (232, 41, 92)  # icon sunset colors
BG = (17, 18, 20)
FONT_BOLD = 'C:/Windows/Fonts/segoeuib.ttf'
FONT = 'C:/Windows/Fonts/segoeui.ttf'

TEXT = {
  'ru-RU': [('Моддинг GTA SA прямо на телефоне', 'Текстуры, модели и звук в одном приложении'),
            ('Текстуры с превью', 'Замена из PNG в пару нажатий'),
            ('Архивы IMG', 'Модели, коллизии, пересборка'),
            ('Понятная инструкция', 'И резервная копия каждого файла')],
  'en-US': [('Mod GTA SA right on your phone', 'Textures, models and audio in one app'),
            ('Textures with previews', 'Replace from PNG in a few taps'),
            ('IMG archives', 'Models, collisions, rebuild'),
            ('Built-in guide', 'And a backup of every file')],
  'es-ES': [('Modea GTA SA desde el teléfono', 'Texturas, modelos y audio en una sola app'),
            ('Texturas con vista previa', 'Reemplaza desde PNG en pocos toques'),
            ('Archivos IMG', 'Modelos, colisiones, reconstrucción'),
            ('Guía integrada', 'Y copia de seguridad de cada archivo')],
  'id':    [('Modding GTA SA langsung di ponsel', 'Tekstur, model, dan audio dalam satu aplikasi'),
            ('Tekstur dengan pratinjau', 'Ganti dari PNG dalam beberapa ketukan'),
            ('Arsip IMG', 'Model, kolisi, bangun ulang'),
            ('Panduan bawaan', 'Dan cadangan setiap file')],
}


def background():
  bg = Image.new('RGB', (W * N, H), BG)
  glow = Image.new('RGB', (W * N, H), (0, 0, 0))
  d = ImageDraw.Draw(glow)
  # Glows sit on the slice borders, so every slice continues into the next one.
  for x, y, r, c in [(-100, 1500, 700, BOTTOM), (W, 300, 650, TOP), (2 * W, 1650, 700, BOTTOM), (3 * W, 250, 650, TOP), (4 * W + 100, 1500, 700, BOTTOM)]:
    d.ellipse((x - r, y - r, x + r, y + r), fill=tuple(int(v * 0.55) for v in c))
  glow = glow.filter(ImageFilter.GaussianBlur(260))
  out = ImageChops.add(bg, glow)
  # A thin wave across the whole panorama.
  wave = Image.new('L', bg.size, 0)
  wd = ImageDraw.Draw(wave)
  pts = [(x, 1180 + 140 * math.sin(x / (W * N) * math.pi * 3)) for x in range(0, W * N + 1, 8)]
  wd.line(pts, fill=90, width=6)
  wave = wave.filter(ImageFilter.GaussianBlur(2))
  out.paste(Image.new('RGB', bg.size, TOP), (0, 0), wave)
  return out


def wrap(draw, text, font, width):
  lines, cur = [], ''
  for w in text.split():
    t = (cur + ' ' + w).strip()
    if draw.textlength(t, font=font) <= width or not cur: cur = t
    else: lines.append(cur); cur = w
  return lines + [cur]


def phone(shot, width):
  s = shot.resize((width, round(shot.height * width / shot.width)), Image.LANCZOS)
  bezel, r = 18, 70
  ph = Image.new('RGBA', (s.width + 2 * bezel, s.height + 2 * bezel), (0, 0, 0, 0))
  d = ImageDraw.Draw(ph)
  d.rounded_rectangle((0, 0, ph.width - 1, ph.height - 1), r, fill=(8, 8, 10, 255), outline=(58, 60, 66, 255), width=4)
  m = Image.new('L', s.size, 0)
  ImageDraw.Draw(m).rounded_rectangle((0, 0, s.width - 1, s.height - 1), r - bezel, fill=255)
  ph.paste(s, (bezel, bezel), m)
  return ph


def render(lang, shots, icon, bg):
  pano = bg.copy()
  d = ImageDraw.Draw(pano)
  big = ImageFont.truetype(FONT_BOLD, 92)
  small = ImageFont.truetype(FONT, 46)
  brand = ImageFont.truetype(FONT_BOLD, 44)
  for i, (title, sub) in enumerate(TEXT[lang]):
    x0, y = i * W + 90, 150
    if i == 0:
      ic = icon.resize((96, 96), Image.LANCZOS)
      m = Image.new('L', ic.size, 0)
      ImageDraw.Draw(m).rounded_rectangle((0, 0, 95, 95), 24, fill=255)
      pano.paste(ic, (x0, y - 20), m)
      d.text((x0 + 120, y + 28), 'GTA Universal Tool', font=brand, fill=(242, 242, 243), anchor='lm')
      y += 130
    d.rounded_rectangle((x0, y, x0 + 90, y + 10), 5, fill=TOP)
    y += 40
    for line in wrap(d, title, big, W - 180):
      d.text((x0, y), line, font=big, fill=(242, 242, 243))
      y += 108
    y += 10
    for line in wrap(d, sub, small, W - 180):
      d.text((x0, y), line, font=small, fill=(176, 177, 184))
      y += 60
    ph = phone(shots[i], 720)
    px, py = i * W + (W - ph.width) // 2, max(y + 70, 760)
    shadow = Image.new('L', pano.size, 0)
    ImageDraw.Draw(shadow).rounded_rectangle((px + 10, py + 30, px + ph.width - 10, py + ph.height + 30), 80, fill=170)
    pano.paste((0, 0, 0), (0, 0), shadow.filter(ImageFilter.GaussianBlur(40)))
    pano.paste(ph, (px, py), ph)
  return pano


def main():
  shots = [Image.open(os.path.join(ROOT, f'art/screens/{i}.png')).convert('RGB') for i in range(1, N + 1)]
  icon = Image.open(os.path.join(ROOT, 'fastlane/metadata/android/en-US/images/icon.png')).convert('RGB')
  bg = background()
  os.makedirs(os.path.join(ROOT, 'art/promo'), exist_ok=True)
  for lang in TEXT:
    pano = render(lang, shots, icon, bg)
    pano.save(os.path.join(ROOT, f'art/promo/{lang}.png'), optimize=True)
    out = os.path.join(ROOT, f'fastlane/metadata/android/{lang}/images/phoneScreenshots')
    os.makedirs(out, exist_ok=True)
    for i in range(N):
      pano.crop((i * W, 0, (i + 1) * W, H)).save(os.path.join(out, f'{i + 1}.png'), optimize=True)
    print(lang)


main()
