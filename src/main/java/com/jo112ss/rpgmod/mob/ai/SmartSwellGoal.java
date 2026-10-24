package com.jo112ss.rpgmod.mob.ai;

import com.jo112ss.rpgmod.mob.MobConfig;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.pathfinder.Path;
import net.neoforged.neoforge.event.EventHooks;

/**
 * Replaces the vanilla creeper fuse goal. Unlike vanilla it keeps hissing without line of sight, so a
 * thin wall no longer saves you, and a creeper that cannot path to you walks up to the wall between
 * you and blows a hole in it (breach). Explosions follow the usual mobGriefing rules.
 */
public final class SmartSwellGoal extends Goal {

    private final Creeper creeper;
    private final int level;
    private LivingEntity target;
    private int stuck;
    private boolean breaching;

    public SmartSwellGoal(Creeper creeper, int level) {
        this.creeper = creeper;
        this.level = level;
        setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    /** Through a wall the creeper reacts from a little further than vanilla's 3 blocks. */
    private double wallRange() {
        return 3.5 + Math.min(1.5, level * 0.02);
    }

    @Override
    public boolean canUse() {
        if (creeper.getSwellDir() > 0) return true;
        LivingEntity t = creeper.getTarget();
        if (t == null) {
            stuck = 0;
            return false;
        }
        double d2 = creeper.distanceToSqr(t);
        boolean seen = creeper.getSensing().hasLineOfSight(t);
        // a leaping creeper with its leap ready jumps over the wall instead of blasting through it
        if (!seen && CreeperLeapGoal.leapReady(creeper)) return false;
        if (d2 < 9) return true;
        if (d2 < wallRange() * wallRange() && !seen) return true;
        breaching = shouldBreach(t, d2);
        return breaching;
    }

    private boolean shouldBreach(LivingEntity t, double d2) {
        if (d2 > 100 || Math.abs(t.getY() - creeper.getY()) > 4) return false;
        Path path = creeper.getNavigation().getPath();
        boolean pathOk = path != null && path.canReach();
        stuck = pathOk ? 0 : stuck + 1;
        if (stuck < 30 || !(creeper.level() instanceof ServerLevel sl)) return false;
        if (!MobConfig.breakBlocks() || !EventHooks.canEntityGrief(sl, creeper)) return false;
        Direction dir = Direction.getApproximateNearest(t.getX() - creeper.getX(), 0, t.getZ() - creeper.getZ());
        BlockPos front = creeper.blockPosition().relative(dir);
        return !sl.getBlockState(front).getCollisionShape(sl, front).isEmpty()
                || !sl.getBlockState(front.above()).getCollisionShape(sl, front.above()).isEmpty();
    }

    @Override
    public void start() {
        creeper.getNavigation().stop();
        target = creeper.getTarget();
    }

    @Override
    public void stop() {
        target = null;
        breaching = false;
        stuck = 0;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (target == null || !target.isAlive()) {
            creeper.setSwellDir(-1);
            return;
        }
        double limit = breaching ? 12 : 7;
        creeper.setSwellDir(creeper.distanceToSqr(target) > limit * limit ? -1 : 1);
    }
}
