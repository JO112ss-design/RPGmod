package com.jo112ss.rpgmod.client;

import com.jo112ss.rpgmod.network.ForgeSyncPayload;
import com.jo112ss.rpgmod.smithing.core.TimingBar;

/** Latest forging snapshot from the server, plus local timing for the bar animation. */
public final class ClientForgeView {
    private ClientForgeView() {}

    private static ForgeSyncPayload current = ForgeSyncPayload.select();
    private static long receivedAt = System.currentTimeMillis();
    /** Token the player already stopped; input is ignored until the server answers with a new one. */
    private static int stoppedToken = Integer.MIN_VALUE;
    private static double frozenPos = -1;
    private static ForgeSyncPayload previous;
    private static long judgementShownAt;

    public static void receive(ForgeSyncPayload payload) {
        if (payload.stage() == ForgeSyncPayload.STAGE_SELECT) resetInput();
        previous = current;
        if (payload.strikes() != (previous == null ? 0 : previous.strikes()) && payload.lastJudgement() >= 0) {
            judgementShownAt = System.currentTimeMillis();
        }
        current = payload;
        receivedAt = System.currentTimeMillis();
        if (payload.barToken() != stoppedToken) {
            frozenPos = -1;
        }
    }

    /** Forgets the stopped bar (new screen or new session). */
    public static void resetInput() {
        stoppedToken = Integer.MIN_VALUE;
        frozenPos = -1;
    }

    public static ForgeSyncPayload get() {
        return current;
    }

    public static long elapsedMillis() {
        return System.currentTimeMillis() - receivedAt;
    }

    /** Current marker position in [0,1]. */
    public static double barPosition() {
        if (!current.barRunning()) return 0.5;
        if (frozenPos >= 0) return frozenPos;
        return TimingBar.position(current.barSpeed(), elapsedMillis());
    }

    public static boolean canStop() {
        return current.barRunning() && current.barToken() != stoppedToken;
    }

    /** Freezes the marker where the player stopped it and returns the elapsed time to report. */
    public static long markStopped() {
        long elapsed = elapsedMillis();
        frozenPos = TimingBar.position(current.barSpeed(), elapsed);
        stoppedToken = current.barToken();
        return elapsed;
    }

    public static long judgementAge() {
        return System.currentTimeMillis() - judgementShownAt;
    }
}
