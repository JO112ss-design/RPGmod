package com.jo112ss.rpgmod.job;

import com.jo112ss.rpgmod.job.SkillExecutor.Ctx;
import com.jo112ss.rpgmod.job.fx.ServerFx;
import com.jo112ss.rpgmod.registry.ModEffects;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * The bard's performance. A song skill starts a song (12s): after a one second lead-in a note comes
 * every half second, and the bard clicks (attack button) in time with it - judged on the client
 * against the synced start time: perfect +2, good +1, miss -3 to the song's points (0..20). Every
 * second the song works on everyone around: the points set its strength (60% to 120%) and reach
 * (6 to 10 blocks). Ending a song with 16+ points plays an encore (6s more, once). While playing the
 * bard walks slower and cannot attack.
 */
public final class Bard {
    private Bard() {}

    public static final int COLOR = 0x48C9B0;
    /** Ticks before the first note, and between notes. */
    public static final int LEAD = 20, BEAT = 10;
    public static final int ENCORE_POINTS = 16, ENCORE_TICKS = 120;
    public static final int PERFECT = 2, GOOD = 1, MISS = 0;

    /** A song in progress. */
    private static final class Perf {
        final Ctx c;
        int judged;
        boolean encore;

        Perf(Ctx c) {
            this.c = c;
        }
    }

    private static final Map<UUID, Perf> PERF = new HashMap<>();

    public static boolean isSong(String id) {
        return switch (id) {
            case "war_song", "heal_song", "guard_song", "wind_song", "discord", "lullaby" -> true;
            default -> false;
        };
    }

    public static int songColor(String song) {
        return switch (song) {
            case "war_song" -> 0xFF7050;
            case "heal_song" -> 0x70FF90;
            case "guard_song" -> 0x70A0FF;
            case "wind_song" -> 0xB0FFE0;
            case "discord" -> 0xC060FF;
            case "lullaby" -> 0xF0B0FF;
            default -> COLOR;
        };
    }

    /** Song strength from the points: 0.6 to 1.2. */
    public static double factor(int points) {
        return 0.6 + 0.03 * points;
    }

    public static double radius(int points) {
        return 6 + 4.0 * points / JobData.MAX_SONG_POINTS;
    }

    public static boolean performing(Player p) {
        JobData d = JobManager.data(p);
        return !d.song().isEmpty() && d.songEnd() > p.level().getGameTime();
    }

