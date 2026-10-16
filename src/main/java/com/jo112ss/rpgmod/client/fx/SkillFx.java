package com.jo112ss.rpgmod.client.fx;

import com.jo112ss.rpgmod.client.fx.Fx.S;
import com.jo112ss.rpgmod.network.FxPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Plays skill effects sent by the server ({@link FxPayload}). The choreographies live in
 * {@link FxMartial} (fighter, ranger, paladin, assassin), {@link FxArcane} (wizard, healer,
 * enchanter) and {@link FxMisc} (gambler and shared effects); this class dispatches and holds the
 * building blocks they share.
 */
public final class SkillFx {
    private SkillFx() {}

    /** The payload plus the entity it refers to (may be null if it is not loaded here). */
    public record P(String fx, Vec3 pos, Vec3 vec, float a, float b, int color, Entity e, List<Vec3> pts) {
        int colorOr(int def) {
            return color != 0 ? color : def;
        }
    }

    public static void play(FxPayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        if (payload.fx().equals("dmg")) {
            // damage numbers are HUD, not particles: they ignore the particle setting
            com.jo112ss.rpgmod.client.mob.MobOverlay.addNumber(payload.pos(), payload.a(), (int) payload.b(), payload.entity());
            return;
        }
        Entity e = payload.entity() >= 0 ? level.getEntity(payload.entity()) : null;
        P p = new P(payload.fx(), payload.pos(), payload.vec(), payload.a(), payload.b(), payload.color(), e, payload.points());
        try {
            if (!FxMartial.play(p) && !FxArcane.play(p) && !FxExtra.play(p)) FxMisc.play(p);
        } catch (RuntimeException ex) {
            com.jo112ss.rpgmod.RpgMod.LOGGER.error("Skill effect {} failed", p.fx(), ex);
        }
    }

    // ================================================================== entity anchors

    static Vec3 feet(Entity e, Vec3 fallback) {
        return e != null ? e.position() : fallback;
    }

    static Vec3 chest(Entity e, Vec3 fallback) {
        return e != null ? e.position().add(0, e.getBbHeight() * 0.6, 0) : fallback.add(0, 1.1, 0);
    }

    static Vec3 head(Entity e, Vec3 fallback) {
        return e != null ? e.position().add(0, e.getBbHeight() + 0.1, 0) : fallback.add(0, 2.0, 0);
    }

    /** Roughly where a held weapon's tip is. */
    static Vec3 hand(Entity e, Vec3 fallback) {
        if (e == null) return fallback;
        Vec3 look = e.getLookAngle();
        Vec3 side = Fx.right(Vec3.directionFromRotation(0, e.getYRot()));
        return e.getEyePosition().add(0, -0.35, 0).add(look.scale(0.7)).add(side.scale(0.3));
    }

    /** Horizontal facing of an entity. */
    static Vec3 facing(Entity e) {
        return e == null ? new Vec3(0, 0, 1) : Vec3.directionFromRotation(0, e.getYRot());
    }

    static Vec3 flatDir(Vec3 v) {
        Vec3 f = new Vec3(v.x, 0, v.z);
        return f.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : f.normalize();
    }

    // ================================================================== building blocks

    /** A flat sprite lying on the ground, a little above it so it is not hidden. */
    static Fx.B ground(S s, Vec3 at, float size, int color, int life) {
        return Fx.p(s, at.x, at.y + 0.06, at.z).flat().size(size).color(color).life(life);
    }

    /** Calls {@code each} for {@code n} points on a horizontal circle. */
    static void circle(Vec3 c, double r, int n, double phase, Consumer<Vec3> each) {
        for (int i = 0; i < n; i++) {
            double a = phase + Math.PI * 2 * i / n;
            each.accept(c.add(Math.cos(a) * r, 0, Math.sin(a) * r));
        }
    }

    /** Points along a segment, spaced about {@code step} apart (fraction 0..1 given too). */
    static void line(Vec3 a, Vec3 b, double step, LineStep each) {
        double len = a.distanceTo(b);
        int n = Math.max(1, (int) Math.ceil(len / step));
        for (int i = 0; i <= n; i++) {
            double t = i / (double) n;
            each.at(a.add(b.subtract(a).scale(t)), t);
        }
    }

