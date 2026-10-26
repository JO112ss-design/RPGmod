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


# ---- pirate, lancer, monk, bard
def note(d, col):
    d.ellipse([3, 11, 8, 15], fill=col)
    d.rectangle([7, 3, 8, 13], fill=col)
    d.polygon([(8, 3), (14, 6), (13, 8), (8, 6)], fill=col)


def jolly_roger(d):
    d.rectangle([3, 2, 4, 16], fill=(120, 85, 50, 255))
    d.rectangle([5, 3, 15, 10], fill=(25, 25, 30, 255))
    d.ellipse([8, 4, 12, 8], fill=(236, 232, 220, 255))
    d.point([(9, 6), (11, 6)], fill=(25, 25, 30, 255))
    d.line([(7, 9), (13, 9)], fill=(236, 232, 220, 255))


def counter_stance(d):
    d.line([(3, 15), (15, 3)], fill=(200, 160, 110, 255), width=2)
    d.polygon([(15, 3), (11, 4), (14, 7)], fill=(230, 230, 240, 255))
    d.arc([2, 2, 16, 16], 200, 340, fill=(255, 220, 140, 255), width=2)


def parry(d):
    d.ellipse([3, 6, 11, 14], fill=(240, 190, 120, 255), outline=(140, 90, 40, 255))
    d.arc([5, 2, 17, 14], 270, 30, fill=(255, 240, 200, 255), width=2)


def untouchable(d):
    for i, a in enumerate((90, 160, 255)):
        d.line([(2, 5 + i * 4), (10, 5 + i * 4)], fill=(255, 224, 176, a), width=1)
    d.ellipse([10, 5, 15, 13], fill=(255, 224, 176, 255))


def diamond_body(d):
    d.polygon([(9, 2), (15, 7), (9, 16), (3, 7)], fill=(255, 210, 122, 255), outline=(150, 100, 30, 255))
    d.line([(3, 7), (15, 7)], fill=(255, 245, 200, 255))


def meditate(d):
    d.ellipse([7, 2, 11, 6], fill=(255, 230, 160, 255))
    d.polygon([(4, 15), (9, 7), (14, 15)], fill=(240, 200, 120, 255))
    d.line([(3, 15), (15, 15)], fill=(200, 150, 80, 255), width=2)


def war_song(d):
    note(d, (255, 112, 80, 255))
    d.line([(12, 11), (16, 15)], fill=(230, 230, 240, 255), width=1)


def guard_song(d):
    note(d, (112, 160, 255, 255))
    d.polygon([(11, 10), (16, 10), (16, 13), (13.5, 16), (11, 13)], fill=(200, 215, 255, 255))


def heal_song(d):
    note(d, (112, 255, 144, 255))
    d.rectangle([13, 10, 14, 16], fill=(230, 255, 230, 255))
    d.rectangle([11, 12, 16, 13], fill=(230, 255, 230, 255))


def wind_song(d):
    note(d, (176, 255, 224, 255))
    d.arc([10, 9, 17, 15], 180, 360, fill=(230, 255, 245, 255))


def performing(d):
    d.ellipse([3, 7, 12, 16], fill=(160, 100, 50, 255), outline=(90, 55, 25, 255))
    d.line([(10, 9), (16, 2)], fill=(120, 80, 40, 255), width=2)
    d.ellipse([6, 10, 9, 13], fill=(40, 25, 15, 255))


for n, f in [('jolly_roger', jolly_roger), ('counter_stance', counter_stance), ('parry', parry), ('untouchable', untouchable),
             ('diamond_body', diamond_body), ('meditate', meditate), ('war_song', war_song), ('guard_song', guard_song),
             ('heal_song', heal_song), ('wind_song', wind_song), ('performing', performing)]:
    icon(n, f)


# ---- captain, champion, dragoon, maestro and the hybrid jobs: a coloured badge with a simple mark
def badge(col, mark):
    dk = tuple(max(0, int(v * 0.45)) for v in col[:3]) + (255,)
    lt = tuple(min(255, int(v * 0.6 + 110)) for v in col[:3]) + (255,)

    def draw(d):
        d.ellipse([2, 2, 15, 15], fill=dk, outline=col)
        mark(d, lt)
    return draw


def m_eye(d, c):
    d.ellipse([4, 6, 13, 11], outline=c)
    d.ellipse([7, 7, 10, 10], fill=c)


def m_flame(d, c):
    d.polygon([(8.5, 3), (12, 9), (11, 13), (8.5, 14), (6, 13), (5, 9)], fill=c)


def m_star(d, c):
    d.polygon([(8.5, 3), (10, 7), (14, 7.5), (11, 10), (12, 14), (8.5, 11.5), (5, 14), (6, 10), (3, 7.5), (7, 7)], fill=c)


def m_notes(d, c):
    for x in (5, 10):
        d.ellipse([x - 1, 10, x + 2, 13], fill=c)
        d.line([(x + 2, 4), (x + 2, 12)], fill=c)
    d.line([(7, 4), (12, 4)], fill=c, width=2)


def m_shield(d, c):
    d.polygon([(5, 4), (12, 4), (12, 9), (8.5, 14), (5, 9)], fill=c)


def m_spear(d, c):
    d.line([(4, 13), (12, 5)], fill=c, width=2)
    d.polygon([(13, 3), (10, 4), (12, 7)], fill=c)


def m_tower(d, c):
    d.rectangle([5, 6, 12, 14], fill=c)
    for x in (5, 8, 11):
        d.rectangle([x, 4, x + 1, 6], fill=c)


def m_dagger(d, c):
    d.line([(5, 12), (12, 5)], fill=c, width=2)
    d.line([(4, 9), (8, 13)], fill=c)


def m_moon(d, c):
    d.ellipse([4, 4, 13, 13], fill=c)
    d.ellipse([7, 3, 15, 11], fill=(30, 36, 60, 255))


def m_target(d, c):
    d.ellipse([4, 4, 13, 13], outline=c)
    d.ellipse([7, 7, 10, 10], fill=c)


def m_coin(d, c):
    d.ellipse([4, 4, 13, 13], fill=c)
    d.rectangle([8, 6, 9, 11], fill=(150, 110, 20, 255))


def m_coins(d, c):
    for y in (11, 8, 5):
        d.ellipse([4, y, 13, y + 4], fill=c, outline=(150, 110, 20, 255))


def m_two(d, c):
    d.ellipse([3, 6, 9, 12], outline=c)
    d.ellipse([8, 6, 14, 12], outline=c)


for n, col, mk in [('foresight', (255, 176, 112, 255), m_eye), ('battle_aura', (255, 128, 48, 255), m_flame), ('heroic', (255, 208, 80, 255), m_star),
                   ('duet', (95, 211, 188, 255), m_notes), ('flame_song', (255, 122, 48, 255), m_flame), ('spell_guard', (140, 124, 255, 255), m_shield),
                   ('immovable', (192, 176, 128, 255), m_tower), ('counter_lance', (224, 192, 128, 255), m_spear), ('fortress', (240, 216, 144, 255), m_tower),
                   ('ambush', (74, 90, 128, 255), m_dagger), ('night_hunt', (90, 100, 160, 255), m_moon), ('hunted', (192, 48, 64, 255), m_target),
                   ('treasure', (212, 175, 55, 255), m_coin), ('double_down', (232, 192, 64, 255), m_two),
                   ('gold_rush', (255, 200, 48, 255), m_coins), ('mana_meditate', (111, 168, 255, 255), m_notes)]:
    icon(n, badge(col, mk))
