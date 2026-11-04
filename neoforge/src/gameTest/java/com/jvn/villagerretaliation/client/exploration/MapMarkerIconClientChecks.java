package com.jvn.villagerretaliation.client.exploration;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.exploration.MapLocation;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

final class MapMarkerIconClientChecks {
    private static final String[] NAMES = {"ruined_portal", "desert_temple", "pillager_outpost", "village", "nether_portal"};
    private static final MapLocation.Icon[] ICONS = {MapLocation.Icon.RUINED_PORTAL, MapLocation.Icon.DESERT_TEMPLE,
            MapLocation.Icon.PILLAGER_OUTPOST, null, MapLocation.Icon.NETHER_PORTAL};
    private MapMarkerIconClientChecks() { }

    static void verify(ExploredMapScreen screen) {
        var minecraft = Minecraft.getInstance();
        var graphics = new GuiGraphics(minecraft, minecraft.renderBuffers().bufferSource());
        graphics.flush();
        boolean setting = VillagerRetaliationConfig.PARCHMENT_MAP_COLORS.get();
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND), depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        var originals = new ArrayList<NativeImage>();
        var modelView = RenderSystem.getModelViewStack();
        RenderSystem.backupProjectionMatrix(); modelView.pushMatrix().identity(); RenderSystem.applyModelViewMatrix();
        try {
            RenderSystem.disableDepthTest();
            for (String name : NAMES) {
                try (var stream = minecraft.getResourceManager().getResourceOrThrow(texture(name)).open()) {
                    var image = NativeImage.read(stream); originals.add(image);
                    require(image.getWidth() == 16 && image.getHeight() == 16, "map icon textures retain their 16 by 16 canvases");
                }
            }
            for (int scale = 1; scale <= 3; scale++) {
                var target = new TextureTarget(128 * scale, 40 * scale, false, Minecraft.ON_OSX);
                try {
                    RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, 128, 40, 0, -1000, 1000), VertexSorting.ORTHOGRAPHIC_Z);
                    for (boolean parchment : new boolean[]{false, true}) {
                        VillagerRetaliationConfig.PARCHMENT_MAP_COLORS.set(parchment);
                        target.setClearColor(0, 0, 0, 0); target.clear(Minecraft.ON_OSX); target.bindWrite(true);
                        draw(graphics);
                        try (var image = snapshot(target)) {
                            var shades = new ArrayList<java.util.Map<Integer, Integer>>();
                            for (int icon = 0; icon < NAMES.length; icon++) shades.add(new java.util.HashMap<>());
                            for (int y = 0; y < 40; y++) for (int x = 0; x < 128; x++) {
                                int source = 0, sourceIcon = -1;
                                for (int icon = 0; icon < NAMES.length; icon++) {
                                    int localX = x - (8 + icon * 24), localY = y - 12;
                                    if (localX >= 0 && localX < 16 && localY >= 0 && localY < 16) {
                                        source = originals.get(icon).getPixelRGBA(localX, localY); sourceIcon = icon;
                                    }
                                }
                                int expected = parchment ? image.getPixelRGBA(x * scale, y * scale) : source;
                                if (parchment && source >>> 24 != 0) shades.get(sourceIcon).putIfAbsent(source, expected);
                                for (int dy = 0; dy < scale; dy++) for (int dx = 0; dx < scale; dx++) {
                                    int actual = image.getPixelRGBA(x * scale + dx, y * scale + dy);
                                    require(actual >>> 24 == source >>> 24, "sprite transparency and bottom-center placement match the original at every GUI scale");
                                    if (source >>> 24 == 0) require(actual == 0, "transparent sprite margins remain clear");
                                    else {
                                        require(close(actual, expected), "sprite pixels stay crisp and preserve original colors in vanilla mode");
                                        if (parchment) require((actual & 255) > (actual >>> 8 & 255)
                                                && (actual >>> 8 & 255) > (actual >>> 16 & 255), "sprite shades use warm map ink and paper colors");
                                    }
                                }
                            }
                            if (parchment) for (var sprite : shades)
                                require(new java.util.HashSet<>(sprite.values()).size() == sprite.size(),
                                        "every distinct original sprite shade remains distinct after tinting");
                        }
                        if (scale == 3 && parchment) {
                            target.setClearColor(0.72f, 0.68f, 0.52f, 1); target.clear(Minecraft.ON_OSX); target.bindWrite(true);
                            draw(graphics);
                            try (var image = snapshot(target)) {
                                image.writeToFile(Path.of(System.getProperty("villagerretaliation.mapLocationClientSmoke"), "map-marker-icons.png"));
                            }
                        }
                    }
                } finally { target.destroyBuffers(); }
            }
            verifyZoom(graphics, originals);
        } catch (IOException exception) { throw new UncheckedIOException(exception); }
        finally {
            for (var image : originals) image.close();
            VillagerRetaliationConfig.PARCHMENT_MAP_COLORS.set(setting);
            modelView.popMatrix(); RenderSystem.applyModelViewMatrix(); RenderSystem.restoreProjectionMatrix();
            minecraft.getMainRenderTarget().bindWrite(true);
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
        }
        verifyPicker(screen);
        System.out.println("Map marker icon checks passed: five sprites, original shading, palette toggle, exact silhouettes, bottom anchors, world texel alignment, map zoom, GUI scales and manual selection");
    }

    private static void verifyZoom(GuiGraphics graphics, List<NativeImage> originals) {
        var target = new TextureTarget(512, 800, false, Minecraft.ON_OSX);
        try {
            VillagerRetaliationConfig.PARCHMENT_MAP_COLORS.set(false);
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, 512, 800, 0, -1000, 1000), VertexSorting.ORTHOGRAPHIC_Z);
            for (double zoom : new double[]{.12, 1, 1.25, 2.5, 4, 24}) {
                for (int coordinate : new int[]{-3, 29999985}) {
                    var view = new com.jvn.villagerretaliation.exploration.MapView(512, 800, coordinate - .375, coordinate + 8.125, zoom);
                    int stride = 1 << view.level();
                    int anchorX = Math.floorDiv(coordinate, stride) * stride, anchorZ = Math.floorDiv(coordinate + 8, stride) * stride;
                    for (int icon = 0; icon < NAMES.length; icon++) {
                        var destination = new MapDestination(ICONS[icon] == null ? MapDestination.Kind.VILLAGE : MapDestination.Kind.LOCATION,
                                NAMES[icon], ResourceLocation.withDefaultNamespace("overworld"), coordinate, coordinate + 8, NAMES[icon], NAMES[icon], ICONS[icon], false, 0xffffffff, 0);
                        target.setClearColor(0, 0, 0, 0); target.clear(Minecraft.ON_OSX); target.bindWrite(true);
                        MapMarkerOverlay.render(graphics, view, Minecraft.getInstance().font,
                                MapMarkerLayout.groups(List.of(destination), view, ""), new MapLayers(true), "", -1, -1, true);
                        graphics.flush();
                        try (var image = snapshot(target)) {
                            for (int y = 0; y < 800; y++) for (int x = 0; x < 512; x++) {
                                int actual = image.getPixelRGBA(x, y);
                                double sourceX = (view.worldX(x + .5) - anchorX) / stride + 8;
                                double sourceY = (view.worldZ(y + .5) - anchorZ) / stride + 16;
                                double boundaryTolerance = .00001;
                                if (sourceX < -boundaryTolerance || sourceX > 16 + boundaryTolerance
                                        || sourceY < -boundaryTolerance || sourceY > 16 + boundaryTolerance) {
                                    require(actual == 0, "zoomed sprites stay inside their anchored canvas");
                                    continue;
                                }
                                boolean canvasEdge = Math.abs(sourceX) <= boundaryTolerance || Math.abs(sourceX - 16) <= boundaryTolerance
                                        || Math.abs(sourceY) <= boundaryTolerance || Math.abs(sourceY - 16) <= boundaryTolerance;
                                if (canvasEdge && actual == 0) continue;
                                boolean matches = false;
                                for (int sy = Math.max(0, (int) Math.floor(sourceY - boundaryTolerance)); sy <= Math.min(15, Math.floor(sourceY + boundaryTolerance)); sy++)
                                    for (int sx = Math.max(0, (int) Math.floor(sourceX - boundaryTolerance)); sx <= Math.min(15, Math.floor(sourceX + boundaryTolerance)); sx++) {
                                        int expected = originals.get(icon).getPixelRGBA(sx, sy);
                                        matches |= actual >>> 24 == expected >>> 24 && (expected >>> 24 == 0 ? actual == 0 : close(actual, expected));
                                    }
                                require(matches, "zoomed sprites preserve source pixels and transparency with nearest sampling at texel boundaries");
                            }
                        }
                    }
                }
            }
        } finally { target.destroyBuffers(); }
    }

    private static ResourceLocation texture(String name) { return VillagerRetaliation.id("textures/gui/map_" + name + ".png"); }

    private static void draw(GuiGraphics graphics) {
        for (int index = 0; index < NAMES.length; index++) {
            int anchorX = 16 + index * 24;
            if (ICONS[index] == null) MapVillageIcon.render(graphics, anchorX, 28);
            else MapMarkerIcon.render(graphics, ICONS[index], anchorX - 8, 12);
        }
        graphics.flush();
    }

    private static NativeImage snapshot(TextureTarget target) {
        var image = new NativeImage(target.width, target.height, false);
        RenderSystem.bindTexture(target.getColorTextureId()); image.downloadTexture(0, false); image.flipY();
        return image;
    }

    private static boolean close(int actual, int expected) {
        for (int shift = 0; shift < 24; shift += 8)
            if (Math.abs((actual >>> shift & 255) - (expected >>> shift & 255)) > 1) return false;
        return true;
    }

    private static void verifyPicker(ExploredMapScreen screen) {
        var minecraft = Minecraft.getInstance();
        var location = new MapLocation(UUID.randomUUID(), minecraft.level.dimension().location(), 0, 0,
                MapLocation.Icon.EMERALD, "Map landmark", "", false);
        try {
            var choose = MapLocationEditScreen.class.getDeclaredMethod("chooseIcon"); choose.setAccessible(true);
            for (var icon : MapLocation.Icon.values()) if (icon.mapSprite()) {
                var editor = new MapLocationEditScreen(screen, location); minecraft.setScreen(editor); choose.invoke(editor);
                Object popup = field(field(editor, "menu"), "popup");
                var buttons = (List<?>) field(popup, "buttons");
                var button = (Button) buttons.get(icon.ordinal());
                require(field(button, "renderer") != null && button.getHeight() >= 18, "manual sprite choices have real artwork previews");
                require(button.getMessage().getString().equals(MapLocationsClient.iconName(icon).getString()), "manual sprite names are localized in the picker");
                require(!button.getMessage().getString().startsWith("map.villagerretaliation."), "manual sprite names resolve to translated labels");
                button.onPress();
                require(field(editor, "icon") == icon, "every manual map sprite can be selected");
            }
            var editor = new MapLocationEditScreen(screen, location); minecraft.setScreen(editor); choose.invoke(editor);
            editor.keyPressed(GLFW.GLFW_KEY_UP, 0, 0); editor.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
            require(field(editor, "icon") == MapLocation.Icon.NETHER_PORTAL, "keyboard navigation reaches the appended sprite choices");
        } catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
        finally { minecraft.setScreen(screen); }
    }

    private static Object field(Object owner, String name) throws ReflectiveOperationException {
        var field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(owner);
    }

    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
