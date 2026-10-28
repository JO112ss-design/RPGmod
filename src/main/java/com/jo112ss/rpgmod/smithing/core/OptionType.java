package com.jo112ss.rpgmod.smithing.core;

/**
 * Random options. Weapons and armour draw from separate pools (an option may be in both with
 * different ranges). Values are rolled uniformly in the pool's range and rounded to {@link #step}.
 * Percent options store the percentage (5.0 = +5%).
 *
 * <p>Armour comes in two kinds with their own pools: metal armour (and shields) is for fighting up
 * close - it can roll attack, attack speed and boss damage - and robes are the casters' - magic
 * power, cast speed and MP. Health, critical chance and damage and the defensive options are in both. A piece's own kind
 * of option is twice as likely as a shared one.
 */
public enum OptionType {
    //                        id                     step  percent  weapon range   armor range  armour kind
    ATTACK_DAMAGE("attack_damage", 0.1, true, 3, 8, 2, 5, Side.MELEE),
    ATTACK_SPEED("attack_speed", 0.1, true, 3, 8, 2, 5, Side.MELEE),
    // crits work on magic too: robes roll them as well
    CRIT_CHANCE("crit_chance", 0.1, true, 2, 5, 1, 3, Side.BOTH),
    CRIT_DAMAGE("crit_damage", 0.1, true, 5, 15, 3, 8, Side.BOTH),
    BOSS_DAMAGE("boss_damage", 0.1, true, 4, 10, 2, 5, Side.MELEE),
    // max health is a percentage now that players reach hundreds (old flat 1-2 options read as 1-2%)
    MAX_HEALTH("max_health", 1, true, 2, 5, 2, 5, Side.BOTH),
    ARMOR("armor", 1, false, 1, 2, 1, 2, Side.BOTH),
    MOVEMENT_SPEED("movement_speed", 0.1, true, 2, 5, 1, 3, Side.BOTH),
    DURABILITY("durability", 1, true, 10, 25, 10, 25, Side.BOTH),
    EXPERIENCE("experience", 0.1, true, 5, 12, 3, 8, Side.BOTH),
    KNOCKBACK_RESISTANCE("knockback_resistance", 0.1, true, 5, 15, 3, 8, Side.BOTH),
    ARMOR_TOUGHNESS("armor_toughness", 0.1, false, NaN(), NaN(), 0.5, 1.5, Side.BOTH),
    DAMAGE_REDUCTION("damage_reduction", 0.1, true, NaN(), NaN(), 2, 4, Side.BOTH),
    BOSS_RESISTANCE("boss_resistance", 0.1, true, NaN(), NaN(), 3, 8, Side.BOTH),
    // magic options: weapons only roll them on magic staffs (see OptionRoller), armour only on robes
    MAGIC_POWER("magic_power", 0.1, true, 5, 12, 2, 5, Side.MAGIC),
    CAST_SPEED("cast_speed", 0.1, true, 5, 12, 2, 5, Side.MAGIC),
    // mana: every weapon can roll them, armour only robes
    MAX_MANA("max_mana", 1, false, 10, 25, 5, 12, Side.MAGIC),
    MANA_REGEN("mana_regen", 0.1, true, 8, 20, 4, 10, Side.MAGIC),
    // gems only (forging never rolls it): flat attack, on the x3 attack scale
    ATTACK_FLAT("attack_flat", 1, false, NaN(), NaN(), NaN(), NaN(), Side.BOTH),
    // gems only: flat magic attack, the casters' counterpart of ATTACK_FLAT
    MAGIC_FLAT("magic_flat", 1, false, NaN(), NaN(), NaN(), NaN(), Side.BOTH);

    /** The option pools forging rolls from: weapons, metal armour and shields, robes. */
    public enum Pool { WEAPON, ARMOR, ROBE }

    /** Which kind of armour an option is rolled on. */
    public enum Side { BOTH, MELEE, MAGIC }

    public final String id;
    public final double step;
    public final boolean percent;
    public final double weaponMin, weaponMax, armorMin, armorMax;
    public final Side side;

    OptionType(String id, double step, boolean percent, double weaponMin, double weaponMax, double armorMin, double armorMax, Side side) {
        this.side = side;
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

    /** Whether forging can roll this option in a pool (weapons: the staff rule is the roller's). */
    public boolean inPool(Pool pool) {
        if (pool == Pool.WEAPON) return inPool(false);
        if (!inPool(true)) return false;
        return side == Side.BOTH || side == (pool == Pool.ROBE ? Side.MAGIC : Side.MELEE);
    }

    /** Draw weight in a pool: an armour piece's own kind of option is twice as likely as a shared one. */
    public int weight(Pool pool) {
        return pool != Pool.WEAPON && side != Side.BOTH ? 2 : 1;
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
