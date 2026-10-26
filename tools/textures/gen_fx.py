"""Generates the particle sprites used by skill effects (assets/rpgmod/textures/particle/fx_*.png).

Most sprites are white with the shape in the alpha channel so they can be tinted to any colour and
drawn with additive blending. A few small pixel-art sprites (coin, card, dice, skull) keep their own
colours. Shapes are drawn at 4x and downsampled for smooth edges.
"""
import math, os
from PIL import Image, ImageDraw, ImageFilter

OUT = os.path.join(os.path.dirname(__file__), '..', '..', 'src', 'main', 'resources', 'assets', 'rpgmod', 'textures', 'particle')
os.makedirs(OUT, exist_ok=True)
SS = 4


def save(img, name, size):
    img = img.resize((size, size), Image.LANCZOS)
    img.save(os.path.join(OUT, 'fx_' + name + '.png'))


def field(size, fn):
    """White image whose alpha is fn(x, y) with x, y in [-1, 1]."""
    n = size * SS
    img = Image.new('LA', (n, n))
    px = img.load()
    for j in range(n):
        y = (j + 0.5) / n * 2 - 1
        for i in range(n):
            x = (i + 0.5) / n * 2 - 1
            a = max(0.0, min(1.0, fn(x, -y)))
            px[i, j] = (255, int(a * 255 + 0.5))
    return img.convert('RGBA')


def smooth(e0, e1, v):
    t = max(0.0, min(1.0, (v - e0) / (e1 - e0)))
    return t * t * (3 - 2 * t)


# ---------------------------------------------------------------- soft shapes

save(field(64, lambda x, y: math.exp(-(x * x + y * y) * 4.5) * 1.05), 'glow', 64)
# big soft flash: bright core with a long falloff, high resolution so it stays smooth when huge
save(field(128, lambda x, y: max(0.0, math.exp(-(x * x + y * y) * 9) * 0.9 + math.exp(-(x * x + y * y) * 3) * 0.35 - 0.02)), 'flare', 128)
# bright core for magic projectiles
save(field(32, lambda x, y: max(math.exp(-(x * x + y * y) * 30), 0.6 * math.exp(-(x * x + y * y) * 5))), 'orb', 32)


def spark(x, y):
    r = math.hypot(x, y)
    core = math.exp(-r * r * 40)
    ray = math.exp(-abs(x) * 28) * max(0, 1 - abs(y)) ** 1.6 + math.exp(-abs(y) * 28) * max(0, 1 - abs(x)) ** 1.6
    return core + ray * 0.9


save(field(32, spark), 'spark', 32)


def ring(r0, w):
    return lambda x, y: math.exp(-((math.hypot(x, y) - r0) / w) ** 2)


save(field(128, ring(0.86, 0.045)), 'ring', 128)


def wave(x, y):
    r = math.hypot(x, y)
    if r > 0.97:
        return 0
    edge = math.exp(-((r - 0.9) / 0.05) ** 2)
    inner = smooth(0.35, 0.9, r) * 0.45
    return max(edge, inner) * smooth(0.97, 0.93, r) if r > 0.93 else max(edge, inner)


save(field(128, wave), 'wave', 128)


def streak(x, y):
    # vertical line of light, brightest just below the top (a falling streak's head)
    along = smooth(-1.0, 0.6, y) * smooth(1.0, 0.75, y)
    return math.exp(-(x / 0.07) ** 2) * along


save(field(32, streak), 'streak', 32)


def beam(x, y):
    # straight line of even brightness with soft ends: one segment of a lightning bolt or a link
    return math.exp(-(x / 0.16) ** 2) * smooth(1.0, 0.82, abs(y))


save(field(32, beam), 'beam', 32)


def slash(x, y):
    # arc of a swing around the quad centre (radius ~0.88, spanning ~150 degrees, opening downwards):
    # bright outer edge with a body fading inwards like a motion-blurred blade, tapered ends
    r = math.hypot(x, y)
    th = math.degrees(math.atan2(y, x))
    if th < 0:
        return 0
    taper = smooth(8, 50, th) * smooth(172, 130, th)
    edge = math.exp(-((r - 0.88) / 0.035) ** 2)
    body = smooth(0.5, 0.86, r) * smooth(0.93, 0.88, r) * 0.55
    return (edge + body) * taper


