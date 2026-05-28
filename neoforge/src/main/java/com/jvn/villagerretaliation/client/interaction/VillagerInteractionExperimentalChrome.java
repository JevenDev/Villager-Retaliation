package com.jvn.villagerretaliation.client.interaction;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

final class VillagerInteractionExperimentalChrome {
    private static final float NAME_TEXT_SCALE = 1.85F;
    private static final float NAME_DETAIL_TEXT_SCALE = 1.4F;
    private static final int NAME_LINE_GAP = 5;
    private static final float OVERLAY_SOURCE_WIDTH = 1920.0F;
    private static final float OVERLAY_SOURCE_HEIGHT = 1080.0F;
    private static final OverlayLayer[] BACKDROP_LAYERS = {
            new OverlayLayer("lower-veil", new OverlayShape[] {
                    OverlayShape.quad(
                            0x63000000,
                            0.0F, 816.0F,
                            1920.0F, 641.0F,
                            1920.0F, 1080.0F,
                            0.0F, 1080.0F)
            }),
            new OverlayLayer("lower-shadow", new OverlayShape[] {
                    OverlayShape.quad(
                            0xFF000000,
                            0.0F, 960.0F,
                            1920.0F, 640.0F,
                            1920.0F, 1080.0F,
                            0.0F, 1080.0F)
            }),
            new OverlayLayer("right-shadow", new OverlayShape[] {
                    OverlayShape.quad(
                            0xFF101010,
                            1920.0F, 448.0F,
                            1920.0F, 1080.0F,
                            1062.0F, 1080.0F,
                            1920.0F, 448.0F)
            }),
            new OverlayLayer("right-highlight", new OverlayShape[] {
                    OverlayShape.quad(
                            0xFF323232,
                            1920.0F, 683.0F,
                            1920.0F, 1080.0F,
                            1658.0F, 1080.0F,
                            1920.0F, 683.0F)
            })
    };

    private VillagerInteractionExperimentalChrome() {
    }

    static void renderBackdrop(GuiGraphics graphics, int width, int height, float veilTop) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        for (OverlayLayer layer : BACKDROP_LAYERS) {
            layer.render(graphics, width, height, 1.0F, 0.0F, 0.0F, 1.0F);
        }
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
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

        drawRightAlignedScaled(graphics, font, context.villagerName(), right, nameY, context.moodColor(), nameScale);
        drawRightAlignedScaled(graphics, font, context.professionName(), right, professionY, context.infoSecondaryColor(), detailScale);
        drawRightAlignedScaled(graphics, font, context.reputationText(), right, reputationY, context.infoLabelColor(), detailScale);
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

    private static void drawRightAlignedScaled(GuiGraphics graphics, Font font, String text, int right, int y, int color, float scale) {
        float scaledWidth = font.width(text) * scale;
        graphics.pose().pushPose();
        graphics.pose().translate(right - scaledWidth, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    private record OverlayLayer(String name, OverlayShape[] shapes) {
        private void render(GuiGraphics graphics, int width, int height, float alpha, float offsetX, float offsetY, float scale) {
            if (alpha <= 0.0F) {
                return;
            }

            for (OverlayShape shape : this.shapes) {
                shape.render(graphics, width, height, alpha, offsetX, offsetY, scale);
            }
        }
    }

    private record OverlayShape(int color, float[] vertices) {
        private static OverlayShape quad(
                int color,
                float x1,
                float y1,
                float x2,
                float y2,
                float x3,
                float y3,
                float x4,
                float y4) {
            return new OverlayShape(color, new float[] {x1, y1, x2, y2, x3, y3, x4, y4});
        }

        private void render(
                GuiGraphics graphics,
                int width,
                int height,
                float alpha,
                float offsetX,
                float offsetY,
                float scale) {
            float[] transformedVertices = transformedVertices(width, height, offsetX, offsetY, scale);
            int vertexCount = transformedVertices.length / 2;
            if (vertexCount < 3) {
                return;
            }

            float minY = Float.POSITIVE_INFINITY;
            float maxY = Float.NEGATIVE_INFINITY;
            for (int index = 1; index < transformedVertices.length; index += 2) {
                minY = Math.min(minY, transformedVertices[index]);
                maxY = Math.max(maxY, transformedVertices[index]);
            }

            int top = Math.max(0, (int) Math.floor(minY));
            int bottom = Math.min(height, (int) Math.ceil(maxY));
            if (bottom <= top) {
                return;
            }

            int tintedColor = multiplyAlpha(this.color, alpha);
            float[] intersections = new float[vertexCount];
            for (int y = top; y < bottom; y++) {
                float scanY = y + 0.5F;
                int intersectionCount = collectIntersections(transformedVertices, scanY, intersections);
                if (intersectionCount < 2) {
                    continue;
                }

                Arrays.sort(intersections, 0, intersectionCount);
                for (int index = 0; index + 1 < intersectionCount; index += 2) {
                    int left = Math.max(0, (int) Math.floor(intersections[index]));
                    int right = Math.min(width, (int) Math.ceil(intersections[index + 1]));
                    if (right > left) {
                        graphics.fill(left, y, right, y + 1, tintedColor);
                    }
                }
            }
        }

        private float[] transformedVertices(int width, int height, float offsetX, float offsetY, float scale) {
            float[] transformed = new float[this.vertices.length];
            for (int index = 0; index < this.vertices.length; index += 2) {
                transformed[index] = transformX(this.vertices[index], width, offsetX, scale);
                transformed[index + 1] = transformY(this.vertices[index + 1], height, offsetY, scale);
            }
            return transformed;
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
    }

    private static float transformX(float sourceX, int width, float offsetX, float scale) {
        float screenX = sourceX * width / OVERLAY_SOURCE_WIDTH;
        return (screenX - width * 0.5F) * scale + width * 0.5F + offsetX;
    }

    private static float transformY(float sourceY, int height, float offsetY, float scale) {
        float screenY = sourceY * height / OVERLAY_SOURCE_HEIGHT;
        return (screenY - height * 0.5F) * scale + height * 0.5F + offsetY;
    }

    private static int multiplyAlpha(int color, float alphaMultiplier) {
        int alpha = Math.round(((color >>> 24) & 0xFF) * alphaMultiplier);
        alpha = Math.max(0, Math.min(255, alpha));
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    interface Context {
        Font font();

        int infoRight();

        int infoBottom();

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
