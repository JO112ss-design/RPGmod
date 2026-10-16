package com.jo112ss.rpgmod.smithing.core;

/**
 * A marker that sweeps left-right across a bar. Position is a pure function of elapsed time, so the
 * client can animate it and the server can recompute exactly where it was stopped.
 */
public final class TimingBar {
    private TimingBar() {}

    /** Position in [0,1]; starts at 0, reaches 1 after half a cycle, then returns. */
    public static double position(double cyclesPerSecond, long elapsedMillis) {
        double phase = (elapsedMillis / 1000.0) * cyclesPerSecond;
        phase -= Math.floor(phase);
        return phase < 0.5 ? phase * 2 : (1 - phase) * 2;
    }

    /** Distance from the centre, 0 at the centre and 1 at either end. */
    public static double offCenter(double pos) {
        return Math.abs(pos - 0.5) * 2;
    }

    public static Judgement judge(SmithingRules rules, double pos) {
        double d = offCenter(pos);
        if (d <= rules.perfectZone) return Judgement.PERFECT;
        if (d <= rules.goodZone) return Judgement.GOOD;
        if (d <= rules.badZone) return Judgement.BAD;
        return Judgement.MISS;
    }
}
