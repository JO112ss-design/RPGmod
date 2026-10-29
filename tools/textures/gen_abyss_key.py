"""The abyss key (16x16): a black iron key with a violet soul in its bow."""
import os
from PIL import Image
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'src', 'main', 'resources', 'assets', 'rpgmod', 'textures', 'item', 'abyss_key.png')
im = Image.new('RGBA', (16, 16))
px = im.load()
D, M, L = (34, 26, 44, 255), (70, 56, 88, 255), (118, 98, 140, 255)
V, VL = (150, 70, 230, 255), (220, 170, 255, 255)
# ring (bow) at the top left
for y in range(1, 8):
    for x in range(1, 8):
        d = ((x - 4) ** 2 + (y - 4) ** 2) ** 0.5
        if 2.2 <= d <= 3.4: px[x, y] = M if x + y > 8 else L
        elif d < 2.2: px[x, y] = V if d > 1 else VL
# shaft down to the right
for i in range(6, 14):
    px[i, i] = M; px[i + 1, i] = D if i + 1 < 16 else D
# teeth
for x, y in ((11, 13), (12, 13), (12, 14), (9, 11), (10, 11), (10, 12)): px[x, y] = M
for x, y in ((13, 14), (11, 12)): px[x, y] = D
# outline
src = im.copy(); sp = src.load()
for y in range(16):
    for x in range(16):
        if sp[x, y][3] == 0 and any(0 <= x + dx < 16 and 0 <= y + dy < 16 and sp[x + dx, y + dy][3] > 0 for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
            px[x, y] = (14, 8, 20, 255)
im.save(OUT)
print('abyss key ok')
