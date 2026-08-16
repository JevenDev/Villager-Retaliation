package com.jvn.villagerretaliation.client.party;

import com.jvn.villagerretaliation.network.PartyActionRequestPayload;
import com.jvn.villagerretaliation.network.PartyInvitationSyncPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

final class PartyInvitationScreen extends Screen {
    private final Screen parent;
    private final PartyInvitationSyncPayload invitation;
    private boolean responded;

    PartyInvitationScreen(Screen parent, PartyInvitationSyncPayload invitation) {
        super(Component.translatable("villagerretaliation.party.invitation.title"));
        this.parent = parent;
        this.invitation = invitation;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int buttonY = this.height / 2 + 18;
        addRenderableWidget(Button.builder(
                Component.translatable("villagerretaliation.party.invitation.accept"),
                button -> respond(PartyActionRequestPayload.Action.ACCEPT_INVITATION))
                .bounds(centerX - 104, buttonY, 100, 20)
                .build());
        addRenderableWidget(Button.builder(
                Component.translatable("villagerretaliation.party.invitation.decline"),
                button -> respond(PartyActionRequestPayload.Action.DECLINE_INVITATION))
                .bounds(centerX + 4, buttonY, 100, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 35, 0xFFFFFF);
        graphics.drawCenteredString(
                this.font,
                Component.translatable("villagerretaliation.party.invitation.prompt", this.invitation.inviterName()),
                this.width / 2,
                this.height / 2 - 10,
                0xDDDDDD);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (!this.responded) {
            respond(PartyActionRequestPayload.Action.DECLINE_INVITATION);
        }
    }

    private void respond(PartyActionRequestPayload.Action action) {
        if (this.responded) {
            return;
        }
        this.responded = true;
        PacketDistributor.sendToServer(new PartyActionRequestPayload(action, null, this.invitation.invitationId()));
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }
}
