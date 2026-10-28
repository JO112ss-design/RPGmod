package com.jo112ss.rpgmod.job;

import java.util.Random;
import java.util.function.IntUnaryOperator;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/**
 * The notes of a song: not one note every half second any more, but bars of 2 seconds, each
 * played to a rhythm picked from the song's style (calm songs leave room, lively ones run in
 * quavers, dark ones go off the beat). Which rhythm comes in which bar is drawn from the song's
 * start time, so the server and every client get the same chart, and a new performance plays
 * differently. Each song also has its own instrument and tune for the notes the bard hits.
 * <p>
 * A note is known by its id: bar x {@link #PER_BAR} + its place in the bar.
 */
public final class SongChart {
    private SongChart() {}

    /** Ticks per bar (four beats of half a second), and the most notes a bar can hold. */
    public static final int BAR = 40, PER_BAR = 8;

    private static final int[][] CALM = {{0, 20}, {0, 10, 20}, {0, 20, 30}, {0, 15, 20}, {0, 10, 20, 30}, {0, 30}};
    private static final int[][] STEADY = {{0, 10, 20, 30}, {0, 10, 20, 25, 30}, {0, 15, 20, 30}, {0, 10, 30}, {0, 5, 10, 20, 30}, {0, 20, 25, 30}};
    private static final int[][] LIVELY = {{0, 10, 20, 30}, {0, 5, 10, 20, 30}, {0, 10, 15, 20, 30, 35}, {0, 5, 10, 15, 20, 30}, {0, 10, 20, 25, 30, 35}, {0, 15, 20, 25, 30}};
    private static final int[][] DARK = {{0, 15, 30}, {0, 10, 25, 30}, {5, 15, 25, 35}, {0, 15, 20, 35}, {0, 10, 20, 30}, {0, 25, 30}};

    private static int[][] style(String song) {
        return switch (song) {
            case "heal_song", "lullaby", "mana_chord", "ward_melody", "harvest_song" -> CALM;
            case "wind_song", "haste_tune", "fanfare", "rhapsody", "thunder_tune" -> LIVELY;
            case "discord", "dirge", "seal_melody", "drain_tune" -> DARK;
            default -> STEADY;
        };
    }

    /** Which rhythm a bar plays (the first bar is always the style's plain one, as an intro). */
    private static int pick(String song, long seed, int bar) {
        if (bar <= 0) return 0;
        int n = style(song).length;
        int k = new Random(seed * 31L + bar * 7919L + song.hashCode()).nextInt(n);
        int prev = bar == 1 ? 0 : new Random(seed * 31L + (bar - 1) * 7919L + song.hashCode()).nextInt(n);
        return k == prev ? (k + 1) % n : k; // never the same rhythm twice in a row
    }

    /** Ticks after the first note of the song when note {@code id} comes, or -1 if that note does not exist. */
    public static int offset(String song, long seed, int id) {
        int bar = id / PER_BAR, k = id % PER_BAR;
        int[] r = style(song)[pick(song, seed, bar)];
        return k < r.length ? bar * BAR + r[k] : -1;
    }

    /** Calls {@code each} with the id of every note from {@code fromTick} to {@code toTick} (after the first note), in order. */
    public static void forEach(String song, long seed, double fromTick, double toTick, IntUnaryOperator each) {
        int b0 = Math.max(0, (int) Math.floor(fromTick / BAR)), b1 = (int) Math.floor(toTick / BAR);
        for (int bar = b0; bar <= b1; bar++) {
            int[] r = style(song)[pick(song, seed, bar)];
            for (int k = 0; k < r.length; k++) {
                int t = bar * BAR + r[k];
                if (t < fromTick || t > toTick) continue;
                if (each.applyAsInt(bar * PER_BAR + k) != 0) return;
            }
        }
    }

    /** How many notes come up to {@code tick} after the first one. */
    public static int count(String song, long seed, double tick) {
        int[] n = {0};
        forEach(song, seed, 0, tick, id -> {
            n[0]++;
            return 0;
        });
        return n[0];
    }

    /** The note nearest to {@code tick} (after the first note), or -1. */
    public static int nearest(String song, long seed, double tick) {
        int[] best = {-1};
        double[] bd = {Double.MAX_VALUE};
        forEach(song, seed, tick - BAR, tick + BAR, id -> {
            double d = Math.abs(offset(song, seed, id) - tick);
            if (d < bd[0]) {
                bd[0] = d;
                best[0] = id;
            }
            return 0;
        });
        return best[0];
    }

