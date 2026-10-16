"""Generates item models, recipes, tags and lang entries for the RPG weapons."""
import json, os
R = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources')
A = os.path.join(R, 'assets', 'rpgmod'); D = os.path.join(R, 'data')

def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f: json.dump(obj, f, ensure_ascii=False, indent=2); f.write('\n')

MATS = ['copper', 'iron', 'gold', 'diamond', 'netherite']
INGOT = {'copper': 'minecraft:copper_ingot', 'iron': 'minecraft:iron_ingot', 'gold': 'minecraft:gold_ingot', 'diamond': 'minecraft:diamond'}
MELEE = ['dagger', 'mace', 'hammer', 'staff']

# ---- models
for m in MATS:
    for t in MELEE:
        n = f'{m}_{t}'
        write(f'{A}/models/item/{n}.json', {'parent': 'minecraft:item/handheld', 'textures': {'layer0': f'rpgmod:item/{n}'}})
        write(f'{A}/items/{n}.json', {'model': {'type': 'minecraft:model', 'model': f'rpgmod:item/{n}'}})
    n = f'{m}_bow'
    write(f'{A}/models/item/{n}.json', {'parent': 'minecraft:item/bow', 'textures': {'layer0': f'rpgmod:item/{n}'}})
    for p in range(3):
        write(f'{A}/models/item/{n}_pulling_{p}.json', {'parent': f'minecraft:item/bow_pulling_{p}', 'textures': {'layer0': f'rpgmod:item/{n}_pulling_{p}'}})
    write(f'{A}/items/{n}.json', {'model': {
        'type': 'minecraft:condition', 'property': 'minecraft:using_item',
        'on_false': {'type': 'minecraft:model', 'model': f'rpgmod:item/{n}'},
        'on_true': {'type': 'minecraft:range_dispatch', 'property': 'minecraft:use_duration', 'scale': 0.05,
                    'entries': [{'threshold': 0.65, 'model': {'type': 'minecraft:model', 'model': f'rpgmod:item/{n}_pulling_1'}},
                                {'threshold': 0.9, 'model': {'type': 'minecraft:model', 'model': f'rpgmod:item/{n}_pulling_2'}}],
                    'fallback': {'type': 'minecraft:model', 'model': f'rpgmod:item/{n}_pulling_0'}}}})

# ---- recipes (normal crafting = plain weapons without forging options)
PATTERNS = {
    'dagger': (['I', 'S'], {'I': None, 'S': 'minecraft:stick'}),
    'mace': ([' I ', 'III', ' S '], {'I': None, 'S': 'minecraft:stick'}),
    'hammer': (['III', 'ISI', ' S '], {'I': None, 'S': 'minecraft:stick'}),
    'staff': (['A', 'I', 'S'], {'A': 'minecraft:amethyst_shard', 'I': None, 'S': 'minecraft:stick'}),
}
for m, ingot in INGOT.items():
    for t, (pat, key) in PATTERNS.items():
        k = {c: (ingot if v is None else v) for c, v in key.items()}
        write(f'{D}/rpgmod/recipe/{m}_{t}.json', {'type': 'minecraft:crafting_shaped', 'category': 'equipment',
                                                   'pattern': pat, 'key': k, 'result': {'id': f'rpgmod:{m}_{t}', 'count': 1}})
    write(f'{D}/rpgmod/recipe/{m}_bow.json', {'type': 'minecraft:crafting_shapeless', 'category': 'equipment',
                                               'ingredients': ['minecraft:bow', ingot, ingot], 'result': {'id': f'rpgmod:{m}_bow', 'count': 1}})
for t in MELEE + ['bow']:
    write(f'{D}/rpgmod/recipe/netherite_{t}_smithing.json', {'type': 'minecraft:smithing_transform',
        'template': 'minecraft:netherite_upgrade_smithing_template', 'base': f'rpgmod:diamond_{t}',
        'addition': 'minecraft:netherite_ingot', 'result': {'id': f'rpgmod:netherite_{t}'}})

# ---- enchantability tags
melee = [f'rpgmod:{m}_{t}' for t in MELEE for m in MATS]
bows = [f'rpgmod:{m}_bow' for m in MATS]
for tag in ['sharp_weapon', 'weapon', 'melee_weapon', 'fire_aspect']:
    write(f'{D}/minecraft/tags/item/enchantable/{tag}.json', {'replace': False, 'values': melee})
write(f'{D}/minecraft/tags/item/enchantable/durability.json', {'replace': False, 'values': melee + bows})
write(f'{D}/minecraft/tags/item/enchantable/bow.json', {'replace': False, 'values': bows})
write(f'{D}/minecraft/tags/item/enchantable/vanishing.json', {'replace': False, 'values': melee + bows})

