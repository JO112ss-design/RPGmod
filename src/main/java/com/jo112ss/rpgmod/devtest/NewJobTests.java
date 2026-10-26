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
import com.jo112ss.rpgmod.registry.ModEffects;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

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
            case "bard" -> become(p, "bard");
            case "hybrids" -> hybrids(level, p, dummies);
            case "coins" -> coins(level, p);
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
            log("gun: bayonet hit loads a round {} -> {}", before, d.ammo());
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
                p.hasEffect(ModEffects.WARD_MELODY), d.cooldownEnd("sonic_boom") - level.getGameTime()));
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
