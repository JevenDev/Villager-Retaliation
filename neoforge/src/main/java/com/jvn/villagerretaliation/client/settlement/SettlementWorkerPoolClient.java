package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.network.SettlementWorkerPoolSnapshotPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class SettlementWorkerPoolClient {
    private SettlementWorkerPoolClient() { }

    public static void accept(SettlementWorkerPoolSnapshotPayload payload) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || !minecraft.player.isAlive() || minecraft.player.isSpectator()
                || !minecraft.level.dimension().location().equals(payload.dimension())) return;
        Screen screen = minecraft.screen;
        while (!(screen instanceof SettlementWorkerPoolsScreen) && screen instanceof SettlementPolicyClient.Child child)
            screen = child.previousPolicy();
        if (screen instanceof SettlementWorkerPoolsScreen pools) pools.accept(payload);
    }

    static Component text(String key, Object... args) {
        return Component.translatable("villagerretaliation.gui.settlement.pool." + key, args);
    }

    static Component blocker(String value) {
        if (value.isEmpty()) return Component.empty();
        String key = "villagerretaliation.gui.settlement.pool.blocker." + value;
        if (net.minecraft.client.resources.language.I18n.exists(key)) return Component.translatable(key);
        return SettlementPolicyClient.blocker(value);
    }
}
