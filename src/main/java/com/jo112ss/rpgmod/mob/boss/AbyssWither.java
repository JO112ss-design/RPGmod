package com.jo112ss.rpgmod.mob.boss;

import com.jo112ss.rpgmod.job.fx.ServerFx;
import com.jo112ss.rpgmod.mob.MobConfig;
import com.jo112ss.rpgmod.mob.MobLevels;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Abyssal Wither: the hidden boss, behind a key the Ender Dragon leaves. A wither half again as big,
 * fought in a dungeon arena (no block is broken there). Its health is the dragon's (base x level growth x
 * players). It hovers low around its prey and strings together a combo of three moves (four below half
 * health); every second combo it is <b>spent</b> and sinks to the floor (1.4x damage taken).
 * <ul>
 * <li>skull volley: its three heads fire black skulls at the players, three or four rounds</li>
 * <li>skull rain: circles under and around everyone; blue skulls fall on them and burst (withering)</li>
 * <li>dive: it rises, a red lane shows its path, then it dives straight through it</li>
 * <li>withering nova: a ring round it fills, then a wave that withers and throws back everyone inside</li>
 * <li>summon: wither skeletons climb out of the floor</li>
 * <li>below half health it awakens (armour of light, a shockwave, more skeletons), dives twice, and adds
 * the <b>triple death ray</b> (each head locks on to someone) and the <b>skull storm</b> (a spiral of skulls)</li>
 * </ul>
 * Its own attacks go through the bosses' rules (one hit takes at most a fifth of a player's health).
 * The vanilla wither's own head shots, block breaking, spawn blast and armour against arrows are gone.
 */
public class AbyssWither extends WitherBoss implements ModBoss {
    public static final float SCALE = 1.6F;

    public enum State { CHASE, VOLLEY_UP, VOLLEY, RAIN_UP, DIVE_AIM, DIVE, NOVA_UP, SUMMON, BEAM_AIM, STORM, SPENT, AWAKEN, CAST_END }

    enum Move { VOLLEY, RAIN, DIVE, NOVA, SUMMON, BEAMS, STORM }

    private static final EntityDataAccessor<Integer> DATA_STATE = SynchedEntityData.defineId(AbyssWither.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(AbyssWither.class, EntityDataSerializers.INT);

    public static final int MAX_MINIONS = 5;
    public static final double NOVA_RADIUS = 8, BEAM_RANGE = 36, DIVE_LENGTH = 26;
    public static final String MINION_OF = "rpgmod_wither_minion";
    private static final int BLACK = 0x2A2030, BLUE = 0x6AB0FF, VIOLET = 0x9A40FF;

    private State state = State.CHASE;
    private int stateTicks, stateLength;
    private int nextMove = 50;
    private final Deque<Move> combo = new ArrayDeque<>();
    private final BossCore core = new BossCore(this, "abyss_wither", MobConfig::dragonHealth, MobConfig::dragonPerPlayer, BossEvent.BossBarColor.WHITE, 0x7A4AB0);
    private int combos;
    private float damageTakenMult = 1;
    private double orbit;
    private int orbitDir = 1;
    private int rounds, dives;
    private Vec3 diveDir = Vec3.ZERO, diveFrom = Vec3.ZERO;
    private final java.util.Set<java.util.UUID> diveStruck = new java.util.HashSet<>();
    private final List<Skull> skulls = new ArrayList<>();
    private final List<Mark> marks = new ArrayList<>();
    private final List<LivingEntity> beamTargets = new ArrayList<>();
    private final List<Vec3> beamDirs = new ArrayList<>();
    private float stormAngle;
    /** Dev test counters. */
    public int skullsFired, skullHits, marksBurst, diveHits, novaHits, beamHits, summoned;

    public int clientStateTicks;

    /** A skull flying straight on: hits the first player it touches, or a wall. */
    private static final class Skull {
        Vec3 pos;
        final Vec3 vel;
        int life;
        final float damage;

        Skull(Vec3 pos, Vec3 vel, int life, float damage) {
            this.pos = pos;
            this.vel = vel;
            this.life = life;
            this.damage = damage;
        }
    }

    /** A blue skull about to land on a marked circle. */
    private record Mark(Vec3 at, int tick, float radius, float damage) {}

    public AbyssWither(EntityType<? extends AbyssWither> type, Level level) {
        super(type, level);
        this.xpReward = 600;
        // hovers in place when not told to move (the vanilla wither's control lets it sink)
        this.moveControl = new net.minecraft.world.entity.ai.control.FlyingMoveControl<>(this, 10, true);
        this.setNoGravity(true);
        this.refreshDimensions();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return WitherBoss.createAttributes()
                .add(Attributes.MAX_HEALTH, 800)
                .add(Attributes.FOLLOW_RANGE, 48)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.ARMOR, 10)
                .add(Attributes.ATTACK_DAMAGE, 8)
                .add(Attributes.SCALE, SCALE);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(DATA_STATE, 0);
        b.define(DATA_PHASE, 1);
    }

