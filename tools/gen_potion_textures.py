"""Generates the HP / MP potion icons (16x16): a round flask with a cork, filled with red or blue."""
from PIL import Image

A = 'src/main/resources/assets/rpgmod/textures/item'

# rows of the flask outline: '.' empty, 'c' cork, 'g' glass rim, 'L' liquid, 'h' highlight, 'd' dark liquid edge
SHAPE = [
    '................',
    '......cccc......',
    '......cccc......',
    '.....gggggg.....',
    '......g..g......',
    '......gLLg......',
    '.....gLLLLg.....',
    '....gLhLLLLg....',
    '...gLhhLLLLLg...',
    '...gLhLLLLLLg...',
    '...gLLLLLLLdg...',
    '...gLLLLLLLdg...',
    '....gLLLLLdg....',
    '.....gddddg.....',
    '......gggg......',
    '................',
]


def make(name, liquid, dark, light):
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    colors = {
        'c': (139, 94, 60, 255),
        'g': (205, 225, 235, 255),
        'L': liquid + (255,),
        'd': dark + (255,),
        'h': light + (255,),
    }
    for y, row in enumerate(SHAPE):
        for x, ch in enumerate(row):
            if ch in colors:
                img.putpixel((x, y), colors[ch])
    # a lighter top layer of the liquid
    for x in range(16):
        if SHAPE[5][x] == 'L':
            img.putpixel((x, 5), light + (255,))
    img.save(f'{A}/{name}.png')


make('hp_potion', (220, 40, 50), (140, 20, 30), (255, 140, 140))
make('mp_potion', (50, 110, 230), (25, 55, 150), (140, 190, 255))
print('ok')
