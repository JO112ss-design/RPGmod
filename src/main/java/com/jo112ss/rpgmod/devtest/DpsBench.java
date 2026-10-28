package com.jo112ss.rpgmod.devtest;

import com.jo112ss.rpgmod.RpgMod;
import com.jo112ss.rpgmod.item.ModItems;
import com.jo112ss.rpgmod.item.WeaponMaterial;
import com.jo112ss.rpgmod.item.WeaponType;
import com.jo112ss.rpgmod.job.Bard;
import com.jo112ss.rpgmod.job.JobData;
import com.jo112ss.rpgmod.job.JobManager;
import com.jo112ss.rpgmod.job.JobStats;
import com.jo112ss.rpgmod.job.JobType;
import com.jo112ss.rpgmod.job.SkillTasks;
import com.jo112ss.rpgmod.job.SpellLoadout;
import com.jo112ss.rpgmod.job.core.JobDefs;
import com.jo112ss.rpgmod.job.core.SkillDef;
import com.jo112ss.rpgmod.job.core.SkillEffect;
import com.jo112ss.rpgmod.job.core.SkillKind;
import com.jo112ss.rpgmod.job.core.SkillPanel;
import com.jo112ss.rpgmod.registry.ModEffects;
import com.jo112ss.rpgmod.registry.ModRegistries;
import com.jo112ss.rpgmod.smithing.ForgedData;
import com.jo112ss.rpgmod.smithing.core.OptionType;
import com.jo112ss.rpgmod.smithing.core.Quality;
import com.jo112ss.rpgmod.smithing.core.RolledOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

/**
 * Dev test: every job's damage against one target, on equal terms - the same level, diamond weapons,
 * every skill learnt, no stat or boost cells - and what each job adds to a party of six.
 *
 * <p>Solo, each job fights a training dummy (a monster of the same level, kept alive) for
 * {@link #RUN} ticks: skills and plain attacks only, opening with each of its ultimates, and once
 * more in good forged gear ({@link #gear}). The rotation uses every attack skill, spell and buff
 * the moment it is ready (a half-second pause after each, a spell's cast time) and plain attacks in
 * between. Bards play their song perfectly.
 *
 * <p>Party: each job then fights for {@link #PARTY_RUN} ticks with a stand-in ally next to it (a
 * fake player every party skill reaches): every effect the ally is given, and every effect put on
 * the dummy, is written down tick by tick. Three reference allies (melee, bow, magic) then fight the
 * dummy with that job's effects played back onto them and the dummy; how much more they deal is
 * the job's gift to each of the five others. Mana given, cooldowns shortened, heals and shields are
 * not counted - only damage.
 *
 * <p>Damage is what reaches the dummy after its defence. The server runs at full speed (tick
 * sprint) while it lasts.
 */
@EventBusSubscriber(modid = RpgMod.MODID)
public final class DpsBench {
    private DpsBench() {}

    public static final String DUMMY = "rpgmod_dps_dummy";
    public static final int LEVEL = 130, RUN = 300, PARTY_RUN = 1200, GAP = 30, GCD = 10;
    /** The reference allies a job's party effects are played back onto: melee, bow, magic. */
    public static final List<String> ALLIES = List.of("champion", "sharpshooter", "spellmaster");
    /** Party size the gift is counted for: the job and five others. */
    public static final int PARTY = 6;

    private static boolean counting;
    private static double dealt, basicDealt;
    private static int basicHits;
    /** The skill last cast (damage that is not a plain attack is put down to it) and each skill's damage. */
    private static String lastCast;
    private static final Map<String, Long> bySkill = new LinkedHashMap<>();
    /** Ally runs: only the player's own hits count (played-back poison and the like are not theirs). */
    private static boolean onlyOwn;
    private static ServerPlayer benchPlayer;

    /** The stand-in ally while a party run is being written down (null otherwise). */
    private static FakePlayer partyAlly;
    private static boolean recording;
    private static int recTick;
    private static List<Ev> recordingTo;

    /** One effect given in a party run: on the ally, or on the dummy. */
    private record Ev(int tick, Holder<MobEffect> effect, int amp, int dur, boolean onDummy) {}

