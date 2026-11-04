package com.jvn.villagerretaliation.client.settlement;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

final class SettlementHoverClientChecks {
    private SettlementHoverClientChecks() {
    }

    static void verify(Minecraft minecraft) {
        var source = new TextureTarget(256, 256, true, Minecraft.ON_OSX);
        var output = new TextureTarget(256, 256, true, Minecraft.ON_OSX);
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        try {
            var field = SettlementHoverRenderer.class.getDeclaredField("shader");
            field.setAccessible(true);
            var shader = (ShaderInstance) field.get(null);
            require(shader != null, "the terrain hover shader loads");
            source.bindWrite(true);
            GL11.glClearDepth(0.5);
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
            GL11.glClearDepth(1);
            shader.setSampler("SceneDepth", source.getDepthTextureId());
            shader.safeGetUniform("Cursor").set(0F, 0F, 0F);
            shader.safeGetUniform("CameraOffset").set(0F, 0F, 0F);
            var ground = new Matrix4f().zero().m00(10).m12(10).m21(1).m33(1);
            shader.safeGetUniform("InverseViewProjection").set(ground);
            output.setClearColor(0, 0, 0, 0);
            RenderSystem.disableDepthTest();
            RenderSystem.disableCull();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            var pixels = draw(output, shader);
            int edge = red(pixels, 128, 134);
            int grid = red(pixels, 128, 179);
            require(edge > 140 && red(pixels, 140, 134) > 140
                    && red(pixels, 134, 128) > 140 && red(pixels, 134, 140) > 140,
                    "the hovered block has four bright square edges");
            require(red(pixels, 134, 134) < edge / 4, "the hovered square keeps its center clear");
            require(red(pixels, 115, 128) < edge / 2, "the square does not extend into a cross");
            require(grid > 5 && grid < edge / 2, "the surrounding grid fades below the hovered square");
            require(red(pixels, 128, 251) == 0, "the grid fades out beyond its radius");
            require(red(pixels, 185, 134) == 0, "space between grid lines stays clear");

            shader.safeGetUniform("CameraOffset").set(-100F, 0F, -100F);
            shader.safeGetUniform("Cursor").set(-100F, 0F, -100F);
            require(java.util.Arrays.equals(pixels, draw(output, shader)), "the grid remains aligned at negative world coordinates");
            shader.safeGetUniform("Cursor").set(0F, 0F, 0F);
            shader.safeGetUniform("CameraOffset").set(0F, 4F, 0F);
            require(empty(draw(output, shader)), "surfaces well above the cursor do not receive the grid");
            shader.safeGetUniform("CameraOffset").set(0F, 0F, 0F);
            shader.safeGetUniform("InverseViewProjection").set(new Matrix4f().scale(10, 10, 1));
            require(empty(draw(output, shader)), "vertical walls do not receive the ground grid");
            shader.safeGetUniform("InverseViewProjection").set(ground);
            source.bindWrite(true);
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
            require(empty(draw(output, shader)), "sky pixels do not receive the grid");
            require(GL11.glGetError() == GL11.GL_NO_ERROR, "the hover shader renders without OpenGL errors");
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        } finally {
            source.destroyBuffers();
            output.destroyBuffers();
            minecraft.getMainRenderTarget().bindWrite(true);
            if (depth) RenderSystem.enableDepthTest();
            else RenderSystem.disableDepthTest();
            if (cull) RenderSystem.enableCull();
            else RenderSystem.disableCull();
            if (blend) RenderSystem.enableBlend();
            else RenderSystem.disableBlend();
        }
    }

