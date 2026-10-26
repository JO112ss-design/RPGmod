package com.jo112ss.rpgmod.dungeon;

import com.jo112ss.rpgmod.dungeon.Dungeons.Room;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Builds a dungeon in its slot, only for the eye: one of thirteen stone styles for the whole run,
 * and for every room its own shape (square, octagonal, round or a cross), floor pattern, layout
 * (pillars, a ring of columns, a raised centre, braziers, colonnades, an obelisk, ruins, water
 * channels, a raised terrace) and dressing (chandeliers, banners, moss, rubble). Lit so nothing
 * spawns by itself. Forward doors are bars until the room is won.
 */
public final class DungeonBuilder {
    private DungeonBuilder() {}

    /** A stone style: blocks for walls, trim bands, floors, ceiling, lights, pillars, steps, hanging lights, banners, rubble, an optional carpet. */
    record Theme(String name, BlockState wall, BlockState wallAccent, BlockState trim, BlockState floor, BlockState floorAlt, BlockState floorAccent,
            BlockState ceiling, BlockState light, BlockState pillar, BlockState slab, BlockState lantern, String banner, BlockState rubble, BlockState carpet) {}

    static final List<Theme> THEMES = List.of(
            theme("deepslate", "deepslate_bricks", "cracked_deepslate_bricks", "polished_deepslate", "polished_deepslate", "deepslate_tiles", "chiseled_deepslate",
                    "deepslate_tiles", "sea_lantern", "polished_basalt", "deepslate_tile_slab", "soul_lantern", "cyan", "cobbled_deepslate", null),
            theme("old_stone", "stone_bricks", "mossy_stone_bricks", "chiseled_stone_bricks", "stone_bricks", "cracked_stone_bricks", "mossy_cobblestone",
                    "stone_bricks", "glowstone", "chiseled_stone_bricks", "stone_brick_slab", "lantern", "green", "mossy_cobblestone", "moss_carpet"),
            theme("blackstone", "polished_blackstone_bricks", "cracked_polished_blackstone_bricks", "gilded_blackstone", "polished_blackstone",
                    "polished_blackstone_bricks", "gilded_blackstone", "polished_blackstone_bricks", "shroomlight", "chiseled_polished_blackstone",
                    "polished_blackstone_slab", "lantern", "black", "blackstone", null),
            theme("sunken", "prismarine_bricks", "prismarine", "dark_prismarine", "dark_prismarine", "prismarine_bricks", "prismarine", "dark_prismarine",
                    "sea_lantern", "dark_prismarine", "dark_prismarine_slab", "soul_lantern", "light_blue", "prismarine", null),
            theme("desert", "cut_sandstone", "chiseled_sandstone", "chiseled_sandstone", "smooth_sandstone", "cut_sandstone", "chiseled_sandstone",
                    "smooth_sandstone", "ochre_froglight", "cut_sandstone", "smooth_sandstone_slab", "lantern", "orange", "sandstone", null),
            theme("mud", "mud_bricks", "packed_mud", "chiseled_tuff", "tuff_bricks", "polished_tuff", "chiseled_tuff_bricks", "mud_bricks",
                    "pearlescent_froglight", "chiseled_tuff_bricks", "tuff_brick_slab", "lantern", "brown", "tuff", null),
            theme("nether", "nether_bricks", "cracked_nether_bricks", "chiseled_nether_bricks", "red_nether_bricks", "nether_bricks", "chiseled_nether_bricks",
                    "nether_bricks", "shroomlight", "red_nether_bricks", "nether_brick_slab", "soul_lantern", "red", "cracked_nether_bricks", null),
            theme("end", "purpur_block", "end_stone_bricks", "purpur_pillar", "end_stone_bricks", "purpur_block", "purpur_pillar", "purpur_block",
                    "pearlescent_froglight", "purpur_pillar", "purpur_slab", "soul_lantern", "purple", "end_stone", null),
            theme("ice", "packed_ice", "blue_ice", "polished_diorite", "polished_diorite", "snow_block", "calcite", "packed_ice", "sea_lantern", "blue_ice",
                    "polished_diorite_slab", "soul_lantern", "light_blue", "snow_block", null),
            theme("copper", "waxed_cut_copper", "waxed_oxidized_cut_copper", "waxed_chiseled_copper", "polished_tuff", "waxed_weathered_cut_copper",
                    "waxed_chiseled_copper", "waxed_cut_copper", "ochre_froglight", "waxed_oxidized_cut_copper", "waxed_cut_copper_slab", "lantern", "orange",
                    "waxed_exposed_cut_copper", null),
            theme("temple", "quartz_bricks", "chiseled_quartz_block", "quartz_pillar", "smooth_quartz", "quartz_bricks", "chiseled_quartz_block", "smooth_quartz",
                    "sea_lantern", "quartz_pillar", "smooth_quartz_slab", "lantern", "yellow", "quartz_block", null),
            theme("crypt", "smooth_basalt", "bone_block", "polished_basalt", "polished_deepslate", "deepslate_tiles", "bone_block", "smooth_basalt",
                    "verdant_froglight", "bone_block", "polished_deepslate_slab", "soul_lantern", "white", "bone_block", null),
            theme("resin", "resin_bricks", "chiseled_resin_bricks", "chiseled_resin_bricks", "polished_tuff", "tuff_bricks", "chiseled_resin_bricks", "resin_bricks",
                    "ochre_froglight", "chiseled_resin_bricks", "resin_brick_slab", "lantern", "orange", "resin_bricks", "pale_moss_carpet"));

