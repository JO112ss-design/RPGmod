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
            case "n_shell" -> shell(p);
            case "n_ghost_ship" -> ghostShip(p);
            case "n_coin_fall" -> coinFall(p);
            case "n_dark_portal" -> darkPortal(p);
            case "n_rush" -> rush(p);
            case "n_blink" -> blink(p);
            case "n_bolt_dash" -> boltDash(p);
            case "n_stun" -> stun(p);
            case "n_thrown" -> thrown(p);
            case "n_stance" -> stance(p);
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

    /**
     * A cannonball arcing from pos to vec in b ticks (a = ball size): a dark ball with a burning
     * fuse spark and a smoke trail, the arc's height growing with the distance.
     */
    private static void shell(P p) {
        Vec3 from = p.pos(), to = p.vec();
        int flight = Math.max(2, (int) p.b());
        float size = p.a() > 0 ? p.a() : 0.4F;
        double h = 2 + from.distanceTo(to) * 0.18;
        FxAnims.run(flight, age -> {
            for (int k = 0; k < 2; k++) { // two positions a tick, so the ball does not jump
                double t = (age + k * 0.5) / flight;
                Vec3 at = from.add(to.subtract(from).scale(t)).add(0, h * 4 * t * (1 - t), 0);
                Fx.p(S.ORB, at).alphaBlend().lit().color(0x26262A).size(size).life(2).spawn();
                Fx.p(S.GLOW, at.add(0, size * 0.6, 0)).color(0xFFFFFF, 0xFFA030).size(0.12F, 0).life(3).spawn();
                if (k == 0) smoke(at, 0x8A8A90, size * 0.6F, size * 1.6F, 16).alpha(0.45F, 0).spawn();
            }
            return false;
        });
    }

    /**
     * A ghost ship hanging in the air at pos for b ticks, its keel along vec (a direction): a
     * glowing pale hull drawn as lines, two masts with misty sails, mist dripping off it. It
     * rises out of nothing, rocks gently and fades away again.
     */
    private static void ghostShip(P p) {
        Vec3 base = p.pos();
        Vec3 u = new Vec3(p.vec().x, 0, p.vec().z).normalize();
        if (u.lengthSqr() < 1e-4) u = new Vec3(1, 0, 0);
        Vec3 w = new Vec3(-u.z, 0, u.x);
        int life = Math.max(20, (int) p.b());
        int hull = p.colorOr(0x7FE8D8), sail = 0x9FE8E0;
        Vec3 fu = u, fw = w;
        FxAnims.run(life, age -> {
            float fade = Math.min(1, Math.min(age / 12F, (life - age) / 12F));
            if (fade <= 0) return false;
            double roll = Math.sin(age * 0.12) * 0.12, bob = Math.sin(age * 0.09) * 0.25;
            Vec3 o = base.add(0, bob, 0);
            // a point on the ship: x along the keel, y up, z across (rolled a little)
            Ship at = (x, y, z) -> o.add(fu.scale(x)).add(fw.scale(z + y * roll)).add(0, y - z * roll, 0);
            float a = 0.85F * fade;
            if (age % 2 == 1) return false; // drawn every other tick, each piece lasting two
            // the hull: rails at a few heights, narrowing to the keel, and a bow that rises
            for (int i = 0; i <= 4; i++) {
                double y = i * 0.45, half = 3.4 + i * 0.3, side = 0.35 + i * 0.3;
                for (int s = -1; s <= 1; s += 2) {
                    shipLine(at.p(-half, y, s * side), at.p(half, y, s * side), 0.12F, i == 4 ? 0xFFFFFF : hull, hull, a * (i == 4 ? 1 : 0.6F));
                    shipLine(at.p(half, y, s * side), at.p(half + 1.4 - i * 0.1, y + 0.3, 0), 0.12F, hull, hull, a * 0.7F);
                    shipLine(at.p(-half, y, s * side), at.p(-half - 0.3, y + 0.2, 0), 0.12F, hull, hull, a * 0.6F);
                }
            }
            for (double x = -3; x <= 3.01; x += 1.5) { // ribs
                shipLine(at.p(x, 0, -0.35), at.p(x, 1.8, -1.55), 0.1F, hull, hull, a * 0.5F);
                shipLine(at.p(x, 0, 0.35), at.p(x, 1.8, 1.55), 0.1F, hull, hull, a * 0.5F);
            }
            // masts and sails (square sails across the keel, bellied forward)
            double[][] masts = {{0.6, 7.6, 2.4}, {-2.4, 6.0, 1.8}};
            for (double[] m : masts) {
                shipLine(at.p(m[0], 1.8, 0), at.p(m[0], m[1], 0), 0.14F, 0xFFFFFF, hull, a);
                shipLine(at.p(m[0], m[1] - 0.6, -m[2]), at.p(m[0], m[1] - 0.6, m[2]), 0.1F, hull, hull, a * 0.8F);
                {
                    for (double y = 3.0; y <= m[1] - 0.8; y += 0.7) {
                        for (double z = -m[2]; z <= m[2] + 0.01; z += 0.7) {
                            double belly = 0.6 * (1 - (z / m[2]) * (z / m[2])) * Math.sin(Math.PI * (y - 3.0) / (m[1] - 3.8));
                            Fx.p(S.GLOW, at.p(m[0] + belly, y, z)).fixed(Fx.across(fu)).color(sail).size(0.45F).alpha(0.07F * fade).life(3).spawn();
                        }
                    }
                }
            }
            // mist dripping off the hull, a lantern at the stern
            if (age % 2 == 0) {
                Vec3 drip = at.p(Fx.r(-3.5, 3.5), 0, Fx.r(-0.4, 0.4));
                Fx.p(S.GLOW, drip).vel(0, -0.04, 0).color(hull).size(0.3F, 0.6F).alpha(0.35F * fade, 0).life(24).spawn();
            }
            Fx.p(S.GLOW, at.p(-3.6, 2.4, 0)).color(0xFFFFFF, 0x9FFFE0).size(0.5F).alpha(0.8F * fade).life(3).spawn();
            return false;
        });
    }

    /** A thin glowing line in short pieces (one long beam sprite would be as wide as it is long). */
    private static void shipLine(Vec3 a, Vec3 b, float width, int c0, int c1, float alpha) {
        int n = Math.max(1, (int) Math.ceil(a.distanceTo(b) / (width * 6)));
        for (int k = 0; k < n; k++) {
            segment(a.add(b.subtract(a).scale(k / (double) n)), a.add(b.subtract(a).scale((k + 1) / (double) n)), width).color(c0, c1).alpha(alpha).life(3).spawn();
        }
    }

    /** A big gold coin tumbling down onto pos in b ticks, glinting, and a splash of gold where it lands. */
    private static void coinFall(P p) {
        Vec3 at = p.pos();
        int fall = Math.max(2, (int) p.b());
        Vec3 sky = new Vec3(Fx.r(-0.8, 0.8), 8, Fx.r(-0.8, 0.8));
        Fx.p(S.COIN, at.add(sky)).vel(sky.scale(-1.0 / fall)).size(0.32F).alpha(1, 1).coin().spin(0.5F).life(fall).spawn();
        FxAnims.run(fall, age -> {
            Vec3 v = at.add(sky.scale(1 - (double) age / fall));
            Fx.p(S.GLOW, v).color(0xFFFFFF, 0xFFC830).size(0.2F, 0).alpha(0.8F, 0).life(6).spawn();
            if (age % 2 == 0) Fx.p(S.SPARK, v.add(Fx.randomUnit().scale(0.25))).color(0xFFFFFF, 0xFFE070).size(0.15F, 0).randomAngle().life(5).spawn();
            return false;
        });
        FxAnims.later(fall, () -> {
            ground(S.RING, at, 0.2F, 0xFFD040, 8).size(0.2F, 1.2F).easeOut().alpha(0.9F, 0).spawn();
            for (int i = 0, n = Fx.n(5); i < n; i++) {
                Fx.p(S.COIN, at.add(0, 0.2, 0)).vel(Fx.randomUnit().scale(Fx.r(0.08, 0.18)).add(0, 0.25, 0)).gravity(0.6F).size(0.12F).alpha(1, 0).coin().spin(0.6F).life(16).spawn();
            }
        });
    }

    /**
     * A dark hole in the air above an entity (followed for b ticks) that the shadow arrows come
     * out of: a turning dark circle with smoke curling at its edge.
     */
    /** How high the shadow rain's portal hangs over the target's head (the server aims the arrows from it). */
    static final double PORTAL_UP = 3.2;

    private static void darkPortal(P p) {
        Entity e = p.e();
        if (e == null) return;
        int life = Math.max(10, (int) p.b());
        int c = p.colorOr(0x6070C0);
        FxAnims.follow(e, life, age -> {
            float fade = Math.min(1, Math.min(age / 6F, (life - age) / 6F));
            Vec3 top = e.position().add(0, e.getBbHeight() + PORTAL_UP, 0);
            // turned to the camera (a flat one would vanish when seen from below)
            Fx.p(S.CIRCLE_HEX, top).size(1.7F).color(c).alpha(0.8F * fade).angle(age * 0.08F).life(2).spawn();
            Fx.p(S.GLOW, top).alphaBlend().color(0x0A0A14).size(1.4F).alpha(0.9F * fade).life(2).spawn();
            if (age % 2 == 0) {
                double a = Fx.r(0, Math.PI * 2);
                Vec3 edge = top.add(Math.cos(a) * 1.4, 0, Math.sin(a) * 1.4);
                smoke(edge, 0x20202C, 0.3F, 0.7F, 14).vel(Math.cos(a + 1.6) * 0.05, -0.02, Math.sin(a + 1.6) * 0.05).alpha(0.6F * fade, 0).spawn();
            }
            return false;
        });
    }

    @FunctionalInterface
    private interface Ship {
        Vec3 p(double x, double y, double z);
    }

    // ------------------------------------------------------------------ dashes

    /**
     * A charge (entity, for b ticks, along vec): wind piling up in a cone before the spear or shield
     * and streaming past, stronger as the charge speeds up (a = top speed tick, 0 = steady), dust kicked up behind.
     */
    private static void rush(P p) {
        Entity e = p.e();
        if (e == null) return;
        int life = Math.max(4, (int) p.b());
        int c = p.colorOr(0xE0D0B0);
        Vec3 dir = flatDir(p.vec());
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        float top = p.a();
        FxAnims.follow(e, life, age -> {
            float k = top > 0 ? Math.min(1, age / top) : 1; // how fast now
            Vec3 tip = e.position().add(0, 1.0, 0).add(dir.scale(1.6 + k * 0.6));
            // the bow wave: a ring of air bent back from the tip
            Fx.p(S.RING, tip).fixed(Fx.across(dir)).color(0xFFFFFF, c).size(0.3F + k * 0.5F, 0.9F + k * 1.2F).alpha(0.25F + k * 0.45F, 0).life(4).spawn();
            for (int i = 0, n = 2 + (int) (k * 5); i < n; i++) {
                double a = Fx.r(0, Math.PI * 2), r = Fx.r(0.4, 0.9 + k * 0.6);
                Vec3 v = tip.add(side.scale(Math.cos(a) * r)).add(0, Math.sin(a) * r * 0.8, 0).add(dir.scale(Fx.r(-1.5, 0)));
                segment(v, v.subtract(dir.scale(1.2 + k * 1.8)), 0.06F).color(0xFFFFFF, c).alpha(0.5F + k * 0.4F, 0).life(3).spawn();
            }
            if (age % 2 == 0) {
                Vec3 f = e.position();
                smoke(f.add(Fx.r(-0.3, 0.3), 0.1, Fx.r(-0.3, 0.3)), 0xB8A88C, 0.3F, 0.9F + k, 18).vel(dir.scale(-0.05).add(0, 0.03, 0)).alpha(0.5F, 0).spawn();
                debris(f, 1 + (int) (k * 2), 0.3, 0.3);
            }
            return false;
        });
    }

    /** A blink from pos to vec: an afterimage left behind, a streak of colour, a burst where it arrives. */
    private static void blink(P p) {
        int c = p.colorOr(0xFFD27A);
        Vec3 a = p.pos(), b = p.vec();
        Vec3 d = flatDir(b.subtract(a));
        Vec3 side = new Vec3(-d.z, 0, d.x);
        // the afterimage: a body of glows fading where you stood
        for (int i = 0, n = Fx.n(26); i < n; i++) {
            double h = Fx.r(0, 1.85);
            double wdt = h > 1.45 ? 0.22 : h > 0.75 ? 0.36 : 0.2;
            Fx.p(S.GLOW, a.add(side.scale(Fx.r(-wdt, wdt))).add(0, h, 0)).color(light(c), c).size(0.3F, 0.1F).alpha(0.7F, 0).life(12 + Fx.RND.nextInt(6)).spawn();
        }
        line(a.add(0, 1, 0), b.add(0, 1, 0), 0.3, (v, t) -> {
            Fx.p(S.GLOW, v.add(Fx.randomUnit().scale(0.15))).color(0xFFFFFF, c).size(0.2F, 0).alpha(0.9F, 0).life(6 + (int) (t * 4)).spawn();
            if (Fx.RND.nextInt(3) == 0) Fx.p(S.STREAK, v).fixed(Fx.axial(v, d)).color(0xFFFFFF, c).size(0.5F, 0.2F).alpha(0.8F, 0).life(5).spawn();
        });
        flash(b.add(0, 1, 0), c, 1.0F, 4);
        ground(S.RING, b, 0.3F, c, 8).size(0.3F, 1.4F).easeOut().alpha(0.8F, 0).spawn();
    }

    /**
     * Thunder thrust: a jagged bolt along the path pos -> vec at once (the player gone into it), the
     * path crackling, then after b ticks lightning crashing down on it every two blocks.
     */
    private static void boltDash(P p) {
        Vec3 a = p.pos().add(0, 1, 0), b = p.vec().add(0, 1, 0);
        int delay = Math.max(2, (int) p.b());
        int c = p.colorOr(0xFFF07A);
        drawBolt(bolt(a, b, 0.6, 4), c, 0.12F, 6, 0);
        drawBolt(bolt(a, b, 0.9, 3), 0xB0D8FF, 0.07F, 5, 1);
        flash(a, c, 1.4F, 4);
        flash(b, c, 1.8F, 5);
        double len = a.distanceTo(b);
        int marks = Math.max(1, (int) (len / 2));
        Vec3 d = b.subtract(a);
        FxAnims.run(delay + 1, age -> {
            for (int i = 0; i <= marks; i++) {
                Vec3 m = p.pos().add(d.scale(i / (double) marks)).add(0, 0.05, 0);
                if (age < delay) {
                    if (Fx.RND.nextInt(3) == 0) {
                        Vec3 s = m.add(Fx.r(-0.4, 0.4), Fx.r(0, 0.4), Fx.r(-0.4, 0.4));
                        drawBolt(bolt(s, s.add(Fx.randomUnit().scale(0.5)), 0.15, 1), c, 0.04F, 2, 0);
                    }
                    if (age % 3 == 0) ground(S.HEX, m, 0.5F + age * 0.05F, c, 3).alpha(0.5F, 0).spawn();
                } else {
                    Vec3 sky = m.add(Fx.r(-0.6, 0.6), 9, Fx.r(-0.6, 0.6));
                    drawBolt(bolt(sky, m, 0.9, 4), 0xFFFFFF, 0.14F, 5, 0);
                    drawBolt(bolt(sky, m, 1.2, 3), c, 0.08F, 6, 0);
                    flash(m.add(0, 0.5, 0), c, 1.6F, 5);
                    ground(S.RING, m, 0.3F, c, 10).size(0.3F, 2.0F).easeOut().alpha(0.9F, 0).spawn();
                    spray(m, new Vec3(0, 1, 0), 1.2, 6, 0.3, S.SPARK, 0xFFFFFF, c, 0.12F, 8, 0.4F);
                }
            }
            if (age == delay) FxAnims.shake(b, 0.25F, 5, 16);
            return false;
        });
    }

    /** Stunned (entity, b ticks): little stars and sparks circling over its head. */
    private static void stun(P p) {
        Entity e = p.e();
        if (e == null) return;
        int life = Math.max(5, (int) p.b());
        FxAnims.follow(e, life, age -> {
            Vec3 top = e.position().add(0, e.getBbHeight() + 0.35, 0);
            double r = Math.max(0.35, e.getBbWidth() * 0.55);
            for (int i = 0; i < 3; i++) {
                double a = age * 0.3 + i * Math.PI * 2 / 3;
                Fx.p(S.CROSS, top.add(Math.cos(a) * r, Math.sin(age * 0.2 + i) * 0.08, Math.sin(a) * r)).color(0xFFFFFF, 0xFFE060).size(0.16F).life(2).spawn();
            }
            return false;
        });
    }

    /** Something thrown hard (entity, b ticks): a streak behind it and dust where it scrapes the ground. */
    private static void thrown(P p) {
        Entity e = p.e();
        if (e == null) return;
        int c = p.colorOr(0xFFD27A);
        int life = Math.max(4, (int) p.b());
        Vec3[] last = {e.position().add(0, e.getBbHeight() * 0.5, 0)};
        FxAnims.follow(e, life, age -> {
            Vec3 now = e.position().add(0, e.getBbHeight() * 0.5, 0);
            if (now.distanceToSqr(last[0]) > 0.01) {
                segment(last[0], now, 0.25F).color(0xFFFFFF, c).alpha(0.7F * (1 - (float) age / life), 0).life(6).spawn();
                for (int i = 0; i < 3; i++) Fx.p(S.GLOW, now.add(Fx.randomUnit().scale(e.getBbWidth() * 0.5))).color(light(c), c).size(0.2F, 0).alpha(0.8F, 0).life(8).spawn();
            }
            if (e.onGround() && age % 2 == 0) {
                smoke(e.position(), 0xA89878, 0.3F, 1.0F, 16).alpha(0.6F, 0).spawn();
                debris(e.position(), 2, 0.3, 0.3);
            }
            last[0] = now;
            return false;
        });
    }

    /** Gathering for a blow (entity, b ticks): ki drawn in from all around, the ground cracking under the feet. */
    private static void stance(P p) {
        Entity e = p.e();
        if (e == null) return;
        int c = p.colorOr(0xFFD27A);
        int life = Math.max(4, (int) p.b());
        FxAnims.follow(e, life, age -> {
            Vec3 core = e.position().add(0, 1.0, 0);
            for (int i = 0, n = Fx.n(4); i < n; i++) {
                Vec3 from = core.add(Fx.randomUnit().scale(Fx.r(1.8, 2.6)));
                Fx.p(S.GLOW, from).vel(core.subtract(from).scale(1 / 7.0)).color(light(c), c).size(0.15F, 0.25F).alpha(0.4F, 1).life(7).spawn();
            }
            if (age % 3 == 0) ground(S.RING, e.position(), 2.2F - age * 0.12F, c, 4).alpha(0.6F, 0).spawn();
            if (age == life - 1) {
                ground(S.HEX, e.position(), 1.6F, 0x806040, 20).alpha(0.9F, 0).alphaBlend().lit().spawn();
                debris(e.position(), 10, 0.8, 0.5);
                flash(core, c, 1.6F, 4);
            }
            return false;
        });
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

    /** A spear falling from the sky onto pos, landing after b ticks (from a blocks up if over 1, else 7). */
    private static void spearFall(P p) {
        int c = p.colorOr(0xE0B070);
        Vec3 at = p.pos();
        int fall = Math.max(2, (int) p.b());
        Vec3 sky = new Vec3(Fx.r(-0.6, 0.6), p.a() > 1 ? p.a() : 7, Fx.r(-0.6, 0.6));
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
