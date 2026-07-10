package com.jvn.villagerretaliation.client.party;

import com.jvn.villagerretaliation.network.OpenPlayerPartyMenuPayload;
import com.jvn.villagerretaliation.network.PartyActionRequestPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

final class PlayerPartyInteractionScreen extends Screen {
    private final Screen parent;
    private final OpenPlayerPartyMenuPayload payload;

    PlayerPartyInteractionScreen(Screen parent, OpenPlayerPartyMenuPayload payload) {
        super(Component.translatable("villagerretaliation.party.player_menu.title", payload.targetName()));
        this.parent = parent;
        this.payload = payload;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int y = this.height / 2 - 10;
        if (this.payload.canInvite()) {
            addRenderableWidget(Button.builder(
                    Component.translatable("villagerretaliation.party.action.invite"),
                    button -> send(PartyActionRequestPayload.Action.SEND_INVITATION))
                    .bounds(centerX - 75, y, 150, 20)
                    .build());
            y += 24;
        }
        if (this.payload.canRemove()) {
            addRenderableWidget(Button.builder(
                    Component.translatable("villagerretaliation.party.action.remove_player"),
                    button -> send(PartyActionRequestPayload.Action.REMOVE_PLAYER))
                    .bounds(centerX - 75, y, 150, 20)
                    .build());
            y += 24;
        }
        addRenderableWidget(Button.builder(
                Component.translatable("gui.back"),
                button -> onClose())
                .bounds(centerX - 50, y + 8, 100, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 42, 0xFFFFFF);
        if (!this.payload.canInvite() && !this.payload.canRemove()) {
            graphics.drawCenteredString(
                    this.font,
                    Component.translatable("villagerretaliation.party.player_menu.no_actions"),
                    this.width / 2,
                    this.height / 2 - 15,
                    0xAAAAAA);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    private void send(PartyActionRequestPayload.Action action) {
        PacketDistributor.sendToServer(new PartyActionRequestPayload(action, this.payload.targetId(), null));
        onClose();
    }
}
