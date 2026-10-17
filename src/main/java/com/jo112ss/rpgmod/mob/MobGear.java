package com.jo112ss.rpgmod.mob;

import com.jo112ss.rpgmod.item.ModItems;
import com.jo112ss.rpgmod.item.WeaponMaterial;
import com.jo112ss.rpgmod.item.WeaponType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Random equipment by level for every hostile monster but endermen and bosses: armour pieces and one
 * of the jobs' weapons, from copper up to diamond, netherite only from level 90. Monsters whose model
 * cannot show it still get its armour and attack. Nothing of it drops.
 */
public final class MobGear {
    private MobGear() {}

    private static final WeaponType[] MELEE = {WeaponType.SWORD, WeaponType.DAGGER, WeaponType.MACE, WeaponType.HAMMER, WeaponType.STAFF};
    private static final WeaponType[] ARMOR = {WeaponType.HELMET, WeaponType.CHESTPLATE, WeaponType.LEGGINGS, WeaponType.BOOTS};
    private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    /**
     * Every hostile monster except endermen (they carry blocks), the dragon and other bosses. Most
     * monsters (creepers, spiders, slimes...) cannot show it, but the armour and the weapon's attack
     * still count, just out of sight.
     */
    public static boolean wearsGear(Mob m) {
        return m instanceof net.minecraft.world.entity.monster.Enemy && !(m instanceof net.minecraft.world.entity.monster.Enderman)
                && !(m instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon) && !m.is(net.neoforged.neoforge.common.Tags.EntityTypes.BOSSES);
    }

    /** A material for this level, with some spread; netherite only from level 90. */
    static WeaponMaterial material(int lv, RandomSource r) {
        double x = r.nextDouble();
        if (lv >= 90) return x < 0.5 ? WeaponMaterial.NETHERITE : WeaponMaterial.DIAMOND;
        if (lv >= 70) return x < 0.7 ? WeaponMaterial.DIAMOND : WeaponMaterial.IRON;
        if (lv >= 45) return x < 0.4 ? WeaponMaterial.DIAMOND : x < 0.8 ? WeaponMaterial.IRON : WeaponMaterial.GOLD;
        if (lv >= 20) return x < 0.6 ? WeaponMaterial.IRON : x < 0.8 ? WeaponMaterial.COPPER : WeaponMaterial.GOLD;
        return x < 0.7 ? WeaponMaterial.COPPER : WeaponMaterial.IRON;
    }

    /** Called once, when the monster gets its level. */
    public static void equip(Mob mob, int lv) {
        if (!MobConfig.equipment() || !wearsGear(mob)) return;
        RandomSource r = mob.getRandom();
        double armorChance = Math.min(0.65, 0.25 + lv * 0.004);
        for (int i = 0; i < ARMOR_SLOTS.length; i++) {
            if (r.nextDouble() >= armorChance) continue;
            put(mob, ARMOR_SLOTS[i], new ItemStack(ModItems.get(ARMOR[i], material(lv, r))));
        }
        if (r.nextDouble() >= Math.min(0.85, 0.5 + lv * 0.004)) return;
        ItemStack held = mob.getMainHandItem();
        if (mob instanceof AbstractSkeleton && !(mob instanceof WitherSkeleton)) {
            // archers keep shooting: one of the jobs' bows
            put(mob, EquipmentSlot.MAINHAND, new ItemStack(ModItems.get(WeaponType.BOW, material(lv, r))));
            ((AbstractSkeleton) mob).reassessWeaponGoal();
            return;
        }
        // keep the ranged weapons monsters shoot with (pillagers, piglins, drowned) and the witch's hands free for potions
        if (held.getItem() instanceof net.minecraft.world.item.ProjectileWeaponItem || held.is(Items.TRIDENT)
                || mob instanceof net.minecraft.world.entity.monster.Witch) return;
        ItemStack weapon = new ItemStack(ModItems.get(MELEE[r.nextInt(MELEE.length)], material(lv, r)));
        put(mob, EquipmentSlot.MAINHAND, weapon);
        dampenWeapon(mob, weapon);
    }

    private static final net.minecraft.resources.Identifier DAMPEN = net.minecraft.resources.Identifier.fromNamespaceAndPath(com.jo112ss.rpgmod.RpgMod.MODID, "gear_weapon_share");

    /** Re-applies the weapon share to a monster loaded from disk (the setting or the weapons may have changed). */
    public static void refreshWeapon(Mob mob) {
        dampenWeapon(mob, mob.getMainHandItem());
    }

    /** Only part of a monster weapon's attack counts (monsterWeaponEffect, a quarter by default). */
    private static void dampenWeapon(Mob mob, ItemStack weapon) {
        var inst = mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        var mods = weapon.get(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS);
        if (inst == null) return;
        if (mods == null) {
            inst.removeModifier(DAMPEN);
            return;
        }
        double bonus = 0;
        for (var e : mods.modifiers()) {
            if (e.attribute().value() == net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE.value()
                    && e.modifier().operation() == net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE) bonus += e.modifier().amount();
        }
        inst.removeModifier(DAMPEN);
        double cut = -bonus * (1 - MobConfig.weaponShare());
        if (cut < 0) inst.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(DAMPEN, cut,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
    }

    private static void put(Mob mob, EquipmentSlot slot, ItemStack stack) {
        mob.setItemSlot(slot, stack);
        mob.setDropChance(slot, 0F); // monster gear never drops
    }
}
