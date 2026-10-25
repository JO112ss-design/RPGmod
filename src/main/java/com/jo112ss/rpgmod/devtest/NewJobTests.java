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
            default -> log("unknown scene {}", name);
        }
    }

    private static void become(ServerPlayer p, String job) {
        JobData d = JobManager.data(p);
        if (startJob == null) {
            startJob = d.jobId();
            startAdv = d.isAdvanced();
        }
        d.switchTo(job, false);
        JobType type = JobType.byId(job);
        p.getInventory().setItem(6, new ItemStack(ModItems.get(type.weapons.get(0), WeaponMaterial.IRON)));
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
        SkillTasks.later(40, () -> {
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
            float hp2 = p.getHealth();
            p.setInvulnerableTime(0);
            p.hurtServer(level, level.damageSources().mobAttack(near), 20);
            log("diamond body: a hit took {} plain, {} with it", String.format("%.1f", plain), String.format("%.1f", hp2 - p.getHealth()));
            p.removeEffect(ModEffects.DIAMOND_BODY);
            p.setHealth(p.getMaxHealth());
        });
    }
}
