package com.jo112ss.rpgmod.network;

import com.jo112ss.rpgmod.RpgMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server → client: show the job selection screen - for the first job ({@code change} false), or to
 * change job with a job change book.
 */
public record OpenJobSelectPayload(boolean change) implements CustomPacketPayload {
    public static final Type<OpenJobSelectPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(RpgMod.MODID, "open_job_select"));
    public static final StreamCodec<FriendlyByteBuf, OpenJobSelectPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> buf.writeBoolean(p.change), buf -> new OpenJobSelectPayload(buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
