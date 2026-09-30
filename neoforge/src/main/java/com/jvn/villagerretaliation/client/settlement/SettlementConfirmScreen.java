package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.client.ui.VillagerUi;
import com.jvn.villagerretaliation.client.ui.VillagerWindowScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

final class SettlementConfirmScreen extends VillagerWindowScreen {
    private final SettlementManagementScreen parent;
    private final Component confirmationMessage;
    private final Runnable accepted;

    SettlementConfirmScreen(SettlementManagementScreen parent, Component title, Component message, Runnable accepted) {
        super(title);
        this.parent = parent;
        this.confirmationMessage = message;
        this.accepted = accepted;
    }

    SettlementManagementScreen parent() { return this.parent; }

    @Override
    protected void init() {
        int width = Math.min(320, this.width - 24);
        initWindow(width, 70);
        initWindow(width, 70 + this.font.split(this.confirmationMessage, this.panelWidth).size() * this.font.lineHeight);
        int half = (this.panelWidth - 4) / 2;
        addRenderableWidget(VillagerUi.buttonBuilder(Component.translatable("gui.yes"), ignored -> {
            this.minecraft.setScreen(this.parent);
            this.accepted.run();
        }).bounds(this.left, this.top + this.panelHeight - 20, half, 20).build());
        addRenderableWidget(VillagerUi.buttonBuilder(Component.translatable("gui.no"), ignored -> onClose())
                .bounds(this.left + half + 4, this.top + this.panelHeight - 20, half, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(this.font, this.font.plainSubstrByWidth(this.title.getString(), this.panelWidth), this.left, this.top, 0xFFFFFF, false);
        graphics.drawWordWrap(this.font, this.confirmationMessage, this.left, this.top + 24, this.panelWidth, 0xDDDDDD);
    }

    @Override
    public void onClose() { this.minecraft.setScreen(this.parent); }

    @Override
    public boolean isPauseScreen() { return false; }
}
