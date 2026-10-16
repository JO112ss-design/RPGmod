package com.jo112ss.rpgmod.client.fx;

import static com.jo112ss.rpgmod.client.fx.SkillFx.*;

import com.jo112ss.rpgmod.client.fx.Fx.S;
import com.jo112ss.rpgmod.client.fx.SkillFx.P;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/** Effects of the weapon jobs: fighter, ranger, paladin and assassin. */
final class FxMartial {
    private FxMartial() {}

    // fighter: embers and hot steel
    static final int EMBER = 0xFF8A3D, HOT = 0xFFE2B0, BLOOD = 0xFF3A2A;
    // ranger: leaves and wind
    static final int LEAF = 0x8CFF6A, WIND = 0xD8FFE0, GOLD = 0xFFD86B;
    // paladin: holy light
    static final int HOLY = 0xFFD95A, HOLY_W = 0xFFF6D8;
    // assassin: shadow and violet
    static final int SHADOW = 0x9B4DCA, SHADOW_L = 0xE07CFF, DARK = 0x150A1E, POISON = 0x7DFF4A;

    static boolean play(P p) {
        switch (p.fx()) {
            case "power_slash" -> powerSlash(p);
            case "charge" -> charge(p);
            case "war_cry" -> warCry(p);
            case "whirlwind" -> whirlwind(p);
            case "berserk" -> berserk(p);
            case "earthshatter" -> earthshatter(p);
            case "multishot" -> multishot(p);
            case "backstep" -> backstep(p);
            case "hunters_mark" -> huntersMark(p);
            case "mark" -> mark(p);
            case "snare" -> snare(p);
            case "snare_catch" -> snareCatch(p);
            case "arrow_rain" -> arrowRain(p);
            case "eagle_eye" -> eagleEye(p);
            case "shield_bash" -> shieldBash(p);
            case "holy_guard" -> holyGuard(p);
            case "smite" -> smite(p);
            case "smite_hit" -> smiteHit(p);
            case "lay_on_hands" -> layOnHands(p);
            case "divine_aegis" -> divineAegis(p);
            case "judgement" -> judgement(p);
            case "judgement_strike" -> judgementStrike(p);
            case "shadow_step" -> shadowStep(p);
            case "backstab" -> backstab(p);
            case "smoke_bomb" -> smokeBomb(p);
            case "venom" -> venom(p);
            case "venom_drip" -> venomDrip(p);
            case "assassinate" -> assassinate(p);
            case "assassinate_hit" -> assassinateHit(p);
            case "shadow_dance" -> shadowDance(p);
            default -> {
                return false;
            }
        }
        return true;
    }

    /** Orientation of a horizontal swing in front of {@code dir}, tilted by {@code tilt} radians. */
    static Quaternionf swing(Vec3 dir, double tilt) {
        Vec3 side = Fx.right(dir);
        Vec3 right = side.scale(Math.cos(tilt)).add(0, Math.sin(tilt), 0);
        return Fx.basis(right, dir);
    }

    /** Point on a swing arc: local angle {@code a} (0 = right, pi/2 = straight ahead) at distance {@code r}. */
    static Vec3 arcPoint(Vec3 o, Vec3 dir, double tilt, double a, double r) {
        Vec3 side = Fx.right(dir);
        Vec3 right = side.scale(Math.cos(tilt)).add(0, Math.sin(tilt), 0);
        return o.add(right.scale(Math.cos(a) * r)).add(dir.scale(Math.sin(a) * r));
    }

    // ================================================================== fighter

    private static void powerSlash(P p) {
        Vec3 o = p.pos();
        Vec3 dir = flatDir(p.vec());
        float r = Math.max(2, p.a());
        double tilt = 0.32;
        Quaternionf q = swing(dir, tilt);
        Fx.p(S.SLASH, o).fixed(q).size(r * 0.95F).color(0xFFFFFF, EMBER).alpha(1, 0).angle(-0.9F).spin(0.3F).life(7).spawn();
        Fx.p(S.SLASH, o.add(0, 0.06, 0)).fixed(q).size(r * 1.1F).color(EMBER, BLOOD).alpha(0.6F, 0).angle(-1.05F).spin(0.28F).life(9).spawn();
        Fx.p(S.SLASH, o.add(0, -0.06, 0)).fixed(q).size(r * 0.72F).color(HOT).alpha(0.8F, 0).angle(-0.8F).spin(0.33F).life(5).spawn();
        FxAnims.run(6, age -> {
            double rot = -0.9 + age * 0.3;
            for (int i = 0, n = Fx.n(5); i < n; i++) {
                double a = Math.PI / 2 + rot + Fx.r(-1.1, 1.1);
                Vec3 at = arcPoint(o, dir, tilt, a, r * 0.86);
                Vec3 tangent = arcPoint(Vec3.ZERO, dir, tilt, a + Math.PI / 2, 1);
                Fx.p(S.GLOW, at).vel(tangent.scale(0.12).add(at.subtract(o).normalize().scale(0.08))).gravity(0.25F).friction(0.9F)
                        .color(HOT, EMBER).size(0.13F, 0).life(10 + Fx.RND.nextInt(6)).spawn();
            }
            return false;
        });
        hits(p.pts(), EMBER, 1);
    }

