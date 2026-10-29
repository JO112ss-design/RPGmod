package com.jo112ss.rpgmod.mob.boss;

import com.jo112ss.rpgmod.RpgMod;
import com.jo112ss.rpgmod.mob.MobLevels;
import com.jo112ss.rpgmod.registry.ModRegistries;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.levelgen.Heightmap;

/** The boss roster and what goes with any of them: their keys, their entrance, a spot near a player. */
public final class Bosses {
    private Bosses() {}

    /** The four bosses a dungeon's last room (or an offering) may bring. */
    public static List<EntityType<? extends Mob>> all() {
        return List.of(ModRegistries.KING_SLIME.get(), ModRegistries.GOLEM.get(), ModRegistries.LICH.get(), ModRegistries.SPIDER_QUEEN.get());
    }

    /** The lang key part of a boss ({@code king_slime}, {@code golem}, {@code lich}, {@code spider_queen}). */
    public static String key(EntityType<?> type) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath();
    }

    /** A boss's entrance: its arrival effect, a crash and a roar. */
    public static void arrivalFx(ServerLevel level, Mob boss) {
        boolean golem = boss instanceof Golem;
        String spawnFx = boss instanceof Golem ? "golem_spawn" : boss instanceof Lich || boss instanceof AbyssWither ? "lich_spawn" : boss instanceof SpiderQueen ? "queen_spawn" : "king_spawn";
        if (boss instanceof AbyssWither) level.playSound(null, boss.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 3.0F, 0.7F);
        com.jo112ss.rpgmod.job.fx.ServerFx.of(spawnFx, boss.position()).a(boss.getBbWidth()).send(level, 128);
        level.playSound(null, boss.blockPosition(), golem ? SoundEvents.STONE_BREAK : SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 4.0F, 0.3F);
        level.playSound(null, boss.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 3.0F, golem ? 0.4F : 0.6F);
    }

    /**
     * Dev tests: a boss on a free spot 20-30 blocks from the player (on the surface near their height,
     * else an open space at their height), with its entrance. Null if there is no room.
     */
    public static <T extends Mob> T spawnNear(ServerLevel level, ServerPlayer p, EntityType<T> type) {
        T boss = type.create(level, EntitySpawnReason.EVENT);
        if (boss == null) return null;
        var rnd = level.getRandom();
        for (int pass = 0; pass < 3; pass++) {
            for (int i = 0; i < 24; i++) {
                double a = rnd.nextDouble() * Math.PI * 2, r = 20 + rnd.nextDouble() * 10;
                int x = (int) Math.floor(p.getX() + Math.cos(a) * r), z = (int) Math.floor(p.getZ() + Math.sin(a) * r);
                int y;
                if (pass == 1) {
                    y = groundNear(level, x, (int) Math.floor(p.getY()), z);
                    if (y == Integer.MIN_VALUE) continue;
                } else {
                    y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                    if (pass == 0 && Math.abs(y - p.getY()) > 16) continue;
                }
                boss.snapTo(x + 0.5, y, z + 0.5, rnd.nextFloat() * 360, 0);
                BlockPos below = BlockPos.containing(x, y - 1, z);
                if (!level.getFluidState(below).isEmpty() && pass < 2) continue;
                if (!level.noCollision(boss) || level.containsAnyLiquid(boss.getBoundingBox())) continue;
                boss.setPersistenceRequired();
                level.addFreshEntity(boss);
                arrivalFx(level, boss);
                RpgMod.LOGGER.info("[boss] {} Lv{} near {} at {}", key(type), MobLevels.level(boss), p.getGameProfile().name(), boss.blockPosition());
                return boss;
            }
        }
        return null;
    }

    public static KingSlime spawnNear(ServerLevel level, ServerPlayer p) {
        return spawnNear(level, p, ModRegistries.KING_SLIME.get());
    }

    /** Floor of an open space within 6 blocks above/below {@code y} at x/z, or MIN_VALUE. */
    private static int groundNear(ServerLevel level, int x, int y, int z) {
        for (int dy = 0; dy <= 6; dy++) {
            for (int s : new int[] {-dy, dy}) {
                BlockPos at = new BlockPos(x, y + s, z);
                if (level.getBlockState(at).isAir() && !level.getBlockState(at.below()).getCollisionShape(level, at.below()).isEmpty()) {
                    return y + s;
                }
            }
        }
        return Integer.MIN_VALUE;
    }
}
