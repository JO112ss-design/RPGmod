package com.jo112ss.rpgmod.item;

import com.jo112ss.rpgmod.job.JobManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.Level;

/**
 * HP / MP recovery potion (a rare monster drop): drink it (one second) to get back a share of your
 * max health or max MP. Each kind has its own short cooldown so they cannot be chugged in a row.
 */
public class RecoveryPotionItem extends Item {
    /** Share of max health / max MP a potion gives back. */
    public static final double SHARE = 0.3;
    /** Seconds before another potion of the same kind can be drunk. */
    public static final float COOLDOWN = 10;

    private final boolean mana;

    public RecoveryPotionItem(Properties properties, boolean mana) {
        super(properties);
        this.mana = mana;
    }

    public static Properties props(Properties p) {
        return p.stacksTo(16).useCooldown(COOLDOWN)
                .component(net.minecraft.core.component.DataComponents.CONSUMABLE, Consumable.builder()
                        .consumeSeconds(1.0F).animation(ItemUseAnimation.DRINK).sound(SoundEvents.GENERIC_DRINK).hasConsumeParticles(false).build());
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide() && entity instanceof ServerPlayer sp) {
            if (mana) {
                double amount = JobManager.maxMana(sp) * SHARE;
                JobManager.restoreMana(sp, amount);
                sp.sendSystemMessage(Component.translatable("rpgmod.potion.mp_used", Math.round(amount)).withStyle(ChatFormatting.AQUA), true);
            } else {
                float amount = (float) (sp.getMaxHealth() * SHARE);
                com.jo112ss.rpgmod.job.HealthScale.heal(sp, amount);
                sp.sendSystemMessage(Component.translatable("rpgmod.potion.hp_used", Math.round(amount)).withStyle(ChatFormatting.RED), true);
            }
        }
        return super.finishUsingItem(stack, level, entity);
    }
}
