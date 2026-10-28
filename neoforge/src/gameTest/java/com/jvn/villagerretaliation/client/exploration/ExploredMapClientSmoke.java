package com.jvn.villagerretaliation.client.exploration;

import com.jvn.villagerretaliation.exploration.*;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = "villagerretaliation", value = Dist.CLIENT)
public final class ExploredMapClientSmoke {
    private static final String OUTPUT = "villagerretaliation.exploredMapClientSmoke";
    private static int phase, ticks;
    private static long start, uploads, closingStartedAt;
    private static int closingFrame;
    private static CompletableFuture<?> pending;
    private static CompletableFuture<MapRepository> fixture;
    private static final ArrayList<String> results = new ArrayList<>();
    private static double day;
    private static float openingPitch, openingYaw;
    private static NativeImage cloudFrame;
    private static boolean controlsFaded;
    @SubscribeEvent public static void scroll(ScreenEvent.MouseScrolled.Pre event) {
        if (System.getProperty(OUTPUT) != null && event.getScreen() instanceof ExploredMapScreen) event.setCanceled(true);
    }
    @SubscribeEvent public static void click(ScreenEvent.MouseButtonPressed.Pre event) {
        if (System.getProperty(OUTPUT) != null && event.getScreen() instanceof ExploredMapScreen) event.setCanceled(true);
    }
    @SubscribeEvent public static void key(ScreenEvent.KeyPressed.Pre event) {
        if (System.getProperty(OUTPUT) != null && event.getScreen() instanceof ExploredMapScreen) event.setCanceled(true);
    }
    @SubscribeEvent public static void rendered(ScreenEvent.Render.Post event) {
        if (System.getProperty(OUTPUT) == null || phase != 3 || controlsFaded || !(event.getScreen() instanceof ExploredMapScreen screen)) return;
        float progress = screen.transitionProgress();
        if (progress < .86f || progress > .94f) return;
        for (var child : screen.children()) if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget) {
            try {
                var alpha = net.minecraft.client.gui.components.AbstractWidget.class.getDeclaredField("alpha"); alpha.setAccessible(true);
                require(alpha.getFloat(widget) > 0 && alpha.getFloat(widget) < 1, "map controls render with partial opacity during the reveal");
            } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        }
        event.getGuiGraphics().flush(); screenshot("flat-map-controls-fade.png"); controlsFaded = true;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (System.getProperty(OUTPUT) == null || phase == 99) return;
        if (System.getProperty("villagerretaliation.mapPerformanceSource") != null) { MapTerrainPerformanceChecks.tick(); return; }
        var mc = Minecraft.getInstance(); mc.setWindowActive(true); mc.options.pauseOnLostFocus = false;
        if (start == 0) start = System.nanoTime();
        require(System.nanoTime() - start < 180_000_000_000L, "flat map smoke timed out at phase " + phase);
        if (mc.level == null || mc.player == null || mc.getOverlay() != null || ExploredMapClient.repository() == null) return;
        if (phase >= 2) { set(ExploredMapClient.class, null, "observation", null); ExploredMapClient.repository().tick(); }
        if (phase == 0) { pending = ExploredMapCaptureChecks.prepare().thenRunAsync(() -> mc.getSingleplayerServer().overworld().setDayTime(6000), mc.getSingleplayerServer()); phase = 1; }
        if (phase == 1 && pending.isDone() && ExploredMapCaptureChecks.complete()) {
            pending.join(); ExploredMapCaptureChecks.verify(); results.add("Actual water, canopy, surface light and sealed cave capture passed");
            fixture = CompletableFuture.supplyAsync(ExploredMapClientSmoke::fixture); phase = 2;
        }
        if (phase == 2 && fixture.isDone()) {
            ExploredMapClient.repository().close(); set(ExploredMapClient.class, null, "repository", fixture.join());
            ExploredMapClient.tiles().close(); ExploredMapScreen.resetTiles();
            VillagerRetaliationConfig.ANIMATE_EXPLORED_MAP_OPENING.set(false);
            mc.setScreen(new ExploredMapScreen());
            require(((ExploredMapScreen)mc.screen).coversWorld() && ((ExploredMapScreen)mc.screen).transitionProgress() == 1, "disabled opening animation displays the map immediately");
            mc.screen.onClose();
            require(mc.screen == null, "disabled animation closes the map immediately");
            VillagerRetaliationConfig.ANIMATE_EXPLORED_MAP_OPENING.set(true);
            mc.setScreen(new ExploredMapScreen());
            require(!((ExploredMapScreen)mc.screen).coversWorld(), "enabled opening animation initially shows the world camera");
            verifyGentleTilt((ExploredMapScreen)mc.screen);
            ((ExploredMapScreen)mc.screen).mouseScrolled(100, 100, 0, 1);
            require(((ExploredMapScreen)mc.screen).transitionProgress() == 1, "map input skips the transition immediately");
            mc.setScreen(new ExploredMapScreen());
            openingPitch = mc.player.getXRot(); openingYaw = mc.player.getYRot();
            navigation((ExploredMapScreen) mc.screen).focus(.5, .5);
            zoom((ExploredMapScreen) mc.screen, 1.0); phase = 3; ticks = 0;
        }
        if (phase == 3 && ticks == 3) {
            screenshot("flat-map-opening.png");
            require(mc.gameRenderer.getMainCamera().getXRot() < openingPitch || openingPitch <= -90, "opening tilts the rendered camera upward");
            require(mc.player.getXRot() == openingPitch && mc.player.getYRot() == openingYaw, "opening does not change player rotation");
        }
        if (phase == 3 && ticks == 10) screenshot("flat-map-opening-covered.png");
        if (phase == 3 && ticks == 17) screenshot("flat-map-opening-reveal.png");
        if (phase == 3 && ++ticks >= 160) {
            var screen = (ExploredMapScreen)mc.screen;
            require(ExploredMapClient.tiles().uploads() > 100, "recorded cells uploaded to GPU atlas");
            require(screen.transitionProgress() == 1 && screen.view().scale() == 1.0, "opening finishes without changing map zoom");
            require(controlsFaded, "map controls fade in before opening finishes");
            results.add("Small relative camera tilt and gradual map control opacity passed");
            require(mc.gameRenderer.getMainCamera().getXRot() == mc.player.getXRot(), "camera orientation restores beneath the map");
            results.add("Upward camera tilt, layered cloud reveal, input skip, instant-open config and camera restoration passed");
            verifyControls(screen); screenshot("flat-map-day.png");
            verifyPixels(); cloudFrame = Screenshot.takeScreenshot(mc.getMainRenderTarget()); day = brightness(); uploads = ExploredMapClient.tiles().uploads(); phase = 4; ticks = 0;
        }
        if (phase == 4 && ++ticks >= 40) {
            require(uploads == ExploredMapClient.tiles().uploads(), "stationary map reuses terrain without uploading");
            verifyCloudMotion(); verifyPixels();
            screenshot("flat-map-cloud-motion.png");
            results.add("Clouds move across stationary unknown terrain while discovered pixels remain unobstructed");
            results.add("Cursor zoom, drag, keyboard controls, exact top-down coordinates and idle upload reuse passed");
            zoom((ExploredMapScreen) mc.screen, .3); phase = 10; ticks = 0;
        }
        if (phase == 10 && ++ticks >= 120) {
            screenshot("flat-map-distant.png"); verifyPixels();
            results.add("Minecraft map colors, neutral fog and moving vanilla cloud layers match at near and distant zoom");
            zoom((ExploredMapScreen) mc.screen, 1.0);
            pending = mc.getSingleplayerServer().submit(() -> mc.getSingleplayerServer().overworld().setDayTime(18000));
            phase = 5; ticks = 0;
        }
        if (phase == 5 && pending.isDone() && ++ticks >= 60) {
            screenshot("flat-map-night.png");
            require(brightness() < day * .9, "night shader changes brightness without rebuilding tiles");
            results.add("Day and night lighting changes on cached terrain passed");
            var screen = (ExploredMapScreen)mc.screen;
            closingStartedAt = System.nanoTime();
            screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
            require(mc.screen == screen && screen.coversWorld(), "closing keeps the map visible until clouds cover it");
            double zoom = screen.view().scale();
            screen.mouseScrolled(100, 100, 0, 1);
            require(screen.view().scale() == zoom, "closing ignores map manipulation");
            phase = 12; ticks = 0;
        }
        if (phase == 12 && closingFrame == 0 && System.nanoTime() - closingStartedAt >= 100_000_000L) {
            screenshot("flat-map-closing.png"); closingFrame++;
        }
        if (phase == 12 && closingFrame == 1 && System.nanoTime() - closingStartedAt >= 300_000_000L) {
            screenshot("flat-map-closing-covered.png"); closingFrame++;
        }
        if (phase == 12 && closingFrame == 2 && mc.screen instanceof ExploredMapScreen screen && !screen.coversWorld()) {
            screenshot("flat-map-closing-world.png"); closingFrame++;
            require(mc.player.getXRot() == openingPitch && mc.player.getYRot() == openingYaw, "closing does not change player rotation");
        }
        if (phase == 12 && System.nanoTime() - closingStartedAt >= 800_000_000L) {
            require(closingFrame == 3, "closing reveals the world before releasing the screen");
            require(mc.screen == null, "closing finishes automatically in under 0.8 seconds");
            require(mc.gameRenderer.getMainCamera().getXRot() == mc.player.getXRot(), "closing restores the world camera");
            results.add("Reverse cloud close, world reveal, camera restoration and instant-close config passed");
            VillagerRetaliationConfig.SHOW_EXPLORED_MAP_MINIMAP.set(true); phase = 6; ticks = 0;
        }
        if (phase == 6 && ++ticks >= 60) {
            screenshot("flat-minimap.png");
            VillagerRetaliationConfig.EXPLORED_MAP_MINIMAP_SIZE.set(240); VillagerRetaliationConfig.EXPLORED_MAP_MINIMAP_ZOOM.set(2.0);
            phase = 7; ticks = 0;
        }
        if (phase == 7 && ++ticks >= 40) {
            require(ExploredMapMinimap.width() == 240, "minimap size applies live"); screenshot("flat-minimap-large.png");
            VillagerRetaliationConfig.SHOW_EXPLORED_MAP_MINIMAP.set(false);
            mc.setScreen(new ExploredMapScreen()); navigation((ExploredMapScreen) mc.screen).focus(.5, .5);
            zoom((ExploredMapScreen) mc.screen, 1.0);
            phase = 8; ticks = 0;
        }
        if (phase == 8 && ++ticks >= 40) {
            screenshot("flat-map-reopened.png"); results.add("Minimap sizing and zoom, fullscreen close and reopen passed");
            pending = mc.reloadResourcePacks(); phase = 9; ticks = 0;
        }
        if (phase == 9 && pending.isDone() && ++ticks >= 120) {
            pending.join(); screenshot("flat-map-resource-reload.png");
            require(org.lwjgl.opengl.GL11.glGetError() == 0, "GPU tile and shader operations produced no GL errors");
            results.add("Resource reload rebuilt GPU tiles from saved discovery with no GL errors");
            mc.setScreen(null); uploads = ExploredMapClient.tiles().uploads(); phase = 11; ticks = 0;
        }
        if (phase == 11 && ++ticks >= 40) {
            require(uploads == ExploredMapClient.tiles().uploads(), "closed map with minimap disabled performs no terrain uploads");
            results.add("Closed map with minimap disabled performs no terrain uploads");
            var screen = new ExploredMapScreen(); mc.setScreen(screen);
            set(MapScreenTransition.class, field(screen, "transition"), "startedAt", System.nanoTime() - 250_000_000L);
            screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
            require(screen.transitionProgress() > 0 && screen.transitionProgress() < .25f, "closing during opening reverses from the current position");
            phase = 13; ticks = 0;
        }
        if (phase == 13 && ++ticks >= 20) {
            require(mc.screen == null, "interrupted opening closes automatically");
            var screen = new ExploredMapScreen(); mc.setScreen(screen);
            screen.mouseScrolled(100, 100, 0, 1);
            screen.onClose(); screen.onClose();
            require(mc.screen == null, "second close request skips the closing animation");
            results.add("Close during opening and repeated close input passed");
            try { Files.writeString(Path.of(System.getProperty(OUTPUT), "result.txt"), "Explored map client checks passed\n" + String.join("\n", results)); }
            catch (IOException e) { throw new UncheckedIOException(e); }
            phase = 99; mc.stop();
        }
    }
    private static void verifyGentleTilt(ExploredMapScreen screen) {
        for (float pitch : new float[] {-89, 0, 80}) {
            float previous = pitch;
            for (long elapsed : new long[] {100_000_000L, 250_000_000L, 450_000_000L}) {
                set(MapScreenTransition.class, field(screen, "transition"), "startedAt", System.nanoTime() - elapsed);
                float tilted = screen.transitionPitch(pitch);
                require(tilted >= -90 && tilted >= pitch - 6.001f && tilted <= previous, "camera gently tilts by at most six degrees from any viewing angle");
                previous = tilted;
            }
        }
        set(MapScreenTransition.class, field(screen, "transition"), "startedAt", System.nanoTime());
    }
    private static MapRepository fixture() {
        var root = Path.of(System.getProperty(OUTPUT), "flat-fixture");
        try (var store = new MapStore(root, "flat", "world", "player", "dimension")) {
            var batch = new ArrayList<MapSnapshot>();
            for (int z = -16; z < 16; z++) for (int x = -16; x < 16; x++) {
                var columns = new ArrayList<MapSnapshot.Column>();
                for (int i = 0; i < 64; i++) {
                    int bx = x * 8 + i % 8, bz = z * 8 + i / 8;
                    if (bx > 48 && bz > 48) continue;
                    String state = bx < -24 ? "minecraft:water[level=0]" : Math.abs(bx + bz) < 8 ? "minecraft:sand" : bz > 24 ? "minecraft:oak_leaves" : "minecraft:grass_block";
                    int tint = state.contains("grass") ? 0x80ad52 : state.contains("leaves") ? 0x4e8138 : 0xffffff;
                    columns.add(new MapSnapshot.Column(i, 64, List.of(new MapSnapshot.Block(64, state, tint, 0x3f76e4, 2, bx >= 0 && bx < 8 ? 15 : 0)), true));
                }
                batch.add(new MapSnapshot(x, z, 1, columns, x == 0 && z == 0 ? List.of(new MapSnapshot.Marker("village", "Oakford", 4, 64, 4)) : List.of()));
            }
            store.writeBatch(batch);
        } catch (IOException e) { throw new UncheckedIOException(e); }
        return new MapRepository(new MapStore(root, "flat", "world", "player", "dimension"));
    }
    private static void verifyControls(ExploredMapScreen screen) {
        var before = screen.view(); double x = screen.width * .7, y = screen.height * .4;
        screen.mouseScrolled(x, y, 0, 3);
        var after = screen.view();
        require(Math.abs(before.worldX(x) - after.worldX(x)) < .000001 && Math.abs(before.worldZ(y) - after.worldZ(y)) < .000001, "zoom preserves world position under cursor");
        screen.mouseClicked(screen.width / 2.0, screen.height / 2.0, 0);
        screen.mouseDragged(screen.width / 2.0, screen.height / 2.0, 0, 30, -20);
        var dragged = screen.view();
        require(Math.abs(dragged.x() - after.x() + 30 / after.scale()) < .000001 && Math.abs(dragged.z() - after.z() - 20 / after.scale()) < .000001, "drag follows flat axes");
        screen.mouseReleased(screen.width / 2.0, screen.height / 2.0, 0);
        screen.keyPressed(GLFW.GLFW_KEY_HOME, 0, 0);
        require(screen.view().x() == Minecraft.getInstance().player.getX(), "home recenters on player");
        navigation((ExploredMapScreen) screen).focus(.5, .5); zoom((ExploredMapScreen) screen, 1.0);
    }
    private static void verifyPixels() {
        var screen = (ExploredMapScreen)Minecraft.getInstance().screen; var view = screen.view();
        try (var image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
            int water = pixel(image, view, -60, -40), land = pixel(image, view, 30, -60), unknown = pixel(image, view, 90, 90);
            require((water & 0xffffff) == (net.minecraft.world.level.material.MapColor.WATER.calculateRGBColor(
                    net.minecraft.world.level.material.MapColor.Brightness.HIGH) & 0xffffff), "water pixels match Minecraft's map palette");
            require((land & 0xffffff) == (net.minecraft.world.level.material.MapColor.GRASS.calculateRGBColor(
                    net.minecraft.world.level.material.MapColor.Brightness.NORMAL) & 0xffffff), "grass pixels match Minecraft's map palette");
            require((unknown & 255) == (unknown >> 8 & 255) && (unknown >> 8 & 255) == (unknown >> 16 & 255), "unknown terrain contains only neutral white cloud layers");
            int white = 0, middle = 0, back = 0, background = 0;
            for (int y = 90; y < image.getHeight() - 100; y += 3) for (int x = 20; x < image.getWidth() - 20; x += 3) {
                boolean markerOverlay = Math.abs(x - image.getWidth() / 2) < 120 && Math.abs(y - image.getHeight() / 2) < 100;
                if (markerOverlay) continue;
                double wx = view.worldX((double)x * view.width() / image.getWidth());
                double wz = view.worldZ((double)y * view.height() / image.getHeight());
                if (!(wx > 140 || wz > 140 || wx > 55 && wz > 55)) continue;
                int rgb = image.getPixelRGBA(x, y), red = rgb & 255, green = rgb >> 8 & 255, blue = rgb >> 16 & 255;
                require(red == green && green == blue, "unexplored terrain never leaks map colors through cloud gaps at " + wx + ", " + wz);
                require(red >= 218, "undiscovered terrain stays light beneath the cloud layers");
                if (red == 255) white++;
                else if (red >= 241 && red <= 243) middle++;
                else if (red >= 228 && red <= 231) back++;
                else if (red >= 218 && red <= 220) background++;
            }
            require(white > 10 && middle > 10 && back > 10 && background > 10, "unexplored areas show bright vanilla cloud layers over shaded fog");
        }
    }
    private static void verifyCloudMotion() {
        var view = ((ExploredMapScreen)Minecraft.getInstance().screen).view();
        try (var before = cloudFrame; var after = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
            int changed = 0, discovered = 0;
            for (int y = 100; y < before.getHeight() - 100; y += 3) for (int x = 30; x < before.getWidth() - 30; x += 3) {
                double wx = view.worldX((double)x * view.width() / before.getWidth());
                double wz = view.worldZ((double)y * view.height() / before.getHeight());
                if (wx > 140 || wz > 140 || wx > 55 && wz > 55) {
                    if (before.getPixelRGBA(x, y) != after.getPixelRGBA(x, y)) changed++;
                } else if (wx > -120 && wx < -30 && wz > -120 && wz < 110) {
                    require(before.getPixelRGBA(x, y) == after.getPixelRGBA(x, y), "moving clouds never cover discovered water");
                    discovered++;
                }
            }
            require(changed > 20 && discovered > 100, "cloud motion changes unknown pixels while discovered terrain stays fixed");
        }
        cloudFrame = null;
    }
    private static int pixel(NativeImage image, MapView view, double x, double z) {
        return image.getPixelRGBA((int)(view.screenX(x) * image.getWidth() / view.width()), (int)(view.screenY(z) * image.getHeight() / view.height()));
    }
    private static double brightness() {
        try (var image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
            long sum = 0; int count = 0;
            for (int y = image.getHeight() / 3; y < image.getHeight() * 2 / 3; y++) for (int x = image.getWidth() / 3; x < image.getWidth() * 2 / 3; x++) {
                int rgb = image.getPixelRGBA(x, y); sum += (rgb & 255) + (rgb >> 8 & 255) + (rgb >> 16 & 255); count++;
            }
            return (double)sum / count;
        }
    }
    private static void screenshot(String name) {
        try (var image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) { image.writeToFile(Path.of(System.getProperty(OUTPUT), name)); }
        catch (IOException e) { throw new UncheckedIOException(e); }
    }
    static MapNavigation navigation(ExploredMapScreen screen) {
        return (MapNavigation) field(screen, "navigation");
    }
    static void zoom(ExploredMapScreen screen, double scale) {
        navigation(screen).zoomAt(screen.width / 2.0, screen.height / 2.0, scale);
    }
    private static Object field(Object target, String name) {
        try { var field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static void set(Class<?> type, Object target, String name, Object value) {
        try { var field = type.getDeclaredField(name); field.setAccessible(true); field.set(target, value); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
