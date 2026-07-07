package com.jvn.villagerretaliation.client.interaction;

import com.jvn.villagerretaliation.client.VillagerRetaliationClientAssets;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.io.IOException;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;

public final class VillagerInteractionScreenShaderRenderer {
    private static ShaderInstance dialogueCinematicBarsShader;

    private VillagerInteractionScreenShaderRenderer() {
    }

    public record ShaderRect(float left, float top, float right, float bottom) {
        public ShaderRect(int left, int top, int right, int bottom) {
            this((float) left, (float) top, (float) right, (float) bottom);
        }
    }

    public static void registerShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(
                    new ShaderInstance(
                            event.getResourceProvider(),
                            VillagerRetaliationClientAssets.DIALOGUE_CINEMATIC_BARS_SHADER,
                            DefaultVertexFormat.POSITION
                    ),
                    shader -> dialogueCinematicBarsShader = shader
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to register interaction screen shaders", exception);
        }
    }

    public static boolean renderDialogueCinematicBars(
            GuiGraphics graphics,
            int width,
            int height,
            float barHeight,
            float slant,
            float progress) {
        if (dialogueCinematicBarsShader == null) {
            return false;
        }

        ShaderRect rect = new ShaderRect(0, 0, width, height);
        setRectUniforms(dialogueCinematicBarsShader, rect);
        setUniform(dialogueCinematicBarsShader, "BarHeight", barHeight);
        setUniform(dialogueCinematicBarsShader, "Slant", slant);
        setUniform(dialogueCinematicBarsShader, "Progress", progress);
        setUniform(dialogueCinematicBarsShader, "Alpha", 1.0F);

        drawQuad(graphics, dialogueCinematicBarsShader, rect);
        return true;
    }

    private static void setRectUniforms(ShaderInstance shader, ShaderRect rect) {
        setUniform(shader, "RectLeft", rect.left());
        setUniform(shader, "RectTop", rect.top());
        setUniform(shader, "RectRight", rect.right());
        setUniform(shader, "RectBottom", rect.bottom());
    }

    private static void drawQuad(GuiGraphics graphics, ShaderInstance shader, ShaderRect rect) {
        Matrix4f pose = graphics.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(() -> shader);

        BufferBuilder bufferBuilder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        bufferBuilder.addVertex(pose, rect.left(), rect.bottom(), 0.0F);
        bufferBuilder.addVertex(pose, rect.right(), rect.bottom(), 0.0F);
        bufferBuilder.addVertex(pose, rect.right(), rect.top(), 0.0F);
        bufferBuilder.addVertex(pose, rect.left(), rect.top(), 0.0F);
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
