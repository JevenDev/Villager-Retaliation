package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.network.SettlementManagementActionPayload;
import com.jvn.villagerretaliation.network.SettlementManagementActionResultPayload;
import com.jvn.villagerretaliation.network.SettlementManagementPayload;
import com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page;
import com.jvn.villagerretaliation.network.VillageOverviewPayload.Presence;
import com.jvn.villagerretaliation.settlement.SettlementAreaType;
import com.jvn.villagerretaliation.settlement.SettlementViewAction;
import com.jvn.villagerretaliation.settlement.SettlementWorkerSetting;
import com.jvn.villagerretaliation.settlement.SettlementWorkerSettings;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
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
        var unknownSupply = new SettlementManagementPayload.Supply(UUID.randomUUID(), "Bread", BlockPos.ZERO,
                null, area, "Bakery storage", new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD),
                -1L, -1L, 12, "unloaded");
        require(SettlementSupplyView.detail(unknownSupply).getString().equals("Stored Unknown | Reserved Unknown | Incoming Unknown | Target 12 | Short Unknown"),
                "unavailable observations never render as zero stock or a definite shortage");
        var observedSupply = new SettlementManagementPayload.Supply(unknownSupply.id(), unknownSupply.name(), unknownSupply.position(),
                null, area, unknownSupply.storageName(), unknownSupply.item(), 5L, 2L, 3L, 12, "available");
        require(SettlementSupplyView.detail(observedSupply).getString().equals("Stored 5 | Reserved 2 | Incoming 3 | Target 12 | Short 4"),
                "updated supply observations display stored and incoming amounts separately");
        verifySelectionActions();
        verifyProductionTracking();
        verifyTransferTracking();
    }

    private static void verifySelectionActions() {
        var depot = new SettlementManagementPayload.Area(UUID.randomUUID(), "Depot", SettlementAreaType.DEPOT,
                BlockPos.ZERO, BlockPos.ZERO.above(), 5, true, 0, 0, 1);
        var active = selectionResident(depot, "idle", null, "");
        var paused = selectionResident(depot, "paused", null, "");
        require(SettlementViewUi.actionEligible(SettlementViewAction.PAUSE, active, depot)
                && !SettlementViewUi.actionEligible(SettlementViewAction.ENABLE, active, depot),
                "enabled workers only offer Pause work");
        require(SettlementViewUi.actionEligible(SettlementViewAction.ENABLE, paused, depot)
                && !SettlementViewUi.actionEligible(SettlementViewAction.PAUSE, paused, depot),
                "paused workers only offer Enable work");
        require(!SettlementViewUi.actionEligible(SettlementViewAction.MOVE, active, depot)
                && !SettlementViewUi.actionEligible(SettlementViewAction.TEMPORARY_TASK, active, depot),
                "couriers do not offer guard orders or incompatible temporary tasks");
        var areaPaused = selectionResident(depot, "area_paused", null, "");
        require(SettlementViewUi.actionEligible(SettlementViewAction.PAUSE, areaPaused, depot),
                "a paused workplace does not imply that its worker is individually paused");
        var state = new CompoundTag();
        state.putBoolean("Enabled", true);
        var settings = SettlementWorkerSettings.capture(HiredVillagerRole.COURIER, state).with(SettlementWorkerSetting.ENABLED, 0);
        require(SettlementViewUi.actionEligible(SettlementViewAction.ENABLE,
                selectionResident(depot, "awaiting_arrival", settings, ""), depot),
                "detailed worker settings take precedence over the work status summary");
        require(!SettlementViewUi.actionEligible(SettlementViewAction.ASSIGN,
                selectionResident(depot, "personal_commitment", null, ""), depot),
                "personally employed residents do not offer settlement work commands");
        var forestry = new SettlementManagementPayload.Area(UUID.randomUUID(), "Forestry", SettlementAreaType.FORESTRY,
                BlockPos.ZERO, BlockPos.ZERO.above(), 5, true, 0, 0, 1);
        require(!SettlementViewUi.actionEligible(SettlementViewAction.CANCEL_TASKS,
                selectionResident(forestry, "idle", null, ""), forestry)
                && SettlementViewUi.actionEligible(SettlementViewAction.CANCEL_TASKS,
                selectionResident(forestry, "idle", null, "Tree | 1 queued"), forestry),
                "temporary task cancellation is shown only when there is a queue to cancel");
        var unloaded = new SettlementManagementPayload.Resident(UUID.randomUUID(), "Worker", ResourceLocation.withDefaultNamespace("none"),
                false, Presence.UNLOADED, BlockPos.ZERO, forestry.id(), 20, 20, "idle", List.of(HiredVillagerRole.LOGGING),
                null, null, null, "", false, false, 0, "Tree | 1 queued");
        require(!SettlementViewUi.actionEligible(SettlementViewAction.TEMPORARY_TASK, unloaded, forestry)
                && !SettlementViewUi.actionEligible(SettlementViewAction.CANCEL_TASKS, unloaded, forestry)
                && SettlementViewUi.actionEligible(SettlementViewAction.PAUSE, unloaded, forestry),
                "unloaded workers keep saved-state work controls but cannot accept live temporary-task commands");
        var guardPost = new SettlementManagementPayload.Area(UUID.randomUUID(), "Guard post", SettlementAreaType.GUARD_POST,
                BlockPos.ZERO, BlockPos.ZERO.above(), 5, true, 0, 0, 1);
        require(!SettlementViewUi.actionEligible(SettlementViewAction.RETURN,
                selectionResident(guardPost, "idle", null, ""), guardPost)
                && SettlementViewUi.actionEligible(SettlementViewAction.RETURN,
                selectionResident(guardPost, "idle", null, "Temporary move"), guardPost),
                "returning to assignment is shown only for a temporary guard command");
        var group = List.of(active, paused);
        for (var action : List.of(SettlementViewAction.ENABLE, SettlementViewAction.PAUSE)) {
            long eligible = group.stream().filter(row -> SettlementViewUi.actionEligible(action, row, depot)).count();
            require(eligible == 1 && SettlementViewUi.actionLabel(action.label(), eligible, group.size()).getString()
                    .equals(action.label() + " (1/2)"), "mixed selections keep accurate work toggle counts");
        }
        require(SettlementViewUi.actionLabel("Assign to area", 1, 1).getString().equals("Assign to area")
                && SettlementViewUi.actionLabel("Depot", 1, 1).getString().equals("Depot"),
                "single selections omit counts in the action menu and workplace picker");
    }

    private static SettlementManagementPayload.Resident selectionResident(SettlementManagementPayload.Area area, String status,
                                                                          SettlementWorkerSettings settings, String temporary) {
        return new SettlementManagementPayload.Resident(UUID.randomUUID(), "Worker", ResourceLocation.withDefaultNamespace("none"),
                false, Presence.HERE, BlockPos.ZERO, area.id(), 20, 20, status, List.of(area.kind().role()),
                null, settings, null, "", false, false, 0, temporary);
    }

    private static void verifyProductionTracking() {
        var target = new com.jvn.villagerretaliation.network.ProductionTarget(null, UUID.randomUUID());
        var state = new com.jvn.villagerretaliation.client.inventory.ProductionClientState(target);
        var first = state.poll(0L);
        require(first != null && !state.editable(), "production controls wait for their authorized snapshot");
        var snapshot = new com.jvn.villagerretaliation.network.ProductionSnapshotPayload(ResourceLocation.withDefaultNamespace("overworld"),
                target, first.request(), false, com.jvn.villagerretaliation.interaction.HiredVillagerRole.CRAFTSMAN,
                "Personal hire", true, "", "", null, 0, List.of(), List.of(), java.util.Map.of());
        require(state.accept(snapshot, null) && state.editable(), "an authorized snapshot enables private production");
        var refresh = state.poll(1_000L);
        require(refresh != null && state.editable(), "a background refresh leaves the current controls usable");
        var recipe = com.jvn.villagerretaliation.interaction.work.production.HiredProductionRecipe.exact(
                ResourceLocation.withDefaultNamespace("stick"), java.util.Map.of());
        var command = new com.jvn.villagerretaliation.network.ProductionRequestPayload.Append(recipe,
                com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder.Mode.BATCH, 4, null);
        var edit = state.request(command, 1_050L);
        require(edit != null && state.busy(), "a mutation supersedes an older refresh and waits for acknowledgement");
        require(!state.accept(snapshot, null) && state.busy(), "a stale reply cannot release a pending action");
        require(state.request(command, 1_060L) == null, "an unanswered action cannot be submitted twice");
        require(state.poll(11_049L) == null && state.busy(), "actions retain their full acknowledgement window");
        var recovery = state.poll(11_050L);
        require(recovery != null && recovery.command() instanceof com.jvn.villagerretaliation.network.ProductionRequestPayload.View,
                "a timeout refreshes authoritative state without replaying the mutation");
        var reply = new com.jvn.villagerretaliation.network.ProductionSnapshotPayload(snapshot.dimension(), target, edit.request(), true,
                snapshot.role(), snapshot.title(), true, "", "", null, 0, List.of(), List.of(), java.util.Map.of());
        require(!state.accept(reply, null), "a late action reply cannot replace the newer recovery snapshot");
    }

    private static void verifyTransferTracking() {
        var target = new com.jvn.villagerretaliation.network.ProductionTarget(null, UUID.randomUUID());
        var state = new com.jvn.villagerretaliation.client.inventory.TransferClientState(target);
        var first = state.poll(0L);
        require(first != null && !state.editable(), "transfer controls require an authorized snapshot");
        var snapshot = new com.jvn.villagerretaliation.network.TransferSnapshotPayload(ResourceLocation.withDefaultNamespace("overworld"),
                target, first.request(), false, "Personal courier", true, "", "", null, List.of(), List.of());
        require(state.accept(snapshot, null) && state.editable(), "the courier owner can edit transfers");
        var refresh = state.poll(1_000L);
        require(refresh != null && state.editable(), "refreshing keeps authorized controls usable");
        var command = new com.jvn.villagerretaliation.network.TransferRequestPayload.Append(
                new com.jvn.villagerretaliation.interaction.work.courier.HiredTransferQueue.Endpoint(BlockPos.ZERO, null),
                new com.jvn.villagerretaliation.interaction.work.courier.HiredTransferQueue.Endpoint(BlockPos.ZERO.east(), null),
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT), 4);
        var edit = state.request(command, 1_050L);
        require(edit != null && state.busy(), "a transfer edit waits for its acknowledgement");
        require(!state.accept(snapshot, null) && state.busy(), "stale snapshots cannot finish a newer edit");
        require(state.request(command, 1_060L) == null, "an unanswered transfer cannot be submitted twice");
        require(state.poll(11_049L) == null && state.busy(), "transfers retain the complete acknowledgement window");
        var recovery = state.poll(11_050L);
        require(recovery != null && recovery.command() instanceof com.jvn.villagerretaliation.network.TransferRequestPayload.View,
                "a timeout refreshes without replaying the shipment");
        var late = new com.jvn.villagerretaliation.network.TransferSnapshotPayload(snapshot.dimension(), target, edit.request(), true,
                snapshot.title(), true, "", "", null, List.of(), List.of());
        require(!state.accept(late, null), "a late shipment acknowledgement cannot replace the recovery snapshot");
        var revoked = new com.jvn.villagerretaliation.network.TransferSnapshotPayload(snapshot.dimension(), target, recovery.request(), false,
                snapshot.title(), false, "Permission changed", "", null, List.of(), List.of());
        require(state.accept(revoked, null) && !state.editable(), "revoked authority disables the transfer controls");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
