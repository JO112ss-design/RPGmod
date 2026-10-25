package com.jo112ss.rpgmod.devtest;

import com.jo112ss.rpgmod.RpgMod;
import com.jo112ss.rpgmod.job.JobData;
import com.jo112ss.rpgmod.job.JobManager;
import com.jo112ss.rpgmod.job.JobStats;
import com.jo112ss.rpgmod.job.SkillTasks;
import com.jo112ss.rpgmod.job.core.LevelCurve;
import com.jo112ss.rpgmod.job.core.SkillPanel;
import com.jo112ss.rpgmod.registry.ModRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;

/** Dev test for job changes, mastery and the job change book (client showcase entries "job:*"). */
final class JobChangeTests {
    private JobChangeTests() {}

    static void log(String msg, Object... args) {
        RpgMod.LOGGER.info("[AUTOTEST] jobtest " + msg, args);
    }

    static void scene(ServerLevel level, ServerPlayer p, String name) {
        switch (name) {
            case "checks" -> checks(level, p);
            case "screen" -> screen(p);
            default -> log("unknown scene {}", name);
        }
    }

    private static int books(ServerPlayer p) {
        int n = 0;
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(ModRegistries.JOB_BOOK.get())) n += inv.getItem(i).getCount();
        return n;
    }

    private static void checks(ServerLevel level, ServerPlayer p) {
        long max = LevelCurve.masteryExp();
        log("mastery for 100% = {} exp", max);

        // ---- an old save (job with its own level) becomes a character level, mastered, advanced
        CompoundTag old = new CompoundTag();
        old.putString("job", "fighter");
        old.putInt("level", 150);
        old.putInt("panel_version", SkillPanel.version("fighter"));
        JobData mig = JobData.CODEC.codec().parse(NbtOps.INSTANCE, old).getOrThrow();
        boolean changed = mig.migrateJobChange();
        log("migrate: changed={} level={} mastered={} advanced={} shown={} again={}", changed, mig.level(), mig.mastered("fighter"),
                mig.isAdvanced(), mig.displayJobId(), mig.migrateJobChange());
        JobData young = JobData.CODEC.codec().parse(NbtOps.INSTANCE, withLevel(old, 40)).getOrThrow();
        young.migrateJobChange();
        log("migrate Lv40: mastered={} advanced={}", young.mastered("fighter"), young.isAdvanced());

        // ---- first job: mastered exactly when reaching level 100
        JobData d = new JobData();
        d.setJob("fighter");
        long need99 = 0;
        for (int l = 1; l < 99; l++) need99 += LevelCurve.expToNext(l);
        d.addExp(need99, 199);
        log("first job at Lv{}: mastery {}% mastered={}", d.level(), d.masteryPercent("fighter"), d.mastered("fighter"));
        d.addExp(LevelCurve.expToNext(99), 199);
        log("first job at Lv{}: mastery {}% mastered={} advanced={} waiting adv points={}", d.level(), d.masteryPercent("fighter"), d.mastered("fighter"),
                d.isAdvanced(), d.waitingAdvPoints());
        d.unlock(1);
        d.unlock(2);

        // ---- change to the wizard: level stays, fresh panel, mastery from 0 at double speed
        d.switchTo("wizard", false);
        log("to wizard: job={} level={} unlocked={} slot0={} wizard mastery={} fighter mastery={}%", d.jobId(), d.level(), d.unlocked(), d.slot(0),
                d.mastery("wizard"), d.masteryPercent("fighter"));
        d.addExp(1000, 199);
        log("wizard after 1000 exp at Lv100+: mastery {} (x2 = 2000)", d.mastery("wizard"));
        d.setLevel(150);
        d.addExp(1000, 199);
        log("wizard Lv150 points: base {} adv waiting {} adv free {}", d.totalPoints(), d.waitingAdvPoints(), d.freePoints(true));

        // ---- back to the fighter, as the berserker: the panel comes back, advanced from the start
        d.switchTo("fighter", true);
        d.ensureAdvanced();
        log("to berserker: shown={} advanced={} unlocked={} adv unlocked={} adv points={} slot3 ok={}", d.displayJobId(), d.isAdvanced(), d.unlocked(),
                d.unlocked(true), d.totalPoints(true), d.slot(3).isEmpty());

        // ---- the save keeps it all
        var tag = JobData.CODEC.codec().encodeStart(NbtOps.INSTANCE, d).getOrThrow();
        JobData back = JobData.CODEC.codec().parse(NbtOps.INSTANCE, tag).getOrThrow();
        back.switchTo("wizard", false);
        log("round trip: berserker={} fighter mastered={} wizard mastery={} wizard panel back={}", JobData.CODEC.codec().parse(NbtOps.INSTANCE, tag).getOrThrow().isAdvanced(),
                back.mastered("fighter"), back.mastery("wizard"), back.unlocked());

        // ---- which cells need mastery (fighter panel)
        int[] counts = new int[101];
        for (SkillPanel.Node n : SkillPanel.forJob("fighter").nodes()) counts[SkillPanel.masteryNeeded(n)]++;
        log("fighter panel cells by mastery: 0%={} 25%={} 50%={} 75%={} 100%={}", counts[0], counts[25], counts[50], counts[75], counts[100]);

        // ---- on the player: books, the change, the gate, the reward, the health bonus
        JobData pd = JobManager.data(p);
        String startJob = pd.displayJobId();
        var mode = p.gameMode();
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        p.getInventory().add(new ItemStack(ModRegistries.JOB_BOOK.get(), 2));
        double hp0 = p.getMaxHealth();
        int b0 = books(p);
        log("player: {} Lv{} mastered={} books={}", startJob, pd.level(), pd.masteredCount(), b0);
        log("problems: current={} paladin={} guardian={}", JobManager.changeProblem(pd, startJob), JobManager.changeProblem(pd, "paladin"),
                JobManager.changeProblem(pd, "guardian"));
        JobManager.changeJob(p, "guardian"); // not mastered: refused, no book spent
        log("guardian refused: job={} books={}", pd.displayJobId(), books(p));
        JobManager.changeJob(p, "paladin");
        log("to paladin: job={} adv={} level={} books {} -> {} mastery={}%", pd.displayJobId(), pd.isAdvanced(), pd.level(), b0, books(p), pd.masteryPercent("paladin"));

        // gate: a ring 4 skill with no mastery is refused, with 30% it is learnt
        pd.addBonusPoints(300);
        SkillPanel panel = pd.panel();
        SkillPanel.Node ring4 = null;
        for (SkillPanel.Node n : panel.nodes()) {
            if (n.ring() <= 3) pd.unlock(n.id());
            else if (ring4 == null && n.ring() == 4 && n.type() == SkillPanel.NodeType.SKILL) ring4 = n;
        }
        if (ring4 != null) {
            JobManager.unlockNode(p, ring4.id(), false);
            boolean at0 = pd.unlocked().contains(ring4.id());
            pd.setMastery("paladin", Math.round(max * 0.3));
            JobManager.unlockNode(p, ring4.id(), false);
            log("gate: {} (needs {}%) at 0% learnt={} at 30% learnt={}", ring4.skill(), SkillPanel.masteryNeeded(ring4), at0, pd.unlocked().contains(ring4.id()));
        }

        // mastering the paladin: a book, +2% max health per mastered job
        int b1 = books(p);
        pd.setMastery("paladin", max - 10);
        JobManager.grantExp(p, 100);
        SkillTasks.later(2, () -> {
            log("mastered paladin: books {} -> {} mastered={} max health {} -> {}", b1, books(p), pd.masteredCount(), String.format("%.1f", hp0), String.format("%.1f", p.getMaxHealth()));
            JobManager.changeJob(p, "guardian");
            log("to guardian: job={} adv={} adv points={}", pd.displayJobId(), pd.isAdvanced(), pd.freePoints(true));
            // back to where the showcase started (directly: that job may not be mastered here)
            var base = JobManager.baseOf(startJob);
            pd.switchTo(base.id, com.jo112ss.rpgmod.job.JobType.byId(startJob) == null);
            pd.ensureAdvanced();
            JobStats.apply(p, pd);
            JobManager.sync(p);
            log("back: job={} books={}", pd.displayJobId(), books(p));
            p.setGameMode(mode);
        });

        // book drops: always from a blood moon boss
        var king = ModRegistries.KING_SLIME.get().create(level, EntitySpawnReason.COMMAND);
        if (king != null) {
            king.snapTo(p.getX() + 2, p.getY(), p.getZ(), 0, 0);
            for (int i = 0; i < 2; i++) net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.living.LivingDeathEvent(king, p.damageSources().playerAttack(p)));
            SkillTasks.later(3, () -> {
                int n = 0;
                for (var it : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, p.getBoundingBox().inflate(6), x -> x.getItem().is(ModRegistries.JOB_BOOK.get()))) {
                    n++;
                    it.discard();
                }
                log("boss book drops: {} from 2 king slime deaths; chance from monsters {}", n, com.jo112ss.rpgmod.item.JobBookDrops.CHANCE);
            });
        }
        JobStats.apply(p, pd);
        JobManager.sync(p);
    }

    private static CompoundTag withLevel(CompoundTag t, int level) {
        CompoundTag c = t.copy();
        c.putInt("level", level);
        return c;
    }

    /** Sets the player up for the job change screen: a book, some mastery here and there. */
    private static void screen(ServerPlayer p) {
        JobData pd = JobManager.data(p);
        pd.setMastery("fighter", LevelCurve.masteryExp());
        pd.setMastery("ranger", Math.round(LevelCurve.masteryExp() * 0.45));
        pd.setMastery("healer", Math.round(LevelCurve.masteryExp() * 0.8));
        p.getInventory().add(new ItemStack(ModRegistries.JOB_BOOK.get()));
        // the panel shot: a job half way to mastery, so the outer skills show what they need
        pd.addBonusPoints(-pd.bonusPoints());
        pd.switchTo("ranger", false);
        JobStats.apply(p, pd);
        JobManager.sync(p);
        log("screen setup: {} mastery {}% mastered={}", pd.displayJobId(), pd.masteryPercent("ranger"), pd.masteredCount());
    }
}
