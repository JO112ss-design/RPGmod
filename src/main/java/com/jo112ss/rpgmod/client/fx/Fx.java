package com.jo112ss.rpgmod.client.fx;

import com.jo112ss.rpgmod.RpgMod;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;

/**
 * Client-side particle toolkit for skill effects: sprite lookup, a fluent particle builder, and the
 * small amount of vector maths the choreographies in {@link SkillFx} share.
 */
public final class Fx {
    private Fx() {}

    public static final RandomSource RND = RandomSource.create();

    // ------------------------------------------------------------------ sprites

    public enum S {
        GLOW, FLARE, ORB, SPARK, RING, WAVE, STREAK, SLASH, SHARD, CROSS, PETAL, FEATHER, SMOKE, HEX,
        CIRCLE_ARCANE, CIRCLE_HOLY, CIRCLE_HEX, CIRCLE_CLOCK, CIRCLE_NATURE,
        RUNE0, RUNE1, RUNE2, RUNE3, COIN, CARD, DICE, SKULL;

        final Identifier id = Identifier.fromNamespaceAndPath(RpgMod.MODID, "fx_" + name().toLowerCase());

        /** Pixel-art sprites keep their own colours and use normal blending. */
        boolean pixelArt() {
            return this == COIN || this == CARD || this == DICE || this == SKULL;
        }
    }

    private static final Identifier PARTICLE_ATLAS = Identifier.withDefaultNamespace("particles");
    private static TextureAtlas atlas;
    private static final Map<S, TextureAtlasSprite> SPRITES = new HashMap<>();

    static TextureAtlasSprite sprite(S s) {
        TextureAtlas a = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(PARTICLE_ATLAS);
        if (a != atlas) { // resource reload replaced the atlas
            atlas = a;
            SPRITES.clear();
        }
        return SPRITES.computeIfAbsent(s, k -> a.getSprite(k.id));
    }

    public static S rune(int i) {
        return switch (Math.floorMod(i, 4)) {
            case 0 -> S.RUNE0;
            case 1 -> S.RUNE1;
            case 2 -> S.RUNE2;
            default -> S.RUNE3;
        };
    }

    // ------------------------------------------------------------------ density

    /** 1 at "all" particles, less at "decreased" / "minimal". */
    public static float density() {
        ParticleStatus st = Minecraft.getInstance().options.particles().get();
        return st == ParticleStatus.ALL ? 1F : st == ParticleStatus.DECREASED ? 0.5F : 0.25F;
    }

    /** {@code n} scaled by the particle setting (at least 1 when n > 0). */
    public static int n(int n) {
        return n <= 0 ? 0 : Math.max(1, Math.round(n * density()));
    }

    // ------------------------------------------------------------------ builder

    public static B p(S sprite, double x, double y, double z) {
        return new B(sprite, x, y, z);
    }

    public static B p(S sprite, Vec3 v) {
        return new B(sprite, v.x, v.y, v.z);
    }

    public static final class B {
        private final S sprite;
        private final double x, y, z;
        private double vx, vy, vz;
        private int c0 = 0xFFFFFF, c1 = -1;
        private float a0 = 1, a1 = 0, s0 = 0.2F, s1 = -1, fadeIn, sizeEase, gravity, friction = 1, spin, angle;
        private int life = 20, delay;
        private FxParticle.Face face = FxParticle.Face.CAMERA;
        private Quaternionf fixed;
        private Entity follow;
        private Boolean additive;
        private boolean lit;

        private B(S sprite, double x, double y, double z) {
            this.sprite = sprite;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public B vel(double x, double y, double z) {
            vx = x;
            vy = y;
            vz = z;
            return this;
        }

        public B vel(Vec3 v) {
            return vel(v.x, v.y, v.z);
        }

        public B color(int c) {
            c0 = c;
            return this;
        }

        public B color(int from, int to) {
            c0 = from;
            c1 = to;
            return this;
        }

        public B alpha(float from, float to) {
            a0 = from;
            a1 = to;
            return this;
        }

        public B alpha(float a) {
            return alpha(a, 0);
        }

        public B size(float s) {
            s0 = s;
            s1 = -1;
            return this;
        }

        public B size(float from, float to) {
            s0 = from;
            s1 = to;
            return this;
        }

        /** Size changes quickly at first, then settles (shockwaves). */
        public B easeOut() {
            sizeEase = 1;
            return this;
        }

        public B life(int ticks) {
            life = Math.max(1, ticks);
            return this;
        }

        public B fadeIn(float fraction) {
            fadeIn = fraction;
            return this;
        }

        public B gravity(float g) {
            gravity = g;
            return this;
        }

        public B friction(float f) {
            friction = f;
            return this;
        }

        public B spin(float radPerTick) {
            spin = radPerTick;
            return this;
        }

        public B angle(float rad) {
            angle = rad;
            return this;
        }

        public B randomAngle() {
            angle = RND.nextFloat() * (float) Math.PI * 2;
            return this;
        }

        public B upright() {
            face = FxParticle.Face.UPRIGHT;
            return this;
        }

        public B coin() {
            face = FxParticle.Face.COIN;
            return this;
        }

        public B fixed(Quaternionf q) {
            face = FxParticle.Face.FIXED;
            fixed = q;
            return this;
        }

        /** Lies flat on the ground. */
        public B flat() {
            return fixed(FLAT);
        }

        public B follow(Entity e) {
            follow = e;
            return this;
        }

        public B delay(int ticks) {
            delay = ticks;
            return this;
        }

        /** Normal (not additive) blending, for smoke and dark colours. */
        public B alphaBlend() {
            additive = false;
            return this;
        }

        /** Lit by the world instead of glowing (smoke, debris). */
        public B lit() {
            lit = true;
            return this;
        }

        public FxParticle spawn() {
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null) return null;
            FxParticle p = new FxParticle(level, x, y, z, sprite(sprite));
            p.setParticleSpeed(vx, vy, vz);
            p.color0 = c0;
            p.color1 = c1;
            p.alpha0 = a0;
            p.alpha1 = a1;
            p.size0 = s0;
            p.size1 = s1;
            p.sizeEase = sizeEase;
            p.fadeIn = fadeIn;
            p.setLifetime(life);
            p.gravityStrength(gravity);
            p.frictionValue(friction);
            p.spin = spin;
            p.angle = p.oAngle = angle;
            p.face = face;
            p.fixed = fixed;
            p.delay = delay;
            p.additive = additive != null ? additive : !sprite.pixelArt();
            p.fullBright = !lit;
            if (follow != null) p.startFollowing(follow);
            Minecraft.getInstance().particleEngine.add(p);
            return p;
        }
    }

