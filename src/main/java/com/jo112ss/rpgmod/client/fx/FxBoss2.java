package com.jo112ss.rpgmod.client.fx;

import static com.jo112ss.rpgmod.client.fx.SkillFx.*;

import com.jo112ss.rpgmod.client.fx.Fx.S;
import com.jo112ss.rpgmod.client.fx.SkillFx.P;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** The Lich King's and the Spider Queen's effects. */
final class FxBoss2 {
    private FxBoss2() {}

    static boolean play(P p) {
        switch (p.fx()) {
            case "lich_aura" -> lichAura(p);
            case "lich_cast" -> lichCast(p);
            case "lich_orb" -> orb(p, false);
            case "lich_orb_hit" -> orbHit(p);
            case "lich_sigil" -> sigil(p);
            case "lich_pillar" -> pillar(p);
            case "lich_nova" -> nova(p);
            case "lich_beam_lock" -> beamLock(p);
            case "lich_beam" -> beam(p);
            case "lich_summon" -> summon(p);
            case "lich_blink" -> blink(p);
            case "lich_rise" -> rise(p);
            case "lich_drained" -> drained(p);
            case "lich_leave" -> lichLeave(p);
            case "lich_death" -> lichDeath(p);
            case "lich_spawn" -> lichSpawn(p);
            case "queen_spent" -> spent(p);
            case "queen_land" -> land(p);
            case "queen_rear" -> rear(p);
            case "queen_web" -> orb(p, true);
            case "queen_web_hit" -> webHit(p);
            case "queen_mist" -> mist(p);
            case "queen_spray" -> spray(p);
            case "queen_egg" -> egg(p);
            case "queen_hatch" -> hatch(p);
            case "queen_field" -> field(p);
            case "queen_shriek" -> shriek(p);
            case "queen_death" -> queenDeath(p);
            case "queen_spawn" -> queenSpawn(p);
            default -> {
                return false;
            }
        }
        return true;
    }

    private static final int SOUL = 0x7FE8FF, SOUL_DARK = 0x2A6A8A, VIOLET = 0xB06AFF, VENOM = 0x7AE040, SILK = 0xE8E8F0;

    // ================================================================== lich

    /** Wisps of soul light rising round the robe, a cold glow at its feet. */
    private static void lichAura(P p) {
        Entity e = p.e();
        if (e == null) return;
        int c = p.b() >= 2 ? VIOLET : SOUL;
        Vec3 o = e.position();
        float w = Math.max(1, p.a());
        for (int i = 0; i < 3; i++) {
            double a = Fx.r(0, Math.PI * 2), r = w * Fx.r(0.4, 0.8);
            Vec3 v = o.add(Math.cos(a) * r, Fx.r(0, e.getBbHeight() * 0.6), Math.sin(a) * r);
            Fx.p(S.GLOW, v).vel(0, Fx.r(0.03, 0.07), 0).color(0xFFFFFF, c).size(0.25F, 0).alpha(0.7F, 0).life(18).spawn();
        }
        smoke(o.add(Fx.r(-w, w) * 0.5, 0.1, Fx.r(-w, w) * 0.5), 0x101820, 0.6F, 1.4F, 24).alpha(0.4F, 0).spawn();
    }

    /** Casting: a turning sigil at its feet, light gathering at the staff. */
    private static void lichCast(P p) {
        Entity e = p.e();
        int c = p.colorOr(SOUL);
        int life = Math.max(4, (int) p.b());
        float w = Math.max(1, p.a());
        Vec3 f = feet(e, p.pos());
        ground(S.CIRCLE_ARCANE, f, w * 2.4F, c, life).color(FxUlt.light(c), c).alpha(0.9F, 0.5F).spin(0.06F).follow(e).spawn();
        ground(S.CIRCLE_HEX, f, w * 1.3F, c, life).color(0xFFFFFF, c).alpha(0.6F, 0.3F).spin(-0.1F).follow(e).spawn();
        if (e == null) return;
        FxAnims.follow(e, life, age -> {
            Vec3 tip = e.position().add(Vec3.directionFromRotation(0, e.getYRot() + 30).scale(e.getBbWidth() * 0.7)).add(0, e.getBbHeight() * 0.98, 0);
            if (age % 2 == 0) {
                Vec3 from = tip.add(Fx.randomUnit().scale(1.5));
                Fx.p(S.GLOW, from).vel(tip.subtract(from).scale(0.15)).color(0xFFFFFF, c).size(0.2F, 0.05F).life(7).spawn();
            }
            Fx.p(S.FLARE, tip).color(0xFFFFFF, c).size(0.6F + age * 0.03F).alpha(0.6F, 0).life(2).spawn();
            return false;
        });
    }

