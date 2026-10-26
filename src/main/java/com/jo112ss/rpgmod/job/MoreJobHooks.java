package com.jo112ss.rpgmod.job;

import com.jo112ss.rpgmod.RpgMod;
import com.jo112ss.rpgmod.item.ModItems;
import com.jo112ss.rpgmod.item.WeaponType;
import com.jo112ss.rpgmod.job.core.JobStat;
import com.jo112ss.rpgmod.job.fx.ServerFx;
import com.jo112ss.rpgmod.registry.ModEffects;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Fight effects of the captain, champion, dragoon, maestro and the hybrid jobs: the spellblade's
 * element on sword hits, battle aura, the hero's song, the flame melody, ambush / night hunt, double
 * down; foresight, counter lance, spell guard, immovable and fortress on the receiving side; and the
 * treasure hunter's extra loot.
 */
@EventBusSubscriber(modid = RpgMod.MODID)
public final class MoreJobHooks {
    private MoreJobHooks() {}

    public static final double BATTLE_AURA_PER_KI = 0.04, HEROIC = 0.3, AMBUSH = 0.6, HUNTED = 0.5, FLAME_STEP = 0.03;
    /** Spellblade sword hits: this share of the spellblade's own magic attack on top, as the element. */
    public static final double ELEMENT_SHARE = 0.5;
    /** Gold rush (treasure rain coins): this much more damage per coin held. */
    public static final double GOLD_RUSH = 0.05;

