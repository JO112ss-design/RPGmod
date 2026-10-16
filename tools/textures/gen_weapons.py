"""Generates item icons (16x16) and smithing GUI images (160x40) for the RPG weapons."""
from PIL import Image, ImageDraw
import math, os

ROOT = os.path.join(os.path.dirname(__file__), '..', '..', 'src', 'main', 'resources', 'assets', 'rpgmod', 'textures')
ITEM = os.path.join(ROOT, 'item'); GUI = os.path.join(ROOT, 'gui')
os.makedirs(ITEM, exist_ok=True); os.makedirs(GUI, exist_ok=True)

MATS = {
    'copper':    ((255, 180, 140), (231, 124, 86), (160, 76, 46)),
    'iron':      ((255, 255, 255), (216, 216, 216), (130, 130, 136)),
    'gold':      ((255, 250, 180), (250, 214, 70), (190, 140, 25)),
    'diamond':   ((210, 255, 250), (92, 219, 213), (30, 140, 132)),
    'netherite': ((122, 110, 116), (76, 66, 70), (40, 34, 38)),
}
WOOD = ((176, 132, 80), (137, 103, 59), (92, 66, 34))
OUT = (34, 24, 18, 255)
GEM = ((235, 190, 255), (178, 102, 230), (110, 50, 160))
STRING = (240, 240, 240, 255)

def rgba(c): return tuple(c) + (255,)

def put(im, x, y, c):
    if 0 <= x < im.width and 0 <= y < im.height: im.putpixel((x, y), rgba(c) if len(c) == 3 else c)

def outline(im):
    """Dark 1px outline around opaque pixels (like vanilla icons)."""
    px = im.load(); w, h = im.size
    pts = [(x, y) for y in range(h) for x in range(w) if px[x, y][3] == 0 and any(
        0 <= x + dx < w and 0 <= y + dy < h and px[x + dx, y + dy][3] > 0 and px[x + dx, y + dy] != OUT
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))]
    for x, y in pts: px[x, y] = OUT

def handle(im, x0, x1):
    for x in range(x0, x1 + 1):
        y = 15 - x
        put(im, x, y, WOOD[1]); put(im, x, y - 1, WOOD[0]) if (x % 2 == 0) else None

# ---------------- 16x16 icons ----------------
def dagger(m):
    L, M, D = MATS[m]; im = Image.new('RGBA', (16, 16))
    handle(im, 2, 5); put(im, 1, 14, D)
    for x in range(6, 9): put(im, x - 1, 15 - x - 1 + 2 - 1, D)
    put(im, 5, 8, D); put(im, 6, 9, D); put(im, 7, 10, D); put(im, 8, 11, D)
    for x in range(7, 13):
        put(im, x, 15 - x, M); put(im, x, 14 - x, L); put(im, x + 1, 15 - x, D)
    put(im, 13, 2, L); put(im, 14, 1, L)
    outline(im); return im

def mace(m):
    L, M, D = MATS[m]; im = Image.new('RGBA', (16, 16))
    handle(im, 1, 8); put(im, 0, 15, D)
    cx, cy = 11, 4
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - cx, y - cy)
            if d <= 2.7: put(im, x, y, L if (x - cx) + (y - cy) < -1.5 else D if (x - cx) + (y - cy) > 1.5 else M)
    for sx, sy in ((cx, cy - 4), (cx + 4, cy), (cx - 3, cy - 3), (cx + 3, cy + 3), (cx + 3, cy - 3)):
        put(im, sx, sy, L)
    put(im, 8, 7, D); put(im, 9, 6, M)
    outline(im); return im

def hammer(m):
    L, M, D = MATS[m]; im = Image.new('RGBA', (16, 16))
    handle(im, 1, 9); put(im, 0, 15, D)
    cx, cy = 11, 4
    for y in range(16):
        for x in range(16):
            u = (x - cx) - (y - cy); v = (x - cx) + (y - cy)
            if abs(v) <= 5 and abs(u) <= 2:  # head runs across the handle
                put(im, x, y, L if u <= -2 else D if u >= 2 else M)
    outline(im); return im

