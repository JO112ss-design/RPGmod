"""Textures for the Lich King (robe, hood and crown over a wither skeleton; burning eyes) and the
Spider Queen (her whole body on the spider layout; eyes), and their summoning items."""
import os
import random
from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), '..', '..', 'src', 'main', 'resources', 'assets', 'rpgmod', 'textures')
ENT = os.path.join(ROOT, 'entity')
ITEM = os.path.join(ROOT, 'item')


def hexc(c, a=255):
    return ((c >> 16) & 255, (c >> 8) & 255, c & 255, a)


def shade(c, f):
    return tuple(max(0, min(255, int(x * f))) for x in c[:3]) + (c[3] if len(c) > 3 else 255,)


def faces(u, v, w, h, d):
    return {
        'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d),
        'right': (u, v + d, d, h), 'front': (u + d, v + d, w, h),
        'left': (u + d + w, v + d, d, h), 'back': (u + 2 * d + w, v + d, w, h),
    }


def paint(img, box, fn, rnd, noise=(0.88, 1.08)):
    for name, (x0, y0, w, h) in faces(*box).items():
        for y in range(h):
            for x in range(w):
                c = fn(name, x, y, w, h)
                if c is None:
                    continue
                if isinstance(c, int):
                    c = hexc(c)
                img.putpixel((x0 + x, y0 + y), shade(c, rnd.uniform(*noise)))


# ------------------------------------------------------------------ lich

def lich_robe():
    rnd = random.Random(11)
    img = Image.new('RGBA', (64, 32), (0, 0, 0, 0))
    robe, robe_d, gold, gold_d, gem = 0x3A1E5A, 0x26123E, 0xE0B040, 0x9A7420, 0x7FE8FF

    # the hood (hat box): everything but the face, gold-rimmed, with a crown on top
    def hood(face, x, y, w, h):
        if face == 'bottom':
            return None
        if face == 'top':
            return gold if (x in (0, 7) or y in (0, 7)) else robe
        if face == 'front':
            if y <= 1:
                return gold if y == 0 else gold_d
            if x in (0, 7):
                return gold_d
            return None
        if y <= 1:
            # the crown band with gems
            if face in ('left', 'right', 'back') and y == 0 and x % 3 == 1:
                return gem
            return gold if y == 0 else gold_d
        return robe if (x + y) % 5 else robe_d
    paint(img, (32, 0, 8, 8, 8), hood, rnd)

    def body(face, x, y, w, h):
        if face == 'bottom':
            return None
        if face == 'top':
            return robe
        if y == 0 or (face in ('front', 'back') and x in (3, 4)):
            return gold if face == 'front' else gold_d
        if y == 7:
            return gold_d  # a sash
        if face == 'front' and y in (3, 4) and x in (2, 5):
            return gem  # clasps
        return robe if (x * 3 + y) % 7 else robe_d
    paint(img, (16, 16, 8, 12, 4), body, rnd)

    def arm(face, x, y, w, h):
        if face == 'bottom':
            return None
        if y <= 1:
            return gold_d  # pauldron rim
        if y >= 9:
            return robe_d if face != 'top' and rnd.random() > 0.3 else None  # ragged sleeve
        return robe if (x + y) % 4 else robe_d
    paint(img, (40, 16, 4, 12, 4), arm, rnd)

    def leg(face, x, y, w, h):
        if face in ('top', 'bottom'):
            return None
        if y >= 10 and rnd.random() < 0.45:
            return None  # tattered hem
        if y == 9 and face in ('front', 'back'):
            return gold_d
        return robe if (x + y * 2) % 6 else robe_d
    paint(img, (0, 16, 4, 12, 4), leg, rnd)
    img.save(os.path.join(ENT, 'lich_robe.png'))


def lich_eyes():
    img = Image.new('RGBA', (64, 32), (0, 0, 0, 0))
    for (x, y) in [(9, 12), (10, 12), (13, 12), (14, 12), (9, 13), (14, 13)]:
        img.putpixel((x, y), (255, 255, 255, 255))
    img.save(os.path.join(ENT, 'lich_eyes.png'))


# ------------------------------------------------------------------ spider queen

