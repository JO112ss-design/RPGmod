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
            if (type == WeaponType.SWORD) continue;
            Map<WeaponMaterial, DeferredItem<? extends Item>> byMat = new EnumMap<>(WeaponMaterial.class);
            for (WeaponMaterial mat : WeaponMaterial.values()) {
                String name = mat.id + "_" + type.id;
                DeferredItem<? extends Item> item;
                if (type.isBow()) {
                    item = ModRegistries.ITEMS.registerItem(name, p -> new RpgBowItem(mat, p), p -> {
                        p = p.durability(mat.bowDurability).enchantable(Math.max(1, mat.tool.enchantmentValue() / 10)).repairable(mat.tool.repairItems());
                        return mat == WeaponMaterial.NETHERITE ? p.fireResistant() : p;
                    });
                } else {
                    item = ModRegistries.ITEMS.registerItem(name, Item::new, p -> {
                        p = p.sword(mat.tool, type.attackDamage, type.attackSpeed);
                        return mat == WeaponMaterial.NETHERITE ? p.fireResistant() : p;
                    });
                }
                byMat.put(mat, item);
            }
            ITEMS.put(type, byMat);
        }
    }

    /** The item for a type and material (vanilla items for swords). */
    public static Item get(WeaponType type, WeaponMaterial mat) {
        if (type == WeaponType.SWORD) {
            return switch (mat) {
                case COPPER -> Items.COPPER_SWORD;
                case IRON -> Items.IRON_SWORD;
                case GOLD -> Items.GOLDEN_SWORD;
                case DIAMOND -> Items.DIAMOND_SWORD;
                case NETHERITE -> Items.NETHERITE_SWORD;
            };
        }
        return ITEMS.get(type).get(mat).get();
    }
}
