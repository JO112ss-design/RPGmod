package com.jo112ss.rpgmod.job;

import static com.jo112ss.rpgmod.job.SkillExecutor.*;

import com.jo112ss.rpgmod.item.ModItems;
import com.jo112ss.rpgmod.item.WeaponMaterial;
import com.jo112ss.rpgmod.job.SkillExecutor.Ctx;
import com.jo112ss.rpgmod.job.fx.ServerFx;
import com.jo112ss.rpgmod.registry.ModEffects;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Skills of the pirate, lancer, monk and bard. Shares the helpers of {@link SkillExecutor}; the gun,
 * ki and songs live in {@link Pirate}, {@link Monk} and {@link Bard}, the lasting effects are read by
 * {@link NewJobHooks}.
 */
public final class NewJobSkills {
    private NewJobSkills() {}

    static final Set<String> IDS = Set.of(
            "boarding_shot", "quick_draw", "cannon", "powder_keg", "grapple", "rum", "broadside", "jolly_roger",
            "lance_charge", "spear_throw", "spear_sweep", "leap_thrust", "counter_stance", "rapid_thrust", "dragon_fang", "spear_rain",
            "combo", "ki_blast", "parry", "whirl_kick", "meditate", "shrink_step", "hundred_fists", "diamond_body",
            "war_song", "heal_song", "guard_song", "wind_song", "discord", "lullaby", "grand_concert", "finale",
            // the spellsinger's songs start the same performance (they were missing here, so they did nothing)
            "flame_melody", "haste_tune", "mana_chord", "seal_melody", "thunder_tune");

    static boolean has(String id) {
        return IDS.contains(id);
    }

    static boolean run(Ctx c) {
        return switch (c.def().id()) {
            // pirate
            case "boarding_shot" -> boardingShot(c);
            case "quick_draw" -> quickDraw(c);
            case "cannon" -> cannon(c);
            case "powder_keg" -> powderKeg(c);
            case "grapple" -> grapple(c);
            case "rum" -> rum(c);
            case "broadside" -> broadside(c);
            case "jolly_roger" -> jollyRoger(c);
            // lancer
            case "lance_charge" -> lanceCharge(c);
            case "spear_throw" -> spearThrow(c);
            case "spear_sweep" -> spearSweep(c);
            case "leap_thrust" -> leapThrust(c);
            case "counter_stance" -> counterStance(c);
            case "rapid_thrust" -> rapidThrust(c);
            case "dragon_fang" -> dragonFang(c);
            case "spear_rain" -> spearRain(c);
            // monk
            case "combo" -> combo(c);
            case "ki_blast" -> kiBlast(c);
            case "parry" -> parry(c);
            case "whirl_kick" -> whirlKick(c);
            case "meditate" -> meditate(c);
            case "shrink_step" -> shrinkStep(c);
            case "hundred_fists" -> hundredFists(c);
            case "diamond_body" -> diamondBody(c);
            // bard
            case "war_song", "heal_song", "guard_song", "wind_song", "discord", "lullaby",
                 "flame_melody", "haste_tune", "mana_chord", "seal_melody", "thunder_tune" -> Bard.start(c, (int) Math.round(c.def().duration()));
            case "grand_concert" -> grandConcert(c);
            case "finale" -> finale(c);
            default -> false;
        };
    }

    private static final int PIRATE = Pirate.COLOR, LANCE = 0xE0B070, KI = 0xFFC860, IRON = 0x404448;

    static Vec3 mid(LivingEntity e) {
        return e.position().add(0, e.getBbHeight() * 0.5, 0);
    }

