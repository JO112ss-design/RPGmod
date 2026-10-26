package com.jo112ss.rpgmod.client.job;

import com.jo112ss.rpgmod.RpgMod;
import com.jo112ss.rpgmod.item.ModItems;
import com.jo112ss.rpgmod.item.WeaponMaterial;
import com.jo112ss.rpgmod.item.WeaponType;
import com.jo112ss.rpgmod.job.SkillProp;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Draws the skill props: the pirate's cannon, powder keg and flag (block-style models), the lancer's thrown spear (the item). */
public class SkillPropRenderer extends EntityRenderer<SkillProp, SkillPropRenderer.State> {
    private static final Identifier CANNON_TEX = Identifier.fromNamespaceAndPath(RpgMod.MODID, "textures/entity/prop_cannon.png");
    private static final Identifier KEG_TEX = Identifier.fromNamespaceAndPath(RpgMod.MODID, "textures/entity/prop_keg.png");
    private static final Identifier FLAG_TEX = Identifier.fromNamespaceAndPath(RpgMod.MODID, "textures/entity/prop_flag.png");

    private final PropModel cannon = new PropModel(cannonLayer().bakeRoot());
    private final PropModel keg = new PropModel(kegLayer().bakeRoot());
    private final PropModel flag = new PropModel(flagLayer().bakeRoot());
    private final ItemModelResolver items;

