"""Job change book (16x16): a teal book with a silver frame and two arrows chasing each other."""
from PIL import Image

A = 'src/main/resources/assets/rpgmod/textures/item'
img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
px = img.load()
cover, dark, page = (32, 128, 120, 255), (16, 70, 66, 255), (238, 230, 205, 255)
silver, rune = (215, 225, 235, 255), (255, 214, 90, 255)
for y in range(2, 14):
    for x in range(3, 13):
        px[x, y] = cover
    px[2, y] = dark
    px[13, y] = page
    px[14, y] = page if 3 <= y <= 12 else (0, 0, 0, 0)
for x in range(3, 14):
    px[x, 1] = dark
    px[x, 14] = dark
for x in range(3, 13):
    px[x, 13] = (24, 100, 94, 255)
for x in range(4, 12):
    px[x, 3] = silver
    px[x, 12] = silver
for y in range(3, 13):
    px[4, y] = silver
    px[11, y] = silver
# two arrows going round: top one pointing right, bottom one pointing left
for x in range(6, 10):
    px[x, 6] = rune
    px[x, 9] = rune
px[9, 5] = rune; px[10, 6] = rune; px[9, 7] = rune
px[6, 8] = rune; px[5, 9] = rune; px[6, 10] = rune
img.save(f'{A}/job_change_book.png')
print('ok')
