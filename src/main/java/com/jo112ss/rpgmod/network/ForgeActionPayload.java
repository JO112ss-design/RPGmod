package com.jo112ss.rpgmod.network;

import com.jo112ss.rpgmod.RpgMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client → server input. The client only reports what the player did (which button, which part,
 * how long the bar ran); the server validates it and computes every result itself.
 */
public record ForgeActionPayload(int action, int arg, int token, long elapsedMillis) implements CustomPacketPayload {
    public static final int START = 0;
    public static final int STOP_HEAT = 1;
    public static final int SELECT_PART = 2;
    public static final int STRIKE = 3;
    public static final int COOL = 4;
    public static final int FINISH = 5;
    public static final int RESET = 6;

    public static final Type<ForgeActionPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(RpgMod.MODID, "forge_action"));

    public static final StreamCodec<FriendlyByteBuf, ForgeActionPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeByte(p.action);
                buf.writeVarInt(p.arg);
                buf.writeVarInt(p.token);
                buf.writeVarLong(p.elapsedMillis);
            },
            buf -> new ForgeActionPayload(buf.readByte(), buf.readVarInt(), buf.readVarInt(), buf.readVarLong()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
