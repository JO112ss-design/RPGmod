"""Robes (the casters' armour): item icons (16x16), the worn textures (64x32, humanoid and
humanoid_leggings layers) and the smithing GUI images (160x40)."""
from PIL import Image, ImageDraw
import os

ROOT = os.path.join(os.path.dirname(__file__), '..', '..', 'src', 'main', 'resources', 'assets', 'rpgmod', 'textures')
ITEM = os.path.join(ROOT, 'item'); GUI = os.path.join(ROOT, 'gui')
HUM = os.path.join(ROOT, 'entity', 'equipment', 'humanoid'); LEG = os.path.join(ROOT, 'entity', 'equipment', 'humanoid_leggings')
for d in (ITEM, GUI, HUM, LEG): os.makedirs(d, exist_ok=True)

# cloth and the metal woven into it (trim)
MATS = {
    'copper':    ((122, 58, 38), (231, 124, 86)),
    'iron':      ((58, 66, 112), (220, 220, 226)),
    'gold':      ((128, 28, 40), (250, 214, 70)),
    'diamond':   ((30, 92, 116), (110, 230, 222)),
    'netherite': ((44, 30, 52), (150, 128, 140)),
}
OUT = (24, 18, 22, 255)
SHOE = ((92, 64, 40), (64, 44, 28))

def sh(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c) + (255,)

def rgba(c): return tuple(c[:3]) + (255,)

def outline(im):
    px = im.load(); w, h = im.size
    pts = [(x, y) for y in range(h) for x in range(w) if px[x, y][3] == 0 and any(
        0 <= x + dx < w and 0 <= y + dy < h and px[x + dx, y + dy][3] > 0 and px[x + dx, y + dy] != OUT
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))]
    for x, y in pts: px[x, y] = OUT

def noise(im, box, base, seed):
    """Woven cloth: a faint checker of lighter and darker threads."""
    x0, y0, x1, y1 = box
    for y in range(y0, y1):
        for x in range(x0, x1):
            k = (x * 7 + y * 13 + seed) % 5
            im.putpixel((x, y), sh(base, 1.08 if k == 0 else 0.92 if k == 1 else 1.0))

