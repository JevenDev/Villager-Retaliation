package com.jvn.villagerretaliation.client.exploration;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.exploration.MapLocation;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.io.IOException;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.lwjgl.opengl.GL11;

public final class MapMarkerIcon {
    private static ShaderInstance shader;

    private MapMarkerIcon() { }

    public static void registerShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), VillagerRetaliation.id("map_marker_icon"),
                    DefaultVertexFormat.POSITION_TEX), loaded -> shader = loaded);
        } catch (IOException exception) { throw new IllegalStateException("Failed to register map marker shader", exception); }
    }

    static ResourceLocation texture(MapLocation.Icon icon) {
        return VillagerRetaliation.id("textures/gui/map_" + icon.name().toLowerCase(Locale.ROOT) + ".png");
    }

    static void render(GuiGraphics graphics, MapLocation.Icon icon, int x, int y) {
        render(graphics, icon, x, y, 16);
    }

    static void render(GuiGraphics graphics, MapLocation.Icon icon, float x, float y, float size) {
        if (icon.mapSprite()) render(graphics, texture(icon), x, y, size);
        else graphics.renderItem(MapLocationsClient.icon(icon), (int) x, (int) y);
    }

    static void render(GuiGraphics graphics, ResourceLocation texture, float x, float y, float size) {
        if (shader == null) return;
        graphics.flush();
        var previous = RenderSystem.getShader();
        int previousTexture = RenderSystem.getShaderTexture(0);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        try {
            RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
            shader.safeGetUniform("ParchmentColors").set(VillagerRetaliationConfig.PARCHMENT_MAP_COLORS.get() ? 1f : 0f);
            RenderSystem.setShaderTexture(0, texture);
            RenderSystem.setShader(() -> shader);
            var pose = graphics.pose().last().pose();
            var vertices = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            vertices.addVertex(pose, x, y + size, 0).setUv(0, 1);
            vertices.addVertex(pose, x + size, y + size, 0).setUv(1, 1);
            vertices.addVertex(pose, x + size, y, 0).setUv(1, 0);
            vertices.addVertex(pose, x, y, 0).setUv(0, 0);
            BufferUploader.drawWithShader(vertices.buildOrThrow());
        } finally {
            RenderSystem.setShaderTexture(0, previousTexture);
            RenderSystem.setShader(() -> previous);
            if (!blend) RenderSystem.disableBlend();
        }
    }
}
