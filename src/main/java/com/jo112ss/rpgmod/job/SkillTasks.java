package com.jo112ss.rpgmod.job;

import com.jo112ss.rpgmod.RpgMod;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Small server-side scheduler for skills that unfold over several ticks (a projectile in flight, a
 * shockwave that spreads, a meteor that lands later). Tasks run on the server thread at the end of
 * each server tick.
 */
@EventBusSubscriber(modid = RpgMod.MODID)
public final class SkillTasks {
    private SkillTasks() {}

    @FunctionalInterface
    public interface Task {
        /** @param age 0 on the first call; return true when finished. */
        boolean tick(int age);
    }

    private static final class Entry {
        final Task task;
        int age;

        Entry(Task task) {
            this.task = task;
        }
    }

    private static final List<Entry> TASKS = new ArrayList<>();
    private static final List<Entry> PENDING = new ArrayList<>();

    /** Runs {@code task} every tick, starting with the next server tick, until it returns true. */
    public static void run(Task task) {
        PENDING.add(new Entry(task));
    }

    /** Runs {@code r} once after {@code delay} ticks (1 = next tick). */
    public static void later(int delay, Runnable r) {
        run(age -> {
            if (age + 1 < delay) return false;
            r.run();
            return true;
        });
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        if (!PENDING.isEmpty()) {
            TASKS.addAll(PENDING);
            PENDING.clear();
        }
        Iterator<Entry> it = TASKS.iterator();
        while (it.hasNext()) {
            Entry e = it.next();
            boolean done;
            try {
                done = e.task.tick(e.age++);
            } catch (RuntimeException ex) {
                RpgMod.LOGGER.error("Skill task failed", ex);
                done = true;
            }
            if (done || e.age > 20 * 60) it.remove();
        }
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) {
        TASKS.clear();
        PENDING.clear();
    }
}
