package com.jvn.villagerretaliation.client.interaction;

import com.jvn.villagerretaliation.client.ui.VillagerClientUiUtil;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

final class VillagerInteractionConversationPanel {
    private static final int INFO_ACTION_X_OFFSET = 28;
    private static final int INFO_ACTION_PADDING_X = 6;
    private static final int INFO_ACTION_PADDING_Y = 2;
    private static final float OPTION_TEXT_Y_OFFSET = 5.0F;

    private VillagerInteractionConversationPanel() {
    }

    static void render(Context context, GuiGraphics graphics, int mouseX, int mouseY) {
        renderConversationFocus(context, graphics, mouseX, mouseY);
        renderDivider(context, graphics);
    }

    static boolean isPointInsideFamilyButton(Context context, double mouseX, double mouseY) {
        int y = infoBaseY(context, context.conversationInfoTop()) + context.optionStride() * 5;
        return buttonBounds(context, y, context.familyButtonText()).contains(mouseX, mouseY);
    }

    static boolean isPointInsideRelationshipButton(Context context, double mouseX, double mouseY) {
        int y = infoBaseY(context, context.conversationInfoTop()) + context.optionStride() * 6;
        return buttonBounds(context, y, context.relationshipButtonText()).contains(mouseX, mouseY);
    }

    static int skillInfoViewportBottom(Context context) {
        int y = infoBaseY(context, context.conversationInfoTop()) + context.optionStride() * 5;
        return buttonBounds(context, y, context.familyButtonText()).bottom() + INFO_ACTION_PADDING_Y;
    }

    static float optionTextTop(float optionY) {
        return optionY + OPTION_TEXT_Y_OFFSET;
    }

    private static void renderConversationFocus(Context context, GuiGraphics graphics, int mouseX, int mouseY) {
        int dividerX = context.dividerX();
        int infoBaseY = infoBaseY(context, context.conversationInfoTop());
        int infoLineGap = context.optionStride();

        drawRightAlignedInfo(context, graphics, context.villagerName(), infoBaseY, context.infoValueColor(), dividerX);
        drawRightAlignedInfo(context, graphics, context.professionName(), infoBaseY + infoLineGap, context.infoSecondaryColor(), dividerX);
        drawRightAlignedInfo(context, graphics, context.genderText(), infoBaseY + infoLineGap * 2, context.infoSecondaryColor(), dividerX);
        drawRightAlignedInfo(context, graphics, context.moodText(), infoBaseY + infoLineGap * 3, context.moodColor(), dividerX);
        drawRightAlignedInfo(context, graphics, context.reputationText(), infoBaseY + infoLineGap * 4, context.infoLabelColor(), dividerX);
        renderInfoActionButton(
                context,
                graphics,
                context.familyButtonText(),
                infoBaseY + infoLineGap * 5,
                mouseX,
                mouseY,
                context.familyPageActive());
        renderInfoActionButton(
                context,
                graphics,
                context.relationshipButtonText(),
                infoBaseY + infoLineGap * 6,
                mouseX,
                mouseY,
                context.relationshipPageActive());
    }

    private static void drawRightAlignedInfo(Context context, GuiGraphics graphics, String text, int y, int color, int dividerX) {
        Font font = context.font();
        graphics.drawString(font, text, dividerX - INFO_ACTION_X_OFFSET - font.width(text), y, color, false);
    }

    private static void renderInfoActionButton(
            Context context,
            GuiGraphics graphics,
            String text,
            int y,
            int mouseX,
            int mouseY,
            boolean active) {
        Font font = context.font();
        ButtonBounds bounds = buttonBounds(context, y, text);
        boolean hovered = bounds.contains(mouseX, mouseY);
        int color = active ? context.infoValueColor() : hovered ? 0xFFE5E5DE : context.infoSecondaryColor();
        if (hovered || active) {
            graphics.fill(
                    bounds.left() - INFO_ACTION_PADDING_X,
                    bounds.top() - INFO_ACTION_PADDING_Y,
                    bounds.right() + 4,
                    bounds.bottom() + INFO_ACTION_PADDING_Y,
                    hovered ? 0x30000000 : 0x18000000);
        }
        graphics.drawString(font, text, bounds.left(), y, color, false);
    }

