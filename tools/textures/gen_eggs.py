"""Spawn egg textures (and their item model files) for the mod's monsters: the four bosses, a
fallen adventurer of each job and a party. Each egg is the vanilla shape: a shaded base colour with
spots; boss eggs wear a small crown of their spot colour, the party egg has spots of every job."""
import json
import os
import random
from PIL import Image

HERE = os.path.dirname(__file__)
ASSETS = os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'rpgmod')

ROLES = {'warrior': 0xC0C8D8, 'mage': 0x9A6AFF, 'archer': 0x6FBF5A, 'priest': 0xFFE6A0, 'thief': 0x6A6A80}
ROT = 0x4E6B4A  # the fallen adventurers' rotting green

EGGS = {
    'king_slime_spawn_egg': (0x5FD8FF, [0x2A7FC0], 0xE0B040),
    'golem_spawn_egg': (0x3A3A40, [0xFF4020], 0xFF4020),
    'lich_spawn_egg': (0x3A1E5A, [0x7FE8FF], 0xE0B040),
    'spider_queen_spawn_egg': (0x17121F, [0xC060FF], 0xC060FF),
}
for r, c in ROLES.items():
    EGGS['revenant_%s_spawn_egg' % r] = (ROT, [c], None)
EGGS['revenant_party_spawn_egg'] = (ROT, list(ROLES.values()), None)


def rgb(c):
    return ((c >> 16) & 255, (c >> 8) & 255, c & 255)


def mul(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c) + (255,)


def inside(x, y):
    """The egg: 10 wide, 13 tall, narrower at the top (pixel centres)."""
    cx, cy = 7.5, 9.0
    dx, dy = x - cx, y - cy
    ry = 6.4 if dy < 0 else 5.2
    rx = 5.0 - (0.9 * (-dy / 6.4) if dy < 0 else 0)
    return (dx / rx) ** 2 + (dy / ry) ** 2 <= 1.0


def egg(name, base, spots, crown):
    rnd = random.Random(name)
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    b = rgb(base)
    cells = [(x, y) for y in range(16) for x in range(16) if inside(x, y)]
    cs = set(cells)
    # spots: a few small blobs
    spot_px = {}
    n = 7 if len(spots) == 1 else 10
    for i in range(n):
        sx, sy = rnd.choice(cells)
        col = rgb(spots[i % len(spots)])
        for (ox, oy) in [(0, 0), (1, 0), (0, 1), (1, 1)][: rnd.choice([1, 2, 4])]:
            if (sx + ox, sy + oy) in cs:
                spot_px[(sx + ox, sy + oy)] = col
    for (x, y) in cells:
        edge = any((x + dx, y + dy) not in cs for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        c = spot_px.get((x, y), b)
        # light from the upper left
        f = 1.15 - 0.05 * (x - 5) - 0.025 * (y - 6)
        if edge:
            f *= 0.6
        img.putpixel((x, y), mul(c, max(0.45, min(1.35, f))))
    # a highlight
    for (x, y) in [(5, 5), (5, 6), (6, 4)]:
        if (x, y) in cs:
            img.putpixel((x, y), mul(b, 1.6) if sum(b) < 600 else (255, 255, 255, 255))
    if crown is not None:
        gold = rgb(crown)
        for x, y in [(5, 1), (7, 0), (8, 0), (10, 1), (5, 2), (6, 2), (7, 2), (8, 2), (9, 2), (10, 2), (7, 1), (8, 1)]:
            img.putpixel((x, y), mul(gold, 1.1 if y < 2 else 0.85))
    img.save(os.path.join(ASSETS, 'textures', 'item', name + '.png'))
    with open(os.path.join(ASSETS, 'items', name + '.json'), 'w') as f:
        json.dump({'model': {'type': 'minecraft:model', 'model': 'rpgmod:item/' + name}}, f, indent=2)
        f.write('\n')
    with open(os.path.join(ASSETS, 'models', 'item', name + '.json'), 'w') as f:
        json.dump({'parent': 'minecraft:item/generated', 'textures': {'layer0': 'rpgmod:item/' + name}}, f, indent=2)
        f.write('\n')


for name, (base, spots, crown) in EGGS.items():
    egg(name, base, spots, crown)
print('ok', len(EGGS))
