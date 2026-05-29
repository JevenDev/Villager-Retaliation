package com.jvn.villagerretaliation.client.interaction;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

final class VillagerInteractionOptionList {
    private static final float EXPERIMENTAL_OPTION_BASE_SCALE = 1.48F;
    private static final int WRAPPED_OPTION_MAX_LINES = 2;

    private VillagerInteractionOptionList() {
    }

    static void render(Context context, GuiGraphics graphics, int mouseX, int mouseY) {
        if (context.experimentalStyle()) {
            renderExperimental(context, graphics, mouseX, mouseY);
            return;
        }

        int left = context.optionsLeft();
        int top = context.optionsTop();
        int viewportHeight = context.optionViewportHeight();
        int viewportBottom = top + viewportHeight;
        int hovered = optionAt(context, mouseX, mouseY);

        graphics.enableScissor(left - 24, top - 3, left + context.optionWidth() + 10, viewportBottom + 3);
        for (int index = 0; index < context.optionCount(); index++) {
            int rowHeight = optionHeight(context, index);
            float y = top + optionOffset(context, index) - context.optionScroll();
            if (y + rowHeight < top - 10 || y > viewportBottom + 10) {
                continue;
            }
            renderOption(context, graphics, index, hovered, mouseX, mouseY, left, y, rowHeight, top, viewportBottom);
        }
        graphics.disableScissor();
        context.renderScrollbar(graphics);
    }

    private static void renderExperimental(Context context, GuiGraphics graphics, int mouseX, int mouseY) {
        int left = context.optionsLeft();
        int top = context.optionsTop();
        int viewportHeight = context.optionViewportHeight();
        int viewportBottom = top + viewportHeight;
        int hovered = optionAt(context, mouseX, mouseY);

        graphics.enableScissor(Math.max(0, left - context.optionWidth()), top, context.optionsScrollbarLeft() - 4, viewportBottom);
        for (int index = 0; index < context.optionCount(); index++) {
            int rowHeight = optionHeight(context, index);
            float y = top + optionOffset(context, index) - context.optionScroll();
            if (y + rowHeight < top || y > viewportBottom) {
                continue;
            }
            renderExperimentalOption(context, graphics, index, hovered, mouseX, mouseY, left, y, rowHeight, top, viewportBottom);
        }
        graphics.disableScissor();
        context.renderScrollbar(graphics);
    }

    static int optionAt(Context context, double mouseX, double mouseY) {
        int left = context.optionsLeft();
        int top = context.optionsTop();
        int bottom = top + context.optionViewportHeight();
        if (mouseX < left - 18 || mouseX > left + context.optionWidth()) {
            return -1;
        }
        if (mouseY < top - 2 || mouseY > bottom + 2) {
            return -1;
        }
        for (int index = 0; index < context.optionCount(); index++) {
            float y = top + optionOffset(context, index) - context.optionScroll();
            if (mouseY >= y - 2.0F && mouseY <= y + optionHeight(context, index) + 2.0F) {
                return index;
            }
        }
        return -1;
    }

    static float optionOffset(Context context, int optionIndex) {
        float offset = 0.0F;
        for (int index = 0; index < optionIndex; index++) {
            offset += optionHeight(context, index);
        }
        return offset;
    }

    static int optionHeight(Context context, int optionIndex) {
        return context.optionHeight() + wrappedExtraHeight(context, optionIndex);
    }

    static float optionContentHeight(Context context) {
        float height = 0.0F;
        for (int index = 0; index < context.optionCount(); index++) {
            height += optionHeight(context, index);
        }
        return height;
    }

    static List<String> wrappedOptionLabelLines(Context context, String label, float scale) {
        int availableWidth = Math.max(1, context.optionWidth() - context.optionTextInset() * 2);
        int wrapWidth = Math.max(1, Mth.floor(availableWidth / Math.max(scale, 0.001F)));
        return wrapPlainText(context.font(), label, wrapWidth, context.experimentalStyle() ? WRAPPED_OPTION_MAX_LINES : 1);
    }

