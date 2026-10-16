package com.jo112ss.rpgmod.job;

import com.jo112ss.rpgmod.job.core.SkillDef;
import com.jo112ss.rpgmod.job.core.SkillEffect;
import com.jo112ss.rpgmod.job.fx.ServerFx;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Runs a skill on the server: hit detection, damage, status effects and movement. The visuals are
 * played by the client from small {@link ServerFx} messages (see {@code client.fx.SkillFx}), timed
 * to match what happens here; skills that unfold over time use {@link SkillTasks}.
 *
 * <p>Each skill returns false if it could not be used (for example no target in reach), in which
 * case no mana or cooldown is spent.
 */
public final class SkillExecutor {
    private SkillExecutor() {}

    public static boolean execute(ServerPlayer p, SkillDef def, double power) {
        ServerLevel level = (ServerLevel) p.level();
        Ctx c = new Ctx(p, level, def, power);
        return switch (def.id()) {
            // fighter
            case "power_slash" -> powerSlash(c);
            case "charge" -> charge(c);
            case "war_cry" -> warCry(c);
            case "whirlwind" -> whirlwind(c);
            case "berserk" -> selfBuff(c, "berserk", SoundEvents.RAVAGER_ROAR, 0.8F);
            case "earthshatter" -> earthshatter(c);
            // ranger
            case "multishot" -> multishot(c);
            case "backstep" -> backstep(c);
            case "hunters_mark" -> huntersMark(c);
            case "snare" -> snare(c);
            case "arrow_rain" -> arrowRain(c);
            case "eagle_eye" -> selfBuff(c, "eagle_eye", SoundEvents.PHANTOM_FLAP, 1.3F);
            // paladin
            case "shield_bash" -> shieldBash(c);
            case "holy_guard" -> partyBuff(c, "holy_guard", SoundEvents.BEACON_POWER_SELECT, 1.2F);
            case "smite" -> smite(c);
            case "lay_on_hands" -> heal(c, "lay_on_hands", true);
            case "divine_aegis" -> partyBuff(c, "divine_aegis", SoundEvents.BEACON_ACTIVATE, 1.3F);
            case "judgement" -> judgement(c);
            // wizard
            case "mana_surge" -> mana(c, "mana_surge");
            case "blink" -> blink(c);
            case "mana_barrier" -> selfBuff(c, "mana_barrier", SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2F);
            case "arcane_focus" -> mana(c, "arcane_focus");
            case "meteor" -> meteor(c);
            case "time_warp" -> timeWarp(c);
            case "magic_bolt" -> magicBolt(c);
            case "flame_burst" -> flameBurst(c);
            case "frost_nova" -> frostNova(c);
            case "thunder" -> thunder(c);
            case "arcane_shield" -> selfBuff(c, "arcane_shield", SoundEvents.ENCHANTMENT_TABLE_USE, 0.8F);
            // healer
            case "heal" -> heal(c, "heal", false);
            case "group_heal" -> heal(c, "group_heal", false);
            case "blessing" -> partyBuff(c, "blessing", SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F);
            case "mana_gift" -> mana(c, "mana_gift");
            case "sanctuary" -> sanctuary(c);
            case "guardian_light" -> partyBuff(c, "guardian_light", SoundEvents.BEACON_ACTIVATE, 1.5F);
            // assassin
            case "shadow_step" -> shadowStep(c);
            case "backstab" -> backstab(c);
            case "smoke_bomb" -> smokeBomb(c);
            case "venom" -> venom(c);
            case "assassinate" -> assassinate(c);
            case "shadow_dance" -> selfBuff(c, "shadow_dance", SoundEvents.ILLUSIONER_MIRROR_MOVE, 0.8F);
            // enchanter
            case "empower" -> partyBuff(c, "empower", SoundEvents.ENCHANTMENT_TABLE_USE, 1.2F);
            case "haste" -> partyBuff(c, "haste", SoundEvents.BREEZE_JUMP, 1.4F);
            case "weaken" -> curse(c, "weaken");
            case "mana_link" -> mana(c, "mana_link");
            case "grand_enchant" -> partyBuff(c, "grand_enchant", SoundEvents.BEACON_ACTIVATE, 1.1F);
            case "hex" -> curse(c, "hex");
            // gambler
            case "lucky_shot" -> luckyShot(c);
            case "coin_toss", "dice_roll", "all_in", "jackpot", "roulette" -> gamble(c);
            default -> generic(c);
        };
    }

    /** Everything a skill needs, so the helpers stay short. */
    private record Ctx(ServerPlayer p, ServerLevel level, SkillDef def, double power) {
        float dmg(double scale) {
            return (float) (def.power() * power * scale);
        }

        Vec3 look() {
            return p.getLookAngle();
        }

        Vec3 flat() {
            Vec3 l = p.getLookAngle().multiply(1, 0, 1);
            return l.lengthSqr() < 1e-6 ? Vec3.directionFromRotation(0, p.getYRot()) : l.normalize();
        }

        Vec3 chest() {
            return p.position().add(0, p.getBbHeight() * 0.6, 0);
        }

        Vec3 hand() {
            return p.getEyePosition().add(0, -0.35, 0).add(p.getLookAngle().scale(0.6));
        }
    }

    // ------------------------------------------------------------------ targeting helpers

    private static boolean isEnemy(ServerPlayer p, Entity e) {
        if (!(e instanceof LivingEntity le) || e == p || !le.isAlive() || e instanceof Player || e instanceof ArmorStand) return false;
        if (e instanceof TamableAnimal t && t.isOwnedBy(p)) return false;
        return !e.isInvulnerable();
    }

