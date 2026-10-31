package com.jvn.villagerretaliation.client.interaction;

import com.jvn.villagerretaliation.client.VillagerRetaliationClientAssets;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

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
        com.jvn.toucanlib.neoforge.client.ToucanShaders.register(event,
                VillagerRetaliationClientAssets.DIALOGUE_CINEMATIC_BARS_SHADER,
                DefaultVertexFormat.POSITION, shader -> dialogueCinematicBarsShader = shader);
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
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        try {
            com.jvn.toucanlib.neoforge.client.ToucanShaderQuads.drawRect(graphics, shader,
                    rect.left(), rect.top(), rect.right(), rect.bottom());
        } finally {
            RenderSystem.disableBlend();
        }
    }

    private static void setUniform(ShaderInstance shader, String name, float value) {
        com.jvn.toucanlib.neoforge.client.ToucanShaders.setUniform(shader, name, value);
    }
}
