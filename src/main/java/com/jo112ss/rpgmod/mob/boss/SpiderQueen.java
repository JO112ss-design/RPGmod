package com.jo112ss.rpgmod.mob.boss;

import com.jo112ss.rpgmod.job.fx.ServerFx;
import com.jo112ss.rpgmod.mob.MobConfig;
import com.jo112ss.rpgmod.mob.MobLevels;
import com.jo112ss.rpgmod.mob.core.MobLevelMath;
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
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Spider Queen: a giant black-and-violet spider, a crown of spines on her back. She stalks her
 * prey and strings together a combo of hunting moves (three, four below half health); every second
 * combo she is <b>spent</b>: her legs splay, she sinks low and takes 1.4x damage.
 * <ul>
 * <li>pounce: she crouches, a circle marks where she will land, then a leap and a hit inside it</li>
 * <li>web shot: a fan of sticky webs (slow you down hard)</li>
 * <li>venom spray: she rears up, a cone shows the spray, then poison mist that lingers a while</li>
 * <li>egg sacs: she lays sacs around her that hatch into cave spiders (a few at a time)</li>
 * <li>skitter: a marked lane, then a fast dash along it</li>
 * <li>leg swipe: if someone stays close, her front legs rise and sweep the front of her</li>
 * <li>below half health: she shrieks, lays a brood at once, moves faster, pounces three times in a
 * row and casts a <b>web field</b>: marked circles all round that turn into webbing for a while</li>
 * </ul>
 * No single hit takes more than a fifth of a player's health (see {@link BossEvents}). Health follows
 * the boss level and the players fighting her. Burrows away at dawn.
 */
public class SpiderQueen extends Monster implements BloodMoonBoss {
    public static final float SCALE = 3.0F;

    public enum State { CHASE, CROUCH, POUNCE, LANDED, WEB_UP, SPRAY_UP, SPRAY, EGGS, SKITTER_AIM, SKITTER, SWIPE_UP, SWIPE, FIELD_UP, SPENT, SHRIEK, LEAVE }

    enum Move { POUNCE, TRIPLE, WEBS, SPRAY, EGGS, SKITTER, SWIPE, FIELD }

