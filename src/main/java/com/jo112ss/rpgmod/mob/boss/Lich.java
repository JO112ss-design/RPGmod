package com.jo112ss.rpgmod.mob.boss;

import com.jo112ss.rpgmod.RpgMod;
import com.jo112ss.rpgmod.job.fx.ServerFx;
import com.jo112ss.rpgmod.mob.MobConfig;
import com.jo112ss.rpgmod.mob.MobLevels;
import com.jo112ss.rpgmod.mob.core.MobLevelMath;
import com.jo112ss.rpgmod.mob.revenant.Revenant;
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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Lich King: a giant skeletal sorcerer in a tattered robe, the master of the fallen adventurers.
 * It keeps its distance and strings together a combo of spells (three, four below half health);
 * every second combo it is <b>drained</b>: it sinks to one knee, its staff dim, and takes 1.4x damage.
 * <ul>
 * <li>soul bolts: the staff raised, then a fan of slow soul orbs (straight, dodge sideways)</li>
 * <li>sigils: circles under and around every player; a soul pillar bursts from each</li>
 * <li>nova: a ring on the ground round it fills, then a burst that throws back everyone inside</li>
 * <li>death ray: a thin red sight follows the target, locks, then a beam fires along it</li>
 * <li>summon: fallen adventurers rise around it (up to four at a time)</li>
 * <li>blink: crowded or cornered, it vanishes in smoke and steps out a distance away</li>
 * <li>below half health: it rises in a storm of light, calls more of the fallen, casts faster and
 * adds the <b>soul storm</b>: a spiral of orbs pouring out for a few seconds</li>
 * </ul>
 * No single hit takes more than a fifth of a player's health (see {@link BossEvents}). Health follows
 * the boss level and the players fighting it..
 */
public class Lich extends Monster implements ModBoss {
    public static final float SCALE = 2.4F;

    public enum State { CHASE, BOLTS_UP, SIGILS_UP, NOVA_UP, BEAM_AIM, BEAM, SUMMON, BLINK, STORM, DRAINED, RISE, CAST_END }

    enum Move { BOLTS, SIGILS, NOVA, BEAM, SUMMON, BLINK, STORM }

