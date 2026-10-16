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

    /** Melee hits set the target on fire and deal (amplifier + 1) extra damage. */
    public static final DeferredHolder<MobEffect, MobEffect> FIRE_WEAPON = EFFECTS.register("fire_weapon",
            () -> new Simple(MobEffectCategory.BENEFICIAL, 0xFF7A2A));
}