    /** The benchmark under way (driven by the server tick: skill tasks give up after a minute). */
    private static Runner active;
    private static boolean starting;

    /** Running (the client test waits for it). */
    public static boolean running() {
        return starting || active != null;
    }

    /** Party skills reach the stand-in ally too while a party run is written down. */
    public static List<ServerPlayer> withPartyAlly(List<ServerPlayer> list) {
        if (!recording || partyAlly == null || list.contains(partyAlly)) return list;
        List<ServerPlayer> out = new ArrayList<>(list);
        out.add(partyAlly);
        return out;
    }

    @SubscribeEvent
    public static void onTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        if (active == null) return;
        Runner r = active;
        boolean done;
        try {
            done = r.step();
        } catch (Exception e) {
            RpgMod.LOGGER.error("[AUTOTEST] dps: failed on {}", r.cur == null ? "?" : r.cur, e);
            recording = false;
            counting = false;
            r.cur = null; // on to the next run
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

    /** Effects that hurt by themselves are not played back (their damage would not be the ally's). */
    private static boolean damaging(Holder<MobEffect> h) {
        MobEffect e = h.value();
        return e == MobEffects.POISON.value() || e == MobEffects.WITHER.value() || e == MobEffects.INSTANT_DAMAGE.value()
                || e == ModEffects.VENOM.get() || e == ModEffects.TWIN_VENOM.get();
    }

    @SubscribeEvent
    public static void onEffect(MobEffectEvent.Added event) {
        if (!recording || recordingTo == null || event.getEffectInstance() == null) return;
        MobEffectInstance in = event.getEffectInstance();
        LivingEntity e = event.getEntity();
        if (e == partyAlly) {
            recordingTo.add(new Ev(recTick, in.getEffect(), in.getAmplifier(), in.getDuration(), false));
        } else if (e.entityTags().contains(DUMMY) && in.getEffect().value().getCategory() == MobEffectCategory.HARMFUL && !damaging(in.getEffect())) {
            recordingTo.add(new Ev(recTick, in.getEffect(), in.getAmplifier(), in.getDuration(), true));
        }
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
            "ward_rune", "chrono", "refine_qi", "modulate");

    /** The song jobs, and the songs they play here (the maestro plays a second one along). */
    public static final java.util.Set<String> SONG_JOBS = java.util.Set.of("bard", "maestro", "spellsinger");
    private static final Map<String, String[]> SONGS = Map.of(
            "bard", new String[] {"war_song", null},
            "maestro", new String[] {"war_song", "rhapsody"},
            "spellsinger", new String[] {"flame_melody", null});

    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Post event) {
        if (!counting || !event.getEntity().entityTags().contains(DUMMY)) return;
        var src = event.getSource();
        if (onlyOwn && src.getEntity() != benchPlayer) return;
        dealt += event.getInflictedDamage();
        if (src.is(net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK) || src.getDirectEntity() instanceof net.minecraft.world.entity.projectile.arrow.AbstractArrow) {
            basicDealt += event.getInflictedDamage();
            basicHits++;
        } else if (lastCast != null) {
            bySkill.merge(lastCast, (long) Math.round(event.getInflictedDamage()), Long::sum);
        }
    }

    private static void log(String fmt, Object... args) {
        RpgMod.LOGGER.info("[AUTOTEST] dps: " + fmt, args);
    }

    // ------------------------------------------------------------------ the runs

    private enum Kind { PLAIN, ULT, GEAR, PARTY, ALLY_BASE, ALLY_REPLAY }

    /** One run: the job fighting, what kind, the ultimate (ULT) or the job whose effects are played back (ALLY_REPLAY). */
    private record Spec(String job, Kind kind, int ult, String replayOf) {
        int length() {
            return kind == Kind.PLAIN || kind == Kind.ULT ? RUN : PARTY_RUN;
        }

        @Override
        public String toString() {
            return job + "/" + kind + (kind == Kind.ULT ? ult : "") + (replayOf != null ? "<-" + replayOf : "");
        }
    }

