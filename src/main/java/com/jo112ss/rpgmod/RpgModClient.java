package com.jo112ss.rpgmod;

import com.jo112ss.rpgmod.client.ForgeScreen;
import com.jo112ss.rpgmod.registry.ModRegistries;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@Mod(value = RpgMod.MODID, dist = Dist.CLIENT)
public class RpgModClient {
    public RpgModClient(IEventBus modBus, ModContainer container) {
        modBus.addListener(RpgModClient::registerScreens);
        modBus.addListener(com.jo112ss.rpgmod.client.job.JobKeys::register);
        modBus.addListener(RpgModClient::registerLayers);
        modBus.addListener(com.jo112ss.rpgmod.client.fx.FxRender::onRegisterPipelines);
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModRegistries.FORGE_MENU.get(), ForgeScreen::new);
    }

    private static void registerLayers(net.neoforged.neoforge.client.event.RegisterGuiLayersEvent event) {
        // under the vanilla HUD, so the hotbar and chat stay on top of monster plates
        event.registerBelowAll(net.minecraft.resources.Identifier.fromNamespaceAndPath(RpgMod.MODID, "mob_overlay"),
                com.jo112ss.rpgmod.client.mob.MobOverlay::render);
        event.registerAboveAll(net.minecraft.resources.Identifier.fromNamespaceAndPath(RpgMod.MODID, "job_hud"),
                com.jo112ss.rpgmod.client.job.JobHud::render);
        // an RPG style HP bar instead of the hearts (same place, same "only in survival" rule)
        event.replaceLayer(net.neoforged.neoforge.client.gui.VanillaGuiLayers.PLAYER_HEALTH, com.jo112ss.rpgmod.client.hud.HpBar::render);
    }
}
