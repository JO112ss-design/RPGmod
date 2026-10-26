package com.jo112ss.rpgmod.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The dungeon altar: offer a key (or a boss's token) to open a dungeon, then right-click it to go in.
 * It glows while its dungeon is open, and cannot be broken until the run is over.
 */
public class AltarBlock extends Block {
    public static final BooleanProperty ACTIVE = BlockStateProperties.LIT;

    public AltarBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ACTIVE, false));
    }

    public static int light(BlockState s) {
        return s.getValue(ACTIVE) ? 12 : 4;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (DungeonManager.kindOf(stack) == null) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) return DungeonManager.offer(sl, pos, sp, stack);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) return DungeonManager.enter(sl, pos, sp);
        return InteractionResult.SUCCESS;
    }

    static void setActive(Level level, BlockPos pos, boolean on) {
        BlockState s = level.getBlockState(pos);
        if (s.getBlock() instanceof AltarBlock && s.getValue(ACTIVE) != on) level.setBlock(pos, s.setValue(ACTIVE, on), 3);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE);
    }
}