    /** What one job did. */
    private static final class Result {
        final String job, group;
        double plain, geared, sustained;
        /** As a reference ally: its own damage in the party runs, with nothing played back. */
        double allyBase;
        final List<String> ults = new ArrayList<>();
        final List<Double> withUlt = new ArrayList<>();
        int skills;
        List<Ev> party = List.of();
        final Map<String, Double> gain = new LinkedHashMap<>();
        /** This job's effects played back on each reference ally: the ally's damage per second. */
        final Map<String, Double> replayed = new LinkedHashMap<>();

        Result(String job) {
            this.job = job;
            this.group = JobDefs.isAdvanced(job) ? "advanced" : JobDefs.isHybrid(job) ? "hybrid" : "base";
        }

        double avgGain() {
            return gain.isEmpty() ? 0 : gain.values().stream().mapToDouble(Double::doubleValue).average().orElse(0);
        }
    }

    public static void start(ServerLevel level, ServerPlayer p) {
        Vec3 home = p.position();
        Vec3 field = home.add(0, 0, 260);
        p.teleportTo(field.x, field.y, field.z);
        List<String> jobs = new ArrayList<>(JobDefs.JOBS.keySet());
        String startJob = JobManager.data(p).jobId();
        boolean startAdv = JobManager.data(p).isAdvanced();
        int startLevel = JobManager.data(p).level();
        ItemStack[] armour = new ItemStack[4];
        for (int i = 0; i < 4; i++) armour[i] = p.getItemBySlot(ARMOUR[i]).copy();
        List<Spec> queue = new ArrayList<>();
        for (String j : jobs) {
            queue.add(new Spec(j, Kind.PLAIN, 0, null));
            queue.add(new Spec(j, Kind.ULT, 0, null));
            queue.add(new Spec(j, Kind.ULT, 1, null));
            queue.add(new Spec(j, Kind.GEAR, 0, null));
            queue.add(new Spec(j, Kind.PARTY, 0, null));
        }
        for (String a : ALLIES) {
            queue.add(new Spec(a, Kind.ALLY_BASE, 0, null));
            for (String j : jobs) queue.add(new Spec(a, Kind.ALLY_REPLAY, 0, j));
        }
        log("{} jobs at Lv{}, diamond weapons, every skill, no stat or boost cells; solo {}s per run, party {}s with {} as the allies; {} runs",
                jobs.size(), LEVEL, RUN / 20, PARTY_RUN / 20, ALLIES, queue.size());
        starting = true;
        benchPlayer = p;
        SkillTasks.later(40, () -> {
            com.jo112ss.rpgmod.job.fx.ServerFx.mute = true;
            level.getServer().tickRateManager().requestGameToSprint(2000000);
            partyAlly = FakePlayerFactory.getMinecraft(level);
            long t0 = System.currentTimeMillis();
            Map<String, Result> results = new LinkedHashMap<>();
            for (String j : jobs) results.put(j, new Result(j));
            new Runner(level, p, field, queue, results, () -> {
                com.jo112ss.rpgmod.job.fx.ServerFx.mute = false;
                level.getServer().tickRateManager().stopSprinting();
                recording = false;
                partyAlly = null;
                report(results);
                log("took {}s", (System.currentTimeMillis() - t0) / 1000);
                JobData d = JobManager.data(p);
                d.switchTo(startJob, startAdv);
                d.setLevel(startLevel);
                d.ensureAdvanced();
                JobStats.apply(p, d);
                JobManager.sync(p);
                for (int i = 0; i < 4; i++) p.setItemSlot(ARMOUR[i], armour[i]);
                p.teleportTo(home.x, home.y, home.z);
                starting = false;
            }).go();
        });
    }

