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
    private static final int W = 320;
    private int H = 252;
    private int cardH = 62;

    private boolean compact() {
        return cardH < 60;
    }

    /** A y offset inside a card: normal, or on the compact cards. */
    private int dy(int normal, int small) {
        return compact() ? small : normal;
    }
    private static final int CARD_W = 48, GAP = 4, CARDS_Y = 30, PER_ROW = 6;

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

    /** Number of cards on the current tab: the base jobs, or their advanced jobs followed by the hybrid jobs. */
    private int count() {
        return advTab ? JobType.BASE.size() + JobType.HYBRIDS.size() : JobType.BASE.size();
    }

    /** The base job behind card i (null for a hybrid job). */
    private JobType baseOfCard(int i) {
        return i < JobType.BASE.size() ? JobType.BASE.get(i) : null;
    }

    /** Job id of card i on the current tab. */
    private String idOf(int i) {
        if (i >= JobType.BASE.size()) return JobType.HYBRIDS.get(i - JobType.BASE.size()).id;
        JobType base = JobType.BASE.get(i);
        if (!advTab) return base.id;
        JobDefs.Job adv = JobDefs.advancedOf(base.id);
        return adv == null ? base.id : adv.id();
    }

    private int rows() {
        return (count() + PER_ROW - 1) / PER_ROW;
    }

    /** Window height for the current tab (a third row of cards for the hybrid jobs). */
    private void layout() {
        cardH = rows() > 2 ? 46 : 62; // three rows: compact cards so the window still fits
        H = CARDS_Y + rows() * (cardH + GAP) + 90;
        left = (width - W) / 2;
        top = (height - H) / 2;
        if (confirm != null) confirm.setPosition(left + W - 148, top + H - 24);
    }

    private JobDefs.Job defOf(int i) {
        return JobDefs.job(idOf(i));
    }

    /** Lang key of why card i cannot be picked (current job, not mastered, no advanced job yet), or null. */
    private String problem(int i) {
        if (!change) return null;
        JobType base = baseOfCard(i);
        if (advTab && base != null && JobDefs.advancedOf(base.id) == null) return "rpgmod.jobchange.no_advanced";
        return com.jo112ss.rpgmod.job.JobManager.changeProblem(JobClient.data(), idOf(i));
    }

    /** Text centred at cx, shrunk to fit maxWidth (and at most {@code scale}). */
    private void fitText(GuiGraphicsExtractor g, Component text, int cx, int y, int maxWidth, float scale, int color) {
        float s = Math.min(scale, maxWidth / (float) Math.max(1, font.width(text)));
        g.pose().pushMatrix();
        g.pose().translate(cx, y);
        g.pose().scale(s, s);
        g.text(font, text, -font.width(text) / 2, 0, color, false);
        g.pose().popMatrix();
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
        layout();
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
        layout();
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
        else JobClient.send(JobActionPayload.CHOOSE_JOB, 0, idOf(selected));
        onClose();
    }

    private int cardX(int i) {
        return left + 7 + (i % PER_ROW) * (CARD_W + GAP);
    }

    private int cardY(int i) {
        return top + CARDS_Y + (i / PER_ROW) * (cardH + GAP);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            if (change) {
                for (int t = 0; t < 2; t++) {
                    if (event.x() >= tabX(t) && event.x() < tabX(t) + 56 && event.y() >= top + 5 && event.y() < top + 19) {
                        if (advTab != (t == 1)) {
                            advTab = t == 1;
                            if (selected >= count()) selected = -1;
                            layout();
                            confirming = false;
                            updateConfirm();
                        }
                        return true;
                    }
                }
            }
            for (int i = 0; i < count(); i++) {
                if (event.x() >= cardX(i) && event.x() < cardX(i) + CARD_W && event.y() >= cardY(i) && event.y() < cardY(i) + cardH) {
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
        for (int i = 0; i < count(); i++) {
            drawCard(g, i, mouseX, mouseY, intro);
        }
        drawDetail(g);
        super.extractRenderState(g, mouseX, mouseY, a);
    }

    private void drawCard(GuiGraphicsExtractor g, int i, int mouseX, int mouseY, float intro) {
        JobType base = baseOfCard(i);
        JobDefs.Job job = defOf(i);
        int x = cardX(i), y = cardY(i) + Math.round((1 - intro) * (10 + i * 3));
        boolean hover = mouseX >= x && mouseX < x + CARD_W && mouseY >= y && mouseY < y + cardH;
        boolean sel = i == selected;
        if (sel || hover) y -= 2;
        String problem = problem(i);
        boolean soon = "rpgmod.jobchange.no_advanced".equals(problem);
        boolean locked = "rpgmod.jobchange.need_mastery".equals(problem) || soon;
        boolean current = "rpgmod.jobchange.current".equals(problem);
        int c = locked ? 0x50555F : job.color(); // a locked advanced job gives nothing away, not even its colour
        int cx = x + CARD_W / 2;
        g.fill(x, y, x + CARD_W, y + cardH, sel ? Ui.argb(c) : hover ? Ui.EDGE_LIGHT : Ui.EDGE);
        g.fillGradient(x + 1, y + 1, x + CARD_W - 1, y + cardH - 1, Ui.mix(Ui.argb(c), 0xFF14161C, 0.78f), 0xFF0E1015);
        g.fill(x + 1, y + 1, x + CARD_W - 1, y + 3, Ui.argb(c));
        Ui.disc(g, cx, y + dy(18, 13), compact() ? 9 : 11, Ui.mix(Ui.argb(c), 0xFF000000, 0.35f));
        Ui.circle(g, cx, y + dy(18, 13), compact() ? 9 : 11, Ui.argb(c), 1);
        Ui.glyph(g, font, locked ? "?" : job.glyph(), cx + 1, y + dy(19, 14), compact() ? 1.1f : 1.35f, locked ? Ui.DIM : Ui.TEXT);
        Component name = soon ? Component.translatable("rpgmod.jobchange.soon") : locked ? Component.translatable("rpgmod.jobchange.hidden")
                : Component.translatable("rpgmod.job." + job.id());
        fitText(g, name, cx, y + dy(33, 24), CARD_W - 4, 1f, sel ? Ui.argb(c) : locked ? Ui.DIM : Ui.TEXT);
        if (!change) {
            fitText(g, Component.translatable("rpgmod.job." + job.id() + ".role"), cx, y + dy(47, 34), CARD_W - 4, 0.75f, Ui.DIM);
            return;
        }
        if (soon) return;
        // job change: current job / mastery of the base job (the key to its advanced job)
        if (current) {
            fitText(g, Component.translatable("rpgmod.jobchange.current_tag"), cx, y + dy(47, 34), CARD_W - 4, 0.8f, Ui.GOLD);
        } else if (base == null) { // a hybrid job: how many of its two base jobs are mastered
            var d = JobClient.data();
            java.util.List<String> two = JobDefs.HYBRIDS.get(job.id());
            int done = 0, sum = 0;
            for (String b : two) {
                if (d.mastered(b)) done++;
                sum += d.masteryPercent(b);
            }
            int bx = x + 5, bw = CARD_W - 10, by = y + dy(55, 42);
            g.fill(bx, by, bx + bw, by + 3, 0xFF0B0D12);
            g.fill(bx, by, bx + Math.round(bw * sum / 200f), by + 3, done == 2 ? Ui.GOLD : Ui.EXP);
            fitText(g, Component.translatable("rpgmod.jobchange.hybrid_count", done), cx, y + dy(45, 35), CARD_W - 4, 0.75f, locked ? Ui.BAD : Ui.GOLD);
        } else {
            int pct = JobClient.data().masteryPercent(base.id);
            int bx = x + 5, bw = CARD_W - 10, by = y + dy(55, 42);
            g.fill(bx, by, bx + bw, by + 3, 0xFF0B0D12);
            g.fill(bx, by, bx + Math.round(bw * pct / 100f), by + 3, pct >= 100 ? Ui.GOLD : Ui.EXP);
            fitText(g, Component.translatable("rpgmod.jobchange.mastery_pct", pct), cx, y + dy(45, 35), CARD_W - 4, 0.75f, locked ? Ui.BAD : pct >= 100 ? Ui.GOLD : Ui.DIM);
        }
    }

    private void drawDetail(GuiGraphicsExtractor g) {
        int x = left + 8, y = top + CARDS_Y + rows() * (cardH + GAP) + 2;
        int w = W - 16, h = 56;
        Ui.inset(g, x, y, w, h);
        if (selected < 0) {
            g.centeredText(font, Component.translatable("rpgmod.jobselect.hint"), x + w / 2, y + h / 2 - 4, Ui.DIM);
            return;
        }
        JobType job = JobType.byId(com.jo112ss.rpgmod.job.JobManager.baseOf(idOf(selected)).id);
        JobDefs.Job def = defOf(selected);
        String problem = problem(selected);
        boolean soon = "rpgmod.jobchange.no_advanced".equals(problem);
        boolean hidden = "rpgmod.jobchange.need_mastery".equals(problem) || soon;
        g.text(font, soon ? Component.translatable("rpgmod.jobchange.soon") : hidden ? Component.translatable("rpgmod.jobchange.hidden")
                : Component.translatable("rpgmod.job." + def.id()), x + 6, y + 5, hidden ? Ui.DIM : Ui.argb(def.color()), true);
        Component desc = soon ? Component.translatable("rpgmod.jobchange.soon_desc", Component.translatable("rpgmod.job." + job.id))
                : "rpgmod.jobchange.need_mastery".equals(problem) && job.hybrid
                ? Component.translatable("rpgmod.jobchange.hybrid_locked", Component.translatable("rpgmod.job." + JobDefs.HYBRIDS.get(job.id).get(0)),
                        Component.translatable("rpgmod.job." + JobDefs.HYBRIDS.get(job.id).get(1)),
                        JobClient.data().masteryPercent(JobDefs.HYBRIDS.get(job.id).get(0)), JobClient.data().masteryPercent(JobDefs.HYBRIDS.get(job.id).get(1)))
                : "rpgmod.jobchange.need_mastery".equals(problem)
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
