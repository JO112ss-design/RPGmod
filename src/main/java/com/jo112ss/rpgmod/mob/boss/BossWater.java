package com.jo112ss.rpgmod.mob.boss;

import com.jo112ss.rpgmod.RpgMod;
import com.jo112ss.rpgmod.job.fx.ServerFx;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Bosses and water. Standing out on a lake used to beat every boss: they walked in and floundered, or
 * could not reach at all. Now:
 * <ul>
 * <li>they keep out of water: walking ones path around it, and a boss that ends up in water leaps
 * (or blinks) out onto the nearest dry ground</li>
 * <li>someone out on the water (deep water, a boat) is not chased in: the boss leaps to the dry
 * ground closest to them, keeps to its ranged moves, and fires <b>homing shots</b> at them</li>
 * </ul>
 */
public final class BossWater {
    private BossWater() {}

    /** Steering of a homing shot: how much of the way to the wanted heading it turns each tick. */
    public static final double TURN = 0.14;
    /** Farthest from the target the boss looks for dry ground, and the farthest it leaps. */
    public static final double LAND_RANGE = 22, MAX_LEAP = 48;

    /** Water paths cost a walking boss nothing it is willing to pay. */
    public static void avoidWater(Mob boss) {
        boss.setPathfindingMalus(PathType.WATER, -1.0F);
        boss.setPathfindingMalus(PathType.WATER_BORDER, 16.0F);
    }

    private static boolean water(Level l, BlockPos p) {
        return l.getFluidState(p).is(FluidTags.WATER);
    }

    /** Water blocks straight down from {@code p} (up to 4). */
    private static int depth(Level l, BlockPos p) {
        int d = 0;
        while (d < 4 && water(l, p.below(d))) d++;
        return d;
    }

    /** Out on the water where nothing walking can follow: deep water (2+), or a boat on water. */
    public static boolean waterBound(LivingEntity t) {
        Level l = t.level();
        BlockPos feet = t.blockPosition();
        int d = depth(l, feet);
        if (d == 0) d = depth(l, feet.below());
        if (t.getVehicle() instanceof AbstractBoat) return d >= 1;
        return d >= 2;
    }

    /** The boss itself in the water (wading or swimming). */
    public static boolean inWater(Mob boss) {
        return boss.isInWater() && (water(boss.level(), boss.blockPosition()) || water(boss.level(), boss.blockPosition().below()));
    }

