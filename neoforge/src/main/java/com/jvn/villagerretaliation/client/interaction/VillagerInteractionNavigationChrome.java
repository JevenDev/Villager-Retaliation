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
        float alpha = context.chromeAlpha();
        if (!VillagerInteractionExperimentalChrome.shouldDrawText(alpha)) {
            return;
        }
        boolean hovered = context.topBackButtonHovered(mouseX, mouseY);
        int textColor = withAlpha(hovered ? 0xFFF8F8F4 : 0xCFC7C8C5, alpha);
        int backgroundColor = withAlpha(hovered ? 0x30000000 : 0x18000000, alpha);
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
        float alpha = context.chromeAlpha();
        if (!VillagerInteractionExperimentalChrome.shouldDrawText(alpha)) {
            return;
        }
        int width = Math.round(font.width(hintText) * scale);
        int height = Math.round(font.lineHeight * scale);
        VillagerInteractionUiUtil.drawScaledString(
                graphics,
                font,
                hintText,
                context.hintRight() - width,
                context.screenHeight() - height - Math.round(5.0F * scale),
                withAlpha(0x66FFFFFF, alpha),
                scale);
    }

    private static int withAlpha(int color, float alphaFactor) {
        int alpha = Math.round(((color >>> 24) & 0xFF) * alphaFactor);
        alpha = Math.max(0, Math.min(255, alpha));
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    interface Context {
        Font font();

        int screenWidth();

        int screenHeight();

        int hintRight();

        boolean topBackButtonVisible();

        boolean topBackButtonHovered(int mouseX, int mouseY);

        int topBackLeft();

        int topBackRight();

        int topBackTop();

        int topBackBottom();

        String backLabel();

        String hintText();

        float textScale();

        float chromeAlpha();
    }
}
