package com.jo112ss.rpgmod.registry;

import com.jo112ss.rpgmod.RpgMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Status effects added by job skills. The attribute ones work on their own; the marker ones
 * (quick cast, coated weapon, fire weapon) are read by the job code while they are active.
 */
public final class ModEffects {
    private ModEffects() {}

    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, RpgMod.MODID);

    /** Plain effect with no behaviour of its own. */
    private static final class Simple extends MobEffect {
        Simple(MobEffectCategory category, int color) {
            super(category, color);
        }
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(RpgMod.MODID, "effect." + path);
    }

    /** Attack +20% per level: the skills' own attack buff (war cry, empower, blood frenzy...), used instead of vanilla strength. */
    public static final double ATTACK_UP_STEP = 0.2;
    public static final DeferredHolder<MobEffect, MobEffect> ATTACK_UP = EFFECTS.register("attack_up",
            () -> new Simple(MobEffectCategory.BENEFICIAL, 0xE04A2A)
                    .addAttributeModifier(Attributes.ATTACK_DAMAGE, id("attack_up"), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,
                            amp -> ATTACK_UP_STEP * (amp + 1)));

    /** Attack -25% per level (milder than vanilla weakness, which can zero a mob's attack). */
    public static final DeferredHolder<MobEffect, MobEffect> ATTACK_DOWN = EFFECTS.register("attack_down",
            () -> new Simple(MobEffectCategory.HARMFUL, 0x8A4A3A)
                    .addAttributeModifier(Attributes.ATTACK_DAMAGE, id("attack_down"), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,
                            amp -> -Math.min(0.75, 0.25 * (amp + 1))));

    /** Armour -30% per level. */
    public static final DeferredHolder<MobEffect, MobEffect> ARMOR_BREAK = EFFECTS.register("armor_break",
            () -> new Simple(MobEffectCategory.HARMFUL, 0x9AA0B0)
                    .addAttributeModifier(Attributes.ARMOR, id("armor_break"), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,
                            amp -> -Math.min(0.9, 0.3 * (amp + 1))));

    /** Spell charge time -40% (see JobManager.castSpeedPercent). */
    public static final DeferredHolder<MobEffect, MobEffect> QUICK_CAST = EFFECTS.register("quick_cast",
            () -> new Simple(MobEffectCategory.BENEFICIAL, 0xB09CFF));

    /** Melee hits add the coating: amplifier 0 = attack down, 1 = armour break, 2 = poison. */
    public static final DeferredHolder<MobEffect, MobEffect> POISON_COAT = EFFECTS.register("poison_coat",
            () -> new Simple(MobEffectCategory.BENEFICIAL, 0x7DFF4A));

    /**
     * Gambler's roulette prize: every hit is a critical hit, crits deal +10% per level (amplifier + 1),
     * and luck always grants a second roll (see JobManager).
     */
    public static final DeferredHolder<MobEffect, MobEffect> FORTUNE = EFFECTS.register("fortune",
            () -> new Simple(MobEffectCategory.BENEFICIAL, 0xFFD54A));

    /** Melee hits set the target on fire and deal (amplifier + 1) extra damage. */
    public static final DeferredHolder<MobEffect, MobEffect> FIRE_WEAPON = EFFECTS.register("fire_weapon",
            () -> new Simple(MobEffectCategory.BENEFICIAL, 0xFF7A2A));

    // ------------------------------------------------------------------ advanced job skills (read by job.AdvancedHooks)

    private static DeferredHolder<MobEffect, MobEffect> marker(String name, MobEffectCategory category, int color) {
        return EFFECTS.register(name, () -> new Simple(category, color));
    }

    /** Takes +25% damage per level from everything (sharpshooter's weak point, spellmaster's seal). */
    public static final DeferredHolder<MobEffect, MobEffect> EXPOSED = marker("exposed", MobEffectCategory.HARMFUL, 0xFF5050);
    /** RPG crit rate +25 per level (berserker's roar). */
    public static final DeferredHolder<MobEffect, MobEffect> KEEN = marker("keen", MobEffectCategory.BENEFICIAL, 0xFFB040);
    /** Berserker ultimate: the lower your health the harder you hit, and hits heal you. */
    public static final DeferredHolder<MobEffect, MobEffect> ASURA = marker("asura", MobEffectCategory.BENEFICIAL, 0xC01818);
    /** Guardian: damage taken halved, no knockback. */
    public static final DeferredHolder<MobEffect, MobEffect> IRON_WALL = EFFECTS.register("iron_wall",
            () -> new Simple(MobEffectCategory.BENEFICIAL, 0xA0A8B8)
                    .addAttributeModifier(Attributes.KNOCKBACK_RESISTANCE, id("iron_wall"), AttributeModifier.Operation.ADD_VALUE, amp -> 1.0));
    /** Armour +8 and toughness +4 per level. */
    public static final DeferredHolder<MobEffect, MobEffect> GUARD_UP = EFFECTS.register("guard_up",
            () -> new Simple(MobEffectCategory.BENEFICIAL, 0xE0C060)
                    .addAttributeModifier(Attributes.ARMOR, id("guard_up"), AttributeModifier.Operation.ADD_VALUE, amp -> 8.0 * (amp + 1))
                    .addAttributeModifier(Attributes.ARMOR_TOUGHNESS, id("guard_up_toughness"), AttributeModifier.Operation.ADD_VALUE, amp -> 4.0 * (amp + 1)));
    /** Guardian: takes most of the damage meant for nearby allies. */
    public static final DeferredHolder<MobEffect, MobEffect> COVER = marker("cover", MobEffectCategory.BENEFICIAL, 0xF0D080);
    /** Guardian: melee attackers are hurt back. */
    public static final DeferredHolder<MobEffect, MobEffect> REPRISAL = marker("reprisal", MobEffectCategory.BENEFICIAL, 0xFF9040);
    /** Guardian ultimate: health cannot drop below 1. */
    public static final DeferredHolder<MobEffect, MobEffect> UNDYING = marker("undying", MobEffectCategory.BENEFICIAL, 0xFFE070);
    /** Sharpshooter: the next arrow from a bow hits much harder. */
    public static final DeferredHolder<MobEffect, MobEffect> EMPOWERED_SHOT = marker("empowered_shot", MobEffectCategory.BENEFICIAL, 0x8CFF6A);
    /** Spellmaster: spells are cast twice (amplifier + 1 charges). */
    public static final DeferredHolder<MobEffect, MobEffect> DOUBLE_CAST = marker("double_cast", MobEffectCategory.BENEFICIAL, 0x9A7BFF);
    /** Spellmaster: magic attack x1.5, spells cost 50% more mana. */
    public static final DeferredHolder<MobEffect, MobEffect> OVERLOAD = marker("overload", MobEffectCategory.BENEFICIAL, 0xFF4AD8);
    /** Runemaster: no fall damage. */
    public static final DeferredHolder<MobEffect, MobEffect> FEATHER = marker("feather", MobEffectCategory.BENEFICIAL, 0xE8F4FF);
    /** Joker: monsters cannot see you (the next hit is doubled and ends it). */
    public static final DeferredHolder<MobEffect, MobEffect> VANISH = marker("vanish", MobEffectCategory.BENEFICIAL, 0x6A4A9A);
    /** A skill's invisibility: while it lasts (and the invisibility does), monsters cannot target you. */
    public static final DeferredHolder<MobEffect, MobEffect> UNSEEN = marker("unseen", MobEffectCategory.BENEFICIAL, 0x3A3F55);
    /** Joker: the next gamble lands on the best result. */
    public static final DeferredHolder<MobEffect, MobEffect> LOADED_DICE = marker("loaded_dice", MobEffectCategory.BENEFICIAL, 0xFFD54A);
    /** Nightblade: melee hits poison and wither. */
    public static final DeferredHolder<MobEffect, MobEffect> TWIN_VENOM = marker("twin_venom", MobEffectCategory.BENEFICIAL, 0x60E040);
    /** Nightblade ultimate: every melee hit strikes twice. */
    public static final DeferredHolder<MobEffect, MobEffect> YAKSHA = marker("yaksha", MobEffectCategory.BENEFICIAL, 0x5060E0);
    /** Assassin / nightblade poison: a share of max health every second, undead too (see job.Venom). */
    public static final DeferredHolder<MobEffect, MobEffect> VENOM = EFFECTS.register("venom", com.jo112ss.rpgmod.job.Venom.Effect::new);
    /** Bishop: damage taken -30% and harmful effects wash off. */
    public static final DeferredHolder<MobEffect, MobEffect> SANCTUM = marker("sanctum", MobEffectCategory.BENEFICIAL, 0xFFF0B0);

    // ------------------------------------------------------------------ pirate, lancer, monk, bard (read by job.NewJobHooks)

    /** Pirate's flag: +20% damage dealt. */
    public static final DeferredHolder<MobEffect, MobEffect> JOLLY_ROGER = marker("jolly_roger", MobEffectCategory.BENEFICIAL, 0x2E86C1);
    /** Lancer: half damage taken, and enemies that come close or strike are hit back. */
    public static final DeferredHolder<MobEffect, MobEffect> COUNTER_STANCE = marker("counter_stance", MobEffectCategory.BENEFICIAL, 0xC0703A);
    /** Monk: the next melee hit taken is turned aside and answered. */
    public static final DeferredHolder<MobEffect, MobEffect> PARRY = marker("parry", MobEffectCategory.BENEFICIAL, 0xFFC070);
    /** Monk: untouchable for a moment (the shrinking step). */
    public static final DeferredHolder<MobEffect, MobEffect> UNTOUCHABLE = marker("untouchable", MobEffectCategory.BENEFICIAL, 0xFFE0B0);
    /** Monk ultimate: -80% damage taken, no knockback, hits build ki. */
    public static final DeferredHolder<MobEffect, MobEffect> DIAMOND_BODY = EFFECTS.register("diamond_body",
            () -> new Simple(MobEffectCategory.BENEFICIAL, 0xFFD27A)
                    .addAttributeModifier(Attributes.KNOCKBACK_RESISTANCE, id("diamond_body"), AttributeModifier.Operation.ADD_VALUE, amp -> 1.0));
    /** Monk: meditating (cannot move). */
    public static final DeferredHolder<MobEffect, MobEffect> MEDITATE = EFFECTS.register("meditate",
            () -> new Simple(MobEffectCategory.BENEFICIAL, 0xF0E0B0)
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, id("meditate"), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, amp -> -1.0)
                    .addAttributeModifier(Attributes.JUMP_STRENGTH, id("meditate_jump"), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, amp -> -1.0));
    /** Bard's war song: +3% damage dealt per level. */
    public static final DeferredHolder<MobEffect, MobEffect> WAR_SONG = marker("war_song", MobEffectCategory.BENEFICIAL, 0xFF7050);
    /** Bard's guard song: -3% damage taken per level. */
    public static final DeferredHolder<MobEffect, MobEffect> GUARD_SONG = marker("guard_song", MobEffectCategory.BENEFICIAL, 0x70A0FF);
    /** Bard's healing song (the healing itself comes from the performance). */
    public static final DeferredHolder<MobEffect, MobEffect> HEAL_SONG = marker("heal_song", MobEffectCategory.BENEFICIAL, 0x70FF90);
    /** Bard's wind song: +4% movement and +3% attack speed per level. */
    public static final DeferredHolder<MobEffect, MobEffect> WIND_SONG = EFFECTS.register("wind_song",
            () -> new Simple(MobEffectCategory.BENEFICIAL, 0xB0FFE0)
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, id("wind_song"), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, amp -> 0.04 * (amp + 1))
                    .addAttributeModifier(Attributes.ATTACK_SPEED, id("wind_song_attack"), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, amp -> 0.03 * (amp + 1)));
    // ------------------------------------------------------------------ captain, champion, dragoon, maestro and the hybrid jobs

    /** Champion: every melee hit taken while it lasts is dodged and answered. */
    public static final DeferredHolder<MobEffect, MobEffect> FORESIGHT = marker("foresight", MobEffectCategory.BENEFICIAL, 0xFFB070);
    /** Champion: +4% damage dealt per point of ki. */
    public static final DeferredHolder<MobEffect, MobEffect> BATTLE_AURA = marker("battle_aura", MobEffectCategory.BENEFICIAL, 0xFF8030);
    /** Maestro's hero: +30% damage dealt. */
    public static final DeferredHolder<MobEffect, MobEffect> HEROIC = marker("heroic", MobEffectCategory.BENEFICIAL, 0xFFD050);
    /** Maestro: the next song plays alongside the current one. */
    public static final DeferredHolder<MobEffect, MobEffect> DUET = marker("duet", MobEffectCategory.BENEFICIAL, 0x5FD3BC);
    /** Spellsinger's flame melody: hits burn and deal +3% per level. */
    public static final DeferredHolder<MobEffect, MobEffect> FLAME_SONG = marker("flame_song", MobEffectCategory.BENEFICIAL, 0xFF7A30);
    /** Spellblade: -30% damage taken, and the element strikes back at attackers. */
    public static final DeferredHolder<MobEffect, MobEffect> SPELL_GUARD = marker("spell_guard", MobEffectCategory.BENEFICIAL, 0x8C7CFF);
    /** Sentinel: -40% damage taken, no knockback. */
    public static final DeferredHolder<MobEffect, MobEffect> IMMOVABLE = EFFECTS.register("immovable",
            () -> new Simple(MobEffectCategory.BENEFICIAL, 0xC0B080)
                    .addAttributeModifier(Attributes.KNOCKBACK_RESISTANCE, id("immovable"), AttributeModifier.Operation.ADD_VALUE, amp -> 1.0));
    /** Sentinel: the next melee hit taken is blocked and answered. */
    public static final DeferredHolder<MobEffect, MobEffect> COUNTER_LANCE = marker("counter_lance", MobEffectCategory.BENEFICIAL, 0xE0C080);
    /** Sentinel's fortress: -50% damage taken. */
    public static final DeferredHolder<MobEffect, MobEffect> FORTRESS = marker("fortress", MobEffectCategory.BENEFICIAL, 0xF0D890);
    /** Shadow hunter: the next hit is a sure crit and hits 60% harder. */
    public static final DeferredHolder<MobEffect, MobEffect> AMBUSH = marker("ambush", MobEffectCategory.BENEFICIAL, 0x4A5A80);
    /** Shadow hunter's night hunt (on the hunter), and its marked prey (+50% damage from the hunter). */
    public static final DeferredHolder<MobEffect, MobEffect> NIGHT_HUNT = marker("night_hunt", MobEffectCategory.BENEFICIAL, 0x2A3050);
    public static final DeferredHolder<MobEffect, MobEffect> HUNTED = marker("hunted", MobEffectCategory.HARMFUL, 0xC03040);
    /** Treasure hunter: monsters killed drop extra treasure. */
    public static final DeferredHolder<MobEffect, MobEffect> TREASURE = marker("treasure", MobEffectCategory.BENEFICIAL, 0xD4AF37);
    /** Treasure hunter: each hit is doubled or halved (luck helps). */
    public static final DeferredHolder<MobEffect, MobEffect> DOUBLE_DOWN = marker("double_down", MobEffectCategory.BENEFICIAL, 0xE8C040);
    /** Treasure rain coins picked up: +5% damage per level (up to five). */
    /** Bard songs: rhapsody (crit chance per level), fanfare (skills come back sooner), the spellsinger's ward and drain. */
    public static final DeferredHolder<MobEffect, MobEffect> RHAPSODY = marker("rhapsody", MobEffectCategory.BENEFICIAL, 0xFF5AA8);
    public static final DeferredHolder<MobEffect, MobEffect> FANFARE = marker("fanfare", MobEffectCategory.BENEFICIAL, 0xFFD050);
    public static final DeferredHolder<MobEffect, MobEffect> WARD_MELODY = marker("ward_melody", MobEffectCategory.BENEFICIAL, 0x9FD8FF);
    public static final DeferredHolder<MobEffect, MobEffect> DRAIN_TUNE = marker("drain_tune", MobEffectCategory.BENEFICIAL, 0x7A60FF);
    /** The spellblade meditating: mana comes back much faster. */
    public static final DeferredHolder<MobEffect, MobEffect> MANA_MEDITATE = marker("mana_meditate", MobEffectCategory.BENEFICIAL, 0x6FA8FF);
    public static final DeferredHolder<MobEffect, MobEffect> GOLD_RUSH = marker("gold_rush", MobEffectCategory.BENEFICIAL, 0xFFC830);
    // the reworked ultimates (see UltSkills)
    public static final DeferredHolder<MobEffect, MobEffect> HOLY_AEGIS = marker("holy_aegis", MobEffectCategory.BENEFICIAL, 0xFFE070);
    public static final DeferredHolder<MobEffect, MobEffect> REWIND = marker("rewind", MobEffectCategory.BENEFICIAL, 0xFFF0A0);
    public static final DeferredHolder<MobEffect, MobEffect> SYMPHONY = marker("symphony", MobEffectCategory.BENEFICIAL, 0xFFE070);
    public static final DeferredHolder<MobEffect, MobEffect> SILENCED = marker("silenced", MobEffectCategory.HARMFUL, 0xB070E0);
    public static final DeferredHolder<MobEffect, MobEffect> TIME_STOP = marker("time_stop", MobEffectCategory.HARMFUL, 0xB0C8FF);
    public static final DeferredHolder<MobEffect, MobEffect> SHADOW_CLONE = marker("shadow_clone", MobEffectCategory.BENEFICIAL, 0x6A4A9A);
    public static final DeferredHolder<MobEffect, MobEffect> WAR_GOD = marker("war_god", MobEffectCategory.BENEFICIAL, 0xFF5030);

    /** Bard: playing (slower, cannot attack). */
    public static final DeferredHolder<MobEffect, MobEffect> PERFORMING = EFFECTS.register("performing",
            () -> new Simple(MobEffectCategory.NEUTRAL, 0x48C9B0)
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, id("performing"), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, amp -> -0.3));
}
