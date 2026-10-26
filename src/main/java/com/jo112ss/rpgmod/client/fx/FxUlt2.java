package com.jo112ss.rpgmod.client.fx;

import static com.jo112ss.rpgmod.client.fx.FxUlt.dark;
import static com.jo112ss.rpgmod.client.fx.FxUlt.feetOf;
import static com.jo112ss.rpgmod.client.fx.FxUlt.flameAt;
import static com.jo112ss.rpgmod.client.fx.FxUlt.light;
import static com.jo112ss.rpgmod.client.fx.SkillFx.*;

import com.jo112ss.rpgmod.client.fx.Fx.S;
import com.jo112ss.rpgmod.client.fx.SkillFx.P;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * The ultimates of the advanced and hybrid jobs, each with its own look: the asura's arms, the
 * fortress walls, the dead eye, the great beam, the great rune, the wild card's deck, the yaksha's
 * ghost fire, the miracle's wings, the ghost fleet's fog, the peerless blow, the dragon king,
 * the symphony, the spellblade's two, heaven and earth, the field of flowers, the spear palisade,
 * the holy lance, the concerto, the sealing chord, the hunter's moon, and the treasure hunter's two.
 */
public final class FxUlt2 {
    private FxUlt2() {}

    public static boolean play(P p) {
        switch (p.fx()) {
            case "u_asura" -> asura(p);
            case "u_bastion" -> bastion(p);
            case "u_dead_eye" -> deadEye(p);
            case "u_dead_mark" -> deadMark(p);
            case "u_beam_start" -> beamStart(p);
            case "u_beam" -> beam(p);
            case "u_grand_rune" -> grandRune(p);
            case "u_wildcard" -> wildcard(p);
            case "u_yaksha" -> yaksha(p);
            case "u_great_miracle" -> greatMiracle(p);
            case "u_miracle_pillar" -> miraclePillar(p);
            case "u_fleet_mark" -> fleetMark(p);
            case "u_peerless" -> peerless(p);
            case "u_peerless_finish" -> peerlessFinish(p);
            case "u_dk_rise" -> dragonKingRise(p);
            case "u_dk_land" -> dragonKingLand(p);
            case "u_symphony" -> symphony(p);
            case "u_symphony_note" -> symphonyNote(p);
            case "u_arcane_edge" -> arcaneEdge(p);
            case "u_elemental" -> elemental(p);
            case "u_heaven_earth" -> heavenEarth(p);
            case "u_bloom_field" -> bloomField(p);
            case "u_bloom_heal" -> bloomHeal(p);
            case "u_bloom_burst" -> bloomBurst(p);
            case "u_fortress" -> fortress(p);
            case "u_fortress_burst" -> fortressBurst(p);
            case "u_holy_lance" -> holyLance(p);
            case "u_grand_concerto" -> grandConcerto(p);
            case "u_sealing" -> sealing(p);
            case "u_seal_hit" -> sealHit(p);
            case "u_night_hunt" -> nightHunt(p);
            case "u_hunted" -> hunted(p);
            case "u_shadow_mark" -> shadowMark(p);
            case "u_treasure_zone" -> treasureZone(p);
            case "u_coin_pick" -> coinPick(p);
            case "u_golden_shot" -> goldenShot(p);
            case "u_golden_boom" -> goldenBoom(p);
            default -> {
                return false;
            }
        }
        return true;
    }

    // ================================================================== berserker: asura

    private static final int BLOOD = 0xC01818;

    private static void asura(P p) {
        Entity e = p.e();
        Vec3 o = feetOf(p);
        int ticks = Math.max(20, (int) (p.a() * 20));
        FxAnims.shake(o, 1.4F, 12, 18);
        flash(o.add(0, 1.4, 0), BLOOD, 3.2F, 9);
        ground(S.CIRCLE_HEX, o, 3.2F, BLOOD, 40).color(0xFF6040, BLOOD).size(0.5F, 3.6F).easeOut().alpha(1, 0).spin(0.1F).spawn();
        ground(S.SMOKE, o, 3F, 0x300000, 50).alphaBlend().lit().alpha(0.7F, 0).randomAngle().spawn();
        // six arms of blood light fanning out behind the back, swinging for the whole time
        FxAnims.follow(e, ticks, age -> {
            Vec3 c = e.position().add(0, 1.3, 0);
            Vec3 back = Vec3.directionFromRotation(0, e.getYRot()).scale(-0.45);
            Vec3 side = Fx.right(Vec3.directionFromRotation(0, e.getYRot()));
            if (age % 2 == 0) {
                for (int i = 0; i < 6; i++) {
                    int s = i < 3 ? -1 : 1;
                    int k = i % 3;
                    double swing = Math.sin(age * 0.18 + i) * 0.35;
                    Vec3 dir = side.scale(s).add(0, 0.8 - k * 0.6 + swing, 0).normalize();
                    Vec3 tip = c.add(back).add(dir.scale(1.5));
                    Fx.p(S.FANG, c.add(back).add(dir.scale(0.8))).fixed(Fx.basis(dir.cross(back.normalize()), dir)).color(0xFF8060, BLOOD).size(0.9F).alpha(0.8F, 0)
                            .life(3).spawn();
                    Fx.p(S.GLOW, tip).color(0xFF6040, BLOOD).size(0.35F, 0).life(5).spawn();
                }
            }
            if (age % 4 == 0) flameAt(e.position().add(Fx.r(-0.4, 0.4), 0, Fx.r(-0.4, 0.4)), 0xFF5030, 0x500000, 0.6F, 12, null);
            return false;
        });
    }

    // ================================================================== guardian: bastion

    private static final int GOLD = 0xFFD050;

