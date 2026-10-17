package com.jo112ss.rpgmod.mob;

import com.jo112ss.rpgmod.RpgMod;
import com.jo112ss.rpgmod.mob.core.MobLevelMath;
import com.jo112ss.rpgmod.mob.core.MobLevelMath.Realm;
import com.jo112ss.rpgmod.registry.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Gives every monster a level when it first enters the world (kept with the entity afterwards) and
 * makes it that much stronger: more health, harder hits (melee, arrows, fireballs and, less so,
 * explosions), a little faster and steadier. The level is synced so the client can show it.
 */
@EventBusSubscriber(modid = RpgMod.MODID)
public final class MobLevels {
    private MobLevels() {}

    /** The Ender Dragon is a fixed level: it is the final fight, not a roll of the dice. */
    public static final int DRAGON_LEVEL = 100;

    private static final Identifier HP = id("level_health"), SPEED = id("level_speed"), FLY = id("level_fly_speed"), KB = id("level_knockback"), FOLLOW = id("level_follow");

    private static Identifier id(String p) {
        return Identifier.fromNamespaceAndPath(RpgMod.MODID, p);
    }

    public static int level(Entity e) {
        return e == null ? 0 : e.getData(ModRegistries.MOB_LEVEL);
    }

    public static boolean isLevelled(Entity e) {
        return e instanceof Mob && e instanceof Enemy;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !MobConfig.levels()) return;
        if (!(event.getEntity() instanceof Mob mob) || !(mob instanceof Enemy)) return;
        if (mob instanceof com.jo112ss.rpgmod.mob.boss.KingSlime king) {
            // bosses: level = highest player level (+ bonus), health set by the boss itself
            boolean fresh = level(mob) <= 0;
            if (fresh) mob.setData(ModRegistries.MOB_LEVEL, Math.max(1, topPlayerLevel(((ServerLevel) event.getLevel()).getServer()) + MobConfig.bossLevelBonus()));
            king.recomputeHealth(fresh);
            return;
        }
        if (level(mob) > 0) { // already levelled (loaded from disk): refresh the modifiers in case the settings changed
            apply(mob, mob instanceof EnderDragon ? DRAGON_LEVEL : level(mob));
            return;
        }
        ServerLevel sl = (ServerLevel) event.getLevel();
        int lv = mob instanceof EnderDragon ? dragonLevel(sl) : rollLevel(sl, mob.blockPosition(), mob.getRandom().nextDouble());
        apply(mob, lv);
        MobGear.equip(mob, lv);
        com.jo112ss.rpgmod.mob.ai.MobAi.onFirstLevel(mob, lv);
    }

    /** Highest job level among the players online (1 if nobody has a job yet). */
    public static int topPlayerLevel(net.minecraft.server.MinecraftServer server) {
        int top = 1;
        for (net.minecraft.server.level.ServerPlayer p : server.getPlayerList().getPlayers()) {
            var d = com.jo112ss.rpgmod.job.JobManager.data(p);
            if (d.hasJob()) top = Math.max(top, d.level());
        }
        return top;
    }

    /** The dragon is always the same level: it is the final fight, not a roll of the dice. */
    public static int dragonLevel(ServerLevel level) {
        return DRAGON_LEVEL;
    }

    /** Level for a monster appearing at {@code pos}. */
    public static int rollLevel(ServerLevel level, BlockPos pos, double roll) {
        if (MobConfig.fromPlayers()) {
            return MobLevelMath.playerRelative(topPlayerLevel(level.getServer()), MobConfig.playerMin(), MobConfig.playerMax(), roll);
        }
        MobLevelMath.Rules r = MobConfig.rules();
        Realm realm = realm(level);
        double cx = 0, cz = 0;
        if (realm != Realm.END) {
            var spawn = level.getServer().getRespawnData();
            BlockPos sp = spawn.dimension() == Level.OVERWORLD ? spawn.pos() : BlockPos.ZERO;
            double scale = realm == Realm.NETHER ? 1 / 8.0 : 1;
            cx = sp.getX() * scale;
            cz = sp.getZ() * scale;
        }
        double dist = Math.hypot(pos.getX() + 0.5 - cx, pos.getZ() + 0.5 - cz);
        return MobLevelMath.level(r, realm, dist, pos.getY(), roll);
    }

    public static Realm realm(Level level) {
        if (level.dimension() == Level.NETHER) return Realm.NETHER;
        if (level.dimension() == Level.END) return Realm.END;
        return Realm.OVERWORLD;
    }

    /** Sets the level and the stat modifiers that come with it (replacing any from before). */
    public static void apply(Mob mob, int lv) {
        MobLevelMath.Rules r = MobConfig.rules();
        boolean full = mob.getHealth() >= mob.getMaxHealth() - 0.01F;
        double hp = MobLevelMath.healthMultiplier(r, lv) - 1;
        if (mob instanceof EnderDragon) hp = MobConfig.dragonHealth() - 1; // 600 health by default: tuned for the three-stage fight
        modifier(mob, Attributes.MAX_HEALTH, HP, hp, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        if (!(mob instanceof EnderDragon)) {
            // every hostile monster moves faster (x1.5 by default), on foot and in the air
            double speed = (1 + MobLevelMath.speedBonus(r, lv)) * MobConfig.speed() - 1;
            modifier(mob, Attributes.MOVEMENT_SPEED, SPEED, speed, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            modifier(mob, Attributes.FLYING_SPEED, FLY, speed, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            modifier(mob, Attributes.KNOCKBACK_RESISTANCE, KB, MobLevelMath.knockbackResistance(r, lv), AttributeModifier.Operation.ADD_VALUE);
            AttributeInstance follow = mob.getAttribute(Attributes.FOLLOW_RANGE);
            if (com.jo112ss.rpgmod.mob.ai.MobAi.isBuilderZombie(mob) && follow != null) {
                // zombies hunt from far away (128 blocks by default)
                modifier(mob, Attributes.FOLLOW_RANGE, FOLLOW, Math.max(0, MobConfig.zombieRange() - follow.getBaseValue()), AttributeModifier.Operation.ADD_VALUE);
            } else {
                // stronger monsters notice you from further away (+1 block per 4 levels, up to +16)
                modifier(mob, Attributes.FOLLOW_RANGE, FOLLOW, Math.min(16, lv / 4.0), AttributeModifier.Operation.ADD_VALUE);
            }
        }
        if (full) mob.setHealth(mob.getMaxHealth());
        mob.setData(ModRegistries.MOB_LEVEL, lv);
    }

    private static void modifier(LivingEntity e, Holder<Attribute> attr, Identifier id, double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = e.getAttribute(attr);
        if (inst == null) return;
        inst.removeModifier(id);
        if (amount != 0) inst.addPermanentModifier(new AttributeModifier(id, amount, op));
    }

    /** The monster that is really behind a damage source (the skeleton for its arrow...). */
    public static LivingEntity attacker(net.minecraft.world.damagesource.DamageSource source) {
        Entity e = source.getEntity();
        return e instanceof LivingEntity le ? le : null;
    }

    /** Levelled monsters hit harder - every kind of damage they cause, against anything that is not a monster. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDamage(LivingIncomingDamageEvent event) {
        LivingEntity attacker = attacker(event.getSource());
        if (attacker == null || attacker == event.getEntity() || event.getEntity() instanceof Enemy) return;
        int lv = level(attacker);
        if (lv <= 0) return;
        MobLevelMath.Rules r = MobConfig.rules();
        double mult;
        if (attacker instanceof EnderDragon) mult = MobConfig.dragonDamage(); // its breath ignores armour, so it gets its own, gentler factor
        else mult = event.getSource().is(DamageTypeTags.IS_EXPLOSION) ? MobLevelMath.explosionMultiplier(r, lv) : MobLevelMath.damageMultiplier(r, lv);
        event.setAmount((float) (event.getAmount() * mult));
    }

    /** Vanilla experience orbs grow a little with level too (job experience already follows max health). */
    @SubscribeEvent
    public static void onXp(LivingExperienceDropEvent event) {
        int lv = level(event.getEntity());
        if (lv > 1) event.setDroppedExperience((int) Math.round(event.getDroppedExperience() * (1 + 0.02 * (lv - 1))));
    }
}
