package com.jo112ss.rpgmod.mob.boss;

import com.jo112ss.rpgmod.RpgMod;
import com.jo112ss.rpgmod.job.fx.ServerFx;
import com.jo112ss.rpgmod.mob.MobConfig;
import com.jo112ss.rpgmod.mob.MobLevels;
import com.jo112ss.rpgmod.mob.core.MobLevelMath;
import com.jo112ss.rpgmod.registry.ModRegistries;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * King Slime, the blood moon boss. A giant crowned slime that hops after its target like any slime,
 * and every few seconds does one of its readable moves:
 * <ul>
 * <li>slam: crouches (a red circle marks where it will land), leaps and lands with a shockwave ring
 * that rolls outwards and can be jumped; afterwards it is stunned and takes extra damage</li>
 * <li>spit: puffs up and spits slow slime globs that slow you down</li>
 * <li>split: at 75 / 50 / 25 % health it shrinks and throws out small slimes</li>
 * <li>below half health: faster, more globs, and a triple slam ending in a long stun</li>
 * </ul>
 * No hit takes more than a fifth of a player's health (see {@link BossEvents}). Health follows the
 * boss level (highest player job level) and the number of players online. Leaves at dawn.
 */
public class KingSlime extends Slime {
    public static final int START_SIZE = 10;
    private static final int[] SPLIT_SIZES = {8, 7, 6};
    private static final float[] SPLIT_AT = {0.75F, 0.5F, 0.25F};

    public enum State { CHASE, WINDUP, LEAP, STUN, SPIT, ROAR, LEAVE }

