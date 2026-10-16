package com.jo112ss.rpgmod.effects;

import com.jo112ss.rpgmod.RpgMod;
import com.jo112ss.rpgmod.registry.ModRegistries;
import com.jo112ss.rpgmod.smithing.ForgeSessionManager;
import com.jo112ss.rpgmod.smithing.ForgedData;
import com.jo112ss.rpgmod.smithing.core.OptionType;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import com.jo112ss.rpgmod.item.RpgBowItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.GatherSkippedAttributeTooltipsEvent;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/**
 * Makes forging options real in game.
 *
 * <p>Vanilla stats (attack, speed, health, armor, movement, knockback resistance) are added as
 * attribute modifiers through {@link ItemAttributeModifierEvent}. They are computed from the item's
 * data each time, never written onto the item, so they only apply while the weapon is in the main
 * hand and disappear the moment it is switched out (vanilla equipment tracking removes them).
 *
 * <p>Stats vanilla does not have (critical chance/damage, armor penetration, boss damage, experience)
 * are read from the weapon in the attacker's main hand at the moment of the event.
 */
@EventBusSubscriber(modid = RpgMod.MODID)
public final class ForgeOptionEffects {
    private ForgeOptionEffects() {}

    /** Default vanilla critical multiplier used when a forged crit happens without a jump crit. */
    private static final float BASE_CRIT_MULTIPLIER = 1.5F;

    private record AttrBonus(Holder<Attribute> attribute, AttributeModifier.Operation op, double scale) {}

    private static final Map<OptionType, AttrBonus> ATTRIBUTE_OPTIONS = new EnumMap<>(OptionType.class);
    static {
        ATTRIBUTE_OPTIONS.put(OptionType.ATTACK_DAMAGE, new AttrBonus(Attributes.ATTACK_DAMAGE, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, 0.01));
        ATTRIBUTE_OPTIONS.put(OptionType.ATTACK_SPEED, new AttrBonus(Attributes.ATTACK_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, 0.01));
        ATTRIBUTE_OPTIONS.put(OptionType.MAX_HEALTH, new AttrBonus(Attributes.MAX_HEALTH, AttributeModifier.Operation.ADD_VALUE, 1));
        ATTRIBUTE_OPTIONS.put(OptionType.ARMOR, new AttrBonus(Attributes.ARMOR, AttributeModifier.Operation.ADD_VALUE, 1));
        ATTRIBUTE_OPTIONS.put(OptionType.ARMOR_TOUGHNESS, new AttrBonus(Attributes.ARMOR_TOUGHNESS, AttributeModifier.Operation.ADD_VALUE, 1));
        ATTRIBUTE_OPTIONS.put(OptionType.MOVEMENT_SPEED, new AttrBonus(Attributes.MOVEMENT_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, 0.01));
        ATTRIBUTE_OPTIONS.put(OptionType.KNOCKBACK_RESISTANCE, new AttrBonus(Attributes.KNOCKBACK_RESISTANCE, AttributeModifier.Operation.ADD_VALUE, 0.01));
    }

    private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    /** Modifier id per option and slot group, so the same option on several armor pieces stacks. */
    private static Identifier modifierId(OptionType type, EquipmentSlotGroup group) {
        return Identifier.fromNamespaceAndPath(RpgMod.MODID, "forged/" + type.id + "/" + group.getSerializedName());
    }

    /** Armor applies in its own slot; everything else (weapons, bows) in the main hand. */
    private static EquipmentSlotGroup slotGroup(ItemStack stack) {
        Equippable eq = stack.get(DataComponents.EQUIPPABLE);
        if (eq != null && eq.slot().getType() == EquipmentSlot.Type.HUMANOID_ARMOR) return EquipmentSlotGroup.bySlot(eq.slot());
        return EquipmentSlotGroup.MAINHAND;
    }

    public static ForgedData data(ItemStack stack) {
        return stack.isEmpty() ? null : stack.get(ModRegistries.FORGED_DATA.get());
    }

    private static ForgedData mainHandData(LivingEntity entity) {
        return entity == null ? null : data(entity.getMainHandItem());
    }

    // ------------------------------------------------------------------ attribute options

    @SubscribeEvent
    public static void onItemAttributes(ItemAttributeModifierEvent event) {
        ForgedData d = data(event.getItemStack());
        if (d == null) return;
        EquipmentSlotGroup group = slotGroup(event.getItemStack());
        for (ForgedData.Option o : d.options()) {
            OptionType type = o.type();
            AttrBonus bonus = type == null ? null : ATTRIBUTE_OPTIONS.get(type);
            if (bonus == null) continue;
            event.addModifier(bonus.attribute(), new AttributeModifier(modifierId(type, group), o.value() * bonus.scale(), bonus.op()), group);
        }
    }

    /** Our options are listed in the forged tooltip section; hide the duplicate vanilla attribute lines. */
    @SubscribeEvent
    public static void onSkipAttributeTooltips(GatherSkippedAttributeTooltipsEvent event) {
        if (data(event.getStack()) == null) return;
        EquipmentSlotGroup group = slotGroup(event.getStack());
        for (OptionType t : ATTRIBUTE_OPTIONS.keySet()) event.skipId(modifierId(t, group));
    }