    private static void report(Map<String, Result> results) {
        double bossHp = com.jo112ss.rpgmod.mob.MobConfig.kingHealth()
                * com.jo112ss.rpgmod.mob.core.MobLevelMath.healthMultiplier(com.jo112ss.rpgmod.mob.MobConfig.rules(), LEVEL);
        List<Result> list = new ArrayList<>(results.values());
        log("---- solo (damage per second against a Lv{} monster, {}s; then {}s without and with forged gear; boss = a Lv{} boss alone, {} health) ----",
                LEVEL, RUN / 20, PARTY_RUN / 20, LEVEL, Math.round(bossHp));
        list.sort(Comparator.comparingDouble((Result r) -> -r.plain));
        for (Result r : list) {
            StringBuilder u = new StringBuilder();
            double best = Math.max(r.plain, r.geared);
            for (int i = 0; i < r.ults.size(); i++) {
                u.append(" | +").append(r.ults.get(i)).append(' ').append(Math.round(r.withUlt.get(i)));
                best = Math.max(best, r.withUlt.get(i));
            }
            log("{} [{}] plain {}{} | {}s: {} -> geared {} ({}%) | boss {}s ({} skills)", r.job, r.group, Math.round(r.plain), u, PARTY_RUN / 20,
                    Math.round(r.sustained), Math.round(r.geared), pct(r.geared / Math.max(1, r.sustained) - 1), Math.round(bossHp / Math.max(1, best)), r.skills);
        }
        // each reference ally's own damage: its run with nothing played back, and every run with the
        // effects of a job that gives none (those are the same fight - their spread is the noise)
        double base = 0;
        int n = 0;
        StringBuilder noise = new StringBuilder();
        for (String a : ALLIES) {
            Result ar = results.get(a);
            List<Double> same = new ArrayList<>();
            if (ar != null && ar.allyBase > 0) same.add(ar.allyBase);
            for (Result r : results.values()) if (r.party.isEmpty() && r.replayed.containsKey(a)) same.add(r.replayed.get(a));
            if (same.isEmpty()) continue;
            double mean = same.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double sd = Math.sqrt(same.stream().mapToDouble(v -> (v - mean) * (v - mean)).average().orElse(0));
            noise.append(a).append(' ').append(Math.round(mean)).append(" +-").append(pct(sd / mean)).append("% (").append(same.size()).append(" runs) ");
            for (Result r : results.values()) {
                if (r.party.isEmpty()) r.gain.put(a, 0.0);
                else if (r.replayed.containsKey(a)) r.gain.put(a, r.replayed.get(a) / mean - 1);
            }
            base += mean;
            n++;
        }
        double allyDps = n == 0 ? 0 : base / n;
        log("allies on their own: {}", noise.toString().trim());
        log("---- party of {} ({}s; gift = what the job's effects add to each of the {} others, measured on {} - average ally {} DPS) ----",
                PARTY, PARTY_RUN / 20, PARTY - 1, ALLIES, Math.round(allyDps));
        list.sort(Comparator.comparingDouble((Result r) -> -(r.sustained + (PARTY - 1) * allyDps * r.avgGain())));
        for (Result r : list) {
            double gift = (PARTY - 1) * allyDps * r.avgGain();
            StringBuilder g = new StringBuilder();
            for (var e : r.gain.entrySet()) g.append(e.getKey()).append(' ').append(pct(e.getValue())).append("% ");
            java.util.Set<String> given = new java.util.TreeSet<>();
            for (Ev ev : r.party) given.add((ev.onDummy() ? "foe:" : "") + ev.effect().getRegisteredName().replace("minecraft:", "").replace("rpgmod:", "") + "/" + (ev.amp() + 1));
            log("{} [{}] own {} + gift {} = {} | allies +{}% ({}) | gives {}", r.job, r.group, Math.round(r.sustained), Math.round(gift), Math.round(r.sustained + gift),
                    pct(r.avgGain()), g.toString().trim(), given);
        }
        log("---- end ----");
    }

    private static String pct(double share) {
        return String.format("%.1f", share * 100);
    }

    private static final EquipmentSlot[] ARMOUR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final WeaponType[] METAL = {WeaponType.HELMET, WeaponType.CHESTPLATE, WeaponType.LEGGINGS, WeaponType.BOOTS};
    private static final WeaponType[] ROBE = {WeaponType.ROBE_HOOD, WeaponType.ROBE, WeaponType.ROBE_PANTS, WeaponType.ROBE_BOOTS};

