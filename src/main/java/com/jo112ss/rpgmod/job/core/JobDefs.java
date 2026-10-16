package com.jo112ss.rpgmod.job.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure data for the eight jobs and their (placeholder) skills. Everything a job needs that does not
 * touch Minecraft lives here so it can be tested and tuned in isolation. Weapons per job are in
 * {@code JobType} because they reference items.
 */
public final class JobDefs {
    private JobDefs() {}

    public record Job(
            String id,
            int color,
            String glyph,
            /** The three stats its skill panel is made of. */
            List<JobStat> panelStats,
            /** Flat bonuses from picking the job. */
            Map<JobStat, Double> base,
            /** Bonuses gained per level above 1. */
            Map<JobStat, Double> perLevel,
            List<String> actives,
            List<String> ultimates,
            List<String> spells
    ) {
        public boolean usesSpells() {
            return !spells.isEmpty();
        }
    }

    public static final Map<String, SkillDef> SKILLS = new LinkedHashMap<>();
    /** Charge (hold) time in seconds for spells cast from a staff. */
    private static final Map<String, Double> CAST_TIMES = Map.of(
            "magic_bolt", 0.6, "flame_burst", 1.2, "frost_nova", 1.0, "thunder", 1.6, "arcane_shield", 0.8);
    public static final Map<String, Job> JOBS = new LinkedHashMap<>();

    /** Mana every job starts with before job / level / panel bonuses. */
    public static final double BASE_MANA = 100;
    public static final double BASE_MANA_REGEN = 1.0;

    // ------------------------------------------------------------------ definitions

