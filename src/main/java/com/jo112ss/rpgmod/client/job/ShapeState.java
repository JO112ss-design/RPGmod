package com.jo112ss.rpgmod.client.job;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.jspecify.annotations.Nullable;

/**
 * One flat-coloured polygon made of quads, submitted as a single GUI element. Hexes, rings and discs
 * used to be built from one {@code fill} per pixel row (dozens of elements per hex, thousands per
 * frame on the skill panel); this draws each shape as one element with a handful of vertices.
 *
 * @param quads x/y pairs, 4 corners (8 floats) per quad
 */
public record ShapeState(Matrix3x2fc pose, float[] quads, int color, @Nullable ScreenRectangle scissorArea,
                         @Nullable ScreenRectangle bounds) implements GuiElementRenderState {

    @Override
    public void buildVertices(VertexConsumer v) {
        for (int i = 0; i + 7 < quads.length; i += 8) {
            // same winding as vanilla rectangles, whatever order the corners were given in
            double area = 0;
            for (int k = 0; k < 4; k++) {
                int a = i + k * 2, b = i + ((k + 1) % 4) * 2;
                area += quads[a] * quads[b + 1] - quads[b] * quads[a + 1];
            }
            if (area <= 0) {
                for (int k = 0; k < 4; k++) v.addVertexWith2DPose(pose, quads[i + k * 2], quads[i + k * 2 + 1]).setColor(color);
            } else {
                for (int k = 3; k >= 0; k--) v.addVertexWith2DPose(pose, quads[i + k * 2], quads[i + k * 2 + 1]).setColor(color);
            }
        }
    }

    @Override
    public RenderPipeline pipeline() {
        return RenderPipelines.GUI;
    }

    @Override
    public TextureSetup textureSetup() {
        return TextureSetup.noTexture();
    }

    /** Submits the quads with the current pose and scissor. */
    public static void submit(GuiGraphicsExtractor g, float[] quads, int color) {
        if (quads.length < 8 || (color >>> 24) == 0) return;
        float x0 = Float.MAX_VALUE, y0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, y1 = -Float.MAX_VALUE;
        for (int i = 0; i + 1 < quads.length; i += 2) {
            x0 = Math.min(x0, quads[i]);
            x1 = Math.max(x1, quads[i]);
            y0 = Math.min(y0, quads[i + 1]);
            y1 = Math.max(y1, quads[i + 1]);
        }
        Matrix3x2f pose = new Matrix3x2f(g.pose());
        ScreenRectangle scissor = g.peekScissorStack();
        int bx = (int) Math.floor(x0), by = (int) Math.floor(y0);
        ScreenRectangle b = new ScreenRectangle(bx, by, (int) Math.ceil(x1) - bx, (int) Math.ceil(y1) - by).transformMaxBounds(pose);
        if (scissor != null) {
            b = scissor.intersection(b);
            if (b == null) return; // fully clipped
        }
        g.submitGuiElementRenderState(new ShapeState(pose, quads, color, scissor, b));
    }
}
