package com.jvn.villagerretaliation.client.ui;

import com.jvn.toucanlib.client.ToucanColors;
import com.jvn.toucanlib.client.ToucanGuiText;
import com.jvn.toucanlib.client.ToucanScreenRects;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

public final class VillagerClientUiUtil {
    private static final float SCREEN_LAYER_Z = 320.0F;
    private static final float HUD_LAYER_Z = 360.0F;
    private static final float CHAT_LAYER_Z = 420.0F;

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

    public static void pushGuiLayer(GuiGraphics graphics, float z) {
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, z);
    }

    public static void popGuiLayer(GuiGraphics graphics) {
        graphics.pose().popPose();
    }

    public static int withAlphaFloor(int color, float alphaFactor) {
        int alpha = color >>> 24;
        int adjustedAlpha = Mth.clamp(Mth.floor(alpha * alphaFactor), 0, 255);
        return adjustedAlpha << 24 | color & 0x00FFFFFF;
    }

    public static int withAlphaRound(int color, float alphaFactor) {
        return ToucanColors.multiplyAlpha(color, alphaFactor);
    }

    public static boolean containsInclusive(double x, double y, int left, int top, int right, int bottom) {
        return x >= left && x <= right && y >= top && y <= bottom;
    }

    public static boolean containsExclusive(double x, double y, int left, int top, int right, int bottom) {
        return ToucanScreenRects.contains(x, y, left, top, right - left, bottom - top);
    }

    public static int scaledTextWidth(Font font, String text, float scale) {
        return Math.round(font.width(text) * scale);
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
        if (text == null || text.isBlank()) {
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, z);
        graphics.drawString(font, text, x, y, color, shadow);
        graphics.pose().popPose();
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
        if (text == null || text.isBlank()) {
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, z);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, 0, 0, color, shadow);
        graphics.pose().popPose();
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
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, z);
        graphics.drawString(font, text, x, y, color, shadow);
        graphics.pose().popPose();
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
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, z);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, 0, 0, color, shadow);
        graphics.pose().popPose();
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
        if (text == null || text.isBlank() || width <= 0 || lineStep <= 0) {
            return;
        }
        graphics.enableScissor(x, y, x + width, y + lineStep);
        drawStringAtZ(graphics, font, text, x, y, color, shadow, z);
        graphics.disableScissor();
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
        if (text == null || text.isBlank() || width <= 0 || lineStep <= 0) {
            return;
        }
        graphics.enableScissor(x, y, x + width, y + lineStep);
        drawScaledStringAtZ(graphics, font, text, x, y, color, shadow, scale, z);
        graphics.disableScissor();
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
        if (text == null || text.isBlank() || width <= 0 || maxLines <= 0 || lineStep <= 0) {
            return y;
        }

        List<FormattedCharSequence> lines = font.split(Component.literal(text), width);
        int visibleLines = Math.min(maxLines, lines.size());
        for (int index = 0; index < visibleLines; index++) {
            drawStringAtZ(graphics, font, lines.get(index), x, y + index * lineStep, color, shadow, z);
        }
        return y + visibleLines * lineStep;
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
        if (text == null || text.isBlank() || width <= 0 || maxLines <= 0 || lineStep <= 0) {
            return y;
        }

        List<FormattedCharSequence> lines = font.split(Component.literal(text), scaledWrapWidth(width, scale));
        int visibleLines = Math.min(maxLines, lines.size());
        for (int index = 0; index < visibleLines; index++) {
            drawScaledStringAtZ(graphics, font, lines.get(index), x, y + index * lineStep, color, shadow, scale, z);
        }
        return y + visibleLines * lineStep;
    }
}
