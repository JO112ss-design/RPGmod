package com.jo112ss.rpgmod.mixin;

import com.jo112ss.rpgmod.job.HealthScale;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Marks the regeneration effect's heals, so {@link HealthScale} can size them on their own. */
@Mixin(targets = "net.minecraft.world.effect.RegenerationMobEffect")
public abstract class RegenerationMixin {
    @Inject(method = "applyEffectTick", at = @At("HEAD"))
    private void rpgmod$regenStart(ServerLevel level, LivingEntity mob, int amplification, CallbackInfoReturnable<Boolean> cir) {
        HealthScale.REGEN_TICK.set(true);
    }

    @Inject(method = "applyEffectTick", at = @At("RETURN"))
    private void rpgmod$regenEnd(ServerLevel level, LivingEntity mob, int amplification, CallbackInfoReturnable<Boolean> cir) {
        HealthScale.REGEN_TICK.set(false);
    }
}
