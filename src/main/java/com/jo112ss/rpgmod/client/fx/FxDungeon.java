package com.jo112ss.rpgmod.client.fx;

import static com.jo112ss.rpgmod.client.fx.SkillFx.*;

import com.jo112ss.rpgmod.client.fx.Fx.S;
import com.jo112ss.rpgmod.client.fx.SkillFx.P;
import net.minecraft.world.phys.Vec3;

/** Dungeons: the open altar's swirl, the offering, warping in and out, monsters summoned, a room won. */
final class FxDungeon {
    private FxDungeon() {}

    static boolean play(P p) {
        switch (p.fx()) {
            case "dg_altar" -> altar(p);
            case "dg_open" -> open(p);
            case "dg_warp" -> warp(p);
            case "dg_pad" -> pad(p);
            case "dg_summon" -> summon(p);
            case "dg_clear" -> clear(p);
            default -> {
                return false;
            }
        }
        return true;
    }

    /** An open altar: a turning circle above it and motes drawn up into a small rift. */
    private static void altar(P p) {
        int c = p.colorOr(0xB070FF);
        Vec3 o = p.pos();
        Fx.p(S.CIRCLE_ARCANE, o.add(0, 0.05, 0)).flat().color(FxUlt.light(c), c).size(1.4F).alpha(0.7F, 0.7F).spin(0.05F).life(20).spawn();
        Vec3 rift = o.add(0, 1.4, 0);
        Fx.p(S.ORB, rift).color(0xFFFFFF, c).size(0.35F, 0.45F).alpha(0.8F, 0.6F).life(20).spawn();
        Fx.p(S.GLOW, rift).color(c).size(1.0F, 1.2F).alpha(0.35F, 0.2F).life(20).spawn();
        for (int i = 0; i < 6; i++) {
            double a = Fx.r(0, Math.PI * 2);
            Vec3 v = o.add(Math.cos(a) * 0.8, Fx.r(0, 0.4), Math.sin(a) * 0.8);
            Fx.p(S.GLOW, v).vel(rift.subtract(v).scale(0.05)).color(0xFFFFFF, c).size(0.12F, 0.04F).life(20).delay(Fx.RND.nextInt(20)).spawn();
        }
    }

    /** The offering is taken: a burst, a column and the circle flaring up. */
    private static void open(P p) {
        int c = p.colorOr(0xB070FF);
        Vec3 o = p.pos();
        flash(o.add(0, 1, 0), c, 2.5F, 8);
        ground(S.WAVE, o, 0.5F, c, 16).size(0.5F, 6).easeOut().color(FxUlt.light(c), c).alpha(1, 0).spawn();
        Fx.p(S.BEAM, o.add(0, 2, 0)).upright().color(0xFFFFFF, c).size(0.8F, 0.2F).alpha(0.9F, 0).life(20).spawn();
        burst(o.add(0, 1, 0), 40, 0.45, S.TWINKLE, 0xFFFFFF, c, 0.3F, 18, -0.02F);
        for (int i = 0; i < 4; i++) Fx.p(Fx.rune(i), o.add(Math.cos(i * 1.57) * 1.2, 1.2, Math.sin(i * 1.57) * 1.2)).color(0xFFFFFF, c).size(0.5F, 0).life(24).spawn();
    }

    /** Someone steps through. */
    private static void warp(P p) {
        int c = p.colorOr(0xB070FF);
        Vec3 o = p.pos();
        Fx.p(S.RING, o.add(0, 1, 0)).upright().color(0xFFFFFF, c).size(0.3F, 1.8F).easeOut().alpha(1, 0).life(10).spawn();
        for (int i = 0; i < 20; i++) {
            Vec3 v = o.add(Fx.r(-0.4, 0.4), Fx.r(0, 2), Fx.r(-0.4, 0.4));
            Fx.p(S.GLOW, v).vel(0, Fx.r(0.05, 0.15), 0).color(0xFFFFFF, c).size(0.15F, 0).life(14).spawn();
        }
    }

    /** The exit circle in the entrance hall. */
    private static void pad(P p) {
        Vec3 o = p.pos();
        int c = 0x8AE0FF;
        Fx.p(S.CIRCLE_HOLY, o.add(0, 0.05, 0)).flat().color(0xFFFFFF, c).size(1.6F).alpha(0.6F, 0.6F).spin(-0.04F).life(20).spawn();
        for (int i = 0; i < 4; i++) {
            Vec3 v = o.add(Fx.r(-1.2, 1.2), 0.1, Fx.r(-1.2, 1.2));
            Fx.p(S.GLOW, v).vel(0, 0.04, 0).color(0xFFFFFF, c).size(0.14F, 0).life(24).delay(Fx.RND.nextInt(20)).spawn();
        }
    }

    /** A monster summoned into a room: a dark circle, a flash, smoke. */
    private static void summon(P p) {
        int c = p.colorOr(0xB070FF);
        Vec3 o = p.pos();
        float w = Math.max(1, p.a() * 1.6F);
        ground(S.CIRCLE_HEX, o, w, c, 24).color(FxUlt.light(c), c).alpha(0.9F, 0).spin(0.1F).spawn();
        flash(o.add(0, 1, 0), c, 1.4F, 6);
        for (int i = 0; i < 8; i++) smoke(o.add(Fx.r(-0.5, 0.5), Fx.r(0, 1.5), Fx.r(-0.5, 0.5)), 0x201828, 0.6F, 1.2F, 20).spawn();
        burst(o.add(0, 0.8, 0), 14, 0.3, S.SPARK, 0xFFFFFF, c, 0.2F, 10, 0.05F);
    }

    /** A room won: the chest arrives in a shower of light. */
    private static void clear(P p) {
        int c = p.colorOr(0xB070FF);
        Vec3 o = p.pos();
        flash(o.add(0, 0.6, 0), 0xFFE070, 2.2F, 8);
        ground(S.WAVE, o, 0.5F, 0xFFE070, 18).size(0.5F, 8).easeOut().color(0xFFFFFF, 0xFFE070).alpha(1, 0).spawn();
        burst(o.add(0, 1, 0), 36, 0.5, S.TWINKLE, 0xFFFFFF, 0xFFE070, 0.3F, 20, 0.02F);
        Fx.p(S.BEAM, o.add(0, 2.5, 0)).upright().color(0xFFFFFF, c).size(0.6F, 0.1F).alpha(0.8F, 0).life(24).spawn();
    }
}
