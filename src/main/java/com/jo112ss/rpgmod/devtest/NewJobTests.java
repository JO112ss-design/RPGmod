package com.jo112ss.rpgmod.devtest;

import com.jo112ss.rpgmod.RpgMod;
import com.jo112ss.rpgmod.item.ModItems;
import com.jo112ss.rpgmod.item.WeaponMaterial;
import com.jo112ss.rpgmod.job.JobData;
import com.jo112ss.rpgmod.job.JobManager;
import com.jo112ss.rpgmod.job.JobStats;
import com.jo112ss.rpgmod.job.JobType;
import com.jo112ss.rpgmod.job.Pirate;
import com.jo112ss.rpgmod.job.SkillExecutor;
import com.jo112ss.rpgmod.job.SkillTasks;
import com.jo112ss.rpgmod.job.core.JobDefs;
import com.jo112ss.rpgmod.mob.revenant.Revenant;
import com.jo112ss.rpgmod.mob.revenant.RevenantSpawns;
import com.jo112ss.rpgmod.registry.ModEffects;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Dev test for the pirate, lancer, monk and bard (client showcase entries "nj:*"): switches the test
 * player to the job, then checks the gun and its rounds, spear tip hits, ki, the parry and diamond
 * body, and the bard's songs.
 */
final class NewJobTests {
    private NewJobTests() {}

    private static String startJob;
    private static boolean startAdv;

    static void log(String msg, Object... args) {
        RpgMod.LOGGER.info("[AUTOTEST] newjob " + msg, args);
    }