def spider_queen():
    rnd = random.Random(21)
    img = Image.new('RGBA', (64, 32), (0, 0, 0, 0))
    black, black_l, violet, violet_l, mark, spine = 0x17121F, 0x2A2236, 0x4A2A6A, 0x7A4AA8, 0xC060FF, 0xD8C8E8

    # head (32,4) 8x8x8: black, violet rim, fangs at the bottom of the front
    def head(face, x, y, w, h):
        if face == 'front':
            if y >= 6 and x in (2, 5):
                return 0xE8E0D0  # fangs
            if y == 0:
                return violet
            return black_l if (x + y) % 3 else black
        if face == 'top':
            return violet if (x in (1, 6) and 1 <= y <= 6) else black_l
        return black if (x + y) % 4 else black_l
    paint(img, (32, 4, 8, 8, 8), head, rnd)

    # neck (0,0) 6x6x6
    def neck(face, x, y, w, h):
        return violet if (x + y) % 3 == 0 else black
    paint(img, (0, 0, 6, 6, 6), neck, rnd)

    # abdomen (0,12) 10x8x12: top 10x12 with a glowing crown-and-skull mark, spines along the sides
    def abdomen(face, x, y, w, h):
        if face == 'top':
            cx = x - 4.5
            if (abs(cx) <= 1 and 2 <= y <= 9) or (y == 3 and abs(cx) <= 3) or (y in (8, 9) and abs(cx) <= 2):
                return mark  # a glowing violet cross / hourglass
            if y == 0 and x % 2 == 0:
                return spine
            if x in (0, 9) and y % 3 == 1:
                return spine
            return violet if (x + y) % 5 == 0 else black_l
        if face == 'bottom':
            return black
        if face in ('left', 'right'):
            if y == 0 and x % 3 == 0:
                return spine
            return violet if y in (3, 4) and x % 4 < 2 else black_l
        return violet if (x + y) % 4 == 0 else black
    paint(img, (0, 12, 10, 8, 12), abdomen, rnd)

    # legs (18,0) 16x2x2: black with violet bands, pale tips
    def leg(face, x, y, w, h):
        if face in ('right', 'left'):
            return black
        if face in ('top', 'front', 'back', 'bottom'):
            if x <= 1:
                return spine  # tips (the far end of the leg box)
            if x % 5 == 0:
                return violet_l
            return black_l if (x + y) % 2 else black
        return black
    paint(img, (18, 0, 16, 2, 2), leg, rnd)
    img.save(os.path.join(ENT, 'spider_queen.png'))


def spider_queen_eyes():
    img = Image.new('RGBA', (64, 32), (0, 0, 0, 0))
    # the head's front face is at (40,12) 8x8
    for (x, y) in [(1, 2), (2, 2), (5, 2), (6, 2), (2, 1), (5, 1), (3, 3), (4, 3), (0, 3), (7, 3)]:
        img.putpixel((40 + x, 12 + y), (255, 255, 255, 255))
    img.save(os.path.join(ENT, 'spider_queen_eyes.png'))


# ------------------------------------------------------------------ items

def phylactery():
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    gold, gold_d, glass, soul = (224, 176, 64, 255), (150, 110, 30, 255), (200, 230, 255, 160), (127, 232, 255, 255)
    for y in range(2, 15):
        for x in range(3, 13):
            d = ((x - 7.5) ** 2 / 20 + (y - 8.5) ** 2 / 36)
            if d <= 1:
                img.putpixel((x, y), glass)
    for y in range(5, 13):
        for x in range(5, 11):
            if ((x - 7.5) ** 2 + (y - 9) ** 2) <= 5:
                f = 1 - ((x - 7.5) ** 2 + (y - 9) ** 2) / 5
                img.putpixel((x, y), (int(127 + 128 * f), int(232 + 23 * f), 255, 255))
    for x in range(4, 12):
        img.putpixel((x, 2), gold)
        img.putpixel((x, 14), gold_d)
    for x in range(6, 10):
        img.putpixel((x, 1), gold_d)
    img.putpixel((7, 0), gold)
    img.putpixel((8, 0), gold)
    img.save(os.path.join(ITEM, 'soul_phylactery.png'))


def cocoon():
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    rnd = random.Random(5)
    for y in range(1, 15):
        for x in range(2, 14):
            d = ((x - 7.5) ** 2 / 26 + (y - 8) ** 2 / 45)
            if d <= 1:
                silk = 215 + rnd.randint(-25, 25)
                c = (silk, silk, min(255, silk + 12), 255)
                if (x + y * 2) % 7 == 0:
                    c = (170, 160, 185, 255)
                if ((x - 7.5) ** 2 + (y - 8) ** 2) < 4:
                    c = (150, 80, 200, 255)  # the violet glow of the egg within
                img.putpixel((x, y), c)
    img.save(os.path.join(ITEM, 'queen_cocoon.png'))


lich_robe()
lich_eyes()
spider_queen()
spider_queen_eyes()
phylactery()
cocoon()
print('ok')
