package com.jvn.villagerretaliation.client.settlement;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.lwjgl.opengl.GL11;

final class SettlementPanelFade implements AutoCloseable {
    private static final float DURATION = 200;
    private float opacity = 1;
    private float from = 1;
    private boolean hidden;
    private long changedAt;
    private TextureTarget target;

    float opacity() { return this.opacity; }

    void update(boolean hidden, long now) {
        float progress = Math.clamp((now - this.changedAt) / DURATION, 0, 1);
        float eased = progress * progress * (3 - 2 * progress);
        this.opacity = this.from + ((this.hidden ? 0 : 1) - this.from) * eased;
        if (hidden != this.hidden) {
            this.hidden = hidden;
            this.from = this.opacity;
            this.changedAt = now;
        }
    }

    void render(GuiGraphics graphics, int width, int height, Runnable panels) {
        if (this.opacity == 0 || this.opacity == 1) {
            close();
            if (this.opacity == 1) panels.run();
            return;
        }
        var main = Minecraft.getInstance().getMainRenderTarget();
        graphics.flush();
        if (this.target == null) this.target = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
        else if (this.target.width != main.width || this.target.height != main.height)
            this.target.resize(main.width, main.height, Minecraft.ON_OSX);
        this.target.setClearColor(0, 0, 0, 1);
        this.target.clear(Minecraft.ON_OSX);
        this.target.copyDepthFrom(main);
        this.target.bindWrite(false);
        try {
            main.blitToScreen(main.width, main.height);
            RenderSystem.enableDepthTest();
            panels.run();
            graphics.flush();
            // fade the finished scene uniformly, including nested map shaders and item icons
            RenderSystem.colorMask(false, false, false, true);
            RenderSystem.clearColor(0, 0, 0, 1);
            RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT, Minecraft.ON_OSX);
        } finally {
            RenderSystem.colorMask(true, true, true, true);
            main.bindWrite(false);
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, this.target.getColorTextureId());
        var vertices = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        var pose = graphics.pose().last().pose();
        int alpha = Math.round(this.opacity * 255);
        vertices.addVertex(pose, 0, height, 0).setUv(0, 0).setColor(255, 255, 255, alpha);
        vertices.addVertex(pose, width, height, 0).setUv(1, 0).setColor(255, 255, 255, alpha);
        vertices.addVertex(pose, width, 0, 0).setUv(1, 1).setColor(255, 255, 255, alpha);
        vertices.addVertex(pose, 0, 0, 0).setUv(0, 1).setColor(255, 255, 255, alpha);
        try {
            BufferUploader.drawWithShader(vertices.buildOrThrow());
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
        }
    }

    @Override
    public void close() {
        if (this.target == null) return;
        this.target.destroyBuffers();
        this.target = null;
        Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
    }
}
