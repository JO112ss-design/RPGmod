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
