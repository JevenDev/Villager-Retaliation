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
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
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
            try (var still = render(graphics, target, shader, -1, 0, false, MapLighting.DAY);
                 var repeated = render(graphics, target, shader, -1, 0, false, MapLighting.DAY);
                 var white = render(graphics, target, shader, -1, 0, true, MapLighting.DAY);
                 var night = render(graphics, target, shader, -1, 0, false, MapLighting.at(0.5, true))) {
                int visible = 0, clear = 0, strongest = 0;
                long total = 0, daylight = 0, moonlight = 0;
                for (int y = 0; y < target.height; y++) for (int x = 0; x < target.width; x++) {
                    int color = still.getPixelRGBA(x, y), alpha = color >>> 24;
                    require(color == repeated.getPixelRGBA(x, y), "frozen atmosphere offsets produce identical clouds");
                    if (alpha > 3) visible++;
                    if (alpha == 0) clear++;
                    strongest = Math.max(strongest, alpha);
                    total += alpha;
                    daylight += color & 255;
                    moonlight += night.getPixelRGBA(x, y) & 255;
                    require((white.getPixelRGBA(x, y) & 255) - (color & 255) >= 140,
                            "even overlapping clouds preserve more than half of the terrain contrast");
                }
                int pixels = target.width * target.height;
                require(visible > pixels / 10 && clear > pixels / 100, "separate wisps leave clear gaps across the map");
                require(strongest > 30 && strongest < 115 && total < (long) pixels * 64,
                        "three cloud layers stay transparent (peak alpha: " + strongest + ", average alpha: " + total / pixels + ")");
                require(moonlight < daylight * 0.8, "atmospheric clouds follow the map's night lighting");
                var output = Path.of(System.getProperty("villagerretaliation.mapLocationClientSmoke"));
                still.writeToFile(output.resolve("map-atmosphere-transparent.png"));
                for (int layer = 0; layer < 3; layer++) {
                    try (var moving = render(graphics, target, shader, layer, 0.5f, false, MapLighting.DAY);
                         var wrapped = render(graphics, target, shader, layer, 32f, false, MapLighting.DAY)) {
                        int changed = 0;
                        for (int y = 0; y < target.height; y++) for (int x = 0; x < target.width; x++) {
                            int color = still.getPixelRGBA(x, y), next = moving.getPixelRGBA(x, y);
                            if (Math.abs((color >>> 24) - (next >>> 24)) > 1) changed++;
                            int wrap = wrapped.getPixelRGBA(x, y);
                            for (int bit = 0; bit < 32; bit += 8)
                                require(Math.abs((color >>> bit & 255) - (wrap >>> bit & 255)) <= 1,
                                        "each cloud layer remains continuous when its independent wind wraps");
                        }
                        require(changed > pixels / 100, "each of the three atmospheric layers moves independently");
                        moving.writeToFile(output.resolve("map-atmosphere-layer-" + layer + ".png"));
                    }
                }
            }
            try (var reference = render(graphics, target, shader, -1, 0, false, MapLighting.DAY)) {
                verifyNavigation(graphics, target, shader, reference);
                verifyTexels(graphics, target, shader);
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
            System.out.println("Map atmosphere checks passed: transparency, three independent layers, terrain texel locking at every LOD, panning, zoom, world and wind wraps, night lighting and classic map toggle");
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
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

    private static void verifyNavigation(GuiGraphics graphics, TextureTarget target, ShaderInstance shader, NativeImage reference) {
        var original = new MapView(target.width, target.height, 0, 0, 1);
        for (int origin : new int[]{0, 24576, -24576}) {
            var view = new MapView(target.width, target.height, origin + 8, origin - 6, 1);
            try (var panned = render(graphics, target, shader, -1, 0, false, MapLighting.DAY, view)) {
                for (int y = 6; y < target.height; y++) for (int x = 0; x < target.width - 8; x++)
                    sameColor(reference.getPixelRGBA(x + 8, y - 6), panned.getPixelRGBA(x, y),
                            "panning moves atmospheric clouds with terrain across positive and negative coordinate wraps");
            }
        }
        for (double scale : new double[]{1.25, 1.5}) {
            var view = new MapView(target.width, target.height, 0, 0, scale);
            try (var zoomed = render(graphics, target, shader, -1, 0, false, MapLighting.DAY, view)) {
                int compared = 0;
                int stride = 1 << view.level();
                for (int y = 0; y < target.height; y++) for (int x = 0; x < target.width; x++) {
                    int rx = (int) Math.floor(original.screenX((Math.floor(view.worldX(x + 0.5) / stride) + 0.5) * stride));
                    int ry = (int) Math.floor(original.screenY((Math.floor(view.worldZ(y + 0.5) / stride) + 0.5) * stride));
                    if (rx < 0 || ry < 0 || rx >= target.width || ry >= target.height) continue;
                    sameColor(reference.getPixelRGBA(rx, ry), zoomed.getPixelRGBA(x, y),
                            "zoom within a terrain LOD preserves cloud shapes at the same world positions");
                    compared++;
                }
                require(compared > target.width * target.height / 10, "navigation checks compare a substantial part of the map");
            }
        }
    }

    private static void verifyTexels(GuiGraphics graphics, TextureTarget target, ShaderInstance shader) {
        for (double scale : new double[]{0.125, 0.5, 1, 2, 4, 24}) {
            var view = new MapView(target.width, target.height, 0.25, -0.75, scale);
            int stride = 1 << view.level();
            for (float wind : new float[]{0, 0.5f}) {
                try (var image = render(graphics, target, shader, 0, wind, false, MapLighting.DAY, view)) {
                    int compared = 0;
                    for (int y = 0; y < target.height; y++) for (int x = 0; x < target.width; x++) {
                        int cx = (int) Math.floor(view.screenX((Math.floor(view.worldX(x + 0.5) / stride) + 0.5) * stride));
                        int cy = (int) Math.floor(view.screenY((Math.floor(view.worldZ(y + 0.5) / stride) + 0.5) * stride));
                        if (cx < 0 || cy < 0 || cx >= target.width || cy >= target.height) continue;
                        require(image.getPixelRGBA(x, y) == image.getPixelRGBA(cx, cy),
                                "all three cloud layers share one color and opacity across each terrain texel, including during wind motion");
                        compared++;
                    }
                    require(compared > target.width * target.height / 2, "texel locking is checked across the visible map");
                }
            }
        }
    }

    private static void sameColor(int expected, int actual, String message) {
        for (int bit = 0; bit < 32; bit += 8)
            require(Math.abs((expected >>> bit & 255) - (actual >>> bit & 255)) <= 1, message);
    }

    private static NativeImage render(GuiGraphics graphics, TextureTarget target, ShaderInstance shader,
                                      int layer, float drift, boolean white, MapLighting lighting) {
        return render(graphics, target, shader, layer, drift, white, lighting, new MapView(target.width, target.height, 0, 0, 1));
    }

    private static NativeImage render(GuiGraphics graphics, TextureTarget target, ShaderInstance shader,
                                      int layer, float drift, boolean white, MapLighting lighting, MapView view) {
        target.bindWrite(true);
        MapFogRenderer.renderAtmosphere(graphics, view, lighting);
        target.setClearColor(white ? 1 : 0, white ? 1 : 0, white ? 1 : 0, white ? 1 : 0);
        target.clear(Minecraft.ON_OSX);
        target.bindWrite(true);
        shader.safeGetUniform("AtmospherePass").set(1f);
        shader.safeGetUniform("AtmosphereDriftX").set(layer == 0 ? drift : 0, layer == 1 ? drift : 0, layer == 2 ? drift : 0);
        shader.safeGetUniform("AtmosphereDriftZ").set(layer == 0 ? -drift : 0, layer == 1 ? -drift : 0, layer == 2 ? -drift : 0);
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
