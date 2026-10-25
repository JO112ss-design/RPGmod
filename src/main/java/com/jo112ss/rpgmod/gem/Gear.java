package com.jo112ss.rpgmod.gem;

import com.jo112ss.rpgmod.effects.ForgeOptionEffects;
import com.jo112ss.rpgmod.item.ModItems;
import com.jo112ss.rpgmod.item.WeaponType;
import com.jo112ss.rpgmod.smithing.ForgedData;
import com.jo112ss.rpgmod.smithing.core.OptionType;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Option values of the gear someone has on: forging options plus socketed gems. "Equipped" means
 * the weapon (the main hand, or the bow an arrow came from), the four armour pieces and a shield
 * in the off hand.
 */
public final class Gear {
    private Gear() {}

    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    /** One item's value of an option: forged option plus stat gems. */
    public static double option(ItemStack stack, OptionType type) {
        if (stack.isEmpty()) return 0;
        double sum = 0;
        ForgedData d = ForgeOptionEffects.data(stack);
        if (d != null) sum += d.get(type);
        for (Gem g : Sockets.gems(stack)) if (type.id.equals(g.effect())) sum += g.statValue();
        return sum;
    }

    /** All option totals of one item (forged + gems), for its attribute modifiers. */
    public static Map<OptionType, Double> options(ItemStack stack) {
        Map<OptionType, Double> m = new EnumMap<>(OptionType.class);
        ForgedData d = ForgeOptionEffects.data(stack);
        if (d != null) for (ForgedData.Option o : d.options()) if (o.type() != null) m.merge(o.type(), o.value(), Double::sum);
        for (Gem g : Sockets.gems(stack)) if (g.option() != null) m.merge(g.option(), g.statValue(), Double::sum);
        return m;
    }

    /** Weapon (null = main hand), armour and an off-hand shield. */
    public static List<ItemStack> equipped(LivingEntity e, ItemStack weapon) {
        List<ItemStack> list = new ArrayList<>(6);
        list.add(weapon != null ? weapon : e.getMainHandItem());
        for (EquipmentSlot s : ARMOR) list.add(e.getItemBySlot(s));
        ItemStack off = e.getOffhandItem();
        if (ModItems.typeOf(off.getItem()) == WeaponType.SHIELD) list.add(off);
        return list;
    }

    public static double total(LivingEntity e, OptionType type, ItemStack weapon) {
        double sum = 0;
        for (ItemStack s : equipped(e, weapon)) sum += option(s, type);
        return sum;
    }

    /** Special gems on the equipped gear, with how many of each. */
    public static Map<GemSpecial, Integer> specials(LivingEntity e, ItemStack weapon) {
        Map<GemSpecial, Integer> m = new EnumMap<>(GemSpecial.class);
        for (ItemStack s : equipped(e, weapon)) {
            for (Gem g : Sockets.gems(s)) {
                GemSpecial sp = g.special();
                if (sp != null) m.merge(sp, 1, Integer::sum);
            }
        }
        return m;
    }

    /** Strength of a special: rare ones add up to their cap, legendary ones count once (0 = none). */
    public static double strength(Map<GemSpecial, Integer> specials, GemSpecial s) {
        int n = specials.getOrDefault(s, 0);
        if (n <= 0) return 0;
        if (s.rarity == Gem.LEGENDARY) return s.value;
        return Math.min(s.cap, s.value * n);
    }

    public static boolean has(LivingEntity e, GemSpecial s) {
        return specials(e, null).containsKey(s);
    }
}
