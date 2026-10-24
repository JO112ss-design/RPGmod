package com.jo112ss.rpgmod.client.fx;

import static com.jo112ss.rpgmod.client.fx.SkillFx.*;

import com.jo112ss.rpgmod.client.fx.Fx.S;
import com.jo112ss.rpgmod.client.fx.SkillFx.P;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * Building-block effects for the advanced jobs' skills ("x_*"). Each one takes its colour from the
 * message, so one shape serves many skills (a red slash for the berserker, a blue one for the
 * nightblade...). Parameters per effect are listed on each method.
 */
final class FxAdvanced {
    private FxAdvanced() {}

    static boolean play(P p) {
        switch (p.fx()) {
            case "x_slash" -> slash(p);
            case "x_ring" -> ring(p);
            case "x_aura" -> aura(p);
            case "x_zone" -> zone(p);
            case "x_beam" -> beam(p);
            case "x_bolt" -> bolt(p);
            case "x_orb" -> orb(p);
            case "x_hit" -> hit(p);
            case "x_burst" -> burst(p);
            case "x_dash" -> dash(p);
            case "x_pillar" -> pillar(p);
            case "x_strike" -> strike(p);
            case "x_wall" -> wall(p);
            case "x_orbit" -> orbit(p);
            case "x_tornado" -> tornado(p);
            case "x_spikes" -> spikes(p);
            case "x_mark" -> mark(p);
            case "x_swap" -> swap(p);
            case "x_link" -> link(p);
            case "x_star" -> star(p);
            case "x_aim" -> aim(p);
            case "x_wave" -> wave(p);
            case "x_decoy" -> decoy(p);
            default -> {
                return false;
            }
        }
        return true;
    }

    private static int light(int c) {
        return Fx.mix(c, 0xFFFFFF, 0.55);
    }

    private static int dark(int c) {
        return Fx.mix(c, 0x000000, 0.55);
    }

    // ------------------------------------------------------------------ melee

    /** pos = centre, vec = facing, a = size, b = tilt (radians; 1.5 = straight down). */
    private static void slash(P p) {
        int c = p.colorOr(0xFFFFFF);
        Vec3 at = p.pos();
        Vec3 dir = flatDir(p.vec());
        float size = Math.max(1, p.a());
        float tilt = p.b();
        Fx.p(S.SLASH, at).size(size * 0.55F).color(0xFFFFFF, c).alpha(0.95F, 0).angle(-tilt - 0.6F).spin(0.45F).life(5).spawn();
        Fx.p(S.SLASH, at).size(size * 0.7F).color(c, dark(c)).alpha(0.5F, 0).angle(-tilt - 0.75F).spin(0.42F).life(7).spawn();
        Vec3 side = Fx.right(dir);
        Vec3 axis = side.scale(Math.cos(tilt)).add(0, Math.sin(tilt), 0);
        for (int i = 0, n = Fx.n(10); i < n; i++) {
            double t = Fx.r(-1, 1);
            Vec3 v = at.add(axis.scale(t * size * 0.5)).add(dir.scale((1 - t * t) * 0.3));
            Fx.p(S.GLOW, v).vel(axis.scale(-0.05 * Math.signum(t)).add(Fx.randomUnit().scale(0.03))).friction(0.88F)
                    .color(0xFFFFFF, c).size(0.12F, 0).life(8 + Fx.RND.nextInt(5)).spawn();
        }
        hits(p.pts(), c, 1);
    }

    /** pos = feet, a = radius, b = number of waves. Shockwave rings along the ground. */
    private static void ring(P p) {
        int c = p.colorOr(0xFFFFFF);
        Vec3 o = p.pos();
        float r = Math.max(1, p.a());
        int waves = Math.max(1, (int) p.b());
        flash(o.add(0, 1, 0), c, 1.4F, 6);
        for (int k = 0; k < waves; k++) {
            ground(S.WAVE, o, 0.5F, c, 14).size(0.5F, r * 1.05F).color(light(c), c).alpha(0.9F, 0).easeOut().delay(k * 3).spawn();
        }
        circle(o, r * 0.3, Fx.n(18), Fx.r(0, 6), v -> {
            Vec3 out = v.subtract(o).normalize();
            Fx.p(S.GLOW, v.add(0, 0.2, 0)).vel(out.scale(r * 0.06).add(0, 0.05, 0)).friction(0.9F).color(0xFFFFFF, c).size(0.2F, 0).life(14).spawn();
        });
        hits(p.pts(), c, 3);
    }

