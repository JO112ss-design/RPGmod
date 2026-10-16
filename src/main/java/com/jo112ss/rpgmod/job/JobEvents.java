package com.jo112ss.rpgmod.job;

import com.jo112ss.rpgmod.RpgMod;
import com.jo112ss.rpgmod.job.core.JobStat;
import com.jo112ss.rpgmod.network.JobActionHandler;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;
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

    /** Job critical chance (on top of forged weapon options). */
    @SubscribeEvent
    public static void onCrit(CriticalHitEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp) || event.isCriticalHit()) return;
        JobData d = JobManager.data(sp);
        if (!d.hasJob()) return;
        double chance = JobStats.compute(d).get(JobStat.CRIT_CHANCE) / 100.0;
        if (chance > 0 && sp.getRandom().nextDouble() < chance) {
            event.setCriticalHit(true);
            event.setDamageMultiplier(Math.max(event.getDamageMultiplier(), 1.5F));
        }
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

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        JobCommands.register(event.getDispatcher());
    }
}
