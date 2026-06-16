package com.jvn.villagerretaliation.client.interaction;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

final class VillagerInteractionOptionList {
    private static final float EXPERIMENTAL_OPTION_BASE_SCALE = 1.48F;
    private static final int WRAPPED_OPTION_MAX_LINES = 2;
    private static final int SELECTOR_LABEL_GAP = 17;
    private static final int PIXEL_OPTION_TEXTURE_WIDTH = 64;
    private static final int PIXEL_OPTION_TEXTURE_HEIGHT = 17;
    private static final int PIXEL_OPTION_SLICE_LEFT = 4;
    private static final int PIXEL_OPTION_SLICE_RIGHT = 4;
    private static final int PIXEL_OPTION_SLICE_TOP = 4;
    private static final int PIXEL_OPTION_SLICE_BOTTOM = 4;

    private VillagerInteractionOptionList() {
    }

    static void render(Context context, GuiGraphics graphics, int mouseX, int mouseY) {
        int left = context.optionsLeft();
        int top = context.optionsTop();
        int viewportHeight = context.optionViewportHeight();
        int viewportBottom = top + viewportHeight;
        int hovered = optionAt(context, mouseX, mouseY);
        if (context.usePixelOptionButtons()) {
            renderPixelOptions(context, graphics, hovered, mouseX, mouseY, left, top, viewportBottom);
            return;
        }

        int scissorInset = context.experimentalUnit(4);
        graphics.enableScissor(Math.max(0, left - context.optionWidth()), top, context.optionsScrollbarLeft() - scissorInset, viewportBottom);
        for (int index = 0; index < context.optionCount(); index++) {
            int rowHeight = optionHeight(context, index);
            float y = top + optionOffset(context, index) - context.optionScroll();
            if (y + rowHeight < top || y > viewportBottom) {
                continue;
            }
            renderOption(context, graphics, index, hovered, mouseX, mouseY, left, y, rowHeight, top, viewportBottom);
        }
        graphics.disableScissor();
        context.renderScrollbar(graphics);
    }

    static int optionAt(Context context, double mouseX, double mouseY) {
        int left = context.optionsLeft();
        int top = context.optionsTop();
        int bottom = top + context.optionViewportHeight();
        int horizontalPadding = context.usePixelOptionButtons() ? 0 : context.experimentalUnit(18);
        int verticalPadding = context.usePixelOptionButtons() ? 0 : context.experimentalUnitAtLeast(2, 1);
        if (mouseX < left - horizontalPadding || mouseX > left + context.optionWidth()) {
            return -1;
        }
        if (mouseY < top - verticalPadding || mouseY > bottom + verticalPadding) {
            return -1;
        }
        for (int index = 0; index < context.optionCount(); index++) {
            float y = top + optionOffset(context, index) - context.optionScroll();
            if (mouseY >= y - verticalPadding && mouseY <= y + optionHeight(context, index) + verticalPadding) {
                return index;
            }
        }
        return -1;
    }

    static float optionOffset(Context context, int optionIndex) {
        float offset = 0.0F;
        for (int index = 0; index < optionIndex; index++) {
            offset += optionHeight(context, index) + optionGap(context);
        }
        return offset;
    }

    static int optionHeight(Context context, int optionIndex) {
        if (context.usePixelOptionButtons()) {
            int wrappedLines = pixelOptionLabelLines(context, optionIndex).size();
            return context.optionHeight() + Math.max(0, wrappedLines - 1) * context.pixelOptionLineStep();
        }
        return context.optionHeight() + wrappedExtraHeight(context, optionIndex);
    }

    static float optionContentHeight(Context context) {
        float height = 0.0F;
        for (int index = 0; index < context.optionCount(); index++) {
            height += optionHeight(context, index);
            if (index < context.optionCount() - 1) {
                height += optionGap(context);
            }
        }
        return height;
    }

    private static int optionGap(Context context) {
        int gap = context.optionStride() - context.optionHeight();
        return context.usePixelOptionButtons() ? gap : Math.max(0, gap);
    }

    static List<String> wrappedOptionLabelLines(Context context, String label, float scale) {
        int availableWidth = Math.max(1, context.optionWidth() - context.optionTextInset() * 2);
        int wrapWidth = Math.max(1, Mth.floor(availableWidth / Math.max(scale, 0.001F)));
        return wrapPlainText(context.font(), label, wrapWidth, WRAPPED_OPTION_MAX_LINES);
    }

