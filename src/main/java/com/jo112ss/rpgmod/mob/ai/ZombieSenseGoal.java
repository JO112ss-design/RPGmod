package com.jo112ss.rpgmod.mob.ai;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.player.Player;

/**
 * Zombies (and creepers, spiders and skeletons) sense players anywhere within their follow range
 * (64 blocks by default), through walls, floors and caves alike, and never lose them for being out
 * of sight. Runs ahead of vanilla's "must see the player" goal. Spiders keep their vanilla daytime
 * calm: in bright light they do not pick a target.
 */
public final class ZombieSenseGoal extends NearestAttackableTargetGoal<Player> {
    public ZombieSenseGoal(Mob mob) {
        super(mob, Player.class, 10, false, false, null);
        this.targetConditions.ignoreLineOfSight();
    }

    @Override
    public boolean canUse() {
        if (mob instanceof Spider && mob.getLightLevelDependentMagicValue() >= 0.5F) return false;
        return super.canUse();
    }
}
