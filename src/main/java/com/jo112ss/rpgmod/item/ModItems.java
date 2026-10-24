package com.jo112ss.rpgmod.item;

import com.jo112ss.rpgmod.registry.ModRegistries;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.registries.DeferredItem;

/** Registers every material variant of the mod's weapons. Swords are the vanilla items. */
public final class ModItems {
    private ModItems() {}

    private static final Map<WeaponType, Map<WeaponMaterial, DeferredItem<? extends Item>>> ITEMS = new EnumMap<>(WeaponType.class);

    public static void init() {
        for (WeaponType type : WeaponType.values()) {
            if (type.isVanilla()) continue;
            Map<WeaponMaterial, DeferredItem<? extends Item>> byMat = new EnumMap<>(WeaponMaterial.class);
            for (WeaponMaterial mat : WeaponMaterial.values()) {
                if (type == WeaponType.SHIELD && mat == WeaponMaterial.GOLD) continue; // no golden shield
                String name = mat.id + "_" + type.id;
                DeferredItem<? extends Item> item;
                if (type == WeaponType.SHIELD) {
                    item = ModRegistries.ITEMS.registerItem(name, Item::new, p -> Shields.properties(p, mat));
                } else if (type.isBow()) {
                    item = ModRegistries.ITEMS.registerItem(name, p -> new RpgBowItem(mat, p), p -> {
                        p = p.durability(mat.bowDurability).enchantable(Math.max(1, mat.tool.enchantmentValue() / 10)).repairable(mat.tool.repairItems());
                        return mat == WeaponMaterial.NETHERITE ? p.fireResistant() : p;
                    });
                } else {
                    item = ModRegistries.ITEMS.registerItem(name, type == WeaponType.STAFF ? RpgStaffItem::new : Item::new, p -> {
                        // the material's own steps (see WeaponMaterial.meleeBonus) instead of vanilla's tool bonus
                        p = p.sword(mat.tool, type.attackDamage + mat.meleeBonus() - mat.tool.attackDamageBonus(), type.attackSpeed);
                        return mat == WeaponMaterial.NETHERITE ? p.fireResistant() : p;
                    });
                }
                byMat.put(mat, item);
            }
            ITEMS.put(type, byMat);
        }
    }

    /** The item for a type and material (vanilla items for swords and armor). */
    public static Item get(WeaponType type, WeaponMaterial mat) {
        return switch (type) {
            case SWORD -> pick(mat, Items.COPPER_SWORD, Items.IRON_SWORD, Items.GOLDEN_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD);
            case HELMET -> pick(mat, Items.COPPER_HELMET, Items.IRON_HELMET, Items.GOLDEN_HELMET, Items.DIAMOND_HELMET, Items.NETHERITE_HELMET);
            case CHESTPLATE -> pick(mat, Items.COPPER_CHESTPLATE, Items.IRON_CHESTPLATE, Items.GOLDEN_CHESTPLATE, Items.DIAMOND_CHESTPLATE, Items.NETHERITE_CHESTPLATE);
            case LEGGINGS -> pick(mat, Items.COPPER_LEGGINGS, Items.IRON_LEGGINGS, Items.GOLDEN_LEGGINGS, Items.DIAMOND_LEGGINGS, Items.NETHERITE_LEGGINGS);
            case BOOTS -> pick(mat, Items.COPPER_BOOTS, Items.IRON_BOOTS, Items.GOLDEN_BOOTS, Items.DIAMOND_BOOTS, Items.NETHERITE_BOOTS);
            case ARROW -> Items.ARROW;
            case SHIELD -> mat == WeaponMaterial.GOLD ? Items.SHIELD : ITEMS.get(type).get(mat).get();
            default -> ITEMS.get(type).get(mat).get();
        };
    }

    private static Item pick(WeaponMaterial mat, Item copper, Item iron, Item gold, Item diamond, Item netherite) {
        return switch (mat) {
            case COPPER -> copper;
            case IRON -> iron;
            case GOLD -> gold;
            case DIAMOND -> diamond;
            case NETHERITE -> netherite;
        };
    }

    private static Map<Item, WeaponType> reverse;

    /** Weapon type of an item (any material), or null if it is not one of the RPG weapon types. */
    public static WeaponType typeOf(Item item) {
        if (reverse == null) {
            Map<Item, WeaponType> m = new java.util.HashMap<>();
            for (WeaponType t : WeaponType.values()) {
                if (t.isArmor() || t == WeaponType.ARROW) continue; // arrows are ammunition, not a weapon
                for (WeaponMaterial mat : WeaponMaterial.values()) m.put(get(t, mat), t);
            }
            reverse = m;
        }
        return reverse.get(item);
    }

    /** Material of one of the RPG weapons, or null. */
    public static WeaponMaterial materialOf(Item item) {
        WeaponType t = typeOf(item);
        if (t == null || item == Items.SHIELD) return null;
        for (WeaponMaterial m : WeaponMaterial.values()) if (get(t, m) == item) return m;
        return null;
    }
}
