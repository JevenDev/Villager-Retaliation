package com.jvn.villagerretaliation.authoring.client;

import com.jvn.villagerretaliation.client.interaction.VillagerStyledTextRenderer;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiChildFlags;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

final class EditorTextPreview {
    private static final List<EditorTextPreview> RETIRED = new ArrayList<>();
    private TextureTarget target;
    private float scroll;
    private int contentHeight;
    private int contentWidth;
    private boolean clipped;
    private boolean renderedSingleLine;
    private String text = "";
    private boolean animated;
    private int renderedWidth, renderedHeight, renderedBackground;
    private float renderedScale, renderedScroll;
    private long renderedTick = -1;

    boolean render(String source) {
        boolean activate = false;
        if (ImGui.beginChild("##preview", 0, 112, ImGuiChildFlags.FrameStyle | ImGuiChildFlags.NavFlattened)) {
            float scale = (float) ImGui.getFontSize() / Minecraft.getInstance().font.lineHeight;
            if (!source.equals(text)) {
                ImGui.setScrollY(0);
                source(source);
            }
            int width = Math.max(1, (int) ImGui.getContentRegionAvailX());
            int height = Math.max(1, (int) (ImGui.getWindowSizeY() - 2 * ImGui.getStyle().getWindowPaddingY()));
            var origin = ImGui.getCursorScreenPos();
            origin.y += ImGui.getScrollY();
            scroll = ImGui.getScrollY() / scale;
            int background = ImGui.getColorU32(ImGuiCol.FrameBg);
            ImGui.pushStyleColor(ImGuiCol.Button, background);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, background);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, background);
            activate = ImGui.button("##activate", width, Math.max(height, contentHeight * scale));
            ImGui.popStyleColor(3);
            activate |= ImGui.isItemFocused();
            draw(width, height, scale, background, false);
            ImGui.getWindowDrawList().addImage(target.getColorTextureId(), origin.x, origin.y,
                    origin.x + width, origin.y + height, 0, 1, 1, 0);
            if (ImGui.isItemHovered()) ImGui.setTooltip("Click to edit text and formatting tags.");
        }
        ImGui.endChild();
        return activate;
    }

    boolean image(String source, float x, float y, float width, float height, float fontSize, int background, boolean singleLine) {
        source(source);
        scroll = 0;
        float scale = fontSize / Minecraft.getInstance().font.lineHeight;
        draw(Math.max(1, (int) Math.ceil(width)), Math.max(1, (int) Math.ceil(height)), scale, background, singleLine);
        ImGui.getWindowDrawList().addImage(target.getColorTextureId(), x, y, x + width, y + height, 0, 1, 1, 0);
        clipped = singleLine ? contentWidth + 6 > width / scale : contentHeight - 3 > height / scale + 0.5f;
        return clipped;
    }

    private void source(String source) {
        if (source.equals(text)) return;
        text = source;
        renderedTick = -1;
        animated = com.jvn.villagerretaliation.dialogue.normal.DialogueTextSegment.parse(text,
                com.jvn.villagerretaliation.dialogue.normal.DialogueTextEffects.NONE).stream().anyMatch(segment -> {
                    var effects = segment.effects();
                    return effects.wavy() || effects.shake() || effects.pulse() || effects.jump() || effects.rainbow() || effects.obfuscated();
                });
    }

    private void draw(int width, int height, float scale, int background, boolean singleLine) {
        long tick = (long) (ImGui.getTime() * (animated ? 20 : 1));
        if (target != null && tick == renderedTick && renderedWidth == width && renderedHeight == height
                && renderedScale == scale && renderedScroll == scroll && renderedBackground == background
                && renderedSingleLine == singleLine) return;
        int framebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        int[] scissorBox = new int[4];
        if (scissor) GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissorBox);
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        var shader = RenderSystem.getShader();
        var modelView = RenderSystem.getModelViewStack();
        RenderSystem.backupProjectionMatrix();
        modelView.pushMatrix().identity();
        RenderSystem.applyModelViewMatrix();
        try {
            RenderSystem.disableScissor();
            if (target == null) target = new TextureTarget(width, height, false, Minecraft.ON_OSX);
            else if (target.width != width || target.height != height) target.resize(width, height, Minecraft.ON_OSX);
            target.setClearColor((background & 255) / 255f, (background >> 8 & 255) / 255f, (background >> 16 & 255) / 255f, 1);
            target.clear(Minecraft.ON_OSX);
            target.bindWrite(true);
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, width / scale, height / scale, 0, -1000, 1000), VertexSorting.ORTHOGRAPHIC_Z);
            var minecraft = Minecraft.getInstance();
            var graphics = new GuiGraphics(minecraft, minecraft.renderBuffers().bufferSource());
            if (singleLine) {
                contentWidth = VillagerStyledTextRenderer.renderSingleLine(graphics, minecraft.font, text,
                        0xE3E7EC, (int) (ImGui.getTime() * 20));
            } else {
                contentHeight = VillagerStyledTextRenderer.renderWrapped(graphics, minecraft.font, text,
                        Math.max(1, (int) (width / scale) - 9), (int) (height / scale), (int) scroll, 0xE3E7EC, (int) (ImGui.getTime() * 20));
            }
            graphics.flush();
            renderedTick = tick;
            renderedWidth = width;
            renderedHeight = height;
            renderedScale = scale;
            renderedScroll = scroll;
            renderedBackground = background;
            renderedSingleLine = singleLine;
        } finally {
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
            RenderSystem.setShader(() -> shader);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
            RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            if (scissor) RenderSystem.enableScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]);
            if (depth) RenderSystem.enableDepthTest();
            else RenderSystem.disableDepthTest();
        }
    }

    // the current ImGui draw list still references this texture until the next frame
    void retire() { RETIRED.add(this); }

    static void disposeRetired() {
        RETIRED.forEach(EditorTextPreview::close);
        RETIRED.clear();
    }

    void close() {
        if (target != null) {
            target.destroyBuffers();
            target = null;
        }
    }
}