    private static void charge(P p) {
        Entity e = p.e();
        Vec3 dir = flatDir(p.vec());
        Vec3 start = feet(e, p.pos());
        ground(S.WAVE, start, 0.4F, EMBER, 10).size(0.4F, 2.4F).easeOut().alpha(0.8F, 0).spawn();
        for (int i = 0, n = Fx.n(8); i < n; i++) {
            smoke(start.add(Fx.r(-0.4, 0.4), 0.1, Fx.r(-0.4, 0.4)), 0x9A8C7A, 0.4F, 1.3F, 24).vel(dir.scale(-Fx.r(0.05, 0.2)).add(0, 0.03, 0)).spawn();
        }
        FxAnims.follow(e, 12, age -> {
            Vec3 c = chest(e, start);
            if (age % 2 == 0) {
                Fx.p(S.RING, c.add(dir.scale(1.0))).fixed(Fx.across(dir)).size(0.5F, 1.4F).color(HOT, EMBER).alpha(0.75F, 0).life(5).spawn();
            }
            for (int i = 0, n = Fx.n(6); i < n; i++) {
                double a = Fx.r(0, Math.PI * 2);
                Vec3 at = c.add(Fx.right(dir).scale(Math.cos(a) * 0.7)).add(0, Math.sin(a) * 0.8, 0).add(dir.scale(Fx.r(-0.2, 0.8)));
                Fx.p(S.STREAK, at).fixed(Fx.axial(at, dir)).vel(dir.scale(-0.5)).color(HOT, EMBER).size(0.45F, 0.2F).alpha(0.8F, 0).life(4).spawn();
            }
            Vec3 f = feet(e, start);
            if (age % 2 == 0) smoke(f.add(Fx.r(-0.3, 0.3), 0.1, Fx.r(-0.3, 0.3)), 0x9A8C7A, 0.3F, 1.0F, 18).vel(dir.scale(-0.1).add(0, 0.04, 0)).spawn();
            Fx.p(S.GLOW, c.add(Fx.randomUnit().scale(0.4))).vel(dir.scale(-0.15).add(0, 0.05, 0)).color(HOT, BLOOD).size(0.15F, 0).life(12).spawn();
            return false;
        });
    }

    private static void warCry(P p) {
        Entity e = p.e();
        Vec3 o = feet(e, p.pos());
        float r = Math.max(2, p.a());
        for (int k = 0; k < 3; k++) {
            ground(S.WAVE, o, 0.5F, 0xFF5533, 16).size(0.5F, r).color(0xFFB060, 0xFF3020).alpha(0.85F, 0).easeOut().delay(k * 4).spawn();
        }
        Vec3 h = head(e, o).add(0, -0.3, 0);
        for (int k = 0; k < 4; k++) {
            Fx.p(S.RING, h).flat().size(0.3F, 2.6F).color(0xFFE0B0, EMBER).alpha(0.9F, 0).easeOut().life(10).delay(k * 3).spawn();
        }
        flash(h, EMBER, 1.2F, 6);
        for (int i = 0, n = Fx.n(36); i < n; i++) {
            double a = Fx.r(0, Math.PI * 2), d = Fx.r(0.4, r * 0.6);
            Fx.p(S.GLOW, o.x + Math.cos(a) * d, o.y + Fx.r(0, 0.4), o.z + Math.sin(a) * d).vel(0, Fx.r(0.06, 0.16), 0)
                    .color(HOT, BLOOD).size(0.16F, 0).life(20 + Fx.RND.nextInt(15)).delay(Fx.RND.nextInt(8)).spawn();
        }
        for (Vec3 ally : p.pts()) {
            Vec3 f = ally.add(0, -0.9, 0);
            ground(S.RING, f, 0.3F, EMBER, 14).size(0.3F, 1.2F).alpha(0.8F, 0).delay(6).spawn();
            for (int i = 0; i < Fx.n(10); i++) {
                Fx.p(S.GLOW, f.add(Fx.r(-0.4, 0.4), Fx.r(0, 0.5), Fx.r(-0.4, 0.4))).vel(0, 0.12, 0).color(HOT, BLOOD).size(0.16F, 0).life(18).delay(6 + i).spawn();
            }
        }
        aura(e, 40, 3, EMBER, S.GLOW, 0.12F);
    }

    private static void whirlwind(P p) {
        Entity e = p.e();
        float r = Math.max(2, p.a());
        Vec3 o0 = feet(e, p.pos());
        ground(S.WAVE, o0, 1, EMBER, 9).size(1, r).easeOut().alpha(0.7F, 0).spawn();
        ground(S.WAVE, o0, 1, EMBER, 9).size(1, r * 1.05F).easeOut().alpha(0.7F, 0).delay(10).follow(e).spawn();
        FxAnims.follow(e, 18, age -> {
            Vec3 o = chest(e, o0).add(0, -0.3, 0);
            float rot = age * 0.95F;
            for (int k = 0; k < 2; k++) {
                double tilt = (k == 0 ? 0.12 : -0.12) + Math.sin(age * 0.5) * 0.05;
                Quaternionf q = swing(new Vec3(0, 0, 1), tilt);
                Fx.p(S.SLASH, o).fixed(q).angle(rot + k * (float) Math.PI).size(r * 0.85F).color(0xFFFFFF, EMBER).alpha(0.85F, 0).spin(0.45F).life(3).spawn();
            }
            Fx.p(S.SLASH, o.add(0, 0.1, 0)).fixed(swing(new Vec3(0, 0, 1), 0)).angle(rot + 1.6F).size(r * 1.0F).color(EMBER, BLOOD).alpha(0.4F, 0).spin(0.45F).life(4).spawn();
            for (int i = 0, n = Fx.n(4); i < n; i++) {
                double a = rot + Fx.r(0, Math.PI * 2);
                Vec3 at = o.add(Math.cos(a) * r * 0.8, Fx.r(-0.4, 0.3), Math.sin(a) * r * 0.8);
                Vec3 tan = new Vec3(-Math.sin(a), 0, Math.cos(a));
                Fx.p(S.GLOW, at).vel(tan.scale(0.25).add(0, 0.04, 0)).friction(0.88F).color(HOT, EMBER).size(0.14F, 0).life(10).spawn();
            }
            if (age % 2 == 0) {
                Vec3 f = feet(e, o0);
                double a = Fx.r(0, Math.PI * 2);
                smoke(f.add(Math.cos(a) * r * 0.6, 0.1, Math.sin(a) * r * 0.6), 0xA09080, 0.4F, 1.1F, 16).vel(-Math.sin(a) * 0.15, 0.03, Math.cos(a) * 0.15).spawn();
            }
            return false;
        });
    }

