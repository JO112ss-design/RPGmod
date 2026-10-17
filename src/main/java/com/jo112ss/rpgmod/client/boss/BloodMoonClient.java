package com.jo112ss.rpgmod.client.boss;

import com.jo112ss.rpgmod.RpgMod;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.MoonPhase;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/** Client look of a blood moon: the sky and fog fade to red and the moon is full, big and red. */
@EventBusSubscriber(modid = RpgMod.MODID, value = Dist.CLIENT)
public final class BloodMoonClient {
    private BloodMoonClient() {}

    private static boolean active;
    private static float fade, oFade;

    public static void set(boolean on) {
        active = on;
    }

    /** 0..1, how far the blood moon look has faded in (0 outside the overworld). */
    public static float amount(float partialTick) {
        var level = Minecraft.getInstance().level;
        if (level == null || level.dimension() != Level.OVERWORLD) return 0;
        return oFade + (fade - oFade) * partialTick;
    }

    public static float amount() {
        return amount(1);
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        oFade = fade;
        fade = active ? Math.min(1, fade + 0.01F) : Math.max(0, fade - 0.02F);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        active = false;
        fade = oFade = 0;
    }

    @SubscribeEvent
    public static void onFog(ViewportEvent.ComputeFogColor event) {
        float k = amount((float) event.getPartialTick());
        if (k <= 0) return;
        float r = event.getRed(), g = event.getGreen(), b = event.getBlue();
        event.setRed(r + (Math.max(r, 0.30F) - r) * k * 0.85F);
        event.setGreen(g * (1 - 0.65F * k));
        event.setBlue(b * (1 - 0.7F * k));
    }

    @SubscribeEvent
    public static void onExtract(ExtractLevelRenderStateEvent event) {
        float k = amount(event.getDeltaTracker().getGameTimeDeltaPartialTick(false));
        if (k <= 0) return;
        var sky = event.getRenderState().skyRenderState;
        Vector3fc c = sky.skyColor;
        if (c != null) {
            float r = c.x(), g = c.y(), b = c.z();
            sky.skyColor = new Vector3f(r + (Math.max(r, 0.34F) - r) * k * 0.85F, g * (1 - 0.7F * k), b * (1 - 0.75F * k));
        }
        if (k > 0.5F) sky.moonPhase = MoonPhase.FULL_MOON;
    }
}