def staff(m):
    L, M, D = MATS[m]; im = Image.new('RGBA', (16, 16))
    handle(im, 1, 10); put(im, 0, 15, D)
    for x, y in ((10, 3), (11, 2), (13, 2), (14, 3), (13, 6), (12, 6), (10, 5), (14, 5)): put(im, x, y, M)
    for x, y in ((12, 1), (15, 4), (11, 6), (9, 4)): put(im, x, y, D)
    for x, y in ((11, 3), (12, 3), (13, 3), (11, 4), (12, 4), (13, 4), (12, 5), (11, 5), (13, 5)):
        put(im, x, y, GEM[1])
    put(im, 11, 3, GEM[0]); put(im, 12, 3, GEM[0]); put(im, 13, 5, GEM[2])
    put(im, 9, 6, M)
    outline(im); return im

def bow(m, pull=None):
    """pull: None (idle) or 0..2 for the pulling animation."""
    L, M, D = MATS[m]; im = Image.new('RGBA', (16, 16))
    k = 0 if pull is None else pull + 1
    # limb arc: centre (14,14), radius 11, from (3,14) round to (14,3)
    pts = []
    for a in range(180, 271, 3):
        r = math.radians(a)
        pts.append((round(14 + 11 * math.cos(r)), round(14 + 11 * math.sin(r))))
    pts = sorted(set(pts))
    for i, (x, y) in enumerate(pts):
        grip = abs(x - y) <= 1 and 5 <= x <= 8
        put(im, x, y, WOOD[1] if grip else M)
        put(im, x + 1, y + 1, WOOD[2] if grip else D)
    put(im, 3, 14, L); put(im, 14, 3, L)
    # string: from both tips to the nock point
    nx, ny = 8 + k, 8 + k
    for (tx, ty) in ((3, 14), (14, 3)):
        n = max(abs(nx - tx), abs(ny - ty))
        for i in range(n + 1):
            put(im, round(tx + (nx - tx) * i / n), round(ty + (ny - ty) * i / n), STRING)
    if pull is not None:
        for i in range(0, 8 + k):
            x, y = nx - i, ny - i
            if 0 <= x < 16 and 0 <= y < 16: put(im, x, y, (150, 120, 80))
        hx, hy = nx - (8 + k), ny - (8 + k)
        put(im, hx, hy, (200, 200, 200)); put(im, hx + 1, hy, (120, 120, 120)); put(im, hx, hy + 1, (120, 120, 120))
        put(im, nx, ny, (240, 240, 240)); put(im, nx + 1, ny, (230, 60, 60)); put(im, nx, ny + 1, (230, 60, 60))
    outline(im); return im

for m in MATS:
    dagger(m).save(f'{ITEM}/{m}_dagger.png')
    mace(m).save(f'{ITEM}/{m}_mace.png')
    hammer(m).save(f'{ITEM}/{m}_hammer.png')
    staff(m).save(f'{ITEM}/{m}_staff.png')
    bow(m).save(f'{ITEM}/{m}_bow.png')
    for p in range(3): bow(m, p).save(f'{ITEM}/{m}_bow_pulling_{p}.png')

# ---------------- 160x40 smithing images ----------------
def grip(d, x0, x1, cy=20, hw=4):
    d.rectangle([x0, cy - hw, x1, cy + hw], fill=rgba((110, 72, 40)))
    for x in range(x0 + 1, x1, 3): d.line([(x, cy - hw), (x, cy + hw)], fill=rgba((80, 50, 28)))

