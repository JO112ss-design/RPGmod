package com.jo112ss.rpgmod.client.fx;

import static com.jo112ss.rpgmod.client.fx.SkillFx.*;

import com.jo112ss.rpgmod.client.fx.Fx.S;
import com.jo112ss.rpgmod.client.fx.SkillFx.P;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * The ultimates of the base jobs, each with a look of its own (no shared ring / burst / aura): the
 * war god's burning swings, the splitting earth, a volley from a sigil in the sky, the hawk's eye,
 * a crest of light on one ally, a sword of judgement, stopped time, a light that turns back, shadow
 * copies, a pirate flag and broadside, a spinning dragon fang, a field of spears, the hundred fists'
 * last blow, a diamond body, and the bard's concert and finale.
 */
public final class FxUlt {
    private FxUlt() {}

    public static boolean play(P p) {
        switch (p.fx()) {
            case "u_war_god" -> warGod(p);
            case "u_war_god_swing" -> warGodSwing(p);
            case "u_earthshatter" -> earthshatter(p);
            case "u_arrow_rain" -> arrowRain(p);
            case "u_eagle_eye" -> eagleEye(p);
            case "u_divine_aegis" -> divineAegis(p);
            case "u_judgement" -> judgement(p);
            case "u_judgement_strike" -> judgementStrike(p);
            case "u_time_stop" -> timeStop(p);
            case "u_time_release" -> timeRelease(p);
            case "u_guardian_light" -> guardianLight(p);
            case "u_rewind" -> rewind(p);
            case "u_shadow_dance" -> shadowDance(p);
            case "u_shadow_strike" -> shadowStrike(p);
            case "u_broadside" -> broadside(p);
            case "u_jolly_roger" -> jollyRoger(p);
            case "u_jolly_pulse" -> jollyPulse(p);
            case "u_dragon_fang" -> dragonFang(p);
            case "u_dragon_fang_end" -> dragonFangEnd(p);
            case "u_spear_field" -> spearField(p);
            case "u_spear_burst" -> spearBurst(p);
            case "u_hundred_finish" -> hundredFinish(p);
            case "u_diamond_body" -> diamondBody(p);
            case "u_grand_concert" -> grandConcert(p);
            case "u_concert_pulse" -> concertPulse(p);
            case "u_finale" -> finale(p);
            default -> {
                return false;
            }
        }
        return true;
    }

    static int light(int c) {
        return Fx.mix(c, 0xFFFFFF, 0.55);
    }

    static int dark(int c) {
        return Fx.mix(c, 0x000000, 0.55);
    }

    static Vec3 feetOf(P p) {
        return p.e() != null ? p.e().position() : p.pos();
    }

    /** A flame tongue rising from {@code at}. */
    static void flameAt(Vec3 at, int c0, int c1, float size, int life, Entity follow) {
        Fx.B b = Fx.p(S.FLAME, at).upright().vel(Fx.r(-0.01, 0.01), Fx.r(0.03, 0.08), Fx.r(-0.01, 0.01)).color(c0, c1)
                .size(size, size * 0.3F).alpha(0.95F, 0).fadeIn(0.1F).life(life);
        if (follow != null) b.follow(follow);
        b.spawn();
    }

