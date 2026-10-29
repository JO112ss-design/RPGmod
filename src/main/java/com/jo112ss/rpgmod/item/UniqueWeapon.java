package com.jo112ss.rpgmod.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Item data component of a boss's unique weapon: which one it is, and the level of the boss that
 * dropped it (its strength comes from that level, see {@link UniqueWeapons#flat} / {@link UniqueWeapons#mult}),
 * and its gem sockets (1 to 3, rolled when it dropped).
 */
public record UniqueWeapon(String id, int level, int sockets) {
    public static final Codec<UniqueWeapon> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(UniqueWeapon::id),
            Codec.INT.fieldOf("level").forGetter(UniqueWeapon::level),
            Codec.INT.optionalFieldOf("sockets", 2).forGetter(UniqueWeapon::sockets)
    ).apply(i, UniqueWeapon::new));

    public static final StreamCodec<ByteBuf, UniqueWeapon> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, UniqueWeapon::id,
            ByteBufCodecs.VAR_INT, UniqueWeapon::level,
            ByteBufCodecs.VAR_INT, UniqueWeapon::sockets,
            UniqueWeapon::new);

    public UniqueWeapons.Def def() {
        return UniqueWeapons.Def.byId(id);
    }
}
