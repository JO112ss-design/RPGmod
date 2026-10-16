package com.jo112ss.rpgmod.config;

import com.jo112ss.rpgmod.smithing.core.SmithingRules;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * World config (saved per world in serverconfig/, synced to clients). Every number of the
 * smithing minigame can be tuned here without rebuilding the mod.
 */
public final class SmithingConfig {
    private SmithingConfig() {}

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    private static final SmithingRules D = new SmithingRules();

    static { B.comment("Temperature (internal range 0-100)").push("temperature"); }
    public static final ModConfigSpec.DoubleValue INITIAL_TEMP_MIN = B.comment("Starting heat when the heating bar is stopped at the far left").defineInRange("initialTempMin", D.initialTempMin, 0, 100);
    public static final ModConfigSpec.DoubleValue INITIAL_TEMP_MAX = B.comment("Starting heat when the heating bar is stopped at the far right").defineInRange("initialTempMax", D.initialTempMax, 0, 100);
    public static final ModConfigSpec.DoubleValue TEMP_LOSS_PER_STRIKE = B.comment("Heat lost per strike (Miss included)").defineInRange("tempLossPerStrike", D.tempLossPerStrike, 0.1, 100);
    public static final ModConfigSpec.DoubleValue COOL_AMOUNT = B.comment("Heat lost by the quench (cooling) action").defineInRange("coolAmount", D.coolAmount, 0.1, 100);
    public static final ModConfigSpec.DoubleValue END_TEMP = B.comment("Forging ends when heat is at or below this").defineInRange("endTemp", D.endTemp, 0, 99);
    static { B.pop().comment("Bar speeds in full back-and-forth cycles per second").push("bars"); }
    public static final ModConfigSpec.DoubleValue HEAT_BAR_SPEED = B.comment("Heating (starting heat) bar speed").defineInRange("heatBarSpeed", D.heatBarSpeed, 0.05, 10);
    public static final ModConfigSpec.DoubleValue STRIKE_BAR_SPEED_HOT = B.comment("Strike bar speed at heat 100").defineInRange("strikeBarSpeedHot", D.strikeBarSpeedHot, 0.05, 10);
    public static final ModConfigSpec.DoubleValue STRIKE_BAR_SPEED_COLD = B.comment("Strike bar speed at the end temperature").defineInRange("strikeBarSpeedCold", D.strikeBarSpeedCold, 0.05, 10);
    static { B.pop().comment("Strike power").push("power"); }
    public static final ModConfigSpec.DoubleValue POWER_COLD = B.comment("Base strike power at the end temperature").defineInRange("powerCold", D.powerCold, 0, 1000);
    public static final ModConfigSpec.DoubleValue POWER_HOT = B.comment("Base strike power at heat 100").defineInRange("powerHot", D.powerHot, 0, 1000);
    public static final ModConfigSpec.DoubleValue ADJACENT_RATIO = B.comment("Share of a strike that also hits each neighbouring part").defineInRange("adjacentRatio", D.adjacentRatio, 0, 1);
    static { B.pop().comment("Strike power zones (perfect = strong / centre, good = medium, bad = weak / outer, miss = outside). Zones are half-widths as a fraction of half the bar (0 = centre only, 1 = whole bar)").push("judgement"); }
    public static final ModConfigSpec.DoubleValue PERFECT_ZONE = B.defineInRange("perfectZone", D.perfectZone, 0, 1);
    public static final ModConfigSpec.DoubleValue GOOD_ZONE = B.defineInRange("goodZone", D.goodZone, 0, 1);
    public static final ModConfigSpec.DoubleValue BAD_ZONE = B.defineInRange("badZone", D.badZone, 0, 1);
    public static final ModConfigSpec.DoubleValue PERFECT_MULT = B.defineInRange("perfectMultiplier", D.perfectMultiplier, 0, 10);
    public static final ModConfigSpec.DoubleValue GOOD_MULT = B.defineInRange("goodMultiplier", D.goodMultiplier, 0, 10);
    public static final ModConfigSpec.DoubleValue BAD_MULT = B.defineInRange("badMultiplier", D.badMultiplier, 0, 10);
    public static final ModConfigSpec.DoubleValue MISS_MULT = B.defineInRange("missMultiplier", D.missMultiplier, 0, 10);
    static { B.pop().comment("Quality. Error ratio = weighted error / total target").push("quality"); }
    public static final ModConfigSpec.DoubleValue UNDER_PENALTY = B.comment("Penalty factor for work below the target").defineInRange("underPenalty", D.underPenalty, 0, 10);
    public static final ModConfigSpec.DoubleValue OVER_PENALTY = B.comment("Penalty factor for work above the target").defineInRange("overPenalty", D.overPenalty, 0, 10);
    public static final ModConfigSpec.DoubleValue LEGENDARY_MAX = B.defineInRange("legendaryMaxError", D.legendaryMaxError, 0, 10);
    public static final ModConfigSpec.DoubleValue RARE_MAX = B.defineInRange("rareMaxError", D.rareMaxError, 0, 10);
    public static final ModConfigSpec.DoubleValue FINE_MAX = B.defineInRange("fineMaxError", D.fineMaxError, 0, 10);
    static { B.pop().comment("Target generation (targets come from a simulated forging, so they are always reachable)").push("targets"); }
    public static final ModConfigSpec.DoubleValue PLAN_TEMP_MIN = B.defineInRange("planTempMin", D.targetPlanTempMin, 0, 100);
    public static final ModConfigSpec.DoubleValue PLAN_TEMP_MAX = B.defineInRange("planTempMax", D.targetPlanTempMax, 0, 100);
    public static final ModConfigSpec.IntValue PLAN_MIN_STRIKES = B.defineInRange("planMinStrikes", D.targetPlanMinStrikes, 1, 50);
    public static final ModConfigSpec.IntValue MIN_PER_PART = B.defineInRange("minTargetPerPart", D.targetMinPerPart, 0, 1000);
    static { B.pop().comment("Anti-cheat timing").push("timing"); }
    public static final ModConfigSpec.LongValue MIN_STOP_MILLIS = B.comment("Fastest accepted bar stop in ms").defineInRange("minStopMillis", D.minStopMillis, 0L, 5000L);
    public static final ModConfigSpec.LongValue TOLERANCE_MILLIS = B.comment("Max accepted difference between client and server bar time in ms").defineInRange("toleranceMillis", D.timingToleranceMillis, 0L, 10000L);
    static { B.pop(); }

