package com.jo112ss.rpgmod.client;

import com.jo112ss.rpgmod.RpgMod;
import com.jo112ss.rpgmod.registry.ModRegistries;
import com.jo112ss.rpgmod.smithing.ForgedData;
import com.jo112ss.rpgmod.smithing.core.OptionType;
import com.jo112ss.rpgmod.smithing.core.Quality;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Tooltip for forged weapons: coloured name, quality, options with values, and the crafter. */
@EventBusSubscriber(modid = RpgMod.MODID, value = Dist.CLIENT)
public final class ForgeTooltips {
    private ForgeTooltips() {}

    public static Component optionLine(ForgedData.Option o) {
        OptionType t = o.type();
        if (t == null) return Component.literal(o.id() + " " + o.value());
        String value = t.percent
                ? (t.step >= 1 ? String.format("+%.0f%%", o.value()) : String.format("+%.1f%%", o.value()))
                : String.format("+%.0f", o.value());
        return Component.literal(" ◆ ").withStyle(ChatFormatting.DARK_AQUA)
                .append(Component.translatable("rpgmod.option." + t.id).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" " + value).withStyle(ChatFormatting.WHITE));
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ForgedData d = event.getItemStack().get(ModRegistries.FORGED_DATA.get());
        if (d == null) return;
        List<Component> tip = event.getToolTip();
        Quality q = d.qualityEnum();
        if (!tip.isEmpty()) {
            MutableComponent name = tip.get(0).copy().withColor(q.color);
            tip.set(0, name);
        }
        int at = Math.min(1, tip.size());
        tip.add(at++, Component.translatable("rpgmod.tooltip.quality",
                Component.translatable("rpgmod.quality." + q.id)).withColor(q.color));
        if (!d.options().isEmpty()) {
            tip.add(at++, Component.translatable("rpgmod.tooltip.options").withStyle(ChatFormatting.GRAY));
            for (ForgedData.Option o : d.options()) tip.add(at++, optionLine(o));
        }
        if (!d.crafter().isEmpty()) {
            tip.add(at, Component.translatable("rpgmod.tooltip.crafter", d.crafter()).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
    }
}
