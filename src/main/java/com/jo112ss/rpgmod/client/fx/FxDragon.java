package com.jo112ss.rpgmod.client.fx;

import static com.jo112ss.rpgmod.client.fx.SkillFx.*;

import com.jo112ss.rpgmod.client.fx.Fx.S;
import com.jo112ss.rpgmod.client.fx.SkillFx.P;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Ender Dragon fight effects. Every attack is telegraphed: a sigil burns into the ground where a
 * fireball will land, a ring shows the reach of the wing gust, a red line shows where the dragon dives.
 */
final class FxDragon {
    private FxDragon() {}

    static boolean play(P p) {
        switch (p.fx()) {
            case "dragon_mark" -> mark(p);
            case "dragon_gust" -> gust(p);
            case "dragon_stage" -> stage(p);
            case "dragon_charge" -> charge(p);
            case "dragon_enrage" -> enrage(p);
            case "dragon_summon" -> summon(p);
            default -> {
                return false;
            }
        }
        return true;
    }

    /** A sigil that tightens onto the spot, then flares when the hit is due. */
    private static void mark(P p) {
        Vec3 at = p.pos();
        float r = Math.max(1, p.a());
        int life = Math.max(10, (int) p.b());
        int color = p.colorOr(0xB040FF);
        ground(S.CIRCLE_ARCANE, at, r, color, life).alpha(0.9F, 0.4F).fadeIn(0.1F).spin(0.05F).spawn();
        ground(S.RING, at, r * 2.2F, color, life).size(r * 2.4F, r * 0.9F).alpha(0.3F, 0.9F).spawn();
        ground(S.GLOW, at, r * 1.6F, color, life).alpha(0.15F, 0.45F).spawn();
        FxAnims.run(life, age -> {
            if (age % 4 == 0) {
                double a = Fx.r(0, Math.PI * 2), d = Fx.r(0, r);
                Fx.p(S.STREAK, at.add(Math.cos(a) * d, 0.2, Math.sin(a) * d)).upright().vel(0, 0.08, 0).color(0xFFFFFF, color).size(0.5F, 0.2F).alpha(0.8F, 0).life(10).spawn();
            }
            if (age == life - 6) {
                Fx.p(S.BEAM, at.add(0, 4, 0)).upright().color(0xFFFFFF, color).size(8F, 2F).alpha(0.7F, 0).life(8).spawn();
            }
            return false;
        });
    }

    /** The reach of the gust as a pulsing ring, then a wall of wind rolling outwards. */
    private static void gust(P p) {
        Vec3 at = p.pos();
        float r = Math.max(3, p.a());
        int warn = Math.max(5, (int) p.b());
        int color = p.colorOr(0xB040FF);
        ground(S.RING, at, r * 2, color, warn).alpha(0.4F, 1F).spawn();
        FxAnims.run(warn + 1, age -> {
            if (age % 6 == 0) ground(S.RING, at, r * 2, color, 8).size(r * 0.4F, r * 2).easeOut().alpha(0.6F, 0).spawn();
            if (age == warn) {
                ground(S.WAVE, at, 1, 0xFFFFFF, 14).size(1F, r * 2.2F).easeOut().color(0xFFFFFF, color).alpha(0.9F, 0).spawn();
                circle(at.add(0, 0.3, 0), r * 0.5, 24, 0, v -> {
                    Vec3 out = v.subtract(at).multiply(1, 0, 1).normalize();
                    Fx.p(S.STREAK, v).vel(out.scale(0.9).add(0, 0.1, 0)).friction(0.9F).color(0xFFFFFF, color).size(0.9F, 0.3F).life(12).spawn();
                    smoke(v, 0x2A1E36, 0.6F, 1.6F, 20).vel(out.scale(0.5)).alpha(0.6F, 0).spawn();
                });
                FxAnims.shake(at, 0.6F, 8, r + 6);
            }
            return false;
        });
    }

    /** Stage change: a shock ring, a flare on the dragon and a short rumble. */
    private static void stage(P p) {
        Entity e = p.e();
        Vec3 c = e != null ? e.position().add(0, 2, 0) : p.pos();
        int color = p.colorOr(0xB040FF);
        flash(c, color, 10F, 14);
        for (int k = 0; k < 3; k++) {
            Fx.p(S.RING, c).size(2F, 30F + k * 8).easeOut().color(0xFFFFFF, color).alpha(0.9F, 0).life(18).delay(k * 4).spawn();
        }
        burst(c, 40, 1.4, S.STREAK, 0xFFFFFF, color, 1.2F, 20, 0F);
        FxAnims.shake(c, 1.0F, 20, 80);
        if (p.b() >= 3) enrageBurst(e, c, 120);
    }

    /** A red line from the dragon's head to the target, held until the dive. */
    private static void charge(P p) {
        Entity dragon = p.e();
        Entity target = Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.getEntity((int) p.b());
        int life = Math.max(5, (int) p.a());
        FxAnims.run(life, age -> {
            Vec3 from = dragon != null ? dragon.position().add(0, 2, 0) : p.pos();
            Vec3 to = target != null ? target.position().add(0, 1, 0) : p.vec();
            double len = from.distanceTo(to);
            int n = (int) Math.min(60, len / 1.5);
            for (int i = 0; i <= n; i++) {
                double t = i / (double) n;
                if ((i + age) % 3 != 0) continue;
                Fx.p(S.GLOW, from.add(to.subtract(from).scale(t))).color(0xFF3050).size(0.35F, 0.1F).alpha(0.9F, 0).life(3).spawn();
            }
            if (target != null && age % 5 == 0) ground(S.RING, target.position(), 2.5F, 0xFF3050, 8).size(3F, 1F).alpha(0.9F, 0).spawn();
            return false;
        });
    }

    /** Enraged: embers and dark flames pouring off the dragon. */
    private static void enrage(P p) {
        enrageBurst(p.e(), p.pos(), Math.max(20, (int) p.a()));
    }

    private static void enrageBurst(Entity e, Vec3 fallback, int ticks) {
        if (e == null) return;
        FxAnims.follow(e, ticks, age -> {
            if (age % 2 != 0) return false;
            Vec3 c = e.position();
            for (int i = 0; i < 2; i++) {
                Vec3 at = c.add(Fx.r(-4, 4), Fx.r(0, 4), Fx.r(-4, 4));
                Fx.p(S.GLOW, at).vel(0, 0.06, 0).color(0xFF6070, 0x500818).size(0.5F, 0.1F).alpha(0.8F, 0).life(16).spawn();
                smoke(at, 0x1A0610, 0.8F, 2.0F, 24).vel(0, 0.04, 0).alpha(0.5F, 0).spawn();
            }
            return false;
        });
    }

    /** A rift opening where something is called out of the void. */
    private static void summon(P p) {
        Vec3 at = p.pos();
        float r = Math.max(0.5F, p.a());
        int color = p.colorOr(0xB040FF);
        Fx.p(S.RING, at).size(0.2F, r * 2).easeOut().color(0xFFFFFF, color).alpha(1, 0).life(10).spawn();
        flash(at, color, r * 1.5F, 6);
        burst(at, 14, 0.4, S.GLOW, 0xFFFFFF, color, 0.15F, 14, 0F);
    }
}
