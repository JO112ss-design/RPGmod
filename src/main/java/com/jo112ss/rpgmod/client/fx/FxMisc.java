package com.jo112ss.rpgmod.client.fx;

import static com.jo112ss.rpgmod.client.fx.SkillFx.*;

import com.jo112ss.rpgmod.client.fx.Fx.S;
import com.jo112ss.rpgmod.client.fx.SkillFx.P;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Gambler effects and the shared ones (hits, crits, level up, arrow trails). */
final class FxMisc {
    private FxMisc() {}

    static final int G_GOLD = 0xFFD54A, G_TEAL = 0x3FE8C0;
    /** Same order as the server's outcomes: jackpot, heal, buff, curse, mana, bust. */
    static final int[] OUTCOME = {0xFFD54A, 0x7CFF9A, 0xFF9A3C, 0xB86BFF, 0x6FB8FF, 0x8A8A8A};

    static boolean play(P p) {
        switch (p.fx()) {
            case "hit" -> hits(p.pts(), p.colorOr(0xFFFFFF), 0);
            case "rpg_crit" -> rpgCrit(p);
            case "level_up" -> levelUp(p);
            case "arrow_trail" -> arrowTrail(p);
            case "lucky_shot" -> luckyShot(p);
            case "coin_toss" -> coinToss(p);
            case "dice_roll" -> diceRoll(p);
            case "all_in" -> allIn(p);
            case "jackpot" -> slots(p);
            case "roulette" -> roulette(p);
            case "gamble_jackpot", "gamble_heal", "gamble_buff", "gamble_curse", "gamble_mana", "gamble_bust" -> outcome(p);
            default -> {
                return false;
            }
        }
        return true;
    }

    private static int outcomeIndex(P p) {
        return Math.floorMod((int) p.a(), OUTCOME.length);
    }

    // ================================================================== shared

    private static void rpgCrit(P p) {
        Vec3 at = p.pos();
        int gold = 0xFFC832;
        Fx.p(S.SPARK, at).color(0xFFFFFF, gold).size(1.6F, 0.2F).spin(0.15F).life(10).spawn();
        Fx.p(S.SPARK, at).color(0xFFF0B0, gold).size(1.0F, 0.1F).angle((float) Math.PI / 4).spin(-0.15F).life(8).spawn();
        Fx.p(S.RING, at).color(0xFFF0B0, gold).size(0.3F, 1.8F).easeOut().alpha(1, 0).life(8).spawn();
        Fx.p(S.SLASH, at).color(0xFFFFFF, gold).size(1.1F).alpha(1, 0).randomAngle().life(5).spawn();
        flash(at, gold, 1.2F, 5);
        for (int i = 0, n = Fx.n(16); i < n; i++) {
            Fx.p(S.GLOW, at).vel(Fx.randomUnit().scale(Fx.r(0.2, 0.45))).friction(0.82F).gravity(0.35F).color(0xFFFFFF, gold).size(0.16F, 0).life(12 + Fx.RND.nextInt(6)).spawn();
        }
    }

    private static void levelUp(P p) {
        Entity e = p.e();
        Vec3 o = feet(e, p.pos());
        int color = p.colorOr(G_GOLD);
        ground(S.CIRCLE_HOLY, o, 1.6F, color, 40).size(0.4F, 1.6F).easeOut().color(0xFFFFFF, color).alpha(1, 0).spin(0.08F).follow(e).spawn();
        ground(S.WAVE, o, 0.5F, color, 16).size(0.5F, 3F).easeOut().alpha(0.8F, 0).spawn();
        Fx.p(S.STREAK, o.add(0, 3, 0)).upright().size(3).color(0xFFFFFF, color).alpha(0.9F, 0).life(14).follow(e).spawn();
        FxAnims.follow(e, 30, age -> {
            Vec3 f = feet(e, o);
            for (int k = 0; k < 2; k++) {
                double a = age * 0.55 + k * Math.PI;
                Fx.p(S.GLOW, f.add(Math.cos(a) * 0.75, age * 0.08, Math.sin(a) * 0.75)).color(0xFFFFFF, Fx.mix(color, G_GOLD, 0.5)).size(0.2F, 0).life(16).spawn();
            }
            if (age % 5 == 0) Fx.p(S.SPARK, f.add(Fx.r(-0.6, 0.6), Fx.r(0.3, 2.2), Fx.r(-0.6, 0.6))).color(0xFFFFFF, G_GOLD).size(0.4F, 0).randomAngle().life(8).spawn();
            return false;
        });
    }

