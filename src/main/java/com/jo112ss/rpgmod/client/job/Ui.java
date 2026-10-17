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

    /** Pointy-top hexagon, drawn as one polygon (two quads). */
    public static void hex(GuiGraphicsExtractor g, int cx, int cy, int r, int color) {
        if (r <= 0) return;
        float x = cx, y = cy + 0.5f, h = r + 0.5f, hw = (float) (Math.sqrt(3) / 2 * r), s = h / 2;
        ShapeState.submit(g, new float[] {
                // left half: top, upper-left, lower-left, bottom
                x, y - h, x - hw, y - s, x - hw, y + s, x, y + h,
                // right half: top, bottom, lower-right, upper-right
                x, y - h, x, y + h, x + hw, y + s, x + hw, y - s}, color);
    }

    /** Hexagon outline (outer colour) around a filled body. */
    public static void hexFramed(GuiGraphicsExtractor g, int cx, int cy, int r, int edge, int body) {
        hex(g, cx, cy, r, edge);
        hex(g, cx, cy, r - 2, body);
    }

    private static int segments(int r) {
        return Math.max(16, Math.min(96, r * 2)) & ~1; // even, for the disc's paired fan
    }

    /** Circle outline {@code thickness} pixels wide, drawn as one ring of quads. */
    public static void circle(GuiGraphicsExtractor g, int cx, int cy, int r, int color, int thickness) {
        if (r <= 0) return;
        int n = segments(r);
        float ox = cx + 0.5f, oy = cy + 0.5f, ri = r - thickness / 2f, ro = r + thickness / 2f;
        float[] q = new float[n * 8];
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            int k = i * 8;
            q[k] = ox + c0 * ri; q[k + 1] = oy + s0 * ri;
            q[k + 2] = ox + c0 * ro; q[k + 3] = oy + s0 * ro;
            q[k + 4] = ox + c1 * ro; q[k + 5] = oy + s1 * ro;
            q[k + 6] = ox + c1 * ri; q[k + 7] = oy + s1 * ri;
        }
        ShapeState.submit(g, q, color);
    }

    /** Filled circle, drawn as one fan of quads. */
    public static void disc(GuiGraphicsExtractor g, int cx, int cy, int r, int color) {
        if (r <= 0) return;
        int n = segments(r);
        float ox = cx, oy = cy + 0.5f;
        float[] q = new float[n / 2 * 8];
        for (int i = 0; i < n; i += 2) {
            int k = i / 2 * 8;
            q[k] = ox; q[k + 1] = oy;
            for (int j = 0; j < 3; j++) {
                double a = Math.PI * 2 * (i + j) / n;
                q[k + 2 + j * 2] = ox + (float) Math.cos(a) * r;
                q[k + 3 + j * 2] = oy + (float) Math.sin(a) * r;
            }
        }
        ShapeState.submit(g, q, color);
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
