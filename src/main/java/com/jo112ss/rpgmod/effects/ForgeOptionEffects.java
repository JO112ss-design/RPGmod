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


    private record AttrBonus(Holder<Attribute> attribute, AttributeModifier.Operation op, double scale) {}

    private static final Map<OptionType, AttrBonus> ATTRIBUTE_OPTIONS = new EnumMap<>(OptionType.class);
    static {
        ATTRIBUTE_OPTIONS.put(OptionType.ATTACK_DAMAGE, new AttrBonus(Attributes.ATTACK_DAMAGE, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, 0.01));
        ATTRIBUTE_OPTIONS.put(OptionType.ATTACK_SPEED, new AttrBonus(Attributes.ATTACK_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, 0.01));
        ATTRIBUTE_OPTIONS.put(OptionType.MAX_HEALTH, new AttrBonus(Attributes.MAX_HEALTH, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, 0.01));
        ATTRIBUTE_OPTIONS.put(OptionType.ATTACK_FLAT, new AttrBonus(Attributes.ATTACK_DAMAGE, AttributeModifier.Operation.ADD_VALUE, 1));
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
        if (eq != null && eq.slot() == EquipmentSlot.OFFHAND) return EquipmentSlotGroup.OFFHAND; // shields
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
        ItemStack stack = event.getItemStack();
        if (data(stack) == null && com.jo112ss.rpgmod.gem.Sockets.gems(stack).isEmpty()) return;
        EquipmentSlotGroup group = slotGroup(stack);
        // forged options and socketed stat gems together
        for (Map.Entry<OptionType, Double> o : com.jo112ss.rpgmod.gem.Gear.options(stack).entrySet()) {
            AttrBonus bonus = ATTRIBUTE_OPTIONS.get(o.getKey());
            if (bonus == null) continue;
            event.addModifier(bonus.attribute(), new AttributeModifier(modifierId(o.getKey(), group), o.getValue() * bonus.scale(), bonus.op()), group);
        }
    }

    /** Our options are listed in the forged tooltip section; hide the duplicate vanilla attribute lines. */
    @SubscribeEvent
    public static void onSkipAttributeTooltips(GatherSkippedAttributeTooltipsEvent event) {
        if (data(event.getStack()) == null && com.jo112ss.rpgmod.gem.Sockets.gems(event.getStack()).isEmpty()) return;
        EquipmentSlotGroup group = slotGroup(event.getStack());
        for (OptionType t : ATTRIBUTE_OPTIONS.keySet()) event.skipId(modifierId(t, group));
    }

    /** Sum of an option (forged + gems) over the held weapon, worn armour and an off-hand shield. */
    private static double armorTotal(LivingEntity entity, OptionType type) {
        return com.jo112ss.rpgmod.gem.Gear.total(entity, type, null);
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
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        ItemStack weapon;
        if (event.getSource().getDirectEntity() == player) {
            weapon = player.getMainHandItem(); // melee
        } else if (event.getSource().getDirectEntity() instanceof AbstractArrow arrow && arrow.getWeaponItem() != null) {
            ItemStack bow = arrow.getWeaponItem();
            // players' attack is on a x3 scale (monsters have x3 health): the arrow's own damage too
            if (bow.getItem() instanceof net.minecraft.world.item.ProjectileWeaponItem) event.setAmount(event.getAmount() * 3);
            double mult = 1;
            if (bow.getItem() instanceof RpgBowItem rpgBow) {
                // material bonus, scaled by draw strength (arrow speed 3 = full draw)
                double drawn = Math.min(1.0, arrow.getDeltaMovement().length() / 3.0);
                event.setAmount((float) (event.getAmount() + rpgBow.damageBonus() * drawn));
                mult = rpgBow.material().multiplier;
            }
            weapon = bow;
            applyRangedOptions(event, bow, player);
            // the shooter's own attack (job level, panel, strength...), taken when the arrow was loosed,
            // then the bow material's multiplier on the whole arrow
            double own = arrow.getPersistentData().getDoubleOr(OWN_ATTACK, 0);
            float before = event.getAmount();
            event.setAmount((float) Math.max(0, (before + own) * mult));
            if (com.jo112ss.rpgmod.devtest.AutoTestServer.ENABLED) {
                com.jo112ss.rpgmod.RpgMod.LOGGER.info("[AUTOTEST] bow arrow ({} + own {}) x{} -> {}", before, String.format("%.1f", own), mult, event.getAmount());
            }
        } else {
            return;
        }

        double boss = com.jo112ss.rpgmod.gem.Gear.total(player, OptionType.BOSS_DAMAGE, weapon) / 100.0;
        if (boss > 0 && event.getEntity().is(Tags.EntityTypes.BOSSES)) {
            event.setAmount((float) (event.getAmount() * (1 + boss)));
        }
    }

    private static final String OWN_ATTACK = "rpgmod_own_attack";

    /**
     * Vanilla arrows ignore the shooter's attack attribute, so a bow never felt job levels, the skill
     * panel, strength (war cry, empower...) or weakness. When a player looses an arrow from a bow or
     * crossbow, everything their attack attribute holds above the base 1 is stored on the arrow,
     * scaled by draw strength like the material bonus, and added when it hits (before the RPG crit).
     * That matches the attack the status screen shows for bows. Skill arrows carry no bow and are
     * skipped: their damage already comes from the attack.
     */
    @SubscribeEvent
    public static void onArrowLoosed(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || event.loadedFromDisk()) return;
        if (!(event.getEntity() instanceof AbstractArrow arrow) || !(arrow.getOwner() instanceof Player player)) return;
        ItemStack bow = arrow.getWeaponItem();
        if (bow == null || !(bow.getItem() instanceof net.minecraft.world.item.ProjectileWeaponItem)) return;
        double own = ownAttack(player, bow);
        own += 15 * (com.jo112ss.rpgmod.job.JobManager.attackUp(player) - 1); // attack up also lifts the arrow's own part
        if (Math.abs(own) < 1e-4) return;
        double drawn = Math.min(1.0, arrow.getDeltaMovement().length() / 3.0);
        arrow.getPersistentData().putDouble(OWN_ATTACK, own * drawn);
    }

    /** The player's attack attribute above the base, without a melee weapon held in the other hand. */
    public static double ownAttack(Player player, ItemStack bow) {
        var inst = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        if (inst == null) return 0;
        double value = inst.getValue();
        ItemStack main = player.getMainHandItem();
        if (main != bow && !main.isEmpty()) { // shot from the off hand: the main-hand weapon is not the bow's
            double[] w = {0, 1};
            main.forEachModifier(net.minecraft.world.entity.EquipmentSlot.MAINHAND, (attr, mod) -> {
                if (!attr.is(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)) return;
                if (mod.operation() == net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE) w[0] += mod.amount();
                if (mod.operation() == net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) w[1] *= 1 + mod.amount();
            });
            value = value / w[1] - w[0]; // without that weapon's multiplier and attack
        }
        return value - inst.getBaseValue() - com.jo112ss.rpgmod.job.JobStats.FIST;
    }

    /**
     * Options of a forged bow that vanilla attributes cannot carry to the arrow: attack damage %,
     * and a critical roll (arrows have no melee crit event).
     */
    private static void applyRangedOptions(LivingIncomingDamageEvent event, ItemStack bow, Player player) {
        double atk = com.jo112ss.rpgmod.gem.Gear.option(bow, OptionType.ATTACK_DAMAGE) / 100.0;
        if (atk > 0) event.setAmount((float) (event.getAmount() * (1 + atk)));
    }

    @SubscribeEvent
    public static void onXpChange(PlayerXpEvent.XpChange event) {
        if (event.getAmount() <= 0 || !(event.getEntity() instanceof ServerPlayer player)) return;
        double bonus = armorTotal(player, OptionType.EXPERIENCE) / 100.0;
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