save(field(64, slash), 'slash', 64)


def shard(x, y):
    # elongated diamond, brighter on one face
    d = abs(x) / 0.28 + abs(y) / 0.95
    if d > 1:
        return 0
    face = 0.75 + 0.25 * (1 if x < 0 else 0.4)
    return smooth(1.0, 0.85, d) * face


save(field(32, shard), 'shard', 32)


def cross(x, y):
    a = math.exp(-(x / 0.12) ** 2) * smooth(0.95, 0.7, abs(y))
    b = math.exp(-((y - 0.25) / 0.12) ** 2) * smooth(0.7, 0.45, abs(x))
    return max(a, b) + 0.25 * math.exp(-(x * x + (y - 0.25) ** 2) * 6)


save(field(32, cross), 'cross', 32)


def petal(x, y):
    # teardrop
    yy = (y + 0.1) / 0.85
    w = 0.42 * math.sqrt(max(0.0, 1 - yy * yy)) * (1 - 0.35 * yy)
    if w <= 0:
        return 0
    return smooth(w, w * 0.6, abs(x)) * (0.8 + 0.2 * (1 - abs(x) / w))


save(field(32, petal), 'petal', 32)


def feather(x, y):
    t = (y + 0.9) / 1.8  # 0 at quill, 1 at tip
    if t < 0 or t > 1:
        return 0
    w = 0.32 * math.sin(math.pi * min(1.0, t * 1.15)) * (1 - 0.2 * t)
    shaft = math.exp(-(x / 0.035) ** 2) * 0.9
    vane = 0 if w <= 0 else smooth(w, w * 0.7, abs(x)) * (0.55 + 0.25 * math.sin(abs(x) * 40 + t * 30) ** 2)
    if t < 0.15:
        vane = 0
    return max(shaft, vane)


save(field(32, feather), 'feather', 32)


def smoke_blob(seed):
    import random
    rnd = random.Random(seed)
    lobes = [(rnd.uniform(-0.35, 0.35), rnd.uniform(-0.35, 0.35), rnd.uniform(0.3, 0.55)) for _ in range(6)]

    def f(x, y):
        v = 0
        for cx, cy, r in lobes:
            v += math.exp(-((x - cx) ** 2 + (y - cy) ** 2) / (r * r))
        return smooth(0.3, 1.3, v) * 0.85
    return f


save(field(32, smoke_blob(3)), 'smoke', 32)


def hexagon(x, y):
    # hexagon outline (shield cell)
    a = math.atan2(y, x)
    r = math.hypot(x, y)
    sector = math.pi / 3
    rel = (a % sector) - sector / 2
    edge = 0.82 / math.cos(rel) * math.cos(math.pi / 6)
    return math.exp(-((r - edge) / 0.06) ** 2) + 0.12 * smooth(edge, edge - 0.3, r)


save(field(64, hexagon), 'hex', 64)

# ---------------------------------------------------------------- magic circles (line art)


def circle_canvas(size):
    n = size * SS
    return Image.new('RGBA', (n, n), (255, 255, 255, 0)), n


def ell(d, n, r, w, a=255):
    c = n / 2
    d.ellipse([c - r * c, c - r * c, c + r * c, c + r * c], outline=(255, 255, 255, a), width=max(1, int(w * n)))


def poly(d, n, pts, w, a=255, close=True):
    c = n / 2
    p = [(c + x * c, c - y * c) for x, y in pts]
    if close:
        p.append(p[0])
    d.line(p, fill=(255, 255, 255, a), width=max(1, int(w * n)), joint='curve')


def star_pts(k, r, step, rot=math.pi / 2):
    return [(math.cos(rot + 2 * math.pi * (i * step % k) / k) * r, math.sin(rot + 2 * math.pi * (i * step % k) / k) * r) for i in range(k)]


def glowify(img, n):
    blur = img.filter(ImageFilter.GaussianBlur(n * 0.012))
    out = Image.alpha_composite(blur, img)
    return out


def tick_ring(d, n, r0, r1, count, w, a=255, every=1):
    c = n / 2
    for i in range(count):
        if i % every:
            continue
        ang = 2 * math.pi * i / count
        d.line([(c + math.cos(ang) * r0 * c, c - math.sin(ang) * r0 * c), (c + math.cos(ang) * r1 * c, c - math.sin(ang) * r1 * c)],
               fill=(255, 255, 255, a), width=max(1, int(w * n)))


