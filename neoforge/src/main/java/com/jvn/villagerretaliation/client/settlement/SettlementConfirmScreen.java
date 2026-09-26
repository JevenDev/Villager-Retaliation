package com.jvn.villagerretaliation.client.settlement;

import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;

final class SettlementConfirmScreen extends ConfirmScreen {
    private final SettlementManagementScreen parent;

    SettlementConfirmScreen(SettlementManagementScreen parent, Component title, Component message, Runnable accepted) {
        super(result -> {
            net.minecraft.client.Minecraft.getInstance().setScreen(parent);
            if (result) {
                accepted.run();
            }
        }, title, message);
        this.parent = parent;
    }

    SettlementManagementScreen parent() {
        return this.parent;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
