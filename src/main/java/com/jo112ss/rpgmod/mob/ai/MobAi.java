package com.jo112ss.rpgmod.mob.ai;

import com.jo112ss.rpgmod.RpgMod;
import com.jo112ss.rpgmod.job.SkillTasks;
import com.jo112ss.rpgmod.job.fx.ServerFx;
import com.jo112ss.rpgmod.mob.MobConfig;
import com.jo112ss.rpgmod.mob.MobLevels;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.goal.SwellGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Smarter monsters. Goals are added every time a monster enters the world (goals are not saved);
 * equipment and creeper tuning are decided once, when the monster gets its level.
 *
 * <ul>
 *   <li>Zombies: build, bridge and dig towards their target ({@link BuilderGoal}); some carry an ender
 *       pearl, a fishing rod or TNT in the off hand and use it ({@link ZombieItemGoal}).</li>
 *   <li>Skeletons (and every other levelled archer): lead moving targets, aim tighter with level,
 *       fire volleys and burning arrows at higher levels, hop back when you close in.</li>
 *   <li>Creepers: hiss through walls, breach walls they cannot path around, shorter fuse and the odd
 *       charged creeper at high levels ({@link SmartSwellGoal}).</li>
 *   <li>Spiders spit webs at your feet; endermen strike back from behind you; monsters hurt by a player
 *       call nearby monsters of their kind.</li>
 * </ul>
 */
@EventBusSubscriber(modid = RpgMod.MODID)
public final class MobAi {
    private MobAi() {}

    // ------------------------------------------------------------------ setup

    /** Called once when a monster gets its level (new monsters only): equipment and creeper tuning. */
    public static void onFirstLevel(Mob mob, int lv) {
        if (!MobConfig.smartAi()) return;
        RandomSource r = mob.getRandom();
        if (isBuilderZombie(mob) && mob.getOffhandItem().isEmpty()) {
            double chance = Math.min(0.45, 0.08 + lv * 0.006);
            if (r.nextDouble() < chance) {
                double roll = r.nextDouble();
                ItemStack item;
                if (lv >= 8 && roll < 0.25 && MobConfig.breakBlocks()) item = new ItemStack(Items.TNT, 1 + r.nextInt(2));
                else if (roll < 0.6) item = new ItemStack(Items.FISHING_ROD);
                else item = new ItemStack(Items.ENDER_PEARL, 2);
                mob.setItemSlot(EquipmentSlot.OFFHAND, item);
                mob.setDropChance(EquipmentSlot.OFFHAND, 0.2F);
            }
        }
        if (mob instanceof Creeper c) {
            c.maxSwell = Math.max(16, 30 - Math.min(14, lv / 5));
            double charged = lv >= 40 ? 0.04 + (lv - 40) * 0.003 : 0;
            if (r.nextDouble() < charged) c.getEntityData().set(Creeper.DATA_IS_POWERED, true);
        }
    }