    private static void renderDivider(Context context, GuiGraphics graphics) {
        int dividerX = context.dividerX();
        int dividerTop = context.conversationInfoTop() - 12;
        int dividerBottom = context.conversationInfoTop() + context.rootOptionViewportHeight() + 2;
        int lineLeft = dividerX - 1;
        int lineRight = dividerX + 1;
        int selectorTop = dividerTop + (context.dividerHeight() - context.dividerSelectHeight()) / 2;
        int selectorBottom = selectorTop + context.dividerSelectHeight();

        float selectorAnchorY = context.giftPageActive() ? Float.NaN : dividerSelectorAnchorY(context, dividerTop, dividerBottom);
        if (!Float.isNaN(selectorAnchorY)) {
            selectorTop = Mth.floor(selectorAnchorY + context.optionHeight() * 0.5F - context.dividerSelectHeight() * 0.5F);
            selectorTop = Mth.clamp(selectorTop, dividerTop, dividerBottom - context.dividerSelectHeight());
            selectorBottom = selectorTop + context.dividerSelectHeight();
        }

        graphics.fill(lineLeft, dividerTop, lineRight, selectorTop, context.dividerCoreColor());
        graphics.fill(lineLeft, selectorBottom, lineRight, dividerBottom, context.dividerCoreColor());

        int selectorLeft = lineRight - context.dividerSelectWidth();
        graphics.blit(
                context.dividerSelectTexture(),
                selectorLeft,
                selectorTop,
                0,
                0,
                context.dividerSelectWidth(),
                context.dividerSelectHeight(),
                context.dividerSelectWidth(),
                context.dividerSelectHeight()
        );
    }

    private static float dividerSelectorAnchorY(Context context, int dividerTop, int dividerBottom) {
        if (context.selectedOption() < 0 || context.selectedOption() >= context.optionCount()) {
            return Float.NaN;
        }

        int viewportTop = context.optionsTop();
        int viewportBottom = context.optionsTop() + context.optionViewportHeight();
        float selectedY = context.optionsTop() + context.selectedOption() * context.optionStride() - context.optionScroll();
        if (isOptionTextFullyVisible(context, selectedY, viewportTop, viewportBottom)) {
            return selectedY;
        }

        float selectedTextTop = optionTextTop(selectedY);
        float selectedTextBottom = selectedTextTop + context.font().lineHeight;
        if (selectedTextBottom > viewportBottom) {
            for (int index = context.selectedOption() - 1; index >= 0; index--) {
                float optionY = context.optionsTop() + index * context.optionStride() - context.optionScroll();
                if (isOptionTextFullyVisible(context, optionY, viewportTop, viewportBottom)) {
                    return optionY;
                }
            }
        } else if (selectedTextTop < viewportTop) {
            for (int index = context.selectedOption() + 1; index < context.optionCount(); index++) {
                float optionY = context.optionsTop() + index * context.optionStride() - context.optionScroll();
                if (isOptionTextFullyVisible(context, optionY, viewportTop, viewportBottom)) {
                    return optionY;
                }
            }
        }

        return Mth.clamp(selectedY, dividerTop, dividerBottom - context.optionHeight());
    }

    private static boolean isOptionTextFullyVisible(Context context, float optionY, int viewportTop, int viewportBottom) {
        float textTop = optionTextTop(optionY);
        float textBottom = textTop + context.font().lineHeight;
        return textTop >= viewportTop && textBottom <= viewportBottom;
    }

    private static int infoBaseY(Context context, int optionsTop) {
        return Mth.floor(optionTextTop(optionsTop));
    }

    private static ButtonBounds buttonBounds(Context context, int y, String text) {
        int width = context.font().width(text);
        int left = context.dividerX() - INFO_ACTION_X_OFFSET - width;
        return new ButtonBounds(left, left + width, y, y + context.font().lineHeight);
    }

    interface Context {
        Font font();

        int dividerX();

        int conversationInfoTop();

        int optionsTop();

        int optionViewportHeight();

        int rootOptionViewportHeight();

        int optionStride();

        int optionHeight();

        int optionCount();

        int selectedOption();

        float optionScroll();

        boolean giftPageActive();

        boolean familyPageActive();

        boolean relationshipPageActive();

        String villagerName();

        String professionName();

        String genderText();

        String moodText();

        String reputationText();

        String familyButtonText();

        String relationshipButtonText();

        int moodColor();

        int infoValueColor();

        int infoSecondaryColor();

        int infoLabelColor();

        int dividerCoreColor();

        int dividerHeight();

        int dividerSelectWidth();

        int dividerSelectHeight();

        ResourceLocation dividerSelectTexture();
    }

    private record ButtonBounds(int left, int right, int top, int bottom) {
        boolean contains(double mouseX, double mouseY) {
            return VillagerClientUiUtil.containsInclusive(mouseX, mouseY, this.left, this.top - INFO_ACTION_PADDING_Y, this.right, this.bottom + INFO_ACTION_PADDING_Y);
        }
    }
}
