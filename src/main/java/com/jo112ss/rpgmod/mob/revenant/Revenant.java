package com.jo112ss.rpgmod.mob.revenant;

import com.jo112ss.rpgmod.item.ModItems;
import com.jo112ss.rpgmod.item.WeaponMaterial;
import com.jo112ss.rpgmod.item.WeaponType;
import com.jo112ss.rpgmod.job.SkillTasks;
import com.jo112ss.rpgmod.job.fx.ServerFx;
import com.jo112ss.rpgmod.mob.MobGear;
import com.jo112ss.rpgmod.mob.MobLevels;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.ai.goal.ZombieAttackGoal;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A fallen adventurer: a zombie that still fights in its old job's way, found deep underground.
 * Warriors charge and cleave, mages throw fireballs, archers shoot (and draw one strong shot),
 * priests heal their party and keep out of reach, thieves dart in for a slash. Every special move
 * is shown before it comes (a red line or cone on the ground, a ring where a fireball will land, a
 * laser sight, a casting circle), and none hits harder than a little over a normal blow.
 */
public class Revenant extends Zombie implements RangedAttackMob {
    public enum Role {
        WARRIOR(0xC0C8D8, WeaponType.SWORD), MAGE(0x9A6AFF, WeaponType.STAFF), ARCHER(0x6FBF5A, WeaponType.BOW), PRIEST(0xFFE6A0, WeaponType.MACE),
        THIEF(0x6A6A80, WeaponType.DAGGER);

        public final int color;
        public final WeaponType weapon;

        Role(int color, WeaponType weapon) {
            this.color = color;
            this.weapon = weapon;
        }

        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private static final EntityDataAccessor<Integer> ROLE = SynchedEntityData.defineId(Revenant.class, EntityDataSerializers.INT);

    /** Health compared with a zombie (20): a little tougher; the hits stay a zombie's. */
    public static final double HEALTH = 30;
    /** Damage of the special moves, times the revenant's attack. */
    public static final float CHARGE = 1.3F, CLEAVE = 1.5F, FIREBALL = 1.3F, POWER_SHOT = 1.6F, DART = 1.2F;
    public static final float HEAL_SHARE = 0.15F;

    private int skillCd = 60;
    private int windup;
    private int dashTicks;
    private Vec3 lockDir = Vec3.ZERO, lockAt = Vec3.ZERO;
    private boolean dashHit;
    private boolean goalsSet;