    // ------------------------------------------------------------------ vanilla particles

    public static void vanilla(ParticleOptions o, double x, double y, double z, double vx, double vy, double vz) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) level.addParticle(o, false, true, x, y, z, vx, vy, vz);
    }

    public static void vanilla(ParticleOptions o, Vec3 at, Vec3 v) {
        vanilla(o, at.x, at.y, at.z, v.x, v.y, v.z);
    }

    // ------------------------------------------------------------------ maths

    /** Quad lying flat (normal up / down). */
    public static final Quaternionf FLAT = basis(new Vec3(1, 0, 0), new Vec3(0, 0, 1));

    /**
     * Orientation that maps the sprite's right (+x) to {@code right} and its top (+y) to {@code up}.
     * The vectors are made orthonormal first.
     */
    public static Quaternionf basis(Vec3 right, Vec3 up) {
        Vec3 r = right.normalize();
        Vec3 n = r.cross(up).normalize();
        Vec3 u = n.cross(r).normalize();
        Matrix3f m = new Matrix3f((float) r.x, (float) r.y, (float) r.z, (float) u.x, (float) u.y, (float) u.z,
                (float) n.x, (float) n.y, (float) n.z);
        return new Quaternionf().setFromNormalized(m);
    }

    /** Flat on the ground, rotated by {@code yaw} radians. */
    public static Quaternionf flat(double yaw) {
        return basis(new Vec3(Math.cos(yaw), 0, Math.sin(yaw)), new Vec3(-Math.sin(yaw), 0, Math.cos(yaw)));
    }

    /** Standing up, facing along {@code dir} (horizontal). */
    public static Quaternionf facing(Vec3 dir) {
        Vec3 d = dir.normalize();
        Vec3 side = new Vec3(0, 1, 0).cross(d);
        if (side.lengthSqr() < 1e-6) side = new Vec3(1, 0, 0);
        return basis(side, new Vec3(0, 1, 0).subtract(d.scale(d.y)));
    }

    /** Facing along {@code dir} (any direction), e.g. a ring the projectile flies through. */
    public static Quaternionf across(Vec3 dir) {
        Vec3 d = dir.normalize();
        Vec3 ref = Math.abs(d.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 right = ref.cross(d).normalize();
        Vec3 up = d.cross(right).normalize();
        return basis(right, up);
    }

    /**
     * Sprite's +y along {@code dir}, turned to face the camera as much as possible (for streaks along
     * a direction: speed lines, beams).
     */
    public static Quaternionf axial(Vec3 at, Vec3 dir) {
        Vec3 d = dir.normalize();
        Vec3 toCam = Minecraft.getInstance().gameRenderer.mainCamera().position().subtract(at);
        Vec3 right = d.cross(toCam);
        if (right.lengthSqr() < 1e-6) right = d.cross(Math.abs(d.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0));
        return basis(right, d);
    }

    /** Horizontal right-hand vector of a horizontal direction. */
    public static Vec3 right(Vec3 dir) {
        return new Vec3(-dir.z, 0, dir.x).normalize();
    }

    public static Vec3 rotY(Vec3 v, double a) {
        double c = Math.cos(a), s = Math.sin(a);
        return new Vec3(v.x * c - v.z * s, v.y, v.x * s + v.z * c);
    }

    public static Vec3 randomUnit() {
        double u = RND.nextDouble() * 2 - 1, th = RND.nextDouble() * Math.PI * 2, r = Math.sqrt(1 - u * u);
        return new Vec3(r * Math.cos(th), u, r * Math.sin(th));
    }

    public static double r(double min, double max) {
        return min + RND.nextDouble() * (max - min);
    }

    public static float rf(float min, float max) {
        return min + RND.nextFloat() * (max - min);
    }

    /** Linear blend of two RGB colours. */
    public static int mix(int a, int b, double t) {
        int r = (int) ((a >> 16 & 255) + ((b >> 16 & 255) - (a >> 16 & 255)) * t);
        int g = (int) ((a >> 8 & 255) + ((b >> 8 & 255) - (a >> 8 & 255)) * t);
        int bl = (int) ((a & 255) + ((b & 255) - (a & 255)) * t);
        return r << 16 | g << 8 | bl;
    }
}
