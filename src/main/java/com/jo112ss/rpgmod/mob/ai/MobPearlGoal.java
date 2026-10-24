package com.jo112ss.rpgmod.mob.ai;

import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Spiders and skeletons that carry ender pearls (out of sight, counted on the mob) throw one to close
 * the distance to a player they can see but who is far off - like the zombies' off-hand pearl.
 */
public final class MobPearlGoal extends Goal {
    /** Pearls carried, kept in the mob's saved data. */
    public static final String KEY = "rpgmod_pearls";

    private final Mob mob;
    private int cooldown = 60;

    public MobPearlGoal(Mob mob) {
        this.mob = mob;
        setFlags(EnumSet.noneOf(Goal.Flag.class));
    }

    public static int pearls(Mob mob) {
        return mob.getPersistentData().getIntOr(KEY, 0);
    }

    private LivingEntity target() {
        LivingEntity t = mob.getTarget();
        return t instanceof Player p && !p.isCreative() && !p.isSpectator() && p.isAlive() ? p : null;
    }

    @Override
    public boolean canUse() {
        return mob.level() instanceof ServerLevel && pearls(mob) > 0 && target() != null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        LivingEntity t = target();
        if (t == null || !(mob.level() instanceof ServerLevel sl)) return;
        double d = mob.distanceTo(t);
        // archers only bother when out of bow range; spiders as soon as the player is a little away
        double min = mob instanceof AbstractSkeleton ? 18 : 9;
        if (d < min || d > 32 || !mob.getSensing().hasLineOfSight(t)) return;
        Vec3 from = mob.getEyePosition().add(mob.getLookAngle().scale(0.4)).add(0, -0.3, 0);
        ThrownEnderpearl pearl = new ThrownEnderpearl(sl, mob, new ItemStack(Items.ENDER_PEARL));
        pearl.setPos(from.x, from.y, from.z);
        double dx = t.getX() - from.x, dz = t.getZ() - from.z, dy = t.getY() + 0.2 - from.y;
        double hd = Math.sqrt(dx * dx + dz * dz);
        pearl.shoot(dx, dy + hd * 0.2, dz, 1.5F, 1.0F);
        sl.addFreshEntity(pearl);
        mob.getPersistentData().putInt(KEY, pearls(mob) - 1);
        sl.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.ENDER_PEARL_THROW, SoundSource.HOSTILE, 0.8F, 0.6F);
        cooldown = 120 + mob.getRandom().nextInt(60);
        if (com.jo112ss.rpgmod.devtest.AutoTestServer.ENABLED) com.jo112ss.rpgmod.RpgMod.LOGGER.info("[AUTOTEST] {} threw a pearl at distance {}", mob.getType().getDescriptionId(), String.format("%.1f", d));
    }
}
