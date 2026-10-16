package com.jo112ss.rpgmod.smithing;

import com.jo112ss.rpgmod.smithing.core.TargetProfile;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

/**
 * Something that can be forged at the forging anvil. Adding a new weapon (an axe, a mace...) only
 * needs a new entry in {@link ForgeableWeapons} with its own part layout and GUI image.
 *
 * @param id           stable id, used in packets and lang keys
 * @param result       the vanilla (or modded) item produced
 * @param materials    consumed when forging starts
 * @param profile      relative amount of work per part; its length is the number of parts
 * @param partKeys     lang keys of the parts, tip first
 * @param guiTexture   horizontal image of the weapon drawn above the part gauges
 */
public record ForgeableWeapon(
        String id,
        Supplier<Item> result,
        List<Material> materials,
        TargetProfile profile,
        List<String> partKeys,
        Identifier guiTexture
) {
    public record Material(Supplier<Item> item, int count) {}

    public int parts() {
        return profile.parts();
    }
}
