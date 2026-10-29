package com.jo112ss.rpgmod.dungeon;

import com.jo112ss.rpgmod.RpgMod;
import com.jo112ss.rpgmod.dungeon.DungeonData.Run;
import com.jo112ss.rpgmod.dungeon.Dungeons.Kind;
import com.jo112ss.rpgmod.dungeon.Dungeons.Room;
import com.jo112ss.rpgmod.job.JobManager;
import com.jo112ss.rpgmod.job.fx.ServerFx;
import com.jo112ss.rpgmod.mob.Elite;
import com.jo112ss.rpgmod.mob.MobLevels;
import com.jo112ss.rpgmod.mob.revenant.Revenant;
import com.jo112ss.rpgmod.registry.ModRegistries;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerSetSpawnEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Running the dungeons: offerings at an altar open one, anyone can walk in (and join later) until it
 * is cleared; stepping into a room starts it (its monsters appear, the way on stays barred until they
 * are all down, then a small chest is left), the last room holds the best chest, and thirty seconds
 * after it is won everyone inside is sent back to the altar. The exit circle in the entrance hall
 * takes you back at any time. Inside, blocks cannot be broken or placed and no spawn point can be set.
 */
@EventBusSubscriber(modid = RpgMod.MODID)
public final class DungeonManager {
    private DungeonManager() {}

    /** Monster health per extra player in the dungeon (+40% each). */
    public static final double PARTY_HEALTH = 0.4;
    /** Chance the last room of a key's run holds a boss instead of two or three strong monsters. */
    public static final double BOSS_CHANCE = 0.1;
    public static final int CLOSE_TICKS = 600, EXIT_TICKS = 30;
    private static final Identifier PARTY = Identifier.fromNamespaceAndPath(RpgMod.MODID, "dungeon_party");

    /** Dev test: logs what happens. */
    public static boolean testLog;
    /** Dev test: forces the last room (true = boss, false = strong monsters, null = by chance). */
    public static Boolean forceBoss;

    private static void log(String msg, Object... args) {
        if (testLog) RpgMod.LOGGER.info("[AUTOTEST] dungeon " + msg, args);
    }

    // ================================================================== offerings and the altar

    /** What an item opens at the altar, or null. */
    public static Kind kindOf(ItemStack stack) {
        if (stack.is(ModRegistries.DUNGEON_KEY.get()) || stack.is(ModRegistries.SEALED_KEY.get())) return Kind.RUN;
        if (stack.is(ModRegistries.SLIME_CROWN.get())) return Kind.KING_SLIME;
        if (stack.is(ModRegistries.GOLEM_CORE.get())) return Kind.GOLEM;
        if (stack.is(ModRegistries.SOUL_PHYLACTERY.get())) return Kind.LICH;
        if (stack.is(ModRegistries.QUEEN_COCOON.get())) return Kind.SPIDER_QUEEN;
        if (stack.is(ModRegistries.ABYSS_KEY.get())) return Kind.ABYSS_WITHER;
        return null;
    }

    /** A sealed key: its dungeon is always this level. */
    public static ItemStack sealedKey(int level) {
        ItemStack s = new ItemStack(ModRegistries.SEALED_KEY.get());
        CompoundTag t = new CompoundTag();
        t.putInt("DungeonLevel", level);
        s.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
        s.set(DataComponents.ITEM_NAME, Component.translatable("item.rpgmod.sealed_key.level", level));
        return s;
    }

    public static int sealedLevel(ItemStack s) {
        return s.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getIntOr("DungeonLevel", 0);
    }