    @FunctionalInterface
    interface LineStep {
        void at(Vec3 v, double t);
    }

    /** Particles flying out in all directions. */
    static void burst(Vec3 at, int count, double speed, S s, int c0, int c1, float size, int life, float gravity) {
        for (int i = 0, n = Fx.n(count); i < n; i++) {
            Vec3 v = Fx.randomUnit().scale(speed * Fx.r(0.4, 1.0));
            Fx.p(s, at).vel(v).friction(0.86F).gravity(gravity).color(c0, c1).size(size * Fx.rf(0.7F, 1.2F), 0).life(life + Fx.RND.nextInt(life / 3 + 1)).spawn();
        }
    }

    /** Particles flying out in a cone around {@code dir}. */
    static void spray(Vec3 at, Vec3 dir, double spread, int count, double speed, S s, int c0, int c1, float size, int life, float gravity) {
        Vec3 d = dir.normalize();
        for (int i = 0, n = Fx.n(count); i < n; i++) {
            Vec3 v = d.add(Fx.randomUnit().scale(spread)).normalize().scale(speed * Fx.r(0.5, 1.0));
            Fx.p(s, at).vel(v).friction(0.85F).gravity(gravity).color(c0, c1).size(size * Fx.rf(0.7F, 1.2F), 0).life(life + Fx.RND.nextInt(life / 3 + 1)).spawn();
        }
    }

    /**
     * A bright flash. It is dimmed when the camera is inside it, so a big flash next to you does not
     * wash out the whole screen.
     */
    static void flash(Vec3 at, int color, float size, int life) {
        double cam = Minecraft.getInstance().gameRenderer.mainCamera().position().distanceTo(at);
        float near = (float) Math.min(1, cam / Math.max(0.5, size * 1.3));
        float a = 0.35F + 0.6F * near;
        Fx.p(S.FLARE, at).color(0xFFFFFF, color).size(size, size * 0.5F).alpha(a, 0).life(life).spawn();
        Fx.p(S.FLARE, at).color(color).size(size * 1.5F, size * 1.8F).alpha(0.3F * a, 0).life(life + 2).spawn();
    }

    /** Impact sparks on each point (enemies that were hit). */
    static void hits(List<Vec3> pts, int color, int delay) {
        for (Vec3 v : pts) {
            Fx.p(S.SPARK, v).color(0xFFFFFF, color).size(0.9F, 0.1F).randomAngle().life(6).delay(delay).spawn();
            Fx.p(S.RING, v).color(color).size(0.2F, 1.0F).alpha(0.8F, 0).life(6).delay(delay).spawn();
            for (int i = 0, n = Fx.n(7); i < n; i++) {
                Fx.p(S.GLOW, v).vel(Fx.randomUnit().scale(Fx.r(0.15, 0.35))).friction(0.82F).gravity(0.4F)
                        .color(0xFFFFFF, color).size(0.14F, 0).life(8 + Fx.RND.nextInt(5)).delay(delay).spawn();
            }
        }
    }

    /** Smoke that is lit by the world (dark colours work). */
    static Fx.B smoke(Vec3 at, int color, float s0, float s1, int life) {
        return Fx.p(S.SMOKE, at).alphaBlend().lit().color(color).size(s0, s1).alpha(0.8F, 0).randomAngle()
                .spin(Fx.rf(-0.05F, 0.05F)).life(life).friction(0.9F);
    }

