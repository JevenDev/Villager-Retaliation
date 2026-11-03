package com.jvn.villagerretaliation.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

public final class RouteButton extends Button {
    public RouteButton(Component label, int x, int y, int width, int height, Runnable action) {
        super(x, y, width, height, label, ignored -> action.run(), DEFAULT_NARRATION);
        setTooltip(Tooltip.create(label));
    }

    public static void icon(GuiGraphics graphics, int x, int y, int color) {
        graphics.fill(x + 2, y + 9, x + 8, y + 11, color);
        graphics.fill(x + 6, y + 3, x + 8, y + 11, color);
        graphics.fill(x + 6, y + 3, x + 14, y + 5, color);
        node(graphics, x, y + 8, color);
        node(graphics, x + 5, y + 2, color);
        node(graphics, x + 12, y + 2, color);
    }

    private static void node(GuiGraphics graphics, int x, int y, int color) {
        graphics.fill(x, y, x + 5, y + 5, 0xFF17121E);
        graphics.renderOutline(x, y, 5, 5, color);
    }

    @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        VillagerUi.row(graphics, getX(), getY(), getWidth(), getHeight(), isFocused(), isHoveredOrFocused() && active);
        int color = active ? 0xFFFFFFFF : 0xFF999999;
        icon(graphics, getX() + 4, getY() + (getHeight() - 16) / 2, color);
        var font = Minecraft.getInstance().font;
        graphics.drawString(font, font.plainSubstrByWidth(getMessage().getString(), getWidth() - 27),
                getX() + 25, getY() + (getHeight() - font.lineHeight) / 2, color);
    }
}
