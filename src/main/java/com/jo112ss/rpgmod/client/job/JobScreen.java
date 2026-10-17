package com.jo112ss.rpgmod.client.job;

import com.jo112ss.rpgmod.item.ModItems;
import com.jo112ss.rpgmod.item.WeaponMaterial;
import com.jo112ss.rpgmod.item.WeaponType;
import com.jo112ss.rpgmod.job.JobData;
import com.jo112ss.rpgmod.job.JobStats;
import com.jo112ss.rpgmod.job.JobType;
import com.jo112ss.rpgmod.job.SpellLoadout;
import com.jo112ss.rpgmod.job.core.JobDefs;
import com.jo112ss.rpgmod.job.core.JobStat;
import com.jo112ss.rpgmod.job.core.SkillDef;
import com.jo112ss.rpgmod.job.core.SkillKind;
import com.jo112ss.rpgmod.job.core.SkillPanel;
import com.jo112ss.rpgmod.network.JobActionPayload;
import com.jo112ss.rpgmod.registry.ModRegistries;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Job menu (K): status, honeycomb skill panel, skill loadout and (wizards) staff spells. */
public class JobScreen extends Screen {
    public enum Tab { STATUS, PANEL, SKILLS, SPELLS }

    private static final int W = 384, H = 258;
    /** Hex cell radius in pixels: the panel zooms with the mouse wheel (and pans by dragging). */
    private static int hexR = 9;
    private static double panX, panY;
    private static final int HEX_MIN = 7, HEX_MAX = 16;
    private boolean pressInPanel, panning;
    private double pressX, pressY;
    private int panelX0, panelY0, panelX1, panelY1;

    private Tab tab;
    private int left, top;
    private int selectedSlot = 0;      // skills tab: 0,1,2
    private int selectedSpellSlot = 0; // spells tab: 0..3
    private String hoveredSkill;
    private SkillPanel.Node hoveredNode;
    private int resetX, resetY, resetW;
    private boolean resetArmed;

    private static int resetCost() {
        return com.jo112ss.rpgmod.job.JobConfig.SPEC.isLoaded() ? com.jo112ss.rpgmod.job.JobConfig.RESET_COST_LEVELS.get() : 20;
    }
    private final Map<Integer, Long> unlockAnim = new HashMap<>();
    private Set<Integer> lastUnlocked = new HashSet<>();

