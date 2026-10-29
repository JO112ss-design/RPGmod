package com.jo112ss.rpgmod.job;

import com.jo112ss.rpgmod.RpgMod;
import com.jo112ss.rpgmod.job.core.JobDefs;
import com.jo112ss.rpgmod.job.core.JobStat;
import com.jo112ss.rpgmod.job.core.SkillPanel;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Totals of job + level + skill panel stats, and applying the vanilla ones as attribute modifiers. */
public final class JobStats {
    private JobStats() {}

    public static Map<JobStat, Double> compute(JobData data) {
        Map<JobStat, Double> m = new EnumMap<>(JobStat.class);
        for (JobStat s : JobStat.values()) m.put(s, 0.0);
        m.put(JobStat.MAX_MANA, JobDefs.BASE_MANA);
        m.put(JobStat.MANA_REGEN, JobDefs.BASE_MANA_REGEN);
        JobDefs.Job job = data.jobDef();
        if (job == null) return m;
        m.merge(JobStat.ATTACK_DAMAGE, FIST, Double::sum); // the bare hand's 1 brought to the x3 scale
        job.base().forEach((s, v) -> m.merge(s, v, Double::sum));
        // the base job's growth goes on with the character level (the advanced job's adds to it);
        // attack and magic attack grow in whole points (+1 every 5 levels at 0.2 a level)
        job.perLevel().forEach((s, v) -> {
            double grown = v * (data.level() - 1);
            if (s == JobStat.ATTACK_DAMAGE || s == JobStat.MAGIC_ATTACK) grown = Math.floor(grown + 1e-9);
            m.merge(s, grown, Double::sum);
        });
        SkillPanel panel = data.panel();
        for (int id : data.unlocked()) {
            SkillPanel.Node n = panel.node(id);
            if (n != null && n.type() == SkillPanel.NodeType.STAT) m.merge(n.stat(), n.amount(), Double::sum);
        }
        // advanced job: its own growth per advanced level on top, and its panel
        JobDefs.Job adv = data.isAdvanced() ? data.advancedDef() : null;
        if (adv != null) {
            adv.perLevel().forEach((s, v) -> m.merge(s, v * data.advLevel(), Double::sum));
            SkillPanel ap = data.panel(true);
            for (int id : data.unlocked(true)) {
                SkillPanel.Node n = ap.node(id);
                if (n != null && n.type() == SkillPanel.NodeType.STAT) m.merge(n.stat(), n.amount(), Double::sum);
            }
        }
        m.put(JobStat.COOLDOWN_REDUCTION, Math.min(50, m.get(JobStat.COOLDOWN_REDUCTION)));
        return m;
    }

    /**
     * Attack is on three times vanilla's scale: the bare hand counts 3 (vanilla's 1 + this), and
     * weapons, levels and the panel are tripled too (monsters have three times the health).
     */
    public static final double FIST = 2;

    /** Strength and weakness (vanilla +3 / -4 per level) brought to the x3 scale: their extra share. */
    public static final double STRENGTH_EXTRA = 6, WEAKNESS_EXTRA = 8;
    private static final Identifier POTION_SCALE_ID = Identifier.fromNamespaceAndPath(RpgMod.MODID, "job/potion_scale");

    /** Adds the missing two thirds of strength / weakness to the attack (called every few ticks). */
    public static void updatePotionScale(ServerPlayer player) {
        AttributeInstance atk = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (atk == null) return;
        double extra = 0;
        var s = player.getEffect(net.minecraft.world.effect.MobEffects.STRENGTH);
        if (s != null) extra += STRENGTH_EXTRA * (s.getAmplifier() + 1);
        var w = player.getEffect(net.minecraft.world.effect.MobEffects.WEAKNESS);
        if (w != null) extra -= WEAKNESS_EXTRA * (w.getAmplifier() + 1);
        var cur = atk.getModifier(POTION_SCALE_ID);
        if (extra == 0) {
            if (cur != null) atk.removeModifier(POTION_SCALE_ID);
        } else if (cur == null || cur.amount() != extra) {
            atk.addOrUpdateTransientModifier(new AttributeModifier(POTION_SCALE_ID, extra, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    /** Max health per mastered job (multiplier on the total). */
    public static final double MASTERY_HEALTH = 0.02;
    private static final Identifier MASTERY_ID = Identifier.fromNamespaceAndPath(RpgMod.MODID, "job/mastery_health");
    /** Every player's max health x this (everything on it: job, panel, gear, gems); monsters hit half as much harder (MobLevels). */
    public static final double HEALTH_SCALE = 1.3;
    private static final Identifier HEALTH_SCALE_ID = Identifier.fromNamespaceAndPath(RpgMod.MODID, "job/health_scale");

    private record Mapping(JobStat stat, Holder<Attribute> attribute, AttributeModifier.Operation op, double scale) {}

    private static final Mapping[] VANILLA = {
            new Mapping(JobStat.MAX_HEALTH, Attributes.MAX_HEALTH, AttributeModifier.Operation.ADD_VALUE, 1),
            new Mapping(JobStat.ATTACK_DAMAGE, Attributes.ATTACK_DAMAGE, AttributeModifier.Operation.ADD_VALUE, 1),
            new Mapping(JobStat.ARMOR, Attributes.ARMOR, AttributeModifier.Operation.ADD_VALUE, 1),
            new Mapping(JobStat.MOVEMENT_SPEED, Attributes.MOVEMENT_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, 0.01),
            new Mapping(JobStat.ATTACK_SPEED, Attributes.ATTACK_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, 0.01),
            new Mapping(JobStat.LUCK, Attributes.LUCK, AttributeModifier.Operation.ADD_VALUE, 1),
    };

    /** Re-applies the job's vanilla attribute bonuses (transient: rebuilt on login / respawn / change). */
    public static void apply(ServerPlayer player, JobData data) {
        Map<JobStat, Double> stats = compute(data);
        for (Mapping mp : VANILLA) {
            AttributeInstance inst = player.getAttribute(mp.attribute());
            if (inst == null) continue;
            Identifier id = Identifier.fromNamespaceAndPath(RpgMod.MODID, "job/" + mp.stat().id);
            double v = stats.getOrDefault(mp.stat(), 0.0) * mp.scale();
            if (v == 0) inst.removeModifier(id);
            else inst.addOrUpdateTransientModifier(new AttributeModifier(id, v, mp.op()));
        }
        // every mastered job: +2% max health
        AttributeInstance hp = player.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) {
            hp.addOrUpdateTransientModifier(new AttributeModifier(HEALTH_SCALE_ID, HEALTH_SCALE - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            double bonus = MASTERY_HEALTH * data.masteredCount();
            if (bonus <= 0) hp.removeModifier(MASTERY_ID);
            else hp.addOrUpdateTransientModifier(new AttributeModifier(MASTERY_ID, bonus, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
    }
}
