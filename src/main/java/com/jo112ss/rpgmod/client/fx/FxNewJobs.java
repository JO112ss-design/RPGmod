package com.jo112ss.rpgmod.client.fx;

import static com.jo112ss.rpgmod.client.fx.SkillFx.*;

import com.jo112ss.rpgmod.client.fx.Fx.S;
import com.jo112ss.rpgmod.client.fx.SkillFx.P;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Effects of the pirate, lancer, monk and bard ("n_*"). */
final class FxNewJobs {
    private FxNewJobs() {}

    static boolean play(P p) {
        switch (p.fx()) {
            case "n_shot" -> shot(p);
            case "n_cannon_fire" -> cannonFire(p);
            case "n_chain" -> chain(p);
            case "n_fuse" -> fuse(p);
            case "n_ki" -> ki(p);
            case "n_fist" -> fist(p);
            case "n_spear_fall" -> spearFall(p);
            case "n_note" -> note(p);
            case "n_notes" -> notes(p);
            case "n_song" -> song(p);
            case "n_finale" -> finale(p);
            case "n_wave" -> wave(p);
            default -> {
                return false;
            }
        }
        return true;
    }

    private static int light(int c) {
        return Fx.mix(c, 0xFFFFFF, 0.55);
    }

    // ------------------------------------------------------------------ pirate

    /** Gunshot: pos = muzzle, vec = where it stopped, b = 1 if it hit something. */
    private static void shot(P p) {
        int c = p.colorOr(0xFFE0A0);
        Vec3 from = p.pos(), to = p.vec();
        Vec3 dir = to.subtract(from).normalize();
        flash(from, 0xFFC060, 0.7F, 3);
        Fx.p(S.SPARK, from).color(0xFFFFFF, 0xFFB040).size(0.6F, 0.1F).randomAngle().life(3).spawn();
        spray(from, dir, 0.35, 6, 0.25, S.GLOW, 0xFFFFFF, 0xFFA030, 0.08F, 5, 0.1F);
        for (int i = 0; i < 3; i++) smoke(from.add(dir.scale(0.2 * i)), 0x9A9AA0, 0.15F, 0.6F, 18).vel(dir.scale(0.03).add(0, 0.02, 0)).alpha(0.5F, 0).spawn();
        // the tracer: a short bright streak racing along the line
        double len = from.distanceTo(to);
        int steps = Math.max(1, (int) (len / 6));
        for (int i = 0; i < steps; i++) {
            Vec3 a = from.add(dir.scale(len * i / steps)), b = from.add(dir.scale(Math.min(len, len * (i + 1) / steps)));
            segment(a, b, 0.05F).color(0xFFFFFF, c).alpha(0.9F, 0).life(3).delay(i / 2).spawn();
        }
        if (p.b() > 0) {
            Fx.p(S.SPARK, to).color(0xFFFFFF, c).size(0.5F, 0).randomAngle().life(4).spawn();
            spray(to, dir.scale(-1), 0.6, 6, 0.2, S.GLOW, 0xFFFFFF, 0xFF9040, 0.08F, 6, 0.3F);
        } else {
            Fx.vanilla(ParticleTypes.SMOKE, to.x, to.y, to.z, 0, 0.02, 0);
        }
    }

    /**
     * A chain from the player's hand (entity) to another entity (id in a) for b ticks, drawn every
     * tick from where both are on this client, so it stays attached while they move.
     */
    private static void chain(P p) {
        Entity owner = p.e();
        var level = net.minecraft.client.Minecraft.getInstance().level;
        if (owner == null || level == null) return;
        int otherId = Math.round(p.a());
        int life = Math.max(2, (int) p.b());
        FxAnims.follow(owner, life, age -> {
            Entity other = level.getEntity(otherId);
            if (other == null || other.isRemoved()) return true;
            Vec3 from = hand(owner, owner.position());
            Vec3 to = other.position().add(0, other.getBbHeight() * 0.5, 0);
            Vec3 d = to.subtract(from);
            double len = d.length();
            if (len < 0.3) return false;
            Vec3 dir = d.scale(1 / len);
            int links = Math.min(80, (int) (len / 0.32));
            for (int i = 0; i <= links; i++) {
                double t = i / (double) Math.max(1, links);
                // a little slack: the chain sags in the middle
                Vec3 v = from.add(d.scale(t)).add(0, -Math.sin(t * Math.PI) * Math.min(0.6, len * 0.04), 0);
                Fx.p(S.RING, v).fixed(i % 2 == 0 ? Fx.across(dir) : Fx.axial(v, dir)).size(0.17F).color(0x60666E).alphaBlend().lit().life(2).spawn();
            }
            return false;
        });
    }

