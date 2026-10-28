package com.jo112ss.rpgmod.devtest;

import com.jo112ss.rpgmod.RpgMod;
import com.jo112ss.rpgmod.item.ModItems;
import com.jo112ss.rpgmod.item.WeaponMaterial;
import com.jo112ss.rpgmod.item.WeaponType;
import com.jo112ss.rpgmod.job.JobData;
import com.jo112ss.rpgmod.job.JobManager;
import com.jo112ss.rpgmod.job.JobStats;
import com.jo112ss.rpgmod.job.JobType;
import com.jo112ss.rpgmod.job.SkillTasks;
import com.jo112ss.rpgmod.job.core.JobDefs;
import com.jo112ss.rpgmod.job.core.SkillDef;
import com.jo112ss.rpgmod.job.core.SkillEffect;
import com.jo112ss.rpgmod.job.core.SkillKind;
import com.jo112ss.rpgmod.job.core.SkillPanel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * Dev test: every job's damage against one target, on equal terms - the same level, diamond weapons,
 * every skill learnt, no stat or boost cells. Each job fights a training dummy (a monster of the same
 * level, kept alive) for {@link #RUN} ticks three times: skills and plain attacks only, then opening
 * with each of its ultimates. The rotation uses every attack skill, spell and buff the moment it is
 * ready (a half-second pause after each, a spell's cast time) and plain attacks in between. Damage is
 * what reaches the dummy after its defence. The server runs at full speed (tick sprint) while it lasts.
 */
@EventBusSubscriber(modid = RpgMod.MODID)
public final class DpsBench {
    private DpsBench() {}

    public static final String DUMMY = "rpgmod_dps_dummy";
    public static final int LEVEL = 130, RUN = 300, GAP = 30, GCD = 10;

    private static boolean counting;
    private static double dealt, basicDealt;
    private static int basicHits;

    /** The benchmark under way (driven by the server tick: skill tasks give up after a minute). */
    private static Runner active;

    @SubscribeEvent
    public static void onTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        if (active == null) return;
        Runner r = active;
        boolean done;
        try {
            done = r.step();
        } catch (Exception e) {
            RpgMod.LOGGER.error("[AUTOTEST] dps: failed on {}", r.ji < 0 || r.ji >= r.jobs.size() ? "?" : r.jobs.get(r.ji), e);
            r.run = 3; // on to the next job
            r.t = 0;
            done = false;
        }
        if (done && active == r) active = null;
    }

    /** The dummy never dies (a big hit would otherwise end the run early). */
    @SubscribeEvent
    public static void onDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        if (!event.getEntity().entityTags().contains(DUMMY)) return;
        event.setCanceled(true);
        event.getEntity().setHealth(event.getEntity().getMaxHealth());
    }

    /**
     * Left out of the rotation: guarding, healing, getting away, drawing aggro, mana tricks - what a
     * player would not spend time on to deal damage to one target.
     */
    public static final java.util.Set<String> NOT_DAMAGE = java.util.Set.of(
            "meditate", "parry", "foresight", "counter_stance", "holy_guard", "guardian_dome", "iron_wall", "shield_wall", "cover", "provoke_shield",
            "mana_barrier", "arcane_shield", "holy_barrier", "guardian_angel", "nourish", "spell_guard", "mana_meditate", "mana_share", "element_shift",
            "hard_qi", "immovable", "dragon_scale", "smoke_bomb", "stealth", "vanish", "roll", "backstep", "shadow_leap", "blink", "phase_shift",
            "decoy", "taunt", "provoke", "switcheroo", "rum", "grapple", "sky_step", "lucky_compass", "shadow_run", "mimic", "steal", "night_sight", "feather_rune",
            "ward_rune", "chrono", "refine_qi");

    /** Jobs whose damage comes from the lute's rhythm game: the benchmark cannot play it. */
    public static final java.util.Set<String> SONG_JOBS = java.util.Set.of("bard", "maestro", "spellsinger");

    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Post event) {
        if (!counting || !event.getEntity().entityTags().contains(DUMMY)) return;
        dealt += event.getInflictedDamage();
        var src = event.getSource();
        if (src.is(net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK) || src.getDirectEntity() instanceof net.minecraft.world.entity.projectile.arrow.AbstractArrow) {
            basicDealt += event.getInflictedDamage();
            basicHits++;
        }
    }

    private record Result(String job, String group, double plain, String ult1, double withUlt1, String ult2, double withUlt2, int skills) {}

    private static void log(String fmt, Object... args) {
        RpgMod.LOGGER.info("[AUTOTEST] dps: " + fmt, args);
    }

    public static void start(ServerLevel level, ServerPlayer p) {
        Vec3 home = p.position();
        Vec3 field = home.add(0, 0, 260);
        p.teleportTo(field.x, field.y, field.z);
        List<String> jobs = new ArrayList<>(JobDefs.JOBS.keySet());
        List<Result> results = new ArrayList<>();
        String startJob = JobManager.data(p).jobId();
        boolean startAdv = JobManager.data(p).isAdvanced();
        int startLevel = JobManager.data(p).level();
        jobs.removeAll(SONG_JOBS);
        log("{} jobs at Lv{}, diamond weapons, every skill, no stat or boost cells; {}s per run (bards left out: their damage is the rhythm game)", jobs.size(), LEVEL, RUN / 20);
        SkillTasks.later(40, () -> {
            com.jo112ss.rpgmod.job.fx.ServerFx.mute = true;
            level.getServer().tickRateManager().requestGameToSprint(200000);
            new Runner(level, p, field, jobs, results, () -> {
                com.jo112ss.rpgmod.job.fx.ServerFx.mute = false;
                level.getServer().tickRateManager().stopSprinting();
                report(results);
                JobData d = JobManager.data(p);
                d.switchTo(startJob, startAdv);
                d.setLevel(startLevel);
                d.ensureAdvanced();
                JobStats.apply(p, d);
                JobManager.sync(p);
                p.teleportTo(home.x, home.y, home.z);
            }).go();
        });
    }

    private static void report(List<Result> results) {
        double bossHp = com.jo112ss.rpgmod.mob.MobConfig.kingHealth()
                * com.jo112ss.rpgmod.mob.core.MobLevelMath.healthMultiplier(com.jo112ss.rpgmod.mob.MobConfig.rules(), LEVEL);
        log("---- results (damage per second against a Lv{} monster; boss = a Lv{} boss alone, {} health) ----", LEVEL, LEVEL, Math.round(bossHp));
        results.sort(Comparator.comparingDouble((Result r) -> -r.plain));
        for (Result r : results) {
            log("{} [{}] plain {} | +{} {} | +{} {} | boss {}s ({} skills)", r.job, r.group, Math.round(r.plain), r.ult1, Math.round(r.withUlt1),
                    r.ult2, Math.round(r.withUlt2), Math.round(bossHp / Math.max(1, Math.max(r.plain, Math.max(r.withUlt1, r.withUlt2)))), r.skills);
        }
        log("---- end ----");
    }

    /** Works through the jobs one tick at a time. */
    private static final class Runner {
        final ServerLevel level;
        final ServerPlayer p;
        final Vec3 field;
        final List<String> jobs;
        final List<Result> results;
        final Runnable done;
        int ji = -1, run, t;
        Mob dummy;
        List<SkillDef> rotation = List.of(), ults = List.of();
        double[] dps = new double[3];
        int busy;
        boolean bow;
        final java.util.Map<String, Integer> casts = new java.util.LinkedHashMap<>();
        int swings;

        Runner(ServerLevel level, ServerPlayer p, Vec3 field, List<String> jobs, List<Result> results, Runnable done) {
            this.level = level;
            this.p = p;
            this.field = field;
            this.jobs = jobs;
            this.results = results;
            this.done = done;
        }

        void go() {
            active = this;
        }

        /** One tick; true when every job is done. */
        boolean step() {
            if (ji < 0 || run >= 3) {
                if (ji >= 0) finish();
                ji++;
                if (ji >= jobs.size()) {
                    counting = false;
                    if (dummy != null) dummy.discard();
                    done.run();
                    return true;
                }
                setup(jobs.get(ji));
                run = 0;
                t = -GAP;
                return false;
            }
            keepUp();
            if (t < 0) { // a pause between runs: lingering effects run out, cooldowns come back
                t++;
                if (t == 0) startRun();
                return false;
            }
            if (t == 0 && run > 0 && run - 1 < ults.size()) castNow(ults.get(run - 1));
            act();
            t++;
            if (t >= RUN) {
                counting = false;
                dps[run] = dealt / (RUN / 20.0);
                if (run == 0) {
                    log("  {} plain run: total {} = plain attacks {} ({} hits of {} swings) + skills {}; casts {}; mana left {}", jobs.get(ji), Math.round(dealt),
                            Math.round(basicDealt), basicHits, swings, Math.round(dealt - basicDealt), casts, Math.round(JobManager.data(p).mana()));
                }
                run++;
                t = -GAP;
                if (run >= 1 && run - 1 >= ults.size() && run < 3) { // no (second) ultimate: nothing more to try
                    for (int k = run; k < 3; k++) dps[k] = 0;
                    run = 3;
                }
            }
            return false;
        }

        void finish() {
            String id = jobs.get(ji);
            String group = JobDefs.isAdvanced(id) ? "advanced" : JobDefs.isHybrid(id) ? "hybrid" : "base";
            Result r = new Result(id, group, dps[0], ults.size() > 0 ? ults.get(0).id() : "-", dps[1], ults.size() > 1 ? ults.get(1).id() : "-", dps[2], rotation.size());
            results.add(r);
            log("{} plain {} | +{} {} | +{} {} (rotation {})", id, Math.round(r.plain()), r.ult1(), Math.round(r.withUlt1()), r.ult2(), Math.round(r.withUlt2()),
                    rotation.stream().map(SkillDef::id).toList());
        }

        void setup(String id) {
            boolean adv = JobDefs.isAdvanced(id);
            String base = adv ? JobDefs.BASE_OF.get(id) : id;
            JobData d = JobManager.data(p);
            d.switchTo(base, adv);
            d.setLevel(LEVEL);
            d.ensureAdvanced();
            d.resetPanel();
            if (adv) d.resetAdvPanel();
            // every skill cell (base panel, and the advanced one), nothing else
            for (SkillPanel.Node n : d.panel().nodes()) if (n.skill() != null) d.unlock(n.id());
            if (adv && d.panel(true) != null) for (SkillPanel.Node n : d.panel(true).nodes()) if (n.skill() != null) d.unlockAdv(n.id());
            JobType type = JobType.byId(base);
            WeaponType wt = type.weapons.get(0);
            bow = wt == WeaponType.BOW;
            p.getInventory().setItem(6, new ItemStack(ModItems.get(wt, WeaponMaterial.DIAMOND)));
            p.getInventory().setItem(7, bow ? new ItemStack(Items.ARROW, 64) : ItemStack.EMPTY);
            p.getInventory().setSelectedSlot(6);
            JobStats.apply(p, d);
            List<SkillDef> rot = new ArrayList<>(), ult = new ArrayList<>();
            for (String s : d.knownSkills()) {
                SkillDef def = JobDefs.skill(s);
                if (def == null) continue;
                if (def.kind() == SkillKind.ULTIMATE) {
                    ult.add(def);
                    continue;
                }
                if ("heal".equals(JobDefs.powerKind(def)) || def.effect() == SkillEffect.HEAL || def.effect() == SkillEffect.MANA || NOT_DAMAGE.contains(s)) continue;
                rot.add(def);
            }
            // buffs and debuffs first (they raise what follows), then the hardest hitters
            rot.sort(Comparator.comparingDouble((SkillDef s) -> JobDefs.powerKind(s) == null ? -1e9 : -s.power()));
            rotation = rot;
            ults = ult;
            dps = new double[3];
            JobManager.sync(p);
        }

        void startRun() {
            JobData d = JobManager.data(p);
            d.setMana(JobManager.maxMana(p));
            for (SkillDef s : rotation) d.setCooldown(s.id(), 0);
            for (SkillDef s : ults) d.setCooldown(s.id(), 0);
            p.removeAllEffects();
            if (dummy != null) dummy.discard();
            com.jo112ss.rpgmod.mob.MobLevels.forceLevel = LEVEL;
            com.jo112ss.rpgmod.mob.Elite.forceNext = 0;
            try {
                var m = net.minecraft.world.entity.EntityTypes.HUSK.create(level, EntitySpawnReason.COMMAND);
                m.snapTo(field.x, field.y, field.z + 3, 180, 0);
                m.setNoAi(true);
                m.setPersistenceRequired();
                m.addTag(DUMMY);
                level.addFreshEntity(m);
                for (EquipmentSlot s : EquipmentSlot.values()) m.setItemSlot(s, ItemStack.EMPTY);
                dummy = m;
            } finally {
                com.jo112ss.rpgmod.mob.MobLevels.forceLevel = null;
                com.jo112ss.rpgmod.mob.Elite.forceNext = null;
            }
            busy = 0;
            dealt = 0;
            basicDealt = 0;
            basicHits = 0;
            swings = 0;
            casts.clear();
            counting = true;
            JobManager.sync(p);
        }

        void keepUp() {
            p.setHealth(p.getMaxHealth());
            if (dummy != null && dummy.isAlive()) dummy.setHealth(dummy.getMaxHealth());
            if (dummy == null) return;
            Vec3 at = dummy.position();
            if (p.position().distanceTo(at) > 5 || p.position().distanceTo(at) < 1.5) p.teleportTo(at.x, at.y, at.z - 3);
            Vec3 d = dummy.position().add(0, dummy.getBbHeight() * 0.6, 0).subtract(p.getEyePosition());
            p.setYRot((float) Math.toDegrees(Math.atan2(-d.x, d.z)));
            p.setXRot((float) -Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z))));
            p.setYHeadRot(p.getYRot());
        }

        boolean castNow(SkillDef def) {
            JobData d = JobManager.data(p);
            long now = level.getGameTime();
            if (d.cooldownEnd(def.id()) > now || d.mana() < JobManager.manaCost(p, def)) return false;
            if (!JobManager.cast(p, d, def)) return false;
            casts.merge(def.id(), 1, Integer::sum);
            busy = Math.max(GCD, JobManager.castTicks(p, def));
            return true;
        }

        void act() {
            if (dummy == null || !dummy.isAlive()) return;
            if (busy > 0) {
                busy--;
                return;
            }
            for (SkillDef s : rotation) if (castNow(s)) return;
            if (bow) {
                if (t % 20 == 0) { // a fully drawn arrow a second
                    ItemStack b = p.getMainHandItem();
                    p.getInventory().setItem(7, new ItemStack(Items.ARROW, 64));
                    b.getItem().releaseUsing(b, level, p, b.getUseDuration(p) - 20);
                    swings++;
                }
            } else if (p.getAttackStrengthScale(0.5F) >= 0.95F) {
                p.attack(dummy);
                swings++;
                p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, p.getMainHandItem().getAttackAnimation(), true);
            }
        }
    }
}
