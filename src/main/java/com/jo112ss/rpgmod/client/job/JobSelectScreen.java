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
 * Job selection: the first job (shown the first time a player joins, and on K until a job is
 * chosen), or a job change with a job change book ({@code change}). Eight cards; the selected one
 * shows its description, weapons, bonuses and signature skills. When changing job, a second tab
 * shows the advanced jobs, open once their base job is mastered, and every card shows its mastery.
 * Choosing needs two clicks.
 */
public class JobSelectScreen extends Screen {
    private static final int W = 320, H = 252;
    private static final int CARD_W = 72, CARD_H = 62, GAP = 6, CARDS_Y = 30;

    private final boolean change;
    private boolean advTab;
    private int left, top;
    private int selected = -1;
    private boolean confirming;
    private Button confirm;
    private long openedAt;

    public JobSelectScreen(boolean change) {
        super(Component.translatable(change ? "rpgmod.jobchange.title" : "rpgmod.jobselect.title"));
        this.change = change;
    }

    /** Job id of card i on the current tab. */
    private String idOf(int i) {
        JobType base = JobType.values()[i];
        if (!advTab) return base.id;
        JobDefs.Job adv = JobDefs.advancedOf(base.id);
        return adv == null ? base.id : adv.id();
    }

    private JobDefs.Job defOf(int i) {
        return JobDefs.job(idOf(i));
    }

    /** Lang key of why card i cannot be picked (current job, not mastered), or null. */
    private String problem(int i) {
        return change ? com.jo112ss.rpgmod.job.JobManager.changeProblem(JobClient.data(), idOf(i)) : null;
    }

    private void updateConfirm() {
        confirm.active = selected >= 0 && problem(selected) == null;
        confirm.setMessage(Component.translatable(confirming ? (change ? "rpgmod.jobchange.confirm" : "rpgmod.jobselect.confirm")
                : (change ? "rpgmod.jobchange.choose" : "rpgmod.jobselect.choose")));
    }

    private int tabX(int t) {
        return left + 150 + t * 60;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        openedAt = System.currentTimeMillis();
        confirm = addRenderableWidget(Button.builder(Component.translatable("rpgmod.jobselect.choose"), b -> onConfirm())
                .bounds(left + W - 148, top + H - 24, 140, 18).build());
        updateConfirm();
    }

    /** Dev test hook: select a card as if clicked (on the advanced tab when {@code adv}). */
    public void testSelect(int i) {
        testSelect(i, false);
    }

    public void testSelect(int i, boolean adv) {
        advTab = adv && change;
        selected = i;
        confirming = false;
        updateConfirm();
    }

