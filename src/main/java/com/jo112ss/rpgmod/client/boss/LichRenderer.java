package com.jo112ss.rpgmod.client.boss;

import com.jo112ss.rpgmod.RpgMod;
import com.jo112ss.rpgmod.mob.boss.Lich;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.monster.skeleton.SkeletonModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.SkeletonRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * The Lich King: a wither skeleton's frame grown huge, in a tattered violet robe and a gold-rimmed
 * hood with a crown, eyes burning (cyan, violet once enraged), its staff raised to cast, levelled at
 * its target to fire the death ray, and down on one knee when drained.
 */
public class LichRenderer extends HumanoidMobRenderer<Lich, LichRenderer.State, LichRenderer.Model> {
    private static final Identifier BODY = Identifier.withDefaultNamespace("textures/entity/skeleton/wither_skeleton.png");
    private static final Identifier ROBE = Identifier.fromNamespaceAndPath(RpgMod.MODID, "textures/entity/lich_robe.png");
    private static final Identifier EYES = Identifier.fromNamespaceAndPath(RpgMod.MODID, "textures/entity/lich_eyes.png");

    public static class State extends SkeletonRenderState {
        public Lich.State mode = Lich.State.CHASE;
        public float modeTicks;
        public int phase = 1;
    }

    public static class Model extends SkeletonModel<State> {
        public Model(ModelPart root) {
            super(root);
        }

        @Override
        public void setupAnim(State s) {
            super.setupAnim(s);
            float t = s.modeTicks;
            switch (s.mode) {
                case BOLTS_UP, SIGILS_UP, NOVA_UP, SUMMON, STORM, RISE -> { // both arms up, the staff high
                    float sway = Mth.sin(t * 0.35F) * 0.08F;
                    rightArm.xRot = -2.7F + sway;
                    leftArm.xRot = -2.7F - sway;
                    rightArm.zRot = 0.25F;
                    leftArm.zRot = -0.25F;
                    head.xRot = -0.35F;
                }
                case BEAM_AIM, BEAM -> { // the staff levelled at the target
                    rightArm.xRot = -1.5F + head.xRot;
                    rightArm.yRot = head.yRot - 0.1F;
                    leftArm.xRot = -0.4F;
                }
                case CAST_END, BLINK -> {
                    rightArm.xRot = -1.0F;
                    leftArm.xRot = -0.6F;
                }
                case DRAINED -> { // down on one knee, head bowed
                    body.xRot = 0.45F;
                    head.xRot = 0.7F;
                    rightArm.xRot = -0.25F;
                    leftArm.xRot = 0.2F;
                    rightLeg.xRot = -1.45F;
                    leftLeg.xRot = 0.35F;
                }
                default -> { }
            }
        }
    }

    public LichRenderer(EntityRendererProvider.Context context) {
        super(context, new Model(context.bakeLayer(ModelLayers.WITHER_SKELETON)), 0.5F);
        this.addLayer(new Robe(this));
        this.addLayer(new Eyes(this));
    }

    @Override
    public Identifier getTextureLocation(State state) {
        return BODY;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(Lich entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.mode = entity.state();
        state.modeTicks = entity.clientStateTicks + partialTicks;
        state.phase = entity.phase();
        state.isAggressive = true;
    }

    @Override
    protected void setupRotations(State state, PoseStack poseStack, float bodyRot, float entityScale) {
        super.setupRotations(state, poseStack, bodyRot, entityScale);
        if (state.mode == Lich.State.DRAINED) poseStack.translate(0, -0.35F * entityScale, 0); // kneeling
        else poseStack.translate(0, (0.06F + 0.06F * Mth.sin(state.ageInTicks * 0.08F)) * entityScale, 0); // a faint hover
    }

    /** The robe, hood and crown, a size bigger than the bones. */
    static final class Robe extends RenderLayer<State, Model> {
        private final Model model = new Model(LayerDefinition.create(HumanoidModel.createMesh(new CubeDeformation(0.35F), 0), 64, 32).bakeRoot());

        Robe(RenderLayerParent<State, Model> parent) {
            super(parent);
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, State state, float yRot, float xRot) {
            coloredCutoutModelCopyLayerRender(model, ROBE, poseStack, collector, light, state, -1, 1);
        }
    }

    /** Eyes burning in the sockets: cyan, violet below half health; brighter while casting. */
    static final class Eyes extends RenderLayer<State, Model> {
        Eyes(RenderLayerParent<State, Model> parent) {
            super(parent);
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, State state, float yRot, float xRot) {
            if (state.isInvisible) return;
            int rgb = state.phase >= 2 ? 0xC07AFF : 0x7FE8FF;
            boolean casting = state.mode != Lich.State.CHASE && state.mode != Lich.State.DRAINED;
            float b = state.mode == Lich.State.DRAINED ? 0.35F : casting ? 1.0F : 0.75F + 0.2F * Mth.sin(state.ageInTicks * 0.15F);
            int r = (int) (((rgb >> 16) & 255) * b), g = (int) (((rgb >> 8) & 255) * b), bl = (int) ((rgb & 255) * b);
            collector.order(2).submitModel(getParentModel(), state, poseStack, RenderTypes.eyes(EYES), light, OverlayTexture.NO_OVERLAY,
                    0xFF000000 | (r << 16) | (g << 8) | bl, null, state.outlineColor);
        }
    }
}