def shaft(d, x0, x1, cy=20, hw=3):
    d.rectangle([x0, cy - hw, x1, cy + hw], fill=rgba(WOOD[1]))
    d.line([(x0, cy - hw), (x1, cy - hw)], fill=rgba(WOOD[0])); d.line([(x0, cy + hw), (x1, cy + hw)], fill=rgba(WOOD[2]))

def g_dagger(m):
    L, M, D = MATS[m]; im = Image.new('RGBA', (160, 40)); d = ImageDraw.Draw(im)
    for x in range(40, 126):
        hw = 6 if x >= 58 else max(1, int(6 * (x - 40) / 18))
        d.line([(x, 20 - hw), (x, 20 + hw)], fill=rgba(M)); put(im, x, 20 - hw, L); put(im, x, 20 + hw, D)
        if hw > 2: put(im, x, 20, L)
    d.rectangle([126, 9, 130, 31], fill=rgba(D)); d.rectangle([126, 9, 127, 31], fill=rgba(M))
    grip(d, 131, 149); d.rectangle([150, 14, 155, 26], fill=rgba(D))
    return im

def g_mace(m):
    L, M, D = MATS[m]; im = Image.new('RGBA', (160, 40)); d = ImageDraw.Draw(im)
    shaft(d, 70, 150); grip(d, 128, 150, hw=4)
    for bx in (80, 100): d.rectangle([bx, 15, bx + 3, 25], fill=rgba(D))
    d.polygon([(2, 20), (26, 12), (26, 28)], fill=rgba(M)); d.line([(2, 20), (26, 12)], fill=rgba(L))
    d.ellipse([22, 3, 56, 37], fill=rgba(M)); d.ellipse([26, 6, 44, 20], fill=rgba(L)); d.arc([22, 3, 56, 37], 20, 160, fill=rgba(D), width=3)
    for (sx, sy) in ((39, 0), (39, 39), (30, 2), (48, 2), (30, 37), (48, 37)):
        d.polygon([(sx - 2, 20 + (sy - 20) * 0.75), (sx + 2, 20 + (sy - 20) * 0.75), (sx, sy)], fill=rgba(L))
    d.polygon([(54, 12), (76, 16), (76, 24), (54, 28)], fill=rgba(D)); d.line([(54, 12), (76, 16)], fill=rgba(M))
    d.rectangle([151, 14, 157, 26], fill=rgba(D))
    return im

def g_hammer(m):
    L, M, D = MATS[m]; im = Image.new('RGBA', (160, 40)); d = ImageDraw.Draw(im)
    shaft(d, 90, 152); grip(d, 124, 150, hw=4); d.rectangle([152, 15, 157, 25], fill=rgba(D))
    d.rectangle([2, 4, 32, 36], fill=rgba(M)); d.rectangle([2, 4, 32, 7], fill=rgba(L)); d.rectangle([2, 33, 32, 36], fill=rgba(D))
    d.rectangle([2, 4, 4, 36], fill=rgba(L))
    d.rectangle([32, 7, 63, 33], fill=rgba(M)); d.rectangle([32, 7, 63, 10], fill=rgba(L)); d.rectangle([32, 30, 63, 33], fill=rgba(D))
    d.polygon([(63, 11), (94, 16), (94, 24), (63, 29)], fill=rgba(D)); d.line([(63, 11), (94, 16)], fill=rgba(M))
    return im

def g_staff(m):
    L, M, D = MATS[m]; im = Image.new('RGBA', (160, 40)); d = ImageDraw.Draw(im)
    shaft(d, 40, 152); d.rectangle([152, 16, 157, 24], fill=rgba(D))
    for bx in (76, 100, 124): d.rectangle([bx, 15, bx + 3, 25], fill=rgba(M)); d.line([(bx, 15), (bx + 3, 15)], fill=rgba(L))
    d.ellipse([2, 6, 30, 34], fill=rgba(M)); d.ellipse([6, 10, 26, 30], fill=rgba(GEM[1])); d.ellipse([9, 12, 17, 20], fill=rgba(GEM[0]))
    d.arc([6, 10, 26, 30], 0, 90, fill=rgba(GEM[2]), width=2)
    d.polygon([(28, 10), (51, 14), (51, 26), (28, 30)], fill=rgba(M)); d.line([(28, 10), (51, 14)], fill=rgba(L)); d.line([(28, 30), (51, 26)], fill=rgba(D))
    for wx in (33, 42): d.polygon([(wx, 12), (wx + 4, 2), (wx + 6, 13)], fill=rgba(L)); d.polygon([(wx, 28), (wx + 4, 38), (wx + 6, 27)], fill=rgba(D))
    return im

