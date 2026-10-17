package com.jo112ss.rpgmod.client.fx;

import static com.jo112ss.rpgmod.client.fx.SkillFx.*;

import com.jo112ss.rpgmod.client.fx.Fx.S;
import com.jo112ss.rpgmod.client.fx.SkillFx.P;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Effects of the blood moon bosses (King Slime) and of the skill book. */
final class FxBoss {
    private FxBoss() {}

    static final int SLIME = 0x4F8CFF, SLIME_LIGHT = 0xA8D0FF, WARN = 0xFF3A30, GOLD = 0xFFD54A;

    static boolean play(P p) {
        switch (p.fx()) {
            case "king_mark" -> mark(p);
            case "king_land" -> land(p);
            case "king_stun" -> stun(p);
            case "king_split" -> split(p);
            case "king_glob" -> glob(p);
            case "king_spawn" -> spawn(p);
            case "king_leave" -> leave(p);
            case "king_death" -> death(p);
            case "skill_book" -> book(p);
            case "king_dash_line" -> dashLine(p);
            case "king_puddle" -> puddle(p);
            case "king_rain_up" -> rainUp(p);
            case "king_rain_mark" -> rainMark(p);
            case "king_rain_hit" -> rainHit(p);
            case "king_tired" -> tired(p);
            case "king_absorb" -> absorbLine(p);
            case "king_wall" -> wall(p);
            default -> {
                return false;
            }
        }
        return true;
    }

    /**
     * Where a slam will land: a red ring that stays put, and a disc that fills in from the centre,
     * reaching the ring the moment the king comes down.
     */
    private static void mark(P p) {
        Vec3 o = p.pos();
        float r = Math.max(1.5F, p.a());
        int life = Math.max(6, (int) p.b());
        ground(S.RING, o, r * 2.2F, WARN, life).alpha(0.9F, 1F).spawn();
        ground(S.RING, o, r * 2.3F, 0xFFFFFF, life).alpha(0.35F, 0.6F).spawn();
        ground(S.GLOW, o, r * 0.2F, WARN, life).size(r * 0.2F, r * 2.0F).alpha(0.25F, 0.65F).spawn();
        FxAnims.run(life, age -> {
            if (age % 4 == 0) {
                circle(o.add(0, 0.15, 0), r, Fx.n(14), age * 0.15, v -> Fx.p(S.GLOW, v).vel(0, 0.03, 0).color(WARN).size(0.25F, 0).life(8).spawn());
            }
            return false;
        });
    }

    /** Landing: a splash of slime, debris, and the shockwave ring rolling out at the server's speed. */
    private static void land(P p) {
        Vec3 o = p.pos();
        float half = Math.max(1, p.a()), reach = Math.max(2, p.b());
        int ticks = (int) (reach / 0.55F) + 1;
        ground(S.WAVE, o, half * 2, SLIME, ticks).size(half * 2.1F, (half + reach) * 2.15F).color(SLIME_LIGHT, SLIME).alpha(1F, 0.2F).spawn();
        ground(S.RING, o, half * 2, 0xFFFFFF, ticks).size(half * 2.0F, (half + reach) * 2.1F).alpha(0.8F, 0).spawn();
        circle(o.add(0, 0.3, 0), half, Fx.n(28), 0, v -> {
            Vec3 out = v.subtract(o).multiply(1, 0, 1).normalize();
            Fx.p(S.GLOW, v).vel(out.scale(0.35).add(0, 0.25 + Fx.r(0, 0.2), 0)).gravity(1.2F).friction(0.95F)
                    .color(SLIME_LIGHT, SLIME).size(0.5F, 0.15F).life(20).spawn();
        });
        debris(o, 40, half + 1, 0.6);
        flash(o.add(0, 0.8, 0), SLIME, half * 1.4F, 6);
    }

    /** Stunned: little stars circling above the flattened king. */
    private static void stun(P p) {
        Entity e = p.e();
        float w = Math.max(1, p.a());
        int life = Math.max(10, (int) p.b());
        FxAnims.follow(e, life, age -> {
            Vec3 top = e.position().add(0, e.getBbHeight() * 0.7 + 0.4, 0);
            circle(top, w * 0.35, 5, age * 0.18, v -> Fx.p(S.SPARK, v).color(0xFFFFFF, GOLD).size(0.45F, 0.2F).randomAngle().life(2).spawn());
            if (age % 3 == 0) {
                Fx.p(S.GLOW, e.position().add(Fx.r(-w / 2, w / 2), 0.2, Fx.r(-w / 2, w / 2))).vel(0, 0.06, 0).color(SLIME_LIGHT).size(0.3F, 0).life(12).spawn();
            }
            return false;
        });
    }

    /** Shrinking: blobs bursting out and a ring. */
    private static void split(P p) {
        Entity e = p.e();
        Vec3 o = chest(e, p.pos());
        float w = Math.max(1, p.a());
        burst(o, 40, w * 0.25, S.GLOW, SLIME_LIGHT, SLIME, 0.6F, 18, 0.8F);
        ground(S.RING, feet(e, p.pos()), w, SLIME, 14).size(w, w * 3.2F).easeOut().alpha(1, 0).spawn();
        flash(o, SLIME_LIGHT, w, 8);
    }

