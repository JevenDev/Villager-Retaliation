package com.jvn.villagerretaliation.authoring.client;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import imgui.ImGui;
import java.io.IOException;
import java.util.Base64;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

final class EditorWorldPreview implements AutoCloseable {
    private TextureTarget target;

    void render(float width) {
        int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        try {
            RenderSystem.disableScissor();
            if (target == null) target = new TextureTarget(480, 270, false, Minecraft.ON_OSX);
            var source = Minecraft.getInstance().getMainRenderTarget();
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, source.frameBufferId);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, target.frameBufferId);
            GL30.glBlitFramebuffer(0, 0, source.width, source.height, 0, 0, target.width, target.height, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
            RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            if (scissor) GL11.glEnable(GL11.GL_SCISSOR_TEST);
        }
        ImGui.image(target.getColorTextureId(), width, width * 9 / 16, 0, 1, 1, 0);
    }

    String thumbnail() throws IOException {
        if (target == null) return "";
        try (NativeImage image = Screenshot.takeScreenshot(target)) { return Base64.getEncoder().encodeToString(image.asByteArray()); }
    }

    @Override public void close() {
        if (target != null) { target.destroyBuffers(); target = null; }
    }
}
