package com.jvn.villagerretaliation.client.party;

import com.jvn.villagerretaliation.client.ui.VillagerUi;
import com.jvn.villagerretaliation.client.ui.VillagerWindowScreen;
import com.jvn.villagerretaliation.network.PartyActionRequestPayload;
import com.jvn.villagerretaliation.network.PartyInvitationSyncPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

final class PartyInvitationScreen extends VillagerWindowScreen {
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
        initWindow(280, 100);
        int centerX = this.left + this.panelWidth / 2;
        int buttonY = this.top + 76;
        addRenderableWidget(VillagerUi.buttonBuilder(
                Component.translatable("villagerretaliation.party.invitation.accept"),
                button -> respond(PartyActionRequestPayload.Action.ACCEPT_INVITATION))
                .bounds(centerX - 104, buttonY, 100, 20)
                .build());
        addRenderableWidget(VillagerUi.buttonBuilder(
                Component.translatable("villagerretaliation.party.invitation.decline"),
                button -> respond(PartyActionRequestPayload.Action.DECLINE_INVITATION))
                .bounds(centerX + 4, buttonY, 100, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.left + this.panelWidth / 2, this.top + 2, 0xFFFFFF);
        graphics.drawCenteredString(
                this.font,
                Component.translatable("villagerretaliation.party.invitation.prompt", this.invitation.inviterName()),
                this.left + this.panelWidth / 2,
                this.top + 28,
                0xDDDDDD);
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
