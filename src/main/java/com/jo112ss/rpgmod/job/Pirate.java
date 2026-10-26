package com.jo112ss.rpgmod.job;

import static com.jo112ss.rpgmod.job.SkillExecutor.isEnemy;

import com.jo112ss.rpgmod.item.ModItems;
import com.jo112ss.rpgmod.item.WeaponType;
import com.jo112ss.rpgmod.job.fx.ServerFx;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The pirate's gun: right-click with the gunblade fires a shot along the crosshair. Six rounds, then
 * it reloads by itself (2s; sneak + right-click reloads early). A bayonet hit (normal melee attack)
 * loads one round. Powder kegs blow up when shot. The flag (jolly roger) gives endless rounds.
 */
public final class Pirate {
    private Pirate() {}

    public static final int COLOR = 0x2E86C1;
    /** A normal shot hits for this much of the attack. */
    public static final double SHOT_RATIO = 0.9;
    public static final double SHOT_RANGE = 32;
    public static final int SHOT_GAP = 10, RELOAD_TICKS = 40;

    /** Owner -> game time their flag stands until. */
    static final Map<UUID, Long> FLAG_UNTIL = new HashMap<>();
    /** Keg entity id -> what it does when it goes off. */
    static final Map<Integer, Runnable> KEGS = new HashMap<>();
    /** Owner -> their cannon (one each), and the captain's second one. */
    static final Map<UUID, SkillProp> CANNONS = new HashMap<>();
    static final Map<UUID, SkillProp> CANNONS2 = new HashMap<>();
    /** Owner -> game time their cannons fire as fast as they can until (captain's fleet barrage). */
    static final Map<UUID, Long> BARRAGE_UNTIL = new HashMap<>();

    static boolean barrage(ServerPlayer p) {
        Long until = BARRAGE_UNTIL.get(p.getUUID());
        return until != null && until > p.level().getGameTime();
    }

    public static boolean isPirate(Player p) {
        return JobManager.data(p).gunJob();
    }

    public static boolean holdsGun(Player p) {
        return ModItems.typeOf(p.getMainHandItem().getItem()) == WeaponType.GUNBLADE;
    }

    /** The flag stands: endless rounds and faster cannons. */
    public static boolean flagUp(ServerPlayer p) {
        Long until = FLAG_UNTIL.get(p.getUUID());
        return until != null && until > p.level().getGameTime();
    }

