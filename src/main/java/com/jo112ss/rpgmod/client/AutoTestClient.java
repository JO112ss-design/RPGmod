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
            case 80 -> {
                var sp = mc.getSingleplayerServer() == null ? null : mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                RpgMod.LOGGER.info("[AUTOTEST] status client atk={} armor={} | server atk={} armor={} reduction={} level={}",
                        com.jo112ss.rpgmod.job.JobManager.physicalAttack(mc.player), com.jo112ss.rpgmod.mob.Defense.armor(mc.player),
                        sp == null ? -1 : com.jo112ss.rpgmod.job.JobManager.physicalAttack(sp), sp == null ? -1 : com.jo112ss.rpgmod.mob.Defense.armor(sp),
                        sp == null ? -1 : com.jo112ss.rpgmod.mob.Defense.reduction(sp),
                        sp == null ? -1 : com.jo112ss.rpgmod.job.JobManager.data(sp).level());
                shot(mc, "12_status");
            }
            case 82 -> mc.gui.setScreen(new com.jo112ss.rpgmod.client.job.JobScreen(com.jo112ss.rpgmod.client.job.JobScreen.Tab.PANEL));
            case 92 -> {
                com.jo112ss.rpgmod.RpgMod.LOGGER.info("[AUTOTEST] panel build={}us fps={}", com.jo112ss.rpgmod.client.job.JobScreen.lastPanelNanos / 1000, mc.getFps());
                shot(mc, "13_panel");
            }
            case 93 -> com.jo112ss.rpgmod.client.job.JobScreen.testAdvView(true);
            case 101 -> shot(mc, "13b_adv_panel");
            case 102 -> com.jo112ss.rpgmod.client.job.JobScreen.testAdvView(false);
            case 104 -> mc.gui.setScreen(new com.jo112ss.rpgmod.client.job.JobScreen(com.jo112ss.rpgmod.client.job.JobScreen.Tab.SKILLS));
            case 112 -> shot(mc, "14_skills");
            case 113 -> com.jo112ss.rpgmod.client.job.JobScreen.testAdvView(true);
            case 115 -> shot(mc, "14b_skills_adv");
            case 116 -> mc.gui.setScreen(new com.jo112ss.rpgmod.client.job.JobScreen(com.jo112ss.rpgmod.client.job.JobScreen.Tab.SPELLS));
            case 126 -> shot(mc, "15_spells_adv");
            case 127 -> com.jo112ss.rpgmod.client.job.JobScreen.testAdvView(false);
            case 128 -> mc.gui.setScreen(null);
            case 134 -> com.jo112ss.rpgmod.client.job.JobClient.send(com.jo112ss.rpgmod.network.JobActionPayload.USE_SKILL, 0, "");
            case 136 -> mc.gameMode.useItem(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND);
            case 142 -> shot(mc, "16_hud");
            case 144 -> {
                com.jo112ss.rpgmod.client.job.SpellWheelScreen.testHold = true;
                mc.gui.setScreen(new com.jo112ss.rpgmod.client.job.SpellWheelScreen());
            }
            case 156 -> shot(mc, "17_wheel");
            case 160 -> {
                com.jo112ss.rpgmod.client.job.SpellWheelScreen.testHold = false;
                mc.gui.setScreen(null);
                com.jo112ss.rpgmod.job.JobData d = com.jo112ss.rpgmod.client.job.JobClient.data();
                RpgMod.LOGGER.info("[AUTOTEST] job client: job={} shown as {} Lv{} level={} points={} adv points={} slots={},{},{},{} mana={}", d.jobId(),
                        d.displayJobId(), d.displayLevel(), d.level(), d.freePoints(), d.freePoints(true), d.slot(0), d.slot(1), d.slot(3), d.slot(2), d.mana());
            }
            case 170 -> {
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
                case 15 -> { if (screen instanceof ForgeScreen fs) fs.testChoose(com.jo112ss.rpgmod.item.WeaponType.SHIELD); }
                case 20 -> shot(mc, "01_select");
                // this run forges an iron shield (vanilla shield + 3 iron)
                case 25 -> send(ForgeActionPayload.START, com.jo112ss.rpgmod.smithing.ForgeableWeapons.indexOf(com.jo112ss.rpgmod.item.WeaponType.SHIELD, com.jo112ss.rpgmod.item.WeaponMaterial.IRON));
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
                    RpgMod.LOGGER.info("[AUTOTEST] result {} x{} (arrows in inventory: {})", result.getItem(), result.getCount(),
                            mc.player == null ? -1 : mc.player.getInventory().countItem(net.minecraft.world.item.Items.ARROW));
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
            case "hunters_mark" -> new int[] {4, 14};
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
            case "guardian_dome" -> new int[] {16};
            case "blizzard" -> new int[] {20};
            case "gravity" -> new int[] {12, 26};
            case "arcane_missiles" -> new int[] {8};
            case "holy_wave", "card_throw" -> new int[] {5};
            case "piercing_shot", "intimidate", "russian_roulette", "throwing_knives" -> new int[] {3};
            case "explosive_arrow" -> new int[] {6, 14};
            case "shadow_bind", "chrono", "quick_cast", "nourish", "fire_weapon", "poison_coat" -> new int[] {8};
            case "binding_field" -> new int[] {10};
            case "mob:levels" -> new int[] {5, 11, 18};
            case "mob:pillar" -> new int[] {50, 110, 200};
            case "mob:dig" -> new int[] {70, 140, 210};
            case "mob:creeper" -> new int[] {12, 26, 60};
            case "mob:archer" -> new int[] {40, 100, 160};
            case "mob:items" -> new int[] {30, 60, 120, 190};
            case "mob:sense" -> new int[] {40, 200, 380};
            case "mob:leap" -> new int[] {30, 60, 90, 130, 200};
            case "mob:golem" -> new int[] {54, 72, 80, 84, 112, 118, 128, 164, 178, 186, 214, 228, 268, 271, 300, 328, 340, 380, 452, 470, 504, 512, 590, 600, 660, 676, 712, 800, 900, 1010, 1060};
            case "gem:checks" -> new int[] {20};
            case "perf" -> new int[] {200};
            case "gem:socket" -> new int[] {20, 45};
            case "gem:procs" -> new int[] {4, 12, 30};
            case "snipe" -> new int[] {6, 11};
            case "rapid_fire", "flurry", "card_storm" -> new int[] {10};
            case "dead_eye" -> new int[] {8, 20};
            case "ricochet" -> new int[] {5, 12};
            case "armageddon" -> new int[] {14, 40};
            case "inferno", "earth_rune", "ward_rune", "grand_rune", "mana_well", "arcane_seal", "divine_domain", "shield_wall", "light_pillar" -> new int[] {10, 30};
            case "absolute_zero" -> new int[] {8, 15};
            case "thunderstorm", "starfall", "tornado", "dark_orb" -> new int[] {14, 38};
            case "stone_spikes", "shadow_stitch", "great_miracle", "bastion", "yaksha", "asura" -> new int[] {8};
            case "triple_whirl" -> new int[] {2, 12, 22};
            case "decoy" -> new int[] {10, 102};
            case "steam_burst" -> new int[] {6, 13};
            case "plasma_ray" -> new int[] {3};
            case "frost_tempest" -> new int[] {14, 40};
            case "wildcard" -> new int[] {10, 30};
            case "radiant_lance", "switcheroo", "vanish", "shadow_run", "decapitate", "phase_shift", "holy_nova" -> new int[] {3};
            case "mob:dragonhp" -> new int[] {100, 140};
            case "nj:pirate", "nj:lancer", "nj:monk" -> new int[] {8};
            case "nj:counter_lance" -> new int[] {58, 101};
            case "magic_slash" -> new int[] {3, 9, 16};
            case "boarding_shot" -> new int[] {4, 9};
            case "quick_draw", "spear_sweep", "ki_blast", "whirl_kick", "grapple", "finale" -> new int[] {3};
            case "cannon" -> new int[] {12, 44};
            case "powder_keg" -> new int[] {4, 8, 12, 20, 42};
            case "broadside" -> new int[] {14, 28, 44};
            case "spear_rain" -> new int[] {24, 56, 77, 80};
            case "lance_charge" -> new int[] {4, 9, 14};
            case "shrink_step" -> new int[] {1, 5};
            case "iron_mountain" -> new int[] {5, 10, 15};
            case "thunder_thrust" -> new int[] {1, 9, 11};
            case "flowing_step" -> new int[] {2, 7, 12};
            case "shield_charge" -> new int[] {4, 9, 12};
            case "spear_throw" -> new int[] {6, 16};
            case "leap_thrust" -> new int[] {6, 19, 24};
            case "counter_stance", "jolly_roger" -> new int[] {10};
            case "rapid_thrust" -> new int[] {7};
            case "dragon_fang" -> new int[] {8, 14, 22};
            case "combo" -> new int[] {5};
            case "parry", "rum" -> new int[] {6};
            case "meditate", "hundred_fists" -> new int[] {20};
            case "war_song", "heal_song", "guard_song", "wind_song", "discord", "lullaby" -> new int[] {30};
            case "grand_concert" -> new int[] {25};
            case "high_jump" -> new int[] {24, 40, 48, 54};
            case "dragon_king" -> new int[] {22, 27, 50};
            case "fleet_barrage" -> new int[] {12, 36, 70};
            case "anchor_throw" -> new int[] {4, 8, 12, 20};
            case "twin_cannon" -> new int[] {14, 50};
            case "peerless" -> new int[] {6, 18};
            case "deadshot" -> new int[] {12};
            case "mimic" -> new int[] {10, 40};
            case "gamble_cannon", "golden_cannon" -> new int[] {6, 16};
            case "shadow_rain" -> new int[] {16, 40};
            case "treasure_rain" -> new int[] {24, 60};
            case "spear_wall", "requiem", "fortress", "night_hunt" -> new int[] {12};
            case "elemental_burst" -> new int[] {2, 12, 22};
            case "symphony", "grand_concerto" -> new int[] {25};
            case "ki_wave", "holy_lance", "vital_shot", "scattershot", "shock_chord" -> new int[] {3};
            case "mob:downed" -> new int[] {50, 120, 200};
            case "mob:stairs" -> new int[] {60, 300, 550, 700};
            case "mob:exit" -> new int[] {60, 200, 340, 480};
            case "mob:spider" -> new int[] {40, 80};
            case "mob:dragon" -> new int[] {12, 45, 100, 118, 150, 196, 245, 262, 300};
            case "mob:nether" -> new int[] {30, 70, 110, 150, 190, 230, 270};
            case "mob:king" -> new int[] {56, 70, 86, 104, 122, 140, 172, 195, 212, 222, 290, 345, 362, 395, 434, 470, 490, 552, 580, 680, 760, 880, 945};
            default -> new int[] {6};
        };
    }

    private static net.minecraft.world.phys.Vec3 walkFrom = net.minecraft.world.phys.Vec3.ZERO;

    private static int length(String entry) {
        if (entry.startsWith("sword:")) return Integer.parseInt(entry.substring(6)) + 60;
        if (entry.contains("@")) entry = entry.substring(0, entry.indexOf('@'));
        String id = entry.startsWith("fp:") || entry.startsWith("up:") || entry.startsWith("down:") || entry.startsWith("front:") || entry.startsWith("steep:") || entry.startsWith("sf:") ? entry.substring(entry.indexOf(':') + 1) : entry;
        return switch (id) {
            case "mob:pillar", "mob:dig", "mob:archer", "mob:items" -> 220;
            case "mob:creeper", "mob:spider" -> 120;
            case "mob:dragon" -> 330;
            case "mob:nether" -> 280;
            case "mob:king" -> 960;
            case "mob:sense" -> 400;
            case "decoy" -> 120;
            case "mob:leap" -> 260;
            case "mob:golem" -> 1110;
            case "gem:checks" -> 140;
            case "job:checks" -> 60;
            case "bal:checks" -> 40;
            case "job:screen" -> 70;
            case "nj:pirate", "nj:lancer", "nj:monk", "nj:hybrids" -> 70;
            case "nj:counter_lance" -> 120;
            case "nj:meditate" -> 140;
            case "dragon_king" -> 100;
            case "twin_cannon", "mimic" -> 70;
            case "high_jump" -> 75;
            case "fleet_barrage", "symphony", "grand_concerto" -> 110;
            case "bard:play" -> 300;
            case "cannon", "jolly_roger", "broadside" -> 60;
            case "spear_rain", "treasure_rain" -> 95;
            case "perf" -> 1800;
            case "gem:socket" -> 70;
            case "gem:procs" -> 80;
            case "lcharge" -> 100;
            case "frost_tempest" -> 110;
            // lingering areas: let them run out before the next skill so its numbers are its own
            case "armageddon", "thunderstorm", "inferno", "starfall", "tornado", "earth_rune", "ward_rune", "grand_rune",
                 "shield_wall", "divine_domain", "mana_well", "arcane_seal", "dark_orb", "card_storm" -> 110;
            case "mob:dragonhp" -> 220;
            case "mob:downed" -> 260;
            case "mob:stairs" -> 720;
            case "mob:exit" -> 520;
            default -> SLOT;
        };
    }

    /** Casts every skill in front of training dummies and photographs each mid-effect. */
    private static void showcase(Minecraft mc) {
        int t = show++;
        if (t == 0) {
            // the newest skills (the older ones were checked in earlier runs; set rpgmod.showcase=all for every skill)
            showSkills = new java.util.ArrayList<>();
            if ("all".equals(System.getProperty("rpgmod.showcase"))) showSkills.addAll(com.jo112ss.rpgmod.job.core.JobDefs.SKILLS.keySet());
            // enemy work: levels, plates and damage numbers (skills were checked in earlier runs)
            // blood moon boss (the monster scenes and skills were checked in earlier runs)
            // advanced jobs: every new skill and spell
            for (var j : com.jo112ss.rpgmod.job.core.JobDefs.JOBS.values()) {
                if (!com.jo112ss.rpgmod.job.core.JobDefs.isAdvanced(j.id())) continue;
                showSkills.addAll(j.actives());
                showSkills.addAll(j.ultimates());
                showSkills.addAll(j.spells());
            }
            // spellmaster: fusions and the left-click charge
            showSkills.addAll(java.util.List.of("steam_burst", "plasma_ray", "frost_tempest", "lcharge"));
            // healer: the new actives and the heals as spells
            showSkills.addAll(java.util.List.of("holy_light", "holy_barrier", "guardian_angel", "heal", "group_heal", "purify"));
            // gems: logic checks, the socket table screen, gem effects on a dummy
            // (the advanced skills, healer spells and monster scenes above were checked in earlier runs)
            showSkills.clear();
            if ("all".equals(System.getProperty("rpgmod.showcase"))) showSkills.addAll(com.jo112ss.rpgmod.job.core.JobDefs.SKILLS.keySet());
            // job changes: logic checks, then the job change screen and the panel's mastery locks
            showSkills.clear(); // the 500-health rebalance: numbers by level, damage and healing paths
            if ("all".equals(System.getProperty("rpgmod.showcase"))) showSkills.addAll(com.jo112ss.rpgmod.job.core.JobDefs.SKILLS.keySet());
            showSkills.clear(); // the four new jobs: every skill, the gun, spear tips, ki, the parry and the bard's note lane
            if ("all".equals(System.getProperty("rpgmod.showcase"))) showSkills.addAll(com.jo112ss.rpgmod.job.core.JobDefs.SKILLS.keySet());
            showSkills.clear(); // the four advanced jobs and the six hybrid jobs: every skill, the hybrid unlock and points
            if ("all".equals(System.getProperty("rpgmod.showcase"))) showSkills.addAll(com.jo112ss.rpgmod.job.core.JobDefs.SKILLS.keySet());
            showSkills.clear(); // thrown props (keg, anchor) fly on the client too: their path tick by tick
            showSkills.clear(); // group A, the rain skills made distinct: carpet bombing, ghost ships, stuck spears, focused arrows, coins to pick up
            showSkills.addAll(java.util.List.of("nj:pirate", "down:broadside", "nj:captain", "up:fleet_barrage", "nj:lancer", "spear_rain", "nj:shadow_hunter", "shadow_rain",
                    "nj:treasure_hunter", "treasure_rain", "nj:coins", "nj:restore"));
            showSkills.clear(); // spellblade: sword magic by element and stage, the three magic slashes, meditation
            showSkills.addAll(java.util.List.of("nj:restore", "nj:restore", "nj:restore", "nj:restore", "nj:sb0", "swordwalk", "nj:sb0", "sword:8", "sword:18", "sword:60", "nj:sb1", "sword:18", "sword:60", "nj:sb2", "sword:18", "sword:60", "nj:sbquick", "sword:36", "nj:sbcrit", "sword:18", "nj:nocrit", "nj:sb1", "sword:60",
                    "nj:sb0", "magic_slash", "nj:sb1", "magic_slash", "nj:sb2", "magic_slash", "nj:meditate", "nj:restore"));
            if (false) showSkills.clear(); // counter lance: braced blows stored, the next hit carries them
            if (false) showSkills.addAll(java.util.List.of("nj:restore", "nj:restore", "nj:counter_lance", "nj:restore"));
            if (false) showSkills.clear(); // group C (after a pause), the jumps made distinct: pole vault and ground wave, straight up and spin down, hover and aim, the dragon
            if (false) showSkills.addAll(java.util.List.of("nj:restore", "nj:restore", "nj:restore", "nj:lancer", "leap_thrust@26", "dragon_fang", "nj:dragoon", "steep:high_jump",
                    "steep:dragon_king", "sf:dragon_king", "nj:restore"));
            if (false) showSkills.clear(); // group B (after a pause, so what the earlier tests left running has died down), the dashes made distinct: accelerating skewer, blink behind, gather and slam, lightning, flowing zigzag, shield push (twice against a wall)
            if (false) showSkills.addAll(java.util.List.of("nj:restore", "nj:restore", "nj:restore", "nj:lancer", "lance_charge", "nj:monk", "shrink_step", "nj:champion", "iron_mountain", "nj:spellblade", "front:thunder_thrust",
                    "nj:qi_master", "flowing_step", "nj:sentinel", "shield_charge", "nj:wall", "nj:lancer", "lance_charge", "nj:sentinel", "shield_charge", "nj:unwall", "nj:restore"));
            mc.options.gamma().set(1.0); // the night fight, brightly lit for the screenshots
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
            com.jo112ss.rpgmod.client.mob.MobOverlay.testSelf = true;
            mc.options.particles().set(net.minecraft.server.level.ParticleStatus.ALL);
            AutoTestServer.requestShowcase();
            return;
        }
        int base = 30;
        if (t < base) return;
        // entries have different lengths (monster scenes need time to play out)
        int i = 0, k = t - base;
        while (i < showSkills.size() && k >= length(showSkills.get(i))) {
            k -= length(showSkills.get(i));
            i++;
        }
        if (i >= showSkills.size()) {
            if (k == 10) {
                RpgMod.LOGGER.info("[AUTOTEST] finished");
                mc.stop();
            }
            return;
        }
        String entry = showSkills.get(i);
        if (entry.equals("gem:socket") && mc.gui.screen() instanceof com.jo112ss.rpgmod.client.SocketScreen ss) {
            if (k == 30) mc.gameMode.handleInventoryButtonClick(ss.getMenu().containerId, 0); // set the gem
            if (k == 50) {
                for (Component line : Screen.getTooltipFromItem(mc, ss.getMenu().gear())) RpgMod.LOGGER.info("[AUTOTEST] socketed tooltip: {}", line.getString());
                for (Component line : Screen.getTooltipFromItem(mc, com.jo112ss.rpgmod.gem.GemItems.stack(com.jo112ss.rpgmod.gem.Gem.of(com.jo112ss.rpgmod.gem.GemSpecial.PHOENIX)))) {
                    RpgMod.LOGGER.info("[AUTOTEST] gem tooltip: {}", line.getString());
                }
            }
            if (k == 60) mc.player.closeContainer();
        }
        if (entry.equals("charge")) { // hold right click with the staff: charging circle, ready glow, release
            switch (k) {
                case 0 -> {
                    mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
                    AutoTestServer.requestCast("!reset");
                }
                case 4 -> {
                    mc.player.getInventory().setSelectedSlot(0);
                    mc.options.keyUse.setDown(true);
                    mc.gameMode.useItem(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND);
                }
                case 11 -> shot(mc, String.format("c%02d_charging", i));
                case 24 -> shot(mc, String.format("c%02d_charged", i));
                case 26 -> mc.options.keyUse.setDown(false);
                case 29 -> shot(mc, String.format("c%02d_released", i));
                default -> { }
            }
            return;
        }
        if (entry.equals("job:screen")) { // the job change screen (both tabs) and the panel with mastery locks
            switch (k) {
                case 0 -> AutoTestServer.requestCast("job:screen");
                case 10 -> mc.gui.setScreen(new com.jo112ss.rpgmod.client.job.JobSelectScreen(true));
                case 14 -> { if (mc.gui.screen() instanceof com.jo112ss.rpgmod.client.job.JobSelectScreen js) js.testSelect(1); }
                case 18 -> shot(mc, String.format("j%02d_change_base", i));
                case 22 -> { if (mc.gui.screen() instanceof com.jo112ss.rpgmod.client.job.JobSelectScreen js) js.testSelect(4, true); }
                case 26 -> shot(mc, String.format("j%02d_change_adv", i));
                case 30 -> mc.gui.setScreen(new com.jo112ss.rpgmod.client.job.JobScreen(com.jo112ss.rpgmod.client.job.JobScreen.Tab.PANEL));
                case 40 -> shot(mc, String.format("j%02d_panel", i));
                case 44 -> mc.gui.setScreen(new com.jo112ss.rpgmod.client.job.JobScreen(com.jo112ss.rpgmod.client.job.JobScreen.Tab.STATUS));
                case 52 -> shot(mc, String.format("j%02d_status", i));
                case 60 -> mc.gui.setScreen(null);
                default -> { }
            }
            return;
        }
        if (entry.equals("bard:play")) { // a song played on the note lane: on time, missing every seventh note
            if (k == 0) {
                mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
                AutoTestServer.requestCast("war_song");
            }
            var d = com.jo112ss.rpgmod.client.job.JobClient.data();
            if (k > 4 && com.jo112ss.rpgmod.client.job.BardClient.performing()) {
                long since = mc.level.getGameTime() - d.songStart() - com.jo112ss.rpgmod.job.Bard.LEAD;
                long note = since / com.jo112ss.rpgmod.job.Bard.BEAT;
                if (since >= 0 && since % com.jo112ss.rpgmod.job.Bard.BEAT == 0 && note % 7 != 6) com.jo112ss.rpgmod.client.job.BardClient.click();
            }
            if (k == 60 || k == 150 || k == 275) {
                RpgMod.LOGGER.info("[AUTOTEST] bard lane k={} points={} song={} ends in {}", k, d.songPoints(), d.song(), d.songEnd() - mc.level.getGameTime());
                shot(mc, String.format("b%02d_lane_%d", i, k));
            }
            return;
        }
        if (entry.equals("lcharge")) { // spellmaster: hold left click with the staff for the left-hand spell
            switch (k) {
                case 0 -> {
                    mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
                    AutoTestServer.requestCast("!reset");
                }
                case 4 -> {
                    mc.player.getInventory().setSelectedSlot(0);
                    com.jo112ss.rpgmod.client.job.JobKeys.LEFT_SPELL.setDown(true);
                }
                case 20 -> shot(mc, String.format("c%02d_left_charging", i));
                case 78 -> shot(mc, String.format("c%02d_left_charged", i));
                case 80 -> com.jo112ss.rpgmod.client.job.JobKeys.LEFT_SPELL.setDown(false);
                case 86 -> shot(mc, String.format("c%02d_left_released", i));
                default -> { }
            }
            return;
        }
        if (entry.equals("swordwalk")) { // walking: plain, then while charging sword magic
            if (k == 0) mc.player.getInventory().setSelectedSlot(6);
            if (k == 2) mc.options.keyDown.setDown(true); // backwards: nothing in the way
            if (k == 6 || k == 22) walkFrom = mc.player.position();
            if (k == 16 || k == 32) RpgMod.LOGGER.info("[AUTOTEST] sword walk {} moved {} in 10 ticks (charge {})", k == 16 ? "plain" : "charging",
                    String.format("%.2f", mc.player.position().distanceTo(walkFrom)), com.jo112ss.rpgmod.client.job.JobClient.swordCharge);
            if (k == 18) mc.options.keyUse.setDown(true);
            if (k == 33) {
                mc.options.keyUse.setDown(false);
                mc.options.keyDown.setDown(false);
            }
            return;
        }
        if (entry.startsWith("sword:")) { // hold right click with the spellblade's sword this many ticks
            int hold = Integer.parseInt(entry.substring(6));
            if (k == 0) {
                mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
                mc.player.getInventory().setSelectedSlot(6);
            }
            if (k == 3) mc.options.keyUse.setDown(true);
            if (k == 3 + hold) mc.options.keyUse.setDown(false);
            if (k == 2 + hold || k == 3 + hold + 6 || k == 3 + hold + 14) shot(mc, String.format("w%02d_sword%d_%d", i, hold, k));
            return;
        }
        boolean fp = entry.startsWith("fp:");
        // "up:" / "down:" turn the camera after the cast (the skill is already aimed) to see the sky or the ground
        float tilt = entry.startsWith("up:") ? -28 : entry.startsWith("down:") ? 38 : entry.startsWith("steep:") || entry.startsWith("sf:") ? 62 : Float.NaN;
        boolean front = entry.startsWith("front:") || entry.startsWith("sf:"); // the camera in front, looking back along the way you came
        String id = fp ? entry.substring(3) : Float.isNaN(tilt) && !front ? entry : entry.substring(entry.indexOf(':') + 1);
        if (front && k == 2) mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
        if (!Float.isNaN(tilt) && k == 3) mc.player.setXRot(tilt);
        // the new jobs: hold the job's weapon on this side too (the server puts it in slot 7), for the HUD
        if (k == 4 && entry.startsWith("nj:") && !entry.equals("nj:restore")) mc.player.getInventory().setSelectedSlot(6);
        if (k == 0) {
            mc.options.setCameraType(fp ? net.minecraft.client.CameraType.FIRST_PERSON : net.minecraft.client.CameraType.THIRD_PERSON_BACK);
            AutoTestServer.requestCast(id); // "skill@pitch": cast looking that far down
        }
        if ((id.equals("powder_keg") || id.equals("anchor_throw")) && k >= 3 && k <= 24) {
            // where the thrown prop is on this client each tick (it should glide, not jump)
            for (var e : mc.level.getEntitiesOfClass(com.jo112ss.rpgmod.job.SkillProp.class, mc.player.getBoundingBox().inflate(30),
                    e -> e.kind() == com.jo112ss.rpgmod.job.SkillProp.KEG || e.kind() == com.jo112ss.rpgmod.job.SkillProp.ANCHOR)) {
                RpgMod.LOGGER.info("[AUTOTEST] prop path {} k={} pos=({}, {}, {}) ground={}", id, k, String.format("%.2f", e.getX() - mc.player.getX()),
                        String.format("%.2f", e.getY() - mc.player.getY()), String.format("%.2f", e.getZ() - mc.player.getZ()), e.onGround());
            }
        }
        if (id.contains("@")) id = id.substring(0, id.indexOf('@'));
        int[] times = shotTimes(id);
        for (int j = 0; j < times.length; j++) {
            if (k == times[j] + 1) shot(mc, String.format("%s%02d_%s%s", fp ? "f" : "s", i, id, times.length > 1 ? "_" + (j + 1) : ""));
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