    static LivingEntity nearest(List<LivingEntity> list, Vec3 to) {
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (LivingEntity e : list) {
            double d = e.position().distanceToSqr(to);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        return best;
    }

    /** A random spot within {@code r} of {@code o} - half the time next to one of the enemies there. */
    static Vec3 spotIn(Ctx c, Vec3 o, double r, List<LivingEntity> foes) {
        var rnd = c.p().getRandom();
        if (!foes.isEmpty() && rnd.nextBoolean()) {
            LivingEntity e = foes.get(rnd.nextInt(foes.size()));
            return e.position().add(rnd.nextGaussian() * 0.6, 0, rnd.nextGaussian() * 0.6);
        }
        double a = rnd.nextDouble() * Math.PI * 2, d = Math.sqrt(rnd.nextDouble()) * r;
        return o.add(Math.cos(a) * d, 0, Math.sin(a) * d);
    }

    /** Area blast (cannonballs, kegs): falls off to 60% at the edge, throws enemies out. */
    static List<LivingEntity> blast(Ctx c, Vec3 at, double r, float damage, int style, int color) {
        List<LivingEntity> hit = enemies(c.p(), at, r);
        for (LivingEntity e : hit) {
            double fall = 1 - 0.4 * Math.min(1, horizontal(e.position(), at) / r);
            hurt(c, e, (float) (damage * fall), melee(c));
            pushAway(e, at, 0.5, 0.35);
        }
        ServerFx.of("x_burst", at).a(r).b(style).color(color).points(hit).send(c.level());
        sound(c, at, SoundEvents.GENERIC_EXPLODE, 1.0F, 1.0F + c.p().getRandom().nextFloat() * 0.2F);
        return hit;
    }

    // ================================================================== pirate

    /** Lunge with the bayonet, then a shot point-blank; loads a round. */
    private static boolean boardingShot(Ctx c) {
        ServerPlayer p = c.p();
        Vec3 dir = c.flat();
        Vec3 from = p.position();
        launch(p, dir.scale(1.3).add(0, 0.15, 0));
        ServerFx.of("x_dash", from).vec(from.add(dir.scale(c.def().radius()))).color(PIRATE).send(c.level());
        sound(c, SoundEvents.PLAYER_ATTACK_SWEEP, 1.4F);
        SkillTasks.run(age -> {
            if (!p.isAlive()) return true;
            Vec3 front = p.position().add(dir.scale(0.9));
            LivingEntity t = nearest(enemies(p, front, 1.8), front);
            if (t == null && age < 8) return false;
            launch(p, dir.scale(0.05));
            if (t != null) {
                hurt(c, t, c.dmg(0.42), melee(c));
                ServerFx.of("x_hit", mid(t)).vec(dir).a(1.0).color(0xE0E8F0).send(c.level());
                sound(c, t.position(), SoundEvents.PLAYER_ATTACK_STRONG, 1.0F, 1.2F);
            }
            LivingEntity target = t;
            SkillTasks.later(4, () -> {
                if (!p.isAlive()) return;
                Vec3 aim = target != null && target.isAlive() ? mid(target).subtract(p.getEyePosition()) : p.getLookAngle();
                LivingEntity shot = Pirate.shot(p, c.dmg(0.58), aim, 8, 0);
                if (shot != null) push(shot, dir, 0.9, 0.3);
                Pirate.load(p, 1);
            });
            return true;
        });
        return true;
    }

    /** Fires every round left (at least three) in a burst. */
    private static boolean quickDraw(Ctx c) {
        ServerPlayer p = c.p();
        JobData d = JobManager.data(p);
        boolean endless = Pirate.flagUp(p);
        int shots = endless ? JobData.MAG : Math.max(3, d.ammo());
        if (!endless) {
            d.setAmmo(0);
            d.setReloadEnd(0);
            JobManager.sync(p);
        }
        SkillTasks.run(age -> {
            if (!p.isAlive()) return true;
            if (age % 2 == 0) {
                LivingEntity t = lookTarget(c, c.def().radius(), 8);
                Vec3 dir = t != null ? mid(t).subtract(p.getEyePosition()) : p.getLookAngle();
                Pirate.shot(p, c.dmg(1), dir, c.def().radius(), 0.015);
            }
            if (age >= (shots - 1) * 2) {
                if (!endless) Pirate.reload(p, JobManager.data(p));
                return true;
            }
            return false;
        });
        sound(c, SoundEvents.CROSSBOW_LOADING_END, 1.6F);
        return true;
    }

    /** A spot beside the player (to the right with {@code side} > 0), on the ground if there is any below. */
    static Vec3 beside(Ctx c, double side, double ahead) {
        ServerPlayer p = c.p();
        Vec3 want = p.position().add(Fx3.right(c.flat()).scale(side)).add(c.flat().scale(ahead));
        // not into a wall: stop short of the first block on the way
        BlockHitResult wall = c.level().clip(new ClipContext(p.position().add(0, 0.5, 0), want.add(0, 0.5, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        if (wall.getType() != HitResult.Type.MISS) want = p.position().add(wall.getLocation().subtract(p.position().add(0, 0.5, 0)).scale(0.7));
        BlockHitResult down = c.level().clip(new ClipContext(want.add(0, 1, 0), want.add(0, -4, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        return down.getType() == HitResult.Type.MISS ? want : down.getLocation();
    }

    /** Sets down a cannon beside you that shells the nearest enemy every 1.5s (0.75s under the flag). */
    private static boolean cannon(Ctx c) {
        placeCannon(c, beside(c, 1.6, 0.8), Pirate.CANNONS, 1);
        return true;
    }

    /**
     * A cannon at {@code at} (one per player in {@code store}) that shells the nearest enemy for the
     * skill's duration; {@code shells} per shot (the captain's twin cannon fires two).
     */
    static void placeCannon(Ctx c, Vec3 at, Map<UUID, SkillProp> store, int shells) {
        ServerPlayer p = c.p();
        SkillProp old = store.remove(p.getUUID());
        if (old != null) old.discard();
        int life = ticks(c.def().duration());
        SkillProp gun = SkillProp.create(c.level(), SkillProp.CANNON, at, p.getYRot(), life + 5);
        store.put(p.getUUID(), gun);
        ServerFx.of("x_ring", at).a(1.5).b(1).color(0xC0C0C0).send(c.level());
        sound(c, at, SoundEvents.ANVIL_LAND, 0.6F, 0.7F);
        sound(c, at, SoundEvents.CHAIN_PLACE, 1.0F, 0.8F);
        double range = c.def().radius();
        final int[] next = {10};
        SkillTasks.run(age -> {
            if (gun.isRemoved() || age >= life) {
                if (!gun.isRemoved()) {
                    ServerFx.of("x_burst", gun.position().add(0, 0.5, 0)).a(1).b(3).color(0x808080).send(c.level());
                    gun.discard();
                }
                store.remove(p.getUUID(), gun);
                return true;
            }
            LivingEntity t = Pirate.cannonTarget(p, gun, range);
            if (t != null && age % 2 == 0) gun.aimAt(mid(t));
            if (age >= next[0] && t != null) {
                next[0] = age + (Pirate.barrage(p) ? 6 : Pirate.flagUp(p) ? 15 : 30);
                for (int s = 0; s < shells; s++) {
                    Vec3 aim = mid(t).add(s == 0 ? Vec3.ZERO : new Vec3(p.getRandom().nextGaussian() * 0.8, 0, p.getRandom().nextGaussian() * 0.8));
                    Vec3 dir = aim.subtract(gun.position().add(0, 0.95, 0)).normalize();
                    Vec3 muzzle = gun.position().add(0, 0.95, 0).add(dir.scale(0.9));
                    LivingEntity follow = s == 0 ? t : null;
                    SkillTasks.later(s * 4, () -> {
                        if (gun.isRemoved()) return;
                        ServerFx.of("n_cannon_fire", muzzle).vec(dir).send(c.level());
                        ServerFx.of("x_bolt", muzzle).vec(dir.scale(1.4)).a(range + 2).b(0.32).color(0x303030).follow(follow).send(c.level());
                        sound(c, muzzle, SoundEvents.GENERIC_EXPLODE, 1.2F, 1.4F);
                        projectile(c, muzzle, dir, 1.4, range + 2, follow, 0.25, (e, hitAt, d) -> blast(c, hitAt, 2.0, c.dmg(1), 0, 0xFF8030),
                                (endAt, d, wall) -> blast(c, endAt, 2.0, c.dmg(1), 0, 0xFF8030));
                    });
                }
            }
            return false;
        });
    }

    /** Throws a keg that goes off after 2s, or as soon as it is shot (half again as strong). */
    private static boolean powderKeg(Ctx c) {
        ServerPlayer p = c.p();
        SkillProp keg = SkillProp.create(c.level(), SkillProp.KEG, c.hand(), p.getYRot(), 70);
        keg.setDeltaMovement(c.look().scale(0.75).add(0, 0.28, 0));
        ServerFx.of("n_fuse", keg.position()).follow(keg).b(40).send(c.level());
        sound(c, SoundEvents.TNT_PRIMED, 1.2F);
        sound(c, SoundEvents.PLAYER_ATTACK_SWEEP, 0.6F);
        final boolean[] done = {false};
        Runnable shotBoom = () -> {
            if (done[0]) return;
            done[0] = true;
            Vec3 at = keg.position().add(0, 0.4, 0);
            keg.discard();
            blast(c, at, c.def().radius() * 1.2, c.dmg(1.5), 0, 0xFFA040);
            sound(c, at, SoundEvents.GENERIC_EXPLODE, 1.6F, 0.7F);
        };
        Pirate.KEGS.put(keg.getId(), shotBoom);
        SkillTasks.later(40, () -> {
            Pirate.KEGS.remove(keg.getId());
            if (done[0]) return;
            done[0] = true;
            Vec3 at = keg.position().add(0, 0.4, 0);
            keg.discard();
            blast(c, at, c.def().radius(), c.dmg(1), 0, 0xFF8030);
            sound(c, at, SoundEvents.GENERIC_EXPLODE, 1.4F, 0.8F);
        });
        return true;
    }

    /** Pulls the targeted enemy in (or you to it, if it is huge); with no enemy, swings you to the aimed spot. */
    private static boolean grapple(Ctx c) {
        ServerPlayer p = c.p();
        double range = c.def().radius();
        LivingEntity t = lookTarget(c, range, 6);
        if (t != null) {
            Vec3 to = p.position().subtract(t.position());
            double dist = to.length();
            boolean big = t.getBbWidth() > 1.6 || t.getMaxHealth() > p.getMaxHealth() * 8;
            if (big) {
                launch(p, to.normalize().scale(-Math.min(2.2, dist * 0.2)).add(0, 0.4, 0));
            } else {
                push(t, to, Math.min(2.4, dist * 0.22), 0.35);
                t.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 2), p);
            }
            hurt(c, t, c.dmg(1), melee(c));
            ServerFx.of("n_chain", c.hand()).follow(p).a(t.getId()).b(8).send(c.level());
            ServerFx.of("x_hit", mid(t)).vec(to).a(0.8).color(0xD0D0D8).send(c.level());
            sound(c, SoundEvents.FISHING_BOBBER_RETRIEVE, 0.7F);
            sound(c, t.position(), SoundEvents.CHAIN_PLACE, 1.0F, 1.2F);
            return true;
        }
        Vec3 eye = p.getEyePosition();
        BlockHitResult hit = c.level().clip(new ClipContext(eye, eye.add(c.look().scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        if (hit.getType() == HitResult.Type.MISS) {
            fail(p, "rpgmod.job.no_target");
            return false;
        }
        Vec3 to = hit.getLocation().subtract(p.position());
        launch(p, to.normalize().scale(Math.min(2.0, 0.5 + to.length() * 0.1)).add(0, 0.45, 0));
        ServerFx.of("x_link", c.hand()).vec(hit.getLocation()).color(0xB0B0B8).send(c.level());
        sound(c, SoundEvents.FISHING_BOBBER_THROW, 0.7F);
        sound(c, hit.getLocation(), SoundEvents.CHAIN_PLACE, 1.0F, 1.0F);
        return true;
    }

    /** A swig: 15% health back and faster attacks. */
    private static boolean rum(Ctx c) {
        ServerPlayer p = c.p();
        HealthScale.heal(p, p.getMaxHealth() * 0.15F);
        apply(p, c.def(), p);
        ServerFx.of("x_aura", p.position()).follow(p).a(c.def().duration()).b(1).color(0xD08A40).send(c.level());
        sound(c, SoundEvents.GENERIC_DRINK, 0.9F);
        sound(c, p.position(), SoundEvents.PLAYER_BURP, 0.8F, 1.0F);
        return true;
    }

    /** Cannonballs rain on the aimed spot for 3s (half of them at the enemies there). */
    /**
     * Carpet bombardment: shells come over your head from the ships behind you and land in rows of
     * three, the first row in front of the aimed spot and every next one further on.
     */
    private static boolean broadside(Ctx c) {
        ServerPlayer p = c.p();
        Vec3 o = aimPoint(c, 28);
        double r = c.def().radius();
        Vec3 dir = c.flat(), side = Fx3.right(dir);
        int rows = 8;
        ServerFx.of("x_zone", o).a(r).b(rows * 4 + 24).vec(new Vec3(2, 4, 0)).color(0xFF6030).send(c.level());
        sound(c, SoundEvents.RAVAGER_ROAR, 1.6F);
        SkillTasks.run(age -> {
            if (age % 4 == 0 && age / 4 < rows) {
                int row = age / 4;
                Vec3 line = o.add(dir.scale(-r + row * (2 * r / (rows - 1))));
                for (int k = -1; k <= 1; k++) {
                    Vec3 at = line.add(side.scale(k * 2.4 + (p.getRandom().nextDouble() - 0.5) * 0.8));
                    Vec3 from = p.position().add(dir.scale(-7)).add(side.scale(k * 3)).add(0, 9, 0);
                    shell(c, from, ground(c, at), 14, 2.2, c.dmg(1));
                }
                sound(c, p.position().add(dir.scale(-6)).add(0, 6, 0), SoundEvents.GENERIC_EXPLODE, 0.7F, 0.7F);
            }
            return age >= rows * 4;
        });
        return true;
    }

    /** The ground under a spot (or the spot). */
    static Vec3 ground(Ctx c, Vec3 at) {
        BlockHitResult down = c.level().clip(new ClipContext(at.add(0, 3, 0), at.add(0, -8, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, c.p()));
        return down.getType() == HitResult.Type.MISS ? at : down.getLocation();
    }

    /** A cannonball arcing from {@code from} to {@code to} in {@code flight} ticks, then a blast there. */
    static void shell(Ctx c, Vec3 from, Vec3 to, int flight, double r, float damage) {
        ServerFx.of("n_shell", from).vec(to).b(flight).a(0.4).send(c.level());
        SkillTasks.later(flight, () -> blast(c, to, r, damage, 0, 0xFF8030));
    }

    /** Plants the flag: allies near it hit 20% harder; your cannons fire twice as fast and the gun never runs dry. */
    private static boolean jollyRoger(Ctx c) {
        ServerPlayer p = c.p();
        int life = ticks(c.def().duration());
        Vec3 at = beside(c, -1.3, 0.3);
        SkillProp flag = SkillProp.create(c.level(), SkillProp.FLAG, at, p.getYRot() + 90, life);
        Pirate.FLAG_UNTIL.put(p.getUUID(), c.level().getGameTime() + life);
        double r = c.def().radius();
        ServerFx.of("x_ring", at).a(r).b(2).color(PIRATE).send(c.level());
        sound(c, SoundEvents.RAVAGER_ROAR, 1.2F);
        sound(c, at, SoundEvents.WOOL_PLACE, 1.0F, 0.8F);
        SkillTasks.run(age -> {
            if (flag.isRemoved()) return true;
            if (age % 20 == 0) {
                for (ServerPlayer a : Pirate.aroundFlag(c.level(), at, r)) {
                    a.addEffect(new MobEffectInstance(ModEffects.JOLLY_ROGER, 30, 0, true, true), p);
                }
                ServerFx.of("x_ring", at).a(r).b(1).color(0x203040).send(c.level());
            }
            return age >= life;
        });
        return true;
    }

    // ================================================================== lancer

    /** A long straight charge that skewers everything on the way. */
    /** Lance charge: the run speeds up from this to the top speed (blocks a tick) over the charge. */
    static final double LANCE_START = 0.4, LANCE_TOP = 1.4;

    /**
     * Charges straight ahead, faster and faster. Enemies in the way are skewered and carried along
     * on the spear (up to three); at the end they are flung off, harder the faster you were going.
     * Run them into a wall and they take a heavy extra hit and are stunned.
     */
    private static boolean lanceCharge(Ctx c) {
        ServerPlayer p = c.p();
        Vec3 dir = c.flat();
        int ticks = 12;
        ServerFx.of("n_rush", p.position()).follow(p).vec(dir).a(ticks - 2).b(ticks + 2).color(LANCE).send(c.level());
        sound(c, SoundEvents.SPEAR_USE, 0.9F);
        sound(c, p.position(), SoundEvents.BREEZE_JUMP, 0.8F, 1.0F);
        charge(c, dir, ticks, age -> Math.min(LANCE_TOP, LANCE_START + 0.09 * age), 1.7, 3, c.dmg(0.5), (carried, wall, v) -> {
            float fast = (float) (v / LANCE_TOP);
            for (LivingEntity e : carried) {
                if (!e.isAlive()) continue;
                if (wall) {
                    hurt(c, e, c.dmg(1.3), melee(c));
                    stun(c, e, 40);
                    ServerFx.of("x_burst", mid(e)).a(1.4).b(3).color(LANCE).send(c.level());
                } else {
                    hurt(c, e, c.dmg(0.4 + 0.6 * fast), melee(c));
                    push(e, dir, 0.6 + 1.4 * fast, 0.45);
                    ServerFx.of("n_thrown", e.position()).follow(e).b(12).color(LANCE).send(c.level());
                }
            }
            if (wall) {
                launch(p, dir.scale(-0.35).add(0, 0.2, 0));
                sound(c, p.position(), SoundEvents.ANVIL_LAND, 0.8F, 0.7F);
                sound(c, p.position(), SoundEvents.SPEAR_HIT, 1.2F, 0.6F);
            } else if (!carried.isEmpty()) {
                sound(c, p.position(), SoundEvents.SPEAR_HIT, 1.0F, 1.3F);
            }
        });
        return true;
    }

    /** How a charge ended: who was carried, whether it ran into a wall, how fast it was going. */
    @FunctionalInterface
    interface ChargeEnd {
        void at(List<LivingEntity> carried, boolean wall, double speed);
    }

    /**
     * Runs the player straight along {@code dir} for {@code ticks} at {@code speed(age)} blocks a
     * tick. Enemies met in front (within {@code catchR}) take {@code pick} and are carried along in
     * front (up to {@code max}); it ends early if they or the player hit a wall.
     */
    static void charge(Ctx c, Vec3 dir, int ticks, java.util.function.IntToDoubleFunction speed, double catchR, int max, float pick, ChargeEnd end) {
        ServerPlayer p = c.p();
        List<LivingEntity> carried = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        Vec3 side = Fx3.right(dir);
        SkillTasks.run(age -> {
            if (!p.isAlive()) return true;
            double v = speed.applyAsDouble(age);
            Vec3 front = p.position().add(dir.scale(1.1));
            for (LivingEntity e : enemies(p, front, catchR)) {
                if (carried.size() >= max || !seen.add(e.getId())) continue;
                hurt(c, e, pick, melee(c));
                carried.add(e);
                ServerFx.of("x_hit", mid(e)).vec(dir).a(1.0).color(0xFFFFFF).send(c.level());
                sound(c, e.position(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.9F, 0.8F);
            }
            carried.removeIf(e -> !e.isAlive());
            boolean wall = !c.level().noCollision(p, p.getBoundingBox().move(dir.scale(0.7)).deflate(0.05).move(0, 0.1, 0));
            for (LivingEntity e : carried) {
                if (!c.level().noCollision(e, e.getBoundingBox().move(dir.scale(Math.max(0.7, v))).deflate(0.05).move(0, 0.1, 0))) wall = true;
            }
            if (wall || age >= ticks) {
                end.at(carried, wall, v);
                return true;
            }
            launch(p, dir.scale(v).add(0, Math.min(0, p.getDeltaMovement().y), 0));
            for (int i = 0; i < carried.size(); i++) {
                LivingEntity e = carried.get(i);
                double off = (i - (carried.size() - 1) / 2.0) * 0.8;
                // where the player will be next tick, plus a spear's (or shield's) length
                Vec3 at = p.position().add(dir.scale(v + 1.0 + e.getBbWidth() / 2)).add(side.scale(off));
                e.setPos(at.x, p.getY(), at.z);
                e.setDeltaMovement(Vec3.ZERO); // moved here; its own physics should not move it again
                e.resetFallDistance();
            }
            return false;
        });
    }

    /** Stunned: can hardly move for a while (and the stars over its head say so). */
    static void stun(Ctx c, LivingEntity e, int ticks) {
        e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, 9, false, false), c.p());
        if (e instanceof net.minecraft.world.entity.Mob m) m.getNavigation().stop();
        ServerFx.of("n_stun", e.position()).follow(e).b(ticks).send(c.level());
    }

    /** Moves the player to a spot at once, facing {@code look} (a point). */
    static void blinkTo(Ctx c, Vec3 to, Vec3 look) {
        ServerPlayer p = c.p();
        Vec3 d = look.subtract(to);
        float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90);
        p.teleportTo(c.level(), to.x, to.y, to.z, java.util.Set.of(), yaw, p.getXRot(), false);
        p.resetFallDistance();
    }

    /** Whether the player fits at a spot (standing there). */
    static boolean fits(Ctx c, Vec3 at) {
        ServerPlayer p = c.p();
        return c.level().noCollision(p, p.getBoundingBox().move(at.subtract(p.position())).deflate(0.02).move(0, 0.05, 0));
    }

    /** Throws the spear through a line of enemies; it flies back to you (hitting again on the way, at half). */
    private static boolean spearThrow(Ctx c) {
        ServerPlayer p = c.p();
        Vec3 dir = c.look();
        Vec3 start = c.hand();
        double range = c.def().radius(), speed = 1.6;
        WeaponMaterial mat = ModItems.materialOf(p.getMainHandItem().getItem());
        SkillProp spear = SkillProp.create(c.level(), SkillProp.SPEAR, start, p.getYRot(), 120);
        spear.setVariant(mat != null ? mat.ordinal() : 1);
        spear.aimAt(start.add(dir));
        sound(c, SoundEvents.TRIDENT_THROW, 1.0F);
        Set<Integer> out = new HashSet<>(), back = new HashSet<>();
        final Vec3[] pos = {start};
        final double[] gone = {0};
        final boolean[] returning = {false};
        SkillTasks.run(age -> {
            if (spear.isRemoved() || age > 100) {
                spear.discard();
                return true;
            }
            Vec3 from = pos[0];
            Vec3 to;
            if (!returning[0]) {
                to = from.add(dir.scale(speed));
                BlockHitResult block = c.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
                if (block.getType() != HitResult.Type.MISS) {
                    to = block.getLocation();
                    returning[0] = true;
                    sound(c, to, SoundEvents.TRIDENT_HIT, 0.8F, 1.0F);
                }
                gone[0] += speed;
                if (gone[0] >= range) returning[0] = true;
            } else {
                Vec3 home = c.hand();
                Vec3 d = home.subtract(from);
                if (d.length() < 1.5) {
                    spear.discard();
                    sound(c, SoundEvents.TRIDENT_RETURN, 1.2F);
                    return true;
                }
                to = from.add(d.normalize().scale(Math.min(d.length(), 1.9)));
            }
            Vec3 a = from, b = to;
            for (LivingEntity e : c.level().getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(a, b).inflate(0.8), e -> isEnemy(p, e))) {
                if (e.getBoundingBox().inflate(0.4).clip(a, b).isEmpty() && !e.getBoundingBox().inflate(0.4).contains(b)) continue;
                if (!(returning[0] ? back : out).add(e.getId())) continue;
                hurt(c, e, c.dmg(returning[0] ? 0.5 : 1), melee(c));
                ServerFx.of("x_hit", mid(e)).vec(b.subtract(a)).a(0.9).color(LANCE).send(c.level());
                sound(c, e.position(), SoundEvents.SPEAR_HIT, 0.9F, 1.1F);
            }
            spear.setPos(b.x, b.y - 0.4, b.z);
            spear.aimAt(b.add(b.subtract(a)));
            pos[0] = b;
            return false;
        });
        return true;
    }

    /** A wide arc in front that throws enemies back. */
    private static boolean spearSweep(Ctx c) {
        double r = c.def().radius();
        List<LivingEntity> hit = cone(c, r, 65);
        for (LivingEntity e : hit) {
            hurt(c, e, c.dmg(1), melee(c));
            pushAway(e, c.p().position(), 1.0, 0.3);
        }
        ServerFx.of("x_slash", c.chest().add(c.flat().scale(1.5))).vec(c.flat()).a(r * 1.1).b(0.1).color(LANCE).points(hit).send(c.level());
        ServerFx.of("power_slash", c.chest()).vec(c.flat()).a(r).points(hit).send(c.level());
        sound(c, SoundEvents.PLAYER_ATTACK_SWEEP, 0.7F);
        sound(c, c.p().position(), SoundEvents.SPEAR_ATTACK, 1.0F, 0.8F);
        return true;
    }

    /** Players whose next landing does not hurt (leaps). */
    static final Set<UUID> SOFT_LANDING = new HashSet<>();

    /** Leap thrust: the shockwave from the landing runs this far ahead, in this many ticks. */
    static final double LEAP_WAVE = 8;
    static final int LEAP_WAVE_TICKS = 8;

    /**
     * Plants the spear and vaults over it onto the aimed spot. Landing strikes the spot and sends a
     * shockwave running straight on along the ground, throwing up whoever it passes.
     */
    private static boolean leapThrust(Ctx c) {
        ServerPlayer p = c.p();
        Vec3 target = aimPoint(c, 12);
        Vec3 d = target.subtract(p.position());
        Vec3 flat = new Vec3(d.x, 0, d.z);
        Vec3 dir = flat.lengthSqr() > 0.01 ? flat.normalize() : c.flat();
        // the pole: the spear planted a step ahead, leaning back towards you, left standing a moment
        WeaponMaterial mat = ModItems.materialOf(p.getMainHandItem().getItem());
        Vec3 plant = ground(c, p.position().add(dir.scale(1.4)));
        SkillProp pole = SkillProp.create(c.level(), SkillProp.SPEAR, plant.add(dir.scale(-0.9)).add(0, 1.4, 0), p.getYRot(), 16);
        pole.setVariant(mat != null ? mat.ordinal() : 1);
        pole.aimAt(plant.add(0, -0.6, 0));
        // players lose 9% of their speed in the air each tick: over the ~16 tick flight, distance = 8.5 x speed
        Vec3 v = flat.scale(1 / 8.5).add(0, Math.max(0.7, 0.75 + d.y * 0.06), 0);
        launch(p, v);
        SOFT_LANDING.add(p.getUUID());
        sound(c, SoundEvents.SPEAR_USE, 1.0F);
        sound(c, p.position(), SoundEvents.BREEZE_JUMP, 1.0F, 1.1F);
        SkillTasks.run(age -> {
            if (!p.isAlive()) return true;
            p.resetFallDistance();
            if (age < 4 || (!p.onGround() && age < 40)) return false;
            Vec3 o = p.position();
            double r = c.def().radius();
            List<LivingEntity> hit = enemies(p, o, r);
            for (LivingEntity e : hit) hurt(c, e, c.dmg(0.6), melee(c));
            ServerFx.of("x_ring", o).a(r * 1.2).b(1).color(LANCE).points(hit).send(c.level());
            ServerFx.of("n_ground_wave", o).vec(dir.scale(LEAP_WAVE)).b(LEAP_WAVE_TICKS).color(LANCE).send(c.level());
            sound(c, o, SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.0F, 1.2F);
            sound(c, o, SoundEvents.SPEAR_HIT, 1.0F, 0.8F);
            // the shockwave
            Set<Integer> done = new HashSet<>();
            for (LivingEntity e : hit) done.add(e.getId());
            SkillTasks.run(w -> {
                double reach = LEAP_WAVE * (w + 1) / LEAP_WAVE_TICKS;
                Vec3 front = o.add(dir.scale(reach));
                for (LivingEntity e : enemies(p, front, 1.6)) {
                    if (!done.add(e.getId())) continue;
                    hurt(c, e, c.dmg(1), melee(c));
                    airborne(c, e, 0.55);
                    ServerFx.of("x_hit", mid(e)).vec(new Vec3(0, 1, 0)).a(1.0).color(LANCE).send(c.level());
                }
                if (w % 2 == 0) sound(c, front, SoundEvents.ROOTED_DIRT_BREAK, 0.8F, 0.7F);
                return w + 1 >= LEAP_WAVE_TICKS;
            });
            SkillTasks.later(5, () -> SOFT_LANDING.remove(p.getUUID()));
            return true;
        });
        return true;
    }

    /**
     * Throws an enemy straight up (a starting speed, blocks a tick) and lets it fall back, driving
     * it here so it works on any mob (knockback resistance makes it go less high).
     */
    static void airborne(Ctx c, LivingEntity e, double vy) {
        double resist = 1 - e.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE);
        if (resist <= 0.05) return;
        final double[] v = {vy * resist};
        SkillTasks.run(age -> {
            if (!e.isAlive() || age > 60) return true;
            double dy = v[0];
            if (!c.level().noCollision(e, e.getBoundingBox().move(0, dy, 0))) {
                if (dy > 0) {
                    v[0] = 0;
                    return false;
                }
                // down onto the ground: as far as it goes
                for (double s = dy + 0.1; s < 0; s += 0.1) {
                    if (c.level().noCollision(e, e.getBoundingBox().move(0, s, 0))) {
                        e.setPos(e.getX(), e.getY() + s, e.getZ());
                        break;
                    }
                }
                e.setDeltaMovement(Vec3.ZERO);
                return true;
            }
            e.setPos(e.getX(), e.getY() + dy, e.getZ());
            e.setDeltaMovement(Vec3.ZERO); // moved here; its own physics should not move it again
            e.resetFallDistance();
            v[0] = Math.max(-1.0, v[0] - 0.08);
            return false;
        });
    }

    /** Counter stance: the damage a counter does, set when it was used. */
    static final Map<UUID, Float> COUNTER_POWER = new HashMap<>();

    /** Braced for 2s: half damage taken; enemies that come close or strike are hit back (once each). */
    private static boolean counterStance(Ctx c) {
        ServerPlayer p = c.p();
        int t = ticks(c.def().duration());
        p.addEffect(new MobEffectInstance(ModEffects.COUNTER_STANCE, t, 0), p);
        p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, t, 2, false, false), p);
        COUNTER_POWER.put(p.getUUID(), c.dmg(1));
        ServerFx.of("x_aura", p.position()).follow(p).a(c.def().duration()).b(2).color(LANCE).send(c.level());
        sound(c, SoundEvents.SHIELD_BLOCK, 0.7F);
        Set<Integer> done = new HashSet<>();
        SkillTasks.run(age -> {
            if (!p.isAlive() || !p.hasEffect(ModEffects.COUNTER_STANCE)) return true;
            for (LivingEntity e : enemies(p, p.position(), 2.8)) {
                if (!done.add(e.getId())) continue;
                counter(p, c.level(), e);
            }
            return age >= t;
        });
        return true;
    }

    /** One counter thrust at {@code e} (stance and its hook). */
    static void counter(ServerPlayer p, ServerLevel level, LivingEntity e) {
        float dmg = COUNTER_POWER.getOrDefault(p.getUUID(), 0F);
        if (dmg <= 0) return;
        e.hurtServer(level, p.damageSources().source(SKILL_DAMAGE, p, p), dmg);
        pushAway(e, p.position(), 0.9, 0.3);
        ServerFx.of("x_beam", p.position().add(0, 1.2, 0)).vec(mid(e)).a(0.18).color(LANCE).send(level);
        ServerFx.of("x_hit", mid(e)).vec(e.position().subtract(p.position())).a(1.1).color(LANCE).send(level);
        level.playSound(null, p.blockPosition(), SoundEvents.SPEAR_ATTACK.value(), net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.3F);
    }

    /** Five quick thrusts straight ahead. */
    private static boolean rapidThrust(Ctx c) {
        ServerPlayer p = c.p();
        double r = c.def().radius();
        SkillTasks.run(age -> {
            if (!p.isAlive()) return true;
            if (age % 3 == 0) {
                List<LivingEntity> hit = cone(c, r, 18);
                for (LivingEntity e : hit) {
                    hurt(c, e, c.dmg(1), melee(c));
                    push(e, c.flat(), 0.15, 0.02);
                }
                Vec3 from = c.hand();
                Vec3 side = Fx3.right(c.look()).scale((age / 3 % 2 == 0 ? 1 : -1) * 0.25);
                ServerFx.of("x_beam", from.add(side)).vec(from.add(side).add(c.look().scale(r))).a(0.12).color(0xFFF0D0).points(hit).send(c.level());
                sound(c, p.position(), SoundEvents.SPEAR_ATTACK, 0.8F, 1.2F + age * 0.03F);
            }
            return age >= 12;
        });
        return true;
    }

    /**
     * Jumps straight up, then comes down spinning the spear round; the landing smashes the ground
     * and throws every enemy around high into the air (the nearer, the harder it is hit).
     */
    private static boolean dragonFang(Ctx c) {
        ServerPlayer p = c.p();
        int apex = 12;
        launch(p, new Vec3(0, 1.35, 0));
        SOFT_LANDING.add(p.getUUID());
        ServerFx.of("x_aura", p.position()).follow(p).a(1.5).b(3).color(0xFF9030).send(c.level());
        sound(c, SoundEvents.ENDER_DRAGON_FLAP, 1.0F);
        sound(c, p.position(), SoundEvents.BREEZE_JUMP, 1.0F, 0.6F);
        SkillTasks.run(age -> {
            if (!p.isAlive()) return true;
            p.resetFallDistance();
            if (age == apex) {
                launch(p, new Vec3(0, -1.8, 0));
                ServerFx.of("n_spin", p.position()).follow(p).b(14).color(0xFF9030).send(c.level());
                sound(c, p.position(), SoundEvents.TRIDENT_RIPTIDE_3, 1.0F, 0.8F);
            }
            if (age <= apex + 1 || (!p.onGround() && age < 50)) return false;
            double r = c.def().radius();
            Vec3 o = p.position();
            List<LivingEntity> hit = enemies(p, o, r);
            for (LivingEntity e : hit) {
                double near = 1 - Math.min(1, horizontal(e.position(), o) / r);
                hurt(c, e, c.dmg(0.6 + 0.4 * near), melee(c));
                airborne(c, e, 0.6 + 0.5 * near);
            }
            ServerFx.of("earthshatter", o).a(r).send(c.level());
            ServerFx.of("x_burst", o.add(0, 0.3, 0)).a(r * 0.6).b(0).color(0xFF9030).points(hit).send(c.level());
            ServerFx.of("x_spikes", o).vec(c.flat()).a(r).color(LANCE).send(c.level());
            sound(c, o, SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.6F, 0.6F);
            sound(c, o, SoundEvents.GENERIC_EXPLODE, 1.0F, 0.7F);
            SkillTasks.later(5, () -> SOFT_LANDING.remove(p.getUUID()));
            return true;
        });
        return true;
    }

    /** Spears rain on the aimed area for 3s. */
    /** Spears in the ground at once, from a thousand spears, burst this hard each (up to four on one enemy). */
    static final double SPEAR_BURST = 0.6;

    /**
     * Spears rain on the aimed area (one every 2 ticks for 3s) and stay stuck in the ground where
     * they land; when the rain is over they all burst at once.
     */
    private static boolean spearRain(Ctx c) {
        ServerPlayer p = c.p();
        Vec3 o = aimPoint(c, 24);
        double r = c.def().radius();
        int life = ticks(c.def().duration());
        WeaponMaterial mat = ModItems.materialOf(p.getMainHandItem().getItem());
        List<SkillProp> stuck = new ArrayList<>();
        ServerFx.of("x_zone", o).a(r).b(life + 30).vec(new Vec3(0, 0, 0)).color(LANCE).send(c.level());
        sound(c, SoundEvents.TRIDENT_THROW, 0.7F);
        SkillTasks.run(age -> {
            if (age % 2 == 0 && age < life) {
                Vec3 at = ground(c, spotIn(c, o, r, enemies(p, o, r)));
                Vec3 tilt = new Vec3(p.getRandom().nextGaussian() * 0.25, 0, p.getRandom().nextGaussian() * 0.25);
                Vec3 top = at.add(tilt.scale(-12)).add(0, 9, 0);
                SkillProp spear = SkillProp.create(c.level(), SkillProp.SPEAR, top, p.getYRot(), life + 60);
                spear.setVariant(mat != null ? mat.ordinal() : 1);
                spear.aimAt(at.add(0, -1, 0).add(tilt));
                stuck.add(spear);
                // falls in 6 ticks, then stays stuck with its head in the ground
                for (int t = 1; t <= 6; t++) {
                    double f = t / 6.0;
                    Vec3 v = top.add(at.subtract(top).scale(f)).add(0, -0.5 * f, 0);
                    int tt = t;
                    SkillTasks.later(t, () -> {
                        spear.setPos(v.x, v.y, v.z);
                        if (tt < 6) return;
                        for (LivingEntity e : enemies(p, at, 1.3)) hurt(c, e, c.dmg(1), melee(c));
                        ServerFx.of("x_hit", at.add(0, 0.2, 0)).vec(new Vec3(0, -1, 0)).a(0.7).color(LANCE).send(c.level());
                        sound(c, at, SoundEvents.SPEAR_HIT, 0.5F, 0.9F + p.getRandom().nextFloat() * 0.4F);
                    });
                }
                if (age % 6 == 0) sound(c, o, SoundEvents.ARROW_SHOOT, 0.8F, 0.6F);
            }
            if (age == life + 16) { // all of them burst
                java.util.Map<LivingEntity, Integer> count = new java.util.HashMap<>();
                for (SkillProp s : stuck) {
                    Vec3 at = s.position();
                    for (LivingEntity e : enemies(p, at, 1.6)) count.merge(e, 1, Integer::sum);
                    ServerFx.of("x_burst", at.add(0, 0.4, 0)).a(1).b(0).color(0xFFB060).send(c.level());
                    s.discard();
                }
                count.forEach((e, n) -> hurt(c, e, c.dmg(SPEAR_BURST) * Math.min(4, n), melee(c)));
                ServerFx.of("x_ring", o).a(r).b(2).color(LANCE).points(new ArrayList<>(count.keySet())).send(c.level());
                sound(c, o, SoundEvents.GENERIC_EXPLODE, 1.4F, 1.1F);
                return true;
            }
            return false;
        });
        return true;
    }

    // ================================================================== monk

    private static void punchFx(Ctx c, List<LivingEntity> hit, int color) {
        Vec3 front = c.chest().add(c.flat().scale(1.6));
        ServerFx.of("n_fist", front).vec(c.flat()).color(color).points(hit).send(c.level());
    }

    /** Three quick punches; each one that lands builds ki. */
    private static boolean combo(Ctx c) {
        ServerPlayer p = c.p();
        SkillTasks.run(age -> {
            if (!p.isAlive()) return true;
            if (age % 4 == 0) {
                List<LivingEntity> hit = cone(c, c.def().radius(), 50);
                for (LivingEntity e : hit) {
                    hurt(c, e, c.dmg(1), melee(c));
                    push(e, c.flat(), age == 8 ? 0.7 : 0.15, age == 8 ? 0.25 : 0.02);
                }
                if (!hit.isEmpty()) Monk.add(p, 1);
                launch(p, c.flat().scale(0.3).add(0, p.getDeltaMovement().y, 0));
                punchFx(c, hit, KI);
                sound(c, p.position(), SoundEvents.PLAYER_ATTACK_STRONG, 0.8F, 1.1F + age * 0.04F);
            }
            return age >= 8;
        });
        return true;
    }

    /** Spends all ki on one strike: +80% per point. */
    private static boolean kiBlast(Ctx c) {
        ServerPlayer p = c.p();
        LivingEntity t = nearest(cone(c, c.def().radius(), 45), p.position());
        if (t == null) {
            fail(p, "rpgmod.job.no_target");
            return false;
        }
        int ki = Monk.spend(p);
        double mult = 1 + 0.8 * ki;
        hurt(c, t, c.dmg(mult), melee(c));
        push(t, c.flat(), 0.8 + 0.25 * ki, 0.3);
        for (LivingEntity e : enemies(p, t.position(), 1.8)) {
            if (e != t) hurt(c, e, c.dmg(mult * 0.5), melee(c));
        }
        ServerFx.of("x_burst", mid(t)).a(1 + ki * 0.4).b(3).color(KI).send(c.level());
        ServerFx.of("x_hit", mid(t)).vec(c.flat()).a(1 + ki * 0.3).color(0xFFF0C0).send(c.level());
        sound(c, t.position(), SoundEvents.PLAYER_ATTACK_STRONG, 1.2F, 0.6F);
        if (ki >= 3) sound(c, t.position(), SoundEvents.WARDEN_SONIC_BOOM, 0.5F, 1.6F);
        return true;
    }

    /** The parry's counter damage, set when it was used. */
    static final Map<UUID, Float> PARRY_POWER = new HashMap<>();

    private static boolean parry(Ctx c) {
        ServerPlayer p = c.p();
        p.addEffect(new MobEffectInstance(ModEffects.PARRY, ticks(c.def().duration()), 0), p);
        PARRY_POWER.put(p.getUUID(), c.dmg(1));
        ServerFx.of("x_aura", p.position()).follow(p).a(c.def().duration()).b(0).color(0xFFE0A0).send(c.level());
        sound(c, SoundEvents.ARMOR_EQUIP_LEATHER, 1.4F);
        return true;
    }

    /** A spinning kick: knocks everything around away; ki for each hit (up to 2). */
    private static boolean whirlKick(Ctx c) {
        ServerPlayer p = c.p();
        double r = c.def().radius();
        List<LivingEntity> hit = enemies(p, p.position(), r);
        for (LivingEntity e : hit) {
            hurt(c, e, c.dmg(1), melee(c));
            pushAway(e, p.position(), 1.0, 0.35);
        }
        Monk.add(p, Math.min(2, hit.size()));
        ServerFx.of("whirlwind", p.position()).follow(p).a(r).send(c.level());
        ServerFx.of("x_ring", p.position()).a(r).b(1).color(KI).points(hit).send(c.level());
        sound(c, SoundEvents.PLAYER_ATTACK_SWEEP, 1.2F);
        sound(c, p.position(), SoundEvents.BREEZE_WIND_CHARGE_BURST, 0.6F, 1.5F);
        return true;
    }

    /** Stand still for 3s: 8% health a second, and full ki at the end. */
    private static boolean meditate(Ctx c) {
        ServerPlayer p = c.p();
        int t = ticks(c.def().duration());
        p.addEffect(new MobEffectInstance(ModEffects.MEDITATE, t, 0), p);
        ServerFx.of("x_aura", p.position()).follow(p).a(c.def().duration()).b(0).color(0xFFE6A0).send(c.level());
        ServerFx.of("x_zone", p.position()).a(1.6).b(t).vec(new Vec3(1, 3, 0)).color(0xFFE6A0).send(c.level());
        sound(c, SoundEvents.BEACON_POWER_SELECT, 0.8F);
        SkillTasks.run(age -> {
            if (!p.isAlive()) return true;
            if (age % 20 == 19) {
                HealthScale.heal(p, p.getMaxHealth() * 0.08F);
                Monk.add(p, 2);
                sound(c, p.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.6F, 1.0F + age * 0.01F);
            }
            if (age >= t - 1) {
                Monk.add(p, JobManager.data(p).maxKi());
                return true;
            }
            return false;
        });
        return true;
    }

    /** A blink-fast step forward, untouchable, striking what you pass. */
    /** Shrink step: hitting from behind does this much more. */
    static final double BACK_HIT = 1.3;

    /**
     * Blinks behind the enemy you are looking at (or the nearest one ahead) and strikes its back,
     * an afterimage left where you stood.
     */
    private static boolean shrinkStep(Ctx c) {
        ServerPlayer p = c.p();
        double range = c.def().radius() + 2;
        LivingEntity t = lookTarget(c, range, 25);
        if (t == null) {
            fail(p, "rpgmod.job.no_target");
            return false;
        }
        Vec3 from = p.position();
        Vec3 through = t.position().subtract(from).multiply(1, 0, 1).normalize();
        Vec3 spot = null;
        for (double turn : new double[] {0, 0.6, -0.6, 1.2, -1.2}) {
            Vec3 at = t.position().add(Fx3.rotY(through, turn).scale(t.getBbWidth() / 2 + 0.9));
            if (fits(c, at)) {
                spot = at;
                break;
            }
        }
        if (spot == null) {
            fail(p, "rpgmod.job.no_room_behind");
            return false;
        }
        p.addEffect(new MobEffectInstance(ModEffects.UNTOUCHABLE, 10, 0, false, false, true), p);
        blinkTo(c, spot, t.position());
        ServerFx.of("n_blink", from).vec(spot).color(KI).send(c.level());
        sound(c, from, SoundEvents.BREEZE_JUMP, 0.8F, 1.8F);
        Vec3 landed = spot;
        SkillTasks.later(2, () -> {
            if (!t.isAlive()) return;
            Vec3 hitDir = t.position().subtract(landed);
            hurt(c, t, c.dmg(BACK_HIT), melee(c));
            push(t, hitDir, 0.7, 0.3);
            ServerFx.of("n_fist", mid(t).subtract(hitDir.normalize().scale(0.4))).vec(hitDir).color(KI).send(c.level());
            ServerFx.of("x_hit", mid(t)).vec(hitDir).a(1.3).color(KI).send(c.level());
            sound(c, t.position(), SoundEvents.PLAYER_ATTACK_CRIT, 1.0F, 0.9F);
            Monk.add(p, 1);
        });
        return true;
    }

    /** A storm of punches in front for 3s (one every 3 ticks). */
    private static boolean hundredFists(Ctx c) {
        ServerPlayer p = c.p();
        int life = ticks(c.def().duration());
        final int[] landed = {0};
        sound(c, SoundEvents.RAVAGER_ROAR, 1.8F);
        SkillTasks.run(age -> {
            if (!p.isAlive()) return true;
            if (age % 3 == 0) {
                List<LivingEntity> hit = cone(c, c.def().radius(), 45);
                for (LivingEntity e : hit) {
                    hurt(c, e, c.dmg(1), melee(c));
                    push(e, c.flat(), 0.06, 0.02);
                }
                if (!hit.isEmpty() && ++landed[0] % 5 == 0) Monk.add(p, 1);
                punchFx(c, hit, age % 6 == 0 ? KI : 0xFFFFFF);
                sound(c, p.position(), SoundEvents.PLAYER_ATTACK_WEAK, 0.7F, 0.9F + c.p().getRandom().nextFloat() * 0.6F);
            }
            if (age == life) {
                List<LivingEntity> hit = cone(c, c.def().radius(), 45);
                for (LivingEntity e : hit) {
                    hurt(c, e, c.dmg(3), melee(c));
                    push(e, c.flat(), 1.2, 0.4);
                }
                ServerFx.of("x_burst", c.chest().add(c.flat().scale(1.8))).a(1.6).b(3).color(KI).points(hit).send(c.level());
                sound(c, p.position(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.2F, 0.6F);
            }
            return age >= life;
        });
        return true;
    }

    private static boolean diamondBody(Ctx c) {
        ServerPlayer p = c.p();
        p.addEffect(new MobEffectInstance(ModEffects.DIAMOND_BODY, ticks(c.def().duration()), 0), p);
        ServerFx.of("x_aura", p.position()).follow(p).a(c.def().duration()).b(2).color(0xFFD27A).send(c.level());
        ServerFx.of("x_burst", p.position().add(0, 1, 0)).a(1.5).b(2).color(0xFFD27A).send(c.level());
        sound(c, SoundEvents.ANVIL_LAND, 0.6F);
        sound(c, p.position(), SoundEvents.BEACON_ACTIVATE, 0.8F, 1.6F);
        return true;
    }

    // ================================================================== bard

    private static boolean grandConcert(Ctx c) {
        ServerPlayer p = c.p();
        int life = ticks(c.def().duration());
        double r = c.def().radius();
        ServerFx.of("x_ring", p.position()).a(r).b(3).color(Bard.COLOR).send(c.level());
        ServerFx.of("n_notes", p.position()).follow(p).a(c.def().duration()).b(1).color(0xFFE070).send(c.level());
        sound(c, SoundEvents.PLAYER_LEVELUP, 1.2F);
        SkillTasks.run(age -> {
            if (!p.isAlive()) return true;
            if (age % 20 == 0) {
                Bard.grand(p, r, 30);
                ServerFx.of("n_song", p.position()).follow(p).a(r).b(1.2).color(0xFFE070).send(c.level());
                float[] chord = {1.0F, 1.26F, 1.5F};
                for (float pitch : chord) sound(c, p.position(), SoundEvents.NOTE_BLOCK_BELL, 0.5F, pitch * (age % 40 == 0 ? 1F : 0.84F));
            }
            return age >= life;
        });
        return true;
    }

    /** Ends the performance with a shockwave: bigger and stronger the better it went. */
    private static boolean finale(Ctx c) {
        ServerPlayer p = c.p();
        JobData d = JobManager.data(p);
        int points = Bard.performing(p) ? d.songPoints() : 0;
        double mult = 0.6 + 0.02 * points;
        double r = Bard.radius(points);
        Vec3 o = p.position();
        List<LivingEntity> hit = enemies(p, o, r);
        for (LivingEntity e : hit) {
            hurt(c, e, c.dmg(mult), magic(c));
            pushAway(e, o, 1.0 + points * 0.03, 0.5);
        }
        Bard.stop(p);
        d.setSongPoints(0);
        JobManager.sync(p);
        ServerFx.of("x_ring", o).a(r).b(3).color(0xFFE070).points(hit).send(c.level());
        ServerFx.of("n_finale", o).a(r).b(points).color(Bard.COLOR).send(c.level());
        sound(c, SoundEvents.NOTE_BLOCK_BELL, 0.5F);
        sound(c, o, SoundEvents.GENERIC_EXPLODE, 0.6F, 1.4F);
        sound(c, o, SoundEvents.PLAYER_LEVELUP, 0.8F, 0.8F);
        return true;
    }
}
