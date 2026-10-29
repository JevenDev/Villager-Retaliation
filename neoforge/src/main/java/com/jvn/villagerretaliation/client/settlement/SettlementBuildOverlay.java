package com.jvn.villagerretaliation.client.settlement;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

final class SettlementBuildOverlay {
    record Bounds(int left, int top, int width, int height) {
        boolean contains(double x, double y) {
            return x >= this.left - 4 && x < this.left + this.width + 4
                    && y >= this.top - 4 && y < this.top + this.height + 4;
        }
    }

    private List<FormattedCharSequence> lines = List.of();
    private Bounds hint = new Bounds(6, 0, 0, 0);
    private Bounds controls;
    private boolean clipped;
    private boolean hintsVisible;
    private Bounds toggle = new Bounds(6, 0, 18, 18);
    private int footerTop;
    private List<Component> summary = List.of();
    private List<FormattedCharSequence> summaryLines = List.of();
    private List<AbstractWidget> actions = List.of();
    private Bounds summaryBounds;

    void summary(List<Component> summary, List<AbstractWidget> actions) { this.summary = summary; this.actions = actions; }
    List<Component> summary() { return this.summary; }

    Bounds hint() { return this.hint; }
    Bounds controls() { return this.controls; }
    Bounds toggle() { return this.toggle; }
    int footerTop() { return this.footerTop; }
    boolean hintsVisible() { return this.hintsVisible; }

    void hint(Font font, Component text, int width, int height, boolean visible) {
        this.hintsVisible = visible;
        this.toggle = new Bounds(6, height - 24, 18, 18);
        int left = this.toggle.left() + this.toggle.width() + 10;
        var wrapped = visible ? font.split(text, Math.max(40, Math.min(440, width - left - 8))) : List.<FormattedCharSequence>of();
        int limit = Math.max(1, Math.min(3, height / 3 / (font.lineHeight + 1)));
        int count = Math.min(wrapped.size(), limit);
        this.lines = wrapped.subList(0, count);
        this.clipped = count < wrapped.size();
        int contentHeight = count * (font.lineHeight + 1);
        this.hint = new Bounds(left, height - 6 - contentHeight,
                this.lines.stream().mapToInt(font::width).max().orElse(0), contentHeight);
        this.footerTop = height - 6 - Math.max(this.toggle.height(), visible ? limit * (font.lineHeight + 1) : 0);
    }

    void layout(Font font, List<AbstractWidget> widgets, int width, int height, int railWidth) {
        var visible = new ArrayList<>(widgets.stream().filter(widget -> widget.visible && !this.actions.contains(widget)).toList());
        visible.addAll(this.actions.stream().filter(widget -> widget.visible).toList());
        if (visible.isEmpty()) { this.controls = null; this.summaryBounds = null; return; }
        int available = Math.max(40, Math.min(560, width - railWidth * 2));
        var rows = new ArrayList<List<AbstractWidget>>();
        var row = new ArrayList<AbstractWidget>();
        int used = 0;
        boolean actionRow = false;
        for (var widget : visible) {
            if (!actionRow && this.actions.contains(widget)) {
                actionRow = true;
                if (!row.isEmpty()) { rows.add(row); row = new ArrayList<>(); used = 0; }
            }
            int size = Math.min(available, widget instanceof AbstractSliderButton ? 118 : Math.max(20, font.width(widget.getMessage()) + 12 + (widget instanceof ActionButton button ? button.shortcutWidth(font) : 0)));
            if (!row.isEmpty() && used + size > available) {
                rows.add(row);
                row = new ArrayList<>();
                used = 0;
            }
            widget.setWidth(size);
            row.add(widget);
            used += size + 4;
        }
        rows.add(row);
        int contentWidth = rows.stream().mapToInt(SettlementBuildOverlay::rowWidth).max().orElse(0);
        int contentHeight = rows.size() * 22 - 4;
        int bottom = this.footerTop > 0 ? this.footerTop - 12 : height - 40;
        var summaryLines = new ArrayList<FormattedCharSequence>();
        for (var line : this.summary) summaryLines.addAll(font.split(line, available));
        int maxLines = Math.max(0, (bottom - contentHeight - SettlementViewLayout.HEADER_HEIGHT - 16) / (font.lineHeight + 1));
        this.summaryLines = summaryLines.subList(0, Math.min(Math.min(6, maxLines), summaryLines.size()));
        int summaryHeight = this.summaryLines.isEmpty() ? 0 : this.summaryLines.size() * (font.lineHeight + 1) + 6;
        contentWidth = Math.max(contentWidth, this.summaryLines.stream().mapToInt(font::width).max().orElse(0));
        int top = Math.max(SettlementViewLayout.HEADER_HEIGHT + 8, bottom - contentHeight - summaryHeight);
        this.controls = new Bounds((width - contentWidth) / 2, top, contentWidth, contentHeight + summaryHeight);
        this.summaryBounds = summaryHeight == 0 ? null : new Bounds(this.controls.left(), top, contentWidth, summaryHeight);
        top += summaryHeight;
        for (var current : rows) {
            int left = (width - rowWidth(current)) / 2;
            for (var widget : current) {
                widget.setPosition(left, top);
                widget.setHeight(18);
                left += widget.getWidth() + 4;
            }
            top += 22;
        }
    }

