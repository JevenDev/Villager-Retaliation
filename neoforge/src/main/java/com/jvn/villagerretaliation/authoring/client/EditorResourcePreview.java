package com.jvn.villagerretaliation.authoring.client;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import imgui.ImGui;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

final class EditorResourcePreview {
    private static volatile long resourceGeneration;
    private long generation = -1;
    private final Map<String, TextureTarget> icons = new HashMap<>();
    private final Map<String, String> iconErrors = new HashMap<>();
    private TextureTarget entityTarget;
    private Entity entity;
    private String entityId = "", error = "";
    private boolean retired;

    static void resourcesReloaded() { resourceGeneration++; }
    static long generation() { return resourceGeneration; }

    void beginFrame(boolean active) {
        if (!active || retired || generation != resourceGeneration || icons.size() > 128) {
            icons.values().forEach(TextureTarget::destroyBuffers);
            icons.clear(); iconErrors.clear();
            if (entityTarget != null) entityTarget.destroyBuffers();
            entityTarget = null; entity = null; entityId = ""; error = "";
            generation = resourceGeneration; retired = false;
        }
    }

    void retire() { retired = true; }

    void icon(String kind, String id, float x, float y, float size) {
        var stack = EditorRegistryResources.icon(kind, id);
        if (stack.isEmpty()) return;
        String key = kind + ":" + id;
        TextureTarget target = icons.get(key);
        if (target == null && !iconErrors.containsKey(key)) {
            target = new TextureTarget(32, 32, true, Minecraft.ON_OSX);
            try {
                draw(target, graphics -> {
                    graphics.pose().scale(2, 2, 2);
                    graphics.renderFakeItem(stack, 0, 0);
                });
                icons.put(key, target);
            } catch (net.minecraft.ReportedException | IllegalArgumentException | IllegalStateException exception) {
                target.destroyBuffers(); target = null;
                iconErrors.put(key, exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
            }
        }
        if (target == null) {
            ImGui.getWindowDrawList().addText(x + size / 3, y + size / 4, ImGui.getColorU32(imgui.flag.ImGuiCol.TextDisabled), "?");
            if (ImGui.isMouseHoveringRect(x, y, x + size, y + size)) ImGui.setTooltip("Icon unavailable: " + iconErrors.get(key));
            return;
        }
        ImGui.getWindowDrawList().addImage(target.getColorTextureId(), x, y, x + size, y + size, 0, 1, 1, 0);
    }

    void entity(String id, float width, float height) {
        var minecraft = Minecraft.getInstance();
        if (!id.equals(entityId) || entity != null && entity.level() != minecraft.level) {
            entity = null; entityId = id; error = "";
            var type = ResourceLocation.tryParse(id);
            if (minecraft.level != null && type != null && BuiltInRegistries.ENTITY_TYPE.containsKey(type)) {
                try { entity = BuiltInRegistries.ENTITY_TYPE.get(type).create(minecraft.level); }
                catch (IllegalArgumentException | IllegalStateException exception) { error = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage(); }
            }
            if (entity == null && error.isEmpty()) error = "This entity has no standalone preview.";
            if (entity != null) {
                entity.setYRot(180); entity.yRotO = 180;
                if (entity instanceof net.minecraft.world.entity.LivingEntity living) {
                    living.yBodyRot = living.yBodyRotO = 180;
                    living.yHeadRot = living.yHeadRotO = 180;
                }
            }
        }
        if (entity == null || !error.isEmpty()) { ImGui.textWrapped("Preview unavailable: " + error); return; }
        if (entityTarget == null) entityTarget = new TextureTarget(160, 180, true, Minecraft.ON_OSX);
        try {
            draw(entityTarget, graphics -> {
                float scale = Math.min(130 / Math.max(.5f, entity.getBbHeight()), 130 / Math.max(.5f, entity.getBbWidth()));
                graphics.pose().pushPose();
                graphics.pose().translate(80, 90, 50);
                graphics.pose().scale(scale, scale, -scale);
                graphics.pose().translate(0, entity.getBbHeight() / 2, 0);
                graphics.pose().mulPose(new Quaternionf().rotateZ((float) Math.PI).rotateY(.4f));
                var dispatcher = minecraft.getEntityRenderDispatcher();
                var orientation = new Quaternionf(dispatcher.cameraOrientation());
                Lighting.setupForEntityInInventory();
                dispatcher.overrideCameraOrientation(new Quaternionf().rotateY((float) Math.PI));
                dispatcher.setRenderShadow(false);
                try {
                    RenderSystem.runAsFancy(() -> dispatcher.render(entity, 0, 0, 0, 0, 1, graphics.pose(), graphics.bufferSource(), 15728880));
                    graphics.flush();
                } finally {
                    dispatcher.setRenderShadow(true);
                    dispatcher.overrideCameraOrientation(orientation);
                    graphics.pose().popPose();
                    Lighting.setupFor3DItems();
                }
            });
            ImGui.image(entityTarget.getColorTextureId(), width, height, 0, 1, 1, 0);
        } catch (net.minecraft.ReportedException | IllegalArgumentException | IllegalStateException exception) {
            error = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            ImGui.textWrapped("Preview unavailable: " + error);
        }
    }

    private static void draw(TextureTarget target, Consumer<GuiGraphics> render) {
        int framebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int[] viewport = new int[4], scissorBox = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST), depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        if (scissor) GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissorBox);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND), cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        var shader = RenderSystem.getShader();
        float[] color = RenderSystem.getShaderColor().clone();
        var modelView = RenderSystem.getModelViewStack();
        RenderSystem.backupProjectionMatrix();
        modelView.pushMatrix().identity();
        RenderSystem.applyModelViewMatrix();
        try {
            RenderSystem.disableScissor();
            target.setClearColor(0, 0, 0, 0); target.clear(Minecraft.ON_OSX); target.bindWrite(true);
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, target.width, target.height, 0, -1000, 1000), VertexSorting.ORTHOGRAPHIC_Z);
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.enableDepthTest();
            var minecraft = Minecraft.getInstance();
            var graphics = new GuiGraphics(minecraft, minecraft.renderBuffers().bufferSource());
            try { render.accept(graphics); }
            finally { graphics.flush(); }
        } finally {
            modelView.popMatrix(); RenderSystem.applyModelViewMatrix(); RenderSystem.restoreProjectionMatrix();
            RenderSystem.setShader(() -> shader); RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
            RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            if (scissor) RenderSystem.enableScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]);
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            RenderSystem.depthMask(depthMask);
        }
    }
}