    /** Cannon shot: a big muzzle flash and smoke. pos = muzzle, vec = direction. */
    private static void cannonFire(P p) {
        Vec3 at = p.pos();
        Vec3 dir = p.vec().normalize();
        flash(at, 0xFFA040, 1.4F, 4);
        spray(at, dir, 0.4, 12, 0.35, S.GLOW, 0xFFFFFF, 0xFF8020, 0.15F, 6, 0.0F);
        for (int i = 0; i < 8; i++) {
            smoke(at.add(dir.scale(Fx.r(0, 0.8))), 0x7A7A80, 0.4F, 1.4F, 30).vel(dir.scale(Fx.r(0.02, 0.1)).add(Fx.randomUnit().scale(0.02)).add(0, 0.02, 0)).alpha(0.7F, 0).spawn();
        }
        FxAnims.shake(at, 0.15F, 3, 10);
    }

    /** A burning fuse on an entity for b ticks. */
    private static void fuse(P p) {
        Entity e = p.e();
        if (e == null) return;
        int life = Math.max(5, (int) p.b());
        FxAnims.follow(e, life, age -> {
            Vec3 top = e.position().add(0, 0.85, 0);
            Fx.p(S.GLOW, top).vel(Fx.randomUnit().scale(0.03).add(0, 0.04, 0)).color(0xFFFFFF, 0xFFA020).size(0.15F, 0).life(5).spawn();
            if (age % 3 == 0) Fx.vanilla(ParticleTypes.SMOKE, top.x, top.y + 0.1, top.z, 0, 0.03, 0);
            if (age > life - 12 && age % 2 == 0) flash(top, 0xFF4020, 0.4F, 2); // about to go
            return false;
        });
    }

    // ------------------------------------------------------------------ lancer

    /** A spear falling from the sky onto pos, landing after b ticks. */
    private static void spearFall(P p) {
        int c = p.colorOr(0xE0B070);
        Vec3 at = p.pos();
        int fall = Math.max(2, (int) p.b());
        Vec3 sky = new Vec3(Fx.r(-0.6, 0.6), 7, Fx.r(-0.6, 0.6));
        FxAnims.run(fall, age -> {
            double t = 1 - (double) age / fall;
            Vec3 tip = at.add(sky.scale(t));
            Vec3 tail = tip.add(sky.normalize().scale(1.6));
            segment(tip, tail, 0.08F).color(0xFFFFFF, c).alpha(0.95F, 0.6F).life(2).spawn();
            Fx.p(S.GLOW, tip).color(light(c)).size(0.18F, 0).life(4).spawn();
            return false;
        });
        FxAnims.later(fall, () -> {
            Fx.p(S.STREAK, at.add(0, 0.6, 0)).upright().size(1.2F, 0.6F).color(0xFFFFFF, c).alpha(0.9F, 0).life(10).spawn();
            ground(S.RING, at, 0.2F, c, 8).size(0.2F, 1.4F).easeOut().alpha(0.9F, 0).spawn();
            debris(at, 4, 0.3, 0.4);
        });
    }

    // ------------------------------------------------------------------ monk

    /** Ki gathers into the monk: b = ki now. */
    private static void ki(P p) {
        int c = p.colorOr(0xFFD27A);
        Entity e = p.e();
        Vec3 o = chest(e, p.pos());
        for (int i = 0, n = Fx.n(6 + (int) p.b() * 2); i < n; i++) {
            Vec3 off = Fx.randomUnit().scale(1.1);
            Fx.p(S.GLOW, o.add(off)).vel(off.scale(-0.1)).color(light(c), c).size(0.14F, 0).life(10).follow(e).spawn();
        }
        flash(o, c, 0.5F + 0.12F * p.b(), 4);
    }

    /** Fist impacts in front: pos = in front of the monk, vec = facing; pts = enemies hit. */
    private static void fist(P p) {
        int c = p.colorOr(0xFFC860);
        Vec3 at = p.pos();
        Vec3 dir = flatDir(p.vec());
        for (int i = 0; i < 3; i++) {
            Vec3 v = at.add(Fx.right(dir).scale(Fx.r(-0.6, 0.6))).add(0, Fx.r(-0.4, 0.4), 0);
            Fx.p(S.RING, v).fixed(Fx.across(dir)).size(0.1F, 0.7F).easeOut().color(0xFFFFFF, c).alpha(0.9F, 0).life(5).delay(i).spawn();
            Fx.p(S.GLOW, v).color(0xFFFFFF, c).size(0.45F, 0).life(3).delay(i).spawn();
        }
        spray(at, dir, 0.5, 5, 0.25, S.GLOW, 0xFFFFFF, c, 0.08F, 6, 0.2F);
        hits(p.pts(), c, 0);
    }

