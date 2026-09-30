package com.jvn.villagerretaliation.client.settlement;

import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;

final class SettlementConfirmScreen extends ConfirmScreen {
    private final SettlementManagementScreen parent;
    private final Component confirmationMessage;

    SettlementConfirmScreen(SettlementManagementScreen parent, Component title, Component message, Runnable accepted) {
        super(result -> {
            net.minecraft.client.Minecraft.getInstance().setScreen(parent);
            if (result) {
                accepted.run();
            }
        }, title, message);
        this.parent = parent;
        this.confirmationMessage = message;
    }

    SettlementManagementScreen parent() {
        return this.parent;
    }

    @Override
    public void renderBackground(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var lines = this.font.split(this.confirmationMessage, this.width - 50);
        int messageHeight = lines.size() * this.font.lineHeight;
        int titleTop = Math.clamp((this.height - messageHeight) / 2 - 29, 10, 80);
        int panelWidth = Math.min(this.width - 16, Math.max(310, Math.max(this.font.width(this.title),
                lines.stream().mapToInt(this.font::width).max().orElse(0))));
        int bottom = children().stream().filter(net.minecraft.client.gui.components.Button.class::isInstance)
                .map(net.minecraft.client.gui.components.Button.class::cast).mapToInt(net.minecraft.client.gui.components.Button::getBottom)
                .max().orElse(titleTop + messageHeight + 20);
        if (!SettlementViewClient.renderPanelBackground(graphics, (this.width - panelWidth) / 2,
                titleTop, panelWidth, bottom - titleTop)) {
            super.renderBackground(graphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
