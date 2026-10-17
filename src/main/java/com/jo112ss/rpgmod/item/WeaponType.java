package com.jo112ss.rpgmod.item;

import com.jo112ss.rpgmod.smithing.core.TargetProfile;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Weapon kinds. Each has its melee stats (added on top of the material bonus, like vanilla swords),
 * the materials needed per weapon besides the ingots, and its smithing layout: the parts and which
 * horizontal span of its GUI image (160x40) they cover.
 */
public enum WeaponType {
    SWORD("sword", 3.0F, -2.4F, 2, List.of(new Extra(() -> Items.STICK, 1)), TargetProfile.SWORD, 2, 123),
    DAGGER("dagger", 1.0F, -1.5F, 1, List.of(new Extra(() -> Items.STICK, 1)), TargetProfile.DAGGER, 40, 85),
    MACE("mace", 4.0F, -2.9F, 4, List.of(new Extra(() -> Items.STICK, 1)), TargetProfile.MACE, 2, 123),
    HAMMER("hammer", 6.0F, -3.3F, 5, List.of(new Extra(() -> Items.STICK, 2)), TargetProfile.HAMMER, 2, 123),
    STAFF("staff", 1.5F, -2.6F, 1, List.of(new Extra(() -> Items.STICK, 1), new Extra(() -> Items.AMETHYST_SHARD, 1)), TargetProfile.STAFF, 2, 123),
    BOW("bow", 0, 0, 2, List.of(new Extra(() -> Items.BOW, 1)), TargetProfile.BOW, 2, 155),
    HELMET("helmet", 0, 0, 5, List.of(), TargetProfile.HELMET, 10, 140),
    CHESTPLATE("chestplate", 0, 0, 8, List.of(), TargetProfile.CHESTPLATE, 10, 140),
    LEGGINGS("leggings", 0, 0, 7, List.of(), TargetProfile.LEGGINGS, 10, 140),
    BOOTS("boots", 0, 0, 4, List.of(), TargetProfile.BOOTS, 10, 140),
    /** Vanilla arrows, forged in a batch (iron only): 1 iron + 1 stick, 4 to 32 arrows by quality. */
    ARROW("arrow", 0, 0, 1, List.of(new Extra(() -> Items.STICK, 1)), TargetProfile.ARROW, 6, 148);

    public record Extra(Supplier<Item> item, int count) {}

    public final String id;
    public final float attackDamage;
    public final float attackSpeed;
    public final int ingots;
    public final List<Extra> extras;
    public final TargetProfile profile;
    public final int workX;
    public final int workW;

    WeaponType(String id, float attackDamage, float attackSpeed, int ingots, List<Extra> extras, TargetProfile profile, int workX, int workW) {
        this.id = id;
        this.attackDamage = attackDamage;
        this.attackSpeed = attackSpeed;
        this.ingots = ingots;
        this.extras = extras;
        this.profile = profile;
        this.workX = workX;
        this.workW = workW;
    }

    public boolean isBow() {
        return this == BOW;
    }

    /** Armor uses the vanilla items and the armor option pool. */
    public boolean isArmor() {
        return this == HELMET || this == CHESTPLATE || this == LEGGINGS || this == BOOTS;
    }

    /** Vanilla items (swords and armor) are not registered by this mod. */
    public boolean isVanilla() {
        return this == SWORD || isArmor() || this == ARROW;
    }
}
