package com.jo112ss.rpgmod.job;

import com.jo112ss.rpgmod.item.WeaponType;
import com.jo112ss.rpgmod.job.core.JobDefs;
import java.util.List;

/**
 * The twelve jobs and the weapon types that activate their skills. Stats and skills are in
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
    BARD("bard", List.of(WeaponType.LUTE));

    public final String id;
    public final List<WeaponType> weapons;

    JobType(String id, List<WeaponType> weapons) {
        this.id = id;
        this.weapons = weapons;
    }

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
