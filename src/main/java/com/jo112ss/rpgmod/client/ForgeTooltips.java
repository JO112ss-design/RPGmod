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
                : (t.step >= 1 ? String.format("+%.0f", o.value()) : String.format("+%.1f", o.value()));
        return Component.literal(" ◆ ").withStyle(ChatFormatting.DARK_AQUA)
                .append(Component.translatable("rpgmod.option." + t.id).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" " + value).withStyle(ChatFormatting.WHITE));
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        List<Component> tip = event.getToolTip();
        if (event.getItemStack().getItem() instanceof com.jo112ss.rpgmod.item.RpgBowItem bow) {
            tip.add(Math.min(1, tip.size()), Component.translatable("rpgmod.tooltip.bow_bonus", bow.damageBonus()).withStyle(ChatFormatting.DARK_GREEN));
            if (bow.material().multiplier != 1) tip.add(Math.min(2, tip.size()), Component.translatable("rpgmod.tooltip.weapon_mult",
                    String.format("%.2f", bow.material().multiplier)).withStyle(ChatFormatting.DARK_GREEN));
        }
        net.minecraft.world.item.Item it = event.getItemStack().getItem();
        var wt = com.jo112ss.rpgmod.item.ModItems.typeOf(it);
        if (wt != null && wt.magical()) {
            var mat = com.jo112ss.rpgmod.item.ModItems.materialOf(it);
            if (mat != null) tip.add(Math.min(1, tip.size()), Component.translatable("rpgmod.tooltip.staff_bonus",
                    String.format("%.0f", mat.staffMagic()), String.format("%.2f", mat.multiplier)).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        if (event.getItemStack().has(net.minecraft.core.component.DataComponents.BLOCKS_ATTACKS)) {
            int pct = (int) Math.round(com.jo112ss.rpgmod.item.Shields.block(event.getItemStack()) * 100);
            tip.add(Math.min(1, tip.size()), Component.translatable("rpgmod.tooltip.shield_block", pct).withStyle(ChatFormatting.DARK_GREEN));
            if (event.getItemStack().isDamageableItem()) {
                tip.add(Math.min(2, tip.size()), Component.translatable(com.jo112ss.rpgmod.item.Shields.isDown(event.getItemStack()) ? "rpgmod.tooltip.shield_down" : "rpgmod.tooltip.shield_recover",
                        com.jo112ss.rpgmod.item.Shields.downSeconds(event.getItemStack())).withStyle(com.jo112ss.rpgmod.item.Shields.isDown(event.getItemStack()) ? ChatFormatting.RED : ChatFormatting.GRAY));
            }
        }
        double heavy = com.jo112ss.rpgmod.job.JobConfig.SPEC.isLoaded() ? com.jo112ss.rpgmod.job.JobEvents.heavyMultiplier(event.getItemStack()) : 1;
        if (heavy != 1) {
            tip.add(Math.min(1, tip.size()), Component.translatable("rpgmod.tooltip.heavy", String.format("%.2f", heavy).replaceAll("0+$", "").replaceAll("\\.$", ""))
                    .withStyle(ChatFormatting.GOLD));
        }
        double light = com.jo112ss.rpgmod.job.JobConfig.SPEC.isLoaded() ? com.jo112ss.rpgmod.job.JobEvents.lightMultiplier(event.getItemStack()) : 1;
        if (light != 1) {
            tip.add(Math.min(1, tip.size()), Component.translatable("rpgmod.tooltip.light", String.format("%.2f", light).replaceAll("0+$", "").replaceAll("\\.$", ""))
                    .withStyle(ChatFormatting.AQUA));
        }
        ForgedData d = event.getItemStack().get(ModRegistries.FORGED_DATA.get());
        if (d == null) return;
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
