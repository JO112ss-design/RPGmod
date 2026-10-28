package com.jo112ss.rpgmod.job;

import com.jo112ss.rpgmod.job.core.JobDefs;
import com.jo112ss.rpgmod.job.core.LevelCurve;
import com.jo112ss.rpgmod.job.core.SkillDef;
import com.jo112ss.rpgmod.job.core.SkillKind;
import com.jo112ss.rpgmod.job.core.SkillPanel;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * A player's job progress, attached to the player (saved with the player and copied on death).
 * Mutated only on the server; the owner receives a copy through attachment sync.
 */
public final class JobData {
    private String job = "";
    private int level = 1;
    private long exp;
    private final Set<Integer> unlocked = new TreeSet<>();
    /** Cells owned on the advanced job's panel (paid with advanced points only). */
    private final Set<Integer> advUnlocked = new TreeSet<>();
    private int advPanelVersion = SkillPanel.ADV_VERSION;
    /** Equipped skill ids: 0 = skill 1, 1 = skill 2, 2 = ultimate, 3 = skill 3 (advanced job). Empty string = none. */
    private final String[] slots = {"", "", "", ""};
    public static final int SLOTS = 4;
    private double mana = JobDefs.BASE_MANA;
    /** Skill id -> game time when it is ready again. */
    private final Map<String, Long> cooldowns = new HashMap<>();
    /** Skill panel layout version the unlocked ids belong to. */
    private int panelVersion = SkillPanel.VERSION;
    /** Assassin weapon coating: 0 = attack down, 1 = armour break, 2 = poison. */
    private int coatMode;
    /** Out of combat long enough for faster mana regeneration (not saved, only synced for the HUD). */
    private boolean resting;
    /** Extra skill points from skill books; kept when the job changes. */
    private int bonusPoints;
    /** The advanced job of the current base job was chosen (with a job change book). */
    private boolean advanced;
    /** Base job id -> experience earned while it was the job (capped at {@link LevelCurve#masteryExp()}). */
    private final Map<String, Long> mastery = new HashMap<>();
    /** Jobs whose first full mastery has been rewarded (a job change book). */
    private final Set<String> masteryRewarded = new TreeSet<>();
    /** Jobs whose weapons have been handed out once. */
    private final Set<String> kitGiven = new TreeSet<>();
    /** Panels of the jobs not in use, kept for when the player comes back to them. */
    private final Map<String, Saved> saved = new HashMap<>();
    /** Monk's ki (0..{@link #MAX_KI}); not saved, synced for the HUD. */
    private int ki;
    /** Gains towards the next point of ki (0..{@link com.jo112ss.rpgmod.job.Monk#PARTS}-1); not saved, synced for the HUD. */
    private int kiParts;
    public static final int MAX_KI = 5;
    /** The champion holds more ki. */
    public static final int CHAMPION_KI = 7;
    /** Rounds in the pirate's gun; not saved (a full gun after relogging), synced for the HUD. */
    private int ammo = MAG;
    public static final int MAG = 6;

    /** Jobs with the gun, ki, the lute's songs, the spear's tip (base jobs and the hybrids built on them). */
    public boolean gunJob() {
        return "pirate".equals(job) || "treasure_hunter".equals(job);
    }

    public boolean kiJob() {
        return "monk".equals(job) || "qi_master".equals(job);
    }

    public boolean songJob() {
        return "bard".equals(job) || "spellsinger".equals(job);
    }

    public boolean spearJob() {
        return "lancer".equals(job) || "sentinel".equals(job);
    }
    /** Game time the gun is loaded again (0 = not reloading). */
    private long reloadEnd;
    /** Bard: the song being played ("" = none), when it started and ends (game time), and how well it is played (0..20). */
    private String song = "";
    /** The maestro's second song playing along ("" = none). */
    private String song2 = "";
    private long songStart, songEnd;
    private int songPoints;
    public static final int MAX_SONG_POINTS = 20;
    /** Save format: 0 = before job changes (the job had its own level), 1 = character level + mastery. */
    private int dataVersion = DATA_VERSION;
    public static final int DATA_VERSION = 1;

