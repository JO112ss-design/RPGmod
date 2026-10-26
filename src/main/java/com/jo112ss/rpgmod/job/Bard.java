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
 * The bard's performance. Songs are set on the lute like spells on a staff (four slots, picked with
 * the spell wheel); the "perform" skill plays the selected song - the maestro always plays two, the
 * inner and the outer ring's. A song lasts 12s: after a one second lead-in a note comes
 * every half second, and the bard clicks (attack button) in time with it - judged on the client
 * against the synced start time: perfect +2, good +1, miss -3 to the song's points (0..20). Every
 * second the song works on everyone around: the points set its strength (60% to 120%) and reach
 * (6 to 10 blocks). Ending a song with 16+ points plays an encore (6s more, once). While playing the
 * bard walks slower and cannot attack with the left button (the lute's sound wave still works).
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
        Ctx c;
        int judged;
        /** Notes played well so far (they walk through the song's tune). */
        int notes;
        boolean encore;
        /** The maestro's second song playing along (null = none), and its own context (power). */
        String second;
        Ctx secondCtx;
        /** Maestro's crescendo used in this song. */
        boolean crescendo;

        Perf(Ctx c) {
            this.c = c;
        }
    }

    private static final Map<UUID, Perf> PERF = new HashMap<>();

    public static boolean isSong(String id) {
        return switch (id) {
            case "war_song", "heal_song", "guard_song", "wind_song", "discord", "lullaby", "rhapsody", "fanfare", "dirge",
                 "flame_melody", "haste_tune", "mana_chord", "seal_melody", "thunder_tune", "ward_melody", "drain_tune" -> true;
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
            case "flame_melody" -> 0xFF8A40;
            case "haste_tune" -> 0xA0F0FF;
            case "mana_chord" -> 0x6A8CFF;
            case "seal_melody" -> 0xB070E0;
            case "thunder_tune" -> 0xFFF07A;
            case "rhapsody" -> 0xFF5AA8;
            case "fanfare" -> 0xFFD050;
            case "dirge" -> 0x8A7AB8;
            case "ward_melody" -> 0x9FD8FF;
            case "drain_tune" -> 0x7A60FF;
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

    /** Every song plays this long (not longer at higher levels), plus the encore. */
    public static final int SONG_SECONDS = 12;
    /**
     * The spellsinger's afterglow: seconds its song's effects stay on per point at the end of the song
     * (up to 100s); the ward melody's absorption stays this long on whoever still has it.
     */
    public static final double LINGER_PER_POINT = 5;
    public static final int WARD_LINGER_SECONDS = 200;

    /** The maestro's second song costs this share of its mana. */
    public static final double SECOND_SONG_COST = 0.5;

    /** The lute in the main hand (or empty). */
    public static net.minecraft.world.item.ItemStack lute(Player p) {
        var s = p.getMainHandItem();
        return com.jo112ss.rpgmod.item.ModItems.typeOf(s.getItem()) == com.jo112ss.rpgmod.item.WeaponType.LUTE ? s : net.minecraft.world.item.ItemStack.EMPTY;
    }

    /** A song set on the lute that this player knows, or null. */
    private static com.jo112ss.rpgmod.job.core.SkillDef song(JobData d, String id) {
        var def = com.jo112ss.rpgmod.job.core.JobDefs.skill(id);
        return def != null && isSong(id) && d.knows(id) ? def : null;
    }

    /** The songs "perform" would play now: the selected one, and for the maestro the outer ring's too. */
    public static com.jo112ss.rpgmod.job.core.SkillDef[] selectedSongs(Player p) {
        JobData d = JobManager.data(p);
        var l = lute(p).getOrDefault(com.jo112ss.rpgmod.registry.ModRegistries.SPELL_LOADOUT.get(), SpellLoadout.EMPTY);
        var first = song(d, l.selectedSpell());
        var second = d.isAdvancedAs("maestro") ? song(d, l.leftSpell()) : null;
        if (second != null && first != null && second.id().equals(first.id())) second = null;
        if (first == null && second != null) {
            first = second;
            second = null;
        }
        return new com.jo112ss.rpgmod.job.core.SkillDef[] {first, second};
    }

    /** Mana "perform" costs now (the selected song, plus half of the maestro's second). */
    public static double performCost(Player p) {
        var s = selectedSongs(p);
        if (s[0] == null) return 0;
        return Math.ceil(s[0].mana() + (s[1] != null ? s[1].mana() * SECOND_SONG_COST : 0));
    }

    /** The "perform" skill: plays the song(s) set on the lute. */
    static boolean perform(Ctx c) {
        ServerPlayer p = c.p();
        var songs = selectedSongs(p);
        if (songs[0] == null) {
            SkillExecutor.fail(p, lute(p).isEmpty() ? "rpgmod.bard.need_lute" : "rpgmod.bard.no_song");
            return false;
        }
        // (the mana is checked and paid by the cast: see JobManager.manaCost)
        ServerLevel level = c.level();
        Ctx first = new Ctx(p, level, songs[0], JobManager.powerMultiplier(p, songs[0]), 1.0);
        Ctx second = songs[1] == null ? null : new Ctx(p, level, songs[1], JobManager.powerMultiplier(p, songs[1]), 1.0);
        return start(first, second, SONG_SECONDS);
    }

    /** Starts (or restarts) a song for {@code seconds}. */
    static boolean start(Ctx c, int seconds) {
        return start(c, null, seconds);
    }

    /** Starts (or restarts) a song for {@code seconds}, with a second one playing along (maestro). */
    static boolean start(Ctx c, Ctx secondCtx, int seconds) {
        ServerPlayer p = c.p();
        JobData d = JobManager.data(p);
        long now = p.level().getGameTime();
        Perf old = PERF.get(p.getUUID());
        if (old != null && performing(p)) linger(old, p, d.songPoints()); // a song cut short by the next one
        d.setSong(c.def().id(), now, now + LEAD + seconds * 20L);
        Perf perf = new Perf(c);
        if (secondCtx != null) {
            perf.second = secondCtx.def().id();
            perf.secondCtx = secondCtx;
            p.sendSystemMessage(Component.translatable("rpgmod.bard.duet", Component.translatable("rpgmod.skill." + c.def().id()),
                    Component.translatable("rpgmod.skill." + perf.second)).withStyle(ChatFormatting.AQUA), true);
        }
        d.setSong2(perf.second);
        PERF.put(p.getUUID(), perf);
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
        d.setSong2("");
        p.removeEffect(ModEffects.PERFORMING);
        JobManager.sync(p);
    }

    /** A judged note from the client. */
    public static void judge(ServerPlayer p, int grade) {
        Perf perf = PERF.get(p.getUUID());
        JobData d = JobManager.data(p);
        if (perf == null || !performing(p)) return;
        long now = p.level().getGameTime();
        // no more judgements than notes that have come so far (with a little timing slack)
        long due = SongChart.count(d.song(), d.songStart(), now - d.songStart() - LEAD + 6) + 1;
        if (perf.judged >= due) return;
        perf.judged++;
        int add = grade == PERFECT ? 2 : grade == GOOD ? 1 : -3;
        d.setSongPoints(d.songPoints() + add);
        JobManager.sync(p);
        // the bard's note: the song's own instrument and tune while it goes well, a dull thud on a miss
        Holder<SoundEvent> s = grade == MISS ? SoundEvents.NOTE_BLOCK_BASS : SongChart.instrument(d.song());
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), s, SoundSource.PLAYERS, grade == MISS ? 0.6F : grade == PERFECT ? 1.0F : 0.75F,
                grade == MISS ? 0.6F : SongChart.pitch(d.song(), perf.notes++));
        if (grade != MISS) ServerFx.of("n_note", p.position().add(0, 1.6, 0)).follow(p).b(grade).color(songColor(d.song())).send((ServerLevel) p.level());
    }

    /** Every tick for every player (cheap when not playing). */
    static void tick(ServerPlayer p) {
        Perf perf = PERF.get(p.getUUID());
        if (perf == null) return;
        JobData d = JobManager.data(p);
        long now = p.level().getGameTime();
        if (d.song().isEmpty() || !p.isAlive() || !d.songJob()) {
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
                linger(perf, p, d.songPoints());
                stop(p);
                return;
            }
        }
        long t = now - d.songStart();
        if (t % 20 == 0) {
            p.addEffect(new MobEffectInstance(ModEffects.PERFORMING, 30, 0, false, false, true));
            if (t >= LEAD) {
                pulse(perf.c, p, d.song(), d.songPoints(), 30);
                if (perf.second != null) pulse(perf.secondCtx, p, perf.second, d.songPoints(), 30);
            }
        }
    }

    /**
     * The spellsinger's afterglow: when a song ends, its effects stay on for a while, longer the
     * better it was played (half a second per point: up to 10s). Lasting effects are put on once
     * for that long; the songs that work every second (mana, thunder) go on working.
     */
    private static void linger(Perf perf, ServerPlayer p, int points) {
        if (!"spellsinger".equals(JobManager.data(p).jobId())) return;
        String song = perf.c.def().id();
        if (song.equals("ward_melody")) {
            // the absorption the party still has stays on (as it is: not topped up)
            int keep = WARD_LINGER_SECONDS * 20;
            int n = 0;
            for (ServerPlayer a : SkillExecutor.allies(p, radius(points))) {
                MobEffectInstance abs = a.getEffect(MobEffects.ABSORPTION);
                if (abs == null || a.getAbsorptionAmount() <= 0) continue;
                a.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, keep, abs.getAmplifier(), true, true), p);
                n++;
            }
            if (n > 0) p.sendSystemMessage(Component.translatable("rpgmod.bard.linger", Component.translatable("rpgmod.skill." + song), WARD_LINGER_SECONDS)
                    .withStyle(ChatFormatting.LIGHT_PURPLE), true);
            return;
        }
        int ticks = (int) Math.round(points * LINGER_PER_POINT * 20);
        if (ticks < 20) return;
        pulse(perf.c, p, song, points, ticks);
        if (song.equals("mana_chord") || song.equals("thunder_tune")) {
            SkillTasks.run(age -> {
                if (!p.isAlive()) return true;
                if (age > 0 && age % 20 == 0) pulse(perf.c, p, song, points, 30);
                return age >= ticks;
            });
        }
        p.sendSystemMessage(Component.translatable("rpgmod.bard.linger", Component.translatable("rpgmod.skill." + song), ticks / 20)
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        ServerFx.of("x_ring", p.position()).a(radius(points)).b(2).color(songColor(song)).send((ServerLevel) p.level());
    }

    /** One second of a song on everyone around (its effects lasting {@code dur} ticks). */
    private static void pulse(Ctx c, ServerPlayer p, String song, int points, int dur) {
        ServerLevel level = (ServerLevel) p.level();
        double f = factor(points), r = radius(points);
        int amp = Math.max(0, (int) Math.round(5 * f) - 1);
        int singerAmp = Math.max(0, (int) Math.round(3 * f) - 1); // the spellsinger's songs: lower
        int color = songColor(song);
        switch (song) {
            case "war_song" -> allies(p, r, a -> a.addEffect(new MobEffectInstance(ModEffects.WAR_SONG, dur, amp, true, true), p));
            case "guard_song" -> allies(p, r, a -> a.addEffect(new MobEffectInstance(ModEffects.GUARD_SONG, dur, amp, true, true), p));
            case "wind_song" -> allies(p, r, a -> a.addEffect(new MobEffectInstance(ModEffects.WIND_SONG, dur, amp, true, true), p));
            case "heal_song" -> allies(p, r, a -> {
                a.addEffect(new MobEffectInstance(ModEffects.HEAL_SONG, dur, 0, true, true), p);
                HealthScale.heal(a, (float) (a.getMaxHealth() * 0.02 * f));
            });
            case "discord" -> {
                List<LivingEntity> foes = SkillExecutor.enemies(p, p.position(), r);
                for (LivingEntity e : foes) {
                    SkillExecutor.hurt(c, e, (float) (songPower(c, song) * f), SkillExecutor.magic(c));
                    e.addEffect(new MobEffectInstance(ModEffects.ARMOR_BREAK, dur, 0), p);
                }
                if (!foes.isEmpty()) ServerFx.of("hit", p.position()).color(color).a(0.8).points(foes).send(level);
            }
            case "lullaby" -> {
                for (LivingEntity e : SkillExecutor.enemies(p, p.position(), r)) {
                    e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, dur, f >= 0.9 ? 2 : 1), p);
                    e.addEffect(new MobEffectInstance(ModEffects.ATTACK_DOWN, dur, f >= 0.9 ? 1 : 0), p);
                }
            }
            // spellsinger: magic on the party's weapons
            // the spellsinger's songs are weaker than the bard's, but they stay on long after the song (see linger)
            case "flame_melody" -> allies(p, r, a -> a.addEffect(new MobEffectInstance(ModEffects.FLAME_SONG, dur, singerAmp, true, true), p));
            case "haste_tune" -> allies(p, r, a -> {
                a.addEffect(new MobEffectInstance(MobEffects.HASTE, dur, 0, true, true), p);
                a.addEffect(new MobEffectInstance(MobEffects.SPEED, dur, 0, true, true), p);
            });
            case "mana_chord" -> allies(p, r, a -> JobManager.restoreMana(a, JobManager.maxMana(a) * SINGER_MANA * f));
            case "seal_melody" -> {
                for (LivingEntity e : SkillExecutor.enemies(p, p.position(), r)) {
                    e.addEffect(new MobEffectInstance(ModEffects.EXPOSED, dur, 0), p);
                    e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, dur, 0), p);
                }
            }
            case "thunder_tune" -> {
                List<LivingEntity> foes = SkillExecutor.enemies(p, p.position(), r);
                if (!foes.isEmpty()) {
                    LivingEntity e = foes.get(p.getRandom().nextInt(foes.size()));
                    SkillExecutor.hurt(c, e, (float) (songPower(c, song) * f * SINGER_DAMAGE), SkillExecutor.magic(c));
                    ServerFx.of("x_strike", e.position()).a(1).color(color).point(e.position().add(0, e.getBbHeight() * 0.5, 0)).send(level);
                    level.playSound(null, e.blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.5F, 1.6F);
                }
            }
            case "rhapsody" -> allies(p, r, a -> a.addEffect(new MobEffectInstance(ModEffects.RHAPSODY, dur, amp, true, true), p));
            case "fanfare" -> allies(p, r, a -> {
                a.addEffect(new MobEffectInstance(ModEffects.FANFARE, dur, 0, true, true), p);
                JobData ad = JobManager.data(a);
                if (ad.hasJob()) { // the skills come back sooner: a share of a second off every second
                    ad.shiftCooldowns(a.level().getGameTime(), Math.round(20 * FANFARE_SPEED * f));
                    JobManager.sync(a);
                }
            });
            case "dirge" -> {
                List<LivingEntity> foes = SkillExecutor.enemies(p, p.position(), r);
                float each = (float) (songPower(c, song) * f);
                for (LivingEntity e : foes) SkillExecutor.hurt(c, e, each, SkillExecutor.magic(c));
                if (!foes.isEmpty()) {
                    ServerFx.of("hit", p.position()).color(color).a(0.6).points(foes).send(level);
                    var party = SkillExecutor.allies(p, r);
                    float heal = each * foes.size() * DIRGE_DRAIN / Math.max(1, party.size());
                    for (ServerPlayer a : party) HealthScale.heal(a, heal);
                }
            }
            case "ward_melody" -> allies(p, r, a -> ward(p, a, f, dur));
            case "drain_tune" -> allies(p, r, a -> a.addEffect(new MobEffectInstance(ModEffects.DRAIN_TUNE, dur, singerAmp, true, true), p));
            default -> { }
        }
        ServerFx.of("n_song", p.position()).follow(p).a(r).b(f).color(color).send(level);
    }

    /** Rhapsody: crit chance (percent) per level of the song (levels 2 to 6 by the points). */
    public static final double RHAPSODY_CRIT = 3;

    /** Fanfare: seconds of cooldown taken off every second (at full strength 1.0 x this... times the strength). */
    public static final double FANFARE_SPEED = 0.35;
    /** Dirge: share of the damage dealt that heals the party (split among them). */
    public static final float DIRGE_DRAIN = 0.3F;
    /** Ward melody: absorption on each ally worth this share of their max health (times the strength: 15% to 30%). */
    public static final double WARD_SHARE = 0.25;
    /** The spellsinger's songs: mana per second (share of the max, times the strength) and the thunder's damage share. */
    public static final double SINGER_MANA = 0.012, SINGER_DAMAGE = 0.6;
    /** Ward melody: once someone's absorption is broken, none for them for this long. */
    public static final int WARD_BROKEN_TICKS = 80;

    /** Who has the ward melody's absorption now, and who broke it (until when they get none). */
    private static final java.util.Set<UUID> WARDED = new java.util.HashSet<>();
    private static final Map<UUID, Long> WARD_BROKEN = new HashMap<>();

    /**
     * Ward melody: vanilla absorption (yellow on the health bar). It is not topped up while it lasts:
     * what a blow takes is gone; broken, it comes back only after {@link #WARD_BROKEN_TICKS}.
     */
    private static void ward(ServerPlayer singer, ServerPlayer a, double f, int dur) {
        long now = a.level().getGameTime();
        UUID id = a.getUUID();
        if (WARDED.contains(id) && !a.hasEffect(MobEffects.ABSORPTION)) { // it was broken
            WARDED.remove(id);
            WARD_BROKEN.put(id, now + WARD_BROKEN_TICKS);
            ServerFx.of("x_burst", a.position().add(0, 1.0, 0)).a(0.8).b(1).color(0x9FD8FF).send((ServerLevel) a.level());
            a.level().playSound(null, a.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.7F, 1.4F);
        }
        if (WARD_BROKEN.getOrDefault(id, 0L) > now) return;
        int amp = Math.max(0, (int) Math.round(a.getMaxHealth() * WARD_SHARE * f / 4.0) - 1);
        a.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, dur, amp, true, true), singer);
        WARDED.add(id);
    }

    /** Maestro's crescendo: the song swells at once (once per song). */
    static boolean crescendo(Ctx c) {
        ServerPlayer p = c.p();
        Perf perf = PERF.get(p.getUUID());
        if (perf == null || !performing(p)) {
            SkillExecutor.fail(p, "rpgmod.bard.not_playing");
            return false;
        }
        if (perf.crescendo) {
            SkillExecutor.fail(p, "rpgmod.bard.crescendo_used");
            return false;
        }
        perf.crescendo = true;
        JobData d = JobManager.data(p);
        d.setSongPoints(d.songPoints() + CRESCENDO_POINTS);
        JobManager.sync(p);
        ServerFx.of("x_ring", p.position()).a(radius(d.songPoints())).b(2).color(songColor(d.song())).send(c.level());
        ServerFx.of("n_notes", p.position()).follow(p).a(2).color(songColor(d.song())).send(c.level());
        p.level().playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1.0F, 1.2F);
        p.level().playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 0.8F, 1.6F);
        return true;
    }

    public static final int CRESCENDO_POINTS = 6, CHEER_POINTS = 6;

    /** Cheer: +6 to the song playing (if any), and a short burst of the war song on the party. */
    static boolean cheer(Ctx c) {
        ServerPlayer p = c.p();
        JobData d = JobManager.data(p);
        if (performing(p)) {
            d.setSongPoints(d.songPoints() + CHEER_POINTS);
            JobManager.sync(p);
        }
        int t = (int) Math.round(c.def().duration() * 20);
        for (ServerPlayer a : SkillExecutor.allies(p, c.def().radius())) {
            a.addEffect(new MobEffectInstance(ModEffects.WAR_SONG, t, 2, true, true), p);
            ServerFx.of("n_note", a.position().add(0, 2.0, 0)).follow(a).b(PERFECT).color(0xFFD050).send(c.level());
        }
        ServerFx.of("x_ring", p.position()).a(c.def().radius()).b(1).color(0xFFD050).send(c.level());
        p.level().playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 1.0F, 1.5F);
        p.level().playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 0.8F, 2.0F);
        return true;
    }

    /** A song's damage per second before the strength: its own ratio x the magic attack when it started. */
    private static double songPower(Ctx c, String song) {
        var def = com.jo112ss.rpgmod.job.core.JobDefs.skill(song);
        return def == null ? 0 : def.power() * c.power();
    }

    /** The bard's songs, and the spellsinger's, in the order modulation walks through them. */
    private static final List<String> BARD_SONGS = List.of("war_song", "heal_song", "guard_song", "wind_song", "discord", "lullaby", "rhapsody", "fanfare", "dirge");
    private static final List<String> SINGER_SONGS = List.of("flame_melody", "haste_tune", "mana_chord", "seal_melody", "thunder_tune", "ward_melody", "drain_tune");

    /** Maestro's modulation: the next song, same time left and points, plus a little. */
    static boolean modulate(Ctx c) {
        ServerPlayer p = c.p();
        JobData d = JobManager.data(p);
        if (!performing(p)) {
            SkillExecutor.fail(p, "rpgmod.bard.not_playing");
            return false;
        }
        // the next song this player knows (one never learnt is skipped)
        List<String> order = SINGER_SONGS.contains(d.song()) ? SINGER_SONGS : BARD_SONGS;
        String next = d.song();
        for (int k = 1; k <= order.size(); k++) {
            String s = order.get((order.indexOf(d.song()) + k) % order.size());
            if (d.knows(s) && !s.equals(PERF.get(p.getUUID()) == null ? null : PERF.get(p.getUUID()).second)) {
                next = s;
                break;
            }
        }
        if (next.equals(d.song())) {
            SkillExecutor.fail(p, "rpgmod.bard.no_song");
            return false;
        }
        Perf playing = PERF.get(p.getUUID());
        if (playing != null) playing.c = new Ctx(p, c.level(), com.jo112ss.rpgmod.job.core.JobDefs.skill(next), JobManager.powerMultiplier(p, com.jo112ss.rpgmod.job.core.JobDefs.skill(next)), 1.0);
        d.setSong(next, d.songStart(), d.songEnd());
        d.setSongPoints(d.songPoints() + 4);
        JobManager.sync(p);
        p.sendSystemMessage(Component.translatable("rpgmod.bard.modulated", Component.translatable("rpgmod.skill." + next)).withStyle(ChatFormatting.AQUA), true);
        ServerFx.of("x_ring", p.position()).a(radius(d.songPoints())).b(2).color(songColor(next)).send(c.level());
        ServerFx.of("n_notes", p.position()).follow(p).a(1).color(songColor(next)).send(c.level());
        p.level().playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 1.0F, 1.5F);
        return true;
    }

    /** Spellsinger's grand concerto: the weapon songs at full strength (every second while it lasts). */
    static void concerto(ServerPlayer p, double r, int ticks) {
        for (ServerPlayer a : SkillExecutor.allies(p, r)) {
            a.addEffect(new MobEffectInstance(ModEffects.FLAME_SONG, ticks, 5, true, true), p);
            a.addEffect(new MobEffectInstance(MobEffects.HASTE, ticks, 1, true, true), p);
            a.addEffect(new MobEffectInstance(MobEffects.SPEED, ticks, 1, true, true), p);
            JobManager.restoreMana(a, JobManager.maxMana(a) * 0.024);
        }
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

    /** The maestro's second song playing now (or null). */
    public static String secondSong(Player p) {
        Perf perf = PERF.get(p.getUUID());
        return perf == null ? null : perf.second;
    }

    static void forget(UUID id) {
        PERF.remove(id);
    }
}
