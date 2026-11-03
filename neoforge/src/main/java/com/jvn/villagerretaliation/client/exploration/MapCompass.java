package com.jvn.villagerretaliation.client.exploration;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.io.IOException;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

public final class MapCompass {
    static final int SIZE = 64;
    static final int PADDING = 10;
    private static final ResourceLocation BACKGROUND = ResourceLocation.fromNamespaceAndPath("vr_explored_map", "textures/gui/compass.png");
    private static final ResourceLocation NEEDLE = ResourceLocation.fromNamespaceAndPath("vr_explored_map", "textures/gui/compass_needle.png");
    private static ShaderInstance shader;

    private MapCompass() { }

    public static void registerShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), VillagerRetaliation.id("map_compass"),
                    DefaultVertexFormat.POSITION_TEX), loaded -> shader = loaded);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to register map compass shader", exception);
        }
    }

    static void render(GuiGraphics graphics, int width, int height, float yaw, float opacity) {
        if (shader == null || opacity <= 0) return;
        int left = width - SIZE - PADDING, top = height - SIZE - PADDING;
        double angle = Math.toRadians(Mth.wrapDegrees(yaw) + 180);
        graphics.flush();
        shader.safeGetUniform("Rotation").set((float) Math.cos(angle), (float) Math.sin(angle));
        shader.safeGetUniform("Opacity").set(opacity);
        RenderSystem.setShaderTexture(0, BACKGROUND);
        RenderSystem.setShaderTexture(1, NEEDLE);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(() -> shader);
        var pose = graphics.pose().last().pose();
        var vertices = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        vertices.addVertex(pose, left, top + SIZE, 0).setUv(0, 1);
        vertices.addVertex(pose, left + SIZE, top + SIZE, 0).setUv(1, 1);
        vertices.addVertex(pose, left + SIZE, top, 0).setUv(1, 0);
        vertices.addVertex(pose, left, top, 0).setUv(0, 0);
        BufferUploader.drawWithShader(vertices.buildOrThrow());
    }
}