    public static boolean isBuilderZombie(Entity e) {
        return e instanceof Zombie && !(e instanceof ZombifiedPiglin) && !(e instanceof Drowned);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !MobConfig.smartAi()) return;
        Entity e = event.getEntity();
        if (e instanceof AbstractArrow arrow && !event.loadedFromDisk()) {
            aimArrow(arrow);
            return;
        }
        if (!(e instanceof Mob mob)) return;
        int lv = MobLevels.level(mob);
        if (lv <= 0) return;
        if (isBuilderZombie(mob) && mob.goalSelector.getAvailableGoals().stream().noneMatch(g -> g.getGoal() instanceof BuilderGoal)) {
            mob.goalSelector.addGoal(1, new BuilderGoal(mob, lv));
            mob.goalSelector.addGoal(1, new ZombieItemGoal(mob));
        }
        if (mob instanceof Creeper c && c.goalSelector.getAvailableGoals().stream().noneMatch(g -> g.getGoal() instanceof SmartSwellGoal)) {
            c.goalSelector.removeAllGoals(g -> g instanceof SwellGoal);
            c.goalSelector.addGoal(2, new SmartSwellGoal(c, lv));
        }
    }

    // ------------------------------------------------------------------ archers

    private static boolean inVolley;
    private static double volleyYaw;

    /**
     * Re-aims an arrow a levelled monster just fired: leads a moving target (better with level), tighter
     * spread, and from level 20 a chance of a three-arrow volley; from 35 some arrows burn.
     */
    private static void aimArrow(AbstractArrow arrow) {
        if (!(arrow.getOwner() instanceof Mob shooter) || !(arrow.level() instanceof ServerLevel sl)) return;
        int lv = MobLevels.level(shooter);
        LivingEntity t = shooter.getTarget();
        if (lv <= 0 || t == null) return;
        double speed = arrow.getDeltaMovement().length();
        if (speed < 0.5) return;
        Vec3 from = arrow.position();
        Vec3 aim = t.position().add(0, t.getBbHeight() * 0.45, 0);
        Vec3 vel = new Vec3(t.getX() - t.xo, t.onGround() ? 0 : t.getY() - t.yo, t.getZ() - t.zo);
        double lead = Math.min(1.0, 0.35 + lv * 0.012);
        double flight = from.distanceTo(aim) / speed;
        Vec3 predicted = aim;
        for (int i = 0; i < 2; i++) {
            predicted = aim.add(vel.scale(flight * lead));
            flight = from.distanceTo(predicted) / speed * 1.05;
        }
        Vec3 d = predicted.subtract(from);
        if (inVolley) d = rotY(d, volleyYaw);
        double drop = 0.5 * 0.05 * flight * flight; // arrow gravity
        float spread = (float) Math.max(0.3, 6.0 - lv * 0.12);
        arrow.shoot(d.x, d.y + drop, d.z, (float) speed, spread);
        if (lv >= 35 && sl.getRandom().nextDouble() < 0.3) arrow.setRemainingFireTicks(100);
        if (!inVolley && lv >= 20 && shooter instanceof AbstractSkeleton sk && sl.getRandom().nextDouble() < Math.min(0.35, (lv - 15) * 0.008)) {
            SkillTasks.later(1, () -> {
                if (!sk.isAlive() || sk.getTarget() == null) return;
                inVolley = true;
                try {
                    volleyYaw = Math.toRadians(9);
                    sk.performRangedAttack(sk.getTarget(), 1.0F);
                    volleyYaw = Math.toRadians(-9);
                    sk.performRangedAttack(sk.getTarget(), 1.0F);
                } finally {
                    inVolley = false;
                }
            });
        }
    }

    private static Vec3 rotY(Vec3 v, double a) {
        double c = Math.cos(a), s = Math.sin(a);
        return new Vec3(v.x * c - v.z * s, v.y, v.x * s + v.z * c);
    }

    // ------------------------------------------------------------------ per-tick behaviours

    private static final Map<Entity, Long> NEXT = new WeakHashMap<>();

    private static boolean ready(Entity e, long now) {
        Long n = NEXT.get(e);
        return n == null || now >= n;
    }

    @SubscribeEvent
    public static void onTick(EntityTickEvent.Post event) {
        Entity e = event.getEntity();
        if (!(e instanceof Mob mob) || e.tickCount % 5 != 0 || !(e.level() instanceof ServerLevel sl) || !MobConfig.smartAi()) return;
        if (!(mob instanceof AbstractSkeleton) && !(mob instanceof Spider)) return;
        int lv = MobLevels.level(mob);
        LivingEntity t = mob.getTarget();
        if (lv <= 0 || !(t instanceof Player p) || p.isCreative() || p.isSpectator()) return;
        long now = sl.getGameTime();
        if (!ready(mob, now)) return;
        double d = mob.distanceTo(t);
        if (mob instanceof AbstractSkeleton && !(mob instanceof WitherSkeleton) && lv >= 10 && d < 3.5 && mob.onGround()) {
            // hop back out of sword reach
            Vec3 away = new Vec3(mob.getX() - t.getX(), 0, mob.getZ() - t.getZ());
            if (away.lengthSqr() < 1e-4) away = new Vec3(1, 0, 0);
            away = away.normalize();
            mob.setDeltaMovement(away.x * 0.6, 0.38, away.z * 0.6);
            sl.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.SKELETON_STEP, SoundSource.HOSTILE, 1.0F, 1.4F);
            NEXT.put(mob, now + 60 + sl.getRandom().nextInt(40));
        } else if (mob instanceof Spider && lv >= 20 && d >= 3 && d <= 12 && mob.getSensing().hasLineOfSight(t) && sl.getRandom().nextDouble() < 0.12) {
            spitWeb(sl, mob, p);
            NEXT.put(mob, now + 200 + sl.getRandom().nextInt(100));
        }
    }

    /** A sticky glob that lands at the target's feet as a cobweb (gone again after 5 seconds). */
    private static void spitWeb(ServerLevel sl, Mob spider, Player p) {
        Vec3 from = spider.position().add(0, spider.getBbHeight() * 0.6, 0);
        BlockPos at = p.blockPosition();
        int flight = Math.max(3, (int) Math.ceil(from.distanceTo(p.position()) / 1.4));
        ServerFx.of("web_shot", from).vec(Vec3.atBottomCenterOf(at)).a(flight).send(sl);
        sl.playSound(null, spider.getX(), spider.getY(), spider.getZ(), SoundEvents.SPIDER_AMBIENT, SoundSource.HOSTILE, 1.0F, 1.6F);
        SkillTasks.later(flight, () -> {
            if (!sl.getBlockState(at).isAir()) return;
            sl.setBlockAndUpdate(at, Blocks.COBWEB.defaultBlockState());
            sl.playSound(null, at, SoundEvents.WOOL_PLACE, SoundSource.HOSTILE, 1.0F, 0.7F);
            SkillTasks.later(100, () -> {
                if (sl.getBlockState(at).is(Blocks.COBWEB)) sl.removeBlock(at, false);
            });
        });
    }

    // ------------------------------------------------------------------ reactions to being hurt

    @SubscribeEvent
    public static void onHurt(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        if (!(victim instanceof Mob mob) || !(victim.level() instanceof ServerLevel sl) || !MobConfig.smartAi()) return;
        if (!(event.getSource().getEntity() instanceof Player p) || p.isCreative() || p.isSpectator()) return;
        int lv = MobLevels.level(mob);
        if (lv <= 0) return;
        // call for help: up to four monsters of the same kind nearby join in
        if (mob instanceof Enemy && !(mob instanceof NeutralMob)) {
            int n = 0;
            for (Mob other : sl.getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(12), o -> o != mob && o.getType() == mob.getType() && o.isAlive())) {
                if (other.getTarget() == null) {
                    other.setTarget(p);
                    if (++n >= 4) break;
                }
            }
        }
        // strong endermen blink behind whoever hits them
        if (mob instanceof Enderman em && lv >= 40 && victim.isAlive() && sl.getRandom().nextDouble() < 0.35) {
            Vec3 back = p.position().subtract(p.getLookAngle().multiply(1, 0, 1).normalize().scale(2.0));
            if (em.randomTeleport(back.x, p.getY(), back.z, true, s -> false)) {
                em.setTarget(p);
                sl.playSound(null, em.getX(), em.getY(), em.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0F, 0.8F);
            }
        }
    }
}