    private static int rowWidth(List<AbstractWidget> row) {
        return row.stream().mapToInt(AbstractWidget::getWidth).sum() + (row.size() - 1) * 4;
    }

    boolean contains(double x, double y) {
        return this.toggle.contains(x, y) || this.hintsVisible && this.hint.contains(x, y)
                || this.controls != null && this.controls.contains(x, y);
    }

    void renderControls(GuiGraphics graphics) {
        if (this.controls != null) TooltipRenderUtil.renderTooltipBackground(graphics,
                this.controls.left(), this.controls.top(), this.controls.width(), this.controls.height(), 0);
        if (this.summaryBounds != null) {
            var font = Minecraft.getInstance().font;
            int y = this.summaryBounds.top();
            for (var line : this.summaryLines) { graphics.drawString(font, line, this.summaryBounds.left(), y, 0xFFFFFF); y += font.lineHeight + 1; }
        }
    }

    void renderHint(GuiGraphics graphics, Font font, Component text, AbstractWidget toggleButton, int mouseX, int mouseY, float partialTick) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 1000);
        TooltipRenderUtil.renderTooltipBackground(graphics, this.toggle.left(), this.toggle.top(), this.toggle.width(), this.toggle.height(), 0);
        toggleButton.render(graphics, mouseX, mouseY, partialTick);
        if (this.hintsVisible) {
            TooltipRenderUtil.renderTooltipBackground(graphics, this.hint.left(), this.hint.top(), this.hint.width(), this.hint.height(), 0);
            int y = this.hint.top();
            for (var line : this.lines) {
                graphics.drawString(font, line, this.hint.left(), y, 0xFFFFFF);
                y += font.lineHeight + 1;
            }
            if (this.clipped && this.hint.contains(mouseX, mouseY)) graphics.renderTooltip(font, text, mouseX, mouseY);
        }
        if (this.summaryBounds != null && this.summaryBounds.contains(mouseX, mouseY))
            graphics.renderComponentTooltip(font, this.summary, mouseX, mouseY);
        graphics.pose().popPose();
    }

    static final class ActionButton extends Button {
        private String shortcut = "";
        ActionButton shortcut(String shortcut) { this.shortcut = shortcut; return this; }
        int shortcutWidth(Font font) { return this.shortcut.isEmpty() ? 0 : font.width(this.shortcut) + 8; }
        ActionButton(Component label, OnPress action) {
            super(0, 0, 64, 18, label, action, DEFAULT_NARRATION);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            if (this.active && isHoveredOrFocused()) graphics.fill(getX(), getY(), getRight(), getBottom(), 0x30FFFFFF);
            if (isFocused()) graphics.fill(getX() + 3, getBottom() - 2, getRight() - 3, getBottom() - 1, 0xFFFFFFFF);
            var font = Minecraft.getInstance().font;
            int color = this.active ? 0xFFFFFF : 0x777777;
            if (this.shortcut.isEmpty()) renderScrollingString(graphics, font, 4, color);
            else {
                int y = getY() + (getHeight() - font.lineHeight) / 2;
                graphics.drawString(font, font.plainSubstrByWidth(getMessage().getString(), Math.max(0, getWidth() - shortcutWidth(font) - 8)), getX() + 4, y, color);
                graphics.drawString(font, this.shortcut, getRight() - font.width(this.shortcut) - 4, y, this.active ? 0xAAAAAA : 0x666666);
            }
        }
    }
}
