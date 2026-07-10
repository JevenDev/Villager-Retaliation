package com.jvn.villagerretaliation.client.party;

import com.jvn.villagerretaliation.network.PartyActionRequestPayload;
import com.jvn.villagerretaliation.network.PartyRosterSyncPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

final class PartyManagementScreen extends Screen {
    private final Screen parent;

    PartyManagementScreen(Screen parent) {
        super(Component.translatable("villagerretaliation.party.manage.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        PartyRosterSyncPayload roster = PartyRosterClient.roster();
        int centerX = this.width / 2;
        int y = Math.max(36, this.height / 2 - 70);
        if (roster.active() && roster.recipientLeader()) {
            for (PartyRosterSyncPayload.PlayerEntry player : roster.players()) {
                if (player.leader()) {
                    continue;
                }
                addRenderableWidget(Button.builder(
                        Component.translatable("villagerretaliation.party.manage.remove", player.name()),
                        button -> send(PartyActionRequestPayload.Action.REMOVE_PLAYER, player.playerId()))
                        .bounds(centerX - 90, y, 180, 20)
                        .build());
                y += 24;
            }
            addRenderableWidget(Button.builder(
                    Component.translatable("villagerretaliation.party.action.disband"),
                    button -> this.minecraft.setScreen(new PartyDisbandConfirmationScreen(this)))
                    .bounds(centerX - 90, y + 6, 180, 20)
                    .build());
            y += 30;
        } else if (roster.active()) {
            addRenderableWidget(Button.builder(
                    Component.translatable("villagerretaliation.party.action.leave"),
                    button -> send(PartyActionRequestPayload.Action.LEAVE_PARTY, null))
                    .bounds(centerX - 90, y, 180, 20)
                    .build());
            y += 24;
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
                .bounds(centerX - 50, y + 14, 100, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 18, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    private void send(PartyActionRequestPayload.Action action, java.util.UUID targetId) {
        PacketDistributor.sendToServer(new PartyActionRequestPayload(action, targetId, null));
        if (action == PartyActionRequestPayload.Action.LEAVE_PARTY
                || action == PartyActionRequestPayload.Action.DISBAND_PARTY) {
            onClose();
        }
    }
}
