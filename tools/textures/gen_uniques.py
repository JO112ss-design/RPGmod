"""Icons and models for the bosses' unique weapons (the regular weapon shapes in each boss's colours)."""
import json, os
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
src = open(os.path.join(HERE, 'gen_weapons.py'), encoding='utf-8').read()
ns = {'__file__': os.path.join(HERE, 'gen_weapons.py')}
exec(src[:src.index('\nfor m in MATS:')], ns)  # the shapes only, nothing saved
MATS, put, outline, handle = ns['MATS'], ns['put'], ns['outline'], ns['handle']

ASSETS = os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'rpgmod')
TEX = os.path.join(ASSETS, 'textures', 'item', 'unique'); MODEL = os.path.join(ASSETS, 'models', 'item', 'unique'); ITEMS = os.path.join(ASSETS, 'items', 'unique')
for d in (TEX, MODEL, ITEMS): os.makedirs(d, exist_ok=True)

MATS['slime'] = ((200, 255, 190), (110, 215, 95), (45, 140, 50))
MATS['golem'] = ((214, 208, 196), (146, 140, 128), (84, 80, 74))
MATS['lich'] = ((222, 204, 255), (150, 112, 222), (78, 48, 140))
MATS['spider'] = ((240, 120, 120), (160, 40, 52), (62, 20, 30))

def sword(m):
    L, M, D = MATS[m]; im = Image.new('RGBA', (16, 16))
    handle(im, 1, 3); put(im, 0, 15, D)
    for x, y in ((3, 10), (4, 11), (5, 12), (2, 9), (6, 13)): put(im, x, y, D)  # guard
    for x in range(5, 15):  # blade along the diagonal
        y = 15 - x
        put(im, x, y, M); put(im, x - 1, y, L); put(im, x, y + 1, D)
    put(im, 15, 0, L)
    outline(im); return im

def accent(im, m):
    """A touch of each boss: slime drips, moss, soul sparks, red eyes."""
    px = im.load()
    spots = {'slime': ((120, 230, 110, 255), 3), 'golem': ((96, 140, 70, 255), 5), 'lich': ((120, 230, 255, 255), 4), 'spider': ((20, 10, 12, 255), 4)}
    c, k = spots[m]
    n = 0
    for y in range(16):
        for x in range(16):
            if px[x, y][3] and px[x, y] != (34, 24, 18, 255) and (x * 7 + y * 13) % 11 == 0:
                n += 1
                if n % k == 0: px[x, y] = c
    return im

UNIQUES = [  # id, shape, colours
    ('slime_greatsword', 'sword', 'slime'), ('bouncing_hammer', 'hammer', 'slime'),
    ('rockbreaker_mace', 'mace', 'golem'), ('stonelord_spear', 'spear', 'golem'), ('stone_fists', 'knuckle', 'golem'),
    ('lich_staff', 'staff', 'lich'), ('dead_lute', 'lute', 'lich'),
    ('queen_fang', 'dagger', 'spider'), ('silk_bow', 'bow', 'spider'), ('venom_gunblade', 'gunblade', 'spider'),
]
SHAPES = {'sword': sword}
for k in ('hammer', 'mace', 'spear', 'knuckle', 'staff', 'lute', 'dagger', 'bow', 'gunblade'): SHAPES[k] = ns[k]

def save_json(path, obj):
    with open(path, 'w') as f: json.dump(obj, f, indent=2); f.write('\n')

for uid, shape, m in UNIQUES:
    if shape == 'staff': ns['GEM'] = ((200, 250, 255), (90, 200, 230), (30, 110, 150))  # a soul in the lich's staff
    accent(SHAPES[shape](m), m).save(f'{TEX}/{uid}.png')
    model = {'parent': 'minecraft:item/handheld', 'textures': {'layer0': f'rpgmod:item/unique/{uid}'}}
    if uid == 'slime_greatsword':  # a great sword: much bigger in hand
        model['display'] = {
            'thirdperson_righthand': {'rotation': [0, -90, 55], 'translation': [0, 7, 1], 'scale': [1.45, 1.45, 1.45]},
            'thirdperson_lefthand': {'rotation': [0, 90, -55], 'translation': [0, 7, 1], 'scale': [1.45, 1.45, 1.45]},
            'firstperson_righthand': {'rotation': [0, -90, 25], 'translation': [1.13, 5.2, 1.13], 'scale': [1.1, 1.1, 1.1]},
            'firstperson_lefthand': {'rotation': [0, 90, -25], 'translation': [1.13, 5.2, 1.13], 'scale': [1.1, 1.1, 1.1]}}
    if shape == 'bow':
        model['parent'] = 'minecraft:item/bow'
        for p in range(3):
            accent(ns['bow'](m, p), m).save(f'{TEX}/{uid}_pulling_{p}.png')
            save_json(f'{MODEL}/{uid}_pulling_{p}.json', {'parent': f'minecraft:item/bow_pulling_{p}', 'textures': {'layer0': f'rpgmod:item/unique/{uid}_pulling_{p}'}})
        item = {'model': {'type': 'minecraft:condition', 'property': 'minecraft:using_item',
                          'on_false': {'type': 'minecraft:model', 'model': f'rpgmod:item/unique/{uid}'},
                          'on_true': {'type': 'minecraft:range_dispatch', 'property': 'minecraft:use_duration', 'scale': 0.05,
                                      'entries': [{'threshold': 0.65, 'model': {'type': 'minecraft:model', 'model': f'rpgmod:item/unique/{uid}_pulling_1'}},
                                                  {'threshold': 0.9, 'model': {'type': 'minecraft:model', 'model': f'rpgmod:item/unique/{uid}_pulling_2'}}],
                                      'fallback': {'type': 'minecraft:model', 'model': f'rpgmod:item/unique/{uid}_pulling_0'}}}}
    else:
        item = {'model': {'type': 'minecraft:model', 'model': f'rpgmod:item/unique/{uid}'}}
    save_json(f'{MODEL}/{uid}.json', model)
    save_json(f'{ITEMS}/{uid}.json', item)
print('uniques ok', len(UNIQUES))
