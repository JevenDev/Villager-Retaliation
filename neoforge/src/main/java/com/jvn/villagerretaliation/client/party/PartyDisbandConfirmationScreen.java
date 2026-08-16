package com.jvn.villagerretaliation.client.party;

import com.jvn.villagerretaliation.network.PartyActionRequestPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

final class PartyDisbandConfirmationScreen extends Screen {
    private final Screen parent;

    PartyDisbandConfirmationScreen(Screen parent) {
        super(Component.translatable("villagerretaliation.party.disband.confirmation.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int y = this.height / 2 + 30;
        addRenderableWidget(Button.builder(
                Component.translatable("villagerretaliation.party.action.disband_confirm"),
                button -> confirm())
                .bounds(centerX - 104, y, 100, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
                .bounds(centerX + 4, y, 100, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 45, 0xFFFFFF);
        graphics.drawCenteredString(this.font,
                Component.translatable("villagerretaliation.party.disband.confirmation.players"),
                this.width / 2, this.height / 2 - 18, 0xDDDDDD);
        graphics.drawCenteredString(this.font,
                Component.translatable("villagerretaliation.party.disband.confirmation.villagers"),
                this.width / 2, this.height / 2 - 6, 0xDDDDDD);
        graphics.drawCenteredString(this.font,
                Component.translatable("villagerretaliation.party.disband.confirmation.refund"),
                this.width / 2, this.height / 2 + 6, 0xCC7777);
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