    /** Starts (or restarts) a song for {@code seconds}. */
    static boolean start(Ctx c, int seconds) {
        ServerPlayer p = c.p();
        JobData d = JobManager.data(p);
        long now = p.level().getGameTime();
        d.setSong(c.def().id(), now, now + LEAD + seconds * 20L);
        PERF.put(p.getUUID(), new Perf(c));
        JobManager.sync(p);
        ServerFx.of("x_ring", p.position()).a(radius(d.songPoints())).b(1).color(songColor(c.def().id())).send(c.level());
        ServerFx.of("n_notes", p.position()).follow(p).a(1.5).color(songColor(c.def().id())).send(c.level());
        p.level().playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_HARP.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
        p.level().playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_HARP.value(), SoundSource.PLAYERS, 0.8F, 1.26F);
        p.level().playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_HARP.value(), SoundSource.PLAYERS, 0.7F, 1.5F);
        return true;
    }

    /** Ends the song (finale, death, job change...). */
    public static void stop(ServerPlayer p) {
        JobData d = JobManager.data(p);
        PERF.remove(p.getUUID());
        if (d.song().isEmpty()) return;
        d.setSong("", 0, 0);
        p.removeEffect(ModEffects.PERFORMING);
        JobManager.sync(p);
    }

    private static final float[] SCALE = {0.5F, 0.56F, 0.63F, 0.67F, 0.75F, 0.84F, 0.94F, 1.0F, 1.12F, 1.26F, 1.33F, 1.5F, 1.68F, 1.89F, 2.0F};

    /** A judged note from the client. */
    public static void judge(ServerPlayer p, int grade) {
        Perf perf = PERF.get(p.getUUID());
        JobData d = JobManager.data(p);
        if (perf == null || !performing(p)) return;
        long now = p.level().getGameTime();
        // no more judgements than notes that have come so far (+1 for timing slack)
        long due = (now - d.songStart() - LEAD) / BEAT + 2;
        if (perf.judged >= due) return;
        perf.judged++;
        int add = grade == PERFECT ? 2 : grade == GOOD ? 1 : -3;
        d.setSongPoints(d.songPoints() + add);
        JobManager.sync(p);
        // the bard's note, on the scale while it goes well, a dull thud on a miss
        int step = Math.floorMod(perf.judged * 2 + (perf.judged / 4), SCALE.length);
        Holder<SoundEvent> s = grade == MISS ? SoundEvents.NOTE_BLOCK_BASS : grade == PERFECT ? SoundEvents.NOTE_BLOCK_HARP : SoundEvents.NOTE_BLOCK_GUITAR;
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), s, SoundSource.PLAYERS, grade == MISS ? 0.6F : 0.9F, grade == MISS ? 0.6F : SCALE[step]);
        if (grade != MISS) ServerFx.of("n_note", p.position().add(0, 1.6, 0)).follow(p).b(grade).color(songColor(d.song())).send((ServerLevel) p.level());
    }

    /** Every tick for every player (cheap when not playing). */
    static void tick(ServerPlayer p) {
        Perf perf = PERF.get(p.getUUID());
        if (perf == null) return;
        JobData d = JobManager.data(p);
        long now = p.level().getGameTime();
        if (d.song().isEmpty() || !p.isAlive() || !"bard".equals(d.jobId())) {
            stop(p);
            return;
        }
        if (now >= d.songEnd()) {
            if (!perf.encore && d.songPoints() >= ENCORE_POINTS) {
                perf.encore = true;
                d.setSong(d.song(), d.songStart(), d.songEnd() + ENCORE_TICKS);
                JobManager.sync(p);
                p.sendSystemMessage(Component.translatable("rpgmod.bard.encore").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
                ServerFx.of("x_burst", p.position().add(0, 1, 0)).a(2).b(2).color(songColor(d.song())).send((ServerLevel) p.level());
                p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.5F);
            } else {
                stop(p);
                return;
            }
        }
        long t = now - d.songStart();
        if (t % 20 == 0) {
            p.addEffect(new MobEffectInstance(ModEffects.PERFORMING, 30, 0, false, false, true));
            if (t >= LEAD) pulse(perf.c, p, d.song(), d.songPoints());
        }
    }

    /** One second of a song on everyone around. */
    private static void pulse(Ctx c, ServerPlayer p, String song, int points) {
        ServerLevel level = (ServerLevel) p.level();
        double f = factor(points), r = radius(points);
        int amp = Math.max(0, (int) Math.round(5 * f) - 1);
        int color = songColor(song);
        switch (song) {
            case "war_song" -> allies(p, r, a -> a.addEffect(new MobEffectInstance(ModEffects.WAR_SONG, 30, amp, true, true), p));
            case "guard_song" -> allies(p, r, a -> a.addEffect(new MobEffectInstance(ModEffects.GUARD_SONG, 30, amp, true, true), p));
            case "wind_song" -> allies(p, r, a -> a.addEffect(new MobEffectInstance(ModEffects.WIND_SONG, 30, amp, true, true), p));
            case "heal_song" -> allies(p, r, a -> {
                a.addEffect(new MobEffectInstance(ModEffects.HEAL_SONG, 30, 0, true, true), p);
                HealthScale.heal(a, (float) (a.getMaxHealth() * 0.02 * f));
            });
            case "discord" -> {
                List<LivingEntity> foes = SkillExecutor.enemies(p, p.position(), r);
                for (LivingEntity e : foes) {
                    SkillExecutor.hurt(c, e, (float) (c.dmg(1) * f), SkillExecutor.magic(c));
                    e.addEffect(new MobEffectInstance(ModEffects.ARMOR_BREAK, 30, 0), p);
                }
                if (!foes.isEmpty()) ServerFx.of("hit", p.position()).color(color).a(0.8).points(foes).send(level);
            }
            case "lullaby" -> {
                for (LivingEntity e : SkillExecutor.enemies(p, p.position(), r)) {
                    e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, f >= 0.9 ? 2 : 1), p);
                    e.addEffect(new MobEffectInstance(ModEffects.ATTACK_DOWN, 30, f >= 0.9 ? 1 : 0), p);
                }
            }
            default -> { }
        }
        ServerFx.of("n_song", p.position()).follow(p).a(r).b(f).color(color).send(level);
    }

    static void allies(ServerPlayer p, double r, java.util.function.Consumer<ServerPlayer> each) {
        for (ServerPlayer a : SkillExecutor.allies(p, r)) each.accept(a);
    }

    /** Grand concert: the four songs at full strength. */
    static void grand(ServerPlayer p, double r, int ticks) {
        int amp = 5;
        for (ServerPlayer a : SkillExecutor.allies(p, r)) {
            a.addEffect(new MobEffectInstance(ModEffects.WAR_SONG, ticks, amp, true, true), p);
            a.addEffect(new MobEffectInstance(ModEffects.GUARD_SONG, ticks, amp, true, true), p);
            a.addEffect(new MobEffectInstance(ModEffects.WIND_SONG, ticks, amp, true, true), p);
            a.addEffect(new MobEffectInstance(ModEffects.HEAL_SONG, ticks, 0, true, true), p);
            HealthScale.heal(a, (float) (a.getMaxHealth() * 0.02 * factor(JobData.MAX_SONG_POINTS)));
        }
    }

    static void forget(UUID id) {
        PERF.remove(id);
    }
}