    private static final EntityDataAccessor<Integer> DATA_STATE = SynchedEntityData.defineId(SpiderQueen.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(SpiderQueen.class, EntityDataSerializers.INT);

    public static final int MAX_BROOD = 8;
    public static final String BROOD_OF = "rpgmod_queen_brood";

    // ---- server state
    private State state = State.CHASE;
    private int stateTicks, stateLength;
    private int nextMove = 50;
    private final Deque<Move> combo = new ArrayDeque<>();
    /** Keeping out of water, and homing venom for someone out on it. */
    public final BossWater.Tracker water = new BossWater.Tracker();
    public final BossWater.Homing homing = new BossWater.Homing(0x9AF060);
    private boolean relocating;
    private int pounceTime = 14;
    private int combos;
    private float damageTakenMult = 1;
    private int onlineSeen = -1;
    private double healthTarget;
    private float toughness = 1;
    private int close, stuckTicks;
    private Vec3 lastPos = Vec3.ZERO;
    private final Set<UUID> participants = new HashSet<>();
    private final List<Web> webs = new ArrayList<>();
    private final List<Zone> zones = new ArrayList<>();
    private final List<Egg> eggs = new ArrayList<>();
    private ServerBossEvent bossBar;
    private Vec3 aimDir = Vec3.ZERO;
    private Vec3 leapFrom = Vec3.ZERO, leapTo = Vec3.ZERO;
    private int pouncesLeft;
    private final Set<UUID> dashHit = new HashSet<>();
    /** Dev test counters. */
    public int pounceHits, webHits, sprayHits, hatched, skitterHits, swipeHits, fieldHits;

    // ---- client animation
    public int clientStateTicks;

    /** A web ball flying straight on. */
    private static final class Web {
        Vec3 pos;
        final Vec3 vel;
        int life;

        Web(Vec3 pos, Vec3 vel, int life) {
            this.pos = pos;
            this.vel = vel;
            this.life = life;
        }
    }

    /** Lingering poison mist or webbing on the ground. */
    private record Zone(Vec3 at, float radius, int until, boolean poison, int from) {}

    /** An egg sac about to hatch. */
    private record Egg(Vec3 at, int hatch) {}

    public SpiderQueen(EntityType<? extends SpiderQueen> type, Level level) {
        super(type, level);
        this.xpReward = 200;
        this.refreshDimensions(); // the scale attribute sets her size
        BossWater.avoidWater(this);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 800)
                .add(Attributes.FOLLOW_RANGE, 48)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.ARMOR, 6)
                .add(Attributes.ATTACK_DAMAGE, 7)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.STEP_HEIGHT, 1.6)
                .add(Attributes.SCALE, SCALE);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(DATA_STATE, 0);
        b.define(DATA_PHASE, 1);
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

    public State state() {
        return State.values()[Mth.clamp(this.entityData.get(DATA_STATE), 0, State.values().length - 1)];
    }

    @Override
    public void recomputeHealth(boolean fill) {
        if (!(this.level() instanceof ServerLevel sl)) return;
        int online = com.jo112ss.rpgmod.dungeon.Dungeons.bossPlayers(this, sl);
        onlineSeen = online;
        double hp = MobConfig.queenHealth() * MobLevelMath.healthMultiplier(MobConfig.rules(), bossLevel()) * (1 + MobConfig.bossPerPlayer() * (online - 1));
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
    protected boolean isImmobile() {
        return super.isImmobile() || (!this.level().isClientSide() && state != State.CHASE);
    }

    // ------------------------------------------------------------------ tick

    @Override
    public void tick() {
        if (this.level() instanceof ServerLevel sl) serverTick(sl);
        super.tick();
        if (this.level().isClientSide()) clientStateTicks++;
    }

    private void serverTick(ServerLevel sl) {
        if (!this.isAlive()) return;
        stateTicks++;
        if (this.tickCount % 10 == 0) updateBossBar(sl);
        if (this.tickCount % 20 == 0) {
            if (com.jo112ss.rpgmod.dungeon.Dungeons.bossPlayers(this, sl) != onlineSeen && healthTarget > 0) recomputeHealth(false);
            if (state != State.LEAVE && !BloodMoon.isBloodMoon(sl) && !BloodMoon.staysByDay(this, sl)) startLeaving(sl);
        }
        tickWebs(sl);
        tickZones(sl);
        tickEggs(sl);
        homing.tick(sl, this);
        if (state != State.LEAVE && phase() < 2 && this.getHealth() <= this.getMaxHealth() * 0.5F) startShriek(sl);

        switch (state) {
            case CHASE -> chase(sl);
            case CROUCH -> crouch(sl);
            case POUNCE -> pounce(sl);
            case LANDED, SWIPE, SPRAY, EGGS -> {
                hold();
                if (stateTicks >= stateLength) {
                    if (state == State.LANDED && pouncesLeft > 0) {
                        LivingEntity t = target(sl);
                        if (t != null) {
                            startPounce(sl, t, 8);
                            return;
                        }
                    }
                    moveDone();
                }
            }
            case WEB_UP -> webUp(sl);
            case SPRAY_UP -> sprayUp(sl);
            case SKITTER_AIM -> skitterAim(sl);
            case SKITTER -> skitter(sl);
            case SWIPE_UP -> swipeUp(sl);
            case FIELD_UP -> fieldUp(sl);
            case SPENT -> {
                hold();
                if (stateTicks >= stateLength) {
                    setState(State.CHASE, 0);
                    nextMove = phase() >= 2 ? 18 + this.random.nextInt(10) : 26 + this.random.nextInt(14);
                }
            }
            case SHRIEK -> shriek(sl);
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
        return this.getBbWidth() / 2 + 1.5;
    }

    private void face(Vec3 dir) {
        this.setYRot((float) Math.toDegrees(Math.atan2(-dir.x, dir.z)));
        this.yBodyRot = this.yHeadRot = this.getYRot();
    }

    private Vec3 toward(LivingEntity t) {
        Vec3 d = t.position().subtract(this.position()).multiply(1, 0, 1);
        return d.lengthSqr() < 1e-4 ? Vec3.directionFromRotation(0, this.getYRot()) : d.normalize();
    }

    private static Vec3 groundBelow(ServerLevel sl, Vec3 at) {
        BlockPos pos = BlockPos.containing(at);
        for (int i = 0; i < 12; i++) {
            if (!sl.getBlockState(pos.below()).getCollisionShape(sl, pos.below()).isEmpty()) break;
            pos = pos.below();
        }
        return new Vec3(at.x, pos.getY(), at.z);
    }

    // ------------------------------------------------------------------ rhythm: combo -> spent

    private void chase(ServerLevel sl) {
        LivingEntity t = target(sl);
        if (t == null) {
            this.getNavigation().stop();
            return;
        }
        double dist = Math.sqrt(t.distanceToSqr(this));
        this.getLookControl().setLookAt(t, 30, 30);
        // out of the water / to the shore nearest someone out on it
        Vec3 dry = water.relocate(sl, this, t);
        if (dry != null) {
            relocating = true;
            pouncesLeft = 0;
            pounceTo(sl, dry, 6);
            return;
        }
        boolean ranged = water.ranged(t);
        if (water.homingDue(t, phase())) homing.volley(sl, this, t, phase() >= 2 ? 5 : 3, 6, 0.6, 14);
        if (this.tickCount % 8 == 0) this.getNavigation().moveTo(t, 1.0);
        close = dist < reach() + 1.5 ? close + 1 : Math.max(0, close - 2);
        // stuck (walls, a tower): she leaps at the target
        if (this.tickCount % 20 == 0) {
            boolean moved = this.position().distanceTo(lastPos) > 1.0;
            lastPos = this.position();
            stuckTicks = moved || dist < reach() + 1 ? 0 : stuckTicks + 20;
            if (stuckTicks >= 60 && this.onGround() && !ranged) {
                stuckTicks = 0;
                pouncesLeft = 0;
                startPounce(sl, t, 8);
                return;
            }
        }
        if (--nextMove > 0 || !this.onGround()) return;
        if (dist > 36) return;
        if (combo.isEmpty()) buildCombo(sl);
        Move m = combo.poll();
        if (m != null) start(sl, m, t);
    }

    private int brood(ServerLevel sl) {
        return sl.getEntitiesOfClass(Mob.class, this.getBoundingBox().inflate(40), m -> m.isAlive() && m.getPersistentData().getIntOr(BROOD_OF, -1) == this.getId()).size()
                + eggs.size() * 2;
    }

    /** Three moves (four below half health), no repeats; someone close gets a leg swipe first. */
    private void buildCombo(ServerLevel sl) {
        boolean p2 = phase() >= 2;
        List<Move> pool = new ArrayList<>(p2
                ? List.of(Move.TRIPLE, Move.TRIPLE, Move.POUNCE, Move.WEBS, Move.WEBS, Move.SPRAY, Move.SPRAY, Move.SKITTER, Move.SKITTER, Move.FIELD, Move.FIELD)
                : List.of(Move.POUNCE, Move.POUNCE, Move.WEBS, Move.WEBS, Move.SPRAY, Move.SPRAY, Move.SKITTER, Move.SKITTER));
        if (brood(sl) < (p2 ? 6 : 4)) pool.add(Move.EGGS);
        int n = p2 ? 4 : 3;
        if (water.ranged(target(sl))) { // someone out on the water: webs, and a brood that swims after them
            boolean eggs = pool.contains(Move.EGGS);
            pool = new ArrayList<>(List.of(Move.WEBS));
            if (eggs) pool.add(Move.EGGS);
            n = 2;
        }
        if (close > 25 && this.random.nextDouble() < 0.75) {
            combo.add(Move.SWIPE);
            close = 0;
            n--;
        }
        while (n > 0 && !pool.isEmpty()) {
            Move m = pool.get(this.random.nextInt(pool.size()));
            pool.removeIf(x -> x == m || (m == Move.TRIPLE && x == Move.POUNCE) || (m == Move.POUNCE && x == Move.TRIPLE));
            combo.add(m);
            n--;
        }
    }

    private void start(ServerLevel sl, Move m, LivingEntity t) {
        this.getNavigation().stop();
        switch (m) {
            case POUNCE -> {
                pouncesLeft = 0;
                startPounce(sl, t, phase() >= 2 ? 10 : 14);
            }
            case TRIPLE -> {
                pouncesLeft = 2;
                startPounce(sl, t, 10);
            }
            case WEBS -> startWebs(sl, t);
            case SPRAY -> startSpray(sl, t);
            case EGGS -> layEggs(sl, phase() >= 2 ? 3 : 2);
            case SKITTER -> startSkitter(sl, t);
            case SWIPE -> startSwipe(sl, t);
            case FIELD -> startField(sl);
        }
    }

    private void moveDone() {
        if (!combo.isEmpty()) {
            setState(State.CHASE, 0);
            nextMove = phase() >= 2 ? 8 + this.random.nextInt(6) : 12 + this.random.nextInt(8);
        } else if (++combos % 2 == 0) {
            startSpent(); // spent after every second combo only
        } else {
            setState(State.CHASE, 0);
            nextMove = phase() >= 2 ? 16 + this.random.nextInt(8) : 22 + this.random.nextInt(10);
        }
    }

    private void startSpent() {
        int len = phase() >= 2 ? 40 : 50;
        setState(State.SPENT, len);
        damageTakenMult = 1.4F;
        if (this.level() instanceof ServerLevel sl) {
            ServerFx.of("queen_spent", this.position()).follow(this).a(this.getBbWidth()).b(len).send(sl);
            sl.playSound(null, this.blockPosition(), SoundEvents.SPIDER_HURT, SoundSource.HOSTILE, 2.0F, 0.4F);
        }
    }

    // ------------------------------------------------------------------ pounce

    private float pounceRadius() {
        return this.getBbWidth() / 2 + 1.6F;
    }

    private void startPounce(ServerLevel sl, LivingEntity t, int crouch) {
        aimDir = toward(t);
        face(aimDir);
        leapFrom = this.position();
        Vec3 lead = t.position().add(t.getDeltaMovement().multiply(6, 0, 6));
        Vec3 d = lead.subtract(leapFrom).multiply(1, 0, 1);
        if (d.length() > 18) lead = leapFrom.add(d.normalize().scale(18));
        pounceTo(sl, groundBelow(sl, lead.add(0, 2, 0)), crouch);
    }

    /** A pounce onto a spot: crouch, mark, leap (longer leaps take longer). */
    private void pounceTo(ServerLevel sl, Vec3 to, int crouch) {
        leapFrom = this.position();
        leapTo = to;
        aimDir = to.subtract(leapFrom).multiply(1, 0, 1);
        aimDir = aimDir.lengthSqr() < 1e-4 ? Vec3.directionFromRotation(0, this.getYRot()) : aimDir.normalize();
        face(aimDir);
        pounceTime = (int) Mth.clamp(10 + leapFrom.distanceTo(leapTo) * 0.4, 14, 26);
        setState(State.CROUCH, crouch);
        ServerFx.of("king_mark", leapTo).a(pounceRadius()).b(crouch + pounceTime).send(sl, 64);
        sl.playSound(null, this.blockPosition(), SoundEvents.SPIDER_AMBIENT, SoundSource.HOSTILE, 2.5F, 0.5F);
    }

    private void crouch(ServerLevel sl) {
        hold();
        face(aimDir);
        if (stateTicks >= stateLength) {
            setState(State.POUNCE, pounceTime);
            sl.playSound(null, this.blockPosition(), SoundEvents.SPIDER_STEP, SoundSource.HOSTILE, 3.0F, 0.6F);
        }
    }

    private void pounce(ServerLevel sl) {
        double s = Math.min(1, (stateTicks + 1) / (double) stateLength);
        double h = 3 + leapFrom.distanceTo(leapTo) * 0.15;
        Vec3 want = leapFrom.lerp(leapTo, s).add(0, h * 4 * s * (1 - s), 0);
        this.noPhysics = s < 0.8;
        this.setDeltaMovement(want.subtract(this.position()));
        this.needsSync = true;
        this.resetFallDistance();
        if (s >= 1 || stateTicks > stateLength + 8) {
            this.noPhysics = false;
            Vec3 c = this.position();
            for (ServerPlayer p : sl.players()) {
                if (!valid(p)) continue;
                Vec3 d = p.position().subtract(c);
                if (Math.hypot(d.x, d.z) <= pounceRadius() && Math.abs(d.y) < 3) {
                    p.hurtServer(sl, this.damageSources().mobAttack(this), 8);
                    fling(p, c, 0.8, 0.45);
                    participants.add(p.getUUID());
                    pounceHits++;
                }
            }
            ServerFx.of("queen_land", c).a(pounceRadius()).send(sl, 64);
            sl.playSound(null, this.blockPosition(), SoundEvents.SPIDER_STEP, SoundSource.HOSTILE, 3.0F, 0.4F);
            sl.playSound(null, this.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.8F, 1.2F);
            if (relocating) { // a leap to dry ground is not a move of the combo
                relocating = false;
                pouncesLeft = 0;
                setState(State.CHASE, 0);
                nextMove = Math.max(nextMove, 12);
                return;
            }
            if (pouncesLeft > 0) pouncesLeft--;
            setState(State.LANDED, pouncesLeft > 0 ? 4 : 10);
        }
    }

    private static void fling(LivingEntity e, Vec3 from, double out, double up) {
        Vec3 d = e.position().subtract(from).multiply(1, 0, 1);
        d = d.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : d.normalize();
        e.setDeltaMovement(e.getDeltaMovement().add(d.scale(out)).add(0, up, 0));
        e.syncVelocity = true;
    }

    // ------------------------------------------------------------------ webs

    private Vec3 mouth() {
        return this.position().add(Vec3.directionFromRotation(0, this.yBodyRot).scale(this.getBbWidth() * 0.55)).add(0, this.getBbHeight() * 0.5, 0);
    }

    private void startWebs(ServerLevel sl, LivingEntity t) {
        aimDir = toward(t);
        face(aimDir);
        int up = phase() >= 2 ? 10 : 14;
        setState(State.WEB_UP, up);
        ServerFx.of("queen_rear", this.position()).follow(this).a(this.getBbWidth()).b(up).color(0xE8E8F0).send(sl, 64);
        sl.playSound(null, this.blockPosition(), SoundEvents.SPIDER_AMBIENT, SoundSource.HOSTILE, 2.5F, 0.8F);
    }

    private void webUp(ServerLevel sl) {
        hold();
        LivingEntity t = target(sl);
        if (t != null) face(toward(t));
        if (stateTicks < stateLength) return;
        int n = phase() >= 2 ? 5 : 3;
        Vec3 from = mouth();
        Vec3 aim = t != null ? t.position().add(0, 0.8, 0).subtract(from).normalize() : Vec3.directionFromRotation(0, this.getYRot());
        for (int i = 0; i < n; i++) {
            double a = Math.toRadians((i - (n - 1) / 2.0) * 14);
            double c = Math.cos(a), s = Math.sin(a);
            Vec3 dir = new Vec3(aim.x * c - aim.z * s, aim.y, aim.x * s + aim.z * c).scale(0.7);
            webs.add(new Web(from, dir, 50));
            ServerFx.of("queen_web", from).vec(dir).a(50).send(sl, 64);
        }
        sl.playSound(null, this.blockPosition(), SoundEvents.LLAMA_SPIT, SoundSource.HOSTILE, 2.0F, 0.6F);
        setState(State.EGGS, 8);
    }

    private void tickWebs(ServerLevel sl) {
        for (var it = webs.iterator(); it.hasNext(); ) {
            Web w = it.next();
            Vec3 next = w.pos.add(w.vel);
            var block = sl.clip(new ClipContext(w.pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            boolean wall = block.getType() != HitResult.Type.MISS;
            Vec3 end = wall ? block.getLocation() : next;
            ServerPlayer hit = null;
            for (ServerPlayer p : sl.getEntitiesOfClass(ServerPlayer.class, new AABB(w.pos, end).inflate(0.8), this::valid)) {
                if (p.getBoundingBox().inflate(0.35).clip(w.pos, end).isPresent() || p.getBoundingBox().inflate(0.35).contains(end)) {
                    hit = p;
                    break;
                }
            }
            if (hit != null) {
                hit.hurtServer(sl, this.damageSources().mobAttack(this), 3);
                hit.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 3), this);
                participants.add(hit.getUUID());
                webHits++;
                ServerFx.of("queen_web_hit", end).send(sl, 48);
                it.remove();
                continue;
            }
            w.pos = end;
            if (wall || --w.life <= 0) {
                if (wall) ServerFx.of("queen_web_hit", end).send(sl, 48);
                it.remove();
            }
        }
    }

    // ------------------------------------------------------------------ venom spray

    private static final double SPRAY_LEN = 8, SPRAY_HALF = Math.toRadians(35);

    private void startSpray(ServerLevel sl, LivingEntity t) {
        aimDir = toward(t);
        face(aimDir);
        int up = phase() >= 2 ? 16 : 20;
        setState(State.SPRAY_UP, up);
        Vec3 base = this.position().add(aimDir.scale(this.getBbWidth() * 0.4));
        ServerFx.of("rv_cone", base).vec(aimDir).a(SPRAY_LEN).b(up).color(0x7AE040).send(sl, 64);
        ServerFx.of("queen_rear", this.position()).follow(this).a(this.getBbWidth()).b(up).color(0x7AE040).send(sl, 64);
        sl.playSound(null, this.blockPosition(), SoundEvents.SPIDER_AMBIENT, SoundSource.HOSTILE, 2.5F, 0.5F);
    }

    private void sprayUp(ServerLevel sl) {
        hold();
        face(aimDir);
        if (stateTicks < stateLength) return;
        Vec3 base = this.position().add(aimDir.scale(this.getBbWidth() * 0.4));
        for (ServerPlayer p : sl.players()) {
            if (!valid(p)) continue;
            Vec3 d = p.position().subtract(base).multiply(1, 0, 1);
            double len = d.length();
            if (len > SPRAY_LEN || Math.abs(p.getY() - this.getY()) > 4) continue;
            if (len > 1 && Math.acos(Mth.clamp(d.normalize().dot(aimDir), -1, 1)) > SPRAY_HALF + 0.08) continue;
            p.hurtServer(sl, this.damageSources().mobAttack(this), 5);
            p.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1), this);
            participants.add(p.getUUID());
            sprayHits++;
        }
        // the mist lingers where it fell
        for (int i = 1; i <= 3; i++) {
            Vec3 at = groundBelow(sl, base.add(aimDir.scale(SPRAY_LEN * i / 3.5)).add(0, 1, 0));
            float r = 1.4F + i * 0.6F;
            zones.add(new Zone(at, r, this.tickCount + 100, true, this.tickCount));
            ServerFx.of("queen_mist", at).a(r).b(100).send(sl, 64);
        }
        ServerFx.of("queen_spray", base.add(0, 1, 0)).vec(aimDir.scale(SPRAY_LEN)).send(sl, 64);
        sl.playSound(null, this.blockPosition(), SoundEvents.LLAMA_SPIT, SoundSource.HOSTILE, 2.5F, 0.4F);
        setState(State.SPRAY, 14);
    }

    private void tickZones(ServerLevel sl) {
        if (this.tickCount % 5 != 0) {
            zones.removeIf(z -> this.tickCount >= z.until());
            return;
        }
        for (var it = zones.iterator(); it.hasNext(); ) {
            Zone z = it.next();
            if (this.tickCount >= z.until()) {
                it.remove();
                continue;
            }
            if (this.tickCount < z.from()) continue;
            for (ServerPlayer p : sl.players()) {
                if (!valid(p)) continue;
                Vec3 v = p.position().subtract(z.at());
                if (Math.hypot(v.x, v.z) > z.radius() || Math.abs(v.y) > 2) continue;
                if (z.poison()) {
                    p.addEffect(new MobEffectInstance(MobEffects.POISON, 40, 0), this);
                } else {
                    p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 15, 2), this);
                    fieldHits++;
                }
            }
        }
    }

    // ------------------------------------------------------------------ eggs

    private void layEggs(ServerLevel sl, int n) {
        setState(State.EGGS, 24);
        sl.playSound(null, this.blockPosition(), SoundEvents.FROGSPAWN_HATCH, SoundSource.HOSTILE, 2.5F, 0.5F);
        if (brood(sl) >= MAX_BROOD) return;
        for (int i = 0; i < n; i++) {
            double a = this.random.nextDouble() * Math.PI * 2, r = 4 + this.random.nextDouble() * 4;
            Vec3 at = groundBelow(sl, this.position().add(Math.cos(a) * r, 2, Math.sin(a) * r));
            int hatch = this.tickCount + 70 + i * 6;
            eggs.add(new Egg(at, hatch));
            ServerFx.of("queen_egg", at).b(hatch - this.tickCount).send(sl, 64);
        }
    }

    private void tickEggs(ServerLevel sl) {
        if (!this.isAlive()) return;
        for (var it = eggs.iterator(); it.hasNext(); ) {
            Egg e = it.next();
            if (this.tickCount < e.hatch()) continue;
            it.remove();
            for (int k = 0; k < 2; k++) {
                if (brood(sl) >= MAX_BROOD + eggs.size() * 2) break;
                var s = EntityTypes.CAVE_SPIDER.create(sl, EntitySpawnReason.MOB_SUMMONED);
                if (s == null) continue;
                s.snapTo(e.at().x + (k - 0.5) * 0.6, e.at().y, e.at().z, this.random.nextFloat() * 360, 0);
                s.getPersistentData().putBoolean(BossEvents.CHILD, true);
                s.getPersistentData().putInt(BROOD_OF, this.getId());
                MobLevels.forceLevel = bossLevel();
                com.jo112ss.rpgmod.mob.Elite.forceNext = 0;
                try {
                    sl.addFreshEntity(s);
                } finally {
                    MobLevels.forceLevel = null;
                    com.jo112ss.rpgmod.mob.Elite.forceNext = null;
                }
                s.setTarget(target(sl));
                hatched++;
            }
            ServerFx.of("queen_hatch", e.at()).send(sl, 64);
            sl.playSound(null, e.at().x, e.at().y, e.at().z, SoundEvents.TURTLE_EGG_HATCH, SoundSource.HOSTILE, 2.0F, 0.6F);
        }
    }

    // ------------------------------------------------------------------ skitter

    private void startSkitter(ServerLevel sl, LivingEntity t) {
        aimDir = toward(t);
        face(aimDir);
        int aim = phase() >= 2 ? 12 : 16;
        setState(State.SKITTER_AIM, aim);
        ServerFx.of("king_dash_line", this.position()).vec(aimDir.scale(1.1 * 18)).a(this.getBbWidth()).b(aim + 4).send(sl, 64);
        sl.playSound(null, this.blockPosition(), SoundEvents.SPIDER_AMBIENT, SoundSource.HOSTILE, 2.5F, 1.2F);
    }

    private void skitterAim(ServerLevel sl) {
        hold();
        face(aimDir);
        if (stateTicks >= stateLength) {
            dashHit.clear();
            setState(State.SKITTER, 18);
        }
    }

    private void skitter(ServerLevel sl) {
        Vec3 v = this.getDeltaMovement();
        this.setDeltaMovement(aimDir.x * 1.1, v.y, aimDir.z * 1.1);
        this.needsSync = true;
        face(aimDir);
        var box = this.getBoundingBox().inflate(0.4);
        for (ServerPlayer p : sl.players()) {
            if (!valid(p) || dashHit.contains(p.getUUID()) || !p.getBoundingBox().intersects(box)) continue;
            dashHit.add(p.getUUID());
            p.hurtServer(sl, this.damageSources().mobAttack(this), 7);
            Vec3 side = new Vec3(-aimDir.z, 0, aimDir.x);
            if (side.dot(p.position().subtract(this.position())) < 0) side = side.scale(-1);
            p.setDeltaMovement(p.getDeltaMovement().add(side.scale(1.0)).add(0, 0.45, 0));
            p.syncVelocity = true;
            participants.add(p.getUUID());
            skitterHits++;
        }
        if (stateTicks % 3 == 0) sl.playSound(null, this.blockPosition(), SoundEvents.SPIDER_STEP, SoundSource.HOSTILE, 2.0F, 1.3F);
        // into a wall she simply stops (no daze: openings are kept for when she is spent)
        if ((stateTicks > 3 && this.horizontalCollision) || stateTicks >= stateLength) {
            this.setDeltaMovement(Vec3.ZERO);
            setState(State.SPRAY, 8);
        }
    }

    // ------------------------------------------------------------------ leg swipe

    private void startSwipe(ServerLevel sl, LivingEntity t) {
        aimDir = toward(t);
        face(aimDir);
        int up = phase() >= 2 ? 10 : 12;
        setState(State.SWIPE_UP, up);
        ServerFx.of("rv_cone", this.position()).vec(aimDir).a(reach() + 1.5).b(up).color(0xFF3030).send(sl, 64);
        sl.playSound(null, this.blockPosition(), SoundEvents.SPIDER_AMBIENT, SoundSource.HOSTILE, 2.5F, 0.4F);
    }

    private void swipeUp(ServerLevel sl) {
        hold();
        face(aimDir);
        if (stateTicks < stateLength) return;
        double r = reach() + 1.5;
        for (ServerPlayer p : sl.players()) {
            if (!valid(p)) continue;
            Vec3 d = p.position().subtract(this.position()).multiply(1, 0, 1);
            if (d.length() > r || Math.abs(p.getY() - this.getY()) > 3) continue;
            if (d.length() > 1.5 && d.normalize().dot(aimDir) < Math.cos(Math.toRadians(55))) continue;
            p.hurtServer(sl, this.damageSources().mobAttack(this), 8);
            fling(p, this.position(), 1.4, 0.5);
            participants.add(p.getUUID());
            swipeHits++;
        }
        ServerFx.of("x_slash", this.position().add(0, 1.2, 0).add(aimDir.scale(this.getBbWidth() * 0.5))).vec(aimDir).a((float) r).b(0.1).color(0xB070FF).send(sl, 64);
        sl.playSound(null, this.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 2.5F, 0.5F);
        setState(State.SWIPE, 10);
    }

    // ------------------------------------------------------------------ web field (below half health)

    private void startField(ServerLevel sl) {
        int up = 22;
        setState(State.FIELD_UP, up);
        ServerFx.of("queen_rear", this.position()).follow(this).a(this.getBbWidth()).b(up).color(0xE8E8F0).send(sl, 64);
        sl.playSound(null, this.blockPosition(), SoundEvents.SPIDER_AMBIENT, SoundSource.HOSTILE, 3.0F, 0.6F);
        for (ServerPlayer p : fighters(sl, 32)) {
            for (int i = 0; i < 3; i++) {
                double a = this.random.nextDouble() * Math.PI * 2, r = i == 0 ? 0 : 3 + this.random.nextDouble() * 3;
                Vec3 at = groundBelow(sl, p.position().add(Math.cos(a) * r, 1, Math.sin(a) * r));
                int from = this.tickCount + up + 4;
                zones.add(new Zone(at, 2.4F, from + 160, false, from));
                ServerFx.of("queen_field", at).a(2.4F).b(up + 4).vec(new Vec3(160, 0, 0)).send(sl, 64);
            }
        }
    }

    private void fieldUp(ServerLevel sl) {
        hold();
        if (stateTicks >= stateLength) {
            sl.playSound(null, this.blockPosition(), SoundEvents.LLAMA_SPIT, SoundSource.HOSTILE, 2.5F, 0.4F);
            setState(State.EGGS, 10);
        }
    }

    // ------------------------------------------------------------------ shriek (half health)

    private void startShriek(ServerLevel sl) {
        this.entityData.set(DATA_PHASE, 2);
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.36);
        combo.clear();
        setState(State.SHRIEK, 34);
        if (bossBar != null) bossBar.setColor(BossEvent.BossBarColor.PURPLE);
        ServerFx.of("queen_shriek", this.position()).follow(this).a(this.getBbWidth()).b(34).send(sl, 64);
        sl.playSound(null, this.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 3.0F, 1.5F);
        sl.playSound(null, this.blockPosition(), SoundEvents.SPIDER_DEATH, SoundSource.HOSTILE, 3.0F, 0.5F);
        for (ServerPlayer p : bossPlayers()) p.sendSystemMessage(Component.translatable("rpgmod.boss.spider_queen.enraged").withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }

    private void shriek(ServerLevel sl) {
        hold();
        if (stateTicks == 16) {
            State keep = state;
            int t = stateTicks, len = stateLength;
            layEggs(sl, 3);
            state = keep; // laying eggs here is part of the shriek
            stateTicks = t;
            stateLength = len;
            this.entityData.set(DATA_STATE, State.SHRIEK.ordinal());
        }
        if (stateTicks >= stateLength) {
            setState(State.CHASE, 0);
            nextMove = 16;
        }
    }

    // ------------------------------------------------------------------ damage taken

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (state == State.LEAVE) return false;
        if (source.getEntity() instanceof ServerPlayer p) participants.add(p.getUUID());
        if (state == State.SPENT) damage *= damageTakenMult;
        if (state == State.SHRIEK) damage *= 0.3F;
        return super.hurtServer(level, source, damage);
    }

    public float openingMultiplier() {
        return state == State.SPENT ? damageTakenMult : state == State.SHRIEK ? 0.3F : 1;
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
    protected net.minecraft.sounds.SoundEvent getAmbientSound() {
        return SoundEvents.SPIDER_AMBIENT;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.SPIDER_HURT;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getDeathSound() {
        return SoundEvents.SPIDER_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, net.minecraft.world.level.block.state.BlockState block) {
        this.playSound(SoundEvents.SPIDER_STEP, 1.5F, 0.6F);
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource source) {
        return false;
    }

    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        return !effect.is(MobEffects.POISON) && super.canBeAffected(effect);
    }

    // ------------------------------------------------------------------ boss bar

    private List<ServerPlayer> bossPlayers() {
        return bossBar == null ? List.of() : List.copyOf(bossBar.getPlayers());
    }

    private void updateBossBar(ServerLevel sl) {
        if (bossBar == null) {
            bossBar = new ServerBossEvent(UUID.randomUUID(), name(), phase() >= 2 ? BossEvent.BossBarColor.PURPLE : BossEvent.BossBarColor.GREEN,
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
        Component n = Component.translatable("entity.rpgmod.spider_queen").append(Component.literal("  Lv " + bossLevel()));
        if (state == State.SPENT) n = n.copy().append(Component.literal("  ")).append(Component.translatable("rpgmod.boss.opening").withStyle(ChatFormatting.YELLOW));
        return n;
    }

    private void dismissBrood(ServerLevel sl) {
        eggs.clear();
        for (Mob m : sl.getEntitiesOfClass(Mob.class, this.getBoundingBox().inflate(64), m -> m.getPersistentData().getIntOr(BROOD_OF, -1) == this.getId())) m.discard();
    }

    @Override
    public void remove(RemovalReason reason) {
        if (bossBar != null) bossBar.removeAllPlayers();
        if (this.level() instanceof ServerLevel sl) dismissBrood(sl);
        super.remove(reason);
    }

    // ------------------------------------------------------------------ leaving and dying

    private void startLeaving(ServerLevel sl) {
        setState(State.LEAVE, 60);
        webs.clear();
        zones.clear();
        combo.clear();
        dismissBrood(sl);
        this.noPhysics = true;
        ServerFx.of("golem_leave", this.position()).follow(this).a(this.getBbWidth()).b(60).send(sl, 64);
        sl.playSound(null, this.blockPosition(), SoundEvents.SPIDER_AMBIENT, SoundSource.HOSTILE, 3.0F, 0.4F);
        BossEvents.broadcast(sl.getServer(), Component.translatable("rpgmod.boss.spider_queen.left").withStyle(ChatFormatting.GRAY));
    }

    private void leave(ServerLevel sl) {
        this.noPhysics = true;
        this.setDeltaMovement(0, -this.getBbHeight() / 60.0, 0);
        this.needsSync = true;
        if (stateTicks >= stateLength) this.discard();
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!(this.level() instanceof ServerLevel sl) || state == State.LEAVE) return;
        if (bossBar != null) bossBar.removeAllPlayers();
        dismissBrood(sl);
        // and once more a moment later, for any that were still joining the world
        com.jo112ss.rpgmod.job.SkillTasks.later(10, () -> dismissBrood(sl));
        com.jo112ss.rpgmod.job.SkillTasks.later(40, () -> dismissBrood(sl));
        BossEvents.reward(sl, this, participants, "spider_queen");
        ServerFx.of("queen_death", this.position()).a(this.getBbWidth()).b(this.getBbHeight()).send(sl, 96);
    }

    // ------------------------------------------------------------------ save

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.putInt("QueenPhase", phase());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        this.entityData.set(DATA_PHASE, in.getIntOr("QueenPhase", 1));
    }

    // ------------------------------------------------------------------ dev test

    /** Autotest: start a move right away (pounce, triple, webs, spray, eggs, skitter, swipe, field, spent, shriek, combo). */
    public void testMove(String move) {
        if (!(this.level() instanceof ServerLevel sl)) return;
        LivingEntity t = target(sl);
        if (t == null) return;
        setState(State.CHASE, 0);
        combo.clear();
        switch (move) {
            case "spent" -> startSpent();
            case "combo" -> nextMove = 0;
            case "shriek" -> startShriek(sl);
            default -> start(sl, Move.valueOf(move.toUpperCase(java.util.Locale.ROOT)), t);
        }
    }
}
