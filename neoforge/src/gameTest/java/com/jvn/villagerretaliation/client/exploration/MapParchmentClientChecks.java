package com.jvn.villagerretaliation.client.exploration;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.exploration.MapFlatTile;
import com.jvn.villagerretaliation.exploration.MapLighting;
import com.jvn.villagerretaliation.exploration.MapView;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.HashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.lwjgl.opengl.GL11;

final class MapParchmentClientChecks {
    private static final int[] COLORS = {0x00308A48, 0x00B25A2A, 0x00808080, 0x00E0F2FF};

    private MapParchmentClientChecks() { }

    static void verify(ExploredMapScreen screen) {
        var minecraft = Minecraft.getInstance();
        var graphics = new GuiGraphics(minecraft, minecraft.renderBuffers().bufferSource());
        graphics.flush();
        var previousShader = RenderSystem.getShader();
        var textures = new int[6];
        for (int index = 0; index < textures.length; index++) textures[index] = RenderSystem.getShaderTexture(index);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean originalSetting = VillagerRetaliationConfig.PARCHMENT_MAP_COLORS.get();
        var target = new TextureTarget(screen.width, screen.height, false, Minecraft.ON_OSX);
        try (var terrain = new DynamicTexture(8, 8, true);
             var masks = new DynamicTexture(16, 8, true);
             var pages = new DynamicTexture(64, 256, true);
             var clouds = new DynamicTexture(8, 8, true);
             var regions = new DynamicTexture(8, 16, true)) {
            for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
                terrain.getPixels().setPixelRGBA(x, y, COLORS[x / 2]);
                masks.getPixels().setPixelRGBA(x * 2, y, 255);
            }
            for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) pages.getPixels().setPixelRGBA(x, y, 1);
            terrain.upload(); masks.upload(); pages.upload(); clouds.upload(); regions.upload();
            var field = MapFogRenderer.class.getDeclaredField("shader");
            field.setAccessible(true);
            var shader = (ShaderInstance) field.get(null);
            require(shader != null, "the map shader is loaded");
            var fixture = new int[]{terrain.getId(), clouds.getId(), masks.getId(), pages.getId(), regions.getId()};
            try (var original = render(graphics, target, shader, fixture, false, MapLighting.DAY, false);
                 var styled = render(graphics, target, shader, fixture, true, MapLighting.DAY, false);
                 var restored = render(graphics, target, shader, fixture, false, MapLighting.DAY, false);
                 var night = render(graphics, target, shader, fixture, true, MapLighting.at(0.5, true), false);
                 var fog = render(graphics, target, shader, fixture, true, MapLighting.DAY, true)) {
                int minimum = 255, maximum = 0;
                long daylight = 0, moonlight = 0;
                for (int y = 0; y < target.height; y++) for (int x = 0; x < target.width; x++) {
                    int color = original.getPixelRGBA(x, y);
                    require(color == (COLORS[x * COLORS.length / target.width] | 0xFF000000),
                            "disabling parchment preserves the original terrain colors exactly");
                    require(restored.getPixelRGBA(x, y) == color, "the config toggle restores colors immediately without reloading tiles");
                    int paper = styled.getPixelRGBA(x, y);
                    warm(paper);
                    warm(fog.getPixelRGBA(x, y));
                    daylight += brightness(paper);
                    moonlight += brightness(night.getPixelRGBA(x, y));
                    if (x > target.width / 2 && x < target.width * 3 / 4) {
                        minimum = Math.min(minimum, paper & 255);
                        maximum = Math.max(maximum, paper & 255);
                    }
                }
                int middle = target.height / 2;
                int land = brightness(styled.getPixelRGBA(target.width / 8, middle));
                int water = brightness(styled.getPixelRGBA(target.width * 3 / 8, middle));
                int stone = brightness(styled.getPixelRGBA(target.width * 5 / 8, middle));
                int highlights = brightness(styled.getPixelRGBA(target.width * 7 / 8, middle));
                require(water - land > 120, "pale waterways stand out against the darker land wash");
                require(highlights - stone > 180, "the ink and paper palette preserves strong terrain contrast");
                int coast = brightness(styled.getPixelRGBA(target.width / 4, middle));
                int interior = brightness(styled.getPixelRGBA(target.width / 4 + 4, middle));
                require(Math.abs(interior - coast) < 20, "material boundaries do not add dark pixel outlines");
                require(maximum - minimum >= 2 && maximum - minimum <= 8, "paper texture stays subtle on flat terrain");
                require(moonlight < daylight * 0.8 && moonlight > daylight * 0.2, "the parchment map retains readable day and night lighting");
                var output = Path.of(System.getProperty("villagerretaliation.mapLocationClientSmoke"));
                original.writeToFile(output.resolve("map-original-palette.png"));
                styled.writeToFile(output.resolve("map-parchment-palette.png"));
            }
            for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
                int shade = y < 3 ? 180 : y < 6 ? 220 : 255;
                terrain.getPixels().setPixelRGBA(x, y, MapFlatTile.shade(0x00FF4040, shade));
            }
            terrain.upload();
            try (var water = render(graphics, target, shader, fixture, true, MapLighting.DAY, false)) {
                int deep = brightness(water.getPixelRGBA(target.width / 2, target.height / 8));
                int shallow = brightness(water.getPixelRGBA(target.width / 2, target.height * 7 / 8));
                require(shallow - deep > 35, "water retains its depth shading instead of becoming a flat paper fill");
                water.writeToFile(Path.of(System.getProperty("villagerretaliation.mapLocationClientSmoke"), "map-parchment-water.png"));
            }
            verifyClouds(graphics, target, shader, fixture, terrain, masks, pages, clouds, regions);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        } finally {
            VillagerRetaliationConfig.PARCHMENT_MAP_COLORS.set(originalSetting);
            target.destroyBuffers();
            minecraft.getMainRenderTarget().bindWrite(true);
            for (int index = 0; index < textures.length; index++) RenderSystem.setShaderTexture(index, textures[index]);
            RenderSystem.setShader(() -> previousShader);
            if (blend) RenderSystem.enableBlend();
            else RenderSystem.disableBlend();
            if (depth) RenderSystem.enableDepthTest();
            else RenderSystem.disableDepthTest();
        }
    }

    private static void verifyClouds(GuiGraphics graphics, TextureTarget target, ShaderInstance shader, int[] fixture,
                                     DynamicTexture terrain, DynamicTexture masks, DynamicTexture pages,
                                     DynamicTexture clouds, DynamicTexture regions) throws IOException {
        fillTerrain(terrain, COLORS[0]);
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
            clouds.getPixels().setPixelRGBA(x, y, x < 4 ? 0xFFFFFFFF : 0);
            regions.getPixels().setPixelRGBA(x, y + 8, 8 | 8 << 16);
        }
        clouds.upload(); regions.upload();
        var output = Path.of(System.getProperty("villagerretaliation.mapLocationClientSmoke"));
        try (var clear = renderClouds(graphics, target, shader, fixture, true, 0)) {
            fillPages(pages, 32);
            try (var fringe = renderClouds(graphics, target, shader, fixture, true, 0);
                 var moving = renderClouds(graphics, target, shader, fixture, true, 96);
                 var classic = renderClouds(graphics, target, shader, fixture, false, 0);
                 var classicMoving = renderClouds(graphics, target, shader, fixture, false, 96);
                 var cloudsOnly = render(graphics, target, shader, fixture, true, MapLighting.DAY, false, loaded -> configureClouds(loaded, 0),
                         loaded -> loaded.safeGetUniform("FirstLevel").set(4f))) {
                fringe.writeToFile(output.resolve("map-discovery-clouds.png"));
                moving.writeToFile(output.resolve("map-discovery-clouds-wind.png"));
                classic.writeToFile(output.resolve("map-vanilla-clouds.png"));
                int edge = target.width / 2;
                int terrainPixels = 0, cloudPixels = 0, moved = 0, classicMoved = 0, cloudEdges = 0;
                int[] ranks = new int[16], samples = new int[16];
                var shades = new HashSet<Integer>();
                int darkest = 765, lightest = 0;
                for (int y = 0; y < target.height; y++) for (int x = 0; x < target.width; x++) {
                    int color = fringe.getPixelRGBA(x, y);
                    if (x < edge - 36) require(color == clear.getPixelRGBA(x, y), "clouds leave the explored interior clear");
                    if (x >= edge - 36 && x < edge) {
                        samples[ditherRank(x, y, target.width, target.height)]++;
                        boolean terrainPixel = color == clear.getPixelRGBA(x, y);
                        require(terrainPixel || color == cloudsOnly.getPixelRGBA(x, y), "the cloud edge dithers crisp pixels without blurring their colors");
                        if (terrainPixel) terrainPixels++;
                        else {
                            cloudPixels++;
                            ranks[ditherRank(x, y, target.width, target.height)]++;
                        }
                    }
                    if (x >= edge) {
                        shades.add(color);
                        if (x > edge && Math.abs(brightness(color) - brightness(fringe.getPixelRGBA(x - 1, y))) > 16) cloudEdges++;
                        if (color != moving.getPixelRGBA(x, y)) moved++;
                        if (classic.getPixelRGBA(x, y) != classicMoving.getPixelRGBA(x, y)) classicMoved++;
                        darkest = Math.min(darkest, brightness(color));
                        lightest = Math.max(lightest, brightness(color));
                    }
                }
                require(terrainPixels > target.height && cloudPixels > target.height, "the discovery edge transitions through visible terrain and clouds");
                int lower = ranks[0] + ranks[4] + ranks[1] + ranks[5];
                int upper = ranks[15] + ranks[11] + ranks[14] + ranks[10];
                int lowerSamples = samples[0] + samples[4] + samples[1] + samples[5];
                int upperSamples = samples[15] + samples[11] + samples[14] + samples[10];
                require(lower / (double) lowerSamples > upper / (double) upperSamples + 0.05,
                        "ordered dither ranks fade gradually within each terrain texel column");
                require(shades.size() > 12, "irregular cloud banks retain internal shading detail");
                require(cloudEdges > target.height / 4, "cloud banks have defined irregular edges instead of a blurred wash");
                require(moved > target.width * target.height / 20, "parchment cloud billows move with the wind");
                require(classicMoved > target.height, "disabling parchment restores the moving vanilla cloud silhouettes");
                require(lightest - darkest > 45, "cloud shadows and highlights remain visible against the parchment");
                fillTerrain(terrain, 0x00FF00FF);
                try (var hidden = renderClouds(graphics, target, shader, fixture, true, 0)) {
                    for (int y = 0; y < target.height; y++) for (int x = edge; x < target.width; x++)
                        require(hidden.getPixelRGBA(x, y) == fringe.getPixelRGBA(x, y), "clouds never reveal cached terrain outside discovery");
                }
                fillTerrain(terrain, COLORS[0]);
                fillPages(pages, 34);
                try (var expanded = renderClouds(graphics, target, shader, fixture, true, 0)) {
                    for (int y = 0; y < target.height; y++)
                        require(expanded.getPixelRGBA(edge + 20, y) == clear.getPixelRGBA(edge + 20, y),
                                "the cloud fringe follows newly discovered terrain immediately");
                }
                for (int y = 0; y < 8; y++) masks.getPixels().setPixelRGBA(8, y, 0);
                masks.upload();
                try (var partial = renderClouds(graphics, target, shader, fixture, true, 0)) {
                    int hole = edge + Math.round(target.width * 4.5F / 128);
                    require(partial.getPixelRGBA(hole, target.height / 2) != clear.getPixelRGBA(hole, target.height / 2),
                            "unknown columns stay clouded inside partially discovered tiles");
                }
                for (int y = 0; y < 8; y++) masks.getPixels().setPixelRGBA(8, y, 255);
                masks.upload();
                for (int y = 32; y < 64; y++) for (int x = 0; x < 64; x++) pages.getPixels().setPixelRGBA(x, y, 0);
                pages.upload();
                try (var horizontal = renderClouds(graphics, target, shader, fixture, true, 0)) {
                    int x = target.width / 4;
                    require(horizontal.getPixelRGBA(x, target.height / 4) == clear.getPixelRGBA(x, target.height / 4),
                            "the discovery mask keeps its vertical orientation when sampled from a framebuffer");
                    require(horizontal.getPixelRGBA(x, target.height * 3 / 4) != clear.getPixelRGBA(x, target.height * 3 / 4),
                            "clouds follow horizontal discovery boundaries");
                }
            }
        }
        try (var coarse = render(graphics, target, shader, fixture, true, MapLighting.DAY, true, loaded -> {
            configureClouds(loaded, 0);
            loaded.safeGetUniform("TerrainTexelSize").set(4f);
        })) {
            coarse.writeToFile(output.resolve("map-clouds-coarse.png"));
            int boundaries = 0;
            for (int y = 0; y < target.height; y++) for (int x = 1; x < target.width; x++) {
                int previous = (int) Math.floor(((x - 0.5) * 128 / target.width - 64) / 4);
                int current = (int) Math.floor(((x + 0.5) * 128 / target.width - 64) / 4);
                int color = coarse.getPixelRGBA(x, y), neighbor = coarse.getPixelRGBA(x - 1, y);
                if (previous == current) {
                    for (int shift = 0; shift < 24; shift += 8)
                        require(Math.abs((color >> shift & 255) - (neighbor >> shift & 255)) <= 3,
                                "cloud shapes use the terrain texel size at coarse map levels");
                } else if (Math.abs(brightness(color) - brightness(neighbor)) > 16) boundaries++;
            }
            require(boundaries > target.height / 4, "coarse cloud banks retain visible shading on their texel boundaries");
        }
        for (float progress : new float[]{0.28F, 0.68F}) {
            try (var vanilla = renderTransition(graphics, target, shader, false, progress);
                 var parchment = renderTransition(graphics, target, shader, true, progress)) {
                int visible = 0;
                for (int y = 0; y < target.height; y++) for (int x = 0; x < target.width; x++) {
                    int color = vanilla.getPixelRGBA(x, y);
                    require(parchment.getPixelRGBA(x, y) == color, "opening and closing use identical vanilla clouds in both map modes");
                    if (color >>> 24 == 0) continue;
                    visible++;
                    require((color & 255) == (color >>> 8 & 255) && (color >>> 8 & 255) == (color >>> 16 & 255),
                            "transition clouds keep their vanilla colors");
                }
                require(visible > 100, "the vanilla cloud transition renders visible clouds");
            }
        }
    }

    private static int ditherRank(int x, int y, int width, int height) {
        int worldX = (int) Math.floor((x + 0.5) * 128 / width - 64);
        int worldZ = (int) Math.floor((y + 0.5) * 128 / height - 64);
        int low = ((worldX & 1) ^ (worldZ & 1)) * 2 + (worldZ & 1);
        int high = (((worldX >> 1) & 1) ^ ((worldZ >> 1) & 1)) * 2 + ((worldZ >> 1) & 1);
        return low * 4 + high;
    }

    private static void fillTerrain(DynamicTexture terrain, int color) {
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) terrain.getPixels().setPixelRGBA(x, y, color);
        terrain.upload();
    }

    private static void fillPages(DynamicTexture pages, int discovered) {
        for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) pages.getPixels().setPixelRGBA(x, y, x < discovered ? 1 : 0);
        pages.upload();
    }

    private static NativeImage renderClouds(GuiGraphics graphics, TextureTarget target, ShaderInstance shader, int[] textures,
                                            boolean parchment, float wind) {
        return render(graphics, target, shader, textures, parchment, MapLighting.DAY, false, loaded -> configureClouds(loaded, wind));
    }

    private static void configureClouds(ShaderInstance loaded, float wind) {
        loaded.safeGetUniform("WorldSpan").set(128f, 128f);
        loaded.safeGetUniform("TerrainTexelSize").set(1f);
        loaded.safeGetUniform("PageX").set(-256f, -256f, -256f, -256f);
        loaded.safeGetUniform("PageZ").set(-256f, -256f, -256f, -256f);
        loaded.safeGetUniform("CloudPeriod").set(384f);
        loaded.safeGetUniform("CloudOrigin").set(0f, 0f);
        loaded.safeGetUniform("CloudDriftX").set(wind, wind * 0.425F, wind * 0.175F);
        loaded.safeGetUniform("CloudDriftZ").set(wind * 0.3F, wind * 0.1275F, wind * 0.0525F);
    }

    private static NativeImage renderTransition(GuiGraphics graphics, TextureTarget target, ShaderInstance shader,
                                                boolean parchment, float progress) {
        target.setClearColor(0, 0, 0, 0);
        target.clear(Minecraft.ON_OSX);
        target.bindWrite(true);
        VillagerRetaliationConfig.PARCHMENT_MAP_COLORS.set(parchment);
        shader.safeGetUniform("ParchmentColors").set(parchment ? 1f : 0f);
        MapFogRenderer.renderOpening(graphics, target.width, target.height, MapLighting.DAY, progress);
        return snapshot(target);
    }

    private static NativeImage render(GuiGraphics graphics, TextureTarget target, ShaderInstance shader, int[] textures,
                                      boolean parchment, MapLighting lighting, boolean fog) {
        return render(graphics, target, shader, textures, parchment, lighting, fog, ignored -> { });
    }

    private static NativeImage render(GuiGraphics graphics, TextureTarget target, ShaderInstance shader, int[] textures,
                                      boolean parchment, MapLighting lighting, boolean fog, Consumer<ShaderInstance> configure) {
        return render(graphics, target, shader, textures, parchment, lighting, fog, configure, ignored -> { });
    }

    private static NativeImage render(GuiGraphics graphics, TextureTarget target, ShaderInstance shader, int[] textures,
                                      boolean parchment, MapLighting lighting, boolean fog, Consumer<ShaderInstance> configure,
                                      Consumer<ShaderInstance> beforeDraw) {
        target.bindWrite(true);
        VillagerRetaliationConfig.PARCHMENT_MAP_COLORS.set(parchment);
        MapFogRenderer.render(graphics, new MapView(target.width, target.height, 0, 0, 1), null, null, lighting);
        shader.safeGetUniform("WorldSpan").set(8f, 8f);
        shader.safeGetUniform("PageX").set(-260f, -260f, -260f, -260f);
        shader.safeGetUniform("PageZ").set(-260f, -260f, -260f, -260f);
        shader.safeGetUniform("FirstLevel").set(fog ? 4f : 0f);
        shader.safeGetUniform("CanvasOrigin").set(0f, 0f);
        shader.safeGetUniform("CloudDriftX").set(0f, 0f, 0f);
        shader.safeGetUniform("CloudDriftZ").set(0f, 0f, 0f);
        configure.accept(shader);
        for (int index = 0; index < textures.length; index++) RenderSystem.setShaderTexture(index, textures[index]);
        try (var mask = new MapTileView()) {
            if (parchment) MapFogRenderer.renderMask(mask, target.width, target.height);
            beforeDraw.accept(shader);
            RenderSystem.setShader(() -> shader);
            var pose = graphics.pose().last().pose();
            var vertices = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
            vertices.addVertex(pose, 0, target.height, 0);
            vertices.addVertex(pose, target.width, target.height, 0);
            vertices.addVertex(pose, target.width, 0, 0);
            vertices.addVertex(pose, 0, 0, 0);
            BufferUploader.drawWithShader(vertices.buildOrThrow());
            return snapshot(target);
        }
    }

    private static NativeImage snapshot(TextureTarget target) {
        var image = new NativeImage(target.width, target.height, false);
        try {
            RenderSystem.bindTexture(target.getColorTextureId());
            image.downloadTexture(0, false);
            image.flipY();
            return image;
        } catch (RuntimeException | Error exception) {
            image.close();
            throw exception;
        }
    }

    private static void warm(int color) {
        require((color & 255) > (color >>> 8 & 255) && (color >>> 8 & 255) > (color >>> 16 & 255),
                "terrain and unexplored areas use the warm parchment palette");
    }

    private static int brightness(int color) { return (color & 255) + (color >>> 8 & 255) + (color >>> 16 & 255); }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
