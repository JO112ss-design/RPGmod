package com.jo112ss.rpgmod.client;

import com.jo112ss.rpgmod.network.ForgeActionPayload;
import com.jo112ss.rpgmod.network.ForgeSyncPayload;
import com.jo112ss.rpgmod.registry.ModRegistries;
import com.jo112ss.rpgmod.smithing.ForgeMenu;
import com.jo112ss.rpgmod.smithing.ForgeableWeapon;
import com.jo112ss.rpgmod.smithing.ForgeableWeapons;
import com.jo112ss.rpgmod.smithing.ForgedData;
import com.jo112ss.rpgmod.smithing.core.Judgement;
import com.jo112ss.rpgmod.smithing.core.Quality;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Smithing minigame GUI. Draws only what the server sent ({@link ClientForgeView}); every click is
 * forwarded to the server as input. Original layout: an ember-lit forge panel with a heat gauge,
 * the blade split into hammer zones with work gauges, and a timing bar below.
 */
public class ForgeScreen extends AbstractContainerScreen<ForgeMenu> {
    private static final int W = 256;
    private static final int H = 206;

    // palette (ARGB)
    private static final int C_BG = 0xFF27211D;
    private static final int C_BG_INNER = 0xFF1B1714;
    private static final int C_EDGE_LIGHT = 0xFF6E5D4F;
    private static final int C_EDGE_DARK = 0xFF0E0B09;
    private static final int C_EMBER = 0xFFE58A34;
    private static final int C_TEXT = 0xFFEFE5D6;
    private static final int C_DIM = 0xFFA4968A;
    private static final int C_UNDER = 0xFFE0A030;
    private static final int C_OK = 0xFF57C463;
    private static final int C_OVER = 0xFFD8443C;

    // layout (relative to leftPos/topPos)
    private static final int TEMP_X = 14, TEMP_Y = 26, TEMP_W = 10, TEMP_H = 104;
    private static final int SWORD_X = 48, SWORD_Y = 24, SWORD_W = 160, SWORD_H = 40;
    private static final int GAUGE_Y = 70, GAUGE_H = 46;
    private static final int BAR_X = 40, BAR_Y = 148, BAR_W = 176, BAR_H = 12;
    private static final int BTN_Y = 170;
    // select stage
    private static final int TYPE_X = 10, TYPE_Y = 24, TYPE_W = 38, TYPE_H = 22, TYPE_STEP = 40, TYPES_PER_ROW = 6;
    private static final int MAT_X = 10, MAT_Y = 78, MAT_W = 56, MAT_H = 18, MAT_STEP = 60;
    private static final int PREVIEW_Y = 102;

    /** Weapons on the first row, armor on the second. */
    private static int tabX(int i) {
        return TYPE_X + (i % TYPES_PER_ROW) * TYPE_STEP;
    }

    private static int tabY(int i) {
        return TYPE_Y + (i / TYPES_PER_ROW) * (TYPE_H + 4);
    }

    private int chosenType = 0;
    private int chosenMat = 1; // iron
    private int chosenWeapon = ForgeableWeapons.indexOf(ForgeableWeapons.TYPES.get(0), ForgeableWeapons.MATERIALS.get(1));
    private Button startBtn, coolBtn, finishBtn, againBtn, closeBtn, stopBtn;

