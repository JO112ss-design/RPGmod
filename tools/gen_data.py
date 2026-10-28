"""Generates item models, recipes, tags and lang entries for the RPG weapons."""
import json, os
R = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources')
A = os.path.join(R, 'assets', 'rpgmod'); D = os.path.join(R, 'data')

def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f: json.dump(obj, f, ensure_ascii=False, indent=2); f.write('\n')

MATS = ['copper', 'iron', 'gold', 'diamond', 'netherite']
INGOT = {'copper': 'minecraft:copper_ingot', 'iron': 'minecraft:iron_ingot', 'gold': 'minecraft:gold_ingot', 'diamond': 'minecraft:diamond'}
MELEE = ['dagger', 'mace', 'hammer', 'staff', 'gunblade', 'spear', 'knuckle', 'lute']
# the casters' cloth armour (see RobeArmor)
ROBES = ['robe_hood', 'robe', 'robe_pants', 'robe_boots']

# ---- models
for m in MATS:
    for t in MELEE:
        n = f'{m}_{t}'
        if t == 'spear':
            # the vanilla spear models (flat in the inventory, the long spear in hand)
            v = 'minecraft:item/' + {'gold': 'golden'}.get(m, m) + '_spear'
            write(f'{A}/items/{n}.json', {'model': {
                'type': 'minecraft:select', 'property': 'minecraft:display_context',
                'cases': [{'when': ['gui', 'ground', 'fixed', 'on_shelf'], 'model': {'type': 'minecraft:model', 'model': v}}],
                'fallback': {'type': 'minecraft:model', 'model': v + '_in_hand'}},
                'swap_animation_scale': 1.95})
            continue
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

