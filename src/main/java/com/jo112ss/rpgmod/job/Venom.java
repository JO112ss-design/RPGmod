package com.jo112ss.rpgmod.job;

import com.jo112ss.rpgmod.RpgMod;
import com.jo112ss.rpgmod.registry.ModEffects;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * The assassin's and nightblade's poison. Vanilla poison does nothing to the undead (most monsters)
 * and only a point of damage now and then, so this one takes a share of the target's max health
 * every second instead: 5% (7.5% at level II). Bosses are immune. It works on the undead, can
 * kill, ignores armour, and the kill counts for the player who poisoned it.
 */
public final class Venom {
    private Venom() {}

    public static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(RpgMod.MODID, "venom"));
    /** Share of max health per second at level I; +50% per extra level. */
    public static final double PER_SECOND = 0.05;

    /** Who poisoned whom (for kill credit and experience). */
    private static final Map<LivingEntity, UUID> SOURCE = new WeakHashMap<>();

    /** Poisons {@code target} for {@code ticks} at {@code amp} (0 = level I). */
    public static void apply(LivingEntity target, int ticks, int amp, ServerPlayer by) {
        if (isBoss(target)) return; // bosses shrug poison off
        MobEffectInstance cur = target.getEffect(ModEffects.VENOM);
        if (cur != null && cur.getAmplifier() > amp) amp = cur.getAmplifier(); // a weaker coat does not cut a stronger poison short
        target.addEffect(new MobEffectInstance(ModEffects.VENOM, ticks, amp), by);
        if (by != null) SOURCE.put(target, by.getUUID());
    }

    public static boolean isBoss(LivingEntity e) {
        return e.is(net.neoforged.neoforge.common.Tags.EntityTypes.BOSSES) || e instanceof com.jo112ss.rpgmod.mob.boss.ModBoss;
    }

    /** Damage of one tick (once a second). */
    public static float tickDamage(LivingEntity e, int amp) {
        double share = PER_SECOND * (1 + 0.5 * amp);
        return (float) Math.max(1, e.getMaxHealth() * share);
    }

    public static final class Effect extends MobEffect {
        public Effect() {
            super(MobEffectCategory.HARMFUL, 0x5AC832);
        }

        @Override
        public boolean shouldApplyEffectTickThisTick(int tickCount, int amplification) {
            return tickCount % 20 == 0;
        }

        @Override
        public boolean applyEffectTick(ServerLevel level, LivingEntity mob, int amplification) {
            if (isBoss(mob)) return false;
            UUID by = SOURCE.get(mob);
            ServerPlayer player = by == null ? null : level.getServer().getPlayerList().getPlayer(by);
            DamageSource src = level.damageSources().source(DAMAGE, null, player);
            mob.hurtServer(level, src, tickDamage(mob, amplification));
            return true;
        }
    }
}