    private static void arrowTrail(P p) {
        Entity arrow = p.e();
        if (arrow == null) return;
        int color = p.colorOr(0x8CFF6A);
        final Vec3[] last = {arrow.position()};
        FxAnims.follow(arrow, 80, age -> {
            Vec3 now = arrow.position();
            if (age > 2 && now.distanceToSqr(last[0]) < 0.0004) return true;
            for (int k = 0; k < 3; k++) {
                Vec3 v = last[0].add(now.subtract(last[0]).scale(k / 3.0));
                Fx.p(S.GLOW, v).color(0xFFFFFF, color).size(0.2F, 0).alpha(0.9F, 0).life(8).spawn();
            }
            if (age % 3 == 0) Fx.p(S.SPARK, now).color(0xFFFFFF, color).size(0.3F, 0).randomAngle().life(4).spawn();
            last[0] = now;
            return false;
        });
    }

    // ================================================================== gambler

    private static void luckyShot(P p) {
        Vec3 o = p.pos();
        Vec3 dir = p.vec().normalize();
        boolean lucky = p.b() > 0;
        int color = lucky ? G_GOLD : G_TEAL;
        Fx.p(S.RING, o.add(dir.scale(0.4))).fixed(Fx.across(dir)).size(0.2F, 1.2F).easeOut().color(0xFFFFFF, color).alpha(0.9F, 0).life(7).spawn();
        if (lucky) {
            flash(o, G_GOLD, 1.4F, 6);
            Fx.p(S.SPARK, o).color(0xFFFFFF, G_GOLD).size(1.6F, 0).spin(0.2F).life(9).spawn();
            for (int i = 0; i < Fx.n(6); i++) {
                Fx.p(S.COIN, o).vel(Fx.randomUnit().scale(0.2).add(0, 0.2, 0)).gravity(0.6F).size(0.14F).alpha(1, 0.6F).coin().spin(0.6F).life(26).spawn();
            }
        } else {
            spray(o, dir, 0.3, 8, 0.4, S.GLOW, 0xFFFFFF, color, 0.12F, 6, 0);
        }
    }

    private static void coinToss(P p) {
        Entity e = p.e();
        Vec3 o = feet(e, p.pos());
        int ticks = Math.max(4, (int) p.b());
        int color = OUTCOME[outcomeIndex(p)];
        FxParticle coin = Fx.p(S.COIN, hand(e, o)).size(0.3F).alpha(1, 1).coin().spin(0.75F).life(ticks + 30).spawn();
        FxAnims.run(ticks + 1, age -> {
            Vec3 base = hand(e, o).add(0, 0.2, 0);
            double t = age / (double) ticks;
            Vec3 at = base.add(0, 2.6 * 4 * t * (1 - t), 0);
            if (coin != null) coin.moveTo(at.x, at.y, at.z);
            Fx.p(S.GLOW, at).color(0xFFFFFF, G_GOLD).size(0.16F, 0).life(8).spawn();
            if (age == ticks) {
                flash(at, color, 1.0F, 6);
                if (coin != null) {
                    coin.spinNow(0);
                    coin.fadeOut(16);
                }
                Fx.p(S.RING, at).size(0.2F, 1.1F).easeOut().color(color).alpha(1, 0).life(8).spawn();
            }
            return false;
        });
    }