    /** Right-click on the altar holding something: an offering opens a dungeon; anything else is a plain click. */
    public static InteractionResult offer(ServerLevel level, BlockPos pos, ServerPlayer p, ItemStack stack) {
        Kind kind = kindOf(stack);
        DungeonData d = DungeonData.get(level.getServer());
        if (kind == null || d.byAltar(level.dimension(), pos) != null) return enter(level, pos, p);
        ServerLevel dl = Dungeons.level(level.getServer());
        if (dl == null) return InteractionResult.FAIL;
        int fixed = stack.is(ModRegistries.SEALED_KEY.get()) ? sealedLevel(stack) : 0;
        if (!p.getAbilities().instabuild) stack.shrink(1);
        Run r = d.open(level.dimension(), pos, kind, fixed, level.getRandom().nextLong());
        // the entrance and the first room now, the rest one room a tick (a whole dungeon at once would stall the server)
        List<Runnable> build = DungeonBuilder.steps(dl, r);
        build.remove(0).run();
        for (int i = 0; i < build.size(); i++) com.jo112ss.rpgmod.job.SkillTasks.later(i + 1, build.get(i));
        AltarBlock.setActive(level, pos, true);
        Vec3 top = Vec3.atBottomCenterOf(pos.above());
        ServerFx.of("dg_open", top).color(color(kind)).send(level);
        level.playSound(null, pos, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 0.8F, 1.2F);
        Component what = Component.translatable("rpgmod.dungeon.kind." + kind.name().toLowerCase(java.util.Locale.ROOT));
        Component lv = fixed > 0 ? Component.translatable("rpgmod.dungeon.fixed", fixed) : Component.translatable("rpgmod.dungeon.scaled");
        for (ServerPlayer o : level.getEntitiesOfClass(ServerPlayer.class, new net.minecraft.world.phys.AABB(pos).inflate(24)))
            o.sendSystemMessage(Component.translatable("rpgmod.dungeon.opened", what, lv));
        log("opened {} slot {} fixed level {} at {} - {}", kind, r.slot, fixed, pos, DungeonBuilder.lastDescription);
        return InteractionResult.SUCCESS;
    }

    /** Right-click on the altar: walk into its dungeon (or be told what it wants). */
    public static InteractionResult enter(ServerLevel level, BlockPos pos, ServerPlayer p) {
        Run r = DungeonData.get(level.getServer()).byAltar(level.dimension(), pos);
        if (r == null) {
            p.sendSystemMessage(Component.translatable("rpgmod.dungeon.needs_offering"), true);
            return InteractionResult.SUCCESS;
        }
        if (r.closing >= 0) {
            p.sendSystemMessage(Component.translatable("rpgmod.dungeon.closing"), true);
            return InteractionResult.SUCCESS;
        }
        ServerLevel dl = Dungeons.level(level.getServer());
        if (dl == null) return InteractionResult.FAIL;
        Vec3 at = Dungeons.entry(r.slot);
        ServerFx.of("dg_warp", p.position()).color(color(r.kind)).send(level);
        p.teleportTo(dl, at.x, at.y, at.z, Set.of(), 0, 0, true);
        dl.playSound(null, BlockPos.containing(at), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 0.7F);
        p.sendSystemMessage(Component.translatable("rpgmod.dungeon.entered"));
        log("{} entered slot {}", p.getGameProfile().name(), r.slot);
        return InteractionResult.SUCCESS;
    }

    static int color(Kind k) {
        return switch (k) {
            case RUN -> 0xB070FF;
            case KING_SLIME -> 0x70E070;
            case GOLEM -> 0xFF9050;
            case LICH -> 0x7FE8FF;
            case SPIDER_QUEEN -> 0xB070FF;
            case ABYSS_WITHER -> 0x6A30A0;
        };
    }

    // ================================================================== going home

    private static final Map<UUID, Integer> EXIT = new HashMap<>();

