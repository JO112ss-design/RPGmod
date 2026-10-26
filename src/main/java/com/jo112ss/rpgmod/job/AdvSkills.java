package com.jo112ss.rpgmod.job;

import static com.jo112ss.rpgmod.job.SkillExecutor.*;

import com.jo112ss.rpgmod.job.SkillExecutor.Ctx;
import com.jo112ss.rpgmod.job.core.JobDefs;
import com.jo112ss.rpgmod.job.core.SkillDef;
import com.jo112ss.rpgmod.job.core.SkillKind;
import com.jo112ss.rpgmod.job.fx.ServerFx;
import com.jo112ss.rpgmod.registry.ModEffects;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The advanced jobs' skills (level 100+): five actives and an ultimate each, the spellmaster's eight
 * spells and the bishop's three. Shares the targeting / damage helpers of {@link SkillExecutor}; the
 * lasting effects (exposed, cover, undying, twin venom...) are read by {@link AdvancedHooks}.
 */
public final class AdvSkills {
    private AdvSkills() {}

    static final Set<String> IDS = Set.of(
            "blood_frenzy", "decapitate", "triple_whirl", "reckless_charge", "terror_roar", "asura",
            "iron_wall", "provoke_shield", "cover", "shield_wall", "reprisal", "bastion",
            "snipe", "rapid_fire", "weak_point", "ricochet", "roll", "dead_eye",
            "double_cast", "mana_overload", "mana_well", "phase_shift", "arcane_seal", "armageddon",
            "inferno", "absolute_zero", "thunderstorm", "stone_spikes", "radiant_lance", "dark_orb", "tornado", "starfall",
            "earth_rune", "ward_rune", "gale_rune", "night_sight", "feather_rune", "grand_rune",
            "switcheroo", "decoy", "loaded_dice", "card_storm", "vanish", "wildcard",
            "shadow_run", "flurry", "twin_venom", "shadow_stitch", "moon_shadow", "yaksha",
            "revive_prayer", "divine_domain", "great_miracle", "holy_bolt", "holy_nova", "light_pillar",
            "steam_burst", "plasma_ray", "frost_tempest");

    static boolean has(String id) {
        return IDS.contains(id);
    }

    static boolean run(Ctx c) {
        return switch (c.def().id()) {
            // berserker
            case "blood_frenzy" -> bloodFrenzy(c);
            case "decapitate" -> decapitate(c);
            case "triple_whirl" -> tripleWhirl(c);
            case "reckless_charge" -> recklessCharge(c);
            case "terror_roar" -> terrorRoar(c);
            case "asura" -> asura(c);
            // guardian
            case "iron_wall" -> selfMark(c, ModEffects.IRON_WALL, 0, 0xB8C0D0, 2, SoundEvents.ANVIL_LAND, 0.8F);
            case "provoke_shield" -> provokeShield(c);
            case "cover" -> cover(c);
            case "shield_wall" -> shieldWall(c);
            case "reprisal" -> reprisal(c);
            case "bastion" -> bastion(c);
            // sharpshooter
            case "snipe" -> snipe(c);
            case "rapid_fire" -> rapidFire(c);
            case "weak_point" -> weakPoint(c);
            case "ricochet" -> ricochet(c);
            case "roll" -> roll(c);
            case "dead_eye" -> deadEye(c);
            // spellmaster
            case "double_cast" -> selfMark(c, ModEffects.DOUBLE_CAST, c.def().amplifier(), 0x9A7BFF, 4, SoundEvents.ENCHANTMENT_TABLE_USE, 1.4F);
            case "mana_overload" -> selfMark(c, ModEffects.OVERLOAD, 0, 0xFF4AD8, 7, SoundEvents.BEACON_POWER_SELECT, 0.6F);
            case "mana_well" -> manaWell(c);
            case "phase_shift" -> phaseShift(c);
            case "arcane_seal" -> arcaneSeal(c);
            case "armageddon" -> UltSkills.greatBeam(c);
            case "inferno" -> inferno(c);
            case "absolute_zero" -> absoluteZero(c);
            case "thunderstorm" -> thunderstorm(c);
            case "stone_spikes" -> stoneSpikes(c);
            case "radiant_lance" -> lance(c, 0xFFF0A0, true);
            case "dark_orb" -> darkOrb(c);
            case "tornado" -> tornado(c);
            case "starfall" -> starfall(c);
            // runemaster
            case "earth_rune" -> earthRune(c);
            case "ward_rune" -> wardRune(c);
            case "gale_rune" -> partyMark(c, 0x9CFFB0, 3, SoundEvents.BREEZE_JUMP, 1.5F);
            case "night_sight" -> partyMark(c, 0x8FA8FF, 4, SoundEvents.AMETHYST_BLOCK_CHIME, 0.8F);
            case "feather_rune" -> partyMark(c, 0xE8F4FF, 3, SoundEvents.PHANTOM_FLAP, 1.6F);
            case "grand_rune" -> grandRune(c);
            // joker
            case "switcheroo" -> switcheroo(c);
            case "decoy" -> decoy(c);
            case "loaded_dice" -> selfMark(c, ModEffects.LOADED_DICE, 0, 0xFFD54A, 5, SoundEvents.NOTE_BLOCK_CHIME.value(), 1.6F);
            case "card_storm" -> cardStorm(c);
            case "vanish" -> vanish(c);
            case "wildcard" -> wildcard(c);
            // nightblade
            case "shadow_run" -> shadowRun(c);
            case "flurry" -> flurry(c);
            case "twin_venom" -> selfMark(c, ModEffects.TWIN_VENOM, 0, 0x60E040, 8, SoundEvents.BREWING_STAND_BREW, 0.8F);
            case "shadow_stitch" -> shadowStitch(c);
            case "moon_shadow" -> moonShadow(c);
            case "yaksha" -> yaksha(c);
            // bishop
            case "revive_prayer" -> revivePrayer(c);
            case "divine_domain" -> divineDomain(c);
            case "great_miracle" -> greatMiracle(c);
            case "holy_bolt" -> holyBolt(c);
            case "holy_nova" -> holyNova(c);
            case "light_pillar" -> lightPillar(c);
            // spellmaster fusions
            case "steam_burst" -> steamBurst(c);
            case "plasma_ray" -> plasmaRay(c);
            case "frost_tempest" -> frostTempest(c);
            default -> false;
        };
    }

    // ================================================================== shared bits

    private static final int BLOOD = 0xC01818, STEEL = 0xB8C0D0, GOLD = 0xFFD95A, SHOT = 0x8CFF6A, ARC = 0x9A7BFF,
            FIRE = 0xFF6A20, ICE = 0x9FE8FF, VOLT = 0xFFF07A, EARTH = 0xB08850, VOID = 0x7A3CFF, RUNE = 0xE070B0,
            JOKER = 0xB070FF, NIGHT = 0x5068E0, HOLY = 0xFFE89A;

    private static Vec3 mid(LivingEntity e) {
        return e.position().add(0, e.getBbHeight() * 0.5, 0);
    }

    private static void effect(LivingEntity e, Holder<MobEffect> h, int ticks, int amp, ServerPlayer src) {
        e.addEffect(new MobEffectInstance(h, ticks, amp), src);
    }

    /** A lasting mark on the caster (the hooks do the rest) with an aura. */
    private static boolean selfMark(Ctx c, Holder<MobEffect> h, int amp, int color, int style, net.minecraft.sounds.SoundEvent s, float pitch) {
        effect(c.p(), h, ticks(c.def().duration()), amp, c.p());
        apply(c.p(), c.def(), c.p());
        ServerFx.of("x_aura", c.p().position()).follow(c.p()).a(c.def().duration()).b(style).color(color).send(c.level());
        sound(c, s, pitch);
        return true;
    }

    /** The skill's listed effects on the party around the caster. */
    private static boolean partyMark(Ctx c, int color, int style, net.minecraft.sounds.SoundEvent s, float pitch) {
        List<ServerPlayer> allies = allies(c.p(), c.def().radius());
        for (ServerPlayer a : allies) apply(a, c.def(), c.p());
        ServerFx.of("x_aura", c.p().position()).follow(c.p()).a(Math.min(6, c.def().duration())).b(style).color(color).points(others(c.p(), allies)).send(c.level());
        ServerFx.of("x_ring", c.p().position()).a(c.def().radius()).b(1).color(color).send(c.level());
        sound(c, s, pitch);
        sound(c, c.p().position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.6F, 1.4F);
        return true;
    }

    /** Refreshes an effect only when it is about to run out (keeps regeneration ticking normally). */
    private static void keep(LivingEntity e, Holder<MobEffect> h, int ticks, int amp, ServerPlayer src) {
        MobEffectInstance cur = e.getEffect(h);
        if (cur == null || cur.getAmplifier() < amp || cur.getDuration() < ticks / 3) e.addEffect(new MobEffectInstance(h, ticks, amp), src);
    }

    private static List<ServerPlayer> playersIn(Ctx c, Vec3 o, double r) {
        return c.level().getEntitiesOfClass(ServerPlayer.class, new AABB(o, o).inflate(r, 3, r),
                a -> a.isAlive() && !a.isSpectator() && horizontal(a.position(), o) <= r);
    }

    private static boolean consumeLoaded(ServerPlayer p) {
        if (!p.hasEffect(ModEffects.LOADED_DICE)) return false;
        p.removeEffect(ModEffects.LOADED_DICE);
        p.sendSystemMessage(Component.translatable("rpgmod.joker.loaded_used").withStyle(ChatFormatting.GOLD), true);
        return true;
    }

    /** Joker's loaded die, for the gambler skills: true = this gamble lands on its best result. */
    static boolean loaded(ServerPlayer p) {
        return consumeLoaded(p);
    }

