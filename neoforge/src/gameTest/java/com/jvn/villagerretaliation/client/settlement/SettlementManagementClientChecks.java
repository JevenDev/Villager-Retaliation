package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.network.SettlementManagementActionPayload;
import com.jvn.villagerretaliation.network.SettlementManagementActionResultPayload;
import com.jvn.villagerretaliation.network.SettlementManagementPayload;
import com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page;
import com.jvn.villagerretaliation.network.VillageOverviewPayload.Presence;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

final class SettlementManagementClientChecks {
    private SettlementManagementClientChecks() { }

    static void verify() {
        UUID town = UUID.randomUUID();
        var first = new SettlementManagementActionPayload(town, new SettlementManagementActionPayload.Treasury(true, 10));
        var second = new SettlementManagementActionPayload(town, new SettlementManagementActionPayload.Management(false));
        var firstResult = new SettlementManagementActionResultPayload(town, true, first.request(), "");
        var tracker = new SettlementManagementTracker();
        tracker.sent(first, 100);
        require(!tracker.accept(new SettlementManagementActionResultPayload(town, true, second.request(), "")),
                "unrelated requests cannot finish a pending management action");
        require(!tracker.accept(new SettlementManagementActionResultPayload(UUID.randomUUID(), true, first.request(), "")),
                "results from another town cannot finish a pending action");
        require(!tracker.timeout(10_099) && tracker.busy(), "controls remain pending before the deadline");
        require(tracker.timeout(10_100) && !tracker.busy() && tracker.unconfirmed(), "missing acknowledgements release controls");
        require(!tracker.timeout(20_100), "a timeout refresh happens once");
        require(tracker.accept(firstResult), "late success can refresh an unconfirmed action");
        require(!tracker.accept(firstResult), "duplicate acknowledgements are ignored");
        tracker.sent(first, 30_000);
        tracker.timeout(40_000);
        tracker.sent(second, 40_001);
        require(!tracker.accept(firstResult) && tracker.busy(), "late success cannot finish a newer action");
        require(tracker.accept(new SettlementManagementActionResultPayload(town, false, second.request(), "leader_required")),
                "a matching server rejection releases controls");
        tracker.sent(first, 50_000);
        tracker.clear();
        require(!tracker.accept(firstResult) && !tracker.busy(), "closed sessions ignore old results");
        require(!new SettlementManagementTracker().accept(firstResult), "reopened screens cannot consume earlier results");
        UUID resident = UUID.randomUUID(), area = UUID.randomUUID();
        var row = new SettlementManagementPayload.Resident(resident, "Worker", ResourceLocation.withDefaultNamespace("farmer"),
                false, Presence.UNLOADED, BlockPos.ZERO, area, 20, 20, "construction_needs_observation", List.of());
        var issue = new SettlementManagementPayload.Issue(UUID.randomUUID(), "Worker", SettlementManagementPayload.IssueKind.WORKER,
                row.status(), "", Page.RESIDENTS, resident, resident, area, true);
        var browser = new SettlementViewBrowser();
        browser.updateAttention(List.of(issue));
        browser.filter = "Needs attention";
        require(browser.rows(List.of(row)).size() == 1, "observation blockers appear in the browser attention filter");
        browser.updateAttention(List.of());
        require(browser.rows(List.of(row)).isEmpty(), "resolved issues leave the attention filter");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
