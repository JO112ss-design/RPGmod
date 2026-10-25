package com.jo112ss.rpgmod.client.fx;

import static com.jo112ss.rpgmod.client.fx.SkillFx.*;

import com.jo112ss.rpgmod.client.fx.Fx.S;
import com.jo112ss.rpgmod.client.fx.SkillFx.P;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Effects of the Golem boss: stone dust, molten glow, rocks and spikes. */
final class FxGolem {
    private FxGolem() {}

    static final int EMBER = 0xFF6A30, EMBER_LIGHT = 0xFFC070, WARN = 0xFF3A30, GOLD = 0xFFD54A, STONE = 0x5A5560;

    static boolean play(P p) {
        switch (p.fx()) {
            case "golem_land" -> land(p);
            case "golem_overheat" -> overheat(p);
            case "golem_rip" -> rip(p);
            case "golem_rock_mark" -> rockMark(p);
            case "golem_boulder" -> boulder(p);
            case "golem_rock_hit" -> rockHit(p);
            case "golem_lane" -> lane(p);
            case "golem_spike" -> spike(p);
            case "golem_dust" -> dust(p);
            case "golem_sweep" -> sweep(p);
            case "golem_link" -> link(p);
            case "golem_pillar_rise" -> pillarRise(p);
            case "golem_pillar_break" -> pillarBreak(p);
            case "golem_shield" -> shield(p);
            case "golem_shield_break" -> shieldBreak(p);
            case "golem_spawn" -> spawn(p);
            case "golem_leave" -> leave(p);
            case "golem_death" -> death(p);
            default -> {
                return false;
            }
        }
        return true;
    }