    private static final EntityDataAccessor<Integer> DATA_STATE = SynchedEntityData.defineId(KingSlime.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(KingSlime.class, EntityDataSerializers.INT);

    // ---- server state
    private State state = State.CHASE;
    private int stateTicks;
    private int stateLength;
    private int nextMove = 80;
    private int splits;
    private int leapsLeft;
    private boolean tripleSlam;
    private Vec3 leapFrom = Vec3.ZERO, leapTo = Vec3.ZERO;
    private int leapTime;
    private double leapHeight;
    private int lastLand = -100;
    private int onlineSeen = -1;
    private double healthTarget;
    private final Set<UUID> participants = new HashSet<>();
    private final Map<UUID, Integer> touchCooldown = new HashMap<>();
    private final List<Wave> waves = new ArrayList<>();
    private ServerBossEvent bossBar;

    // ---- client animation
    public int clientStateTicks;

    /** A shockwave ring rolling out from a landing: it hits players on the ground once as it passes. */
    private static final class Wave {
        final Vec3 c;
        double r;
        final double max;
        final float damage;
        final Set<UUID> hit = new HashSet<>();

        Wave(Vec3 c, double r, double max, float damage) {
            this.c = c;
            this.r = r;
            this.max = max;
            this.damage = damage;
        }
    }

    public KingSlime(EntityType<? extends KingSlime> type, Level level) {
        super(type, level);
        this.setSize(START_SIZE, true);
        this.xpReward = 200;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 400)
                .add(Attributes.FOLLOW_RANGE, 48)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.9)
                .add(Attributes.ARMOR, 4)
                .add(Attributes.ATTACK_DAMAGE, 4);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(DATA_STATE, 0);
        b.define(DATA_PHASE, 1);
    }

    @Override
    protected void addTargetingGoals() {
        // any player in range, whatever the height difference (vanilla slimes only look 4 blocks up/down)
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    // ------------------------------------------------------------------ size, health, level

    @Override
    public void setSize(int size, boolean updateHealth) {
        super.setSize(size, updateHealth);
        // vanilla scales speed and damage with size: the king keeps its own numbers
        this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(4);
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(phase() >= 2 ? 0.85 : 0.7);
        this.xpReward = 200;
    }

    @Override
    protected void setCubeMobHealth(int actualSize) {
        if (healthTarget > 0) this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(healthTarget);
        else super.setCubeMobHealth(actualSize);
    }

    public int bossLevel() {
        return Math.max(1, MobLevels.level(this));
    }

    public int phase() {
        return this.entityData.get(DATA_PHASE);
    }

    public State state() {
        return State.values()[Mth.clamp(this.entityData.get(DATA_STATE), 0, State.values().length - 1)];
    }

    /**
     * Max health = base x level growth x (1 + share per extra player online). The current share of
     * health is kept, so people joining mid-fight do not reset it.
     */
    public void recomputeHealth(boolean fill) {
        if (!(this.level() instanceof ServerLevel sl)) return;
        int online = Math.max(1, sl.getServer().getPlayerList().getPlayerCount());
        onlineSeen = online;
        double hp = MobConfig.kingHealth() * MobLevelMath.healthMultiplier(MobConfig.rules(), bossLevel())
                * (1 + MobConfig.bossPerPlayer() * (online - 1));
        float ratio = this.getMaxHealth() > 0 ? this.getHealth() / this.getMaxHealth() : 1;
        healthTarget = hp;
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(hp);
        this.setHealth(fill ? this.getMaxHealth() : Math.max(1, ratio * this.getMaxHealth()));
    }

    // ------------------------------------------------------------------ tick

    @Override
    protected boolean isImmobile() {
        // during its moves the king is steered by the move itself, not by the slime AI
        return super.isImmobile() || (!this.level().isClientSide() && state != State.CHASE);
    }

    @Override
    protected int getJumpDelay() {
        // divided by 3 while chasing: 10-16 ticks between hops, 6-11 below half health
        return phase() >= 2 ? 18 + this.random.nextInt(14) : 30 + this.random.nextInt(18);
    }

    @Override
    public void tick() {
        if (this.level() instanceof ServerLevel sl) serverTick(sl);
        super.tick();
        if (this.level().isClientSide()) clientStateTicks++;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (DATA_STATE.equals(accessor)) clientStateTicks = 0;
    }

    private void setState(State s, int length) {
        state = s;
        stateTicks = 0;
        stateLength = length;
        this.entityData.set(DATA_STATE, s.ordinal());
    }

    private void serverTick(ServerLevel sl) {
        if (!this.isAlive()) return;
        stateTicks++;
        if (this.tickCount % 10 == 0) updateBossBar(sl);
        if (this.tickCount % 20 == 0) {
            if (sl.getServer().getPlayerList().getPlayerCount() != onlineSeen && healthTarget > 0) recomputeHealth(false);
            if (state != State.LEAVE && !BloodMoon.isBloodMoon(sl) && !BloodMoon.testKeep) startLeaving(sl);
        }
        tickWaves(sl);
        touchCooldown.replaceAll((k, v) -> v - 1);
        touchCooldown.values().removeIf(v -> v <= 0);
        if (state != State.LEAVE) checkSplit(sl);

        switch (state) {
            case CHASE -> chase(sl);
            case WINDUP -> windup(sl);
            case LEAP -> leap(sl);
            case STUN -> {
                this.setDeltaMovement(this.getDeltaMovement().multiply(0.5, 1, 0.5));
                if (stateTicks % 8 == 0) sl.playSound(null, this.blockPosition(), SoundEvents.SLIME_SQUISH_SMALL, SoundSource.HOSTILE, 0.8F, 0.6F);
                if (stateTicks >= stateLength) endMove();
            }
            case SPIT -> spit(sl);
            case ROAR -> {
                this.setDeltaMovement(this.getDeltaMovement().multiply(0.3, 1, 0.3));
                if (stateTicks >= stateLength) endMove();
            }
            case LEAVE -> leave(sl);
        }
    }

    private LivingEntity target(ServerLevel sl) {
        LivingEntity t = this.getTarget();
        if (t instanceof Player p && (!p.isAlive() || p.isSpectator() || p.isCreative())) t = null;
        if (t == null || t.distanceToSqr(this) > 48 * 48) {
            Player best = null;
            double bd = 48 * 48;
            for (ServerPlayer p : sl.players()) {
                if (!p.isAlive() || p.isSpectator() || p.isCreative()) continue;
                double d = p.distanceToSqr(this);
                if (d < bd) {
                    bd = d;
                    best = p;
                }
            }
            t = best;
            this.setTarget(best);
        }
        return t;
    }

    private void chase(ServerLevel sl) {
        LivingEntity t = target(sl);
        if (t == null) return;
        if (--nextMove > 0 || !this.onGround()) return;
        double dist = Math.sqrt(t.distanceToSqr(this));
        if (dist > 36) return;
        double roll = this.random.nextDouble();
        if (phase() >= 2) {
            if (roll < 0.4) startSlam(sl, t, 3);
            else if (roll < 0.7) startSlam(sl, t, 1);
            else startSpit(sl);
        } else {
            if (roll < 0.65 || dist > 14) startSlam(sl, t, 1);
            else startSpit(sl);
        }
    }

    private void endMove() {
        setState(State.CHASE, 0);
        nextMove = phase() >= 2 ? 70 + this.random.nextInt(40) : 100 + this.random.nextInt(50);
    }

    // ------------------------------------------------------------------ slam

    private float hitRadius() {
        return this.getBbWidth() / 2 + 1.2F;
    }

    private void startSlam(ServerLevel sl, LivingEntity t, int leaps) {
        leapsLeft = leaps;
        tripleSlam = leaps > 1;
        windupAt(sl, t);
    }

    private void windupAt(ServerLevel sl, LivingEntity t) {
        Vec3 to = groundBelow(sl, t.position());
        Vec3 flat = to.subtract(this.position());
        double dist = Math.hypot(flat.x, flat.z);
        int windup = tripleSlam ? (leapsLeft == 3 ? 16 : 9) : (phase() >= 2 ? 15 : 18);
        leapFrom = this.position();
        leapTo = to;
        leapTime = (int) Mth.clamp(10 + dist * 0.9, 14, 28);
        leapHeight = tripleSlam ? 3 + dist * 0.15 : 4.5 + dist * 0.25;
        setState(State.WINDUP, windup);
        this.getNavigation().stop();
        // the marker stays until the landing: windup + flight
        ServerFx.of("king_mark", to).a(hitRadius()).b(windup + leapTime).send(sl);
        sl.playSound(null, this.blockPosition(), SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 3.0F, 0.5F);
    }

    /** Lands on the ground under a point (a player in the air or on a ledge). */
    private static Vec3 groundBelow(ServerLevel sl, Vec3 at) {
        var pos = net.minecraft.core.BlockPos.containing(at);
        for (int i = 0; i < 12; i++) {
            if (!sl.getBlockState(pos.below()).getCollisionShape(sl, pos.below()).isEmpty()) break;
            pos = pos.below();
        }
        return new Vec3(at.x, pos.getY(), at.z);
    }

    private void windup(ServerLevel sl) {
        this.setDeltaMovement(this.getDeltaMovement().multiply(0.2, 1, 0.2));
        if (stateTicks >= stateLength) {
            leapFrom = this.position();
            setState(State.LEAP, leapTime);
            sl.playSound(null, this.blockPosition(), SoundEvents.SLIME_JUMP, SoundSource.HOSTILE, 3.0F, 0.6F);
            this.hasImpulse = true;
        }
    }

    private void leap(ServerLevel sl) {
        double s = Math.min(1, (stateTicks + 1) / (double) stateLength);
        Vec3 want = leapFrom.lerp(leapTo, s).add(0, leapHeight * 4 * s * (1 - s), 0);
        this.setDeltaMovement(want.subtract(this.position()));
        this.hasImpulse = true;
        this.needsSync = true;
        this.resetFallDistance();
        boolean blocked = stateTicks > stateLength / 2 && this.onGround();
        if (s >= 1 || blocked || stateTicks > stateLength + 10) land(sl);
    }

    private void land(ServerLevel sl) {
        Vec3 c = this.position();
        lastLand = this.tickCount;
        float lv = 1; // damage is scaled by level in MobLevels
        float slam = 8 * lv, wave = 5 * lv;
        double r = hitRadius();
        for (ServerPlayer p : sl.players()) {
            if (!p.isAlive() || p.isSpectator()) continue;
            Vec3 d = p.position().subtract(c);
            if (Math.hypot(d.x, d.z) <= r && Math.abs(d.y) < 3) {
                p.hurtServer(sl, this.damageSources().mobAttack(this), slam);
                fling(p, c, 0.9, 0.55);
                participants.add(p.getUUID());
            }
        }
        waves.add(new Wave(c, this.getBbWidth() / 2, this.getBbWidth() / 2 + (tripleSlam ? 6 : 9), wave));
        ServerFx.of("king_land", c).a(this.getBbWidth() / 2).b(tripleSlam ? 6 : 9).send(sl);
        sl.playSound(null, this.blockPosition(), SoundEvents.SLIME_BLOCK_FALL, SoundSource.HOSTILE, 3.0F, 0.5F);
        sl.playSound(null, this.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.2F, 0.7F);
        sl.playSound(null, this.blockPosition(), SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 3.0F, 0.4F);
        this.setDeltaMovement(Vec3.ZERO);
        if (--leapsLeft > 0) {
            LivingEntity t = target(sl);
            if (t != null) {
                windupAt(sl, t);
                return;
            }
        }
        int stun = tripleSlam ? 55 : phase() >= 2 ? 32 : 42;
        setState(State.STUN, stun);
        ServerFx.of("king_stun", this.position()).follow(this).a(this.getBbWidth()).b(stun).send(sl);
    }

    private void tickWaves(ServerLevel sl) {
        for (var it = waves.iterator(); it.hasNext(); ) {
            Wave w = it.next();
            w.r += 0.55;
            for (ServerPlayer p : sl.players()) {
                if (!p.isAlive() || p.isSpectator() || w.hit.contains(p.getUUID())) continue;
                Vec3 d = p.position().subtract(w.c);
                double h = Math.hypot(d.x, d.z);
                // the ring hugs the ground: jumping over it (feet a block up) dodges it
                if (Math.abs(h - w.r) < 0.8 && d.y > -1.5 && d.y < 0.9) {
                    w.hit.add(p.getUUID());
                    p.hurtServer(sl, this.damageSources().mobAttack(this), w.damage);
                    fling(p, w.c, 0.6, 0.35);
                }
            }
            if (w.r > w.max) it.remove();
        }
    }

    private static void fling(LivingEntity e, Vec3 from, double out, double up) {
        Vec3 d = e.position().subtract(from).multiply(1, 0, 1);
        d = d.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : d.normalize();
        e.setDeltaMovement(e.getDeltaMovement().add(d.scale(out)).add(0, up, 0));
        e.needsSync = true;
    }

    // ------------------------------------------------------------------ spit

    private void startSpit(ServerLevel sl) {
        setState(State.SPIT, 14);
        ServerFx.of("mob_warn", this.position()).follow(this).a(this.getBbWidth() / 2).b(14).color(0x6CFF7A).send(sl);
        sl.playSound(null, this.blockPosition(), SoundEvents.PUFFER_FISH_BLOW_UP, SoundSource.HOSTILE, 2.0F, 0.6F);
    }

    private void spit(ServerLevel sl) {
        this.setDeltaMovement(this.getDeltaMovement().multiply(0.2, 1, 0.2));
        if (stateTicks < stateLength) return;
        LivingEntity t = target(sl);
        if (t != null) {
            int n = phase() >= 2 ? 5 : 3;
            Vec3 from = this.position().add(0, this.getBbHeight() * 0.6, 0);
            Vec3 aim = t.position().add(0, t.getBbHeight() * 0.5, 0).subtract(from);
            double dist = aim.length();
            for (int i = 0; i < n; i++) {
                double yaw = (i - (n - 1) / 2.0) * Math.toRadians(14);
                Vec3 dir = new Vec3(aim.x * Math.cos(yaw) - aim.z * Math.sin(yaw), aim.y, aim.x * Math.sin(yaw) + aim.z * Math.cos(yaw));
                Snowball ball = new Snowball(sl, this, new ItemStack(Items.SLIME_BALL));
                ball.setPos(from.add(dir.normalize().scale(this.getBbWidth() * 0.45)));
                ball.getPersistentData().putBoolean(BossEvents.GLOB, true);
                // slow and lobbed a little: easy to sidestep
                ball.shoot(dir.x, dir.y + dist * 0.12, dir.z, 0.75F, 2.0F);
                sl.addFreshEntity(ball);
            }
            sl.playSound(null, this.blockPosition(), SoundEvents.LLAMA_SPIT, SoundSource.HOSTILE, 2.5F, 0.5F);
        }
        endMove();
    }

    /** A glob hit a player: a little damage and a slow. */
    void globHit(ServerLevel sl, Snowball ball, LivingEntity victim) {
        victim.hurtServer(sl, this.damageSources().mobProjectile(ball, this), 3);
        victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1), this);
        ServerFx.of("king_glob", ball.position()).send(sl);
    }

    // ------------------------------------------------------------------ split

    private void checkSplit(ServerLevel sl) {
        if (splits >= SPLIT_AT.length || this.getHealth() > this.getMaxHealth() * SPLIT_AT[splits]) return;
        int size = SPLIT_SIZES[splits];
        splits++;
        this.setSize(size, false);
        if (splits >= 2 && phase() < 2) {
            this.entityData.set(DATA_PHASE, 2);
            this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.85);
            if (bossBar != null) bossBar.setColor(BossEvent.BossBarColor.PURPLE);
        }
        int online = Math.max(1, sl.getServer().getPlayerList().getPlayerCount());
        int kids = Math.min(6, 2 + (online - 1));
        for (int i = 0; i < kids; i++) {
            Slime kid = EntityTypes.SLIME.create(sl, EntitySpawnReason.MOB_SUMMONED);
            if (kid == null) continue;
            kid.setSize(2, true);
            double a = Math.PI * 2 * i / kids + this.random.nextDouble() * 0.5;
            kid.snapTo(this.getX() + Math.cos(a) * 1.5, this.getY() + this.getBbHeight() * 0.6, this.getZ() + Math.sin(a) * 1.5, this.random.nextFloat() * 360, 0);
            kid.setDeltaMovement(Math.cos(a) * 0.5, 0.45, Math.sin(a) * 0.5);
            kid.setData(ModRegistries.MOB_LEVEL, bossLevel());
            kid.getPersistentData().putBoolean(BossEvents.CHILD, true);
            sl.addFreshEntity(kid);
        }
        // a short roar instead of whatever it was doing (a slam in the air still lands first)
        if (state != State.LEAP) {
            setState(State.ROAR, 22);
            waves.clear();
        }
        ServerFx.of("king_split", this.position()).follow(this).a(this.getBbWidth()).b(splits).send(sl);
        sl.playSound(null, this.blockPosition(), SoundEvents.SLIME_DEATH, SoundSource.HOSTILE, 3.0F, 0.5F);
        sl.playSound(null, this.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.5F, 1.4F);
        if (phase() >= 2 && splits == 2) {
            for (ServerPlayer p : bossPlayers()) {
                p.sendSystemMessage(Component.translatable("rpgmod.boss.king_slime.enraged").withStyle(ChatFormatting.LIGHT_PURPLE), true);
            }
        }
    }

    // ------------------------------------------------------------------ damage taken / dealt

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (state == State.LEAVE) return false;
        if (source.getEntity() instanceof ServerPlayer p) participants.add(p.getUUID());
        if (state == State.STUN) damage *= 1.3F; // the opening after a slam
        return super.hurtServer(level, source, damage);
    }

    /** Touching the king only hurts while it bounces onto you, so melee can stand next to it. */
    @Override
    public void playerTouch(Player player) {
        if (state != State.CHASE && state != State.SPIT) return;
        boolean bouncing = !this.onGround() || this.tickCount - lastLand < 4;
        if (!bouncing || touchCooldown.containsKey(player.getUUID())) return;
        if (this.canDealDamage()) {
            touchCooldown.put(player.getUUID(), 20);
            this.dealDamage(player);
        }
    }

    @Override
    protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    protected float getSoundVolume() {
        return 2.0F;
    }

    // ------------------------------------------------------------------ boss bar

    private List<ServerPlayer> bossPlayers() {
        return bossBar == null ? List.of() : List.copyOf(bossBar.getPlayers());
    }

    private void updateBossBar(ServerLevel sl) {
        if (bossBar == null) {
            bossBar = new ServerBossEvent(UUID.randomUUID(), name(), phase() >= 2 ? BossEvent.BossBarColor.PURPLE : BossEvent.BossBarColor.BLUE,
                    BossEvent.BossBarOverlay.NOTCHED_10);
        }
        bossBar.setName(name());
        bossBar.setProgress(Mth.clamp(this.getHealth() / this.getMaxHealth(), 0, 1));
        for (ServerPlayer p : sl.players()) {
            boolean near = p.distanceToSqr(this) < 72 * 72;
            if (near && !bossBar.getPlayers().contains(p)) bossBar.addPlayer(p);
            else if (!near && bossBar.getPlayers().contains(p)) bossBar.removePlayer(p);
        }
        for (ServerPlayer p : List.copyOf(bossBar.getPlayers())) {
            if (p.level() != sl || p.isRemoved()) bossBar.removePlayer(p);
        }
    }

    private Component name() {
        return Component.translatable("entity.rpgmod.king_slime").append(Component.literal("  Lv " + bossLevel()));
    }

    @Override
    public void remove(RemovalReason reason) {
        if (bossBar != null) bossBar.removeAllPlayers();
        super.remove(reason);
    }

    // ------------------------------------------------------------------ leaving and dying

    private void startLeaving(ServerLevel sl) {
        setState(State.LEAVE, 70);
        waves.clear();
        this.setInvulnerable(true);
        this.noPhysics = true;
        ServerFx.of("king_leave", this.position()).follow(this).a(this.getBbWidth()).b(70).send(sl);
        sl.playSound(null, this.blockPosition(), SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 3.0F, 0.3F);
        BossEvents.broadcast(sl.getServer(), Component.translatable("rpgmod.boss.king_slime.left").withStyle(ChatFormatting.GRAY));
        dissolveChildren(sl);
    }

    private void leave(ServerLevel sl) {
        this.noPhysics = true;
        this.setDeltaMovement(0, -this.getBbHeight() / 60.0, 0);
        this.hasImpulse = true;
        if (stateTicks >= stateLength) this.discard();
    }

    private void dissolveChildren(ServerLevel sl) {
        for (Slime s : sl.getEntitiesOfClass(Slime.class, this.getBoundingBox().inflate(64), s -> s != this && s.getPersistentData().getBooleanOr(BossEvents.CHILD, false))) {
            ServerFx.of("king_glob", s.position()).send(sl);
            s.discard();
        }
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!(this.level() instanceof ServerLevel sl) || state == State.LEAVE) return;
        if (bossBar != null) bossBar.removeAllPlayers();
        dissolveChildren(sl);
        Set<UUID> who = new HashSet<>(participants);
        for (ServerPlayer p : sl.getEntitiesOfClass(ServerPlayer.class, this.getBoundingBox().inflate(48))) who.add(p.getUUID());
        int books = MobConfig.bossBooks();
        List<String> names = new ArrayList<>();
        for (UUID id : who) {
            ServerPlayer p = sl.getServer().getPlayerList().getPlayer(id);
            if (p == null) continue;
            names.add(p.getGameProfile().name());
            if (books > 0) {
                ItemStack stack = new ItemStack(ModRegistries.SKILL_BOOK.get(), books);
                if (!p.getInventory().add(stack)) p.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
                p.sendSystemMessage(Component.translatable("rpgmod.boss.reward", books).withStyle(ChatFormatting.GOLD));
            }
            sl.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        ServerFx.of("king_death", this.position()).a(this.getBbWidth()).send(sl);
        BossEvents.broadcast(sl.getServer(), Component.translatable("rpgmod.boss.king_slime.defeated", String.join(", ", names)).withStyle(ChatFormatting.GOLD));
        RpgMod.LOGGER.info("[boss] King Slime defeated, {} participants rewarded", names.size());
    }

    // ------------------------------------------------------------------ save

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.putInt("KingSplits", splits);
        out.putInt("KingPhase", phase());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        splits = in.getIntOr("KingSplits", 0);
        this.entityData.set(DATA_PHASE, in.getIntOr("KingPhase", 1));
        super.readAdditionalSaveData(in);
    }

    // ------------------------------------------------------------------ dev test

    /** Autotest: start a move right away ("slam", "triple", "spit"). */
    public void testMove(String move) {
        if (!(this.level() instanceof ServerLevel sl)) return;
        LivingEntity t = target(sl);
        if (t == null) return;
        setState(State.CHASE, 0);
        switch (move) {
            case "slam" -> startSlam(sl, t, 1);
            case "triple" -> startSlam(sl, t, 3);
            case "spit" -> startSpit(sl);
            default -> { }
        }
    }

    public int splits() {
        return splits;
    }

    public void playBossSound(SoundEvent s, float vol, float pitch) {
        this.level().playSound(null, this.blockPosition(), s, SoundSource.HOSTILE, vol, pitch);
    }

    public AABB area(double r) {
        return this.getBoundingBox().inflate(r);
    }
}
