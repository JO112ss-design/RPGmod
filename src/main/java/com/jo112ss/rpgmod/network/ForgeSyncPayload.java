package com.jo112ss.rpgmod.network;

import com.jo112ss.rpgmod.RpgMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Server → client snapshot of the player's forging session. The GUI draws only what is in here;
 * the bar animation is reproduced locally from {@link #barSpeed} and the time the snapshot arrived.
 */
public record ForgeSyncPayload(
        int stage,
        int weapon,
        float temperature,
        float[] values,
        float[] targets,
        int selectedPart,
        boolean barRunning,
        int barToken,
        float barSpeed,
        float perfectZone,
        float goodZone,
        float badZone,
        float heatMin,
        float heatMax,
        float endTemp,
        float estimatedPower,
        float adjacentRatio,
        int strikes,
        int lastJudgement,
        int lastPart,
        float lastDamage,
        int endReason,
        int quality,
        float errorRatio,
        ItemStack result
) implements CustomPacketPayload {
    public static final int STAGE_SELECT = 0;
    public static final int STAGE_HEATING = 1;
    public static final int STAGE_WORKING = 2;
    public static final int STAGE_FINISHED = 3;

    public static final Type<ForgeSyncPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(RpgMod.MODID, "forge_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ForgeSyncPayload> STREAM_CODEC = StreamCodec.of(
            ForgeSyncPayload::write, ForgeSyncPayload::read);

    public static ForgeSyncPayload select() {
        return new ForgeSyncPayload(STAGE_SELECT, -1, 0, new float[0], new float[0], -1, false, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, -1, -1, 0, 0, 0, 0, ItemStack.EMPTY);
    }

    private static void write(RegistryFriendlyByteBuf buf, ForgeSyncPayload p) {
        buf.writeVarInt(p.stage);
        buf.writeVarInt(p.weapon);
        buf.writeFloat(p.temperature);
        writeFloats(buf, p.values);
        writeFloats(buf, p.targets);
        buf.writeVarInt(p.selectedPart);
        buf.writeBoolean(p.barRunning);
        buf.writeVarInt(p.barToken);
        buf.writeFloat(p.barSpeed);
        buf.writeFloat(p.perfectZone);
        buf.writeFloat(p.goodZone);
        buf.writeFloat(p.badZone);
        buf.writeFloat(p.heatMin);
        buf.writeFloat(p.heatMax);
        buf.writeFloat(p.endTemp);
        buf.writeFloat(p.estimatedPower);
        buf.writeFloat(p.adjacentRatio);
        buf.writeVarInt(p.strikes);
        buf.writeVarInt(p.lastJudgement);
        buf.writeVarInt(p.lastPart);
        buf.writeFloat(p.lastDamage);
        buf.writeVarInt(p.endReason);
        buf.writeVarInt(p.quality);
        buf.writeFloat(p.errorRatio);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, p.result);
    }

    private static ForgeSyncPayload read(RegistryFriendlyByteBuf buf) {
        return new ForgeSyncPayload(
                buf.readVarInt(), buf.readVarInt(), buf.readFloat(), readFloats(buf), readFloats(buf),
                buf.readVarInt(), buf.readBoolean(), buf.readVarInt(), buf.readFloat(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readFloat(),
                buf.readVarInt(), buf.readVarInt(), buf.readFloat(), ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
    }

    private static void writeFloats(RegistryFriendlyByteBuf buf, float[] a) {
        buf.writeVarInt(a.length);
        for (float f : a) buf.writeFloat(f);
    }

    private static float[] readFloats(RegistryFriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), 16);
        float[] a = new float[n];
        for (int i = 0; i < n; i++) a[i] = buf.readFloat();
        return a;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
