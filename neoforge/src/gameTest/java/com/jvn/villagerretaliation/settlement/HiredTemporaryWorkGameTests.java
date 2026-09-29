package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.interaction.HiredTemporaryWorkService;
import com.jvn.villagerretaliation.interaction.HiredVillagerContractService;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredWorkArea;
import com.jvn.villagerretaliation.interaction.HiredWorkSession;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.interaction.VillagerWorkAuthority;
import com.jvn.villagerretaliation.interaction.work.HiredPathMemory;
import com.jvn.villagerretaliation.interaction.work.HiredTemporaryQueue;
import com.jvn.villagerretaliation.interaction.work.HiredTemporaryTask;
import com.jvn.villagerretaliation.interaction.work.HiredTemporaryTask.Kind;
import com.jvn.villagerretaliation.interaction.work.WorkResult;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.network.SettlementViewBatchPayload;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("villagerretaliation")
@PrefixGameTestTemplate(false)
public final class HiredTemporaryWorkGameTests {
    private HiredTemporaryWorkGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "temporary_work")
    public static void temporaryQueuesRoundTripAndRejectInvalidRecords(GameTestHelper helper) {
        var task = task(Kind.FISH, new BlockPos(1, 2, 3), 5);
        var queue = new HiredTemporaryQueue(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                HiredVillagerRole.FISHING, BlockPos.ZERO, new BlockPos(8, 8, 8), List.of(task));
        var state = new CompoundTag();
        queue.store(state);
        helper.assertValueEqual(HiredTemporaryQueue.load(state), queue, "temporary quantities and ownership survive reload");
        helper.assertValueEqual(queue.advance().current().remaining(), 4, "one completion consumes one catch");
        var request = new SettlementViewBatchPayload(UUID.randomUUID(), UUID.randomUUID(), SettlementViewAction.TEMPORARY_TASK,
                List.of(new SettlementViewBatchPayload.Target(UUID.randomUUID(), queue.worksite())), null, null, task, true);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            SettlementViewBatchPayload.STREAM_CODEC.encode(buffer, request);
            helper.assertValueEqual(SettlementViewBatchPayload.STREAM_CODEC.decode(buffer), request, "task bounds and append survive the wire");
        } finally {
            buffer.release();
        }
        var entries = new ArrayList<HiredTemporaryTask>();
        for (int i = 0; i < HiredTemporaryQueue.MAX_TASKS; i++) entries.add(task(Kind.FISH, BlockPos.ZERO, 1));
        var full = new HiredTemporaryQueue(queue.owner(), queue.issuer(), queue.worksite(), queue.role(),
                queue.homeMin(), queue.homeMax(), entries);
        rejects(helper, () -> full.append(task(Kind.FISH, BlockPos.ZERO, 1)), "a seventeenth task is rejected");
        rejects(helper, () -> new HiredTemporaryTask(UUID.randomUUID(), Kind.FARM_PATCH, BlockPos.ZERO, new BlockPos(64, 0, 0), 1),
                "oversized task bounds are rejected");
        rejects(helper, () -> new HiredTemporaryTask(UUID.randomUUID(), Kind.FARM_PATCH, BlockPos.ZERO, BlockPos.ZERO, 2),
                "patch quantities cannot masquerade as catch counts");
        var corrupt = state.copy();
        corrupt.getCompound(HiredTemporaryQueue.STATE_TAG).putString("Version", "1");
        rejects(helper, () -> HiredTemporaryQueue.load(corrupt), "malformed queue versions are rejected");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "temporary_work")
    public static void personalTasksStayWithinContractBoundsAndPreserveInventory(GameTestHelper helper) {
        var fixture = personal(helper, HiredVillagerRole.FARMING);
        var villager = fixture.villager();
        var level = helper.getLevel();
        try {
            var stranger = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "TaskVisitor"));
            var home = HiredWorkArea.fromState(HiredWorkStateStore.state(villager), villager.blockPosition());
            var first = task(Kind.FARM_PATCH, home.min(), 1);
            helper.assertFalse(HiredTemporaryWorkService.submit(stranger, villager, first, false).isEmpty(), "another player cannot order a personal hire");
            helper.assertFalse(HiredTemporaryWorkService.submit(fixture.owner(), villager, task(Kind.FARM_PATCH, home.max().east(), 1), false).isEmpty(),
                    "a personal task cannot leave the assigned bounds");
            var inventory = HiredJobInventory.getJobInventory(villager);
            inventory.insertPlainOutput(new ItemStack(Items.WHEAT, 9));
            helper.assertTrue(HiredTemporaryWorkService.submit(fixture.owner(), villager, first, false).isEmpty(), "the owner can issue a standalone task");
            helper.assertTrue(SettlementWorkforceService.duty(villager).isEmpty(), "temporary work grants no civic authority");
            helper.assertTrue(HiredTemporaryWorkService.submit(fixture.owner(), villager, first, true).isEmpty(), "repeated commands are idempotent");
            helper.assertFalse(HiredTemporaryWorkService.submit(fixture.owner(), villager,
                    new HiredTemporaryTask(first.id(), first.kind(), home.max(), home.max(), 1), true).isEmpty(),
                    "reusing an identifier cannot change its task");
            helper.assertValueEqual(HiredWorkSession.active(level, villager).area(), first.area(), "the session uses temporary bounds");
            helper.assertValueEqual(HiredWorkArea.fromState(HiredWorkStateStore.state(villager), villager.blockPosition()), home,
                    "persistent assignment bounds remain unchanged");
            var second = task(Kind.FARM_PATCH, home.max(), 1);
            helper.assertTrue(HiredTemporaryWorkService.submit(fixture.owner(), villager, second, true).isEmpty(), "Shift appends a task");
            helper.assertValueEqual(HiredTemporaryWorkService.queue(HiredWorkStateStore.state(villager)).tasks().size(), 2, "both tasks stay queued");
            HiredPathMemory.reserveTarget(level, villager, first.min());
            helper.assertTrue(HiredTemporaryWorkService.submit(fixture.owner(), villager, task(Kind.FARM_PATCH, home.max(), 1), false).isEmpty(), "a normal command replaces the pending tasks");
            helper.assertValueEqual(HiredTemporaryWorkService.queue(HiredWorkStateStore.state(villager)).tasks().size(), 1, "replacement removes old tasks");
            helper.assertFalse(HiredPathMemory.isReservedByOther(level, EntityType.VILLAGER.create(level), first.min()), "replacement releases target claims");
            HiredTemporaryWorkService.submit(fixture.owner(), villager, null, false);
            helper.assertTrue(HiredTemporaryWorkService.queue(HiredWorkStateStore.state(villager)) == null, "cancellation clears temporary work");
            helper.assertValueEqual(HiredWorkSession.active(level, villager).area(), home, "cancellation restores the original area");
            helper.assertValueEqual(inventory.collectOutputItems().stream().mapToInt(entry -> entry.stack().getCount()).sum(), 9,
                    "replacement and cancellation preserve carried output");
        } finally {
            HiredPathMemory.clear(villager);
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "temporary_work")
    public static void civicTasksRetainWorksitesAndStopWhenPermissionIsRevoked(GameTestHelper helper) {
        var level = helper.getLevel();
        var origin = helper.absolutePos(BlockPos.ZERO);
        var center = new BlockPos(13_000_000 + origin.getX() * 8, 64, 13_000_000 + origin.getZ() * 8);
        var leader = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "TaskLeader"));
        var delegate = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "TaskDelegate"));
        leader.setPos(origin.getCenter());
        delegate.setPos(origin.getCenter());
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(level.getGameTime(), level.dimension().location(), center, "");
        var town = SettlementSavedData.get(level).establish(registry, id, leader.getUUID(), center, level.getGameTime()).orElseThrow();
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.FARM, "Home farm", center, center.offset(2, 2, 2), 5, true);
        helper.assertTrue(town.putArea(leader.getUUID(), area), "the assigned farm exists");
        var villager = EntityType.VILLAGER.create(level);
        villager.setPos(center.getCenter());
        villager.setNoAi(true);
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));
        VillageAllegianceApi.assignKnown(level, villager, id, AllegianceAssignmentSource.EXPLICIT_API);
        town.assignDuty(villager.getUUID(), area.id());
        SettlementWorkforceService.reconcile(level, villager);
        BlockPos outside = null;
        for (var pos : BlockPos.betweenClosed(center.offset(-8, 0, -8), center.offset(8, 0, 8))) {
            if (town.contains(pos) && !HiredWorkArea.fromBounds(area.min(), area.max(), true).contains(pos)) {
                outside = pos.immutable();
                break;
            }
        }
        helper.assertTrue(outside != null, "there is territory outside the assigned farm");
        var task = task(Kind.FARM_PATCH, outside, 1);
        helper.assertFalse(HiredTemporaryWorkService.submit(delegate, villager, task, false).isEmpty(), "a visitor cannot command civic workers");
        town.setPermissions(leader.getUUID(), delegate.getUUID(), Set.of(SettlementPermission.STAFF));
        helper.assertTrue(HiredTemporaryWorkService.submit(delegate, villager, task, false).isEmpty(), "staff can order temporary work elsewhere in town");
        helper.assertValueEqual(town.duties().get(villager.getUUID()), area.id(), "temporary work retains civic assignment");
        helper.assertFalse(level.hasChunkAt(outside), "the remote task starts unloaded");
        helper.assertFalse(HiredTemporaryWorkService.ready(level, HiredWorkSession.active(level, villager)), "unloaded targets block work");
        helper.assertFalse(level.hasChunkAt(outside), "checking a task never loads its chunk");
        var worker = SettlementWorkerState.capture(level, villager, SettlementWorkforceService.duty(villager).orElseThrow(), 0);
        town.recordWorker(worker);
        var restored = SettlementRecord.load(town.save(level.registryAccess()), level.registryAccess(), () -> {});
        helper.assertValueEqual(HiredTemporaryQueue.load(restored.workers().get(villager.getUUID()).settings()).current(), task,
                "civic task progress survives settlement reload");
        level.getChunkSource().addEntity(leader);
        helper.assertTrue(SettlementViewService.open(leader, town.id()), "the ruler can open a command session");
        var viewing = SettlementViewService.session(leader.getUUID());
        try {
            var stale = new SettlementViewBatchPayload(viewing.id, UUID.randomUUID(), SettlementViewAction.TEMPORARY_TASK,
                    List.of(new SettlementViewBatchPayload.Target(villager.getUUID(), UUID.randomUUID())), null, null, task, false);
            helper.assertValueEqual(SettlementViewCommands.apply(viewing, stale, stale.targets().getFirst()).reason(),
                    "Assignment changed since review", "stale map selections cannot replace a newer assignment");
            var incompatible = new SettlementViewBatchPayload(viewing.id, UUID.randomUUID(), SettlementViewAction.TEMPORARY_TASK,
                    List.of(new SettlementViewBatchPayload.Target(villager.getUUID(), area.id())), null, null, task(Kind.FISH, outside, 1), false);
            helper.assertValueEqual(SettlementViewCommands.apply(viewing, incompatible, incompatible.targets().getFirst()).status(),
                    com.jvn.villagerretaliation.network.SettlementViewBatchResultPayload.Status.SKIPPED, "mixed-role commands skip incompatible workers");
        } finally {
            SettlementViewService.close(leader.getUUID(), false);
            level.getChunkSource().removeEntity(leader);
        }
        town.setPermissions(leader.getUUID(), delegate.getUUID(), Set.of());
        HiredTemporaryWorkService.validate(level, villager, VillagerWorkAuthority.resolve(level, villager));
        helper.assertTrue(HiredTemporaryWorkService.queue(HiredWorkStateStore.state(villager)) == null, "revoked staff permission cancels queued tasks");
        var fishery = new SettlementArea(UUID.randomUUID(), SettlementAreaType.FISHERY, "Fishing tasks", area.min(), area.max(), 5, true);
        helper.assertTrue(town.putArea(leader.getUUID(), fishery), "a fishery can use the same bounds");
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FISHERMAN));
        town.assignDuty(villager.getUUID(), fishery.id());
        SettlementWorkforceService.reconcile(level, villager);
        helper.assertTrue(HiredTemporaryWorkService.submit(leader, villager, task(Kind.FISH, center, 2), false).isEmpty(),
                "the fisher has a finite shared-authority task");
        var fishState = HiredWorkStateStore.state(villager);
        var fishWorker = SettlementWorkerState.capture(level, villager, SettlementWorkforceService.duty(villager).orElseThrow(), 0);
        town.recordWorker(fishWorker);
        var pendingCatch = new CompoundTag();
        var catchItems = new net.minecraft.nbt.ListTag();
        catchItems.add(new ItemStack(Items.COD).save(level.registryAccess()));
        pendingCatch.put("Items", catchItems);
        fishState.put("SettlementFishingCatch", pendingCatch.copy());
        fishWorker.settings().put("SettlementFishingCatch", pendingCatch.copy());
        helper.assertTrue(SettlementBackgroundFishing.finishReturnedCatch(level, villager), "a pending native catch returns to the worker");
        helper.assertValueEqual(HiredTemporaryWorkService.queue(fishState).current().remaining(), 1, "returning the catch consumes its quantity once");
        helper.assertValueEqual(HiredTemporaryQueue.load(fishWorker.settings()).current().remaining(), 1,
                "the saved worker records the quantity with its inventory receipt");
        helper.assertTrue(SettlementBackgroundFishing.finishReturnedCatch(level, villager), "replaying an already returned catch is harmless");
        helper.assertValueEqual(HiredTemporaryWorkService.queue(fishState).current().remaining(), 1, "replay cannot consume another quantity");
        helper.assertValueEqual(HiredJobInventory.getJobInventory(villager).collectOutputItems().stream()
                .filter(entry -> entry.stack().is(Items.COD)).mapToInt(entry -> entry.stack().getCount()).sum(), 1,
                "the returned catch is stored exactly once");
        HiredPathMemory.clear(villager);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "temporary_work")
    public static void farmPatchCompletesAfterHarvestAndWaitsForClaimedTargets(GameTestHelper helper) {
        var fixture = personal(helper, HiredVillagerRole.FARMING);
        var villager = fixture.villager();
        var level = helper.getLevel();
        try {
            var state = HiredWorkStateStore.state(villager);
            var crop = villager.blockPosition().east();
            level.setBlockAndUpdate(crop.below(), Blocks.FARMLAND.defaultBlockState());
            level.setBlockAndUpdate(crop, ((CropBlock) Blocks.WHEAT).getStateForAge(7));
            var inventory = HiredJobInventory.getJobInventory(villager);
            inventory.setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_HOE));
            state.putBoolean("FarmingTillSoil", false);
            state.putBoolean("FarmingPlantCrops", false);
            state.putBoolean("AutoDepositOutputs", false);
            var task = task(Kind.FARM_PATCH, crop, 1);
            helper.assertTrue(HiredTemporaryWorkService.submit(fixture.owner(), villager, task, false).isEmpty(), "the owner orders a harvest patch");
            var other = EntityType.VILLAGER.create(level);
            HiredPathMemory.reserveTarget(level, other, crop);
            var session = HiredWorkSession.active(level, villager);
            var blocked = session.worker().tick(level, villager, fixture.owner(), session.context());
            HiredTemporaryWorkService.completed(level, villager, session, blocked);
            helper.assertTrue(HiredTemporaryWorkService.queue(state) != null && level.getBlockState(crop).is(Blocks.WHEAT),
                    "another worker's claim neither completes the task nor harvests its crop");
            HiredPathMemory.clear(other);
            var harvest = session.worker().tick(level, villager, fixture.owner(), session.context());
            HiredTemporaryWorkService.completed(level, villager, session, harvest);
            helper.assertTrue(level.getBlockState(crop).isAir(), "the patch harvests after the claim is released");
            var finish = session.worker().tick(level, villager, fixture.owner(), session.context());
            HiredTemporaryWorkService.completed(level, villager, session, finish);
            helper.assertTrue(HiredTemporaryWorkService.queue(state) == null, "an exhausted patch returns to normal work");
            helper.assertValueEqual(inventory.collectOutputItems().stream().filter(entry -> entry.stack().is(Items.WHEAT))
                    .mapToInt(entry -> entry.stack().getCount()).sum(), 1, "one native crop produces wheat once");
        } finally {
            HiredPathMemory.clear(villager);
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "temporary_work")
    public static void finiteFishingCountsOnlyNewCompletedCatches(GameTestHelper helper) {
        var fixture = personal(helper, HiredVillagerRole.FISHING);
        var villager = fixture.villager();
        var level = helper.getLevel();
        try {
            var state = HiredWorkStateStore.state(villager);
            state.putBoolean("FishingCatchCompleted", true);
            var task = task(Kind.FISH, villager.blockPosition(), 2);
            helper.assertTrue(HiredTemporaryWorkService.submit(fixture.owner(), villager, task, false).isEmpty(), "the fisher accepts two catches");
            var session = HiredWorkSession.active(level, villager);
            com.jvn.villagerretaliation.interaction.work.FishingWorker.takeCompletedPractice(state);
            HiredTemporaryWorkService.completed(level, villager, session, WorkResult.completed("interaction.work.fishing.completed"));
            helper.assertValueEqual(HiredTemporaryWorkService.queue(state).current().remaining(), 2, "the already earned catch is preserved outside the new quantity");
            HiredTemporaryWorkService.completed(level, villager, session, WorkResult.progressed("interaction.work.fishing.cast"));
            helper.assertValueEqual(HiredTemporaryWorkService.queue(state).current().remaining(), 2, "casting does not consume a catch");
            state.putBoolean("FishingCatchCompleted", true);
            com.jvn.villagerretaliation.interaction.work.FishingWorker.takeCompletedPractice(state);
            HiredTemporaryWorkService.completed(level, villager, session, WorkResult.completed("interaction.work.fishing.completed"));
            helper.assertValueEqual(HiredTemporaryWorkService.queue(state).current().remaining(), 1, "one new completed catch consumes one unit");
            HiredTemporaryWorkService.caught(state);
            HiredTemporaryWorkService.validate(level, villager, VillagerWorkAuthority.resolve(level, villager));
            helper.assertTrue(HiredTemporaryWorkService.queue(state) == null, "a returned catch finishes the task before another work tick");
        } finally {
            HiredPathMemory.clear(villager);
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "temporary_work")
    public static void emptyFarmPatchWaitsForSeedsAndAssignmentChangesCancelIt(GameTestHelper helper) {
        var fixture = personal(helper, HiredVillagerRole.FARMING);
        var villager = fixture.villager();
        var level = helper.getLevel();
        try {
            var state = HiredWorkStateStore.state(villager);
            var crop = villager.blockPosition().east();
            level.setBlockAndUpdate(crop.below(), Blocks.FARMLAND.defaultBlockState());
            level.setBlockAndUpdate(crop, Blocks.AIR.defaultBlockState());
            HiredJobInventory.getJobInventory(villager).setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_HOE));
            state.putBoolean("FarmingTillSoil", false);
            state.putBoolean("UseAssignedStorageForSupplies", false);
            HiredTemporaryWorkService.submit(fixture.owner(), villager, task(Kind.FARM_PATCH, crop, 1), false);
            var session = HiredWorkSession.active(level, villager);
            var result = session.worker().tick(level, villager, fixture.owner(), session.context());
            helper.assertValueEqual(result.status(), "interaction.work.farming.missing_planting_item", "vacant plots require finite planting stock");
            HiredTemporaryWorkService.completed(level, villager, session, result);
            helper.assertTrue(HiredTemporaryWorkService.queue(state) != null, "missing seeds cannot prematurely complete a patch");
            HiredWorkArea.fromBounds(crop, crop, true).save(state);
            HiredTemporaryWorkService.validate(level, villager, VillagerWorkAuthority.resolve(level, villager));
            helper.assertTrue(HiredTemporaryWorkService.queue(state) == null, "changing the permanent assignment cancels its old temporary queue");
            helper.assertTrue(level.getBlockState(crop).isAir(), "cancellation does not create crops or seeds");
        } finally {
            HiredPathMemory.clear(villager);
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "temporary_work")
    public static void temporaryMiningUsesNativeOreRulesAndReturnsToItsArea(GameTestHelper helper) {
        var fixture = personal(helper, HiredVillagerRole.MINING);
        var villager = fixture.villager();
        var level = helper.getLevel();
        try {
            var state = HiredWorkStateStore.state(villager);
            var center = villager.blockPosition();
            var ore = center.east();
            var home = HiredWorkArea.fromState(state, center);
            for (var pos : BlockPos.betweenClosed(center.offset(-2, -1, -2), center.offset(3, -1, 3))) {
                level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
            }
            level.setBlockAndUpdate(ore, Blocks.COAL_ORE.defaultBlockState());
            level.setBlockAndUpdate(ore.south(), Blocks.STONE.defaultBlockState());
            com.jvn.villagerretaliation.interaction.work.mining.HiredOreBlockTracker.clearRuntimeState();
            var inventory = HiredJobInventory.getJobInventory(villager);
            inventory.setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.DIAMOND_PICKAXE));
            state.putBoolean("AutoDepositOutputs", false);
            state.putBoolean("UseAssignedStorageForSupplies", false);
            state.putString("MiningMode", "exposed_ores");
            var task = new HiredTemporaryTask(UUID.randomUUID(), Kind.EXCAVATE, center, center.offset(1, 1, 1), 1);
            helper.assertTrue(HiredTemporaryWorkService.submit(fixture.owner(), villager, task, false).isEmpty(), "the miner accepts a bounded task");
            for (int i = 0; i < 160 && HiredTemporaryWorkService.queue(state) != null; i++) {
                var session = HiredWorkSession.active(level, villager);
                var result = session.worker().tick(level, villager, fixture.owner(), session.context());
                HiredTemporaryWorkService.completed(level, villager, session, result);
            }
            helper.assertTrue(level.getBlockState(ore).isAir(), "the temporary miner harvests exposed ore");
            helper.assertTrue(level.getBlockState(ore.south()).is(Blocks.STONE), "exposed-ore mode preserves unrelated stone");
            helper.assertTrue(inventory.hasOutput(stack -> stack.is(Items.COAL)), "native mining stores finite coal output");
            helper.assertTrue(HiredTemporaryWorkService.queue(state) == null, "a complete bounded scan finishes the mining task");
            helper.assertValueEqual(HiredWorkSession.active(level, villager).area(), home, "the miner returns to its permanent bounds");
        } finally {
            HiredPathMemory.clear(villager);
            com.jvn.villagerretaliation.interaction.work.mining.HiredOreBlockTracker.clearRuntimeState();
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "temporary_work")
    public static void selectedTreeTaskHonorsClaimsAndLeavesTheNextTree(GameTestHelper helper) {
        var fixture = logger(helper);
        var villager = fixture.villager();
        var level = helper.getLevel();
        var state = HiredWorkStateStore.state(villager);
        var root = villager.blockPosition().east(2);
        var neighbor = root.west(6);
        try {
            var canopy = root.above(3).north();
            var task = new HiredTemporaryTask(UUID.randomUUID(), Kind.TREE, canopy, canopy, 1, canopy);
            helper.assertTrue(HiredTemporaryWorkService.submit(fixture.owner(), villager, task, false).isEmpty(), "a personal logger accepts a selected canopy");
            var saved = HiredTemporaryWorkService.queue(state);
            helper.assertTrue(saved.current().area().contains(root.above(3)), "the server includes the canopy in the tree task");
            helper.assertValueEqual(HiredTemporaryTask.load(saved.current().save()), saved.current(), "selected tree identity survives reload");
            var other = EntityType.VILLAGER.create(level);
            HiredPathMemory.reserveTarget(level, other, root.above(2));
            var session = HiredWorkSession.active(level, villager);
            helper.assertValueEqual(session.worker().tick(level, villager, fixture.owner(), session.context()).status(),
                    "interaction.work.logging.selected_tree_blocked", "a claim on a branch blocks the whole selected tree");
            helper.assertTrue(level.getBlockState(root).is(Blocks.OAK_LOG), "a competing claim preserves the trunk");
            HiredPathMemory.clear(other);
            for (int i = 0; i < 800 && HiredTemporaryWorkService.queue(state) != null; i++) {
                session = HiredWorkSession.active(level, villager);
                var result = session.worker().tick(level, villager, fixture.owner(), session.context());
                HiredTemporaryWorkService.completed(level, villager, session, result);
            }
            helper.assertTrue(HiredTemporaryWorkService.queue(state) == null, "finishing the native tree sequence completes the task");
            helper.assertTrue(level.getBlockState(root).isAir(), "the selected tree is harvested");
            helper.assertTrue(level.getBlockState(neighbor).is(Blocks.OAK_LOG), "the neighboring tree remains untouched");
            helper.assertValueEqual(HiredJobInventory.getJobInventory(villager).collectOutputItems().stream()
                    .filter(entry -> entry.stack().is(Items.OAK_LOG)).mapToInt(entry -> entry.stack().getCount()).sum(), 4,
                    "the tree produces exactly its four native logs");
        } finally {
            HiredPathMemory.clear(villager);
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "temporary_work")
    public static void replacingOrCancellingTreeTasksPreservesAnUnfinishedHarvest(GameTestHelper helper) {
        var fixture = logger(helper);
        var villager = fixture.villager();
        var level = helper.getLevel();
        var state = HiredWorkStateStore.state(villager);
        var root = villager.blockPosition().east(2);
        try {
            var task = new HiredTemporaryTask(UUID.randomUUID(), Kind.TREE, root, root, 1, root);
            HiredTemporaryWorkService.submit(fixture.owner(), villager, task, false);
            var session = HiredWorkSession.active(level, villager);
            var logs = com.jvn.villagerretaliation.interaction.work.logging.LoggingTreeGeometry.connectedTreeLogs(level, level::hasChunkAt, root, Set.of());
            com.jvn.villagerretaliation.interaction.work.logging.LoggingHarvestPlan.begin(session.context(), root, villager.blockPosition(),
                    logs, List.of(), List.of(), ItemStack.EMPTY, false, "minecraft:oak");
            var neighbor = root.west(6);
            var next = new HiredTemporaryTask(UUID.randomUUID(), Kind.TREE, neighbor, neighbor, 1, neighbor);
            helper.assertTrue(HiredTemporaryWorkService.submit(fixture.owner(), villager, next, true).isEmpty(), "Shift queues the next selected tree");
            helper.assertValueEqual(HiredTemporaryQueue.pending(state).tasks().size(), 1, "the pending queue does not repeat the current tree");
            helper.assertValueEqual(HiredTemporaryQueue.pending(state).current().id(), next.id(), "the next tree keeps its identity");
            var restored = state.copy();
            helper.assertValueEqual(HiredTemporaryQueue.pending(restored), HiredTemporaryQueue.pending(state), "a deferred task change survives reload");
            helper.assertTrue(HiredTemporaryWorkService.submit(fixture.owner(), villager, null, false).isEmpty(), "cancellation is accepted during harvesting");
            helper.assertTrue(state.contains(HiredTemporaryQueue.PENDING_TAG), "cancellation waits for a safe tree boundary");
            helper.assertTrue(com.jvn.villagerretaliation.interaction.work.logging.LoggingHarvestPlan.read(session.context()) != null,
                    "cancellation preserves the unfinished native harvest");
            for (int i = 0; i < 800 && state.contains(HiredTemporaryQueue.PENDING_TAG); i++) {
                session = HiredWorkSession.active(level, villager);
                var result = session.worker().tick(level, villager, fixture.owner(), session.context());
                HiredTemporaryWorkService.completed(level, villager, session, result);
            }
            helper.assertFalse(state.contains(HiredTemporaryQueue.PENDING_TAG), "the cancellation applies after native work completes");
            helper.assertTrue(HiredTemporaryWorkService.queue(state) == null, "cancellation removes queued work");
            helper.assertTrue(level.getBlockState(neighbor).is(Blocks.OAK_LOG), "cancellation never starts the next tree");
            helper.assertValueEqual(HiredJobInventory.getJobInventory(villager).collectOutputItems().stream()
                    .filter(entry -> entry.stack().is(Items.OAK_LOG)).mapToInt(entry -> entry.stack().getCount()).sum(), 4,
                    "cancellation preserves every earned log");
        } finally {
            HiredPathMemory.clear(villager);
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "temporary_work")
    public static void savedTreeClaimsSurviveReloadAndReleaseOnReassignment(GameTestHelper helper) {
        var level = helper.getLevel();
        var origin = helper.absolutePos(BlockPos.ZERO);
        var center = new BlockPos(14_000_000 + origin.getX() * 8, 64, 14_000_000 + origin.getZ() * 8);
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(0, level.dimension().location(), center, "");
        var town = SettlementSavedData.get(level).establish(registry, id, UUID.randomUUID(), center, 0).orElseThrow();
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.FORESTRY, "Claimed forest", center, center.offset(5, 6, 5), 5, true);
        town.putArea(town.leader(), area);
        var villager = EntityType.VILLAGER.create(level);
        villager.setPos(center.getCenter());
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.TOOLSMITH));
        VillageAllegianceApi.assignKnown(level, villager, id, AllegianceAssignmentSource.EXPLICIT_API);
        town.assignDuty(villager.getUUID(), area.id());
        SettlementWorkforceService.reconcile(level, villager);
        var worker = SettlementWorkerState.capture(level, villager, SettlementWorkforceService.duty(villager).orElseThrow(), 0);
        town.recordWorker(worker);
        var session = HiredWorkSession.create(level, villager, HiredVillagerRole.LOGGING);
        com.jvn.villagerretaliation.interaction.work.logging.LoggingHarvestPlan.begin(session.context(), center, center.east(),
                List.of(center, center.above()), List.of(), List.of(), ItemStack.EMPTY, false, "minecraft:oak");
        worker.observe(level, villager, SettlementWorkforceService.duty(villager).orElseThrow(), 0);
        var restored = SettlementWorkerState.load(worker.save());
        town.recordWorker(restored);
        var outsider = UUID.randomUUID();
        helper.assertTrue(SettlementTreeClaims.claimedByOther(level, outsider, List.of(center.above())), "a saved worker retains the whole tree claim");
        villager.setPos(origin.getCenter());
        helper.assertTrue(level.addFreshEntity(villager), "the worker returns in a loaded chunk");
        com.jvn.villagerretaliation.interaction.work.logging.LoggingHarvestPlan.clear(session.context());
        helper.assertFalse(SettlementTreeClaims.claimedByOther(level, outsider, List.of(center)), "the current loaded worker takes precedence over an older snapshot");
        com.jvn.villagerretaliation.interaction.work.logging.LoggingHarvestPlan.begin(session.context(), center, center.east(),
                List.of(center, center.above()), List.of(), List.of(), ItemStack.EMPTY, false, "minecraft:oak");
        helper.assertTrue(SettlementTreeClaims.claimedByOther(level, outsider, List.of(center)), "a paused loaded harvest keeps its saved work reserved");
        villager.discard();
        var duplicateTag = worker.save();
        var secondId = UUID.randomUUID();
        duplicateTag.putUUID("Id", secondId);
        var duplicate = SettlementWorkerState.load(duplicateTag);
        town.assignDuty(secondId, area.id());
        town.recordWorker(duplicate);
        var winner = worker.id().compareTo(secondId) < 0 ? worker.id() : secondId;
        var loser = winner.equals(worker.id()) ? secondId : worker.id();
        helper.assertFalse(SettlementTreeClaims.claimedByOther(level, winner, List.of(center)), "overlapping legacy plans choose one owner");
        helper.assertTrue(SettlementTreeClaims.claimedByOther(level, loser, List.of(center)), "the other legacy plan waits without duplicating work");
        town.removeDuty(worker.id());
        town.removeDuty(secondId);
        helper.assertFalse(SettlementTreeClaims.claimedByOther(level, outsider, List.of(center)), "reassignment releases saved tree claims");
        helper.assertFalse(level.hasChunkAt(center), "claim checks never load the tree");
        helper.succeed();
    }

    private static Personal logger(GameTestHelper helper) {
        var fixture = personal(helper, HiredVillagerRole.LOGGING);
        var villager = fixture.villager();
        var level = helper.getLevel();
        var center = villager.blockPosition();
        villager.setPos(center.getBottomCenter());
        var state = HiredWorkStateStore.state(villager);
        HiredWorkArea.fromBounds(center.offset(-7, -1, -4), center.offset(6, 6, 4), true).save(state);
        for (var position : BlockPos.betweenClosed(center.offset(-7, -1, -4), center.offset(6, -1, 4))) {
            level.setBlockAndUpdate(position, Blocks.DIRT.defaultBlockState());
        }
        for (var root : List.of(center.east(2), center.west(4))) {
            for (int y = 0; y < 4; y++) level.setBlockAndUpdate(root.above(y), Blocks.OAK_LOG.defaultBlockState());
            for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                level.setBlockAndUpdate(root.above(3).relative(direction), Blocks.OAK_LEAVES.defaultBlockState());
            }
        }
        state.putBoolean(com.jvn.villagerretaliation.interaction.work.logging.HiredLoggingOptions.PLANT_SAPLINGS_TAG, false);
        state.putBoolean(com.jvn.villagerretaliation.interaction.work.logging.HiredLoggingOptions.BONEMEAL_SAPLINGS_TAG, false);
        state.putBoolean("AutoDepositOutputs", false);
        state.putBoolean("UseAssignedStorageForSupplies", false);
        HiredJobInventory.getJobInventory(villager).setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.DIAMOND_AXE));
        HiredWorkSession.invalidate(villager);
        return fixture;
    }

    private static HiredTemporaryTask task(Kind kind, BlockPos pos, int quantity) {
        return new HiredTemporaryTask(UUID.randomUUID(), kind, pos, pos, quantity);
    }

    private static Personal personal(GameTestHelper helper, HiredVillagerRole role) {
        var level = helper.getLevel();
        var owner = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "TaskOwner"));
        var villager = EntityType.VILLAGER.create(level);
        var center = helper.absolutePos(new BlockPos(2, 2, 2));
        villager.setPos(center.getCenter());
        villager.setNoAi(true);
        villager.setVillagerData(villager.getVillagerData().setProfession(
                role == HiredVillagerRole.FISHING ? VillagerProfession.FISHERMAN
                        : role == HiredVillagerRole.MINING ? VillagerProfession.TOOLSMITH : VillagerProfession.FARMER));
        helper.assertTrue(level.addFreshEntity(villager), "the personal worker is loaded");
        helper.assertTrue(HiredVillagerContractService.startHireContract(level, villager, owner, 2, 8, role), "the worker accepts a personal contract");
        var state = HiredWorkStateStore.state(villager);
        HiredWorkStateStore.initializeDefaults(state, villager);
        HiredWorkArea.fromBounds(center.offset(-2, -1, -2), center.offset(2, 1, 2), true).save(state);
        state.putBoolean("Enabled", true);
        HiredWorkSession.invalidate(villager);
        return new Personal(owner, villager);
    }

    private static void rejects(GameTestHelper helper, Runnable action, String message) {
        try {
            action.run();
            helper.fail(message);
        } catch (IllegalArgumentException expected) {
        }
    }

    private record Personal(ServerPlayer owner, Villager villager) {
    }
}
