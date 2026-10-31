package com.jvn.villagerretaliation.client.ui;

import com.jvn.toucanlib.client.ui.ToucanWindowScreen;
import com.jvn.villagerretaliation.client.settlement.SettlementViewClient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public abstract class VillagerWindowScreen extends ToucanWindowScreen {
    protected VillagerWindowScreen(Component title) { super(title); }

    @Override
    protected void initWindow(int preferredWidth, int preferredHeight, boolean compact) {
        var layout = SettlementViewClient.panelLayout(this.width, this.height,
                Math.min(preferredWidth, this.width - 24), Math.min(preferredHeight, this.height - 24), compact);
        positionWindow(layout.left(), layout.top(), layout.width(), layout.height());
    }

    @Override
    protected int minimumTop() {
        return SettlementViewClient.connectedPanel() && this.height >= 280 ? 46 : 8;
    }

    @Override
    protected boolean renderWindowBackdrop(GuiGraphics graphics) {
        return SettlementViewClient.renderPanelBackground(graphics, this.left, this.top, this.panelWidth, this.panelHeight);
    }

    @Override
    protected boolean renderVanillaBackground() { return !SettlementViewClient.basicUi(); }

    @Override
    protected void renderWindowFrame(GuiGraphics graphics, int left, int top, int width, int height) {
        VillagerUi.frame(graphics, left, top, width, height);
    }
}