    /** No vanilla goals: it moves and attacks on its own (see {@link #serverTick}). */
    @Override
    protected void registerGoals() {
    }

    /** None of the vanilla wither's own work: head shots at anything near, block breaking, its spawn blast, its bar. */
    @Override
    protected void customServerAiStep(ServerLevel level) {
    }

    /** Its bar is the bosses' own (BossCore), not the vanilla wither's. */
    @Override
    public void startSeenByPlayer(ServerPlayer player) {
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
    }

    /** The armour of light shows once it has awakened; it never turns arrows away. */
    @Override
    public boolean isPowered() {
        return this.level().isClientSide() && phase() >= 2;
    }

    // ------------------------------------------------------------------ state

    @Override
    public BossCore core() {
        return core;
    }

    public int bossLevel() {
        return core.level();
    }

    @Override
    public int phase() {
        return this.entityData.get(DATA_PHASE);
    }

    public State state() {
        return State.values()[Mth.clamp(this.entityData.get(DATA_STATE), 0, State.values().length - 1)];
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

    // ------------------------------------------------------------------ tick

    @Override
    public void tick() {
        if (this.level() instanceof ServerLevel sl) serverTick(sl);
        super.tick();
        if (this.level().isClientSide()) clientStateTicks++;
    }

    private void serverTick(ServerLevel sl) {
        if (!this.isAlive()) return;
        this.setNoGravity(true);
        stateTicks++;
        core.tick(sl, phase(), state == State.SPENT ? BossCore.opening() : null);
        if (this.tickCount % 10 == 0) ServerFx.of("lich_aura", this.position()).follow(this).a(this.getBbWidth()).b(phase()).send(sl, 48);
        tickSkulls(sl);
        tickMarks(sl);
        if (phase() < 2 && this.getHealth() <= this.getMaxHealth() * 0.5F) startAwaken(sl);
        LivingEntity t = core.target(sl);
        // the heads look at their prey (the vanilla flight toward a target stays off: it moves on its own)
        this.setAlternativeTarget(0, 0);
        if (state != State.BEAM_AIM) {
            int id = t != null ? t.getId() : 0;
            this.setAlternativeTarget(1, id);
            this.setAlternativeTarget(2, id);
        }
        switch (state) {
            case CHASE -> chase(sl, t);
            case VOLLEY_UP -> volleyUp(sl, t);
            case VOLLEY -> volley(sl, t);
            case RAIN_UP, CAST_END -> {
                hover(t);
                if (stateTicks >= stateLength) moveDone();
            }
            case DIVE_AIM -> diveAim(sl, t);
            case DIVE -> dive(sl);
            case NOVA_UP -> novaUp(sl);
            case SUMMON -> summon(sl);
            case BEAM_AIM -> beamAim(sl);
            case STORM -> storm(sl);
            case SPENT -> spent(sl);
            case AWAKEN -> awaken(sl);
        }
    }

    private Vec3 headPos(int i) {
        Vec3 c = this.position().add(0, this.getBbHeight() * 0.86, 0);
        if (i == 0) return c;
        Vec3 side = Vec3.directionFromRotation(0, this.yBodyRot + (i == 1 ? 90 : -90));
        return c.add(side.scale(1.3 * SCALE)).add(0, -0.25 * SCALE, 0);
    }

    private void faceTarget(LivingEntity t) {
        if (t == null) return;
        Vec3 d = t.position().subtract(this.position());
        this.setYRot((float) Math.toDegrees(Math.atan2(-d.x, d.z)));
        this.yBodyRot = this.yHeadRot = this.getYRot();
        this.getLookControl().setLookAt(t, 60, 60);
    }

    /** Flies toward {@code goal} (eased), no faster than {@code speed} blocks a tick. */
    private void flyTo(Vec3 goal, double speed) {
        Vec3 d = goal.subtract(this.position());
        double len = d.length();
        Vec3 want = len < 0.2 ? Vec3.ZERO : d.scale(Math.min(speed, len * 0.25) / len);
        this.setDeltaMovement(this.getDeltaMovement().scale(0.6).add(want.scale(0.4)).add(0, want.y * 0.7, 0));
    }

    /** Holds its place in the air, facing its prey. */
    private void hover(LivingEntity t) {
        this.setDeltaMovement(this.getDeltaMovement().scale(0.5));
        faceTarget(t);
    }

    // ------------------------------------------------------------------ rhythm

    private void chase(ServerLevel sl, LivingEntity t) {
        if (t == null) {
            this.setDeltaMovement(this.getDeltaMovement().scale(0.8));
            return;
        }
        // circles its prey low enough to be struck (its body starts about a block over their feet)
        double r = phase() >= 2 ? 6.5 : 7.5;
        orbit += Math.toRadians(1.4) * orbitDir;
        if (this.random.nextInt(160) == 0) orbitDir = -orbitDir;
        Vec3 goal = t.position().add(Math.cos(orbit) * r, 1.1, Math.sin(orbit) * r);
        flyTo(goal, phase() >= 2 ? 0.42 : 0.34);
        this.getLookControl().setLookAt(t, 30, 30);
        if (--nextMove > 0) return;
        if (t.distanceToSqr(this) > 40 * 40) return;
        if (combo.isEmpty()) buildCombo(sl);
        Move m = combo.poll();
        if (m != null) start(sl, m, t);
    }

    private int minions(ServerLevel sl) {
        return sl.getEntitiesOfClass(Mob.class, this.getBoundingBox().inflate(48), m -> m.isAlive() && m.getPersistentData().getIntOr(MINION_OF, -1) == this.getId()).size();
    }

    /** Three moves (four below half health), no repeats; someone hugging it gets a nova first. */
    private void buildCombo(ServerLevel sl) {
        boolean p2 = phase() >= 2;
        List<Move> pool = new ArrayList<>(p2
                ? List.of(Move.VOLLEY, Move.RAIN, Move.RAIN, Move.DIVE, Move.DIVE, Move.BEAMS, Move.BEAMS, Move.STORM, Move.NOVA)
                : List.of(Move.VOLLEY, Move.VOLLEY, Move.RAIN, Move.RAIN, Move.DIVE, Move.DIVE, Move.NOVA));
        if (minions(sl) < (p2 ? 3 : 2)) pool.add(Move.SUMMON);
        int n = p2 ? 4 : 3;
        boolean close = !core.fighters(sl, 5).isEmpty();
        if (close && this.random.nextFloat() < 0.6F) {
            combo.add(Move.NOVA);
            pool.removeIf(x -> x == Move.NOVA);
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
            case VOLLEY -> startVolley(sl, t);
            case RAIN -> startRain(sl);
            case DIVE -> {
                dives = phase() >= 2 ? 2 : 1;
                startDive(sl, t);
            }
            case NOVA -> startNova(sl);
            case SUMMON -> startSummon(sl);
            case BEAMS -> startBeams(sl, t);
            case STORM -> startStorm(sl);
        }
    }

    private void moveDone() {
        if (!combo.isEmpty()) {
            setState(State.CHASE, 0);
            nextMove = phase() >= 2 ? 8 + this.random.nextInt(6) : 12 + this.random.nextInt(8);
        } else if (++combos % 2 == 0) {
            startSpent();
        } else {
            setState(State.CHASE, 0);
            nextMove = phase() >= 2 ? 14 + this.random.nextInt(8) : 22 + this.random.nextInt(10);
        }
    }

    // ------------------------------------------------------------------ spent (the opening)

    private void startSpent() {
        int len = phase() >= 2 ? 40 : 50;
        setState(State.SPENT, len);
        damageTakenMult = 1.4F;
        if (this.level() instanceof ServerLevel sl) {
            ServerFx.of("lich_drained", this.position()).follow(this).a(this.getBbWidth()).b(len).send(sl);
            sl.playSound(null, this.blockPosition(), SoundEvents.WITHER_HURT, SoundSource.HOSTILE, 2.0F, 0.5F);
        }
    }

    private void spent(ServerLevel sl) {
        // sinks to the floor, heads drooping
        Vec3 floor = BossCore.groundBelow(sl, this.position());
        this.setDeltaMovement(this.getDeltaMovement().multiply(0.3, 1, 0.3).add(0, this.getY() - floor.y > 0.2 ? -0.12 : 0, 0));
        if (stateTicks % 14 == 0) sl.playSound(null, this.blockPosition(), SoundEvents.WITHER_AMBIENT, SoundSource.HOSTILE, 1.0F, 0.4F);
        if (stateTicks >= stateLength) {
            setState(State.CHASE, 0);
            nextMove = phase() >= 2 ? 18 + this.random.nextInt(10) : 28 + this.random.nextInt(14);
        }
    }

    // ------------------------------------------------------------------ skull volley

    private void startVolley(ServerLevel sl, LivingEntity t) {
        int up = phase() >= 2 ? 10 : 14;
        faceTarget(t);
        setState(State.VOLLEY_UP, up);
        rounds = phase() >= 2 ? 4 : 3;
        ServerFx.of("lich_cast", this.position()).follow(this).a(this.getBbWidth()).b(up).color(VIOLET).send(sl);
        sl.playSound(null, this.blockPosition(), SoundEvents.WITHER_AMBIENT, SoundSource.HOSTILE, 2.5F, 0.7F);
    }

    private void volleyUp(ServerLevel sl, LivingEntity t) {
        hover(t);
        if (stateTicks >= stateLength) setState(State.VOLLEY, 8 * rounds);
    }

    private void volley(ServerLevel sl, LivingEntity t) {
        hover(t);
        if (stateTicks % 8 == 1) {
            List<ServerPlayer> fs = core.fighters(sl, 40);
            fs.sort(Comparator.comparingDouble(p -> p.distanceToSqr(this)));
            for (int h = 0; h < 3; h++) {
                LivingEntity aim = fs.isEmpty() ? t : fs.get(h % fs.size());
                if (aim == null) continue;
                Vec3 from = headPos(h);
                // a little ahead of where they are going
                Vec3 at = aim.position().add(0, aim.getBbHeight() * 0.5, 0).add(aim.getDeltaMovement().multiply(6, 0, 6));
                Vec3 dir = at.subtract(from).normalize();
                fireSkull(sl, from, dir.scale(0.9), 60, 8, BLACK);
            }
            sl.playSound(null, this.blockPosition(), SoundEvents.WITHER_SHOOT, SoundSource.HOSTILE, 2.0F, 0.8F);
        }
        if (stateTicks >= stateLength) setState(State.CAST_END, 8);
    }

    private void fireSkull(ServerLevel sl, Vec3 from, Vec3 vel, int life, float damage, int color) {
        skulls.add(new Skull(from, vel, life, damage));
        skullsFired++;
        ServerFx.of("lich_orb", from).vec(vel).a(life).color(color).send(sl, 64);
    }

    private void tickSkulls(ServerLevel sl) {
        for (var it = skulls.iterator(); it.hasNext(); ) {
            Skull s = it.next();
            Vec3 next = s.pos.add(s.vel);
            var block = sl.clip(new ClipContext(s.pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            boolean wall = block.getType() != HitResult.Type.MISS;
            Vec3 end = wall ? block.getLocation() : next;
            ServerPlayer hit = null;
            for (ServerPlayer p : sl.getEntitiesOfClass(ServerPlayer.class, new AABB(s.pos, end).inflate(0.9), BossCore::valid)) {
                if (p.getBoundingBox().inflate(0.4).clip(s.pos, end).isPresent() || p.getBoundingBox().inflate(0.4).contains(end)) {
                    hit = p;
                    break;
                }
            }
            if (hit != null) {
                hit.hurtServer(sl, this.damageSources().indirectMagic(this, this), s.damage);
                hit.addEffect(new MobEffectInstance(MobEffects.WITHER, 40, 0), this);
                core.participants.add(hit.getUUID());
                skullHits++;
                ServerFx.of("lich_orb_hit", end).color(VIOLET).send(sl, 48);
                it.remove();
                continue;
            }
            s.pos = end;
            if (wall) {
                sl.sendParticles(ParticleTypes.SMOKE, end.x, end.y, end.z, 6, 0.2, 0.2, 0.2, 0.02);
                it.remove();
            } else if (--s.life <= 0) {
                it.remove();
            }
        }
    }

    // ------------------------------------------------------------------ skull rain

    private void startRain(ServerLevel sl) {
        int up = 20;
        setState(State.RAIN_UP, up);
        ServerFx.of("lich_cast", this.position()).follow(this).a(this.getBbWidth()).b(up).color(BLUE).send(sl);
        sl.playSound(null, this.blockPosition(), SoundEvents.WITHER_AMBIENT, SoundSource.HOSTILE, 2.5F, 1.2F);
        int delay = phase() >= 2 ? 22 : 28;
        int extra = phase() >= 2 ? 4 : 2;
        for (ServerPlayer p : core.fighters(sl, 40)) {
            List<Vec3> spots = new ArrayList<>();
            spots.add(p.position().add(p.getDeltaMovement().multiply(6, 0, 6)));
            for (int i = 0; i < extra; i++) {
                double a = this.random.nextDouble() * Math.PI * 2, r = 2.5 + this.random.nextDouble() * 4;
                spots.add(p.position().add(Math.cos(a) * r, 0, Math.sin(a) * r));
            }
            for (int i = 0; i < spots.size(); i++) {
                Vec3 at = BossCore.groundBelow(sl, spots.get(i));
                int when = this.tickCount + 6 + delay + i * 4;
                marks.add(new Mark(at, when, 2.4F, 11));
                ServerFx.of("lich_sigil", at).a(2.4F).b(when - this.tickCount).color(BLUE).send(sl, 64);
            }
        }
    }

    private void tickMarks(ServerLevel sl) {
        for (var it = marks.iterator(); it.hasNext(); ) {
            Mark m = it.next();
            if (this.tickCount == m.tick() - 6) { // the blue skull comes down
                ServerFx.of("lich_orb", m.at().add(0, 7, 0)).vec(new Vec3(0, -1.15, 0)).a(6).color(BLUE).send(sl, 64);
            }
            if (this.tickCount < m.tick()) continue;
            it.remove();
            marksBurst++;
            for (ServerPlayer p : sl.players()) {
                if (!BossCore.valid(p)) continue;
                Vec3 v = p.position().subtract(m.at());
                if (Math.hypot(v.x, v.z) < m.radius() && v.y > -1 && v.y < 3) {
                    p.hurtServer(sl, this.damageSources().indirectMagic(this, this), m.damage());
                    p.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0), this);
                    core.participants.add(p.getUUID());
                }
            }
            sl.sendParticles(ParticleTypes.EXPLOSION, m.at().x, m.at().y + 0.5, m.at().z, 2, 0.4, 0.2, 0.4, 0);
            ServerFx.of("lich_nova", m.at()).a(m.radius()).color(BLUE).send(sl, 64);
            sl.playSound(null, m.at().x, m.at().y, m.at().z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.2F, 1.3F);
        }
    }

    // ------------------------------------------------------------------ dive

    private void startDive(ServerLevel sl, LivingEntity t) {
        if (t == null) {
            moveDone();
            return;
        }
        int aim = phase() >= 2 ? 18 : 24;
        setState(State.DIVE_AIM, aim);
        faceTarget(t);
        diveDir = t.position().subtract(this.position()).multiply(1, 0, 1);
        diveDir = diveDir.lengthSqr() < 1e-4 ? Vec3.directionFromRotation(0, this.getYRot()) : diveDir.normalize();
        Vec3 ground = new Vec3(this.getX(), t.getY(), this.getZ());
        ServerFx.of("king_dash_line", ground).vec(diveDir.scale(DIVE_LENGTH)).a(3.2F).b(aim).send(sl, 64);
        sl.playSound(null, this.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.2F, 1.6F);
    }

    private void diveAim(ServerLevel sl, LivingEntity t) {
        // rises a little and leans into the lane
        this.setDeltaMovement(this.getDeltaMovement().scale(0.5).add(0, 0.04, 0));
        float yaw = (float) Math.toDegrees(Math.atan2(-diveDir.x, diveDir.z));
        this.setYRot(yaw);
        this.yBodyRot = this.yHeadRot = yaw;
        if (stateTicks >= stateLength) {
            diveFrom = this.position();
            diveStruck.clear();
            setState(State.DIVE, 26);
            sl.playSound(null, this.blockPosition(), SoundEvents.WITHER_SHOOT, SoundSource.HOSTILE, 2.5F, 0.5F);
        }
    }

    private void dive(ServerLevel sl) {
        Vec3 before = this.position();
        LivingEntity t = this.getTarget();
        double floorY = t != null ? t.getY() + 0.3 : before.y;
        double dy = (floorY - before.y) * 0.25;
        this.setDeltaMovement(diveDir.scale(1.15).add(0, dy, 0));
        // anyone in its way is struck and thrown aside
        AABB sweep = this.getBoundingBox().inflate(0.9);
        for (ServerPlayer p : sl.getEntitiesOfClass(ServerPlayer.class, sweep, BossCore::valid)) {
            if (!diveStruck.add(p.getUUID())) continue; // once a dive
            p.hurtServer(sl, this.damageSources().mobAttack(this), 13);
            BossCore.fling(p, this.position(), 1.4, 0.5);
            core.participants.add(p.getUUID());
            diveHits++;
        }
        sl.sendParticles(ParticleTypes.SMOKE, before.x, before.y + 1.5, before.z, 6, 0.6, 0.6, 0.6, 0.02);
        boolean blocked = stateTicks > 3 && this.horizontalCollision;
        if (stateTicks >= stateLength || before.distanceTo(diveFrom) > DIVE_LENGTH || blocked) {
            this.setDeltaMovement(Vec3.ZERO);
            if (blocked) sl.playSound(null, this.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.0F, 0.7F);
            if (--dives > 0) {
                startDive(sl, core.target(sl));
            } else {
                setState(State.CAST_END, 14);
            }
        }
    }

    // ------------------------------------------------------------------ withering nova

    private void startNova(ServerLevel sl) {
        int up = phase() >= 2 ? 18 : 22;
        setState(State.NOVA_UP, up);
        ServerFx.of("rv_circle", BossCore.groundBelow(sl, this.position())).a((float) NOVA_RADIUS).b(up).color(VIOLET).send(sl, 64);
        ServerFx.of("lich_cast", this.position()).follow(this).a(this.getBbWidth()).b(up).color(VIOLET).send(sl);
        sl.playSound(null, this.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 2.0F, 0.4F);
    }

    private void novaUp(ServerLevel sl) {
        this.setDeltaMovement(this.getDeltaMovement().scale(0.4));
        if (stateTicks < stateLength) return;
        Vec3 c = BossCore.groundBelow(sl, this.position());
        for (ServerPlayer p : sl.players()) {
            if (!BossCore.valid(p)) continue;
            Vec3 d = p.position().subtract(c);
            if (Math.hypot(d.x, d.z) <= NOVA_RADIUS && d.y > -2 && d.y < 7) {
                p.hurtServer(sl, this.damageSources().indirectMagic(this, this), 11);
                p.addEffect(new MobEffectInstance(MobEffects.WITHER, 80, 0), this);
                BossCore.fling(p, c, 1.3, 0.5);
                core.participants.add(p.getUUID());
                novaHits++;
            }
        }
        ServerFx.of("lich_nova", c).a((float) NOVA_RADIUS).color(VIOLET).send(sl, 64);
        sl.playSound(null, this.blockPosition(), SoundEvents.WITHER_BREAK_BLOCK, SoundSource.HOSTILE, 1.5F, 0.6F);
        sl.playSound(null, this.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 1.0F, 0.8F);
        setState(State.CAST_END, 12);
    }

    // ------------------------------------------------------------------ summon

    private void startSummon(ServerLevel sl) {
        setState(State.SUMMON, 30);
        ServerFx.of("lich_summon", this.position()).follow(this).a(this.getBbWidth()).b(30).send(sl, 64);
        sl.playSound(null, this.blockPosition(), SoundEvents.WITHER_AMBIENT, SoundSource.HOSTILE, 2.5F, 0.5F);
    }

    private void summon(ServerLevel sl) {
        hover(core.target(sl));
        if (stateTicks < stateLength) return;
        int players = com.jo112ss.rpgmod.dungeon.Dungeons.bossPlayers(this, sl);
        raiseSkeletons(sl, (phase() >= 2 ? 3 : 2) + players / 2);
        setState(State.CAST_END, 10);
    }

    private void raiseSkeletons(ServerLevel sl, int n) {
        n = Math.min(n, MAX_MINIONS - minions(sl));
        LivingEntity t = core.target(sl);
        Vec3 around = t != null ? t.position() : this.position();
        for (int i = 0; i < n; i++) {
            double a = this.random.nextDouble() * Math.PI * 2, r = 4 + this.random.nextDouble() * 4;
            Vec3 at = BossCore.groundBelow(sl, around.add(Math.cos(a) * r, 1, Math.sin(a) * r));
            if (!sl.getBlockState(BlockPos.containing(at)).isAir() || !sl.getBlockState(BlockPos.containing(at).above()).isAir()
                    || !sl.getBlockState(BlockPos.containing(at).above(2)).isAir()) continue;
            var m = net.minecraft.world.entity.EntityTypes.WITHER_SKELETON.create(sl, EntitySpawnReason.MOB_SUMMONED);
            if (m == null) continue;
            m.snapTo(at.x, at.y, at.z, this.random.nextFloat() * 360, 0);
            m.getPersistentData().putBoolean(BossEvents.CHILD, true);
            m.getPersistentData().putInt(MINION_OF, this.getId());
            m.finalizeSpawn(sl, sl.getCurrentDifficultyAt(m.blockPosition()), EntitySpawnReason.MOB_SUMMONED, null);
            MobLevels.forceLevel = bossLevel();
            com.jo112ss.rpgmod.mob.Elite.forceNext = 0;
            try {
                sl.addFreshEntity(m);
            } finally {
                MobLevels.forceLevel = null;
                com.jo112ss.rpgmod.mob.Elite.forceNext = null;
            }
            m.setTarget(t);
            summoned++;
            ServerFx.of("dg_summon", m.position()).a(m.getBbWidth()).color(VIOLET).send(sl, 64);
        }
        sl.playSound(null, this.blockPosition(), SoundEvents.WITHER_SKELETON_AMBIENT, SoundSource.HOSTILE, 2.0F, 0.5F);
    }

    private void dismissMinions(ServerLevel sl) {
        for (Mob m : sl.getEntitiesOfClass(Mob.class, this.getBoundingBox().inflate(64), m -> m.getPersistentData().getIntOr(MINION_OF, -1) == this.getId())) {
            ServerFx.of("lich_blink", m.position()).a(m.getBbWidth()).b(m.getBbHeight()).send(sl, 48);
            m.discard();
        }
    }

    // ------------------------------------------------------------------ triple death ray (below half health)

    private void startBeams(ServerLevel sl, LivingEntity t) {
        int aim = 30;
        setState(State.BEAM_AIM, aim);
        beamTargets.clear();
        beamDirs.clear();
        List<ServerPlayer> fs = core.fighters(sl, BEAM_RANGE);
        fs.sort(Comparator.comparingDouble(p -> p.distanceToSqr(this)));
        for (int h = 0; h < 3; h++) {
            LivingEntity aimAt = fs.isEmpty() ? t : fs.get(h % fs.size());
            beamTargets.add(aimAt);
            beamDirs.add(Vec3.ZERO);
            if (aimAt != null) {
                if (h > 0) this.setAlternativeTarget(h, aimAt.getId());
                ServerFx.of("x_aim", headPos(h)).follow(this).b(aim).color(0xFF3040).send(sl, 64);
            }
        }
        sl.playSound(null, this.blockPosition(), SoundEvents.GUARDIAN_ATTACK, SoundSource.HOSTILE, 2.5F, 0.5F);
    }

    private void beamAim(ServerLevel sl) {
        LivingEntity main = beamTargets.isEmpty() ? null : beamTargets.get(0);
        hover(main);
        // each head's sight follows its prey, then holds still for the last 10 ticks
        for (int h = 0; h < beamTargets.size(); h++) {
            LivingEntity t = beamTargets.get(h);
            if (t == null || !t.isAlive()) continue;
            if (stateTicks < stateLength - 10) {
                // with more than one head on the same player, the others aim to either side of them
                Vec3 at = t.position().add(0, t.getBbHeight() * 0.5, 0);
                boolean shared = h > 0 && beamTargets.get(0) == t;
                if (shared) at = at.add(Vec3.directionFromRotation(0, this.getYRot() + (h == 1 ? 90 : -90)).scale(2.2));
                beamDirs.set(h, at.subtract(headPos(h)).normalize());
            }
            if (stateTicks == stateLength - 10) ServerFx.of("lich_beam_lock", headPos(h)).vec(beamDirs.get(h).scale(BEAM_RANGE)).send(sl, 64);
        }
        if (stateTicks == stateLength - 10) sl.playSound(null, this.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 2.0F, 1.4F);
        if (stateTicks < stateLength) return;
        for (int h = 0; h < beamDirs.size(); h++) {
            Vec3 dir = beamDirs.get(h);
            if (dir.lengthSqr() < 1e-4) continue;
            Vec3 from = headPos(h), to = from.add(dir.scale(BEAM_RANGE));
            var block = sl.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (block.getType() != HitResult.Type.MISS) to = block.getLocation();
            for (ServerPlayer p : sl.players()) {
                if (!BossCore.valid(p)) continue;
                if (p.getBoundingBox().inflate(0.6).clip(from, to).isPresent()) {
                    p.hurtServer(sl, this.damageSources().indirectMagic(this, this), 12);
                    core.participants.add(p.getUUID());
                    beamHits++;
                }
            }
            ServerFx.of("lich_beam", from).vec(to.subtract(from)).color(VIOLET).send(sl, 64);
        }
        sl.playSound(null, this.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 1.8F, 0.7F);
        setState(State.CAST_END, 14);
    }

    // ------------------------------------------------------------------ skull storm (below half health)

    private void startStorm(ServerLevel sl) {
        setState(State.STORM, 80);
        stormAngle = this.random.nextFloat() * 360;
        ServerFx.of("mob_warn", this.position()).follow(this).a(this.getBbWidth() / 2 + 1).b(16).color(VIOLET).send(sl);
        sl.playSound(null, this.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.5F, 0.9F);
    }

    private void storm(ServerLevel sl) {
        this.setDeltaMovement(this.getDeltaMovement().scale(0.5).add(0, 0.01, 0));
        if (stateTicks > 16 && stateTicks % 3 == 0) {
            Vec3 from = this.position().add(0, this.getBbHeight() * 0.6, 0);
            for (int k = 0; k < 4; k++) {
                double a = Math.toRadians(stormAngle + k * 90);
                fireSkull(sl, from, new Vec3(Math.cos(a), -0.08, Math.sin(a)).scale(0.38), 70, 6, BLACK);
            }
            stormAngle += 13;
            if (stateTicks % 12 == 0) sl.playSound(null, this.blockPosition(), SoundEvents.WITHER_SHOOT, SoundSource.HOSTILE, 1.5F, 1.1F);
        }
        if (stateTicks >= stateLength) moveDone();
    }

    // ------------------------------------------------------------------ awakening (half health)

    private void startAwaken(ServerLevel sl) {
        this.entityData.set(DATA_PHASE, 2);
        combo.clear();
        marks.clear();
        setState(State.AWAKEN, 50);
        core.enraged();
        ServerFx.of("lich_rise", this.position()).follow(this).a(this.getBbWidth()).b(50).send(sl, 64);
        sl.playSound(null, this.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 2.0F, 0.8F);
        for (ServerPlayer p : core.barPlayers()) p.sendSystemMessage(Component.translatable("rpgmod.boss.abyss_wither.enraged").withStyle(ChatFormatting.DARK_PURPLE), true);
    }

    private void awaken(ServerLevel sl) {
        hover(core.target(sl));
        if (stateTicks == 25) { // the armour of light bursts out: everyone near is thrown back
            for (ServerPlayer p : core.fighters(sl, 12)) {
                p.hurtServer(sl, this.damageSources().indirectMagic(this, this), 6);
                BossCore.fling(p, this.position(), 1.6, 0.6);
            }
            ServerFx.of("lich_nova", BossCore.groundBelow(sl, this.position())).a(12).color(0xFFFFFF).send(sl, 64);
            sl.playSound(null, this.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 2.0F, 0.6F);
            raiseSkeletons(sl, 2);
        }
        if (stateTicks >= stateLength) {
            setState(State.CHASE, 0);
            nextMove = 16;
        }
    }

    // ------------------------------------------------------------------ damage taken

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        core.hurtBy(source);
        if (state == State.SPENT) damage *= damageTakenMult;
        if (state == State.AWAKEN) damage *= 0.3F;
        return super.hurtServer(level, source, damage);
    }