    private static LivingEntity nearestIn(List<LivingEntity> list, Vec3 to) {
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (LivingEntity e : list) {
            double d = e.position().distanceToSqr(to);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        return best;
    }

    // ================================================================== berserker

    /** Pays 15% of current health for strength II and haste II. */
    private static boolean bloodFrenzy(Ctx c) {
        ServerPlayer p = c.p();
        float cost = Math.min((float) (p.getHealth() * c.def().power()), Math.max(0, p.getHealth() - 1));
        p.setHealth(p.getHealth() - cost);
        apply(p, c.def(), p);
        ServerFx.of("x_aura", p.position()).follow(p).a(c.def().duration()).b(6).color(BLOOD).send(c.level());
        ServerFx.of("x_burst", mid(p)).a(1.4).b(3).color(BLOOD).send(c.level());
        sound(c, SoundEvents.RAVAGER_ROAR, 1.5F);
        sound(c, p.position(), SoundEvents.PLAYER_HURT_SWEET_BERRY_BUSH, 1.0F, 0.6F);
        return true;
    }

    /** One heavy downward cut; double damage on enemies at half health or less. */
    private static boolean decapitate(Ctx c) {
        LivingEntity target = nearestIn(cone(c, c.def().radius(), 50), c.p().position());
        if (target == null) {
            fail(c.p(), "rpgmod.job.no_target");
            return false;
        }
        boolean low = target.getHealth() <= target.getMaxHealth() * 0.5F;
        hurt(c, target, c.dmg(low ? 2 : 1), melee(c));
        ServerFx.of("x_slash", mid(target).add(0, 0.4, 0)).vec(c.flat()).a(low ? 3.2 : 2.4).b(1.45).color(low ? 0xFF2020 : BLOOD).send(c.level());
        ServerFx.of("x_hit", mid(target)).vec(c.flat()).a(low ? 1.6 : 1).color(BLOOD).send(c.level());
        sound(c, target.position(), SoundEvents.PLAYER_ATTACK_STRONG, 1.2F, 0.5F);
        if (low) {
            sound(c, target.position(), SoundEvents.ANVIL_LAND, 0.6F, 0.6F);
            c.p().sendSystemMessage(Component.translatable("rpgmod.skill_msg.decapitate").withStyle(ChatFormatting.RED), true);
        }
        return true;
    }

    /** Three spins, drawing enemies in between; the last one throws them out. */
    private static boolean tripleWhirl(Ctx c) {
        double r = c.def().radius();
        SkillTasks.run(age -> {
            if (!c.p().isAlive()) return true;
            if (age % 10 == 0) {
                List<LivingEntity> hit = enemies(c.p(), c.p().position(), r);
                for (LivingEntity e : hit) {
                    hurt(c, e, c.dmg(1), melee(c));
                    if (age == 20) pushAway(e, c.p().position(), 0.9, 0.4);
                    else push(e, c.p().position().subtract(e.position()), 0.25, 0.05);
                }
                ServerFx.of("whirlwind", c.p().position()).follow(c.p()).a(r).send(c.level());
                if (!hit.isEmpty()) ServerFx.of("hit", c.p().position()).color(BLOOD).a(1).points(hit).send(c.level());
                sound(c, SoundEvents.PLAYER_ATTACK_SWEEP, 0.6F + age * 0.02F);
                sound(c, c.p().position(), SoundEvents.BREEZE_WIND_CHARGE_BURST, 0.6F, 1.2F);
            }
            return age >= 20;
        });
        return true;
    }

    /** A long dash that throws enemies up - and leaves you open for a while. */
    private static boolean recklessCharge(Ctx c) {
        Vec3 dir = c.flat();
        Vec3 from = c.p().position();
        launch(c.p(), dir.scale(2.4).add(0, 0.2, 0));
        effect(c.p(), ModEffects.EXPOSED, ticks(c.def().duration()), 0, c.p());
        ServerFx.of("charge", from).vec(dir).follow(c.p()).send(c.level());
        sound(c, SoundEvents.RAVAGER_ROAR, 1.6F);
        sound(c, from, SoundEvents.BREEZE_JUMP, 1.0F, 0.6F);
        Set<Integer> done = new HashSet<>();
        SkillTasks.run(age -> {
            if (!c.p().isAlive()) return true;
            Vec3 front = c.p().position().add(dir.scale(0.8));
            List<LivingEntity> hit = new ArrayList<>();
            for (LivingEntity e : enemies(c.p(), front, 2.0)) {
                if (!done.add(e.getId())) continue;
                hurt(c, e, c.dmg(1), melee(c));
                push(e, dir, 1.2, 0.7);
                hit.add(e);
            }
            if (!hit.isEmpty()) {
                ServerFx.of("x_burst", front.add(0, 1, 0)).a(1.5).b(3).color(BLOOD).points(hit).send(c.level());
                sound(c, front, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.2F, 0.6F);
            }
            return age >= 12;
        });
        return true;
    }

    /** A roar: enemies around lose their attack and speed; you see their openings (crit up). */
    private static boolean terrorRoar(Ctx c) {
        double r = c.def().radius();
        List<LivingEntity> foes = enemies(c.p(), c.p().position(), r);
        for (LivingEntity e : foes) apply(e, c.def(), c.p());
        effect(c.p(), ModEffects.KEEN, ticks(c.def().duration()), 0, c.p());
        ServerFx.of("x_ring", c.p().position()).a(r).b(3).color(BLOOD).points(foes).send(c.level());
        ServerFx.of("x_aura", c.p().position()).follow(c.p()).a(c.def().duration()).b(1).color(0xFFB040).send(c.level());
        sound(c, SoundEvents.RAVAGER_ROAR, 0.7F);
        sound(c, c.p().position(), SoundEvents.WARDEN_ROAR, 0.5F, 1.4F);
        return true;
    }

    private static boolean asura(Ctx c) {
        effect(c.p(), ModEffects.ASURA, ticks(c.def().duration()), 0, c.p());
        ServerFx.of("u_asura", c.p().position()).follow(c.p()).a(c.def().duration()).send(c.level());
        sound(c, SoundEvents.WARDEN_ROAR, 0.8F);
        sound(c, c.p().position(), SoundEvents.FIRECHARGE_USE, 0.8F, 0.5F);
        return true;
    }

    // ================================================================== guardian

    /** Every monster around turns on you; your armour hardens. */
    private static boolean provokeShield(Ctx c) {
        List<LivingEntity> foes = enemies(c.p(), c.p().position(), c.def().radius());
        for (LivingEntity e : foes) if (e instanceof Mob m) m.setTarget(c.p());
        effect(c.p(), ModEffects.GUARD_UP, ticks(c.def().duration()), c.def().amplifier(), c.p());
        ServerFx.of("taunt", c.p().position()).follow(c.p()).a(c.def().radius()).points(foes).send(c.level());
        ServerFx.of("x_aura", c.p().position()).follow(c.p()).a(c.def().duration()).b(2).color(0xE0C060).send(c.level());
        sound(c, SoundEvents.SHIELD_BLOCK, 0.6F);
        sound(c, c.p().position(), SoundEvents.RAVAGER_ROAR, 0.6F, 1.2F);
        return true;
    }

    /** Takes the hits meant for allies nearby (see AdvancedHooks). */
    private static boolean cover(Ctx c) {
        effect(c.p(), ModEffects.COVER, ticks(c.def().duration()), 0, c.p());
        List<ServerPlayer> allies = others(c.p(), allies(c.p(), c.def().radius()));
        ServerFx.of("x_aura", c.p().position()).follow(c.p()).a(c.def().duration()).b(10).color(0xF0D080).points(allies).send(c.level());
        for (ServerPlayer a : allies) ServerFx.of("x_link", c.chest()).vec(mid(a)).color(0xF0D080).send(c.level());
        sound(c, SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 0.8F);
        sound(c, c.p().position(), SoundEvents.BEACON_POWER_SELECT, 0.8F, 1.3F);
        return true;
    }

    /** A wall of light a few blocks ahead: stops enemy projectiles and holds monsters back. */
    private static boolean shieldWall(Ctx c) {
        Vec3 n = c.flat();
        Vec3 centre = c.p().position().add(n.scale(3));
        double hw = c.def().radius();
        int life = ticks(c.def().duration());
        ServerFx.of("x_wall", centre).vec(n).a(hw).b(life).color(GOLD).send(c.level());
        sound(c, SoundEvents.BEACON_ACTIVATE, 1.5F);
        sound(c, centre, SoundEvents.SHIELD_BLOCK, 1.0F, 0.6F);
        Vec3 side = new Vec3(-n.z, 0, n.x);
        SkillTasks.run(age -> {
            AABB box = new AABB(centre, centre).inflate(hw + 2, 5, hw + 2);
            for (LivingEntity e : c.level().getEntitiesOfClass(LivingEntity.class, box, e -> isEnemy(c.p(), e))) {
                Vec3 rel = e.position().subtract(centre);
                double front = rel.dot(n), lat = Math.abs(rel.dot(side));
                if (lat > hw + e.getBbWidth() / 2 || rel.y < -2 || rel.y > 4) continue;
                if (front < -0.4 || front > 1.2 + e.getBbWidth() / 2) continue;
                // keep it on the far side
                if (front < 0.6) e.teleportTo(e.getX() + n.x * (0.6 - front), e.getY(), e.getZ() + n.z * (0.6 - front));
                e.setDeltaMovement(n.x * 0.45, Math.max(0.1, e.getDeltaMovement().y), n.z * 0.45);
                e.needsSync = true;
                if (age % 5 == 0) ServerFx.of("dome_block", e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(n.scale(Math.max(0, front)))).vec(n).send(c.level());
            }
            for (var proj : c.level().getEntitiesOfClass(net.minecraft.world.entity.projectile.Projectile.class, box, pr -> !(pr.getOwner() instanceof Player))) {
                Vec3 rel = proj.position().subtract(centre);
                if (Math.abs(rel.dot(side)) <= hw && rel.y > -1 && rel.y < 5 && Math.abs(rel.dot(n)) < 1.5) {
                    ServerFx.of("dome_block", proj.position()).vec(n).send(c.level());
                    sound(c, proj.position(), SoundEvents.SHIELD_BLOCK, 0.8F, 1.4F);
                    proj.discard();
                }
            }
            if (age == life - 20) sound(c, centre, SoundEvents.BEACON_DEACTIVATE, 0.8F, 1.3F);
            return age >= life - 1;
        });
        return true;
    }

    /** Melee attackers take damage back (the skill's ratio of your attack plus half of their hit). */
    private static boolean reprisal(Ctx c) {
        AdvancedHooks.REPRISAL_POWER.put(c.p().getUUID(), c.dmg(1));
        return selfMark(c, ModEffects.REPRISAL, 0, 0xFF9040, 2, SoundEvents.SHIELD_BLOCK.value(), 1.4F);
    }

    /** The party is protected for a while; you yourself cannot fall. */
    private static boolean bastion(Ctx c) {
        List<ServerPlayer> allies = allies(c.p(), c.def().radius());
        for (ServerPlayer a : allies) apply(a, c.def(), c.p());
        effect(c.p(), ModEffects.UNDYING, ticks(c.def().duration()), 0, c.p());
        ServerFx.of("u_bastion", c.p().position()).follow(c.p()).a(c.def().radius()).b(c.def().duration()).points(others(c.p(), allies)).send(c.level());
        sound(c, SoundEvents.BEACON_ACTIVATE, 0.9F);
        sound(c, c.p().position(), SoundEvents.ANVIL_USE, 0.8F, 0.6F);
        sound(c, c.p().position(), SoundEvents.TOTEM_USE, 0.5F, 1.2F);
        return true;
    }

    // ================================================================== sharpshooter

    /** Aims for half a second, then a shot that goes through armour along the crosshair. */
    private static boolean snipe(Ctx c) {
        int aim = 10;
        ServerFx.of("x_aim", c.p().position()).follow(c.p()).b(aim).color(0xFF4040).send(c.level());
        sound(c, SoundEvents.CROSSBOW_LOADING_MIDDLE.value(), 1.2F);
        SkillTasks.later(aim, () -> {
            if (!c.p().isAlive()) return;
            Vec3 eye = c.p().getEyePosition();
            Vec3 look = c.p().getLookAngle();
            double range = c.def().radius();
            BlockHitResult block = c.level().clip(new ClipContext(eye, eye.add(look.scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, c.p()));
            Vec3 end = block.getType() == HitResult.Type.MISS ? eye.add(look.scale(range)) : block.getLocation();
            LivingEntity hit = null;
            double best = Double.MAX_VALUE;
            for (LivingEntity e : c.level().getEntitiesOfClass(LivingEntity.class, new AABB(eye, end).inflate(1), e -> isEnemy(c.p(), e))) {
                var at = e.getBoundingBox().inflate(0.35).clip(eye, end);
                if (at.isPresent() && at.get().distanceToSqr(eye) < best) {
                    best = at.get().distanceToSqr(eye);
                    hit = e;
                }
            }
            if (hit != null) {
                end = eye.add(look.scale(Math.sqrt(best)));
                hurt(c, hit, c.dmg(1), magic(c));
                push(hit, look, 0.8, 0.2);
                ServerFx.of("x_hit", end).vec(look).a(1.6).color(0xFF6040).send(c.level());
            }
            ServerFx.of("x_beam", c.hand()).vec(end).a(0.35).color(0xFFE0C0).send(c.level());
            sound(c, SoundEvents.CROSSBOW_SHOOT, 0.5F);
            sound(c, c.p().position(), SoundEvents.GENERIC_EXPLODE.value(), 0.5F, 1.8F);
            if (hit != null) sound(c, hit.position(), SoundEvents.PLAYER_ATTACK_CRIT, 1.2F, 0.6F);
        });
        return true;
    }

    /**
     * Eight shots in quick succession wherever you aim. Simulated shots with skill damage: real arrows
     * this close together would mostly bounce off the target's hit cooldown.
     */
    private static boolean rapidFire(Ctx c) {
        SkillTasks.run(age -> {
            if (!c.p().isAlive()) return true;
            if (age % 3 == 0) {
                Vec3 start = c.hand();
                Vec3 dir = c.look().add(new Vec3(c.p().getRandom().nextGaussian(), c.p().getRandom().nextGaussian(), c.p().getRandom().nextGaussian()).scale(0.02)).normalize();
                LivingEntity target = lookTarget(c, 40, 4);
                ServerFx.of("x_bolt", start).vec(dir.scale(3)).a(40).b(0.18).color(SHOT).follow(target).send(c.level());
                projectile(c, start, dir, 3, 40, target, 0.15, (e, at, d) -> {
                    hurt(c, e, c.dmg(1), melee(c));
                    ServerFx.of("hit", at).color(SHOT).a(0.6).point(at).send(c.level());
                }, (at, d, wall) -> { });
                sound(c, c.p().position(), SoundEvents.ARROW_SHOOT, 0.7F, 1.3F + age * 0.02F);
            }
            return age >= 21;
        });
        ServerFx.of("multishot", c.hand()).vec(c.look()).send(c.level());
        return true;
    }

    /** Marks the weak point of the targeted enemy (and those right next to it): they take more damage. */
    private static boolean weakPoint(Ctx c) {
        LivingEntity target = lookTarget(c, c.def().radius(), 12);
        if (target == null) {
            fail(c.p(), "rpgmod.job.no_target");
            return false;
        }
        int t = ticks(c.def().duration());
        List<LivingEntity> marked = new ArrayList<>(List.of(target));
        for (LivingEntity e : enemies(c.p(), target.position(), 4)) if (e != target) marked.add(e);
        for (LivingEntity e : marked) {
            apply(e, c.def(), c.p());
            effect(e, ModEffects.EXPOSED, t, e == target ? 1 : 0, c.p());
            ServerFx.of("x_mark", e.position()).follow(e).a(c.def().duration()).color(e == target ? 0xFF3030 : 0xFF9050).send(c.level());
        }
        ServerFx.of("x_beam", c.hand()).vec(mid(target)).a(0.08).color(0xFF5050).send(c.level());
        sound(c, SoundEvents.SPYGLASS_USE, 0.8F);
        sound(c, target.position(), SoundEvents.AMETHYST_CLUSTER_BREAK, 1.0F, 1.8F);
        return true;
    }

    /** A shot that bounces from enemy to enemy (five hits, a little weaker each time). */
    private static boolean ricochet(Ctx c) {
        LivingEntity target = lookTarget(c, c.def().radius(), 15);
        Vec3 start = c.hand();
        Vec3 dir = target != null ? mid(target).subtract(start).normalize() : c.look();
        sound(c, SoundEvents.CROSSBOW_SHOOT, 1.4F);
        bounce(c, start, dir, target, c.def().radius(), 0, new HashSet<>());
        return true;
    }

    private static void bounce(Ctx c, Vec3 start, Vec3 dir, LivingEntity target, double range, int n, Set<Integer> done) {
        ServerFx.of("x_bolt", start).vec(dir.scale(2.2)).a(range).b(0.35).color(0xD8FF7A).follow(target).send(c.level());
        projectile(c, start, dir, 2.2, range, target, 0.35, (e, at, d) -> {
            done.add(e.getId());
            hurt(c, e, c.dmg(Math.pow(0.85, n)), melee(c));
            ServerFx.of("x_hit", at).vec(d).a(0.8).color(0xD8FF7A).send(c.level());
            sound(c, at, SoundEvents.ANVIL_PLACE, 0.4F, 1.9F);
            if (n >= 4) return;
            LivingEntity next = null;
            double bd = Double.MAX_VALUE;
            for (LivingEntity o : enemies(c.p(), e.position(), 10)) {
                if (done.contains(o.getId()) || !canSee(c.level(), at, mid(o), c.p())) continue;
                double dd = o.distanceToSqr(e);
                if (dd < bd) {
                    bd = dd;
                    next = o;
                }
            }
            if (next == null) return;
            LivingEntity nx = next;
            Vec3 from = at;
            SkillTasks.later(1, () -> {
                if (nx.isAlive()) bounce(c, from, mid(nx).subtract(from).normalize(), nx, 12, n + 1, done);
            });
        }, (at, d, wall) -> ServerFx.of("x_hit", at).vec(d).a(0.4).color(0xD8FF7A).send(c.level()));
    }

    /** A low roll backwards; the next arrow from your bow hits two and a half times as hard. */
    private static boolean roll(Ctx c) {
        Vec3 dir = c.flat();
        ServerFx.of("backstep", c.p().position()).vec(dir.scale(-1)).follow(c.p()).send(c.level());
        launch(c.p(), dir.scale(-c.def().power()).add(0, 0.25, 0));
        effect(c.p(), ModEffects.EMPOWERED_SHOT, ticks(c.def().duration()), 0, c.p());
        ServerFx.of("x_aura", c.p().position()).follow(c.p()).a(c.def().duration()).b(0).color(SHOT).send(c.level());
        sound(c, SoundEvents.BREEZE_JUMP, 1.6F);
        sound(c, c.p().position(), SoundEvents.CROSSBOW_LOADING_END.value(), 1.0F, 1.2F);
        return true;
    }

    /** Marks every enemy in sight, then a homing shot flies at each of them. */
    private static boolean deadEye(Ctx c) {
        Vec3 eye = c.p().getEyePosition();
        List<LivingEntity> targets = new ArrayList<>();
        for (LivingEntity e : enemies(c.p(), c.p().position(), c.def().radius())) {
            if (canSee(c.level(), eye, mid(e), c.p())) targets.add(e);
        }
        if (targets.isEmpty()) {
            fail(c.p(), "rpgmod.job.no_target");
            return false;
        }
        targets.sort(Comparator.comparingDouble(e -> e.distanceToSqr(c.p())));
        if (targets.size() > 24) targets = new ArrayList<>(targets.subList(0, 24));
        for (LivingEntity e : targets) ServerFx.of("u_dead_mark", e.position()).follow(e).send(c.level());
        ServerFx.of("u_dead_eye", c.p().position()).follow(c.p()).send(c.level());
        sound(c, SoundEvents.SPYGLASS_USE, 0.6F);
        sound(c, c.p().position(), SoundEvents.BELL_RESONATE, 0.8F, 1.8F);
        // at least four shots: with few enemies in sight, they take several each
        List<LivingEntity> fire = new ArrayList<>();
        for (int i = 0; i < Math.max(4, targets.size()); i++) fire.add(targets.get(i % targets.size()));
        for (int i = 0; i < fire.size(); i++) {
            LivingEntity t = fire.get(i);
            SkillTasks.later(14 + i * 2, () -> {
                if (!t.isAlive() || !c.p().isAlive()) return;
                Vec3 start = c.hand();
                Vec3 dir = mid(t).subtract(start).normalize();
                ServerFx.of("x_bolt", start).vec(dir.scale(2.5)).a(c.def().radius() + 8).b(0.4).color(0xFF5040).follow(t).send(c.level());
                sound(c, c.p().position(), SoundEvents.ARROW_SHOOT, 0.6F, 1.6F);
                projectile(c, start, dir, 2.5, c.def().radius() + 8, t, 0.5, (e, at, d) -> {
                    hurt(c, e, c.dmg(1), melee(c));
                    ServerFx.of("x_hit", at).vec(d).a(1.2).color(0xFF5040).send(c.level());
                    sound(c, at, SoundEvents.PLAYER_ATTACK_CRIT, 0.8F, 1.2F);
                }, (at, d, wall) -> { });
            });
        }
        return true;
    }

    // ================================================================== spellmaster

    /** A spring of mana: allies standing in it regain mana every second. */
    private static boolean manaWell(Ctx c) {
        Vec3 o = c.p().position();
        double r = c.def().radius();
        int life = ticks(c.def().duration());
        ServerFx.of("x_zone", o).vec(new Vec3(0, 0, 0)).a(r).b(life).color(0x6FB8FF).send(c.level());
        sound(c, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.6F);
        sound(c, o, SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, 1.0F, 1.2F);
        SkillTasks.run(age -> {
            if (age % 20 == 10) {
                for (ServerPlayer a : playersIn(c, o, r)) {
                    JobManager.restoreMana(a, c.def().power() * c.boost());
                    ServerFx.of("heal_burst", a.position()).follow(a).color(0x6FB8FF).send(c.level());
                }
                sound(c, o, SoundEvents.AMETHYST_BLOCK_CHIME, 0.6F, 1.4F);
            }
            return age >= life - 1;
        });
        return true;
    }

    /** Blink, untouchable for a moment as you come out. */
    private static boolean phaseShift(Ctx c) {
        Vec3 from = c.p().position();
        Vec3 to = safeTeleport(c, c.look(), c.def().radius());
        c.p().teleportTo(to.x, to.y, to.z);
        c.p().resetFallDistance();
        effect(c.p(), MobEffects.RESISTANCE, 30, 4, c.p());
        ServerFx.of("blink", from).vec(to).send(c.level());
        ServerFx.of("x_aura", to).follow(c.p()).a(1.5).b(4).color(ARC).send(c.level());
        sound(c, from, SoundEvents.ENDERMAN_TELEPORT, 0.8F, 0.8F);
        sound(c, to, SoundEvents.AMETHYST_CLUSTER_BREAK, 1.0F, 1.2F);
        return true;
    }

    /** A seal on the ground: enemies inside are slowed and take more damage. */
    private static boolean arcaneSeal(Ctx c) {
        Vec3 at = aimPoint(c, 24);
        double r = c.def().radius();
        int life = ticks(c.def().duration());
        ServerFx.of("x_zone", at).vec(new Vec3(2, 3, 0)).a(r).b(life).color(VOID).send(c.level());
        sound(c, SoundEvents.EVOKER_PREPARE_SUMMON, 1.1F);
        SkillTasks.run(age -> {
            if (age % 10 == 0) for (LivingEntity e : enemies(c.p(), at, r)) apply(e, c.def(), c.p(), 1.5 / Math.max(0.1, c.def().duration()));
            return age >= life - 1;
        });
        return true;
    }

    /** Drops a point onto the ground below it. */
    private static Vec3 ground(Ctx c, Vec3 at) {
        BlockHitResult down = c.level().clip(new ClipContext(at.add(0, 4, 0), at.add(0, -16, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, c.p()));
        return down.getType() == HitResult.Type.MISS ? at : down.getLocation();
    }

    /** Spell: a pillar of hellfire bursts up, then the ground keeps burning. */
    private static boolean inferno(Ctx c) {
        Vec3 at = aimPoint(c, 26);
        double r = c.def().radius();
        int life = ticks(c.def().duration());
        List<LivingEntity> hit = enemies(c.p(), at, r);
        for (LivingEntity e : hit) {
            hurt(c, e, c.dmg(1), magic(c));
            e.igniteForSeconds(6);
            pushAway(e, at, 0.4, 0.5);
        }
        ServerFx.of("x_burst", at).a(r).b(0).color(FIRE).points(hit).send(c.level());
        ServerFx.of("x_zone", at).vec(new Vec3(2, 1, 0)).a(r).b(life).color(FIRE).send(c.level());
        sound(c, at, SoundEvents.BLAZE_SHOOT, 1.5F, 0.5F);
        sound(c, at, SoundEvents.GENERIC_EXPLODE.value(), 1.2F, 0.8F);
        SkillTasks.run(age -> {
            if (age > 0 && age % 10 == 0) {
                for (LivingEntity e : enemies(c.p(), at, r)) {
                    hurt(c, e, c.dmg(0.2), magic(c));
                    e.igniteForSeconds(3);
                }
                sound(c, at, SoundEvents.FIRE_AMBIENT, 1.0F, 0.8F);
            }
            return age >= life;
        });
        return true;
    }

    /** Spell: everything around the aimed spot freezes solid. */
    private static boolean absoluteZero(Ctx c) {
        Vec3 at = aimPoint(c, 24);
        double r = c.def().radius();
        int t = ticks(c.def().duration());
        ServerFx.of("x_zone", at).vec(new Vec3(3, 2, 0)).a(r).b(12).color(ICE).send(c.level());
        sound(c, SoundEvents.POWDER_SNOW_STEP, 0.5F);
        SkillTasks.later(12, () -> {
            List<LivingEntity> hit = enemies(c.p(), at, r);
            for (LivingEntity e : hit) {
                hurt(c, e, c.dmg(1), magic(c));
                effect(e, MobEffects.SLOWNESS, t, 9, c.p());
                e.setTicksFrozen(Math.max(e.getTicksFrozen(), e.getTicksRequiredToFreeze() + t));
                e.setDeltaMovement(0, Math.min(0, e.getDeltaMovement().y), 0);
                e.needsSync = true;
                ServerFx.of("frozen", e.position()).follow(e).a(e.getBbHeight()).b(e.getBbWidth()).send(c.level());
            }
            ServerFx.of("x_burst", at).a(r).b(1).color(ICE).points(hit).send(c.level());
            sound(c, at, SoundEvents.GLASS_BREAK, 1.5F, 0.5F);
            sound(c, at, SoundEvents.PLAYER_HURT_FREEZE, 1.2F, 0.5F);
        });
        return true;
    }

    /** Spell: a storm cloud over the aimed area; six bolts come down on the enemies under it. */
    private static boolean thunderstorm(Ctx c) {
        Vec3 centre = aimPoint(c, 28);
        double area = c.def().radius();
        ServerFx.of("x_zone", centre).vec(new Vec3(3, 3, 0)).a(area).b(66).color(VOLT).send(c.level());
        sound(c, SoundEvents.EVOKER_PREPARE_ATTACK, 0.8F);
        for (int i = 0; i < 6; i++) {
            SkillTasks.later(6 + i * 10, () -> {
                List<LivingEntity> in = enemies(c.p(), centre, area);
                Vec3 at;
                if (!in.isEmpty()) at = in.get(c.p().getRandom().nextInt(in.size())).position();
                else {
                    double a = c.p().getRandom().nextDouble() * Math.PI * 2, d = c.p().getRandom().nextDouble() * area;
                    at = ground(c, centre.add(Math.cos(a) * d, 0, Math.sin(a) * d));
                }
                List<LivingEntity> hit = enemies(c.p(), at, 2.5);
                for (LivingEntity e : hit) hurt(c, e, c.dmg(1), magic(c));
                LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(c.level(), EntitySpawnReason.TRIGGERED);
                if (bolt != null) {
                    bolt.snapTo(at.x, at.y, at.z);
                    bolt.setVisualOnly(true);
                    c.level().addFreshEntity(bolt);
                }
                ServerFx.of("x_strike", at).a(2.5).color(VOLT).points(hit).send(c.level());
            });
        }
        return true;
    }

    /** Spell: stone spikes burst out of the ground in a line, throwing enemies up. */
    private static boolean stoneSpikes(Ctx c) {
        Vec3 o = c.p().position();
        Vec3 dir = c.flat();
        double len = c.def().radius();
        ServerFx.of("x_spikes", o).vec(dir).a(len).color(EARTH).send(c.level());
        sound(c, SoundEvents.MACE_SMASH_GROUND, 0.6F);
        Set<Integer> done = new HashSet<>();
        SkillTasks.run(age -> {
            double front = 1 + age;
            for (LivingEntity e : c.level().getEntitiesOfClass(LivingEntity.class, new AABB(o, o).inflate(len + 2, 4, len + 2), e -> isEnemy(c.p(), e))) {
                Vec3 to = e.position().subtract(o);
                double along = to.dot(dir);
                double side = to.subtract(dir.scale(along)).multiply(1, 0, 1).length();
                if (along < 0 || along > front || side > 1.6 + e.getBbWidth() / 2 || Math.abs(to.y) > 3 || !done.add(e.getId())) continue;
                hurt(c, e, c.dmg(1), magic(c));
                push(e, dir, 0.2, 0.85);
            }
            if (age % 2 == 0) sound(c, o.add(dir.scale(front)), SoundEvents.POINTED_DRIPSTONE_LAND, 1.0F, 0.6F);
            return front >= len;
        });
        return true;
    }

    /** Spell: a lance of light that goes through every enemy in a line (more so the undead). */
    private static boolean lance(Ctx c, int color, boolean holy) {
        Vec3 eye = c.p().getEyePosition();
        Vec3 look = c.look();
        double range = c.def().radius();
        BlockHitResult block = c.level().clip(new ClipContext(eye, eye.add(look.scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, c.p()));
        Vec3 end = block.getType() == HitResult.Type.MISS ? eye.add(look.scale(range)) : block.getLocation();
        List<LivingEntity> hit = new ArrayList<>();
        for (LivingEntity e : c.level().getEntitiesOfClass(LivingEntity.class, new AABB(eye, end).inflate(1.2), e -> isEnemy(c.p(), e))) {
            if (e.getBoundingBox().inflate(0.6).clip(eye, end).isPresent()) hit.add(e);
        }
        for (LivingEntity e : hit) {
            hurt(c, e, c.dmg(1), magic(c));
            push(e, look, 0.4, 0.15);
        }
        ServerFx.of("x_beam", c.hand()).vec(end).a(0.7).color(color).points(hit).send(c.level());
        sound(c, SoundEvents.BEACON_POWER_SELECT, 2.0F);
        sound(c, c.p().position(), SoundEvents.TRIDENT_THUNDER.value(), 0.5F, 1.8F);
        return true;
    }

    /** Spell: a slow orb of darkness that tears at everything it passes and bursts at the end. */
    private static boolean darkOrb(Ctx c) {
        Vec3 start = c.hand();
        Vec3 dir = c.flat(); // level, at chest height (aimed down it would burst on the ground at once)
        double speed = 0.4, range = c.def().radius();
        ServerFx.of("x_orb", start).vec(dir.scale(speed)).a(range).b(1.1).color(VOID).send(c.level());
        sound(c, SoundEvents.WITHER_SHOOT, 0.6F);
        final Vec3[] pos = {start};
        final double[] gone = {0};
        SkillTasks.run(age -> {
            Vec3 to = pos[0].add(dir.scale(speed));
            BlockHitResult block = c.level().clip(new ClipContext(pos[0], to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, c.p()));
            boolean wall = block.getType() != HitResult.Type.MISS;
            pos[0] = wall ? block.getLocation() : to;
            gone[0] += speed;
            if (age % 5 == 0) {
                for (LivingEntity e : c.level().getEntitiesOfClass(LivingEntity.class, new AABB(pos[0], pos[0]).inflate(2.5), e -> isEnemy(c.p(), e))) {
                    if (mid(e).distanceTo(pos[0]) > 2.5 + e.getBbWidth() / 2) continue;
                    hurt(c, e, c.dmg(1), magic(c));
                    Vec3 pull = pos[0].subtract(e.position());
                    e.setDeltaMovement(e.getDeltaMovement().add(pull.normalize().scale(0.12)));
                    e.needsSync = true;
                }
            }
            if (wall || gone[0] >= range) {
                List<LivingEntity> hit = enemies(c.p(), pos[0], 3.5);
                for (LivingEntity e : hit) {
                    hurt(c, e, c.dmg(3), magic(c));
                    pushAway(e, pos[0], 0.6, 0.4);
                }
                ServerFx.of("x_burst", pos[0]).a(3.5).b(3).color(VOID).points(hit).send(c.level());
                sound(c, pos[0], SoundEvents.WARDEN_SONIC_BOOM, 0.8F, 0.8F);
                return true;
            }
            return false;
        });
        return true;
    }

    /** Spell: a whirlwind that crawls forward, lifting and grinding enemies. */
    private static boolean tornado(Ctx c) {
        Vec3 dir = c.flat();
        Vec3 start = c.p().position().add(dir.scale(2));
        double speed = 0.25, r = c.def().radius();
        int life = ticks(c.def().duration());
        ServerFx.of("x_tornado", start).vec(dir.scale(speed)).a(r).b(life).color(0xC8F0E0).send(c.level());
        sound(c, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 0.5F);
        SkillTasks.run(age -> {
            Vec3 at = start.add(dir.scale(speed * age));
            for (LivingEntity e : enemies(c.p(), at, r)) {
                Vec3 in = at.subtract(e.position()).multiply(1, 0, 1);
                double resist = 1 - e.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE) * 0.6;
                Vec3 swirl = new Vec3(-in.z, 0, in.x).normalize().scale(0.18);
                e.setDeltaMovement(in.scale(0.08).add(swirl).add(dir.scale(speed)).scale(resist).add(0, Math.min(0.25, 0.08 + e.getDeltaMovement().y * 0.5), 0));
                e.needsSync = true;
                e.resetFallDistance();
                if (age % 10 == 0) hurt(c, e, c.dmg(1), magic(c));
            }
            if (age % 10 == 0) sound(c, at, SoundEvents.BREEZE_IDLE_GROUND, 1.0F, 0.6F);
            return age >= life;
        });
        return true;
    }

    /** Spell: stars rain down on the aimed area for three seconds. */
    private static boolean starfall(Ctx c) {
        Vec3 centre = aimPoint(c, 28);
        double area = c.def().radius();
        int life = ticks(c.def().duration());
        ServerFx.of("x_zone", centre).vec(new Vec3(0, 3, 0)).a(area).b(life + 8).color(0xB0A0FF).send(c.level());
        sound(c, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.6F);
        SkillTasks.run(age -> {
            if (age % 4 == 0) {
                List<LivingEntity> in = enemies(c.p(), centre, area);
                Vec3 at;
                if (!in.isEmpty() && c.p().getRandom().nextBoolean()) at = in.get(c.p().getRandom().nextInt(in.size())).position();
                else {
                    double a = c.p().getRandom().nextDouble() * Math.PI * 2, d = Math.sqrt(c.p().getRandom().nextDouble()) * area;
                    at = ground(c, centre.add(Math.cos(a) * d, 0, Math.sin(a) * d));
                }
                ServerFx.of("x_star", at).a(1.8).b(6).color(0xC8B8FF).send(c.level());
                Vec3 land = at;
                SkillTasks.later(6, () -> {
                    for (LivingEntity e : enemies(c.p(), land, 1.8)) hurt(c, e, c.dmg(1), magic(c));
                    sound(c, land, SoundEvents.AMETHYST_CLUSTER_BREAK, 0.8F, 0.8F + c.p().getRandom().nextFloat() * 0.6F);
                });
            }
            return age >= life;
        });
        return true;
    }

    // ================================================================== runemaster

    /** A rune of earth on the aimed ground: enemies on it are slowed, weakened and ground down. */
    private static boolean earthRune(Ctx c) {
        Vec3 at = aimPoint(c, 24);
        double r = c.def().radius();
        int life = ticks(c.def().duration());
        ServerFx.of("x_zone", at).vec(new Vec3(4, 4, 0)).a(r).b(life).color(EARTH).send(c.level());
        sound(c, SoundEvents.ROOTED_DIRT_BREAK, 0.6F);
        sound(c, at, SoundEvents.EVOKER_PREPARE_SUMMON, 0.8F, 0.8F);
        SkillTasks.run(age -> {
            if (age % 10 == 0) {
                for (LivingEntity e : enemies(c.p(), at, r)) {
                    effect(e, MobEffects.SLOWNESS, 30, c.def().amplifier(), c.p());
                    effect(e, ModEffects.ATTACK_DOWN, 30, c.def().amplifier(), c.p());
                    if (age % 20 == 0) hurt(c, e, c.dmg(1), magic(c));
                }
            }
            return age >= life - 1;
        });
        return true;
    }

    /** A rune of protection under your feet: allies on it have armour and regenerate. */
    private static boolean wardRune(Ctx c) {
        Vec3 o = c.p().position();
        double r = c.def().radius();
        int life = ticks(c.def().duration());
        ServerFx.of("x_zone", o).vec(new Vec3(1, 0, 0)).a(r).b(life).color(0x9CFF9A).send(c.level());
        sound(c, SoundEvents.BEACON_ACTIVATE, 1.6F);
        SkillTasks.run(age -> {
            if (age % 10 == 0) {
                for (ServerPlayer a : playersIn(c, o, r)) {
                    keep(a, MobEffects.RESISTANCE, 30, 0, c.p());
                    keep(a, MobEffects.REGENERATION, 60, 0, c.p());
                }
            }
            return age >= life - 1;
        });
        return true;
    }

    /** The great rune circle: allies inside grow stronger, enemies weaker and hurt. */
    private static boolean grandRune(Ctx c) {
        Vec3 o = c.p().position();
        double r = c.def().radius();
        int life = ticks(c.def().duration());
        ServerFx.of("u_grand_rune", o).a(r).b(life).send(c.level());
        sound(c, SoundEvents.BEACON_ACTIVATE, 0.7F);
        sound(c, o, SoundEvents.ENCHANTMENT_TABLE_USE, 1.2F, 0.6F);
        SkillTasks.run(age -> {
            if (age % 10 == 0) {
                for (ServerPlayer a : playersIn(c, o, r)) {
                    keep(a, MobEffects.STRENGTH, 40, 0, c.p());
                    keep(a, MobEffects.RESISTANCE, 40, 0, c.p());
                    keep(a, MobEffects.SPEED, 40, 0, c.p());
                    keep(a, MobEffects.REGENERATION, 60, 0, c.p());
                }
                for (LivingEntity e : enemies(c.p(), o, r)) {
                    effect(e, MobEffects.SLOWNESS, 40, 0, c.p());
                    effect(e, MobEffects.WEAKNESS, 40, 0, c.p());
                    effect(e, ModEffects.ATTACK_DOWN, 40, 0, c.p());
                    if (age % 20 == 0) hurt(c, e, c.dmg(1), magic(c));
                }
            }
            return age >= life - 1;
        });
        return true;
    }

    // ================================================================== joker

    private static boolean isBig(LivingEntity e) {
        return e.is(net.neoforged.neoforge.common.Tags.EntityTypes.BOSSES) || e instanceof com.jo112ss.rpgmod.mob.boss.BloodMoonBoss;
    }

    /** Trade places with the targeted enemy, leaving it dazed. */
    private static boolean switcheroo(Ctx c) {
        LivingEntity target = lookTarget(c, c.def().radius(), 10);
        if (target == null || isBig(target)) {
            fail(c.p(), "rpgmod.job.no_target");
            return false;
        }
        Vec3 me = c.p().position(), them = target.position();
        if (!c.level().noCollision(c.p(), c.p().getBoundingBox().move(them.subtract(me)))) {
            fail(c.p(), "rpgmod.job.no_room");
            return false;
        }
        float yaw = c.p().getYRot() + 180;
        c.p().teleportTo(c.level(), them.x, them.y, them.z, Set.of(), yaw, c.p().getXRot(), false);
        c.p().resetFallDistance();
        target.teleportTo(me.x, me.y, me.z);
        effect(target, MobEffects.SLOWNESS, ticks(c.def().duration()), 2, c.p());
        effect(target, MobEffects.NAUSEA, ticks(c.def().duration()), 0, c.p());
        if (target instanceof Mob m) m.setTarget(null);
        ServerFx.of("x_swap", me.add(0, 1, 0)).vec(them.add(0, 1, 0)).color(JOKER).send(c.level());
        sound(c, me, SoundEvents.ILLUSIONER_MIRROR_MOVE, 1.0F, 1.2F);
        sound(c, them, SoundEvents.ENDERMAN_TELEPORT, 0.6F, 1.6F);
        return true;
    }

    /** Monsters stop seeing you (until you strike: that hit is doubled). */
    static void hide(ServerPlayer p, int ticks) {
        p.addEffect(new MobEffectInstance(ModEffects.VANISH, ticks, 0), p);
        p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, ticks, 0, false, false), p);
        for (Mob m : p.level().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(48), m -> m.getTarget() == p)) m.setTarget(null);
    }

