package com.jo112ss.rpgmod.job;

import com.jo112ss.rpgmod.RpgMod;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Players grow to hundreds of health, so vanilla amounts made for 20 are scaled by max health / 20:
 * natural regeneration, regeneration and healing potions and other vanilla healing, and damage with
 * nobody behind it (falling, lava, fire, drowning, starving, poison, wither, cactus...). The mod's own
 * healing (skills, gems, its potions) is already sized and goes through {@link #heal} unscaled.
 */
@EventBusSubscriber(modid = RpgMod.MODID)
public final class HealthScale {
    private HealthScale() {}

    private static final ThreadLocal<Boolean> DIRECT = ThreadLocal.withInitial(() -> false);

    /** Max health / 20, at least 1. */
    public static float factor(LivingEntity e) {
        return Math.max(1F, e.getMaxHealth() / 20F);
    }

    /** Heals by exactly this much (the mod's own heals: no scaling). */
    public static void heal(LivingEntity e, float amount) {
        DIRECT.set(true);
        try {
            e.heal(amount);
        } finally {
            DIRECT.set(false);
        }
    }

    @SubscribeEvent
    public static void onHeal(LivingHealEvent event) {
        if (!(event.getEntity() instanceof Player p) || DIRECT.get()) return;
        event.setAmount(event.getAmount() * factor(p));
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHurt(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        var source = event.getSource();
        // anything a creature is behind grows with its level instead; the void and /kill stay as they are
        if (source.getEntity() != null || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        event.setAmount(event.getAmount() * factor(p));
    }
}
