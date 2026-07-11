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
        int y = 30;
        if (roster.active() && roster.recipientLeader()) {
            y = addPolicyButton(centerX, y,
                    PartyActionRequestPayload.Action.SET_ATTACK_WITH_PARTY,
                    "villagerretaliation.party.manage.attack_with_party",
                    roster.attackWithParty());
            y = addPolicyButton(centerX, y,
                    PartyActionRequestPayload.Action.SET_DEFEND_PARTY,
                    "villagerretaliation.party.manage.defend_party",
                    roster.defendParty());
            y = addPolicyButton(centerX, y,
                    PartyActionRequestPayload.Action.SET_SHARED_VILLAGER_INVENTORIES,
                    "villagerretaliation.party.manage.shared_inventories",
                    roster.sharedVillagerInventories());
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

    private int addPolicyButton(
            int centerX,
            int y,
            PartyActionRequestPayload.Action action,
            String labelKey,
            boolean enabled) {
        boolean[] current = {enabled};
        addRenderableWidget(Button.builder(
                policyLabel(labelKey, enabled),
                button -> {
                    current[0] = !current[0];
                    button.setMessage(policyLabel(labelKey, current[0]));
                    PacketDistributor.sendToServer(new PartyActionRequestPayload(action, null, null, current[0]));
                })
                .bounds(centerX - 100, y, 200, 20)
                .build());
        return y + 24;
    }

    private static Component policyLabel(String labelKey, boolean enabled) {
        return Component.translatable(
                "villagerretaliation.party.manage.setting",
                Component.translatable(labelKey),
                Component.translatable(enabled ? "options.on" : "options.off"));
    }
}
