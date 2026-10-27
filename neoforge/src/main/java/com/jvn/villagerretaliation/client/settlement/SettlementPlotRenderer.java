package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.network.SettlementPlotSnapshot;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

public final class SettlementPlotRenderer {
    private static ShaderInstance shader;
    private static RenderTarget depth;

    private SettlementPlotRenderer() {
    }

    public static void registerShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    VillagerRetaliation.id("settlement_plots"), DefaultVertexFormat.POSITION), loaded -> shader = loaded);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to register settlement plot shader", exception);
        }
    }

    static void render(RenderLevelStageEvent event, SettlementPlotSnapshot snapshot, ChunkPos selected, ChunkPos hovered) {
        render(event, snapshot, selected, hovered, false);
    }

    static void render(RenderLevelStageEvent event, SettlementPlotSnapshot snapshot, ChunkPos selected, ChunkPos hovered, boolean outlineOnly) {
        if (shader == null || snapshot.plots().isEmpty()) return;
        var minecraft = Minecraft.getInstance();
        var target = minecraft.getMainRenderTarget();
        if (depth == null) depth = new TextureTarget(target.width, target.height, true, Minecraft.ON_OSX);
        else if (depth.width != target.width || depth.height != target.height) depth.resize(target.width, target.height, Minecraft.ON_OSX);
        depth.copyDepthFrom(target);
        target.bindWrite(false);

        int minX = snapshot.plots().stream().mapToInt(plot -> plot.chunk().x).min().orElseThrow();
        int minZ = snapshot.plots().stream().mapToInt(plot -> plot.chunk().z).min().orElseThrow();
        var states = new float[121];
        for (var plot : snapshot.plots()) {
            if (outlineOnly && !plot.owned()) continue;
            int x = plot.chunk().x - minX, z = plot.chunk().z - minZ;
            if (x < 0 || x >= 11 || z < 0 || z >= 11 || !plot.owned() && !plot.eligible()
                    || minecraft.level.getChunkSource().getChunk(plot.chunk().x, plot.chunk().z, false) == null) continue;
            states[x + z * 11] = plot.chunk().equals(hovered) ? 4 : plot.chunk().equals(selected) ? 3 : plot.owned() ? 1 : 2;
        }
        var camera = event.getCamera().getPosition();
        shader.setSampler("SceneDepth", depth.getDepthTextureId());
        shader.safeGetUniform("InverseViewProjection").set(new Matrix4f(event.getProjectionMatrix()).mul(event.getModelViewMatrix()).invert());
        shader.safeGetUniform("CameraOffset").set((float) (camera.x - minX * 16.0D), (float) camera.y, (float) (camera.z - minZ * 16.0D));
        shader.safeGetUniform("PlotStates").set(states);
        shader.safeGetUniform("OutlineOnly").set(outlineOnly ? 1 : 0);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(() -> shader);
        var vertices = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        vertices.addVertex(-1, -1, 0);
        vertices.addVertex(1, -1, 0);
        vertices.addVertex(1, 1, 0);
        vertices.addVertex(-1, 1, 0);
        BufferUploader.drawWithShader(vertices.buildOrThrow());
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    static void clear() {
        if (depth != null) {
            depth.destroyBuffers();
            depth = null;
        }
    }
}
