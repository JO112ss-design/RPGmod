package com.jo112ss.rpgmod.job;

import com.jo112ss.rpgmod.job.core.SkillDef;
import com.jo112ss.rpgmod.job.core.SkillEffect;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
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
 * Runs a skill's effect on the server. These are generic placeholder behaviours meant to be replaced
 * or extended when the real skills are designed; each returns false if it could not be used (for
 * example a bolt with no target), in which case no mana or cooldown is spent.
 */
public final class SkillExecutor {
    private SkillExecutor() {}

    public static boolean execute(ServerPlayer p, SkillDef def, double power) {
        ServerLevel level = (ServerLevel) p.level();
        return switch (def.effect()) {
            case SWEEP -> damageArea(p, level, def, power, false);
            case WHIRL -> damageArea(p, level, def, power, true);
            case DASH -> push(p, p.getLookAngle().multiply(1, 0, 1).normalize().scale(def.power()).add(0, 0.25, 0), SoundEvents.BREEZE_JUMP);
            case BACKSTEP -> push(p, p.getLookAngle().multiply(1, 0, 1).normalize().scale(-def.power()).add(0, 0.35, 0), SoundEvents.BREEZE_JUMP);
            case BLINK -> blink(p, level, def.radius());
            case SHOT -> shoot(p, level, (int) Math.round(def.power()), power);
            case HEAL -> heal(p, level, def, power);
            case BUFF -> buff(p, level, def);
            case DEBUFF -> debuff(p, level, def, power);
            case MANA -> mana(p, level, def, power);
            case STEALTH -> stealth(p, level, def);
            case GAMBLE -> gamble(p, level, def, power);
            case BOLT -> bolt(p, level, def, power);
            case LIGHTNING -> lightning(p, level, def, power);
        };
    }

    // ------------------------------------------------------------------ helpers

    private static void sound(ServerPlayer p, SoundEvent s, float pitch) {
        p.level().playSound(null, p.blockPosition(), s, SoundSource.PLAYERS, 0.9F, pitch);
    }

    private static void ring(ServerLevel level, Vec3 c, double r, ParticleOptions particle, int count) {
        for (int i = 0; i < count; i++) {
            double a = Math.PI * 2 * i / count;
            level.sendParticles(particle, c.x + Math.cos(a) * r, c.y + 0.2, c.z + Math.sin(a) * r, 1, 0, 0.05, 0, 0.01);
        }
    }

