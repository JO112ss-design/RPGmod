package com.jo112ss.rpgmod.job;

import com.jo112ss.rpgmod.RpgMod;
import com.jo112ss.rpgmod.job.fx.ServerFx;
import com.jo112ss.rpgmod.registry.ModEffects;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * What the advanced jobs' lasting effects do in a fight: exposed targets, the berserker's asura, the
 * guardian's iron wall / cover / reprisal / undying, the joker's vanish, the nightblade's twin venom
 * and yaksha, the sharpshooter's empowered shot, the bishop's sanctum and the runemaster's feather.
 */
@EventBusSubscriber(modid = RpgMod.MODID)
public final class AdvancedHooks {
    private AdvancedHooks() {}

    /** Reprisal: the counter damage set when the guardian used it. */
    static final Map<UUID, Float> REPRISAL_POWER = new HashMap<>();

    private static final ResourceKey<DamageType> SKILL = ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath("rpgmod", "skill"));
    private static final ResourceKey<DamageType> SKILL_MAGIC = ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath("rpgmod", "skill_magic"));

    /** Sharpshooter's roll: the next bow arrow carries this tag and hits this much harder. */
    static final String EMPOWERED_TAG = "rpgmod_empowered";
    public static final double EMPOWERED_MULT = 2.5;
    /** Exposed: +30% damage taken per level. */
    public static final double EXPOSED_PER_LEVEL = 0.3;
    /** Asura: up to +150% damage at (almost) no health; 15% of the damage dealt heals. */
    public static final double ASURA_MAX = 1.5, ASURA_LIFESTEAL = 0.15;
    /** Cover: share of an ally's damage the guardian takes instead. */
    public static final double COVER_SHARE = 0.7, COVER_RANGE = 10;

    @SubscribeEvent
    public static void onIncoming(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level)) return;
        DamageSource src = event.getSource();
        float amount = event.getAmount();
        if (amount <= 0) return;

        boolean venom = src.is(Venom.DAMAGE);
        // poison ticks leave no hit cooldown behind (melee keeps landing in full)
        if (venom) event.getContainer().setPostAttackInvulnerabilityTicks(0);

        // ---- the attacker's side (not for poison ticks)
        if (!venom && src.getEntity() instanceof ServerPlayer sp && sp != victim) {
            if (sp.hasEffect(ModEffects.ASURA)) {
                float missing = 1 - sp.getHealth() / Math.max(1, sp.getMaxHealth());
                amount *= (float) (1 + ASURA_MAX * Math.max(0, missing));
            }
            if (sp.hasEffect(ModEffects.VANISH) && !(victim instanceof Player)) {
                // striking from nowhere: doubled, and you are seen again
                amount *= 2;
                sp.removeEffect(ModEffects.VANISH);
                sp.removeEffect(MobEffects.INVISIBILITY);
                ServerFx.of("rpg_crit", victim.position().add(0, victim.getBbHeight() * 0.6, 0)).vec(victim.position().subtract(sp.position())).a(victim.getBbWidth()).send(level);
            }
            boolean melee = src.getDirectEntity() == sp && src.is(DamageTypes.PLAYER_ATTACK);
            if (melee && sp.hasEffect(ModEffects.TWIN_VENOM)) {
                Venom.apply(victim, 100, 1, sp);
                victim.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0), sp);
                ServerFx.of("coat_hit", victim.position().add(0, victim.getBbHeight() * 0.5, 0)).b(2).send(level);
            }
        }

        if (src.getDirectEntity() instanceof AbstractArrow arrow && arrow.entityTags().contains(EMPOWERED_TAG)) amount *= (float) EMPOWERED_MULT;

        // ---- the victim's side
        MobEffectInstance exposed = victim.getEffect(ModEffects.EXPOSED);
        if (exposed != null) amount *= (float) (1 + EXPOSED_PER_LEVEL * (exposed.getAmplifier() + 1));
        if (victim instanceof ServerPlayer vp) {
            if (vp.hasEffect(ModEffects.IRON_WALL)) amount *= 0.5F;
            if (vp.hasEffect(ModEffects.SANCTUM)) amount *= 0.7F;
            amount = cover(vp, level, src, amount);
            reprisal(vp, level, src, amount);
        }
        event.setAmount(amount);
    }

    /** A guardian with cover nearby takes most of an ally's damage. */
    private static float cover(ServerPlayer vp, ServerLevel level, DamageSource src, float amount) {
        if (vp.hasEffect(ModEffects.COVER) || src.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || src.is(DamageTypes.FALL)
                || src.is(DamageTypes.STARVE) || src.is(DamageTypes.DROWN) || src.is(DamageTypes.IN_WALL)) return amount;
        ServerPlayer guard = null;
        double best = COVER_RANGE * COVER_RANGE;
        for (ServerPlayer g : level.getEntitiesOfClass(ServerPlayer.class, vp.getBoundingBox().inflate(COVER_RANGE),
                g -> g != vp && g.isAlive() && !g.isSpectator() && g.hasEffect(ModEffects.COVER) && !com.jo112ss.rpgmod.compat.Downed.is(g))) {
            double d = g.distanceToSqr(vp);
            if (d < best) {
                best = d;
                guard = g;
            }
        }
        if (guard == null) return amount;
        float moved = (float) (amount * COVER_SHARE);
        ServerPlayer g = guard;
        SkillTasks.later(1, () -> {
            if (g.isAlive()) g.hurtServer(level, g.damageSources().source(SKILL), moved);
        });
        ServerFx.of("x_link", g.position().add(0, 1.1, 0)).vec(vp.position().add(0, 1.1, 0)).color(0xF0D080).send(level);
        level.playSound(null, g.blockPosition(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.PLAYERS, 0.6F, 1.3F);
        return amount - moved;
    }

    /** Reprisal: a monster that hits the guardian in melee gets hurt back. */
    private static void reprisal(ServerPlayer vp, ServerLevel level, DamageSource src, float amount) {
        if (!vp.hasEffect(ModEffects.REPRISAL) || !(src.getEntity() instanceof LivingEntity att) || att instanceof Player) return;
        if (src.getDirectEntity() != att || att.distanceTo(vp) > 6) return;
        float back = REPRISAL_POWER.getOrDefault(vp.getUUID(), 0F) + amount / 3F;
        SkillTasks.later(1, () -> {
            if (att.isAlive()) att.hurtServer(level, vp.damageSources().source(SKILL_MAGIC, vp, vp), back);
        });
        ServerFx.of("x_hit", att.position().add(0, att.getBbHeight() * 0.5, 0)).vec(att.position().subtract(vp.position())).a(1.2).color(0xFF9040).send(level);
        level.playSound(null, vp.blockPosition(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.PLAYERS, 0.8F, 0.6F);
    }

    /**
     * Yaksha: every melee hit lands again a moment later (its own crit roll). Low, not lowest: the
     * repeat must copy the hit before the bosses' toughness share is taken off (that comes last).
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDealt(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer sp) || event.getEntity() == sp || event.getAmount() <= 0) return;
        if (!(sp.level() instanceof ServerLevel level)) return;
        LivingEntity victim = event.getEntity();
        if (sp.hasEffect(ModEffects.YAKSHA) && event.getSource().getDirectEntity() == sp && event.getSource().is(DamageTypes.PLAYER_ATTACK)) {
            float again = event.getAmount();
            SkillTasks.later(4, () -> {
                if (!victim.isAlive()) return;
                victim.hurtServer(level, sp.damageSources().source(SKILL, sp, sp), again);
                ServerFx.of("x_slash", victim.position().add(0, victim.getBbHeight() * 0.6, 0)).vec(victim.position().subtract(sp.position())).a(1.6).b(-0.8).color(0x6070FF).send(level);
                level.playSound(null, victim.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.7F, 1.6F);
            });
        }
    }

    @SubscribeEvent
    public static void onDamaged(LivingDamageEvent.Post event) {
        if (event.getSource().getEntity() instanceof ServerPlayer sp && sp != event.getEntity() && sp.hasEffect(ModEffects.ASURA) && !event.getSource().is(Venom.DAMAGE)) {
            float heal = (float) (event.getHealthDamage() * ASURA_LIFESTEAL);
            if (heal > 0) com.jo112ss.rpgmod.job.HealthScale.heal(sp, heal);
        }
    }

    /** Undying: health does not go below 1 while it lasts. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLethal(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !p.hasEffect(ModEffects.UNDYING)) return;
        float room = Math.max(0, p.getHealth() + p.getAbsorptionAmount() - 1);
        if (event.getNewDamage() > room) event.setNewDamage(room);
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (event.getEntity().hasEffect(ModEffects.FEATHER)) event.setDamageMultiplier(0);
    }

    /** Empowered shot: the next arrow from a bow hits much harder. */
    @SubscribeEvent
    public static void onArrow(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof AbstractArrow arrow) || arrow.getWeaponItem() == null) return;
        if (!(arrow.getOwner() instanceof ServerPlayer sp) || !sp.hasEffect(ModEffects.EMPOWERED_SHOT)) return;
        sp.removeEffect(ModEffects.EMPOWERED_SHOT);
        arrow.addTag(EMPOWERED_TAG); // the hit is multiplied in onIncoming
        arrow.setCritArrow(true);
        if (event.getLevel() instanceof ServerLevel level) {
            ServerFx.of("arrow_trail", arrow.position()).follow(arrow).color(0xFFE060).send(level);
            level.playSound(null, sp.blockPosition(), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 0.8F, 0.7F);
        }
    }

    /** Vanish ends when it runs out (the effect itself is removed by the hit that breaks it). */
    public static boolean hidden(Player p) {
        return p.hasEffect(ModEffects.VANISH);
    }
}