    private static void berserk(P p) {
        Entity e = p.e();
        Vec3 o = feet(e, p.pos());
        int dur = (int) (p.a() * 20);
        flash(chest(e, o), 0xFF3020, 1.6F, 7);
        ground(S.WAVE, o, 0.5F, BLOOD, 14).size(0.5F, 3.5F).easeOut().color(0xFF6040, 0x801010).alpha(0.9F, 0).spawn();
        burst(chest(e, o), 24, 0.45, S.SPARK, 0xFFD0A0, BLOOD, 0.35F, 10, 0.2F);
        FxAnims.follow(e, 30, age -> {
            Vec3 f = feet(e, o);
            for (int i = 0; i < 2; i++) {
                double a = age * 0.5 + i * Math.PI;
                Vec3 at = f.add(Math.cos(a) * 0.7, age * 0.06, Math.sin(a) * 0.7);
                smoke(at, 0x400808, 0.5F, 1.2F, 20).vel(0, 0.05, 0).spawn();
                Fx.p(S.GLOW, at).color(0xFF6A30, BLOOD).size(0.25F, 0).life(14).spawn();
            }
            return false;
        });
        FxAnims.follow(e, dur, age -> {
            if (age % 3 == 0) {
                Vec3 f = feet(e, o);
                Fx.p(S.GLOW, f.add(Fx.r(-0.35, 0.35), Fx.r(0.1, 1.6), Fx.r(-0.35, 0.35))).vel(0, Fx.r(0.04, 0.09), 0)
                        .color(0xFFB070, BLOOD).size(0.14F, 0).life(14).spawn();
            }
            if (age % 12 == 0) smoke(feet(e, o).add(0, 1, 0), 0x300606, 0.6F, 1.2F, 18).alpha(0.4F, 0).vel(0, 0.04, 0).spawn();
            return false;
        });
    }

    private static void earthshatter(P p) {
        Vec3 o = p.pos();
        float r = Math.max(2, p.a());
        FxAnims.shake(o, 1.6F, 14, 18);
        flash(o.add(0, 0.5, 0), 0xFFB060, 2.8F, 7);
        ground(S.WAVE, o, 1, 0xFFB060, 12).size(1, r).color(0xFFE0A0, 0xAA5522).alpha(0.95F, 0).spawn();
        ground(S.RING, o, 1, 0xFFB060, 14).size(1, r * 1.1F).alpha(0.7F, 0).delay(2).spawn();
        Fx.vanilla(ParticleTypes.EXPLOSION, o.x, o.y + 0.5, o.z, 0, 0, 0);
        debris(o, 30, 1.2, 0.55);
        int cracks = 10;
        double[] angles = new double[cracks];
        for (int i = 0; i < cracks; i++) angles[i] = Math.PI * 2 * i / cracks + Fx.r(-0.2, 0.2);
        FxAnims.run(14, age -> {
            double front = 1.0 + age * (r / 12.0);
            if (front > r) return true;
            for (double a : angles) {
                double wob = Math.sin(front * 2.1 + a * 3) * 0.25;
                Vec3 at = o.add(Math.cos(a + wob / front) * front, 0, Math.sin(a + wob / front) * front);
                ground(S.GLOW, at, 0.32F, 0xFF7A2A, 40).color(0xFFD070, 0x802000).alpha(0.9F, 0).spawn();
                if (Fx.RND.nextInt(3) == 0) debris(at, 3, 0.2, 0.45);
            }
            for (int i = 0, n = Fx.n(10); i < n; i++) {
                double a = Fx.r(0, Math.PI * 2);
                Vec3 at = o.add(Math.cos(a) * front, 0.1, Math.sin(a) * front);
                smoke(at, 0x8A7A68, 0.5F, 1.5F, 26).vel(Math.cos(a) * 0.05, Fx.r(0.02, 0.08), Math.sin(a) * 0.05).spawn();
            }
            return false;
        });
        hits(p.pts(), EMBER, 2);
    }

    // ================================================================== ranger

    private static void multishot(P p) {
        Vec3 o = p.pos();
        Vec3 dir = p.vec().normalize();
        Fx.p(S.RING, o.add(dir.scale(0.4))).fixed(Fx.across(dir)).size(0.2F, 1.3F).color(0xFFFFFF, LEAF).alpha(0.9F, 0).easeOut().life(7).spawn();
        Fx.p(S.RING, o.add(dir.scale(0.8))).fixed(Fx.across(dir)).size(0.1F, 0.8F).color(WIND).alpha(0.8F, 0).easeOut().life(6).delay(1).spawn();
        spray(o, dir, 0.35, 14, 0.7, S.GLOW, 0xFFFFFF, LEAF, 0.14F, 6, 0);
        for (int i = 0, n = Fx.n(7); i < n; i++) {
            Fx.p(S.PETAL, o).vel(dir.scale(Fx.r(0.1, 0.3)).add(Fx.randomUnit().scale(0.15))).gravity(0.04F).friction(0.92F)
                    .color(Fx.mix(0x7FD957, 0xC8F07A, Fx.RND.nextDouble())).alpha(1, 0).size(0.14F).randomAngle().spin(Fx.rf(-0.3F, 0.3F)).life(28).spawn();
        }
    }

    private static void backstep(P p) {
        Entity e = p.e();
        Vec3 o = p.pos();
        Vec3 away = flatDir(p.vec());
        ground(S.WAVE, o, 0.3F, WIND, 12).size(0.3F, 2.2F).color(0xFFFFFF, LEAF).alpha(0.8F, 0).easeOut().spawn();
        for (int i = 0, n = Fx.n(10); i < n; i++) {
            double a = Fx.r(0, Math.PI * 2);
            Fx.p(S.PETAL, o.add(Math.cos(a) * 0.4, Fx.r(0.1, 1.2), Math.sin(a) * 0.4)).vel(Math.cos(a) * 0.15, Fx.r(0.05, 0.15), Math.sin(a) * 0.15)
                    .gravity(0.03F).friction(0.9F).color(Fx.mix(0x7FD957, 0xC8F07A, Fx.RND.nextDouble())).alpha(1, 0).size(0.13F).randomAngle()
                    .spin(Fx.rf(-0.3F, 0.3F)).life(30).spawn();
        }
        FxAnims.follow(e, 9, age -> {
            Vec3 c = chest(e, o);
            for (int i = 0, n = Fx.n(4); i < n; i++) {
                Vec3 at = c.add(Fx.randomUnit().scale(0.5));
                Fx.p(S.STREAK, at).fixed(Fx.axial(at, away)).vel(away.scale(-0.2)).color(WIND, LEAF).size(0.4F, 0.1F).alpha(0.7F, 0).life(5).spawn();
            }
            return false;
        });
    }

