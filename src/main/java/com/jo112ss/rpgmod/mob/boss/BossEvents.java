package com.jo112ss.rpgmod.mob.boss;

import com.jo112ss.rpgmod.RpgMod;
import com.jo112ss.rpgmod.mob.MobConfig;
import com.jo112ss.rpgmod.registry.ModRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobSplitEvent;

/** Event glue for the bosses: attributes, the King Slime's globs, its damage cap and no vanilla split. */
@EventBusSubscriber(modid = RpgMod.MODID)
public final class BossEvents {
    private BossEvents() {}

    /** Marks a King Slime glob (a snowball that looks like a slime ball). */
    public static final String GLOB = "rpgmod_king_glob";
    /** Marks the small slimes the king throws out (removed with it). */
    public static final String CHILD = "rpgmod_king_child";

    @SubscribeEvent
    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(ModRegistries.KING_SLIME.get(), KingSlime.createAttributes().build());
        event.put(ModRegistries.GOLEM.get(), Golem.createAttributes().build());
        event.put(ModRegistries.GOLEM_PILLAR.get(), GolemPillar.createAttributes().build());
        event.put(ModRegistries.LICH.get(), Lich.createAttributes().build());
        event.put(ModRegistries.SPIDER_QUEEN.get(), SpiderQueen.createAttributes().build());
        event.put(ModRegistries.REVENANT.get(), com.jo112ss.rpgmod.mob.revenant.Revenant.createAttributes().build());
    }

    @SubscribeEvent
    public static void onGlobImpact(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof Snowball ball) || !ball.getPersistentData().getBooleanOr(GLOB, false)) return;
        if (!(ball.level() instanceof ServerLevel sl)) return;
        if (event.getRayTraceResult() instanceof EntityHitResult hit) {
            Entity e = hit.getEntity();
            if (e instanceof Enemy || !(e instanceof LivingEntity victim)) {
                event.setCanceled(true); // flies through the king's own slimes
                return;
            }
            if (ball.getOwner() instanceof KingSlime king) king.globHit(sl, ball, victim);
        } else {
            com.jo112ss.rpgmod.job.fx.ServerFx.of("king_glob", ball.position()).send(sl);
        }
    }

    /** No single boss hit takes more than a share of the player's max health (before armour). */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onBossHit(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        if (!(event.getSource().getEntity() instanceof ModBoss)) return;
        float cap = (float) (p.getMaxHealth() * MobConfig.bossMaxHit());
        if (event.getAmount() > cap) event.setAmount(cap);
    }

    /**
     * Health past the vanilla cap is played by dividing every hit by the king's toughness. Done last
     * (lowest priority), after everything that adds to a hit - arrows get the bow's material bonus
     * and the shooter's own attack added in events, which would otherwise come through undivided.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onKingHurt(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ModBoss king && king.toughness() > 1) {
            event.setAmount(event.getAmount() / king.toughness());
        }
    }

    /** The halfway gate ({@link BossCore#halfGate}): the last of the damage, what reaches its health. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHalfGate(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ModBoss b) || !b.core().halfGate(b.phase())) return;
        LivingEntity e = event.getEntity();
        float room = e.getHealth() - e.getMaxHealth() * 0.5F;
        if (event.getNewDamage() > room) event.setNewDamage(Math.max(0, room));
    }

    /** The king shrinks on its own terms; it never splits like a vanilla slime when it dies. */
    @SubscribeEvent
    public static void onSplit(MobSplitEvent event) {
        if (event.getParent() instanceof KingSlime) event.setCanceled(true);
    }

    /**
     * A boss is down: skill books for everyone who fought it or stood near (48 blocks), a fanfare, and
     * word to the server ({@code rpgmod.boss.<key>.defeated}).
     */
    public static void reward(ServerLevel sl, LivingEntity boss, java.util.Set<java.util.UUID> participants, String key) {
        java.util.Set<java.util.UUID> who = new java.util.HashSet<>(participants);
        for (net.minecraft.server.level.ServerPlayer p : sl.getEntitiesOfClass(net.minecraft.server.level.ServerPlayer.class, boss.getBoundingBox().inflate(48))) who.add(p.getUUID());
        int books = com.jo112ss.rpgmod.mob.MobConfig.bossBooks();
        java.util.List<String> names = new java.util.ArrayList<>();
        for (java.util.UUID id : who) {
            net.minecraft.server.level.ServerPlayer p = sl.getServer().getPlayerList().getPlayer(id);
            if (p == null) continue;
            names.add(p.getGameProfile().name());
            com.jo112ss.rpgmod.item.UniqueWeapons.reward(p, boss, key); // 25%: one of the boss's own weapons
            if (books > 0) {
                net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(ModRegistries.SKILL_BOOK.get(), books);
                if (!p.getInventory().add(stack)) p.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
                p.sendSystemMessage(Component.translatable("rpgmod.boss.reward", books).withStyle(net.minecraft.ChatFormatting.GOLD));
            }
            sl.playSound(null, p.getX(), p.getY(), p.getZ(), net.minecraft.sounds.SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        broadcast(sl.getServer(), Component.translatable("rpgmod.boss." + key + ".defeated", String.join(", ", names)).withStyle(net.minecraft.ChatFormatting.GOLD));
        RpgMod.LOGGER.info("[boss] {} defeated, {} participants rewarded", key, names.size());
    }

    public static void broadcast(MinecraftServer server, Component msg) {
        server.getPlayerList().broadcastSystemMessage(msg, false);
    }
}