    /**
     * Buff on an entity: a flash and a lasting aura. a = seconds, b = style (0 sparks, 1 embers / blood,
     * 2 shield hexes, 3 feathers / wind, 4 runes, 5 coins). pts = allies who got it too.
     */
    private static void aura(P p) {
        int c = p.colorOr(0xFFFFFF);
        Entity e = p.e();
        Vec3 o = feet(e, p.pos());
        int style = (int) p.b();
        int ticks = Math.max(10, (int) (p.a() * 20));
        Vec3 ch = chest(e, o);
        flash(ch, c, 1.3F, 6);
        ground(S.RING, o, 0.4F, c, 12).size(0.4F, 2.0F).easeOut().alpha(0.9F, 0).spawn();
        for (int i = 0, n = Fx.n(16); i < n; i++) {
            double a = Fx.r(0, Math.PI * 2), r = Fx.r(0.3, 0.7);
            Fx.p(S.GLOW, o.x + Math.cos(a) * r, o.y + Fx.r(0, 0.4), o.z + Math.sin(a) * r).vel(0, Fx.r(0.08, 0.16), 0)
                    .color(0xFFFFFF, c).size(0.16F, 0).life(16 + Fx.RND.nextInt(8)).follow(e).spawn();
        }
        if (style == 2) {
            for (int i = 0; i < 8; i++) {
                double a = Math.PI * 2 * i / 8;
                Vec3 n = new Vec3(Math.cos(a), 0, Math.sin(a));
                Fx.p(S.HEX, ch.add(n.scale(0.9))).fixed(Fx.across(n)).size(0.35F).color(light(c), c).alpha(0.8F, 0).fadeIn(0.2F).life(20).follow(e).spawn();
            }
        }
        if (e == null) return;
        S sprite = switch (style) {
            case 1 -> S.GLOW;
            case 2 -> S.HEX;
            case 3 -> S.FEATHER;
            case 4 -> Fx.rune(Fx.RND.nextInt(4));
            case 5 -> S.COIN;
            default -> S.SPARK;
        };
        int every = style == 1 ? 2 : 4;
        FxAnims.follow(e, ticks, age -> {
            if (age % every != 0) return false;
            double a = Fx.r(0, Math.PI * 2), r = e.getBbWidth() * 0.6 + 0.25;
            Vec3 v = new Vec3(e.getX() + Math.cos(a) * r, e.getY() + Fx.r(0.1, e.getBbHeight()), e.getZ() + Math.sin(a) * r);
            if (style == 1) {
                Fx.p(S.GLOW, v).vel(0, 0.06, 0).color(light(c), dark(c)).size(0.18F, 0).alpha(0.9F, 0).life(14).spawn();
            } else if (style == 5) {
                Fx.p(S.COIN, v).vel(0, 0.05, 0).size(0.12F).alpha(1, 0).coin().spin(0.4F).life(16).spawn();
            } else {
                Fx.p(style == 4 ? Fx.rune(age / 4) : sprite, v).vel(0, 0.03, 0).color(0xFFFFFF, c).size(style == 3 ? 0.18F : 0.14F, 0).life(18).spawn();
            }
            return false;
        });
        for (Vec3 v : p.pts()) healAt(v.subtract(0, 0.9, 0), null, c, 2);
    }

    /**
     * A circle on the ground for a while. a = radius, b = life (ticks), vec.x = circle kind (0 arcane,
     * 1 holy, 2 hex, 3 clock, 4 nature), vec.y = what rises from it (0 sparks, 1 flames, 2 snow,
     * 3 sparkles falling, 4 dust).
     */
    private static void zone(P p) {
        int c = p.colorOr(0xFFFFFF);
        Vec3 o = p.pos();
        float r = Math.max(1, p.a());
        int life = Math.max(10, (int) p.b());
        S circle = switch ((int) p.vec().x) {
            case 1 -> S.CIRCLE_HOLY;
            case 2 -> S.CIRCLE_HEX;
            case 3 -> S.CIRCLE_CLOCK;
            case 4 -> S.CIRCLE_NATURE;
            default -> S.CIRCLE_ARCANE;
        };
        int rise = (int) p.vec().y;
        // sprite size is the radius
        ground(circle, o, r, c, life).color(light(c), c).alpha(0.6F, 0.3F).fadeIn(0.08F).spin(0.01F).size(r * 0.8F, r).easeOut().spawn();
        ground(S.RING, o, r * 1.02F, c, life).color(c).alpha(0.5F, 0.2F).fadeIn(0.08F).spawn();
        FxAnims.run(life, age -> {
            int n = Fx.n(Math.max(1, (int) (r * r * 0.25)));
            for (int i = 0; i < n; i++) {
                double a = Fx.r(0, Math.PI * 2), d = Math.sqrt(Fx.RND.nextDouble()) * r;
                Vec3 v = o.add(Math.cos(a) * d, 0.1, Math.sin(a) * d);
                switch (rise) {
                    case 1 -> {
                        Fx.p(S.GLOW, v).vel(0, Fx.r(0.06, 0.14), 0).color(0xFFE0A0, c).size(Fx.rf(0.25F, 0.45F), 0).alpha(0.9F, 0).life(14).spawn();
                        if (Fx.RND.nextInt(4) == 0) Fx.vanilla(ParticleTypes.FLAME, v.x, v.y, v.z, 0, 0.04, 0);
                    }
                    case 2 -> Fx.vanilla(ParticleTypes.SNOWFLAKE, v.x, v.y + 1.5, v.z, Fx.r(-0.03, 0.03), -0.05, Fx.r(-0.03, 0.03));
                    case 3 -> Fx.p(S.SPARK, v.add(0, Fx.r(2, 4), 0)).vel(0, -0.15, 0).color(0xFFFFFF, c).size(0.14F, 0.05F).life(18).spawn();
                    case 4 -> smoke(v, dark(c), 0.3F, 0.8F, 20).vel(0, 0.02, 0).alpha(0.5F, 0).spawn();
                    default -> Fx.p(S.GLOW, v).vel(0, Fx.r(0.03, 0.07), 0).color(0xFFFFFF, c).size(0.12F, 0).life(18).spawn();
                }
            }
            return false;
        });
    }

