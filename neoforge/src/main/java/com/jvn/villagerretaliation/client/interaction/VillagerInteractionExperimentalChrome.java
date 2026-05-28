package com.jvn.villagerretaliation.client.interaction;

import com.jvn.toucanlib.client.ToucanColors;
import com.jvn.toucanlib.client.ToucanEasing;
import com.jvn.toucanlib.client.ToucanGuiText;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

final class VillagerInteractionExperimentalChrome {
    private static final float NAME_TEXT_SCALE = 1.85F;
    private static final float NAME_DETAIL_TEXT_SCALE = 1.4F;
    private static final int NAME_LINE_GAP = 5;
    private static final float OVERLAY_SOURCE_WIDTH = 1920.0F;
    private static final float OVERLAY_SOURCE_HEIGHT = 1080.0F;
    private static final float EXIT_DURATION_MILLIS = 860.0F;
    private static final float TEXT_FADE_IN_DURATION_MILLIS = 320.0F;
    private static final float TEXT_ALPHA_DRAW_THRESHOLD = 0.04F;
    private static final OverlayLayer[] BACKDROP_LAYERS = {
            new OverlayLayer("lower-veil", 0, 260, -0.04F, 0.16F, 1.025F, 80, -0.10F, 1.02F, 0x63000000, new float[] {
                    -560.0F, 867.0F,
                    2480.0F, 590.0F,
                    2480.0F, 1360.0F,
                    -560.0F, 1360.0F
            }),
            new OverlayLayer("lower-shadow", 90, 240, -0.06F, 0.30F, 1.04F, 0, -0.16F, 1.12F, 0xFF000000, new float[] {
                    -560.0F, 1053.0F,
                    2480.0F, 547.0F,
                    2480.0F, 1360.0F,
                    -560.0F, 1360.0F
            }),
            new OverlayLayer("right-shadow", 210, 250, 0.34F, -0.05F, 1.03F, 170, 0.18F, 1.18F, 0xFF101010, new float[] {
                    2280.0F, 183.0F,
                    2280.0F, 1360.0F,
                    682.0F, 1360.0F
            }),
            new OverlayLayer("right-highlight", 340, 180, 0.22F, 0.02F, 0.98F, 260, 0.30F, 1.28F, 0xFF323232, new float[] {
                    2280.0F, 138.0F,
                    2280.0F, 1360.0F,
                    1473.0F, 1360.0F
            })
    };
    private static final long BACKDROP_IDLE_AFTER_MILLIS = 900L;

    private static long backdropAnimationStartMillis = -1L;
    private static long backdropExitStartMillis = -1L;
    private static List<ExitTextElement> exitTextElements = List.of();
    private static List<ExitFadeTextElement> exitFadeTextElements = List.of();
    private static List<ExitFadeRectElement> exitFadeRectElements = List.of();

    private VillagerInteractionExperimentalChrome() {
    }

    static void resetAnimation() {
        backdropAnimationStartMillis = Util.getMillis();
        backdropExitStartMillis = -1L;
        exitTextElements = List.of();
        exitFadeTextElements = List.of();
        exitFadeRectElements = List.of();
    }

    static void startExitAnimation(
            List<ExitTextElement> textElements,
            List<ExitFadeTextElement> fadeTextElements,
            List<ExitFadeRectElement> fadeRectElements) {
        if (backdropExitStartMillis < 0L) {
            backdropExitStartMillis = Util.getMillis();
            exitTextElements = new ArrayList<>(textElements);
            exitFadeTextElements = new ArrayList<>(fadeTextElements);
            exitFadeRectElements = new ArrayList<>(fadeRectElements);
        }
    }

    static boolean exitAnimationRunning() {
        return backdropExitStartMillis >= 0L && !exitAnimationComplete();
    }

    static boolean exitAnimationComplete() {
        return backdropExitStartMillis >= 0L
                && Util.getMillis() - backdropExitStartMillis >= EXIT_DURATION_MILLIS;
    }

