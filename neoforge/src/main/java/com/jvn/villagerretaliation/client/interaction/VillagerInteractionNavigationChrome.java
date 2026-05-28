package com.jvn.villagerretaliation.client.interaction;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

final class VillagerInteractionNavigationChrome {
    private VillagerInteractionNavigationChrome() {
    }

    static void renderTopBackButton(Context context, GuiGraphics graphics, int mouseX, int mouseY) {
        if (!context.topBackButtonVisible()) {
            return;
        }

        int left = context.topBackLeft();
        int right = context.topBackRight();
        int top = context.topBackTop();
        int bottom = context.topBackBottom();
        float scale = context.textScale();
        boolean hovered = context.topBackButtonHovered(mouseX, mouseY);
        int textColor = hovered ? 0xFFF8F8F4 : 0xCFC7C8C5;
        int backgroundColor = hovered ? 0x30000000 : 0x18000000;
        int horizontalPad = Math.round(6.0F * scale);
        int rightPad = Math.round(4.0F * scale);
        int verticalPad = Math.round(2.0F * scale);

        graphics.fill(left - horizontalPad, top - verticalPad, right + rightPad, bottom + verticalPad, backgroundColor);
        VillagerInteractionUiUtil.drawScaledString(graphics, context.font(), context.backLabel(), left, top, textColor, scale);
    }

    static void renderHint(Context context, GuiGraphics graphics) {
        String hintText = context.hintText();
        Font font = context.font();
        float scale = context.textScale();
        int width = Math.round(font.width(hintText) * scale);
        int height = Math.round(font.lineHeight * scale);
        VillagerInteractionUiUtil.drawScaledString(graphics, font, hintText, context.screenWidth() - width - 8, context.screenHeight() - height - 5, 0x66FFFFFF, scale);
    }

    interface Context {
        Font font();

        int screenWidth();

        int screenHeight();

        boolean topBackButtonVisible();

        boolean topBackButtonHovered(int mouseX, int mouseY);

        int topBackLeft();

        int topBackRight();

        int topBackTop();

        int topBackBottom();

        String backLabel();

        String hintText();

        float textScale();
    }
}