    /** A straight beam from pos to vec, a = width. pts = enemies hit along it. */
    private static void beam(P p) {
        int c = p.colorOr(0xFFFFFF);
        Vec3 from = p.pos(), to = p.vec();
        float w = Math.max(0.05F, p.a());
        Vec3 dir = to.subtract(from).normalize();
        double len = from.distanceTo(to);
        int n = Math.max(1, (int) Math.ceil(len / 1.5));
        for (int i = 0; i < n; i++) {
            Vec3 a = from.add(dir.scale(len * i / n)), b = from.add(dir.scale(len * (i + 1) / n));
            segment(a, b, w).color(0xFFFFFF, c).alpha(1, 0).life(8).spawn();
            if (w > 0.2F) segment(a, b, w * 2.2F).color(c).alpha(0.35F, 0).life(10).spawn();
        }
        if (w > 0.2F) {
            Fx.p(S.RING, from.add(dir.scale(0.4))).fixed(Fx.across(dir)).size(0.2F, w * 3).easeOut().color(0xFFFFFF, c).alpha(1, 0).life(7).spawn();
            flash(from, c, w * 2, 4);
            flash(to, c, w * 3, 5);
        }
        hits(p.pts(), c, 1);
    }

    /** Homing projectile: pos = start, vec = velocity, a = range, b = size, entity = target. */
    private static void bolt(P p) {
        int c = p.colorOr(0xFFFFFF);
        float size = Math.max(0.15F, p.b());
        flash(p.pos(), c, size * 1.5F, 4);
        simProjectile(p.pos(), p.vec(), p.a(), p.e(), p.e() != null ? 0.4 : 0, new ProjDraw() {
            @Override
            public void step(Vec3 at, Vec3 dir, int n) {
                Fx.p(S.GLOW, at).color(light(c), c).size(size * 0.7F, 0).alpha(0.9F, 0).life(7).spawn();
            }

            @Override
            public void head(Vec3 at, Vec3 dir, int age) {
                Fx.p(S.ORB, at).color(0xFFFFFF, light(c)).size(size).alpha(1, 1).life(1).spawn();
                Fx.p(S.GLOW, at).color(c).size(size * 2.4F).alpha(0.5F, 0.4F).life(1).spawn();
            }
        });
    }

    /** Slow orb that only stops at blocks: pos = start, vec = velocity, a = range, b = size. */
    private static void orb(P p) {
        int c = p.colorOr(0xFFFFFF);
        float size = Math.max(0.3F, p.b());
        Vec3 vel = p.vec();
        double speed = Math.max(0.05, vel.length());
        Vec3 dir = vel.normalize();
        final Vec3[] pos = {p.pos()};
        final double[] gone = {0};
        var level = Minecraft.getInstance().level;
        FxAnims.run((int) (p.a() / speed) + 2, age -> {
            if (level == null) return true;
            Vec3 to = pos[0].add(dir.scale(speed));
            BlockHitResult hit = level.clip(new ClipContext(pos[0], to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, Minecraft.getInstance().player));
            boolean wall = hit.getType() != HitResult.Type.MISS;
            pos[0] = wall ? hit.getLocation() : to;
            gone[0] += speed;
            Vec3 at = pos[0];
            Fx.p(S.ORB, at).color(light(c), c).size(size).alpha(1, 1).life(1).spawn();
            Fx.p(S.GLOW, at).color(c, dark(c)).size(size * 3).alpha(0.5F, 0.4F).life(1).spawn();
            for (int i = 0, n = Fx.n(4); i < n; i++) {
                Vec3 off = Fx.randomUnit().scale(2.2);
                Fx.p(S.GLOW, at.add(off)).vel(off.scale(-0.08)).color(light(c), dark(c)).size(0.14F, 0).life(12).spawn();
            }
            if (age % 3 == 0) smoke(at, 0x10001A, 0.4F, 1.0F, 16).alpha(0.6F, 0).spawn();
            return wall || gone[0] >= p.a();
        });
    }

