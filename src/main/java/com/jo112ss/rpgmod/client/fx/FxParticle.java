package com.jo112ss.rpgmod.client.fx;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.joml.Quaternionf;

/**
 * The one particle class behind every skill effect. Colour, alpha and size are interpolated over the
 * particle's life; it can face the camera, stay upright (streaks), spin like a coin, or keep a fixed
 * orientation (magic circles on the ground, slash arcs). It can also ride along with an entity.
 * Create them through {@link Fx#p}.
 */
public final class FxParticle extends SingleQuadParticle {
    public enum Face { CAMERA, UPRIGHT, FIXED, COIN }

    boolean additive = true;
    boolean fullBright = true;
    int color0 = 0xFFFFFF, color1 = -1;
    float alpha0 = 1, alpha1 = 0;
    float size0 = 0.2F, size1 = -1;
    /** Fraction of the life spent fading in. */
    float fadeIn;
    /** Size curve: 0 = linear, 1 = fast at the start (ease out). */
    float sizeEase;
    Face face = Face.CAMERA;
    Quaternionf fixed;
    float spin;
    float angle;
    float oAngle;
    Entity follow;
    double fx, fy, fz;
    double lastFollowX, lastFollowY, lastFollowZ;
    int delay;
    int fadeAge = -1, fadeLen;
    float fadeFrom;

    FxParticle(ClientLevel level, double x, double y, double z, TextureAtlasSprite sprite) {
        super(level, x, y, z, sprite);
        this.hasPhysics = false;
        this.gravity = 0;
        this.friction = 1;
        this.xd = this.yd = this.zd = 0;
    }

    void gravityStrength(float g) {
        this.gravity = g;
    }

    void frictionValue(float f) {
        this.friction = f;
    }

    void startFollowing(Entity e) {
        this.follow = e;
        this.lastFollowX = e.getX();
        this.lastFollowY = e.getY();
        this.lastFollowZ = e.getZ();
    }

    @Override
    public void tick() {
        if (delay > 0) {
            delay--;
            xo = x;
            yo = y;
            zo = z;
            return;
        }
        oAngle = angle;
        angle += spin;
        super.tick();
        if (follow != null) {
            if (follow.isRemoved()) {
                follow = null;
            } else {
                double dx = follow.getX() - lastFollowX, dy = follow.getY() - lastFollowY, dz = follow.getZ() - lastFollowZ;
                lastFollowX = follow.getX();
                lastFollowY = follow.getY();
                lastFollowZ = follow.getZ();
                if (dx != 0 || dy != 0 || dz != 0) setPos(x + dx, y + dy, z + dz);
            }
        }
    }

    private float t(float partial) {
        return Mth.clamp((age + partial) / Math.max(1, lifetime), 0, 1);
    }

    @Override
    public float getQuadSize(float partial) {
        float t = t(partial);
        if (sizeEase > 0) t = 1 - (1 - t) * (1 - t);
        float s1 = size1 < 0 ? size0 : size1;
        return Mth.lerp(t, size0, s1);
    }

    private float alphaAt(float partial) {
        float t = t(partial);
        float a = Mth.lerp(t, alpha0, alpha1);
        if (fadeIn > 0 && t < fadeIn) a *= t / fadeIn;
        if (fadeAge >= 0) a = fadeFrom * (1 - Mth.clamp((age + partial - fadeAge) / fadeLen, 0, 1));
        return a;
    }

    // ---------------------------------------------------------------- live control (for particles an animation drives)

    /** Fades out over {@code ticks} from whatever it looks like now, then disappears. */
    public void fadeOut(int ticks) {
        if (fadeAge >= 0 || removed) return;
        fadeFrom = alphaAt(0);
        fadeAge = age;
        fadeLen = Math.max(1, ticks);
        lifetime = age + fadeLen;
    }

    public void moveTo(double x, double y, double z) {
        setPos(x, y, z);
    }

    public void sizeNow(float s) {
        size0 = s;
        size1 = s;
    }

    public void colorNow(int c) {
        color0 = c;
        color1 = -1;
    }

