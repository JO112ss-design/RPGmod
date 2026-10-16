package com.jo112ss.rpgmod.item;

import com.jo112ss.rpgmod.job.JobManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** Magic staff: a light melee weapon; a wizard casts the selected spell with right-click. */
public class RpgStaffItem extends Item {
    public RpgStaffItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!level.isClientSide() && player instanceof ServerPlayer sp) {
            return JobManager.castSpell(sp, player.getItemInHand(hand)) ? InteractionResult.SUCCESS_SERVER : InteractionResult.FAIL;
        }
        return InteractionResult.SUCCESS;
    }
}
