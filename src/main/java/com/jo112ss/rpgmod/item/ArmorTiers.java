package com.jo112ss.rpgmod.item;

import com.jo112ss.rpgmod.RpgMod;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;

/**
 * Higher-tier armour is worth more than its vanilla value: extra armour per piece (helmet, chest,
 * legs, boots), iron +13 / gold +34 / diamond +44 / netherite +66 for all four, so a full set is
 * worth iron 28, gold 45, diamond 64, netherite 86. The bonus sits on the armour piece itself, so
 * its tooltip shows the real value. Only players get it: anything else wearing the armour (monsters)
 * has it taken back off.
 */
@EventBusSubscriber(modid = RpgMod.MODID)
public final class ArmorTiers {
    private ArmorTiers() {}

    private static final WeaponType[] PIECES = {WeaponType.HELMET, WeaponType.CHESTPLATE, WeaponType.LEGGINGS, WeaponType.BOOTS};
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final int[][] TIER = {
            {0, 0, 0, 0}, // copper
            {3, 4, 4, 2}, // iron
            {8, 10, 9, 7}, // gold
            {10, 14, 11, 9}, // diamond
            {15, 20, 17, 14}, // netherite
    };
    private static final Identifier OFF_ID = Identifier.fromNamespaceAndPath(RpgMod.MODID, "armor_tier_off");

    /** The extra armour of a metal piece of this material (piece: helmet, chest, legs, boots). */
    public static int tierBonus(WeaponMaterial mat, int piece) {
        return TIER[Math.min(mat.ordinal(), TIER.length - 1)][piece];
    }

    /** The extra armour of one armour piece (0 for anything else). */
    public static int bonus(Item item) {
        for (int i = 0; i < PIECES.length; i++) {
            for (WeaponMaterial mat : WeaponMaterial.values()) {
                if (ModItems.get(PIECES[i], mat) == item) return TIER[Math.min(mat.ordinal(), TIER.length - 1)][i];
            }
        }
        return 0;
    }

    /** The piece's own armour line becomes vanilla + the tier bonus (one line, the real value). */
    @SubscribeEvent
    public static void onItemAttributes(ItemAttributeModifierEvent event) {
        int bonus = bonus(event.getItemStack().getItem());
        if (bonus <= 0) return;
        for (var e : List.copyOf(event.getModifiers())) {
            if (!e.attribute().is(Attributes.ARMOR) || e.modifier().operation() != AttributeModifier.Operation.ADD_VALUE
                    || !e.modifier().id().getNamespace().equals("minecraft")) continue; // the piece's own (vanilla) armour line
            event.removeModifier(e.attribute(), e.modifier().id());
            event.addModifier(e.attribute(), new AttributeModifier(e.modifier().id(), e.modifier().amount() + bonus, AttributeModifier.Operation.ADD_VALUE), e.slot());
            return;
        }
    }

    /** Non-players wearing tiered armour (monsters' gear): the tier bonus is taken back off. */
    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        LivingEntity e = event.getEntity();
        if (e instanceof Player || e.level().isClientSide() || event.getSlot().getType() != EquipmentSlot.Type.HUMANOID_ARMOR) return;
        AttributeInstance armor = e.getAttribute(Attributes.ARMOR);
        if (armor == null) return;
        int sum = 0;
        for (EquipmentSlot s : SLOTS) {
            ItemStack worn = e.getItemBySlot(s);
            if (!worn.isEmpty()) sum += bonus(worn.getItem());
        }
        if (sum == 0) armor.removeModifier(OFF_ID);
        else armor.addOrUpdateTransientModifier(new AttributeModifier(OFF_ID, -sum, AttributeModifier.Operation.ADD_VALUE));
    }
}