    static void renderBackdrop(GuiGraphics graphics, int width, int height, float veilTop, int mouseX, int mouseY) {
        long now = Util.getMillis();
        if (backdropAnimationStartMillis < 0L) {
            backdropAnimationStartMillis = now;
        }
        float elapsedMillis = now - backdropAnimationStartMillis;
        float exitElapsedMillis = backdropExitStartMillis < 0L ? -1.0F : now - backdropExitStartMillis;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        if (!VillagerInteractionScreenShaderRenderer.renderExperimentalChrome(graphics, width, height, elapsedMillis, exitElapsedMillis, mouseX, mouseY)) {
            renderBackdropFallback(graphics, width, height, elapsedMillis, exitElapsedMillis, mouseX, mouseY);
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        if (exitElapsedMillis >= 0.0F) {
            renderExitText(graphics, exitElapsedMillis);
            renderExitFadeChrome(graphics, exitElapsedMillis);
        }
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }

    private static void renderBackdropFallback(
            GuiGraphics graphics,
            int width,
            int height,
            float elapsedMillis,
            float exitElapsedMillis,
            int mouseX,
            int mouseY) {
        for (OverlayLayer layer : BACKDROP_LAYERS) {
            LayerState state = layer.state(width, height, elapsedMillis, exitElapsedMillis, mouseX, mouseY);
            if (state.alpha() <= 0.0F) {
                continue;
            }

            int color = ToucanColors.multiplyAlpha(layer.color(), state.alpha());
            float[] vertices = transformedVertices(layer.vertices(), width, height, state.offsetX(), state.offsetY(), state.scale());
            if (vertices.length == 8) {
                fillTriangle(graphics, width, height, vertices[0], vertices[1], vertices[2], vertices[3], vertices[4], vertices[5], color);
                fillTriangle(graphics, width, height, vertices[0], vertices[1], vertices[4], vertices[5], vertices[6], vertices[7], color);
            } else if (vertices.length == 6) {
                fillTriangle(graphics, width, height, vertices[0], vertices[1], vertices[2], vertices[3], vertices[4], vertices[5], color);
            }
        }
    }

    private static void fillTriangle(
            GuiGraphics graphics,
            int width,
            int height,
            float x1,
            float y1,
            float x2,
            float y2,
            float x3,
            float y3,
            int color) {
        float[] vertices = {x1, y1, x2, y2, x3, y3};
        float minY = Math.min(y1, Math.min(y2, y3));
        float maxY = Math.max(y1, Math.max(y2, y3));
        int top = Math.max(0, (int) Math.floor(minY));
        int bottom = Math.min(height, (int) Math.ceil(maxY));
        float[] intersections = new float[3];
        for (int y = top; y < bottom; y++) {
            int intersectionCount = collectIntersections(vertices, y + 0.5F, intersections);
            if (intersectionCount < 2) {
                continue;
            }

            Arrays.sort(intersections, 0, intersectionCount);
            int left = Math.max(0, (int) Math.floor(intersections[0]));
            int right = Math.min(width, (int) Math.ceil(intersections[intersectionCount - 1]));
            if (right > left) {
                graphics.fill(left, y, right, y + 1, color);
            }
        }
    }

    private static int collectIntersections(float[] vertices, float scanY, float[] intersections) {
        int count = 0;
        int vertexCount = vertices.length / 2;
        for (int index = 0; index < vertexCount; index++) {
            int nextIndex = (index + 1) % vertexCount;
            float x1 = vertices[index * 2];
            float y1 = vertices[index * 2 + 1];
            float x2 = vertices[nextIndex * 2];
            float y2 = vertices[nextIndex * 2 + 1];
            if (y1 == y2) {
                continue;
            }

            float edgeTop = Math.min(y1, y2);
            float edgeBottom = Math.max(y1, y2);
            if (scanY < edgeTop || scanY >= edgeBottom) {
                continue;
            }

            intersections[count++] = x1 + (scanY - y1) * (x2 - x1) / (y2 - y1);
        }
        return count;
    }

    static void renderFocus(Context context, GuiGraphics graphics, int mouseX, int mouseY) {
        Font font = context.font();
        int right = context.infoRight();
        float nameScale = NAME_TEXT_SCALE * context.experimentalTextScale();
        float detailScale = NAME_DETAIL_TEXT_SCALE * context.experimentalTextScale();
        int lineGap = context.experimentalUnit(NAME_LINE_GAP);
        int reputationY = context.infoBottom() - Math.round(font.lineHeight * detailScale);
        int professionY = reputationY - lineGap - Math.round(font.lineHeight * detailScale);
        int nameY = professionY - lineGap - Math.round(font.lineHeight * nameScale);

        drawAnimatedRightAlignedScaled(
                graphics,
                font,
                context.villagerName(),
                right,
                nameY,
                context.moodColor(),
                nameScale,
                textFadeInAlpha());
        drawAnimatedRightAlignedScaled(
                graphics,
                font,
                context.professionName(),
                right,
                professionY,
                context.infoSecondaryColor(),
                detailScale,
                textFadeInAlpha());
        drawAnimatedRightAlignedScaled(
                graphics,
                font,
                context.reputationText(),
                right,
                reputationY,
                context.infoLabelColor(),
                detailScale,
                textFadeInAlpha());
    }

    static void renderNameTooltip(Context context, GuiGraphics graphics, int mouseX, int mouseY) {
        if (!isPointInsideName(context, mouseX, mouseY)) {
            return;
        }

        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.literal(context.villagerName()).withStyle(ChatFormatting.WHITE));
        tooltip.add(Component.literal(context.moodText()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal(context.genderText()).withStyle(ChatFormatting.GRAY));
        graphics.renderComponentTooltip(context.font(), tooltip, mouseX, mouseY);
    }

    private static boolean isPointInsideName(Context context, double mouseX, double mouseY) {
        Font font = context.font();
        float nameScale = NAME_TEXT_SCALE * context.experimentalTextScale();
        float detailScale = NAME_DETAIL_TEXT_SCALE * context.experimentalTextScale();
        int lineGap = context.experimentalUnit(NAME_LINE_GAP);
        int nameWidth = Math.round(font.width(context.villagerName()) * nameScale);
        int nameLeft = context.infoRight() - nameWidth;
        int reputationY = context.infoBottom() - Math.round(font.lineHeight * detailScale);
        int professionY = reputationY - lineGap - Math.round(font.lineHeight * detailScale);
        int nameTop = professionY - lineGap - Math.round(font.lineHeight * nameScale);
        int nameHeight = Math.round(font.lineHeight * nameScale);
        return mouseX >= nameLeft - 4
                && mouseX <= nameLeft + nameWidth + 4
                && mouseY >= nameTop - 3
                && mouseY <= nameTop + nameHeight + 3;
    }

    private static void drawAnimatedRightAlignedScaled(
            GuiGraphics graphics,
            Font font,
            String text,
            int right,
            int y,
            int color,
            float scale,
            float alpha) {
        if (!shouldDrawText(alpha)) {
            return;
        }

        ToucanGuiText.drawRightAlignedScaledString(graphics, font, text, right, y, ToucanColors.multiplyAlpha(color, alpha), scale);
    }

    private static void renderExitText(GuiGraphics graphics, float exitElapsedMillis) {
        if (exitTextElements.isEmpty()) {
            return;
        }

        Font font = Minecraft.getInstance().font;
        float progress = normalizedProgress(exitElapsedMillis, 0.0F, 540.0F);
        if (progress <= 0.0F) {
            return;
        }

        float fall = easeInCubic(progress);
        float fade = 1.0F - smoothstep(normalizedProgress(exitElapsedMillis, 650.0F, 170.0F));
        if (!shouldDrawText(fade)) {
            return;
        }

        for (ExitTextElement element : exitTextElements) {
            int color = ToucanColors.multiplyAlpha(element.color(), fade);
            float x = element.x();
            if (element.rightAligned()) {
                x -= font.width(element.text()) * element.scale();
            }
            float y = element.y() + element.fallDistance() * fall;
            float scale = element.scale() * (1.0F + progress * 0.035F);
            graphics.pose().pushPose();
            graphics.pose().translate(x, y, 0.0F);
            graphics.pose().scale(scale, scale, 1.0F);
            graphics.drawString(font, element.text(), 0, 0, color, false);
            graphics.pose().popPose();
        }
    }

    private static void renderExitFadeChrome(GuiGraphics graphics, float exitElapsedMillis) {
        float alpha = 1.0F - smoothstep(normalizedProgress(exitElapsedMillis, 0.0F, 260.0F));
        if (alpha <= 0.0F) {
            return;
        }

        Font font = Minecraft.getInstance().font;
        for (ExitFadeRectElement element : exitFadeRectElements) {
            graphics.fill(
                    element.left(),
                    element.top(),
                    element.right(),
                    element.bottom(),
                    ToucanColors.multiplyAlpha(element.color(), alpha * element.alpha()));
        }
        for (ExitFadeTextElement element : exitFadeTextElements) {
            if (!shouldDrawText(alpha)) {
                continue;
            }
            graphics.pose().pushPose();
            graphics.pose().translate(element.x(), element.y(), 0.0F);
            graphics.pose().scale(element.scale(), element.scale(), 1.0F);
            graphics.drawString(font, element.text(), 0, 0, ToucanColors.multiplyAlpha(element.color(), alpha), false);
            graphics.pose().popPose();
        }
    }

    private static float normalizedProgress(float elapsedMillis, float delayMillis, float durationMillis) {
        return Mth.clamp((elapsedMillis - delayMillis) / durationMillis, 0.0F, 1.0F);
    }

    static float textEntranceProgress(float delayMillis, float durationMillis) {
        long now = Util.getMillis();
        if (backdropAnimationStartMillis < 0L) {
            backdropAnimationStartMillis = now;
        }
        return normalizedProgress(now - backdropAnimationStartMillis, delayMillis, durationMillis);
    }

    static float textFadeInAlpha() {
        return ToucanEasing.smoothstep(textEntranceProgress(0.0F, TEXT_FADE_IN_DURATION_MILLIS));
    }

    static float backdropElapsedMillis() {
        long now = Util.getMillis();
        if (backdropAnimationStartMillis < 0L) {
            backdropAnimationStartMillis = now;
        }
        return now - backdropAnimationStartMillis;
    }

    static float backdropExitElapsedMillis() {
        return backdropExitStartMillis < 0L ? -1.0F : Util.getMillis() - backdropExitStartMillis;
    }

    static float chromeAlpha() {
        return textFadeInAlpha();
    }

    static boolean shouldDrawText(float alpha) {
        return alpha > TEXT_ALPHA_DRAW_THRESHOLD;
    }

    record ExitTextElement(
            String text,
            int x,
            int y,
            int color,
            float scale,
            float delayMillis,
            float driftX,
            float fallDistance,
            boolean rightAligned) {
    }

    record ExitFadeTextElement(String text, int x, int y, int color, float scale) {
    }

    record ExitFadeRectElement(int left, int top, int right, int bottom, int color, float alpha) {
    }

    private record OverlayLayer(
            String name,
            float delayMillis,
            float durationMillis,
            float startXRatio,
            float startYRatio,
            float startScale,
            float exitDelayMillis,
            float exitXRatio,
            float exitFallRatio,
            int color,
            float[] vertices) {
        private LayerState state(
                int width,
                int height,
                float elapsedMillis,
                float exitElapsedMillis,
                int mouseX,
                int mouseY) {
            float progress = normalizedProgress(elapsedMillis, this.delayMillis, this.durationMillis);
            if (progress <= 0.0F) {
                return LayerState.EMPTY;
            }

            float easedProgress = ToucanEasing.easeOutBack(progress, 1.45F);
            float settle = ToucanEasing.easeOutCubic(progress);
            float alpha = Mth.clamp(progress * 1.35F, 0.0F, 1.0F);
            float offsetX = this.startXRatio * width * (1.0F - easedProgress);
            float offsetY = this.startYRatio * height * (1.0F - easedProgress);
            float scale = 1.0F + (this.startScale - 1.0F) * (1.0F - settle);
            float idlePulse = idlePulse(elapsedMillis);
            if (idlePulse > 0.0F) {
                offsetX += this.startXRatio * width * 0.012F * idlePulse;
                offsetY += this.startYRatio * height * 0.012F * idlePulse;
            }
            float mouseSettle = ToucanEasing.easeOutCubic(normalizedProgress(elapsedMillis, this.delayMillis + this.durationMillis, 280.0F));
            float mouseXRatio = width <= 0 ? 0.0F : Mth.clamp(mouseX / (float) width, 0.0F, 1.0F) * 2.0F - 1.0F;
            float mouseYRatio = height <= 0 ? 0.0F : Mth.clamp(mouseY / (float) height, 0.0F, 1.0F) * 2.0F - 1.0F;
            float layerDepth = Math.max(0.45F, Math.abs(this.startXRatio) + Math.abs(this.startYRatio));
            offsetX += mouseXRatio * layerDepth * 5.0F * mouseSettle;
            offsetY += mouseYRatio * layerDepth * 3.0F * mouseSettle;

            if (exitElapsedMillis >= 0.0F) {
                float exitProgress = normalizedProgress(exitElapsedMillis, this.exitDelayMillis, 290.0F);
                float fall = easeInBack(exitProgress);
                float fade = 1.0F - ToucanEasing.smoothstep(normalizedProgress(exitElapsedMillis, this.exitDelayMillis + 120.0F, 180.0F));
                alpha *= fade;
                offsetX += this.exitXRatio * width * fall;
                offsetY += this.exitFallRatio * height * fall;
                scale += 0.045F * fall;
            }

            return new LayerState(offsetX, offsetY, scale, Mth.clamp(alpha, 0.0F, 1.0F));
        }
    }

    private record LayerState(float offsetX, float offsetY, float scale, float alpha) {
        private static final LayerState EMPTY = new LayerState(0.0F, 0.0F, 1.0F, 0.0F);
    }

    private static float easeInCubic(float progress) {
        return progress * progress * progress;
    }

    private static float easeInBack(float progress) {
        float overshoot = 1.25F;
        return progress * progress * ((overshoot + 1.0F) * progress - overshoot);
    }

    static float smoothstep(float progress) {
        return ToucanEasing.smoothstep(progress);
    }

    private static float idlePulse(float elapsedMillis) {
        if (elapsedMillis < BACKDROP_IDLE_AFTER_MILLIS) {
            return 0.0F;
        }
        return Mth.sin((elapsedMillis - BACKDROP_IDLE_AFTER_MILLIS) * 0.003F) * 0.5F + 0.5F;
    }

    private static float[] transformedVertices(float[] vertices, int width, int height, float offsetX, float offsetY, float scale) {
        float[] transformed = new float[vertices.length];
        for (int index = 0; index < vertices.length; index += 2) {
            transformed[index] = transformX(vertices[index], width, offsetX, scale);
            transformed[index + 1] = transformY(vertices[index + 1], height, offsetY, scale);
        }
        return transformed;
    }

    private static float transformX(float sourceX, int width, float offsetX, float scale) {
        float screenX = sourceX * width / OVERLAY_SOURCE_WIDTH;
        return (screenX - width * 0.5F) * scale + width * 0.5F + offsetX;
    }

    private static float transformY(float sourceY, int height, float offsetY, float scale) {
        float screenY = sourceY * height / OVERLAY_SOURCE_HEIGHT;
        return (screenY - height * 0.5F) * scale + height * 0.5F + offsetY;
    }

    interface Context {
        Font font();

        int infoRight();

        int infoBottom();

        int screenWidth();

        int screenHeight();

        String villagerName();

        String professionName();

        String genderText();

        String moodText();

        String reputationText();

        int moodColor();

        int infoSecondaryColor();

        int infoLabelColor();

        float experimentalTextScale();

        int experimentalUnit(int value);
    }

}
