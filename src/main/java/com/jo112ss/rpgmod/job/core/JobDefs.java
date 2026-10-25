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
    /** Skills that raise downed players (their cooldown is x5 instead of the ultimates' x2). */
    private static final java.util.Set<String> REVIVES = java.util.Set.of("revive_prayer", "great_miracle");
    /** Charge (hold) time in seconds for spells cast from a staff. */
    private static final Map<String, Double> CAST_TIMES = Map.ofEntries(
            Map.entry("magic_bolt", 0.6), Map.entry("flame_burst", 1.2), Map.entry("frost_nova", 1.0), Map.entry("thunder", 1.6),
            Map.entry("arcane_shield", 0.8), Map.entry("arcane_missiles", 0.8), Map.entry("blizzard", 1.4), Map.entry("gravity", 1.2),
            // spellmaster: stronger spells that take longer to charge
            Map.entry("inferno", 2.0), Map.entry("absolute_zero", 2.2), Map.entry("thunderstorm", 2.4), Map.entry("stone_spikes", 1.6),
            Map.entry("radiant_lance", 1.8), Map.entry("dark_orb", 2.0), Map.entry("tornado", 2.0), Map.entry("starfall", 2.6),
            // healer (heals) and bishop
            Map.entry("heal", 0.8), Map.entry("group_heal", 1.4), Map.entry("purify", 1.0),
            Map.entry("holy_bolt", 0.8), Map.entry("holy_nova", 1.4), Map.entry("light_pillar", 1.8));
    public static final Map<String, Job> JOBS = new LinkedHashMap<>();

    /** Jobs whose skills scale with magic attack (staff + own magic) instead of attack (weapon + own). */
    public static final java.util.Set<String> MAGIC_JOBS = java.util.Set.of("wizard", "healer", "enchanter", "spellmaster", "runemaster", "bishop");

    /** Spells, and every skill of the caster jobs, use magic attack. */
    public static boolean isMagic(SkillDef def) {
        return def.kind() == SkillKind.SPELL || MAGIC_JOBS.contains(def.job());
    }

    /** What the skill's power number means: "damage" / "heal" (a ratio of attack), or null. */
    public static String powerKind(SkillDef def) {
        if (def.power() <= 0) return null;
        return switch (def.effect()) {
            case HEAL -> "heal";
            case SWEEP, WHIRL, DASH, SHOT, DEBUFF, BOLT, LIGHTNING -> NO_POWER_LINE.contains(def.id()) ? null : "damage";
            default -> DAMAGE_TOO.contains(def.id()) ? "damage" : null;
        };
    }

    /** Skills whose power is not a damage ratio although their effect type usually is (and the reverse). */
    private static final java.util.Set<String> NO_POWER_LINE = java.util.Set.of();
    private static final java.util.Set<String> DAMAGE_TOO = java.util.Set.of("decoy", "reprisal", "grand_rune");

    /** Mana every job starts with before job / level / panel bonuses. */
    public static final double BASE_MANA = 100;
    public static final double BASE_MANA_REGEN = 1.0;
    /** Attack and magic attack from levels are half of what they were: the other half went to the weapon materials. */
    private static final double LEVEL_ATK = 0.5;

    // ------------------------------------------------------------------ definitions

    static {
        // ---- Fighter: heavy melee
        job("fighter", 0xD9534F, "闘", List.of(JobStat.MAX_HEALTH, JobStat.ATTACK_DAMAGE, JobStat.ARMOR),
                stats(JobStat.MAX_HEALTH, 4, JobStat.ATTACK_DAMAGE, 2), stats(JobStat.MAX_HEALTH, hpGrow(1.0, 4), JobStat.ATTACK_DAMAGE, LEVEL_ATK * (0.08 + 4 / 99.0)),
                List.of(
                        skill("power_slash", SkillKind.ACTIVE, SkillEffect.SWEEP, 6, 11, 3.0, 4.5, 0, List.of(), 0, "斬"),
                        skill("charge", SkillKind.ACTIVE, SkillEffect.DASH, 8, 7, 2.0, 0, 0, List.of(), 0, "突"),
                        skill("war_cry", SkillKind.ACTIVE, SkillEffect.BUFF, 20, 18, 0, 8, 10, List.of("minecraft:strength"), 0, "鬨"),
                        skill("whirlwind", SkillKind.ACTIVE, SkillEffect.WHIRL, 10, 14, 1.6, 4.5, 0, List.of(), 0, "旋"),
                        skill("intimidate", SkillKind.ACTIVE, SkillEffect.SWEEP, 10, 11, 1.0, 4.5, 8, List.of("rpgmod:attack_down"), 1, "圧"),
                        skill("taunt", SkillKind.ACTIVE, SkillEffect.BUFF, 16, 11, 0, 10, 6, List.of("minecraft:resistance"), 0, "挑")),
                List.of(
                        skill("berserk", SkillKind.ULTIMATE, SkillEffect.BUFF, 90, 35, 0, 0, 15, List.of("minecraft:strength", "minecraft:speed"), 1, "狂"),
                        skill("earthshatter", SkillKind.ULTIMATE, SkillEffect.WHIRL, 90, 42, 5.0, 7, 4, List.of("minecraft:slowness"), 1, "砕")),
                List.of());
        // ---- Ranger: bows
        job("ranger", 0x5CB85C, "弓", List.of(JobStat.ATTACK_DAMAGE, JobStat.MOVEMENT_SPEED, JobStat.CRIT_CHANCE),
                stats(JobStat.MOVEMENT_SPEED, 5, JobStat.CRIT_CHANCE, 3, JobStat.ATTACK_DAMAGE, 1), stats(JobStat.CRIT_CHANCE, 0.05, JobStat.ATTACK_DAMAGE, LEVEL_ATK * (0.08 + 4 / 99.0), JobStat.MAX_HEALTH, hpGrow(1.0, 0)),
                List.of(
                        skill("multishot", SkillKind.ACTIVE, SkillEffect.SHOT, 6, 11, 1.6, 0, 0, List.of(), 0, "連"),
                        skill("backstep", SkillKind.ACTIVE, SkillEffect.BACKSTEP, 7, 7, 1.4, 0, 0, List.of(), 0, "退"),
                        skill("hunters_mark", SkillKind.ACTIVE, SkillEffect.DEBUFF, 20, 14, 0, 128, 12, List.of("minecraft:glowing"), 0, "探"),
                        skill("snare", SkillKind.ACTIVE, SkillEffect.DEBUFF, 12, 14, 0.8, 3.5, 4, List.of("minecraft:slowness"), 2, "罠"),
                        skill("piercing_shot", SkillKind.ACTIVE, SkillEffect.BOLT, 8, 13, 3.2, 30, 0, List.of(), 0, "貫"),
                        skill("explosive_arrow", SkillKind.ACTIVE, SkillEffect.SHOT, 10, 14, 2.4, 3, 0, List.of(), 0, "爆")),
                List.of(
                        skill("arrow_rain", SkillKind.ULTIMATE, SkillEffect.SHOT, 90, 35, 1.2, 5, 0, List.of(), 0, "雨"),
                        skill("eagle_eye", SkillKind.ULTIMATE, SkillEffect.BUFF, 90, 28, 0, 0, 15, List.of("minecraft:strength", "minecraft:speed", "minecraft:night_vision"), 1, "鷹")),
                List.of());
        // ---- Paladin: front line, protects allies
        job("paladin", 0xF0C040, "聖", List.of(JobStat.MAX_HEALTH, JobStat.ARMOR, JobStat.ATTACK_DAMAGE, JobStat.COOLDOWN_REDUCTION),
                stats(JobStat.MAX_HEALTH, 6, JobStat.ARMOR, 4), stats(JobStat.MAX_HEALTH, hpGrow(1.2, 6), JobStat.ATTACK_DAMAGE, LEVEL_ATK * (0.06 + 4 / 99.0)),
                List.of(
                        skill("shield_bash", SkillKind.ACTIVE, SkillEffect.SWEEP, 7, 11, 1.6, 4, 3, List.of("minecraft:slowness"), 1, "打"),
                        skill("holy_guard", SkillKind.ACTIVE, SkillEffect.BUFF, 20, 18, 0, 7, 8, List.of("minecraft:resistance"), 0, "護"),
                        skill("smite", SkillKind.ACTIVE, SkillEffect.SWEEP, 8, 14, 3.2, 8, 0, List.of(), 0, "裁"),
                        skill("lay_on_hands", SkillKind.ACTIVE, SkillEffect.HEAL, 15, 18, 0.9, 5, 0, List.of(), 0, "手"),
                        skill("guardian_dome", SkillKind.ACTIVE, SkillEffect.BUFF, 40, 28, 0, 5, 12, List.of(), 0, "囲"),
                        skill("holy_wave", SkillKind.ACTIVE, SkillEffect.SWEEP, 10, 14, 2.0, 10, 0, List.of(), 0, "波")),
                List.of(
                        skill("divine_aegis", SkillKind.ULTIMATE, SkillEffect.BUFF, 100, 42, 0, 8, 12, List.of("minecraft:resistance", "minecraft:absorption"), 1, "盾"),
                        skill("judgement", SkillKind.ULTIMATE, SkillEffect.WHIRL, 90, 42, 2.5, 6.5, 0, List.of(), 0, "審")),
                List.of());
        // ---- Wizard: spells on the staff, skills 1/2 for mana and mobility
        job("wizard", 0x7B68EE, "魔", List.of(JobStat.MAX_MANA, JobStat.MAGIC_ATTACK, JobStat.COOLDOWN_REDUCTION, JobStat.CAST_SPEED, JobStat.MANA_REGEN),
                stats(JobStat.MAX_MANA, 50, JobStat.MANA_REGEN, 0.5, JobStat.MAGIC_ATTACK, 2), stats(JobStat.MAX_MANA, 1, JobStat.MAGIC_ATTACK, LEVEL_ATK * (0.08 + 4 / 99.0), JobStat.ATTACK_DAMAGE, LEVEL_ATK * (4 / 99.0), JobStat.MAX_HEALTH, hpGrow(0.6, 0)),
                List.of(
                        skill("mana_surge", SkillKind.ACTIVE, SkillEffect.MANA, 20, 0, 40, 0, 0, List.of(), 0, "充"),
                        skill("blink", SkillKind.ACTIVE, SkillEffect.BLINK, 8, 21, 0, 10, 0, List.of(), 0, "瞬"),
                        skill("mana_barrier", SkillKind.ACTIVE, SkillEffect.BUFF, 25, 42, 0, 0, 10, List.of("minecraft:absorption"), 1, "障"),
                        skill("arcane_focus", SkillKind.ACTIVE, SkillEffect.MANA, 30, 0, 25, 6, 0, List.of(), 0, "集"),
                        skill("quick_cast", SkillKind.ACTIVE, SkillEffect.BUFF, 30, 28, 0, 0, 12, List.of("rpgmod:quick_cast"), 0, "速"),
                        skill("chrono", SkillKind.ACTIVE, SkillEffect.BUFF, 45, 42, 0.5, 0, 0, List.of(), 0, "時")),
                List.of(
                        skill("meteor", SkillKind.ULTIMATE, SkillEffect.WHIRL, 100, 112, 9.0, 5.5, 5, List.of(), 0, "隕"),
                        skill("time_warp", SkillKind.ULTIMATE, SkillEffect.DEBUFF, 90, 84, 0, 10, 8, List.of("minecraft:slowness", "minecraft:weakness"), 2, "遅")),
                List.of(
                        skill("magic_bolt", SkillKind.SPELL, SkillEffect.BOLT, 0.3, 5, 1.1, 28, 0, List.of(), 0, "弾"),
                        skill("flame_burst", SkillKind.SPELL, SkillEffect.WHIRL, 6, 28, 2.1, 7, 4, List.of(), 0, "炎"),
                        skill("frost_nova", SkillKind.SPELL, SkillEffect.DEBUFF, 8, 28, 1.5, 6, 6, List.of("minecraft:slowness"), 2, "氷"),
                        skill("thunder", SkillKind.SPELL, SkillEffect.LIGHTNING, 10, 42, 3.3, 28, 0, List.of(), 0, "雷"),
                        skill("arcane_shield", SkillKind.SPELL, SkillEffect.BUFF, 20, 35, 0, 0, 15, List.of("minecraft:absorption", "minecraft:resistance"), 1, "護"),
                        skill("arcane_missiles", SkillKind.SPELL, SkillEffect.BOLT, 5, 26, 0.75, 24, 0, List.of(), 0, "矢"),
                        skill("blizzard", SkillKind.SPELL, SkillEffect.DEBUFF, 14, 49, 0.525, 4, 2, List.of("minecraft:slowness"), 1, "嵐"),
                        skill("gravity", SkillKind.SPELL, SkillEffect.WHIRL, 12, 42, 2.4, 5, 0, List.of(), 0, "重")));
        // ---- Healer: heals (spells charged on the staff) and buffs
        job("healer", 0x5BC0DE, "癒", List.of(JobStat.MAX_MANA, JobStat.MAGIC_ATTACK, JobStat.MANA_REGEN, JobStat.MAX_HEALTH),
                stats(JobStat.MAX_MANA, 40, JobStat.MAGIC_ATTACK, 1), stats(JobStat.MAGIC_ATTACK, LEVEL_ATK * (0.07 + 4 / 99.0), JobStat.ATTACK_DAMAGE, LEVEL_ATK * (0.05 + 4 / 99.0), JobStat.MAX_MANA, 0.75, JobStat.MAX_HEALTH, hpGrow(0.6, 0)),
                List.of(
                        skill("holy_light", SkillKind.ACTIVE, SkillEffect.BOLT, 6, 14, 1.8, 24, 0, List.of(), 0, "照"),
                        skill("blessing", SkillKind.ACTIVE, SkillEffect.BUFF, 20, 35, 0, 8, 8, List.of("minecraft:regeneration"), 0, "祝"),
                        skill("mana_gift", SkillKind.ACTIVE, SkillEffect.MANA, 25, 0, 30, 8, 0, List.of(), 0, "恵"),
                        skill("nourish", SkillKind.ACTIVE, SkillEffect.BUFF, 30, 35, 6, 8, 0, List.of(), 0, "糧"),
                        skill("holy_barrier", SkillKind.ACTIVE, SkillEffect.BUFF, 20, 28, 0, 8, 15, List.of(), 0, "盾"),
                        skill("guardian_angel", SkillKind.ACTIVE, SkillEffect.BUFF, 60, 35, 0, 12, 5, List.of(), 0, "天")),
                List.of(
                        skill("sanctuary", SkillKind.ULTIMATE, SkillEffect.HEAL, 100, 84, 2.8, 6, 0, List.of(), 0, "域"),
                        skill("guardian_light", SkillKind.ULTIMATE, SkillEffect.BUFF, 100, 84, 0, 10, 15, List.of("minecraft:absorption", "minecraft:resistance", "minecraft:regeneration"), 1, "光")),
                List.of(
                        skill("heal", SkillKind.SPELL, SkillEffect.HEAL, 6, 21, 0.9, 4, 0, List.of(), 0, "癒"),
                        skill("group_heal", SkillKind.SPELL, SkillEffect.HEAL, 12, 42, 0.6, 10, 0, List.of(), 0, "群"),
                        skill("purify", SkillKind.SPELL, SkillEffect.HEAL, 18, 28, 0.4, 8, 0, List.of(), 0, "浄")));
        // ---- Assassin: daggers, burst and stealth
        job("assassin", 0x8E44AD, "影", List.of(JobStat.CRIT_CHANCE, JobStat.ATTACK_DAMAGE, JobStat.ATTACK_SPEED, JobStat.MOVEMENT_SPEED),
                stats(JobStat.CRIT_CHANCE, 5, JobStat.MOVEMENT_SPEED, 5, JobStat.ATTACK_DAMAGE, 1), stats(JobStat.CRIT_CHANCE, 0.075, JobStat.ATTACK_DAMAGE, LEVEL_ATK * (0.08 + 4 / 99.0), JobStat.MAX_HEALTH, hpGrow(1.0, 0)),
                List.of(
                        skill("shadow_step", SkillKind.ACTIVE, SkillEffect.BLINK, 7, 11, 0, 12, 0, List.of(), 0, "影"),
                        skill("backstab", SkillKind.ACTIVE, SkillEffect.SWEEP, 6, 11, 3.0, 3.5, 0, List.of(), 0, "急"),
                        skill("smoke_bomb", SkillKind.ACTIVE, SkillEffect.STEALTH, 18, 14, 0, 0, 5, List.of("minecraft:speed"), 1, "煙"),
                        skill("poison_coat", SkillKind.ACTIVE, SkillEffect.BUFF, 20, 11, 0, 0, 30, List.of(), 0, "毒"),
                        skill("throwing_knives", SkillKind.ACTIVE, SkillEffect.SHOT, 8, 11, 1.2, 16, 0, List.of(), 0, "投"),
                        skill("shadow_bind", SkillKind.ACTIVE, SkillEffect.DEBUFF, 14, 14, 1.0, 12, 3, List.of(), 0, "縛")),
                List.of(
                        skill("assassinate", SkillKind.ULTIMATE, SkillEffect.SWEEP, 90, 35, 7.0, 12, 0, List.of(), 0, "殺"),
                        skill("shadow_dance", SkillKind.ULTIMATE, SkillEffect.STEALTH, 90, 35, 0, 0, 10, List.of("minecraft:strength", "minecraft:speed"), 1, "舞")),
                List.of());
        // ---- Enchanter: buffs allies, weakens enemies
        job("enchanter", 0xE67EB5, "付", List.of(JobStat.MANA_REGEN, JobStat.MAGIC_ATTACK, JobStat.COOLDOWN_REDUCTION, JobStat.MAX_MANA),
                stats(JobStat.COOLDOWN_REDUCTION, 5, JobStat.MAX_MANA, 30, JobStat.MAGIC_ATTACK, 1), stats(JobStat.MAGIC_ATTACK, LEVEL_ATK * (0.07 + 4 / 99.0), JobStat.ATTACK_DAMAGE, LEVEL_ATK * (4 / 99.0), JobStat.COOLDOWN_REDUCTION, 0.05, JobStat.MAX_HEALTH, hpGrow(0.6, 0)),
                List.of(
                        skill("empower", SkillKind.ACTIVE, SkillEffect.BUFF, 18, 35, 0, 8, 20, List.of("minecraft:strength"), 0, "強"),
                        skill("haste", SkillKind.ACTIVE, SkillEffect.BUFF, 18, 35, 0, 8, 20, List.of("minecraft:speed", "minecraft:haste"), 0, "速"),
                        skill("weaken", SkillKind.ACTIVE, SkillEffect.DEBUFF, 15, 28, 0, 8, 8, List.of("minecraft:weakness"), 0, "弱"),
                        skill("mana_link", SkillKind.ACTIVE, SkillEffect.MANA, 25, 0, 20, 8, 0, List.of(), 0, "繋"),
                        skill("fire_weapon", SkillKind.ACTIVE, SkillEffect.BUFF, 25, 42, 0.6, 8, 30, List.of("rpgmod:fire_weapon"), 0, "焔"),
                        skill("binding_field", SkillKind.ACTIVE, SkillEffect.DEBUFF, 18, 35, 0, 4, 5, List.of("rpgmod:attack_down"), 0, "陣")),
                List.of(
                        skill("grand_enchant", SkillKind.ULTIMATE, SkillEffect.BUFF, 100, 84, 0, 10, 30, List.of("minecraft:strength", "minecraft:resistance", "minecraft:speed"), 1, "極"),
                        skill("hex", SkillKind.ULTIMATE, SkillEffect.DEBUFF, 90, 84, 1.2, 10, 10, List.of("minecraft:weakness", "minecraft:slowness"), 1, "呪")),
                List.of());
        // ---- Gambler: random results
        job("gambler", 0x1ABC9C, "賭", List.of(JobStat.CRIT_CHANCE, JobStat.ATTACK_DAMAGE, JobStat.LUCK, JobStat.COOLDOWN_REDUCTION),
                stats(JobStat.LUCK, 2, JobStat.CRIT_CHANCE, 5), stats(JobStat.LUCK, 0.025, JobStat.CRIT_CHANCE, 0.05, JobStat.ATTACK_DAMAGE, LEVEL_ATK * (0.06 + 4 / 99.0), JobStat.MAX_HEALTH, hpGrow(1.0, 0)),
                List.of(
                        skill("coin_toss", SkillKind.ACTIVE, SkillEffect.GAMBLE, 8, 7, 2, 6, 6, List.of(), 0, "銭"),
                        skill("lucky_shot", SkillKind.ACTIVE, SkillEffect.SHOT, 6, 7, 2.0, 0, 0, List.of(), 0, "幸"),
                        skill("dice_roll", SkillKind.ACTIVE, SkillEffect.GAMBLE, 12, 11, 3.0, 6, 8, List.of(), 0, "骰"),
                        skill("all_in", SkillKind.ACTIVE, SkillEffect.GAMBLE, 20, 18, 5.0, 8, 10, List.of(), 0, "賭"),
                        skill("russian_roulette", SkillKind.ACTIVE, SkillEffect.SHOT, 2, 4, 10.0, 16, 0, List.of(), 0, "銃"),
                        skill("card_throw", SkillKind.ACTIVE, SkillEffect.SHOT, 9, 11, 1.8, 16, 0, List.of(), 0, "札")),
                List.of(
                        skill("jackpot", SkillKind.ULTIMATE, SkillEffect.GAMBLE, 90, 35, 8, 10, 15, List.of(), 0, "当"),
                        skill("roulette", SkillKind.ULTIMATE, SkillEffect.BUFF, 90, 35, 0, 0, 12, List.of(), 0, "輪")),
                List.of());
    }

    // ------------------------------------------------------------------ advanced jobs (level 100+)

    /** Base job id -> its advanced job id, and back. */
    public static final Map<String, String> ADVANCED = new LinkedHashMap<>();
    public static final Map<String, String> BASE_OF = new LinkedHashMap<>();

    static {
        // growth per advanced level comes on top of the base job's own growth, which keeps going
        // (except health: the base job's stops at Lv100, then only the advanced job's below adds -
        // Lv199: guardian 100, berserker 80, spellmaster 50, the others stay at 60).
        // Each advanced job learns five skills and one ultimate on its own panel (the spellmaster
        // also eight spells, the bishop's attacks are spells cast from the staff).
        SkillKind A = SkillKind.ACTIVE, U = SkillKind.ULTIMATE, S = SkillKind.SPELL;
        // ---- Berserker: all-out damage, paid for with health
        advanced("fighter", "berserker", 0xB0201C, "狂", List.of(JobStat.ATTACK_DAMAGE, JobStat.CRIT_CHANCE, JobStat.ATTACK_SPEED, JobStat.MAX_HEALTH),
                stats(JobStat.ATTACK_DAMAGE, LEVEL_ATK * 0.05, JobStat.CRIT_CHANCE, 0.05),
                List.of(
                        skill("blood_frenzy", A, SkillEffect.BUFF, 25, 15, 0.15, 0, 12, List.of("minecraft:strength", "minecraft:haste"), 1, "血"),
                        skill("decapitate", A, SkillEffect.SWEEP, 8, 18, 4.0, 4, 0, List.of(), 0, "断"),
                        skill("triple_whirl", A, SkillEffect.WHIRL, 12, 22, 1.8, 5, 0, List.of(), 0, "渦"),
                        skill("reckless_charge", A, SkillEffect.DASH, 10, 15, 3.0, 0, 4, List.of(), 0, "捨"),
                        skill("terror_roar", A, SkillEffect.DEBUFF, 20, 20, 0, 8, 10, List.of("rpgmod:attack_down", "minecraft:slowness"), 1, "咆")),
                List.of(
                        skill("asura", U, SkillEffect.BUFF, 100, 40, 0, 0, 15, List.of(), 0, "修")),
                List.of());
        // ---- Guardian: stands in front and takes the blows for everyone
        advanced("paladin", "guardian", 0xC8A030, "護", List.of(JobStat.ARMOR, JobStat.MAX_HEALTH, JobStat.ATTACK_DAMAGE),
                stats(),
                List.of(
                        skill("iron_wall", A, SkillEffect.BUFF, 30, 20, 0, 0, 8, List.of(), 0, "壁"),
                        skill("provoke_shield", A, SkillEffect.BUFF, 18, 15, 0, 12, 10, List.of(), 1, "挑"),
                        skill("cover", A, SkillEffect.BUFF, 25, 20, 0, 10, 10, List.of(), 0, "庇"),
                        skill("shield_wall", A, SkillEffect.BUFF, 25, 22, 0, 4, 8, List.of(), 0, "垣"),
                        skill("reprisal", A, SkillEffect.BUFF, 20, 15, 0.15, 0, 8, List.of(), 0, "返")),
                List.of(
                        skill("bastion", U, SkillEffect.BUFF, 120, 42, 0, 10, 12, List.of("minecraft:resistance", "minecraft:absorption"), 2, "塞")),
                List.of());
        // ---- Sharpshooter: long range, heavy single shots
        advanced("ranger", "sharpshooter", 0x3E9E4A, "狙", List.of(JobStat.ATTACK_DAMAGE, JobStat.CRIT_CHANCE, JobStat.MOVEMENT_SPEED),
                stats(JobStat.ATTACK_DAMAGE, LEVEL_ATK * 0.05, JobStat.CRIT_CHANCE, 0.05),
                List.of(
                        skill("snipe", A, SkillEffect.SHOT, 10, 18, 6.0, 64, 0, List.of(), 0, "狙"),
                        skill("rapid_fire", A, SkillEffect.SHOT, 9, 15, 0.7, 0, 0, List.of(), 0, "速"),
                        skill("weak_point", A, SkillEffect.DEBUFF, 18, 15, 0, 32, 10, List.of("minecraft:glowing"), 0, "看"),
                        skill("ricochet", A, SkillEffect.BOLT, 8, 15, 2.2, 28, 0, List.of(), 0, "跳"),
                        skill("roll", A, SkillEffect.BACKSTEP, 8, 10, 1.6, 0, 6, List.of(), 0, "転")),
                List.of(
                        skill("dead_eye", U, SkillEffect.SHOT, 100, 35, 2.5, 40, 0, List.of(), 0, "眼")),
                List.of());
        // ---- Spellmaster: the strongest spells, slow to charge; two at once
        advanced("wizard", "spellmaster", 0x5A3FD8, "極", List.of(JobStat.MAGIC_ATTACK, JobStat.CAST_SPEED, JobStat.MAX_MANA, JobStat.MANA_REGEN),
                stats(JobStat.MAGIC_ATTACK, LEVEL_ATK * 0.05, JobStat.MAX_MANA, 1),
                List.of(
                        skill("double_cast", A, SkillEffect.BUFF, 30, 30, 0, 0, 15, List.of(), 1, "重"),
                        skill("mana_overload", A, SkillEffect.BUFF, 45, 0, 0, 0, 12, List.of(), 0, "暴"),
                        skill("mana_well", A, SkillEffect.MANA, 40, 0, 6, 5, 8, List.of(), 0, "泉"),
                        skill("phase_shift", A, SkillEffect.BLINK, 12, 20, 0, 14, 0, List.of(), 0, "位"),
                        skill("arcane_seal", A, SkillEffect.DEBUFF, 22, 30, 0, 6, 8, List.of("minecraft:slowness", "rpgmod:exposed"), 0, "封")),
                List.of(
                        skill("armageddon", U, SkillEffect.WHIRL, 120, 140, 3.0, 12, 5, List.of(), 0, "滅")),
                List.of(
                        skill("inferno", S, SkillEffect.WHIRL, 12, 50, 2.5, 4.5, 4, List.of(), 0, "獄"),
                        skill("absolute_zero", S, SkillEffect.DEBUFF, 16, 55, 3.5, 7, 3, List.of(), 0, "零"),
                        skill("thunderstorm", S, SkillEffect.LIGHTNING, 18, 60, 1.4, 8, 0, List.of(), 0, "霆"),
                        skill("stone_spikes", S, SkillEffect.SWEEP, 10, 40, 3.5, 14, 0, List.of(), 0, "岩"),
                        skill("radiant_lance", S, SkillEffect.BOLT, 10, 45, 4.5, 32, 0, List.of(), 0, "槍"),
                        skill("dark_orb", S, SkillEffect.BOLT, 14, 50, 1.0, 20, 0, List.of(), 0, "闇"),
                        skill("tornado", S, SkillEffect.WHIRL, 15, 50, 0.6, 4, 4, List.of(), 0, "竜"),
                        skill("starfall", S, SkillEffect.WHIRL, 20, 70, 0.7, 6, 3, List.of(), 0, "星")));
        // ---- Runemaster: runes on the ground, wide buffs and debuffs, handy blessings
        advanced("enchanter", "runemaster", 0xC0508A, "刻", List.of(JobStat.MAGIC_ATTACK, JobStat.COOLDOWN_REDUCTION, JobStat.MAX_MANA, JobStat.MANA_REGEN),
                stats(JobStat.MAGIC_ATTACK, LEVEL_ATK * 0.04, JobStat.MAX_MANA, 0.5),
                List.of(
                        skill("earth_rune", A, SkillEffect.DEBUFF, 20, 35, 0.3, 6, 8, List.of(), 1, "地"),
                        skill("ward_rune", A, SkillEffect.BUFF, 30, 40, 0, 5, 10, List.of(), 0, "守"),
                        skill("gale_rune", A, SkillEffect.BUFF, 25, 30, 0, 10, 20, List.of("minecraft:speed", "minecraft:jump_boost"), 1, "疾"),
                        skill("night_sight", A, SkillEffect.BUFF, 60, 30, 0, 12, 300, List.of("minecraft:night_vision", "minecraft:water_breathing"), 0, "視"),
                        skill("feather_rune", A, SkillEffect.BUFF, 60, 30, 0, 12, 120, List.of("rpgmod:feather"), 0, "羽")),
                List.of(
                        skill("grand_rune", U, SkillEffect.BUFF, 120, 100, 0.6, 12, 15, List.of(), 0, "環")),
                List.of());
        // ---- Joker: tricks - swaps, decoys and a loaded die
        advanced("gambler", "joker", 0x7A3FB0, "奇", List.of(JobStat.LUCK, JobStat.CRIT_CHANCE, JobStat.COOLDOWN_REDUCTION, JobStat.ATTACK_DAMAGE),
                stats(JobStat.LUCK, 0.02, JobStat.CRIT_CHANCE, 0.05, JobStat.ATTACK_DAMAGE, LEVEL_ATK * 0.03),
                List.of(
                        skill("switcheroo", A, SkillEffect.BLINK, 12, 15, 0, 24, 3, List.of(), 0, "換"),
                        skill("decoy", A, SkillEffect.BUFF, 25, 25, 3.0, 4, 5, List.of(), 0, "分"),
                        skill("loaded_dice", A, SkillEffect.BUFF, 40, 20, 0, 0, 30, List.of(), 0, "詐"),
                        skill("card_storm", A, SkillEffect.WHIRL, 14, 25, 0.5, 5, 3, List.of(), 0, "嵐"),
                        skill("vanish", A, SkillEffect.STEALTH, 18, 20, 0, 16, 3, List.of(), 0, "消")),
                List.of(
                        skill("wildcard", U, SkillEffect.GAMBLE, 90, 40, 0, 0, 0, List.of(), 0, "J")),
                List.of());
        // ---- Nightblade: two blades and the dark
        advanced("assassin", "nightblade", 0x2E3A6E, "夜", List.of(JobStat.ATTACK_DAMAGE, JobStat.CRIT_CHANCE, JobStat.MOVEMENT_SPEED, JobStat.ATTACK_SPEED),
                stats(JobStat.ATTACK_DAMAGE, LEVEL_ATK * 0.04, JobStat.CRIT_CHANCE, 0.05),
                List.of(
                        skill("shadow_run", A, SkillEffect.DASH, 8, 15, 2.5, 9, 0, List.of(), 0, "走"),
                        skill("flurry", A, SkillEffect.SWEEP, 10, 18, 0.6, 4, 0, List.of(), 0, "乱"),
                        skill("twin_venom", A, SkillEffect.BUFF, 25, 15, 0, 0, 15, List.of(), 0, "双"),
                        skill("shadow_stitch", A, SkillEffect.DEBUFF, 16, 20, 1.5, 20, 3, List.of(), 0, "縫"),
                        skill("moon_shadow", A, SkillEffect.SWEEP, 12, 22, 3.5, 16, 0, List.of(), 0, "月")),
                List.of(
                        skill("yaksha", U, SkillEffect.BUFF, 100, 35, 0, 0, 12, List.of("minecraft:speed"), 0, "叉")),
                List.of());
        // ---- Bishop: holy magic from the staff, and miracles
        advanced("healer", "bishop", 0x3A8FD0, "聖", List.of(JobStat.MAGIC_ATTACK, JobStat.MAX_MANA, JobStat.CAST_SPEED, JobStat.MANA_REGEN),
                stats(JobStat.MAGIC_ATTACK, LEVEL_ATK * 0.04, JobStat.MAX_MANA, 0.75),
                List.of(
                        skill("revive_prayer", A, SkillEffect.HEAL, 120, 60, 1.0, 12, 0, List.of(), 0, "蘇"),
                        skill("divine_domain", A, SkillEffect.BUFF, 45, 50, 0, 6, 10, List.of(), 0, "神")),
                List.of(
                        skill("great_miracle", U, SkillEffect.HEAL, 150, 120, 0, 30, 10, List.of("minecraft:resistance", "minecraft:regeneration"), 1, "跡")),
                List.of(
                        skill("holy_bolt", S, SkillEffect.BOLT, 0.8, 8, 1.6, 28, 0, List.of(), 0, "聖"),
                        skill("holy_nova", S, SkillEffect.WHIRL, 10, 40, 3.0, 7, 0, List.of(), 0, "輝"),
                        skill("light_pillar", S, SkillEffect.WHIRL, 14, 50, 1.0, 3, 0, List.of(), 0, "柱")));
        // fusion spells (spellmaster, a charged spell in each hand): never learnt, the power listed
        // here is replaced by 1.2 x the two fused spells' worth (see SpellCasting)
        skill("steam_burst", S, SkillEffect.WHIRL, 0, 0, 6.0, 6, 3, List.of(), 0, "蒸");
        skill("plasma_ray", S, SkillEffect.BOLT, 0, 0, 7.0, 40, 6, List.of(), 0, "電");
        skill("frost_tempest", S, SkillEffect.WHIRL, 0, 0, 6.0, 7, 5, List.of(), 0, "凍");
    }

    private static void advanced(String base, String id, int color, String glyph, List<JobStat> panelStats, Map<JobStat, Double> perLevel,
                                 List<SkillDef> actives, List<SkillDef> ultimates, List<SkillDef> spells) {
        job(id, color, glyph, panelStats, Map.of(), perLevel, actives, ultimates, spells);
        ADVANCED.put(base, id);
        BASE_OF.put(id, base);
    }

    /** The advanced job of a base job, or null. */
    public static Job advancedOf(String base) {
        return job(ADVANCED.get(base));
    }

    public static boolean isAdvanced(String id) {
        return BASE_OF.containsKey(id);
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

    /** Max health at the top level for a job with health factor 1 (melee); armour no longer grows with level. */
    public static final double TOP_HEALTH = 500;

    /**
     * Health per level, the same at every level, so that without skill panel cells a job reaches
     * {@code factor} x {@link #TOP_HEALTH} at the top level (vanilla 20 + its starting bonus + growth):
     * casters 0.6, the paladin line 1.2, everyone else 1.
     */
    private static double hpGrow(double factor, double start) {
        return (TOP_HEALTH * factor - 20 - start) / (LevelCurve.MAX_TOTAL_LEVEL - 1);
    }

    private static Map<JobStat, Double> stats(Object... kv) {
        Map<JobStat, Double> m = new EnumMap<>(JobStat.class);
        for (int i = 0; i < kv.length; i += 2) m.put((JobStat) kv[i], ((Number) kv[i + 1]).doubleValue());
        return Collections.unmodifiableMap(m);
    }

    /** Registers a skill for the job currently being defined (the job id is filled in by {@link #job}). */
    private static SkillDef skill(String id, SkillKind kind, SkillEffect effect, double cooldown, double mana, double power,
                                  double radius, double duration, List<String> effects, int amplifier, String glyph) {
        mana = Math.ceil(mana - 1e-9); // whole MP only
        // ultimates wait twice as long, and the skills that raise the downed five times as long
        if (REVIVES.contains(id)) cooldown *= 5;
        else if (kind == SkillKind.ULTIMATE) cooldown *= 2;
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
