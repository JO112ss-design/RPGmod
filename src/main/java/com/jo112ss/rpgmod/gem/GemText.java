package com.jo112ss.rpgmod.gem;

import com.jo112ss.rpgmod.smithing.core.OptionType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Text for gems: the effect line and the rarity name. */
public final class GemText {
    private GemText() {}

    public static String number(double v) {
        return Math.abs(v - Math.rint(v)) < 1e-6 ? String.format("%.0f", v) : String.format("%.1f", v);
    }

    /** "◆ Attack +5.2%" for a stat gem, the effect's own sentence for a special one. */
    public static MutableComponent effect(Gem g) {
        OptionType t = g.option();
        if (t != null) {
            String v = "+" + number(g.value()) + (t.percent ? "%" : "");
            return Component.translatable("rpgmod.option." + t.id).withStyle(ChatFormatting.AQUA)
                    .append(Component.literal(" " + v).withStyle(ChatFormatting.WHITE));
        }
        GemSpecial s = g.special();
        if (s == null) return Component.literal(g.effect()).withStyle(ChatFormatting.GRAY);
        return Component.translatable("rpgmod.gem.special." + s.id, number(s.value)).withStyle(ChatFormatting.WHITE);
    }

    /** Socket line: a coloured gem mark, then the effect. */
    public static MutableComponent socketLine(Gem g) {
        return Component.literal(" ◆ ").withColor(Gem.RARITY_COLORS[g.rarity()]).append(effect(g));
    }

    public static MutableComponent rarity(Gem g) {
        return Component.translatable("rpgmod.gem.rarity." + g.rarityId()).withColor(Gem.RARITY_COLORS[g.rarity()]);
    }
}