    /** Impact: pos, vec = direction, a = size. */
    private static void hit(P p) {
        int c = p.colorOr(0xFFFFFF);
        Vec3 at = p.pos();
        Vec3 dir = p.vec().lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : p.vec().normalize();
        float s = Math.max(0.3F, p.a());
        flash(at, c, s * 1.4F, 5);
        Fx.p(S.RING, at).fixed(Fx.across(dir)).size(0.2F, s * 1.6F).easeOut().color(0xFFFFFF, c).alpha(1, 0).life(7).spawn();
        Fx.p(S.SPARK, at).color(0xFFFFFF, light(c)).size(s, 0).randomAngle().life(5).spawn();
        spray(at, dir, 0.8, (int) (10 * s), 0.4, S.GLOW, 0xFFFFFF, c, 0.12F, 10, 0.2F);
    }

    /**
     * Explosion: pos, a = radius, b = style (0 fire, 1 ice, 2 holy, 3 dark / blood, 4 trick).
     * pts = enemies hit.
     */
    private static void burst(P p) {
        int c = p.colorOr(0xFFFFFF);
        Vec3 at = p.pos();
        float r = Math.max(1, p.a());
        int style = (int) p.b();
        flash(at.add(0, 0.5, 0), c, r * 1.1F, 6);
        ground(S.WAVE, at, 0.5F, c, 12).size(0.5F, r * 1.4F).easeOut().color(light(c), c).alpha(1, 0).spawn();
        SkillFx.burst(at.add(0, 0.5, 0), (int) (12 * r), 0.35 + r * 0.08, S.GLOW, light(c), c, 0.2F, 14, style == 0 ? 0.3F : 0.1F);
        switch (style) {
            case 0 -> {
                Fx.vanilla(ParticleTypes.EXPLOSION, at.x, at.y + 0.5, at.z, 0, 0, 0);
                for (int i = 0, n = Fx.n((int) (r * 4)); i < n; i++) {
                    smoke(at.add(Fx.randomUnit().scale(r * 0.4)).add(0, 0.5, 0), 0x3A2A20, 0.6F, 1.8F, 34).vel(Fx.randomUnit().scale(0.05).add(0, 0.06, 0)).alpha(0.7F, 0).spawn();
                }
                for (int i = 0, n = Fx.n((int) (r * 5)); i < n; i++) {
                    double a = Fx.r(0, Math.PI * 2), d = Fx.r(0, r);
                    Fx.p(S.GLOW, at.add(Math.cos(a) * d, 0.1, Math.sin(a) * d)).vel(0, Fx.r(0.15, 0.35), 0).gravity(0.3F)
                            .color(0xFFE0A0, c).size(0.35F, 0).life(16).spawn();
                }
                debris(at, (int) (r * 4), r * 0.4, 0.6);
                FxAnims.shake(at, 0.4F, 6, 14);
            }
            case 1 -> {
                for (int i = 0, n = Fx.n((int) (r * 6)); i < n; i++) {
                    double a = Fx.r(0, Math.PI * 2), d = Fx.r(0.3, r);
                    Vec3 v = at.add(Math.cos(a) * d, 0, Math.sin(a) * d);
                    Fx.p(S.SHARD, v).upright().vel(0, Fx.r(0.05, 0.2), 0).gravity(0.4F).color(0xFFFFFF, c).size(Fx.rf(0.3F, 0.6F), 0.1F).life(20).delay((int) (d * 1.5)).spawn();
                }
                for (int i = 0; i < 12; i++) Fx.vanilla(ParticleTypes.SNOWFLAKE, at.x + Fx.r(-r, r), at.y + 1, at.z + Fx.r(-r, r), 0, 0.05, 0);
            }
            case 2 -> {
                for (int i = 0; i < 6; i++) {
                    Fx.p(S.STREAK, at.add(Fx.r(-r * 0.5, r * 0.5), 2.5, Fx.r(-r * 0.5, r * 0.5))).upright().vel(0, -0.3, 0)
                            .color(0xFFFFFF, c).size(0.6F, 0.3F).alpha(0.9F, 0).life(9).delay(i).spawn();
                }
                Fx.p(S.CIRCLE_HOLY, at.add(0, 0.08, 0)).flat().size(r * 0.5F, r * 1.2F).easeOut().color(0xFFFFFF, c).alpha(1, 0).life(14).spawn();
            }
            case 4 -> {
                for (int i = 0, n = Fx.n(10); i < n; i++) {
                    Fx.p(S.CARD, at.add(0, 0.6, 0)).vel(Fx.randomUnit().scale(Fx.r(0.2, 0.5)).add(0, 0.15, 0)).gravity(0.3F).friction(0.92F)
                            .size(0.18F).alpha(1, 0).coin().spin(0.6F).life(24).spawn();
                }
                for (int i = 0, n = Fx.n((int) (r * 4)); i < n; i++) {
                    smoke(at.add(Fx.randomUnit().scale(r * 0.4)).add(0, 0.5, 0), 0x40206A, 0.6F, 1.6F, 28).vel(Fx.randomUnit().scale(0.05)).alpha(0.7F, 0).spawn();
                }
                FxAnims.shake(at, 0.3F, 5, 12);
            }
            default -> {
                for (int i = 0, n = Fx.n((int) (r * 5)); i < n; i++) {
                    smoke(at.add(Fx.randomUnit().scale(r * 0.3)).add(0, 0.5, 0), dark(c), 0.5F, 1.4F, 26).vel(Fx.randomUnit().scale(0.08)).alpha(0.8F, 0).spawn();
                }
                Fx.p(S.RING, at.add(0, 0.6, 0)).size(0.3F, r * 1.3F).easeOut().color(light(c), c).alpha(0.9F, 0).life(10).spawn();
            }
        }
        hits(p.pts(), c, 2);
    }

