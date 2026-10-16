package com.jo112ss.rpgmod.smithing.core;

/** Forging quality. Determines only the number of random options (no flat stat bonus). */
public enum Quality {
    COMMON("common", 1, 0xAAAAAA),
    FINE("fine", 2, 0x5599FF),
    RARE("rare", 3, 0xB25AFF),
    LEGENDARY("legendary", 4, 0xFFAA00);

    public final String id;
    public final int optionCount;
    public final int color;

    Quality(String id, int optionCount, int color) {
        this.id = id;
        this.optionCount = optionCount;
        this.color = color;
    }

    public static Quality byOrdinal(int i) {
        Quality[] v = values();
        return v[Math.max(0, Math.min(v.length - 1, i))];
    }

    public static Quality fromError(SmithingRules rules, double errorRatio) {
        if (errorRatio <= rules.legendaryMaxError) return LEGENDARY;
        if (errorRatio <= rules.rareMaxError) return RARE;
        if (errorRatio <= rules.fineMaxError) return FINE;
        return COMMON;
    }
}
