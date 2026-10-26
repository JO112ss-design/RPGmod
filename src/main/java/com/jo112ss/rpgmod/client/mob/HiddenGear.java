package com.jo112ss.rpgmod.client.mob;

import com.jo112ss.rpgmod.RpgMod;
import com.jo112ss.rpgmod.mob.MobConfig;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;

/**
 * Monsters' armour is worn but not shown: it still counts (the server keeps it), it is only left out
 * of the picture, so a zombie looks like a zombie again. Weapons stay in view, and so do carved
 * pumpkins and heads.
 */
@EventBusSubscriber(modid = RpgMod.MODID, value = Dist.CLIENT)
public final class HiddenGear {
    private HiddenGear() {}

    @SubscribeEvent
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void onRegister(RegisterRenderStateModifiersEvent event) {
        event.registerEntityModifier((Class) LivingEntityRenderer.class, (java.util.function.BiConsumer<LivingEntity, LivingEntityRenderState>) HiddenGear::hide);
    }

    static void hide(LivingEntity entity, LivingEntityRenderState state) {
        if (!(entity instanceof Mob) || !(entity instanceof Enemy) || MobConfig.showArmor()) return;
        if (state instanceof HumanoidRenderState h) {
            if (!(h.headEquipment.getItem() instanceof BlockItem)) h.headEquipment = ItemStack.EMPTY;
            h.chestEquipment = ItemStack.EMPTY;
            h.legsEquipment = ItemStack.EMPTY;
            h.feetEquipment = ItemStack.EMPTY;
        }
    }
}
