package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.interaction.HiredVillagerContractService;
import com.jvn.villagerretaliation.interaction.HiredVillagerFocusService;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredVillagerWorkService;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.inventory.HiredJobInventorySlotType;
import com.mojang.authlib.GameProfile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.StructureUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class VillagerWorkerGameTests {
    private static final String EMPTY_TEMPLATE = "empty";
    private static final String WORK_STATE_TAG = "VillagerRetaliationHiredWork";

    static {
        configureGameTestStructures();
    }

    private VillagerWorkerGameTests() {
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void workerRegistryCoversEveryRoleAndStatusRolesFailSafely(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildFloor(helper, 0, 8, 0, 8, 1);
        ServerPlayer hirer = fakePlayer(level, "VrWorkerRegistry");
        movePlayer(helper, hirer, new BlockPos(1, 2, 1));
        Villager villager = spawnVillager(helper, new BlockPos(3, 2, 3));

        for (HiredVillagerRole role : HiredVillagerRole.values()) {
            HiredRoleWorker worker = HiredRoleWorkerRegistry.get(role);
            helper.assertTrue(worker != null, role + " should have a worker registered");

            HiredWorkContext context = context(helper, villager, new CompoundTag(), new BlockPos(1, 2, 1), new BlockPos(7, 4, 7), true);
            WorkResult result = worker.tick(level, villager, hirer, context);
            helper.assertTrue(result != null, role + " worker should return a result");
            helper.assertFalse(result.status().isBlank(), role + " worker should expose a safe status");
            HiredWorkerBrain.Snapshot snapshot = HiredWorkerBrain.snapshot(context.state(), level.getGameTime());
            helper.assertTrue(snapshot.taskState() != null, role + " worker should leave a readable task state");
        }

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void workerBrainStateTargetsRoundTripAndClearSafely(GameTestHelper helper) {
        CompoundTag state = new CompoundTag();
        HiredWorkerBrain.initialize(state);
        helper.assertValueEqual(
                HiredWorkerBrain.snapshot(state, 0L).taskState(),
                HiredWorkerTaskState.IDLE,
                "default worker state");

        BlockPos workTarget = new BlockPos(4, 2, 4);
        HiredWorkerBrain.setState(state, HiredWorkerTaskState.WORKING, workTarget);
        HiredWorkerBrain.Snapshot working = HiredWorkerBrain.snapshot(state, 10L);
        helper.assertValueEqual(working.taskState(), HiredWorkerTaskState.WORKING, "working state");
        helper.assertValueEqual(working.targetPos(), workTarget, "working target");

        BlockPos storageTarget = new BlockPos(6, 2, 6);
        HiredJobInventory dummyInventory = new HiredJobInventory(spawnVillager(helper, new BlockPos(1, 2, 1)));
        HiredWorkContext context = new HiredWorkContext(
                dummyInventory,
                state,
                BlockPos.ZERO,
                BlockPos.ZERO,
                BlockPos.ZERO,
                4,
                2,
                false,
                100,
                true,
                true);
        HiredWorkerBrain.setStorageTarget(context, storageTarget);
        HiredWorkerBrain.setState(context, HiredWorkerTaskState.MOVING_TO_STORAGE);
        HiredWorkerBrain.Snapshot storing = HiredWorkerBrain.snapshot(state, 11L);
        helper.assertValueEqual(storing.storageTargetPos(), storageTarget, "storage target should be retained while moving to storage");

        HiredWorkerBrain.setState(context, HiredWorkerTaskState.IDLE);
        HiredWorkerBrain.Snapshot idle = HiredWorkerBrain.snapshot(state, 12L);
        helper.assertTrue(idle.targetPos() == null, "idle state should clear block target");
        helper.assertTrue(idle.storageTargetPos() == null, "idle state should clear storage target");
        helper.assertValueEqual(HiredWorkerTaskState.byId("target_unreachable"), HiredWorkerTaskState.FAILED_COOLDOWN, "legacy state alias");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void workAreaScanningIsBatchedAndCursorDriven(GameTestHelper helper) {
        buildFloor(helper, 0, 6, 0, 6, 1);
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        CompoundTag state = new CompoundTag();
        HiredWorkContext context = context(helper, villager, state, new BlockPos(1, 2, 1), new BlockPos(3, 3, 3), true);

        HiredWorkAreaScan.Result first = HiredWorkAreaScan.collect(context, "Cursor", 5, ignored -> true);
        helper.assertValueEqual(first.visitedPositions(), 5L, "first scan should visit only the configured batch");
        helper.assertFalse(first.completedFullPass(), "first scan should not finish a larger area");
        helper.assertTrue(HiredWorkAreaScan.isInProgress(context, "Cursor"), "scan cursor should persist between work ticks");

        long cursorAfterFirstBatch = state.getLong("Cursor");
        HiredWorkAreaScan.Result second = HiredWorkAreaScan.collect(context, "Cursor", 5, pos -> pos.getY() == context.workMax().getY());
        helper.assertTrue(state.getLong("Cursor") != cursorAfterFirstBatch || second.completedFullPass(), "second scan should advance the cursor");
        helper.assertTrue(second.candidates().stream().allMatch(pos -> pos.getY() == context.workMax().getY()), "scan should respect candidate filters");

        int guard = 0;
        while (HiredWorkAreaScan.isInProgress(context, "Cursor") && guard++ < 10) {
            HiredWorkAreaScan.collect(context, "Cursor", 5, ignored -> true);
        }
        helper.assertFalse(HiredWorkAreaScan.isInProgress(context, "Cursor"), "scan cursor should clear after a full pass");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void targetSearchUsesActivePlannedAndCooldownBeforeScanning(GameTestHelper helper) {
        buildFloor(helper, 0, 6, 0, 6, 1);
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        CompoundTag state = new CompoundTag();
        HiredWorkContext context = context(helper, villager, state, new BlockPos(1, 2, 1), new BlockPos(4, 2, 4), true);
        HiredTargetSearch.Messages messages = new HiredTargetSearch.Messages(
                "active",
                "planned",
                "cooldown",
                "none",
                "partial_",
                "found",
                40);

        BlockPos active = new BlockPos(2, 2, 2);
        BlockPos activeResult = HiredTargetSearch.find(
                helper.getLevel(),
                context,
                () -> active,
                pos -> true,
                ignored -> null,
                pos -> true,
                "NextScan",
                "ScanCursor",
                1,
                candidates -> candidates.isEmpty() ? null : candidates.getFirst(),
                messages);
        helper.assertValueEqual(activeResult, active, "active target should win without scanning");
        helper.assertValueEqual(
                HiredWorkerBrain.snapshot(state, helper.getLevel().getGameTime()).lastTargetScanResult(),
                "active",
                "active scan result");

        state.putLong("NextScan", helper.getLevel().getGameTime() + 100L);
        BlockPos cooldownResult = HiredTargetSearch.find(
                helper.getLevel(),
                context,
                () -> null,
                pos -> true,
                ignored -> null,
                pos -> true,
                "NextScan",
                "ScanCursor",
                1,
                candidates -> candidates.isEmpty() ? null : candidates.getFirst(),
                messages);
        helper.assertTrue(cooldownResult == null, "search should honor no-target cooldown before a new scan");
        helper.assertValueEqual(
                HiredWorkerBrain.snapshot(state, helper.getLevel().getGameTime()).lastTargetScanResult(),
                "cooldown",
                "cooldown scan result");

        state.remove("NextScan");
        BlockPos scanned = HiredTargetSearch.find(
                helper.getLevel(),
                context,
                () -> null,
                pos -> true,
                ignored -> null,
                pos -> pos.equals(context.workMin()),
                "NextScan",
                "ScanCursor",
                64,
                candidates -> candidates.isEmpty() ? null : candidates.getFirst(),
                messages);
        helper.assertValueEqual(scanned, context.workMin(), "search should rebuild from filtered scan candidates");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 200)
    public static void pathingFindsAdjacentReachableTargetAndRejectsBlockedOrOutOfAreaTargets(GameTestHelper helper) {
        buildFloor(helper, 0, 9, 0, 6, 1);
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 3));
        ServerLevel level = helper.getLevel();
        tickVillager(level, villager, 20);
        BlockPos reachable = helper.absolutePos(new BlockPos(6, 2, 3));
        setBlock(helper, new BlockPos(6, 2, 3), Blocks.COAL_ORE.defaultBlockState());

        BlockPos directApproach = helper.absolutePos(new BlockPos(5, 2, 3));
        helper.assertTrue(HiredMoveToBlockFaceJob.isValidApproachPosition(level, directApproach), "direct ore approach should be walkable");
        helper.assertTrue(
                HiredMoveToBlockFaceJob.visibleHitPosition(level, villager, villager.getEyePosition(), reachable) != null,
                "reachable ore should expose a visible face");
        net.minecraft.world.level.pathfinder.Path directPath = villager.getNavigation().createPath(directApproach, 0);
        helper.assertTrue(directPath != null && directPath.canReach(), "vanilla navigation should reach the direct ore approach");

        HiredPathResult reachableResult = new HiredMoveToBlockFaceJob(level, villager, List.of(reachable), 16).search();
        helper.assertTrue(reachableResult.reachesDestination(), "nearby exposed ore should have an adjacent reachable approach");
        helper.assertTrue(reachableResult.target() != null, "reachable result should include a target");
        helper.assertTrue(reachableResult.target().approachPos().distSqr(reachable) <= 4, "worker should choose an adjacent approach");
        helper.assertTrue(
                HiredMoveToBlockFaceJob.canReachFromCurrentPosition(level, villager, reachableResult.target())
                        || reachableResult.path() != null && reachableResult.path().canReach(),
                "target should be immediately reachable or have a complete path");

        HiredPathResult outOfArea = new HiredMoveToBlockFaceJob(
                level,
                villager,
                List.of(reachable),
                16,
                pos -> pos.getX() < reachable.getX()).search();
        helper.assertFalse(outOfArea.reachesDestination(), "work-area filter should reject targets outside the allowed area");

        BlockPos blocked = helper.absolutePos(new BlockPos(8, 2, 3));
        setBlock(helper, new BlockPos(8, 2, 3), Blocks.COAL_ORE.defaultBlockState());
        for (BlockPos rel : List.of(
                new BlockPos(7, 2, 3),
                new BlockPos(9, 2, 3),
                new BlockPos(8, 2, 2),
                new BlockPos(8, 2, 4),
                new BlockPos(8, 3, 3))) {
            setBlock(helper, rel, Blocks.STONE.defaultBlockState());
        }
        HiredPathResult blockedResult = new HiredMoveToBlockFaceJob(level, villager, List.of(blocked), 16).search();
        helper.assertFalse(blockedResult.reachesDestination(), "fully blocked target should fail gracefully");

        setBlock(helper, new BlockPos(3, 2, 3), Blocks.STONE.defaultBlockState());
        setBlock(helper, new BlockPos(3, 3, 3), Blocks.STONE.defaultBlockState());
        BlockPos observer = helper.absolutePos(new BlockPos(1, 2, 3));
        villager.moveTo(observer.getX() + 0.5D, observer.getY(), observer.getZ() + 0.5D, 0.0F, 0.0F);
        Vec3 eye = villager.getEyePosition();
        Vec3 hit = Vec3.atCenterOf(helper.absolutePos(new BlockPos(4, 2, 3)));
        setBlock(helper, new BlockPos(4, 2, 3), Blocks.COAL_ORE.defaultBlockState());
        helper.assertFalse(
                HiredMoveToBlockFaceJob.hasLineOfSightToBlock(level, villager, eye, helper.absolutePos(new BlockPos(4, 2, 3)), hit),
                "solid blocks should block required line of sight");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 200)
    public static void pathingCanRouteAroundSimpleObstaclesWithoutLeavingTheWorkArea(GameTestHelper helper) {
        buildFloor(helper, 0, 10, 0, 8, 1);
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 4));
        ServerLevel level = helper.getLevel();
        tickVillager(level, villager, 20);
        for (int z = 3; z <= 5; z++) {
            setBlock(helper, new BlockPos(4, 2, z), Blocks.STONE.defaultBlockState());
            setBlock(helper, new BlockPos(4, 3, z), Blocks.STONE.defaultBlockState());
        }
        BlockPos target = helper.absolutePos(new BlockPos(8, 2, 4));
        setBlock(helper, new BlockPos(8, 2, 4), Blocks.COAL_ORE.defaultBlockState());
        BlockPos routeApproach = helper.absolutePos(new BlockPos(7, 2, 4));
        net.minecraft.world.level.pathfinder.Path routePath = villager.getNavigation().createPath(routeApproach, 0);
        helper.assertTrue(routePath != null && routePath.canReach(), "vanilla navigation should route around the obstacle to the target approach");

        HiredPathResult result = new HiredMoveToBlockFaceJob(
                level,
                villager,
                List.of(target),
                32,
                pos -> pos.getX() >= helper.absolutePos(new BlockPos(0, 0, 0)).getX()
                        && pos.getX() <= helper.absolutePos(new BlockPos(10, 0, 0)).getX()
                        && pos.getZ() >= helper.absolutePos(new BlockPos(0, 0, 0)).getZ()
                        && pos.getZ() <= helper.absolutePos(new BlockPos(0, 0, 8)).getZ()).search();

        helper.assertTrue(result.reachesDestination(), "path search should route around a simple obstacle wall");
        helper.assertTrue(result.path() == null || HiredMoveToBlockFaceJob.pathStaysInsideFilter(result.path(), pos ->
                pos.getX() >= helper.absolutePos(new BlockPos(0, 0, 0)).getX()
                        && pos.getX() <= helper.absolutePos(new BlockPos(10, 0, 0)).getX()
                        && pos.getZ() >= helper.absolutePos(new BlockPos(0, 0, 0)).getZ()
                        && pos.getZ() <= helper.absolutePos(new BlockPos(0, 0, 8)).getZ()), "path should stay in the allowed area");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void pathMemoryAvoidsRepeatedFailuresReservationsAndNavigationStalls(GameTestHelper helper) {
        buildFloor(helper, 0, 5, 0, 5, 1);
        ServerLevel level = helper.getLevel();
        Villager first = spawnVillager(helper, new BlockPos(1, 2, 1));
        Villager second = spawnVillager(helper, new BlockPos(2, 2, 1));
        BlockPos target = helper.absolutePos(new BlockPos(4, 2, 4));

        helper.assertFalse(HiredPathMemory.recordFailure(level, first, target), "first path failure should not blacklist");
        helper.assertFalse(HiredPathMemory.recordFailure(level, first, target), "second path failure should not blacklist");
        helper.assertTrue(HiredPathMemory.recordFailure(level, first, target), "third path failure should blacklist");
        helper.assertTrue(HiredPathMemory.isAvoided(level, first, target), "blacklisted target should be avoided");

        HiredPathMemory.reserveTarget(level, first, target);
        helper.assertTrue(HiredPathMemory.isReservedByOther(level, second, target), "other workers should respect target reservations");
        HiredPathMemory.releaseAll(first);
        helper.assertFalse(HiredPathMemory.isReservedByOther(level, second, target), "reservations should clear when a worker stops");

        HiredPathMemory.rememberNavigationProgress(level, first, target, 25.0D);
        helper.assertFalse(HiredPathMemory.isNavigationBlocked(level, first, target, 25.0D), "same-tick navigation should not be marked stuck");

        HiredPathMemory.clear(first);
        HiredPathMemory.clear(second);
        first.discard();
        second.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void jobInventoryProtectsGearAndKeepsOutputsOutOfSupplySlots(GameTestHelper helper) {
        buildFloor(helper, 0, 4, 0, 4, 1);
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        HiredJobInventory inventory = HiredJobInventory.getJobInventory(villager);

        ItemStack protectedEmerald = HiredJobInventory.markAsProtectedVillagerProperty(
                new ItemStack(Items.EMERALD),
                villager,
                "gametest");
        inventory.setItem(18, protectedEmerald);
        ItemStack removed = inventory.removeItem(18, 1);
        helper.assertTrue(removed.isEmpty(), "protected job items should not be removable by automation");
        helper.assertValueEqual(inventory.slotType(18), HiredJobInventorySlotType.PROTECTED_PROPERTY, "protected slot type");

        ItemStack outputRemainder = inventory.insertOutput(new ItemStack(Items.WHEAT, 3));
        helper.assertTrue(outputRemainder.isEmpty(), "output should fit into output slots");
        helper.assertTrue(inventory.getItem(18).is(Items.EMERALD), "protected output slot should remain untouched");
        helper.assertTrue(inventory.getItem(19).is(Items.WHEAT), "output should use the next output slot");

        for (int slot = 6; slot < 18; slot++) {
            helper.assertTrue(inventory.getItem(slot).isEmpty(), "outputs should not occupy empty supply slot " + slot);
            helper.assertValueEqual(inventory.slotType(slot), HiredJobInventorySlotType.SUPPLY, "supply slot type " + slot);
        }

        inventory.setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_PICKAXE));
        helper.assertTrue(villager.getMainHandItem().is(Items.IRON_PICKAXE), "gear slots should stay synced to villager equipment");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void assignedStoragePersistsPurposeOwnershipAndOutputPriority(GameTestHelper helper) {
        buildFloor(helper, 0, 7, 0, 5, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerStorage");
        movePlayer(helper, hirer, new BlockPos(1, 2, 1));
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        Villager otherVillager = spawnVillager(helper, new BlockPos(2, 2, 4));
        BlockPos inputRel = new BlockPos(4, 2, 2);
        BlockPos outputRel = new BlockPos(5, 2, 2);
        BlockPos input = helper.absolutePos(inputRel);
        BlockPos output = helper.absolutePos(outputRel);
        setBlock(helper, inputRel, Blocks.CHEST.defaultBlockState());
        setBlock(helper, outputRel, Blocks.CHEST.defaultBlockState());
        AssignedStorageService.removeAssignedContainer(level, input);
        AssignedStorageService.removeAssignedContainer(level, output);

        AssignedStorageService.AssignSummary inputSummary = AssignedStorageService.assign(
                hirer,
                villager,
                List.of(new AssignedStorageService.StoragePosition(level.dimension(), input)),
                AssignedStorageService.INPUT_PURPOSE);
        AssignedStorageService.AssignSummary outputSummary = AssignedStorageService.assign(
                hirer,
                villager,
                List.of(new AssignedStorageService.StoragePosition(level.dimension(), output)),
                AssignedStorageService.OUTPUT_PURPOSE);
        helper.assertValueEqual(inputSummary.assigned(), 1, "input storage assignment");
        helper.assertValueEqual(outputSummary.assigned(), 1, "output storage assignment");

        AssignedStorageService.AssignSummary conflicting = AssignedStorageService.assign(
                hirer,
                otherVillager,
                List.of(new AssignedStorageService.StoragePosition(level.dimension(), output)),
                AssignedStorageService.OUTPUT_PURPOSE);
        helper.assertValueEqual(conflicting.alreadyAssigned(), 1, "storage ownership should not leak to another villager");

        HiredJobInventory inventory = HiredJobInventory.getJobInventory(villager);
        inventory.insertOutput(new ItemStack(Items.COBBLESTONE, 7));
        helper.assertTrue(inventory.depositOutputToAssignedStorage(), "output should deposit to assigned output storage");
        helper.assertValueEqual(countItem(container(level, output), Items.COBBLESTONE), 7, "output chest item count");
        helper.assertValueEqual(countItem(container(level, input), Items.COBBLESTONE), 0, "input chest should not receive outputs first");

        AssignedStorageService.removeAllAssignedStorage(level, villager);
        AssignedStorageService.removeAllAssignedStorage(level, otherVillager);
        villager.discard();
        otherVillager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 300)
    public static void miningWorkerMinesOnlyValidOreStoresDropsAndQueuesAdjacentTargets(GameTestHelper helper) {
        buildFloor(helper, 0, 7, 0, 5, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerMining");
        movePlayer(helper, hirer, new BlockPos(1, 2, 1));
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        HiredOreBlockTracker.clearRuntimeState();
        HiredRoleWorkerRegistry.clearRuntimeState();

        BlockPos firstOreRel = new BlockPos(3, 2, 2);
        BlockPos secondOreRel = new BlockPos(4, 2, 2);
        BlockPos invalidRel = new BlockPos(3, 2, 3);
        setBlock(helper, firstOreRel, Blocks.COAL_ORE.defaultBlockState());
        setBlock(helper, secondOreRel, Blocks.COAL_ORE.defaultBlockState());
        setBlock(helper, invalidRel, Blocks.STONE.defaultBlockState());

        CompoundTag state = new CompoundTag();
        HiredWorkContext context = context(helper, villager, state, new BlockPos(1, 2, 1), new BlockPos(6, 4, 4), true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.DIAMOND_PICKAXE));
        MiningWorker worker = new MiningWorker();

        runWorkerUntil(helper, worker, level, villager, hirer, context, 80, () ->
                level.getBlockState(helper.absolutePos(firstOreRel)).isAir());
        helper.assertTrue(level.getBlockState(helper.absolutePos(firstOreRel)).isAir(), "miner should break the first valid exposed ore");
        boolean secondOreRemaining = !level.getBlockState(helper.absolutePos(secondOreRel)).isAir();
        helper.assertTrue(secondOreRemaining || countInventoryItem(context.inventory(), Items.COAL) >= 2,
                "adjacent ore should stay queued or be mined as the next valid vein block");
        helper.assertTrue(level.getBlockState(helper.absolutePos(invalidRel)).is(Blocks.STONE), "miner should not break unrelated stone in exposed-ore mode");
        helper.assertTrue(context.inventory().hasOutput(stack -> stack.is(Items.COAL)), "mined coal drops should be stored as output");

        HiredWorkerBrain.Snapshot snapshot = HiredWorkerBrain.snapshot(state, level.getGameTime());
        helper.assertTrue(
                snapshot.taskState() == HiredWorkerTaskState.MOVING_TO_TARGET
                        || snapshot.taskState() == HiredWorkerTaskState.FINDING_CHAIN_TARGET
                        || snapshot.taskState() == HiredWorkerTaskState.WORKING
                        || snapshot.taskState() == HiredWorkerTaskState.IDLE,
                "miner should recover into a valid follow-up state");

        HiredOreBlockTracker.clearRuntimeState();
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void miningWorkerPausesSafelyForMissingToolsAndFullOutput(GameTestHelper helper) {
        buildFloor(helper, 0, 6, 0, 5, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerMiningPause");
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        setBlock(helper, new BlockPos(3, 2, 2), Blocks.COAL_ORE.defaultBlockState());

        CompoundTag state = new CompoundTag();
        HiredWorkContext context = context(helper, villager, state, new BlockPos(1, 2, 1), new BlockPos(5, 4, 4), true);
        WorkResult missingTool = new MiningWorker().tick(level, villager, hirer, context);
        HiredWorkerBrain.Snapshot missingToolState = HiredWorkerBrain.snapshot(state, level.getGameTime());
        helper.assertValueEqual(missingToolState.taskState(), HiredWorkerTaskState.PAUSED_MISSING_TOOL, "missing pickaxe state");
        helper.assertValueEqual(missingToolState.failureReason(), "missing_pickaxe", "missing pickaxe reason");
        helper.assertFalse(missingTool.completed(), "missing tool should not pretend work completed");

        for (int slot = 18; slot < HiredJobInventory.SLOT_COUNT; slot++) {
            context.inventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        helper.assertFalse(context.hasOutputSpace(), "filled output inventory should report no output space");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 200)
    public static void farmingWorkerHarvestsMatureCropsReplantsAndStoresOutputs(GameTestHelper helper) {
        buildFloor(helper, 0, 6, 0, 5, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerFarming");
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        BlockPos cropRel = new BlockPos(3, 2, 2);
        setBlock(helper, new BlockPos(3, 1, 2), Blocks.FARMLAND.defaultBlockState());
        setBlock(helper, cropRel, ((CropBlock) Blocks.WHEAT).getStateForAge(7));

        CompoundTag state = new CompoundTag();
        state.putString("CropMode", "harvest_replant");
        HiredWorkContext context = context(helper, villager, state, new BlockPos(1, 2, 1), new BlockPos(5, 4, 4), true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_HOE));

        WorkResult result = new FarmingWorker().tick(level, villager, hirer, context);
        BlockState replanted = level.getBlockState(helper.absolutePos(cropRel));
        helper.assertTrue(replanted.getBlock() == Blocks.WHEAT, "farmer should replant wheat");
        helper.assertValueEqual(replanted.getValue(CropBlock.AGE), 0, "replanted wheat age");
        helper.assertTrue(context.inventory().hasOutput(stack -> stack.is(Items.WHEAT)), "farmer should store crop output");
        helper.assertTrue(result.progressed(), "harvest should count as worker progress");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 300)
    public static void loggingWorkerHarvestsNaturalLogsStoresDropsAndHandlesRemovedTrees(GameTestHelper helper) {
        buildFloor(helper, 0, 8, 0, 6, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerLogging");
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 3));
        BlockPos logRel = new BlockPos(4, 2, 3);
        setBlock(helper, new BlockPos(4, 1, 3), Blocks.DIRT.defaultBlockState());
        for (BlockPos rel : List.of(
                logRel,
                new BlockPos(4, 3, 3),
                new BlockPos(4, 4, 3))) {
            setBlock(helper, rel, Blocks.OAK_LOG.defaultBlockState());
        }
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, false);
        for (BlockPos rel : List.of(
                new BlockPos(4, 5, 3),
                new BlockPos(3, 5, 3),
                new BlockPos(5, 5, 3),
                new BlockPos(4, 5, 2),
                new BlockPos(4, 5, 4),
                new BlockPos(3, 4, 3),
                new BlockPos(5, 4, 3),
                new BlockPos(4, 4, 2),
                new BlockPos(4, 4, 4))) {
            setBlock(helper, rel, leaves);
        }

        CompoundTag state = new CompoundTag();
        HiredWorkContext context = context(helper, villager, state, new BlockPos(1, 2, 1), new BlockPos(7, 6, 5), true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_AXE));
        LoggingWorker worker = new LoggingWorker();
        HiredPathTarget logTarget = new HiredMoveToBlockFaceJob(
                level,
                villager,
                List.of(helper.absolutePos(logRel)),
                8,
                context::isInsideWorkArea,
                blockState -> blockState.is(BlockTags.LEAVES)).search().target();
        helper.assertTrue(logTarget != null, "test tree log should have a reachable leaf-transparent work face");

        runWorkerUntil(helper, worker, level, villager, hirer, context, 160, () ->
                !level.getBlockState(helper.absolutePos(logRel)).is(BlockTags.LOGS));
        helper.assertFalse(level.getBlockState(helper.absolutePos(logRel)).is(BlockTags.LOGS), "logger should remove the valid natural log");
        helper.assertTrue(context.inventory().hasOutput(stack -> stack.is(Items.OAK_LOG)), "logger should store log drops");

        BlockPos removedTreeRel = new BlockPos(6, 2, 3);
        setBlock(helper, removedTreeRel, Blocks.OAK_LOG.defaultBlockState());
        worker.prepareBreakingTarget(level, context, villager, helper.absolutePos(removedTreeRel));
        setBlock(helper, removedTreeRel, Blocks.AIR.defaultBlockState());
        WorkResult removedResult = worker.tick(level, villager, hirer, context);
        HiredWorkerBrain.Snapshot snapshot = HiredWorkerBrain.snapshot(state, level.getGameTime());
        helper.assertFalse(snapshot.taskState() == HiredWorkerTaskState.WORKING, "logger should not stay working on a removed tree");
        helper.assertFalse(removedResult.status().isBlank(), "removed tree should still produce a safe status");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 200)
    public static void hiredAiSuppressionStartsOnlyForActiveWorkAndClearsAfterDisableOrContractEnd(GameTestHelper helper) {
        buildFloor(helper, 0, 6, 0, 6, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = helper.makeMockServerPlayerInLevel();
        movePlayer(helper, hirer, new BlockPos(1, 2, 1));
        Villager villager = spawnVillager(helper, new BlockPos(3, 2, 3));

        helper.assertFalse(HiredVillagerFocusService.shouldSuppressVanillaBrainTick(level, villager), "unhired villagers should keep vanilla brain ticks");
        HiredVillagerContractService.startHireContract(level, villager, hirer, 1, 8);
        HiredVillagerWorkService.initializeWorkArea(level, villager);
        CompoundTag state = villager.getPersistentData().getCompound(WORK_STATE_TAG);
        state.putBoolean("Enabled", true);
        HiredWorkerBrain.setState(state, HiredWorkerTaskState.SELECTING_TARGET, null);
        helper.assertTrue(HiredVillagerFocusService.shouldSuppressVanillaBrainTick(level, villager), "active hired work should suppress vanilla idle AI");

        state.putBoolean("Enabled", false);
        helper.assertFalse(HiredVillagerFocusService.shouldSuppressVanillaBrainTick(level, villager), "disabled work should clear suppression");
        state.putBoolean("Enabled", true);
        HiredVillagerContractService.endHireContract(level, villager, hirer);
        helper.assertFalse(HiredVillagerFocusService.shouldSuppressVanillaBrainTick(level, villager), "ended contracts should clear suppression");

        villager.discard();
        helper.succeed();
    }

    private static void runWorkerUntil(
            GameTestHelper helper,
            HiredRoleWorker worker,
            ServerLevel level,
            Villager villager,
            ServerPlayer hirer,
            HiredWorkContext context,
            int maxTicks,
            java.util.function.BooleanSupplier done) {
        for (int tick = 0; tick < maxTicks && !done.getAsBoolean(); tick++) {
            worker.maintain(level, villager, context);
            worker.tick(level, villager, hirer, context);
            level.tickNonPassenger(villager);
        }
        if (!done.getAsBoolean()) {
            HiredWorkerBrain.Snapshot snapshot = HiredWorkerBrain.snapshot(context.state(), level.getGameTime());
            String debug = worker instanceof LoggingWorker ? " " + LoggingWorker.debugSummary(context) : "";
            throw new GameTestAssertException("Worker did not reach expected state in " + maxTicks
                    + " direct ticks; task=" + snapshot.taskState()
                    + ", failure=" + snapshot.failureReason()
                    + ", scan=" + snapshot.lastTargetScanResult()
                    + debug);
        }
    }

    private static HiredWorkContext context(
            GameTestHelper helper,
            Villager villager,
            CompoundTag state,
            BlockPos minRelative,
            BlockPos maxRelative,
            boolean hasWorkArea) {
        BlockPos min = helper.absolutePos(minRelative);
        BlockPos max = helper.absolutePos(maxRelative);
        BlockPos center = new BlockPos(
                (min.getX() + max.getX()) / 2,
                (min.getY() + max.getY()) / 2,
                (min.getZ() + max.getZ()) / 2);
        HiredWorkerBrain.initialize(state);
        return new HiredWorkContext(
                HiredJobInventory.getJobInventory(villager),
                state,
                center,
                min,
                max,
                Math.max(4, Math.max(max.getX() - min.getX(), max.getZ() - min.getZ())),
                Math.max(2, max.getY() - min.getY()),
                hasWorkArea,
                100,
                true,
                true);
    }

    private static void buildFloor(GameTestHelper helper, int minX, int maxX, int minZ, int maxZ, int y) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                setBlock(helper, new BlockPos(x, y, z), Blocks.STONE.defaultBlockState());
                setBlock(helper, new BlockPos(x, y + 1, z), Blocks.AIR.defaultBlockState());
                setBlock(helper, new BlockPos(x, y + 2, z), Blocks.AIR.defaultBlockState());
            }
        }
    }

    private static void setBlock(GameTestHelper helper, BlockPos relativePos, BlockState state) {
        helper.getLevel().setBlock(helper.absolutePos(relativePos), state, Block.UPDATE_ALL);
    }

    private static Villager spawnVillager(GameTestHelper helper, BlockPos relativePos) {
        ServerLevel level = helper.getLevel();
        Villager villager = EntityType.VILLAGER.create(level);
        if (villager == null) {
            throw new GameTestAssertException("Could not create villager");
        }
        BlockPos pos = helper.absolutePos(relativePos);
        villager.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        if (!level.addFreshEntity(villager)) {
            throw new GameTestAssertException("Could not add villager to level");
        }
        level.tickNonPassenger(villager);
        return villager;
    }

    private static ServerPlayer fakePlayer(ServerLevel level, String name) {
        UUID id = UUID.nameUUIDFromBytes(("villagerretaliation:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        ServerPlayer player = FakePlayerFactory.get(level, new GameProfile(id, name));
        BlockPos spawn = level.getSharedSpawnPos();
        player.moveTo(spawn.getX() + 0.5D, spawn.getY() + 1.0D, spawn.getZ() + 0.5D, 0.0F, 0.0F);
        return player;
    }

    private static void movePlayer(GameTestHelper helper, ServerPlayer player, BlockPos relativePos) {
        BlockPos pos = helper.absolutePos(relativePos);
        player.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
    }

    private static void tickVillager(ServerLevel level, Villager villager, int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            level.tickNonPassenger(villager);
        }
    }

    private static Container container(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof Container container) {
            return container;
        }
        throw new GameTestAssertException("Expected container at " + pos);
    }

    private static int countItem(Container container, net.minecraft.world.item.Item item) {
        int count = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static int countInventoryItem(HiredJobInventory inventory, net.minecraft.world.item.Item item) {
        int count = 0;
        for (int slot = 0; slot < HiredJobInventory.SLOT_COUNT; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void configureGameTestStructures() {
        String configured = System.getProperty("villagerretaliation.gameteststructures");
        if (configured != null && !configured.isBlank()) {
            StructureUtils.testStructuresDir = configured;
            return;
        }

        List<Path> candidates = new ArrayList<>();
        candidates.add(Path.of("src/main/gameteststructures"));
        candidates.add(Path.of("../src/main/gameteststructures"));
        candidates.add(Path.of("neoforge/src/main/gameteststructures"));
        for (Path candidate : candidates) {
            if (Files.isDirectory(candidate)) {
                StructureUtils.testStructuresDir = candidate.toAbsolutePath().normalize().toString();
                return;
            }
        }
    }
}
