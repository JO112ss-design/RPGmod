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
        job.base().forEach((s, v) -> m.merge(s, v, Double::sum));
        job.perLevel().forEach((s, v) -> m.merge(s, v * (data.level() - 1), Double::sum));
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
        if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
    }
}
