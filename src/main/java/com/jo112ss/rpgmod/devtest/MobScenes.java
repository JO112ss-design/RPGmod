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
            case "king" -> 9;
            case "sense" -> 8;
            case "stairs" -> 10;
            case "exit" -> 11;
            default -> 5;
        };
        c = BlockPos.containing(arena).offset(40, 0, idx * 24 - 60);
        clear(level, c, name.equals("king") ? 22 : 10);
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
                if (name.equals("exit")) {
                    // a walkable ramp up to the open air at the far end of the cave...
                    for (int i = 0; i <= 11; i++) for (int y = 1; y <= 3; y++) level.setBlock(c.offset(17 + i, i + y, 0), Blocks.AIR.defaultBlockState(), 2);
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
            case "king" -> {
                tp(player, Vec3.atBottomCenterOf(c), -90, -6);
                savedTime = level.getOverworldClockTime();
                level.setData(com.jo112ss.rpgmod.registry.ModRegistries.BOSS_DAY, -1L);
                setTime(level, 9 * 24000L + 14000); // night of day 10: blood moon
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
                k = king = findKing(level, player);
                RpgMod.LOGGER.info("[AUTOTEST] king natural spawn={} bloodMoon={} day={} dist={} level={} hp={}", k != null,
                        com.jo112ss.rpgmod.mob.boss.BloodMoon.active(), com.jo112ss.rpgmod.mob.boss.BloodMoon.day(level),
                        k == null ? -1 : String.format("%.1f", k.distanceTo(player)), k == null ? -1 : MobLevels.level(k),
                        k == null ? -1 : String.format("%.0f", k.effectiveMaxHealth()));
                if (k == null) k = king = com.jo112ss.rpgmod.mob.boss.BloodMoon.spawnNear(level, player);
                if (k != null) {
                    com.jo112ss.rpgmod.mob.boss.BloodMoon.testKeep = true;
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
                var ev = new net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent(player, c, null);
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(ev);
                RpgMod.LOGGER.info("[AUTOTEST] king sleep blocked={}", ev.getProblem() != null);
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
                k = king = com.jo112ss.rpgmod.mob.boss.BloodMoon.spawnNear(level, player);
                kingMoves.clear();
                if (k != null) {
                    k.snapTo(c.getX() + 14.5, c.getY(), c.getZ() + 0.5, 90, 0);
                    if (dummy != null) {
                        dummy.setPos(c.getX() + 8.5, c.getY(), c.getZ() + 0.5);
                        k.setTarget(dummy);
                    }
                }
            }
            case 840 -> {
                RpgMod.LOGGER.info("[AUTOTEST] king free run states={}", kingMoves);
                com.jo112ss.rpgmod.mob.boss.BloodMoon.testKeep = false;
                setTime(level, 9 * 24000L + 23300); // dawn
            }
            case 930 -> RpgMod.LOGGER.info("[AUTOTEST] king left at dawn={} bloodMoon={}", k == null || k.isRemoved(), com.jo112ss.rpgmod.mob.boss.BloodMoon.active());
            case 935 -> setTime(level, 10 * 24000L + 300); // morning of day 11: the day title
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
        if (!java.util.Set.of("sense", "stairs", "exit").contains(scene)) for (Mob m : mobs) if (m.isAlive() && m.getTarget() == null) m.setTarget(player);
        switch (scene) {
            case "king" -> kingTick(level, player);
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
                if (t % 20 == 0 && !mobs.isEmpty()) {
                    Mob z = mobs.get(0);
                    RpgMod.LOGGER.info("[AUTOTEST] {} t={} zombie at {} (relative x={} y={}) target={} mode={} dug={} dist={} health lost={}", scene, t,
                            z.blockPosition(), z.getBlockX() - c.getX(), z.getBlockY() - c.getY(), z.getTarget() == player,
                            com.jo112ss.rpgmod.mob.ai.SurfaceGoal.lastMode, com.jo112ss.rpgmod.mob.ai.BuilderGoal.dugBlocks,
                            String.format("%.1f", z.distanceTo(player)), String.format("%.1f", lostHealth));
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
        if (dummy != null) dummy.discard();
        dummy = null;
        com.jo112ss.rpgmod.mob.boss.BloodMoon.testKeep = false;
        if (savedTime >= 0) setTime(level, savedTime);
        savedTime = -1;
        if (dragon != null) dragon.discard();
        dragon = null;
        for (Mob m : mobs) m.discard();
        mobs.clear();
        scene = null;
    }
}
