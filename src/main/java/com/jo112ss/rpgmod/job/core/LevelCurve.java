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
        double base = 15 + 2.5 * level + level * (double) level / 8.0;
        return Math.round(base * earlyMultiplier(level));
    }

    /** Levels below this cost extra (more at the start), joining the plain curve here. */
    public static final int EARLY_UNTIL = 40;

    /**
     * Early levels cost up to 3x (level 1), easing back to 1x at level 40: about 2.5 monsters per
     * level at the very start, rising to ~6 by level 20 and staying there instead of 1 -> 6.5.
     */
    static double earlyMultiplier(int level) {
        if (level >= EARLY_UNTIL) return 1;
        return 1 + 2.0 * Math.pow(1 - level / (double) EARLY_UNTIL, 1.5);
    }

    /** Skill points earned by a level: one at level 1, then one per level. */
    public static int pointsForLevel(int level) {
        return Math.max(1, level);
    }
}