    /** Sum of an option over the worn armor pieces. */
    private static double armorTotal(LivingEntity entity, OptionType type) {
        double sum = 0;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ForgedData d = data(entity.getItemBySlot(slot));
            if (d != null) sum += d.get(type);
        }
        return sum;
    }

    /** Forged armor on the player being hurt: general damage reduction and boss resistance. */
    @SubscribeEvent
    public static void onPlayerHurt(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        double reduction = Math.min(60, armorTotal(victim, OptionType.DAMAGE_REDUCTION)) / 100.0;
        Entity attacker = event.getSource().getEntity();
        double boss = attacker != null && attacker.is(Tags.EntityTypes.BOSSES) ? Math.min(60, armorTotal(victim, OptionType.BOSS_RESISTANCE)) / 100.0 : 0;
        if (reduction > 0 || boss > 0) {
            event.setAmount((float) (event.getAmount() * (1 - reduction) * (1 - boss)));
        }
    }

    // ------------------------------------------------------------------ custom combat options

    @SubscribeEvent
    public static void onCriticalHit(CriticalHitEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return; // roll on the server only
        ForgedData d = mainHandData(player);
        if (d == null) return;
        double chance = d.get(OptionType.CRIT_CHANCE) / 100.0;
        if (!event.isCriticalHit() && chance > 0 && player.getRandom().nextDouble() < chance) {
            event.setCriticalHit(true);
            event.setDamageMultiplier(Math.max(event.getDamageMultiplier(), BASE_CRIT_MULTIPLIER));
        }
        double critDamage = d.get(OptionType.CRIT_DAMAGE) / 100.0;
        if (event.isCriticalHit() && critDamage > 0) {
            event.setDamageMultiplier((float) (event.getDamageMultiplier() + critDamage));
        }
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        ForgedData d;
        if (event.getSource().getDirectEntity() == player) {
            d = mainHandData(player); // melee
        } else if (event.getSource().getDirectEntity() instanceof AbstractArrow arrow && arrow.getWeaponItem() != null) {
            ItemStack bow = arrow.getWeaponItem();
            if (bow.getItem() instanceof RpgBowItem rpgBow) {
                // material bonus, scaled by draw strength (arrow speed 3 = full draw)
                double drawn = Math.min(1.0, arrow.getDeltaMovement().length() / 3.0);
                event.setAmount((float) (event.getAmount() + rpgBow.damageBonus() * drawn));
            }
            d = data(bow);
            if (d != null) applyRangedOptions(event, d, player);
        } else {
            return;
        }
        if (d == null) return;

        double pen = d.get(OptionType.ARMOR_PENETRATION) / 100.0;
        if (pen > 0) {
            float keep = (float) Math.max(0, 1 - pen);
            event.addReductionModifier(DamageContainer.Reduction.ARMOR, (container, reduction) -> reduction * keep);
        }
        double boss = d.get(OptionType.BOSS_DAMAGE) / 100.0;
        if (boss > 0 && event.getEntity().is(Tags.EntityTypes.BOSSES)) {
            event.setAmount((float) (event.getAmount() * (1 + boss)));
        }
    }

    /**
     * Options of a forged bow that vanilla attributes cannot carry to the arrow: attack damage %,
     * and a critical roll (arrows have no melee crit event).
     */
    private static void applyRangedOptions(LivingIncomingDamageEvent event, ForgedData d, Player player) {
        double atk = d.get(OptionType.ATTACK_DAMAGE) / 100.0;
        if (atk > 0) event.setAmount((float) (event.getAmount() * (1 + atk)));
        double chance = d.get(OptionType.CRIT_CHANCE) / 100.0;
        if (chance > 0 && player.getRandom().nextDouble() < chance) {
            event.setAmount((float) (event.getAmount() * (BASE_CRIT_MULTIPLIER + d.get(OptionType.CRIT_DAMAGE) / 100.0)));
        }
    }

    @SubscribeEvent
    public static void onXpChange(PlayerXpEvent.XpChange event) {
        if (event.getAmount() <= 0 || !(event.getEntity() instanceof ServerPlayer player)) return;
        ForgedData d = mainHandData(player);
        double bonus = ((d == null ? 0 : d.get(OptionType.EXPERIENCE)) + armorTotal(player, OptionType.EXPERIENCE)) / 100.0;
        if (bonus <= 0) return;
        double scaled = event.getAmount() * (1 + bonus);
        int whole = (int) Math.floor(scaled);
        if (player.getRandom().nextDouble() < scaled - whole) whole++;
        event.setAmount(whole);
    }

    // ------------------------------------------------------------------ item lifecycle

    /** Netherite upgrade keeps quality and options (vanilla copies components); fix durability for the new base. */
    @SubscribeEvent
    public static void onSmithed(PlayerEvent.ItemSmithedEvent event) {
        ItemStack result = event.getResult();
        ForgedData d = data(result);
        if (d != null) ForgeSessionManager.applyDurability(result, d);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) ForgeSessionManager.onLogout(sp);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        ForgeSessionManager.onServerStopping(event.getServer());
    }
}
