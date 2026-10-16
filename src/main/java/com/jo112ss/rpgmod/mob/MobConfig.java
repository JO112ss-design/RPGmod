package com.jo112ss.rpgmod.mob;

import com.jo112ss.rpgmod.mob.core.MobLevelMath;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Enemy levels and AI (config/rpgmod-mobs.toml, synced so the client knows what to draw). */
public final class MobConfig {
    private MobConfig() {}

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue LEVELS = B.comment("Give monsters levels that make them stronger further from spawn").define("levels", true);

    static {
        B.push("overworld");
    }
    public static final ModConfigSpec.DoubleValue OW_BLOCKS = B.comment("Blocks from the world spawn per level").defineInRange("blocksPerLevel", 100.0, 1, 100000);
    public static final ModConfigSpec.IntValue OW_MAX = B.comment("Highest level in the overworld").defineInRange("maxLevel", 40, 1, 1000);
    public static final ModConfigSpec.IntValue DEPTH_MAX = B.comment("Extra levels deep underground (below y=0), at most").defineInRange("depthBonus", 5, 0, 100);
    static {
        B.pop().push("nether");
    }
    public static final ModConfigSpec.IntValue NETHER_MIN = B.defineInRange("minLevel", 35, 1, 1000);
    public static final ModConfigSpec.IntValue NETHER_MAX = B.defineInRange("maxLevel", 60, 1, 1000);
    public static final ModConfigSpec.DoubleValue NETHER_BLOCKS = B.comment("Overworld-equivalent blocks (nether distance x8) per level").defineInRange("blocksPerLevel", 250.0, 1, 100000);
    static {
        B.pop().push("end");
    }
    public static final ModConfigSpec.IntValue END_MIN = B.defineInRange("minLevel", 70, 1, 1000);
    public static final ModConfigSpec.IntValue END_MAX = B.defineInRange("maxLevel", 85, 1, 1000);
    public static final ModConfigSpec.DoubleValue END_OUTER = B.comment("Distance from 0,0 where levels start rising (the outer islands)").defineInRange("outerStart", 800.0, 0, 100000);
    public static final ModConfigSpec.DoubleValue END_BLOCKS = B.defineInRange("blocksPerLevel", 400.0, 1, 100000);
    static {
        B.pop().push("stats");
    }
    public static final ModConfigSpec.IntValue VARIANCE = B.comment("Random spread of levels (+-)").defineInRange("variance", 2, 0, 50);
    public static final ModConfigSpec.DoubleValue HP_LINEAR = B.comment("Health x (1 + a(L-1) + b(L-1)^2)").defineInRange("healthA", 0.025, 0, 10);
    public static final ModConfigSpec.DoubleValue HP_SQUARE = B.defineInRange("healthB", 0.00025, 0, 10);
    public static final ModConfigSpec.DoubleValue DMG_LINEAR = B.comment("Damage x (1 + a(L-1) + b(L-1)^2)").defineInRange("damageA", 0.02, 0, 10);
    public static final ModConfigSpec.DoubleValue DMG_SQUARE = B.defineInRange("damageB", 0.0002, 0, 10);
    public static final ModConfigSpec.DoubleValue EXPLOSION_SHARE = B.comment("Share of the damage growth that explosions get").defineInRange("explosionShare", 0.25, 0, 1);
    static {
        B.pop().push("ai");
    }
    public static final ModConfigSpec.BooleanValue SMART_AI = B.comment("Smarter monsters (building, digging, items, aimed shots...)").define("enabled", true);
    public static final ModConfigSpec.BooleanValue BREAK_BLOCKS = B.comment("Monsters may break blocks (digging zombies, wall-breaching creepers, TNT); also needs mobGriefing").define("breakBlocks", true);
    public static final ModConfigSpec.BooleanValue DRAGON = B.comment("Three-phase Ender Dragon").define("dragon", true);
    static {
        B.pop();
    }

    public static final ModConfigSpec SPEC = B.build();

    private static MobLevelMath.Rules cached;

    public static MobLevelMath.Rules rules() {
        if (cached != null) return cached;
        MobLevelMath.Rules r = new MobLevelMath.Rules();
        if (SPEC.isLoaded()) {
            r.overworldBlocksPerLevel = OW_BLOCKS.get();
            r.overworldMax = OW_MAX.get();
            r.depthBonusMax = DEPTH_MAX.get();
            r.netherMin = NETHER_MIN.get();
            r.netherMax = Math.max(NETHER_MIN.get(), NETHER_MAX.get());
            r.netherBlocksPerLevel = NETHER_BLOCKS.get();
            r.endMin = END_MIN.get();
            r.endMax = Math.max(END_MIN.get(), END_MAX.get());
            r.endOuterStart = END_OUTER.get();
            r.endBlocksPerLevel = END_BLOCKS.get();
            r.variance = VARIANCE.get();
            r.hpLinear = HP_LINEAR.get();
            r.hpSquare = HP_SQUARE.get();
            r.damageLinear = DMG_LINEAR.get();
            r.damageSquare = DMG_SQUARE.get();
            r.explosionShare = EXPLOSION_SHARE.get();
            cached = r;
        }
        return r;
    }

    public static void invalidate() {
        cached = null;
    }

    public static boolean levels() {
        return !SPEC.isLoaded() || LEVELS.get();
    }

    public static boolean smartAi() {
        return !SPEC.isLoaded() || SMART_AI.get();
    }

    public static boolean breakBlocks() {
        return !SPEC.isLoaded() || BREAK_BLOCKS.get();
    }

    public static boolean dragon() {
        return !SPEC.isLoaded() || DRAGON.get();
    }
}
