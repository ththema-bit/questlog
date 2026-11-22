#!/usr/bin/env python3
"""Erzeugt App-Icon, Benachrichtigungs-Icon und Widget-Bilder aus den Pixel-Icons in IconArt.kt.
Aufruf aus Questlog/android:  python3 tools/gen_assets.py   (braucht Pillow für die PNGs)"""
import os, re
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "app/src/main/java/io/github/ththemabit/questlog/ui/pixel/IconArt.kt")
RES = os.path.join(ROOT, "app/src/main/res")
text = open(SRC, encoding="utf-8").read()
PAL = {m.group(1): m.group(2) for m in re.finditer(r"'(.)' to 0x([0-9A-Fa-f]{8})", text)}
ICONS = {n: b.strip("\n").split("\n") for n, b in re.findall(r'"(\w+)" to """\n(.*?)"""', text, re.S)}

def runs(grid, pred):
    """Waagerechte Läufe gleicher Farbe -> Rechtecke (x, y, w, colorKey)."""
    out = []
    for y, row in enumerate(grid):
        x = 0
        while x < len(row):
            c = row[x]
            if pred(c):
                s = x
                while x < len(row) and row[x] == c:
                    x += 1
                out.append((s, y, x - s, c))
            else:
                x += 1
    return out

def vector(grid, size_dp, viewport, offset, scale, mono=None):
    paths = {}
    for (x, y, w, c) in runs(grid, lambda ch: ch != '.'):
        key = mono or ('#' + PAL[c][2:])
        px, py = offset + x * scale, offset + y * scale
        paths.setdefault(key, []).append(f"M{px:g},{py:g}h{w*scale:g}v{scale:g}h{-w*scale:g}z")
    body = "\n".join(f'    <path android:fillColor="{col}" android:pathData="{"".join(p)}" />' for col, p in paths.items())
    return (f'<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="{size_dp}dp" '
            f'android:height="{size_dp}dp" android:viewportWidth="{viewport}" android:viewportHeight="{viewport}">\n{body}\n</vector>\n')

def write(path, content):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    open(path, "w", encoding="utf-8").write(content)

flame = ICONS["flame"]
# Launcher: 108er Viewport, Flamme 16 Pixel * 4 = 64 (innerhalb der 66er Schutzzone)
write(f"{RES}/drawable/ic_launcher_foreground.xml", vector(flame, 108, 108, 22, 4))
write(f"{RES}/drawable/ic_launcher_monochrome.xml", vector(flame, 108, 108, 22, 4, mono="#FFFFFFFF"))
# Hintergrund: dunkle Mauer mit Fugen
bg = ['<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">',
      '    <path android:fillColor="#FF15100B" android:pathData="M0,0h108v108h-108z" />']
mortar = []
for row in range(0, 9):
    y = row * 12
    mortar.append(f"M0,{y+11}h108v1h-108z")
    off = 0 if row % 2 == 0 else 12
    for x in range(-24 + off, 108, 24):
        if x >= 0:
            mortar.append(f"M{x+23},{y}h1v11h-1z")
bg.append(f'    <path android:fillColor="#FF0D0906" android:pathData="{"".join(mortar)}" />')
bg.append('    <path android:fillColor="#FF1D1610" android:pathData="' + "".join(f"M0,{r*12}h108v1h-108z" for r in range(9)) + '" />')
bg.append('</vector>\n')
write(f"{RES}/drawable/ic_launcher_background.xml", "\n".join(bg))
# Benachrichtigung: weiße Silhouette, 24dp
write(f"{RES}/drawable/ic_notification.xml", vector(flame, 24, 16, 0, 1, mono="#FFFFFFFF"))

# Widget-Icons als scharfe PNGs (nearest neighbour, nodpi)
os.makedirs(f"{RES}/drawable-nodpi", exist_ok=True)
for name in ["seal", "flame", "shield", "check", "scroll", "coin"]:
    g = ICONS[name]
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(g):
        for x, c in enumerate(row):
            if c != '.':
                v = int(PAL[c], 16)
                im.putpixel((x, y), ((v >> 16) & 255, (v >> 8) & 255, v & 255, 255))
    im.resize((96, 96), Image.NEAREST).save(f"{RES}/drawable-nodpi/px_{name}.png")
print("ok")
