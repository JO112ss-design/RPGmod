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
 * Physical jobs keep 100 MP and win it back by fighting: a normal attack that hits an enemy gives
 * MP for the time since the last gain ({@link #RATE} a second, at most {@link #CAP} ticks' worth),
 * so any weapon swung without pause gives about the same a second. Gains are at least
 * {@link #DELAY} ticks apart and only a swing charged to {@link #MIN_CHARGE} (an arrow drawn all
 * the way, any round of a pirate's gun) counts, so mashing the button gives nothing. Skills, sweeps
 * and the bard's sound wave give nothing. Their natural regeneration in combat is a third.
 */
@EventBusSubscriber(modid = RpgMod.MODID)
public final class HitMana {
    private HitMana() {}

    /** MP a second of steady normal attacks. */
    public static final double RATE = 2.0;
    /** Ticks a gain at least waits after the last one, and the most ticks one gain pays for. */
    public static final int DELAY = 10, CAP = 30;
    /** Charge a swing needs to count. */
    public static final float MIN_CHARGE = 0.8F;
    /** Natural regeneration of the physical jobs in combat (out of combat it is the usual). */
    public static final double NATURAL = 1 / 3.0;

    private static final Map<UUID, Long> LAST = new HashMap<>();

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
        if (src.getDirectEntity() == p && src.is(DamageTypes.PLAYER_ATTACK)) {
            // only the swing's own target (not the sweep, nor a skill using the melee damage type)
            Swing s = SWINGS.remove(p.getUUID());
            if (s == null || s.target() != event.getEntity().getId() || p.level().getGameTime() - s.tick() > 1) return;
            if (s.strength() < MIN_CHARGE) return;
        } else if (src.getDirectEntity() instanceof AbstractArrow arrow && arrow.getWeaponItem() != null
                && arrow.getWeaponItem().getItem() instanceof BowItem) {
            // shot from a bow (skill arrows have no weapon), drawn all the way
            if (!arrow.isCritArrow()) return;
        } else {
            return;
        }
        give(p);
    }

    /** A pirate's round hit an enemy. */
    public static void gunHit(ServerPlayer p) {
        if (physical(JobManager.data(p))) give(p);
    }

    private static void give(ServerPlayer p) {
        long now = p.level().getGameTime();
        Long last = LAST.get(p.getUUID());
        long gap = last == null ? CAP : Math.min(CAP, now - last);
        if (gap < DELAY) return;
        LAST.put(p.getUUID(), now);
        double mp = RATE * gap / 20.0;
        JobManager.restoreMana(p, mp);
        if (testLog) RpgMod.LOGGER.info("[AUTOTEST] hitmana|{}|{}|{}", p.getMainHandItem().getItem(), String.format("%.2f", mp),
                String.format("%.1f", JobManager.data(p).mana()));
    }

    public static void forget(UUID id) {
        SWINGS.remove(id);
        LAST.remove(id);
    }
}
