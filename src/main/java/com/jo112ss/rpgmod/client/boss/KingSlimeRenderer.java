package com.jo112ss.rpgmod.client.boss;

import com.jo112ss.rpgmod.RpgMod;
import com.jo112ss.rpgmod.mob.boss.KingSlime;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.monster.slime.SlimeModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.AbstractCubeMobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * The King Slime: a big blue slime with a gold crown. Its body squashes while it winds up a slam,
 * flattens while stunned, puffs up before spitting, and sinks away at dawn.
 */
public class KingSlimeRenderer extends AbstractCubeMobRenderer<KingSlime, KingSlimeRenderState, SlimeModel> {
    public static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(RpgMod.MODID, "textures/entity/king_slime.png");
    public static final Identifier CROWN = Identifier.fromNamespaceAndPath(RpgMod.MODID, "textures/entity/king_slime_crown.png");

    public KingSlimeRenderer(EntityRendererProvider.Context context) {
        super(context, new SlimeModel(context.bakeLayer(ModelLayers.SLIME)));
        this.addLayer(new Outer(this, new SlimeModel(context.bakeLayer(ModelLayers.SLIME_OUTER))));
        this.addLayer(new Crown(this));
    }

    @Override
    public Identifier getTextureLocation(KingSlimeRenderState state) {
        return TEXTURE;
    }

    @Override
    public KingSlimeRenderState createRenderState() {
        return new KingSlimeRenderState();
    }

    @Override
    public void extractRenderState(KingSlime entity, KingSlimeRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.mode = entity.state().ordinal();
        state.modeTicks = entity.clientStateTicks + partialTicks;
        state.phase = entity.phase();
    }

    @Override
    protected void scale(KingSlimeRenderState state, PoseStack poseStack) {
        this.downscaleSlightly(poseStack);
        super.scale(state, poseStack);
        float t = state.modeTicks, x = 1, z = 1, y = 1, xz = 1;
        KingSlime.State mode = KingSlime.State.values()[Mth.clamp(state.mode, 0, KingSlime.State.values().length - 1)];
        switch (mode) {
            case WINDUP -> { // crouch, trembling more and more
                float k = Math.min(1, t / 14F);
                float shake = Mth.sin(t * 2.6F) * 0.035F * k;
                y = 1 - 0.34F * k + shake;
                xz = 1 + 0.2F * k - shake;
            }
            case LEAP -> { // stretched in flight
                y = 1.12F;
                xz = 0.92F;
            }
            case STUN -> { // flattened, wobbling
                float w = Mth.sin(t * 0.55F);
                float in = Math.min(1, t / 4F);
                y = 1 - in * (0.38F + 0.05F * w);
                xz = 1 + in * (0.24F - 0.04F * w);
            }
            case SPIT -> { // puffs up
                float k = Math.min(1, t / 12F);
                y = xz = 1 + 0.16F * k;
            }
            case ROAR -> {
                float k = Mth.sin(t * 0.9F) * Math.max(0, 1 - t / 22F);
                y = 1 + 0.14F * k;
                xz = 1 - 0.08F * k;
            }
            case LEAVE -> {
                float k = Math.min(1, t / 70F);
                y = 1 - 0.55F * k;
                xz = 1 + 0.35F * k;
            }
            case DASH_AIM -> { // dug in, leaning forward, shaking
                float k = Math.min(1, t / 10F);
                float shake = Mth.sin(t * 3.1F) * 0.03F * k;
                y = 1 - 0.22F * k + shake;
                xz = 1 + 0.1F * k;
                z = 1 + 0.12F * k;
            }
            case DASH -> { // stretched along the slide
                y = 0.82F;
                z = 1.3F;
                xz = 0.95F;
            }
            case STOMP -> { // bobbing with each stomp
                float w = Mth.abs(Mth.sin(t * 0.25F));
                y = 1 - 0.12F * w;
                xz = 1 + 0.07F * w;
            }
            case TIRED -> { // sagging and panting: the opening
                float breath = Mth.sin(t * 0.22F);
                float in = Math.min(1, t / 6F);
                y = 1 - in * (0.3F + 0.06F * breath);
                xz = 1 + in * (0.18F - 0.04F * breath);
            }
            case ABSORB -> { // pulsing as it draws its slimes in
                float k = Mth.sin(t * 0.5F);
                y = xz = 1 + 0.07F * k;
            }
            case CAST -> { // swelling up before the rain
                float k = Math.min(1, t / 18F);
                float shake = Mth.sin(t * 2.4F) * 0.04F * k;
                y = 1 + 0.22F * k + shake;
                xz = 1 - 0.06F * k;
            }
            default -> { }
        }
        poseStack.scale(xz * x, y, xz * z);
    }

