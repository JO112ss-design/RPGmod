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

    /** Sword: tip / fore / middle / rear / base. Middle of the blade needs the most work. */
    public static final TargetProfile SWORD = new TargetProfile(new double[] {0.45, 1.0, 1.7, 1.0, 0.55});
    /** Dagger: tip / blade / base. */
    public static final TargetProfile DAGGER = new TargetProfile(new double[] {0.6, 1.5, 0.7});
    /** Mace: tip / head / neck / upper shaft / lower shaft. The head carries the weight. */
    public static final TargetProfile MACE = new TargetProfile(new double[] {0.8, 1.7, 1.0, 0.45, 0.35});
    /** Hammer: striking face / head / neck / handle. */
    public static final TargetProfile HAMMER = new TargetProfile(new double[] {1.2, 1.7, 0.8, 0.4});
    /** Staff: head / ornament / upper / middle / lower shaft. */
    public static final TargetProfile STAFF = new TargetProfile(new double[] {1.3, 1.1, 0.7, 0.6, 0.5});
    /** Bow: upper tip / upper limb / grip / lower limb / lower tip. Both limbs need the most work. */
    public static final TargetProfile BOW = new TargetProfile(new double[] {0.5, 1.4, 0.9, 1.4, 0.5});
    /** Helmet: left cheek / left side / crown / right side / right cheek. */
    public static final TargetProfile HELMET = new TargetProfile(new double[] {0.6, 1.1, 1.6, 1.1, 0.6});
    /** Chestplate: left shoulder / left chest / centre / right chest / right shoulder. */
    public static final TargetProfile CHESTPLATE = new TargetProfile(new double[] {0.7, 1.3, 1.7, 1.3, 0.7});
    /** Leggings: left hem / left thigh / waist / right thigh / right hem. */
    public static final TargetProfile LEGGINGS = new TargetProfile(new double[] {0.6, 1.2, 1.5, 1.2, 0.6});
    /** Boots: left toe / left heel / right heel / right toe. */
    /** Arrows: head / shaft / fletching. The iron head takes the work. */
    public static final TargetProfile ARROW = new TargetProfile(new double[] {1.5, 0.7, 0.5});
    public static final TargetProfile BOOTS = new TargetProfile(new double[] {0.9, 1.4, 1.4, 0.9});
}
