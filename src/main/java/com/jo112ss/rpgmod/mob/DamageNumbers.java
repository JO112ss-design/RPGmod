package com.jo112ss.rpgmod.mob;

import com.jo112ss.rpgmod.RpgMod;
import com.jo112ss.rpgmod.job.fx.ServerFx;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;

/**
 * Sends floating damage numbers (client effect "dmg") to the players near a hit: the health actually
 * lost, coloured by kind. Only nearby players get them, so a big fight stays cheap on the network.
 */
@EventBusSubscriber(modid = RpgMod.MODID)
public final class DamageNumbers {
    private DamageNumbers() {}

    public static final int NORMAL = 0, CRIT = 1, MAGIC = 2, FIRE = 3, POISON = 4, PLAYER = 5, HEAL = 6, EXPLOSION = 7;
    private static final double RANGE = 32;
    private static final ResourceKey<DamageType> SKILL_MAGIC =
            ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(RpgMod.MODID, "skill_magic"));

    /** Entities that just took an RPG crit (game time of the crit), so the number shows in gold. */
    private static final Map<LivingEntity, Long> CRITS = new WeakHashMap<>();

    public static void markCrit(LivingEntity target) {
        CRITS.put(target, target.level().getGameTime());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent.Post event) {
        LivingEntity e = event.getEntity();
        if (!(e.level() instanceof ServerLevel sl)) return;
        float amount = event.getInflictedDamage();
        if (amount < 0.05F) return;
        Long crit = CRITS.remove(e);
        int kind = kind(e, event.getSource(), crit != null && crit >= sl.getGameTime() - 1);
        send(sl, e, amount, kind);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHeal(LivingHealEvent event) {
        LivingEntity e = event.getEntity();
        // small ticks (natural regeneration) would be noise; skill heals and potions are bigger
        if (!(e.level() instanceof ServerLevel sl) || event.getAmount() < 1.5F || e.getHealth() >= e.getMaxHealth()) return;
        send(sl, e, Math.min(event.getAmount(), e.getMaxHealth() - e.getHealth()), HEAL);
    }

    private static int kind(LivingEntity e, DamageSource s, boolean crit) {
        if (e instanceof Player) return PLAYER;
        if (crit) return CRIT;
        if (s.is(DamageTypeTags.IS_EXPLOSION)) return EXPLOSION;
        if (s.is(DamageTypeTags.IS_FIRE)) return FIRE;
        if (s.is(DamageTypes.WITHER) || (s.is(DamageTypes.MAGIC) && s.getEntity() == null && e.hasEffect(MobEffects.POISON))) return POISON;
        if (s.is(SKILL_MAGIC) || s.is(DamageTypes.MAGIC) || s.is(DamageTypes.INDIRECT_MAGIC)) return MAGIC;
        return NORMAL;
    }

    private static void send(ServerLevel sl, LivingEntity e, float amount, int kind) {
        // the King Slime past the health cap: show what the hit was really worth against its full health
        if (e instanceof com.jo112ss.rpgmod.mob.boss.BloodMoonBoss k) amount *= k.toughness();
        if (e instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon d) amount *= com.jo112ss.rpgmod.mob.dragon.DragonDirector.toughness(d);
        // from the upper body, below the health plate; the client lets it rise and drift sideways
        Vec3 at = e.position().add(0, e.getBbHeight() * 0.7, 0);
        ServerFx.of("dmg", at).follow(e).a(amount).b(kind).send(sl, RANGE);
    }
}
