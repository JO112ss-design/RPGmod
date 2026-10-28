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
    // attack is three times vanilla's scale (the whole attack side and monster health were tripled)
    SWORD("sword", 9.0F, -2.4F, 2, List.of(new Extra(() -> Items.STICK, 1)), TargetProfile.SWORD, 2, 123),
    DAGGER("dagger", 3.0F, -1.5F, 1, List.of(new Extra(() -> Items.STICK, 1)), TargetProfile.DAGGER, 40, 85),
    MACE("mace", 12.0F, -2.9F, 4, List.of(new Extra(() -> Items.STICK, 1)), TargetProfile.MACE, 2, 123),
    HAMMER("hammer", 18.0F, -3.3F, 5, List.of(new Extra(() -> Items.STICK, 2)), TargetProfile.HAMMER, 2, 123),
    STAFF("staff", 4.5F, -2.6F, 1, List.of(new Extra(() -> Items.STICK, 1), new Extra(() -> Items.GLASS, 1)), TargetProfile.STAFF, 2, 123),
    BOW("bow", 0, 0, 2, List.of(new Extra(() -> Items.BOW, 1)), TargetProfile.BOW, 2, 155),
    /** Pirate: a bayonet you stab with and a gun you fire with right click (see GunbladeItem). */
    GUNBLADE("gunblade", 7.5F, -2.6F, 3, List.of(new Extra(() -> Items.GUNPOWDER, 1), new Extra(() -> Items.STICK, 1)), TargetProfile.GUNBLADE, 2, 155),
    /** Lancer: hits harder and reaches further (+1.5 blocks). */
    SPEAR("spear", 10.5F, -2.9F, 2, List.of(new Extra(() -> Items.STICK, 2)), TargetProfile.SPEAR, 2, 155),
    /** Monk: light and fast. */
    KNUCKLE("knuckle", 4.5F, -1.2F, 2, List.of(new Extra(() -> Items.LEATHER, 1)), TargetProfile.KNUCKLE, 40, 85),
    /** Bard: a lute - magic attack like a staff, played with the songs. */
    LUTE("lute", 3.0F, -2.4F, 2, List.of(new Extra(() -> Items.STRING, 2), new Extra(() -> Items.OAK_PLANKS, 2)), TargetProfile.LUTE, 2, 155),
    HELMET("helmet", 0, 0, 5, List.of(), TargetProfile.HELMET, 10, 140),
    CHESTPLATE("chestplate", 0, 0, 8, List.of(), TargetProfile.CHESTPLATE, 10, 140),
    LEGGINGS("leggings", 0, 0, 7, List.of(), TargetProfile.LEGGINGS, 10, 140),
    BOOTS("boots", 0, 0, 4, List.of(), TargetProfile.BOOTS, 10, 140),
    /** Shields (copper / iron / diamond, netherite by smithing): a vanilla shield + 3 ingots. */
    SHIELD("shield", 0, 0, 3, List.of(new Extra(() -> Items.SHIELD, 1)), TargetProfile.SHIELD, 10, 140),
    /** Vanilla arrows, forged in a batch (iron only): 1 iron + 1 stick, 4 to 32 arrows by quality. */
    ARROW("arrow", 0, 0, 1, List.of(new Extra(() -> Items.STICK, 1)), TargetProfile.ARROW, 6, 148),
    /**
     * Robes: the casters' armour (see {@link RobeArmor}) - 60% of a metal piece's armour, and their
     * forging options lean to magic (magic power, cast speed, MP) where metal armour's lean to attack.
     */
    ROBE_HOOD("robe_hood", 0, 0, 2, List.of(new Extra(() -> Items.WHITE_WOOL, 3)), TargetProfile.HELMET, 10, 140),
    ROBE("robe", 0, 0, 4, List.of(new Extra(() -> Items.WHITE_WOOL, 5)), TargetProfile.CHESTPLATE, 10, 140),
    ROBE_PANTS("robe_pants", 0, 0, 3, List.of(new Extra(() -> Items.WHITE_WOOL, 4)), TargetProfile.LEGGINGS, 10, 140),
    ROBE_BOOTS("robe_boots", 0, 0, 2, List.of(new Extra(() -> Items.WHITE_WOOL, 2)), TargetProfile.BOOTS, 10, 140);

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

    /** The casters' cloth armour. */
    public boolean isRobe() {
        return this == ROBE_HOOD || this == ROBE || this == ROBE_PANTS || this == ROBE_BOOTS;
    }

    /** Anything worn in an armour slot: metal armour and robes. */
    public boolean wearable() {
        return isArmor() || isRobe();
    }

    /** Armor, robes and shields roll from the armour option pools. */
    public boolean defensive() {
        return wearable() || this == SHIELD;
    }

    /** Which option pool forging rolls from. */
    public com.jo112ss.rpgmod.smithing.core.OptionType.Pool optionPool() {
        if (isRobe()) return com.jo112ss.rpgmod.smithing.core.OptionType.Pool.ROBE;
        return defensive() ? com.jo112ss.rpgmod.smithing.core.OptionType.Pool.ARMOR : com.jo112ss.rpgmod.smithing.core.OptionType.Pool.WEAPON;
    }

    /** Weapons that carry magic attack (and roll the magic forging options): staffs and lutes. */
    public boolean magical() {
        return this == STAFF || this == LUTE;
    }

    /** Vanilla items (swords and armor) are not registered by this mod. */
    public boolean isVanilla() {
        return this == SWORD || isArmor() || this == ARROW;
    }
}
