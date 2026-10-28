package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderConstructionCost;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureCatalog;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureScanner;
import com.jvn.villagerretaliation.sell.CurrencyAmount;
import com.jvn.villagerretaliation.interaction.VillagerWalletService;
import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("settlement_lifecycle")
@PrefixGameTestTemplate(false)
public final class SettlementLifecycleGameTests {
    @GameTest(template = "empty", batch = "settlement_lifecycle")
    public static void dissolutionWaitsForConstructionMaterialRefunds(GameTestHelper helper) {
        SettlementBackgroundConstructionGameTests.fixture(helper, true, Map.of(BlockPos.ZERO, Blocks.OAK_PLANKS.defaultBlockState()), fixture -> {
            var level = helper.getLevel();
            var town = fixture.town();
            var project = fixture.project();
            var player = FakePlayerFactory.get(level, new GameProfile(town.leader(), "LifecycleLeader"));
            helper.assertTrue(level.addFreshEntity(fixture.builder()), "the builder is loaded for dissolution");
            level.setBlockAndUpdate(fixture.depot().position(), com.jvn.villagerretaliation.block.VillagerRetaliationBlocks.SETTLEMENT_DEPOT.get().defaultBlockState());
            ((com.jvn.villagerretaliation.block.SettlementDepotBlockEntity) level.getBlockEntity(fixture.depot().position())).bind(town.id(), fixture.depot().id());
            fixture.depot().observeRules(List.of());
            fixture.depot().setItem(0, new ItemStack(Items.OAK_PLANKS, 1));
            var plan = fixture.plan();
            var entry = plan.entry();
            var exact = new BuilderStructureScanner.StructurePlan(new BuilderStructureCatalog.Entry(entry.id(), entry.category(), entry.label(), entry.baseCost(), BuilderConstructionCost.EXACT, ""),
                    plan.rotation(), plan.templateSize(), plan.localMin(), plan.localMax(), plan.blocks(), plan.materials(), plan.price(), plan.clearingBlocks());
            SettlementTestPlots.planSupplies(level, town);
            helper.assertTrue(SettlementConstructionMaterials.reserve(level, town, project, exact, fixture.builder()), "construction reserves the plank");
            helper.assertTrue(fixture.depot().isEmpty(), "the reserved plank is no longer in the depot");
            helper.assertTrue(town.configureDepot(town.leader(), fixture.depot().id(), SettlementDepotPurpose.FOOD, 5), "the source now rejects construction materials");
            helper.assertTrue(SettlementConstructionService.cancel(player, town, project.id()), "construction is cancelled");
            helper.assertTrue(SettlementConstructionMaterials.refundPending(project), "the cancelled project retains its unreturned material");
            helper.assertFalse(project.site().unsettled(), "there are no pending terrain changes");
            var lifecycle = new Fixture(player, town);
            var blocked = preview(lifecycle, SettlementLifecycleService.Operation.DISSOLVE, null, null);
            helper.assertTrue(blocked.token() == null && !blocked.blockers().isEmpty(), "unreturned construction materials block dissolution");
            helper.assertTrue(SettlementSavedData.get(level).get(town.id()).orElseThrow() == town, "the town remains managed");
            var restored = SettlementRecord.load(town.save(level.registryAccess()), level.registryAccess(), () -> {});
            helper.assertTrue(SettlementConstructionMaterials.refundPending(restored.projects().get(project.id())), "the blocked refund survives reload");
            helper.assertTrue(town.configureDepot(town.leader(), fixture.depot().id(), SettlementDepotPurpose.MATERIALS, 5), "the source accepts materials again");
            SettlementConstructionMaterials.refund(level, town, project);
            helper.assertFalse(SettlementConstructionMaterials.refundPending(project), "the material is returned before dissolution");
            helper.assertValueEqual(fixture.depot().removeItem(0, 1).getCount(), 1, "the returned plank can be collected exactly once");
            var ready = preview(lifecycle, SettlementLifecycleService.Operation.DISSOLVE, null, null);
            helper.assertTrue(ready.token() != null, "collecting the refund makes dissolution available");
            helper.assertValueEqual(confirm(lifecycle, ready), "", "dissolution succeeds after returning the material");
            helper.assertTrue(SettlementSavedData.get(level).get(town.id()).isEmpty(), "the settled town can be retired");
        });
    }