# ---- robes: flat icons, and the worn look (equipment asset, both layers from one texture each)
for m in MATS:
    for t in ROBES:
        n = f'{m}_{t}'
        write(f'{A}/models/item/{n}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'rpgmod:item/{n}'}})
        write(f'{A}/items/{n}.json', {'model': {'type': 'minecraft:model', 'model': f'rpgmod:item/{n}'}})
    write(f'{A}/equipment/robe_{m}.json', {'layers': {'humanoid': [{'texture': f'rpgmod:robe_{m}'}],
                                                      'humanoid_leggings': [{'texture': f'rpgmod:robe_{m}'}]}})

# ---- shields: the vanilla shield model, coloured by the item's base colour
SHIELD_MATS = ['copper', 'iron', 'diamond', 'netherite']
for m in SHIELD_MATS:
    n = f'{m}_shield'
    write(f'{A}/items/{n}.json', {'model': {
        'type': 'minecraft:condition', 'property': 'minecraft:using_item',
        'on_false': {'type': 'minecraft:special', 'base': 'minecraft:item/shield', 'model': {'type': 'minecraft:shield'}},
        'on_true': {'type': 'minecraft:special', 'base': 'minecraft:item/shield_blocking', 'model': {'type': 'minecraft:shield'}}}})

# ---- recipes (normal crafting = plain weapons without forging options)
PATTERNS = {
    'dagger': (['I', 'S'], {'I': None, 'S': 'minecraft:stick'}),
    'mace': ([' I ', 'III', ' S '], {'I': None, 'S': 'minecraft:stick'}),
    'hammer': (['III', 'ISI', ' S '], {'I': None, 'S': 'minecraft:stick'}),
    'staff': (['A', 'I', 'S'], {'A': 'minecraft:glass', 'I': None, 'S': 'minecraft:stick'}),
    'gunblade': ([' II', 'GI ', 'S  '], {'I': None, 'G': 'minecraft:gunpowder', 'S': 'minecraft:stick'}),
    'spear': (['  I', ' S ', 'S  '], {'I': None, 'S': 'minecraft:stick'}),
    'knuckle': (['ILI'], {'I': None, 'L': 'minecraft:leather'}),
    'lute': ([' I ', 'SIS', 'PPP'], {'I': None, 'S': 'minecraft:string', 'P': '#minecraft:planks'}),
}
for m, ingot in INGOT.items():
    for t, (pat, key) in PATTERNS.items():
        k = {c: (ingot if v is None else v) for c, v in key.items()}
        write(f'{D}/rpgmod/recipe/{m}_{t}.json', {'type': 'minecraft:crafting_shaped', 'category': 'equipment',
                                                   'pattern': pat, 'key': k, 'result': {'id': f'rpgmod:{m}_{t}', 'count': 1}})
    write(f'{D}/rpgmod/recipe/{m}_bow.json', {'type': 'minecraft:crafting_shapeless', 'category': 'equipment',
                                               'ingredients': ['minecraft:bow', ingot, ingot], 'result': {'id': f'rpgmod:{m}_bow', 'count': 1}})
ROBE_PATTERNS = {
    'robe_hood': ['WIW', 'W W'],
    'robe': ['W W', 'WIW', 'WIW'],
    'robe_pants': ['WIW', 'W W', 'W W'],
    'robe_boots': ['W W', 'I I'],
}
for m, ingot in INGOT.items():
    for t, pat in ROBE_PATTERNS.items():
        write(f'{D}/rpgmod/recipe/{m}_{t}.json', {'type': 'minecraft:crafting_shaped', 'category': 'equipment',
                                                   'pattern': pat, 'key': {'W': '#minecraft:wool', 'I': ingot}, 'result': {'id': f'rpgmod:{m}_{t}', 'count': 1}})
for m, ingot in INGOT.items():
    if m in SHIELD_MATS:
        write(f'{D}/rpgmod/recipe/{m}_shield.json', {'type': 'minecraft:crafting_shapeless', 'category': 'equipment',
                                                     'ingredients': ['minecraft:shield', ingot, ingot, ingot], 'result': {'id': f'rpgmod:{m}_shield', 'count': 1}})
for t in MELEE + ['bow', 'shield'] + ROBES:
    write(f'{D}/rpgmod/recipe/netherite_{t}_smithing.json', {'type': 'minecraft:smithing_transform',
        'template': 'minecraft:netherite_upgrade_smithing_template', 'base': f'rpgmod:diamond_{t}',
        'addition': 'minecraft:netherite_ingot', 'result': {'id': f'rpgmod:netherite_{t}'}})

# ---- enchantability tags
melee = [f'rpgmod:{m}_{t}' for t in MELEE for m in MATS]
bows = [f'rpgmod:{m}_bow' for m in MATS]
for tag in ['sharp_weapon', 'weapon', 'melee_weapon', 'fire_aspect']:
    write(f'{D}/minecraft/tags/item/enchantable/{tag}.json', {'replace': False, 'values': melee})
shields = [f'rpgmod:{m}_shield' for m in SHIELD_MATS]
write(f'{D}/minecraft/tags/item/enchantable/durability.json', {'replace': False, 'values': melee + bows + shields})
write(f'{D}/minecraft/tags/item/enchantable/bow.json', {'replace': False, 'values': bows})
spears = [f'rpgmod:{m}_spear' for m in MATS]
write(f'{D}/minecraft/tags/item/spears.json', {'replace': False, 'values': spears})
write(f'{D}/minecraft/tags/item/enchantable/lunge.json', {'replace': False, 'values': spears})
robes = [f'rpgmod:{m}_{t}' for t in ROBES for m in MATS]
write(f'{D}/minecraft/tags/item/enchantable/vanishing.json', {'replace': False, 'values': melee + bows + shields + robes})
write(f'{D}/minecraft/tags/item/enchantable/armor.json', {'replace': False, 'values': robes})
write(f'{D}/minecraft/tags/item/enchantable/equippable.json', {'replace': False, 'values': robes})
for t, tag in (('robe_hood', 'head_armor'), ('robe', 'chest_armor'), ('robe_pants', 'leg_armor'), ('robe_boots', 'foot_armor')):
    v = [f'rpgmod:{m}_{t}' for m in MATS]
    write(f'{D}/minecraft/tags/item/enchantable/{tag}.json', {'replace': False, 'values': v})
    write(f'{D}/minecraft/tags/item/{tag}.json', {'replace': False, 'values': v})

# ---- lang
JA_M = {'copper': '銅', 'iron': '鉄', 'gold': '金', 'diamond': 'ダイヤモンド', 'netherite': 'ネザライト'}
EN_M = {'copper': 'Copper', 'iron': 'Iron', 'gold': 'Golden', 'diamond': 'Diamond', 'netherite': 'Netherite'}
JA_T = {'shield': '盾', 'sword': '剣', 'dagger': '短剣', 'mace': 'メイス', 'hammer': 'ハンマー', 'staff': '魔法の杖', 'bow': '弓', 'arrow': '矢', 'gunblade': '銃剣', 'spear': '槍', 'knuckle': 'ナックル', 'lute': 'リュート'}
EN_T = {'shield': 'Shield', 'sword': 'Sword', 'dagger': 'Dagger', 'mace': 'Mace', 'hammer': 'Hammer', 'staff': 'Magic Staff', 'bow': 'Bow', 'arrow': 'Arrows', 'gunblade': 'Gunblade', 'spear': 'Spear', 'knuckle': 'Knuckles', 'lute': 'Lute'}
JA_ARMOR = {'helmet': 'ヘルメット', 'chestplate': 'チェストプレート', 'leggings': 'レギンス', 'boots': 'ブーツ'}
EN_ARMOR = {'helmet': 'Helmet', 'chestplate': 'Chestplate', 'leggings': 'Leggings', 'boots': 'Boots'}
JA_ROBE = {'robe_hood': 'フード', 'robe': 'ローブ', 'robe_pants': 'ズボン', 'robe_boots': 'シューズ'}
EN_ROBE = {'robe_hood': 'Hood', 'robe': 'Robe', 'robe_pants': 'Trousers', 'robe_boots': 'Shoes'}
PARTS_JA = {'sword': ['切先', '前部', '中央', '後部', '根元'], 'dagger': ['切先', '刃', '根元'],
            'mace': ['先端', '頭部', '首', '柄上', '柄下'], 'hammer': ['打面', '頭部', '首', '柄'],
            'staff': ['杖頭', '飾り', '柄上', '柄中', '柄下'], 'bow': ['上弭', '上リム', '握り', '下リム', '下弭'],
            'helmet': ['左頬', '左側面', '頭頂', '右側面', '右頬'], 'chestplate': ['左肩', '左胸', '胸中央', '右胸', '右肩'],
            'leggings': ['左裾', '左腿', '腰', '右腿', '右裾'], 'boots': ['左つま先', '左かかと', '右かかと', '右つま先'], 'arrow': ['矢じり', '矢柄', '矢羽根'],
            'shield': ['左縁', '左面', '中央', '右面', '右縁'], 'gunblade': ['銃口', '銃剣', '銃身', '機関部', '銃床'],
            'spear': ['穂先', '刃', '口金', '柄上', '柄下'], 'knuckle': ['突起', '甲板', '握り'], 'lute': ['糸倉', '棹', '胴', '駒', '尾'],
            'robe_hood': ['左裾', '左側', '頭頂', '右側', '右裾'], 'robe': ['左袖', '左身頃', '胸元', '右身頃', '右袖'],
            'robe_pants': ['左裾', '左腿', '腰', '右腿', '右裾'], 'robe_boots': ['左つま先', '左かかと', '右かかと', '右つま先']}
PARTS_EN = {'sword': ['Tip', 'Fore', 'Middle', 'Rear', 'Base'], 'dagger': ['Tip', 'Blade', 'Base'],
            'mace': ['Tip', 'Head', 'Neck', 'Upper shaft', 'Lower shaft'], 'hammer': ['Face', 'Head', 'Neck', 'Handle'],
            'staff': ['Head', 'Ornament', 'Upper shaft', 'Middle shaft', 'Lower shaft'], 'bow': ['Upper tip', 'Upper limb', 'Grip', 'Lower limb', 'Lower tip'],
            'helmet': ['Left cheek', 'Left side', 'Crown', 'Right side', 'Right cheek'], 'chestplate': ['Left shoulder', 'Left chest', 'Centre', 'Right chest', 'Right shoulder'],
            'leggings': ['Left hem', 'Left thigh', 'Waist', 'Right thigh', 'Right hem'], 'boots': ['Left toe', 'Left heel', 'Right heel', 'Right toe'], 'arrow': ['Head', 'Shaft', 'Fletching'],
            'shield': ['Left rim', 'Left face', 'Boss', 'Right face', 'Right rim'], 'gunblade': ['Muzzle', 'Bayonet', 'Barrel', 'Lock', 'Stock'],
            'spear': ['Point', 'Head', 'Socket', 'Upper shaft', 'Lower shaft'], 'knuckle': ['Studs', 'Plate', 'Grip'], 'lute': ['Pegbox', 'Neck', 'Body', 'Bridge', 'Tail'],
            'robe_hood': ['Left hem', 'Left side', 'Crown', 'Right side', 'Right hem'], 'robe': ['Left sleeve', 'Left front', 'Collar', 'Right front', 'Right sleeve'],
            'robe_pants': ['Left hem', 'Left thigh', 'Waist', 'Right thigh', 'Right hem'], 'robe_boots': ['Left toe', 'Left heel', 'Right heel', 'Right toe']}
JA_MAT_SHORT = {'copper': '銅', 'iron': '鉄', 'gold': '金', 'diamond': 'ダイヤ'}
EN_MAT_SHORT = {'copper': 'Copper', 'iron': 'Iron', 'gold': 'Gold', 'diamond': 'Diamond'}
for lang, M, T, P, MS, extra in (
        ('ja_jp', JA_M, {**JA_T, **JA_ARMOR, **JA_ROBE}, PARTS_JA, JA_MAT_SHORT, {'rpgmod.gui.armor_row': '← 防具', 'rpgmod.gui.arrow_count': '品質で 4／8／16／32本', 'rpgmod.gui.difficulty': '難易度 ×%s',
            'rpgmod.option.armor_toughness': '防具強度', 'rpgmod.option.damage_reduction': '被ダメージ軽減', 'rpgmod.option.boss_resistance': 'ボス耐性','rpgmod.gui.type': '武器の種類', 'rpgmod.gui.material': '素材',
            'rpgmod.gui.power_label': '威力', 'rpgmod.gui.adjacent_label': '隣接', 'rpgmod.gui.strikes_label': '打撃 %s',
            'rpgmod.tooltip.bow_bonus': '矢の攻撃力 +%s（普通の弓より）',
            'rpgmod.tooltip.shield_block': 'ガード：ダメージの%1$s%%を防ぐ（パラディンは+20%%）'}),
        ('en_us', EN_M, {**EN_T, **EN_ARMOR, **EN_ROBE}, PARTS_EN, EN_MAT_SHORT, {'rpgmod.gui.armor_row': '< Armor', 'rpgmod.gui.arrow_count': 'Quality: 4/8/16/32 arrows', 'rpgmod.gui.difficulty': 'Difficulty x%s',
            'rpgmod.option.armor_toughness': 'Armor Toughness', 'rpgmod.option.damage_reduction': 'Damage Reduction', 'rpgmod.option.boss_resistance': 'Boss Resistance','rpgmod.gui.type': 'Weapon', 'rpgmod.gui.material': 'Material',
            'rpgmod.gui.power_label': 'Power', 'rpgmod.gui.adjacent_label': 'Sides', 'rpgmod.gui.strikes_label': 'Hits %s',
            'rpgmod.tooltip.bow_bonus': 'Arrow damage +%s (vs. a normal bow)',
            'rpgmod.tooltip.shield_block': 'Guard: blocks %1$s%% of the damage (paladins +20%%)'})):
    p = f'{A}/lang/{lang}.json'
    d = json.load(open(p, encoding='utf-8'))
    for k in [k for k in d if k.startswith('rpgmod.part.')]: del d[k]
    for m in MATS:
        for t in MELEE + ['bow'] + ROBES + (['shield'] if m in SHIELD_MATS else []):
            d[f'item.rpgmod.{m}_{t}'] = (f'{M[m]}の{T[t]}' if lang == 'ja_jp' else f'{M[m]} {T[t]}')
    for t, names in P.items():
        for i, n in enumerate(names): d[f'rpgmod.part.{t}.{i}'] = n
    for t in T: d[f'rpgmod.type.{t}'] = T[t]
    for m, n in MS.items(): d[f'rpgmod.material.{m}'] = n
    d.update(extra)
    write(p, d)
print('ok')
