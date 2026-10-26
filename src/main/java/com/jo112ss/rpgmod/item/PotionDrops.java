package com.jo112ss.rpgmod.item;

import com.jo112ss.rpgmod.RpgMod;
import com.jo112ss.rpgmod.job.JobConfig;
import com.jo112ss.rpgmod.registry.ModRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

/**
 * Monsters killed by a player sometimes drop an HP potion and/or an MP potion (3% each by default,
 * a little more with luck). The King Slime's thrown-out small slimes never drop any.
 */
@EventBusSubscriber(modid = RpgMod.MODID)
public final class PotionDrops {
    private PotionDrops() {}

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        LivingEntity mob = event.getEntity();
        if (!(mob instanceof Enemy) || !(mob.level() instanceof ServerLevel sl)) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer killer)) return;
        if (mob.getPersistentData().getBooleanOr(com.jo112ss.rpgmod.mob.boss.BossEvents.CHILD, false)) return;
        boolean loaded = JobConfig.SPEC.isLoaded();
        double chance = (loaded ? JobConfig.POTION_DROP.get() : 0.03)
                + Math.max(0, killer.getAttributeValue(Attributes.LUCK)) * (loaded ? JobConfig.POTION_DROP_LUCK.get() : 0.005);
        if (com.jo112ss.rpgmod.mob.Elite.is(mob)) chance *= com.jo112ss.rpgmod.mob.Elite.POTION_MULT;
        drop(event, sl, mob, chance, ModRegistries.HP_POTION.get());
        drop(event, sl, mob, chance, ModRegistries.MP_POTION.get());
    }

    private static void drop(LivingDropsEvent event, ServerLevel sl, LivingEntity mob, double chance, Item item) {
        if (mob.getRandom().nextDouble() >= chance) return;
        ItemEntity e = new ItemEntity(sl, mob.getX(), mob.getY() + 0.5, mob.getZ(), new ItemStack(item));
        e.setDefaultPickUpDelay();
        event.getDrops().add(e);
    }
}