    private static void renderOption(
            Context context,
            GuiGraphics graphics,
            int index,
            int hovered,
            int mouseX,
            int mouseY,
            int left,
            float y,
            int rowHeight,
            int viewportTop,
            int viewportBottom
    ) {
        boolean selected = index == context.selectedOption();
        boolean isHovered = hovered == index;
        float hoverMix = isHovered ? context.hoverIntensity(mouseX, mouseY, left, y) : 0.0F;
        float scale = optionScale(context, selected, hoverMix);
        float cursorShiftX = isHovered ? context.hoverShift(mouseX, left, context.optionWidth(), 3.2F) * hoverMix : 0.0F;
        float cursorShiftY = isHovered ? context.hoverShift(mouseY, y, rowHeight, 1.6F) * hoverMix : 0.0F;
        float edgeAlpha = context.edgeFadeAlpha(y, viewportTop, viewportBottom);
        int textColor = optionTextColor(selected, isHovered);

        graphics.pose().pushPose();
        applyOptionTransform(context, graphics, left, y, rowHeight, scale, cursorShiftX, cursorShiftY);
        renderOptionBackground(context, graphics, isHovered, left, y, rowHeight, edgeAlpha);
        if (selected) {
            graphics.drawString(context.font(), ">", left - 7, selectorY(context, y, rowHeight), VillagerInteractionUiUtil.withAlpha(0xFFFFFFFF, edgeAlpha), false);
        }
        renderWrappedLabel(context, graphics, context.optionLabel(index), left, y, rowHeight, 1.0F, textColor, 1.0F, viewportTop, viewportBottom, false);
        graphics.pose().popPose();
    }

    private static void renderExperimentalOption(
            Context context,
            GuiGraphics graphics,
            int index,
            int hovered,
            int mouseX,
            int mouseY,
            int left,
            float y,
            int rowHeight,
            int viewportTop,
            int viewportBottom
    ) {
        boolean selected = index == context.selectedOption();
        boolean isHovered = hovered == index;
        float hoverMix = isHovered ? context.hoverIntensity(mouseX, mouseY, left, y) : 0.0F;
        float textScale = context.experimentalTextScale();
        float scale = (EXPERIMENTAL_OPTION_BASE_SCALE
                + (selected ? context.optionSelectedScale() : 0.0F)
                + hoverMix * context.optionHoverScale()) * textScale;
        float cursorShiftX = isHovered ? context.hoverShift(mouseX, left, context.optionWidth(), 3.2F * textScale) * hoverMix : 0.0F;
        float cursorShiftY = isHovered ? context.hoverShift(mouseY, y, rowHeight, 1.6F * textScale) * hoverMix : 0.0F;
        float edgeAlpha = context.edgeFadeAlpha(y, viewportTop, viewportBottom);
        int textColor = optionTextColor(selected, isHovered);
        float textFadeInAlpha = VillagerInteractionExperimentalChrome.textFadeInAlpha();
        if (!VillagerInteractionExperimentalChrome.shouldDrawText(textFadeInAlpha)) {
            return;
        }
        float textAlpha = edgeAlpha * textFadeInAlpha;

        renderExperimentalOptionBackground(context, graphics, isHovered, left, y, rowHeight, textAlpha);
        graphics.pose().pushPose();
        applyExperimentalOptionTransform(context, graphics, left, y, scale, cursorShiftX, cursorShiftY);
        if (selected) {
            graphics.drawString(context.font(), ">", left - 7, selectorY(context, y, rowHeight), VillagerInteractionUiUtil.withAlpha(0xFFFFFFFF, textAlpha), false);
        }
        renderWrappedLabel(context, graphics, context.optionLabel(index), left, y, rowHeight, scale, textColor, textFadeInAlpha, viewportTop, viewportBottom, false);
        graphics.pose().popPose();
    }

