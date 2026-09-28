package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.interaction.VillagerWalletService;
import com.jvn.villagerretaliation.network.SettlementBuildingPreviewPayload;
import com.jvn.villagerretaliation.network.SettlementBuildingPreviewRequestPayload;
import com.jvn.villagerretaliation.network.SettlementManagementActionPayload;
import com.jvn.villagerretaliation.sell.CurrencyAmount;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SettlementBuildingPlanningGameTests {
    private SettlementBuildingPlanningGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_construction")
    public static void directRequestNeedsNeitherPaperNorRegisteredAreaAndRefundsBeforeWork(GameTestHelper helper) {
        var fixture = SettlementConstructionGameTests.fixture(helper);
        try {
            fixture.town().advance();
            fixture.town().removeArea(fixture.leader().getUUID(), fixture.area().id());
            SettlementWorkforceService.reconcile(helper.getLevel(), fixture.builder());
            var balance = fixture.town().economy().balance();
            UUID id = UUID.randomUUID();
            var command = command(fixture, id);
            helper.assertTrue(SettlementManagementService.execute(fixture.leader(), command), "an unassigned builder accepts a prefab directly");
            var project = fixture.town().projects().get(id);
            helper.assertTrue(fixture.town().areas().isEmpty(), "the project does not register a manual area");
            helper.assertFalse(fixture.town().putArea(fixture.town().leader(), project.workplace()), "manual areas cannot reuse a project-owned identity");
            helper.assertFalse(fixture.town().putArea(fixture.town().leader(), new SettlementArea(UUID.randomUUID(), SettlementAreaType.FARM, "Conflicting field", project.min(), project.max(), 5, true)), "terrain work cannot claim an active building footprint");
            helper.assertTrue(project.workplace() != null && fixture.town().workplace(project.area()) != null, "the worker has a project-owned site");
            helper.assertValueEqual(fixture.leader().getInventory().countItem(Items.PAPER), 0, "no paper is needed");
            helper.assertValueEqual(fixture.town().economy().balance(), balance.subtract(CurrencyAmount.of(fixture.plan().price(), 1)), "the fee is reserved exactly once");
            helper.assertTrue(SettlementManagementService.execute(fixture.leader(), command), "a duplicate accepted request is idempotent");
            helper.assertValueEqual(fixture.town().projects().size(), 1, "duplicate requests do not create another project");
            var saved = fixture.town().save(helper.getLevel().registryAccess());
            var restored = SettlementRecord.load(saved, helper.getLevel().registryAccess(), () -> {});
            helper.assertTrue(restored.workplace(project.area()) != null, "project-owned duties survive reload");
            helper.assertTrue(SettlementConstructionService.cancel(fixture.leader(), fixture.town(), id), "an unstarted project can be cancelled");
            helper.assertValueEqual(fixture.town().economy().balance(), balance, "cancellation refunds the reservation");
            helper.assertFalse(fixture.town().duties().containsKey(fixture.builder().getUUID()), "an originally unassigned builder is released");
            helper.assertFalse(SettlementConstructionService.cancel(fixture.leader(), fixture.town(), id), "cancellation cannot refund twice");
            helper.assertTrue(SettlementManagementService.execute(fixture.leader(), command), "a cancelled request remains acknowledged without restarting");
            helper.assertFalse(project.active(), "replays cannot restart cancelled work");
        } finally {
            fixture.builder().discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_construction")
    public static void treasuryReserveAndRejectedPlacementDoNotChangeAssignmentsOrFunds(GameTestHelper helper) {
        var fixture = SettlementConstructionGameTests.fixture(helper);
        try {
            fixture.town().advance();
            var balance = fixture.town().economy().balance();
            fixture.town().economy().configureBudget(balance, CurrencyAmount.ZERO);
            helper.assertFalse(SettlementManagementService.execute(fixture.leader(), command(fixture, UUID.randomUUID())), "the treasury reserve cannot fund construction");
            helper.assertValueEqual(fixture.town().duties().get(fixture.builder().getUUID()), fixture.area().id(), "denied requests preserve the existing duty");
            fixture.town().economy().configureBudget(CurrencyAmount.ZERO, CurrencyAmount.ZERO);
            var target = fixture.plan().worldPos(fixture.origin(), fixture.plan().blocks().getFirst());
            helper.getLevel().setBlockAndUpdate(target, Blocks.BEDROCK.defaultBlockState());
            helper.assertFalse(SettlementManagementService.execute(fixture.leader(), command(fixture, UUID.randomUUID())), "protected blocks reject placement");
            helper.assertValueEqual(fixture.town().economy().balance(), balance, "failed placement does not reserve money");
            fixture.builder().setAge(-24_000);
            helper.getLevel().setBlockAndUpdate(target, Blocks.AIR.defaultBlockState());
            helper.assertFalse(SettlementManagementService.execute(fixture.leader(), command(fixture, UUID.randomUUID())), "children are not eligible builders");
            helper.assertTrue(fixture.town().projects().isEmpty(), "rejected requests create no projects");
        } finally {
            fixture.builder().discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_construction")
    public static void earnedPaymentAndPreviousAssignmentSurviveCancellationAndReload(GameTestHelper helper) {
        var fixture = SettlementConstructionGameTests.fixture(helper);
        try {
            fixture.town().advance();
            var settings = SettlementWorkerSettings.capture(com.jvn.villagerretaliation.interaction.HiredVillagerRole.BUILDER,
                    HiredWorkStateStore.state(fixture.builder()));
            fixture.town().setWorkerSettings(fixture.leader().getUUID(), fixture.builder().getUUID(), settings);
            int wallet = VillagerWalletService.getCurrentEmeralds(fixture.builder());
            var balance = fixture.town().economy().balance();
            UUID id = UUID.randomUUID();
            helper.assertTrue(SettlementManagementService.execute(fixture.leader(), command(fixture, id)), "the idle construction builder accepts a new site");
            SettlementConstructionService.worked(fixture.builder(), HiredWorkStateStore.state(fixture.builder()));
            SettlementConstructionService.worked(fixture.builder(), HiredWorkStateStore.state(fixture.builder()));
            helper.assertValueEqual(VillagerWalletService.getCurrentEmeralds(fixture.builder()), wallet + fixture.plan().price(), "payment is credited once");
            var restored = SettlementRecord.load(fixture.town().save(helper.getLevel().registryAccess()), helper.getLevel().registryAccess(), () -> {});
            SettlementConstructionService.synchronizePayments(restored, fixture.builder());
            helper.assertValueEqual(VillagerWalletService.getCurrentEmeralds(fixture.builder()), wallet + fixture.plan().price(), "reloaded payment receipts cannot pay again");
            helper.assertTrue(SettlementConstructionService.cancel(fixture.leader(), fixture.town(), id), "earned work can still be cancelled");
            helper.assertValueEqual(fixture.town().economy().balance(), balance.subtract(CurrencyAmount.of(fixture.plan().price(), 1)), "earned fees are not refunded");
            helper.assertValueEqual(fixture.town().duties().get(fixture.builder().getUUID()), fixture.area().id(), "the previous construction duty is restored");
            helper.assertValueEqual(fixture.town().workerSettings().get(fixture.builder().getUUID()), settings, "previous settings are restored");
        } finally {
            fixture.builder().discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_construction")
    public static void previewAndCommissionPacketsAreReadOnlyBoundedAndCorrelated(GameTestHelper helper) {
        var fixture = SettlementConstructionGameTests.fixture(helper);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            fixture.town().advance();
            var command = command(fixture, UUID.randomUUID());
            var build = (SettlementManagementActionPayload.CommissionBuilding) command.command();
            var request = new SettlementBuildingPreviewRequestPayload(fixture.town().id().value(), 17, build.structure(), build.anchor(), build.rotation(), build.builder());
            var balance = fixture.town().economy().balance();
            var preview = SettlementBuildingPlanner.preview(fixture.leader(), request);
            helper.assertTrue(preview.failure().isEmpty() && !preview.blocks().isEmpty(), "a valid placement supplies the real preview geometry");
            helper.assertTrue(preview.builders().stream().anyMatch(builder -> builder.id().equals(build.builder())), "idle builders are listed");
            helper.assertTrue(preview.materials().stream().anyMatch(material -> material.available() < material.required()), "missing supplies are reported without blocking planning");
            helper.assertValueEqual(fixture.town().economy().balance(), balance, "previewing reserves no money");
            helper.assertTrue(fixture.town().projects().isEmpty(), "previewing reserves no project");
            SettlementBuildingPreviewRequestPayload.STREAM_CODEC.encode(buffer, request);
            helper.assertValueEqual(SettlementBuildingPreviewRequestPayload.STREAM_CODEC.decode(buffer), request, "preview requests round trip");
            SettlementBuildingPreviewPayload.STREAM_CODEC.encode(buffer, preview);
            var decoded = SettlementBuildingPreviewPayload.STREAM_CODEC.decode(buffer);
            helper.assertValueEqual(decoded.requestId(), 17, "preview responses preserve correlation");
            helper.assertValueEqual(decoded.blocks(), preview.blocks(), "preview geometry round trips");
            SettlementManagementActionPayload.STREAM_CODEC.encode(buffer, command);
            helper.assertValueEqual(SettlementManagementActionPayload.STREAM_CODEC.decode(buffer), command, "direct commissioning round trips");
        } finally {
            buffer.release();
            fixture.builder().discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_construction")
    public static void directAuthorityBoundsOverlapCapacityAndWorkingDutiesAreEnforced(GameTestHelper helper) {
        var fixture = SettlementConstructionGameTests.fixture(helper);
        try {
            fixture.town().advance();
            fixture.town().advance();
            var delegate = net.neoforged.neoforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                    new com.mojang.authlib.GameProfile(UUID.randomUUID(), "PrefabDelegate"));
            var build = (SettlementManagementActionPayload.CommissionBuilding) command(fixture, UUID.randomUUID()).command();
            fixture.town().setPermissions(fixture.town().leader(), delegate.getUUID(), java.util.Set.of(SettlementPermission.STAFF));
            helper.assertValueEqual(SettlementConstructionService.commission(delegate, fixture.town(), build.request(), build.structure(), build.anchor(), build.rotation(), build.builder()), "permission", "staffing does not authorize spending on a building");
            fixture.town().setPermissions(fixture.town().leader(), delegate.getUUID(), java.util.Set.of(SettlementPermission.BUILD));
            helper.assertValueEqual(SettlementConstructionService.commission(delegate, fixture.town(), build.request(), build.structure(),
                    build.anchor().east(fixture.town().radius() + 32), build.rotation(), build.builder()), "bounds", "the footprint must fit the settlement");
            helper.assertValueEqual(SettlementConstructionService.commission(delegate, fixture.town(), build.request(), build.structure(),
                    build.anchor().atY(helper.getLevel().getMaxBuildHeight()), build.rotation(), build.builder()), "bounds", "world height limits reject placement");
            var storage = new SettlementArea(UUID.randomUUID(), SettlementAreaType.DEPOT, "Other duty", fixture.area().min().east(24), fixture.area().max().east(24), 5, true);
            helper.assertTrue(fixture.town().putArea(fixture.town().leader(), storage), "a second working duty exists");
            helper.assertTrue(fixture.town().assignDuty(build.builder(), storage.id()), "the builder takes another duty");
            helper.assertValueEqual(SettlementConstructionService.commission(delegate, fixture.town(), build.request(), build.structure(), build.anchor(), build.rotation(), build.builder()), "builder", "another working duty prevents commissioning");
            fixture.town().assignDuty(build.builder(), fixture.area().id());
            helper.assertValueEqual(SettlementConstructionService.commission(delegate, fixture.town(), build.request(), build.structure(), build.anchor(), build.rotation(), build.builder()), "", "BUILD alone authorizes the temporary assignment and its fee");
            helper.assertValueEqual(SettlementConstructionService.placementFailure(delegate, fixture.town(), fixture.plan(), fixture.origin()), "overlap", "active footprints remain reserved");
            while (fixture.town().projects().values().stream().filter(SettlementProject::active).count() < fixture.town().stage().projects()) {
                UUID other = UUID.randomUUID();
                fixture.town().assignDuty(other, fixture.area().id());
                helper.assertTrue(fixture.town().startProject(fixture.town().leader(), SettlementProject.create(fixture.town(), fixture.area().id(), other, fixture.plan(), fixture.origin().east(12), UUID.randomUUID(), 0)), "the remaining project slot is filled");
            }
            helper.assertValueEqual(SettlementConstructionService.placementFailure(delegate, fixture.town(), fixture.plan(), fixture.origin()), "capacity", "exhausted project capacity has a specific failure");
        } finally {
            fixture.builder().discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_construction")
    public static void legacySavesHaveNoFeeAndMatchingCompletionSettlesNewFees(GameTestHelper helper) {
        var fixture = SettlementConstructionGameTests.fixture(helper);
        try {
            fixture.town().advance();
            var legacy = SettlementProject.create(fixture.town(), fixture.area().id(), fixture.builder().getUUID(), fixture.plan(), fixture.origin(), UUID.randomUUID(), 0);
            helper.assertTrue(fixture.town().startProject(fixture.town().leader(), legacy), "a legacy project exists");
            var tag = fixture.town().save(helper.getLevel().registryAccess());
            var oldProject = tag.getList("Projects", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0);
            oldProject.remove("Fee");
            oldProject.remove("PaymentEarned");
            oldProject.remove("PaymentRefunded");
            var restored = SettlementRecord.load(tag, helper.getLevel().registryAccess(), () -> {});
            helper.assertValueEqual(restored.projects().get(legacy.id()).fee(), 0, "old saved projects incur no retroactive fee");
            fixture.town().finishProject(legacy.id(), SettlementProject.Status.CANCELLED);
            UUID id = UUID.randomUUID();
            helper.assertTrue(SettlementManagementService.execute(fixture.leader(), command(fixture, id)), "a new project reserves its fee");
            var project = fixture.town().projects().get(id);
            SettlementBuildingService.refresh(helper.getLevel(), fixture.town(), project.workplace());
            helper.assertTrue(SettlementRecord.load(fixture.town().save(helper.getLevel().registryAccess()), helper.getLevel().registryAccess(), () -> {}).surveys().containsKey(project.area()), "owned-site surveys survive saving");
            int wallet = VillagerWalletService.getCurrentEmeralds(fixture.builder());
            SettlementConstructionService.finish(fixture.builder(), HiredWorkStateStore.state(fixture.builder()), SettlementProject.Status.COMPLETED);
            helper.assertTrue(project.paymentEarned(), "completion without placements settles the reserved fee");
            helper.assertValueEqual(VillagerWalletService.getCurrentEmeralds(fixture.builder()), wallet + fixture.plan().price(), "matching completion pays the builder");
            helper.assertTrue(SettlementRecord.load(fixture.town().save(helper.getLevel().registryAccess()), helper.getLevel().registryAccess(), () -> {}).projects().containsKey(id), "finished sites can save before their survey is released");
            SettlementConstructionSiteService.update(helper.getLevel(), fixture.town());
            helper.assertFalse(fixture.town().surveys().containsKey(project.area()), "finished site observations are released after reconciliation");
            SettlementRecord.load(fixture.town().save(helper.getLevel().registryAccess()), helper.getLevel().registryAccess(), () -> {});
        } finally {
            fixture.builder().discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_construction")
    public static void backgroundPaymentIsEarnedOnceAndReconcilesWithTheReturningNativeBuilder(GameTestHelper helper) {
        SettlementBackgroundConstructionGameTests.fixture(helper, false, java.util.Map.of(net.minecraft.core.BlockPos.ZERO, Blocks.OAK_PLANKS.defaultBlockState()), fixture -> {
            var level = helper.getLevel();
            fixture.project().reservePayment(fixture.plan().price());
            fixture.town().economy().credit(CurrencyAmount.of(fixture.plan().price(), 1));
            fixture.town().economy().reserveConstruction(fixture.plan().price());
            int wallet = VillagerWalletService.getCurrentEmeralds(fixture.builder());
            fixture.worker().rememberPayments(fixture.builder());
            fixture.town().residents().get(fixture.builder().getUUID()).remember(fixture.builder());
            var original = fixture.builder().saveWithoutId(new net.minecraft.nbt.CompoundTag());
            SettlementBackgroundConstructionGameTests.work(helper, fixture, 10);
            helper.assertFalse(fixture.project().paymentEarned(), "a background material shortage earns no payment");
            fixture.depot().setItem(0, new net.minecraft.world.item.ItemStack(Items.OAK_PLANKS));
            SettlementBackgroundConstructionGameTests.work(helper, fixture, 30);
            helper.assertTrue(fixture.project().paymentEarned() && fixture.project().site().pending(), "committed background placement earns the fee before world delivery");
            var restored = SettlementRecord.load(fixture.town().save(level.registryAccess()), level.registryAccess(), () -> {});
            var context = restored.workers().get(fixture.builder().getUUID()).context(level, restored);
            SettlementConstructionService.synchronizePayments(restored, context);
            helper.assertValueEqual(VillagerWalletService.getCurrentEmeralds(context), wallet + fixture.plan().price(), "background receipts survive saving without another credit");
            var returning = net.minecraft.world.entity.EntityType.VILLAGER.create(level);
            returning.load(original);
            try {
                SettlementConstructionService.synchronizePayments(restored, returning);
                SettlementConstructionService.synchronizePayments(restored, returning);
                helper.assertValueEqual(VillagerWalletService.getCurrentEmeralds(returning), wallet + fixture.plan().price(), "the returning native wallet receives its payment once");
                var reloaded = net.minecraft.world.entity.EntityType.VILLAGER.create(level);
                reloaded.load(returning.saveWithoutId(new net.minecraft.nbt.CompoundTag()));
                SettlementConstructionService.synchronizePayments(restored, reloaded);
                helper.assertValueEqual(VillagerWalletService.getCurrentEmeralds(reloaded), wallet + fixture.plan().price(), "subsequent native reloads retain the receipt");
            } finally {
                returning.discard();
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_construction")
    public static void cancelledOwnedSitesReleaseUnloadedBuildersForAnotherRequest(GameTestHelper helper) {
        var fixture = SettlementConstructionGameTests.fixture(helper);
        try {
            fixture.town().advance();
            fixture.town().removeArea(fixture.town().leader(), fixture.area().id());
            SettlementWorkforceService.reconcile(helper.getLevel(), fixture.builder());
            UUID first = UUID.randomUUID();
            helper.assertTrue(SettlementManagementService.execute(fixture.leader(), command(fixture, first)), "the unassigned resident accepts the first site");
            SettlementCareService.onUnloaded(fixture.builder());
            fixture.builder().remove(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
            helper.assertTrue(SettlementConstructionService.cancel(fixture.leader(), fixture.town(), first), "the owned site can be cancelled while the resident is unloaded");
            UUID second = UUID.randomUUID();
            helper.assertTrue(SettlementManagementService.execute(fixture.leader(), command(fixture, second)), "stale resident task data cannot prevent the next request");
            helper.assertValueEqual(fixture.town().activeProject(fixture.builder().getUUID()).id(), second, "the builder receives only the new project");
            helper.assertValueEqual(fixture.town().economy().spent(), CurrencyAmount.ZERO, "construction does not consume trading's daily budget");
            helper.assertTrue(SettlementConstructionService.cancel(fixture.leader(), fixture.town(), second), "the second reservation can be refunded");
        } finally {
            fixture.builder().discard();
        }
        helper.succeed();
    }

    private static SettlementManagementActionPayload command(SettlementConstructionGameTests.Fixture fixture, UUID id) {
        var anchor = fixture.origin().offset(fixture.plan().localCenter().getX(), fixture.plan().localMin().getY(), fixture.plan().localCenter().getZ());
        return new SettlementManagementActionPayload(fixture.town().id().value(), new SettlementManagementActionPayload.CommissionBuilding(id,
                fixture.plan().entry().id(), anchor, Rotation.NONE, fixture.builder().getUUID()));
    }
}
