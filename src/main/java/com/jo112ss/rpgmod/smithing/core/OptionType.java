package com.jo112ss.rpgmod.smithing.core;

/**
 * Random options. Weapons and armor draw from separate pools (an option may be in both with
 * different ranges). Values are rolled uniformly in the pool's range and rounded to {@link #step}.
 * Percent options store the percentage (5.0 = +5%).
 */
public enum OptionType {
    //                        id                     step  percent  weapon range   armor range
    ATTACK_DAMAGE("attack_damage", 0.1, true, 3, 8, NaN(), NaN()),
    ATTACK_SPEED("attack_speed", 0.1, true, 3, 8, NaN(), NaN()),
    CRIT_CHANCE("crit_chance", 0.1, true, 2, 5, NaN(), NaN()),
    CRIT_DAMAGE("crit_damage", 0.1, true, 5, 15, NaN(), NaN()),
    BOSS_DAMAGE("boss_damage", 0.1, true, 4, 10, NaN(), NaN()),
    // max health is a percentage now that players reach hundreds (old flat 1-2 options read as 1-2%)
    MAX_HEALTH("max_health", 1, true, 2, 5, 2, 5),
    ARMOR("armor", 1, false, 1, 2, 1, 2),
    MOVEMENT_SPEED("movement_speed", 0.1, true, 2, 5, 1, 3),
    DURABILITY("durability", 1, true, 10, 25, 10, 25),
    EXPERIENCE("experience", 0.1, true, 5, 12, 3, 8),
    KNOCKBACK_RESISTANCE("knockback_resistance", 0.1, true, 5, 15, 3, 8),
    ARMOR_TOUGHNESS("armor_toughness", 0.1, false, NaN(), NaN(), 0.5, 1.5),
    DAMAGE_REDUCTION("damage_reduction", 0.1, true, NaN(), NaN(), 2, 4),
    BOSS_RESISTANCE("boss_resistance", 0.1, true, NaN(), NaN(), 3, 8),
    // magic options: weapons only roll them on magic staffs (see OptionRoller)
    MAGIC_POWER("magic_power", 0.1, true, 5, 12, 2, 5),
    CAST_SPEED("cast_speed", 0.1, true, 5, 12, 2, 5),
    // mana: every weapon and armour piece can roll them
    MAX_MANA("max_mana", 1, false, 10, 25, 5, 12),
    MANA_REGEN("mana_regen", 0.1, true, 8, 20, 4, 10),
    // gems only (forging never rolls it): flat attack, on the x3 attack scale
    ATTACK_FLAT("attack_flat", 1, false, NaN(), NaN(), NaN(), NaN()),
    // gems only: flat magic attack, the casters' counterpart of ATTACK_FLAT
    MAGIC_FLAT("magic_flat", 1, false, NaN(), NaN(), NaN(), NaN());

    public final String id;
    public final double step;
    public final boolean percent;
    public final double weaponMin, weaponMax, armorMin, armorMax;

    OptionType(String id, double step, boolean percent, double weaponMin, double weaponMax, double armorMin, double armorMax) {
        this.id = id;
        this.step = step;
        this.percent = percent;
        this.weaponMin = weaponMin;
        this.weaponMax = weaponMax;
        this.armorMin = armorMin;
        this.armorMax = armorMax;
    }

    private static double NaN() {
        return Double.NaN;
    }

    /** Armour penetration was removed: options and gems that had it count as attack. */
    public static final String OLD_PENETRATION = "armor_penetration";

    /** The id an old option id now stands for. */
    public static String current(String id) {
        return OLD_PENETRATION.equals(id) ? ATTACK_DAMAGE.id : id;
    }

    public static OptionType byId(String id) {
        id = current(id);
        for (OptionType t : values()) if (t.id.equals(id)) return t;
        return null;
    }

    public boolean isMagic() {
        return this == MAGIC_POWER || this == CAST_SPEED;
    }

    public boolean inPool(boolean armor) {
        return !Double.isNaN(armor ? armorMin : weaponMin);
    }

    public double min(boolean armor) {
        return armor ? armorMin : weaponMin;
    }

    public double max(boolean armor) {
        return armor ? armorMax : weaponMax;
    }

    public double round(double v) {
        return Math.round(v / step) * step;
    }
}
