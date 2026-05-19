package com.jvn.villagerretaliation.client.interaction;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.io.IOException;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;

public final class VillagerInteractionScreenShaderRenderer {
    private static final ResourceLocation INTERACTION_VEIL_SHADER_ID = VillagerRetaliation.id("interaction_veil");
    private static final float DITHER_CELL_SIZE = 1.0F;
    private static final float DITHER_ARC_DEPTH = 30.0F;

    private static ShaderInstance interactionVeilShader;

    private VillagerInteractionScreenShaderRenderer() {
    }

    public static void registerShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(), INTERACTION_VEIL_SHADER_ID, DefaultVertexFormat.POSITION),
                    shader -> interactionVeilShader = shader
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to register interaction veil shader", exception);
        }
    }

    public static void renderInteractionVeil(GuiGraphics graphics, int width, int height, float veilTop, float fadeHeight) {
        if (interactionVeilShader == null) {
            graphics.fill(0, Math.max(0, Math.round(veilTop + fadeHeight)), width, height, 0xFF000000);
            return;
        }

        setUniform(interactionVeilShader, "VeilTop", veilTop);
        setUniform(interactionVeilShader, "FadeHeight", fadeHeight);
        setUniform(interactionVeilShader, "CellSize", DITHER_CELL_SIZE);
        setUniform(interactionVeilShader, "ScreenWidth", (float) width);
        setUniform(interactionVeilShader, "ArcDepth", DITHER_ARC_DEPTH);
        Matrix4f pose = graphics.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(() -> interactionVeilShader);

        BufferBuilder bufferBuilder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        bufferBuilder.addVertex(pose, 0.0F, height, 0.0F);
        bufferBuilder.addVertex(pose, width, height, 0.0F);
        bufferBuilder.addVertex(pose, width, Math.max(0.0F, veilTop), 0.0F);
        bufferBuilder.addVertex(pose, 0.0F, Math.max(0.0F, veilTop), 0.0F);
        BufferUploader.drawWithShader(bufferBuilder.buildOrThrow());

        RenderSystem.disableBlend();
    }

    private static void setUniform(ShaderInstance shader, String name, float value) {
        var uniform = shader.getUniform(name);
        if (uniform != null) {
            uniform.set(value);
        }
    }
}
