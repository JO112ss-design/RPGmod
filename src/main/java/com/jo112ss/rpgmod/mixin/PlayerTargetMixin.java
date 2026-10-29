package com.jo112ss.rpgmod.mixin;

import com.jo112ss.rpgmod.compat.Downed;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A downed player (Resurrection mod) is not an enemy to anyone: monsters do not pick them as a
 * target, and the ones already after them drop them (targeting checks this every tick).
 */
@Mixin(Player.class)
public abstract class PlayerTargetMixin {
    @Inject(method = "canBeSeenAsEnemy", at = @At("HEAD"), cancellable = true)
    private void rpgmod$downedIsNoEnemy(CallbackInfoReturnable<Boolean> cir) {
        Player self = (Player) (Object) this;
        // downed (Resurrection mod), or vanished (joker): not an enemy to anyone
        if (Downed.is(self) || self.hasEffect(com.jo112ss.rpgmod.registry.ModEffects.VANISH)) cir.setReturnValue(false);
        // invisible from a skill (stealth, smoke, shadow dance...): the same, until the invisibility goes (a potion's is not enough)
        else if (self.hasEffect(com.jo112ss.rpgmod.registry.ModEffects.UNSEEN) && self.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY)) cir.setReturnValue(false);
    }
}
