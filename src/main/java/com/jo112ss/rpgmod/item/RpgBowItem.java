package com.jo112ss.rpgmod.item;

import net.minecraft.world.item.BowItem;

/**
 * A material bow. Its arrows deal a flat bonus on top of a normal bow (reached at full draw);
 * the bonus is applied when the arrow hits, see ForgeOptionEffects.
 */
public class RpgBowItem extends BowItem {
    private final WeaponMaterial material;

    public RpgBowItem(WeaponMaterial material, Properties properties) {
        super(properties);
        this.material = material;
    }

    public WeaponMaterial material() {
        return material;
    }

    public int damageBonus() {
        return material.bowBonus;
    }
}