    /** Afterimage dash from pos to vec. */
    private static void dash(P p) {
        int c = p.colorOr(0xFFFFFF);
        Vec3 a = p.pos().add(0, 1, 0), b = p.vec().add(0, 1, 0);
        Vec3 dir = b.subtract(a);
        double len = dir.length();
        if (len < 0.1) return;
        dir = dir.normalize();
        line(a, b, 0.35, (v, t) -> {
            Fx.p(S.GLOW, v.add(Fx.randomUnit().scale(0.15))).color(light(c), c).size(0.3F, 0).alpha(0.8F, 0).life(10).delay((int) (t * 3)).spawn();
            smoke(v.add(Fx.randomUnit().scale(0.3)).subtract(0, 0.4, 0), dark(c), 0.4F, 0.9F, 16).alpha(0.5F, 0).delay((int) (t * 3)).spawn();
        });
        for (int i = 0; i < 4; i++) {
            Vec3 v = a.add(dir.scale(len * i / 4));
            Fx.p(S.STREAK, v).fixed(Fx.axial(v, dir)).size(1.2F, 0.4F).color(0xFFFFFF, c).alpha(0.7F, 0).life(6).delay(i).spawn();
        }
        flash(a, c, 1.0F, 4);
        flash(b, c, 1.2F, 5);
    }

    /** A column of light: pos, a = radius, b = life (ticks). */
    private static void pillar(P p) {
        int c = p.colorOr(0xFFFFFF);
        Vec3 o = p.pos();
        float r = Math.max(0.6F, p.a());
        int life = Math.max(10, (int) p.b());
        ground(S.CIRCLE_HOLY, o, r, c, life).color(0xFFFFFF, c).alpha(0.8F, 0.3F).spin(0.03F).spawn();
        flash(o.add(0, 1, 0), c, r * 1.5F, 6);
        FxAnims.run(life, age -> {
            for (int i = 0, n = Fx.n((int) (3 + r * 2)); i < n; i++) {
                double a = Fx.r(0, Math.PI * 2), d = Math.sqrt(Fx.RND.nextDouble()) * r;
                Fx.p(S.STREAK, o.add(Math.cos(a) * d, Fx.r(0, 5), Math.sin(a) * d)).upright().vel(0, 0.25, 0)
                        .color(0xFFFFFF, c).size(0.5F, 0.2F).alpha(0.8F, 0).life(8).spawn();
            }
            if (age % 3 == 0) Fx.p(S.GLOW, o.add(0, 2, 0)).color(c).size(r * 2.2F, r * 2).alpha(0.25F, 0).life(4).spawn();
            return false;
        });
    }

    /** A bolt from the sky: pos, a = radius. pts = enemies struck. */
    private static void strike(P p) {
        int c = p.colorOr(0xFFF07A);
        Vec3 at = p.pos();
        Vec3 top = at.add(Fx.r(-1.5, 1.5), 14, Fx.r(-1.5, 1.5));
        drawBolt(SkillFx.bolt(top, at, 1.6, 5), c, 0.2F, 6, 0);
        flash(at.add(0, 0.5, 0), c, 2.5F, 6);
        ground(S.WAVE, at, 0.5F, c, 10).size(0.5F, Math.max(1, p.a()) * 1.3F).easeOut().alpha(1, 0).spawn();
        SkillFx.burst(at.add(0, 0.3, 0), 18, 0.5, S.SPARK, 0xFFFFFF, c, 0.25F, 8, 0.3F);
        for (Vec3 v : p.pts()) drawBolt(SkillFx.bolt(at.add(0, 0.8, 0), v, 0.6, 3), c, 0.1F, 5, 1);
        hits(p.pts(), c, 1);
    }

