package com.jo112ss.rpgmod.job;

import com.jo112ss.rpgmod.RpgMod;
import com.jo112ss.rpgmod.item.ModItems;
import com.jo112ss.rpgmod.item.WeaponType;
import com.jo112ss.rpgmod.job.fx.ServerFx;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The nightblade's second blade: with a dagger in the off hand, right click strikes with it. The hit
 * counts as a normal attack (crits, coatings, twin venom, yaksha all apply) with the off-hand
 * dagger's damage in place of the main-hand weapon's, on its own swing timer.
 */
public final class DualWield {
    private DualWield() {}

    /** Dev test only: allow it without being a nightblade. */
    public static boolean testAllow;

    private static final Map<UUID, Long> NEXT = new HashMap<>();

    public static boolean canDualWield(Player p) {
        JobData d = JobManager.data(p);
        return (testAllow || d.isAdvancedAs("nightblade")) && isBlade(p.getOffhandItem()) && mainHandLetsGo(p.getMainHandItem());
    }

    public static boolean isBlade(ItemStack s) {
        WeaponType t = ModItems.typeOf(s.getItem());
        return t != null && JobType.ASSASSIN.weapons.contains(t);
    }

    /**
     * Right click goes to the off-hand blade only when the main hand has nothing of its own to do
     * with it: empty, or a melee weapon. Food, potions, bows, blocks, pearls... keep their right click.
     */
    public static boolean mainHandLetsGo(ItemStack main) {
        if (main.isEmpty()) return true;
        WeaponType t = ModItems.typeOf(main.getItem());
        if (t != null) return t == WeaponType.SWORD || t == WeaponType.DAGGER || t == WeaponType.MACE || t == WeaponType.HAMMER;
        return main.is(net.minecraft.tags.ItemTags.SWORDS) || main.is(net.minecraft.tags.ItemTags.AXES);
    }

    /** Flat bonus of an item's own modifiers to an attribute when held in the main hand. */
    private static double itemBonus(ItemStack s, Holder<Attribute> attr) {
        double[] sum = {0};
        s.forEachModifier(EquipmentSlot.MAINHAND, (a, m) -> {
            if (a.equals(attr) && m.operation() == AttributeModifier.Operation.ADD_VALUE) sum[0] += m.amount();
        });
        return sum[0];
    }

    /** Off-hand hit damage: the player's attack with the off-hand dagger in place of the main-hand item. */
    public static float damage(ServerPlayer p) {
        double atk = p.getAttributeValue(Attributes.ATTACK_DAMAGE);
        return (float) Math.max(1, atk - itemBonus(p.getMainHandItem(), Attributes.ATTACK_DAMAGE) + itemBonus(p.getOffhandItem(), Attributes.ATTACK_DAMAGE));
    }

    /** Ticks between off-hand strikes, from the dagger's own attack speed (plus the job's bonus). */
    public static int interval(ServerPlayer p) {
        double base = p.getAttributeValue(Attributes.ATTACK_SPEED) - itemBonus(p.getMainHandItem(), Attributes.ATTACK_SPEED);
        double speed = Math.max(0.5, base + itemBonus(p.getOffhandItem(), Attributes.ATTACK_SPEED));
        return Math.max(4, (int) Math.round(20 / speed));
    }

    /** Right click with the off-hand blade, at {@code entityId} (or -1: a swing at nothing). */
    public static boolean strike(ServerPlayer p, int entityId) {
        if (!canDualWield(p) || !p.isAlive() || p.isSpectator() || com.jo112ss.rpgmod.compat.Downed.is(p)) return false;
        long now = p.level().getGameTime();
        if (now < NEXT.getOrDefault(p.getUUID(), 0L)) return false;
        NEXT.put(p.getUUID(), now + interval(p));
        ServerLevel level = (ServerLevel) p.level();
        p.swing(InteractionHand.OFF_HAND, p.getOffhandItem().getAttackAnimation(), false); // the client already swung its own arm
        Entity e = entityId >= 0 ? level.getEntity(entityId) : null;
        if (!(e instanceof LivingEntity target) || !target.isAlive() || target == p || target instanceof ArmorStand
                || target instanceof Player || (target instanceof TamableAnimal t && t.isOwnedBy(p))
                || p.distanceToSqr(target) > Math.pow(p.entityInteractionRange() + 1.5, 2)) {
            level.playSound(null, p.blockPosition(), SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.PLAYERS, 0.8F, 1.3F);
            return false;
        }
        float dmg = damage(p);
        boolean hit = target.hurtServer(level, p.damageSources().playerAttack(p), dmg);
        if (hit) {
            Vec3 dir = target.position().subtract(p.position()).multiply(1, 0, 1);
            if (dir.lengthSqr() > 1e-4) SkillExecutor.push(target, dir, 0.3, 0.1);
            p.getOffhandItem().hurtAndBreak(1, p, EquipmentSlot.OFFHAND);
            ServerFx.of("x_slash", target.position().add(0, target.getBbHeight() * 0.6, 0)).vec(dir).a(1.4).b(-0.7).color(0x9CB0FF).send(level);
            level.playSound(null, p.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.7F, 1.5F);
            p.setLastHurtMob(target);
        }
        if (com.jo112ss.rpgmod.devtest.AutoTestServer.ENABLED) {
            RpgMod.LOGGER.info("[AUTOTEST] off-hand strike: damage {} hit={} next in {} ticks", String.format("%.2f", dmg), hit, interval(p));
        }
        return hit;
    }

    public static void forget(UUID id) {
        NEXT.remove(id);
    }
}
