package com.jo112ss.rpgmod.dungeon;

import com.jo112ss.rpgmod.dungeon.Dungeons.Room;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Builds a dungeon in its slot: the rooms and the corridors between them, in one of a few stone
 * styles picked at random, each room with its own layout (pillars, a ring of columns, a raised
 * centre, braziers), lit so nothing spawns by itself. Forward doors are bars until the room is won.
 */
public final class DungeonBuilder {
    private DungeonBuilder() {}

    record Theme(BlockState wall, BlockState wallAccent, BlockState floor, BlockState floorAccent, BlockState ceiling, BlockState light, BlockState pillar) {}

    static final List<Theme> THEMES = List.of(
            new Theme(b(Blocks.DEEPSLATE_BRICKS), b(Blocks.CRACKED_DEEPSLATE_BRICKS), b(Blocks.POLISHED_DEEPSLATE), b(Blocks.DEEPSLATE_TILES), b(Blocks.DEEPSLATE_TILES),
                    b(Blocks.SEA_LANTERN), b(Blocks.CHISELED_DEEPSLATE)),
            new Theme(b(Blocks.STONE_BRICKS), b(Blocks.MOSSY_STONE_BRICKS), b(Blocks.STONE_BRICKS), b(Blocks.CRACKED_STONE_BRICKS), b(Blocks.STONE_BRICKS),
                    b(Blocks.GLOWSTONE), b(Blocks.CHISELED_STONE_BRICKS)),
            new Theme(b(Blocks.POLISHED_BLACKSTONE_BRICKS), b(Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS), b(Blocks.POLISHED_BLACKSTONE), b(Blocks.GILDED_BLACKSTONE),
                    b(Blocks.POLISHED_BLACKSTONE_BRICKS), b(Blocks.SHROOMLIGHT), b(Blocks.CHISELED_POLISHED_BLACKSTONE)),
            new Theme(b(Blocks.PRISMARINE_BRICKS), b(Blocks.PRISMARINE), b(Blocks.DARK_PRISMARINE), b(Blocks.PRISMARINE_BRICKS), b(Blocks.DARK_PRISMARINE),
                    b(Blocks.SEA_LANTERN), b(Blocks.DARK_PRISMARINE)),
            new Theme(b(Blocks.CUT_SANDSTONE), b(Blocks.CHISELED_SANDSTONE), b(Blocks.SMOOTH_SANDSTONE), b(Blocks.CUT_SANDSTONE), b(Blocks.SMOOTH_SANDSTONE),
                    b(Blocks.OCHRE_FROGLIGHT), b(Blocks.CHISELED_SANDSTONE)),
            new Theme(b(Blocks.MUD_BRICKS), b(Blocks.PACKED_MUD), b(Blocks.TUFF_BRICKS), b(Blocks.POLISHED_TUFF), b(Blocks.MUD_BRICKS),
                    b(Blocks.PEARLESCENT_FROGLIGHT), b(Blocks.CHISELED_TUFF_BRICKS)));

    private static BlockState b(Block block) {
        return block.defaultBlockState();
    }

    public static final BlockState DOOR = Blocks.IRON_BARS.defaultBlockState();
    private static final int FLAGS = Block.UPDATE_CLIENTS;

    /** Builds the whole dungeon of {@code run}. */
    public static void build(ServerLevel level, DungeonData.Run run) {
        RandomSource rnd = RandomSource.create(run.seed);
        Theme t = THEMES.get(rnd.nextInt(THEMES.size()));
        int ox = Dungeons.originX(run.slot);
        List<Room> rooms = run.rooms();
        for (int i = 0; i < rooms.size(); i++) {
            Room r = rooms.get(i);
            shell(level, ox, r, t, rnd);
            if (i == 0) entrance(level, ox, t);
            else decorate(level, ox, r, t, rnd);
        }
        // corridors last: they cut the doorways through the walls the rooms put up
        for (int i = 0; i + 1 < rooms.size(); i++) corridor(level, ox, rooms.get(i).z1() + 1, rooms.get(i + 1).z0() - 1, t, i > 0);
    }

    private static void set(ServerLevel level, int x, int y, int z, BlockState s) {
        level.setBlock(new BlockPos(x, y, z), s, FLAGS);
    }

