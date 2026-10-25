package com.jo112ss.rpgmod.devtest;

import com.jo112ss.rpgmod.RpgMod;
import com.jo112ss.rpgmod.job.HealthScale;
import com.jo112ss.rpgmod.job.JobData;
import com.jo112ss.rpgmod.job.JobManager;
import com.jo112ss.rpgmod.job.JobStats;
import com.jo112ss.rpgmod.job.SkillTasks;
import com.jo112ss.rpgmod.job.core.JobStat;
import com.jo112ss.rpgmod.job.core.LevelCurve;
import com.jo112ss.rpgmod.mob.MobConfig;
import com.jo112ss.rpgmod.mob.core.MobLevelMath;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Dev test for the 500-health rebalance (client showcase entry "bal:checks"). */
final class BalanceTests {
    private BalanceTests() {}

    static void log(String msg, Object... args) {
        RpgMod.LOGGER.info("[AUTOTEST] baltest " + msg, args);
    }

    static void scene(ServerLevel level, ServerPlayer p, String name) {
        if (name.equals("checks")) checks(level, p);
        else log("unknown scene {}", name);
    }

    private static double hp(String job, int lv) {
        JobData d = new JobData();
        d.setJob(job);
        d.setLevel(lv);
        return 20 + JobStats.compute(d).get(JobStat.MAX_HEALTH);
    }

