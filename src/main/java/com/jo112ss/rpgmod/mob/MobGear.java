package com.jo112ss.rpgmod.mob;

import com.jo112ss.rpgmod.item.ModItems;
import com.jo112ss.rpgmod.item.WeaponMaterial;
import com.jo112ss.rpgmod.item.WeaponType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Random equipment by level for monsters that can wear it (zombies, skeletons, piglins and their
 * kin): armour pieces and one of the jobs' weapons, from copper up to diamond, netherite only from
 * level 90. Nothing of it drops.
 */
public final class MobGear {
    private MobGear() {}

    private static final WeaponType[] MELEE = {WeaponType.SWORD, WeaponType.DAGGER, WeaponType.MACE, WeaponType.HAMMER, WeaponType.STAFF};
    private static final WeaponType[] ARMOR = {WeaponType.HELMET, WeaponType.CHESTPLATE, WeaponType.LEGGINGS, WeaponType.BOOTS};
    private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    /** Monsters whose models show armour and held items. */
    public static boolean wearsGear(Mob m) {
        return m instanceof Zombie || m instanceof AbstractSkeleton || m instanceof AbstractPiglin;
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
        if (held.getItem() instanceof CrossbowItem || held.is(Items.TRIDENT)) return; // keep ranged piglins and trident drowned
        put(mob, EquipmentSlot.MAINHAND, new ItemStack(ModItems.get(MELEE[r.nextInt(MELEE.length)], material(lv, r))));
    }

    private static void put(Mob mob, EquipmentSlot slot, ItemStack stack) {
        mob.setItemSlot(slot, stack);
        mob.setDropChance(slot, 0F); // monster gear never drops
    }
}
