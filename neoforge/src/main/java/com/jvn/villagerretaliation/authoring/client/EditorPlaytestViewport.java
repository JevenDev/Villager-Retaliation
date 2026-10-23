package com.jvn.villagerretaliation.authoring.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

public final class EditorPlaytestViewport {
    private static boolean enabled;
    private static boolean rendering;
    private static boolean invalidated;
    private static float fraction = 2F / 3;
    private static TextureTarget target;
    private static PlaytestLayout layout;
    private static Runnable releaseInput;

    private EditorPlaytestViewport() { }

    public static boolean active() {
        var mc = Minecraft.getInstance();
        return enabled && mc.level != null && mc.player != null;
    }

    public static void invalidate() { invalidated = true; }
    public static boolean rendering() { return rendering; }
    public static PlaytestLayout layout() { return layout; }
    public static RenderTarget target(RenderTarget original) { return rendering ? target : original; }

    static void open(float value, Runnable release) {
        enabled = true;
        com.jvn.villagerretaliation.authoring.preview.PlaytestLineSources.active(true);
        fraction = value;
        releaseInput = release;
    }

    static void fraction(float value) { fraction = value; }

    public static void releaseInput() {
        if (active() && releaseInput != null) releaseInput.run();
    }

    public static void resizeScreen() {
        var mc = Minecraft.getInstance();
        if (active() && layout != null && mc.screen != null
                && (mc.screen.width != layout.guiWidth() || mc.screen.height != layout.guiHeight()))
            mc.screen.resize(mc, layout.guiWidth(), layout.guiHeight());
    }

    public static void render(GameRenderer renderer, DeltaTracker delta, boolean renderLevel) {
        if (!active()) { renderer.render(delta, renderLevel); return; }
        var mc = Minecraft.getInstance();
        var window = mc.getWindow();
        var full = mc.getMainRenderTarget();
        var next = PlaytestLayout.calculate(window.getWidth(), window.getHeight(), window.getScreenWidth(),
                window.getScreenHeight(), fraction, mc.options.guiScale().get(), mc.isEnforceUnicode());
        boolean resized = invalidated || !next.equals(layout);
        invalidated = false;
        layout = next;
        if (target == null) target = new TextureTarget(layout.width(), layout.height(), true, Minecraft.ON_OSX);
        else if (target.width != layout.width() || target.height != layout.height())
            target.resize(layout.width(), layout.height(), Minecraft.ON_OSX);
        rendering = true;
        try {
            if (resized) {
                renderer.resize(layout.width(), layout.height());
                net.neoforged.neoforge.client.ClientHooks.resizeGuiLayers(mc, layout.guiWidth(), layout.guiHeight());
            }
            resizeScreen();
            target.clear(Minecraft.ON_OSX);
            target.bindWrite(true);
            renderer.render(delta, renderLevel);
        } finally {
            rendering = false;
            RenderSystem.disableScissor();
            full.bindWrite(true);
            full.setClearColor(0.08F, 0.08F, 0.09F, 1);
            full.clear(Minecraft.ON_OSX);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, target.frameBufferId);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, full.frameBufferId);
            GL30.glBlitFramebuffer(0, 0, target.width, target.height, 0, 0, target.width, target.height,
                    GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
            full.bindWrite(true);
        }
    }

    static void close() {
        if (!enabled && target == null) return;
        enabled = false;
        com.jvn.villagerretaliation.authoring.preview.PlaytestLineSources.active(false);
        releaseInput = null;
        layout = null;
        if (target != null) { target.destroyBuffers(); target = null; }
        var mc = Minecraft.getInstance();
        if (mc.gameRenderer != null) mc.resizeDisplay();
    }
}
