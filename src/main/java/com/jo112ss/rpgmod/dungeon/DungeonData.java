package com.jo112ss.rpgmod.dungeon;

import com.jo112ss.rpgmod.RpgMod;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** The open dungeons (one per altar) and the next free slot; kept with the world. */
public final class DungeonData extends SavedData {
    public static final SavedDataType<DungeonData> TYPE = new SavedDataType<>(Identifier.fromNamespaceAndPath(RpgMod.MODID, "dungeons"), DungeonData::new,
            CompoundTag.CODEC.xmap(DungeonData::load, DungeonData::save));

    /** One open dungeon. */
    public static final class Run {
        public int slot;
        public Dungeons.Kind kind = Dungeons.Kind.RUN;
        /** Fixed monster level (a sealed key), or 0 = the strongest player inside. */
        public int fixedLevel;
        public ResourceKey<Level> altarDim = Level.OVERWORLD;
        public BlockPos altar = BlockPos.ZERO;
        /** Index of the room being fought or next to fight (0 = the entrance hall, never fought). */
        public int room = 1;
        public boolean fighting;
        public Set<UUID> mobs = new HashSet<>();
        /** Ticks left before everyone is sent back, once the last room is cleared (-1 = not yet). */
        public int closing = -1;
        public long seed;

        public List<Dungeons.Room> rooms() {
            return Dungeons.layout(kind);
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putInt("slot", slot);
            t.putString("kind", kind.name());
            t.putInt("fixedLevel", fixedLevel);
            t.putString("altarDim", altarDim.identifier().toString());
            t.putIntArray("altar", new int[] {altar.getX(), altar.getY(), altar.getZ()});
            t.putInt("room", room);
            t.putBoolean("fighting", fighting);
            ListTag l = new ListTag();
            for (UUID u : mobs) l.add(new IntArrayTag(UUIDUtil.uuidToIntArray(u)));
            t.put("mobs", l);
            t.putInt("closing", closing);
            t.putLong("seed", seed);
            return t;
        }

        static Run load(CompoundTag t) {
            Run r = new Run();
            r.slot = t.getIntOr("slot", 0);
            try {
                r.kind = Dungeons.Kind.valueOf(t.getStringOr("kind", "RUN"));
            } catch (IllegalArgumentException e) {
                r.kind = Dungeons.Kind.RUN;
            }
            r.fixedLevel = t.getIntOr("fixedLevel", 0);
            Identifier dim = Identifier.tryParse(t.getStringOr("altarDim", "minecraft:overworld"));
            r.altarDim = ResourceKey.create(Registries.DIMENSION, dim != null ? dim : Identifier.withDefaultNamespace("overworld"));
            int[] a = t.getIntArray("altar").orElse(new int[] {0, 0, 0});
            if (a.length == 3) r.altar = new BlockPos(a[0], a[1], a[2]);
            r.room = t.getIntOr("room", 1);
            r.fighting = t.getBooleanOr("fighting", false);
            for (Tag e : t.getListOrEmpty("mobs")) if (e instanceof IntArrayTag ia && ia.getAsIntArray().length == 4) r.mobs.add(UUIDUtil.uuidFromIntArray(ia.getAsIntArray()));
            r.closing = t.getIntOr("closing", -1);
            r.seed = t.getLongOr("seed", 0);
            return r;
        }
    }

    public final List<Run> runs = new ArrayList<>();
    public int nextSlot = 1;

    public static DungeonData get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public Run bySlot(int slot) {
        for (Run r : runs) if (r.slot == slot) return r;
        return null;
    }

    public Run byAltar(ResourceKey<Level> dim, BlockPos pos) {
        for (Run r : runs) if (r.altarDim.equals(dim) && r.altar.equals(pos)) return r;
        return null;
    }

    public Run open(ResourceKey<Level> dim, BlockPos altar, Dungeons.Kind kind, int fixedLevel, long seed) {
        Run r = new Run();
        r.slot = nextSlot++;
        r.kind = kind;
        r.fixedLevel = fixedLevel;
        r.altarDim = dim;
        r.altar = altar.immutable();
        r.seed = seed;
        runs.add(r);
        setDirty();
        return r;
    }

    public void close(Run r) {
        runs.remove(r);
        setDirty();
    }

    private CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("nextSlot", nextSlot);
        ListTag l = new ListTag();
        for (Run r : runs) l.add(r.save());
        t.put("runs", l);
        return t;
    }

    private static DungeonData load(CompoundTag t) {
        DungeonData d = new DungeonData();
        d.nextSlot = Math.max(1, t.getIntOr("nextSlot", 1));
        for (Tag e : t.getListOrEmpty("runs")) if (e instanceof CompoundTag c) d.runs.add(Run.load(c));
        return d;
    }
}
