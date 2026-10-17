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
 *   reduction = defence / (defence + K)          K = 40 for players, 80 for monsters
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
        return e.getArmorValue() * 2.0 + e.getAttributeValue(Attributes.ARMOR_TOUGHNESS) * 2.0;
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
        // replaces the vanilla armour reduction: the container still runs vanilla's (for armour wear),
        // then this decides how much is actually taken off
        event.getContainer().addModifier(DamageContainer.Reduction.ARMOR, (c, vanilla) -> (float) (c.getNewDamage() * reduction(victim)));
    }
}
