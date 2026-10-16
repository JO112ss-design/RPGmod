package com.jo112ss.rpgmod.smithing.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.random.RandomGenerator;

/** Rolls distinct random options; no type appears twice on one weapon. */
public final class OptionRoller {
    private OptionRoller() {}

    public static List<RolledOption> roll(int count, RandomGenerator random) {
        List<OptionType> pool = new ArrayList<>(Arrays.asList(OptionType.values()));
        shuffle(pool, random);
        List<RolledOption> out = new ArrayList<>();
        for (int i = 0; i < Math.min(count, pool.size()); i++) {
            OptionType t = pool.get(i);
            double v = t.clamp(t.round(t.min + random.nextDouble() * (t.max - t.min)));
            out.add(new RolledOption(t, v));
        }
        return Collections.unmodifiableList(out);
    }

    private static <T> void shuffle(List<T> list, RandomGenerator random) {
        for (int i = list.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            Collections.swap(list, i, j);
        }
    }
}