    static void pointAtGround(Minecraft minecraft, SettlementViewScreen view) {
        try {
            var world = SettlementViewScreen.class.getDeclaredMethod("worldContains", double.class, double.class);
            var rayMethod = SettlementViewScreen.class.getDeclaredMethod("ray", double.class, double.class);
            world.setAccessible(true);
            rayMethod.setAccessible(true);
            for (int y = view.height / 2; y < view.height - 40; y += 15) {
                for (int x = view.viewportWidth() / 2; x > 40; x -= 15) {
                    if (!(Boolean) world.invoke(view, (double) x, (double) y)) continue;
                    var ray = (SettlementSelectionGizmo.Ray) rayMethod.invoke(view, (double) x, (double) y);
                    var hit = SettlementViewPeek.clip(minecraft.level, ray.origin(), ray.origin().add(ray.direction().scale(256)),
                            net.minecraft.world.level.ClipContext.Block.OUTLINE, minecraft.getCameraEntity());
                    if (hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS
                            || hit.getDirection() != net.minecraft.core.Direction.UP || !view.contains(hit.getBlockPos())
                            || !minecraft.level.getBlockState(hit.getBlockPos()).is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK)) continue;
                    org.lwjgl.glfw.GLFW.glfwSetCursorPos(minecraft.getWindow().getWindow(),
                            x * minecraft.getWindow().getScreenWidth() / (double) view.width,
                            y * minecraft.getWindow().getScreenHeight() / (double) view.height);
                    return;
                }
            }
            throw new IllegalStateException("the RTS fixture must have visible ground for the hover preview");
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    static void toggle(SettlementViewScreen view, boolean enabled) {
        view.children().stream().filter(child -> child instanceof net.minecraft.client.gui.components.Button button
                && button.getMessage().getString().equals("Overlays"))
                .map(child -> (net.minecraft.client.gui.components.Button) child).findFirst().orElseThrow().onPress();
        view.contextPopup().buttons().stream().filter(button ->
                button.getMessage().getString().equals("Hover grid: " + (enabled ? "Off" : "On")))
                .findFirst().orElseThrow().onPress();
        require(view.contextPopup() == null, "the hover toggle dismisses the overlays menu");
        require(SettlementViewPreferences.forSettlement(view.parent().snapshot().header().id()).hoverGrid == enabled,
                "the hover grid choice survives reloading view preferences");
        if (!enabled) {
            try {
                var field = SettlementViewScreen.class.getDeclaredField("groundHover");
                field.setAccessible(true);
                ((SettlementHoverRenderer) field.get(view)).clear();
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException(exception);
            }
        }
    }

    static void verifyDisabled(SettlementViewScreen view) {
        try {
            var renderer = SettlementViewScreen.class.getDeclaredField("groundHover");
            var depth = SettlementHoverRenderer.class.getDeclaredField("depth");
            renderer.setAccessible(true);
            depth.setAccessible(true);
            require(depth.get(renderer.get(view)) == null, "disabling the grid skips the terrain hover pass");
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    static void verifyRendered(SettlementViewScreen view) {
        try {
            var renderer = SettlementViewScreen.class.getDeclaredField("groundHover");
            var depth = SettlementHoverRenderer.class.getDeclaredField("depth");
            renderer.setAccessible(true);
            depth.setAccessible(true);
            require(depth.get(renderer.get(view)) != null, "the RTS viewport renders the ground hover pass");
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static byte[] draw(TextureTarget output, ShaderInstance shader) {
        output.clear(Minecraft.ON_OSX);
        output.bindWrite(true);
        RenderSystem.setShader(() -> shader);
        var vertices = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        vertices.addVertex(-1, -1, 0);
        vertices.addVertex(1, -1, 0);
        vertices.addVertex(1, 1, 0);
        vertices.addVertex(-1, 1, 0);
        BufferUploader.drawWithShader(vertices.buildOrThrow());
        var buffer = BufferUtils.createByteBuffer(256 * 256 * 4);
        GL11.glReadPixels(0, 0, 256, 256, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
        var pixels = new byte[buffer.capacity()];
        buffer.get(pixels);
        return pixels;
    }

    private static int red(byte[] pixels, int x, int y) {
        return Byte.toUnsignedInt(pixels[(x + y * 256) * 4]);
    }

    private static boolean empty(byte[] pixels) {
        for (byte pixel : pixels) if (pixel != 0) return false;
        return true;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
