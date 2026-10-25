package com.jo112ss.rpgmod.job;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Something a skill puts into the world for a while, only to be seen: the pirate's cannon, powder
 * keg and flag, the lancer's thrown spear. What it does is run by the skill on the server; this
 * entity only carries the look (kind, facing, the cannon barrel's pitch) to the clients. A keg falls
 * and rolls on its own (on the server; clients follow every tick). Never saved; gone after its life.
 */
public class SkillProp extends Entity {
    public static final int CANNON = 0, KEG = 1, FLAG = 2, SPEAR = 3;
    private static final EntityDataAccessor<Integer> DATA_KIND = SynchedEntityData.defineId(SkillProp.class, EntityDataSerializers.INT);
    /** Spear: the material index of the thrown spear. */
    private static final EntityDataAccessor<Integer> DATA_VARIANT = SynchedEntityData.defineId(SkillProp.class, EntityDataSerializers.INT);
    /** Ticks left before it is removed (server). */
    private int life = 200;

    public SkillProp(EntityType<? extends SkillProp> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static SkillProp create(ServerLevel level, int kind, Vec3 at, float yaw, int life) {
        SkillProp p = new SkillProp(com.jo112ss.rpgmod.registry.ModRegistries.SKILL_PROP.get(), level);
        p.setKind(kind);
        p.snapTo(at.x, at.y, at.z, yaw, 0);
        p.life = life;
        level.addFreshEntity(p);
        return p;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(DATA_KIND, CANNON);
        b.define(DATA_VARIANT, 0);
    }

    public int kind() {
        return this.entityData.get(DATA_KIND);
    }

    public void setKind(int kind) {
        this.entityData.set(DATA_KIND, kind);
        this.noPhysics = kind != KEG;
    }

    public int variant() {
        return this.entityData.get(DATA_VARIANT);
    }

    public void setVariant(int v) {
        this.entityData.set(DATA_VARIANT, v);
    }

    public void setLife(int ticks) {
        this.life = ticks;
    }

    /** Points the cannon barrel (or the spear) at a spot. */
    public void aimAt(Vec3 target) {
        Vec3 d = target.subtract(this.position().add(0, 0.6, 0));
        double flat = Math.sqrt(d.x * d.x + d.z * d.z);
        this.setYRot((float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90));
        this.setXRot((float) -Math.toDegrees(Math.atan2(d.y, flat)));
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_KIND.equals(key)) this.noPhysics = kind() != KEG;
    }

    @Override
    public void tick() {
        super.tick();
        if (kind() == KEG && !this.level().isClientSide()) { // clients may not move it: they follow the server's updates
            Vec3 v = this.getDeltaMovement();
            if (!this.onGround()) v = v.add(0, -0.05, 0);
            this.move(MoverType.SELF, v);
            v = this.getDeltaMovement().scale(0.98);
            if (this.onGround()) v = v.multiply(0.7, 0, 0.7);
            this.setDeltaMovement(v);
        }
        if (!this.level().isClientSide() && --life <= 0) this.discard();
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
    }
}
