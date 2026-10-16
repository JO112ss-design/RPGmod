package com.jo112ss.rpgmod.smithing;

import com.jo112ss.rpgmod.RpgMod;
import com.jo112ss.rpgmod.smithing.core.TargetProfile;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;

/** The list of weapons the forging anvil can make. Order is the order shown in the GUI. */
public final class ForgeableWeapons {
    private ForgeableWeapons() {}

    public static final List<String> SWORD_PARTS = List.of("rpgmod.part.0", "rpgmod.part.1", "rpgmod.part.2", "rpgmod.part.3", "rpgmod.part.4");

    public static final List<ForgeableWeapon> ALL = List.of(
            sword("copper_sword", () -> Items.COPPER_SWORD, () -> Items.COPPER_INGOT, "copper"),
            sword("iron_sword", () -> Items.IRON_SWORD, () -> Items.IRON_INGOT, "iron"),
            sword("golden_sword", () -> Items.GOLDEN_SWORD, () -> Items.GOLD_INGOT, "gold"),
            sword("diamond_sword", () -> Items.DIAMOND_SWORD, () -> Items.DIAMOND, "diamond")
    );

    private static ForgeableWeapon sword(String id, java.util.function.Supplier<net.minecraft.world.item.Item> result,
                                         java.util.function.Supplier<net.minecraft.world.item.Item> ingot, String texture) {
        return new ForgeableWeapon(id, result,
                List.of(new ForgeableWeapon.Material(ingot, 2), new ForgeableWeapon.Material(() -> Items.STICK, 1)),
                TargetProfile.SWORD, SWORD_PARTS,
                Identifier.fromNamespaceAndPath(RpgMod.MODID, "textures/gui/sword_" + texture + ".png"));
    }

    public static ForgeableWeapon byIndex(int index) {
        return index >= 0 && index < ALL.size() ? ALL.get(index) : null;
    }

    public static int indexOf(ForgeableWeapon w) {
        return ALL.indexOf(w);
    }
}
