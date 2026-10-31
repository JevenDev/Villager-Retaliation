package com.jvn.villagerretaliation.client.ui;

import com.jvn.toucanlib.client.ToucanColors;
import com.jvn.toucanlib.client.ToucanEasing;
import com.jvn.toucanlib.client.ToucanGuiText;
import com.jvn.toucanlib.client.ToucanScreenRects;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;

public final class VillagerClientUiUtil {
    private static final float SCREEN_LAYER_Z = 320.0F;
    private static final float HUD_LAYER_Z = 360.0F;
    private static final float CHAT_LAYER_Z = 420.0F;
    private static final float TOOLTIP_LAYER_Z = 640.0F;

    private VillagerClientUiUtil() {
    }

    public static float screenLayerZ() {
        return SCREEN_LAYER_Z;
    }

    public static float hudLayerZ() {
        return HUD_LAYER_Z;
    }

    public static float chatLayerZ() {
        return CHAT_LAYER_Z;
    }

    public static float tooltipLayerZ() {
        return TOOLTIP_LAYER_Z;
    }

    public static void pushGuiLayer(GuiGraphics graphics, float z) {
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, z);
    }

    public static void popGuiLayer(GuiGraphics graphics) {
        graphics.pose().popPose();
    }

    public static int withAlphaFloor(int color, float alphaFactor) {
        return ToucanColors.multiplyAlphaFloor(color, alphaFactor);
    }

    public static int withAlphaRound(int color, float alphaFactor) {
        return ToucanColors.multiplyAlpha(color, alphaFactor);
    }

    public static float smoothstep(float value) {
        return ToucanEasing.smoothstep(value);
    }

    public static float easeOutCubic(float value) {
        return ToucanEasing.easeOutCubic(value);
    }

    public static boolean containsInclusive(double x, double y, int left, int top, int right, int bottom) {
        return ToucanScreenRects.containsInclusive(x, y, left, top, right - left, bottom - top);
    }

    public static boolean containsExclusive(double x, double y, int left, int top, int right, int bottom) {
        return ToucanScreenRects.contains(x, y, left, top, right - left, bottom - top);
    }

    public static int scaledTextWidth(Font font, String text, float scale) {
        return ToucanGuiText.scaledTextWidth(font, text, scale);
    }

    public static int scaledWrapWidth(int width, float scale) {
        return ToucanGuiText.scaledWrapWidth(width, scale);
    }

    public static int scaledLineStep(Font font, float scale) {
        return ToucanGuiText.scaledLineStep(font, scale);
    }

    public static void drawStringAtZ(
            GuiGraphics graphics,
            Font font,
            String text,
            int x,
            int y,
            int color,
            boolean shadow,
            float z) {
        ToucanGuiText.drawStringAtZ(graphics, font, text, x, y, color, shadow, z);
    }

    public static void drawScaledStringAtZ(
            GuiGraphics graphics,
            Font font,
            String text,
            int x,
            int y,
            int color,
            boolean shadow,
            float scale,
            float z) {
        ToucanGuiText.drawScaledStringAtZ(graphics, font, text, x, y, color, shadow, scale, z);
    }

    public static void drawStringAtZ(
            GuiGraphics graphics,
            Font font,
            FormattedCharSequence text,
            int x,
            int y,
            int color,
            boolean shadow,
            float z) {
        ToucanGuiText.drawStringAtZ(graphics, font, text, x, y, color, shadow, z);
    }

    public static void drawScaledStringAtZ(
            GuiGraphics graphics,
            Font font,
            FormattedCharSequence text,
            int x,
            int y,
            int color,
            boolean shadow,
            float scale,
            float z) {
        ToucanGuiText.drawScaledStringAtZ(graphics, font, text, x, y, color, shadow, scale, z);
    }

    public static void drawClippedStringAtZ(
            GuiGraphics graphics,
            Font font,
            String text,
            int x,
            int y,
            int width,
            int lineStep,
            int color,
            boolean shadow,
            float z) {
        ToucanGuiText.drawClippedStringAtZ(graphics, font, text, x, y, width, lineStep, color, shadow, z);
    }

    public static void drawClippedScaledStringAtZ(
            GuiGraphics graphics,
            Font font,
            String text,
            int x,
            int y,
            int width,
            int lineStep,
            int color,
            boolean shadow,
            float scale,
            float z) {
        ToucanGuiText.drawClippedScaledStringAtZ(graphics, font, text, x, y, width, lineStep, color, shadow, scale, z);
    }

    public static int drawWrappedLinesAtZ(
            GuiGraphics graphics,
            Font font,
            String text,
            int x,
            int y,
            int width,
            int maxLines,
            int lineStep,
            int color,
            boolean shadow,
            float z) {
        return ToucanGuiText.drawWrappedLinesAtZ(graphics, font, text, x, y, width, maxLines, lineStep, color, shadow, z);
    }

    public static int drawWrappedScaledLinesAtZ(
            GuiGraphics graphics,
            Font font,
            String text,
            int x,
            int y,
            int width,
            int maxLines,
            int lineStep,
            int color,
            boolean shadow,
            float scale,
            float z) {
        return ToucanGuiText.drawWrappedScaledLinesAtZ(graphics, font, text, x, y, width, maxLines, lineStep, color, shadow, scale, z);
    }
}
