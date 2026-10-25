package com.jo112ss.rpgmod.item;

import com.jo112ss.rpgmod.job.JobManager;
import com.jo112ss.rpgmod.network.OpenJobSelectPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Job change book: use it to open the job selection and change to another job, or to the advanced
 * job of a mastered one. The book is spent only when a job is actually picked (see
 * {@link JobManager#changeJob}). Dropped rarely by monsters, always by blood moon bosses and the
 * dragon, and given once for each job mastered.
 */
public class JobBookItem extends Item {
    public JobBookItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer sp) {
            boolean change = JobManager.data(sp).hasJob();
            PacketDistributor.sendToPlayer(sp, new OpenJobSelectPayload(change));
            sp.level().playSound(null, sp.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
