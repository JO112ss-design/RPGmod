package com.jo112ss.rpgmod.smithing;

import com.jo112ss.rpgmod.smithing.core.OptionType;
import com.jo112ss.rpgmod.smithing.core.Quality;
import com.jo112ss.rpgmod.smithing.core.RolledOption;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * Item data component stored on forged weapons. Persistent (saved with the item, so it survives
 * drops, trades and server restarts) and synced to clients for tooltips.
 */
public record ForgedData(int quality, List<Option> options, String crafter) {
    public record Option(String id, double value) {
        public static final Codec<Option> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("id").forGetter(Option::id),
                Codec.DOUBLE.fieldOf("value").forGetter(Option::value)
        ).apply(i, Option::new));

        public OptionType type() {
            return OptionType.byId(id);
        }
    }

    public static final Codec<ForgedData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("quality").forGetter(ForgedData::quality),
            Option.CODEC.listOf().fieldOf("options").forGetter(ForgedData::options),
            Codec.STRING.optionalFieldOf("crafter", "").forGetter(ForgedData::crafter)
    ).apply(i, ForgedData::new));

    public static final StreamCodec<FriendlyByteBuf, ForgedData> STREAM_CODEC = StreamCodec.of(
            (buf, d) -> {
                buf.writeVarInt(d.quality);
                buf.writeVarInt(d.options.size());
                for (Option o : d.options) {
                    buf.writeUtf(o.id);
                    buf.writeDouble(o.value);
                }
                buf.writeUtf(d.crafter);
            },
            buf -> {
                int q = buf.readVarInt();
                int n = Math.min(buf.readVarInt(), 32);
                List<Option> list = new ArrayList<>(n);
                for (int k = 0; k < n; k++) list.add(new Option(buf.readUtf(), buf.readDouble()));
                return new ForgedData(q, List.copyOf(list), buf.readUtf());
            });

    public ForgedData {
        options = List.copyOf(options);
    }

    public static ForgedData of(Quality quality, List<RolledOption> rolled, String crafter) {
        List<Option> list = new ArrayList<>();
        for (RolledOption r : rolled) list.add(new Option(r.type().id, r.value()));
        return new ForgedData(quality.ordinal(), list, crafter);
    }

    public Quality qualityEnum() {
        return Quality.byOrdinal(quality);
    }

    /** Value of an option, or 0 if the weapon does not have it. */
    public double get(OptionType type) {
        for (Option o : options) if (o.id.equals(type.id)) return o.value;
        return 0;
    }
}
