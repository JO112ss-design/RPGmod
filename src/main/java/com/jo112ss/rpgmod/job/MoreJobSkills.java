package com.jo112ss.rpgmod.job;

import static com.jo112ss.rpgmod.job.SkillExecutor.*;

import com.jo112ss.rpgmod.job.SkillExecutor.Ctx;
import com.jo112ss.rpgmod.job.fx.ServerFx;
import com.jo112ss.rpgmod.registry.ModEffects;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Skills of the captain, champion, dragoon and maestro (advanced jobs of the pirate, monk, lancer and
 * bard) and of the six hybrid jobs. Shares the helpers of {@link SkillExecutor} and
 * {@link NewJobSkills}; the lasting effects are read by {@link MoreJobHooks}.
 */
public final class MoreJobSkills {
    private MoreJobSkills() {}

    static final Set<String> IDS = Set.of(
            "twin_cannon", "scattershot", "anchor_throw", "rally", "deadshot", "fleet_barrage",
            "rising_dragon", "ki_wave", "iron_mountain", "foresight", "battle_aura", "peerless",
            "high_jump", "dragon_spear", "dragon_scale", "spiral_lance", "sky_step", "dragon_king",
            "duet", "modulate", "heroic_song", "requiem", "shock_chord", "symphony",
            "element_shift", "magic_slash", "thunder_thrust", "frost_blade", "flame_wheel", "spell_guard", "arcane_edge", "elemental_burst",
            "qi_bullet", "refine_qi", "healing_palm", "hard_qi", "qi_burst", "flowing_step", "heaven_earth", "blooming_fists",
            "guard_thrust", "shield_charge", "immovable", "spear_wall", "counter_lance", "provoke", "fortress", "holy_lance",
            "resonance", "grand_concerto", "sealing_chord",
            "vital_shot", "stealth", "shadow_arrow", "hunter_trap", "shadow_leap", "fan_knives", "night_hunt", "shadow_rain",
            "gamble_cannon", "coin_shot", "mimic", "steal", "lucky_compass", "double_down", "treasure_rain", "golden_cannon");

    static boolean has(String id) {
        return IDS.contains(id);
    }

