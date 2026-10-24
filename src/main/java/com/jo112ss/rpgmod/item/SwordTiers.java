package com.jo112ss.rpgmod.item;

import com.jo112ss.rpgmod.RpgMod;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;

/**
 * Vanilla swords are the fighter's and paladin's swords: they follow the mod's material steps too
 * (copper as vanilla, then iron +7, gold +11, diamond +15, netherite +17 - see {@link WeaponMaterial#meleeBonus()}).
 */
@EventBusSubscriber(modid = RpgMod.MODID)
public final class SwordTiers {
    private SwordTiers() {}

    @SubscribeEvent
    public static void onComponents(ModifyDefaultComponentsEvent event) {
        for (WeaponMaterial mat : WeaponMaterial.values()) {
            Item sword = ModItems.get(WeaponType.SWORD, mat);
            float damage = WeaponType.SWORD.attackDamage + mat.meleeBonus();
            ItemAttributeModifiers mods = ItemAttributeModifiers.builder()
                    .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, damage, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                    .add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, WeaponType.SWORD.attackSpeed, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                    .build();
            event.modify(sword, (components, context, item) -> components.set(DataComponents.ATTRIBUTE_MODIFIERS, mods));
        }
    }
}
