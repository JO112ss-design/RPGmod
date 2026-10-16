package com.jo112ss.rpgmod.client.fx;

import static com.jo112ss.rpgmod.client.fx.SkillFx.*;

import com.jo112ss.rpgmod.client.fx.Fx.S;
import com.jo112ss.rpgmod.client.fx.SkillFx.P;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Effects of the casters: wizard (spells and skills), healer and enchanter. */
final class FxArcane {
    private FxArcane() {}

    static final int ARC = 0xA98BFF, ARC_L = 0x7FE3FF, MANA = 0x5FA8FF;
    static final int FIRE = 0xFF6A1F, FIRE_L = 0xFFD070, FIRE_D = 0x801808;
    static final int ICE = 0x9FE8FF, ICE_D = 0x3A8FD0;
    static final int VOLT = 0xFFF07A, VOLT_B = 0x9FC0FF;
    static final int TIME = 0x9FE0FF;
    static final int HEAL = 0x8CFFB0, PETAL = 0xFFB7D5;
    static final int ENCH = 0xFF7FD1, ENCH_G = 0xFFD27F, HEX = 0xB040FF;

    static boolean play(P p) {
        switch (p.fx()) {
            case "spell_charge" -> spellCharge(p);
            case "mana_surge" -> manaSurge(p);
            case "blink" -> blink(p);
            case "mana_barrier" -> manaBarrier(p);
            case "arcane_focus" -> arcaneFocus(p);
            case "meteor" -> meteor(p);
            case "meteor_impact" -> meteorImpact(p);
            case "time_warp" -> timeWarp(p);
            case "magic_bolt" -> magicBolt(p);
            case "magic_bolt_hit" -> magicBoltHit(p);
            case "flame_burst" -> flameBurst(p);
            case "frost_nova" -> frostNova(p);
            case "frozen" -> frozen(p);
            case "thunder" -> thunder(p);
            case "thunder_strike" -> thunderStrike(p);
            case "arcane_shield" -> arcaneShield(p);
            case "heal" -> heal(p);
            case "group_heal" -> groupHeal(p);
            case "blessing" -> blessing(p);
            case "mana_gift" -> manaGift(p);
            case "sanctuary" -> sanctuary(p);
            case "guardian_light" -> guardianLight(p);
            case "empower" -> empower(p);
            case "haste" -> haste(p);
            case "weaken" -> weaken(p);
            case "mana_link" -> manaLink(p);
            case "grand_enchant" -> grandEnchant(p);
            case "hex" -> hex(p);
            case "curse_aura" -> curseAura(p);
            default -> {
                return false;
            }
        }
        return true;
    }

    // ================================================================== wizard

    /** Magic circle under the caster while a staff spell charges; it locks in when the cast is ready. */
    private static void spellCharge(P p) {
        Entity e = p.e();
        if (!(e instanceof LivingEntity le)) return;
        int need = Math.max(1, (int) p.b());
        int color = p.colorOr(ARC);
        FxParticle circle = ground(S.CIRCLE_ARCANE, le.position(), 0.3F, color, 100000).alpha(0.9F, 0.9F).spin(0.05F).follow(le).spawn();
        FxParticle outer = ground(S.RING, le.position(), 0.3F, color, 100000).alpha(0.6F, 0.6F).follow(le).spawn();
        FxAnims.run(20 * 60, age -> {
            boolean charging = le.isAlive() && le.isUsingItem() && age < 20 * 60 - 1;
            if (!charging) {
                if (circle != null) circle.fadeOut(6);
                if (outer != null) outer.fadeOut(6);
                return true;
            }
            float t = Math.min(1, age / (float) need);
            float size = 0.4F + 0.9F * (1 - (1 - t) * (1 - t));
            if (circle != null) circle.sizeNow(size);
            if (outer != null) outer.sizeNow(size * 1.12F);
            Vec3 tip = hand(le, le.position());
            if (age < need) {
                for (int i = 0, n = Fx.n(2); i < n; i++) {
                    Vec3 from = tip.add(Fx.randomUnit().scale(1.3));
                    Fx.p(S.GLOW, from).vel(tip.subtract(from).scale(0.16)).color(0xFFFFFF, color).size(0.12F, 0.04F).alpha(0.4F, 1).life(6).spawn();
                }
                if (age % 3 == 0) {
                    double a = age * 0.5;
                    Fx.p(Fx.rune(age / 3), le.position().add(Math.cos(a) * size, 0.1, Math.sin(a) * size)).vel(0, 0.05, 0)
                            .color(0xFFFFFF, color).size(0.16F, 0.08F).alpha(1, 0).life(16).spawn();
                }
            } else {
                if (age == need) {
                    flash(tip, color, 0.8F, 5);
                    ground(S.WAVE, le.position(), size, color, 8).size(size, size * 1.8F).easeOut().alpha(0.8F, 0).spawn();
                    if (circle != null) circle.spinNow(0.12F);
                }
                Fx.p(S.GLOW, tip).color(0xFFFFFF, color).size(0.35F + 0.08F * (float) Math.sin(age * 0.6), 0.1F).alpha(0.8F, 0).life(2).spawn();
                if (age % 2 == 0) Fx.p(S.SPARK, tip.add(Fx.randomUnit().scale(0.15))).color(0xFFFFFF, color).size(0.25F, 0).randomAngle().life(5).spawn();
            }
            return false;
        });
    }

    private static void manaSurge(P p) {
        Entity e = p.e();
        Vec3 o = feet(e, p.pos());
        int dur = 14;
        FxAnims.follow(e, dur + 1, age -> {
            Vec3 c = chest(e, o);
            if (age < dur) {
                for (int i = 0, n = Fx.n(5); i < n; i++) {
                    double a = age * 0.7 + i * 1.3;
                    Vec3 from = c.add(Math.cos(a) * 2.4, Fx.r(-1.0, 1.2), Math.sin(a) * 2.4);
                    Fx.p(S.GLOW, from).vel(c.subtract(from).scale(0.12)).color(MANA, 0xBFE8FF).size(0.18F, 0.06F).alpha(0.5F, 1).life(8).spawn();
                }
                if (age % 3 == 0) {
                    double a = age * 0.4;
                    Fx.p(Fx.rune(age), c.add(Math.cos(a) * 1.0, -0.2 + age * 0.04, Math.sin(a) * 1.0)).color(0xFFFFFF, MANA).size(0.15F, 0).alpha(1, 0).life(12).spawn();
                }
            } else {
                flash(c, MANA, 1.3F, 6);
                Fx.p(S.RING, c).flat().size(0.3F, 2.0F).easeOut().color(0xBFE8FF, MANA).alpha(0.9F, 0).life(10).spawn();
                burst(c, 18, 0.35, S.GLOW, 0xFFFFFF, MANA, 0.14F, 12, 0);
            }
            return false;
        });
    }

