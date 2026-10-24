package com.jo112ss.rpgmod.mob;

import com.jo112ss.rpgmod.RpgMod;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * RPG-style armour, replacing the vanilla formula (where armour stops working against big hits and
 * caps at 20):
 *
 * <pre>
 *   defence   = armour x 2 + toughness x 2      (job and panel armour included)
 *   reduction = defence / (defence + K)          K = 80 for players and monsters
 * </pre>
 *
 * The reduction is the same for every hit, so it can be shown as a plain percentage, keeps growing
 * with more armour and never reaches 100%. Enchantments (protection) and the resistance effect apply
 * afterwards as in vanilla; damage that ignores armour (magic spells, falling...) still ignores it.
 */
@EventBusSubscriber(modid = RpgMod.MODID)
public final class Defense {
    private Defense() {}

    public static double defence(LivingEntity e) {
        return armor(e) * 2.0 + toughness(e) * 2.0;
    }

    /** Armour without vanilla's cap of 30 (job, panel and forged armour keep counting). */
    public static double armor(LivingEntity e) {
        return uncapped(e, Attributes.ARMOR);
    }

    /** Armour toughness without vanilla's cap of 20. */
    public static double toughness(LivingEntity e) {
        return uncapped(e, Attributes.ARMOR_TOUGHNESS);
    }

    /** An attribute's value computed like vanilla but without clamping it to the attribute's range. */
    static double uncapped(LivingEntity e, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr) {
        var inst = e.getAttribute(attr);
        if (inst == null) return 0;
        double base = inst.getBaseValue();
        var mods = inst.getModifiers();
        for (var m : mods) if (m.operation() == net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE) base += m.amount();
        double result = base;
        for (var m : mods) if (m.operation() == net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_BASE) result += base * m.amount();
        for (var m : mods) if (m.operation() == net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) result *= 1 + m.amount();
        return Math.max(0, result);
    }

    /** Fraction of a hit that armour takes away (0..1). */
    public static double reduction(LivingEntity e) {
        if (!MobConfig.rpgArmor()) return 0;
        double d = defence(e);
        if (d <= 0) return 0;
        double k = e instanceof Enemy ? MobConfig.monsterArmorK() : MobConfig.playerArmorK();
        return d / (d + k);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingIncomingDamageEvent event) {
        if (!MobConfig.rpgArmor() || event.getSource().is(DamageTypeTags.BYPASSES_ARMOR)) return;
        LivingEntity victim = event.getEntity();
        // armour penetration on the attacker's forged weapon ignores that share of the armour
        double keep = 1 - com.jo112ss.rpgmod.effects.ForgeOptionEffects.armorPenetration(event.getSource());
        // replaces the vanilla armour reduction: the container still runs vanilla's (for armour wear),
        // then this decides how much is actually taken off
        event.getContainer().addModifier(DamageContainer.Reduction.ARMOR, (c, vanilla) -> (float) (c.getNewDamage() * reduction(victim) * keep));
    }
}
