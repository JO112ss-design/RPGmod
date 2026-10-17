package com.jo112ss.rpgmod.mixin;

import com.jo112ss.rpgmod.client.boss.BloodMoonClient;
import net.minecraft.client.renderer.SkyRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Blood moon: the moon turns red and grows (vanilla draws it plain white). */
@Mixin(SkyRenderer.class)
public abstract class SkyRendererMixin {
    @ModifyArg(method = "renderMoon", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/DynamicGpuData;writeTransform(Lorg/joml/Matrix4f;Lorg/joml/Vector4f;)Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;"), index = 1)
    private Vector4f rpgmod$redMoon(Vector4f color) {
        float k = BloodMoonClient.amount();
        if (k <= 0) return color;
        return new Vector4f(color.x(), color.y() * (1 - 0.8F * k), color.z() * (1 - 0.85F * k), color.w());
    }

    @ModifyArg(method = "renderMoon", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/SkyRenderer;applyCelestialBodyTransform(Lcom/mojang/blaze3d/vertex/PoseStack;FF)Lorg/joml/Matrix4f;"), index = 2)
    private float rpgmod$bigMoon(float scale) {
        return scale * (1 + 0.6F * BloodMoonClient.amount());
    }
}
