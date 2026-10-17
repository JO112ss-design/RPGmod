package com.jo112ss.rpgmod.client.fx;

import static com.jo112ss.rpgmod.client.fx.SkillFx.*;

import com.jo112ss.rpgmod.client.fx.Fx.S;
import com.jo112ss.rpgmod.client.fx.SkillFx.P;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Effects of monster abilities (smarter zombies, spiders...) and of the Ender Dragon fight. */
final class FxMobs {
    private FxMobs() {}

    static boolean play(P p) {
        switch (p.fx()) {
            case "rod_line" -> rodLine(p);
            case "rod_pull" -> rodPull(p);
            case "web_shot" -> webShot(p);
            case "mob_warn" -> mobWarn(p);
            case "fire_ring" -> fireRing(p);
            case "charge_dust" -> chargeDust(p);
            default -> {
                return FxDragon.play(p);
            }
        }
        return true;
    }

    /** A fishing line flying from the zombie's hand to its target, with the bobber at the tip. */
    private static void rodLine(P p) {
        Entity zombie = p.e();
        Entity target = Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.getEntity((int) p.b());
        int flight = Math.max(2, (int) p.a());
        Vec3 fallbackTo = p.vec();
        FxAnims.run(flight + 6, age -> {
            Vec3 from = zombie != null ? hand(zombie, p.pos()) : p.pos();
            Vec3 to = target != null ? target.position().add(0, target.getBbHeight() * 0.6, 0) : fallbackTo;
            double k = Math.min(1, (age + 1) / (double) flight);
            Vec3 tip = from.add(to.subtract(from).scale(k)).add(0, Math.sin(k * Math.PI) * 0.8, 0);
            // the line sags a little between hand and bobber
            line(from, tip, 0.35, (v, t) -> Fx.p(S.GLOW, v.add(0, -Math.sin(t * Math.PI) * 0.15, 0)).color(0xE8E8E8).size(0.035F).alpha(0.9F, 0.5F).life(2).spawn());
            Fx.p(S.GLOW, tip).color(0xFF4040).size(0.12F).alpha(1, 1).life(2).spawn();
            Fx.p(S.GLOW, tip.add(0, 0.08, 0)).color(0xFFFFFF).size(0.08F).alpha(1, 1).life(2).spawn();
            return false;
        });
    }

    private static void rodPull(P p) {
        Vec3 at = p.pos();
        Vec3 dir = p.vec().subtract(at);
        spray(at, dir.lengthSqr() < 1e-4 ? new Vec3(0, 1, 0) : dir, 0.5, 10, 0.3, S.GLOW, 0xFFFFFF, 0x9FC8FF, 0.1F, 8, 0.1F);
        Fx.p(S.RING, at).size(0.2F, 1.0F).easeOut().color(0xFFFFFF, 0x9FC8FF).alpha(0.9F, 0).life(6).spawn();
    }

    /** A warning before a monster's special move: a ring tightening at its feet and a "!" spark. */
    private static void mobWarn(P p) {
        Entity e = p.e();
        int life = Math.max(4, (int) p.b());
        float r = Math.max(0.8F, p.a());
        int color = p.colorOr(0xFF3040);
        Vec3 o = feet(e, p.pos());
        ground(S.RING, o, r * 2.2F, color, life).size(r * 2.4F, r * 0.6F).alpha(0.4F, 1F).follow(e).spawn();
        ground(S.GLOW, o, r * 1.6F, color, life).alpha(0.1F, 0.5F).follow(e).spawn();
        Vec3 top = head(e, o).add(0, 0.4, 0);
        Fx.p(S.STREAK, top).upright().color(0xFFFFFF, color).size(0.5F).alpha(1, 0.6F).life(life).follow(e).spawn();
        Fx.p(S.GLOW, top.add(0, -0.45, 0)).color(color).size(0.14F).alpha(1, 0.6F).life(life).follow(e).spawn();
    }

    /** A ring of fire rolling outwards (blaze nova, magma cube landing). */
    private static void fireRing(P p) {
        Vec3 o = p.pos();
        float r = Math.max(1, p.a());
        int color = p.colorOr(0xFF8A30);
        ground(S.WAVE, o, 0.5F, color, 12).size(0.5F, r * 2.1F).easeOut().color(0xFFE0A0, color).alpha(1, 0).spawn();
        circle(o.add(0, 0.2, 0), 0.6, Fx.n(20), 0, v -> {
            Vec3 out = v.subtract(o).multiply(1, 0, 1).normalize();
            Fx.p(S.GLOW, v).vel(out.scale(r * 0.09).add(0, 0.04, 0)).friction(0.92F).color(0xFFE0A0, color).size(0.4F, 0.1F).life(14).spawn();
            smoke(v, 0x2A1A10, 0.4F, 1.2F, 18).vel(out.scale(r * 0.05).add(0, 0.05, 0)).alpha(0.5F, 0).spawn();
        });
        flash(o.add(0, 0.6, 0), color, r * 0.6F, 6);
    }

    private static void chargeDust(P p) {
        Vec3 o = p.pos();
        for (int i = 0, n = Fx.n(4); i < n; i++) {
            smoke(o.add(Fx.r(-0.6, 0.6), 0.1, Fx.r(-0.6, 0.6)), 0x6A5040, 0.4F, 1.1F, 16).vel(0, 0.04, 0).alpha(0.6F, 0).spawn();
        }
    }

    /** A sticky glob arcing to the target's feet, splatting into strands. */
    private static void webShot(P p) {
        Vec3 from = p.pos(), to = p.vec();
        int flight = Math.max(2, (int) p.a());
        FxAnims.run(flight + 1, age -> {
            double k = Math.min(1, age / (double) flight);
            Vec3 at = from.add(to.subtract(from).scale(k)).add(0, Math.sin(k * Math.PI) * 1.2, 0);
            Fx.p(S.GLOW, at).color(0xFFFFFF, 0xD8D8E0).size(0.22F, 0.1F).life(4).spawn();
            Fx.p(S.GLOW, at).color(0xD8D8E0).size(0.1F, 0).alpha(0.6F, 0).life(8).spawn();
            if (age == flight) {
                burst(to.add(0, 0.3, 0), 14, 0.25, S.STREAK, 0xFFFFFF, 0xC8C8D0, 0.25F, 10, 0.3F);
                ground(S.RING, to, 1.0F, 0xE0E0E8, 10).size(0.3F, 1.2F).easeOut().alpha(0.8F, 0).spawn();
            }
            return false;
        });
    }
}
