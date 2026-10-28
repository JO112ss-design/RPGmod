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
 * natural regeneration, healing potions and other vanilla healing, and damage with
 * nobody behind it (falling, lava, fire, drowning, starving, poison, wither, cactus...). The mod's own
 * healing (skills, gems, its potions) is already sized and goes through {@link #heal} unscaled. The
 * regeneration effect gives a fixed share of max health per heal instead ({@link #regenHeal}).
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

    /**
     * The regeneration effect: each of its heals (vanilla: 1 health) is this share of max health -
     * 2%, so a potion of regeneration gives 36% (not 90% as scaled by max health / 20 it did).
     */
    public static final double REGEN_HEAL = 0.02;
    /** Regeneration from a skill (or the phoenix gem) heals as before: 5% each time. */
    public static final double SKILL_REGEN_HEAL = 0.05;
    /** Blessing: 14s instead of 8s for the same healing as before, so less each time. */
    public static final double BLESSING_REGEN_HEAL = SKILL_REGEN_HEAL * 8 / 14;

    /** The skill being used on this thread (set by SkillExecutor, the skill tasks, the phoenix gem). */
    public static final ThreadLocal<String> SKILL = new ThreadLocal<>();
    /** Set while the regeneration effect heals (RegenerationMixin). */
    public static final ThreadLocal<Boolean> REGEN_TICK = ThreadLocal.withInitial(() -> false);
    /** Players with a skill's regeneration: until when, and how much each heal gives. */
    private static final java.util.Map<java.util.UUID, double[]> SKILL_REGEN = new java.util.HashMap<>();

    @SubscribeEvent
    public static void onEffect(net.neoforged.neoforge.event.entity.living.MobEffectEvent.Added event) {
        if (!(event.getEntity() instanceof Player p) || p.level().isClientSide()) return;
        var inst = event.getEffectInstance();
        if (inst == null || inst.getEffect().value() != net.minecraft.world.effect.MobEffects.REGENERATION.value()) return;
        String skill = SKILL.get();
        if (skill == null) { // a potion, a golden apple...: a stronger one from a skill still running is replaced
            SKILL_REGEN.remove(p.getUUID());
            return;
        }
        double each = "blessing".equals(skill) ? BLESSING_REGEN_HEAL : SKILL_REGEN_HEAL;
        SKILL_REGEN.put(p.getUUID(), new double[] {p.level().getGameTime() + inst.getDuration(), each});
    }

    /** Share of max health one heal of the regeneration effect gives this player now. */
    public static double regenHeal(Player p) {
        double[] s = SKILL_REGEN.get(p.getUUID());
        if (s != null && p.level().getGameTime() <= s[0]) return s[1];
        if (s != null) SKILL_REGEN.remove(p.getUUID());
        return REGEN_HEAL;
    }

    @SubscribeEvent
    public static void onHeal(LivingHealEvent event) {
        if (!(event.getEntity() instanceof Player p) || DIRECT.get()) return;
        if (REGEN_TICK.get()) {
            event.setAmount((float) (event.getAmount() * p.getMaxHealth() * regenHeal(p)));
            return;
        }
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
