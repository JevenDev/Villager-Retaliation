package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.client.settlement.SettlementSelectionSet.Operation;
import com.jvn.villagerretaliation.client.settlement.SettlementViewSelection;
import com.jvn.villagerretaliation.network.SettlementManagementPayload.Bed;
import com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page;
import com.jvn.villagerretaliation.network.SettlementViewBatchPayload;
import com.jvn.villagerretaliation.network.SettlementViewBatchResultPayload.Status;
import com.jvn.villagerretaliation.network.SettlementViewOverviewPayload;
import com.jvn.villagerretaliation.network.SettlementViewOverviewRequestPayload;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("settlement_housing_assignment")
@PrefixGameTestTemplate(false)
public final class SettlementHousingAssignmentGameTests {
    private SettlementHousingAssignmentGameTests() { }

    @GameTest(template = "empty", batch = "settlement_housing_assignment")
    public static void mixedSelectionsShareAddToggleRemoveAndControlGroupRules(GameTestHelper helper) {
        var selection = new SettlementViewSelection();
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        var bed = new BlockPos(-4, 64, -8);
        selection.apply(List.of(first), List.of(bed), Operation.REPLACE);
        selection.apply(List.of(second), List.of(), Operation.modifiers(true, false));
        helper.assertValueEqual(selection.residents(), List.of(first, second), "shift adds residents without clearing beds");
        helper.assertValueEqual(selection.beds(), List.of(bed), "mixed selections retain their beds");
        selection.apply(List.of(first, first), List.of(bed, bed), Operation.modifiers(false, true));
        helper.assertValueEqual(selection.residents(), List.of(second), "control toggles duplicate hits only once");
        helper.assertTrue(selection.beds().isEmpty(), "control toggles selected beds off");
        selection.apply(List.of(first), List.of(bed), Operation.ADD);
        selection.apply(List.of(first), List.of(bed.east()), Operation.modifiers(true, true));
        helper.assertValueEqual(selection.residents(), List.of(second), "control shift subtracts residents");
        helper.assertValueEqual(selection.beds(), List.of(bed), "subtract never adds an unselected bed");
        selection.store(0);
        selection.apply(List.of(), List.of(), Operation.REPLACE);
        selection.recall(0, 1_000L);
        helper.assertValueEqual(selection.residents(), List.of(second), "existing resident control groups still work");
        helper.assertTrue(selection.beds().isEmpty(), "recalling a resident group replaces the mixed selection");
        selection.apply(List.of(), List.of(bed), Operation.ADD);
        selection.prune(Set.of());
        helper.assertValueEqual(selection.beds(), List.of(bed), "roster pruning cannot remove a selected bed");
        selection.retainBeds(Set.of());
        helper.assertTrue(selection.beds().isEmpty(), "authoritative bed observations prune obsolete beds");
        var many = java.util.stream.IntStream.range(0, 129).mapToObj(index -> bed.east(index)).toList();
        helper.assertFalse(selection.apply(List.of(), many, Operation.REPLACE), "oversized bed selections report truncation");
        helper.assertValueEqual(selection.beds().size(), 128, "bed selections stay bounded");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_housing_assignment")
    public static void distributionKeepsSelectedHomesAndNeverTakesOtherResidentsBeds(GameTestHelper helper) {
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        var third = UUID.randomUUID();
        var origin = new BlockPos(0, 64, 0);
        var beds = List.of(bed(origin, first, false), bed(origin.east(), null, true), bed(origin.east(2), UUID.randomUUID(), false),
                bed(origin.east(3), null, false), bed(origin.east(4), second, false));
        var selected = List.of(origin.east(3), origin.east(2), origin.east(), origin);
        var plan = SettlementHousingDistribution.plan(List.of(first, second, third), selected, beds);
        helper.assertValueEqual(plan.unchanged(), 1, "a resident already owning a selected bed stays there");
        helper.assertValueEqual(plan.unassigned(), 1, "a shortage is reported without taking an occupied bed");
        helper.assertValueEqual(plan.targets(), List.of(new SettlementViewBatchPayload.Target(second, null, origin.east(), origin.east(4))),
                "the remaining free bed includes the resident's reviewed former home");
        var reversed = new java.util.ArrayList<>(beds);
        java.util.Collections.reverse(reversed);
        helper.assertValueEqual(SettlementHousingDistribution.plan(List.of(first, second, third), selected, reversed), plan,
                "snapshot ordering does not change the proposed distribution");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_housing_assignment")
    public static void bedSnapshotsAndAssignmentsRoundTripAndBatchesCannotDoubleBook(GameTestHelper helper) {
        var fixture = SettlementPopulationGameTests.fixture(helper);
        fixture.leader().setLastSectionPos(net.minecraft.core.SectionPos.of(fixture.leader()));
        var first = resident(helper, fixture, true);
        var second = resident(helper, fixture, false);
        first.setAge(-24000);
        try {
            helper.assertTrue(SettlementViewService.open(fixture.leader(), fixture.town().id()), "the leader opens world view");
            var session = SettlementViewService.session(fixture.leader().getUUID());
            var overview = session.overview.page(session, new SettlementViewOverviewRequestPayload(session.id, 1, 0, Page.RESIDENTS, 0));
            var snapshot = session.overview.page(session, new SettlementViewOverviewRequestPayload(session.id, 2, overview.generation(), Page.BEDS, 0));
            helper.assertValueEqual(snapshot.entries().size(), 2, "world view includes both surveyed beds");
            roundTrip(helper, SettlementViewOverviewPayload.STREAM_CODEC, snapshot);
            var beds = snapshot.entries().stream().map(Bed.class::cast).toList();
            var targets = List.of(new SettlementViewBatchPayload.Target(first.getUUID(), null, beds.getFirst().position(), null),
                    new SettlementViewBatchPayload.Target(second.getUUID(), null, beds.getLast().position(), null));
            var request = request(session, targets);
            roundTrip(helper, SettlementViewBatchPayload.STREAM_CODEC, request);
            session.commands.submit(session, request);
            helper.assertValueEqual(session.commands.tick(session, 1), 1, "housing batches respect the command tick budget");
            helper.assertValueEqual(session.commands.tick(session, 1), 1, "the remaining resident is processed on the next tick");
            helper.assertValueEqual(fixture.town().homes().get(first.getUUID()).bed(), targets.getFirst().bed(), "children can receive a bed");
            helper.assertValueEqual(fixture.town().homes().get(second.getUUID()).bed(), targets.getLast().bed(), "unloaded residents can receive a bed");
            long revision = fixture.town().revision();
            session.commands.submit(session, request);
            helper.assertValueEqual(session.commands.tick(session, 16), 0, "duplicate commands are not reapplied");
            helper.assertValueEqual(fixture.town().revision(), revision, "replaying the command preserves home versions");
            expectInvalid(helper, () -> request(session, List.of(targets.getFirst(), new SettlementViewBatchPayload.Target(second.getUUID(), null, targets.getFirst().bed(), null))));
            expectInvalid(helper, () -> request(session, List.of(new SettlementViewBatchPayload.Target(first.getUUID(), null))));
        } finally {
            first.discard();
            SettlementViewService.close(fixture.leader().getUUID(), false);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_housing_assignment")
    public static void housingCommandsRejectStaleHomesOccupiedBedsAndLostPermission(GameTestHelper helper) {
        var fixture = SettlementPopulationGameTests.fixture(helper);
        fixture.leader().setLastSectionPos(net.minecraft.core.SectionPos.of(fixture.leader()));
        var first = resident(helper, fixture, false);
        var second = resident(helper, fixture, false);
        var beds = fixture.town().surveys().get(fixture.home().id()).beds();
        try {
            SettlementViewService.open(fixture.leader(), fixture.town().id());
            var session = SettlementViewService.session(fixture.leader().getUUID());
            var target = new SettlementViewBatchPayload.Target(first.getUUID(), null, beds.getFirst(), null);
            var request = request(session, List.of(target));
            helper.assertTrue(SettlementHousingService.assign(fixture.leader(), fixture.town(), first.getUUID(), beds.getLast()), "the home changes after review");
            helper.assertValueEqual(SettlementViewCommands.apply(session, request, target).reason(), "Home changed since review", "stale review cannot replace a newer home");
            var occupied = new SettlementViewBatchPayload.Target(second.getUUID(), null, beds.getLast(), null);
            helper.assertValueEqual(SettlementViewCommands.apply(session, request(session, List.of(occupied)), occupied).status(), Status.FAILED, "an occupied bed cannot be taken");
            helper.assertValueEqual(fixture.town().homes().get(first.getUUID()).bed(), beds.getLast(), "the current owner keeps the bed");
            fixture.town().setPermissions(fixture.leader().getUUID(), second.getUUID(), Set.of(SettlementPermission.VIEW));
            var viewer = net.neoforged.neoforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                    new com.mojang.authlib.GameProfile(second.getUUID(), "HousingViewer"));
            viewer.setPos(fixture.town().center().getCenter());
            viewer.setLastSectionPos(net.minecraft.core.SectionPos.of(viewer));
            SettlementViewService.open(viewer, fixture.town().id());
            var viewSession = SettlementViewService.session(viewer.getUUID());
            var denied = request(viewSession, List.of(target));
            helper.assertValueEqual(SettlementViewCommands.apply(viewSession, denied, target).status(), Status.FAILED, "view permission does not allow bed assignments");
            SettlementViewService.close(viewer.getUUID(), false);
            var disabled = new SettlementArea(fixture.home().id(), fixture.home().type(), fixture.home().name(), fixture.home().min(), fixture.home().max(), 5, false);
            fixture.town().putArea(fixture.leader().getUUID(), disabled);
            var invalid = new SettlementViewBatchPayload.Target(second.getUUID(), null, beds.getFirst(), null);
            helper.assertValueEqual(SettlementViewCommands.apply(session, request(session, List.of(invalid)), invalid).status(), Status.FAILED, "disabled housing is revalidated before assignment");
        } finally {
            SettlementViewService.close(fixture.leader().getUUID(), false);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_housing_assignment")
    public static void nativeHomeAssignmentsRemainCompatible(GameTestHelper helper) {
        SettlementHousingGameTests.managementAssignmentsUpdateNativeHomesAndReleaseOnlyTheSelectedBed(helper);
    }

    @GameTest(template = "empty", batch = "settlement_housing_assignment")
    public static void unloadedHomeReservationsRemainCompatible(GameTestHelper helper) {
        SettlementHousingGameTests.unloadedReservationsBlockDuplicateAssignmentsAndNativeAcquisition(helper);
    }

    @GameTest(template = "empty", batch = "settlement_housing_assignment")
    public static void savedHomeAssignmentsRemainCompatible(GameTestHelper helper) {
        SettlementHousingGameTests.savedHomeAssignmentsRejectDuplicateBedsAndRetainLegacyCompatibility(helper);
    }

    @GameTest(template = "empty", batch = "settlement_housing_assignment")
    public static void invalidHousingStillReleasesHomes(GameTestHelper helper) {
        SettlementHousingGameTests.missingBedsAndDisabledHomesInvalidateAssignments(helper);
    }

    @GameTest(template = "empty", batch = "settlement_housing_assignment")
    public static void unloadedHousingRemainsAssignable(GameTestHelper helper) {
        SettlementHousingGameTests.recordedUnloadedBedsRemainAssignableWithoutInventingLegacyAvailability(helper);
    }

    private static Bed bed(BlockPos position, UUID resident, boolean available) {
        return new Bed(UUID.randomUUID(), "Housing", position, resident, "", available);
    }

    private static Villager resident(GameTestHelper helper, SettlementPopulationGameTests.Fixture fixture, boolean loaded) {
        var villager = EntityType.VILLAGER.create(helper.getLevel());
        villager.setPos(fixture.town().center().getBottomCenter());
        villager.setNoAi(true);
        VillageAllegianceApi.assignKnown(helper.getLevel(), villager, fixture.town().id(), AllegianceAssignmentSource.EXPLICIT_API);
        if (loaded) helper.assertTrue(helper.getLevel().addFreshEntity(villager), "the resident is loaded");
        SettlementCareService.onUnloaded(villager);
        return villager;
    }

    private static SettlementViewBatchPayload request(SettlementViewService.Session session, List<SettlementViewBatchPayload.Target> targets) {
        return new SettlementViewBatchPayload(session.id, SettlementViewBatchPayload.operation(session.id, 1), SettlementViewAction.ASSIGN_HOME, targets, null, null);
    }

    private static <T> void roundTrip(GameTestHelper helper, StreamCodec<RegistryFriendlyByteBuf, T> codec, T value) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            codec.encode(buffer, value);
            helper.assertValueEqual(codec.decode(buffer), value, "the complete payload survives encoding");
            helper.assertValueEqual(buffer.readableBytes(), 0, "the payload is fully consumed");
        } finally {
            buffer.release();
        }
    }

    private static void expectInvalid(GameTestHelper helper, Runnable action) {
        try {
            action.run();
            helper.fail("invalid home targets must be rejected");
        } catch (IllegalArgumentException expected) { }
    }
}
