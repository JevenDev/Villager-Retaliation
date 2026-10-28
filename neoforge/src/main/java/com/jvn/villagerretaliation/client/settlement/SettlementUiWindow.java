package com.jvn.villagerretaliation.client.settlement;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.network.chat.Component;

final class SettlementUiWindow {
    static final int TITLE_HEIGHT = 14;
    private final int minimumWidth;
    private final int minimumHeight;
    private List<AbstractWidget> widgets = List.of();
    private SettlementViewPreferences.WindowPosition position;
    private int defaultLeft;
    private int defaultTop;
    private int left;
    private int top;
    private int width;
    private int height;
    private int screenWidth;
    private int screenHeight;
    private double grabX;
    private double grabY;
    private int resizeCorner;
    private int resizeAnchor;

    SettlementUiWindow(int minimumWidth, int minimumHeight) {
        this.minimumWidth = minimumWidth;
        this.minimumHeight = minimumHeight;
    }

    void restore(SettlementViewPreferences.WindowPosition position) { this.position = position; }

    void layout(int left, int top, int width, int height, int screenWidth, int screenHeight) {
        this.defaultLeft = left;
        this.defaultTop = top;
        this.width = width;
        this.height = height;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.left = clampX(this.position == null ? left : this.position.left());
        this.top = clampY(this.position == null ? top : this.position.top());
        this.widgets = List.of();
    }

    void bind(List<? extends AbstractWidget> widgets) {
        this.widgets = List.copyOf(widgets);
        for (var widget : this.widgets) widget.setPosition(widget.getX() + offsetX(), widget.getY() + offsetY());
    }

    int left() { return this.left; }
    int top() { return this.top; }
    int width() { return this.width; }
    int height() { return this.height; }
    int offsetX() { return this.left - this.defaultLeft; }
    int offsetY() { return this.top - this.defaultTop; }
    List<AbstractWidget> widgets() { return this.widgets; }
    SettlementViewPreferences.WindowPosition position() { return this.position; }

    boolean contains(double x, double y) {
        return x >= this.left && x < this.left + this.width && y >= this.top && y < this.top + this.height;
    }

    boolean title(double x, double y) { return contains(x, y) && y < this.top + TITLE_HEIGHT; }

    void grab(double x, double y) {
        this.grabX = x - this.left;
        this.grabY = y - this.top;
    }

    int[] drag(double x, double y) {
        int nextX = clampX((int) Math.round(x - this.grabX));
        int nextY = clampY((int) Math.round(y - this.grabY));
        int dx = nextX - this.left, dy = nextY - this.top;
        this.left = nextX;
        this.top = nextY;
        this.position = new SettlementViewPreferences.WindowPosition(nextX, nextY,
                this.position == null ? 0 : this.position.width(), this.position == null ? 0 : this.position.height());
        for (var widget : this.widgets) widget.setPosition(widget.getX() + dx, widget.getY() + dy);
        return new int[]{dx, dy};
    }

    int resizeCorner(double x, double y) {
        if (!contains(x, y) || y < this.top + this.height - 8) return 0;
        if (x < this.left + 8) return -1;
        return x >= this.left + this.width - 8 ? 1 : 0;
    }

    void grabResize(double x, double y) {
        this.resizeCorner = resizeCorner(x, y);
        this.resizeAnchor = this.resizeCorner < 0 ? this.left + this.width : this.left;
        this.grabX = x - (this.resizeCorner < 0 ? this.left : this.left + this.width);
        this.grabY = y - this.top - this.height;
    }

    boolean resize(double x, double y) {
        int edge = (int) Math.round(x - this.grabX);
        int width = clampSize(this.resizeCorner < 0 ? this.resizeAnchor - edge : edge - this.resizeAnchor,
                this.minimumWidth, this.resizeCorner < 0 ? this.resizeAnchor - 2 : this.screenWidth - this.resizeAnchor - 2);
        int height = clampSize((int) Math.round(y - this.grabY) - this.top,
                this.minimumHeight, this.screenHeight - this.top - 2);
        int left = this.resizeCorner < 0 ? this.resizeAnchor - width : this.resizeAnchor;
        if (left == this.left && width == this.width && height == this.height) return false;
        this.left = left;
        this.width = width;
        this.height = height;
        this.position = new SettlementViewPreferences.WindowPosition(left, this.top, width, height);
        return true;
    }

    static int clampSize(int size, int minimum, int maximum) {
        maximum = Math.max(1, maximum);
        return Math.clamp(size, Math.min(minimum, maximum), maximum);
    }

    void render(GuiGraphics graphics, Font font, Component title) {
        TooltipRenderUtil.renderTooltipBackground(graphics, this.left + 4, this.top + 4, this.width - 8, this.height - 8, 0);
        graphics.drawString(font, font.plainSubstrByWidth(title.getString(), Math.max(0, this.width - 8)),
                this.left + 4, this.top + 3, 0xFFFFFF, false);
    }

    void renderResizeHandles(GuiGraphics graphics, int mouseX, int mouseY) {
        int hovered = resizeCorner(mouseX, mouseY);
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);
        for (int corner : new int[]{-1, 1}) {
            int color = hovered == corner ? 0xFFFFFFFF : 0xFF888888;
            for (int offset = 0; offset < 3; offset++) {
                int x = corner < 0 ? this.left + 2 + offset * 2 : this.left + this.width - 3 - offset * 2;
                int bottom = this.top + this.height - 2;
                graphics.fill(x, bottom - 1 - offset * 2, x + 1, bottom, color);
            }
        }
        graphics.pose().popPose();
    }

    private int clampX(int x) { return Math.clamp(x, 2, Math.max(2, this.screenWidth - this.width - 2)); }
    private int clampY(int y) {
        int minimum = SettlementViewLayout.HEADER_HEIGHT + 2;
        return Math.clamp(y, minimum, Math.max(minimum, this.screenHeight - this.height - 2));
    }
}