    // ------------------------------------------------------------------ bard

    /** One note floating up from pos: b = 2 perfect (bigger), 1 good. */
    private static void note(P p) {
        int c = p.colorOr(0x48C9B0);
        Vec3 at = p.pos();
        float s = p.b() >= 2 ? 0.42F : 0.3F;
        Fx.p(S.NOTE, at.add(Fx.r(-0.4, 0.4), 0, Fx.r(-0.4, 0.4))).vel(Fx.r(-0.02, 0.02), 0.06, Fx.r(-0.02, 0.02))
                .color(0xFFFFFF, c).size(s, s * 0.6F).alpha(1, 0).life(22).spawn();
        if (p.b() >= 2) Fx.p(S.GLOW, at).color(c).size(0.8F, 0).alpha(0.6F, 0).life(6).spawn();
    }

    /** Notes swirling around an entity for a seconds (b = 1: golden, grand). */
    private static void notes(P p) {
        int c = p.colorOr(0x48C9B0);
        Entity e = p.e();
        if (e == null) return;
        int life = Math.max(10, (int) (p.a() * 20));
        FxAnims.follow(e, life, age -> {
            if (age % (p.b() > 0 ? 2 : 4) != 0) return false;
            double a = age * 0.4, r = 0.9;
            Vec3 v = e.position().add(Math.cos(a) * r, 0.8 + 0.5 * Math.sin(age * 0.2), Math.sin(a) * r);
            Fx.p(S.NOTE, v).vel(0, 0.05, 0).color(0xFFFFFF, c).size(0.28F, 0.15F).alpha(1, 0).life(20).spawn();
            return false;
        });
    }

    /** One second of a song: a ring of sound around the bard. a = reach, b = strength. */
    private static void song(P p) {
        int c = p.colorOr(0x48C9B0);
        Entity e = p.e();
        Vec3 o = feet(e, p.pos());
        float r = Math.max(1, p.a());
        ground(S.RING, o, 0.5F, c, 16).size(0.5F, r).easeOut().color(light(c), c).alpha(0.35F + 0.2F * p.b(), 0).spawn();
        circle(o, r * 0.9, Fx.n(6), Fx.r(0, 6), v -> Fx.p(S.NOTE, v.add(0, 0.4, 0)).vel(0, 0.04, 0).color(0xFFFFFF, c).size(0.25F, 0.1F).alpha(0.8F, 0).life(18).spawn());
    }

    /** Finale: rings rolling out, notes bursting (b = points). */
    private static void finale(P p) {
        int c = p.colorOr(0x48C9B0);
        Vec3 o = p.pos();
        float r = Math.max(2, p.a());
        int pts = (int) p.b();
        for (int k = 0; k < 3; k++) ground(S.WAVE, o, 0.5F, c, 16).size(0.5F, r * 1.1F).color(0xFFFFFF, k == 1 ? 0xFFE070 : c).alpha(0.9F, 0).easeOut().delay(k * 4).spawn();
        for (int i = 0, n = Fx.n(16 + pts * 2); i < n; i++) {
            Vec3 v = Fx.randomUnit().multiply(1, 0.3, 1).normalize();
            Fx.p(S.NOTE, o.add(0, 1, 0)).vel(v.scale(Fx.r(0.2, 0.45)).add(0, 0.1, 0)).friction(0.9F).color(0xFFFFFF, i % 3 == 0 ? 0xFFE070 : c)
                    .size(0.35F, 0.15F).alpha(1, 0).life(26).spawn();
        }
        flash(o.add(0, 1, 0), 0xFFE070, 2.0F, 6);
        FxAnims.shake(o, 0.3F, 6, 12);
    }

    /** The lute's sound wave: pos = start, vec = velocity, a = range, entity = target. */
    private static void wave(P p) {
        int c = p.colorOr(0x48C9B0);
        simProjectile(p.pos(), p.vec(), p.a(), p.e(), p.e() != null ? 0.2 : 0, new ProjDraw() {
            @Override
            public void step(Vec3 at, Vec3 dir, int n) {
                if (n % 2 == 0) Fx.p(S.RING, at).fixed(Fx.across(dir)).size(0.2F, 0.7F).color(0xFFFFFF, c).alpha(0.8F, 0).life(6).spawn();
            }

            @Override
            public void head(Vec3 at, Vec3 dir, int age) {
                Fx.p(S.NOTE, at).color(0xFFFFFF, c).size(0.3F).alpha(1, 1).life(1).spawn();
                Fx.p(S.GLOW, at).color(c).size(0.6F).alpha(0.5F, 0.4F).life(1).spawn();
            }
        });
    }
}