    private static void stoneChunks(Vec3 at, int count, double spread, double up) {
        BlockParticleOption block = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COBBLED_DEEPSLATE.defaultBlockState());
        for (int i = 0, n = Fx.n(count); i < n; i++) {
            Fx.vanilla(block, at.x + Fx.r(-spread, spread), at.y + 0.2, at.z + Fx.r(-spread, spread), Fx.r(-0.2, 0.2), up * Fx.r(0.5, 1.0), Fx.r(-0.2, 0.2));
        }
    }

    /** Fists on the ground: a dust ring and the shockwave rolling out at the server's speed. */
    private static void land(P p) {
        Vec3 o = p.pos();
        float half = Math.max(1, p.a()), reach = Math.max(2, p.b());
        int ticks = (int) (reach / 0.55F) + 1;
        ground(S.WAVE, o, half * 2, EMBER, ticks).size(half * 2.1F, (half + reach) * 2.15F).color(EMBER_LIGHT, EMBER).alpha(1F, 0.2F).spawn();
        ground(S.RING, o, half * 2, 0xFFFFFF, ticks).size(half * 2.0F, (half + reach) * 2.1F).alpha(0.7F, 0).spawn();
        debris(o, 50, half + 1.5, 0.7);
        stoneChunks(o, 30, half + 1, 0.6);
        circle(o.add(0, 0.2, 0), half + 0.5, Fx.n(24), 0, v -> {
            Vec3 out = v.subtract(o).multiply(1, 0, 1).normalize();
            Fx.p(S.SMOKE, v).vel(out.scale(0.25).add(0, 0.08, 0)).friction(0.9F).color(0x8A8290, 0x4A4550).size(1.2F, 2.2F).alpha(0.7F, 0).life(26).spawn();
        });
        flash(o.add(0, 0.8, 0), EMBER, half * 1.2F, 6);
    }

    /** Overheated: steam pouring off, the core flaring and a gold "open" ring at its feet. */
    private static void overheat(P p) {
        Entity e = p.e();
        float w = Math.max(1, p.a());
        int life = Math.max(10, (int) p.b());
        if (e == null) return;
        ground(S.RING, e.position(), w * 1.4F, GOLD, life).alpha(0.75F, 0.3F).follow(e).spawn();
        FxAnims.follow(e, life, age -> {
            Vec3 top = e.position().add(0, e.getBbHeight() * 0.75, 0);
            if (age % 2 == 0) {
                Fx.p(S.SMOKE, top.add(Fx.r(-w / 3, w / 3), 0, Fx.r(-w / 3, w / 3))).vel(Fx.r(-0.05, 0.05), Fx.r(0.12, 0.22), Fx.r(-0.05, 0.05))
                        .color(0xF0E8E0, 0xA09890).size(1.0F, 2.6F).alpha(0.6F, 0).life(24).spawn();
            }
            if (age % 5 == 0) {
                Vec3 core = e.position().add(Vec3.directionFromRotation(0, e.getYRot()).scale(e.getBbWidth() * 0.3)).add(0, e.getBbHeight() * 0.55, 0);
                Fx.p(S.FLARE, core).color(0xFFFFFF, EMBER_LIGHT).size(1.4F, 0.6F).alpha(0.8F, 0).life(8).spawn();
                for (int i = 0; i < 3; i++) {
                    Fx.p(S.GLOW, core).vel(Fx.randomUnit().scale(0.12)).gravity(0.6F).color(EMBER_LIGHT, EMBER).size(0.25F, 0).life(14).spawn();
                }
            }
            if (age % 10 == 0) {
                circle(e.position().add(0, e.getBbHeight() + 0.3, 0), w * 0.3, 4, age * 0.2,
                        v -> Fx.p(S.SPARK, v).color(0xFFFFFF, GOLD).size(0.45F, 0).randomAngle().life(8).spawn());
            }
            return false;
        });
    }

    /** Tearing a rock out of the ground. */
    private static void rip(P p) {
        Vec3 o = p.pos();
        debris(o, 40, 1.2, 0.8);
        stoneChunks(o, 30, 1.0, 0.9);
        ground(S.RING, o, 2, STONE, 12).size(1.5F, 4F).alpha(0.7F, 0).spawn();
    }

    /** Where a rock comes down: a red ring, filling in, and a shadow that grows. */
    private static void rockMark(P p) {
        Vec3 o = p.pos();
        float r = Math.max(1, p.a());
        int life = Math.max(6, (int) p.b());
        int c = p.colorOr(0xFF7A3A);
        ground(S.RING, o, r * 2.2F, c, life).size(r * 3.0F, r * 2.1F).alpha(0.5F, 1F).spawn();
        ground(S.GLOW, o, r * 0.2F, c, life).size(r * 0.3F, r * 2.0F).alpha(0.2F, 0.6F).spawn();
        if (p.color() == 0) { // a falling rock (not a thrown boulder, which draws its own flight)
            int fall = Math.min(12, life);
            FxAnims.run(life, age -> {
                int k = age - (life - fall);
                if (k < 0) return false;
                double h = 16 * (1 - (k + 1) / (double) fall);
                Vec3 at = o.add(0, h + 0.5, 0);
                stoneChunks(at, 3, 0.35, 0.0);
                Fx.p(S.GLOW, at).color(0x3A3640).size(1.1F, 0.8F).alpha(0.9F, 0.4F).life(2).spawn();
                return false;
            });
        }
    }

    /** A thrown boulder: a big dark lump with a trail of chips along an arc. */
    private static void boulder(P p) {
        Vec3 from = p.pos(), to = p.vec();
        int flight = Math.max(4, (int) p.a());
        double h = 4 + from.distanceTo(to) * 0.15;
        FxAnims.run(flight, age -> {
            double s = (age + 1) / (double) flight;
            Vec3 at = from.lerp(to, s).add(0, h * 4 * s * (1 - s), 0);
            Fx.p(S.GLOW, at).color(0x2E2A34).size(1.6F, 1.4F).alpha(1F, 0.6F).life(2).spawn();
            Fx.p(S.GLOW, at).color(EMBER).size(0.7F, 0).alpha(0.5F, 0).life(3).spawn();
            stoneChunks(at, 4, 0.5, 0.0);
            return false;
        });
    }

    private static void rockHit(P p) {
        Vec3 o = p.pos();
        float r = Math.max(1, p.a());
        boolean big = p.b() > 0;
        debris(o, big ? 50 : 24, r * 0.7, big ? 0.8 : 0.5);
        stoneChunks(o, big ? 40 : 18, r * 0.6, big ? 0.9 : 0.6);
        ground(S.WAVE, o, r, STONE, 12).size(r, r * (big ? 4.5F : 3F)).easeOut().alpha(0.9F, 0).spawn();
        for (int i = 0; i < (big ? 10 : 4); i++) {
            Fx.p(S.SMOKE, o.add(Fx.r(-r, r), 0.3, Fx.r(-r, r))).vel(0, 0.06, 0).color(0x8A8290).size(1F, 2F).alpha(0.6F, 0).life(22).spawn();
        }
        if (big) flash(o.add(0, 0.6, 0), EMBER, r, 5);
    }

    /** A lane where spikes will burst up: red edges, filling out from the golem. */
    private static void lane(P p) {
        Vec3 o = p.pos(), d = p.vec();
        float w = Math.max(1, p.a());
        int life = Math.max(6, (int) p.b());
        Vec3 dir = flatDir(d);
        Vec3 side = Fx.right(dir);
        FxAnims.run(life, age -> {
            if (age % 3 != 0) return false;
            double fill = Math.min(1, (age + 3) / (double) life * 1.6);
            line(o.add(0, 0.1, 0), o.add(d.scale(fill)).add(0, 0.1, 0), 0.8, (v, t) -> {
                Fx.p(S.GLOW, v.add(side.scale(w / 2))).color(WARN).size(0.3F, 0.1F).alpha(0.9F, 0).life(5).spawn();
                Fx.p(S.GLOW, v.add(side.scale(-w / 2))).color(WARN).size(0.3F, 0.1F).alpha(0.9F, 0).life(5).spawn();
            });
            return false;
        });
        Vec3 mid = o.add(d.scale(0.5));
        ground(S.GLOW, mid, (float) d.length(), WARN, life).alpha(0.12F, 0.28F).spawn();
    }

    /** A rock spike bursting up out of the ground. */
    private static void spike(P p) {
        Vec3 o = p.pos();
        for (int i = 0; i < 6; i++) {
            Fx.p(S.SHARD, o.add(Fx.r(-0.3, 0.3), 0.2 + i * 0.35, Fx.r(-0.3, 0.3))).vel(0, 0.25, 0).friction(0.8F).gravity(0.6F)
                    .color(0x6A6470, 0x3A3640).size(0.9F - i * 0.1F, 0.3F).randomAngle().life(14).spawn();
        }
        stoneChunks(o, 10, 0.5, 0.9);
        debris(o, 10, 0.5, 0.6);
        Fx.p(S.GLOW, o.add(0, 0.4, 0)).color(EMBER).size(0.9F, 0).alpha(0.6F, 0).life(8).spawn();
    }

    /** Dust thrown up by its feet while it charges. */
    private static void dust(P p) {
        Vec3 o = p.pos();
        float w = Math.max(1, p.a());
        debris(o, 16, w / 2, 0.4);
        for (int i = 0; i < 4; i++) {
            Fx.p(S.SMOKE, o.add(Fx.r(-w / 2, w / 2), 0.2, Fx.r(-w / 2, w / 2))).vel(0, 0.04, 0).color(0x9A9298).size(1.2F, 2.4F).alpha(0.6F, 0).life(22).spawn();
        }
    }

    /** The sweep: a wide arc of dust around it. */
    private static void sweep(P p) {
        Vec3 o = p.pos();
        float r = Math.max(2, p.a());
        circle(o, r * 0.8, Fx.n(28), 0, v -> {
            Vec3 out = v.subtract(o).multiply(1, 0, 1).normalize();
            Fx.p(S.SMOKE, v).vel(out.scale(0.35)).friction(0.88F).color(0xB0A8B0, 0x605860).size(1.0F, 2.0F).alpha(0.7F, 0).life(16).spawn();
        });
        Fx.p(S.SLASH, o).flat().color(0xFFFFFF, EMBER).size(r * 2.1F).alpha(0.8F, 0).life(6).spawn();
        ground(S.RING, o.add(0, -1.1, 0), r, EMBER, 8).size(r * 1.4F, r * 2.3F).alpha(0.8F, 0).spawn();
    }

    /** A beam of embers running from an armour pillar to the golem. */
    private static void link(P p) {
        Vec3 a = p.pos(), b = p.vec();
        line(a, b, 0.9, (v, t) -> Fx.p(S.GLOW, v).vel(b.subtract(v).normalize().scale(0.08)).color(EMBER_LIGHT, EMBER).size(0.22F, 0).alpha(0.7F, 0).life(6).spawn());
    }

    private static void pillarRise(P p) {
        Vec3 o = p.pos();
        debris(o, 30, 0.9, 0.9);
        stoneChunks(o, 24, 0.8, 1.0);
        Fx.p(S.BEAM, o.add(0, 2, 0)).color(EMBER_LIGHT, EMBER).size(1.2F, 0).alpha(0.8F, 0).life(14).spawn();
        ground(S.RING, o, 2, EMBER, 16).size(1F, 4F).easeOut().alpha(1F, 0).spawn();
    }

    private static void pillarBreak(P p) {
        Vec3 o = p.pos();
        float h = Math.max(1, p.a());
        for (int i = 0; i < 4; i++) stoneChunks(o.add(0, h * i / 4.0, 0), 12, 0.5, 0.5);
        burst(o.add(0, h * 0.9, 0), 24, 0.35, S.SHARD, 0xFFE0A0, EMBER, 0.5F, 18, 0.8F);
        flash(o.add(0, h * 0.9, 0), EMBER_LIGHT, 1.6F, 6);
    }

    /** The stone armour rising: a ring of embers climbing up the body. */
    private static void shield(P p) {
        Entity e = p.e();
        float w = Math.max(1, p.a());
        if (e == null) return;
        FxAnims.follow(e, 24, age -> {
            double y = e.getBbHeight() * age / 24.0;
            circle(e.position().add(0, y, 0), w * 0.7, Fx.n(10), age * 0.3, v -> Fx.p(S.GLOW, v).color(EMBER_LIGHT, EMBER).size(0.35F, 0).life(8).spawn());
            return false;
        });
    }

    private static void shieldBreak(P p) {
        Vec3 o = p.pos();
        float w = Math.max(1, p.a());
        burst(o.add(0, w, 0), 60, 0.6, S.SHARD, 0xFFE0A0, EMBER, 0.6F, 22, 0.6F);
        ground(S.WAVE, o, w, EMBER, 18).size(w, w * 5).easeOut().alpha(1, 0).spawn();
        flash(o.add(0, w, 0), EMBER_LIGHT, w * 1.6F, 10);
    }

    /** Arrival: the ground bursts and it climbs out amid dust and embers. */
    private static void spawn(P p) {
        Vec3 o = p.pos();
        float w = Math.max(2, p.a());
        debris(o, 80, w, 1.0);
        stoneChunks(o, 60, w, 1.0);
        for (int i = 0; i < 20; i++) {
            Fx.p(S.SMOKE, o.add(Fx.r(-w, w), 0.3, Fx.r(-w, w))).vel(0, Fx.r(0.05, 0.15), 0).color(0x8A8290).size(1.5F, 3F).alpha(0.7F, 0).life(40).spawn();
            Fx.p(S.GLOW, o.add(Fx.r(-w / 2, w / 2), Fx.r(0, 2), Fx.r(-w / 2, w / 2))).vel(0, Fx.r(0.1, 0.3), 0).color(EMBER_LIGHT, EMBER).size(0.4F, 0).life(30).spawn();
        }
        ground(S.RING, o, w, EMBER, 30).size(w, w * 4).easeOut().alpha(1, 0).spawn();
    }

    /** Dawn: it crumbles into the ground. */
    private static void leave(P p) {
        Entity e = p.e();
        float w = Math.max(1, p.a());
        Vec3 base = p.pos();
        FxAnims.run(Math.max(10, (int) p.b()), age -> {
            Vec3 o = e != null ? new Vec3(e.getX(), base.y, e.getZ()) : base;
            stoneChunks(o.add(0, Fx.r(0, 3), 0), 4, w / 2, 0.2);
            if (age % 6 == 0) Fx.p(S.SMOKE, o.add(Fx.r(-w / 2, w / 2), 0.2, Fx.r(-w / 2, w / 2))).vel(0, 0.05, 0).color(0x8A8290).size(1.5F, 3F).alpha(0.6F, 0).life(30).spawn();
            return false;
        });
    }

    private static void death(P p) {
        Vec3 o = p.pos();
        float w = Math.max(2, p.a()), h = Math.max(2, p.b());
        for (int i = 0; i < 6; i++) stoneChunks(o.add(0, h * i / 6.0, 0), 30, w * 0.5, 0.7);
        burst(o.add(0, h * 0.55, 0), 60, 0.7, S.GLOW, EMBER_LIGHT, EMBER, 0.6F, 26, 0.7F);
        burst(o.add(0, h * 0.7, 0), 30, 0.5, S.SPARK, 0xFFFFFF, GOLD, 0.7F, 20, 0.3F);
        ground(S.WAVE, o, w, EMBER, 20).size(w, w * 5).easeOut().alpha(1, 0).spawn();
        flash(o.add(0, h * 0.55, 0), GOLD, w * 2, 12);
    }
}
