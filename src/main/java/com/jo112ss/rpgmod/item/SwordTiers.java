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
 * Attack of every melee weapon (vanilla swords included: they are the fighter's and paladin's):
 * the weapon kind's own attack plus a small flat material bonus (copper +1, iron +3, gold +4,
 * diamond +5, netherite +6), and a material multiplier on the whole attack while it is held (x1.0 /
 * x1.1 / x1.2 / x1.33 / x1.45), which also multiplies the character's own attack from levels.
 */
@EventBusSubscriber(modid = RpgMod.MODID)
public final class SwordTiers {
    private SwordTiers() {}

    private static final WeaponType[] MELEE = {WeaponType.SWORD, WeaponType.DAGGER, WeaponType.MACE, WeaponType.HAMMER, WeaponType.STAFF};
    /** The material multiplier's modifier on a weapon (monsters' gear cancels most of it). */
    public static final net.minecraft.resources.Identifier MATERIAL_ID = net.minecraft.resources.Identifier.fromNamespaceAndPath(RpgMod.MODID, "material_power");

    @SubscribeEvent
    public static void onComponents(ModifyDefaultComponentsEvent event) {
        for (WeaponType type : MELEE) {
            for (WeaponMaterial mat : WeaponMaterial.values()) {
                Item weapon = ModItems.get(type, mat);
                float damage = type.attackDamage + mat.meleeBonus();
                var b = ItemAttributeModifiers.builder()
                        .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, damage, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                        .add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, type.attackSpeed, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
                if (mat.multiplier != 1) {
                    b.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(MATERIAL_ID, mat.multiplier - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL), EquipmentSlotGroup.MAINHAND);
                }
                ItemAttributeModifiers mods = b.build();
                event.modify(weapon, (components, context, item) -> components.set(DataComponents.ATTRIBUTE_MODIFIERS, mods));
            }
        }
    }
}
