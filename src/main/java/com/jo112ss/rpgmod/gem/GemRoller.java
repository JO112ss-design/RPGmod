package com.jo112ss.rpgmod.gem;

import com.jo112ss.rpgmod.smithing.core.OptionType;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.RandomSource;

/** Rolls new gems: the rarity, then one effect of that rarity. */
public final class GemRoller {
    private GemRoller() {}

    /** Rarity weights (common, rare, legendary) of ordinary monsters and of bosses. */
    public static final double[] NORMAL = {80, 17, 3};
    public static final double[] BOSS = {50, 35, 15};

    public static int rollRarity(RandomSource r, boolean boss) {
        double[] w = boss ? BOSS : NORMAL;
        double x = r.nextDouble() * (w[0] + w[1] + w[2]);
        if (x < w[0]) return Gem.COMMON;
        if (x < w[0] + w[1]) return Gem.RARE;
        return Gem.LEGENDARY;
    }

    /**
     * Stat options a gem can carry: every forging option but durability (gear no longer wears out)
     * and attack / magic attack % - those gems give flat attack / magic attack instead.
     */
    public static List<OptionType> statPool() {
        List<OptionType> out = new ArrayList<>();
        for (OptionType t : OptionType.values()) {
            if (t != OptionType.DURABILITY && t != OptionType.ATTACK_DAMAGE && t != OptionType.MAGIC_POWER) out.add(t);
        }
        return out;
    }

    /** Flat attack of a common attack gem (rare ones 1.5 to 2 times): about 1-3% of a level 100 attack. */
    public static final int ATTACK_MIN = 1, ATTACK_MAX = 3;

    /**
     * Value range of a common stat gem, in whole numbers and at least 1: a third of the forging
     * range (weapon range when it has one, else armour), rounded.
     */
    public static double min(OptionType t) {
        if (t == OptionType.ATTACK_FLAT || t == OptionType.MAGIC_FLAT) return ATTACK_MIN;
        return Math.max(1, Math.round((Double.isNaN(t.weaponMin) ? t.armorMin : t.weaponMin) / 3));
    }

    public static double max(OptionType t) {
        if (t == OptionType.ATTACK_FLAT || t == OptionType.MAGIC_FLAT) return ATTACK_MAX;
        return Math.max(min(t), Math.round((Double.isNaN(t.weaponMax) ? t.armorMax : t.weaponMax) / 3));
    }

    public static Gem stat(OptionType t, int rarity, RandomSource r) {
        double lo = min(t), hi = max(t);
        if (rarity == Gem.RARE) { // a rare stat gem is 1.5 to 2 times a common one (and always above it)
            lo = Math.max(lo + 1, Math.round(lo * 1.5));
            hi = Math.max(lo, Math.round(hi * 2));
        }
        long v = Math.round(lo + r.nextDouble() * (hi - lo)); // gems are whole numbers
        return new Gem(t.id, rarity, Math.max(1, v));
    }

    public static Gem roll(int rarity, RandomSource r) {
        if (rarity == Gem.LEGENDARY || (rarity == Gem.RARE && r.nextBoolean())) {
            List<GemSpecial> pool = new ArrayList<>();
            for (GemSpecial s : GemSpecial.values()) if (s.rarity == rarity) pool.add(s);
            return Gem.of(pool.get(r.nextInt(pool.size())));
        }
        List<OptionType> pool = statPool();
        return stat(pool.get(r.nextInt(pool.size())), rarity, r);
    }

    public static Gem roll(RandomSource r, boolean boss) {
        return roll(rollRarity(r, boss), r);
    }
}