    public Revenant(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
        this.setCanPickUpLoot(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Zombie.createAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.SPAWN_REINFORCEMENTS_CHANCE, 0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder data) {
        super.defineSynchedData(data);
        data.define(ROLE, 0);
    }

    public Role role() {
        int i = this.entityData.get(ROLE);
        return Role.values()[Math.max(0, Math.min(Role.values().length - 1, i))];
    }

    /** Sets the job: its weapon, its moves, its pace. */
    public void setRole(Role role) {
        this.entityData.set(ROLE, role.ordinal());
        if (!level().isClientSide()) setupGoals();
    }

    private boolean armed;

    /** The job's weapon, by the level the revenant got (called on its first step, once it has one). */
    private void arm() {
        armed = true;
        int lv = Math.max(1, MobLevels.level(this));
        WeaponMaterial mat = lv >= 105 ? WeaponMaterial.DIAMOND : lv >= 30 ? WeaponMaterial.IRON : WeaponMaterial.COPPER;
        ItemStack weapon = new ItemStack(ModItems.get(role().weapon, mat));
        MobGear.arm(this, weapon);
        for (EquipmentSlot s : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) setItemSlot(s, ItemStack.EMPTY);
        var speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(THIEF_SPEED);
            if (role() == Role.THIEF) speed.addPermanentModifier(new AttributeModifier(THIEF_SPEED, 0.25, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    private static final net.minecraft.resources.Identifier THIEF_SPEED = net.minecraft.resources.Identifier.fromNamespaceAndPath(com.jo112ss.rpgmod.RpgMod.MODID, "revenant_thief_speed");

    @Override
    protected void addBehaviourGoals() {
        super.addBehaviourGoals();
    }

    /** The goals a job fights with (ranged jobs keep their distance, the priest stays back). */
    private void setupGoals() {
        if (goalsSet) return;
        goalsSet = true;
        switch (role()) {
            case MAGE -> {
                goalSelector.removeAllGoals(g -> g instanceof ZombieAttackGoal || g instanceof net.minecraft.world.entity.ai.goal.SpearUseGoal<?>);
                goalSelector.addGoal(2, new AvoidEntityGoal<>(this, Player.class, 5.0F, 1.0, 1.2));
            }
            case ARCHER -> {
                goalSelector.removeAllGoals(g -> g instanceof ZombieAttackGoal || g instanceof net.minecraft.world.entity.ai.goal.SpearUseGoal<?>);
                goalSelector.addGoal(3, new RangedBowAttackGoal<>(this, 1.0, 30, 16.0F));
            }
            case PRIEST -> {
                goalSelector.removeAllGoals(g -> g instanceof ZombieAttackGoal || g instanceof net.minecraft.world.entity.ai.goal.SpearUseGoal<?>);
                goalSelector.addGoal(2, new AvoidEntityGoal<>(this, Player.class, 6.0F, 1.0, 1.3));
                goalSelector.addGoal(5, new ZombieAttackGoal(this, 1.0, false)); // only when cornered
            }
            default -> { }
        }
    }

    @Override
    protected Component getTypeName() {
        return Component.translatable("entity.rpgmod.revenant." + role().id());
    }

    @Override
    protected boolean convertsInWater() {
        return false;
    }

    @Override
    public boolean isBaby() {
        return false;
    }

    @Override
    public void setBaby(boolean baby) {
        super.setBaby(false);
    }

    @Override
    public net.minecraft.world.entity.SpawnGroupData finalizeSpawn(net.minecraft.world.level.ServerLevelAccessor level, net.minecraft.world.DifficultyInstance difficulty,
            net.minecraft.world.entity.EntitySpawnReason reason, net.minecraft.world.entity.SpawnGroupData data) {
        // never a baby or a chicken jockey, no random tools, no picking up loot
        var out = super.finalizeSpawn(level, difficulty, reason, data instanceof ZombieGroupData ? data : new ZombieGroupData(false, false));
        setCanPickUpLoot(false);
        setCanBreakDoors(false);
        return out;
    }

    @Override
    protected void populateDefaultEquipmentSlots(net.minecraft.util.RandomSource random, net.minecraft.world.DifficultyInstance difficulty) {
    }

    @Override
    protected void populateDefaultEquipmentEnchantments(net.minecraft.world.level.ServerLevelAccessor level, net.minecraft.util.RandomSource random,
            net.minecraft.world.DifficultyInstance difficulty) {
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("RevenantRole", role().ordinal());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.entityData.set(ROLE, input.getIntOr("RevenantRole", 0));
        setupGoals();
    }

    /** Something of the old job: iron, lapis, arrows, gold, an emerald; and some rotten flesh. */
    @Override
    protected void dropCustomDeathLoot(ServerLevel level, net.minecraft.world.damagesource.DamageSource source, boolean killedByPlayer) {
        super.dropCustomDeathLoot(level, source, killedByPlayer);
        var r = getRandom();
        int flesh = r.nextInt(3);
        if (flesh > 0) spawnAtLocation(level, new ItemStack(Items.ROTTEN_FLESH, flesh));
        if (!killedByPlayer) return;
        ItemStack loot = switch (role()) {
            case WARRIOR -> r.nextFloat() < 0.25F ? new ItemStack(Items.IRON_INGOT) : new ItemStack(Items.IRON_NUGGET, 2 + r.nextInt(4));
            case MAGE -> new ItemStack(Items.LAPIS_LAZULI, 1 + r.nextInt(4));
            case ARCHER -> new ItemStack(Items.ARROW, 2 + r.nextInt(5));
            case PRIEST -> new ItemStack(Items.GOLD_NUGGET, 3 + r.nextInt(5));
            case THIEF -> r.nextFloat() < 0.35F ? new ItemStack(Items.EMERALD) : new ItemStack(Items.GOLD_NUGGET, 2 + r.nextInt(4));
        };
        spawnAtLocation(level, loot);
    }

    // ------------------------------------------------------------------ moves

    /** Dev test: logs the moves. */
    public static boolean testLog;

    /** Dev test: the next move comes in {@code ticks}. */
    public void readyIn(int ticks) {
        skillCd = ticks;
    }

    private void log(String msg, Object... args) {
        if (testLog) com.jo112ss.rpgmod.RpgMod.LOGGER.info("[AUTOTEST] revenant " + role().id() + " " + msg, args);
    }

    private float attack() {
        return (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (!goalsSet) setupGoals();
        if (!armed) {
            if (getMainHandItem().isEmpty()) arm();
            else armed = true;
        }
        LivingEntity t = getTarget();
        if (skillCd > 0) skillCd--;
        if (dashTicks > 0) {
            dashStep(level);
            return;
        }
        if (windup > 0) {
            getNavigation().stop();
            setDeltaMovement(getDeltaMovement().multiply(0, 1, 0));
            if (t != null && role() != Role.WARRIOR && role() != Role.THIEF) getLookControl().setLookAt(t, 30, 30);
            else lookAtDir(lockDir);
            if (--windup == 0) release(level, t);
            return;
        }
        if (role() == Role.MAGE && t != null) keepRange(t, 7, 14);
        if (role() == Role.PRIEST) staySupportive();
        if (skillCd > 0) return;
        if (role() == Role.PRIEST) {
            tryHeal(level);
            return;
        }
        if (t == null || !t.isAlive() || !(t instanceof Player pl) || pl.isCreative() || pl.isSpectator() || !hasLineOfSight(t)) return;
        double d = distanceTo(t);
        switch (role()) {
            case WARRIOR -> {
                if (d < 3.0) start(14, 120, flatTo(t), t, "rv_cone", 3.5, 0);
                else if (d >= 4 && d <= 12) start(16, 140, flatTo(t), t, "rv_line", d + 2, 0);
            }
            case MAGE -> {
                if (d <= 20) {
                    lockAt = t.position();
                    start(20, 80, flatTo(t), t, "rv_cast", 1.2, 0);
                    ServerFx.of("rv_circle", lockAt).a(FIRE_RADIUS).b(20 + travelTicks(lockAt)).color(0xFF5030).send(level);
                }
            }
            case ARCHER -> {
                if (d >= 5 && d <= 24) start(24, 140, flatTo(t), t, "x_aim", 0, 0);
            }
            case THIEF -> {
                if (d >= 3 && d <= 8) start(10, 100, flatTo(t), t, "rv_line", d + 2.5, 0);
            }
            default -> { }
        }
    }

    private static final double FIRE_RADIUS = 1.8, FIRE_SPEED = 0.8;

    private int travelTicks(Vec3 at) {
        return (int) Math.ceil(getEyePosition().distanceTo(at.add(0, 1, 0)) / FIRE_SPEED);
    }

    private Vec3 flatTo(LivingEntity t) {
        Vec3 d = t.position().subtract(position()).multiply(1, 0, 1);
        return d.lengthSqr() < 1e-6 ? Vec3.directionFromRotation(0, getYRot()) : d.normalize();
    }

    private void lookAtDir(Vec3 dir) {
        if (dir.lengthSqr() < 1e-6) return;
        float yaw = (float) (Math.atan2(dir.z, dir.x) * 180 / Math.PI) - 90;
        setYRot(yaw);
        setYHeadRot(yaw);
        yBodyRot = yaw;
    }

    /** Starts a move: its warning effect, then {@code wind} ticks later {@link #release}. */
    private void start(int wind, int cd, Vec3 dir, LivingEntity t, String warn, double len, int unused) {
        ServerLevel level = (ServerLevel) level();
        windup = wind;
        skillCd = cd + getRandom().nextInt(40);
        lockDir = dir;
        int c = role().color;
        switch (warn) {
            case "rv_line" -> ServerFx.of("rv_line", position()).vec(dir).a(len).b(wind).color(0xFF3030).send(level);
            case "rv_cone" -> ServerFx.of("rv_cone", position()).vec(dir).a(len).b(wind).color(0xFF3030).send(level);
            case "rv_cast" -> ServerFx.of("rv_cast", position()).follow(this).a(len).b(wind).color(c).send(level);
            case "x_aim" -> ServerFx.of("x_aim", getEyePosition()).follow(this).b(wind).color(0xFF4040).send(level);
            default -> { }
        }
        level.playSound(null, blockPosition(), role() == Role.MAGE || role() == Role.PRIEST ? SoundEvents.EVOKER_PREPARE_ATTACK : SoundEvents.ZOMBIE_AMBIENT,
                SoundSource.HOSTILE, 1.0F, role() == Role.THIEF ? 1.4F : 0.8F);
        log("starts a move ({} ticks)", wind);
    }

    private void release(ServerLevel level, LivingEntity t) {
        switch (role()) {
            case WARRIOR -> {
                if (t != null && distanceTo(t) < 4.5 && lockDir.dot(flatTo(t)) > 0.3) cleave(level);
                else dash(10, 0.65);
            }
            case MAGE -> fireball(level);
            case ARCHER -> {
                if (t != null && t.isAlive()) powerShot(level, t);
            }
            case THIEF -> dash(6, 0.95);
            case PRIEST -> heal(level);
        }
    }

    private void dash(int ticks, double speed) {
        dashTicks = ticks;
        dashHit = false;
        lockAt = lockDir.scale(speed);
        level().playSound(null, blockPosition(), SoundEvents.RAVAGER_STEP, SoundSource.HOSTILE, 1.0F, 1.4F);
    }

    private void dashStep(ServerLevel level) {
        dashTicks--;
        setDeltaMovement(lockAt.x, getDeltaMovement().y, lockAt.z);
        lookAtDir(lockDir);
        if (dashTicks % 2 == 0) ServerFx.of("x_dash", position()).vec(position().add(lockAt.scale(2))).color(role().color).send(level);
        if (dashHit) return;
        for (Player p : level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(0.6), p -> p.isAlive() && !p.isSpectator() && !p.isCreative())) {
            dashHit = true;
            float dmg = attack() * (role() == Role.THIEF ? DART : CHARGE);
            p.hurtServer(level, damageSources().mobAttack(this), dmg);
            p.knockback(0.6, -lockDir.x, -lockDir.z, damageSources().mobAttack(this), dmg);
            ServerFx.of("x_hit", p.position().add(0, 1, 0)).vec(lockDir).a(0.9).color(role().color).send(level);
            log("dash hits for {}", String.format("%.1f", dmg));
            break;
        }
    }

    private void cleave(ServerLevel level) {
        List<Player> hit = new ArrayList<>();
        for (Player p : level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(4), p -> p.isAlive() && !p.isSpectator() && !p.isCreative())) {
            Vec3 to = p.position().subtract(position()).multiply(1, 0, 1);
            if (to.length() > 3.8 || (to.lengthSqr() > 0.01 && to.normalize().dot(lockDir) < Math.cos(Math.toRadians(50)))) continue;
            p.hurtServer(level, damageSources().mobAttack(this), attack() * CLEAVE);
            hit.add(p);
        }
        ServerFx.of("x_slash", position().add(0, 1.1, 0).add(lockDir.scale(1.6))).vec(lockDir).a(3.5).b(0.2).color(0xFF6040).points(hit).send(level);
        level.playSound(null, blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.0F, 0.7F);
        swing(net.minecraft.world.InteractionHand.MAIN_HAND, getMainHandItem().getAttackAnimation(), true);
        log("cleaves {} for {}", hit.size(), String.format("%.1f", attack() * CLEAVE));
    }

    private void fireball(ServerLevel level) {
        Vec3 start = getEyePosition().add(Vec3.directionFromRotation(0, getYRot()).scale(0.6));
        Vec3 end = lockAt.add(0, 0.6, 0);
        Vec3 dir = end.subtract(start);
        double len = dir.length();
        if (len < 0.5) return;
        Vec3 v = dir.scale(FIRE_SPEED / len);
        ServerFx.of("x_bolt", start).vec(v).a(len).b(0.45).color(0xFF6020).send(level);
        level.playSound(null, blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1.0F, 1.0F);
        final Vec3[] pos = {start};
        int steps = (int) Math.ceil(len / FIRE_SPEED);
        SkillTasks.run(age -> {
            if (!isAlive()) return true;
            Vec3 next = pos[0].add(v);
            var block = level.clip(new ClipContext(pos[0], next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            boolean wall = block.getType() != HitResult.Type.MISS;
            Player direct = null;
            for (Player p : level.getEntitiesOfClass(Player.class, new AABB(pos[0], next).inflate(0.6), p -> p.isAlive() && !p.isSpectator())) {
                if (p.getBoundingBox().inflate(0.4).clip(pos[0], next).isPresent()) {
                    direct = p;
                    break;
                }
            }
            pos[0] = wall ? block.getLocation() : next;
            if (direct != null || wall || age >= steps) {
                explode(level, direct != null ? direct.position().add(0, 0.8, 0) : pos[0]);
                return true;
            }
            return false;
        });
        log("throws a fireball");
    }

    private void explode(ServerLevel level, Vec3 at) {
        List<Player> hit = new ArrayList<>();
        for (Player p : level.getEntitiesOfClass(Player.class, new AABB(at, at).inflate(FIRE_RADIUS + 1), p -> p.isAlive() && !p.isSpectator() && !p.isCreative())) {
            if (p.position().add(0, 0.9, 0).distanceTo(at) > FIRE_RADIUS + 0.6) continue;
            p.hurtServer(level, damageSources().mobAttack(this), attack() * FIREBALL);
            p.igniteForSeconds(2);
            hit.add(p);
        }
        ServerFx.of("x_burst", at).a(FIRE_RADIUS).b(0).color(0xFF6020).points(hit).send(level);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.6F, 1.4F);
        log("fireball lands, hits {}", hit.size());
    }

    private boolean powerShot;

    private void powerShot(ServerLevel level, LivingEntity t) {
        powerShot = true;
        performRangedAttack(t, 1.0F);
        powerShot = false;
        log("power shot");
    }

    @Override
    public void performRangedAttack(LivingEntity target, float power) {
        if (windup > 0 && !powerShot) return; // drawing the strong shot
        ItemStack bow = getMainHandItem();
        AbstractArrow arrow = ProjectileUtil.getMobArrow(this, new ItemStack(Items.ARROW), power, bow);
        if (powerShot) arrow.setBaseDamage((power * 2.0 + 0.6) * POWER_SHOT);
        double xd = target.getX() - getX(), yd = target.getY(0.3333) - arrow.getY(), zd = target.getZ() - getZ();
        double flat = Math.sqrt(xd * xd + zd * zd);
        if (level() instanceof ServerLevel sl) {
            Projectile.spawnProjectileUsingShoot(arrow, sl, new ItemStack(Items.ARROW), xd, yd + flat * (powerShot ? 0.1 : 0.2), zd, powerShot ? 2.6F : 1.6F,
                    powerShot ? 0 : 10 - sl.getDifficulty().getId() * 3);
        }
        playSound(powerShot ? SoundEvents.CROSSBOW_SHOOT : SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (getRandom().nextFloat() * 0.4F + 0.8F));
    }

    // ------------------------------------------------------------------ the priest

    private void staySupportive() {
        if (getNavigation().isInProgress() && getTarget() != null && distanceTo(getTarget()) < 6) return; // running away
        Revenant ally = nearestAlly(16);
        if (ally != null && distanceTo(ally) > 5) getNavigation().moveTo(ally, 1.0);
    }

    private void tryHeal(ServerLevel level) {
        boolean hurt = false;
        for (Revenant r : allies(10)) if (r.getHealth() < r.getMaxHealth() * 0.9F) hurt = true;
        if (!hurt) {
            skillCd = 20;
            return;
        }
        start(20, 100, Vec3.directionFromRotation(0, getYRot()), null, "rv_cast", 1.4, 0);
    }

    private void heal(ServerLevel level) {
        List<Revenant> healed = new ArrayList<>();
        for (Revenant r : allies(10)) {
            r.heal(r.getMaxHealth() * HEAL_SHARE);
            healed.add(r);
        }
        ServerFx.of("rv_heal", position()).follow(this).color(Role.PRIEST.color).points(healed).send(level);
        level.playSound(null, blockPosition(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.HOSTILE, 1.0F, 1.4F);
        log("heals {}", healed.size());
    }

    /** Revenants within {@code r} (this one too). */
    List<Revenant> allies(double r) {
        return level().getEntitiesOfClass(Revenant.class, getBoundingBox().inflate(r), x -> x.isAlive() && x.distanceTo(this) <= r);
    }

    private Revenant nearestAlly(double r) {
        Revenant best = null;
        double bd = Double.MAX_VALUE;
        for (Revenant x : allies(r)) {
            if (x == this || x.role() == Role.PRIEST) continue;
            double d = x.distanceToSqr(this);
            if (d < bd) {
                bd = d;
                best = x;
            }
        }
        return best;
    }

    /** Mages stay between {@code near} and {@code far} blocks from their target. */
    private void keepRange(LivingEntity t, double near, double far) {
        double d = distanceTo(t);
        if (d > far) getNavigation().moveTo(t, 1.0);
        else if (d >= near && getNavigation().isInProgress() && !(getNavigation().getPath() != null && d < near)) getNavigation().stop();
        getLookControl().setLookAt(t, 30, 30);
    }

    public static boolean isRevenant(Mob m) {
        return m instanceof Revenant;
    }
}