    private static List<LivingEntity> enemies(ServerPlayer p, Vec3 c, double r) {
        return p.level().getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r + 1),
                e -> isEnemy(p, e) && horizontal(e.position(), c) <= r + e.getBbWidth() / 2 && Math.abs(e.getY() - c.y) < Math.max(3, r));
    }

    private static double horizontal(Vec3 a, Vec3 b) {
        double dx = a.x - b.x, dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Enemies within {@code range} whose direction from the player is within {@code halfAngle} degrees of {@code dir} (horizontal). */
    private static List<LivingEntity> cone(Ctx c, double range, double halfAngle) {
        Vec3 dir = c.flat();
        double cos = Math.cos(Math.toRadians(halfAngle));
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : enemies(c.p, c.p.position(), range)) {
            Vec3 to = e.position().subtract(c.p.position()).multiply(1, 0, 1);
            double len = to.length();
            if (len < 0.8 || to.scale(1 / len).dot(dir) >= cos) out.add(e); // touching = always hit
        }
        return out;
    }

    private static boolean canSee(ServerLevel level, Vec3 from, Vec3 to, Entity p) {
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)).getType() == HitResult.Type.MISS;
    }

    /** The enemy closest to the crosshair within {@code range} and {@code halfAngle} degrees, in line of sight. */
    private static LivingEntity lookTarget(Ctx c, double range, double halfAngle) {
        Vec3 eye = c.p.getEyePosition();
        Vec3 look = c.look();
        double cos = Math.cos(Math.toRadians(halfAngle));
        LivingEntity best = null;
        double bestScore = -1;
        for (LivingEntity e : c.level.getEntitiesOfClass(LivingEntity.class, c.p.getBoundingBox().inflate(range), e -> isEnemy(c.p, e))) {
            Vec3 centre = e.position().add(0, e.getBbHeight() * 0.5, 0);
            Vec3 to = centre.subtract(eye);
            double dist = to.length();
            if (dist > range + e.getBbWidth()) continue;
            boolean onLine = e.getBoundingBox().inflate(0.3).clip(eye, eye.add(look.scale(range))).isPresent();
            double dot = to.scale(1 / Math.max(dist, 1e-4)).dot(look);
            if (!onLine && dot < cos) continue;
            if (!canSee(c.level, eye, centre, c.p)) continue;
            double score = (onLine ? 2 : dot) - dist * 0.002;
            if (score > bestScore) {
                bestScore = score;
                best = e;
            }
        }
        return best;
    }

    /** Where the player is aiming, dropped onto the ground (for area spells). */
    private static Vec3 aimPoint(Ctx c, double range) {
        Vec3 eye = c.p.getEyePosition();
        Vec3 look = c.look();
        BlockHitResult hit = c.level.clip(new ClipContext(eye, eye.add(look.scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, c.p));
        Vec3 at = hit.getType() == HitResult.Type.MISS ? eye.add(look.scale(range)) : hit.getLocation().subtract(look.scale(0.3));
        BlockHitResult down = c.level.clip(new ClipContext(at.add(0, 0.2, 0), at.add(0, -24, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, c.p));
        return down.getType() == HitResult.Type.MISS ? at : down.getLocation();
    }

    private static List<ServerPlayer> allies(ServerPlayer p, double r) {
        if (r <= 0) return List.of(p);
        return p.level().getEntitiesOfClass(ServerPlayer.class, p.getBoundingBox().inflate(r),
                e -> e.isAlive() && !e.isSpectator() && e.distanceTo(p) <= r);
    }

    private static List<ServerPlayer> others(ServerPlayer p, List<ServerPlayer> all) {
        List<ServerPlayer> out = new ArrayList<>(all);
        out.remove(p);
        return out;
    }

    private static List<Holder<MobEffect>> effects(SkillDef def) {
        List<Holder<MobEffect>> out = new ArrayList<>();
        for (String id : def.effects()) BuiltInRegistries.MOB_EFFECT.get(Identifier.parse(id)).ifPresent(out::add);
        return out;
    }

    private static int ticks(double seconds) {
        return (int) Math.round(seconds * 20);
    }

    private static void apply(LivingEntity e, SkillDef def, ServerPlayer source) {
        for (Holder<MobEffect> h : effects(def)) e.addEffect(new MobEffectInstance(h, ticks(def.duration()), def.amplifier()), source);
    }

    // ------------------------------------------------------------------ damage / movement helpers

    private static DamageSource melee(Ctx c) {
        return c.p.damageSources().playerAttack(c.p);
    }

    private static DamageSource magic(Ctx c) {
        return c.p.damageSources().indirectMagic(c.p, c.p);
    }

    private static void hurt(Ctx c, LivingEntity e, float amount, DamageSource source) {
        if (amount > 0) e.hurtServer(c.level, source, amount);
    }

    /** Adds velocity to an entity (scaled by its knockback resistance). */
    private static void push(LivingEntity e, Vec3 horizontalDir, double strength, double up) {
        double resist = 1 - e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        if (resist <= 0) return;
        Vec3 d = horizontalDir.multiply(1, 0, 1);
        if (d.lengthSqr() > 1e-6) d = d.normalize();
        e.setDeltaMovement(e.getDeltaMovement().add(d.x * strength * resist, up * resist, d.z * strength * resist));
        e.needsSync = true;
    }

    private static void pushAway(LivingEntity e, Vec3 from, double strength, double up) {
        push(e, e.position().subtract(from), strength, up);
    }

    private static void launch(ServerPlayer p, Vec3 v) {
        p.setDeltaMovement(v);
        p.connection.send(new ClientboundSetEntityMotionPacket(p));
        p.resetFallDistance();
    }

    private static void sound(Ctx c, Vec3 at, SoundEvent s, float volume, float pitch) {
        c.level.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, volume, pitch);
    }

    private static void sound(Ctx c, Vec3 at, Holder<SoundEvent> s, float volume, float pitch) {
        c.level.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, volume, pitch);
    }

    private static void sound(Ctx c, SoundEvent s, float pitch) {
        sound(c, c.p.position(), s, 1.0F, pitch);
    }

    private static boolean isUndead(LivingEntity e) {
        return e.is(EntityTypeTags.UNDEAD);
    }

    private static void fail(ServerPlayer p, String key) {
        p.sendSystemMessage(Component.translatable(key).withStyle(ChatFormatting.GRAY), true);
    }

    // ================================================================== fighter

    /** Wide forward cleave. */
    private static boolean powerSlash(Ctx c) {
        List<LivingEntity> hit = cone(c, c.def.radius(), 65);
        for (LivingEntity e : hit) {
            hurt(c, e, c.dmg(1), melee(c));
            pushAway(e, c.p.position(), 0.45, 0.12);
        }
        ServerFx.of("power_slash", c.chest()).vec(c.flat()).a(c.def.radius()).points(hit).send(c.level);
        sound(c, SoundEvents.PLAYER_ATTACK_SWEEP, 0.75F);
        sound(c, c.p.position(), SoundEvents.TRIDENT_THROW, 0.8F, 0.7F);
        if (!hit.isEmpty()) sound(c, SoundEvents.PLAYER_ATTACK_STRONG, 0.8F);
        return true;
    }

    private static final double CHARGE_DAMAGE = 6;

    /** Dash forward; enemies in the way are hit and thrown aside. */
    private static boolean charge(Ctx c) {
        Vec3 dir = c.flat();
        launch(c.p, dir.scale(c.def.power()).add(0, 0.2, 0));
        ServerFx.of("charge", c.p.position()).vec(dir).follow(c.p).send(c.level);
        sound(c, SoundEvents.BREEZE_JUMP, 0.8F);
        sound(c, c.p.position(), SoundEvents.TRIDENT_RIPTIDE_1, 0.8F, 1.2F);
        Set<Integer> done = new HashSet<>();
        SkillTasks.run(age -> {
            if (!c.p.isAlive() || c.p.isRemoved()) return true;
            Vec3 front = c.p.position().add(dir.scale(0.8));
            List<LivingEntity> hit = new ArrayList<>();
            for (LivingEntity e : enemies(c.p, front, 1.7)) {
                if (!done.add(e.getId())) continue;
                hurt(c, e, (float) (CHARGE_DAMAGE * c.power), melee(c));
                push(e, dir, 1.1, 0.35);
                hit.add(e);
            }
            if (!hit.isEmpty()) {
                ServerFx.of("hit", front).color(0xFFAA55).a(1.2).points(hit).send(c.level);
                sound(c, c.p.position(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0F, 0.8F);
            }
            return age >= 9;
        });
        return true;
    }

    private static boolean warCry(Ctx c) {
        List<ServerPlayer> allies = allies(c.p, c.def.radius());
        for (ServerPlayer a : allies) apply(a, c.def, c.p);
        List<LivingEntity> foes = enemies(c.p, c.p.position(), c.def.radius() * 0.75);
        for (LivingEntity e : foes) e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks(5), 0), c.p);
        ServerFx.of("war_cry", c.p.position()).follow(c.p).a(c.def.radius()).points(others(c.p, allies)).send(c.level);
        sound(c, SoundEvents.RAVAGER_ROAR, 1.3F);
        sound(c, c.p.position(), SoundEvents.GOAT_RAM_IMPACT, 0.8F, 0.6F);
        return true;
    }

    /** Two spins around the player, 10 ticks apart (damage immunity frames last 10 ticks). */
    private static boolean whirlwind(Ctx c) {
        double r = c.def.radius();
        ServerFx.of("whirlwind", c.p.position()).follow(c.p).a(r).send(c.level);
        SkillTasks.run(age -> {
            if (!c.p.isAlive()) return true;
            if (age == 0 || age == 10) {
                List<LivingEntity> hit = enemies(c.p, c.p.position(), r);
                for (LivingEntity e : hit) {
                    hurt(c, e, c.dmg(0.65), melee(c));
                    if (age == 10) pushAway(e, c.p.position(), 0.6, 0.2);
                }
                if (!hit.isEmpty()) ServerFx.of("hit", c.p.position()).color(0xFFC080).a(1).points(hit).send(c.level);
                sound(c, SoundEvents.PLAYER_ATTACK_SWEEP, age == 0 ? 0.7F : 0.9F);
                sound(c, c.p.position(), SoundEvents.BREEZE_WIND_CHARGE_BURST, 0.5F, 1.4F);
            }
            return age >= 10;
        });
        return true;
    }

    /** Ground slam: a shockwave spreads out and hits each enemy as it passes. */
    private static boolean earthshatter(Ctx c) {
        double r = c.def.radius();
        Vec3 o = c.p.position();
        ServerFx.of("earthshatter", o).a(r).send(c.level);
        sound(c, o, SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.5F, 0.6F);
        sound(c, o, SoundEvents.GENERIC_EXPLODE, 0.8F, 0.6F);
        Set<Integer> done = new HashSet<>();
        SkillTasks.run(age -> {
            double front = 1.0 + age * (r / 12.0);
            for (LivingEntity e : enemies(c.p, o, Math.min(front, r))) {
                if (!done.add(e.getId())) continue;
                double fall = 1 - 0.4 * Math.min(1, horizontal(e.position(), o) / r);
                hurt(c, e, c.dmg(fall), melee(c));
                apply(e, c.def, c.p);
                pushAway(e, o, 0.4, 0.55);
            }
            return front >= r;
        });
        return true;
    }

    /** Self buff whose look is all in the effect (berserk, eagle eye, barriers, shadow dance). */
    private static boolean selfBuff(Ctx c, String fx, SoundEvent s, float pitch) {
        apply(c.p, c.def, c.p);
        if (fx.equals("shadow_dance")) c.p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, ticks(c.def.duration()), 0), c.p);
        ServerFx.of(fx, c.p.position()).follow(c.p).a(c.def.duration()).send(c.level);
        sound(c, s, pitch);
        if (fx.equals("berserk")) sound(c, c.p.position(), SoundEvents.FIRECHARGE_USE, 0.8F, 0.6F);
        if (fx.equals("arcane_shield") || fx.equals("mana_barrier")) sound(c, c.p.position(), SoundEvents.BEACON_POWER_SELECT, 0.6F, 1.6F);
        return true;
    }

    // ================================================================== ranger

    private static void arrow(Ctx c, float yawOffset, double damage, boolean crit, String trail, int color) {
        Arrow arrow = new Arrow(c.level, c.p, new ItemStack(Items.ARROW), c.p.getMainHandItem());
        arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
        arrow.setBaseDamage(damage);
        arrow.setCritArrow(crit);
        arrow.shootFromRotation(c.p, c.p.getXRot(), c.p.getYRot() + yawOffset, 0, 3.2F, 0.5F);
        c.level.addFreshEntity(arrow);
        ServerFx.of(trail, arrow.position()).follow(arrow).color(color).send(c.level);
    }

    private static boolean multishot(Ctx c) {
        int n = Math.max(1, (int) Math.round(c.def.power()));
        float spread = 9;
        for (int i = 0; i < n; i++) arrow(c, -spread * (n - 1) / 2f + spread * i, 2.0 * c.power, false, "arrow_trail", 0x8CFF6A);
        ServerFx.of("multishot", c.hand()).vec(c.look()).send(c.level);
        sound(c, SoundEvents.ARROW_SHOOT, 1.2F);
        sound(c, c.p.position(), SoundEvents.CROSSBOW_SHOOT, 0.7F, 1.4F);
        return true;
    }

    private static boolean backstep(Ctx c) {
        Vec3 dir = c.flat();
        ServerFx.of("backstep", c.p.position()).vec(dir.scale(-1)).follow(c.p).send(c.level);
        launch(c.p, dir.scale(-c.def.power()).add(0, 0.45, 0));
        c.p.addEffect(new MobEffectInstance(MobEffects.SPEED, ticks(2), 1), c.p);
        sound(c, SoundEvents.BREEZE_JUMP, 1.3F);
        sound(c, c.p.position(), SoundEvents.PHANTOM_FLAP, 0.6F, 1.6F);
        return true;
    }

    private static boolean huntersMark(Ctx c) {
        List<LivingEntity> marked = cone(c, c.def.radius(), 45);
        for (LivingEntity e : marked) {
            apply(e, c.def, c.p);
            ServerFx.of("mark", e.position()).follow(e).a(c.def.duration()).send(c.level);
        }
        ServerFx.of("hunters_mark", c.p.getEyePosition()).vec(c.flat()).a(c.def.radius()).points(marked).send(c.level);
        sound(c, SoundEvents.ILLUSIONER_PREPARE_BLINDNESS, 1.4F);
        if (!marked.isEmpty()) sound(c, SoundEvents.AMETHYST_CLUSTER_BREAK, 1.6F);
        return true;
    }

    /** Throws a snare to the aimed spot; it springs shut and keeps rooting enemies that walk in. */
    private static boolean snare(Ctx c) {
        Vec3 at = aimPoint(c, 14);
        double r = c.def.radius();
        int life = ticks(4);
        ServerFx.of("snare", at).vec(c.hand()).a(r).b(life).send(c.level);
        sound(c, SoundEvents.WITCH_THROW, 0.9F);
        Set<Integer> caught = new HashSet<>();
        SkillTasks.run(age -> {
            if (age == 6) sound(c, at, SoundEvents.CHAIN_BREAK, 1.0F, 0.7F);
            if (age >= 6 && (age - 6) % 5 == 0) {
                List<LivingEntity> in = enemies(c.p, at, r);
                List<LivingEntity> fresh = new ArrayList<>();
                for (LivingEntity e : in) {
                    e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, c.def.amplifier() + 1), c.p);
                    if (caught.add(e.getId())) {
                        hurt(c, e, c.dmg(1), melee(c));
                        e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks(c.def.duration()), c.def.amplifier()), c.p);
                        e.setDeltaMovement(0, Math.min(0, e.getDeltaMovement().y), 0);
                        e.needsSync = true;
                        fresh.add(e);
                    }
                }
                if (!fresh.isEmpty()) {
                    ServerFx.of("snare_catch", at).a(r).points(fresh).send(c.level);
                    sound(c, at, SoundEvents.CHAIN_BREAK, 1.0F, 1.2F);
                }
            }
            return age >= 6 + life;
        });
        return true;
    }

    /** Arrows fall on the aimed area for two seconds. */
    private static boolean arrowRain(Ctx c) {
        Vec3 at = aimPoint(c, 32);
        double r = c.def.radius();
        int life = 40;
        ServerFx.of("arrow_rain", at).a(r).b(life).send(c.level);
        sound(c, SoundEvents.ARROW_SHOOT, 0.6F);
        sound(c, c.p.position(), SoundEvents.CROSSBOW_SHOOT, 1.0F, 0.6F);
        SkillTasks.run(age -> {
            if (age % 3 == 0) sound(c, at, SoundEvents.ARROW_HIT, 0.6F, 0.8F + c.p.getRandom().nextFloat() * 0.5F);
            if (age >= 8 && (age - 8) % 10 == 0) {
                for (LivingEntity e : enemies(c.p, at, r)) hurt(c, e, c.dmg(0.35), melee(c));
            }
            return age >= life;
        });
        return true;
    }

    // ================================================================== paladin

    private static boolean shieldBash(Ctx c) {
        List<LivingEntity> hit = cone(c, c.def.radius(), 55);
        for (LivingEntity e : hit) {
            hurt(c, e, c.dmg(1), melee(c));
            apply(e, c.def, c.p);
            push(e, c.flat(), 1.1, 0.3);
        }
        ServerFx.of("shield_bash", c.chest()).vec(c.flat()).a(c.def.radius()).points(hit).send(c.level);
        sound(c, c.p.position(), SoundEvents.SHIELD_BLOCK, 1.0F, 0.7F);
        sound(c, SoundEvents.MACE_SMASH_GROUND, 1.1F);
        return true;
    }

    /** Buff for the party around the caster (the effect shows on each of them). */
    private static boolean partyBuff(Ctx c, String fx, SoundEvent s, float pitch) {
        List<ServerPlayer> allies = allies(c.p, c.def.radius());
        for (ServerPlayer a : allies) apply(a, c.def, c.p);
        ServerFx.of(fx, c.p.position()).follow(c.p).a(Math.max(1, c.def.radius())).b(c.def.duration()).points(others(c.p, allies)).send(c.level);
        sound(c, s, pitch);
        if (fx.equals("guardian_light") || fx.equals("divine_aegis")) sound(c, c.p.position(), SoundEvents.TOTEM_USE, 0.5F, 1.4F);
        if (fx.equals("grand_enchant")) sound(c, c.p.position(), SoundEvents.ENCHANTMENT_TABLE_USE, 1.0F, 0.7F);
        return true;
    }

    /** A pillar of light falls on the targeted enemy, splashing around it. */
    private static boolean smite(Ctx c) {
        LivingEntity target = lookTarget(c, c.def.radius(), 14);
        Vec3 start = target != null ? target.position() : aimPoint(c, c.def.radius());
        ServerFx.of("smite", start).follow(target).a(2.5).send(c.level);
        sound(c, SoundEvents.BEACON_POWER_SELECT, 1.8F);
        SkillTasks.later(7, () -> {
            Vec3 at = target != null && target.isAlive() ? target.position() : start;
            List<LivingEntity> hit = enemies(c.p, at, 2.5);
            for (LivingEntity e : hit) {
                float amount = c.dmg(e == target || target == null ? 1 : 0.5) * (isUndead(e) ? 1.5F : 1);
                hurt(c, e, amount, magic(c));
                pushAway(e, at, 0.3, 0.25);
            }
            ServerFx.of("smite_hit", at).a(2.5).points(hit).send(c.level);
            sound(c, at, SoundEvents.BELL_BLOCK, 1.2F, 1.4F);
            sound(c, at, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.6F, 1.6F);
        });
        return true;
    }

    /** Holy circle forms, then light comes down on everything in it. */
    private static boolean judgement(Ctx c) {
        double r = c.def.radius();
        Vec3 o = c.p.position();
        ServerFx.of("judgement", o).a(r).send(c.level);
        sound(c, SoundEvents.BEACON_ACTIVATE, 0.8F);
        sound(c, o, SoundEvents.BELL_RESONATE, 1.0F, 1.2F);
        SkillTasks.later(16, () -> {
            List<LivingEntity> hit = enemies(c.p, o, r);
            for (LivingEntity e : hit) {
                hurt(c, e, c.dmg(1) * (isUndead(e) ? 1.5F : 1), magic(c));
                pushAway(e, o, 0.3, 0.45);
            }
            ServerFx.of("judgement_strike", o).a(r).points(hit).send(c.level);
            sound(c, o, SoundEvents.LIGHTNING_BOLT_IMPACT, 1.2F, 1.2F);
            sound(c, o, SoundEvents.BELL_BLOCK, 1.5F, 0.8F);
        });
        return true;
    }

    // ================================================================== healing / mana

    private static boolean heal(Ctx c, String fx, boolean cleanse) {
        List<ServerPlayer> allies = allies(c.p, c.def.radius());
        for (ServerPlayer a : allies) {
            a.heal(c.dmg(1));
            if (cleanse) {
                a.removeEffect(MobEffects.POISON);
                a.removeEffect(MobEffects.WITHER);
            }
        }
        ServerFx.of(fx, c.p.position()).follow(c.p).a(c.def.radius()).points(others(c.p, allies)).send(c.level);
        sound(c, SoundEvents.AMETHYST_BLOCK_CHIME, 1.5F);
        sound(c, c.p.position(), SoundEvents.NOTE_BLOCK_CHIME, 0.6F, 1.6F);
        return true;
    }

    private static boolean mana(Ctx c, String fx) {
        List<ServerPlayer> allies = allies(c.p, c.def.radius());
        for (ServerPlayer a : allies) JobManager.restoreMana(a, c.def.power() * c.power);
        ServerFx.of(fx, c.p.position()).follow(c.p).a(Math.max(1, c.def.radius())).points(others(c.p, allies)).send(c.level);
        sound(c, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8F);
        sound(c, c.p.position(), SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5F, 0.6F);
        return true;
    }

    /** A holy zone that heals allies inside every second and slows enemies. */
    private static boolean sanctuary(Ctx c) {
        double r = c.def.radius();
        Vec3 o = c.p.position();
        int pulses = 8;
        ServerFx.of("sanctuary", o).a(r).b(pulses * 20).send(c.level);
        sound(c, SoundEvents.BEACON_ACTIVATE, 1.4F);
        SkillTasks.run(age -> {
            if (age % 20 == 0) {
                for (ServerPlayer a : c.level.getEntitiesOfClass(ServerPlayer.class, new AABB(o, o).inflate(r, 3, r),
                        a -> a.isAlive() && horizontal(a.position(), o) <= r)) {
                    a.heal(c.dmg(1.0 / pulses));
                }
                for (LivingEntity e : enemies(c.p, o, r)) e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 0), c.p);
                sound(c, o, SoundEvents.AMETHYST_BLOCK_CHIME, 0.7F, 1.2F);
            }
            return age >= pulses * 20 - 1;
        });
        return true;
    }

    // ================================================================== wizard

    private static boolean blink(Ctx c) {
        Vec3 eye = c.p.getEyePosition();
        Vec3 look = c.look();
        BlockHitResult hit = c.level.clip(new ClipContext(eye, eye.add(look.scale(c.def.radius())), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, c.p));
        Vec3 target = hit.getType() == HitResult.Type.MISS ? eye.add(look.scale(c.def.radius())) : hit.getLocation().subtract(look.scale(0.6));
        Vec3 from = c.p.position();
        Vec3 to = new Vec3(target.x, Math.max(target.y - c.p.getEyeHeight(), c.level.getMinY()), target.z);
        c.p.teleportTo(to.x, to.y, to.z);
        c.p.resetFallDistance();
        ServerFx.of("blink", from).vec(to).send(c.level);
        sound(c, from, SoundEvents.ENDERMAN_TELEPORT, 0.8F, 1.3F);
        sound(c, to, SoundEvents.AMETHYST_CLUSTER_BREAK, 1.0F, 1.5F);
        return true;
    }

    /** A meteor falls on the aimed spot. */
    private static boolean meteor(Ctx c) {
        Vec3 at = aimPoint(c, 36);
        double r = c.def.radius();
        int fall = 28;
        Vec3 sky = c.flat().scale(-10).add(0, 26, 0); // comes in at an angle from behind the caster
        ServerFx.of("meteor", at).vec(sky).a(r).b(fall).send(c.level);
        sound(c, SoundEvents.BLAZE_SHOOT, 0.6F);
        sound(c, at.add(sky.scale(0.5)), SoundEvents.FIREWORK_ROCKET_LAUNCH, 2.0F, 0.4F);
        SkillTasks.later(fall, () -> {
            List<LivingEntity> hit = enemies(c.p, at, r);
            for (LivingEntity e : hit) {
                double fallOff = 1 - 0.5 * Math.min(1, horizontal(e.position(), at) / r);
                hurt(c, e, c.dmg(fallOff), magic(c));
                e.igniteForSeconds((float) c.def.duration());
                pushAway(e, at, 0.8, 0.55);
            }
            ServerFx.of("meteor_impact", at).a(r).points(hit).send(c.level);
            sound(c, at, SoundEvents.GENERIC_EXPLODE, 2.5F, 0.7F);
            sound(c, at, SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.5F, 0.5F);
        });
        return true;
    }

    private static boolean timeWarp(Ctx c) {
        double r = c.def.radius();
        Vec3 o = c.p.position();
        List<LivingEntity> hit = enemies(c.p, o, r);
        for (LivingEntity e : hit) apply(e, c.def, c.p);
        ServerFx.of("time_warp", o).a(r).b(ticks(c.def.duration())).points(hit).send(c.level);
        sound(c, SoundEvents.BELL_RESONATE, 0.5F);
        sound(c, o, SoundEvents.ILLUSIONER_PREPARE_BLINDNESS, 1.0F, 0.5F);
        return true;
    }

    private static final double BOLT_SPEED = 1.8;

    /** A homing bolt that travels to the target (or the first thing in the way). */
    private static boolean magicBolt(Ctx c) {
        double range = c.def.radius();
        LivingEntity target = lookTarget(c, range, 15);
        Vec3 start = c.hand();
        Vec3 dir0 = target != null ? target.position().add(0, target.getBbHeight() * 0.55, 0).subtract(start).normalize() : c.look();
        ServerFx.of("magic_bolt", start).vec(dir0.scale(BOLT_SPEED)).a(range).follow(target).send(c.level);
        sound(c, SoundEvents.ILLUSIONER_CAST_SPELL, 1.7F);
        sound(c, c.p.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.6F, 2.0F);
        final Vec3[] pos = {start};
        final Vec3[] dir = {dir0};
        final double[] travelled = {0};
        SkillTasks.run(age -> {
            if (target != null && target.isAlive()) {
                Vec3 want = target.position().add(0, target.getBbHeight() * 0.55, 0).subtract(pos[0]).normalize();
                dir[0] = turn(dir[0], want, 0.22);
            }
            for (int s = 0; s < 3; s++) {
                Vec3 from = pos[0];
                Vec3 to = from.add(dir[0].scale(BOLT_SPEED / 3));
                BlockHitResult block = c.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, c.p));
                Vec3 end = block.getType() == HitResult.Type.MISS ? to : block.getLocation();
                for (LivingEntity e : c.level.getEntitiesOfClass(LivingEntity.class, new AABB(from, end).inflate(0.6), e -> isEnemy(c.p, e))) {
                    if (e.getBoundingBox().inflate(0.3).clip(from, end).isPresent() || e.getBoundingBox().inflate(0.3).contains(end)) {
                        hurt(c, e, c.dmg(1), magic(c));
                        push(e, dir[0], 0.25, 0.1);
                        ServerFx.of("magic_bolt_hit", end).vec(dir[0]).b(1).send(c.level);
                        sound(c, end, SoundEvents.AMETHYST_CLUSTER_BREAK, 1.0F, 1.3F);
                        return true;
                    }
                }
                if (block.getType() != HitResult.Type.MISS) {
                    ServerFx.of("magic_bolt_hit", end).vec(dir[0]).send(c.level);
                    sound(c, end, SoundEvents.AMETHYST_BLOCK_BREAK, 0.8F, 1.5F);
                    return true;
                }
                pos[0] = end;
                travelled[0] += BOLT_SPEED / 3;
                if (travelled[0] >= range) {
                    ServerFx.of("magic_bolt_hit", end).vec(dir[0]).b(-1).send(c.level);
                    return true;
                }
            }
            return false;
        });
        return true;
    }

    /** Rotates {@code from} towards {@code to} by at most {@code maxAngle} radians. */
    public static Vec3 turn(Vec3 from, Vec3 to, double maxAngle) {
        double dot = Mth.clamp(from.dot(to), -1, 1);
        double angle = Math.acos(dot);
        if (angle <= maxAngle || angle < 1e-4) return to;
        double t = maxAngle / angle;
        return from.scale(1 - t).add(to.scale(t)).normalize();
    }

    /** A cone of fire; enemies burn as the flames reach them. */
    private static boolean flameBurst(Ctx c) {
        double range = c.def.radius();
        double half = 35;
        Vec3 origin = c.p.position();
        List<LivingEntity> hit = cone(c, range, half);
        ServerFx.of("flame_burst", c.hand()).vec(c.look()).a(range).b(half).send(c.level);
        sound(c, SoundEvents.BLAZE_SHOOT, 0.7F);
        sound(c, origin, SoundEvents.FIRECHARGE_USE, 1.0F, 0.8F);
        for (LivingEntity e : hit) {
            int delay = 2 + (int) (horizontal(e.position(), origin) / 1.6);
            SkillTasks.later(delay, () -> {
                if (!e.isAlive()) return;
                hurt(c, e, c.dmg(1), magic(c));
                e.igniteForSeconds((float) c.def.duration());
                push(e, c.flat(), 0.25, 0.1);
            });
        }
        return true;
    }

    /** A ring of ice bursts out from the caster, freezing what it passes. */
    private static boolean frostNova(Ctx c) {
        double r = c.def.radius();
        Vec3 o = c.p.position();
        ServerFx.of("frost_nova", o).a(r).send(c.level);
        sound(c, SoundEvents.PLAYER_HURT_FREEZE, 0.7F);
        sound(c, o, SoundEvents.GLASS_BREAK, 1.0F, 0.6F);
        sound(c, o, SoundEvents.POWDER_SNOW_BREAK, 1.2F, 0.6F);
        for (LivingEntity e : enemies(c.p, o, r)) {
            int delay = (int) (horizontal(e.position(), o) / 0.7);
            SkillTasks.later(Math.max(1, delay), () -> {
                if (!e.isAlive()) return;
                hurt(c, e, c.dmg(1), magic(c));
                apply(e, c.def, c.p);
                e.setTicksFrozen(Math.max(e.getTicksFrozen(), e.getTicksRequiredToFreeze() + 60));
                ServerFx.of("frozen", e.position()).follow(e).a(e.getBbHeight()).b(e.getBbWidth()).send(c.level);
            });
        }
        return true;
    }

    /** Marks the aimed spot, then lightning strikes it and arcs to nearby enemies. */
    private static boolean thunder(Ctx c) {
        double range = c.def.radius();
        LivingEntity target = lookTarget(c, range, 6);
        Vec3 at0 = target != null ? target.position() : aimPoint(c, range);
        int warn = 10;
        ServerFx.of("thunder", at0).follow(target).b(warn).send(c.level);
        sound(c, SoundEvents.EVOKER_PREPARE_ATTACK, 1.6F);
        SkillTasks.later(warn, () -> {
            Vec3 at = target != null && target.isAlive() ? target.position() : at0;
            List<LivingEntity> main = enemies(c.p, at, 3);
            for (LivingEntity e : main) hurt(c, e, c.dmg(1), magic(c));
            List<LivingEntity> chain = new ArrayList<>();
            for (LivingEntity e : enemies(c.p, at, 7)) {
                if (main.contains(e) || chain.size() >= 3) continue;
                if (!canSee(c.level, at.add(0, 1, 0), e.position().add(0, e.getBbHeight() * 0.5, 0), c.p)) continue;
                hurt(c, e, c.dmg(0.5), magic(c));
                chain.add(e);
            }
            LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(c.level, EntitySpawnReason.TRIGGERED);
            if (bolt != null) {
                bolt.snapTo(at.x, at.y, at.z);
                bolt.setVisualOnly(true);
                c.level.addFreshEntity(bolt);
            }
            ServerFx.of("thunder_strike", at).a(3).points(chain).send(c.level);
        });
        return true;
    }

    // ================================================================== assassin

    /** Teleports behind the targeted enemy, or forward if there is none. */
    private static boolean shadowStep(Ctx c) {
        Vec3 from = c.p.position();
        LivingEntity target = lookTarget(c, c.def.radius(), 12);
        Vec3 to;
        float yaw = c.p.getYRot();
        if (target != null) {
            Vec3 back = Vec3.directionFromRotation(0, target.getYRot()).scale(-(target.getBbWidth() / 2 + 0.9));
            to = target.position().add(back);
            if (!c.level.noCollision(c.p, c.p.getBoundingBox().move(to.subtract(from)))) to = target.position().add(back.scale(-1));
            Vec3 face = target.position().subtract(to);
            yaw = (float) (Mth.atan2(face.z, face.x) * Mth.RAD_TO_DEG) - 90F;
        } else {
            Vec3 eye = c.p.getEyePosition();
            Vec3 dir = c.flat();
            BlockHitResult hit = c.level.clip(new ClipContext(eye, eye.add(dir.scale(7)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, c.p));
            Vec3 t = hit.getType() == HitResult.Type.MISS ? eye.add(dir.scale(7)) : hit.getLocation().subtract(dir.scale(0.6));
            to = new Vec3(t.x, t.y - c.p.getEyeHeight(), t.z);
        }
        c.p.teleportTo(c.level, to.x, to.y, to.z, Set.of(), yaw, c.p.getXRot(), false);
        c.p.resetFallDistance();
        c.p.addEffect(new MobEffectInstance(MobEffects.SPEED, ticks(2), 1), c.p);
        ServerFx.of("shadow_step", from).vec(to).send(c.level);
        sound(c, from, SoundEvents.ILLUSIONER_MIRROR_MOVE, 1.0F, 1.4F);
        sound(c, to, SoundEvents.ENDERMAN_TELEPORT, 0.5F, 1.8F);
        return true;
    }

    private static boolean isBehind(LivingEntity target, Vec3 attacker) {
        Vec3 facing = Vec3.directionFromRotation(0, target.getYRot());
        Vec3 toAttacker = attacker.subtract(target.position()).multiply(1, 0, 1).normalize();
        return facing.dot(toAttacker) < -0.3;
    }

    /** A precise strike on one enemy; much stronger from behind. */
    private static boolean backstab(Ctx c) {
        LivingEntity target = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity e : cone(c, c.def.radius(), 50)) {
            double d = e.distanceToSqr(c.p);
            if (d < best) {
                best = d;
                target = e;
            }
        }
        if (target == null) {
            fail(c.p, "rpgmod.job.no_target");
            return false;
        }
        boolean behind = isBehind(target, c.p.position());
        hurt(c, target, c.dmg(behind ? 1.6 : 1), melee(c));
        ServerFx.of("backstab", target.position().add(0, target.getBbHeight() * 0.55, 0)).vec(c.flat()).b(behind ? 1 : 0).send(c.level);
        sound(c, SoundEvents.PLAYER_ATTACK_CRIT, 1.3F);
        if (behind) sound(c, target.position(), SoundEvents.PLAYER_ATTACK_STRONG, 1.0F, 0.6F);
        return true;
    }

    private static boolean smokeBomb(Ctx c) {
        double r = 5;
        c.p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, ticks(c.def.duration()), 0), c.p);
        apply(c.p, c.def, c.p);
        for (LivingEntity e : enemies(c.p, c.p.position(), r)) {
            e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks(3), 0), c.p);
            e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, ticks(3), 0), c.p);
            if (e instanceof Mob m && m.getTarget() == c.p) m.setTarget(null);
        }
        ServerFx.of("smoke_bomb", c.p.position()).a(r).send(c.level);
        sound(c, SoundEvents.SPLASH_POTION_BREAK, 0.6F);
        sound(c, c.p.position(), SoundEvents.FIRE_EXTINGUISH, 1.0F, 0.6F);
        return true;
    }

    private static boolean venom(Ctx c) {
        List<LivingEntity> hit = cone(c, c.def.radius(), 50);
        for (LivingEntity e : hit) {
            hurt(c, e, c.dmg(1), melee(c));
            apply(e, c.def, c.p);
            ServerFx.of("venom_drip", e.position()).follow(e).a(c.def.duration()).send(c.level);
        }
        ServerFx.of("venom", c.hand()).vec(c.flat()).a(c.def.radius()).points(hit).send(c.level);
        sound(c, SoundEvents.PLAYER_ATTACK_SWEEP, 1.5F);
        sound(c, c.p.position(), SoundEvents.SLIME_SQUISH, 1.0F, 0.7F);
        return true;
    }

    /** Appears behind the targeted enemy with a flurry; executes enemies on low health. */
    private static boolean assassinate(Ctx c) {
        LivingEntity target = lookTarget(c, c.def.radius(), 10);
        if (target == null) {
            fail(c.p, "rpgmod.job.no_target");
            return false;
        }
        Vec3 from = c.p.position();
        Vec3 back = Vec3.directionFromRotation(0, target.getYRot()).scale(-(target.getBbWidth() / 2 + 0.8));
        Vec3 to = target.position().add(back);
        if (!c.level.noCollision(c.p, c.p.getBoundingBox().move(to.subtract(from)))) to = target.position().add(back.scale(-1));
        Vec3 face = target.position().subtract(to);
        float yaw = (float) (Mth.atan2(face.z, face.x) * Mth.RAD_TO_DEG) - 90F;
        c.p.teleportTo(c.level, to.x, to.y, to.z, Set.of(), yaw, 10, false);
        c.p.resetFallDistance();
        boolean execute = target.getHealth() / target.getMaxHealth() < 0.3;
        ServerFx.of("assassinate", from).vec(to).follow(target).b(execute ? 1 : 0).send(c.level);
        sound(c, from, SoundEvents.ILLUSIONER_MIRROR_MOVE, 1.0F, 0.6F);
        SkillTasks.later(6, () -> {
            if (!target.isAlive()) return;
            hurt(c, target, c.dmg(execute ? 2 : 1), melee(c));
            ServerFx.of("assassinate_hit", target.position().add(0, target.getBbHeight() * 0.55, 0)).b(execute ? 1 : 0).send(c.level);
            sound(c, target.position(), SoundEvents.PLAYER_ATTACK_CRIT, 1.2F, 0.5F);
            sound(c, target.position(), SoundEvents.WITHER_SHOOT, 0.5F, 1.6F);
        });
        return true;
    }

    // ================================================================== enchanter

    private static boolean curse(Ctx c, String fx) {
        double r = c.def.radius();
        List<LivingEntity> hit = enemies(c.p, c.p.position(), r);
        for (LivingEntity e : hit) {
            if (c.def.power() > 0) hurt(c, e, c.dmg(1), magic(c));
            apply(e, c.def, c.p);
        }
        ServerFx.of(fx, c.p.position()).a(r).b(c.def.duration()).points(hit).send(c.level);
        for (LivingEntity e : hit.subList(0, Math.min(8, hit.size()))) {
            ServerFx.of("curse_aura", e.position()).follow(e).a(c.def.duration()).color(fx.equals("hex") ? 0xB040FF : 0xFF6FB0).send(c.level);
        }
        sound(c, SoundEvents.EVOKER_CAST_SPELL, fx.equals("hex") ? 0.6F : 1.1F);
        if (fx.equals("hex")) sound(c, c.p.position(), SoundEvents.ELDER_GUARDIAN_CURSE, 0.4F, 1.4F);
        return true;
    }

    // ================================================================== gambler

    private static boolean luckyShot(Ctx c) {
        boolean lucky = c.p.getRandom().nextDouble() < 0.35;
        arrow(c, 0, 2.0 * c.power * (lucky ? 2.5 : 1), lucky, "arrow_trail", lucky ? 0xFFD54A : 0x5FE8C8);
        ServerFx.of("lucky_shot", c.hand()).vec(c.look()).b(lucky ? 1 : 0).send(c.level);
        sound(c, SoundEvents.ARROW_SHOOT, 1.1F);
        if (lucky) {
            sound(c, c.p.position(), SoundEvents.NOTE_BLOCK_PLING, 1.0F, 1.6F);
            c.p.sendSystemMessage(Component.translatable("rpgmod.gamble.lucky").withStyle(ChatFormatting.GOLD), true);
        }
        return true;
    }

    /** How long each gamble's roll animation runs before the result lands. */
    private static int rollTicks(String id) {
        return switch (id) {
            case "coin_toss" -> 16;
            case "dice_roll" -> 18;
            case "all_in" -> 14;
            case "jackpot" -> 26;
            case "roulette" -> 34;
            default -> 12;
        };
    }

    static final String[] OUTCOMES = {"jackpot", "heal", "buff", "curse", "mana", "bust"};
    static final int[] OUTCOME_COLORS = {0xFFD54A, 0x7CFF9A, 0xFF9A3C, 0xB86BFF, 0x6FB8FF, 0x8A8A8A};

    /** Gambler skills: a random outcome scaled by the bet (the skill's power). */
    private static boolean gamble(Ctx c) {
        int roll = c.p.getRandom().nextInt(6);
        int wait = rollTicks(c.def.id());
        ServerFx.of(c.def.id(), c.p.position()).follow(c.p).a(roll).b(wait).send(c.level);
        sound(c, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5F);
        SkillTasks.later(wait, () -> {
            if (!c.p.isAlive()) return;
            gambleOutcome(c, roll);
        });
        return true;
    }

    private static void gambleOutcome(Ctx c, int roll) {
        SkillDef def = c.def;
        double bet = def.power();
        double r = def.radius();
        Vec3 o = c.p.position();
        List<LivingEntity> foes = List.of();
        List<ServerPlayer> allies = List.of();
        switch (roll) {
            case 0 -> { // jackpot: a burst of damage all around
                foes = enemies(c.p, o, r);
                for (LivingEntity e : foes) {
                    hurt(c, e, (float) (8 * bet * c.power), magic(c));
                    pushAway(e, o, 0.6, 0.4);
                }
                sound(c, o, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 1.5F, 1.0F);
                sound(c, o, SoundEvents.PLAYER_LEVELUP, 1.0F, 1.2F);
            }
            case 1 -> { // heal the party
                allies = allies(c.p, r);
                for (ServerPlayer a : allies) a.heal((float) (4 * bet * c.power));
                sound(c, o, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.5F);
            }
            case 2 -> { // buff the party
                allies = allies(c.p, r);
                for (ServerPlayer a : allies) {
                    a.addEffect(new MobEffectInstance(MobEffects.STRENGTH, ticks(def.duration()), 0), c.p);
                    a.addEffect(new MobEffectInstance(MobEffects.SPEED, ticks(def.duration()), 0), c.p);
                }
                sound(c, o, SoundEvents.BEACON_POWER_SELECT, 1.0F, 1.4F);
            }
            case 3 -> { // curse enemies
                foes = enemies(c.p, o, r);
                for (LivingEntity e : foes) {
                    hurt(c, e, (float) (2 * bet * c.power), magic(c));
                    e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks(def.duration()), 0), c.p);
                    e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks(def.duration()), 0), c.p);
                }
                sound(c, o, SoundEvents.EVOKER_CAST_SPELL, 1.0F, 0.9F);
            }
            case 4 -> { // mana
                JobManager.restoreMana(c.p, 15 * bet * c.power);
                sound(c, o, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 1.2F);
            }
            default -> { // bust: hurts yourself a little
                c.p.hurtServer(c.level, c.p.damageSources().magic(), (float) (1.5 * bet));
                sound(c, o, SoundEvents.ITEM_BREAK, 1.0F, 0.7F);
            }
        }
        ServerFx fx = ServerFx.of("gamble_" + OUTCOMES[roll], c.p.position()).follow(c.p).a(r).color(OUTCOME_COLORS[roll]);
        if (!foes.isEmpty()) fx.points(foes);
        else if (!allies.isEmpty()) fx.points(others(c.p, allies));
        fx.send(c.level);
        c.p.sendSystemMessage(Component.translatable("rpgmod.gamble." + OUTCOMES[roll]).withStyle(roll == 5 ? ChatFormatting.GRAY : ChatFormatting.AQUA), true);
    }

    // ================================================================== fallback for skills without their own effect

    private static boolean generic(Ctx c) {
        SkillDef def = c.def;
        switch (def.effect()) {
            case SWEEP, WHIRL -> {
                List<LivingEntity> hit = def.effect() == SkillEffect.SWEEP ? cone(c, def.radius(), 65) : enemies(c.p, c.p.position(), def.radius());
                for (LivingEntity e : hit) {
                    hurt(c, e, c.dmg(1), melee(c));
                    apply(e, def, c.p);
                }
                ServerFx.of("hit", c.chest()).a(1).points(hit).send(c.level);
                sound(c, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F);
            }
            case HEAL -> heal(c, "heal", false);
            case BUFF -> partyBuff(c, "empower", SoundEvents.BEACON_POWER_SELECT, 1.4F);
            case DEBUFF -> curse(c, "weaken");
            case MANA -> mana(c, "mana_surge");
            default -> {
                return false;
            }
        }
        return true;
    }
}
