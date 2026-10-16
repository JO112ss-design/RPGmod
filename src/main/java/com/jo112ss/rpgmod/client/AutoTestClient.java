package com.jo112ss.rpgmod.client;

import com.jo112ss.rpgmod.RpgMod;
import com.jo112ss.rpgmod.devtest.AutoTestServer;
import com.jo112ss.rpgmod.network.ForgeActionPayload;
import com.jo112ss.rpgmod.network.ForgeSyncPayload;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Development-only client half of the end-to-end test (see {@link AutoTestServer}). */
@EventBusSubscriber(modid = RpgMod.MODID, value = Dist.CLIENT)
public final class AutoTestClient {
    private AutoTestClient() {}

    private static int t = -1;
    private static int after = -1;

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (!AutoTestServer.ENABLED) return;
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.gui.screen();
        if (t < 0 && screen instanceof ForgeScreen) t = 0;
        if (t >= 0) {
            t++;
            switch (t) {
                case 20 -> shot(mc, "01_select");
                case 25 -> send(ForgeActionPayload.START, 1);
                case 50 -> shot(mc, "02_heating");
                case 52 -> stop();
                case 65 -> send(ForgeActionPayload.SELECT_PART, 2);
                case 80 -> shot(mc, "03_selected");
                case 82 -> stop();
                case 95 -> send(ForgeActionPayload.SELECT_PART, 1);
                case 110 -> stop();
                case 120 -> send(ForgeActionPayload.SELECT_PART, 3);
                case 135 -> stop();
                case 138 -> shot(mc, "04_worked");
                case 150 -> send(ForgeActionPayload.COOL, 0);
                case 160 -> send(ForgeActionPayload.SELECT_PART, 0);
                case 172 -> shot(mc, "05_cooled");
                case 175 -> stop();
                case 190 -> send(ForgeActionPayload.FINISH, 0);
                case 215 -> shot(mc, "06_result");
                case 225 -> {
                    ForgeSyncPayload v = ClientForgeView.get();
                    ItemStack result = v.result();
                    RpgMod.LOGGER.info("[AUTOTEST] result stage={} quality={} error={} values={} targets={}", v.stage(), v.quality(),
                            v.errorRatio(), Arrays.toString(v.values()), Arrays.toString(v.targets()));
                    for (Component line : Screen.getTooltipFromItem(mc, result)) {
                        RpgMod.LOGGER.info("[AUTOTEST] tooltip: {}", line.getString());
                    }
                    if (mc.player != null) mc.player.closeContainer();
                    after = 0;
                    t = -2;
                }
                default -> { }
            }
        }
        if (after >= 0) {
            after++;
            if (after == 10) AutoTestServer.requestEquipCheck();
            if (after == 30) shot(mc, "07_world");
            if (after == 60) {
                RpgMod.LOGGER.info("[AUTOTEST] finished");
                mc.stop();
            }
        }
    }

    private static void log(String what) {
        ForgeSyncPayload v = ClientForgeView.get();
        RpgMod.LOGGER.info("[AUTOTEST] {} stage={} temp={} values={} targets={} sel={} bar={} token={} strikes={} judge={}",
                what, v.stage(), v.temperature(), Arrays.toString(v.values()), Arrays.toString(v.targets()), v.selectedPart(),
                v.barRunning(), v.barToken(), v.strikes(), v.lastJudgement());
    }

    private static void shot(Minecraft mc, String name) {
        log(name);
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.gameRenderer.mainRenderTarget(), 1, msg -> {});
    }

    private static void send(int action, int arg) {
        ClientPacketDistributor.sendToServer(new ForgeActionPayload(action, arg, 0, 0));
    }

    private static void stop() {
        ForgeSyncPayload v = ClientForgeView.get();
        if (!ClientForgeView.canStop()) {
            log("stop skipped");
            return;
        }
        int token = v.barToken();
        long elapsed = ClientForgeView.markStopped();
        int action = v.stage() == ForgeSyncPayload.STAGE_HEATING ? ForgeActionPayload.STOP_HEAT : ForgeActionPayload.STRIKE;
        ClientPacketDistributor.sendToServer(new ForgeActionPayload(action, v.selectedPart(), token, elapsed));
    }
}