    private static void blink(P p) {
        Vec3 from = p.pos();
        Vec3 to = p.vec();
        Vec3 c0 = from.add(0, 1, 0);
        for (int i = 0, n = Fx.n(22); i < n; i++) {
            Vec3 at = c0.add(Fx.randomUnit().scale(1.3));
            Fx.p(S.GLOW, at).vel(c0.subtract(at).scale(0.2)).color(0xFFFFFF, ARC).size(0.16F, 0.05F).life(5).spawn();
        }
        for (int k = 0; k < 9; k++) {
            Fx.p(S.GLOW, from.add(Fx.r(-0.15, 0.15), 0.2 * k, Fx.r(-0.15, 0.15))).color(ARC_L, ARC).size(0.4F, 0.2F).alpha(0.7F, 0).life(12).spawn();
        }
        flash(c0, ARC, 1.2F, 5);
        line(c0, to.add(0, 1, 0), 0.35, (v, t) -> Fx.p(S.GLOW, v).color(ARC_L, ARC).size(0.18F, 0).alpha(0.8F, 0).life(10).delay((int) (t * 3)).spawn());
        FxAnims.later(3, () -> {
            Vec3 c1 = to.add(0, 1, 0);
            flash(c1, ARC_L, 1.4F, 6);
            ground(S.WAVE, to, 0.2F, ARC, 10).size(0.2F, 2.0F).easeOut().alpha(0.8F, 0).spawn();
            ground(S.CIRCLE_ARCANE, to, 1.1F, ARC, 14).alpha(0.9F, 0).spin(0.15F).spawn();
            burst(c1, 20, 0.35, S.GLOW, 0xFFFFFF, ARC, 0.14F, 10, 0);
        });
    }

    /** Hex plates on a sphere around the caster. */
    private static void hexSphere(Entity e, Vec3 fallback, int c0, int c1, float radius, int count, int life, int delayMax) {
        Vec3 c = chest(e, fallback).add(0, -0.2, 0);
        double golden = Math.PI * (3 - Math.sqrt(5));
        for (int i = 0; i < count; i++) {
            double y = 1 - (i + 0.5) / count * 2;
            double rr = Math.sqrt(1 - y * y);
            double th = golden * i;
            Vec3 n = new Vec3(Math.cos(th) * rr, y, Math.sin(th) * rr);
            Fx.p(S.HEX, c.add(n.scale(radius))).fixed(Fx.across(n)).size(0.3F).color(c0, c1).alpha(0.7F, 0).fadeIn(0.3F)
                    .life(life).delay(Fx.RND.nextInt(delayMax + 1)).follow(e).spawn();
        }
    }

    private static void manaBarrier(P p) {
        Entity e = p.e();
        Vec3 o = feet(e, p.pos());
        hexSphere(e, o, ARC_L, ARC, 1.25F, 46, 26, 8);
        flash(chest(e, o), ARC_L, 1.2F, 6);
        int dur = (int) (p.a() * 20);
        FxAnims.follow(e, dur, age -> {
            if (age % 10 == 0) hexSphere(e, o, ARC_L, ARC, 1.25F, 3, 10, 0);
            return false;
        });
    }

