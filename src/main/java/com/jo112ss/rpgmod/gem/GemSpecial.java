package com.jo112ss.rpgmod.gem;

/**
 * Gem effects beyond plain stats. Rare ones stack (each copy adds its value, up to a cap);
 * legendary ones either are there or not.
 */
public enum GemSpecial {
    /** Heals this % of the damage dealt. */
    LIFESTEAL("lifesteal", Gem.RARE, 3, 12),
    /** Melee hits taken: this % goes back to the attacker. */
    THORNS("thorns", Gem.RARE, 20, 60),
    /** % chance to slow the target (Slowness II, 3 s). */
    CHILL("chill", Gem.RARE, 15, 45),
    /** % chance to poison the target (venom, 5 s). */
    VENOM("venom", Gem.RARE, 10, 30),
    /** +% damage while at half health or less. */
    DESPERATION("desperation", Gem.RARE, 15, 45),
    /** Seconds of Speed II after a kill. */
    TAILWIND("tailwind", Gem.RARE, 3, 9),
    /** % less fall damage (each copy cuts what is left). */
    ACROBAT("acrobat", Gem.RARE, 50, 100),

    /** Sets the target on fire for this many seconds. */
    IGNITE("ignite", Gem.LEGENDARY, 4, 4),
    /** Arrows are not used up. */
    ENDLESS_ARROWS("endless_arrows", Gem.LEGENDARY, 0, 0),
    /** Each hit restores this % of max MP (at most twice a second). */
    MANA_DRAIN("mana_drain", Gem.LEGENDARY, 3, 3),
    /** % chance that lightning strikes the target for half the hit again. */
    THUNDER("thunder", Gem.LEGENDARY, 15, 15),
    /** A lethal hit leaves 1 HP instead, once every this many seconds. */
    PHOENIX("phoenix", Gem.LEGENDARY, 300, 300),
    /** Heals this % of max health every 5 seconds. */
    REGENERATION("regeneration", Gem.LEGENDARY, 2, 2),
    /** On a critical hit, enemies around the target take this % of it. */
    SHOCKWAVE("shockwave", Gem.LEGENDARY, 50, 50),
    /** % chance to freeze the target in place for 2 seconds. */
    FREEZE("freeze", Gem.LEGENDARY, 20, 20);

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