    /** Leave a decoy behind: monsters go for it while you vanish, then it blows up. */
    private static boolean decoy(Ctx c) {
        Vec3 at = c.p().position();
        int life = ticks(c.def().duration());
        hide(c.p(), life);
        ServerFx.of("x_decoy", at).b(life).color(JOKER).vec(new Vec3(c.p().getYRot(), 0, 0)).send(c.level());
        sound(c, SoundEvents.ILLUSIONER_PREPARE_MIRROR, 1.2F);
        SkillTasks.run(age -> {
            if (age % 10 == 0) {
                for (Mob m : c.level().getEntitiesOfClass(Mob.class, new AABB(at, at).inflate(16), m -> isEnemy(c.p(), m))) {
                    if (m.getTarget() == c.p()) m.setTarget(null);
                    m.getNavigation().moveTo(at.x, at.y, at.z, 1.15);
                }
            }
            if (age >= life) {
                double r = c.def().radius();
                List<LivingEntity> hit = enemies(c.p(), at, r);
                for (LivingEntity e : hit) {
                    hurt(c, e, c.dmg(1), magic(c));
                    pushAway(e, at, 0.8, 0.45);
                }
                ServerFx.of("x_burst", at.add(0, 0.8, 0)).a(r).b(4).color(JOKER).points(hit).send(c.level());
                sound(c, at, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 1.5F, 0.8F);
                sound(c, at, SoundEvents.GENERIC_EXPLODE.value(), 1.0F, 1.3F);
                return true;
            }
            return false;
        });
        return true;
    }

