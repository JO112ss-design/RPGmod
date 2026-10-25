package com.jo112ss.rpgmod.mob.boss;

import com.jo112ss.rpgmod.RpgMod;
import com.jo112ss.rpgmod.job.fx.ServerFx;
import com.jo112ss.rpgmod.mob.MobConfig;
import com.jo112ss.rpgmod.mob.MobLevels;
import com.jo112ss.rpgmod.mob.core.MobLevelMath;
import com.jo112ss.rpgmod.registry.ModRegistries;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * The Golem, the other blood moon boss: a giant dark stone golem with a glowing core. It walks after
 * its target and strings together a combo of heavy, readable moves (two, three below half health),
 * then <b>overheats</b>: it kneels with its core open for a few seconds and takes 1.5x damage.
 * <ul>
 * <li>slam: both arms up, a red circle around it, then a hit inside and a ground shockwave (jumpable)</li>
 * <li>boulder: tears a rock from the ground and throws it at a player (marked landing spot)</li>
 * <li>fissure: a stomp, red lanes towards the players, then rock spikes burst up along them</li>
 * <li>charge: a marked lane, then a run along it; into a wall it is dazed for a long while</li>
 * <li>sweep: when someone stays close, an arm pulled back and a wide swing that throws them away</li>
 * <li>rockfall: a roar, then rocks fall on marked spots around every player</li>
 * <li>below half health: faster, the slam becomes a triple quake (three rings to jump in rhythm),
 * and at 50% and 25% it raises <b>stone armour</b>: glowing pillars around it halve all damage
 * until they are broken (then it staggers)</li>
 * </ul>
 * It leaps at a target it cannot walk to. No single hit takes more than a fifth of a player's health
 * (see {@link BossEvents}). Health follows the boss level and the players online. Crumbles at dawn.
 */
public class Golem extends Monster implements BloodMoonBoss {
    public static final float SCALE = 2.2F;

    /** What the golem is doing (synced: the model poses by it). */
    public enum State { CHASE, SLAM_UP, SLAM, QUAKE, THROW_UP, THROW, STOMP, CHARGE_AIM, CHARGE, SWEEP_UP, SWEEP, ROAR, OVERHEAT, STUN, LEAP, ARMOUR, LEAVE }

    enum Move { SLAM, QUAKE, THROW, FISSURE, CHARGE, SWEEP, ROCKFALL }

    private static final EntityDataAccessor<Integer> DATA_STATE = SynchedEntityData.defineId(Golem.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(Golem.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_SHIELD = SynchedEntityData.defineId(Golem.class, EntityDataSerializers.BOOLEAN);

    private static final float[] ARMOUR_AT = {0.5F, 0.25F};

    // ---- server state
    private State state = State.CHASE;
    private int stateTicks, stateLength;
    private int nextMove = 50;
    private final Deque<Move> combo = new ArrayDeque<>();
    private float damageTakenMult = 1;
    private int armours;
    private int onlineSeen = -1;
    private double healthTarget;
    private float toughness = 1;
    private int punchCooldown;
    private int stuckTicks;
    private Vec3 lastPos = Vec3.ZERO;
    private final Set<UUID> participants = new HashSet<>();
    private final List<Wave> waves = new ArrayList<>();
    private final List<Rock> rocks = new ArrayList<>();
    private final List<Fissure> fissures = new ArrayList<>();
    private ServerBossEvent bossBar;
    // moves
    private int quakesDone;
    private Vec3 aimDir = Vec3.ZERO;
    private final Set<UUID> chargeHit = new HashSet<>();
    private Vec3 leapFrom = Vec3.ZERO, leapTo = Vec3.ZERO;
    private int leapTime;
    private double leapHeight;
    /** Dev test counters. */
    public int wallHits, shieldBreaks;

    // ---- client animation
    public int clientStateTicks;
    public int attackAnim;

    /** A shockwave ring rolling out along the ground: hits each player once as it passes, jumpable. */
    private static final class Wave {
        final Vec3 c;
        double r;
        final double max, speed;
        final float damage;
        final Set<UUID> hit = new HashSet<>();

        Wave(Vec3 c, double r, double max, double speed, float damage) {
            this.c = c;
            this.r = r;
            this.max = max;
            this.speed = speed;
            this.damage = damage;
        }
    }

    /** A thrown boulder or a falling rock: lands on a marked spot at a given tick. */
    private record Rock(Vec3 at, int landTick, float radius, float damage, boolean boulder) {}

    /** Rock spikes bursting up one after another along a lane. */
    private static final class Fissure {
        final Vec3 from, dir;
        final int start, length;
        int done;
        final Set<UUID> hit = new HashSet<>();

        Fissure(Vec3 from, Vec3 dir, int start, int length) {
            this.from = from;
            this.dir = dir;
            this.start = start;
            this.length = length;
        }
    }

    public Golem(EntityType<? extends Golem> type, Level level) {
        super(type, level);
        this.xpReward = 200;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 800)
                .add(Attributes.FOLLOW_RANGE, 48)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.ARMOR, 6)
                .add(Attributes.ATTACK_DAMAGE, 7)
                .add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.STEP_HEIGHT, 1.6);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(DATA_STATE, 0);
        b.define(DATA_PHASE, 1);
        b.define(DATA_SHIELD, false);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    // ------------------------------------------------------------------ health, level, state

