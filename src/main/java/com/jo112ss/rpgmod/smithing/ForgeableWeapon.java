package com.jo112ss.rpgmod.smithing;

import com.jo112ss.rpgmod.item.WeaponMaterial;
import com.jo112ss.rpgmod.item.WeaponType;
import com.jo112ss.rpgmod.smithing.core.TargetProfile;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

/**
 * Something that can be forged at the forging anvil.
 *
 * @param type        weapon kind
 * @param material    weapon material
 * @param result      the item produced
 * @param materials   consumed when forging starts
 * @param profile     relative amount of work per part; its length is the number of parts
 * @param partKeys    lang keys of the parts, in image order (left to right)
 * @param guiTexture  horizontal 160x40 image of the weapon drawn above the part gauges
 * @param workX       left edge of the hammered span in the image
 * @param workW       width of the hammered span; it is split evenly between the parts
 */
public record ForgeableWeapon(
        WeaponType type,
        WeaponMaterial material,
        Supplier<Item> result,
        List<Material> materials,
        TargetProfile profile,
        List<String> partKeys,
        Identifier guiTexture,
        int workX,
        int workW
) {
    public record Material(Supplier<Item> item, int count) {}

    public String id() {
        return material.id + "_" + type.id;
    }

    public int parts() {
        return profile.parts();
    }
}
