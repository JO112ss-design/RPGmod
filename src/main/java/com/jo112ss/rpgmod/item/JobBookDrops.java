package com.jo112ss.rpgmod.item;

import com.jo112ss.rpgmod.RpgMod;
import com.jo112ss.rpgmod.gem.GemDrops;
import com.jo112ss.rpgmod.registry.ModRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * Job change books from monsters killed by a player: 0.5% (luck and looting do not change it).
 * The dungeon bosses and the Ender Dragon always drop one.
 */
@EventBusSubscriber(modid = RpgMod.MODID)
public final class JobBookDrops {
    private JobBookDrops() {}

    public static final double CHANCE = 0.005;

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity mob = event.getEntity();
        if (!(mob.level() instanceof ServerLevel level)) return;
        boolean byPlayer = event.getSource().getEntity() instanceof Player || mob.getLastHurtByMob() instanceof Player;
        if (!byPlayer) return;
        boolean boss = mob instanceof EnderDragon || mob instanceof com.jo112ss.rpgmod.mob.boss.ModBoss;
        if (!boss) {
            if (!(mob instanceof Enemy) || GemDrops.isBoss(mob) || mob instanceof com.jo112ss.rpgmod.mob.boss.GolemPillar) return;
            if (mob.getPersistentData().getBooleanOr(com.jo112ss.rpgmod.mob.boss.BossEvents.CHILD, false)) return;
            if (mob.getRandom().nextDouble() >= CHANCE) return;
        }
        Vec3 at = mob.position().add(0, 0.5, 0);
        if (mob instanceof EnderDragon) { // it dies high in the air: drop it on the exit portal
            var top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, net.minecraft.core.BlockPos.ZERO);
            at = Vec3.atCenterOf(top).add(0, 1, 0);
        }
        ItemEntity e = new ItemEntity(level, at.x, at.y, at.z, new ItemStack(ModRegistries.JOB_BOOK.get()));
        e.setDefaultPickUpDelay();
        e.setGlowingTag(true);
        level.addFreshEntity(e);
        if (com.jo112ss.rpgmod.devtest.AutoTestServer.ENABLED) {
            RpgMod.LOGGER.info("[AUTOTEST] job book drop from {} (boss={})", mob.getType().getDescriptionId(), boss);
        }
    }
}
