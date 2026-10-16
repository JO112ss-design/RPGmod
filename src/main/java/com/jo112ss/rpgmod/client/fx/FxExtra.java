package com.jo112ss.rpgmod.client.fx;

import static com.jo112ss.rpgmod.client.fx.SkillFx.*;

import com.jo112ss.rpgmod.client.fx.Fx.S;
import com.jo112ss.rpgmod.client.fx.SkillFx.P;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Effects of the second wave of skills (two new ones per job, the new wizard spells). */
final class FxExtra {
    private FxExtra() {}

    static final int CRIMSON = 0xC02828, HOLY = 0xFFD95A, ARC = 0xA98BFF, ICE = 0x9FE8FF, VOID = 0x7A3CFF;
    /** Coating colours: attack down, armour break, poison. */
    static final int[] COAT = {0xFF6A3C, 0x9FB8FF, 0x7DFF4A};
    /** Card suits: spade, heart, diamond, club. */
    static final int[] SUIT = {0x6FA8FF, 0xFF5A7A, 0xFFD54A, 0x7DFF4A};

    static boolean play(P p) {
        switch (p.fx()) {
            case "intimidate" -> intimidate(p);
            case "taunt" -> taunt(p);
            case "piercing_shot" -> piercingShot(p);
            case "explosive_arrow_boom" -> explosion(p.pos(), Math.max(1.5F, p.a()), 0xFF8A3D, p);
            case "guardian_dome" -> guardianDome(p);
            case "dome_block" -> domeBlock(p);
            case "holy_wave" -> holyWave(p);
            case "heal_burst" -> healAt(feet(p.e(), p.pos()), p.e(), p.colorOr(HOLY), 0);
            case "chrono" -> chrono(p);
            case "quick_cast" -> quickCast(p);
            case "arcane_missile" -> arcaneMissile(p);
            case "blizzard" -> blizzard(p);
            case "gravity" -> gravity(p);
            case "gravity_burst" -> gravityBurst(p);
            case "nourish" -> nourish(p);
            case "purify" -> purify(p);
            case "poison_coat" -> poisonCoat(p);
            case "coat_hit" -> coatHit(p);
            case "knife" -> knife(p);
            case "shadow_bind" -> shadowBind(p);
            case "fire_weapon" -> fireWeapon(p);
            case "fire_weapon_hit" -> fireHit(p);
            case "binding_field" -> bindingField(p);
            case "roulette_click" -> rouletteClick(p);
            case "roulette_bang" -> rouletteBang(p);
            case "card" -> card(p);
            case "card_hit" -> cardHit(p);
            default -> {
                return false;
            }
        }
        return true;
    }

    // ================================================================== fighter

    private static void intimidate(P p) {
        Vec3 chest = p.pos();
        Vec3 dir = flatDir(p.vec());
        float r = Math.max(2, p.a());
        Vec3 front = chest.add(dir.scale(1.6)).add(0, -0.3, 0);
        // a heavy downward cleave (crescent rolled to strike from above) in dark red
        Fx.p(S.SLASH, front).size(r * 0.5F).color(0xFF5040, CRIMSON).alpha(0.95F, 0).angle(-0.3F).spin(0.42F).life(6).spawn();
        Fx.p(S.SLASH, front).size(r * 0.6F).color(CRIMSON, 0x400808).alpha(0.55F, 0).angle(-0.45F).spin(0.4F).life(8).spawn();
        ground(S.WAVE, chest.add(0, -1.05, 0).add(dir.scale(1.5)), 0.5F, CRIMSON, 12).size(0.5F, r * 0.8F).easeOut().alpha(0.8F, 0).spawn();
        for (int i = 0, n = Fx.n(8); i < n; i++) smoke(front.add(Fx.randomUnit().scale(0.8)), 0x300808, 0.5F, 1.3F, 22).vel(dir.scale(0.05)).alpha(0.6F, 0).spawn();
        hits(p.pts(), CRIMSON, 1);
        for (Vec3 t : p.pts()) {
            // the strength being pressed out of them: red chevrons sinking down
            for (int k = 0; k < 3; k++) {
                Fx.p(S.STREAK, t.add(Fx.r(-0.3, 0.3), 1.0, Fx.r(-0.3, 0.3))).upright().vel(0, -0.12, 0).color(0xFF6040, CRIMSON)
                        .size(0.35F, 0.2F).alpha(0.9F, 0).life(12).delay(2 + k * 2).spawn();
            }
            Fx.p(S.RING, t.add(0, 0.6, 0)).flat().size(0.9F, 0.4F).color(CRIMSON).alpha(0.9F, 0).vel(0, -0.06, 0).life(14).delay(2).spawn();
        }
    }

