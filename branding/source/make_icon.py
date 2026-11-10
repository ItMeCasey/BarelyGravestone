"""Draws the Barely Gravestone icon as 32x32 pixel art and writes it at several sizes.

Run from the repository root: python branding/source/make_icon.py
Writes branding/icon/icon_<size>.png, branding/icon/icon.svg and barelygravestone_logo.png (for the jar).

The picture: a gravestone on a grassy mound, a thin pike with a head on top, a gold glow behind it, on the studio's night tile.
"""
import math
from pathlib import Path

from PIL import Image

N = 32
OUT = Path(__file__).resolve().parent.parent / "icon"

BG = (24, 22, 38)
BG_EDGE = (46, 42, 70)
BG_TOP = (58, 54, 88)
GOLD = (242, 179, 61)
GOLD_HI = (255, 226, 140)
GOLD_LO = (178, 116, 32)
STONE = (128, 128, 140)
STONE_HI = (170, 170, 184)
STONE_LO = (78, 78, 92)
WOOD = (110, 74, 44)
WOOD_HI = (146, 104, 66)
SKIN = (205, 140, 98)
SKIN_LO = (160, 100, 68)
HAIR = (104, 62, 150)
HAIR_LO = (70, 40, 108)
GRASS = (70, 150, 70)
GRASS_LO = (44, 102, 50)
EYE = (30, 24, 40)

px = [[None] * N for _ in range(N)]


def put(x, y, c):
    if 0 <= x < N and 0 <= y < N:
        px[y][x] = c


def rect(x0, y0, x1, y1, c):
    for yy in range(y0, y1 + 1):
        for xx in range(x0, x1 + 1):
            put(xx, yy, c)


def mix(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


# Night tile with stepped corners.
R = 4
for y in range(N):
    for x in range(N):
        cx = min(max(x, R - 0.5), N - R - 0.5)
        cy = min(max(y, R - 0.5), N - R - 0.5)
        if math.hypot(x - cx, y - cy) <= R:
            px[y][x] = BG
for y in range(N):
    for x in range(N):
        if px[y][x] is None:
            continue
        edge = any(not (0 <= x + dx < N and 0 <= y + dy < N) or px[y + dy][x + dx] is None for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        if edge:
            px[y][x] = BG_TOP if y < N // 2 else BG_EDGE

# Gold glow behind the head: concentric dithered discs.
for y in range(N):
    for x in range(N):
        if px[y][x] is None or x in (0, N - 1) or y in (0, N - 1):
            continue
        d = math.hypot(x - 15.5, y - 10.5)
        if d < 9.5:
            t = max(0.0, 1.0 - d / 9.5)
            if t > 0.55 or (t > 0.25 and (x + y) % 2 == 0):
                px[y][x] = mix(BG, GOLD_LO, 0.55 if t > 0.55 else 0.35)

# Ground: a grassy mound with a dark underside.
for y in range(24, 31):
    for x in range(1, 31):
        if px[y][x] is None:
            continue
        top = 26 - round(2.2 * math.cos((x - 15.5) / 15.5 * math.pi / 2))
        if y >= top:
            px[y][x] = GRASS if y <= top + 1 else (GRASS_LO if y <= top + 2 else (60, 44, 36))
for x in range(2, 30):
    if px[26][x] == GRASS and x % 3 == 0:
        put(x, 25, GRASS)

# Stone base slab.
rect(9, 23, 22, 25, STONE)
rect(9, 23, 22, 23, STONE_HI)
rect(9, 25, 22, 25, STONE_LO)

# The pike: two pixels wide, from the slab to the head.
rect(15, 14, 16, 22, WOOD)
rect(15, 14, 15, 22, WOOD_HI)
put(15, 21, WOOD)

# The head on the pike: 10x10 player-style head with hair, a face and two eyes.
rect(11, 4, 20, 13, SKIN)
rect(11, 4, 20, 6, HAIR)
rect(11, 7, 11, 9, HAIR)
rect(20, 7, 20, 9, HAIR)
rect(11, 4, 20, 4, mix(HAIR, (255, 255, 255), 0.25))
rect(12, 12, 19, 13, SKIN_LO)
put(13, 9, (255, 255, 255))
put(14, 9, EYE)
put(17, 9, EYE)
put(18, 9, (255, 255, 255))
put(15, 11, SKIN_LO)
put(16, 11, SKIN_LO)
# outline
for x in range(10, 22):
    put(x, 3, (30, 22, 40))
    put(x, 14, (30, 22, 40))
for y in range(3, 15):
    put(10, y, (30, 22, 40))
    put(21, y, (30, 22, 40))

# Gold sparkle marks, like the other studio icons.
for (sx, sy) in ((5, 7), (26, 5), (27, 14)):
    put(sx, sy, GOLD_HI)
    put(sx - 1, sy, GOLD)
    put(sx + 1, sy, GOLD)
    put(sx, sy - 1, GOLD)
    put(sx, sy + 1, GOLD)

img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
for y in range(N):
    for x in range(N):
        if px[y][x] is not None:
            img.putpixel((x, y), px[y][x] + (255,))

OUT.mkdir(parents=True, exist_ok=True)
for size in (32, 64, 128, 256, 512, 1024):
    img.resize((size, size), Image.NEAREST).save(OUT / f"icon_{size}.png", optimize=True)
img.resize((512, 512), Image.NEAREST).save(OUT / "barelygravestone_logo.png", optimize=True)

rects = []
for y in range(N):
    for x in range(N):
        c = px[y][x]
        if c is not None:
            rects.append(f'<rect x="{x}" y="{y}" width="1.02" height="1.02" fill="#{c[0]:02x}{c[1]:02x}{c[2]:02x}"/>')
(OUT / "icon.svg").write_text(
    f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {N} {N}" shape-rendering="crispEdges">' + "".join(rects) + "</svg>\n",
    encoding="utf-8",
)
print("wrote", OUT)