# ---- lang
JA_M = {'copper': '銅', 'iron': '鉄', 'gold': '金', 'diamond': 'ダイヤモンド', 'netherite': 'ネザライト'}
EN_M = {'copper': 'Copper', 'iron': 'Iron', 'gold': 'Golden', 'diamond': 'Diamond', 'netherite': 'Netherite'}
JA_T = {'sword': '剣', 'dagger': '短剣', 'mace': 'メイス', 'hammer': 'ハンマー', 'staff': '魔法の杖', 'bow': '弓'}
EN_T = {'sword': 'Sword', 'dagger': 'Dagger', 'mace': 'Mace', 'hammer': 'Hammer', 'staff': 'Magic Staff', 'bow': 'Bow'}
JA_ARMOR = {'helmet': 'ヘルメット', 'chestplate': 'チェストプレート', 'leggings': 'レギンス', 'boots': 'ブーツ'}
EN_ARMOR = {'helmet': 'Helmet', 'chestplate': 'Chestplate', 'leggings': 'Leggings', 'boots': 'Boots'}
PARTS_JA = {'sword': ['切先', '前部', '中央', '後部', '根元'], 'dagger': ['切先', '刃', '根元'],
            'mace': ['先端', '頭部', '首', '柄上', '柄下'], 'hammer': ['打面', '頭部', '首', '柄'],
            'staff': ['杖頭', '飾り', '柄上', '柄中', '柄下'], 'bow': ['上弭', '上リム', '握り', '下リム', '下弭'],
            'helmet': ['左頬', '左側面', '頭頂', '右側面', '右頬'], 'chestplate': ['左肩', '左胸', '胸中央', '右胸', '右肩'],
            'leggings': ['左裾', '左腿', '腰', '右腿', '右裾'], 'boots': ['左つま先', '左かかと', '右かかと', '右つま先']}
PARTS_EN = {'sword': ['Tip', 'Fore', 'Middle', 'Rear', 'Base'], 'dagger': ['Tip', 'Blade', 'Base'],
            'mace': ['Tip', 'Head', 'Neck', 'Upper shaft', 'Lower shaft'], 'hammer': ['Face', 'Head', 'Neck', 'Handle'],
            'staff': ['Head', 'Ornament', 'Upper shaft', 'Middle shaft', 'Lower shaft'], 'bow': ['Upper tip', 'Upper limb', 'Grip', 'Lower limb', 'Lower tip'],
            'helmet': ['Left cheek', 'Left side', 'Crown', 'Right side', 'Right cheek'], 'chestplate': ['Left shoulder', 'Left chest', 'Centre', 'Right chest', 'Right shoulder'],
            'leggings': ['Left hem', 'Left thigh', 'Waist', 'Right thigh', 'Right hem'], 'boots': ['Left toe', 'Left heel', 'Right heel', 'Right toe']}
JA_MAT_SHORT = {'copper': '銅', 'iron': '鉄', 'gold': '金', 'diamond': 'ダイヤ'}
EN_MAT_SHORT = {'copper': 'Copper', 'iron': 'Iron', 'gold': 'Gold', 'diamond': 'Diamond'}
for lang, M, T, P, MS, extra in (
        ('ja_jp', JA_M, {**JA_T, **JA_ARMOR}, PARTS_JA, JA_MAT_SHORT, {'rpgmod.gui.armor_row': '← 防具', 'rpgmod.gui.difficulty': '難易度 ×%s',
            'rpgmod.option.armor_toughness': '防具強度', 'rpgmod.option.damage_reduction': '被ダメージ軽減', 'rpgmod.option.boss_resistance': 'ボス耐性','rpgmod.gui.type': '武器の種類', 'rpgmod.gui.material': '素材',
            'rpgmod.gui.power_label': '威力', 'rpgmod.gui.adjacent_label': '隣接', 'rpgmod.gui.strikes_label': '打撃 %s',
            'rpgmod.tooltip.bow_bonus': '矢の攻撃力 +%s（普通の弓より）'}),
        ('en_us', EN_M, {**EN_T, **EN_ARMOR}, PARTS_EN, EN_MAT_SHORT, {'rpgmod.gui.armor_row': '< Armor', 'rpgmod.gui.difficulty': 'Difficulty x%s',
            'rpgmod.option.armor_toughness': 'Armor Toughness', 'rpgmod.option.damage_reduction': 'Damage Reduction', 'rpgmod.option.boss_resistance': 'Boss Resistance','rpgmod.gui.type': 'Weapon', 'rpgmod.gui.material': 'Material',
            'rpgmod.gui.power_label': 'Power', 'rpgmod.gui.adjacent_label': 'Sides', 'rpgmod.gui.strikes_label': 'Hits %s',
            'rpgmod.tooltip.bow_bonus': 'Arrow damage +%s (vs. a normal bow)'})):
    p = f'{A}/lang/{lang}.json'
    d = json.load(open(p, encoding='utf-8'))
    for k in [k for k in d if k.startswith('rpgmod.part.')]: del d[k]
    for m in MATS:
        for t in MELEE + ['bow']:
            d[f'item.rpgmod.{m}_{t}'] = (f'{M[m]}の{T[t]}' if lang == 'ja_jp' else f'{M[m]} {T[t]}')
    for t, names in P.items():
        for i, n in enumerate(names): d[f'rpgmod.part.{t}.{i}'] = n
    for t in T: d[f'rpgmod.type.{t}'] = T[t]
    for m, n in MS.items(): d[f'rpgmod.material.{m}'] = n
    d.update(extra)
    write(p, d)
print('ok')
