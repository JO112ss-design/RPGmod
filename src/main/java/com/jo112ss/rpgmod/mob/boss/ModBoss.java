package com.jo112ss.rpgmod.mob.boss;

import net.minecraft.world.entity.Entity;

/**
 * The mod's bosses (King Slime, Golem, Lich King, Spider Queen). They live in dungeons (and come
 * from spawn eggs for testing); their shared rules - health, the damage cap, the boss bar, keeping
 * out of water, rewards - are in their {@link BossCore}.
 */
public interface ModBoss {
    BossCore core();

    /** Damage taken is divided by this past the vanilla max health cap (1 below it). */
    default float toughness() {
        return core().toughness();
    }

    /** Max health from the boss level and the players fighting; {@code fill} = start at full. */
    default void recomputeHealth(boolean fill) {
        core().recomputeHealth(fill);
    }

    static boolean is(Entity e) {
        return e instanceof ModBoss;
    }
}