    /** Light coming down onto someone and crosses rising: the shared look of healing. */
    static void healAt(Vec3 feet, Entity follow, int color, int delay) {
        ground(S.CIRCLE_NATURE, feet, 0.9F, color, 26).alpha(0.9F, 0).fadeIn(0.15F).spin(0.06F).follow(follow).delay(delay).spawn();
        for (int i = 0, n = Fx.n(12); i < n; i++) {
            double a = Fx.r(0, Math.PI * 2), r = Fx.r(0.2, 0.6);
            Fx.p(S.GLOW, feet.x + Math.cos(a) * r, feet.y + Fx.r(0, 0.4), feet.z + Math.sin(a) * r).vel(0, Fx.r(0.05, 0.11), 0)
                    .color(0xFFFFFF, color).size(0.18F, 0).life(18 + Fx.RND.nextInt(10)).follow(follow).delay(delay + Fx.RND.nextInt(6)).spawn();
        }
        for (int i = 0; i < 3; i++) {
            double a = Fx.r(0, Math.PI * 2);
            Fx.p(S.CROSS, feet.x + Math.cos(a) * 0.45, feet.y + 0.6 + i * 0.3, feet.z + Math.sin(a) * 0.45).vel(0, 0.04, 0)
                    .color(0xFFFFFF, color).size(0.22F, 0.12F).alpha(1, 0).fadeIn(0.2F).life(24).follow(follow).delay(delay + i * 3).spawn();
        }
        for (int i = 0; i < 5; i++) {
            Fx.p(S.STREAK, feet.x + Fx.r(-0.4, 0.4), feet.y + 3.2, feet.z + Fx.r(-0.4, 0.4)).upright().vel(0, -0.24, 0)
                    .color(0xFFFFFF, color).size(0.45F, 0.3F).alpha(0.8F, 0.2F).life(11).follow(follow).delay(delay + i).spawn();
        }
    }

    /** A jagged lightning path from {@code a} to {@code b} (midpoint displacement). */
    static List<Vec3> bolt(Vec3 a, Vec3 b, double jitter, int depth) {
        List<Vec3> pts = new ArrayList<>();
        pts.add(a);
        boltSplit(pts, a, b, jitter, depth);
        pts.add(b);
        return pts;
    }

    private static void boltSplit(List<Vec3> out, Vec3 a, Vec3 b, double jitter, int depth) {
        if (depth == 0) return;
        Vec3 mid = a.add(b).scale(0.5).add(Fx.randomUnit().scale(jitter));
        boltSplit(out, a, mid, jitter * 0.55, depth - 1);
        out.add(mid);
        boltSplit(out, mid, b, jitter * 0.55, depth - 1);
    }

    /** A line from {@code a} to {@code b} drawn as one stretched sprite turned towards the camera. */
    static Fx.B segment(Vec3 a, Vec3 b, float width) {
        Vec3 mid = a.add(b).scale(0.5);
        Vec3 d = b.subtract(a);
        double len = Math.max(0.05, d.length());
        // the beam sprite's line is ~0.3 of the quad wide, so long segments are split to keep it thin
        return Fx.p(S.BEAM, mid).fixed(Fx.axial(mid, d)).size((float) Math.max(len / 2 * 1.08, width));
    }

    /** Draws a lightning path: a hot core and a coloured halo, as connected line segments. */
    static void drawBolt(List<Vec3> pts, int color, float width, int life, int delay) {
        for (int i = 0; i + 1 < pts.size(); i++) {
            Vec3 a = pts.get(i), b = pts.get(i + 1);
            int n = Math.max(1, (int) Math.ceil(a.distanceTo(b) / (width * 6)));
            for (int k = 0; k < n; k++) {
                Vec3 s0 = a.add(b.subtract(a).scale(k / (double) n));
                Vec3 s1 = a.add(b.subtract(a).scale((k + 1) / (double) n));
                segment(s0, s1, width).color(0xFFFFFF, color).alpha(1, 0).life(life).delay(delay).spawn();
            }
            Fx.p(S.GLOW, a).color(color).size(width * 3.5F, width * 2).alpha(0.3F, 0).life(life + 1).delay(delay).spawn();
        }
    }

    /** The block under a position, for debris (dirt if it is air). */
    static BlockState groundBlock(Vec3 at) {
        ClientLevel level = Minecraft.getInstance().level;
        BlockState s = level == null ? Blocks.DIRT.defaultBlockState() : level.getBlockState(BlockPos.containing(at.x, at.y - 0.2, at.z));
        return s.isAir() ? Blocks.DIRT.defaultBlockState() : s;
    }