    @GameTest(template = "empty", batch = "settlement_lifecycle")
    public static void mergePreservesPurchasePolicyBudgetsThroughReload(GameTestHelper helper) {
        var fixture = fixture(helper);
        var source = fixture.town();
        var target = neighbor(helper, source, source.center().east(48));
        var sourcePolicy = UUID.randomUUID();
        var targetPolicy = UUID.randomUUID();
        var limit = CurrencyAmount.of(10, 1);
        var targetSpent = CurrencyAmount.of(10, 3);
        long now = helper.getLevel().getGameTime();
        for (var town : List.of(source, target)) {
            town.economy().beginTradeCycle(now);
            town.economy().credit(CurrencyAmount.of(100, 1));
            town.economy().configureBudget(CurrencyAmount.ZERO, CurrencyAmount.of(100, 1));
        }
        var group = ResourceLocation.withDefaultNamespace("stone");
        source.economy().recordPolicyPurchase(sourcePolicy, limit, group, 10, limit);
        target.economy().recordPolicyPurchase(targetPolicy, limit, group, 1, targetSpent);
        helper.assertTrue(source.economy().availablePolicyBudget(sourcePolicy, limit).isZero(), "the source policy has exhausted its daily allowance");
        var preview = preview(fixture, SettlementLifecycleService.Operation.MERGE, target, null);
        helper.assertTrue(preview.token() != null, "the towns can merge with purchase accounts");
        helper.assertValueEqual(confirm(fixture, preview), "", "the merger commits");
        var merged = SettlementSavedData.get(helper.getLevel()).get(target.id()).orElseThrow();
        var restored = SettlementRecord.load(merged.save(helper.getLevel().registryAccess()), helper.getLevel().registryAccess(), () -> {});
        for (var town : List.of(merged, restored)) {
            helper.assertValueEqual(town.economy().policySpending(sourcePolicy), limit, "the source policy keeps its spending after merger and reload");
            helper.assertValueEqual(town.economy().policySpending(targetPolicy), targetSpent, "the target policy retains exact fractional spending");
            helper.assertValueEqual(town.economy().spent(), limit.add(targetSpent), "aggregate spending matches both policy accounts");
            helper.assertTrue(town.economy().availablePolicyBudget(sourcePolicy, limit).isZero(), "merging cannot reopen the exhausted allowance");
            helper.assertValueEqual(town.economy().availablePolicyBudget(targetPolicy, limit), limit.subtract(targetSpent), "the target keeps only its unspent allowance");
        }
        helper.assertTrue(restored.economy().beginTradeCycle((Math.floorDiv(now, 24_000L) + 1L) * 24_000L), "the next day starts a new trade period");
        helper.assertValueEqual(restored.economy().availablePolicyBudget(sourcePolicy, limit), limit, "the source allowance renews on the next day");
        helper.assertValueEqual(restored.economy().availablePolicyBudget(targetPolicy, limit), limit, "the target allowance renews on the next day");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_lifecycle")
    public static void emptyDissolutionRequiresPermissionAndConfirmationAndSurvivesRestart(GameTestHelper helper) throws Exception {
        var fixture = fixture(helper);
        var town = fixture.town();
        var level = helper.getLevel();
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var beforeRegistry = registry.save(new CompoundTag(), level.registryAccess());
        var denied = SettlementLifecycleService.preview(level.getServer().createCommandSourceStack().withPermission(0),
                town.id(), SettlementLifecycleService.Operation.DISSOLVE, null, null);
        helper.assertTrue(denied.token() == null, "unauthorized actors cannot preview dissolution");
        var preview = preview(fixture, SettlementLifecycleService.Operation.DISSOLVE, null, null);
        helper.assertTrue(preview.token() != null, "an empty town can be dissolved");
        helper.assertTrue(SettlementSavedData.get(level).get(town.id()).isPresent(), "preview never mutates a town");
        helper.assertValueEqual(confirm(fixture, preview), "", "confirmed dissolution succeeds");
        helper.assertTrue(SettlementSavedData.get(level).get(town.id()).isEmpty(), "the town leaves active selection");
        helper.assertFalse(registry.isManaged(town.id()), "ordinary village lifecycle resumes");
        helper.assertTrue(SettlementSavedData.get(level).owner(town.dimension(), new ChunkPos(town.center()).x, new ChunkPos(town.center()).z).isEmpty(),
                "ownership indexes release the plots");
        helper.assertFalse(confirm(fixture, preview).isEmpty(), "replaying the token cannot repeat dissolution");
        var disk = NbtIo.readCompressed(level.getServer().getWorldPath(LevelResource.ROOT).resolve("data/villagerretaliation_settlements.dat"), NbtAccounter.unlimitedHeap());
        var restored = SettlementSavedData.load(disk.getCompound("data"), level.registryAccess());
        var oldRegistry = VillageAllegianceRegistrySavedData.load(beforeRegistry, level.registryAccess());
        restored.restoreRegistry(oldRegistry);
        helper.assertFalse(oldRegistry.isManaged(town.id()), "the durable journal repairs a registry saved before the operation");
        helper.assertTrue(restored.get(town.id()).isEmpty(), "restart does not resurrect a retired town");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_lifecycle")
    public static void plotReleaseChecksOccupancyAndStaleConfirmations(GameTestHelper helper) {
        var fixture = fixture(helper);
        var town = fixture.town();
        var center = new ChunkPos(town.center());
        var position = new BlockPos((center.x - 1) * 16, 64, (center.z - 1) * 16);
        var preview = preview(fixture, SettlementLifecycleService.Operation.RELEASE, null, position);
        helper.assertTrue(preview.token() != null, "an empty corner plot can be released");
        town.advance();
        helper.assertFalse(confirm(fixture, preview).isEmpty(), "territory revisions invalidate old previews");
        preview = preview(fixture, SettlementLifecycleService.Operation.RELEASE, null, position);
        int credits = town.territory().freeClaims();
        helper.assertValueEqual(confirm(fixture, preview), "", "fresh release succeeds");
        var updated = SettlementSavedData.get(helper.getLevel()).get(town.id()).orElseThrow();
        helper.assertFalse(updated.contains(position), "the released column leaves the town");
        helper.assertValueEqual(updated.territory().freeClaims(), credits, "release cannot farm free credits");
        helper.assertTrue(SettlementSavedData.get(helper.getLevel()).owner(town.dimension(), center.x - 1, center.z - 1).isEmpty(),
                "the index matches the saved territory");
        helper.assertTrue(preview(new Fixture(fixture.player(), updated), SettlementLifecycleService.Operation.RELEASE, null, town.center()).token() == null,
                "the management center is protected");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_lifecycle")
    public static void relocationPreservesCreditsAndRejectsOtherOwners(GameTestHelper helper) {
        var fixture = fixture(helper);
        var town = fixture.town();
        var target = neighbor(helper, town, town.center().east(96));
        helper.assertTrue(preview(fixture, SettlementLifecycleService.Operation.RELOCATE, null, target.center()).token() == null,
                "relocation cannot seize another town's plots");
        var destination = town.center().south(160);
        var preview = preview(fixture, SettlementLifecycleService.Operation.RELOCATE, null, destination);
        helper.assertTrue(preview.token() != null, "an empty town can relocate its claims");
        helper.assertValueEqual(confirm(fixture, preview), "", "relocation commits");
        var updated = SettlementSavedData.get(helper.getLevel()).get(town.id()).orElseThrow();
        helper.assertValueEqual(updated.center(), destination, "the new center persists");
        helper.assertFalse(updated.contains(town.center()), "old claims are released");
        helper.assertValueEqual(updated.territory().plots().size(), town.territory().plots().size(), "no plots are created");
        helper.assertValueEqual(updated.territory().freeClaims(), town.territory().freeClaims(), "credits do not reset");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_lifecycle")
    public static void populatedCenterRelocationLeavesFacilitiesInPlace(GameTestHelper helper) {
        var fixture = fixture(helper);
        var town = fixture.town();
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.HOME, "Home", town.center(), town.center().offset(2, 2, 2), 5, true);
        helper.assertTrue(town.putArea(town.leader(), area), "the town has a facility");
        helper.assertTrue(preview(fixture, SettlementLifecycleService.Operation.RELOCATE, null, town.center().east(160)).token() == null,
                "facilities cannot be abandoned by relocating claims");
        var preview = preview(fixture, SettlementLifecycleService.Operation.RELOCATE, null, town.center().east(16));
        helper.assertValueEqual(confirm(fixture, preview), "", "the management center can move inside owned plots");
        var updated = SettlementSavedData.get(helper.getLevel()).get(town.id()).orElseThrow();
        helper.assertValueEqual(updated.areas().get(area.id()), area, "facilities are never teleported");
        helper.assertValueEqual(updated.territory().plots(), town.territory().plots(), "existing territory remains intact");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_lifecycle")
    public static void mergeConservesTreasuryAndDepotAssetsAndCanonicalIdentity(GameTestHelper helper) {
        var fixture = fixture(helper);
        var town = fixture.town();
        var target = neighbor(helper, town, town.center().east(48));
        town.economy().credit(CurrencyAmount.of(7, 1));
        target.economy().credit(CurrencyAmount.of(11, 1));
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.DEPOT, "Store", town.center(), town.center().offset(2, 2, 2), 5, true);
        town.putArea(town.leader(), area);
        var depot = town.registerDepot(town.leader(), area.id(), area.min(), SettlementDepotPurpose.GENERAL);
        depot.setItem(0, new ItemStack(Items.DIAMOND, 3));
        UUID delegate = UUID.randomUUID();
        town.setPermissions(town.leader(), delegate, Set.of(SettlementPermission.TREASURY));
        var beforeRegistry = VillageAllegianceRegistrySavedData.get(helper.getLevel()).save(new CompoundTag(), helper.getLevel().registryAccess());
        var preview = preview(fixture, SettlementLifecycleService.Operation.MERGE, target, null);
        helper.assertTrue(preview.token() != null, "adjacent towns with the same leader can merge");
        helper.assertValueEqual(confirm(fixture, preview), "", "merge commits");
        var data = SettlementSavedData.get(helper.getLevel());
        var merged = data.get(target.id()).orElseThrow();
        helper.assertTrue(data.get(town.id()).orElseThrow() == merged, "the source becomes an alias to the target");
        helper.assertFalse(SettlementManagementService.execute(fixture.player(),
                new com.jvn.villagerretaliation.network.SettlementManagementActionPayload(town.id().value(),
                        new com.jvn.villagerretaliation.network.SettlementManagementActionPayload.Management(true))),
                "an old source management screen cannot mutate the merged target");
        helper.assertFalse(data.settlements().stream().anyMatch(value -> value.id().equals(town.id())), "the source no longer consumes a simulation slot");
        helper.assertValueEqual(merged.economy().balance(), CurrencyAmount.of(18, 1), "treasury is conserved exactly");
        helper.assertValueEqual(merged.depots().get(depot.id()).getItem(0).getCount(), 3, "depot assets move once under their existing identity");
        helper.assertFalse(merged.permits(delegate, SettlementPermission.TREASURY), "source delegates do not acquire target authority");
        var restored = SettlementSavedData.load(data.save(new CompoundTag(), helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
        var staleRegistry = VillageAllegianceRegistrySavedData.load(beforeRegistry, helper.getLevel().registryAccess());
        restored.restoreRegistry(staleRegistry);
        helper.assertValueEqual(staleRegistry.canonical(town.id()).orElseThrow(), target.id(), "restart completes an unfinished registry handoff");
        helper.assertValueEqual(restored.get(town.id()).orElseThrow().economy().balance(), CurrencyAmount.of(18, 1), "restart cannot duplicate money");
        helper.assertFalse(confirm(fixture, preview).isEmpty(), "a merge confirmation cannot replay");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_lifecycle")
    public static void mergeRejectsAnotherLeaderAndConflictingAssetIdentities(GameTestHelper helper) {
        var fixture = fixture(helper);
        var target = neighbor(helper, fixture.town(), fixture.town().center().east(48));
        target.recoverLeadership(fixture.player().createCommandSourceStack().withPermission(2), UUID.randomUUID());
        helper.assertTrue(preview(fixture, SettlementLifecycleService.Operation.MERGE, target, null).token() == null,
                "owning one town never authorizes taking another");
        var admin = fixture.player().createCommandSourceStack().withPermission(2);
        var allowed = SettlementLifecycleService.preview(admin, fixture.town().id(), SettlementLifecycleService.Operation.MERGE, target.id(), null);
        helper.assertTrue(allowed.token() != null, "an administrator can preview a managed merger");
        UUID shared = UUID.randomUUID();
        fixture.town().putArea(fixture.town().leader(), new SettlementArea(shared, SettlementAreaType.HOME, "One",
                fixture.town().center(), fixture.town().center().offset(2, 2, 2), 5, true));
        target.putArea(target.leader(), new SettlementArea(shared, SettlementAreaType.HOME, "Two",
                target.center(), target.center().offset(2, 2, 2), 5, true));
        helper.assertTrue(SettlementLifecycleService.preview(admin, fixture.town().id(), SettlementLifecycleService.Operation.MERGE,
                target.id(), null).token() == null, "duplicated facility identities must be resolved before merging");
        helper.assertFalse(SettlementLifecycleService.confirm(admin, allowed.token()).isEmpty(), "confirmation rechecks asset conflicts");
        var disconnected = neighbor(helper, fixture.town(), fixture.town().center().south(160));
        helper.assertTrue(preview(fixture, SettlementLifecycleService.Operation.MERGE, disconnected, null).token() == null,
                "mergers cannot produce disconnected territory");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_lifecycle", timeoutTicks = 200)
    public static void dissolutionPreservesResidentInventoryAndAllegiance(GameTestHelper helper) {
        var fixture = SettlementPopulationGameTests.fixture(helper);
        fixture.depot().clearContent();
        var level = helper.getLevel();
        var resident = EntityType.VILLAGER.create(level);
        resident.setPos(fixture.town().center().getCenter());
        resident.setNoAi(true);
        resident.getInventory().setItem(0, new ItemStack(Items.BREAD, 5));
        level.addFreshEntity(resident);
        VillageAllegianceApi.assignKnown(level, resident, fixture.town().id(), AllegianceAssignmentSource.EXPLICIT_API);
        fixture.town().recordResident(SettlementResidentState.capture(resident, fixture.town(), level.getGameTime()));
        var wrapper = new Fixture(fixture.leader(), fixture.town());
        var preview = preview(wrapper, SettlementLifecycleService.Operation.DISSOLVE, null, null);
        helper.assertTrue(preview.token() != null, "loaded residents can return to an independent village");
        helper.assertValueEqual(confirm(wrapper, preview), "", "dissolution completes native handoff");
        helper.assertTrue(resident.isAlive(), "dissolution never deletes residents");
        helper.assertValueEqual(resident.getInventory().getItem(0).getCount(), 5, "resident inventory survives");
        helper.assertValueEqual(VillageAllegianceApi.canonicalPrimary(level, resident).orElseThrow(), fixture.town().id(), "resident identity stays with its village");
        resident.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_lifecycle")
    public static void mergerPreservesActiveConstructionPendingPlacementsAndPayments(GameTestHelper helper) {
        SettlementBackgroundConstructionGameTests.fixture(helper, true, null, fixture -> {
            var level = helper.getLevel();
            var town = fixture.town();
            town.economy().credit(CurrencyAmount.of(20, 1));
            town.rememberManualDuty(fixture.builder().getUUID());
            UUID delegate = UUID.randomUUID(), poolId = UUID.randomUUID();
            town.setPermissions(town.leader(), delegate, Set.of(SettlementPermission.STAFF));
            helper.assertTrue(town.configureWorkerPool(delegate, poolId, -1,
                    new SettlementWorkerPool.Configuration("Construction crew", List.of(fixture.builder().getUUID()),
                            List.of(new SettlementWorkerPool.Staffing(fixture.area().id(), 0, 1))), true),
                    "the source delegate authorizes an explicitly enrolled pool");
            long pinRevision = town.manualDutyRevision(fixture.builder().getUUID());
            long poolRevision = town.workerPools().get(poolId).revision();
            fixture.project().reservePayment(5);
            fixture.project().earnPayment();
            var placement = new SettlementConstructionPlacement(List.of(new SettlementConstructionPlacement.Part(
                    fixture.origin(), Blocks.AIR.defaultBlockState(), Blocks.OAK_PLANKS.defaultBlockState(), null)));
            fixture.site().queue(placement);
            var target = neighbor(helper, town, town.center().east(48));
            helper.assertTrue(town.revision() > target.revision(), "the source has newer staffing records than the target");
            var source = level.getServer().createCommandSourceStack().withPermission(2);
            var blocked = SettlementLifecycleService.preview(source, town.id(), SettlementLifecycleService.Operation.DISSOLVE, null, null);
            helper.assertTrue(blocked.token() == null, "active work, debt and pending placements block dissolution");
            var preview = SettlementLifecycleService.preview(source, town.id(), SettlementLifecycleService.Operation.MERGE, target.id(), null);
            helper.assertTrue(preview.token() != null, "pending work can move intact in a merger: " + preview.blockers());
            helper.assertValueEqual(SettlementLifecycleService.confirm(source, preview.token()), "", "the managed merger succeeds");
            var merged = SettlementSavedData.get(level).get(target.id()).orElseThrow();
            var project = merged.projects().get(fixture.project().id());
            helper.assertTrue(merged.revision() > poolRevision, "the merged revision covers the source staffing records");
            helper.assertValueEqual(merged.manualDutyRevision(fixture.builder().getUUID()), pinRevision, "the pin keeps its observed revision");
            var pool = merged.workerPools().get(poolId);
            helper.assertValueEqual(pool.configuration().residents(), List.of(fixture.builder().getUUID()), "explicit enrollment survives merger");
            helper.assertValueEqual(pool.state(), SettlementWorkerPool.State.SUSPENDED, "revoked source delegates cannot keep pools active");
            helper.assertValueEqual(pool.blocker(), "pool_permission_changed", "the merged pool explains its lost authority");
            helper.assertTrue(pool.revision() > poolRevision, "suspension invalidates pre-merger pool edits");
            var player = FakePlayerFactory.get(level, new GameProfile(town.leader(), "PoolMergeLeader"));
            var responses = new java.util.ArrayList<com.jvn.villagerretaliation.network.SettlementWorkerPoolSnapshotPayload>();
            var request = new com.jvn.villagerretaliation.network.SettlementWorkerPoolRequestPayload(town.id().value(), UUID.randomUUID(),
                    poolId, poolRevision, "", 0, new com.jvn.villagerretaliation.network.SettlementWorkerPoolRequestPayload.View());
            var inspection = SettlementWorkerPoolManagementService.prepare(player, request, responses::add);
            do { inspection.run(); } while (!SettlementWork.complete(inspection));
            helper.assertValueEqual(responses.getFirst().feedback(), "town_missing", "old source pool screens cannot inspect the merged target");
            helper.assertTrue(responses.getFirst().pools().isEmpty(), "stale source requests reveal no merged pool state");
            helper.assertTrue(project.active() && project.site().pending(), "active work and pending world actions survive");
            helper.assertTrue(project.paymentEarned() && !project.paymentDelivered() && project.fee() == 5, "earned payment remains owed exactly once");
            helper.assertValueEqual(project.town(), target.id().value(), "project authority follows the new canonical town");
            helper.assertTrue(merged.workers().containsKey(fixture.builder().getUUID()), "unloaded worker state is preserved");
            var restored = SettlementRecord.load(merged.save(level.registryAccess()), level.registryAccess(), () -> {});
            helper.assertTrue(restored.projects().get(project.id()).site().pending(), "pending receipts remain after restart");
            helper.assertTrue(restored.manuallyStaffed(fixture.builder().getUUID()), "manual staffing survives merger and reload");
            helper.assertTrue(restored.returnToAutomaticStaffing(restored.leader(), fixture.builder().getUUID()),
                    "the merged town can restore automatic staffing");
            helper.assertFalse(restored.manuallyStaffed(fixture.builder().getUUID()), "the merged override is reversible");
            SettlementWorkforceService.reconcile(level, fixture.builder());
            helper.assertValueEqual(SettlementWorkforceService.duty(fixture.builder()).orElseThrow().town().id(), target.id(),
                    "an unloaded source worker returns to the transferred duty");
            helper.assertTrue(project.active(), "canonical rebinding does not cancel the active construction project");
            int wallet = VillagerWalletService.getCurrentCurrency(fixture.builder());
            SettlementConstructionService.synchronizePayments(merged, fixture.builder());
            helper.assertTrue(project.paymentDelivered(), "the merged project records native payment delivery");
            SettlementConstructionService.synchronizePayments(restored, fixture.builder());
            helper.assertValueEqual(VillagerWalletService.getCurrentCurrency(fixture.builder()), wallet + 5,
                    "a pre-payment settlement snapshot cannot pay the same native builder twice");
            level.setBlock(fixture.origin(), Blocks.AIR.defaultBlockState(), 2);
            helper.assertTrue(placement.reconcile(level, project.id()), "the merged project delivers its pending placement");
            level.setBlock(fixture.origin(), Blocks.DIAMOND_BLOCK.defaultBlockState(), 2);
            helper.assertTrue(SettlementConstructionPlacement.load(placement.save()).reconcile(level, project.id()),
                    "the original receipt remains authoritative after merging");
            helper.assertTrue(level.getBlockState(fixture.origin()).is(Blocks.DIAMOND_BLOCK),
                    "replayed merged work preserves later player edits");
        });
    }

    @GameTest(template = "empty", batch = "settlement_lifecycle")
    public static void dissolvedMergeLineageReplaysAgainstAnOlderRegistry(GameTestHelper helper) {
        var fixture = fixture(helper);
        var first = fixture.town();
        var second = neighbor(helper, first, first.center().east(48));
        var third = neighbor(helper, first, first.center().east(96));
        var registry = VillageAllegianceRegistrySavedData.get(helper.getLevel());
        var before = registry.save(new CompoundTag(), helper.getLevel().registryAccess());
        var admin = fixture.player().createCommandSourceStack().withPermission(2);
        var recovery = SettlementOwnershipService.previewRecovery(admin, first, UUID.randomUUID());
        helper.assertValueEqual(confirm(fixture, preview(fixture, SettlementLifecycleService.Operation.MERGE, second, null)),
                "", "the first merger succeeds");
        helper.assertFalse(SettlementOwnershipService.confirmRecovery(admin, recovery), "an old recovery cannot change the merged target owner");
        var data = SettlementSavedData.get(helper.getLevel());
        var middle = new Fixture(fixture.player(), data.get(second.id()).orElseThrow());
        helper.assertValueEqual(confirm(middle, preview(middle, SettlementLifecycleService.Operation.MERGE, third, null)),
                "", "the second merger succeeds");
        var last = new Fixture(fixture.player(), data.get(third.id()).orElseThrow());
        helper.assertValueEqual(confirm(last, preview(last, SettlementLifecycleService.Operation.DISSOLVE, null, null)),
                "", "the merged town dissolves");
        var restored = SettlementSavedData.load(data.save(new CompoundTag(), helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
        var stale = VillageAllegianceRegistrySavedData.load(before, helper.getLevel().registryAccess());
        restored.restoreRegistry(stale);
        helper.assertValueEqual(stale.canonical(first.id()).orElseThrow(), third.id(), "the oldest identity retains its lineage");
        helper.assertValueEqual(stale.canonical(second.id()).orElseThrow(), third.id(), "the intermediate identity retains its lineage");
        helper.assertFalse(stale.isManaged(third.id()), "the final independent village has no managed claims");
        helper.assertTrue(restored.get(first.id()).isEmpty(), "dissolution does not resurrect the merged town");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_lifecycle")
    public static void mergeKeepsOneNavigationObservationPerSection(GameTestHelper helper) {
        var fixture = fixture(helper);
        var source = fixture.town();
        var target = neighbor(helper, source, source.center().east(48));
        var level = helper.getLevel();
        var position = helper.absolutePos(new BlockPos(8, 2, 8));
        var origin = SettlementTerrainSection.sectionOrigin(position);
        var chunk = level.getChunkAt(position);
        source.recordNavigation(SettlementNavigationSection.capture(origin, chunk));
        var unique = origin.above(16);
        source.recordNavigation(SettlementNavigationSection.capture(unique, chunk));
        level.setBlock(position, Blocks.GOLD_BLOCK.defaultBlockState(), 3);
        target.recordNavigation(SettlementNavigationSection.capture(origin, chunk));
        var merged = SettlementRecord.load(SettlementLifecyclePlan.merge(level, source, target), level.registryAccess(), () -> {});
        helper.assertValueEqual(merged.navigation().size(), 2, "merging retains unique sections without duplicating shared origins");
        helper.assertTrue(merged.constructionQueueRevision() > Math.max(source.constructionQueueRevision(), target.constructionQueueRevision()),
                "merging invalidates queue commands captured in either town");
        helper.assertTrue(merged.navigation().get(origin).state(position).is(Blocks.GOLD_BLOCK), "the destination observation wins conflicts");
        helper.succeed();
    }

    private static Fixture fixture(GameTestHelper helper) {
        var level = helper.getLevel();
        var origin = helper.absolutePos(BlockPos.ZERO);
        var center = new BlockPos(-12_000_000 + origin.getX() * 64 + 8, 80, -12_000_000 + origin.getZ() * 64 + 8);
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "LifecycleLeader"));
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(0, level.dimension().location(), center, "");
        var town = SettlementSavedData.get(level).establish(registry, id, player.getUUID(), center, 0).orElseThrow();
        return new Fixture(player, town);
    }

    private static SettlementRecord neighbor(GameTestHelper helper, SettlementRecord town, BlockPos center) {
        var registry = VillageAllegianceRegistrySavedData.get(helper.getLevel());
        var id = registry.create(0, town.dimension(), center, "");
        return SettlementSavedData.get(helper.getLevel()).establish(registry, id, town.leader(), center, 0).orElseThrow();
    }

    private static SettlementLifecycleService.Preview preview(Fixture fixture, SettlementLifecycleService.Operation operation, SettlementRecord target, BlockPos position) {
        return SettlementLifecycleService.preview(fixture.player().createCommandSourceStack().withPermission(0), fixture.town().id(),
                operation, target == null ? null : target.id(), position);
    }

    private static String confirm(Fixture fixture, SettlementLifecycleService.Preview preview) {
        return SettlementLifecycleService.confirm(fixture.player().createCommandSourceStack().withPermission(0), preview.token());
    }

    private record Fixture(ServerPlayer player, SettlementRecord town) {
    }
}
