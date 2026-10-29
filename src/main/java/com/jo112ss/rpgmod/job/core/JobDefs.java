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
            Map.entry("heal", 0.8), Map.entry("group_heal", 1.4), Map.entry("purify", 1.0), Map.entry("greater_heal", 1.8), Map.entry("grand_heal", 2.2),
            Map.entry("holy_bolt", 0.8), Map.entry("holy_nova", 1.4), Map.entry("light_pillar", 1.8));
    public static final Map<String, Job> JOBS = new LinkedHashMap<>();

    /** Jobs whose skills scale with magic attack (staff + own magic) instead of attack (weapon + own). */
    public static final java.util.Set<String> MAGIC_JOBS = java.util.Set.of("wizard", "healer", "enchanter", "spellmaster", "runemaster", "bishop", "bard", "maestro", "spellsinger");

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
    private static final java.util.Set<String> DAMAGE_TOO = java.util.Set.of("decoy", "reprisal", "grand_rune", "counter_stance", "parry",
            "foresight", "counter_lance", "symphony", "mimic", "fortress", "berserk");
    /** Damage skills that keep their ratio (see SKILL_DAMAGE): the wizard's plain bolt is its normal attack. */
    private static final java.util.Set<String> UNSCALED = java.util.Set.of("magic_bolt", "reprisal"); // reprisal: 15% of attack, as asked

    /** Mana every job starts with before job / level / panel bonuses. */
    public static final double BASE_MANA = 100;
    public static final double BASE_MANA_REGEN = 1.0;
    /** Extra MP a second for the casters (every job with MP of its own; the physical jobs win theirs back by hitting). */
    public static final double CASTER_MANA_REGEN = 0.5;
    // Attack and magic attack from levels: 0.6 a level counted in whole points (+1 at almost every
    // level, see JobStats); casters' melee and the enchanter's dagger 0.3. Weapon materials multiply it.

    // ------------------------------------------------------------------ definitions

    static {
        // ---- Fighter: heavy melee
        job("fighter", 0xD9534F, "闘", List.of(JobStat.MAX_HEALTH, JobStat.ATTACK_DAMAGE, JobStat.ARMOR),
                stats(JobStat.MAX_HEALTH, 4, JobStat.ATTACK_DAMAGE, 6), stats(JobStat.MAX_HEALTH, hpGrow(1.0, 4), JobStat.ATTACK_DAMAGE, 0.6),
                List.of(
                        skill("power_slash", SkillKind.ACTIVE, SkillEffect.SWEEP, 4, 15, 3.0, 4.5, 0, List.of(), 0, "斬"),
                        skill("charge", SkillKind.ACTIVE, SkillEffect.DASH, 4, 8, 2.0, 0, 0, List.of(), 0, "突"),
                        skill("war_cry", SkillKind.ACTIVE, SkillEffect.BUFF, 20, 20, 0, 8, 20, List.of("rpgmod:attack_up"), 0, "鬨"),
                        skill("whirlwind", SkillKind.ACTIVE, SkillEffect.WHIRL, 5, 15, 1.6, 4.5, 0, List.of(), 0, "旋"),
                        skill("intimidate", SkillKind.ACTIVE, SkillEffect.SWEEP, 8, 15, 1.0, 4.5, 8, List.of("rpgmod:attack_down"), 1, "圧"),
                        skill("taunt", SkillKind.ACTIVE, SkillEffect.BUFF, 8, 15, 0, 10, 6, List.of("minecraft:resistance"), 0, "挑")),
                List.of(
                        skill("berserk", SkillKind.ULTIMATE, SkillEffect.BUFF, 60, 40, 3.0, 14, 15, List.of(), 0, "神"),
                        skill("earthshatter", SkillKind.ULTIMATE, SkillEffect.WHIRL, 60, 40, 8.0, 14, 4, List.of("minecraft:slowness"), 1, "砕")),
                List.of());
        // ---- Ranger: bows
        job("ranger", 0x5CB85C, "弓", List.of(JobStat.ATTACK_DAMAGE, JobStat.MOVEMENT_SPEED, JobStat.CRIT_CHANCE),
                stats(JobStat.MOVEMENT_SPEED, 5, JobStat.CRIT_CHANCE, 3, JobStat.ATTACK_DAMAGE, 3), stats(JobStat.CRIT_CHANCE, 0.05, JobStat.ATTACK_DAMAGE, 0.6, JobStat.MAX_HEALTH, hpGrow(1.0, 0)),
                List.of(
                        skill("multishot", SkillKind.ACTIVE, SkillEffect.SHOT, 3, 8, 1.6, 0, 0, List.of(), 0, "連"),
                        skill("backstep", SkillKind.ACTIVE, SkillEffect.BACKSTEP, 3.5, 8, 1.4, 0, 0, List.of(), 0, "退"),
                        skill("hunters_mark", SkillKind.ACTIVE, SkillEffect.DEBUFF, 12, 15, 0, 128, 12, List.of("minecraft:glowing"), 0, "探"),
                        skill("snare", SkillKind.ACTIVE, SkillEffect.DEBUFF, 8, 15, 0.8, 3.5, 4, List.of("minecraft:slowness"), 2, "罠"),
                        skill("piercing_shot", SkillKind.ACTIVE, SkillEffect.BOLT, 4, 15, 3.2, 30, 0, List.of(), 0, "貫"),
                        skill("explosive_arrow", SkillKind.ACTIVE, SkillEffect.SHOT, 5, 15, 2.4, 3, 0, List.of(), 0, "爆")),
                List.of(
                        skill("arrow_rain", SkillKind.ULTIMATE, SkillEffect.SHOT, 60, 40, 1.2, 10, 0, List.of(), 0, "雨"),
                        skill("eagle_eye", SkillKind.ULTIMATE, SkillEffect.BUFF, 60, 40, 0, 0, 15, List.of("rpgmod:attack_up", "minecraft:speed", "minecraft:night_vision"), 1, "鷹")),
                List.of());
        // ---- Paladin: front line, protects allies
        job("paladin", 0xF0C040, "聖", List.of(JobStat.MAX_HEALTH, JobStat.ARMOR, JobStat.ATTACK_DAMAGE, JobStat.COOLDOWN_REDUCTION),
                stats(JobStat.MAX_HEALTH, 6, JobStat.ARMOR, 4), stats(JobStat.MAX_HEALTH, hpGrow(1.2, 6), JobStat.ATTACK_DAMAGE, 0.6),
                List.of(
                        skill("shield_bash", SkillKind.ACTIVE, SkillEffect.SWEEP, 6, 8, 1.6, 4, 3, List.of("minecraft:slowness"), 1, "打"),
                        skill("holy_guard", SkillKind.ACTIVE, SkillEffect.BUFF, 18, 20, 0, 7, 16, List.of("minecraft:resistance"), 0, "護"),
                        skill("smite", SkillKind.ACTIVE, SkillEffect.SWEEP, 4, 15, 3.2, 8, 0, List.of(), 0, "裁"),
                        skill("lay_on_hands", SkillKind.ACTIVE, SkillEffect.HEAL, 7.5, 20, 0.9, 5, 0, List.of(), 0, "手"),
                        skill("guardian_dome", SkillKind.ACTIVE, SkillEffect.BUFF, 30, 25, 0, 5, 24, List.of(), 0, "囲"),
                        skill("holy_wave", SkillKind.ACTIVE, SkillEffect.SWEEP, 5, 15, 2.0, 10, 0, List.of(), 0, "波")),
                List.of(
                        skill("divine_aegis", SkillKind.ULTIMATE, SkillEffect.BUFF, 75, 40, 0, 48, 6, List.of(), 0, "盾"),
                        skill("judgement", SkillKind.ULTIMATE, SkillEffect.WHIRL, 60, 40, 6.0, 13, 0, List.of(), 0, "審")),
                List.of());
        // ---- Wizard: spells on the staff, skills 1/2 for mana and mobility
        job("wizard", 0x7B68EE, "魔", List.of(JobStat.MAX_MANA, JobStat.MAGIC_ATTACK, JobStat.COOLDOWN_REDUCTION, JobStat.CAST_SPEED, JobStat.MANA_REGEN),
                stats(JobStat.MAX_MANA, 50, JobStat.MANA_REGEN, 0.5, JobStat.MAGIC_ATTACK, 6), stats(JobStat.MAX_MANA, 1, JobStat.MAGIC_ATTACK, 0.6, JobStat.ATTACK_DAMAGE, 0.3, JobStat.MAX_HEALTH, hpGrow(0.6, 0)),
                List.of(
                        skill("mana_surge", SkillKind.ACTIVE, SkillEffect.MANA, 10, 0, 0.4, 0, 0, List.of(), 0, "充"),
                        skill("blink", SkillKind.ACTIVE, SkillEffect.BLINK, 8, 21, 0, 10, 0, List.of(), 0, "瞬"),
                        skill("mana_barrier", SkillKind.ACTIVE, SkillEffect.BUFF, 25, 42, 0, 0, 10, List.of("minecraft:absorption"), 1, "障"),
                        skill("arcane_focus", SkillKind.ACTIVE, SkillEffect.MANA, 30, 0, 25, 6, 0, List.of(), 0, "集"),
                        skill("quick_cast", SkillKind.ACTIVE, SkillEffect.BUFF, 30, 28, 0, 0, 12, List.of("rpgmod:quick_cast"), 0, "速"),
                        skill("chrono", SkillKind.ACTIVE, SkillEffect.BUFF, 45, 42, 0.5, 0, 0, List.of(), 0, "時")),
                List.of(
                        skill("meteor", SkillKind.ULTIMATE, SkillEffect.WHIRL, 100, 112, 9.0, 11, 5, List.of(), 0, "隕"),
                        skill("time_warp", SkillKind.ULTIMATE, SkillEffect.DEBUFF, 90, 84, 0, 20, 3, List.of("minecraft:slowness", "minecraft:weakness"), 3, "止")),
                List.of(
                        skill("magic_bolt", SkillKind.SPELL, SkillEffect.BOLT, 0.3, 5, 1.1, 28, 0, List.of(), 0, "弾"),
                        skill("flame_burst", SkillKind.SPELL, SkillEffect.WHIRL, 9, 28, 3.78, 7, 4, List.of(), 0, "炎"),
                        skill("frost_nova", SkillKind.SPELL, SkillEffect.DEBUFF, 8, 28, 1.5, 6, 6, List.of("minecraft:slowness"), 2, "氷"),
                        skill("thunder", SkillKind.SPELL, SkillEffect.LIGHTNING, 15, 42, 8.1, 28, 0, List.of(), 0, "雷"),
                        skill("arcane_shield", SkillKind.SPELL, SkillEffect.BUFF, 20, 35, 0, 0, 15, List.of("minecraft:absorption", "minecraft:resistance"), 1, "護"),
                        skill("arcane_missiles", SkillKind.SPELL, SkillEffect.BOLT, 5, 26, 0.75, 24, 0, List.of(), 0, "矢"),
                        skill("blizzard", SkillKind.SPELL, SkillEffect.DEBUFF, 14, 49, 0.525, 4, 2, List.of("minecraft:slowness"), 1, "嵐"),
                        skill("gravity", SkillKind.SPELL, SkillEffect.WHIRL, 18, 42, 6.48, 5, 0, List.of(), 0, "重")));
        // ---- Healer: heals (spells charged on the staff) and buffs
        job("healer", 0x5BC0DE, "癒", List.of(JobStat.MAX_MANA, JobStat.MAGIC_ATTACK, JobStat.MANA_REGEN, JobStat.MAX_HEALTH),
                stats(JobStat.MAX_MANA, 40, JobStat.MAGIC_ATTACK, 3), stats(JobStat.MAGIC_ATTACK, 0.6, JobStat.ATTACK_DAMAGE, 0.6, JobStat.MAX_MANA, 0.75, JobStat.MAX_HEALTH, hpGrow(0.6, 0)),
                List.of(
                        skill("holy_light", SkillKind.ACTIVE, SkillEffect.BOLT, 6, 14, 1.8, 24, 0, List.of(), 0, "照"),
                        skill("blessing", SkillKind.ACTIVE, SkillEffect.BUFF, 20, 35, 0, 8, 14, List.of("minecraft:regeneration"), 0, "祝"),
                        skill("mana_gift", SkillKind.ACTIVE, SkillEffect.MANA, 25, 0, 30, 8, 0, List.of(), 0, "恵"),
                        skill("nourish", SkillKind.ACTIVE, SkillEffect.BUFF, 30, 35, 4, 8, 0, List.of(), 0, "糧"),
                        skill("holy_barrier", SkillKind.ACTIVE, SkillEffect.BUFF, 20, 28, 0, 8, 15, List.of(), 0, "盾"),
                        skill("guardian_angel", SkillKind.ACTIVE, SkillEffect.BUFF, 60, 35, 0, 12, 5, List.of(), 0, "天")),
                List.of(
                        skill("sanctuary", SkillKind.ULTIMATE, SkillEffect.HEAL, 100, 84, 2.8, 12, 0, List.of(), 0, "域"),
                        skill("guardian_light", SkillKind.ULTIMATE, SkillEffect.BUFF, 100, 84, 0, 20, 15, List.of(), 0, "光")),
                List.of(
                        skill("heal", SkillKind.SPELL, SkillEffect.HEAL, 6, 21, 0.9, 4, 0, List.of(), 0, "癒"),
                        skill("group_heal", SkillKind.SPELL, SkillEffect.HEAL, 12, 42, 0.6, 10, 0, List.of(), 0, "群"),
                        skill("purify", SkillKind.SPELL, SkillEffect.HEAL, 18, 28, 0.4, 8, 0, List.of(), 0, "浄"),
                        // the heal's strong form: 2.5x the healing, a longer charge and much more mana
                        skill("greater_heal", SkillKind.SPELL, SkillEffect.HEAL, 6, 50, 2.25, 4, 0, List.of(), 0, "快")));
        // ---- Assassin: daggers, burst and stealth
        job("assassin", 0x8E44AD, "影", List.of(JobStat.CRIT_CHANCE, JobStat.ATTACK_DAMAGE, JobStat.ATTACK_SPEED, JobStat.MOVEMENT_SPEED),
                stats(JobStat.CRIT_CHANCE, 5, JobStat.MOVEMENT_SPEED, 5, JobStat.ATTACK_DAMAGE, 3), stats(JobStat.CRIT_CHANCE, 0.075, JobStat.ATTACK_DAMAGE, 0.6, JobStat.MAX_HEALTH, hpGrow(1.0, 0)),
                List.of(
                        skill("shadow_step", SkillKind.ACTIVE, SkillEffect.BLINK, 3.5, 8, 0, 12, 0, List.of(), 0, "影"),
                        skill("backstab", SkillKind.ACTIVE, SkillEffect.SWEEP, 3.5, 15, 3.0, 3.5, 0, List.of(), 0, "急"),
                        skill("smoke_bomb", SkillKind.ACTIVE, SkillEffect.STEALTH, 7.5, 20, 0, 0, 5, List.of("minecraft:speed"), 1, "煙"),
                        skill("poison_coat", SkillKind.ACTIVE, SkillEffect.BUFF, 30, 12, 0, 0, 60, List.of(), 0, "毒"),
                        skill("throwing_knives", SkillKind.ACTIVE, SkillEffect.SHOT, 4, 15, 1.2, 16, 0, List.of(), 0, "投"),
                        skill("shadow_bind", SkillKind.ACTIVE, SkillEffect.DEBUFF, 7, 15, 1.0, 12, 3, List.of(), 0, "縛")),
                List.of(
                        skill("assassinate", SkillKind.ULTIMATE, SkillEffect.SWEEP, 60, 40, 7.0, 12, 0, List.of(), 0, "殺"),
                        skill("shadow_dance", SkillKind.ULTIMATE, SkillEffect.BUFF, 60, 40, 0, 0, 10, List.of(), 0, "舞")),
                List.of());
        // ---- Enchanter: buffs allies, weakens enemies
        job("enchanter", 0xE67EB5, "付", List.of(JobStat.MANA_REGEN, JobStat.MAGIC_ATTACK, JobStat.COOLDOWN_REDUCTION, JobStat.MAX_MANA),
                stats(JobStat.COOLDOWN_REDUCTION, 5, JobStat.MAX_MANA, 30, JobStat.MAGIC_ATTACK, 3), stats(JobStat.MAGIC_ATTACK, 0.6, JobStat.ATTACK_DAMAGE, 0.3, JobStat.COOLDOWN_REDUCTION, 0.05, JobStat.MAX_HEALTH, hpGrow(0.6, 0)),
                List.of(
                        skill("empower", SkillKind.ACTIVE, SkillEffect.BUFF, 18, 35, 0, 8, 20, List.of("rpgmod:attack_up"), 0, "強"),
                        skill("haste", SkillKind.ACTIVE, SkillEffect.BUFF, 18, 35, 0, 8, 20, List.of("minecraft:speed", "minecraft:haste"), 0, "速"),
                        skill("weaken", SkillKind.ACTIVE, SkillEffect.DEBUFF, 15, 28, 0, 8, 8, List.of("minecraft:weakness"), 0, "弱"),
                        skill("mana_link", SkillKind.ACTIVE, SkillEffect.MANA, 25, 0, 20, 8, 0, List.of(), 0, "繋"),
                        skill("fire_weapon", SkillKind.ACTIVE, SkillEffect.BUFF, 25, 42, 0, 8, 30, List.of("rpgmod:fire_weapon"), 0, "焔"),
                        skill("binding_field", SkillKind.ACTIVE, SkillEffect.DEBUFF, 18, 35, 0, 4, 5, List.of("rpgmod:attack_down"), 0, "陣")),
                List.of(
                        skill("grand_enchant", SkillKind.ULTIMATE, SkillEffect.BUFF, 100, 84, 0, 20, 30, List.of("rpgmod:attack_up", "minecraft:resistance", "minecraft:speed"), 1, "極"),
                        skill("hex", SkillKind.ULTIMATE, SkillEffect.DEBUFF, 90, 84, 1.2, 20, 10, List.of("minecraft:weakness", "minecraft:slowness"), 1, "呪")),
                List.of());
        // ---- Gambler: random results
        job("gambler", 0x1ABC9C, "賭", List.of(JobStat.CRIT_CHANCE, JobStat.ATTACK_DAMAGE, JobStat.LUCK, JobStat.COOLDOWN_REDUCTION),
                stats(JobStat.LUCK, 2, JobStat.CRIT_CHANCE, 5), stats(JobStat.LUCK, 0.025, JobStat.CRIT_CHANCE, 0.05, JobStat.ATTACK_DAMAGE, 0.6, JobStat.MAX_HEALTH, hpGrow(1.0, 0)),
                List.of(
                        skill("coin_toss", SkillKind.ACTIVE, SkillEffect.GAMBLE, 6, 8, 2, 6, 6, List.of(), 0, "銭"),
                        skill("lucky_shot", SkillKind.ACTIVE, SkillEffect.SHOT, 3, 8, 2.0, 0, 0, List.of(), 0, "幸"),
                        skill("dice_roll", SkillKind.ACTIVE, SkillEffect.GAMBLE, 8, 15, 3.0, 6, 8, List.of(), 0, "骰"),
                        skill("all_in", SkillKind.ACTIVE, SkillEffect.GAMBLE, 10, 20, 5.0, 8, 10, List.of(), 0, "賭"),
                        skill("russian_roulette", SkillKind.ACTIVE, SkillEffect.SHOT, 2, 5, 6.0, 16, 0, List.of(), 0, "銃"),
                        skill("card_throw", SkillKind.ACTIVE, SkillEffect.SHOT, 4.5, 15, 1.8, 16, 0, List.of(), 0, "札")),
                List.of(
                        skill("jackpot", SkillKind.ULTIMATE, SkillEffect.GAMBLE, 60, 40, 8, 20, 15, List.of(), 0, "当"),
                        skill("roulette", SkillKind.ULTIMATE, SkillEffect.BUFF, 60, 40, 0, 0, 12, List.of(), 0, "輪")),
                List.of());
        // ---- Pirate: a gunblade - shots from range, the bayonet up close (and it reloads the gun)
        job("pirate", 0x2E86C1, "海", List.of(JobStat.ATTACK_DAMAGE, JobStat.CRIT_CHANCE, JobStat.MAX_HEALTH, JobStat.ATTACK_SPEED),
                stats(JobStat.MAX_HEALTH, 3, JobStat.ATTACK_DAMAGE, 4, JobStat.CRIT_CHANCE, 3), stats(JobStat.ATTACK_DAMAGE, 0.6, JobStat.MAX_HEALTH, hpGrow(0.9, 3)),
                List.of(
                        skill("boarding_shot", SkillKind.ACTIVE, SkillEffect.DASH, 3.5, 8, 2.4, 3.5, 0, List.of(), 0, "撃"),
                        skill("quick_draw", SkillKind.ACTIVE, SkillEffect.SHOT, 5, 15, 1.2, 32, 0, List.of(), 0, "速"),
                        skill("cannon", SkillKind.ACTIVE, SkillEffect.SHOT, 10, 22, 1.6, 16, 10, List.of(), 0, "砲"),
                        skill("powder_keg", SkillKind.ACTIVE, SkillEffect.WHIRL, 6, 22, 3.0, 3.5, 0, List.of(), 0, "樽"),
                        skill("grapple", SkillKind.ACTIVE, SkillEffect.DASH, 4, 8, 0.8, 20, 0, List.of(), 0, "鉤"),
                        skill("rum", SkillKind.ACTIVE, SkillEffect.BUFF, 20, 15, 0, 0, 16, List.of("minecraft:haste"), 1, "酒")),
                List.of(
                        skill("broadside", SkillKind.ULTIMATE, SkillEffect.WHIRL, 60, 40, 1.2, 14, 4, List.of(), 0, "斉"),
                        skill("jolly_roger", SkillKind.ULTIMATE, SkillEffect.BUFF, 60, 40, 0, 20, 15, List.of(), 0, "旗")),
                List.of());
        // ---- Lancer (the spear jobs' skills are a quarter stronger than they first were: lancer, dragoon, sentinel): long reach and charges; hits from the far end of the spear count for more
        job("lancer", 0xC0703A, "槍", List.of(JobStat.ATTACK_DAMAGE, JobStat.MAX_HEALTH, JobStat.ARMOR, JobStat.MOVEMENT_SPEED),
                stats(JobStat.MAX_HEALTH, 4, JobStat.ATTACK_DAMAGE, 6), stats(JobStat.ATTACK_DAMAGE, 0.6, JobStat.MAX_HEALTH, hpGrow(1.0, 4)),
                List.of(
                        skill("lance_charge", SkillKind.ACTIVE, SkillEffect.DASH, 4, 8, 1.6, 8, 0, List.of(), 0, "突"),
                        skill("spear_throw", SkillKind.ACTIVE, SkillEffect.BOLT, 4.5, 15, 3.25, 24, 0, List.of(), 0, "投"),
                        skill("spear_sweep", SkillKind.ACTIVE, SkillEffect.SWEEP, 4, 15, 2.25, 5.5, 0, List.of(), 0, "薙"),
                        skill("leap_thrust", SkillKind.ACTIVE, SkillEffect.WHIRL, 6, 22, 3.25, 2.5, 0, List.of(), 0, "跳"),
                        skill("counter_stance", SkillKind.ACTIVE, SkillEffect.BUFF, 7, 8, 3.75, 5, 2, List.of(), 0, "構"),
                        skill("rapid_thrust", SkillKind.ACTIVE, SkillEffect.SWEEP, 5, 15, 0.9, 6, 0, List.of(), 0, "連")),
                List.of(
                        skill("dragon_fang", SkillKind.ULTIMATE, SkillEffect.WHIRL, 60, 40, 7.5, 6, 3, List.of(), 0, "竜"),
                        skill("spear_rain", SkillKind.ULTIMATE, SkillEffect.WHIRL, 60, 40, 1.15, 12, 4.5, List.of(), 0, "雨")),
                List.of());
        // ---- Monk: fast fists; hits build ki, the big moves spend it
        job("monk", 0xE67E22, "拳", List.of(JobStat.ATTACK_DAMAGE, JobStat.ATTACK_SPEED, JobStat.CRIT_CHANCE, JobStat.MOVEMENT_SPEED),
                stats(JobStat.MAX_HEALTH, 2, JobStat.ATTACK_DAMAGE, 4, JobStat.ATTACK_SPEED, 10, JobStat.MOVEMENT_SPEED, 5),
                stats(JobStat.ATTACK_DAMAGE, 0.6, JobStat.MAX_HEALTH, hpGrow(1.0, 2)),
                List.of(
                        skill("combo", SkillKind.ACTIVE, SkillEffect.SWEEP, 2.5, 8, 0.9, 3.5, 0, List.of(), 0, "拳"),
                        skill("ki_blast", SkillKind.ACTIVE, SkillEffect.SWEEP, 4, 15, 1.0, 4, 0, List.of(), 0, "勁"),
                        skill("parry", SkillKind.ACTIVE, SkillEffect.BUFF, 5, 8, 1.5, 0, 1.2, List.of(), 0, "流"),
                        skill("whirl_kick", SkillKind.ACTIVE, SkillEffect.WHIRL, 4.5, 15, 1.5, 3.5, 0, List.of(), 0, "脚"),
                        skill("meditate", SkillKind.ACTIVE, SkillEffect.BUFF, 10, 8, 0, 0, 3, List.of(), 0, "瞑"),
                        skill("shrink_step", SkillKind.ACTIVE, SkillEffect.DASH, 3.5, 8, 1.2, 7, 0, List.of(), 0, "縮")),
                List.of(
                        skill("hundred_fists", SkillKind.ULTIMATE, SkillEffect.SWEEP, 60, 40, 0.45, 8, 3, List.of(), 0, "百"),
                        skill("diamond_body", SkillKind.ULTIMATE, SkillEffect.BUFF, 75, 40, 0, 0, 6, List.of(), 0, "剛")),
                List.of());
        // ---- Bard: songs around the party, stronger the better they are played (the note lane)
        // MP stays at 100 like the physical jobs (no MP growth, no MP cells)
        job("bard", 0x48C9B0, "詩", List.of(JobStat.MAGIC_ATTACK, JobStat.MANA_REGEN, JobStat.COOLDOWN_REDUCTION, JobStat.MAX_HEALTH),
                stats(JobStat.MAGIC_ATTACK, 3, JobStat.MANA_REGEN, 0.5),
                stats(JobStat.MAGIC_ATTACK, 0.6, JobStat.ATTACK_DAMAGE, 0.3, JobStat.MAX_HEALTH, hpGrow(0.6, 0)),
                List.of(
                        // the songs are set on the lute (like spells on a staff); "perform" plays the selected one
                        skill("perform", SkillKind.ACTIVE, SkillEffect.BUFF, 3, 0, 0, 0, 12, List.of(), 0, "奏"),
                        skill("sonic_boom", SkillKind.ACTIVE, SkillEffect.SWEEP, 8, 14, 1.6, 6, 0, List.of(), 0, "響"),
                        skill("cheer", SkillKind.ACTIVE, SkillEffect.BUFF, 20, 20, 0, 8, 5, List.of(), 0, "鼓")),
                List.of(
                        skill("grand_concert", SkillKind.ULTIMATE, SkillEffect.BUFF, 90, 50, 0.8, 20, 10, List.of(), 0, "合"),
                        skill("finale", SkillKind.ULTIMATE, SkillEffect.WHIRL, 90, 42, 36.0, 10, 0, List.of(), 0, "幕")),
                List.of(
                        skill("war_song", SkillKind.SPELL, SkillEffect.BUFF, 3, 14, 0, 6, 12, List.of(), 0, "戦"),
                        skill("heal_song", SkillKind.SPELL, SkillEffect.BUFF, 3, 18, 0, 6, 12, List.of(), 0, "癒"),
                        skill("guard_song", SkillKind.SPELL, SkillEffect.BUFF, 3, 14, 0, 6, 12, List.of(), 0, "守"),
                        skill("wind_song", SkillKind.SPELL, SkillEffect.BUFF, 3, 14, 0, 6, 12, List.of(), 0, "風"),
                        skill("discord", SkillKind.SPELL, SkillEffect.DEBUFF, 3, 18, 0.5, 6, 12, List.of(), 0, "乱"),
                        skill("lullaby", SkillKind.SPELL, SkillEffect.DEBUFF, 3, 18, 0, 6, 12, List.of("minecraft:slowness", "rpgmod:attack_down"), 3, "眠"),
                        skill("rhapsody", SkillKind.SPELL, SkillEffect.BUFF, 3, 16, 0, 6, 12, List.of(), 0, "狂"),
                        skill("fanfare", SkillKind.SPELL, SkillEffect.BUFF, 3, 20, 0, 6, 12, List.of(), 0, "凱"),
                        skill("dirge", SkillKind.SPELL, SkillEffect.DEBUFF, 3, 20, 0.4, 6, 12, List.of(), 0, "葬"),
                        skill("harvest_song", SkillKind.SPELL, SkillEffect.BUFF, 3, 16, 0, 6, 12, List.of(), 0, "穣")));
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
                stats(JobStat.CRIT_CHANCE, 0.05),
                List.of(
                        skill("blood_frenzy", A, SkillEffect.BUFF, 22, 20, 0.15, 0, 24, List.of("rpgmod:attack_up", "minecraft:haste"), 1, "血"),
                        skill("decapitate", A, SkillEffect.SWEEP, 4, 15, 4.0, 4, 0, List.of(), 0, "断"),
                        skill("triple_whirl", A, SkillEffect.WHIRL, 6, 22, 1.8, 5, 0, List.of(), 0, "渦"),
                        skill("reckless_charge", A, SkillEffect.DASH, 5, 15, 3.0, 0, 4, List.of(), 0, "捨"),
                        skill("terror_roar", A, SkillEffect.DEBUFF, 10, 15, 0, 8, 10, List.of("rpgmod:attack_down", "minecraft:slowness"), 1, "咆")),
                List.of(
                        skill("asura", U, SkillEffect.BUFF, 60, 40, 0, 0, 15, List.of(), 0, "修")),
                List.of());
        // ---- Guardian: stands in front and takes the blows for everyone
        advanced("paladin", "guardian", 0xC8A030, "護", List.of(JobStat.ARMOR, JobStat.MAX_HEALTH, JobStat.ATTACK_DAMAGE),
                stats(),
                List.of(
                        skill("iron_wall", A, SkillEffect.BUFF, 25, 20, 0, 0, 16, List.of(), 0, "壁"),
                        skill("provoke_shield", A, SkillEffect.BUFF, 20, 20, 0, 12, 20, List.of(), 1, "挑"),
                        skill("cover", A, SkillEffect.BUFF, 20, 18, 0, 10, 20, List.of(), 0, "庇"),
                        skill("shield_wall", A, SkillEffect.BUFF, 18, 20, 0, 4, 16, List.of(), 0, "垣"),
                        skill("reprisal", A, SkillEffect.BUFF, 18, 20, 0.15, 0, 16, List.of(), 0, "返")),
                List.of(
                        skill("bastion", U, SkillEffect.BUFF, 75, 40, 0, 20, 12, List.of("minecraft:resistance", "minecraft:absorption"), 2, "塞")),
                List.of());
        // ---- Sharpshooter: long range, heavy single shots
        advanced("ranger", "sharpshooter", 0x3E9E4A, "狙", List.of(JobStat.ATTACK_DAMAGE, JobStat.CRIT_CHANCE, JobStat.MOVEMENT_SPEED),
                stats(JobStat.CRIT_CHANCE, 0.05),
                List.of(
                        skill("snipe", A, SkillEffect.SHOT, 5, 15, 6.0, 64, 0, List.of(), 0, "狙"),
                        skill("rapid_fire", A, SkillEffect.SHOT, 4.5, 15, 0.7, 0, 0, List.of(), 0, "速"),
                        skill("weak_point", A, SkillEffect.DEBUFF, 10, 15, 0, 32, 10, List.of("minecraft:glowing"), 0, "看"),
                        skill("ricochet", A, SkillEffect.BOLT, 4, 15, 2.2, 28, 0, List.of(), 0, "跳"),
                        skill("roll", A, SkillEffect.BACKSTEP, 6, 8, 1.6, 0, 6, List.of(), 0, "転")),
                List.of(
                        skill("dead_eye", U, SkillEffect.SHOT, 60, 40, 2.5, 40, 0, List.of(), 0, "眼")),
                List.of());
        // ---- Spellmaster: the strongest spells, slow to charge; two at once
        advanced("wizard", "spellmaster", 0x5A3FD8, "極", List.of(JobStat.MAGIC_ATTACK, JobStat.CAST_SPEED, JobStat.MAX_MANA, JobStat.MANA_REGEN),
                stats(JobStat.MAX_MANA, 1),
                List.of(
                        skill("double_cast", A, SkillEffect.BUFF, 30, 30, 0, 0, 15, List.of(), 1, "重"),
                        skill("mana_overload", A, SkillEffect.BUFF, 45, 0, 0, 0, 12, List.of(), 0, "暴"),
                        skill("mana_well", A, SkillEffect.MANA, 40, 0, 6, 5, 8, List.of(), 0, "泉"),
                        skill("phase_shift", A, SkillEffect.BLINK, 12, 20, 0, 14, 0, List.of(), 0, "位"),
                        skill("arcane_seal", A, SkillEffect.DEBUFF, 22, 30, 0, 6, 8, List.of("minecraft:slowness", "rpgmod:exposed"), 0, "封")),
                List.of(
                        skill("armageddon", U, SkillEffect.BOLT, 120, 140, 12.0, 30, 3, List.of(), 0, "滅")),
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
                stats(JobStat.MAX_MANA, 0.5),
                List.of(
                        skill("earth_rune", A, SkillEffect.DEBUFF, 20, 35, 0.3, 6, 8, List.of(), 1, "地"),
                        skill("ward_rune", A, SkillEffect.BUFF, 30, 40, 0, 5, 10, List.of(), 0, "守"),
                        skill("gale_rune", A, SkillEffect.BUFF, 25, 30, 0, 10, 20, List.of("minecraft:speed", "minecraft:jump_boost"), 1, "疾"),
                        skill("night_sight", A, SkillEffect.BUFF, 60, 30, 0, 12, 300, List.of("minecraft:night_vision", "minecraft:water_breathing"), 0, "視"),
                        skill("feather_rune", A, SkillEffect.BUFF, 60, 30, 0, 12, 120, List.of("rpgmod:feather"), 0, "羽")),
                List.of(
                        skill("grand_rune", U, SkillEffect.BUFF, 120, 100, 0.6, 24, 22.5, List.of(), 0, "環")),
                List.of());
        // ---- Joker: tricks - swaps, decoys and a loaded die
        advanced("gambler", "joker", 0x7A3FB0, "奇", List.of(JobStat.LUCK, JobStat.CRIT_CHANCE, JobStat.COOLDOWN_REDUCTION, JobStat.ATTACK_DAMAGE),
                stats(JobStat.LUCK, 0.02, JobStat.CRIT_CHANCE, 0.05),
                List.of(
                        skill("switcheroo", A, SkillEffect.BLINK, 6, 15, 0, 24, 3, List.of(), 0, "換"),
                        skill("decoy", A, SkillEffect.BUFF, 11, 22, 3.0, 4, 5, List.of(), 0, "分"),
                        skill("loaded_dice", A, SkillEffect.BUFF, 35, 20, 0, 0, 30, List.of(), 0, "詐"),
                        skill("card_storm", A, SkillEffect.WHIRL, 7, 22, 0.5, 5, 3, List.of(), 0, "嵐"),
                        skill("vanish", A, SkillEffect.STEALTH, 7.5, 20, 0, 16, 3, List.of(), 0, "消")),
                List.of(
                        skill("wildcard", U, SkillEffect.GAMBLE, 60, 40, 0, 0, 0, List.of(), 0, "J")),
                List.of());
        // ---- Nightblade: two blades and the dark
        advanced("assassin", "nightblade", 0x2E3A6E, "夜", List.of(JobStat.ATTACK_DAMAGE, JobStat.CRIT_CHANCE, JobStat.MOVEMENT_SPEED, JobStat.ATTACK_SPEED),
                stats(JobStat.CRIT_CHANCE, 0.05),
                List.of(
                        skill("shadow_run", A, SkillEffect.DASH, 4, 15, 2.5, 9, 0, List.of(), 0, "走"),
                        skill("flurry", A, SkillEffect.SWEEP, 5, 15, 0.6, 4, 0, List.of(), 0, "乱"),
                        skill("twin_venom", A, SkillEffect.BUFF, 35, 18, 0, 0, 30, List.of(), 0, "双"),
                        skill("shadow_stitch", A, SkillEffect.DEBUFF, 8, 15, 1.5, 20, 3, List.of(), 0, "縫"),
                        skill("moon_shadow", A, SkillEffect.SWEEP, 6, 22, 3.5, 16, 0, List.of(), 0, "月")),
                List.of(
                        skill("yaksha", U, SkillEffect.BUFF, 60, 40, 0, 0, 12, List.of("minecraft:speed"), 0, "叉")),
                List.of());
        // ---- Bishop: holy magic from the staff, and miracles
        advanced("healer", "bishop", 0x3A8FD0, "聖", List.of(JobStat.MAGIC_ATTACK, JobStat.MAX_MANA, JobStat.CAST_SPEED, JobStat.MANA_REGEN),
                stats(JobStat.MAX_MANA, 0.75),
                List.of(
                        skill("revive_prayer", A, SkillEffect.HEAL, 120, 60, 1.0, 12, 0, List.of(), 0, "蘇"),
                        skill("divine_domain", A, SkillEffect.BUFF, 45, 50, 0, 6, 10, List.of(), 0, "神")),
                List.of(
                        skill("great_miracle", U, SkillEffect.HEAL, 150, 120, 0, 60, 10, List.of("minecraft:resistance", "minecraft:regeneration"), 1, "跡")),
                List.of(
                        skill("holy_bolt", S, SkillEffect.BOLT, 0.8, 8, 1.6, 28, 0, List.of(), 0, "聖"),
                        skill("holy_nova", S, SkillEffect.WHIRL, 10, 40, 3.0, 7, 0, List.of(), 0, "輝"),
                        skill("light_pillar", S, SkillEffect.WHIRL, 14, 50, 1.0, 3, 0, List.of(), 0, "柱"),
                        // the group heal's strong form: 2.5x the healing, further, and it clears poison and wither
                        skill("grand_heal", S, SkillEffect.HEAL, 14, 85, 1.5, 12, 0, List.of(), 0, "讃")));
        // ---- Captain: two cannons, a crew to command, heavy shots
        advanced("pirate", "captain", 0x1F5F99, "船", List.of(JobStat.ATTACK_DAMAGE, JobStat.CRIT_CHANCE, JobStat.ATTACK_SPEED, JobStat.MAX_HEALTH),
                stats(JobStat.CRIT_CHANCE, 0.05),
                List.of(
                        skill("twin_cannon", A, SkillEffect.SHOT, 12, 25, 1.6, 16, 12, List.of(), 0, "双"),
                        skill("scattershot", A, SkillEffect.SWEEP, 4, 15, 0.55, 8, 0, List.of(), 0, "散"),
                        skill("anchor_throw", A, SkillEffect.WHIRL, 6, 22, 2.2, 4, 0, List.of(), 0, "錨"),
                        skill("rally", A, SkillEffect.BUFF, 20, 20, 0, 10, 20, List.of("minecraft:haste", "minecraft:speed"), 1, "令"),
                        skill("deadshot", A, SkillEffect.SHOT, 5, 15, 5.0, 48, 0, List.of(), 0, "狙")),
                List.of(
                        skill("fleet_barrage", U, SkillEffect.WHIRL, 60, 40, 1.4, 18, 7.5, List.of(), 0, "艦")),
                List.of());
        // ---- Champion: seven ki and the secret arts that spend it
        advanced("monk", "champion", 0xC0561A, "覇", List.of(JobStat.ATTACK_DAMAGE, JobStat.ATTACK_SPEED, JobStat.CRIT_CHANCE, JobStat.MAX_HEALTH),
                stats(JobStat.CRIT_CHANCE, 0.05),
                List.of(
                        skill("rising_dragon", A, SkillEffect.SWEEP, 4, 15, 2.6, 3.5, 0, List.of(), 0, "昇"),
                        skill("ki_wave", A, SkillEffect.BOLT, 6, 22, 1.2, 18, 0, List.of(), 0, "波"),
                        skill("iron_mountain", A, SkillEffect.DASH, 4.5, 15, 2.4, 6, 0, List.of(), 0, "靠"),
                        skill("foresight", A, SkillEffect.BUFF, 7.5, 8, 1.2, 0, 3, List.of(), 0, "見"),
                        skill("battle_aura", A, SkillEffect.BUFF, 25, 20, 0, 0, 24, List.of(), 0, "闘")),
                List.of(
                        skill("peerless", U, SkillEffect.SWEEP, 60, 40, 1.2, 10, 0, List.of(), 0, "無")),
                List.of());
        // ---- Dragoon: fights from the sky
        advanced("lancer", "dragoon", 0x8A3A2A, "竜", List.of(JobStat.ATTACK_DAMAGE, JobStat.MAX_HEALTH, JobStat.MOVEMENT_SPEED, JobStat.CRIT_CHANCE),
                stats(),
                List.of(
                        skill("high_jump", A, SkillEffect.WHIRL, 5, 15, 5.0, 3.5, 0, List.of(), 0, "跳"),
                        skill("dragon_spear", A, SkillEffect.BOLT, 4.5, 15, 3.5, 24, 0, List.of(), 0, "爆"),
                        skill("dragon_scale", A, SkillEffect.BUFF, 20, 20, 0, 0, 20, List.of("minecraft:resistance"), 1, "鱗"),
                        skill("spiral_lance", A, SkillEffect.SWEEP, 4.5, 15, 1.25, 6, 0, List.of(), 0, "螺"),
                        skill("sky_step", A, SkillEffect.DASH, 2.5, 8, 0, 0, 0, List.of(), 0, "天")),
                List.of(
                        skill("dragon_king", U, SkillEffect.WHIRL, 60, 40, 10.0, 16, 0, List.of(), 0, "王")),
                List.of());
        // ---- Maestro: songs on top of songs
        advanced("bard", "maestro", 0x2FA38C, "指", List.of(JobStat.MAGIC_ATTACK, JobStat.MANA_REGEN, JobStat.COOLDOWN_REDUCTION, JobStat.MAX_HEALTH),
                stats(),
                List.of(
                        skill("crescendo", A, SkillEffect.BUFF, 15, 10, 0, 0, 0, List.of(), 0, "昂"),
                        skill("modulate", A, SkillEffect.BUFF, 4, 8, 0, 0, 0, List.of(), 0, "転"),
                        skill("heroic_song", A, SkillEffect.BUFF, 30, 30, 0, 12, 10, List.of("minecraft:resistance", "minecraft:speed"), 0, "英"),
                        skill("requiem", A, SkillEffect.HEAL, 40, 35, 0, 10, 10, List.of(), 0, "鎮"),
                        skill("shock_chord", A, SkillEffect.WHIRL, 8, 20, 2.4, 6, 0, List.of(), 0, "響")),
                List.of(
                        skill("symphony", U, SkillEffect.BUFF, 100, 60, 1.0, 10, 18, List.of(), 0, "楽")),
                List.of());
        // fusion spells (spellmaster, a charged spell in each hand): never learnt, the power listed
        // here is replaced by 1.2 x the two fused spells' worth (see SpellCasting)
        skill("steam_burst", S, SkillEffect.WHIRL, 0, 0, 6.0, 6, 3, List.of(), 0, "蒸");
        skill("plasma_ray", S, SkillEffect.BOLT, 0, 0, 7.0, 40, 6, List.of(), 0, "電");
        skill("frost_tempest", S, SkillEffect.WHIRL, 0, 0, 6.0, 7, 5, List.of(), 0, "凍");
    }

    // ------------------------------------------------------------------ hybrid jobs (two mastered base jobs)

    /** Hybrid job id -> its two base jobs. */
    public static final Map<String, List<String>> HYBRIDS = new LinkedHashMap<>();

    static {
        // A hybrid job takes nothing from its base jobs: its own stats, growth (over every level) and one
        // panel for all of its points, with no mastery needed. Six skills and two ultimates each,
        // a little stronger than a base job's, and three skill slots like an advanced job.
        SkillKind A = SkillKind.ACTIVE, U = SkillKind.ULTIMATE;
        // ---- Spellblade: an element on the sword; skills scale with attack + magic attack
        hybrid("spellblade", "fighter", "wizard", 0x6C5CE7, "閃", List.of(JobStat.ATTACK_DAMAGE, JobStat.MAGIC_ATTACK, JobStat.MAX_HEALTH, JobStat.CRIT_CHANCE),
                // mana like the healer's (sword magic is dear)
                stats(JobStat.MAX_HEALTH, 4, JobStat.ATTACK_DAMAGE, 4, JobStat.MAGIC_ATTACK, 4, JobStat.MAX_MANA, 40),
                stats(JobStat.ATTACK_DAMAGE, 0.45, JobStat.MAGIC_ATTACK, 0.45, JobStat.MAX_HEALTH, hpGrow(0.9, 4), JobStat.MAX_MANA, 0.75),
                List.of(
                        skill("element_shift", A, SkillEffect.BUFF, 2, 0, 0, 0, 0, List.of(), 0, "属"),
                        skill("magic_slash", A, SkillEffect.BOLT, 6, 14, 2.4, 16, 0, List.of(), 0, "斬"),
                        skill("thunder_thrust", A, SkillEffect.DASH, 8, 16, 2.2, 8, 0, List.of(), 0, "雷"),
                        skill("mana_share", A, SkillEffect.BUFF, 30, 20, 0, 24, 0, List.of(), 0, "譲"),
                        skill("mana_meditate", A, SkillEffect.BUFF, 1, 0, 0, 0, 15, List.of(), 0, "瞑"),
                        skill("spell_guard", A, SkillEffect.BUFF, 20, 18, 0, 0, 6, List.of(), 0, "護")),
                List.of(
                        skill("arcane_edge", U, SkillEffect.SWEEP, 90, 45, 7.0, 16, 0, List.of(), 0, "極"),
                        skill("elemental_burst", U, SkillEffect.WHIRL, 90, 45, 2.2, 14, 0, List.of(), 0, "解")));
        // ---- God hand (qi master): fists, and ki that heals
        hybrid("qi_master", "healer", "monk", 0xE0A040, "功", List.of(JobStat.ATTACK_DAMAGE, JobStat.MAX_HEALTH, JobStat.ATTACK_SPEED, JobStat.COOLDOWN_REDUCTION),
                stats(JobStat.MAX_HEALTH, 4, JobStat.ATTACK_DAMAGE, 4, JobStat.ATTACK_SPEED, 5),
                stats(JobStat.ATTACK_DAMAGE, 0.6, JobStat.MAX_HEALTH, hpGrow(1.0, 4)),
                List.of(
                        skill("refine_qi", A, SkillEffect.BUFF, 7.5, 8, 0, 0, 4, List.of("minecraft:regeneration"), 1, "練"),
                        skill("healing_palm", A, SkillEffect.HEAL, 7.5, 20, 1.0, 4, 0, List.of(), 0, "掌"),
                        skill("hard_qi", A, SkillEffect.BUFF, 18, 20, 0, 0, 16, List.of(), 0, "硬"),
                        skill("qi_burst", A, SkillEffect.SWEEP, 4.5, 15, 1.8, 4, 0, List.of(), 0, "勁"),
                        skill("flowing_step", A, SkillEffect.DASH, 3.5, 8, 1.0, 6, 0, List.of(), 0, "流")),
                List.of(
                        skill("heaven_earth", U, SkillEffect.HEAL, 75, 40, 2.0, 16, 0, List.of(), 0, "天"),
                        skill("blooming_fists", U, SkillEffect.WHIRL, 60, 40, 4.0, 10, 8, List.of(), 0, "花")));
        // ---- Sentinel: spear in one hand, shield in the other
        hybrid("sentinel", "paladin", "lancer", 0xB8A060, "哨", List.of(JobStat.ARMOR, JobStat.MAX_HEALTH, JobStat.ATTACK_DAMAGE, JobStat.COOLDOWN_REDUCTION),
                stats(JobStat.MAX_HEALTH, 6, JobStat.ARMOR, 4, JobStat.ATTACK_DAMAGE, 4),
                stats(JobStat.ATTACK_DAMAGE, 0.6, JobStat.MAX_HEALTH, hpGrow(1.2, 6)),
                List.of(
                        skill("guard_thrust", A, SkillEffect.SWEEP, 3, 8, 2.0, 5, 0, List.of(), 0, "突"),
                        skill("shield_charge", A, SkillEffect.DASH, 4, 8, 2.25, 7, 2, List.of("minecraft:slowness"), 4, "盾"),
                        skill("immovable", A, SkillEffect.BUFF, 16, 20, 0, 0, 12, List.of(), 0, "動"),
                        skill("spear_wall", A, SkillEffect.WHIRL, 9, 20, 0.75, 3.5, 8, List.of(), 0, "衾"),
                        skill("counter_lance", A, SkillEffect.BUFF, 6, 8, 3.25, 0, 3, List.of(), 0, "返"),
                        skill("provoke", A, SkillEffect.DEBUFF, 7.5, 15, 0, 10, 6, List.of(), 0, "挑")),
                List.of(
                        skill("fortress", U, SkillEffect.BUFF, 75, 40, 2.5, 16, 10, List.of(), 0, "砦"),
                        skill("holy_lance", U, SkillEffect.SWEEP, 60, 40, 7.5, 12, 0, List.of(), 0, "聖")));
        // ---- Spellsinger: songs that put magic on the party's weapons
        hybrid("spellsinger", "enchanter", "bard", 0x9A6AD8, "律", List.of(JobStat.MAGIC_ATTACK, JobStat.MANA_REGEN, JobStat.COOLDOWN_REDUCTION, JobStat.MAX_HEALTH),
                stats(JobStat.MAGIC_ATTACK, 4, JobStat.MANA_REGEN, 0.5),
                stats(JobStat.MAGIC_ATTACK, 0.6, JobStat.ATTACK_DAMAGE, 0.3, JobStat.MAX_HEALTH, hpGrow(0.6, 0)),
                List.of(
                        skill("spell_perform", A, SkillEffect.BUFF, 3, 0, 0, 0, 12, List.of(), 0, "奏"),
                        skill("resonance", A, SkillEffect.BUFF, 30, 30, 0, 10, 0, List.of(), 0, "共"),
                        skill("arcane_note", A, SkillEffect.BOLT, 6, 14, 1.4, 16, 0, List.of(), 0, "符")),
                List.of(
                        skill("grand_concerto", U, SkillEffect.BUFF, 90, 50, 10.0, 10, 10, List.of(), 0, "協"),
                        skill("sealing_chord", U, SkillEffect.WHIRL, 90, 42, 2.4, 16, 0, List.of(), 0, "曲")),
                List.of(
                        skill("flame_melody", SkillKind.SPELL, SkillEffect.BUFF, 3, 14, 0, 6, 12, List.of(), 0, "炎"),
                        skill("haste_tune", SkillKind.SPELL, SkillEffect.BUFF, 3, 14, 0, 6, 12, List.of(), 0, "速"),
                        skill("mana_chord", SkillKind.SPELL, SkillEffect.BUFF, 3, 14, 0, 6, 12, List.of(), 0, "魔"),
                        skill("seal_melody", SkillKind.SPELL, SkillEffect.DEBUFF, 3, 18, 0, 6, 12, List.of(), 0, "封"),
                        skill("thunder_tune", SkillKind.SPELL, SkillEffect.DEBUFF, 3, 18, 1.2, 6, 12, List.of(), 0, "雷"),
                        skill("ward_melody", SkillKind.SPELL, SkillEffect.BUFF, 3, 18, 0, 6, 12, List.of(), 0, "護"),
                        skill("drain_tune", SkillKind.SPELL, SkillEffect.BUFF, 3, 16, 0, 6, 12, List.of(), 0, "吸")));
        // ---- Shadow hunter: unseen, then the shot to the weak spot
        hybrid("shadow_hunter", "assassin", "ranger", 0x3A4A6A, "影", List.of(JobStat.ATTACK_DAMAGE, JobStat.CRIT_CHANCE, JobStat.MOVEMENT_SPEED, JobStat.ATTACK_SPEED),
                stats(JobStat.ATTACK_DAMAGE, 4, JobStat.CRIT_CHANCE, 5, JobStat.MOVEMENT_SPEED, 5),
                stats(JobStat.ATTACK_DAMAGE, 0.6, JobStat.CRIT_CHANCE, 0.05, JobStat.MAX_HEALTH, hpGrow(1.0, 0)),
                List.of(
                        skill("vital_shot", A, SkillEffect.SHOT, 4, 15, 3.0, 40, 0, List.of(), 0, "急"),
                        skill("stealth", A, SkillEffect.STEALTH, 7.5, 20, 0, 0, 4, List.of(), 0, "隠"),
                        skill("shadow_arrow", A, SkillEffect.DEBUFF, 6, 15, 1.4, 30, 3, List.of(), 0, "縫"),
                        skill("hunter_trap", A, SkillEffect.DEBUFF, 14, 15, 2.0, 2.5, 20, List.of(), 0, "罠"),
                        skill("shadow_leap", A, SkillEffect.BACKSTEP, 4, 8, 0, 0, 3, List.of(), 0, "跳"),
                        skill("fan_knives", A, SkillEffect.SWEEP, 3.5, 8, 0.9, 8, 0, List.of(), 0, "扇")),
                List.of(
                        skill("night_hunt", U, SkillEffect.BUFF, 60, 40, 0, 30, 10, List.of(), 0, "夜"),
                        skill("shadow_rain", U, SkillEffect.SHOT, 60, 40, 0.6, 6, 3, List.of(), 0, "雨")));
        // ---- Treasure hunter: lucky cannons and loot
        hybrid("treasure_hunter", "gambler", "pirate", 0xD4AF37, "宝", List.of(JobStat.LUCK, JobStat.ATTACK_DAMAGE, JobStat.CRIT_CHANCE, JobStat.MAX_HEALTH),
                stats(JobStat.ATTACK_DAMAGE, 4, JobStat.LUCK, 1, JobStat.CRIT_CHANCE, 3),
                stats(JobStat.ATTACK_DAMAGE, 0.6, JobStat.LUCK, 0.02, JobStat.MAX_HEALTH, hpGrow(0.9, 0)),
                List.of(
                        skill("gamble_cannon", A, SkillEffect.SHOT, 4.5, 15, 2.0, 24, 0, List.of(), 0, "博"),
                        skill("coin_shot", A, SkillEffect.SWEEP, 4, 15, 0.5, 8, 0, List.of(), 0, "銭"),
                        skill("mimic", A, SkillEffect.BUFF, 9, 20, 2.0, 3, 8, List.of(), 0, "箱"),
                        skill("steal", A, SkillEffect.SWEEP, 6, 10, 1.2, 4, 0, List.of(), 0, "盗"),
                        skill("lucky_compass", A, SkillEffect.BUFF, 60, 12, 0, 0, 120, List.of("minecraft:luck"), 1, "羅"),
                        skill("double_down", A, SkillEffect.BUFF, 16, 20, 0, 0, 12, List.of(), 0, "倍")),
                List.of(
                        skill("treasure_rain", U, SkillEffect.WHIRL, 60, 40, 1.2, 14, 4.5, List.of(), 0, "財"),
                        skill("golden_cannon", U, SkillEffect.SHOT, 60, 40, 9.0, 40, 0, List.of(), 0, "金")));
    }

    private static void hybrid(String id, String baseA, String baseB, int color, String glyph, List<JobStat> panelStats, Map<JobStat, Double> base,
                               Map<JobStat, Double> perLevel, List<SkillDef> actives, List<SkillDef> ultimates) {
        hybrid(id, baseA, baseB, color, glyph, panelStats, base, perLevel, actives, ultimates, List.of());
    }

    /** A hybrid with spells of its own (the spellsinger's songs, set on the lute). */
    private static void hybrid(String id, String baseA, String baseB, int color, String glyph, List<JobStat> panelStats, Map<JobStat, Double> base,
                               Map<JobStat, Double> perLevel, List<SkillDef> actives, List<SkillDef> ultimates, List<SkillDef> spells) {
        job(id, color, glyph, panelStats, base, perLevel, actives, ultimates, spells);
        HYBRIDS.put(id, List.of(baseA, baseB));
    }

    public static boolean isHybrid(String id) {
        return HYBRIDS.containsKey(id);
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
        if ("damage".equals(powerKind(def)) && !UNSCALED.contains(id)) {
            // damage skills hit for three quarters of their listed ratio (heals, buffs and the plain bolt are left alone)
            def = new SkillDef(id, "", kind, effect, cooldown, mana, power * SKILL_DAMAGE, radius, duration, effects, amplifier, glyph, 0, CAST_TIMES.getOrDefault(id, 0.0));
        }
        SKILLS.put(id, def);
        return def;
    }

    /**
     * Every damage skill's ratio is multiplied by this: against monsters sized for 3 to 6 plain hits,
     * a skill should take about half of one, not all of it.
     */
    public static final double SKILL_DAMAGE = 0.75;


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
