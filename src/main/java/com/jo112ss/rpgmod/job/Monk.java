package com.jo112ss.rpgmod.job;

import com.jo112ss.rpgmod.item.ModItems;
import com.jo112ss.rpgmod.item.WeaponType;
import com.jo112ss.rpgmod.job.fx.ServerFx;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/**
 * The monk's ki (0 to 5): knuckle hits and some skills build it, the ki blast spends it. It fades one
 * point a second after 8 seconds without building any.
 */
public final class Monk {
    private Monk() {}

    public static final int COLOR = 0xE67E22;
    public static final int FADE_AFTER = 160;
    /** Game time ki was last built, per player. */
    private static final Map<UUID, Long> LAST = new HashMap<>();
    /** Diamond body: game time ki was last built by a hit (at most twice a second). */
    private static final Map<UUID, Long> DIAMOND_LAST = new HashMap<>();

    public static boolean isMonk(Player p) {
        return JobManager.data(p).kiJob();
    }

    public static boolean holdsKnuckles(Player p) {
        return ModItems.typeOf(p.getMainHandItem().getItem()) == WeaponType.KNUCKLE;
    }

    public static int ki(Player p) {
        return JobManager.data(p).ki();
    }

    public static void add(ServerPlayer p, int n) {
        JobData d = JobManager.data(p);
        LAST.put(p.getUUID(), p.level().getGameTime());
        if (n <= 0 || d.ki() >= d.maxKi()) return;
        d.setKi(d.ki() + n);
        ServerFx.of("n_ki", p.position()).follow(p).b(d.ki()).color(0xFFD27A).send((ServerLevel) p.level());
        if (d.ki() >= d.maxKi()) p.level().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.6F);
        JobManager.sync(p);
    }

    /** Spends all ki; returns how much there was. */
    public static int spend(ServerPlayer p) {
        JobData d = JobManager.data(p);
        int k = d.ki();
        if (k > 0) {
            d.setKi(0);
            JobManager.sync(p);
        }
        return k;
    }

    /** Diamond body: any hit builds ki (twice a second at most). */
    static void diamondHit(ServerPlayer p) {
        long now = p.level().getGameTime();
        Long last = DIAMOND_LAST.get(p.getUUID());
        if (last != null && now - last < 10) return;
        DIAMOND_LAST.put(p.getUUID(), now);
        add(p, 1);
    }

    /** Every second: ki fades after a while without fighting. */
    static void tick(ServerPlayer p) {
        JobData d = JobManager.data(p);
        if (d.ki() <= 0) return;
        Long last = LAST.get(p.getUUID());
        long now = p.level().getGameTime();
        if (last == null || now - last > FADE_AFTER) {
            d.setKi(d.ki() - 1);
            JobManager.sync(p);
        }
    }

    static void forget(UUID id) {
        LAST.remove(id);
        DIAMOND_LAST.remove(id);
    }
}
