package com.jvn.villagerretaliation.client.exploration;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.exploration.MapLighting;
import com.jvn.villagerretaliation.exploration.MapView;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.io.IOException;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

public final class MapFogRenderer {
    private static final ResourceLocation CLOUDS = ResourceLocation.withDefaultNamespace("textures/environment/clouds.png");
    private static ShaderInstance shader;
    private MapFogRenderer() { }
    public static void registerShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), VillagerRetaliation.id("map_fog"),
                    DefaultVertexFormat.POSITION), loaded -> shader = loaded);
        } catch (IOException e) { throw new IllegalStateException("Failed to register map shader", e); }
    }
    static void render(GuiGraphics graphics, MapView view, MapTileView pages, MapTileCache cache, MapLighting lighting) {
        if (shader == null) return;
        graphics.flush();
        shader.safeGetUniform("Viewport").set((float)view.width(), (float)view.height());
        shader.safeGetUniform("WorldSpan").set((float)(view.width() / view.scale()), (float)(view.height() / view.scale()));
        if (pages != null) {
            // subtract world coordinates in double precision before sending local offsets to the GPU
            shader.safeGetUniform("PageX").set((float)(pages.originX(0) - view.x()), (float)(pages.originX(1) - view.x()),
                    (float)(pages.originX(2) - view.x()), (float)(pages.originX(3) - view.x()));
            shader.safeGetUniform("PageZ").set((float)(pages.originZ(0) - view.z()), (float)(pages.originZ(1) - view.z()),
                    (float)(pages.originZ(2) - view.z()), (float)(pages.originZ(3) - view.z()));
        }
        shader.safeGetUniform("FirstLevel").set(pages == null ? 4f : (float)view.level());
        double period = 512;
        shader.safeGetUniform("CloudPeriod").set((float)period);
        shader.safeGetUniform("CloudOrigin").set(wrap(view.x(), period * 4), wrap(view.z(), period * 4));
        shader.safeGetUniform("Lighting").set(lighting.red(), lighting.green(), lighting.blue());
        RenderSystem.setShaderTexture(1, CLOUDS);
        if (pages == null) {
            RenderSystem.setShaderTexture(0, CLOUDS); RenderSystem.setShaderTexture(2, CLOUDS); RenderSystem.setShaderTexture(3, CLOUDS);
        } else {
            RenderSystem.setShaderTexture(0, cache.terrainId()); RenderSystem.setShaderTexture(2, cache.maskId()); RenderSystem.setShaderTexture(3, pages.pageId());
        }
        RenderSystem.disableBlend(); RenderSystem.setShader(() -> shader);
        var pose = graphics.pose().last().pose();
        shader.safeGetUniform("CanvasOrigin").set(pose.m30(), pose.m31());
        var vertices = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        vertices.addVertex(pose, 0, view.height(), 0); vertices.addVertex(pose, view.width(), view.height(), 0);
        vertices.addVertex(pose, view.width(), 0, 0); vertices.addVertex(pose, 0, 0, 0);
        BufferUploader.drawWithShader(vertices.buildOrThrow());
    }
    private static float wrap(double value, double period) { return (float)(value - Math.floor(value / period) * period); }
}
