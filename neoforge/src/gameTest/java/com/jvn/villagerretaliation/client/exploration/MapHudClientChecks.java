package com.jvn.villagerretaliation.client.exploration;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

final class MapHudClientChecks {
    private MapHudClientChecks() { }

    static void verify(ExploredMapScreen screen) {
        var minecraft = Minecraft.getInstance();
        boolean hidden = minecraft.options.hideGui;
        var original = screen.view();
        var shader = RenderSystem.getShader();
        var target = new TextureTarget(screen.width, screen.height, false, Minecraft.ON_OSX);
        RenderSystem.backupProjectionMatrix();
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix().identity();
        RenderSystem.applyModelViewMatrix();
        try {
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, screen.width, screen.height, 0, -1000, 1000), VertexSorting.ORTHOGRAPHIC_Z);
            minecraft.options.hideGui = false;
            try (var visible = render(screen, target)) {
                verifyZoomScale(screen, target);
                verifyCompassTooltip(screen, target);
                verifyTabNavigation(screen, target);
                verifyRelationsPage(screen, target);
                verifyBottomPage(screen, target);
                var destinations = screen.destinations();
                var widgets = screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast).toList();
                require(!widgets.isEmpty(), "the map has controls to hide");
                screen.setFocused(widgets.getFirst());
                require(screen.keyPressed(GLFW.GLFW_KEY_F1, 0, 0) && minecraft.options.hideGui, "F1 uses Minecraft's HUD visibility setting");
                require(screen.getFocused() == null, "hiding the map clears keyboard focus from its controls");
                require(screen.keyPressed(GLFW.GLFW_KEY_F1, 0, 0) && minecraft.options.hideGui, "holding F1 cannot repeatedly toggle the map HUD");
                screen.keyReleased(GLFW.GLFW_KEY_F1, 0, 0);
                long frames = screen.renderedFrames();
                try (var clean = render(screen, target)) {
                    require(screen.renderedFrames() > frames, "the map terrain keeps rendering with its HUD hidden");
                    for (var widget : widgets) {
                        require(!widget.visible, "all map buttons are hidden");
                        int changed = 0, pixels = 0;
                        for (int y = Math.max(0, widget.getY()); y < Math.min(target.height, widget.getBottom()); y++)
                            for (int x = Math.max(0, widget.getX()); x < Math.min(target.width, widget.getRight()); x++) {
                                pixels++;
                                if (visible.getPixelRGBA(x, y) != clean.getPixelRGBA(x, y)) changed++;
                            }
                        require(changed > (widget instanceof MapZoomScale ? 100 : pixels / 3), "hidden controls leave the terrain visible underneath");
                    }
                    var output = Path.of(System.getProperty("villagerretaliation.mapLocationClientSmoke"));
                    visible.writeToFile(output.resolve("map-hud-visible.png"));
                    clean.writeToFile(output.resolve("map-hud-hidden.png"));
                }
                require(screen.destinations().equals(destinations), "hiding the HUD preserves map destinations and layer settings");
                require(screen.markerAt(screen.width / 2.0, screen.height / 2.0) == null
                        && screen.hoveredMarker(screen.width / 2.0, screen.height / 2.0) == null, "hidden markers cannot be clicked or hovered");
                screen.keyPressed(GLFW.GLFW_KEY_F, 0, GLFW.GLFW_MOD_CONTROL);
                require(minecraft.screen == screen, "hidden map shortcuts cannot open an invisible interface");
                double before = screen.view().x();
                int rulerY = screen.height - 40;
                require(screen.mouseClicked(20, rulerY, 0), "clicking the hidden ruler's position starts map dragging");
                screen.mouseDragged(30, rulerY, 0, 10, 0);
                screen.mouseReleased(30, rulerY, 0);
                require(screen.view().x() != before && minecraft.screen == screen, "hidden controls do not intercept panning");
                double zoom = screen.view().scale();
                screen.mouseScrolled(screen.width / 2.0, screen.height / 2.0, 0, 1);
                require(screen.view().scale() > zoom, "scroll zoom remains available while the HUD is hidden");
                require(!screen.mouseClicked(screen.width / 2.0, screen.height / 2.0, 1), "right click cannot open hidden marker or placement menus");
                verifyReset(screen);
                screen.resize(minecraft, screen.width, screen.height);
                require(screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast).noneMatch(widget -> widget.visible),
                        "resizing cannot reveal hidden map controls");
                require(screen.keyPressed(GLFW.GLFW_KEY_F1, 0, 0) && !minecraft.options.hideGui, "F1 restores the map HUD");
                screen.keyReleased(GLFW.GLFW_KEY_F1, 0, 0);
                require(screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast).allMatch(widget -> widget.visible),
                        "showing the HUD restores every map control");
                verifyReset(screen);
                try (var restored = render(screen, target)) {
                    restored.writeToFile(Path.of(System.getProperty("villagerretaliation.mapLocationClientSmoke"), "map-hud-restored.png"));
                }
            }
            System.out.println("Map HUD checks passed: F1 toggle, repeat suppression, clean terrain rendering, hidden interactions, panning, zoom, middle-click reset and resize");
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        } finally {
            screen.keyReleased(GLFW.GLFW_KEY_F1, 0, 0);
            minecraft.options.hideGui = hidden;
            ExploredMapClientSmoke.navigation(screen).focus(original.x(), original.z());
            ExploredMapClientSmoke.zoom(screen, original.scale());
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
            target.destroyBuffers();
            minecraft.getMainRenderTarget().bindWrite(true);
            RenderSystem.setShader(() -> shader);
        }
    }

    private static void verifyBottomPage(ExploredMapScreen screen, TextureTarget target) throws IOException {
        var minecraft = Minecraft.getInstance();
        var texture = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("villagerretaliation", "textures/gui/map_bottom_page.png");
        var selected = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("villagerretaliation", "textures/gui/map_bottom_page_selected.png");
        float[] position = new float[2];
        var textures = new java.util.ArrayList<net.minecraft.resources.ResourceLocation>();
        var graphics = new GuiGraphics(minecraft, minecraft.renderBuffers().bufferSource()) {
            @Override public void blit(net.minecraft.resources.ResourceLocation sprite, int x, int y, float u, float v,
                    int width, int height, int textureWidth, int textureHeight) {
                if (sprite.equals(texture) || sprite.equals(selected)) {
                    position[0] = pose().last().pose().m30();
                    position[1] = pose().last().pose().m31();
                    textures.add(sprite);
                    require(width == 348 && height == 431 && textureWidth == 348 && textureHeight == 431,
                            "the bottom page retains its native dimensions");
                }
                super.blit(sprite, x, y, u, v, width, height, textureWidth, textureHeight);
            }
        };
        var page = new MapBottomPage();
        target.bindWrite(true);
        for (int width : new int[]{320, 640, 960}) {
            page.render(graphics, width, 360, -1, -1, 0, 0, 0, 1, 1);
            float left = (width - 104 - 348) / 2f;
            require(position[0] == left && position[1] == 335,
                    "the bottom page is centered before the resting wood anchor with 25 pixels exposed at every width");
            if (width == 640) require(!page.at(left, 335) && page.at(left + 20, 345)
                    && !page.at(left + 20, 360), "bottom page hover follows opaque pixels and screen clipping");
        }
        page.render(graphics, 640, 360, 200, 345, 0, 0, 0, 1, 1);
        require(textures.getLast().equals(selected) && position[1] == 325,
                "hover swaps the bottom page sprite and slides it ten pixels upward");
        MapHudSprite.clearMasks();
        page.render(graphics, 640, 360, 200, 345, 0, 0, 0, 0, 1);
        require(position[1] == 325, "reloading HUD masks preserves the page hover offset");
        page.render(graphics, 640, 360, -1, -1, .25f, -.5f, 1, 1, 1);
        require(textures.getLast().equals(texture) && position[0] == 93.25f && position[1] == 339.5f,
                "bottom page parallax retains fractional movement and zoom pushes it outward");
        page.render(graphics, 640, 360, -1, -1, 0, 0, 0, 1, 0);
        require(position[1] == 360 && !page.at(200, 345), "the bottom page begins offscreen during the HUD entrance");
        graphics.flush();
        int x = Math.max(20, (screen.width - 104) / 2), y = screen.height - 10;
        screen.render(graphics, x, y, 0);
        graphics.flush();
        require(textures.getLast().equals(selected), "the bottom page participates in map screen hover rendering");
        try (var image = new NativeImage(target.width, target.height, false)) {
            RenderSystem.bindTexture(target.getColorTextureId());
            image.downloadTexture(0, false);
            image.flipY();
            image.writeToFile(Path.of(System.getProperty("villagerretaliation.mapLocationClientSmoke"), "map-bottom-page-hover.png"));
        }
        var view = screen.view();
        require(screen.mouseClicked(x, y, 1), "the bottom page consumes right clicks over the paper");
        screen.mouseDragged(x + 20, y, 0, 20, 0);
        screen.mouseReleased(x + 20, y, 0);
        screen.mouseScrolled(x, y, 0, 1);
        require(minecraft.screen == screen && screen.view().equals(view), "bottom page input cannot pan or zoom the map underneath");
        minecraft.options.hideGui = true;
        textures.clear();
        screen.render(graphics, x, y, 0);
        graphics.flush();
        require(textures.isEmpty(), "F1 hides the bottom page");
        minecraft.options.hideGui = false;
        verifyMapSettings(screen, target);
        System.out.println("Map bottom page checks passed: native dimensions, anchor centering, 25-pixel exposure, alpha hit masks, selected sprite, hover motion, fractional parallax, zoom, entrance, input occlusion and F1 visibility");
    }

    private static void verifyMapSettings(ExploredMapScreen screen, TextureTarget target) throws IOException {
        var minecraft = Minecraft.getInstance();
        var pageTexture = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("villagerretaliation", "textures/gui/map_bottom_page_selected.png");
        float[] paper = new float[2];
        int[] papers = {0};
        var textures = new java.util.ArrayList<String>();
        var graphics = new GuiGraphics(minecraft, minecraft.renderBuffers().bufferSource()) {
            @Override public void blit(net.minecraft.resources.ResourceLocation texture, int x, int y, float u, float v,
                    int width, int height, int textureWidth, int textureHeight) {
                textures.add(texture.getPath());
                if (texture.equals(pageTexture)) {
                    paper[0] = pose().last().pose().m30(); paper[1] = pose().last().pose().m31(); papers[0]++;
                }
                super.blit(texture, x, y, u, v, width, height, textureWidth, textureHeight);
            }
        };
        var full = new MapLayers(false);
        var mini = new MapLayers(true);
        boolean fullNames = full.visible(MapLayers.Layer.VILLAGE_NAMES), miniNames = mini.visible(MapLayers.Layer.VILLAGE_NAMES);
        var original = screen.view();
        long[] now = {0};
        try {
            int x = (screen.width - 104) / 2, y = screen.height - 10;
            screen.render(graphics, x, y, 0); graphics.flush();
            require(screen.mouseClicked(x, y, 0) && minecraft.screen instanceof MapLayersScreen,
                    "clicking Map Settings opens the paper settings screen");
            minecraft.setScreen(screen);
            screen.render(graphics, x, y, 0); graphics.flush();
            float restingTop = screen.settingsPage().top(), restingLeft = screen.settingsPage().left();
            var settings = new MapLayersScreen(screen, () -> now[0]);
            minecraft.setScreen(settings);
            require(screen.children().size() == 1 && screen.children().getFirst() instanceof MapZoomScale,
                    "the paper replaces the old Layers button");
            float halfway = 0;
            for (int phase = 0; phase < 3; phase++) {
                now[0] = phase * 200_000_000L;
                papers[0] = 0; textures.clear();
                target.bindWrite(true);
                settings.render(graphics, -1, -1, 0); graphics.flush();
                require(papers[0] == 1, "the raised settings page replaces the resting sheet without drawing a duplicate");
                require(screen.settingsPage().top() == restingTop && screen.settingsPage().left() == restingLeft,
                        "the hidden resting page retains its parallax and hover position during the settings animation");
                require(textures.indexOf(pageTexture.getPath()) > textures.indexOf("textures/gui/map_wood_corner.png"),
                        "the raised paper renders after the map decorations");
                if (phase == 0) require(paper[1] == restingTop && settings.progress() == 0, "the settings page starts at the clicked paper position without snapping");
                if (phase == 1) { halfway = paper[1]; require(settings.progress() == .5f && halfway < restingTop && halfway > 10, "the paper moves smoothly upward over 400 milliseconds"); }
                if (phase == 2) require(paper[1] == 10 && settings.progress() == 1, "the paper finishes at its expanded position");
                try (var image = new NativeImage(target.width, target.height, false)) {
                    RenderSystem.bindTexture(target.getColorTextureId()); image.downloadTexture(0, false); image.flipY();
                    image.writeToFile(Path.of(System.getProperty("villagerretaliation.mapLocationClientSmoke"), "map-settings-" + phase + ".png"));
                }
            }
            double left = paper[0], top = paper[1];
            require(settings.children().size() == 7, "the settings paper retains both map selectors and all five layer controls");
            settings.mouseClicked(left + 28, top + 85, 0);
            require(full.visible(MapLayers.Layer.VILLAGE_NAMES) != fullNames && mini.visible(MapLayers.Layer.VILLAGE_NAMES) == miniNames,
                    "the full-map checkbox saves its preference without changing the minimap");
            textures.clear(); settings.render(graphics, -1, -1, 0); graphics.flush();
            require(textures.contains("textures/gui/checkbox_black_filled.png") && textures.contains("textures/gui/checbox_black.png"),
                    "the layer controls render the supplied checked and unchecked textures");
            settings.mouseClicked(left + 28, top + 85, 0);
            settings.mouseClicked(left + 220, top + 50, 0);
            settings.mouseClicked(left + 28, top + 85, 0);
            require(mini.visible(MapLayers.Layer.VILLAGE_NAMES) != miniNames && full.visible(MapLayers.Layer.VILLAGE_NAMES) == fullNames,
                    "the minimap selector edits its own saved layer preferences");
            settings.mouseClicked(left + 28, top + 85, 0);
            settings.mouseScrolled(10, 100, 0, 1);
            require(screen.view().equals(original), "settings input cannot zoom the underlying map");
            require(settings.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0) && minecraft.screen == settings && settings.progress() == 1,
                    "Escape starts closing the paper without snapping back");
            now[0] += 200_000_000L;
            settings.render(graphics, -1, -1, 0); graphics.flush();
            require(settings.progress() == .5f && paper[1] > 10, "closing slides the page back toward the bottom edge");
            now[0] += 200_000_000L;
            settings.render(graphics, -1, -1, 0); graphics.flush();
            require(paper[0] == restingLeft && paper[1] == restingTop, "closing ends at the same resting paper position without a final snap");
            settings.tick();
            require(minecraft.screen == screen && screen.view().equals(original) && screen.hudProgress() == 1,
                    "closing settings preserves the map view and does not replay its entrance");
            System.out.println("Map settings checks passed: title, paper replacement, foreground layering, smooth opening and closing, checkbox textures, independent saved map preferences, input occlusion and view preservation");
        } finally {
            if (minecraft.screen != screen) minecraft.setScreen(screen);
            if (full.visible(MapLayers.Layer.VILLAGE_NAMES) != fullNames) full.toggle(MapLayers.Layer.VILLAGE_NAMES);
            if (mini.visible(MapLayers.Layer.VILLAGE_NAMES) != miniNames) mini.toggle(MapLayers.Layer.VILLAGE_NAMES);
            target.bindWrite(true); screen.render(graphics, -1, -1, 0); graphics.flush();
        }
    }

    private static void verifyRelationsPage(ExploredMapScreen screen, TextureTarget target) throws IOException {
        var minecraft = Minecraft.getInstance();
        boolean hidden = minecraft.options.hideGui;
        var layers = new MapLayers(false);
        boolean original = layers.visible(MapLayers.Layer.RELATIONS);
        var tooltips = new java.util.ArrayList<java.util.List<String>>();
        var textures = new java.util.ArrayList<net.minecraft.resources.ResourceLocation>();
        var selected = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("villagerretaliation", "textures/gui/map_left_page_1_selected.png");
        var graphics = new GuiGraphics(minecraft, minecraft.renderBuffers().bufferSource()) {
            @Override public void renderComponentTooltip(net.minecraft.client.gui.Font font,
                    java.util.List<net.minecraft.network.chat.Component> lines, int x, int y) {
                tooltips.add(lines.stream().map(net.minecraft.network.chat.Component::getString).toList());
                super.renderComponentTooltip(font, lines, x, y);
            }
            @Override public void blit(net.minecraft.resources.ResourceLocation texture, int x, int y, float u, float v,
                    int width, int height, int textureWidth, int textureHeight) {
                textures.add(texture);
                super.blit(texture, x, y, u, v, width, height, textureWidth, textureHeight);
            }
        };
        try {
            target.bindWrite(true);
            screen.render(graphics, 25, 30, 0);
            graphics.flush();
            require(textures.contains(selected) && tooltips.size() == 1 && tooltips.getFirst().getFirst().equals("Relations"),
                    "hovering the front page swaps its texture and shows the Relations tooltip");
            try (var image = new NativeImage(target.width, target.height, false)) {
                RenderSystem.bindTexture(target.getColorTextureId());
                image.downloadTexture(0, false);
                image.flipY();
                image.writeToFile(Path.of(System.getProperty("villagerretaliation.mapLocationClientSmoke"), "map-relations-page-hover.png"));
            }
            screen.mouseClicked(25, 30, 0);
            require(minecraft.screen == screen && layers.visible(MapLayers.Layer.RELATIONS) != original,
                    "the front page performs the original Relations button action");
            screen.mouseClicked(25, 30, 0);
            require(layers.visible(MapLayers.Layer.RELATIONS) == original, "clicking the page again restores the previous relations layer state");
            tooltips.clear(); textures.clear();
            screen.render(graphics, 100, 40, 0);
            graphics.flush();
            require(tooltips.isEmpty() && !textures.contains(selected), "the exposed second page has no hover action or selected texture");
            screen.mouseClicked(100, 40, 0);
            screen.mouseReleased(100, 40, 0);
            require(layers.visible(MapLayers.Layer.RELATIONS) == original, "the second page does not activate Relations");
            minecraft.options.hideGui = true;
            screen.render(graphics, 25, 30, 0);
            graphics.flush();
            screen.mouseClicked(25, 30, 0);
            screen.mouseReleased(25, 30, 0);
            require(tooltips.isEmpty() && layers.visible(MapLayers.Layer.RELATIONS) == original, "hidden pages cannot show tooltips or activate Relations");
            System.out.println("Map relations page checks passed: selected texture, tooltip, original Relations action, unchanged second page and F1 visibility");
        } finally {
            minecraft.options.hideGui = hidden;
            if (layers.visible(MapLayers.Layer.RELATIONS) != original) layers.toggle(MapLayers.Layer.RELATIONS);
            screen.render(graphics, -1, -1, 0);
            graphics.flush();
        }
    }

    private static void verifyTabNavigation(ExploredMapScreen screen, TextureTarget target) throws IOException {
        var minecraft = Minecraft.getInstance();
        boolean hidden = minecraft.options.hideGui;
        var original = screen.view();
        var tooltips = new java.util.ArrayList<java.util.List<String>>();
        var graphics = new GuiGraphics(minecraft, minecraft.renderBuffers().bufferSource()) {
            @Override public void renderComponentTooltip(net.minecraft.client.gui.Font font,
                    java.util.List<net.minecraft.network.chat.Component> lines, int x, int y) {
                tooltips.add(lines.stream().map(net.minecraft.network.chat.Component::getString).toList());
                super.renderComponentTooltip(font, lines, x, y);
            }
        };
        Class<?>[] destinations = {SettlementRoutesScreen.class,
                com.jvn.villagerretaliation.client.settlement.VillageRelationsScreen.class, MapSearchScreen.class};
        String[] labels = {"Routes", "Villages", "Locations"};
        int[] ys = {30, 85, 135};
        int x = screen.width - 15;
        try {
            for (int tab = 0; tab < destinations.length; tab++) {
                tooltips.clear();
                target.bindWrite(true);
                screen.render(graphics, x, ys[tab], 0);
                graphics.flush();
                require(tooltips.size() == 1 && tooltips.getFirst().size() == 2
                        && tooltips.getFirst().getFirst().equals(labels[tab]), "each tab shows its destination and description on hover");
                try (var image = new NativeImage(target.width, target.height, false)) {
                    RenderSystem.bindTexture(target.getColorTextureId());
                    image.downloadTexture(0, false);
                    image.flipY();
                    image.writeToFile(Path.of(System.getProperty("villagerretaliation.mapLocationClientSmoke"), "map-tab-" + (tab + 1) + "-tooltip.png"));
                }
                require(screen.mouseClicked(x, ys[tab], 0) && destinations[tab].isInstance(minecraft.screen),
                        "left-clicking each tab opens its original GUI");
                minecraft.setScreen(screen);
                require(screen.view().equals(original) && screen.hudProgress() == 1,
                        "returning from a tab preserves the map view without replaying its entrance");
            }
            tooltips.clear();
            target.bindWrite(true);
            screen.render(graphics, x, 44, 0);
            graphics.flush();
            require(tooltips.size() == 1 && tooltips.getFirst().getFirst().equals("Routes"),
                    "the removed Layers button leaves the route tab exposed");
            tooltips.clear();
            minecraft.options.hideGui = true;
            screen.render(graphics, x, ys[2], 0);
            graphics.flush();
            require(tooltips.isEmpty(), "hidden tabs cannot display tooltips");
            screen.mouseClicked(x, ys[2], 0);
            screen.mouseReleased(x, ys[2], 0);
            require(minecraft.screen == screen, "hidden tabs cannot open their GUIs");
            System.out.println("Map tab navigation checks passed: Routes, Villages and Locations destinations, hover descriptions, overlay priority, preserved view, screen resume and F1 visibility");
        } finally {
            if (minecraft.screen != screen) minecraft.setScreen(screen);
            minecraft.options.hideGui = hidden;
            target.bindWrite(true);
            screen.render(graphics, -1, -1, 0);
            graphics.flush();
        }
    }

    private static void verifyCompassTooltip(ExploredMapScreen screen, TextureTarget target) throws IOException {
        var minecraft = Minecraft.getInstance();
        boolean hidden = minecraft.options.hideGui;
        var tooltips = new java.util.ArrayList<java.util.List<String>>();
        var graphics = new GuiGraphics(minecraft, minecraft.renderBuffers().bufferSource()) {
            @Override public void renderComponentTooltip(net.minecraft.client.gui.Font font,
                    java.util.List<net.minecraft.network.chat.Component> lines, int x, int y) {
                tooltips.add(lines.stream().map(net.minecraft.network.chat.Component::getString).toList());
                super.renderComponentTooltip(font, lines, x, y);
            }
        };
        int x = screen.width - MapCompass.PADDING - MapCompass.SIZE / 2;
        int y = screen.height - MapCompass.PADDING - MapCompass.SIZE / 2;
        try {
            target.bindWrite(true);
            screen.render(graphics, x, y, 0);
            graphics.flush();
            require(tooltips.size() == 1 && tooltips.getFirst().equals(MapCompass.tooltip(minecraft.player,
                    minecraft.player.getViewYRot(0)).stream().map(net.minecraft.network.chat.Component::getString).toList()),
                    "hovering the rendered compass shows the three-line player tooltip");
            try (var image = new NativeImage(target.width, target.height, false)) {
                RenderSystem.bindTexture(target.getColorTextureId());
                image.downloadTexture(0, false);
                image.flipY();
                image.writeToFile(Path.of(System.getProperty("villagerretaliation.mapLocationClientSmoke"), "map-compass-tooltip.png"));
            }
            tooltips.clear();
            minecraft.options.hideGui = true;
            screen.render(graphics, x, y, 0);
            graphics.flush();
            require(tooltips.isEmpty(), "hidden compass cannot show a tooltip");
            System.out.println("Map compass tooltip checks passed: live player details, circular hover and F1 visibility");
        } finally {
            minecraft.options.hideGui = hidden;
            screen.render(graphics, -1, -1, 0);
            graphics.flush();
        }
    }

    private static void verifyZoomScale(ExploredMapScreen screen, TextureTarget target) throws IOException {
        var scale = screen.children().stream().filter(MapZoomScale.class::isInstance).map(MapZoomScale.class::cast).findFirst().orElseThrow();
        var original = screen.view();
        try {
            require(scale.getX() == MapCompass.PADDING && scale.getBottom() == screen.height - MapCompass.PADDING,
                    "the vertical ruler uses the compass inset in the bottom-left corner");
            int rulerPixels = scale.rulerPixels(), rulerSegments = scale.segments();
            int[] reference = null;
            for (double zoom : new double[]{.16, .5, 1, 2.5, 8, 24}) {
                ExploredMapClientSmoke.zoom(screen, zoom);
                require(scale.rulerPixels() == rulerPixels && scale.segments() == rulerSegments,
                        "ruler length and divisions stay fixed throughout the full zoom range");
                for (int index = 0; index <= scale.segments(); index++)
                    require(Math.abs(scale.blocks(index) * screen.view().scale() - index * (double) rulerPixels / rulerSegments) < .00001,
                            "each fixed ruler graduation measures the actual map distance");
                target.setClearColor(0, 0, 0, 0);
                target.clear(Minecraft.ON_OSX);
                target.bindWrite(true);
                var minecraft = Minecraft.getInstance();
                var labels = new java.util.ArrayList<String>();
                var graphics = new GuiGraphics(minecraft, minecraft.renderBuffers().bufferSource()) {
                    @Override public int drawString(net.minecraft.client.gui.Font font, net.minecraft.network.chat.Component text,
                            int x, int y, int color, boolean shadow) {
                        labels.add(text.getString());
                        return super.drawString(font, text, x, y, color, shadow);
                    }
                };
                scale.render(graphics, -1, -1, 0);
                require(labels.size() == 2 && labels.getLast().endsWith(" blocks") && !labels.getLast().contains("."),
                        "the simplified ruler has only a zoom heading and one whole-number distance label");
                graphics.flush();
                try (var image = new NativeImage(target.width, target.height, false)) {
                    RenderSystem.bindTexture(target.getColorTextureId());
                    image.downloadTexture(0, false);
                    image.flipY();
                    int bottom = scale.getBottom() - minecraft.font.lineHeight / 2 - 1;
                    int[] geometry = new int[(rulerPixels + 3) * 7];
                    for (int py = 0; py < rulerPixels + 3; py++) for (int px = 0; px < 7; px++)
                        geometry[px + py * 7] = image.getPixelRGBA(scale.getX() + px, bottom - rulerPixels - 1 + py);
                    require(java.util.Arrays.stream(geometry).filter(color -> color >>> 24 != 0).count() > 80,
                            "the fixed ruler geometry comparison contains a visible line and ticks");
                    if (reference == null) reference = geometry;
                    else require(java.util.Arrays.equals(reference, geometry), "the rendered ruler line and ticks do not change shape or position while zooming");
                    image.writeToFile(Path.of(System.getProperty("villagerretaliation.mapLocationClientSmoke"), "map-zoom-scale-" + zoom + ".png"));
                }
            }
            ExploredMapClientSmoke.zoom(screen, MapNavigation.DEFAULT_SCALE);
            int x = scale.getX() + 12, y = scale.getBottom() - 24;
            var before = screen.view();
            require(screen.mouseScrolled(x, y, 0, 1), "scrolling over the ruler adjusts zoom");
            require(screen.view().scale() > before.scale() && screen.view().x() == before.x() && screen.view().z() == before.z(),
                    "ruler wheel zoom keeps the map center fixed");
            before = screen.view();
            require(screen.mouseClicked(x, y, 0), "the ruler accepts drag focus");
            screen.mouseDragged(x, y - 16, 0, 0, -16);
            screen.mouseReleased(x, y - 16, 0);
            require(screen.view().scale() > before.scale() && screen.view().x() == before.x() && screen.view().z() == before.z(),
                    "dragging upward zooms without panning the map");
            before = screen.view();
            require(screen.keyPressed(GLFW.GLFW_KEY_UP, 0, 0) && screen.view().scale() > before.scale(),
                    "focused ruler supports keyboard zoom");
            screen.keyReleased(GLFW.GLFW_KEY_UP, 0, 0);
            screen.keyPressed(GLFW.GLFW_KEY_HOME, 0, 0);
            require(screen.view().scale() == MapNavigation.DEFAULT_SCALE, "Home restores default zoom while the ruler is focused");
            try (var image = render(screen, target)) {
                image.writeToFile(Path.of(System.getProperty("villagerretaliation.mapLocationClientSmoke"), "map-zoom-scale.png"));
            }
            target.setClearColor(0, 0, 0, 0);
            target.clear(Minecraft.ON_OSX);
            target.bindWrite(true);
            var minecraft = Minecraft.getInstance();
            var graphics = new GuiGraphics(minecraft, minecraft.renderBuffers().bufferSource());
            scale.render(graphics, -1, -1, 0);
            graphics.flush();
            try (var image = new NativeImage(target.width, target.height, false)) {
                RenderSystem.bindTexture(target.getColorTextureId());
                image.downloadTexture(0, false);
                image.flipY();
                int ink = 0, clear = 0;
                for (int py = scale.getY(); py < scale.getBottom(); py++) for (int px = scale.getX(); px < scale.getRight(); px++) {
                    int color = image.getPixelRGBA(px, py);
                    if (color >>> 24 == 0) clear++;
                    else { require((color & 0xffffff) == 0, "the ruler draws only black ink"); ink++; }
                }
                require(ink > 100 && clear > scale.getWidth() * scale.getHeight() * .85, "the ruler leaves its background transparent");
            }
            screen.mouseScrolled(x, y, 0, 1000);
            require(screen.view().scale() == 24, "ruler zoom stops at the upper map limit");
            screen.mouseScrolled(x, y, 0, -1000);
            double minimum = screen.view().scale();
            screen.mouseScrolled(x, y, 0, -1);
            require(screen.view().scale() == minimum && minimum > 0, "ruler zoom stops at a valid lower map limit");
            screen.mouseClicked(screen.width / 2.0, screen.height - 64, 0);
            screen.mouseReleased(screen.width / 2.0, screen.height - 64, 0);
            require(screen.getFocused() == null, "clicking the map releases ruler keyboard focus");
            System.out.println("Map zoom scale checks passed: fixed geometry across zoom levels, compass inset, distance accuracy, black ink, transparent background, graduations, center-anchored wheel and drag zoom, keyboard access, reset and zoom limits");
        } finally {
            screen.setFocused(null);
            ExploredMapClientSmoke.navigation(screen).clear();
            ExploredMapClientSmoke.navigation(screen).focus(original.x(), original.z());
            ExploredMapClientSmoke.zoom(screen, original.scale());
        }
    }

    private static void verifyReset(ExploredMapScreen screen) {
        var player = Minecraft.getInstance().player;
        var navigation = ExploredMapClientSmoke.navigation(screen);
        navigation.focus(player.getX() + 200, player.getZ() - 100);
        navigation.zoomAt(screen.width / 2.0, screen.height / 2.0, 4);
        navigation.beginDrag();
        navigation.press(GLFW.GLFW_KEY_RIGHT);
        var before = screen.view();
        require(screen.mouseClicked(screen.width * .75, screen.height * .75, GLFW.GLFW_MOUSE_BUTTON_MIDDLE),
                "middle click resets the map with the HUD visible or hidden");
        require(screen.view().equals(before), "middle click starts without snapping the position or zoom");
        require(!navigation.drag(10, 10), "reset clears the active drag");
        navigation.update(1, true);
        navigation.update(225_000_001, true);
        var halfway = screen.view();
        require(halfway.x() < before.x() && halfway.x() > player.getX()
                && halfway.z() > before.z() && halfway.z() < player.getZ(), "reset pans toward the player over time");
        require(halfway.scale() < before.scale() && halfway.scale() > MapNavigation.DEFAULT_SCALE,
                "reset zooms while panning");
        navigation.update(450_000_001, true);
        require(screen.view().x() == player.getX() && screen.view().z() == player.getZ(),
                "middle click finishes centered on the player rather than the cursor");
        require(screen.view().scale() == MapNavigation.DEFAULT_SCALE, "middle click finishes at the default zoom");
        var reset = screen.view();
        navigation.update(500_000_001, true);
        require(!navigation.resetting() && screen.view().equals(reset), "reset finishes exactly and clears held navigation keys");
        verifyResetCancellation();
    }

    private static void verifyResetCancellation() {
        for (int action = 0; action < 6; action++) {
            var navigation = new MapNavigation();
            navigation.resize(640, 360);
            navigation.focus(200, -100);
            navigation.zoomAt(320, 180, 4);
            navigation.resetTo(0, 0);
            navigation.update(1, true);
            navigation.update(100_000_001, true);
            switch (action) {
                case 0 -> { navigation.beginDrag(); navigation.drag(10, 10); navigation.endDrag(); }
                case 1 -> navigation.zoomAt(320, 180, 5);
                case 2 -> { navigation.press(GLFW.GLFW_KEY_RIGHT); navigation.release(GLFW.GLFW_KEY_RIGHT); }
                case 3 -> navigation.update(150_000_001, false);
                case 4 -> navigation.resize(320, 180);
                case 5 -> navigation.focus(-20, 30);
            }
            var interrupted = navigation.view();
            navigation.update(600_000_001, true);
            require(!navigation.resetting() && navigation.view().equals(interrupted),
                    "manual navigation, focus loss and resizing cancel a pending reset (action " + action + ")");
        }
    }

    private static NativeImage render(ExploredMapScreen screen, TextureTarget target) {
        target.bindWrite(true);
        var minecraft = Minecraft.getInstance();
        var graphics = new GuiGraphics(minecraft, minecraft.renderBuffers().bufferSource());
        screen.render(graphics, -1, -1, 0);
        graphics.flush();
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
