"""Textures for the skill props (assets/rpgmod/textures/entity/prop_*.png): the pirate's cannon,
powder keg and flag. Each model box uses the standard box layout from its texture offset:

    row v:        [d][ top w ][ bottom w ]
    row v + d:    [ side d ][ front w ][ side d ][ back w ]   (h tall)
"""
import os, random
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), '..', '..', 'src', 'main', 'resources', 'assets', 'rpgmod', 'textures', 'entity')
os.makedirs(OUT, exist_ok=True)
rnd = random.Random(7)


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c[:3]) + (255,)


def box(img, u, v, w, h, d, paint):
    """Calls paint(face, x, y, fw, fh) -> colour for every texel of a box's faces."""
    faces = {
        'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d),
        'east': (u, v + d, d, h), 'north': (u + d, v + d, w, h),
        'west': (u + d + w, v + d, d, h), 'south': (u + 2 * d + w, v + d, w, h),
    }
    for face, (fx, fy, fw, fh) in faces.items():
        for y in range(fh):
            for x in range(fw):
                c = paint(face, x, y, fw, fh)
                if c is not None:
                    img.putpixel((fx + x, fy + y), c)


def wood(base, grain=0.12, plank=None):
    def paint(face, x, y, w, h):
        k = 1 + rnd.uniform(-grain, grain)
        if plank and (y % plank == 0):
            k *= 0.72
        if face in ('top', 'bottom'):
            k *= 1.08
        return shade(base, k)
    return paint


def metal(base, hi=1.35):
    def paint(face, x, y, w, h):
        k = 1 + rnd.uniform(-0.06, 0.06)
        if face == 'top':
            k *= hi
        if face in ('east', 'west') and y == 0:
            k *= 1.2
        return shade(base, k)
    return paint


# ---- cannon (64x64)
img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
box(img, 0, 0, 12, 5, 14, wood((120, 78, 42), plank=3))           # carriage


def wheel(face, x, y, w, h):
    if face in ('east', 'west'):
        cx, cy = (w - 1) / 2, (h - 1) / 2
        r = max(abs(x - cx), abs(y - cy))
        if r >= 3.6:
            return shade((70, 70, 76), 1 + rnd.uniform(-0.05, 0.05))  # iron rim
        if r <= 0.8:
            return (40, 40, 44, 255)                                  # hub
        if abs(x - cx) < 0.6 or abs(y - cy) < 0.6:
            return shade((150, 104, 60), 1.0)                         # spokes
        return shade((92, 60, 32), 0.85)
    return shade((70, 70, 76), 1 + rnd.uniform(-0.05, 0.05))


box(img, 0, 19, 2, 9, 9, wheel)
box(img, 0, 37, 6, 6, 17, metal((44, 46, 52)))                    # barrel
box(img, 24, 19, 7, 7, 2, metal((150, 112, 48), 1.2))             # bronze muzzle ring


def muzzle_hole(face, x, y, w, h):
    if face == 'north' and 2 <= x <= 4 and 2 <= y <= 4:
        return (12, 12, 14, 255)
    return None


box(img, 24, 19, 7, 7, 2, lambda f, x, y, w, h: muzzle_hole(f, x, y, w, h) or shade((150, 112, 48), (1.2 if f == 'top' else 1) + rnd.uniform(-0.05, 0.05)))
box(img, 46, 37, 3, 3, 2, metal((44, 46, 52)))                    # cascabel knob
img.save(os.path.join(OUT, 'prop_cannon.png'))

# ---- powder keg (64x32)
img = Image.new('RGBA', (64, 32), (0, 0, 0, 0))


def keg(face, x, y, w, h):
    base = (110, 66, 34)
    k = 1 + rnd.uniform(-0.08, 0.08)
    if face in ('top', 'bottom'):
        cx, cy = (w - 1) / 2, (h - 1) / 2
        if face == 'top' and abs(x - cx) < 1.5 and abs(y - cy) < 1.5:
            return (30, 22, 18, 255)  # the fuse hole
        return shade(base, k * (1.15 if (x + y) % 3 else 0.95))
    if x % 3 == 0:
        k *= 0.75  # staves
    if 4 <= y <= 5:
        return shade((200, 40, 30), k)  # a red powder band
    return shade(base, k)


box(img, 0, 0, 10, 10, 10, keg)
box(img, 0, 20, 11, 1, 11, metal((96, 96, 104), 1.1))
box(img, 44, 0, 1, 3, 1, lambda f, x, y, w, h: (220, 200, 150, 255) if y else (255, 180, 60, 255))
img.save(os.path.join(OUT, 'prop_keg.png'))

# ---- flag (64x64)
img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
box(img, 0, 0, 2, 48, 2, wood((96, 66, 40), grain=0.15))
box(img, 10, 20, 6, 2, 6, metal((70, 70, 76)))
SKULL = [
    '..........WWWWWW..........',
    '........WWWWWWWWWW........',
    '.......WWWWWWWWWWWW.......',
    '.......WW..WWWW..WW.......',
    '.......WW..WWWW..WW.......',
    '.......WWWWWW.WWWWW.......',
    '........WWWW...WWWW.......',
    '.........WWWWWWWWW........',
    '..........W.W.W.W.........',
    '....W..............W......',
    '.....WW..........WW.......',
    '.......WWW....WWW.........',
    '..........WWWW............',
]


def cloth(face, x, y, w, h):
    if face in ('north', 'south'):
        sx = x if face == 'north' else w - 1 - x
        # the skull and bones, centred on the cloth
        col = sx + 3
        if 0 <= y < len(SKULL) and 0 <= col < len(SKULL[0]) and SKULL[y][col] == 'W':
            return (236, 232, 220, 255)
        k = 1 + rnd.uniform(-0.06, 0.06)
        return shade((22, 22, 26), k)
    return shade((22, 22, 26), 1.0)


box(img, 10, 0, 20, 13, 1, cloth)
img.save(os.path.join(OUT, 'prop_flag.png'))
print('wrote props to', os.path.abspath(OUT))
