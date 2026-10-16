package com.jo112ss.rpgmod.job.core;

/** Experience needed per level. Kill experience is roughly the victim's max health. */
public final class LevelCurve {
    private LevelCurve() {}

    public static final int DEFAULT_MAX_LEVEL = 50;

    /** Experience to go from {@code level} to {@code level + 1}. */
    public static long expToNext(int level) {
        return 30L + 10L * level + (long) level * level;
    }

    /** Skill points earned by a level: one at level 1, then one per level. */
    public static int pointsForLevel(int level) {
        return Math.max(1, level);
    }
}
