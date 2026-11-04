package com.jvn.villagerretaliation.client.exploration;

import com.jvn.villagerretaliation.client.ui.VillagerClientUiUtil;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

final class MapEntranceClientChecks {
    private MapEntranceClientChecks() { }

    static void verify() {
        verifyTiming();
        var minecraft = Minecraft.getInstance();
        var graphics = new GuiGraphics(minecraft, minecraft.renderBuffers().bufferSource());
        graphics.flush();
        var shader = RenderSystem.getShader();
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND), depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        var target = new TextureTarget(640, 360, false, Minecraft.ON_OSX);
        var pages = new MapPages();
        pages.init();
        RenderSystem.backupProjectionMatrix();
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix().identity();
        RenderSystem.applyModelViewMatrix();
        try {
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, 640, 360, 0, -1000, 1000), VertexSorting.ORTHOGRAPHIC_Z);
            RenderSystem.disableDepthTest();
            verifyAlpha(graphics, target);
            int[][] visible = new int[3][3];
            float[] progress = {0, .3f, 1};
            for (int phase = 0; phase < progress.length; phase++) {
                target.setClearColor(0, 0, 0, 0);
                target.clear(Minecraft.ON_OSX);
                target.bindWrite(true);
                pages.render(graphics, 640, 360, 320, 180, MapNavigation.DEFAULT_SCALE, 1,
                        VillagerClientUiUtil.easeOutCubic(progress[phase]));
                MapCompass.render(graphics, 640, 360, 0, 1, pages.compassX(), pages.compassY());
                graphics.flush();
                try (var image = new NativeImage(640, 360, false)) {
                    RenderSystem.bindTexture(target.getColorTextureId());
                    image.downloadTexture(0, false);
                    image.flipY();
                    for (int y = 0; y < 360; y++) for (int x = 0; x < 640; x++) {
                        if (image.getPixelRGBA(x, y) >>> 24 == 0) continue;
                        require(phase != 0, "all decorative elements begin completely offscreen");
                        if (x < 200 && y < 220) visible[phase][0]++;
                        if (x >= 520 && y < 180) visible[phase][1]++;
                        if (x >= 490 && y >= 210) visible[phase][2]++;
                    }
                    image.writeToFile(Path.of(System.getProperty("villagerretaliation.mapLocationClientSmoke"), "map-hud-entrance-" + phase + ".png"));
                }
                if (phase == 0) require(!pages.overTab(620, 30)
                        && !MapCompass.hovered(640, 360, 598, 318, pages.compassX(), pages.compassY()),
                        "offscreen tabs and compass cannot be hovered at their resting positions");
            }
            for (int layer = 0; layer < 3; layer++)
                require(visible[1][layer] > 0 && visible[2][layer] > visible[1][layer],
                        "left pages, right tabs and the compass corner enter progressively from their edges");
            require(pages.overTab(620, 30) && MapCompass.hovered(640, 360, 598, 318, pages.compassX(), pages.compassY()),
                    "hover targets arrive with the tabs and compass");
            System.out.println("Map HUD entrance checks passed: transition sequencing, offscreen start, edge slides, moving hover targets, exact completion, skipped animation and screen resume");
        } catch (IOException exception) { throw new UncheckedIOException(exception); }
        finally {
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
            target.destroyBuffers();
            minecraft.getMainRenderTarget().bindWrite(true);
            RenderSystem.setShader(() -> shader);
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
        }
    }

    private static void verifyAlpha(GuiGraphics graphics, TextureTarget target) throws IOException {
        var minecraft = Minecraft.getInstance();
        var sprites = new NativeImage[5];
        try {
            String[] names = {"map_left_page_1", "map_left_page_2", "map_right_tab_1", "map_right_tab_2", "map_right_tab_3"};
            for (int index = 0; index < names.length; index++) {
                var texture = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("villagerretaliation", "textures/gui/" + names[index] + ".png");
                try (var input = minecraft.getResourceManager().getResourceOrThrow(texture).open()) { sprites[index] = NativeImage.read(input); }
            }
            for (boolean raised : new boolean[]{false, true}) {
                var pages = new MapPages(); pages.init(); pages.bottom().raised(raised);
                target.setClearColor(.25f, .5f, .75f, 1); target.clear(Minecraft.ON_OSX); target.bindWrite(true);
                RenderSystem.disableBlend();
                pages.render(graphics, 640, 360, 320, 180, MapNavigation.DEFAULT_SCALE, 1, 1);
                graphics.flush();
                try (var image = new NativeImage(640, 360, false)) {
                    RenderSystem.bindTexture(target.getColorTextureId()); image.downloadTexture(0, false); image.flipY();
                    int background = image.getPixelRGBA(320, 180);
                    require(background >>> 24 == 255 && (background & 0xffffff) != 0, "the alpha checks use an opaque colored backdrop");
                    int clearLeft = 0, clearRight = 0;
                    for (int y = 0; y < 150; y++) for (int x = 0; x < 124; x++) {
                        if (!opaque(sprites[0], x + 96, y + 68) && !opaque(sprites[1], x + 84, y + 68)) {
                            require(image.getPixelRGBA(x, y) == background, "transparent left page pixels remain clear after the settings title renders"); clearLeft++;
                        }
                    }
                    for (int y = 0; y < 155; y++) for (int x = 0; x < 44; x++) {
                        if (!opaque(sprites[2], x, y) && !opaque(sprites[3], x, y) && !opaque(sprites[4], x, y)) {
                            require(image.getPixelRGBA(596 + x, y) == background, "transparent right tab pixels remain clear with settings open or closed"); clearRight++;
                        }
                    }
                    require(clearLeft > 100 && clearRight > 100, "the alpha checks cover transparent pixels on both sides of the HUD");
                    image.writeToFile(Path.of(System.getProperty("villagerretaliation.mapLocationClientSmoke"), "map-paper-alpha-" + raised + ".png"));
                }
            }
            target.clear(Minecraft.ON_OSX); target.bindWrite(true);
            graphics.fill(0, 0, 640, 360, 0xff567890);
            graphics.pose().pushPose(); graphics.pose().translate(146, 10, 300);
            RenderSystem.disableBlend();
            MapBottomPage.renderPaper(graphics, true);
            graphics.pose().popPose(); graphics.flush();
            try (var image = new NativeImage(640, 360, false)) {
                RenderSystem.bindTexture(target.getColorTextureId()); image.downloadTexture(0, false); image.flipY();
                require(image.getPixelRGBA(146, 10) == image.getPixelRGBA(145, 10),
                        "the expanded settings paper preserves the backdrop through its transparent corner after the dimming fill");
            }
            System.out.println("Map paper alpha checks passed: integrated left pages, right tabs, open and closed settings, title/divider state changes and transparent modal corners");
        } finally { for (var sprite : sprites) if (sprite != null) sprite.close(); }
    }

    private static boolean opaque(NativeImage image, int x, int y) {
        return x >= 0 && y >= 0 && x < image.getWidth() && y < image.getHeight() && image.getPixelRGBA(x, y) >>> 24 != 0;
    }

    private static void verifyTiming() {
        long[] now = {0};
        var transition = new MapScreenTransition(() -> now[0]);
        transition.open();
        require(transition.hudProgress(true) == 0, "HUD entrance waits for the map opening");
        now[0] = 1_249_000_000;
        require(transition.hudProgress(true) == 0, "HUD entrance stays hidden until the map transition completes");
        now[0] = 1_250_000_000;
        require(transition.progress(true) == 1 && transition.hudProgress(true) == 0, "HUD entrance starts after the map transition");
        now[0] += 225_000_000;
        require(Math.abs(transition.hudProgress(true) - .5f) < .00001f, "HUD entrance advances over 450 milliseconds");
        now[0] += 225_000_000;
        require(transition.hudProgress(true) == 1, "HUD entrance finishes exactly at its resting positions");
        transition.close(true);
        require(transition.hudProgress(true) == 1, "closing does not restart the entrance");
        var resumed = new MapScreenTransition(() -> now[0]);
        resumed.open();
        resumed.suspend();
        resumed.open();
        require(resumed.hudProgress(true) == 1, "returning from another screen does not replay the entrance");
        var skipped = new MapScreenTransition(() -> now[0]);
        skipped.open();
        skipped.finishOpening();
        require(skipped.hudProgress(true) == 1, "manual input skips both opening phases");
        var disabled = new MapScreenTransition(() -> now[0]);
        disabled.open();
        require(disabled.hudProgress(false) == 1 && disabled.hudProgress(true) == 1,
                "disabled opening animations show the HUD immediately without a later replay");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
