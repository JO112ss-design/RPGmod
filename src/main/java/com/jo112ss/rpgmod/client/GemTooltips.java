package com.jo112ss.rpgmod.client;

import com.jo112ss.rpgmod.RpgMod;
import com.jo112ss.rpgmod.gem.Gem;
import com.jo112ss.rpgmod.gem.GemItems;
import com.jo112ss.rpgmod.gem.GemText;
import com.jo112ss.rpgmod.gem.Sockets;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Gem tooltips (rarity and effect) and the sockets of gear (gems set, empty slots). */
@EventBusSubscriber(modid = RpgMod.MODID, value = Dist.CLIENT)
public final class GemTooltips {
    private GemTooltips() {}

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        List<Component> tip = event.getToolTip();
        Gem gem = GemItems.gem(stack);
        if (gem != null) {
            if (!tip.isEmpty()) tip.set(0, tip.get(0).copy().withColor(Gem.RARITY_COLORS[gem.rarity()]));
            int at = Math.min(1, tip.size());
            tip.add(at++, GemText.rarity(gem));
            tip.add(at++, GemText.socketLine(gem));
            tip.add(at, Component.translatable("rpgmod.gem.how").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
            return;
        }
        int cap = Sockets.capacity(stack);
        if (cap <= 0) return;
        List<Gem> gems = Sockets.gems(stack);
        // after the forged section, before the vanilla attribute lines: find the first empty line or the end
        int at = tip.size();
        for (int i = 1; i < tip.size(); i++) {
            if (tip.get(i).getString().isEmpty()) {
                at = i;
                break;
            }
        }
        tip.add(at++, Component.translatable("rpgmod.gem.sockets", gems.size(), cap).withStyle(ChatFormatting.LIGHT_PURPLE));
        for (Gem g : gems) tip.add(at++, GemText.socketLine(g));
        for (int i = gems.size(); i < cap; i++) tip.add(at++, Component.literal(" ◇ ").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.translatable("rpgmod.gem.empty_socket").withStyle(ChatFormatting.DARK_GRAY)));
    }
}
