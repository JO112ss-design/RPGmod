package com.jo112ss.rpgmod.gem;

import com.jo112ss.rpgmod.item.ModItems;
import com.jo112ss.rpgmod.item.WeaponMaterial;
import com.jo112ss.rpgmod.item.WeaponType;
import com.jo112ss.rpgmod.registry.ModRegistries;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Gems set into a piece of gear (item data component). Every weapon, armour piece and shield the
 * forge knows has sockets: copper, iron and gold one, diamond two, netherite three. Gems stay
 * for good (they cannot be taken out) and survive the netherite upgrade.
 */
public record Sockets(List<Gem> gems) {
    public static final Sockets EMPTY = new Sockets(List.of());
    public static final int MAX = 3;

    public static final Codec<Sockets> CODEC = Gem.CODEC.listOf().xmap(Sockets::new, Sockets::gems);

    public static final StreamCodec<FriendlyByteBuf, Sockets> STREAM_CODEC = StreamCodec.of(
            (buf, s) -> {
                buf.writeVarInt(s.gems.size());
                for (Gem g : s.gems) Gem.STREAM_CODEC.encode(buf, g);
            },
            buf -> {
                int n = Math.min(buf.readVarInt(), MAX);
                List<Gem> list = new ArrayList<>(n);
                for (int k = 0; k < n; k++) list.add(Gem.STREAM_CODEC.decode(buf));
                return new Sockets(list);
            });

    public Sockets {
        gems = List.copyOf(gems);
    }

    public Sockets with(Gem gem) {
        List<Gem> list = new ArrayList<>(gems);
        list.add(gem);
        return new Sockets(list);
    }

    private record Kind(WeaponType type, WeaponMaterial material) {}

    private static Map<Item, Kind> kinds;

    private static Kind kind(Item item) {
        if (kinds == null) {
            Map<Item, Kind> m = new HashMap<>();
            for (WeaponType t : WeaponType.values()) {
                if (t == WeaponType.ARROW) continue;
                for (WeaponMaterial mat : WeaponMaterial.values()) m.putIfAbsent(ModItems.get(t, mat), new Kind(t, mat));
            }
            kinds = m;
        }
        return kinds.get(item);
    }

    /** Socket count of an item: copper/iron/gold 1, diamond 2, netherite 3; 0 if it takes no gems. */
    public static int capacity(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        var unique = com.jo112ss.rpgmod.item.UniqueWeapons.of(stack);
        if (unique != null) return unique.sockets(); // a boss's weapon: rolled when it dropped
        Kind k = kind(stack.getItem());
        if (k == null) return 0;
        return switch (k.material) {
            case DIAMOND -> 2;
            case NETHERITE -> 3;
            default -> 1;
        };
    }

    public static Sockets of(ItemStack stack) {
        return stack.isEmpty() ? EMPTY : stack.getOrDefault(ModRegistries.SOCKETS.get(), EMPTY);
    }

    public static List<Gem> gems(ItemStack stack) {
        return of(stack).gems;
    }

    public static int free(ItemStack stack) {
        return Math.max(0, capacity(stack) - gems(stack).size());
    }

    public static boolean isGem(ItemStack stack) {
        return !stack.isEmpty() && stack.has(ModRegistries.GEM.get());
    }
}
