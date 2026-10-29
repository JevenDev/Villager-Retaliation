package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.network.SettlementManagementPayload.Issue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

final class SettlementIssueView {
    private SettlementIssueView() { }

    static boolean storageIssue(Issue issue) {
        String cause = issue.cause();
        return issue.kind() == com.jvn.villagerretaliation.network.SettlementManagementPayload.IssueKind.SUPPLY
                || cause.contains("storage") || cause.contains("supply") || cause.contains("materials")
                || cause.startsWith("missing_") || cause.equals("output_full") || cause.equals("resident_hungry");
    }

    static String detailsLabel(Issue issue) {
        if (issue.target() == com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page.DEMAND) return "Resolve supply shortage";
        return switch (issue.kind()) {
            case PROJECT -> "Project details";
            case POLICY -> "Review policy";
            case WORKER_POOL -> "Review worker pool";
            case TRADE -> "Review trade order";
            default -> "Open issue details";
        };
    }

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