    private static void diceRoll(P p) {
        Entity e = p.e();
        Vec3 o = feet(e, p.pos());
        int ticks = Math.max(4, (int) p.b());
        int color = OUTCOME[outcomeIndex(p)];
        Vec3 fwd = facing(e);
        Vec3 start = hand(e, o);
        for (int d = 0; d < 2; d++) {
            Vec3 land = o.add(fwd.scale(1.8)).add(Fx.right(fwd).scale(d == 0 ? -0.45 : 0.45)).add(0, 0.25, 0);
            FxParticle die = Fx.p(S.DICE, start).size(0.22F).alpha(1, 1).spin(0.7F).randomAngle().life(ticks + 30).spawn();
            int dd = d;
            FxAnims.run(ticks + 1, age -> {
                double t = Math.min(1, age / (ticks * 0.7));
                Vec3 at;
                if (t < 1) {
                    at = start.add(land.subtract(start).scale(t)).add(0, Math.sin(t * Math.PI) * 1.0, 0);
                } else {
                    double b = (age - ticks * 0.7) / (ticks * 0.3);
                    at = land.add(0, Math.sin(Math.min(1, b) * Math.PI) * 0.25, 0);
                    if (die != null) die.spinNow((float) (0.7 * (1 - Math.min(1, b))));
                }
                if (die != null) die.moveTo(at.x, at.y, at.z);
                if (age == ticks && die != null) {
                    die.spinNow(0);
                    die.fadeOut(20);
                    if (dd == 0) {
                        Vec3 mid = o.add(fwd.scale(1.8)).add(0, 0.6, 0);
                        flash(mid, color, 1.2F, 6);
                        ground(S.RING, o.add(fwd.scale(1.8)), 0.3F, color, 10).size(0.3F, 1.4F).easeOut().alpha(0.9F, 0).spawn();
                    }
                }
                return false;
            });
        }
    }

    private static void allIn(P p) {
        Entity e = p.e();
        Vec3 o = feet(e, p.pos());
        int ticks = Math.max(4, (int) p.b());
        int color = OUTCOME[outcomeIndex(p)];
        Vec3 c = chest(e, o);
        for (int i = 0, n = Fx.n(16); i < n; i++) {
            Vec3 v = Fx.randomUnit().multiply(0.25, 0, 0.25).add(0, Fx.r(0.35, 0.55), 0);
            Fx.p(i % 4 == 0 ? S.CARD : S.COIN, c).vel(v).gravity(0.7F).friction(0.98F).size(0.16F).alpha(1, 0.4F).coin().spin(Fx.rf(0.4F, 0.9F)).life(ticks + 8).spawn();
        }
        FxAnims.later(ticks, () -> {
            Vec3 cc = chest(e, o);
            flash(cc, color, 1.8F, 7);
            Fx.p(S.RING, cc).flat().size(0.3F, 2.4F).easeOut().color(color).alpha(1, 0).life(10).spawn();
            burst(cc, 20, 0.4, S.SPARK, 0xFFFFFF, color, 0.3F, 10, 0.1F);
        });
    }

    private static final S[] REEL = {S.COIN, S.CARD, S.DICE, S.SKULL};

