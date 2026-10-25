package com.jo112ss.rpgmod.devtest;

import com.jo112ss.rpgmod.RpgMod;
import com.jo112ss.rpgmod.job.SkillTasks;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Server load test: about 80 monsters around a player shut in an obsidian box (so they keep trying
 * to reach them), the player stepping back and forth. Logs the average tick time and where the
 * server thread spends it (a simple stack sampler).
 */
@EventBusSubscriber(modid = RpgMod.MODID)
final class PerfTest {
    private PerfTest() {}

    private static boolean measuring;
    private static long tickStart, tickSum, tickMax;
    private static int ticks;
    private static final Map<String, Integer> INCL = new HashMap<>();
    private static final Map<String, Integer> SELF = new HashMap<>();
    private static int samples;
    private static volatile boolean sampling;

    @SubscribeEvent
    public static void onPre(ServerTickEvent.Pre e) {
        if (measuring) tickStart = System.nanoTime();
    }

    @SubscribeEvent
    public static void onPost(ServerTickEvent.Post e) {
        if (!measuring || tickStart == 0) return;
        long dt = System.nanoTime() - tickStart;
        tickSum += dt;
        tickMax = Math.max(tickMax, dt);
        ticks++;
    }

    static void run(ServerLevel level, ServerPlayer p, int seconds) {
        var server = level.getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set midnight");
        var mode = p.gameMode();
        boolean inv = p.getAbilities().invulnerable;
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        p.getAbilities().invulnerable = false;
        p.onUpdateAbilities();
        p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, seconds * 20 + 400, 4));
        p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, seconds * 20 + 400, 4));
        var diff = level.getDifficulty();
        server.setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
        // a closed obsidian box, 3 long inside
        BlockPos c = p.blockPosition();
        for (int dx = -2; dx <= 2; dx++) for (int dy = -1; dy <= 2; dy++) for (int dz = -1; dz <= 1; dz++) {
            boolean shell = Math.abs(dx) == 2 || dy == -1 || dy == 2 || Math.abs(dz) == 1;
            level.setBlockAndUpdate(c.offset(dx, dy, dz), shell ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.AIR.defaultBlockState());
        }
        p.teleportTo(c.getX() + 0.5, c.getY(), c.getZ() + 0.5);
        List<Mob> mobs = new ArrayList<>();
        var r = level.getRandom();
        Object[][] kinds = {{EntityTypes.ZOMBIE, 40}, {EntityTypes.SKELETON, 15}, {EntityTypes.CREEPER, 15}, {EntityTypes.SPIDER, 10}};
        SkillTasks.later(10, () -> {
            for (Object[] k : kinds) {
                for (int i = 0; i < (int) k[1]; i++) {
                    double ang = r.nextDouble() * Math.PI * 2, dist = 20 + r.nextDouble() * 70;
                    Mob m = (Mob) ((EntityType<?>) k[0]).create(level, EntitySpawnReason.NATURAL);
                    if (m == null) continue;
                    m.snapTo(c.getX() + Math.cos(ang) * dist, c.getY(), c.getZ() + Math.sin(ang) * dist, 0, 0);
                    m.setPersistenceRequired();
                    level.addFreshEntity(m);
                    mobs.add(m);
                }
            }
            RpgMod.LOGGER.info("[AUTOTEST] perf: {} monsters around the box", mobs.size());
        });
        // the player steps from one end of the box to the other every half second
        int total = seconds * 20;
        for (int t = 40; t < total + 60; t += 10) {
            int tt = t;
            SkillTasks.later(t, () -> {
                double x = c.getX() + 0.5 + ((tt / 10) % 2 == 0 ? -1 : 1);
                p.teleportTo(x, c.getY(), c.getZ() + 0.5);
                p.setHealth(p.getMaxHealth());
            });
        }
        SkillTasks.later(60, () -> start(server.getRunningThread()));
        SkillTasks.later(60 + total, () -> {
            stop();
            int targeting = 0;
            for (Mob m : mobs) if (m.isAlive() && m.getTarget() == p) targeting++;
            RpgMod.LOGGER.info("[AUTOTEST] perf: {} ticks, average {} ms, worst {} ms, {} of {} monsters alive and targeting",
                    ticks, String.format("%.2f", tickSum / 1e6 / Math.max(1, ticks)), String.format("%.1f", tickMax / 1e6), targeting, mobs.size());
            report();
            for (Mob m : mobs) m.discard();
            for (int dx = -2; dx <= 2; dx++) for (int dy = -1; dy <= 2; dy++) for (int dz = -1; dz <= 1; dz++) {
                level.setBlockAndUpdate(c.offset(dx, dy, dz), dy == -1 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState());
            }
            p.setGameMode(mode);
            p.getAbilities().invulnerable = inv;
            p.onUpdateAbilities();
            server.setDifficulty(diff, true);
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set noon");
        });
    }

    private static void start(Thread server) {
        measuring = true;
        tickSum = tickMax = tickStart = 0;
        ticks = 0;
        INCL.clear();
        SELF.clear();
        samples = 0;
        sampling = true;
        Thread s = new Thread(() -> {
            while (sampling) {
                StackTraceElement[] st = server.getStackTrace();
                boolean inTick = false;
                for (StackTraceElement f : st) {
                    if (f.getMethodName().equals("tickChildren")) {
                        inTick = true;
                        break;
                    }
                }
                if (inTick) {
                    synchronized (INCL) {
                        samples++;
                        java.util.Set<String> seen = new java.util.HashSet<>();
                        for (int i = 0; i < st.length; i++) {
                            String k = shortName(st[i]);
                            if (seen.add(k)) INCL.merge(k, 1, Integer::sum);
                        }
                        if (st.length > 0) SELF.merge(shortName(st[0]), 1, Integer::sum);
                    }
                }
                try {
                    Thread.sleep(1);
                } catch (InterruptedException ex) {
                    return;
                }
            }
        }, "rpgmod-perf-sampler");
        s.setDaemon(true);
        s.start();
    }

    private static String shortName(StackTraceElement f) {
        String c = f.getClassName();
        int dot = c.lastIndexOf('.');
        return c.substring(dot + 1) + "." + f.getMethodName();
    }

    private static void stop() {
        measuring = false;
        sampling = false;
    }

    private static void report() {
        synchronized (INCL) {
            RpgMod.LOGGER.info("[AUTOTEST] perf samples in tick: {}", samples);
            INCL.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(70)
                    .forEach(e -> RpgMod.LOGGER.info("[AUTOTEST] perf incl {}% {}", String.format("%5.1f", e.getValue() * 100.0 / Math.max(1, samples)), e.getKey()));
            SELF.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(25)
                    .forEach(e -> RpgMod.LOGGER.info("[AUTOTEST] perf self {}% {}", String.format("%5.1f", e.getValue() * 100.0 / Math.max(1, samples)), e.getKey()));
        }
    }
}
