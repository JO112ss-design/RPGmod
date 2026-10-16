package com.jo112ss.rpgmod.client.fx;

import com.jo112ss.rpgmod.RpgMod;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Client tick scheduler for effects that play out over time (a bolt in flight, a spreading ring,
 * an aura that follows a player), plus a small camera shake for heavy impacts.
 */
@EventBusSubscriber(modid = RpgMod.MODID, value = Dist.CLIENT)
public final class FxAnims {
    private FxAnims() {}

    @FunctionalInterface
    public interface Anim {
        /** @return true to stop early. */
        boolean tick(int age);
    }

    private record Running(Anim anim, int duration, int[] age) {}

    private static final List<Running> ANIMS = new ArrayList<>();
    private static final List<Running> PENDING = new ArrayList<>();

    /** Calls {@code anim} with ages 0 .. duration-1 (once per client tick), starting now. */
    public static void run(int duration, Anim anim) {
        Running r = new Running(anim, duration, new int[] {0});
        if (step(r)) PENDING.add(r);
    }

    /** Calls {@code body} once after {@code delay} ticks. */
    public static void later(int delay, Runnable body) {
        if (delay <= 0) {
            body.run();
            return;
        }
        run(delay + 1, age -> {
            if (age == delay) body.run();
            return false;
        });
    }

    /** Like {@link #run} but stops when the entity is gone. */
    public static void follow(Entity e, int duration, Anim anim) {
        if (e == null) return;
        run(duration, age -> e.isRemoved() || anim.tick(age));
    }

    /** @return true if it should keep running. */
    private static boolean step(Running r) {
        int age = r.age[0]++;
        if (age >= r.duration) return false;
        try {
            return !r.anim.tick(age) && age + 1 < r.duration;
        } catch (RuntimeException ex) {
            RpgMod.LOGGER.error("Skill effect failed", ex);
            return false;
        }
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            ANIMS.clear();
            PENDING.clear();
            shake = 0;
            return;
        }
        if (mc.isPaused()) return;
        Iterator<Running> it = ANIMS.iterator();
        while (it.hasNext()) {
            if (!step(it.next())) it.remove();
        }
        // started since the last tick (they already played their first frame when they were created)
        ANIMS.addAll(PENDING);
        PENDING.clear();
        if (shakeTicks > 0) shakeTicks--;
        else shake = 0;
    }

    // ------------------------------------------------------------------ camera shake

    private static float shake;
    private static int shakeTicks, shakeMax;

    /** Shakes the camera of the local player if they are near {@code at}. */
    public static void shake(Vec3 at, float strength, int ticks, double radius) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        double d = mc.player.position().distanceTo(at);
        if (d > radius) return;
        float s = strength * (float) (1 - d / radius * 0.7);
        if (s > shake * shakeTicks / Math.max(1F, shakeMax)) {
            shake = s;
            shakeTicks = shakeMax = ticks;
        }
    }

    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        if (shakeTicks <= 0 || shake <= 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        float t = (shakeTicks - (float) event.getPartialTick()) / Math.max(1, shakeMax);
        float amp = shake * Math.max(0, t) * Math.max(0, t);
        float time = (mc.player.tickCount + (float) event.getPartialTick());
        event.setPitch(event.getPitch() + amp * (float) Math.sin(time * 2.9) * 1.6F);
        event.setYaw(event.getYaw() + amp * (float) Math.sin(time * 3.7 + 1.3) * 1.2F);
        event.setRoll(event.getRoll() + amp * (float) Math.sin(time * 2.3 + 0.7) * 1.4F);
    }
}
