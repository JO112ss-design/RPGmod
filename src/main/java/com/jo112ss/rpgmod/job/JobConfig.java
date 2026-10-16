package com.jo112ss.rpgmod.job;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Job system settings (config/rpgmod-jobs.toml, synced from the server). */
public final class JobConfig {
    private JobConfig() {}

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue MAX_LEVEL = B.comment("Highest job level").defineInRange("maxLevel", 50, 1, 1000);
    public static final ModConfigSpec.DoubleValue EXP_MULTIPLIER = B.comment("Job experience per kill = victim max health x this").defineInRange("expMultiplier", 1.0, 0, 1000);
    public static final ModConfigSpec.DoubleValue BOSS_EXP_MULTIPLIER = B.comment("Extra multiplier for bosses").defineInRange("bossExpMultiplier", 3.0, 0, 1000);
    public static final ModConfigSpec.DoubleValue SHARE_RADIUS = B.comment("Players within this many blocks of a kill get the same experience (co-op); 0 = killer only").defineInRange("shareRadius", 24.0, 0, 256);
    public static final ModConfigSpec.BooleanValue REQUIRE_JOB_WEAPON = B.comment("Skills only work while holding one of the job's weapons").define("requireJobWeapon", true);

    public static final ModConfigSpec SPEC = B.build();

    public static int maxLevel() {
        return SPEC.isLoaded() ? MAX_LEVEL.get() : 50;
    }
}