    private static float wrappedLayoutScale(Context context) {
        return (EXPERIMENTAL_OPTION_BASE_SCALE
                + context.optionSelectedScale()
                + context.optionHoverScale()) * context.experimentalTextScale();
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
        boolean showSelector = index == selectorOption(context, hovered);
        float hoverMix = isHovered ? context.hoverIntensity(mouseX, mouseY, left, y) : 0.0F;
        float textScale = context.experimentalTextScale();
        float scale = (EXPERIMENTAL_OPTION_BASE_SCALE
                + (selected ? context.optionSelectedScale() : 0.0F)
                + hoverMix * context.optionHoverScale()) * textScale;
        float cursorShiftX = isHovered ? context.hoverShift(mouseX, left, context.optionWidth(), context.experimentalUnit(3)) * hoverMix : 0.0F;
        float cursorShiftY = isHovered ? context.hoverShift(mouseY, y, rowHeight, context.experimentalUnit(2)) * hoverMix : 0.0F;
        float edgeAlpha = context.edgeFadeAlpha(y, viewportTop, viewportBottom);
        int textColor = optionTextColor(selected, isHovered);
        float textFadeInAlpha = context.textAlpha();
        if (!VillagerInteractionExperimentalChrome.shouldDrawText(textFadeInAlpha)) {
            return;
        }
        float textAlpha = edgeAlpha * textFadeInAlpha;

        renderExperimentalOptionBackground(context, graphics, isHovered, left, y, rowHeight, textAlpha);
        graphics.pose().pushPose();
        applyExperimentalOptionTransform(context, graphics, left, y, scale, cursorShiftX, cursorShiftY);
        renderWrappedLabel(context, graphics, context.optionLabel(index), left, y, rowHeight, scale, textColor, textFadeInAlpha, viewportTop, viewportBottom, false);
        if (showSelector) {
            renderSelector(context, graphics, left, y, rowHeight, scale, cursorShiftX, cursorShiftY, textAlpha, isHovered);
        }
        graphics.pose().popPose();
    }

    private static int selectorOption(Context context, int hovered) {
        return hovered >= 0 ? hovered : context.selectedOption();
    }

    private static void renderPixelOptions(
            Context context,
            GuiGraphics graphics,
            int hovered,
            int mouseX,
            int mouseY,
            int left,
            int top,
            int viewportBottom) {
        int scissorRight = left + context.optionWidth();
        if (context.pixelOptionSelectionArrowTexture() != null) {
            scissorRight += context.pixelOptionSelectionArrowGap() + context.pixelOptionSelectionArrowWidth();
        }
        graphics.enableScissor(left, top, scissorRight, viewportBottom);
        for (int index = 0; index < context.optionCount(); index++) {
            int rowHeight = optionHeight(context, index);
            float y = top + optionOffset(context, index) - context.optionScroll();
            if (y + rowHeight < top || y > viewportBottom) {
                continue;
            }
            renderPixelOption(context, graphics, index, hovered, left, Mth.floor(y), rowHeight);
        }
        graphics.disableScissor();
        renderPixelScrollArrows(context, graphics, left, top, viewportBottom);
    }