    /** Right-click with the gunblade. */
    public static void fire(ServerPlayer p) {
        JobData d = JobManager.data(p);
        long now = p.level().getGameTime();
        if (p.isShiftKeyDown() && d.ammo() < JobData.MAG && d.reloadEnd() <= now) {
            reload(p, d);
            return;
        }
        if (d.reloadEnd() > now) {
            p.sendSystemMessage(Component.translatable("rpgmod.pirate.reloading").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        boolean endless = flagUp(p);
        if (d.ammo() <= 0 && !endless) {
            reload(p, d);
            return;
        }
        if (!endless) d.setAmmo(d.ammo() - 1);
        if (shot(p, (float) (SHOT_RATIO * JobManager.physicalAttack(p)), p.getLookAngle(), SHOT_RANGE, 0.0) != null) HitMana.gunHit(p);
        p.getCooldowns().addCooldown(p.getMainHandItem(), SHOT_GAP);
        JobManager.markCombat(p);
        if (d.ammo() <= 0 && !endless) reload(p, d);
        JobManager.sync(p);
    }

    /** Starts reloading the gun (full again after {@link #RELOAD_TICKS}). */
    public static void reload(ServerPlayer p, JobData d) {
        long now = p.level().getGameTime();
        if (d.reloadEnd() > now) return;
        long end = now + RELOAD_TICKS;
        d.setReloadEnd(end);
        p.level().playSound(null, p.blockPosition(), SoundEvents.CROSSBOW_LOADING_START.value(), SoundSource.PLAYERS, 0.8F, 0.9F);
        JobManager.sync(p);
        SkillTasks.later(RELOAD_TICKS, () -> {
            if (d.reloadEnd() != end) return;
            d.setReloadEnd(0);
            d.setAmmo(JobData.MAG);
            if (p.isAlive()) {
                p.level().playSound(null, p.blockPosition(), SoundEvents.CROSSBOW_LOADING_END.value(), SoundSource.PLAYERS, 0.9F, 1.1F);
                JobManager.sync(p);
            }
        });
    }

    /** Loads rounds into the gun (bayonet hits, boarding shot). */
    public static void load(ServerPlayer p, int rounds) {
        JobData d = JobManager.data(p);
        if (d.ammo() >= JobData.MAG) return;
        d.setAmmo(d.ammo() + rounds);
        if (d.ammo() >= JobData.MAG) d.setReloadEnd(0);
        p.level().playSound(null, p.blockPosition(), SoundEvents.CROSSBOW_LOADING_MIDDLE.value(), SoundSource.PLAYERS, 0.5F, 1.6F);
        JobManager.sync(p);
    }

    /**
     * One shot along {@code dir}: hits the first enemy (or powder keg) on the line. Returns the enemy hit
     * (null if none). {@code spread} jitters the direction a little.
     */
    public static LivingEntity shot(ServerPlayer p, float damage, Vec3 dir, double range, double spread) {
        ServerLevel level = (ServerLevel) p.level();
        if (spread > 0) dir = dir.add(new Vec3(p.getRandom().nextGaussian(), p.getRandom().nextGaussian(), p.getRandom().nextGaussian()).scale(spread));
        dir = dir.normalize();
        Vec3 eye = p.getEyePosition();
        BlockHitResult block = level.clip(new ClipContext(eye, eye.add(dir.scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 end = block.getType() == HitResult.Type.MISS ? eye.add(dir.scale(range)) : block.getLocation();
        LivingEntity hit = null;
        SkillProp keg = null;
        double best = eye.distanceToSqr(end);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(eye, end).inflate(1), e -> isEnemy(p, e))) {
            var at = e.getBoundingBox().inflate(0.25).clip(eye, end);
            if (at.isPresent() && at.get().distanceToSqr(eye) < best) {
                best = at.get().distanceToSqr(eye);
                hit = e;
            }
        }
        for (SkillProp k : level.getEntitiesOfClass(SkillProp.class, new AABB(eye, end).inflate(1), k -> k.kind() == SkillProp.KEG)) {
            var at = k.getBoundingBox().inflate(0.2).clip(eye, end);
            if (at.isPresent() && at.get().distanceToSqr(eye) < best) {
                best = at.get().distanceToSqr(eye);
                hit = null;
                keg = k;
            }
        }
        Vec3 stop = eye.add(dir.scale(Math.sqrt(best)));
        Vec3 muzzle = eye.add(0, -0.25, 0).add(dir.scale(0.9)).add(SkillExecutor.Fx3.right(dir).scale(0.25));
        ServerFx.of("n_shot", muzzle).vec(stop).b(hit != null || keg != null ? 1 : 0).color(0xFFE0A0).send(level);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 0.9F, 1.6F + p.getRandom().nextFloat() * 0.2F);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.25F, 1.9F);
        if (hit != null) {
            hit.hurtServer(level, p.damageSources().source(SkillExecutor.SKILL_DAMAGE, p, p), damage);
            SkillExecutor.push(hit, dir, 0.2, 0.05);
        }
        if (keg != null) {
            Runnable boom = KEGS.remove(keg.getId());
            if (boom != null) boom.run();
        }
        return hit;
    }

    /** Nearest enemy the cannon can see. */
    static LivingEntity cannonTarget(ServerPlayer owner, SkillProp cannon, double range) {
        Vec3 from = cannon.position().add(0, 1.0, 0);
        LivingEntity best = null;
        double bd = range * range;
        for (LivingEntity e : owner.level().getEntitiesOfClass(LivingEntity.class, cannon.getBoundingBox().inflate(range), e -> isEnemy(owner, e))) {
            double dd = e.distanceToSqr(cannon);
            if (dd < bd && SkillExecutor.canSee((ServerLevel) owner.level(), from, e.position().add(0, e.getBbHeight() * 0.5, 0), cannon)) {
                bd = dd;
                best = e;
            }
        }
        return best;
    }

    static void forget(UUID id) {
        FLAG_UNTIL.remove(id);
        SkillProp c = CANNONS.remove(id);
        if (c != null) c.discard();
        SkillProp c2 = CANNONS2.remove(id);
        if (c2 != null) c2.discard();
        BARRAGE_UNTIL.remove(id);
    }

    /** The flag's attack bonus for players around it. */
    public static List<ServerPlayer> aroundFlag(ServerLevel level, Vec3 flag, double r) {
        return level.getEntitiesOfClass(ServerPlayer.class, new AABB(flag, flag).inflate(r), a -> a.isAlive() && !a.isSpectator() && a.position().distanceTo(flag) <= r);
    }
}
