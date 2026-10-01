package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.network.SettlementManagementPayload.Issue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

final class SettlementIssueView {
    private SettlementIssueView() { }

    static boolean open(SettlementManagementScreen previous, Issue issue) {
        var town = previous.snapshot().header().id();
        Screen screen = switch (issue.kind()) {
            case POLICY -> SettlementPoliciesScreen.forPolicy(previous, town, issue.targetId());
            case WORKER_POOL -> SettlementWorkerPoolsScreen.forPool(previous, town, issue.targetId());
            default -> null;
        };
        if (screen == null) return false;
        Minecraft.getInstance().setScreen(screen);
        return true;
    }
}
