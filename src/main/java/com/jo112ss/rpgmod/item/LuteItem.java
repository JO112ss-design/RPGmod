package com.jo112ss.rpgmod.item;

import com.jo112ss.rpgmod.job.Bard;
import com.jo112ss.rpgmod.job.BardAttack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * Lute: the bard's instrument. Songs are started with the song skills; right-click (when not
 * playing) sends out a sound wave that hits the first enemy in its way (magic attack).
 */
public class LuteItem extends Item {
    public LuteItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || !"bard".equals(com.jo112ss.rpgmod.job.JobManager.data(player).jobId()) || Bard.performing(player)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide() && player instanceof ServerPlayer sp) BardAttack.wave(sp);
        return InteractionResult.CONSUME;
    }
}
