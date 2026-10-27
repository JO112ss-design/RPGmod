package com.jo112ss.rpgmod.job.core;

import java.util.List;
import java.util.Set;

/**
 * What a skill's boost cell on the skill panel improves. Every skill, spell and ultimate gets its own
 * cells right next to it on the panel (two; one for an ultimate), learnable once the skill is.
 * <ul>
 * <li>damage skills: power +10% and cooldown -10%</li>
 * <li>rapid and many-hit skills (combo, flurry, the magic bolt...): power +5% and mana -15% - their
 * damage comes from being used again and again, so a cooldown cut or a full 10% would add too much</li>
 * <li>heals: healing +10% and cooldown -10%</li>
 * <li>buffs and debuffs: duration +15% and cooldown -10% (no duration: cooldown -10% and mana -15%)</li>
 * <li>ultimates: one cell - power +10% (+5% for the many-hit ones), or duration +15%</li>
 * </ul>
 */
public enum SkillBoost {
    POWER, COOLDOWN, MANA, DURATION;

    public static final double POWER_STEP = 0.10, RAPID_POWER_STEP = 0.05, COOLDOWN_STEP = 0.10, MANA_STEP = 0.15, DURATION_STEP = 0.15;

    /** One boost cell: what it improves and by how much (a share: 0.1 = 10%). */
    public record Cell(SkillBoost kind, double amount) {}

    /**
     * Skills that hit many times or can be used again almost at once: smaller power steps and mana
     * instead of cooldown.
     */
    public static final Set<String> RAPID = Set.of(
            "combo", "multishot", "rapid_thrust", "flurry", "scattershot", "coin_shot", "arcane_missiles", "spiral_lance", "triple_whirl",
            "whirlwind", "quick_draw", "magic_bolt", "holy_bolt", "russian_roulette", "twin_cannon", "spear_wall",
            "hundred_fists", "spear_rain", "peerless", "dead_eye", "blooming_fists", "elemental_burst");

    public static boolean rapid(SkillDef def) {
        return RAPID.contains(def.id());
    }

    /** The boost cells a skill gets. */
    public static List<Cell> cellsFor(SkillDef def) {
        String kind = JobDefs.powerKind(def);
        boolean rapid = rapid(def);
        if (def.kind() == SkillKind.ULTIMATE) {
            if (kind != null) return List.of(new Cell(POWER, rapid ? RAPID_POWER_STEP : POWER_STEP));
            return List.of(def.duration() > 0 ? new Cell(DURATION, DURATION_STEP) : new Cell(COOLDOWN, COOLDOWN_STEP));
        }
        if ("damage".equals(kind)) {
            return rapid ? List.of(new Cell(POWER, RAPID_POWER_STEP), new Cell(MANA, MANA_STEP))
                    : List.of(new Cell(POWER, POWER_STEP), new Cell(COOLDOWN, COOLDOWN_STEP));
        }
        if ("heal".equals(kind)) return List.of(new Cell(POWER, POWER_STEP), new Cell(COOLDOWN, COOLDOWN_STEP));
        if (def.duration() > 0) return List.of(new Cell(DURATION, DURATION_STEP), new Cell(COOLDOWN, COOLDOWN_STEP));
        return List.of(new Cell(COOLDOWN, COOLDOWN_STEP), new Cell(MANA, MANA_STEP));
    }

    /** Lang key of the line shown for this boost on a skill ({@code rpgmod.boost.<kind>}, heals say "healing"). */
    public String langKey(SkillDef def) {
        if (this == POWER && "heal".equals(JobDefs.powerKind(def))) return "rpgmod.boost.heal";
        return "rpgmod.boost." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
