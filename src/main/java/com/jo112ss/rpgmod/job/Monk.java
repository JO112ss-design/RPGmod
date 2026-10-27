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
 * The monk's ki (0 to 5): knuckle hits and some skills build it - three gains make a point
 * ({@link #PARTS}); the ki blast spends it all, and <b>holding right click with knuckles charges a ki
 * shot</b>, one point per charge (every ki job: monk, champion, god hand). It fades one point a second
 * after 8 seconds without building any.
 */
public final class Monk {
    private Monk() {}

    public static final int COLOR = 0xE67E22;
    public static final int FADE_AFTER = 160;
    /** Game time ki was last built, per player. */
    private static final Map<UUID, Long> LAST = new HashMap<>();
    /** Gains that make one point of ki (a knuckle hit is one gain). */
    public static final int PARTS = 3;
    /** Ticks of holding the right button per charge of a ki shot (each charge is one point of ki). */
    public static final int CHARGE_EVERY = 8;
    /** Game time a ki shot started charging, per player. */
    private static final Map<UUID, Long> CHARGING = new HashMap<>();
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

    /** {@code n} gains of ki: every {@link #PARTS} gains make a point. */
    public static void add(ServerPlayer p, int n) {
        JobData d = JobManager.data(p);
        LAST.put(p.getUUID(), p.level().getGameTime());
        if (n <= 0 || d.ki() >= d.maxKi()) return;
        int parts = d.kiParts() + n, before = d.ki();
        d.setKi(d.ki() + parts / PARTS);
        d.setKiParts(d.ki() >= d.maxKi() ? 0 : parts % PARTS);
        if (d.ki() > before) {
            ServerFx.of("n_ki", p.position()).follow(p).b(d.ki()).color(0xFFD27A).send((ServerLevel) p.level());
            if (d.ki() >= d.maxKi()) p.level().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.6F);
        }
        JobManager.sync(p);
    }

    /** Ki all the way up (meditation's end). */
    public static void fill(ServerPlayer p) {
        JobData d = JobManager.data(p);
        LAST.put(p.getUUID(), p.level().getGameTime());
        if (d.ki() >= d.maxKi()) return;
        d.setKi(d.maxKi());
        d.setKiParts(0);
        ServerFx.of("n_ki", p.position()).follow(p).b(d.ki()).color(0xFFD27A).send((ServerLevel) p.level());
        p.level().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.6F);
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

    /** Charges a ki shot holding for {@code held} ticks with {@code ki} points: one per {@link #CHARGE_EVERY} ticks, up to the ki there is. */
    public static int charges(long held, int ki) {
        return (int) Math.max(0, Math.min(ki, held / CHARGE_EVERY));
    }

    /** Right button down with knuckles: a ki shot starts charging (a dull click with no ki). */
    public static void press(ServerPlayer p) {
        JobData d = JobManager.data(p);
        if (!d.kiJob() || !holdsKnuckles(p) || !p.isAlive() || p.isSpectator()) return;
        if (d.ki() <= 0) {
            CHARGING.remove(p.getUUID());
            p.level().playSound(null, p.blockPosition(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.4F, 1.8F);
            return;
        }
        CHARGING.put(p.getUUID(), p.level().getGameTime());
        ServerFx.of("x_aura", p.position()).follow(p).a(1.5).b(0).color(0xFFC860).send((ServerLevel) p.level(), 32);
    }

    /** Right button up: the shot goes, one point of ki per charge (let go before the first charge: nothing). */
    public static void release(ServerPlayer p) {
        Long start = CHARGING.remove(p.getUUID());
        if (start == null) return;
        JobData d = JobManager.data(p);
        if (!d.kiJob() || !holdsKnuckles(p) || !p.isAlive() || p.isSpectator()) return;
        int n = charges(p.level().getGameTime() - start, d.ki());
        if (n <= 0) return;
        d.setKi(d.ki() - n);
        JobManager.sync(p);
        p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, p.getMainHandItem().getAttackAnimation(), true);
        MoreJobSkills.kiShot(p, n);
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
        if (d.ki() <= 0 && d.kiParts() <= 0) return;
        Long last = LAST.get(p.getUUID());
        long now = p.level().getGameTime();
        if (last == null || now - last > FADE_AFTER) {
            if (d.kiParts() > 0) d.setKiParts(0); // the part-built point goes first
            else d.setKi(d.ki() - 1);
            JobManager.sync(p);
        }
    }

    static void forget(UUID id) {
        LAST.remove(id);
        DIAMOND_LAST.remove(id);
        CHARGING.remove(id);
    }
}
