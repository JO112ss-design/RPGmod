package com.jo112ss.rpgmod.devtest;

import com.jo112ss.rpgmod.RpgMod;
import com.jo112ss.rpgmod.mob.MobLevels;
import com.jo112ss.rpgmod.mob.ai.MobAi;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Development-only: small stages for checking the monster AI in the client test (logged + photographed). */
final class MobScenes {
    private MobScenes() {}

    private static String scene;
    private static int t;
    private static BlockPos c;
    private static final List<Mob> mobs = new ArrayList<>();
    private static double startY;
    private static net.minecraft.world.entity.boss.enderdragon.EnderDragon dragon;
    private static Vec3 startPos;
    private static int arrowsHit;
    private static float lostHealth;
    private static float maxHit;
    private static com.jo112ss.rpgmod.mob.boss.KingSlime king;
    private static com.jo112ss.rpgmod.mob.boss.Golem golem;
    private static Mob dummy;
    private static long savedTime = -1;

    static boolean active() {
        return scene != null;
    }

    static void onPlayerHurt(float amount, boolean arrow) {
        if (scene == null) return;
        lostHealth += amount;
        maxHit = Math.max(maxHit, amount);
        if (arrow) arrowsHit++;
    }

    static void start(ServerLevel level, ServerPlayer player, Vec3 arena, String name) {
        end(level, player);
        int idx = switch (name) {
            case "pillar" -> 0;
            case "dig" -> 1;
            case "creeper" -> 2;
            case "archer" -> 3;
            case "items" -> 4;
            case "dragon" -> 6;
            case "nether" -> 7;
            case "dragonhp" -> 12;
            case "downed" -> 13;
            case "king" -> 9;
            case "golem" -> 9;
            case "sense" -> 8;
            case "stairs" -> 10;
            case "exit" -> 11;
            case "leap" -> 8; // the sense scene's spot (that scene is not in the current showcase)
            default -> 5;
        };
        c = BlockPos.containing(arena).offset(40, 0, idx * 24 - 60);
        clear(level, c, name.equals("king") || name.equals("golem") ? 22 : 10);
        scene = name;
        maxHit = 0;
        t = 0;
        arrowsHit = 0;
        lostHealth = 0;
        player.setGameMode(GameType.SURVIVAL);
        player.getAbilities().invulnerable = false;
        player.onUpdateAbilities();
        player.removeAllEffects();
        player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 600, 2, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 600, 2, false, false));
        player.setHealth(player.getMaxHealth());
        switch (name) {
            case "pillar" -> {
                for (int y = 0; y < 4; y++) level.setBlockAndUpdate(c.above(y), Blocks.STONE.defaultBlockState());
                tp(player, Vec3.atBottomCenterOf(c.above(4)), -90, 35);
                Mob z = spawn(level, EntityTypes.HUSK, c.offset(3, 0, 0), 20);
                startY = z.getY();
            }
            case "dig" -> {
                // a closed stone hut around the player
                for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) for (int y = 0; y <= 3; y++) {
                    boolean wall = Math.abs(x) == 2 || Math.abs(z) == 2 || y == 3;
                    if (wall) level.setBlockAndUpdate(c.offset(x, y, z), Blocks.STONE.defaultBlockState());
                }
                tp(player, Vec3.atBottomCenterOf(c), -90, 10);
                spawn(level, EntityTypes.HUSK, c.offset(6, 0, 0), 25);
            }
            case "stairs", "exit" -> {
                // a stone hill 12 high; the player on top, a zombie in a cave inside it
                for (int x = -6; x <= 30; x++) for (int z = -10; z <= 10; z++) for (int y = 0; y < 12; y++) {
                    level.setBlock(c.offset(x, y, z), (name.equals("stairs") ? Blocks.DIRT : Blocks.STONE).defaultBlockState(), 2);
                }
                for (int x = 11; x <= 16; x++) for (int z = -1; z <= 1; z++) for (int y = 1; y <= 2; y++) level.setBlock(c.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                BlockPos top = c.above(12);
                // lit, so no other monster spawns in the dark cave during the test
                level.setBlock(c.offset(16, 1, 1), Blocks.TORCH.defaultBlockState(), 2);
                if (name.equals("exit")) {
                    // a walkable ramp up to the open air at the far end of the cave...
                    for (int i = 0; i <= 11; i++) for (int y = 1; y <= 3; y++) level.setBlock(c.offset(17 + i, i + y, 0), Blocks.AIR.defaultBlockState(), 2);
                    for (int i = 2; i <= 11; i += 3) level.setBlock(c.offset(17 + i, i + 1, 0), Blocks.TORCH.defaultBlockState(), 2);
                    // ...and the player shut in a hut on top, so nobody can walk right up to them
                    for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) for (int y = 0; y <= 3; y++) {
                        boolean wall = Math.abs(x) == 2 || Math.abs(z) == 2 || y == 3;
                        if (wall) level.setBlockAndUpdate(top.offset(x, y, z), Blocks.STONE.defaultBlockState());
                    }
                }
                tp(player, Vec3.atBottomCenterOf(top), -90, 25);
                com.jo112ss.rpgmod.mob.ai.BuilderGoal.dugBlocks = 0;
                com.jo112ss.rpgmod.mob.ai.SurfaceGoal.lastMode = "-";
                spawn(level, EntityTypes.ZOMBIE, c.offset(12, 1, 0), 25);
                for (Mob m : mobs) m.setTarget(null);
            }
            case "leap" -> {
                // the player in a roofless stone pen: a leaping creeper outside has no way in but over the top;
                // a skeleton and a creeper far off behind the walls should still find the player
                for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) for (int y = 0; y <= 2; y++) {
                    if (Math.abs(x) == 2 || Math.abs(z) == 2) level.setBlockAndUpdate(c.offset(x, y, z), Blocks.STONE.defaultBlockState());
                }
                tp(player, Vec3.atBottomCenterOf(c), -90, 10);
                // spawned once the player's arrival has the chunks ticking (else they unload at once)
                com.jo112ss.rpgmod.job.SkillTasks.later(10, () -> {
                    var leaper = EntityTypes.CREEPER.create(level, EntitySpawnReason.COMMAND);
                    leaper.snapTo(c.getX() + 8.5, c.getY(), c.getZ() + 0.5, 90F, 0F);
                    MobLevels.apply(leaper, 30);
                    MobAi.onFirstLevel(leaper, 30);
                    leaper.getPersistentData().putBoolean(com.jo112ss.rpgmod.mob.ai.CreeperLeapGoal.KEY, true);
                    leaper.setPersistenceRequired();
                    level.addFreshEntity(leaper);
                    mobs.add(leaper);
                    Mob sk = spawn(level, EntityTypes.SKELETON, c.offset(-9, 0, 9), 30);
                    sk.setTarget(null);
                    RpgMod.LOGGER.info("[AUTOTEST] leap scene: alive {} {}, creeper follow range {} armour slots empty={} skeleton follow range {}", leaper.isAlive(), sk.isAlive(),
                            leaper.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE),
                            leaper.getItemBySlot(EquipmentSlot.CHEST).isEmpty() && leaper.getItemBySlot(EquipmentSlot.HEAD).isEmpty(),
                            sk.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE));
                });
            }
            case "sense" -> {
                // the player sealed in a stone hut: zombies far outside can never see them
                for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) for (int y = 0; y <= 3; y++) {
                    boolean wall = Math.abs(x) == 2 || Math.abs(z) == 2 || y == 3;
                    if (wall) level.setBlockAndUpdate(c.offset(x, y, z), Blocks.STONE.defaultBlockState());
                }
                tp(player, Vec3.atBottomCenterOf(c), -90, 10);
                spawn(level, EntityTypes.HUSK, c.offset(28, 0, 0), 25);
                spawn(level, EntityTypes.ZOMBIE, c.offset(-20, 0, 18), 25);
                for (Mob m : mobs) m.setTarget(null);
            }
            case "creeper" -> {
                for (int z = -4; z <= 4; z++) for (int y = 0; y <= 2; y++) level.setBlockAndUpdate(c.offset(2, y, z), Blocks.STONE.defaultBlockState());
                tp(player, Vec3.atBottomCenterOf(c), -90, 10);
                spawn(level, EntityTypes.CREEPER, c.offset(4, 0, 0), 30);
            }
            case "archer" -> {
                tp(player, Vec3.atBottomCenterOf(c), -90, 5);
                Mob s = spawn(level, EntityTypes.SKELETON, c.offset(14, 0, 0), 50);
                s.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
                ((AbstractSkeleton) s).reassessWeaponGoal();
            }
            case "items" -> {
                tp(player, Vec3.atBottomCenterOf(c), -90, 5);
                Mob rod = spawn(level, EntityTypes.HUSK, c.offset(8, 0, 2), 20);
                rod.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.FISHING_ROD));
                Mob pearl = spawn(level, EntityTypes.HUSK, c.offset(18, 0, -3), 20);
                pearl.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.ENDER_PEARL, 2));
                startPos = pearl.position();
                Mob tnt = spawn(level, EntityTypes.HUSK, c.offset(11, 0, 6), 20);
                tnt.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TNT, 1));
            }
            case "nether" -> {
                tp(player, Vec3.atBottomCenterOf(c), -90, -5);
                com.jo112ss.rpgmod.mob.ai.NetherAi.novas = com.jo112ss.rpgmod.mob.ai.NetherAi.lunges = com.jo112ss.rpgmod.mob.ai.NetherAi.charges = 0;
                com.jo112ss.rpgmod.mob.ai.NetherAi.leaps = com.jo112ss.rpgmod.mob.ai.NetherAi.followUps = 0;
                spawn(level, EntityTypes.BLAZE, c.offset(6, 2, 0), 60);
                spawn(level, EntityTypes.WITHER_SKELETON, c.offset(8, 0, 3), 60);
                Mob hog = spawn(level, EntityTypes.HOGLIN, c.offset(11, 0, -3), 60);
                hog.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET, player);
                Mob cube = spawn(level, EntityTypes.MAGMA_CUBE, c.offset(8, 0, -7), 60);
                ((net.minecraft.world.entity.monster.cubemob.MagmaCube) cube).setSize(3, true);
                spawn(level, EntityTypes.GHAST, c.offset(24, 8, 0), 60);
            }
            case "dragon" -> {
                tp(player, Vec3.atBottomCenterOf(c), -90, -12);
                var d = EntityTypes.ENDER_DRAGON.create(level, EntitySpawnReason.COMMAND);
                d.snapTo(c.getX() + 22, c.getY() + 9, c.getZ() + 0.5, 90F, 0F);
                d.setFightOrigin(c);
                level.addFreshEntity(d);
                d.getPhaseManager().setPhase(net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase.HOVERING);
                dragon = d;
            }
            case "downed" -> {
                tp(player, Vec3.atBottomCenterOf(c), -90, 10);
                spawn(level, EntityTypes.ZOMBIE, c.offset(5, 0, 0), 20);
                spawn(level, EntityTypes.SKELETON, c.offset(8, 0, 3), 20);
            }
            case "dragonhp" -> {
                // six players' worth of dragon: health scaling and the per-window damage limit
                com.jo112ss.rpgmod.mob.dragon.DragonDirector.testPlayers = 6;
                tp(player, Vec3.atBottomCenterOf(c), -90, -12);
                var d = EntityTypes.ENDER_DRAGON.create(level, EntitySpawnReason.COMMAND);
                d.snapTo(c.getX() + 22, c.getY() + 9, c.getZ() + 0.5, 90F, 0F);
                d.setFightOrigin(c);
                level.addFreshEntity(d);
                d.getPhaseManager().setPhase(net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase.HOVERING);
                dragon = d;
            }
            case "golem" -> {
                tp(player, Vec3.atBottomCenterOf(c), -90, -2);
                savedTime = level.getOverworldClockTime();
            }
            case "king" -> {
                tp(player, Vec3.atBottomCenterOf(c), -90, -6);
                savedTime = level.getOverworldClockTime();
            }
            default -> {
                tp(player, Vec3.atBottomCenterOf(c), -90, 15);
                spawn(level, EntityTypes.SPIDER, c.offset(10, 0, 0), 40);
            }
        }
        for (Mob m : mobs) m.setTarget(player);
        RpgMod.LOGGER.info("[AUTOTEST] scene {} at {} with {}", name, c, mobs.stream().map(m -> m.getType().getDescriptionId() + " Lv" + MobLevels.level(m)).toList());
    }

    private static void setTime(ServerLevel level, long ticks) {
        var clock = level.registryAccess().get(net.minecraft.world.clock.WorldClocks.OVERWORLD).orElseThrow();
        level.getServer().clockManager().setTotalTicks(clock, ticks);
    }

    private static com.jo112ss.rpgmod.mob.boss.KingSlime findKing(ServerLevel level, ServerPlayer player) {
        var list = level.getEntitiesOfClass(com.jo112ss.rpgmod.mob.boss.KingSlime.class, player.getBoundingBox().inflate(64));
        return list.isEmpty() ? null : list.get(0);
    }

    private static final java.util.Map<String, Integer> kingMoves = new java.util.LinkedHashMap<>();

    private static void kingTick(ServerLevel level, ServerPlayer player) {
        var k = king;
        switch (t) {
            case 45 -> {
                k = king = com.jo112ss.rpgmod.mob.boss.Bosses.spawnNear(level, player);
                RpgMod.LOGGER.info("[AUTOTEST] king spawned={} level={} hp={}", k != null, k == null ? -1 : MobLevels.level(k),
                        k == null ? -1 : String.format("%.0f", k.effectiveMaxHealth()));
                if (k != null) {
                    k.snapTo(c.getX() + 22.5, c.getY(), c.getZ() + 0.5, 90, 0);
                    // the scripted moves aim at a dummy in front of the camera; its waves still reach the player
                    dummy = EntityTypes.HUSK.create(level, EntitySpawnReason.COMMAND);
                    dummy.snapTo(c.getX() + 11.5, c.getY(), c.getZ() + 0.5, 90, 0);
                    dummy.setNoAi(true);
                    dummy.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(5000);
                    dummy.setHealth(5000);
                    dummy.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
                    level.addFreshEntity(dummy);
                    k.setTarget(dummy);
                }
            }
            case 52, 100, 168, 285, 430 -> portrait(player, k, -1);
            case 548 -> portrait(player, k, 1); // from the open side of the wall
            case 58, 106, 174, 292, 436 -> tp(player, Vec3.atBottomCenterOf(c), -90, -6);
            case 60 -> move(k, "bounce");
            case 110 -> move(k, "dash");
            case 180 -> move(k, "rain");
            case 240 -> { if (k != null) k.setHealth(k.getMaxHealth() * 0.74F); }
            case 270 -> move(k, "tired");
            case 300 -> { if (k != null) k.setHealth(k.getMaxHealth() * 0.49F); }
            case 330 -> move(k, "ripple");
            case 400 -> move(k, "absorb");
            case 460 -> move(k, "triple");
            case 515 -> { // dash into a wall: the long opening
                if (k != null) {
                    for (int x = -6; x <= 6; x++) for (int y = 0; y <= 6; y++) level.setBlockAndUpdate(c.offset(11 + x, y, -10), Blocks.STONE.defaultBlockState());
                    k.snapTo(c.getX() + 11.5, c.getY(), c.getZ() + 8.5, 180, 0);
                    dummy.snapTo(c.getX() + 11.5, c.getY(), c.getZ() - 6.5, 0, 0);
                    dummy.setPos(c.getX() + 11.5, c.getY(), c.getZ() - 6.5);
                }
            }
            case 520 -> move(k, "dash");
            case 575 -> {
                if (k != null) {
                    RpgMod.LOGGER.info("[AUTOTEST] king wall hits={} state={} opening x{}", k.wallHits, k.state(), k.openingMultiplier());
                    RpgMod.LOGGER.info("[AUTOTEST] king before kill: size={} splits={} phase={} hp={}/{} moves={} max hit={} lost={}", k.getSize(), k.splits(), k.phase(),
                            String.format("%.0f", k.getHealth()), String.format("%.0f", k.getMaxHealth()), kingMoves, String.format("%.1f", maxHit), String.format("%.1f", lostHealth));
                    k.hurtServer(level, player.damageSources().playerAttack(player), 1000000);
                }
            }
            case 585 -> {
                int books = player.getInventory().countItem(com.jo112ss.rpgmod.registry.ModRegistries.SKILL_BOOK.get());
                RpgMod.LOGGER.info("[AUTOTEST] king dead={} books={}", k == null || !k.isAlive(), books);
            }
            case 590 -> { // a free-running king: its own combos and exhausted windows
                k = king = com.jo112ss.rpgmod.mob.boss.Bosses.spawnNear(level, player);
                kingMoves.clear();
                if (k != null) {
                    k.snapTo(c.getX() + 14.5, c.getY(), c.getZ() + 0.5, 90, 0);
                    if (dummy != null) {
                        dummy.setPos(c.getX() + 8.5, c.getY(), c.getZ() + 0.5);
                        k.setTarget(dummy);
                    }
                }
            }
            case 840 -> RpgMod.LOGGER.info("[AUTOTEST] king free run states={}", kingMoves);
            default -> { }
        }
        if (t % 10 == 0) { // keep the stage to the boss: no wandering monsters, the player stays alive
            for (var m : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(96),
                    m -> m instanceof net.minecraft.world.entity.monster.Enemy && !(m instanceof net.minecraft.world.entity.monster.cubemob.Slime) && m != dummy)) m.discard();
            player.setHealth(player.getMaxHealth());
            player.getFoodData().setFoodLevel(20);
        }
        if (k != null && k.isAlive() && dummy != null && dummy.isAlive() && k.getTarget() != dummy) k.setTarget(dummy);
        if (k != null && k.isAlive()) {
            String st = k.state().name();
            if (t % 2 == 0) kingMoves.merge(st, 1, Integer::sum);
            if (t % 20 == 0) {
                RpgMod.LOGGER.info("[AUTOTEST] king t={} state={} size={} hp={}/{} dist={} max hit={} lost={}", t, k.state(), k.getSize(),
                        String.format("%.0f", k.getHealth()), String.format("%.0f", k.getMaxHealth()), String.format("%.1f", k.distanceTo(player)),
                        String.format("%.1f", maxHit), String.format("%.1f", lostHealth));
            }
        }
    }

    private static final java.util.Map<String, Integer> golemStates = new java.util.LinkedHashMap<>();

    private static void golemTick(ServerLevel level, ServerPlayer player) {
        var g = golem;
        switch (t) {
            case 45 -> {
                g = golem = com.jo112ss.rpgmod.mob.boss.Bosses.spawnNear(level, player, com.jo112ss.rpgmod.registry.ModRegistries.GOLEM.get());
                RpgMod.LOGGER.info("[AUTOTEST] golem summoned={} level={} hp={} toughness={} size={}x{}", g != null, g == null ? -1 : MobLevels.level(g),
                        g == null ? -1 : String.format("%.0f", g.effectiveMaxHealth()), g == null ? -1 : g.toughness(), g == null ? -1 : g.getBbWidth(), g == null ? -1 : g.getBbHeight());
                if (g != null) {
                    g.snapTo(c.getX() + 19.5, c.getY(), c.getZ() + 0.5, 90, 0);
                    dummy = EntityTypes.HUSK.create(level, EntitySpawnReason.COMMAND);
                    dummy.snapTo(c.getX() + 9.5, c.getY(), c.getZ() + 0.5, 90, 0);
                    dummy.setNoAi(true);
                    dummy.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(5000);
                    dummy.setHealth(5000);
                    dummy.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
                    level.addFreshEntity(dummy);
                    g.setTarget(dummy);
                }
            }
            case 50, 466 -> golemPortrait(player, g, -1);
            case 56, 472 -> tp(player, Vec3.atBottomCenterOf(c), -90, -2);
            case 60 -> gmove(g, "slam");
            case 100 -> gmove(g, "throw");
            case 150 -> gmove(g, "fissure");
            case 200 -> gmove(g, "charge");
            case 255 -> { // somebody close for the sweep: the dummy steps up to it
                if (g != null && dummy != null) dummy.setPos(g.getX() - 3.2, g.getY(), g.getZ());
                gmove(g, "sweep");
            }
            case 285 -> { if (dummy != null) dummy.setPos(c.getX() + 9.5, c.getY(), c.getZ() + 0.5); }
            case 290 -> gmove(g, "rockfall");
            case 370 -> gmove(g, "overheat");
            case 448 -> { if (g != null) g.setHealth(g.getMaxHealth() * 0.49F); }
            case 500 -> { // break the pillars: the armour shatters and it staggers
                if (g != null) {
                    var ps = g.pillars(level);
                    RpgMod.LOGGER.info("[AUTOTEST] golem armour: pillars={} shield={} phase={} opening x{} pillar hp={}", ps.size(), g.shielded(), g.phase(), g.openingMultiplier(),
                            ps.isEmpty() ? -1 : String.format("%.0f", ps.get(0).getMaxHealth()));
                    for (var p : ps) p.hurtServer(level, player.damageSources().playerAttack(player), 100000);
                }
            }
            case 530 -> { if (g != null) RpgMod.LOGGER.info("[AUTOTEST] golem after pillars: shield={} breaks={} state={} opening x{}", g.shielded(), g.shieldBreaks, g.state(), g.openingMultiplier()); }
            case 580 -> gmove(g, "quake");
            case 650 -> { // a target it cannot walk to: up on a stone tower
                if (g != null && dummy != null) {
                    for (int y = 0; y < 5; y++) for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) level.setBlockAndUpdate(c.offset(9 + x, y, z), Blocks.STONE.defaultBlockState());
                    dummy.setPos(c.getX() + 9.5, c.getY() + 5, c.getZ() + 0.5);
                }
                gmove(g, "leap");
            }
            case 710 -> {
                if (g != null) {
                    RpgMod.LOGGER.info("[AUTOTEST] golem before kill: hp={}/{} armours={} wall hits={} states={} max hit={} lost={}", String.format("%.0f", g.getHealth()),
                            String.format("%.0f", g.getMaxHealth()), g.armours(), g.wallHits, golemStates, String.format("%.1f", maxHit), String.format("%.1f", lostHealth));
                    g.hurtServer(level, player.damageSources().playerAttack(player), 1000000);
                }
            }
            case 720 -> {
                int books = player.getInventory().countItem(com.jo112ss.rpgmod.registry.ModRegistries.SKILL_BOOK.get());
                int gems = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, player.getBoundingBox().inflate(40), e -> com.jo112ss.rpgmod.gem.Sockets.isGem(e.getItem())).size();
                RpgMod.LOGGER.info("[AUTOTEST] golem dead={} books={} gems dropped={}", g == null || !g.isAlive(), books, gems);
                for (int y = 0; y < 5; y++) for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) level.setBlockAndUpdate(c.offset(9 + x, y, z), Blocks.AIR.defaultBlockState());
            }
            case 725 -> { // a free-running golem: its own combos and overheats
                g = golem = com.jo112ss.rpgmod.mob.boss.Bosses.spawnNear(level, player, com.jo112ss.rpgmod.registry.ModRegistries.GOLEM.get());
                golemStates.clear();
                if (g != null) {
                    g.snapTo(c.getX() + 16.5, c.getY(), c.getZ() + 0.5, 90, 0);
                    if (dummy != null) {
                        dummy.setPos(c.getX() + 8.5, c.getY(), c.getZ() + 0.5);
                        g.setTarget(dummy);
                    }
                }
            }
            case 1000 -> RpgMod.LOGGER.info("[AUTOTEST] golem free run states={}", golemStates);
            default -> { }
        }
        if (t % 10 == 0) {
            for (var m : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(96),
                    m -> m instanceof net.minecraft.world.entity.monster.Enemy && !(m instanceof com.jo112ss.rpgmod.mob.boss.Golem)
                            && !(m instanceof com.jo112ss.rpgmod.mob.boss.GolemPillar) && m != dummy)) m.discard();
            player.setHealth(player.getMaxHealth());
            player.getFoodData().setFoodLevel(20);
        }
        if (g != null && g.isAlive() && dummy != null && dummy.isAlive() && g.getTarget() != dummy) g.setTarget(dummy);
        // film from the side, a fixed distance from the golem (not during the portraits)
        if (g != null && g.isAlive() && t > 56 && !(t >= 466 && t < 472)) {
            Vec3 at = g.position().add(-1, 0, -15);
            Vec3 look = g.position().add(0, g.getBbHeight() * 0.45, 0).subtract(at.add(0, 1.6, 0));
            float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
            float pitch = (float) -Math.toDegrees(Math.atan2(look.y, Math.hypot(look.x, look.z)));
            if (player.position().distanceToSqr(at) > 0.25 || t % 20 == 0) tp(player, at, yaw, pitch);
        }
        if (g != null && g.isAlive()) {
            if (t % 2 == 0) golemStates.merge(g.state().name(), 1, Integer::sum);
            if (t % 20 == 0) {
                RpgMod.LOGGER.info("[AUTOTEST] golem t={} state={} phase={} shield={} hp={}/{} dist={} max hit={} lost={}", t, g.state(), g.phase(), g.shielded(),
                        String.format("%.0f", g.getHealth()), String.format("%.0f", g.getMaxHealth()), String.format("%.1f", g.distanceTo(player)),
                        String.format("%.1f", maxHit), String.format("%.1f", lostHealth));
            }
        }
    }

    private static void gmove(com.jo112ss.rpgmod.mob.boss.Golem g, String m) {
        if (g != null) g.testMove(m);
    }

    private static void golemPortrait(ServerPlayer player, com.jo112ss.rpgmod.mob.boss.Golem g, int side) {
        if (g == null || !g.isAlive()) return;
        double d = g.getBbWidth() * 1.3 + 7;
        Vec3 at = g.position().add(side * d * 0.7, 0, side * d * 0.7);
        Vec3 look = g.position().add(0, g.getBbHeight() * 0.5, 0).subtract(at.add(0, 1.6, 0));
        float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(look.y, Math.hypot(look.x, look.z)));
        tp(player, at, yaw, pitch);
    }

    private static void move(com.jo112ss.rpgmod.mob.boss.KingSlime k, String m) {
        if (k != null) k.testMove(m);
    }

    /** Puts the camera beside the king, looking at it (the moves themselves are filmed from behind the player). */
    private static void portrait(ServerPlayer player, com.jo112ss.rpgmod.mob.boss.KingSlime k, int side) {
        if (k == null || !k.isAlive()) return;
        double d = k.getBbWidth() * 1.3 + 5;
        Vec3 at = k.position().add(side * d * 0.7, 0, side * d * 0.7);
        Vec3 look = k.position().add(0, k.getBbHeight() * 0.5, 0).subtract(at.add(0, 1.6, 0));
        float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(look.y, Math.hypot(look.x, look.z)));
        tp(player, at, yaw, pitch);
    }

    private static void tp(ServerPlayer p, Vec3 at, float yaw, float pitch) {
        p.teleportTo((ServerLevel) p.level(), at.x, at.y, at.z, java.util.Set.of(), yaw, pitch, false);
        p.setDeltaMovement(Vec3.ZERO);
    }

    private static Mob spawn(ServerLevel level, EntityType<? extends Mob> type, BlockPos at, int lv) {
        Mob m = type.create(level, EntitySpawnReason.COMMAND);
        m.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 90F, 0F);
        MobLevels.apply(m, lv);
        MobAi.onFirstLevel(m, lv);
        m.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        m.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET)); // no burning in daylight
        m.setPersistenceRequired();
        level.addFreshEntity(m);
        mobs.add(m);
        return m;
    }

    private static void clear(ServerLevel level, BlockPos center, int r) {
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) {
            for (int y = 0; y <= 7; y++) level.setBlock(center.offset(x, y, z), air, 2);
            if (level.getBlockState(center.offset(x, -1, z)).isAir()) level.setBlock(center.offset(x, -1, z), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
        }
    }

    static void tick(ServerLevel level, ServerPlayer player) {
        if (scene == null) return;
        t++;
        // (the sense scene checks that zombies find the player on their own)
        if (!java.util.Set.of("sense", "stairs", "exit", "downed", "leap").contains(scene)) for (Mob m : mobs) if (m.isAlive() && m.getTarget() == null) m.setTarget(player);
        switch (scene) {
            case "king" -> kingTick(level, player);
            case "golem" -> golemTick(level, player);
            case "downed" -> {
                // with the Resurrection mod: monsters leave a downed player alone, and come back once revived
                var cmd = level.getServer().createCommandSourceStack().withSuppressedOutput();
                String name = player.getGameProfile().name();
                if (t == 60) level.getServer().getCommands().performPrefixedCommand(cmd, "resurrection down " + name);
                if (t == 160) level.getServer().getCommands().performPrefixedCommand(cmd, "resurrection revive " + name);
                if (t % 20 == 0) {
                    RpgMod.LOGGER.info("[AUTOTEST] downed t={} downed={} targets={} health={}", t, com.jo112ss.rpgmod.compat.Downed.is(player),
                            mobs.stream().map(m -> m.getType().toShortString() + "=" + (m.getTarget() == player)).toList(), player.getHealth());
                }
            }
            case "dragonhp" -> {
                var d = dragon;
                if (d == null || !d.isAlive()) break;
                var src = level.damageSources().playerAttack(player);
                var pm = d.getPhaseManager();
                switch (t) {
                    case 60 -> RpgMod.LOGGER.info("[AUTOTEST] dragonhp max={} health={} toughness={}", d.getMaxHealth(), d.getHealth(),
                            com.jo112ss.rpgmod.mob.dragon.DragonDirector.toughness(d));
                    case 65, 68 -> {
                        float before = d.getHealth();
                        d.hurt(level, d.head, src, t == 65 ? 2000F : 100F);
                        RpgMod.LOGGER.info("[AUTOTEST] dragonhp flying hit at t={}: {} -> {} phase={}", t, before, d.getHealth(), pm.getCurrentPhase().getPhase());
                    }
                    case 75 -> pm.setPhase(net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase.SITTING_SCANNING);
                    case 79 -> {
                        float before = d.getHealth();
                        d.hurt(level, d.head, src, 2000F);
                        RpgMod.LOGGER.info("[AUTOTEST] dragonhp perched hit: {} -> {} phase={}", before, d.getHealth(), pm.getCurrentPhase().getPhase());
                    }
                    case 100 -> { // a wing, not the head: full damage now (vanilla: a quarter) - well after the last swing's hit window
                        float before = d.getHealth();
                        var parts = d.getSubEntities();
                        var wing = parts[parts.length - 1];
                        d.hurt(level, wing, src, 1000F);
                        RpgMod.LOGGER.info("[AUTOTEST] dragonhp wing hit 1000: {} -> {} ({} lost)", before, d.getHealth(), before - d.getHealth());
                    }
                    case 83 -> { // an arrow while it perches: lands now (vanilla: none)
                        float before = d.getHealth();
                        var arrow = net.minecraft.world.entity.EntityTypes.ARROW.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                        d.hurt(level, d.head, level.damageSources().arrow(arrow, player), 1000F);
                        RpgMod.LOGGER.info("[AUTOTEST] dragonhp perched arrow 1000: {} -> {} ({} lost) phase={}", before, d.getHealth(), before - d.getHealth(), pm.getCurrentPhase().getPhase());
                    }
                    case 85 -> RpgMod.LOGGER.info("[AUTOTEST] dragonhp after perched hit: phase={} health={}", pm.getCurrentPhase().getPhase(), d.getHealth());
                    default -> { }
                }
                if (t < 75 && pm.getCurrentPhase().getPhase() != net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase.HOVERING) {
                    pm.setPhase(net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase.HOVERING);
                }
            }
            case "dragon" -> {
                var d = dragon;
                if (d == null || !d.isAlive()) break;
                // keep it in front of the camera between the scripted attacks
                if (d.getPhaseManager().getCurrentPhase().getPhase() != net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase.CHARGING_PLAYER
                        && d.getPhaseManager().getCurrentPhase().getPhase() != net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase.HOVERING) {
                    d.getPhaseManager().setPhase(net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase.HOVERING);
                }
                switch (t) {
                    case 5 -> d.setHealth(d.getMaxHealth() * 0.55F);
                    case 30 -> com.jo112ss.rpgmod.mob.dragon.DragonDirector.testAttack(d, "volley");
                    case 90 -> com.jo112ss.rpgmod.mob.dragon.DragonDirector.testAttack(d, "gust");
                    case 140 -> {
                        com.jo112ss.rpgmod.mob.dragon.DragonDirector.testAttack(d, "orbs");
                        com.jo112ss.rpgmod.mob.dragon.DragonDirector.testAttack(d, "summon");
                    }
                    case 190 -> d.setHealth(d.getMaxHealth() * 0.2F);
                    case 230 -> com.jo112ss.rpgmod.mob.dragon.DragonDirector.testAttack(d, "rain");
                    case 290 -> com.jo112ss.rpgmod.mob.dragon.DragonDirector.testAttack(d, "charge");
                    default -> { }
                }
                if (t % 30 == 0) {
                    RpgMod.LOGGER.info("[AUTOTEST] dragon t={} level={} health={}/{} stage={} phase={} health lost={}", t, MobLevels.level(d),
                            String.format("%.0f", d.getHealth()), String.format("%.0f", d.getMaxHealth()),
                            com.jo112ss.rpgmod.mob.dragon.DragonDirector.stage(d), d.getPhaseManager().getCurrentPhase().getPhase(), String.format("%.1f", lostHealth));
                }
            }
            case "nether" -> {
                if (t % 40 == 0) {
                    RpgMod.LOGGER.info("[AUTOTEST] nether t={} novas={} lunges={} charges={} leaps={} followUps={} alive={} health lost={}", t,
                            com.jo112ss.rpgmod.mob.ai.NetherAi.novas, com.jo112ss.rpgmod.mob.ai.NetherAi.lunges, com.jo112ss.rpgmod.mob.ai.NetherAi.charges,
                            com.jo112ss.rpgmod.mob.ai.NetherAi.leaps, com.jo112ss.rpgmod.mob.ai.NetherAi.followUps,
                            mobs.stream().filter(Mob::isAlive).map(m -> m.getType().getDescriptionId().replace("entity.minecraft.", "")).toList(),
                            String.format("%.1f", lostHealth));
                }
            }
            case "archer" -> {
                // strafe sideways so the skeleton has to lead its shots
                double z = Math.sin(t * 0.06) * 4;
                Vec3 at = Vec3.atBottomCenterOf(c).add(0, 0, z);
                player.teleportTo(level, at.x, at.y, at.z, java.util.Set.of(), -90, 5, false);
                if (t % 50 == 0) RpgMod.LOGGER.info("[AUTOTEST] archer t={} arrows hit={} health lost={}", t, arrowsHit, String.format("%.1f", lostHealth));
            }
            case "pillar" -> {
                if (t % 40 == 0 && !mobs.isEmpty()) {
                    Mob z = mobs.get(0);
                    RpgMod.LOGGER.info("[AUTOTEST] pillar t={} zombie y {} -> {} (player y {})", t, String.format("%.1f", startY),
                            String.format("%.1f", z.getY()), String.format("%.1f", player.getY()));
                }
            }
            case "dig" -> {
                if (t % 40 == 0) {
                    int broken = 0;
                    for (int x = -2; x <= 2; x++) for (int zz = -2; zz <= 2; zz++) for (int y = 0; y <= 3; y++) {
                        boolean wall = Math.abs(x) == 2 || Math.abs(zz) == 2 || y == 3;
                        if (wall && level.getBlockState(c.offset(x, y, zz)).isAir()) broken++;
                    }
                    Mob z = mobs.isEmpty() ? null : mobs.get(0);
                    RpgMod.LOGGER.info("[AUTOTEST] dig t={} wall blocks broken={} zombie dist={} health lost={}", t, broken,
                            z == null ? -1 : String.format("%.1f", z.distanceTo(player)), String.format("%.1f", lostHealth));
                }
            }
            case "creeper" -> {
                if (t % 20 == 0) {
                    int broken = 0;
                    for (int z = -4; z <= 4; z++) for (int y = 0; y <= 2; y++) if (level.getBlockState(c.offset(2, y, z)).isAir()) broken++;
                    Mob cr = mobs.isEmpty() ? null : mobs.get(0);
                    RpgMod.LOGGER.info("[AUTOTEST] creeper t={} alive={} swell={} wall broken={} health lost={}", t, cr != null && cr.isAlive(),
                            cr instanceof Creeper k ? k.getSwellDir() : 0, broken, String.format("%.1f", lostHealth));
                }
            }
            case "stairs", "exit" -> {
                // the zombie object in the list may be stale (its chunk reloaded): look up the live one
                var live = level.getEntitiesOfClass(net.minecraft.world.entity.monster.zombie.Zombie.class, new net.minecraft.world.phys.AABB(c).inflate(40));
                if (t % 20 == 0 && !live.isEmpty()) {
                    Mob z = live.get(0);
                    RpgMod.LOGGER.info("[AUTOTEST] {} t={} zombie #{} at {} (relative x={} y={}) target={} mode={} dug={} dist={} health lost={}", scene, t, z.getId(),
                            z.blockPosition(), z.getBlockX() - c.getX(), z.getBlockY() - c.getY(), z.getTarget() == player,
                            com.jo112ss.rpgmod.mob.ai.SurfaceGoal.lastMode, com.jo112ss.rpgmod.mob.ai.BuilderGoal.dugBlocks,
                            String.format("%.1f", z.distanceTo(player)), String.format("%.1f", lostHealth));
                }
            }
            case "leap" -> {
                if (t % 20 == 0) {
                    RpgMod.LOGGER.info("[AUTOTEST] leap t={} difficulty {} {} health lost={}", t, level.getDifficulty(),
                            mobs.stream().map(m0 -> level.getEntity(m0.getUUID()) instanceof Mob live ? live : m0).map(m -> m.getType().getDescriptionId().replace("entity.minecraft.", "") + (m.isAlive() ? "" : "(gone " + m.getRemovalReason() + " hp " + m.getHealth() + ")")
                                    + " " + (m.getTarget() == player ? "target" : "none") + " sees=" + m.getSensing().hasLineOfSight(player)
                                    + " dist=" + String.format("%.1f", m.distanceTo(player)) + " y=" + String.format("%.1f", m.getY() - c.getY())).toList(),
                            String.format("%.1f", lostHealth));
                }
            }
            case "sense" -> {
                if (t % 20 == 0) {
                    int broken = 0;
                    for (int x = -2; x <= 2; x++) for (int zz = -2; zz <= 2; zz++) for (int y = 0; y <= 3; y++) {
                        boolean wall = Math.abs(x) == 2 || Math.abs(zz) == 2 || y == 3;
                        if (wall && level.getBlockState(c.offset(x, y, zz)).isAir()) broken++;
                    }
                    RpgMod.LOGGER.info("[AUTOTEST] sense t={} {} wall broken={} health lost={}", t,
                            mobs.stream().map(m -> (m.getTarget() == player ? "target" : "none") + " sees=" + m.getSensing().hasLineOfSight(player)
                                    + " dist=" + String.format("%.1f", m.distanceTo(player))).toList(), broken, String.format("%.1f", lostHealth));
                }
            }
            case "items" -> {
                if (t % 30 == 0) {
                    long tnt = level.getEntitiesOfClass(net.minecraft.world.entity.item.PrimedTnt.class, player.getBoundingBox().inflate(30)).size();
                    Mob pearl = mobs.size() > 1 ? mobs.get(1) : null;
                    RpgMod.LOGGER.info("[AUTOTEST] items t={} player moved {} pearl zombie moved {} primed tnt={} offhands={} health lost={}", t,
                            String.format("%.1f", player.position().distanceTo(Vec3.atBottomCenterOf(c))),
                            pearl == null ? "-" : String.format("%.1f", pearl.position().distanceTo(startPos)), tnt,
                            mobs.stream().map(m -> m.getOffhandItem().getItem().toString()).toList(), String.format("%.1f", lostHealth));
                }
            }
            default -> {
                if (t % 20 == 0) {
                    int webs = 0;
                    for (BlockPos p : BlockPos.betweenClosed(c.offset(-3, 0, -6), c.offset(3, 2, 6))) if (level.getBlockState(p).is(Blocks.COBWEB)) webs++;
                    RpgMod.LOGGER.info("[AUTOTEST] spider t={} webs={} health lost={}", t, webs, String.format("%.1f", lostHealth));
                }
            }
        }
    }

    static void end(ServerLevel level, ServerPlayer player) {
        if (king != null) king.discard();
        king = null;
        if (golem != null) golem.discard();
        golem = null;
        if (dummy != null) dummy.discard();
        dummy = null;
        if (savedTime >= 0) setTime(level, savedTime);
        savedTime = -1;
        if (dragon != null) dragon.discard();
        com.jo112ss.rpgmod.mob.dragon.DragonDirector.testPlayers = 0;
        dragon = null;
        for (Mob m : mobs) m.discard();
        mobs.clear();
        scene = null;
    }
}
