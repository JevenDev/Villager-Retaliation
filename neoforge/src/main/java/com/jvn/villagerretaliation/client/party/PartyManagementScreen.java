package com.jvn.villagerretaliation.client.party;

import com.jvn.villagerretaliation.network.PartyActionRequestPayload;
import com.jvn.villagerretaliation.network.PartyRosterSyncPayload;
import com.jvn.villagerretaliation.party.PartyAttackMode;
import com.jvn.villagerretaliation.party.PartyAttackModeState;
import com.jvn.villagerretaliation.party.PartyCombatMode;
import com.jvn.villagerretaliation.party.PartyCombatModeState;
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
        int y = 20;
        if (roster.active() && roster.recipientLeader()) {
            y = addCombatModeButton(centerX, y, roster.combatMode());
            y = addAttackModeButton(centerX, y, roster.attackMode());
            y = addBooleanPolicyButton(centerX, y,
                    PartyActionRequestPayload.Action.SET_SHARED_VILLAGER_INVENTORIES,
                    "villagerretaliation.party.manage.shared_inventories",
                    "villagerretaliation.party.manage.shared_inventories.tooltip",
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
                y += 20;
            }
            addRenderableWidget(Button.builder(
                    Component.translatable("villagerretaliation.party.action.disband"),
                    button -> this.minecraft.setScreen(new PartyDisbandConfirmationScreen(this)))
                    .bounds(centerX - 90, y, 180, 20)
                    .build());
            y += 22;
        } else if (roster.active()) {
            addRenderableWidget(Button.builder(
                    Component.translatable("villagerretaliation.party.action.leave"),
                    button -> send(PartyActionRequestPayload.Action.LEAVE_PARTY, null))
                    .bounds(centerX - 90, y, 180, 20)
                    .build());
            y += 20;
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
                .bounds(centerX - 50, y + 2, 100, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 7, 0xFFFFFF);
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

    private int addBooleanPolicyButton(
            int centerX,
            int y,
            PartyActionRequestPayload.Action action,
            String labelKey,
            String tooltipKey,
            boolean enabled) {
        boolean[] current = {enabled};
        addRenderableWidget(Button.builder(
                booleanPolicyLabel(labelKey, enabled),
                button -> {
                    current[0] = !current[0];
                    button.setMessage(booleanPolicyLabel(labelKey, current[0]));
                    PacketDistributor.sendToServer(new PartyActionRequestPayload(action, null, null, current[0]));
                })
                .bounds(centerX - 100, y, 200, 20)
                .tooltip(Tooltip.create(Component.translatable(tooltipKey)))
                .build());
        return y + 20;
    }

    private int addCombatModeButton(int centerX, int y, PartyCombatModeState state) {
        PartyCombatModeState[] current = {state};
        addRenderableWidget(Button.builder(
                combatModeLabel(state),
                button -> {
                    PartyCombatMode next = current[0].nextMode();
                    current[0] = PartyCombatModeState.of(next);
                    button.setMessage(combatModeLabel(current[0]));
                    PacketDistributor.sendToServer(new PartyActionRequestPayload(
                            PartyActionRequestPayload.Action.SET_COMBAT_MODE,
                            null,
                            null,
                            false,
                            null,
                            next));
                })
                .bounds(centerX - 100, y, 200, 20)
                .tooltip(Tooltip.create(Component.translatable(
                        state == PartyCombatModeState.CUSTOM
                                ? "villagerretaliation.party.manage.combat_mode.custom.tooltip"
                                : "villagerretaliation.party.manage.combat_mode.tooltip")))
                .build());
        return y + 20;
    }

    private int addAttackModeButton(int centerX, int y, PartyAttackModeState state) {
        PartyAttackModeState[] current = {state};
        addRenderableWidget(Button.builder(
                attackModeLabel(state),
                button -> {
                    PartyAttackMode next = current[0].nextMode();
                    current[0] = PartyAttackModeState.of(next);
                    button.setMessage(attackModeLabel(current[0]));
                    PacketDistributor.sendToServer(new PartyActionRequestPayload(
                            PartyActionRequestPayload.Action.SET_ATTACK_MODE,
                            null,
                            null,
                            false,
                            next,
                            null));
                })
                .bounds(centerX - 100, y, 200, 20)
                .tooltip(Tooltip.create(Component.translatable(
                        state == PartyAttackModeState.CUSTOM
                                ? "villagerretaliation.party.manage.attack_mode.custom.tooltip"
                                : "villagerretaliation.party.manage.attack_mode.tooltip")))
                .build());
        return y + 20;
    }

    private static Component booleanPolicyLabel(String labelKey, boolean enabled) {
        return Component.translatable(
                "villagerretaliation.party.manage.setting",
                Component.translatable(labelKey),
                Component.translatable(enabled ? "options.on" : "options.off"));
    }

    private static Component combatModeLabel(PartyCombatModeState state) {
        Component value = Component.translatable(
                "villagerretaliation.party.combat_mode." + state.name().toLowerCase(java.util.Locale.ROOT));
        return Component.translatable(
                "villagerretaliation.party.manage.setting",
                Component.translatable("villagerretaliation.party.manage.combat_mode"),
                value);
    }

    private static Component attackModeLabel(PartyAttackModeState state) {
        Component value = Component.translatable(
                "villagerretaliation.party.attack_mode." + state.name().toLowerCase(java.util.Locale.ROOT));
        return Component.translatable(
                "villagerretaliation.party.manage.setting",
                Component.translatable("villagerretaliation.party.manage.attack_mode"),
                value);
    }

}
