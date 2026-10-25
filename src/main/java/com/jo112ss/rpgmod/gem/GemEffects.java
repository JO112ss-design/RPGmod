package com.jo112ss.rpgmod.gem;

import com.jo112ss.rpgmod.RpgMod;
import com.jo112ss.rpgmod.job.JobManager;
import com.jo112ss.rpgmod.job.SkillTasks;
import com.jo112ss.rpgmod.job.Venom;
import com.jo112ss.rpgmod.job.fx.ServerFx;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** What the rare and legendary gems do. Stat gems are handled with the forging options. */
@EventBusSubscriber(modid = RpgMod.MODID)
public final class GemEffects {
    private GemEffects() {}

    /** Damage dealt by gems themselves (lightning, shockwave): ignores hit cooldowns, no knockback. */
    public static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(RpgMod.MODID, "gem"));

    public static final String PHOENIX_NEXT = "rpgmod_phoenix_next";

    /** Set while a gem deals its own damage, so that damage does not set off more gems. */
    private static boolean busy;
    private static final Map<UUID, Long> MANA_NEXT = new HashMap<>();
    private static final Map<UUID, Long> STEAL_NEXT = new HashMap<>();

    /** Dev test only: every chance-based gem triggers. */
    public static boolean testAlways;

    private static boolean roll(LivingEntity who, double percent) {
        return percent > 0 && (testAlways || who.getRandom().nextDouble() * 100 < percent);
    }

    /** The weapon behind a hit: the bow for arrows, otherwise the main hand. */
    private static ItemStack weapon(DamageSource src) {
        return src.getDirectEntity() instanceof AbstractArrow a && a.getWeaponItem() != null ? a.getWeaponItem() : null;
    }

    private static boolean ownDamage(DamageSource src) {
        return busy || src.is(DAMAGE) || src.is(Venom.DAMAGE);
    }

    private static DamageSource source(ServerPlayer p) {
        return p.level().damageSources().source(DAMAGE, p, p);
    }

    /** Hurts with gem damage without setting off more gems. */
    private static void hurt(ServerLevel level, LivingEntity target, ServerPlayer by, float amount) {
        if (!target.isAlive() || amount <= 0) return;
        busy = true;
        try {
            target.hurtServer(level, source(by), amount);
        } finally {
            busy = false;
        }
    }

    // ------------------------------------------------------------------ attacking

    @SubscribeEvent
    public static void onAttack(LivingIncomingDamageEvent event) {
        DamageSource src = event.getSource();
        if (!(src.getEntity() instanceof ServerPlayer sp) || ownDamage(src)) return;
        LivingEntity target = event.getEntity();
        if (target == sp || event.getAmount() <= 0 || !(sp.level() instanceof ServerLevel level)) return;
        Map<GemSpecial, Integer> g = Gear.specials(sp, weapon(src));
        if (g.isEmpty()) return;

        double desperate = Gear.strength(g, GemSpecial.DESPERATION);
        if (desperate > 0 && sp.getHealth() <= sp.getMaxHealth() * 0.5F) {
            event.setAmount((float) (event.getAmount() * (1 + desperate / 100.0)));
        }
        if (g.containsKey(GemSpecial.IGNITE) && !target.fireImmune()) {
            target.igniteForSeconds((float) GemSpecial.IGNITE.value);
        }
        // bosses shrug off being slowed or frozen (like venom)
        boolean boss = GemDrops.isBoss(target);
        if (!boss && roll(sp, Gear.strength(g, GemSpecial.CHILL))) {
            target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1), sp);
            level.sendParticles(ParticleTypes.SNOWFLAKE, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 8, 0.3, 0.3, 0.3, 0.02);
        }
        if (roll(sp, Gear.strength(g, GemSpecial.VENOM))) {
            Venom.apply(target, 100, 0, sp);
        }
        if (!boss && g.containsKey(GemSpecial.FREEZE) && roll(sp, GemSpecial.FREEZE.value)) freeze(level, target, sp);
        if (g.containsKey(GemSpecial.MANA_DRAIN)) {
            long now = level.getGameTime();
            if (now >= MANA_NEXT.getOrDefault(sp.getUUID(), 0L)) {
                MANA_NEXT.put(sp.getUUID(), now + GemSpecial.HEAL_COOLDOWN);
                JobManager.restoreMana(sp, JobManager.maxMana(sp) * GemSpecial.MANA_DRAIN.value / 100.0);
                level.sendParticles(ParticleTypes.WITCH, sp.getX(), sp.getY() + 1.2, sp.getZ(), 4, 0.3, 0.3, 0.3, 0.02);
            }
        }
        if (g.containsKey(GemSpecial.THUNDER) && roll(sp, GemSpecial.THUNDER.value)) {
            float bolt = event.getAmount() * 0.5F;
            // a tick later, after this hit is done
            SkillTasks.later(1, () -> {
                LightningBolt l = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
                if (l != null) {
                    l.setVisualOnly(true);
                    l.snapTo(target.getX(), target.getY(), target.getZ());
                    level.addFreshEntity(l);
                }
                hurt(level, target, sp, bolt);
                log("thunder {} on {}", bolt, target);
            });
        }
    }

    private static void freeze(ServerLevel level, LivingEntity target, ServerPlayer by) {
        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 9, false, false), by);
        target.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 40, 128, false, false), by); // no jumping out of it
        target.setTicksFrozen(Math.max(target.getTicksFrozen(), target.getTicksRequiredToFreeze() + 30));
        level.sendParticles(ParticleTypes.SNOWFLAKE, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 20, 0.4, 0.5, 0.4, 0.01);
        level.playSound(null, target.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.7F, 1.6F);
        log("freeze {}", target);
    }

    /** Critical hit (RPG crit): the shockwave hits the enemies around the target. */
    public static void onCrit(ServerPlayer sp, LivingEntity target, DamageSource src, float damage) {
        if (ownDamage(src) || !(sp.level() instanceof ServerLevel level)) return;
        if (!Gear.specials(sp, weapon(src)).containsKey(GemSpecial.SHOCKWAVE)) return;
        float wave = (float) (damage * GemSpecial.SHOCKWAVE.value / 100.0);
        Vec3 c = target.position();
        SkillTasks.later(1, () -> {
            int hit = 0;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(3.0), x -> x != target && x != sp && x.isAlive())) {
                if (!(e instanceof Enemy) || (e instanceof TamableAnimal t && t.isOwnedBy(sp))) continue;
                hurt(level, e, sp, wave);
                hit++;
            }
            ServerFx.of("x_ring", c).a(3.0F).b(1).color(0xFFD27A).send(level);
            level.playSound(null, target.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.5F, 1.6F);
            log("shockwave {} to {} enemies", wave, hit);
        });
    }

    // ------------------------------------------------------------------ dealt / taken

    @SubscribeEvent
    public static void onDealt(LivingDamageEvent.Post event) {
        DamageSource src = event.getSource();
        float dealt = event.getHealthDamage();
        if (dealt <= 0) return;
        // lifesteal on the attacker
        if (src.getEntity() instanceof ServerPlayer sp && event.getEntity() != sp && !ownDamage(src)) {
            double steal = Gear.strength(Gear.specials(sp, weapon(src)), GemSpecial.LIFESTEAL);
            long now = sp.level().getGameTime();
            if (steal > 0 && now >= STEAL_NEXT.getOrDefault(sp.getUUID(), 0L)) {
                STEAL_NEXT.put(sp.getUUID(), now + GemSpecial.HEAL_COOLDOWN);
                com.jo112ss.rpgmod.job.HealthScale.heal(sp, (float) (dealt * steal / 100.0));
            }
        }
        // thorns on the one hit: melee only
        if (event.getEntity() instanceof ServerPlayer victim && src.getEntity() instanceof LivingEntity attacker
                && src.getDirectEntity() == attacker && attacker != victim && !(attacker instanceof Player) && !ownDamage(src)
                && victim.level() instanceof ServerLevel level) {
            double thorns = Gear.strength(Gear.specials(victim, null), GemSpecial.THORNS);
            if (thorns > 0) {
                hurt(level, attacker, victim, (float) (dealt * thorns / 100.0));
                level.sendParticles(ParticleTypes.CRIT, attacker.getX(), attacker.getY() + attacker.getBbHeight() * 0.6, attacker.getZ(), 6, 0.3, 0.3, 0.3, 0.1);
            }
        }
    }

    /** Phoenix: a lethal hit leaves 1 HP instead, once every 5 minutes. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLethal(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !(p.level() instanceof ServerLevel level)) return;
        float room = p.getHealth() + p.getAbsorptionAmount() - 1;
        if (event.getNewDamage() <= room || !Gear.has(p, GemSpecial.PHOENIX)) return;
        long now = level.getGameTime();
        if (now < p.getPersistentData().getLongOr(PHOENIX_NEXT, 0L)) return;
        p.getPersistentData().putLong(PHOENIX_NEXT, now + (long) (GemSpecial.PHOENIX.value * 60 * 20));
        event.setNewDamage(Math.max(0, room));
        p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 40, 3));
        p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
        level.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 1, p.getZ(), 40, 0.5, 0.8, 0.5, 0.08);
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, p.getX(), p.getY() + 1, p.getZ(), 30, 0.5, 0.8, 0.5, 0.3);
        level.playSound(null, p.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.8F, 1.2F);
        p.sendSystemMessage(Component.translatable("rpgmod.gem.phoenix_used", (int) GemSpecial.PHOENIX.value).withStyle(ChatFormatting.GOLD), true);
        log("phoenix saved {} at {}", p.getName().getString(), p.getHealth());
    }

    @SubscribeEvent
    public static void onKill(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer sp) || event.getEntity() == sp) return;
        double secs = Gear.strength(Gear.specials(sp, weapon(event.getSource())), GemSpecial.TAILWIND);
        if (secs > 0) sp.addEffect(new MobEffectInstance(MobEffects.SPEED, (int) (secs * 20), 0));
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        double cut = Gear.strength(Gear.specials(p, null), GemSpecial.ACROBAT);
        if (cut > 0) event.setDamageMultiplier((float) (event.getDamageMultiplier() * (1 - cut / 100.0)));
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.tickCount % 100 != 0 || !p.isAlive()) return;
        if (p.getHealth() < p.getMaxHealth() && Gear.has(p, GemSpecial.REGENERATION)) {
            com.jo112ss.rpgmod.job.HealthScale.heal(p, (float) (p.getMaxHealth() * GemSpecial.REGENERATION.value / 100.0));
        }
    }

    /** Endless arrows: a player's arrow shot from a bow or crossbow comes back to the quiver. */
    @SubscribeEvent
    public static void onArrow(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || event.loadedFromDisk()) return;
        if (!(event.getEntity() instanceof AbstractArrow arrow) || !(arrow.getOwner() instanceof ServerPlayer sp)) return;
        if (arrow.getWeaponItem() == null || arrow.pickup != AbstractArrow.Pickup.ALLOWED || sp.hasInfiniteMaterials()) return;
        if (!Gear.specials(sp, arrow.getWeaponItem()).containsKey(GemSpecial.ENDLESS_ARROWS)) return;
        ItemStack ammo = arrow.getPickupItemStackOrigin().copyWithCount(1);
        if (ammo.isEmpty()) return;
        arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY; // no second copy from picking it up
        log("endless arrow: giving back {}", ammo);
        sp.getInventory().placeItemBackInInventory(ammo, net.minecraft.util.Prediction.SERVER_ONLY);
    }

    private static void log(String msg, Object... args) {
        if (com.jo112ss.rpgmod.devtest.AutoTestServer.ENABLED) RpgMod.LOGGER.info("[AUTOTEST] gem " + msg, args);
    }

    public static void forget(UUID id) {
        MANA_NEXT.remove(id);
        STEAL_NEXT.remove(id);
    }
}
