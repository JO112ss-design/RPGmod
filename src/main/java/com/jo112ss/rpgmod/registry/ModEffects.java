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
}
