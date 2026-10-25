package com.jo112ss.rpgmod.mob.core;

/**
 * Enemy level rules, free of Minecraft classes so they can be tested and simulated on their own.
 *
 * <p>The level comes from where a monster appears: in the overworld it grows with the horizontal
 * distance from the world spawn (and a little with depth), the nether starts higher and counts its
 * distance eight times (like portal travel), and the end is the hardest, rising towards the outer
 * islands. Stats grow with level along gentle curves; explosions grow slower than other damage so a
 * single creeper never becomes an unavoidable one-shot.
 */
public final class MobLevelMath {
    private MobLevelMath() {}

    public enum Realm { OVERWORLD, NETHER, END }

    /** Tunables (the config fills these in; the defaults are the designed balance). */
    public static final class Rules {
        public double overworldBlocksPerLevel = 100;
        public int overworldMax = 40;
        public int depthBonusMax = 5;
        public double depthBlocksPerLevel = 12;
        public int netherMin = 35;
        public int netherMax = 60;
        /** Overworld-equivalent blocks (nether distance x8) per level in the nether. */
        public double netherBlocksPerLevel = 250;
        public int endMin = 70;
        public int endMax = 85;
        public double endOuterStart = 800;
        public double endBlocksPerLevel = 400;
        public int variance = 2;

        public double hpLinear = 0.07, hpSquare = 0.00007;
        /** Unchanged at level 1, about x2.8 at level 110 (half of the original x5.6 at the top). */
        public double damageLinear = 0.105, damageSquare = 0.00007;
        /** Share of the damage growth that explosions get. */
        public double explosionShare = 0.25;
        /** Off by default: every monster gets the flat speed multiplier instead (config speedMultiplier). */
        public double speedPerLevel = 0.0025, speedCap = 0;
        public double knockbackPerLevel = 0.006, knockbackCap = 0.5;
    }

    /**
     * Base level of a place, before the random spread.
     *
     * @param dist horizontal distance from the realm's centre (world spawn; nether: spawn / 8; end: 0,0)
     * @param y    height, for the overworld depth bonus
     */
    public static int baseLevel(Rules r, Realm realm, double dist, double y) {
        return switch (realm) {
            case OVERWORLD -> {
                int depth = y < 0 ? (int) Math.min(r.depthBonusMax, Math.floor(-y / r.depthBlocksPerLevel)) : 0;
                yield clamp(1 + (int) Math.floor(dist / r.overworldBlocksPerLevel) + depth, 1, r.overworldMax);
            }
            case NETHER -> clamp(r.netherMin + (int) Math.floor(dist * 8 / r.netherBlocksPerLevel), r.netherMin, r.netherMax);
            case END -> clamp(r.endMin + (int) Math.floor(Math.max(0, dist - r.endOuterStart) / r.endBlocksPerLevel), r.endMin, r.endMax);
        };
    }

    /** Level relative to the strongest player: top + min .. top + max ({@code roll} in [0,1)). */
    public static int playerRelative(int topPlayerLevel, int min, int max, double roll) {
        int spread = Math.max(0, max - min);
        return Math.max(1, topPlayerLevel + min + Math.min(spread, (int) Math.floor(roll * (spread + 1))));
    }

    /** Level with a small random spread ({@code roll} in [0,1)); never leaves the realm's range. */
    public static int level(Rules r, Realm realm, double dist, double y, double roll) {
        int base = baseLevel(r, realm, dist, y);
        int spread = (int) Math.floor(roll * (2 * r.variance + 1)) - r.variance;
        int min = switch (realm) {
            case OVERWORLD -> 1;
            case NETHER -> r.netherMin;
            case END -> r.endMin;
        };
        int max = switch (realm) {
            case OVERWORLD -> r.overworldMax;
            case NETHER -> r.netherMax;
            case END -> r.endMax;
        };
        // no spread right at the start so the area around spawn stays gentle
        if (realm == Realm.OVERWORLD && base <= 2) spread = Math.max(0, spread);
        return clamp(base + spread, min, max);
    }

    public static double healthMultiplier(Rules r, int level) {
        int k = Math.max(0, level - 1);
        return 1 + r.hpLinear * k + r.hpSquare * k * k;
    }

    public static double damageMultiplier(Rules r, int level) {
        int k = Math.max(0, level - 1);
        return 1 + r.damageLinear * k + r.damageSquare * k * k;
    }

    public static double explosionMultiplier(Rules r, int level) {
        return 1 + (damageMultiplier(r, level) - 1) * r.explosionShare;
    }

    /** Extra movement speed as a fraction (0.1 = +10%). */
    public static double speedBonus(Rules r, int level) {
        return Math.min(r.speedCap, r.speedPerLevel * Math.max(0, level - 1));
    }

    public static double knockbackResistance(Rules r, int level) {
        return Math.min(r.knockbackCap, r.knockbackPerLevel * Math.max(0, level - 1));
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
