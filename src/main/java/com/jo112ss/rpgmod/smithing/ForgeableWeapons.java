package com.jo112ss.rpgmod.smithing;

import com.jo112ss.rpgmod.RpgMod;
import com.jo112ss.rpgmod.item.ModItems;
import com.jo112ss.rpgmod.item.WeaponMaterial;
import com.jo112ss.rpgmod.item.WeaponType;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.Identifier;

/** Everything the forging anvil can make: every weapon type in every forgeable material. */
public final class ForgeableWeapons {
    private ForgeableWeapons() {}

    public static final List<WeaponType> TYPES = List.of(WeaponType.values());
    public static final List<WeaponMaterial> MATERIALS;
    public static final List<ForgeableWeapon> ALL;

    static {
        List<WeaponMaterial> mats = new ArrayList<>();
        for (WeaponMaterial m : WeaponMaterial.values()) if (m.forgeable) mats.add(m);
        MATERIALS = List.copyOf(mats);
        List<ForgeableWeapon> all = new ArrayList<>();
        for (WeaponType type : TYPES) {
            for (WeaponMaterial mat : MATERIALS) all.add(create(type, mat));
        }
        ALL = List.copyOf(all);
    }

    private static ForgeableWeapon create(WeaponType type, WeaponMaterial mat) {
        List<ForgeableWeapon.Material> materials = new ArrayList<>();
        materials.add(new ForgeableWeapon.Material(mat.ingot, type.ingots));
        for (WeaponType.Extra e : type.extras) materials.add(new ForgeableWeapon.Material(e.item(), e.count()));
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < type.profile.parts(); i++) parts.add("rpgmod.part." + type.id + "." + i);
        return new ForgeableWeapon(type, mat, () -> ModItems.get(type, mat), List.copyOf(materials), type.profile, List.copyOf(parts),
                Identifier.fromNamespaceAndPath(RpgMod.MODID, "textures/gui/" + type.id + "_" + mat.id + ".png"),
                type.workX, type.workW);
    }

    public static ForgeableWeapon byIndex(int index) {
        return index >= 0 && index < ALL.size() ? ALL.get(index) : null;
    }

    public static int indexOf(ForgeableWeapon w) {
        return ALL.indexOf(w);
    }

    public static int indexOf(WeaponType type, WeaponMaterial mat) {
        for (int i = 0; i < ALL.size(); i++) {
            ForgeableWeapon w = ALL.get(i);
            if (w.type() == type && w.material() == mat) return i;
        }
        return -1;
    }
}
