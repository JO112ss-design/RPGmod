package com.jo112ss.rpgmod.mixin;

import com.jo112ss.rpgmod.job.HealthScale;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Marks instant health's heals (drunk, splashed or from a lingering cloud), so {@link HealthScale} can size them on their own. */
@Mixin(targets = "net.minecraft.world.effect.HealOrHarmMobEffect")
public abstract class InstantHealthMixin {
    @Inject(method = "applyEffectTick", at = @At("HEAD"))
    private void rpgmod$tickStart(ServerLevel level, LivingEntity mob, int amplification, CallbackInfoReturnable<Boolean> cir) {
        HealthScale.POTION_HEAL.set(true);
    }

    @Inject(method = "applyEffectTick", at = @At("RETURN"))
    private void rpgmod$tickEnd(ServerLevel level, LivingEntity mob, int amplification, CallbackInfoReturnable<Boolean> cir) {
        HealthScale.POTION_HEAL.set(false);
    }

    @Inject(method = "applyInstantaneousEffect", at = @At("HEAD"))
    private void rpgmod$instantStart(ServerLevel level, Entity source, Entity owner, LivingEntity mob, int amplification, double scale, CallbackInfo ci) {
        HealthScale.POTION_HEAL.set(true);
    }

    @Inject(method = "applyInstantaneousEffect", at = @At("RETURN"))
    private void rpgmod$instantEnd(ServerLevel level, Entity source, Entity owner, LivingEntity mob, int amplification, double scale, CallbackInfo ci) {
        HealthScale.POTION_HEAL.set(false);
    }
}
