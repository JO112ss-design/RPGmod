"""Outfits for the fallen adventurers (revenants): one 64x64 humanoid-layout texture per job, worn
over the zombie skin, plus the white eyes texture tinted per job in game.

Humanoid box layout: head (0,0), hood/hat (32,0), body (16,16), arm (40,16), leg (0,16)."""
import os
import random
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), '..', '..', 'src', 'main', 'resources', 'assets', 'rpgmod', 'textures', 'entity')
os.makedirs(OUT, exist_ok=True)

PARTS = {'head': (0, 0, 8, 8, 8), 'hat': (32, 0, 8, 8, 8), 'body': (16, 16, 8, 12, 4), 'arm': (40, 16, 4, 12, 4), 'leg': (0, 16, 4, 12, 4)}


def faces(part):
    u, v, w, h, d = PARTS[part]
    return {
        'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d),
        'right': (u, v + d, d, h), 'front': (u + d, v + d, w, h),
        'left': (u + d + w, v + d, d, h), 'back': (u + 2 * d + w, v + d, w, h),
    }


def hexc(c, a=255):
    return ((c >> 16) & 255, (c >> 8) & 255, c & 255, a)


def shade(c, f):
    return tuple(max(0, min(255, int(x * f))) for x in c[:3]) + (c[3],)


class Painter:
    def __init__(self, seed):
        self.img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
        self.rnd = random.Random(seed)

    def paint(self, part, fn, which=None, grime=0.12, tatter=0.0):
        """fn(face, x, y, w, h) -> colour int / (r,g,b,a) / None for each pixel of the part's faces."""
        for name, (x0, y0, w, h) in faces(part).items():
            if which and name not in which:
                continue
            for y in range(h):
                for x in range(w):
                    c = fn(name, x, y, w, h)
                    if c is None:
                        continue
                    if isinstance(c, int):
                        c = hexc(c)
                    # worn cloth: some noise, grime stains, frayed lower edges
                    c = shade(c, self.rnd.uniform(0.86, 1.08))
                    if self.rnd.random() < grime:
                        c = shade((c[0] * 0.6 + 60 * 0.4, c[1] * 0.6 + 66 * 0.4, c[2] * 0.6 + 40 * 0.4, c[3]), 0.8)
                    if tatter and name in ('front', 'back', 'left', 'right') and y >= h - 2 and self.rnd.random() < tatter * (1 if y == h - 1 else 0.4):
                        continue
                    self.img.putpixel((x0 + x, y0 + y), c)

    def save(self, name):
        self.img.save(os.path.join(OUT, name + '.png'))


def hood(main, trim=None, mask=None):
    """A hood on the hat box: everything but the face, which is framed (and maybe masked below the eyes)."""
    def fn(face, x, y, w, h):
        if face == 'bottom':
            return None
        if face == 'front':
            if y <= 1 or x == 0 or x == w - 1:
                return trim if trim and (y == 1 or (x in (0, w - 1) and y > 1)) else main
            if mask is not None and y >= 5:
                return mask
            return None
        if face in ('left', 'right') and y >= h - 1:
            return None
        return main
    return fn


def warrior():
    p = Painter(1)
    iron, iron_d, red, gold, chain_a, chain_b, leather = 0xA8B0B8, 0x6E747C, 0x9E2A2A, 0xD8B040, 0x8A9098, 0x5E646C, 0x6A4628

    def helmet(face, x, y, w, h):
        if face == 'bottom':
            return None
        if face == 'front':
            if y <= 2:
                return iron if y < 2 else iron_d
            if x in (3, 4) and y <= 5:
                return iron_d  # nose guard
            if (x <= 0 or x >= w - 1) and y <= 6:
                return iron_d
            return None
        if y >= h - 1 and face != 'top':
            return iron_d
        return iron if (x + y) % 5 else iron_d
    p.paint('head', helmet, grime=0.06)

    def chain(x, y):
        return chain_a if (x + y) % 2 == 0 else chain_b

    def body(face, x, y, w, h):
        if face in ('top', 'bottom'):
            return chain(x, y) if face == 'top' else None
        if y == 8:
            return gold if face == 'front' and x in (3, 4) else leather
        if face in ('front', 'back') and 2 <= x <= 5:
            if x in (2, 5):
                return gold
            return red
        return chain(x, y)
    p.paint('body', body, tatter=0.5)

    def arm(face, x, y, w, h):
        if face == 'bottom':
            return None
        if y <= 3:
            return iron if y < 3 else iron_d  # pauldron
        if y <= 7:
            return chain(x, y) if face != 'top' else None
        return None
    p.paint('arm', arm, tatter=0)

    def leg(face, x, y, w, h):
        if face in ('top', 'bottom'):
            return None
        if y >= 6:
            return iron if face == 'front' and y < 11 else iron_d
        return chain(x, y)
    p.paint('leg', leg)
    p.save('revenant_warrior')


