package com.jo112ss.rpgmod.client.job;

import com.jo112ss.rpgmod.item.ModItems;
import com.jo112ss.rpgmod.item.WeaponType;
import com.jo112ss.rpgmod.job.Bard;
import com.jo112ss.rpgmod.job.JobData;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Under the crosshair: the pirate's rounds (and the reload), the monk's ki. Above the hotbar: the
 * bard's note lane while a song is playing.
 */
public final class NewJobHud {
    private NewJobHud() {}

    public static void render(GuiGraphicsExtractor g, DeltaTracker delta, JobData d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        int sw = g.guiWidth(), sh = g.guiHeight();
        String job = d.jobId();
        if (d.gunJob() && ModItems.typeOf(mc.player.getMainHandItem().getItem()) == WeaponType.GUNBLADE) ammo(g, mc, d, sw, sh);
        if (d.kiJob()) ki(g, d, sw, sh);
        if ("spellblade".equals(job)) { // the sword's element
            int el = Math.floorMod(d.coatMode(), 3);
            Component t = Component.translatable("rpgmod.spellblade.element." + el);
            int col = 0xFF000000 | com.jo112ss.rpgmod.job.MoreJobSkills.ELEMENT_COLORS[el];
            g.centeredText(mc.font, t, sw / 2, sh / 2 + 10, col);
            if (JobClient.swordCharge >= 0) swordCharge(g, el, sw, sh);
        }
        if (BardClient.performing()) lane(g, mc, d, sw, sh);
    }

    /** The sword magic charging: three segments, filling up to each stage, the reached ones lit. */
    private static void swordCharge(GuiGraphicsExtractor g, int el, int sw, int sh) {
        int held = JobClient.swordCharge;
        int[] at = {com.jo112ss.rpgmod.job.SpellbladeMagic.STAGE1, com.jo112ss.rpgmod.job.SpellbladeMagic.STAGE2, com.jo112ss.rpgmod.job.SpellbladeMagic.STAGE3};
        int seg = 18, gap = 3, w = seg * 3 + gap * 2;
        int x0 = sw / 2 - w / 2, y = sh / 2 + 22;
        int col = 0xFF000000 | com.jo112ss.rpgmod.job.MoreJobSkills.ELEMENT_COLORS[el];
        for (int i = 0; i < 3; i++) {
            int x = x0 + i * (seg + gap);
            g.fill(x - 1, y - 1, x + seg + 1, y + 4, 0xA0000000);
            float f;
            int from = i == 0 ? 0 : at[i - 1];
            f = Math.max(0, Math.min(1, (held - from) / (float) (at[i] - from)));
            boolean reached = held >= at[i];
            g.fill(x, y, x + Math.round(seg * f), y + 3, reached ? col : 0xFF808080);
        }
    }

    private static void ammo(GuiGraphicsExtractor g, Minecraft mc, JobData d, int sw, int sh) {
        int cx = sw / 2, y = sh / 2 + 10;
        long now = mc.level.getGameTime();
        int w = JobData.MAG * 5 - 1;
        int x0 = cx - w / 2;
        if (d.reloadEnd() > now) {
            float f = 1 - (d.reloadEnd() - now) / (float) com.jo112ss.rpgmod.job.Pirate.RELOAD_TICKS;
            g.fill(x0 - 1, y - 1, x0 + w + 1, y + 4, 0xA0000000);
            g.fill(x0, y, x0 + Math.round(w * Math.max(0, Math.min(1, f))), y + 3, 0xFFFFC060);
            return;
        }
        for (int i = 0; i < JobData.MAG; i++) {
            int x = x0 + i * 5;
            boolean full = i < d.ammo();
            g.fill(x, y, x + 4, y + 6, 0xA0000000);
            if (full) {
                g.fill(x + 1, y + 2, x + 3, y + 5, 0xFFD8A040); // the case
                g.fill(x + 1, y + 1, x + 3, y + 2, 0xFFE8E0D0); // the ball
            }
        }
    }

    private static void ki(GuiGraphicsExtractor g, JobData d, int sw, int sh) {
        int cx = sw / 2, y = sh / 2 + 10;
        int max = d.maxKi();
        int w = max * 6 - 2;
        int x0 = cx - w / 2;
        for (int i = 0; i < max; i++) {
            int x = x0 + i * 6;
            g.fill(x, y, x + 4, y + 4, 0x90000000);
            if (i < d.ki()) {
                int col = d.ki() >= max ? 0xFFFFF0A0 : 0xFFFFB040;
                g.fill(x + 1, y, x + 3, y + 4, col);
                g.fill(x, y + 1, x + 4, y + 3, col);
            }
        }
    }

