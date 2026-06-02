package com.jvn.villagerretaliation.client.interaction;

import com.jvn.toucanlib.client.ToucanColors;
import com.jvn.toucanlib.client.ToucanGuiText;
import com.jvn.toucanlib.client.ToucanScrollbarThumb;
import com.jvn.toucanlib.client.ToucanScrollbars;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.joml.Vector2i;
import org.joml.Vector2ic;

final class VillagerInteractionUiUtil {
    private VillagerInteractionUiUtil() {
    }

    static float edgeFadeAlpha(
            float currentScroll,
            float maxScroll,
            float elementTop,
            float elementBottom,
            int viewportTop,
            int viewportBottom,
            float fadeBand) {
        return ToucanScrollbars.edgeFadeAlpha(
                currentScroll,
                maxScroll,
                elementTop,
                elementBottom,
                viewportTop,
                viewportBottom,
                fadeBand);
    }

    static ToucanScrollbarThumb buildScrollbarThumb(
            int viewportTop,
            int viewportHeight,
            int scrollbarLeft,
            int scrollbarWidth,
            int scrollbarHitWidth,
            int minimumThumbHeight,
            float currentScroll,
            float maxScroll,
            float contentHeight) {
        return ToucanScrollbars.buildThumb(
                viewportTop,
                viewportHeight,
                scrollbarLeft,
                scrollbarWidth,
                scrollbarHitWidth,
                minimumThumbHeight,
                currentScroll,
                maxScroll,
                contentHeight);
    }

    static int withAlpha(int color, float alphaFactor) {
        return ToucanColors.multiplyAlpha(color, alphaFactor);
    }

    static int scaledWrapWidth(int width, float scale) {
        return ToucanGuiText.scaledWrapWidth(width, scale);
    }

    static int scaledLineStep(Font font, float scale) {
        return ToucanGuiText.scaledLineStep(font, scale);
    }

    static void drawScaledString(GuiGraphics graphics, Font font, String text, int left, int top, int color, float scale) {
        ToucanGuiText.drawScaledString(graphics, font, text, left, top, color, scale);
    }

    static void drawScaledString(GuiGraphics graphics, Font font, FormattedCharSequence text, int left, int top, int color, float scale) {
        ToucanGuiText.drawScaledString(graphics, font, text, left, top, color, scale);
    }

    static void renderScaledComponentTooltip(GuiGraphics graphics, Font font, List<Component> tooltip, int mouseX, int mouseY, float scale) {
        graphics.pose().pushPose();
        graphics.pose().scale(scale, scale, 1.0F);
        renderBoundedComponentTooltipInCurrentPose(
                graphics,
                font,
                tooltip,
                Mth.floor(mouseX / Math.max(scale, 0.001F)),
                Mth.floor(mouseY / Math.max(scale, 0.001F)),
                scale,
                0.0F,
                0.0F);
        graphics.pose().popPose();
    }

    static void renderBoundedComponentTooltipInCurrentPose(
            GuiGraphics graphics,
            Font font,
            List<Component> tooltip,
            int mouseX,
            int mouseY,
            float scale,
            float originX,
            float originY) {
        if (tooltip.isEmpty()) {
            return;
        }
        List<FormattedCharSequence> lines = tooltip.stream()
                .map(Component::getVisualOrderText)
                .toList();
        graphics.renderTooltip(font, lines, boundedTooltipPositioner(graphics, scale, originX, originY), mouseX, mouseY);
    }

    static void renderBoundedItemTooltipInCurrentPose(
            GuiGraphics graphics,
            Font font,
            ItemStack stack,
            int mouseX,
            int mouseY,
            float scale,
            float originX,
            float originY) {
        if (stack.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        TooltipFlag tooltipFlag = minecraft.options.advancedItemTooltips ? TooltipFlag.ADVANCED : TooltipFlag.NORMAL;
        Item.TooltipContext tooltipContext = minecraft.level == null ? Item.TooltipContext.EMPTY : Item.TooltipContext.of(minecraft.level);
        renderBoundedComponentTooltipInCurrentPose(
                graphics,
                font,
                stack.getTooltipLines(tooltipContext, minecraft.player, tooltipFlag),
                mouseX,
                mouseY,
                scale,
                originX,
                originY);
    }

    private static ClientTooltipPositioner boundedTooltipPositioner(GuiGraphics graphics, float scale, float originX, float originY) {
        float safeScale = Math.max(scale, 0.001F);
        int minX = Mth.ceil(-originX / safeScale);
        int minY = Mth.ceil(-originY / safeScale);
        int maxX = Mth.floor((graphics.guiWidth() - originX) / safeScale);
        int maxY = Mth.floor((graphics.guiHeight() - originY) / safeScale);
        return new BoundedTooltipPositioner(minX, minY, maxX, maxY);
    }

    private record BoundedTooltipPositioner(int minX, int minY, int maxX, int maxY) implements ClientTooltipPositioner {
        private static final int OFFSET_X = 12;
        private static final int OFFSET_Y = -12;
        private static final int EDGE_MARGIN = 4;

        @Override
        public Vector2ic positionTooltip(int screenWidth, int screenHeight, int mouseX, int mouseY, int tooltipWidth, int tooltipHeight) {
            int left = mouseX + OFFSET_X;
            int top = mouseY + OFFSET_Y;
            int rightLimit = this.maxX - tooltipWidth - EDGE_MARGIN;
            int bottomLimit = this.maxY - tooltipHeight - EDGE_MARGIN;
            if (left > rightLimit) {
                left = mouseX - tooltipWidth - OFFSET_X;
            }
            if (top > bottomLimit) {
                top = mouseY - tooltipHeight - OFFSET_Y;
            }
            left = Mth.clamp(left, this.minX + EDGE_MARGIN, Math.max(this.minX + EDGE_MARGIN, rightLimit));
            top = Mth.clamp(top, this.minY + EDGE_MARGIN, Math.max(this.minY + EDGE_MARGIN, bottomLimit));
            return new Vector2i(left, top);
        }
    }
}