    public ForgeScreen(ForgeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, W, H);
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos, y = topPos;
        startBtn = addRenderableWidget(Button.builder(Component.translatable("rpgmod.gui.start"),
                b -> send(ForgeActionPayload.START, chosenWeapon, 0, 0)).bounds(x + 68, y + BTN_Y, 120, 20).build());
        stopBtn = addRenderableWidget(Button.builder(Component.translatable("rpgmod.gui.stop"),
                b -> stopBar()).bounds(x + 98, y + BTN_Y, 60, 20).build());
        coolBtn = addRenderableWidget(Button.builder(Component.translatable("rpgmod.gui.cool"),
                b -> send(ForgeActionPayload.COOL, 0, 0, 0)).bounds(x + BAR_X, y + BTN_Y, 70, 20).build());
        finishBtn = addRenderableWidget(Button.builder(Component.translatable("rpgmod.gui.finish"),
                b -> send(ForgeActionPayload.FINISH, 0, 0, 0)).bounds(x + BAR_X + BAR_W - 70, y + BTN_Y, 70, 20).build());
        againBtn = addRenderableWidget(Button.builder(Component.translatable("rpgmod.gui.again"),
                b -> send(ForgeActionPayload.RESET, 0, 0, 0)).bounds(x + BAR_X, y + BTN_Y, 80, 20).build());
        closeBtn = addRenderableWidget(Button.builder(Component.translatable("rpgmod.gui.close"),
                b -> onClose()).bounds(x + BAR_X + BAR_W - 80, y + BTN_Y, 80, 20).build());
        refreshWidgets();
    }

    private void refreshWidgets() {
        ForgeSyncPayload v = ClientForgeView.get();
        int stage = v.stage();
        startBtn.visible = stage == ForgeSyncPayload.STAGE_SELECT;
        ForgeableWeapon w = ForgeableWeapons.byIndex(chosenWeapon);
        startBtn.active = w != null && hasMaterials(w);
        stopBtn.visible = stage == ForgeSyncPayload.STAGE_HEATING;
        stopBtn.active = ClientForgeView.canStop();
        coolBtn.visible = stage == ForgeSyncPayload.STAGE_WORKING;
        finishBtn.visible = stage == ForgeSyncPayload.STAGE_WORKING;
        againBtn.visible = stage == ForgeSyncPayload.STAGE_FINISHED;
        closeBtn.visible = stage == ForgeSyncPayload.STAGE_FINISHED;
    }

    // ------------------------------------------------------------------ input

    private static void send(int action, int arg, int token, long elapsed) {
        ClientPacketDistributor.sendToServer(new ForgeActionPayload(action, arg, token, elapsed));
    }

    private void stopBar() {
        ForgeSyncPayload v = ClientForgeView.get();
        if (!ClientForgeView.canStop()) return;
        int token = v.barToken();
        long elapsed = ClientForgeView.markStopped();
        if (v.stage() == ForgeSyncPayload.STAGE_HEATING) {
            send(ForgeActionPayload.STOP_HEAT, 0, token, elapsed);
        } else if (v.stage() == ForgeSyncPayload.STAGE_WORKING && v.selectedPart() >= 0) {
            send(ForgeActionPayload.STRIKE, v.selectedPart(), token, elapsed);
        }
    }

    private void selectPart(int part) {
        ForgeSyncPayload v = ClientForgeView.get();
        if (v.stage() != ForgeSyncPayload.STAGE_WORKING || part < 0 || part >= v.targets().length) return;
        if (part == v.selectedPart() && v.barRunning()) return;
        send(ForgeActionPayload.SELECT_PART, part, 0, 0);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            double mx = event.x() - leftPos, my = event.y() - topPos;
            ForgeSyncPayload v = ClientForgeView.get();
            switch (v.stage()) {
                case ForgeSyncPayload.STAGE_SELECT -> {
                    for (int i = 0; i < ForgeableWeapons.TYPES.size(); i++) {
                        int tx = tabX(i), ty = tabY(i);
                        if (mx >= tx && mx < tx + TYPE_W && my >= ty && my < ty + TYPE_H) {
                            chosenType = i;
                            updateChoice();
                            return true;
                        }
                    }
                    for (int i = 0; i < ForgeableWeapons.MATERIALS.size(); i++) {
                        int tx = MAT_X + i * MAT_STEP;
                        if (mx >= tx && mx < tx + MAT_W && my >= MAT_Y && my < MAT_Y + MAT_H) {
                            chosenMat = i;
                            updateChoice();
                            return true;
                        }
                    }
                }
                case ForgeSyncPayload.STAGE_HEATING -> {
                    if (inBar(mx, my)) {
                        stopBar();
                        return true;
                    }
                }
                case ForgeSyncPayload.STAGE_WORKING -> {
                    int part = partAt(ForgeableWeapons.byIndex(v.weapon()), mx, my, v.targets().length);
                    if (part >= 0) {
                        selectPart(part);
                        return true;
                    }
                    if (inBar(mx, my)) {
                        stopBar();
                        return true;
                    }
                }
                default -> { }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        ForgeSyncPayload v = ClientForgeView.get();
        if (key == InputConstants.KEY_SPACE) {
            if (v.stage() == ForgeSyncPayload.STAGE_HEATING || v.stage() == ForgeSyncPayload.STAGE_WORKING) {
                stopBar();
                return true;
            }
        }
        if (v.stage() == ForgeSyncPayload.STAGE_WORKING && key >= InputConstants.KEY_1 && key <= InputConstants.KEY_9) { // 1..9 select part
            int part = key - InputConstants.KEY_1;
            if (part < v.targets().length) {
                selectPart(part);
                return true;
            }
        }
        return super.keyPressed(event);
    }

    private void updateChoice() {
        chosenWeapon = ForgeableWeapons.indexOf(ForgeableWeapons.TYPES.get(chosenType), ForgeableWeapons.MATERIALS.get(chosenMat));
    }

    private static boolean inBar(double mx, double my) {
        return mx >= BAR_X - 4 && mx < BAR_X + BAR_W + 4 && my >= BAR_Y - 8 && my < BAR_Y + BAR_H + 6;
    }

    /** The hammered span of the weapon image is split evenly into parts; the gauge column below belongs to the same part. */
    private static int partLeft(ForgeableWeapon w, int i, int parts) {
        return SWORD_X + w.workX() + Math.round(i * ((float) w.workW() / parts));
    }

    private static int partWidth(ForgeableWeapon w, int parts) {
        return Math.round((float) w.workW() / parts) - 1;
    }

    private static int partAt(ForgeableWeapon w, double mx, double my, int parts) {
        if (w == null || my < SWORD_Y || my >= GAUGE_Y + GAUGE_H + 12) return -1;
        for (int i = 0; i < parts; i++) {
            int l = partLeft(w, i, parts);
            if (mx >= l && mx < l + partWidth(w, parts)) return i;
        }
        return -1;
    }

    // ------------------------------------------------------------------ rendering

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym) {
        // everything is drawn in extractBackground
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        refreshWidgets();
        super.extractRenderState(graphics, mouseX, mouseY, a);
        ForgeSyncPayload v = ClientForgeView.get();
        if (v.stage() == ForgeSyncPayload.STAGE_WORKING || v.stage() == ForgeSyncPayload.STAGE_HEATING) {
            ForgeableWeapon w = ForgeableWeapons.byIndex(v.weapon());
            int part = partAt(w, mouseX - leftPos, mouseY - topPos, v.targets().length);
            if (part >= 0 && w != null) {
                graphics.setTooltipForNextFrame(font, Component.translatable(w.partKeys().get(part))
                        .append(String.format("  %.1f / %.0f", v.values()[part], v.targets()[part])), mouseX, mouseY);
            }
        } else if (v.stage() == ForgeSyncPayload.STAGE_SELECT) {
            ForgeableWeapon w = ForgeableWeapons.byIndex(chosenWeapon);
            int rx = mouseX - leftPos, ry = mouseY - topPos;
            if (w != null && rx >= SWORD_X && rx < SWORD_X + SWORD_W && ry >= PREVIEW_Y && ry < PREVIEW_Y + SWORD_H) {
                graphics.setTooltipForNextFrame(font, new ItemStack(w.result().get()), mouseX, mouseY);
            }
        } else if (v.stage() == ForgeSyncPayload.STAGE_FINISHED && !v.result().isEmpty()) {
            if (mouseX >= leftPos + 16 && mouseX < leftPos + 40 && mouseY >= topPos + 26 && mouseY < topPos + 50) {
                graphics.setTooltipForNextFrame(font, v.result(), mouseX, mouseY);
            }
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        int x = leftPos, y = topPos;
        drawPanel(graphics, x, y);
        ForgeSyncPayload v = ClientForgeView.get();
        Component heading = switch (v.stage()) {
            case ForgeSyncPayload.STAGE_SELECT -> Component.translatable("rpgmod.gui.select_weapon");
            case ForgeSyncPayload.STAGE_HEATING -> Component.translatable("rpgmod.gui.heating");
            case ForgeSyncPayload.STAGE_WORKING -> Component.translatable(v.selectedPart() < 0 ? "rpgmod.gui.select_part" : "rpgmod.gui.strike_hint");
            default -> Component.translatable(v.endReason() == 2 ? "rpgmod.gui.result_cold" : "rpgmod.gui.result");
        };
        graphics.text(font, heading, x + 10, y + 8, C_EMBER, false);
        switch (v.stage()) {
            case ForgeSyncPayload.STAGE_SELECT -> drawSelect(graphics, x, y, mouseX, mouseY);
            case ForgeSyncPayload.STAGE_HEATING, ForgeSyncPayload.STAGE_WORKING -> drawForge(graphics, x, y, v);
            default -> drawResult(graphics, x, y, v);
        }
    }

    private void drawPanel(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x, y, x + W, y + H, C_EDGE_DARK);
        g.fill(x + 1, y + 1, x + W - 1, y + H - 1, C_EDGE_LIGHT);
        g.fill(x + 2, y + 2, x + W - 2, y + H - 2, C_BG);
        g.fill(x + 2, y + 19, x + W - 2, y + 20, C_EDGE_DARK);
        g.fill(x + 2, y + 20, x + W - 2, y + 21, 0xFF4A3B30);
        // ember strip along the bottom edge
        g.fillGradient(x + 2, y + H - 6, x + W - 2, y + H - 2, 0x00E58A34, 0x90E58A34);
    }

    private static void inset(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int fill) {
        g.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, C_EDGE_DARK);
        g.fill(x0, y0, x1 + 1, y1 + 1, C_EDGE_LIGHT);
        g.fill(x0, y0, x1, y1, fill);
    }

    // ---- select stage

    private void drawSelect(GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY) {
        int rx = mouseX - x, ry = mouseY - y;
        for (int i = 0; i < ForgeableWeapons.TYPES.size(); i++) {
            int tx = tabX(i), ty = tabY(i);
            boolean chosen = i == chosenType;
            boolean hover = rx >= tx && rx < tx + TYPE_W && ry >= ty && ry < ty + TYPE_H;
            inset(g, x + tx, y + ty, x + tx + TYPE_W, y + ty + TYPE_H, chosen ? 0xFF3D2E22 : hover ? 0xFF2F2621 : C_BG_INNER);
            if (chosen) g.outline(x + tx, y + ty, TYPE_W, TYPE_H, C_EMBER);
            com.jo112ss.rpgmod.item.WeaponType t = ForgeableWeapons.TYPES.get(i);
            g.item(new ItemStack(com.jo112ss.rpgmod.item.ModItems.get(t, ForgeableWeapons.MATERIALS.get(chosenMat))), x + tx + 11, y + ty + 3);
            if (hover) g.setTooltipForNextFrame(font, Component.translatable("rpgmod.type." + t.id), mouseX, mouseY);
        }
        g.text(font, Component.translatable("rpgmod.gui.armor_row"), x + tabX(9) + TYPE_W + 8, y + tabY(9) + 7, C_DIM, false);
        for (int i = 0; i < ForgeableWeapons.MATERIALS.size(); i++) {
            int tx = MAT_X + i * MAT_STEP;
            boolean chosen = i == chosenMat;
            boolean hover = rx >= tx && rx < tx + MAT_W && ry >= MAT_Y && ry < MAT_Y + MAT_H;
            inset(g, x + tx, y + MAT_Y, x + tx + MAT_W, y + MAT_Y + MAT_H, chosen ? 0xFF3D2E22 : hover ? 0xFF2F2621 : C_BG_INNER);
            if (chosen) g.outline(x + tx, y + MAT_Y, MAT_W, MAT_H, C_EMBER);
            com.jo112ss.rpgmod.item.WeaponMaterial m = ForgeableWeapons.MATERIALS.get(i);
            g.item(new ItemStack(m.ingot.get()), x + tx + 3, y + MAT_Y + 1);
            g.text(font, Component.translatable("rpgmod.material." + m.id), x + tx + 21, y + MAT_Y + 5, chosen ? C_TEXT : C_DIM, false);
        }
        ForgeableWeapon w = ForgeableWeapons.byIndex(chosenWeapon);
        if (w == null) return;
        inset(g, x + SWORD_X - 2, y + PREVIEW_Y - 2, x + SWORD_X + SWORD_W + 2, y + PREVIEW_Y + SWORD_H + 2, C_BG_INNER);
        g.blit(RenderPipelines.GUI_TEXTURED, w.guiTexture(), x + SWORD_X, y + PREVIEW_Y, 0, 0, SWORD_W, SWORD_H, SWORD_W, SWORD_H);
        ItemStack result = new ItemStack(w.result().get());
        int ly = y + PREVIEW_Y + SWORD_H + 6;
        g.text(font, result.getHoverName(), x + 12, ly + 4, C_TEXT, false);
        int mx = x + 112;
        for (ForgeableWeapon.Material m : w.materials()) {
            Item item = m.item().get();
            g.item(new ItemStack(item), mx, ly);
            boolean enough = count(item) >= m.count();
            g.text(font, "x" + m.count(), mx + 17, ly + 4, enough ? C_OK : C_OVER, false);
            mx += 36;
        }
        double diff = w.material().defaultDifficulty;
        try {
            var v = com.jo112ss.rpgmod.config.SmithingConfig.DIFFICULTY.get(w.material());
            if (v != null) diff = v.get();
        } catch (IllegalStateException ignored) {
            // synced config not received yet
        }
        int dcol = diff <= 0.9 ? C_OK : diff <= 1.05 ? C_TEXT : diff <= 1.15 ? C_UNDER : C_OVER;
        g.text(font, Component.translatable("rpgmod.gui.difficulty", String.format("%.2f", diff)), x + 12, ly + 14, dcol, false);
        if (!hasMaterials(w)) {
            g.centeredText(font, Component.translatable("rpgmod.gui.missing"), x + W / 2, y + BTN_Y + 23, C_OVER);
        }
    }

    private int count(Item item) {
        if (minecraft.player == null) return 0;
        Inventory inv = minecraft.player.getInventory();
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.is(item)) n += st.getCount();
        }
        return n;
    }

    private boolean hasMaterials(ForgeableWeapon w) {
        for (ForgeableWeapon.Material m : w.materials()) if (count(m.item().get()) < m.count()) return false;
        return true;
    }

    // ---- heating / working stage

    private void drawForge(GuiGraphicsExtractor g, int x, int y, ForgeSyncPayload v) {
        ForgeableWeapon w = ForgeableWeapons.byIndex(v.weapon());
        int parts = v.targets().length;
        boolean working = v.stage() == ForgeSyncPayload.STAGE_WORKING;

        drawTemperature(g, x, y, v);

        // weapon image with part highlights
        inset(g, x + SWORD_X - 2, y + SWORD_Y - 2, x + SWORD_X + SWORD_W + 2, y + SWORD_Y + SWORD_H + 2, C_BG_INNER);
        if (w != null) {
            g.blit(RenderPipelines.GUI_TEXTURED, w.guiTexture(), x + SWORD_X, y + SWORD_Y, 0, 0, SWORD_W, SWORD_H, SWORD_W, SWORD_H);
        }
        int sel = working ? v.selectedPart() : -1;
        for (int i = 0; i < parts && w != null; i++) {
            int l = x + partLeft(w, i, parts), pw = partWidth(w, parts);
            boolean isSel = i == sel;
            boolean isAdj = sel >= 0 && Math.abs(i - sel) == 1;
            if (isSel) {
                g.fill(l, y + SWORD_Y, l + pw, y + SWORD_Y + SWORD_H, 0x55FFC070);
                g.outline(l, y + SWORD_Y, pw, SWORD_H, 0xFFFFC070);
            } else if (isAdj) {
                g.fill(l, y + SWORD_Y, l + pw, y + SWORD_Y + SWORD_H, 0x28FFC070);
            }
            if (i > 0) g.fill(l - 1, y + SWORD_Y + 2, l, y + SWORD_Y + SWORD_H - 2, 0x40000000);
            drawGauge(g, l, y, pw, i, v, isSel, isAdj);
        }

        // info column (right of the weapon image)
        int ix = x + SWORD_X + SWORD_W + 6;
        if (working) {
            g.text(font, Component.translatable("rpgmod.gui.power_label"), ix, y + GAUGE_Y, C_DIM, false);
            g.text(font, String.format("%.1f", v.estimatedPower()), ix, y + GAUGE_Y + 10, 0xFFFFC070, false);
            g.text(font, Component.translatable("rpgmod.gui.adjacent_label"), ix, y + GAUGE_Y + 22, C_DIM, false);
            g.text(font, String.format("%.1f", v.estimatedPower() * v.adjacentRatio()), ix, y + GAUGE_Y + 32, C_TEXT, false);
            g.text(font, Component.translatable("rpgmod.gui.strikes_label", v.strikes()), ix, y + GAUGE_Y + 46, C_DIM, false);
        } else {
            g.centeredText(font, Component.translatable("rpgmod.gui.heating_hint"), x + W / 2, y + GAUGE_Y + GAUGE_H + 15, C_DIM);
        }

        // timing bar
        if (v.stage() == ForgeSyncPayload.STAGE_HEATING) drawHeatBar(g, x, y, v);
        else drawStrikeBar(g, x, y, v);

        // judgement popup
        if (working && v.lastJudgement() >= 0 && ClientForgeView.judgementAge() < 1200) {
            Judgement j = Judgement.byId(v.lastJudgement());
            int col = switch (j) {
                case PERFECT -> 0xFFFF6A5A;
                case GOOD -> 0xFFFFD060;
                case BAD -> 0xFF80E080;
                case MISS -> 0xFFB0B0B0;
            };
            float t = ClientForgeView.judgementAge() / 1200f;
            int alpha = (int) (255 * (1 - t * t));
            col = (Math.max(alpha, 16) << 24) | (col & 0xFFFFFF);
            Component jt = Component.translatable("rpgmod.judgement." + j.name().toLowerCase(java.util.Locale.ROOT))
                    .append(String.format(" +%.1f", v.lastDamage()));
            g.centeredText(font, jt, x + BAR_X + BAR_W / 2, y + BAR_Y - 12 - (int) (t * 6), col);
        }
    }

    private void drawGauge(GuiGraphicsExtractor g, int l, int y, int pw, int i, ForgeSyncPayload v, boolean isSel, boolean isAdj) {
        float val = v.values()[i], tgt = v.targets()[i];
        float pred = 0;
        if (isSel) pred = v.estimatedPower();
        else if (isAdj) pred = v.estimatedPower() * v.adjacentRatio();
        float max = Math.max(Math.max(tgt * 1.6f, val + pred), 1f);
        int gx0 = l + 3, gx1 = l + pw - 3, gy0 = y + GAUGE_Y, gy1 = y + GAUGE_Y + GAUGE_H;
        inset(g, gx0, gy0, gx1, gy1, C_BG_INNER);
        float tol = Math.max(0.6f, tgt * 0.06f);
        int col = val < tgt - tol ? C_UNDER : val <= tgt + tol ? C_OK : C_OVER;
        int fillTop = gy1 - Math.round(val / max * GAUGE_H);
        if (val > 0) g.fill(gx0, fillTop, gx1, gy1, col);
        if (pred > 0) {
            int predTop = gy1 - Math.round((val + pred) / max * GAUGE_H);
            g.fill(gx0, predTop, gx1, fillTop, 0x66FFFFFF);
        }
        int ty = gy1 - Math.round(tgt / max * GAUGE_H);
        g.fill(gx0 - 2, ty, gx1 + 2, ty + 1, 0xFFFFFFFF);
        String label = String.format("%.0f/%.0f", val, tgt);
        int lw = font.width(label);
        g.text(font, label, l + (pw - lw) / 2, gy1 + 3, isSel ? 0xFFFFC070 : C_DIM, false);
    }

    private void drawTemperature(GuiGraphicsExtractor g, int x, int y, ForgeSyncPayload v) {
        int x0 = x + TEMP_X, y0 = y + TEMP_Y, x1 = x0 + TEMP_W, y1 = y0 + TEMP_H;
        inset(g, x0, y0, x1, y1, C_BG_INNER);
        float temp = v.stage() == ForgeSyncPayload.STAGE_HEATING
                ? v.heatMin() + (v.heatMax() - v.heatMin()) * (float) ClientForgeView.barPosition()
                : v.temperature();
        int top = y1 - Math.round(temp / 100f * TEMP_H);
        g.fillGradient(x0, top, x1, y1, heatColor(temp), heatColor(Math.max(0, temp - 40)));
        int endY = y1 - Math.round(v.endTemp() / 100f * TEMP_H);
        g.fill(x0 - 2, endY, x1 + 2, endY + 1, C_OVER);
        g.centeredText(font, Component.translatable("rpgmod.gui.temperature"), x0 + TEMP_W / 2 + 1, y1 + 4, C_DIM);
        g.centeredText(font, Component.literal(String.valueOf(Math.round(temp))), x0 + TEMP_W / 2 + 1, y1 + 14, heatColor(temp));
    }

    /** Dull red → orange → yellow-white as the metal heats up. */
    private static int heatColor(float temp) {
        float t = Math.max(0, Math.min(1, temp / 100f));
        int r, gg, b;
        if (t < 0.5f) {
            float k = t / 0.5f;
            r = (int) (90 + 140 * k); gg = (int) (40 + 60 * k); b = (int) (40 - 20 * k);
        } else {
            float k = (t - 0.5f) / 0.5f;
            r = (int) (230 + 25 * k); gg = (int) (100 + 140 * k); b = (int) (20 + 150 * k);
        }
        return 0xFF000000 | (r << 16) | (gg << 8) | b;
    }

    private void drawHeatBar(GuiGraphicsExtractor g, int x, int y, ForgeSyncPayload v) {
        int x0 = x + BAR_X, y0 = y + BAR_Y;
        inset(g, x0, y0, x0 + BAR_W, y0 + BAR_H, C_BG_INNER);
        for (int i = 0; i < BAR_W; i++) {
            float temp = v.heatMin() + (v.heatMax() - v.heatMin()) * (i / (float) (BAR_W - 1));
            g.fill(x0 + i, y0, x0 + i + 1, y0 + BAR_H, heatColor(temp));
        }
        drawMarker(g, x0, y0);
        g.text(font, String.valueOf(Math.round(v.heatMin())), x0 - 2 - font.width(String.valueOf(Math.round(v.heatMin()))), y0 + 2, C_DIM, false);
        g.text(font, String.valueOf(Math.round(v.heatMax())), x0 + BAR_W + 3, y0 + 2, C_DIM, false);
    }

    private void drawStrikeBar(GuiGraphicsExtractor g, int x, int y, ForgeSyncPayload v) {
        int x0 = x + BAR_X, y0 = y + BAR_Y;
        inset(g, x0, y0, x0 + BAR_W, y0 + BAR_H, 0xFF3A3533);
        int c = x0 + BAR_W / 2, half = BAR_W / 2;
        int bad = Math.round(v.badZone() * half), good = Math.round(v.goodZone() * half), perf = Math.max(1, Math.round(v.perfectZone() * half));
        // power zones: outer = weak (green), middle = medium (yellow), centre = strong (red)
        g.fill(c - bad, y0, c + bad, y0 + BAR_H, 0xFF4C9F56);
        g.fill(c - good, y0, c + good, y0 + BAR_H, 0xFFD9A22B);
        g.fill(c - perf, y0, c + perf, y0 + BAR_H, 0xFFD23A36);
        if (v.barRunning()) drawMarker(g, x0, y0);
        else g.centeredText(font, Component.translatable("rpgmod.gui.select_part"), c, y0 + 2, C_TEXT);
    }

    private void drawMarker(GuiGraphicsExtractor g, int x0, int y0) {
        int mx = x0 + (int) Math.round(ClientForgeView.barPosition() * (BAR_W - 1));
        g.fill(mx - 2, y0 - 3, mx + 3, y0 + BAR_H + 3, 0xFF000000);
        g.fill(mx - 1, y0 - 2, mx + 2, y0 + BAR_H + 2, 0xFFFFFFFF);
    }

    // ---- result stage

    private void drawResult(GuiGraphicsExtractor g, int x, int y, ForgeSyncPayload v) {
        ItemStack result = v.result();
        inset(g, x + 14, y + 24, x + W - 14, y + BTN_Y - 8, C_BG_INNER);
        if (result.isEmpty()) {
            g.centeredText(font, Component.translatable("rpgmod.gui.missing"), x + W / 2, y + 60, C_DIM);
            return;
        }
        Quality q = Quality.byOrdinal(Math.max(0, v.quality()));
        inset(g, x + 18, y + 28, x + 38, y + 48, 0xFF2E2622);
        g.item(result, x + 20, y + 30);
        g.text(font, result.getHoverName(), x + 44, y + 29, q.color | 0xFF000000, true);
        g.text(font, Component.translatable("rpgmod.tooltip.quality", Component.translatable("rpgmod.quality." + q.id)),
                x + 44, y + 40, q.color | 0xFF000000, false);
        g.text(font, Component.translatable("rpgmod.gui.error", String.format("%.1f", v.errorRatio() * 100)), x + 170, y + 40, C_DIM, false);
        ForgedData d = result.get(ModRegistries.FORGED_DATA.get());
        if (d != null) {
            List<Component> lines = new ArrayList<>();
            for (ForgedData.Option o : d.options()) lines.add(ForgeTooltips.optionLine(o));
            int ly = y + 58;
            for (Component line : lines) {
                g.text(font, line, x + 26, ly, 0xFF9FD8FF, false);
                ly += 12;
            }
        }
        // final gauges, small
        int parts = v.targets().length;
        for (int i = 0; i < parts; i++) {
            int bx = x + 150 + i * 18, by0 = y + 60, by1 = y + 112;
            float val = v.values()[i], tgt = v.targets()[i];
            float max = Math.max(Math.max(tgt * 1.6f, val), 1f);
            inset(g, bx, by0, bx + 12, by1, 0xFF2E2622);
            float tol = Math.max(0.6f, tgt * 0.06f);
            int col = val < tgt - tol ? C_UNDER : val <= tgt + tol ? C_OK : C_OVER;
            g.fill(bx, by1 - Math.round(val / max * (by1 - by0)), bx + 12, by1, col);
            int ty = by1 - Math.round(tgt / max * (by1 - by0));
            g.fill(bx - 1, ty, bx + 13, ty + 1, 0xFFFFFFFF);
        }
    }
}
