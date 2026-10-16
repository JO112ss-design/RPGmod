package com.jo112ss.rpgmod.job.core;

/**
 * Building blocks for (placeholder) skills. Each skill picks one and tunes it with power, radius,
 * duration and mob effects. Real skill designs can add new effects later.
 */
public enum SkillEffect {
    /** Damage enemies in front of the caster. */
    SWEEP,
    /** Damage enemies all around the caster. */
    WHIRL,
    /** Lunge forward. */
    DASH,
    /** Leap backwards. */
    BACKSTEP,
    /** Teleport forward up to {@code radius} blocks. */
    BLINK,
    /** Fire {@code power} arrows in a fan. */
    SHOT,
    /** Heal the caster and nearby players. */
    HEAL,
    /** Give mob effects to the caster and nearby players. */
    BUFF,
    /** Give mob effects to nearby enemies (and damage them by {@code power}). */
    DEBUFF,
    /** Restore mana to the caster (and nearby players if radius &gt; 0). */
    MANA,
    /** Invisibility plus the listed effects. */
    STEALTH,
    /** A random effect of random strength. */
    GAMBLE,
    /** Instant magic bolt at the first enemy in sight. */
    BOLT,
    /** Lightning strike at the looked-at spot. */
    LIGHTNING
}
