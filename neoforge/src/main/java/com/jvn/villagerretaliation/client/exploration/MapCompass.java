package com.jvn.villagerretaliation.client.exploration;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.io.IOException;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
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
    private static final String[] DIRECTIONS = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
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

    static boolean hovered(int width, int height, double x, double y) { return hovered(width, height, x, y, 0, 0); }

    static boolean hovered(int width, int height, double x, double y, float offsetX, float offsetY) {
        double dx = x - (width - PADDING - SIZE / 2.0 + offsetX), dy = y - (height - PADDING - SIZE / 2.0 + offsetY);
        return dx * dx + dy * dy < SIZE * SIZE / 4.0;
    }

    static String direction(float yaw) {
        return DIRECTIONS[Math.floorMod(Mth.floor((Mth.wrapDegrees(yaw) + 22.5f) / 45), DIRECTIONS.length)];
    }

    static List<Component> tooltip(Player player, float yaw) {
        return List.of(player.getName(), MapLayers.text("compass_coordinates", player.getBlockX(), player.getBlockY(), player.getBlockZ()),
                MapLayers.text("facing", direction(yaw)));
    }

    static void render(GuiGraphics graphics, int width, int height, float yaw, float opacity) {
        render(graphics, width, height, yaw, opacity, 0, 0);
    }

    static void render(GuiGraphics graphics, int width, int height, float yaw, float opacity, float offsetX, float offsetY) {
        if (shader == null || opacity <= 0) return;
        float left = width - SIZE - PADDING + offsetX, top = height - SIZE - PADDING + offsetY;
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
