"""Smithing GUI images (160x40) for the forged shields: a wooden board lying on its side, with a
metal rim, a band across and a boss in the middle in the material's colours."""
from PIL import Image, ImageDraw
import os

GUI = os.path.join(os.path.dirname(__file__), '..', '..', 'src', 'main', 'resources', 'assets', 'rpgmod', 'textures', 'gui')
MATS = {
    'copper': ((255, 180, 140), (231, 124, 86), (160, 76, 46)),
    'iron': ((255, 255, 255), (216, 216, 216), (130, 130, 136)),
    'diamond': ((210, 255, 250), (92, 219, 213), (30, 140, 132)),
}
WOOD = ((176, 132, 80), (137, 103, 59), (92, 66, 34))


def rgba(c):
    return tuple(c) + (255,)


def shield(m):
    L, M, D = MATS[m]
    im = Image.new('RGBA', (160, 40))
    d = ImageDraw.Draw(im)
    # metal rim (outer rounded board), then the wooden face inside it
    d.rounded_rectangle([10, 3, 150, 37], radius=12, fill=rgba(M))
    d.rounded_rectangle([14, 7, 146, 33], radius=9, fill=rgba(WOOD[1]))
    for x in range(20, 142, 12):  # planks
        d.line([(x, 8), (x, 32)], fill=rgba(WOOD[2]))
        d.line([(x + 1, 8), (x + 1, 32)], fill=rgba(WOOD[0]))
    # band across and the boss
    d.rectangle([14, 18, 146, 22], fill=rgba(M))
    d.line([(14, 18), (146, 18)], fill=rgba(L))
    d.line([(14, 22), (146, 22)], fill=rgba(D))
    d.ellipse([66, 6, 94, 34], fill=rgba(M))
    d.arc([66, 6, 94, 34], 180, 300, fill=rgba(L), width=2)
    d.arc([66, 6, 94, 34], 300, 120, fill=rgba(D), width=2)
    d.ellipse([75, 15, 85, 25], fill=rgba(L))
    # rim highlights
    d.arc([10, 3, 34, 27], 180, 270, fill=rgba(L), width=2)
    d.line([(22, 3), (138, 3)], fill=rgba(L))
    d.line([(22, 37), (138, 37)], fill=rgba(D))
    # rivets
    for x in (24, 48, 112, 136):
        d.point([(x, 10), (x, 30)], fill=rgba(L))
    return im


for m in MATS:
    shield(m).save(f'{GUI}/shield_{m}.png')
print('shields ok')
