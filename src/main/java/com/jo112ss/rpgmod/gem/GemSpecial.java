package com.jo112ss.rpgmod.gem;

/**
 * Gem effects beyond plain stats. Rare ones stack (each copy adds its value, up to a cap);
 * legendary ones either are there or not. Values were cut to about a third of the first version;
 * lifesteal and mana drain work at most once every {@link #HEAL_COOLDOWN} ticks.
 */
public enum GemSpecial {
    /** Heals this % of the damage dealt (once every 5 s). */
    LIFESTEAL("lifesteal", Gem.RARE, 3, 9),
    /** Melee hits taken: this % goes back to the attacker. */
    THORNS("thorns", Gem.RARE, 7, 21),
    /** % chance to slow the target (Slowness II, 3 s). */
    CHILL("chill", Gem.RARE, 5, 15),
    /** % chance to poison the target (venom, 5 s). */
    VENOM("venom", Gem.RARE, 3, 9),
    /** +% damage while at half health or less. */
    DESPERATION("desperation", Gem.RARE, 5, 15),
    /** Seconds of Speed I after a kill. */
    TAILWIND("tailwind", Gem.RARE, 2, 6),
    /** % less fall damage (each copy cuts what is left). */
    ACROBAT("acrobat", Gem.RARE, 17, 100),

    /** Sets the target on fire for this many seconds. */
    IGNITE("ignite", Gem.LEGENDARY, 2, 2),
    /** Arrows are not used up. */
    ENDLESS_ARROWS("endless_arrows", Gem.LEGENDARY, 0, 0),
    /** A hit restores this % of max MP (once every 5 s). */
    MANA_DRAIN("mana_drain", Gem.LEGENDARY, 3, 3),
    /** % chance that lightning strikes the target for half the hit again. */
    THUNDER("thunder", Gem.LEGENDARY, 5, 5),
    /** A lethal hit leaves 1 HP instead, once every this many minutes. */
    PHOENIX("phoenix", Gem.LEGENDARY, 15, 15),
    /** Heals this % of max health every 5 seconds. */
    REGENERATION("regeneration", Gem.LEGENDARY, 0.7, 0.7),
    /** On a critical hit, enemies around the target take this % of it. */
    SHOCKWAVE("shockwave", Gem.LEGENDARY, 17, 17),
    /** % chance to freeze the target in place for 2 seconds. */
    FREEZE("freeze", Gem.LEGENDARY, 7, 7);

    /** Ticks between two lifesteal heals or mana drains. */
    public static final int HEAL_COOLDOWN = 100;

    public final String id;
    public final int rarity;
    public final double value;
    /** Most that several copies add up to (rare only). */
    public final double cap;

    GemSpecial(String id, int rarity, double value, double cap) {
        this.id = id;
        this.rarity = rarity;
        this.value = value;
        this.cap = cap;
    }

    public static GemSpecial byId(String id) {
        for (GemSpecial s : values()) if (s.id.equals(id)) return s;
        return null;
    }
}