    /** A ring of upright sprites around {@code c} (a fence, a palisade, a crown). */
    static void uprightRing(Vec3 c, double r, int n, S s, int c0, int c1, float size, int life, int delayEach) {
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            Vec3 at = c.add(Math.cos(a) * r, 0, Math.sin(a) * r);
            Fx.p(s, at).fixed(Fx.facing(at.subtract(c))).color(c0, c1).size(size).alpha(1, 0).fadeIn(0.15F).life(life).delay(i * delayEach).spawn();
        }
    }

    // ================================================================== fighter: war god

    private static final int WAR = 0xFF4A20, WAR_L = 0xFFC060;

    private static void warGod(P p) {
        Entity e = p.e();
        Vec3 o = feetOf(p);
        int ticks = Math.max(20, (int) (p.a() * 20));
        FxAnims.shake(o, 1.2F, 10, 16);
        flash(o.add(0, 1.2, 0), WAR, 3.5F, 8);
        ground(S.CRACK, o, 2.6F, WAR, 60).color(WAR_L, WAR).alpha(1, 0).randomAngle().size(0.5F, 3.4F).easeOut().spawn();
        ground(S.CRACK, o, 2.0F, WAR, 60).color(0xFFFFFF, WAR).alpha(0.8F, 0).randomAngle().size(0.4F, 2.4F).easeOut().delay(2).spawn();
        // a column of fire
        for (int i = 0, n = Fx.n(44); i < n; i++) {
            double a = Fx.r(0, Math.PI * 2), r = Fx.r(0.2, 1.3);
            flameAt(o.add(Math.cos(a) * r, Fx.r(0, 0.8), Math.sin(a) * r), WAR_L, WAR, Fx.rf(0.9F, 1.7F), 20 + Fx.RND.nextInt(12), null);
        }
        Fx.p(S.FLARE, o.add(0, 1.0, 0)).color(WAR).size(4F, 2F).alpha(0.5F, 0).life(16).spawn();
        spray(o.add(0, 0.2, 0), new Vec3(0, 1, 0), 0.35, 30, 0.8, S.GLOW, WAR_L, WAR, 0.25F, 22, 0.25F);
        debris(o, 22, 0.8, 0.6);
        // embers rising off the body for the whole time
        FxAnims.follow(e, ticks, age -> {
            if (age % 2 != 0) return false;
            Entity f = e;
            double a = Fx.r(0, Math.PI * 2);
            Vec3 at = f.position().add(Math.cos(a) * 0.45, Fx.r(0, 0.9), Math.sin(a) * 0.45);
            flameAt(at, WAR_L, WAR, Fx.rf(0.5F, 0.8F), 14, null);
            if (age % 12 == 0) Fx.p(S.GLOW, f.position().add(0, 1, 0)).color(WAR).size(1.8F, 1.2F).alpha(0.25F, 0).life(10).follow(f).spawn();
            return false;
        });
    }

    private static void warGodSwing(P p) {
        Vec3 at = p.pos();
        Vec3 dir = flatDir(p.vec());
        float r = Math.max(3, p.a());
        Vec3 side = Fx.right(dir);
        FxAnims.shake(at, 0.8F, 6, 12);
        // the burning crescent flies out along the ground
        FxAnims.run(8, age -> {
            double t = (age + 1) / 8.0;
            Vec3 c = at.add(dir.scale(r * t));
            Fx.p(S.SLASH, c).fixed(Fx.basis(side, new Vec3(0, 1, 0).add(dir.scale(0.2)))).color(0xFFFFFF, WAR).size(1.6F + (float) t * 1.6F)
                    .alpha(1, 0).life(5).spawn();
            Fx.p(S.SLASH, c.add(0, -0.2, 0)).fixed(Fx.basis(side, new Vec3(0, 1, 0))).color(WAR_L, WAR).size(2.0F + (float) t * 1.8F).alpha(0.6F, 0).life(6).spawn();
            for (int k = -2; k <= 2; k++) {
                Vec3 g = c.add(side.scale(k * 0.7 * (0.6 + t))).subtract(0, 1.1, 0);
                flameAt(g, WAR_L, WAR, Fx.rf(0.5F, 0.8F), 10, null);
            }
            return false;
        });
        for (int i = 0; i < 3; i++) {
            Vec3 g = at.add(dir.scale(r * (0.35 + i * 0.3))).subtract(0, 1.1, 0);
            ground(S.CRACK, g, 1.4F, WAR, 40).color(WAR_L, dark(WAR)).alpha(0.9F, 0).angle((float) Math.atan2(dir.z, dir.x)).delay(i * 2).spawn();
        }
        hits(p.pts(), WAR, 2);
    }

    // ================================================================== fighter: earthshatter

    private static void earthshatter(P p) {
        Vec3 o = p.pos();
        float r = Math.max(3, p.a());
        int c = 0xC08040;
        FxAnims.shake(o, 2.4F, 20, 28);
        flash(o.add(0, 0.6, 0), 0xFFD080, 3, 6);
        ground(S.CRACK, o, r * 0.6F, c, 120).color(0xFFE0A0, 0x704020).alpha(1, 0).randomAngle().size(1, r * 1.3F).easeOut().spawn();
        // the split runs outwards: rocks thrown up in the wave's front
        FxAnims.run(13, age -> {
            double front = 1.0 + age * (r / 12.0);
            int n = Fx.n(10 + (int) front * 2);
            for (int i = 0; i < n; i++) {
                double a = Fx.r(0, Math.PI * 2);
                Vec3 g = o.add(Math.cos(a) * front, 0.1, Math.sin(a) * front);
                Fx.p(S.SHARD, g).vel(Math.cos(a) * 0.05, Fx.r(0.25, 0.5), Math.sin(a) * 0.05).gravity(1.1F).alphaBlend().lit()
                        .color(Fx.mix(0x6A5038, 0x9A8060, Fx.RND.nextDouble())).size(Fx.rf(0.25F, 0.45F)).randomAngle().spin(Fx.rf(-0.3F, 0.3F)).alpha(1, 1)
                        .life(22).spawn();
            }
            if (age % 2 == 0) debris(o.add(Math.cos(age) * front, 0, Math.sin(age) * front), 8, 0.6, 0.5);
            ground(S.WAVE, o, (float) front, c, 6).color(0xFFE0A0, c).alpha(0.7F, 0).spawn();
            return false;
        });
        for (int i = 0, n = Fx.n(12); i < n; i++) {
            double a = Fx.r(0, Math.PI * 2), d = Fx.r(0.5, r);
            smoke(o.add(Math.cos(a) * d, 0.3, Math.sin(a) * d), 0x8A7058, 1.0F, 2.6F, 40).vel(0, 0.04, 0).alpha(0.6F, 0).delay((int) (d * 2)).spawn();
        }
    }

    // ================================================================== ranger: arrow rain

    private static final int HUNT = 0x7CE070;

    private static void arrowRain(P p) {
        Vec3 at = p.pos();
        float r = Math.max(2, p.a());
        int life = Math.max(10, (int) p.b());
        Vec3 sky = at.add(0, 9, 0);
        // a ring in the sky, and its shadow on the ground
        Fx.p(S.CIRCLE_NATURE, sky).flat().color(light(HUNT), HUNT).size(r * 0.4F, r * 1.1F).easeOut().alpha(0.9F, 0).spin(0.05F).life(life + 10).spawn();
        Fx.p(S.RING, sky).flat().color(HUNT).size(r * 1.2F).alpha(0.7F, 0).life(life + 10).spawn();
        ground(S.CIRCLE_NATURE, at, r, HUNT, life + 10).color(light(HUNT), HUNT).alpha(0.6F, 0).fadeIn(0.2F).spin(-0.03F).spawn();
        FxAnims.run(life, age -> {
            for (int i = 0, n = Fx.n(5); i < n; i++) {
                double a = Fx.r(0, Math.PI * 2), d = Math.sqrt(Fx.RND.nextDouble()) * r;
                Vec3 top = sky.add(Math.cos(a) * d * 0.4, 0, Math.sin(a) * d * 0.4);
                Vec3 g = at.add(Math.cos(a) * d, 0, Math.sin(a) * d);
                Vec3 v = g.subtract(top).scale(1 / 7.0);
                Fx.p(S.STREAK, top).fixed(Fx.axial(top, v)).vel(v).color(0xFFFFFF, HUNT).size(0.5F).alpha(1, 0.7F).life(7).spawn();
                FxAnims.later(7, () -> {
                    Fx.p(S.SPARK, g.add(0, 0.1, 0)).color(0xFFFFFF, HUNT).size(0.5F, 0).randomAngle().life(4).spawn();
                    if (Fx.RND.nextInt(3) == 0) debris(g, 2, 0.1, 0.2);
                });
            }
            return false;
        });
    }

    // ================================================================== ranger: eagle eye

    private static void eagleEye(P p) {
        Entity e = p.e();
        Vec3 o = feetOf(p);
        int ticks = Math.max(20, (int) (p.a() * 20));
        Fx.p(S.EYE, o.add(0, 2.6, 0)).color(0xFFFFFF, HUNT).size(0.2F, 1.1F).easeOut().alpha(1, 0).life(30).follow(e).spawn();
        // two wings of feathers spread behind and fold away
        Vec3 back = e == null ? new Vec3(0, 0, -1) : Vec3.directionFromRotation(0, e.getYRot()).scale(-1);
        Vec3 side = Fx.right(back.scale(-1));
        for (int s = -1; s <= 1; s += 2) {
            for (int i = 0; i < 7; i++) {
                double t = i / 6.0;
                Vec3 at = o.add(0, 1.3 + t * 0.6, 0).add(back.scale(0.3)).add(side.scale(s * (0.3 + t * 1.6)));
                Fx.p(S.FEATHER, at).fixed(Fx.basis(side.scale(s), new Vec3(0, 1, 0).add(side.scale(s * 0.8)))).color(0xFFFFFF, HUNT)
                        .size(0.5F + (float) t * 0.2F).alpha(1, 0).fadeIn(0.2F).life(26).delay(i).follow(e).spawn();
            }
        }
        spray(o.add(0, 1.2, 0), new Vec3(0, 1, 0), 0.9, 18, 0.25, S.FEATHER, 0xFFFFFF, HUNT, 0.3F, 30, -0.02F);
        FxAnims.follow(e, ticks, age -> {
            if (age % 8 != 0) return false;
            Fx.p(S.TWINKLE, e.getEyePosition().add(Fx.r(-0.3, 0.3), Fx.r(-0.1, 0.2), Fx.r(-0.3, 0.3))).color(0xFFFFFF, HUNT).size(0.3F, 0).life(10).follow(e).spawn();
            return false;
        });
    }

    // ================================================================== paladin: divine aegis

    private static final int HOLY = 0xFFE070;

    private static void divineAegis(P p) {
        Entity e = p.e();
        Vec3 o = feetOf(p);
        int ticks = Math.max(20, (int) (p.b() * 20));
        if (p.vec().lengthSqr() > 0 && !p.pts().isEmpty()) {
            Vec3 from = p.pts().get(0);
            line(from, o.add(0, 1, 0), 0.25, (v, t) -> Fx.p(S.GLOW, v).color(0xFFFFFF, HOLY).size(0.3F, 0).life(10).delay((int) (t * 6)).spawn());
        }
        // a shaft of light comes down onto them
        for (int i = 0; i < 8; i++) {
            Fx.p(S.BEAM, o.add(0, 6 - i * 0.6, 0)).upright().color(0xFFFFFF, HOLY).size(1.6F, 0.6F).alpha(0.8F, 0).life(14).delay(i).spawn();
        }
        flash(o.add(0, 1.2, 0), HOLY, 3, 10);
        // six crests turning around them for the whole time
        FxParticle[] crests = new FxParticle[6];
        for (int i = 0; i < 6; i++) {
            crests[i] = Fx.p(S.CREST, o.add(0, 1, 0)).color(0xFFFFFF, HOLY).size(0.1F, 0.7F).easeOut().alpha(0.95F, 0.95F).life(ticks).spawn();
        }
        FxAnims.run(ticks, age -> {
            Vec3 c = (e != null && !e.isRemoved() ? e.position() : o).add(0, 1.0, 0);
            for (int i = 0; i < 6; i++) {
                if (crests[i] == null) continue;
                double a = age * 0.08 + Math.PI * 2 * i / 6;
                Vec3 at = c.add(Math.cos(a) * 1.1, Math.sin(age * 0.1 + i) * 0.25, Math.sin(a) * 1.1);
                crests[i].moveTo(at.x, at.y, at.z);
                crests[i].orientNow(Fx.facing(at.subtract(c)));
                if (age > ticks - 8) crests[i].alphaNow((ticks - age) / 8F);
            }
            if (age % 6 == 0) Fx.p(S.TWINKLE, c.add(Fx.randomUnit().scale(1.2))).color(0xFFFFFF, HOLY).size(0.35F, 0).life(10).spawn();
            return false;
        });
    }

    // ================================================================== paladin: judgement

    private static void judgement(P p) {
        Vec3 o = p.pos();
        float r = Math.max(3, p.a());
        ground(S.CIRCLE_HOLY, o, r, HOLY, 40).color(0xFFFFFF, HOLY).size(r * 0.3F, r).easeOut().alpha(1, 0.4F).spin(0.04F).spawn();
        ground(S.RING, o, r * 1.05F, HOLY, 40).alpha(0.8F, 0.2F).spawn();
        // crosses of light rise around the circle while the sword is called down
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2 * i / 8;
            Vec3 at = o.add(Math.cos(a) * r * 0.85, 0.2, Math.sin(a) * r * 0.85);
            Fx.p(S.CROSS, at).upright().vel(0, 0.06, 0).color(0xFFFFFF, HOLY).size(0.6F, 0.3F).alpha(1, 0).fadeIn(0.2F).life(18).delay(i).spawn();
            Fx.p(S.BEAM, at.add(0, 1.5, 0)).upright().color(0xFFFFFF, HOLY).size(1.6F, 1.2F).alpha(0.6F, 0).life(16).delay(i).spawn();
        }
        // the sword itself, high above, turning to point down
        Vec3 top = o.add(0, 14, 0);
        Fx.p(S.BLADE, top).fixed(Fx.basis(new Vec3(1, 0, 0), new Vec3(0, -1, 0))).color(0xFFFFFF, HOLY).size(1F, 4.5F).easeOut().alpha(0.2F, 1).life(12).spawn();
        Fx.p(S.FLARE, top).color(HOLY).size(3F).alpha(0.5F, 0).life(12).spawn();
        // and it falls in the last four ticks, landing as the judgement strikes
        FxAnims.later(12, () -> FxAnims.run(4, age -> {
            double t = (age + 1) / 4.0;
            Vec3 at = o.add(0, 14 * (1 - t) + 2.6, 0);
            Fx.p(S.BLADE, at).fixed(Fx.basis(new Vec3(1, 0, 0), new Vec3(0, -1, 0))).color(0xFFFFFF, HOLY).size(4.5F).alpha(1, 0.4F).life(2).spawn();
            Fx.p(S.FLARE, at.add(0, 3, 0)).color(HOLY).size(1.6F).alpha(0.6F, 0).life(4).spawn();
            return false;
        }));
    }

    private static void judgementStrike(P p) {
        Vec3 o = p.pos();
        float r = Math.max(3, p.a());
        FxAnims.shake(o, 2.0F, 14, 28);
        // the sword stands in the ground
        FxAnims.later(0, () -> {
            Fx.p(S.BLADE, o.add(0, 2.6, 0)).fixed(Fx.basis(new Vec3(1, 0, 0), new Vec3(0, -1, 0))).color(0xFFFFFF, HOLY).size(4.5F, 4.2F).alpha(1, 0).life(30).spawn();
            flash(o.add(0, 1, 0), HOLY, r * 0.9F, 9);
            ground(S.CRACK, o, r, HOLY, 50).color(0xFFFFFF, HOLY).alpha(1, 0).randomAngle().spawn();
            ground(S.WAVE, o, 1, HOLY, 14).size(1, r * 1.6F).easeOut().color(0xFFFFFF, HOLY).alpha(1, 0).spawn();
            for (Vec3 v : p.pts()) {
                for (int i = 0; i < 4; i++) {
                    Fx.p(S.BEAM, v.add(0, i * 1.2, 0)).upright().color(0xFFFFFF, HOLY).size(1.4F, 0.4F).alpha(0.9F, 0).life(10).delay(i).spawn();
                }
            }
            hits(p.pts(), HOLY, 0);
            burst(o.add(0, 0.5, 0), 40, 0.7, S.TWINKLE, 0xFFFFFF, HOLY, 0.4F, 18, 0.05F);
        });
    }

    // ================================================================== wizard: time stop

    private static final int TIME = 0xB0C8FF;

    private static void timeStop(P p) {
        Vec3 o = p.pos();
        float r = Math.max(3, p.a());
        int life = Math.max(20, (int) p.b());
        FxAnims.shake(o, 0.6F, 6, 16);
        // the clock face: it turns fast, then stops dead
        FxParticle face = ground(S.CIRCLE_CLOCK, o, r, TIME, life + 6).color(0x80A0FF).size(r * 0.2F, r).easeOut().alpha(0.75F, 0.75F).spin(0.4F).spawn();
        FxParticle ring = ground(S.RING, o, r * 1.03F, 0x6080FF, life + 6).alpha(0.6F, 0.6F).spawn();
        Fx.p(S.SMOKE, o.add(0, 1, 0)).color(0x4060C0).size(r * 1.4F).alpha(0.12F, 0.12F).life(life).spawn();
        FxParticle[] hands = new FxParticle[2];
        for (int i = 0; i < 2; i++) {
            hands[i] = Fx.p(S.BEAM, o.add(0, 0.1, 0)).flat().color(0x90B0FF).size(i == 0 ? r * 0.75F : r * 0.5F).alpha(0.9F, 0.9F).life(life + 6).spawn();
        }
        FxAnims.run(life + 6, age -> {
            float spinSpeed = age < 10 ? 0.4F * (1 - age / 10F) : 0;
            if (face != null) face.spinNow(spinSpeed);
            for (int i = 0; i < 2; i++) {
                if (hands[i] == null) continue;
                double a = (i == 0 ? 1 : 12) * (age < 10 ? age * 0.3 : 3.0) + (age >= 10 && age % 20 == 0 ? 0.52 * (age / 20) : 0);
                Vec3 d = new Vec3(Math.cos(a), 0, Math.sin(a));
                Vec3 mid = o.add(d.scale((i == 0 ? r * 0.75 : r * 0.5) * 0.5)).add(0, 0.1, 0);
                hands[i].moveTo(mid.x, mid.y, mid.z);
                hands[i].orientNow(Fx.basis(new Vec3(0, 1, 0).cross(d), d));
            }
            if (age > life) {
                float k = (life + 6 - age) / 6F;
                if (face != null) face.alphaNow(k);
                if (ring != null) ring.alphaNow(k);
                for (FxParticle h : hands) if (h != null) h.alphaNow(k);
            }
            // the air itself hangs still: motes that do not move
            if (age % 2 == 0) {
                double a = Fx.r(0, Math.PI * 2), d = Math.sqrt(Fx.RND.nextDouble()) * r;
                Fx.p(S.TWINKLE, o.add(Math.cos(a) * d, Fx.r(0.2, 2.5), Math.sin(a) * d)).color(0xFFFFFF, TIME).size(0.25F).alpha(0.9F, 0).life(Math.max(4, life - age)).spawn();
            }
            return false;
        });
        for (Vec3 v : p.pts()) {
            Fx.p(S.CIRCLE_CLOCK, v).color(0xFFFFFF, TIME).size(0.3F, 1.3F).easeOut().alpha(0.9F, 0.6F).life(life).spawn();
        }
    }

    private static void timeRelease(P p) {
        Vec3 at = p.pos();
        flash(at, TIME, 1.8F, 5);
        burst(at, 20, 0.5, S.SHARD, 0xFFFFFF, TIME, 0.35F, 14, 0.4F);
        Fx.p(S.RING, at).color(0xFFFFFF, TIME).size(0.3F, 2.2F).easeOut().alpha(1, 0).life(8).spawn();
    }

    // ================================================================== healer: guardian light (rewind)

    private static final int DAWN = 0xFFE6A0;

    private static void guardianLight(P p) {
        Entity e = p.e();
        Vec3 o = feetOf(p);
        float r = Math.max(3, p.a());
        int ticks = Math.max(20, (int) (p.b() * 20));
        // a golden clock face turning backwards
        ground(S.CIRCLE_CLOCK, o, r, 0xFFB030, 50).color(0xFFC850, 0xFF9020).size(r * 0.3F, r).easeOut().alpha(0.7F, 0).spin(-0.12F).spawn();
        ground(S.CIRCLE_HOLY, o, r * 0.6F, 0xFFB030, 50).color(0xFFC850, 0xFF9020).alpha(0.6F, 0).spin(0.08F).delay(4).spawn();
        // light spirals up the wrong way round
        FxAnims.run(30, age -> {
            for (int k = 0; k < 3; k++) {
                double a = -age * 0.4 + k * Math.PI * 2 / 3;
                double h = age * 0.1;
                double rr = r * 0.5 * (1 - age / 40.0);
                Fx.p(S.GLOW, o.add(Math.cos(a) * rr, h, Math.sin(a) * rr)).color(0xFFFFFF, DAWN).size(0.4F, 0).life(14).spawn();
            }
            return false;
        });
        for (Vec3 v : p.pts()) healAt(v.subtract(0, 0.9, 0), null, DAWN, 6);
        FxAnims.follow(e, ticks, age -> {
            if (age % 10 != 0) return false;
            double a = -age * 0.2;
            Fx.p(S.FEATHER, e.position().add(Math.cos(a) * 0.6, 2.2, Math.sin(a) * 0.6)).vel(0, -0.03, 0).color(0xFFFFFF, DAWN).size(0.25F, 0.15F)
                    .randomAngle().spin(0.05F).life(30).spawn();
            return false;
        });
    }

    /** A blow being turned back into health: golden motes winding back into the body. */
    private static void rewind(P p) {
        Entity e = p.e();
        Vec3 o = feetOf(p);
        for (int i = 0; i < 6; i++) {
            double a = -i * 1.05;
            Vec3 at = o.add(Math.cos(a) * 0.9, 0.4 + i * 0.25, Math.sin(a) * 0.9);
            Fx.p(S.GLOW, at).vel(o.add(0, 1.0, 0).subtract(at).scale(0.12)).color(0xFFFFFF, DAWN).size(0.3F, 0.05F).life(8).delay(i).follow(e).spawn();
        }
    }

    // ================================================================== assassin: shadow dance

    private static final int SHADE = 0x8A5AD8;

    private static void shadowDance(P p) {
        Entity e = p.e();
        Vec3 o = feetOf(p);
        int ticks = Math.max(20, (int) (p.a() * 20));
        Vec3 face = e == null ? new Vec3(0, 0, 1) : Vec3.directionFromRotation(0, e.getYRot());
        Vec3 side = Fx.right(face);
        // ink spreading on the ground, two shapes rising out of it
        ground(S.SMOKE, o, 3.5F, 0x1A0A2A, 50).alphaBlend().lit().alpha(0.8F, 0).randomAngle().spawn();
        ground(S.CIRCLE_HEX, o, 2.6F, SHADE, 40).color(light(SHADE), SHADE).alpha(0.8F, 0).spin(-0.08F).spawn();
        for (int s = -1; s <= 1; s += 2) {
            Vec3 at = o.add(side.scale(1.3 * s)).add(face.scale(-0.4));
            for (int i = 0, n = Fx.n(20); i < n; i++) {
                double h = Fx.r(0, 1.9), w = h > 1.45 ? 0.22 : h > 0.75 ? 0.36 : 0.2;
                Fx.p(S.GLOW, at.add(side.scale(Fx.r(-w, w))).add(0, h * 0.2, 0)).vel(0, h * 0.04, 0).color(light(SHADE), 0x2A1050).size(0.35F, 0.15F)
                        .alpha(0.9F, 0).life(20).delay((int) (h * 4)).spawn();
            }
            smoke(at.add(0, 0.8, 0), 0x2A1840, 0.6F, 1.6F, 30).alpha(0.7F, 0).spawn();
        }
        FxAnims.follow(e, ticks, age -> {
            if (age % 4 != 0) return false;
            // afterimages trailing off the body
            Fx.p(S.GLOW, e.position().add(Fx.r(-0.3, 0.3), Fx.r(0.2, 1.7), Fx.r(-0.3, 0.3))).color(SHADE, 0x200830).size(0.35F, 0.1F).alpha(0.6F, 0).life(14).spawn();
            return false;
        });
    }

    /** One shadow's cut on a target: a dark crescent from the side it stands on. */
    private static void shadowStrike(P p) {
        Vec3 at = p.pos();
        Vec3 from = p.vec();
        Vec3 d = at.subtract(from).normalize();
        Vec3 side = Fx.right(flatDir(d));
        Fx.p(S.SLASH, at).fixed(Fx.basis(d, new Vec3(0, 1, 0).add(side.scale(0.5)))).color(light(SHADE), SHADE).size(1.3F, 1.6F).alpha(1, 0).life(6).spawn();
        line(from.add(0, 1, 0), at, 0.3, (v, t) -> Fx.p(S.GLOW, v).color(SHADE, 0x200830).size(0.3F, 0).alpha(0.7F, 0).life(6).delay((int) (t * 3)).spawn());
        Fx.p(S.SPARK, at).color(0xFFFFFF, SHADE).size(0.8F, 0).randomAngle().life(5).spawn();
    }

    // ================================================================== pirate: broadside, jolly roger

    private static final int SEA = 0xFF6030;

    private static void broadside(P p) {
        Vec3 o = p.pos();
        Vec3 dir = flatDir(p.vec());
        Vec3 side = Fx.right(dir);
        float r = Math.max(3, p.a());
        int rows = 8;
        // the bombardment lines light up row by row, front to back
        for (int row = 0; row < rows; row++) {
            Vec3 line = o.add(dir.scale(-r + row * (2 * r / (rows - 1))));
            for (int k = -1; k <= 1; k++) {
                Vec3 at = line.add(side.scale(k * 2.4));
                ground(S.CIRCLE_HEX, at, 1.2F, SEA, 18).color(0xFFFFFF, SEA).alpha(0.9F, 0).spin(0.15F).delay(row * 4).spawn();
                ground(S.CROSS, at, 0.8F, SEA, 18).alpha(0.9F, 0).angle(0.785F).delay(row * 4).spawn();
            }
        }
        // the guns flashing in the sky behind
        Vec3 behind = o.subtract(dir.scale(r + 6)).add(0, 9, 0);
        for (int i = 0; i < rows; i++) {
            for (int k = -1; k <= 1; k++) {
                Vec3 g = behind.add(side.scale(k * 3));
                Fx.p(S.FLARE, g).color(0xFFE0A0, SEA).size(1.6F, 0.4F).alpha(0.9F, 0).life(5).delay(i * 4).spawn();
                smoke(g, 0x50443A, 0.8F, 2.0F, 30).vel(dir.scale(0.05)).alpha(0.5F, 0).delay(i * 4).spawn();
            }
        }
    }

    private static final int JOLLY = 0x203040;

    private static void jollyRoger(P p) {
        Vec3 at = p.pos();
        float r = Math.max(3, p.a());
        Fx.p(S.SKULL, at.add(0, 3.4, 0)).size(0.15F, 0.7F).easeOut().alpha(1, 0).life(40).spawn();
        Fx.p(S.FLARE, at.add(0, 3.6, 0)).color(0xFF3020).size(2.6F, 0.5F).alpha(0.5F, 0).life(14).spawn();
        ground(S.SMOKE, at, r * 0.8F, 0x101820, 60).alphaBlend().lit().alpha(0.6F, 0).randomAngle().spawn();
        for (int i = 0, n = Fx.n(30); i < n; i++) {
            double a = Fx.r(0, Math.PI * 2);
            Vec3 v = new Vec3(Math.cos(a), 0.1, Math.sin(a)).scale(Fx.r(0.3, 0.6));
            smoke(at.add(0, 0.4, 0), 0x182030, 0.5F, 1.4F, 26).vel(v).alpha(0.6F, 0).spawn();
        }
        ground(S.WAVE, at, 1, 0xFF4030, 14).size(1, r * 1.4F).easeOut().color(0xFF8060, 0x802010).alpha(0.9F, 0).spawn();
    }

    /** The flag's beat: a dark ripple and a little skull over the allies it reaches. */
    private static void jollyPulse(P p) {
        Vec3 at = p.pos();
        float r = Math.max(3, p.a());
        ground(S.RING, at, 1, JOLLY, 12).size(1, r * 2).easeOut().color(0xFF5040, 0x301010).alpha(0.5F, 0).spawn();
        for (Vec3 v : p.pts()) Fx.p(S.GLOW, v.add(0, 1.2, 0)).vel(0, 0.05, 0).color(0xFF5040).size(0.3F, 0).life(12).spawn();
    }

    // ================================================================== lancer: dragon fang, spear field

    private static final int FANG = 0xFF9030;

    private static void dragonFang(P p) {
        Entity e = p.e();
        int ticks = Math.max(10, (int) (p.b() * 20));
        float r = Math.max(2, p.a());
        FxAnims.follow(e, ticks, age -> {
            Vec3 c = e.position().add(0, 1.0, 0);
            Vec3 face = Vec3.directionFromRotation(0, e.getYRot());
            // the spear's circle: two fangs of fire chasing each other round
            for (int k = 0; k < 2; k++) {
                double a = age * 0.9 + k * Math.PI;
                Vec3 d = new Vec3(Math.cos(a), 0, Math.sin(a));
                Vec3 at = c.add(d.scale(r * 0.7));
                Fx.p(S.FANG, at).fixed(Fx.basis(d.cross(new Vec3(0, 1, 0)), d)).color(0xFFFFFF, FANG).size(1.1F).alpha(1, 0).life(4).spawn();
                Fx.p(S.GLOW, at).color(FANG, 0x802000).size(0.6F, 0).life(8).spawn();
            }
            if (age % 2 == 0) {
                Fx.p(S.SLASH, c).fixed(Fx.flat(age * 0.9)).color(light(FANG), FANG).size(r * 0.9F).alpha(0.7F, 0).life(4).spawn();
                debris(e.position(), 3, 0.6, 0.3);
                smoke(e.position().subtract(face.scale(0.8)).add(0, 0.2, 0), 0x806040, 0.5F, 1.4F, 18).alpha(0.5F, 0).spawn();
            }
            return false;
        });
    }

    private static void dragonFangEnd(P p) {
        Vec3 at = p.pos();
        Vec3 dir = flatDir(p.vec());
        FxAnims.shake(at, 1.4F, 10, 18);
        flash(at.add(0, 1, 0), FANG, 3, 7);
        // a fountain of spear points thrown upwards
        for (int i = 0; i < 9; i++) {
            Vec3 g = at.add(dir.scale(Fx.r(0, 2.5))).add(Fx.right(dir).scale(Fx.r(-1.5, 1.5)));
            Fx.p(S.BLADE, g).upright().vel(0, Fx.r(0.4, 0.7), 0).friction(0.85F).color(0xFFFFFF, FANG).size(1.4F, 0.8F).alpha(1, 0).life(14).delay(i).spawn();
            flameAt(g, light(FANG), FANG, 0.9F, 14, null);
        }
        ground(S.CRACK, at.add(dir.scale(1.5)), 2.6F, FANG, 40).color(0xFFE0A0, FANG).alpha(1, 0).angle((float) Math.atan2(dir.z, dir.x)).spawn();
        hits(p.pts(), FANG, 1);
    }

    private static final int LANCE = 0xE0B070;

    private static void spearField(P p) {
        Vec3 o = p.pos();
        float r = Math.max(2, p.a());
        int life = Math.max(20, (int) p.b());
        ground(S.CIRCLE_ARCANE, o, r, LANCE, life).color(0xFFF0C0, LANCE).size(r * 0.4F, r).easeOut().alpha(0.7F, 0.2F).spin(0.02F).spawn();
        // the spears' shadows gathering overhead
        FxAnims.run(life, age -> {
            if (age % 3 != 0) return false;
            double a = Fx.r(0, Math.PI * 2), d = Math.sqrt(Fx.RND.nextDouble()) * r;
            Vec3 sky = o.add(Math.cos(a) * d * 0.5, 10, Math.sin(a) * d * 0.5);
            Fx.p(S.TWINKLE, sky).color(0xFFFFFF, LANCE).size(0.5F, 0).life(8).spawn();
            return false;
        });
    }

    /** Every stuck spear bursts at once (pts: the spears). */
    private static void spearBurst(P p) {
        Vec3 o = p.pos();
        FxAnims.shake(o, 1.6F, 12, 24);
        for (Vec3 v : p.pts()) {
            Vec3 g = v.subtract(0, 0.5, 0);
            Fx.p(S.FLARE, g.add(0, 0.4, 0)).color(0xFFFFFF, LANCE).size(1.6F, 0.5F).alpha(0.9F, 0).life(6).spawn();
            Fx.p(S.BEAM, g.add(0, 1.2, 0)).upright().color(0xFFFFFF, LANCE).size(1.4F, 0.5F).alpha(0.9F, 0).life(8).spawn();
            ground(S.CRACK, g, 1.0F, LANCE, 30).color(0xFFF0C0, LANCE).alpha(0.9F, 0).randomAngle().spawn();
            spray(g.add(0, 0.2, 0), new Vec3(0, 1, 0), 0.6, 6, 0.4, S.SHARD, 0xFFFFFF, LANCE, 0.25F, 12, 0.6F);
        }
    }

    // ================================================================== monk: hundred fists' last blow, diamond body

    private static final int KI = 0xFFB040;

    private static void hundredFinish(P p) {
        Vec3 at = p.pos();
        Vec3 dir = p.vec().normalize();
        FxAnims.shake(at, 1.6F, 10, 16);
        flash(at, KI, 2.8F, 7);
        // one great fist of ki, and the air rings behind it
        Fx.p(S.FLARE, at).color(0xFFFFFF, KI).size(3.4F, 1.0F).alpha(1, 0).life(8).spawn();
        for (int i = 0; i < 4; i++) {
            Vec3 c = at.add(dir.scale(i * 0.9));
            Fx.p(S.RING, c).fixed(Fx.across(dir)).color(0xFFFFFF, KI).size(0.6F + i * 0.4F, 2.6F + i * 0.6F).easeOut().alpha(0.9F, 0).life(9).delay(i).spawn();
        }
        spray(at, dir, 0.4, 30, 1.0, S.STREAK, 0xFFFFFF, KI, 0.4F, 8, 0);
        hits(p.pts(), KI, 1);
    }

    private static final int DIAMOND = 0xFFD27A;

    private static void diamondBody(P p) {
        Entity e = p.e();
        Vec3 o = feetOf(p);
        int ticks = Math.max(20, (int) (p.a() * 20));
        flash(o.add(0, 1, 0), DIAMOND, 2.4F, 8);
        // facets close in around the body, then glint for the whole time
        for (int i = 0; i < 16; i++) {
            double a = Math.PI * 2 * i / 8, h = i < 8 ? 0.55 : 1.35;
            Vec3 at = o.add(Math.cos(a) * 0.75, h, Math.sin(a) * 0.75);
            Vec3 from = o.add(Math.cos(a) * 2.2, h + 0.5, Math.sin(a) * 2.2);
            Fx.p(S.SHARD, from).vel(at.subtract(from).scale(0.2)).friction(0.75F).fixed(Fx.facing(at.subtract(o))).color(0xFFFFFF, DIAMOND).size(0.55F)
                    .alpha(1, 0).life(ticks).follow(e).delay(i / 2).spawn();
        }
        FxAnims.follow(e, ticks, age -> {
            if (age % 5 != 0) return false;
            Fx.p(S.TWINKLE, e.position().add(Fx.r(-0.5, 0.5), Fx.r(0.2, 1.9), Fx.r(-0.5, 0.5))).color(0xFFFFFF, DIAMOND).size(0.4F, 0).life(8).spawn();
            return false;
        });
    }

    // ================================================================== bard: grand concert, finale

    private static final int[] SONGS = {0xFF7050, 0x70FF90, 0x70A0FF, 0x7FE8D8};

    private static void grandConcert(P p) {
        Entity e = p.e();
        Vec3 o = feetOf(p);
        float r = Math.max(4, p.a());
        int ticks = Math.max(20, (int) (p.b() * 20));
        // four rings of notes, one per song, open out from the bard
        for (int k = 0; k < 4; k++) {
            int c = SONGS[k];
            ground(S.RING, o.add(0, 0.05 * k, 0), 1, c, 18).size(1, r * (0.55F + k * 0.15F)).easeOut().color(light(c), c).alpha(0.9F, 0).delay(k * 3).spawn();
            for (int i = 0; i < 8; i++) {
                double a = Math.PI * 2 * i / 8 + k * 0.4;
                Vec3 at = o.add(Math.cos(a) * 1.0, 0.6 + k * 0.4, Math.sin(a) * 1.0);
                Fx.p(S.NOTE, at).vel(Math.cos(a) * 0.15, 0.04, Math.sin(a) * 0.15).friction(0.92F).color(0xFFFFFF, c).size(0.45F, 0.2F).alpha(1, 0).life(26).delay(k * 3).spawn();
            }
        }
        // the four colours wound round the bard while it lasts
        FxAnims.follow(e, ticks, age -> {
            if (age % 2 != 0) return false;
            int k = (age / 2) % 4;
            double a = age * 0.25 + k * Math.PI / 2;
            Fx.p(S.NOTE, e.position().add(Math.cos(a) * 0.9, 0.4 + (age % 40) * 0.04, Math.sin(a) * 0.9)).color(0xFFFFFF, SONGS[k]).size(0.3F, 0.1F).life(12).spawn();
            return false;
        });
    }

    private static void concertPulse(P p) {
        Vec3 o = feetOf(p);
        float r = Math.max(4, p.a());
        int k = Fx.RND.nextInt(4);
        ground(S.WAVE, o, 1, SONGS[k], 12).size(1, r * 2).easeOut().color(light(SONGS[k]), SONGS[(k + 1) % 4]).alpha(0.6F, 0).spawn();
        for (Vec3 v : p.pts()) Fx.p(S.NOTE, v.add(0, 1.2, 0)).vel(0, 0.05, 0).color(0xFFFFFF, SONGS[Fx.RND.nextInt(4)]).size(0.35F, 0).life(14).spawn();
    }

    private static void finale(P p) {
        Vec3 o = p.pos();
        float r = Math.max(4, p.a());
        int points = (int) p.b();
        FxAnims.shake(o, 1.2F + points * 0.04F, 10, 18);
        flash(o.add(0, 1.2, 0), 0xFFE070, 3 + points * 0.1F, 8);
        ground(S.WAVE, o, 1, 0xFFE070, 14).size(1, r * 2).easeOut().color(0xFFFFFF, 0xFFB040).alpha(1, 0).spawn();
        // fireworks of notes: the better the song, the more go up
        int shells = 2 + points / 4;
        for (int i = 0; i < shells; i++) {
            double a = Math.PI * 2 * i / shells;
            Vec3 top = o.add(Math.cos(a) * r * 0.4, 5 + Fx.r(0, 2), Math.sin(a) * r * 0.4);
            int c = SONGS[i % 4];
            int d = i * 3;
            line(o.add(0, 1, 0), top, 0.4, (v, t) -> Fx.p(S.GLOW, v).color(0xFFFFFF, c).size(0.25F, 0).life(6).delay(d + (int) (t * 8)).spawn());
            FxAnims.later(d + 8, () -> {
                Fx.p(S.FLARE, top).color(0xFFFFFF, c).size(2.2F, 0.4F).alpha(0.8F, 0).life(8).spawn();
                for (int j = 0, n = Fx.n(16); j < n; j++) {
                    Fx.p(j % 2 == 0 ? S.NOTE : S.TWINKLE, top).vel(Fx.randomUnit().scale(Fx.r(0.15, 0.3))).gravity(0.15F).friction(0.92F)
                            .color(0xFFFFFF, c).size(0.4F, 0).life(24).spawn();
                }
            });
        }
        hits(p.pts(), 0xFFE070, 1);
    }
}
