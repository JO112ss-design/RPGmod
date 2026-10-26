package com.jo112ss.rpgmod.client.mob;

import com.jo112ss.rpgmod.RpgMod;
import com.jo112ss.rpgmod.item.ModItems;
import com.jo112ss.rpgmod.item.WeaponType;
import com.jo112ss.rpgmod.mob.MobConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;

/**
 * Monsters' gear is worn but not shown: the armour and the RPG weapons they carry still count (the
 * server keeps them), they are only left out of the picture, so a zombie looks like a zombie again.
 * Kept in view: carved pumpkins and heads, and bows (drawn as a plain bow, so archers still aim
 * one); the tools the smarter monsters use (pearls, rods, TNT) stay visible too, as warnings.
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
        if (!(entity instanceof Mob) || !(entity instanceof Enemy) || MobConfig.showGear()) return;
        if (state instanceof HumanoidRenderState h) {
            if (!(h.headEquipment.getItem() instanceof BlockItem)) h.headEquipment = ItemStack.EMPTY;
            h.chestEquipment = ItemStack.EMPTY;
            h.legsEquipment = ItemStack.EMPTY;
            h.feetEquipment = ItemStack.EMPTY;
        }
        if (state instanceof ArmedEntityRenderState a) {
            a.rightHandItemStack = hand(entity, a.rightHandItemStack, a.rightHandItemState, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND);
            a.leftHandItemStack = hand(entity, a.leftHandItemStack, a.leftHandItemState, ItemDisplayContext.THIRD_PERSON_LEFT_HAND);
        }
    }

    /** An RPG weapon in hand: hidden, or for a bow, drawn as the plain vanilla bow. */
    private static ItemStack hand(LivingEntity entity, ItemStack held, ItemStackRenderState out, ItemDisplayContext ctx) {
        if (held.isEmpty()) return held;
        WeaponType type = ModItems.typeOf(held.getItem());
        if (type == null) return held;
        if (type == WeaponType.BOW) {
            if (held.is(Items.BOW)) return held;
            ItemStack bow = new ItemStack(Items.BOW);
            Minecraft.getInstance().getItemModelResolver().updateForLiving(out, bow, ctx, entity);
            return bow;
        }
        out.clear();
        return ItemStack.EMPTY;
    }
}