    private static void arcaneFocus(P p) {
        Entity e = p.e();
        Vec3 o = feet(e, p.pos());
        ground(S.CIRCLE_ARCANE, o, 1.8F, ARC, 34).size(0.6F, 1.8F).easeOut().alpha(0.9F, 0).spin(0.08F).follow(e).spawn();
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2 * i / 8;
            Fx.p(Fx.rune(i), o.add(Math.cos(a) * 1.4, 0.2, Math.sin(a) * 1.4)).vel(0, 0.06, 0).color(0xFFFFFF, MANA).size(0.2F, 0.1F).alpha(1, 0).fadeIn(0.2F).life(26).delay(i).spawn();
        }
        for (Vec3 a : p.pts()) manaArc(chest(e, o), a, MANA);
    }

    /** Motes flowing from one point to another along an arc. */
    private static void manaArc(Vec3 from, Vec3 to, int color) {
        Vec3 mid = from.add(to).scale(0.5).add(0, 1.2 + from.distanceTo(to) * 0.15, 0);
        FxAnims.run(12, age -> {
            for (int k = 0; k < 3; k++) {
                double t = Math.min(1, (age + k / 3.0) / 10.0);
                Vec3 v = from.scale((1 - t) * (1 - t)).add(mid.scale(2 * t * (1 - t))).add(to.scale(t * t));
                Fx.p(S.GLOW, v).color(0xFFFFFF, color).size(0.2F, 0).life(8).spawn();
            }
            if (age == 10) {
                flash(to, color, 0.9F, 5);
                ground(S.RING, to.add(0, -0.9, 0), 0.3F, color, 10).size(0.3F, 1.3F).alpha(0.8F, 0).spawn();
            }
            return false;
        });
    }

    private static void meteor(P p) {
        Vec3 at = p.pos();
        Vec3 sky = p.vec();
        float r = Math.max(2, p.a());
        int fall = Math.max(4, (int) p.b());
        FxParticle warn = ground(S.CIRCLE_ARCANE, at, r, FIRE, fall + 6).color(FIRE_L, FIRE).alpha(0.3F, 0.95F).spin(0.06F).size(r * 0.5F, r).easeOut().spawn();
        ground(S.RING, at, r * 1.04F, FIRE, fall + 6).color(0xFF4020).alpha(0.4F, 0.9F).spawn();
        FxAnims.run(fall, age -> {
            for (int s = 0; s < 3; s++) {
                double t = (age + s / 3.0) / fall;
                double k = 1 - Math.pow(t, 1.3);
                Vec3 pos = at.add(sky.scale(k));
                if (s == 2) {
                    Fx.p(S.ORB, pos).color(0xFFFFFF, FIRE_L).size(1.4F).alpha(1, 1).life(1).spawn();
                    Fx.p(S.GLOW, pos).color(FIRE_L, FIRE).size(3.0F).alpha(0.7F, 0.6F).life(1).spawn();
                }
                for (int i = 0, n = Fx.n(3); i < n; i++) {
                    Fx.p(S.GLOW, pos.add(Fx.randomUnit().scale(0.5))).vel(Fx.randomUnit().scale(0.05)).color(FIRE_L, FIRE_D)
                            .size(Fx.rf(0.6F, 1.0F), 0.1F).alpha(0.9F, 0).life(12 + Fx.RND.nextInt(6)).spawn();
                }
                if (s == 0) {
                    smoke(pos.add(Fx.randomUnit().scale(0.4)), 0x2A2420, 0.8F, 2.2F, 40).alpha(0.7F, 0).spawn();
                    Fx.vanilla(ParticleTypes.FLAME, pos.x, pos.y, pos.z, Fx.r(-0.05, 0.05), Fx.r(-0.05, 0.05), Fx.r(-0.05, 0.05));
                }
            }
            if (warn != null && age > fall - 6) warn.alphaNow(age % 2 == 0 ? 1 : 0.6F);
            return false;
        });
    }

    private static void meteorImpact(P p) {
        Vec3 at = p.pos();
        float r = Math.max(2, p.a());
        FxAnims.shake(at, 2.2F, 18, 32);
        flash(at.add(0, 1, 0), FIRE, 6, 7);
        Fx.vanilla(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 0.5, at.z, 0, 0, 0);
        ground(S.WAVE, at, 1, FIRE, 16).size(1, r * 1.7F).easeOut().color(FIRE_L, FIRE_D).alpha(1, 0).spawn();
        ground(S.RING, at, 1, FIRE, 12).size(1, r * 1.3F).easeOut().color(0xFFFFFF, FIRE).alpha(0.9F, 0).delay(1).spawn();
        ground(S.SMOKE, at, r * 0.9F, 0x1A1410, 120).alphaBlend().lit().alpha(0.75F, 0).randomAngle().spawn();
        for (int i = 0, n = Fx.n(40); i < n; i++) {
            Vec3 v = Fx.randomUnit().multiply(0.5, 1, 0.5).add(0, 1.2, 0).normalize().scale(Fx.r(0.3, 0.9));
            Fx.p(S.GLOW, at.add(0, 0.5, 0)).vel(v).gravity(0.9F).friction(0.96F).color(FIRE_L, FIRE_D).size(0.25F, 0.05F).life(25 + Fx.RND.nextInt(25)).spawn();
        }
        for (int i = 0, n = Fx.n(36); i < n; i++) {
            double a = Fx.r(0, Math.PI * 2);
            Fx.vanilla(ParticleTypes.FLAME, at.x, at.y + 0.3, at.z, Math.cos(a) * Fx.r(0.2, 0.5), Fx.r(0.02, 0.12), Math.sin(a) * Fx.r(0.2, 0.5));
        }
        for (int i = 0, n = Fx.n(14); i < n; i++) {
            smoke(at.add(Fx.r(-1, 1), Fx.r(0.5, 2), Fx.r(-1, 1)), Fx.mix(0x2A2420, 0x5A5048, Fx.RND.nextDouble()), 1.0F, 2.8F, 40 + Fx.RND.nextInt(25))
                    .alpha(0.6F, 0).vel(Fx.r(-0.05, 0.05), Fx.r(0.06, 0.15), Fx.r(-0.05, 0.05)).friction(0.95F).spawn();
        }
        for (int i = 0, n = Fx.n(10); i < n; i++) {
            double a = Fx.r(0, Math.PI * 2), d = Fx.r(0.5, r);
            Vec3 v = at.add(Math.cos(a) * d, 0, Math.sin(a) * d);
            ground(S.GLOW, v, Fx.rf(0.4F, 0.8F), FIRE, 60).color(FIRE_L, FIRE_D).alpha(0.8F, 0).delay(Fx.RND.nextInt(6)).spawn();
        }
        debris(at, 40, r * 0.5, 0.7);
        hits(p.pts(), FIRE, 1);
    }

    private static void timeWarp(P p) {
        Vec3 o = p.pos();
        float r = Math.max(2, p.a());
        int life = Math.max(20, (int) p.b());
        ground(S.CIRCLE_CLOCK, o, r, TIME, life).color(0xFFFFFF, TIME).alpha(0.85F, 0).fadeIn(0.04F).spin(-0.015F).size(r * 0.3F, r).easeOut().spawn();
        ground(S.CIRCLE_CLOCK, o.add(0, 0.02, 0), r * 0.45F, TIME, life).alpha(0.7F, 0).fadeIn(0.06F).spin(0.03F).spawn();
        ground(S.WAVE, o, 1, TIME, 22).size(1, r).easeOut().alpha(0.7F, 0).spawn();
        flash(o.add(0, 1, 0), TIME, 1.6F, 8);
        FxAnims.run(life, age -> {
            if (age % 3 != 0) return false;
            for (int i = 0, n = Fx.n(3); i < n; i++) {
                double a = Fx.r(0, Math.PI * 2), d = Math.sqrt(Fx.RND.nextDouble()) * r;
                Fx.p(S.GLOW, o.add(Math.cos(a) * d, Fx.r(0.2, 2.2), Math.sin(a) * d)).vel(Fx.randomUnit().scale(0.004))
                        .color(0xFFFFFF, TIME).size(0.1F, 0.12F).alpha(0.9F, 0).fadeIn(0.3F).life(50).spawn();
            }
            return false;
        });
        for (Vec3 t : p.pts()) {
            Fx.p(S.CIRCLE_CLOCK, t).size(0.9F, 0.5F).color(TIME).alpha(0.9F, 0).spin(-0.05F).life(30).spawn();
        }
    }

    // ---------------------------------------------------------------- spells

    private static boolean boltBlocker(Entity x) {
        return x instanceof LivingEntity le && le.isAlive() && !(x instanceof Player) && !(x instanceof ArmorStand);
    }

    /** The bolt is simulated here with the same rules as the server, so it flies smoothly. */
    private static void magicBolt(P p) {
        Vec3 start = p.pos();
        Vec3 vel = p.vec();
        double speed = Math.max(0.1, vel.length());
        double range = p.a();
        Entity target = p.e();
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        Fx.p(S.RING, start).fixed(Fx.across(vel)).size(0.2F, 1.0F).easeOut().color(0xFFFFFF, ARC).alpha(0.9F, 0).life(6).spawn();
        flash(start, ARC, 0.7F, 4);
        final Vec3[] pos = {start};
        final Vec3[] dir = {vel.normalize()};
        final double[] travelled = {0};
        final Entity self = Minecraft.getInstance().player;
        FxAnims.run((int) (range / speed) + 3, age -> {
            if (target != null && target.isAlive()) {
                Vec3 want = target.position().add(0, target.getBbHeight() * 0.55, 0).subtract(pos[0]).normalize();
                dir[0] = com.jo112ss.rpgmod.job.SkillExecutor.turn(dir[0], want, 0.22);
            }
            for (int s = 0; s < 3; s++) {
                Vec3 from = pos[0];
                Vec3 to = from.add(dir[0].scale(speed / 3));
                BlockHitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, self));
                Vec3 end = block.getType() == HitResult.Type.MISS ? to : block.getLocation();
                trail(end, dir[0], age * 3 + s);
                if (block.getType() != HitResult.Type.MISS) return true;
                for (Entity x : level.getEntities((Entity) null, new AABB(from, end).inflate(0.6), FxArcane::boltBlocker)) {
                    if (x.getBoundingBox().inflate(0.3).clip(from, end).isPresent() || x.getBoundingBox().inflate(0.3).contains(end)) return true;
                }
                pos[0] = end;
                travelled[0] += speed / 3;
                if (travelled[0] >= range) return true;
            }
            Fx.p(S.ORB, pos[0]).color(0xFFFFFF, ARC_L).size(0.45F).alpha(1, 1).life(1).spawn();
            Fx.p(S.GLOW, pos[0]).color(ARC).size(1.1F).alpha(0.55F, 0.5F).life(1).spawn();
            return false;
        });
    }

    private static void trail(Vec3 at, Vec3 dir, int step) {
        Fx.p(S.GLOW, at).color(ARC_L, ARC).size(0.32F, 0).alpha(0.9F, 0).life(8).spawn();
        Vec3 side = Fx.right(new Vec3(dir.x, 0, dir.z).lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : new Vec3(dir.x, 0, dir.z).normalize());
        Vec3 up = side.cross(dir).normalize();
        for (int k = 0; k < 2; k++) {
            double a = step * 0.9 + k * Math.PI;
            Vec3 off = side.scale(Math.cos(a) * 0.22).add(up.scale(Math.sin(a) * 0.22));
            Fx.p(S.GLOW, at.add(off)).color(0xFFFFFF, ARC).size(0.1F, 0).life(10).spawn();
        }
    }

    private static void magicBoltHit(P p) {
        Vec3 at = p.pos();
        Vec3 dir = p.vec().lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : p.vec().normalize();
        if (p.b() < 0) { // fizzled at max range
            burst(at, 8, 0.15, S.GLOW, ARC_L, ARC, 0.12F, 8, 0);
            return;
        }
        flash(at, ARC, 1.6F, 5);
        Fx.p(S.RING, at).fixed(Fx.across(dir)).size(0.2F, 1.7F).easeOut().color(0xFFFFFF, ARC).alpha(1, 0).life(7).spawn();
        Fx.p(S.SPARK, at).color(0xFFFFFF, ARC_L).size(1.2F, 0).randomAngle().life(5).spawn();
        Vec3 out = p.b() > 0 ? dir : dir.scale(-1);
        spray(at, out, 0.8, 18, 0.45, S.GLOW, 0xFFFFFF, ARC, 0.14F, 10, 0.1F);
        for (int i = 0; i < 3; i++) {
            Fx.p(Fx.rune(i), at.add(Fx.randomUnit().scale(0.4))).vel(0, 0.04, 0).color(0xFFFFFF, ARC).size(0.15F, 0).life(14).spawn();
        }
    }

    private static void flameBurst(P p) {
        Vec3 o = p.pos();
        Vec3 dir = p.vec().normalize();
        float range = Math.max(2, p.a());
        double half = Math.toRadians(Math.max(5, p.b()));
        Fx.p(S.RING, o.add(dir.scale(0.3))).fixed(Fx.across(dir)).size(0.2F, 1.4F).easeOut().color(FIRE_L, FIRE).alpha(0.9F, 0).life(6).spawn();
        flash(o, FIRE, 1.0F, 4);
        FxAnims.run(9, age -> {
            for (int i = 0, n = Fx.n(16); i < n; i++) {
                Vec3 d = cone(dir, half * 0.85);
                double speed = range / 11.0 * Fx.r(0.8, 1.15);
                Fx.p(S.GLOW, o.add(d.scale(0.2))).vel(d.scale(speed)).friction(0.97F).color(FIRE_L, FIRE_D)
                        .size(0.25F, Fx.rf(0.9F, 1.3F)).alpha(0.85F, 0).life(13 + Fx.RND.nextInt(4)).spawn();
            }
            for (int i = 0, n = Fx.n(4); i < n; i++) {
                Vec3 d = cone(dir, half * 0.7);
                Fx.vanilla(ParticleTypes.FLAME, o, d.scale(range / 14.0 * Fx.r(0.8, 1.2)));
            }
            if (age % 2 == 0) {
                Vec3 d = cone(dir, half * 0.6);
                smoke(o.add(d.scale(range * 0.8)), 0x2A1E18, 0.6F, 1.6F, 30).alpha(0.6F, 0).vel(0, 0.04, 0).spawn();
            }
            return false;
        });
        FxAnims.run(16, age -> {
            if (age < 4) return false;
            Vec3 d = cone(dir, half);
            Fx.p(S.GLOW, o.add(d.scale(Fx.r(1, range)))).vel(0, Fx.r(0.03, 0.08), 0).color(FIRE_L, FIRE).size(0.1F, 0).life(20).spawn();
            return false;
        });
    }

    /** Random direction within {@code half} radians of {@code dir}. */
    private static Vec3 cone(Vec3 dir, double half) {
        Vec3 ref = Math.abs(dir.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 a = ref.cross(dir).normalize();
        Vec3 b = dir.cross(a).normalize();
        double th = Math.sqrt(Fx.RND.nextDouble()) * half;
        double ph = Fx.r(0, Math.PI * 2);
        return dir.scale(Math.cos(th)).add(a.scale(Math.sin(th) * Math.cos(ph))).add(b.scale(Math.sin(th) * Math.sin(ph))).normalize();
    }

    private static void frostNova(P p) {
        Vec3 o = p.pos();
        float r = Math.max(2, p.a());
        double speed = 0.7;
        int dur = (int) Math.ceil(r / speed);
        flash(o.add(0, 1, 0), ICE, 2.6F, 6);
        ground(S.WAVE, o, 0.5F, ICE, dur + 2).size(0.5F, r).color(0xFFFFFF, ICE).alpha(0.95F, 0.2F).spawn();
        ground(S.CIRCLE_ARCANE, o, 1.6F, ICE, 20).alpha(0.9F, 0).spin(0.1F).spawn();
        FxAnims.run(dur + 1, age -> {
            double front = Math.min(r, 0.5 + age * speed);
            circle(o, front, Fx.n(10 + (int) (front * 3)), Fx.r(0, 1), v -> {
                float s = Fx.rf(0.25F, 0.55F);
                Fx.p(S.SHARD, v.add(Fx.r(-0.2, 0.2), s * 0.8, Fx.r(-0.2, 0.2))).upright().color(0xFFFFFF, ICE).size(s * 0.6F, s).alpha(0.95F, 0).fadeIn(0.05F)
                        .angle(Fx.rf(-0.35F, 0.35F)).life(26 + Fx.RND.nextInt(14)).spawn();
            });
            for (int i = 0, n = Fx.n(4); i < n; i++) {
                double a = Fx.r(0, Math.PI * 2);
                Fx.vanilla(ParticleTypes.SNOWFLAKE, o.x + Math.cos(a) * front, o.y + 0.3, o.z + Math.sin(a) * front, Math.cos(a) * 0.1, 0.05, Math.sin(a) * 0.1);
                smoke(o.add(Math.cos(a) * front, 0.2, Math.sin(a) * front), 0xDDF6FF, 0.6F, 1.6F, 30).alpha(0.35F, 0).vel(Math.cos(a) * 0.03, 0.01, Math.sin(a) * 0.03).spawn();
            }
            if (front >= r) {
                circle(o, r, Fx.n(18), 0, v -> Fx.p(S.SHARD, v.add(0, 0.7, 0)).upright().color(0xFFFFFF, ICE_D).size(0.5F, 0.9F).alpha(1, 0).fadeIn(0.05F)
                        .angle(Fx.rf(-0.3F, 0.3F)).life(40).spawn());
                return true;
            }
            return false;
        });
    }

    private static void frozen(P p) {
        Entity e = p.e();
        if (e == null) return;
        float h = Math.max(0.5F, p.a()), w = Math.max(0.4F, p.b());
        for (int i = 0, n = Fx.n(10); i < n; i++) {
            double a = Fx.r(0, Math.PI * 2);
            Fx.p(S.SHARD, e.position().add(Math.cos(a) * w * 0.6, Fx.r(0.1, h), Math.sin(a) * w * 0.6)).color(0xFFFFFF, ICE).size(0.22F, 0.3F)
                    .alpha(0.9F, 0).randomAngle().fadeIn(0.05F).life(50 + Fx.RND.nextInt(20)).follow(e).spawn();
        }
        burst(e.position().add(0, h * 0.5, 0), 10, 0.2, S.GLOW, 0xFFFFFF, ICE, 0.12F, 12, 0.2F);
    }

    private static void thunder(P p) {
        Entity t = p.e();
        Vec3 at = feet(t, p.pos());
        int warn = Math.max(2, (int) p.b());
        FxParticle mark = ground(S.CIRCLE_ARCANE, at, 0.5F, VOLT, warn + 4).size(0.5F, 3.0F).easeOut().color(0xFFFFFF, VOLT).alpha(0.6F, 1).spin(0.2F).follow(t).spawn();
        ground(S.RING, at, 3.0F, VOLT_B, warn + 4).alpha(0.3F, 0.9F).follow(t).spawn();
        for (int i = 0; i < 6; i++) {
            smoke(at.add(Fx.r(-1.5, 1.5), 12 + Fx.r(-0.5, 0.5), Fx.r(-1.5, 1.5)), 0x2A2A38, 2.0F, 3.2F, warn + 24).alpha(0.75F, 0).fadeIn(0.2F).spawn();
        }
        FxAnims.run(warn, age -> {
            Vec3 f = feet(t, at);
            if (age % 2 == 0) {
                double a = Fx.r(0, Math.PI * 2);
                Vec3 s = f.add(Math.cos(a) * 2.2, 0.1, Math.sin(a) * 2.2);
                drawBolt(bolt(s, s.add(Fx.r(-0.6, 0.6), Fx.r(0.8, 1.6), Fx.r(-0.6, 0.6)), 0.3, 2), VOLT_B, 0.08F, 3, 0);
            }
            Fx.vanilla(ParticleTypes.ELECTRIC_SPARK, f.x + Fx.r(-1.5, 1.5), f.y + 0.2, f.z + Fx.r(-1.5, 1.5), 0, 0.1, 0);
            Vec3 cloud = f.add(Fx.r(-1, 1), 11.5, Fx.r(-1, 1));
            if (age % 3 == 0) Fx.p(S.GLOW, cloud).color(VOLT_B).size(1.5F, 0.5F).alpha(0.7F, 0).life(3).spawn();
            return false;
        });
        if (mark != null) FxAnims.later(warn, () -> mark.fadeOut(4));
    }

    private static void thunderStrike(P p) {
        Vec3 at = p.pos();
        float r = Math.max(1, p.a());
        Vec3 top = at.add(Fx.r(-1.5, 1.5), 18, Fx.r(-1.5, 1.5));
        for (int k = 0; k < 3; k++) {
            int d = k * 2;
            List<Vec3> path = bolt(top, at, 1.6, 6);
            drawBolt(path, VOLT_B, 0.28F, 4, d);
            for (int b = 0; b < 2; b++) {
                Vec3 from = path.get(Fx.RND.nextInt(path.size() - 2) + 1);
                drawBolt(bolt(from, from.add(Fx.r(-3, 3), -Fx.r(2, 4), Fx.r(-3, 3)), 0.8, 4), VOLT_B, 0.14F, 3, d);
            }
        }
        flash(at.add(0, 0.6, 0), VOLT, 5, 5);
        ground(S.WAVE, at, 0.5F, VOLT, 10).size(0.5F, r * 1.6F).easeOut().color(0xFFFFFF, VOLT).alpha(1, 0).spawn();
        ground(S.SMOKE, at, 1.6F, 0x141414, 80).alphaBlend().lit().alpha(0.7F, 0).randomAngle().spawn();
        burst(at.add(0, 0.4, 0), 26, 0.6, S.SPARK, 0xFFFFFF, VOLT, 0.3F, 8, 0.4F);
        for (int i = 0, n = Fx.n(20); i < n; i++) {
            Fx.vanilla(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 0.3, at.z, Fx.r(-0.6, 0.6), Fx.r(0.1, 0.6), Fx.r(-0.6, 0.6));
        }
        int i = 0;
        for (Vec3 c : p.pts()) {
            drawBolt(bolt(at.add(0, 1, 0), c, 0.5, 4), VOLT_B, 0.12F, 4, 2 + i * 2);
            Fx.p(S.SPARK, c).color(0xFFFFFF, VOLT).size(0.9F, 0).randomAngle().life(5).delay(2 + i * 2).spawn();
            i++;
        }
        debris(at, 14, 0.6, 0.5);
        FxAnims.shake(at, 1.0F, 8, 20);
    }

    private static void arcaneShield(P p) {
        Entity e = p.e();
        Vec3 o = feet(e, p.pos());
        Vec3 c = chest(e, o).add(0, -0.15, 0);
        double[][] tilts = {{0.0, 0.0}, {1.1, 0.4}, {-1.1, 1.6}};
        for (int k = 0; k < 3; k++) {
            double tx = tilts[k][0], yaw = tilts[k][1];
            Vec3 right = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
            Vec3 fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
            Vec3 up = fwd.scale(Math.cos(tx)).add(0, Math.sin(tx), 0);
            Fx.p(S.RING, c).fixed(Fx.basis(right, up)).size(1.15F).color(0xFFFFFF, ARC_L).alpha(0.9F, 0).fadeIn(0.15F).spin(0.1F).life(40).delay(k * 2).follow(e).spawn();
        }
        flash(c, ARC_L, 1.4F, 6);
        FxAnims.follow(e, 40, age -> {
            Vec3 cc = chest(e, o).add(0, -0.15, 0);
            for (int k = 0; k < 4; k++) {
                double a = age * 0.25 + k * Math.PI / 2;
                Fx.p(Fx.rune(k), cc.add(Math.cos(a) * 1.25, Math.sin(age * 0.2 + k) * 0.3, Math.sin(a) * 1.25)).color(0xFFFFFF, ARC_L).size(0.16F).alpha(0.9F, 0.9F).life(1).spawn();
            }
            return false;
        });
        aura(e, (int) (p.a() * 20), 12, ARC_L, S.GLOW, 0.12F);
    }

    // ================================================================== healer

    private static void heal(P p) {
        Entity e = p.e();
        Vec3 o = feet(e, p.pos());
        healAt(o, e, HEAL, 0);
        for (Vec3 a : p.pts()) healAt(a.add(0, -0.9, 0), null, HEAL, 3);
    }

    private static void groupHeal(P p) {
        Entity e = p.e();
        Vec3 o = feet(e, p.pos());
        float r = Math.max(2, p.a());
        ground(S.WAVE, o, 0.5F, HEAL, 16).size(0.5F, r).easeOut().color(0xFFFFFF, HEAL).alpha(0.8F, 0).spawn();
        ground(S.CIRCLE_NATURE, o, 1.6F, HEAL, 26).alpha(0.9F, 0).spin(0.06F).spawn();
        healAt(o, e, HEAL, 0);
        for (Vec3 a : p.pts()) healAt(a.add(0, -0.9, 0), null, HEAL, (int) (o.distanceTo(a) / r * 14));
    }

    private static void halo(Entity e, Vec3 fallbackHead, int life, int delay) {
        Vec3 h = e != null ? head(e, fallbackHead).add(0, 0.25, 0) : fallbackHead;
        Fx.p(S.RING, h).flat().size(0.32F).color(0xFFF2B0).alpha(0.95F, 0).fadeIn(0.1F).life(life).delay(delay).follow(e).spawn();
        Fx.p(S.GLOW, h).flat().size(0.45F).color(0xFFE080).alpha(0.4F, 0).fadeIn(0.1F).life(life).delay(delay).follow(e).spawn();
    }

    private static void petals(Vec3 head, Entity follow, int ticks) {
        FxAnims.run(ticks, age -> {
            if (age % 2 != 0) return false;
            Vec3 h = follow != null && !follow.isRemoved() ? head(follow, head) : head;
            Fx.p(S.PETAL, h.add(Fx.r(-0.8, 0.8), Fx.r(0.3, 0.8), Fx.r(-0.8, 0.8))).vel(Fx.r(-0.02, 0.02), -0.04, Fx.r(-0.02, 0.02)).gravity(0.02F)
                    .color(Fx.RND.nextBoolean() ? PETAL : 0xFFFFFF).size(0.12F).alpha(1, 0).fadeIn(0.1F).randomAngle().spin(Fx.rf(-0.2F, 0.2F)).life(40).spawn();
            return false;
        });
    }

    private static void blessing(P p) {
        Entity e = p.e();
        Vec3 o = feet(e, p.pos());
        int dur = (int) (p.b() * 20);
        halo(e, head(e, o), 80, 0);
        petals(head(e, o), e, 40);
        ground(S.CIRCLE_NATURE, o, 1.2F, PETAL, 30).alpha(0.8F, 0).spin(0.05F).follow(e).spawn();
        for (Vec3 a : p.pts()) {
            halo(null, a.add(0, 1.3, 0), 80, 4);
            petals(a.add(0, 1.1, 0), null, 30);
        }
        aura(e, dur, 10, HEAL, S.CROSS, 0.14F);
    }

    private static void manaGift(P p) {
        Entity e = p.e();
        Vec3 o = feet(e, p.pos());
        Vec3 c = chest(e, o);
        flash(c, MANA, 1.0F, 5);
        Fx.p(S.RING, c).flat().size(0.3F, 1.5F).easeOut().color(0xBFE8FF, MANA).alpha(0.9F, 0).life(9).spawn();
        for (Vec3 a : p.pts()) manaArc(c, a, MANA);
        if (p.pts().isEmpty()) burst(c, 16, 0.3, S.GLOW, 0xFFFFFF, MANA, 0.14F, 12, 0);
    }

    private static void sanctuary(P p) {
        Vec3 o = p.pos();
        float r = Math.max(2, p.a());
        int life = Math.max(20, (int) p.b());
        ground(S.CIRCLE_NATURE, o, r, HEAL, life).color(0xFFFFFF, HEAL).alpha(0.85F, 0.25F).fadeIn(0.04F).spin(0.006F).size(r * 0.3F, r).easeOut().spawn();
        ground(S.CIRCLE_HOLY, o.add(0, 0.02, 0), r * 0.55F, 0xFFF2B0, life).alpha(0.7F, 0.1F).fadeIn(0.06F).spin(-0.012F).spawn();
        flash(o.add(0, 1, 0), HEAL, 2.0F, 8);
        FxAnims.run(life, age -> {
            if (age % 2 == 0) {
                for (int i = 0, n = Fx.n(3); i < n; i++) {
                    double a = Fx.r(0, Math.PI * 2);
                    Fx.p(S.GLOW, o.add(Math.cos(a) * r, 0.1, Math.sin(a) * r)).vel(0, Fx.r(0.06, 0.12), 0).color(0xFFFFFF, HEAL).size(0.2F, 0).life(22).spawn();
                }
                double a = Fx.r(0, Math.PI * 2), d = Math.sqrt(Fx.RND.nextDouble()) * r;
                Fx.p(S.GLOW, o.add(Math.cos(a) * d, 0.1, Math.sin(a) * d)).vel(0, 0.05, 0).color(0xFFFFFF, HEAL).size(0.12F, 0).life(30).spawn();
            }
            if (age % 20 == 0) {
                ground(S.WAVE, o, 0.5F, HEAL, 14).size(0.5F, r).easeOut().alpha(0.5F, 0).spawn();
                if (age % 40 == 0) {
                    double a = Fx.r(0, Math.PI * 2), d = Fx.r(0, r * 0.7);
                    Fx.p(S.CROSS, o.add(Math.cos(a) * d, 0.5, Math.sin(a) * d)).vel(0, 0.05, 0).color(0xFFFFFF, HEAL).size(0.3F, 0.1F).alpha(1, 0).fadeIn(0.2F).life(26).spawn();
                }
            }
            return false;
        });
    }

    /** Wings of light feathers behind someone. */
    private static void wings(Entity e, Vec3 feet, int life, int delay) {
        Vec3 fwd = e != null ? facing(e) : new Vec3(0, 0, 1);
        Vec3 side = Fx.right(fwd);
        for (int s = -1; s <= 1; s += 2) {
            for (int i = 0; i < 12; i++) {
                double u = i / 11.0;
                Vec3 off = side.scale(s * (0.2 + u * 1.5)).add(0, 1.25 + Math.sin(u * Math.PI) * 0.55 - u * 0.25, 0).add(fwd.scale(-(0.3 + u * 0.25)));
                for (int k = 0; k < 2; k++) {
                    Vec3 at = feet.add(off).add(0, -k * (0.25 + u * 0.35), 0);
                    Fx.p(S.FEATHER, at).color(0xFFFFFF, 0xFFE9A0).size(0.22F - k * 0.04F).alpha(0.95F, 0).fadeIn(0.15F).angle((float) (s * (0.3 + u * 0.9)))
                            .life(life).delay(delay + i).follow(e).spawn();
                }
            }
        }
    }

    private static void guardianLight(P p) {
        Entity e = p.e();
        Vec3 o = feet(e, p.pos());
        float r = Math.max(2, p.a());
        int dur = (int) (p.b() * 20);
        Vec3 mid = o.add(0, 7, 0);
        Fx.p(S.STREAK, mid).upright().size(7).color(0xFFFFFF, 0xFFE9A0).alpha(1, 0).life(14).spawn();
        Fx.p(S.STREAK, mid).upright().size(7.2F).color(0xFFE9A0).alpha(0.5F, 0).life(16).spawn();
        flash(o.add(0, 1, 0), 0xFFE9A0, 3.0F, 8);
        ground(S.CIRCLE_HOLY, o, r * 0.5F, 0xFFE9A0, 40).alpha(0.9F, 0).spin(0.04F).spawn();
        ground(S.WAVE, o, 0.5F, 0xFFE9A0, 18).size(0.5F, r).easeOut().alpha(0.7F, 0).spawn();
        wings(e, o, 60, 2);
        for (Vec3 a : p.pts()) {
            Vec3 f = a.add(0, -0.9, 0);
            line(o.add(0, 1.5, 0), a, 0.35, (v, t) -> Fx.p(S.GLOW, v).color(0xFFFFFF, 0xFFE9A0).size(0.18F, 0).life(8).delay((int) (t * 8)).spawn());
            wings(null, f, 50, 8);
        }
        aura(e, dur, 8, 0xFFE9A0, S.GLOW, 0.13F);
    }

    // ================================================================== enchanter

    /** Runes orbiting an entity, then rising away. */
    private static void runeRing(Entity e, Vec3 feet, int color, int life) {
        FxParticle[] runes = new FxParticle[4];
        for (int k = 0; k < 4; k++) runes[k] = Fx.p(Fx.rune(k), feet).color(0xFFFFFF, color).size(0.2F).alpha(1, 1).life(life + 10).spawn();
        FxAnims.run(life, age -> {
            Vec3 c = (e != null && !e.isRemoved() ? e.position() : feet).add(0, 0.9, 0);
            for (int k = 0; k < 4; k++) {
                if (runes[k] == null) continue;
                double a = age * 0.22 + k * Math.PI / 2;
                double rise = Math.max(0, age - life * 0.6) * 0.06;
                runes[k].moveTo(c.x + Math.cos(a) * 0.9, c.y + Math.sin(age * 0.15 + k) * 0.15 + rise, c.z + Math.sin(a) * 0.9);
                if (age == life - 1) runes[k].fadeOut(8);
            }
            return false;
        });
    }

    private static void empower(P p) {
        Entity e = p.e();
        Vec3 o = feet(e, p.pos());
        ground(S.CIRCLE_ARCANE, o, 1.3F, ENCH, 28).color(ENCH_G, ENCH).alpha(0.9F, 0).spin(0.08F).follow(e).spawn();
        runeRing(e, o, ENCH, 26);
        burst(chest(e, o), 16, 0.3, S.GLOW, ENCH_G, 0xFF5050, 0.14F, 14, -0.05F);
        for (Vec3 a : p.pts()) {
            Vec3 f = a.add(0, -0.9, 0);
            ground(S.CIRCLE_ARCANE, f, 1.0F, ENCH, 24).color(ENCH_G, ENCH).alpha(0.9F, 0).spin(0.08F).delay(3).spawn();
            runeRing(null, f, ENCH, 22);
        }
        aura(e, (int) (p.b() * 20), 8, 0xFF7050, S.GLOW, 0.12F);
    }

    private static void windSpiral(Entity e, Vec3 feet, int color, int delay) {
        FxAnims.run(16 + delay, age -> {
            if (age < delay) return false;
            int t = age - delay;
            Vec3 f = e != null && !e.isRemoved() ? e.position() : feet;
            for (int k = 0; k < 3; k++) {
                double a = t * 0.8 + k * Math.PI * 2 / 3;
                Fx.p(S.GLOW, f.add(Math.cos(a) * 0.6, t * 0.12, Math.sin(a) * 0.6)).color(0xFFFFFF, color).size(0.15F, 0).life(10).spawn();
            }
            return false;
        });
        ground(S.WAVE, feet, 0.3F, color, 10).size(0.3F, 1.5F).easeOut().alpha(0.8F, 0).delay(delay).follow(e).spawn();
    }

    private static void haste(P p) {
        Entity e = p.e();
        Vec3 o = feet(e, p.pos());
        windSpiral(e, o, 0x9FF8FF, 0);
        for (Vec3 a : p.pts()) windSpiral(null, a.add(0, -0.9, 0), 0x9FF8FF, 3);
        aura(e, (int) (p.b() * 20), 6, 0x9FF8FF, S.GLOW, 0.1F);
    }

    private static void weaken(P p) {
        Vec3 o = p.pos();
        float r = Math.max(2, p.a());
        ground(S.CIRCLE_HEX, o, 1.4F, ENCH, 26).color(0xFFB0E0, ENCH).alpha(0.9F, 0).spin(-0.08F).spawn();
        ground(S.WAVE, o, 0.5F, ENCH, 16).size(0.5F, r).easeOut().alpha(0.6F, 0).spawn();
        for (Vec3 t : p.pts()) {
            FxParticle ring = Fx.p(S.RING, t.add(0, 1.4, 0)).flat().size(0.7F).color(ENCH).alpha(0.9F, 0.9F).life(40).spawn();
            FxAnims.run(16, age -> {
                if (ring != null) {
                    ring.moveTo(t.x, t.y + 1.4 - age * 0.15, t.z);
                    if (age == 15) ring.fadeOut(6);
                }
                return false;
            });
            Fx.p(Fx.rune(Fx.RND.nextInt(4)), t.add(0, 1.6, 0)).vel(0, -0.05, 0).color(0xFFFFFF, ENCH).size(0.25F, 0.1F).alpha(1, 0).life(22).spawn();
        }
    }

    private static void manaLink(P p) {
        Entity e = p.e();
        Vec3 o = feet(e, p.pos());
        Vec3 c = chest(e, o);
        Fx.p(S.RING, c).flat().size(0.3F, 1.4F).easeOut().color(0xB0D0FF, ENCH).alpha(0.9F, 0).life(9).spawn();
        if (p.pts().isEmpty()) {
            burst(c, 14, 0.3, S.GLOW, 0xFFFFFF, MANA, 0.13F, 12, 0);
            return;
        }
        FxAnims.run(22, age -> {
            Vec3 cc = chest(e, o);
            for (Vec3 a : p.pts()) {
                Vec3 d = a.subtract(cc);
                for (int k = 0; k < 2; k++) {
                    double t = ((age * 0.08) + k * 0.5) % 1.0;
                    Fx.p(S.GLOW, cc.add(d.scale(t))).color(0xFFFFFF, k == 0 ? MANA : ENCH).size(0.18F, 0).life(5).spawn();
                }
                if (age % 3 == 0) line(cc, a, 0.5, (v, t) -> Fx.p(S.GLOW, v).color(ENCH).size(0.08F).alpha(0.5F, 0).life(3).spawn());
            }
            return false;
        });
        for (Vec3 a : p.pts()) ground(S.RING, a.add(0, -0.9, 0), 0.3F, MANA, 12).size(0.3F, 1.2F).alpha(0.8F, 0).delay(4).spawn();
    }

    private static void grandEnchant(P p) {
        Vec3 o = p.pos();
        float r = Math.max(2, p.a());
        ground(S.CIRCLE_ARCANE, o, r, ENCH, 50).size(r * 0.3F, r).easeOut().color(ENCH_G, ENCH).alpha(0.95F, 0).spin(0.03F).spawn();
        Fx.p(S.CIRCLE_ARCANE, o.add(0, 2.6, 0)).flat().size(r * 0.25F, r * 0.55F).easeOut().color(0xFFFFFF, ENCH).alpha(0.8F, 0).spin(-0.06F).life(44).fadeIn(0.1F).spawn();
        Fx.p(S.CIRCLE_ARCANE, o.add(0, 5.0, 0)).flat().size(r * 0.15F, r * 0.3F).easeOut().color(ENCH_G).alpha(0.7F, 0).spin(0.09F).life(40).fadeIn(0.15F).spawn();
        flash(o.add(0, 1.2, 0), ENCH, 2.5F, 8);
        circle(o, r * 0.85, Fx.n(14), 0, v -> {
            Fx.p(Fx.rune(Fx.RND.nextInt(4)), v.add(0, 0.2, 0)).vel(0, Fx.r(0.06, 0.12), 0).color(0xFFFFFF, Fx.RND.nextBoolean() ? ENCH : ENCH_G)
                    .size(0.24F, 0.1F).alpha(1, 0).fadeIn(0.15F).life(30).delay(Fx.RND.nextInt(10)).spawn();
        });
        runeRing(p.e(), feet(p.e(), o), ENCH_G, 30);
        for (Vec3 a : p.pts()) runeRing(null, a.add(0, -0.9, 0), ENCH, 26);
        FxAnims.shake(o, 0.4F, 8, 12);
    }

    private static void hex(P p) {
        Vec3 o = p.pos();
        float r = Math.max(2, p.a());
        ground(S.CIRCLE_HEX, o, r, HEX, 50).size(r * 0.3F, r).easeOut().color(0xE080FF, HEX).alpha(0.95F, 0).spin(-0.03F).spawn();
        ground(S.WAVE, o, 1, HEX, 18).size(1, r * 1.1F).easeOut().alpha(0.7F, 0).spawn();
        for (int i = 0, n = Fx.n(24); i < n; i++) {
            double a = Fx.r(0, Math.PI * 2);
            smoke(o.add(Math.cos(a) * r, 0.2, Math.sin(a) * r), 0x1A0624, 0.6F, 1.6F, 40).vel(0, Fx.r(0.04, 0.1), 0).delay(Fx.RND.nextInt(10)).spawn();
        }
        for (Vec3 t : p.pts()) {
            flash(t, HEX, 1.2F, 6);
            Fx.p(S.SKULL, t.add(0, 0.9, 0)).vel(0, 0.03, 0).size(0.15F, 0.35F).alpha(1, 0).fadeIn(0.15F).life(28).delay(Fx.RND.nextInt(6)).spawn();
        }
        FxAnims.shake(o, 0.3F, 6, 12);
    }

    private static void curseAura(P p) {
        Entity e = p.e();
        if (e == null) return;
        int color = p.colorOr(HEX);
        int life = (int) (p.a() * 20);
        FxAnims.follow(e, life, age -> {
            if (age % 5 != 0) return false;
            Vec3 at = e.position().add(Fx.r(-0.35, 0.35), Fx.r(0.1, e.getBbHeight()), Fx.r(-0.35, 0.35));
            smoke(at, 0x1A0624, 0.3F, 0.7F, 16).alpha(0.55F, 0).vel(0, 0.04, 0).spawn();
            Fx.p(S.GLOW, at).vel(0, 0.03, 0).color(color).size(0.12F, 0).life(14).spawn();
            return false;
        });
    }
}
