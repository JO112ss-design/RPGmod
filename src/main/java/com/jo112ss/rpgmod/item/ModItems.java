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
                } else if (type.isRobe()) {
                    item = ModRegistries.ITEMS.registerItem(name, Item::new, p -> RobeArmor.properties(p, type, mat));
                } else if (type.isBow()) {
                    item = ModRegistries.ITEMS.registerItem(name, p -> new RpgBowItem(mat, p), p -> {
                        p = p.durability(mat.bowDurability).enchantable(Math.max(1, mat.tool.enchantmentValue() / 10)).repairable(mat.tool.repairItems());
                        return mat == WeaponMaterial.NETHERITE ? p.fireResistant() : p;
                    });
                } else {
                    java.util.function.Function<Item.Properties, ? extends Item> make = switch (type) {
                        case STAFF -> RpgStaffItem::new;
                        case GUNBLADE -> GunbladeItem::new;
                        case LUTE -> LuteItem::new;
                        default -> Item::new;
                    };
                    item = ModRegistries.ITEMS.registerItem(name, make, p -> {
                        if (type == WeaponType.SPEAR) {
                            p = spear(p, mat);
                        } else {
                            // the material's own steps (see WeaponMaterial.meleeBonus) instead of vanilla's tool bonus
                            p = p.sword(mat.tool, type.attackDamage + mat.meleeBonus() - mat.tool.attackDamageBonus(), type.attackSpeed);
                        }
                        return mat == WeaponMaterial.NETHERITE ? p.fireResistant() : p;
                    });
                }
                byMat.put(mat, item);
            }
            ITEMS.put(type, byMat);
        }
    }

    /**
     * The vanilla spear's behaviour (the jab that goes through a line of enemies, the charge while
     * holding right-click, its long reach) with the vanilla numbers of the same material. Its attack
     * is set by {@link SwordTiers} like every other weapon; the hits count as normal player attacks,
     * and enemies right in front can be hit too (vanilla spears cannot reach closer than 2 blocks).
     */
    private static Item.Properties spear(Item.Properties p, WeaponMaterial mat) {
        p = switch (mat) {
            case COPPER -> p.spear(mat.tool, 0.85F, 0.82F, 0.65F, 4.0F, 12.0F, 8.25F, 5.1F, 12.5F, 4.6F);
            case IRON -> p.spear(mat.tool, 0.95F, 0.95F, 0.6F, 2.5F, 11.0F, 6.75F, 5.1F, 11.25F, 4.6F);
            case GOLD -> p.spear(mat.tool, 0.95F, 0.7F, 0.7F, 3.5F, 13.0F, 8.5F, 5.1F, 13.75F, 4.6F);
            case DIAMOND -> p.spear(mat.tool, 1.05F, 1.075F, 0.5F, 3.0F, 10.0F, 6.5F, 5.1F, 10.0F, 4.6F);
            case NETHERITE -> p.spear(mat.tool, 1.15F, 1.2F, 0.4F, 2.5F, 9.0F, 5.5F, 5.1F, 8.75F, 4.6F);
        };
        return p.delayedHolderComponent(net.minecraft.core.component.DataComponents.DAMAGE_TYPE, net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK)
                .component(net.minecraft.core.component.DataComponents.ATTACK_RANGE,
                        new net.minecraft.world.item.component.AttackRange(0.0F, 4.5F, 0.0F, 6.5F, 0.125F, 0.5F));
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
                if (t.wearable() || t == WeaponType.ARROW) continue; // arrows are ammunition, not a weapon
                for (WeaponMaterial mat : WeaponMaterial.values()) m.put(get(t, mat), t);
            }
            reverse = m;
        }
        return reverse.get(item);
    }

    /** Robe piece type of an item, or null. */
    public static WeaponType robeOf(Item item) {
        for (WeaponType t : WeaponType.values()) {
            if (!t.isRobe()) continue;
            for (WeaponMaterial mat : WeaponMaterial.values()) if (get(t, mat) == item) return t;
        }
        return null;
    }

    /** Material of one of the RPG weapons, or null. */
    public static WeaponMaterial materialOf(Item item) {
        WeaponType t = typeOf(item);
        if (t == null || item == Items.SHIELD) return null;
        for (WeaponMaterial m : WeaponMaterial.values()) if (get(t, m) == item) return m;
        return null;
    }
}
