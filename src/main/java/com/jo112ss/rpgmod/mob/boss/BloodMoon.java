package com.jo112ss.rpgmod.mob.boss;

import com.jo112ss.rpgmod.RpgMod;
import com.jo112ss.rpgmod.mob.MobConfig;
import com.jo112ss.rpgmod.mob.MobLevels;
import com.jo112ss.rpgmod.network.BloodMoonPayload;
import com.jo112ss.rpgmod.registry.ModRegistries;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Days and blood moons. Every morning everyone sees "Day N". On the night of every 5th day (by
 * default) a blood moon rises: the sky turns red, nobody can sleep, ordinary monsters mostly stop
 * spawning, and the King Slime appears near one random player in the overworld. It leaves at dawn
 * if it is still alive.
 */
@EventBusSubscriber(modid = RpgMod.MODID)
public final class BloodMoon {
    private BloodMoon() {}

    /** Blood moon from sunset (when beds start working) until dawn. */
    public static final long START = 12500, END = 23200;

    private static boolean active;
    private static long lastDay = -1;
    /** Dev test: keep the boss around even when it is not a blood moon. */
    public static boolean testKeep;

    public static long day(Level level) {
        return Math.floorDiv(level.getOverworldClockTime(), 24000L) + 1;
    }

    public static long timeOfDay(Level level) {
        return Math.floorMod(level.getOverworldClockTime(), 24000L);
    }

    /** True during a blood moon night (overworld time). */
    public static boolean isBloodMoon(Level level) {
        if (!MobConfig.bloodMoon()) return false;
        long tod = timeOfDay(level);
        return day(level) % MobConfig.bloodMoonDays() == 0 && tod >= START && tod < END;
    }

