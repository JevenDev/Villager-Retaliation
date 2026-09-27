package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.interaction.VillagerAssignmentStore;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureCatalog;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureScanner;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderTaskState;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.item.ConstructionBlueprintItem;
import com.jvn.villagerretaliation.network.SettlementManagementActionPayload;
import com.jvn.villagerretaliation.network.SettlementManagementPayload;
import com.jvn.villagerretaliation.network.SettlementManagementRequestPayload;
import com.jvn.villagerretaliation.network.VillageOverviewPayload;
import com.mojang.authlib.GameProfile;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
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
public final class SettlementRemoteConstructionGameTests {
    private SettlementRemoteConstructionGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_remote_construction")
    public static void unloadedBuildersCanCommissionThroughTheManagementCommand(GameTestHelper helper) {
        var fixture = fixture(helper);
        helper.assertTrue(helper.getLevel().addFreshEntity(fixture.builder()), "the original builder is tracked before unloading");
        fixture.builder().remove(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
        var snapshot = SettlementManagementService.snapshot(fixture.leader(), new SettlementManagementRequestPayload(
                fixture.town().id().value(), SettlementManagementRequestPayload.Page.RESIDENTS, 0, "", 1, false));
        var resident = (SettlementManagementPayload.Resident) snapshot.entries().getFirst();
        helper.assertValueEqual(resident.presence(), VillageOverviewPayload.Presence.UNLOADED, "the management selection has no loaded entity");
        helper.assertTrue(resident.roles().contains(HiredVillagerRole.BUILDER), "the recorded profession qualifies for construction");
        helper.assertValueEqual(resident.settings().role(), HiredVillagerRole.BUILDER, "the selection exposes the current civic job");
        var project = begin(helper, fixture);
        helper.assertTrue(project.site() != null, "commissioning captures the actual observed construction site");
        var worker = fixture.town().workers().get(fixture.builder().getUUID());
        helper.assertValueEqual(BuilderTaskState.jobId(worker.settings()).orElseThrow(), project.id(), "the worker ledger receives the commissioned project");
        helper.assertValueEqual(BuilderTaskState.jobId(HiredWorkStateStore.state(
                fixture.town().residents().get(worker.id()).context(helper.getLevel()))).orElseThrow(), project.id(),
                "the saved resident context acknowledges the same task");
        helper.assertTrue(ConstructionBlueprintItem.previewData(fixture.leader().getMainHandItem()).orElseThrow().started(), "the held blueprint is marked started");
        helper.assertTrue(helper.getLevel().getEntity(worker.id()) == null, "commissioning does not spawn a second villager");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_remote_construction")
    public static void commissioningDoesNotLoadTheAbsentBuildersChunk(GameTestHelper helper) {
        var origin = helper.absolutePos(new BlockPos(12, 2, 12)).offset(7_000_000, 0, 7_000_000).atY(80);
        var fixture = fixture(helper, origin, origin.west(96));
        helper.assertFalse(helper.getLevel().hasChunkAt(fixture.builder().blockPosition()), "the builder's recorded chunk starts absent");
        begin(helper, fixture);
        helper.assertFalse(helper.getLevel().hasChunkAt(fixture.builder().blockPosition()), "project validation only uses the visible site");
        helper.assertTrue(helper.getLevel().getEntity(fixture.builder().getUUID()) == null, "the absent builder remains an unloaded resident");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_remote_construction")
    public static void commissioningPreservesPendingInventoryThroughSaveAndNativeReturn(GameTestHelper helper) {
        var fixture = fixture(helper);
        var level = helper.getLevel();
        var original = fixture.builder().saveWithoutId(new CompoundTag());
        var worker = fixture.town().workers().get(fixture.builder().getUUID());
        var items = worker.items(level.registryAccess());
        items.set(HiredJobInventory.HOTBAR_START, new ItemStack(Items.OAK_PLANKS, 11));
        worker.updateInventory(level.registryAccess(), items);
        worker.changed();
        var project = begin(helper, fixture);
        helper.assertValueEqual(count(helper, worker), 11, "commissioning preserves inventory newer than the cached resident");
        var saved = SettlementRecord.load(fixture.town().save(level.registryAccess()), level.registryAccess(), () -> {});
        helper.assertValueEqual(count(helper, saved.workers().get(worker.id())), 11, "the current finite supplies survive saving");
        fixture.town().recordWorker(saved.workers().get(worker.id()));
        fixture.town().recordResident(saved.residents().get(worker.id()));
        var returning = EntityType.VILLAGER.create(level);
        returning.load(original);
        helper.assertTrue(level.addFreshEntity(returning), "the original native resident returns once");
        try {
            SettlementBackgroundService.onActive(returning);
            var inventory = HiredJobInventory.getJobInventory(returning);
            helper.assertValueEqual(inventory.countItem(Items.OAK_PLANKS), 11, "the native handoff restores the latest inventory");
            helper.assertValueEqual(BuilderTaskState.jobId(HiredWorkStateStore.state(returning)).orElseThrow(), project.id(),
                    "the returning resident receives the commissioned task");
            inventory.setItem(HiredJobInventory.HOTBAR_START, ItemStack.EMPTY);
            SettlementBackgroundService.onActive(returning);
            helper.assertValueEqual(inventory.countItem(Items.OAK_PLANKS), 0, "repeated handoff cannot refill collected supplies");
        } finally {
            returning.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_remote_construction")
    public static void remoteCommissioningStillRequiresBuildPermissionAndHamlet(GameTestHelper helper) {
        var origin = helper.absolutePos(new BlockPos(12, 2, 12));
        var fixture = fixture(helper, origin, origin.offset(-2, 0, 2), false);
        var delegate = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "BuildDelegate"));
        var id = UUID.randomUUID();
        delegate.setItemInHand(InteractionHand.MAIN_HAND, blueprint(helper, fixture, fixture.origin(), id));
        fixture.town().setPermissions(fixture.town().leader(), delegate.getUUID(), Set.of(SettlementPermission.STAFF));
        helper.assertFalse(start(delegate, fixture, id), "staffing permission does not grant commissioning");
        fixture.town().setPermissions(fixture.town().leader(), delegate.getUUID(), Set.of(SettlementPermission.BUILD));
        helper.assertFalse(start(delegate, fixture, id), "a cached builder cannot bypass Camp's construction gate");
        helper.assertTrue(fixture.town().projects().isEmpty(), "rejected requests create no project");
        helper.assertFalse(ConstructionBlueprintItem.previewData(delegate.getMainHandItem()).orElseThrow().started(), "rejection leaves the blueprint unused");
        helper.assertValueEqual(count(helper, fixture.town().workers().get(fixture.builder().getUUID())), 3, "rejection preserves existing supplies");
        fixture.town().advance();
        helper.assertTrue(start(delegate, fixture, id), "a construction delegate can use the same unloaded builder at Hamlet");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_remote_construction")
    public static void staleBuilderSnapshotsCannotOverrideReleaseOrCensusRemoval(GameTestHelper helper) {
        var fixture = fixture(helper);
        var id = hold(helper, fixture);
        helper.assertTrue(SettlementWorkforceService.release(helper.getLevel(), fixture.leader(), fixture.town().id(), fixture.builder().getUUID()),
                "the leader releases the unloaded builder");
        helper.assertFalse(start(fixture.leader(), fixture, id), "the old native assignment cannot restore a released civic job");
        helper.assertTrue(SettlementWorkforceService.assignResident(helper.getLevel(), fixture.leader(), fixture.town().id(),
                fixture.builder().getUUID(), fixture.area().id()), "the current resident can be explicitly staffed again");
        VillageAllegianceRegistrySavedData.get(helper.getLevel()).removeResident(fixture.town().id(), fixture.builder().getUUID());
        helper.assertFalse(start(fixture.leader(), fixture, id), "a stale care snapshot cannot bypass current census membership");
        helper.assertTrue(fixture.town().projects().isEmpty(), "neither stale authority creates a project");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_remote_construction")
    public static void currentNonConstructionDutyOverridesTheCachedBuilderAssignment(GameTestHelper helper) {
        var fixture = fixture(helper);
        var storage = new SettlementArea(UUID.randomUUID(), SettlementAreaType.DEPOT, "Storage",
                fixture.origin().offset(22, 0, 0), fixture.origin().offset(24, 2, 2), 5, true);
        helper.assertTrue(fixture.town().putArea(fixture.town().leader(), storage), "the second workplace is registered");
        helper.assertTrue(fixture.town().assignDuty(fixture.builder().getUUID(), storage.id()), "the authoritative duty changes while the native context stays old");
        var id = hold(helper, fixture);
        helper.assertFalse(start(fixture.leader(), fixture, id), "a former builder cannot commission from stale assignment tags");
        helper.assertValueEqual(fixture.town().duties().get(fixture.builder().getUUID()), storage.id(), "commissioning does not reverse the current job");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_remote_construction")
    public static void remoteConstructionPreservesChildAndPersonalCommitmentRestrictions(GameTestHelper helper) {
        var fixture = fixture(helper);
        var id = hold(helper, fixture);
        fixture.builder().setAge(-1_000);
        SettlementCareService.onUnloaded(fixture.builder());
        helper.assertFalse(start(fixture.leader(), fixture, id), "an unloaded child cannot receive a project");
        fixture.builder().setAge(0);
        VillagerAssignmentStore.unassign(fixture.builder());
        var hired = VillagerAssignmentStore.hire(fixture.builder(), UUID.randomUUID(), HiredVillagerRole.BUILDER, 0L, null);
        helper.assertValueEqual(hired.state(), com.jvn.villagerretaliation.interaction.VillagerAssignmentState.HIRED, "the recorded resident has a real personal assignment");
        SettlementCareService.onUnloaded(fixture.builder());
        helper.assertFalse(start(fixture.leader(), fixture, id), "a current personal hire cannot become a civic builder");
        VillagerAssignmentStore.unassign(fixture.builder());
        VillagerAssignmentStore.beginFollowing(fixture.builder(), UUID.randomUUID(), fixture.builder().blockPosition(), "", false);
        SettlementCareService.onUnloaded(fixture.builder());
        helper.assertFalse(start(fixture.leader(), fixture, id), "personal following also blocks remote construction");
        helper.assertTrue(fixture.town().projects().isEmpty(), "rejected commitments create no parallel project");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_remote_construction")
    public static void oldCareRecordsNeedNativeObservationBeforeRemoteCommissioning(GameTestHelper helper) {
        var fixture = fixture(helper);
        var old = fixture.town().residents().get(fixture.builder().getUUID()).save();
        old.remove("EntityContext");
        fixture.town().recordResident(SettlementResidentState.load(old));
        var id = hold(helper, fixture);
        helper.assertFalse(start(fixture.leader(), fixture, id), "legacy saves cannot invent an unobserved builder");
        helper.assertValueEqual(fixture.town().residents().get(fixture.builder().getUUID()).food(), old.getInt("Food"), "legacy care remains intact");
        SettlementCareService.onUnloaded(fixture.builder());
        helper.assertTrue(start(fixture.leader(), fixture, id), "observing the actual resident enables remote commissioning");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_remote_construction")
    public static void aLoadedEntityWithTheSameIdentityPreventsCachedFallback(GameTestHelper helper) {
        var fixture = fixture(helper);
        var id = hold(helper, fixture);
        var imposter = EntityType.COW.create(helper.getLevel());
        imposter.setUUID(fixture.builder().getUUID());
        imposter.setPos(fixture.builder().position());
        helper.assertTrue(helper.getLevel().addFreshEntity(imposter), "another native entity currently owns the identity");
        try {
            helper.assertFalse(start(fixture.leader(), fixture, id), "an incompatible live identity cannot fall back to the saved villager");
        } finally {
            imposter.discard();
        }
        helper.assertTrue(helper.getLevel().addFreshEntity(fixture.builder()), "the original villager now owns the live identity");
        try {
            VillagerAssignmentStore.unassign(fixture.builder());
            var hired = VillagerAssignmentStore.hire(fixture.builder(), UUID.randomUUID(), HiredVillagerRole.BUILDER, 0L, null);
            helper.assertValueEqual(hired.state(), com.jvn.villagerretaliation.interaction.VillagerAssignmentState.HIRED, "the live resident has a real personal assignment");
            helper.assertFalse(start(fixture.leader(), fixture, id), "current loaded commitments override the earlier eligible cache");
        } finally {
            fixture.builder().discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_remote_construction")
    public static void cancelledUnloadedBuildersCanAcceptNewProjectsWithoutReplayingOldBlueprints(GameTestHelper helper) {
        var fixture = fixture(helper);
        var firstId = hold(helper, fixture);
        var oldBlueprint = fixture.leader().getMainHandItem().copy();
        helper.assertTrue(start(fixture.leader(), fixture, firstId), "the first remote project starts");
        var oldContext = fixture.town().residents().get(fixture.builder().getUUID()).context(helper.getLevel()).saveWithoutId(new CompoundTag());
        helper.assertTrue(SettlementManagementService.execute(fixture.leader(), new SettlementManagementActionPayload(
                fixture.town().id().value(), new SettlementManagementActionPayload.CancelProject(firstId))), "the first project is cancelled remotely");
        fixture.leader().setItemInHand(InteractionHand.MAIN_HAND, oldBlueprint);
        helper.assertFalse(start(fixture.leader(), fixture, firstId), "an unused stale copy cannot replay the cancelled project");
        var second = begin(helper, fixture);
        var returning = EntityType.VILLAGER.create(helper.getLevel());
        returning.load(oldContext);
        helper.assertTrue(helper.getLevel().addFreshEntity(returning), "the villager returns with the first task in its native save");
        try {
            SettlementBackgroundService.onActive(returning);
            helper.assertValueEqual(BuilderTaskState.jobId(HiredWorkStateStore.state(returning)).orElseThrow(), second.id(),
                    "current project authority replaces the cancelled native task");
            helper.assertValueEqual(HiredJobInventory.getJobInventory(returning).countItem(Items.OAK_PLANKS), 3, "cancel and recommission preserve finite supplies");
            helper.assertValueEqual(fixture.town().projects().get(firstId).status(), SettlementProject.Status.CANCELLED, "the old project remains cancelled");
        } finally {
            returning.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_remote_construction")
    public static void remoteBuildersStillValidateTheHeldBlueprintAndCurrentSite(GameTestHelper helper) {
        var fixture = fixture(helper);
        var id = hold(helper, fixture);
        helper.assertFalse(start(fixture.leader(), fixture, UUID.randomUUID()), "the held blueprint must match the requested project identity");
        var block = fixture.plan().blocks().stream().filter(part -> !part.state().isAir()).findFirst().orElseThrow();
        var position = fixture.plan().worldPos(fixture.origin(), block);
        helper.getLevel().setBlock(position, Blocks.BEDROCK.defaultBlockState(), 2);
        helper.assertFalse(start(fixture.leader(), fixture, id), "remote workers cannot reserve a protected real block");
        helper.getLevel().setBlock(position, Blocks.AIR.defaultBlockState(), 2);
        var smaller = new SettlementArea(fixture.area().id(), fixture.area().type(), fixture.area().name(),
                fixture.origin(), fixture.origin().offset(1, 1, 1), 5, true);
        helper.assertTrue(fixture.town().putArea(fixture.town().leader(), smaller), "the current construction area can be resized");
        helper.assertFalse(start(fixture.leader(), fixture, id), "saved native work bounds cannot bypass the current designation");
        helper.assertTrue(fixture.town().putArea(fixture.town().leader(), fixture.area()), "the valid bounds can be restored");
        helper.assertTrue(start(fixture.leader(), fixture, id), "the same observed builder succeeds when the real site is valid");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_remote_construction")
    public static void newlyCommissionedRemoteProjectsUseFiniteMaterialsDuringBackgroundWork(GameTestHelper helper) {
        var fixture = fixture(helper);
        var level = helper.getLevel();
        var target = fixture.plan().blocks().stream().filter(block -> block.state().is(Blocks.OAK_PLANKS))
                .min(java.util.Comparator.comparingDouble(block -> fixture.plan().worldPos(fixture.origin(), block)
                        .distSqr(fixture.builder().blockPosition()))).orElseThrow();
        for (var block : fixture.plan().blocks()) level.setBlock(fixture.plan().worldPos(fixture.origin(), block), block.state(), 2);
        var position = fixture.plan().worldPos(fixture.origin(), target);
        level.setBlock(position, Blocks.AIR.defaultBlockState(), 2);
        var worker = fixture.town().workers().get(fixture.builder().getUUID());
        var items = worker.items(level.registryAccess());
        items.set(HiredJobInventory.HOTBAR_START, ItemStack.EMPTY);
        worker.updateInventory(level.registryAccess(), items);
        worker.changed();
        var project = begin(helper, fixture);
        for (int index = 0; index < 10; index++) SettlementBackgroundConstruction.tick(level, fixture.town(), worker, 200, 1.0D);
        helper.assertTrue(project.active() && level.getBlockState(position).isAir(), "the remote project cannot build without an actual supply");
        items = worker.items(level.registryAccess());
        items.set(HiredJobInventory.HOTBAR_START, new ItemStack(Items.OAK_PLANKS));
        worker.updateInventory(level.registryAccess(), items);
        worker.changed();
        for (int index = 0; index < 40 && project.active(); index++) SettlementBackgroundConstruction.tick(level, fixture.town(), worker, 200, 1.0D);
        helper.assertValueEqual(project.status(), SettlementProject.Status.COMPLETED,
                "the commissioned project completes through the background worker: " + worker.status() + " / " + project.blocker());
        SettlementConstructionSiteService.update(level, fixture.town());
        helper.assertValueEqual(level.getBlockState(position), target.state(), "the funded construction reaches the real world");
        helper.assertValueEqual(count(helper, worker), 0, "the real block consumes the only supplied plank");
        SettlementBackgroundConstruction.tick(level, fixture.town(), worker, 200, 1.0D);
        helper.assertValueEqual(count(helper, worker), 0, "completed work cannot duplicate or refund its material");
        helper.assertTrue(level.getEntity(fixture.builder().getUUID()) == null, "production does not need to spawn the worker");
        helper.succeed();
    }

    private static SettlementProject begin(GameTestHelper helper, Fixture fixture) {
        var id = hold(helper, fixture);
        helper.assertTrue(start(fixture.leader(), fixture, id), "the unloaded builder receives a valid real blueprint");
        return fixture.town().projects().get(id);
    }

    private static UUID hold(GameTestHelper helper, Fixture fixture) {
        var id = UUID.randomUUID();
        fixture.leader().setItemInHand(InteractionHand.MAIN_HAND, blueprint(helper, fixture, fixture.origin(), id));
        return id;
    }

    private static ItemStack blueprint(GameTestHelper helper, Fixture fixture, BlockPos origin, UUID id) {
        return ConstructionBlueprintItem.create(helper.getLevel(), null, fixture.plan(), origin, id, 0, fixture.plan().price(), "", 0L);
    }

    private static boolean start(ServerPlayer actor, Fixture fixture, UUID id) {
        return SettlementManagementService.execute(actor, new SettlementManagementActionPayload(fixture.town().id().value(),
                new SettlementManagementActionPayload.StartProject(fixture.builder().getUUID(), id)));
    }

    private static int count(GameTestHelper helper, SettlementWorkerState worker) {
        return worker.items(helper.getLevel().registryAccess()).stream().filter(stack -> stack.is(Items.OAK_PLANKS)).mapToInt(ItemStack::getCount).sum();
    }

    private static Fixture fixture(GameTestHelper helper) {
        var origin = helper.absolutePos(new BlockPos(12, 2, 12));
        return fixture(helper, origin, origin.offset(-2, 0, 2));
    }

    private static Fixture fixture(GameTestHelper helper, BlockPos origin, BlockPos position) {
        return fixture(helper, origin, position, true);
    }

    private static Fixture fixture(GameTestHelper helper, BlockPos origin, BlockPos position, boolean hamlet) {
        var level = helper.getLevel();
        var leader = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "RemoteBuilder"));
        leader.setPos(origin.getCenter());
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(level.getGameTime(), level.dimension().location(), origin, "");
        var town = SettlementSavedData.get(level).establish(registry, id, leader.getUUID(), origin, level.getGameTime()).orElseThrow();
        if (hamlet) town.advance();
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.CONSTRUCTION, "Building site",
                origin.offset(-4, -1, -4), origin.offset(20, 12, 20), 5, true);
        helper.assertTrue(town.putArea(town.leader(), area), "the construction designation is registered");
        for (var pos : BlockPos.betweenClosed(area.min(), area.max())) {
            level.setBlock(pos, pos.getY() < origin.getY() ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
        }
        var entry = BuilderStructureCatalog.byId(level.getServer(),
                ResourceLocation.withDefaultNamespace("village/plains/houses/plains_small_house_1")).orElseThrow();
        var plan = BuilderStructureScanner.scan(level, entry, Rotation.NONE).orElseThrow();
        var builder = EntityType.VILLAGER.create(level);
        builder.setPos(position.getBottomCenter());
        builder.setNoAi(true);
        builder.setOnGround(true);
        builder.setVillagerData(builder.getVillagerData().setProfession(VillagerProfession.MASON));
        HiredJobInventory.getJobInventory(builder).setItem(HiredJobInventory.HOTBAR_START, new ItemStack(Items.OAK_PLANKS, 3));
        VillageAllegianceApi.assignKnown(level, builder, id, AllegianceAssignmentSource.EXPLICIT_API);
        if (hamlet) {
            helper.assertTrue(SettlementWorkforceService.assign(level, leader, id, builder, area.id()), "the known adult is staffed as a civic builder");
        } else {
            helper.assertTrue(town.assignDuty(builder.getUUID(), area.id()), "the Camp fixture retains a prior builder assignment");
            SettlementWorkforceService.reconcile(level, builder);
            SettlementBackgroundService.onUnloaded(builder);
        }
        SettlementCareService.onUnloaded(builder);
        helper.assertTrue(level.getEntity(builder.getUUID()) == null, "the fixture never spawns the recorded resident");
        return new Fixture(town, area, leader, builder, origin, plan);
    }

    private record Fixture(SettlementRecord town, SettlementArea area, ServerPlayer leader, Villager builder, BlockPos origin,
                           BuilderStructureScanner.StructurePlan plan) {
    }
}
