package com.jvn.villagerretaliation.client.ui;

import com.jvn.villagerretaliation.client.settlement.SettlementViewClient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public abstract class VillagerWindowScreen extends Screen {
    protected int left;
    protected int top;
    protected int panelWidth;
    protected int panelHeight;
    private boolean positioned;
    private boolean draggingWindow;
    private double grabX;
    private double grabY;

    protected VillagerWindowScreen(Component title) { super(title); }

    protected void initWindow(int preferredWidth, int preferredHeight) {
        var layout = SettlementViewClient.panelLayout(this.width, this.height,
                Math.min(preferredWidth, this.width - 24), Math.min(preferredHeight, this.height - 24), true);
        this.panelWidth = layout.width();
        this.panelHeight = layout.height();
        this.left = Math.clamp(this.positioned ? this.left : layout.left(), 8, Math.max(8, this.width - this.panelWidth - 8));
        this.top = Math.clamp(this.positioned ? this.top : layout.top(), minimumTop(), Math.max(minimumTop(), this.height - this.panelHeight - 8));
        this.positioned = true;
        this.draggingWindow = false;
    }

    public ScreenRectangle windowBounds() {
        return new ScreenRectangle(this.left, this.top, this.panelWidth, this.panelHeight);
    }

    private int minimumTop() {
        return SettlementViewClient.connectedPanel() && this.height >= 280 ? 46 : 8;
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (super.mouseClicked(x, y, button)) return true;
        if (button == 0 && x >= this.left - 4 && x < this.left + this.panelWidth + 4
                && y >= this.top - 4 && y < this.top + 16) {
            this.draggingWindow = true;
            this.grabX = x - this.left;
            this.grabY = y - this.top;
            setFocused(null);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button != 0 || !this.draggingWindow) return super.mouseDragged(x, y, button, dx, dy);
        int nextLeft = Math.clamp((int) Math.round(x - this.grabX), 8, Math.max(8, this.width - this.panelWidth - 8));
        int nextTop = Math.clamp((int) Math.round(y - this.grabY), minimumTop(), Math.max(minimumTop(), this.height - this.panelHeight - 8));
        int moveX = nextLeft - this.left, moveY = nextTop - this.top;
        for (var child : children()) if (child instanceof AbstractWidget widget)
            widget.setPosition(widget.getX() + moveX, widget.getY() + moveY);
        windowMoved(moveX, moveY);
        this.left = nextLeft;
        this.top = nextTop;
        return true;
    }

    protected void windowMoved(int dx, int dy) { }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (button == 0 && this.draggingWindow) {
            this.draggingWindow = false;
            setDragging(false);
            return true;
        }
        return super.mouseReleased(x, y, button);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (mouseX >= this.left && mouseX < this.left + this.panelWidth && mouseY >= this.top - 4 && mouseY < this.top + 16
                && children().stream().noneMatch(child -> child.isMouseOver(mouseX, mouseY)))
            graphics.renderTooltip(this.font, this.title.copy().append("\nDrag the title to move this window"), mouseX, mouseY);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!SettlementViewClient.renderPanelBackground(graphics, this.left, this.top, this.panelWidth, this.panelHeight)) {
            super.renderBackground(graphics, mouseX, mouseY, partialTick);
            VillagerUi.frame(graphics, this.left - 8, this.top - 8, this.panelWidth + 16, this.panelHeight + 16);
        }
    }
}
