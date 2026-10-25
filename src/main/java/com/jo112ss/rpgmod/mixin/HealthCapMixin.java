package com.jo112ss.rpgmod.mixin;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Max health goes past vanilla's 1024: monster health is on three times vanilla's scale, so strong
 * monsters at high levels need more (up to a million).
 */
@Mixin(RangedAttribute.class)
public abstract class HealthCapMixin {
    @Inject(method = "sanitizeValue", at = @At("HEAD"), cancellable = true)
    private void rpgmod$liftHealthCap(double value, CallbackInfoReturnable<Double> cir) {
        if ((Object) this == Attributes.MAX_HEALTH.value()) cir.setReturnValue(Double.isNaN(value) ? 1.0 : Mth.clamp(value, 1.0, 1_000_000.0));
    }
}