    /** Back to the altar of {@code r} (or, without one, to the world spawn). */
    public static void sendHome(ServerPlayer p, Run r) {
        MinecraftServer server = p.level().getServer();
        EXIT.remove(p.getUUID());
        ServerLevel to = r != null ? server.getLevel(r.altarDim) : null;
        Vec3 at;
        if (to != null) {
            at = Vec3.atBottomCenterOf(r.altar.above());
        } else {
            var spawn = server.getRespawnData();
            to = server.getLevel(spawn.dimension());
            if (to == null) to = server.overworld();
            BlockPos sp = spawn.pos();
            at = new Vec3(sp.getX() + 0.5, to.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sp.getX(), sp.getZ()), sp.getZ() + 0.5);
        }
        p.teleportTo(to, at.x, at.y, at.z, Set.of(), p.getYRot(), 0, true);
        ServerFx.of("dg_warp", at).color(r != null ? color(r.kind) : 0xB070FF).send(to);
        to.playSound(null, BlockPos.containing(at), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 1.1F);
        log("{} sent home ({})", p.getGameProfile().name(), r != null ? "altar" : "spawn");
    }

    // ================================================================== every tick

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ServerLevel dl = Dungeons.level(server);
        if (dl == null) return;
        DungeonData d = DungeonData.get(server);
        long t = server.getTickCount();
        for (ServerPlayer p : List.copyOf(dl.players())) {
            int slot = Dungeons.slotAt(p.getX());
            Run r = d.bySlot(slot);
            if (r == null) { // its dungeon is gone (logged out in it, or a stray teleport)
                if (p.isAlive() && t % 20 == 0) sendHome(p, null);
                continue;
            }
            if (p.isAlive() && Dungeons.onExit(slot, p)) {
                int n = EXIT.merge(p.getUUID(), 1, Integer::sum);
                if (n % 10 == 1) p.sendSystemMessage(Component.translatable("rpgmod.dungeon.leaving"), true);
                if (n >= EXIT_TICKS) sendHome(p, r);
            } else {
                EXIT.remove(p.getUUID());
            }
        }
        for (Run r : List.copyOf(d.runs)) {
            tickRun(server, dl, d, r, t);
            if (t % 20 == 0) ambient(server, dl, r);
        }
    }

    private static void ambient(MinecraftServer server, ServerLevel dl, Run r) {
        ServerLevel al = server.getLevel(r.altarDim);
        if (al != null && al.isLoaded(r.altar)) ServerFx.of("dg_altar", Vec3.atBottomCenterOf(r.altar.above())).color(color(r.kind)).send(al, 48);
        if (!Dungeons.players(dl, r.slot).isEmpty()) ServerFx.of("dg_pad", Vec3.atBottomCenterOf(Dungeons.exitCentre(r.slot).above())).send(dl, 48);
    }

    private static void tickRun(MinecraftServer server, ServerLevel dl, DungeonData d, Run r, long t) {
        if (r.closing >= 0) {
            if (r.closing % 20 == 0 && r.closing > 0) {
                for (ServerPlayer p : Dungeons.players(dl, r.slot)) p.sendSystemMessage(Component.translatable("rpgmod.dungeon.returning", r.closing / 20), true);
            }
            if (--r.closing <= 0) finish(server, dl, d, r);
            d.setDirty();
            return;
        }
        List<Room> rooms = r.rooms();
        if (r.room <= 0 || r.room >= rooms.size()) return;
        Room room = rooms.get(r.room);
        if (!r.fighting) {
            if (t % 5 != 0) return;
            var inside = Dungeons.players(dl, r.slot);
            var area = Dungeons.interior(r.slot, room).deflate(0, 0, 1);
            for (ServerPlayer p : inside) {
                if (area.contains(p.position())) {
                    startRoom(dl, d, r, room, inside);
                    return;
                }
            }
            return;
        }
        if (r.waveDelay > 0) { // a breather between waves
            if (--r.waveDelay == 0) spawnWave(dl, d, r, room, Dungeons.players(dl, r.slot));
            return;
        }
        if (t % 20 == 0) retarget(dl, r);
        if (t % 10 != 0) return;
        BlockPos c = new BlockPos(Dungeons.originX(r.slot), Dungeons.FLOOR, room.zMid());
        if (!dl.areEntitiesLoaded(ChunkPos.pack(c))) return; // nobody near: the monsters are asleep, not gone
        for (UUID u : r.mobs) {
            Entity e = dl.getEntity(u);
            if (e instanceof LivingEntity le && le.isAlive()) return;
        }
        if (r.wave + 1 < waves(r)) {
            r.wave++;
            r.waveDelay = waveDelayTicks;
            r.mobs.clear();
            d.setDirty();
            for (ServerPlayer p : Dungeons.players(dl, r.slot)) p.sendSystemMessage(Component.translatable("rpgmod.dungeon.wave_cleared", r.wave, waves(r)), true);
            dl.playSound(null, c, SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 0.6F, 1.4F);
            log("room {} wave {} beaten", r.room, r.wave);
            return;
        }
        clearRoom(dl, d, r, room);
    }

    // ================================================================== rooms

    /** Level of the dungeon's monsters: fixed by a sealed key, else that of the strongest player inside. */
    static int levelFor(Run r, List<ServerPlayer> inside) {
        if (r.fixedLevel > 0) return r.fixedLevel;
        if (r.kind == Kind.ABYSS_WITHER) return com.jo112ss.rpgmod.mob.boss.AbyssWither.LEVEL; // the hidden boss is always Lv200
        int top = 1;
        for (ServerPlayer p : inside) {
            var jd = JobManager.data(p);
            if (jd.hasJob()) top = Math.max(top, jd.level());
        }
        return top;
    }

    private static final List<EntityType<? extends Mob>> ADDS = List.of(EntityTypes.ZOMBIE, EntityTypes.SKELETON, EntityTypes.SPIDER, EntityTypes.HUSK, EntityTypes.STRAY);
    private static final List<EntityType<? extends Mob>> STRONG = List.of(EntityTypes.ZOMBIE, EntityTypes.SKELETON, EntityTypes.HUSK, EntityTypes.STRAY, EntityTypes.SPIDER,
            EntityTypes.VINDICATOR);

    public static final int WAVES = 3;
    /** Ticks between waves (dev tests shorten it). */
    public static int waveDelayTicks = 60;

    /** How many waves a room has: three, or the boss alone in an arena opened by a boss offering. */
    static int waves(Run r) {
        return r.kind.boss() ? 1 : WAVES;
    }

    private static void startRoom(ServerLevel dl, DungeonData d, Run r, Room room, List<ServerPlayer> inside) {
        r.party = Math.max(1, inside.size());
        r.level = levelFor(r, inside);
        r.mobs.clear();
        r.fighting = true;
        r.wave = 0;
        r.waveDelay = 0;
        Component title = room.last() ? Component.translatable("rpgmod.dungeon.room_last") : Component.translatable("rpgmod.dungeon.room", r.room, r.rooms().size() - 1);
        for (ServerPlayer p : inside) p.sendSystemMessage(title, true);
        dl.playSound(null, new BlockPos(Dungeons.originX(r.slot), Dungeons.FLOOR, room.zMid()), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.4F, room.last() ? 0.7F : 1.3F);
        spawnWave(dl, d, r, room, inside);
    }

    /** The monsters of the current wave: the first two are the rank and file, the third brings the strong one (or the boss). */
    private static void spawnWave(ServerLevel dl, DungeonData d, Run r, Room room, List<ServerPlayer> inside) {
        int n = r.party, lv = r.level;
        RandomSource rnd = dl.getRandom();
        boolean finalWave = r.wave + 1 >= waves(r);
        List<Spawn> plan = new ArrayList<>();
        int traits = lv >= 100 ? 3 : 2;
        if (room.last() && finalWave) {
            boolean boss = r.kind.boss() || (forceBoss != null ? forceBoss : rnd.nextDouble() < BOSS_CHANCE);
            if (boss) {
                Kind k = r.kind.boss() ? r.kind : BOSSES[rnd.nextInt(BOSSES.length)];
                plan.add(new Spawn(bossType(k), null, 0, true));
            } else {
                int count = 2 + (n >= 3 ? 1 : 0);
                for (int i = 0; i < count; i++) plan.add(strong(rnd, 3));
            }
        } else if (finalWave) {
            if (r.room >= 2 && rnd.nextFloat() < 0.35F) { // a party of fallen adventurers, one of them strong
                Revenant.Role[] party = {Revenant.Role.WARRIOR, Revenant.Role.MAGE, rnd.nextBoolean() ? Revenant.Role.ARCHER : Revenant.Role.THIEF, Revenant.Role.PRIEST};
                for (int i = 0; i < party.length; i++) plan.add(new Spawn(ModRegistries.REVENANT.get(), party[i], i == 0 ? mask(rnd, traits) : 0, false));
                adds(plan, rnd, n);
            } else {
                plan.add(strong(rnd, traits));
                adds(plan, rnd, 2 + n);
            }
        } else if (room.last() && r.wave == 1) {
            plan.add(strong(rnd, traits)); // the last room's second wave already has a strong one
            adds(plan, rnd, 3 + n);
        } else {
            adds(plan, rnd, (room.last() ? 4 : 3) + n);
            if (r.wave == 1 && r.room >= 2 && rnd.nextBoolean()) { // a pair of fallen adventurers joins
                Revenant.Role[] roles = {Revenant.Role.WARRIOR, Revenant.Role.MAGE, Revenant.Role.ARCHER, Revenant.Role.THIEF};
                plan.add(new Spawn(ModRegistries.REVENANT.get(), roles[rnd.nextInt(roles.length)], 0, false));
                plan.add(new Spawn(ModRegistries.REVENANT.get(), Revenant.Role.PRIEST, 0, false));
            }
        }
        for (Spawn s : plan) {
            BlockPos at = s.boss() ? new BlockPos(Dungeons.originX(r.slot), Dungeons.FLOOR, room.zMid() + room.half() / 2)
                    : DungeonBuilder.spawnSpot(dl, r.slot, room, rnd, inside);
            Mob m = spawn(dl, s, at, lv, n, inside);
            if (m != null) r.mobs.add(m.getUUID());
        }
        d.setDirty();
        Component wave = Component.translatable("rpgmod.dungeon.wave", r.wave + 1, waves(r));
        if (waves(r) > 1) for (ServerPlayer p : inside) p.sendSystemMessage(wave, true);
        log("room {} wave {}: {} monsters at Lv{} for {} players (last {})", r.room, r.wave + 1, r.mobs.size(), lv, n, room.last());
    }

    private static void adds(List<Spawn> plan, RandomSource rnd, int count) {
        for (int i = 0; i < count; i++) plan.add(new Spawn(ADDS.get(rnd.nextInt(ADDS.size())), null, 0, false));
    }

    /** Monsters keep after the players: spiders in a lit room would otherwise leave them alone, and any that lost track pick the nearest again. */
    private static void retarget(ServerLevel dl, Run r) {
        var inside = Dungeons.players(dl, r.slot);
        inside.removeIf(p -> p.isCreative() || com.jo112ss.rpgmod.compat.Downed.is(p));
        if (inside.isEmpty()) return;
        for (UUID u : r.mobs) {
            if (!(dl.getEntity(u) instanceof Mob m) || !m.isAlive()) continue;
            LivingEntity t = m.getTarget();
            if (t != null && t.isAlive() && !(t instanceof Player pl && (pl.isCreative() || pl.isSpectator()))) continue;
            ServerPlayer near = null;
            for (ServerPlayer p : inside) if (near == null || p.distanceToSqr(m) < near.distanceToSqr(m)) near = p;
            m.setTarget(near);
        }
    }

    private static final Kind[] BOSSES = {Kind.KING_SLIME, Kind.GOLEM, Kind.LICH, Kind.SPIDER_QUEEN};

    static EntityType<? extends Mob> bossType(Kind k) {
        return switch (k) {
            case GOLEM -> ModRegistries.GOLEM.get();
            case LICH -> ModRegistries.LICH.get();
            case SPIDER_QUEEN -> ModRegistries.SPIDER_QUEEN.get();
            case ABYSS_WITHER -> ModRegistries.ABYSS_WITHER.get();
            default -> ModRegistries.KING_SLIME.get();
        };
    }

    private static String spawnFx(Mob m) {
        if (m instanceof com.jo112ss.rpgmod.mob.boss.Golem) return "golem_spawn";
        if (m instanceof com.jo112ss.rpgmod.mob.boss.Lich) return "lich_spawn";
        if (m instanceof com.jo112ss.rpgmod.mob.boss.SpiderQueen) return "queen_spawn";
        if (m instanceof com.jo112ss.rpgmod.mob.boss.AbyssWither) return "lich_spawn";
        return "king_spawn";
    }

    /** One monster to spawn: its type, a job for revenants, elite traits (0 = none), a boss? */
    record Spawn(EntityType<? extends Mob> type, Revenant.Role role, int traits, boolean boss) {}

    private static Spawn strong(RandomSource rnd, int traits) {
        if (rnd.nextFloat() < 0.25F) {
            Revenant.Role[] roles = {Revenant.Role.WARRIOR, Revenant.Role.MAGE, Revenant.Role.ARCHER, Revenant.Role.THIEF};
            return new Spawn(ModRegistries.REVENANT.get(), roles[rnd.nextInt(roles.length)], mask(rnd, traits), false);
        }
        return new Spawn(STRONG.get(rnd.nextInt(STRONG.size())), null, mask(rnd, traits), false);
    }

    /** Random elite traits (no splitting: the halves would not count for the room). */
    private static int mask(RandomSource rnd, int count) {
        List<Elite.Trait> pool = new ArrayList<>(List.of(Elite.Trait.values()));
        pool.remove(Elite.Trait.SPLIT);
        int m = 0;
        for (int i = 0; i < count && !pool.isEmpty(); i++) m |= pool.remove(rnd.nextInt(pool.size())).bit();
        return m;
    }

    private static Mob spawn(ServerLevel dl, Spawn s, BlockPos at, int lv, int n, List<ServerPlayer> inside) {
        Mob m = s.type().create(dl, EntitySpawnReason.EVENT);
        if (m == null) return null;
        m.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 180, 0);
        m.setPersistenceRequired();
        if (m instanceof Revenant rv && s.role() != null) rv.setRole(s.role());
        if (s.boss()) {
            m.setData(ModRegistries.MOB_LEVEL, Math.max(1, lv)); // the bosses keep a level they already have
        } else {
            m.finalizeSpawn(dl, dl.getCurrentDifficultyAt(at), EntitySpawnReason.EVENT, null);
            if (m instanceof net.minecraft.world.entity.monster.zombie.Zombie z) z.setBaby(false);
        }
        MobLevels.forceLevel = lv;
        Elite.forceNext = s.traits();
        try {
            dl.addFreshEntityWithPassengers(m);
            if (s.boss()) log("boss {} added: removed {} found {} alive {} health {} / {} at {}", m.getType().getDescriptionId(), m.isRemoved(), dl.getEntity(m.getUUID()) != null,
                    m.isAlive(), m.getHealth(), m.getMaxHealth(), m.blockPosition());
        } finally {
            MobLevels.forceLevel = null;
            Elite.forceNext = null;
        }
        if (m instanceof com.jo112ss.rpgmod.mob.boss.ModBoss boss) {
            boss.recomputeHealth(true); // fought by the party inside, not by everyone online
        } else if (n > 1) {
            var hp = m.getAttribute(Attributes.MAX_HEALTH);
            if (hp != null) {
                hp.removeModifier(PARTY);
                hp.addPermanentModifier(new AttributeModifier(PARTY, PARTY_HEALTH * (n - 1), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                m.setHealth(m.getMaxHealth());
            }
        }
        ServerPlayer near = null;
        for (ServerPlayer p : inside) if (near == null || p.distanceToSqr(m) < near.distanceToSqr(m)) near = p;
        if (near != null && !near.isCreative()) m.setTarget(near);
        ServerFx.of(s.boss() ? spawnFx(m) : "dg_summon", m.position()).a(m.getBbWidth())
                .color(s.traits() != 0 ? 0xFFB040 : 0xB070FF).send(dl, 64);
        return m;
    }

    private static void clearRoom(ServerLevel dl, DungeonData d, Run r, Room room) {
        r.fighting = false;
        r.mobs.clear();
        var inside = Dungeons.players(dl, r.slot);
        int lv = levelFor(r, inside);
        BlockPos chestAt = DungeonBuilder.chestSpot(dl, r.slot, room);
        placeChest(dl, chestAt, room.last() ? finalLoot(dl.getRandom(), lv, r.kind.boss()) : roomLoot(dl.getRandom(), lv));
        ServerFx.of("dg_clear", Vec3.atBottomCenterOf(chestAt)).color(color(r.kind)).send(dl, 64);
        if (room.last()) {
            r.closing = CLOSE_TICKS;
            for (ServerPlayer p : inside) p.sendSystemMessage(Component.translatable("rpgmod.dungeon.cleared", CLOSE_TICKS / 20));
            dl.playSound(null, chestAt, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.0F);
        } else {
            DungeonBuilder.openDoor(dl, r, room);
            r.room++;
            for (ServerPlayer p : inside) p.sendSystemMessage(Component.translatable("rpgmod.dungeon.room_cleared"), true);
            dl.playSound(null, chestAt, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1.0F, 0.6F);
        }
        d.setDirty();
        log("room cleared (last {}), chest at {}", room.last(), chestAt);
    }

    private static void placeChest(ServerLevel dl, BlockPos at, List<ItemStack> loot) {
        BlockState chest = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH);
        dl.setBlock(at, chest, 3);
        if (!(dl.getBlockEntity(at) instanceof ChestBlockEntity c)) return;
        RandomSource rnd = dl.getRandom();
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < c.getContainerSize(); i++) slots.add(i);
        for (ItemStack s : loot) {
            if (slots.isEmpty()) break;
            c.setItem(slots.remove(rnd.nextInt(slots.size())), s);
        }
        c.setChanged();
        log("chest: {}", loot.stream().map(s -> s.getCount() + "x" + s.getHoverName().getString()).toList());
    }

    /**
     * A little after each room: potions, a few emeralds and experience bottles, now and then a skill
     * book, and the plain metals of the dungeon's level (see {@link #metals}). No keys, nothing past gold.
     */
    public static List<ItemStack> roomLoot(RandomSource rnd, int lv) {
        List<ItemStack> out = new ArrayList<>();
        if (rnd.nextFloat() < 0.7F) out.add(new ItemStack(ModRegistries.HP_POTION.get(), 1 + rnd.nextInt(2)));
        if (rnd.nextFloat() < 0.5F) out.add(new ItemStack(ModRegistries.MP_POTION.get(), 1 + rnd.nextInt(2)));
        if (rnd.nextFloat() < 0.6F) out.add(new ItemStack(Items.EMERALD, 1 + rnd.nextInt(3)));
        out.add(new ItemStack(Items.EXPERIENCE_BOTTLE, 1 + rnd.nextInt(2)));
        if (rnd.nextFloat() < ROOM_BOOK_CHANCE) out.add(new ItemStack(ModRegistries.SKILL_BOOK.get()));
        metals(out, rnd, lv, false);
        return out;
    }

    /** Chance of a skill book in a room's chest. */
    public static final float ROOM_BOOK_CHANCE = 0.05F;
    /** Dungeon levels from which gold, diamonds, and netherite with the upgrade template come up. */
    public static final int GOLD_AT = 50, DIAMOND_AT = 100, NETHERITE_AT = 150;

    /**
     * The metals of a chest by the dungeon's level: copper and iron from the start (iron a little more
     * likely as the level goes up), gold from 50; the final chest alone has diamonds from 100 and
     * netherite and the netherite upgrade template from 150.
     */
    static void metals(List<ItemStack> out, RandomSource rnd, int lv, boolean last) {
        double rise = Math.min(1.0, Math.max(0, lv) / 100.0); // 0 at level 0, 1 from level 100
        out.add(new ItemStack(Items.COPPER_INGOT, last ? 4 + rnd.nextInt(7) : 2 + rnd.nextInt(4)));
        if (rnd.nextDouble() < (last ? 0.5 : 0.25) + 0.3 * rise) out.add(new ItemStack(Items.IRON_INGOT, last ? 2 + rnd.nextInt(5) : 1 + rnd.nextInt(3)));
        if (lv >= GOLD_AT && rnd.nextDouble() < (last ? 0.7 : 0.3)) out.add(new ItemStack(Items.GOLD_INGOT, last ? 2 + rnd.nextInt(4) : 1 + rnd.nextInt(2)));
        if (!last) return;
        if (lv >= DIAMOND_AT) out.add(new ItemStack(Items.DIAMOND, 1 + rnd.nextInt(lv >= NETHERITE_AT ? 3 : 2)));
        if (lv >= NETHERITE_AT) {
            if (rnd.nextFloat() < 0.5F) out.add(new ItemStack(Items.NETHERITE_SCRAP, 1 + rnd.nextInt(2)));
            if (rnd.nextFloat() < 0.15F) out.add(new ItemStack(Items.NETHERITE_INGOT));
            if (rnd.nextFloat() < 0.35F) out.add(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE));
        }
    }

    /** The best chest, at the end: the metals of its level (diamonds and netherite only here), gems, books and keys. */
    public static List<ItemStack> finalLoot(RandomSource rnd, int lv, boolean boss) {
        List<ItemStack> out = new ArrayList<>();
        out.add(new ItemStack(ModRegistries.HP_POTION.get(), 2 + rnd.nextInt(2)));
        out.add(new ItemStack(ModRegistries.MP_POTION.get(), 2 + rnd.nextInt(2)));
        out.add(new ItemStack(Items.EMERALD, 3 + rnd.nextInt(6)));
        out.add(new ItemStack(Items.EXPERIENCE_BOTTLE, 6 + rnd.nextInt(7)));
        metals(out, rnd, lv, true);
        out.add(com.jo112ss.rpgmod.gem.GemItems.stack(com.jo112ss.rpgmod.gem.GemRoller.roll(rnd, boss)));
        if (rnd.nextFloat() < 0.35F) out.add(com.jo112ss.rpgmod.gem.GemItems.stack(com.jo112ss.rpgmod.gem.GemRoller.roll(rnd, false)));
        if (rnd.nextFloat() < 0.2F) out.add(new ItemStack(ModRegistries.JOB_BOOK.get()));
        if (rnd.nextFloat() < 0.35F) out.add(new ItemStack(ModRegistries.DUNGEON_KEY.get()));
        if (rnd.nextFloat() < 0.2F) out.add(sealedKey(lv));
        if (rnd.nextFloat() < OFFERING_CHANCE) { // one of the boss offerings (they are not crafted)
            var offerings = List.of(ModRegistries.SLIME_CROWN, ModRegistries.GOLEM_CORE, ModRegistries.SOUL_PHYLACTERY, ModRegistries.QUEEN_COCOON);
            out.add(new ItemStack(offerings.get(rnd.nextInt(offerings.size())).get()));
        }
        return out;
    }

    /** Chance the final chest holds one of the four boss offerings. */
    public static final float OFFERING_CHANCE = 0.1F;

    /** The end of a run: everyone inside goes back, the slot is emptied, the altar goes quiet. */
    private static void finish(MinecraftServer server, ServerLevel dl, DungeonData d, Run r) {
        for (ServerPlayer p : Dungeons.players(dl, r.slot)) sendHome(p, r);
        var area = Dungeons.area(r.slot);
        for (Entity e : dl.getEntitiesOfClass(Entity.class, area, e -> !(e instanceof Player))) e.discard();
        DungeonBuilder.clear(dl, r);
        d.close(r);
        ServerLevel al = server.getLevel(r.altarDim);
        if (al != null) AltarBlock.setActive(al, r.altar, false);
        log("slot {} closed", r.slot);
    }

    /** An altar is broken in creative mode while its dungeon is open: the run ends. */
    public static void abort(MinecraftServer server, Run r) {
        ServerLevel dl = Dungeons.level(server);
        DungeonData d = DungeonData.get(server);
        if (dl != null) {
            for (ServerPlayer p : Dungeons.players(dl, r.slot)) sendHome(p, null);
            for (Entity e : dl.getEntitiesOfClass(Entity.class, Dungeons.area(r.slot), e -> !(e instanceof Player))) e.discard();
            DungeonBuilder.clear(dl, r);
        }
        d.close(r);
    }

    // ================================================================== rules inside

    private static boolean builder(Entity e) {
        return e instanceof Player p && p.getAbilities().instabuild;
    }

    @SubscribeEvent
    public static void onBreak(net.neoforged.neoforge.event.level.block.BreakBlockEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (Dungeons.isDungeon(level) && !builder(event.getPlayer())) {
            event.setCanceled(true);
            return;
        }
        if (event.getState().getBlock() instanceof AltarBlock) {
            Run r = DungeonData.get(level.getServer()).byAltar(level.dimension(), event.getPos());
            if (r == null) return;
            if (!builder(event.getPlayer())) {
                event.setCanceled(true);
                if (event.getPlayer() instanceof ServerPlayer sp) sp.sendSystemMessage(Component.translatable("rpgmod.dungeon.altar_locked"), true);
            } else {
                abort(level.getServer(), r);
            }
        }
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof ServerLevel level && Dungeons.isDungeon(level) && !builder(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onExplode(ExplosionEvent.Detonate event) {
        if (Dungeons.isDungeon(event.getLevel())) event.getAffectedBlocks().clear();
    }

    @SubscribeEvent
    public static void onGrief(EntityMobGriefingEvent event) {
        if (Dungeons.isDungeon(event.getEntity().level())) event.setCanGrief(false);
    }

    @SubscribeEvent
    public static void onSetSpawn(PlayerSetSpawnEvent event) {
        if (event.getSpawnLevel() == Dungeons.DIM) event.setCanceled(true);
    }

    /** No pouring water or lava, lighting fires or growing things inside. */
    @SubscribeEvent
    public static void onUseOnBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!Dungeons.isDungeon(event.getLevel()) || builder(event.getEntity())) return;
        ItemStack s = event.getItemStack();
        if (s.getItem() instanceof net.minecraft.world.item.BucketItem || s.is(Items.FLINT_AND_STEEL) || s.is(Items.FIRE_CHARGE) || s.is(Items.BONE_MEAL)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        if (!Dungeons.isDungeon(event.getLevel()) || builder(event.getEntity())) return;
        if (event.getItemStack().getItem() instanceof net.minecraft.world.item.BucketItem) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onDeathLog(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        if (!testLog || event.getEntity().level().isClientSide() || !Dungeons.isDungeon(event.getEntity().level())) return;
        var src = event.getSource();
        log("death of {} by {} (attacker {}, direct {}) health {}", event.getEntity().getType().getDescriptionId(), src.getMsgId(),
                src.getEntity() != null ? src.getEntity().getType().getDescriptionId() : "-", src.getDirectEntity() != null ? src.getDirectEntity().getType().getDescriptionId() : "-",
                event.getEntity().getHealth());
    }

    /** Chances of a key from an elite and from a fallen adventurer (killed by a player). */
    public static final double KEY_ELITE = 0.12, KEY_REVENANT = 0.04;

    @SubscribeEvent
    public static void onDrops(net.neoforged.neoforge.event.entity.living.LivingDropsEvent event) {
        LivingEntity mob = event.getEntity();
        if (!(mob.level() instanceof ServerLevel sl) || !(event.getSource().getEntity() instanceof Player)) return;
        if (mob.getPersistentData().getBooleanOr(com.jo112ss.rpgmod.mob.boss.BossEvents.CHILD, false)) return;
        double c = Elite.is(mob) ? KEY_ELITE : mob instanceof Revenant ? KEY_REVENANT : 0;
        if (c <= 0 || mob.getRandom().nextDouble() >= c) return;
        ItemEntity e = new ItemEntity(sl, mob.getX(), mob.getY() + 0.5, mob.getZ(), new ItemStack(ModRegistries.DUNGEON_KEY.get()));
        e.setDefaultPickUpDelay();
        event.getDrops().add(e);
    }
}
