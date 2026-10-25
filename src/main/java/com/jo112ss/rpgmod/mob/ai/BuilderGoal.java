package com.jo112ss.rpgmod.mob.ai;

import com.jo112ss.rpgmod.mob.MobConfig;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.EventHooks;

/**
 * Zombies that do not give up: when they cannot walk to their target they build a pillar under
 * themselves to reach someone above, bridge over gaps and liquids, and dig through walls (time
 * depends on the block's hardness; never obsidian, bedrock or blocks with contents such as chests).
 *
 * <p>The goal claims no control flags, so it runs beside the normal attack goal and only steps in
 * once the zombie has been stuck for a second.
 */
public final class BuilderGoal extends Goal {
    private static final int MAX_BLOCKS = 40;

    private final Mob mob;
    private final int level;
    private int stuck, sinceBest, cooldown, placed;
    private double best = Double.MAX_VALUE;
    private BlockPos digPos;
    private int digTime, digNeed;
    private BlockPos pillarBase;
    private int pillarAge;
    /** Where the zombie was a second ago: walking along a path counts as progress, even a partial one. */
    private net.minecraft.world.phys.Vec3 sample;
    private int sampleAge;
    private boolean moving;
    /** The companion goal that takes zombies out of caves (walking out, or digging stairs up). */
    private SurfaceGoal surface;
    /** Dev test: blocks dug by all builders. */
    public static int dugBlocks;

    public BuilderGoal(Mob mob, int level) {
        this.mob = mob;
        this.level = level;
        setFlags(EnumSet.noneOf(Goal.Flag.class));
    }

    void setSurface(SurfaceGoal surface) {
        this.surface = surface;
    }

    boolean digging() {
        return digPos != null;
    }

    void cancelDig() {
        resetDig();
    }

    private LivingEntity target() {
        LivingEntity t = mob.getTarget();
        return t instanceof Player p && !p.isCreative() && !p.isSpectator() && p.isAlive() ? p : null;
    }