    private static void bastion(P p) {
        Entity e = p.e();
        Vec3 o = feetOf(p);
        float r = Math.max(4, p.a());
        int ticks = Math.max(20, (int) (p.b() * 20));
        FxAnims.shake(o, 1.0F, 10, 20);
        // walls of light rise all round, with crests on them
        int n = 14;
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            Vec3 at = o.add(Math.cos(a) * r, 0, Math.sin(a) * r);
            for (int h = 0; h < 3; h++) {
                Fx.p(S.HEX, at.add(0, 0.6 + h * 1.0, 0)).fixed(Fx.facing(at.subtract(o))).color(0xFFFFFF, GOLD).size(0.9F).alpha(0.8F, 0).fadeIn(0.1F)
                        .life(Math.min(ticks, 80)).delay(i + h * 2).spawn();
            }
            if (i % 2 == 0) Fx.p(S.CREST, at.add(0, 3.4, 0)).fixed(Fx.facing(at.subtract(o))).color(0xFFFFFF, GOLD).size(0.8F).alpha(1, 0).life(60).delay(i).spawn();
        }
        ground(S.CIRCLE_HOLY, o, r, GOLD, 60).color(0xFFFFFF, GOLD).alpha(0.8F, 0).spin(0.02F).spawn();
        for (Vec3 v : p.pts()) Fx.p(S.CREST, v.add(0, 1.4, 0)).color(0xFFFFFF, GOLD).size(0.3F, 0.7F).easeOut().alpha(1, 0).life(30).spawn();
        // the guardian itself cannot fall: a golden heart of light beats
        FxAnims.follow(e, ticks, age -> {
            if (age % 20 == 0) Fx.p(S.CREST, e.position().add(0, 1.1, 0)).color(0xFFFFFF, GOLD).size(0.6F, 1.4F).alpha(0.7F, 0).life(12).follow(e).spawn();
            return false;
        });
    }

    // ================================================================== sharpshooter: dead eye

    private static final int DEAD = 0xFF3030;

    private static void deadEye(P p) {
        Entity e = p.e();
        Vec3 o = feetOf(p);
        Fx.p(S.EYE, o.add(0, 2.8, 0)).color(0xFFFFFF, DEAD).size(0.3F, 1.6F).easeOut().alpha(1, 0).life(26).follow(e).spawn();
        Fx.p(S.FLARE, o.add(0, 2.8, 0)).color(DEAD).size(2.4F, 0.6F).alpha(0.5F, 0).life(14).follow(e).spawn();
        // the world dims to red for a moment
        for (int i = 0; i < 3; i++) Fx.p(S.RING, o.add(0, 1.4, 0)).color(DEAD).size(0.5F, 8F + i * 3).easeOut().alpha(0.6F, 0).life(12).delay(i * 3).spawn();
    }

    private static void deadMark(P p) {
        Entity e = p.e();
        Vec3 c = e != null ? e.position().add(0, e.getBbHeight() * 0.55, 0) : p.pos().add(0, 1, 0);
        Fx.p(S.CIRCLE_HEX, c).color(0xFFFFFF, DEAD).size(2.4F, 0.9F).easeOut().alpha(0, 1).spin(0.2F).life(18).follow(e).spawn();
        Fx.p(S.CROSS, c).color(0xFFFFFF, DEAD).size(1.6F, 0.6F).easeOut().alpha(0.2F, 1).angle(0.785F).life(18).follow(e).spawn();
    }

    // ================================================================== spellmaster: the great beam

    private static final int ARC = 0xC8A8FF;

    private static void beamStart(P p) {
        Entity e = p.e();
        int ticks = Math.max(20, (int) p.b());
        FxAnims.shake(feetOf(p), 0.6F, ticks, 12);
        // three magic circles stand in front of the hand while it fires
        FxAnims.follow(e, ticks, age -> {
            Vec3 look = e.getLookAngle();
            Vec3 hand = e.getEyePosition().add(0, -0.35, 0).add(look.scale(0.9));
            for (int k = 0; k < 3; k++) {
                Vec3 at = hand.add(look.scale(0.4 + k * 0.9));
                float s = 1.4F + k * 0.6F;
                Fx.p(k == 1 ? S.CIRCLE_HEX : S.CIRCLE_ARCANE, at).fixed(Fx.across(look)).color(0xFFFFFF, ARC).size(s).alpha(0.9F, 0.6F).angle(age * (k % 2 == 0 ? 0.15F : -0.2F))
                        .life(2).spawn();
            }
            return false;
        });
    }

    private static void beam(P p) {
        Vec3 a = p.pos(), b = p.vec();
        Vec3 d = b.subtract(a);
        double len = d.length();
        if (len < 0.1) return;
        Vec3 dir = d.scale(1 / len);
        // a white core, a violet sheath, and rings sliding along it
        int segs = Math.max(1, (int) Math.ceil(len / 3.0));
        for (int i = 0; i < segs; i++) {
            Vec3 s0 = a.add(dir.scale(len * i / segs)), s1 = a.add(dir.scale(len * (i + 1) / segs));
            segment(s0, s1, 0.9F).color(0xFFFFFF).alpha(1, 0.6F).life(3).spawn();
            segment(s0, s1, 2.2F).color(ARC).alpha(0.55F, 0).life(3).spawn();
        }
        double off = (Fx.RND.nextDouble()) * 2.0;
        for (double t = off; t < len; t += 2.0) {
            Fx.p(S.RING, a.add(dir.scale(t))).fixed(Fx.across(dir)).color(0xFFFFFF, ARC).size(1.0F, 1.8F).alpha(0.8F, 0).life(4).spawn();
        }
        Fx.p(S.FLARE, b).color(0xFFFFFF, ARC).size(2.4F, 1.4F).alpha(0.8F, 0).life(3).spawn();
        for (int i = 0, n = Fx.n(4); i < n; i++) {
            Fx.p(S.TWINKLE, b).vel(Fx.randomUnit().scale(0.3)).color(0xFFFFFF, ARC).size(0.4F, 0).life(8).spawn();
        }
        hits(p.pts(), ARC, 0);
    }

    // ================================================================== runemaster: grand rune

    private static final int RUNE = 0x60E0C0;

    private static void grandRune(P p) {
        Vec3 o = p.pos();
        float r = Math.max(4, p.a());
        int life = Math.max(20, (int) p.b());
        ground(S.CIRCLE_ARCANE, o, r, RUNE, life).color(0xFFFFFF, RUNE).size(r * 0.2F, r).easeOut().alpha(0.8F, 0.3F).spin(0.01F).spawn();
        ground(S.CIRCLE_HEX, o, r * 0.6F, RUNE, life).color(0xFFFFFF, RUNE).alpha(0.7F, 0.2F).spin(-0.02F).delay(4).spawn();
        // four runestones stand at the edge, their runes orbiting
        for (int k = 0; k < 4; k++) {
            double a = Math.PI / 2 * k + Math.PI / 4;
            Vec3 at = o.add(Math.cos(a) * r * 0.9, 0, Math.sin(a) * r * 0.9);
            for (int h = 0; h < 4; h++) {
                Fx.p(S.BEAM, at.add(0, 0.5 + h * 0.9, 0)).upright().color(0xFFFFFF, RUNE).size(1.0F).alpha(0.7F, 0.1F).fadeIn(0.1F).life(life).delay(k * 3).spawn();
            }
            Fx.p(Fx.rune(k), at.add(0, 4, 0)).color(0xFFFFFF, RUNE).size(0.2F, 0.9F).easeOut().alpha(1, 0.3F).life(life).delay(k * 3).spawn();
        }
        FxAnims.run(life, age -> {
            if (age % 4 != 0) return false;
            double a = age * 0.05;
            for (int k = 0; k < 4; k++) {
                double aa = a + Math.PI / 2 * k;
                Fx.p(Fx.rune(k + age), o.add(Math.cos(aa) * r * 0.55, 0.4 + Math.sin(age * 0.1 + k) * 0.3, Math.sin(aa) * r * 0.55)).color(0xFFFFFF, RUNE)
                        .size(0.45F, 0.2F).alpha(0.9F, 0).life(10).spawn();
            }
            return false;
        });
    }

    // ================================================================== joker: wild card

    private static final int JOKER = 0xB070FF;

    private static void wildcard(P p) {
        Entity e = p.e();
        Vec3 o = feetOf(p);
        // a deck fans out round the joker, spins, and one card flies up and flips
        FxAnims.run(20, age -> {
            for (int i = 0; i < 10; i++) {
                double a = age * 0.35 + Math.PI * 2 * i / 10;
                double rr = 0.6 + age * 0.06;
                Vec3 at = o.add(Math.cos(a) * rr, 1.0 + Math.sin(a * 2) * 0.2, Math.sin(a) * rr);
                Fx.p(S.CARD, at).fixed(Fx.facing(at.subtract(o))).size(0.3F).alpha(1, 0).life(2).spawn();
            }
            return false;
        });
        FxAnims.later(18, () -> {
            Vec3 top = o.add(0, 3.0, 0);
            Fx.p(S.CARD, top).coin().vel(0, 0.03, 0).size(0.6F).alpha(1, 0).life(20).spawn();
            flash(top, JOKER, 2.4F, 8);
            burst(top, 24, 0.4, S.TWINKLE, 0xFFFFFF, JOKER, 0.35F, 16, 0);
        });
    }

    // ================================================================== nightblade: yaksha

    private static final int ONI = 0x5060E0;

    private static void yaksha(P p) {
        Entity e = p.e();
        Vec3 o = feetOf(p);
        int ticks = Math.max(20, (int) (p.a() * 20));
        flash(o.add(0, 1.2, 0), ONI, 2.6F, 8);
        ground(S.MOON, o, 3, ONI, 40).color(0xC0D0FF, ONI).alpha(0.9F, 0).spin(0.05F).spawn();
        // blue ghost fire burning off the body, and two crescent blades circling
        FxAnims.follow(e, ticks, age -> {
            Vec3 c = e.position();
            if (age % 2 == 0) flameAt(c.add(Fx.r(-0.35, 0.35), Fx.r(0, 1.2), Fx.r(-0.35, 0.35)), 0xA0B8FF, 0x101850, 0.45F, 12, null);
            for (int k = 0; k < 2; k++) {
                double a = age * 0.3 + k * Math.PI;
                Vec3 at = c.add(Math.cos(a) * 1.0, 1.1 + Math.sin(age * 0.2 + k) * 0.3, Math.sin(a) * 1.0);
                Fx.p(S.MOON, at).fixed(Fx.facing(at.subtract(c.add(0, 1.1, 0)))).color(0xFFFFFF, ONI).size(0.5F).alpha(0.9F, 0).life(3).spawn();
            }
            return false;
        });
    }

    // ================================================================== bishop: great miracle

    private static final int HOLY = 0xFFF0B0;

    private static void greatMiracle(P p) {
        Entity e = p.e();
        Vec3 o = feetOf(p);
        float r = Math.max(4, p.a());
        FxAnims.shake(o, 0.8F, 16, 30);
        flash(o.add(0, 2, 0), HOLY, 5, 14);
        ground(S.CIRCLE_HOLY, o, r * 0.5F, HOLY, 60).color(0xFFFFFF, HOLY).size(1, r).easeOut().alpha(1, 0).spin(0.03F).spawn();
        // great wings of light spread above the bishop
        Vec3 face = e == null ? new Vec3(0, 0, 1) : Vec3.directionFromRotation(0, e.getYRot());
        Vec3 side = Fx.right(face);
        for (int s = -1; s <= 1; s += 2) {
            for (int i = 0; i < 10; i++) {
                double t = i / 9.0;
                Vec3 at = o.add(0, 2.4 + Math.sin(t * Math.PI) * 1.2, 0).add(face.scale(-0.4)).add(side.scale(s * (0.4 + t * 3.0)));
                Fx.p(S.FEATHER, at).fixed(Fx.basis(side.scale(s), new Vec3(0, 1, 0).add(side.scale(s * 0.5)))).color(0xFFFFFF, HOLY).size(0.7F + (float) t * 0.4F)
                        .alpha(1, 0).fadeIn(0.2F).life(40).delay(i).spawn();
            }
        }
        // feathers drifting down over everyone
        FxAnims.run(60, age -> {
            if (age % 2 != 0) return false;
            double a = Fx.r(0, Math.PI * 2), d = Math.sqrt(Fx.RND.nextDouble()) * r;
            Fx.p(S.FEATHER, o.add(Math.cos(a) * d, 6, Math.sin(a) * d)).vel(0, -0.06, 0).color(0xFFFFFF, HOLY).size(0.3F, 0.2F).randomAngle().spin(0.05F).life(60).spawn();
            return false;
        });
    }

    /** One raised / healed ally: a column of light with a crown of crosses. */
    private static void miraclePillar(P p) {
        Vec3 at = p.pos();
        for (int i = 0; i < 10; i++) {
            Fx.p(S.BEAM, at.add(0, 0.6 + i * 1.0, 0)).upright().color(0xFFFFFF, HOLY).size(1.5F, 0.6F).alpha(0.9F, 0).life(30).delay(i).spawn();
        }
        for (int i = 0; i < 6; i++) {
            double a = Math.PI * 2 * i / 6;
            Fx.p(S.CROSS, at.add(Math.cos(a) * 0.8, 2.4, Math.sin(a) * 0.8)).upright().vel(0, 0.03, 0).color(0xFFFFFF, HOLY).size(0.35F, 0.2F).alpha(1, 0).life(30).delay(i * 2).spawn();
        }
        healAt(at, null, HOLY, 4);
    }

    // ================================================================== captain: the ghost fleet's fog

    private static final int GHOST = 0x7FE8D8;

    private static void fleetMark(P p) {
        Vec3 o = p.pos();
        float r = Math.max(4, p.a());
        int life = Math.max(20, (int) p.b());
        // a green ghost fog rolls over the area
        FxAnims.run(life, age -> {
            if (age % 3 != 0) return false;
            double a = Fx.r(0, Math.PI * 2), d = Math.sqrt(Fx.RND.nextDouble()) * r;
            Fx.p(S.SMOKE, o.add(Math.cos(a) * d, 0.3, Math.sin(a) * d)).color(GHOST, 0x204840).size(1.2F, 2.6F).alpha(0.35F, 0).randomAngle().spin(0.02F)
                    .vel(Fx.r(-0.02, 0.02), 0.01, Fx.r(-0.02, 0.02)).life(40).spawn();
            if (age % 9 == 0) Fx.p(S.SKULL, o.add(Math.cos(a) * d, 1.2, Math.sin(a) * d)).vel(0, 0.03, 0).size(0.25F).alpha(0.6F, 0).life(20).spawn();
            return false;
        });
        ground(S.CIRCLE_HEX, o, r, GHOST, life).color(0xFFFFFF, GHOST).alpha(0.4F, 0).spin(0.01F).spawn();
    }

    // ================================================================== champion: peerless

    private static final int KI = 0xFF8030;

    private static void peerless(P p) {
        Entity e = p.e();
        Vec3 o = feetOf(p);
        int strikes = Math.max(3, (int) p.b());
        FxAnims.shake(o, 0.8F, 8, 14);
        ground(S.CRACK, o, 2.4F, KI, 50).color(0xFFE0A0, KI).alpha(1, 0).randomAngle().spawn();
        // the ki flares up in a column of flame
        for (int i = 0, n = Fx.n(30); i < n; i++) {
            double a = Fx.r(0, Math.PI * 2), rr = Fx.r(0.3, 1.0);
            flameAt(o.add(Math.cos(a) * rr, Fx.r(0, 0.4), Math.sin(a) * rr), 0xFFE0A0, KI, Fx.rf(0.6F, 1.2F), 16 + Fx.RND.nextInt(10), null);
        }
        FxAnims.follow(e, strikes * 3 + 6, age -> {
            if (age % 2 == 0) Fx.p(S.GLOW, e.position().add(0, 1, 0)).color(KI).size(2.0F, 1.0F).alpha(0.3F, 0).life(4).follow(e).spawn();
            return false;
        });
    }

    private static void peerlessFinish(P p) {
        Vec3 at = p.pos();
        Vec3 dir = p.vec().normalize();
        FxAnims.shake(at, 2.2F, 14, 20);
        flash(at, 0xFFF0C0, 4, 9);
        // a fist of ki the size of a house, and the shock rolling away from it
        Fx.p(S.FLARE, at.add(dir.scale(1.5))).color(0xFFFFFF, KI).size(5F, 1.5F).alpha(1, 0).life(10).spawn();
        for (int i = 0; i < 6; i++) {
            Fx.p(S.RING, at.add(dir.scale(i * 1.2))).fixed(Fx.across(dir)).color(0xFFFFFF, KI).size(1F + i * 0.5F, 4F + i).easeOut().alpha(1, 0).life(10).delay(i).spawn();
        }
        spray(at, dir, 0.3, 50, 1.4, S.STREAK, 0xFFFFFF, KI, 0.5F, 10, 0);
        ground(S.CRACK, at.subtract(0, 1.1, 0).add(dir.scale(2)), 3.2F, KI, 50).color(0xFFE0A0, KI).alpha(1, 0).angle((float) Math.atan2(dir.z, dir.x)).spawn();
        hits(p.pts(), KI, 1);
    }

    // ================================================================== dragoon: dragon king

    private static final int DK = 0xFF6020;

    private static void dragonKingRise(P p) {
        Entity e = p.e();
        Vec3 o = feetOf(p);
        flash(o.add(0, 0.5, 0), DK, 2.6F, 6);
        ground(S.CRACK, o, 2.2F, DK, 40).color(0xFFE0A0, DK).alpha(1, 0).randomAngle().spawn();
        // a spiral of fire carries the dragoon up
        FxAnims.follow(e, 22, age -> {
            Vec3 c = e.position();
            for (int k = 0; k < 2; k++) {
                double a = age * 0.6 + k * Math.PI;
                Fx.p(S.FLAME, c.add(Math.cos(a) * 0.8, -0.4, Math.sin(a) * 0.8)).upright().color(0xFFE0A0, DK).size(0.9F, 0.3F).alpha(0.9F, 0).life(10).spawn();
            }
            Fx.p(S.GLOW, c.add(0, 0.5, 0)).color(DK).size(1.4F, 0.4F).alpha(0.5F, 0).life(6).spawn();
            return false;
        });
    }

    private static void dragonKingLand(P p) {
        Vec3 o = p.pos();
        float r = Math.max(4, p.a());
        int fire = Math.max(20, (int) p.b());
        float fireR = Math.max(2, (float) p.vec().x);
        FxAnims.shake(o, 2.8F, 22, 30);
        flash(o.add(0, 1, 0), DK, r, 10);
        Fx.vanilla(ParticleTypes.EXPLOSION_EMITTER, o.x, o.y + 0.5, o.z, 0, 0, 0);
        ground(S.CRACK, o, r * 1.2F, DK, 100).color(0xFFE0A0, 0x601000).size(1, r * 1.4F).easeOut().alpha(1, 0).randomAngle().spawn();
        ground(S.WAVE, o, 1, DK, 16).size(1, r * 2).easeOut().color(0xFFE0A0, DK).alpha(1, 0).spawn();
        // a geyser of fire, and the scorched ground burning on
        for (int i = 0, n = Fx.n(40); i < n; i++) {
            Vec3 v = Fx.randomUnit().multiply(0.5, 0, 0.5).add(0, Fx.r(0.6, 1.4), 0);
            Fx.p(S.FLAME, o.add(0, 0.3, 0)).upright().vel(v.scale(0.5)).gravity(0.5F).friction(0.95F).color(0xFFE0A0, DK).size(1.2F, 0.3F).alpha(1, 0).life(24).spawn();
        }
        debris(o, 50, r * 0.4, 0.9);
        FxAnims.run(fire, age -> {
            if (age % 2 != 0) return false;
            double a = Fx.r(0, Math.PI * 2), d = Math.sqrt(Fx.RND.nextDouble()) * fireR;
            flameAt(o.add(Math.cos(a) * d, 0, Math.sin(a) * d), 0xFFC060, DK, Fx.rf(0.5F, 1.0F), 16, null);
            if (age % 10 == 0) smoke(o.add(Math.cos(a) * d, 0.5, Math.sin(a) * d), 0x302018, 0.8F, 2.2F, 40).vel(0, 0.05, 0).alpha(0.5F, 0).spawn();
            return false;
        });
        hits(p.pts(), DK, 2);
    }

    // ================================================================== maestro: symphony

    private static final int MAESTRO = 0xFFE070;

    private static void symphony(P p) {
        Entity e = p.e();
        Vec3 o = feetOf(p);
        float r = Math.max(4, p.a());
        int ticks = Math.max(20, (int) (p.b() * 20));
        flash(o.add(0, 1.4, 0), MAESTRO, 2.6F, 8);
        // a staff of five golden lines wound round the maestro
        FxAnims.follow(e, ticks, age -> {
            if (age % 3 != 0) return false;
            Vec3 c = e.position();
            for (int line = 0; line < 5; line++) {
                double a = age * 0.12 + line * 0.15;
                double h = 0.6 + line * 0.22;
                Fx.p(S.GLOW, c.add(Math.cos(a) * 1.3, h, Math.sin(a) * 1.3)).color(0xFFFFFF, MAESTRO).size(0.12F).alpha(0.8F, 0).life(18).spawn();
            }
            return false;
        });
        ground(S.CIRCLE_ARCANE, o, r * 0.5F, MAESTRO, 30).color(0xFFFFFF, MAESTRO).size(1, r).easeOut().alpha(0.8F, 0).spin(0.05F).spawn();
    }

    /** A note played well during the symphony: a beat of sound with the note thrown out. */
    private static void symphonyNote(P p) {
        Vec3 o = feetOf(p);
        float r = Math.max(4, p.a());
        boolean perfect = p.b() >= 2;
        ground(S.WAVE, o, 1, MAESTRO, 9).size(1, r * 2).easeOut().color(0xFFFFFF, perfect ? MAESTRO : 0xFFF4C0).alpha(perfect ? 0.9F : 0.6F, 0).spawn();
        for (Vec3 v : p.pts()) {
            Fx.p(S.NOTE, v).vel(0, 0.08, 0).color(0xFFFFFF, MAESTRO).size(0.5F, 0.1F).alpha(1, 0).life(10).spawn();
            Fx.p(S.SPARK, v).color(0xFFFFFF, MAESTRO).size(0.6F, 0).randomAngle().life(4).spawn();
        }
    }

    // ================================================================== spellblade: arcane edge, elemental burst

    private static final int[] EL = {0xFF6A20, 0x9FE8FF, 0xFFF07A};

    private static void arcaneEdge(P p) {
        Vec3 at = p.pos();
        Vec3 dir = flatDir(p.vec());
        float r = Math.max(3, p.a());
        int el = Math.max(0, Math.min(2, (int) p.b()));
        int c = EL[el];
        Vec3 side = Fx.right(dir);
        FxAnims.shake(at, 1.2F, 8, 14);
        // one huge blade of the element sweeping across
        FxAnims.run(6, age -> {
            double t = (age + 1) / 6.0;
            double a = -1.2 + t * 2.4;
            Vec3 bladeDir = dir.scale(Math.cos(a)).add(side.scale(Math.sin(a)));
            for (int k = 1; k <= 6; k++) {
                Vec3 v = at.add(bladeDir.scale(k * r / 6));
                Fx.p(S.GLOW, v).color(0xFFFFFF, c).size(0.6F + k * 0.05F, 0).life(6).spawn();
            }
            return false;
        });
        Fx.p(S.SLASH, at.add(dir.scale(r * 0.5))).fixed(Fx.basis(side, dir.scale(-1).add(0, 0.15, 0))).color(0xFFFFFF, c).size(r * 0.9F, r * 1.1F).alpha(1, 0).life(8).spawn();
        for (int i = 0, n = Fx.n(30); i < n; i++) {
            double a = Fx.r(-1.2, 1.2);
            Vec3 v = dir.scale(Math.cos(a)).add(side.scale(Math.sin(a)));
            Vec3 at2 = at.add(v.scale(Fx.r(1, r)));
            switch (el) {
                case 0 -> flameAt(at2.subtract(0, 0.8, 0), 0xFFE0A0, c, 0.7F, 14, null);
                case 1 -> Fx.p(S.SHARD, at2).vel(v.scale(0.1)).gravity(0.6F).color(0xFFFFFF, c).size(0.35F).randomAngle().life(18).spawn();
                default -> Fx.p(S.LIGHTNING, at2).color(0xFFFFFF, c).size(0.6F).randomAngle().alpha(1, 0).life(4).delay(Fx.RND.nextInt(4)).spawn();
            }
        }
        hits(p.pts(), c, 1);
    }

    private static void elemental(P p) {
        Vec3 o = p.pos();
        float r = Math.max(3, p.a());
        int el = Math.max(0, Math.min(2, (int) p.b()));
        int c = EL[el];
        FxAnims.shake(o, 1.2F, 8, 16);
        switch (el) {
            case 0 -> { // a ring of flame bursting outwards
                flash(o.add(0, 1, 0), c, 3, 6);
                for (int i = 0; i < 24; i++) {
                    double a = Math.PI * 2 * i / 24;
                    Vec3 v = new Vec3(Math.cos(a), 0, Math.sin(a));
                    Fx.p(S.FLAME, o.add(v.scale(0.8))).upright().vel(v.scale(r * 0.08)).friction(0.9F).color(0xFFE0A0, c).size(1.2F, 0.4F).alpha(1, 0).life(14).spawn();
                }
                ground(S.CRACK, o, r, c, 40).color(0xFFE0A0, c).alpha(0.9F, 0).randomAngle().spawn();
            }
            case 1 -> { // ice spikes standing up all round
                for (int i = 0; i < 16; i++) {
                    double a = Math.PI * 2 * i / 16, d = Fx.r(1.2, r);
                    Vec3 g = o.add(Math.cos(a) * d, 0, Math.sin(a) * d);
                    Fx.p(S.SHARD, g.add(0, 0.6, 0)).upright().color(0xFFFFFF, c).size(0.2F, 1.4F).easeOut().alpha(1, 0).life(30).delay((int) d).spawn();
                }
                ground(S.CIRCLE_HEX, o, r, c, 40).color(0xFFFFFF, c).alpha(0.8F, 0).spawn();
                burst(o.add(0, 1, 0), 30, 0.5, S.TWINKLE, 0xFFFFFF, c, 0.3F, 20, 0.05F);
            }
            default -> { // the sky answers: bolts on everything around
                flash(o.add(0, 3, 0), c, 3, 5);
                for (Vec3 v : p.pts()) {
                    drawBolt(bolt(v.add(Fx.r(-1, 1), 9, Fx.r(-1, 1)), v, 0.9, 4), c, 0.25F, 6, 0);
                    Fx.p(S.FLARE, v).color(0xFFFFFF, c).size(1.6F, 0.4F).alpha(1, 0).life(6).spawn();
                }
                for (int i = 0; i < 4; i++) {
                    double a = Fx.r(0, Math.PI * 2), d = Fx.r(1, r);
                    Vec3 g = o.add(Math.cos(a) * d, 0, Math.sin(a) * d);
                    drawBolt(bolt(g.add(0, 9, 0), g, 0.9, 4), c, 0.2F, 5, i * 2);
                }
            }
        }
        hits(p.pts(), c, 1);
    }

    // ================================================================== god hand: heaven and earth, blooming fists

    private static void heavenEarth(P p) {
        Vec3 o = p.pos();
        float r = Math.max(3, p.a());
        FxAnims.shake(o, 1.4F, 12, 20);
        // a pillar of heaven's light and the earth's ki answering it
        for (int i = 0; i < 12; i++) {
            Fx.p(S.BEAM, o.add(0, i * 1.0, 0)).upright().color(0xFFFFFF, 0xFFE6A0).size(2.4F, 0.8F).alpha(0.9F, 0).life(24).delay(i / 2).spawn();
        }
        for (int k = 0; k < 2; k++) {
            int c = k == 0 ? 0xFFF8E0 : 0xFF9030;
            int kk = k;
            FxAnims.run(24, age -> {
                double a = age * 0.5 + kk * Math.PI;
                double h = kk == 0 ? 4 - age * 0.15 : age * 0.15;
                Fx.p(S.ORB, o.add(Math.cos(a) * 1.0, h, Math.sin(a) * 1.0)).color(0xFFFFFF, c).size(0.6F, 0.2F).life(10).spawn();
                return false;
            });
        }
        ground(S.WAVE, o, 1, 0xFF9030, 14).size(1, r * 2).easeOut().color(0xFFF0C0, 0xFF9030).alpha(1, 0).spawn();
        hits(p.pts(), 0xFF9030, 2);
    }

    private static final int BLOSSOM = 0xFF9CC8;

    private static void bloomField(P p) {
        Vec3 o = p.pos();
        float r = Math.max(3, p.a());
        int life = Math.max(20, (int) p.b());
        ground(S.CIRCLE_NATURE, o, r, BLOSSOM, life).color(0xFFFFFF, BLOSSOM).size(r * 0.3F, r).easeOut().alpha(0.8F, 0.2F).spin(0.01F).spawn();
        // flowers open across the field
        for (int i = 0, n = Fx.n(18); i < n; i++) {
            double a = Fx.r(0, Math.PI * 2), d = Math.sqrt(Fx.RND.nextDouble()) * r;
            ground(S.SAKURA, o.add(Math.cos(a) * d, 0.02, Math.sin(a) * d), Fx.rf(0.4F, 0.8F), BLOSSOM, life).color(0xFFFFFF, BLOSSOM).size(0.05F, Fx.rf(0.4F, 0.8F)).easeOut()
                    .alpha(0.9F, 0.2F).randomAngle().delay(Fx.RND.nextInt(10)).spawn();
        }
        // petals on the wind the whole time
        FxAnims.run(life, age -> {
            if (age % 2 != 0) return false;
            double a = Fx.r(0, Math.PI * 2), d = Math.sqrt(Fx.RND.nextDouble()) * r;
            Fx.p(S.PETAL, o.add(Math.cos(a) * d, Fx.r(2, 4), Math.sin(a) * d)).vel(0.03, -0.04, 0.01).color(0xFFFFFF, BLOSSOM).size(0.22F, 0.15F).randomAngle()
                    .spin(Fx.rf(-0.15F, 0.15F)).life(50).spawn();
            return false;
        });
    }

    private static void bloomHeal(P p) {
        Vec3 at = p.pos();
        for (int i = 0; i < 6; i++) {
            double a = Math.PI * 2 * i / 6;
            Fx.p(S.SAKURA, at.add(Math.cos(a) * 0.5, 0, Math.sin(a) * 0.5)).vel(Math.cos(a) * 0.05, 0.06, Math.sin(a) * 0.05).color(0xFFFFFF, BLOSSOM).size(0.3F, 0)
                    .randomAngle().spin(0.2F).life(14).spawn();
        }
        for (Vec3 v : p.pts()) Fx.p(S.PETAL, v.add(0, 1.2, 0)).vel(0, 0.05, 0).color(0xFFFFFF, BLOSSOM).size(0.25F, 0).life(12).spawn();
    }

    private static void bloomBurst(P p) {
        Vec3 o = p.pos();
        float r = Math.max(3, p.a());
        FxAnims.shake(o, 1.0F, 8, 16);
        flash(o.add(0, 1, 0), BLOSSOM, 2.6F, 8);
        // a storm of petals spiralling out
        for (int i = 0, n = Fx.n(70); i < n; i++) {
            double a = Fx.r(0, Math.PI * 2);
            Vec3 v = new Vec3(Math.cos(a + 0.8), Fx.r(0.1, 0.5), Math.sin(a + 0.8)).scale(Fx.r(0.3, 0.8));
            Fx.p(i % 3 == 0 ? S.SAKURA : S.PETAL, o.add(Math.cos(a) * 0.5, 0.6, Math.sin(a) * 0.5)).vel(v).friction(0.9F).gravity(0.05F).color(0xFFFFFF, BLOSSOM)
                    .size(0.35F, 0.1F).randomAngle().spin(Fx.rf(-0.3F, 0.3F)).life(30 + Fx.RND.nextInt(20)).spawn();
        }
        ground(S.WAVE, o, 1, BLOSSOM, 14).size(1, r * 2).easeOut().color(0xFFFFFF, BLOSSOM).alpha(0.9F, 0).spawn();
        hits(p.pts(), BLOSSOM, 1);
    }

    // ================================================================== sentinel: fortress, holy lance

    private static final int FORT = 0xF0D890;

    private static void fortress(P p) {
        Entity e = p.e();
        Vec3 o = feetOf(p);
        float r = Math.max(3, p.a());
        int ticks = Math.max(20, (int) (p.b() * 20));
        FxAnims.shake(o, 1.0F, 8, 16);
        // a palisade of spears rises round the sentinel and stands for the whole time
        int n = 16;
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            Vec3 at = o.add(Math.cos(a) * 2.2, 0.9, Math.sin(a) * 2.2);
            Fx.p(S.BLADE, at).upright().vel(0, 0.0, 0).color(0xFFFFFF, FORT).size(0.2F, 1.0F).easeOut().alpha(0.95F, 0.6F).life(ticks).delay(i).spawn();
            debris(at.subtract(0, 0.9, 0), 2, 0.1, 0.3);
        }
        ground(S.CIRCLE_HOLY, o, r, FORT, ticks).color(0xFFFFFF, FORT).alpha(0.5F, 0.1F).spin(0.01F).spawn();
        for (Vec3 v : p.pts()) Fx.p(S.CREST, v.add(0, 1.2, 0)).color(0xFFFFFF, FORT).size(0.3F, 0.6F).alpha(1, 0).life(24).spawn();
    }

    private static void fortressBurst(P p) {
        Vec3 o = p.pos();
        float r = Math.max(3, p.a());
        FxAnims.shake(o, 2.0F, 14, 24);
        flash(o.add(0, 1, 0), FORT, 3, 8);
        // the spears fly out on every side
        for (int i = 0; i < 16; i++) {
            double a = Math.PI * 2 * i / 16;
            Vec3 d = new Vec3(Math.cos(a), 0, Math.sin(a));
            Vec3 at = o.add(d.scale(2.2)).add(0, 0.9, 0);
            Fx.p(S.BLADE, at).fixed(Fx.basis(new Vec3(0, 1, 0).cross(d), d)).vel(d.scale(r * 0.12)).friction(0.9F).color(0xFFFFFF, FORT).size(1.0F).alpha(1, 0).life(12).spawn();
        }
        ground(S.WAVE, o, 1, FORT, 12).size(1, r * 2).easeOut().color(0xFFFFFF, FORT).alpha(1, 0).spawn();
        ground(S.CRACK, o, r * 0.8F, FORT, 40).color(0xFFFFFF, FORT).alpha(0.9F, 0).randomAngle().spawn();
        hits(p.pts(), FORT, 1);
    }

    private static void holyLance(P p) {
        Vec3 a = p.pos(), b = p.vec();
        Vec3 d = b.subtract(a);
        double len = Math.max(0.5, d.length());
        Vec3 dir = d.scale(1 / len);
        FxAnims.shake(a, 1.0F, 6, 14);
        // a lance of light flies the line, leaving a trail of crosses
        FxAnims.run(6, age -> {
            double t = (age + 1) / 6.0;
            Vec3 at = a.add(dir.scale(len * t));
            Fx.p(S.BLADE, at).fixed(Fx.axial(at, dir)).color(0xFFFFFF, FORT).size(1.8F).alpha(1, 0).life(3).spawn();
            Fx.p(S.FLARE, at).color(FORT).size(1.6F, 0.6F).alpha(0.6F, 0).life(5).spawn();
            return false;
        });
        line(a, b, 0.8, (v, t) -> {
            Fx.p(S.GLOW, v).color(0xFFFFFF, FORT).size(0.6F, 0).life(14).delay((int) (t * 6)).spawn();
            if (Fx.RND.nextInt(3) == 0) Fx.p(S.CROSS, v).vel(0, 0.04, 0).color(0xFFFFFF, FORT).size(0.3F, 0).life(16).delay((int) (t * 6)).spawn();
        });
        FxAnims.later(6, () -> {
            flash(b, FORT, 2.4F, 7);
            for (int i = 0; i < 6; i++) Fx.p(S.BEAM, b.add(0, i * 1.0, 0)).upright().color(0xFFFFFF, FORT).size(1.3F, 0.5F).alpha(0.9F, 0).life(14).delay(i).spawn();
        });
        hits(p.pts(), FORT, 2);
    }

    // ================================================================== spellsinger: grand concerto, sealing chord

    private static final int SINGER = 0x9A6AD8;

    private static void grandConcerto(P p) {
        Entity e = p.e();
        Vec3 o = feetOf(p);
        float r = Math.max(4, p.a());
        flash(o.add(0, 1.4, 0), SINGER, 2.6F, 8);
        // the songs rise round the singer in a spiral and crown them
        for (int i = 0; i < 24; i++) {
            double a = i * 0.5;
            Vec3 at = o.add(Math.cos(a) * 1.2, 0.2 + i * 0.12, Math.sin(a) * 1.2);
            Fx.p(S.NOTE, at).vel(0, 0.02, 0).color(0xFFFFFF, i % 2 == 0 ? SINGER : 0xFFD070).size(0.4F, 0.1F).alpha(1, 0).life(30).delay(i).follow(e).spawn();
        }
        FxAnims.later(24, () -> {
            Vec3 top = (e != null ? e.position() : o).add(0, 3.2, 0);
            for (int i = 0; i < 8; i++) {
                double a = Math.PI * 2 * i / 8;
                Fx.p(S.TWINKLE, top.add(Math.cos(a) * 0.6, 0, Math.sin(a) * 0.6)).color(0xFFFFFF, 0xFFD070).size(0.4F, 0).life(20).spawn();
            }
            Fx.p(S.RING, top).flat().color(0xFFFFFF, 0xFFD070).size(1.4F, 0.9F).alpha(1, 0).life(20).spawn();
        });
        ground(S.WAVE, o, 1, SINGER, 16).size(1, r * 2).easeOut().color(0xFFFFFF, SINGER).alpha(0.9F, 0).spawn();
    }

    private static void sealing(P p) {
        Vec3 o = p.pos();
        flash(o.add(0, 1.4, 0), SINGER, 2.2F, 7);
        // the staff closes: chains of notes snap shut round the singer
        for (int i = 0; i < 12; i++) {
            double a = Math.PI * 2 * i / 12;
            Vec3 at = o.add(Math.cos(a) * 1.4, 1.2, Math.sin(a) * 1.4);
            Fx.p(S.CHAIN, at).fixed(Fx.basis(new Vec3(-Math.sin(a), 0, Math.cos(a)), new Vec3(0, 1, 0))).color(0xFFFFFF, SINGER).size(0.45F, 0.2F).alpha(1, 0).life(16)
                    .delay(i).spawn();
        }
    }

    private static void sealHit(P p) {
        Entity e = p.e();
        Vec3 c = e != null ? e.position().add(0, e.getBbHeight() * 0.55, 0) : p.pos();
        Fx.p(S.SPARK, c).color(0xFFFFFF, SINGER).size(0.8F, 0).randomAngle().life(5).spawn();
        int life = Math.max(10, (int) p.b());
        // chains round the silenced enemy
        for (int k = 0; k < 2; k++) {
            int kk = k;
            FxAnims.follow(e, life, age -> {
                if (age % 3 != 0) return false;
                double a = age * 0.2 + kk * Math.PI;
                Vec3 at = e.position().add(Math.cos(a) * 0.7, e.getBbHeight() * (0.3 + 0.4 * kk), Math.sin(a) * 0.7);
                Fx.p(S.CHAIN, at).fixed(Fx.basis(new Vec3(-Math.sin(a), 0, Math.cos(a)), new Vec3(0, 1, 0))).color(0xFFFFFF, SINGER).size(0.3F).alpha(0.9F, 0).life(4).spawn();
                return false;
            });
        }
    }

    // ================================================================== shadow hunter: night hunt, shadow rain's mark

    private static final int MOON = 0xC0D0FF, PREY = 0xC03040;

    private static void nightHunt(P p) {
        Entity e = p.e();
        Vec3 o = feetOf(p);
        // the moon comes up over the hunt, and the dark closes in
        Fx.p(S.MOON, o.add(0, 7, 0)).color(0xFFFFFF, MOON).size(0.5F, 3.2F).easeOut().alpha(1, 0).fadeIn(0.1F).life(80).spawn();
        Fx.p(S.FLARE, o.add(0, 7, 0)).color(MOON).size(5F).alpha(0.4F, 0).life(80).spawn();
        for (int i = 0, n = Fx.n(30); i < n; i++) {
            double a = Fx.r(0, Math.PI * 2);
            smoke(o.add(Math.cos(a) * 3, 0.5, Math.sin(a) * 3), 0x10141E, 1.2F, 2.6F, 40).vel(-Math.cos(a) * 0.05, 0.01, -Math.sin(a) * 0.05).alpha(0.7F, 0).spawn();
        }
    }

    /** A hunted enemy: a red eye watching over it while the hunt lasts. */
    private static void hunted(P p) {
        Entity e = p.e();
        int life = Math.max(20, (int) (p.a() * 20));
        FxAnims.follow(e, life, age -> {
            if (age % 4 != 0) return false;
            Vec3 h = e.position().add(0, e.getBbHeight() + 0.6, 0);
            Fx.p(S.EYE, h).color(0xFFFFFF, PREY).size(0.5F).alpha(age % 20 < 4 ? 0.4F : 0.9F, 0.9F).life(4).spawn();
            return false;
        });
    }

    private static void shadowMark(P p) {
        Entity e = p.e();
        int life = Math.max(20, (int) (p.a() * 20));
        FxAnims.follow(e, life, age -> {
            if (age % 3 != 0) return false;
            Vec3 f = e.position();
            Fx.p(S.CIRCLE_HEX, f.add(0, 0.05, 0)).flat().color(0xFFFFFF, 0x6070C0).size(e.getBbWidth() + 1.2F).angle(age * 0.1F).alpha(0.7F, 0.7F).life(3).spawn();
            return false;
        });
    }

    // ================================================================== treasure hunter: treasure rain, golden cannon

    private static final int GILT = 0xFFD040;

    private static void treasureZone(P p) {
        Vec3 o = p.pos();
        float r = Math.max(3, p.a());
        int life = Math.max(20, (int) p.b());
        // a ring of coins turning round the area, a glow in the clouds
        FxParticle[] coins = new FxParticle[12];
        for (int i = 0; i < coins.length; i++) coins[i] = Fx.p(S.COIN, o).coin().size(0.35F).alpha(1, 1).life(life).spawn();
        FxAnims.run(life, age -> {
            for (int i = 0; i < coins.length; i++) {
                if (coins[i] == null) continue;
                double a = age * 0.05 + Math.PI * 2 * i / coins.length;
                coins[i].moveTo(o.x + Math.cos(a) * r, o.y + 0.4 + Math.sin(age * 0.2 + i) * 0.15, o.z + Math.sin(a) * r);
            }
            return false;
        });
        Fx.p(S.FLARE, o.add(0, 9, 0)).color(GILT).size(r * 1.4F).alpha(0.5F, 0).life(life).spawn();
        ground(S.CIRCLE_HOLY, o, r, GILT, life).color(0xFFFFFF, GILT).alpha(0.5F, 0).spin(0.02F).spawn();
    }

    private static void coinPick(P p) {
        Vec3 at = p.pos();
        for (int i = 0, n = Fx.n(8); i < n; i++) {
            Fx.p(S.COIN, at).vel(Fx.randomUnit().multiply(0.2, 0, 0.2).add(0, 0.3, 0)).gravity(0.8F).coin().size(0.2F).alpha(1, 0).life(16).spawn();
        }
        Fx.p(S.TWINKLE, at.add(0, 0.6, 0)).color(0xFFFFFF, GILT).size(0.9F, 0).life(8).spawn();
    }

    private static void goldenShot(P p) {
        Vec3 at = p.pos();
        Vec3 dir = p.vec().normalize();
        FxAnims.shake(at, 1.0F, 6, 10);
        flash(at.add(dir.scale(0.6)), GILT, 2.0F, 6);
        for (int i = 0, n = Fx.n(14); i < n; i++) {
            Vec3 v = dir.add(Fx.randomUnit().scale(0.4)).normalize().scale(Fx.r(0.3, 0.7));
            Fx.p(S.COIN, at.add(dir.scale(0.6))).vel(v).gravity(0.7F).coin().size(0.18F).alpha(1, 0).life(20).spawn();
        }
        smoke(at.add(dir.scale(0.8)), 0x605030, 0.6F, 1.6F, 26).vel(dir.scale(0.05)).alpha(0.6F, 0).spawn();
    }

    private static void goldenBoom(P p) {
        Vec3 at = p.pos();
        float r = Math.max(2, p.a());
        FxAnims.shake(at, 2.0F, 14, 24);
        flash(at, GILT, r * 1.2F, 9);
        Fx.vanilla(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 0, 0, 0);
        // a fountain of gold
        for (int i = 0, n = Fx.n(40); i < n; i++) {
            Vec3 v = Fx.randomUnit().multiply(0.6, 0, 0.6).add(0, Fx.r(0.4, 0.9), 0);
            Fx.p(S.COIN, at).vel(v.scale(Fx.r(0.5, 1.0))).gravity(0.9F).coin().size(Fx.rf(0.18F, 0.3F)).alpha(1, 0.8F).life(30 + Fx.RND.nextInt(15)).spawn();
        }
        burst(at, 30, 0.6, S.TWINKLE, 0xFFFFFF, GILT, 0.4F, 18, 0.1F);
        Fx.p(S.RING, at).flat().color(0xFFFFFF, GILT).size(1, r * 2).easeOut().alpha(1, 0).life(12).spawn();
    }
}