    /** Cards whirl around you for three seconds; each pass deals a random suit's effect. */
    private static boolean cardStorm(Ctx c) {
        double r = c.def().radius();
        int life = ticks(c.def().duration());
        ServerFx.of("x_orbit", c.p().position()).follow(c.p()).a(r * 0.7).b(life).send(c.level());
        sound(c, SoundEvents.BOOK_PAGE_TURN, 0.8F);
        SkillTasks.run(age -> {
            if (!c.p().isAlive()) return true;
            if (age % 10 == 5) {
                for (LivingEntity e : enemies(c.p(), c.p().position(), r)) {
                    int suit = c.p().getRandom().nextInt(4);
                    switch (suit) {
                        case 0 -> hurt(c, e, c.dmg(1.6), melee(c));
                        case 1 -> {
                            hurt(c, e, c.dmg(1), melee(c));
                            com.jo112ss.rpgmod.job.HealthScale.heal(c.p(), c.dmg(0.15));
                        }
                        case 2 -> {
                            hurt(c, e, c.dmg(1), melee(c));
                            keep(c.p(), MobEffects.ABSORPTION, 200, 0, c.p());
                        }
                        default -> {
                            hurt(c, e, c.dmg(0.8), melee(c));
                            effect(e, MobEffects.POISON, 80, 0, c.p());
                        }
                    }
                    ServerFx.of("card_hit", mid(e)).b(suit).send(c.level());
                }
                sound(c, c.p().position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.5F, 1.8F);
            }
            return age >= life;
        });
        return true;
    }

