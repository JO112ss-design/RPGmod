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
public record ForgedData(int quality, List<Option> options, String crafter, java.util.Optional<Masterwork> masterwork) {
    public record Option(String id, double value) {
        public Option {
            id = OptionType.current(id); // old armour penetration options are attack now
        }

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
            Codec.STRING.optionalFieldOf("crafter", "").forGetter(ForgedData::crafter),
            Masterwork.CODEC.optionalFieldOf("masterwork").forGetter(ForgedData::masterwork)
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
                buf.writeBoolean(d.masterwork.isPresent());
                d.masterwork.ifPresent(m -> {
                    buf.writeUtf(m.skill());
                    buf.writeUtf(m.kind());
                    buf.writeDouble(m.amount());
                });
            },
            buf -> {
                int q = buf.readVarInt();
                int n = Math.min(buf.readVarInt(), 32);
                List<Option> list = new ArrayList<>(n);
                for (int k = 0; k < n; k++) list.add(new Option(buf.readUtf(), buf.readDouble()));
                String crafter = buf.readUtf();
                java.util.Optional<Masterwork> m = buf.readBoolean()
                        ? java.util.Optional.of(new Masterwork(buf.readUtf(), buf.readUtf(), buf.readDouble())) : java.util.Optional.empty();
                return new ForgedData(q, List.copyOf(list), crafter, m);
            });

    public ForgedData {
        options = List.copyOf(options);
        masterwork = masterwork == null ? java.util.Optional.empty() : masterwork;
    }

    public ForgedData(int quality, List<Option> options, String crafter) {
        this(quality, options, crafter, java.util.Optional.empty());
    }

    public static ForgedData of(Quality quality, List<RolledOption> rolled, String crafter) {
        return of(quality, rolled, crafter, null);
    }

    public static ForgedData of(Quality quality, List<RolledOption> rolled, String crafter, Masterwork masterwork) {
        List<Option> list = new ArrayList<>();
        for (RolledOption r : rolled) list.add(new Option(r.type().id, r.value()));
        return new ForgedData(quality.ordinal(), list, crafter, java.util.Optional.ofNullable(masterwork));
    }

    public Quality qualityEnum() {
        return Quality.byOrdinal(quality);
    }

    /** Value of an option, or 0 if the weapon does not have it. */
    public double get(OptionType type) {
        double sum = 0; // an old penetration option may sit next to an attack one: they add up
        for (Option o : options) if (o.id.equals(type.id)) sum += o.value;
        if (sum != 0) return sum;
        return 0;
    }
}
