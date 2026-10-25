package com.jo112ss.rpgmod.gem;

import com.jo112ss.rpgmod.RpgMod;
import com.jo112ss.rpgmod.mob.MobLevels;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * Gems from monsters killed by a player: 0.6% up to level 100, then rising to 1.5% at level 200.
 * Luck and looting do not change it. Bosses always drop one, more often rare or legendary.
 */
@EventBusSubscriber(modid = RpgMod.MODID)
public final class GemDrops {
    private GemDrops() {}

    public static final double BASE = 0.006, TOP = 0.015;

    /** Drop chance of an ordinary monster of this level. */
    public static double chance(int level) {
        if (level <= 100) return BASE;
        return BASE + (TOP - BASE) * (Math.min(level, 200) - 100) / 100.0;
    }

    public static boolean isBoss(LivingEntity e) {
        return e.is(Tags.EntityTypes.BOSSES) || e instanceof com.jo112ss.rpgmod.mob.boss.BloodMoonBoss;
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity mob = event.getEntity();
        if (!(mob.level() instanceof ServerLevel level)) return;
        boolean byPlayer = event.getSource().getEntity() instanceof Player || mob.getLastHurtByMob() instanceof Player;
        if (!byPlayer) return;
        boolean boss = isBoss(mob);
        if (!boss) {
            if (!(mob instanceof Enemy) || mob instanceof com.jo112ss.rpgmod.mob.boss.GolemPillar) return;
            if (mob.getPersistentData().getBooleanOr(com.jo112ss.rpgmod.mob.boss.BossEvents.CHILD, false)) return;
            if (mob.getRandom().nextDouble() >= chance(MobLevels.level(mob))) return;
        }
        Gem gem = GemRoller.roll(mob.getRandom(), boss);
        Vec3 at = mob.position().add(0, 0.5, 0);
        if (mob instanceof EnderDragon) { // it dies high in the air: drop it on the exit portal
            var top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, net.minecraft.core.BlockPos.ZERO);
            at = Vec3.atCenterOf(top).add(0, 1, 0);
        }
        ItemEntity e = new ItemEntity(level, at.x, at.y, at.z, GemItems.stack(gem));
        e.setDefaultPickUpDelay();
        e.setGlowingTag(gem.rarity() >= Gem.RARE);
        level.addFreshEntity(e);
        if (com.jo112ss.rpgmod.devtest.AutoTestServer.ENABLED) {
            RpgMod.LOGGER.info("[AUTOTEST] gem drop from {} (boss={}): {} {} {}", mob.getType().getDescriptionId(), boss, gem.rarityId(), gem.effect(), gem.value());
        }
    }
}
