package com.jo112ss.rpgmod.smithing.core;

/**
 * Describes how much work each part of a weapon tends to need. Weights bias the reference plan
 * used to generate reachable targets. Index 0 is the tip; the last index is the base.
 */
public record TargetProfile(double[] partWeights) {
    public int parts() {
        return partWeights.length;
    }

    /** Swords: tip and base need less shaping, the middle of the blade needs the most. */
    public static final TargetProfile SWORD = new TargetProfile(new double[] {0.7, 1.1, 1.3, 1.1, 0.8});
}