    /** Bump when the default balance changes; older files are reset to the new defaults once. */
    private static final int CURRENT_VERSION = 2;
    public static final ModConfigSpec.IntValue CONFIG_VERSION = B
            .comment("Balance version of this file. When the mod's default balance changes, an older file is reset to the new defaults.")
            .defineInRange("configVersion", 0, 0, Integer.MAX_VALUE); // missing in old files -> 0 -> migrate

    public static final ModConfigSpec SPEC = B.build();

    private static final java.util.List<ModConfigSpec.ConfigValue<?>> ALL = java.util.List.of(
            INITIAL_TEMP_MIN, INITIAL_TEMP_MAX, TEMP_LOSS_PER_STRIKE, COOL_AMOUNT, END_TEMP,
            HEAT_BAR_SPEED, STRIKE_BAR_SPEED_HOT, STRIKE_BAR_SPEED_COLD, POWER_COLD, POWER_HOT, ADJACENT_RATIO,
            PERFECT_ZONE, GOOD_ZONE, BAD_ZONE, PERFECT_MULT, GOOD_MULT, BAD_MULT, MISS_MULT,
            UNDER_PENALTY, OVER_PENALTY, LEGENDARY_MAX, RARE_MAX, FINE_MAX,
            PLAN_TEMP_MIN, PLAN_TEMP_MAX, PLAN_MIN_STRIKES, MIN_PER_PART, MIN_STOP_MILLIS, TOLERANCE_MILLIS);

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void migrateIfOld() {
        if (CONFIG_VERSION.get() >= CURRENT_VERSION) return;
        try {
            for (ModConfigSpec.ConfigValue v : ALL) v.set(v.getDefault());
            CONFIG_VERSION.set(CURRENT_VERSION);
            SPEC.save();
            com.jo112ss.rpgmod.RpgMod.LOGGER.info("rpgmod smithing config reset to the new default balance (v{})", CURRENT_VERSION);
        } catch (RuntimeException e) {
            // e.g. a read-only synced copy on a client; the server's values are used there anyway
            com.jo112ss.rpgmod.RpgMod.LOGGER.debug("Could not migrate smithing config", e);
        }
    }

    /** Copies the config into the live rules. Called on load and reload. */
    public static void apply() {
        migrateIfOld();
        SmithingRules r = new SmithingRules();
        r.initialTempMin = Math.min(INITIAL_TEMP_MIN.get(), INITIAL_TEMP_MAX.get());
        r.initialTempMax = Math.max(INITIAL_TEMP_MIN.get(), INITIAL_TEMP_MAX.get());
        r.tempLossPerStrike = TEMP_LOSS_PER_STRIKE.get();
        r.coolAmount = COOL_AMOUNT.get();
        r.endTemp = Math.min(END_TEMP.get(), r.initialTempMax - 1);
        r.heatBarSpeed = HEAT_BAR_SPEED.get();
        r.strikeBarSpeedHot = STRIKE_BAR_SPEED_HOT.get();
        r.strikeBarSpeedCold = STRIKE_BAR_SPEED_COLD.get();
        r.powerCold = POWER_COLD.get();
        r.powerHot = POWER_HOT.get();
        r.adjacentRatio = ADJACENT_RATIO.get();
        r.perfectZone = PERFECT_ZONE.get();
        r.goodZone = Math.max(GOOD_ZONE.get(), r.perfectZone);
        r.badZone = Math.max(BAD_ZONE.get(), r.goodZone);
        r.perfectMultiplier = PERFECT_MULT.get();
        r.goodMultiplier = GOOD_MULT.get();
        r.badMultiplier = BAD_MULT.get();
        r.missMultiplier = MISS_MULT.get();
        r.underPenalty = UNDER_PENALTY.get();
        r.overPenalty = OVER_PENALTY.get();
        r.legendaryMaxError = LEGENDARY_MAX.get();
        r.rareMaxError = Math.max(RARE_MAX.get(), r.legendaryMaxError);
        r.fineMaxError = Math.max(FINE_MAX.get(), r.rareMaxError);
        r.targetPlanTempMin = Math.min(PLAN_TEMP_MIN.get(), PLAN_TEMP_MAX.get());
        r.targetPlanTempMax = Math.max(PLAN_TEMP_MIN.get(), PLAN_TEMP_MAX.get());
        r.targetPlanMinStrikes = PLAN_MIN_STRIKES.get();
        r.targetMinPerPart = MIN_PER_PART.get();
        r.minStopMillis = MIN_STOP_MILLIS.get();
        r.timingToleranceMillis = TOLERANCE_MILLIS.get();
        SmithingRules.set(r);
    }
}
