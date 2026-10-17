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
            "magic_bolt", 0.6, "flame_burst", 1.2, "frost_nova", 1.0, "thunder", 1.6, "arcane_shield", 0.8,
            "arcane_missiles", 0.8, "blizzard", 1.4, "gravity", 1.2);
    public static final Map<String, Job> JOBS = new LinkedHashMap<>();

    /** Jobs whose skills scale with magic attack (staff + own magic) instead of attack (weapon + own). */
    public static final java.util.Set<String> MAGIC_JOBS = java.util.Set.of("wizard", "healer", "enchanter");

    /** Spells, and every skill of the caster jobs, use magic attack. */
    public static boolean isMagic(SkillDef def) {
        return def.kind() == SkillKind.SPELL || MAGIC_JOBS.contains(def.job());
    }

    /** What the skill's power number means: "damage" / "heal" (a ratio of attack), or null. */
    public static String powerKind(SkillDef def) {
        if (def.power() <= 0) return null;
        return switch (def.effect()) {
            case HEAL -> "heal";
            case SWEEP, WHIRL, DASH, SHOT, DEBUFF, BOLT, LIGHTNING -> "damage";
            default -> null;
        };
    }

    /** Mana every job starts with before job / level / panel bonuses. */
    public static final double BASE_MANA = 100;
    public static final double BASE_MANA_REGEN = 1.0;

    // ------------------------------------------------------------------ definitions

    static {
        // ---- Fighter: heavy melee
        job("fighter", 0xD9534F, "闘", List.of(JobStat.MAX_HEALTH, JobStat.ATTACK_DAMAGE, JobStat.ARMOR),
                stats(JobStat.MAX_HEALTH, 4, JobStat.ATTACK_DAMAGE, 2), stats(JobStat.MAX_HEALTH, hpTo(40, 4), JobStat.ATTACK_DAMAGE, 0.08),
                List.of(
                        skill("power_slash", SkillKind.ACTIVE, SkillEffect.SWEEP, 6, 15, 1.5, 4.5, 0, List.of(), 0, "斬"),
                        skill("charge", SkillKind.ACTIVE, SkillEffect.DASH, 8, 10, 1.0, 0, 0, List.of(), 0, "突"),
                        skill("war_cry", SkillKind.ACTIVE, SkillEffect.BUFF, 20, 25, 0, 8, 10, List.of("minecraft:strength"), 0, "鬨"),
                        skill("whirlwind", SkillKind.ACTIVE, SkillEffect.WHIRL, 10, 20, 0.8, 4.5, 0, List.of(), 0, "旋"),
                        skill("intimidate", SkillKind.ACTIVE, SkillEffect.SWEEP, 10, 15, 0.5, 4.5, 8, List.of("rpgmod:attack_down"), 1, "圧"),
                        skill("taunt", SkillKind.ACTIVE, SkillEffect.BUFF, 16, 15, 0, 10, 6, List.of("minecraft:resistance"), 0, "挑")),
                List.of(
                        skill("berserk", SkillKind.ULTIMATE, SkillEffect.BUFF, 90, 50, 0, 0, 15, List.of("minecraft:strength", "minecraft:speed"), 1, "狂"),
                        skill("earthshatter", SkillKind.ULTIMATE, SkillEffect.WHIRL, 90, 60, 2.5, 7, 4, List.of("minecraft:slowness"), 1, "砕")),
                List.of());
        // ---- Ranger: bows
        job("ranger", 0x5CB85C, "弓", List.of(JobStat.ATTACK_DAMAGE, JobStat.MOVEMENT_SPEED, JobStat.CRIT_CHANCE),
                stats(JobStat.MOVEMENT_SPEED, 5, JobStat.CRIT_CHANCE, 3, JobStat.ATTACK_DAMAGE, 1), stats(JobStat.CRIT_CHANCE, 0.05, JobStat.ATTACK_DAMAGE, 0.08, JobStat.MAX_HEALTH, hpTo(30, 0)),
                List.of(
                        skill("multishot", SkillKind.ACTIVE, SkillEffect.SHOT, 6, 15, 0.8, 0, 0, List.of(), 0, "連"),
                        skill("backstep", SkillKind.ACTIVE, SkillEffect.BACKSTEP, 7, 10, 1.4, 0, 0, List.of(), 0, "退"),
                        skill("hunters_mark", SkillKind.ACTIVE, SkillEffect.DEBUFF, 15, 20, 0, 16, 10, List.of("minecraft:glowing", "minecraft:weakness"), 0, "印"),
                        skill("snare", SkillKind.ACTIVE, SkillEffect.DEBUFF, 12, 20, 0.4, 3.5, 4, List.of("minecraft:slowness"), 2, "罠"),
                        skill("piercing_shot", SkillKind.ACTIVE, SkillEffect.BOLT, 8, 18, 1.6, 30, 0, List.of(), 0, "貫"),
                        skill("explosive_arrow", SkillKind.ACTIVE, SkillEffect.SHOT, 10, 20, 1.2, 3, 0, List.of(), 0, "爆")),
                List.of(
                        skill("arrow_rain", SkillKind.ULTIMATE, SkillEffect.SHOT, 90, 50, 0.6, 5, 0, List.of(), 0, "雨"),
                        skill("eagle_eye", SkillKind.ULTIMATE, SkillEffect.BUFF, 90, 40, 0, 0, 15, List.of("minecraft:strength", "minecraft:speed", "minecraft:night_vision"), 1, "鷹")),
                List.of());
        // ---- Paladin: front line, protects allies
        job("paladin", 0xF0C040, "聖", List.of(JobStat.MAX_HEALTH, JobStat.ARMOR, JobStat.ATTACK_DAMAGE, JobStat.SKILL_POWER),
                stats(JobStat.MAX_HEALTH, 6, JobStat.ARMOR, 4), stats(JobStat.MAX_HEALTH, hpTo(40, 6), JobStat.ARMOR, 0.05, JobStat.ATTACK_DAMAGE, 0.06),
                List.of(
                        skill("shield_bash", SkillKind.ACTIVE, SkillEffect.SWEEP, 7, 15, 0.8, 4, 3, List.of("minecraft:slowness"), 1, "打"),
                        skill("holy_guard", SkillKind.ACTIVE, SkillEffect.BUFF, 20, 25, 0, 7, 8, List.of("minecraft:resistance"), 0, "護"),
                        skill("smite", SkillKind.ACTIVE, SkillEffect.SWEEP, 8, 20, 1.6, 8, 0, List.of(), 0, "裁"),
                        skill("lay_on_hands", SkillKind.ACTIVE, SkillEffect.HEAL, 15, 25, 0.9, 5, 0, List.of(), 0, "手"),
                        skill("guardian_dome", SkillKind.ACTIVE, SkillEffect.BUFF, 40, 40, 0, 5, 8, List.of(), 0, "囲"),
                        skill("holy_wave", SkillKind.ACTIVE, SkillEffect.SWEEP, 10, 20, 1.0, 10, 0, List.of(), 0, "波")),
                List.of(
                        skill("divine_aegis", SkillKind.ULTIMATE, SkillEffect.BUFF, 100, 60, 0, 8, 12, List.of("minecraft:resistance", "minecraft:absorption"), 1, "盾"),
                        skill("judgement", SkillKind.ULTIMATE, SkillEffect.WHIRL, 90, 60, 2.8, 6.5, 0, List.of(), 0, "審")),
                List.of());
        // ---- Wizard: spells on the staff, skills 1/2 for mana and mobility
        job("wizard", 0x7B68EE, "魔", List.of(JobStat.MAX_MANA, JobStat.MAGIC_ATTACK, JobStat.SKILL_POWER, JobStat.CAST_SPEED, JobStat.MANA_REGEN),
                stats(JobStat.MAX_MANA, 50, JobStat.MANA_REGEN, 0.5, JobStat.MAGIC_ATTACK, 2), stats(JobStat.MAX_MANA, 1, JobStat.MAGIC_ATTACK, 0.08),
                List.of(
                        skill("mana_surge", SkillKind.ACTIVE, SkillEffect.MANA, 20, 0, 40, 0, 0, List.of(), 0, "充"),
                        skill("blink", SkillKind.ACTIVE, SkillEffect.BLINK, 8, 15, 0, 10, 0, List.of(), 0, "瞬"),
                        skill("mana_barrier", SkillKind.ACTIVE, SkillEffect.BUFF, 25, 30, 0, 0, 10, List.of("minecraft:absorption"), 1, "障"),
                        skill("arcane_focus", SkillKind.ACTIVE, SkillEffect.MANA, 30, 0, 25, 6, 0, List.of(), 0, "集"),
                        skill("quick_cast", SkillKind.ACTIVE, SkillEffect.BUFF, 30, 20, 0, 0, 12, List.of("rpgmod:quick_cast"), 0, "速"),
                        skill("chrono", SkillKind.ACTIVE, SkillEffect.BUFF, 45, 30, 0.5, 0, 0, List.of(), 0, "時")),
                List.of(
                        skill("meteor", SkillKind.ULTIMATE, SkillEffect.WHIRL, 100, 80, 3.0, 5.5, 5, List.of(), 0, "隕"),
                        skill("time_warp", SkillKind.ULTIMATE, SkillEffect.DEBUFF, 90, 60, 0, 10, 8, List.of("minecraft:slowness", "minecraft:weakness"), 2, "遅")),
                List.of(
                        skill("magic_bolt", SkillKind.SPELL, SkillEffect.BOLT, 1.2, 8, 1.4, 28, 0, List.of(), 0, "弾"),
                        skill("flame_burst", SkillKind.SPELL, SkillEffect.WHIRL, 6, 20, 1.4, 7, 4, List.of(), 0, "炎"),
                        skill("frost_nova", SkillKind.SPELL, SkillEffect.DEBUFF, 8, 20, 1.0, 6, 6, List.of("minecraft:slowness"), 2, "氷"),
                        skill("thunder", SkillKind.SPELL, SkillEffect.LIGHTNING, 10, 30, 2.2, 28, 0, List.of(), 0, "雷"),
                        skill("arcane_shield", SkillKind.SPELL, SkillEffect.BUFF, 20, 25, 0, 0, 15, List.of("minecraft:absorption", "minecraft:resistance"), 1, "護"),
                        skill("arcane_missiles", SkillKind.SPELL, SkillEffect.BOLT, 5, 18, 0.5, 24, 0, List.of(), 0, "矢"),
                        skill("blizzard", SkillKind.SPELL, SkillEffect.DEBUFF, 14, 35, 0.35, 4, 2, List.of("minecraft:slowness"), 1, "嵐"),
                        skill("gravity", SkillKind.SPELL, SkillEffect.WHIRL, 12, 30, 1.6, 5, 0, List.of(), 0, "重")));
        // ---- Healer: heals and buffs
        job("healer", 0x5BC0DE, "癒", List.of(JobStat.MAX_MANA, JobStat.MAGIC_ATTACK, JobStat.SKILL_POWER, JobStat.MAX_HEALTH),
                stats(JobStat.MAX_MANA, 40, JobStat.SKILL_POWER, 10, JobStat.MAGIC_ATTACK, 1), stats(JobStat.MAGIC_ATTACK, 0.07, JobStat.MAX_MANA, 0.75, JobStat.MAX_HEALTH, hpTo(30, 0)),
                List.of(
                        skill("heal", SkillKind.ACTIVE, SkillEffect.HEAL, 6, 15, 0.9, 4, 0, List.of(), 0, "癒"),
                        skill("group_heal", SkillKind.ACTIVE, SkillEffect.HEAL, 12, 30, 0.6, 10, 0, List.of(), 0, "群"),
                        skill("blessing", SkillKind.ACTIVE, SkillEffect.BUFF, 20, 25, 0, 8, 8, List.of("minecraft:regeneration"), 0, "祝"),
                        skill("mana_gift", SkillKind.ACTIVE, SkillEffect.MANA, 25, 0, 30, 8, 0, List.of(), 0, "恵"),
                        skill("nourish", SkillKind.ACTIVE, SkillEffect.BUFF, 30, 25, 6, 8, 0, List.of(), 0, "糧"),
                        skill("purify", SkillKind.ACTIVE, SkillEffect.HEAL, 18, 20, 0.4, 8, 0, List.of(), 0, "浄")),
                List.of(
                        skill("sanctuary", SkillKind.ULTIMATE, SkillEffect.HEAL, 100, 60, 2.8, 6, 0, List.of(), 0, "域"),
                        skill("guardian_light", SkillKind.ULTIMATE, SkillEffect.BUFF, 100, 60, 0, 10, 15, List.of("minecraft:absorption", "minecraft:resistance", "minecraft:regeneration"), 1, "光")),
                List.of());
        // ---- Assassin: daggers, burst and stealth
        job("assassin", 0x8E44AD, "影", List.of(JobStat.CRIT_CHANCE, JobStat.ATTACK_DAMAGE, JobStat.ATTACK_SPEED, JobStat.MOVEMENT_SPEED),
                stats(JobStat.CRIT_CHANCE, 5, JobStat.MOVEMENT_SPEED, 5, JobStat.ATTACK_DAMAGE, 1), stats(JobStat.CRIT_CHANCE, 0.075, JobStat.ATTACK_DAMAGE, 0.08, JobStat.MAX_HEALTH, hpTo(30, 0)),
                List.of(
                        skill("shadow_step", SkillKind.ACTIVE, SkillEffect.BLINK, 7, 15, 0, 12, 0, List.of(), 0, "影"),
                        skill("backstab", SkillKind.ACTIVE, SkillEffect.SWEEP, 6, 15, 1.5, 3.5, 0, List.of(), 0, "急"),
                        skill("smoke_bomb", SkillKind.ACTIVE, SkillEffect.STEALTH, 18, 20, 0, 0, 5, List.of("minecraft:speed"), 1, "煙"),
                        skill("poison_coat", SkillKind.ACTIVE, SkillEffect.BUFF, 20, 15, 0, 0, 30, List.of(), 0, "毒"),
                        skill("throwing_knives", SkillKind.ACTIVE, SkillEffect.SHOT, 8, 15, 0.6, 16, 0, List.of(), 0, "投"),
                        skill("shadow_bind", SkillKind.ACTIVE, SkillEffect.DEBUFF, 14, 20, 0.5, 12, 3, List.of(), 0, "縛")),
                List.of(
                        skill("assassinate", SkillKind.ULTIMATE, SkillEffect.SWEEP, 90, 50, 3.5, 12, 0, List.of(), 0, "殺"),
                        skill("shadow_dance", SkillKind.ULTIMATE, SkillEffect.STEALTH, 90, 50, 0, 0, 10, List.of("minecraft:strength", "minecraft:speed"), 1, "舞")),
                List.of());
        // ---- Enchanter: buffs allies, weakens enemies
        job("enchanter", 0xE67EB5, "付", List.of(JobStat.SKILL_POWER, JobStat.MAGIC_ATTACK, JobStat.COOLDOWN_REDUCTION, JobStat.MAX_MANA),
                stats(JobStat.COOLDOWN_REDUCTION, 5, JobStat.MAX_MANA, 30, JobStat.MAGIC_ATTACK, 1), stats(JobStat.MAGIC_ATTACK, 0.07, JobStat.COOLDOWN_REDUCTION, 0.05, JobStat.MAX_HEALTH, hpTo(30, 0)),
                List.of(
                        skill("empower", SkillKind.ACTIVE, SkillEffect.BUFF, 18, 25, 0, 8, 10, List.of("minecraft:strength"), 0, "強"),
                        skill("haste", SkillKind.ACTIVE, SkillEffect.BUFF, 18, 25, 0, 8, 10, List.of("minecraft:speed", "minecraft:haste"), 0, "速"),
                        skill("weaken", SkillKind.ACTIVE, SkillEffect.DEBUFF, 15, 20, 0, 8, 8, List.of("minecraft:weakness"), 0, "弱"),
                        skill("mana_link", SkillKind.ACTIVE, SkillEffect.MANA, 25, 0, 20, 8, 0, List.of(), 0, "繋"),
                        skill("fire_weapon", SkillKind.ACTIVE, SkillEffect.BUFF, 25, 30, 0.3, 8, 15, List.of("rpgmod:fire_weapon"), 0, "焔"),
                        skill("binding_field", SkillKind.ACTIVE, SkillEffect.DEBUFF, 18, 25, 0, 4, 5, List.of("rpgmod:attack_down"), 0, "陣")),
                List.of(
                        skill("grand_enchant", SkillKind.ULTIMATE, SkillEffect.BUFF, 100, 60, 0, 10, 15, List.of("minecraft:strength", "minecraft:resistance", "minecraft:speed"), 1, "極"),
                        skill("hex", SkillKind.ULTIMATE, SkillEffect.DEBUFF, 90, 60, 0.6, 10, 10, List.of("minecraft:weakness", "minecraft:slowness"), 1, "呪")),
                List.of());
        // ---- Gambler: random results
        job("gambler", 0x1ABC9C, "賭", List.of(JobStat.CRIT_CHANCE, JobStat.ATTACK_DAMAGE, JobStat.LUCK, JobStat.COOLDOWN_REDUCTION),
                stats(JobStat.LUCK, 2, JobStat.CRIT_CHANCE, 5), stats(JobStat.LUCK, 0.025, JobStat.CRIT_CHANCE, 0.05, JobStat.ATTACK_DAMAGE, 0.06, JobStat.MAX_HEALTH, hpTo(40, 0)),
                List.of(
                        skill("coin_toss", SkillKind.ACTIVE, SkillEffect.GAMBLE, 8, 10, 1, 6, 6, List.of(), 0, "銭"),
                        skill("lucky_shot", SkillKind.ACTIVE, SkillEffect.SHOT, 6, 10, 1.0, 0, 0, List.of(), 0, "幸"),
                        skill("dice_roll", SkillKind.ACTIVE, SkillEffect.GAMBLE, 12, 15, 1.5, 6, 8, List.of(), 0, "骰"),
                        skill("all_in", SkillKind.ACTIVE, SkillEffect.GAMBLE, 20, 25, 2.5, 8, 10, List.of(), 0, "賭"),
                        skill("russian_roulette", SkillKind.ACTIVE, SkillEffect.SHOT, 2, 5, 5.0, 16, 0, List.of(), 0, "銃"),
                        skill("card_throw", SkillKind.ACTIVE, SkillEffect.SHOT, 9, 15, 0.9, 16, 0, List.of(), 0, "札")),
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

    /**
     * Health per level so that, without skill panel cells, a job reaches {@code total} max health at
     * level 100 (vanilla 20 + its starting bonus + growth).
     */
    private static double hpTo(double total, double start) {
        return (total - 20 - start) / (LevelCurve.DEFAULT_MAX_LEVEL - 1);
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
