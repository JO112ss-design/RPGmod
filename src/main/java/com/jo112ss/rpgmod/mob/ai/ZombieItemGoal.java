package com.jo112ss.rpgmod.mob.ai;

import com.jo112ss.rpgmod.job.SkillTasks;
import com.jo112ss.rpgmod.job.fx.ServerFx;
import com.jo112ss.rpgmod.mob.MobConfig;
import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Uses the item a zombie carries in its off hand: an ender pearl to close the distance, a fishing rod
 * to drag its target in, or TNT lobbed at whoever hides out of reach.
 */
public final class ZombieItemGoal extends Goal {
    private final Mob mob;
    private int cooldown = 40;

    public ZombieItemGoal(Mob mob) {
        this.mob = mob;
        setFlags(EnumSet.noneOf(Goal.Flag.class));
    }

    public static boolean carries(Mob mob) {
        ItemStack s = mob.getOffhandItem();
        return s.is(Items.ENDER_PEARL) || s.is(Items.FISHING_ROD) || s.is(Items.TNT);
    }

    private LivingEntity target() {
        LivingEntity t = mob.getTarget();
        return t instanceof Player p && !p.isCreative() && !p.isSpectator() && p.isAlive() ? p : null;
    }

    @Override
    public boolean canUse() {
        return mob.level() instanceof ServerLevel && carries(mob) && target() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        LivingEntity t = target();
        if (t == null || !(mob.level() instanceof ServerLevel sl)) return;
        double d = mob.distanceTo(t);
        if (!mob.getSensing().hasLineOfSight(t)) return;
        ItemStack held = mob.getOffhandItem();
        if (held.is(Items.ENDER_PEARL) && d >= 9 && d <= 32) {
            throwPearl(sl, t, held);
            cooldown = 120 + mob.getRandom().nextInt(60);
        } else if (held.is(Items.FISHING_ROD) && d >= 3.5 && d <= 14) {
            castRod(sl, t);
            cooldown = 70 + mob.getRandom().nextInt(40);
        } else if (held.is(Items.TNT) && d >= 5 && d <= 16 && MobConfig.breakBlocks()) {
            throwTnt(sl, t, held);
            cooldown = 140 + mob.getRandom().nextInt(60);
        }
    }

    private Vec3 hand() {
        return mob.getEyePosition().add(mob.getLookAngle().scale(0.4)).add(0, -0.3, 0);
    }

    private void throwPearl(ServerLevel sl, LivingEntity t, ItemStack held) {
        ThrownEnderpearl pearl = new ThrownEnderpearl(sl, mob, held.copyWithCount(1));
        Vec3 from = hand();
        pearl.setPos(from.x, from.y, from.z);
        double dx = t.getX() - from.x, dz = t.getZ() - from.z;
        double dy = t.getY() + 0.2 - from.y;
        double hd = Math.sqrt(dx * dx + dz * dz);
        pearl.shoot(dx, dy + hd * 0.2, dz, 1.5F, 1.0F);
        sl.addFreshEntity(pearl);
        held.shrink(1);
        mob.swingForAttack(InteractionHand.OFF_HAND);
        sl.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.ENDER_PEARL_THROW, SoundSource.HOSTILE, 0.8F, 0.6F);
    }

    private void castRod(ServerLevel sl, LivingEntity t) {
        Vec3 from = hand();
        double d = from.distanceTo(t.position());
        int flight = Math.max(3, (int) Math.ceil(d / 1.6));
        mob.swingForAttack(InteractionHand.OFF_HAND);
        sl.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.HOSTILE, 0.8F, 0.5F);
        ServerFx.of("rod_line", from).follow(mob).vec(t.position().add(0, t.getBbHeight() * 0.6, 0)).a(flight).b(t.getId()).send(sl);
        SkillTasks.later(flight, () -> {
            if (!mob.isAlive() || !t.isAlive() || mob.distanceTo(t) > 18 || !mob.getSensing().hasLineOfSight(t)) return;
            if (t instanceof Player p && p.isBlocking()) return; // a raised shield stops the hook
            Vec3 pull = mob.position().subtract(t.position());
            double len = pull.length();
            Vec3 dir = new Vec3(pull.x, 0, pull.z).normalize();
            double strength = Math.min(1.25, 0.3 + len * 0.07);
            t.push(dir.x * strength, 0.32, dir.z * strength);
            sl.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.HOSTILE, 1.0F, 0.8F);
            ServerFx.of("rod_pull", t.position().add(0, t.getBbHeight() * 0.6, 0)).follow(t).vec(mob.position()).send(sl);
        });
    }

    private void throwTnt(ServerLevel sl, LivingEntity t, ItemStack held) {
        Vec3 from = hand();
        PrimedTnt tnt = new PrimedTnt(sl, from.x, from.y, from.z, mob);
        int fuse = 50;
        tnt.setFuse(fuse);
        // a lob that lands at the target's feet shortly before the fuse runs out
        double flight = 20;
        double g = 0.04;
        Vec3 to = t.position();
        double vx = (to.x - from.x) / flight, vz = (to.z - from.z) / flight;
        double vy = (to.y - from.y + 0.5 * g * flight * flight) / flight;
        tnt.setDeltaMovement(vx * 1.08, vy, vz * 1.08); // a little extra for air drag
        sl.addFreshEntity(tnt);
        held.shrink(1);
        mob.swingForAttack(InteractionHand.OFF_HAND);
        sl.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.TNT_PRIMED, SoundSource.HOSTILE, 1.0F, 1.0F);
        if (held.isEmpty()) mob.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
    }
}
