package com.jo112ss.rpgmod.client.job;

import com.jo112ss.rpgmod.job.JobData;
import com.jo112ss.rpgmod.job.JobStats;
import com.jo112ss.rpgmod.job.SpellLoadout;
import com.jo112ss.rpgmod.job.core.JobDefs;
import com.jo112ss.rpgmod.job.core.JobStat;
import com.jo112ss.rpgmod.job.core.SkillDef;
import com.jo112ss.rpgmod.registry.ModRegistries;
import java.util.Map;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * In-game overlay, bottom right: level + experience, mana bar, the three skill slots with key
 * labels and cooldown shading, and (for wizards holding a staff) the active spell.
 */
public final class JobHud {
    private JobHud() {}

    private static final int SLOT = 20;

    public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.player.isSpectator()) return;
        JobData d = JobClient.data();
        if (!d.hasJob()) return;
        Font font = mc.font;
        int sw = g.guiWidth(), sh = g.guiHeight();
        int[] order = d.isAdvanced() ? new int[] {0, 1, 3, 2} : new int[] {0, 1, 2};
        int x0 = sw - order.length * (SLOT + 4) - 6, y0 = sh - SLOT - 14;
        long now = mc.level.getGameTime();
        Map<JobStat, Double> st = JobStats.compute(d);
        int color = JobDefs.job(d.displayJobId()).color();

        // background plate
        g.fill(x0 - 4, y0 - 26, sw - 2, sh - 2, 0x70000000);
        g.fill(x0 - 4, y0 - 26, sw - 2, y0 - 25, Ui.alpha(color, 0xC0));

        // level + exp
        Component lv = Component.translatable("rpgmod.job." + d.displayJobId()).append(" Lv" + d.displayLevel());
        g.text(font, lv, x0, y0 - 23, Ui.argb(color), true);
        float expFrac = d.expToNext() > 0 ? (float) d.exp() / d.expToNext() : 1f;
        int bw = sw - 6 - x0;
        g.fill(x0, y0 - 13, x0 + bw, y0 - 11, 0xFF20242C);
        g.fill(x0, y0 - 13, x0 + Math.round(bw * expFrac), y0 - 11, Ui.EXP);
        // mana
        double max = com.jo112ss.rpgmod.job.JobManager.maxMana(mc.player);
        float mf = (float) (d.mana() / Math.max(1, max));
        g.fill(x0, y0 - 9, x0 + bw, y0 - 4, 0xFF20242C);
        g.fillGradient(x0, y0 - 9, x0 + Math.round(bw * mf), y0 - 4, 0xFF8AB4FF, Ui.MANA);
        if (d.resting() && mf < 1) {
            // out of combat: faster regeneration, shown as a sweep of light along the bar and a small label
            int fill = Math.round(bw * mf);
            int sweep = (int) ((mc.level.getGameTime() * 2 + delta.getGameTimeDeltaPartialTick(true) * 2) % Math.max(1, fill + 12)) - 6;
            g.fill(x0 + Math.max(0, sweep), y0 - 9, x0 + Math.min(fill, sweep + 6), y0 - 4, 0x60FFFFFF);
            g.pose().pushMatrix();
            g.pose().translate(sw - 4, y0 - 33);
            g.pose().scale(0.6F, 0.6F);
            Component rest = Component.translatable("rpgmod.hud.resting", (int) Math.round(com.jo112ss.rpgmod.job.JobConfig.REST_MULT.get()));
            g.text(font, rest, -font.width(rest), 0, 0xFF8AB4FF, true);
            g.pose().popMatrix();
        }

        // skill slots
        double cdr = st.get(JobStat.COOLDOWN_REDUCTION) / 100.0;
        for (int i = 0; i < order.length; i++) {
            int s = order[i];
            int x = x0 + i * (SLOT + 4), y = y0;
            SkillDef def = JobDefs.skill(d.slot(s));
            Ui.skillIcon(g, font, x, y, SLOT, def, false);
            if (def != null) {
                long remain = d.cooldownEnd(def.id()) - now;
                if (remain > 0) {
                    double total = Math.max(1, def.cooldown() * 20 * (1 - cdr));
                    int h = (int) Math.round(SLOT * Math.min(1, remain / total));
                    g.fill(x + 1, y + SLOT - 1 - h, x + SLOT - 1, y + SLOT - 1, 0xB0000000);
                    String secs = remain >= 200 ? String.valueOf(remain / 20) : String.format("%.1f", remain / 20.0);
                    g.centeredText(font, Component.literal(secs), x + SLOT / 2, y + 6, 0xFFFFFFFF);
                } else if (d.mana() < def.mana()) {
                    g.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, 0x803050C0);
                }
            }
            Component key = JobKeys.SKILLS[s].getTranslatedKeyMessage();
            String k = font.substrByWidth(key, SLOT).getString();
            g.text(font, k, x + (SLOT - font.width(k)) / 2, y + SLOT + 2, Ui.DIM, true);
        }

        // spell charge gauge under the crosshair
        if (mc.player.isUsingItem() && mc.player.getUseItem().getItem() instanceof com.jo112ss.rpgmod.item.RpgStaffItem) {
            SkillDef sp = com.jo112ss.rpgmod.job.JobManager.selectedSpell(mc.player, mc.player.getUseItem());
            if (sp != null) {
                int need = Math.max(1, com.jo112ss.rpgmod.job.JobManager.castTicks(mc.player, sp));
                float f = Math.min(1f, mc.player.getTicksUsingItem() / (float) need);
                int cw = 60, cx = sw / 2 - cw / 2, cy = sh / 2 + 14;
                g.fill(cx - 1, cy - 1, cx + cw + 1, cy + 5, 0xC0000000);
                int col = f >= 1 ? 0xFFFFE07A : 0xFFB09CFF;
                g.fill(cx, cy, cx + Math.round(cw * f), cy + 4, col);
                Component t = f >= 1 ? Component.translatable("rpgmod.hud.charged") : Ui.skillName(sp.id());
                g.centeredText(font, t, sw / 2, cy + 8, col);
            }
        }

        // wizard: active spell on the held staff
        if (d.jobDef().usesSpells() && JobClient.holdingStaff()) {
            SpellLoadout l = mc.player.getMainHandItem().getOrDefault(ModRegistries.SPELL_LOADOUT.get(), SpellLoadout.EMPTY);
            SkillDef spell = JobDefs.skill(l.selectedSpell());
            int x = x0 - SLOT - 12, y = y0;
            g.fill(x - 2, y - 2, x + SLOT + 2, y + SLOT + 2, 0xFFB09CFF);
            Ui.skillIcon(g, font, x, y, SLOT, spell, false);
            if (spell != null) {
                long remain = d.cooldownEnd(spell.id()) - now;
                if (remain > 0) {
                    double total = Math.max(1, spell.cooldown() * 20 * (1 - cdr));
                    int h = (int) Math.round(SLOT * Math.min(1, remain / total));
                    g.fill(x + 1, y + SLOT - 1 - h, x + SLOT - 1, y + SLOT - 1, 0xB0000000);
                }
            }
            String hint = font.substrByWidth(JobKeys.SPELL_WHEEL.getTranslatedKeyMessage(), SLOT).getString();
            g.text(font, hint, x + (SLOT - font.width(hint)) / 2, y + SLOT + 2, 0xFFB09CFF, true);
        }
    }
}
