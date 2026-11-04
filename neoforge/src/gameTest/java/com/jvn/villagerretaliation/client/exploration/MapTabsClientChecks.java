package com.jvn.villagerretaliation.client.exploration;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

final class MapTabsClientChecks {
    private MapTabsClientChecks() { }

    static void verify(ExploredMapScreen screen) {
        var minecraft = Minecraft.getInstance();
        var graphics = new GuiGraphics(minecraft, minecraft.renderBuffers().bufferSource());
        graphics.flush();
        var shader = RenderSystem.getShader();
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND), depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        var tabs = new MapTabs();
        RenderSystem.backupProjectionMatrix();
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix().identity();
        RenderSystem.applyModelViewMatrix();
        try {
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, 640, 360, 0, -1000, 1000), VertexSorting.ORTHOGRAPHIC_Z);
            RenderSystem.disableDepthTest();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            int left = 640 - MapTabs.WIDTH + MapTabs.OVERHANG, top = 0;
            var target = new TextureTarget(640, 360, false, Minecraft.ON_OSX);
            try {
                render(graphics, target, tabs, -1, -1, 0, 0, 0);
                require(tabs.at(left, top + 10) == -1 && tabs.at(640, top + 30) == -1,
                        "transparent padding and offscreen pixels are not hover targets");
                require(tabs.at(left + 30, top + 60) == 0, "the front tab receives overlapping clicks");
                int[] hoverY = {30, 85, 135};
                for (int index = 0; index < hoverY.length; index++) {
                    render(graphics, target, tabs, -1, -1, 0, 0, 0);
                    render(graphics, target, tabs, left + 30, top + hoverY[index], 0, 0, 0);
                    require(tabs.at(left + 30, top + hoverY[index]) == index, "each exposed tab can be hovered");
                }
                render(graphics, target, tabs, -1, -1, .5f, .5f, 0);
                for (int index = 0; index < hoverY.length; index++) {
                    double offset = (4 - index) * .5;
                    require(tabs.at(left + 25 - offset, top + hoverY[index] - offset) == index,
                            "tab hover masks track fractional positions");
                }
            } finally { target.destroyBuffers(); }
            var original = screen.view();
            int x = screen.width - 15, y = 30;
            screen.mouseClicked(x, y, 1);
            screen.mouseDragged(x + 10, y, 0, 10, 0);
            screen.mouseReleased(x + 10, y, 0);
            screen.mouseScrolled(x, y, 0, 1);
            require(minecraft.screen == screen && screen.view().equals(original), "right-clicking tabs does not open screens or pass dragging and scrolling through to the map");
            System.out.println("Map tab checks passed: transparent hit masks, overlapping tabs, moving hover targets and input occlusion");
        } finally {
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
            minecraft.getMainRenderTarget().bindWrite(true);
            RenderSystem.setShader(() -> shader);
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
        }
    }

    private static void render(GuiGraphics graphics, TextureTarget target, MapTabs tabs, int x, int y,
                               float mouseX, float mouseY, float zoom) {
        target.setClearColor(0, 0, 0, 0);
        target.clear(Minecraft.ON_OSX);
        target.bindWrite(true);
        tabs.render(graphics, 640, 360, x, y, mouseX, mouseY, zoom, 1, 1, 1);
        graphics.flush();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