    public static boolean active() {
        return active;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 10 != 0) return;
        ServerLevel ow = server.overworld();
        long d = day(ow);
        if (lastDay < 0 || d < lastDay) {
            lastDay = d; // world loaded or time turned back: nothing to announce
        } else if (d > lastDay) {
            lastDay = d;
            if (MobConfig.dayTitle()) announceDay(server, d);
        }
        boolean now = isBloodMoon(ow);
        if (now != active) {
            active = now;
            PacketDistributor.sendToAllPlayers(new BloodMoonPayload(now));
            if (now) announceRise(server);
            RpgMod.LOGGER.info("[blood moon] {} on day {}", now ? "rises" : "sets", d);
        }
        if (active && ow.getData(ModRegistries.BOSS_DAY) != d && server.getTickCount() % 40 == 0) {
            var boss = summon(ow);
            if (boss != null) ow.setData(ModRegistries.BOSS_DAY, d);
        }
    }

    /** A new world (or the same one reopened in single player) starts from scratch. */
    @SubscribeEvent
    public static void onServerStarted(net.neoforged.neoforge.event.server.ServerStartedEvent event) {
        lastDay = -1;
        active = false;
        testKeep = false;
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) PacketDistributor.sendToPlayer(sp, new BloodMoonPayload(active));
    }

    @SubscribeEvent
    public static void onSleep(CanPlayerSleepEvent event) {
        if (active && event.getLevel().dimension() == Level.OVERWORLD) {
            event.setProblem(new Player.BedSleepingProblem(Component.translatable("rpgmod.bloodmoon.no_sleep").withStyle(ChatFormatting.RED)));
        }
    }

    /** Ordinary monsters mostly stay away on a blood moon: the boss is the night's threat. */
    @SubscribeEvent
    public static void onSpawnCheck(MobSpawnEvent.PositionCheck event) {
        if (!active || event.getSpawnType() != EntitySpawnReason.NATURAL) return;
        if (!(event.getEntity() instanceof Enemy) || event.getEntity() instanceof BloodMoonBoss || event.getEntity() instanceof GolemPillar) return;
        if (event.getLevel().getLevel().dimension() != Level.OVERWORLD) return;
        if (event.getEntity().getRandom().nextDouble() >= MobConfig.bloodMoonSpawns()) {
            event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
        }
    }

    // ------------------------------------------------------------------ announcements

    private static void title(ServerPlayer p, Component title, Component sub, int in, int stay, int out) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(in, stay, out));
        p.connection.send(new ClientboundSetSubtitleTextPacket(sub));
        p.connection.send(new ClientboundSetTitleTextPacket(title));
    }

    private static void announceDay(MinecraftServer server, long d) {
        Component t = Component.translatable("rpgmod.day.title", d).withStyle(ChatFormatting.GOLD);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) title(p, t, Component.empty(), 15, 50, 25);
    }

    private static void announceRise(MinecraftServer server) {
        Component t = Component.translatable("rpgmod.bloodmoon.title").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
        Component sub = Component.translatable("rpgmod.bloodmoon.subtitle").withStyle(ChatFormatting.RED);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            title(p, t, sub, 20, 60, 30);
            p.level().playSound(null, p.blockPosition(), SoundEvents.AMBIENT_CAVE.value(), SoundSource.AMBIENT, 1.0F, 0.6F);
        }
    }

    // ------------------------------------------------------------------ the boss

    /**
     * Summons the night's boss - the King Slime or the Golem ({@link MobConfig#golemChance()}) - near a
     * random player in the overworld; null if nobody is there or no room.
     */
    public static net.minecraft.world.entity.Mob summon(ServerLevel ow) {
        boolean golem = ow.getRandom().nextDouble() < MobConfig.golemChance();
        return summon(ow, golem);
    }

    public static net.minecraft.world.entity.Mob summon(ServerLevel ow, boolean golem) {
        List<ServerPlayer> pool = new ArrayList<>();
        for (ServerPlayer p : ow.players()) if (p.isAlive() && !p.isSpectator() && !com.jo112ss.rpgmod.compat.Downed.is(p)) pool.add(p);
        java.util.Collections.shuffle(pool, new java.util.Random(ow.getRandom().nextLong()));
        String key = golem ? "golem" : "king_slime";
        for (ServerPlayer p : pool) {
            net.minecraft.world.entity.Mob boss = golem ? spawnNear(ow, p, ModRegistries.GOLEM.get()) : spawnNear(ow, p);
            if (boss != null) {
                BossEvents.broadcast(ow.getServer(), Component.translatable("rpgmod.boss." + key + ".appeared", p.getDisplayName()).withStyle(ChatFormatting.RED));
                for (ServerPlayer q : ow.players()) {
                    q.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("rpgmod.boss." + key + ".near", p.getDisplayName()).withStyle(ChatFormatting.RED)));
                }
                return boss;
            }
        }
        return null;
    }

    /**
     * A free spot 20-30 blocks from the player: on the surface if that is near their height, else an
     * open space at their own height (a big cave), else the surface anyway.
     */
    public static KingSlime spawnNear(ServerLevel level, ServerPlayer p) {
        return spawnNear(level, p, ModRegistries.KING_SLIME.get());
    }

    public static <T extends net.minecraft.world.entity.Mob> T spawnNear(ServerLevel level, ServerPlayer p, net.minecraft.world.entity.EntityType<T> type) {
        T king = type.create(level, EntitySpawnReason.EVENT);
        if (king == null) return null;
        boolean golem = king instanceof Golem;
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
                king.snapTo(x + 0.5, y, z + 0.5, rnd.nextFloat() * 360, 0);
                BlockPos below = BlockPos.containing(x, y - 1, z);
                if (!level.getFluidState(below).isEmpty() && pass < 2) continue;
                if (!level.noCollision(king) || level.containsAnyLiquid(king.getBoundingBox())) continue;
                king.setPersistenceRequired();
                level.addFreshEntity(king);
                com.jo112ss.rpgmod.job.fx.ServerFx.of(golem ? "golem_spawn" : "king_spawn", king.position()).a(king.getBbWidth()).send(level, 128);
                level.playSound(null, king.blockPosition(), golem ? SoundEvents.STONE_BREAK : SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 4.0F, 0.3F);
                level.playSound(null, king.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 3.0F, golem ? 0.4F : 0.6F);
                RpgMod.LOGGER.info("[blood moon] {} Lv{} near {} at {}", golem ? "Golem" : "King Slime", MobLevels.level(king), p.getGameProfile().name(), king.blockPosition());
                return king;
            }
        }
        return null;
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
