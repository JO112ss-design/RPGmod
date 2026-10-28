package com.jo112ss.rpgmod.job;

import com.jo112ss.rpgmod.RpgMod;
import com.jo112ss.rpgmod.item.ModItems;
import com.jo112ss.rpgmod.item.WeaponType;
import com.jo112ss.rpgmod.job.fx.ServerFx;
import com.jo112ss.rpgmod.registry.ModEffects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * What the pirate, lancer, monk and bard do in a fight beyond their skills: bayonet hits load the
 * gun, spear tip hits count for more, knuckle hits build ki, the parry / counter stance / diamond
 * body / untouchable step, the flag and the songs' damage changes, and the bard not attacking while
 * playing.
 */
@EventBusSubscriber(modid = RpgMod.MODID)
public final class NewJobHooks {
    private NewJobHooks() {}

    /** Spear hits from at least this far (to the target's edge) are tip hits. */
    public static final double TIP_DISTANCE = 2.5;
    public static final double TIP_BONUS = 0.25;
    public static final double FLAG_BONUS = 0.2;
    public static final double SONG_STEP = 0.03;

    static boolean melee(DamageSource src, ServerPlayer sp) {
        return src.getDirectEntity() == sp && src.is(DamageTypes.PLAYER_ATTACK);
    }

    @SubscribeEvent
    public static void onIncoming(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level)) return;
        DamageSource src = event.getSource();
        float amount = event.getAmount();
        if (amount <= 0) return;

        // ---- the attacker's side
        if (src.getEntity() instanceof ServerPlayer sp && sp != victim && !src.is(Venom.DAMAGE)) {
            if (sp.hasEffect(ModEffects.JOLLY_ROGER)) amount *= (float) (1 + FLAG_BONUS);
            MobEffectInstance war = sp.getEffect(ModEffects.WAR_SONG);
            if (war != null) amount *= (float) (1 + SONG_STEP * (war.getAmplifier() + 1));
            if (sp.hasEffect(ModEffects.DIAMOND_BODY)) Monk.diamondHit(sp);
            if (melee(src, sp) && !(victim instanceof Player)) {
                WeaponType held = ModItems.typeOf(sp.getMainHandItem().getItem());
                JobData d = JobManager.data(sp);
                if (held == WeaponType.SPEAR && d.spearJob()) {
                    double edge = sp.getEyePosition().distanceTo(victim.getBoundingBox().getCenter()) - victim.getBbWidth() / 2;
                    if (edge >= TIP_DISTANCE) {
                        amount *= (float) (1 + TIP_BONUS);
                        ServerFx.of("x_hit", victim.position().add(0, victim.getBbHeight() * 0.6, 0)).vec(victim.position().subtract(sp.position())).a(0.9).color(0xFFE0A0).send(level);
                        level.playSound(null, victim.blockPosition(), SoundEvents.SPEAR_HIT.value(), SoundSource.PLAYERS, 0.8F, 1.4F);
                    }
                } else if (held == WeaponType.KNUCKLE && d.kiJob()) {
                    Monk.hit(sp);
                }
            }
        }

        // ---- the victim's side
        if (victim instanceof ServerPlayer vp) {
            if (vp.hasEffect(ModEffects.UNTOUCHABLE) && src.getEntity() != null) {
                event.setCanceled(true);
                return;
            }
            LivingEntity att = src.getEntity() instanceof LivingEntity l && !(l instanceof Player) ? l : null;
            boolean meleeHit = att != null && src.getDirectEntity() == att && att.distanceTo(vp) < 6;
            if (meleeHit && vp.hasEffect(ModEffects.PARRY)) {
                vp.removeEffect(ModEffects.PARRY);
                float back = NewJobSkills.PARRY_POWER.getOrDefault(vp.getUUID(), 0F);
                SkillTasks.later(1, () -> {
                    if (att.isAlive()) att.hurtServer(level, vp.damageSources().source(SkillExecutor.SKILL_DAMAGE, vp, vp), back);
                    SkillExecutor.pushAway(att, vp.position(), 0.8, 0.3);
                });
                Monk.add(vp, 2);
                ServerFx.of("x_hit", att.position().add(0, att.getBbHeight() * 0.5, 0)).vec(att.position().subtract(vp.position())).a(1.4).color(0xFFE0A0).send(level);
                ServerFx.of("n_fist", vp.position().add(0, 1.1, 0).add(att.position().subtract(vp.position()).normalize().scale(0.9)))
                        .vec(att.position().subtract(vp.position())).color(0xFFE0A0).send(level);
                level.playSound(null, vp.blockPosition(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.PLAYERS, 1.0F, 1.5F);
                level.playSound(null, vp.blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0F, 1.2F);
                event.setCanceled(true);
                return;
            }
            if (vp.hasEffect(ModEffects.COUNTER_STANCE)) {
                amount *= 0.5F;
                if (meleeHit) {
                    LivingEntity a = att;
                    SkillTasks.later(1, () -> {
                        if (a.isAlive()) NewJobSkills.counter(vp, level, a);
                    });
                }
            }
            if (vp.hasEffect(ModEffects.DIAMOND_BODY)) amount *= 0.2F;
            MobEffectInstance guard = vp.getEffect(ModEffects.GUARD_SONG);
            if (guard != null) amount *= (float) Math.max(0.5, 1 - SONG_STEP * (guard.getAmplifier() + 1));
        }
        event.setAmount(amount);
    }

    /** A playing bard does not attack (the attack button plays the notes). */
    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (Bard.performing(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        Bard.tick(sp);
        if (sp.tickCount % 20 == 7) Monk.tick(sp);
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (NewJobSkills.SOFT_LANDING.contains(event.getEntity().getUUID())) event.setDamageMultiplier(0);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        var id = event.getEntity().getUUID();
        Bard.forget(id);
        Monk.forget(id);
        Pirate.forget(id);
        NewJobSkills.SOFT_LANDING.remove(id);
        NewJobSkills.COUNTER_POWER.remove(id);
        NewJobSkills.PARRY_POWER.remove(id);
    }
}
