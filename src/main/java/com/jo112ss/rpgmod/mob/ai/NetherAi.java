package com.jo112ss.rpgmod.mob.ai;

import com.jo112ss.rpgmod.RpgMod;
import com.jo112ss.rpgmod.job.SkillTasks;
import com.jo112ss.rpgmod.job.fx.ServerFx;
import com.jo112ss.rpgmod.mob.MobConfig;
import com.jo112ss.rpgmod.mob.MobLevels;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Zoglin;
import net.minecraft.world.entity.monster.cubemob.MagmaCube;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Smarter nether monsters (every level; level only sharpens aim):
 * <ul>
 *   <li>Blazes lead their fireballs, sometimes fire an extra one, and blast a ring of fire around
 *       themselves when you get close (announced by a ring on the ground).</li>
 *   <li>Ghasts lead their shots and sometimes follow up with two more fireballs.</li>
 *   <li>Wither skeletons and piglin brutes lunge at you from a few blocks away after a short warning.</li>
 *   <li>Hoglins and zoglins snort, lower their heads and charge in a straight line.</li>
 *   <li>Magma cubes leap at you from range and land in a burst of fire.</li>
 *   <li>Zombified piglins, once angry, build and dig towards you like zombies (see {@link MobAi}).</li>
 * </ul>
 */
@EventBusSubscriber(modid = RpgMod.MODID)
public final class NetherAi {
    private NetherAi() {}

    /** Counters for the development test. */
    public static int novas, lunges, charges, leaps, followUps;

    private static boolean inFollowUp;
    private static final Map<Entity, Long> NEXT = new WeakHashMap<>();

    private static boolean ready(Entity e, long now) {
        Long n = NEXT.get(e);
        return n == null || now >= n;
    }

    private static Player target(Mob mob) {
        return mob.getTarget() instanceof Player p && !p.isCreative() && !p.isSpectator() && p.isAlive() ? p : null;
    }

