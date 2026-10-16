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
        if (sp.tickCount % 2 == 0) JobManager.updateAffinity(sp);
    }

    /**
     * RPG critical hit, separate from the vanilla jump crit: one roll with chance = job crit rate +
     * forged weapon crit rate, multiplier 1.5 + forged crit damage. Applies to every hit a player
     * deals (melee, arrows, skills, spells) and stacks with a vanilla crit.
     */
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOW)
    public static void onRpgCrit(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer sp) || event.getEntity() == sp || event.getAmount() <= 0) return;
        double chance = JobManager.critChance(sp, event.getSource()) / 100.0;
        if (chance <= 0 || sp.getRandom().nextDouble() >= chance) return;
        float before = event.getAmount();
        double mult = JobManager.critMultiplier(sp, event.getSource());
        event.setAmount((float) (before * mult));
        showRpgCrit(sp, event.getEntity(), event.getAmount(), mult);
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
        if (!(event.getSource().getEntity() instanceof ServerPlayer sp) || event.getSource().getDirectEntity() != sp) return;
        if (event.getEntity() == sp || event.getAmount() <= 0) return;
        var target = event.getEntity();
        int coat = SkillExecutor.activeCoat(sp);
        if (coat >= 0) SkillExecutor.applyCoat(target, coat, sp);
        var fire = sp.getEffect(com.jo112ss.rpgmod.registry.ModEffects.FIRE_WEAPON);
        if (fire != null) {
            target.igniteForSeconds(3);
            event.setAmount(event.getAmount() + fire.getAmplifier() + 1);
            com.jo112ss.rpgmod.job.fx.ServerFx.of("fire_weapon_hit", target.position().add(0, target.getBbHeight() * 0.5, 0))
                    .send((net.minecraft.server.level.ServerLevel) sp.level());
        }
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        JobCommands.register(event.getDispatcher());
    }
}
