package com.jo112ss.rpgmod.client.hud;

import com.jo112ss.rpgmod.client.job.Ui;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

/**
 * Replaces the row of hearts with an RPG style HP bar in the same place: the current health over
 * a fading "damage taken" segment, absorption as a gold extension, the numbers in the middle, and
 * colours for poison, wither and freezing. Flashes when hit.
 */
public final class HpBar {
    private HpBar() {}

    private static final int W = 81, H = 9;
    private static float lag = -1;
    private static float lastHp = -1;
    private static long lastHit, lastFrame;

    public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.getCameraEntity() instanceof Player p)) return;
        int x = g.guiWidth() / 2 - 91;
        int y = g.guiHeight() - mc.gui.hud.leftHeight;
        mc.gui.hud.leftHeight += H + 1; // the armour row and the others go above, like after one row of hearts

        long now = System.currentTimeMillis();
        float dt = lastFrame == 0 ? 0 : Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        float hp = Math.max(0, p.getHealth()), max = Math.max(1, p.getMaxHealth()), abs = Math.max(0, p.getAbsorptionAmount());
        if (lastHp >= 0 && hp < lastHp - 0.01f) lastHit = now;
        lastHp = hp;
        if (lag < hp) lag = hp;
        else if (now - lastHit > 400) lag = Math.max(hp, lag - max * dt * 0.7f);

        float total = max + abs; // with absorption the bar is shared by both
        int fill = Math.round(W * Math.min(1, hp / total));
        int lagW = Math.round(W * Math.min(1, lag / total));
        int absW = Math.round(W * Math.min(1, (hp + abs) / total)) - fill;

        int top, bottom;
        if (p.hasEffect(MobEffects.WITHER)) {
            top = 0xFF6B6B6B; bottom = 0xFF2E2E2E;
        } else if (p.hasEffect(MobEffects.POISON)) {
            top = 0xFFA8D84A; bottom = 0xFF5E8A1E;
        } else if (p.isFullyFrozen()) {
            top = 0xFFB8E8FF; bottom = 0xFF6AAFD8;
        } else if (mc.level != null && mc.level.getLevelData().isHardcore()) {
            top = 0xFFE0453A; bottom = 0xFF8A1A14;
        } else {
            top = 0xFFF0544A; bottom = 0xFFB0221C;
        }

        // frame and background
        g.fill(x - 1, y - 1, x + W + 1, y + H + 1, 0xE0000000);
        g.fill(x, y, x + W, y + H, 0xFF2A1416);
        // what was just lost, fading out
        if (lagW > fill) g.fill(x + fill, y, x + lagW, y + H, 0xFFF4E6C8);
        // health: lighter top half, darker bottom half, a highlight line
        if (fill > 0) {
            g.fill(x, y, x + fill, y + H / 2, top);
            g.fill(x, y + H / 2, x + fill, y + H, bottom);
            g.fill(x, y, x + fill, y + 1, Ui.mix(top, 0xFFFFFFFF, 0.45f));
        }
        // regeneration: a shine running along the bar
        if (p.hasEffect(MobEffects.REGENERATION) && fill > 4) {
            int sx = (int) ((now / 12) % (fill + 12)) - 6;
            g.fill(x + Math.max(0, sx), y + 1, x + Math.min(fill, sx + 4), y + H - 1, 0x60FFFFFF);
        }
        // absorption on top, in gold
        if (absW > 0) {
            g.fill(x + fill, y, x + fill + absW, y + H, 0xFFE8B83A);
            g.fill(x + fill, y, x + fill + absW, y + 1, 0xFFFFE89A);
        }
        // quarter marks
        for (int i = 1; i < 4; i++) g.fill(x + W * i / 4, y + H - 2, x + W * i / 4 + 1, y + H, 0x50000000);
        // flash when hit
        long since = now - lastHit;
        if (since < 180) g.fill(x, y, x + W, y + H, ((int) (0x70 * (1 - since / 180f)) << 24) | 0xFFFFFF);

        String text = fmt(hp) + " / " + fmt(max) + (abs >= 0.5f ? " +" + fmt(abs) : "");
        g.pose().pushMatrix();
        g.pose().translate(x + W / 2f, y + 1.5f);
        g.pose().scale(0.75f, 0.75f);
        g.text(mc.font, text, -mc.font.width(text) / 2, 0, 0xFFFFFFFF, true);
        g.pose().popMatrix();
    }

    private static String fmt(float v) {
        return v >= 10 || Math.abs(v - Math.round(v)) < 0.05f ? Integer.toString(Math.round(v)) : String.format("%.1f", v);
    }
}