    private static final int LANE_W = 180, LANE_H = 16, SPEED = 4;

    private static void lane(GuiGraphicsExtractor g, Minecraft mc, JobData d, int sw, int sh) {
        Font font = mc.font;
        int x0 = sw / 2 - LANE_W / 2, y0 = sh - 80;
        int hitX = x0 + 16;
        int color = 0xFF000000 | Bard.songColor(d.song());
        double now = BardClient.now(mc);
        long first = BardClient.firstNote(d);
        g.fill(x0 - 1, y0 - 1, x0 + LANE_W + 1, y0 + LANE_H + 1, 0x70FFFFFF & color | 0x40000000);
        g.fill(x0, y0, x0 + LANE_W, y0 + LANE_H, 0xB0101418);
        // the timing windows around the hit line
        g.fill(hitX - (int) (BardClient.GOOD_WINDOW * SPEED), y0, hitX + (int) (BardClient.GOOD_WINDOW * SPEED), y0 + LANE_H, 0x30FFFFFF);
        g.fill(hitX - (int) (BardClient.PERFECT_WINDOW * SPEED), y0, hitX + (int) (BardClient.PERFECT_WINDOW * SPEED), y0 + LANE_H, 0x40FFE070);
        g.fill(hitX, y0 - 2, hitX + 1, y0 + LANE_H + 2, 0xFFFFFFFF);
        // the notes coming in from the right
        int from = (int) Math.floor((now - first) / Bard.BEAT) - 1;
        for (int idx = Math.max(0, from); idx < from + LANE_W / (Bard.BEAT * SPEED) + 3; idx++) {
            long t = first + (long) idx * Bard.BEAT;
            if (t >= d.songEnd()) break;
            if (BardClient.judged(idx)) continue;
            int x = hitX + (int) Math.round((t - now) * SPEED);
            if (x < x0 - 2 || x > x0 + LANE_W - 6) continue;
            g.fill(x - 4, y0 + 3, x + 5, y0 + LANE_H - 3, color);
            g.fill(x - 3, y0 + 4, x + 4, y0 + LANE_H - 4, 0xFFFFFFFF);
            g.fill(x - 2, y0 + 5, x + 3, y0 + LANE_H - 5, color);
        }
        // song name, points, time left
        Component name = Component.translatable("rpgmod.skill." + d.song());
        g.text(font, name, x0, y0 - 11, color, true);
        long left = Math.max(0, d.songEnd() - mc.level.getGameTime());
        String info = d.songPoints() + "/" + JobData.MAX_SONG_POINTS + "  " + (left + 19) / 20 + "s";
        g.text(font, info, x0 + LANE_W - font.width(info), y0 - 11, 0xFFE0E0E0, true);
        // the bar of how well it goes (strength of the song)
        float f = d.songPoints() / (float) JobData.MAX_SONG_POINTS;
        g.fill(x0, y0 + LANE_H + 2, x0 + LANE_W, y0 + LANE_H + 4, 0xA0000000);
        g.fill(x0, y0 + LANE_H + 2, x0 + Math.round(LANE_W * f), y0 + LANE_H + 4, d.songPoints() >= Bard.ENCORE_POINTS ? 0xFFFFE070 : color);
        // the last judgement
        long age = mc.level.getGameTime() - BardClient.lastAt;
        if (BardClient.lastGrade >= 0 && age < 12) {
            String key = BardClient.lastGrade == Bard.PERFECT ? "rpgmod.bard.perfect" : BardClient.lastGrade == Bard.GOOD ? "rpgmod.bard.good" : "rpgmod.bard.miss";
            int col = BardClient.lastGrade == Bard.PERFECT ? 0xFFFFE070 : BardClient.lastGrade == Bard.GOOD ? 0xFF80FF90 : 0xFF909090;
            Component c = Component.translatable(key);
            if (BardClient.streak >= 3) c = c.copy().append(" x" + BardClient.streak);
            g.pose().pushMatrix();
            g.pose().translate(sw / 2F - font.width(c) / 2F, y0 - 24 - Math.min(3, age));
            g.text(font, c, 0, 0, col, true);
            g.pose().popMatrix();
        }
    }
}