    public void alphaNow(float a) {
        if (fadeAge >= 0) return;
        alpha0 = a;
        alpha1 = a;
    }

    public void spriteNow(Fx.S s) {
        setSprite(Fx.sprite(s));
    }

    public void orientNow(Quaternionf q) {
        face = Face.FIXED;
        fixed = q;
    }

    public void spinNow(float s) {
        spin = s;
    }

    public boolean gone() {
        return removed;
    }

    @Override
    public void extract(QuadParticleRenderState state, Camera camera, float partial) {
        if (delay > 0) return;
        float t = t(partial);
        float a = alphaAt(partial);
        int c1 = color1 < 0 ? color0 : color1;
        float r0 = (color0 >> 16 & 255) / 255F, g0 = (color0 >> 8 & 255) / 255F, b0 = (color0 & 255) / 255F;
        float r1 = (c1 >> 16 & 255) / 255F, g1 = (c1 >> 8 & 255) / 255F, b1 = (c1 & 255) / 255F;
        setColor(Mth.lerp(t, r0, r1), Mth.lerp(t, g0, g1), Mth.lerp(t, b0, b1));
        setAlpha(Mth.clamp(a, 0, 1));
        if (alpha < 0.01F) return;
        float rot = Mth.lerp(partial, oAngle, angle);
        Quaternionf q;
        switch (face) {
            case FIXED -> q = new Quaternionf(fixed).rotateZ(rot);
            case UPRIGHT -> q = upright(camera);
            case COIN -> q = upright(camera).rotateY(rot);
            default -> q = new Quaternionf(camera.rotation()).rotateZ(rot);
        }
        float near = nearCamera(camera, q, partial);
        if (near < 1) {
            setAlpha(alpha * near);
            if (alpha < 0.01F) return;
        }
        extractRotatedQuad(state, camera, q, partial);
    }

    /**
     * 0..1: fades particles that would cover the screen because the camera is inside or right in
     * front of them (a flash on yourself, a ring at your own hand, a beam you stand in). Flat effects
     * on the ground are left alone - they are below the eyes.
     */
    private float nearCamera(Camera camera, Quaternionf q, float partial) {
        double px = Mth.lerp(partial, xo, x), py = Mth.lerp(partial, yo, y), pz = Mth.lerp(partial, zo, z);
        net.minecraft.world.phys.Vec3 cam = camera.position();
        double vx = cam.x - px, vy = cam.y - py, vz = cam.z - pz;
        float size = getQuadSize(partial);
        double d;
        switch (face) {
            case UPRIGHT -> {
                // tall beams: only the horizontal distance matters, against the beam's width
                d = Math.sqrt(vx * vx + vz * vz) / Math.max(0.3, size * 0.25);
                if (Math.abs(vy) > size * 1.05) return 1;
            }
            case FIXED -> {
                org.joml.Vector3f n = new org.joml.Vector3f(0, 0, 1).rotate(q);
                if (Math.abs(n.y()) > 0.7F) return 1;
                double dn = vx * n.x() + vy * n.y() + vz * n.z();
                double lx = vx - n.x() * dn, ly = vy - n.y() * dn, lz = vz - n.z() * dn;
                if (lx * lx + ly * ly + lz * lz > size * size * 1.2) return 1;
                d = Math.abs(dn) / Math.max(0.2, size * 0.6);
            }
            default -> d = Math.sqrt(vx * vx + vy * vy + vz * vz) / Math.max(0.2, size * 1.25);
        }
        if (d >= 1) return 1;
        return (float) Math.max(0, d * d);
    }

    private static Quaternionf upright(Camera camera) {
        Quaternionf c = camera.rotation();
        return new Quaternionf(0, c.y(), 0, c.w()).normalize();
    }

    @Override
    public int getLightCoords(float partial) {
        return fullBright ? LightCoordsUtil.FULL_BRIGHT : super.getLightCoords(partial);
    }

    @Override
    protected Layer getLayer() {
        return additive ? FxRender.ADDITIVE : FxRender.ALPHA;
    }
}
