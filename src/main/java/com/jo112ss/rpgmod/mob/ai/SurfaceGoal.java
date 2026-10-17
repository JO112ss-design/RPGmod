package com.jo112ss.rpgmod.mob.ai;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.Path;

/**
 * Takes a zombie out of a cave towards a player up on the surface.
 *
 * <ul>
 *   <li>If the cave has a way out it can walk, it walks out (to the ground next to the player) instead
 *       of digging straight at them.</li>
 *   <li>If not, it digs a staircase up towards the player, one step per block, and comes out of the
 *       ground in front of them rather than straight below.</li>
 * </ul>
 *
 * Holds the movement flag while it works, so the normal attack goal does not drag the zombie back to
 * the spot under the player. Gives control back once the zombie is out on the surface.
 */
public final class SurfaceGoal extends Goal {
    private enum Mode { EXIT, STAIRS }

    private final Mob mob;
    private final BuilderGoal builder;
    private Mode mode;
    private Path exitPath;
    private Direction dir;
    private int runTicks, cool, hops, nextExitCheck, lastY;
    private boolean running, failed;
    /** Dev test: the last mode chosen by any zombie ("exit" / "stairs"). */
    public static String lastMode = "-";

    public SurfaceGoal(Mob mob, BuilderGoal builder) {
        this.mob = mob;
        this.builder = builder;
        builder.setSurface(this);
        setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    boolean running() {
        return running;
    }

    private LivingEntity target() {
        LivingEntity t = mob.getTarget();
        return t instanceof Player p && !p.isCreative() && !p.isSpectator() && p.isAlive() ? p : null;
    }

    private static int surfaceY(ServerLevel sl, double x, double z) {
        return sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
    }

    /** Below the ground (rock above its head), with the target up near the surface well above it. */
    private boolean below(ServerLevel sl, LivingEntity t) {
        if (surfaceY(sl, mob.getX(), mob.getZ()) - mob.getY() < 4) return false;
        if (surfaceY(sl, t.getX(), t.getZ()) - t.getY() > 6) return false; // the player is underground too
        return t.getY() - mob.getY() >= 3;
    }

    @Override
    public boolean canUse() {
        if (!(mob.level() instanceof ServerLevel sl)) return false;
        LivingEntity t = target();
        if (t == null || !below(sl, t)) return false;
        // the attack goal already has a full path to them: just let it walk
        Path cur = mob.getNavigation().getPath();
        if (cur != null && !cur.isDone() && cur.canReach()) return false;
        if (--nextExitCheck <= 0) {
            nextExitCheck = 60 + mob.getRandom().nextInt(40);
            Path out = exitPath(sl, t);
            if (out != null) {
                mode = Mode.EXIT;
                exitPath = out;
                return true;
            }
        }
        // no way out: dig up once a 45-degree staircase would come out near them
        double hx = t.getX() - mob.getX(), hz = t.getZ() - mob.getZ();
        double hd = Math.sqrt(hx * hx + hz * hz), dy = t.getY() - mob.getY();
        boolean idle = mob.getNavigation().isDone();
        if (hd <= dy + 2 || idle) {
            mode = Mode.STAIRS;
            return true;
        }
        return false;
    }

    /** A walkable path out to the ground around the player, or null. */
    private Path exitPath(ServerLevel sl, LivingEntity t) {
        Set<BlockPos> goals = new HashSet<>();
        int px = (int) Math.floor(t.getX()), pz = (int) Math.floor(t.getZ());
        for (int dx = -4; dx <= 4; dx += 2) {
            for (int dz = -4; dz <= 4; dz += 2) {
                int y = surfaceY(sl, px + dx, pz + dz);
                if (y > t.getY() + 3 || y < t.getY() - 4) continue; // a roof, or a pit
                goals.add(new BlockPos(px + dx, y, pz + dz));
            }
        }
        if (goals.isEmpty()) return null;
        Path p = mob.getNavigation().createPath(goals, 1);
        return p != null && p.canReach() ? p : null;
    }

    @Override
    public void start() {
        running = true;
        runTicks = 0;
        cool = 0;
        hops = 0;
        failed = false;
        lastY = mob.getBlockY();
        builder.cancelDig();
        lastMode = mode == Mode.EXIT ? "exit" : "stairs";
        if (mode == Mode.EXIT) {
            mob.getNavigation().moveTo(exitPath, 1.0);
        } else {
            mob.getNavigation().stop();
            dir = null;
        }
        if (com.jo112ss.rpgmod.devtest.AutoTestServer.ENABLED) {
            com.jo112ss.rpgmod.RpgMod.LOGGER.info("[AUTOTEST] surface goal {} from {}", lastMode, mob.blockPosition());
        }
    }

    @Override
    public boolean canContinueToUse() {
        if (!(mob.level() instanceof ServerLevel sl) || failed || ++runTicks > 1200) return false;
        LivingEntity t = target();
        if (t == null) return false;
        if (mode == Mode.EXIT) return mob.getNavigation().isInProgress();
        // up the stairs until out in the open or level with them
        return surfaceY(sl, mob.getX(), mob.getZ()) - mob.getY() >= 2 && t.getY() - mob.getY() >= 1.5;
    }

    @Override
    public void stop() {
        running = false;
        exitPath = null;
        if (mode == Mode.STAIRS) nextExitCheck = Math.max(nextExitCheck, 40);
        mode = null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (mode != Mode.STAIRS || !(mob.level() instanceof ServerLevel sl)) return;
        LivingEntity t = target();
        if (t == null || builder.digging()) return;
        if (cool > 0) {
            cool--;
            return;
        }
        mob.getNavigation().stop();
        double hx = t.getX() - mob.getX(), hz = t.getZ() - mob.getZ();
        // keep one heading for the staircase; turn only while still well off to the side
        if (dir == null || hx * hx + hz * hz >= 9 && Direction.getApproximateNearest(hx, 0, hz) != dir) {
            dir = hx * hx + hz * hz < 1 ? mob.getDirection() : Direction.getApproximateNearest(hx, 0, hz);
        }
        BlockPos feet = mob.blockPosition();
        BlockPos front = feet.relative(dir);
        // headroom to jump, then the two blocks the zombie steps into
        for (BlockPos p : new BlockPos[] {feet.above(2), front.above(2), front.above()}) {
            if (BuilderGoal.solid(sl, p)) {
                if (!builder.tryDig(sl, p)) failed = true;
                return;
            }
        }
        if (!BuilderGoal.solid(sl, front)) {
            // nothing to step up onto (a gap in the rock): put a block there
            if (!builder.place(sl, front)) cool = 10;
            return;
        }
        if (mob.getBlockY() > lastY) { // made it up a step
            lastY = mob.getBlockY();
            hops = 0;
        } else if (++hops > 10) {
            failed = true; // something keeps it from climbing: leave it to the other goals
            return;
        }
        if (mob.onGround()) mob.getJumpControl().jump();
        mob.getMoveControl().setWantedPosition(front.getX() + 0.5, front.getY() + 1, front.getZ() + 0.5, 1.0);
        cool = 6;
    }
}
