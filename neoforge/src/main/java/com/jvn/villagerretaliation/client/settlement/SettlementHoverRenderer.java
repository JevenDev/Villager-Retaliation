package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.VillagerRetaliation;
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
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

public final class SettlementHoverRenderer {
    private static ShaderInstance shader;
    private RenderTarget depth;

    public static void registerShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    VillagerRetaliation.id("settlement_hover"), DefaultVertexFormat.POSITION), loaded -> shader = loaded);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to register settlement hover shader", exception);
        }
    }

    void render(RenderLevelStageEvent event, Vec3 cursor) {
        if (shader == null) return;
        var target = Minecraft.getInstance().getMainRenderTarget();
        if (this.depth == null) this.depth = new TextureTarget(target.width, target.height, true, Minecraft.ON_OSX);
        else if (this.depth.width != target.width || this.depth.height != target.height)
            this.depth.resize(target.width, target.height, Minecraft.ON_OSX);
        this.depth.copyDepthFrom(target);
        target.bindWrite(false);

        // keep the grid anchored to blocks without losing precision far from spawn
        var origin = BlockPos.containing(cursor);
        var camera = event.getCamera().getPosition().subtract(Vec3.atLowerCornerOf(origin));
        var local = cursor.subtract(Vec3.atLowerCornerOf(origin));
        shader.setSampler("SceneDepth", this.depth.getDepthTextureId());
        shader.safeGetUniform("InverseViewProjection").set(new Matrix4f(event.getProjectionMatrix()).mul(event.getModelViewMatrix()).invert());
        shader.safeGetUniform("CameraOffset").set((float) camera.x, (float) camera.y, (float) camera.z);
        shader.safeGetUniform("Cursor").set((float) local.x, (float) local.y, (float) local.z);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        try {
            RenderSystem.setShader(() -> shader);
            var vertices = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
            vertices.addVertex(-1, -1, 0);
            vertices.addVertex(1, -1, 0);
            vertices.addVertex(1, 1, 0);
            vertices.addVertex(-1, 1, 0);
            BufferUploader.drawWithShader(vertices.buildOrThrow());
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
        }
    }

    void clear() {
        if (this.depth != null) {
            this.depth.destroyBuffers();
            this.depth = null;
        }
    }
}
