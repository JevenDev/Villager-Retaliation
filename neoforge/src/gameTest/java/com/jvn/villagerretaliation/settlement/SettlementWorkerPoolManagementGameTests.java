package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.network.SettlementWorkerPoolRequestPayload;
import com.jvn.villagerretaliation.network.SettlementWorkerPoolSnapshotPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("villagerretaliation")
@PrefixGameTestTemplate(false)
public final class SettlementWorkerPoolManagementGameTests {
    private SettlementWorkerPoolManagementGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_worker_pool_management")
    public static void viewOnlyDraftPreviewShowsDefaultsAndPinsWithoutEnrollment(GameTestHelper helper) {
        var fixture = SettlementWorkerPoolAssignmentGameTests.ready(helper, 1);
        var town = fixture.town();
        var player = viewer(helper, town, false);
        var resident = fixture.residents().getFirst();
        SettlementWorkerPoolAssignmentGameTests.staff(helper, town, resident, fixture.farm());
        town.rememberManualDuty(resident.getUUID());
        var before = town.save(helper.getLevel().registryAccess());
        var response = request(player, town, UUID.randomUUID(), -1, new SettlementWorkerPoolRequestPayload.Preview(configuration(fixture)));
        helper.assertFalse(response.applied() || response.editable(), "VIEW permits inspection without granting staffing authority");
        helper.assertTrue(response.feedback().isEmpty() && response.blocker().isEmpty(), "a valid draft can be inspected before activation");
        var farm = response.worksites().stream().filter(site -> site.id().equals(fixture.farm().id())).findFirst().orElseThrow();
        helper.assertValueEqual(farm.minimum(), 0, "new pool minimum is visible before activation");
        helper.assertValueEqual(farm.maximum(), 2, "new pool cap comes from existing desired staffing");
        helper.assertValueEqual(response.residents().getFirst().blocker(), "pool_manual_duty", "the preview preserves the manual pin explanation");
        helper.assertValueEqual(town.save(helper.getLevel().registryAccess()), before, "preview does not enroll workers or alter native assignments");
        codec(helper, response);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_worker_pool_management")
    public static void staffAuthorityAndOptimisticRevisionsControlPoolEdits(GameTestHelper helper) {
        var fixture = SettlementWorkerPoolAssignmentGameTests.ready(helper, 1);
        var town = fixture.town();
        var player = viewer(helper, town, false);
        var id = UUID.randomUUID();
        var command = new SettlementWorkerPoolRequestPayload.Save(configuration(fixture), true);
        var denied = request(player, town, id, -1, command);
        helper.assertValueEqual(denied.feedback(), "pool_permission_changed", "inspection alone cannot authorize staffing");
        helper.assertTrue(town.workerPools().isEmpty(), "rejected creation leaves no pool");
        town.setPermissions(town.leader(), player.getUUID(), Set.of(SettlementPermission.VIEW, SettlementPermission.STAFF));
        var saved = request(player, town, id, -1, command);
        helper.assertTrue(saved.applied() && saved.editable(), "STAFF can activate a pool without LOGISTICS or TRADE");
        helper.assertValueEqual(saved.selected().authority(), player.getUUID(), "execution retains the actual authorizing player");
        codec(helper, saved);
        var duplicate = request(player, town, UUID.randomUUID(), -1, command);
        helper.assertValueEqual(duplicate.feedback(), "pool_resident_enrolled", "the server rejects overlapping membership");
        var stale = request(player, town, id, -1, new SettlementWorkerPoolRequestPayload.Change(SettlementWorkerPoolRequestPayload.Action.PAUSE));
        helper.assertValueEqual(stale.feedback(), "pool_changed", "a stale editor cannot pause a newer pool");
        var paused = request(player, town, id, saved.selected().revision(),
                new SettlementWorkerPoolRequestPayload.Change(SettlementWorkerPoolRequestPayload.Action.PAUSE));
        helper.assertTrue(paused.applied(), "the current configuration can be paused");
        helper.assertValueEqual(paused.selected().state(), SettlementWorkerPool.State.PAUSED, "the response contains authoritative paused state");
        var removed = request(player, town, id, paused.selected().revision(),
                new SettlementWorkerPoolRequestPayload.Change(SettlementWorkerPoolRequestPayload.Action.REMOVE));
        helper.assertTrue(removed.applied() && removed.selected() == null && town.workerPools().isEmpty(), "removal releases enrollment without inventing a replacement");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_worker_pool_management")
    public static void queuedRequestsRecheckAuthorityBeforeMutationAndDisclosure(GameTestHelper helper) {
        var fixture = SettlementWorkerPoolAssignmentGameTests.ready(helper, 1);
        var town = fixture.town();
        var player = viewer(helper, town, true);
        var id = UUID.randomUUID();
        var responses = new ArrayList<SettlementWorkerPoolSnapshotPayload>();
        var request = payload(town, id, -1, new SettlementWorkerPoolRequestPayload.Save(configuration(fixture), true));
        var work = SettlementWorkerPoolManagementService.prepare(player, request, responses::add);
        town.setPermissions(town.leader(), player.getUUID(), Set.of(SettlementPermission.VIEW));
        drain(work);
        helper.assertValueEqual(responses.getFirst().feedback(), "pool_permission_changed", "revocation before execution rejects a queued edit");
        helper.assertTrue(town.workerPools().isEmpty(), "the old request creates no staffing authority");
        responses.clear();
        work = SettlementWorkerPoolManagementService.prepare(player, payload(town, null, -1, new SettlementWorkerPoolRequestPayload.View()), responses::add);
        work.run();
        town.setPermissions(town.leader(), player.getUUID(), Set.of());
        drain(work);
        var hidden = responses.getFirst();
        helper.assertTrue(hidden.residents().isEmpty() && hidden.worksites().isEmpty() && hidden.pools().isEmpty(), "revoked inspection discloses no settlement roster");
        helper.assertValueEqual(hidden.feedback(), "pool_permission_changed", "the response explains revoked access");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_worker_pool_management")
    public static void returningToPoolControlRequiresCurrentEnrollmentAndManualState(GameTestHelper helper) {
        var fixture = SettlementWorkerPoolAssignmentGameTests.ready(helper, 2);
        var town = fixture.town();
        var player = viewer(helper, town, true);
        var resident = fixture.residents().getFirst();
        SettlementWorkerPoolAssignmentGameTests.staff(helper, town, resident, fixture.farm());
        town.rememberManualDuty(resident.getUUID());
        var saved = request(player, town, UUID.randomUUID(), -1, new SettlementWorkerPoolRequestPayload.Save(
                new SettlementWorkerPool.Configuration("Selected resident", List.of(resident.getUUID()), configuration(fixture).worksites()), true));
        long observed = saved.revision();
        town.assignDuty(resident.getUUID(), fixture.stores().id());
        var stale = request(player, town, saved.selected().id(), saved.selected().revision(),
                new SettlementWorkerPoolRequestPayload.ReturnControl(resident.getUUID(), observed));
        helper.assertValueEqual(stale.feedback(), "pool_changed", "returning a pin cannot race a newer manual assignment");
        helper.assertTrue(town.manuallyStaffed(resident.getUUID()), "the newer manual duty stays pinned");
        var outsider = fixture.residents().getLast().getUUID();
        town.rememberManualDuty(outsider);
        var denied = request(player, town, saved.selected().id(), saved.selected().revision(),
                new SettlementWorkerPoolRequestPayload.ReturnControl(outsider, town.revision()));
        helper.assertValueEqual(denied.feedback(), "pool_resident_missing", "pool controls cannot release an unenrolled resident");
        var returned = request(player, town, saved.selected().id(), saved.selected().revision(),
                new SettlementWorkerPoolRequestPayload.ReturnControl(resident.getUUID(), town.revision()));
        helper.assertTrue(returned.applied() && !town.manuallyStaffed(resident.getUUID()), "the explicit current request releases this resident");
        helper.assertTrue(town.manuallyStaffed(outsider), "another manual resident remains untouched");
        helper.assertValueEqual(town.duties().get(resident.getUUID()), fixture.stores().id(), "returning authority does not itself perform a reassignment");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_worker_pool_management")
    public static void returningControlSurvivesTownProgressButRejectsNewPins(GameTestHelper helper) {
        var fixture = SettlementWorkerPoolAssignmentGameTests.ready(helper, 1);
        var town = fixture.town();
        var player = viewer(helper, town, true);
        var resident = fixture.residents().getFirst().getUUID();
        town.rememberManualDuty(resident);
        var saved = request(player, town, UUID.randomUUID(), -1,
                new SettlementWorkerPoolRequestPayload.Save(configuration(fixture), true));
        town.recordResident(town.residents().get(resident));
        var returned = request(player, town, saved.selected().id(), saved.selected().revision(),
                new SettlementWorkerPoolRequestPayload.ReturnControl(resident, saved.revision()));
        helper.assertTrue(returned.applied() && !town.manuallyStaffed(resident), "unrelated care updates do not invalidate explicit pool control");

        town.rememberManualDuty(resident);
        long observed = town.revision();
        town.rememberManualDuty(resident);
        var stale = request(player, town, saved.selected().id(), saved.selected().revision(),
                new SettlementWorkerPoolRequestPayload.ReturnControl(resident, observed));
        helper.assertValueEqual(stale.feedback(), "pool_changed", "renewing a pin on the same duty invalidates an earlier return request");
        helper.assertTrue(town.manuallyStaffed(resident), "the renewed manual pin remains protected");
        long pinnedAt = town.manualDutyRevision(resident);
        town.recordResident(town.residents().get(resident));
        var tag = town.save(helper.getLevel().registryAccess());
        var restored = SettlementRecord.load(tag, helper.getLevel().registryAccess(), () -> {});
        helper.assertValueEqual(restored.manualDutyRevision(resident), pinnedAt, "reload preserves the pin's revision independently of town progress");
        helper.assertTrue(restored.returnToAutomaticStaffing(town.leader(), resident), "a restored pin can return to pool control");
        helper.assertValueEqual(restored.manualDutyRevision(resident), -1L, "releasing a pin removes its revision");

        var legacy = tag.copy();
        legacy.remove("ManualDutyRevisions");
        restored = SettlementRecord.load(legacy, helper.getLevel().registryAccess(), () -> {});
        helper.assertTrue(restored.manuallyStaffed(resident), "older saves preserve manual assignments");
        helper.assertValueEqual(restored.manualDutyRevision(resident), restored.revision(), "older pins start conservatively at the saved town revision");
        tag.getList("ManualDutyRevisions", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0).putLong("Revision", town.revision() + 1);
        try {
            SettlementRecord.load(tag, helper.getLevel().registryAccess(), () -> {});
            throw new AssertionError("A future manual pin revision must be rejected");
        } catch (IllegalArgumentException expected) {
            helper.assertTrue(expected.getMessage().contains("manual duty revision"), "recovery rejects the malformed pin revision");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_worker_pool_management")
    public static void incrementalPreviewDoesNotAuthorizePinsCreatedDuringInspection(GameTestHelper helper) {
        var fixture = SettlementWorkerPoolAssignmentGameTests.ready(helper, 1);
        var town = fixture.town();
        var player = viewer(helper, town, true);
        var resident = fixture.residents().getFirst();
        SettlementWorkerPoolAssignmentGameTests.staff(helper, town, resident, fixture.farm());
        town.rememberManualDuty(resident.getUUID());
        var saved = request(player, town, UUID.randomUUID(), -1,
                new SettlementWorkerPoolRequestPayload.Save(configuration(fixture), true));
        var responses = new ArrayList<SettlementWorkerPoolSnapshotPayload>();
        var inspection = SettlementWorkerPoolManagementService.prepare(player,
                payload(town, saved.selected().id(), saved.selected().revision(), new SettlementWorkerPoolRequestPayload.View()), responses::add);
        inspection.run();
        long observed = town.revision();
        town.rememberManualDuty(resident.getUUID());
        drain(inspection);
        helper.assertValueEqual(responses.getFirst().revision(), observed, "the preview retains its revision from before incremental inspection");
        var returned = request(player, town, saved.selected().id(), saved.selected().revision(),
                new SettlementWorkerPoolRequestPayload.ReturnControl(resident.getUUID(), responses.getFirst().revision()));
        helper.assertValueEqual(returned.feedback(), "pool_changed", "a pin created while the snapshot was being built requires a fresh inspection");
        helper.assertTrue(town.manuallyStaffed(resident.getUUID()), "the concurrent manual assignment remains pinned");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_worker_pool_management")
    public static void residentCatalogueIsPagedAndSearchableWithoutAutomaticEnrollment(GameTestHelper helper) {
        var fixture = SettlementWorkerPoolAssignmentGameTests.ready(helper, 35);
        var town = fixture.town();
        var player = viewer(helper, town, false);
        var first = request(player, payload(town, null, -1, new SettlementWorkerPoolRequestPayload.View()));
        var second = request(player, new SettlementWorkerPoolRequestPayload(town.id().value(), UUID.randomUUID(), null, -1, "", 1,
                new SettlementWorkerPoolRequestPayload.View()));
        helper.assertValueEqual(first.residents().size(), SettlementWorkerPoolSnapshotPayload.PAGE_SIZE, "the first page has a bounded roster");
        helper.assertTrue(first.more() && !second.more(), "page metadata identifies the remaining residents");
        var found = new ArrayList<>(first.residents().stream().map(SettlementWorkerPoolSnapshotPayload.Resident::id).toList());
        found.addAll(second.residents().stream().map(SettlementWorkerPoolSnapshotPayload.Resident::id).toList());
        helper.assertValueEqual(found, town.residents().keySet().stream().sorted().toList(), "every resident appears once in stable order");
        var id = fixture.residents().getLast().getUUID();
        var search = request(player, new SettlementWorkerPoolRequestPayload(town.id().value(), UUID.randomUUID(), null, -1, id.toString(), 0,
                new SettlementWorkerPoolRequestPayload.View()));
        helper.assertValueEqual(search.residents().stream().map(SettlementWorkerPoolSnapshotPayload.Resident::id).toList(), List.of(id),
                "search reaches residents beyond the initial page");
        helper.assertTrue(town.workerPools().isEmpty(), "browsing never enrolls the population");
        codec(helper, second);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_worker_pool_management")
    public static void requestCodecsRetainDraftsActionsAndRevisionGuards(GameTestHelper helper) {
        var fixture = SettlementWorkerPoolAssignmentGameTests.ready(helper, 1);
        var config = configuration(fixture);
        var commands = List.<SettlementWorkerPoolRequestPayload.Command>of(new SettlementWorkerPoolRequestPayload.View(),
                new SettlementWorkerPoolRequestPayload.Preview(config), new SettlementWorkerPoolRequestPayload.Save(config, true),
                new SettlementWorkerPoolRequestPayload.Change(SettlementWorkerPoolRequestPayload.Action.ACTIVATE),
                new SettlementWorkerPoolRequestPayload.Change(SettlementWorkerPoolRequestPayload.Action.PAUSE),
                new SettlementWorkerPoolRequestPayload.Change(SettlementWorkerPoolRequestPayload.Action.REMOVE),
                new SettlementWorkerPoolRequestPayload.ReturnControl(fixture.residents().getFirst().getUUID(), fixture.town().revision()));
        var buffer = new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            for (var command : commands) {
                var original = payload(fixture.town(), UUID.randomUUID(), -1, command);
                SettlementWorkerPoolRequestPayload.STREAM_CODEC.encode(buffer, original);
                helper.assertValueEqual(SettlementWorkerPoolRequestPayload.STREAM_CODEC.decode(buffer), original, "the request retains every command and expected revision");
            }
            helper.assertValueEqual(buffer.readableBytes(), 0, "request decoding consumes the exact wire format");
        } finally {
            buffer.release();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_worker_pool_management")
    public static void inspectionExplainsActualMovesAndCooldowns(GameTestHelper helper) {
        var fixture = SettlementWorkerPoolAssignmentGameTests.ready(helper, 1);
        var town = fixture.town();
        var player = viewer(helper, town, false);
        var pool = SettlementWorkerPoolAssignmentGameTests.pool(helper, fixture, 0);
        SettlementWorkerPoolSchedulingGameTests.cycle(helper, town, helper.getLevel().getGameTime());
        var response = request(player, town, pool.id(), pool.revision(), new SettlementWorkerPoolRequestPayload.View());
        helper.assertValueEqual(response.history().size(), 1, "inspection includes the actual bounded move history");
        var move = response.history().getFirst();
        helper.assertValueEqual(move.reason(), SettlementWorkerPool.Reason.PROTECTED_SHORTAGE, "the protected shortage explains the assignment");
        helper.assertTrue(move.demand() != null && !move.cause().isBlank(), "the client receives a stable demand and readable cause");
        helper.assertValueEqual(move.to(), fixture.farm().name(), "history resolves the assigned worksite");
        helper.assertValueEqual(response.residents().getFirst().cooldown(), 1_200L, "the actual move cooldown is visible");
        helper.assertValueEqual(response.residents().getFirst().blocker(), "pool_cooldown", "inspection explains why the resident cannot move again");
        codec(helper, response);
        helper.succeed();
    }

    private static SettlementWorkerPool.Configuration configuration(SettlementWorkerPoolGameTests.Fixture fixture) {
        return new SettlementWorkerPool.Configuration("Food and deliveries", fixture.residents().stream().map(net.minecraft.world.entity.npc.Villager::getUUID).toList(),
                List.of(SettlementWorkerPool.Staffing.defaults(fixture.town(), fixture.farm().id()),
                        SettlementWorkerPool.Staffing.defaults(fixture.town(), fixture.stores().id())));
    }

    private static ServerPlayer viewer(GameTestHelper helper, SettlementRecord town, boolean staff) {
        var player = helper.makeMockServerPlayerInLevel();
        town.setPermissions(town.leader(), player.getUUID(), staff ? Set.of(SettlementPermission.VIEW, SettlementPermission.STAFF) : Set.of(SettlementPermission.VIEW));
        return player;
    }

    private static SettlementWorkerPoolRequestPayload payload(SettlementRecord town, UUID id, long revision, SettlementWorkerPoolRequestPayload.Command command) {
        return new SettlementWorkerPoolRequestPayload(town.id().value(), UUID.randomUUID(), id, revision, "", 0, command);
    }

    private static SettlementWorkerPoolSnapshotPayload request(ServerPlayer player, SettlementRecord town, UUID id, long revision,
                                                              SettlementWorkerPoolRequestPayload.Command command) {
        return request(player, payload(town, id, revision, command));
    }

    private static SettlementWorkerPoolSnapshotPayload request(ServerPlayer player, SettlementWorkerPoolRequestPayload request) {
        var responses = new ArrayList<SettlementWorkerPoolSnapshotPayload>();
        drain(SettlementWorkerPoolManagementService.prepare(player, request, responses::add));
        if (responses.size() != 1) throw new AssertionError("Pool requests must finish exactly once");
        return responses.getFirst();
    }

    private static void drain(Runnable work) {
        int steps = 0;
        while (!SettlementWork.complete(work) && steps++ < 10_000) work.run();
        if (!SettlementWork.complete(work)) throw new AssertionError("Pool inspection exceeded the bounded fixture budget");
    }

    private static void codec(GameTestHelper helper, SettlementWorkerPoolSnapshotPayload snapshot) {
        var buffer = new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            SettlementWorkerPoolSnapshotPayload.STREAM_CODEC.encode(buffer, snapshot);
            helper.assertValueEqual(SettlementWorkerPoolSnapshotPayload.STREAM_CODEC.decode(buffer), snapshot, "snapshot state and previews survive the wire codec");
            helper.assertValueEqual(buffer.readableBytes(), 0, "snapshot decoding consumes the exact payload");
        } finally {
            buffer.release();
        }
    }
}
