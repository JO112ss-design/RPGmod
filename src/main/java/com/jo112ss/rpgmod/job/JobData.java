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
    /** Equipped skill ids: 0 = skill 1, 1 = skill 2, 2 = ultimate. Empty string = none. */
    private final String[] slots = {"", "", ""};
    private double mana = JobDefs.BASE_MANA;
    /** Skill id -> game time when it is ready again. */
    private final Map<String, Long> cooldowns = new HashMap<>();

    public static final MapCodec<JobData> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.optionalFieldOf("job", "").forGetter(d -> d.job),
            Codec.INT.optionalFieldOf("level", 1).forGetter(d -> d.level),
            Codec.LONG.optionalFieldOf("exp", 0L).forGetter(d -> d.exp),
            Codec.INT.listOf().optionalFieldOf("unlocked", List.of()).forGetter(d -> List.copyOf(d.unlocked)),
            Codec.STRING.listOf().optionalFieldOf("slots", List.of("", "", "")).forGetter(d -> List.of(d.slots)),
            Codec.DOUBLE.optionalFieldOf("mana", JobDefs.BASE_MANA).forGetter(d -> d.mana),
            Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("cooldowns", Map.of()).forGetter(d -> Map.copyOf(d.cooldowns))
    ).apply(i, JobData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, JobData> STREAM_CODEC = StreamCodec.of(
            (buf, d) -> {
                buf.writeUtf(d.job);
                buf.writeVarInt(d.level);
                buf.writeVarLong(d.exp);
                buf.writeVarInt(d.unlocked.size());
                for (int id : d.unlocked) buf.writeVarInt(id);
                for (String s : d.slots) buf.writeUtf(s);
                buf.writeDouble(d.mana);
                buf.writeVarInt(d.cooldowns.size());
                d.cooldowns.forEach((k, v) -> {
                    buf.writeUtf(k);
                    buf.writeVarLong(v);
                });
            },
            buf -> {
                JobData d = new JobData();
                d.job = buf.readUtf();
                d.level = buf.readVarInt();
                d.exp = buf.readVarLong();
                int n = Math.min(buf.readVarInt(), 256);
                for (int k = 0; k < n; k++) d.unlocked.add(buf.readVarInt());
                for (int k = 0; k < 3; k++) d.slots[k] = buf.readUtf();
                d.mana = buf.readDouble();
                int c = Math.min(buf.readVarInt(), 256);
                for (int k = 0; k < c; k++) d.cooldowns.put(buf.readUtf(), buf.readVarLong());
                return d;
            });

    public JobData() {}

    private JobData(String job, int level, long exp, List<Integer> unlocked, List<String> slots, double mana, Map<String, Long> cooldowns) {
        this.job = job;
        this.level = Math.max(1, level);
        this.exp = Math.max(0, exp);
        this.unlocked.addAll(unlocked);
        for (int k = 0; k < 3 && k < slots.size(); k++) this.slots[k] = slots.get(k);
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

    public int totalPoints() {
        return LevelCurve.pointsForLevel(level);
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

    /** Sets the job and resets progress; the centre node is owned from the start. */
    public void setJob(String id) {
        job = id == null ? "" : id;
        level = 1;
        exp = 0;
        unlocked.clear();
        cooldowns.clear();
        slots[0] = slots[1] = slots[2] = "";
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

    /** Adds experience; returns the number of levels gained. */
    public int addExp(long amount, int maxLevel) {
        if (!hasJob() || amount <= 0) return 0;
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
