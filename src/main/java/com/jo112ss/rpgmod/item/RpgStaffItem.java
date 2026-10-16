package com.jo112ss.rpgmod.item;

import com.jo112ss.rpgmod.job.JobManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;

/**
 * Magic staff: a light melee weapon. A wizard holds right-click to charge the staff's selected spell
 * and releases once the cast time is reached to fire it; releasing early fizzles at no cost.
 */
public class RpgStaffItem extends Item {
    private static final int MAX_USE = 72000;

    public RpgStaffItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        ItemStack stack = player.getItemInHand(hand);
        // checked on both sides (job data is synced) so the client animation matches the server
        if (!JobManager.canStartCast(player, stack, !level.isClientSide())) return InteractionResult.FAIL;
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return MAX_USE;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.TRIDENT;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int ticksRemaining) {
        if (level instanceof ServerLevel sl && entity instanceof ServerPlayer sp) {
            JobManager.chargeTick(sp, stack, MAX_USE - ticksRemaining, sl);
        }
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remainingTime) {
        if (!level.isClientSide() && entity instanceof ServerPlayer sp) {
            return JobManager.releaseCast(sp, stack, MAX_USE - remainingTime);
        }
        return true;
    }
}
