package com.jo112ss.rpgmod.client.boss;

import com.jo112ss.rpgmod.RpgMod;
import com.jo112ss.rpgmod.mob.boss.Golem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;

/**
 * The Golem: a 2.2x iron golem shape in dark stone with glowing cracks, eyes and core (brighter below
 * half health, blazing while it overheats), a glowing swirl while its stone armour stands, and the
 * boulder it is about to throw held overhead. Leans into charges, twists for the sweep, kneels when
 * overheated.
 */
public class GolemRenderer extends MobRenderer<Golem, GolemRenderState, GolemModel> {
    public static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(RpgMod.MODID, "textures/entity/golem.png");
    public static final Identifier GLOW = Identifier.fromNamespaceAndPath(RpgMod.MODID, "textures/entity/golem_glow.png");
    public static final Identifier ARMOR = Identifier.fromNamespaceAndPath(RpgMod.MODID, "textures/entity/golem_armor.png");
    private static final BlockDisplayContext BLOCK_CONTEXT = BlockDisplayContext.create();
    private final BlockModelResolver blocks;

    public GolemRenderer(EntityRendererProvider.Context context) {
        super(context, new GolemModel(GolemModel.createBodyLayer().bakeRoot()), 0.7F * Golem.SCALE);
        this.blocks = context.getBlockModelResolver();
        this.addLayer(new Glow(this));
        this.addLayer(new Armour(this, new GolemModel(GolemModel.createBodyLayer().bakeRoot())));
        this.addLayer(new Boulder(this));
    }

    @Override
    public Identifier getTextureLocation(GolemRenderState state) {
        return TEXTURE;
    }

    @Override
    public GolemRenderState createRenderState() {
        return new GolemRenderState();
    }

    @Override
    public void extractRenderState(Golem entity, GolemRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.mode = entity.state().ordinal();
        state.modeTicks = entity.clientStateTicks + partialTicks;
        state.phase = entity.phase();
        state.shield = entity.shielded();
        state.attackAnim = entity.attackAnim > 0 ? entity.attackAnim - partialTicks : 0;
        if (entity.state() == Golem.State.THROW_UP) {
            blocks.update(state.boulder, Blocks.COBBLED_DEEPSLATE.defaultBlockState(), BLOCK_CONTEXT);
        } else {
            state.boulder.clear();
        }
    }

    @Override
    protected void scale(GolemRenderState state, PoseStack poseStack) {
        poseStack.scale(Golem.SCALE, Golem.SCALE, Golem.SCALE);
    }

    @Override
    protected void setupRotations(GolemRenderState state, PoseStack poseStack, float bodyRot, float entityScale) {
        super.setupRotations(state, poseStack, bodyRot, entityScale);
        float t = state.modeTicks;
        switch (GolemModel.mode(state)) {
            case CHASE -> {
                if (state.walkAnimationSpeed >= 0.01F) {
                    float wp = state.walkAnimationPos + 6.0F;
                    poseStack.rotateDegrees(Axis.ZP, 4.0F * (Math.abs(wp % 13.0F - 6.5F) - 3.25F) / 3.25F);
                }
            }
            case SLAM -> poseStack.rotateDegrees(Axis.XP, -16F * Math.min(1, t / 3F));
            case CHARGE_AIM -> poseStack.rotateDegrees(Axis.XP, -12F * Math.min(1, t / 10F));
            case CHARGE -> poseStack.rotateDegrees(Axis.XP, -22F);
            case SWEEP_UP -> poseStack.rotateDegrees(Axis.YP, -35F * Math.min(1, t / 10F));
            case SWEEP -> poseStack.rotateDegrees(Axis.YP, Mth.lerp(Math.min(1, t / 3F), -35F, 45F));
            case OVERHEAT -> {
                float k = Math.min(1, t / 8F);
                poseStack.translate(0, -0.45F * k * Golem.SCALE, 0);
                poseStack.rotateDegrees(Axis.XP, -14F * k);
            }
            case LEAVE -> poseStack.rotateDegrees(Axis.ZP, Math.min(1, t / 70F) * 12F);
            default -> { }
        }
    }

    /** Glowing cracks, eyes and core. */
    static final class Glow extends RenderLayer<GolemRenderState, GolemModel> {
        Glow(RenderLayerParent<GolemRenderState, GolemModel> parent) {
            super(parent);
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, GolemRenderState state, float yRot, float xRot) {
            if (state.isInvisible) return;
            float pulse = 0.5F + 0.5F * Mth.sin(state.ageInTicks * (state.phase >= 2 ? 0.35F : 0.18F));
            float b;
            int rgb;
            if (GolemModel.mode(state) == Golem.State.OVERHEAT) {
                b = 1;
                rgb = 0xFFD070; // blazing
            } else {
                b = state.phase >= 2 ? 0.75F + 0.25F * pulse : 0.45F + 0.3F * pulse;
                rgb = 0xFF7040;
            }
            int r = (int) (((rgb >> 16) & 255) * b), g = (int) (((rgb >> 8) & 255) * b), bl = (int) ((rgb & 255) * b);
            int tint = 0xFF000000 | (r << 16) | (g << 8) | bl;
            collector.order(1).submitModel(getParentModel(), state, poseStack, RenderTypes.eyes(GLOW), light, OverlayTexture.NO_OVERLAY, tint, null, state.outlineColor);
        }
    }

    /** The stone armour: a slowly turning swirl of runes around the body while the pillars stand. */
    static final class Armour extends RenderLayer<GolemRenderState, GolemModel> {
        private final GolemModel model;

        Armour(RenderLayerParent<GolemRenderState, GolemModel> parent, GolemModel model) {
            super(parent);
            this.model = model;
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, GolemRenderState state, float yRot, float xRot) {
            if (!state.shield || state.isInvisible) return;
            model.setupAnim(state);
            float f = state.ageInTicks * 0.01F;
            poseStack.pushPose();
            poseStack.scale(1.06F, 1.03F, 1.08F);
            collector.order(2).submitModel(model, state, poseStack, RenderTypes.energySwirl(ARMOR, f % 1.0F, f * 0.6F % 1.0F), light,
                    OverlayTexture.NO_OVERLAY, 0xFFFF9050, null, state.outlineColor);
            poseStack.popPose();
        }
    }

    /** The boulder held overhead before a throw. */
    static final class Boulder extends RenderLayer<GolemRenderState, GolemModel> {
        Boulder(RenderLayerParent<GolemRenderState, GolemModel> parent) {
            super(parent);
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, GolemRenderState state, float yRot, float xRot) {
            if (state.boulder.isEmpty()) return;
            poseStack.pushPose();
            getParentModel().rightArm.translateAndRotate(poseStack);
            // the right hand's end, moved over to the middle between both hands
            poseStack.translate(11 / 16F, 28 / 16F, 0);
            float s = 1.5F * Math.min(1, state.modeTicks / 8F);
            poseStack.scale(s, s, s);
            poseStack.translate(-0.5F, -0.2F, -0.5F);
            state.boulder.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
            poseStack.popPose();
        }
    }

    static int overlay(GolemRenderState s) {
        return LivingEntityRenderer.getOverlayCoords(s, 0.0F);
    }
}
