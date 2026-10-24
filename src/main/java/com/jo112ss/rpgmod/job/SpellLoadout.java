package com.jo112ss.rpgmod.job;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * Spells set on a magic staff (item data component): up to nine slots (four for most casters, nine
 * for the spellmaster - see {@link JobData#spellSlots()}), the one cast with right click and the one
 * the spellmaster casts with left click.
 */
public record SpellLoadout(List<String> spells, int selected, int selectedLeft) {
    /** Slots stored on every staff. */
    public static final int SLOTS = 9;
    /** Slots a caster can use before the spellmaster's five extra ones. */
    public static final int BASE_SLOTS = 4;
    public static final SpellLoadout EMPTY = new SpellLoadout(List.of(), 0, 1);

    public static final Codec<SpellLoadout> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.listOf().fieldOf("spells").forGetter(SpellLoadout::spells),
            Codec.INT.optionalFieldOf("selected", 0).forGetter(SpellLoadout::selected),
            Codec.INT.optionalFieldOf("selected_left", 1).forGetter(SpellLoadout::selectedLeft)
    ).apply(i, SpellLoadout::new));

    public static final StreamCodec<FriendlyByteBuf, SpellLoadout> STREAM_CODEC = StreamCodec.of(
            (buf, l) -> {
                for (int k = 0; k < SLOTS; k++) buf.writeUtf(l.spell(k));
                buf.writeVarInt(l.selected);
                buf.writeVarInt(l.selectedLeft);
            },
            buf -> {
                List<String> s = new ArrayList<>();
                for (int k = 0; k < SLOTS; k++) s.add(buf.readUtf());
                return new SpellLoadout(s, buf.readVarInt(), buf.readVarInt());
            });

    public SpellLoadout {
        List<String> fixed = new ArrayList<>(spells);
        while (fixed.size() < SLOTS) fixed.add("");
        spells = List.copyOf(fixed.subList(0, SLOTS));
        selected = Math.max(0, Math.min(SLOTS - 1, selected));
        selectedLeft = Math.max(0, Math.min(SLOTS - 1, selectedLeft));
    }

    public String spell(int i) {
        return i >= 0 && i < spells.size() ? spells.get(i) : "";
    }

    /** The right-click spell (only from the slots this caster can use). */
    public String selectedSpell(int usable) {
        return selected < usable ? spell(selected) : "";
    }

    public String selectedSpell() {
        return spell(selected);
    }

    /** The spellmaster's left-click spell. */
    public String leftSpell() {
        return spell(selectedLeft);
    }

    public SpellLoadout withSpell(int slot, String spell) {
        List<String> s = new ArrayList<>(spells);
        // a spell sits in one slot only
        for (int k = 0; k < s.size(); k++) if (k != slot && s.get(k).equals(spell)) s.set(k, "");
        s.set(slot, spell == null ? "" : spell);
        return new SpellLoadout(s, selected, selectedLeft);
    }

    public SpellLoadout withSelected(int i) {
        return new SpellLoadout(spells, i, selectedLeft);
    }

    public SpellLoadout withSelectedLeft(int i) {
        return new SpellLoadout(spells, selected, i);
    }
}
