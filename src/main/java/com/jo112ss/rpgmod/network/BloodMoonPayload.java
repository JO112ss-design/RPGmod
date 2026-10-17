package com.jo112ss.rpgmod.network;

import com.jo112ss.rpgmod.RpgMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server → client: a blood moon started or ended (red sky, red moon). */
public record BloodMoonPayload(boolean active) implements CustomPacketPayload {
    public static final Type<BloodMoonPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(RpgMod.MODID, "blood_moon"));
    public static final StreamCodec<FriendlyByteBuf, BloodMoonPayload> STREAM_CODEC =
            ByteBufCodecs.BOOL.<FriendlyByteBuf>cast().map(BloodMoonPayload::new, BloodMoonPayload::active);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
