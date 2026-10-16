package com.jo112ss.rpgmod.network;

import com.jo112ss.rpgmod.RpgMod;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * Server → client: play a skill effect. The server only says what happened and where; the particles
 * themselves are spawned and animated by the client ({@code client.fx.SkillFx}), so a big effect costs
 * one small packet instead of hundreds of particle packets.
 *
 * @param fx     effect id (usually the skill id, or a sub-event such as "meteor_impact")
 * @param pos    origin
 * @param vec    direction or second point, meaning depends on the effect
 * @param a      first number parameter (usually radius / range)
 * @param b      second number parameter
 * @param color  RGB, 0 = effect default
 * @param entity entity the effect follows, -1 = none
 * @param points extra positions (chain targets, allies...)
 */
public record FxPayload(String fx, Vec3 pos, Vec3 vec, float a, float b, int color, int entity, List<Vec3> points)
        implements CustomPacketPayload {
    public static final Type<FxPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(RpgMod.MODID, "fx"));
    private static final int MAX_POINTS = 32;

    public static final StreamCodec<FriendlyByteBuf, FxPayload> STREAM_CODEC = StreamCodec.of(FxPayload::write, FxPayload::read);

    private static void write(FriendlyByteBuf buf, FxPayload p) {
        buf.writeUtf(p.fx, 64);
        buf.writeDouble(p.pos.x).writeDouble(p.pos.y).writeDouble(p.pos.z);
        buf.writeFloat((float) p.vec.x).writeFloat((float) p.vec.y).writeFloat((float) p.vec.z);
        buf.writeFloat(p.a).writeFloat(p.b);
        buf.writeInt(p.color);
        buf.writeVarInt(p.entity + 1);
        int n = Math.min(MAX_POINTS, p.points.size());
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            Vec3 v = p.points.get(i);
            buf.writeDouble(v.x).writeDouble(v.y).writeDouble(v.z);
        }
    }

    private static FxPayload read(FriendlyByteBuf buf) {
        String fx = buf.readUtf(64);
        Vec3 pos = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        Vec3 vec = new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat());
        float a = buf.readFloat(), b = buf.readFloat();
        int color = buf.readInt();
        int entity = buf.readVarInt() - 1;
        int n = Math.min(MAX_POINTS, buf.readVarInt());
        List<Vec3> pts = new ArrayList<>(n);
        for (int i = 0; i < n; i++) pts.add(new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()));
        return new FxPayload(fx, pos, vec, a, b, color, entity, pts);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
