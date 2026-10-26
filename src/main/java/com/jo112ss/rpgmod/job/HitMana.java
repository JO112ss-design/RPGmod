package com.jo112ss.rpgmod.job;

import com.jo112ss.rpgmod.RpgMod;
import com.jo112ss.rpgmod.job.core.JobDefs;
import com.jo112ss.rpgmod.job.core.JobStat;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;

/**
 * Physical jobs keep 100 MP and win it back by fighting: every normal attack that hits an enemy
 * gives MP for the time it took, so any weapon swung without pause gives about {@link #RATE} a
 * second (a dagger a little each hit, a hammer a lot). Half-charged swings give their share; an
 * arrow gives the full amount when drawn all the way; a pirate's round gives the time between shots.
 * Skills, sweeps and the bard's sound wave give nothing.
 */
@EventBusSubscriber(modid = RpgMod.MODID)
public final class HitMana {
    private HitMana() {}

    /** MP a second of steady normal attacks. */
    public static final double RATE = 4.0;
    /** MP of a fully drawn arrow (a full draw takes a second); a weaker one gives half. */
    public static final double ARROW = 4.0;

    /** Dev test: logs every gain. */
    public static boolean testLog;

    /** Jobs without MP growth of their own (no MP in their stats), except the song jobs. */
    public static boolean physical(JobData d) {
        if (!d.hasJob() || d.songJob()) return false;
        JobDefs.Job job = d.jobDef();
        return job != null && !job.base().containsKey(JobStat.MAX_MANA) && !job.perLevel().containsKey(JobStat.MAX_MANA)
                && !job.panelStats().contains(JobStat.MAX_MANA);
    }

    /** The swing being made: its target and how charged it was (read before vanilla resets it). */
    private record Swing(int target, float strength, long tick) {}

    private static final Map<UUID, Swing> SWINGS = new HashMap<>();

    @SubscribeEvent
    public static void onSwing(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        SWINGS.put(p.getUUID(), new Swing(event.getTarget().getId(), p.getAttackStrengthScale(0.5F), p.level().getGameTime()));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHit(LivingIncomingDamageEvent event) {
        if (event.getAmount() <= 0) return;
        DamageSource src = event.getSource();
        if (!(src.getEntity() instanceof ServerPlayer p) || !SkillExecutor.isEnemy(p, event.getEntity())) return;
        if (!physical(JobManager.data(p))) return;
        double mp = 0;
        if (src.getDirectEntity() == p && src.is(DamageTypes.PLAYER_ATTACK)) {
            // only the swing's own target (not the sweep, nor a skill using the melee damage type)
            Swing s = SWINGS.remove(p.getUUID());
            if (s == null || s.target() != event.getEntity().getId() || p.level().getGameTime() - s.tick() > 1) return;
            mp = RATE * p.getCurrentItemAttackStrengthDelay() / 20.0 * s.strength();
        } else if (src.getDirectEntity() instanceof AbstractArrow arrow && arrow.getWeaponItem() != null
                && arrow.getWeaponItem().getItem() instanceof BowItem) {
            // shot from a bow (skill arrows have no weapon)
            mp = arrow.isCritArrow() ? ARROW : ARROW * 0.5;
        }
        give(p, mp);
    }

    /** A pirate's round hit an enemy. */
    public static void gunHit(ServerPlayer p) {
        if (physical(JobManager.data(p))) give(p, RATE * Pirate.SHOT_GAP / 20.0);
    }

    private static void give(ServerPlayer p, double mp) {
        if (mp <= 0) return;
        JobManager.restoreMana(p, mp);
        if (testLog) RpgMod.LOGGER.info("[AUTOTEST] hitmana|{}|{}|{}", p.getMainHandItem().getItem(), String.format("%.2f", mp),
                String.format("%.1f", JobManager.data(p).mana()));
    }

    public static void forget(UUID id) {
        SWINGS.remove(id);
    }
}