    private static void applyOptionTransform(Context context, GuiGraphics graphics, int left, float top, int rowHeight, float scale, float shiftX, float shiftY) {
        float pivotX = left + context.optionWidth() * 0.5F;
        float pivotY = top + rowHeight * 0.5F;
        graphics.pose().translate(pivotX + shiftX, pivotY + shiftY, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.pose().translate(-pivotX, -pivotY, 0.0F);
    }

    private static void applyExperimentalOptionTransform(Context context, GuiGraphics graphics, int left, float top, float scale, float shiftX, float shiftY) {
        float pivotX = left + context.optionTextInset();
        float pivotY = top + optionTextYOffset(context.optionHeight());
        graphics.pose().translate(pivotX + shiftX, pivotY + shiftY, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.pose().translate(-pivotX, -pivotY, 0.0F);
    }

    private static float optionTextYOffset(int rowHeight) {
        return rowHeight * (5.0F / 18.0F);
    }

    private static int selectorY(Context context, float optionTop, int rowHeight) {
        return Mth.floor(optionTop + rowHeight * 0.5F - context.font().lineHeight * 0.5F);
    }

    private static void renderWrappedLabel(
            Context context,
            GuiGraphics graphics,
            String label,
            int left,
            float top,
            int rowHeight,
            float scale,
            int color,
            float alpha,
            int viewportTop,
            int viewportBottom,
            boolean shadow) {
        int textLeft = left + context.optionTextInset();
        List<String> lines = wrappedOptionLabelLines(context, label, scale);
        int baseHeight = context.optionHeight();
        float textTop = top + optionTextYOffset(baseHeight);
        float lineStep = context.font().lineHeight + 1.0F;
        boolean wrappedExperimental = context.experimentalStyle() && lines.size() > 1;
        if (wrappedExperimental) {
            lineStep = baseHeight / Math.max(scale, 0.001F);
        }
        for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
            float lineTop = textTop + lineIndex * lineStep;
            float lineScreenTop = top + optionTextYOffset(baseHeight) + lineIndex * baseHeight;
            float lineAlpha = alpha * context.edgeFadeAlpha(lineScreenTop, viewportTop, viewportBottom);
            if (VillagerInteractionExperimentalChrome.shouldDrawText(lineAlpha)) {
                int lineColor = VillagerInteractionUiUtil.withAlpha(color, lineAlpha);
                if (wrappedExperimental) {
                    graphics.pose().pushPose();
                    graphics.pose().translate(textLeft, lineTop, 0.0F);
                    graphics.drawString(context.font(), lines.get(lineIndex), 0, 0, lineColor, shadow);
                    graphics.pose().popPose();
                } else {
                    graphics.drawString(context.font(), lines.get(lineIndex), textLeft, Mth.floor(lineTop), lineColor, shadow);
                }
            }
        }
    }

    private static void renderExperimentalOptionBackground(Context context, GuiGraphics graphics, boolean hovered, int left, float top, int rowHeight, float edgeAlpha) {
        if (!hovered) {
            return;
        }

        int bgLeft = left - 12;
        int bgTop = Mth.floor(top + 1.0F);
        int bgRight = Math.min(context.optionsScrollbarLeft() - 8, left + context.optionWidth() - 8);
        int bgBottom = bgTop + rowHeight - 1;
        if (bgRight > bgLeft) {
            graphics.fill(bgLeft, bgTop, bgRight, bgBottom, VillagerInteractionUiUtil.withAlpha(0xFF000000, edgeAlpha * 0.16F));
        }
    }

    private static void renderOptionBackground(Context context, GuiGraphics graphics, boolean hovered, int left, float top, int rowHeight, float edgeAlpha) {
        if (!hovered) {
            return;
        }

        int bgLeft = left - 12;
        int bgTop = Mth.floor(top + 1.0F);
        int bgRight = left + context.optionWidth() - 8;
        int bgBottom = bgTop + rowHeight - 1;
        graphics.fill(bgLeft, bgTop, bgRight, bgBottom, VillagerInteractionUiUtil.withAlpha(0xFF000000, edgeAlpha * 0.16F));
    }

    private static int wrappedExtraHeight(Context context, int optionIndex) {
        if (!context.experimentalStyle()) {
            return 0;
        }
        float scale = EXPERIMENTAL_OPTION_BASE_SCALE * context.experimentalTextScale();
        return wrappedOptionLabelLines(context, context.optionLabel(optionIndex), scale).size() > 1
                ? context.optionHeight()
                : 0;
    }

    private static List<String> wrapPlainText(Font font, String text, int wrapWidth, int maxLines) {
        if (text == null || text.isBlank() || maxLines <= 0) {
            return List.of("");
        }

        List<String> lines = new java.util.ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (word.isBlank()) {
                continue;
            }
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (font.width(candidate) <= wrapWidth || line.isEmpty()) {
                line.setLength(0);
                line.append(candidate);
                continue;
            }

            lines.add(line.toString());
            if (lines.size() >= maxLines) {
                return lines;
            }
            line.setLength(0);
            line.append(word);
        }
        if (!line.isEmpty() && lines.size() < maxLines) {
            lines.add(line.toString());
        }
        return lines.isEmpty() ? List.of(text) : lines;
    }

    private static float optionScale(Context context, boolean selected, float hoverMix) {
        return 1.0F + (selected ? context.optionSelectedScale() : 0.0F) + hoverMix * context.optionHoverScale();
    }

    private static int optionTextColor(boolean selected, boolean hovered) {
        if (selected) {
            return 0xFFF8F8F4;
        }
        return hovered ? 0xFFE5E5DE : 0xCFC7C8C5;
    }

    interface Context {
        Font font();

        int optionsLeft();

        int optionsTop();

        int optionWidth();

        int optionHeight();

        int optionTextInset();

        int optionCount();

        String optionLabel(int index);

        int selectedOption();

        float optionScroll();

        int optionViewportHeight();

        int optionStride();

        float edgeFadeAlpha(float optionY, int viewportTop, int viewportBottom);

        float hoverIntensity(double mouseX, double mouseY, int left, float top);

        float hoverShift(double mouse, float start, float size, float strength);

        float optionHoverScale();

        float optionSelectedScale();

        boolean experimentalStyle();

        float experimentalTextScale();

        int optionsScrollbarLeft();

        void renderScrollbar(GuiGraphics graphics);
    }
}