def runes(d, n, r, count, size, a=230, seed=1):
    import random
    rnd = random.Random(seed)
    c = n / 2
    for i in range(count):
        ang = 2 * math.pi * (i + 0.5) / count
        cx, cy = c + math.cos(ang) * r * c, c - math.sin(ang) * r * c
        s = size * c
        # small angular glyph: 2-3 strokes
        pts = [(rnd.uniform(-1, 1), rnd.uniform(-1, 1)) for _ in range(rnd.randint(3, 4))]
        rot = -ang + math.pi / 2
        line = []
        for px, py in pts:
            x = px * math.cos(rot) - py * math.sin(rot)
            y = px * math.sin(rot) + py * math.cos(rot)
            line.append((cx + x * s, cy + y * s))
        d.line(line, fill=(255, 255, 255, a), width=max(1, int(0.012 * n)))


# arcane: double ring, runes, hexagram, inner ring
img, n = circle_canvas(128)
d = ImageDraw.Draw(img)
ell(d, n, 0.96, 0.022)
ell(d, n, 0.84, 0.014)
runes(d, n, 0.9, 18, 0.035)
poly(d, n, star_pts(3, 0.82, 1), 0.014)
poly(d, n, star_pts(3, 0.82, 1, -math.pi / 2), 0.014)
ell(d, n, 0.42, 0.016)
ell(d, n, 0.12, 0.02)
tick_ring(d, n, 0.42, 0.5, 24, 0.008, 200)
save(glowify(img, n), 'circle_arcane', 128)

# holy: sun circle with rays and cross
img, n = circle_canvas(128)
d = ImageDraw.Draw(img)
ell(d, n, 0.96, 0.02)
ell(d, n, 0.78, 0.024)
tick_ring(d, n, 0.8, 0.94, 32, 0.012, 230)
tick_ring(d, n, 0.8, 0.98, 32, 0.02, 255, every=4)
poly(d, n, [(0, 0.7), (0, -0.7)], 0.03, close=False)
poly(d, n, [(-0.7, 0), (0.7, 0)], 0.03, close=False)
ell(d, n, 0.3, 0.03)
poly(d, n, star_pts(8, 0.62, 3), 0.01, 200)
save(glowify(img, n), 'circle_holy', 128)

# hex: pentagram curse circle with spikes
img, n = circle_canvas(128)
d = ImageDraw.Draw(img)
ell(d, n, 0.95, 0.02)
ell(d, n, 0.86, 0.012)
poly(d, n, star_pts(5, 0.85, 2), 0.02)
tick_ring(d, n, 0.95, 1.0, 10, 0.02)
runes(d, n, 0.905, 15, 0.03, seed=7)
ell(d, n, 0.25, 0.015)
save(glowify(img, n), 'circle_hex', 128)

# clock: time circle
img, n = circle_canvas(128)
d = ImageDraw.Draw(img)
ell(d, n, 0.96, 0.02)
ell(d, n, 0.88, 0.01)
tick_ring(d, n, 0.76, 0.88, 60, 0.006, 190)
tick_ring(d, n, 0.68, 0.88, 12, 0.022, 255)
ell(d, n, 0.55, 0.012)
poly(d, n, [(0, 0), (0, 0.5)], 0.025, close=False)
poly(d, n, [(0, 0), (0.32, -0.18)], 0.03, close=False)
ell(d, n, 0.06, 0.03)
runes(d, n, 0.62, 12, 0.03, a=170, seed=4)
save(glowify(img, n), 'circle_clock', 128)

# nature: rosette for healing and snares
img, n = circle_canvas(128)
d = ImageDraw.Draw(img)
ell(d, n, 0.95, 0.02)
for k in range(6):
    ang = math.pi * 2 * k / 6
    cx, cy = math.cos(ang) * 0.38, math.sin(ang) * 0.38
    c = n / 2
    d.ellipse([c + (cx - 0.38) * c, c - (cy + 0.38) * c, c + (cx + 0.38) * c, c - (cy - 0.38) * c], outline=(255, 255, 255, 220), width=int(0.014 * n))
ell(d, n, 0.76, 0.01, 200)
tick_ring(d, n, 0.8, 0.92, 48, 0.006, 160)
ell(d, n, 0.1, 0.03)
save(glowify(img, n), 'circle_nature', 128)