# ---------------- worn textures (64x32) ----------------
def worn(m):
    cloth, trim = MATS[m]
    im = Image.new('RGBA', (64, 32), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    T = rgba(trim)
    # head: the hood (8x8x8 box at 0,0), the face left open
    noise(im, (0, 0, 32, 16), cloth, 1)
    for x in range(8, 16): im.putpixel((x, 0), sh(cloth, 1.15))                    # top highlight
    d.rectangle([9, 10, 14, 15], fill=(0, 0, 0, 0))                               # face opening
    d.line([(8, 9), (15, 9)], fill=T); d.line([(8, 9), (8, 15)], fill=T); d.line([(15, 9), (15, 15)], fill=T)
    for x0 in (0, 16, 24):                                                        # hem on the sides and back
        d.line([(x0, 15), (x0 + 7, 15)], fill=T)
    # body (16,16): the robe - collar, a trimmed opening down the front, a belt
    noise(im, (16, 16, 40, 32), cloth, 2)
    d.line([(20, 20), (27, 20)], fill=T)                                          # collar
    d.line([(23, 21), (23, 31)], fill=T); d.line([(24, 21), (24, 31)], fill=sh(cloth, 0.7))
    for x in range(16, 40): im.putpixel((x, 26), sh(trim, 0.85))                  # belt all round
    im.putpixel((23, 26), sh(trim, 1.15)); im.putpixel((24, 26), sh(trim, 1.15))  # buckle
    d.line([(32, 21), (39, 21)], fill=sh(cloth, 0.8))                             # back seam
    # arms (40,16): wide sleeves with metal cuffs
    noise(im, (40, 16, 56, 32), cloth, 3)
    for x in range(40, 56):
        im.putpixel((x, 30), T); im.putpixel((x, 31), sh(trim, 0.8))
    # legs (0,16) in this layer: the shoes (lower rows only)
    for x in range(0, 16):
        for y in range(28, 32): im.putpixel((x, y), rgba(SHOE[0]) if y < 31 else rgba(SHOE[1]))
        im.putpixel((x, 28), T)
    d.rectangle([8, 16, 11, 19], fill=rgba(SHOE[1]))                              # soles
    im.save(f'{HUM}/robe_{m}.png')

    # leggings layer: a waistband on the body, cloth trousers down the legs
    lg = Image.new('RGBA', (64, 32), (0, 0, 0, 0)); d = ImageDraw.Draw(lg)
    noise(lg, (16, 26, 40, 32), cloth, 4)
    for x in range(16, 40): lg.putpixel((x, 26), sh(trim, 0.85))
    noise(lg, (0, 16, 16, 32), cloth, 5)
    for x in range(0, 16): lg.putpixel((x, 31), T)
    for y in range(20, 31): lg.putpixel((7, y), sh(cloth, 0.75))                 # inner seam on the front
    lg.save(f'{LEG}/robe_{m}.png')

# ---------------- item icons (16x16) ----------------
def icon_hood(m):
    cloth, trim = MATS[m]; im = Image.new('RGBA', (16, 16)); d = ImageDraw.Draw(im)
    d.polygon([(8, 1), (13, 5), (14, 13), (2, 13), (3, 5)], fill=rgba(cloth))
    d.line([(8, 1), (3, 5), (2, 13)], fill=sh(cloth, 1.25))
    d.ellipse([5, 6, 11, 14], fill=(20, 14, 20, 255))                              # the dark inside
    d.line([(2, 13), (5, 13)], fill=rgba(trim)); d.line([(11, 13), (14, 13)], fill=rgba(trim))
    d.arc([5, 6, 11, 14], 180, 360, fill=rgba(trim))
    outline(im); return im

def icon_robe(m):
    cloth, trim = MATS[m]; im = Image.new('RGBA', (16, 16)); d = ImageDraw.Draw(im)
    d.polygon([(5, 1), (11, 1), (14, 5), (12, 6), (12, 15), (4, 15), (4, 6), (2, 5)], fill=rgba(cloth))
    d.line([(5, 1), (2, 5)], fill=sh(cloth, 1.25)); d.line([(4, 6), (4, 15)], fill=sh(cloth, 1.15))
    d.line([(8, 2), (8, 15)], fill=rgba(trim))
    d.line([(4, 9), (12, 9)], fill=sh(trim, 0.85))
    d.line([(6, 1), (10, 1)], fill=rgba(trim))
    d.line([(4, 15), (12, 15)], fill=rgba(trim))
    outline(im); return im

def icon_pants(m):
    cloth, trim = MATS[m]; im = Image.new('RGBA', (16, 16)); d = ImageDraw.Draw(im)
    d.rectangle([3, 1, 12, 4], fill=rgba(cloth))
    d.polygon([(3, 4), (7, 4), (7, 14), (2, 14)], fill=rgba(cloth)); d.polygon([(9, 4), (12, 4), (13, 14), (9, 14)], fill=rgba(cloth))
    d.rectangle([7, 4, 9, 6], fill=rgba(cloth))
    d.line([(3, 1), (12, 1)], fill=rgba(trim))
    d.line([(2, 14), (7, 14)], fill=rgba(trim)); d.line([(9, 14), (13, 14)], fill=rgba(trim))
    d.line([(3, 4), (2, 14)], fill=sh(cloth, 1.25))
    outline(im); return im

def icon_boots(m):
    cloth, trim = MATS[m]; im = Image.new('RGBA', (16, 16)); d = ImageDraw.Draw(im)
    for x0 in (1, 9):
        d.polygon([(x0 + 1, 5), (x0 + 5, 5), (x0 + 5, 12), (x0, 12), (x0, 9)], fill=rgba(SHOE[0]))
        d.line([(x0 + 1, 5), (x0 + 5, 5)], fill=rgba(trim)); d.line([(x0 + 1, 6), (x0 + 5, 6)], fill=rgba(cloth))
        d.line([(x0, 12), (x0 + 5, 12)], fill=rgba(SHOE[1]))
    outline(im); return im

# ---------------- smithing GUI (160x40, front views, hammered span x 10..150) ----------------
def gui_base():
    return Image.new('RGBA', (160, 40))

def g_hood(m):
    cloth, trim = MATS[m]; im = gui_base(); d = ImageDraw.Draw(im)
    d.polygon([(80, 2), (128, 14), (150, 38), (10, 38), (32, 14)], fill=rgba(cloth))
    d.line([(80, 2), (32, 14), (10, 38)], fill=sh(cloth, 1.25))
    d.ellipse([56, 12, 104, 50], fill=(20, 14, 20, 255))
    d.arc([56, 12, 104, 50], 180, 360, fill=rgba(trim), width=2)
    d.line([(10, 37), (150, 37)], fill=rgba(trim), width=2)
    return im

def g_robe(m):
    cloth, trim = MATS[m]; im = gui_base(); d = ImageDraw.Draw(im)
    d.polygon([(10, 22), (40, 4), (66, 4), (80, 10), (94, 4), (120, 4), (150, 22), (140, 30), (118, 18), (118, 38), (42, 38), (42, 18), (20, 30)], fill=rgba(cloth))
    d.line([(10, 22), (40, 4), (66, 4)], fill=sh(cloth, 1.25))
    d.line([(66, 4), (80, 10), (94, 4)], fill=rgba(trim), width=2)
    d.line([(80, 10), (80, 38)], fill=rgba(trim), width=2)
    d.line([(42, 24), (118, 24)], fill=sh(trim, 0.85), width=2)
    d.line([(10, 22), (20, 30)], fill=rgba(trim), width=2); d.line([(150, 22), (140, 30)], fill=rgba(trim), width=2)
    d.line([(42, 37), (118, 37)], fill=rgba(trim), width=2)
    return im

def g_pants(m):
    cloth, trim = MATS[m]; im = gui_base(); d = ImageDraw.Draw(im)
    d.rectangle([50, 2, 110, 12], fill=rgba(cloth)); d.line([(50, 2), (110, 2)], fill=rgba(trim), width=2)
    d.polygon([(50, 12), (78, 12), (42, 38), (10, 38)], fill=rgba(cloth)); d.polygon([(82, 12), (110, 12), (150, 38), (118, 38)], fill=rgba(cloth))
    d.line([(50, 12), (10, 38)], fill=sh(cloth, 1.25))
    d.line([(10, 37), (42, 37)], fill=rgba(trim), width=2); d.line([(118, 37), (150, 37)], fill=rgba(trim), width=2)
    return im

def g_shoes(m):
    cloth, trim = MATS[m]; im = gui_base(); d = ImageDraw.Draw(im)
    d.polygon([(10, 38), (10, 28), (30, 22), (46, 10), (76, 10), (76, 38)], fill=rgba(SHOE[0]))
    d.polygon([(150, 38), (150, 28), (130, 22), (114, 10), (84, 10), (84, 38)], fill=rgba(SHOE[0]))
    d.rectangle([46, 6, 76, 12], fill=rgba(cloth)); d.rectangle([84, 6, 114, 12], fill=rgba(cloth))
    d.line([(46, 12), (76, 12)], fill=rgba(trim), width=2); d.line([(84, 12), (114, 12)], fill=rgba(trim), width=2)
    d.rectangle([10, 35, 76, 38], fill=rgba(SHOE[1])); d.rectangle([84, 35, 150, 38], fill=rgba(SHOE[1]))
    return im

for m in MATS:
    worn(m)
    icon_hood(m).save(f'{ITEM}/{m}_robe_hood.png'); icon_robe(m).save(f'{ITEM}/{m}_robe.png')
    icon_pants(m).save(f'{ITEM}/{m}_robe_pants.png'); icon_boots(m).save(f'{ITEM}/{m}_robe_boots.png')
    if m != 'netherite':
        g_hood(m).save(f'{GUI}/robe_hood_{m}.png'); g_robe(m).save(f'{GUI}/robe_{m}.png')
        g_pants(m).save(f'{GUI}/robe_pants_{m}.png'); g_shoes(m).save(f'{GUI}/robe_boots_{m}.png')
print('robes ok')