    /** Slot machine: three reels above the head stop one after another. */
    private static void slots(P p) {
        Entity e = p.e();
        Vec3 o = feet(e, p.pos());
        int ticks = Math.max(6, (int) p.b());
        int outcome = outcomeIndex(p);
        S[] result = outcome == 0 ? new S[] {S.COIN, S.COIN, S.COIN}
                : outcome == 5 ? new S[] {S.SKULL, S.SKULL, S.DICE}
                : new S[] {S.COIN, S.CARD, S.DICE};
        int[] stopAt = {(int) (ticks * 0.55), (int) (ticks * 0.78), ticks};
        FxParticle[] reels = new FxParticle[3];
        FxParticle[] frames = new FxParticle[3];
        for (int k = 0; k < 3; k++) {
            reels[k] = Fx.p(REEL[k], o).size(0.25F).alpha(1, 1).life(ticks + 40).spawn();
            frames[k] = Fx.p(S.RING, o).size(0.38F).color(G_GOLD).alpha(0.9F, 0.9F).life(ticks + 40).spawn();
        }
        FxAnims.run(ticks + 1, age -> {
            Vec3 h = head(e, o).add(0, 0.7, 0);
            Vec3 side = Fx.right(facing(e));
            for (int k = 0; k < 3; k++) {
                Vec3 at = h.add(side.scale((k - 1) * 0.62));
                if (reels[k] != null) {
                    reels[k].moveTo(at.x, at.y, at.z);
                    if (age < stopAt[k]) reels[k].spriteNow(REEL[(age / 2 + k) % REEL.length]);
                    else if (age == stopAt[k]) {
                        reels[k].spriteNow(result[k]);
                        flash(at, OUTCOME[outcome], 0.6F, 4);
                    }
                }
                if (frames[k] != null) frames[k].moveTo(at.x, at.y, at.z);
            }
            if (age == ticks) {
                for (int k = 0; k < 3; k++) {
                    if (reels[k] != null) reels[k].fadeOut(24);
                    if (frames[k] != null) frames[k].fadeOut(24);
                }
            }
            return false;
        });
    }

    /** Roulette: a wheel on the ground around the caster with a ball that settles on the outcome. */
    private static void roulette(P p) {
        Entity e = p.e();
        Vec3 o = feet(e, p.pos());
        int ticks = Math.max(8, (int) p.b());
        int outcome = outcomeIndex(p);
        double r = 2.0;
        int life = ticks + 24;
        FxParticle wheel = ground(S.RING, o, (float) r, G_GOLD, life).alpha(0.9F, 0.9F).follow(e).spawn();
        ground(S.CIRCLE_HOLY, o, (float) (r * 0.75), G_TEAL, life).alpha(0.4F, 0.2F).spin(-0.08F).follow(e).spawn();
        for (int k = 0; k < 12; k++) {
            double a = Math.PI * 2 * k / 12;
            ground(S.GLOW, o.add(Math.cos(a) * r * 0.88, 0, Math.sin(a) * r * 0.88), 0.32F, OUTCOME[k % 6], life).alpha(0.9F, 0.9F).follow(e).spawn();
        }
        double target = Math.PI * 2 * outcome / 12;
        double total = Math.PI * 2 * 3 + target;
        FxParticle ball = Fx.p(S.ORB, o).size(0.22F).color(0xFFFFFF).alpha(1, 1).life(life).spawn();
        FxAnims.run(life, age -> {
            Vec3 f = feet(e, o).add(0, 0.25, 0);
            double t = Math.min(1, age / (double) ticks);
            double a = total * (1 - Math.pow(1 - t, 3));
            Vec3 at = f.add(Math.cos(a) * r * 0.88, Math.abs(Math.sin(age * 0.9)) * 0.2 * (1 - t), Math.sin(a) * r * 0.88);
            if (ball != null) ball.moveTo(at.x, at.y, at.z);
            if (age < ticks && age % 2 == 0) Fx.p(S.GLOW, at).color(0xFFFFFF).size(0.14F, 0).life(5).spawn();
            if (age == ticks) {
                flash(at, OUTCOME[outcome], 1.2F, 6);
                Fx.p(S.SPARK, at).color(0xFFFFFF, OUTCOME[outcome]).size(1.2F, 0).spin(0.2F).life(10).spawn();
            }
            if (age == ticks + 4) {
                if (ball != null) ball.fadeOut(16);
                if (wheel != null) wheel.fadeOut(16);
            }
            return false;
        });
    }