    private static void taunt(P p) {
        Entity e = p.e();
        Vec3 o = feet(e, p.pos());
        float r = Math.max(2, p.a());
        Vec3 c = chest(e, o);
        flash(c, 0xFF4020, 1.4F, 6);
        for (int k = 0; k < 3; k++) {
            ground(S.WAVE, o, 0.5F, 0xFF5030, 14).size(0.5F, r).color(0xFF7040, 0xA01010).alpha(0.8F, 0).easeOut().delay(k * 3).spawn();
        }
        // a war-shield flash on the caster
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2 * i / 8;
            Vec3 n = new Vec3(Math.cos(a), 0, Math.sin(a));
            Fx.p(S.HEX, c.add(n.scale(0.9))).fixed(Fx.across(n)).size(0.35F).color(0xFFB080, 0xFF4020).alpha(0.8F, 0).fadeIn(0.2F).life(18).follow(e).spawn();
        }
        // a red thread from each provoked monster to the caster, and a "!" spark above it
        for (Vec3 t : p.pts()) {
            line(t, c, 0.45, (v, tt) -> Fx.p(S.GLOW, v).color(0xFF3020).size(0.12F, 0).alpha(0.8F, 0).life(8).delay((int) (tt * 5)).spawn());
            Fx.p(S.STREAK, t.add(0, 1.4, 0)).upright().color(0xFFFFFF, 0xFF3020).size(0.35F).alpha(1, 0).life(16).spawn();
            Fx.p(S.GLOW, t.add(0, 1.0, 0)).color(0xFF3020).size(0.15F).alpha(1, 0).life(16).spawn();
        }
    }

    // ================================================================== ranger

    private static void piercingShot(P p) {
        Vec3 from = p.pos();
        Vec3 to = p.vec();
        Vec3 dir = to.subtract(from).normalize();
        Fx.p(S.RING, from.add(dir.scale(0.5))).fixed(Fx.across(dir)).size(0.2F, 1.4F).easeOut().color(0xFFFFFF, 0x8CFF6A).alpha(1, 0).life(7).spawn();
        double len = from.distanceTo(to);
        int n = Math.max(1, (int) Math.ceil(len / 1.2));
        for (int i = 0; i < n; i++) {
            Vec3 a = from.add(dir.scale(len * i / n)), b = from.add(dir.scale(len * (i + 1) / n));
            int d = (int) (len * i / n / 4);
            segment(a, b, 0.6F).color(0xFFFFFF, 0x8CFF6A).alpha(1, 0).life(7).delay(d).spawn();
            segment(a, b, 1.2F).color(0x8CFF6A).alpha(0.35F, 0).life(9).delay(d).spawn();
            if (i % 3 == 0) Fx.p(S.RING, a).fixed(Fx.across(dir)).size(0.3F, 0.9F).color(0xD8FFE0).alpha(0.6F, 0).life(6).delay(d).spawn();
        }
        for (Vec3 t : p.pts()) hits(java.util.List.of(t), 0x8CFF6A, 1 + (int) (t.distanceTo(from) / 4));
    }

    /** Shared explosion (explosive arrow). */
    private static void explosion(Vec3 at, float r, int color, P p) {
        flash(at.add(0, 0.5, 0), color, r * 1.2F, 6);
        ground(S.WAVE, at, 0.5F, color, 12).size(0.5F, r * 1.4F).easeOut().color(0xFFE0A0, color).alpha(1, 0).spawn();
        Fx.vanilla(ParticleTypes.EXPLOSION, at.x, at.y + 0.5, at.z, 0, 0, 0);
        burst(at.add(0, 0.4, 0), 26, 0.6, S.GLOW, 0xFFE0A0, color, 0.2F, 14, 0.5F);
        for (int i = 0, n = Fx.n(10); i < n; i++) {
            smoke(at.add(Fx.randomUnit().scale(0.6)).add(0, 0.5, 0), 0x3A3028, 0.6F, 1.8F, 36).vel(Fx.randomUnit().scale(0.06).add(0, 0.05, 0)).alpha(0.7F, 0).spawn();
        }
        debris(at, 14, 0.8, 0.5);
        hits(p.pts(), color, 1);
        FxAnims.shake(at, 0.5F, 6, 10);
    }

    // ================================================================== paladin

    static final int DOME = 0xE8B048;

    /**
     * A hemisphere of faint gold hex plates that stays for the dome's life. Kept see-through on
     * purpose: you fight from inside it for 8 seconds, so it must not paint over the sky.
     */
    private static void guardianDome(P p) {
        Vec3 o = p.pos();
        float r = Math.max(1.5F, p.a());
        int life = Math.max(20, (int) p.b());
        flash(o.add(0, 1, 0), HOLY, 2.0F, 8);
        // the dome rises from the ground: a bright ring that climbs, then the plates appear row by row
        ground(S.WAVE, o, 0.5F, HOLY, 12).size(0.5F, r * 1.05F).easeOut().color(0xFFFFFF, HOLY).alpha(0.8F, 0).spawn();
        ground(S.RING, o, r * 1.02F, DOME, life).alpha(0.55F, 0.4F).fadeIn(0.05F).spawn();
        for (int k = 1; k <= 6; k++) {
            double y = k / 7.0;
            float rr = (float) (r * Math.sqrt(1 - y * y));
            ground(S.RING, o.add(0, r * y, 0), rr, HOLY, 8).color(0xFFFFFF, HOLY).alpha(0.7F, 0).delay(k).spawn();
        }
        int count = Fx.n(150);
        double golden = Math.PI * (3 - Math.sqrt(5));
        java.util.List<FxParticle> cells = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            double y = (i + 0.5) / count; // upper hemisphere only
            double rr = Math.sqrt(1 - y * y);
            double th = golden * i;
            Vec3 n = new Vec3(Math.cos(th) * rr, y, Math.sin(th) * rr);
            int delay = (int) (y * 8);
            FxParticle cell = Fx.p(S.HEX, o.add(n.scale(r))).fixed(Fx.across(n)).size(0.58F).color(0xFFE6A0, DOME).alpha(0.2F, 0.16F)
                    .fadeIn(0.03F).life(life - delay).delay(delay).spawn();
            if (cell != null) cells.add(cell);
        }
        FxAnims.run(life, age -> {
            // shimmer: a few plates light up, as if something touched the shield
            if (age % 2 == 0) {
                double th = Fx.r(0, Math.PI * 2), y = Fx.r(0.05, 0.95), rr = Math.sqrt(1 - y * y);
                Vec3 nrm = new Vec3(Math.cos(th) * rr, y, Math.sin(th) * rr);
                Fx.p(S.HEX, o.add(nrm.scale(r * 1.01))).fixed(Fx.across(nrm)).size(0.58F).color(0xFFF4D0, DOME).alpha(0.45F, 0).life(12).spawn();
            }
            // a slow band of light sweeping up the dome every two seconds
            if (age % 40 == 10) {
                for (int k = 1; k <= 6; k++) {
                    double y = k / 7.0;
                    ground(S.RING, o.add(0, r * y, 0), (float) (r * Math.sqrt(1 - y * y)), DOME, 8).alpha(0.35F, 0).delay(k * 2).spawn();
                }
            }
            if (age == life - 12) for (FxParticle c : cells) c.fadeOut(12);
            return false;
        });
    }

    private static void domeBlock(P p) {
        Vec3 at = p.pos();
        Vec3 n = p.vec().lengthSqr() < 1e-6 ? new Vec3(0, 1, 0) : p.vec().normalize();
        Fx.p(S.HEX, at).fixed(Fx.across(n)).size(0.5F, 1.2F).easeOut().color(0xFFFFFF, HOLY).alpha(1, 0).life(8).spawn();
        Fx.p(S.SPARK, at).color(0xFFFFFF, HOLY).size(0.9F, 0).randomAngle().life(5).spawn();
        spray(at, n, 0.7, 8, 0.3, S.GLOW, 0xFFFFFF, HOLY, 0.12F, 8, 0.2F);
    }

    private static void holyWave(P p) {
        Vec3 o = p.pos();
        Vec3 dir = flatDir(p.vec());
        Vec3 side = Fx.right(dir);
        float len = Math.max(2, p.a());
        int ticks = (int) Math.ceil(len / 1.2);
        FxAnims.run(ticks + 1, age -> {
            double front = 1 + age * 1.2;
            if (front > len + 0.5) return true;
            Vec3 c = o.add(dir.scale(front));
            for (int i = -3; i <= 3; i++) {
                Vec3 at = c.add(side.scale(i * 0.55));
                Fx.p(S.STREAK, at.add(0, 0.9, 0)).upright().color(0xFFFFFF, HOLY).size(0.9F, 0.5F).alpha(0.8F, 0).life(6).spawn();
                Fx.p(S.GLOW, at.add(0, Fx.r(0.1, 1.6), 0)).vel(dir.scale(0.1).add(0, 0.05, 0)).color(0xFFFFFF, HOLY).size(0.16F, 0).life(12).spawn();
            }
            ground(S.GLOW, c, 1.8F, HOLY, 10).alpha(0.4F, 0).spawn();
            return false;
        });
        Fx.p(S.CIRCLE_HOLY, o.add(dir.scale(0.8)).add(0, 1, 0)).fixed(Fx.facing(dir)).size(0.4F, 1.2F).easeOut().color(0xFFFFFF, HOLY).alpha(0.9F, 0).life(10).spawn();
    }

    // ================================================================== wizard

    private static void chrono(P p) {
        Entity e = p.e();
        Vec3 o = feet(e, p.pos());
        ground(S.CIRCLE_CLOCK, o, 1.6F, 0xFFE6A0, 30).size(0.5F, 1.8F).easeOut().alpha(0.95F, 0).spin(-0.5F).follow(e).spawn();
        ground(S.CIRCLE_CLOCK, o, 0.9F, 0xA0E8FF, 30).alpha(0.8F, 0).spin(0.35F).follow(e).spawn();
        flash(chest(e, o), 0xFFE6A0, 1.3F, 6);
        // sand running backwards: golden grains rising in a spiral
        FxAnims.follow(e, 24, age -> {
            Vec3 f = feet(e, o);
            for (int k = 0; k < 3; k++) {
                double a = -age * 0.5 + k * Math.PI * 2 / 3;
                Fx.p(S.GLOW, f.add(Math.cos(a) * 0.8, 0.1 + age * 0.08, Math.sin(a) * 0.8)).color(0xFFF0C0, 0xE8B33A).size(0.13F, 0).life(14).spawn();
            }
            return false;
        });
    }

    private static void quickCast(P p) {
        Entity e = p.e();
        Vec3 o = feet(e, p.pos());
        int dur = (int) (p.a() * 20);
        ground(S.CIRCLE_ARCANE, o, 1.4F, ARC, 24).alpha(0.9F, 0).spin(0.5F).follow(e).spawn();
        for (int i = 0; i < 6; i++) {
            double a = Math.PI * 2 * i / 6;
            Fx.p(Fx.rune(i), o.add(Math.cos(a) * 1.0, 0.3, Math.sin(a) * 1.0)).vel(0, 0.12, 0).color(0xFFFFFF, ARC).size(0.18F, 0.08F).alpha(1, 0).life(16).follow(e).spawn();
        }
        FxAnims.follow(e, dur, age -> {
            if (age % 4 != 0) return false;
            Vec3 at = chest(e, o).add(Fx.randomUnit().scale(0.6));
            Fx.p(S.STREAK, at).upright().vel(0, 0.15, 0).color(0xFFFFFF, ARC).size(0.3F, 0.1F).alpha(0.7F, 0).life(6).spawn();
            return false;
        });
    }

    private static void arcaneMissile(P p) {
        Fx.p(S.SPARK, p.pos()).color(0xFFFFFF, ARC).size(0.5F, 0).randomAngle().life(4).spawn();
        simProjectile(p.pos(), p.vec(), p.a(), p.e(), 0.3, new ProjDraw() {
            @Override
            public void step(Vec3 at, Vec3 dir, int n) {
                Fx.p(S.GLOW, at).color(0xE0D0FF, ARC).size(0.2F, 0).alpha(0.9F, 0).life(7).spawn();
            }

            @Override
            public void head(Vec3 at, Vec3 dir, int age) {
                Fx.p(S.ORB, at).color(0xFFFFFF, 0xD0C0FF).size(0.28F).alpha(1, 1).life(1).spawn();
                Fx.p(S.GLOW, at).color(ARC).size(0.6F).alpha(0.5F, 0.5F).life(1).spawn();
            }
        });
    }

    private static void blizzard(P p) {
        Vec3 at = p.pos();
        float r = Math.max(2, p.a());
        int life = Math.max(20, (int) p.b());
        ground(S.CIRCLE_ARCANE, at, r, ICE, life).color(0xFFFFFF, ICE).alpha(0.7F, 0.2F).fadeIn(0.05F).spin(0.02F).spawn();
        for (int i = 0; i < 7; i++) {
            smoke(at.add(Fx.r(-r, r) * 0.7, 6 + Fx.r(-0.5, 0.5), Fx.r(-r, r) * 0.7), 0xB8C8D8, 2.2F, 3.4F, life + 10).alpha(0.7F, 0).fadeIn(0.15F).spawn();
        }
        FxAnims.run(life, age -> {
            for (int i = 0, n = Fx.n(7); i < n; i++) {
                double a = Fx.r(0, Math.PI * 2), d = Math.sqrt(Fx.RND.nextDouble()) * r;
                Vec3 top = at.add(Math.cos(a) * d - 1.0, 5.5, Math.sin(a) * d);
                Fx.p(S.GLOW, top).vel(0.18, -0.45, 0.04).color(0xFFFFFF, ICE).size(0.12F).alpha(0.9F, 0.5F).life(12).spawn();
                if (i % 3 == 0) Fx.vanilla(ParticleTypes.SNOWFLAKE, top.x, top.y - 2, top.z, 0.15, -0.3, 0);
            }
            if (age % 3 == 0) {
                double a = Fx.r(0, Math.PI * 2);
                Vec3 v = at.add(Math.cos(a) * r * 0.8, Fx.r(0.3, 2.0), Math.sin(a) * r * 0.8);
                Vec3 tan = new Vec3(-Math.sin(a), 0, Math.cos(a));
                Fx.p(S.STREAK, v).fixed(Fx.axial(v, tan)).vel(tan.scale(0.3)).color(0xFFFFFF, ICE).size(0.6F).alpha(0.5F, 0).life(6).spawn();
            }
            if (age % 10 == 5) {
                ground(S.WAVE, at, 0.5F, ICE, 10).size(0.5F, r).easeOut().alpha(0.5F, 0).spawn();
                for (int i = 0, n = Fx.n(6); i < n; i++) {
                    double a = Fx.r(0, Math.PI * 2), d = Fx.r(0, r);
                    Fx.p(S.SHARD, at.add(Math.cos(a) * d, 0.3, Math.sin(a) * d)).upright().color(0xFFFFFF, ICE).size(0.2F, 0.4F).alpha(0.9F, 0).life(16).spawn();
                }
            }
            return false;
        });
    }

    private static void gravity(P p) {
        Vec3 at = p.pos();
        float r = Math.max(2, p.a());
        int pull = Math.max(4, (int) p.b());
        FxParticle core = smoke(at, 0x0A0414, 0.4F, 0.4F, pull + 4).alpha(0.95F, 0.95F).spin(0.2F).spawn();
        FxParticle rim = Fx.p(S.RING, at).size(0.6F).color(0xD0A0FF, VOID).alpha(0.95F, 0.95F).life(pull + 4).spawn();
        ground(S.CIRCLE_HEX, at.add(0, -0.8, 0), r, VOID, pull + 6).alpha(0.7F, 0.3F).spin(0.15F).spawn();
        FxAnims.run(pull, age -> {
            float t = age / (float) pull;
            if (core != null) core.sizeNow(0.4F + t * 0.9F);
            if (rim != null) rim.sizeNow(0.6F + t * 1.0F);
            for (int i = 0, n = Fx.n(6); i < n; i++) {
                Vec3 from = at.add(Fx.randomUnit().scale(r));
                Vec3 to = at.subtract(from);
                Vec3 swirl = to.cross(new Vec3(0, 1, 0)).normalize().scale(0.12);
                Fx.p(S.GLOW, from).vel(to.scale(0.11).add(swirl)).color(0xE0C0FF, VOID).size(0.14F, 0.05F).alpha(0.5F, 1).life(9).spawn();
            }
            if (age % 4 == 0) ground(S.RING, at.add(0, -0.8, 0), r, VOID, 8).size(r, 0.5F).alpha(0.8F, 0).spawn();
            return false;
        });
        if (core != null) FxAnims.later(pull, () -> core.fadeOut(3));
        if (rim != null) FxAnims.later(pull, () -> rim.fadeOut(3));
    }

    private static void gravityBurst(P p) {
        Vec3 at = p.pos();
        float r = Math.max(2, p.a());
        flash(at, VOID, 2.6F, 6);
        Fx.p(S.RING, at).size(0.3F, r * 1.3F).easeOut().color(0xFFFFFF, VOID).alpha(1, 0).life(9).spawn();
        ground(S.WAVE, at.add(0, -0.8, 0), 0.5F, VOID, 12).size(0.5F, r * 1.3F).easeOut().alpha(0.9F, 0).spawn();
        burst(at, 30, 0.7, S.GLOW, 0xFFFFFF, VOID, 0.18F, 12, 0);
        hits(p.pts(), VOID, 1);
        FxAnims.shake(at, 0.7F, 8, 14);
    }

    // ================================================================== healer

    private static void nourish(P p) {
        Entity e = p.e();
        Vec3 o = feet(e, p.pos());
        java.util.List<Vec3> spots = new java.util.ArrayList<>();
        spots.add(o);
        for (Vec3 a : p.pts()) spots.add(a.add(0, -0.9, 0));
        int k = 0;
        for (Vec3 f : spots) {
            int d = k++ * 3;
            ground(S.CIRCLE_NATURE, f, 1.0F, 0xFFC860, 26).alpha(0.9F, 0).spin(0.06F).delay(d).spawn();
            for (int i = 0, n = Fx.n(12); i < n; i++) {
                double a = Fx.r(0, Math.PI * 2), r = Fx.r(0.2, 0.7);
                Fx.p(S.PETAL, f.add(Math.cos(a) * r, Fx.r(0, 0.3), Math.sin(a) * r)).vel(0, Fx.r(0.05, 0.1), 0)
                        .color(Fx.RND.nextBoolean() ? 0xFFC860 : 0xFFE9A0).size(0.12F).alpha(1, 0).randomAngle().spin(Fx.rf(-0.2F, 0.2F)).life(22).delay(d + Fx.RND.nextInt(6)).spawn();
            }
            Fx.vanilla(ParticleTypes.HAPPY_VILLAGER, f.x, f.y + 1.2, f.z, 0, 0.05, 0);
        }
    }

    private static void purify(P p) {
        Entity e = p.e();
        Vec3 o = feet(e, p.pos());
        float r = Math.max(2, p.a());
        ground(S.WAVE, o, 0.5F, 0xE8FFFF, 16).size(0.5F, r).easeOut().color(0xFFFFFF, 0xA0F0FF).alpha(0.5F, 0).spawn();
        java.util.List<Vec3> spots = new java.util.ArrayList<>();
        spots.add(o);
        for (Vec3 a : p.pts()) spots.add(a.add(0, -0.9, 0));
        for (Vec3 f : spots) {
            // the bad goes out as dark wisps, and light takes its place
            for (int i = 0, n = Fx.n(6); i < n; i++) {
                smoke(f.add(Fx.r(-0.3, 0.3), Fx.r(0.3, 1.5), Fx.r(-0.3, 0.3)), 0x201828, 0.3F, 0.8F, 18).vel(0, 0.08, 0).alpha(0.7F, 0).spawn();
            }
            for (int i = 0, n = Fx.n(10); i < n; i++) {
                Fx.p(S.SPARK, f.add(Fx.r(-0.5, 0.5), Fx.r(0.2, 1.8), Fx.r(-0.5, 0.5))).color(0xFFFFFF, 0xA0F0FF).size(0.3F, 0).randomAngle().life(10).delay(4 + Fx.RND.nextInt(8)).spawn();
            }
            Fx.p(S.STREAK, f.add(0, 1.2, 0)).upright().color(0xFFFFFF, 0xA0F0FF).size(1.3F).alpha(0.8F, 0).life(10).delay(3).spawn();
        }
    }

    // ================================================================== assassin

    private static void poisonCoat(P p) {
        Entity e = p.e();
        Vec3 o = feet(e, p.pos());
        int mode = Math.floorMod((int) p.b(), 3);
        int color = COAT[mode];
        int dur = (int) (p.a() * 20);
        FxAnims.follow(e, 16, age -> {
            Vec3 h = hand(e, o);
            for (int k = 0; k < 2; k++) {
                double a = age * 0.8 + k * Math.PI;
                Fx.p(S.GLOW, h.add(Math.cos(a) * 0.35, Math.sin(a * 0.5) * 0.2, Math.sin(a) * 0.35)).color(0xFFFFFF, color).size(0.12F, 0).life(8).spawn();
            }
            return false;
        });
        flash(hand(e, o), color, 0.7F, 6);
        FxAnims.follow(e, dur, age -> {
            if (age % 6 != 0) return false;
            Vec3 h = hand(e, o);
            Fx.p(S.GLOW, h.add(Fx.randomUnit().scale(0.12))).gravity(0.4F).color(0xFFFFFF, color).size(0.09F, 0.04F).alpha(0.9F, 0).life(12).spawn();
            return false;
        });
    }

    private static void coatHit(P p) {
        Vec3 at = p.pos();
        int mode = Math.floorMod((int) p.b(), 3);
        int color = COAT[mode];
        burst(at, 8, 0.25, S.GLOW, 0xFFFFFF, color, 0.12F, 10, mode == 2 ? -0.1F : 0.2F);
        switch (mode) {
            case 0 -> Fx.p(S.STREAK, at.add(0, 0.5, 0)).upright().vel(0, -0.1, 0).color(0xFFFFFF, color).size(0.35F).alpha(1, 0).life(10).spawn();
            case 1 -> {
                for (int i = 0; i < 4; i++) {
                    Fx.p(S.SHARD, at).vel(Fx.randomUnit().scale(0.15)).gravity(0.4F).color(0xFFFFFF, color).size(0.15F).randomAngle().spin(0.3F).alpha(1, 0).life(14).spawn();
                }
            }
            default -> {
                for (int i = 0; i < 4; i++) smoke(at.add(Fx.randomUnit().scale(0.3)), 0x2A5A10, 0.25F, 0.6F, 14).alpha(0.6F, 0).vel(0, 0.04, 0).spawn();
            }
        }
    }

    private static void knife(P p) {
        int mode = (int) p.b();
        int tint = mode < 0 ? 0xD0D8E0 : COAT[Math.floorMod(mode, 3)];
        simProjectile(p.pos(), p.vec(), p.a(), null, 0, new ProjDraw() {
            @Override
            public void step(Vec3 at, Vec3 dir, int n) {
                if (n % 2 == 0) Fx.p(S.GLOW, at).color(tint).size(0.08F, 0).alpha(0.7F, 0).life(5).spawn();
            }

            @Override
            public void head(Vec3 at, Vec3 dir, int age) {
                Fx.p(S.STREAK, at).fixed(Fx.axial(at, dir)).color(0xFFFFFF).size(0.35F).alpha(1, 1).life(1).spawn();
                Fx.p(S.GLOW, at).color(tint).size(0.3F).alpha(0.5F, 0.5F).life(1).spawn();
            }
        });
    }

    private static void shadowBind(P p) {
        Entity t = p.e();
        if (t == null) return;
        int life = Math.max(10, (int) p.b());
        float w = Math.max(0.5F, p.a());
        ground(S.CIRCLE_HEX, t.position(), w + 0.8F, 0x8E44AD, life).color(0xC080FF, 0x5A1A7A).alpha(0.9F, 0.4F).spin(0.05F).follow(t).spawn();
        for (int i = 0, n = Fx.n(10); i < n; i++) smoke(t.position().add(Fx.randomUnit().multiply(0.8, 0.1, 0.8)), 0x150A1E, 0.5F, 1.0F, 20).alpha(0.8F, 0).spawn();
        FxAnims.follow(t, life, age -> {
            if (age % 3 != 0) return false;
            Vec3 f = t.position();
            for (int k = 0; k < 4; k++) {
                double a = k * Math.PI / 2 + 0.4;
                Vec3 anchor = f.add(Math.cos(a) * (w + 0.7), 0.05, Math.sin(a) * (w + 0.7));
                Vec3 body = f.add(Math.cos(a) * 0.2, t.getBbHeight() * (0.3 + 0.15 * (k % 2)), Math.sin(a) * 0.2);
                segment(anchor, body, 0.08F).color(0xD090FF, 0x6A2A9A).alpha(0.9F, 0.3F).life(4).spawn();
            }
            return false;
        });
    }

    // ================================================================== enchanter

    private static void fireWeapon(P p) {
        Entity e = p.e();
        Vec3 o = feet(e, p.pos());
        ground(S.CIRCLE_ARCANE, o, 1.4F, 0xFF7A2A, 26).color(0xFFD070, 0xFF5020).alpha(0.9F, 0).spin(0.12F).follow(e).spawn();
        burst(chest(e, o), 18, 0.3, S.GLOW, 0xFFD070, 0xFF4010, 0.16F, 14, -0.05F);
        aura(e, 300, 4, 0xFF7A2A, S.GLOW, 0.1F);
        for (Vec3 a : p.pts()) {
            Vec3 f = a.add(0, -0.9, 0);
            ground(S.CIRCLE_ARCANE, f, 1.0F, 0xFF7A2A, 22).color(0xFFD070, 0xFF5020).alpha(0.9F, 0).spin(0.12F).delay(3).spawn();
            burst(a, 12, 0.25, S.GLOW, 0xFFD070, 0xFF4010, 0.14F, 12, -0.05F);
        }
    }

    private static void fireHit(P p) {
        Vec3 at = p.pos();
        burst(at, 10, 0.3, S.GLOW, 0xFFD070, 0xFF4010, 0.15F, 10, -0.1F);
        for (int i = 0; i < 4; i++) Fx.vanilla(ParticleTypes.FLAME, at.x, at.y, at.z, Fx.r(-0.08, 0.08), Fx.r(0.02, 0.1), Fx.r(-0.08, 0.08));
    }

    private static void bindingField(P p) {
        Vec3 at = p.pos();
        float r = Math.max(2, p.a());
        int life = Math.max(10, (int) p.b());
        ground(S.CIRCLE_HEX, at, r, 0xFF7FD1, life).color(0xFFC0E8, 0xC04090).alpha(0.9F, 0.3F).fadeIn(0.05F).spin(-0.03F).size(r * 0.3F, r).easeOut().spawn();
        flash(at.add(0, 0.5, 0), 0xFF7FD1, 1.4F, 6);
        FxAnims.run(life, age -> {
            if (age % 3 != 0) return false;
            double a = Fx.r(0, Math.PI * 2);
            Vec3 base = at.add(Math.cos(a) * r, 0.05, Math.sin(a) * r);
            segment(base, base.add(0, Fx.r(0.8, 1.6), 0), 0.07F).color(0xFFFFFF, 0xFF7FD1).alpha(0.9F, 0).life(8).spawn();
            double b = Fx.r(0, Math.PI * 2), d = Math.sqrt(Fx.RND.nextDouble()) * r;
            Fx.p(Fx.rune(age / 3), at.add(Math.cos(b) * d, 0.2, Math.sin(b) * d)).vel(0, 0.04, 0).color(0xFFFFFF, 0xFF7FD1).size(0.16F, 0.08F).alpha(0.9F, 0).life(18).spawn();
            return false;
        });
    }

    // ================================================================== gambler

    /** The revolver's cylinder at the hand: bright chambers are the ones still to fire. */
    private static void rouletteClick(P p) {
        Vec3 at = p.pos();
        int left = Math.max(0, (int) p.b());
        for (int i = 0; i < 6; i++) {
            double a = Math.PI * 2 * i / 6 + Math.PI / 2;
            Vec3 v = at.add(Math.cos(a) * 0.32, Math.sin(a) * 0.32, 0);
            boolean live = i < left;
            Fx.p(S.RING, v).size(0.09F).color(live ? 0xFFD54A : 0x606060).alpha(live ? 1 : 0.5F, 0).life(18).spawn();
        }
        smoke(at, 0x808080, 0.2F, 0.5F, 12).alpha(0.5F, 0).vel(0, 0.03, 0).spawn();
        Fx.p(S.SPARK, at).color(0xFFFFFF, 0x9A9A9A).size(0.3F, 0).randomAngle().life(4).spawn();
    }

    private static void rouletteBang(P p) {
        Vec3 from = p.pos();
        Vec3 to = p.vec();
        Vec3 dir = to.subtract(from).normalize();
        flash(from.add(dir.scale(0.4)), 0xFFB040, 1.6F, 4);
        Fx.p(S.SPARK, from.add(dir.scale(0.4))).color(0xFFFFFF, 0xFFB040).size(1.4F, 0).randomAngle().life(4).spawn();
        segment(from, to, 0.1F).color(0xFFFFFF, 0xFFD54A).alpha(1, 0).life(4).spawn();
        for (int i = 0, n = Fx.n(6); i < n; i++) smoke(from.add(dir.scale(0.5)), 0x6A6A6A, 0.3F, 1.0F, 18).vel(dir.scale(0.05).add(Fx.randomUnit().scale(0.02))).alpha(0.6F, 0).spawn();
        if (p.b() > 0) {
            flash(to, 0xFF5030, 2.2F, 6);
            Fx.p(S.RING, to).size(0.3F, 2.2F).easeOut().color(0xFFFFFF, 0xFFD54A).alpha(1, 0).life(8).spawn();
            burst(to, 26, 0.6, S.SPARK, 0xFFFFFF, 0xFFB040, 0.3F, 10, 0.2F);
            for (int i = 0; i < Fx.n(8); i++) {
                Fx.p(S.COIN, to).vel(Fx.randomUnit().scale(0.25).add(0, 0.25, 0)).gravity(0.8F).size(0.12F).alpha(1, 0.4F).coin().spin(0.6F).life(24).spawn();
            }
            FxAnims.shake(to, 0.8F, 8, 16);
        }
    }

    private static void card(P p) {
        int suit = Math.floorMod((int) p.b(), 4);
        int color = SUIT[suit];
        final FxParticle[] sprite = {null};
        final int[] seen = {0};
        simProjectile(p.pos(), p.vec(), p.a(), null, 0, new ProjDraw() {
            @Override
            public void step(Vec3 at, Vec3 dir, int n) {
                if (n % 2 == 0) Fx.p(S.GLOW, at).color(0xFFFFFF, color).size(0.12F, 0).alpha(0.8F, 0).life(6).spawn();
            }

            @Override
            public void head(Vec3 at, Vec3 dir, int age) {
                if (sprite[0] == null) sprite[0] = Fx.p(S.CARD, at).size(0.2F).alpha(1, 1).coin().spin(0.9F).life(80).spawn();
                if (sprite[0] != null) sprite[0].moveTo(at.x, at.y, at.z);
                seen[0] = age;
            }
        });
        // the card disappears as soon as the flight ends (hit or out of range)
        FxAnims.run(80, age -> {
            if (age > seen[0] + 1 && sprite[0] != null) {
                sprite[0].fadeOut(2);
                return true;
            }
            return false;
        });
    }

    private static void cardHit(P p) {
        Vec3 at = p.pos();
        int suit = Math.floorMod((int) p.b(), 4);
        int color = SUIT[suit];
        flash(at, color, 1.0F, 5);
        Fx.p(S.SPARK, at).color(0xFFFFFF, color).size(1.0F, 0).randomAngle().life(6).spawn();
        Fx.p(S.CARD, at).vel(0, 0.08, 0).size(0.18F, 0.24F).alpha(1, 0).coin().spin(0.6F).life(16).spawn();
        switch (suit) {
            case 1 -> {
                for (int i = 0; i < 3; i++) Fx.vanilla(ParticleTypes.HEART, at.x + Fx.r(-0.4, 0.4), at.y + 0.3, at.z + Fx.r(-0.4, 0.4), 0, 0.05, 0);
            }
            case 2 -> burst(at, 12, 0.3, S.SPARK, 0xFFFFFF, color, 0.25F, 10, 0.1F);
            case 3 -> {
                for (int i = 0; i < 4; i++) smoke(at.add(Fx.randomUnit().scale(0.3)), 0x2A5A10, 0.25F, 0.6F, 14).alpha(0.6F, 0).vel(0, 0.04, 0).spawn();
            }
            default -> burst(at, 14, 0.4, S.GLOW, 0xFFFFFF, color, 0.15F, 10, 0.2F);
        }
    }
}
