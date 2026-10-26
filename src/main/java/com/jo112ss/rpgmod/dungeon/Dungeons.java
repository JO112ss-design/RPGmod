package com.jo112ss.rpgmod.dungeon;

import com.jo112ss.rpgmod.RpgMod;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Dungeons: every run is built in its own slot of a dark, empty dimension (2048 blocks apart along x),
 * as a line of rooms going south from the entrance hall. A key opens four rooms (three with a strong
 * monster, the last with a boss or two or three strong ones); a boss offering opens a single arena.
 */
public final class Dungeons {
    private Dungeons() {}

    public static final ResourceKey<Level> DIM = ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath(RpgMod.MODID, "dungeon"));

    public static final int SPACING = 2048, FLOOR = 64;

    /** What was offered: a key's run of four rooms, or straight to a boss. */
    public enum Kind {
        RUN, KING_SLIME, GOLEM;

        public boolean boss() {
            return this != RUN;
        }
    }

    /** A room's box: interior x within +-{@code half} of the centre line, z from z0 to z1, {@code height} blocks tall. */
    public record Room(int half, int z0, int z1, int height, boolean last) {
        public int zMid() {
            return (z0 + z1) / 2;
        }
    }

    public static final int CORRIDOR = 6;

    /** The entrance hall, then the rooms (each about three times the floor of the first version). */
    public static List<Room> layout(Kind kind) {
        List<Room> out = new ArrayList<>();
        out.add(new Room(6, 0, 12, 7, false));
        int z = 13 + CORRIDOR + 1;
        if (kind == Kind.RUN) {
            for (int i = 0; i < 3; i++) {
                out.add(new Room(13, z, z + 26, 9, false));
                z += 27 + CORRIDOR + 1;
            }
            out.add(new Room(20, z, z + 40, 14, true));
        } else {
            out.add(new Room(25, z, z + 50, 16, true));
        }
        return out;
    }

    public static boolean isDungeon(Level level) {
        return level != null && level.dimension() == DIM;
    }

    public static ServerLevel level(net.minecraft.server.MinecraftServer server) {
        return server.getLevel(DIM);
    }

    public static int originX(int slot) {
        return slot * SPACING + SPACING / 2;
    }

    /** The slot a dungeon position belongs to. */
    public static int slotAt(double x) {
        return (int) Math.floor(x / SPACING);
    }

    public static AABB area(int slot) {
        int ox = originX(slot);
        return new AABB(ox - 64, FLOOR - 16, -16, ox + 64, FLOOR + 48, 300);
    }

    /** Interior of a room (where standing makes it start). */
    public static AABB interior(int slot, Room r) {
        int ox = originX(slot);
        return new AABB(ox - r.half(), FLOOR, r.z0(), ox + r.half() + 1, FLOOR + r.height(), r.z1() + 1);
    }

    public static Vec3 entry(int slot) {
        return new Vec3(originX(slot) + 0.5, FLOOR, 8.5);
    }

    /** The exit circle in the entrance hall: stand on it to go back. */
    public static boolean onExit(int slot, Entity e) {
        int ox = originX(slot);
        return Math.abs(e.getX() - (ox + 0.5)) <= 1.5 && e.getZ() >= 0 && e.getZ() <= 3 && Math.abs(e.getY() - FLOOR) < 1.5;
    }

    public static BlockPos exitCentre(int slot) {
        return new BlockPos(originX(slot), FLOOR - 1, 1);
    }

    /** Players inside the dungeon of this slot. */
    public static List<net.minecraft.server.level.ServerPlayer> players(ServerLevel dungeon, int slot) {
        AABB a = area(slot);
        List<net.minecraft.server.level.ServerPlayer> out = new ArrayList<>();
        for (var p : dungeon.players()) if (a.contains(p.position()) && p.isAlive() && !p.isSpectator()) out.add(p);
        return out;
    }

    /** For the bosses: how many players they are fought by (the run's party in a dungeon, else everyone online). */
    public static int bossPlayers(Entity boss, ServerLevel level) {
        if (isDungeon(level)) return Math.max(1, players(level, slotAt(boss.getX())).size());
        return Math.max(1, level.getServer().getPlayerList().getPlayerCount());
    }
}
