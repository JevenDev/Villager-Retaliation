package com.jvn.villagerretaliation.client.exploration;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.exploration.MapLighting;
import com.jvn.villagerretaliation.exploration.MapView;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

final class MapAtmosphereClientChecks {
    private MapAtmosphereClientChecks() { }

    static void verify(ExploredMapScreen screen) {
        var minecraft = Minecraft.getInstance();
        var graphics = new GuiGraphics(minecraft, minecraft.renderBuffers().bufferSource());
        graphics.flush();
        var previousShader = RenderSystem.getShader();
        boolean parchment = VillagerRetaliationConfig.PARCHMENT_MAP_COLORS.get();
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        var target = new TextureTarget(640, 360, false, Minecraft.ON_OSX);
        RenderSystem.backupProjectionMatrix();
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix().identity();
        RenderSystem.applyModelViewMatrix();
        try {
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, target.width, target.height, 0, -1000, 1000), VertexSorting.ORTHOGRAPHIC_Z);
            RenderSystem.disableDepthTest();
            var field = MapFogRenderer.class.getDeclaredField("shader");
            field.setAccessible(true);
            var shader = (ShaderInstance) field.get(null);
            require(shader != null, "the atmosphere shader is loaded");
            VillagerRetaliationConfig.PARCHMENT_MAP_COLORS.set(true);
            try (var still = render(graphics, target, shader, false);
                 var repeated = render(graphics, target, shader, false);
                 var white = render(graphics, target, shader, true)) {
                boolean visible = false;
                for (int y = 0; y < target.height; y++) for (int x = 0; x < target.width; x++) {
                    int color = still.getPixelRGBA(x, y);
                    require(color == repeated.getPixelRGBA(x, y), "frozen atmosphere offsets produce identical clouds");
                    visible |= color >>> 24 != 0;
                    require((color >>> 24) < 255 && (white.getPixelRGBA(x, y) & 255) > (color & 255),
                            "atmospheric clouds preserve the terrain underneath");
                }
                require(visible, "the atmosphere pass renders clouds");
            }
            VillagerRetaliationConfig.PARCHMENT_MAP_COLORS.set(false);
            target.setClearColor(0, 0, 0, 0);
            target.clear(Minecraft.ON_OSX);
            target.bindWrite(true);
            MapFogRenderer.renderAtmosphere(graphics, new MapView(target.width, target.height, 0, 0, 1), MapLighting.DAY);
            try (var vanilla = snapshot(target)) {
                for (int y = 0; y < target.height; y++) for (int x = 0; x < target.width; x++)
                    require(vanilla.getPixelRGBA(x, y) == 0, "disabling parchment leaves the classic map atmosphere unchanged");
            }
            System.out.println("Map atmosphere checks passed: transparency and classic map toggle");
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        } finally {
            VillagerRetaliationConfig.PARCHMENT_MAP_COLORS.set(parchment);
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
            target.destroyBuffers();
            minecraft.getMainRenderTarget().bindWrite(true);
            RenderSystem.setShader(() -> previousShader);
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
        }
    }

    private static NativeImage render(GuiGraphics graphics, TextureTarget target, ShaderInstance shader, boolean white) {
        target.bindWrite(true);
        MapFogRenderer.renderAtmosphere(graphics, new MapView(target.width, target.height, 0, 0, 1), MapLighting.DAY);
        target.setClearColor(white ? 1 : 0, white ? 1 : 0, white ? 1 : 0, white ? 1 : 0);
        target.clear(Minecraft.ON_OSX);
        target.bindWrite(true);
        shader.safeGetUniform("AtmospherePass").set(1f);
        shader.safeGetUniform("AtmosphereDriftX").set(0f, 0f, 0f);
        shader.safeGetUniform("AtmosphereDriftZ").set(0f, 0f, 0f);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(() -> shader);
        var vertices = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        vertices.addVertex(0, target.height, 0); vertices.addVertex(target.width, target.height, 0);
        vertices.addVertex(target.width, 0, 0); vertices.addVertex(0, 0, 0);
        BufferUploader.drawWithShader(vertices.buildOrThrow());
        shader.safeGetUniform("AtmospherePass").set(0f);
        RenderSystem.disableBlend();
        return snapshot(target);
    }

    private static NativeImage snapshot(TextureTarget target) {
        var image = new NativeImage(target.width, target.height, false);
        RenderSystem.bindTexture(target.getColorTextureId());
        image.downloadTexture(0, false);
        image.flipY();
        return image;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