    public int bossLevel() {
        return Math.max(1, MobLevels.level(this));
    }

    public int phase() {
        return this.entityData.get(DATA_PHASE);
    }

    public boolean shielded() {
        return this.entityData.get(DATA_SHIELD);
    }

    public State state() {
        return State.values()[Mth.clamp(this.entityData.get(DATA_STATE), 0, State.values().length - 1)];
    }

    @Override
    public void recomputeHealth(boolean fill) {
        if (!(this.level() instanceof ServerLevel sl)) return;
        int online = Math.max(1, sl.getServer().getPlayerList().getPlayerCount());
        onlineSeen = online;
        double hp = MobConfig.golemHealth() * MobLevelMath.healthMultiplier(MobConfig.rules(), bossLevel())
                * (1 + MobConfig.bossPerPlayer() * (online - 1));
        float ratio = this.getMaxHealth() > 0 ? this.getHealth() / this.getMaxHealth() : 1;
        double applied = Math.min(hp, 1000);
        toughness = (float) (hp / applied);
        healthTarget = applied;
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(applied);
        this.setHealth(fill ? this.getMaxHealth() : Math.max(1, ratio * this.getMaxHealth()));
    }

    @Override
    public float toughness() {
        return toughness;
    }

    public float effectiveMaxHealth() {
        return this.getMaxHealth() * toughness;
    }

