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
import net.minecraft.world.phys.Vec3;

/**
 * King Slime, a dungeon boss: a giant crowned slime with a rhythm. It hops after its target
 * quickly, then strings together a <b>combo</b> of readable moves (three, four below half health);
 * every second combo ends <b>exhausted</b>: it sags and pants for a moment and takes 1.4x damage. A
 * dash into a wall leaves it dazed for a while too.
 * <ul>
 * <li>slam: red circle where it will land, a leap, a hit inside the circle and a ground shockwave
 * that can be jumped</li>
 * <li>bounce: a string of quick low hops after you, each with a small circle and ripple</li>
 * <li>dash: a line marks the path, then it slides straight along it leaving sticky puddles</li>
 * <li>slime rain: globs shot into the sky come down on marked spots around every player</li>
 * <li>spit: slow slime globs in a fan</li>
 * <li>below half health: triple slam, stomp ripples (three rings to jump in rhythm) and absorbing
 * its small slimes to heal unless they are killed first</li>
 * <li>split: at 75 / 50 / 25 % health it shrinks a size and throws out small slimes</li>
 * </ul>
 * No single hit takes more than a fifth of a player's health (see {@link BossEvents}). Health follows
 * the boss level (highest player job level) and the number of players online..
 */
public class KingSlime extends Slime implements ModBoss {
    public static final int START_SIZE = 10;
    private static final int[] SPLIT_SIZES = {8, 7, 6};
    private static final float[] SPLIT_AT = {0.75F, 0.5F, 0.25F};

    /** What the king is doing (synced: the renderer squashes and stretches the body by it). */
    public enum State { CHASE, WINDUP, LEAP, STUN, SPIT, ROAR, DASH_AIM, DASH, STOMP, TIRED, ABSORB, CAST }

    enum Move { SLAM, TRIPLE, BOUNCE, SPIT, DASH, RAIN, RIPPLE, ABSORB }

    enum Hop { SLAM, TRIPLE, BOUNCE }