    /** A projectile flying straight on until a wall or a player: a soul orb, or a web ball. */
    private static void orb(P p, boolean web) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        int c = web ? SILK : p.colorOr(SOUL);
        Vec3 vel = p.vec();
        final Vec3[] pos = {p.pos()};
        int life = Math.max(1, (int) p.a());
        FxAnims.run(life, age -> {
            Vec3 from = pos[0], to = from.add(vel);
            var block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, (Entity) null));
            boolean wall = block.getType() != HitResult.Type.MISS;
            Vec3 end = wall ? block.getLocation() : to;
            for (Player pl : level.getEntitiesOfClass(Player.class, new AABB(from, end).inflate(0.8), pl -> pl.isAlive() && !pl.isSpectator())) {
                if (pl.getBoundingBox().inflate(0.35).clip(from, end).isPresent() || pl.getBoundingBox().inflate(0.35).contains(end)) return true;
            }
            pos[0] = end;
            if (web) {
                Fx.p(S.SHARD, end).color(0xFFFFFF, c).size(0.45F, 0.3F).randomAngle().spin(0.3F).life(2).spawn();
                if (age % 2 == 0) Fx.p(S.GLOW, end).color(c).size(0.2F, 0).alpha(0.6F, 0).life(6).spawn();
            } else {
                Fx.p(S.ORB, end).color(0xFFFFFF, c).size(0.45F).alpha(1, 1).life(1).spawn();
                Fx.p(S.GLOW, end).color(c).size(1.0F).alpha(0.45F, 0.3F).life(1).spawn();
                Fx.p(S.GLOW, end).color(FxUlt.light(c), c).size(0.3F, 0).alpha(0.7F, 0).life(9).spawn();
                if (age % 3 == 0) Fx.p(S.TWINKLE, end.add(Fx.randomUnit().scale(0.3))).color(0xFFFFFF, c).size(0.3F, 0).life(6).spawn();
            }
            return wall;
        });
    }

    private static void orbHit(P p) {
        int c = p.colorOr(SOUL);
        flash(p.pos(), c, 1.4F, 5);
        burst(p.pos(), 14, 0.3, S.GLOW, 0xFFFFFF, c, 0.2F, 10, 0);
    }

    /** A sigil filling up under where a soul pillar will burst. */
    private static void sigil(P p) {
        int c = p.colorOr(VIOLET);
        float r = Math.max(1, p.a());
        int life = Math.max(6, (int) p.b());
        Vec3 o = p.pos();
        ground(S.RING, o, r * 1.05F, c, life).color(FxUlt.light(c), c).alpha(0.95F, 0.95F).spawn();
        ground(S.CIRCLE_ARCANE, o, 0.2F, c, life).color(0xFFFFFF, c).size(0.2F, r * 1.1F).alpha(0.4F, 0.85F).spin(0.08F).spawn();
    }

    private static void pillar(P p) {
        int c = p.colorOr(VIOLET);
        Vec3 o = p.pos();
        float r = Math.max(1, p.a());
        Fx.p(S.BEAM, o.add(0, 2.5, 0)).upright().color(0xFFFFFF, c).size(r * 0.9F, r * 0.3F).alpha(0.9F, 0).life(12).spawn();
        flash(o.add(0, 1, 0), c, r * 1.2F, 6);
        for (int i = 0; i < 16; i++) {
            Vec3 v = o.add(Fx.r(-r, r) * 0.6, Fx.r(0, 0.5), Fx.r(-r, r) * 0.6);
            Fx.p(S.GLOW, v).vel(0, Fx.r(0.2, 0.45), 0).color(0xFFFFFF, c).size(0.25F, 0).life(14).spawn();
        }
    }

    private static void nova(P p) {
        int c = p.colorOr(SOUL);
        Vec3 o = p.pos();
        float r = Math.max(2, p.a());
        flash(o.add(0, 1.5, 0), c, r * 0.6F, 8);
        ground(S.WAVE, o, 0.5F, c, 14).size(0.5F, r * 1.15F).easeOut().color(0xFFFFFF, c).alpha(1, 0).spawn();
        ground(S.RING, o, 0.5F, c, 10).size(0.5F, r).easeOut().color(FxUlt.light(c), c).alpha(1, 0).spawn();
        burst(o.add(0, 1, 0), 50, 0.6, S.GLOW, 0xFFFFFF, c, 0.3F, 14, 0);
        FxAnims.shake(o, 0.4F, 6, 14);
    }

    /** The sight locks: a brighter, steady line for the moment before the beam. */
    private static void beamLock(P p) {
        Vec3 a = p.pos(), b = p.pos().add(p.vec());
        SkillFx.line(a, b, 0.6, (v, t) -> Fx.p(S.GLOW, v).color(0xFFFFFF, 0xFF3040).size(0.18F, 0.1F).alpha(0.9F, 0.4F).life(10).spawn());
    }

    private static void beam(P p) {
        int c = p.colorOr(0xFF4060);
        Vec3 a = p.pos(), b = p.pos().add(p.vec());
        Vec3 dir = p.vec().normalize();
        SkillFx.line(a, b, 0.5, (v, t) -> {
            Fx.p(S.GLOW, v).color(0xFFFFFF, c).size(0.9F, 0.2F).alpha(1, 0).life(10).spawn();
            if (Fx.RND.nextFloat() < 0.3F) Fx.p(S.SPARK, v.add(Fx.randomUnit().scale(0.4))).vel(Fx.randomUnit().scale(0.08)).color(0xFFFFFF, c).size(0.2F, 0).life(8).spawn();
        });
        Fx.p(S.STREAK, a.add(dir.scale(a.distanceTo(b) / 2))).fixed(Fx.axial(a, dir)).color(0xFFFFFF, c).size((float) a.distanceTo(b) * 0.5F, 0.6F).alpha(0.8F, 0).life(8).spawn();
        flash(a, c, 2.0F, 6);
        flash(b, c, 1.6F, 6);
    }

    private static void summon(P p) {
        Entity e = p.e();
        Vec3 f = feet(e, p.pos());
        int life = Math.max(4, (int) p.b());
        ground(S.CIRCLE_HEX, f, 6, SOUL, life).color(0xFFFFFF, SOUL_DARK).alpha(0.8F, 0.3F).spin(0.05F).spawn();
        for (int i = 0; i < 24; i++) {
            double a = Fx.r(0, Math.PI * 2), r = Fx.r(2, 6);
            Vec3 v = f.add(Math.cos(a) * r, 0.1, Math.sin(a) * r);
            Fx.p(S.GLOW, v).vel(0, Fx.r(0.04, 0.12), 0).color(0xFFFFFF, SOUL).size(0.25F, 0).life(20).delay(Fx.RND.nextInt(life)).spawn();
            smoke(v, 0x102028, 0.5F, 1.2F, 22).delay(Fx.RND.nextInt(life)).spawn();
        }
    }

    private static void blink(P p) {
        Vec3 o = p.pos();
        float w = Math.max(1, p.a()), h = Math.max(2, p.b());
        for (int i = 0; i < 26; i++) {
            Vec3 v = o.add(Fx.r(-w, w) * 0.7, Fx.r(0, h), Fx.r(-w, w) * 0.7);
            smoke(v, 0x181424, 0.8F, 1.6F, 22).vel(Fx.randomUnit().scale(0.04)).spawn();
            if (i % 2 == 0) Fx.p(S.GLOW, v).vel(Fx.randomUnit().scale(0.08)).color(0xFFFFFF, SOUL).size(0.2F, 0).life(12).spawn();
        }
        Fx.p(S.RING, o.add(0, h / 2, 0)).upright().color(0xFFFFFF, SOUL).size(0.3F, w * 2).easeOut().alpha(1, 0).life(8).spawn();
    }

    private static void rise(P p) {
        Entity e = p.e();
        Vec3 f = feet(e, p.pos());
        int life = Math.max(10, (int) p.b());
        ground(S.CIRCLE_ARCANE, f, 7, VIOLET, life).color(0xFFFFFF, VIOLET).alpha(0.9F, 0.6F).spin(0.12F).spawn();
        Fx.p(S.BEAM, f.add(0, 6, 0)).upright().color(0xFFFFFF, VIOLET).size(2.5F, 1.0F).alpha(0.7F, 0).life(life).spawn();
        if (e != null) FxAnims.follow(e, life, age -> {
            if (age % 2 == 0) {
                double a = age * 0.4;
                for (int k = 0; k < 3; k++) {
                    double aa = a + k * 2.094;
                    Vec3 v = e.position().add(Math.cos(aa) * 3, age * 0.15, Math.sin(aa) * 3);
                    Fx.p(S.GLOW, v).color(0xFFFFFF, VIOLET).size(0.4F, 0).life(12).spawn();
                }
            }
            return false;
        });
    }

    private static void drained(P p) {
        Entity e = p.e();
        int life = Math.max(10, (int) p.b());
        Vec3 f = feet(e, p.pos());
        float w = Math.max(1, p.a());
        ground(S.RING, f, w * 2.2F, 0xFFD050, life).color(0xFFF0A0, 0xFFD050).alpha(0.9F, 0.6F).follow(e).spawn();
        if (e != null) FxAnims.follow(e, life, age -> {
            if (age % 5 == 0) {
                Vec3 v = e.position().add(Fx.r(-w, w) * 0.5, e.getBbHeight() * Fx.r(0.4, 0.9), Fx.r(-w, w) * 0.5);
                Fx.p(S.GLOW, v).vel(0, -0.03, 0).color(SOUL, SOUL_DARK).size(0.25F, 0).alpha(0.6F, 0).life(20).spawn();
                smoke(v, 0x202a30, 0.4F, 1.0F, 20).alpha(0.4F, 0).spawn();
            }
            return false;
        });
    }

    private static void lichLeave(P p) {
        Entity e = p.e();
        int life = Math.max(10, (int) p.b());
        if (e != null) FxAnims.follow(e, life, age -> {
            for (int i = 0; i < 2; i++) smoke(e.position().add(Fx.r(-1, 1), Fx.r(0, e.getBbHeight()), Fx.r(-1, 1)), 0x101820, 0.8F, 1.8F, 30).spawn();
            return false;
        });
    }

    private static void lichDeath(P p) {
        Vec3 o = p.pos();
        float h = Math.max(2, p.b());
        flash(o.add(0, h / 2, 0), SOUL, 5, 14);
        Fx.p(S.BEAM, o.add(0, 8, 0)).upright().color(0xFFFFFF, SOUL).size(3, 0.5F).alpha(0.9F, 0).life(40).spawn();
        for (int i = 0; i < 80; i++) {
            Vec3 v = o.add(Fx.r(-1.5, 1.5), Fx.r(0, h), Fx.r(-1.5, 1.5));
            Fx.p(S.GLOW, v).vel(Fx.randomUnit().scale(0.1).add(0, 0.12, 0)).color(0xFFFFFF, i % 3 == 0 ? VIOLET : SOUL).size(0.3F, 0).life(30 + Fx.RND.nextInt(20)).spawn();
        }
        for (int i = 0; i < 20; i++) Fx.vanilla(ParticleTypes.SOUL, o.x + Fx.r(-1.5, 1.5), o.y + Fx.r(0, h), o.z + Fx.r(-1.5, 1.5), 0, 0.05, 0);
    }

    private static void lichSpawn(P p) {
        Vec3 o = p.pos();
        float w = Math.max(1, p.a());
        ground(S.CIRCLE_ARCANE, o, w * 4, SOUL, 40).color(0xFFFFFF, SOUL).alpha(1, 0).spin(0.1F).spawn();
        Fx.p(S.BEAM, o.add(0, 5, 0)).upright().color(0xFFFFFF, SOUL).size(2, 0.4F).alpha(0.9F, 0).life(30).spawn();
        for (int i = 0; i < 40; i++) smoke(o.add(Fx.r(-2, 2), Fx.r(0, 4), Fx.r(-2, 2)), 0x101820, 1.0F, 2.2F, 30).spawn();
    }

    // ================================================================== spider queen

    private static void spent(P p) {
        Entity e = p.e();
        int life = Math.max(10, (int) p.b());
        Vec3 f = feet(e, p.pos());
        float w = Math.max(1, p.a());
        ground(S.RING, f, w * 1.3F, 0xFFD050, life).color(0xFFF0A0, 0xFFD050).alpha(0.9F, 0.6F).follow(e).spawn();
        if (e != null) FxAnims.follow(e, life, age -> {
            if (age % 6 == 0) Fx.p(S.GLOW, e.position().add(Fx.r(-w, w) * 0.4, e.getBbHeight() * 0.9, Fx.r(-w, w) * 0.4)).vel(0, 0.03, 0)
                    .color(0xFFFFFF, 0xFFD050).size(0.25F, 0).life(16).spawn();
            return false;
        });
    }

    private static void land(P p) {
        Vec3 o = p.pos();
        float r = Math.max(1, p.a());
        ground(S.WAVE, o, 0.5F, VIOLET, 12).size(0.5F, r * 1.3F).easeOut().color(0xFFFFFF, VIOLET).alpha(1, 0).spawn();
        ground(S.CRACK, o, r, 0x605070, 30).color(0xB0A0C0, 0x403050).alpha(0.8F, 0).randomAngle().spawn();
        for (int i = 0; i < 20; i++) debrisAt(o, r);
        FxAnims.shake(o, 0.35F, 5, 12);
    }

    private static void debrisAt(Vec3 o, float r) {
        double a = Fx.r(0, Math.PI * 2), d = Fx.r(0, r);
        Vec3 v = o.add(Math.cos(a) * d, 0.2, Math.sin(a) * d);
        smoke(v, 0x403848, 0.5F, 1.4F, 18).vel(Math.cos(a) * 0.08, 0.06, Math.sin(a) * 0.08).spawn();
    }

    /** Rearing: a glow at her mouth in the colour of what is coming (silk or venom). */
    private static void rear(P p) {
        Entity e = p.e();
        int c = p.colorOr(SILK);
        int life = Math.max(4, (int) p.b());
        if (e == null) return;
        FxAnims.follow(e, life, age -> {
            Vec3 mouth = e.position().add(Vec3.directionFromRotation(0, e.getYRot()).scale(e.getBbWidth() * 0.55)).add(0, e.getBbHeight() * 0.6, 0);
            Fx.p(S.GLOW, mouth).color(0xFFFFFF, c).size(0.5F + age * 0.04F).alpha(0.6F, 0).life(2).spawn();
            if (age % 2 == 0) {
                Vec3 from = mouth.add(Fx.randomUnit().scale(1.2));
                Fx.p(S.GLOW, from).vel(mouth.subtract(from).scale(0.18)).color(0xFFFFFF, c).size(0.18F, 0.05F).life(6).spawn();
            }
            return false;
        });
    }

    private static void webHit(P p) {
        Vec3 o = p.pos();
        Fx.p(S.CROSS, o).color(0xFFFFFF, SILK).size(0.4F, 1.6F).easeOut().alpha(0.9F, 0).randomAngle().life(14).spawn();
        burst(o, 10, 0.2, S.SHARD, 0xFFFFFF, SILK, 0.25F, 12, 0.03F);
    }

    /** Poison mist lingering on the ground. */
    private static void mist(P p) {
        Vec3 o = p.pos();
        float r = Math.max(1, p.a());
        int life = Math.max(10, (int) p.b());
        ground(S.CIRCLE_NATURE, o, r * 2, VENOM, life).color(0xB0FF80, VENOM).alpha(0.5F, 0.0F).spin(0.02F).spawn();
        FxAnims.run(life, age -> {
            if (age % 3 == 0) {
                double a = Fx.r(0, Math.PI * 2), d = Fx.r(0, r);
                Vec3 v = o.add(Math.cos(a) * d, 0.2, Math.sin(a) * d);
                smoke(v, 0x507a28, 0.8F, 1.8F, 30).vel(0, 0.015, 0).alpha(0.45F, 0).spawn();
                Fx.p(S.GLOW, v).vel(0, 0.02, 0).color(0xC0FF90, VENOM).size(0.15F, 0).life(20).spawn();
            }
            return false;
        });
    }

    private static void spray(P p) {
        Vec3 o = p.pos();
        Vec3 dir = p.vec().normalize();
        double len = p.vec().length();
        for (int i = 0; i < 60; i++) {
            Vec3 d = dir.add(Fx.randomUnit().scale(0.5)).normalize();
            Fx.p(S.GLOW, o).vel(d.scale(Fx.r(0.3, 0.6) * len / 8)).friction(0.86F).color(0xC0FF90, VENOM).size(0.3F, 0).life(16 + Fx.RND.nextInt(8)).spawn();
            if (i % 3 == 0) smoke(o.add(d.scale(Fx.r(1, len))), 0x507a28, 0.6F, 1.6F, 22).alpha(0.5F, 0).spawn();
        }
    }

    /** An egg sac pulsing until it hatches. */
    private static void egg(P p) {
        Vec3 o = p.pos();
        int life = Math.max(10, (int) p.b());
        FxAnims.run(life, age -> {
            float pulse = 0.5F + 0.5F * (float) Math.sin(age * (0.2 + age * 0.004));
            Fx.p(S.ORB, o.add(0, 0.45, 0)).color(0xF0E8F8, 0xB090D0).size(0.8F + pulse * 0.15F).alpha(0.95F, 0.95F).life(1).spawn();
            Fx.p(S.GLOW, o.add(0, 0.45, 0)).color(VIOLET).size(1.2F).alpha(0.25F + 0.25F * pulse, 0).life(1).spawn();
            if (age % 10 == 0) ground(S.RING, o, 1.4F, VIOLET, 10).color(0xFFFFFF, VIOLET).alpha(0.5F, 0).spawn();
            return false;
        });
    }

    private static void hatch(P p) {
        Vec3 o = p.pos();
        burst(o.add(0, 0.5, 0), 18, 0.3, S.SHARD, 0xF0E8F8, 0xB090D0, 0.3F, 14, 0.05F);
        flash(o.add(0, 0.5, 0), VIOLET, 1.2F, 5);
    }

    /** A web field: a ring that fills, then webbing lying on the ground a while. */
    private static void field(P p) {
        Vec3 o = p.pos();
        float r = Math.max(1, p.a());
        int warn = Math.max(4, (int) p.b());
        int last = Math.max(10, (int) p.vec().x);
        ground(S.RING, o, r * 1.05F, SILK, warn).color(0xFFFFFF, 0xB0B0C0).alpha(0.9F, 0.9F).spawn();
        ground(S.CIRCLE_HEX, o, 0.2F, SILK, warn).color(0xFFFFFF, SILK).size(0.2F, r).alpha(0.3F, 0.7F).spawn();
        FxAnims.later(warn, () -> {
            for (int k = 0; k < 3; k++) ground(S.CROSS, o, r * 1.9F, SILK, last).color(0xFFFFFF, 0xC8C8D8).alpha(0.75F, 0.5F).angle(k * 1.05F).spawn();
            ground(S.RING, o, r * 1.9F, SILK, last).color(0xFFFFFF, 0xC8C8D8).alpha(0.6F, 0.3F).spawn();
        });
    }

    private static void shriek(P p) {
        Entity e = p.e();
        int life = Math.max(10, (int) p.b());
        if (e == null) return;
        FxAnims.follow(e, life, age -> {
            if (age % 6 == 0) {
                Vec3 m = e.position().add(0, e.getBbHeight() * 0.6, 0);
                Fx.p(S.RING, m).upright().fixed(Fx.across(Vec3.directionFromRotation(0, e.getYRot()))).color(0xFFFFFF, VIOLET).size(0.5F, 6).easeOut().alpha(0.8F, 0).life(12).spawn();
            }
            if (age == 0) FxAnims.shake(e.position(), 0.5F, 10, 20);
            return false;
        });
    }

    private static void queenDeath(P p) {
        Vec3 o = p.pos();
        float w = Math.max(1, p.a());
        flash(o.add(0, 1, 0), VIOLET, 4, 12);
        for (int i = 0; i < 60; i++) {
            Vec3 v = o.add(Fx.r(-w, w) * 0.6, Fx.r(0, 2), Fx.r(-w, w) * 0.6);
            Fx.p(S.GLOW, v).vel(Fx.randomUnit().scale(0.12).add(0, 0.08, 0)).color(0xFFFFFF, i % 2 == 0 ? VIOLET : VENOM).size(0.3F, 0).life(30).spawn();
            if (i % 3 == 0) smoke(v, 0x302838, 0.8F, 2.0F, 30).spawn();
        }
    }

    private static void queenSpawn(P p) {
        Vec3 o = p.pos();
        float w = Math.max(1, p.a());
        ground(S.CRACK, o, w * 2, 0x605070, 60).color(0xB0A0C0, 0x403050).alpha(0.9F, 0).randomAngle().spawn();
        for (int i = 0; i < 40; i++) debrisAt(o, w * 1.5F);
        burst(o.add(0, 1, 0), 30, 0.4, S.SHARD, 0xFFFFFF, VIOLET, 0.3F, 20, 0.05F);
        FxAnims.shake(o, 0.5F, 8, 24);
    }
}