    private static ItemStack forged(ItemStack stack, Object... optionsAndValues) {
        List<RolledOption> o = new ArrayList<>();
        for (int i = 0; i < optionsAndValues.length; i += 2) o.add(new RolledOption((OptionType) optionsAndValues[i], ((Number) optionsAndValues[i + 1]).doubleValue()));
        stack.set(ModRegistries.FORGED_DATA.get(), ForgedData.of(Quality.RARE, o, "bench"));
        return stack;
    }

    /**
     * Good forged gear (rare: three options, each in the middle of its range, all of them for
     * damage): a staff or lute with magic power, cast speed and MP regeneration and robes with the
     * same; any other weapon with attack, crit chance and crit damage and metal armour with the same.
     */
    private static void gear(ServerPlayer p, WeaponType weapon, boolean on) {
        ItemStack w = new ItemStack(ModItems.get(weapon, WeaponMaterial.DIAMOND));
        boolean magic = weapon.magical();
        if (on) {
            if (magic) forged(w, OptionType.MAGIC_POWER, 8.5, OptionType.CAST_SPEED, 8.5, OptionType.MANA_REGEN, 14);
            else forged(w, OptionType.ATTACK_DAMAGE, 5.5, OptionType.CRIT_CHANCE, 3.5, OptionType.CRIT_DAMAGE, 10);
        }
        ItemStack old = p.getInventory().getItem(6);
        var loadout = old.get(ModRegistries.SPELL_LOADOUT.get());
        if (loadout != null) w.set(ModRegistries.SPELL_LOADOUT.get(), loadout); // a bard's songs stay set
        p.getInventory().setItem(6, w);
        for (int i = 0; i < 4; i++) {
            if (!on) {
                p.setItemSlot(ARMOUR[i], ItemStack.EMPTY);
                continue;
            }
            ItemStack a = new ItemStack(ModItems.get(magic ? ROBE[i] : METAL[i], WeaponMaterial.DIAMOND));
            if (magic) forged(a, OptionType.MAGIC_POWER, 3.5, OptionType.CAST_SPEED, 3.5, OptionType.MANA_REGEN, 7);
            else forged(a, OptionType.ATTACK_DAMAGE, 3.5, OptionType.CRIT_CHANCE, 2, OptionType.CRIT_DAMAGE, 5.5);
            p.setItemSlot(ARMOUR[i], a);
        }
    }

    /** Works through the runs one tick at a time. */
    private static final class Runner {
        final ServerLevel level;
        final ServerPlayer p;
        final Vec3 field;
        final List<Spec> queue;
        final Map<String, Result> results;
        final Runnable done;
        int qi = -1, t;
        Spec cur;
        String curJob;
        WeaponType weapon;
        Mob dummy;
        List<SkillDef> rotation = List.of(), ults = List.of();
        int busy;
        boolean bow;
        final Map<String, Integer> casts = new LinkedHashMap<>();
        int swings;
        Map<Integer, List<Ev>> replay = Map.of();

        Runner(ServerLevel level, ServerPlayer p, Vec3 field, List<Spec> queue, Map<String, Result> results, Runnable done) {
            this.level = level;
            this.p = p;
            this.field = field;
            this.queue = queue;
            this.results = results;
            this.done = done;
        }

        void go() {
            active = this;
        }