    private static final EntityDataAccessor<Integer> DATA_STATE = SynchedEntityData.defineId(KingSlime.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(KingSlime.class, EntityDataSerializers.INT);

    // ---- server state
    private State state = State.CHASE;
    private int stateTicks;
    private int stateLength;
    private int nextMove = 50;
    private final Deque<Move> combo = new ArrayDeque<>();
    /** Health, boss bar, targets, water, rewards: what every boss shares. */
    private final BossCore core = new BossCore(this, "king_slime", MobConfig::kingHealth, BossEvent.BossBarColor.BLUE, 0x5FD8FF);
    private boolean relocating, holdShore;
    private int splits;
    private float damageTakenMult = 1;
    private int absorbCooldown = 300;
    private int lastLand = -100;
    /**
     * Vanilla caps max health at 1024: past that the king keeps a capped health bar and takes damage
     * divided by this, so it still lasts as long as its full health would.
     */
    private final Map<UUID, Integer> touchCooldown = new HashMap<>();
    private final List<Wave> waves = new ArrayList<>();
    private final List<Drop> drops = new ArrayList<>();
    private final List<Puddle> puddles = new ArrayList<>();
    /** Dev test: how many dashes ended against a wall. */
    public int wallHits;

    // leaps (slam / triple / bounce)
    private Hop hop = Hop.SLAM;
    private int hopsLeft;
    private Vec3 leapFrom = Vec3.ZERO, leapTo = Vec3.ZERO;
    private int leapTime;
    private double leapHeight;
    // dash
    private Vec3 dashDir = Vec3.ZERO;
    private final Set<UUID> dashHit = new HashSet<>();
    // stomp
    private int stompsDone;
    private boolean stompAir;

    // ---- client animation
    public int clientStateTicks;

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

    /** A glob of slime rain falling on a marked spot. */
    private record Drop(Vec3 at, int landTick) {}

    /** A sticky puddle left by the dash: slows whoever stands in it. */
    private static final class Puddle {
        final Vec3 at;
        int life;

        Puddle(Vec3 at, int life) {
            this.at = at;
            this.life = life;
        }
    }

    public KingSlime(EntityType<? extends KingSlime> type, Level level) {
        super(type, level);
        this.setSize(START_SIZE, true);
        this.xpReward = 200;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 800)
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

    private double chaseSpeed() {
        return phase() >= 2 ? 1.1 : 0.95;
    }

    @Override
    public void setSize(int size, boolean updateHealth) {
        super.setSize(size, updateHealth);
        // vanilla scales speed and damage with size: the king keeps its own numbers
        this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(4);
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(chaseSpeed());
        this.xpReward = 200;
    }

    @Override
    protected void setCubeMobHealth(int actualSize) {
        if (core.healthTarget() > 0) this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(core.healthTarget());
        else super.setCubeMobHealth(actualSize);
    }

    public int bossLevel() {
        return core.level();
    }

    public int phase() {
        return this.entityData.get(DATA_PHASE);
    }

    public State state() {
        return State.values()[Mth.clamp(this.entityData.get(DATA_STATE), 0, State.values().length - 1)];
    }

    // ------------------------------------------------------------------ tick

    @Override
    protected boolean isImmobile() {
        // during its moves the king is steered by the move itself, not by the slime AI
        return super.isImmobile() || (!this.level().isClientSide() && (state != State.CHASE || holdShore));
    }

    @Override
    protected int getJumpDelay() {
        // divided by 3 while chasing: a hop every 6-9 ticks, 4-7 below half health
        return phase() >= 2 ? 12 + this.random.nextInt(9) : 18 + this.random.nextInt(10);
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
        damageTakenMult = 1;
        this.entityData.set(DATA_STATE, s.ordinal());
    }

    private void serverTick(ServerLevel sl) {
        if (!this.isAlive()) return;
        stateTicks++;
        if (absorbCooldown > 0) absorbCooldown--;
        core.tick(sl, phase(), (state == State.TIRED || (state == State.STUN && damageTakenMult > 1.4F)) ? BossCore.opening() : null);
        tickWaves(sl);
        tickDrops(sl);
        tickPuddles(sl);
        // in the water mid-move: drop it and get out first
        if (state != State.CHASE && state != State.LEAP && state != State.WINDUP && state != State.DASH && stateTicks > 10
                && BossWater.inWater(this)) {
            relocating = false;
            setState(State.CHASE, 0);
        }
        touchCooldown.replaceAll((k, v) -> v - 1);
        touchCooldown.values().removeIf(v -> v <= 0);
        checkSplit(sl);

        switch (state) {
            case CHASE -> chase(sl);
            case WINDUP -> windup(sl);
            case LEAP -> leap(sl);
            case STUN, TIRED -> {
                this.setDeltaMovement(this.getDeltaMovement().multiply(0.5, 1, 0.5));
                if (stateTicks % (state == State.TIRED ? 14 : 8) == 0) {
                    sl.playSound(null, this.blockPosition(), SoundEvents.SLIME_SQUISH_SMALL, SoundSource.HOSTILE, 0.9F, state == State.TIRED ? 0.45F : 0.6F);
                }
                if (stateTicks >= stateLength) {
                    if (state == State.TIRED) restAfterTired();
                    else moveDone();
                }
            }
            case SPIT -> spit(sl);
            case ROAR -> {
                this.setDeltaMovement(this.getDeltaMovement().multiply(0.3, 1, 0.3));
                if (stateTicks >= stateLength) moveDone();
            }
            case DASH_AIM -> dashAim(sl);
            case DASH -> dash(sl);
            case STOMP -> stomp(sl);
            case ABSORB -> absorb(sl);
            case CAST -> cast(sl);
        }
    }

    // ------------------------------------------------------------------ rhythm: combo -> exhausted

    private void chase(ServerLevel sl) {
        LivingEntity t = core.target(sl);
        holdShore = false;
        if (t == null) return;
        // out of the water / to the shore nearest someone out on it
        Vec3 dry = core.water.relocate(sl, this, t);
        if (dry != null) {
            relocating = true;
            hop = Hop.SLAM;
            hopsLeft = 1;
            windupTo(sl, dry, true);
            return;
        }
        // someone out on the water: no hopping in after them (from close by), homing slime instead
        holdShore = core.water.ranged(t) && t.distanceToSqr(this) < 24 * 24 && !BossWater.inWater(this);
        if (core.water.homingDue(t, phase())) core.homing.volley(sl, this, t, phase() >= 2 ? 5 : 3, 6, 0.6, 14);
        if (--nextMove > 0 || !this.onGround()) return;
        if (Math.sqrt(t.distanceToSqr(this)) > 40) return;
        if (combo.isEmpty()) buildCombo(sl);
        Move m = combo.poll();
        if (m != null) start(sl, m, t);
    }

    /** Two moves (three below half health), no repeats; may open with absorbing its small slimes. */
    private void buildCombo(ServerLevel sl) {
        boolean p2 = phase() >= 2;
        List<Move> pool = new ArrayList<>(p2
                ? List.of(Move.TRIPLE, Move.TRIPLE, Move.SLAM, Move.BOUNCE, Move.BOUNCE, Move.SPIT, Move.DASH, Move.DASH, Move.RAIN, Move.RIPPLE, Move.RIPPLE)
                : List.of(Move.SLAM, Move.SLAM, Move.BOUNCE, Move.BOUNCE, Move.SPIT, Move.DASH, Move.DASH, Move.RAIN));
        int n = p2 ? 4 : 3;
        if (core.water.ranged(core.target(sl))) { // someone out on the water: spit and rain only
            pool = new ArrayList<>(List.of(Move.SPIT, Move.RAIN));
            n = 2;
        }
        if (p2 && absorbCooldown <= 0 && children(sl).size() >= 2 && this.random.nextBoolean()) {
            combo.add(Move.ABSORB);
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
        switch (m) {
            case SLAM -> startHops(sl, t, Hop.SLAM, 1);
            case TRIPLE -> startHops(sl, t, Hop.TRIPLE, 3);
            case BOUNCE -> startHops(sl, t, Hop.BOUNCE, phase() >= 2 ? 6 : 4);
            case SPIT -> startSpit(sl);
            case DASH -> startDash(sl, t);
            case RAIN -> startRain(sl);
            case RIPPLE -> startStomp(sl);
            case ABSORB -> startAbsorb(sl);
        }
    }

    /** A move finished: next move of the combo after a short hop, or the exhausted window. */
    private void moveDone() {
        if (!combo.isEmpty()) {
            setState(State.CHASE, 0);
            nextMove = phase() >= 2 ? 6 + this.random.nextInt(6) : 10 + this.random.nextInt(8);
        } else if (++combos % 2 == 0) {
            startTired(); // exhausted after every second combo only
        } else {
            setState(State.CHASE, 0);
            nextMove = phase() >= 2 ? 14 + this.random.nextInt(8) : 20 + this.random.nextInt(10);
        }
    }

    /** Combos done (every second one ends exhausted). */
    private int combos;

    /** A short pause to land, with no extra damage taken (the openings are kept for the big moments). */
    private void recover(int ticks) {
        setState(State.STUN, ticks);
        damageTakenMult = 1;
    }

    private void startTired() {
        int len = phase() >= 2 ? 40 : 50;
        setState(State.TIRED, len);
        damageTakenMult = 1.4F;
        if (this.level() instanceof ServerLevel sl) {
            ServerFx.of("king_tired", this.position()).follow(this).a(this.getBbWidth()).b(len).send(sl);
            sl.playSound(null, this.blockPosition(), SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 2.5F, 0.35F);
        }
    }

    private void restAfterTired() {
        setState(State.CHASE, 0);
        nextMove = phase() >= 2 ? 22 + this.random.nextInt(14) : 30 + this.random.nextInt(20);
    }

    private void stun(ServerLevel sl, int ticks, float mult) {
        setState(State.STUN, ticks);
        damageTakenMult = mult;
        ServerFx.of("king_stun", this.position()).follow(this).a(this.getBbWidth()).b(ticks).send(sl);
    }

    // ------------------------------------------------------------------ slam / triple / bounce

    private float hitRadius() {
        return this.getBbWidth() / 2 + (hop == Hop.BOUNCE ? 0.8F : 1.2F);
    }

    private void startHops(ServerLevel sl, LivingEntity t, Hop kind, int hops) {
        hop = kind;
        hopsLeft = hops;
        windupAt(sl, t, true);
    }

    private void windupAt(ServerLevel sl, LivingEntity t, boolean first) {
        windupTo(sl, t.position().add(t.getDeltaMovement().multiply(4, 0, 4)), first); // a little lead
    }

    private void windupTo(ServerLevel sl, Vec3 aim, boolean first) {
        boolean p2 = phase() >= 2;
        Vec3 flat = aim.subtract(this.position()).multiply(1, 0, 1);
        double dist = flat.length();
        int windup;
        switch (hop) {
            case BOUNCE -> {
                if (dist > 7) aim = this.position().add(flat.normalize().scale(7)); // short hops
                dist = Math.min(dist, 7);
                windup = first ? 10 : 4;
                leapTime = 10;
                leapHeight = 2.2;
            }
            case TRIPLE -> {
                windup = first ? 12 : 7;
                leapTime = (int) Mth.clamp(8 + dist * 0.6, 10, 18);
                leapHeight = 3 + dist * 0.15;
            }
            default -> {
                windup = p2 ? 11 : 14;
                leapTime = (int) Mth.clamp(8 + dist * 0.7, 12, 22);
                leapHeight = 4.5 + dist * 0.2;
            }
        }
        Vec3 to = BossCore.groundBelow(sl, aim);
        leapFrom = this.position();
        leapTo = to;
        setState(State.WINDUP, windup);
        this.getNavigation().stop();
        // the circle stays until the landing: windup + flight
        ServerFx.of("king_mark", to).a(hitRadius()).b(windup + leapTime).send(sl);
        if (first || hop != Hop.BOUNCE) sl.playSound(null, this.blockPosition(), SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 3.0F, 0.5F);
    }

    private void windup(ServerLevel sl) {
        this.setDeltaMovement(this.getDeltaMovement().multiply(0.2, 1, 0.2));
        if (stateTicks >= stateLength) {
            leapFrom = this.position();
            setState(State.LEAP, leapTime);
            sl.playSound(null, this.blockPosition(), hop == Hop.BOUNCE ? SoundEvents.SLIME_JUMP_SMALL : SoundEvents.SLIME_JUMP, SoundSource.HOSTILE, 3.0F, 0.6F);
        }
    }

    private void leap(ServerLevel sl) {
        double s = Math.min(1, (stateTicks + 1) / (double) stateLength);
        Vec3 want = leapFrom.lerp(leapTo, s).add(0, leapHeight * 4 * s * (1 - s), 0);
        this.noPhysics = relocating && s < 0.85; // out of the water and over the bank
        this.setDeltaMovement(want.subtract(this.position()));
        this.needsSync = true;
        this.resetFallDistance();
        boolean blocked = !relocating && stateTicks > stateLength / 2 && this.onGround();
        if (s >= 1 || blocked || stateTicks > stateLength + 10) land(sl);
    }

    private void land(ServerLevel sl) {
        this.noPhysics = false;
        Vec3 c = this.position();
        lastLand = this.tickCount;
        float slam = switch (hop) { case BOUNCE -> 5; case TRIPLE -> 7; default -> 8; };
        float waveDmg = switch (hop) { case BOUNCE -> 3; case TRIPLE -> 4; default -> 5; };
        double reach = switch (hop) { case BOUNCE -> 4; case TRIPLE -> 6; default -> 9; };
        double r = hitRadius();
        for (ServerPlayer p : sl.players()) {
            if (!p.isAlive() || p.isSpectator()) continue;
            Vec3 d = p.position().subtract(c);
            if (Math.hypot(d.x, d.z) <= r && Math.abs(d.y) < 3) {
                p.hurtServer(sl, this.damageSources().mobAttack(this), slam);
                BossCore.fling(p, c, 0.9, 0.55);
                core.participants.add(p.getUUID());
            }
        }
        waves.add(new Wave(c, this.getBbWidth() / 2, this.getBbWidth() / 2 + reach, 0.6, waveDmg));
        ServerFx.of("king_land", c).a(this.getBbWidth() / 2).b(reach).send(sl);
        sl.playSound(null, this.blockPosition(), SoundEvents.SLIME_BLOCK_FALL, SoundSource.HOSTILE, 3.0F, hop == Hop.BOUNCE ? 0.8F : 0.5F);
        if (hop != Hop.BOUNCE) sl.playSound(null, this.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.2F, 0.7F);
        sl.playSound(null, this.blockPosition(), SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 3.0F, 0.4F);
        this.setDeltaMovement(Vec3.ZERO);
        if (relocating) { // a leap to dry ground is not a move of the combo
            relocating = false;
            hopsLeft = 0;
            setState(State.CHASE, 0);
            nextMove = Math.max(nextMove, 12);
            return;
        }
        if (--hopsLeft > 0) {
            LivingEntity t = core.target(sl);
            if (t != null) {
                windupAt(sl, t, false);
                return;
            }
        }
        switch (hop) {
            case SLAM -> recover(phase() >= 2 ? 8 : 10);
            case TRIPLE -> recover(14);
            default -> moveDone();
        }
    }

    private void tickWaves(ServerLevel sl) {
        for (var it = waves.iterator(); it.hasNext(); ) {
            Wave w = it.next();
            w.r += w.speed;
            for (ServerPlayer p : sl.players()) {
                if (!p.isAlive() || p.isSpectator() || w.hit.contains(p.getUUID())) continue;
                Vec3 d = p.position().subtract(w.c);
                double h = Math.hypot(d.x, d.z);
                // the ring hugs the ground: jumping over it (feet a block up) dodges it
                if (Math.abs(h - w.r) < 0.8 && d.y > -1.5 && d.y < 0.9) {
                    w.hit.add(p.getUUID());
                    p.hurtServer(sl, this.damageSources().mobAttack(this), w.damage);
                    BossCore.fling(p, w.c, 0.6, 0.35);
                }
            }
            if (w.r > w.max) it.remove();
        }
    }

    // ------------------------------------------------------------------ dash

    private void startDash(ServerLevel sl, LivingEntity t) {
        Vec3 d = t.position().subtract(this.position()).multiply(1, 0, 1);
        dashDir = d.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : d.normalize();
        int aim = phase() >= 2 ? 13 : 18;
        setState(State.DASH_AIM, aim);
        this.setYRot((float) Math.toDegrees(Math.atan2(-dashDir.x, dashDir.z)));
        this.yBodyRot = this.yHeadRot = this.getYRot();
        double len = dashSpeed() * 22;
        ServerFx.of("king_dash_line", this.position()).vec(dashDir.scale(len)).a(this.getBbWidth()).b(aim + 6).send(sl);
        sl.playSound(null, this.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.5F, 1.6F);
    }

    private double dashSpeed() {
        return phase() >= 2 ? 1.15 : 0.95;
    }

    private void dashAim(ServerLevel sl) {
        this.setDeltaMovement(this.getDeltaMovement().multiply(0.2, 1, 0.2));
        this.setYRot((float) Math.toDegrees(Math.atan2(-dashDir.x, dashDir.z)));
        this.yBodyRot = this.yHeadRot = this.getYRot();
        if (stateTicks >= stateLength) {
            dashHit.clear();
            setState(State.DASH, 22);
            sl.playSound(null, this.blockPosition(), SoundEvents.SLIME_JUMP, SoundSource.HOSTILE, 3.0F, 0.4F);
        }
    }

    private void dash(ServerLevel sl) {
        Vec3 v = this.getDeltaMovement();
        this.setDeltaMovement(dashDir.x * dashSpeed(), v.y, dashDir.z * dashSpeed());
        this.needsSync = true;
        this.setYRot((float) Math.toDegrees(Math.atan2(-dashDir.x, dashDir.z)));
        this.yBodyRot = this.yHeadRot = this.getYRot();
        var box = this.getBoundingBox().inflate(0.4);
        for (ServerPlayer p : sl.players()) {
            if (!p.isAlive() || p.isSpectator() || dashHit.contains(p.getUUID()) || !p.getBoundingBox().intersects(box)) continue;
            dashHit.add(p.getUUID());
            p.hurtServer(sl, this.damageSources().mobAttack(this), 7);
            // thrown aside, off the line
            Vec3 side = new Vec3(-dashDir.z, 0, dashDir.x);
            if (side.dot(p.position().subtract(this.position())) < 0) side = side.scale(-1);
            p.setDeltaMovement(p.getDeltaMovement().add(side.scale(1.0)).add(dashDir.scale(0.4)).add(0, 0.45, 0));
            p.syncVelocity = true;
        }
        if (stateTicks % 3 == 0) {
            Vec3 at = this.position();
            puddles.add(new Puddle(at, 160));
            ServerFx.of("king_puddle", at).a(1.6).b(160).send(sl);
        }
        if (stateTicks % 4 == 0) sl.playSound(null, this.blockPosition(), SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 1.5F, 0.7F);
        if (stateTicks > 3 && this.horizontalCollision) {
            // slammed into a wall: dazed for a long while - the best opening of the fight
            wallHits++;
            combo.clear();
            this.setDeltaMovement(dashDir.scale(-0.3).add(0, 0.2, 0));
            ServerFx.of("king_wall", this.position().add(dashDir.scale(this.getBbWidth() / 2)).add(0, 1, 0)).a(this.getBbWidth()).send(sl);
            sl.playSound(null, this.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.5F, 0.8F);
            sl.playSound(null, this.blockPosition(), SoundEvents.SLIME_BLOCK_FALL, SoundSource.HOSTILE, 3.0F, 0.4F);
            stun(sl, 45, 1.4F);
            return;
        }
        if (stateTicks >= stateLength) moveDone();
    }

    private void tickPuddles(ServerLevel sl) {
        if (puddles.isEmpty()) return;
        boolean check = this.tickCount % 10 == 0;
        for (var it = puddles.iterator(); it.hasNext(); ) {
            Puddle pd = it.next();
            pd.life--;
            if (check) {
                for (ServerPlayer p : sl.players()) {
                    Vec3 d = p.position().subtract(pd.at);
                    if (Math.hypot(d.x, d.z) < 1.6 && Math.abs(d.y) < 1.5) p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 1), this);
                }
            }
            if (pd.life <= 0) it.remove();
        }
    }