    public float openingMultiplier() {
        return state == State.SPENT ? damageTakenMult : state == State.AWAKEN ? 0.3F : 1;
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return false;
    }

    @Override
    protected float getSoundVolume() {
        return 2.5F;
    }

    @Override
    public float getVoicePitch() {
        return 0.7F;
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource source) {
        return false;
    }

    @Override
    public void remove(RemovalReason reason) {
        core.removeBar();
        if (this.level() instanceof ServerLevel sl) dismissMinions(sl);
        super.remove(reason);
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!(this.level() instanceof ServerLevel sl)) return;
        dismissMinions(sl);
        com.jo112ss.rpgmod.job.SkillTasks.later(10, () -> dismissMinions(sl));
        core.died(sl);
        ServerFx.of("lich_death", this.position()).a(this.getBbWidth()).b(this.getBbHeight()).send(sl, 96);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.putInt("AbyssPhase", phase());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        this.entityData.set(DATA_PHASE, in.getIntOr("AbyssPhase", 1));
        this.setInvulnerableTicks(0);
    }

    // ------------------------------------------------------------------ dev test

    /** Autotest: a move right away (volley, rain, dive, nova, summon, beams, storm, spent, awaken, combo). */
    public void testMove(String move) {
        if (!(this.level() instanceof ServerLevel sl)) return;
        LivingEntity t = core.target(sl);
        if (t == null) return;
        setState(State.CHASE, 0);
        combo.clear();
        switch (move) {
            case "spent" -> startSpent();
            case "combo" -> nextMove = 0;
            case "awaken" -> startAwaken(sl);
            default -> start(sl, Move.valueOf(move.toUpperCase(java.util.Locale.ROOT)), t);
        }
    }
}
