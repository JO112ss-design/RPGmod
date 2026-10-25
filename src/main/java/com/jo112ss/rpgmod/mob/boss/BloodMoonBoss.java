package com.jo112ss.rpgmod.mob.boss;

import net.minecraft.world.entity.Entity;

/** The blood moon bosses (King Slime, Golem): shared rules for damage caps, health and rewards. */
public interface BloodMoonBoss {
    /** Damage taken is divided by this past the vanilla max health cap (1 below it). */
    float toughness();

    /** Max health from the boss level and the players online; {@code fill} = start at full. */
    void recomputeHealth(boolean fill);

    static boolean is(Entity e) {
        return e instanceof BloodMoonBoss;
    }
}