    private static Theme theme(String name, String wall, String wallAccent, String trim, String floor, String floorAlt, String floorAccent, String ceiling,
            String light, String pillar, String slab, String lantern, String banner, String rubble, String carpet) {
        return new Theme(name, b(wall), b(wallAccent), b(trim), b(floor), b(floorAlt), b(floorAccent), b(ceiling), b(light), b(pillar), b(slab), b(lantern),
                banner, b(rubble), carpet != null ? b(carpet) : null);
    }

    /** A block by its id (falls back to stone bricks if this version has no such block). */
    private static BlockState b(String id) {
        Block block = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(id));
        return block == null || block == Blocks.AIR ? Blocks.STONE_BRICKS.defaultBlockState() : block.defaultBlockState();
    }

    private static BlockState b(Block block) {
        return block.defaultBlockState();
    }

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    public static final BlockState DOOR = Blocks.IRON_BARS.defaultBlockState();
    private static final int FLAGS = Block.UPDATE_CLIENTS;

    /** Dev tests: the style to use (null = by chance). */
    public static Integer forceTheme;

    public enum Shape { SQUARE, OCTAGON, ROUND, CROSS }

    public enum Layout { PILLARS, RING, DAIS, BRAZIERS, COLONNADE, OBELISK, RUINS, CHANNELS, TERRACE, OPEN }

    /** What a room ended up looking like (for the logs). */
    public static String lastDescription = "";

    /** Builds the whole dungeon of {@code run} at once. */
    public static void build(ServerLevel level, DungeonData.Run run) {
        for (Runnable step : steps(level, run)) step.run();
    }

    /**
     * The dungeon in steps, to spread over a few ticks: the entrance, the first room and the way
     * between them first (so it can be entered at once), then one room and its corridor at a time.
     */
    public static List<Runnable> steps(ServerLevel level, DungeonData.Run run) {
        RandomSource rnd = RandomSource.create(run.seed);
        Theme t = THEMES.get(forceTheme != null ? Math.floorMod(forceTheme, THEMES.size()) : rnd.nextInt(THEMES.size()));
        int ox = Dungeons.originX(run.slot);
        List<Room> rooms = run.rooms();
        Shape[] shapes = new Shape[rooms.size()];
        Layout[] layouts = new Layout[rooms.size()];
        StringBuilder desc = new StringBuilder(t.name());
        for (int i = 1; i < rooms.size(); i++) {
            shapes[i] = Shape.values()[rnd.nextInt(Shape.values().length)];
            layouts[i] = pickLayout(rooms.get(i), rnd);
            desc.append(" | ").append(shapes[i]).append('/').append(layouts[i]);
        }
        lastDescription = desc.toString();
        long seed = run.seed;
        List<Runnable> out = new java.util.ArrayList<>();
        for (int i = 0; i < rooms.size(); i++) {
            int k = i;
            Runnable room = () -> {
                Room r = rooms.get(k);
                RandomSource rr = RandomSource.create(seed * 31 + k);
                if (k == 0) {
                    shell(level, ox, r, t, Shape.SQUARE, rr);
                    entrance(level, ox, r, t);
                } else {
                    shell(level, ox, r, t, shapes[k], rr);
                    layout(level, ox, r, t, shapes[k], layouts[k], rr);
                    dress(level, ox, r, t, shapes[k], rr);
                }
                // the corridor from the room before: it cuts the doorways through both walls
                if (k > 0) corridor(level, ox, rooms.get(k - 1).z1() + 1, r.z0() - 1, t, k - 1 > 0);
            };
            if (k <= 1 && !out.isEmpty()) {
                Runnable first = out.remove(0);
                out.add(() -> {
                    first.run();
                    room.run();
                });
            } else {
                out.add(room);
            }
        }
        return out;
    }

    private static Layout pickLayout(Room r, RandomSource rnd) {
        // the last room (bosses, two or three strong ones) stays open in the middle
        Layout[] pool = r.last() ? new Layout[] {Layout.DAIS, Layout.BRAZIERS, Layout.CHANNELS, Layout.TERRACE, Layout.OPEN, Layout.RING}
                : new Layout[] {Layout.PILLARS, Layout.RING, Layout.DAIS, Layout.BRAZIERS, Layout.COLONNADE, Layout.OBELISK, Layout.RUINS, Layout.CHANNELS, Layout.TERRACE};
        return pool[rnd.nextInt(pool.length)];
    }

    private static void set(ServerLevel level, int x, int y, int z, BlockState s) {
        level.setBlock(new BlockPos(x, y, z), s, FLAGS);
    }

    private static void fill(ServerLevel level, int x0, int y0, int z0, int x1, int y1, int z1, BlockState s) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++)
            for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++)
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) set(level, x, y, z, s);
    }

    // ================================================================== shapes

    private static double halfLen(Room r) {
        return (r.z1() - r.z0()) / 2.0;
    }

    /** Whether (x, z) is floor of the room in this shape. */
    static boolean inside(int ox, Room r, Shape shape, int x, int z) {
        int dx = x - ox;
        if (Math.abs(dx) > r.half() || z < r.z0() || z > r.z1()) return false;
        double u = dx / (r.half() + 0.5), v = (z - (r.z0() + r.z1()) / 2.0) / (halfLen(r) + 0.5);
        return switch (shape) {
            case SQUARE -> true;
            case OCTAGON -> Math.abs(u) + Math.abs(v) <= 1.45;
            case ROUND -> u * u + v * v <= 1.0;
            case CROSS -> Math.abs(u) <= 0.5 || Math.abs(v) <= 0.5;
        };
    }

    /** Walls hug the shape; outside them is left empty. */
    private static void shell(ServerLevel level, int ox, Room r, Theme t, Shape shape, RandomSource rnd) {
        int y0 = Dungeons.FLOOR - 1, y1 = Dungeons.FLOOR + r.height();
        int x0 = ox - r.half() - 1, x1 = ox + r.half() + 1, z0 = r.z0() - 1, z1 = r.z1() + 1;
        int pattern = rnd.nextInt(5);
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (inside(ox, r, shape, x, z)) {
                    set(level, x, y0, z, floorAt(ox, r, t, pattern, x, z));
                    int dx = x - ox, dz = z - r.zMid();
                    boolean lamp = Math.floorMod(dx, 4) == 0 && Math.floorMod(dz, 4) == 0;
                    set(level, x, y1, z, lamp ? t.light() : t.ceiling());
                    continue;
                }
                boolean wall = false;
                for (int ax = -1; ax <= 1 && !wall; ax++) for (int az = -1; az <= 1 && !wall; az++) wall = inside(ox, r, shape, x + ax, z + az);
                if (!wall) continue;
                for (int y = y0; y <= y1; y++) {
                    BlockState s;
                    if (y == y0 || y == y1) s = t.wall();
                    else if (y == Dungeons.FLOOR || y == y1 - 1) s = t.trim(); // bands at the foot and the top of the walls
                    else if (y == Dungeons.FLOOR + 2 && Math.floorMod(x + z, 5) == 0) s = t.light();
                    else s = rnd.nextFloat() < 0.12F ? t.wallAccent() : t.wall();
                    set(level, x, y, z, s);
                }
            }
        }
    }

    /** The floor: one of five patterns, with glowing tiles in a grid (the ceiling is too high to light it). */
    private static BlockState floorAt(int ox, Room r, Theme t, int pattern, int x, int z) {
        int dx = x - ox, dz = z - r.zMid();
        if (Math.floorMod(dx, 6) == 3 && Math.floorMod(z - r.z0(), 6) == 3) return t.light();
        return switch (pattern) {
            case 0 -> dx == 0 || dz == 0 ? t.floorAccent() : t.floor(); // a cross
            case 1 -> ((dx + dz) & 1) == 0 ? t.floor() : t.floorAlt(); // a checker
            case 2 -> { // rings out from the middle
                int ring = Math.max(Math.abs(dx), Math.abs(dz));
                yield ring % 5 == 0 ? t.floorAccent() : ring % 5 == 1 ? t.floorAlt() : t.floor();
            }
            case 3 -> Math.abs(dx) + Math.abs(dz) <= 3 ? t.floorAccent() : Math.abs(dx) >= r.half() - 1 || z <= r.z0() + 1 || z >= r.z1() - 1 ? t.floorAlt() : t.floor();
            default -> Math.floorMod(dx + dz, 6) < 2 ? t.floorAlt() : t.floor(); // diagonal stripes
        };
    }

    // ================================================================== layouts

    /** Keeps the way between the two doors clear. */
    private static boolean onPath(int ox, Room r, int x, int z) {
        return Math.abs(x - ox) <= 2 && (z <= r.z0() + 3 || z >= r.z1() - 3);
    }

    private static void column(ServerLevel level, int ox, Room r, Shape shape, Theme t, int x, int z, int height) {
        if (!inside(ox, r, shape, x, z) || onPath(ox, r, x, z)) return;
        for (int y = 0; y < height; y++) set(level, x, Dungeons.FLOOR + y, z, y == 0 || y == height - 1 ? t.trim() : t.pillar());
    }

    private static void layout(ServerLevel level, int ox, Room r, Theme t, Shape shape, Layout lay, RandomSource rnd) {
        int zm = r.zMid(), h = r.height(), half = r.half();
        int hl = (int) halfLen(r);
        switch (lay) {
            case PILLARS -> { // four thick pillars
                int dx = Math.max(3, half / 2), dz = Math.max(3, hl / 2);
                for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2)
                    for (int a = 0; a <= 1; a++) for (int c = 0; c <= 1; c++) column(level, ox, r, shape, t, ox + sx * (dx + a), zm + sz * (dz + c), h);
            }
            case RING -> { // a ring of columns round the middle
                int rad = Math.max(4, Math.min(half, hl) - 4), n = rad >= 10 ? 12 : 8;
                for (int k = 0; k < n; k++) {
                    double a = Math.PI * 2 * k / n + Math.PI / n;
                    column(level, ox, r, shape, t, ox + (int) Math.round(Math.cos(a) * rad), zm + (int) Math.round(Math.sin(a) * rad), h);
                }
            }
            case DAIS -> { // a low raised centre with a step all round and lights at its corners
                int d = Math.max(3, Math.min(half, hl) / 3);
                for (int x = ox - d; x <= ox + d; x++) for (int z = zm - d; z <= zm + d; z++) {
                    boolean rim = Math.abs(x - ox) == d || Math.abs(z - zm) == d;
                    set(level, x, Dungeons.FLOOR, z, rim ? t.slab() : t.floorAccent());
                }
                for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2) {
                    set(level, ox + sx * (d - 1), Dungeons.FLOOR + 1, zm + sz * (d - 1), t.lantern());
                }
            }
            case BRAZIERS -> { // a pedestal with a light near every corner
                for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2) {
                    int cx = ox + sx * (half - 2), cz = zm + sz * (hl - 2);
                    if (!inside(ox, r, shape, cx, cz)) {
                        cx = ox + sx * (half * 3 / 5);
                        cz = zm + sz * (hl * 3 / 5);
                    }
                    if (!inside(ox, r, shape, cx, cz)) continue;
                    set(level, cx, Dungeons.FLOOR, cz, t.trim());
                    set(level, cx, Dungeons.FLOOR + 1, cz, t.light());
                    set(level, cx, Dungeons.FLOOR + 2, cz, t.lantern());
                }
            }
            case COLONNADE -> { // two rows of columns down the length of the hall
                int cx = Math.max(3, half * 11 / 20);
                for (int z = r.z0() + 3; z <= r.z1() - 3; z += 4) {
                    column(level, ox, r, shape, t, ox - cx, z, h);
                    column(level, ox, r, shape, t, ox + cx, z, h);
                }
            }
            case OBELISK -> { // a tall stone in the middle, crowned with light, steps round its foot
                for (int x = ox - 2; x <= ox + 2; x++) for (int z = zm - 2; z <= zm + 2; z++) set(level, x, Dungeons.FLOOR, z, t.slab());
                int top = Math.max(4, h - 3);
                for (int y = 0; y < top; y++) for (int x = ox - 1; x <= ox + 1; x++) for (int z = zm - 1; z <= zm + 1; z++) {
                    boolean edge = Math.abs(x - ox) == 1 && Math.abs(z - zm) == 1;
                    set(level, x, Dungeons.FLOOR + y, z, y == 0 ? t.trim() : edge ? t.trim() : t.pillar());
                }
                set(level, ox, Dungeons.FLOOR + top, zm, t.light());
            }
            case RUINS -> { // broken columns and fallen stone
                for (int k = 0; k < 10; k++) {
                    int x = ox - half + 2 + rnd.nextInt(2 * half - 3), z = r.z0() + 2 + rnd.nextInt(Math.max(1, r.z1() - r.z0() - 3));
                    if (Math.abs(x - ox) <= 2) continue;
                    column(level, ox, r, shape, t, x, z, 1 + rnd.nextInt(Math.max(1, h - 2)));
                }
                rubble(level, ox, r, t, shape, rnd, 24);
            }
            case CHANNELS -> { // two water channels down the length of the room
                int cx = Math.max(3, half - 3);
                for (int z = r.z0() + 2; z <= r.z1() - 2; z++) for (int sx = -1; sx <= 1; sx += 2) {
                    int x = ox + sx * cx;
                    if (!inside(ox, r, shape, x, z) || !inside(ox, r, shape, x + sx, z)) continue;
                    set(level, x, Dungeons.FLOOR - 2, z, t.floorAlt());
                    set(level, x, Dungeons.FLOOR - 1, z, Blocks.WATER.defaultBlockState());
                }
            }
            case TERRACE -> { // a raised walk along the walls, with a step down into the room
                for (int x = ox - half; x <= ox + half; x++) for (int z = r.z0(); z <= r.z1(); z++) {
                    if (!inside(ox, r, shape, x, z) || onPath(ox, r, x, z)) continue;
                    int d = distToWall(ox, r, shape, x, z, 3);
                    if (d <= 2) set(level, x, Dungeons.FLOOR, z, t.floorAlt());
                    else if (d == 3) set(level, x, Dungeons.FLOOR, z, t.slab());
                }
            }
            case OPEN -> { }
        }
    }

    /** Distance (1..max, or max+1) from (x, z) to the nearest spot outside the shape. */
    private static int distToWall(int ox, Room r, Shape shape, int x, int z, int max) {
        for (int d = 1; d <= max; d++)
            for (int ax = -d; ax <= d; ax++) for (int az = -d; az <= d; az++)
                if ((Math.abs(ax) == d || Math.abs(az) == d) && !inside(ox, r, shape, x + ax, z + az)) return d;
        return max + 1;
    }

    private static void rubble(ServerLevel level, int ox, Room r, Theme t, Shape shape, RandomSource rnd, int n) {
        for (int k = 0; k < n; k++) {
            int x = ox - r.half() + rnd.nextInt(2 * r.half() + 1), z = r.z0() + rnd.nextInt(r.z1() - r.z0() + 1);
            if (!inside(ox, r, shape, x, z) || onPath(ox, r, x, z)) continue;
            BlockPos p = new BlockPos(x, Dungeons.FLOOR, z);
            if (!level.getBlockState(p).isAir()) continue;
            set(level, x, Dungeons.FLOOR, z, rnd.nextBoolean() ? t.slab() : rnd.nextBoolean() ? t.rubble() : t.wallAccent());
        }
    }

    // ================================================================== dressing

    /** Chandeliers, banners on straight walls, moss, rubble: a little of each, by chance. */
    private static void dress(ServerLevel level, int ox, Room r, Theme t, Shape shape, RandomSource rnd) {
        int y1 = Dungeons.FLOOR + r.height();
        if (rnd.nextFloat() < 0.6F && r.height() >= 8) { // chandeliers: chains down from the ceiling, a light at the end
            for (int x = ox - r.half() + 4; x <= ox + r.half() - 4; x += 8)
                for (int z = r.z0() + 4; z <= r.z1() - 4; z += 8) {
                    if (!inside(ox, r, shape, x, z) || !level.getBlockState(new BlockPos(x, Dungeons.FLOOR + 3, z)).isAir()) continue;
                    int len = 2 + rnd.nextInt(Math.max(1, r.height() / 3));
                    for (int y = y1 - 1; y > y1 - 1 - len; y--) set(level, x, y, z, Blocks.IRON_CHAIN.defaultBlockState());
                    set(level, x, y1 - 1 - len, z, t.lantern().hasProperty(LanternBlock.HANGING) ? t.lantern().setValue(LanternBlock.HANGING, true) : t.lantern());
                }
        }
        if (shape == Shape.SQUARE && rnd.nextFloat() < 0.7F) { // banners down the side walls
            BlockState banner = b(t.banner() + "_wall_banner");
            if (banner.hasProperty(HorizontalDirectionalBlock.FACING)) {
                for (int z = r.z0() + 3; z <= r.z1() - 3; z += 6) {
                    set(level, ox - r.half(), Dungeons.FLOOR + 4, z, banner.setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
                    set(level, ox + r.half(), Dungeons.FLOOR + 4, z, banner.setValue(HorizontalDirectionalBlock.FACING, Direction.WEST));
                }
            }
        }
        if (t.carpet() != null) { // moss creeping over the floor
            for (int k = 0; k < (2 * r.half() + 1) * (r.z1() - r.z0()) / 14; k++) {
                int x = ox - r.half() + rnd.nextInt(2 * r.half() + 1), z = r.z0() + rnd.nextInt(r.z1() - r.z0() + 1);
                BlockPos p = new BlockPos(x, Dungeons.FLOOR, z);
                if (inside(ox, r, shape, x, z) && level.getBlockState(p).isAir() && !level.getBlockState(p.below()).isAir()
                        && level.getBlockState(p.below()).getFluidState().isEmpty()) set(level, x, Dungeons.FLOOR, z, t.carpet());
            }
        }
        if (rnd.nextFloat() < 0.3F) rubble(level, ox, r, t, shape, rnd, 10);
    }

    /** A corridor from z0 to z1, with arches and hanging lights; a barred door at the start unless it leaves the entrance. */
    private static void corridor(ServerLevel level, int ox, int z0, int z1, Theme t, boolean barred) {
        int y0 = Dungeons.FLOOR - 1, y1 = Dungeons.FLOOR + 4;
        for (int z = z0; z <= z1; z++) {
            boolean arch = (z - z0) % 3 == 0;
            for (int x = ox - 2; x <= ox + 2; x++) {
                set(level, x, y0, z, Math.abs(x - ox) <= 1 ? t.floorAccent() : t.wall());
                set(level, x, y1, z, t.ceiling());
                for (int y = y0 + 1; y < y1; y++) {
                    BlockState s = AIR;
                    if (Math.abs(x - ox) == 2) s = arch ? t.trim() : t.wall();
                    else if (arch && y == y1 - 1) s = t.trim(); // the arch over the way
                    set(level, x, y, z, s);
                }
            }
            if ((z - z0) % 3 == 1) {
                BlockState lamp = t.lantern().hasProperty(LanternBlock.HANGING) ? t.lantern().setValue(LanternBlock.HANGING, true) : t.lantern();
                set(level, ox, y1 - 1, z, lamp);
            }
        }
        // the doorways through the two room walls (the walls are the corridor's ends)
        fill(level, ox - 1, Dungeons.FLOOR, z0, ox + 1, Dungeons.FLOOR + 2, z0, barred ? DOOR : AIR);
        fill(level, ox - 1, Dungeons.FLOOR, z1, ox + 1, Dungeons.FLOOR + 2, z1, AIR);
        fill(level, ox - 1, Dungeons.FLOOR + 3, z0, ox + 1, Dungeons.FLOOR + 3, z0, t.trim());
        fill(level, ox - 1, Dungeons.FLOOR + 3, z1, ox + 1, Dungeons.FLOOR + 3, z1, t.trim());
    }

    /** Opens the barred door out of a room. */
    public static void openDoor(ServerLevel level, DungeonData.Run run, Room r) {
        int ox = Dungeons.originX(run.slot);
        int z = r.z1() + 1;
        fill(level, ox - 1, Dungeons.FLOOR, z, ox + 1, Dungeons.FLOOR + 2, z, AIR);
    }

    /** The entrance hall: the exit circle at the back, lanterns round it, banners. */
    private static void entrance(ServerLevel level, int ox, Room r, Theme t) {
        int y = Dungeons.FLOOR - 1;
        for (int x = ox - 1; x <= ox + 1; x++) for (int z = 0; z <= 2; z++) set(level, x, y, z, b(Blocks.CRYING_OBSIDIAN));
        set(level, ox, y, 1, b(Blocks.LODESTONE));
        set(level, ox - 2, Dungeons.FLOOR, 0, b(Blocks.SOUL_LANTERN));
        set(level, ox + 2, Dungeons.FLOOR, 0, b(Blocks.SOUL_LANTERN));
        BlockState banner = b(t.banner() + "_wall_banner");
        if (banner.hasProperty(HorizontalDirectionalBlock.FACING)) {
            for (int z = 3; z <= r.z1() - 2; z += 4) {
                set(level, ox - r.half(), Dungeons.FLOOR + 3, z, banner.setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
                set(level, ox + r.half(), Dungeons.FLOOR + 3, z, banner.setValue(HorizontalDirectionalBlock.FACING, Direction.WEST));
            }
        }
    }

    // ================================================================== spots

    /** Where a monster appears: a free spot on the floor away from the players. */
    static BlockPos spawnSpot(ServerLevel level, int slot, Room r, RandomSource rnd, List<? extends net.minecraft.world.entity.Entity> players) {
        int ox = Dungeons.originX(slot);
        BlockPos best = null;
        for (int tries = 0; tries < 60; tries++) {
            int x = ox - (r.half() - 2) + rnd.nextInt(2 * (r.half() - 2) + 1);
            int z = r.z0() + 2 + rnd.nextInt(Math.max(1, r.z1() - r.z0() - 3));
            BlockPos p = new BlockPos(x, Dungeons.FLOOR, z);
            while (!level.getBlockState(p).isAir() && p.getY() < Dungeons.FLOOR + 2) p = p.above(); // on a raised centre or a slab
            if (!free(level, p)) continue;
            boolean near = false;
            for (var e : players) if (e.distanceToSqr(p.getX() + 0.5, p.getY(), p.getZ() + 0.5) < 8 * 8) near = true;
            if (!near) return p;
            best = p;
        }
        return best != null ? best : new BlockPos(ox, Dungeons.FLOOR, r.zMid());
    }

    /** Air for a tall monster, with ground (not the empty outside) under it. */
    private static boolean free(ServerLevel level, BlockPos p) {
        return level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir() && level.getBlockState(p.above(2)).isAir()
                && !level.getBlockState(p.below()).isAir();
    }

    /** Where a room's chest goes: the free floor nearest the middle. */
    static BlockPos chestSpot(ServerLevel level, int slot, Room r) {
        int ox = Dungeons.originX(slot);
        for (int d = 0; d <= 8; d++)
            for (int ax = -d; ax <= d; ax++) for (int az = -d; az <= d; az++) {
                if (Math.max(Math.abs(ax), Math.abs(az)) != d) continue;
                for (int y = 0; y <= 1; y++) {
                    BlockPos p = new BlockPos(ox + ax, Dungeons.FLOOR + y, r.zMid() - 3 + az);
                    if (level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir() && !level.getBlockState(p.below()).isAir()
                            && level.getBlockState(p.below()).getFluidState().isEmpty()) return p;
                }
            }
        return new BlockPos(ox, Dungeons.FLOOR, r.zMid());
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
        fill(level, ox - half, Dungeons.FLOOR - 2, -2, ox + half, Dungeons.FLOOR + top, last.z1() + 2, AIR);
    }
}
