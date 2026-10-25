package com.jo112ss.rpgmod.item;

import com.jo112ss.rpgmod.RpgMod;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;

/**
 * Eagle-eye arrows: ordinary arrows marked as the skill's own. They vanish the moment they leave
 * the player - thrown out, dropped on death, or left in a chest (or any other container) when it is
 * closed. Shot arrows can be picked up again as eagle-eye arrows.
 */
@EventBusSubscriber(modid = RpgMod.MODID)
public final class TempArrows {
    private TempArrows() {}

    public static final String KEY = "rpgmod_eagle_arrow";
    public static final int COUNT = 40;

    public static ItemStack make(int count) {
        ItemStack s = new ItemStack(Items.ARROW, count);
        CustomData.update(DataComponents.CUSTOM_DATA, s, t -> t.putBoolean(KEY, true));
        s.set(DataComponents.ITEM_NAME, Component.translatable("item.rpgmod.eagle_arrow"));
        s.set(DataComponents.LORE, new ItemLore(List.of(Component.translatable("rpgmod.eagle_arrow.lore").withStyle(ChatFormatting.GRAY))));
        return s;
    }

    public static boolean is(ItemStack s) {
        if (s.isEmpty() || !s.is(Items.ARROW)) return false;
        CustomData cd = s.get(DataComponents.CUSTOM_DATA);
        return cd != null && cd.copyTag().getBooleanOr(KEY, false);
    }

    public static void give(ServerPlayer p) {
        ItemStack s = make(COUNT);
        p.getInventory().add(s);
        if (!s.isEmpty() && com.jo112ss.rpgmod.devtest.AutoTestServer.ENABLED) RpgMod.LOGGER.info("[AUTOTEST] eagle arrows: no room for {}", s.getCount());
    }

    /** Thrown out or dropped on death: gone before it lands. */
    @SubscribeEvent
    public static void onItemJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ItemEntity item) || !is(item.getItem())) return;
        event.setCanceled(true);
    }

    /** Left in a chest (or any container that is not the player's own inventory): gone when it closes. */
    @SubscribeEvent
    public static void onClose(PlayerContainerEvent.Close event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        int removed = 0;
        for (Slot slot : event.getContainer().slots) {
            if (slot.container instanceof Inventory) continue;
            ItemStack s = slot.getItem();
            if (is(s)) {
                removed += s.getCount();
                slot.set(ItemStack.EMPTY);
            }
        }
        // the cursor stack is given back by vanilla on close (and then vanishes if it is dropped)
        if (removed > 0 && com.jo112ss.rpgmod.devtest.AutoTestServer.ENABLED) RpgMod.LOGGER.info("[AUTOTEST] eagle arrows: {} vanished from a container", removed);
    }
}
