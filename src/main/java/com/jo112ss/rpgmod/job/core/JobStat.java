package com.jo112ss.rpgmod.job.core;

/**
 * Stats a job, its level and its skill panel can raise. The first group maps to vanilla attributes;
 * the rest are used by the mod's own systems (critical hits, mana, skills). Attack and magic attack
 * are flat values added to the weapon's (skills deal a percentage of the total).
 */
public enum JobStat {
    MAX_HEALTH("max_health", "体", false),
    ATTACK_DAMAGE("attack_damage", "攻", false),
    ARMOR("armor", "防", false),
    MOVEMENT_SPEED("movement_speed", "速", true),
    ATTACK_SPEED("attack_speed", "敏", true),
    LUCK("luck", "運", false),
    CRIT_CHANCE("crit_chance", "会", true),
    MAX_MANA("max_mana", "MP", false),
    MANA_REGEN("mana_regen", "回", false),
    SKILL_POWER("skill_power", "技", true),
    COOLDOWN_REDUCTION("cooldown_reduction", "短", true),
    CAST_SPEED("cast_speed", "詠", true),
    /** Flat magic attack added to the staff's (casters' skills scale with it). */
    MAGIC_ATTACK("magic_attack", "魔", false);

    public final String id;
    /** Single character drawn on panel nodes. */
    public final String glyph;
    /** Stored as a percentage (5 = +5%). */
    public final boolean percent;

    JobStat(String id, String glyph, boolean percent) {
        this.id = id;
        this.glyph = glyph;
        this.percent = percent;
    }

    /**
     * Amount a normal (inner) panel node gives; middle rings give 1.5x, outer rings 2x. Half of what
     * it was on the old 91-cell panel with a level 50 cap: there are now about twice the cells and
     * twice the points, so the most a fully built character reaches stays the same.
     */
    public double nodeAmount() {
        return switch (this) {
            case MAX_HEALTH -> 2;
            case ATTACK_DAMAGE -> 0.4;
            case ARMOR -> 1;
            case MOVEMENT_SPEED -> 1;
            case ATTACK_SPEED -> 1.5;
            case LUCK -> 0.5;
            case CRIT_CHANCE -> 1;
            case MAX_MANA -> 5;
            case MANA_REGEN -> 0.1;
            case SKILL_POWER -> 2;
            case COOLDOWN_REDUCTION -> 1.5;
            case CAST_SPEED -> 2;
            case MAGIC_ATTACK -> 0.4;
        };
    }

    public static JobStat byId(String id) {
        for (JobStat s : values()) if (s.id.equals(id)) return s;
        return null;
    }
}
