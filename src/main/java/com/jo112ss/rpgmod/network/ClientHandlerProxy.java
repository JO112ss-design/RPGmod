package com.jo112ss.rpgmod.network;

import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Indirection so the dedicated server never loads client classes. */
public final class ClientHandlerProxy {
    private ClientHandlerProxy() {}

    public static void handleSync(ForgeSyncPayload payload, IPayloadContext context) {
        com.jo112ss.rpgmod.client.ClientForgeView.receive(payload);
    }

    public static void openJobSelect(OpenJobSelectPayload payload, IPayloadContext context) {
        com.jo112ss.rpgmod.client.job.JobClient.openJobSelect();
    }
}
