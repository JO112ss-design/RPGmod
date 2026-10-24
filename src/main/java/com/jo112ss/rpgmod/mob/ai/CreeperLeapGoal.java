package com.jo112ss.rpgmod.mob.ai;

import com.jo112ss.rpgmod.job.fx.ServerFx;
import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/**
 * Leaping creepers (a fifth of them): when the target is up on something, behind a wall or otherwise
 * out of walking reach, the creeper jumps high and comes down on top of them - over walls and into
 * bases from above. It takes no fall damage from its own leaps.
 */
public final class CreeperLeapGoal extends Goal {
    /** Marks a leaping creeper (saved on the mob). */
    public static final String KEY = "rpgmod_leaper";
    private static final double G = 0.08;

    /** Game time of the next possible leap (saved on the mob). */
    private static final String NEXT = "rpgmod_leap_next";

    private final Creeper creeper;
    private int airborne = -1;

    public CreeperLeapGoal(Creeper creeper) {
        this.creeper = creeper;
        setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP));
    }

    public static boolean isLeaper(Creeper c) {
        return c.getPersistentData().getBooleanOr(KEY, false);
    }

    /** A leaper whose leap is ready: it would rather jump in than hiss through the wall (see SmartSwellGoal). */
    public static boolean leapReady(Creeper c) {
        return isLeaper(c) && c.level().getGameTime() >= c.getPersistentData().getLongOr(NEXT, 0L);
    }

    private LivingEntity target() {
        LivingEntity t = creeper.getTarget();
        return t instanceof Player p && !p.isCreative() && !p.isSpectator() && p.isAlive() ? p : null;
    }

    @Override
    public boolean canUse() {
        if (!leapReady(creeper)) return false;
        LivingEntity t = target();
        if (t == null || !creeper.onGround() || creeper.getSwellDir() > 0 || creeper.isInWater()) return false;
        double dx = t.getX() - creeper.getX(), dz = t.getZ() - creeper.getZ();
        double h = Math.sqrt(dx * dx + dz * dz);
        double up = t.getY() - creeper.getY();
        if (h < 1.0 || h > 16 || up < -6 || up > 12) return false;
        Path path = creeper.getNavigation().getPath();
        boolean walkable = path != null && path.canReach();
        boolean seen = creeper.getSensing().hasLineOfSight(t);
        // only when walking will not do: the player is up high, behind a wall, or there is no way there
        return up >= 2 || !seen || !walkable;
    }

    @Override
    public boolean canContinueToUse() {
        return airborne >= 0 && airborne < 60;
    }

    @Override
    public void start() {
        LivingEntity t = target();
        if (t == null || !(creeper.level() instanceof ServerLevel sl)) return;
        // a ballistic arc whose top clears both ends by 4 blocks, landing at the player's feet
        Vec3 from = creeper.position(), to = t.position();
        double apex = Math.max(from.y, to.y) + 4;
        double vy = Math.sqrt(2 * G * (apex - from.y));
        double tUp = vy / G, tDown = Math.sqrt(2 * (apex - to.y) / G);
        double flight = tUp + tDown;
        double vx = (to.x - from.x) / flight * 1.1, vz = (to.z - from.z) / flight * 1.1; // a little for air drag
        creeper.getNavigation().stop();
        creeper.setDeltaMovement(vx, vy * 1.04, vz);
        creeper.needsSync = true;
        creeper.resetFallDistance();
        airborne = 0;
        ServerFx.of("x_ring", from).a(1.8).b(1).color(0x7CFF6A).send(sl);
        sl.playSound(null, creeper.getX(), creeper.getY(), creeper.getZ(), SoundEvents.BREEZE_JUMP, SoundSource.HOSTILE, 1.0F, 0.7F);
        sl.playSound(null, creeper.getX(), creeper.getY(), creeper.getZ(), SoundEvents.SLIME_JUMP, SoundSource.HOSTILE, 1.0F, 0.6F);
        if (com.jo112ss.rpgmod.devtest.AutoTestServer.ENABLED) {
            com.jo112ss.rpgmod.RpgMod.LOGGER.info("[AUTOTEST] creeper leap: from {} to {} ({} blocks up)", from, to, String.format("%.1f", to.y - from.y));
        }
    }

    @Override
    public void stop() {
        airborne = -1;
        creeper.getPersistentData().putLong(NEXT, creeper.level().getGameTime() + 60 + creeper.getRandom().nextInt(40));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (airborne < 0) return;
        airborne++;
        creeper.resetFallDistance(); // its own leaps never hurt it
        if (airborne > 3 && creeper.onGround()) airborne = 60; // landed
    }
}
