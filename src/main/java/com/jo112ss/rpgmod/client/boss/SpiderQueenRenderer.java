package com.jo112ss.rpgmod.client.boss;

import com.jo112ss.rpgmod.RpgMod;
import com.jo112ss.rpgmod.mob.boss.SpiderQueen;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.monster.spider.SpiderModel;
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

/**
 * The Spider Queen: black and violet with glowing markings and a crown of spines, many red eyes.
 * She crouches before a pounce, spreads her legs in the air, rears up to spit and spray, and sinks
 * flat with her legs splayed when spent.
 */
public class SpiderQueenRenderer extends MobRenderer<SpiderQueen, SpiderQueenRenderer.State, SpiderQueenRenderer.Model> {
    private static final Identifier BODY = Identifier.fromNamespaceAndPath(RpgMod.MODID, "textures/entity/spider_queen.png");
    private static final Identifier EYES = Identifier.fromNamespaceAndPath(RpgMod.MODID, "textures/entity/spider_queen_eyes.png");

    public static class State extends LivingEntityRenderState {
        public SpiderQueen.State mode = SpiderQueen.State.CHASE;
        public float modeTicks;
        public int phase = 1;
    }

    static boolean rearing(SpiderQueen.State m) {
        return m == SpiderQueen.State.WEB_UP || m == SpiderQueen.State.SPRAY_UP || m == SpiderQueen.State.FIELD_UP || m == SpiderQueen.State.SWIPE_UP
                || m == SpiderQueen.State.SHRIEK;
    }

    public static class Model extends SpiderModel {
        private final ModelPart[] right, left;
        private final ModelPart head;

        public Model(ModelPart root) {
            super(root);
            head = root.getChild("head");
            right = new ModelPart[] {root.getChild("right_front_leg"), root.getChild("right_middle_front_leg"), root.getChild("right_middle_hind_leg"),
                    root.getChild("right_hind_leg")};
            left = new ModelPart[] {root.getChild("left_front_leg"), root.getChild("left_middle_front_leg"), root.getChild("left_middle_hind_leg"),
                    root.getChild("left_hind_leg")};
        }

        @Override
        public void setupAnim(LivingEntityRenderState s) {
            super.setupAnim(s);
            if (!(s instanceof State q)) return;
            float t = q.modeTicks;
            switch (q.mode) {
                case CROUCH -> { // legs flatter: the body sinks, ready to spring
                    for (ModelPart p : right) p.zRot += 0.35F;
                    for (ModelPart p : left) p.zRot -= 0.35F;
                }
                case POUNCE -> { // legs flung out, the front ones reaching
                    for (ModelPart p : right) p.zRot = -0.15F;
                    for (ModelPart p : left) p.zRot = 0.15F;
                    right[0].yRot -= 0.5F;
                    left[0].yRot += 0.5F;
                }
                case SPENT -> { // splayed flat
                    for (ModelPart p : right) p.zRot = -0.05F;
                    for (ModelPart p : left) p.zRot = 0.05F;
                    head.xRot = 0.4F;
                }
                case SWIPE -> { // the front legs sweep down and in
                    right[0].zRot = -0.2F;
                    left[0].zRot = 0.2F;
                    right[0].yRot = 0.4F;
                    left[0].yRot = -0.4F;
                }
                default -> {
                    if (rearing(q.mode)) { // front legs raised and waving
                        float wave = Mth.sin(t * 0.5F) * 0.15F;
                        right[0].zRot = 0.55F + wave;
                        left[0].zRot = -0.55F - wave;
                        right[1].zRot = 0.1F;
                        left[1].zRot = -0.1F;
                        head.xRot = -0.3F;
                    }
                }
            }
        }
    }

    public SpiderQueenRenderer(EntityRendererProvider.Context context) {
        super(context, new Model(context.bakeLayer(ModelLayers.SPIDER)), 0.8F);
        this.addLayer(new Eyes(this));
    }

    @Override
    protected float getFlipDegrees() {
        return 180.0F;
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
    public void extractRenderState(SpiderQueen entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.mode = entity.state();
        state.modeTicks = entity.clientStateTicks + partialTicks;
        state.phase = entity.phase();
    }

    @Override
    protected void setupRotations(State state, PoseStack poseStack, float bodyRot, float entityScale) {
        super.setupRotations(state, poseStack, bodyRot, entityScale);
        if (rearing(state.mode)) { // the front of the body lifted
            float k = Math.min(1, state.modeTicks / 6F);
            poseStack.translate(0, 0, 0.3F * entityScale);
            poseStack.mulPose(Axis.XP.rotationDegrees(-22F * k));
            poseStack.translate(0, 0, -0.3F * entityScale);
        } else if (state.mode == SpiderQueen.State.CROUCH) {
            poseStack.translate(0, -0.12F * entityScale, 0);
        } else if (state.mode == SpiderQueen.State.SPENT) {
            poseStack.translate(0, -0.3F * entityScale, 0);
        }
    }

    /** Eight red eyes, glowing (violet-red once enraged). */
    static final class Eyes extends RenderLayer<State, Model> {
        Eyes(RenderLayerParent<State, Model> parent) {
            super(parent);
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, State state, float yRot, float xRot) {
            if (state.isInvisible) return;
            int rgb = state.phase >= 2 ? 0xFF40C0 : 0xFF3030;
            float b = state.mode == SpiderQueen.State.SPENT ? 0.4F : 0.85F + 0.15F * Mth.sin(state.ageInTicks * 0.2F);
            int r = (int) (((rgb >> 16) & 255) * b), g = (int) (((rgb >> 8) & 255) * b), bl = (int) ((rgb & 255) * b);
            collector.order(2).submitModel(getParentModel(), state, poseStack, RenderTypes.eyes(EYES), light, OverlayTexture.NO_OVERLAY,
                    0xFF000000 | (r << 16) | (g << 8) | bl, null, state.outlineColor);
        }
    }
}
