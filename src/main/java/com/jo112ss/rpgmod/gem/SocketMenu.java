package com.jo112ss.rpgmod.gem;

import com.jo112ss.rpgmod.RpgMod;
import com.jo112ss.rpgmod.registry.ModRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Socket table menu: a gear slot and a gem slot. Button 0 sets the gem into the gear for
 * {@link #COST} experience levels. Gems can never be taken out again.
 */
public class SocketMenu extends AbstractContainerMenu {
    /** Experience levels it costs to set a gem. */
    public static final int COST = 5;
    public static final int GEAR_X = 35, GEAR_Y = 30, GEM_X = 71, GEM_Y = 30;
    public static final int INV_Y = 102;

    private final ContainerLevelAccess access;
    private final Player player;
    private final Container table = new SimpleContainer(2) {
        @Override
        public void setChanged() {
            super.setChanged();
            SocketMenu.this.slotsChanged(this);
        }
    };

    public SocketMenu(int id, Inventory inv) {
        this(id, inv, ContainerLevelAccess.NULL);
    }

    public SocketMenu(int id, Inventory inv, ContainerLevelAccess access) {
        super(ModRegistries.SOCKET_MENU.get(), id);
        this.access = access;
        this.player = inv.player;
        addSlot(new Slot(table, 0, GEAR_X, GEAR_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return Sockets.capacity(stack) > 0;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        addSlot(new Slot(table, 1, GEM_X, GEM_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return Sockets.isGem(stack);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        addStandardInventorySlots(inv, 8, INV_Y);
    }

    public ItemStack gear() {
        return table.getItem(0);
    }

    public ItemStack gemStack() {
        return table.getItem(1);
    }

    /** Why the gem cannot be set right now (a translation key), or null if it can. */
    public static String problem(ItemStack gear, ItemStack gemStack, Player player) {
        if (gear.isEmpty()) return "rpgmod.socket.need_gear";
        if (Sockets.free(gear) <= 0) return "rpgmod.socket.full";
        if (gemStack.isEmpty()) return "rpgmod.socket.need_gem";
        Gem gem = GemItems.gem(gemStack);
        if (gem == null) return "rpgmod.socket.blank_gem";
        if (gem.rarity() == Gem.LEGENDARY) {
            for (Gem g : Sockets.gems(gear)) if (g.effect().equals(gem.effect())) return "rpgmod.socket.same_legendary";
        }
        if (!player.hasInfiniteMaterials() && player.experienceLevel < COST) return "rpgmod.socket.no_xp";
        return null;
    }

    public String problem() {
        return problem(gear(), gemStack(), player);
    }

    @Override
    public boolean clickMenuButton(Player p, int id) {
        if (id != 0 || problem() != null) return false;
        ItemStack gear = gear();
        Gem gem = GemItems.gem(gemStack());
        gear.set(ModRegistries.SOCKETS.get(), Sockets.of(gear).with(gem));
        table.setItem(1, ItemStack.EMPTY);
        if (!p.hasInfiniteMaterials()) p.giveExperienceLevels(-COST);
        access.execute((level, pos) -> {
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0F, 1.2F);
            level.playSound(null, pos, SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 0.8F, 1.4F);
            if (level instanceof ServerLevel sl) {
                sl.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 12, 0.25, 0.1, 0.25, 0.03);
            }
        });
        table.setChanged();
        broadcastChanges();
        if (com.jo112ss.rpgmod.devtest.AutoTestServer.ENABLED) {
            RpgMod.LOGGER.info("[AUTOTEST] socketed {} {} into {} ({}/{}), level now {}", gem.rarityId(), gem.effect(), gear.getItem(),
                    Sockets.gems(gear).size(), Sockets.capacity(gear), p.experienceLevel);
        }
        return true;
    }

    /** Dev test: put items in the two slots. */
    public void testPut(ItemStack gear, ItemStack gem) {
        table.setItem(0, gear);
        table.setItem(1, gem);
    }

    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        Slot slot = slots_(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int invStart = 2, invEnd = this.slots.size();
        if (index < 2) {
            if (!moveItemStackTo(stack, invStart, invEnd, true)) return ItemStack.EMPTY;
        } else if (Sockets.isGem(stack)) {
            if (!moveItemStackTo(stack, 1, 2, false)) return ItemStack.EMPTY;
        } else if (Sockets.capacity(stack) > 0) {
            if (!moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    private Slot slots_(int index) {
        return index >= 0 && index < this.slots.size() ? getSlot(index) : null;
    }

    @Override
    public boolean stillValid(Player p) {
        return stillValid(access, p, ModRegistries.SOCKET_TABLE.get());
    }

    @Override
    public void removed(Player p) {
        super.removed(p);
        access.execute((level, pos) -> clearContainer(p, table));
    }
}