    // ------------------------------------------------------------------ how the notes sound

    /** The instrument of a song's notes. */
    public static Holder<SoundEvent> instrument(String song) {
        return switch (song) {
            case "war_song" -> SoundEvents.NOTE_BLOCK_GUITAR;
            case "heal_song" -> SoundEvents.NOTE_BLOCK_HARP;
            case "guard_song" -> SoundEvents.NOTE_BLOCK_IRON_XYLOPHONE;
            case "wind_song" -> SoundEvents.NOTE_BLOCK_FLUTE;
            case "discord" -> SoundEvents.NOTE_BLOCK_BIT;
            case "lullaby" -> SoundEvents.NOTE_BLOCK_CHIME;
            case "rhapsody" -> SoundEvents.NOTE_BLOCK_BANJO;
            case "fanfare" -> SoundEvents.NOTE_BLOCK_PLING;
            case "dirge" -> SoundEvents.NOTE_BLOCK_DIDGERIDOO;
            case "harvest_song" -> SoundEvents.NOTE_BLOCK_GUITAR;
            case "flame_melody" -> SoundEvents.NOTE_BLOCK_XYLOPHONE;
            case "haste_tune" -> SoundEvents.NOTE_BLOCK_COW_BELL;
            case "mana_chord" -> SoundEvents.NOTE_BLOCK_BELL;
            case "seal_melody" -> SoundEvents.NOTE_BLOCK_DIDGERIDOO;
            case "thunder_tune" -> SoundEvents.NOTE_BLOCK_BIT;
            case "ward_melody" -> SoundEvents.NOTE_BLOCK_BELL;
            case "drain_tune" -> SoundEvents.NOTE_BLOCK_FLUTE;
            default -> SoundEvents.NOTE_BLOCK_HARP;
        };
    }

    /** Pitches of the scale (note block steps), low to high. */
    private static final float[] SCALE = {0.5F, 0.56F, 0.63F, 0.67F, 0.75F, 0.84F, 0.94F, 1.0F, 1.12F, 1.26F, 1.33F, 1.5F, 1.68F, 1.89F, 2.0F};

    /** Each song's tune: the steps of the scale its notes walk through, over and over. */
    private static int[] tune(String song) {
        return switch (song) {
            case "war_song" -> new int[] {7, 7, 9, 11, 9, 7, 4, 7};
            case "heal_song" -> new int[] {7, 9, 11, 14, 11, 9, 7, 4};
            case "guard_song" -> new int[] {4, 7, 4, 7, 9, 7, 4, 2};
            case "wind_song" -> new int[] {9, 11, 12, 14, 12, 11, 9, 11};
            case "discord" -> new int[] {3, 6, 2, 8, 1, 6, 3, 9};
            case "lullaby" -> new int[] {11, 9, 7, 9, 7, 4, 7, 4};
            case "rhapsody" -> new int[] {7, 11, 9, 12, 11, 14, 12, 9};
            case "fanfare" -> new int[] {7, 7, 7, 11, 9, 11, 14, 14};
            case "dirge" -> new int[] {4, 3, 2, 0, 2, 3, 1, 0};
            case "harvest_song" -> new int[] {4, 7, 9, 7, 4, 7, 11, 9};
            case "flame_melody" -> new int[] {7, 9, 10, 12, 10, 9, 7, 5};
            case "haste_tune" -> new int[] {9, 11, 9, 12, 9, 11, 14, 11};
            case "mana_chord" -> new int[] {7, 11, 14, 11, 9, 12, 14, 12};
            case "seal_melody" -> new int[] {2, 5, 3, 6, 2, 5, 1, 0};
            case "thunder_tune" -> new int[] {7, 14, 9, 12, 7, 14, 11, 14};
            case "ward_melody" -> new int[] {4, 7, 9, 7, 11, 9, 7, 4};
            case "drain_tune" -> new int[] {9, 6, 8, 5, 7, 4, 6, 2};
            default -> new int[] {0, 2, 4, 5, 7, 9, 11, 12};
        };
    }

    /** Pitch of the n-th note the bard plays in this song. */
    public static float pitch(String song, int n) {
        int[] t = tune(song);
        return SCALE[Math.max(0, Math.min(SCALE.length - 1, t[Math.floorMod(n, t.length)]))];
    }
}
