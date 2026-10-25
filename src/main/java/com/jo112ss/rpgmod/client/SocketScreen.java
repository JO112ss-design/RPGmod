package com.jo112ss.rpgmod.client;

import com.jo112ss.rpgmod.client.job.Ui;
import com.jo112ss.rpgmod.gem.Gem;
import com.jo112ss.rpgmod.gem.GemItems;
import com.jo112ss.rpgmod.gem.GemText;
import com.jo112ss.rpgmod.gem.SocketMenu;
import com.jo112ss.rpgmod.gem.Sockets;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Socket table: gear on the left, gem next to it, the gear's sockets below and the set button. */
public class SocketScreen extends AbstractContainerScreen<SocketMenu> {
    private static final int W = 176, H = 180;
    private static final int ACCENT = 0xB070FF;
    private static final int SOCKET_Y = 62;
    private Button setBtn;

    public SocketScreen(SocketMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, W, H);
        inventoryLabelY = SocketMenu.INV_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        setBtn = addRenderableWidget(Button.builder(Component.translatable("rpgmod.socket.set", SocketMenu.COST),
                b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0)).bounds(leftPos + 104, topPos + 28, 64, 20).build());
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (setBtn != null) setBtn.active = menu.problem() == null;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        int x = leftPos, y = topPos;
        Ui.panel(g, x, y, W, H, ACCENT);
        for (Slot s : menu.slots) Ui.inset(g, x + s.x - 1, y + s.y - 1, 18, 18);
        // "+" between gear and gem
        g.centeredText(font, "+", x + (SocketMenu.GEAR_X + SocketMenu.GEM_X) / 2 + 8, y + SocketMenu.GEAR_Y + 4, 0xFFB8B0D8);

        ItemStack gear = menu.gear();
        int cap = Sockets.capacity(gear);
        List<Gem> gems = Sockets.gems(gear);
        int cx = x + W / 2;
        if (cap > 0) {
            int spacing = 26, first = cx - (cap - 1) * spacing / 2;
            for (int i = 0; i < cap; i++) {
                int sx = first + i * spacing, sy = y + SOCKET_Y;
                boolean filled = i < gems.size();
                int ring = filled ? Gem.RARITY_COLORS[gems.get(i).rarity()] : 0xFF6A6280;
                Ui.disc(g, sx, sy, 11, 0xFF15121E);
                Ui.circle(g, sx, sy, 11, ring, 1);
                if (filled) g.item(GemItems.stack(gems.get(i)), sx - 8, sy - 8);
            }
        } else {
            g.centeredText(font, Component.translatable("rpgmod.socket.hint"), cx, y + SOCKET_Y - 4, 0xFF8A82A8);
        }
        String problem = menu.problem();
        if (problem != null && !gear.isEmpty()) {
            g.centeredText(font, Component.translatable(problem, SocketMenu.COST), cx, y + SOCKET_Y + 15, 0xFFFF7A7A);
        } else if (problem == null) {
            Gem gem = GemItems.gem(menu.gemStack());
            g.centeredText(font, GemText.effect(gem), cx, y + SOCKET_Y + 15, 0xFFFFFFFF);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int xm, int ym) {
        g.text(font, title, titleLabelX, titleLabelY + 1, 0xFFE8DCFF, false);
        g.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFFB8B0D8, false);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        // tooltip of a socketed gem
        ItemStack gear = menu.gear();
        int cap = Sockets.capacity(gear);
        List<Gem> gems = Sockets.gems(gear);
        int spacing = 26, first = leftPos + W / 2 - (cap - 1) * spacing / 2;
        for (int i = 0; i < gems.size() && i < cap; i++) {
            int sx = first + i * spacing, sy = topPos + SOCKET_Y;
            if (Math.abs(mouseX - sx) <= 10 && Math.abs(mouseY - sy) <= 10) {
                g.setTooltipForNextFrame(font, GemItems.stack(gems.get(i)), mouseX, mouseY);
            }
        }
    }
}