    /** The translucent outer body with the king's texture (purple-tinted when enraged). */
    static final class Outer extends RenderLayer<KingSlimeRenderState, SlimeModel> {
        private final SlimeModel model;

        Outer(RenderLayerParent<KingSlimeRenderState, SlimeModel> parent, SlimeModel model) {
            super(parent);
            this.model = model;
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, KingSlimeRenderState state, float yRot, float xRot) {
            if (state.isInvisible) return;
            int overlay = LivingEntityRenderer.getOverlayCoords(state, 0.0F);
            int tint = state.phase >= 2 ? 0xFFE0A8FF : 0xFFFFFFFF;
            collector.order(1).submitModel(model, state, poseStack, RenderTypes.entityTranslucent(TEXTURE), light, overlay, tint, null, state.outlineColor);
        }
    }

    /** A gold crown sitting on top of the body, gems in front and on the sides. */
    static final class Crown extends RenderLayer<KingSlimeRenderState, SlimeModel> {
        private final CrownModel model = new CrownModel(createCrown().bakeRoot());

        Crown(RenderLayerParent<KingSlimeRenderState, SlimeModel> parent) {
            super(parent);
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, KingSlimeRenderState state, float yRot, float xRot) {
            if (state.isInvisible) return;
            int overlay = LivingEntityRenderer.getOverlayCoords(state, 0.0F);
            collector.order(2).submitModel(model, state, poseStack, RenderTypes.entityCutout(CROWN), light, overlay, -1, null, state.outlineColor);
        }
    }

    static final class CrownModel extends EntityModel<KingSlimeRenderState> {
        CrownModel(ModelPart root) {
            super(root);
        }
    }

    static LayerDefinition createCrown() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        // model space: the outer body spans y 16..24 (24 = feet), so the crown stands on y = 16
        PartDefinition crown = root.addOrReplaceChild("crown", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-3.0F, 13.2F, -3.0F, 6.0F, 2.8F, 6.0F), PartPose.rotation(0.0F, 0.0F, 0.06F));
        float[][] spikes = {{-3, -3}, {2, -3}, {-3, 2}, {2, 2}, {-3, -0.5F}, {2, -0.5F}, {-0.5F, 2}};
        int i = 0;
        for (float[] s : spikes) {
            crown.addOrReplaceChild("spike" + i++, CubeListBuilder.create().texOffs(0, 10).addBox(s[0], 11.2F, s[1], 1.0F, 2.0F, 1.0F), PartPose.ZERO);
        }
        // the tall front spike with a ruby, sapphires on the sides
        crown.addOrReplaceChild("front", CubeListBuilder.create().texOffs(0, 14).addBox(-0.5F, 10.2F, -3.0F, 1.0F, 3.0F, 1.0F), PartPose.ZERO);
        crown.addOrReplaceChild("ruby", CubeListBuilder.create().texOffs(8, 10).addBox(-0.75F, 14.0F, -3.35F, 1.5F, 1.2F, 0.6F), PartPose.ZERO);
        crown.addOrReplaceChild("sapphire_l", CubeListBuilder.create().texOffs(8, 13).addBox(-3.35F, 14.1F, -0.5F, 0.6F, 1.0F, 1.0F), PartPose.ZERO);
        crown.addOrReplaceChild("sapphire_r", CubeListBuilder.create().texOffs(8, 13).addBox(2.75F, 14.1F, -0.5F, 0.6F, 1.0F, 1.0F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 32, 32);
    }
}