    /** A job's panels and skill slots while another job is in use. */
    public record Saved(List<Integer> unlocked, int version, List<String> slots, List<Integer> advUnlocked, int advVersion) {
        public static final Codec<Saved> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.listOf().fieldOf("unlocked").forGetter(Saved::unlocked),
                Codec.INT.fieldOf("version").forGetter(Saved::version),
                Codec.STRING.listOf().fieldOf("slots").forGetter(Saved::slots),
                Codec.INT.listOf().optionalFieldOf("adv_unlocked", List.of()).forGetter(Saved::advUnlocked),
                Codec.INT.optionalFieldOf("adv_version", 0).forGetter(Saved::advVersion)
        ).apply(i, Saved::new));
    }

    /** Everything about job changes, saved as one field (the main codec is at its field limit). */
    private record Change(boolean advanced, Map<String, Long> mastery, List<String> rewarded, List<String> kits, Map<String, Saved> saved, int version) {
        static final Codec<Change> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.optionalFieldOf("advanced", false).forGetter(Change::advanced),
                Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("mastery", Map.of()).forGetter(Change::mastery),
                Codec.STRING.listOf().optionalFieldOf("rewarded", List.of()).forGetter(Change::rewarded),
                Codec.STRING.listOf().optionalFieldOf("kits", List.of()).forGetter(Change::kits),
                Codec.unboundedMap(Codec.STRING, Saved.CODEC).optionalFieldOf("saved", Map.of()).forGetter(Change::saved),
                Codec.INT.optionalFieldOf("version", 0).forGetter(Change::version)
        ).apply(i, Change::new));
        static final Change OLD = new Change(false, Map.of(), List.of(), List.of(), Map.of(), 0);
    }

    private Change change() {
        return new Change(advanced, Map.copyOf(mastery), List.copyOf(masteryRewarded), List.copyOf(kitGiven), Map.copyOf(saved), dataVersion);
    }

    public static final MapCodec<JobData> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.optionalFieldOf("job", "").forGetter(d -> d.job),
            Codec.INT.optionalFieldOf("level", 1).forGetter(d -> d.level),
            Codec.LONG.optionalFieldOf("exp", 0L).forGetter(d -> d.exp),
            Codec.INT.listOf().optionalFieldOf("unlocked", List.of()).forGetter(d -> List.copyOf(d.unlocked)),
            Codec.STRING.listOf().optionalFieldOf("slots", List.of("", "", "", "")).forGetter(d -> List.of(d.slots)),
            Codec.DOUBLE.optionalFieldOf("mana", JobDefs.BASE_MANA).forGetter(d -> d.mana),
            Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("cooldowns", Map.of()).forGetter(d -> Map.copyOf(d.cooldowns)),
            Codec.INT.optionalFieldOf("panel_version", 1).forGetter(d -> d.panelVersion),
            Codec.INT.optionalFieldOf("coat_mode", 0).forGetter(d -> d.coatMode),
            Codec.INT.optionalFieldOf("bonus_points", 0).forGetter(d -> d.bonusPoints),
            Codec.INT.listOf().optionalFieldOf("adv_unlocked", List.of()).forGetter(d -> List.copyOf(d.advUnlocked)),
            Codec.INT.optionalFieldOf("adv_panel_version", SkillPanel.ADV_VERSION).forGetter(d -> d.advPanelVersion),
            Change.CODEC.optionalFieldOf("change", Change.OLD).forGetter(JobData::change)
    ).apply(i, JobData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, JobData> STREAM_CODEC = StreamCodec.of(
            (buf, d) -> {
                buf.writeUtf(d.job);
                buf.writeVarInt(d.level);
                buf.writeVarLong(d.exp);
                buf.writeVarInt(d.unlocked.size());
                for (int id : d.unlocked) buf.writeVarInt(id);
                buf.writeVarInt(d.advUnlocked.size());
                for (int id : d.advUnlocked) buf.writeVarInt(id);
                for (String s : d.slots) buf.writeUtf(s);
                buf.writeDouble(d.mana);
                buf.writeVarInt(d.cooldowns.size());
                d.cooldowns.forEach((k, v) -> {
                    buf.writeUtf(k);
                    buf.writeVarLong(v);
                });
                buf.writeVarInt(d.coatMode);
                buf.writeBoolean(d.resting);
                buf.writeVarInt(d.bonusPoints);
                buf.writeBoolean(d.advanced);
                buf.writeVarInt(d.mastery.size());
                d.mastery.forEach((k, v) -> {
                    buf.writeUtf(k);
                    buf.writeVarLong(v);
                });
                buf.writeVarInt(d.ki);
                buf.writeVarInt(d.kiParts);
                buf.writeVarInt(d.ammo);
                buf.writeVarLong(d.reloadEnd);
                buf.writeUtf(d.song);
                buf.writeUtf(d.song2);
                buf.writeVarLong(d.songStart);
                buf.writeVarLong(d.songEnd);
                buf.writeVarInt(d.songPoints);
            },
            buf -> {
                JobData d = new JobData();
                d.job = buf.readUtf();
                d.level = buf.readVarInt();
                d.exp = buf.readVarLong();
                int n = Math.min(buf.readVarInt(), 256);
                for (int k = 0; k < n; k++) d.unlocked.add(buf.readVarInt());
                int an = Math.min(buf.readVarInt(), 256);
                for (int k = 0; k < an; k++) d.advUnlocked.add(buf.readVarInt());
                for (int k = 0; k < SLOTS; k++) d.slots[k] = buf.readUtf();
                d.mana = buf.readDouble();
                int c = Math.min(buf.readVarInt(), 256);
                for (int k = 0; k < c; k++) d.cooldowns.put(buf.readUtf(), buf.readVarLong());
                d.coatMode = buf.readVarInt();
                d.resting = buf.readBoolean();
                d.bonusPoints = buf.readVarInt();
                d.advanced = buf.readBoolean();
                int m = Math.min(buf.readVarInt(), 64);
                for (int k = 0; k < m; k++) d.mastery.put(buf.readUtf(), buf.readVarLong());
                d.ki = buf.readVarInt();
                d.kiParts = buf.readVarInt();
                d.ammo = buf.readVarInt();
                d.reloadEnd = buf.readVarLong();
                d.song = buf.readUtf();
                d.song2 = buf.readUtf();
                d.songStart = buf.readVarLong();
                d.songEnd = buf.readVarLong();
                d.songPoints = buf.readVarInt();
                return d;
            });

    public JobData() {}

    /** Skills that were replaced by others in the same panel cell: equipped ones carry over. */
    private static final Map<String, String> RENAMED = Map.of("flame_wheel", "mana_meditate", "frost_blade", "mana_share");

    private static String renamed(String id) {
        return RENAMED.getOrDefault(id, id);
    }

    private JobData(String job, int level, long exp, List<Integer> unlocked, List<String> slots, double mana, Map<String, Long> cooldowns, int panelVersion, int coatMode, int bonusPoints,
                    List<Integer> advUnlocked, int advPanelVersion, Change change) {
        this.advanced = change.advanced();
        this.mastery.putAll(change.mastery());
        this.masteryRewarded.addAll(change.rewarded());
        this.kitGiven.addAll(change.kits());
        this.saved.putAll(change.saved());
        this.dataVersion = change.version();
        this.advUnlocked.addAll(advUnlocked);
        this.advPanelVersion = advPanelVersion;
        this.bonusPoints = Math.max(0, bonusPoints);
        this.panelVersion = panelVersion;
        this.coatMode = coatMode;
        this.job = job;
        this.level = Math.max(1, level);
        this.exp = Math.max(0, exp);
        this.unlocked.addAll(unlocked);
        for (int k = 0; k < SLOTS && k < slots.size(); k++) this.slots[k] = renamed(slots.get(k));
        this.mana = mana;
        this.cooldowns.putAll(cooldowns);
    }

    // ------------------------------------------------------------------ queries

    public boolean hasJob() {
        return !job.isEmpty() && JobType.byId(job) != null;
    }

    public JobType jobType() {
        return JobType.byId(job);
    }

    public String jobId() {
        return job;
    }

    public JobDefs.Job jobDef() {
        return JobDefs.job(job);
    }

    public SkillPanel panel() {
        return hasJob() ? SkillPanel.forJob(job) : null;
    }

    public int level() {
        return level;
    }

    public long exp() {
        return exp;
    }

    public long expToNext() {
        return LevelCurve.expToNext(level);
    }

    public Set<Integer> unlocked() {
        return java.util.Collections.unmodifiableSet(unlocked);
    }

    /** Base panel points: one per level up to 100 (a hybrid job: every level), plus skill books. */
    public int totalPoints() {
        if (isHybrid()) return LevelCurve.pointsForLevel(level) + bonusPoints;
        return LevelCurve.pointsForLevel(Math.min(level, LevelCurve.DEFAULT_MAX_LEVEL)) + bonusPoints;
    }

    /** A hybrid job: one panel for all its points, no mastery of its own, no advanced job. */
    public boolean isHybrid() {
        return hasJob() && JobDefs.isHybrid(job);
    }

    /** The third active slot: advanced and hybrid jobs. */
    public boolean hasSlot3() {
        return isAdvanced() || isHybrid();
    }

    // ---- advanced job (level 100+)

    /** True once the advanced job has been chosen (level 100+, the base job mastered). */
    public boolean isAdvanced() {
        return hasJob() && advanced && level >= LevelCurve.ADVANCED_AT && JobDefs.advancedOf(job) != null;
    }

    /** Advanced points earned (level 100+) but waiting for an advanced job to spend them on. */
    public int waitingAdvPoints() {
        return hasJob() && !isAdvanced() && !isHybrid() ? LevelCurve.advancedLevel(level) : 0;
    }

    // ---- mastery (per base job)

    public long mastery(String jobId) {
        return Math.min(LevelCurve.masteryExp(), mastery.getOrDefault(jobId, 0L));
    }

    /** 0..1 */
    public double masteryFraction(String jobId) {
        return mastery(jobId) / (double) LevelCurve.masteryExp();
    }

    /** Mastery in whole percent (rounded down, so 100 only when full). */
    public int masteryPercent(String jobId) {
        return mastered(jobId) ? 100 : Math.min(99, (int) Math.floor(masteryFraction(jobId) * 100));
    }

    public boolean mastered(String jobId) {
        return mastery(jobId) >= LevelCurve.masteryExp();
    }

    /** Number of base jobs mastered (each one adds a little max health). */
    public int masteredCount() {
        int n = 0;
        for (JobType t : JobType.BASE) if (mastered(t.id)) n++;
        return n;
    }

    /** True the first time a job reaches full mastery (marks it so it pays out once). */
    public boolean claimMasteryReward(String jobId) {
        return mastered(jobId) && masteryRewarded.add(jobId);
    }

    /** True the first time this job's weapons are handed out (marks it). */
    public boolean claimKit(String jobId) {
        return kitGiven.add(jobId);
    }

    /** Commands and tests: become (or leave) the advanced job directly, without a book. */
    public void setAdvanced(boolean adv) {
        advanced = adv;
    }

    public void setMastery(String jobId, long amount) {
        mastery.put(jobId, Math.max(0, Math.min(LevelCurve.masteryExp(), amount)));
    }

    public JobDefs.Job advancedDef() {
        return hasJob() ? JobDefs.advancedOf(job) : null;
    }

    /** True for the advanced job with this id. */
    public boolean isAdvancedAs(String advancedId) {
        return isAdvanced() && advancedDef().id().equals(advancedId);
    }

    /** Staff slots this caster can fill: four (right hand), eight for the spellmaster (four per hand). */
    public int spellSlots() {
        // the spellmaster's two hands, the maestro's two songs at once
        return isAdvancedAs("spellmaster") || isAdvancedAs("maestro") ? SpellLoadout.SLOTS : SpellLoadout.BASE_SLOTS;
    }

    /** Can this skill be set on the staff and charged? (spells) */
    public boolean castable(SkillDef def) {
        return def != null && def.kind() == SkillKind.SPELL;
    }

    /** Casts spells from a staff: the wizard, and advanced jobs with spells of their own (bishop). */
    public boolean castsSpells() {
        if (!hasJob()) return false;
        if (jobDef().usesSpells()) return true;
        return isAdvanced() && advancedDef().usesSpells();
    }

    /** Advanced job level, 1-100 (0 before the advanced job). */
    public int advLevel() {
        return isAdvanced() ? LevelCurve.advancedLevel(level) : 0;
    }

    /** The job shown to players: the advanced job once reached. */
    public String displayJobId() {
        return isAdvanced() ? advancedDef().id() : job;
    }

    /** Level shown with the job name: the advanced level once reached. */
    public int displayLevel() {
        return isAdvanced() ? advLevel() : level;
    }

    /** What this player's boost cells (both panels) add to a skill: a share, 0.15 = 15%. */
    public double skillBoost(String skill, com.jo112ss.rpgmod.job.core.SkillBoost kind) {
        double sum = 0;
        SkillPanel p = panel();
        if (p != null) sum += p.boost(unlocked(), skill, kind);
        SkillPanel ap = panel(true);
        if (ap != null) sum += ap.boost(advUnlocked, skill, kind);
        return sum;
    }

    public SkillPanel panel(boolean adv) {
        if (!adv) return panel();
        return isAdvanced() ? SkillPanel.forJob(advancedDef().id()) : null;
    }

    public Set<Integer> unlocked(boolean adv) {
        return adv ? java.util.Collections.unmodifiableSet(advUnlocked) : unlocked();
    }

    public int totalPoints(boolean adv) {
        return adv ? advLevel() : totalPoints();
    }

    public int spentPoints(boolean adv) {
        if (!adv) return spentPoints();
        SkillPanel p = panel(true);
        return p == null ? 0 : p.spent(advUnlocked);
    }

    public int freePoints(boolean adv) {
        return totalPoints(adv) - spentPoints(adv);
    }

    /** Gives the advanced panel its centre cell; true the first time (the promotion). */
    public boolean ensureAdvanced() {
        if (!isAdvanced() || advUnlocked.contains(0)) return false;
        advUnlocked.add(0);
        advPanelVersion = SkillPanel.advVersion(job);
        return true;
    }

    public boolean advPanelOutdated() {
        return isAdvanced() && advPanelVersion != SkillPanel.advVersion(job);
    }

    /** Back to only the advanced centre cell (advanced points refunded). */
    public void resetAdvPanel() {
        advUnlocked.clear();
        advPanelVersion = SkillPanel.advVersion(job);
        if (isAdvanced()) advUnlocked.add(0);
        dropUnknownSlots();
    }

    public void unlockAdv(int nodeId) {
        advUnlocked.add(nodeId);
    }

    /** Empties slots holding skills that are no longer learnt (after a reset). */
    public void dropUnknownSlots() {
        List<String> known = knownSkills();
        for (int k = 0; k < SLOTS; k++) {
            if (slots[k].isEmpty()) continue;
            SkillDef def = JobDefs.skill(slots[k]);
            // spells live on the staff, not in skill slots
            if (!known.contains(slots[k]) || def == null || def.kind() == SkillKind.SPELL) slots[k] = "";
        }
        if (!hasSlot3()) slots[3] = "";
    }

    public int bonusPoints() {
        return bonusPoints;
    }

    public void addBonusPoints(int n) {
        bonusPoints = Math.max(0, bonusPoints + n);
    }

    public int spentPoints() {
        SkillPanel p = panel();
        return p == null ? 0 : p.spent(unlocked);
    }

    public int freePoints() {
        return totalPoints() - spentPoints();
    }

    public String slot(int i) {
        return slots[i];
    }

    public double mana() {
        return mana;
    }

    public long cooldownEnd(String skill) {
        return cooldowns.getOrDefault(skill, 0L);
    }

    /** Skills (of any kind) the player has learnt: from the start node and unlocked panel nodes. */
    public List<String> knownSkills() {
        Set<String> out = new LinkedHashSet<>();
        JobDefs.Job def = jobDef();
        if (def == null) return List.of();
        if (!def.actives().isEmpty()) out.add(def.actives().get(0));
        if (def.usesSpells()) out.add(def.spells().get(0));
        SkillPanel p = panel();
        for (int id : unlocked) {
            SkillPanel.Node n = p.node(id);
            if (n != null && n.skill() != null) out.add(n.skill());
        }
        SkillPanel ap = panel(true);
        if (ap != null) {
            for (int id : advUnlocked) {
                SkillPanel.Node n = ap.node(id);
                if (n != null && n.skill() != null) out.add(n.skill());
            }
        }
        return new ArrayList<>(out);
    }

    public List<String> knownSkills(SkillKind kind) {
        List<String> out = new ArrayList<>();
        for (String s : knownSkills()) {
            SkillDef d = JobDefs.skill(s);
            if (d != null && d.kind() == kind) out.add(s);
        }
        return out;
    }

    public boolean knows(String skill) {
        return knownSkills().contains(skill);
    }

    // ------------------------------------------------------------------ mutation (server only)

    /** Sets the job and resets all progress (level, mastery, saved panels); the centre node is owned from the start. */
    public void setJob(String id) {
        job = id == null ? "" : id;
        level = 1;
        exp = 0;
        advanced = false;
        mastery.clear();
        masteryRewarded.clear();
        kitGiven.clear();
        saved.clear();
        dataVersion = DATA_VERSION;
        unlocked.clear();
        advUnlocked.clear();
        cooldowns.clear();
        panelVersion = SkillPanel.version(job);
        advPanelVersion = SkillPanel.advVersion(job);
        slots[0] = slots[1] = slots[2] = slots[3] = "";
        if (hasJob()) {
            unlocked.add(0);
            JobDefs.Job def = jobDef();
            if (!def.actives().isEmpty()) slots[0] = def.actives().get(0);
        }
    }

    public void setLevel(int level) {
        this.level = Math.max(1, level);
        this.exp = 0;
    }

    /** Adds experience (and the current job's mastery); returns the number of levels gained. */
    public int addExp(long amount, int maxLevel) {
        if (!hasJob() || amount <= 0) return 0;
        long cap = LevelCurve.masteryExp();
        long m = mastery.getOrDefault(job, 0L);
        if (m < cap && !isHybrid()) mastery.put(job, Math.min(cap, m + amount * LevelCurve.masteryRate(level)));
        int gained = 0;
        exp += amount;
        while (level < maxLevel && exp >= expToNext()) {
            exp -= expToNext();
            level++;
            gained++;
        }
        if (level >= maxLevel) exp = 0;
        return gained;
    }

    public boolean resting() {
        return resting;
    }

    public void setResting(boolean resting) {
        this.resting = resting;
    }

    public int ki() {
        return ki;
    }

    public void setKi(int ki) {
        this.ki = Math.max(0, Math.min(maxKi(), ki));
    }

    public int kiParts() {
        return kiParts;
    }

    public void setKiParts(int parts) {
        this.kiParts = Math.max(0, parts);
    }

    public int maxKi() {
        return isAdvancedAs("champion") ? CHAMPION_KI : MAX_KI;
    }

    public int ammo() {
        return ammo;
    }

    public void setAmmo(int ammo) {
        this.ammo = Math.max(0, Math.min(MAG, ammo));
    }

    public long reloadEnd() {
        return reloadEnd;
    }

    public void setReloadEnd(long at) {
        this.reloadEnd = at;
    }

    public String song() {
        return song;
    }

    public String song2() {
        return song2;
    }

    public void setSong2(String song) {
        this.song2 = song == null ? "" : song;
    }

    public long songStart() {
        return songStart;
    }

    public long songEnd() {
        return songEnd;
    }

    public int songPoints() {
        return songPoints;
    }

    public void setSong(String song, long start, long end) {
        this.song = song == null ? "" : song;
        this.songStart = start;
        this.songEnd = end;
    }

    public void setSongPoints(int points) {
        this.songPoints = Math.max(0, Math.min(MAX_SONG_POINTS, points));
    }

    public int coatMode() {
        return coatMode;
    }

    public void setCoatMode(int mode) {
        coatMode = Math.floorMod(mode, 3);
    }

    /** Remaining cooldowns of every skill except {@code except} are multiplied by {@code factor}. */
    /** Takes {@code ticks} off every running cooldown (the bard's fanfare). */
    public void shiftCooldowns(long now, long ticks) {
        cooldowns.replaceAll((k, v) -> k.startsWith("#") || v <= now ? v : Math.max(now, v - ticks)); // "#..." are not skills
    }

    /** Dev test only: every cell of both panels learnt. */
    public void devUnlockAll() {
        SkillPanel p = panel();
        if (p != null) for (var n : p.nodes()) if (!unlocked.contains(n.id())) unlocked.add(n.id());
        SkillPanel ap = panel(true);
        if (ap != null) for (var n : ap.nodes()) if (!advUnlocked.contains(n.id())) advUnlocked.add(n.id());
    }

    public void scaleCooldowns(long now, double factor, String except) {
        cooldowns.replaceAll((k, v) -> k.equals(except) || k.startsWith("#") || v <= now ? v : now + Math.round((v - now) * factor));
    }

    /**
     * Saves from before panel version 5 used a level cap of 50; levels are doubled so a character
     * keeps its place on the road to the (now 100) cap.
     */
    public boolean migrateLevelCap(int maxLevel) {
        if (!hasJob() || panelVersion >= 5) return false;
        level = Math.min(maxLevel, Math.max(1, level * 2 - 1));
        exp = 0;
        return true;
    }

    /**
     * Saves from before job changes: the job and its level become the character's and the job's
     * mastery is all the experience it took to get there - full from level 100, so those characters
     * keep their advanced job (and their book counts as given).
     */
    public boolean migrateJobChange() {
        if (dataVersion >= DATA_VERSION) return false;
        dataVersion = DATA_VERSION;
        if (!hasJob()) return false;
        long earned = exp;
        for (int l = 1; l < level && l < LevelCurve.ADVANCED_AT; l++) earned += LevelCurve.expToNext(l);
        setMastery(job, level >= LevelCurve.ADVANCED_AT ? LevelCurve.masteryExp() : earned);
        kitGiven.add(job);
        advanced = level >= LevelCurve.ADVANCED_AT && JobDefs.advancedOf(job) != null;
        if (advanced) masteryRewarded.add(job);
        return true;
    }

    /**
     * Changes to another base job, or to the advanced job of one ({@code adv}). Level, experience,
     * mastery and skill books stay; the current panels are put away and the new job's come back as
     * they were left (or fresh, with only the centre cell).
     */
    public void switchTo(String base, boolean adv) {
        if (hasJob()) {
            saved.put(job, new Saved(List.copyOf(unlocked), panelVersion, List.of(slots), List.copyOf(advUnlocked), advPanelVersion));
        }
        job = base;
        advanced = adv;
        unlocked.clear();
        advUnlocked.clear();
        cooldowns.clear();
        slots[0] = slots[1] = slots[2] = slots[3] = "";
        Saved s = saved.remove(base);
        if (s != null && s.version() == SkillPanel.version(base)) {
            unlocked.addAll(s.unlocked());
            for (int k = 0; k < SLOTS && k < s.slots().size(); k++) slots[k] = renamed(s.slots().get(k));
        } else {
            unlocked.add(0);
        }
        panelVersion = SkillPanel.version(base);
        if (s != null && s.advVersion() == SkillPanel.advVersion(base)) advUnlocked.addAll(s.advUnlocked());
        advPanelVersion = SkillPanel.advVersion(base);
        if (isAdvanced()) advUnlocked.add(0);
        dropUnknownSlots();
        JobDefs.Job def = jobDef();
        if (def != null && !def.actives().isEmpty() && slots[0].isEmpty() && !java.util.Arrays.asList(slots).contains(def.actives().get(0))) {
            slots[0] = def.actives().get(0);
        }
    }

    /** True if the saved unlocked cells belong to an older panel layout. */
    public boolean panelOutdated() {
        return hasJob() && panelVersion != SkillPanel.version(job);
    }

    /** Back to only the centre cell (all points refunded); the first skill stays equipped. */
    public void resetPanel() {
        unlocked.clear();
        panelVersion = SkillPanel.version(job);
        if (hasJob()) unlocked.add(0);
        // skills from the advanced panel stay equipped; the base ones that are gone come off
        dropUnknownSlots();
        if (hasJob() && slots[0].isEmpty()) {
            JobDefs.Job def = jobDef();
            if (!def.actives().isEmpty() && !java.util.Arrays.asList(slots).contains(def.actives().get(0))) slots[0] = def.actives().get(0);
        }
    }

    public void unlock(int nodeId) {
        unlocked.add(nodeId);
    }

    public void setSlot(int i, String skill) {
        slots[i] = skill == null ? "" : skill;
    }

    public void setMana(double mana) {
        this.mana = mana;
    }

    public void setCooldown(String skill, long readyAt) {
        cooldowns.put(skill, readyAt);
    }

    /** Drops cooldowns that have already expired so the saved data stays small. */
    public void pruneCooldowns(long now) {
        cooldowns.values().removeIf(t -> t <= now);
    }
}
