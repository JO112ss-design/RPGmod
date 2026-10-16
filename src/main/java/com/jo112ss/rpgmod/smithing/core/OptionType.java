package com.jo112ss.rpgmod.smithing.core;

/**
 * Random options a forged weapon can receive. Values are rolled uniformly inside [min,max] and
 * stored already rounded to {@link #step}. Percent options store the percentage (5.0 = +5%).
 */
public enum OptionType {
    ATTACK_DAMAGE("attack_damage", 3, 8, 0.1, true),
    ATTACK_SPEED("attack_speed", 3, 8, 0.1, true),
    CRIT_CHANCE("crit_chance", 2, 5, 0.1, true),
    CRIT_DAMAGE("crit_damage", 5, 15, 0.1, true),
    ARMOR_PENETRATION("armor_penetration", 3, 8, 0.1, true),
    BOSS_DAMAGE("boss_damage", 4, 10, 0.1, true),
    MAX_HEALTH("max_health", 1, 2, 1, false),
    ARMOR("armor", 1, 2, 1, false),
    MOVEMENT_SPEED("movement_speed", 2, 5, 0.1, true),
    DURABILITY("durability", 10, 25, 1, true),
    EXPERIENCE("experience", 5, 12, 0.1, true),
    KNOCKBACK_RESISTANCE("knockback_resistance", 5, 15, 0.1, true);

    public final String id;
    public final double min;
    public final double max;
    public final double step;
    public final boolean percent;

    OptionType(String id, double min, double max, double step, boolean percent) {
        this.id = id;
        this.min = min;
        this.max = max;
        this.step = step;
        this.percent = percent;
    }

    public static OptionType byId(String id) {
        for (OptionType t : values()) if (t.id.equals(id)) return t;
        return null;
    }

    public double clamp(double v) {
        return Math.max(min, Math.min(max, v));
    }

    public double round(double v) {
        return Math.round(v / step) * step;
    }
}
