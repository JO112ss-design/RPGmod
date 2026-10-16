import com.jo112ss.rpgmod.smithing.core.*;

import java.util.*;

/** Quality distribution per material difficulty (uses the bots from BalanceSim). */
public class DifficultySim {
    public static void main(String[] args) {
        int n = args.length > 0 ? Integer.parseInt(args[0]) : 1500;
        double[] diffs = args.length > 4 ? new double[] {Double.parseDouble(args[1]), Double.parseDouble(args[2]), Double.parseDouble(args[3]), Double.parseDouble(args[4])} : new double[] {0.85, 1.0, 1.15, 1.3};
        String[] mats = {"copper", "iron", "gold", "diamond"};
        BalanceSim.Skill[] skills = {
            new BalanceSim.Skill("expert  ", 30, true),
            new BalanceSim.Skill("good    ", 55, true),
            new BalanceSim.Skill("average ", 80, true),
            new BalanceSim.Skill("beginner", 120, false),
        };
        Map<String, TargetProfile> profiles = new LinkedHashMap<>();
        profiles.put("SWORD", TargetProfile.SWORD);
        profiles.put("HAMMER", TargetProfile.HAMMER);
        profiles.put("CHESTPLATE", TargetProfile.CHESTPLATE);
        profiles.put("BOOTS", TargetProfile.BOOTS);
        for (var e : profiles.entrySet()) {
            System.out.println("== " + e.getKey());
            for (int mi = 0; mi < mats.length; mi++) {
                SmithingRules rules = new SmithingRules().withDifficulty(diffs[mi]);
                StringBuilder line = new StringBuilder(String.format("  %-8s x%.2f ", mats[mi], diffs[mi]));
                for (BalanceSim.Skill sk : skills) {
                    int[] q = new int[4];
                    Random seed = new Random(7);
                    for (int i = 0; i < n; i++) {
                        Random r = new Random(seed.nextLong());
                        double[] t = TargetGenerator.generate(rules, e.getValue(), r);
                        q[BalanceSim.play(rules, t, sk, r).quality().ordinal()]++;
                    }
                    line.append(String.format("| %s C%3.0f F%3.0f R%3.0f L%3.0f ", sk.name().trim().substring(0, 3), 100.0 * q[0] / n, 100.0 * q[1] / n, 100.0 * q[2] / n, 100.0 * q[3] / n));
                }
                System.out.println(line);
            }
        }
    }
}