    public SkillPropRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.items = context.getItemModelResolver();
        this.shadowRadius = 0.5F;
    }

    public static final class State extends EntityRenderState {
        int kind;
        float yaw, pitch;
        final ItemStackRenderState item = new ItemStackRenderState();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(SkillProp entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.kind = entity.kind();
        state.yaw = entity.getViewYRot(partialTicks);
        state.pitch = entity.getViewXRot(partialTicks);
        if (state.kind == SkillProp.SPEAR) {
            WeaponMaterial[] mats = WeaponMaterial.values();
            ItemStack spear = new ItemStack(ModItems.get(WeaponType.SPEAR, mats[Math.floorMod(entity.variant(), mats.length)]));
            items.updateForNonLiving(state.item, spear, ItemDisplayContext.FIXED, entity);
        } else if (state.kind == SkillProp.COIN) {
            items.updateForNonLiving(state.item, new ItemStack(net.minecraft.world.item.Items.GOLD_NUGGET), ItemDisplayContext.GROUND, entity);
        } else if (state.kind == SkillProp.CHEST || state.kind == SkillProp.ANCHOR) {
            ItemStack block = new ItemStack(state.kind == SkillProp.CHEST ? net.minecraft.world.item.Items.CHEST : net.minecraft.world.item.Items.ANVIL);
            items.updateForNonLiving(state.item, block, ItemDisplayContext.FIXED, entity);
        } else {
            state.item.clear();
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        if (state.kind == SkillProp.SPEAR) {
            // the flat spear icon points up and to the right: turn it along the flight
            poseStack.translate(0, 0.4F, 0);
            poseStack.rotateDegrees(Axis.YP, -(state.yaw + 90));
            poseStack.rotateDegrees(Axis.ZP, -state.pitch - 45);
            poseStack.scale(1.8F, 1.8F, 1.8F);
            state.item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        } else if (state.kind == SkillProp.COIN) {
            // a treasure-rain coin lying about: spinning and bobbing like a pickup, a bit larger
            poseStack.translate(0, 0.25F + Mth.sin(state.ageInTicks * 0.15F) * 0.08F, 0);
            poseStack.rotateDegrees(Axis.YP, state.ageInTicks * 9);
            poseStack.scale(1.6F, 1.6F, 1.6F);
            state.item.submit(poseStack, collector, net.minecraft.util.LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, state.outlineColor);
        } else if (state.kind == SkillProp.CHEST || state.kind == SkillProp.ANCHOR) {
            // a block-sized chest (the mimic, its lid snapping) or the captain's anchor (an anvil)
            poseStack.translate(0, 0.45F, 0);
            poseStack.rotateDegrees(Axis.YP, 180.0F - state.yaw);
            if (state.kind == SkillProp.CHEST) poseStack.rotateDegrees(Axis.XP, Math.max(0, Mth.sin(state.ageInTicks * 0.5F)) * 8);
            poseStack.scale(1.7F, 1.7F, 1.7F);
            state.item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        } else {
            PropModel model = state.kind == SkillProp.KEG ? keg : state.kind == SkillProp.FLAG ? flag : cannon;
            Identifier tex = state.kind == SkillProp.KEG ? KEG_TEX : state.kind == SkillProp.FLAG ? FLAG_TEX : CANNON_TEX;
            poseStack.rotateDegrees(Axis.YP, 180.0F - state.yaw);
            poseStack.scale(-1.0F, -1.0F, 1.0F);
            poseStack.translate(0.0F, -1.501F, 0.0F);
            collector.submitModel(model, state, poseStack, tex, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        }
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }

    /** One model class for all three: the cannon's barrel follows the pitch, the flag's cloth waves. */
    static final class PropModel extends EntityModel<State> {
        private final ModelPart moving;

        PropModel(ModelPart root) {
            super(root);
            ModelPart m = null;
            try {
                m = root.getChild("moving");
            } catch (RuntimeException ignored) {
                // the keg has no moving part
            }
            this.moving = m;
        }

        @Override
        public void setupAnim(State s) {
            super.setupAnim(s);
            if (moving == null) return;
            if (s.kind == SkillProp.CANNON) {
                moving.xRot = s.pitch * Mth.DEG_TO_RAD;
            } else if (s.kind == SkillProp.FLAG) {
                moving.yRot = Mth.sin(s.ageInTicks * 0.18F) * 0.22F;
            }
        }
    }

    static LayerDefinition cannonLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("carriage", CubeListBuilder.create().texOffs(0, 0).addBox(-6.0F, 17.0F, -7.0F, 12.0F, 5.0F, 14.0F), PartPose.ZERO);
        root.addOrReplaceChild("wheels", CubeListBuilder.create()
                .texOffs(0, 19).addBox(-8.0F, 15.0F, -4.5F, 2.0F, 9.0F, 9.0F)
                .texOffs(0, 19).addBox(6.0F, 15.0F, -4.5F, 2.0F, 9.0F, 9.0F), PartPose.ZERO);
        root.addOrReplaceChild("moving", CubeListBuilder.create()
                .texOffs(0, 37).addBox(-3.0F, -3.0F, -11.0F, 6.0F, 6.0F, 17.0F)
                .texOffs(24, 19).addBox(-3.5F, -3.5F, -12.0F, 7.0F, 7.0F, 2.0F)
                .texOffs(46, 37).addBox(-1.5F, -1.5F, 6.0F, 3.0F, 3.0F, 2.0F), PartPose.offset(0.0F, 15.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    static LayerDefinition kegLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-5.0F, 14.0F, -5.0F, 10.0F, 10.0F, 10.0F), PartPose.ZERO);
        root.addOrReplaceChild("hoops", CubeListBuilder.create()
                .texOffs(0, 20).addBox(-5.5F, 15.5F, -5.5F, 11.0F, 1.0F, 11.0F)
                .texOffs(0, 20).addBox(-5.5F, 21.5F, -5.5F, 11.0F, 1.0F, 11.0F), PartPose.ZERO);
        root.addOrReplaceChild("fuse", CubeListBuilder.create().texOffs(44, 0).addBox(-0.5F, 11.0F, -0.5F, 1.0F, 3.0F, 1.0F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 32);
    }

    static LayerDefinition flagLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("pole", CubeListBuilder.create().texOffs(0, 0).addBox(-1.0F, -24.0F, -1.0F, 2.0F, 48.0F, 2.0F), PartPose.ZERO);
        root.addOrReplaceChild("base", CubeListBuilder.create().texOffs(10, 20).addBox(-3.0F, 22.0F, -3.0F, 6.0F, 2.0F, 6.0F), PartPose.ZERO);
        root.addOrReplaceChild("moving", CubeListBuilder.create().texOffs(10, 0).addBox(0.0F, 0.0F, -0.5F, 20.0F, 13.0F, 1.0F),
                PartPose.offset(1.0F, -22.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }
}
