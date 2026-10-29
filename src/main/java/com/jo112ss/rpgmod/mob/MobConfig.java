package com.jo112ss.rpgmod.mob;

import com.jo112ss.rpgmod.mob.core.MobLevelMath;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Enemy levels and AI (config/rpgmod-mobs.toml, synced so the client knows what to draw). */
public final class MobConfig {
    private MobConfig() {}

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue LEVELS = B.comment("Give monsters levels that make them stronger").define("levels", true);
    public static final ModConfigSpec.BooleanValue FROM_PLAYERS = B.comment("true: monsters appear at (highest job level among online players) + playerLevelBonusMin..playerLevelBonusMax;",
            "false: level by place (distance from spawn and dimension, see the sections below)").define("levelsFromPlayers", true);
    public static final ModConfigSpec.IntValue PLAYER_MIN = B.defineInRange("playerLevelBonusMin", 0, -1000, 1000);
    public static final ModConfigSpec.IntValue PLAYER_MAX = B.defineInRange("playerLevelBonusMax", 3, -1000, 1000);
    public static final ModConfigSpec.DoubleValue SPEED = B.comment("Movement speed multiplier for every hostile monster").defineInRange("monsterSpeedMultiplier", 1.5, 0.1, 10);
    public static final ModConfigSpec.BooleanValue EQUIPMENT = B.comment("Random armour and job weapons by level for hostile monsters except endermen and bosses (never dropped)").define("monsterEquipment", true);
    public static final ModConfigSpec.BooleanValue SHOW_ARMOR = B.comment("Show the armour monsters wear (off: they still wear it, unseen; weapons are always shown)").define("showMonsterArmor", false);
    public static final ModConfigSpec.BooleanValue RPG_ARMOR = B.comment("RPG armour: reduction = defence / (defence + K), defence = armour x2 + toughness x2 (replaces the vanilla formula)").define("rpgArmor", true);
    public static final ModConfigSpec.DoubleValue PLAYER_ARMOR_K = B.comment("K for players (bigger = armour less effective; 80: copper set -20%, iron -41%, netherite -71% at Lv1 and -75% at Lv100)").defineInRange("playerArmorReductionK", 80.0, 1, 10000);
    public static final ModConfigSpec.DoubleValue MONSTER_ARMOR_K = B.comment("K for monsters (their random gear counts for less)").defineInRange("monsterArmorK", 80.0, 1, 10000);
    public static final ModConfigSpec.DoubleValue GEAR_WEAPON = B.comment("Share of a monster's weapon attack that counts (its flat attack and its material multiplier)").defineInRange("monsterWeaponShare", 0.45, 0, 1);
    public static final ModConfigSpec.IntValue ZOMBIE_RANGE = B.comment("How far zombies, creepers, spiders and skeletons notice and follow players, through walls (blocks)").defineInRange("monsterSenseRange", 64, 8, 512);

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
    // (renamed with the 500-health rebalance so older configs pick up the new curves)
    public static final ModConfigSpec.DoubleValue HP_LINEAR = B.comment("Health x (1 + a(L-1) + b(L-1)^2): x8.6 at Lv100, x17.6 at Lv200").defineInRange("healthGrowthLinear", 0.07, 0, 10);
    public static final ModConfigSpec.DoubleValue HP_SQUARE = B.defineInRange("healthGrowthSquare", 0.00007, 0, 10);
    public static final ModConfigSpec.DoubleValue DMG_LINEAR = B.comment("Damage x (1 + a(L-1) + b(L-1)^2): x12 at Lv100, x24 at Lv200 (players have up to 500 health)").defineInRange("damageGrowthLinear", 0.105, 0, 10);
    public static final ModConfigSpec.DoubleValue DMG_SQUARE = B.defineInRange("damageGrowthSquare", 0.00007, 0, 10);
    public static final ModConfigSpec.DoubleValue EXPLOSION_SHARE = B.comment("Share of the damage growth that explosions get").defineInRange("explosionShare", 0.25, 0, 1);
    static {
        B.pop().push("ai");
    }
    public static final ModConfigSpec.BooleanValue SMART_AI = B.comment("Smarter monsters (building, digging, items, aimed shots...)").define("enabled", true);
    public static final ModConfigSpec.BooleanValue BREAK_BLOCKS = B.comment("Monsters may break blocks (digging zombies, wall-breaching creepers, TNT); also needs mobGriefing").define("breakBlocks", true);
    public static final ModConfigSpec.DoubleValue ELITE_CHANCE = B.comment("Share of ordinary monsters that appear as elites with traits (0 = none)").defineInRange("eliteChance", 0.08, 0, 1);
    public static final ModConfigSpec.DoubleValue REVENANT_CHANCE = B.comment("Share of monsters spawning in overworld caves (below y 40, no sky) that come as fallen adventurers instead (0 = none)")
            .defineInRange("revenantChance", 0.25, 0, 1);
    public static final ModConfigSpec.BooleanValue DRAGON = B.comment("Three-stage Ender Dragon").define("dragon", true);
    public static final ModConfigSpec.DoubleValue DRAGON_HEALTH = B.comment("Ender Dragon health at level 1 for one player; it grows with the highest job level on the server like the dungeon bosses (2600: as theirs)").defineInRange("dragonBaseHealth", 2600.0, 1, 100000);
    public static final ModConfigSpec.DoubleValue DRAGON_DAMAGE = B.comment("Ender Dragon damage multiplier (all its attacks, breath included)").defineInRange("dragonDamageMultiplier", 8.0, 0, 100);
    public static final ModConfigSpec.DoubleValue DRAGON_PER_PLAYER = B.comment("Extra Ender Dragon health per additional player online, spectators not counted (0.6 = +60%; 6 players: x4)").defineInRange("dragonHealthPerPlayer", 0.6, 0, 10);
    public static final ModConfigSpec.DoubleValue DRAGON_WINDOW = B.comment("Most of its health the dragon can lose per perch, or per flight between perches (0.1 = 10%: at least ten rounds; 0 = no limit)").defineInRange("dragonMaxShare", 0.1, 0, 1);
    static {
        B.pop().push("bosses");
    }
    public static final ModConfigSpec.DoubleValue KING_HEALTH = B.comment("King Slime health at level 1 for one player (grows with level like other monsters)").defineInRange("kingSlimeBaseHealth", 2600.0, 1, 100000);
    public static final ModConfigSpec.DoubleValue GOLEM_HEALTH = B.comment("Golem health at level 1 for one player (grows with level like other monsters)").defineInRange("golemBaseHealth", 2600.0, 1, 100000);
    public static final ModConfigSpec.DoubleValue LICH_HEALTH = B.comment("Lich King health at level 1 for one player (grows with level like other monsters)").defineInRange("lichBaseHealth", 2600.0, 1, 100000);
    public static final ModConfigSpec.DoubleValue QUEEN_HEALTH = B.comment("Spider Queen health at level 1 for one player (grows with level like other monsters)").defineInRange("spiderQueenBaseHealth", 2600.0, 1, 100000);
    public static final ModConfigSpec.DoubleValue BOSS_PER_PLAYER = B.comment("Extra boss health per additional player fighting (0.8 = +80%)").defineInRange("healthPerPlayer", 0.8, 0, 10);
    public static final ModConfigSpec.IntValue BOSS_LEVEL_BONUS = B.comment("Boss level = highest player job level + this").defineInRange("levelBonus", 0, -100, 100);
    public static final ModConfigSpec.DoubleValue BOSS_MAX_HIT = B.comment("Largest share of a player's max health one boss hit can take (before armour)").defineInRange("maxHitShare", 0.2, 0.01, 10);
    public static final ModConfigSpec.IntValue BOSS_BOOKS = B.comment("Skill books every participant gets for defeating the boss").defineInRange("rewardBooks", 5, 0, 64);
    static {
        B.pop();
    }

