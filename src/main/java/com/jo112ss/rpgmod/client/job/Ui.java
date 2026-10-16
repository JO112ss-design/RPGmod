package com.jo112ss.rpgmod.client.job;

import com.jo112ss.rpgmod.job.core.SkillDef;
import com.jo112ss.rpgmod.job.core.SkillKind;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Shared drawing kit for the job screens and HUD. Style: dark slate panels with a thin bright edge
 * and the job's colour as accent; hexes and icons are drawn from fills so no textures are needed.
 */
public final class Ui {
    private Ui() {}

    public static final int BG = 0xF0181A22;
    public static final int BG_INNER = 0xFF101218;
    public static final int EDGE = 0xFF3A4152;
    public static final int EDGE_LIGHT = 0xFF5D6780;
    public static final int TEXT = 0xFFE8ECF4;
    public static final int DIM = 0xFF8D96AA;
    public static final int GOLD = 0xFFE8B33A;
    public static final int MANA = 0xFF4F8DFF;
    public static final int EXP = 0xFF7CDB5A;
    public static final int BAD = 0xFFE05555;

    public static int argb(int rgb) {
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    public static int alpha(int rgb, int a) {
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    public static int mix(int a, int b, float t) {
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return 0xFF000000 | ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    /** Panel with a soft drop shadow, dark body and a light top edge. */
    public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, int accent) {
        g.fill(x + 3, y + 3, x + w + 3, y + h + 3, 0x60000000);
        g.fill(x, y, x + w, y + h, EDGE);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, BG);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, argb(accent));
        g.fillGradient(x + 1, y + 2, x + w - 1, y + 14, alpha(accent, 0x50), alpha(accent, 0x00));
    }

    public static void inset(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, EDGE);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, BG_INNER);
    }

    /** Horizontal bar with a label inside. */
    public static void bar(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, float frac, int color, String label) {
        inset(g, x, y, w, h);
        int fw = Math.round((w - 2) * Math.max(0, Math.min(1, frac)));
        if (fw > 0) g.fillGradient(x + 1, y + 1, x + 1 + fw, y + h - 1, mix(color, 0xFFFFFFFF, 0.25f), argb(color));
        if (label != null && h >= 9) g.centeredText(font, Component.literal(label), x + w / 2, y + (h - 8) / 2, TEXT);
    }

    /** Pointy-top hexagon filled with horizontal lines. */
    public static void hex(GuiGraphicsExtractor g, int cx, int cy, int r, int color) {
        double hw = Math.sqrt(3) / 2 * r;
        for (int dy = -r; dy <= r; dy++) {
            double w = Math.abs(dy) <= r / 2.0 ? hw : hw * (r - Math.abs(dy)) / (r / 2.0);
            int iw = (int) Math.round(w);
            if (iw > 0) g.fill(cx - iw, cy + dy, cx + iw, cy + dy + 1, color);
        }
    }

    /** Hexagon outline (outer colour) around a filled body. */
    public static void hexFramed(GuiGraphicsExtractor g, int cx, int cy, int r, int edge, int body) {
        hex(g, cx, cy, r, edge);
        hex(g, cx, cy, r - 2, body);
    }

    /** Circle outline made of dots (used by the spell wheel and emblems). */
    public static void circle(GuiGraphicsExtractor g, int cx, int cy, int r, int color, int thickness) {
        int steps = Math.max(24, r * 6);
        for (int i = 0; i < steps; i++) {
            double a = Math.PI * 2 * i / steps;
            int x = cx + (int) Math.round(Math.cos(a) * r), y = cy + (int) Math.round(Math.sin(a) * r);
            g.fill(x, y, x + thickness, y + thickness, color);
        }
    }

    public static void disc(GuiGraphicsExtractor g, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int w = (int) Math.round(Math.sqrt(r * r - dy * dy));
            g.fill(cx - w, cy + dy, cx + w, cy + dy + 1, color);
        }
    }

    /** Glyph drawn at an integer scale, centred on (cx, cy). */
    public static void glyph(GuiGraphicsExtractor g, Font font, String text, int cx, int cy, float scale, int color) {
        g.pose().pushMatrix();
        g.pose().translate(cx, cy);
        g.pose().scale(scale, scale);
        int w = font.width(text);
        g.text(font, text, -w / 2, -4, color, true);
        g.pose().popMatrix();
    }

    /**
     * A skill icon: a bevelled square in the skill's colour with its glyph. Ultimates get a gold frame,
     * spells a violet one. {@code locked} greys it out.
     */
    public static void skillIcon(GuiGraphicsExtractor g, Font font, int x, int y, int size, SkillDef def, boolean locked) {
        if (def == null) {
            inset(g, x, y, size, size);
            return;
        }
        int base = locked ? 0x40444C : def.color();
        int frame = def.kind() == SkillKind.ULTIMATE ? 0xE8B33A : def.kind() == SkillKind.SPELL ? 0xB09CFF : 0x2A2F3A;
        g.fill(x, y, x + size, y + size, argb(frame));
        g.fillGradient(x + 1, y + 1, x + size - 1, y + size - 1, mix(argb(base), 0xFFFFFFFF, 0.25f), mix(argb(base), 0xFF000000, 0.45f));
        g.fill(x + 1, y + 1, x + size - 1, y + 2, alpha(0xFFFFFF, 0x60));
        glyph(g, font, def.glyph(), x + size / 2, y + size / 2, Math.max(1f, size / 16f), locked ? 0xFF8A8A8A : TEXT);
    }

    public static Component skillName(String id) {
        return Component.translatable("rpgmod.skill." + id);
    }

    public static Component skillDesc(String id) {
        return Component.translatable("rpgmod.skill." + id + ".desc");
    }
}
