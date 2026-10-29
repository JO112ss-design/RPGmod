package com.jo112ss.rpgmod.mixin;

import com.jo112ss.rpgmod.item.UniqueWeapons;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.ItemCombinerMenuSlotDefinition;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A boss's unique weapon is not upgraded at the smithing table (its strength and sockets are its own). */
@Mixin(SmithingMenu.class)
public abstract class SmithingUniqueMixin extends ItemCombinerMenu {
    protected SmithingUniqueMixin(MenuType<?> type, int id, Inventory inv, ContainerLevelAccess access, ItemCombinerMenuSlotDefinition slots) {
        super(type, id, inv, access, slots);
    }

    @Inject(method = "createResult", at = @At("TAIL"))
    private void rpgmod$noUniqueUpgrade(CallbackInfo ci) {
        if (UniqueWeapons.of(this.inputSlots.getItem(SmithingMenu.BASE_SLOT)) != null) this.resultSlots.setItem(0, ItemStack.EMPTY);
    }
}
