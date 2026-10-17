package com.jo112ss.rpgmod.item;

import com.jo112ss.rpgmod.job.JobData;
import com.jo112ss.rpgmod.job.JobManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Skill book (boss reward): use it for one extra skill point. Sneak + use reads the whole stack.
 * The points are kept when the job changes.
 */
public class SkillBookItem extends Item {
    public SkillBookItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
        int n = player.isShiftKeyDown() ? stack.getCount() : 1;
        JobData d = JobManager.data(sp);
        d.addBonusPoints(n);
        if (!player.hasInfiniteMaterials()) stack.shrink(n);
        JobManager.sync(sp);
        sp.sendSystemMessage(Component.translatable("rpgmod.skill_book.used", n, d.freePoints()).withStyle(ChatFormatting.GOLD), true);
        ServerLevel sl = sp.level();
        sl.playSound(null, sp.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0F, 1.3F);
        sl.playSound(null, sp.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5F, 1.6F);
        com.jo112ss.rpgmod.job.fx.ServerFx.of("skill_book", sp.position()).follow(sp).send(sl);
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