    /**
     * The dry ground nearest to {@code near} (within {@code range}) where the boss fits: solid footing
     * that is not water, no water at its feet, room for its body. Null if there is none.
     */
    public static Vec3 dryLandNear(ServerLevel sl, Mob boss, Vec3 near, double range) {
        var dims = boss.getDimensions(boss.getPose());
        int baseY = (int) Math.floor(near.y);
        for (double r = 0; r <= range; r += 1.5) {
            int n = r == 0 ? 1 : Math.max(6, (int) (Math.PI * 2 * r / 1.5));
            double a0 = boss.getRandom().nextDouble() * Math.PI * 2;
            Vec3 best = null;
            double bestD = Double.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                double a = a0 + Math.PI * 2 * i / n;
                int x = (int) Math.floor(near.x + Math.cos(a) * r), z = (int) Math.floor(near.z + Math.sin(a) * r);
                Vec3 at = footing(sl, x, baseY, z);
                if (at == null) continue;
                AABB box = dims.makeBoundingBox(at);
                if (!sl.noCollision(boss, box) || sl.containsAnyLiquid(box.inflate(0.3, 0, 0.3))) continue;
                // all four corners on solid, dry ground (not hanging over the water's edge)
                if (!dryUnder(sl, box)) continue;
                double dist = at.distanceToSqr(boss.position());
                if (at.distanceTo(boss.position()) > MAX_LEAP) continue;
                if (dist < bestD) {
                    bestD = dist;
                    best = at;
                }
            }
            if (best != null) return best;
        }
        return null;
    }

    /** Floor of an open, dry spot at x/z within 6 above / 10 below y, or null. */
    private static Vec3 footing(ServerLevel sl, int x, int y, int z) {
        for (int dy = 6; dy >= -10; dy--) {
            BlockPos at = new BlockPos(x, y + dy, z), below = at.below();
            if (!sl.getFluidState(at).isEmpty() || !sl.getBlockState(at).getCollisionShape(sl, at).isEmpty()) continue;
            if (!sl.getFluidState(below).isEmpty()) return null; // water (or lava) is the first thing under
            if (sl.getBlockState(below).getCollisionShape(sl, below).isEmpty()) continue;
            return new Vec3(x + 0.5, at.getY(), z + 0.5);
        }
        return null;
    }

    private static boolean dryUnder(ServerLevel sl, AABB box) {
        int y = (int) Math.floor(box.minY) - 1;
        for (double x : new double[] {box.minX + 0.2, box.maxX - 0.2}) {
            for (double z : new double[] {box.minZ + 0.2, box.maxZ - 0.2}) {
                BlockPos p = BlockPos.containing(x, y, z);
                if (!sl.getFluidState(p).isEmpty() || !sl.getFluidState(p.above()).isEmpty()) return false;
                if (sl.getBlockState(p).getCollisionShape(sl, p).isEmpty() && sl.getBlockState(p.below()).getCollisionShape(sl, p.below()).isEmpty()) return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ per boss: when to leap, when to shoot

    /** One per boss: decides when to leap to dry ground and when to fire homing shots. */
    public static final class Tracker {
        private int leapCd = 20, homingCd = 40;
        /** Dev test counters. */
        public int leaps, volleys;

        /**
         * Call each tick of the boss's chase. Dry ground to leap (or blink) to right now, or null: out of
         * the water when in it, or to the shore nearest a target out on the water.
         */
        public Vec3 relocate(ServerLevel sl, Mob boss, LivingEntity t) {
            if (leapCd > 0) {
                leapCd--;
                return null;
            }
            if (!boss.onGround() && !boss.isInWater()) return null;
            boolean wet = inWater(boss);
            boolean out = t != null && waterBound(t);
            if (!wet && !out) return null;
            Vec3 near = t != null ? t.position() : boss.position();
            Vec3 dest = dryLandNear(sl, boss, near, LAND_RANGE);
            if (dest == null && wet) dest = dryLandNear(sl, boss, boss.position(), 32);
            if (dest == null) {
                leapCd = 20;
                return null;
            }
            if (!wet && t != null) {
                // only worth a leap if it brings the boss well closer
                double now = Math.hypot(t.getX() - boss.getX(), t.getZ() - boss.getZ());
                double then = Math.hypot(t.getX() - dest.x, t.getZ() - dest.z);
                if (then + 5 > now) {
                    leapCd = 20;
                    return null;
                }
            }
            leapCd = 60;
            leaps++;
            RpgMod.LOGGER.info("[boss] {} leaves the water's edge for {} ({})", boss.getType().getDescriptionId(), BlockPos.containing(dest),
                    wet ? "it was in the water" : "target out on the water");
            return dest;
        }

        /** Every tick (whatever the boss is doing): the homing shots come due while the target stays out on the water. */
        public void tick(LivingEntity t) {
            if (t != null && waterBound(t)) {
                if (homingCd > 0) homingCd--;
            } else {
                homingCd = Math.max(homingCd, 30);
            }
        }

        /** Whether it is time for a volley of homing shots at a target out on the water (asked between moves). */
        public boolean homingDue(LivingEntity t, int phase) {
            if (t == null || homingCd > 0 || !waterBound(t)) return false;
            homingCd = phase >= 2 ? 70 : 90;
            volleys++;
            return true;
        }

        /** Out on the water: the boss should keep to ranged moves. */
        public boolean ranged(LivingEntity t) {
            return t != null && waterBound(t);
        }
    }

    // ------------------------------------------------------------------ homing shots

    /** A boss's homing shots: they rise from it, hang a moment, then chase their target and turn after it. */
    public static final class Homing {
        private static final class Shot {
            Vec3 pos, vel;
            final UUID target;
            int delay, life;
            final float damage;

            Shot(Vec3 pos, Vec3 vel, UUID target, int delay, float damage) {
                this.pos = pos;
                this.vel = vel;
                this.target = target;
                this.delay = delay;
                this.damage = damage;
                this.life = 120;
            }
        }

        private final List<Shot> shots = new ArrayList<>();
        private final int color;
        /** Dev test: shots that hit. */
        public int hits;

        public Homing(int color) {
            this.color = color;
        }

        /** {@code n} shots at {@code t}, fanned out over the boss, launching one after another after {@code windup} ticks. */
        public void volley(ServerLevel sl, Mob boss, LivingEntity t, int n, float damage, double speed, int windup) {
            Vec3 top = boss.position().add(0, boss.getBbHeight() * 0.9 + 0.6, 0);
            Vec3 fwd = t.position().subtract(boss.position()).multiply(1, 0, 1);
            fwd = fwd.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : fwd.normalize();
            Vec3 side = new Vec3(-fwd.z, 0, fwd.x);
            for (int i = 0; i < n; i++) {
                double k = n == 1 ? 0 : (i / (double) (n - 1)) * 2 - 1;
                Vec3 at = top.add(side.scale(k * (boss.getBbWidth() * 0.5 + 1.2))).add(0, (1 - Math.abs(k)) * 0.8, 0);
                Vec3 vel = side.scale(k * 0.6).add(0, 0.5, 0).add(fwd.scale(0.2)).normalize().scale(speed);
                int delay = windup + i * 5;
                shots.add(new Shot(at, vel, t.getUUID(), delay, damage));
                ServerFx.of("boss_homing", at).vec(vel).follow(t).a(delay).b(120).color(color).send(sl, 96);
            }
            ServerFx.of("mob_warn", boss.position()).follow(boss).a(boss.getBbWidth() / 2 + 1).b(windup).color(color).send(sl);
            sl.playSound(null, boss.blockPosition(), SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.HOSTILE, 2.0F, 0.6F);
        }

        public void tick(ServerLevel sl, Mob boss) {
            for (var it = shots.iterator(); it.hasNext(); ) {
                Shot s = it.next();
                if (s.delay > 0) {
                    if (--s.delay == 0) sl.playSound(null, BlockPos.containing(s.pos), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1.0F, 1.4F);
                    continue;
                }
                if (--s.life <= 0) {
                    ServerFx.of("boss_homing_hit", s.pos).color(color).send(sl, 64);
                    it.remove();
                    continue;
                }
                Player target = sl.getPlayerByUUID(s.target);
                double speed = s.vel.length();
                if (target != null && target.isAlive() && !target.isSpectator()) {
                    Vec3 want = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(s.pos);
                    if (want.lengthSqr() > 1e-4) s.vel = s.vel.normalize().lerp(want.normalize(), TURN).normalize().scale(speed);
                }
                Vec3 from = s.pos, to = s.pos.add(s.vel);
                var block = sl.clip(new net.minecraft.world.level.ClipContext(from, to, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                        net.minecraft.world.level.ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()));
                boolean wall = block.getType() != net.minecraft.world.phys.HitResult.Type.MISS;
                Vec3 end = wall ? block.getLocation() : to;
                ServerPlayer hit = null;
                for (ServerPlayer p : sl.getEntitiesOfClass(ServerPlayer.class, new AABB(from, end).inflate(1.0),
                        p -> p.isAlive() && !p.isSpectator() && !p.isCreative())) {
                    AABB bb = p.getBoundingBox().inflate(0.4);
                    if (bb.contains(end) || bb.clip(from, end).isPresent()) {
                        hit = p;
                        break;
                    }
                }
                s.pos = end;
                if (hit != null) {
                    hit.hurtServer(sl, boss.damageSources().indirectMagic(boss, boss), s.damage);
                    hits++;
                    ServerFx.of("boss_homing_hit", end).color(color).send(sl, 64);
                    sl.playSound(null, hit.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.6F, 1.6F);
                    it.remove();
                } else if (wall) {
                    ServerFx.of("boss_homing_hit", end).color(color).send(sl, 64);
                    it.remove();
                }
            }
        }

        public void clear() {
            shots.clear();
        }
    }
}