    private static void huntersMark(P p) {
        Vec3 eye = p.pos();
        Vec3 dir = flatDir(p.vec());
        float range = Math.max(4, p.a());
        int steps = 8;
        FxAnims.run(steps + 1, age -> {
            double d = (age + 1) / (double) steps * range;
            int n = Fx.n(14);
            for (int i = 0; i < n; i++) {
                double a = Math.toRadians(-45 + 90.0 * i / (n - 1));
                Vec3 v = Fx.rotY(dir, a).scale(d);
                Fx.p(S.GLOW, eye.add(v).add(0, -0.6 + Fx.r(-0.2, 0.2), 0)).color(GOLD, LEAF).size(0.2F, 0).alpha(0.8F, 0).life(6).spawn();
            }
            return false;
        });
        Fx.p(S.RING, eye.add(dir.scale(0.6))).fixed(Fx.across(dir)).size(0.2F, 1.0F).color(GOLD).alpha(0.9F, 0).life(8).spawn();
        for (Vec3 t : p.pts()) {
            int delay = (int) (eye.distanceTo(t) / range * steps);
            flash(t, GOLD, 0.8F, 5);
            Fx.p(S.SPARK, t).color(0xFFFFFF, 0xFF5A3C).size(1.2F, 0.1F).life(8).delay(delay).spawn();
        }
    }

    /** Reticle above a marked enemy for the mark's duration. */
    private static void mark(P p) {
        Entity e = p.e();
        if (e == null) return;
        int life = (int) (p.a() * 20);
        Vec3 h = head(e, p.pos()).add(0, 0.55, 0);
        Fx.p(S.RING, h).size(0.32F).color(0xFF5A3C).alpha(0.9F, 0.9F).fadeIn(0.03F).life(life).follow(e).spawn();
        Fx.p(S.SPARK, h).size(0.5F).color(0xFFD86B).alpha(0.9F, 0.9F).spin(0.12F).fadeIn(0.03F).life(life).follow(e).spawn();
        Fx.p(S.GLOW, h).size(0.25F).color(0xFF5A3C).alpha(0.5F, 0.4F).life(life).follow(e).spawn();
    }

    private static void snare(P p) {
        Vec3 at = p.pos();
        Vec3 from = p.vec();
        float r = Math.max(1.5F, p.a());
        int life = (int) p.b();
        int fly = 6;
        FxAnims.run(fly, age -> {
            for (int s = 0; s < 3; s++) {
                double t = (age + s / 3.0) / fly;
                Vec3 v = from.add(at.subtract(from).scale(t)).add(0, Math.sin(t * Math.PI) * 1.5, 0);
                Fx.p(S.GLOW, v).color(0xFFFFFF, LEAF).size(0.22F, 0).life(8).spawn();
            }
            return false;
        });
        FxAnims.later(fly, () -> {
            flash(at.add(0, 0.3, 0), LEAF, 1.4F, 6);
            ground(S.CIRCLE_NATURE, at, r, LEAF, life).color(0xB8FF90, 0x3A9A2A).alpha(0.9F, 0.2F).spin(0.02F).fadeIn(0.04F).size(r * 0.4F, r).easeOut().spawn();
            ground(S.RING, at, r, LEAF, life).alpha(0.8F, 0.1F).size(r * 1.04F).spawn();
            circle(at, r, Fx.n(16), 0, v -> {
                Fx.p(S.STREAK, v.add(0, 0.5, 0)).upright().color(0xFFFFFF, LEAF).size(0.2F, 0.6F).alpha(1, 0).vel(0, 0.06, 0).life(10).spawn();
                Fx.p(S.SHARD, v.add(0, 0.25, 0)).upright().color(0xB8FF90, 0x2E7A20).size(0.32F).alpha(0.9F, 0).fadeIn(0.05F).life(life).spawn();
            });
            debris(at, 12, r * 0.6, 0.3);
        });
        FxAnims.run(fly + life, age -> {
            if (age < fly || age % 4 != 0) return false;
            for (int i = 0, n = Fx.n(3); i < n; i++) {
                double a = Fx.r(0, Math.PI * 2), d = Math.sqrt(Fx.RND.nextDouble()) * r;
                Fx.p(S.GLOW, at.add(Math.cos(a) * d, 0.1, Math.sin(a) * d)).vel(0, 0.04, 0).color(0xD0FFB0, LEAF).size(0.14F, 0).life(20).spawn();
            }
            return false;
        });
    }

    private static void snareCatch(P p) {
        for (Vec3 t : p.pts()) {
            Vec3 f = t.add(0, -0.8, 0);
            ground(S.RING, f, 0.8F, LEAF, 16).size(1.2F, 0.5F).alpha(1, 0).spawn();
            FxAnims.run(10, age -> {
                for (int k = 0; k < 2; k++) {
                    double a = age * 0.9 + k * Math.PI;
                    Fx.p(S.GLOW, f.add(Math.cos(a) * 0.5, age * 0.09, Math.sin(a) * 0.5)).color(0xD8FFB0, LEAF).size(0.16F, 0).life(12).spawn();
                }
                return false;
            });
        }
    }

