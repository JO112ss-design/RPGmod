package com.jo112ss.rpgmod.smithing.core;

/**
 * Describes how much work each part of a weapon tends to need. Weights bias the reference plan
 * used to generate reachable targets. Index 0 is the tip; the last index is the base.
 */
public record TargetProfile(double[] partWeights) {
    public int parts() {
        return partWeights.length;
    }

    /**
     * True if the targets follow the profile's shape: wherever one part's weight is clearly larger
     * (30%+) than another's, its target must not be smaller. Used to make "middle heavy, ends light"
     * the usual outcome while still allowing variation.
     */
    public boolean matchesShape(double[] targets) {
        for (int i = 0; i < partWeights.length; i++) {
            for (int j = 0; j < partWeights.length; j++) {
                if (partWeights[i] > partWeights[j] * 1.3 && targets[i] < targets[j]) return false;
            }
        }
        return true;
    }

    /** Swords: tip and base need less shaping, the middle of the blade needs the most. */
    public static final TargetProfile SWORD = new TargetProfile(new double[] {0.45, 1.0, 1.7, 1.0, 0.55});
}
