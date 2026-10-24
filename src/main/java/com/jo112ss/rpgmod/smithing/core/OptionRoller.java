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
        List<OptionType> pool = new ArrayList<>();
        for (OptionType t : OptionType.values()) {
            if (!t.inPool(armor)) continue;
            if (t.isMagic() && !armor && !staff) continue;
            if (t == OptionType.DURABILITY && !wears) continue;
            pool.add(t);
        }
        for (int i = pool.size() - 1; i > 0; i--) Collections.swap(pool, i, random.nextInt(i + 1));
        List<RolledOption> out = new ArrayList<>();
        for (int i = 0; i < Math.min(count, pool.size()); i++) {
            OptionType t = pool.get(i);
            double min = t.min(armor), max = t.max(armor);
            double v = Math.max(min, Math.min(max, t.round(min + random.nextDouble() * (max - min))));
            out.add(new RolledOption(t, v));
        }
        return Collections.unmodifiableList(out);
    }
}
