package com.jo112ss.rpgmod.smithing.core;

import java.util.random.RandomGenerator;

/**
 * Creates target values that are guaranteed to be reachable: it plays a random "reference forging"
 * with the real rules (a plausible start temperature, Perfect/Good strikes, occasional cooling) and
 * uses the result as the targets. Reproducing that plan reaches the targets exactly, so no target can
 * be physically impossible for the temperature and number of strikes available.
 */
public final class TargetGenerator {
    private TargetGenerator() {}

    public static double[] generate(SmithingRules rules, TargetProfile profile, RandomGenerator random) {
        double[] best = null;
        for (int attempt = 0; attempt < 64; attempt++) {
            double[] t = plan(rules, profile, random);
            if (t == null) continue;
            best = t;
            boolean ok = true;
            for (double v : t) if (v < rules.targetMinPerPart) { ok = false; break; }
            if (ok) return t;
        }
        if (best == null) {
            best = new double[profile.parts()];
            java.util.Arrays.fill(best, rules.targetMinPerPart);
        }
        return best;
    }

    private static double[] plan(SmithingRules rules, TargetProfile profile, RandomGenerator random) {
        ForgeState s = new ForgeState(rules, new double[profile.parts()], 0);
        double startTemp = rules.targetPlanTempMin + random.nextDouble() * (rules.targetPlanTempMax - rules.targetPlanTempMin);
        startTemp = Math.min(startTemp, rules.initialTempMax);
        s.setWorkingTemperature(startTemp);
        int maxStrikes = (int) Math.floor((startTemp - rules.endTemp) / rules.tempLossPerStrike);
        if (maxStrikes < rules.targetPlanMinStrikes) return null;
        // leave at least one strike of slack so the player has room to correct
        int strikes = rules.targetPlanMinStrikes + random.nextInt(Math.max(1, maxStrikes - rules.targetPlanMinStrikes));
        int done = 0;
        while (done < strikes && s.phase() == ForgeState.Phase.WORKING) {
            if (done >= 2 && random.nextDouble() < 0.12
                    && s.temperature() - rules.coolAmount - rules.tempLossPerStrike > rules.endTemp) {
                s.cool(0);
                continue;
            }
            int part = pickPart(profile, random);
            Judgement j = random.nextDouble() < 0.55 ? Judgement.PERFECT : Judgement.GOOD;
            s.applyStrike(part, j);
            done++;
        }
        double[] v = s.values();
        for (int i = 0; i < v.length; i++) v[i] = Math.round(v[i]);
        return v;
    }

    private static int pickPart(TargetProfile profile, RandomGenerator random) {
        double sum = 0;
        for (double w : profile.partWeights()) sum += w;
        double r = random.nextDouble() * sum;
        for (int i = 0; i < profile.parts(); i++) {
            r -= profile.partWeights()[i];
            if (r <= 0) return i;
        }
        return profile.parts() - 1;
    }
}