    static {
        // ---- Fighter: heavy melee
        job("fighter", 0xD9534F, "闘", List.of(JobStat.MAX_HEALTH, JobStat.ATTACK_DAMAGE, JobStat.ARMOR),
                stats(JobStat.MAX_HEALTH, 4, JobStat.ATTACK_DAMAGE, 5), stats(JobStat.MAX_HEALTH, 0.3, JobStat.ATTACK_DAMAGE, 0.2),
                List.of(
                        skill("power_slash", SkillKind.ACTIVE, SkillEffect.SWEEP, 6, 15, 7, 3.5, 0, List.of(), 0, "斬"),
                        skill("charge", SkillKind.ACTIVE, SkillEffect.DASH, 8, 10, 1.6, 0, 0, List.of(), 0, "突"),
                        skill("war_cry", SkillKind.ACTIVE, SkillEffect.BUFF, 20, 25, 0, 8, 10, List.of("minecraft:strength"), 0, "鬨"),
                        skill("whirlwind", SkillKind.ACTIVE, SkillEffect.WHIRL, 10, 20, 5, 4, 0, List.of(), 0, "旋")),
                List.of(
                        skill("berserk", SkillKind.ULTIMATE, SkillEffect.BUFF, 90, 50, 0, 0, 15, List.of("minecraft:strength", "minecraft:speed"), 1, "狂"),
                        skill("earthshatter", SkillKind.ULTIMATE, SkillEffect.WHIRL, 90, 60, 14, 6, 4, List.of("minecraft:slowness"), 1, "砕")),
                List.of());
        // ---- Ranger: bows
        job("ranger", 0x5CB85C, "弓", List.of(JobStat.ATTACK_DAMAGE, JobStat.MOVEMENT_SPEED, JobStat.CRIT_CHANCE),
                stats(JobStat.MOVEMENT_SPEED, 5, JobStat.CRIT_CHANCE, 3), stats(JobStat.CRIT_CHANCE, 0.1, JobStat.ATTACK_DAMAGE, 0.2),
                List.of(
                        skill("multishot", SkillKind.ACTIVE, SkillEffect.SHOT, 6, 15, 3, 0, 0, List.of(), 0, "連"),
                        skill("backstep", SkillKind.ACTIVE, SkillEffect.BACKSTEP, 7, 10, 1.4, 0, 0, List.of(), 0, "退"),
                        skill("hunters_mark", SkillKind.ACTIVE, SkillEffect.DEBUFF, 15, 20, 0, 12, 10, List.of("minecraft:glowing", "minecraft:weakness"), 0, "印"),
                        skill("snare", SkillKind.ACTIVE, SkillEffect.DEBUFF, 12, 20, 2, 5, 5, List.of("minecraft:slowness"), 2, "罠")),
                List.of(
                        skill("arrow_rain", SkillKind.ULTIMATE, SkillEffect.SHOT, 90, 50, 12, 0, 0, List.of(), 0, "雨"),
                        skill("eagle_eye", SkillKind.ULTIMATE, SkillEffect.BUFF, 90, 40, 0, 0, 15, List.of("minecraft:strength", "minecraft:speed", "minecraft:night_vision"), 1, "鷹")),
                List.of());
        // ---- Paladin: front line, protects allies
        job("paladin", 0xF0C040, "聖", List.of(JobStat.MAX_HEALTH, JobStat.ARMOR, JobStat.SKILL_POWER),
                stats(JobStat.MAX_HEALTH, 6, JobStat.ARMOR, 2), stats(JobStat.MAX_HEALTH, 0.4, JobStat.ARMOR, 0.05),
                List.of(
                        skill("shield_bash", SkillKind.ACTIVE, SkillEffect.SWEEP, 7, 15, 4, 3, 3, List.of("minecraft:slowness"), 1, "打"),
                        skill("holy_guard", SkillKind.ACTIVE, SkillEffect.BUFF, 20, 25, 0, 6, 8, List.of("minecraft:resistance"), 0, "護"),
                        skill("smite", SkillKind.ACTIVE, SkillEffect.SWEEP, 8, 20, 7, 3.5, 0, List.of(), 0, "裁"),
                        skill("lay_on_hands", SkillKind.ACTIVE, SkillEffect.HEAL, 15, 25, 6, 5, 0, List.of(), 0, "手")),
                List.of(
                        skill("divine_aegis", SkillKind.ULTIMATE, SkillEffect.BUFF, 100, 60, 0, 8, 12, List.of("minecraft:resistance", "minecraft:absorption"), 1, "盾"),
                        skill("judgement", SkillKind.ULTIMATE, SkillEffect.WHIRL, 90, 60, 16, 6, 0, List.of(), 0, "審")),
                List.of());
        // ---- Wizard: spells on the staff, skills 1/2 for mana and mobility
        job("wizard", 0x7B68EE, "魔", List.of(JobStat.MAX_MANA, JobStat.SKILL_POWER, JobStat.MANA_REGEN, JobStat.CAST_SPEED),
                stats(JobStat.MAX_MANA, 50, JobStat.MANA_REGEN, 0.5), stats(JobStat.MAX_MANA, 2, JobStat.SKILL_POWER, 0.3),
                List.of(
                        skill("mana_surge", SkillKind.ACTIVE, SkillEffect.MANA, 20, 0, 40, 0, 0, List.of(), 0, "充"),
                        skill("blink", SkillKind.ACTIVE, SkillEffect.BLINK, 8, 15, 0, 8, 0, List.of(), 0, "瞬"),
                        skill("mana_barrier", SkillKind.ACTIVE, SkillEffect.BUFF, 25, 30, 0, 0, 10, List.of("minecraft:absorption"), 1, "障"),
                        skill("arcane_focus", SkillKind.ACTIVE, SkillEffect.MANA, 30, 0, 25, 6, 0, List.of(), 0, "集")),
                List.of(
                        skill("meteor", SkillKind.ULTIMATE, SkillEffect.WHIRL, 100, 80, 20, 6, 5, List.of(), 0, "隕"),
                        skill("time_warp", SkillKind.ULTIMATE, SkillEffect.DEBUFF, 90, 60, 0, 10, 8, List.of("minecraft:slowness", "minecraft:weakness"), 2, "遅")),
                List.of(
                        skill("magic_bolt", SkillKind.SPELL, SkillEffect.BOLT, 1.2, 8, 10, 28, 0, List.of(), 0, "弾"),
                        skill("flame_burst", SkillKind.SPELL, SkillEffect.WHIRL, 6, 20, 10, 4.5, 4, List.of(), 0, "炎"),
                        skill("frost_nova", SkillKind.SPELL, SkillEffect.DEBUFF, 8, 20, 7, 5.5, 6, List.of("minecraft:slowness"), 2, "氷"),
                        skill("thunder", SkillKind.SPELL, SkillEffect.LIGHTNING, 10, 30, 16, 28, 0, List.of(), 0, "雷"),
                        skill("arcane_shield", SkillKind.SPELL, SkillEffect.BUFF, 20, 25, 0, 0, 15, List.of("minecraft:absorption", "minecraft:resistance"), 1, "護")));
        // ---- Healer: heals and buffs
        job("healer", 0x5BC0DE, "癒", List.of(JobStat.MAX_MANA, JobStat.SKILL_POWER, JobStat.MAX_HEALTH),
                stats(JobStat.MAX_MANA, 40, JobStat.SKILL_POWER, 10), stats(JobStat.SKILL_POWER, 0.4, JobStat.MAX_MANA, 1.5),
                List.of(
                        skill("heal", SkillKind.ACTIVE, SkillEffect.HEAL, 6, 15, 6, 2, 0, List.of(), 0, "癒"),
                        skill("group_heal", SkillKind.ACTIVE, SkillEffect.HEAL, 12, 30, 4, 8, 0, List.of(), 0, "群"),
                        skill("blessing", SkillKind.ACTIVE, SkillEffect.BUFF, 20, 25, 0, 8, 8, List.of("minecraft:regeneration"), 0, "祝"),
                        skill("mana_gift", SkillKind.ACTIVE, SkillEffect.MANA, 25, 0, 30, 8, 0, List.of(), 0, "恵")),
                List.of(
                        skill("sanctuary", SkillKind.ULTIMATE, SkillEffect.HEAL, 100, 60, 20, 10, 0, List.of(), 0, "域"),
                        skill("guardian_light", SkillKind.ULTIMATE, SkillEffect.BUFF, 100, 60, 0, 10, 15, List.of("minecraft:absorption", "minecraft:resistance", "minecraft:regeneration"), 1, "光")),
                List.of());
        // ---- Assassin: daggers, burst and stealth
        job("assassin", 0x8E44AD, "影", List.of(JobStat.CRIT_CHANCE, JobStat.ATTACK_SPEED, JobStat.MOVEMENT_SPEED),
                stats(JobStat.CRIT_CHANCE, 5, JobStat.MOVEMENT_SPEED, 5), stats(JobStat.CRIT_CHANCE, 0.15, JobStat.ATTACK_SPEED, 0.2),
                List.of(
                        skill("shadow_step", SkillKind.ACTIVE, SkillEffect.BLINK, 7, 15, 0, 6, 0, List.of(), 0, "影"),
                        skill("backstab", SkillKind.ACTIVE, SkillEffect.SWEEP, 6, 15, 9, 2.5, 0, List.of(), 0, "急"),
                        skill("smoke_bomb", SkillKind.ACTIVE, SkillEffect.STEALTH, 18, 20, 0, 0, 5, List.of("minecraft:speed"), 1, "煙"),
                        skill("venom", SkillKind.ACTIVE, SkillEffect.DEBUFF, 10, 15, 2, 3, 6, List.of("minecraft:poison"), 1, "毒")),
                List.of(
                        skill("assassinate", SkillKind.ULTIMATE, SkillEffect.SWEEP, 90, 50, 25, 3, 0, List.of(), 0, "殺"),
                        skill("shadow_dance", SkillKind.ULTIMATE, SkillEffect.STEALTH, 90, 50, 0, 0, 10, List.of("minecraft:strength", "minecraft:speed"), 1, "舞")),
                List.of());
        // ---- Enchanter: buffs allies, weakens enemies
        job("enchanter", 0xE67EB5, "付", List.of(JobStat.SKILL_POWER, JobStat.COOLDOWN_REDUCTION, JobStat.MAX_MANA),
                stats(JobStat.COOLDOWN_REDUCTION, 5, JobStat.MAX_MANA, 30), stats(JobStat.SKILL_POWER, 0.3, JobStat.COOLDOWN_REDUCTION, 0.1),
                List.of(
                        skill("empower", SkillKind.ACTIVE, SkillEffect.BUFF, 18, 25, 0, 8, 10, List.of("minecraft:strength"), 0, "強"),
                        skill("haste", SkillKind.ACTIVE, SkillEffect.BUFF, 18, 25, 0, 8, 10, List.of("minecraft:speed", "minecraft:haste"), 0, "速"),
                        skill("weaken", SkillKind.ACTIVE, SkillEffect.DEBUFF, 15, 20, 0, 8, 8, List.of("minecraft:weakness"), 0, "弱"),
                        skill("mana_link", SkillKind.ACTIVE, SkillEffect.MANA, 25, 0, 20, 8, 0, List.of(), 0, "繋")),
                List.of(
                        skill("grand_enchant", SkillKind.ULTIMATE, SkillEffect.BUFF, 100, 60, 0, 10, 15, List.of("minecraft:strength", "minecraft:resistance", "minecraft:speed"), 1, "極"),
                        skill("hex", SkillKind.ULTIMATE, SkillEffect.DEBUFF, 90, 60, 4, 10, 10, List.of("minecraft:weakness", "minecraft:slowness"), 1, "呪")),
                List.of());
        // ---- Gambler: random results
        job("gambler", 0x1ABC9C, "賭", List.of(JobStat.CRIT_CHANCE, JobStat.LUCK, JobStat.COOLDOWN_REDUCTION),
                stats(JobStat.LUCK, 2, JobStat.CRIT_CHANCE, 5), stats(JobStat.LUCK, 0.05, JobStat.CRIT_CHANCE, 0.1),
                List.of(
                        skill("coin_toss", SkillKind.ACTIVE, SkillEffect.GAMBLE, 8, 10, 1, 6, 6, List.of(), 0, "銭"),
                        skill("lucky_shot", SkillKind.ACTIVE, SkillEffect.SHOT, 6, 10, 1, 0, 0, List.of(), 0, "幸"),
                        skill("dice_roll", SkillKind.ACTIVE, SkillEffect.GAMBLE, 12, 15, 1.5, 6, 8, List.of(), 0, "骰"),
                        skill("all_in", SkillKind.ACTIVE, SkillEffect.GAMBLE, 20, 25, 2.5, 8, 10, List.of(), 0, "賭")),
                List.of(
                        skill("jackpot", SkillKind.ULTIMATE, SkillEffect.GAMBLE, 90, 50, 4, 10, 15, List.of(), 0, "当"),
                        skill("roulette", SkillKind.ULTIMATE, SkillEffect.GAMBLE, 90, 50, 3, 10, 12, List.of(), 0, "輪")),
                List.of());
    }

