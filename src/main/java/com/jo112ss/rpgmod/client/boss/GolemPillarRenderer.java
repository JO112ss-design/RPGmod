package com.jo112ss.rpgmod.client.boss;

import com.jo112ss.rpgmod.RpgMod;
import com.jo112ss.rpgmod.mob.boss.GolemPillar;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/** The Golem's armour pillar: a stone column with a glowing crystal on top that slowly turns. */
public class GolemPillarRenderer extends MobRenderer<GolemPillar, LivingEntityRenderState, GolemPillarRenderer.PillarModel> {
    public static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(RpgMod.MODID, "textures/entity/golem_pillar.png");
    public static final Identifier GLOW = Identifier.fromNamespaceAndPath(RpgMod.MODID, "textures/entity/golem_pillar_glow.png");

    public GolemPillarRenderer(EntityRendererProvider.Context context) {
        super(context, new PillarModel(createLayer().bakeRoot()), 0.6F);
        this.addLayer(new Glow(this));
    }

    @Override
    public Identifier getTextureLocation(LivingEntityRenderState state) {
        return TEXTURE;
    }

    @Override
    public LivingEntityRenderState createRenderState() {
        return new LivingEntityRenderState();
    }

    @Override
    protected float getFlipDegrees() {
        return 0; // it does not topple over when broken
    }

    static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("base", CubeListBuilder.create().texOffs(0, 0).addBox(-6.0F, 18.0F, -6.0F, 12.0F, 6.0F, 12.0F), PartPose.ZERO);
        root.addOrReplaceChild("column", CubeListBuilder.create().texOffs(0, 18).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 26.0F, 8.0F), PartPose.ZERO);
        root.addOrReplaceChild("crystal", CubeListBuilder.create().texOffs(32, 18).addBox(-3.0F, -5.0F, -3.0F, 6.0F, 10.0F, 6.0F), PartPose.offset(0, -14.0F, 0));
        return LayerDefinition.create(mesh, 64, 64);
    }

    public static final class PillarModel extends EntityModel<LivingEntityRenderState> {
        private final ModelPart crystal;

        PillarModel(ModelPart root) {
            super(root);
            this.crystal = root.getChild("crystal");
        }

        @Override
        public void setupAnim(LivingEntityRenderState s) {
            super.setupAnim(s);
            crystal.yRot = s.ageInTicks * 0.05F;
            crystal.y = -14.0F + Mth.sin(s.ageInTicks * 0.12F) * 0.8F;
        }
    }

    static final class Glow extends RenderLayer<LivingEntityRenderState, PillarModel> {
        Glow(RenderLayerParent<LivingEntityRenderState, PillarModel> parent) {
            super(parent);
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, LivingEntityRenderState state, float yRot, float xRot) {
            collector.order(1).submitModel(getParentModel(), state, poseStack, RenderTypes.eyes(GLOW), light, OverlayTexture.NO_OVERLAY, -1, null, state.outlineColor);
        }
    }
}
