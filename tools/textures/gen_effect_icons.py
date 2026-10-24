"""Status effect icons (18x18) for the job skill effects."""
import os
from PIL import Image, ImageDraw

OUT = os.path.join(os.path.dirname(__file__), '..', '..', 'src', 'main', 'resources', 'assets', 'rpgmod', 'textures', 'mob_effect')
os.makedirs(OUT, exist_ok=True)


def icon(name, draw_fn):
    img = Image.new('RGBA', (18, 18), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    draw_fn(d)
    img.save(os.path.join(OUT, name + '.png'))


def sword(d, col, dark):
    d.line([(4, 13), (13, 4)], fill=col, width=2)
    d.line([(3, 11), (6, 14)], fill=dark, width=2)
    d.point([(2, 15), (3, 15), (2, 14)], fill=dark)


def attack_down(d):
    sword(d, (220, 220, 230, 255), (120, 80, 50, 255))
    d.polygon([(11, 11), (17, 11), (14, 16)], fill=(230, 60, 40, 255))
    d.rectangle([13, 7, 15, 11], fill=(230, 60, 40, 255))


def armor_break(d):
    d.polygon([(3, 3), (14, 3), (14, 9), (8.5, 15), (3, 9)], fill=(150, 165, 200, 255), outline=(70, 80, 110, 255))
    d.line([(9, 3), (7, 7), (10, 9), (8, 14)], fill=(30, 30, 40, 255), width=1)


def quick_cast(d):
    d.ellipse([2, 2, 15, 15], outline=(176, 156, 255, 255), width=2)
    d.polygon([(9, 3), (5, 10), (8, 10), (7, 15), (12, 7), (9, 7)], fill=(255, 240, 150, 255))


def poison_coat(d):
    sword(d, (200, 230, 200, 255), (60, 90, 50, 255))
    d.ellipse([10, 10, 15, 15], fill=(125, 255, 74, 255))
    d.polygon([(12.5, 7), (10.3, 11), (14.7, 11)], fill=(125, 255, 74, 255))


def fire_weapon(d):
    sword(d, (230, 230, 230, 255), (120, 80, 50, 255))
    d.polygon([(11, 16), (8, 11), (10, 12), (11, 7), (13, 11), (15, 9), (15, 13), (13, 16)], fill=(255, 122, 42, 255))
    d.polygon([(11, 15), (10, 12), (12, 13), (13, 15)], fill=(255, 220, 110, 255))


def fortune(d):
    # a gold four-leaf clover
    g, dk = (255, 213, 74, 255), (170, 120, 20, 255)
    for cx, cy in ((6, 6), (12, 6), (6, 12), (12, 12)):
        d.ellipse([cx - 3, cy - 3, cx + 3, cy + 3], fill=g, outline=dk)
    d.line([(9, 9), (14, 17)], fill=(90, 160, 60, 255), width=2)
    d.ellipse([7, 7, 11, 11], fill=(255, 245, 190, 255))


for n, f in [('fortune', fortune), ('attack_down', attack_down), ('armor_break', armor_break), ('quick_cast', quick_cast), ('poison_coat', poison_coat), ('fire_weapon', fire_weapon)]:
    icon(n, f)
print('icons written')