# ---------------------------------------------------------------- runes (single glyphs)
GLYPHS = [
    [[(0, 0.9), (0, -0.9)], [(0, 0.5), (0.5, 0.9)], [(0, 0.1), (0.5, 0.5)]],
    [[(-0.5, -0.9), (-0.5, 0.9), (0.5, 0.2), (-0.5, -0.1)]],
    [[(-0.5, 0.9), (0.5, -0.9)], [(0.5, 0.9), (-0.5, -0.9)], [(-0.6, 0), (0.6, 0)]],
    [[(0, -0.9), (0, 0.9)], [(-0.55, 0.4), (0, 0), (0.55, 0.4)], [(-0.55, -0.4), (0, -0.8)]],
]
for i, strokes in enumerate(GLYPHS):
    img, n = circle_canvas(32)
    d = ImageDraw.Draw(img)
    for s in strokes:
        poly(d, n, [(x * 0.8, y * 0.8) for x, y in s], 0.09, close=False)
    save(glowify(img, n), 'rune%d' % i, 32)

# ---------------------------------------------------------------- pixel art (own colours)


def pixel(name, rows, palette):
    h = len(rows)
    img = Image.new('RGBA', (h, h), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != '.':
                img.putpixel((x, y), palette[ch])
    img.save(os.path.join(OUT, 'fx_' + name + '.png'))


pixel('coin', [
    '................',
    '.....oooooo.....',
    '...ooyyyyyyoo...',
    '..oyyyyyyyyyyo..',
    '..oyyhhyyyyyyo..',
    '.oyyhhyddddyyyo.',
    '.oyyhydyyyydyyo.',
    '.oyyydyyyyyydyo.',
    '.oyyydyyyyyydyo.',
    '.oyyyydyyyydyyo.',
    '.oyyyyyddddyyyo.',
    '..oyyyyyyyyyyo..',
    '..oyyyyyyyyyyo..',
    '...ooyyyyyyoo...',
    '.....oooooo.....',
    '................'],
    {'o': (150, 95, 20, 255), 'y': (255, 205, 60, 255), 'h': (255, 245, 190, 255), 'd': (200, 140, 30, 255)})

pixel('card', [
    '................',
    '...wwwwwwwwww...',
    '..wwwwwwwwwwww..',
    '..wwrwwwwwwwww..',
    '..wwwwwwwwwwww..',
    '..wwwwwrrwwwww..',
    '..wwwwrrrrwwww..',
    '..wwwrrrrrrwww..',
    '..wwwrrrrrrwww..',
    '..wwwwrrrrwwww..',
    '..wwwwwrrwwwww..',
    '..wwwwwwwwwwww..',
    '..wwwwwwwwwrww..',
    '..wwwwwwwwwwww..',
    '...wwwwwwwwww...',
    '................'],
    {'w': (250, 248, 240, 255), 'r': (220, 40, 50, 255)})

pixel('dice', [
    '................',
    '..wwwwwwwwwwww..',
    '.wwwwwwwwwwwwww.',
    '.wwkkwwwwwwkkww.',
    '.wwkkwwwwwwkkww.',
    '.wwwwwwwwwwwwww.',
    '.wwwwwwwwwwwwww.',
    '.wwwwwwkkwwwwww.',
    '.wwwwwwkkwwwwww.',
    '.wwwwwwwwwwwwww.',
    '.wwwwwwwwwwwwww.',
    '.wwkkwwwwwwkkww.',
    '.wwkkwwwwwwkkww.',
    '.wwwwwwwwwwwwww.',
    '..wwwwwwwwwwww..',
    '................'],
    {'w': (245, 245, 245, 255), 'k': (30, 30, 40, 255)})

pixel('skull', [
    '................',
    '.....wwwwww.....',
    '...wwwwwwwwww...',
    '..wwwwwwwwwwww..',
    '..wwwwwwwwwwww..',
    '..wwkkkwwkkkww..',
    '..wwkkkwwkkkww..',
    '..wwkkkwwkkkww..',
    '...wwwwkkwwww...',
    '....wwwwwwww....',
    '....wkwkwkww....',
    '....wwwwwwww....',
    '.....wkwkww.....',
    '................',
    '................',
    '................'],
    {'w': (235, 225, 245, 255), 'k': (40, 10, 50, 255)})

# ---------------------------------------------------------------- music note (tintable, like the soft shapes)
n = 32 * SS
img = Image.new('RGBA', (n, n), (255, 255, 255, 0))
d = ImageDraw.Draw(img)
W = (255, 255, 255, 255)
d.ellipse([n * 0.14, n * 0.62, n * 0.50, n * 0.88], fill=W)                      # head
d.rectangle([n * 0.42, n * 0.12, n * 0.50, n * 0.76], fill=W)                     # stem
d.polygon([(n * 0.46, n * 0.12), (n * 0.84, n * 0.30), (n * 0.80, n * 0.44), (n * 0.46, n * 0.27)], fill=W)  # flag
img = img.filter(ImageFilter.GaussianBlur(SS * 0.4))
save(img, 'note', 32)

# ---------------------------------------------------------------- ultimate sprites (tintable)


def flame(x, y):
    # a flame tongue: round glowing base, curling pointed tip, soft edges
    yy = y + 0.35
    if yy < -0.6:
        return 0
    if yy < 0:
        w = 0.5 * math.sqrt(max(0.0, 1 - (yy / 0.6) ** 2))
    else:
        t = min(1.0, yy / 1.15)
        w = 0.5 * (1 - t) ** 1.4
    sway = 0.12 * max(0.0, yy) ** 1.5 * math.sin(yy * 3.2)
    d = abs(x - sway)
    if w <= 0.002:
        return 0
    body = smooth(w, w * 0.25, d)
    core = math.exp(-((x - sway) ** 2 / 0.02 + (yy + 0.1) ** 2 / 0.08)) * 0.5
    return min(1.0, body * (0.6 + 0.4 * (1 - max(0.0, yy))) + core)


save(field(32, flame), 'flame', 32)


def crack_img(size, seed):
    import random
    rnd = random.Random(seed)
    n = size * SS
    img = Image.new('RGBA', (n, n), (255, 255, 255, 0))
    d = ImageDraw.Draw(img)
    c = n / 2
    for k in range(7):
        ang = 2 * math.pi * k / 7 + rnd.uniform(-0.3, 0.3)
        x, y, w = c, c, n * 0.028
        for s in range(9):
            ang += rnd.uniform(-0.45, 0.45)
            step = n * rnd.uniform(0.04, 0.07)
            nx, ny = x + math.cos(ang) * step, y + math.sin(ang) * step
            d.line([(x, y), (nx, ny)], fill=(255, 255, 255, 255), width=max(1, int(w)))
            if rnd.random() < 0.3:
                ba = ang + rnd.choice([-1, 1]) * rnd.uniform(0.6, 1.1)
                d.line([(nx, ny), (nx + math.cos(ba) * step * 0.8, ny + math.sin(ba) * step * 0.8)], fill=(255, 255, 255, 220), width=max(1, int(w * 0.6)))
            x, y, w = nx, ny, w * 0.86
    return glowify(img, n)


save(crack_img(128, 7), 'crack', 128)


def blade(x, y):
    # a long straight blade pointing up (+y), bright edge line in the middle
    t = (y + 0.95) / 1.9
    if t < 0 or t > 1:
        return 0
    w = 0.16 * (1 - t ** 3) if t > 0.12 else 0.05
    if t > 0.12 and t < 0.16:
        w = 0.3  # guard
    if w <= 0:
        return 0
    body = smooth(w, w * 0.7, abs(x))
    core = math.exp(-(x / 0.03) ** 2) * 0.6
    return min(1.0, body * 0.85 + core)


save(field(32, blade), 'blade', 32)


def twinkle(x, y):
    # four-pointed star glint
    r = math.hypot(x, y)
    a = math.exp(-abs(x) * 18) * max(0, 1 - abs(y)) ** 2.5 + math.exp(-abs(y) * 18) * max(0, 1 - abs(x)) ** 2.5
    d = (math.exp(-abs(x - y) * 22) + math.exp(-abs(x + y) * 22)) * max(0, 1 - r * 1.6) ** 2 * 0.5
    return a + d + math.exp(-r * r * 60)


save(field(32, twinkle), 'twinkle', 32)


def sakura(x, y):
    # five petals with notched tips
    r = math.hypot(x, y)
    th = math.atan2(y, x)
    k = (th * 5 / (2 * math.pi)) % 1.0
    notch = 0.12 * math.exp(-((k - 0.5) / 0.06) ** 2)
    edge = 0.92 * (0.55 + 0.45 * abs(math.cos(th * 2.5))) - notch
    v = smooth(edge, edge - 0.08, r)
    centre = math.exp(-r * r * 40) * 0.6
    return min(1.0, v * (0.75 + 0.25 * r) + centre)


save(field(32, sakura), 'sakura', 32)


def moon(x, y):
    a = smooth(0.8, 0.74, math.hypot(x, y))
    b = smooth(0.7, 0.64, math.hypot(x - 0.38, y + 0.12))
    return max(0.0, a - b)


save(field(64, moon), 'moon', 64)


def lightning_img(size, seed):
    import random
    rnd = random.Random(seed)
    n = size * SS
    img = Image.new('RGBA', (n, n), (255, 255, 255, 0))
    d = ImageDraw.Draw(img)
    pts = [(n * 0.5, 0)]
    for i in range(1, 9):
        pts.append((n * 0.5 + rnd.uniform(-0.22, 0.22) * n, n * i / 8))
    d.line(pts, fill=(255, 255, 255, 255), width=int(n * 0.05), joint='curve')
    for i in (2, 5):
        x, y = pts[i]
        d.line([(x, y), (x + rnd.choice([-1, 1]) * n * 0.2, y + n * 0.14)], fill=(255, 255, 255, 220), width=int(n * 0.025))
    return glowify(glowify(img, n), n)


save(lightning_img(64, 3), 'lightning', 64)


def crest_img(size):
    # a heater shield with a cross
    n = size * SS
    img = Image.new('RGBA', (n, n), (255, 255, 255, 0))
    d = ImageDraw.Draw(img)
    W = (255, 255, 255, 255)
    shape = [(n * 0.16, n * 0.12), (n * 0.84, n * 0.12), (n * 0.84, n * 0.48), (n * 0.5, n * 0.92), (n * 0.16, n * 0.48)]
    d.line(shape + [shape[0]], fill=W, width=int(n * 0.05), joint='curve')
    d.line([(n * 0.5, n * 0.24), (n * 0.5, n * 0.74)], fill=W, width=int(n * 0.05))
    d.line([(n * 0.3, n * 0.4), (n * 0.7, n * 0.4)], fill=W, width=int(n * 0.05))
    return glowify(img, n)


save(crest_img(64), 'crest', 64)


def eye(x, y):
    lid = abs(y) < 0.45 * (1 - x * x) ** 1.2 if abs(x) < 1 else False
    outline = math.exp(-((abs(y) - 0.45 * max(0, 1 - x * x) ** 1.2) / 0.04) ** 2) * (1 if abs(x) < 0.95 else 0)
    iris = math.exp(-((math.hypot(x, y) - 0.24) / 0.05) ** 2)
    pupil = smooth(0.13, 0.09, math.hypot(x, y))
    return min(1.0, outline + iris + pupil + (0.12 if lid else 0))


save(field(64, eye), 'eye', 64)


def chain_img(size):
    n = size * SS
    img = Image.new('RGBA', (n, n), (255, 255, 255, 0))
    d = ImageDraw.Draw(img)
    for i in range(3):
        cy = n * (0.18 + i * 0.32)
        if i % 2 == 0:
            d.ellipse([n * 0.34, cy - n * 0.17, n * 0.66, cy + n * 0.17], outline=(255, 255, 255, 255), width=int(n * 0.06))
        else:
            d.line([(n * 0.5, cy - n * 0.17), (n * 0.5, cy + n * 0.17)], fill=(255, 255, 255, 255), width=int(n * 0.07))
    return glowify(img, n)


save(chain_img(32), 'chain', 32)


def fang(x, y):
    # a curved claw / fang mark
    t = (y + 0.9) / 1.8
    if t < 0 or t > 1:
        return 0
    cx = 0.35 * (t - 0.5) ** 2 * 4 - 0.2
    w = 0.14 * (1 - t) ** 0.8 + 0.01
    return smooth(w, w * 0.5, abs(x - cx))


save(field(32, fang), 'fang', 32)

print('wrote sprites to', os.path.abspath(OUT))
