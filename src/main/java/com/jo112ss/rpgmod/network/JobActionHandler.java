package com.jo112ss.rpgmod.network;

import com.jo112ss.rpgmod.job.JobManager;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;

/** Routes job requests to JobManager with a small per-player rate limit against spam. */
public final class JobActionHandler {
    private JobActionHandler() {}

    private static final long MIN_GAP_MILLIS = 50;
    private static final Map<UUID, Long> LAST = new ConcurrentHashMap<>();

    public static void handle(ServerPlayer player, JobActionPayload p) {
        // held-button events must never be dropped (their own timers limit them)
        switch (p.action()) {
            case JobActionPayload.LEFT_CAST -> {
                if (p.arg() == 1) com.jo112ss.rpgmod.job.SpellCasting.leftPress(player);
                else com.jo112ss.rpgmod.job.SpellCasting.leftRelease(player);
                return;
            }
            case JobActionPayload.OFFHAND_ATTACK -> {
                com.jo112ss.rpgmod.job.DualWield.strike(player, p.arg());
                return;
            }
            default -> { }
        }
        long now = System.currentTimeMillis();
        Long last = LAST.get(player.getUUID());
        if (last != null && now - last < MIN_GAP_MILLIS) return;
        LAST.put(player.getUUID(), now);
        switch (p.action()) {
            case JobActionPayload.CHOOSE_JOB -> JobManager.chooseJob(player, p.text());
            case JobActionPayload.USE_SKILL -> JobManager.useSkill(player, p.arg());
            case JobActionPayload.UNLOCK_NODE -> JobManager.unlockNode(player, p.arg(), false);
            case JobActionPayload.UNLOCK_ADV_NODE -> JobManager.unlockNode(player, p.arg(), true);
            case JobActionPayload.EQUIP_SKILL -> JobManager.equipSkill(player, p.arg(), p.text());
            case JobActionPayload.SET_SPELL -> JobManager.setSpell(player, p.arg(), p.text());
            case JobActionPayload.SELECT_SPELL -> JobManager.selectSpell(player, p.arg());
            case JobActionPayload.SELECT_LEFT_SPELL -> JobManager.selectLeftSpell(player, p.arg());
            case JobActionPayload.RESET_PANEL -> JobManager.resetPanel(player, p.arg() == 1);
            default -> { }
        }
    }

    public static void forget(UUID id) {
        LAST.remove(id);
        com.jo112ss.rpgmod.job.SpellCasting.forget(id);
        com.jo112ss.rpgmod.job.DualWield.forget(id);
    }
}
