"""Generates the King Slime body / crown textures and the skill book icon."""
import random
from PIL import Image

A = 'src/main/resources/assets/rpgmod/textures'
random.seed(7)


def box_uv(img, u, v, w, h, d, face):
    """Paints a cube's UV net (vanilla box layout) with face(fx, fy, side) -> rgba."""
    # side rects: (x, y, width, height, name)
    rects = [
        (u + d, v, w, d, 'top'), (u + d + w, v, w, d, 'bottom'),
        (u, v + d, d, h, 'right'), (u + d, v + d, w, h, 'front'),
        (u + d + w, v + d, d, h, 'left'), (u + d + w + d, v + d, w, h, 'back'),
    ]
    for x0, y0, rw, rh, side in rects:
        for y in range(rh):
            for x in range(rw):
                img.putpixel((x0 + x, y0 + y), face(x, y, rw, rh, side))


def jitter(c, n=10):
    return tuple(max(0, min(255, ch + random.randint(-n, n))) for ch in c[:3]) + (c[3],)


def king():
    img = Image.new('RGBA', (64, 32), (0, 0, 0, 0))

    def outer(x, y, w, h, side):
        # translucent royal blue jelly, lighter rim, a glossy streak on top/front
        edge = x == 0 or y == 0 or x == w - 1 or y == h - 1
        base = (92, 150, 255, 150) if not edge else (150, 200, 255, 185)
        if side in ('front', 'top') and (x + y) in (2, 3) and not edge:
            base = (220, 240, 255, 200)
        return jitter(base, 8)
    box_uv(img, 0, 0, 8, 8, 8, outer)

    def inner(x, y, w, h, side):
        base = (40, 78, 190, 255) if (x + y) % 5 else (52, 96, 214, 255)
        return jitter(base, 6)
    box_uv(img, 0, 16, 6, 6, 6, inner)

    def eye(x, y, w, h, side):
        if side == 'front':
            return (20, 18, 40, 255) if (x, y) != (0, 0) else (235, 240, 255, 255)
        return (24, 30, 70, 255)
    box_uv(img, 32, 0, 2, 2, 2, eye)
    box_uv(img, 32, 4, 2, 2, 2, eye)
    box_uv(img, 32, 8, 1, 1, 1, lambda x, y, w, h, s: (20, 18, 40, 255))
    img.save(f'{A}/entity/king_slime.png')


def crown():
    img = Image.new('RGBA', (32, 32), (0, 0, 0, 0))

    def gold(x, y, w, h, side):
        if side in ('front', 'back', 'left', 'right'):
            if y == 0:
                return jitter((255, 236, 140, 255), 6)
            if y == h - 1:
                return jitter((170, 110, 20, 255), 6)
            return jitter((236, 182, 48, 255) if (x % 3) else (214, 156, 34, 255), 6)
        return jitter((226, 170, 40, 255), 6)
    box_uv(img, 0, 0, 6, 3, 6, gold)
    box_uv(img, 0, 10, 1, 2, 1, lambda x, y, w, h, s: jitter((255, 214, 90, 255) if y == 0 else (230, 176, 44, 255), 5))
    box_uv(img, 0, 14, 1, 3, 1, lambda x, y, w, h, s: jitter((255, 224, 110, 255) if y == 0 else (232, 178, 46, 255), 5))
    box_uv(img, 8, 10, 1, 1, 1, lambda x, y, w, h, s: (230, 40, 60, 255))
    box_uv(img, 8, 13, 1, 1, 1, lambda x, y, w, h, s: (60, 120, 255, 255))
    img.save(f'{A}/entity/king_slime_crown.png')


def book():
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = img.load()
    cover, dark, page, gold, gem = (92, 52, 160, 255), (52, 26, 96, 255), (238, 230, 205, 255), (255, 206, 72, 255), (120, 230, 255, 255)
    # book seen at a slight angle: pages on the right, cover with a gold rune
    for y in range(2, 14):
        for x in range(3, 13):
            px[x, y] = cover
        px[2, y] = dark
        px[13, y] = page
        px[14, y] = page if 3 <= y <= 12 else (0, 0, 0, 0)
    for x in range(3, 14):
        px[x, 1] = dark
        px[x, 14] = dark
    for x in range(3, 13):
        px[x, 13] = (70, 38, 128, 255)
    # gold frame and a star rune
    for x in range(4, 12):
        px[x, 3] = gold
        px[x, 12] = gold
    for y in range(3, 13):
        px[4, y] = gold
        px[11, y] = gold
    for x, y in [(7, 5), (8, 5), (6, 7), (7, 7), (8, 7), (9, 7), (7, 6), (8, 6), (7, 8), (8, 8), (6, 9), (9, 9), (7, 10), (8, 10)]:
        px[x, y] = gold
    px[7, 7] = gem
    px[8, 8] = gem
    img.save(f'{A}/item/skill_book.png')


import os
os.makedirs(f'{A}/entity', exist_ok=True)
king()
crown()
book()
print('ok')
