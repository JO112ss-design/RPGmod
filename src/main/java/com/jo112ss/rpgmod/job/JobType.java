package com.jo112ss.rpgmod.job;

import com.jo112ss.rpgmod.item.WeaponType;
import com.jo112ss.rpgmod.job.core.JobDefs;
import java.util.List;

/**
 * The twelve base jobs, the six hybrid jobs, and the weapon types that activate their skills. Stats and skills are in
 * {@link JobDefs}; this enum only adds what needs Minecraft types.
 */
public enum JobType {
    FIGHTER("fighter", List.of(WeaponType.SWORD, WeaponType.HAMMER)),
    RANGER("ranger", List.of(WeaponType.BOW)),
    PALADIN("paladin", List.of(WeaponType.MACE, WeaponType.SWORD)),
    WIZARD("wizard", List.of(WeaponType.STAFF)),
    HEALER("healer", List.of(WeaponType.STAFF, WeaponType.MACE)),
    ASSASSIN("assassin", List.of(WeaponType.DAGGER)),
    ENCHANTER("enchanter", List.of(WeaponType.STAFF, WeaponType.DAGGER)),
    GAMBLER("gambler", List.of(WeaponType.DAGGER, WeaponType.BOW)),
    PIRATE("pirate", List.of(WeaponType.GUNBLADE)),
    LANCER("lancer", List.of(WeaponType.SPEAR)),
    MONK("monk", List.of(WeaponType.KNUCKLE)),
    BARD("bard", List.of(WeaponType.LUTE)),
    // hybrid jobs: open once both of their base jobs are mastered (see JobDefs.HYBRIDS)
    SPELLBLADE("spellblade", List.of(WeaponType.SWORD), true),
    QI_MASTER("qi_master", List.of(WeaponType.KNUCKLE), true),
    SENTINEL("sentinel", List.of(WeaponType.SPEAR), true),
    SPELLSINGER("spellsinger", List.of(WeaponType.LUTE), true),
    SHADOW_HUNTER("shadow_hunter", List.of(WeaponType.BOW, WeaponType.DAGGER), true),
    TREASURE_HUNTER("treasure_hunter", List.of(WeaponType.GUNBLADE), true);

    public final String id;
    public final List<WeaponType> weapons;
    /** A hybrid job (two mastered base jobs open it); false for the twelve base jobs. */
    public final boolean hybrid;

    JobType(String id, List<WeaponType> weapons) {
        this(id, weapons, false);
    }

    JobType(String id, List<WeaponType> weapons, boolean hybrid) {
        this.id = id;
        this.weapons = weapons;
        this.hybrid = hybrid;
    }

    /** The base jobs (the first choice, mastery, advanced jobs) and the hybrid jobs, in order. */
    public static final List<JobType> BASE = java.util.Arrays.stream(values()).filter(t -> !t.hybrid).toList();
    public static final List<JobType> HYBRIDS = java.util.Arrays.stream(values()).filter(t -> t.hybrid).toList();

    public JobDefs.Job def() {
        return JobDefs.job(id);
    }

    public int color() {
        return def().color();
    }

    public static JobType byId(String id) {
        for (JobType j : values()) if (j.id.equals(id)) return j;
        return null;
    }
}