    private static void glob(P p) {
        Vec3 o = p.pos();
        burst(o, 10, 0.25, S.GLOW, 0xB8FF9A, 0x58D858, 0.3F, 10, 0.6F);
        ground(S.RING, o, 0.6F, 0x78E878, 8).size(0.4F, 1.6F).alpha(0.8F, 0).spawn();
    }

    /** Arrival: a pillar of blue light and a ring. */
    private static void spawn(P p) {
        Vec3 o = p.pos();
        float w = Math.max(2, p.a());
        for (int i = 0; i < 24; i++) {
            Fx.p(S.GLOW, o.add(Fx.r(-w / 2, w / 2), Fx.r(0, 1), Fx.r(-w / 2, w / 2))).vel(0, Fx.r(0.2, 0.5), 0).friction(0.97F)
                    .color(SLIME_LIGHT, SLIME).size(0.6F, 0).life(40).spawn();
        }
        ground(S.RING, o, w, SLIME, 30).size(w, w * 4).easeOut().alpha(1, 0).spawn();
        flash(o.add(0, w * 0.5, 0), SLIME_LIGHT, w * 1.5F, 10);
    }

    /** Dawn: it sinks into the ground, bubbling. */
    private static void leave(P p) {
        Entity e = p.e();
        float w = Math.max(1, p.a());
        Vec3 base = p.pos();
        FxAnims.run(Math.max(10, (int) p.b()), age -> {
            Vec3 o = e != null ? new Vec3(e.getX(), base.y, e.getZ()) : base;
            for (int i = 0; i < 3; i++) {
                Fx.p(S.GLOW, o.add(Fx.r(-w / 2, w / 2), 0.1, Fx.r(-w / 2, w / 2))).vel(0, Fx.r(0.05, 0.15), 0).color(SLIME_LIGHT, SLIME).size(0.4F, 0).life(14).spawn();
            }
            if (age % 10 == 0) ground(S.RING, o, w, SLIME, 12).size(w * 0.8F, w * 1.6F).alpha(0.7F, 0).spawn();
            return false;
        });
    }

    private static void death(P p) {
        Vec3 o = p.pos().add(0, 1, 0);
        float w = Math.max(2, p.a());
        burst(o, 70, w * 0.3, S.GLOW, SLIME_LIGHT, SLIME, 0.8F, 26, 0.7F);
        burst(o.add(0, w * 0.6, 0), 30, 0.5, S.SPARK, 0xFFFFFF, GOLD, 0.7F, 20, 0.3F);
        ground(S.WAVE, p.pos(), w, SLIME, 20).size(w, w * 5).easeOut().alpha(1, 0).spawn();
        flash(o, GOLD, w * 2, 12);
    }

    /** Reading a skill book: gold glyph sparks spiralling up around the player. */
    private static void book(P p) {
        Entity e = p.e();
        Vec3 base = feet(e, p.pos());
        FxAnims.run(24, age -> {
            Vec3 o = e != null ? e.position() : base;
            double a = age * 0.5;
            for (int k = 0; k < 2; k++) {
                double ang = a + k * Math.PI;
                Fx.p(S.SPARK, o.add(Math.cos(ang) * 0.8, age * 0.08, Math.sin(ang) * 0.8)).color(0xFFFFFF, GOLD).size(0.3F, 0).randomAngle().life(10).spawn();
            }
            return false;
        });
        ground(S.CIRCLE_ARCANE, base, 2.2F, GOLD, 24).alpha(0.9F, 0).spin(0.08F).spawn();
    }

    /** The dash path: a red lane on the ground from the king along its slide, filling in. */
    private static void dashLine(P p) {
        Vec3 o = p.pos(), d = p.vec();
        float w = Math.max(1, p.a());
        int life = Math.max(6, (int) p.b());
        Vec3 dir = flatDir(d);
        Vec3 side = Fx.right(dir);
        double len = d.length();
        FxAnims.run(life, age -> {
            if (age % 3 != 0) return false;
            double fill = Math.min(1, (age + 3) / (double) life);
            line(o.add(0, 0.1, 0), o.add(d.scale(fill)).add(0, 0.1, 0), 0.9, (v, t) -> {
                Fx.p(S.GLOW, v.add(side.scale(w / 2))).color(WARN).size(0.3F, 0.1F).alpha(0.9F, 0).life(5).spawn();
                Fx.p(S.GLOW, v.add(side.scale(-w / 2))).color(WARN).size(0.3F, 0.1F).alpha(0.9F, 0).life(5).spawn();
            });
            Vec3 tip = o.add(d.scale(fill));
            Fx.p(S.STREAK, tip.add(0, 0.4, 0)).fixed(Fx.facing(dir)).color(0xFFFFFF, WARN).size(w * 0.6F).alpha(0.9F, 0).life(5).spawn();
            return false;
        });
        ground(S.GLOW, o.add(d.scale(0.5)), (float) len, WARN, life).alpha(0.15F, 0.3F).spawn();
    }

