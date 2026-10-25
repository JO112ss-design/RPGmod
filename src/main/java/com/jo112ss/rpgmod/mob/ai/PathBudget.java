package com.jo112ss.rpgmod.mob.ai;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;

/**
 * Keeps monster pathfinding cheap. With a 128-block follow range every path search may look at
 * 2048 nodes, and a target behind walls (unreachable) uses all of them every time - dozens of
 * monsters doing that several times a second made the server fall behind. So for monsters:
 *
 * <ul>
 *   <li>a search looks at about 200-700 nodes depending on how far the goal is (a far goal gets a
 *       partial path towards it, renewed as the monster walks);</li>
 *   <li>a new path is only searched every few ticks: 5 close up, up to 2 seconds far away, longer
 *       after a search that could not reach the goal. In between the monster keeps its current path.</li>
 * </ul>
 */
public final class PathBudget {
    private PathBudget() {}

    /** Set while our own goals ask for a path they need right now (cave exits). */
    public static boolean bypass;

    public static boolean applies(Mob mob) {
        return !bypass && mob instanceof Enemy && !mob.level().isClientSide();
    }

    /** Nodes a search may visit for a goal this far away. */
    public static int nodes(double dist) {
        return (int) Math.max(200, Math.min(700, 150 + dist * 12));
    }

    /** Multiplier for the navigation's own node limit (follow range x 16) to get {@link #nodes}. */
    public static float multiplier(Mob mob, double dist) {
        double base = Math.max(16, mob.getAttributeValue(Attributes.FOLLOW_RANGE)) * 16;
        return (float) Math.min(1.0, nodes(dist) / base);
    }

    /** Ticks before the next search may run. */
    public static int interval(double dist, boolean reached) {
        int t = dist < 6 ? 5 : dist < 16 ? 10 : dist < 40 ? 20 : 40;
        return reached ? t : t + 20;
    }
}
