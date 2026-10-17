package com.jo112ss.rpgmod.client.mob;

import com.jo112ss.rpgmod.client.job.Ui;
import com.jo112ss.rpgmod.mob.DamageNumbers;
import com.jo112ss.rpgmod.registry.ModRegistries;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * World-anchored HUD: a level badge, name and health bar over nearby monsters, and floating damage
 * numbers. Drawn as part of the GUI (projected from world positions), so it uses the mod's crisp UI
 * style instead of in-world text, and never hides behind fog or blocks the view of the monster.
 */
public final class MobOverlay {
    private MobOverlay() {}

    private static final double PLATE_RANGE = 18, FOCUS_RANGE = 32;
    private static final Random RND = new Random();

    // ------------------------------------------------------------------ damage numbers

    private record Num(Vec3 at, float amount, int kind, long born, float dx, int entity) {}

    private static final List<Num> NUMS = new ArrayList<>();

    /** Called for the "dmg" effect from the server. */
    public static void addNumber(Vec3 at, float amount, int kind, int entity) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && entity == mc.player.getId() && mc.options.getCameraType().isFirstPerson()) return;
        if (NUMS.size() > 120) NUMS.remove(0);
        NUMS.add(new Num(at.add((RND.nextDouble() - 0.5) * 0.6, RND.nextDouble() * 0.3, (RND.nextDouble() - 0.5) * 0.6), amount, kind,
                System.currentTimeMillis(), (float) (RND.nextDouble() - 0.5), entity));
        if (entity >= 0) lastHit.put(entity, System.currentTimeMillis());
    }

    // ------------------------------------------------------------------ per-entity state

    /** Health shown by the bar's trailing "damage taken" segment, per entity id. */
    private static final Map<Integer, Float> lagHealth = new HashMap<>();
    private static final Map<Integer, Long> lastHit = new HashMap<>();
    private static final Map<Integer, Boolean> sight = new HashMap<>();
    private static long sightTick = -1;
    private static long lastFrame = System.currentTimeMillis();

    public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        Camera cam = mc.gameRenderer.mainCamera();
        if (cam == null || !cam.isInitialized()) return;
        float partial = delta.getGameTimeDeltaPartialTick(true);
        Matrix4f proj = cam.getViewRotationProjectionMatrix(new Matrix4f());
        Vec3 eye = cam.position();
        int sw = g.guiWidth(), sh = g.guiHeight();
        long now = System.currentTimeMillis();
        float dt = Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        long tick = mc.level.getGameTime();
        boolean recheck = tick != sightTick;
        sightTick = tick;

        // ---- plates: collect, then draw far to near
        Entity crosshair = mc.crosshairPickEntity;
        List<Object[]> plates = new ArrayList<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity le) || e instanceof EnderDragon || e instanceof com.jo112ss.rpgmod.mob.boss.KingSlime || !le.isAlive() || le.isInvisible()) continue;
            int lv = e.getData(ModRegistries.MOB_LEVEL);
            if (lv <= 0) continue;
            Vec3 pos = e.getPosition(partial);
            double dist = pos.distanceTo(eye);
            Long hit = lastHit.get(e.getId());
            boolean focus = e == crosshair || (hit != null && now - hit < 5000);
            if (dist > (focus ? FOCUS_RANGE : PLATE_RANGE)) continue;
            Boolean vis = sight.get(e.getId());
            if (vis == null || recheck && (e.getId() + tick) % 4 == 0) {
                vis = mc.level.clip(new ClipContext(eye, e.getEyePosition(partial), ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player))
                        .getType() == HitResult.Type.MISS;
                sight.put(e.getId(), vis);
            }
            if (!vis && e != crosshair) continue;
            Vec3 top = pos.add(0, e.getBbHeight() + 0.5, 0);
            float[] s = project(proj, top.subtract(eye), sw, sh);
            if (s == null) continue;
            plates.add(new Object[] {le, s, dist, focus, lv});
        }
        // priority: the monster under the crosshair, then recently hit ones, then the nearest. A plate
        // that would overlap a more important one is dimmed so the important one stays readable.
        plates.sort((a, b) -> {
            int pa = a[0] == crosshair ? 0 : (boolean) a[3] ? 1 : 2, pb = b[0] == crosshair ? 0 : (boolean) b[3] ? 1 : 2;
            return pa != pb ? Integer.compare(pa, pb) : Double.compare((double) a[2], (double) b[2]);
        });
        List<float[]> placed = new ArrayList<>();
        List<Object[]> draw = new ArrayList<>();
        for (Object[] p : plates) {
            float[] s = (float[]) p[1];
            double dist = (double) p[2];
            boolean focus = (boolean) p[3];
            float scale = (float) Math.max(0.55, Math.min(1.0, 1.15 - dist / 30));
            float hw = (focus ? 48 : 30) * scale, top = (focus ? 26 : 16) * scale;
            float[] rect = {s[0] - hw, s[1] - top, s[0] + hw, s[1] + 2 * scale};
            boolean covered = false;
            for (float[] r : placed) {
                if (rect[0] < r[2] && rect[2] > r[0] && rect[1] < r[3] && rect[3] > r[1]) {
                    covered = true;
                    break;
                }
            }
            placed.add(rect);
            float fade = focus ? 1f : (float) Math.min(1, (PLATE_RANGE - dist) / 4);
            if (covered) fade *= 0.22f;
            draw.add(new Object[] {p[0], s, scale, fade, focus, p[4]});
        }
        for (int i = draw.size() - 1; i >= 0; i--) { // least important first, so the important ones end on top
            Object[] d = draw.get(i);
            float[] s = (float[]) d[1];
            plate(g, mc.font, (LivingEntity) d[0], (int) d[5], s[0], s[1], (float) d[2], (float) d[3], (boolean) d[4], dt);
        }

        // ---- damage numbers
        Iterator<Num> it = NUMS.iterator();
        while (it.hasNext()) {
            Num n = it.next();
            float life = n.kind == DamageNumbers.CRIT ? 1.3f : 1.0f;
            float t = (now - n.born) / 1000f / life;
            if (t >= 1) {
                it.remove();
                continue;
            }
            float rise = 1 - (1 - t) * (1 - t);
            Vec3 at = n.at.add(0, rise * 0.7, 0);
            float[] s = project(proj, at.subtract(eye), sw, sh);
            if (s == null) continue;
            double dist = at.distanceTo(eye);
            float pop = t < 0.1f ? 1 + (1 - t / 0.1f) * 0.5f : 1;
            float base = (float) Math.max(0.6, Math.min(1.15, 6 / Math.max(1, dist)));
            if (n.kind == DamageNumbers.CRIT) base *= 1.3f;
            float alpha = t > 0.7f ? (1 - t) / 0.3f : 1;
            number(g, mc.font, n, s[0] + n.dx * 24 * rise, s[1], base * pop, alpha);
        }

        // forget entities that are gone
        if (tick % 100 == 0) {
            lagHealth.keySet().removeIf(id -> mc.level.getEntity(id) == null);
            sight.keySet().removeIf(id -> mc.level.getEntity(id) == null);
            lastHit.values().removeIf(t -> now - t > 10000);
        }
    }

    /** World offset from the camera → GUI coordinates, or null if behind the camera. */
    private static float[] project(Matrix4f proj, Vec3 rel, int sw, int sh) {
        Vector4f v = new Vector4f((float) rel.x, (float) rel.y, (float) rel.z, 1).mul(proj);
        if (v.w <= 0.05f) return null;
        float nx = v.x / v.w, ny = v.y / v.w;
        if (Math.abs(nx) > 1.2f || Math.abs(ny) > 1.2f) return null;
        return new float[] {(nx * 0.5f + 0.5f) * sw, (0.5f - ny * 0.5f) * sh};
    }

    /** Level colour: green while easy, through yellow, orange and red to purple at the end. */
    public static int tierColor(int lv) {
        if (lv <= 10) return 0x8CE07A;
        if (lv <= 25) return 0xE8D35A;
        if (lv <= 40) return 0xF09A3E;
        if (lv <= 60) return 0xE85A4F;
        return 0xC77DFF;
    }

    private static int a(int argb, float alpha) {
        int base = argb >>> 24;
        return ((int) (base * Math.max(0, Math.min(1, alpha))) << 24) | (argb & 0xFFFFFF);
    }

    private static void plate(GuiGraphicsExtractor g, Font font, LivingEntity e, int lv, float x, float y, float scale, float fade, boolean full, float dt) {
        float hp = e.getHealth(), max = Math.max(1, e.getMaxHealth());
        float frac = Math.max(0, Math.min(1, hp / max));
        // trailing segment: drops towards the real value after a short hold
        float lag = lagHealth.getOrDefault(e.getId(), hp);
        Long hit = lastHit.get(e.getId());
        if (lag < hp) lag = hp;
        else if (hit == null || System.currentTimeMillis() - hit > 350) lag = Math.max(hp, lag - max * dt * 0.8f);
        lagHealth.put(e.getId(), lag);
        float lagFrac = Math.max(frac, Math.min(1, lag / max));

        String lvText = "Lv" + lv;
        Component name = e.getDisplayName();
        int badgeW = font.width(lvText) + 6;
        int nameW = full ? font.width(name) : 0;
        int barW = Math.max(48, full ? badgeW + 3 + nameW : 48);
        int w = full ? Math.max(barW, badgeW + 3 + nameW) : Math.max(barW, badgeW + 2);
        int tier = tierColor(lv);

        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        int left = -w / 2;
        int top = full ? -24 : -15;
        // badge + name
        g.fill(left, top, left + badgeW, top + 10, a(Ui.alpha(tier, 0xE0), fade));
        g.fill(left, top + 10, left + badgeW, top + 11, a(Ui.alpha(Ui.mix(Ui.argb(tier), 0xFF000000, 0.5f), 0xE0), fade));
        g.text(font, lvText, left + 3, top + 1, a(0xFF101218, fade), false);
        if (full) {
            g.fill(left + badgeW, top, left + w, top + 11, a(0xB0101218, fade));
            g.text(font, name, left + badgeW + 3, top + 1, a(Ui.TEXT, fade), true);
        }
        // health bar: frame, lag segment, fill with a highlight line
        int by = top + 13, bh = full ? 5 : 4;
        g.fill(left - 1, by - 1, left + w + 1, by + bh + 1, a(0xD0000000, fade));
        g.fill(left, by, left + w, by + bh, a(0xFF2A1416, fade));
        g.fill(left, by, left + Math.round(w * lagFrac), by + bh, a(0xFFF4E6C8, fade));
        int hc = frac > 0.5f ? Ui.mix(0xFFE8D35A, 0xFF5FD65A, (frac - 0.5f) * 2) : Ui.mix(0xFFE04848, 0xFFE8D35A, frac * 2);
        int fw = Math.round(w * frac);
        g.fill(left, by, left + fw, by + bh, a(hc, fade));
        g.fill(left, by, left + fw, by + 1, a(Ui.mix(hc, 0xFFFFFFFF, 0.45f), fade));
        if (full) {
            String hpText = fmt(hp) + " / " + fmt(max);
            g.pose().pushMatrix();
            g.pose().translate(0, by + bh + 2);
            g.pose().scale(0.75f, 0.75f);
            g.text(font, hpText, -font.width(hpText) / 2, 0, a(0xFFDDE2EA, fade), true);
            g.pose().popMatrix();
        }
        g.pose().popMatrix();
    }

    private static String fmt(float v) {
        return v >= 10 ? Integer.toString(Math.round(v)) : String.format("%.1f", v);
    }

    private static final int[] NUM_COLOR = {0xFFFFFFFF, 0xFFFFC83A, 0xFFC08CFF, 0xFFFF9442, 0xFF8CE05A, 0xFFFF5A5A, 0xFF6CFF8C, 0xFFFFB070};

    private static void number(GuiGraphicsExtractor g, Font font, Num n, float x, float y, float scale, float alpha) {
        String text = n.kind == DamageNumbers.HEAL ? "+" + fmt(n.amount) : fmt(n.amount);
        if (n.kind == DamageNumbers.CRIT) text = text + "!";
        int color = NUM_COLOR[Math.max(0, Math.min(NUM_COLOR.length - 1, n.kind))];
        int w = font.width(text);
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        int ox = -w / 2, oy = -4;
        g.text(font, text, ox, oy, a(color, alpha), true);
        g.pose().popMatrix();
    }
}
