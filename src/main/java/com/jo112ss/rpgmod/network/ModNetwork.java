package com.jo112ss.rpgmod.network;

import com.jo112ss.rpgmod.smithing.ForgeSessionManager;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetwork {
    private ModNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        // Handlers run on the main server thread (the default), so session state is never touched concurrently.
        registrar.playToServer(ForgeActionPayload.TYPE, ForgeActionPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer sp) {
                ForgeSessionManager.handleAction(sp, payload);
            }
        });
        registrar.playToClient(ForgeSyncPayload.TYPE, ForgeSyncPayload.STREAM_CODEC, ClientHandlerProxy::handleSync);
        registrar.playToServer(JobActionPayload.TYPE, JobActionPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer sp) JobActionHandler.handle(sp, payload);
        });
        registrar.playToClient(OpenJobSelectPayload.TYPE, OpenJobSelectPayload.STREAM_CODEC, ClientHandlerProxy::openJobSelect);
        registrar.playToClient(FxPayload.TYPE, FxPayload.STREAM_CODEC, ClientHandlerProxy::playFx);
        registrar.playToClient(BloodMoonPayload.TYPE, BloodMoonPayload.STREAM_CODEC, ClientHandlerProxy::bloodMoon);
    }
}
