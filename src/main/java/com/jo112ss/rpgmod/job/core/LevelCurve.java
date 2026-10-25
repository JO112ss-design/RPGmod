package com.jo112ss.rpgmod.job.core;

/** Experience needed per level. Kill experience is roughly the victim's max health. */
public final class LevelCurve {
    private LevelCurve() {}

    /** Top level of a base job; reaching it promotes the character to the advanced job. */
    public static final int DEFAULT_MAX_LEVEL = 100;
    /** Level at which the advanced job begins (advanced level 1 = this level). */
    public static final int ADVANCED_AT = 100;
    /** Top level with the advanced job: advanced level 100. */
    public static final int MAX_TOTAL_LEVEL = 199;

    /** Advanced job level (1-100) for a total level, 0 below the advanced job. */
    public static int advancedLevel(int level) {
        return level >= ADVANCED_AT ? level - ADVANCED_AT + 1 : 0;
    }

    /**
     * Experience to go from {@code level} to {@code level + 1}. Twice as many levels as the old
     * curve (30 + 10L + L^2, cap 50) at half the cost each, so reaching 100 takes about what 50 did.
     */
    public static long expToNext(int level) {
        double base = 15 + 2.5 * level + level * (double) level / 8.0;
        return Math.round(base * earlyMultiplier(level) * SCALE * stageMultiplier(level));
    }

    /**
     * Levels up to 100 cost half (the base job goes by quicker), the advanced job's 0.9 times: with
     * monsters dying in about as many hits at every level, 100 -> 199 takes about four times 1 -> 100.
     */
    public static double stageMultiplier(int level) {
        return level < ADVANCED_AT ? 0.5 : 0.9;
    }

    /** Every level costs this much more than the curve above (a longer climb to 100). */
    public static final double SCALE = 2.5;

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

    /**
     * Job mastery that counts as 100%: all the experience from level 1 to 100. A character's first
     * job is mastered the moment it reaches level 100, and since mastery only grows with experience
     * earned (doubled only from level 100 on), a mastered job always means level 100 or more.
     */
    public static long masteryExp() {
        long m = masteryCache;
        if (m == 0) {
            for (int l = 1; l < ADVANCED_AT; l++) m += expToNext(l);
            masteryCache = m;
        }
        return m;
    }

    private static volatile long masteryCache;

    /** Mastery grows twice as fast once the character is level 100 or more. */
    public static int masteryRate(int level) {
        return level >= ADVANCED_AT ? 2 : 1;
    }

    /** Skill points earned by a level: one at level 1, then one per level. */
    public static int pointsForLevel(int level) {
        return Math.max(1, level);
    }
}