    // ------------------------------------------------------------------ slime rain

    private void startRain(ServerLevel sl) {
        setState(State.CAST, 18);
        ServerFx.of("mob_warn", this.position()).follow(this).a(this.getBbWidth() / 2).b(18).color(0x6C9CFF).send(sl);
        sl.playSound(null, this.blockPosition(), SoundEvents.PUFFER_FISH_BLOW_UP, SoundSource.HOSTILE, 2.0F, 0.4F);
    }

    private void cast(ServerLevel sl) {
        this.setDeltaMovement(this.getDeltaMovement().multiply(0.2, 1, 0.2));
        if (stateTicks == stateLength) {
            ServerFx.of("king_rain_up", this.position().add(0, this.getBbHeight(), 0)).a(this.getBbWidth()).send(sl);
            sl.playSound(null, this.blockPosition(), SoundEvents.LLAMA_SPIT, SoundSource.HOSTILE, 3.0F, 0.4F);
            int extra = phase() >= 2 ? 4 : 2;
            int land = this.tickCount + 26;
            for (ServerPlayer p : sl.players()) {
                if (!p.isAlive() || p.isSpectator() || p.distanceToSqr(this) > 34 * 34) continue;
                List<Vec3> spots = new ArrayList<>();
                spots.add(p.position().add(p.getDeltaMovement().multiply(8, 0, 8)));
                for (int i = 0; i < extra; i++) {
                    double a = this.random.nextDouble() * Math.PI * 2, r = 2 + this.random.nextDouble() * 4;
                    spots.add(p.position().add(Math.cos(a) * r, 0, Math.sin(a) * r));
                }
                for (int i = 0; i < spots.size(); i++) {
                    Vec3 at = BossCore.groundBelow(sl, spots.get(i));
                    int when = land + i * 3; // they come down one after another
                    drops.add(new Drop(at, when));
                    ServerFx.of("king_rain_mark", at).a(1.8).b(when - this.tickCount).send(sl);
                }
            }
        }
        if (stateTicks >= stateLength + 4) moveDone();
    }

