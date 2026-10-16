package com.jo112ss.rpgmod.job.core;

import java.util.List;

/**
 * Definition of a skill, ultimate or spell.
 *
 * @param id        stable id (lang keys rpgmod.skill.&lt;id&gt; and .desc)
 * @param job       job id that can learn it
 * @param kind      which slot it goes in
 * @param effect    what it does
 * @param cooldown  seconds
 * @param mana      mana cost
 * @param power     damage / heal / arrow count / mana amount, depending on the effect
 * @param radius    area or distance in blocks
 * @param duration  seconds for mob effects
 * @param effects   mob effect ids (namespace:path) applied by BUFF / DEBUFF / STEALTH
 * @param amplifier mob effect level - 1
 * @param glyph     one or two characters drawn on the icon
 * @param color     icon colour (RGB)
 * @param castTime  seconds the cast must be charged (held) before it fires; 0 = instant
 */
public record SkillDef(
        String id,
        String job,
        SkillKind kind,
        SkillEffect effect,
        double cooldown,
        double mana,
        double power,
        double radius,
        double duration,
        List<String> effects,
        int amplifier,
        String glyph,
        int color,
        double castTime
) {
    public SkillDef withEffect(SkillEffect effect, double power, double radius, double duration, List<String> effects) {
        return new SkillDef(id, job, kind, effect, cooldown, mana, power, radius, duration, effects, amplifier, glyph, color, castTime);
    }
}