    @Override
    public boolean canUse() {
        return mob.level() instanceof ServerLevel && target() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void stop() {
        resetDig();
        pillarBase = null;
        stuck = 0;
        sinceBest = 0;
        best = Double.MAX_VALUE;
        placed = 0;
    }

    @Override
    public void tick() {
        LivingEntity t = target();
        if (t == null || !(mob.level() instanceof ServerLevel sl)) return;
        if (cooldown > 0) cooldown--;
        double d = mob.distanceTo(t);
        if (d < best - 0.5) {
            best = d;
            sinceBest = 0;
        } else {
            sinceBest++;
        }
        if (d < 2.0) {
            stuck = 0;
            resetDig();
            return;
        }
        if (sample == null || ++sampleAge >= 20) {
            moving = sample != null && mob.position().distanceTo(sample) >= 1.0;
            sample = mob.position();
            sampleAge = 0;
        }
        Path path = mob.getNavigation().getPath();
        boolean pathOk = path != null && path.canReach();
        // still walking somewhere (even only part of the way, like out of a cave): not stuck yet
        boolean walking = mob.getNavigation().isInProgress() && moving;
        stuck = walking || (pathOk && sinceBest <= 40) ? Math.max(0, stuck - 2) : stuck + 1;
        if (digPos != null) {
            continueDig(sl);
            return;
        }
        if (pillarBase != null) {
            continuePillar(sl);
            return;
        }
        if (surface != null && surface.running()) return;
        if (stuck < 10 || cooldown > 0 || placed >= MAX_BLOCKS) return;
        act(sl, t);
    }

    private void act(ServerLevel sl, LivingEntity t) {
        double dy = t.getY() - mob.getY();
        double hx = t.getX() - mob.getX(), hz = t.getZ() - mob.getZ();
        double hd = Math.sqrt(hx * hx + hz * hz);
        BlockPos feet = mob.blockPosition();
        if (dy >= 1.5 && hd < 3.5) {
            startPillar(sl, feet);
            return;
        }
        if (dy <= -2 && hd < 2.0) {
            tryDig(sl, feet.below());
            return;
        }
        Direction dir = Direction.getApproximateNearest(hx, 0, hz);
        BlockPos front = feet.relative(dir);
        if (solid(sl, front.above())) {
            tryDig(sl, front.above());
            return;
        }
        if (solid(sl, front)) {
            if (dy >= 0.5 && !solid(sl, feet.above(2)) && !solid(sl, front.above(2))) {
                // a one-block step towards someone higher up: hop onto it
                if (mob.onGround()) mob.getJumpControl().jump();
                walk(front.above());
                return;
            }
            tryDig(sl, front);
            return;
        }
        BlockPos under = front.below();
        if (!solid(sl, under)) {
            if (dy <= -1 && solid(sl, under.below())) { // just a one-block drop: walk off it
                walk(front);
                return;
            }
            if (place(sl, under)) {
                cooldown = 3;
                walk(front);
            }
            return;
        }
        walk(front);
    }

    private void walk(BlockPos p) {
        mob.getMoveControl().setWantedPosition(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 1.0);
    }

    // ------------------------------------------------------------------ pillar

    private void startPillar(ServerLevel sl, BlockPos feet) {
        if (solid(sl, feet.above(2))) {
            tryDig(sl, feet.above(2));
            return;
        }
        if (!mob.onGround()) return;
        mob.getNavigation().stop();
        // a strong hop straight up: clears one block in about three ticks
        mob.setDeltaMovement(0, 0.55, 0);
        pillarBase = feet;
        pillarAge = 0;
    }

    private void continuePillar(ServerLevel sl) {
        pillarAge++;
        // stay centred over the column
        mob.setDeltaMovement(0, mob.getDeltaMovement().y, 0);
        if (mob.getY() >= pillarBase.getY() + 1.0 && sl.getBlockState(pillarBase).canBeReplaced()) {
            place(sl, pillarBase);
            pillarBase = null;
            return;
        }
        if (pillarAge > 14) pillarBase = null;
    }

    // ------------------------------------------------------------------ blocks

    static boolean solid(ServerLevel sl, BlockPos p) {
        return !sl.getBlockState(p).getCollisionShape(sl, p).isEmpty();
    }

    private static Block material(Level level) {
        if (level.dimension() == Level.NETHER) return Blocks.NETHERRACK;
        if (level.dimension() == Level.END) return Blocks.END_STONE;
        return Blocks.DIRT;
    }

    boolean place(ServerLevel sl, BlockPos p) {
        BlockState at = sl.getBlockState(p);
        if (!at.canBeReplaced()) return false;
        AABB box = new AABB(p);
        if (!sl.getEntitiesOfClass(LivingEntity.class, box, e -> e != mob && e.isAlive()).isEmpty()) return false;
        if (mob.getBoundingBox().intersects(box)) return false;
        BlockState s = material(sl).defaultBlockState();
        sl.setBlock(p, s, Block.UPDATE_ALL);
        sl.playSound(null, p, s.getSoundType().getPlaceSound(), SoundSource.HOSTILE, 1.0F, 0.9F);
        mob.swingForAttack(InteractionHand.MAIN_HAND);
        placed++;
        return true;
    }

    // ------------------------------------------------------------------ digging

    private boolean canBreak(ServerLevel sl, BlockPos p) {
        if (!MobConfig.breakBlocks() || !EventHooks.canEntityGrief(sl, mob)) return false;
        BlockState s = sl.getBlockState(p);
        float h = s.getDestroySpeed(sl, p);
        return !s.isAir() && h >= 0 && h < 50 && !s.hasBlockEntity() && EventHooks.onEntityDestroyBlock(mob, p, s);
    }

    /** Starts digging {@code p}; false if this zombie may not break it. */
    boolean tryDig(ServerLevel sl, BlockPos p) {
        if (!canBreak(sl, p)) {
            cooldown = 40;
            return false;
        }
        float h = sl.getBlockState(p).getDestroySpeed(sl, p);
        digPos = p;
        digTime = 0;
        // dirt ~0.6 s, stone ~1.2 s, deepslate ~2 s; quicker for stronger zombies
        digNeed = Math.max(6, (int) ((8 + h * 12) * (1 - Math.min(0.5, level * 0.008))));
        mob.getNavigation().stop();
        return true;
    }

    private void continueDig(ServerLevel sl) {
        BlockState s = sl.getBlockState(digPos);
        if (s.isAir() || mob.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(digPos)) > 12) {
            resetDig();
            return;
        }
        mob.getLookControl().setLookAt(digPos.getX() + 0.5, digPos.getY() + 0.5, digPos.getZ() + 0.5);
        digTime++;
        if (digTime % 4 == 0) {
            mob.swingForAttack(InteractionHand.MAIN_HAND);
            sl.playSound(null, digPos, s.getSoundType().getHitSound(), SoundSource.HOSTILE, 0.8F, 0.8F);
        }
        sl.destroyBlockProgress(mob.getId(), digPos, Math.min(9, digTime * 10 / digNeed));
        if (digTime >= digNeed) {
            BlockPos p = digPos;
            resetDig();
            // plain stone, deepslate and dirt break without dropping (heaps of items made the game lag)
            sl.destroyBlock(p, !noDrop(s), mob);
            dugBlocks++;
            cooldown = 1;
        }
    }

    /** Blocks a digging zombie breaks without leaving an item: stone and deepslate (and their cobble), dirt. */
    public static boolean noDrop(net.minecraft.world.level.block.state.BlockState s) {
        return s.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD) || s.is(net.minecraft.tags.BlockTags.DIRT)
                || s.is(net.minecraft.world.level.block.Blocks.COBBLESTONE) || s.is(net.minecraft.world.level.block.Blocks.COBBLED_DEEPSLATE);
    }

    private void resetDig() {
        if (digPos != null && mob.level() instanceof ServerLevel sl) sl.destroyBlockProgress(mob.getId(), digPos, -1);
        digPos = null;
        digTime = 0;
    }
}
