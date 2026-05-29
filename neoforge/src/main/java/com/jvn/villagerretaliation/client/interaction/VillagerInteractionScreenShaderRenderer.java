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
    private static ShaderInstance experimentalSkillsShader;

    private VillagerInteractionScreenShaderRenderer() {
    }

    public record ShaderRect(float left, float top, float right, float bottom) {
        public ShaderRect(int left, int top, int right, int bottom) {
            this((float) left, (float) top, (float) right, (float) bottom);
        }
    }

    public record ExperimentalNotificationPanel(
            ShaderRect rect,
            int accentColor,
            float alpha,
            float elapsedTicks,
            float direction,
            float slant) {
        private static final float DEFAULT_SLANT = 9.0F;

        public ExperimentalNotificationPanel(
                ShaderRect rect,
                int accentColor,
                float alpha,
                float elapsedTicks,
                float direction) {
            this(rect, accentColor, alpha, elapsedTicks, direction, DEFAULT_SLANT);
        }
    }

    public record ExperimentalSkillsPanel(
            ShaderRect rect,
            int accentColor,
            float fillProgress,
            float alpha,
            float elapsedTicks,
            float elapsedMillis,
            float exitElapsedMillis,
            float chromeElapsedMillis,
            float chromeExitElapsedMillis,
            int screenWidth,
            int screenHeight,
            int mouseX,
            int mouseY,
            boolean clipMainChrome,
            boolean hovered) {
        public static ExperimentalSkillsPanel backdrop(
                ShaderRect rect,
                float alpha,
                float elapsedTicks,
                float elapsedMillis,
                float exitElapsedMillis,
                float chromeElapsedMillis,
                float chromeExitElapsedMillis,
                int screenWidth,
                int screenHeight,
                int mouseX,
                int mouseY,
                boolean clipMainChrome) {
            return new ExperimentalSkillsPanel(
                    rect,
                    0,
                    1.0F,
                    alpha,
                    elapsedTicks,
                    elapsedMillis,
                    exitElapsedMillis,
                    chromeElapsedMillis,
                    chromeExitElapsedMillis,
                    screenWidth,
                    screenHeight,
                    mouseX,
                    mouseY,
                    clipMainChrome,
                    false);
        }

        public static ExperimentalSkillsPanel bar(
                ShaderRect rect,
                int accentColor,
                float fillProgress,
                float alpha,
                float elapsedTicks,
                boolean hovered) {
            return new ExperimentalSkillsPanel(
                    rect,
                    accentColor,
                    fillProgress,
                    alpha,
                    elapsedTicks,
                    0.0F,
                    -1.0F,
                    0.0F,
                    -1.0F,
                    1,
                    1,
                    0,
                    0,
                    true,
                    hovered);
        }
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
            event.registerShader(
                    new ShaderInstance(
                            event.getResourceProvider(),
                            VillagerRetaliationClientAssets.EXPERIMENTAL_SKILLS_SHADER,
                            DefaultVertexFormat.POSITION
                    ),
                    shader -> experimentalSkillsShader = shader
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
        drawQuad(graphics, interactionVeilShader, new ShaderRect(0.0F, Math.max(0.0F, veilTop), width, height));
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

        drawQuad(graphics, experimentalChromeShader, new ShaderRect(0, 0, width, height));
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
        return renderExperimentalNotification(
                graphics,
                left,
                top,
                right,
                bottom,
                accentColor,
                alpha,
                elapsedTicks,
                direction,
                9.0F);
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
            float direction,
            float slant) {
        return renderExperimentalNotification(
                graphics,
                new ExperimentalNotificationPanel(
                        new ShaderRect(left, top, right, bottom),
                        accentColor,
                        alpha,
                        elapsedTicks,
                        direction,
                        slant));
    }

    public static boolean renderExperimentalNotification(
            GuiGraphics graphics,
            ExperimentalNotificationPanel panel) {
        if (experimentalNotificationShader == null) {
            return false;
        }

        setRectUniforms(experimentalNotificationShader, panel.rect());
        setAccentUniforms(experimentalNotificationShader, panel.accentColor());
        setUniform(experimentalNotificationShader, "Alpha", panel.alpha());
        setUniform(experimentalNotificationShader, "ElapsedTicks", panel.elapsedTicks());
        setUniform(experimentalNotificationShader, "Direction", panel.direction());
        setUniform(experimentalNotificationShader, "Slant", panel.slant());

        drawQuad(graphics, experimentalNotificationShader, panel.rect());
        return true;
    }

    public static boolean renderExperimentalSkillsPanel(
            GuiGraphics graphics,
            int left,
            int top,
            int right,
            int bottom,
            float alpha,
            float elapsedTicks,
            float elapsedMillis,
            float exitElapsedMillis,
            float chromeElapsedMillis,
            float chromeExitElapsedMillis,
            int screenWidth,
            int screenHeight,
            int mouseX,
            int mouseY) {
        return renderExperimentalSkillsPanel(
                graphics,
                left,
                top,
                right,
                bottom,
                alpha,
                elapsedTicks,
                elapsedMillis,
                exitElapsedMillis,
                chromeElapsedMillis,
                chromeExitElapsedMillis,
                screenWidth,
                screenHeight,
                mouseX,
                mouseY,
                true);
    }

    public static boolean renderExperimentalSkillsPanel(
            GuiGraphics graphics,
            int left,
            int top,
            int right,
            int bottom,
            float alpha,
            float elapsedTicks,
            float elapsedMillis,
            float exitElapsedMillis,
            float chromeElapsedMillis,
            float chromeExitElapsedMillis,
            int screenWidth,
            int screenHeight,
            int mouseX,
            int mouseY,
            boolean clipMainChrome) {
        return renderExperimentalSkillsPanel(
                graphics,
                ExperimentalSkillsPanel.backdrop(
                        new ShaderRect(left, top, right, bottom),
                        alpha,
                        elapsedTicks,
                        elapsedMillis,
                        exitElapsedMillis,
                        chromeElapsedMillis,
                        chromeExitElapsedMillis,
                        screenWidth,
                        screenHeight,
                        mouseX,
                        mouseY,
                        clipMainChrome));
    }

    public static boolean renderExperimentalSkillBar(
            GuiGraphics graphics,
            int left,
            int top,
            int right,
            int bottom,
            int accentColor,
            float fillProgress,
            float alpha,
            float elapsedTicks,
            boolean hovered) {
        return renderExperimentalSkillsPanel(
                graphics,
                ExperimentalSkillsPanel.bar(
                        new ShaderRect(left, top, right, bottom),
                        accentColor,
                        fillProgress,
                        alpha,
                        elapsedTicks,
                        hovered));
    }

    public static boolean renderExperimentalSkillsPanel(
            GuiGraphics graphics,
            ExperimentalSkillsPanel panel) {
        if (experimentalSkillsShader == null) {
            return false;
        }

        setRectUniforms(experimentalSkillsShader, panel.rect());
        setAccentUniforms(experimentalSkillsShader, panel.accentColor());
        setUniform(experimentalSkillsShader, "FillProgress", panel.fillProgress());
        setUniform(experimentalSkillsShader, "Alpha", panel.alpha());
        setUniform(experimentalSkillsShader, "ElapsedTicks", panel.elapsedTicks());
        setUniform(experimentalSkillsShader, "ElapsedMillis", panel.elapsedMillis());
        setUniform(experimentalSkillsShader, "ExitElapsedMillis", panel.exitElapsedMillis());
        setUniform(experimentalSkillsShader, "ChromeElapsedMillis", panel.chromeElapsedMillis());
        setUniform(experimentalSkillsShader, "ChromeExitElapsedMillis", panel.chromeExitElapsedMillis());
        setUniform(experimentalSkillsShader, "ScreenWidth", (float) panel.screenWidth());
        setUniform(experimentalSkillsShader, "ScreenHeight", (float) panel.screenHeight());
        setUniform(experimentalSkillsShader, "MouseX", (float) panel.mouseX());
        setUniform(experimentalSkillsShader, "MouseY", (float) panel.mouseY());
        setUniform(experimentalSkillsShader, "Hovered", panel.hovered() ? 1.0F : 0.0F);
        setUniform(experimentalSkillsShader, "Mode", panel.accentColor() == 0 ? 0.0F : 1.0F);
        setUniform(experimentalSkillsShader, "ClipMainChrome", panel.clipMainChrome() ? 1.0F : 0.0F);

        drawQuad(graphics, experimentalSkillsShader, panel.rect());
        return true;
    }

    private static void setRectUniforms(ShaderInstance shader, ShaderRect rect) {
        setUniform(shader, "RectLeft", rect.left());
        setUniform(shader, "RectTop", rect.top());
        setUniform(shader, "RectRight", rect.right());
        setUniform(shader, "RectBottom", rect.bottom());
    }

    private static void setAccentUniforms(ShaderInstance shader, int accentColor) {
        setUniform(shader, "AccentRed", ((accentColor >> 16) & 0xFF) / 255.0F);
        setUniform(shader, "AccentGreen", ((accentColor >> 8) & 0xFF) / 255.0F);
        setUniform(shader, "AccentBlue", (accentColor & 0xFF) / 255.0F);
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
