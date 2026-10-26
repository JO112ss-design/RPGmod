"""Textures for the dungeon: the altar (side, top, bottom; a glowing version for an open dungeon) and
the offerings (key, sealed key, slime crown, golem core). 16x16 pixel art."""
import os
import random
from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), '..', '..', 'src', 'main', 'resources', 'assets', 'rpgmod', 'textures')
BLOCK = os.path.join(ROOT, 'block')
ITEM = os.path.join(ROOT, 'item')
os.makedirs(BLOCK, exist_ok=True)
os.makedirs(ITEM, exist_ok=True)


def hexc(c, a=255):
    return ((c >> 16) & 255, (c >> 8) & 255, c & 255, a)


def shade(c, f):
    return tuple(max(0, min(255, int(x * f))) for x in c[:3]) + (c[3] if len(c) > 3 else 255,)


def bricks(rnd, base=0x3A3A44, mortar=0x22222A):
    img = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            row = y // 4
            off = 4 if row % 2 else 0
            if y % 4 == 3 or (x + off) % 8 == 7:
                c = hexc(mortar)
            else:
                c = shade(hexc(base), rnd.uniform(0.85, 1.12))
            img.putpixel((x, y), c)
    return img


RUNES = [
    [(7, 5), (8, 5), (7, 6), (7, 7), (8, 7), (8, 8), (7, 9), (8, 9)],
    [(6, 6), (9, 6), (7, 7), (8, 7), (7, 8), (8, 8), (6, 9), (9, 9)],
]


def altar_side(active):
    rnd = random.Random(3)
    img = bricks(rnd)
    gold, gold_d = hexc(0xE0B040), hexc(0x9A7420)
    for x in range(16):
        img.putpixel((x, 0), gold)
        img.putpixel((x, 1), gold_d)
        img.putpixel((x, 15), gold_d)
    # a carved rune panel in the middle
    for y in range(4, 12):
        for x in range(5, 11):
            img.putpixel((x, y), hexc(0x1A1420))
    glow = hexc(0xE0B0FF) if active else hexc(0x5A3A80)
    for (x, y) in RUNES[0]:
        img.putpixel((x, y), glow)
    if active:
        for (x, y) in [(5, 4), (10, 4), (5, 11), (10, 11)]:
            img.putpixel((x, y), hexc(0xB070FF))
    img.save(os.path.join(BLOCK, 'dungeon_altar_side' + ('_active' if active else '') + '.png'))


def altar_top(active):
    rnd = random.Random(5)
    img = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), shade(hexc(0x2E2E38), rnd.uniform(0.85, 1.1)))
    gold = hexc(0xE0B040)
    # a gold ring with a sigil inside
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if 5.6 <= d <= 6.6:
                img.putpixel((x, y), gold)
            elif d < 4.2:
                if active:
                    f = 1 - d / 4.2
                    img.putpixel((x, y), (int(150 + 100 * f), int(90 + 140 * f), 255, 255))
                else:
                    img.putpixel((x, y), hexc(0x1A1420))
    for (x, y) in [(7, 1), (8, 1), (1, 7), (1, 8), (14, 7), (14, 8), (7, 14), (8, 14)]:
        img.putpixel((x, y), hexc(0xB070FF) if active else hexc(0x5A3A80))
    img.save(os.path.join(BLOCK, 'dungeon_altar_top' + ('_active' if active else '') + '.png'))


def altar_bottom():
    rnd = random.Random(7)
    img = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), shade(hexc(0x1E1A26), rnd.uniform(0.8, 1.15)))
    img.save(os.path.join(BLOCK, 'dungeon_altar_bottom.png'))


def draw(rows, palette):
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != '.':
                img.putpixel((x, y), hexc(palette[ch]))
    return img


KEY = [
    "................",
    "....aaaa........",
    "...aAAAAa.......",
    "..aAa..aAa......",
    "..aA....Aa......",
    "..aA....Aa......",
    "..aAa..aAa......",
    "...aAAAAa.......",
    "....aGGa........",
    ".....GG.........",
    ".....GG.........",
    ".....GGgg.......",
    ".....GG.........",
    ".....GGgg.......",
    ".....gg.........",
    "................",
]


def key_item(name, pal):
    img = draw(KEY, pal)
    img.save(os.path.join(ITEM, name + '.png'))


def crown():
    rows = [
        "................",
        "................",
        "................",
        "..y....y....y...",
        "..yY..yYy..Yy...",
        "..yYy.yYy.yYy...",
        "..yYYyYYYyYYy...",
        "..yYYYYgYYYYy...",
        "..yYYYgGgYYYy...",
        "..yYYYYgYYYYy...",
        "..dddddddddd....",
        "................",
        "................",
        "................",
        "................",
        "................",
    ]
    draw(rows, {'y': 0xB08020, 'Y': 0xF0C040, 'g': 0x40A040, 'G': 0x90F070, 'd': 0x806010}).save(os.path.join(ITEM, 'slime_crown.png'))


def core():
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    rnd = random.Random(9)
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d <= 6.3:
                if d <= 2.6:
                    f = 1 - d / 2.6
                    img.putpixel((x, y), (255, int(140 + 100 * f), int(60 + 120 * f), 255))
                elif d <= 3.4:
                    img.putpixel((x, y), hexc(0xC05020))
                else:
                    shade_f = 1.15 - (x + y) / 40
                    img.putpixel((x, y), shade(hexc(0x6A6A72), shade_f * rnd.uniform(0.9, 1.1)))
    for (x, y) in [(3, 6), (4, 7), (11, 9), (12, 8), (7, 2), (8, 13)]:
        img.putpixel((x, y), hexc(0xFF8040))
    img.save(os.path.join(ITEM, 'golem_core.png'))


for a in (False, True):
    altar_side(a)
    altar_top(a)
altar_bottom()
key_item('dungeon_key', {'a': 0x9A6A20, 'A': 0xF0C040, 'G': 0xE0B040, 'g': 0xA07820})
key_item('sealed_key', {'a': 0x4A4A55, 'A': 0x9A9AAA, 'G': 0x8A8A98, 'g': 0xC02828})
crown()
core()
print('ok')