    /** A wall of light: pos = centre (feet), vec = facing, a = half width, b = life. */
    private static void wall(P p) {
        int c = p.colorOr(0xFFD95A);
        Vec3 o = p.pos();
        Vec3 n = flatDir(p.vec());
        Vec3 side = Fx.right(n);
        float hw = Math.max(1, p.a());
        int life = Math.max(10, (int) p.b());
        Quaternionf q = Fx.basis(side, new Vec3(0, 1, 0));
        for (int i = -(int) hw; i <= (int) hw; i += 1) {
            for (int h = 0; h < 4; h++) {
                Vec3 v = o.add(side.scale(i)).add(0, 0.5 + h, 0);
                Fx.p(S.HEX, v).fixed(q).size(0.55F).color(light(c), c).alpha(0.55F, 0.35F).fadeIn(0.1F).life(life).delay(Math.abs(i) + h).spawn();
            }
        }
        FxAnims.run(life, age -> {
            if (age % 2 != 0) return false;
            Vec3 v = o.add(side.scale(Fx.r(-hw, hw))).add(0, Fx.r(0, 4), 0);
            Fx.p(S.GLOW, v).vel(0, 0.04, 0).color(0xFFFFFF, c).size(0.16F, 0).life(14).spawn();
            return false;
        });
        line(o.add(side.scale(-hw)), o.add(side.scale(hw)), 0.5, (v, t) -> Fx.p(S.GLOW, v.add(0, 0.1, 0)).color(c).size(0.3F, 0).alpha(0.9F, 0.3F).life(life).spawn());
    }

    /** Cards circling an entity: follow e, a = radius, b = life. */
    private static void orbit(P p) {
        Entity e = p.e();
        if (e == null) return;
        float r = Math.max(1, p.a());
        int life = Math.max(10, (int) p.b());
        int n = 8;
        FxParticle[] cards = new FxParticle[n];
        for (int i = 0; i < n; i++) cards[i] = Fx.p(S.CARD, e.position()).size(0.22F).alpha(1, 1).coin().spin(0.5F).life(life + 2).spawn();
        FxAnims.follow(e, life, age -> {
            for (int i = 0; i < n; i++) {
                double a = age * 0.35 + Math.PI * 2 * i / n;
                double rr = r * (0.8 + 0.2 * Math.sin(age * 0.2 + i));
                Vec3 v = e.position().add(Math.cos(a) * rr, 0.6 + 0.5 * Math.sin(age * 0.15 + i * 1.3), Math.sin(a) * rr);
                if (cards[i] != null) cards[i].moveTo(v.x, v.y, v.z);
                if (age % 2 == 0) Fx.p(S.GLOW, v).color(0xFFFFFF, FxExtra.SUIT[i % 4]).size(0.12F, 0).alpha(0.8F, 0).life(6).spawn();
            }
            if (age == life - 1) for (FxParticle f : cards) if (f != null) f.fadeOut(3);
            return false;
        });
    }

    /** A moving whirlwind: pos = start, vec = velocity per tick, a = radius, b = life. */
    private static void tornado(P p) {
        int c = p.colorOr(0xC8F0E0);
        Vec3 start = p.pos();
        Vec3 vel = p.vec();
        float r = Math.max(1, p.a());
        int life = Math.max(10, (int) p.b());
        FxAnims.run(life, age -> {
            Vec3 o = start.add(vel.scale(age));
            for (int i = 0, n = Fx.n(22); i < n; i++) {
                double h = Fx.r(0, 5.5);
                double rr = 0.3 + r * 0.85 * h / 5.5;
                double a = age * 0.7 + Fx.r(0, Math.PI * 2);
                Vec3 v = o.add(Math.cos(a) * rr, h, Math.sin(a) * rr);
                Vec3 t = new Vec3(-Math.sin(a), 0.12, Math.cos(a)).scale(0.35 + rr * 0.08);
                Fx.p(S.STREAK, v).fixed(Fx.axial(v, t)).vel(t).color(0xFFFFFF, c).size(0.9F, 0.4F).alpha(0.85F, 0).life(7).spawn();
                if (i % 3 == 0) smoke(v, 0x7A8A80, 0.6F, 1.4F, 16).vel(t.scale(0.6)).alpha(0.55F, 0).spawn();
                if (i % 4 == 0) Fx.p(S.GLOW, v).vel(t).color(light(c), c).size(0.22F, 0).life(10).spawn();
            }
            if (age % 2 == 0) ground(S.WAVE, o, r * 0.6F, c, 8).size(r * 0.5F, r).color(light(c), c).alpha(0.5F, 0).spin(0.2F).spawn();
            if (age % 4 == 0) debris(o, 3, r * 0.6, 0.6);
            return false;
        });
    }

