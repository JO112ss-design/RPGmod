package com.jo112ss.rpgmod.item;

import com.jo112ss.rpgmod.RpgMod;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Unit;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;

/**
 * Every weapon and armour piece the forge makes (copper to netherite, forged or crafted) never
 * breaks. Shields have their own rule (knocked out for a while, see {@link Shields}); arrows are
 * ammunition.
 */
@EventBusSubscriber(modid = RpgMod.MODID)
public final class UnbreakableGear {
    private UnbreakableGear() {}

    public static boolean covers(WeaponType type) {
        return type != WeaponType.SHIELD && type != WeaponType.ARROW;
    }

    @SubscribeEvent
    public static void onComponents(ModifyDefaultComponentsEvent event) {
        Set<Item> done = new HashSet<>();
        for (WeaponType type : WeaponType.values()) {
            if (!covers(type)) continue;
            for (WeaponMaterial mat : WeaponMaterial.values()) {
                Item item = ModItems.get(type, mat);
                if (!done.add(item)) continue;
                event.modify(item, (components, context, it) -> components.set(DataComponents.UNBREAKABLE, Unit.INSTANCE));
            }
        }
    }
}