    /** Disappear - and reappear behind the targeted enemy. */
    private static boolean vanish(Ctx c) {
        Vec3 from = c.p().position();
        LivingEntity target = lookTarget(c, c.def().radius(), 12);
        if (target != null) {
            Vec3 back = Vec3.directionFromRotation(0, target.getYRot()).scale(-(target.getBbWidth() / 2 + 0.9));
            Vec3 to = target.position().add(back);
            if (c.level().noCollision(c.p(), c.p().getBoundingBox().move(to.subtract(from)))) {
                Vec3 face = target.position().subtract(to);
                float yaw = (float) (Mth.atan2(face.z, face.x) * Mth.RAD_TO_DEG) - 90F;
                c.p().teleportTo(c.level(), to.x, to.y, to.z, Set.of(), yaw, c.p().getXRot(), false);
                c.p().resetFallDistance();
            }
        }
        hide(c.p(), ticks(c.def().duration()));
        ServerFx.of("x_swap", from.add(0, 1, 0)).vec(c.p().position().add(0, 1, 0)).color(JOKER).send(c.level());
        ServerFx.of("smoke_bomb", from).a(2).send(c.level());
        sound(c, from, SoundEvents.ILLUSIONER_MIRROR_MOVE, 1.0F, 0.7F);
        sound(c, from, SoundEvents.FIRE_EXTINGUISH, 0.6F, 1.4F);
        return true;
    }

