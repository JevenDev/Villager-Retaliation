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
    private static final float DITHER_CELL_SIZE = 1.0F;
    private static final float DITHER_ARC_DEPTH = 30.0F;

    private static ShaderInstance interactionVeilShader;
    private static ShaderInstance experimentalChromeShader;
    private static ShaderInstance experimentalNotificationShader;

    private VillagerInteractionScreenShaderRenderer() {
    }

    public static void registerShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(
                    new ShaderInstance(
                            event.getResourceProvider(),
                            VillagerRetaliationClientAssets.INTERACTION_VEIL_SHADER,
                            DefaultVertexFormat.POSITION
                    ),
                    shader -> interactionVeilShader = shader
            );
            event.registerShader(
                    new ShaderInstance(
                            event.getResourceProvider(),
                            VillagerRetaliationClientAssets.EXPERIMENTAL_CHROME_SHADER,
                            DefaultVertexFormat.POSITION
                    ),
                    shader -> experimentalChromeShader = shader
            );
            event.registerShader(
                    new ShaderInstance(
                            event.getResourceProvider(),
                            VillagerRetaliationClientAssets.EXPERIMENTAL_NOTIFICATION_SHADER,
                            DefaultVertexFormat.POSITION
                    ),
                    shader -> experimentalNotificationShader = shader
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to register interaction screen shaders", exception);
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

    public static boolean renderExperimentalChrome(
            GuiGraphics graphics,
            int width,
            int height,
            float elapsedMillis,
            float exitElapsedMillis,
            int mouseX,
            int mouseY) {
        if (experimentalChromeShader == null) {
            return false;
        }

        setUniform(experimentalChromeShader, "ScreenWidth", (float) width);
        setUniform(experimentalChromeShader, "ScreenHeight", (float) height);
        setUniform(experimentalChromeShader, "MouseX", (float) mouseX);
        setUniform(experimentalChromeShader, "MouseY", (float) mouseY);
        setUniform(experimentalChromeShader, "ElapsedMillis", elapsedMillis);
        setUniform(experimentalChromeShader, "ExitElapsedMillis", exitElapsedMillis);

        Matrix4f pose = graphics.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(() -> experimentalChromeShader);

        BufferBuilder bufferBuilder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        bufferBuilder.addVertex(pose, 0.0F, height, 0.0F);
        bufferBuilder.addVertex(pose, width, height, 0.0F);
        bufferBuilder.addVertex(pose, width, 0.0F, 0.0F);
        bufferBuilder.addVertex(pose, 0.0F, 0.0F, 0.0F);
        BufferUploader.drawWithShader(bufferBuilder.buildOrThrow());

        RenderSystem.disableBlend();
        return true;
    }

    public static boolean renderExperimentalNotification(
            GuiGraphics graphics,
            int left,
            int top,
            int right,
            int bottom,
            int accentColor,
            float alpha,
            float elapsedTicks,
            float direction) {
        if (experimentalNotificationShader == null) {
            return false;
        }

        setUniform(experimentalNotificationShader, "RectLeft", (float) left);
        setUniform(experimentalNotificationShader, "RectTop", (float) top);
        setUniform(experimentalNotificationShader, "RectRight", (float) right);
        setUniform(experimentalNotificationShader, "RectBottom", (float) bottom);
        setUniform(experimentalNotificationShader, "AccentRed", ((accentColor >> 16) & 0xFF) / 255.0F);
        setUniform(experimentalNotificationShader, "AccentGreen", ((accentColor >> 8) & 0xFF) / 255.0F);
        setUniform(experimentalNotificationShader, "AccentBlue", (accentColor & 0xFF) / 255.0F);
        setUniform(experimentalNotificationShader, "Alpha", alpha);
        setUniform(experimentalNotificationShader, "ElapsedTicks", elapsedTicks);
        setUniform(experimentalNotificationShader, "Direction", direction);

        Matrix4f pose = graphics.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(() -> experimentalNotificationShader);

        BufferBuilder bufferBuilder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        bufferBuilder.addVertex(pose, left, bottom, 0.0F);
        bufferBuilder.addVertex(pose, right, bottom, 0.0F);
        bufferBuilder.addVertex(pose, right, top, 0.0F);
        bufferBuilder.addVertex(pose, left, top, 0.0F);
        BufferUploader.drawWithShader(bufferBuilder.buildOrThrow());

        RenderSystem.disableBlend();
        return true;
    }

    private static void setUniform(ShaderInstance shader, String name, float value) {
        var uniform = shader.getUniform(name);
        if (uniform != null) {
            uniform.set(value);
        }
    }

}