    private void onConfirm() {
        if (selected < 0 || problem(selected) != null) return;
        if (!confirming) {
            confirming = true;
            updateConfirm();
            return;
        }
        if (change) JobClient.send(JobActionPayload.CHANGE_JOB, 0, idOf(selected));
        else JobClient.send(JobActionPayload.CHOOSE_JOB, 0, JobType.values()[selected].id);
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
            if (change) {
                for (int t = 0; t < 2; t++) {
                    if (event.x() >= tabX(t) && event.x() < tabX(t) + 56 && event.y() >= top + 5 && event.y() < top + 19) {
                        if (advTab != (t == 1)) {
                            advTab = t == 1;
                            confirming = false;
                            updateConfirm();
                        }
                        return true;
                    }
                }
            }
            for (int i = 0; i < JobType.values().length; i++) {
                if (event.x() >= cardX(i) && event.x() < cardX(i) + CARD_W && event.y() >= cardY(i) && event.y() < cardY(i) + CARD_H) {
                    if (selected != i) {
                        selected = i;
                        confirming = false;
                    }
                    updateConfirm();
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
        int accent = selected >= 0 ? defOf(selected).color() : 0xC9A45C;
        Ui.panel(g, left, top, W, H, accent);
        g.text(font, title, left + 10, top + 7, Ui.TEXT, true);
        if (change) {
            for (int t = 0; t < 2; t++) {
                boolean on = advTab == (t == 1);
                boolean hov = mouseX >= tabX(t) && mouseX < tabX(t) + 56 && mouseY >= top + 5 && mouseY < top + 19;
                g.fill(tabX(t), top + 5, tabX(t) + 56, top + 19, on ? Ui.argb(accent) : hov ? Ui.EDGE_LIGHT : Ui.EDGE);
                g.fill(tabX(t) + 1, top + 6, tabX(t) + 55, top + 19, on ? Ui.mix(Ui.argb(accent), 0xFF101218, 0.55f) : Ui.BG_INNER);
                g.centeredText(font, Component.translatable(t == 0 ? "rpgmod.jobchange.tab_base" : "rpgmod.jobchange.tab_adv"), tabX(t) + 28, top + 8, on ? Ui.TEXT : Ui.DIM);
            }
        }
        Component sub = Component.translatable(change ? "rpgmod.jobchange.cost" : "rpgmod.jobselect.once");
        g.text(font, sub, left + 10, top + H - 19, change ? Ui.DIM : Ui.GOLD, false);

        float intro = Math.min(1f, (System.currentTimeMillis() - openedAt) / 400f);
        for (int i = 0; i < JobType.values().length; i++) {
            drawCard(g, i, mouseX, mouseY, intro);
        }
        drawDetail(g);
        super.extractRenderState(g, mouseX, mouseY, a);
    }

    private void drawCard(GuiGraphicsExtractor g, int i, int mouseX, int mouseY, float intro) {
        JobType base = JobType.values()[i];
        JobDefs.Job job = defOf(i);
        int x = cardX(i), y = cardY(i) + Math.round((1 - intro) * (10 + i * 3));
        boolean hover = mouseX >= x && mouseX < x + CARD_W && mouseY >= y && mouseY < y + CARD_H;
        boolean sel = i == selected;
        if (sel || hover) y -= 2;
        String problem = problem(i);
        boolean locked = "rpgmod.jobchange.need_mastery".equals(problem);
        boolean current = "rpgmod.jobchange.current".equals(problem);
        int c = locked ? 0x50555F : job.color(); // a locked advanced job gives nothing away, not even its colour
        g.fill(x, y, x + CARD_W, y + CARD_H, sel ? Ui.argb(c) : hover ? Ui.EDGE_LIGHT : Ui.EDGE);
        g.fillGradient(x + 1, y + 1, x + CARD_W - 1, y + CARD_H - 1, Ui.mix(Ui.argb(c), 0xFF14161C, 0.78f), 0xFF0E1015);
        g.fill(x + 1, y + 1, x + CARD_W - 1, y + 3, Ui.argb(c));
        Ui.disc(g, x + CARD_W / 2, y + 20, 13, Ui.mix(Ui.argb(c), 0xFF000000, 0.35f));
        Ui.circle(g, x + CARD_W / 2, y + 20, 13, Ui.argb(c), 1);
        Ui.glyph(g, font, locked ? "?" : job.glyph(), x + CARD_W / 2 + 1, y + 21, 1.6f, locked ? Ui.DIM : Ui.TEXT);
        Component name = locked ? Component.translatable("rpgmod.jobchange.hidden") : Component.translatable("rpgmod.job." + job.id());
        g.centeredText(font, font.substrByWidth(name, CARD_W - 4).getString(), x + CARD_W / 2, y + (change ? 36 : 38), sel ? Ui.argb(c) : locked ? Ui.DIM : Ui.TEXT);
        if (!change) {
            Component role = Component.translatable("rpgmod.job." + job.id() + ".role");
            g.centeredText(font, role, x + CARD_W / 2, y + 50, Ui.DIM);
            return;
        }
        // job change: current job / mastery of the base job (the key to its advanced job)
        if (current) {
            g.centeredText(font, Component.translatable("rpgmod.jobchange.current_tag"), x + CARD_W / 2, y + 50, Ui.GOLD);
        } else {
            int pct = JobClient.data().masteryPercent(base.id);
            int bx = x + 6, bw = CARD_W - 12, by = y + 55;
            g.fill(bx, by, bx + bw, by + 3, 0xFF0B0D12);
            g.fill(bx, by, bx + Math.round(bw * pct / 100f), by + 3, pct >= 100 ? Ui.GOLD : Ui.EXP);
            Component m = Component.translatable("rpgmod.jobchange.mastery_pct", pct);
            g.pose().pushMatrix();
            g.pose().translate(x + CARD_W / 2f, y + 46f);
            g.pose().scale(0.8f, 0.8f);
            g.text(font, m, -font.width(m) / 2, 0, locked ? Ui.BAD : pct >= 100 ? Ui.GOLD : Ui.DIM, false);
            g.pose().popMatrix();
        }
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
        JobDefs.Job def = defOf(selected);
        String problem = problem(selected);
        boolean hidden = "rpgmod.jobchange.need_mastery".equals(problem);
        g.text(font, hidden ? Component.translatable("rpgmod.jobchange.hidden") : Component.translatable("rpgmod.job." + def.id()), x + 6, y + 5,
                hidden ? Ui.DIM : Ui.argb(def.color()), true);
        Component desc = "rpgmod.jobchange.need_mastery".equals(problem)
                ? Component.translatable("rpgmod.jobchange.locked_desc", Component.translatable("rpgmod.job." + job.id), JobClient.data().masteryPercent(job.id))
                : Component.translatable("rpgmod.job." + def.id() + ".desc");
        g.textWithWordWrap(font, desc, x + 6, y + 17, 172, problem != null && !"rpgmod.jobchange.current".equals(problem) ? Ui.BAD : Ui.TEXT, false);

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
        for (Map.Entry<JobStat, Double> e : hidden ? Map.<JobStat, Double>of().entrySet() : def.base().entrySet()) {
            double val = e.getValue();
            String num = Math.abs(val - Math.round(val)) < 0.01 ? String.valueOf(Math.round(val)) : String.format("%.1f", val);
            String v = "+" + num + (e.getKey().percent ? "%" : "");
            Component line = Component.translatable("rpgmod.stat." + e.getKey().id).append(" " + v);
            int lw = font.width(line);
            if (bx > wx && bx + lw > maxX) {
                if (++row > 2) break;
                bx = wx;
            }
            g.text(font, line, bx, y + 33 + row * 8, Ui.EXP, false);
            bx += lw + 8;
        }
        // signature skills: first skill and ultimates
        int sx = x + w - 6 - 3 * 18;
        g.text(font, Component.translatable("rpgmod.jobselect.skills"), sx, y + 5, Ui.DIM, false);
        if (hidden) { // still a secret: three empty frames
            for (int k = 0; k < 3; k++) {
                Ui.inset(g, sx + 18 * k, y + 15, 16, 16);
                g.centeredText(font, "?", sx + 18 * k + 8, y + 19, Ui.DIM);
            }
            return;
        }
        if (!def.actives().isEmpty()) Ui.skillIcon(g, font, sx, y + 15, 16, JobDefs.skill(def.actives().get(0)), false);
        for (int k = 0; k < def.ultimates().size() && k < 2; k++) {
            Ui.skillIcon(g, font, sx + 18 * (k + 1), y + 15, 16, JobDefs.skill(def.ultimates().get(k)), false);
        }
    }
}