    /** Stone spikes erupting along a line: pos = start (feet), vec = direction, a = length. */
    private static void spikes(P p) {
        int c = p.colorOr(0xB08850);
        Vec3 o = p.pos();
        Vec3 dir = flatDir(p.vec());
        double len = Math.max(2, p.a());
        for (int i = 1; i <= (int) len; i++) {
            Vec3 at = o.add(dir.scale(i));
            int d = i;
            FxAnims.later(d, () -> {
                debris(at, 10, 0.7, 0.7);
                for (int k = 0; k < 4; k++) {
                    Vec3 v = at.add(Fx.r(-0.7, 0.7), 0, Fx.r(-0.7, 0.7));
                    float s = Fx.rf(0.9F, 1.5F);
                    // a spike shoots up out of the ground, then sinks back
                    Fx.p(S.SHARD, v.add(0, s * 0.4, 0)).upright().vel(0, 0.12, 0).friction(0.7F).color(0xFFFFFF, light(c)).size(s, s * 0.6F).alpha(1, 0).life(18).spawn();
                    Fx.p(S.SHARD, v.add(0, s * 0.4, 0)).upright().vel(0, 0.12, 0).friction(0.7F).color(c, dark(c)).size(s * 1.15F, s * 0.7F).alpha(0.8F, 0).life(18).spawn();
                }
                for (int k = 0; k < 3; k++) smoke(at.add(Fx.r(-0.5, 0.5), 0.2, Fx.r(-0.5, 0.5)), dark(c), 0.5F, 1.2F, 20).vel(0, 0.04, 0).alpha(0.6F, 0).spawn();
                ground(S.WAVE, at, 0.3F, c, 8).size(0.3F, 1.6F).easeOut().alpha(0.8F, 0).spawn();
            });
        }
        FxAnims.shake(o, 0.3F, 8, 12);
    }

    /** Target reticle over an entity: follow e, a = seconds. */
    private static void mark(P p) {
        int c = p.colorOr(0xFF3030);
        Entity e = p.e();
        if (e == null) return;
        int life = Math.max(10, (int) (p.a() * 20));
        FxParticle ring = Fx.p(S.RING, head(e, p.pos()).add(0, 0.3, 0)).size(0.5F).color(light(c), c).alpha(0.9F, 0.9F).spin(0.08F).follow(e).life(life).spawn();
        FxParticle dot = Fx.p(S.GLOW, head(e, p.pos()).add(0, 0.3, 0)).size(0.25F).color(c).alpha(1, 1).follow(e).life(life).spawn();
        Fx.p(S.RING, chest(e, p.pos())).size(2.0F, 0.6F).easeOut().color(0xFFFFFF, c).alpha(1, 0).life(8).spawn();
        if (ring == null && dot == null) return;
    }

    /** Two puffs and a streak between them: pos = A, vec = B. */
    private static void swap(P p) {
        int c = p.colorOr(0xB070FF);
        Vec3 a = p.pos(), b = p.vec();
        for (Vec3 v : new Vec3[] {a, b}) {
            flash(v, c, 1.4F, 6);
            for (int i = 0, n = Fx.n(10); i < n; i++) smoke(v.add(Fx.randomUnit().scale(0.5)), 0x3A2050, 0.5F, 1.3F, 22).vel(Fx.randomUnit().scale(0.04)).alpha(0.7F, 0).spawn();
            SkillFx.burst(v, 10, 0.25, S.CARD, 0xFFFFFF, c, 0.15F, 14, 0.2F);
        }
        line(a, b, 0.4, (v, t) -> Fx.p(S.GLOW, v).color(light(c), c).size(0.15F, 0).alpha(0.8F, 0).life(8).delay((int) (t * 4)).spawn());
    }

    /** A thin glowing line from pos to vec. */
    private static void link(P p) {
        int c = p.colorOr(0xFFFFFF);
        Vec3 a = p.pos(), b = p.vec();
        line(a, b, 0.3, (v, t) -> Fx.p(S.GLOW, v).color(0xFFFFFF, c).size(0.12F, 0).alpha(0.9F, 0).life(10).delay((int) (t * 4)).spawn());
        Fx.p(S.RING, b).size(0.3F, 1.2F).easeOut().color(c).alpha(0.9F, 0).life(8).delay(4).spawn();
    }

    /** A falling star: pos = landing spot, a = radius, b = fall time. */
    private static void star(P p) {
        int c = p.colorOr(0xC8B8FF);
        Vec3 at = p.pos();
        int fall = Math.max(2, (int) p.b());
        Vec3 sky = new Vec3(Fx.r(-3, 3), 10, Fx.r(-3, 3));
        FxAnims.run(fall, age -> {
            for (int s = 0; s < 3; s++) {
                double t = 1 - (age + s / 3.0) / fall;
                Vec3 v = at.add(sky.scale(t));
                Fx.p(S.GLOW, v).color(0xFFFFFF, c).size(0.45F, 0).alpha(1, 0).life(8).spawn();
            }
            Fx.p(S.SPARK, at.add(sky.scale(1 - (double) age / fall))).color(0xFFFFFF, c).size(0.7F).alpha(1, 1).randomAngle().life(1).spawn();
            return false;
        });
        FxAnims.later(fall, () -> {
            flash(at.add(0, 0.3, 0), c, 1.6F, 5);
            ground(S.RING, at, 0.4F, c, 10).size(0.4F, Math.max(1, p.a()) * 2).easeOut().alpha(1, 0).spawn();
            SkillFx.burst(at.add(0, 0.3, 0), 12, 0.35, S.SPARK, 0xFFFFFF, c, 0.2F, 10, 0.2F);
        });
    }