    // ------------------------------------------------------------------ helpers

    private static void job(String id, int color, String glyph, List<JobStat> panelStats, Map<JobStat, Double> base,
                            Map<JobStat, Double> perLevel, List<SkillDef> actives, List<SkillDef> ultimates, List<SkillDef> spells) {
        JOBS.put(id, new Job(id, color, glyph, panelStats, base, perLevel,
                ids(actives), ids(ultimates), ids(spells)));
    }

    private static List<String> ids(List<SkillDef> defs) {
        List<String> out = new ArrayList<>();
        for (SkillDef d : defs) out.add(d.id());
        return Collections.unmodifiableList(out);
    }

    private static Map<JobStat, Double> stats(Object... kv) {
        Map<JobStat, Double> m = new EnumMap<>(JobStat.class);
        for (int i = 0; i < kv.length; i += 2) m.put((JobStat) kv[i], ((Number) kv[i + 1]).doubleValue());
        return Collections.unmodifiableMap(m);
    }

    /** Registers a skill for the job currently being defined (the job id is filled in by {@link #job}). */
    private static SkillDef skill(String id, SkillKind kind, SkillEffect effect, double cooldown, double mana, double power,
                                  double radius, double duration, List<String> effects, int amplifier, String glyph) {
        SkillDef def = new SkillDef(id, "", kind, effect, cooldown, mana, power, radius, duration, effects, amplifier, glyph, 0, CAST_TIMES.getOrDefault(id, 0.0));
        SKILLS.put(id, def);
        return def;
    }

    static {
        // fill in owner job and icon colour now that every job exists
        for (Job j : JOBS.values()) {
            List<String> all = new ArrayList<>(j.actives());
            all.addAll(j.ultimates());
            all.addAll(j.spells());
            for (String s : all) {
                SkillDef d = SKILLS.get(s);
                int color = d.kind() == SkillKind.ULTIMATE ? 0xE8B33A : d.kind() == SkillKind.SPELL ? 0x7B68EE : j.color();
                SKILLS.put(s, new SkillDef(d.id(), j.id(), d.kind(), d.effect(), d.cooldown(), d.mana(), d.power(), d.radius(),
                        d.duration(), d.effects(), d.amplifier(), d.glyph(), color, d.castTime()));
            }
        }
    }

    public static Job job(String id) {
        return id == null ? null : JOBS.get(id);
    }

    public static SkillDef skill(String id) {
        return id == null ? null : SKILLS.get(id);
    }
}