    private static void renderPixelOption(Context context, GuiGraphics graphics, int index, int hovered, int left, int top, int rowHeight) {
        boolean selected = index == context.selectedOption();
        boolean isHovered = hovered == index;
        boolean keyboardFocused = selected && context.pixelOptionKeyboardFocusVisible();
        boolean active = isHovered || keyboardFocused;
        ResourceLocation texture = context.pixelOptionTexture(keyboardFocused, isHovered);
        blitPixelOptionButton(graphics, texture, left, top, context.optionWidth(), rowHeight);

        int textLeft = left + context.optionTextInset();
        int textWidth = Math.max(1, context.optionWidth() - context.optionTextInset() - context.pixelOptionTextRightPadding());
        List<String> lines = pixelOptionLabelLines(context, index);
        int textColor = context.pixelOptionTextColor(keyboardFocused, isHovered || active);
        for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
            graphics.drawString(
                    context.font(),
                    fitPlainText(context.font(), lines.get(lineIndex), textWidth),
                    textLeft,
                    top + context.pixelOptionTextTop() + lineIndex * context.pixelOptionLineStep(),
                    textColor,
                    false
            );
        }
        if (active) {
            renderPixelSelectionArrow(context, graphics, left, top, rowHeight);
        }
    }

    private static void renderPixelSelectionArrow(Context context, GuiGraphics graphics, int left, int top, int rowHeight) {
        ResourceLocation texture = context.pixelOptionSelectionArrowTexture();
        if (texture == null) {
            return;
        }

        int arrowWidth = context.pixelOptionSelectionArrowWidth();
        int arrowHeight = context.pixelOptionSelectionArrowHeight();
        int arrowLeft = left + context.optionWidth() + context.pixelOptionSelectionArrowGap();
        int arrowTop = top + Math.max(0, (rowHeight - arrowHeight) / 2);
        graphics.blit(
                texture,
                arrowLeft,
                arrowTop,
                0,
                0,
                arrowWidth,
                arrowHeight,
                arrowWidth,
                arrowHeight
        );
    }

    private static void renderPixelScrollArrows(Context context, GuiGraphics graphics, int left, int top, int viewportBottom) {
        float maxScroll = Math.max(0.0F, optionContentHeight(context) - context.optionViewportHeight());
        int arrowWidth = context.pixelOptionArrowWidth();
        int arrowHeight = context.pixelOptionArrowHeight();
        int arrowX = left + (context.optionWidth() - arrowWidth) / 2;
        if (context.optionScroll() > 0.75F && context.pixelOptionArrowUpTexture() != null) {
            graphics.blit(
                    context.pixelOptionArrowUpTexture(),
                    arrowX,
                    top - arrowHeight - 1,
                    0,
                    0,
                    arrowWidth,
                    arrowHeight,
                    arrowWidth,
                    arrowHeight
            );
        }
        if (context.optionScroll() < maxScroll - 0.75F && context.pixelOptionArrowDownTexture() != null) {
            graphics.blit(
                    context.pixelOptionArrowDownTexture(),
                    arrowX,
                    viewportBottom + 1,
                    0,
                    0,
                    arrowWidth,
                    arrowHeight,
                    arrowWidth,
                    arrowHeight
            );
        }
    }

    private static void blitPixelOptionButton(GuiGraphics graphics, ResourceLocation texture, int left, int top, int width, int height) {
        int centerSourceWidth = PIXEL_OPTION_TEXTURE_WIDTH - PIXEL_OPTION_SLICE_LEFT - PIXEL_OPTION_SLICE_RIGHT;
        int centerSourceHeight = PIXEL_OPTION_TEXTURE_HEIGHT - PIXEL_OPTION_SLICE_TOP - PIXEL_OPTION_SLICE_BOTTOM;
        int centerWidth = Math.max(0, width - PIXEL_OPTION_SLICE_LEFT - PIXEL_OPTION_SLICE_RIGHT);
        int centerHeight = Math.max(0, height - PIXEL_OPTION_SLICE_TOP - PIXEL_OPTION_SLICE_BOTTOM);

        blitPixelOptionSlice(graphics, texture, left, top, PIXEL_OPTION_SLICE_LEFT, PIXEL_OPTION_SLICE_TOP, 0, 0, PIXEL_OPTION_SLICE_LEFT, PIXEL_OPTION_SLICE_TOP);
        blitPixelOptionSlice(graphics, texture, left + PIXEL_OPTION_SLICE_LEFT, top, centerWidth, PIXEL_OPTION_SLICE_TOP, PIXEL_OPTION_SLICE_LEFT, 0, centerSourceWidth, PIXEL_OPTION_SLICE_TOP);
        blitPixelOptionSlice(graphics, texture, left + width - PIXEL_OPTION_SLICE_RIGHT, top, PIXEL_OPTION_SLICE_RIGHT, PIXEL_OPTION_SLICE_TOP, PIXEL_OPTION_TEXTURE_WIDTH - PIXEL_OPTION_SLICE_RIGHT, 0, PIXEL_OPTION_SLICE_RIGHT, PIXEL_OPTION_SLICE_TOP);

        blitPixelOptionSlice(graphics, texture, left, top + PIXEL_OPTION_SLICE_TOP, PIXEL_OPTION_SLICE_LEFT, centerHeight, 0, PIXEL_OPTION_SLICE_TOP, PIXEL_OPTION_SLICE_LEFT, centerSourceHeight);
        blitPixelOptionSlice(graphics, texture, left + PIXEL_OPTION_SLICE_LEFT, top + PIXEL_OPTION_SLICE_TOP, centerWidth, centerHeight, PIXEL_OPTION_SLICE_LEFT, PIXEL_OPTION_SLICE_TOP, centerSourceWidth, centerSourceHeight);
        blitPixelOptionSlice(graphics, texture, left + width - PIXEL_OPTION_SLICE_RIGHT, top + PIXEL_OPTION_SLICE_TOP, PIXEL_OPTION_SLICE_RIGHT, centerHeight, PIXEL_OPTION_TEXTURE_WIDTH - PIXEL_OPTION_SLICE_RIGHT, PIXEL_OPTION_SLICE_TOP, PIXEL_OPTION_SLICE_RIGHT, centerSourceHeight);

        blitPixelOptionSlice(graphics, texture, left, top + height - PIXEL_OPTION_SLICE_BOTTOM, PIXEL_OPTION_SLICE_LEFT, PIXEL_OPTION_SLICE_BOTTOM, 0, PIXEL_OPTION_TEXTURE_HEIGHT - PIXEL_OPTION_SLICE_BOTTOM, PIXEL_OPTION_SLICE_LEFT, PIXEL_OPTION_SLICE_BOTTOM);
        blitPixelOptionSlice(graphics, texture, left + PIXEL_OPTION_SLICE_LEFT, top + height - PIXEL_OPTION_SLICE_BOTTOM, centerWidth, PIXEL_OPTION_SLICE_BOTTOM, PIXEL_OPTION_SLICE_LEFT, PIXEL_OPTION_TEXTURE_HEIGHT - PIXEL_OPTION_SLICE_BOTTOM, centerSourceWidth, PIXEL_OPTION_SLICE_BOTTOM);
        blitPixelOptionSlice(graphics, texture, left + width - PIXEL_OPTION_SLICE_RIGHT, top + height - PIXEL_OPTION_SLICE_BOTTOM, PIXEL_OPTION_SLICE_RIGHT, PIXEL_OPTION_SLICE_BOTTOM, PIXEL_OPTION_TEXTURE_WIDTH - PIXEL_OPTION_SLICE_RIGHT, PIXEL_OPTION_TEXTURE_HEIGHT - PIXEL_OPTION_SLICE_BOTTOM, PIXEL_OPTION_SLICE_RIGHT, PIXEL_OPTION_SLICE_BOTTOM);
    }

    private static void blitPixelOptionSlice(
            GuiGraphics graphics,
            ResourceLocation texture,
            int destLeft,
            int destTop,
            int destWidth,
            int destHeight,
            int sourceLeft,
            int sourceTop,
            int sourceWidth,
            int sourceHeight) {
        if (destWidth <= 0 || destHeight <= 0 || sourceWidth <= 0 || sourceHeight <= 0) {
            return;
        }
        graphics.blit(
                texture,
                destLeft,
                destTop,
                destWidth,
                destHeight,
                (float) sourceLeft,
                (float) sourceTop,
                sourceWidth,
                sourceHeight,
                PIXEL_OPTION_TEXTURE_WIDTH,
                PIXEL_OPTION_TEXTURE_HEIGHT
        );
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

    private static void renderSelector(
            Context context,
            GuiGraphics graphics,
            int left,
            float top,
            int rowHeight,
            float scale,
            float shiftX,
            float shiftY,
            float alpha,
            boolean hovered) {
        int textLeft = left + context.optionTextInset();
        float pivotX = textLeft;
        float pivotY = top + optionTextYOffset(context.optionHeight());
        float desiredScreenX = textLeft + shiftX - context.experimentalUnit(SELECTOR_LABEL_GAP);
        float desiredScreenY = top + rowHeight * 0.5F + shiftY - context.font().lineHeight * scale * 0.5F + 3.0F + (hovered ? 1.0F : 0.0F);
        int localX = Mth.floor(pivotX + (desiredScreenX - pivotX - shiftX) / Math.max(scale, 0.001F));
        int localY = Mth.floor(pivotY + (desiredScreenY - pivotY - shiftY) / Math.max(scale, 0.001F));
        graphics.drawString(context.font(), ">", localX, localY, VillagerInteractionUiUtil.withAlpha(0xFFFFFFFF, alpha), false);
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
        List<String> lines = wrappedOptionLabelLines(context, label, wrappedLayoutScale(context));
        int baseHeight = context.optionHeight();
        float textTop = top + optionTextYOffset(baseHeight);
        float lineStep = context.font().lineHeight + 1.0F;
        boolean wrapped = lines.size() > 1;
        if (wrapped) {
            lineStep = baseHeight / Math.max(scale, 0.001F);
        }
        for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
            float lineTop = textTop + lineIndex * lineStep;
            float lineScreenTop = top + optionTextYOffset(baseHeight) + lineIndex * baseHeight;
            float lineAlpha = alpha * context.edgeFadeAlpha(lineScreenTop, viewportTop, viewportBottom);
            if (VillagerInteractionExperimentalChrome.shouldDrawText(lineAlpha)) {
                int lineColor = VillagerInteractionUiUtil.withAlpha(color, lineAlpha);
                if (wrapped) {
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

        int bgLeft = left - context.experimentalUnit(12);
        int bgTop = Mth.floor(top + 1.0F);
        int bgRight = Math.min(context.optionsScrollbarLeft() - context.experimentalUnit(8), left + context.optionWidth() - context.experimentalUnit(8));
        int bgBottom = bgTop + rowHeight - 1;
        if (bgRight > bgLeft) {
            graphics.fill(bgLeft, bgTop, bgRight, bgBottom, VillagerInteractionUiUtil.withAlpha(0xFF000000, edgeAlpha * 0.16F));
        }
    }

    private static int wrappedExtraHeight(Context context, int optionIndex) {
        if (context.usePixelOptionButtons()) {
            return 0;
        }
        float scale = wrappedLayoutScale(context);
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

    private static int optionTextColor(boolean selected, boolean hovered) {
        if (selected) {
            return 0xFFF8F8F4;
        }
        return hovered ? 0xFFE5E5DE : 0xCFC7C8C5;
    }

    private static String fitPlainText(Font font, String text, int width) {
        if (text == null || text.isBlank() || font.width(text) <= width) {
            return text == null ? "" : text;
        }

        String ellipsis = "...";
        int ellipsisWidth = font.width(ellipsis);
        if (width <= ellipsisWidth) {
            return font.plainSubstrByWidth(text, Math.max(0, width));
        }
        return font.plainSubstrByWidth(text, Math.max(0, width - ellipsisWidth)) + ellipsis;
    }

    static List<String> pixelOptionLabelLines(Context context, int index) {
        return wrapPlainTextByCharacters(context.pixelOptionLabel(index), context.pixelOptionMaxLineCharacters());
    }

    private static List<String> wrapPlainTextByCharacters(String text, int maxCharacters) {
        if (text == null || text.isBlank()) {
            return List.of("");
        }
        int max = Math.max(1, maxCharacters);
        List<String> lines = new java.util.ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (word.isBlank()) {
                continue;
            }
            if (word.length() > max) {
                if (!line.isEmpty()) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                for (int start = 0; start < word.length(); start += max) {
                    lines.add(word.substring(start, Math.min(word.length(), start + max)));
                }
                continue;
            }

            String candidate = line.isEmpty() ? word : line + " " + word;
            if (candidate.length() <= max || line.isEmpty()) {
                line.setLength(0);
                line.append(candidate);
                continue;
            }

            lines.add(line.toString());
            line.setLength(0);
            line.append(word);
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return lines.isEmpty() ? List.of(text) : lines;
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

        float experimentalTextScale();

        int experimentalUnit(int value);

        int experimentalUnitAtLeast(int value, int minimum);

        int optionsScrollbarLeft();

        float textAlpha();

        default boolean usePixelOptionButtons() {
            return false;
        }

        default ResourceLocation pixelOptionTexture(boolean selected, boolean hovered) {
            return null;
        }

        default boolean pixelOptionKeyboardFocusVisible() {
            return false;
        }

        default String pixelOptionLabel(int index) {
            return optionLabel(index);
        }

        default int pixelOptionTextTop() {
            return 4;
        }

        default int pixelOptionTextRightPadding() {
            return 4;
        }

        default int pixelOptionMaxLineCharacters() {
            return 20;
        }

        default int pixelOptionLineStep() {
            return 10;
        }

        default int pixelOptionTextColor(boolean selected, boolean hovered) {
            return 0xFF2E2418;
        }

        default ResourceLocation pixelOptionArrowUpTexture() {
            return null;
        }

        default ResourceLocation pixelOptionArrowDownTexture() {
            return null;
        }

        default int pixelOptionArrowWidth() {
            return 9;
        }

        default int pixelOptionArrowHeight() {
            return 6;
        }

        default ResourceLocation pixelOptionSelectionArrowTexture() {
            return null;
        }

        default int pixelOptionSelectionArrowWidth() {
            return 0;
        }

        default int pixelOptionSelectionArrowHeight() {
            return 0;
        }

        default int pixelOptionSelectionArrowGap() {
            return 0;
        }

        void renderScrollbar(GuiGraphics graphics);
    }
}
