package com.jo112ss.rpgmod.item;

import com.jo112ss.rpgmod.RpgMod;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorMaterials;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

/**
 * Robes, the casters' armour: hood, robe, trousers and shoes in the same five materials as metal
 * armour (cloth woven with the metal). Each piece has {@link #SHARE} of the armour of the metal piece
 * of the same material (vanilla + the tier bonus of {@link ArmorTiers}), at least 1, and 60% of its
 * toughness; no knockback resistance. What makes them worth wearing is the forging pool: robes lean
 * to magic options (see {@code OptionType.Side}).
 */
public final class RobeArmor {
    private RobeArmor() {}

    public static final double SHARE = 0.6;

    private static final ArmorType[] TYPES = {ArmorType.HELMET, ArmorType.CHESTPLATE, ArmorType.LEGGINGS, ArmorType.BOOTS};

    /** The armour slot of a robe piece. */
    public static ArmorType armorType(WeaponType type) {
        return switch (type) {
            case ROBE_HOOD -> ArmorType.HELMET;
            case ROBE -> ArmorType.CHESTPLATE;
            case ROBE_PANTS -> ArmorType.LEGGINGS;
            default -> ArmorType.BOOTS;
        };
    }

    private static ArmorMaterial metal(WeaponMaterial mat) {
        return switch (mat) {
            case COPPER -> ArmorMaterials.COPPER;
            case IRON -> ArmorMaterials.IRON;
            case GOLD -> ArmorMaterials.GOLD;
            case DIAMOND -> ArmorMaterials.DIAMOND;
            case NETHERITE -> ArmorMaterials.NETHERITE;
        };
    }

    /** Armour of a robe piece: 60% of the metal piece's (vanilla + tier bonus), at least 1. */
    public static int defense(WeaponMaterial mat, int piece) {
        int metal = metal(mat).defense().getOrDefault(TYPES[piece], 0) + ArmorTiers.tierBonus(mat, piece);
        return Math.max(1, (int) Math.round(metal * SHARE));
    }

    private static final Map<WeaponMaterial, ArmorMaterial> MATERIALS = new EnumMap<>(WeaponMaterial.class);

    public static ArmorMaterial material(WeaponMaterial mat) {
        return MATERIALS.computeIfAbsent(mat, m -> {
            ArmorMaterial base = metal(m);
            Map<ArmorType, Integer> def = new EnumMap<>(ArmorType.class);
            for (int i = 0; i < TYPES.length; i++) def.put(TYPES[i], defense(m, i));
            ResourceKey<EquipmentAsset> asset = ResourceKey.create(EquipmentAssets.ROOT_ID, Identifier.fromNamespaceAndPath(RpgMod.MODID, "robe_" + m.id));
            return new ArmorMaterial(base.durability(), def, base.enchantmentValue(), SoundEvents.ARMOR_EQUIP_LEATHER,
                    base.toughness() * (float) SHARE, 0, base.repairIngredient(), asset);
        });
    }

    /** Item properties of a robe piece. */
    public static Item.Properties properties(Item.Properties p, WeaponType type, WeaponMaterial mat) {
        p = p.humanoidArmor(material(mat), armorType(type));
        return mat == WeaponMaterial.NETHERITE ? p.fireResistant() : p;
    }
}
