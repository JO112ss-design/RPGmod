package com.jo112ss.rpgmod.job.core;

/** Experience needed per level. Kill experience is roughly the victim's max health. */
public final class LevelCurve {
    private LevelCurve() {}

    public static final int DEFAULT_MAX_LEVEL = 100;

    /**
     * Experience to go from {@code level} to {@code level + 1}. Twice as many levels as the old
     * curve (30 + 10L + L^2, cap 50) at half the cost each, so reaching 100 takes about what 50 did.
     */
    public static long expToNext(int level) {
        return Math.round(15 + 2.5 * level + level * (double) level / 8.0);
    }

    /** Skill points earned by a level: one at level 1, then one per level. */
    public static int pointsForLevel(int level) {
        return Math.max(1, level);
    }
}