    private static void arrowRain(P p) {
        Vec3 at = p.pos();
        float r = Math.max(2, p.a());
        int life = (int) p.b();
        double height = 14;
        ground(S.RING, at, r, LEAF, life + 10).alpha(0.8F, 0.2F).fadeIn(0.05F).spawn();
        ground(S.CIRCLE_NATURE, at, r * 0.95F, LEAF, life + 10).alpha(0.45F, 0).fadeIn(0.05F).spin(0.01F).spawn();
        Fx.p(S.RING, at.x, at.y + height, at.z).flat().size(r).color(WIND).alpha(0.4F, 0).fadeIn(0.1F).life(life).spawn();
        FxAnims.run(life, age -> {
            for (int i = 0, n = Fx.n(4); i < n; i++) {
                double a = Fx.r(0, Math.PI * 2), d = Math.sqrt(Fx.RND.nextDouble()) * r;
                Vec3 ground = at.add(Math.cos(a) * d, 0, Math.sin(a) * d);
                double speed = 1.6;
                int fall = (int) (height / speed);
                Vec3 top = ground.add(0, height, 0);
                Fx.p(S.STREAK, top).upright().vel(0, -speed, 0).color(0xFFFFFF, LEAF).size(0.7F).alpha(0.95F, 0.6F).life(fall).spawn();
                Fx.p(S.GLOW, top).vel(0, -speed, 0).color(WIND).size(0.18F).alpha(0.7F, 0.5F).life(fall).spawn();
                int d0 = fall;
                Fx.p(S.SPARK, ground.add(0, 0.1, 0)).color(0xFFFFFF, LEAF).size(0.4F, 0).randomAngle().life(4).delay(d0).spawn();
                Fx.p(S.GLOW, ground.add(0, 0.1, 0)).vel(Fx.r(-0.05, 0.05), 0.06, Fx.r(-0.05, 0.05)).gravity(0.3F).color(LEAF).size(0.12F, 0).life(8).delay(d0).spawn();
            }
            return false;
        });
    }

    private static void eagleEye(P p) {
        Entity e = p.e();
        Vec3 o = feet(e, p.pos());
        int dur = (int) (p.a() * 20);
        flash(head(e, o).add(0, -0.3, 0), GOLD, 1.3F, 7);
        Fx.p(S.RING, head(e, o).add(0, -0.25, 0)).size(0.3F, 1.6F).color(GOLD).alpha(0.9F, 0).easeOut().life(9).spawn();
        for (int i = 0, n = Fx.n(16); i < n; i++) {
            double a = Math.PI * 2 * i / 16;
            Fx.p(S.FEATHER, o.add(Math.cos(a) * 0.9, 0.2, Math.sin(a) * 0.9)).vel(Math.cos(a + 1.2) * 0.08, Fx.r(0.08, 0.14), Math.sin(a + 1.2) * 0.08)
                    .friction(0.95F).color(0xFFFFFF, GOLD).size(0.2F).alpha(1, 0).randomAngle().spin(Fx.rf(-0.2F, 0.2F)).life(30).follow(e).spawn();
        }
        FxAnims.follow(e, 20, age -> {
            for (int k = 0; k < 2; k++) {
                double a = age * 0.6 + k * Math.PI;
                Fx.p(S.GLOW, feet(e, o).add(Math.cos(a) * 0.6, age * 0.1, Math.sin(a) * 0.6)).color(0xFFFFFF, GOLD).size(0.15F, 0).life(12).spawn();
            }
            return false;
        });
        aura(e, dur, 10, GOLD, S.SPARK, 0.25F);
    }

    // ================================================================== paladin

    private static void shieldBash(P p) {
        Vec3 o = p.pos();
        Vec3 dir = flatDir(p.vec());
        Quaternionf q = Fx.facing(dir);
        Fx.p(S.HEX, o.add(dir.scale(0.9))).fixed(q).size(0.3F, 1.2F).easeOut().color(HOLY_W, HOLY).alpha(1, 0).life(8).spawn();
        Fx.p(S.HEX, o.add(dir.scale(1.0))).fixed(q).size(0.5F, 0.9F).color(0xFFFFFF).alpha(0.7F, 0).life(4).spawn();
        Fx.p(S.RING, o.add(dir.scale(1.2))).fixed(q).vel(dir.scale(0.25)).size(0.6F, 2.0F).easeOut().color(HOLY).alpha(0.8F, 0).life(8).spawn();
        spray(o.add(dir.scale(1.0)), dir, 0.6, 16, 0.5, S.GLOW, 0xFFFFFF, HOLY, 0.15F, 8, 0.2F);
        hits(p.pts(), HOLY, 1);
    }

