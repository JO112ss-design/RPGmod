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
    private static int jobT = 0;

    /** Job UI walkthrough before the forge test. */
    private static void jobStep(Minecraft mc, Screen screen) {
        if (jobT == 0 && !(screen instanceof com.jo112ss.rpgmod.client.job.JobSelectScreen)) {
            if (mc.player.tickCount > 400) jobT = 1; // selection never showed: carry on anyway
            else return;
        }
        jobT++;
        switch (jobT) {
            case 20 -> shot(mc, "10_jobselect");
            case 22 -> { if (screen instanceof com.jo112ss.rpgmod.client.job.JobSelectScreen js) js.testSelect(3); }
            case 30 -> shot(mc, "11_jobselect_wizard");
            case 32 -> {
                com.jo112ss.rpgmod.client.job.JobClient.send(com.jo112ss.rpgmod.network.JobActionPayload.CHOOSE_JOB, 0, "wizard");
                mc.gui.setScreen(null);
            }
            case 45 -> AutoTestServer.requestJobSetup();
            case 70 -> mc.gui.setScreen(new com.jo112ss.rpgmod.client.job.JobScreen(com.jo112ss.rpgmod.client.job.JobScreen.Tab.STATUS));
            case 80 -> shot(mc, "12_status");
            case 82 -> mc.gui.setScreen(new com.jo112ss.rpgmod.client.job.JobScreen(com.jo112ss.rpgmod.client.job.JobScreen.Tab.PANEL));
            case 92 -> shot(mc, "13_panel");
            case 94 -> mc.gui.setScreen(new com.jo112ss.rpgmod.client.job.JobScreen(com.jo112ss.rpgmod.client.job.JobScreen.Tab.SKILLS));
            case 104 -> shot(mc, "14_skills");
            case 106 -> mc.gui.setScreen(new com.jo112ss.rpgmod.client.job.JobScreen(com.jo112ss.rpgmod.client.job.JobScreen.Tab.SPELLS));
            case 116 -> shot(mc, "15_spells");
            case 118 -> mc.gui.setScreen(null);
            case 124 -> com.jo112ss.rpgmod.client.job.JobClient.send(com.jo112ss.rpgmod.network.JobActionPayload.USE_SKILL, 0, "");
            case 126 -> mc.gameMode.useItem(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND);
            case 132 -> shot(mc, "16_hud");
            case 134 -> {
                com.jo112ss.rpgmod.client.job.SpellWheelScreen.testHold = true;
                mc.gui.setScreen(new com.jo112ss.rpgmod.client.job.SpellWheelScreen());
            }
            case 146 -> shot(mc, "17_wheel");
            case 150 -> {
                com.jo112ss.rpgmod.client.job.SpellWheelScreen.testHold = false;
                mc.gui.setScreen(null);
                com.jo112ss.rpgmod.job.JobData d = com.jo112ss.rpgmod.client.job.JobClient.data();
                RpgMod.LOGGER.info("[AUTOTEST] job client: job={} level={} points={} slots={},{},{} mana={}", d.jobId(), d.level(), d.freePoints(),
                        d.slot(0), d.slot(1), d.slot(2), d.mana());
            }
            case 160 -> {
                jobT = -1;
                AutoTestServer.requestForge();
            }
            default -> { }
        }
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (!AutoTestServer.ENABLED) return;
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.gui.screen();
        if (jobT >= 0 && mc.player != null) {
            jobStep(mc, screen);
            return;
        }
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
            if (after == 200) {
                show = 0;
                after = -1;
            }
        }
        if (show >= 0) showcase(mc);
    }

    // ------------------------------------------------------------------ skill showcase

    private static int show = -1;
    private static final int SLOT = 44;
    private static java.util.List<String> showSkills;

    /** Ticks after the cast at which to take screenshots (mid-effect). */
    private static int[] shotTimes(String id) {
        return switch (id) {
            case "power_slash", "shield_bash" -> new int[] {2};
            case "multishot", "backstep", "magic_bolt", "backstab", "lucky_shot" -> new int[] {3};
            case "earthshatter", "frost_nova", "flame_burst", "berserk", "holy_guard", "lay_on_hands", "heal", "arcane_shield", "shadow_dance" -> new int[] {6};
            case "snare", "time_warp", "weaken", "mana_link", "grand_enchant", "mana_surge", "blessing", "guardian_light" -> new int[] {10};
            case "arrow_rain" -> new int[] {16};
            case "smite" -> new int[] {4, 9};
            case "judgement" -> new int[] {12, 18};
            case "meteor" -> new int[] {18, 30};
            case "thunder" -> new int[] {6, 12};
            case "assassinate" -> new int[] {5, 9};
            case "coin_toss" -> new int[] {9, 18};
            case "dice_roll" -> new int[] {10, 20};
            case "all_in" -> new int[] {8, 16};
            case "jackpot" -> new int[] {16, 28};
            case "roulette" -> new int[] {18, 37};
            case "sanctuary" -> new int[] {20};
            default -> new int[] {6};
        };
    }

    /** Casts every skill in front of training dummies and photographs each mid-effect. */
    private static void showcase(Minecraft mc) {
        int t = show++;
        if (t == 0) {
            showSkills = new java.util.ArrayList<>(com.jo112ss.rpgmod.job.core.JobDefs.SKILLS.keySet());
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
            mc.options.particles().set(net.minecraft.server.level.ParticleStatus.ALL);
            AutoTestServer.requestShowcase();
            return;
        }
        int base = 30;
        if (t < base) return;
        int i = (t - base) / SLOT, k = (t - base) % SLOT;
        if (i >= showSkills.size()) {
            if (k == 10) {
                RpgMod.LOGGER.info("[AUTOTEST] finished");
                mc.stop();
            }
            return;
        }
        String id = showSkills.get(i);
        if (k == 0) AutoTestServer.requestCast(id);
        int[] times = shotTimes(id);
        for (int j = 0; j < times.length; j++) {
            if (k == times[j] + 1) shot(mc, String.format("s%02d_%s%s", i, id, times.length > 1 ? "_" + (j + 1) : ""));
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
