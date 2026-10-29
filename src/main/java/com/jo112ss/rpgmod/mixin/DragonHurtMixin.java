package com.jo112ss.rpgmod.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonPhaseInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Hitting the Ender Dragon anywhere counts: vanilla gives a quarter of the damage to every part but
 * the head and neck, and none to arrows while it perches. With its health on the bosses' scale and
 * the per-round limit (DragonDirector) that made the fight a slog, so every part takes full damage
 * and arrows land while it perches too.
 */
@Mixin(EnderDragon.class)
public abstract class DragonHurtMixin {
    @ModifyVariable(method = "hurt(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/boss/enderdragon/EnderDragonPart;Lnet/minecraft/world/damagesource/DamageSource;F)Z",
            at = @At("HEAD"), argsOnly = true)
    private EnderDragonPart rpgmod$everyPartIsTheHead(EnderDragonPart part) {
        return ((EnderDragon) (Object) this).head;
    }

    @Redirect(method = "hurt(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/boss/enderdragon/EnderDragonPart;Lnet/minecraft/world/damagesource/DamageSource;F)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/boss/enderdragon/phases/DragonPhaseInstance;onHurt(Lnet/minecraft/world/damagesource/DamageSource;F)F"))
    private float rpgmod$arrowsLandWhilePerched(DragonPhaseInstance phase, DamageSource source, float damage) {
        return damage;
    }
}
