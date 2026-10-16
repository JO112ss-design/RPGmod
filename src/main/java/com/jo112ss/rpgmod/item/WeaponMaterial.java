package com.jo112ss.rpgmod.item;

import java.util.function.Supplier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ToolMaterial;

/** Weapon materials. Netherite is not forged directly: it comes from upgrading diamond at a smithing table. */
public enum WeaponMaterial {
    COPPER("copper", ToolMaterial.COPPER, () -> Items.COPPER_INGOT, 1, 400, true),
    IRON("iron", ToolMaterial.IRON, () -> Items.IRON_INGOT, 2, 480, true),
    GOLD("gold", ToolMaterial.GOLD, () -> Items.GOLD_INGOT, 3, 360, true),
    DIAMOND("diamond", ToolMaterial.DIAMOND, () -> Items.DIAMOND, 4, 640, true),
    NETHERITE("netherite", ToolMaterial.NETHERITE, () -> Items.NETHERITE_INGOT, 5, 720, false);

    public final String id;
    public final ToolMaterial tool;
    public final Supplier<Item> ingot;
    /** Extra arrow damage (at full draw) for bows of this material, on top of a normal bow. */
    public final int bowBonus;
    public final int bowDurability;
    public final boolean forgeable;

    WeaponMaterial(String id, ToolMaterial tool, Supplier<Item> ingot, int bowBonus, int bowDurability, boolean forgeable) {
        this.id = id;
        this.tool = tool;
        this.ingot = ingot;
        this.bowBonus = bowBonus;
        this.bowDurability = bowDurability;
        this.forgeable = forgeable;
    }
}