    @SubscribeEvent
    public static void onIncoming(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level)) return;
        DamageSource src = event.getSource();
        float amount = event.getAmount();
        if (amount <= 0) return;

        // ---- the attacker's side
        if (src.getEntity() instanceof ServerPlayer sp && sp != victim && !src.is(Venom.DAMAGE)) {
            JobData d = JobManager.data(sp);
            if (sp.hasEffect(ModEffects.BATTLE_AURA)) amount *= (float) (1 + BATTLE_AURA_PER_KI * d.ki());
            if (sp.hasEffect(ModEffects.HEROIC)) amount *= (float) (1 + HEROIC);
            // counter lance: the stored blows ride on your next spear thrust (your own swing, not a skill or anything lingering)
            double stored = victim instanceof Player || !NewJobHooks.melee(src, sp) ? 0 : MoreJobSkills.lanceStore(sp);
            if (stored > 0) {
                amount += (float) stored;
                MoreJobSkills.LANCE_STORE.remove(sp.getUUID());
                sp.removeEffect(ModEffects.COUNTER_LANCE);
                ServerFx.of("x_burst", victim.position().add(0, victim.getBbHeight() * 0.5, 0)).a(1.6).b(2).color(0xFFD27A).send(level);
                ServerFx.of("x_beam", sp.position().add(0, 1.2, 0)).vec(victim.position().add(0, victim.getBbHeight() * 0.5, 0)).a(0.3).color(0xFFD27A).send(level);
                level.playSound(null, victim.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.8F, 1.4F);
                level.playSound(null, victim.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.5F, 1.6F);
                sp.sendSystemMessage(net.minecraft.network.chat.Component.translatable("rpgmod.job.lance_release", Math.round(stored)).withStyle(net.minecraft.ChatFormatting.GOLD), true);
            }
            MobEffectInstance rush = sp.getEffect(ModEffects.GOLD_RUSH);
            if (rush != null) amount *= (float) (1 + GOLD_RUSH * (rush.getAmplifier() + 1));
            MobEffectInstance flame = sp.getEffect(ModEffects.FLAME_SONG);
            if (flame != null) {
                amount *= (float) (1 + FLAME_STEP * (flame.getAmplifier() + 1));
                victim.igniteForSeconds(2);
            }
            if (sp.hasEffect(ModEffects.AMBUSH) && !(victim instanceof Player)) amount *= (float) (1 + AMBUSH); // taken off after the crit (below)
            if (sp.hasEffect(ModEffects.NIGHT_HUNT) && victim.hasEffect(ModEffects.HUNTED)) amount *= (float) (1 + HUNTED);
            if (sp.hasEffect(ModEffects.DOUBLE_DOWN) && !(victim instanceof Player)) {
                boolean win = sp.getRandom().nextBoolean() || JobManager.luckyReroll(sp) && sp.getRandom().nextBoolean();
                amount *= win ? 2F : 0.5F;
                ServerFx.of("x_hit", victim.position().add(0, victim.getBbHeight() * 0.6, 0)).vec(victim.position().subtract(sp.position())).a(win ? 1.2 : 0.5)
                        .color(win ? 0xFFD040 : 0x808080).send(level);
            }
            // spellblade: the element rides on every sword hit
            boolean melee = NewJobHooks.melee(src, sp);
            if (melee && "spellblade".equals(d.jobId()) && ModItems.typeOf(sp.getMainHandItem().getItem()) == WeaponType.SWORD && !(victim instanceof Player)) {
                int el = MoreJobSkills.element(sp);
                float extra = (float) (ELEMENT_SHARE * JobStats.compute(d).get(JobStat.MAGIC_ATTACK));
                amount += extra;
                MoreJobSkills.elementHit(sp, level, victim, el, extra * 2);
                ServerFx.of("x_hit", victim.position().add(0, victim.getBbHeight() * 0.6, 0)).vec(victim.position().subtract(sp.position())).a(0.8)
                        .color(MoreJobSkills.ELEMENT_COLORS[el]).send(level);
            }
        }

        // ---- the victim's side
        if (victim instanceof ServerPlayer vp) {
            LivingEntity att = src.getEntity() instanceof LivingEntity l && !(l instanceof Player) ? l : null;
            boolean meleeHit = att != null && src.getDirectEntity() == att && att.distanceTo(vp) < 6;
            if (meleeHit && vp.hasEffect(ModEffects.FORESIGHT)) {
                Monk.add(vp, 1); // foresight dodges every blow while it lasts
                float back = MoreJobSkills.FORESIGHT_POWER.getOrDefault(vp.getUUID(), 0F);
                SkillTasks.later(1, () -> {
                    if (att.isAlive()) att.hurtServer(level, vp.damageSources().source(SkillExecutor.SKILL_DAMAGE, vp, vp), back);
                    SkillExecutor.pushAway(att, vp.position(), 0.9, 0.3);
                });
                Vec3Fx.counter(level, vp, att, 0xFFB070);
                level.playSound(null, vp.blockPosition(), SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 1.0F, 1.4F);
                event.setCanceled(true);
                return;
            }
            // counter lance: the shield takes most of every blow (from a creature, near or far), and stores it
            double[] store = MoreJobSkills.LANCE_STORE.get(vp.getUUID());
            if (store != null && vp.hasEffect(ModEffects.COUNTER_LANCE) && src.getEntity() instanceof LivingEntity from && from != vp) {
                float blocked = amount * MoreJobSkills.LANCE_BLOCK;
                amount -= blocked;
                store[0] = Math.min(store[1], store[0] + blocked * MoreJobSkills.LANCE_STORE_RATE);
                net.minecraft.world.phys.Vec3 at = vp.position().add(0, 1.1, 0).add(from.position().subtract(vp.position()).multiply(1, 0, 1).normalize().scale(0.7));
                ServerFx.of("x_hit", at).vec(vp.position().subtract(from.position())).a(1.1).color(0xE0C080).send(level);
                ServerFx.of("x_burst", vp.position().add(0, 1.0, 0)).a(0.6).b(2).color(0xFFD27A).send(level);
                level.playSound(null, vp.blockPosition(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.PLAYERS, 1.0F, 1.2F);
                vp.sendSystemMessage(net.minecraft.network.chat.Component.translatable("rpgmod.job.lance_store", Math.round(store[0]),
                        Math.round(store[1])), true);
            }
            if (vp.hasEffect(ModEffects.SPELL_GUARD)) {
                amount *= 0.7F;
                if (meleeHit) {
                    int el = MoreJobSkills.element(vp);
                    float back = MoreJobSkills.GUARD_POWER.getOrDefault(vp.getUUID(), 0F) * 0.3F;
                    SkillTasks.later(1, () -> {
                        if (!att.isAlive()) return;
                        if (back > 0) att.hurtServer(level, vp.damageSources().source(SkillExecutor.SKILL_MAGIC_DAMAGE, vp, vp), back);
                        MoreJobSkills.elementHit(vp, level, att, el, back);
                    });
                }
            }
            if (vp.hasEffect(ModEffects.IMMOVABLE)) amount *= 0.6F;
            if (vp.hasEffect(ModEffects.FORTRESS)) amount *= 0.5F;
        }
        event.setAmount(amount);
    }

    /** The ambush is used up by the hit that carried it (after the crit roll, which it makes sure of). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void afterHit(LivingIncomingDamageEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer sp && sp != event.getEntity() && sp.hasEffect(ModEffects.AMBUSH)
                && !(event.getEntity() instanceof Player)) {
            sp.removeEffect(ModEffects.AMBUSH);
            sp.removeEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY);
        }
    }

    /** Treasure hunter's compass: monsters killed drop a little extra treasure. */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim instanceof Player || !(victim.level() instanceof ServerLevel level)) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer sp) || !sp.hasEffect(ModEffects.TREASURE)) return;
        var rnd = sp.getRandom();
        if (rnd.nextFloat() > 0.4F && !(JobManager.luckyReroll(sp) && rnd.nextFloat() < 0.4F)) return;
        double roll = rnd.nextDouble();
        ItemStack loot = roll < 0.03 ? new ItemStack(Items.DIAMOND) : roll < 0.2 ? new ItemStack(Items.EMERALD)
                : new ItemStack(Items.GOLD_NUGGET, 2 + rnd.nextInt(4));
        ItemEntity item = new ItemEntity(level, victim.getX(), victim.getY() + 0.5, victim.getZ(), loot);
        level.addFreshEntity(item);
        ServerFx.of("x_burst", victim.position().add(0, 0.5, 0)).a(1).b(4).color(0xD4AF37).send(level);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        MoreJobSkills.FORESIGHT_POWER.remove(id);
        MoreJobSkills.LANCE_STORE.remove(id);
        MoreJobSkills.GUARD_POWER.remove(id);
    }

    /** Small effect helpers. */
    private static final class Vec3Fx {
        static void counter(ServerLevel level, ServerPlayer vp, LivingEntity att, int color) {
            ServerFx.of("x_hit", att.position().add(0, att.getBbHeight() * 0.5, 0)).vec(att.position().subtract(vp.position())).a(1.3).color(color).send(level);
            ServerFx.of("x_beam", vp.position().add(0, 1.2, 0)).vec(att.position().add(0, att.getBbHeight() * 0.5, 0)).a(0.15).color(color).send(level);
        }
    }
}
