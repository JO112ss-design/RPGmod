package com.jo112ss.rpgmod.item;

import java.util.function.Supplier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ToolMaterial;

/** Weapon materials. Netherite is not forged directly: it comes from upgrading diamond at a smithing table. */
public enum WeaponMaterial {
    // flat bonus, then a multiplier on the whole attack (weapon + the character's own attack)
    COPPER("copper", ToolMaterial.COPPER, () -> Items.COPPER_INGOT, 3, 1.0, 400, true, 0.85),
    IRON("iron", ToolMaterial.IRON, () -> Items.IRON_INGOT, 9, 1.1, 480, true, 1.0),
    GOLD("gold", ToolMaterial.GOLD, () -> Items.GOLD_INGOT, 12, 1.2, 360, true, 1.1),
    DIAMOND("diamond", ToolMaterial.DIAMOND, () -> Items.DIAMOND, 15, 1.33, 640, true, 1.2),
    NETHERITE("netherite", ToolMaterial.NETHERITE, () -> Items.NETHERITE_INGOT, 18, 1.45, 720, false, 1.3);

    public final String id;
    public final ToolMaterial tool;
    public final Supplier<Item> ingot;
    /** Extra arrow damage (at full draw) for bows of this material, on top of a normal bow. */
    /** Flat attack the material adds to the weapon kind's own (bows: to the arrow; staffs: to their magic). */
    public final int bowBonus;
    /**
     * Multiplier on the whole attack while the weapon is held - the weapon and the character's own
     * attack from levels and the panel - so better weapons matter more the stronger the character is.
     */
    public final double multiplier;
    public final int bowDurability;
    public final boolean forgeable;
    /** Default smithing difficulty (bar speed, zone width, quality strictness); configurable. */
    public final double defaultDifficulty;

    WeaponMaterial(String id, ToolMaterial tool, Supplier<Item> ingot, int bowBonus, double multiplier, int bowDurability, boolean forgeable, double defaultDifficulty) {
        this.id = id;
        this.multiplier = multiplier;
        this.tool = tool;
        this.ingot = ingot;
        this.bowBonus = bowBonus;
        this.bowDurability = bowDurability;
        this.forgeable = forgeable;
        this.defaultDifficulty = defaultDifficulty;
    }

    /**
     * Melee attack bonus of the material (added to the weapon kind's own attack): copper keeps vanilla's
     * +1, then iron +7, gold +11, diamond +15, netherite +17. Half of the attack levels used to give
     * went into these steps, so at level 100 a diamond weapon hits about as hard as netherite did
     * before. Bows use the same steps for their arrow bonus.
     */
    public float meleeBonus() {
        return bowBonus;
    }

    /** Magic attack of a staff of this material (like a sword's attack, for spells and caster skills). */
    /** A staff's own magic attack: 9 + the flat bonus (copper 12, iron 18, gold 21, diamond 24, netherite 27), then x {@link #multiplier}. */
    public double staffMagic() {
        return 9 + bowBonus;
    }
}
