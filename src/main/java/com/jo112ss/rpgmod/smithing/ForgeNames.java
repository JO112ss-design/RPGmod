package com.jo112ss.rpgmod.smithing;

import java.util.Random;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Just for fun: every forged piece gets a name of two random words in front of its own, like
 * "きらきら光るダイヤの剣" (a manner, then a doing). The words are lang keys, so the name reads in each
 * player's language; an anvil rename still goes over it.
 */
public final class ForgeNames {
    private ForgeNames() {}

    /** How many words each list has ({@code rpgmod.forgename.a.0..} and {@code rpgmod.forgename.b.0..}). */
    public static final int A = 24, B = 24;

    public static void apply(ItemStack stack, Random rnd) {
        Component base = stack.getHoverName();
        stack.set(DataComponents.ITEM_NAME, Component.translatable("rpgmod.forgename",
                Component.translatable("rpgmod.forgename.a." + rnd.nextInt(A)),
                Component.translatable("rpgmod.forgename.b." + rnd.nextInt(B)), base));
    }
}