    static void scene(ServerLevel level, ServerPlayer p, String name, List<? extends LivingEntity> dummies) {
        JobData d = JobManager.data(p);
        switch (name) {
            case "restore" -> {
                if (startJob != null) {
                    d.switchTo(startJob, startAdv);
                    d.ensureAdvanced();
                    JobStats.apply(p, d);
                    JobManager.sync(p);
                    log("restored {}", d.displayJobId());
                }
            }
            case "pirate" -> pirate(level, p, dummies);
            case "lancer" -> lancer(level, p, dummies);
            case "monk" -> monk(level, p, dummies);
            case "kishot" -> kiShot(level, p);
            case "boosts" -> skillBoosts(level, p);
            case "robes" -> robes(level, p);
            case "hitwin" -> hitWindows(level, p);
            case "bardults" -> bardUlts(level, p);
            case "unseen" -> unseenTest(level, p);
            case "uniques" -> uniquesTest(level, p);
            case "wither" -> witherTest(level, p);
            case "encore" -> { // a bard's song well played gets its encore; a spellsinger's never does
                for (String[] js : new String[][] {{"bard", "war_song"}, {"spellsinger", "flame_melody"}}) {
                    int start = js[0].equals("bard") ? 0 : 340;
                    SkillTasks.later(start + 1, () -> {
                        become(p, js[0]);
                        JobManager.data(p).devUnlockAll();
                        var def = JobDefs.skill(js[1]);
                        SkillExecutor.execute(p, def, JobManager.powerMultiplier(p, def));
                    });
                    long[] end = {0};
                    SkillTasks.later(start + 3, () -> end[0] = JobManager.data(p).songEnd() - level.getGameTime());
                    // full points just before the end (the client's note lane would take them back with misses meanwhile)
                    SkillTasks.run(age -> {
                        long left = JobManager.data(p).songEnd() - level.getGameTime();
                        if (age > 5 && left <= 2 && left >= 0) JobManager.data(p).setSongPoints(JobData.MAX_SONG_POINTS);
                        return age > 330;
                    });
                    SkillTasks.later(start + 3 + 300, () -> {
                        log("encore: {} {} - song had {}t left at the start, 300t later performing {} (encore expected {})", js[0], js[1], end[0],
                                com.jo112ss.rpgmod.job.Bard.performing(p), js[0].equals("bard"));
                        com.jo112ss.rpgmod.job.Bard.stop(p);
                    });
                }
            }
            case "heals" -> { // the greater heal and the holy circle against the heal and the group heal
                become(p, "healer", true);
                JobManager.data(p).devUnlockAll();
                var maxHp = p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
                var big = net.minecraft.resources.Identifier.fromNamespaceAndPath(com.jo112ss.rpgmod.RpgMod.MODID, "test_big_health");
                maxHp.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(big, 20000, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
                StringBuilder sb = new StringBuilder();
                for (String id : new String[] {"heal", "greater_heal", "group_heal", "grand_heal"}) {
                    var def = JobDefs.skill(id);
                    JobManager.data(p).setMana(JobManager.maxMana(p));
                    p.setHealth(1);
                    p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON, 200, 0));
                    SkillExecutor.execute(p, def, JobManager.powerMultiplier(p, def));
                    sb.append(id).append(": healed ").append(String.format("%.0f", p.getHealth() - 1)).append(", cast ").append(def.castTime()).append("s, mana ")
                            .append(Math.round(JobManager.manaCost(p, def))).append(", poisoned ").append(p.hasEffect(net.minecraft.world.effect.MobEffects.POISON)).append("; ");
                    p.removeEffect(net.minecraft.world.effect.MobEffects.POISON);
                }
                maxHp.removeModifier(big);
                p.setHealth(p.getMaxHealth());
                log("heals: {}", sb);
            }
            case "dirge" -> { // the dirge among eight enemies: no more than 40 health a second
                become(p, "bard");
                JobManager.data(p).devUnlockAll();
                java.util.List<net.minecraft.world.entity.Mob> foes = new java.util.ArrayList<>();
                for (int k = 0; k < 8; k++) {
                    var m = net.minecraft.world.entity.EntityTypes.HUSK.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                    double a = k * Math.PI / 4;
                    m.snapTo(p.getX() + Math.cos(a) * 3, p.getY(), p.getZ() + Math.sin(a) * 3, 0, 0);
                    m.setNoAi(true);
                    level.addFreshEntity(m);
                    m.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(100000);
                    m.setHealth(100000);
                    foes.add(m);
                }
                p.setHealth(10);
                SkillExecutor.execute(p, JobDefs.skill("dirge"), JobManager.powerMultiplier(p, JobDefs.skill("dirge")));
                JobManager.data(p).setSongPoints(JobData.MAX_SONG_POINTS);
                float[] h = {0};
                SkillTasks.later(41, () -> h[0] = p.getHealth());
                SkillTasks.later(81, () -> {
                    log("dirge: eight enemies, health {} -> {} in 2s (cap {} a second)", String.format("%.1f", h[0]), String.format("%.1f", p.getHealth()),
                            com.jo112ss.rpgmod.job.Bard.DIRGE_HEAL_CAP);
                    foes.forEach(net.minecraft.world.entity.Entity::discard);
                    com.jo112ss.rpgmod.job.Bard.stop(p);
                });
            }
            case "truce" -> { // monsters leave each other alone; the fallen adventurers sense from 64 blocks
                Vec3 at = p.position().add(0, 0, 30);
                java.util.List<net.minecraft.world.entity.Mob> ms = new java.util.ArrayList<>();
                for (var type : java.util.List.<net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.Mob>>of(net.minecraft.world.entity.EntityTypes.ZOMBIE,
                        net.minecraft.world.entity.EntityTypes.ZOMBIE, net.minecraft.world.entity.EntityTypes.SKELETON, com.jo112ss.rpgmod.registry.ModRegistries.REVENANT.get())) {
                    var m = type.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                    m.snapTo(at.x + ms.size() * 2, p.getY(), at.z, 0, 0);
                    level.addFreshEntity(m);
                    ms.add(m);
                }
                var a = ms.get(0); var b = ms.get(1); var sk = ms.get(2); var rv = ms.get(3);
                float hb = b.getHealth();
                b.hurtServer(level, level.damageSources().mobAttack(a), 6);
                float hs = b.getHealth();
                b.hurtServer(level, level.damageSources().mobAttack(sk), 6);
                a.setTarget(b);
                var creeper = net.minecraft.world.entity.EntityTypes.CREEPER.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                creeper.snapTo(at.x, p.getY(), at.z + 3, 0, 0);
                level.addFreshEntity(creeper);
                float hc = b.getHealth();
                b.hurtServer(level, level.damageSources().explosion(creeper, creeper), 6);
                log("truce: zombie caught in a creeper's blast {} -> {} (it may hurt), target after it {}", hc, b.getHealth(), b.getTarget() == null ? "none" : b.getTarget().getType().getDescriptionId());
                creeper.discard();
                float hp = b.getHealth();
                b.hurtServer(level, p.damageSources().playerAttack(p), 1);
                log("truce: zombie hit by a zombie {} -> {}, by a skeleton {} -> {}; zombie told to target a zombie -> {}; by the player {} -> {}; revenant follow range {}, zombie {}",
                        hb, hs, hs, b.getHealth(), a.getTarget() == null ? "no target" : a.getTarget().getType().getDescriptionId(), hp, b.getHealth(),
                        rv.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE),
                        a.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE));
                ms.forEach(net.minecraft.world.entity.Entity::discard);
            }
            case "songtime" -> { // a bard's song grows from 5s (level 1) to 12s (top level); a spellsinger's stays 12s
                become(p, "bard");
                var sd = JobManager.data(p);
                int was = sd.level();
                StringBuilder sb = new StringBuilder();
                for (int lvl : new int[] {1, 50, 100, 150, com.jo112ss.rpgmod.job.JobConfig.maxLevel()}) {
                    sd.setLevel(lvl);
                    sb.append("Lv").append(lvl).append(' ').append(String.format("%.2f", com.jo112ss.rpgmod.job.Bard.songSeconds(p, "war_song")))
                            .append("s/").append(String.format("%.2f", com.jo112ss.rpgmod.job.Bard.songSeconds(p, "flame_melody"))).append("s ");
                }
                sd.setLevel(was);
                log("song time bard/spellsinger: {}", sb.toString().trim());
                StringBuilder rg = new StringBuilder();
                for (String j : new String[] {"bard", "maestro", "spellsinger", "wizard", "fighter"}) {
                    become(p, j);
                    rg.append(j).append('=').append(String.format("%.2f", JobManager.manaRegen(p))).append(' ');
                }
                log("mana regen per second: {}", rg.toString().trim());
            }
            case "dungeonloot" -> { // chests by level: plain metals on the way, diamonds and netherite only at the end
                var lr = net.minecraft.util.RandomSource.create(11);
                for (int lvl : new int[] {1, 49, 50, 99, 100, 149, 150, 200}) {
                    for (boolean last : new boolean[] {false, true}) {
                        java.util.Map<String, Integer> tally = new java.util.TreeMap<>();
                        for (int k = 0; k < 200; k++) {
                            var loot = last ? com.jo112ss.rpgmod.dungeon.DungeonManager.finalLoot(lr, lvl, false) : com.jo112ss.rpgmod.dungeon.DungeonManager.roomLoot(lr, lvl);
                            for (var st : loot) tally.merge(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()).getPath(), st.getCount(), Integer::sum);
                        }
                        StringBuilder sb = new StringBuilder();
                        tally.forEach((k, v) -> sb.append(k).append('=').append(String.format("%.2f", v / 200.0)).append(' '));
                        log("dungeon loot Lv{} {} per chest: {}", lvl, last ? "final" : "room", sb.toString().trim());
                    }
                }
                log("dungeon loot: boss chance {}, boss books {}", com.jo112ss.rpgmod.dungeon.DungeonManager.BOSS_CHANCE, com.jo112ss.rpgmod.mob.MobConfig.bossBooks());
            }
            case "masterwork" -> { // one weapon in five, any quality; the boost works from the main hand
                java.util.Random rnd = new java.util.Random(7);
                for (var type : java.util.List.of(com.jo112ss.rpgmod.item.WeaponType.SWORD, com.jo112ss.rpgmod.item.WeaponType.STAFF,
                        com.jo112ss.rpgmod.item.WeaponType.LUTE, com.jo112ss.rpgmod.item.WeaponType.CHESTPLATE)) {
                    int got = 0;
                    java.util.List<String> some = new java.util.ArrayList<>();
                    for (int k = 0; k < 1000; k++) {
                        var m = com.jo112ss.rpgmod.smithing.Masterwork.maybe(type, 150, rnd);
                        if (m == null) continue;
                        got++;
                        if (some.size() < 4) some.add(m.skill() + " " + m.kind() + " " + m.amount());
                    }
                    // below level 100: never an advanced job's skill
                    int adv = 0;
                    for (int k = 0; k < 1000; k++) {
                        var m = com.jo112ss.rpgmod.smithing.Masterwork.maybe(type, 99, rnd);
                        if (m != null && com.jo112ss.rpgmod.job.core.JobDefs.isAdvanced(com.jo112ss.rpgmod.job.core.JobDefs.skill(m.skill()).job())) adv++;
                    }
                    log("masterwork {} at Lv99: {} advanced-job skills of 1000, low jobs {}", type, adv, com.jo112ss.rpgmod.smithing.Masterwork.jobsFor(type, false));
                    log("masterwork {}: {} of 1000, jobs {}, {} skills, e.g. {}", type, got, com.jo112ss.rpgmod.smithing.Masterwork.jobsFor(type),
                            com.jo112ss.rpgmod.smithing.Masterwork.skillsFor(type).size(), some);
                }
                become(p, "fighter");
                JobManager.data(p).devUnlockAll();
                var od = JobManager.data(p);
                String advF = JobDefs.ADVANCED.get("fighter");
                log("masterwork opened: fighter {}, its advanced {} {} (mastered {}), wizard {}", od.opened("fighter"), advF, od.opened(advF),
                        od.mastered("fighter"), od.opened("wizard"));
                var def = JobDefs.skill("power_slash");
                var sword = new net.minecraft.world.item.ItemStack(com.jo112ss.rpgmod.item.ModItems.get(com.jo112ss.rpgmod.item.WeaponType.SWORD, com.jo112ss.rpgmod.item.WeaponMaterial.DIAMOND));
                p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, sword);
                double plain = JobManager.powerMultiplier(p, def), cdPlain = JobManager.cooldownSeconds(p, def);
                sword.set(com.jo112ss.rpgmod.registry.ModRegistries.FORGED_DATA.get(), com.jo112ss.rpgmod.smithing.ForgedData.of(com.jo112ss.rpgmod.smithing.core.Quality.COMMON,
                        java.util.List.of(), "test", new com.jo112ss.rpgmod.smithing.Masterwork("power_slash", "POWER", 0.15)));
                double boosted = JobManager.powerMultiplier(p, def);
                log("masterwork: power slash x{} with the masterwork sword (1.15 expected), cooldown {}s -> {}s; tooltip line '{}'",
                        String.format("%.3f", boosted / plain), String.format("%.2f", cdPlain), String.format("%.2f", JobManager.cooldownSeconds(p, def)),
                        com.jo112ss.rpgmod.smithing.Masterwork.describe(new com.jo112ss.rpgmod.smithing.Masterwork("power_slash", "POWER", 0.15)).getString());
                java.util.List<String> names = new java.util.ArrayList<>();
                for (int k = 0; k < 4; k++) {
                    var st = new net.minecraft.world.item.ItemStack(com.jo112ss.rpgmod.item.ModItems.get(com.jo112ss.rpgmod.item.WeaponType.SPEAR, com.jo112ss.rpgmod.item.WeaponMaterial.DIAMOND));
                    com.jo112ss.rpgmod.smithing.ForgeNames.apply(st, rnd);
                    names.add(st.getHoverName().getString());
                }
                log("forge names: {}", names);
                // songs: boost cells lengthen the bard's songs and shorten the spellsinger's, and cut their mana
                for (String[] js : new String[][] {{"bard", "war_song"}, {"spellsinger", "flame_melody"}}) {
                    become(p, js[0]);
                    var dd = JobManager.data(p);
                    double s0 = com.jo112ss.rpgmod.job.Bard.songSeconds(p, js[1]);
                    dd.devUnlockAll();
                    log("songs {}: {} plays {}s -> {}s with its boost cells, mana {} -> {} ({})", js[0], js[1], String.format("%.1f", s0),
                            String.format("%.1f", com.jo112ss.rpgmod.job.Bard.songSeconds(p, js[1])), Math.round(JobDefs.skill(js[1]).mana()),
                            Math.round(JobManager.manaCost(p, JobDefs.skill(js[1]))), JobManager.skillBoost(p, js[1], com.jo112ss.rpgmod.job.core.SkillBoost.DURATION));
                }
            }
            case "regen" -> { // a potion's regeneration (2% a heal), then the blessing (as much in all as before, over 14s)
                become(p, "healer");
                JobManager.data(p).devUnlockAll();
                float max = p.getMaxHealth();
                p.getFoodData().setFoodLevel(10); // no natural regeneration from food in the way (needs 18)
                p.getFoodData().setSaturation(0);
                p.setHealth(max * 0.1F);
                p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.INSTANT_HEALTH, 1, 1));
                SkillTasks.later(2, () -> {
                    log("regen: instant health II healed {}% of max health (16% expected)", String.format("%.1f", (p.getHealth() / max - 0.1F) * 100));
                    p.setHealth(max * 0.1F);
                });
                SkillTasks.later(4, () -> { // as a potion would: not from a skill (the task runs as one)
                    com.jo112ss.rpgmod.job.HealthScale.SKILL.remove();
                    p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.REGENERATION, 101, 0));
                });
                SkillTasks.later(109, () -> {
                    log("regen: potion regeneration I 5s healed {}% of max health (2 heals x 2% expected)", String.format("%.1f", (p.getHealth() / max - 0.1F) * 100));
                    p.removeEffect(net.minecraft.world.effect.MobEffects.REGENERATION);
                    p.setHealth(max * 0.1F);
                    SkillExecutor.execute(p, JobDefs.skill("blessing"), JobManager.powerMultiplier(p, JobDefs.skill("blessing")));
                    var inst = p.getEffect(net.minecraft.world.effect.MobEffects.REGENERATION);
                    int dur = inst == null ? 0 : inst.getDuration();
                    SkillTasks.later(Math.min(dur + 5, 700), () -> log("regen: blessing ({}t) healed {}% of max health (as before: {} heals x 5% over 8s)", dur,
                            String.format("%.1f", (p.getHealth() / max - 0.1F) * 100), (int) (dur * 8 / 14 / 50)));
                });
            }
            case "harvest" -> { // the harvest song: hunger and saturation back over the song
                become(p, "bard");
                JobData hd = JobManager.data(p);
                hd.devUnlockAll();
                p.getFoodData().setFoodLevel(2);
                p.getFoodData().setSaturation(0);
                SkillExecutor.execute(p, JobDefs.skill("harvest_song"), JobManager.powerMultiplier(p, JobDefs.skill("harvest_song")));
                hd.setSongPoints(JobData.MAX_SONG_POINTS);
                for (int at : new int[] {120, 280, 400}) SkillTasks.later(at, () -> log("harvest: {}s food {} saturation {} performing {}", at / 20,
                        p.getFoodData().getFoodLevel(), String.format("%.1f", p.getFoodData().getSaturationLevel()), com.jo112ss.rpgmod.job.Bard.performing(p)));
            }
            case "spearshield" -> { // a lancer with a shield in the off hand: right click should raise the shield
                become(p, "lancer");
                p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, new ItemStack(ModItems.get(com.jo112ss.rpgmod.item.WeaponType.SHIELD, WeaponMaterial.IRON)));
            }
            case "dps" -> DpsBench.start(level, p);
            case "bard" -> become(p, "bard");
            case "hybrids" -> hybrids(level, p, dummies);
            case "coins" -> coins(level, p);
            case "statsdump" -> statsDump();
            case "jobstats" -> { // every job at the top level (199): its stats with no panel cells, and with the whole panel
                StringBuilder head = new StringBuilder("jobstats|job|kind|panel|maxhp");
                for (var st : com.jo112ss.rpgmod.job.core.JobStat.values()) head.append('|').append(st.id);
                head.append("|points|panel cost");
                log(head.toString());
                for (var job : JobDefs.JOBS.values()) {
                    String id = job.id();
                    boolean adv = JobDefs.isAdvanced(id);
                    String base = adv ? JobDefs.BASE_OF.get(id) : id;
                    JobData jd = JobManager.data(p);
                    for (boolean full : new boolean[] {false, true}) {
                        jd.switchTo(base, adv);
                        jd.setLevel(com.jo112ss.rpgmod.job.core.LevelCurve.MAX_TOTAL_LEVEL);
                        jd.ensureAdvanced();
                        jd.resetPanel();
                        if (adv) jd.resetAdvPanel();
                        if (full) jd.devUnlockAll();
                        com.jo112ss.rpgmod.job.JobStats.apply(p, jd);
                        var m = com.jo112ss.rpgmod.job.JobStats.compute(jd);
                        StringBuilder b = new StringBuilder("jobstats|" + id + "|" + (adv ? "adv" : JobDefs.isHybrid(id) ? "hybrid" : "base") + "|" + (full ? "full" : "none")
                                + "|" + String.format("%.0f", p.getMaxHealth()));
                        for (var st : com.jo112ss.rpgmod.job.core.JobStat.values()) b.append('|').append(String.format("%.2f", m.getOrDefault(st, 0.0)));
                        int cost = 0;
                        for (var n : jd.panel().nodes()) cost += n.cost();
                        if (adv && jd.panel(true) != null) for (var n : jd.panel(true).nodes()) cost += n.cost();
                        b.append('|').append(jd.totalPoints() + (adv ? jd.totalPoints(true) : 0)).append('|').append(cost);
                        log(b.toString());
                    }
                }
            }
            case "linger" -> linger(level, p);
            case "ward" -> ward(level, p, dummies);
            case "wardlinger" -> {
                become(p, "spellsinger");
                SkillExecutor.execute(p, JobDefs.skill("ward_melody"), JobManager.powerMultiplier(p, JobDefs.skill("ward_melody")));
                JobManager.data(p).setSongPoints(14);
                SkillTasks.later(30, () -> JobManager.data(p).setSong(JobManager.data(p).song(), JobManager.data(p).songStart(), level.getGameTime() + 5));
                SkillTasks.later(50, () -> {
                    var abs = p.getEffect(net.minecraft.world.effect.MobEffects.ABSORPTION);
                    log("ward linger: song over {}, absorption {} for {} ticks more (max hp {})", !com.jo112ss.rpgmod.job.Bard.performing(p),
                            String.format("%.1f", p.getAbsorptionAmount()), abs == null ? 0 : abs.getDuration(), String.format("%.0f", p.getMaxHealth()));
                });
            }
            case "songs_bard" -> songs(level, p, "bard", false, List.of("war_song", "rhapsody", "fanfare", "dirge"), 2, -1);
            case "songs_maestro" -> songs(level, p, "bard", true, List.of("war_song", "heal_song", "", "", "guard_song", "rhapsody"), 0, 5);
            case "songs_singer" -> songs(level, p, "spellsinger", false, List.of("ward_melody", "drain_tune", "flame_melody"), 0, -1);
            case "sb0", "sb1", "sb2" -> spellblade(p, name.charAt(2) - '0');
            case "sblow" -> { // only the first stage paid for: the other two show red
                spellblade(p, 0);
                JobManager.data(p).setMana(20);
                JobManager.sync(p);
                log("spellblade: mana 20");
            }
            case "sbcrit" -> { // every hit would crit: sword magic must not
                spellblade(p, 2);
                JobManager.testForceCrit = true;
                log("spellblade: crits forced on");
            }
            case "nocrit" -> {
                JobManager.testForceCrit = null;
                log("crits back to normal");
            }
            case "sbquick" -> {
                spellblade(p, 2);
                p.addEffect(new net.minecraft.world.effect.MobEffectInstance(ModEffects.QUICK_CAST, 200, 0));
                log("spellblade with quick cast: stages at {} ticks", java.util.Arrays.toString(com.jo112ss.rpgmod.job.SpellbladeMagic.stageTicks(p)));
            }
            case "meditate" -> meditate(level, p);
            case "hitmana" -> hitMana(p, dummies);
            case "ults" -> ults(level, p, dummies);
            case "elites" -> elites(level, p);
            case "revenants" -> revenants(level, p);
            case "gear" -> hiddenGear(level, p);
            case "dungeon" -> dungeon(level, p);
            case "dgallery" -> dungeonGallery(level, p);
            case "bosses" -> newBosses(level, p);
            case "eggs" -> spawnEggs(level, p);
            case "bosswater" -> bossWater(level, p);
            case "counter_lance" -> counterLance(level, p, dummies);
            case "wall", "unwall" -> wall(level, p, name.equals("wall"));
            default -> {
                if (JobDefs.isAdvanced(name)) become(p, JobDefs.BASE_OF.get(name), true);
                else if (JobDefs.job(name) != null) become(p, name);
                else log("unknown scene {}", name);
            }
        }
    }

    private static void become(ServerPlayer p, String job) {
        become(p, job, false);
    }

    private static void become(ServerPlayer p, String job, boolean adv) {
        JobData d = JobManager.data(p);
        if (startJob == null) {
            startJob = d.jobId();
            startAdv = d.isAdvanced();
        }
        d.switchTo(job, adv);
        d.ensureAdvanced();
        JobType type = JobType.byId(job);
        p.getInventory().setItem(6, new ItemStack(ModItems.get(type.weapons.get(0), WeaponMaterial.IRON)));
        if (type == JobType.SENTINEL) p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, new ItemStack(ModItems.get(com.jo112ss.rpgmod.item.WeaponType.SHIELD, WeaponMaterial.IRON)));
        p.getInventory().setSelectedSlot(6);
        JobStats.apply(p, d);
        JobManager.sync(p);
        log("now {} Lv{} hp {} atk {} matk {}", job, d.level(), String.format("%.0f", p.getMaxHealth()),
                String.format("%.1f", JobManager.physicalAttack(p)), String.format("%.1f", JobManager.magicAttack(p)));
    }

    private static LivingEntity front(List<? extends LivingEntity> dummies) {
        return dummies.isEmpty() ? null : dummies.get(0);
    }

    private static void pirate(ServerLevel level, ServerPlayer p, List<? extends LivingEntity> dummies) {
        become(p, "pirate");
        LivingEntity t = front(dummies);
        if (t == null) return;
        JobData d = JobManager.data(p);
        SkillTasks.later(3, () -> {
            float hp0 = t.getHealth();
            Pirate.fire(p);
            log("gun: one shot {} -> {} hp (atk {} x {}), rounds {}", String.format("%.1f", hp0), String.format("%.1f", t.getHealth()),
                    String.format("%.1f", JobManager.physicalAttack(p)), Pirate.SHOT_RATIO, d.ammo());
            for (int i = 0; i < 6; i++) Pirate.fire(p);
            log("gun: emptied, rounds {} reloading {}", d.ammo(), d.reloadEnd() > level.getGameTime());
        });
        SkillTasks.later(46, () -> log("gun: after the reload rounds {}", d.ammo()));
        SkillTasks.later(48, () -> {
            Pirate.fire(p);
            Pirate.fire(p);
            int before = d.ammo();
            p.attack(t);
            log("gun: bayonet hit (no round loaded any more) {} -> {}", before, d.ammo());
        });
    }

    /** Hybrid jobs: the unlock, the points, the panel without mastery locks, the spellblade's element on sword hits. */
    private static void hybrids(ServerLevel level, ServerPlayer p, List<? extends LivingEntity> dummies) {
        JobData d = JobManager.data(p);
        long fw = d.mastery("fighter"), wz = d.mastery("wizard");
        d.setMastery("fighter", com.jo112ss.rpgmod.job.core.LevelCurve.masteryExp());
        d.setMastery("wizard", 0);
        String half = JobManager.changeProblem(d, "spellblade");
        d.setMastery("wizard", com.jo112ss.rpgmod.job.core.LevelCurve.masteryExp());
        String both = JobManager.changeProblem(d, "spellblade");
        become(p, "spellblade");
        var panel = d.panel();
        com.jo112ss.rpgmod.job.core.SkillPanel.Node outer = null;
        for (var n : panel.nodes()) if (n.type() == com.jo112ss.rpgmod.job.core.SkillPanel.NodeType.ULTIMATE) outer = n;
        log("hybrid: needs one mastered -> {}, both -> {}; spellblade hybrid={} slot3={} points {} (level {}) waiting adv {} ultimate needs mastery? {}",
                half, both, d.isHybrid(), d.hasSlot3(), d.totalPoints(), d.level(), d.waitingAdvPoints(), outer == null ? "-" : "no (hybrid)");
        log("hybrid: spellblade skill attack {} = attack {} + own magic {}", String.format("%.1f", JobManager.spellbladeAttack(p)),
                String.format("%.1f", JobManager.physicalAttack(p)), String.format("%.1f", JobStats.compute(d).get(com.jo112ss.rpgmod.job.core.JobStat.MAGIC_ATTACK)));
        if (dummies.size() >= 5) {
            LivingEntity near = dummies.get(4);
            SkillTasks.later(25, () -> {
                float a = near.getHealth();
                float charge = p.getAttackStrengthScale(0.5F);
                p.attack(near);
                log("hybrid: spellblade sword hit with {} took {} (on fire {}, charge {}, held {})", com.jo112ss.rpgmod.job.MoreJobSkills.element(p),
                        String.format("%.1f", a - near.getHealth()), near.isOnFire(), String.format("%.2f", charge), p.getMainHandItem().getItem());
                d.setMastery("fighter", fw);
                d.setMastery("wizard", wz);
            });
        }
    }

    /** The spellblade with an element and full mana, sword magic casts logged. */
    private static void spellblade(ServerPlayer p, int el) {
        JobData d = JobManager.data(p);
        if (!"spellblade".equals(d.jobId())) become(p, "spellblade");
        d.setCoatMode(el);
        d.setMana(JobManager.maxMana(p));
        com.jo112ss.rpgmod.job.SpellbladeMagic.testLog = true;
        JobManager.sync(p);
        log("spellblade element {} mana {} / {}", el, String.format("%.0f", d.mana()), String.format("%.0f", JobManager.maxMana(p)));
    }

    /** Mana meditation: mana back over 3s with it and without; using it again stands you up; mana share with no one near. */
    private static void meditate(ServerLevel level, ServerPlayer p) {
        JobData d = JobManager.data(p);
        if (!"spellblade".equals(d.jobId())) become(p, "spellblade");
        var med = JobDefs.skill("mana_meditate");
        d.setMana(0);
        SkillTasks.later(60, () -> {
            double plain = d.mana();
            d.setMana(0);
            SkillExecutor.execute(p, med, 1);
            SkillTasks.later(60, () -> {
                log("meditate: mana over 3s plain {} meditating {} (max {}), effect {}", String.format("%.1f", plain), String.format("%.1f", d.mana()),
                        String.format("%.0f", JobManager.maxMana(p)), p.hasEffect(ModEffects.MANA_MEDITATE));
                SkillExecutor.execute(p, med, 1);
                log("meditate: used again -> effect {}", p.hasEffect(ModEffects.MANA_MEDITATE));
                boolean shared = SkillExecutor.execute(p, JobDefs.skill("mana_share"), 1);
                log("mana share alone: {}", shared);
                d.setMana(JobManager.maxMana(p));
            });
        });
    }

    /**
     * Physical MP: max 100 whatever the level, normal hits give MP back (by the weapon's attack time,
     * scaled by the charge), a skill does not; the bard line keeps 100 too and pays for the sound wave.
     */
    private static void hitMana(ServerPlayer p, List<? extends LivingEntity> dummies) {
        LivingEntity t = front(dummies);
        if (t == null) return;
        JobData d = JobManager.data(p);
        com.jo112ss.rpgmod.job.HitMana.testLog = true;
        String[] jobs = {"fighter", "lancer", "monk"};
        for (int i = 0; i < jobs.length; i++) {
            String job = jobs[i];
            SkillTasks.later(i * 40, () -> become(p, job));
            SkillTasks.later(i * 40 + 30, () -> {
                d.setMana(0);
                p.attack(t); // charged
                double one = d.mana();
                p.attack(t); // straight after: hardly charged
                log("hitmana {}: physical {} max {} after a charged hit {} after an uncharged one {}", job,
                        com.jo112ss.rpgmod.job.HitMana.physical(d), String.format("%.0f", JobManager.maxMana(p)),
                        String.format("%.2f", one), String.format("%.2f", d.mana()));
                double before = d.mana();
                SkillExecutor.execute(p, JobDefs.skill(d.knownSkills(com.jo112ss.rpgmod.job.core.SkillKind.ACTIVE).isEmpty() ? "power_slash"
                        : d.knownSkills(com.jo112ss.rpgmod.job.core.SkillKind.ACTIVE).get(0)), 1);
                log("hitmana {}: a skill hit leaves mana {} -> {} (no gain)", job, String.format("%.2f", before), String.format("%.2f", d.mana()));
            });
        }
        // the monk's knuckle is charged again before the delay: that hit gives nothing, the next one the time since the gain
        SkillTasks.later(2 * 40 + 36, () -> {
            double m = d.mana();
            p.attack(t);
            log("hitmana monk: charged hit 6 ticks after a gain {} -> {} (delay)", String.format("%.2f", m), String.format("%.2f", d.mana()));
        });
        SkillTasks.later(2 * 40 + 50, () -> {
            double m = d.mana();
            p.attack(t);
            log("hitmana monk: charged hit 20 ticks after the gain {} -> {}", String.format("%.2f", m), String.format("%.2f", d.mana()));
            d.setMana(0);
        });
        SkillTasks.later(2 * 40 + 50 + 41, () -> log("hitmana monk: natural regeneration in combat over 2s: {} (a third of {}/s)",
                String.format("%.2f", d.mana()), String.format("%.2f", JobManager.manaRegen(p))));
        SkillTasks.later(180, () -> {
            for (String job : new String[] {"bard", "spellsinger", "wizard"}) {
                become(p, job);
                log("hitmana {}: physical {} max mana {}", job, com.jo112ss.rpgmod.job.HitMana.physical(d), String.format("%.0f", JobManager.maxMana(p)));
            }
            become(p, "bard");
            d.setMana(100);
            com.jo112ss.rpgmod.job.BardAttack.wave(p);
            log("hitmana bard: a sound wave leaves {} MP", String.format("%.0f", d.mana()));
            d.setMana(1);
            com.jo112ss.rpgmod.job.BardAttack.wave(p);
            log("hitmana bard: with 1 MP the wave is refused: {} MP", String.format("%.0f", d.mana()));
            d.setMana(100);
            com.jo112ss.rpgmod.job.HitMana.testLog = false;
        });
    }

    /** Elite monsters: their stats, a few traits at work, and their plates on camera. */
    private static void elites(ServerLevel level, ServerPlayer p) {
        Vec3 face = net.minecraft.world.phys.Vec3.directionFromRotation(0, p.getYRot());
        Vec3 side = new Vec3(-face.z, 0, face.x);
        java.util.function.BiFunction<Integer, Double, net.minecraft.world.entity.Mob> spawn = (mask, off) -> {
            var z = net.minecraft.world.entity.EntityTypes.HUSK.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND); // husks do not burn in the sun
            Vec3 at = p.position().add(face.scale(16)).add(side.scale(off * 0.8)); // beyond the training dummies and what lingers round them
            z.snapTo(at.x, at.y, at.z, p.getYRot() + 180, 0);
            z.setNoAi(true);
            com.jo112ss.rpgmod.mob.Elite.forceNext = mask;
            level.addFreshEntity(z);
            com.jo112ss.rpgmod.mob.Elite.forceNext = null;
            // no random gear: the same armour on all of them, so the hits compare
            for (var slot : net.minecraft.world.entity.EquipmentSlot.values()) z.setItemSlot(slot, ItemStack.EMPTY);
            return z;
        };
        var plain = spawn.apply(0, -6.0);
        var iron = spawn.apply(com.jo112ss.rpgmod.mob.Elite.Trait.IRON.bit() | com.jo112ss.rpgmod.mob.Elite.Trait.COMMANDER.bit(), -3.0);
        var ward = spawn.apply(com.jo112ss.rpgmod.mob.Elite.Trait.WARDED.bit() | com.jo112ss.rpgmod.mob.Elite.Trait.BLAZE.bit(), 0.0);
        var regen = spawn.apply(com.jo112ss.rpgmod.mob.Elite.Trait.REGEN.bit() | com.jo112ss.rpgmod.mob.Elite.Trait.FROST.bit() | com.jo112ss.rpgmod.mob.Elite.Trait.SWIFT.bit(), 3.0);
        var split = spawn.apply(com.jo112ss.rpgmod.mob.Elite.Trait.SPLIT.bit() | com.jo112ss.rpgmod.mob.Elite.Trait.GUARDIAN.bit(), 6.0);
        log("elites: max health plain {} elite {} (x{}), traits iron {} ward {} regen {} split {}, scale {}", String.format("%.0f", plain.getMaxHealth()),
                String.format("%.0f", iron.getMaxHealth()), String.format("%.2f", iron.getMaxHealth() / plain.getMaxHealth()),
                com.jo112ss.rpgmod.mob.Elite.traits(iron), com.jo112ss.rpgmod.mob.Elite.traits(ward), com.jo112ss.rpgmod.mob.Elite.traits(regen),
                com.jo112ss.rpgmod.mob.Elite.traits(split), String.format("%.2f", iron.getScale()));
        SkillTasks.later(30, () -> {
            float a = plain.getHealth(), b = iron.getHealth();
            plain.hurtServer(level, p.damageSources().playerAttack(p), 100);
            iron.hurtServer(level, p.damageSources().playerAttack(p), 100);
            log("elites: a 100 hit takes plain {} iron {} (-35%)", String.format("%.1f", a - plain.getHealth()), String.format("%.1f", b - iron.getHealth()));
            regen.hurtServer(level, p.damageSources().playerAttack(p), 300);
        });
        SkillTasks.later(50, () -> { // magic, once the hurt time is over
            float c = ward.getHealth(), d = plain.getHealth();
            ward.hurtServer(level, p.damageSources().source(com.jo112ss.rpgmod.job.SkillExecutor.magicKey(), p, p), 100);
            plain.hurtServer(level, p.damageSources().source(com.jo112ss.rpgmod.job.SkillExecutor.magicKey(), p, p), 100);
            log("elites: 100 magic takes plain {} ward {} (half)", String.format("%.1f", d - plain.getHealth()), String.format("%.1f", c - ward.getHealth()));
        });
        float[] afterHit = {0};
        SkillTasks.later(32, () -> afterHit[0] = regen.getHealth());
        SkillTasks.later(130, () -> log("elites: regen {} -> {} after 5s unhurt (max {})", String.format("%.1f", afterHit[0]), String.format("%.1f", regen.getHealth()),
                String.format("%.0f", regen.getMaxHealth())));
        // the guardian: a target, then (within 10s) the warning and the shields
        SkillTasks.later(200, () -> com.jo112ss.rpgmod.mob.Elite.guard(split, level));
        SkillTasks.later(240, () -> log("elites: guardian shields (8 blocks) - absorption on the ward (4.8 away) {} regen (2.4 away) {}, the plain one (9.6 away) {}",
                String.format("%.1f", ward.getAbsorptionAmount()), String.format("%.1f", regen.getAbsorptionAmount()), String.format("%.1f", plain.getAbsorptionAmount())));
        SkillTasks.later(250, () -> {
            int before = level.getEntitiesOfClass(net.minecraft.world.entity.monster.zombie.Husk.class, p.getBoundingBox().inflate(20)).size();
            split.hurtServer(level, p.damageSources().playerAttack(p), 1e6F);
            SkillTasks.later(2, () -> {
                var halves = level.getEntitiesOfClass(net.minecraft.world.entity.monster.zombie.Husk.class, p.getBoundingBox().inflate(20),
                        z -> z.entityTags().contains(com.jo112ss.rpgmod.mob.Elite.HALF));
                log("elites: split -> zombies {} before, halves {} (health {} / {}, elite {})", before, halves.size(),
                        halves.isEmpty() ? "-" : String.format("%.0f", halves.get(0).getHealth()), halves.isEmpty() ? "-" : String.format("%.0f", halves.get(0).getMaxHealth()),
                        halves.isEmpty() ? "-" : com.jo112ss.rpgmod.mob.Elite.traits(halves.get(0)));
            });
        });
        SkillTasks.later(300, () -> {
            for (var z : level.getEntitiesOfClass(net.minecraft.world.entity.monster.zombie.Husk.class, p.getBoundingBox().inflate(20))) z.discard();
        });
    }

    /**
     * Bosses against a player in a boat out on a pond: each boss should leap to the shore nearest them
     * (not wade in), fire homing shots that hit, and, dropped into the water, leap back out.
     */
    private static void bossWater(ServerLevel level, ServerPlayer p) {
        Vec3 home = p.position();
        int cx = (int) Math.floor(home.x), cz = (int) Math.floor(home.z) - 220;
        int top = (int) Math.floor(home.y) - 1; // the grass of the flat test world
        p.teleportTo(cx + 0.5, top + 1, cz + 0.5);
        var mode = p.gameMode();
        boolean[] done = {false};
        net.minecraft.world.entity.Entity[] boat = {null};
        float[] lost = {0};
        SkillTasks.later(30, () -> {
            // a pond 17 x 17, three deep, in the flat ground
            for (int x = -8; x <= 8; x++) {
                for (int z = -8; z <= 8; z++) {
                    for (int y = top - 2; y <= top; y++) level.setBlockAndUpdate(new net.minecraft.core.BlockPos(cx + x, y, cz + z), net.minecraft.world.level.block.Blocks.WATER.defaultBlockState());
                }
            }
            for (var m : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, p.getBoundingBox().inflate(60))) m.discard();
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            p.getAbilities().invulnerable = false;
            p.onUpdateAbilities();
            var b = net.minecraft.world.entity.EntityTypes.DARK_OAK_BOAT.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            b.snapTo(cx + 0.5, top + 0.6, cz + 0.5, 0, 0);
            level.addFreshEntity(b);
            boat[0] = b;
            p.startRiding(b, true, true);
            log("bosswater: pond at {} {} {}, player riding {} - out on the water {}", cx, top, cz, p.getVehicle() != null,
                    com.jo112ss.rpgmod.mob.boss.BossWater.waterBound(p));
        });
        SkillTasks.run(age -> { // keep the tester afloat and standing
            if (age > 30) {
                lost[0] += Math.max(0, p.getMaxHealth() - p.getHealth());
                p.setHealth(p.getMaxHealth());
                p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.RESISTANCE, 20, 3, false, false));
                if (boat[0] != null && p.getVehicle() == null) p.startRiding(boat[0], true, true);
                p.setYRot(0);
            }
            return done[0] || age > 1400;
        });
        var types = com.jo112ss.rpgmod.mob.boss.Bosses.all();
        for (int i = 0; i < types.size(); i++) {
            var type = types.get(i);
            int t0 = 50 + i * 300;
            net.minecraft.world.entity.Mob[] boss = {null};
            SkillTasks.later(t0, () -> {
                for (var m : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, p.getBoundingBox().inflate(60))) m.discard();
                var b = type.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
                b.snapTo(cx + 0.5, top + 1, cz + 24.5, 180, 0);
                b.setPersistenceRequired();
                b.setData(com.jo112ss.rpgmod.registry.ModRegistries.MOB_LEVEL, 60);
                level.addFreshEntity(b);
                ((com.jo112ss.rpgmod.mob.boss.ModBoss) b).recomputeHealth(true);
                b.setTarget(p);
                boss[0] = b;
                log("bosswater: {} on the bank, {} blocks from the boat", b.getType().getDescriptionId(), String.format("%.1f", b.distanceTo(p)));
            });
            java.util.function.Consumer<String> report = when -> {
                var b = boss[0];
                if (b == null) return;
                com.jo112ss.rpgmod.mob.boss.BossWater.Tracker w = waterOf(b);
                com.jo112ss.rpgmod.mob.boss.BossWater.Homing h = homingOf(b);
                log("bosswater: {} {} - at {} {} from the boat, in water {}, leaps {} volleys {} homing hits {}, alive {}", b.getType().getDescriptionId(), when,
                        b.blockPosition(), String.format("%.1f", b.distanceTo(p)), com.jo112ss.rpgmod.mob.boss.BossWater.inWater(b), w.leaps, w.volleys, h.hits, b.isAlive());
            };
            SkillTasks.later(t0 + 150, () -> report.accept("after 7 s"));
            SkillTasks.later(t0 + 160, () -> { // dropped into the pond
                var b = boss[0];
                if (b == null) return;
                b.teleportTo(cx + 4.5, top - 1, cz + 4.5);
                log("bosswater: {} dropped into the pond, in water {}", b.getType().getDescriptionId(), b.isInWater());
            });
            SkillTasks.later(t0 + 175, () -> report.accept("0.75 s in the water"));
            SkillTasks.later(t0 + 290, () -> report.accept("after 6.5 s more"));
        }
        SkillTasks.later(50 + types.size() * 300 + 10, () -> {
            done[0] = true;
            log("bosswater: player took {} in all", String.format("%.0f", lost[0]));
            for (var m : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, p.getBoundingBox().inflate(60))) m.discard();
            p.stopRiding();
            if (boat[0] != null) boat[0].discard();
            p.setGameMode(mode);
            p.teleportTo(home.x, home.y, home.z);
        });
    }

    private static com.jo112ss.rpgmod.mob.boss.BossWater.Tracker waterOf(net.minecraft.world.entity.Mob b) {
        return ((com.jo112ss.rpgmod.mob.boss.ModBoss) b).core().water;
    }

    private static com.jo112ss.rpgmod.mob.boss.BossWater.Homing homingOf(net.minecraft.world.entity.Mob b) {
        return ((com.jo112ss.rpgmod.mob.boss.ModBoss) b).core().homing;
    }

    /**
     * The spawn eggs, used like a player would on the ground: every boss and every adventurer, an elite
     * when sneaking, a party of four; the bosses then left to fight on their own for a while.
     */
    private static void spawnEggs(ServerLevel level, ServerPlayer p) {
        Vec3 home = p.position();
        Vec3 field = home.add(-160, 0, 0);
        p.teleportTo(field.x, field.y, field.z);
        p.setYRot(0);
        p.setXRot(10);
        var eggs = new java.util.ArrayList<>(com.jo112ss.rpgmod.registry.ModRegistries.SPAWN_EGGS.values());
        ItemStack[] saved = new ItemStack[9];
        for (int i = 0; i < 9; i++) {
            saved[i] = p.getInventory().getItem(i).copy();
            p.getInventory().setItem(i, new ItemStack(eggs.get(i).get(), 4));
        }
        java.util.List<net.minecraft.world.entity.Mob> bosses = new java.util.ArrayList<>();
        SkillTasks.later(40, () -> {
            for (var m : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, p.getBoundingBox().inflate(60))) m.discard();
            int i = 0;
            for (var holder : eggs) {
                var egg = holder.get();
                var k = egg.kind();
                boolean sneak = k == com.jo112ss.rpgmod.item.RpgSpawnEggItem.Kind.WARRIOR || k == com.jo112ss.rpgmod.item.RpgSpawnEggItem.Kind.PARTY;
                // bosses in a row behind, the adventurers in front
                int x, z;
                if (k.boss()) {
                    x = (int) Math.floor(field.x) - 18 + i * 12;
                    z = (int) Math.floor(field.z) + 26;
                } else {
                    int j = i - 4;
                    x = (int) Math.floor(field.x) - 10 + j * 4;
                    z = (int) Math.floor(field.z) + 10;
                }
                i++;
                net.minecraft.core.BlockPos ground = new net.minecraft.core.BlockPos(x, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
                ItemStack stack = new ItemStack(egg, 2);
                p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
                p.setShiftKeyDown(sneak);
                var before = new java.util.HashSet<>(level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, new net.minecraft.world.phys.AABB(ground).inflate(8)));
                var hit = new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(ground).add(0, 0.5, 0), net.minecraft.core.Direction.UP, ground, false);
                var result = stack.useOn(new net.minecraft.world.item.context.UseOnContext(p, net.minecraft.world.InteractionHand.MAIN_HAND, hit));
                p.setShiftKeyDown(false);
                var came = new java.util.ArrayList<net.minecraft.world.entity.Mob>();
                for (var m : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, new net.minecraft.world.phys.AABB(ground).inflate(8))) {
                    if (!before.contains(m)) came.add(m);
                }
                StringBuilder sb = new StringBuilder();
                for (var m : came) {
                    if (m.getPersistentData().getBooleanOr(com.jo112ss.rpgmod.mob.boss.BossEvents.CHILD, false)) continue;
                    sb.append(m.getType().getDescriptionId());
                    if (m instanceof Revenant r) sb.append("/").append(r.role().id());
                    sb.append(" Lv").append(com.jo112ss.rpgmod.mob.MobLevels.level(m));
                    sb.append(" elite ").append(com.jo112ss.rpgmod.mob.Elite.list(com.jo112ss.rpgmod.mob.Elite.traits(m)));
                    sb.append(m.isPersistenceRequired() ? " persistent" : " may despawn");
                    sb.append("; ");
                    if (k.boss()) bosses.add(m);
                }
                log("eggs: {}{} -> {} (eggs left {}) : {}", k.itemId(), sneak ? " (sneaking)" : "", result, stack.getCount(), sb);
            }
            p.getInventory().setItem(p.getInventory().getSelectedSlot(), new ItemStack(eggs.get(p.getInventory().getSelectedSlot()).get(), 4));
        });
        SkillTasks.later(200, () -> {
            for (var b : bosses) {
                Object st = b instanceof com.jo112ss.rpgmod.mob.boss.KingSlime k ? k.state() : b instanceof com.jo112ss.rpgmod.mob.boss.Golem g ? g.state()
                        : b instanceof com.jo112ss.rpgmod.mob.boss.Lich l ? l.state() : b instanceof com.jo112ss.rpgmod.mob.boss.SpiderQueen q ? q.state() : "?";
                log("eggs: by day {} {} alive {} state {}", b.getType().getDescriptionId(),
                        "(egg)", b.isAlive(), st);
            }
        });
        SkillTasks.later(260, () -> {
            for (var m : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, p.getBoundingBox().inflate(80))) m.discard();
            for (int i = 0; i < 9; i++) p.getInventory().setItem(i, saved[i]);
            p.teleportTo(home.x, home.y, home.z);
        });
    }

    /** The Lich King and the Spider Queen: every move in turn, the enrage, the opening, the reward. */
    private static void newBosses(ServerLevel level, ServerPlayer p) {
        var mode = p.gameMode();
        Vec3 home = p.position();
        Vec3 field = home.add(0, 0, 160);
        p.teleportTo(field.x, field.y, field.z);
        p.setYRot(0);
        p.setXRot(0);
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        p.getAbilities().invulnerable = false;
        p.onUpdateAbilities();
        boolean[] done = {false};
        float[] lost = {0};
        SkillTasks.run(age -> { // keep the tester standing, and count what the bosses take off
            lost[0] += Math.max(0, p.getMaxHealth() - p.getHealth());
            p.setHealth(p.getMaxHealth());
            p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.RESISTANCE, 20, 3, false, false));
            return done[0] || age > 1300;
        });
        com.jo112ss.rpgmod.mob.boss.Lich[] lich = {null};
        com.jo112ss.rpgmod.mob.boss.SpiderQueen[] queen = {null};
        java.util.function.Function<net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.Mob>, net.minecraft.world.entity.Mob> summon = type -> {
            for (var m : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, p.getBoundingBox().inflate(40))) m.discard();
            var b = type.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
            b.snapTo(field.x, field.y, field.z + 13, 180, 0);
            b.setPersistenceRequired();
            b.setData(com.jo112ss.rpgmod.registry.ModRegistries.MOB_LEVEL, 60);
            level.addFreshEntity(b);
            ((com.jo112ss.rpgmod.mob.boss.ModBoss) b).recomputeHealth(true);
            b.setTarget(p);
            log("bosses: {} Lv{} health {} toughness {} size {}x{}", b.getType().getDescriptionId(), com.jo112ss.rpgmod.mob.MobLevels.level(b),
                    String.format("%.0f", b.getMaxHealth()), String.format("%.2f", ((com.jo112ss.rpgmod.mob.boss.ModBoss) b).toughness()),
                    String.format("%.1f", b.getBbWidth()), String.format("%.1f", b.getBbHeight()));
            return b;
        };
        java.util.function.Consumer<String> lm = m -> {
            if (lich[0] == null) return;
            lich[0].testMove(m);
            log("bosses: lich {} -> state {}", m, lich[0].state());
        };
        java.util.function.Consumer<String> qm = m -> {
            if (queen[0] == null) return;
            queen[0].testMove(m);
            log("bosses: queen {} -> state {}", m, queen[0].state());
        };
        // ---- the Lich King
        SkillTasks.later(30, () -> lich[0] = (com.jo112ss.rpgmod.mob.boss.Lich) summon.apply(com.jo112ss.rpgmod.registry.ModRegistries.LICH.get()));
        SkillTasks.later(60, () -> lm.accept("bolts"));
        SkillTasks.later(100, () -> lm.accept("sigils"));
        SkillTasks.later(160, () -> lm.accept("beam"));
        SkillTasks.later(220, () -> lm.accept("summon"));
        SkillTasks.later(268, () -> { // close in for the nova
            p.teleportTo(field.x, field.y, lich[0].getZ() - 4);
            lm.accept("nova");
        });
        SkillTasks.later(300, () -> lm.accept("blink"));
        SkillTasks.later(312, () -> p.teleportTo(field.x, field.y, field.z));
        SkillTasks.later(320, () -> {
            // one huge hit in the first half: the halfway gate stops it at 50%, the rise begins
            var l = lich[0];
            float before = l.getHealth() / l.getMaxHealth();
            l.hurtServer(level, p.damageSources().playerAttack(p), 1e7F);
            log("bosses: lich gate - a huge hit at {}% leaves {}% (phase {})", Math.round(before * 100), String.format("%.1f", l.getHealth() / l.getMaxHealth() * 100), l.phase());
        });
        SkillTasks.later(335, () -> { // and during the rise it still holds
            var l = lich[0];
            l.hurtServer(level, p.damageSources().playerAttack(p), 1e7F);
            log("bosses: lich gate during the rise - {}% (phase {}, state {})", String.format("%.1f", l.getHealth() / l.getMaxHealth() * 100), l.phase(), l.state());
        });
        SkillTasks.later(330, () -> log("bosses: lich phase {} state {} opening x{}", lich[0].phase(), lich[0].state(), lich[0].openingMultiplier()));
        SkillTasks.later(380, () -> lm.accept("storm"));
        SkillTasks.later(470, () -> {
            lm.accept("drained");
            log("bosses: lich drained opening x{}", lich[0].openingMultiplier());
        });
        SkillTasks.later(520, () -> {
            var l = lich[0];
            int minions = level.getEntitiesOfClass(Revenant.class, l.getBoundingBox().inflate(40)).size();
            log("bosses: lich counters - orbs {} sigils {} nova hits {} beam hits {} summoned {} (alive {}) blinks {}; player lost {}", l.orbsFired, l.sigilsBurst,
                    l.novaHits, l.beamHits, l.summoned, minions, l.blinks, String.format("%.0f", lost[0]));
            lost[0] = 0;
            l.hurtServer(level, p.damageSources().playerAttack(p), 1e7F);
            if (l.isAlive()) l.setHealth(0);
        });
        SkillTasks.later(535, () -> log("bosses: lich dead {}, minions left {}, skill books {}", !lich[0].isAlive(),
                level.getEntitiesOfClass(Revenant.class, p.getBoundingBox().inflate(60)).size(),
                p.getInventory().countItem(com.jo112ss.rpgmod.registry.ModRegistries.SKILL_BOOK.get())));
        // ---- the Spider Queen
        SkillTasks.later(560, () -> queen[0] = (com.jo112ss.rpgmod.mob.boss.SpiderQueen) summon.apply(com.jo112ss.rpgmod.registry.ModRegistries.SPIDER_QUEEN.get()));
        SkillTasks.later(585, () -> qm.accept("pounce"));
        SkillTasks.later(625, () -> {
            p.teleportTo(field.x, field.y, field.z);
            qm.accept("webs");
        });
        SkillTasks.later(665, () -> qm.accept("spray"));
        SkillTasks.later(715, () -> qm.accept("eggs"));
        SkillTasks.later(800, () -> qm.accept("skitter"));
        SkillTasks.later(840, () -> {
            p.teleportTo(field.x, field.y, queen[0].getZ() - 3.5);
            qm.accept("swipe");
        });
        SkillTasks.later(870, () -> {
            p.teleportTo(field.x, field.y, field.z);
            var q = queen[0];
            q.hurtServer(level, p.damageSources().playerAttack(p), 1e7F);
            log("bosses: queen gate - a huge hit leaves {}% (phase {})", String.format("%.1f", q.getHealth() / q.getMaxHealth() * 100), q.phase());
        });
        SkillTasks.later(880, () -> log("bosses: queen phase {} state {} opening x{}", queen[0].phase(), queen[0].state(), queen[0].openingMultiplier()));
        SkillTasks.later(925, () -> qm.accept("triple"));
        SkillTasks.later(985, () -> {
            p.teleportTo(field.x, field.y, field.z);
            qm.accept("field");
        });
        SkillTasks.later(1050, () -> {
            qm.accept("spent");
            log("bosses: queen spent opening x{}", queen[0].openingMultiplier());
        });
        SkillTasks.later(1100, () -> {
            var q = queen[0];
            int brood = level.getEntitiesOfClass(net.minecraft.world.entity.monster.spider.CaveSpider.class, q.getBoundingBox().inflate(40)).size();
            for (var s : level.getEntitiesOfClass(net.minecraft.world.entity.monster.spider.CaveSpider.class, p.getBoundingBox().inflate(80)))
                log("bosses:   before death: spider {} at {} age {} (queen at {})", s.getId(), s.blockPosition(), s.tickCount, q.blockPosition());
            log("bosses: queen counters - pounce hits {} web hits {} spray hits {} hatched {} (alive {}) skitter hits {} swipe hits {} field slows {}; player lost {}",
                    q.pounceHits, q.webHits, q.sprayHits, q.hatched, brood, q.skitterHits, q.swipeHits, q.fieldHits, String.format("%.0f", lost[0]));
            q.hurtServer(level, p.damageSources().playerAttack(p), 1e7F);
            if (q.isAlive()) q.setHealth(0);
        });
        SkillTasks.later(1115, () -> {
            log("bosses: queen dead {}, brood left {}, skill books {}", !queen[0].isAlive(),
                    level.getEntitiesOfClass(net.minecraft.world.entity.monster.spider.CaveSpider.class, p.getBoundingBox().inflate(60)).size(),
                    p.getInventory().countItem(com.jo112ss.rpgmod.registry.ModRegistries.SKILL_BOOK.get()));
            for (var s : level.getEntitiesOfClass(net.minecraft.world.entity.monster.spider.CaveSpider.class, p.getBoundingBox().inflate(60)))
                log("bosses:   spider {} at {} brood of {} (queen {}) child {} age {}", s.getId(), s.blockPosition(),
                        s.getPersistentData().getIntOr(com.jo112ss.rpgmod.mob.boss.SpiderQueen.BROOD_OF, -1), queen[0].getId(),
                        s.getPersistentData().getBooleanOr(com.jo112ss.rpgmod.mob.boss.BossEvents.CHILD, false), s.tickCount);
        });
        SkillTasks.later(1140, () -> {
            for (var m : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, p.getBoundingBox().inflate(60))) m.discard();
            p.removeAllEffects();
            p.teleportTo(home.x, home.y, home.z);
            p.setGameMode(mode);
            done[0] = true;
        });
    }

    /** One dungeon in every style, a different room of each in the picture (moving slot to slot, never leaving the dimension). */
    private static void dungeonGallery(ServerLevel level, ServerPlayer p) {
        var server = level.getServer();
        var dl = com.jo112ss.rpgmod.dungeon.Dungeons.level(server);
        if (dl == null) return;
        Vec3 home = p.position();
        int n = 13, step = 60;
        com.jo112ss.rpgmod.dungeon.DungeonData.Run[] runs = new com.jo112ss.rpgmod.dungeon.DungeonData.Run[n];
        for (int i = 0; i < n; i++) {
            int k = i;
            SkillTasks.later(20 + k * step, () -> {
                var d = com.jo112ss.rpgmod.dungeon.DungeonData.get(server);
                runs[k] = d.open(level.dimension(), new net.minecraft.core.BlockPos(900000 + k, 0, 0), com.jo112ss.rpgmod.dungeon.Dungeons.Kind.RUN, 0, 1000L + k * 7919L);
                runs[k].room = 99; // a picture, not a fight
                com.jo112ss.rpgmod.dungeon.DungeonBuilder.forceTheme = k;
                long t0 = System.nanoTime();
                var steps = com.jo112ss.rpgmod.dungeon.DungeonBuilder.steps(dl, runs[k]);
                long worst = 0;
                for (Runnable r : steps) {
                    long s0 = System.nanoTime();
                    r.run();
                    worst = Math.max(worst, System.nanoTime() - s0);
                }
                com.jo112ss.rpgmod.dungeon.DungeonBuilder.forceTheme = null;
                int roomIdx = 1 + k % 4;
                var room = runs[k].rooms().get(roomIdx);
                log("gallery {}: built in {} ms (longest step {} ms) - {} (showing room {})", k, (System.nanoTime() - t0) / 1_000_000, worst / 1_000_000,
                        com.jo112ss.rpgmod.dungeon.DungeonBuilder.lastDescription, roomIdx);
                p.teleportTo(dl, com.jo112ss.rpgmod.dungeon.Dungeons.originX(runs[k].slot) + 0.5, com.jo112ss.rpgmod.dungeon.Dungeons.FLOOR, room.z0() + 1.5,
                        java.util.Set.of(), 0, 15, true);
                if (k > 0) com.jo112ss.rpgmod.dungeon.DungeonManager.abort(server, runs[k - 1]);
            });
        }
        SkillTasks.later(20 + n * step, () -> {
            p.teleportTo(level, home.x, home.y, home.z, java.util.Set.of(), p.getYRot(), 0, true);
            com.jo112ss.rpgmod.dungeon.DungeonManager.abort(server, runs[n - 1]);
        });
    }

    /** A whole dungeon run: offer a key, go in, fight the waves of each room, leave and come back, the boss, the way home; then a boss arena. */
    private static void dungeon(ServerLevel level, ServerPlayer p) {
        com.jo112ss.rpgmod.dungeon.DungeonManager.testLog = true;
        com.jo112ss.rpgmod.dungeon.DungeonManager.forceBoss = true;
        com.jo112ss.rpgmod.dungeon.DungeonManager.waveDelayTicks = 10;
        var server = level.getServer();
        var dl = com.jo112ss.rpgmod.dungeon.Dungeons.level(server);
        log("dungeon: dimension {}", dl != null ? dl.dimension().identifier() : "MISSING");
        if (dl == null) return;
        var mode = p.gameMode();
        Vec3 home = p.position();
        Vec3 face = Vec3.directionFromRotation(0, p.getYRot());
        net.minecraft.core.BlockPos altar = net.minecraft.core.BlockPos.containing(home.add(face.scale(3))).atY((int) Math.floor(home.y));
        level.setBlockAndUpdate(altar, com.jo112ss.rpgmod.registry.ModRegistries.DUNGEON_ALTAR.get().defaultBlockState());
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        p.getAbilities().invulnerable = false;
        p.onUpdateAbilities();
        boolean[] done = {false};
        SkillTasks.run(age -> {
            p.setHealth(p.getMaxHealth());
            p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.RESISTANCE, 20, 4, false, false));
            return done[0] || age > 2400;
        });
        int[] slot = {-1};
        java.util.function.Supplier<com.jo112ss.rpgmod.dungeon.DungeonData.Run> run = () -> com.jo112ss.rpgmod.dungeon.DungeonData.get(server).bySlot(slot[0]);
        java.util.function.IntConsumer into = i -> { // stand a few blocks into room i
            var room = com.jo112ss.rpgmod.dungeon.Dungeons.layout(run.get().kind).get(i);
            int ox = com.jo112ss.rpgmod.dungeon.Dungeons.originX(slot[0]);
            p.teleportTo(dl, ox + 0.5, com.jo112ss.rpgmod.dungeon.Dungeons.FLOOR, room.z0() + 2.5, java.util.Set.of(), 0, 10, true);
        };
        // the steps run one after the other; each says when it is done
        List<java.util.function.IntPredicate> steps = new java.util.ArrayList<>();
        java.util.function.IntFunction<java.util.function.IntPredicate> wait = n -> a -> a >= n;
        java.util.function.Function<Runnable, java.util.function.IntPredicate> act = r -> a -> {
            r.run();
            return true;
        };
        // fight a room through: strike whatever is alive every few ticks until the room is won
        java.util.function.IntFunction<java.util.function.IntPredicate> fight = i -> a -> {
            var r = run.get();
            if (r == null || r.room > i || r.closing >= 0) {
                log("dungeon: room {} won after {} ticks", i, a);
                return true;
            }
            if (a % 5 == 0) {
                for (var u : List.copyOf(r.mobs)) {
                    if (!(dl.getEntity(u) instanceof LivingEntity e) || !e.isAlive()) continue;
                    if (a % 40 == 0) log("dungeon: wave {} monster {} Lv{} health {} / {} elite {} target {}", r.wave + 1, e.getType().getDescriptionId(),
                            com.jo112ss.rpgmod.mob.MobLevels.level(e), String.format("%.0f", e.getHealth()), String.format("%.0f", e.getMaxHealth()),
                            com.jo112ss.rpgmod.mob.Elite.traits(e), e instanceof net.minecraft.world.entity.Mob m && m.getTarget() != null ? "player" : "-");
                    if (a % 40 == 0) continue; // the log round lets the picture show them first
                    e.hurtServer(dl, p.damageSources().playerAttack(p), 1e7F);
                    if (e.isAlive() && e instanceof com.jo112ss.rpgmod.mob.boss.ModBoss) e.discard(); // bosses cap what they take
                }
            }
            if (a > 900) {
                log("dungeon: room {} NOT won", i);
                return true;
            }
            return false;
        };
        steps.add(wait.apply(10));
        steps.add(act.apply(() -> {
            ItemStack key = new ItemStack(com.jo112ss.rpgmod.registry.ModRegistries.DUNGEON_KEY.get(), 2);
            var res = com.jo112ss.rpgmod.dungeon.DungeonManager.offer(level, altar, p, key);
            var r = com.jo112ss.rpgmod.dungeon.DungeonData.get(server).byAltar(level.dimension(), altar);
            slot[0] = r != null ? r.slot : -1;
            log("dungeon: offered a key -> {} ({} keys left), slot {}, altar lit {}", res, key.getCount(), slot[0],
                    level.getBlockState(altar).getValue(com.jo112ss.rpgmod.dungeon.AltarBlock.ACTIVE));
        }));
        steps.add(wait.apply(10));
        steps.add(act.apply(() -> {
            com.jo112ss.rpgmod.dungeon.DungeonManager.enter(level, altar, p);
            log("dungeon: entered - in {} at {}", p.level().dimension().identifier(), p.blockPosition());
        }));
        steps.add(wait.apply(20));
        steps.add(act.apply(() -> into.accept(1)));
        net.minecraft.world.entity.monster.spider.Spider[] spider = {null};
        steps.add(wait.apply(5));
        steps.add(act.apply(() -> { // a spider in the lit room: it must still come for the player
            var s = net.minecraft.world.entity.EntityTypes.SPIDER.create(dl, net.minecraft.world.entity.EntitySpawnReason.EVENT);
            s.snapTo(p.getX() + 6, p.getY(), p.getZ() + 6, 0, 0);
            s.setPersistenceRequired();
            dl.addFreshEntity(s);
            spider[0] = s;
            run.get().mobs.add(s.getUUID());
            log("dungeon: spider placed, light {} (spiders leave you alone above 0.5)", String.format("%.2f", s.getLightLevelDependentMagicValue()));
        }));
        steps.add(wait.apply(30)); // the first wave in the picture
        steps.add(act.apply(() -> log("dungeon: spider target after 1.5 s: {}, distance {}", spider[0].getTarget() != null ? "player" : "none",
                String.format("%.1f", spider[0].distanceTo(p)))));
        steps.add(fight.apply(1));
        steps.add(act.apply(() -> into.accept(2)));
        steps.add(fight.apply(2));
        // leave by the exit circle, then come back in (joining a run in progress)
        steps.add(act.apply(() -> p.teleportTo(dl, com.jo112ss.rpgmod.dungeon.Dungeons.originX(slot[0]) + 0.5, com.jo112ss.rpgmod.dungeon.Dungeons.FLOOR, 1.5,
                java.util.Set.of(), 180, 0, true)));
        steps.add(wait.apply(40));
        steps.add(act.apply(() -> log("dungeon: after the exit circle - in {} at {} (altar {})", p.level().dimension().identifier(), p.blockPosition(), altar)));
        steps.add(act.apply(() -> com.jo112ss.rpgmod.dungeon.DungeonManager.enter(level, altar, p)));
        steps.add(wait.apply(10));
        steps.add(act.apply(() -> {
            log("dungeon: back in - next room {}", run.get().room);
            into.accept(3);
        }));
        steps.add(fight.apply(3));
        steps.add(act.apply(() -> into.accept(4)));
        steps.add(wait.apply(30)); // the last room in the picture
        steps.add(fight.apply(4));
        steps.add(act.apply(() -> {
            var r = run.get();
            log("dungeon: after the last room - closing {}", r != null ? r.closing : "gone");
            if (r != null && r.closing > 40) r.closing = 40; // the test does not wait the full 30 s
        }));
        steps.add(wait.apply(60));
        steps.add(act.apply(() -> {
            log("dungeon: sent back - in {} at {}, run {}, altar lit {}", p.level().dimension().identifier(), p.blockPosition(), run.get() == null ? "closed" : "open",
                    level.getBlockState(altar).getValue(com.jo112ss.rpgmod.dungeon.AltarBlock.ACTIVE));
            var spawnEvent = new net.neoforged.neoforge.event.entity.player.PlayerSetSpawnEvent(p, new ServerPlayer.RespawnConfig(
                    net.minecraft.world.level.storage.LevelData.RespawnData.of(com.jo112ss.rpgmod.dungeon.Dungeons.DIM, net.minecraft.core.BlockPos.ZERO, 0, 0), true));
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(spawnEvent);
            log("dungeon: setting a spawn point inside is refused: {}", spawnEvent.isCanceled());
        }));
        // a boss offering: the golem's arena, straight to the fight
        steps.add(act.apply(() -> {
            ItemStack core = new ItemStack(com.jo112ss.rpgmod.registry.ModRegistries.GOLEM_CORE.get());
            com.jo112ss.rpgmod.dungeon.DungeonManager.offer(level, altar, p, core);
            var r = com.jo112ss.rpgmod.dungeon.DungeonData.get(server).byAltar(level.dimension(), altar);
            slot[0] = r != null ? r.slot : -1;
            log("dungeon: offered a golem core -> {} rooms, slot {}", r != null ? r.rooms().size() : 0, slot[0]);
            com.jo112ss.rpgmod.dungeon.DungeonManager.enter(level, altar, p);
        }));
        steps.add(wait.apply(20));
        steps.add(act.apply(() -> into.accept(1)));
        steps.add(wait.apply(40)); // the golem in the picture
        steps.add(fight.apply(1));
        steps.add(act.apply(() -> {
            var r = run.get();
            log("dungeon: arena won - closing {}", r != null ? r.closing : "gone");
            if (r != null && r.closing > 20) r.closing = 20;
        }));
        steps.add(wait.apply(40));
        steps.add(act.apply(() -> {
            log("dungeon: after the arena - in {}, run {}", p.level().dimension().identifier(), run.get() == null ? "closed" : "open");
            level.removeBlock(altar, false);
            p.teleportTo(level, home.x, home.y, home.z, java.util.Set.of(), p.getYRot(), 0, true);
            p.setGameMode(mode);
            com.jo112ss.rpgmod.dungeon.DungeonManager.testLog = false;
            com.jo112ss.rpgmod.dungeon.DungeonManager.forceBoss = null;
            com.jo112ss.rpgmod.dungeon.DungeonManager.waveDelayTicks = 60;
            done[0] = true;
        }));
        int[] idx = {0}, start = {0};
        SkillTasks.run(age -> {
            if (idx[0] >= steps.size()) return true;
            if (steps.get(idx[0]).test(age - start[0])) {
                idx[0]++;
                start[0] = age + 1;
                if (idx[0] < steps.size()) log("dungeon: step {} at tick {}", idx[0], age + 1);
            }
            return age > 2400;
        });
    }

    /** Monsters in full gear: worn (armour and attack count) but not drawn. */
    private static void hiddenGear(ServerLevel level, ServerPlayer p) {
        Vec3 home = p.position();
        Vec3 field = home.add(-120, 0, 0);
        p.teleportTo(field.x, field.y, field.z);
        SkillTasks.later(40, () -> {
            for (var m : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, p.getBoundingBox().inflate(40))) m.discard();
            Vec3 face = Vec3.directionFromRotation(0, p.getYRot());
            Vec3 side = new Vec3(-face.z, 0, face.x);
            java.util.List<net.minecraft.world.entity.Mob> mobs = new java.util.ArrayList<>();
            net.minecraft.world.entity.EntityType<?>[] types = {net.minecraft.world.entity.EntityTypes.ZOMBIE, net.minecraft.world.entity.EntityTypes.SKELETON,
                    net.minecraft.world.entity.EntityTypes.PIGLIN_BRUTE, net.minecraft.world.entity.EntityTypes.HUSK};
            com.jo112ss.rpgmod.mob.Elite.forceNext = 0;
            for (int i = 0; i < types.length; i++) {
                var m = (net.minecraft.world.entity.Mob) types[i].create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                Vec3 at = field.add(face.scale(5)).add(side.scale((i - 2) * 1.6));
                m.snapTo(at.x, at.y, at.z, p.getYRot() + 180, 0);
                m.setNoAi(true);
                level.addFreshEntity(m);
                m.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(net.minecraft.world.item.Items.DIAMOND_HELMET));
                m.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new ItemStack(net.minecraft.world.item.Items.DIAMOND_CHESTPLATE));
                m.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, new ItemStack(net.minecraft.world.item.Items.IRON_LEGGINGS));
                m.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, new ItemStack(net.minecraft.world.item.Items.GOLDEN_BOOTS));
                m.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(i == 1 ? ModItems.get(com.jo112ss.rpgmod.item.WeaponType.BOW, WeaponMaterial.DIAMOND)
                        : ModItems.get(i == 3 ? com.jo112ss.rpgmod.item.WeaponType.HAMMER : com.jo112ss.rpgmod.item.WeaponType.SWORD, WeaponMaterial.DIAMOND)));
                mobs.add(m);
            }
            var party = RevenantSpawns.spawnGroup(level, field.add(face.scale(5)).add(side.scale(2 * 1.6)), List.of(Revenant.Role.WARRIOR));
            for (var r : party) r.setNoAi(true);
            com.jo112ss.rpgmod.mob.Elite.forceNext = null;
            SkillTasks.later(10, () -> {
                for (var m : mobs) log("gear: {} armour {} holds {} (head {})", m.getType().getDescriptionId(), m.getArmorValue(), m.getMainHandItem().getItem(),
                        m.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).getItem());
                for (var r : party) log("gear: revenant {} holds {}", r.role().id(), r.getMainHandItem().getItem());
            });
            SkillTasks.later(100, () -> {
                for (var m : mobs) m.discard();
                for (var r : party) r.discard();
                p.teleportTo(home.x, home.y, home.z);
            });
        });
    }

    /** The fallen adventurers: who comes together, their health and attack, each job's move, a cave spawn. */
    private static void revenants(ServerLevel level, ServerPlayer p) {
        Revenant.testLog = true;
        var rnd = net.minecraft.util.RandomSource.create(42);
        int[] sizes = new int[5];
        int lonePriest = 0, n = 20000;
        java.util.Map<Revenant.Role, Integer> alone = new java.util.EnumMap<>(Revenant.Role.class);
        for (int i = 0; i < n; i++) {
            var roles = RevenantSpawns.roll(rnd);
            sizes[roles.size()]++;
            if (roles.size() == 1) alone.merge(roles.get(0), 1, Integer::sum);
            if (roles.size() == 1 && roles.get(0) == Revenant.Role.PRIEST) lonePriest++;
        }
        log("revenants: groups of {} - alone {}% pair {}% party of four {}%, priests alone {}, alone by job {}", n, sizes[1] * 100 / n, sizes[2] * 100 / n, sizes[4] * 100 / n,
                lonePriest, alone);
        var mode = p.gameMode();
        Vec3 home = p.position();
        // a clean field away from the training dummies and what earlier scenes left behind
        Vec3 field = home.add(120, 0, 0);
        p.teleportTo(field.x, field.y, field.z);
        // the field's chunks load first: what is added to an unloaded chunk only joins the world later
        SkillTasks.later(40, () -> revenantField(level, p, mode, home, field));
    }

    private static void revenantField(ServerLevel level, ServerPlayer p, net.minecraft.world.level.GameType mode, Vec3 home, Vec3 field) {
        for (var m : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, p.getBoundingBox().inflate(40))) m.discard();
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        p.getAbilities().invulnerable = false; // they only hunt players they can hurt...
        p.onUpdateAbilities();
        SkillTasks.run(age -> { // ...so keep the tester standing
            p.setHealth(p.getMaxHealth());
            p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.RESISTANCE, 20, 4, false, false));
            return age >= 300;
        });
        Vec3 face = Vec3.directionFromRotation(0, p.getYRot());
        Vec3 side = new Vec3(-face.z, 0, face.x);
        Vec3 at = p.position().add(face.scale(12));
        com.jo112ss.rpgmod.mob.Elite.forceNext = 0;
        var party = RevenantSpawns.spawnGroup(level, at, List.of(Revenant.Role.WARRIOR, Revenant.Role.MAGE, Revenant.Role.ARCHER, Revenant.Role.PRIEST));
        var thief = RevenantSpawns.spawnGroup(level, p.position().add(face.scale(7)).add(side.scale(5)), List.of(Revenant.Role.THIEF)).get(0);
        var zombie = net.minecraft.world.entity.EntityTypes.ZOMBIE.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        zombie.snapTo(at.x + side.x * 30, at.y, at.z + side.z * 30, 0, 0);
        zombie.setNoAi(true);
        level.addFreshEntity(zombie);
        com.jo112ss.rpgmod.mob.Elite.forceNext = null;
        var all = new java.util.ArrayList<Revenant>(party);
        all.add(thief);
        for (Revenant r : all) {
            r.readyIn(r.role() == Revenant.Role.PRIEST ? 40 : 12);
            r.setTarget(p);
        }
        SkillTasks.later(15, () -> { // equipment attributes settle a tick after arming
            com.jo112ss.rpgmod.mob.MobLevels.apply(zombie, com.jo112ss.rpgmod.mob.MobLevels.level(party.get(0)));
            for (var slot : net.minecraft.world.entity.EquipmentSlot.values()) zombie.setItemSlot(slot, ItemStack.EMPTY);
            com.jo112ss.rpgmod.mob.MobGear.refreshWeapon(zombie);
            for (Revenant r : all) log("revenants: {} lv {} health {} attack {} weapon {} speed {}", r.role().id(), com.jo112ss.rpgmod.mob.MobLevels.level(r),
                    String.format("%.0f", r.getMaxHealth()), String.format("%.1f", r.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)),
                    r.getMainHandItem().getItem(), String.format("%.3f", r.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)));
            log("revenants: a bare zombie at the same level - health {} attack {} speed {}", String.format("%.0f", zombie.getMaxHealth()),
                    String.format("%.1f", zombie.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)),
                    String.format("%.3f", zombie.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)));
        });
        // the priest heals the hurt warrior
        SkillTasks.later(50, () -> {
            var w = party.get(0);
            float before = w.getHealth();
            boolean hit = w.hurtServer(level, level.damageSources().generic(), w.getMaxHealth() * 0.6F);
            log("revenants: warrior hurt {} {} -> {}", hit, String.format("%.1f", before), String.format("%.1f", w.getHealth()));
        });
        SkillTasks.later(150, () -> log("revenants: warrior health now {} / {}", String.format("%.1f", party.get(0).getHealth()), String.format("%.0f", party.get(0).getMaxHealth())));
        // a cave spawn: a zombie about to appear under a roof below y 40 comes as revenants (the roof first, so the sky light updates)
        net.minecraft.core.BlockPos base = net.minecraft.core.BlockPos.containing(field.subtract(face.scale(14)));
        SkillTasks.later(140, () -> {
            for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++)
                level.setBlockAndUpdate(base.offset(dx, 4, dz), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        });
        SkillTasks.later(200, () -> {
            var z = net.minecraft.world.entity.EntityTypes.ZOMBIE.create(level, net.minecraft.world.entity.EntitySpawnReason.NATURAL);
            z.snapTo(base.getX() + 0.5, base.getY(), base.getZ() + 0.5, 0, 0);
            RevenantSpawns.forceChance = 1.0;
            var ev = new net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent(z, level, z.getX(), z.getY(), z.getZ(), level.getCurrentDifficultyAt(base),
                    net.minecraft.world.entity.EntitySpawnReason.NATURAL, null, null);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(ev);
            RevenantSpawns.forceChance = null;
            int before = level.getEntitiesOfClass(Revenant.class, p.getBoundingBox().inflate(40)).size();
            log("revenants: cave spawn at y {} (sky {}) - zombie cancelled {}", base.getY(), level.canSeeSky(base), ev.isSpawnCancelled());
            SkillTasks.later(3, () -> log("revenants: cave spawn -> revenants {} before, {} after", before,
                    level.getEntitiesOfClass(Revenant.class, p.getBoundingBox().inflate(40)).size()));
        });
        SkillTasks.later(300, () -> {
            for (var r : level.getEntitiesOfClass(Revenant.class, p.getBoundingBox().inflate(60))) r.discard();
            zombie.discard();
            for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++)
                level.setBlockAndUpdate(base.offset(dx, 4, dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            p.teleportTo(home.x, home.y, home.z);
            p.setGameMode(mode);
            p.getAbilities().invulnerable = false;
            p.onUpdateAbilities();
            Revenant.testLog = false;
        });
    }

    /** The twelve reworked ultimates, one after the other (UltSkills logs what each does). */
    private static void ults(ServerLevel level, ServerPlayer p, List<? extends LivingEntity> dummies) {
        LivingEntity t = front(dummies);
        if (t == null) return;
        JobData d = JobManager.data(p);
        var mode = p.gameMode();
        boolean inv = p.getAbilities().invulnerable;
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        p.getAbilities().invulnerable = false;
        com.jo112ss.rpgmod.job.UltSkills.testLog = true;
        java.util.function.Consumer<String> cast = id -> {
            d.setMana(JobManager.maxMana(p));
            p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, t.position().add(0, t.getBbHeight() * 0.5, 0));
            boolean ok = SkillExecutor.execute(p, JobDefs.skill(id), JobManager.powerMultiplier(p, JobDefs.skill(id)));
            log("ult cast {}: {}", id, ok);
        };
        java.util.function.Supplier<String> hp = () -> String.format("%.1f", p.getHealth());
        // fighter: war god, two of the five shockwave swings
        SkillTasks.later(0, () -> become(p, "fighter"));
        SkillTasks.later(30, () -> cast.accept("berserk"));
        SkillTasks.later(31, () -> p.attack(t));
        SkillTasks.later(50, () -> {
            p.attack(t);
            var e = p.getEffect(ModEffects.WAR_GOD);
            log("ult war god charges left {}", e == null ? 0 : e.getAmplifier() + 1);
        });
        // assassin: the shadows copy a hit
        SkillTasks.later(60, () -> become(p, "assassin"));
        SkillTasks.later(90, () -> cast.accept("shadow_dance"));
        SkillTasks.later(91, () -> p.attack(t));
        // paladin: untouchable
        SkillTasks.later(110, () -> become(p, "paladin"));
        SkillTasks.later(140, () -> cast.accept("divine_aegis"));
        SkillTasks.later(141, () -> {
            String a = hp.get();
            p.hurtServer(level, level.damageSources().mobAttack(t), 20);
            log("ult divine aegis: a 20 blow {} -> {}", a, hp.get());
        });
        // healer: half of a blow comes back over 3s
        SkillTasks.later(150, () -> become(p, "healer"));
        SkillTasks.later(180, () -> cast.accept("guardian_light"));
        SkillTasks.later(181, () -> {
            p.removeEffect(ModEffects.HOLY_AEGIS);
            p.setHealth(p.getMaxHealth());
            p.hurtServer(level, level.damageSources().mobAttack(t), 20);
            log("ult rewind: after the blow {}", hp.get());
        });
        SkillTasks.later(245, () -> log("ult rewind: 3s later {} (max {})", hp.get(), String.format("%.1f", p.getMaxHealth())));
        // sentinel: the fortress keeps 30% off and hits back at the end
        SkillTasks.later(250, () -> become(p, "sentinel", true));
        SkillTasks.later(280, () -> cast.accept("fortress"));
        SkillTasks.later(282, () -> {
            p.setHealth(p.getMaxHealth());
            String a = hp.get();
            p.hurtServer(level, level.damageSources().mobAttack(t), 20);
            log("ult fortress: a 20 blow {} -> {}", a, hp.get());
        });
        // god hand: hits from the flowers heal; they burst at the end
        SkillTasks.later(300, () -> become(p, "qi_master"));
        SkillTasks.later(330, () -> {
            p.setHealth(p.getMaxHealth() * 0.5F);
            cast.accept("blooming_fists");
        });
        SkillTasks.later(336, () -> p.attack(t));
        // wizard: time stop, a hit while stopped lands at the end
        SkillTasks.later(500, () -> become(p, "wizard"));
        SkillTasks.later(530, () -> cast.accept("time_warp"));
        SkillTasks.later(533, () -> {
            float a = t.getHealth();
            p.attack(t);
            log("ult time stop: hit while stopped took {} now (noAI {})", String.format("%.1f", a - t.getHealth()), t instanceof net.minecraft.world.entity.Mob m && m.isNoAi());
        });
        // spellmaster: the beam
        SkillTasks.later(610, () -> become(p, "wizard", true));
        SkillTasks.later(640, () -> cast.accept("armageddon"));
        // lancer: spinning charge
        SkillTasks.later(720, () -> become(p, "lancer"));
        SkillTasks.later(750, () -> cast.accept("dragon_fang"));
        // maestro: symphony (a perfect note)
        SkillTasks.later(830, () -> songs(level, p, "bard", true, List.of("war_song", "heal_song", "", "", "guard_song", "rhapsody"), 0, 5));
        SkillTasks.later(870, () -> {
            cast.accept("symphony");
            log("ult symphony effect {} performing {}", p.hasEffect(ModEffects.SYMPHONY), com.jo112ss.rpgmod.job.Bard.performing(p));
            com.jo112ss.rpgmod.job.UltSkills.symphonyNoteForTest(p);
        });
        // spellsinger: every afterglow at once, then the sealing notes
        SkillTasks.later(900, () -> songs(level, p, "spellsinger", false, List.of("ward_melody", "drain_tune", "flame_melody"), 0, -1));
        SkillTasks.later(940, () -> {
            cast.accept("grand_concerto");
            var fl = p.getEffect(ModEffects.FLAME_SONG);
            var dr = p.getEffect(ModEffects.DRAIN_TUNE);
            log("ult grand concerto: flame {}t drain {}t absorption {}", fl == null ? 0 : fl.getDuration(), dr == null ? 0 : dr.getDuration(),
                    String.format("%.1f", p.getAbsorptionAmount()));
        });
        SkillTasks.later(960, () -> cast.accept("sealing_chord"));
        SkillTasks.later(1000, () -> log("ult sealing chord: front dummy silenced {}", t.hasEffect(ModEffects.SILENCED)));
        SkillTasks.later(1010, () -> {
            p.setGameMode(mode);
            p.getAbilities().invulnerable = inv;
            p.onUpdateAbilities();
            p.setHealth(p.getMaxHealth());
            com.jo112ss.rpgmod.job.UltSkills.testLog = false;
        });
    }

    /** Counter lance: blows taken while braced are cut and stored, and the next hit carries the store. */
    private static void counterLance(ServerLevel level, ServerPlayer p, List<? extends LivingEntity> dummies) {
        become(p, "sentinel", true);
        if (dummies.size() < 5) return;
        LivingEntity near = dummies.get(4);
        var mode = p.gameMode();
        boolean inv = p.getAbilities().invulnerable;
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        p.getAbilities().invulnerable = false;
        float[] plain = {0};
        SkillTasks.later(25, () -> {
            float a = near.getHealth();
            p.attack(near);
            plain[0] = a - near.getHealth();
            float hp = p.getHealth();
            p.hurtServer(level, level.damageSources().mobAttack(near), 20);
            log("counter lance: plain sword-and-board hit {}, a 20 blow plain took {}", String.format("%.1f", plain[0]), String.format("%.1f", hp - p.getHealth()));
            p.setHealth(p.getMaxHealth());
        });
        SkillTasks.later(50, () -> SkillExecutor.execute(p, JobDefs.skill("counter_lance"), JobManager.powerMultiplier(p, JobDefs.skill("counter_lance"))));
        for (int k = 0; k < 2; k++) {
            int kk = k;
            SkillTasks.later(52 + k * 24, () -> {
                float hp = p.getHealth();
                p.hurtServer(level, level.damageSources().mobAttack(near), 20);
                log("counter lance: braced blow {} took {}, stored {}", kk + 1, String.format("%.1f", hp - p.getHealth()),
                        String.format("%.1f", com.jo112ss.rpgmod.job.MoreJobSkills.lanceStoreForTest(p)));
            });
        }
        SkillTasks.later(100, () -> {
            float a = near.getHealth();
            p.attack(near);
            log("counter lance: the next hit {} (plain {}), store left {}", String.format("%.1f", a - near.getHealth()), String.format("%.1f", plain[0]),
                    String.format("%.1f", com.jo112ss.rpgmod.job.MoreJobSkills.lanceStoreForTest(p)));
            p.setGameMode(mode);
            p.getAbilities().invulnerable = inv;
            p.setHealth(p.getMaxHealth());
        });
    }

    /**
     * Songs on the lute: the job with every cell learnt, songs set on the held lute, then "perform"
     * equipped and used like a player would (mana paid), and what is playing logged.
     */
    private static void songs(ServerLevel level, ServerPlayer p, String job, boolean adv, List<String> set, int selected, int selectedLeft) {
        become(p, job, adv);
        JobData d = JobManager.data(p);
        d.devUnlockAll();
        var lute = p.getMainHandItem();
        var l = com.jo112ss.rpgmod.job.SpellLoadout.EMPTY;
        for (int i = 0; i < set.size(); i++) l = l.withSpell(i, set.get(i));
        l = l.withSelected(selected);
        if (selectedLeft >= 0) l = l.withSelectedLeft(selectedLeft);
        lute.set(com.jo112ss.rpgmod.registry.ModRegistries.SPELL_LOADOUT.get(), l);
        d.setMana(JobManager.maxMana(p));
        String perform = "spellsinger".equals(job) ? "spell_perform" : "perform";
        JobManager.equipSkill(p, 0, perform);
        // the fanfare test: a skill cooling down
        d.setCooldown("sonic_boom", level.getGameTime() + 200);
        JobManager.sync(p);
        double before = d.mana();
        log("songs {}: slots {} lute {} cost {}", job + (adv ? " (maestro)" : ""), d.spellSlots(), lute.getItem(),
                String.format("%.0f", com.jo112ss.rpgmod.job.Bard.performCost(p)));
        SkillTasks.later(3, () -> {
            JobManager.useSkill(p, 0);
            log("songs {}: playing {} + {} (mana {} -> {}), performing {}", job, d.song(), com.jo112ss.rpgmod.job.Bard.secondSong(p),
                    String.format("%.0f", before), String.format("%.0f", d.mana()), com.jo112ss.rpgmod.job.Bard.performing(p));
        });
        SkillTasks.later(63, () -> log("songs {}: after 3s effects {} ward {} sonic boom cooldown left {}t (200 - 60 plain)", job,
                p.getActiveEffects().stream().map(e -> e.getEffect().getRegisteredName()).toList(),
                String.format("%.1f", p.getAbsorptionAmount()), d.cooldownEnd("sonic_boom") - level.getGameTime()));
    }

    /** Ward melody: absorption on the health bar, not topped up when hit, gone for a while once broken. */
    private static void ward(ServerLevel level, ServerPlayer p, List<? extends LivingEntity> dummies) {
        become(p, "spellsinger");
        if (dummies.isEmpty()) return;
        LivingEntity mob = dummies.get(0);
        com.jo112ss.rpgmod.job.Bard.forgetWard(p.getUUID()); // (the arena reset took the last test's absorption away)
        var mode = p.gameMode();
        boolean inv = p.getAbilities().invulnerable;
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        p.getAbilities().invulnerable = false;
        SkillExecutor.execute(p, JobDefs.skill("ward_melody"), JobManager.powerMultiplier(p, JobDefs.skill("ward_melody")));
        JobManager.data(p).setSongPoints(10);
        SkillTasks.later(45, () -> {
            float a0 = p.getAbsorptionAmount();
            p.hurtServer(level, level.damageSources().mobAttack(mob), 3);
            log("ward: absorption {} (max hp {}), after a small blow {} health {}", String.format("%.1f", a0), String.format("%.0f", p.getMaxHealth()),
                    String.format("%.1f", p.getAbsorptionAmount()), String.format("%.1f", p.getHealth()));
        });
        SkillTasks.later(70, () -> log("ward: a second later {} (not topped up)", String.format("%.1f", p.getAbsorptionAmount())));
        SkillTasks.later(72, () -> p.hurtServer(level, level.damageSources().mobAttack(mob), 30));
        SkillTasks.later(100, () -> log("ward: broken -> absorption {} effect {}", String.format("%.1f", p.getAbsorptionAmount()), p.hasEffect(net.minecraft.world.effect.MobEffects.ABSORPTION)));
        SkillTasks.later(190, () -> {
            log("ward: later -> absorption {} effect {}", String.format("%.1f", p.getAbsorptionAmount()), p.hasEffect(net.minecraft.world.effect.MobEffects.ABSORPTION));
            p.setGameMode(mode);
            p.getAbilities().invulnerable = inv;
            p.setHealth(p.getMaxHealth());
        });
    }

    /** Spellsinger's afterglow: a song ending at 15 points leaves its effect on for 7.5s; a bard's does not. */
    private static void linger(ServerLevel level, ServerPlayer p) {
        for (String job : new String[] {"spellsinger", "bard"}) {
            int delay = job.equals("bard") ? 60 : 0;
            SkillTasks.later(delay, () -> {
                become(p, job);
                JobData d = JobManager.data(p);
                String song = job.equals("bard") ? "war_song" : "flame_melody";
                SkillExecutor.execute(p, JobDefs.skill(song), JobManager.powerMultiplier(p, JobDefs.skill(song)));
                d.setSongPoints(15); // under the encore
                long now = level.getGameTime();
                d.setSong(d.song(), d.songStart(), now + 25); // ends shortly
                SkillTasks.later(45, () -> {
                    var eff = p.getEffect(job.equals("bard") ? ModEffects.WAR_SONG : ModEffects.FLAME_SONG);
                    log("linger {}: song over, performing {}, {} left {} ticks", job, com.jo112ss.rpgmod.job.Bard.performing(p), song,
                            eff == null ? 0 : eff.getDuration());
                });
            });
        }
    }

    /** Every job's stats by level (job alone: no panel, no weapon), and what its whole panel holds. */
    private static void statsDump() {
        var S = com.jo112ss.rpgmod.job.core.JobStat.values();
        StringBuilder head = new StringBuilder("stat|job|kind|level");
        for (var s : S) head.append('|').append(s.id);
        log(head.toString());
        for (var job : JobDefs.JOBS.values()) {
            String id = job.id();
            boolean adv = JobDefs.isAdvanced(id), hyb = JobDefs.isHybrid(id);
            var base = adv ? JobDefs.job(JobDefs.BASE_OF.get(id)) : job;
            int[] levels = adv ? new int[] {100, 150, 199} : new int[] {1, 50, 100, 150, 199};
            for (int lv : levels) {
                java.util.Map<com.jo112ss.rpgmod.job.core.JobStat, Double> m = new java.util.EnumMap<>(com.jo112ss.rpgmod.job.core.JobStat.class);
                for (var s : S) m.put(s, 0.0);
                m.put(com.jo112ss.rpgmod.job.core.JobStat.MAX_MANA, JobDefs.BASE_MANA);
                m.put(com.jo112ss.rpgmod.job.core.JobStat.MANA_REGEN, JobDefs.BASE_MANA_REGEN);
                m.merge(com.jo112ss.rpgmod.job.core.JobStat.ATTACK_DAMAGE, JobStats.FIST, Double::sum);
                m.merge(com.jo112ss.rpgmod.job.core.JobStat.MAX_HEALTH, 20.0, Double::sum); // vanilla's own
                base.base().forEach((s, v) -> m.merge(s, v, Double::sum));
                int L = lv;
                base.perLevel().forEach((s, v) -> {
                    double g = v * (L - 1);
                    if (s == com.jo112ss.rpgmod.job.core.JobStat.ATTACK_DAMAGE || s == com.jo112ss.rpgmod.job.core.JobStat.MAGIC_ATTACK) g = Math.floor(g + 1e-9);
                    m.merge(s, g, Double::sum);
                });
                if (adv) job.perLevel().forEach((s, v) -> m.merge(s, v * com.jo112ss.rpgmod.job.core.LevelCurve.advancedLevel(L), Double::sum));
                StringBuilder b = new StringBuilder("stat|" + id + "|" + (adv ? "adv" : hyb ? "hybrid" : "base") + "|" + lv);
                for (var s : S) b.append('|').append(String.format("%.2f", m.get(s)));
                log(b.toString());
            }
            // the whole panel: every stat cell and every cell's cost
            var panel = com.jo112ss.rpgmod.job.core.SkillPanel.forJob(id);
            java.util.Map<com.jo112ss.rpgmod.job.core.JobStat, Double> sum = new java.util.EnumMap<>(com.jo112ss.rpgmod.job.core.JobStat.class);
            int cost = 0, statCost = 0;
            for (var n : panel.nodes()) {
                cost += n.cost();
                if (n.type() == com.jo112ss.rpgmod.job.core.SkillPanel.NodeType.STAT) {
                    sum.merge(n.stat(), n.amount(), Double::sum);
                    statCost += n.cost();
                }
            }
            StringBuilder b = new StringBuilder("panel|" + id + "|cost " + cost + "|stat cells cost " + statCost);
            sum.forEach((s, v) -> b.append('|').append(s.id).append('=').append(String.format("%.1f", v)));
            log(b.toString());
        }
        for (var w : com.jo112ss.rpgmod.item.WeaponType.values()) log("weapon|" + w.name() + "|atk " + w.attackDamage + "|speed " + w.attackSpeed);
        for (int lv : new int[] {1, 50, 100, 150, 199}) log("points|" + lv + "|" + com.jo112ss.rpgmod.job.core.LevelCurve.pointsForLevel(lv));
    }

    /** A stone wall across the arena behind the dummies (forward is +z), for charges to run into. */
    private static void wall(ServerLevel level, ServerPlayer p, boolean put) {
        net.minecraft.core.BlockPos o = p.blockPosition();
        var state = put ? net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState() : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        for (int x = -6; x <= 6; x++) for (int y = 0; y < 3; y++) level.setBlockAndUpdate(o.offset(x, y, 9), state);
        log("wall {} at z+9", put ? "put" : "removed");
    }

    /** Treasure rain: walk over a coin it left and pick it up (gold rush). */
    private static void coins(ServerLevel level, ServerPlayer p) {
        List<com.jo112ss.rpgmod.job.SkillProp> coins = level.getEntitiesOfClass(com.jo112ss.rpgmod.job.SkillProp.class, p.getBoundingBox().inflate(30),
                e -> e.kind() == com.jo112ss.rpgmod.job.SkillProp.COIN);
        log("coins: {} left on the ground", coins.size());
        if (coins.isEmpty()) return;
        net.minecraft.world.phys.Vec3 back = p.position();
        float yaw = p.getYRot(), pitch = p.getXRot();
        var coin = coins.get(0);
        p.teleportTo(level, coin.getX(), coin.getY(), coin.getZ(), java.util.Set.of(), yaw, pitch, false);
        SkillTasks.later(8, () -> {
            var rush = p.getEffect(ModEffects.GOLD_RUSH);
            log("coins: picked up -> gold rush {} (coin gone {}), {} left", rush == null ? "none" : "lv" + (rush.getAmplifier() + 1), coin.isRemoved(),
                    level.getEntitiesOfClass(com.jo112ss.rpgmod.job.SkillProp.class, p.getBoundingBox().inflate(30), e -> e.kind() == com.jo112ss.rpgmod.job.SkillProp.COIN).size());
            p.teleportTo(level, back.x, back.y, back.z, java.util.Set.of(), yaw, pitch, false);
        });
    }

    private static void lancer(ServerLevel level, ServerPlayer p, List<? extends LivingEntity> dummies) {
        become(p, "lancer");
        if (dummies.size() < 5) return;
        LivingEntity far = dummies.get(0), near = dummies.get(4);
        SkillTasks.later(25, () -> {
            float a = far.getHealth();
            p.attack(far);
            log("spear tip: far dummy (edge {}) took {}", String.format("%.1f", p.getEyePosition().distanceTo(far.getBoundingBox().getCenter()) - far.getBbWidth() / 2),
                    String.format("%.1f", a - far.getHealth()));
        });
        SkillTasks.later(50, () -> {
            float a = near.getHealth();
            p.attack(near);
            log("spear tip: near dummy (edge {}) took {}", String.format("%.1f", p.getEyePosition().distanceTo(near.getBoundingBox().getCenter()) - near.getBbWidth() / 2),
                    String.format("%.1f", a - near.getHealth()));
        });
    }

    /**
     * Skill boost cells: every job's panel has them next to its skills; learning one needs the skill;
     * owned, they raise the skill's power, cut its cooldown or mana.
     */
    /**
     * Robes: a full iron set on (armour against the iron metal set), the option pools of metal
     * armour and robes (which options, how often), and forged robe / armour options taking effect.
     */
    private static void robes(ServerLevel level, ServerPlayer p) {
        com.jo112ss.rpgmod.item.WeaponType[] robe = {com.jo112ss.rpgmod.item.WeaponType.ROBE_HOOD, com.jo112ss.rpgmod.item.WeaponType.ROBE,
                com.jo112ss.rpgmod.item.WeaponType.ROBE_PANTS, com.jo112ss.rpgmod.item.WeaponType.ROBE_BOOTS};
        com.jo112ss.rpgmod.item.WeaponType[] metal = {com.jo112ss.rpgmod.item.WeaponType.HELMET, com.jo112ss.rpgmod.item.WeaponType.CHESTPLATE,
                com.jo112ss.rpgmod.item.WeaponType.LEGGINGS, com.jo112ss.rpgmod.item.WeaponType.BOOTS};
        net.minecraft.world.entity.EquipmentSlot[] slots = {net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST,
                net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET};
        ItemStack[] saved = new ItemStack[4];
        for (int i = 0; i < 4; i++) saved[i] = p.getItemBySlot(slots[i]).copy();
        // armour of every material: robe set against metal set (worn gear counts from the next tick)
        StringBuilder sb = new StringBuilder();
        var mats = com.jo112ss.rpgmod.item.WeaponMaterial.values();
        for (int j = 0; j < mats.length; j++) {
            var mat = mats[j];
            int t0 = 1 + j * 4;
            double[] m = new double[2];
            SkillTasks.later(t0, () -> {
                for (int i = 0; i < 4; i++) p.setItemSlot(slots[i], new ItemStack(com.jo112ss.rpgmod.item.ModItems.get(metal[i], mat)));
            });
            SkillTasks.later(t0 + 2, () -> {
                m[0] = com.jo112ss.rpgmod.mob.Defense.armor(p);
                m[1] = com.jo112ss.rpgmod.mob.Defense.toughness(p);
                for (int i = 0; i < 4; i++) p.setItemSlot(slots[i], new ItemStack(com.jo112ss.rpgmod.item.ModItems.get(robe[i], mat)));
            });
            boolean last = j == mats.length - 1;
            SkillTasks.later(t0 + 4, () -> {
                double r = com.jo112ss.rpgmod.mob.Defense.armor(p), rt = com.jo112ss.rpgmod.mob.Defense.toughness(p);
                sb.append(mat.id).append(" metal ").append(Math.round(m[0])).append('/').append(String.format("%.1f", m[1]))
                        .append(" robe ").append(Math.round(r)).append('/').append(String.format("%.1f", rt)).append(" (")
                        .append(Math.round(r / Math.max(1, m[0]) * 100)).append("%); ");
                if (last) log("robes: armour/toughness of a full set (with the job's own): {}", sb);
            });
        }
        StringBuilder pieces = new StringBuilder();
        for (var mat : mats) {
            pieces.append(mat.id).append(' ');
            for (int i = 0; i < 4; i++) pieces.append(com.jo112ss.rpgmod.item.RobeArmor.defense(mat, i)).append(i < 3 ? "/" : "; ");
        }
        log("robes: robe pieces (hood/robe/trousers/shoes): {}", pieces);
        // the option pools
        java.util.Random rr = new java.util.Random(7);
        for (var pool : com.jo112ss.rpgmod.smithing.core.OptionType.Pool.values()) {
            java.util.Map<String, Integer> n = new java.util.TreeMap<>();
            for (int i = 0; i < 2000; i++) {
                for (var o : com.jo112ss.rpgmod.smithing.core.OptionRoller.roll(2, pool, pool == com.jo112ss.rpgmod.smithing.core.OptionType.Pool.WEAPON, false, rr)) {
                    n.merge(o.type().id, 1, Integer::sum);
                }
            }
            log("robes: pool {} (2000 pieces x2 options): {}", pool, n);
        }
        // a forged iron set on: robes with magic power +5% x4, then metal with attack +5% x4
        var job = JobManager.data(p);
        double[] base = new double[5]; // before: magic, attack, health; with the robes: magic, health
        SkillTasks.later(22, () -> {
            for (int i = 0; i < 4; i++) p.setItemSlot(slots[i], ItemStack.EMPTY);
        });
        SkillTasks.later(24, () -> {
        base[0] = JobManager.magicAttack(p);
        base[1] = JobManager.physicalAttack(p);
        base[2] = p.getMaxHealth();
        for (int i = 0; i < 4; i++) {
            ItemStack st = new ItemStack(com.jo112ss.rpgmod.item.ModItems.get(robe[i], com.jo112ss.rpgmod.item.WeaponMaterial.IRON));
            st.set(com.jo112ss.rpgmod.registry.ModRegistries.FORGED_DATA.get(), com.jo112ss.rpgmod.smithing.ForgedData.of(com.jo112ss.rpgmod.smithing.core.Quality.FINE,
                    java.util.List.of(new com.jo112ss.rpgmod.smithing.core.RolledOption(com.jo112ss.rpgmod.smithing.core.OptionType.MAGIC_POWER, 5),
                            new com.jo112ss.rpgmod.smithing.core.RolledOption(com.jo112ss.rpgmod.smithing.core.OptionType.MAX_HEALTH, 3)), "test"));
            p.setItemSlot(slots[i], st);
        }
        });
        SkillTasks.later(26, () -> {
        base[3] = JobManager.magicAttack(p);
        base[4] = p.getMaxHealth();
        for (int i = 0; i < 4; i++) {
            ItemStack st = new ItemStack(com.jo112ss.rpgmod.item.ModItems.get(metal[i], com.jo112ss.rpgmod.item.WeaponMaterial.IRON));
            st.set(com.jo112ss.rpgmod.registry.ModRegistries.FORGED_DATA.get(), com.jo112ss.rpgmod.smithing.ForgedData.of(com.jo112ss.rpgmod.smithing.core.Quality.FINE,
                    java.util.List.of(new com.jo112ss.rpgmod.smithing.core.RolledOption(com.jo112ss.rpgmod.smithing.core.OptionType.ATTACK_DAMAGE, 5),
                            new com.jo112ss.rpgmod.smithing.core.RolledOption(com.jo112ss.rpgmod.smithing.core.OptionType.CRIT_CHANCE, 2)), "test"));
            p.setItemSlot(slots[i], st);
        }
        });
        SkillTasks.later(28, () -> {
        double atk1 = JobManager.physicalAttack(p);
        log("robes: job {} magic attack {} -> robes (+5% x4) {} ({}%), max health {} -> robes (+3% x4) {}; attack {} -> armour (+5% x4) {} ({}%), crit from armour {}%",
                job.displayJobId(), String.format("%.1f", base[0]), String.format("%.1f", base[3]), Math.round((base[3] / base[0] - 1) * 100), String.format("%.1f", base[2]), String.format("%.1f", base[4]),
                String.format("%.1f", base[1]), String.format("%.1f", atk1), Math.round((atk1 / base[1] - 1) * 100),
                com.jo112ss.rpgmod.gem.Gear.total(p, com.jo112ss.rpgmod.smithing.core.OptionType.CRIT_CHANCE, null));
        });
        java.util.List<LivingEntity> stands = new java.util.ArrayList<>();
        SkillTasks.later(30, () -> {
        // the iron robe set on for the picture, each material's set in a row of armour stands
        for (int i = 0; i < 4; i++) p.setItemSlot(slots[i], new ItemStack(com.jo112ss.rpgmod.item.ModItems.get(robe[i], com.jo112ss.rpgmod.item.WeaponMaterial.IRON)));
        Vec3 look = Vec3.directionFromRotation(0, p.getYRot());
        Vec3 side = new Vec3(-look.z, 0, look.x);
        int k = 0;
        for (var mat : com.jo112ss.rpgmod.item.WeaponMaterial.values()) {
            var st = net.minecraft.world.entity.EntityTypes.ARMOR_STAND.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            if (st == null) break;
            Vec3 at = p.position().add(look.scale(-3)).add(side.scale((k - 2) * 1.4)); // behind the player: the camera looks from the front
            st.snapTo(at.x, p.getY(), at.z, p.getYRot(), 0);
            for (int i = 0; i < 4; i++) st.setItemSlot(slots[i], new ItemStack(com.jo112ss.rpgmod.item.ModItems.get(robe[i], mat)));
            level.addFreshEntity(st);
            stands.add(st);
            k++;
        }
        });
        SkillTasks.later(170, () -> {
            for (var st : stands) st.discard();
            for (int i = 0; i < 4; i++) p.setItemSlot(slots[i], saved[i]);
        });
    }

    /**
     * Hit cooldowns per player and kind: one monster hit by this player (plain, then a skill), by a
     * second player (a fake one) in the same tick, again by the same skill (blocked), by a bigger skill
     * hit (only the excess) and after the window (in full).
     */
    /** The abyssal wither: every move in turn, the awakening at half health, the opening, the reward, no block broken. */
    private static void witherTest(ServerLevel level, ServerPlayer p) {
        Vec3 home = p.position();
        Vec3 field = home.add(0, 0, 200);
        p.teleportTo(field.x, field.y, field.z);
        p.setYRot(0);
        p.setXRot(0);
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        p.getAbilities().invulnerable = false;
        p.onUpdateAbilities();
        boolean[] done = {false};
        float[] lost = {0};
        SkillTasks.run(age -> { // keep the tester standing, and count what it takes off
            lost[0] += Math.max(0, p.getMaxHealth() - p.getHealth());
            p.setHealth(p.getMaxHealth());
            p.removeEffect(net.minecraft.world.effect.MobEffects.WITHER);
            p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.RESISTANCE, 20, 3, false, false));
            return done[0] || age > 900;
        });
        int blocksBefore = countBlocks(level, net.minecraft.core.BlockPos.containing(field), 14);
        com.jo112ss.rpgmod.mob.boss.AbyssWither[] w = {null};
        SkillTasks.later(20, () -> {
            for (var m : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, p.getBoundingBox().inflate(40))) m.discard();
            var b = com.jo112ss.rpgmod.registry.ModRegistries.ABYSS_WITHER.get().create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
            b.snapTo(field.x, field.y + 1, field.z + 12, 180, 0);
            b.setPersistenceRequired();
            level.addFreshEntity(b); // its level: always 200
            b.recomputeHealth(true);
            b.setTarget(p);
            w[0] = b;
            log("wither: Lv{} health {} x toughness {} = {} (dragon formula, 1 player), size {}x{}", com.jo112ss.rpgmod.mob.MobLevels.level(b), String.format("%.0f", b.getMaxHealth()),
                    String.format("%.2f", b.toughness()), String.format("%.0f", b.getMaxHealth() * b.toughness()), String.format("%.1f", b.getBbWidth()), String.format("%.1f", b.getBbHeight()));
        });
        java.util.function.Consumer<String> mv = m -> {
            if (w[0] == null) return;
            w[0].testMove(m);
            log("wither: {} -> state {} (at {} y {})", m, w[0].state(), w[0].blockPosition(), String.format("%.1f", w[0].getY() - field.y));
        };
        SkillTasks.later(60, () -> log("wither: chasing, height above the player {} dist {}", String.format("%.1f", w[0].getY() - p.getY()), String.format("%.1f", w[0].distanceTo(p))));
        SkillTasks.later(70, () -> mv.accept("volley"));
        SkillTasks.later(130, () -> mv.accept("rain"));
        SkillTasks.later(200, () -> mv.accept("dive"));
        SkillTasks.later(260, () -> mv.accept("nova"));
        SkillTasks.later(300, () -> mv.accept("summon"));
        SkillTasks.later(345, () -> {
            var b = w[0];
            float before = b.getHealth() / b.getMaxHealth();
            b.hurtServer(level, p.damageSources().playerAttack(p), 1e7F);
            log("wither: gate - a huge hit at {}% leaves {}% (phase {}, state {})", Math.round(before * 100), String.format("%.1f", b.getHealth() / b.getMaxHealth() * 100), b.phase(), b.state());
        });
        SkillTasks.later(400, () -> mv.accept("beams"));
        SkillTasks.later(445, () -> mv.accept("storm"));
        SkillTasks.later(535, () -> {
            mv.accept("spent");
            log("wither: spent opening x{}", w[0].openingMultiplier());
        });
        SkillTasks.later(585, () -> {
            var b = w[0];
            int minions = level.getEntitiesOfClass(net.minecraft.world.entity.monster.skeleton.WitherSkeleton.class, b.getBoundingBox().inflate(40)).size();
            log("wither: counters - skulls {} (hits {}) rain {} dive hits {} nova hits {} beam hits {} summoned {} (alive {}); player lost {}; blocks {} -> {}",
                    b.skullsFired, b.skullHits, b.marksBurst, b.diveHits, b.novaHits, b.beamHits, b.summoned, minions, String.format("%.0f", lost[0]),
                    blocksBefore, countBlocks(level, net.minecraft.core.BlockPos.containing(field), 14));
            b.hurtServer(level, p.damageSources().playerAttack(p), 1e7F);
            if (b.isAlive()) b.setHealth(0);
        });
        SkillTasks.later(640, () -> {
            log("wither: dead {}, skeletons left {}, key in inventory {}", w[0].isDeadOrDying(),
                    level.getEntitiesOfClass(net.minecraft.world.entity.monster.skeleton.WitherSkeleton.class, p.getBoundingBox().inflate(60)).size(),
                    p.getInventory().contains(new ItemStack(com.jo112ss.rpgmod.registry.ModRegistries.ABYSS_KEY.get())));
            done[0] = true;
            for (var m : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, p.getBoundingBox().inflate(60))) m.discard();
            p.teleportTo(home.x, home.y, home.z);
        });
    }

    private static int countBlocks(ServerLevel level, net.minecraft.core.BlockPos c, int r) {
        int n = 0;
        for (net.minecraft.core.BlockPos b : net.minecraft.core.BlockPos.betweenClosed(c.offset(-r, -3, -r), c.offset(r, 10, r))) if (!level.getBlockState(b).isAir()) n++;
        return n;
    }

    /** The bosses' unique weapons: strength by level, who gets which, two of the effects, and each one in hand. */
    private static void uniquesTest(ServerLevel level, ServerPlayer p) {
        java.util.Random rnd = new java.util.Random(11);
        var U = com.jo112ss.rpgmod.item.UniqueWeapons.class;
        StringBuilder sb = new StringBuilder();
        for (int lv : new int[] {1, 40, 100, 140, 180, 250}) sb.append("Lv").append(lv).append(" +").append(String.format("%.1f", com.jo112ss.rpgmod.item.UniqueWeapons.flat(lv)))
                .append(" x").append(String.format("%.3f", com.jo112ss.rpgmod.item.UniqueWeapons.mult(lv))).append("; ");
        log("uniques: by level {}(diamond +15 x1.33, netherite +18 x1.45)", sb);
        java.util.List<net.minecraft.world.item.ItemStack> all = new java.util.ArrayList<>();
        for (var def : com.jo112ss.rpgmod.item.UniqueWeapons.Def.values()) {
            var st = com.jo112ss.rpgmod.item.UniqueWeapons.create(def, 180, com.jo112ss.rpgmod.item.UniqueWeapons.randomQuality(rnd), rnd);
            all.add(st);
            var mods = st.get(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS);
            net.minecraft.world.item.component.ItemAttributeModifiers nether = new net.minecraft.world.item.ItemStack(com.jo112ss.rpgmod.item.ModItems.get(def.type, com.jo112ss.rpgmod.item.WeaponMaterial.NETHERITE))
                    .get(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS);
            log("uniques: {} ({}, {}) Lv180 name '{}' quality {} options {} masterwork {}; attack mods {} | netherite {}", def.id, def.boss, def.type,
                    st.getHoverName().getString(), st.get(com.jo112ss.rpgmod.registry.ModRegistries.FORGED_DATA.get()).qualityEnum(),
                    st.get(com.jo112ss.rpgmod.registry.ModRegistries.FORGED_DATA.get()).options().size(),
                    st.get(com.jo112ss.rpgmod.registry.ModRegistries.FORGED_DATA.get()).masterwork().isPresent(),
                    mods == null ? "-" : mods.modifiers().stream().map(m -> String.format("%.2f", m.modifier().amount())).toList(),
                    nether == null ? "-" : nether.modifiers().stream().map(m -> String.format("%.2f", m.modifier().amount())).toList());
        }
        int[] sockets = new int[4];
        for (int k = 0; k < 300; k++) sockets[com.jo112ss.rpgmod.gem.Sockets.capacity(com.jo112ss.rpgmod.item.UniqueWeapons.create(
                com.jo112ss.rpgmod.item.UniqueWeapons.Def.LICH_STAFF, 150, com.jo112ss.rpgmod.smithing.core.Quality.COMMON, rnd))]++;
        log("uniques: sockets over 300 drops: 1={} 2={} 3={} (0={})", sockets[1], sockets[2], sockets[3], sockets[0]);
        for (String[] jb : new String[][] {{"wizard", "lich"}, {"fighter", "king_slime"}, {"monk", "golem"}, {"bard", "spider_queen"}, {"shadow_hunter", "spider_queen"}}) {
            become(p, jb[0]);
            log("uniques: a {} at the {} gets one of {}", jb[0], jb[1], com.jo112ss.rpgmod.item.UniqueWeapons.choicesFor(jb[1], p).stream().map(d -> d.id).toList());
        }
        // the slime greatsword's splash, and the lich's staff on a kill
        become(p, "fighter");
        Vec3 at = p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(3));
        java.util.List<net.minecraft.world.entity.monster.zombie.Husk> husks = new java.util.ArrayList<>();
        for (int k = 0; k < 2; k++) {
            var m = net.minecraft.world.entity.EntityTypes.HUSK.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            if (m == null) return;
            m.snapTo(at.x + k * 1.2, p.getY(), at.z, 0, 0);
            m.setNoAi(true);
            level.addFreshEntity(m);
            m.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(10000);
            m.setHealth(10000);
            husks.add(m);
        }
        SkillTasks.later(5, () -> {
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, all.get(0).copy());
            float h0 = husks.get(0).getHealth(), h1 = husks.get(1).getHealth();
            husks.get(0).hurtServer(level, p.damageSources().playerAttack(p), 200);
            log("uniques: slime greatsword hit 200 -> the one hit lost {}, the one beside it {} (30% of what landed), slowed {}", Math.round(h0 - husks.get(0).getHealth()),
                    Math.round(h1 - husks.get(1).getHealth()), husks.get(1).hasEffect(net.minecraft.world.effect.MobEffects.SLOWNESS));
            become(p, "wizard");
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, all.get(5).copy());
            var d = JobManager.data(p);
            d.setMana(0);
            husks.get(1).setHealth(1);
            husks.get(1).hurtServer(level, p.damageSources().playerAttack(p), 50);
            log("uniques: lich staff kill -> MP {} of {} (4% expected), magic attack {}", String.format("%.1f", d.mana()), String.format("%.0f", JobManager.maxMana(p)),
                    String.format("%.1f", JobManager.magicAttack(p)));
            husks.forEach(net.minecraft.world.entity.Entity::discard);
        });
        for (int k = 0; k < all.size(); k++) { // each in hand for the camera
            int i = k;
            SkillTasks.later(20 + k * 20, () -> p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, all.get(i).copy()));
        }
        SkillTasks.later(20 + all.size() * 20, () -> {
            for (int k = 0; k < all.size() && k < 9; k++) p.getInventory().setItem(k, all.get(k).copy());
        });
    }

    /** A skill's invisibility makes monsters drop you; a potion's does not. */
    private static void unseenTest(ServerLevel level, ServerPlayer p) {
        var m = net.minecraft.world.entity.EntityTypes.ZOMBIE.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        if (m == null) return;
        Vec3 at = p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(6));
        m.snapTo(at.x, p.getY(), at.z, 0, 0);
        level.addFreshEntity(m);
        m.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(100000);
        m.setHealth(100000);
        m.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE).setBaseValue(0);
        var mode = p.gameMode();
        boolean inv = p.getAbilities().invulnerable;
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        p.getAbilities().invulnerable = true;
        become(p, "shadow_hunter");
        JobManager.data(p).devUnlockAll();
        // a potion's invisibility: still a target
        p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.INVISIBILITY, 200, 0));
        m.setTarget(p);
        SkillTasks.later(20, () -> {
            log("unseen: potion invisibility, zombie target {}", m.getTarget() == p ? "still you" : String.valueOf(m.getTarget()));
            p.removeEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY);
            m.setTarget(p);
            SkillExecutor.execute(p, JobDefs.skill("stealth"), 1);
        });
        SkillTasks.later(40, () -> log("unseen: stealth 1s in, zombie target {}, unseen {}", m.getTarget() == p ? "still you" : String.valueOf(m.getTarget()),
                p.hasEffect(com.jo112ss.rpgmod.registry.ModEffects.UNSEEN)));
        SkillTasks.later(60, () -> {
            m.setTarget(p); // trying again while unseen
            SkillTasks.later(2, () -> log("unseen: set on you again while unseen -> target {}", m.getTarget() == p ? "still you" : String.valueOf(m.getTarget())));
        });
        SkillTasks.later(80, () -> {
            SkillExecutor.execute(p, JobDefs.skill("night_hunt"), 1);
            boolean before = p.hasEffect(com.jo112ss.rpgmod.registry.ModEffects.UNSEEN);
            com.jo112ss.rpgmod.job.HealthScale.SKILL.remove(); // a swing of the player's own, not a skill task
            m.hurtServer(level, p.damageSources().playerAttack(p), 1);
            log("unseen: night hunt unseen {}, after a hit {} (invisible {})", before, p.hasEffect(com.jo112ss.rpgmod.registry.ModEffects.UNSEEN),
                    p.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY));
        });
        SkillTasks.later(140, () -> {
            m.discard();
            p.setGameMode(mode);
            p.getAbilities().invulnerable = inv;
            p.onUpdateAbilities();
        });
    }

    /** The bard line's ultimates against a sturdy husk, next to the bard's sonic boom: damage each, and what the formula says. */
    private static void bardUlts(ServerLevel level, ServerPlayer p) {
        var m = net.minecraft.world.entity.EntityTypes.HUSK.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        if (m == null) return;
        Vec3 at = p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(3));
        m.snapTo(at.x, p.getY(), at.z, 0, 0);
        m.setNoAi(true);
        level.addFreshEntity(m);
        m.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000000);
        m.setHealth(1000000);
        m.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        String[][] plan = {{"bard", "sonic_boom"}, {"bard", "finale"}, {"bard", "grand_concert"}, {"spellsinger", "arcane_note"},
                {"spellsinger", "sealing_chord"}, {"spellsinger", "grand_concerto"}};
        for (int k = 0; k < plan.length; k++) {
            String job = plan[k][0], skill = plan[k][1];
            SkillTasks.later(10 + k * 260, () -> {
                become(p, job, false);
                JobManager.data(p).devUnlockAll();
                m.snapTo(at.x, p.getY(), at.z, 0, 0);
                float h0 = m.getHealth();
                var def = JobDefs.skill(skill);
                double mult = JobManager.powerMultiplier(p, def);
                boolean ok = SkillExecutor.execute(p, def, mult);
                SkillTasks.later(240, () -> log("bard ult {} ({}): {} damage in 12s, ratio {} x power {} = {} a hit{}", skill, job, Math.round(h0 - m.getHealth()),
                        String.format("%.2f", def.power()), String.format("%.1f", mult), Math.round(def.power() * mult), ok ? "" : " (refused)"));
            });
        }
        SkillTasks.later(10 + plan.length * 260 + 5, m::discard);
    }

    private static void hitWindows(ServerLevel level, ServerPlayer p) {
        var m = net.minecraft.world.entity.EntityTypes.HUSK.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        if (m == null) return;
        Vec3 at = p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(3));
        m.snapTo(at.x, p.getY(), at.z, 0, 0);
        m.setNoAi(true);
        m.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE, 2000, 0));
        level.addFreshEntity(m);
        m.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(100000);
        m.setHealth(100000);
        var other = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);
        var skillKey = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DAMAGE_TYPE,
                net.minecraft.resources.Identifier.fromNamespaceAndPath(com.jo112ss.rpgmod.RpgMod.MODID, "skill"));
        StringBuilder sb = new StringBuilder();
        java.util.function.BiConsumer<String, java.util.function.BooleanSupplier> hit = (name, r) -> {
            float h0 = m.getHealth();
            com.jo112ss.rpgmod.mob.HitWindows.debug = true;
            boolean ok = r.getAsBoolean();
            com.jo112ss.rpgmod.mob.HitWindows.debug = false;
            sb.append(name).append('=').append(String.format("%.1f", h0 - m.getHealth())).append(ok ? "" : " (refused)").append("; ");
        };
        SkillTasks.later(5, () -> {
            com.jo112ss.rpgmod.mob.HitWindows.testBlocked = 0;
            com.jo112ss.rpgmod.mob.HitWindows.testCut = 0;
            hit.accept("skill 20", () -> m.hurtServer(level, p.damageSources().source(skillKey, p, p), 20));
            hit.accept("plain 20 right after a skill", () -> m.hurtServer(level, p.damageSources().playerAttack(p), 20));
            hit.accept("other player's plain 20 (same tick)", () -> m.hurtServer(level, other.damageSources().playerAttack(other), 20));
            hit.accept("plain 20 again", () -> m.hurtServer(level, p.damageSources().playerAttack(p), 20));
            hit.accept("plain 30", () -> m.hurtServer(level, p.damageSources().playerAttack(p), 30));
            hit.accept("skill 20 again (no cooldown)", () -> m.hurtServer(level, p.damageSources().source(skillKey, p, p), 20));
        });
        SkillTasks.later(10, () -> hit.accept("plain 20 after 5 ticks", () -> m.hurtServer(level, p.damageSources().playerAttack(p), 20)));
        SkillTasks.later(17, () -> {
            hit.accept("plain 20 after 12 ticks", () -> m.hurtServer(level, p.damageSources().playerAttack(p), 20));
            log("hit windows: {}blocked {} cut {}", sb, com.jo112ss.rpgmod.mob.HitWindows.testBlocked, com.jo112ss.rpgmod.mob.HitWindows.testCut);
            m.discard();
        });
    }

    private static void skillBoosts(ServerLevel level, ServerPlayer p) {
        int jobs = 0, skills = 0, cells = 0, short_ = 0, apart = 0;
        StringBuilder per = new StringBuilder();
        for (var job : JobDefs.JOBS.values()) {
            var panel = com.jo112ss.rpgmod.job.core.SkillPanel.forJob(job.id());
            jobs++;
            int js = 0, jc = 0;
            for (var n : panel.nodes()) {
                if (n.skill() != null || n.type() == com.jo112ss.rpgmod.job.core.SkillPanel.NodeType.START) {
                    String sid = n.skill() != null ? n.skill() : JobDefs.isAdvanced(job.id()) || job.actives().isEmpty() ? null : job.actives().get(0);
                    if (sid == null) continue;
                    js++;
                    var def = JobDefs.skill(sid);
                    int want = com.jo112ss.rpgmod.job.core.SkillBoost.cellsFor(def).size();
                    int got = 0;
                    for (var o : panel.nodes()) if (o.type() == com.jo112ss.rpgmod.job.core.SkillPanel.NodeType.BOOST && sid.equals(o.boostSkill())) got++;
                    if (got < want) {
                        short_++;
                        log("boosts: {} {} has {} of {} boost cells", job.id(), sid, got, want);
                    }
                }
                if (n.type() == com.jo112ss.rpgmod.job.core.SkillPanel.NodeType.BOOST) {
                    jc++;
                    boolean next = false;
                    for (var o : panel.neighbours(n)) if (n.boostSkill().equals(o.skill()) || o.type() == com.jo112ss.rpgmod.job.core.SkillPanel.NodeType.START) next = true;
                    if (!next) apart++;
                }
            }
            skills += js;
            cells += jc;
            per.append(job.id()).append(" ").append(js).append("/").append(jc).append("  ");
        }
        log("boosts: {} panels, {} skill cells, {} boost cells, {} skills short of cells, {} cells not next to their skill", jobs, skills, cells, short_, apart);
        log("boosts: per panel (skills/boosts) {}", per);
        // in play: the fighter's power slash, the monk's combo (a rapid one)
        become(p, "fighter");
        JobData d = JobManager.data(p);
        d.resetPanel();
        d.addBonusPoints(60);
        var panel = d.panel();
        // power slash comes with the job (its boosts sit round the centre); the second skill is on the panel
        List<com.jo112ss.rpgmod.job.core.SkillPanel.Node> starter = new java.util.ArrayList<>();
        for (var n : panel.nodes()) if ("power_slash".equals(n.boostSkill())) starter.add(n);
        log("boosts: power slash (from the start) boosts {}", starter.stream().map(b -> b.boost() + "+" + Math.round(b.amount() * 100) + "% ring " + b.ring()).toList());
        String second = JobDefs.job("fighter").actives().get(1);
        var slash = JobDefs.skill(second);
        com.jo112ss.rpgmod.job.core.SkillPanel.Node skillNode = null;
        List<com.jo112ss.rpgmod.job.core.SkillPanel.Node> boosts = new java.util.ArrayList<>();
        for (var n : panel.nodes()) {
            if (second.equals(n.skill())) skillNode = n;
            if (second.equals(n.boostSkill())) boosts.add(n);
        }
        log("boosts: {} cell ring {} with boosts {}", second, skillNode == null ? -1 : skillNode.ring(),
                boosts.stream().map(b -> b.boost() + "+" + Math.round(b.amount() * 100) + "% ring " + b.ring() + " cost " + b.cost()).toList());
        double pow0 = JobManager.powerMultiplier(p, slash), cd0 = JobManager.cooldownSeconds(p, slash);
        // a boost next to something owned but without its skill: refused
        var b0 = boosts.get(0);
        for (var o : panel.neighbours(b0)) if (o != skillNode) d.unlock(o.id());
        JobManager.unlockNode(p, b0.id(), false);
        log("boosts: without the skill, buying {} -> owned {}", b0.boost(), d.unlocked(false).contains(b0.id()));
        d.unlock(skillNode.id());
        for (var b : boosts) JobManager.unlockNode(p, b.id(), false);
        log("boosts: with the skill - owned {}; power x{} -> x{} ({}), cooldown {}s -> {}s", boosts.stream().map(b -> d.unlocked(false).contains(b.id())).toList(),
                String.format("%.1f", pow0), String.format("%.1f", JobManager.powerMultiplier(p, slash)),
                String.format("%+.0f%%", (JobManager.powerMultiplier(p, slash) / pow0 - 1) * 100), String.format("%.2f", cd0), String.format("%.2f", JobManager.cooldownSeconds(p, slash)));
        become(p, "monk");
        d.resetPanel();
        d.addBonusPoints(60);
        var combo = JobDefs.skill("combo");
        double cp0 = JobManager.powerMultiplier(p, combo), cm0 = JobManager.manaCost(p, combo);
        for (var n : d.panel().nodes()) if ("combo".equals(n.skill()) || "combo".equals(n.boostSkill())) d.unlock(n.id());
        log("boosts: monk combo (rapid) power {}, mana {} -> {}", String.format("%+.0f%%", (JobManager.powerMultiplier(p, combo) / cp0 - 1) * 100),
                String.format("%.0f", cm0), String.format("%.0f", JobManager.manaCost(p, combo)));
        d.resetPanel();
        JobManager.sync(p);
    }

    /**
     * Ki shots on the right button: one point each, at most every half second, a dull click with none;
     * the god hand's shot heals a little.
     */
    private static void kiShot(ServerLevel level, ServerPlayer p) {
        become(p, "monk");
        JobData d = JobManager.data(p);
        Vec3 home = p.position();
        Vec3 field = home.add(0, 0, -120);
        p.teleportTo(field.x, field.y, field.z);
        p.setYRot(180);
        p.setXRot(0);
        net.minecraft.world.entity.monster.zombie.Husk[] dummy = {null};
        SkillTasks.later(30, () -> {
            for (var m : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, p.getBoundingBox().inflate(40))) m.discard();
            var h = net.minecraft.world.entity.EntityTypes.HUSK.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            h.snapTo(field.x, field.y, field.z - 9, 0, 0);
            h.setNoAi(true);
            level.addFreshEntity(h);
            h.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(5000);
            h.setHealth(5000);
            dummy[0] = h;
            p.setYRot(180);
            com.jo112ss.rpgmod.job.Monk.add(p, 0); // as if just fighting: no fading
            d.setKi(3);
            JobManager.sync(p);
            com.jo112ss.rpgmod.job.MoreJobSkills.kiShotHits = 0;
        });
        // charging: 26 ticks held = 2 charges (2 points); let go too soon = nothing; 1 point left = 1 charge at most
        SkillTasks.later(38, () -> {
            StringBuilder sb = new StringBuilder();
            for (int n = 1; n <= 7; n++) sb.append(n).append(":").append(com.jo112ss.rpgmod.job.Monk.chargeTicks(n)).append("t x").append(String.format("%.1f", com.jo112ss.rpgmod.job.MoreJobSkills.kiShotPower(n))).append("  ");
            log("kishot: charges {}", sb);
        });
        SkillTasks.later(40, () -> com.jo112ss.rpgmod.job.Monk.press(p));
        SkillTasks.later(66, () -> {
            int k = d.ki();
            com.jo112ss.rpgmod.job.Monk.release(p);
            log("kishot: held 26 ticks - ki {} -> {} (shot of {} charges, x{} attack)", k, d.ki(), k - d.ki(),
                    String.format("%.2f", com.jo112ss.rpgmod.job.MoreJobSkills.kiShotPower(k - d.ki())));
        });
        SkillTasks.later(68, () -> com.jo112ss.rpgmod.job.Monk.press(p));
        SkillTasks.later(72, () -> {
            int k = d.ki();
            com.jo112ss.rpgmod.job.Monk.release(p);
            log("kishot: let go after 4 ticks - ki {} -> {}", k, d.ki());
        });
        SkillTasks.later(74, () -> com.jo112ss.rpgmod.job.Monk.press(p));
        SkillTasks.later(104, () -> {
            int k = d.ki();
            com.jo112ss.rpgmod.job.Monk.release(p);
            log("kishot: held 30 ticks with {} ki - ki -> {}", k, d.ki());
        });
        SkillTasks.later(110, () -> {
            int k0 = d.ki(), p0 = d.kiParts();
            for (int i = 0; i < 4; i++) com.jo112ss.rpgmod.job.Monk.hit(p);
            int k1 = d.ki(), p1 = d.kiParts();
            com.jo112ss.rpgmod.job.Monk.add(p, 1);
            log("kishot: four knuckle hits - ki {} (+{} parts) -> {} (+{} parts); a skill's point -> {} (+{} parts)", k0, p0, k1, p1, d.ki(), d.kiParts());
        });
        SkillTasks.later(112, () -> log("kishot: hits {} dummy lost {} (attack {})", com.jo112ss.rpgmod.job.MoreJobSkills.kiShotHits,
                String.format("%.1f", 5000 - dummy[0].getHealth()), String.format("%.1f", JobManager.physicalAttack(p))));
        SkillTasks.later(115, () -> {
            become(p, "qi_master");
            p.setYRot(180);
            com.jo112ss.rpgmod.job.Monk.add(p, 0);
            d.setKi(2);
            JobManager.sync(p);
            com.jo112ss.rpgmod.job.MoreJobSkills.kiShotHealed = 0;
            p.setHealth(p.getMaxHealth() * 0.5F);
            com.jo112ss.rpgmod.job.MoreJobSkills.kiShotHits = 0;
        });
        SkillTasks.later(120, () -> com.jo112ss.rpgmod.job.Monk.press(p));
        SkillTasks.later(146, () -> com.jo112ss.rpgmod.job.Monk.release(p));
        SkillTasks.later(158, () -> log("kishot: god hand hits {} healed {} (max {}), ki {}", com.jo112ss.rpgmod.job.MoreJobSkills.kiShotHits,
                String.format("%.1f", com.jo112ss.rpgmod.job.MoreJobSkills.kiShotHealed), String.format("%.0f", p.getMaxHealth()), d.ki()));
        // meditation: no ki while it lasts (3s), all of it at the end
        SkillTasks.later(160, () -> {
            become(p, "monk");
            com.jo112ss.rpgmod.job.Monk.add(p, 0);
            d.setKi(0);
            d.setKiParts(0);
            JobManager.sync(p);
            com.jo112ss.rpgmod.job.SkillExecutor.execute(p, JobDefs.skill("meditate"), 1);
        });
        SkillTasks.later(220, () -> log("kishot: meditating 3s - ki {} (+{} parts)", d.ki(), d.kiParts()));
        SkillTasks.later(262, () -> log("kishot: meditation over (4.5s at this level) - ki {} of {}", d.ki(), d.maxKi()));
        SkillTasks.later(266, () -> {
            if (dummy[0] != null) dummy[0].discard();
            p.setHealth(p.getMaxHealth());
            p.teleportTo(home.x, home.y, home.z);
        });
    }

    private static void monk(ServerLevel level, ServerPlayer p, List<? extends LivingEntity> dummies) {
        become(p, "monk");
        if (dummies.size() < 5) return;
        LivingEntity near = dummies.get(4);
        JobData d = JobManager.data(p);
        var mode = p.gameMode();
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        boolean inv = p.getAbilities().invulnerable;
        p.getAbilities().invulnerable = false;
        SkillTasks.later(64, () -> {
            p.setGameMode(mode);
            p.getAbilities().invulnerable = inv;
        });
        SkillTasks.later(25, () -> {
            int k0 = d.ki();
            p.attack(near);
            log("ki: knuckle hit {} -> {}", k0, d.ki());
            // parry: the next melee hit taken is turned aside and answered
            SkillExecutor.execute(p, JobDefs.skill("parry"), JobManager.powerMultiplier(p, JobDefs.skill("parry")));
            float hp = p.getHealth(), mobHp = near.getHealth();
            int k1 = d.ki();
            p.setInvulnerableTime(0);
            p.hurtServer(level, level.damageSources().mobAttack(near), 20);
            SkillTasks.later(2, () -> log("parry: health {} -> {}, attacker {} -> {}, ki {} -> {}, parry left {}", String.format("%.1f", hp),
                    String.format("%.1f", p.getHealth()), String.format("%.1f", mobHp), String.format("%.1f", near.getHealth()), k1, d.ki(),
                    p.hasEffect(ModEffects.PARRY)));
        });
        SkillTasks.later(32, () -> {
            float hp = p.getHealth();
            p.setInvulnerableTime(0);
            p.hurtServer(level, level.damageSources().mobAttack(near), 20);
            float plain = hp - p.getHealth();
            p.setHealth(p.getMaxHealth());
            SkillExecutor.execute(p, JobDefs.skill("diamond_body"), 1);
            // the next hit after the damage cooldown (hits within it only count above the last one)
            SkillTasks.later(24, () -> {
                float hp2 = p.getHealth();
                p.hurtServer(level, level.damageSources().mobAttack(near), 20);
                log("diamond body: a hit took {} plain, {} with it", String.format("%.1f", plain), String.format("%.1f", hp2 - p.getHealth()));
                p.removeEffect(ModEffects.DIAMOND_BODY);
                p.setHealth(p.getMaxHealth());
            });
        });
    }
}
