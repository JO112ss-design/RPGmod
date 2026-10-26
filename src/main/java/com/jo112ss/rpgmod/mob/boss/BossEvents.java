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
        if (!(event.getSource().getEntity() instanceof BloodMoonBoss)) return;
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
        if (event.getEntity() instanceof BloodMoonBoss king && king.toughness() > 1) {
            event.setAmount(event.getAmount() / king.toughness());
        }
    }

    /** The king shrinks on its own terms; it never splits like a vanilla slime when it dies. */
    @SubscribeEvent
    public static void onSplit(MobSplitEvent event) {
        if (event.getParent() instanceof KingSlime) event.setCanceled(true);
    }

    public static void broadcast(MinecraftServer server, Component msg) {
        server.getPlayerList().broadcastSystemMessage(msg, false);
    }
}
