package com.jo112ss.rpgmod.client.boss;

import com.jo112ss.rpgmod.RpgMod;
import com.jo112ss.rpgmod.registry.ModRegistries;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = RpgMod.MODID, value = Dist.CLIENT)
public final class BossClient {
    private BossClient() {}

    @SubscribeEvent
    public static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModRegistries.KING_SLIME.get(), KingSlimeRenderer::new);
        event.registerEntityRenderer(ModRegistries.GOLEM.get(), GolemRenderer::new);
        event.registerEntityRenderer(ModRegistries.GOLEM_PILLAR.get(), GolemPillarRenderer::new);
    }
}