    /** The personal shield look of the paladin buffs: hex plates around the body that fade. */
    private static void hexShell(Entity e, Vec3 fallback, int color, int delay, float radius) {
        Vec3 c = chest(e, fallback);
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2 * i / 8;
            Vec3 n = new Vec3(Math.cos(a), 0, Math.sin(a));
            for (int k = -1; k <= 1; k++) {
                Vec3 nk = n.add(0, k * 0.55, 0).normalize();
                Fx.p(S.HEX, c.add(nk.scale(radius))).fixed(Fx.across(nk)).size(0.32F).color(HOLY_W, color).alpha(0.75F, 0).fadeIn(0.25F)
                        .life(22).delay(delay + i / 2).follow(e).spawn();
            }
        }
    }

    private static void holyGuard(P p) {
        Entity e = p.e();
        Vec3 o = feet(e, p.pos());
        float r = Math.max(2, p.a());
        ground(S.CIRCLE_HOLY, o, r * 0.6F, HOLY, 34).size(r * 0.3F, r * 0.6F).easeOut().alpha(0.9F, 0).spin(0.04F).spawn();
        ground(S.WAVE, o, 0.5F, HOLY, 14).size(0.5F, r).easeOut().alpha(0.6F, 0).spawn();
        hexShell(e, o, HOLY, 0, 1.0F);
        for (Vec3 a : p.pts()) {
            Vec3 f = a.add(0, -0.9, 0);
            ground(S.CIRCLE_HOLY, f, 0.9F, HOLY, 24).alpha(0.8F, 0).spin(0.05F).delay(4).spawn();
            hexShell(null, f, HOLY, 4, 0.9F);
        }
        aura(e, (int) (p.b() * 20), 8, HOLY, S.GLOW, 0.12F);
    }

    private static void smite(P p) {
        Entity t = p.e();
        Vec3 at = feet(t, p.pos());
        ground(S.CIRCLE_HOLY, at, 1.4F, HOLY, 12).size(0.2F, 1.4F).easeOut().alpha(1, 0.6F).spin(0.15F).follow(t).spawn();
        for (int i = 0, n = Fx.n(16); i < n; i++) {
            double a = Fx.r(0, Math.PI * 2), d = Fx.r(0.2, 1.2);
            Fx.p(S.GLOW, at.add(Math.cos(a) * d, 7 + Fx.r(0, 2), Math.sin(a) * d)).vel(-Math.cos(a) * d / 7, -1.1, -Math.sin(a) * d / 7)
                    .color(0xFFFFFF, HOLY).size(0.25F, 0.15F).alpha(0.9F, 0.6F).life(7).follow(t).spawn();
        }
    }

    private static void beam(Vec3 ground, float height, float width, int color, int life, int delay) {
        Vec3 mid = ground.add(0, height / 2, 0);
        Fx.p(S.STREAK, mid).upright().size(height / 2).color(0xFFFFFF, color).alpha(1, 0).life(life).delay(delay).spawn();
        Fx.p(S.STREAK, mid).upright().size(height / 2 * 1.02F).color(color).alpha(0.6F, 0).life(life + 2).delay(delay).spawn();
        for (int i = 0; i < 3; i++) {
            Fx.p(S.GLOW, ground.add(0, height * (0.15 + i * 0.3), 0)).size(width * 2.5F, width).color(color).alpha(0.4F, 0).life(life).delay(delay).spawn();
        }
    }

    private static void smiteHit(P p) {
        Vec3 at = p.pos();
        float r = Math.max(1, p.a());
        beam(at, 12, 0.6F, HOLY, 9, 0);
        flash(at.add(0, 0.8, 0), HOLY, 2.4F, 6);
        ground(S.WAVE, at, 0.5F, HOLY, 12).size(0.5F, r * 1.5F).easeOut().color(HOLY_W, HOLY).alpha(0.9F, 0).spawn();
        ground(S.CIRCLE_HOLY, at, r, HOLY, 16).alpha(0.9F, 0).spin(-0.08F).spawn();
        burst(at.add(0, 0.6, 0), 26, 0.5, S.GLOW, 0xFFFFFF, HOLY, 0.17F, 12, 0.15F);
        for (int i = 0; i < 3; i++) {
            Fx.p(S.CROSS, at.add(Fx.r(-0.5, 0.5), 1 + i * 0.4, Fx.r(-0.5, 0.5))).vel(0, 0.05, 0).color(0xFFFFFF, HOLY).size(0.3F, 0.1F).alpha(1, 0).life(18).delay(i * 2).spawn();
        }
        FxAnims.shake(at, 0.5F, 6, 10);
        hits(p.pts(), HOLY, 0);
    }

    private static void layOnHands(P p) {
        Entity e = p.e();
        Vec3 o = feet(e, p.pos());
        healAt(o, e, HOLY, 0);
        flash(chest(e, o), HOLY, 1.0F, 6);
        for (Vec3 a : p.pts()) {
            Vec3 f = a.add(0, -0.9, 0);
            line(chest(e, o), a, 0.3, (v, t) -> Fx.p(S.GLOW, v).color(HOLY_W, HOLY).size(0.16F, 0).life(8).delay((int) (t * 6)).spawn());
            healAt(f, null, HOLY, 6);
        }
    }

    private static void divineAegis(P p) {
        Entity e = p.e();
        Vec3 o = feet(e, p.pos());
        float r = Math.max(2, p.a());
        ground(S.CIRCLE_HOLY, o, r, HOLY, 50).size(r * 0.2F, r).easeOut().alpha(0.95F, 0).spin(0.03F).spawn();
        ground(S.CIRCLE_HOLY, o, r * 0.5F, HOLY, 50).alpha(0.8F, 0).spin(-0.05F).delay(3).spawn();
        ground(S.WAVE, o, 1, HOLY, 16).size(1, r * 1.2F).easeOut().alpha(0.7F, 0).spawn();
        beam(o, 14, 0.8F, HOLY, 14, 0);
        hexShell(e, o, HOLY, 2, 1.1F);
        for (Vec3 a : p.pts()) {
            Vec3 f = a.add(0, -0.9, 0);
            beam(f, 10, 0.5F, HOLY, 12, 4);
            hexShell(null, f, HOLY, 6, 1.0F);
        }
        for (int i = 0, n = Fx.n(18); i < n; i++) {
            double a = Fx.r(0, Math.PI * 2), d = Fx.r(0.5, r);
            Fx.p(S.FEATHER, o.add(Math.cos(a) * d, Fx.r(3, 5), Math.sin(a) * d)).vel(Fx.r(-0.02, 0.02), -0.06, Fx.r(-0.02, 0.02))
                    .color(0xFFFFFF, HOLY).size(0.22F).alpha(1, 0).fadeIn(0.1F).randomAngle().spin(Fx.rf(-0.12F, 0.12F)).life(60).spawn();
        }
        FxAnims.shake(o, 0.4F, 8, 12);
        aura(e, (int) (p.b() * 20), 6, HOLY, S.GLOW, 0.14F);
    }

    private static void judgement(P p) {
        Vec3 o = p.pos();
        float r = Math.max(2, p.a());
        int build = 16;
        FxParticle big = ground(S.CIRCLE_HOLY, o, 0.1F, HOLY, 1000).alpha(0.95F, 0.95F).spin(0.05F).spawn();
        FxParticle inner = ground(S.CIRCLE_HOLY, o.add(0, 0.02, 0), 0.1F, HOLY_W, 1000).alpha(0.8F, 0.8F).spin(-0.09F).spawn();
        FxAnims.run(build + 1, age -> {
            float t = Math.min(1, age / (float) build);
            float ease = 1 - (1 - t) * (1 - t);
            if (big != null) big.sizeNow(r * ease);
            if (inner != null) inner.sizeNow(r * 0.55F * ease);
            circle(o, r * ease, Fx.n(8), age * 0.05, v -> Fx.p(S.GLOW, v.add(0, 0.1, 0)).vel(0, 0.25, 0).color(0xFFFFFF, HOLY).size(0.22F, 0).life(8).spawn());
            for (int i = 0, n = Fx.n(4); i < n; i++) {
                double a = Fx.r(0, Math.PI * 2);
                Vec3 from = o.add(Math.cos(a) * r, 0.2, Math.sin(a) * r);
                Fx.p(S.GLOW, from).vel(o.subtract(from).scale(0.08).add(0, 0.05, 0)).color(HOLY).size(0.14F, 0).life(12).spawn();
            }
            if (age == build) {
                if (big != null) big.fadeOut(14);
                if (inner != null) inner.fadeOut(10);
            }
            return false;
        });
    }

    private static void judgementStrike(P p) {
        Vec3 o = p.pos();
        float r = Math.max(2, p.a());
        beam(o, 18, 1.2F, HOLY, 12, 0);
        circle(o, r * 0.8, 8, 0.3, v -> beam(v, 10, 0.5F, HOLY, 10, Fx.RND.nextInt(3)));
        flash(o.add(0, 1, 0), HOLY, 4.5F, 8);
        ground(S.WAVE, o, 1, HOLY, 14).size(1, r * 1.4F).easeOut().color(0xFFFFFF, HOLY).alpha(1, 0).spawn();
        ground(S.RING, o, 1, HOLY, 12).size(1, r * 1.2F).easeOut().alpha(0.8F, 0).delay(2).spawn();
        burst(o.add(0, 0.8, 0), 44, 0.8, S.GLOW, 0xFFFFFF, HOLY, 0.2F, 14, 0.1F);
        for (int i = 0; i < Fx.n(6); i++) {
            double a = Fx.r(0, Math.PI * 2), d = Fx.r(0.5, r * 0.7);
            Fx.p(S.CROSS, o.add(Math.cos(a) * d, 0.8, Math.sin(a) * d)).vel(0, 0.07, 0).color(0xFFFFFF, HOLY).size(0.35F, 0.1F).alpha(1, 0).life(20).delay(i).spawn();
        }
        FxAnims.shake(o, 1.2F, 12, 16);
        hits(p.pts(), HOLY, 1);
    }

    // ================================================================== assassin

    private static void puff(Vec3 at, int count, float size) {
        for (int i = 0, n = Fx.n(count); i < n; i++) {
            smoke(at.add(Fx.randomUnit().scale(0.3)), Fx.mix(DARK, 0x3A1A4A, Fx.RND.nextDouble()), size * 0.6F, size * 1.6F, 22 + Fx.RND.nextInt(10))
                    .vel(Fx.randomUnit().scale(0.08).add(0, 0.02, 0)).spawn();
        }
    }

    private static void shadowStep(P p) {
        Vec3 from = p.pos();
        Vec3 to = p.vec();
        for (int i = 0; i < 6; i++) puff(from.add(0, 0.3 * i, 0), 2, 0.8F);
        flash(from.add(0, 1, 0), SHADOW, 1.0F, 5);
        burst(from.add(0, 1, 0), 12, 0.3, S.GLOW, SHADOW_L, SHADOW, 0.14F, 10, 0);
        line(from.add(0, 1, 0), to.add(0, 1, 0), 0.4, (v, t) ->
                Fx.p(S.GLOW, v).color(SHADOW_L, SHADOW).size(0.2F, 0).alpha(0.8F, 0).life(8).delay((int) (t * 3)).spawn());
        FxAnims.later(2, () -> {
            puff(to.add(0, 0.8, 0), 10, 0.8F);
            flash(to.add(0, 1, 0), SHADOW_L, 1.2F, 5);
            Fx.p(S.SLASH, to.add(0, 1, 0)).size(0.9F).color(0xFFFFFF, SHADOW_L).alpha(1, 0).randomAngle().life(5).spawn();
            ground(S.RING, to, 0.3F, SHADOW, 10).size(0.3F, 1.6F).easeOut().alpha(0.8F, 0).spawn();
        });
    }

    /** Orientation of a slash cutting across {@code dir} at {@code roll} radians. */
    static Quaternionf crossCut(Vec3 dir, double roll) {
        Vec3 side = Fx.right(dir);
        Vec3 right = side.scale(Math.cos(roll)).add(0, Math.sin(roll), 0);
        Vec3 up = dir.cross(right).normalize();
        return Fx.basis(right, up);
    }

    private static void backstab(P p) {
        Vec3 at = p.pos();
        Vec3 dir = flatDir(p.vec());
        boolean behind = p.b() > 0;
        int color = behind ? 0xFF3050 : SHADOW_L;
        Vec3 c = at.subtract(dir.scale(0.3));
        Fx.p(S.SLASH, c).fixed(crossCut(dir, 0.8)).size(1.0F).color(0xFFFFFF, color).alpha(1, 0).angle(-0.6F).spin(0.35F).life(5).spawn();
        Fx.p(S.SLASH, c).fixed(crossCut(dir, -0.8)).size(1.0F).color(0xFFFFFF, color).alpha(1, 0).angle(-0.6F).spin(0.35F).life(5).delay(2).spawn();
        hits(java.util.List.of(at), color, 1);
        if (behind) {
            flash(at, 0xFF2040, 1.8F, 7);
            burst(at, 22, 0.45, S.SPARK, 0xFFFFFF, 0xFF2040, 0.3F, 9, 0.2F);
            puff(at, 6, 0.6F);
            Fx.p(S.RING, at).size(0.3F, 1.8F).easeOut().color(0xFF2040).alpha(0.9F, 0).life(8).spawn();
        }
    }

    private static void smokeBomb(P p) {
        Vec3 o = p.pos();
        float r = Math.max(2, p.a());
        flash(o.add(0, 0.6, 0), 0xFFFFFF, 1.2F, 4);
        ground(S.WAVE, o, 0.5F, 0x8A7A9A, 12).size(0.5F, r).easeOut().alpha(0.6F, 0).spawn();
        for (int i = 0, n = Fx.n(46); i < n; i++) {
            Vec3 v = Fx.randomUnit().multiply(1, 0.4, 1).normalize().scale(Fx.r(0.08, 0.32)).add(0, 0.03, 0);
            smoke(o.add(0, Fx.r(0.2, 1.4), 0), Fx.mix(0x1E1A24, 0x3E3448, Fx.RND.nextDouble()), Fx.rf(0.8F, 1.2F), Fx.rf(2.0F, 2.8F), 60 + Fx.RND.nextInt(30))
                    .vel(v).friction(0.86F).alpha(0.9F, 0).spawn();
        }
        for (int i = 0, n = Fx.n(14); i < n; i++) {
            Vec3 at = o.add(Fx.randomUnit().multiply(r * 0.6, 0.5, r * 0.6)).add(0, 1, 0);
            Fx.p(S.SPARK, at).color(0xFFFFFF, SHADOW_L).size(0.35F, 0).randomAngle().life(8).delay(4 + Fx.RND.nextInt(30)).spawn();
        }
    }

    private static void venom(P p) {
        Vec3 o = p.pos();
        Vec3 dir = flatDir(p.vec());
        Fx.p(S.SLASH, o.add(dir.scale(0.6))).fixed(swing(dir, -0.4)).size(p.a() * 0.6F).color(0xE0FFB0, POISON).alpha(0.9F, 0).angle(-0.7F).spin(0.3F).life(6).spawn();
        spray(o, dir.add(0, 0.25, 0), 0.45, 26, 0.45, S.GLOW, 0xE0FFB0, POISON, 0.17F, 16, 0.7F);
        for (int i = 0, n = Fx.n(6); i < n; i++) {
            smoke(o.add(dir.scale(Fx.r(0.5, 2.5))), 0x2A5A10, 0.4F, 1.2F, 24).alpha(0.5F, 0).vel(dir.scale(0.05).add(0, 0.02, 0)).spawn();
        }
        hits(p.pts(), POISON, 2);
    }

    private static void venomDrip(P p) {
        Entity e = p.e();
        int life = (int) (p.a() * 20);
        FxAnims.follow(e, life, age -> {
            if (age % 5 != 0) return false;
            Vec3 at = e.position().add(Fx.r(-0.3, 0.3), Fx.r(0.3, e.getBbHeight()), Fx.r(-0.3, 0.3));
            Fx.p(S.GLOW, at).gravity(0.5F).color(0xC8FF80, POISON).size(0.12F, 0.05F).alpha(0.9F, 0).life(14).spawn();
            if (age % 15 == 0) smoke(at, 0x2A5A10, 0.25F, 0.6F, 14).alpha(0.5F, 0).vel(0, 0.03, 0).spawn();
            return false;
        });
    }

    private static void assassinate(P p) {
        Vec3 from = p.pos();
        Vec3 to = p.vec();
        Entity t = p.e();
        for (int i = 0; i < 6; i++) puff(from.add(0, 0.3 * i, 0), 2, 0.9F);
        line(from.add(0, 1, 0), to.add(0, 1, 0), 0.3, (v, tt) -> {
            smoke(v, DARK, 0.5F, 0.9F, 12).alpha(0.8F, 0).spawn();
            Fx.p(S.GLOW, v).color(SHADOW_L, SHADOW).size(0.25F, 0).life(8).spawn();
        });
        FxAnims.run(7, age -> {
            Vec3 c = chest(t, to);
            Vec3 d = Fx.randomUnit();
            Fx.p(S.SLASH, c).fixed(Fx.across(d)).size(Fx.rf(0.8F, 1.2F)).color(0xFFFFFF, age % 2 == 0 ? SHADOW_L : 0xFF3050).alpha(1, 0).randomAngle().spin(0.4F).life(4).spawn();
            if (age % 2 == 0) Fx.p(S.SPARK, c.add(Fx.randomUnit().scale(0.4))).color(0xFFFFFF, SHADOW_L).size(0.6F, 0).randomAngle().life(4).spawn();
            return false;
        });
        FxAnims.shake(to, 0.4F, 8, 4);
    }

    private static void assassinateHit(P p) {
        Vec3 at = p.pos();
        boolean exec = p.b() > 0;
        int color = exec ? 0xFF2040 : SHADOW_L;
        flash(at, color, exec ? 3.2F : 2.2F, 8);
        Fx.p(S.RING, at).size(0.3F, 2.6F).easeOut().color(color).alpha(1, 0).life(9).spawn();
        Fx.p(S.SLASH, at).size(1.6F).color(0xFFFFFF, color).alpha(1, 0).angle(0.7F).life(6).spawn();
        Fx.p(S.SLASH, at).size(1.6F).color(0xFFFFFF, color).alpha(1, 0).angle(0.7F + (float) Math.PI).life(6).spawn();
        burst(at, 34, 0.55, S.SPARK, 0xFFFFFF, color, 0.32F, 10, 0.2F);
        puff(at, 10, 0.9F);
        if (exec) {
            Fx.p(S.SKULL, at.add(0, 0.6, 0)).vel(0, 0.04, 0).size(0.2F, 0.5F).alpha(1, 0).fadeIn(0.1F).life(26).spawn();
        }
        FxAnims.shake(at, exec ? 1.0F : 0.6F, 8, 6);
    }

    private static void shadowDance(P p) {
        Entity e = p.e();
        Vec3 o = feet(e, p.pos());
        int dur = (int) (p.a() * 20);
        Vec3 c = chest(e, o);
        for (int i = 0; i < 6; i++) {
            Fx.p(S.SLASH, c).fixed(swing(new Vec3(0, 0, 1), 0.2 * Math.sin(i))).angle((float) (Math.PI * 2 * i / 6)).size(1.6F)
                    .color(0xFFFFFF, SHADOW_L).alpha(0.9F, 0).spin(0.3F).life(8).delay(i).spawn();
        }
        puff(c, 16, 1.0F);
        ground(S.CIRCLE_HEX, o, 1.6F, SHADOW, 24).alpha(0.8F, 0).spin(0.1F).spawn();
        final Vec3[] last = {o};
        FxAnims.follow(e, dur, age -> {
            Vec3 f = e.position();
            if (age % 2 == 0 && f.distanceToSqr(last[0]) > 0.01) {
                for (int k = 0; k < 3; k++) {
                    smoke(f.add(0, 0.3 + k * 0.55, 0), 0x2A1238, 0.5F, 0.7F, 12).alpha(0.4F, 0).spawn();
                }
            }
            last[0] = f;
            if (age % 6 == 0) Fx.p(S.GLOW, f.add(Fx.r(-0.4, 0.4), Fx.r(0.2, 1.8), Fx.r(-0.4, 0.4))).color(SHADOW_L, SHADOW).size(0.14F, 0).life(12).spawn();
            return false;
        });
    }
}
