package com.jvn.villagerretaliation.client.interaction;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

final class VillagerInteractionOptionList {
    private static final float EXPERIMENTAL_OPTION_BASE_SCALE = 1.48F;

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
            float y = top + index * context.optionStride() - context.optionScroll();
            if (y + context.optionHeight() < top - 10 || y > viewportBottom + 10) {
                continue;
            }
            renderOption(context, graphics, index, hovered, mouseX, mouseY, left, y, top, viewportBottom);
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
            float y = top + index * context.optionStride() - context.optionScroll();
            if (y + context.optionHeight() < top || y > viewportBottom) {
                continue;
            }
            renderExperimentalOption(context, graphics, index, hovered, mouseX, mouseY, left, y, top, viewportBottom);
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
            float y = top + index * context.optionStride() - context.optionScroll();
            if (mouseY >= y - 2.0F && mouseY <= y + context.optionHeight() + 2.0F) {
                return index;
            }
        }
        return -1;
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
            int viewportTop,
            int viewportBottom
    ) {
        boolean selected = index == context.selectedOption();
        boolean isHovered = hovered == index;
        float hoverMix = isHovered ? context.hoverIntensity(mouseX, mouseY, left, y) : 0.0F;
        float scale = optionScale(context, selected, hoverMix);
        float cursorShiftX = isHovered ? context.hoverShift(mouseX, left, context.optionWidth(), 3.2F) * hoverMix : 0.0F;
        float cursorShiftY = isHovered ? context.hoverShift(mouseY, y, context.optionHeight(), 1.6F) * hoverMix : 0.0F;
        float edgeAlpha = context.edgeFadeAlpha(y, viewportTop, viewportBottom);
        int textColor = optionTextColor(selected, isHovered);

        graphics.pose().pushPose();
        applyOptionTransform(context, graphics, left, y, scale, cursorShiftX, cursorShiftY);
        renderOptionBackground(context, graphics, isHovered, left, y, edgeAlpha);
        if (selected) {
            graphics.drawString(context.font(), ">", left - 7, Mth.floor(y + optionTextYOffset(context)), VillagerInteractionUiUtil.withAlpha(0xFFFFFFFF, edgeAlpha), false);
        }
        graphics.drawString(
                context.font(),
                context.optionLabel(index),
                left + context.optionTextInset(),
                Mth.floor(y + optionTextYOffset(context)),
                VillagerInteractionUiUtil.withAlpha(textColor, edgeAlpha),
                false);
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
        float cursorShiftY = isHovered ? context.hoverShift(mouseY, y, context.optionHeight(), 1.6F * textScale) * hoverMix : 0.0F;
        float edgeAlpha = context.edgeFadeAlpha(y, viewportTop, viewportBottom);
        int textColor = optionTextColor(selected, isHovered);
        float textFadeInAlpha = VillagerInteractionExperimentalChrome.textFadeInAlpha();
        if (!VillagerInteractionExperimentalChrome.shouldDrawText(textFadeInAlpha)) {
            return;
        }
        float textAlpha = edgeAlpha * textFadeInAlpha;

        renderExperimentalOptionBackground(context, graphics, isHovered, left, y, textAlpha);
        graphics.pose().pushPose();
        applyExperimentalOptionTransform(context, graphics, left, y, scale, cursorShiftX, cursorShiftY);
        if (selected) {
            graphics.drawString(context.font(), ">", left - 7, Mth.floor(y + optionTextYOffset(context)), VillagerInteractionUiUtil.withAlpha(0xFFFFFFFF, textAlpha), false);
        }
        graphics.drawString(
                context.font(),
                context.optionLabel(index),
                left + context.optionTextInset(),
                Mth.floor(y + optionTextYOffset(context)),
                VillagerInteractionUiUtil.withAlpha(textColor, textAlpha),
                false);
        graphics.pose().popPose();
    }

    private static void applyOptionTransform(Context context, GuiGraphics graphics, int left, float top, float scale, float shiftX, float shiftY) {
        float pivotX = left + context.optionWidth() * 0.5F;
        float pivotY = top + context.optionHeight() * 0.5F;
        graphics.pose().translate(pivotX + shiftX, pivotY + shiftY, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.pose().translate(-pivotX, -pivotY, 0.0F);
    }

    private static void applyExperimentalOptionTransform(Context context, GuiGraphics graphics, int left, float top, float scale, float shiftX, float shiftY) {
        float pivotX = left + context.optionTextInset();
        float pivotY = top + optionTextYOffset(context);
        graphics.pose().translate(pivotX + shiftX, pivotY + shiftY, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.pose().translate(-pivotX, -pivotY, 0.0F);
    }

    private static float optionTextYOffset(Context context) {
        return context.optionHeight() * (5.0F / 18.0F);
    }

    private static void renderExperimentalOptionBackground(Context context, GuiGraphics graphics, boolean hovered, int left, float top, float edgeAlpha) {
        if (!hovered) {
            return;
        }

        int bgLeft = left - 12;
        int bgTop = Mth.floor(top + 1.0F);
        int bgRight = Math.min(context.optionsScrollbarLeft() - 8, left + context.optionWidth() - 8);
        int bgBottom = bgTop + context.optionHeight() - 1;
        if (bgRight > bgLeft) {
            graphics.fill(bgLeft, bgTop, bgRight, bgBottom, VillagerInteractionUiUtil.withAlpha(0xFF000000, edgeAlpha * 0.16F));
        }
    }

    private static void renderOptionBackground(Context context, GuiGraphics graphics, boolean hovered, int left, float top, float edgeAlpha) {
        if (!hovered) {
            return;
        }

        int bgLeft = left - 12;
        int bgTop = Mth.floor(top + 1.0F);
        int bgRight = left + context.optionWidth() - 8;
        int bgBottom = bgTop + context.optionHeight() - 1;
        graphics.fill(bgLeft, bgTop, bgRight, bgBottom, VillagerInteractionUiUtil.withAlpha(0xFF000000, edgeAlpha * 0.16F));
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