def g_bow(m):
    L, M, D = MATS[m]; im = Image.new('RGBA', (160, 40)); d = ImageDraw.Draw(im)
    pts = []
    for i in range(0, 157):
        x = 2 + i; t = (x - 80) / 78.0
        y = 33 - 26 * (1 - t * t)
        pts.append((x, y))
    d.line([(2, 34), (157, 34)], fill=STRING, width=1)
    for x, y in pts:
        g = 64 <= x <= 95
        col = WOOD[1] if g else M
        d.line([(x, y - 3), (x, y + 3)], fill=rgba(col)); put(im, x, int(y - 3), WOOD[0] if g else L); put(im, x, int(y + 3), WOOD[2] if g else D)
    for x in range(66, 94, 3): d.line([(x, 5), (x, 11)], fill=rgba((80, 50, 28)))
    d.rectangle([0, 30, 5, 36], fill=rgba(D)); d.rectangle([154, 30, 159, 36], fill=rgba(D))
    return im

for m in ('copper', 'iron', 'gold', 'diamond'):
    g_dagger(m).save(f'{GUI}/dagger_{m}.png'); g_mace(m).save(f'{GUI}/mace_{m}.png')
    g_hammer(m).save(f'{GUI}/hammer_{m}.png'); g_staff(m).save(f'{GUI}/staff_{m}.png'); g_bow(m).save(f'{GUI}/bow_{m}.png')
print('ok')

# ---------------- armor (front views, hammered span x 10..150) ----------------
def shade_rect(d, box, L, M, D):
    x0, y0, x1, y1 = box
    d.rectangle(box, fill=rgba(M)); d.line([(x0, y0), (x1, y0)], fill=rgba(L)); d.line([(x0, y0), (x0, y1)], fill=rgba(L))
    d.line([(x0, y1), (x1, y1)], fill=rgba(D)); d.line([(x1, y0), (x1, y1)], fill=rgba(D))

def g_helmet(m):
    L, M, D = MATS[m]; im = Image.new('RGBA', (160, 40)); d = ImageDraw.Draw(im)
    d.pieslice([24, 4, 136, 76], 180, 360, fill=rgba(M))
    d.arc([24, 4, 136, 76], 200, 300, fill=rgba(L), width=3); d.arc([24, 4, 136, 76], 300, 360, fill=rgba(D), width=3)
    d.rectangle([10, 26, 40, 38], fill=rgba(M)); d.rectangle([120, 26, 150, 38], fill=rgba(M))
    d.line([(10, 26), (40, 26)], fill=rgba(L)); d.line([(10, 38), (40, 38)], fill=rgba(D)); d.line([(120, 38), (150, 38)], fill=rgba(D))
    d.rectangle([50, 30, 110, 34], fill=rgba(D)); d.line([(80, 6), (80, 30)], fill=rgba(L))
    return im

