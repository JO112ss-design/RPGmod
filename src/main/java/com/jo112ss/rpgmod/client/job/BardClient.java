package com.jo112ss.rpgmod.client.job;

import com.jo112ss.rpgmod.job.Bard;
import com.jo112ss.rpgmod.job.JobData;
import com.jo112ss.rpgmod.network.JobActionPayload;
import java.util.BitSet;
import net.minecraft.client.Minecraft;

/**
 * The bard's note lane on the client: notes come every {@link Bard#BEAT} ticks after the song's
 * start; a click within 2 ticks of a note is perfect, within 4 good, otherwise a miss (as is a note
 * that goes by unplayed). Each judgement is sent to the server, which keeps the song's points.
 */
public final class BardClient {
    private BardClient() {}

    public static final double PERFECT_WINDOW = 2.0, GOOD_WINDOW = 4.0;

    private static long songStart = -1;
    private static final BitSet JUDGED = new BitSet();
    /** Last judgement for the HUD: grade and when (client ticks). */
    static int lastGrade = -1;
    static long lastAt;
    static int streak;

    public static boolean performing() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return false;
        JobData d = JobClient.data();
        return !d.song().isEmpty() && d.songEnd() > mc.level.getGameTime();
    }

    static double now(Minecraft mc) {
        return mc.level.getGameTime() + mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
    }

    static long firstNote(JobData d) {
        return d.songStart() + Bard.LEAD;
    }

    static boolean judged(int idx) {
        return idx >= 0 && JUDGED.get(idx);
    }

    private static void sync(JobData d) {
        if (d.songStart() != songStart) {
            songStart = d.songStart();
            JUDGED.clear();
            streak = 0;
        }
    }

    /** The attack button while playing. */
    public static void click() {
        Minecraft mc = Minecraft.getInstance();
        JobData d = JobClient.data();
        sync(d);
        double now = now(mc);
        long first = firstNote(d);
        if (now < first - GOOD_WINDOW) return; // the lead-in: nothing to play yet
        int idx = (int) Math.round((now - first) / Bard.BEAT);
        double diff = Math.abs(now - (first + (long) idx * Bard.BEAT));
        int grade;
        if (JUDGED.get(idx) || diff > GOOD_WINDOW) grade = Bard.MISS; // between notes, or a note played twice
        else grade = diff <= PERFECT_WINDOW ? Bard.PERFECT : Bard.GOOD;
        if (!JUDGED.get(idx) && diff <= GOOD_WINDOW) JUDGED.set(idx);
        judge(mc, grade);
    }

    private static void judge(Minecraft mc, int grade) {
        lastGrade = grade;
        lastAt = mc.level.getGameTime();
        streak = grade == Bard.MISS ? 0 : streak + 1;
        JobClient.send(JobActionPayload.JUDGE_NOTE, grade, "");
    }

    /** Every client tick: notes that went by unplayed are misses. */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !performing()) return;
        JobData d = JobClient.data();
        sync(d);
        double now = now(mc);
        long first = firstNote(d);
        int last = (int) Math.floor((now - GOOD_WINDOW - 0.5 - first) / Bard.BEAT);
        for (int idx = Math.max(0, last - 2); idx <= last; idx++) {
            long t = first + (long) idx * Bard.BEAT;
            if (t >= d.songEnd() || JUDGED.get(idx)) continue;
            JUDGED.set(idx);
            judge(mc, Bard.MISS);
        }
    }
}
