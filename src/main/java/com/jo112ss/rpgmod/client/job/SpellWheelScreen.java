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
 * staff's active spell. Four slots around a ring, like a radial quick-select. The spellmaster gets
 * two rings side by side: the left one is the left hand's spells, the right one the right hand's.
 */
public class SpellWheelScreen extends Screen {
    private static final int RING = 64;
    /** Dev test only: keep the wheel open without the key held. */
    public static boolean testHold;
    /** Hovered slot (0-3 right hand, 4-7 left hand), or -1. */
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

    private boolean twoHands() {
        return JobClient.data().isAdvancedAs("spellmaster");
    }

    private SpellLoadout loadout() {
        if (!JobClient.holdingStaff()) return SpellLoadout.EMPTY;
        ItemStack s = minecraft.player.getMainHandItem();
        return s.getOrDefault(ModRegistries.SPELL_LOADOUT.get(), SpellLoadout.EMPTY);
    }

    /** Centre of a hand's ring: one ring in the middle, or left hand left / right hand right. */
    private int ringX(boolean left) {
        if (!twoHands()) return width / 2;
        int gap = Math.min(110, width / 4);
        return width / 2 + (left ? -gap : gap);
    }

    /** Slot positions: top, right, bottom, left of its ring. */
    private int[] slotPos(int slot) {
        int i = slot % SpellLoadout.BASE_SLOTS;
        double a = -Math.PI / 2 + i * Math.PI / 2;
        int cx = ringX(SpellLoadout.isLeft(slot));
        return new int[] {cx + (int) Math.round(Math.cos(a) * RING), height / 2 + (int) Math.round(Math.sin(a) * RING)};
    }

    private void choose() {
        if (hovered >= 0) {
            JobClient.send(SpellLoadout.isLeft(hovered) ? JobActionPayload.SELECT_LEFT_SPELL : JobActionPayload.SELECT_SPELL, hovered, "");
        }
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
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT || event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
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
        float open = Math.min(1f, (System.currentTimeMillis() - openedAt) / 120f);
        SpellLoadout l = loadout();
        JobData d = JobClient.data();
        boolean two = twoHands();
        int cy = height / 2;

        // which ring the mouse is over (the nearer one), then which direction
        boolean leftRing = two && mouseX < width / 2;
        int cx = ringX(leftRing);
        double dx = mouseX - cx, dy = mouseY - cy;
        hovered = -1;
        if (Math.hypot(dx, dy) > 14) {
            double ang = Math.atan2(dy, dx) + Math.PI / 2; // 0 = up
            if (ang < 0) ang += Math.PI * 2;
            hovered = (int) Math.round(ang / (Math.PI / 2)) % 4 + (leftRing ? SpellLoadout.BASE_SLOTS : 0);
        }

        for (int hand = 0; hand < (two ? 2 : 1); hand++) {
            boolean left = hand == 1;
            int rx = ringX(left);
            int tint = left ? 0x607FD8FF : 0x60B09CFF;
            Ui.disc(g, rx, cy, (int) (RING + 30 * open), 0x70000000);
            Ui.circle(g, rx, cy, (int) ((RING + 30) * open), tint, 1);
            Ui.circle(g, rx, cy, 16, (tint & 0xFFFFFF) | 0xA0000000, 1);
            if (two) {
                Component name = Component.translatable(left ? "rpgmod.wheel.left_hand" : "rpgmod.wheel.right_hand",
                        (left ? JobKeys.LEFT_SPELL : net.minecraft.client.Minecraft.getInstance().options.keyUse).getTranslatedKeyMessage());
                g.centeredText(font, name, rx, cy - RING - 44, left ? 0xFF7FD8FF : 0xFFB09CFF);
            }
        }
        // pointer
        if (hovered >= 0) {
            double len = Math.min(RING - 18, Math.hypot(dx, dy));
            for (int i = 4; i < len; i += 3) {
                int px = cx + (int) (dx / Math.hypot(dx, dy) * i), py = cy + (int) (dy / Math.hypot(dx, dy) * i);
                g.fill(px - 1, py - 1, px + 1, py + 1, 0xC0E0D8FF);
            }
        }
        for (int slot = 0; slot < d.spellSlots(); slot++) {
            boolean left = SpellLoadout.isLeft(slot);
            int rx = ringX(left);
            int[] p = slotPos(slot);
            int x = rx + (int) ((p[0] - rx) * open), y = cy + (int) ((p[1] - cy) * open);
            boolean hov = slot == hovered;
            int size = hov ? 30 : 24;
            SkillDef def = JobDefs.skill(l.spell(slot));
            Ui.disc(g, x, y, size / 2 + 6, hov ? (left ? 0xC07FD8FF : 0xC0B09CFF) : 0x90202430);
            Ui.skillIcon(g, font, x - size / 2, y - size / 2, size, def, def != null && !d.knows(def.id()));
            boolean chosen = left ? l.selectedLeft() == slot : l.selected() == slot;
            if (chosen) Ui.circle(g, x, y, size / 2 + 8, left ? 0xFF7FD8FF : 0xFFE8B33A, 1);
            Component label = def == null ? Component.translatable("rpgmod.wheel.empty") : Ui.skillName(def.id());
            g.centeredText(font, label, x, y + size / 2 + 9, hov ? 0xFFFFFFFF : 0xFFB8B0D8);
            if (hov && def != null) {
                long ready = d.cooldownEnd(def.id()) - minecraft.level.getGameTime();
                Component info = ready > 0 ? Component.translatable("rpgmod.hud.cooldown", String.format("%.1f", ready / 20.0))
                        : Component.translatable("rpgmod.jobscreen.mp", String.valueOf(Math.round(def.mana())));
                g.centeredText(font, info, rx, cy - 4, ready > 0 ? Ui.BAD : Ui.MANA);
            }
        }
        if (hovered < 0) {
            if (two) g.centeredText(font, Component.translatable("rpgmod.wheel.hint_hands"), width / 2, cy + RING + 40, 0xFFB8B0D8);
            else g.centeredText(font, Component.translatable("rpgmod.wheel.hint"), width / 2, cy - 4, 0xFFB8B0D8);
        }
    }
}