def g_chestplate(m):
    """Flat plate cuirass seen from the front: pauldrons at both ends, a straight torso made of
    horizontal lames with a centre ridge and rivets. No rounded chest shapes."""
    L, M, D = MATS[m]; im = Image.new('RGBA', (160, 40)); d = ImageDraw.Draw(im)
    # torso: straight sides, neck opening at the top centre
    d.polygon([(38, 8), (68, 8), (72, 13), (88, 13), (92, 8), (122, 8), (122, 38), (38, 38)], fill=rgba(M))
    d.line([(38, 8), (68, 8), (72, 13), (88, 13), (92, 8), (122, 8)], fill=rgba(L))
    d.line([(38, 8), (38, 38)], fill=rgba(L)); d.line([(122, 8), (122, 38)], fill=rgba(D))
    # horizontal lames
    for y in (20, 27, 33):
        d.line([(39, y), (121, y)], fill=rgba(D)); d.line([(39, y + 1), (121, y + 1)], fill=rgba(L))
    # centre ridge
    d.line([(80, 14), (80, 38)], fill=rgba(L)); d.line([(81, 14), (81, 38)], fill=rgba(D))
    # rivets along the sides
    for y in (12, 17, 24, 31, 36):
        put(im, 42, y, L); put(im, 118, y, L)
    # pauldrons: three overlapping plates on each side
    for i, (x0, x1) in enumerate(((10, 22), (18, 30), (26, 38))):
        y0 = 4 + i * 2
        d.rectangle([x0, y0, x1, y0 + 14], fill=rgba(M if i % 2 == 0 else D))
        d.line([(x0, y0), (x1, y0)], fill=rgba(L))
        mx0, mx1 = 160 - x1, 160 - x0
        d.rectangle([mx0, y0, mx1, y0 + 14], fill=rgba(M if i % 2 == 0 else D))
        d.line([(mx0, y0), (mx1, y0)], fill=rgba(L))
    d.line([(10, 18), (38, 22)], fill=rgba(D)); d.line([(122, 22), (150, 18)], fill=rgba(D))
    # bottom trim
    d.rectangle([38, 36, 122, 38], fill=rgba(D))
    return im

def g_leggings(m):
    L, M, D = MATS[m]; im = Image.new('RGBA', (160, 40)); d = ImageDraw.Draw(im)
    shade_rect(d, [50, 2, 110, 12], L, M, D)
    d.polygon([(50, 12), (78, 12), (40, 38), (10, 38)], fill=rgba(M)); d.polygon([(82, 12), (110, 12), (150, 38), (120, 38)], fill=rgba(M))
    d.line([(50, 12), (10, 38)], fill=rgba(L)); d.line([(110, 12), (150, 38)], fill=rgba(D))
    d.rectangle([10, 34, 40, 38], fill=rgba(D)); d.rectangle([120, 34, 150, 38], fill=rgba(D))
    d.rectangle([76, 4, 84, 10], fill=rgba(L))
    return im

def g_boots(m):
    L, M, D = MATS[m]; im = Image.new('RGBA', (160, 40)); d = ImageDraw.Draw(im)
    # left boot: toe at the far left, heel towards the middle
    d.polygon([(10, 38), (10, 28), (30, 24), (46, 6), (76, 6), (76, 38)], fill=rgba(M))
    d.polygon([(150, 38), (150, 28), (130, 24), (114, 6), (84, 6), (84, 38)], fill=rgba(M))
    for poly in (((10, 28), (30, 24), (46, 6), (76, 6)), ((150, 28), (130, 24), (114, 6), (84, 6))):
        d.line(poly, fill=rgba(L))
    d.rectangle([10, 35, 76, 38], fill=rgba(D)); d.rectangle([84, 35, 150, 38], fill=rgba(D))
    d.rectangle([46, 6, 76, 10], fill=rgba(L)); d.rectangle([84, 6, 114, 10], fill=rgba(L))
    return im

for m in ('copper', 'iron', 'gold', 'diamond'):
    g_helmet(m).save(f'{GUI}/helmet_{m}.png'); g_chestplate(m).save(f'{GUI}/chestplate_{m}.png')
    g_leggings(m).save(f'{GUI}/leggings_{m}.png'); g_boots(m).save(f'{GUI}/boots_{m}.png')
print('armor ok')
