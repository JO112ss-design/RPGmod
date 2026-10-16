package com.jo112ss.rpgmod.smithing.core;

import java.util.Arrays;

/**
 * Authoritative state of one forging session. Pure Java: time is passed in, so the same code runs
 * on the server, in tests and in the balance simulation. Every mutating call validates the phase and
 * the bar token, so duplicated or out-of-order packets do nothing.
 */
public final class ForgeState {
    public enum Phase { HEATING, WORKING, FINISHED }

    public enum EndReason { NONE, PLAYER_FINISHED, TOO_COLD, ABORTED }

    private final SmithingRules rules;
    private final double[] targets;
    private final double[] values;

    private Phase phase = Phase.HEATING;
    private EndReason endReason = EndReason.NONE;
    private double temperature;
    private int selectedPart = -1;
    private boolean barRunning;
    private long barStartMillis;
    private int barToken;
    private int strikes;
    private Judgement lastJudgement;
    private int lastPart = -1;
    private double lastDamage;

    public ForgeState(SmithingRules rules, double[] targets, long nowMillis) {
        this.rules = rules;
        this.targets = targets.clone();
        this.values = new double[targets.length];
        this.temperature = rules.initialTempMax;
        startBar(nowMillis);
    }

    // ------------------------------------------------------------------ queries

    public SmithingRules rules() { return rules; }
    public Phase phase() { return phase; }
    public EndReason endReason() { return endReason; }
    public double temperature() { return temperature; }
    public int parts() { return targets.length; }
    public double target(int i) { return targets[i]; }
    public double value(int i) { return values[i]; }
    public double[] targets() { return targets.clone(); }
    public double[] values() { return values.clone(); }
    public int selectedPart() { return selectedPart; }
    public boolean barRunning() { return barRunning; }
    public int barToken() { return barToken; }
    public long barStartMillis() { return barStartMillis; }
    public int strikes() { return strikes; }
    public Judgement lastJudgement() { return lastJudgement; }
    public int lastPart() { return lastPart; }
    public double lastDamage() { return lastDamage; }

    /** Speed of the bar that is currently running (heating or strike). */
    public double currentBarSpeed() {
        return phase == Phase.HEATING ? rules.heatBarSpeed : rules.strikeBarSpeed(temperature);
    }

    /** Damage a Perfect strike would deal to the selected part right now. */
    public double estimatedPower() {
        return rules.basePower(temperature) * rules.perfectMultiplier;
    }

    public double errorRatio() {
        return errorRatio(rules, targets, values);
    }

    public Quality quality() {
        return Quality.fromError(rules, errorRatio());
    }

    public static double errorRatio(SmithingRules rules, double[] targets, double[] values) {
        double err = 0, total = 0;
        for (int i = 0; i < targets.length; i++) {
            double diff = values[i] - targets[i];
            err += diff < 0 ? -diff * rules.underPenalty : diff * rules.overPenalty;
            total += targets[i];
        }
        return total <= 0 ? 0 : err / total;
    }

    // ------------------------------------------------------------------ actions

    /**
     * Resolves the elapsed time used for a bar stop. The client reports how long its bar ran; it is
     * accepted only if it is plausible against the server's own clock, otherwise the server's
     * measurement is used. Returns -1 if the stop is too fast to be a human reaction.
     */
    public long resolveElapsed(long clientElapsed, long nowMillis) {
        long serverElapsed = nowMillis - barStartMillis;
        if (serverElapsed < rules.minStopMillis) return -1;
        boolean plausible = clientElapsed >= rules.minStopMillis
                && clientElapsed <= serverElapsed + 50
                && serverElapsed - clientElapsed <= rules.timingToleranceMillis;
        return plausible ? clientElapsed : serverElapsed;
    }

    /** STEP 1: stop the heating bar. */
    public boolean stopHeating(int token, long clientElapsed, long nowMillis) {
        if (phase != Phase.HEATING || !barRunning || token != barToken) return false;
        long elapsed = resolveElapsed(clientElapsed, nowMillis);
        if (elapsed < 0) return false;
        double pos = TimingBar.position(rules.heatBarSpeed, elapsed);
        temperature = rules.clampTemp(rules.initialTempFromBar(pos));
        phase = Phase.WORKING;
        barRunning = false;
        barToken++;
        return true;
    }

    /** STEP 2: choose a part. Starts (or restarts) the strike bar. */
    public boolean selectPart(int part, long nowMillis) {
        if (phase != Phase.WORKING || part < 0 || part >= targets.length) return false;
        selectedPart = part;
        startBar(nowMillis);
        return true;
    }

    /** STEP 3: stop the strike bar and hit the selected part. */
    public boolean strike(int token, int part, long clientElapsed, long nowMillis) {
        if (phase != Phase.WORKING || !barRunning || token != barToken || part != selectedPart) return false;
        long elapsed = resolveElapsed(clientElapsed, nowMillis);
        if (elapsed < 0) return false;
        double pos = TimingBar.position(rules.strikeBarSpeed(temperature), elapsed);
        Judgement j = TimingBar.judge(rules, pos);
        applyStrike(part, j);
        if (phase == Phase.WORKING) startBar(nowMillis);
        return true;
    }

    /** Applies a strike with a known judgement (also used by the simulation and target planner). */
    public void applyStrike(int part, Judgement j) {
        double dmg = rules.basePower(temperature) * rules.multiplier(j);
        values[part] += dmg;
        if (part - 1 >= 0) values[part - 1] += dmg * rules.adjacentRatio;
        if (part + 1 < values.length) values[part + 1] += dmg * rules.adjacentRatio;
        strikes++;
        lastJudgement = j;
        lastPart = part;
        lastDamage = dmg;
        temperature = rules.clampTemp(temperature - rules.tempLossPerStrike);
        checkCold();
    }

    /** Cooling: lowers temperature (weaker, faster bar). Ends forging at the floor. */
    public boolean cool(long nowMillis) {
        if (phase != Phase.WORKING) return false;
        temperature = rules.clampTemp(temperature - rules.coolAmount);
        checkCold();
        if (phase == Phase.WORKING && selectedPart >= 0) startBar(nowMillis);
        return true;
    }

    /** The player chooses to finish. */
    public boolean finish() {
        if (phase != Phase.WORKING) return false;
        end(EndReason.PLAYER_FINISHED);
        return true;
    }

    /** Ends the session from outside (menu closed, logout). Returns false if it had already ended. */
    public boolean forceEnd() {
        if (phase == Phase.FINISHED) return false;
        end(phase == Phase.HEATING ? EndReason.ABORTED : EndReason.PLAYER_FINISHED);
        return true;
    }

    /** For tools: start working at a given temperature without the heating bar. */
    public void setWorkingTemperature(double temp) {
        temperature = rules.clampTemp(temp);
        phase = Phase.WORKING;
        barRunning = false;
        checkCold();
    }

    private void checkCold() {
        if (temperature <= rules.endTemp) end(EndReason.TOO_COLD);
    }

    private void end(EndReason reason) {
        phase = Phase.FINISHED;
        endReason = reason;
        barRunning = false;
        barToken++;
    }

    private void startBar(long nowMillis) {
        barRunning = true;
        barStartMillis = nowMillis;
        barToken++;
    }

    @Override
    public String toString() {
        return "ForgeState{" + phase + ", T=" + temperature + ", v=" + Arrays.toString(values) + ", t=" + Arrays.toString(targets) + "}";
    }
}