    static boolean run(Ctx c) {
        return switch (c.def().id()) {
            // captain
            case "twin_cannon" -> {
                NewJobSkills.placeCannon(c, NewJobSkills.beside(c, -1.6, 0.8), Pirate.CANNONS2, 2);
                yield true;
            }
            case "scattershot" -> scattershot(c);
            case "anchor_throw" -> anchorThrow(c);
            case "rally" -> party(c, 0xFFB040, 1, SoundEvents.BELL_BLOCK, 0.8F);
            case "deadshot" -> deadshot(c);
            case "fleet_barrage" -> fleetBarrage(c);
            // champion
            case "rising_dragon" -> risingDragon(c);
            case "ki_wave" -> kiWave(c);
            case "iron_mountain" -> ironMountain(c);
            case "foresight" -> mark(c, ModEffects.FORESIGHT, FORESIGHT_POWER, 0xFFB070, 3);
            case "battle_aura" -> mark(c, ModEffects.BATTLE_AURA, null, 0xFF8030, 1);
            case "peerless" -> peerless(c);
            // dragoon
            case "high_jump" -> dive(c, 1.7, 14, 18, 2.6, 0xC05030, false);
            case "dragon_spear" -> dragonSpear(c);
            case "dragon_scale" -> mark(c, null, null, 0xD04030, 2);
            case "spiral_lance" -> spiralLance(c);
            case "sky_step" -> skyStep(c);
            case "dragon_king" -> dive(c, 2.2, 20, 26, 3.2, 0xFF6020, true);
            // maestro
            case "duet" -> mark(c, ModEffects.DUET, null, 0x5FD3BC, 4);
            case "modulate" -> Bard.modulate(c);
            case "heroic_song" -> heroicSong(c);
            case "requiem" -> requiem(c);
            case "shock_chord" -> shockChord(c);
            case "symphony" -> symphony(c);
            // spellblade
            case "element_shift" -> elementShift(c);
            case "magic_slash" -> magicSlash(c);
            case "thunder_thrust" -> thunderThrust(c);
            case "frost_blade" -> frostBlade(c);
            case "flame_wheel" -> flameWheel(c);
            case "spell_guard" -> mark(c, ModEffects.SPELL_GUARD, GUARD_POWER, ELEMENT_COLORS[element(c.p())], 2);
            case "arcane_edge" -> arcaneEdge(c);
            case "elemental_burst" -> elementalBurst(c);
            // qi master
            case "qi_bullet" -> qiBullet(c);
            case "refine_qi" -> refineQi(c);
            case "healing_palm" -> {
                heal(c, "heal", false);
                Monk.add(c.p(), 1);
                yield true;
            }
            case "hard_qi" -> hardQi(c);
            case "qi_burst" -> qiBurst(c);
            case "flowing_step" -> flowingStep(c);
            case "heaven_earth" -> heavenEarth(c);
            case "blooming_fists" -> bloomingFists(c);
            // sentinel
            case "guard_thrust" -> guardThrust(c);
            case "shield_charge" -> shieldCharge(c);
            case "immovable" -> {
                c.p().addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks(c.def().duration()), 1, false, false), c.p());
                yield mark(c, ModEffects.IMMOVABLE, null, 0xC0B080, 2);
            }
            case "spear_wall" -> spearWall(c);
            case "counter_lance" -> mark(c, ModEffects.COUNTER_LANCE, LANCE_POWER, 0xE0C080, 2);
            case "provoke" -> provoke(c);
            case "fortress" -> fortress(c);
            case "holy_lance" -> holyLance(c);
            // spellsinger (the five songs are started by NewJobSkills)
            case "resonance" -> resonance(c);
            case "grand_concerto" -> grandConcerto(c);
            case "sealing_chord" -> sealingChord(c);
            // shadow hunter
            case "vital_shot" -> vitalShot(c);
            case "stealth" -> stealth(c);
            case "shadow_arrow" -> shadowArrow(c);
            case "hunter_trap" -> hunterTrap(c);
            case "shadow_leap" -> shadowLeap(c);
            case "fan_knives" -> fanKnives(c);
            case "night_hunt" -> nightHunt(c);
            case "shadow_rain" -> shadowRain(c);
            // treasure hunter
            case "gamble_cannon" -> gambleCannon(c);
            case "coin_shot" -> coinShot(c);
            case "mimic" -> mimic(c);
            case "steal" -> steal(c);
            case "lucky_compass" -> {
                apply(c.p(), c.def(), c.p());
                yield mark(c, ModEffects.TREASURE, null, 0xD4AF37, 5);
            }
            case "double_down" -> mark(c, ModEffects.DOUBLE_DOWN, null, 0xE8C040, 5);
            case "treasure_rain" -> treasureRain(c);
            case "golden_cannon" -> goldenCannon(c);
            default -> false;
        };
    }

    // ================================================================== shared bits

    private static final int KI = 0xFFC860, GOLD = 0xD4AF37, SHADOW = 0x4A5A80, HOLY = 0xFFE89A;
    /** Counter damage set when the skill was used (foresight, counter lance, spell guard). */
    static final Map<UUID, Float> FORESIGHT_POWER = new HashMap<>(), LANCE_POWER = new HashMap<>(), GUARD_POWER = new HashMap<>();

    private static Vec3 mid(LivingEntity e) {
        return NewJobSkills.mid(e);
    }

    /** A lasting mark on the caster (and the skill's listed effects) with an aura. */
    private static boolean mark(Ctx c, Holder<MobEffect> h, Map<UUID, Float> power, int color, int style) {
        ServerPlayer p = c.p();
        int t = ticks(c.def().duration());
        if (h != null) p.addEffect(new MobEffectInstance(h, t, 0), p);
        apply(p, c.def(), p);
        if (power != null) power.put(p.getUUID(), c.dmg(1));
        ServerFx.of("x_aura", p.position()).follow(p).a(c.def().duration()).b(style).color(color).send(c.level());
        sound(c, SoundEvents.BEACON_POWER_SELECT, 1.4F);
        sound(c, p.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.6F, 1.2F);
        return true;
    }

    /** The skill's listed effects on everyone around. */
    private static boolean party(Ctx c, int color, int style, net.minecraft.sounds.SoundEvent s, float pitch) {
        List<ServerPlayer> allies = allies(c.p(), c.def().radius());
        for (ServerPlayer a : allies) apply(a, c.def(), c.p());
        ServerFx.of("x_aura", c.p().position()).follow(c.p()).a(Math.min(6, c.def().duration())).b(style).color(color).points(others(c.p(), allies)).send(c.level());
        ServerFx.of("x_ring", c.p().position()).a(c.def().radius()).b(1).color(color).send(c.level());
        sound(c, s, pitch);
        return true;
    }

    /** The first enemy along a line from the eyes (and where the line stops). */
    private record Ray(LivingEntity hit, Vec3 end) {}

    private static Ray ray(Ctx c, Vec3 dir, double range) {
        Vec3 eye = c.p().getEyePosition();
        dir = dir.normalize();
        BlockHitResult block = c.level().clip(new ClipContext(eye, eye.add(dir.scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, c.p()));
        Vec3 end = block.getType() == HitResult.Type.MISS ? eye.add(dir.scale(range)) : block.getLocation();
        LivingEntity hit = null;
        double best = eye.distanceToSqr(end);
        for (LivingEntity e : c.level().getEntitiesOfClass(LivingEntity.class, new AABB(eye, end).inflate(1), e -> isEnemy(c.p(), e))) {
            var at = e.getBoundingBox().inflate(0.3).clip(eye, end);
            if (at.isPresent() && at.get().distanceToSqr(eye) < best) {
                best = at.get().distanceToSqr(eye);
                hit = e;
            }
        }
        return new Ray(hit, hit == null ? end : eye.add(dir.scale(Math.sqrt(best))));
    }

    /** Every enemy within {@code width} of the line from the eyes along {@code dir} (up to {@code range}, stopped by walls). */
    private static List<LivingEntity> line(Ctx c, Vec3 dir, double range, double width) {
        Vec3 eye = c.p().getEyePosition();
        dir = dir.normalize();
        BlockHitResult block = c.level().clip(new ClipContext(eye, eye.add(dir.scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, c.p()));
        Vec3 end = block.getType() == HitResult.Type.MISS ? eye.add(dir.scale(range)) : block.getLocation();
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : c.level().getEntitiesOfClass(LivingEntity.class, new AABB(eye, end).inflate(width + 1), e -> isEnemy(c.p(), e))) {
            if (e.getBoundingBox().inflate(width).clip(eye, end).isPresent() || e.getBoundingBox().inflate(width).contains(eye)) out.add(e);
        }
        return out;
    }

    private static Vec3 lineEnd(Ctx c, Vec3 dir, double range) {
        Vec3 eye = c.p().getEyePosition();
        BlockHitResult block = c.level().clip(new ClipContext(eye, eye.add(dir.normalize().scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, c.p()));
        return block.getType() == HitResult.Type.MISS ? eye.add(dir.normalize().scale(range)) : block.getLocation();
    }

    // ================================================================== captain

    /** Seven pellets in a fan. */
    private static boolean scattershot(Ctx c) {
        Vec3 look = c.look();
        for (int i = 0; i < 7; i++) {
            Vec3 dir = Fx3.rotY(look, Math.toRadians((i - 3) * 6)).add(0, (c.p().getRandom().nextDouble() - 0.5) * 0.06, 0);
            Pirate.shot(c.p(), c.dmg(1), dir, c.def().radius(), 0.01);
        }
        launch(c.p(), c.flat().scale(-0.35).add(0, 0.1, 0));
        sound(c, SoundEvents.GENERIC_EXPLODE, 1.3F);
        return true;
    }

    /** Throws the anchor (an anvil on a chain): where it lands it hits and drags everything in. */
    private static boolean anchorThrow(Ctx c) {
        ServerPlayer p = c.p();
        Vec3 target = aimPoint(c, 14);
        Vec3 start = c.hand();
        Vec3 d = target.subtract(start);
        SkillProp anchor = SkillProp.create(c.level(), SkillProp.ANCHOR, start, p.getYRot(), 80);
        anchor.setDeltaMovement(new Vec3(d.x / 12, d.y / 12 + 0.32, d.z / 12));
        ServerFx.of("n_chain", start).follow(p).a(anchor.getId()).b(55).send(c.level());
        sound(c, SoundEvents.CHAIN_PLACE, 0.7F);
        sound(c, p.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.8F, 0.6F);
        double r = c.def().radius();
        SkillTasks.run(age -> {
            if (anchor.isRemoved()) return true;
            if (!((age > 3 && anchor.onGround()) || age >= 30)) return false;
            Vec3 o = anchor.position();
            List<LivingEntity> hit = enemies(p, o, r);
            for (LivingEntity e : hit) {
                hurt(c, e, c.dmg(1), melee(c));
                push(e, o.subtract(e.position()), Math.min(1.2, e.position().distanceTo(o) * 0.3), 0.25);
                e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 2), p);
            }
            ServerFx.of("x_ring", o).a(r).b(2).color(0xA0A8B0).points(hit).send(c.level());
            ServerFx.of("x_burst", o.add(0, 0.3, 0)).a(1.6).b(3).color(0x707880).send(c.level());
            sound(c, o, SoundEvents.ANVIL_LAND, 1.2F, 0.6F);
            sound(c, o, SoundEvents.MACE_SMASH_GROUND_HEAVY, 0.8F, 0.8F);
            SkillTasks.later(20, anchor::discard);
            return true;
        });
        return true;
    }

    /** Takes half a second to aim, then a heavy shot; costs three rounds (none under the flag). */
    private static boolean deadshot(Ctx c) {
        ServerPlayer p = c.p();
        JobData d = JobManager.data(p);
        boolean endless = Pirate.flagUp(p);
        if (!endless && d.ammo() < 3) {
            fail(p, "rpgmod.pirate.need_rounds");
            return false;
        }
        if (!endless) {
            d.setAmmo(d.ammo() - 3);
            JobManager.sync(p);
        }
        ServerFx.of("x_aim", p.position()).follow(p).b(10).color(0xFFB040).send(c.level());
        sound(c, SoundEvents.CROSSBOW_LOADING_MIDDLE, 0.7F);
        SkillTasks.later(10, () -> {
            if (!p.isAlive()) return;
            LivingEntity hit = Pirate.shot(p, c.dmg(1), p.getLookAngle(), c.def().radius(), 0);
            if (hit != null) push(hit, p.getLookAngle(), 1.0, 0.3);
            sound(c, p.position(), SoundEvents.GENERIC_EXPLODE, 0.9F, 0.8F);
            if (!endless && JobManager.data(p).ammo() <= 0) Pirate.reload(p, JobManager.data(p));
        });
        return true;
    }

    /**
     * Three ghost ships appear in the sky behind you, side on to the aimed area; each fires at an
     * enemy there in turn (they share out the targets). Your own cannons fire flat out meanwhile.
     */
    private static boolean fleetBarrage(Ctx c) {
        ServerPlayer p = c.p();
        Vec3 o = aimPoint(c, 32);
        double r = c.def().radius();
        int life = ticks(c.def().duration());
        Vec3 dir = c.flat(), side = Fx3.right(dir);
        Pirate.BARRAGE_UNTIL.put(p.getUUID(), c.level().getGameTime() + life);
        // the ships hang in the air around the far side of the area, each side on to it
        Vec3[] ships = new Vec3[3];
        for (int i = 0; i < 3; i++) {
            Vec3 out = Fx3.rotY(dir, Math.toRadians((i - 1) * 60));
            ships[i] = o.add(out.scale(r + 1)).add(0, 8 + (i == 1 ? 1.5 : 0), 0);
            ServerFx.of("n_ghost_ship", ships[i]).vec(Fx3.right(out)).b(life + 20).send(c.level());
        }
        ServerFx.of("x_zone", o).a(r).b(life + 10).vec(new Vec3(2, 4, 0)).color(0x7FE8D8).send(c.level());
        sound(c, SoundEvents.BELL_BLOCK, 0.5F);
        sound(c, p.position(), SoundEvents.ELDER_GUARDIAN_CURSE, 0.6F, 1.4F);
        final int[] turn = {0};
        SkillTasks.run(age -> {
            if (age >= 10 && age < life && (age - 10) % 6 == 0) {
                int i = turn[0]++ % 3;
                List<LivingEntity> foes = enemies(p, o, r + 2);
                Vec3 at = foes.isEmpty() ? NewJobSkills.spotIn(c, o, r, foes) : foes.get(turn[0] % foes.size()).position();
                Vec3 from = ships[i].add(at.subtract(ships[i]).multiply(1, 0, 1).normalize().scale(1.6)).add(0, 1.0, 0);
                ServerFx.of("n_cannon_fire", from).vec(at.subtract(from)).send(c.level());
                NewJobSkills.shell(c, from, at, 12, 2.2, c.dmg(1));
                sound(c, from, SoundEvents.GENERIC_EXPLODE, 1.0F, 0.6F);
            }
            return age >= life;
        });
        return true;
    }

    // ================================================================== champion

    /** An uppercut that throws the enemies in front into the air. */
    private static boolean risingDragon(Ctx c) {
        ServerPlayer p = c.p();
        List<LivingEntity> hit = cone(c, c.def().radius(), 50);
        for (LivingEntity e : hit) {
            hurt(c, e, c.dmg(1), melee(c));
            push(e, c.flat(), 0.2, 0.95);
        }
        if (!hit.isEmpty()) Monk.add(p, 1);
        launch(p, c.flat().scale(0.2).add(0, 0.55, 0));
        NewJobSkills.SOFT_LANDING.add(p.getUUID());
        SkillTasks.later(30, () -> NewJobSkills.SOFT_LANDING.remove(p.getUUID()));
        ServerFx.of("x_slash", c.chest().add(c.flat().scale(1.4))).vec(c.flat()).a(3).b(1.5).color(KI).points(hit).send(c.level());
        ServerFx.of("x_pillar", p.position().add(c.flat().scale(1.4))).a(0.8).b(10).color(KI).send(c.level());
        sound(c, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.8F);
        sound(c, p.position(), SoundEvents.BREEZE_JUMP, 0.8F, 1.2F);
        return true;
    }

    /** A beam of ki through everything in a line: +80% per point of ki spent (all of it). */
    private static boolean kiWave(Ctx c) {
        ServerPlayer p = c.p();
        int ki = Monk.spend(p);
        double mult = 1 + 0.8 * ki;
        List<LivingEntity> hit = line(c, c.look(), c.def().radius(), 0.6 + 0.08 * ki);
        for (LivingEntity e : hit) {
            hurt(c, e, c.dmg(mult), melee(c));
            push(e, c.look(), 0.6, 0.2);
        }
        ServerFx.of("x_beam", c.hand()).vec(lineEnd(c, c.look(), c.def().radius())).a(0.3 + 0.08 * ki).color(KI).points(hit).send(c.level());
        sound(c, SoundEvents.WARDEN_SONIC_BOOM, 1.4F);
        return true;
    }

    /** A shoulder charge: the first enemy met is thrown far. */
    private static boolean ironMountain(Ctx c) {
        ServerPlayer p = c.p();
        Vec3 dir = c.flat();
        launch(p, dir.scale(2.0).add(0, 0.1, 0));
        ServerFx.of("x_dash", p.position()).vec(p.position().add(dir.scale(c.def().radius()))).color(KI).send(c.level());
        sound(c, SoundEvents.BREEZE_JUMP, 0.9F);
        SkillTasks.run(age -> {
            if (!p.isAlive()) return true;
            Vec3 front = p.position().add(dir.scale(0.9));
            LivingEntity t = NewJobSkills.nearest(enemies(p, front, 1.8), front);
            if (t == null) return age >= 6;
            hurt(c, t, c.dmg(1), melee(c));
            push(t, dir, 1.9, 0.45);
            launch(p, dir.scale(0.1));
            Monk.add(p, 1);
            ServerFx.of("x_burst", mid(t)).a(1.4).b(3).color(KI).send(c.level());
            sound(c, t.position(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.2F, 0.5F);
            return true;
        });
        return true;
    }

    /** All the ki at once: three strikes, two more per point of ki, the last one heavy. */
    private static boolean peerless(Ctx c) {
        ServerPlayer p = c.p();
        int ki = Monk.spend(p);
        int strikes = 3 + 2 * ki;
        ServerFx.of("x_aura", p.position()).follow(p).a(strikes * 0.15 + 1).b(1).color(0xFF6020).send(c.level());
        sound(c, SoundEvents.RAVAGER_ROAR, 1.5F);
        SkillTasks.run(age -> {
            if (!p.isAlive()) return true;
            if (age % 3 != 0) return false;
            int n = age / 3;
            boolean last = n == strikes - 1;
            List<LivingEntity> hit = cone(c, c.def().radius(), 50);
            for (LivingEntity e : hit) {
                hurt(c, e, c.dmg(last ? 3 : 1), melee(c));
                push(e, c.flat(), last ? 1.4 : 0.08, last ? 0.5 : 0.02);
            }
            ServerFx.of("n_fist", c.chest().add(c.flat().scale(1.6))).vec(c.flat()).color(last ? 0xFFF0C0 : KI).points(hit).send(c.level());
            sound(c, p.position(), last ? SoundEvents.PLAYER_ATTACK_KNOCKBACK : SoundEvents.PLAYER_ATTACK_STRONG, 0.8F, last ? 0.5F : 1.0F + n * 0.04F);
            if (last) ServerFx.of("x_burst", c.chest().add(c.flat().scale(2))).a(2.2).b(3).color(0xFF8030).send(c.level());
            return last;
        });
        return true;
    }

    // ================================================================== dragoon

    /**
     * Up into the sky (untouchable there), then down on the spot aimed at when falling begins: the
     * high jump, and the dragon king (higher, wider, on fire).
     */
    private static boolean dive(Ctx c, double up, int apex, double reach, double speed, int color, boolean king) {
        ServerPlayer p = c.p();
        launch(p, new Vec3(0, up, 0));
        p.addEffect(new MobEffectInstance(ModEffects.UNTOUCHABLE, apex + 10, 0, false, false, true), p);
        NewJobSkills.SOFT_LANDING.add(p.getUUID());
        ServerFx.of("x_aura", p.position()).follow(p).a(2).b(3).color(color).send(c.level());
        sound(c, king ? SoundEvents.ENDER_DRAGON_GROWL : SoundEvents.ENDER_DRAGON_FLAP, king ? 1.2F : 1.0F);
        sound(c, p.position(), SoundEvents.BREEZE_JUMP, 1.0F, 0.6F);
        SkillTasks.run(age -> {
            if (!p.isAlive()) return true;
            p.resetFallDistance();
            if (age == apex) {
                Vec3 target = aimPoint(c, reach);
                Vec3 v = target.subtract(p.position()).normalize().scale(speed);
                launch(p, v.y > -0.5 ? v.add(0, -1.2, 0) : v);
                ServerFx.of("x_dash", p.position()).vec(target).color(color).send(c.level());
                sound(c, p.position(), SoundEvents.TRIDENT_THROW, 1.0F, 0.5F);
            }
            if (age <= apex + 1 || (!p.onGround() && age < apex + 40)) return false;
            double r = c.def().radius();
            Vec3 o = p.position();
            List<LivingEntity> hit = enemies(p, o, r);
            for (LivingEntity e : hit) {
                double fall = 1 - 0.4 * Math.min(1, horizontal(e.position(), o) / r);
                hurt(c, e, c.dmg(fall), melee(c));
                pushAway(e, o, 0.7, 0.7);
                if (king) e.igniteForSeconds(4);
            }
            ServerFx.of("earthshatter", o).a(r).send(c.level());
            ServerFx.of("x_spikes", o).vec(c.flat()).a(Math.max(2, r * 0.6)).color(color).send(c.level());
            if (king) ServerFx.of("x_burst", o.add(0, 0.3, 0)).a(r * 0.7).b(0).color(0xFF6020).points(hit).send(c.level());
            else ServerFx.of("x_ring", o).a(r * 1.3).b(2).color(color).points(hit).send(c.level());
            sound(c, o, SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.4F, king ? 0.5F : 0.8F);
            if (king) sound(c, o, SoundEvents.GENERIC_EXPLODE, 1.4F, 0.6F);
            SkillTasks.later(5, () -> NewJobSkills.SOFT_LANDING.remove(p.getUUID()));
            return true;
        });
        return true;
    }

    /** A thrown spear that blows up on the first thing it meets. */
    private static boolean dragonSpear(Ctx c) {
        ServerPlayer p = c.p();
        Vec3 dir = c.look();
        Vec3 start = c.hand();
        var mat = com.jo112ss.rpgmod.item.ModItems.materialOf(p.getMainHandItem().getItem());
        SkillProp spear = SkillProp.create(c.level(), SkillProp.SPEAR, start, p.getYRot(), 60);
        spear.setVariant(mat != null ? mat.ordinal() : 1);
        spear.aimAt(start.add(dir));
        sound(c, SoundEvents.TRIDENT_THROW, 0.8F);
        final Vec3[] pos = {start};
        final double[] gone = {0};
        double range = c.def().radius(), speed = 1.8;
        SkillTasks.run(age -> {
            if (spear.isRemoved()) return true;
            Vec3 from = pos[0], to = from.add(dir.scale(speed));
            BlockHitResult block = c.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            boolean stop = block.getType() != HitResult.Type.MISS;
            if (stop) to = block.getLocation();
            for (LivingEntity e : c.level().getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(0.8), e -> isEnemy(p, e))) {
                if (e.getBoundingBox().inflate(0.4).clip(from, to).isPresent()) {
                    stop = true;
                    to = mid(e);
                    break;
                }
            }
            gone[0] += speed;
            spear.setPos(to.x, to.y - 0.4, to.z);
            pos[0] = to;
            if (stop || gone[0] >= range) {
                spear.discard();
                NewJobSkills.blast(c, to, 2.5, c.dmg(1), 0, 0xFF6020);
                return true;
            }
            return false;
        });
        return true;
    }

    /** Four turning thrusts that drag the enemies in front onto the spear. */
    private static boolean spiralLance(Ctx c) {
        ServerPlayer p = c.p();
        double r = c.def().radius();
        SkillTasks.run(age -> {
            if (!p.isAlive()) return true;
            if (age % 4 == 0) {
                Vec3 tip = p.position().add(c.flat().scale(r * 0.6));
                List<LivingEntity> hit = cone(c, r, 22);
                for (LivingEntity e : hit) {
                    hurt(c, e, c.dmg(1), melee(c));
                    push(e, tip.subtract(e.position()), 0.3, 0.02);
                }
                ServerFx.of("x_beam", c.hand()).vec(c.hand().add(c.look().scale(r))).a(0.22).color(0xE08050).points(hit).send(c.level());
                ServerFx.of("x_ring", c.hand().add(c.look().scale(r * 0.7))).a(1).b(1).color(0xE08050).send(c.level());
                sound(c, p.position(), SoundEvents.SPEAR_ATTACK, 0.8F, 0.9F + age * 0.04F);
            }
            return age >= 12;
        });
        return true;
    }

    /** A jump off thin air (forward and up); the landing does not hurt. */
    private static boolean skyStep(Ctx c) {
        ServerPlayer p = c.p();
        launch(p, c.flat().scale(1.0).add(0, 0.8, 0));
        NewJobSkills.SOFT_LANDING.add(p.getUUID());
        ServerFx.of("x_ring", p.position()).a(1.2).b(1).color(0xFFD0A0).send(c.level());
        sound(c, SoundEvents.BREEZE_JUMP, 1.4F);
        SkillTasks.run(age -> {
            p.resetFallDistance();
            if (age > 3 && (p.onGround() || age > 100)) {
                SkillTasks.later(5, () -> NewJobSkills.SOFT_LANDING.remove(p.getUUID()));
                return true;
            }
            return false;
        });
        return true;
    }

    // ================================================================== maestro

    /** A big song for one ally (the one you look at, or yourself): +30% damage, tougher, faster. */
    private static boolean heroicSong(Ctx c) {
        ServerPlayer p = c.p();
        ServerPlayer target = p;
        double best = -1;
        for (ServerPlayer a : allies(p, c.def().radius())) {
            if (a == p) continue;
            Vec3 to = a.getEyePosition().subtract(p.getEyePosition());
            double dot = to.normalize().dot(c.look());
            if (dot > 0.9 && dot > best) {
                best = dot;
                target = a;
            }
        }
        int t = ticks(c.def().duration());
        target.addEffect(new MobEffectInstance(ModEffects.HEROIC, t, 0), p);
        apply(target, c.def(), p);
        if (target != p) ServerFx.of("x_link", p.position().add(0, 1.2, 0)).vec(target.position().add(0, 1.2, 0)).color(0xFFD050).send(c.level());
        ServerFx.of("x_aura", target.position()).follow(target).a(c.def().duration()).b(1).color(0xFFD050).send(c.level());
        ServerFx.of("n_notes", target.position()).follow(target).a(2).b(1).color(0xFFE070).send(c.level());
        sound(c, SoundEvents.NOTE_BLOCK_BELL, 1.2F);
        sound(c, p.position(), SoundEvents.PLAYER_LEVELUP, 0.6F, 1.6F);
        return true;
    }

    /** 10s of a quiet song: everyone around (the downed too) gets 4% of their health back each second. */
    private static boolean requiem(Ctx c) {
        ServerPlayer p = c.p();
        int life = ticks(c.def().duration());
        double r = c.def().radius();
        Vec3 o = p.position();
        ServerFx.of("x_zone", o).a(r).b(life).vec(new Vec3(1, 3, 0)).color(0xC8E8FF).send(c.level());
        sound(c, SoundEvents.NOTE_BLOCK_CHIME, 0.6F);
        SkillTasks.run(age -> {
            if (age % 20 == 10) {
                for (ServerPlayer a : c.level().getEntitiesOfClass(ServerPlayer.class, new AABB(o, o).inflate(r, 3, r), a -> a.isAlive() && horizontal(a.position(), o) <= r)) {
                    HealthScale.heal(a, a.getMaxHealth() * 0.04F);
                }
                sound(c, o, SoundEvents.NOTE_BLOCK_FLUTE, 0.6F, 0.6F + (age / 20 % 4) * 0.12F);
            }
            return age >= life;
        });
        return true;
    }

    /** A chord that hits everything around and stuns it for a moment. */
    private static boolean shockChord(Ctx c) {
        ServerPlayer p = c.p();
        double r = c.def().radius();
        List<LivingEntity> hit = enemies(p, p.position(), r);
        for (LivingEntity e : hit) {
            hurt(c, e, c.dmg(1), magic(c));
            e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 25, 5), p);
            pushAway(e, p.position(), 0.5, 0.2);
        }
        ServerFx.of("x_ring", p.position()).a(r).b(3).color(Bard.COLOR).points(hit).send(c.level());
        ServerFx.of("n_finale", p.position()).a(r * 0.6).b(4).color(Bard.COLOR).send(c.level());
        sound(c, SoundEvents.NOTE_BLOCK_BASS, 0.5F);
        sound(c, p.position(), SoundEvents.WARDEN_SONIC_BOOM, 0.4F, 1.8F);
        return true;
    }

    /** 12s: the four songs at full strength, and every enemy around takes the music each second. */
    private static boolean symphony(Ctx c) {
        ServerPlayer p = c.p();
        int life = ticks(c.def().duration());
        double r = c.def().radius();
        ServerFx.of("n_notes", p.position()).follow(p).a(c.def().duration()).b(1).color(0xFFE070).send(c.level());
        ServerFx.of("x_ring", p.position()).a(r).b(3).color(0xFFE070).send(c.level());
        sound(c, SoundEvents.PLAYER_LEVELUP, 1.0F);
        SkillTasks.run(age -> {
            if (!p.isAlive()) return true;
            if (age % 20 == 0) {
                Bard.grand(p, r, 30);
                List<LivingEntity> foes = enemies(p, p.position(), r);
                for (LivingEntity e : foes) hurt(c, e, c.dmg(1), magic(c));
                ServerFx.of("n_song", p.position()).follow(p).a(r).b(1.2).color(0xFFE070).points(foes).send(c.level());
                for (float pitch : new float[] {1.0F, 1.26F, 1.5F, 2.0F}) sound(c, p.position(), SoundEvents.NOTE_BLOCK_BELL, 0.45F, pitch * (age % 40 == 0 ? 1F : 0.89F));
            }
            return age >= life;
        });
        return true;
    }

    // ================================================================== spellblade

    /** 0 fire, 1 ice, 2 thunder (kept in the coating slot of the job data, which the spellblade has no other use for). */
    public static int element(ServerPlayer p) {
        return Math.floorMod(JobManager.data(p).coatMode(), 3);
    }

    public static final int[] ELEMENT_COLORS = {0xFF6A20, 0x9FE8FF, 0xFFF07A};

    /** What the element does to an enemy it hits (sword hits and skills). */
    static void elementHit(ServerPlayer p, ServerLevel level, LivingEntity e, int element, float damage) {
        switch (element) {
            case 0 -> e.igniteForSeconds(3);
            case 1 -> e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1), p);
            default -> { // thunder: jumps to one more enemy
                LivingEntity next = null;
                double bd = 16;
                for (LivingEntity o : enemies(p, e.position(), 4)) {
                    double dd = o.distanceToSqr(e);
                    if (o != e && dd < bd) {
                        bd = dd;
                        next = o;
                    }
                }
                if (next != null && damage > 0) {
                    LivingEntity n = next;
                    SkillTasks.later(2, () -> {
                        if (!n.isAlive()) return;
                        n.hurtServer(level, p.damageSources().source(SKILL_MAGIC_DAMAGE, p, p), damage * 0.4F);
                        ServerFx.of("x_link", mid(e)).vec(mid(n)).color(0xFFF07A).send(level);
                    });
                }
            }
        }
    }

    private static boolean elementShift(Ctx c) {
        ServerPlayer p = c.p();
        JobData d = JobManager.data(p);
        d.setCoatMode(d.coatMode() + 1);
        JobManager.sync(p);
        int el = element(p);
        p.sendSystemMessage(Component.translatable("rpgmod.spellblade.element", Component.translatable("rpgmod.spellblade.element." + el))
                .withColor(ELEMENT_COLORS[el]), true);
        ServerFx.of("x_aura", p.position()).follow(p).a(1.5).b(el == 0 ? 1 : el == 1 ? 3 : 0).color(ELEMENT_COLORS[el]).send(c.level());
        sound(c, el == 0 ? SoundEvents.FIRECHARGE_USE : el == 1 ? SoundEvents.GLASS_BREAK : SoundEvents.LIGHTNING_BOLT_IMPACT, 1.6F);
        return true;
    }

    /** A flying crescent of the sword's element; goes through enemies. */
    private static boolean magicSlash(Ctx c) {
        ServerPlayer p = c.p();
        int el = element(p);
        double range = c.def().radius(), speed = 1.5;
        Vec3 dir = c.look();
        Vec3 start = c.chest().add(c.flat().scale(0.8));
        ServerFx.of("x_wave", start).vec(dir.scale(speed)).a(range).b(2.4).color(ELEMENT_COLORS[el]).send(c.level());
        sound(c, SoundEvents.PLAYER_ATTACK_SWEEP, 1.3F);
        Set<Integer> done = new HashSet<>();
        final Vec3[] pos = {start};
        final double[] gone = {0};
        SkillTasks.run(age -> {
            Vec3 from = pos[0], to = from.add(dir.scale(speed));
            BlockHitResult block = c.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            boolean wall = block.getType() != HitResult.Type.MISS;
            if (wall) to = block.getLocation();
            for (LivingEntity e : c.level().getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(1.3), e -> isEnemy(p, e))) {
                if (!done.add(e.getId())) continue;
                hurt(c, e, c.dmg(1), melee(c));
                elementHit(p, c.level(), e, el, c.dmg(1));
                ServerFx.of("x_hit", mid(e)).vec(dir).a(1).color(ELEMENT_COLORS[el]).send(c.level());
            }
            pos[0] = to;
            gone[0] += speed;
            return wall || gone[0] >= range;
        });
        return true;
    }

    /** A lightning-fast thrust through a line of enemies; thunder jumps from each. */
    private static boolean thunderThrust(Ctx c) {
        ServerPlayer p = c.p();
        Vec3 dir = c.flat();
        Vec3 from = p.position();
        launch(p, dir.scale(2.2).add(0, 0.1, 0));
        ServerFx.of("x_dash", from).vec(from.add(dir.scale(c.def().radius()))).color(0xFFF07A).send(c.level());
        sound(c, SoundEvents.LIGHTNING_BOLT_IMPACT, 1.4F);
        Set<Integer> done = new HashSet<>();
        SkillTasks.run(age -> {
            if (!p.isAlive()) return true;
            for (LivingEntity e : enemies(p, p.position().add(dir.scale(0.8)), 1.8)) {
                if (!done.add(e.getId())) continue;
                hurt(c, e, c.dmg(1), melee(c));
                elementHit(p, c.level(), e, 2, c.dmg(1));
                ServerFx.of("x_strike", e.position()).a(1).color(0xFFF07A).point(mid(e)).send(c.level());
            }
            return age >= 7;
        });
        return true;
    }

    private static boolean frostBlade(Ctx c) {
        List<LivingEntity> hit = cone(c, c.def().radius(), 65);
        for (LivingEntity e : hit) {
            hurt(c, e, c.dmg(1), melee(c));
            apply(e, c.def(), c.p());
            e.setTicksFrozen(Math.max(e.getTicksFrozen(), 140));
        }
        ServerFx.of("x_slash", c.chest().add(c.flat().scale(1.2))).vec(c.flat()).a(c.def().radius()).b(0.2).color(0x9FE8FF).points(hit).send(c.level());
        for (LivingEntity e : hit) ServerFx.of("x_burst", e.position()).a(1).b(1).color(0x9FE8FF).send(c.level());
        sound(c, SoundEvents.GLASS_BREAK, 0.9F);
        sound(c, c.p().position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.8F, 1.2F);
        return true;
    }

    private static boolean flameWheel(Ctx c) {
        ServerPlayer p = c.p();
        double r = c.def().radius();
        SkillTasks.run(age -> {
            if (!p.isAlive()) return true;
            if (age == 0 || age == 10) {
                List<LivingEntity> hit = enemies(p, p.position(), r);
                for (LivingEntity e : hit) {
                    hurt(c, e, c.dmg(1), melee(c));
                    e.igniteForSeconds(4);
                    if (age == 10) pushAway(e, p.position(), 0.6, 0.2);
                }
                ServerFx.of("whirlwind", p.position()).follow(p).a(r).send(c.level());
                ServerFx.of("x_ring", p.position()).a(r).b(1).color(0xFF6A20).points(hit).send(c.level());
                sound(c, p.position(), SoundEvents.FIRECHARGE_USE, 0.9F, 0.8F);
            }
            return age >= 10;
        });
        return true;
    }

    /** One huge slash of the element along a wide arc. */
    private static boolean arcaneEdge(Ctx c) {
        ServerPlayer p = c.p();
        int el = element(p);
        List<LivingEntity> hit = cone(c, c.def().radius(), 40);
        for (LivingEntity e : hit) {
            hurt(c, e, c.dmg(1), melee(c));
            elementHit(p, c.level(), e, el, c.dmg(1));
            push(e, c.flat(), 0.9, 0.3);
        }
        ServerFx.of("x_slash", c.chest().add(c.flat().scale(2.5))).vec(c.flat()).a(c.def().radius()).b(1.2).color(ELEMENT_COLORS[el]).points(hit).send(c.level());
        ServerFx.of("x_wave", c.chest()).vec(c.look().scale(1.6)).a(c.def().radius()).b(4).color(ELEMENT_COLORS[el]).send(c.level());
        sound(c, SoundEvents.PLAYER_ATTACK_SWEEP, 0.5F);
        sound(c, p.position(), SoundEvents.ENCHANTMENT_TABLE_USE, 1.0F, 0.6F);
        return true;
    }

    /** Fire, then ice, then thunder bursting out around you. */
    private static boolean elementalBurst(Ctx c) {
        ServerPlayer p = c.p();
        double r = c.def().radius();
        SkillTasks.run(age -> {
            if (!p.isAlive()) return true;
            if (age % 10 == 0) {
                int el = age / 10;
                List<LivingEntity> hit = enemies(p, p.position(), r);
                for (LivingEntity e : hit) {
                    hurt(c, e, c.dmg(1), melee(c));
                    elementHit(p, c.level(), e, el, c.dmg(1));
                }
                if (el == 2) for (LivingEntity e : hit) ServerFx.of("x_strike", e.position()).a(1).color(0xFFF07A).point(mid(e)).send(c.level());
                else ServerFx.of("x_burst", p.position().add(0, 0.5, 0)).a(r).b(el == 0 ? 0 : 1).color(ELEMENT_COLORS[el]).points(hit).send(c.level());
                sound(c, p.position(), el == 0 ? SoundEvents.FIRECHARGE_USE : el == 1 ? SoundEvents.GLASS_BREAK : SoundEvents.LIGHTNING_BOLT_THUNDER, 1.0F, 1.0F);
            }
            return age >= 20;
        });
        return true;
    }

    // ================================================================== qi master

    /** A ball of ki; with ki to spend (one point) it hits 80% harder. */
    private static boolean qiBullet(Ctx c) {
        ServerPlayer p = c.p();
        JobData d = JobManager.data(p);
        double mult = 1;
        if (d.ki() > 0) {
            d.setKi(d.ki() - 1);
            JobManager.sync(p);
            mult = 1.8;
        }
        double m = mult;
        LivingEntity target = lookTarget(c, c.def().radius(), 12);
        Vec3 start = c.hand();
        Vec3 dir = target != null ? mid(target).subtract(start).normalize() : c.look();
        ServerFx.of("x_bolt", start).vec(dir.scale(1.6)).a(c.def().radius()).b(m > 1 ? 0.45 : 0.3).color(KI).follow(target).send(c.level());
        sound(c, SoundEvents.BLAZE_SHOOT, 1.6F);
        projectile(c, start, dir, 1.6, c.def().radius(), target, 0.25, (e, at, dd) -> {
            hurt(c, e, c.dmg(m), melee(c));
            push(e, dd, 0.3, 0.1);
            ServerFx.of("x_hit", at).vec(dd).a(0.9).color(KI).send(c.level());
        }, (at, dd, wall) -> { });
        return true;
    }

    /** 4s of gathering: a point of ki each second, and regeneration. */
    private static boolean refineQi(Ctx c) {
        ServerPlayer p = c.p();
        apply(p, c.def(), p);
        ServerFx.of("x_aura", p.position()).follow(p).a(c.def().duration()).b(0).color(0xFFE6A0).send(c.level());
        sound(c, SoundEvents.BEACON_POWER_SELECT, 1.0F);
        int life = ticks(c.def().duration());
        SkillTasks.run(age -> {
            if (!p.isAlive()) return true;
            if (age % 20 == 19) Monk.add(p, 1);
            return age >= life;
        });
        return true;
    }

    /** Ki into a shell: 20% less damage for every two points spent (all of it, up to 60%), and a little health back. */
    private static boolean hardQi(Ctx c) {
        ServerPlayer p = c.p();
        int ki = Monk.spend(p);
        if (ki <= 0) {
            fail(p, "rpgmod.monk.no_ki");
            return false;
        }
        int amp = Math.min(2, (ki - 1) / 2);
        p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, ticks(c.def().duration()), amp), p);
        HealthScale.heal(p, p.getMaxHealth() * 0.03F * ki);
        ServerFx.of("x_aura", p.position()).follow(p).a(c.def().duration()).b(2).color(KI).send(c.level());
        sound(c, SoundEvents.ANVIL_PLACE, 1.2F);
        return true;
    }

    /** A burst of ki into the enemies in front; a third of the damage heals you. */
    private static boolean qiBurst(Ctx c) {
        ServerPlayer p = c.p();
        List<LivingEntity> hit = cone(c, c.def().radius(), 50);
        float dealt = 0;
        for (LivingEntity e : hit) {
            float before = e.getHealth();
            hurt(c, e, c.dmg(1), melee(c));
            dealt += Math.max(0, before - e.getHealth());
            push(e, c.flat(), 0.8, 0.2);
        }
        if (dealt > 0) HealthScale.heal(p, Math.min(p.getMaxHealth() * 0.25F, dealt * 0.3F));
        ServerFx.of("x_burst", c.chest().add(c.flat().scale(1.6))).a(1.6).b(3).color(KI).points(hit).send(c.level());
        if (dealt > 0) ServerFx.of("heal", p.position()).follow(p).a(1).send(c.level());
        sound(c, SoundEvents.PLAYER_ATTACK_STRONG, 0.7F);
        return true;
    }

    /** Glides forward through the enemies, striking them; each one struck gives back a little health. */
    private static boolean flowingStep(Ctx c) {
        ServerPlayer p = c.p();
        Vec3 dir = c.flat();
        Vec3 from = p.position();
        launch(p, dir.scale(2.2).add(0, 0.1, 0));
        ServerFx.of("x_dash", from).vec(from.add(dir.scale(c.def().radius()))).color(0xFFE6A0).send(c.level());
        sound(c, SoundEvents.BREEZE_JUMP, 1.6F);
        Set<Integer> done = new HashSet<>();
        SkillTasks.run(age -> {
            if (!p.isAlive()) return true;
            for (LivingEntity e : enemies(p, p.position(), 1.7)) {
                if (!done.add(e.getId())) continue;
                hurt(c, e, c.dmg(1), melee(c));
                HealthScale.heal(p, p.getMaxHealth() * 0.03F);
            }
            if (age >= 5) {
                Monk.add(p, 1);
                return true;
            }
            return false;
        });
        return true;
    }

    /** Heals everyone around a lot and knocks the enemies around away. */
    private static boolean heavenEarth(Ctx c) {
        ServerPlayer p = c.p();
        double r = c.def().radius();
        heal(c, "group_heal", true);
        List<LivingEntity> hit = enemies(p, p.position(), r);
        for (LivingEntity e : hit) {
            hurt(c, e, c.dmg(0.75), melee(c));
            pushAway(e, p.position(), 1.0, 0.5);
        }
        ServerFx.of("x_pillar", p.position()).a(2).b(30).color(0xFFE6A0).send(c.level());
        ServerFx.of("x_ring", p.position()).a(r).b(3).color(KI).points(hit).send(c.level());
        sound(c, SoundEvents.BEACON_ACTIVATE, 1.2F);
        return true;
    }

    /** 3s of punches; every hit that lands gives back 1% of your health. */
    private static boolean bloomingFists(Ctx c) {
        ServerPlayer p = c.p();
        int life = ticks(c.def().duration());
        SkillTasks.run(age -> {
            if (!p.isAlive()) return true;
            if (age % 3 == 0) {
                List<LivingEntity> hit = cone(c, c.def().radius(), 45);
                for (LivingEntity e : hit) {
                    hurt(c, e, c.dmg(1), melee(c));
                    push(e, c.flat(), 0.06, 0.02);
                }
                if (!hit.isEmpty()) HealthScale.heal(p, p.getMaxHealth() * 0.01F * hit.size());
                ServerFx.of("n_fist", c.chest().add(c.flat().scale(1.6))).vec(c.flat()).color(age % 6 == 0 ? 0xFFA0C8 : 0xFFE0F0).points(hit).send(c.level());
                sound(c, p.position(), SoundEvents.PLAYER_ATTACK_WEAK, 0.7F, 1.0F + p.getRandom().nextFloat() * 0.5F);
            }
            return age >= life;
        });
        return true;
    }

    // ================================================================== sentinel

    /** A straight thrust; you hold your guard up after it (resistance for 2s). */
    private static boolean guardThrust(Ctx c) {
        ServerPlayer p = c.p();
        List<LivingEntity> hit = cone(c, c.def().radius(), 20);
        for (LivingEntity e : hit) {
            hurt(c, e, c.dmg(1), melee(c));
            push(e, c.flat(), 0.5, 0.1);
        }
        p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 40, 0), p);
        ServerFx.of("x_beam", c.hand()).vec(c.hand().add(c.look().scale(c.def().radius()))).a(0.2).color(0xF0E0A0).points(hit).send(c.level());
        sound(c, SoundEvents.SPEAR_ATTACK, 1.0F);
        sound(c, p.position(), SoundEvents.SHIELD_BLOCK, 0.6F, 1.4F);
        return true;
    }

    /** Charges behind the shield; enemies hit are knocked aside and stunned. */
    private static boolean shieldCharge(Ctx c) {
        ServerPlayer p = c.p();
        Vec3 dir = c.flat();
        launch(p, dir.scale(2.0).add(0, 0.15, 0));
        ServerFx.of("charge", p.position()).vec(dir).follow(p).send(c.level());
        sound(c, SoundEvents.SHIELD_BLOCK, 0.7F);
        Set<Integer> done = new HashSet<>();
        SkillTasks.run(age -> {
            if (!p.isAlive()) return true;
            Vec3 front = p.position().add(dir.scale(0.9));
            List<LivingEntity> hit = new ArrayList<>();
            for (LivingEntity e : enemies(p, front, 1.8)) {
                if (!done.add(e.getId())) continue;
                hurt(c, e, c.dmg(1), melee(c));
                apply(e, c.def(), p);
                push(e, dir, 1.2, 0.35);
                hit.add(e);
            }
            if (!hit.isEmpty()) {
                ServerFx.of("x_hit", front.add(0, 1, 0)).vec(dir).a(1.3).color(0xF0E0A0).send(c.level());
                sound(c, front, SoundEvents.ANVIL_LAND, 0.5F, 1.4F);
            }
            return age >= 9;
        });
        return true;
    }

    /** A ring of spears where you stand: enemies inside are struck and pushed out twice a second. */
    private static boolean spearWall(Ctx c) {
        ServerPlayer p = c.p();
        Vec3 o = p.position();
        double r = c.def().radius();
        int life = ticks(c.def().duration());
        ServerFx.of("x_zone", o).a(r).b(life).vec(new Vec3(4, 4, 0)).color(0xD0B070).send(c.level());
        sound(c, SoundEvents.SPEAR_USE, 0.8F);
        SkillTasks.run(age -> {
            if (age % 10 == 0) {
                List<LivingEntity> hit = enemies(p, o, r);
                for (LivingEntity e : hit) {
                    hurt(c, e, c.dmg(1), melee(c));
                    pushAway(e, o, 0.6, 0.1);
                }
                if (!hit.isEmpty()) {
                    ServerFx.of("x_spikes", o).vec(hit.get(0).position().subtract(o)).a(r).color(0xD0B070).send(c.level());
                    sound(c, o, SoundEvents.SPEAR_HIT, 0.7F, 1.0F);
                }
            }
            return age >= life;
        });
        return true;
    }

    /** Every monster around turns on you; you brace (resistance). */
    private static boolean provoke(Ctx c) {
        ServerPlayer p = c.p();
        List<LivingEntity> foes = enemies(p, p.position(), c.def().radius());
        for (LivingEntity e : foes) if (e instanceof Mob m) m.setTarget(p);
        p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, ticks(c.def().duration()), 0), p);
        ServerFx.of("x_ring", p.position()).a(c.def().radius()).b(2).color(0xFF6040).points(foes).send(c.level());
        ServerFx.of("x_aura", p.position()).follow(p).a(c.def().duration()).b(2).color(0xE0C080).send(c.level());
        sound(c, SoundEvents.RAVAGER_ROAR, 1.2F);
        return true;
    }

    /** Everyone around takes half damage for 10s; you cannot be moved. */
    private static boolean fortress(Ctx c) {
        ServerPlayer p = c.p();
        int t = ticks(c.def().duration());
        List<ServerPlayer> allies = allies(p, c.def().radius());
        for (ServerPlayer a : allies) a.addEffect(new MobEffectInstance(ModEffects.FORTRESS, t, 0), p);
        p.addEffect(new MobEffectInstance(ModEffects.IMMOVABLE, t, 0), p);
        ServerFx.of("x_wall", p.position().add(c.flat().scale(1.5))).vec(c.flat()).a(3).b(t).color(0xF0D890).send(c.level());
        ServerFx.of("x_aura", p.position()).follow(p).a(c.def().duration()).b(2).color(0xF0D890).points(others(p, allies)).send(c.level());
        ServerFx.of("x_ring", p.position()).a(c.def().radius()).b(2).color(0xF0D890).send(c.level());
        sound(c, SoundEvents.BEACON_ACTIVATE, 0.9F);
        sound(c, p.position(), SoundEvents.ANVIL_LAND, 0.6F, 0.8F);
        return true;
    }

    /** A spear of light straight ahead, through everything in the way. */
    private static boolean holyLance(Ctx c) {
        ServerPlayer p = c.p();
        List<LivingEntity> hit = line(c, c.look(), c.def().radius(), 1.2);
        for (LivingEntity e : hit) {
            hurt(c, e, c.dmg(1), melee(c));
            push(e, c.look(), 0.8, 0.3);
        }
        Vec3 end = lineEnd(c, c.look(), c.def().radius());
        ServerFx.of("x_beam", c.hand()).vec(end).a(0.8).color(HOLY).points(hit).send(c.level());
        ServerFx.of("x_pillar", end.subtract(0, 1, 0)).a(1.5).b(20).color(HOLY).send(c.level());
        sound(c, SoundEvents.BEACON_ACTIVATE, 1.6F);
        sound(c, p.position(), SoundEvents.SPEAR_ATTACK, 1.2F, 0.6F);
        return true;
    }

    // ================================================================== spellsinger

    /** Every good effect on the party lasts 5s longer; a song being played gains points. */
    private static boolean resonance(Ctx c) {
        ServerPlayer p = c.p();
        List<ServerPlayer> allies = allies(p, c.def().radius());
        for (ServerPlayer a : allies) {
            for (MobEffectInstance e : new ArrayList<>(a.getActiveEffects())) {
                if (!e.getEffect().value().isBeneficial() || e.isInfiniteDuration() || e.getEffect().is(ModEffects.PERFORMING.getKey())) continue;
                a.addEffect(new MobEffectInstance(e.getEffect(), e.getDuration() + 100, e.getAmplifier(), e.isAmbient(), e.isVisible(), e.showIcon()), p);
            }
        }
        if (Bard.performing(p)) {
            JobData d = JobManager.data(p);
            d.setSongPoints(d.songPoints() + 4);
            JobManager.sync(p);
        }
        ServerFx.of("x_ring", p.position()).a(c.def().radius()).b(3).color(0x9A6AD8).points(others(p, allies)).send(c.level());
        ServerFx.of("n_notes", p.position()).follow(p).a(1.5).b(1).color(0xC8A0FF).send(c.level());
        sound(c, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F);
        return true;
    }

    private static boolean grandConcerto(Ctx c) {
        ServerPlayer p = c.p();
        int life = ticks(c.def().duration());
        double r = c.def().radius();
        ServerFx.of("n_notes", p.position()).follow(p).a(c.def().duration()).b(1).color(0xC8A0FF).send(c.level());
        ServerFx.of("x_ring", p.position()).a(r).b(3).color(0x9A6AD8).send(c.level());
        sound(c, SoundEvents.PLAYER_LEVELUP, 1.1F);
        SkillTasks.run(age -> {
            if (!p.isAlive()) return true;
            if (age % 20 == 0) {
                Bard.concerto(p, r, 30);
                ServerFx.of("n_song", p.position()).follow(p).a(r).b(1.2).color(0xC8A0FF).send(c.level());
                for (float pitch : new float[] {0.84F, 1.0F, 1.26F}) sound(c, p.position(), SoundEvents.NOTE_BLOCK_CHIME, 0.45F, pitch * (age % 40 == 0 ? 1F : 1.12F));
            }
            return age >= life;
        });
        return true;
    }

    /** Ends the performance with a sealing chord: damage by the points, and the enemies are left exposed. */
    private static boolean sealingChord(Ctx c) {
        ServerPlayer p = c.p();
        JobData d = JobManager.data(p);
        int points = Bard.performing(p) ? d.songPoints() : 0;
        double r = Bard.radius(points);
        List<LivingEntity> hit = enemies(p, p.position(), r);
        for (LivingEntity e : hit) {
            hurt(c, e, c.dmg(0.6 + 0.02 * points), magic(c));
            e.addEffect(new MobEffectInstance(ModEffects.EXPOSED, 120, 1), p);
            e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 2), p);
        }
        Bard.stop(p);
        d.setSongPoints(0);
        JobManager.sync(p);
        ServerFx.of("x_ring", p.position()).a(r).b(3).color(0xB070E0).points(hit).send(c.level());
        ServerFx.of("n_finale", p.position()).a(r).b(points).color(0x9A6AD8).send(c.level());
        sound(c, SoundEvents.NOTE_BLOCK_BELL, 0.6F);
        sound(c, p.position(), SoundEvents.ENCHANTMENT_TABLE_USE, 1.0F, 0.5F);
        return true;
    }

    // ================================================================== shadow hunter

    /** A shot to the weak spot: twice the damage on enemies at half health or less. */
    private static boolean vitalShot(Ctx c) {
        ServerPlayer p = c.p();
        Ray r = ray(c, c.look(), c.def().radius());
        if (r.hit() != null) {
            boolean weak = r.hit().getHealth() <= r.hit().getMaxHealth() * 0.5F;
            hurt(c, r.hit(), c.dmg(weak ? 2 : 1), melee(c));
            push(r.hit(), c.look(), 0.5, 0.15);
            ServerFx.of("x_hit", r.end()).vec(c.look()).a(weak ? 1.6 : 1.1).color(weak ? 0xFF3040 : 0xC0E0A0).send(c.level());
            if (weak) p.sendSystemMessage(Component.translatable("rpgmod.hunter.vital").withStyle(ChatFormatting.RED), true);
        }
        ServerFx.of("x_beam", c.hand()).vec(r.end()).a(0.08).color(0xC0E0A0).send(c.level());
        sound(c, SoundEvents.CROSSBOW_SHOOT, 0.9F);
        sound(c, p.position(), SoundEvents.ARROW_HIT_PLAYER, 0.5F, 1.4F);
        return true;
    }

    /** Vanish: invisible for a while, monsters lose you, and the next hit is a sure, heavier crit. */
    private static boolean stealth(Ctx c) {
        ServerPlayer p = c.p();
        int t = ticks(c.def().duration());
        p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, t, 0, false, false, true), p);
        p.addEffect(new MobEffectInstance(ModEffects.AMBUSH, t + 40, 0), p);
        for (Mob m : c.level().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(20), m -> m.getTarget() == p)) m.setTarget(null);
        ServerFx.of("x_burst", p.position().add(0, 1, 0)).a(1.6).b(3).color(SHADOW).send(c.level());
        sound(c, SoundEvents.ILLUSIONER_MIRROR_MOVE, 0.8F);
        return true;
    }

    /** An arrow that pins the enemy where it stands. */
    private static boolean shadowArrow(Ctx c) {
        ServerPlayer p = c.p();
        LivingEntity target = lookTarget(c, c.def().radius(), 10);
        Vec3 start = c.hand();
        Vec3 dir = target != null ? mid(target).subtract(start).normalize() : c.look();
        ServerFx.of("x_bolt", start).vec(dir.scale(2.2)).a(c.def().radius()).b(0.25).color(SHADOW).follow(target).send(c.level());
        sound(c, SoundEvents.ARROW_SHOOT, 0.7F);
        projectile(c, start, dir, 2.2, c.def().radius(), target, 0.2, (e, at, dd) -> {
            hurt(c, e, c.dmg(1), melee(c));
            e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks(c.def().duration()), 6), p);
            e.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks(c.def().duration()), 0), p);
            ServerFx.of("x_mark", e.position()).follow(e).a(c.def().duration()).color(SHADOW).send(c.level());
        }, (at, dd, wall) -> { });
        return true;
    }

    /** Sets a trap on the aimed spot (20s): the first enemy to step on it sets it off. */
    private static boolean hunterTrap(Ctx c) {
        ServerPlayer p = c.p();
        Vec3 at = aimPoint(c, 8);
        int life = ticks(c.def().duration());
        double r = c.def().radius();
        sound(c, at, SoundEvents.CHAIN_PLACE, 0.8F, 1.4F);
        SkillTasks.run(age -> {
            if (age % 40 == 0) ServerFx.of("x_zone", at).a(1.2).b(42).vec(new Vec3(2, 0, 0)).color(SHADOW).send(c.level());
            if (age % 4 == 0 && !enemies(p, at, 1.3).isEmpty()) {
                List<LivingEntity> hit = enemies(p, at, r);
                for (LivingEntity e : hit) {
                    hurt(c, e, c.dmg(1), melee(c));
                    e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 6), p);
                }
                ServerFx.of("x_burst", at.add(0, 0.3, 0)).a(r).b(3).color(SHADOW).points(hit).send(c.level());
                ServerFx.of("x_spikes", at).vec(c.flat()).a(2).color(0x606880).send(c.level());
                sound(c, at, SoundEvents.GENERIC_EXPLODE, 0.8F, 1.3F);
                return true;
            }
            return age >= life;
        });
        return true;
    }

    /** Leaps back out of a puff of smoke: enemies near it lose sight of you; the next hit is an ambush. */
    private static boolean shadowLeap(Ctx c) {
        ServerPlayer p = c.p();
        Vec3 from = p.position();
        launch(p, c.flat().scale(-1.3).add(0, 0.45, 0));
        for (LivingEntity e : enemies(p, from, 3.5)) {
            e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0), p);
            if (e instanceof Mob m && m.getTarget() == p) m.setTarget(null);
        }
        p.addEffect(new MobEffectInstance(ModEffects.AMBUSH, ticks(c.def().duration()), 0), p);
        ServerFx.of("x_burst", from.add(0, 0.8, 0)).a(2).b(3).color(0x303848).send(c.level());
        ServerFx.of("backstep", from).vec(c.flat().scale(-1)).follow(p).send(c.level());
        sound(c, SoundEvents.BREEZE_JUMP, 1.4F);
        return true;
    }

    /** Five knives in a fan. */
    private static boolean fanKnives(Ctx c) {
        ServerPlayer p = c.p();
        for (int i = 0; i < 5; i++) {
            Vec3 dir = Fx3.rotY(c.look(), Math.toRadians((i - 2) * 9));
            Vec3 start = c.hand();
            ServerFx.of("x_bolt", start).vec(dir.scale(1.8)).a(c.def().radius()).b(0.15).color(0xD8E0F0).send(c.level());
            projectile(c, start, dir, 1.8, c.def().radius(), null, 0, (e, at, dd) -> {
                hurt(c, e, c.dmg(1), melee(c));
                ServerFx.of("x_hit", at).vec(dd).a(0.6).color(0xD8E0F0).send(c.level());
            }, (at, dd, wall) -> { });
        }
        sound(c, SoundEvents.PLAYER_ATTACK_SWEEP, 1.8F);
        return true;
    }

    /** 10s: you are unseen, and every enemy within 30 blocks is marked as prey (+50% damage from you). */
    private static boolean nightHunt(Ctx c) {
        ServerPlayer p = c.p();
        int t = ticks(c.def().duration());
        p.addEffect(new MobEffectInstance(ModEffects.NIGHT_HUNT, t, 0), p);
        p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, t, 0, false, false, true), p);
        int shown = 0;
        for (LivingEntity e : enemies(p, p.position(), c.def().radius())) {
            e.addEffect(new MobEffectInstance(ModEffects.HUNTED, t, 0), p);
            e.addEffect(new MobEffectInstance(MobEffects.GLOWING, t, 0), p);
            if (e instanceof Mob m && m.getTarget() == p) m.setTarget(null);
            if (shown++ < 12) ServerFx.of("x_mark", e.position()).follow(e).a(c.def().duration()).color(0xC03040).send(c.level());
        }
        ServerFx.of("x_aura", p.position()).follow(p).a(c.def().duration()).b(1).color(0x2A3050).send(c.level());
        sound(c, SoundEvents.WARDEN_HEARTBEAT, 0.8F);
        sound(c, p.position(), SoundEvents.ILLUSIONER_MIRROR_MOVE, 0.8F, 0.6F);
        return true;
    }

    /**
     * Shadow arrows fall on one enemy (the one aimed at) for 3s, pinning it; each one that hits
     * glances off to the nearest other enemy (half the damage, pinned too). A fallen target is
     * replaced by the nearest enemy.
     */
    private static boolean shadowRain(Ctx c) {
        ServerPlayer p = c.p();
        LivingEntity first = lookTarget(c, 28, 10);
        if (first == null) first = NewJobSkills.nearest(enemies(p, aimPoint(c, 28), c.def().radius()), aimPoint(c, 28));
        if (first == null) {
            fail(p, "rpgmod.job.no_target");
            return false;
        }
        int life = ticks(c.def().duration());
        final LivingEntity[] target = {first};
        ServerFx.of("x_mark", first.position()).follow(first).a(c.def().duration()).color(0x6070C0).send(c.level());
        ServerFx.of("n_dark_portal", first.position()).follow(first).b(life + 6).color(0x6070C0).send(c.level());
        sound(c, SoundEvents.ARROW_SHOOT, 0.6F);
        SkillTasks.run(age -> {
            if (age % 4 == 0 && age < life) {
                LivingEntity t = target[0];
                if (!t.isAlive()) {
                    t = NewJobSkills.nearest(enemies(p, t.position(), c.def().radius()), t.position());
                    if (t == null) return true;
                    target[0] = t;
                }
                LivingEntity hitT = t;
                ServerFx.of("n_spear_fall", hitT.position().add(0, hitT.getBbHeight() * 0.6, 0)).b(4).a(hitT.getBbHeight() * 0.4 + 3.2).color(0x6070C0).send(c.level());
                SkillTasks.later(4, () -> {
                    if (!hitT.isAlive()) return;
                    hurt(c, hitT, c.dmg(1), melee(c));
                    pin(c, hitT);
                    LivingEntity next = null;
                    double bd = 25;
                    for (LivingEntity o2 : enemies(p, hitT.position(), 5)) {
                        double dd = o2.distanceToSqr(hitT);
                        if (o2 != hitT && dd < bd) {
                            bd = dd;
                            next = o2;
                        }
                    }
                    if (next != null) {
                        hurt(c, next, c.dmg(0.5), melee(c));
                        pin(c, next);
                        ServerFx.of("x_bolt", mid(hitT)).vec(mid(next).subtract(mid(hitT)).normalize().scale(2.5)).a(6).b(0.15).color(0x8090D0).follow(next).send(c.level());
                    }
                    sound(c, hitT.position(), SoundEvents.ARROW_HIT, 0.7F, 0.8F);
                });
            }
            return age >= life + 4;
        });
        return true;
    }

    /** Pinned by a shadow arrow: cannot move for a moment. */
    private static void pin(Ctx c, LivingEntity e) {
        e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 25, 6), c.p());
        ServerFx.of("x_ring", e.position()).a(1).b(1).color(0x303850).send(c.level());
    }

    // ================================================================== treasure hunter

    private static final String[] SHELLS = {"explosive", "ice", "poison", "gold", "dud", "jackpot"};

    /** A cannon shot with a random shell: explosive, ice, poison, gold, a dud or the jackpot (luck rerolls a dud). */
    private static boolean gambleCannon(Ctx c) {
        ServerPlayer p = c.p();
        var rnd = p.getRandom();
        int shell = rnd.nextInt(6);
        if ((shell == 4 || shell == 2) && JobManager.luckyReroll(p)) shell = rnd.nextInt(6);
        if (AdvSkills.loaded(p)) shell = 5;
        int kind = shell;
        p.sendSystemMessage(Component.translatable("rpgmod.treasure.shell." + SHELLS[kind]).withStyle(kind == 5 ? ChatFormatting.GOLD : kind == 4 ? ChatFormatting.GRAY : ChatFormatting.YELLOW), true);
        LivingEntity target = lookTarget(c, c.def().radius(), 10);
        Vec3 start = c.hand();
        Vec3 dir = target != null ? mid(target).subtract(start).normalize() : c.look();
        int color = switch (kind) {
            case 1 -> 0x9FE8FF;
            case 2 -> 0x70E040;
            case 3, 5 -> 0xFFD040;
            case 4 -> 0x808080;
            default -> 0x303030;
        };
        ServerFx.of("n_cannon_fire", start).vec(dir).send(c.level());
        ServerFx.of("x_bolt", start).vec(dir.scale(1.4)).a(c.def().radius()).b(kind == 5 ? 0.5 : 0.32).color(color).follow(target).send(c.level());
        sound(c, SoundEvents.GENERIC_EXPLODE, 1.3F);
        ProjectileEnd boom = (at, dd, wall) -> shellHit(c, at, kind, color);
        projectile(c, start, dir, 1.4, c.def().radius(), target, 0.25, (e, at, dd) -> boom.end(at, dd, false), boom);
        return true;
    }

    private static void shellHit(Ctx c, Vec3 at, int kind, int color) {
        ServerPlayer p = c.p();
        switch (kind) {
            case 1 -> {
                for (LivingEntity e : NewJobSkills.blast(c, at, 2.5, c.dmg(0.8), 1, color)) e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 3), p);
            }
            case 2 -> {
                for (LivingEntity e : NewJobSkills.blast(c, at, 3, c.dmg(0.6), 3, color)) e.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1), p);
            }
            case 3 -> {
                NewJobSkills.blast(c, at, 2, c.dmg(0.7), 4, color);
                drop(c.level(), at, new ItemStack(Items.GOLD_NUGGET, 2 + p.getRandom().nextInt(4)));
            }
            case 4 -> {
                for (LivingEntity e : enemies(p, at, 1.2)) hurt(c, e, c.dmg(0.2), melee(c));
                ServerFx.of("x_burst", at).a(1).b(3).color(0x808080).send(c.level());
                sound(c, at, SoundEvents.DISPENSER_FAIL, 1.0F, 0.6F);
            }
            case 5 -> {
                NewJobSkills.blast(c, at, 3.5, c.dmg(3), 4, color);
                ServerFx.of("x_burst", at).a(3).b(0).color(0xFFD040).send(c.level());
                sound(c, at, SoundEvents.PLAYER_LEVELUP, 1.0F, 1.4F);
            }
            default -> NewJobSkills.blast(c, at, 2.5, c.dmg(1), 0, 0xFF8030);
        }
    }

    private static void drop(ServerLevel level, Vec3 at, ItemStack stack) {
        ItemEntity item = new ItemEntity(level, at.x, at.y + 0.5, at.z, stack);
        item.setDeltaMovement(level.getRandom().nextGaussian() * 0.1, 0.25, level.getRandom().nextGaussian() * 0.1);
        level.addFreshEntity(item);
    }

    /** Eight gold coins fired in a spread. */
    private static boolean coinShot(Ctx c) {
        for (int i = 0; i < 8; i++) Pirate.shot(c.p(), c.dmg(1), c.look(), c.def().radius(), 0.09);
        ServerFx.of("x_burst", c.hand()).a(1).b(4).color(GOLD).send(c.level());
        sound(c, SoundEvents.CHAIN_PLACE, 1.8F);
        return true;
    }

    /** A chest that lures the monsters around to it and bites the ones next to it. */
    private static boolean mimic(Ctx c) {
        ServerPlayer p = c.p();
        Vec3 at = NewJobSkills.beside(c, 0, 2.2);
        int life = ticks(c.def().duration());
        SkillProp chest = SkillProp.create(c.level(), SkillProp.CHEST, at, p.getYRot() + 180, life + 5);
        ServerFx.of("x_burst", at.add(0, 0.5, 0)).a(1.2).b(4).color(GOLD).send(c.level());
        sound(c, at, SoundEvents.CHEST_OPEN, 1.0F, 0.7F);
        SkillTasks.run(age -> {
            if (chest.isRemoved()) return true;
            if (age % 20 == 0) {
                for (LivingEntity e : enemies(p, at, 10)) {
                    if (e instanceof Mob m) {
                        if (m.getTarget() == p) m.setTarget(null);
                        m.getNavigation().moveTo(at.x, at.y, at.z, 1.1);
                    }
                }
                List<LivingEntity> bitten = enemies(p, at, c.def().radius());
                for (LivingEntity e : bitten) {
                    hurt(c, e, c.dmg(1), melee(c));
                    push(e, at.subtract(e.position()), 0.3, 0.1);
                }
                if (!bitten.isEmpty()) {
                    ServerFx.of("hit", at.add(0, 0.6, 0)).color(GOLD).a(1).points(bitten).send(c.level());
                    sound(c, at, SoundEvents.CHEST_CLOSE, 1.0F, 1.4F);
                }
            }
            if (age >= life) {
                ServerFx.of("x_burst", at.add(0, 0.5, 0)).a(1.2).b(4).color(GOLD).send(c.level());
                chest.discard();
                return true;
            }
            return false;
        });
        return true;
    }

    /** Hits the nearest enemy in front, with a chance to come away with something (each monster once). */
    private static boolean steal(Ctx c) {
        ServerPlayer p = c.p();
        LivingEntity t = NewJobSkills.nearest(cone(c, c.def().radius(), 50), p.position());
        if (t == null) {
            fail(p, "rpgmod.job.no_target");
            return false;
        }
        hurt(c, t, c.dmg(1), melee(c));
        ServerFx.of("x_slash", mid(t)).vec(c.flat()).a(1.6).b(-0.5).color(GOLD).send(c.level());
        if (t.entityTags().contains("rpgmod_stolen")) {
            fail(p, "rpgmod.treasure.empty");
            return true;
        }
        boolean got = p.getRandom().nextDouble() < 0.4 || JobManager.luckyReroll(p) && p.getRandom().nextDouble() < 0.4;
        if (got) {
            t.addTag("rpgmod_stolen");
            double roll = p.getRandom().nextDouble();
            ItemStack loot = roll < 0.05 ? new ItemStack(Items.DIAMOND) : roll < 0.35 ? new ItemStack(Items.EMERALD, 1 + p.getRandom().nextInt(2))
                    : roll < 0.7 ? new ItemStack(Items.GOLD_NUGGET, 3 + p.getRandom().nextInt(4)) : new ItemStack(Items.IRON_NUGGET, 4 + p.getRandom().nextInt(5));
            if (!p.getInventory().add(loot)) p.drop(loot, false, net.minecraft.util.Prediction.SERVER_ONLY);
            p.sendSystemMessage(Component.translatable("rpgmod.treasure.stole", loot.getHoverName(), loot.getCount()).withStyle(ChatFormatting.GOLD), true);
            ServerFx.of("x_burst", mid(t)).a(1).b(4).color(GOLD).send(c.level());
            sound(c, SoundEvents.ITEM_PICKUP, 1.2F);
        } else {
            p.sendSystemMessage(Component.translatable("rpgmod.treasure.miss").withStyle(ChatFormatting.GRAY), true);
        }
        return true;
    }

    /** Coins left by the treasure rain stay this long, and each one picked up gives this (stacking to five). */
    static final int COIN_LIFE = 200, GOLD_RUSH_TICKS = 200;

    /**
     * Gold coins rain on the aimed area for 3s. Every fourth coin stays on the ground, spinning; a
     * player who walks over one picks it up: gold rush, +5% damage for 10s, stacking up to five.
     */
    private static boolean treasureRain(Ctx c) {
        ServerPlayer p = c.p();
        Vec3 o = aimPoint(c, 24);
        double r = c.def().radius();
        int life = ticks(c.def().duration());
        List<SkillProp> coins = new ArrayList<>();
        ServerFx.of("x_zone", o).a(r).b(life + 10).vec(new Vec3(1, 3, 0)).color(GOLD).send(c.level());
        sound(c, SoundEvents.PLAYER_LEVELUP, 1.6F);
        final int[] fallen = {0};
        SkillTasks.run(age -> {
            if (age % 2 == 0 && age < life) {
                Vec3 at = NewJobSkills.ground(c, NewJobSkills.spotIn(c, o, r, enemies(p, o, r)));
                ServerFx.of("n_coin_fall", at).b(6).send(c.level());
                SkillTasks.later(6, () -> {
                    List<LivingEntity> hit = enemies(p, at, 1.6);
                    for (LivingEntity e : hit) hurt(c, e, c.dmg(1), melee(c));
                    sound(c, at, SoundEvents.CHAIN_PLACE, 0.5F, 1.8F);
                    if (++fallen[0] % 4 == 0) coins.add(SkillProp.create(c.level(), SkillProp.COIN, at.add(0, 0.1, 0), 0, COIN_LIFE));
                });
            }
            // the coins on the ground: picked up by any player walking over them
            if (age % 4 == 0) {
                for (SkillProp coin : new ArrayList<>(coins)) {
                    if (coin.isRemoved()) {
                        coins.remove(coin);
                        continue;
                    }
                    for (ServerPlayer a : c.level().getEntitiesOfClass(ServerPlayer.class, coin.getBoundingBox().inflate(0.9), a -> a.isAlive() && !a.isSpectator())) {
                        MobEffectInstance cur = a.getEffect(ModEffects.GOLD_RUSH);
                        int amp = cur == null ? 0 : Math.min(4, cur.getAmplifier() + 1);
                        a.addEffect(new MobEffectInstance(ModEffects.GOLD_RUSH, GOLD_RUSH_TICKS, amp), p);
                        ServerFx.of("x_burst", coin.position().add(0, 0.4, 0)).a(0.8).b(4).color(GOLD).send(c.level());
                        c.level().playSound(null, coin.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, net.minecraft.sounds.SoundSource.PLAYERS, 0.8F, 1.4F + amp * 0.1F);
                        coin.discard();
                        coins.remove(coin);
                        break;
                    }
                }
            }
            return age >= life + COIN_LIFE;
        });
        return true;
    }

    /** A golden cannonball: a huge blast, half again as big when luck smiles. */
    private static boolean goldenCannon(Ctx c) {
        ServerPlayer p = c.p();
        boolean lucky = JobManager.luckyReroll(p) || AdvSkills.loaded(p);
        LivingEntity target = lookTarget(c, c.def().radius(), 10);
        Vec3 start = c.hand();
        Vec3 dir = target != null ? mid(target).subtract(start).normalize() : c.look();
        ServerFx.of("n_cannon_fire", start).vec(dir).send(c.level());
        ServerFx.of("x_bolt", start).vec(dir.scale(1.2)).a(c.def().radius()).b(0.7).color(0xFFD040).follow(target).send(c.level());
        sound(c, SoundEvents.GENERIC_EXPLODE, 0.8F);
        sound(c, p.position(), SoundEvents.BELL_BLOCK, 1.0F, 1.2F);
        ProjectileEnd boom = (at, dd, wall) -> {
            NewJobSkills.blast(c, at, lucky ? 4.5 : 3.5, c.dmg(lucky ? 1.5 : 1), 0, 0xFFD040);
            ServerFx.of("x_burst", at).a(lucky ? 3 : 2).b(4).color(0xFFD040).send(c.level());
            if (lucky) p.sendSystemMessage(Component.translatable("rpgmod.treasure.lucky").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
        };
        projectile(c, start, dir, 1.2, c.def().radius(), target, 0.2, (e, at, dd) -> boom.end(at, dd, false), boom);
        return true;
    }
}
