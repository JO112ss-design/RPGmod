package com.jo112ss.rpgmod.gem;

import com.jo112ss.rpgmod.smithing.core.OptionType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * One gem: its rarity and its single effect. {@code effect} is a forging option id (a stat gem:
 * common, or rare with a bigger value) or a {@link GemSpecial} id.
 */
public record Gem(String effect, int rarity, double value, int ver) {
    /** Gems made before stat values were cut to a third (version 1) count a third of their stored value. */
    public static final int VERSION = 2;

    public Gem(String effect, int rarity, double value) {
        this(effect, rarity, value, VERSION);
    }

    public static final int COMMON = 0, RARE = 1, LEGENDARY = 2;
    public static final String[] RARITY_IDS = {"common", "rare", "legendary"};
    /** Name colours: common green, rare blue, legendary gold. */
    public static final int[] RARITY_COLORS = {0xFF6EE07A, 0xFF5AA8FF, 0xFFFFB627};

    public static final Codec<Gem> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("effect").forGetter(Gem::effect),
            Codec.INT.fieldOf("rarity").forGetter(Gem::rarity),
            Codec.DOUBLE.optionalFieldOf("value", 0.0).forGetter(Gem::value),
            Codec.INT.optionalFieldOf("ver", 1).forGetter(Gem::ver)
    ).apply(i, Gem::new));

    public static final StreamCodec<FriendlyByteBuf, Gem> STREAM_CODEC = StreamCodec.of(
            (buf, g) -> {
                buf.writeUtf(g.effect);
                buf.writeVarInt(g.rarity);
                buf.writeDouble(g.value);
                buf.writeVarInt(g.ver);
            },
            buf -> new Gem(buf.readUtf(), buf.readVarInt(), buf.readDouble(), buf.readVarInt()));

    public Gem {
        rarity = Math.max(COMMON, Math.min(LEGENDARY, rarity));
        effect = OptionType.current(effect); // armour penetration gems became attack gems
    }

    /** What a stat gem adds: its value (old gems from before the cut count a third). */
    public double statValue() {
        return ver < VERSION ? Math.round(value / 3 * 10) / 10.0 : value;
    }

    /** The forging option this gem adds to, or null for a special gem. */
    public OptionType option() {
        return OptionType.byId(effect);
    }

    public GemSpecial special() {
        return GemSpecial.byId(effect);
    }

    public String rarityId() {
        return RARITY_IDS[rarity];
    }

    public static Gem of(GemSpecial s) {
        return new Gem(s.id, s.rarity, s.value);
    }
}
