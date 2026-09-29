package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.interaction.HiredVillagerContractService;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredWorkSession;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureCatalog;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureScanner;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderTaskState;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderWorker;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.item.ConstructionBlueprintItem;
import com.jvn.villagerretaliation.network.SettlementManagementActionPayload;
import com.jvn.villagerretaliation.network.SettlementManagementPayload;
import com.jvn.villagerretaliation.network.SettlementManagementRequestPayload;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SettlementConstructionGameTests {
    private SettlementConstructionGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_construction")
    public static void projectIssuesLinkBuildersAndReuseAuthorizedCancellation(GameTestHelper helper) {
        var fixture = fixture(helper);
        var town = fixture.town();
        var level = helper.getLevel();
        try {
            town.advance();
            var project = begin(helper, fixture);
            var state = HiredWorkStateStore.state(fixture.builder());
            BuilderTaskState.setBlocked(state, "missing_materials");
            BuilderTaskState.setMissingMaterials(state, "Oak Planks x12");
            SettlementConstructionService.observe(fixture.builder(), state);
            var request = new SettlementManagementRequestPayload(town.id().value(),
                    SettlementManagementRequestPayload.Page.ISSUES, 0, project.id().toString(), 5, false);
            var page = SettlementManagementService.snapshot(fixture.leader(), request);
            var issue = (SettlementManagementPayload.Issue) page.entries().getFirst();
            helper.assertValueEqual(issue.kind(), SettlementManagementPayload.IssueKind.PROJECT, "active blockers become project issues");
            helper.assertValueEqual(issue.detail(), "Oak Planks x12", "the issue includes missing materials");
            helper.assertValueEqual(issue.targetId(), project.id(), "navigation identifies the project");
            helper.assertValueEqual(issue.resident(), fixture.builder().getUUID(), "builder access identifies the resident");
            helper.assertValueEqual(issue.area(), fixture.area().id(), "the construction area remains reachable");
            var restored = SettlementRecord.load(town.save(level.registryAccess()), level.registryAccess(), () -> {});
            helper.assertTrue(SettlementIssueService.issues(level, restored, SettlementProgressionService.assess(level, restored))
                    .stream().anyMatch(issue::equals), "the blocked project survives reload without replaying construction");
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
            try {
                SettlementManagementPayload.STREAM_CODEC.encode(buffer, page);
                helper.assertValueEqual(SettlementManagementPayload.STREAM_CODEC.decode(buffer), page, "project issue fields round trip");
            } finally {
                buffer.release();
            }
            var delegate = player(helper, "IssueBuilder");
            town.setPermissions(fixture.leader().getUUID(), delegate.getUUID(), Set.of(SettlementPermission.STAFF));
            var cancel = new SettlementManagementActionPayload(town.id().value(),
                    new SettlementManagementActionPayload.CancelProject(issue.targetId()));
            helper.assertFalse(SettlementManagementService.execute(delegate, cancel), "staff controls do not grant construction cancellation");
            var builderPage = SettlementManagementService.snapshot(delegate, new SettlementManagementRequestPayload(town.id().value(),
                    SettlementManagementRequestPayload.Page.RESIDENTS, 0, issue.resident().toString(), 6, false));
            helper.assertValueEqual(builderPage.entries().getFirst().id(), fixture.builder().getUUID(), "the builder link resolves exactly");
            BuilderTaskState.setBlocked(state, "site_unloaded");
            SettlementConstructionService.observe(fixture.builder(), state);
            var observationPage = SettlementManagementService.snapshot(fixture.leader(), request);
            helper.assertValueEqual(observationPage.total(), 1, "awaiting site observation remains visible");
            var observationIssue = (SettlementManagementPayload.Issue) observationPage.entries().getFirst();
            helper.assertValueEqual(observationIssue.cause(), "site_unloaded", "observation replaces retained material warnings");
            helper.assertValueEqual(observationIssue.diagnostic().severity(), SettlementWorkerDiagnostics.Severity.OBSERVATION,
                    "unloaded sites explain missing observation");
            helper.assertTrue(observationIssue.needsAttention(), "unobserved projects remain actionable");
            BuilderTaskState.setBlocked(state, "blocked_existing");
            SettlementConstructionService.observe(fixture.builder(), state);
            helper.assertValueEqual(SettlementManagementService.snapshot(fixture.leader(), request).total(), 1, "a real obstruction reappears");
            town.setPermissions(fixture.leader().getUUID(), delegate.getUUID(), Set.of(SettlementPermission.BUILD));
            helper.assertTrue(SettlementManagementService.execute(delegate, cancel), "build delegates use the existing cancellation action");
            helper.assertValueEqual(project.status(), SettlementProject.Status.CANCELLED, "cancellation updates the real project");
            helper.assertValueEqual(SettlementManagementService.snapshot(fixture.leader(), request).total(), 0, "cancelled project blockers disappear");
            helper.assertFalse(SettlementManagementService.execute(delegate, cancel), "a stale cancellation cannot replay");
        } finally {
            fixture.builder().discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_construction")
    public static void commissioningRequiresBuildPermissionStageAndAssignedBuilder(GameTestHelper helper) {
        var fixture = fixture(helper);
        var delegate = player(helper, "ProjectDelegate");
        try {
            var blueprint = blueprint(helper, fixture, fixture.origin(), UUID.randomUUID());
            delegate.setItemInHand(InteractionHand.MAIN_HAND, blueprint.copy());
            var id = ConstructionBlueprintItem.previewData(blueprint).orElseThrow().jobId();
            helper.assertFalse(start(delegate, fixture, id), "outsiders cannot commission projects");
            fixture.town().setPermissions(fixture.leader().getUUID(), delegate.getUUID(), Set.of(SettlementPermission.STAFF));
            helper.assertFalse(start(delegate, fixture, id), "staff permission does not grant construction");
            fixture.town().setPermissions(fixture.leader().getUUID(), delegate.getUUID(), Set.of(SettlementPermission.BUILD));
            helper.assertFalse(start(delegate, fixture, id), "Camp cannot commission civic projects");
            fixture.town().advance();
            helper.assertTrue(start(delegate, fixture, id), "a construction delegate can commission at Hamlet");
            helper.assertFalse(HiredVillagerContractService.hasActiveOrPendingContract(fixture.builder()), "civic construction does not create a paid contract");
            helper.assertValueEqual(BuilderTaskState.paidCurrency(HiredWorkStateStore.state(fixture.builder())), 0, "civic construction has no wage escrow");
            helper.assertFalse(start(delegate, fixture, id), "repeating a command cannot start the same blueprint again");
            fixture.town().setPermissions(fixture.leader().getUUID(), delegate.getUUID(), Set.of());
            helper.assertFalse(SettlementConstructionService.cancel(delegate, fixture.town(), id), "revoked delegates cannot cancel projects");
            helper.assertTrue(SettlementConstructionService.cancel(fixture.leader(), fixture.town(), id), "the current leader can cancel");
            fixture.leader().setItemInHand(InteractionHand.MAIN_HAND, blueprint.copy());
            helper.assertFalse(start(fixture.leader(), fixture, id), "a stale unused copy cannot replay a cancelled blueprint");
        } finally {
            fixture.builder().discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_construction")
    public static void projectCapacityCountsOnlyActiveProjectsAndPreventsAreaEscape(GameTestHelper helper) {
        var fixture = fixture(helper);
        try {
            fixture.town().advance();
            var first = begin(helper, fixture);
            UUID secondBuilder = UUID.randomUUID();
            fixture.town().assignDuty(secondBuilder, fixture.area().id());
            var second = SettlementProject.create(fixture.town(), fixture.area().id(), secondBuilder, fixture.plan(),
                    fixture.origin(), UUID.randomUUID(), helper.getLevel().getGameTime());
            helper.assertFalse(fixture.town().startProject(fixture.leader().getUUID(), second), "Hamlet permits one active project");
            var narrowed = new SettlementArea(fixture.area().id(), SettlementAreaType.CONSTRUCTION, "Too small",
                    fixture.area().min(), fixture.origin(), 5, true);
            helper.assertFalse(fixture.town().putArea(fixture.leader().getUUID(), narrowed), "an active project prevents shrinking its area past its footprint");
            var expanded = new SettlementArea(fixture.area().id(), SettlementAreaType.CONSTRUCTION, "Expanded",
                    fixture.area().min().offset(-1, 0, -1), fixture.area().max().offset(1, 0, 1), 5, true);
            helper.assertTrue(fixture.town().putArea(fixture.leader().getUUID(), expanded), "safe area expansion remains available");
            SettlementWorkforceService.reconcile(helper.getLevel(), fixture.builder());
            helper.assertValueEqual(BuilderTaskState.jobId(HiredWorkStateStore.state(fixture.builder())).orElseThrow(), first.id(),
                    "safe area edits preserve the accepted task");
            helper.assertTrue(SettlementConstructionService.cancel(fixture.leader(), fixture.town(), first.id()), "cancellation releases capacity");
            helper.assertTrue(fixture.town().startProject(fixture.leader().getUUID(), second), "cancelled history does not consume active capacity");
            fixture.town().removeDuty(secondBuilder);
            helper.assertValueEqual(second.status(), SettlementProject.Status.CANCELLED, "releasing an unloaded builder cancels the project");
            helper.assertTrue(fixture.town().activeProject(secondBuilder) == null, "released builders have no active project");
        } finally {
            fixture.builder().discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_construction")
    public static void blueprintPlacementRechecksActualStructureAndAllThreeBounds(GameTestHelper helper) {
        var fixture = fixture(helper);
        try {
            fixture.town().advance();
            UUID id = UUID.randomUUID();
            fixture.leader().setItemInHand(InteractionHand.MAIN_HAND, blueprint(helper, fixture, fixture.area().max().above(), id));
            helper.assertFalse(start(fixture.leader(), fixture, id), "a blueprint outside the construction area is rejected");
            fixture.leader().setItemInHand(InteractionHand.MAIN_HAND, blueprint(helper, fixture,
                    new BlockPos(fixture.origin().getX(), fixture.area().max().getY(), fixture.origin().getZ()), id));
            helper.assertFalse(start(fixture.leader(), fixture, id), "vertical bounds are checked as well as the footprint");
            var valid = blueprint(helper, fixture, fixture.origin(), id);
            fixture.leader().setItemInHand(InteractionHand.MAIN_HAND, valid);
            helper.assertFalse(start(fixture.leader(), fixture, UUID.randomUUID()), "the actual held blueprint must match the requested identity");
            var obstructed = fixture.plan().worldPos(fixture.origin(), fixture.plan().blocks().getFirst());
            helper.getLevel().setBlockAndUpdate(obstructed, Blocks.BEDROCK.defaultBlockState());
            helper.assertFalse(start(fixture.leader(), fixture, id), "protected world blocks prevent commissioning");
            helper.getLevel().setBlockAndUpdate(obstructed, Blocks.AIR.defaultBlockState());
            helper.assertTrue(start(fixture.leader(), fixture, id), "the same blueprint succeeds once its real site is safe");
        } finally {
            fixture.builder().discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_construction")
    public static void projectSaveAndReloadPreserveProgressAndRejectOrphanedAuthority(GameTestHelper helper) {
        var fixture = fixture(helper);
        try {
            fixture.town().advance();
            var project = begin(helper, fixture);
            var state = HiredWorkStateStore.state(fixture.builder());
            BuilderTaskState.setPlacedIndex(state, 3);
            BuilderTaskState.setBlocked(state, "missing_materials");
            SettlementConstructionService.observe(fixture.builder(), state);
            CompoundTag saved = fixture.town().save(helper.getLevel().registryAccess());
            var restored = SettlementRecord.load(saved, helper.getLevel().registryAccess(), () -> {});
            helper.assertValueEqual(restored.projects().get(project.id()).placed(), 3, "project progress survives a town save");
            helper.assertValueEqual(restored.projects().get(project.id()).blocker(), "missing_materials", "blocked work survives a town save");
            BuilderTaskState.clearTask(state);
            SettlementConstructionService.reconcile(fixture.builder());
            helper.assertValueEqual(BuilderTaskState.placedIndex(state), 3, "a missing live task restores from the authoritative project");
            SettlementConstructionService.reconcile(fixture.builder());
            helper.assertValueEqual(BuilderTaskState.placedIndex(state), 3, "repeated restoration is idempotent");
            CompoundTag legacy = saved.copy();
            legacy.remove("Projects");
            helper.assertTrue(SettlementRecord.load(legacy, helper.getLevel().registryAccess(), () -> {}).projects().isEmpty(),
                    "older saves need no construction records");
            CompoundTag invalid = saved.copy();
            invalid.put("Duties", new ListTag());
            invalid.put("WorkerSettings", new ListTag());
            helper.assertTrue(rejects(helper, invalid), "active projects cannot load without their assigned worker");
            var duplicate = saved.getList("Projects", Tag.TAG_COMPOUND);
            duplicate.add(duplicate.getCompound(0).copy());
            helper.assertTrue(rejects(helper, saved), "duplicate project identities cannot load");
        } finally {
            fixture.builder().discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_construction")
    public static void removingConstructionAreaCancelsWorkWithoutConsumingSupplies(GameTestHelper helper) {
        var fixture = fixture(helper);
        try {
            fixture.town().advance();
            var project = begin(helper, fixture);
            var inventory = HiredJobInventory.getJobInventory(fixture.builder());
            inventory.setItem(HiredJobInventory.HOTBAR_START, new ItemStack(Items.OAK_PLANKS, 17));
            helper.assertTrue(fixture.town().removeArea(fixture.leader().getUUID(), fixture.area().id()), "the construction area can be removed");
            helper.assertValueEqual(project.status(), SettlementProject.Status.CANCELLED, "removal cancels its project");
            SettlementConstructionService.reconcile(fixture.builder());
            SettlementWorkforceService.reconcile(helper.getLevel(), fixture.builder());
            helper.assertFalse(BuilderTaskState.hasTask(HiredWorkStateStore.state(fixture.builder())), "cancelled authority cannot resume a live task");
            helper.assertValueEqual(inventory.getItem(HiredJobInventory.HOTBAR_START).getCount(), 17, "unused materials remain real and unchanged");
            helper.assertTrue(SettlementRecord.load(fixture.town().save(helper.getLevel().registryAccess()),
                    helper.getLevel().registryAccess(), () -> {}).projects().containsKey(project.id()), "cancelled history survives removal of its area");
        } finally {
            fixture.builder().discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_construction")
    public static void nativeBuilderConsumesOneRealMaterialThenCompletesTheProject(GameTestHelper helper) {
        var fixture = fixture(helper);
        try {
            fixture.town().advance();
            var project = begin(helper, fixture);
            var level = helper.getLevel();
            var plan = fixture.plan();
            int targetIndex = -1;
            for (int i = 0; i < plan.blocks().size(); i++) {
                var block = plan.blocks().get(i);
                if (block.requiresMaterial() && !block.requiresToolAction() && block.state().is(Blocks.OAK_PLANKS)) {
                    targetIndex = i;
                }
                level.setBlock(plan.worldPos(fixture.origin(), block), block.state(), 2);
            }
            helper.assertTrue(targetIndex >= 0, "the real house blueprint contains a plank placement");
            var target = plan.blocks().get(targetIndex);
            var position = plan.worldPos(fixture.origin(), target);
            level.setBlock(position, Blocks.AIR.defaultBlockState(), 2);
            var state = HiredWorkStateStore.state(fixture.builder());
            BuilderTaskState.setPlacedIndex(state, targetIndex);
            var inventory = HiredJobInventory.getJobInventory(fixture.builder());
            var worker = new BuilderWorker();
            var session = HiredWorkSession.create(level, fixture.builder(), HiredVillagerRole.BUILDER);
            worker.tick(level, fixture.builder(), null, session.context());
            helper.assertTrue(level.getBlockState(position).isAir(), "missing supplies cannot create a block");
            helper.assertFalse(project.paymentEarned(), "waiting for materials does not earn the fee");
            inventory.setItem(HiredJobInventory.HOTBAR_START, target.requiredItem().copyWithCount(1));
            var result = worker.tick(level, fixture.builder(), null, session.context());
            helper.assertTrue(level.getBlockState(position).equals(target.state()), "the native builder places the real requested block: " + result.status());
            helper.assertTrue(inventory.getItem(HiredJobInventory.HOTBAR_START).isEmpty(), "exactly one real supply is consumed");
            helper.assertTrue(project.paymentEarned(), "a committed native placement earns the reserved fee");
            for (int i = 0; i < 4 && project.active(); i++) {
                worker.tick(level, fixture.builder(), null, session.context());
            }
            helper.assertValueEqual(project.status(), SettlementProject.Status.COMPLETED, "native completion updates the settlement ledger");
            helper.assertValueEqual(project.placed(), project.total(), "completed progress is persistent");
            helper.assertFalse(BuilderTaskState.hasTask(state), "completion clears the live builder task");
            worker.tick(level, fixture.builder(), null, session.context());
            helper.assertTrue(inventory.getItem(HiredJobInventory.HOTBAR_START).isEmpty(), "repeated ticking cannot refund or duplicate consumed materials");
            helper.assertValueEqual(fixture.town().history().stream().filter(event -> event.type() == SettlementEvent.Type.PROJECT_STARTED).count(), 1L,
                    "commissioning enters history once");
            helper.assertValueEqual(fixture.town().history().stream().filter(event -> event.type() == SettlementEvent.Type.PROJECT_COMPLETED).count(), 1L,
                    "completion enters history once even after another worker tick");
        } finally {
            fixture.builder().discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_construction")
    public static void projectCommandsAndManagementSnapshotsRoundTrip(GameTestHelper helper) {
        var fixture = fixture(helper);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            fixture.town().advance();
            UUID id = UUID.randomUUID();
            fixture.leader().setItemInHand(InteractionHand.MAIN_HAND, blueprint(helper, fixture, fixture.origin(), id));
            var command = new SettlementManagementActionPayload(fixture.town().id().value(),
                    new SettlementManagementActionPayload.StartProject(fixture.builder().getUUID(), id));
            SettlementManagementActionPayload.STREAM_CODEC.encode(buffer, command);
            helper.assertValueEqual(SettlementManagementActionPayload.STREAM_CODEC.decode(buffer), command, "project commissioning has a typed codec");
            helper.assertTrue(SettlementManagementService.execute(fixture.leader(), command), "management commissions through the same authority checks");
            var snapshot = SettlementManagementService.snapshot(fixture.leader(), new SettlementManagementRequestPayload(
                    fixture.town().id().value(), SettlementManagementRequestPayload.Page.PROJECTS, 0, id.toString(), 1, false));
            helper.assertValueEqual(snapshot.entries().size(), 1, "projects can be retrieved by exact identity");
            SettlementManagementPayload.STREAM_CODEC.encode(buffer, snapshot);
            helper.assertValueEqual(SettlementManagementPayload.STREAM_CODEC.decode(buffer), snapshot, "project pages round trip with progress and materials");
            var cancel = new SettlementManagementActionPayload(fixture.town().id().value(), new SettlementManagementActionPayload.CancelProject(id));
            SettlementManagementActionPayload.STREAM_CODEC.encode(buffer, cancel);
            helper.assertValueEqual(SettlementManagementActionPayload.STREAM_CODEC.decode(buffer), cancel, "project cancellation has a typed codec");
            helper.assertTrue(SettlementManagementService.execute(fixture.leader(), cancel), "management cancellation uses construction permission");
        } finally {
            buffer.release();
            fixture.builder().discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_construction")
    public static void cancellingAnUnloadedProjectClearsStaleTasksWithoutChangingInventory(GameTestHelper helper) {
        var fixture = fixture(helper);
        Villager restored = null;
        try {
            fixture.town().advance();
            var project = begin(helper, fixture);
            HiredJobInventory.getJobInventory(fixture.builder()).setItem(HiredJobInventory.HOTBAR_START, new ItemStack(Items.OAK_PLANKS, 17));
            SettlementBackgroundService.onUnloaded(fixture.builder());
            CompoundTag entity = fixture.builder().saveWithoutId(new CompoundTag());
            fixture.builder().remove(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
            helper.assertTrue(helper.getLevel().getEntity(fixture.builder().getUUID()) == null, "the builder is actually unloaded");
            int chunks = helper.getLevel().getChunkSource().getLoadedChunksCount();
            helper.assertTrue(SettlementConstructionService.cancel(fixture.leader(), fixture.town(), project.id()), "unloaded projects can be cancelled");
            helper.assertValueEqual(helper.getLevel().getChunkSource().getLoadedChunksCount(), chunks, "cancellation does not load chunks");
            restored = EntityType.VILLAGER.create(helper.getLevel());
            restored.load(entity);
            helper.assertTrue(helper.getLevel().addFreshEntity(restored), "the saved builder returns");
            SettlementBackgroundService.onActive(restored);
            SettlementBackgroundService.onActive(restored);
            helper.assertFalse(BuilderTaskState.hasTask(HiredWorkStateStore.state(restored)), "the stale task cannot resume after cancellation");
            helper.assertValueEqual(HiredJobInventory.getJobInventory(restored).getItem(HiredJobInventory.HOTBAR_START).getCount(), 17,
                    "repeated reconciliation preserves the original materials exactly once");
        } finally {
            if (restored != null) {
                restored.discard();
            }
            fixture.builder().discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_construction")
    public static void concurrentProjectsReserveSeparateFootprints(GameTestHelper helper) {
        var fixture = fixture(helper);
        try {
            fixture.town().advance();
            fixture.town().advance();
            begin(helper, fixture);
            UUID builder = UUID.randomUUID();
            fixture.town().assignDuty(builder, fixture.area().id());
            var overlapping = SettlementProject.create(fixture.town(), fixture.area().id(), builder, fixture.plan(),
                    fixture.origin(), UUID.randomUUID(), helper.getLevel().getGameTime());
            helper.assertFalse(fixture.town().startProject(fixture.leader().getUUID(), overlapping),
                    "available construction capacity does not allow overlapping projects");
            var separate = SettlementProject.create(fixture.town(), fixture.area().id(), builder, fixture.plan(),
                    fixture.origin().offset(12, 0, 0), UUID.randomUUID(), helper.getLevel().getGameTime());
            helper.assertTrue(fixture.town().startProject(fixture.leader().getUUID(), separate),
                    "Village supports two builders on separate sites");
            helper.assertValueEqual(SettlementRecord.load(fixture.town().save(helper.getLevel().registryAccess()),
                    helper.getLevel().registryAccess(), () -> {}).projects().size(), 2, "concurrent projects survive saving");
            fixture.town().removeDuty(builder);
        } finally {
            fixture.builder().discard();
        }
        helper.succeed();
    }

    private static boolean rejects(GameTestHelper helper, CompoundTag tag) {
        try {
            SettlementRecord.load(tag, helper.getLevel().registryAccess(), () -> {});
            return false;
        } catch (IllegalArgumentException exception) {
            return true;
        }
    }

    private static boolean start(ServerPlayer player, Fixture fixture, UUID id) {
        return SettlementConstructionService.start(player, fixture.town(), fixture.builder().getUUID(), id);
    }

    private static SettlementProject begin(GameTestHelper helper, Fixture fixture) {
        UUID id = UUID.randomUUID();
        fixture.leader().setItemInHand(InteractionHand.MAIN_HAND, blueprint(helper, fixture, fixture.origin(), id));
        helper.assertTrue(start(fixture.leader(), fixture, id), "a valid blueprint can be commissioned");
        return fixture.town().projects().get(id);
    }

    private static ItemStack blueprint(GameTestHelper helper, Fixture fixture, BlockPos origin, UUID id) {
        return ConstructionBlueprintItem.create(helper.getLevel(), null, fixture.plan(), origin, id, 0, fixture.plan().price(), "", 0L);
    }

    private static ServerPlayer player(GameTestHelper helper, String name) {
        return FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
    }

    static Fixture fixture(GameTestHelper helper) {
        var level = helper.getLevel();
        var origin = helper.absolutePos(new BlockPos(12, 2, 12));
        var leader = player(helper, "ProjectLeader");
        leader.setPos(origin.getCenter());
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(level.getGameTime(), level.dimension().location(), origin, "");
        var town = SettlementSavedData.get(level).establish(registry, id, leader.getUUID(), origin, level.getGameTime()).orElseThrow();
        town.economy().credit(com.jvn.villagerretaliation.sell.CurrencyAmount.of(10_000, 1));
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.CONSTRUCTION, "Building site",
                origin.offset(-4, -1, -4), origin.offset(20, 12, 20), 5, true);
        SettlementTestPlots.claim(level, town, area.min(), area.max());
        helper.assertTrue(town.putArea(leader.getUUID(), area), "the construction area is registered");
        var entry = BuilderStructureCatalog.byId(level.getServer(),
                ResourceLocation.withDefaultNamespace("village/plains/houses/plains_small_house_1")).orElseThrow();
        var plan = BuilderStructureScanner.scan(level, entry, Rotation.NONE).orElseThrow();
        Villager builder = EntityType.VILLAGER.create(level);
        builder.setPos(origin.offset(-2, 0, 2).getBottomCenter());
        builder.setNoAi(true);
        builder.setVillagerData(builder.getVillagerData().setProfession(VillagerProfession.MASON));
        VillageAllegianceApi.assignKnown(level, builder, id, AllegianceAssignmentSource.EXPLICIT_API);
        helper.assertTrue(level.addFreshEntity(builder), "the assigned builder is loaded");
        town.assignDuty(builder.getUUID(), area.id());
        SettlementWorkforceService.reconcile(level, builder);
        SettlementCareService.onUnloaded(builder);
        return new Fixture(town, area, leader, builder, origin, plan);
    }

    record Fixture(SettlementRecord town, SettlementArea area, ServerPlayer leader, Villager builder, BlockPos origin,
                           BuilderStructureScanner.StructurePlan plan) {
    }
}