    private static void outcome(P p) {
        Entity e = p.e();
        Vec3 o = feet(e, p.pos());
        int color = p.colorOr(G_GOLD);
        float r = Math.max(2, p.a());
        Vec3 c = chest(e, o);
        switch (p.fx()) {
            case "gamble_jackpot" -> {
                flash(c, G_GOLD, 2.6F, 8);
                ground(S.WAVE, o, 1, G_GOLD, 16).size(1, r).easeOut().color(0xFFFFFF, G_GOLD).alpha(1, 0).spawn();
                for (int i = 0, n = Fx.n(24); i < n; i++) {
                    Vec3 v = Fx.randomUnit().multiply(0.3, 0, 0.3).add(0, Fx.r(0.4, 0.7), 0);
                    Fx.p(S.COIN, c).vel(v).gravity(0.8F).size(0.16F).alpha(1, 0.3F).coin().spin(Fx.rf(0.4F, 0.9F)).life(40).spawn();
                }
                for (int i = 0, n = Fx.n(40); i < n; i++) {
                    Fx.vanilla(ParticleTypes.FIREWORK, c.x, c.y + 1, c.z, Fx.r(-0.3, 0.3), Fx.r(0.0, 0.4), Fx.r(-0.3, 0.3));
                }
                burst(c.add(0, 1, 0), 30, 0.6, S.SPARK, 0xFFFFFF, G_GOLD, 0.35F, 12, 0.1F);
                hits(p.pts(), G_GOLD, 2);
                FxAnims.shake(o, 0.6F, 8, 10);
            }
            case "gamble_heal" -> {
                healAt(o, e, color, 0);
                for (Vec3 a : p.pts()) healAt(a.add(0, -0.9, 0), null, color, 3);
            }
            case "gamble_buff" -> {
                ground(S.CIRCLE_ARCANE, o, 1.4F, color, 26).alpha(0.9F, 0).spin(0.1F).follow(e).spawn();
                burst(c, 18, 0.3, S.GLOW, 0xFFFFFF, color, 0.15F, 14, -0.1F);
                for (Vec3 a : p.pts()) ground(S.CIRCLE_ARCANE, a.add(0, -0.9, 0), 1.0F, color, 22).alpha(0.9F, 0).spin(0.1F).spawn();
                aura(e, 120, 6, color, S.GLOW, 0.12F);
            }
            case "gamble_curse" -> {
                ground(S.CIRCLE_HEX, o, r, color, 30).size(r * 0.4F, r).easeOut().alpha(0.9F, 0).spin(-0.05F).spawn();
                for (Vec3 t : p.pts()) {
                    flash(t, color, 1.0F, 5);
                    Fx.p(S.SKULL, t.add(0, 0.9, 0)).vel(0, 0.03, 0).size(0.15F, 0.3F).alpha(1, 0).life(24).spawn();
                }
            }
            case "gamble_mana" -> {
                FxAnims.follow(e, 12, age -> {
                    Vec3 cc = chest(e, o);
                    for (int k = 0; k < 3; k++) {
                        double a = age * 0.6 + k * Math.PI * 2 / 3;
                        Fx.p(S.GLOW, cc.add(Math.cos(a) * 1.0, -0.6 + age * 0.1, Math.sin(a) * 1.0)).color(0xFFFFFF, color).size(0.17F, 0).life(10).spawn();
                    }
                    return false;
                });
                flash(c, color, 1.2F, 6);
            }
            default -> { // bust
                for (int i = 0; i < Fx.n(12); i++) smoke(c.add(Fx.randomUnit().scale(0.4)), 0x3A3A3A, 0.5F, 1.4F, 30).vel(0, 0.04, 0).spawn();
                Fx.p(S.SKULL, c.add(0, 0.8, 0)).vel(0, 0.03, 0).color(0xB0B0B0).size(0.15F, 0.25F).alpha(1, 0).life(26).spawn();
                Fx.p(S.COIN, c.add(0, 0.5, 0)).vel(0.05, 0.15, 0).gravity(0.6F).color(0x8A8A8A).size(0.16F).alpha(1, 0).coin().spin(0.3F).life(24).spawn();
            }
        }
    }
}