    private static List<LivingEntity> enemies(ServerPlayer p, Vec3 c, double r) {
        return p.level().getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r),
                e -> e != p && e.isAlive() && !(e instanceof Player) && e.position().distanceTo(c) <= r + e.getBbWidth() / 2);
    }

    private static List<ServerPlayer> allies(ServerPlayer p, double r) {
        if (r <= 0) return List.of(p);
        return p.level().getEntitiesOfClass(ServerPlayer.class, p.getBoundingBox().inflate(r),
                e -> e.isAlive() && e.distanceTo(p) <= r);
    }

    private static List<Holder<MobEffect>> effects(SkillDef def) {
        List<Holder<MobEffect>> out = new ArrayList<>();
        for (String id : def.effects()) BuiltInRegistries.MOB_EFFECT.get(Identifier.parse(id)).ifPresent(out::add);
        return out;
    }

    private static int ticks(double seconds) {
        return (int) Math.round(seconds * 20);
    }

    // ------------------------------------------------------------------ effects

    private static boolean damageArea(ServerPlayer p, ServerLevel level, SkillDef def, double power, boolean around) {
        Vec3 look = p.getLookAngle().multiply(1, 0, 1).normalize();
        Vec3 centre = around ? p.position() : p.position().add(look.scale(def.radius() * 0.5));
        List<Holder<MobEffect>> fx = effects(def);
        for (LivingEntity e : enemies(p, p.position(), def.radius())) {
            if (!around) {
                Vec3 to = e.position().subtract(p.position()).multiply(1, 0, 1).normalize();
                if (to.dot(look) < 0.35) continue; // ~70 degree cone in front
            }
            e.hurtServer(level, p.damageSources().playerAttack(p), (float) (def.power() * power));
            for (Holder<MobEffect> h : fx) e.addEffect(new MobEffectInstance(h, ticks(def.duration()), def.amplifier()), p);
        }
        if (around) ring(level, p.position(), def.radius() * 0.8, ParticleTypes.SWEEP_ATTACK, 16);
        else level.sendParticles(ParticleTypes.SWEEP_ATTACK, centre.x, p.getY() + 1, centre.z, 3, def.radius() * 0.3, 0.2, def.radius() * 0.3, 0);
        sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, around ? 0.8F : 1.0F);
        return true;
    }

    private static boolean push(ServerPlayer p, Vec3 v, SoundEvent s) {
        p.setDeltaMovement(v);
        p.connection.send(new ClientboundSetEntityMotionPacket(p));
        p.resetFallDistance();
        ((ServerLevel) p.level()).sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.2, p.getZ(), 10, 0.3, 0.1, 0.3, 0.02);
        sound(p, s, 1.2F);
        return true;
    }

    private static boolean blink(ServerPlayer p, ServerLevel level, double distance) {
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getLookAngle().scale(distance));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 target = hit.getType() == HitResult.Type.MISS ? end : hit.getLocation().subtract(p.getLookAngle().scale(0.6));
        level.sendParticles(ParticleTypes.PORTAL, p.getX(), p.getY() + 1, p.getZ(), 30, 0.3, 0.6, 0.3, 0.2);
        p.teleportTo(target.x, Math.max(target.y - p.getEyeHeight(), level.getMinY()), target.z);
        p.resetFallDistance();
        level.sendParticles(ParticleTypes.PORTAL, p.getX(), p.getY() + 1, p.getZ(), 30, 0.3, 0.6, 0.3, 0.2);
        sound(p, SoundEvents.ENDERMAN_TELEPORT, 1.2F);
        return true;
    }

    private static boolean shoot(ServerPlayer p, ServerLevel level, int count, double power) {
        count = Math.max(1, count);
        float spread = count == 1 ? 0 : Math.min(60, 8f * (count - 1));
        for (int i = 0; i < count; i++) {
            Arrow arrow = new Arrow(level, p, new ItemStack(Items.ARROW), p.getMainHandItem());
            arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
            arrow.setBaseDamage(2.0 * power);
            float yaw = p.getYRot() + (count == 1 ? 0 : -spread / 2 + spread * i / (count - 1));
            float pitch = count > 6 ? p.getXRot() - 25 : p.getXRot();
            arrow.shootFromRotation(p, pitch, yaw, 0, 3.0F, count > 6 ? 6.0F : 1.0F);
            level.addFreshEntity(arrow);
        }
        sound(p, SoundEvents.ARROW_SHOOT, 1.0F);
        return true;
    }

    private static boolean heal(ServerPlayer p, ServerLevel level, SkillDef def, double power) {
        for (ServerPlayer a : allies(p, def.radius())) {
            a.heal((float) (def.power() * power));
            level.sendParticles(ParticleTypes.HEART, a.getX(), a.getY() + 1.8, a.getZ(), 5, 0.4, 0.3, 0.4, 0);
        }
        ring(level, p.position(), Math.max(1, def.radius()), ParticleTypes.HAPPY_VILLAGER, 24);
        sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 1.5F);
        return true;
    }

    private static boolean buff(ServerPlayer p, ServerLevel level, SkillDef def) {
        List<Holder<MobEffect>> fx = effects(def);
        for (ServerPlayer a : allies(p, def.radius())) {
            for (Holder<MobEffect> h : fx) a.addEffect(new MobEffectInstance(h, ticks(def.duration()), def.amplifier()), p);
            level.sendParticles(ParticleTypes.ENCHANT, a.getX(), a.getY() + 1, a.getZ(), 20, 0.4, 0.6, 0.4, 0.5);
        }
        if (def.radius() > 0) ring(level, p.position(), def.radius(), ParticleTypes.END_ROD, 24);
        sound(p, SoundEvents.BEACON_POWER_SELECT, 1.4F);
        return true;
    }

    private static boolean debuff(ServerPlayer p, ServerLevel level, SkillDef def, double power) {
        List<Holder<MobEffect>> fx = effects(def);
        for (LivingEntity e : enemies(p, p.position(), def.radius())) {
            if (def.power() > 0) e.hurtServer(level, p.damageSources().indirectMagic(p, p), (float) (def.power() * power));
            for (Holder<MobEffect> h : fx) e.addEffect(new MobEffectInstance(h, ticks(def.duration()), def.amplifier()), p);
            level.sendParticles(ParticleTypes.WITCH, e.getX(), e.getY() + 1, e.getZ(), 8, 0.3, 0.5, 0.3, 0);
        }
        ring(level, p.position(), def.radius(), ParticleTypes.WITCH, 28);
        sound(p, SoundEvents.EVOKER_CAST_SPELL, 1.0F);
        return true;
    }

    private static boolean mana(ServerPlayer p, ServerLevel level, SkillDef def, double power) {
        for (ServerPlayer a : allies(p, def.radius())) {
            JobManager.restoreMana(a, def.power() * power);
            level.sendParticles(new DustParticleOptions(0x5599FF, 1.2F), a.getX(), a.getY() + 1, a.getZ(), 20, 0.4, 0.6, 0.4, 0);
        }
        sound(p, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.7F);
        return true;
    }

    private static boolean stealth(ServerPlayer p, ServerLevel level, SkillDef def) {
        p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, ticks(def.duration()), 0), p);
        for (Holder<MobEffect> h : effects(def)) p.addEffect(new MobEffectInstance(h, ticks(def.duration()), def.amplifier()), p);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, p.getX(), p.getY() + 1, p.getZ(), 40, 0.6, 0.8, 0.6, 0.02);
        sound(p, SoundEvents.ILLUSIONER_MIRROR_MOVE, 1.0F);
        return true;
    }

    /** Gambler: a random outcome scaled by the skill's power (1 = small bet, bigger = bigger swings). */
    private static boolean gamble(ServerPlayer p, ServerLevel level, SkillDef def, double power) {
        int roll = p.getRandom().nextInt(6);
        double bet = def.power();
        String result;
        switch (roll) {
            case 0 -> { // jackpot: strong hit all around
                SkillDef hit = copy(def, SkillEffect.WHIRL, 8 * bet, def.radius(), 0, List.of());
                damageArea(p, level, hit, power, true);
                result = "jackpot";
            }
            case 1 -> { // heal party
                heal(p, level, copy(def, SkillEffect.HEAL, 4 * bet, def.radius(), 0, List.of()), power);
                result = "heal";
            }
            case 2 -> { // buff
                buff(p, level, copy(def, SkillEffect.BUFF, 0, def.radius(), def.duration(), List.of("minecraft:strength", "minecraft:speed")));
                result = "buff";
            }
            case 3 -> { // curse enemies
                debuff(p, level, copy(def, SkillEffect.DEBUFF, 2 * bet, def.radius(), def.duration(), List.of("minecraft:weakness", "minecraft:slowness")), power);
                result = "curse";
            }
            case 4 -> { // mana
                mana(p, level, copy(def, SkillEffect.MANA, 15 * bet, 0, 0, List.of()), power);
                result = "mana";
            }
            default -> { // bust: hurts yourself a little
                p.hurtServer(level, p.damageSources().magic(), (float) (1.5 * bet));
                level.sendParticles(ParticleTypes.SMOKE, p.getX(), p.getY() + 1, p.getZ(), 20, 0.3, 0.5, 0.3, 0.02);
                result = "bust";
            }
        }
        p.sendSystemMessage(Component.translatable("rpgmod.gamble." + result).withStyle(roll == 5 ? ChatFormatting.GRAY : ChatFormatting.AQUA), true);
        sound(p, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5F + roll * 0.2F);
        return true;
    }

    private static SkillDef copy(SkillDef d, SkillEffect effect, double power, double radius, double duration, List<String> fx) {
        return d.withEffect(effect, power, radius, duration, fx);
    }

    private static boolean bolt(ServerPlayer p, ServerLevel level, SkillDef def, double power) {
        Vec3 eye = p.getEyePosition();
        Vec3 dir = p.getLookAngle();
        Vec3 end = eye.add(dir.scale(def.radius()));
        BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        if (block.getType() != HitResult.Type.MISS) end = block.getLocation();
        LivingEntity target = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, new AABB(eye, end).inflate(1.0),
                e -> e != p && e.isAlive() && !(e instanceof Player))) {
            if (e.getBoundingBox().inflate(0.4).clip(eye, end).isPresent()) {
                double d = e.distanceToSqr(p);
                if (d < best) { best = d; target = e; }
            }
        }
        Vec3 hitPos = target != null ? target.getEyePosition() : end;
        int steps = (int) (eye.distanceTo(hitPos) * 3);
        for (int i = 0; i < steps; i++) {
            Vec3 v = eye.add(hitPos.subtract(eye).scale(i / (double) steps));
            level.sendParticles(new DustParticleOptions(0xB088FF, 1.0F), v.x, v.y, v.z, 1, 0, 0, 0, 0);
        }
        if (target != null) {
            target.hurtServer(level, p.damageSources().indirectMagic(p, p), (float) (def.power() * power));
            level.sendParticles(ParticleTypes.ENCHANTED_HIT, hitPos.x, hitPos.y, hitPos.z, 12, 0.2, 0.2, 0.2, 0.2);
        }
        sound(p, SoundEvents.ILLUSIONER_CAST_SPELL, 1.6F);
        return true;
    }

    private static boolean lightning(ServerPlayer p, ServerLevel level, SkillDef def, double power) {
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getLookAngle().scale(def.radius()));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
        Vec3 at = hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
        LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
        if (bolt == null) return false;
        bolt.snapTo(at.x, at.y, at.z);
        bolt.setVisualOnly(true);
        level.addFreshEntity(bolt);
        for (LivingEntity e : enemies(p, at, 3)) {
            e.hurtServer(level, p.damageSources().indirectMagic(p, p), (float) (def.power() * power));
        }
        return true;
    }
}