    /** Chunks of the ground thrown up. */
    static void debris(Vec3 at, int count, double spread, double up) {
        BlockParticleOption block = new BlockParticleOption(ParticleTypes.BLOCK, groundBlock(at));
        for (int i = 0, n = Fx.n(count); i < n; i++) {
            Fx.vanilla(block, at.x + Fx.r(-spread, spread), at.y + 0.1, at.z + Fx.r(-spread, spread), Fx.r(-0.15, 0.15), up * Fx.r(0.5, 1.0), Fx.r(-0.15, 0.15));
        }
    }

    /** Small persistent sparkles around a buffed entity for the effect's duration. */
    static void aura(Entity e, int ticks, int every, int color, S sprite, float size) {
        FxAnims.follow(e, ticks, age -> {
            if (age % every != 0) return false;
            double a = Fx.r(0, Math.PI * 2), r = e.getBbWidth() * 0.6 + 0.2;
            Fx.p(sprite, e.getX() + Math.cos(a) * r, e.getY() + Fx.r(0.1, e.getBbHeight()), e.getZ() + Math.sin(a) * r)
                    .vel(0, 0.03, 0).color(0xFFFFFF, color).size(size, 0).life(16).follow(e).spawn();
            return false;
        });
    }

    static boolean isLiving(Entity e) {
        return e instanceof LivingEntity le && le.isAlive();
    }

    // ================================================================== projectiles

    /** Callbacks for drawing a simulated projectile. */
    interface ProjDraw {
        /** Every sub-step (three per tick): trail. */
        void step(Vec3 at, Vec3 dir, int n);

        /** Once per tick at the current position: the projectile itself. */
        void head(Vec3 at, Vec3 dir, int age);
    }

    /**
     * Flies a projectile with the same rules as the server ({@code SkillExecutor.projectile}): three
     * steps per tick, optional homing, stops at blocks and at the first non-player living thing.
     */
    static void simProjectile(Vec3 start, Vec3 vel, double range, Entity target, double turnRate, ProjDraw draw) {
        net.minecraft.client.multiplayer.ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        double speed = Math.max(0.1, vel.length());
        final Vec3[] pos = {start};
        final Vec3[] dir = {vel.normalize()};
        final double[] travelled = {0};
        final Entity self = Minecraft.getInstance().player;
        FxAnims.run((int) (range / speed) + 3, age -> {
            if (target != null && target.isAlive() && turnRate > 0) {
                Vec3 want = target.position().add(0, target.getBbHeight() * 0.55, 0).subtract(pos[0]).normalize();
                dir[0] = com.jo112ss.rpgmod.job.SkillExecutor.turn(dir[0], want, turnRate);
            }
            for (int s = 0; s < 3; s++) {
                Vec3 from = pos[0];
                Vec3 to = from.add(dir[0].scale(speed / 3));
                var block = level.clip(new net.minecraft.world.level.ClipContext(from, to, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                        net.minecraft.world.level.ClipContext.Fluid.NONE, self));
                boolean wall = block.getType() != net.minecraft.world.phys.HitResult.Type.MISS;
                Vec3 end = wall ? block.getLocation() : to;
                draw.step(end, dir[0], age * 3 + s);
                if (wall) return true;
                for (Entity x : level.getEntities((Entity) null, new net.minecraft.world.phys.AABB(from, end).inflate(0.6), SkillFx::blocksProjectile)) {
                    if (x.getBoundingBox().inflate(0.3).clip(from, end).isPresent() || x.getBoundingBox().inflate(0.3).contains(end)) return true;
                }
                pos[0] = end;
                travelled[0] += speed / 3;
                if (travelled[0] >= range) return true;
            }
            draw.head(pos[0], dir[0], age);
            return false;
        });
    }

    private static boolean blocksProjectile(Entity x) {
        return x instanceof LivingEntity le && le.isAlive() && !(x instanceof net.minecraft.world.entity.player.Player)
                && !(x instanceof net.minecraft.world.entity.decoration.ArmorStand);
    }
}