        /** One tick; true when every run is done. */
        boolean step() {
            if (cur == null) {
                qi++;
                if (qi >= queue.size()) {
                    counting = false;
                    onlyOwn = false;
                    if (dummy != null) dummy.discard();
                    gear(p, weapon == null ? WeaponType.SWORD : weapon, false);
                    done.run();
                    return true;
                }
                cur = queue.get(qi);
                if (!cur.job().equals(curJob)) {
                    setup(cur.job());
                    curJob = cur.job();
                }
                if (cur.kind() == Kind.ULT && cur.ult() >= ults.size()) { // no (second) ultimate
                    cur = null;
                    return false;
                }
                gear(p, weapon, cur.kind() == Kind.GEAR);
                replay = Map.of();
                if (cur.kind() == Kind.ALLY_REPLAY) {
                    Result of = results.get(cur.replayOf());
                    Map<Integer, List<Ev>> m = new HashMap<>();
                    if (of != null) for (Ev ev : of.party) m.computeIfAbsent(ev.tick(), k -> new ArrayList<>()).add(ev);
                    replay = m;
                }
                t = -GAP;
                return false;
            }
            keepUp();
            if (t < 0) { // a pause between runs: lingering effects run out, cooldowns come back
                t++;
                if (t == 0) startRun();
                return false;
            }
            recTick = t;
            List<Ev> now = replay.get(t);
            if (now != null) {
                for (Ev ev : now) {
                    LivingEntity to = ev.onDummy() ? dummy : p;
                    if (to != null) to.addEffect(new MobEffectInstance(ev.effect(), ev.dur(), ev.amp()));
                }
            }
            if (t == 0 && cur.kind() == Kind.ULT) castNow(ults.get(cur.ult()));
            if (Bard.performing(p)) Bard.judge(p, Bard.PERFECT); // a perfect player (one judgement a note at most)
            act();
            t++;
            if (recording && partyAlly != null) partyAlly.removeAllEffects(); // every new effect shows up as added
            if (t >= cur.length()) end();
            return false;
        }

        void end() {
            counting = false;
            boolean wasRecording = recording;
            recording = false;
            double dps = dealt / (cur.length() / 20.0);
            Result r = results.get(cur.job());
            switch (cur.kind()) {
                case PLAIN -> {
                    r.plain = dps;
                    r.skills = rotation.size();
                    log("  {} plain run: total {} = plain attacks {} ({} hits of {} swings) + skills {}; casts {}; mana left {}", cur.job(), Math.round(dealt),
                            Math.round(basicDealt), basicHits, swings, Math.round(dealt - basicDealt), casts, Math.round(JobManager.data(p).mana()));
                    log("  {} skill damage {}; health left {} of {}", cur.job(), bySkill, Math.round(p.getHealth()), Math.round(p.getMaxHealth()));
                }
                case ULT -> {
                    r.ults.add(ults.get(cur.ult()).id());
                    r.withUlt.add(dps);
                    log("  {} with {}: {} ; casts {}; health left {} of {}", cur.job(), ults.get(cur.ult()).id(), Math.round(dps), casts, Math.round(p.getHealth()), Math.round(p.getMaxHealth()));
                }
                case GEAR -> {
                    r.geared = dps;
                    log("  {} geared: {} (plain {}), attack {} magic {}", cur.job(), Math.round(dps), Math.round(r.plain),
                            String.format("%.1f", JobManager.physicalAttack(p)), String.format("%.1f", JobManager.magicAttack(p)));
                }
                case PARTY -> {
                    r.sustained = dps;
                    if (wasRecording && recordingTo != null) r.party = List.copyOf(recordingTo);
                    recordingTo = null;
                    log("  {} party run: own {} DPS, {} effects given ({} on the dummy); casts {}", cur.job(), Math.round(dps), r.party.size(),
                            r.party.stream().filter(Ev::onDummy).count(), casts);
                }
                case ALLY_BASE -> {
                    r.allyBase = dps;
                    log("  ally {} on its own: {} DPS", cur.job(), Math.round(dps));
                }
                case ALLY_REPLAY -> {
                    Result of = results.get(cur.replayOf());
                    if (of != null) of.replayed.put(cur.job(), dps);
                }
            }
            cur = null;
        }

