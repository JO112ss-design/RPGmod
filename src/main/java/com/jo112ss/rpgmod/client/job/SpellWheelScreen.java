package com.jo112ss.rpgmod.client.job;

import com.jo112ss.rpgmod.job.JobData;
import com.jo112ss.rpgmod.job.SpellLoadout;
import com.jo112ss.rpgmod.job.core.JobDefs;
import com.jo112ss.rpgmod.job.core.SkillDef;
import com.jo112ss.rpgmod.network.JobActionPayload;
import com.jo112ss.rpgmod.registry.ModRegistries;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Hold the spell key to open, point the mouse towards a spell, release (or click) to make it the
 * staff's active spell. Four slots around a ring, like a radial quick-select.
 */
public class SpellWheelScreen extends Screen {
    private static final int RING = 64;
    /** Dev test only: keep the wheel open without the key held. */
    public static boolean testHold;
    private int hovered = -1;
    private final long openedAt = System.currentTimeMillis();

    public SpellWheelScreen() {
        super(Component.translatable("rpgmod.wheel.title"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void extractBlurredBackground(GuiGraphicsExtractor graphics) {
        // keep the world visible behind the wheel
    }

    private SpellLoadout loadout() {
        if (!JobClient.holdingStaff()) return SpellLoadout.EMPTY;
        ItemStack s = minecraft.player.getMainHandItem();
        return s.getOrDefault(ModRegistries.SPELL_LOADOUT.get(), SpellLoadout.EMPTY);
    }

    /** Slot positions: top, right, bottom, left. */
    private int[] slotPos(int i) {
        double a = -Math.PI / 2 + i * Math.PI / 2;
        return new int[] {width / 2 + (int) Math.round(Math.cos(a) * RING), height / 2 + (int) Math.round(Math.sin(a) * RING)};
    }

    private void choose() {
        if (hovered >= 0) JobClient.send(JobActionPayload.SELECT_SPELL, hovered, "");
        onClose();
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (JobKeys.SPELL_WHEEL.matches(event)) {
            choose();
            return true;
        }
        return super.keyReleased(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            choose();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void tick() {
        // also close if the key is no longer held (release can be missed while the screen opens)
        if (!testHold && !InputConstants.isKeyDown(JobKeys.SPELL_WHEEL.getKey().getValue()) && System.currentTimeMillis() - openedAt > 150) choose();
        if (!JobClient.holdingStaff()) onClose();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        int cx = width / 2, cy = height / 2;
        float open = Math.min(1f, (System.currentTimeMillis() - openedAt) / 120f);
        SpellLoadout l = loadout();
        JobData d = JobClient.data();

        double dx = mouseX - cx, dy = mouseY - cy;
        hovered = -1;
        if (Math.hypot(dx, dy) > 14) {
            double ang = Math.atan2(dy, dx) + Math.PI / 2; // 0 = up
            if (ang < 0) ang += Math.PI * 2;
            hovered = (int) Math.round(ang / (Math.PI / 2)) % 4;
        }

        Ui.disc(g, cx, cy, (int) (RING + 30 * open), 0x70000000);
        Ui.circle(g, cx, cy, (int) ((RING + 30) * open), 0x60B09CFF, 1);
        Ui.circle(g, cx, cy, 16, 0xA0B09CFF, 1);
        // pointer
        if (hovered >= 0) {
            double len = Math.min(RING - 18, Math.hypot(dx, dy));
            for (int i = 4; i < len; i += 3) {
                int px = cx + (int) (dx / Math.hypot(dx, dy) * i), py = cy + (int) (dy / Math.hypot(dx, dy) * i);
                g.fill(px - 1, py - 1, px + 1, py + 1, 0xC0E0D8FF);
            }
        }
        for (int i = 0; i < SpellLoadout.SLOTS; i++) {
            int[] p = slotPos(i);
            int x = cx + (int) ((p[0] - cx) * open), y = cy + (int) ((p[1] - cy) * open);
            boolean hov = i == hovered;
            int size = hov ? 30 : 24;
            SkillDef def = JobDefs.skill(l.spell(i));
            Ui.disc(g, x, y, size / 2 + 6, hov ? 0xC0B09CFF : 0x90202430);
            Ui.skillIcon(g, font, x - size / 2, y - size / 2, size, def, def != null && !d.knows(def.id()));
            if (l.selected() == i) Ui.circle(g, x, y, size / 2 + 8, 0xFFE8B33A, 1);
            Component label = def == null ? Component.translatable("rpgmod.wheel.empty") : Ui.skillName(def.id());
            int ly = i == 0 ? y - size / 2 - 18 : y + size / 2 + 10;
            g.centeredText(font, label, x, ly, hov ? 0xFFFFFFFF : 0xFFB8B0D8);
            if (hov && def != null) {
                long ready = d.cooldownEnd(def.id()) - minecraft.level.getGameTime();
                Component info = ready > 0 ? Component.translatable("rpgmod.hud.cooldown", String.format("%.1f", ready / 20.0))
                        : Component.translatable("rpgmod.jobscreen.mp", String.valueOf(Math.round(def.mana())));
                g.centeredText(font, info, cx, cy - 4, ready > 0 ? Ui.BAD : Ui.MANA);
            }
        }
        if (hovered < 0) g.centeredText(font, Component.translatable("rpgmod.wheel.hint"), cx, cy - 4, 0xFFB8B0D8);
    }
}
