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
            double chance = 0.25; // every level: a quarter of the zombies carry something
            if (r.nextDouble() < chance) {
                double roll = r.nextDouble();
                ItemStack item;
                if (roll < 0.25 && MobConfig.breakBlocks()) item = new ItemStack(Items.TNT, 1 + r.nextInt(2));
                else if (roll < 0.6) item = new ItemStack(Items.FISHING_ROD);
                else item = new ItemStack(Items.ENDER_PEARL, 2);
                mob.setItemSlot(EquipmentSlot.OFFHAND, item);
                mob.setDropChance(EquipmentSlot.OFFHAND, 0.2F);
            }
        }
        if (mob instanceof Creeper c) {
            c.maxSwell = Math.max(16, 30 - Math.min(14, lv / 5));
            double charged = 0.05;
            if (r.nextDouble() < charged) c.getEntityData().set(Creeper.DATA_IS_POWERED, true);
            // a fifth leap over walls and onto roofs
            if (r.nextDouble() < 0.2) c.getPersistentData().putBoolean(CreeperLeapGoal.KEY, true);
        }
        // a tenth of the spiders and skeletons carry two ender pearls (unseen, like the zombies' share)
        if ((mob instanceof Spider || isOverworldSkeleton(mob)) && r.nextDouble() < 0.1) mob.getPersistentData().putInt(MobPearlGoal.KEY, 2);
    }

    public static boolean isOverworldSkeleton(Entity e) {
        return e instanceof AbstractSkeleton && !(e instanceof WitherSkeleton);
    }

    /** Monsters that sense players through walls from the zombies' range (128 blocks by default). */
    public static boolean sensesFar(Entity e) {
        return isBuilderZombie(e) || e instanceof Creeper || e instanceof Spider || isOverworldSkeleton(e);
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
        if ((isBuilderZombie(mob) || mob instanceof ZombifiedPiglin) && mob.goalSelector.getAvailableGoals().stream().noneMatch(g -> g.getGoal() instanceof BuilderGoal)) {
            // zombified piglins only ever have a target once angered, so they build and dig only then
            BuilderGoal builder = new BuilderGoal(mob, lv);
            mob.goalSelector.addGoal(1, builder);
            mob.goalSelector.addGoal(1, new ZombieItemGoal(mob));
            // out of caves by walking or by digging stairs (ahead of the attack goal, which only heads for the spot below)
            if (isBuilderZombie(mob)) mob.goalSelector.addGoal(2, new SurfaceGoal(mob, builder));
        }
        if (isBuilderZombie(mob) && mob.targetSelector.getAvailableGoals().stream().noneMatch(g -> g.getGoal() instanceof ZombieSenseGoal)) {
            // zombies find players anywhere within their follow range, walls or not
            mob.targetSelector.addGoal(1, new ZombieSenseGoal(mob));
            // and keep re-pathing towards them while out of sight (vanilla only does so while it can see them)
            if (mob instanceof net.minecraft.world.entity.monster.zombie.Zombie zombie) {
                zombie.goalSelector.removeAllGoals(g -> g instanceof net.minecraft.world.entity.ai.goal.ZombieAttackGoal);
                zombie.goalSelector.addGoal(3, new net.minecraft.world.entity.ai.goal.ZombieAttackGoal(zombie, 1.0, true));
            }
        }
        if (!isBuilderZombie(mob) && sensesFar(mob) && mob.targetSelector.getAvailableGoals().stream().noneMatch(g -> g.getGoal() instanceof ZombieSenseGoal)) {
            // creepers, spiders and skeletons find players like the zombies do: anywhere in range, through walls
            mob.targetSelector.addGoal(1, new ZombieSenseGoal(mob));
            if (mob instanceof Spider || isOverworldSkeleton(mob)) mob.goalSelector.addGoal(1, new MobPearlGoal(mob));
            if (mob instanceof Creeper c) {
                // and keep walking towards them out of sight (vanilla creepers stop when they lose sight)
                c.goalSelector.removeAllGoals(g -> g.getClass() == net.minecraft.world.entity.ai.goal.MeleeAttackGoal.class);
                c.goalSelector.addGoal(4, new net.minecraft.world.entity.ai.goal.MeleeAttackGoal(c, 1.0, true));
                if (CreeperLeapGoal.isLeaper(c)) c.goalSelector.addGoal(1, new CreeperLeapGoal(c));
            }
        }
        if (mob instanceof Creeper) {
            // creepers wear no armour (older saves may still have some from before)
            for (EquipmentSlot s : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                if (!mob.getItemBySlot(s).isEmpty()) mob.setItemSlot(s, ItemStack.EMPTY);
            }
        }
        if (mob instanceof AbstractSkeleton sk) tuneBow(sk, lv);
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
     * spread, a chance of a three-arrow volley, and some arrows burn.
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
        // players move on their own client: their last reported movement is the reliable velocity
        Vec3 known = t.getKnownMovement();
        Vec3 vel = new Vec3(known.x, t.onGround() ? 0 : known.y, known.z);
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
        if (sl.getRandom().nextDouble() < 0.25) arrow.setRemainingFireTicks(100);
        if (shooter instanceof AbstractSkeleton) tipArrow(arrow, lv, sl.getRandom());
        if (!inVolley && shooter instanceof AbstractSkeleton sk && sl.getRandom().nextDouble() < 0.2) {
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

    /**
     * Skeleton bow handling by level: shoots from further away (15 blocks + 1 per 4 levels, up to 30)
     * and waits less between shots (vanilla 40 ticks on normal, 20 on hard; minus a tick per 3 levels).
     * Re-applied now and then because vanilla resets the interval when the skeleton changes weapons.
     */
    static void tuneBow(AbstractSkeleton sk, int lv) {
        if (sk.bowGoal == null) return;
        float radius = 15 + Math.min(15, lv / 4F);
        sk.bowGoal.attackRadiusSqr = radius * radius;
        int base = sk.level().getDifficulty() == net.minecraft.world.Difficulty.HARD ? 20 : 40;
        sk.bowGoal.setMinAttackInterval(Math.max(6, base - lv / 3));
    }

    /** Tipped arrows (any level, 30%): slowness, weakness, poison or a short blindness. */
    private static void tipArrow(AbstractArrow arrow, int lv, RandomSource r) {
        if (!(arrow instanceof net.minecraft.world.entity.projectile.arrow.Arrow a) || r.nextDouble() > 0.3) return;
        switch (r.nextInt(4)) {
            case 0 -> a.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, 100, 1));
            case 1 -> a.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WEAKNESS, 120, 0));
            case 2 -> a.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON, 80, 0));
            default -> a.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.BLINDNESS, 40, 0));
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
        if (mob instanceof AbstractSkeleton sk && e.tickCount % 40 == 0) tuneBow(sk, lv);
        if (!ready(mob, now)) return;
        double d = mob.distanceTo(t);
        if (mob instanceof AbstractSkeleton && !(mob instanceof WitherSkeleton) && mob.onGround() && d > 4 && aimingAt(p, mob)) {
            // the player is drawing a bow or crossbow on it: sidestep out of the line of fire
            Vec3 toMob = new Vec3(mob.getX() - p.getX(), 0, mob.getZ() - p.getZ()).normalize();
            Vec3 side = new Vec3(-toMob.z, 0, toMob.x).scale(sl.getRandom().nextBoolean() ? 1 : -1);
            mob.setDeltaMovement(side.x * 0.65, 0.3, side.z * 0.65);
            NEXT.put(mob, now + 40 + sl.getRandom().nextInt(30));
            return;
        }
        if (mob instanceof AbstractSkeleton && !(mob instanceof WitherSkeleton) && d < 3.5 && mob.onGround()) {
            // hop back out of sword reach
            Vec3 away = new Vec3(mob.getX() - t.getX(), 0, mob.getZ() - t.getZ());
            if (away.lengthSqr() < 1e-4) away = new Vec3(1, 0, 0);
            away = away.normalize();
            mob.setDeltaMovement(away.x * 0.6, 0.38, away.z * 0.6);
            sl.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.SKELETON_STEP, SoundSource.HOSTILE, 1.0F, 1.4F);
            NEXT.put(mob, now + 60 + sl.getRandom().nextInt(40));
        } else if (mob instanceof Spider && d >= 2 && d <= 14 && mob.getSensing().hasLineOfSight(t) && sl.getRandom().nextDouble() < 0.3) {
            spitWeb(sl, mob, p);
            NEXT.put(mob, now + 200 + sl.getRandom().nextInt(100));
        }
    }

    /** True while the player holds a drawn bow or crossbow and looks roughly at {@code mob}. */
    private static boolean aimingAt(Player p, Mob mob) {
        if (!p.isUsingItem()) return false;
        var item = p.getUseItem().getItem();
        if (!(item instanceof net.minecraft.world.item.BowItem) && !(item instanceof net.minecraft.world.item.CrossbowItem)) return false;
        Vec3 to = mob.getEyePosition().subtract(p.getEyePosition()).normalize();
        return p.getLookAngle().dot(to) > 0.97;
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
        if (mob instanceof Enderman em && victim.isAlive() && sl.getRandom().nextDouble() < 0.35) {
            Vec3 back = p.position().subtract(p.getLookAngle().multiply(1, 0, 1).normalize().scale(2.0));
            if (em.randomTeleport(back.x, p.getY(), back.z, true, s -> false)) {
                em.setTarget(p);
                sl.playSound(null, em.getX(), em.getY(), em.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0F, 0.8F);
            }
        }
    }
}
