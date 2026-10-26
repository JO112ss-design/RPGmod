package com.jo112ss.rpgmod.client.fx;

import static com.jo112ss.rpgmod.client.fx.SkillFx.*;

import com.jo112ss.rpgmod.client.fx.Fx.S;
import com.jo112ss.rpgmod.client.fx.SkillFx.P;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * The fallen adventurers' warnings: every special move is drawn on the ground before it comes (a
 * line for a charge or dash, a cone for a cleave, a ring where a fireball will land, a casting
 * circle), filling in over exactly the time there is to step aside.
 */
final class FxRevenant {
    private FxRevenant() {}

    static boolean play(P p) {
        switch (p.fx()) {
            case "rv_line" -> line(p);
            case "rv_cone" -> cone(p);
            case "rv_circle" -> circle(p);
            case "rv_cast" -> cast(p);
            case "rv_heal" -> heal(p);
            default -> {
                return false;
            }
        }
        return true;
    }

    private static Vec3 flat(Vec3 v) {
        Vec3 f = new Vec3(v.x, 0, v.z);
        return f.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : f.normalize();
    }

    /** A charge or dash lane: two red edges, a fill running from the monster to the end, an arrowhead. */
    private static void line(P p) {
        int c = p.colorOr(0xFF3030);
        Vec3 o = p.pos();
        Vec3 dir = flat(p.vec());
        Vec3 side = new Vec3(-dir.z, 0, dir.x).scale(0.55);
        double len = Math.max(2, p.a());
        int life = Math.max(4, (int) p.b());
        Vec3 end = o.add(dir.scale(len));
        for (int s = -1; s <= 1; s += 2) {
            Vec3 off = side.scale(s);
            SkillFx.line(o.add(off), end.add(off), 0.5, (v, t) -> ground(S.GLOW, v, 0.35F, c, life).color(0xFF9070, c).alpha(0.9F, 0.9F).spawn());
        }
        for (int s = -1; s <= 1; s += 2) {
            Vec3 tip = end.add(dir.scale(0.6));
            Vec3 back = end.subtract(dir.scale(0.4)).add(side.scale(s * 1.4));
            SkillFx.line(back, tip, 0.3, (v, t) -> ground(S.GLOW, v, 0.4F, c, life).color(0xFFB090, c).alpha(1, 1).spawn());
        }
        FxAnims.run(life, age -> {
            double f = (age + 1) / (double) life;
            Vec3 v = o.add(dir.scale(len * f));
            ground(S.GLOW, v, 1.1F, c, 6).color(0xFF6040, c).alpha(0.45F, 0).spawn();
            return false;
        });
    }

    /** A cleave: the wedge outlined in red, filling from the monster outwards. */
    private static void cone(P p) {
        int c = p.colorOr(0xFF3030);
        Vec3 o = p.pos();
        Vec3 dir = flat(p.vec());
        double r = Math.max(2, p.a());
        int life = Math.max(4, (int) p.b());
        double half = Math.toRadians(50), base = Math.atan2(dir.z, dir.x);
        for (int s = -1; s <= 1; s += 2) {
            double a = base + half * s;
            Vec3 e = o.add(Math.cos(a) * r, 0, Math.sin(a) * r);
            SkillFx.line(o, e, 0.45, (v, t) -> ground(S.GLOW, v, 0.35F, c, life).color(0xFF9070, c).alpha(0.9F, 0.9F).spawn());
        }
        for (int i = 0; i <= 12; i++) {
            double a = base - half + 2 * half * i / 12;
            ground(S.GLOW, o.add(Math.cos(a) * r, 0, Math.sin(a) * r), 0.4F, c, life).color(0xFF9070, c).alpha(0.9F, 0.9F).spawn();
        }
        FxAnims.run(life, age -> {
            double rr = r * (age + 1) / life;
            for (int i = 0; i <= 8; i++) {
                double a = base - half + 2 * half * i / 8;
                ground(S.GLOW, o.add(Math.cos(a) * rr, 0, Math.sin(a) * rr), 0.7F, c, 5).color(0xFF6040, c).alpha(0.4F, 0).spawn();
            }
            return false;
        });
    }

    /** Where a fireball will land: a ring and a disc that grows to fill it just as it arrives. */
    private static void circle(P p) {
        int c = p.colorOr(0xFF5030);
        Vec3 o = p.pos();
        float r = Math.max(1, p.a());
        int life = Math.max(6, (int) p.b());
        ground(S.RING, o, r * 1.05F, c, life).color(0xFFA070, c).alpha(0.95F, 0.95F).spawn();
        ground(S.CIRCLE_HEX, o, 0.3F, c, life).color(0xFF8050, c).size(0.2F, r).alpha(0.3F, 0.7F).spin(0.06F).spawn();
        Fx.p(S.CROSS, o.add(0, 0.08, 0)).flat().color(0xFFFFFF, c).size(0.7F).alpha(0.9F, 0.9F).angle(0.785F).life(life).spawn();
    }

    /** Casting: a turning circle under the feet in the job's colour and motes rising into the hands. */
    private static void cast(P p) {
        Entity e = p.e();
        int c = p.colorOr(0x9A6AFF);
        int life = Math.max(4, (int) p.b());
        float size = Math.max(1, p.a());
        Vec3 f = feet(e, p.pos());
        ground(S.CIRCLE_ARCANE, f, size * 2.2F, c, life).color(FxUlt.light(c), c).alpha(0.9F, 0.6F).spin(0.08F).follow(e).spawn();
        FxAnims.follow(e, life, age -> {
            if (age % 2 != 0) return false;
            Vec3 b = e.position();
            double a = Fx.r(0, Math.PI * 2);
            Vec3 v = b.add(Math.cos(a) * size, 0.1, Math.sin(a) * size);
            Vec3 hand = e.position().add(0, e.getBbHeight() * 0.7, 0).add(e.getLookAngle().multiply(1, 0, 1).scale(0.5));
            Fx.p(S.GLOW, v).vel(hand.subtract(v).scale(0.12)).color(0xFFFFFF, c).size(0.18F, 0.05F).life(8).spawn();
            if (age % 6 == 0) Fx.p(Fx.rune(age / 6), hand).color(0xFFFFFF, c).size(0.3F, 0).alpha(0.9F, 0).life(8).spawn();
            return false;
        });
    }

    /** The priest's heal: a gold flash and crosses rising on everyone it reached. */
    private static void heal(P p) {
        int c = p.colorOr(0xFFE6A0);
        Entity e = p.e();
        Vec3 o = feet(e, p.pos());
        flash(o.add(0, 1.2, 0), c, 1.8F, 6);
        ground(S.CIRCLE_HOLY, o, 1, c, 14).size(1, 6).easeOut().color(0xFFFFFF, c).alpha(0.8F, 0).spawn();
        for (Vec3 v : p.pts()) {
            for (int i = 0; i < 5; i++) {
                Fx.p(S.CROSS, v.add(Fx.r(-0.4, 0.4), Fx.r(-0.6, 0.6), Fx.r(-0.4, 0.4))).vel(0, 0.05, 0).color(0xFFFFFF, c).size(0.28F, 0).life(16).delay(i * 2).spawn();
            }
            SkillFx.line(o.add(0, 1.2, 0), v, 0.5, (a, t) -> Fx.p(S.GLOW, a).color(0xFFFFFF, c).size(0.14F, 0).life(8).delay((int) (t * 5)).spawn());
        }
    }
}
