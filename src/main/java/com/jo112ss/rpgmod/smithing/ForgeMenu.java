package com.jo112ss.rpgmod.smithing;

import com.jo112ss.rpgmod.registry.ModRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;

/**
 * Container menu for the forging anvil. It has no slots: materials are taken from the player's
 * inventory when forging starts. Its job is to tie the forging session to an open, in-range anvil
 * and to tell the server when the GUI closes (including logout or walking away).
 */
public class ForgeMenu extends AbstractContainerMenu {
    private final ContainerLevelAccess access;
    private final Player player;

    /** Client side. */
    public ForgeMenu(int id, Inventory inv) {
        this(id, inv, ContainerLevelAccess.NULL);
    }

    /** Server side. */
    public ForgeMenu(int id, Inventory inv, ContainerLevelAccess access) {
        super(ModRegistries.FORGE_MENU.get(), id);
        this.access = access;
        this.player = inv.player;
        if (player instanceof ServerPlayer sp) {
            ForgeSessionManager.onMenuOpened(sp, this);
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModRegistries.FORGING_ANVIL.get());
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (player instanceof ServerPlayer sp) {
            ForgeSessionManager.onMenuClosed(sp, this);
        }
    }
}
