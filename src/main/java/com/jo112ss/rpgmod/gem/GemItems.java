package com.jo112ss.rpgmod.gem;

import com.jo112ss.rpgmod.registry.ModRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Gem item stacks: one item per rarity, the effect in the gem component. */
public final class GemItems {
    private GemItems() {}

    public static Item item(int rarity) {
        return switch (rarity) {
            case Gem.LEGENDARY -> ModRegistries.GEM_LEGENDARY.get();
            case Gem.RARE -> ModRegistries.GEM_RARE.get();
            default -> ModRegistries.GEM_COMMON.get();
        };
    }

    public static ItemStack stack(Gem gem) {
        ItemStack s = new ItemStack(item(gem.rarity()));
        s.set(ModRegistries.GEM.get(), gem);
        return s;
    }

    public static Gem gem(ItemStack s) {
        return s.isEmpty() ? null : s.get(ModRegistries.GEM.get());
    }
}