    /** Ultimates the wild card can turn into: every ultimate except itself. */
    private static List<String> wildcardPool(boolean loaded) {
        if (loaded) return List.of("meteor", "armageddon", "dead_eye", "judgement", "assassinate", "earthshatter");
        List<String> out = new ArrayList<>();
        for (SkillDef d : JobDefs.SKILLS.values()) if (d.kind() == SkillKind.ULTIMATE && !d.id().equals("wildcard")) out.add(d.id());
        return out;
    }

    /** Draws a random ultimate of any job and plays it with your own strength. */
    private static boolean wildcard(Ctx c) {
        boolean loaded = consumeLoaded(c.p());
        List<String> pool = new ArrayList<>(wildcardPool(loaded));
        java.util.Collections.shuffle(pool, new java.util.Random(c.p().getRandom().nextLong()));
        for (String id : pool.subList(0, Math.min(6, pool.size()))) {
            SkillDef u = JobDefs.skill(id);
            if (u == null) continue;
            double pw = JobManager.powerMultiplier(c.p(), u);
            if (SkillExecutor.execute(c.p(), u, pw)) {
                ServerFx.of("u_wildcard", c.p().position()).follow(c.p()).send(c.level());
                sound(c, SoundEvents.PLAYER_LEVELUP, 1.6F);
                c.p().sendSystemMessage(Component.translatable("rpgmod.joker.wildcard", Component.translatable("rpgmod.skill." + id))
                        .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), true);
                if (com.jo112ss.rpgmod.devtest.AutoTestServer.ENABLED) com.jo112ss.rpgmod.RpgMod.LOGGER.info("[AUTOTEST] wildcard -> {}", id);
                return true;
            }
        }
        return false;
    }

    // ================================================================== nightblade

    /** A dash through the shadows; everything in the way is cut. */
    private static boolean shadowRun(Ctx c) {
        Vec3 from = c.p().position();
        Vec3 dir = c.flat();
        Vec3 to = safeTeleport(c, dir, c.def().radius());
        List<LivingEntity> hit = new ArrayList<>();
        Vec3 a = from.add(0, 1, 0), b = to.add(0, 1, 0);
        for (LivingEntity e : c.level().getEntitiesOfClass(LivingEntity.class, new AABB(a, b).inflate(2), e -> isEnemy(c.p(), e))) {
            if (e.getBoundingBox().inflate(1.2).clip(a, b).isPresent()) hit.add(e);
        }
        c.p().teleportTo(c.level(), to.x, to.y, to.z, Set.of(), c.p().getYRot(), c.p().getXRot(), false);
        c.p().resetFallDistance();
        for (LivingEntity e : hit) {
            hurt(c, e, c.dmg(1), melee(c));
            effect(e, MobEffects.SLOWNESS, 30, 1, c.p());
        }
        ServerFx.of("x_dash", from).vec(to).color(NIGHT).send(c.level());
        if (!hit.isEmpty()) ServerFx.of("hit", from).color(NIGHT).a(1).points(hit).send(c.level());
        sound(c, from, SoundEvents.ILLUSIONER_MIRROR_MOVE, 1.0F, 1.6F);
        sound(c, to, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 1.5F);
        return true;
    }

    /** Six quick cuts in front. */
    private static boolean flurry(Ctx c) {
        SkillTasks.run(age -> {
            if (!c.p().isAlive()) return true;
            if (age % 4 == 0) {
                List<LivingEntity> hit = cone(c, c.def().radius(), 70);
                for (LivingEntity e : hit) hurt(c, e, c.dmg(1), melee(c));
                double tilt = (age / 4 % 2 == 0 ? 1 : -1) * (0.4 + c.p().getRandom().nextDouble() * 0.6);
                ServerFx.of("x_slash", c.chest().add(c.flat().scale(1.2))).vec(c.flat()).a(1.8).b(tilt).color(0x9CB0FF).points(hit).send(c.level());
                sound(c, c.p().position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.6F, 1.4F + age * 0.03F);
            }
            return age >= 20;
        });
        return true;
    }

    /** A kunai pins the targeted enemy's shadow - and those of the enemies around it. */
    private static boolean shadowStitch(Ctx c) {
        LivingEntity target = lookTarget(c, c.def().radius(), 12);
        if (target == null) {
            fail(c.p(), "rpgmod.job.no_target");
            return false;
        }
        Vec3 start = c.hand();
        Vec3 dir = mid(target).subtract(start).normalize();
        ServerFx.of("knife", start).vec(dir.scale(1.8)).a(c.def().radius() + 4).b(-1).send(c.level());
        sound(c, SoundEvents.TRIDENT_THROW.value(), 1.3F);
        int t = ticks(c.def().duration());
        projectile(c, start, dir, 1.8, c.def().radius() + 4, target, 0.4, (e, at, d) -> {
            List<LivingEntity> pinned = new ArrayList<>(List.of(e));
            for (LivingEntity o : enemies(c.p(), e.position(), 4)) if (o != e) pinned.add(o);
            for (LivingEntity o : pinned) {
                hurt(c, o, c.dmg(1), melee(c));
                effect(o, MobEffects.SLOWNESS, t, 9, c.p());
                effect(o, ModEffects.ATTACK_DOWN, t, 0, c.p());
                o.setDeltaMovement(0, Math.min(0, o.getDeltaMovement().y), 0);
                o.needsSync = true;
                ServerFx.of("shadow_bind", o.position()).follow(o).a(o.getBbWidth()).b(t).send(c.level());
            }
            sound(c, at, SoundEvents.CHAIN_BREAK, 1.0F, 0.6F);
        }, (at, d, wall) -> { });
        return true;
    }

    /** A crescent of moonlight flies forward, cutting through every enemy in its path. */
    private static boolean moonShadow(Ctx c) {
        Vec3 dir = c.flat();
        Vec3 start = c.chest();
        double speed = 1.2, range = c.def().radius();
        ServerFx.of("x_wave", start).vec(dir.scale(speed)).a(range).b(3.0).color(0xC8D8FF).send(c.level());
        sound(c, SoundEvents.PLAYER_ATTACK_SWEEP, 0.5F);
        sound(c, c.p().position(), SoundEvents.TRIDENT_RIPTIDE_2.value(), 0.8F, 1.6F);
        Set<Integer> done = new HashSet<>();
        final Vec3[] pos = {start};
        final double[] gone = {0};
        SkillTasks.run(age -> {
            Vec3 to = pos[0].add(dir.scale(speed));
            BlockHitResult block = c.level().clip(new ClipContext(pos[0], to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, c.p()));
            boolean wall = block.getType() != HitResult.Type.MISS;
            pos[0] = wall ? block.getLocation() : to;
            gone[0] += speed;
            for (LivingEntity e : c.level().getEntitiesOfClass(LivingEntity.class, new AABB(pos[0], pos[0]).inflate(2.4, 2, 2.4), e -> isEnemy(c.p(), e))) {
                if (horizontal(e.position(), pos[0]) > 2.0 + e.getBbWidth() / 2 || !done.add(e.getId())) continue;
                hurt(c, e, c.dmg(1), melee(c));
                push(e, dir, 0.4, 0.15);
                ServerFx.of("x_hit", mid(e)).vec(dir).a(1).color(0xC8D8FF).send(c.level());
            }
            return wall || gone[0] >= range;
        });
        return true;
    }

    private static boolean yaksha(Ctx c) {
        effect(c.p(), ModEffects.YAKSHA, ticks(c.def().duration()), 0, c.p());
        apply(c.p(), c.def(), c.p());
        ServerFx.of("u_yaksha", c.p().position()).follow(c.p()).a(c.def().duration()).send(c.level());
        sound(c, SoundEvents.WARDEN_HEARTBEAT, 0.6F);
        sound(c, c.p().position(), SoundEvents.ILLUSIONER_CAST_SPELL, 1.0F, 0.6F);
        return true;
    }

    // ================================================================== spellmaster fusions

    /** Fire + ice: a ball of steam gathers on the aimed spot and explodes, blinding and slowing. */
    private static boolean steamBurst(Ctx c) {
        Vec3 at = aimPoint(c, 30);
        double r = c.def().radius();
        int t = ticks(c.def().duration());
        ServerFx.of("x_zone", at).vec(new Vec3(3, 2, 0)).a(r).b(10).color(0xE8F4FF).send(c.level());
        ServerFx.of("x_orb", at.add(0, 6, 0)).vec(new Vec3(0, -0.6, 0)).a(6).b(1.6).color(0xFFB080).send(c.level());
        sound(c, SoundEvents.FIRE_EXTINGUISH, 0.5F);
        sound(c, at, SoundEvents.BREWING_STAND_BREW, 1.5F, 0.5F);
        SkillTasks.later(10, () -> {
            List<LivingEntity> hit = enemies(c.p(), at, r);
            for (LivingEntity e : hit) {
                double off = 1 - 0.4 * Math.min(1, horizontal(e.position(), at) / r);
                hurt(c, e, c.dmg(off), magic(c));
                effect(e, MobEffects.BLINDNESS, t, 0, c.p());
                effect(e, MobEffects.SLOWNESS, t, 1, c.p());
                pushAway(e, at, 1.0, 0.6);
            }
            ServerFx.of("x_burst", at).a(r).b(5).color(0xFFFFFF).points(hit).send(c.level());
            ServerFx.of("x_burst", at).a(r * 0.6).b(0).color(0xFF8040).send(c.level());
            sound(c, at, SoundEvents.GENERIC_EXPLODE, 2.0F, 0.9F);
            sound(c, at, SoundEvents.FIRE_EXTINGUISH, 2.0F, 0.6F);
        });
        return true;
    }

    /** Fire + thunder: a ray of plasma through everything in a line; each enemy hit arcs to others. */
    private static boolean plasmaRay(Ctx c) {
        Vec3 eye = c.p().getEyePosition();
        Vec3 look = c.look();
        double range = c.def().radius();
        BlockHitResult block = c.level().clip(new ClipContext(eye, eye.add(look.scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, c.p()));
        Vec3 end = block.getType() == HitResult.Type.MISS ? eye.add(look.scale(range)) : block.getLocation();
        List<LivingEntity> hit = new ArrayList<>();
        for (LivingEntity e : c.level().getEntitiesOfClass(LivingEntity.class, new AABB(eye, end).inflate(1.6), e -> isEnemy(c.p(), e))) {
            if (e.getBoundingBox().inflate(1.0).clip(eye, end).isPresent()) hit.add(e);
        }
        Set<Integer> arced = new HashSet<>();
        for (LivingEntity e : hit) {
            hurt(c, e, c.dmg(1), magic(c));
            e.igniteForSeconds((float) c.def().duration());
            arced.add(e.getId());
        }
        for (LivingEntity e : hit) {
            int n = 0;
            for (LivingEntity o : enemies(c.p(), e.position(), 6)) {
                if (n >= 2 || !arced.add(o.getId())) continue;
                hurt(c, o, c.dmg(0.3), magic(c));
                ServerFx.of("x_link", mid(e)).vec(mid(o)).color(0xFF80FF).send(c.level());
                n++;
            }
        }
        ServerFx.of("x_beam", c.hand()).vec(end).a(1.1).color(0xFF60E0).points(hit).send(c.level());
        ServerFx.of("x_beam", c.hand()).vec(end).a(0.4).color(0xFFE0A0).send(c.level());
        ServerFx.of("x_strike", end).a(2).color(0xFF80FF).send(c.level());
        sound(c, SoundEvents.TRIDENT_THUNDER.value(), 1.4F);
        sound(c, c.p().position(), SoundEvents.BLAZE_SHOOT, 1.0F, 0.6F);
        return true;
    }

    /** Ice + thunder: a freezing storm over the aimed area; enemies freeze and lightning keeps falling. */
    private static boolean frostTempest(Ctx c) {
        Vec3 at = aimPoint(c, 28);
        double r = c.def().radius();
        int life = ticks(c.def().duration());
        int pulses = Math.max(1, life / 10);
        ServerFx.of("x_zone", at).vec(new Vec3(3, 2, 0)).a(r).b(life).color(ICE).send(c.level());
        ServerFx.of("x_tornado", at).vec(Vec3.ZERO).a(r * 0.7).b(life).color(ICE).send(c.level());
        sound(c, SoundEvents.POWDER_SNOW_BREAK, 0.5F);
        SkillTasks.run(age -> {
            if (age % 10 == 5) {
                for (LivingEntity e : enemies(c.p(), at, r)) {
                    hurt(c, e, c.dmg(0.8 / pulses), magic(c));
                    effect(e, MobEffects.SLOWNESS, 30, 2, c.p());
                    e.setTicksFrozen(Math.min(e.getTicksRequiredToFreeze() + 60, e.getTicksFrozen() + 40));
                }
            }
            if (age % 20 == 10) {
                List<LivingEntity> in = enemies(c.p(), at, r);
                if (!in.isEmpty()) {
                    LivingEntity t = in.get(c.p().getRandom().nextInt(in.size()));
                    hurt(c, t, c.dmg(0.2 * 20 / Math.max(20, life)), magic(c));
                    ServerFx.of("x_strike", t.position()).a(1.5).color(VOLT).point(mid(t)).send(c.level());
                    sound(c, t.position(), SoundEvents.LIGHTNING_BOLT_IMPACT, 0.8F, 1.5F);
                }
            }
            return age >= life;
        });
        return true;
    }

    // ================================================================== bishop

    /** Revives a player lying downed (Resurrection mod); false if they were not downed. */
    public static boolean revive(ServerPlayer p) {
        if (!com.jo112ss.rpgmod.compat.Downed.is(p)) return false;
        try {
            Class<?> m = Class.forName("io.github.jo112ss.resurrection.DownedManager");
            Object ok = m.getMethod("forceRevive", ServerPlayer.class).invoke(null, p);
            return Boolean.TRUE.equals(ok);
        } catch (ReflectiveOperationException | LinkageError ex) {
            com.jo112ss.rpgmod.RpgMod.LOGGER.warn("Could not revive {} through Resurrection", p.getName().getString(), ex);
            return false;
        }
    }

    /** Experience levels the revive prayer costs (like reviving by hand with the Resurrection mod). */
    public static final int REVIVE_XP_LEVELS = 10;

    /**
     * Raises downed allies around you at once (half health) and heals everyone a little. Costs ten
     * experience levels; with nobody downed in reach it is not cast (nothing spent).
     */
    private static boolean revivePrayer(Ctx c) {
        List<ServerPlayer> allies = allies(c.p(), c.def().radius());
        boolean anyDown = false;
        for (ServerPlayer a : allies) if (com.jo112ss.rpgmod.compat.Downed.is(a)) anyDown = true;
        if (!anyDown) {
            fail(c.p(), "rpgmod.job.no_downed");
            return false;
        }
        boolean free = c.p().hasInfiniteMaterials();
        if (!free && c.p().experienceLevel < REVIVE_XP_LEVELS) {
            c.p().sendSystemMessage(Component.translatable("rpgmod.job.need_xp", REVIVE_XP_LEVELS).withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (!free) c.p().giveExperienceLevels(-REVIVE_XP_LEVELS);
        int raised = 0;
        for (ServerPlayer a : allies) {
            if (revive(a)) {
                raised++;
                a.setHealth(Math.max(a.getHealth(), a.getMaxHealth() * 0.5F));
                ServerFx.of("x_pillar", a.position()).a(1.2).b(30).color(HOLY).send(c.level());
            } else {
                com.jo112ss.rpgmod.job.HealthScale.heal(a, c.dmg(1));
            }
            ServerFx.of("heal_burst", a.position()).follow(a).color(HOLY).send(c.level());
        }
        ServerFx.of("x_ring", c.p().position()).a(c.def().radius()).b(2).color(HOLY).send(c.level());
        sound(c, SoundEvents.BEACON_ACTIVATE, 1.8F);
        sound(c, c.p().position(), SoundEvents.BELL_RESONATE, 1.0F, 1.5F);
        if (raised > 0) sound(c, c.p().position(), SoundEvents.TOTEM_USE, 0.6F, 1.3F);
        return true;
    }

    private static void cleanse(LivingEntity a) {
        for (MobEffectInstance inst : new ArrayList<>(a.getActiveEffects())) {
            if (inst.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) a.removeEffect(inst.getEffect());
        }
        a.clearFire();
        a.setTicksFrozen(0);
    }

    /** Holy ground: allies on it take 30% less damage and are cleansed of harmful effects. */
    private static boolean divineDomain(Ctx c) {
        Vec3 o = c.p().position();
        double r = c.def().radius();
        int life = ticks(c.def().duration());
        ServerFx.of("x_zone", o).vec(new Vec3(1, 3, 0)).a(r).b(life).color(HOLY).send(c.level());
        ServerFx.of("x_pillar", o).a(1.5).b(20).color(HOLY).send(c.level());
        sound(c, SoundEvents.BEACON_ACTIVATE, 1.3F);
        sound(c, o, SoundEvents.BELL_RESONATE, 0.8F, 1.0F);
        SkillTasks.run(age -> {
            if (age % 10 == 0) {
                for (ServerPlayer a : playersIn(c, o, r)) {
                    keep(a, ModEffects.SANCTUM, 30, 0, c.p());
                    if (age % 20 == 0) cleanse(a);
                }
            }
            return age >= life - 1;
        });
        return true;
    }

    /** Everyone near is raised, healed to full, cleansed and protected. */
    private static boolean greatMiracle(Ctx c) {
        List<ServerPlayer> allies = allies(c.p(), c.def().radius());
        for (ServerPlayer a : allies) {
            revive(a);
            a.setHealth(a.getMaxHealth());
            cleanse(a);
            apply(a, c.def(), c.p());
            ServerFx.of("u_miracle_pillar", a.position()).send(c.level());
        }
        ServerFx.of("u_great_miracle", c.p().position()).follow(c.p()).a(c.def().radius()).send(c.level());
        sound(c, SoundEvents.BEACON_ACTIVATE, 1.0F);
        sound(c, c.p().position(), SoundEvents.TOTEM_USE, 0.8F, 1.0F);
        sound(c, c.p().position(), SoundEvents.BELL_RESONATE, 1.2F, 0.8F);
        return true;
    }

    /** Spell: a homing bolt of light. */
    private static boolean holyBolt(Ctx c) {
        double range = c.def().radius();
        LivingEntity target = lookTarget(c, range, 15);
        Vec3 start = c.hand();
        Vec3 dir = target != null ? mid(target).subtract(start).normalize() : c.look();
        ServerFx.of("x_bolt", start).vec(dir.scale(1.8)).a(range).b(0.5).color(HOLY).follow(target).send(c.level());
        sound(c, SoundEvents.AMETHYST_BLOCK_CHIME, 1.8F);
        projectile(c, start, dir, 1.8, range, target, 0.22, (e, at, d) -> {
            hurt(c, e, c.dmg(1), magic(c));
            push(e, d, 0.25, 0.1);
            ServerFx.of("x_hit", at).vec(d).a(1).color(HOLY).send(c.level());
            sound(c, at, SoundEvents.BELL_BLOCK, 0.6F, 1.8F);
        }, (at, d, wall) -> ServerFx.of("x_hit", at).vec(d).a(0.5).color(HOLY).send(c.level()));
        return true;
    }

    /** Spell: a ring of light bursts out - enemies are struck, allies healed. */
    private static boolean holyNova(Ctx c) {
        double r = c.def().radius();
        Vec3 o = c.p().position();
        List<LivingEntity> foes = enemies(c.p(), o, r);
        for (LivingEntity e : foes) {
            hurt(c, e, c.dmg(1), magic(c));
            pushAway(e, o, 0.6, 0.3);
        }
        List<ServerPlayer> allies = allies(c.p(), r);
        for (ServerPlayer a : allies) com.jo112ss.rpgmod.job.HealthScale.heal(a, c.dmg(0.3));
        ServerFx.of("x_ring", o).a(r).b(2).color(HOLY).points(foes).send(c.level());
        ServerFx.of("x_burst", o.add(0, 1, 0)).a(2).b(2).color(HOLY).send(c.level());
        for (ServerPlayer a : others(c.p(), allies)) ServerFx.of("heal_burst", a.position()).follow(a).color(HOLY).send(c.level());
        sound(c, SoundEvents.BELL_BLOCK, 1.4F);
        sound(c, o, SoundEvents.BEACON_POWER_SELECT, 1.0F, 1.6F);
        return true;
    }

    /** Spell: a column of light on the aimed spot that keeps striking for a few seconds. */
    private static boolean lightPillar(Ctx c) {
        Vec3 at = aimPoint(c, 26);
        double r = c.def().radius();
        int pulses = 5;
        ServerFx.of("x_pillar", at).a(r).b(pulses * 10 + 6).color(HOLY).send(c.level());
        sound(c, SoundEvents.BEACON_ACTIVATE, 2.0F);
        SkillTasks.run(age -> {
            if (age % 10 == 5) {
                List<LivingEntity> hit = enemies(c.p(), at, r);
                for (LivingEntity e : hit) hurt(c, e, c.dmg(1), magic(c));
                if (!hit.isEmpty()) ServerFx.of("hit", at).color(HOLY).a(1).points(hit).send(c.level());
                sound(c, at, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.6F);
            }
            return age >= pulses * 10;
        });
        return true;
    }
}