    private static final EntityDataAccessor<Integer> DATA_STATE = SynchedEntityData.defineId(Lich.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(Lich.class, EntityDataSerializers.INT);

    public static final int MAX_MINIONS = 4;
    public static final double ORB_SPEED = 0.45, NOVA_RADIUS = 7, BEAM_RANGE = 34;

    // ---- server state
    private State state = State.CHASE;
    private int stateTicks, stateLength;
    private int nextMove = 50;
    private final Deque<Move> combo = new ArrayDeque<>();
    /** Health, boss bar, targets, water, rewards: what every boss shares. */
    private final BossCore core = new BossCore(this, "lich", MobConfig::lichHealth, BossEvent.BossBarColor.BLUE, 0xB06AFF);
    private boolean relocating;
    private int combos;
    private float damageTakenMult = 1;
    private int crowded;
    private final List<Orb> orbs = new ArrayList<>();
    private final List<Sigil> sigils = new ArrayList<>();
    private Vec3 aimDir = Vec3.ZERO;
    private float stormAngle;
    /** Dev test counters. */
    public int orbsFired, sigilsBurst, novaHits, beamHits, summoned, blinks;

    // ---- client animation
    public int clientStateTicks;

    /** A soul orb flying straight on: hits the first player it touches, or a wall. */
    private static final class Orb {
        Vec3 pos;
        final Vec3 vel;
        int life;
        final float damage;

        Orb(Vec3 pos, Vec3 vel, int life, float damage) {
            this.pos = pos;
            this.vel = vel;
            this.life = life;
            this.damage = damage;
        }
    }

    /** A soul pillar about to burst from a marked circle. */
    private record Sigil(Vec3 at, int tick, float radius, float damage) {}

    public Lich(EntityType<? extends Lich> type, Level level) {
        super(type, level);
        this.xpReward = 200;
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(com.jo112ss.rpgmod.item.ModItems.get(com.jo112ss.rpgmod.item.WeaponType.STAFF,
                com.jo112ss.rpgmod.item.WeaponMaterial.NETHERITE)));
        this.setDropChance(EquipmentSlot.MAINHAND, 0);
        this.refreshDimensions(); // the scale attribute sets its size
        BossWater.avoidWater(this);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 800)
                .add(Attributes.FOLLOW_RANGE, 48)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.ARMOR, 4)
                .add(Attributes.ATTACK_DAMAGE, 6)
                .add(Attributes.MOVEMENT_SPEED, 0.24)
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

    // ------------------------------------------------------------------ level, state

    @Override
    public BossCore core() {
        return core;
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
        core.tick(sl, phase(), state == State.DRAINED ? BossCore.opening() : null);
        if (this.tickCount % 8 == 0) ServerFx.of("lich_aura", this.position()).follow(this).a(this.getBbWidth()).b(phase()).send(sl, 48);
        tickOrbs(sl);
        tickSigils(sl);
        // in the water mid-spell: drop it and get out first
        if (state != State.CHASE && state != State.BLINK && state != State.RISE && stateTicks > 10 && BossWater.inWater(this)) setState(State.CHASE, 0);
        if (phase() < 2 && this.getHealth() <= this.getMaxHealth() * 0.5F) startRise(sl);

        switch (state) {
            case CHASE -> chase(sl);
            case BOLTS_UP -> boltsUp(sl);
            case SIGILS_UP -> sigilsUp(sl);
            case NOVA_UP -> novaUp(sl);
            case BEAM_AIM -> beamAim(sl);
            case BEAM, CAST_END, BLINK -> {
                hold();
                if (stateTicks >= stateLength) {
                    if (relocating) { // a blink to dry ground is not a spell of the combo
                        relocating = false;
                        setState(State.CHASE, 0);
                        nextMove = Math.max(nextMove, 12);
                    } else {
                        moveDone();
                    }
                }
            }
            case SUMMON -> summon(sl);
            case STORM -> storm(sl);
            case DRAINED -> {
                hold();
                if (stateTicks % 12 == 0) sl.playSound(null, this.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 1.5F, 0.6F);
                if (stateTicks >= stateLength) {
                    setState(State.CHASE, 0);
                    nextMove = phase() >= 2 ? 20 + this.random.nextInt(12) : 30 + this.random.nextInt(16);
                }
            }
            case RISE -> rise(sl);
        }
    }

    private void hold() {
        this.getNavigation().stop();
        this.setDeltaMovement(this.getDeltaMovement().multiply(0.2, 1, 0.2));
    }

    private Vec3 eye() {
        return this.position().add(0, this.getBbHeight() * 0.8, 0);
    }

    private Vec3 staffTip() {
        return this.position().add(Vec3.directionFromRotation(0, this.yBodyRot + 30).scale(this.getBbWidth() * 0.7)).add(0, this.getBbHeight() * 0.95, 0);
    }

    private void faceTarget(LivingEntity t) {
        Vec3 d = t.position().subtract(this.position());
        this.setYRot((float) Math.toDegrees(Math.atan2(-d.x, d.z)));
        this.yBodyRot = this.yHeadRot = this.getYRot();
        this.getLookControl().setLookAt(t, 60, 60);
    }

    // ------------------------------------------------------------------ rhythm: combo -> drained

    private void chase(ServerLevel sl) {
        LivingEntity t = core.target(sl);
        if (t == null) {
            this.getNavigation().stop();
            return;
        }
        double dist = Math.sqrt(t.distanceToSqr(this));
        this.getLookControl().setLookAt(t, 20, 20);
        // out of the water / to the shore nearest someone out on it: it blinks there
        Vec3 dry = core.water.relocate(sl, this, t);
        if (dry != null) {
            blinkTo(sl, dry, t);
            relocating = true;
            return;
        }
        if (core.water.homingDue(t, phase())) core.homing.volley(sl, this, t, phase() >= 2 ? 5 : 3, 6, 0.6, 14);
        // keep a caster's distance: closer than 7 it backs off, further than 16 it comes on
        if (this.tickCount % 10 == 0) {
            if (dist < 7) {
                Vec3 away = this.position().subtract(t.position()).multiply(1, 0, 1).normalize().scale(6).add(this.position());
                this.getNavigation().moveTo(away.x, away.y, away.z, 1.1);
            } else if (dist > 16) {
                this.getNavigation().moveTo(t, 1.0);
            } else {
                this.getNavigation().stop();
            }
        }
        crowded = dist < 5 ? crowded + 1 : Math.max(0, crowded - 2);
        if (--nextMove > 0) return;
        if (dist > 36) return;
        if (combo.isEmpty()) buildCombo(sl);
        Move m = combo.poll();
        if (m != null) start(sl, m, t);
    }

    private int minions(ServerLevel sl) {
        return sl.getEntitiesOfClass(Mob.class, this.getBoundingBox().inflate(40), m -> m.isAlive() && m.getPersistentData().getBooleanOr(BossEvents.CHILD, false)
                && m.getPersistentData().getIntOr(MINION_OF, -1) == this.getId()).size();
    }

    /** Three spells (four below half health), no repeats; crowded, it opens with a nova or a blink. */
    private void buildCombo(ServerLevel sl) {
        boolean p2 = phase() >= 2;
        List<Move> pool = new ArrayList<>(p2
                ? List.of(Move.BOLTS, Move.BOLTS, Move.SIGILS, Move.SIGILS, Move.BEAM, Move.BEAM, Move.STORM, Move.STORM, Move.NOVA)
                : List.of(Move.BOLTS, Move.BOLTS, Move.SIGILS, Move.SIGILS, Move.BEAM, Move.NOVA));
        if (minions(sl) < (p2 ? 3 : 2)) pool.add(Move.SUMMON);
        int n = p2 ? 4 : 3;
        if (core.water.ranged(core.target(sl))) pool.removeIf(x -> x == Move.NOVA); // someone out on the water: no use
        if (crowded > 30) {
            combo.add(this.random.nextBoolean() ? Move.NOVA : Move.BLINK);
            pool.removeIf(x -> x == Move.NOVA);
            crowded = 0;
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
            case BOLTS -> startBolts(sl, t);
            case SIGILS -> startSigils(sl);
            case NOVA -> startNova(sl);
            case BEAM -> startBeam(sl, t);
            case SUMMON -> startSummon(sl);
            case BLINK -> blink(sl, t);
            case STORM -> startStorm(sl);
        }
    }

    private void moveDone() {
        if (!combo.isEmpty()) {
            setState(State.CHASE, 0);
            nextMove = phase() >= 2 ? 10 + this.random.nextInt(6) : 14 + this.random.nextInt(8);
        } else if (++combos % 2 == 0) {
            startDrained(); // drained after every second combo only
        } else {
            setState(State.CHASE, 0);
            nextMove = phase() >= 2 ? 16 + this.random.nextInt(8) : 24 + this.random.nextInt(10);
        }
    }

    private void startDrained() {
        int len = phase() >= 2 ? 40 : 50;
        setState(State.DRAINED, len);
        damageTakenMult = 1.4F;
        if (this.level() instanceof ServerLevel sl) {
            ServerFx.of("lich_drained", this.position()).follow(this).a(this.getBbWidth()).b(len).send(sl);
            sl.playSound(null, this.blockPosition(), SoundEvents.WITHER_SKELETON_HURT, SoundSource.HOSTILE, 2.0F, 0.5F);
        }
    }

    // ------------------------------------------------------------------ soul bolts

    private void startBolts(ServerLevel sl, LivingEntity t) {
        int up = phase() >= 2 ? 12 : 16;
        faceTarget(t);
        setState(State.BOLTS_UP, up);
        ServerFx.of("lich_cast", this.position()).follow(this).a(this.getBbWidth()).b(up).color(0x7FE8FF).send(sl);
        sl.playSound(null, this.blockPosition(), SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.HOSTILE, 2.0F, 0.6F);
    }

    private void boltsUp(ServerLevel sl) {
        hold();
        LivingEntity t = core.target(sl);
        if (t != null) faceTarget(t);
        if (stateTicks < stateLength) return;
        int n = phase() >= 2 ? 7 : 5;
        Vec3 from = staffTip();
        Vec3 aim = t != null ? t.position().add(0, t.getBbHeight() * 0.5, 0).subtract(from).normalize() : Vec3.directionFromRotation(0, this.getYRot());
        double spread = Math.toRadians(phase() >= 2 ? 12 : 15);
        for (int i = 0; i < n; i++) {
            double a = (i - (n - 1) / 2.0) * spread;
            Vec3 dir = rotateY(aim, a);
            fireOrb(sl, from, dir.scale(ORB_SPEED), 80, 6);
        }
        sl.playSound(null, this.blockPosition(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.HOSTILE, 2.0F, 0.8F);
        setState(State.CAST_END, 10);
    }

    private static Vec3 rotateY(Vec3 v, double a) {
        double c = Math.cos(a), s = Math.sin(a);
        return new Vec3(v.x * c - v.z * s, v.y, v.x * s + v.z * c);
    }

    private void fireOrb(ServerLevel sl, Vec3 from, Vec3 vel, int life, float damage) {
        orbs.add(new Orb(from, vel, life, damage));
        orbsFired++;
        ServerFx.of("lich_orb", from).vec(vel).a(life).color(phase() >= 2 ? 0xB06AFF : 0x7FE8FF).send(sl, 64);
    }

    private void tickOrbs(ServerLevel sl) {
        for (var it = orbs.iterator(); it.hasNext(); ) {
            Orb o = it.next();
            Vec3 next = o.pos.add(o.vel);
            var block = sl.clip(new ClipContext(o.pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            boolean wall = block.getType() != HitResult.Type.MISS;
            Vec3 end = wall ? block.getLocation() : next;
            ServerPlayer hit = null;
            for (ServerPlayer p : sl.getEntitiesOfClass(ServerPlayer.class, new AABB(o.pos, end).inflate(0.8), BossCore::valid)) {
                if (p.getBoundingBox().inflate(0.35).clip(o.pos, end).isPresent() || p.getBoundingBox().inflate(0.35).contains(end)) {
                    hit = p;
                    break;
                }
            }
            if (hit != null) {
                hit.hurtServer(sl, this.damageSources().indirectMagic(this, this), o.damage);
                core.participants.add(hit.getUUID());
                ServerFx.of("lich_orb_hit", end).color(0x7FE8FF).send(sl, 48);
                it.remove();
                continue;
            }
            o.pos = end;
            if (wall || --o.life <= 0) it.remove();
        }
    }

    // ------------------------------------------------------------------ sigils

    private void startSigils(ServerLevel sl) {
        int up = 16;
        setState(State.SIGILS_UP, up);
        ServerFx.of("lich_cast", this.position()).follow(this).a(this.getBbWidth()).b(up).color(0x9A6AFF).send(sl);
        sl.playSound(null, this.blockPosition(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 2.0F, 0.7F);
        int delay = phase() >= 2 ? 24 : 30;
        int extra = phase() >= 2 ? 4 : 2;
        for (ServerPlayer p : core.fighters(sl, 36)) {
            List<Vec3> spots = new ArrayList<>();
            spots.add(p.position().add(p.getDeltaMovement().multiply(6, 0, 6)));
            for (int i = 0; i < extra; i++) {
                double a = this.random.nextDouble() * Math.PI * 2, r = 2.5 + this.random.nextDouble() * 3.5;
                spots.add(p.position().add(Math.cos(a) * r, 0, Math.sin(a) * r));
            }
            for (int i = 0; i < spots.size(); i++) {
                Vec3 at = BossCore.groundBelow(sl, spots.get(i));
                int when = this.tickCount + up + delay + i * 3;
                sigils.add(new Sigil(at, when, 1.9F, 8));
                ServerFx.of("lich_sigil", at).a(1.9F).b(when - this.tickCount).color(0x9A6AFF).send(sl, 64);
            }
        }
    }

    private void sigilsUp(ServerLevel sl) {
        hold();
        if (stateTicks >= stateLength) {
            sl.playSound(null, this.blockPosition(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.HOSTILE, 2.0F, 0.6F);
            setState(State.CAST_END, 14);
        }
    }

    private void tickSigils(ServerLevel sl) {
        for (var it = sigils.iterator(); it.hasNext(); ) {
            Sigil s = it.next();
            if (this.tickCount < s.tick()) continue;
            it.remove();
            sigilsBurst++;
            for (ServerPlayer p : sl.players()) {
                if (!BossCore.valid(p)) continue;
                Vec3 v = p.position().subtract(s.at());
                if (Math.hypot(v.x, v.z) < s.radius() && v.y > -1 && v.y < 3) {
                    p.hurtServer(sl, this.damageSources().indirectMagic(this, this), s.damage());
                    p.setDeltaMovement(p.getDeltaMovement().add(0, 0.6, 0));
                    p.syncVelocity = true;
                    core.participants.add(p.getUUID());
                }
            }
            ServerFx.of("lich_pillar", s.at()).a(s.radius()).color(0x9A6AFF).send(sl, 64);
            sl.playSound(null, s.at().x, s.at().y, s.at().z, SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 1.5F, 0.6F);
        }
    }

    // ------------------------------------------------------------------ nova

    private void startNova(ServerLevel sl) {
        int up = phase() >= 2 ? 18 : 22;
        setState(State.NOVA_UP, up);
        ServerFx.of("rv_circle", this.position()).a(NOVA_RADIUS).b(up).color(0x7FE8FF).send(sl, 64);
        ServerFx.of("lich_cast", this.position()).follow(this).a(this.getBbWidth()).b(up).color(0x7FE8FF).send(sl);
        sl.playSound(null, this.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 2.0F, 0.5F);
    }

    private void novaUp(ServerLevel sl) {
        hold();
        if (stateTicks < stateLength) return;
        for (ServerPlayer p : sl.players()) {
            if (!BossCore.valid(p)) continue;
            Vec3 d = p.position().subtract(this.position());
            if (Math.hypot(d.x, d.z) <= NOVA_RADIUS && Math.abs(d.y) < 4) {
                p.hurtServer(sl, this.damageSources().indirectMagic(this, this), 9);
                Vec3 out = d.multiply(1, 0, 1).lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : d.multiply(1, 0, 1).normalize();
                p.setDeltaMovement(p.getDeltaMovement().add(out.scale(1.3)).add(0, 0.5, 0));
                p.syncVelocity = true;
                core.participants.add(p.getUUID());
                novaHits++;
            }
        }
        ServerFx.of("lich_nova", this.position()).a(NOVA_RADIUS).color(0x7FE8FF).send(sl, 64);
        sl.playSound(null, this.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.2F, 1.4F);
        sl.playSound(null, this.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 1.0F, 1.6F);
        setState(State.CAST_END, 12);
    }

    // ------------------------------------------------------------------ death ray

    private void startBeam(ServerLevel sl, LivingEntity t) {
        int aim = phase() >= 2 ? 26 : 32;
        faceTarget(t);
        setState(State.BEAM_AIM, aim);
        ServerFx.of("x_aim", eye()).follow(this).b(aim).color(0xFF3040).send(sl, 64);
        sl.playSound(null, this.blockPosition(), SoundEvents.GUARDIAN_ATTACK, SoundSource.HOSTILE, 2.0F, 0.6F);
    }

    private void beamAim(ServerLevel sl) {
        hold();
        LivingEntity t = core.target(sl);
        // the sight follows the target, then holds still for the last 10 ticks
        if (t != null && stateTicks < stateLength - 10) {
            faceTarget(t);
            aimDir = t.position().add(0, t.getBbHeight() * 0.5, 0).subtract(eye()).normalize();
            this.setXRot((float) -Math.toDegrees(Math.asin(Mth.clamp(aimDir.y, -1, 1))));
        }
        if (stateTicks == stateLength - 10) {
            ServerFx.of("lich_beam_lock", eye()).vec(aimDir.scale(BEAM_RANGE)).send(sl, 64);
            sl.playSound(null, this.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 2.0F, 1.6F);
        }
        if (stateTicks < stateLength) return;
        Vec3 from = eye();
        Vec3 to = from.add(aimDir.scale(BEAM_RANGE));
        var block = sl.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (block.getType() != HitResult.Type.MISS) to = block.getLocation();
        for (ServerPlayer p : sl.players()) {
            if (!BossCore.valid(p)) continue;
            if (p.getBoundingBox().inflate(0.7).clip(from, to).isPresent()) {
                p.hurtServer(sl, this.damageSources().indirectMagic(this, this), 10);
                core.participants.add(p.getUUID());
                beamHits++;
            }
        }
        ServerFx.of("lich_beam", from).vec(to.subtract(from)).color(0xFF4060).send(sl, 64);
        sl.playSound(null, this.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 1.5F, 0.8F);
        setState(State.BEAM, 14);
    }

    // ------------------------------------------------------------------ summon

    public static final String MINION_OF = "rpgmod_lich_minion";

    private void startSummon(ServerLevel sl) {
        setState(State.SUMMON, 30);
        ServerFx.of("lich_summon", this.position()).follow(this).a(this.getBbWidth()).b(30).send(sl, 64);
        sl.playSound(null, this.blockPosition(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 2.5F, 0.5F);
    }

    private void summon(ServerLevel sl) {
        hold();
        if (stateTicks < stateLength) return;
        raiseDead(sl, phase() >= 2 ? 3 : 2);
        setState(State.CAST_END, 12);
    }

    private void raiseDead(ServerLevel sl, int n) {
        n = Math.min(n, MAX_MINIONS - minions(sl));
        Revenant.Role[] roles = {Revenant.Role.WARRIOR, Revenant.Role.ARCHER, Revenant.Role.MAGE, Revenant.Role.THIEF};
        for (int i = 0; i < n; i++) {
            double a = this.random.nextDouble() * Math.PI * 2, r = 3 + this.random.nextDouble() * 3;
            Vec3 at = BossCore.groundBelow(sl, this.position().add(Math.cos(a) * r, 1, Math.sin(a) * r));
            if (!sl.getBlockState(BlockPos.containing(at)).isAir() || !sl.getBlockState(BlockPos.containing(at).above()).isAir()) continue;
            Revenant m = ModRegistries.REVENANT.get().create(sl, EntitySpawnReason.MOB_SUMMONED);
            if (m == null) continue;
            m.snapTo(at.x, at.y, at.z, this.random.nextFloat() * 360, 0);
            m.setRole(roles[this.random.nextInt(roles.length)]);
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
            m.setTarget(core.target(sl));
            summoned++;
            ServerFx.of("dg_summon", m.position()).a(m.getBbWidth()).color(0x7FE8FF).send(sl, 64);
        }
        sl.playSound(null, this.blockPosition(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.HOSTILE, 2.0F, 0.5F);
    }

    // ------------------------------------------------------------------ blink

    /** Vanishes and steps out at {@code at} (dry ground). */
    private void blinkTo(ServerLevel sl, Vec3 at, LivingEntity t) {
        Vec3 from = this.position();
        ServerFx.of("lich_blink", from).a(this.getBbWidth()).b(this.getBbHeight()).send(sl, 64);
        ServerFx.of("lich_blink", at).a(this.getBbWidth()).b(this.getBbHeight()).send(sl, 64);
        sl.playSound(null, BlockPos.containing(from), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 2.0F, 0.5F);
        this.teleportTo(at.x, at.y, at.z);
        sl.playSound(null, this.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 2.0F, 0.5F);
        this.getNavigation().stop();
        if (t != null) faceTarget(t);
        setState(State.BLINK, 8);
    }

    private void blink(ServerLevel sl, LivingEntity t) {
        Vec3 from = this.position();
        for (int tries = 0; tries < 20; tries++) {
            double a = this.random.nextDouble() * Math.PI * 2, r = 10 + this.random.nextDouble() * 4;
            Vec3 at = BossCore.groundBelow(sl, t.position().add(Math.cos(a) * r, 2, Math.sin(a) * r));
            this.setPos(at);
            if (sl.noCollision(this) && !sl.containsAnyLiquid(this.getBoundingBox())
                    && !sl.getBlockState(BlockPos.containing(at).below()).getCollisionShape(sl, BlockPos.containing(at).below()).isEmpty()) {
                ServerFx.of("lich_blink", from).a(this.getBbWidth()).b(this.getBbHeight()).send(sl, 64);
                ServerFx.of("lich_blink", at).a(this.getBbWidth()).b(this.getBbHeight()).send(sl, 64);
                sl.playSound(null, BlockPos.containing(from), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 2.0F, 0.5F);
                sl.playSound(null, this.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 2.0F, 0.5F);
                blinks++;
                this.teleportTo(at.x, at.y, at.z);
                faceTarget(t);
                setState(State.BLINK, 8);
                return;
            }
        }
        this.setPos(from);
        setState(State.BLINK, 2);
    }

    // ------------------------------------------------------------------ soul storm (below half health)

    private void startStorm(ServerLevel sl) {
        setState(State.STORM, 76);
        stormAngle = this.random.nextFloat() * 360;
        ServerFx.of("lich_cast", this.position()).follow(this).a(this.getBbWidth()).b(16).color(0xB06AFF).send(sl);
        ServerFx.of("mob_warn", this.position()).follow(this).a(this.getBbWidth() / 2 + 1).b(16).color(0xB06AFF).send(sl);
        sl.playSound(null, this.blockPosition(), SoundEvents.WITHER_AMBIENT, SoundSource.HOSTILE, 2.0F, 0.7F);
    }

    private void storm(ServerLevel sl) {
        hold();
        if (stateTicks > 16 && stateTicks % 4 == 0) {
            // three arms of a turning spiral
            Vec3 from = this.position().add(0, this.getBbHeight() * 0.55, 0);
            for (int k = 0; k < 3; k++) {
                double a = Math.toRadians(stormAngle + k * 120);
                fireOrb(sl, from, new Vec3(Math.cos(a), 0, Math.sin(a)).scale(0.32), 70, 5);
            }
            stormAngle += 17;
            if (stateTicks % 12 == 0) sl.playSound(null, this.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 2.0F, 0.9F);
        }
        if (stateTicks >= stateLength) moveDone();
    }

    // ------------------------------------------------------------------ rise (half health)

    private void startRise(ServerLevel sl) {
        this.entityData.set(DATA_PHASE, 2);
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.28);
        combo.clear();
        sigils.clear();
        setState(State.RISE, 40);
        core.enraged();
        ServerFx.of("lich_rise", this.position()).follow(this).a(this.getBbWidth()).b(40).send(sl, 64);
        sl.playSound(null, this.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.5F, 1.2F);
        for (ServerPlayer p : core.barPlayers()) p.sendSystemMessage(Component.translatable("rpgmod.boss.lich.enraged").withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }

    private void rise(ServerLevel sl) {
        hold();
        if (stateTicks == 20) raiseDead(sl, 3);
        if (stateTicks >= stateLength) {
            setState(State.CHASE, 0);
            nextMove = 20;
        }
    }

    // ------------------------------------------------------------------ damage taken

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        core.hurtBy(source);
        if (state == State.DRAINED || state == State.BLINK) damage *= state == State.DRAINED ? damageTakenMult : 1;
        if (state == State.RISE) damage *= 0.3F; // wreathed in light while it rises
        return super.hurtServer(level, source, damage);
    }

    public float openingMultiplier() {
        return state == State.DRAINED ? damageTakenMult : state == State.RISE ? 0.3F : 1;
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
        return 0.55F;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getAmbientSound() {
        return SoundEvents.WITHER_SKELETON_AMBIENT;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.WITHER_SKELETON_HURT;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getDeathSound() {
        return SoundEvents.WITHER_DEATH;
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource source) {
        return false;
    }

    // ------------------------------------------------------------------ boss bar

    private void dismissMinions(ServerLevel sl) {
        for (Mob m : sl.getEntitiesOfClass(Mob.class, this.getBoundingBox().inflate(64), m -> m.getPersistentData().getIntOr(MINION_OF, -1) == this.getId())) {
            ServerFx.of("lich_blink", m.position()).a(m.getBbWidth()).b(m.getBbHeight()).send(sl, 48);
            m.discard();
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        core.removeBar();
        if (this.level() instanceof ServerLevel sl) dismissMinions(sl);
        super.remove(reason);
    }

    // ------------------------------------------------------------------ leaving and dying

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!(this.level() instanceof ServerLevel sl)) return;
        dismissMinions(sl);
        com.jo112ss.rpgmod.job.SkillTasks.later(10, () -> dismissMinions(sl));
        com.jo112ss.rpgmod.job.SkillTasks.later(40, () -> dismissMinions(sl));
        core.died(sl);
        ServerFx.of("lich_death", this.position()).a(this.getBbWidth()).b(this.getBbHeight()).send(sl, 96);
    }

    // ------------------------------------------------------------------ save

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.putInt("LichPhase", phase());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        this.entityData.set(DATA_PHASE, in.getIntOr("LichPhase", 1));
    }

    // ------------------------------------------------------------------ dev test

    /** Autotest: start a move right away (bolts, sigils, nova, beam, summon, blink, storm, drained, rise, combo). */
    public void testMove(String move) {
        if (!(this.level() instanceof ServerLevel sl)) return;
        LivingEntity t = core.target(sl);
        if (t == null) return;
        setState(State.CHASE, 0);
        combo.clear();
        switch (move) {
            case "drained" -> startDrained();
            case "combo" -> nextMove = 0;
            case "rise" -> startRise(sl);
            default -> start(sl, Move.valueOf(move.toUpperCase(java.util.Locale.ROOT)), t);
        }
    }
}
