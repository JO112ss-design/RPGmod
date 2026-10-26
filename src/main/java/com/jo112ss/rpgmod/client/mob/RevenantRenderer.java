package com.jo112ss.rpgmod.client.mob;

import com.jo112ss.rpgmod.RpgMod;
import com.jo112ss.rpgmod.mob.revenant.Revenant;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.monster.zombie.BabyZombieModel;
import net.minecraft.client.model.monster.zombie.ZombieModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.AbstractZombieRenderer;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * A fallen adventurer: the zombie body in its old job's clothes (a helmet and tabard, a robe and
 * hood, a ranger's cloak, a priest's white robe, a thief's mask), with eyes glowing in the job's
 * colour.
 */
public class RevenantRenderer extends AbstractZombieRenderer<Revenant, RevenantRenderer.State, ZombieModel<RevenantRenderer.State>> {
    public static class State extends ZombieRenderState {
        public Revenant.Role role = Revenant.Role.WARRIOR;
    }

    private static final Identifier[] OUTFITS = new Identifier[Revenant.Role.values().length];
    private static final Identifier EYES = Identifier.fromNamespaceAndPath(RpgMod.MODID, "textures/entity/revenant_eyes.png");

    static {
        for (Revenant.Role r : Revenant.Role.values()) OUTFITS[r.ordinal()] = Identifier.fromNamespaceAndPath(RpgMod.MODID, "textures/entity/revenant_" + r.id() + ".png");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public RevenantRenderer(EntityRendererProvider.Context context) {
        super(context, new ZombieModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), new BabyZombieModel(context.bakeLayer(ModelLayers.ZOMBIE_BABY)),
                (ArmorModelSet) ArmorModelSet.bake(ModelLayers.ZOMBIE_ARMOR, context.getModelSet(), ZombieModel::new),
                (ArmorModelSet) ArmorModelSet.bake(ModelLayers.ZOMBIE_BABY_ARMOR, context.getModelSet(), BabyZombieModel::new));
        this.addLayer(new Outfit(this));
        this.addLayer(new Eyes(this));
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(Revenant entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.role = entity.role();
    }

    /** The job's clothes, a little bigger than the body. */
    static final class Outfit extends RenderLayer<State, ZombieModel<State>> {
        private final ZombieModel<State> model = new ZombieModel<>(LayerDefinition.create(HumanoidModel.createMesh(new CubeDeformation(0.3F), 0), 64, 64).bakeRoot());

        Outfit(RenderLayerParent<State, ZombieModel<State>> parent) {
            super(parent);
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, State state, float yRot, float xRot) {
            coloredCutoutModelCopyLayerRender(model, OUTFITS[state.role.ordinal()], poseStack, collector, light, state, -1, 1);
        }
    }

    /** Eyes glowing in the job's colour, slowly breathing. */
    static final class Eyes extends RenderLayer<State, ZombieModel<State>> {
        Eyes(RenderLayerParent<State, ZombieModel<State>> parent) {
            super(parent);
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, State state, float yRot, float xRot) {
            if (state.isInvisible) return;
            int rgb = state.role.color;
            float b = 0.75F + 0.25F * Mth.sin(state.ageInTicks * 0.12F);
            int r = (int) (((rgb >> 16) & 255) * b), g = (int) (((rgb >> 8) & 255) * b), bl = (int) ((rgb & 255) * b);
            collector.order(2).submitModel(getParentModel(), state, poseStack, RenderTypes.eyes(EYES), light, OverlayTexture.NO_OVERLAY,
                    0xFF000000 | (r << 16) | (g << 8) | bl, null, state.outlineColor);
        }
    }
}
