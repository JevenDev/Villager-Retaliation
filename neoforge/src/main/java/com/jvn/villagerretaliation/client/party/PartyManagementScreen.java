package com.jvn.villagerretaliation.client.party;

import com.jvn.villagerretaliation.network.PartyActionRequestPayload;
import com.jvn.villagerretaliation.network.PartyRosterSyncPayload;
import com.jvn.villagerretaliation.party.PartyPolicyState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
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
                    "villagerretaliation.party.manage.attack_with_party.tooltip",
                    roster.attackWithParty(),
                    roster);
            y = addPolicyButton(centerX, y,
                    PartyActionRequestPayload.Action.SET_DEFEND_PARTY,
                    "villagerretaliation.party.manage.defend_party",
                    "villagerretaliation.party.manage.defend_party.tooltip",
                    roster.defendParty(),
                    roster);
            y = addBooleanPolicyButton(centerX, y,
                    PartyActionRequestPayload.Action.SET_SHARED_VILLAGER_INVENTORIES,
                    "villagerretaliation.party.manage.shared_inventories",
                    "villagerretaliation.party.manage.shared_inventories.tooltip",
                    roster.sharedVillagerInventories(),
                    roster);
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
            String tooltipKey,
            PartyPolicyState state,
            PartyRosterSyncPayload roster) {
        PartyPolicyState[] current = {state};
        addRenderableWidget(Button.builder(
                policyLabel(labelKey, state),
                button -> {
                    boolean enabled = current[0] != PartyPolicyState.ON;
                    current[0] = enabled ? PartyPolicyState.ON : PartyPolicyState.OFF;
                    button.setMessage(policyLabel(labelKey, current[0]));
                    button.setTooltip(policyTooltip(tooltipKey, current[0], roster, action));
                    PacketDistributor.sendToServer(new PartyActionRequestPayload(action, null, null, enabled));
                })
                .bounds(centerX - 100, y, 200, 20)
                .tooltip(policyTooltip(tooltipKey, state, roster, action))
                .build());
        return y + 24;
    }

    private int addBooleanPolicyButton(
            int centerX,
            int y,
            PartyActionRequestPayload.Action action,
            String labelKey,
            String tooltipKey,
            boolean enabled,
            PartyRosterSyncPayload roster) {
        return addPolicyButton(
                centerX,
                y,
                action,
                labelKey,
                tooltipKey,
                enabled ? PartyPolicyState.ON : PartyPolicyState.OFF,
                roster);
    }

    private static Component policyLabel(String labelKey, PartyPolicyState state) {
        Component value = state == PartyPolicyState.CUSTOM
                ? Component.translatable("villagerretaliation.party.manage.custom")
                : Component.translatable(state == PartyPolicyState.ON ? "options.on" : "options.off");
        return Component.translatable(
                "villagerretaliation.party.manage.setting",
                Component.translatable(labelKey),
                value);
    }

    private static Tooltip policyTooltip(
            String tooltipKey,
            PartyPolicyState state,
            PartyRosterSyncPayload roster,
            PartyActionRequestPayload.Action action) {
        Component text = Component.translatable(tooltipKey);
        if (state != PartyPolicyState.CUSTOM) {
            return Tooltip.create(text);
        }
        String enabled = villagerNames(roster, action, true);
        String disabled = villagerNames(roster, action, false);
        return Tooltip.create(text.copy()
                .append("\n")
                .append(Component.translatable(
                        "villagerretaliation.party.manage.custom.tooltip",
                        enabled,
                        disabled)));
    }

    private static String villagerNames(
            PartyRosterSyncPayload roster,
            PartyActionRequestPayload.Action action,
            boolean enabled) {
        return String.join(", ", roster.villagers().stream()
                .filter(villager -> policyEnabled(villager, action) == enabled)
                .map(PartyManagementScreen::villagerName)
                .toList());
    }

    private static boolean policyEnabled(
            PartyRosterSyncPayload.VillagerEntry villager,
            PartyActionRequestPayload.Action action) {
        return action == PartyActionRequestPayload.Action.SET_ATTACK_WITH_PARTY
                ? villager.attackWithParty()
                : villager.defendParty();
    }

    private static String villagerName(PartyRosterSyncPayload.VillagerEntry villager) {
        return villager.name().isBlank()
                ? Component.translatable("entity.minecraft.villager").getString()
                : villager.name();
    }
}
