package com.jo112ss.rpgmod.smithing.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.random.RandomGenerator;

/** Rolls distinct random options from the weapon or the armor pool; no type appears twice. */
public final class OptionRoller {
    private OptionRoller() {}

    public static List<RolledOption> roll(int count, boolean armor, RandomGenerator random) {
        return roll(count, armor, false, random);
    }

    public static List<RolledOption> roll(int count, boolean armor, boolean staff, RandomGenerator random) {
        return roll(count, armor, staff, true, random);
    }

    /**
     * {@code staff}: magic options (magic power, cast speed) are only in a weapon's pool for staffs.
     * {@code wears}: durability is only rolled for gear that can wear out (shields; the rest never breaks).
     */
    public static List<RolledOption> roll(int count, boolean armor, boolean staff, boolean wears, RandomGenerator random) {
        return roll(count, armor ? OptionType.Pool.ARMOR : OptionType.Pool.WEAPON, staff, wears, random);
    }

    /** Distinct options from a pool, drawn by weight (see {@link OptionType#weight}). */
    public static List<RolledOption> roll(int count, OptionType.Pool kind, boolean staff, boolean wears, RandomGenerator random) {
        boolean armor = kind != OptionType.Pool.WEAPON;
        List<OptionType> pool = new ArrayList<>();
        for (OptionType t : OptionType.values()) {
            if (!t.inPool(kind)) continue;
            if (t.isMagic() && !armor && !staff) continue;
            if (t == OptionType.DURABILITY && !wears) continue;
            pool.add(t);
        }
        List<RolledOption> out = new ArrayList<>();
        while (out.size() < count && !pool.isEmpty()) {
            int total = 0;
            for (OptionType t : pool) total += t.weight(kind);
            int pick = random.nextInt(total);
            OptionType t = pool.get(pool.size() - 1);
            for (OptionType u : pool) {
                pick -= u.weight(kind);
                if (pick < 0) {
                    t = u;
                    break;
                }
            }
            pool.remove(t);
            double min = t.min(armor), max = t.max(armor);
            double v = Math.max(min, Math.min(max, t.round(min + random.nextDouble() * (max - min))));
            out.add(new RolledOption(t, v));
        }
        return Collections.unmodifiableList(out);
    }
}
