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


# ---------------------------------------------------------------- advanced job effects

def shield(d, fill, edge):
    d.polygon([(3, 2), (15, 2), (15, 9), (9, 16), (3, 9)], fill=fill, outline=edge)


def exposed(d):
    r = (255, 80, 80, 255)
    d.ellipse([3, 3, 15, 15], outline=r, width=1)
    d.ellipse([7, 7, 11, 11], fill=r)
    for a, b in (((9, 0), (9, 4)), ((9, 14), (9, 18)), ((0, 9), (4, 9)), ((14, 9), (18, 9))):
        d.line([a, b], fill=r, width=1)


def keen(d):
    sword(d, (255, 230, 200, 255), (140, 90, 40, 255))
    d.polygon([(13, 1), (14, 4), (17, 5), (14, 6), (13, 9), (12, 6), (9, 5), (12, 4)], fill=(255, 176, 64, 255))


def asura(d):
    d.polygon([(9, 1), (12, 7), (17, 8), (12, 11), (14, 17), (9, 13), (4, 17), (6, 11), (1, 8), (6, 7)], fill=(200, 24, 24, 255), outline=(90, 0, 0, 255))
    d.ellipse([7, 7, 11, 11], fill=(255, 210, 120, 255))


def iron_wall(d):
    shield(d, (160, 168, 184, 255), (70, 76, 90, 255))
    for y in (5, 9):
        d.line([(4, y), (14, y)], fill=(90, 96, 110, 255))
    d.line([(9, 2), (9, 15)], fill=(90, 96, 110, 255))


def guard_up(d):
    shield(d, (224, 192, 96, 255), (120, 90, 30, 255))
    d.polygon([(9, 4), (13, 9), (10, 9), (10, 13), (8, 13), (8, 9), (5, 9)], fill=(255, 250, 220, 255))


def cover(d):
    shield(d, (240, 208, 128, 255), (130, 100, 40, 255))
    d.ellipse([7, 5, 11, 9], fill=(255, 255, 255, 255))
    d.rectangle([6, 10, 12, 13], fill=(255, 255, 255, 255))


def reprisal(d):
    shield(d, (200, 120, 60, 255), (100, 50, 20, 255))
    d.line([(5, 12), (13, 4)], fill=(255, 220, 160, 255), width=2)
    d.polygon([(13, 3), (14, 8), (9, 4)], fill=(255, 220, 160, 255))


def undying(d):
    g = (255, 224, 112, 255)
    d.ellipse([4, 2, 14, 10], fill=g, outline=(150, 110, 20, 255))
    d.rectangle([7, 9, 11, 16], fill=g)
    d.line([(4, 12), (14, 12)], fill=g, width=2)
    d.point([(7, 5), (11, 5)], fill=(120, 80, 10, 255))


def empowered_shot(d):
    d.line([(3, 15), (14, 4)], fill=(230, 230, 230, 255), width=1)
    d.polygon([(15, 3), (10, 4), (14, 8)], fill=(140, 255, 106, 255))
    d.line([(2, 13), (5, 16)], fill=(140, 255, 106, 255), width=1)
    d.polygon([(4, 3), (6, 7), (2, 7)], fill=(255, 240, 150, 255))


def double_cast(d):
    d.ellipse([1, 4, 11, 14], outline=(154, 123, 255, 255), width=2)
    d.ellipse([7, 4, 17, 14], outline=(220, 200, 255, 255), width=2)


def overload(d):
    d.ellipse([3, 3, 15, 15], fill=(255, 74, 216, 255), outline=(120, 20, 100, 255))
    d.polygon([(10, 2), (6, 10), (9, 10), (8, 16), (13, 7), (10, 7)], fill=(255, 250, 200, 255))


def feather(d):
    d.polygon([(14, 2), (16, 5), (8, 15), (4, 14), (6, 9)], fill=(232, 244, 255, 255), outline=(140, 160, 190, 255))
    d.line([(15, 3), (3, 16)], fill=(150, 170, 200, 255))


def vanish(d):
    for i, a in enumerate((90, 150, 220)):
        d.ellipse([3 + i * 2, 3 + i * 2, 15 - i * 2, 15 - i * 2], outline=(106, 74, 154, a), width=1)
    d.point([(8, 8), (10, 8)], fill=(255, 255, 255, 255))


def loaded_dice(d):
    d.rectangle([3, 3, 15, 15], fill=(255, 245, 225, 255), outline=(170, 120, 20, 255))
    for x, y in ((6, 6), (12, 6), (9, 9), (6, 12), (12, 12)):
        d.ellipse([x - 1, y - 1, x + 1, y + 1], fill=(200, 40, 40, 255))


def twin_venom(d):
    d.line([(3, 14), (11, 3)], fill=(210, 230, 210, 255), width=2)
    d.line([(15, 14), (7, 3)], fill=(210, 230, 210, 255), width=2)
    d.ellipse([7, 11, 11, 16], fill=(96, 224, 64, 255))


def yaksha(d):
    d.polygon([(4, 3), (7, 9), (2, 8)], fill=(200, 210, 255, 255))
    d.polygon([(14, 3), (16, 8), (11, 9)], fill=(200, 210, 255, 255))
    d.ellipse([4, 6, 14, 16], fill=(80, 96, 224, 255), outline=(30, 30, 90, 255))
    d.point([(7, 10), (11, 10)], fill=(255, 80, 80, 255))


def sanctum(d):
    g = (255, 240, 176, 255)
    d.ellipse([2, 2, 16, 16], outline=g, width=1)
    d.rectangle([8, 4, 10, 14], fill=g)
    d.rectangle([5, 7, 13, 9], fill=g)


for n, f in [('exposed', exposed), ('keen', keen), ('asura', asura), ('iron_wall', iron_wall), ('guard_up', guard_up), ('cover', cover),
             ('reprisal', reprisal), ('undying', undying), ('empowered_shot', empowered_shot), ('double_cast', double_cast),
             ('overload', overload), ('feather', feather), ('vanish', vanish), ('loaded_dice', loaded_dice), ('twin_venom', twin_venom),
             ('yaksha', yaksha), ('sanctum', sanctum)]:
    icon(n, f)


def venom(d):
    g, dk = (90, 200, 50, 255), (30, 90, 20, 255)
    d.polygon([(9, 1), (14, 9), (14, 12), (12, 15), (9, 16), (6, 15), (4, 12), (4, 9)], fill=g, outline=dk)
    d.ellipse([6, 9, 9, 12], fill=(200, 255, 170, 255))
    d.point([(11, 13), (12, 12)], fill=(200, 255, 170, 255))


icon('venom', venom)
