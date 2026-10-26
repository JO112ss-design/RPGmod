package com.jo112ss.rpgmod.mixin;

import com.jo112ss.rpgmod.RpgMod;
import com.mojang.serialization.Lifecycle;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The dungeon dimension is part of the mod, not an experiment: without this every world would open
 * with the "experimental settings" warning, because it has a dimension the game does not know.
 */
@Mixin(WorldDimensions.class)
public abstract class DungeonDimensionMixin {
    @Inject(method = "checkStability", at = @At("HEAD"), cancellable = true)
    private static void rpgmod$ownDimensionsAreStable(ResourceKey<LevelStem> key, LevelStem dimension, CallbackInfoReturnable<Lifecycle> cir) {
        if (key.identifier().getNamespace().equals(RpgMod.MODID)) cir.setReturnValue(Lifecycle.stable());
    }
}
