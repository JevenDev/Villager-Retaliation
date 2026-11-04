package com.jvn.villagerretaliation.client.exploration;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.exploration.MapFlatTile;
import com.jvn.villagerretaliation.exploration.MapLighting;
import com.jvn.villagerretaliation.exploration.MapView;
import com.jvn.villagerretaliation.exploration.MapRepository;
import com.jvn.villagerretaliation.exploration.MapSnapshot;
import com.jvn.villagerretaliation.exploration.MapStore;
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
import java.util.function.Consumer;
import java.util.HashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.lwjgl.opengl.GL11;
import org.joml.Matrix4f;

final class MapParchmentClientChecks {
    private static final float CLOUD_SPAN = 384f;
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
            verifyWater(graphics, target, shader, fixture, terrain);
            verifyClouds(graphics, target, shader, fixture, terrain, masks, pages, clouds, regions);
            verifyCaches(graphics, target);
            verifyWideZoom(graphics);
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

    private static void verifyWater(GuiGraphics graphics, TextureTarget target, ShaderInstance shader,
                                    int[] fixture, DynamicTexture terrain) throws IOException {
        fillTerrain(terrain, 0x00FF4040);
        try (var still = renderWater(graphics, target, shader, fixture, true, 0, 0);
             var moving = renderWater(graphics, target, shader, fixture, true, 4, 0);
             var wrapped = renderWater(graphics, target, shader, fixture, true, 3072, 0);
             var distant = renderWater(graphics, target, shader, fixture, true, 0, 12288);
             var vanilla = renderWater(graphics, target, shader, fixture, false, 0, 0);
             var vanillaMoving = renderWater(graphics, target, shader, fixture, false, 4, 0)) {
            int changed = 0, strongest = 0;
            for (int y = 0; y < target.height; y++) for (int x = 0; x < target.width; x++) {
                int color = still.getPixelRGBA(x, y);
                int difference = Math.abs(brightness(color) - brightness(moving.getPixelRGBA(x, y)));
                if (difference > 3) changed++;
                strongest = Math.max(strongest, difference);
                require(Math.abs(brightness(color) - brightness(wrapped.getPixelRGBA(x, y))) <= 3,
                        "water motion remains continuous when the wind clock wraps");
                require(Math.abs(brightness(color) - brightness(distant.getPixelRGBA(x, y))) <= 3,
                        "water currents retain their world position across wrapped map coordinates");
                require(vanilla.getPixelRGBA(x, y) == vanillaMoving.getPixelRGBA(x, y), "vanilla water remains unchanged by animation");
                warm(moving.getPixelRGBA(x, y));
            }
            require(changed > target.width * target.height / 10,
                    "soft currents visibly move through the water wash");
            require(strongest > 6 && strongest < 45, "water motion keeps the paper wash subdued and readable");
            var output = Path.of(System.getProperty("villagerretaliation.mapLocationClientSmoke"));
            still.writeToFile(output.resolve("map-water-currents.png"));
            moving.writeToFile(output.resolve("map-water-currents-moving.png"));
        }
        fillTerrain(terrain, COLORS[0]);
        try (var land = renderWater(graphics, target, shader, fixture, true, 0, 0);
             var moving = renderWater(graphics, target, shader, fixture, true, 4, 0)) {
            for (int y = 0; y < target.height; y++) for (int x = 0; x < target.width; x++)
                require(land.getPixelRGBA(x, y) == moving.getPixelRGBA(x, y), "water animation leaves land and map details stationary");
        }
        System.out.println("Paper water checks passed: soft moving currents, continuous wraps, static land and unchanged vanilla water");
    }

    private static NativeImage renderWater(GuiGraphics graphics, TextureTarget target, ShaderInstance shader,
                                           int[] fixture, boolean parchment, float wind, float origin) {
        return render(graphics, target, shader, fixture, parchment, MapLighting.DAY, false, loaded -> {
            loaded.safeGetUniform("WorldSpan").set(384f, 384f * target.height / target.width);
            loaded.safeGetUniform("PageX").set(-256f, -256f, -256f, -256f);
            loaded.safeGetUniform("PageZ").set(-256f, -256f, -256f, -256f);
            loaded.safeGetUniform("CloudOrigin").set(origin, origin);
            loaded.safeGetUniform("CloudDriftX").set(wind, 0f, 0f);
        });
    }

