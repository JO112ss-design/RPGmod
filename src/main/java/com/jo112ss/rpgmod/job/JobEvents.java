package com.jo112ss.rpgmod.job;

import com.jo112ss.rpgmod.RpgMod;
import com.jo112ss.rpgmod.job.core.JobStat;
import com.jo112ss.rpgmod.network.JobActionHandler;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Game events for the job system. */
@EventBusSubscriber(modid = RpgMod.MODID)
public final class JobEvents {
    private JobEvents() {}

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) JobManager.onLogin(sp);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        JobActionHandler.forget(event.getEntity().getUUID());
        JobManager.forgetCombat(event.getEntity());
        HitMana.forget(event.getEntity().getUUID());
        UltSkills.forget(event.getEntity().getUUID());
    }

    /** Attacking or being hurt puts a player "in combat" (pauses the faster out-of-combat mana regeneration). */
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public static void onCombat(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        if (event.getAmount() <= 0) return;
        if (event.getEntity() instanceof ServerPlayer victim) JobManager.markCombat(victim);
        if (event.getSource().getEntity() instanceof ServerPlayer attacker && attacker != event.getEntity()) JobManager.markCombat(attacker);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) JobManager.onRespawn(sp);
    }

    @SubscribeEvent
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            JobStats.apply(sp, JobManager.data(sp));
            JobManager.sync(sp);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer killer && !event.getEntity().level().isClientSide()) {
            JobManager.onKill(killer, event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (sp.tickCount % 20 == 0) JobManager.tickMana(sp);
        if (sp.tickCount % 2 == 0) {
            JobManager.updateAffinity(sp);
            JobStats.updatePotionScale(sp);
        }
        if (sp.tickCount % 10 == 5) com.jo112ss.rpgmod.item.Shields.tickDown(sp);
    }

    /**
     * RPG critical hit, separate from the vanilla jump crit: one roll with chance = job crit rate +
     * forged weapon crit rate, multiplier 1.5 + forged crit damage. Applies to every hit a player
     * deals (melee, arrows, skills, spells) and stacks with a vanilla crit.
     */
    /**
     * Heavy weapons: a normal hit with a mace or hammer counts for more (the whole hit, so it keeps up
     * at every level). Skills are not affected - their power is set by the skill ratios alone.
     */
    @SubscribeEvent
    public static void onHeavyHit(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        var src = event.getSource();
        if (!(src.getEntity() instanceof ServerPlayer sp) || src.getDirectEntity() != sp || event.getEntity() == sp) return;
        if (!src.is(net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK)) return;
        double mult = heavyMultiplier(sp.getMainHandItem());
        if (mult != 1) event.setAmount((float) (event.getAmount() * mult));
    }

    /** Normal-attack multiplier of the held weapon (mace / hammer), 1 for everything else. */
    public static double heavyMultiplier(net.minecraft.world.item.ItemStack held) {
        var type = com.jo112ss.rpgmod.item.ModItems.typeOf(held.getItem());
        if (type == com.jo112ss.rpgmod.item.WeaponType.MACE) return JobConfig.MACE_MULT.get();
        if (type == com.jo112ss.rpgmod.item.WeaponType.HAMMER) return JobConfig.HAMMER_MULT.get();
        return 1;
    }

    /**
     * Light weapons (dagger, knuckles): every full swing lands now (see HitWindows), twice as many as
     * when half of them fell into the monster's hit cooldown, so each normal hit counts for half.
     */
    public static double lightMultiplier(net.minecraft.world.item.ItemStack held) {
        var type = com.jo112ss.rpgmod.item.ModItems.typeOf(held.getItem());
        if (type == com.jo112ss.rpgmod.item.WeaponType.DAGGER) return JobConfig.DAGGER_MULT.get();
        if (type == com.jo112ss.rpgmod.item.WeaponType.KNUCKLE) return JobConfig.KNUCKLE_MULT.get();
        return 1;
    }

    /** After the weapon buffs (fire weapon...) are added: they come with every hit too, so they are halved with it. */
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOW)
    public static void onLightHit(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        var src = event.getSource();
        if (!(src.getEntity() instanceof ServerPlayer sp) || src.getDirectEntity() != sp || event.getEntity() == sp) return;
        if (!src.is(net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK)) return;
        double mult = lightMultiplier(DualWield.striking ? sp.getOffhandItem() : sp.getMainHandItem());
        if (mult != 1) event.setAmount((float) (event.getAmount() * mult));
    }

    /** Set while a hit that must not crit is being dealt (server thread). */
    public static boolean noCrit;

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOW)
    public static void onRpgCrit(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer sp) || event.getEntity() == sp || event.getAmount() <= 0) return;
        if (event.getSource().is(Venom.DAMAGE)) return; // poison ticks do not crit
        if (noCrit) return; // the spellblade's sword magic
        double chance = JobManager.critChance(sp, event.getSource()) / 100.0;
        if (chance <= 0 || sp.getRandom().nextDouble() >= chance) return;
        float before = event.getAmount();
        double mult = JobManager.critMultiplier(sp, event.getSource());
        event.setAmount((float) (before * mult));
        showRpgCrit(sp, event.getEntity(), event.getAmount(), mult);
        com.jo112ss.rpgmod.gem.GemEffects.onCrit(sp, event.getEntity(), event.getSource(), event.getAmount());
        if (com.jo112ss.rpgmod.devtest.AutoTestServer.ENABLED) {
            com.jo112ss.rpgmod.RpgMod.LOGGER.info("[AUTOTEST] rpg crit: {} x{} -> {}", before, mult, event.getAmount());
        }
    }

    /**
     * RPG crit feedback, deliberately unlike the vanilla crit (white stars + crit sound): a gold star
     * burst with a ring and a slash glint (client effect "rpg_crit"), a bright chime, and the damage on
     * the attacker's action bar.
     */
    private static void showRpgCrit(ServerPlayer sp, net.minecraft.world.entity.LivingEntity target, float damage, double mult) {
        var level = (net.minecraft.server.level.ServerLevel) sp.level();
        double x = target.getX(), y = target.getY() + target.getBbHeight() * 0.6, z = target.getZ();
        com.jo112ss.rpgmod.job.fx.ServerFx.of("rpg_crit", new net.minecraft.world.phys.Vec3(x, y, z)).vec(target.position().subtract(sp.position())).a(target.getBbWidth()).send(level);
        level.playSound(null, target.blockPosition(), net.minecraft.sounds.SoundEvents.AMETHYST_CLUSTER_BREAK, net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.7F);
        level.playSound(null, target.blockPosition(), net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_STRONG, net.minecraft.sounds.SoundSource.PLAYERS, 0.8F, 0.8F);
        // the number itself floats up in gold over the target (damage numbers)
        com.jo112ss.rpgmod.mob.DamageNumbers.markCrit(target);
    }

    /**
     * Attack speed does not affect bows, so for bow jobs the job-weapon bonus speeds up drawing
     * instead: 3 extra ticks of draw every 10 ticks (x1.3). Runs on both sides with the synced data.
     */
    @SubscribeEvent
    public static void onUseTick(net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Tick event) {
        if (!(event.getEntity() instanceof net.minecraft.world.entity.player.Player p)) return;
        if (com.jo112ss.rpgmod.item.ModItems.typeOf(event.getItem().getItem()) != com.jo112ss.rpgmod.item.WeaponType.BOW) return;
        if (!JobManager.canUseBow(p) || JobConfig.AFFINITY_ATTACK_SPEED.get() <= 0) return;
        if (p.tickCount % 10 < Math.round(JobConfig.AFFINITY_ATTACK_SPEED.get() * 10)) event.setDuration(event.getDuration() - 1);
    }

    /**
     * Weapon buffs from skills, on melee hits: the assassin's coating (attack down / armour break /
     * poison, chosen with sneak + the skill key) and the enchanter's fire weapon.
     */
    @SubscribeEvent
    public static void onWeaponBuffHit(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer sp)) return;
        boolean hand = event.getSource().getDirectEntity() == sp;
        // the fire weapon burns on arrows too (fire arrows); the coatings stay on blades
        boolean arrow = event.getSource().getDirectEntity() instanceof net.minecraft.world.entity.projectile.arrow.AbstractArrow;
        if (!hand && !arrow) return;
        if (event.getEntity() == sp || event.getAmount() <= 0) return;
        var target = event.getEntity();
        int coat = hand ? SkillExecutor.activeCoat(sp) : -1;
        if (coat >= 0) SkillExecutor.applyCoat(target, coat, sp);
        var fire = sp.getEffect(com.jo112ss.rpgmod.registry.ModEffects.FIRE_WEAPON);
        if (fire != null) {
            target.igniteForSeconds(3);
            event.setAmount(event.getAmount() * (1 + (fire.getAmplifier() + 1) / 100.0F)); // +percent (see SkillExecutor.FIRE_WEAPON_BONUS)
            com.jo112ss.rpgmod.job.fx.ServerFx.of("fire_weapon_hit", target.position().add(0, target.getBbHeight() * 0.5, 0))
                    .send((net.minecraft.server.level.ServerLevel) sp.level());
        }
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        JobCommands.register(event.getDispatcher());
    }
}
