package com.jo112ss.rpgmod.mob;

import com.jo112ss.rpgmod.RpgMod;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Monsters do not fight each other: a spear-carrying zombie's thrust, a skeleton's stray arrow or a
 * boss's attack no longer hurt another monster (so none of them turns on the other), and no monster
 * takes another for its target. A creeper's blast still hurts whatever is near (but nobody hit by it
 * turns on anyone for it).
 */
@EventBusSubscriber(modid = RpgMod.MODID)
public final class MobTruce {
    private MobTruce() {}

    private static boolean monster(Entity e) {
        return e instanceof Mob && e instanceof Enemy && !(e instanceof Player);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onHurt(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        Entity attacker = event.getSource().getEntity();
        if (attacker != null && attacker != victim && monster(victim) && monster(attacker)) {
            if (attacker instanceof net.minecraft.world.entity.monster.Creeper && event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)) return;
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onTarget(LivingChangeTargetEvent event) {
        if (monster(event.getEntity()) && monster(event.getNewAboutToBeSetTarget()) && event.getNewAboutToBeSetTarget() != event.getEntity()) event.setCanceled(true);
    }
}
