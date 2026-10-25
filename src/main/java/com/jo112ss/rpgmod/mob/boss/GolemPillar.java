package com.jo112ss.rpgmod.mob.boss;

import com.jo112ss.rpgmod.job.fx.ServerFx;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A glowing stone pillar of the Golem's armour: it stands still, does nothing, and while any of them
 * stands the Golem takes half damage. Health grows with the boss level like a monster's (15 x the level health multiplier: about three hits from a player of that level). Gone with the
 * Golem (and when the world is reloaded, as it cannot find its Golem again).
 */
public class GolemPillar extends Monster {
    private static final EntityDataAccessor<Integer> DATA_OWNER = SynchedEntityData.defineId(GolemPillar.class, EntityDataSerializers.INT);

    public GolemPillar(EntityType<? extends GolemPillar> type, Level level) {
        super(type, level);
        this.xpReward = 5;
        this.setNoAi(true);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 40)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.ARMOR, 0)
                .add(Attributes.MOVEMENT_SPEED, 0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(DATA_OWNER, -1);
    }

    public void setOwner(int id) {
        this.entityData.set(DATA_OWNER, id);
    }

    public int owner() {
        return this.entityData.get(DATA_OWNER);
    }

    /** Health for the boss level. */
    public void setup(int level) {
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(15 * com.jo112ss.rpgmod.mob.core.MobLevelMath.healthMultiplier(com.jo112ss.rpgmod.mob.MobConfig.rules(), level));
        this.setHealth(this.getMaxHealth());
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level() instanceof ServerLevel sl && this.tickCount % 20 == 0) {
            // no Golem any more (it left, died, or the world was reloaded): crumble
            if (!(sl.getEntity(owner()) instanceof Golem g) || !g.isAlive()) crumble(sl);
        }
    }

    private void crumble(ServerLevel sl) {
        ServerFx.of("golem_pillar_break", this.position()).a(this.getBbHeight()).send(sl);
        this.discard();
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (this.level() instanceof ServerLevel sl) {
            ServerFx.of("golem_pillar_break", this.position()).a(this.getBbHeight()).send(sl);
            sl.playSound(null, this.blockPosition(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.HOSTILE, 2.0F, 0.6F);
            sl.playSound(null, this.blockPosition(), SoundEvents.STONE_BREAK, SoundSource.HOSTILE, 2.0F, 0.6F);
        }
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.AMETHYST_BLOCK_HIT;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getDeathSound() {
        return SoundEvents.STONE_BREAK;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return false;
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource source) {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
    }
}