    public static final ModConfigSpec SPEC = B.build();

    public static double eliteChance() {
        return SPEC.isLoaded() ? ELITE_CHANCE.get() : 0.08;
    }

    public static double revenantChance() {
        return SPEC.isLoaded() ? REVENANT_CHANCE.get() : 0.25;
    }

    public static double golemHealth() {
        return SPEC.isLoaded() ? GOLEM_HEALTH.get() : 2600;
    }

    public static double lichHealth() {
        return SPEC.isLoaded() ? LICH_HEALTH.get() : 2600;
    }

    public static double queenHealth() {
        return SPEC.isLoaded() ? QUEEN_HEALTH.get() : 2600;
    }

    public static double kingHealth() {
        return SPEC.isLoaded() ? KING_HEALTH.get() : 2600;
    }

    public static double bossPerPlayer() {
        return SPEC.isLoaded() ? BOSS_PER_PLAYER.get() : 0.8;
    }

    public static int bossLevelBonus() {
        return SPEC.isLoaded() ? BOSS_LEVEL_BONUS.get() : 0;
    }

    public static double bossMaxHit() {
        return SPEC.isLoaded() ? BOSS_MAX_HIT.get() : 0.2;
    }

    public static int bossBooks() {
        return SPEC.isLoaded() ? BOSS_BOOKS.get() : 5;
    }

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

    public static boolean fromPlayers() {
        return !SPEC.isLoaded() || FROM_PLAYERS.get();
    }

    public static int playerMin() {
        return SPEC.isLoaded() ? Math.min(PLAYER_MIN.get(), PLAYER_MAX.get()) : 0;
    }

    public static int playerMax() {
        return SPEC.isLoaded() ? Math.max(PLAYER_MIN.get(), PLAYER_MAX.get()) : 3;
    }

    public static double speed() {
        return SPEC.isLoaded() ? SPEED.get() : 1.5;
    }

    public static boolean rpgArmor() {
        return !SPEC.isLoaded() || RPG_ARMOR.get();
    }

    public static double playerArmorK() {
        return SPEC.isLoaded() ? PLAYER_ARMOR_K.get() : 80;
    }

    public static double monsterArmorK() {
        return SPEC.isLoaded() ? MONSTER_ARMOR_K.get() : 80;
    }

    public static double weaponShare() {
        return SPEC.isLoaded() ? GEAR_WEAPON.get() : 0.45;
    }

    public static boolean showArmor() {
        return SPEC.isLoaded() && SHOW_ARMOR.get();
    }

    public static boolean equipment() {
        return !SPEC.isLoaded() || EQUIPMENT.get();
    }

    public static int zombieRange() {
        return SPEC.isLoaded() ? ZOMBIE_RANGE.get() : 64;
    }

    public static boolean smartAi() {
        return !SPEC.isLoaded() || SMART_AI.get();
    }

    public static boolean breakBlocks() {
        return !SPEC.isLoaded() || BREAK_BLOCKS.get();
    }

    public static double dragonHealth() {
        return SPEC.isLoaded() ? DRAGON_HEALTH.get() : 2600;
    }

    public static double dragonPerPlayer() {
        return SPEC.isLoaded() ? DRAGON_PER_PLAYER.get() : 0.6;
    }

    public static double dragonWindow() {
        return SPEC.isLoaded() ? DRAGON_WINDOW.get() : 0.1;
    }

    public static double dragonDamage() {
        return SPEC.isLoaded() ? DRAGON_DAMAGE.get() : 8.0;
    }

    public static boolean dragon() {
        return !SPEC.isLoaded() || DRAGON.get();
    }
}
