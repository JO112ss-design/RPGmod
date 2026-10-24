package com.jo112ss.rpgmod.network;

import com.jo112ss.rpgmod.RpgMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client → server job requests. The server validates everything (see JobManager). */
public record JobActionPayload(int action, int arg, String text) implements CustomPacketPayload {
    public static final int CHOOSE_JOB = 0;   // text = job id
    public static final int USE_SKILL = 1;    // arg = slot 0..2
    public static final int UNLOCK_NODE = 2;  // arg = node id (base panel)
    public static final int EQUIP_SKILL = 3;  // arg = slot 0..2, text = skill id ("" clears)
    public static final int SET_SPELL = 4;    // arg = staff slot, text = spell id ("" clears)
    public static final int SELECT_SPELL = 5; // arg = staff slot
    public static final int RESET_PANEL = 6;  // arg = 1 for the advanced panel
    public static final int UNLOCK_ADV_NODE = 7; // arg = node id (advanced panel)

    public static final Type<JobActionPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(RpgMod.MODID, "job_action"));

    public static final StreamCodec<FriendlyByteBuf, JobActionPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeByte(p.action);
                buf.writeVarInt(p.arg);
                buf.writeUtf(p.text, 64);
            },
            buf -> new JobActionPayload(buf.readByte(), buf.readVarInt(), buf.readUtf(64)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
