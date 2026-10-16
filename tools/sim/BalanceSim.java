import com.jo112ss.rpgmod.smithing.core.*;

import java.util.*;
import java.util.random.RandomGenerator;

/**
 * Balance simulation: plays thousands of forgings with bots of different skill.
 * Timing error is modelled in milliseconds, so faster (colder) bars really are harder.
 * Run: javac -d out $(find src/main/java/com/jo112ss/rpgmod/smithing/core -name '*.java') tools/sim/BalanceSim.java && java -cp out BalanceSim
 */
public class BalanceSim {
    record Skill(String name, double sigmaMs, boolean smart) {}

    public static void main(String[] args) {
        SmithingRules rules = new SmithingRules();
        int n = args.length > 0 ? Integer.parseInt(args[0]) : 4000;
        Skill[] skills = {
            new Skill("expert  (30ms, smart)", 30, true),
            new Skill("good    (55ms, smart)", 55, true),
            new Skill("average (80ms, smart)", 80, true),
            new Skill("beginner(120ms,naive)", 120, false),
            new Skill("novice  (170ms,naive)", 170, false),
        };
        Random seedRng = new Random(1);
        // reachability check: replaying perfectly-informed plans
        System.out.println("rules: power " + rules.powerCold + ".." + rules.powerHot + ", loss " + rules.tempLossPerStrike + ", cool " + rules.coolAmount);
        double sumT = 0, minT = 1e9, maxT = 0;
        for (int i = 0; i < 2000; i++) {
            double[] t = TargetGenerator.generate(rules, TargetProfile.SWORD, new Random(i));
            double s = Arrays.stream(t).sum();
            sumT += s; minT = Math.min(minT, s); maxT = Math.max(maxT, s);
        }
        System.out.printf("target totals: avg %.1f min %.1f max %.1f%n", sumT / 2000, minT, maxT);
        for (Skill sk : skills) {
            int[] q = new int[4];
            double errSum = 0;
            int strikesSum = 0;
            for (int i = 0; i < n; i++) {
                long seed = seedRng.nextLong();
                Random r = new Random(seed);
                double[] t = TargetGenerator.generate(rules, TargetProfile.SWORD, r);
                ForgeState s = play(rules, t, sk, r);
                q[s.quality().ordinal()]++;
                errSum += s.errorRatio();
                strikesSum += s.strikes();
            }
            System.out.printf("%s  common %5.1f%%  fine %5.1f%%  rare %5.1f%%  legend %5.1f%%   avgErr %.3f  strikes %.1f%n",
                sk.name, 100.0 * q[0] / n, 100.0 * q[1] / n, 100.0 * q[2] / n, 100.0 * q[3] / n, errSum / n, (double) strikesSum / n);
        }
    }

    static Judgement hit(SmithingRules rules, double speed, double sigmaMs, RandomGenerator r) {
        double dt = r.nextGaussian() * sigmaMs / 1000.0;
        double off = 4 * speed * Math.abs(dt);
        double pos = 0.5 + Math.min(1, off) / 2;
        return TimingBar.judge(rules, pos);
    }

    static double expectedMult(SmithingRules rules, double speed, double sigmaMs) {
        // Monte Carlo-free approximation via a fixed sample
        Random r = new Random(42);
        double sum = 0;
        for (int i = 0; i < 200; i++) sum += rules.multiplier(hit(rules, speed, sigmaMs, r));
        return sum / 200;
    }

    static ForgeState play(SmithingRules rules, double[] targets, Skill sk, Random r) {
        ForgeState s = new ForgeState(rules, targets, 0);
        double total = Arrays.stream(targets).sum();
        // choose initial temperature
        double wantTemp;
        if (sk.smart) {
            // estimate how hot we need to be: enough power budget for the total work (~1.4 per strike with neighbours)
            wantTemp = rules.initialTempMax;
            for (double temp = rules.initialTempMin; temp <= rules.initialTempMax; temp += 1) {
                double budget = 0;
                for (double tt = temp; tt > rules.endTemp; tt -= rules.tempLossPerStrike) budget += rules.basePower(tt) * 0.85 * 1.4;
                if (budget >= total * 1.15) { wantTemp = temp; break; }
            }
        } else {
            wantTemp = rules.initialTempMin + r.nextDouble() * (rules.initialTempMax - rules.initialTempMin);
        }
        double wantPos = (wantTemp - rules.initialTempMin) / (rules.initialTempMax - rules.initialTempMin);
        double posErr = r.nextGaussian() * sk.sigmaMs / 1000.0 * rules.heatBarSpeed * 2;
        s.setWorkingTemperature(rules.initialTempFromBar(Math.max(0, Math.min(1, wantPos + posErr))));

        int guard = 0;
        while (s.phase() == ForgeState.Phase.WORKING && guard++ < 60) {
            double speed = rules.strikeBarSpeed(s.temperature());
            if (sk.smart) {
                double em = expectedMult(rules, speed, sk.sigmaMs);
                double bestScore = ForgeState.errorRatio(rules, targets, s.values());
                int bestAction = -1; // -1 finish, -2 cool, else part
                double[] v = s.values();
                for (int p = 0; p < targets.length; p++) {
                    double[] nv = v.clone();
                    double dmg = rules.basePower(s.temperature()) * em;
                    nv[p] += dmg;
                    if (p > 0) nv[p - 1] += dmg * rules.adjacentRatio;
                    if (p < nv.length - 1) nv[p + 1] += dmg * rules.adjacentRatio;
                    double sc = ForgeState.errorRatio(rules, targets, nv);
                    if (sc < bestScore - 1e-9) { bestScore = sc; bestAction = p; }
                }
                if (bestAction == -1) {
                    // would cooling let a smaller strike help?
                    double ct = s.temperature() - rules.coolAmount;
                    if (ct > rules.endTemp) {
                        double em2 = expectedMult(rules, rules.strikeBarSpeed(ct), sk.sigmaMs);
                        for (int p = 0; p < targets.length; p++) {
                            double[] nv = v.clone();
                            double dmg = rules.basePower(ct) * em2;
                            nv[p] += dmg;
                            if (p > 0) nv[p - 1] += dmg * rules.adjacentRatio;
                            if (p < nv.length - 1) nv[p + 1] += dmg * rules.adjacentRatio;
                            double sc = ForgeState.errorRatio(rules, targets, nv);
                            if (sc < bestScore - 1e-9) { bestScore = sc; bestAction = -2; }
                        }
                    }
                }
                if (bestAction == -1) { s.finish(); break; }
                if (bestAction == -2) { s.cool(0); continue; }
                s.applyStrike(bestAction, hit(rules, speed, sk.sigmaMs, r));
            } else {
                double[] v = s.values();
                int best = -1; double deficit = 0.5;
                for (int p = 0; p < targets.length; p++) if (targets[p] - v[p] > deficit) { deficit = targets[p] - v[p]; best = p; }
                if (best < 0) { s.finish(); break; }
                // naive players sometimes pick a neighbour by mistake
                if (r.nextDouble() < 0.15) best = Math.max(0, Math.min(targets.length - 1, best + (r.nextBoolean() ? 1 : -1)));
                s.applyStrike(best, hit(rules, speed, sk.sigmaMs, r));
            }
        }
        if (s.phase() == ForgeState.Phase.WORKING) s.finish();
        return s;
    }
}
