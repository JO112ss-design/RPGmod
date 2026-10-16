package com.jo112ss.rpgmod.client.job;

import com.jo112ss.rpgmod.item.ModItems;
import com.jo112ss.rpgmod.item.WeaponMaterial;
import com.jo112ss.rpgmod.item.WeaponType;
import com.jo112ss.rpgmod.job.JobType;
import com.jo112ss.rpgmod.job.core.JobDefs;
import com.jo112ss.rpgmod.job.core.JobStat;
import com.jo112ss.rpgmod.job.core.SkillDef;
import com.jo112ss.rpgmod.network.JobActionPayload;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Job selection, shown the first time a player joins (and on K until a job is chosen).
 * Eight cards; the selected one shows its description, weapons, bonuses and signature skills.
 * Choosing needs two clicks because it cannot be undone.
 */
public class JobSelectScreen extends Screen {
    private static final int W = 320, H = 252;
    private static final int CARD_W = 72, CARD_H = 62, GAP = 6, CARDS_Y = 30;

    private int left, top;
    private int selected = -1;
    private boolean confirming;
    private Button confirm;
    private long openedAt;

    public JobSelectScreen() {
        super(Component.translatable("rpgmod.jobselect.title"));
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        openedAt = System.currentTimeMillis();
        confirm = addRenderableWidget(Button.builder(Component.translatable("rpgmod.jobselect.choose"), b -> onConfirm())
                .bounds(left + W - 118, top + H - 24, 110, 18).build());
        confirm.active = selected >= 0;
    }

    /** Dev test hook: select a card as if clicked. */
    public void testSelect(int i) {
        selected = i;
        confirm.active = true;
    }

    private void onConfirm() {
        if (selected < 0) return;
        if (!confirming) {
            confirming = true;
            confirm.setMessage(Component.translatable("rpgmod.jobselect.confirm"));
            return;
        }
        JobClient.send(JobActionPayload.CHOOSE_JOB, 0, JobType.values()[selected].id);
        onClose();
    }

    private int cardX(int i) {
        return left + 7 + (i % 4) * (CARD_W + GAP);
    }

    private int cardY(int i) {
        return top + CARDS_Y + (i / 4) * (CARD_H + GAP);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            for (int i = 0; i < JobType.values().length; i++) {
                if (event.x() >= cardX(i) && event.x() < cardX(i) + CARD_W && event.y() >= cardY(i) && event.y() < cardY(i) + CARD_H) {
                    if (selected != i) {
                        selected = i;
                        confirming = false;
                        confirm.setMessage(Component.translatable("rpgmod.jobselect.choose"));
                    }
                    confirm.active = true;
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        int accent = selected >= 0 ? JobType.values()[selected].color() : 0xC9A45C;
        Ui.panel(g, left, top, W, H, accent);
        g.text(font, title, left + 10, top + 7, Ui.TEXT, true);
        Component sub = Component.translatable("rpgmod.jobselect.once");
        g.text(font, sub, left + W - 10 - font.width(sub), top + 7, Ui.GOLD, false);

        float intro = Math.min(1f, (System.currentTimeMillis() - openedAt) / 400f);
        for (int i = 0; i < JobType.values().length; i++) {
            drawCard(g, i, mouseX, mouseY, intro);
        }
        drawDetail(g);
        super.extractRenderState(g, mouseX, mouseY, a);
    }

    private void drawCard(GuiGraphicsExtractor g, int i, int mouseX, int mouseY, float intro) {
        JobType job = JobType.values()[i];
        int x = cardX(i), y = cardY(i) + Math.round((1 - intro) * (10 + i * 3));
        boolean hover = mouseX >= x && mouseX < x + CARD_W && mouseY >= y && mouseY < y + CARD_H;
        boolean sel = i == selected;
        if (sel || hover) y -= 2;
        int c = job.color();
        g.fill(x, y, x + CARD_W, y + CARD_H, sel ? Ui.argb(c) : hover ? Ui.EDGE_LIGHT : Ui.EDGE);
        g.fillGradient(x + 1, y + 1, x + CARD_W - 1, y + CARD_H - 1, Ui.mix(Ui.argb(c), 0xFF14161C, 0.78f), 0xFF0E1015);
        g.fill(x + 1, y + 1, x + CARD_W - 1, y + 3, Ui.argb(c));
        Ui.disc(g, x + CARD_W / 2, y + 20, 13, Ui.mix(Ui.argb(c), 0xFF000000, 0.35f));
        Ui.circle(g, x + CARD_W / 2, y + 20, 13, Ui.argb(c), 1);
        Ui.glyph(g, font, job.def().glyph(), x + CARD_W / 2 + 1, y + 21, 1.6f, Ui.TEXT);
        Component name = Component.translatable("rpgmod.job." + job.id);
        g.centeredText(font, name, x + CARD_W / 2, y + 38, sel ? Ui.argb(c) : Ui.TEXT);
        Component role = Component.translatable("rpgmod.job." + job.id + ".role");
        g.centeredText(font, role, x + CARD_W / 2, y + 50, Ui.DIM);
    }

    private void drawDetail(GuiGraphicsExtractor g) {
        int x = left + 8, y = top + CARDS_Y + 2 * (CARD_H + GAP) + 2;
        int w = W - 16, h = 56;
        Ui.inset(g, x, y, w, h);
        if (selected < 0) {
            g.centeredText(font, Component.translatable("rpgmod.jobselect.hint"), x + w / 2, y + h / 2 - 4, Ui.DIM);
            return;
        }
        JobType job = JobType.values()[selected];
        JobDefs.Job def = job.def();
        g.text(font, Component.translatable("rpgmod.job." + job.id), x + 6, y + 5, Ui.argb(job.color()), true);
        g.textWithWordWrap(font, Component.translatable("rpgmod.job." + job.id + ".desc"), x + 6, y + 17, 172, Ui.TEXT, false);

        // weapons
        int wx = x + 186;
        g.text(font, Component.translatable("rpgmod.jobselect.weapons"), wx, y + 5, Ui.DIM, false);
        int ix = wx;
        for (WeaponType t : job.weapons) {
            g.item(new ItemStack(ModItems.get(t, WeaponMaterial.IRON)), ix, y + 15);
            ix += 18;
        }
        // bonuses
        // bonuses flow left to right under the weapons and skills, wrapping onto a second row
        int maxX = x + w - 6, bx = wx, row = 0;
        for (Map.Entry<JobStat, Double> e : def.base().entrySet()) {
            double val = e.getValue();
            String num = Math.abs(val - Math.round(val)) < 0.01 ? String.valueOf(Math.round(val)) : String.format("%.1f", val);
            String v = "+" + num + (e.getKey().percent ? "%" : "");
            Component line = Component.translatable("rpgmod.stat." + e.getKey().id).append(" " + v);
            int lw = font.width(line);
            if (bx > wx && bx + lw > maxX) {
                if (++row > 1) break;
                bx = wx;
            }
            g.text(font, line, bx, y + 35 + row * 10, Ui.EXP, false);
            bx += lw + 8;
        }
        // signature skills: first skill and ultimates
        int sx = x + w - 6 - 3 * 18;
        g.text(font, Component.translatable("rpgmod.jobselect.skills"), sx, y + 5, Ui.DIM, false);
        SkillDef first = JobDefs.skill(def.actives().get(0));
        Ui.skillIcon(g, font, sx, y + 15, 16, first, false);
        for (int k = 0; k < def.ultimates().size() && k < 2; k++) {
            Ui.skillIcon(g, font, sx + 18 * (k + 1), y + 15, 16, JobDefs.skill(def.ultimates().get(k)), false);
        }
    }
}