    private static void fill(ServerLevel level, int x0, int y0, int z0, int x1, int y1, int z1, BlockState s) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++)
            for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++)
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) set(level, x, y, z, s);
    }

    /** Floor, walls and ceiling round the room's interior, with lights set in the ceiling. */
    private static void shell(ServerLevel level, int ox, Room r, Theme t, RandomSource rnd) {
        int y0 = Dungeons.FLOOR - 1, y1 = Dungeons.FLOOR + r.height();
        int x0 = ox - r.half() - 1, x1 = ox + r.half() + 1, z0 = r.z0() - 1, z1 = r.z1() + 1;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                int dx = x - ox, dz = z - r.zMid();
                // floor: a checker of the two floor blocks, the accent in a cross through the middle
                BlockState floor = (dx == 0 || dz == 0) ? t.floorAccent() : t.floor();
                // glowing tiles in the floor: the ceiling is too high in the big rooms to light it
                if (!edge && Math.floorMod(dx, 6) == 3 && Math.floorMod(dz - r.z0(), 6) == 3) floor = t.light();
                set(level, x, y0, z, edge ? t.wall() : floor);
                boolean lamp = !edge && Math.floorMod(dx, 4) == 0 && Math.floorMod(dz, 4) == 0;
                set(level, x, y1, z, lamp ? t.light() : t.ceiling());
                if (edge) for (int y = y0 + 1; y < y1; y++) set(level, x, y, z, rnd.nextFloat() < 0.12F ? t.wallAccent() : t.wall());
            }
        }
        // lights along the walls, at head height
        for (int z = r.z0() + 2; z <= r.z1() - 2; z += 4) {
            set(level, x0, Dungeons.FLOOR + 2, z, t.light());
            set(level, x1, Dungeons.FLOOR + 2, z, t.light());
        }
    }

    /** A corridor from z0 to z1, with openings into the rooms at both ends; a barred door at the start unless it leaves the entrance. */
    private static void corridor(ServerLevel level, int ox, int z0, int z1, Theme t, boolean barred) {
        int y0 = Dungeons.FLOOR - 1, y1 = Dungeons.FLOOR + 3;
        for (int z = z0; z <= z1; z++) {
            for (int x = ox - 2; x <= ox + 2; x++) {
                set(level, x, y0, z, Math.abs(x - ox) <= 1 ? t.floorAccent() : t.wall());
                set(level, x, y1, z, x == ox && (z - z0) % 3 == 1 ? t.light() : t.ceiling());
                for (int y = y0 + 1; y < y1; y++) set(level, x, y, z, Math.abs(x - ox) == 2 ? t.wall() : b(Blocks.AIR));
            }
        }
        // the doorways through the two room walls (the walls are the corridor's ends)
        fill(level, ox - 1, Dungeons.FLOOR, z0, ox + 1, Dungeons.FLOOR + 2, z0, barred ? DOOR : b(Blocks.AIR));
        fill(level, ox - 1, Dungeons.FLOOR, z1, ox + 1, Dungeons.FLOOR + 2, z1, b(Blocks.AIR));
    }

    /** Opens the barred door out of a room. */
    public static void openDoor(ServerLevel level, DungeonData.Run run, Room r) {
        int ox = Dungeons.originX(run.slot);
        int z = r.z1() + 1;
        fill(level, ox - 1, Dungeons.FLOOR, z, ox + 1, Dungeons.FLOOR + 2, z, b(Blocks.AIR));
    }

    /** The entrance hall: the exit circle at the back, banners of light along the sides. */
    private static void entrance(ServerLevel level, int ox, Theme t) {
        int y = Dungeons.FLOOR - 1;
        for (int x = ox - 1; x <= ox + 1; x++) for (int z = 0; z <= 2; z++) set(level, x, y, z, b(Blocks.CRYING_OBSIDIAN));
        set(level, ox, y, 1, b(Blocks.LODESTONE));
        set(level, ox - 1, Dungeons.FLOOR, 0, b(Blocks.SOUL_LANTERN));
        set(level, ox + 1, Dungeons.FLOOR, 0, b(Blocks.SOUL_LANTERN));
    }

    /** One of a few layouts for a fighting room. */
    private static void decorate(ServerLevel level, int ox, Room r, Theme t, RandomSource rnd) {
        int zm = r.zMid(), h = r.height();
        switch (r.last() ? 2 + rnd.nextInt(2) : rnd.nextInt(4)) { // the boss rooms stay open
            case 0 -> { // four thick pillars
                int d = Math.max(3, r.half() / 2);
                for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2) {
                    int cx = ox + sx * d, cz = zm + sz * d;
                    fill(level, cx, Dungeons.FLOOR, cz, cx + sx, Dungeons.FLOOR + h - 1, cz + sz, t.pillar());
                }
            }
            case 1 -> { // a ring of thin columns
                int rad = r.half() - 2;
                for (int k = 0; k < 8; k++) {
                    double a = Math.PI * 2 * k / 8 + Math.PI / 8;
                    int cx = ox + (int) Math.round(Math.cos(a) * rad), cz = zm + (int) Math.round(Math.sin(a) * rad);
                    fill(level, cx, Dungeons.FLOOR, cz, cx, Dungeons.FLOOR + h - 1, cz, t.pillar());
                }
            }
            case 2 -> { // a low raised centre with steps all round
                int d = Math.max(2, r.half() / 3);
                for (int x = ox - d; x <= ox + d; x++) for (int z = zm - d; z <= zm + d; z++) {
                    boolean rim = Math.abs(x - ox) == d || Math.abs(z - zm) == d;
                    set(level, x, Dungeons.FLOOR, z, rim ? slab(t) : t.floorAccent());
                }
            }
            default -> { // braziers in the corners
                int d = r.half() - 1;
                for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2) {
                    int cx = ox + sx * d, cz = zm + sz * d;
                    set(level, cx, Dungeons.FLOOR, cz, t.pillar());
                    set(level, cx, Dungeons.FLOOR + 1, cz, b(Blocks.SOUL_LANTERN));
                }
            }
        }
    }

    private static BlockState slab(Theme t) {
        Block w = t.floor().getBlock();
        if (w == Blocks.POLISHED_DEEPSLATE) return b(Blocks.POLISHED_DEEPSLATE_SLAB);
        if (w == Blocks.STONE_BRICKS) return b(Blocks.STONE_BRICK_SLAB);
        if (w == Blocks.POLISHED_BLACKSTONE) return b(Blocks.POLISHED_BLACKSTONE_SLAB);
        if (w == Blocks.DARK_PRISMARINE) return b(Blocks.DARK_PRISMARINE_SLAB);
        if (w == Blocks.SMOOTH_SANDSTONE) return b(Blocks.SMOOTH_SANDSTONE_SLAB);
        return b(Blocks.TUFF_BRICK_SLAB);
    }

    /** Where a room's monsters appear: spread over its far half. */
    static BlockPos spawnSpot(ServerLevel level, int slot, Room r, RandomSource rnd, List<? extends net.minecraft.world.entity.Entity> players) {
        int ox = Dungeons.originX(slot);
        BlockPos best = new BlockPos(ox, Dungeons.FLOOR, r.zMid() + r.half() / 2);
        for (int tries = 0; tries < 40; tries++) {
            int x = ox - (r.half() - 2) + rnd.nextInt(2 * (r.half() - 2) + 1);
            int z = r.z0() + 2 + rnd.nextInt(Math.max(1, r.z1() - r.z0() - 3));
            BlockPos p = new BlockPos(x, Dungeons.FLOOR, z);
            while (!level.getBlockState(p).isAir() && p.getY() < Dungeons.FLOOR + 2) p = p.above(); // on top of a raised centre
            if (!level.getBlockState(p).isAir() || !level.getBlockState(p.above()).isAir() || !level.getBlockState(p.above(2)).isAir()) continue;
            boolean near = false;
            for (var e : players) if (e.distanceToSqr(p.getX() + 0.5, p.getY(), p.getZ() + 0.5) < 8 * 8) near = true;
            if (!near) return p;
            best = p;
        }
        return best;
    }

    /** Clears the dungeon's blocks (after the run, so the slot holds nothing). */
    public static void clear(ServerLevel level, DungeonData.Run run) {
        int ox = Dungeons.originX(run.slot);
        List<Room> rooms = run.rooms();
        Room last = rooms.get(rooms.size() - 1);
        int half = 0, top = 0;
        for (Room r : rooms) {
            half = Math.max(half, r.half() + 2);
            top = Math.max(top, r.height() + 1);
        }
        fill(level, ox - half, Dungeons.FLOOR - 1, -2, ox + half, Dungeons.FLOOR + top, last.z1() + 2, b(Blocks.AIR));
    }
}