        void setup(String id) {
            boolean adv = JobDefs.isAdvanced(id);
            boolean hybrid = JobDefs.isHybrid(id);
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
            weapon = type.weapons.get(0);
            bow = weapon == WeaponType.BOW;
            ItemStack w = new ItemStack(ModItems.get(weapon, WeaponMaterial.DIAMOND));
            String[] songs = SONGS.get(id);
            if (songs != null) { // the song(s) on the lute; "perform" plays them
                List<String> s = new ArrayList<>(List.of("", "", "", "", "", "", "", ""));
                s.set(0, songs[0]);
                if (songs[1] != null) s.set(SpellLoadout.BASE_SLOTS, songs[1]);
                w.set(ModRegistries.SPELL_LOADOUT.get(), new SpellLoadout(s, 0, SpellLoadout.BASE_SLOTS));
            }
            p.getInventory().setItem(6, w);
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
                if (Bard.isSong(s)) continue; // set on the lute, not cast
                if ("heal".equals(JobDefs.powerKind(def)) || def.effect() == SkillEffect.HEAL || def.effect() == SkillEffect.MANA || NOT_DAMAGE.contains(s)) continue;
                rot.add(def);
            }
            // buffs and debuffs first (they raise what follows), then the hardest hitters
            rot.sort(Comparator.comparingDouble((SkillDef s) -> JobDefs.powerKind(s) == null ? -1e9 : -s.power()));
            if (adv && d.panel(true) != null) { // the advanced job's own ultimate first
                java.util.Set<String> own = new java.util.HashSet<>();
                for (SkillPanel.Node n : d.panel(true).nodes()) if (n.skill() != null) own.add(n.skill());
                ult.sort(Comparator.comparingInt((SkillDef s) -> own.contains(s.id()) ? 0 : 1));
            }
            rotation = rot;
            ults = ult;
            JobManager.sync(p);
            if (hybrid) log("  {} (hybrid) rotation {}", id, rot.stream().map(SkillDef::id).toList());
        }

        void startRun() {
            JobData d = JobManager.data(p);
            Bard.stop(p);
            d.setMana(JobManager.maxMana(p));
            for (SkillDef s : rotation) d.setCooldown(s.id(), 0);
            for (SkillDef s : ults) d.setCooldown(s.id(), 0);
            d.setCooldown("perform", 0);
            d.setCooldown("spell_perform", 0);
            d.setCooldown(JobManager.BLOOD_KEY, 0);
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
            lastCast = null;
            bySkill.clear();
            dealt = 0;
            basicDealt = 0;
            basicHits = 0;
            swings = 0;
            p.setHealth(p.getMaxHealth());
            casts.clear();
            onlyOwn = cur.kind() == Kind.ALLY_BASE || cur.kind() == Kind.ALLY_REPLAY;
            if (cur.kind() == Kind.PARTY && partyAlly != null) {
                partyAlly.removeAllEffects();
                recordingTo = new ArrayList<>();
                recTick = 0;
                recording = true;
            }
            counting = true;
            JobManager.sync(p);
        }

        void keepUp() {
            // health is not refilled during a run: what blood frenzy and the blood price take stays taken
            // (the dummy does not fight back), so asura sees it; it is refilled between runs
            if (!counting) p.setHealth(p.getMaxHealth());
            if (dummy != null && dummy.isAlive()) dummy.setHealth(dummy.getMaxHealth());
            if (dummy == null) return;
            Vec3 home = new Vec3(field.x, field.y, field.z + 3);
            if (dummy.position().distanceTo(home) > 0.3) { // knocked back: a player would follow, so the fight stays in reach
                dummy.teleportTo(home.x, home.y, home.z);
                dummy.setDeltaMovement(Vec3.ZERO);
            }
            Vec3 at = dummy.position();
            double dist = p.position().distanceTo(at);
            if ((dist > 3.2 || dist < 1.5) && busy <= 0) p.teleportTo(at.x, at.y, at.z - 2.5);
            Vec3 d = dummy.position().add(0, dummy.getBbHeight() * 0.6, 0).subtract(p.getEyePosition());
            p.setYRot((float) Math.toDegrees(Math.atan2(-d.x, d.z)));
            p.setXRot((float) -Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z))));
            p.setYHeadRot(p.getYRot());
        }

        boolean castNow(SkillDef def) {
            JobData d = JobManager.data(p);
            long now = level.getGameTime();
            double cost = JobManager.manaCost(p, def);
            if (d.cooldownEnd(def.id()) > now || d.mana() < cost && !JobManager.canPayBlood(p, d, cost)) return false;
            String before = lastCast;
            lastCast = def.id(); // set first: most skills hit inside the cast
            if (!JobManager.cast(p, d, def)) {
                lastCast = before;
                return false;
            }
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
            for (SkillDef s : rotation) {
                // a song keeps playing: "perform" again only once it is over
                if ((s.id().equals("perform") || s.id().equals("spell_perform")) && Bard.performing(p)) continue;
                if (castNow(s)) return;
            }
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
