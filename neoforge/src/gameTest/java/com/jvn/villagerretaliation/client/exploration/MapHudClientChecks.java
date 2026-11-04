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
                        require(changed > pixels / 3, "hidden controls leave the terrain visible underneath");
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
                require(screen.mouseClicked(20, 44, 0), "clicking a hidden button's position starts map dragging");
                screen.mouseDragged(30, 44, 0, 10, 0);
                screen.mouseReleased(30, 44, 0);
                require(screen.view().x() != before && minecraft.screen == screen, "hidden controls do not intercept panning");
                double zoom = screen.view().scale();
                screen.mouseScrolled(screen.width / 2.0, screen.height / 2.0, 0, 1);
                require(screen.view().scale() > zoom, "scroll zoom remains available while the HUD is hidden");
                require(!screen.mouseClicked(screen.width / 2.0, screen.height / 2.0, 1), "right click cannot open hidden marker or placement menus");
                screen.resize(minecraft, screen.width, screen.height);
                require(screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast).noneMatch(widget -> widget.visible),
                        "resizing cannot reveal hidden map controls");
                require(screen.keyPressed(GLFW.GLFW_KEY_F1, 0, 0) && !minecraft.options.hideGui, "F1 restores the map HUD");
                screen.keyReleased(GLFW.GLFW_KEY_F1, 0, 0);
                require(screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast).allMatch(widget -> widget.visible),
                        "showing the HUD restores every map control");
                try (var restored = render(screen, target)) {
                    restored.writeToFile(Path.of(System.getProperty("villagerretaliation.mapLocationClientSmoke"), "map-hud-restored.png"));
                }
            }
            System.out.println("Map HUD checks passed: F1 toggle, repeat suppression, clean terrain rendering, hidden interactions, panning, zoom and resize");
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
