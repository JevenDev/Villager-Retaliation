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
        boolean hovered = context.topBackButtonHovered(mouseX, mouseY);
        int textColor = hovered ? 0xFFF8F8F4 : 0xCFC7C8C5;
        int backgroundColor = hovered ? 0x30000000 : 0x18000000;

        graphics.fill(left - 6, top - 2, right + 4, bottom + 2, backgroundColor);
        graphics.drawString(context.font(), context.backLabel(), left, top, textColor, false);
    }

    static void renderHint(Context context, GuiGraphics graphics) {
        String hintText = context.hintText();
        Font font = context.font();
        graphics.drawString(font, hintText, context.screenWidth() - font.width(hintText) - 8, context.screenHeight() - 14, 0x66FFFFFF, false);
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
    }
}