    private static void verifyCaches(GuiGraphics graphics, TextureTarget target) throws IOException, ReflectiveOperationException {
        var output = Path.of(System.getProperty("villagerretaliation.mapLocationClientSmoke"));
        var columns = new java.util.ArrayList<MapSnapshot.Column>();
        for (int i = 0; i < 64; i++) columns.add(new MapSnapshot.Column(i, 64,
                java.util.List.of(new MapSnapshot.Block(64, "minecraft:grass_block[snowy=false]", 0x88aa44, 0x4488ff, 2)), true));
        var store = new MapStore(output.resolve("cache-fixture"), "server", "world", "player", "dimension");
        store.write(new MapSnapshot(0, 0, 1, columns, java.util.List.of()));
        try (var repository = new MapRepository(store); var cache = new MapTileCache(); var pages = new MapTileView()) {
            VillagerRetaliationConfig.PARCHMENT_MAP_COLORS.set(true);
            var view = new MapView(target.width, target.height, 4, 4, 8);
            awaitTiles(repository, cache, pages, view);
            target.bindWrite(true);
            MapFogRenderer.render(graphics, view, pages, cache, MapLighting.DAY);
            long builds = pages.fogBuilds(), uploads = pages.pageUploads();
            for (int frame = 0; frame < 20; frame++) {
                pages.update(repository, cache, view);
                MapFogRenderer.render(graphics, view, pages, cache, MapLighting.DAY);
            }
            require(pages.fogBuilds() == builds && pages.pageUploads() == uploads,
                    "stationary map frames reuse their discovery mask and page texture");
            var maskField = MapTileView.class.getDeclaredField("fogMask"); maskField.setAccessible(true);
            var before = maskPixels((TextureTarget) maskField.get(pages));
            columns.remove(18);
            require(repository.put(new MapSnapshot(0, 0, 2, columns, java.util.List.of())), "the loaded fixture accepts a discovery change");
            awaitTiles(repository, cache, pages, view);
            MapFogRenderer.render(graphics, view, pages, cache, MapLighting.DAY);
            require(pages.pageUploads() == uploads && pages.fogBuilds() == builds + 1,
                    "changed coverage rebuilds fog even when terrain atlas slots and page entries stay the same");
            var after = maskPixels((TextureTarget) maskField.get(pages));
            int changed = 0;
            for (int index = 0; index < before.length; index++)
                if (Math.abs(before[index] - after[index]) > 0.000001f) changed++;
            require(changed > 0, "the reused discovery mask reflects changed terrain coverage");
            cache.close();
            awaitTiles(repository, cache, pages, view);
            MapFogRenderer.render(graphics, view, pages, cache, MapLighting.DAY);
            require(pages.fogBuilds() == builds + 2, "recreating terrain textures invalidates cached discovery masks");
            long settledUploads = pages.pageUploads();
            pages.close();
            awaitTiles(repository, cache, pages, view);
            MapFogRenderer.render(graphics, view, pages, cache, MapLighting.DAY);
            require(pages.pageUploads() > settledUploads && pages.fogBuilds() == builds + 3,
                    "reopening a view restores its page texture and discovery mask");
            System.out.println("Map cache checks passed: 20 stationary frames required zero page uploads and zero fog rebuilds");
        }
    }

