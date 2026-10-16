package com.jo112ss.rpgmod.smithing.core;

/**
 * All tunable numbers of the smithing minigame. Pure Java so it can be unit tested and simulated
 * outside Minecraft. The live values are replaced from the server config on load ({@link #current()}).
 */
public final class SmithingRules {
    private static volatile SmithingRules current = new SmithingRules();

    public static SmithingRules current() {
        return current;
    }

    public static void set(SmithingRules rules) {
        current = rules;
    }

    // ---- temperature (internal range 0..100) ----
    /** Lowest temperature the heating bar can produce (bar fully left). */
    public double initialTempMin = 45;
    /** Highest temperature the heating bar can produce (bar fully right). */
    public double initialTempMax = 100;
    /** Temperature lost per strike (Miss included). */
    public double tempLossPerStrike = 8;
    /** Temperature lost by the cooling action. */
    public double coolAmount = 15;
    /** Forging ends when the temperature is at or below this value. */
    public double endTemp = 20;

    // ---- bar speeds (full back-and-forth cycles per second) ----
    /** Heating (initial temperature) bar speed. Independent from the strike bar. */
    public double heatBarSpeed = 0.75;
    /** Strike bar speed at the hottest temperature. */
    public double strikeBarSpeedHot = 0.55;
    /** Strike bar speed at the end temperature (coldest). */
    public double strikeBarSpeedCold = 1.45;

    // ---- strike power ----
    /** Base power at the end temperature. */
    public double powerCold = 3;
    /** Base power at temperature 100. */
    public double powerHot = 18;

    // ---- judgement zones: half-width of each zone as a fraction of half the bar ----
    public double perfectZone = 0.08;
    public double goodZone = 0.22;
    public double badZone = 0.45;
    public double perfectMultiplier = 1.0;
    public double goodMultiplier = 0.75;
    public double badMultiplier = 0.40;
    public double missMultiplier = 0.0;

    /** Share of the strike damage that also lands on each neighbouring part. */
    public double adjacentRatio = 0.25;

    // ---- quality ----
    /** Penalty factor for missing work (target not reached). */
    public double underPenalty = 1.0;
    /** Penalty factor for overworking (target exceeded); heavier than under. */
    public double overPenalty = 1.5;
    /** Error ratio (weighted error / total target) at or below which the result is Legendary. */
    public double legendaryMaxError = 0.075;
    public double rareMaxError = 0.15;
    public double fineMaxError = 0.28;

    // ---- target generation ----
    /** Reference plans used to create reachable targets start between these temperatures. */
    public double targetPlanTempMin = 72;
    public double targetPlanTempMax = 92;
    /** Minimum strikes in a reference plan. */
    public int targetPlanMinStrikes = 5;
    /** Lowest acceptable target for any part. */
    public int targetMinPerPart = 3;

    // ---- anti-cheat timing ----
    /** Fastest accepted reaction (ms) between a bar starting and stopping it. */
    public long minStopMillis = 120;
    /** Allowed difference between client-reported elapsed time and server-measured elapsed time. */
    public long timingToleranceMillis = 600;

    public double clampTemp(double t) {
        return Math.max(0, Math.min(100, t));
    }

    /** 0 at the end temperature, 1 at temperature 100. */
    public double heatFactor(double temp) {
        double f = (temp - endTemp) / (100 - endTemp);
        return Math.max(0, Math.min(1, f));
    }

    public double basePower(double temp) {
        return powerCold + (powerHot - powerCold) * heatFactor(temp);
    }

    public double strikeBarSpeed(double temp) {
        return strikeBarSpeedCold + (strikeBarSpeedHot - strikeBarSpeedCold) * heatFactor(temp);
    }

    public double initialTempFromBar(double barPos) {
        return initialTempMin + (initialTempMax - initialTempMin) * Math.max(0, Math.min(1, barPos));
    }

    public double multiplier(Judgement j) {
        return switch (j) {
            case PERFECT -> perfectMultiplier;
            case GOOD -> goodMultiplier;
            case BAD -> badMultiplier;
            case MISS -> missMultiplier;
        };
    }

    public SmithingRules copy() {
        SmithingRules r = new SmithingRules();
        r.initialTempMin = initialTempMin;
        r.initialTempMax = initialTempMax;
        r.tempLossPerStrike = tempLossPerStrike;
        r.coolAmount = coolAmount;
        r.endTemp = endTemp;
        r.heatBarSpeed = heatBarSpeed;
        r.strikeBarSpeedHot = strikeBarSpeedHot;
        r.strikeBarSpeedCold = strikeBarSpeedCold;
        r.powerCold = powerCold;
        r.powerHot = powerHot;
        r.perfectZone = perfectZone;
        r.goodZone = goodZone;
        r.badZone = badZone;
        r.perfectMultiplier = perfectMultiplier;
        r.goodMultiplier = goodMultiplier;
        r.badMultiplier = badMultiplier;
        r.missMultiplier = missMultiplier;
        r.adjacentRatio = adjacentRatio;
        r.underPenalty = underPenalty;
        r.overPenalty = overPenalty;
        r.legendaryMaxError = legendaryMaxError;
        r.rareMaxError = rareMaxError;
        r.fineMaxError = fineMaxError;
        r.targetPlanTempMin = targetPlanTempMin;
        r.targetPlanTempMax = targetPlanTempMax;
        r.targetPlanMinStrikes = targetPlanMinStrikes;
        r.targetMinPerPart = targetMinPerPart;
        r.minStopMillis = minStopMillis;
        r.timingToleranceMillis = timingToleranceMillis;
        return r;
    }
}