    private void setState(State s, int length) {
        state = s;
        stateTicks = 0;
        stateLength = length;
        damageTakenMult = 1;
        this.entityData.set(DATA_STATE, s.ordinal());
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (DATA_STATE.equals(accessor)) clientStateTicks = 0;
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == 4) attackAnim = 10; // punch swing on the client
        else super.handleEntityEvent(id);
    }

    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || (!this.level().isClientSide() && state != State.CHASE);
    }

    // ------------------------------------------------------------------ tick

    @Override
    public void tick() {
        if (this.level() instanceof ServerLevel sl) serverTick(sl);
        super.tick();
        if (this.level().isClientSide()) {
            clientStateTicks++;
            if (attackAnim > 0) attackAnim--;
        }
    }

    private void serverTick(ServerLevel sl) {
        if (!this.isAlive()) return;
        stateTicks++;
        if (punchCooldown > 0) punchCooldown--;
        if (this.tickCount % 10 == 0) updateBossBar(sl);
        if (this.tickCount % 20 == 0) {
            if (sl.getServer().getPlayerList().getPlayerCount() != onlineSeen && healthTarget > 0) recomputeHealth(false);
            if (state != State.LEAVE && !BloodMoon.isBloodMoon(sl) && !BloodMoon.testKeep) startLeaving(sl);
            if (shielded() && pillars(sl).isEmpty()) breakShield(sl);
        }
        if (shielded() && this.tickCount % 6 == 0) {
            for (GolemPillar p : pillars(sl)) {
                ServerFx.of("golem_link", p.position().add(0, p.getBbHeight() * 0.85, 0)).vec(this.position().add(0, this.getBbHeight() * 0.55, 0)).send(sl);
            }
        }
        tickWaves(sl);
        tickRocks(sl);
        tickFissures(sl);
        if (state != State.LEAVE) checkArmour(sl);

        switch (state) {
            case CHASE -> chase(sl);
            case SLAM_UP -> slamUp(sl);
            case SLAM, ROAR -> {
                hold();
                if (stateTicks >= stateLength) moveDone();
            }
            case ARMOUR -> { // back to the fight (no overheat right after raising the armour)
                hold();
                if (stateTicks >= stateLength) {
                    setState(State.CHASE, 0);
                    nextMove = 20;
                }
            }
            case QUAKE -> quake(sl);
            case THROW_UP -> throwUp(sl);
            case THROW -> {
                hold();
                if (stateTicks >= stateLength) moveDone();
            }
            case STOMP -> stomp(sl);
            case CHARGE_AIM -> chargeAim(sl);
            case CHARGE -> charge(sl);
            case SWEEP_UP -> sweepUp(sl);
            case SWEEP -> {
                hold();
                if (stateTicks >= stateLength) moveDone();
            }
            case OVERHEAT, STUN -> {
                hold();
                if (state == State.OVERHEAT && stateTicks % 10 == 0) {
                    sl.playSound(null, this.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, 1.0F, 0.5F);
                }
                if (stateTicks >= stateLength) {
                    if (state == State.OVERHEAT) restAfterOverheat();
                    else moveDone();
                }
            }
            case LEAP -> leap(sl);
            case LEAVE -> leave(sl);
        }
    }

    private void hold() {
        this.getNavigation().stop();
        this.setDeltaMovement(this.getDeltaMovement().multiply(0.2, 1, 0.2));
    }

    private boolean valid(Player p) {
        return p.isAlive() && !p.isSpectator() && !p.isCreative() && !com.jo112ss.rpgmod.compat.Downed.is(p);
    }

    private LivingEntity target(ServerLevel sl) {
        LivingEntity t = this.getTarget();
        if (t instanceof Player p && !valid(p)) t = null;
        if (t != null && !t.isAlive()) t = null;
        if (t == null || t.distanceToSqr(this) > 48 * 48) {
            Player best = null;
            double bd = 48 * 48;
            for (ServerPlayer p : sl.players()) {
                if (!valid(p)) continue;
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

    private List<ServerPlayer> fighters(ServerLevel sl, double range) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : sl.players()) if (valid(p) && p.distanceToSqr(this) <= range * range) out.add(p);
        return out;
    }

    private double reach() {
        return this.getBbWidth() / 2 + 1.6;
    }

    // ------------------------------------------------------------------ rhythm: combo -> overheat

    private void chase(ServerLevel sl) {
        LivingEntity t = target(sl);
        if (t == null) {
            this.getNavigation().stop();
            return;
        }
        double dist = Math.sqrt(t.distanceToSqr(this));
        this.getLookControl().setLookAt(t, 20, 20);
        if (this.tickCount % 8 == 0) this.getNavigation().moveTo(t, 1.0);
        // a plain punch when someone is in reach
        double flat = Math.hypot(t.getX() - this.getX(), t.getZ() - this.getZ());
        if (punchCooldown <= 0 && flat <= reach() && Math.abs(t.getY() - this.getY()) < 4) {
            punchCooldown = phase() >= 2 ? 24 : 32;
            sl.broadcastEntityEvent(this, (byte) 4);
            t.hurtServer(sl, this.damageSources().mobAttack(this), 7);
            fling(t, this.position(), 0.7, 0.4);
            sl.playSound(null, this.blockPosition(), SoundEvents.IRON_GOLEM_ATTACK, SoundSource.HOSTILE, 2.0F, 0.6F);
        }
        // stuck (walls, a tower, water): leap at the target
        if (this.tickCount % 20 == 0) {
            boolean moved = this.position().distanceTo(lastPos) > 1.0;
            lastPos = this.position();
            stuckTicks = moved || dist < reach() + 1 ? 0 : stuckTicks + 20;
            if (stuckTicks >= 60 && this.onGround()) {
                stuckTicks = 0;
                startLeap(sl, t);
                return;
            }
        }
        if (--nextMove > 0 || !this.onGround()) return;
        if (dist > 36) return;
        if (combo.isEmpty()) buildCombo(sl);
        Move m = combo.poll();
        if (m != null) start(sl, m, t);
    }

    private void buildCombo(ServerLevel sl) {
        boolean p2 = phase() >= 2;
        List<Move> pool = new ArrayList<>(p2
                ? List.of(Move.QUAKE, Move.QUAKE, Move.SLAM, Move.THROW, Move.THROW, Move.FISSURE, Move.FISSURE, Move.CHARGE, Move.CHARGE, Move.ROCKFALL)
                : List.of(Move.SLAM, Move.SLAM, Move.THROW, Move.THROW, Move.FISSURE, Move.FISSURE, Move.CHARGE, Move.ROCKFALL));
        int n = p2 ? 3 : 2;
        // someone hugging it: the sweep comes first
        if (!fighters(sl, reach() + 1.5).isEmpty() && this.random.nextDouble() < 0.7) {
            combo.add(Move.SWEEP);
            n--;
        }
        while (n > 0 && !pool.isEmpty()) {
            Move m = pool.get(this.random.nextInt(pool.size()));
            pool.removeIf(x -> x == m);
            combo.add(m);
            n--;
        }
    }

    private void start(ServerLevel sl, Move m, LivingEntity t) {
        this.getNavigation().stop();
        switch (m) {
            case SLAM -> startSlam(sl, false);
            case QUAKE -> startSlam(sl, true);
            case THROW -> startThrow(sl, t);
            case FISSURE -> startStomp(sl);
            case CHARGE -> startCharge(sl, t);
            case SWEEP -> startSweep(sl);
            case ROCKFALL -> startRockfall(sl);
        }
    }

    private void moveDone() {
        if (!combo.isEmpty()) {
            setState(State.CHASE, 0);
            nextMove = phase() >= 2 ? 8 + this.random.nextInt(6) : 12 + this.random.nextInt(8);
        } else {
            startOverheat();
        }
    }

    private void startOverheat() {
        int len = phase() >= 2 ? 55 : 70;
        setState(State.OVERHEAT, len);
        damageTakenMult = 1.5F;
        if (this.level() instanceof ServerLevel sl) {
            ServerFx.of("golem_overheat", this.position()).follow(this).a(this.getBbWidth()).b(len).send(sl);
            sl.playSound(null, this.blockPosition(), SoundEvents.LAVA_EXTINGUISH, SoundSource.HOSTILE, 2.0F, 0.6F);
            sl.playSound(null, this.blockPosition(), SoundEvents.IRON_GOLEM_DAMAGE, SoundSource.HOSTILE, 2.0F, 0.5F);
        }
    }

    private void restAfterOverheat() {
        setState(State.CHASE, 0);
        nextMove = phase() >= 2 ? 24 + this.random.nextInt(14) : 34 + this.random.nextInt(20);
    }

    private void stun(ServerLevel sl, int ticks, float mult) {
        setState(State.STUN, ticks);
        damageTakenMult = mult;
        ServerFx.of("king_stun", this.position()).follow(this).a(this.getBbWidth()).b(ticks).send(sl);
    }

    private void face(Vec3 dir) {
        this.setYRot((float) Math.toDegrees(Math.atan2(-dir.x, dir.z)));
        this.yBodyRot = this.yHeadRot = this.getYRot();
    }

    private Vec3 toward(LivingEntity t) {
        Vec3 d = t.position().subtract(this.position()).multiply(1, 0, 1);
        return d.lengthSqr() < 1e-4 ? Vec3.directionFromRotation(0, this.getYRot()) : d.normalize();
    }

    // ------------------------------------------------------------------ slam / quake

    private float slamRadius() {
        return this.getBbWidth() / 2 + 3.2F;
    }

    private void startSlam(ServerLevel sl, boolean quake) {
        int up = phase() >= 2 ? 12 : 16;
        quakesDone = quake ? 0 : -1;
        setState(State.SLAM_UP, up);
        ServerFx.of("king_mark", this.position()).a(slamRadius()).b(up).send(sl);
        sl.playSound(null, this.blockPosition(), SoundEvents.IRON_GOLEM_REPAIR, SoundSource.HOSTILE, 2.0F, 0.4F);
    }

    private void slamUp(ServerLevel sl) {
        hold();
        if (stateTicks < stateLength) return;
        if (quakesDone >= 0) {
            setState(State.QUAKE, 3 * quakeEvery() + 4);
            quakesDone = 0;
            slamHit(sl, 6, 4, 11);
        } else {
            slamHit(sl, 9, 5, 9);
            fistsDown(phase() >= 2 ? 14 : 18);
        }
    }

    /** Fists on the ground after a slam: a short opening (x1.3). */
    private void fistsDown(int ticks) {
        setState(State.SLAM, ticks);
        damageTakenMult = 1.3F;
    }

    private int quakeEvery() {
        return 14;
    }

    private void quake(ServerLevel sl) {
        hold();
        int k = stateTicks;
        if (k > 0 && k % quakeEvery() == 0 && quakesDone < 2) {
            quakesDone++;
            slamHit(sl, 6, 4, 11);
        }
        if (stateTicks >= stateLength) fistsDown(22);
    }

    /** Both fists on the ground: a hit around it and a shockwave rolling out. */
    private void slamHit(ServerLevel sl, float dmg, float waveDmg, double reach) {
        Vec3 c = this.position();
        double r = slamRadius();
        for (ServerPlayer p : sl.players()) {
            if (!p.isAlive() || p.isSpectator()) continue;
            Vec3 d = p.position().subtract(c);
            if (Math.hypot(d.x, d.z) <= r && Math.abs(d.y) < 3) {
                p.hurtServer(sl, this.damageSources().mobAttack(this), dmg);
                fling(p, c, 0.9, 0.55);
                participants.add(p.getUUID());
            }
        }
        waves.add(new Wave(c, this.getBbWidth() / 2, this.getBbWidth() / 2 + reach, 0.6, waveDmg));
        ServerFx.of("golem_land", c).a(this.getBbWidth() / 2).b((float) reach).send(sl);
        sl.playSound(null, this.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.5F, 0.6F);
        sl.playSound(null, this.blockPosition(), SoundEvents.IRON_GOLEM_DAMAGE, SoundSource.HOSTILE, 2.0F, 0.4F);
    }

    private void tickWaves(ServerLevel sl) {
        for (var it = waves.iterator(); it.hasNext(); ) {
            Wave w = it.next();
            w.r += w.speed;
            for (ServerPlayer p : sl.players()) {
                if (!p.isAlive() || p.isSpectator() || w.hit.contains(p.getUUID())) continue;
                Vec3 d = p.position().subtract(w.c);
                double h = Math.hypot(d.x, d.z);
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
        e.syncVelocity = true;
    }

    // ------------------------------------------------------------------ boulder throw

    private static Vec3 groundBelow(ServerLevel sl, Vec3 at) {
        BlockPos pos = BlockPos.containing(at);
        for (int i = 0; i < 12; i++) {
            if (!sl.getBlockState(pos.below()).getCollisionShape(sl, pos.below()).isEmpty()) break;
            pos = pos.below();
        }
        return new Vec3(at.x, pos.getY(), at.z);
    }

    private void startThrow(ServerLevel sl, LivingEntity t) {
        int up = phase() >= 2 ? 16 : 20;
        aimDir = toward(t);
        face(aimDir);
        setState(State.THROW_UP, up);
        ServerFx.of("golem_rip", this.position().add(aimDir.scale(this.getBbWidth() * 0.6))).a(this.getBbWidth()).send(sl);
        sl.playSound(null, this.blockPosition(), SoundEvents.STONE_BREAK, SoundSource.HOSTILE, 2.0F, 0.5F);
    }

    private void throwUp(ServerLevel sl) {
        hold();
        LivingEntity t = target(sl);
        if (t != null) face(toward(t));
        if (stateTicks < stateLength) return;
        Vec3 from = this.position().add(0, this.getBbHeight() + 0.5, 0);
        List<LivingEntity> aims = new ArrayList<>();
        if (t != null) aims.add(t);
        if (phase() >= 2) { // a second boulder at someone else (or ahead of the same one)
            for (ServerPlayer p : fighters(sl, 34)) if (p != t) {
                aims.add(p);
                break;
            }
        }
        int i = 0;
        for (LivingEntity a : aims) {
            Vec3 lead = a.position().add(a.getDeltaMovement().multiply(10, 0, 10));
            if (i == 1 && a == t) lead = lead.add(toward(a).scale(3));
            Vec3 at = groundBelow(sl, lead);
            int flight = (int) Mth.clamp(10 + from.distanceTo(at) * 0.5, 12, 26) + i * 6;
            rocks.add(new Rock(at, this.tickCount + flight, 2.4F, 8, true));
            ServerFx.of("golem_rock_mark", at).a(2.4F).b(flight).color(0xFF3A30).send(sl);
            ServerFx.of("golem_boulder", from).vec(at).a(flight).send(sl);
            i++;
        }
        setState(State.THROW, 10);
        sl.playSound(null, this.blockPosition(), SoundEvents.IRON_GOLEM_ATTACK, SoundSource.HOSTILE, 2.5F, 0.4F);
    }

    // ------------------------------------------------------------------ rockfall

    private void startRockfall(ServerLevel sl) {
        setState(State.ROAR, 24);
        ServerFx.of("mob_warn", this.position()).follow(this).a(this.getBbWidth() / 2 + 1).b(24).color(0xFF6A30).send(sl);
        sl.playSound(null, this.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 3.0F, 0.5F);
        int extra = phase() >= 2 ? 4 : 2;
        int land = this.tickCount + 34;
        for (ServerPlayer p : fighters(sl, 34)) {
            List<Vec3> spots = new ArrayList<>();
            spots.add(p.position().add(p.getDeltaMovement().multiply(8, 0, 8)));
            for (int i = 0; i < extra; i++) {
                double a = this.random.nextDouble() * Math.PI * 2, r = 2 + this.random.nextDouble() * 4;
                spots.add(p.position().add(Math.cos(a) * r, 0, Math.sin(a) * r));
            }
            for (int i = 0; i < spots.size(); i++) {
                Vec3 at = groundBelow(sl, spots.get(i));
                int when = land + i * 4;
                rocks.add(new Rock(at, when, 1.9F, 5, false));
                ServerFx.of("golem_rock_mark", at).a(1.9F).b(when - this.tickCount).send(sl);
            }
        }
    }

    private void tickRocks(ServerLevel sl) {
        for (var it = rocks.iterator(); it.hasNext(); ) {
            Rock r = it.next();
            if (this.tickCount < r.landTick) continue;
            it.remove();
            for (ServerPlayer p : sl.players()) {
                Vec3 v = p.position().subtract(r.at);
                double h = Math.hypot(v.x, v.z);
                if (h < r.radius && Math.abs(v.y) < 2.5) {
                    p.hurtServer(sl, this.damageSources().mobAttack(this), r.damage);
                    fling(p, r.at, 0.5, 0.35);
                } else if (r.boulder && h < r.radius + 2.5 && Math.abs(v.y) < 2.5) {
                    p.hurtServer(sl, this.damageSources().mobAttack(this), 3); // flying fragments
                }
            }
            ServerFx.of("golem_rock_hit", r.at).a(r.radius).b(r.boulder ? 1 : 0).send(sl);
            sl.playSound(null, r.at.x, r.at.y, r.at.z, SoundEvents.STONE_BREAK, SoundSource.HOSTILE, 2.0F, r.boulder ? 0.5F : 0.8F);
            if (r.boulder) sl.playSound(null, r.at.x, r.at.y, r.at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.8F, 1.1F);
        }
    }

    // ------------------------------------------------------------------ fissure

    private static final int FISSURE_LEN = 16;

    private void startStomp(ServerLevel sl) {
        int up = phase() >= 2 ? 12 : 16;
        setState(State.STOMP, up + 6);
        int start = this.tickCount + up + 10; // the spikes come after the lanes have shown a moment
        int lanes = 0;
        for (ServerPlayer p : fighters(sl, 30)) {
            if (lanes >= (phase() >= 2 ? 4 : 3)) break;
            Vec3 dir = toward(p);
            Vec3 from = this.position().add(dir.scale(this.getBbWidth() / 2));
            fissures.add(new Fissure(from, dir, start, FISSURE_LEN));
            ServerFx.of("golem_lane", from).vec(dir.scale(FISSURE_LEN)).a(1.6F).b(start - this.tickCount).send(sl);
            lanes++;
        }
        if (lanes == 0) {
            Vec3 dir = Vec3.directionFromRotation(0, this.getYRot());
            Vec3 from = this.position().add(dir.scale(this.getBbWidth() / 2));
            fissures.add(new Fissure(from, dir, start, FISSURE_LEN));
            ServerFx.of("golem_lane", from).vec(dir.scale(FISSURE_LEN)).a(1.6F).b(start - this.tickCount).send(sl);
        }
        sl.playSound(null, this.blockPosition(), SoundEvents.IRON_GOLEM_STEP, SoundSource.HOSTILE, 3.0F, 0.4F);
    }

    private void stomp(ServerLevel sl) {
        hold();
        if (stateTicks == stateLength - 6) {
            ServerFx.of("golem_land", this.position()).a(this.getBbWidth() / 2).b(3).send(sl);
            sl.playSound(null, this.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.0F, 0.8F);
        }
        if (stateTicks >= stateLength) moveDone();
    }

    private void tickFissures(ServerLevel sl) {
        for (var it = fissures.iterator(); it.hasNext(); ) {
            Fissure f = it.next();
            int k = this.tickCount - f.start;
            if (k < 0) continue;
            // two spikes a tick along the lane
            for (int s = 0; s < 2 && f.done < f.length; s++) {
                Vec3 at = groundBelow(sl, f.from.add(f.dir.scale(f.done + 0.5)));
                f.done++;
                ServerFx.of("golem_spike", at).send(sl);
                for (ServerPlayer p : sl.players()) {
                    if (!p.isAlive() || p.isSpectator() || f.hit.contains(p.getUUID())) continue;
                    Vec3 d = p.position().subtract(at);
                    if (Math.hypot(d.x, d.z) < 1.2 && d.y > -1 && d.y < 2.5) {
                        f.hit.add(p.getUUID());
                        p.hurtServer(sl, this.damageSources().mobAttack(this), 6);
                        p.setDeltaMovement(p.getDeltaMovement().add(0, 0.8, 0));
                        p.syncVelocity = true;
                    }
                }
                if (f.done % 3 == 0) sl.playSound(null, at.x, at.y, at.z, SoundEvents.POINTED_DRIPSTONE_LAND, SoundSource.HOSTILE, 1.5F, 0.6F);
            }
            if (f.done >= f.length) it.remove();
        }
    }

    // ------------------------------------------------------------------ charge

    private double chargeSpeed() {
        return phase() >= 2 ? 0.95 : 0.8;
    }

    private void startCharge(ServerLevel sl, LivingEntity t) {
        aimDir = toward(t);
        int aim = phase() >= 2 ? 14 : 20;
        setState(State.CHARGE_AIM, aim);
        face(aimDir);
        ServerFx.of("king_dash_line", this.position()).vec(aimDir.scale(chargeSpeed() * 24)).a(this.getBbWidth()).b(aim + 6).send(sl);
        sl.playSound(null, this.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2.0F, 0.7F);
    }

    private void chargeAim(ServerLevel sl) {
        hold();
        face(aimDir);
        if (stateTicks >= stateLength) {
            chargeHit.clear();
            setState(State.CHARGE, 24);
        }
    }

    private void charge(ServerLevel sl) {
        Vec3 v = this.getDeltaMovement();
        this.setDeltaMovement(aimDir.x * chargeSpeed(), v.y, aimDir.z * chargeSpeed());
        this.needsSync = true;
        face(aimDir);
        var box = this.getBoundingBox().inflate(0.5);
        for (ServerPlayer p : sl.players()) {
            if (!p.isAlive() || p.isSpectator() || chargeHit.contains(p.getUUID()) || !p.getBoundingBox().intersects(box)) continue;
            chargeHit.add(p.getUUID());
            p.hurtServer(sl, this.damageSources().mobAttack(this), 8);
            Vec3 side = new Vec3(-aimDir.z, 0, aimDir.x);
            if (side.dot(p.position().subtract(this.position())) < 0) side = side.scale(-1);
            p.setDeltaMovement(p.getDeltaMovement().add(side.scale(1.1)).add(aimDir.scale(0.5)).add(0, 0.5, 0));
            p.syncVelocity = true;
        }
        if (stateTicks % 4 == 0) {
            ServerFx.of("golem_dust", this.position()).a(this.getBbWidth()).send(sl);
            sl.playSound(null, this.blockPosition(), SoundEvents.IRON_GOLEM_STEP, SoundSource.HOSTILE, 2.5F, 0.5F);
        }
        if (stateTicks > 3 && this.horizontalCollision) {
            wallHits++;
            combo.clear();
            this.setDeltaMovement(aimDir.scale(-0.3).add(0, 0.2, 0));
            ServerFx.of("king_wall", this.position().add(aimDir.scale(this.getBbWidth() / 2)).add(0, 1.5, 0)).a(this.getBbWidth()).send(sl);
            sl.playSound(null, this.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.5F, 0.7F);
            sl.playSound(null, this.blockPosition(), SoundEvents.IRON_GOLEM_HURT, SoundSource.HOSTILE, 2.5F, 0.4F);
            stun(sl, 70, 1.5F);
            return;
        }
        if (stateTicks >= stateLength) moveDone();
    }

    // ------------------------------------------------------------------ sweep

    private void startSweep(ServerLevel sl) {
        int up = phase() >= 2 ? 10 : 14;
        setState(State.SWEEP_UP, up);
        ServerFx.of("king_mark", this.position()).a(reach() + 1.4F).b(up).send(sl);
        sl.playSound(null, this.blockPosition(), SoundEvents.IRON_GOLEM_REPAIR, SoundSource.HOSTILE, 2.0F, 0.6F);
    }

    private void sweepUp(ServerLevel sl) {
        hold();
        if (stateTicks < stateLength) return;
        double r = reach() + 1.4;
        for (ServerPlayer p : sl.players()) {
            if (!p.isAlive() || p.isSpectator()) continue;
            Vec3 d = p.position().subtract(this.position());
            if (Math.hypot(d.x, d.z) <= r && Math.abs(d.y) < 3.5) {
                p.hurtServer(sl, this.damageSources().mobAttack(this), 7);
                fling(p, this.position(), 1.5, 0.6);
            }
        }
        ServerFx.of("golem_sweep", this.position().add(0, 1.2, 0)).a((float) r).send(sl);
        sl.playSound(null, this.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 3.0F, 0.4F);
        sl.playSound(null, this.blockPosition(), SoundEvents.IRON_GOLEM_ATTACK, SoundSource.HOSTILE, 2.5F, 0.5F);
        setState(State.SWEEP, 8);
    }

    // ------------------------------------------------------------------ leap (cannot walk there)

    private void startLeap(ServerLevel sl, LivingEntity t) {
        leapTo = groundBelow(sl, t.position());
        leapFrom = this.position();
        double dist = leapTo.subtract(leapFrom).multiply(1, 0, 1).length();
        leapTime = (int) Mth.clamp(12 + dist * 0.6, 16, 30);
        leapHeight = 5 + dist * 0.2 + Math.max(0, leapTo.y - leapFrom.y);
        setState(State.LEAP, leapTime);
        ServerFx.of("king_mark", leapTo).a(slamRadius()).b(leapTime).send(sl);
        sl.playSound(null, this.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2.0F, 0.6F);
        RpgMod.LOGGER.info("[boss] golem leaps from {} to {}", this.blockPosition(), BlockPos.containing(leapTo));
    }

    private void leap(ServerLevel sl) {
        double s = Math.min(1, (stateTicks + 1) / (double) stateLength);
        Vec3 want = leapFrom.lerp(leapTo, s).add(0, leapHeight * 4 * s * (1 - s), 0);
        this.noPhysics = s < 0.85; // through leaves and over walls on the way up
        this.setDeltaMovement(want.subtract(this.position()));
        this.needsSync = true;
        this.resetFallDistance();
        if (s >= 1 || stateTicks > stateLength + 10) {
            this.noPhysics = false;
            slamHit(sl, 9, 5, 7);
            fistsDown(16);
        }
    }

    // ------------------------------------------------------------------ stone armour

    public List<GolemPillar> pillars(ServerLevel sl) {
        return sl.getEntitiesOfClass(GolemPillar.class, this.getBoundingBox().inflate(40), p -> p.isAlive() && p.owner() == this.getId());
    }

    private void checkArmour(ServerLevel sl) {
        if (armours >= ARMOUR_AT.length || this.getHealth() > this.getMaxHealth() * ARMOUR_AT[armours]) return;
        armours++;
        if (phase() < 2) {
            this.entityData.set(DATA_PHASE, 2);
            this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.26);
            if (bossBar != null) bossBar.setColor(BossEvent.BossBarColor.PURPLE);
            for (ServerPlayer p : bossPlayers()) p.sendSystemMessage(Component.translatable("rpgmod.boss.golem.enraged").withStyle(ChatFormatting.LIGHT_PURPLE), true);
        }
        raiseArmour(sl);
    }

    private void raiseArmour(ServerLevel sl) {
        if (state != State.LEAP && state != State.CHARGE) {
            combo.clear();
            rocks.clear();
            fissures.clear();
            setState(State.ARMOUR, 30);
        }
        int online = Math.max(1, sl.getServer().getPlayerList().getPlayerCount());
        int n = Math.min(6, 3 + (online - 1));
        double phase0 = this.random.nextDouble() * Math.PI * 2;
        for (int i = 0; i < n; i++) {
            double a = phase0 + Math.PI * 2 * i / n;
            double r = 8 + this.random.nextDouble() * 3;
            Vec3 at = groundBelow(sl, this.position().add(Math.cos(a) * r, 2, Math.sin(a) * r));
            GolemPillar p = ModRegistries.GOLEM_PILLAR.get().create(sl, EntitySpawnReason.MOB_SUMMONED);
            if (p == null) continue;
            p.snapTo(at.x, at.y, at.z, this.random.nextFloat() * 360, 0);
            p.setOwner(this.getId());
            p.setData(ModRegistries.MOB_LEVEL, bossLevel());
            p.setup(bossLevel());
            sl.addFreshEntity(p);
            ServerFx.of("golem_pillar_rise", at).send(sl);
        }
        this.entityData.set(DATA_SHIELD, true);
        ServerFx.of("golem_shield", this.position()).follow(this).a(this.getBbWidth()).send(sl);
        sl.playSound(null, this.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 3.0F, 0.4F);
        sl.playSound(null, this.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 2.0F, 0.5F);
        for (ServerPlayer p : bossPlayers()) p.sendSystemMessage(Component.translatable("rpgmod.boss.golem.armour").withStyle(ChatFormatting.GOLD), true);
        RpgMod.LOGGER.info("[boss] golem raises stone armour ({} pillars)", n);
    }

    private void breakShield(ServerLevel sl) {
        this.entityData.set(DATA_SHIELD, false);
        shieldBreaks++;
        ServerFx.of("golem_shield_break", this.position()).a(this.getBbWidth()).send(sl);
        sl.playSound(null, this.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 3.0F, 0.5F);
        sl.playSound(null, this.blockPosition(), SoundEvents.IRON_GOLEM_HURT, SoundSource.HOSTILE, 3.0F, 0.4F);
        for (ServerPlayer p : bossPlayers()) p.sendSystemMessage(Component.translatable("rpgmod.boss.golem.armour_broken").withStyle(ChatFormatting.YELLOW), true);
        if (state != State.LEAP && state != State.CHARGE) {
            combo.clear();
            stun(sl, 60, 1.5F);
        }
    }

    // ------------------------------------------------------------------ damage taken

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (state == State.LEAVE) return false;
        if (source.getEntity() instanceof ServerPlayer p) participants.add(p.getUUID());
        if (state == State.STUN || state == State.OVERHEAT || state == State.SLAM) damage *= damageTakenMult;
        if (shielded()) damage *= 0.5F;
        return super.hurtServer(level, source, damage);
    }

    public float openingMultiplier() {
        return (state == State.STUN || state == State.OVERHEAT || state == State.SLAM ? damageTakenMult : 1) * (shielded() ? 0.5F : 1);
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
        return 2.5F;
    }

    @Override
    public float getVoicePitch() {
        return 0.5F;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.IRON_GOLEM_HURT;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getDeathSound() {
        return SoundEvents.IRON_GOLEM_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, net.minecraft.world.level.block.state.BlockState block) {
        this.playSound(SoundEvents.IRON_GOLEM_STEP, 2.0F, 0.5F);
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource source) {
        return false;
    }

    // ------------------------------------------------------------------ boss bar

    private List<ServerPlayer> bossPlayers() {
        return bossBar == null ? List.of() : List.copyOf(bossBar.getPlayers());
    }

    private void updateBossBar(ServerLevel sl) {
        if (bossBar == null) {
            bossBar = new ServerBossEvent(UUID.randomUUID(), name(), phase() >= 2 ? BossEvent.BossBarColor.PURPLE : BossEvent.BossBarColor.RED,
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
        Component n = Component.translatable("entity.rpgmod.golem").append(Component.literal("  Lv " + bossLevel()));
        if (shielded()) {
            n = n.copy().append(Component.literal("  ")).append(Component.translatable("rpgmod.boss.golem.armour_bar").withStyle(ChatFormatting.GRAY));
        } else if (state == State.OVERHEAT || (state == State.STUN && damageTakenMult > 1.4F)) {
            n = n.copy().append(Component.literal("  ")).append(Component.translatable("rpgmod.boss.opening").withStyle(ChatFormatting.YELLOW));
        }
        return n;
    }

    @Override
    public void remove(RemovalReason reason) {
        if (bossBar != null) bossBar.removeAllPlayers();
        if (this.level() instanceof ServerLevel sl) for (GolemPillar p : pillars(sl)) p.discard();
        super.remove(reason);
    }

    // ------------------------------------------------------------------ leaving and dying

    private void startLeaving(ServerLevel sl) {
        setState(State.LEAVE, 70);
        waves.clear();
        rocks.clear();
        fissures.clear();
        combo.clear();
        this.noPhysics = true;
        for (GolemPillar p : pillars(sl)) p.discard();
        this.entityData.set(DATA_SHIELD, false);
        ServerFx.of("golem_leave", this.position()).follow(this).a(this.getBbWidth()).b(70).send(sl);
        sl.playSound(null, this.blockPosition(), SoundEvents.STONE_BREAK, SoundSource.HOSTILE, 3.0F, 0.3F);
        BossEvents.broadcast(sl.getServer(), Component.translatable("rpgmod.boss.golem.left").withStyle(ChatFormatting.GRAY));
    }

    private void leave(ServerLevel sl) {
        this.noPhysics = true;
        this.setDeltaMovement(0, -this.getBbHeight() / 70.0, 0);
        this.needsSync = true;
        if (stateTicks >= stateLength) this.discard();
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!(this.level() instanceof ServerLevel sl) || state == State.LEAVE) return;
        if (bossBar != null) bossBar.removeAllPlayers();
        for (GolemPillar p : pillars(sl)) p.discard();
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
        ServerFx.of("golem_death", this.position()).a(this.getBbWidth()).b(this.getBbHeight()).send(sl);
        BossEvents.broadcast(sl.getServer(), Component.translatable("rpgmod.boss.golem.defeated", String.join(", ", names)).withStyle(ChatFormatting.GOLD));
        RpgMod.LOGGER.info("[boss] Golem defeated, {} participants rewarded", names.size());
    }

    // ------------------------------------------------------------------ save

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.putInt("GolemArmours", armours);
        out.putInt("GolemPhase", phase());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        armours = in.getIntOr("GolemArmours", 0);
        this.entityData.set(DATA_PHASE, in.getIntOr("GolemPhase", 1));
    }

    // ------------------------------------------------------------------ dev test

    /** Autotest: start a move right away (slam, quake, throw, fissure, charge, sweep, rockfall, overheat, leap, armour, combo). */
    public void testMove(String move) {
        if (!(this.level() instanceof ServerLevel sl)) return;
        LivingEntity t = target(sl);
        if (t == null) return;
        setState(State.CHASE, 0);
        combo.clear();
        switch (move) {
            case "overheat" -> startOverheat();
            case "combo" -> nextMove = 0;
            case "leap" -> startLeap(sl, t);
            case "armour" -> raiseArmour(sl);
            default -> start(sl, Move.valueOf(move.toUpperCase(java.util.Locale.ROOT)), t);
        }
    }

    public int armours() {
        return armours;
    }
}
