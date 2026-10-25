"""Golem boss textures: the body (iron golem UV layout, 128x128), its glow layer (eyes, cracks, core),
the stone-armour swirl, and the armour pillar (64x64) with its glowing crystal."""
import random
from PIL import Image

A = 'src/main/resources/assets/rpgmod/textures/entity'
rnd = random.Random(11)

STONE = [(54, 50, 58), (62, 58, 66), (47, 44, 52), (70, 65, 74), (58, 54, 62)]
DARK = (32, 29, 36)
MOSS = [(52, 64, 46), (46, 58, 40)]


def faces(u, v, w, h, d):
    """Cube UV unwrap of a box at texOffs (u, v): name -> (x0, y0, width, height)."""
    return {
        'top': (u + d, v, w, d),
        'bottom': (u + d + w, v, w, d),
        'right': (u, v + d, d, h),
        'front': (u + d, v + d, w, h),
        'left': (u + d + w, v + d, d, h),
        'back': (u + 2 * d + w, v + d, w, h),
    }


BOXES = {
    'head': (0, 0, 8, 10, 8),
    'nose': (24, 0, 2, 4, 2),
    'body': (0, 40, 18, 12, 11),
    'waist': (0, 70, 9, 5, 6),
    'rarm': (60, 21, 4, 30, 6),
    'larm': (60, 58, 4, 30, 6),
    'rleg': (37, 0, 6, 16, 5),
    'lleg': (60, 0, 6, 16, 5),
}

body = Image.new('RGBA', (128, 128), (0, 0, 0, 0))
glow = Image.new('RGBA', (128, 128), (0, 0, 0, 0))


def stone_face(x0, y0, w, h, edge=True):
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            c = rnd.choice(STONE)
            if rnd.random() < 0.04:
                c = rnd.choice(MOSS)
            body.putpixel((x, y), c + (255,))
    # block seams: a darker grid every 4 pixels, offset per row like masonry
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            ly, lx = y - y0, x - x0
            row = ly // 4
            if ly % 4 == 3 or (lx + (2 if row % 2 else 0)) % 6 == 5:
                if rnd.random() < 0.85:
                    body.putpixel((x, y), DARK + (255,))
    if edge:
        for x in range(x0, x0 + w):
            body.putpixel((x, y0), (40, 37, 44, 255))


def crack(x0, y0, w, h, n, color=(255, 70, 30)):
    """Glowing cracks wandering down a face (drawn on both layers)."""
    for _ in range(n):
        x = x0 + rnd.randrange(w)
        y = y0 + rnd.randrange(max(1, h // 3))
        for _ in range(rnd.randint(h // 3, h)):
            if not (x0 <= x < x0 + w and y0 <= y < y0 + h):
                break
            c = color if rnd.random() < 0.7 else (255, 150, 60)
            body.putpixel((x, y), (120, 30, 20, 255))
            glow.putpixel((x, y), c + (255,))
            y += 1
            x += rnd.choice((-1, 0, 0, 1))


for name, (u, v, w, h, d) in BOXES.items():
    for fname, (x0, y0, fw, fh) in faces(u, v, w, h, d).items():
        stone_face(x0, y0, fw, fh)
        if fname in ('front', 'back', 'left', 'right') and name in ('body', 'rarm', 'larm', 'rleg', 'lleg'):
            crack(x0, y0, fw, fh, 1 if fw <= 6 else 2)

# the face: a heavy brow, two burning eyes, a mouth slit
fx0, fy0, fw, fh = faces(*BOXES['head'])['front']
for x in range(fx0, fx0 + fw):
    body.putpixel((x, fy0 + 3), DARK + (255,))
for ex in (fx0 + 1, fx0 + 5):
    for dx in range(2):
        for dy in range(2):
            body.putpixel((ex + dx, fy0 + 4 + dy), (255, 90, 40, 255))
            glow.putpixel((ex + dx, fy0 + 4 + dy), (255, 120 if dy == 0 else 60, 40, 255))
for x in range(fx0 + 2, fx0 + 6):
    body.putpixel((x, fy0 + 8), (20, 18, 24, 255))
    glow.putpixel((x, fy0 + 8), (160, 40, 20, 255))

# the core in the chest: a glowing ringed stone
bx0, by0, bw, bh = faces(*BOXES['body'])['front']
cx, cy = bx0 + bw // 2, by0 + bh // 2
for dy in range(-3, 3):
    for dx in range(-3, 3):
        r = max(abs(dx + 0.5), abs(dy + 0.5))
        if r <= 1.5:
            col = (255, 220, 120)
        elif r <= 2.5:
            col = (255, 90, 30)
        else:
            continue
        body.putpixel((cx + dx, cy + dy), col + (255,))
        glow.putpixel((cx + dx, cy + dy), col + (255,))
for dy in range(-4, 4):
    for dx in range(-4, 4):
        if max(abs(dx + 0.5), abs(dy + 0.5)) == 3.5:
            body.putpixel((cx + dx, cy + dy), (90, 80, 70, 255))

body.save(f'{A}/golem.png')
glow.save(f'{A}/golem_glow.png')

# stone armour: a slow swirl of glowing runes (energy swirl texture, tiles)
arm = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
for y in range(64):
    for x in range(64):
        if (x + y) % 16 in (0, 1) or (x - y) % 16 == 0:
            arm.putpixel((x, y), (255, 150, 60, 200))
        elif rnd.random() < 0.03:
            arm.putpixel((x, y), (255, 210, 120, 160))
arm.save(f'{A}/golem_armor.png')

# ---------------------------------------------------------------- pillar (64x64)
pil = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
pglow = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
body, glow = pil, pglow  # reuse the painters
PBOX = {'base': (0, 0, 12, 6, 12), 'column': (0, 18, 8, 26, 8), 'crystal': (32, 18, 6, 10, 6)}
for name, (u, v, w, h, d) in PBOX.items():
    for fname, (x0, y0, fw, fh) in faces(u, v, w, h, d).items():
        if name == 'crystal':
            for y in range(y0, y0 + fh):
                for x in range(x0, x0 + fw):
                    c = rnd.choice([(255, 110, 40), (255, 160, 70), (230, 70, 30)])
                    pil.putpixel((x, y), c + (255,))
                    pglow.putpixel((x, y), c + (255,))
        else:
            stone_face(x0, y0, fw, fh)
            if name == 'column' and fname != 'top':
                crack(x0, y0, fw, fh, 1)
pil.save(f'{A}/golem_pillar.png')
pglow.save(f'{A}/golem_pillar_glow.png')
print('ok')