    /** A sticky blue puddle that fades out over its life. */
    private static void puddle(P p) {
        Vec3 o = p.pos();
        float r = Math.max(0.8F, p.a());
        int life = Math.max(20, (int) p.b());
        ground(S.GLOW, o, r * 2.2F, SLIME, life).alpha(0.55F, 0).color(SLIME, 0x2A4AA8).spawn();
        ground(S.RING, o, r * 2.0F, SLIME_LIGHT, 16).size(r * 1.2F, r * 2.1F).alpha(0.8F, 0).spawn();
        for (int i = 0; i < 3; i++) Fx.p(S.GLOW, o.add(Fx.r(-r, r), 0.1, Fx.r(-r, r))).vel(0, 0.06, 0).color(SLIME_LIGHT).size(0.25F, 0).life(10).spawn();
    }

    /** Globs fired into the sky. */
    private static void rainUp(P p) {
        Vec3 o = p.pos();
        float w = Math.max(1, p.a());
        for (int i = 0, n = Fx.n(16); i < n; i++) {
            Fx.p(S.GLOW, o.add(Fx.r(-w / 3, w / 3), 0, Fx.r(-w / 3, w / 3))).vel(Fx.r(-0.15, 0.15), Fx.r(0.9, 1.4), Fx.r(-0.15, 0.15))
                    .color(SLIME_LIGHT, SLIME).size(0.6F, 0.3F).life(18).spawn();
        }
        flash(o, SLIME_LIGHT, w * 0.8F, 6);
    }

    /** Where a glob of rain will land: a blue circle closing in, then the glob falls in the last ticks. */
    private static void rainMark(P p) {
        Vec3 o = p.pos();
        float r = Math.max(1, p.a());
        int life = Math.max(6, (int) p.b());
        ground(S.RING, o, r * 2.2F, 0x6CB0FF, life).size(r * 3.2F, r * 2.1F).alpha(0.5F, 1F).spawn();
        ground(S.GLOW, o, r * 0.2F, 0x6CB0FF, life).size(r * 0.3F, r * 2.0F).alpha(0.2F, 0.6F).spawn();
        int fall = Math.min(10, life);
        FxAnims.run(life, age -> {
            int k = age - (life - fall);
            if (k < 0) return false;
            double h = 14 * (1 - (k + 1) / (double) fall);
            Vec3 at = o.add(0, h + 0.4, 0);
            Fx.p(S.GLOW, at).color(SLIME_LIGHT, SLIME).size(0.9F, 0.6F).life(2).spawn();
            Fx.p(S.GLOW, at.add(0, 0.6, 0)).color(SLIME).size(0.5F, 0).alpha(0.6F, 0).life(5).spawn();
            return false;
        });
    }

    private static void rainHit(P p) {
        Vec3 o = p.pos();
        burst(o.add(0, 0.3, 0), 16, 0.3, S.GLOW, SLIME_LIGHT, SLIME, 0.45F, 12, 0.9F);
        ground(S.WAVE, o, 1, SLIME, 10).size(1F, 4F).easeOut().alpha(0.9F, 0).spawn();
    }

    /** Exhausted: sweat drops flying off and a gold "open" ring at its feet for the whole window. */
    private static void tired(P p) {
        Entity e = p.e();
        float w = Math.max(1, p.a());
        int life = Math.max(10, (int) p.b());
        if (e == null) return;
        ground(S.RING, e.position(), w * 1.3F, GOLD, life).alpha(0.7F, 0.3F).follow(e).spawn();
        FxAnims.follow(e, life, age -> {
            if (age % 4 == 0) {
                Vec3 top = e.position().add(Fx.r(-w / 3, w / 3), e.getBbHeight() * 0.7, Fx.r(-w / 3, w / 3));
                Fx.p(S.GLOW, top).vel(Fx.r(-0.15, 0.15), 0.25, Fx.r(-0.15, 0.15)).gravity(1.0F).color(0xD8F0FF).size(0.35F, 0.2F).life(16).spawn();
            }
            if (age % 10 == 0) {
                circle(e.position().add(0, e.getBbHeight() * 0.75 + 0.3, 0), w * 0.3, 4, age * 0.2,
                        v -> Fx.p(S.SPARK, v).color(0xFFFFFF, GOLD).size(0.4F, 0).randomAngle().life(8).spawn());
            }
            return false;
        });
    }

    /** A thread of slime pulling a small slime into the king. */
    private static void absorbLine(P p) {
        Vec3 a = p.pos(), b = p.vec();
        line(a, b, 0.5, (v, t) -> Fx.p(S.GLOW, v).vel(b.subtract(v).normalize().scale(0.15)).color(SLIME_LIGHT, SLIME).size(0.25F, 0).alpha(0.8F, 0).life(5).spawn());
    }

    /** Hitting a wall: dust and block bits, a flash. */
    private static void wall(P p) {
        Vec3 o = p.pos();
        float w = Math.max(1, p.a());
        debris(o, 40, w / 2, 0.6);
        burst(o, 20, 0.35, S.SPARK, 0xFFFFFF, GOLD, 0.6F, 12, 0.5F);
        flash(o, 0xFFFFFF, w, 6);
    }
}