def mage():
    p = Painter(2)
    robe, robe_d, gold, rune = 0x4A2C7A, 0x341E58, 0xC8A040, 0x9A6AFF
    p.paint('hat', hood(robe, gold), grime=0.08)

    def body(face, x, y, w, h):
        if face == 'bottom':
            return None
        if face == 'top':
            return robe
        if y == 7:
            return gold
        if face == 'front' and x in (3, 4):
            return gold if y < 7 else robe_d
        if face == 'back' and y in (3, 4) and x in (3, 4):
            return rune
        return robe if (x + y * 3) % 7 else robe_d
    p.paint('body', body, tatter=0.6)

    def arm(face, x, y, w, h):
        if face == 'bottom':
            return None
        if y <= 8:
            return robe if face != 'front' or (x + y) % 6 else robe_d
        if y == 9:
            return gold
        return None
    p.paint('arm', arm)

    def leg(face, x, y, w, h):
        if face in ('top', 'bottom'):
            return None
        if y == 10 and face in ('front', 'back'):
            return gold
        return robe if y % 4 else robe_d
    p.paint('leg', leg, tatter=0.8)
    p.save('revenant_mage')


def archer():
    p = Painter(3)
    green, green_d, leather, leather_d, strap = 0x3E6A2C, 0x2A4A1E, 0x7A5230, 0x55381E, 0x4A2E18
    p.paint('hat', hood(green, green_d), grime=0.1)

    def body(face, x, y, w, h):
        if face == 'bottom':
            return None
        if face == 'top':
            return green
        if y == 8:
            return strap
        if face == 'front' and (x == y - 1 or x == y - 2) and y < 8:
            return strap  # quiver strap across the chest
        if face == 'back' and 4 <= x <= 6 and 1 <= y <= 7:
            return leather_d if x == 5 else leather  # quiver
        return green if y < 8 else leather
    p.paint('body', body, tatter=0.4)

    def arm(face, x, y, w, h):
        if face == 'bottom':
            return None
        if y <= 4:
            return green if face != 'top' else green_d
        if 6 <= y <= 10:
            return leather if y not in (6, 10) else leather_d  # bracer
        return None
    p.paint('arm', arm)

    def leg(face, x, y, w, h):
        if face in ('top', 'bottom'):
            return None
        if y >= 8:
            return leather_d
        return leather
    p.paint('leg', leg)
    p.save('revenant_archer')


def priest():
    p = Painter(4)
    white, white_d, gold, gold_d = 0xE6E2D6, 0xB8B2A2, 0xE8C050, 0xB08A30
    p.paint('hat', hood(white, gold), grime=0.1)

    def cross(x, y):
        return (x in (3, 4) and 1 <= y <= 9) or (y in (3, 4) and 1 <= x <= 6)

    def body(face, x, y, w, h):
        if face == 'bottom':
            return None
        if face == 'top':
            return white
        if face in ('front', 'back') and cross(x, y):
            return gold if face == 'front' else gold_d
        if y == h - 1:
            return gold
        return white if (x * 3 + y) % 9 else white_d
    p.paint('body', body, grime=0.16)

    def arm(face, x, y, w, h):
        if face == 'bottom':
            return None
        if y <= 9:
            return white if (x + y) % 7 else white_d
        if y == 10:
            return gold
        return None
    p.paint('arm', arm, grime=0.16)

    def leg(face, x, y, w, h):
        if face in ('top', 'bottom'):
            return None
        if y == 11:
            return gold
        return white if y % 5 else white_d
    p.paint('leg', leg, grime=0.16, tatter=0.4)
    p.save('revenant_priest')


def thief():
    p = Painter(5)
    cloth, cloth_d, strap, boot = 0x3A3A44, 0x26262E, 0x5A3A22, 0x1A1A1E
    p.paint('hat', hood(cloth, cloth_d, mask=cloth_d), grime=0.08)

    def body(face, x, y, w, h):
        if face == 'bottom':
            return None
        if face == 'top':
            return cloth
        if face in ('front', 'back') and (x == y or x == 7 - y or x == y - 1 or x == 8 - y) and y < 8:
            return strap  # crossed straps
        if y == 8:
            return strap
        if face == 'front' and y in (9, 10) and x in (1, 2):
            return 0xA0A0A8  # a knife sheath's tip
        return cloth if (x + y) % 6 else cloth_d
    p.paint('body', body, tatter=0.5)

    def arm(face, x, y, w, h):
        if face == 'bottom':
            return None
        if y <= 10:
            return cloth_d if y % 3 == 0 else cloth  # wrappings
        return None
    p.paint('arm', arm)

    def leg(face, x, y, w, h):
        if face in ('top', 'bottom'):
            return None
        if y >= 8:
            return boot
        return cloth if y % 3 else cloth_d
    p.paint('leg', leg)
    p.save('revenant_thief')


def eyes():
    img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    for x in (9, 10, 13, 14):
        img.putpixel((x, 12), (255, 255, 255, 255))
    img.save(os.path.join(OUT, 'revenant_eyes.png'))


warrior()
mage()
archer()
priest()
thief()
eyes()
print('ok')