    private static void verifyWideZoom(GuiGraphics graphics) throws IOException, ReflectiveOperationException {
        var output = Path.of(System.getProperty("villagerretaliation.mapLocationClientSmoke"));
        var columns = new java.util.ArrayList<MapSnapshot.Column>();
        for (int i = 0; i < 64; i++) columns.add(new MapSnapshot.Column(i, 64,
                java.util.List.of(new MapSnapshot.Block(64, "minecraft:grass_block[snowy=false]", 0x88aa44, 0x4488ff, 2)), true));
        var store = new MapStore(output.resolve("wide-cache-fixture"), "server", "world", "player", "dimension");
        for (int z : new int[]{-30, 29}) for (int x : new int[]{-30, 29})
            for (int dz = 0; dz < 8; dz++) for (int dx = 0; dx < 8; dx++)
                store.write(new MapSnapshot(x * 8 + dx, z * 8 + dz, 1, columns, java.util.List.of()));
        var target = new TextureTarget(640, 640, false, Minecraft.ON_OSX);
        RenderSystem.backupProjectionMatrix();
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix().identity();
        RenderSystem.applyModelViewMatrix();
        try (var repository = new MapRepository(store); var cache = new MapTileCache(); var pages = new MapTileView()) {
            var navigation = new MapNavigation();
            navigation.resize(target.width, target.height);
            navigation.zoomAt(320, 320, 0);
            var view = navigation.view();
            require(MapOrientation.scaleBlocks(view.scale(), 80) == 500, "the widest rendered map reaches the 500 block scale");
            var missing = MapTileView.class.getDeclaredField("missing"); missing.setAccessible(true);
            long deadline = System.nanoTime() + 30_000_000_000L;
            boolean ready;
            do {
                repository.tick();
                ready = pages.update(repository, cache, view) && !missing.getBoolean(pages);
                if (ready) break;
                java.util.concurrent.locks.LockSupport.parkNanos(2_000_000);
            } while (System.nanoTime() < deadline);
            require(ready, "the widest square map settles without evicting visible terrain");
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, target.width, target.height, 0, -1000, 1000), VertexSorting.ORTHOGRAPHIC_Z);
            VillagerRetaliationConfig.PARCHMENT_MAP_COLORS.set(false);
            target.bindWrite(true);
            MapFogRenderer.render(graphics, view, pages, cache, MapLighting.DAY);
            try (var image = snapshot(target)) {
                image.writeToFile(output.resolve("map-500-block-scale.png"));
                for (int z : new int[]{-1888, 1888}) for (int x : new int[]{-1888, 1888}) {
                    int color = image.getPixelRGBA((int) view.screenX(x), (int) view.screenY(z));
                    require(color >>> 24 == 255 && (color >>> 8 & 255) > (color & 255)
                            && (color >>> 8 & 255) > (color >>> 16 & 255), "recorded terrain renders at " + x + ", " + z + " at the widest zoom (color " + Integer.toHexString(color) + ")");
                }
            }
            long uploads = pages.pageUploads();
            for (int frame = 0; frame < 20; frame++) {
                repository.tick();
                pages.update(repository, cache, view);
            }
            require(pages.pageUploads() == uploads && !missing.getBoolean(pages), "the 500 block view reuses its complete page table while idle");
            System.out.println("Map 500 block zoom checks passed: all corners render and stationary pages remain cached");
        } finally {
            modelView.popMatrix(); RenderSystem.applyModelViewMatrix(); RenderSystem.restoreProjectionMatrix();
            target.destroyBuffers();
        }
    }

    private static void awaitTiles(MapRepository repository, MapTileCache cache, MapTileView pages, MapView view)
            throws ReflectiveOperationException {
        var missing = MapTileView.class.getDeclaredField("missing"); missing.setAccessible(true);
        long deadline = System.nanoTime() + 10_000_000_000L;
        do {
            var detail = repository.get(0, 0);
            repository.tick();
            boolean ready = pages.update(repository, cache, view);
            var entry = cache.get(0, MapSnapshot.key(0, 0));
            if (ready && detail != null && entry != null && entry.source.revision() == detail.revision() && !missing.getBoolean(pages)) return;
            java.util.concurrent.locks.LockSupport.parkNanos(2_000_000);
        } while (System.nanoTime() < deadline);
        throw new IllegalStateException("Map cache fixture did not finish loading");
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
                    if (x < edge - target.width / 4) require(color == clear.getPixelRGBA(x, y), "clouds leave the explored interior clear");
                    if (x < edge - Math.ceil(target.width * 16 / CLOUD_SPAN) && y > target.height / 4 && y < target.height * 3 / 4)
                        require(color == clear.getPixelRGBA(x, y), "stacked cloud banks grow outward instead of covering explored terrain");
                    if (x >= edge - target.width / 4 && x < edge) {
                        boolean join = x >= edge - Math.ceil(target.width * 16 / CLOUD_SPAN);
                        if (join) samples[ditherRank(x, y, target.width, target.height)]++;
                        boolean terrainPixel = color == clear.getPixelRGBA(x, y);
                        require(terrainPixel || color == cloudsOnly.getPixelRGBA(x, y), "the cloud edge dithers crisp pixels without blurring their colors");
                        if (terrainPixel) terrainPixels++;
                        else {
                            cloudPixels++;
                            if (join) ranks[ditherRank(x, y, target.width, target.height)]++;
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
                int[] lowerRanks = {0, 4, 1, 5}, upperRanks = {15, 11, 14, 10};
                double lower = 0, upper = 0;
                for (int column = 0; column < 4; column++) {
                    lower += ranks[lowerRanks[column]] / (double) samples[lowerRanks[column]];
                    upper += ranks[upperRanks[column]] / (double) samples[upperRanks[column]];
                }
                require((lower - upper) / 4 > 0.05,
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
                fillPages(pages, 48);
                try (var expanded = renderClouds(graphics, target, shader, fixture, true, 0)) {
                    for (int y = 0; y < target.height; y++)
                        require(expanded.getPixelRGBA(edge + 20, y) == clear.getPixelRGBA(edge + 20, y),
                                "the cloud fringe follows newly discovered terrain immediately");
                }
                for (int y = 0; y < 8; y++) masks.getPixels().setPixelRGBA(8, y, 0);
                masks.upload();
                try (var partial = renderClouds(graphics, target, shader, fixture, true, 0)) {
                    int hole = edge + (int) Math.floor(target.width * 4.5 / CLOUD_SPAN);
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
        fillPages(pages, 32);
        verifyPanning(graphics, target, shader, fixture);
        verifyZoom(graphics, target, shader, fixture, masks, pages);
        verifyAnimation(graphics, target, shader, fixture);
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

    private static void verifyAnimation(GuiGraphics graphics, TextureTarget target, ShaderInstance shader, int[] fixture) throws IOException {
        try (var before = renderAnimation(graphics, target, shader, fixture, 0, false);
             var after = renderAnimation(graphics, target, shader, fixture, 2, false);
             var wrapped = renderAnimation(graphics, target, shader, fixture, 0, true)) {
            int moved = 0;
            for (int y = 0; y < target.height; y++) for (int x = 0; x < target.width; x++) {
                int original = before.getPixelRGBA(x, y), animated = after.getPixelRGBA(x, y), looped = wrapped.getPixelRGBA(x, y);
                int difference = 0;
                for (int bit = 0; bit < 24; bit += 8) {
                    difference = Math.max(difference, Math.abs((original >> bit & 255) - (animated >> bit & 255)));
                    require(Math.abs((original >> bit & 255) - (looped >> bit & 255)) <= 2,
                            "cloud layers remain continuous when their wind offsets wrap");
                }
                if (difference > 8) moved++;
            }
            var output = Path.of(System.getProperty("villagerretaliation.mapLocationClientSmoke"));
            before.writeToFile(output.resolve("map-cloud-animation-start.png"));
            after.writeToFile(output.resolve("map-cloud-animation-two-seconds.png"));
            require(moved > target.width * target.height / 50,
                    "cloud banks visibly move within two seconds at their actual world scale (changed pixels: " + moved + ")");
        }
    }

    private static NativeImage renderAnimation(GuiGraphics graphics, TextureTarget target, ShaderInstance shader, int[] fixture,
                                                float seconds, boolean wrapped) {
        return render(graphics, target, shader, fixture, true, MapLighting.DAY, true, loaded -> {
            configureClouds(loaded, seconds * 2);
            loaded.safeGetUniform("CloudPeriod").set(3072f);
            loaded.safeGetUniform("WorldSpan").set(1024f, 576f);
            if (wrapped) loaded.safeGetUniform("CloudDriftX").set(3072f, 6144f, 12288f);
        });
    }

    private static void verifyPanning(GuiGraphics graphics, TextureTarget target, ShaderInstance shader, int[] fixture) throws IOException {
        try (var reference = renderClouds(graphics, target, shader, fixture, true, 0)) {
            for (int[] shift : new int[][]{{3, 2}, {-1, 1}, {target.width / 2 - 5, 0}}) {
                float cameraX = shift[0] * CLOUD_SPAN / target.width;
                float cameraZ = shift[1] * CLOUD_SPAN / target.height;
                try (var panned = render(graphics, target, shader, fixture, true, MapLighting.DAY, false, loaded -> {
                    configureClouds(loaded, 0);
                    loaded.safeGetUniform("PageX").set(-256f - cameraX, -256f - cameraX, -256f - cameraX, -256f - cameraX);
                    loaded.safeGetUniform("PageZ").set(-256f - cameraZ, -256f - cameraZ, -256f - cameraZ, -256f - cameraZ);
                    loaded.safeGetUniform("CloudOrigin").set(cameraX - (float) Math.floor(cameraX / 1536) * 1536,
                            cameraZ - (float) Math.floor(cameraZ / 1536) * 1536);
                })) {
                    panned.writeToFile(Path.of(System.getProperty("villagerretaliation.mapLocationClientSmoke"),
                            "map-clouds-pan-" + shift[0] + "-" + shift[1] + ".png"));
                    for (int y = 48; y < target.height - 48; y++) for (int x = target.width / 2 - 32; x < target.width - 48; x++) {
                        int px = x - shift[0], py = y - shift[1];
                        if (px < 0 || py < 0 || px >= target.width || py >= target.height) continue;
                        int before = reference.getPixelRGBA(x, y), after = panned.getPixelRGBA(px, py);
                        boolean cloud = brightness(before) > 400;
                        require(cloud == (brightness(after) > 400), "panning preserves the dither at the same world position");
                        if (cloud) for (int bit = 0; bit < 24; bit += 8)
                            require(Math.abs((before >> bit & 255) - (after >> bit & 255)) <= 3,
                                    "panning preserves cloud shapes with frozen wind, including coordinate wraps and viewport edges");
                    }
                }
            }
        }
    }

    private static void verifyZoom(GuiGraphics graphics, TextureTarget target, ShaderInstance shader, int[] fixture,
                                   DynamicTexture masks, DynamicTexture pages) throws IOException {
        for (int y = 0; y < 8; y++) for (int x = 0; x < 16; x++) masks.getPixels().setPixelRGBA(x, y, -1);
        masks.upload();
        for (int y = 0; y < 256; y++) for (int x = 0; x < 64; x++) pages.getPixels().setPixelRGBA(x, y, x < 32 ? 1 : 0);
        pages.upload();
        for (int level = 0; level < 4; level++) try (var reference = renderZoom(graphics, target, shader, fixture, 1, level)) {
            int stride = 1 << level;
            for (int z = -32; z < 32; z++) for (int x = 0; x < 64; x++) {
                int color = reference.getPixelRGBA(target.width / 2 + x, target.height / 2 + z);
                int cellX = Math.floorDiv(x, stride) * stride, cellZ = Math.floorDiv(z, stride) * stride;
                require(color == reference.getPixelRGBA(target.width / 2 + cellX, target.height / 2 + cellZ),
                        "cloud color and detail align to the terrain pixel grid at every map LOD");
            }
            reference.writeToFile(Path.of(System.getProperty("villagerretaliation.mapLocationClientSmoke"),
                    "map-clouds-pixel-scale-" + stride + ".png"));
            for (float scale : new float[]{0.5f, 1.25f, 2f, 4f}) {
                try (var zoomed = renderZoom(graphics, target, shader, fixture, scale, level)) {
                    for (int z = -32; z < 32; z++) for (int x = -48; x < 64; x++) {
                        int before = reference.getPixelRGBA(target.width / 2 + x, target.height / 2 + z);
                        int px = (int) Math.floor(target.width / 2f + (x + 0.5f) * scale);
                        int py = (int) Math.floor(target.height / 2f + (z + 0.5f) * scale);
                        // subpixel world cells cannot be compared after zooming out
                        int sampledX = (int) Math.floor((px + 0.5f - target.width / 2f) / scale);
                        int sampledZ = (int) Math.floor((py + 0.5f - target.height / 2f) / scale);
                        if (sampledX != x || sampledZ != z) continue;
                        int after = zoomed.getPixelRGBA(px, py);
                        boolean cloud = brightness(before) > 400;
                        require(cloud == (brightness(after) > 400), "zoom preserves the discovery fringe and dither at the same world position");
                        if (cloud) for (int bit = 0; bit < 24; bit += 8)
                            require(Math.abs((before >> bit & 255) - (after >> bit & 255)) <= 3,
                                    "zoom preserves cloud shapes and shading at each terrain LOD with frozen wind");
                    }
                    if (level == 0) zoomed.writeToFile(Path.of(System.getProperty("villagerretaliation.mapLocationClientSmoke"),
                            "map-clouds-zoom-" + scale + ".png"));
                }
            }
        }
    }

    private static NativeImage renderZoom(GuiGraphics graphics, TextureTarget target, ShaderInstance shader, int[] fixture,
                                         float scale, int level) {
        return render(graphics, target, shader, fixture, true, MapLighting.DAY, false, loaded -> {
            configureClouds(loaded, 0);
            loaded.safeGetUniform("CloudPeriod").set(3072f);
            loaded.safeGetUniform("WorldSpan").set(target.width / scale, target.height / scale);
            loaded.safeGetUniform("PageX").set(-256f, -512f, -1024f, -2048f);
            loaded.safeGetUniform("PageZ").set(-256f, -512f, -1024f, -2048f);
            loaded.safeGetUniform("FirstLevel").set((float) level);
            loaded.safeGetUniform("TerrainTexelSize").set((float) (1 << level));
        });
    }

    private static int ditherRank(int x, int y, int width, int height) {
        int worldX = (int) Math.floor((x + 0.5) * CLOUD_SPAN / width - CLOUD_SPAN / 2);
        int worldZ = (int) Math.floor((y + 0.5) * CLOUD_SPAN / height - CLOUD_SPAN / 2);
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
        loaded.safeGetUniform("WorldSpan").set(CLOUD_SPAN, CLOUD_SPAN);
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
        shader.safeGetUniform("TerrainTexelSize").set(1f);
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

    private static float[] maskPixels(TextureTarget target) {
        var pixels = new float[target.width * target.height];
        RenderSystem.bindTexture(target.getColorTextureId());
        GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RED, GL11.GL_FLOAT, pixels);
        return pixels;
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
