package com.jo112ss.rpgmod.client.boss;

import com.jo112ss.rpgmod.mob.boss.Golem;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * The Golem: an iron golem's shape, posed by what it is doing - arms up for a slam, a boulder held
 * overhead, a leg raised for the stomp, an arm drawn back for the sweep, kneeling while overheated.
 */
public class GolemModel extends EntityModel<GolemRenderState> {
    final ModelPart head, body, rightArm, leftArm, rightLeg, leftLeg;

    public GolemModel(ModelPart root) {
        super(root);
        this.head = root.getChild("head");
        this.body = root.getChild("body");
        this.rightArm = root.getChild("right_arm");
        this.leftArm = root.getChild("left_arm");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
    }

    /** Same shapes and texture layout as the iron golem. */
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -12.0F, -5.5F, 8.0F, 10.0F, 8.0F).texOffs(24, 0).addBox(-1.0F, -5.0F, -7.5F, 2.0F, 4.0F, 2.0F),
                PartPose.offset(0.0F, -7.0F, -2.0F));
        root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 40).addBox(-9.0F, -2.0F, -6.0F, 18.0F, 12.0F, 11.0F)
                        .texOffs(0, 70).addBox(-4.5F, 10.0F, -3.0F, 9.0F, 5.0F, 6.0F, new CubeDeformation(0.5F)),
                PartPose.offset(0.0F, -7.0F, 0.0F));
        root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(60, 21).addBox(-13.0F, -2.5F, -3.0F, 4.0F, 30.0F, 6.0F), PartPose.offset(0.0F, -7.0F, 0.0F));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(60, 58).addBox(9.0F, -2.5F, -3.0F, 4.0F, 30.0F, 6.0F), PartPose.offset(0.0F, -7.0F, 0.0F));
        root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(37, 0).addBox(-3.5F, -3.0F, -3.0F, 6.0F, 16.0F, 5.0F), PartPose.offset(-4.0F, 11.0F, 0.0F));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(60, 0).mirror().addBox(-3.5F, -3.0F, -3.0F, 6.0F, 16.0F, 5.0F), PartPose.offset(5.0F, 11.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 128);
    }

    static Golem.State mode(GolemRenderState s) {
        return Golem.State.values()[Mth.clamp(s.mode, 0, Golem.State.values().length - 1)];
    }

    private static float ease(float k) {
        k = Mth.clamp(k, 0, 1);
        return k * k * (3 - 2 * k);
    }

    @Override
    public void setupAnim(GolemRenderState s) {
        super.setupAnim(s);
        float pos = s.walkAnimationPos, spd = s.walkAnimationSpeed;
        float t = s.modeTicks;
        // walking (and the plain punch) like an iron golem
        head.yRot = s.yRot * Mth.DEG_TO_RAD;
        head.xRot = s.xRot * Mth.DEG_TO_RAD;
        rightLeg.xRot = -1.5F * Mth.triangleWave(pos, 13.0F) * spd;
        leftLeg.xRot = 1.5F * Mth.triangleWave(pos, 13.0F) * spd;
        rightArm.xRot = (-0.2F + 1.5F * Mth.triangleWave(pos, 13.0F)) * spd;
        leftArm.xRot = (-0.2F - 1.5F * Mth.triangleWave(pos, 13.0F)) * spd;
        rightArm.zRot = leftArm.zRot = 0;
        rightArm.yRot = leftArm.yRot = 0;
        if (s.attackAnim > 0) {
            rightArm.xRot = -2.0F + 1.5F * Mth.triangleWave(s.attackAnim, 10.0F);
            leftArm.xRot = -2.0F + 1.5F * Mth.triangleWave(s.attackAnim, 10.0F);
        }
        float shake = Mth.sin(t * 2.7F) * 0.04F;
        switch (mode(s)) {
            case SLAM_UP, LEAP -> { // both arms up, trembling
                float k = ease(t / 12F);
                armsBoth(-3.0F * k + shake);
                head.xRot = -0.35F * k;
                if (mode(s) == Golem.State.LEAP) {
                    rightLeg.xRot = -0.7F;
                    leftLeg.xRot = -0.4F;
                }
            }
            case SLAM -> { // fists on the ground
                float k = ease(t / 3F);
                armsBoth(Mth.lerp(k, -3.0F, -0.75F));
                head.xRot = 0.3F;
            }
            case QUAKE -> {
                float c = t % 14;
                armsBoth(c < 8 ? -3.0F * ease(c / 8F) : Mth.lerp(ease((c - 8) / 2F), -3.0F, -0.75F));
            }
            case THROW_UP -> { // a boulder held overhead
                float k = ease(t / 10F);
                armsBoth(-3.05F * k);
                rightArm.zRot = -0.25F * k;
                leftArm.zRot = 0.25F * k;
                head.xRot = -0.2F * k;
            }
            case THROW -> {
                float k = ease(t / 4F);
                armsBoth(Mth.lerp(k, -3.05F, -1.2F));
            }
            case STOMP -> { // one leg up, then down
                float k = ease(t / 12F);
                boolean down = t > 13;
                rightLeg.xRot = down ? 0 : -1.1F * k;
                leftLeg.xRot = 0;
                rightArm.zRot = 0.5F * k;
                leftArm.zRot = -0.5F * k;
                armsBoth(-0.3F);
            }
            case CHARGE_AIM -> { // dug in, arms back
                float k = ease(t / 10F);
                armsBoth(0.7F * k + shake);
                head.xRot = 0.25F * k;
            }
            case CHARGE -> {
                float run = Mth.sin(t * 1.3F);
                rightLeg.xRot = -1.1F * run;
                leftLeg.xRot = 1.1F * run;
                armsBoth(0.8F);
                head.xRot = 0.3F;
            }
            case SWEEP_UP -> { // the right arm drawn back to the side
                float k = ease(t / 10F);
                rightArm.xRot = -1.6F * k;
                rightArm.yRot = 1.2F * k;
                rightArm.zRot = 0.6F * k;
                leftArm.xRot = -0.4F * k;
            }
            case SWEEP -> {
                float k = ease(t / 3F);
                rightArm.xRot = -1.6F;
                rightArm.yRot = Mth.lerp(k, 1.2F, -1.3F);
                rightArm.zRot = 0.6F;
            }
            case ROAR, ARMOUR -> { // head back, arms spread
                float k = ease(t / 8F);
                head.xRot = -0.75F * k;
                rightArm.xRot = leftArm.xRot = -0.9F * k;
                rightArm.zRot = 1.0F * k;
                leftArm.zRot = -1.0F * k;
            }
            case OVERHEAT -> { // slumped, arms hanging, head down
                float k = ease(t / 8F);
                float breath = Mth.sin(t * 0.25F) * 0.05F;
                head.xRot = 0.75F * k;
                armsBoth(0.25F * k + breath);
                rightArm.zRot = 0.15F * k;
                leftArm.zRot = -0.15F * k;
                rightLeg.xRot = -0.9F * k;
                leftLeg.xRot = 0.3F * k;
            }
            case STUN -> { // swaying, head lolling
                head.yRot += Mth.sin(t * 0.35F) * 0.4F;
                head.xRot = 0.3F;
                armsBoth(0.15F);
                rightArm.zRot = 0.2F + Mth.sin(t * 0.3F) * 0.08F;
                leftArm.zRot = -0.2F - Mth.sin(t * 0.3F) * 0.08F;
            }
            case LEAVE -> {
                armsBoth(0.1F);
                head.xRot = 0.6F;
            }
            default -> { }
        }
    }

    private void armsBoth(float x) {
        rightArm.xRot = x;
        leftArm.xRot = x;
    }
}