    public JobScreen(Tab tab) {
        super(Component.translatable("rpgmod.jobscreen.title"));
        this.tab = tab;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        lastUnlocked = new HashSet<>(JobClient.data().unlocked());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private List<Tab> tabs() {
        List<Tab> t = new ArrayList<>(List.of(Tab.STATUS, Tab.PANEL, Tab.SKILLS));
        JobData d = JobClient.data();
        if (d.hasJob() && d.jobDef().usesSpells()) t.add(Tab.SPELLS);
        return t;
    }

    private int tabX(int i) {
        return left + 8 + i * 78;
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (JobKeys.STATUS.matches(event)) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (tab == Tab.PANEL && pressInPanel) {
            if (!panning && Math.hypot(event.x() - pressX, event.y() - pressY) > 3) panning = true;
            if (panning) {
                panX += dx;
                panY += dy;
                clampPan();
            }
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (tab == Tab.PANEL && pressInPanel && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            pressInPanel = false;
            if (!panning) {
                JobData d = JobClient.data();
                SkillPanel.Node n = nodeAt(event.x(), event.y());
                if (d.hasJob() && n != null && d.panel().isReachable(n, d.unlocked()) && d.freePoints() >= n.cost()) {
                    JobClient.send(JobActionPayload.UNLOCK_NODE, n.id(), "");
                }
            }
            panning = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (tab == Tab.PANEL && in(x, y, panelX0, panelY0, panelX1 - panelX0, panelY1 - panelY0) && scrollY != 0) {
            int old = hexR;
            hexR = Math.max(HEX_MIN, Math.min(HEX_MAX, hexR + (scrollY > 0 ? 1 : -1)));
            if (hexR != old) {
                // zoom around the mouse
                double cx = (panelX0 + panelX1) / 2.0 + panX, cy = (panelY0 + panelY1) / 2.0 + panY;
                double k = hexR / (double) old;
                panX += (x - cx) * (1 - k);
                panY += (y - cy) * (1 - k);
                clampPan();
            }
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    /** Keeps the honeycomb from being dragged entirely out of view. */
    private void clampPan() {
        double limX = hexR * Math.sqrt(3) * SkillPanel.RADIUS, limY = hexR * 1.5 * SkillPanel.RADIUS;
        panX = Math.max(-limX, Math.min(limX, panX));
        panY = Math.max(-limY, Math.min(limY, panY));
    }

    /** The panel cell under a screen point, using the current zoom and pan. */
    private SkillPanel.Node nodeAt(double mx, double my) {
        JobData d = JobClient.data();
        if (!d.hasJob() || !in(mx, my, panelX0, panelY0, panelX1 - panelX0, panelY1 - panelY0)) return null;
        int ox = (panelX0 + panelX1) / 2 + (int) panX, oy = (panelY0 + panelY1) / 2 + (int) panY;
        for (SkillPanel.Node n : d.panel().nodes()) {
            int px = ox + (int) Math.round(hexR * Math.sqrt(3) * (n.q() + n.r() / 2.0));
            int py = oy + (int) Math.round(hexR * 1.5 * n.r());
            if (Math.hypot(mx - px, my - py) < hexR * 0.85) return n;
        }
        return null;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) return super.mouseClicked(event, doubleClick);
        double mx = event.x(), my = event.y();
        List<Tab> tabs = tabs();
        for (int i = 0; i < tabs.size(); i++) {
            if (mx >= tabX(i) && mx < tabX(i) + 74 && my >= top + 18 && my < top + 32) {
                tab = tabs.get(i);
                return true;
            }
        }
        JobData d = JobClient.data();
        if (!d.hasJob()) return super.mouseClicked(event, doubleClick);
        switch (tab) {
            case PANEL -> {
                if (resetW > 0 && in(mx, my, resetX, resetY, resetW, 18)) {
                    if (resetArmed) {
                        JobClient.send(JobActionPayload.RESET_PANEL, 0, "");
                        resetArmed = false;
                    } else {
                        resetArmed = true;
                    }
                    return true;
                }
                resetArmed = false;
                if (in(mx, my, panelX0, panelY0, panelX1 - panelX0, panelY1 - panelY0)) {
                    pressInPanel = true;
                    panning = false;
                    pressX = mx;
                    pressY = my;
                    return true;
                }
            }
            case SKILLS -> {
                for (int s = 0; s < 3; s++) {
                    if (in(mx, my, slotX(s), top + 50, 34, 34)) {
                        selectedSlot = s;
                        return true;
                    }
                }
                String clicked = skillCardAt(mx, my, skillList(d));
                if (clicked != null && d.knows(clicked)) {
                    SkillDef def = JobDefs.skill(clicked);
                    int slot = def.kind() == SkillKind.ULTIMATE ? 2 : (selectedSlot == 2 ? 0 : selectedSlot);
                    JobClient.send(JobActionPayload.EQUIP_SKILL, slot, clicked);
                    return true;
                }
            }
            case SPELLS -> {
                for (int s = 0; s < SpellLoadout.SLOTS; s++) {
                    if (in(mx, my, spellSlotX(s), top + 50, 34, 34)) {
                        selectedSpellSlot = s;
                        return true;
                    }
                }
                String clicked = skillCardAt(mx, my, spellList(d));
                if (clicked != null && d.knows(clicked) && JobClient.holdingStaff()) {
                    JobClient.send(JobActionPayload.SET_SPELL, selectedSpellSlot, clicked);
                    selectedSpellSlot = (selectedSpellSlot + 1) % SpellLoadout.SLOTS;
                    return true;
                }
            }
            default -> { }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        JobData d = JobClient.data();
        JobType job = d.jobType();
        int accent = job != null ? job.color() : 0xC9A45C;
        Ui.panel(g, left, top, W, H, accent);
        MutableComponent header = Component.translatable("rpgmod.jobscreen.title");
        if (job != null) header = Component.translatable("rpgmod.job." + job.id).withColor(job.color()).append(Component.literal("  Lv " + d.level()).withColor(Ui.TEXT));
        g.text(font, header, left + 10, top + 6, Ui.TEXT, true);
        Component pts = Component.translatable("rpgmod.jobscreen.points", d.freePoints());
        g.text(font, pts, left + W - 10 - font.width(pts), top + 6, d.freePoints() > 0 ? Ui.GOLD : Ui.DIM, false);

        List<Tab> tabs = tabs();
        for (int i = 0; i < tabs.size(); i++) {
            boolean sel = tabs.get(i) == tab;
            boolean hover = in(mouseX, mouseY, tabX(i), top + 18, 74, 14);
            int x = tabX(i);
            g.fill(x, top + 18, x + 74, top + 32, sel ? Ui.argb(accent) : hover ? Ui.EDGE_LIGHT : Ui.EDGE);
            g.fill(x + 1, top + 19, x + 73, top + 32, sel ? Ui.mix(Ui.argb(accent), 0xFF101218, 0.55f) : Ui.BG_INNER);
            g.centeredText(font, Component.translatable("rpgmod.jobscreen.tab." + tabs.get(i).name().toLowerCase(java.util.Locale.ROOT)),
                    x + 37, top + 21, sel ? Ui.TEXT : Ui.DIM);
        }
        g.fill(left + 1, top + 32, left + W - 1, top + 33, Ui.argb(accent));

        hoveredSkill = null;
        hoveredNode = null;
        if (job == null) {
            g.centeredText(font, Component.translatable("rpgmod.jobscreen.nojob"), left + W / 2, top + H / 2, Ui.DIM);
        } else {
            switch (tab) {
                case STATUS -> drawStatus(g, d, job, mouseX, mouseY);
                case PANEL -> drawPanel(g, d, job, mouseX, mouseY);
                case SKILLS -> drawSkills(g, d, mouseX, mouseY);
                case SPELLS -> drawSpells(g, d, mouseX, mouseY);
            }
        }
        super.extractRenderState(g, mouseX, mouseY, a);
    }

    // ---- status

    private void drawStatus(GuiGraphicsExtractor g, JobData d, JobType job, int mouseX, int mouseY) {
        Player p = minecraft.player;
        int cx = left + 8, cy = top + 40, cw = 112, ch = H - 48;
        Ui.inset(g, cx, cy, cw, ch);
        int ex = cx + cw / 2, ey = cy + 30;
        Ui.disc(g, ex, ey, 23, Ui.mix(Ui.argb(job.color()), 0xFF000000, 0.4f));
        Ui.circle(g, ex, ey, 23, Ui.argb(job.color()), 2);
        Ui.circle(g, ex, ey, 19, Ui.alpha(job.color(), 0x80), 1);
        Ui.glyph(g, font, job.def().glyph(), ex + 1, ey + 1, 2.6f, Ui.TEXT);
        g.centeredText(font, Component.translatable("rpgmod.job." + job.id), ex, cy + 60, Ui.argb(job.color()));
        Ui.glyph(g, font, "Lv " + d.level(), ex, cy + 78, 1.5f, Ui.TEXT);
        float frac = d.expToNext() > 0 ? (float) d.exp() / d.expToNext() : 1f;
        Ui.bar(g, font, cx + 6, cy + 90, cw - 12, 10, frac, Ui.EXP, d.exp() + " / " + d.expToNext());
        g.text(font, Component.translatable("rpgmod.jobscreen.exp"), cx + 6, cy + 102, Ui.DIM, false);
        Map<JobStat, Double> st = JobStats.compute(d);
        double maxMana = com.jo112ss.rpgmod.job.JobManager.maxMana(p);
        Ui.bar(g, font, cx + 6, cy + 116, cw - 12, 10, (float) (d.mana() / maxMana), Ui.MANA, Math.round(d.mana()) + " / " + Math.round(maxMana));
        g.text(font, Component.translatable("rpgmod.jobscreen.mana"), cx + 6, cy + 128, Ui.DIM, false);
        g.text(font, Component.translatable("rpgmod.jobscreen.weapons"), cx + 6, cy + 144, Ui.DIM, false);
        int wx = cx + 6;
        for (WeaponType t : job.weapons) {
            g.item(new ItemStack(ModItems.get(t, WeaponMaterial.IRON)), wx, cy + 155);
            wx += 18;
        }

        // stat table
        int sx = left + 128, sy = top + 40, sw = W - 136;
        Ui.inset(g, sx, sy, sw, 118);
        g.text(font, Component.translatable("rpgmod.jobscreen.stats"), sx + 6, sy + 5, Ui.GOLD, false);
        List<Component[]> rows = new ArrayList<>();
        rows.add(row("max_health", fmt(p.getMaxHealth())));
        rows.add(row("attack_damage", fmt(com.jo112ss.rpgmod.job.JobManager.physicalAttack(p))));
        rows.add(row("armor", fmt(p.getAttributeValue(Attributes.ARMOR))));
        if (com.jo112ss.rpgmod.mob.MobConfig.rpgArmor()) {
            double red = com.jo112ss.rpgmod.mob.Defense.reduction(p) * 100;
            rows.add(row("damage_reduction", red < 0.5 ? "0%" : String.format("-%.0f%%", red)));
        } else {
            rows.add(row("armor_toughness", fmt(p.getAttributeValue(Attributes.ARMOR_TOUGHNESS))));
        }
        rows.add(row("movement_speed", String.format("%.0f%%", p.getAttributeValue(Attributes.MOVEMENT_SPEED) / 0.1 * 100)));
        rows.add(row("attack_speed", String.format("%.2f", p.getAttributeValue(Attributes.ATTACK_SPEED))));
        rows.add(row("crit_chance", String.format("%.1f%% ×%.2f", com.jo112ss.rpgmod.job.JobManager.critChance(p, null), com.jo112ss.rpgmod.job.JobManager.critMultiplier(p, null))));
        rows.add(row("max_mana", fmt(maxMana)));
        rows.add(row("mana_regen", String.format("%.1f/s", com.jo112ss.rpgmod.job.JobManager.manaRegen(p))));
        rows.add(row("skill_power", String.format("+%.0f%%", st.get(JobStat.SKILL_POWER))));
        rows.add(row("cooldown_reduction", String.format("%.0f%%", st.get(JobStat.COOLDOWN_REDUCTION))));
        rows.add(row("luck", fmt(p.getAttributeValue(Attributes.LUCK))));
        rows.add(row("magic_attack", fmt(com.jo112ss.rpgmod.job.JobManager.magicAttack(p))));
        rows.add(row("cast_speed", String.format("-%.0f%%", Math.min(60, com.jo112ss.rpgmod.job.JobManager.castSpeedPercent(p)))));
        for (int i = 0; i < rows.size(); i++) {
            int col = i / 7, r = i % 7;
            int rx = sx + 6 + col * (sw / 2), ry = sy + 17 + r * 14;
            if (r % 2 == 0) g.fill(rx - 2, ry - 3, rx + sw / 2 - 10, ry + 11, 0x18FFFFFF);
            g.text(font, rows.get(i)[0], rx, ry, Ui.DIM, false);
            g.text(font, rows.get(i)[1], rx + sw / 2 - 14 - font.width(rows.get(i)[1]), ry, Ui.TEXT, false);
        }

        // equipped skills
        int qy = sy + 124;
        Ui.inset(g, sx, qy, sw, H - (qy - top) - 8);
        g.text(font, Component.translatable("rpgmod.jobscreen.equipped"), sx + 6, qy + 5, Ui.GOLD, false);
        int colW = (sw - 8) / 3;
        for (int s = 0; s < 3; s++) {
            int colX = sx + 4 + s * colW, iy = qy + 16;
            SkillDef def = JobDefs.skill(d.slot(s));
            int ix = colX + 4;
            Ui.skillIcon(g, font, ix, iy, 22, def, false);
            g.text(font, Component.translatable("rpgmod.jobscreen.slot." + s), ix + 26, iy + 2, Ui.DIM, false);
            g.text(font, Component.literal("[").append(JobKeys.SKILLS[s].getTranslatedKeyMessage()).append("]"), ix + 26, iy + 12, Ui.DIM, false);
            Component nm = def == null ? Component.literal("-") : Ui.skillName(def.id());
            g.text(font, font.substrByWidth(nm, colW - 6).getString(), colX + 4, iy + 26, Ui.TEXT, false);
            if (in(mouseX, mouseY, ix, iy, 22, 22) && def != null) hoveredSkill = def.id();
        }
        drawSkillTooltip(g, mouseX, mouseY);
    }

    private Component[] row(String stat, String value) {
        return new Component[] {Component.translatable("rpgmod.stat." + stat), Component.literal(value)};
    }

    private static String fmt(double v) {
        return Math.abs(v - Math.round(v)) < 0.05 ? String.valueOf(Math.round(v)) : String.format("%.1f", v);
    }

    // ---- honeycomb panel

    private void drawPanel(GuiGraphicsExtractor g, JobData d, JobType job, int mouseX, int mouseY) {
        SkillPanel panel = d.panel();
        long now = System.currentTimeMillis();
        for (int id : d.unlocked()) if (!lastUnlocked.contains(id)) unlockAnim.put(id, now);
        lastUnlocked = new HashSet<>(d.unlocked());

        int ax = left + 8, ay = top + 40, aw = 252, ah = H - 48;
        Ui.inset(g, ax, ay, aw, ah);
        int ox = ax + aw / 2 + (int) panX, oy = ay + ah / 2 + (int) panY;
        panelX0 = ax;
        panelY0 = ay;
        panelX1 = ax + aw;
        panelY1 = ay + ah;
        g.enableScissor(ax + 1, ay + 1, ax + aw - 1, ay + ah - 1);
        // subtle radial backdrop
        int rad = (int) (hexR * 1.6 * (SkillPanel.RADIUS + 0.6));
        Ui.disc(g, ox, oy, rad, 0x0CFFFFFF);
        Ui.disc(g, ox, oy, rad * 2 / 3, 0x0CFFFFFF);
        Ui.disc(g, ox, oy, rad / 3, 0x0CFFFFFF);
        int c = job.color();
        float pulse = (float) (0.5 + 0.5 * Math.sin(now / 220.0));
        // pass 1: soft glow behind owned cells so the unlocked area reads as one shape
        for (SkillPanel.Node n : panel.nodes()) {
            if (!d.unlocked().contains(n.id())) continue;
            int px = ox + (int) Math.round(hexR * Math.sqrt(3) * (n.q() + n.r() / 2.0));
            int py = oy + (int) Math.round(hexR * 1.5 * n.r());
            Ui.hex(g, px, py, hexR + 3, Ui.alpha(c, 0x55));
        }
        // pass 2: cells
        for (SkillPanel.Node n : panel.nodes()) {
            int px = ox + (int) Math.round(hexR * Math.sqrt(3) * (n.q() + n.r() / 2.0));
            int py = oy + (int) Math.round(hexR * 1.5 * n.r());
            boolean owned = d.unlocked().contains(n.id());
            boolean reachable = panel.isReachable(n, d.unlocked());
            boolean affordable = reachable && d.freePoints() >= n.cost();
            int typeTint = switch (n.type()) {
                case ULTIMATE -> Ui.GOLD;
                case SPELL -> 0xFFB09CFF;
                case SKILL -> Ui.mix(Ui.argb(c), 0xFFFFFFFF, 0.4f);
                default -> 0;
            };
            boolean special = typeTint != 0;
            int edge, body, tc;
            if (special && !owned) {
                // skill / spell / ultimate cells keep their own colour in every state, with a double
                // frame, so they never look like a stat cell; reachable ones also get a gold ring (pass 3)
                float k = affordable ? 0.5f - pulse * 0.15f : reachable ? 0.62f : 0.8f;
                body = Ui.mix(typeTint, 0xFF0B0D12, k);
                // gold frame = can be learned now, same rule as stat cells
                edge = affordable ? Ui.mix(Ui.GOLD, 0xFFFFFFFF, pulse * 0.6f) : reachable ? typeTint : Ui.mix(typeTint, 0xFF0D0F14, 0.45f);
                tc = reachable ? 0xFFFFFFFF : Ui.mix(typeTint, 0xFF0D0F14, 0.3f);
            } else if (owned) {
                body = n.type() == SkillPanel.NodeType.STAT ? Ui.argb(c) : Ui.mix(Ui.argb(c), 0xFFFFFFFF, 0.2f);
                edge = typeTint != 0 ? typeTint : Ui.mix(Ui.argb(c), 0xFFFFFFFF, 0.6f);
                tc = 0xFFFFFFFF;
            } else if (affordable) {
                body = 0xFF262B3A;
                edge = Ui.mix(Ui.GOLD, 0xFFFFFFFF, pulse * 0.6f);
                tc = Ui.TEXT;
            } else if (reachable) {
                body = 0xFF1E2230;
                edge = 0xFF8A6A2A;
                tc = Ui.DIM;
            } else {
                body = 0xFF0D0F14;
                edge = typeTint != 0 ? Ui.mix(typeTint, 0xFF0D0F14, 0.65f) : 0xFF252933;
                tc = 0xFF3C424E;
            }
            boolean hover = !panning && in(mouseX, mouseY, ax, ay, aw, ah) && Math.hypot(mouseX - px, mouseY - py) < hexR * 0.85;
            if (hover) hoveredNode = n;
            if (special && !owned) {
                Ui.hex(g, px, py, hexR, hover ? 0xFFFFFFFF : edge);
                Ui.hex(g, px, py, hexR - 2, 0xFF0B0D12);
                Ui.hex(g, px, py, hexR - 3, body);
            } else {
                Ui.hexFramed(g, px, py, hexR, hover ? 0xFFFFFFFF : edge, body);
            }
            if (owned) Ui.hex(g, px, py - hexR / 2, Math.max(2, hexR / 3), 0x30FFFFFF); // small highlight
            Long t = unlockAnim.get(n.id());
            if (t != null) {
                float k = (now - t) / 700f;
                if (k < 1) {
                    Ui.circle(g, px, py, (int) (hexR * (1 + k * 1.2f)), Ui.alpha(0xFFFFFF, (int) (255 * (1 - k))), 1);
                    Ui.circle(g, px, py, (int) (hexR * (1 + k * 0.6f)), Ui.alpha(c, (int) (255 * (1 - k))), 1);
                } else unlockAnim.remove(n.id());
            }
            String glyph = switch (n.type()) {
                case START -> job.def().glyph();
                case STAT -> n.stat().glyph;
                default -> JobDefs.skill(n.skill()).glyph();
            };
            float gs = hexR / 11f * (n.type() == SkillPanel.NodeType.STAT ? 1f : 1.15f);
            if (font.width(glyph) > 8) gs *= 0.8f;
            Ui.glyph(g, font, glyph, px + 1, py + 1, gs, tc);
        }
        // pass 3: a pulsing gold ring around skill cells that can be learned now (drawn last so the
        // neighbouring cells do not cover it); a dimmer one when they are next in line but need points
        for (SkillPanel.Node n : panel.nodes()) {
            if (n.type() == SkillPanel.NodeType.STAT || n.type() == SkillPanel.NodeType.START) continue;
            if (!panel.isReachable(n, d.unlocked())) continue;
            int px = ox + (int) Math.round(hexR * Math.sqrt(3) * (n.q() + n.r() / 2.0));
            int py = oy + (int) Math.round(hexR * 1.5 * n.r());
            if (d.freePoints() >= n.cost()) {
                int a = (int) (140 + 115 * pulse);
                Ui.circle(g, px, py, hexR + 2, Ui.alpha(Ui.GOLD, a), 1);
                Ui.circle(g, px, py, hexR + 3 + (int) (pulse * 2), Ui.alpha(Ui.GOLD, a / 3), 1);
            } else {
                Ui.circle(g, px, py, hexR + 2, Ui.alpha(0x8A6A2A, 0xB0), 1);
            }
        }
        g.disableScissor();
        // hint and legend sit on a dark plate so the honeycomb scrolling under them stays readable
        Component hint = Component.translatable("rpgmod.jobscreen.zoom_hint");
        g.fill(ax + 1, ay + ah - 14, ax + 9 + font.width(hint), ay + ah - 1, 0xD00B0D12);
        g.text(font, hint, ax + 5, ay + ah - 11, 0xFF7A8090, false);
        // legend (top-left corner)
        int lx = ax + 5, ly = ay + 5;
        int lw = 14 + Math.max(Math.max(font.width(Component.translatable("rpgmod.node.legend_owned")), font.width(Component.translatable("rpgmod.node.legend_skill"))),
                Math.max(font.width(Component.translatable("rpgmod.node.legend_open")), font.width(Component.translatable("rpgmod.node.legend_locked"))));
        g.fill(ax + 1, ay + 1, lx + lw + 3, ly + 44, 0xD00B0D12);
        Ui.hexFramed(g, lx + 4, ly + 3, 5, Ui.mix(Ui.argb(c), 0xFFFFFFFF, 0.6f), Ui.argb(c));
        g.text(font, Component.translatable("rpgmod.node.legend_owned"), lx + 12, ly, Ui.DIM, false);
        Ui.hexFramed(g, lx + 4, ly + 14, 5, Ui.GOLD, 0xFF262B3A);
        g.text(font, Component.translatable("rpgmod.node.legend_open"), lx + 12, ly + 11, Ui.DIM, false);
        Ui.hexFramed(g, lx + 4, ly + 25, 5, 0xFF252933, 0xFF0D0F14);
        g.text(font, Component.translatable("rpgmod.node.legend_locked"), lx + 12, ly + 22, Ui.DIM, false);
        int skTint = Ui.mix(Ui.argb(c), 0xFFFFFFFF, 0.4f);
        Ui.hex(g, lx + 4, ly + 36, 5, Ui.GOLD);
        Ui.hex(g, lx + 4, ly + 36, 3, Ui.mix(skTint, 0xFF0B0D12, 0.55f));
        Ui.circle(g, lx + 4, ly + 36, 6, Ui.alpha(Ui.GOLD, (int) (140 + 115 * pulse)), 1);
        g.text(font, Component.translatable("rpgmod.node.legend_skill"), lx + 12, ly + 33, Ui.DIM, false);

        // info column
        int ix = ax + aw + 6, iy = ay, iw = W - (ix - left) - 8;
        Ui.inset(g, ix, iy, iw, ah);
        g.text(font, Component.translatable("rpgmod.jobscreen.points_short"), ix + 5, iy + 5, Ui.DIM, false);
        Ui.glyph(g, font, String.valueOf(d.freePoints()), ix + iw - 14, iy + 9, 1.5f, d.freePoints() > 0 ? Ui.GOLD : Ui.DIM);
        g.fill(ix + 4, iy + 20, ix + iw - 4, iy + 21, Ui.EDGE);
        if (hoveredNode != null) {
            SkillPanel.Node n = hoveredNode;
            int ty = iy + 26;
            Component name;
            Component sub;
            if (n.type() == SkillPanel.NodeType.STAT) {
                String v = "+" + fmt(n.amount()) + (n.stat().percent ? "%" : "");
                name = Component.translatable("rpgmod.stat." + n.stat().id).append(" " + v);
                sub = Component.translatable("rpgmod.node.stat");
            } else if (n.type() == SkillPanel.NodeType.START) {
                name = Component.translatable("rpgmod.node.start");
                sub = Ui.skillName(job.def().actives().get(0));
            } else {
                name = Ui.skillName(n.skill());
                sub = Component.translatable("rpgmod.node." + n.type().name().toLowerCase(java.util.Locale.ROOT));
            }
            g.textWithWordWrap(font, name, ix + 5, ty, iw - 10, Ui.TEXT, false);
            ty += font.split(name, iw - 10).size() * 9 + 3;
            g.text(font, sub, ix + 5, ty, Ui.DIM, false);
            ty += 14;
            if (n.skill() != null) {
                ty = drawSkillFacts(g, n.skill(), ix + 5, ty, iw - 10);
            }
            boolean owned = d.unlocked().contains(n.id());
            boolean reachable = d.panel().isReachable(n, d.unlocked());
            Component state = owned ? Component.translatable("rpgmod.node.owned")
                    : !reachable ? Component.translatable("rpgmod.node.locked")
                    : d.freePoints() >= n.cost() ? Component.translatable("rpgmod.node.click", n.cost())
                    : Component.translatable("rpgmod.node.need", n.cost());
            int sc = owned ? Ui.EXP : reachable && d.freePoints() >= n.cost() ? Ui.GOLD : Ui.BAD;
            g.textWithWordWrap(font, state, ix + 5, iy + ah - 50, iw - 10, sc, false);
        } else {
            g.textWithWordWrap(font, Component.translatable("rpgmod.jobscreen.panel_hint"), ix + 5, iy + 28, iw - 10, Ui.DIM, false);
        }
        // reset button
        resetX = ix + 4;
        resetY = iy + ah - 22;
        resetW = iw - 8;
        boolean canReset = d.unlocked().size() > 1 && (minecraft.player.hasInfiniteMaterials() || minecraft.player.experienceLevel >= resetCost());
        boolean hov = in(mouseX, mouseY, resetX, resetY, resetW, 18);
        g.fill(resetX, resetY, resetX + resetW, resetY + 18, resetArmed ? Ui.BAD : hov && canReset ? Ui.EDGE_LIGHT : Ui.EDGE);
        g.fill(resetX + 1, resetY + 1, resetX + resetW - 1, resetY + 17, canReset ? 0xFF1E2230 : 0xFF14161C);
        Component label = resetArmed ? Component.translatable("rpgmod.jobscreen.reset_confirm")
                : Component.translatable("rpgmod.jobscreen.reset", resetCost());
        g.centeredText(font, label, resetX + resetW / 2, resetY + 5, canReset ? (resetArmed ? Ui.BAD : Ui.TEXT) : 0xFF5A6070);
    }

    /** Cooldown / mana / description lines for a skill; returns the next y. */
    private int drawSkillFacts(GuiGraphicsExtractor g, String id, int x, int y, int w) {
        SkillDef def = JobDefs.skill(id);
        if (def == null) return y;
        g.text(font, Component.translatable("rpgmod.jobscreen.cd", fmt(def.cooldown())), x, y, Ui.DIM, false);
        g.text(font, Component.translatable("rpgmod.jobscreen.mp", fmt(def.mana())), x + w / 2, y, Ui.MANA, false);
        y += 12;
        if (def.castTime() > 0) {
            g.text(font, Component.translatable("rpgmod.jobscreen.cast", fmt(def.castTime())), x, y, 0xFFB09CFF, false);
            y += 12;
        }
        Component power = powerLine(def);
        if (power != null) {
            g.text(font, power, x, y, 0xFFFFC870, false);
            y += 12;
        }
        int lines = font.split(Ui.skillDesc(id), w).size();
        g.textWithWordWrap(font, Ui.skillDesc(id), x, y, w, Ui.TEXT, false);
        return y + lines * 9 + 4;
    }

    // ---- skills loadout

    private int slotX(int s) {
        return left + 16 + s * 70;
    }

    /** Every skill of the job (actives then ultimates), learnt or not. */
    private List<String> skillList(JobData d) {
        List<String> out = new ArrayList<>(d.jobDef().actives());
        out.addAll(d.jobDef().ultimates());
        return out;
    }

    private List<String> spellList(JobData d) {
        return new ArrayList<>(d.jobDef().spells());
    }

    private int cardX(int i) {
        return left + 10 + (i % 2) * 102;
    }

    private int cardY(int i) {
        return top + 104 + (i / 2) * 30;
    }

    private String skillCardAt(double mx, double my, List<String> list) {
        for (int i = 0; i < list.size(); i++) {
            if (in(mx, my, cardX(i), cardY(i), 98, 26)) return list.get(i);
        }
        return null;
    }

    private void drawSkills(GuiGraphicsExtractor g, JobData d, int mouseX, int mouseY) {
        g.text(font, Component.translatable("rpgmod.jobscreen.loadout"), left + 10, top + 38, Ui.GOLD, false);
        for (int s = 0; s < 3; s++) {
            int x = slotX(s), y = top + 50;
            boolean sel = s == selectedSlot;
            g.fill(x - 2, y - 2, x + 36, y + 36, sel ? Ui.GOLD : Ui.EDGE);
            SkillDef def = JobDefs.skill(d.slot(s));
            Ui.skillIcon(g, font, x, y, 34, def, false);
            Component label = Component.translatable("rpgmod.jobscreen.slot." + s).append(" [").append(JobKeys.SKILLS[s].getTranslatedKeyMessage()).append("]");
            g.text(font, label, x, y + 38, sel ? Ui.TEXT : Ui.DIM, false);
            if (in(mouseX, mouseY, x, y, 34, 34) && def != null) hoveredSkill = def.id();
        }
        g.textWithWordWrap(font, Component.translatable("rpgmod.jobscreen.loadout_hint"), left + 222, top + 40, W - 230, Ui.DIM, false);
        drawCards(g, d, skillList(d), mouseX, mouseY, true);
        drawDetailColumn(g, d);
    }

    private void drawCards(GuiGraphicsExtractor g, JobData d, List<String> list, int mouseX, int mouseY, boolean showEquipped) {
        for (int i = 0; i < list.size(); i++) {
            String id = list.get(i);
            SkillDef def = JobDefs.skill(id);
            boolean known = d.knows(id);
            int x = cardX(i), y = cardY(i);
            boolean hover = in(mouseX, mouseY, x, y, 98, 26);
            if (hover) hoveredSkill = id;
            int slotOf = -1;
            for (int s = 0; s < 3; s++) if (d.slot(s).equals(id)) slotOf = s;
            g.fill(x, y, x + 98, y + 26, hover && known ? Ui.EDGE_LIGHT : Ui.EDGE);
            g.fill(x + 1, y + 1, x + 97, y + 25, known ? 0xFF181C26 : 0xFF111318);
            Ui.skillIcon(g, font, x + 3, y + 3, 20, def, !known);
            g.text(font, font.substrByWidth(Ui.skillName(id), 70).getString(), x + 26, y + 4, known ? Ui.TEXT : 0xFF5A6070, false);
            Component tag = !known ? Component.translatable("rpgmod.jobscreen.locked")
                    : (showEquipped && slotOf >= 0) ? Component.translatable("rpgmod.jobscreen.slot." + slotOf)
                    : Component.translatable("rpgmod.node." + def.kind().name().toLowerCase(java.util.Locale.ROOT));
            g.text(font, tag, x + 26, y + 15, !known ? 0xFF5A6070 : slotOf >= 0 ? Ui.GOLD : Ui.DIM, false);
        }
    }

    private void drawDetailColumn(GuiGraphicsExtractor g, JobData d) {
        int x = left + 216, y = top + 100, w = W - 224, h = H - 108;
        Ui.inset(g, x, y, w, h);
        if (hoveredSkill == null) {
            g.textWithWordWrap(font, Component.translatable("rpgmod.jobscreen.hover_hint"), x + 5, y + 6, w - 10, Ui.DIM, false);
            return;
        }
        g.textWithWordWrap(font, Ui.skillName(hoveredSkill), x + 5, y + 5, w - 10, Ui.TEXT, false);
        drawSkillFacts(g, hoveredSkill, x + 5, y + 18, w - 10);
        if (!d.knows(hoveredSkill)) {
            g.textWithWordWrap(font, Component.translatable("rpgmod.jobscreen.unlock_in_panel"), x + 5, y + h - 22, w - 10, Ui.BAD, false);
        }
    }

    // ---- spells

    private int spellSlotX(int s) {
        return left + 16 + s * 50;
    }

    private void drawSpells(GuiGraphicsExtractor g, JobData d, int mouseX, int mouseY) {
        g.text(font, Component.translatable("rpgmod.jobscreen.staff"), left + 10, top + 38, Ui.GOLD, false);
        if (!JobClient.holdingStaff()) {
            g.centeredText(font, Component.translatable("rpgmod.jobscreen.need_staff"), left + 112, top + 64, Ui.BAD);
        } else {
            ItemStack staff = minecraft.player.getMainHandItem();
            SpellLoadout l = staff.getOrDefault(ModRegistries.SPELL_LOADOUT.get(), SpellLoadout.EMPTY);
            for (int s = 0; s < SpellLoadout.SLOTS; s++) {
                int x = spellSlotX(s), y = top + 50;
                boolean sel = s == selectedSpellSlot;
                g.fill(x - 2, y - 2, x + 36, y + 36, sel ? Ui.GOLD : Ui.EDGE);
                SkillDef def = JobDefs.skill(l.spell(s));
                Ui.skillIcon(g, font, x, y, 34, def, false);
                g.text(font, (s + 1) + (l.selected() == s ? " ●" : ""), x, y + 38, l.selected() == s ? Ui.argb(0xB09CFF) : Ui.DIM, false);
                if (in(mouseX, mouseY, x, y, 34, 34) && def != null) hoveredSkill = def.id();
            }
        }
        g.textWithWordWrap(font, Component.translatable("rpgmod.jobscreen.spell_hint", JobKeys.SPELL_WHEEL.getTranslatedKeyMessage()),
                left + 222, top + 40, W - 230, Ui.DIM, false);
        drawCards(g, d, spellList(d), mouseX, mouseY, false);
        drawDetailColumn(g, d);
    }

    /**
     * "Damage: attack x150% (now 10.5)" - what the skill scales with and what it would do right now
     * with the held weapon (before crits and the target's armour).
     */
    private Component powerLine(SkillDef def) {
        String kind = JobDefs.powerKind(def);
        if (kind == null || minecraft.player == null) return null;
        boolean magic = JobDefs.isMagic(def);
        double now = def.power() * com.jo112ss.rpgmod.job.JobManager.powerMultiplier(minecraft.player, def);
        return Component.translatable("rpgmod.jobscreen.power." + kind,
                Component.translatable(magic ? "rpgmod.stat.magic_attack" : "rpgmod.stat.attack_damage"),
                String.format("%.0f", def.power() * 100), String.format("%.1f", now));
    }

    private void drawSkillTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (hoveredSkill == null) return;
        List<Component> lines = new ArrayList<>();
        lines.add(Ui.skillName(hoveredSkill));
        SkillDef def = JobDefs.skill(hoveredSkill);
        lines.add(Component.translatable("rpgmod.jobscreen.cd", fmt(def.cooldown())).append("  ").append(Component.translatable("rpgmod.jobscreen.mp", fmt(def.mana()))));
        Component power = powerLine(def);
        if (power != null) lines.add(power.copy().withColor(0xFFFFC870));
        lines.add(Ui.skillDesc(hoveredSkill));
        g.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
    }
}
