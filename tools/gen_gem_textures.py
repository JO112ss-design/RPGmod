"""Gem icons (common green, rare blue, legendary gold) and the socket table's block faces (16x16)."""
import random
from PIL import Image

A = 'src/main/resources/assets/rpgmod/textures'

# a cut gem: crown facets on top, pavilion below; letters pick the shade
GEM = [
    '................',
    '................',
    '....OOOOOOOO....',
    '...OhHHLLHHmO...',
    '..OhHHLLLLHHmO..',
    '.OOOOOOOOOOOOOO.',
    '.OhhLLMMMMLLmmO.',
    '..OhLLMMMMLmmO..',
    '...OhLMMMMLmO...',
    '....OhLMMLmO....',
    '.....OhMMmO.....',
    '......OMmO......',
    '.......OO.......',
    '................',
    '................',
    '................',
]


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c)


def gem(name, base, sparkle=None):
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    pal = {
        'O': shade(base, 0.35),
        'h': (255, 255, 255),
        'H': shade(base, 1.35),
        'L': shade(base, 1.15),
        'M': base,
        'm': shade(base, 0.7),
    }
    for y, row in enumerate(GEM):
        for x, ch in enumerate(row):
            if ch in pal:
                img.putpixel((x, y), pal[ch] + (255,))
    if sparkle:
        for (x, y) in [(13, 1), (12, 2), (14, 2), (13, 3), (2, 10), (1, 11), (3, 11), (2, 12)]:
            img.putpixel((x, y), sparkle + (255,))
    img.save(f'{A}/item/{name}.png')


gem('gem_common', (70, 200, 95))
gem('gem_rare', (60, 130, 255), sparkle=(200, 230, 255))
gem('gem_legendary', (255, 170, 30), sparkle=(255, 250, 200))

# ---------------------------------------------------------------- socket table
rnd = random.Random(7)
STONE = [(52, 48, 62), (60, 55, 72), (46, 42, 56), (66, 61, 80)]
AME = [(150, 90, 220), (185, 130, 245), (120, 70, 190), (220, 180, 255)]
WOOD = [(96, 62, 38), (110, 72, 44), (84, 54, 32)]


def noise(img, colors, box):
    x0, y0, x1, y1 = box
    for y in range(y0, y1):
        for x in range(x0, x1):
            img.putpixel((x, y), rnd.choice(colors) + (255,))


def side(front):
    img = Image.new('RGBA', (16, 16))
    noise(img, STONE, (0, 0, 16, 16))
    # a wooden top rim and a darker foot
    noise(img, WOOD, (0, 0, 16, 3))
    for x in range(16):
        img.putpixel((x, 3), (30, 26, 38, 255))
        img.putpixel((x, 15), (30, 26, 38, 255))
    if front:
        # an amethyst crystal set into the front
        pts = {(7, 6): 3, (8, 6): 3, (6, 7): 1, (7, 7): 0, (8, 7): 1, (9, 7): 2, (6, 8): 1, (7, 8): 0, (8, 8): 0, (9, 8): 2,
               (6, 9): 2, (7, 9): 0, (8, 9): 0, (9, 9): 2, (7, 10): 2, (8, 10): 2, (7, 11): 2}
        for (x, y), k in pts.items():
            img.putpixel((x, y), AME[k] + (255,))
        for (x, y) in [(5, 7), (10, 7), (5, 9), (10, 9), (6, 11), (9, 11), (7, 12), (8, 12), (6, 5), (9, 5)]:
            img.putpixel((x, y), (200, 170, 90, 255))  # gold setting
    else:
        for x in range(3, 13):
            img.putpixel((x, 9), AME[2] + (255,))
    return img


def top():
    img = Image.new('RGBA', (16, 16))
    noise(img, WOOD, (0, 0, 16, 16))
    # purple cloth with three gold-ringed sockets
    noise(img, [(90, 45, 130), (100, 52, 145), (82, 40, 120)], (2, 2, 14, 14))
    for x in range(2, 14):
        img.putpixel((x, 2), (200, 170, 90, 255))
        img.putpixel((x, 13), (200, 170, 90, 255))
    for y in range(2, 14):
        img.putpixel((2, y), (200, 170, 90, 255))
        img.putpixel((13, y), (200, 170, 90, 255))
    for cx, col in [(5, (70, 200, 95)), (8, (60, 130, 255)), (11, (255, 170, 30))]:
        for (dx, dy) in [(0, -1), (-1, 0), (1, 0), (0, 1)]:
            img.putpixel((cx + dx, 8 + dy), (200, 170, 90, 255))
        img.putpixel((cx, 8), col + (255,))
    return img


side(True).save(f'{A}/block/socket_table_front.png')
side(False).save(f'{A}/block/socket_table_side.png')
top().save(f'{A}/block/socket_table_top.png')
print('ok')
