package com.jo112ss.rpgmod.mob.ai;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;

/**
 * Zombies sense players anywhere within their follow range (128 blocks by default), through walls,
 * floors and caves alike, and never lose them for being out of sight. Runs ahead of vanilla's
 * "must see the player" goal, which it replaces while a player is in range.
 */
public final class ZombieSenseGoal extends NearestAttackableTargetGoal<Player> {
    public ZombieSenseGoal(Mob mob) {
        super(mob, Player.class, 10, false, false, null);
        this.targetConditions.ignoreLineOfSight();
    }
}
