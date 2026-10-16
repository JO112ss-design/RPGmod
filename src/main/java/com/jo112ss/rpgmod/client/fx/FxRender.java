package com.jo112ss.rpgmod.client.fx;

import com.jo112ss.rpgmod.RpgMod;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

/**
 * Render layers for skill particles: an additive one (light adds up, so overlapping sparks glow) and
 * a plain translucent one for smoke and coloured sprites. Both draw both faces so flat effects lying
 * on the ground or standing in the air are visible from any side, and neither writes depth so
 * overlapping particles do not cut holes into each other.
 */
public final class FxRender {
    private FxRender() {}

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(RpgMod.MODID, path);
    }

    public static final RenderPipeline ADDITIVE_PIPELINE = RenderPipeline.builder(RenderPipelines.PARTICLE_SNIPPET)
            .withLocation(id("pipeline/fx_additive"))
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .withCull(false)
            .build();

    public static final RenderPipeline ALPHA_PIPELINE = RenderPipeline.builder(RenderPipelines.PARTICLE_SNIPPET)
            .withLocation(id("pipeline/fx_alpha"))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .withCull(false)
            .build();

    /** Used instead when "improved transparency" (order independent transparency) is on. */
    public static final OitPipelineSet ADDITIVE_OIT = OitPipelineSet.builder(id("fx_additive"),
            RenderPipeline.builder(RenderPipelines.OIT_PARTICLE_SNIPPET).withCull(false)).build();
    public static final OitPipelineSet ALPHA_OIT = OitPipelineSet.builder(id("fx_alpha"),
            RenderPipeline.builder(RenderPipelines.OIT_PARTICLE_SNIPPET).withCull(false)).build();

    public static final SingleQuadParticle.Layer ADDITIVE =
            new SingleQuadParticle.Layer(true, TextureAtlas.LOCATION_PARTICLES, ADDITIVE_PIPELINE, ADDITIVE_OIT);
    public static final SingleQuadParticle.Layer ALPHA =
            new SingleQuadParticle.Layer(true, TextureAtlas.LOCATION_PARTICLES, ALPHA_PIPELINE, ALPHA_OIT);

    /** Registered on the mod bus by {@code RpgModClient}. */
    public static void onRegisterPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(ADDITIVE_PIPELINE);
        event.registerPipeline(ALPHA_PIPELINE);
        event.registerOitPipelineSet(ADDITIVE_OIT);
        event.registerOitPipelineSet(ALPHA_OIT);
    }
}
