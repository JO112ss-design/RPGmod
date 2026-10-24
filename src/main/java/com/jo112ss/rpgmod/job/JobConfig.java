package com.jo112ss.rpgmod.job;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Job system settings (config/rpgmod-jobs.toml, synced from the server). */
public final class JobConfig {
    private JobConfig() {}

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue MAX_LEVEL = B.comment("Highest total job level: 100 for the base job, then the advanced job up to its level 100 (= 199)").defineInRange("maxTotalJobLevel", com.jo112ss.rpgmod.job.core.LevelCurve.MAX_TOTAL_LEVEL, 1, 1000);
    public static final ModConfigSpec.DoubleValue EXP_MULTIPLIER = B.comment("Job experience per kill = victim max health x this").defineInRange("expMultiplier", 1.0, 0, 1000);
    public static final ModConfigSpec.DoubleValue BOSS_EXP_MULTIPLIER = B.comment("Extra multiplier for bosses").defineInRange("bossExpMultiplier", 3.0, 0, 1000);
    public static final ModConfigSpec.DoubleValue SHARE_RADIUS = B.comment("Players within this many blocks of a kill get the same experience (co-op); 0 = killer only").defineInRange("expShareRadius", 70.0, 0, 256);
    public static final ModConfigSpec.BooleanValue REQUIRE_JOB_WEAPON = B.comment("Skills only work while holding one of the job's weapons").define("requireJobWeapon", true);

    public static final ModConfigSpec.IntValue RESET_COST_LEVELS = B.comment("Vanilla experience levels it costs to reset the skill panel").defineInRange("panelResetLevels", 20, 0, 1000);
    public static final ModConfigSpec.DoubleValue AFFINITY_ATTACK_SPEED = B.comment("Attack speed bonus while holding one of your job's weapons (0.3 = x1.3)").defineInRange("jobWeaponAttackSpeed", 0.3, 0, 10);

    public static final ModConfigSpec.IntValue REST_DELAY = B.comment("Seconds without attacking or being hurt before mana regenerates faster").defineInRange("restRegenDelay", 30, 0, 3600);
    public static final ModConfigSpec.DoubleValue REST_MULT = B.comment("Mana regeneration multiplier while out of combat").defineInRange("restRegenMultiplier", 3.0, 1, 100);

    public static final ModConfigSpec.DoubleValue ENCHANT_BUFF_REF = B.comment("Enchanter buffs last their listed time at this magic attack; longer above, shorter below").defineInRange("enchanterBuffReferenceAttack", 10.0, 0.1, 1000);
    public static final ModConfigSpec.DoubleValue ENCHANT_BUFF_MIN = B.comment("Shortest enchanter buff time, as a multiple of the listed time").defineInRange("enchanterBuffMinMultiplier", 0.5, 0.01, 100);
    public static final ModConfigSpec.DoubleValue ENCHANT_BUFF_MAX = B.comment("Longest enchanter buff time, as a multiple of the listed time").defineInRange("enchanterBuffMaxMultiplier", 3.0, 0.01, 100);

    public static final ModConfigSpec.DoubleValue SWAP_COOLDOWN = B.comment("Seconds a skill or spell cannot be used after it is put into a slot (stops swapping in fresh skills mid-fight)").defineInRange("skillSwapCooldown", 10.0, 0, 600);
    public static final ModConfigSpec.DoubleValue DURATION_AT_MAX = B.comment("Extra effect time of non-ultimate skills at the highest level (0.5 = +50%), growing evenly from level 1; enchanter buffs follow magic attack instead").defineInRange("skillDurationBonusAtMax", 0.5, 0, 10);
    public static final ModConfigSpec.DoubleValue LUCK_PER_POINT = B.comment("Per point of luck: chance that a gamble is rolled twice, keeping the better result (0.05 = 5%, up to 50%)").defineInRange("luckRerollPerPoint", 0.05, 0, 1);
    public static final ModConfigSpec.DoubleValue POTION_DROP = B.comment("Chance for a monster killed by a player to drop an HP potion, and separately an MP potion").defineInRange("potionDropChance", 0.03, 0, 1);
    public static final ModConfigSpec.DoubleValue POTION_DROP_LUCK = B.comment("Extra potion drop chance per point of the killer's luck").defineInRange("potionDropPerLuck", 0.005, 0, 1);
    public static final ModConfigSpec.DoubleValue MACE_MULT = B.comment("Normal (non-skill) hits with a mace deal this much (heavy, slow weapon)").defineInRange("maceAttackMultiplier", 1.25, 0, 100);
    public static final ModConfigSpec.DoubleValue HAMMER_MULT = B.comment("Normal (non-skill) hits with a hammer deal this much").defineInRange("hammerAttackMultiplier", 1.6, 0, 100);

    public static final ModConfigSpec SPEC = B.build();

    public static int maxLevel() {
        return SPEC.isLoaded() ? MAX_LEVEL.get() : com.jo112ss.rpgmod.job.core.LevelCurve.MAX_TOTAL_LEVEL;
    }
}