    /** Laser sight from the caster's eyes along their look, for b ticks. */
    private static void aim(P p) {
        int c = p.colorOr(0xFF4040);
        Entity e = p.e();
        if (e == null) return;
        int life = Math.max(2, (int) p.b());
        FxAnims.follow(e, life, age -> {
            Vec3 eye = e.getEyePosition().add(0, -0.15, 0);
            Vec3 look = e.getLookAngle();
            var level = Minecraft.getInstance().level;
            if (level == null) return true;
            BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(look.scale(64)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, e));
            Vec3 end = hit.getType() == HitResult.Type.MISS ? eye.add(look.scale(64)) : hit.getLocation();
            Vec3 from = eye.add(look.scale(0.8));
            double len = from.distanceTo(end);
            for (int i = 0; i < Math.min(40, (int) (len / 1.2)); i++) {
                Vec3 v = from.add(look.scale(i * 1.2 + (age % 2) * 0.6));
                Fx.p(S.GLOW, v).color(c).size(0.05F + 0.004F * age).alpha(0.8F, 0).life(2).spawn();
            }
            Fx.p(S.RING, end).fixed(Fx.across(look)).size(0.6F - 0.03F * age).color(0xFFFFFF, c).alpha(1, 0).life(2).spawn();
            return false;
        });
    }

    /** A flying crescent: pos = start, vec = velocity, a = range, b = width. */
    private static void wave(P p) {
        int c = p.colorOr(0xC8D8FF);
        Vec3 vel = p.vec();
        double speed = Math.max(0.1, vel.length());
        Vec3 dir = vel.normalize();
        float w = Math.max(1, p.b());
        Vec3 side = Fx.right(flatDir(dir));
        Quaternionf q = Fx.basis(side, new Vec3(0, 1, 0));
        final Vec3[] pos = {p.pos()};
        final double[] gone = {0};
        var level = Minecraft.getInstance().level;
        FxAnims.run((int) (p.a() / speed) + 2, age -> {
            if (level == null) return true;
            Vec3 to = pos[0].add(dir.scale(speed));
            BlockHitResult hit = level.clip(new ClipContext(pos[0], to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, Minecraft.getInstance().player));
            boolean wall = hit.getType() != HitResult.Type.MISS;
            pos[0] = wall ? hit.getLocation() : to;
            gone[0] += speed;
            Fx.p(S.SLASH, pos[0]).fixed(Fx.basis(side, dir.scale(-1))).size(w * 0.8F).color(0xFFFFFF, c).alpha(0.9F, 0).angle(0.8F).life(3).spawn();
            Fx.p(S.SLASH, pos[0]).size(w * 0.6F).color(light(c), c).alpha(0.7F, 0).angle(-0.8F).life(3).spawn();
            for (int i = 0, n = Fx.n(4); i < n; i++) {
                Vec3 v = pos[0].add(side.scale(Fx.r(-w * 0.6, w * 0.6)));
                Fx.p(S.GLOW, v).color(0xFFFFFF, c).size(0.14F, 0).life(10).spawn();
            }
            if (q == null) return true;
            return wall || gone[0] >= p.a();
        });
    }

    /** A shadowy copy of the caster standing at pos for b ticks; vec.x = its yaw. */
    private static void decoy(P p) {
        int c = p.colorOr(0xB070FF);
        Vec3 o = p.pos();
        int life = Math.max(10, (int) p.b());
        flash(o.add(0, 1, 0), c, 1.6F, 6);
        FxAnims.run(life, age -> {
            double yaw = Math.toRadians(p.vec().x);
            Vec3 side = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
            for (int i = 0, n = Fx.n(14); i < n; i++) {
                // a rough body: head, torso and legs as stacked glows
                double h = Fx.r(0, 1.9);
                double wdt = h > 1.45 ? 0.25 : h > 0.75 ? 0.4 : 0.22;
                Vec3 v = o.add(side.scale(Fx.r(-wdt, wdt))).add(0, h, 0).add(side.cross(new Vec3(0, 1, 0)).scale(Fx.r(-0.12, 0.12)));
                Fx.p(S.GLOW, v).color(light(c), c).size(0.32F, 0.1F).alpha(0.85F, 0).life(6).spawn();
            }
            if (age % 4 == 0) Fx.p(S.GLOW, o.add(0, 1, 0)).color(c).size(2.0F).alpha(0.25F, 0).life(5).spawn();
            if (age % 3 == 0) smoke(o.add(Fx.r(-0.3, 0.3), Fx.r(0, 1.8), Fx.r(-0.3, 0.3)), 0x2A1840, 0.4F, 0.9F, 14).alpha(0.6F, 0).spawn();
            if (age % 10 == 0) Fx.p(S.SKULL, o.add(0, 2.3, 0)).size(0.25F).alpha(0.9F, 0).life(10).spawn();
            return false;
        });
    }
}
