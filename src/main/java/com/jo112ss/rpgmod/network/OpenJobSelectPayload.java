package com.jo112ss.rpgmod.network;

import com.jo112ss.rpgmod.RpgMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server → client: the player has no job yet, show the job selection screen. */
public record OpenJobSelectPayload() implements CustomPacketPayload {
    public static final Type<OpenJobSelectPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(RpgMod.MODID, "open_job_select"));
    public static final StreamCodec<FriendlyByteBuf, OpenJobSelectPayload> STREAM_CODEC = StreamCodec.unit(new OpenJobSelectPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
