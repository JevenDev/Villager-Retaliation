package com.jvn.villagerretaliation.client.settlement;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

final class SettlementUiWindow {
    static final int TITLE_HEIGHT = 14;
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
        this.position = new SettlementViewPreferences.WindowPosition(nextX, nextY);
        for (var widget : this.widgets) widget.setPosition(widget.getX() + dx, widget.getY() + dy);
        return new int[]{dx, dy};
    }

    void render(GuiGraphics graphics, Font font, Component title) {
        SettlementViewUi.frame(graphics, this.left, this.top, this.width, this.height);
        graphics.fill(this.left + 1, this.top + 1, this.left + this.width - 1, this.top + TITLE_HEIGHT, 0xFF383838);
        graphics.drawString(font, font.plainSubstrByWidth(title.getString(), Math.max(0, this.width - 8)),
                this.left + 4, this.top + 3, 0xFFFFFF, false);
    }

    private int clampX(int x) { return Math.clamp(x, 2, Math.max(2, this.screenWidth - this.width - 2)); }
    private int clampY(int y) {
        int minimum = SettlementViewLayout.HEADER_HEIGHT + 2;
        return Math.clamp(y, minimum, Math.max(minimum, this.screenHeight - this.height - 2));
    }
}
