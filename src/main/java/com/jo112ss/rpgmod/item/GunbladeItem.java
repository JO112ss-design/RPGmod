package com.jo112ss.rpgmod.item;

import com.jo112ss.rpgmod.job.Pirate;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * Gunblade: a bayonet for melee, and for a pirate a gun on right-click (see {@link Pirate}): six
 * rounds, reloading by itself, a round loaded back with each bayonet hit.
 */
public class GunbladeItem extends Item {
    public GunbladeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || !Pirate.isPirate(player)) return InteractionResult.PASS;
        if (!level.isClientSide() && player instanceof ServerPlayer sp) Pirate.fire(sp);
        return InteractionResult.CONSUME;
    }
}