    // ------------------------------------------------------------------ fireballs

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || event.loadedFromDisk() || !MobConfig.smartAi()) return;
        if (!(event.getEntity() instanceof AbstractHurtingProjectile ball) || !(ball.getOwner() instanceof Mob shooter)) return;
        if (!(shooter instanceof Blaze) && !(shooter instanceof Ghast)) return;
        int lv = MobLevels.level(shooter);
        LivingEntity t = shooter.getTarget();
        if (lv <= 0 || t == null || !(ball.level() instanceof ServerLevel sl)) return;
        aim(ball, t, lv, shooter instanceof Blaze ? 1.6 : 1.3);
        if (inFollowUp) return;
        if (ball instanceof SmallFireball && sl.getRandom().nextDouble() < 0.3) {
            // a fourth fireball right after the vanilla three
            SkillTasks.later(4, () -> fire(sl, shooter, true));
        } else if (ball instanceof LargeFireball && shooter instanceof Ghast g && sl.getRandom().nextDouble() < 0.35) {
            SkillTasks.later(8, () -> fire(sl, g, false));
            SkillTasks.later(16, () -> fire(sl, g, false));
        }
    }

    /** Points a fireball at where the target will be, keeping its speed. */
    private static void aim(AbstractHurtingProjectile ball, LivingEntity t, int lv, double avgSpeed) {
        Vec3 from = ball.position();
        Vec3 aimAt = t.position().add(0, t.getBbHeight() * 0.5, 0);
        Vec3 known = t.getKnownMovement();
        Vec3 vel = new Vec3(known.x, t.onGround() ? 0 : known.y, known.z);
        double lead = Math.min(1.0, 0.35 + lv * 0.012);
        double flight = from.distanceTo(aimAt) / avgSpeed;
        Vec3 predicted = aimAt.add(vel.scale(flight * lead));
        Vec3 dir = predicted.subtract(from);
        double spread = Math.max(0.0, 0.08 - lv * 0.001);
        var r = ball.level().getRandom();
        dir = dir.normalize().add(r.nextGaussian() * spread, r.nextGaussian() * spread, r.nextGaussian() * spread).normalize();
        double speed = Math.max(0.1, ball.getDeltaMovement().length());
        ball.setDeltaMovement(dir.scale(speed));
        ball.needsSync = true;
    }

    private static void fire(ServerLevel sl, Mob shooter, boolean small) {
        LivingEntity t = shooter.getTarget();
        if (!shooter.isAlive() || t == null || !shooter.getSensing().hasLineOfSight(t)) return;
        inFollowUp = true;
        try {
            if (small) {
                Vec3 dir = t.position().subtract(shooter.position());
                SmallFireball b = new SmallFireball(sl, shooter, dir.normalize());
                b.setPos(b.getX(), shooter.getY(0.5) + 0.5, b.getZ());
                sl.addFreshEntity(b);
                sl.levelEvent(null, 1018, shooter.blockPosition(), 0);
            } else if (shooter instanceof Ghast g) {
                Vec3 view = g.getViewVector(1.0F);
                Vec3 from = new Vec3(g.getX() + view.x * 4, g.getY(0.5) + 0.5, g.getZ() + view.z * 4);
                LargeFireball b = new LargeFireball(sl, g, t.position().subtract(from).normalize(), g.getExplosionPower());
                b.setPos(from.x, from.y, from.z);
                sl.addFreshEntity(b);
                sl.levelEvent(null, 1016, g.blockPosition(), 0);
            }
            followUps++;
        } finally {
            inFollowUp = false;
        }
    }

    // ------------------------------------------------------------------ close-range moves

    @SubscribeEvent
    public static void onTick(EntityTickEvent.Post event) {
        Entity e = event.getEntity();
        if (!(e instanceof Mob mob) || e.tickCount % 5 != 0 || !(e.level() instanceof ServerLevel sl) || !MobConfig.smartAi()) return;
        boolean blaze = mob instanceof Blaze, lunger = mob instanceof WitherSkeleton || mob instanceof PiglinBrute;
        boolean charger = mob instanceof Hoglin || mob instanceof Zoglin, cube = mob instanceof MagmaCube;
        if (!blaze && !lunger && !charger && !cube) return;
        if (MobLevels.level(mob) <= 0) return;
        Player p = target(mob);
        if (p == null) return;
        long now = sl.getGameTime();
        if (!ready(mob, now)) return;
        double d = mob.distanceTo(p);
        var r = sl.getRandom();
        if (blaze && d < 6.5 && mob.getSensing().hasLineOfSight(p)) {
            flameNova(sl, mob);
            NEXT.put(mob, now + 120 + r.nextInt(40));
        } else if (lunger && mob.onGround() && d > 3.5 && d < 10 && mob.getSensing().hasLineOfSight(p) && r.nextDouble() < 0.35) {
            lunge(sl, mob, p);
            NEXT.put(mob, now + 80 + r.nextInt(40));
        } else if (charger && mob.onGround() && d > 4 && d < 14 && mob.getSensing().hasLineOfSight(p) && r.nextDouble() < 0.3) {
            charge(sl, mob, p);
            NEXT.put(mob, now + 140 + r.nextInt(60));
        } else if (cube && d > 3 && d < 14 && ((MagmaCube) mob).getSize() >= 2 && r.nextDouble() < 0.4) {
            leap(sl, (MagmaCube) mob, p);
            NEXT.put(mob, now + 120 + r.nextInt(60));
        }
    }

    /** Blaze: a ring of fire bursts out after a short warning, burning and pushing away whoever is near. */
    private static void flameNova(ServerLevel sl, Mob blaze) {
        double radius = 6;
        ServerFx.of("mob_warn", blaze.position()).follow(blaze).a(radius).b(14).color(0xFF8A30).send(sl);
        sl.playSound(null, blaze.getX(), blaze.getY(), blaze.getZ(), SoundEvents.BLAZE_BURN, SoundSource.HOSTILE, 1.5F, 0.6F);
        SkillTasks.later(14, () -> {
            if (!blaze.isAlive()) return;
            ServerFx.of("fire_ring", blaze.position()).a(radius).color(0xFF8A30).send(sl);
            sl.playSound(null, blaze.getX(), blaze.getY(), blaze.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1.5F, 0.5F);
            for (Player p : sl.getEntitiesOfClass(Player.class, blaze.getBoundingBox().inflate(radius), x -> !x.isCreative() && !x.isSpectator())) {
                if (p.distanceTo(blaze) > radius) continue;
                p.hurtServer(sl, sl.damageSources().mobAttack(blaze), 4.0F);
                p.igniteForSeconds(4);
                Vec3 away = p.position().subtract(blaze.position()).multiply(1, 0, 1).normalize();
                p.push(away.x * 0.9, 0.35, away.z * 0.9);
            }
            novas++;
        });
    }

    /** Wither skeleton / piglin brute: a red flash, then a leap that closes the gap. */
    private static void lunge(ServerLevel sl, Mob mob, Player p) {
        ServerFx.of("mob_warn", mob.position()).follow(mob).a(1.2).b(8).color(0xFF3040).send(sl);
        sl.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.WITHER_SKELETON_AMBIENT, SoundSource.HOSTILE, 1.0F, 0.6F);
        SkillTasks.later(8, () -> {
            if (!mob.isAlive() || !p.isAlive() || !mob.onGround()) return;
            Vec3 to = p.position().subtract(mob.position());
            Vec3 flat = new Vec3(to.x, 0, to.z);
            double dist = flat.length();
            Vec3 dir = flat.normalize();
            double speed = Math.min(1.6, 0.4 + dist * 0.13);
            mob.setDeltaMovement(dir.x * speed, 0.38, dir.z * speed);
            mob.getLookControl().setLookAt(p, 30, 30);
            lunges++;
        });
    }

    /** Hoglin / zoglin: snorts and paws (warning), then a straight charge that throws whoever it hits. */
    private static void charge(ServerLevel sl, Mob mob, Player p) {
        ServerFx.of("mob_warn", mob.position()).follow(mob).a(1.5).b(16).color(0xFF6A40).send(sl);
        sl.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.HOGLIN_ANGRY, SoundSource.HOSTILE, 1.5F, 0.7F);
        mob.getNavigation().stop();
        Vec3 aimAt = p.position();
        SkillTasks.later(16, () -> {
            if (!mob.isAlive()) return;
            Vec3 dir = new Vec3(aimAt.x - mob.getX(), 0, aimAt.z - mob.getZ()).normalize();
            charges++;
            SkillTasks.run(age -> {
                if (!mob.isAlive() || age > 18) return true;
                mob.setDeltaMovement(dir.x * 0.75, mob.getDeltaMovement().y, dir.z * 0.75);
                mob.setYRot((float) (Math.toDegrees(Math.atan2(-dir.x, dir.z))));
                if (age % 3 == 0) ServerFx.of("charge_dust", mob.position()).send(sl);
                for (Player hit : sl.getEntitiesOfClass(Player.class, mob.getBoundingBox().inflate(0.6), x -> !x.isCreative() && !x.isSpectator())) {
                    mob.doHurtTarget(sl, hit);
                    hit.push(dir.x * 1.4, 0.7, dir.z * 1.4);
                    return true;
                }
                return mob.horizontalCollision;
            });
        });
    }

    /** Magma cube: crouches, leaps at you in an arc and lands in a burst of fire. */
    private static void leap(ServerLevel sl, MagmaCube cube, Player p) {
        ServerFx.of("mob_warn", cube.position()).follow(cube).a(cube.getSize() * 0.6).b(8).color(0xFF7A20).send(sl);
        // magma cubes hop all the time: wait (briefly) until it is on the ground to take off
        SkillTasks.run(wait -> {
            if (!cube.isAlive() || wait > 30) return true;
            if (wait < 8 || !cube.onGround()) return false;
            Vec3 to = p.position().subtract(cube.position());
            double t = 18; // ticks in the air
            cube.setDeltaMovement(to.x / t * 1.15, 0.9, to.z / t * 1.15);
            sl.playSound(null, cube.getX(), cube.getY(), cube.getZ(), SoundEvents.MAGMA_CUBE_JUMP, SoundSource.HOSTILE, 1.5F, 0.6F);
            leaps++;
            SkillTasks.run(age -> {
                if (!cube.isAlive() || age > 60) return true;
                if (age < 4 || !cube.onGround()) return false;
                double radius = 2 + cube.getSize();
                ServerFx.of("fire_ring", cube.position()).a(radius).color(0xFF7A20).send(sl);
                sl.playSound(null, cube.getX(), cube.getY(), cube.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.6F, 1.6F);
                for (Player hit : sl.getEntitiesOfClass(Player.class, cube.getBoundingBox().inflate(radius), x -> !x.isCreative() && !x.isSpectator())) {
                    if (hit.distanceTo(cube) > radius) continue;
                    hit.hurtServer(sl, sl.damageSources().mobAttack(cube), 1.5F * cube.getSize());
                    hit.igniteForSeconds(3);
                }
                return true;
            });
            return true;
        });
    }
}