    private static void checks(ServerLevel level, ServerPlayer p) {
        // growth tables
        var r = MobConfig.rules();
        for (int lv : new int[] {1, 50, 100, 150, 199}) {
            JobData f = new JobData();
            f.setJob("fighter");
            f.setLevel(lv);
            var st = JobStats.compute(f);
            JobData w = new JobData();
            w.setJob("wizard");
            w.setLevel(lv);
            double own = st.get(JobStat.ATTACK_DAMAGE), mag = JobStats.compute(w).get(JobStat.MAGIC_ATTACK);
            StringBuilder sw = new StringBuilder(), staff = new StringBuilder();
            for (var mat : com.jo112ss.rpgmod.item.WeaponMaterial.values()) {
                sw.append(mat.id).append('=').append(String.format("%.1f", (1 + 3 + mat.meleeBonus() + own) * mat.multiplier)).append(' ');
                staff.append(mat.id).append('=').append(String.format("%.1f", (1 + mat.staffMagic() + mag) * mat.multiplier)).append(' ');
            }
            log("Lv{}: hp fighter {} paladin {} wizard {} healer {} enchanter {} ranger {} | armor from level {} | own attack {} magic {} | sword {}| staff {}| monster hp x{} dmg x{} | exp to next {}",
                    lv, Math.round(hp("fighter", lv)), Math.round(hp("paladin", lv)), Math.round(hp("wizard", lv)), Math.round(hp("healer", lv)),
                    Math.round(hp("enchanter", lv)), Math.round(hp("ranger", lv)), String.format("%.1f", st.get(JobStat.ARMOR)),
                    own, mag, sw, staff, String.format("%.2f", MobLevelMath.healthMultiplier(r, lv)),
                    String.format("%.2f", MobLevelMath.damageMultiplier(r, lv)), LevelCurve.expToNext(lv));
        }
        log("dragon health x{} damage x{}; magic bolt ratio {}", MobConfig.dragonHealth(), MobConfig.dragonDamage(),
                com.jo112ss.rpgmod.job.core.JobDefs.skill("magic_bolt").power());
        // panels: no skill power cells left
        int sp = 0;
        for (String j : new String[] {"wizard", "healer", "enchanter", "paladin", "runemaster", "bishop"}) {
            for (var n : com.jo112ss.rpgmod.job.core.SkillPanel.forJob(j).nodes()) if (n.stat() != null && n.stat().id.equals("skill_power")) sp++;
        }
        log("panel stats: wizard {} healer {} enchanter {}; skill power cells {}", com.jo112ss.rpgmod.job.core.JobDefs.job("wizard").panelStats(),
                com.jo112ss.rpgmod.job.core.JobDefs.job("healer").panelStats(), com.jo112ss.rpgmod.job.core.JobDefs.job("enchanter").panelStats(), sp);

        // the player at its level: shown stats
        JobData d = JobManager.data(p);
        log("player {} Lv{}: max hp {} attack {} magic {}", d.displayJobId(), d.level(), String.format("%.0f", p.getMaxHealth()),
                String.format("%.1f", JobManager.physicalAttack(p)), String.format("%.1f", JobManager.magicAttack(p)));
        // the held weapon's multiplier on the attack: a netherite sword in the main hand (checked two ticks later)
        int slot = p.getInventory().getSelectedSlot();
        ItemStack prev = p.getInventory().getItem(slot);
        p.getInventory().setItem(slot, new ItemStack(Items.NETHERITE_SWORD));
        SkillTasks.later(2, () -> {
            var inst = p.getAttribute(Attributes.ATTACK_DAMAGE);
            log("netherite sword held: attack {} (formula (1 + 3 + 6 + own {}) x1.45 = {})", String.format("%.1f", inst.getValue()),
                    JobStats.compute(d).get(JobStat.ATTACK_DAMAGE), String.format("%.1f", (10 + JobStats.compute(d).get(JobStat.ATTACK_DAMAGE)) * 1.45));
            p.getInventory().setItem(slot, prev);
        });

        // damage dealt: the level bonus on a plain hit; magic against armour
        Husk bare = dummy(level, p, 3, 0);
        for (EquipmentSlot s : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) bare.setItemSlot(s, ItemStack.EMPTY);
        Husk armoured = dummy(level, p, -3, 0);
        armoured.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
        armoured.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
        // hit two ticks later, once the dummies' armour counts
        SkillTasks.later(3, () -> {
            JobManager.testForceCrit = false;
            float b0 = bare.getHealth(), a0 = armoured.getHealth();
            bare.damageCooldownTime = 0;
            bare.hurtServer(level, p.damageSources().playerAttack(p), 10);
            var magic = p.damageSources().source(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DAMAGE_TYPE,
                    net.minecraft.resources.Identifier.fromNamespaceAndPath(RpgMod.MODID, "skill_magic")), p, p);
            armoured.damageCooldownTime = 0;
            armoured.hurtServer(level, magic, 10);
            JobManager.testForceCrit = null;
            log("hit 10: bare ({}% reduction) took {}, magic vs diamond armour ({}% reduction) took {} ({} expected)",
                    Math.round(com.jo112ss.rpgmod.mob.Defense.reduction(bare) * 100), String.format("%.1f", b0 - bare.getHealth()),
                    Math.round(com.jo112ss.rpgmod.mob.Defense.reduction(armoured) * 100),
                    String.format("%.1f", a0 - armoured.getHealth()), String.format("%.1f", 10 * (1 - com.jo112ss.rpgmod.mob.Defense.reduction(armoured))));
            bare.discard();
            armoured.discard();

            // health scale: vanilla heals and damage with nobody behind it grow with max health
            var mode = p.gameMode();
            boolean inv = p.getAbilities().invulnerable;
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            p.getAbilities().invulnerable = false;
            p.removeAllEffects();
            p.setHealth(p.getMaxHealth() * 0.5F);
            float h0 = p.getHealth();
            p.heal(2);
            float h1 = p.getHealth();
            HealthScale.heal(p, 2);
            float h2 = p.getHealth();
            p.damageCooldownTime = 0;
            p.hurtServer(level, level.damageSources().fall(), 3);
            float h3 = p.getHealth();
            log("health scale x{}: vanilla heal 2 -> +{}, mod heal 2 -> +{}, fall 3 -> -{}", String.format("%.1f", HealthScale.factor(p)),
                    String.format("%.1f", h1 - h0), String.format("%.1f", h2 - h1), String.format("%.1f", h2 - h3));
            p.setHealth(p.getMaxHealth());
            p.getAbilities().invulnerable = inv;
            p.setGameMode(mode);
        });

        // a levelled zombie's hit on the player, by level (raw, before armour)
        SkillTasks.later(2, () -> log("zombie hit (3) raw: Lv1 {} Lv100 {} Lv199 {}", String.format("%.0f", 3 * MobLevelMath.damageMultiplier(r, 1)),
                String.format("%.0f", 3 * MobLevelMath.damageMultiplier(r, 100)), String.format("%.0f", 3 * MobLevelMath.damageMultiplier(r, 199))));
    }

    private static Husk dummy(ServerLevel level, ServerPlayer p, double dx, double dz) {
        Husk h = EntityTypes.HUSK.create(level, EntitySpawnReason.COMMAND);
        h.setNoAi(true);
        h.setSilent(true);
        h.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500);
        h.setHealth(500);
        h.snapTo(p.getX() + dx, p.getY(), p.getZ() + dz, 0, 0);
        level.addFreshEntity(h);
        return h;
    }
}
