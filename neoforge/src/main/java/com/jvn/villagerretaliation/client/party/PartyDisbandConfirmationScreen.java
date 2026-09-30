package com.jvn.villagerretaliation.client.party;

import com.jvn.villagerretaliation.client.ui.VillagerUi;
import com.jvn.villagerretaliation.client.ui.VillagerWindowScreen;
import com.jvn.villagerretaliation.network.PartyActionRequestPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

final class PartyDisbandConfirmationScreen extends VillagerWindowScreen {
    private final Screen parent;

    PartyDisbandConfirmationScreen(Screen parent) {
        super(Component.translatable("villagerretaliation.party.disband.confirmation.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        initWindow(280, 124);
        int centerX = this.left + this.panelWidth / 2;
        int y = this.top + 100;
        addRenderableWidget(VillagerUi.buttonBuilder(
                Component.translatable("villagerretaliation.party.action.disband_confirm"),
                button -> confirm())
                .bounds(centerX - 104, y, 100, 20)
                .build());
        addRenderableWidget(VillagerUi.buttonBuilder(Component.translatable("gui.cancel"), button -> onClose())
                .bounds(centerX + 4, y, 100, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.left + this.panelWidth / 2, this.top + 2, 0xFFFFFF);
        graphics.drawCenteredString(this.font,
                Component.translatable("villagerretaliation.party.disband.confirmation.players"),
                this.left + this.panelWidth / 2, this.top + 28, 0xDDDDDD);
        graphics.drawCenteredString(this.font,
                Component.translatable("villagerretaliation.party.disband.confirmation.villagers"),
                this.left + this.panelWidth / 2, this.top + 40, 0xDDDDDD);
        graphics.drawCenteredString(this.font,
                Component.translatable("villagerretaliation.party.disband.confirmation.refund"),
                this.left + this.panelWidth / 2, this.top + 52, 0xCC7777);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    private void confirm() {
        PacketDistributor.sendToServer(new PartyActionRequestPayload(PartyActionRequestPayload.Action.DISBAND_PARTY));
        if (this.minecraft != null) {
            this.minecraft.setScreen(null);
        }
    }
}
