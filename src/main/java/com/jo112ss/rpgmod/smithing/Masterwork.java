package com.jo112ss.rpgmod.smithing;

import com.jo112ss.rpgmod.item.WeaponType;
import com.jo112ss.rpgmod.job.JobType;
import com.jo112ss.rpgmod.job.core.JobDefs;
import com.jo112ss.rpgmod.job.core.SkillBoost;
import com.jo112ss.rpgmod.job.core.SkillDef;
import com.jo112ss.rpgmod.registry.ModRegistries;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * "Masterwork" (会心の出来): one forged weapon in five, whatever its quality, also boosts one skill of
 * a job that fights with that kind of weapon - like one of the skill's boost cells, 1 to 1.5 times
 * as much. It works while the weapon is in the main hand. Advanced jobs' skills only come up when
 * the maker is level 100 or more (a boss's unique weapon: the boss's level), and a skill of a job the
 * holder has never opened shows as "???" (see {@link com.jo112ss.rpgmod.job.JobData#opened}).
 */
public record Masterwork(String skill, String kind, double amount) {
    /** Chance a forged weapon (not armour, robes or shields) comes out a masterwork. */
    public static final double CHANCE = 0.2;

    public static final Codec<Masterwork> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("skill").forGetter(Masterwork::skill),
            Codec.STRING.fieldOf("kind").forGetter(Masterwork::kind),
            Codec.DOUBLE.fieldOf("amount").forGetter(Masterwork::amount)
    ).apply(i, Masterwork::new));

    public SkillBoost boost() {
        try {
            return SkillBoost.valueOf(kind);
        } catch (IllegalArgumentException e) {
            return SkillBoost.POWER;
        }
    }

    /** The jobs that fight with this weapon: base and hybrid jobs, and the base jobs' advanced jobs. */
    public static List<String> jobsFor(WeaponType type) {
        return jobsFor(type, true);
    }

    /** As above; without the advanced jobs when {@code advanced} is false. */
    public static List<String> jobsFor(WeaponType type, boolean advanced) {
        Set<String> jobs = new LinkedHashSet<>();
        for (JobType t : JobType.values()) {
            if (!t.weapons.contains(type)) continue;
            jobs.add(t.id);
            String adv = advanced ? JobDefs.ADVANCED.get(t.id) : null;
            if (adv != null) jobs.add(adv);
        }
        return new ArrayList<>(jobs);
    }

    /** Every skill a masterwork of this weapon could boost. */
    public static List<String> skillsFor(WeaponType type) {
        return skillsFor(type, true);
    }

    public static List<String> skillsFor(WeaponType type, boolean advanced) {
        Set<String> skills = new LinkedHashSet<>();
        for (String id : jobsFor(type, advanced)) {
            JobDefs.Job job = JobDefs.job(id);
            if (job == null) continue;
            skills.addAll(job.actives());
            skills.addAll(job.spells());
            skills.addAll(job.ultimates());
        }
        skills.removeIf(s -> s.equals("perform") || s.equals("spell_perform") || JobDefs.skill(s) == null);
        return new ArrayList<>(skills);
    }

    /**
     * A masterwork for this weapon made at this level (the maker's, or the boss's for a unique weapon),
     * or null when it has none (20%: see {@link #CHANCE}). Below level 100 only base and hybrid jobs' skills.
     */
    public static Masterwork maybe(WeaponType type, int level, Random rnd) {
        if (type.optionPool() != com.jo112ss.rpgmod.smithing.core.OptionType.Pool.WEAPON || rnd.nextDouble() >= CHANCE) return null;
        return roll(type, advancedAt(level), rnd);
    }

    /** Level from which a masterwork can be for an advanced job's skill. */
    public static boolean advancedAt(int level) {
        return level >= com.jo112ss.rpgmod.job.core.LevelCurve.ADVANCED_AT;
    }

    public static Masterwork roll(WeaponType type, boolean advanced, Random rnd) {
        List<String> skills = skillsFor(type, advanced);
        if (skills.isEmpty()) return null;
        SkillDef def = JobDefs.skill(skills.get(rnd.nextInt(skills.size())));
        List<SkillBoost.Cell> cells = SkillBoost.cellsFor(def);
        SkillBoost.Cell cell = cells.get(rnd.nextInt(cells.size()));
        double amount = Math.round(cell.amount() * (1 + rnd.nextDouble() * 0.5) * 100) / 100.0;
        return new Masterwork(def.id(), cell.kind().name(), amount);
    }

    /** "Fireball (Wizard) Power +12%". */
    public static net.minecraft.network.chat.Component describe(Masterwork m) {
        SkillDef def = JobDefs.skill(m.skill);
        String job = def == null ? "" : def.job();
        SkillBoost b = m.boost();
        String key = def == null ? "rpgmod.boost." + m.kind.toLowerCase(java.util.Locale.ROOT) : b.langKey(def);
        return net.minecraft.network.chat.Component.translatable("rpgmod.masterwork.line", net.minecraft.network.chat.Component.translatable("rpgmod.skill." + m.skill),
                net.minecraft.network.chat.Component.translatable("rpgmod.job." + job),
                net.minecraft.network.chat.Component.translatable(key, Math.round(m.amount * 100)));
    }

    /** The skill's job, for the "???" check: has this player ever opened it? */
    public static boolean openedBy(Masterwork m, com.jo112ss.rpgmod.job.JobData d) {
        SkillDef def = JobDefs.skill(m.skill);
        return def == null || d.opened(def.job());
    }

    /** What the weapon in this player's main hand adds to one boost of one skill. */
    public static double boost(Player p, String skill, SkillBoost kind) {
        return boost(p.getMainHandItem(), skill, kind);
    }

    public static double boost(ItemStack weapon, String skill, SkillBoost kind) {
        if (weapon.isEmpty()) return 0;
        ForgedData d = weapon.get(ModRegistries.FORGED_DATA.get());
        if (d == null || d.masterwork().isEmpty()) return 0;
        Masterwork m = d.masterwork().get();
        return m.skill.equals(skill) && m.boost() == kind ? m.amount : 0;
    }
}