    private void tickDrops(ServerLevel sl) {
        for (var it = drops.iterator(); it.hasNext(); ) {
            Drop d = it.next();
            if (this.tickCount < d.landTick) continue;
            it.remove();
            for (ServerPlayer p : sl.players()) {
                Vec3 v = p.position().subtract(d.at);
                if (Math.hypot(v.x, v.z) < 1.8 && Math.abs(v.y) < 2.5) {
                    p.hurtServer(sl, this.damageSources().mobAttack(this), 4);
                    p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 50, 1), this);
                }
            }
            ServerFx.of("king_rain_hit", d.at).send(sl);
            sl.playSound(null, d.at.x, d.at.y, d.at.z, SoundEvents.SLIME_BLOCK_FALL, SoundSource.HOSTILE, 1.5F, 1.2F);
        }
    }

    // ------------------------------------------------------------------ stomp ripples

    private static final int STOMP_LEAD = 12;

    private int stompEvery() {
        return phase() >= 2 ? 12 : 14;
    }

    private void startStomp(ServerLevel sl) {
        stompsDone = 0;
        stompAir = false;
        setState(State.STOMP, STOMP_LEAD + 3 * stompEvery() + 6);
        ServerFx.of("mob_warn", this.position()).follow(this).a(this.getBbWidth() / 2 + 2).b(STOMP_LEAD).color(0xFF8040).send(sl);
        sl.playSound(null, this.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.5F, 1.2F);
    }

    private void stomp(ServerLevel sl) {
        this.setDeltaMovement(this.getDeltaMovement().multiply(0.2, 1, 0.2));
        int k = stateTicks - STOMP_LEAD;
        if (k >= 0 && stompsDone < 3 && k == stompsDone * stompEvery()) {
            this.setDeltaMovement(0, 0.55, 0);
            this.needsSync = true;
            stompAir = true;
        } else if (stompAir && k > stompsDone * stompEvery() + 3 && this.onGround()) {
            stompAir = false;
            stompsDone++;
            Vec3 c = this.position();
            waves.add(new Wave(c, this.getBbWidth() / 2, this.getBbWidth() / 2 + 11, 0.65, 4));
            ServerFx.of("king_land", c).a(this.getBbWidth() / 2).b(11).send(sl);
            sl.playSound(null, this.blockPosition(), SoundEvents.SLIME_BLOCK_FALL, SoundSource.HOSTILE, 3.0F, 0.5F);
            sl.playSound(null, this.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.8F, 0.9F);
        }
        if (stateTicks >= stateLength) moveDone();
    }

    // ------------------------------------------------------------------ absorb

    private List<Slime> children(ServerLevel sl) {
        return sl.getEntitiesOfClass(Slime.class, this.getBoundingBox().inflate(24), s -> s != this && s.isAlive() && s.getPersistentData().getBooleanOr(BossEvents.CHILD, false));
    }

    private void startAbsorb(ServerLevel sl) {
        absorbCooldown = 600;
        setState(State.ABSORB, 70);
        damageTakenMult = 1;
        for (ServerPlayer p : core.barPlayers()) {
            p.sendSystemMessage(Component.translatable("rpgmod.boss.king_slime.absorb").withStyle(ChatFormatting.AQUA), true);
        }
        sl.playSound(null, this.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 2.0F, 0.5F);
    }

    private void absorb(ServerLevel sl) {
        this.setDeltaMovement(this.getDeltaMovement().multiply(0.2, 1, 0.2));
        Vec3 c = this.position().add(0, this.getBbHeight() * 0.4, 0);
        for (Slime s : children(sl)) {
            Vec3 d = c.subtract(s.position());
            if (d.length() < this.getBbWidth() / 2 + 0.8) {
                this.heal(this.getMaxHealth() * 0.025F);
                ServerFx.of("king_glob", s.position()).send(sl);
                sl.playSound(null, s.blockPosition(), SoundEvents.SLIME_SQUISH_SMALL, SoundSource.HOSTILE, 1.5F, 1.4F);
                s.discard();
                continue;
            }
            s.setDeltaMovement(d.normalize().scale(0.22).add(0, s.onGround() ? 0.15 : 0, 0));
            s.needsSync = true;
            if (stateTicks % 5 == 0) ServerFx.of("king_absorb", s.position().add(0, s.getBbHeight() / 2, 0)).vec(c).send(sl);
        }
        if (stateTicks >= stateLength) moveDone();
    }

    // ------------------------------------------------------------------ spit

    private void startSpit(ServerLevel sl) {
        int w = phase() >= 2 ? 9 : 12;
        setState(State.SPIT, w);
        ServerFx.of("mob_warn", this.position()).follow(this).a(this.getBbWidth() / 2).b(w).color(0x6CFF7A).send(sl);
        sl.playSound(null, this.blockPosition(), SoundEvents.PUFFER_FISH_BLOW_UP, SoundSource.HOSTILE, 2.0F, 0.6F);
    }

    private void spit(ServerLevel sl) {
        this.setDeltaMovement(this.getDeltaMovement().multiply(0.2, 1, 0.2));
        if (stateTicks < stateLength) return;
        LivingEntity t = core.target(sl);
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
                ball.shoot(dir.x, dir.y + dist * 0.12, dir.z, 0.8F, 2.0F);
                sl.addFreshEntity(ball);
            }
            sl.playSound(null, this.blockPosition(), SoundEvents.LLAMA_SPIT, SoundSource.HOSTILE, 2.5F, 0.5F);
        }
        moveDone();
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
            this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(chaseSpeed());
            core.enraged();
        }
        int online = com.jo112ss.rpgmod.dungeon.Dungeons.bossPlayers(this, sl);
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
        // a short roar instead of whatever it was doing (a leap in the air still lands first)
        if (state != State.LEAP && state != State.DASH) {
            combo.clear();
            drops.clear();
            setState(State.ROAR, 18);
        }
        ServerFx.of("king_split", this.position()).follow(this).a(this.getBbWidth()).b(splits).send(sl);
        sl.playSound(null, this.blockPosition(), SoundEvents.SLIME_DEATH, SoundSource.HOSTILE, 3.0F, 0.5F);
        sl.playSound(null, this.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.5F, 1.4F);
        if (phase() >= 2 && splits == 2) {
            for (ServerPlayer p : core.barPlayers()) {
                p.sendSystemMessage(Component.translatable("rpgmod.boss.king_slime.enraged").withStyle(ChatFormatting.LIGHT_PURPLE), true);
            }
        }
    }

    // ------------------------------------------------------------------ damage taken / dealt

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        core.hurtBy(source);
        // the openings: after a slam, dazed against a wall, exhausted after a combo, absorbing
        if (state == State.STUN || state == State.TIRED || state == State.ABSORB) damage *= damageTakenMult;
        // the toughness share is taken off last, in BossEvents.onKingHurt, so bonuses added on the way
        // (a bow's material and the shooter's own attack, fire weapon...) are divided too
        return super.hurtServer(level, source, damage);
    }

    /** Opening multiplier right now (for the overlay / tests): 1 when not open. */
    public float openingMultiplier() {
        return state == State.STUN || state == State.TIRED || state == State.ABSORB ? damageTakenMult : 1;
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

    @Override
    public void remove(RemovalReason reason) {
        core.removeBar();
        super.remove(reason);
    }

    // ------------------------------------------------------------------ leaving and dying

    private void dissolveChildren(ServerLevel sl) {
        for (Slime s : sl.getEntitiesOfClass(Slime.class, this.getBoundingBox().inflate(64), s -> s != this && s.getPersistentData().getBooleanOr(BossEvents.CHILD, false))) {
            ServerFx.of("king_glob", s.position()).send(sl);
            s.discard();
        }
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!(this.level() instanceof ServerLevel sl)) return;
        dissolveChildren(sl);
        core.died(sl);
        ServerFx.of("king_death", this.position()).a(this.getBbWidth()).send(sl);
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

    /** Autotest: start a move right away (slam, triple, bounce, spit, dash, rain, ripple, absorb, tired, combo). */
    public void testMove(String move) {
        if (!(this.level() instanceof ServerLevel sl)) return;
        LivingEntity t = core.target(sl);
        if (t == null) return;
        setState(State.CHASE, 0);
        combo.clear();
        switch (move) {
            case "tired" -> startTired();
            case "combo" -> nextMove = 0;
            default -> start(sl, Move.valueOf(move.toUpperCase(java.util.Locale.ROOT)), t);
        }
    }

    /** Health the fight is really worth (max health x toughness past the vanilla cap). */
    public float effectiveMaxHealth() {
        return this.getMaxHealth() * toughness();
    }

    public int splits() {
        return splits;
    }
}
