package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.interaction.work.logging.LoggingWorker;
import com.jvn.villagerretaliation.interaction.work.mining.HiredOreBlockTracker;
import com.jvn.villagerretaliation.interaction.work.mining.MiningWorker;
import com.jvn.villagerretaliation.interaction.work.mining.MiningExcavationSupport;
import com.jvn.villagerretaliation.interaction.work.mining.MiningBlockRules;
import com.jvn.villagerretaliation.interaction.work.brewing.BrewingWorker;
import com.jvn.villagerretaliation.block.VillagerRetaliationBlocks;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.interaction.HiredVillagerContractService;
import com.jvn.villagerretaliation.interaction.HiredVillagerFocusService;
import com.jvn.villagerretaliation.interaction.HiredWorkSession;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredVillagerWorkService;
import com.jvn.villagerretaliation.interaction.HiredMiningMode;
import com.jvn.villagerretaliation.interaction.VillagerCurrencyResources;
import com.jvn.villagerretaliation.interaction.VillagerWalletService;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.inventory.HiredJobInventorySlotType;
import com.jvn.villagerretaliation.villager.VillagerTaskNavigationUtil;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderPaymentEscrowService;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderTaskState;
import com.mojang.authlib.GameProfile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.StructureUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LadderBlock;
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
    public static void pausingBuilderWorkPreservesPaidTask(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildFloor(helper, 0, 6, 0, 6, 1);
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        CompoundTag state = persistentWorkState(villager);
        seedBuilderTask(state, 19, 0);
        HiredWorkerBrain.setState(state, HiredWorkerTaskState.WORKING, helper.absolutePos(new BlockPos(3, 2, 3)));

        HiredVillagerWorkService.pauseWork(level, villager, HiredVillagerRole.BUILDER, "paused_for_test");

        helper.assertTrue(BuilderTaskState.hasTask(state), "temporary builder pause must keep the paid task");
        helper.assertValueEqual(BuilderTaskState.paidCurrency(state), 19, "paid amount should survive pause");
        helper.assertValueEqual(BuilderTaskState.placedIndex(state), 0, "build progress should survive pause");
        helper.assertValueEqual(
                HiredWorkerBrain.snapshot(state, level.getGameTime()).taskState(),
                HiredWorkerTaskState.AWAITING_INSTRUCTION,
                "paused builder should stop active movement without deleting the job");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void cancelingBuilderWorkClearsPaidTaskExplicitly(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildFloor(helper, 0, 6, 0, 6, 1);
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        CompoundTag state = persistentWorkState(villager);
        seedBuilderTask(state, 19, 3);

        HiredVillagerWorkService.cancelWork(level, villager, HiredVillagerRole.BUILDER, "cancelled_for_test");

        helper.assertFalse(BuilderTaskState.hasTask(state), "explicit builder cancellation should clear the task");
        helper.assertValueEqual(
                HiredWorkerBrain.snapshot(state, level.getGameTime()).taskState(),
                HiredWorkerTaskState.AWAITING_INSTRUCTION,
                "cancelled builder should return to awaiting instructions");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void builderEscrowRefundDoesNotDependOnVillagerWallet(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildFloor(helper, 0, 6, 0, 6, 1);
        ServerPlayer hirer = fakePlayer(level, "VrBuilderEscrow");
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        UUID jobId = UUID.randomUUID();
        int beforeCurrency = countCurrency(hirer);
        int walletCurrency = VillagerWalletService.getCurrentEmeralds(villager);
        VillagerWalletService.spendCurrency(villager, walletCurrency, VillagerWalletService.WalletSource.DEBUG);

        BuilderPaymentEscrowService.escrow(villager, jobId, 23);
        int refunded = BuilderPaymentEscrowService.refund(hirer, villager, Optional.of(jobId), 23);
        VillagerWalletService.addCurrency(villager, 50, VillagerWalletService.WalletSource.DEBUG);
        int refundedAgain = BuilderPaymentEscrowService.refund(hirer, villager, Optional.of(jobId), 23);

        helper.assertValueEqual(refunded, 23, "escrow should refund the paid builder amount");
        helper.assertValueEqual(refundedAgain, 0, "builder escrow refund should be idempotent");
        helper.assertValueEqual(countCurrency(hirer) - beforeCurrency, 23, "refund should reach the hirer inventory");
        helper.assertValueEqual(VillagerWalletService.getCurrentEmeralds(villager), 50, "repeated refund should not fall back to wallet funds");
        villager.discard();
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
    public static void pathingCanTargetSaplingsWithoutStandingOnSaplings(GameTestHelper helper) {
        buildFloor(helper, 0, 6, 0, 6, 1);
        Villager villager = spawnVillager(helper, new BlockPos(3, 2, 3));
        ServerLevel level = helper.getLevel();
        tickVillager(level, villager, 20);

        BlockPos sapling = helper.absolutePos(new BlockPos(3, 2, 3));
        setBlock(helper, new BlockPos(3, 2, 3), Blocks.OAK_SAPLING.defaultBlockState());
        villager.moveTo(sapling.getX() + 0.5D, sapling.getY(), sapling.getZ() + 0.5D, 0.0F, 0.0F);

        HiredPathResult result = new HiredMoveToBlockFaceJob(
                level,
                villager,
                List.of(sapling),
                16,
                ignored -> true,
                pos -> !level.getBlockState(pos).is(BlockTags.SAPLINGS),
                pos -> pos.equals(villager.blockPosition()) || !level.getBlockState(pos).is(BlockTags.SAPLINGS),
                ignored -> false).search();

        helper.assertTrue(result.reachesDestination(), "sapling target should still be reachable");
        helper.assertTrue(result.target() != null, "sapling target should have an approach");
        helper.assertFalse(result.target().approachPos().equals(sapling), "approach should not be the sapling block");
        helper.assertFalse(
                level.getBlockState(result.target().approachPos()).is(BlockTags.SAPLINGS),
                "approach should not stand inside any sapling");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 200)
    public static void pathingCanRouteAroundSimpleObstaclesWithoutLeavingTheWorkArea(GameTestHelper helper) {
        buildFloor(helper, 0, 10, 0, 8, 1);
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 4));
        ServerLevel level = helper.getLevel();
        tickVillager(level, villager, 20);
        BlockPos start = helper.absolutePos(new BlockPos(1, 2, 4));
        villager.moveTo(start.getX() + 0.5D, start.getY(), start.getZ() + 0.5D, 0.0F, 0.0F);
        villager.setDeltaMovement(Vec3.ZERO);
        villager.getNavigation().stop();
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

        ItemStack supplyRemainder = inventory.insertSupplyFromStorage(new ItemStack(Items.LADDER, 3));
        helper.assertTrue(supplyRemainder.isEmpty(), "storage-sourced supplies should fit into supply slots");
        ItemStack storedLadders = inventory.findSupply(stack -> stack.is(Items.LADDER));
        helper.assertTrue(HiredJobInventory.isJobItem(storedLadders), "storage-sourced supplies should be tagged as job items");

        inventory.setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_PICKAXE));
        helper.assertTrue(villager.getMainHandItem().is(Items.IRON_PICKAXE), "gear slots should stay synced to villager equipment");
        for (int slot = 6; slot < 18; slot++) {
            if (inventory.getItem(slot).isEmpty()) {
                inventory.setItem(slot, new ItemStack(Items.DIRT, 64));
            }
        }
        ItemStack toolRemainder = inventory.insertToolFromStorage(new ItemStack(Items.DIAMOND_PICKAXE));
        helper.assertFalse(toolRemainder.isEmpty(), "storage tools should not spill into output slots when job slots are full");
        for (int slot = 18; slot < HiredJobInventory.SLOT_COUNT; slot++) {
            helper.assertFalse(inventory.getItem(slot).is(Items.DIAMOND_PICKAXE), "output slot " + slot + " should not hold a storage tool");
            helper.assertFalse(inventory.slotType(slot) == HiredJobInventorySlotType.SUPPLY, "output slot " + slot + " should not become a supply slot");
        }
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void endedContractOverflowBlocksForeignHirerUntilClaimExpires(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildFloor(helper, 0, 4, 0, 4, 1);
        ServerPlayer originalHirer = fakePlayer(level, "VrOverflowOwner");
        ServerPlayer otherHirer = fakePlayer(level, "VrOverflowOther");
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));

        HiredVillagerContractService.startHireContract(level, villager, originalHirer, 1, 8);
        UUID originalContractId = HiredVillagerContractService.currentContractId(villager).orElseThrow();
        HiredJobInventory inventory = HiredJobInventory.getJobInventory(villager);
        helper.assertTrue(inventory.insertSupply(new ItemStack(Items.DIRT, 5)).isEmpty(), "contract supplies should fit");
        helper.assertValueEqual(
                HiredJobInventory.jobItemContractId(inventory.findSupply(stack -> stack.is(Items.DIRT))).orElse(null),
                originalContractId,
                "job supplies should be stamped with the active contract");

        HiredVillagerContractService.endHireContract(level, villager, originalHirer);

        helper.assertTrue(
                HiredVillagerContractService.hasBlockingJobInventoryOverflow(level, villager),
                "leftover removable contract items should create a claim");
        helper.assertFalse(
                HiredVillagerContractService.hasForeignJobInventoryOverflow(level, villager, originalHirer),
                "the previous hirer's own leftovers should not be foreign");
        helper.assertTrue(
                HiredVillagerContractService.hasForeignJobInventoryOverflow(level, villager, otherHirer),
                "other players should be blocked by previous-contract leftovers");
        helper.assertTrue(
                HiredVillagerContractService.canAccessJobInventory(level, villager, originalHirer),
                "the previous hirer should be able to reclaim overflow during the claim window");
        helper.assertFalse(
                HiredVillagerContractService.canAccessJobInventory(level, villager, otherHirer),
                "foreign players should not be able to claim overflow during the claim window");

        CompoundTag claim = villager.getPersistentData().getCompound("VillagerRetaliationJobInventoryOverflowClaim");
        claim.putLong("ExpiresGameTime", level.getGameTime());
        helper.assertFalse(
                HiredVillagerContractService.hasBlockingJobInventoryOverflow(level, villager),
                "expired claims should stop blocking new contracts");

        HiredVillagerContractService.startHireContract(level, villager, otherHirer, 1, 8);
        UUID newContractId = HiredVillagerContractService.currentContractId(villager).orElseThrow();
        HiredJobInventory refreshedInventory = HiredJobInventory.getJobInventory(villager);
        helper.assertValueEqual(
                HiredJobInventory.jobItemContractId(refreshedInventory.findSupply(stack -> stack.is(Items.DIRT))).orElse(null),
                newContractId,
                "expired overflow should be claimed by the next contract that takes it on");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void contractHandoffClearsBrewingOrders(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildFloor(helper, 0, 4, 0, 4, 1);
        ServerPlayer hirer = fakePlayer(level, "VrBrewingOrderOwner");
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));

        HiredVillagerContractService.startHireContract(level, villager, hirer, 1, 8);
        villager.getPersistentData()
                .getCompound("VillagerRetaliationHireContract")
                .putString("Role", HiredVillagerRole.BREWING.serializedName());
        CompoundTag state = new CompoundTag();
        villager.getPersistentData().put(WORK_STATE_TAG, state);
        BrewingWorker.setOrder(
                state,
                ResourceLocation.fromNamespaceAndPath("minecraft", "potion"),
                ResourceLocation.fromNamespaceAndPath("minecraft", "water"),
                3,
                false,
                HiredVillagerContractService.currentContractId(villager).orElse(null));
        helper.assertTrue(BrewingWorker.hasOrder(state), "brewing order should be present before contract end");

        HiredVillagerContractService.endHireContract(level, villager, hirer);

        helper.assertFalse(BrewingWorker.hasOrder(state), "contract end should clear brewing orders from the old contract");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void autoPaymentDoesNotRenewWhileWaitingForOfflineHirer(GameTestHelper helper) {
        buildFloor(helper, 0, 4, 0, 4, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrOfflineRenewal");
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        BlockPos paymentRel = new BlockPos(2, 2, 1);
        BlockPos payment = helper.absolutePos(paymentRel);
        setBlock(helper, paymentRel, VillagerRetaliationBlocks.OAK_PAYMENT_BOX.get().defaultBlockState());
        container(level, payment).setItem(0, new ItemStack(Items.EMERALD, 16));
        AssignedStorageService.removeAssignedContainer(level, payment);
        AssignedStorageService.AssignSummary paymentAssignment = AssignedStorageService.assign(
                hirer,
                villager,
                List.of(new AssignedStorageService.StoragePosition(level.dimension(), payment)),
                AssignedStorageService.PAYMENT_PURPOSE);
        helper.assertValueEqual(paymentAssignment.assigned(), 1, "payment box assignment");

        HiredVillagerContractService.startHireContract(level, villager, hirer, 1, 8);
        HiredVillagerContractService.setAutoPaymentEnabled(villager, true);
        CompoundTag contract = villager.getPersistentData().getCompound("VillagerRetaliationHireContract");
        long expiredAt = level.getGameTime();
        contract.putLong("EndGameTime", expiredAt);
        CompoundTag workState = new CompoundTag();
        workState.putString("Status", HiredVillagerWorkService.WAITING_FOR_HIRER_STATUS);
        villager.getPersistentData().put(WORK_STATE_TAG, workState);

        HiredVillagerContractService.onVillagerTickPost(villager);

        helper.assertValueEqual(countItem(container(level, payment), Items.EMERALD), 16, "offline renewal should not charge");
        helper.assertValueEqual(contract.getLong("EndGameTime"), expiredAt, "offline renewal should not extend time");
        helper.assertValueEqual(contract.getString("Status"), "active", "contract should stay active until normal expiry processing");

        AssignedStorageService.removeAllAssignedStorage(level, villager);
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

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void assignedOutputStorageDepositsFromAdjacentApproachWithoutNudge(GameTestHelper helper) {
        buildFloor(helper, 0, 5, 0, 5, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerStorageNudge");
        movePlayer(helper, hirer, new BlockPos(1, 2, 1));
        Villager villager = spawnVillager(helper, new BlockPos(3, 2, 3));
        BlockPos chestRel = new BlockPos(3, 2, 2);
        BlockPos chest = helper.absolutePos(chestRel);
        setBlock(helper, chestRel, Blocks.CHEST.defaultBlockState());
        AssignedStorageService.removeAssignedContainer(level, chest);
        AssignedStorageService.assign(
                hirer,
                villager,
                List.of(new AssignedStorageService.StoragePosition(level.dimension(), chest)),
                AssignedStorageService.OUTPUT_PURPOSE);

        BlockPos approach = helper.absolutePos(new BlockPos(3, 2, 3));
        villager.moveTo(approach.getX() + 0.18D, approach.getY(), approach.getZ() + 0.82D, 0.0F, 0.0F);
        HiredJobInventory inventory = HiredJobInventory.getJobInventory(villager);
        inventory.insertOutput(new ItemStack(Items.COBBLESTONE, 11));

        helper.assertTrue(
                AssignedStorageService.canInteractWithAssignedStorage(villager, chest),
                "off-center villager should still be able to interact with adjacent assigned storage");
        helper.assertTrue(
                inventory.depositOutputToAssignedStorageAt(chest),
                "off-center adjacent villager should deposit without needing a nudge");
        helper.assertValueEqual(countItem(container(level, chest), Items.COBBLESTONE), 11, "adjacent output deposit count");

        AssignedStorageService.removeAllAssignedStorage(level, villager);
        villager.discard();
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

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 420)
    public static void miningWorkerDepositsAndReturnsToExposedOreWorkArea(GameTestHelper helper) {
        buildFloor(helper, 0, 8, 0, 5, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerExposedDeposit");
        movePlayer(helper, hirer, new BlockPos(1, 2, 1));
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        HiredOreBlockTracker.clearRuntimeState();
        HiredRoleWorkerRegistry.clearRuntimeState();

        BlockPos firstOreRel = new BlockPos(3, 2, 2);
        BlockPos secondOreRel = new BlockPos(4, 2, 2);
        BlockPos chestRel = new BlockPos(7, 2, 2);
        BlockPos chest = helper.absolutePos(chestRel);
        setBlock(helper, firstOreRel, Blocks.COAL_ORE.defaultBlockState());
        setBlock(helper, secondOreRel, Blocks.COAL_ORE.defaultBlockState());
        setBlock(helper, chestRel, Blocks.CHEST.defaultBlockState());
        AssignedStorageService.removeAssignedContainer(level, chest);
        AssignedStorageService.assign(
                hirer,
                villager,
                List.of(new AssignedStorageService.StoragePosition(level.dimension(), chest)),
                AssignedStorageService.OUTPUT_PURPOSE);

        CompoundTag state = new CompoundTag();
        HiredWorkContext context = context(helper, villager, state, new BlockPos(1, 2, 1), new BlockPos(5, 4, 4), true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.DIAMOND_PICKAXE));
        for (int slot = 18; slot < HiredJobInventory.SLOT_COUNT; slot++) {
            context.inventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        context.inventory().setItem(18, new ItemStack(Items.COAL, 63));
        MiningWorker worker = new MiningWorker();

        runWorkerUntil(helper, worker, level, villager, hirer, context, 360, () ->
                level.getBlockState(helper.absolutePos(firstOreRel)).isAir()
                        && level.getBlockState(helper.absolutePos(secondOreRel)).isAir()
                        && countItem(container(level, chest), Items.COAL) > 0);

        helper.assertTrue(level.getBlockState(helper.absolutePos(firstOreRel)).isAir(),
                "exposed miner should break the first ore before depositing");
        helper.assertTrue(level.getBlockState(helper.absolutePos(secondOreRel)).isAir(),
                "exposed miner should return from storage and break the queued ore");
        helper.assertTrue(countItem(container(level, chest), Items.COAL) > 0,
                "exposed miner should deposit filled output to the assigned chest");
        helper.assertTrue(context.isInsideWorkArea(villager.blockPosition()),
                "exposed miner should resume from inside the assigned work area");

        AssignedStorageService.removeAllAssignedStorage(level, villager);
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
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.DIAMOND_PICKAXE));
        WorkResult fullOutput = new MiningWorker().tick(level, villager, hirer, context);
        HiredWorkerBrain.Snapshot fullOutputState = HiredWorkerBrain.snapshot(state, level.getGameTime());
        helper.assertValueEqual(fullOutputState.taskState(), HiredWorkerTaskState.PAUSED_FULL_INVENTORY, "full output state");
        helper.assertValueEqual(fullOutputState.failureReason(), "output_inventory_full", "full output reason");
        helper.assertValueEqual(fullOutput.status(), "interaction.work.mining.output_full_blocked", "full output status");
        helper.assertTrue(level.getBlockState(helper.absolutePos(new BlockPos(3, 2, 2))).is(Blocks.COAL_ORE),
                "miner should not break ore when output cannot fit");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 200)
    public static void miningWorkerExcavatesTopLayerWithoutLadders(GameTestHelper helper) {
        buildFloor(helper, 0, 6, 0, 6, 0);
        buildFloor(helper, 0, 6, 0, 6, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerExcavateTop");
        Villager villager = spawnVillager(helper, new BlockPos(3, 2, 3));
        BlockPos targetRel = new BlockPos(3, 1, 3);

        CompoundTag state = new CompoundTag();
        state.putString(HiredMiningMode.STATE_TAG, HiredMiningMode.EXCAVATE_AREA.serializedName());
        HiredWorkContext context = context(helper, villager, state, targetRel, targetRel, true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.DIAMOND_PICKAXE));
        MiningWorker worker = new MiningWorker();

        runWorkerUntil(helper, worker, level, villager, hirer, context, 80, () ->
                level.getBlockState(helper.absolutePos(targetRel)).isAir());

        helper.assertTrue(level.getBlockState(helper.absolutePos(targetRel)).isAir(), "top excavation layer should be mined without ladders");
        helper.assertTrue(context.inventory().hasOutput(stack -> stack.is(Items.COBBLESTONE)), "excavation drops should be stored as output");
        helper.assertFalse(
                HiredWorkerBrain.snapshot(state, level.getGameTime()).failureReason().equals("missing_ladders"),
                "top layer excavation should not pause for missing ladders");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void miningWorkerDoesNotMineUnsafeBlockUnderfoot(GameTestHelper helper) {
        buildFloor(helper, 0, 4, 0, 4, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerUnsafeOre");
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        HiredOreBlockTracker.clearRuntimeState();
        HiredRoleWorkerRegistry.clearRuntimeState();
        BlockPos oreRel = new BlockPos(2, 1, 2);
        BlockPos lavaRel = new BlockPos(2, 0, 2);
        setBlock(helper, oreRel, Blocks.COAL_ORE.defaultBlockState());
        setBlock(helper, lavaRel, Blocks.LAVA.defaultBlockState());

        CompoundTag state = new CompoundTag();
        HiredWorkContext context = context(helper, villager, state, oreRel, new BlockPos(2, 2, 2), true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.DIAMOND_PICKAXE));
        MiningWorker worker = new MiningWorker();

        for (int tick = 0; tick < 40; tick++) {
            worker.maintain(level, villager, context);
            worker.tick(level, villager, hirer, context);
            level.tickNonPassenger(villager);
        }

        helper.assertTrue(level.getBlockState(helper.absolutePos(oreRel)).is(Blocks.COAL_ORE),
                "miner should not break an ore directly underfoot when lava is below");
        helper.assertTrue(level.getBlockState(helper.absolutePos(lavaRel)).is(Blocks.LAVA),
                "unsafe landing lava should remain covered by the ore");
        helper.assertValueEqual(countInventoryItem(context.inventory(), Items.COAL), 0,
                "unsafe underfoot ore should not produce job output");

        villager.discard();
        HiredOreBlockTracker.clearRuntimeState();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120)
    public static void miningWorkerDoesNotFetchUnusedTorchSupplyBeforeMining(GameTestHelper helper) {
        buildFloor(helper, 0, 8, 0, 6, 0);
        buildFloor(helper, 0, 8, 0, 6, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerNoTorchFetch");
        Villager villager = spawnVillager(helper, new BlockPos(3, 2, 3));
        BlockPos targetRel = new BlockPos(3, 1, 3);
        BlockPos chestRel = new BlockPos(7, 2, 3);
        BlockPos chest = helper.absolutePos(chestRel);
        setBlock(helper, chestRel, Blocks.CHEST.defaultBlockState());
        container(level, chest).setItem(0, new ItemStack(Items.TORCH, 16));
        AssignedStorageService.removeAssignedContainer(level, chest);
        AssignedStorageService.assign(
                hirer,
                villager,
                List.of(new AssignedStorageService.StoragePosition(level.dimension(), chest)),
                AssignedStorageService.GENERAL_PURPOSE);

        CompoundTag state = new CompoundTag();
        state.putString(HiredMiningMode.STATE_TAG, HiredMiningMode.EXCAVATE_AREA.serializedName());
        HiredWorkContext context = context(helper, villager, state, targetRel, targetRel, true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.DIAMOND_PICKAXE));
        MiningWorker worker = new MiningWorker();

        WorkResult result = worker.tick(level, villager, hirer, context);
        HiredWorkerBrain.Snapshot snapshot = HiredWorkerBrain.snapshot(state, level.getGameTime());
        helper.assertFalse(snapshot.taskState() == HiredWorkerTaskState.MOVING_TO_STORAGE,
                "miner should not walk to storage only because optional torches are present; status=" + result.status());
        helper.assertTrue(snapshot.storageTargetPos() == null,
                "miner should not set a storage target for unused torch supply");

        runWorkerUntil(helper, worker, level, villager, hirer, context, 80, () ->
                level.getBlockState(helper.absolutePos(targetRel)).isAir());
        helper.assertTrue(level.getBlockState(helper.absolutePos(targetRel)).isAir(),
                "miner should mine the available target before fetching optional torch supply");
        helper.assertValueEqual(countItem(container(level, chest), Items.TORCH), 16,
                "unused torch stack should remain in storage");

        AssignedStorageService.removeAllAssignedStorage(level, villager);
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120)
    public static void miningWorkerPausesWhenLadderStorageCannotFitSupply(GameTestHelper helper) {
        buildFloor(helper, 0, 8, 0, 6, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerLadderSupplyFull");
        Villager villager = spawnVillager(helper, new BlockPos(6, 2, 3));
        BlockPos targetRel = new BlockPos(3, 1, 3);
        BlockPos chestRel = new BlockPos(7, 2, 3);
        BlockPos chest = helper.absolutePos(chestRel);
        setBlock(helper, chestRel, Blocks.CHEST.defaultBlockState());
        container(level, chest).setItem(0, new ItemStack(Items.LADDER, 8));
        AssignedStorageService.removeAssignedContainer(level, chest);
        AssignedStorageService.assign(
                hirer,
                villager,
                List.of(new AssignedStorageService.StoragePosition(level.dimension(), chest)),
                AssignedStorageService.GENERAL_PURPOSE);

        CompoundTag state = new CompoundTag();
        state.putString(HiredMiningMode.STATE_TAG, HiredMiningMode.EXCAVATE_AREA.serializedName());
        HiredWorkContext context = context(helper, villager, state, targetRel, new BlockPos(3, 2, 3), true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.DIAMOND_PICKAXE));
        for (int slot : context.inventory().supplySlots()) {
            context.inventory().setItem(slot, new ItemStack(Items.STICK, 64));
        }

        WorkResult result = new MiningWorker().tick(level, villager, hirer, context);
        HiredWorkerBrain.Snapshot snapshot = HiredWorkerBrain.snapshot(state, level.getGameTime());
        helper.assertValueEqual(result.status(), "interaction.work.mining.support.inventory_full", "ladder supply full status");
        helper.assertValueEqual(snapshot.taskState(), HiredWorkerTaskState.PAUSED_FULL_INVENTORY, "ladder supply full task state");
        helper.assertValueEqual(snapshot.failureReason(), "support_inventory_full", "ladder supply full reason");
        helper.assertTrue(snapshot.storageTargetPos() == null,
                "miner should not keep a storage target when it cannot accept required ladder supplies");
        helper.assertValueEqual(countItem(container(level, chest), Items.LADDER), 8,
                "unaccepted ladders should remain in storage");
        helper.assertValueEqual(countInventoryItem(context.inventory(), Items.LADDER), 0,
                "job inventory should not pretend it gathered ladders");

        AssignedStorageService.removeAllAssignedStorage(level, villager);
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void miningWorkerDoesNotPlaceTopLayerLadderSupportWhenSupplied(GameTestHelper helper) {
        buildFloor(helper, 0, 6, 0, 6, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerTopNoLadder");
        Villager villager = spawnVillager(helper, new BlockPos(3, 2, 3));
        BlockPos ladderRel = new BlockPos(2, 1, 2);
        BlockPos backingRel = new BlockPos(2, 1, 1);
        setBlock(helper, ladderRel, Blocks.AIR.defaultBlockState());
        setBlock(helper, backingRel, Blocks.AIR.defaultBlockState());

        CompoundTag state = new CompoundTag();
        state.putString(HiredMiningMode.STATE_TAG, HiredMiningMode.EXCAVATE_AREA.serializedName());
        HiredWorkContext context = context(helper, villager, state, new BlockPos(2, 1, 2), new BlockPos(4, 1, 4), true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.DIAMOND_PICKAXE));
        context.inventory().insertSupply(new ItemStack(Items.LADDER, 8));
        context.inventory().insertOutput(new ItemStack(Items.COBBLESTONE, 4));

        WorkResult result = new MiningWorker().tick(level, villager, hirer, context);
        helper.assertFalse(level.getBlockState(helper.absolutePos(ladderRel)).is(Blocks.LADDER),
                "top excavation layer should not place a ladder even when ladders are supplied");
        helper.assertTrue(level.getBlockState(helper.absolutePos(backingRel)).isAir(),
                "top excavation layer should not spend mined blocks on ladder backing");
        helper.assertValueEqual(countInventoryItem(context.inventory(), Items.LADDER), 8, "top layer ladder supply count");
        helper.assertTrue(result.progressed(), "top layer miner should keep working instead of placing support");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void miningWorkerDoesNotPlaceTopLayerTorchSupportWhenSupplied(GameTestHelper helper) {
        buildFloor(helper, 0, 6, 0, 6, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerTopNoTorch");
        Villager villager = spawnVillager(helper, new BlockPos(3, 2, 3));
        BlockPos torchRel = new BlockPos(3, 1, 2);
        BlockPos backingRel = new BlockPos(3, 1, 1);
        setBlock(helper, torchRel, Blocks.AIR.defaultBlockState());
        setBlock(helper, backingRel, Blocks.AIR.defaultBlockState());

        CompoundTag state = new CompoundTag();
        state.putString(HiredMiningMode.STATE_TAG, HiredMiningMode.EXCAVATE_AREA.serializedName());
        HiredWorkContext context = context(helper, villager, state, new BlockPos(2, 1, 2), new BlockPos(4, 1, 4), true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.DIAMOND_PICKAXE));
        context.inventory().insertSupply(new ItemStack(Items.TORCH, 8));
        context.inventory().insertOutput(new ItemStack(Items.COBBLESTONE, 4));

        WorkResult result = new MiningWorker().tick(level, villager, hirer, context);
        helper.assertFalse(level.getBlockState(helper.absolutePos(torchRel)).is(Blocks.WALL_TORCH),
                "top excavation layer should not place a wall torch support");
        helper.assertTrue(level.getBlockState(helper.absolutePos(backingRel)).isAir(),
                "top excavation layer should not spend mined blocks on torch backing");
        helper.assertValueEqual(countInventoryItem(context.inventory(), Items.TORCH), 8, "top layer torch supply count");
        helper.assertTrue(result.progressed(), "top layer miner should keep working instead of placing support");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void miningWorkerRequiresLaddersBeforeExcavatingBelowTopLayer(GameTestHelper helper) {
        buildFloor(helper, 0, 6, 0, 6, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerExcavateDeep");
        Villager villager = spawnVillager(helper, new BlockPos(3, 2, 3));
        BlockPos targetRel = new BlockPos(3, 1, 3);

        CompoundTag state = new CompoundTag();
        state.putString(HiredMiningMode.STATE_TAG, HiredMiningMode.EXCAVATE_AREA.serializedName());
        HiredWorkContext context = context(helper, villager, state, targetRel, new BlockPos(3, 2, 3), true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.DIAMOND_PICKAXE));

        WorkResult result = new MiningWorker().tick(level, villager, hirer, context);
        HiredWorkerBrain.Snapshot snapshot = HiredWorkerBrain.snapshot(state, level.getGameTime());
        helper.assertValueEqual(snapshot.taskState(), HiredWorkerTaskState.WAITING_FOR_MATERIALS, "missing ladder task state");
        helper.assertValueEqual(snapshot.failureReason(), "missing_ladders", "missing ladder reason");
        helper.assertValueEqual(result.status(), "interaction.work.mining.support.missing_ladders", "missing ladder status");
        helper.assertTrue(level.getBlockState(helper.absolutePos(targetRel)).is(Blocks.STONE),
                "miner should not dig deeper excavation layers without ladder access");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120)
    public static void miningWorkerPlacesLadderSupportBeforeExcavatingBelowTopLayer(GameTestHelper helper) {
        buildFloor(helper, 0, 6, 0, 6, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerDeepLadder");
        Villager villager = spawnVillager(helper, new BlockPos(3, 3, 3));
        BlockPos ladderRel = new BlockPos(2, 2, 2);
        setBlock(helper, ladderRel, Blocks.AIR.defaultBlockState());
        setBlock(helper, new BlockPos(2, 1, 2), Blocks.AIR.defaultBlockState());
        setBlock(helper, new BlockPos(3, 1, 3), Blocks.STONE.defaultBlockState());

        CompoundTag state = new CompoundTag();
        state.putString(HiredMiningMode.STATE_TAG, HiredMiningMode.EXCAVATE_AREA.serializedName());
        HiredWorkContext context = context(helper, villager, state, new BlockPos(2, 1, 2), new BlockPos(4, 2, 4), true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.DIAMOND_PICKAXE));
        context.inventory().insertSupply(new ItemStack(Items.LADDER, 8));
        context.inventory().insertOutput(new ItemStack(Items.COBBLESTONE, 4));

        WorkResult result = new MiningWorker().tick(level, villager, hirer, context);
        helper.assertValueEqual(result.status(), "interaction.work.mining.support.placed_ladder", "deep layer ladder support status");
        helper.assertTrue(level.getBlockState(helper.absolutePos(ladderRel)).is(Blocks.LADDER),
                "lower excavation layer should place ladder support before mining downward");
        helper.assertTrue(level.getBlockState(helper.absolutePos(new BlockPos(2, 2, 1))).is(Blocks.COBBLESTONE),
                "lower excavation ladder may spend a mined block on needed backing");
        helper.assertValueEqual(countInventoryItem(context.inventory(), Items.LADDER), 7, "deep layer ladder supply count");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120)
    public static void miningWorkerStopsLadderSupportAtSurfaceWhenWorkAreaExtendsAbove(GameTestHelper helper) {
        buildFloor(helper, 0, 6, 0, 6, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerSurfaceLadder");
        Villager villager = spawnVillager(helper, new BlockPos(3, 3, 3));
        BlockPos surfaceLadderRel = new BlockPos(2, 2, 2);
        BlockPos targetRel = new BlockPos(3, 1, 3);
        for (int y = 2; y <= 5; y++) {
            setBlock(helper, new BlockPos(2, y, 2), Blocks.AIR.defaultBlockState());
            setBlock(helper, new BlockPos(2, y, 1), Blocks.AIR.defaultBlockState());
        }
        setBlock(helper, targetRel, Blocks.STONE.defaultBlockState());

        CompoundTag state = new CompoundTag();
        state.putString(HiredMiningMode.STATE_TAG, HiredMiningMode.EXCAVATE_AREA.serializedName());
        HiredWorkContext context = context(helper, villager, state, new BlockPos(2, 1, 2), new BlockPos(4, 5, 4), true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.DIAMOND_PICKAXE));
        context.inventory().insertSupply(new ItemStack(Items.LADDER, 8));
        context.inventory().insertOutput(new ItemStack(Items.COBBLESTONE, 4));

        WorkResult result = new MiningWorker().tick(level, villager, hirer, context);
        helper.assertValueEqual(result.status(), "interaction.work.mining.support.placed_ladder", "surface-clamped ladder status");
        helper.assertTrue(level.getBlockState(helper.absolutePos(surfaceLadderRel)).is(Blocks.LADDER),
                "ladder support should start at the first surface dismount");
        for (int y = 3; y <= 5; y++) {
            helper.assertFalse(level.getBlockState(helper.absolutePos(new BlockPos(2, y, 2))).is(Blocks.LADDER),
                    "ladder support should not be placed above the surface dismount at y=" + y);
        }
        helper.assertValueEqual(countInventoryItem(context.inventory(), Items.LADDER), 7, "surface-clamped ladder supply count");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 200)
    public static void miningWorkerReplansLowerExcavationTargetInsteadOfMiningFromOutsideWorkArea(GameTestHelper helper) {
        buildFloor(helper, 0, 6, 0, 6, 1);
        buildFloor(helper, 0, 6, 0, 6, 0);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerExcavateBounds");
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 3));
        tickVillager(level, villager, 20);
        BlockPos outsideStart = helper.absolutePos(new BlockPos(1, 2, 3));
        villager.moveTo(outsideStart.getX() + 0.5D, outsideStart.getY(), outsideStart.getZ() + 0.5D, 0.0F, 0.0F);
        BlockPos targetRel = new BlockPos(2, 1, 3);

        for (int y = 1; y <= 3; y++) {
            setBlock(helper, new BlockPos(2, y, 1), Blocks.STONE.defaultBlockState());
            setBlock(
                    helper,
                    new BlockPos(2, y, 2),
                    Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
        }
        setBlock(helper, new BlockPos(2, 2, 3), Blocks.AIR.defaultBlockState());
        setBlock(helper, new BlockPos(2, 3, 3), Blocks.AIR.defaultBlockState());
        setBlock(helper, targetRel, Blocks.STONE.defaultBlockState());

        CompoundTag state = new CompoundTag();
        state.putString(HiredMiningMode.STATE_TAG, HiredMiningMode.EXCAVATE_AREA.serializedName());
        HiredWorkContext context = context(helper, villager, state, new BlockPos(2, 1, 2), new BlockPos(4, 3, 4), true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.DIAMOND_PICKAXE));
        MiningWorker worker = new MiningWorker();
        BlockPos target = helper.absolutePos(targetRel);
        BlockPos pathOrigin = villager.blockPosition().immutable();

        helper.assertTrue(MiningBlockRules.isMineableExcavationBlock(level, target), "fixture target should be mineable");
        helper.assertTrue(MiningBlockRules.isCurrentExcavationLayer(level, context, target), "fixture target should be the current layer");
        BlockPos insideApproach = helper.absolutePos(new BlockPos(2, 2, 3));
        helper.assertTrue(HiredMoveToBlockFaceJob.isValidApproachPosition(level, insideApproach), "fixture inside approach should be walkable");
        HiredPathResult constrainedPath = new HiredMoveToBlockFaceJob(
                level,
                villager,
                List.of(target),
                20,
                context::isInsideWorkArea,
                context::isInsideWorkArea,
                pos -> context.isInsideWorkArea(pos) || pos.equals(pathOrigin),
                ignored -> false,
                (candidateTarget, approach) -> MiningExcavationSupport.hasCompleteLadderRouteToLayer(
                        level,
                        context,
                        candidateTarget.getY())
                        && MiningExcavationSupport.entryTarget(level, context) != null
                        && MiningExcavationSupport.shouldUseLadderFallback(
                                context,
                                villager,
                                new HiredPathTarget(candidateTarget.immutable(), approach.immutable(), candidateTarget.getCenter()))).search();
        helper.assertTrue(constrainedPath.reachesDestination(), "fixture target should have an inside lower-layer approach");

        WorkResult firstTick = worker.tick(level, villager, hirer, context);
        HiredWorkerBrain.Snapshot firstSnapshot = HiredWorkerBrain.snapshot(state, level.getGameTime());
        helper.assertTrue(
                firstTick.progressed(),
                "outside lower-layer target should be converted into movement progress, status=" + firstTick.status());
        helper.assertTrue(
                firstSnapshot.taskState() == HiredWorkerTaskState.RETURNING_TO_WORK_AREA
                        || firstSnapshot.taskState() == HiredWorkerTaskState.MOVING_TO_TARGET,
                "outside lower-layer task state should return or resume safely, state=" + firstSnapshot.taskState());
        helper.assertTrue(level.getBlockState(helper.absolutePos(targetRel)).is(Blocks.STONE),
                "miner should not start breaking a lower excavation layer while standing outside the work area");
        BlockPos navigationTarget = villager.getNavigation().getTargetPos();
        helper.assertTrue(navigationTarget == null
                        || context.isInsideWorkArea(navigationTarget)
                        || navigationTarget.getY() == context.workMax().getY() + 1,
                "lower excavation navigation target should stay inside the assigned work area or on the ladder return landing");

        runWorkerUntil(helper, worker, level, villager, hirer, context, 100, () ->
                level.getBlockState(helper.absolutePos(targetRel)).isAir());
        helper.assertTrue(level.getBlockState(helper.absolutePos(targetRel)).isAir(),
                "miner should still recover and mine the lower target from a valid approach");
        helper.assertTrue(context.inventory().hasOutput(stack -> stack.is(Items.COBBLESTONE)),
                "recovered excavation should store drops as output");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 260)
    public static void miningWorkerDescendsLadderAndMinesLowerExcavationTarget(GameTestHelper helper) {
        buildFloor(helper, 0, 6, 0, 6, 1);
        buildFloor(helper, 0, 6, 0, 6, 0);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerDescendMine");
        Villager villager = spawnVillager(helper, new BlockPos(2, 4, 2));
        BlockPos targetRel = new BlockPos(3, 1, 3);

        for (int y = 1; y <= 3; y++) {
            setBlock(helper, new BlockPos(2, y, 1), Blocks.STONE.defaultBlockState());
            setBlock(
                    helper,
                    new BlockPos(2, y, 2),
                    Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
        }
        for (int x = 2; x <= 4; x++) {
            for (int z = 2; z <= 4; z++) {
                if (x == 2 && z == 2) {
                    continue;
                }
                setBlock(helper, new BlockPos(x, 1, z), Blocks.AIR.defaultBlockState());
            }
        }
        setBlock(helper, new BlockPos(2, 1, 3), Blocks.BEDROCK.defaultBlockState());
        setBlock(helper, new BlockPos(3, 1, 2), Blocks.BEDROCK.defaultBlockState());
        setBlock(helper, new BlockPos(3, 2, 3), Blocks.AIR.defaultBlockState());
        setBlock(helper, new BlockPos(3, 3, 3), Blocks.AIR.defaultBlockState());
        setBlock(helper, targetRel, Blocks.STONE.defaultBlockState());

        CompoundTag state = new CompoundTag();
        state.putString(HiredMiningMode.STATE_TAG, HiredMiningMode.EXCAVATE_AREA.serializedName());
        HiredWorkContext context = context(helper, villager, state, new BlockPos(2, 1, 2), new BlockPos(4, 3, 4), true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.DIAMOND_PICKAXE));
        MiningWorker worker = new MiningWorker();

        runWorkerUntil(helper, worker, level, villager, hirer, context, 180, () ->
                level.getBlockState(helper.absolutePos(targetRel)).isAir());

        helper.assertTrue(level.getBlockState(helper.absolutePos(targetRel)).isAir(),
                "miner should descend the ladder and mine the lower excavation target");
        helper.assertTrue(context.inventory().hasOutput(stack -> stack.is(Items.COBBLESTONE)),
                "descended excavation should store drops as output");
        helper.assertTrue(context.isInsideWorkArea(villager.blockPosition()),
                "miner should end the downward path inside the excavation work area");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 260)
    public static void miningWorkerDescendsAfterBeingPushedToUpperLadderLanding(GameTestHelper helper) {
        buildFloor(helper, 0, 6, 0, 6, 0);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerPushDescend");
        Villager villager = spawnVillager(helper, new BlockPos(2, 5, 3));
        BlockPos targetRel = new BlockPos(3, 1, 3);

        for (int y = 1; y <= 4; y++) {
            setBlock(helper, new BlockPos(2, y, 1), Blocks.STONE.defaultBlockState());
            setBlock(
                    helper,
                    new BlockPos(2, y, 2),
                    Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
        }
        for (int y = 1; y <= 4; y++) {
            for (int x = 2; x <= 4; x++) {
                for (int z = 2; z <= 4; z++) {
                    if (x == 2 && z == 2) {
                        continue;
                    }
                    setBlock(helper, new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                }
            }
        }
        setBlock(helper, new BlockPos(2, 4, 3), Blocks.BEDROCK.defaultBlockState());
        setBlock(helper, new BlockPos(3, 2, 2), Blocks.BEDROCK.defaultBlockState());
        setBlock(helper, targetRel, Blocks.STONE.defaultBlockState());

        CompoundTag state = new CompoundTag();
        state.putString(HiredMiningMode.STATE_TAG, HiredMiningMode.EXCAVATE_AREA.serializedName());
        HiredWorkContext context = context(helper, villager, state, new BlockPos(2, 1, 2), new BlockPos(4, 4, 4), true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.DIAMOND_PICKAXE));
        MiningWorker worker = new MiningWorker();
        BlockPos target = helper.absolutePos(targetRel);

        WorkResult firstTick = worker.tick(level, villager, hirer, context);
        helper.assertTrue(firstTick.progressed(), "lower target recovery should start movement");

        runWorkerUntil(helper, worker, level, villager, hirer, context, 120, () ->
                state.contains("ActiveWorkApproachPos"));
        helper.assertTrue(state.contains("ActiveWorkApproachPos"),
                "miner should select a lower-layer approach after descending from the upper landing");
        BlockPos activeApproach = BlockPos.of(state.getLong("ActiveWorkApproachPos"));
        helper.assertTrue(activeApproach.getY() <= target.getY() + 1,
                "lower excavation target should not use the upper ladder landing as its mining stance");
        helper.assertTrue(level.getBlockState(target).is(Blocks.STONE),
                "lower target should still exist before the forced displacement recovery step");

        BlockPos pushedRel = new BlockPos(2, 5, 3);
        BlockPos pushed = helper.absolutePos(pushedRel);
        villager.moveTo(pushed.getX() + 0.5D, pushed.getY(), pushed.getZ() + 0.5D, 0.0F, 0.0F);
        VillagerTaskNavigationUtil.stopHiredNavigation(villager);

        runWorkerUntil(helper, worker, level, villager, hirer, context, 200, () ->
                level.getBlockState(target).isAir());

        helper.assertTrue(level.getBlockState(target).isAir(),
                "miner pushed to the upper landing should descend and mine the lower excavation target");
        helper.assertTrue(context.inventory().hasOutput(stack -> stack.is(Items.COBBLESTONE)),
                "pushed descent excavation should store drops as output");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 220)
    public static void ladderNavigationAllowsImmediateDescentAfterSurfaceDismount(GameTestHelper helper) {
        buildFloor(helper, 0, 5, 0, 5, 0);
        ServerLevel level = helper.getLevel();

        for (int y = 1; y <= 3; y++) {
            setBlock(helper, new BlockPos(2, y, 1), Blocks.STONE.defaultBlockState());
            setBlock(
                    helper,
                    new BlockPos(2, y, 2),
                    Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
        }
        setBlock(helper, new BlockPos(3, 0, 2), Blocks.STONE.defaultBlockState());
        setBlock(helper, new BlockPos(3, 1, 2), Blocks.AIR.defaultBlockState());
        setBlock(helper, new BlockPos(3, 2, 2), Blocks.AIR.defaultBlockState());
        setBlock(helper, new BlockPos(3, 3, 2), Blocks.STONE.defaultBlockState());
        setBlock(helper, new BlockPos(3, 4, 2), Blocks.AIR.defaultBlockState());
        setBlock(helper, new BlockPos(3, 5, 2), Blocks.AIR.defaultBlockState());

        Villager villager = spawnVillager(helper, new BlockPos(3, 1, 2));
        BlockPos topDismount = helper.absolutePos(new BlockPos(3, 4, 2));
        BlockPos lowerDismount = helper.absolutePos(new BlockPos(3, 1, 2));

        for (int tick = 0; tick < 100 && !villager.blockPosition().equals(topDismount); tick++) {
            helper.assertTrue(
                    VillagerTaskNavigationUtil.moveTowardNearbyLadderThenClimb(level, villager, topDismount, 0.55D),
                    "ladder helper should keep climbing toward the surface dismount");
            VillagerTaskNavigationUtil.tickPathLadders(level, villager);
            level.tickNonPassenger(villager);
        }
        helper.assertValueEqual(villager.blockPosition(), topDismount, "villager should reach the top ladder dismount");

        helper.assertTrue(
                VillagerTaskNavigationUtil.moveTowardNearbyLadderThenClimb(level, villager, lowerDismount, 0.55D),
                "recent top dismount should allow intentional immediate descent");

        for (int tick = 0; tick < 100 && !villager.blockPosition().equals(lowerDismount); tick++) {
            helper.assertTrue(
                    VillagerTaskNavigationUtil.moveTowardNearbyLadderThenClimb(level, villager, lowerDismount, 0.55D),
                    "ladder helper should keep descending after reversing from storage height");
            VillagerTaskNavigationUtil.tickPathLadders(level, villager);
            level.tickNonPassenger(villager);
        }
        helper.assertValueEqual(villager.blockPosition(), lowerDismount, "villager should return to the lower ladder dismount");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 1400)
    public static void ladderNavigationMovesFromEveryColumnHeightToTopAndBottom(GameTestHelper helper) {
        buildTallLadderFixture(helper, 2, 1, 2, 6);
        ServerLevel level = helper.getLevel();
        Villager villager = spawnVillager(helper, new BlockPos(3, 1, 2));
        BlockPos bottomDismount = helper.absolutePos(new BlockPos(3, 1, 2));
        BlockPos topDismount = helper.absolutePos(new BlockPos(3, 7, 2));

        for (int y = 1; y <= 6; y++) {
            moveVillagerToBlock(villager, helper.absolutePos(new BlockPos(2, y, 2)));
            runLadderNavigationUntil(helper, level, villager, topDismount, 120,
                    "villager should climb from ladder y=" + y + " to the top dismount");

            moveVillagerToBlock(villager, helper.absolutePos(new BlockPos(2, y, 2)));
            runLadderNavigationUntil(helper, level, villager, bottomDismount, 120,
                    "villager should descend from ladder y=" + y + " to the bottom dismount");
        }

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 240)
    public static void ladderNavigationBottomDismountClearsClimbStateAndStaysLanded(GameTestHelper helper) {
        buildTallLadderFixture(helper, 2, 1, 2, 6);
        ServerLevel level = helper.getLevel();
        Villager villager = spawnVillager(helper, new BlockPos(2, 6, 2));
        BlockPos bottomDismount = helper.absolutePos(new BlockPos(3, 1, 2));

        runLadderNavigationUntil(helper, level, villager, bottomDismount, 160,
                "villager should descend to the bottom dismount");
        helper.assertFalse(villager.isNoGravity(), "bottom dismount should clear ladder no-gravity");
        helper.assertTrue(Math.abs(villager.getDeltaMovement().y) < 0.08D,
                "bottom dismount should not keep descent velocity");

        double landedY = villager.getY();
        for (int tick = 0; tick < 20; tick++) {
            level.tickNonPassenger(villager);
        }
        helper.assertValueEqual(villager.blockPosition(), bottomDismount, "villager should stay on the bottom dismount");
        helper.assertTrue(Math.abs(villager.getY() - landedY) < 0.15D,
                "villager should not bob after bottom dismount");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120)
    public static void ladderNavigationBottomRungSnapsToLowerLandingWithoutFalling(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int y = 1; y <= 4; y++) {
            setBlock(helper, new BlockPos(2, y, 1), Blocks.STONE.defaultBlockState());
            setBlock(
                    helper,
                    new BlockPos(2, y, 2),
                    Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
        }
        setBlock(helper, new BlockPos(3, -1, 2), Blocks.STONE.defaultBlockState());
        setBlock(helper, new BlockPos(3, 0, 2), Blocks.AIR.defaultBlockState());
        setBlock(helper, new BlockPos(3, 1, 2), Blocks.AIR.defaultBlockState());
        setBlock(helper, new BlockPos(3, 2, 2), Blocks.AIR.defaultBlockState());

        Villager villager = spawnVillager(helper, new BlockPos(2, 1, 2));
        BlockPos lowerLanding = helper.absolutePos(new BlockPos(3, 0, 2));
        moveVillagerToBlock(villager, helper.absolutePos(new BlockPos(2, 1, 2)));

        helper.assertTrue(
                VillagerTaskNavigationUtil.moveTowardNearbyLadderThenClimb(level, villager, lowerLanding, 0.55D),
                "bottom rung dismount should be handled by the ladder helper");
        helper.assertValueEqual(villager.blockPosition(), lowerLanding,
                "bottom rung dismount should snap to the lower landing instead of drifting over the gap");
        helper.assertFalse(villager.isNoGravity(), "lower landing snap should clear ladder no-gravity");

        for (int tick = 0; tick < 20; tick++) {
            level.tickNonPassenger(villager);
            helper.assertTrue(villager.getY() >= lowerLanding.getY() - 0.05D,
                    "villager should not fall below the lower ladder landing");
        }
        helper.assertValueEqual(villager.blockPosition(), lowerLanding,
                "villager should remain on the lower landing after dismount");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 260)
    public static void ladderNavigationRetargetsPathDuringActiveClimb(GameTestHelper helper) {
        buildTallLadderFixture(helper, 2, 1, 2, 6);
        ServerLevel level = helper.getLevel();
        Villager villager = spawnVillager(helper, new BlockPos(3, 1, 2));
        BlockPos bottomDismount = helper.absolutePos(new BlockPos(3, 1, 2));
        BlockPos topDismount = helper.absolutePos(new BlockPos(3, 7, 2));

        for (int tick = 0; tick < 90 && villager.getY() < bottomDismount.getY() + 3.0D; tick++) {
            helper.assertTrue(
                    VillagerTaskNavigationUtil.moveTowardNearbyLadderThenClimb(level, villager, topDismount, 0.55D),
                    "villager should start the upward ladder climb");
            VillagerTaskNavigationUtil.tickPathLadders(level, villager);
            level.tickNonPassenger(villager);
        }
        helper.assertTrue(villager.getY() >= bottomDismount.getY() + 3.0D
                        && villager.getY() < topDismount.getY() - 0.5D,
                "fixture should place the villager mid-climb before retargeting");

        double retargetY = villager.getY();
        helper.assertTrue(
                VillagerTaskNavigationUtil.moveTowardNearbyLadderThenClimb(level, villager, bottomDismount, 0.55D),
                "intentional mid-climb retarget should be accepted");
        for (int tick = 0; tick < 10; tick++) {
            VillagerTaskNavigationUtil.moveTowardNearbyLadderThenClimb(level, villager, bottomDismount, 0.55D);
            VillagerTaskNavigationUtil.tickPathLadders(level, villager);
            level.tickNonPassenger(villager);
        }
        helper.assertTrue(villager.getY() <= retargetY + 0.2D,
                "retargeted ladder navigation should not continue the stale upward climb");

        runLadderNavigationUntil(helper, level, villager, bottomDismount, 120,
                "villager should recover and descend to the newly assigned lower target");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 420)
    public static void miningWorkerDepositsAndReturnsToLowerExcavationByLadder(GameTestHelper helper) {
        buildFloor(helper, 0, 8, 0, 6, 3);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerDepositReturnMine");
        Villager villager = spawnVillager(helper, new BlockPos(3, 2, 3));
        BlockPos firstTargetRel = new BlockPos(3, 1, 3);
        BlockPos secondTargetRel = new BlockPos(4, 1, 3);
        BlockPos chestRel = new BlockPos(6, 4, 3);
        BlockPos chest = helper.absolutePos(chestRel);

        for (int x = 2; x <= 4; x++) {
            for (int z = 2; z <= 4; z++) {
                setBlock(helper, new BlockPos(x, 1, z), Blocks.BEDROCK.defaultBlockState());
                setBlock(helper, new BlockPos(x, 2, z), Blocks.AIR.defaultBlockState());
                setBlock(helper, new BlockPos(x, 3, z), Blocks.AIR.defaultBlockState());
                setBlock(helper, new BlockPos(x, 4, z), Blocks.AIR.defaultBlockState());
            }
        }
        setBlock(helper, firstTargetRel, Blocks.STONE.defaultBlockState());
        setBlock(helper, secondTargetRel, Blocks.STONE.defaultBlockState());
        for (int y = 1; y <= 3; y++) {
            setBlock(helper, new BlockPos(2, y, 1), Blocks.STONE.defaultBlockState());
            setBlock(
                    helper,
                    new BlockPos(2, y, 2),
                    Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
        }
        setBlock(helper, chestRel, Blocks.CHEST.defaultBlockState());
        AssignedStorageService.removeAssignedContainer(level, chest);
        AssignedStorageService.assign(
                hirer,
                villager,
                List.of(new AssignedStorageService.StoragePosition(level.dimension(), chest)),
                AssignedStorageService.OUTPUT_PURPOSE);

        CompoundTag state = new CompoundTag();
        state.putString(HiredMiningMode.STATE_TAG, HiredMiningMode.EXCAVATE_AREA.serializedName());
        HiredWorkContext context = context(helper, villager, state, new BlockPos(2, 1, 2), new BlockPos(4, 3, 4), true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.DIAMOND_PICKAXE));
        for (int slot = 18; slot < HiredJobInventory.SLOT_COUNT; slot++) {
            context.inventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        context.inventory().setItem(18, new ItemStack(Items.COBBLESTONE, 63));
        MiningWorker worker = new MiningWorker();

        runWorkerUntil(helper, worker, level, villager, hirer, context, 360, () ->
                level.getBlockState(helper.absolutePos(firstTargetRel)).isAir()
                        && level.getBlockState(helper.absolutePos(secondTargetRel)).isAir()
                        && countItem(container(level, chest), Items.COBBLESTONE) > 0);

        helper.assertTrue(level.getBlockState(helper.absolutePos(firstTargetRel)).isAir(),
                "miner should clear the first lower excavation target before depositing");
        helper.assertTrue(level.getBlockState(helper.absolutePos(secondTargetRel)).isAir(),
                "miner should return from storage and clear the remaining lower excavation target");
        helper.assertTrue(countItem(container(level, chest), Items.COBBLESTONE) > 0,
                "miner should deposit filled output to the assigned surface chest");
        helper.assertTrue(context.isInsideWorkArea(villager.blockPosition()),
                "miner should finish the resumed lower excavation inside the work area");

        AssignedStorageService.removeAllAssignedStorage(level, villager);
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 320)
    public static void miningWorkerOutsideAreaReturnsBeforeTargetScan(GameTestHelper helper) {
        buildFloor(helper, 0, 8, 0, 6, 3);
        buildFloor(helper, 0, 8, 0, 6, 0);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerOutsideReturnMine");
        movePlayer(helper, hirer, new BlockPos(1, 4, 1));
        Villager villager = spawnVillager(helper, new BlockPos(6, 4, 3));
        BlockPos targetRel = new BlockPos(3, 1, 3);

        for (int x = 2; x <= 4; x++) {
            for (int z = 2; z <= 4; z++) {
                setBlock(helper, new BlockPos(x, 1, z), Blocks.BEDROCK.defaultBlockState());
                setBlock(helper, new BlockPos(x, 2, z), Blocks.AIR.defaultBlockState());
                setBlock(helper, new BlockPos(x, 3, z), Blocks.AIR.defaultBlockState());
                setBlock(helper, new BlockPos(x, 4, z), Blocks.AIR.defaultBlockState());
            }
        }
        setBlock(helper, targetRel, Blocks.STONE.defaultBlockState());
        for (int y = 1; y <= 3; y++) {
            setBlock(helper, new BlockPos(2, y, 1), Blocks.STONE.defaultBlockState());
            setBlock(
                    helper,
                    new BlockPos(2, y, 2),
                    Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
        }

        CompoundTag state = new CompoundTag();
        state.putString(HiredMiningMode.STATE_TAG, HiredMiningMode.EXCAVATE_AREA.serializedName());
        HiredWorkContext context = context(helper, villager, state, new BlockPos(2, 1, 2), new BlockPos(4, 3, 4), true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.DIAMOND_PICKAXE));
        HiredWorkerBrain.setFailure(context, "target_unreachable", level.getGameTime() + 100L);
        HiredWorkerBrain.setLastTargetScanResult(context, "no_targets");
        HiredWorkerBrain.setState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION);
        MiningWorker worker = new MiningWorker();

        WorkResult firstTick = worker.tick(level, villager, hirer, context);
        HiredWorkerBrain.Snapshot firstState = HiredWorkerBrain.snapshot(state, level.getGameTime());
        helper.assertTrue(firstTick.progressed(), "outside miner should begin returning instead of idling without targets; status="
                + firstTick.status() + ", task=" + firstState.taskState() + ", failure=" + firstState.failureReason()
                + ", scan=" + firstState.lastTargetScanResult() + ", target=" + firstState.targetPos());
        helper.assertTrue(
                firstState.taskState() == HiredWorkerTaskState.RETURNING_TO_WORK_AREA
                        || firstState.taskState() == HiredWorkerTaskState.MOVING_TO_TARGET,
                "outside miner should return or immediately resume target movement; state=" + firstState.taskState());
        helper.assertFalse(firstState.failureReason().contains("target_unreachable"),
                "stale target failure should be cleared during outside return");
        helper.assertFalse(firstState.lastTargetScanResult().contains("no_targets"),
                "stale no-target scan should be cleared during outside return");
        helper.assertTrue(level.getBlockState(helper.absolutePos(targetRel)).is(Blocks.STONE),
                "return tick should not mine or reject the valid target before navigating back");

        runWorkerUntil(helper, worker, level, villager, hirer, context, 240, () ->
                level.getBlockState(helper.absolutePos(targetRel)).isAir());

        helper.assertTrue(level.getBlockState(helper.absolutePos(targetRel)).isAir(),
                "miner should descend from the surface side and mine the valid lower target");
        helper.assertTrue(context.inventory().hasOutput(stack -> stack.is(Items.COBBLESTONE)),
                "returned excavation should store mined drops as output");
        helper.assertTrue(context.isInsideWorkArea(villager.blockPosition()),
                "miner should finish the recovered excavation inside the work area");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 80000)
    public static void miningWorkerServiceExcavatesFullMixedBoxAndDeposits(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = helper.makeMockServerPlayerInLevel();
        movePlayer(helper, hirer, new BlockPos(12, 6, 6));

        BlockPos workMinRel = new BlockPos(2, 1, 2);
        BlockPos workMaxRel = new BlockPos(10, 5, 10);
        fillMixedExcavationBox(helper, workMinRel, workMaxRel);
        buildStoragePlatform(helper, 11, 15, 4, 8, 5);

        BlockPos chestRel = new BlockPos(12, 6, 6);
        BlockPos paymentRel = new BlockPos(13, 6, 6);
        BlockPos chest = helper.absolutePos(chestRel);
        BlockPos payment = helper.absolutePos(paymentRel);
        setBlock(helper, chestRel, Blocks.CHEST.defaultBlockState());
        setBlock(helper, paymentRel, VillagerRetaliationBlocks.OAK_PAYMENT_BOX.get().defaultBlockState());
        Container supplyAndOutput = container(level, chest);
        supplyAndOutput.setItem(0, new ItemStack(Items.LADDER, 64));
        supplyAndOutput.setItem(1, new ItemStack(Items.TORCH, 64));
        supplyAndOutput.setItem(2, new ItemStack(Items.DIAMOND_PICKAXE));
        supplyAndOutput.setItem(3, new ItemStack(Items.DIAMOND_SHOVEL));
        container(level, payment).setItem(0, new ItemStack(Items.EMERALD, 64));

        Villager villager = spawnVillager(helper, new BlockPos(11, 6, 6));
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.TOOLSMITH));
        pinHiredWorkServicePhase(level, villager);
        HiredVillagerContractService.startHireContract(level, villager, hirer, 1, 64);
        helper.assertTrue(
                HiredVillagerContractService.setActiveRole(level, villager, HiredVillagerRole.MINING),
                "toolsmith villager should accept mining role");
        HiredVillagerWorkService.setWorkArea(
                hirer,
                level,
                villager,
                helper.absolutePos(workMinRel),
                helper.absolutePos(workMaxRel));
        HiredWorkSession session = HiredWorkSession.active(level, villager);
        session.state().putString(HiredMiningMode.STATE_TAG, HiredMiningMode.EXCAVATE_AREA.serializedName());
        session.state().putBoolean("UseAssignedStorageForSupplies", true);
        session.state().putBoolean("AutoDepositOutputs", true);

        AssignedStorageService.removeAssignedContainer(level, chest);
        AssignedStorageService.removeAssignedContainer(level, payment);
        AssignedStorageService.AssignSummary chestAssignment = AssignedStorageService.assign(
                hirer,
                villager,
                List.of(new AssignedStorageService.StoragePosition(level.dimension(), chest)),
                AssignedStorageService.GENERAL_PURPOSE);
        AssignedStorageService.AssignSummary paymentAssignment = AssignedStorageService.assign(
                hirer,
                villager,
                List.of(new AssignedStorageService.StoragePosition(level.dimension(), payment)),
                AssignedStorageService.PAYMENT_PURPOSE);
        helper.assertValueEqual(chestAssignment.assigned(), 1, "mixed mining chest assignment");
        helper.assertValueEqual(paymentAssignment.assigned(), 1, "mixed mining payment assignment");

        runHiredMiningServiceUntil(helper, level, villager, workMinRel, workMaxRel, 76000, () ->
                mixedExcavationBoxCleared(level, helper, workMinRel, workMaxRel)
                        && !HiredJobInventory.getJobInventory(villager).hasOutputItems());

        helper.assertTrue(mixedExcavationBoxCleared(level, helper, workMinRel, workMaxRel),
                "miner should clear every original grass/dirt/stone/iron block from the assigned box");
        helper.assertTrue(countItem(supplyAndOutput, Items.DIRT) >= 150,
                "miner should deposit grass and dirt drops not reused as support backing into the assigned chest, count="
                        + countItem(supplyAndOutput, Items.DIRT));
        helper.assertTrue(countItem(supplyAndOutput, Items.RAW_IRON) >= 40,
                "miner should deposit iron ore drops into the assigned chest, count="
                        + countItem(supplyAndOutput, Items.RAW_IRON));
        helper.assertTrue(countItem(supplyAndOutput, Items.COBBLESTONE) > 0,
                "miner should deposit stone drops into the assigned chest");
        helper.assertFalse(HiredJobInventory.getJobInventory(villager).hasOutputItems(),
                "miner should finish with job output inventory deposited");
        helper.assertTrue(countItem(container(level, payment), Items.EMERALD) >= 64,
                "payment box should remain assigned and stocked with emeralds");

        AssignedStorageService.removeAllAssignedStorage(level, villager);
        HiredVillagerContractService.endHireContract(level, villager, hirer);
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

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 300)
    public static void loggingWorkerKeepsLeafConnectedTreeFamiliesSeparate(GameTestHelper helper) {
        buildFloor(helper, 0, 10, 0, 6, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerLoggingFamilies");
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 3));
        BlockPos oakRootRel = new BlockPos(4, 2, 3);
        BlockPos birchRootRel = new BlockPos(7, 2, 3);
        setBlock(helper, new BlockPos(4, 1, 3), Blocks.DIRT.defaultBlockState());
        setBlock(helper, new BlockPos(7, 1, 3), Blocks.DIRT.defaultBlockState());
        for (int y = 2; y <= 4; y++) {
            setBlock(helper, new BlockPos(4, y, 3), Blocks.OAK_LOG.defaultBlockState());
            setBlock(helper, new BlockPos(7, y, 3), Blocks.BIRCH_LOG.defaultBlockState());
        }

        BlockState oakLeaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, false);
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
            setBlock(helper, rel, oakLeaves);
        }
        BlockState birchLeaves = Blocks.BIRCH_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, false);
        for (BlockPos rel : List.of(
                new BlockPos(7, 5, 3),
                new BlockPos(6, 5, 3),
                new BlockPos(8, 5, 3),
                new BlockPos(7, 5, 2),
                new BlockPos(7, 5, 4),
                new BlockPos(6, 4, 3),
                new BlockPos(8, 4, 3),
                new BlockPos(7, 4, 2),
                new BlockPos(7, 4, 4))) {
            setBlock(helper, rel, birchLeaves);
        }

        CompoundTag state = new CompoundTag();
        HiredWorkContext context = context(helper, villager, state, new BlockPos(1, 2, 1), new BlockPos(9, 6, 5), true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_AXE));
        LoggingWorker worker = new LoggingWorker();

        runWorkerUntil(helper, worker, level, villager, hirer, context, 160, () ->
                !level.getBlockState(helper.absolutePos(oakRootRel)).is(BlockTags.OAK_LOGS));

        helper.assertFalse(level.getBlockState(helper.absolutePos(oakRootRel)).is(BlockTags.OAK_LOGS), "logger should harvest the selected oak tree");
        helper.assertTrue(level.getBlockState(helper.absolutePos(birchRootRel)).is(BlockTags.BIRCH_LOGS), "leaf-connected birch should remain for a separate harvest");
        helper.assertTrue(context.inventory().hasOutput(stack -> stack.is(Items.OAK_LOG)), "oak drops should be stored as output");

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
            VillagerTaskNavigationUtil.tickPathLadders(level, villager);
            level.tickNonPassenger(villager);
        }
        if (!done.getAsBoolean()) {
            HiredWorkerBrain.Snapshot snapshot = HiredWorkerBrain.snapshot(context.state(), level.getGameTime());
            String debug = worker instanceof LoggingWorker ? " " + LoggingWorker.debugSummary(context) : "";
            BlockPos navTarget = villager.getNavigation().getTargetPos();
            BlockPos activeTarget = context.state().contains("ActiveWorkBlockPos")
                    ? BlockPos.of(context.state().getLong("ActiveWorkBlockPos"))
                    : null;
            BlockPos activeApproach = context.state().contains("ActiveWorkApproachPos")
                    ? BlockPos.of(context.state().getLong("ActiveWorkApproachPos"))
                    : null;
            throw new GameTestAssertException("Worker did not reach expected state in " + maxTicks
                    + " direct ticks; task=" + snapshot.taskState()
                    + ", failure=" + snapshot.failureReason()
                    + ", scan=" + snapshot.lastTargetScanResult()
                    + ", pos=" + villager.blockPosition()
                    + ", precise=(" + String.format(java.util.Locale.ROOT, "%.2f", villager.getX())
                    + "," + String.format(java.util.Locale.ROOT, "%.2f", villager.getY())
                    + "," + String.format(java.util.Locale.ROOT, "%.2f", villager.getZ()) + ")"
                    + ", nav=" + navTarget
                    + ", active=" + activeTarget
                    + ", approach=" + activeApproach
                    + debug);
        }
    }

    private static void runHiredMiningServiceUntil(
            GameTestHelper helper,
            ServerLevel level,
            Villager villager,
            BlockPos workMinRel,
            BlockPos workMaxRel,
            int maxTicks,
            java.util.function.BooleanSupplier done) {
        int lastRemainingBlocks = remainingMixedExcavationBlocks(level, helper, workMinRel, workMaxRel);
        int ticksSinceBlockProgress = 0;
        for (int tick = 0; tick < maxTicks && !done.getAsBoolean(); tick++) {
            HiredVillagerContractService.onVillagerTickPost(villager);
            HiredVillagerWorkService.onVillagerTickPost(villager);
            VillagerTaskNavigationUtil.tickVillagerWaterSafety(level, villager);
            VillagerTaskNavigationUtil.tickPathDoors(level, villager);
            VillagerTaskNavigationUtil.tickPathLadders(level, villager);
            level.tickNonPassenger(villager);

            int remainingBlocks = remainingMixedExcavationBlocks(level, helper, workMinRel, workMaxRel);
            if (remainingBlocks < lastRemainingBlocks) {
                lastRemainingBlocks = remainingBlocks;
                ticksSinceBlockProgress = 0;
            } else if (remainingBlocks > 0) {
                ticksSinceBlockProgress++;
            }
            if (ticksSinceBlockProgress > 3000) {
                throw mixedMiningServiceFailure(
                        helper,
                        level,
                        villager,
                        workMinRel,
                        workMaxRel,
                        "Hired mining service stalled for " + ticksSinceBlockProgress
                                + " ticks without clearing another original block");
            }
        }
        if (!done.getAsBoolean()) {
            throw mixedMiningServiceFailure(
                    helper,
                    level,
                    villager,
                    workMinRel,
                    workMaxRel,
                    "Hired mining service did not finish mixed excavation in " + maxTicks + " ticks");
        }
    }

    private static GameTestAssertException mixedMiningServiceFailure(
            GameTestHelper helper,
            ServerLevel level,
            Villager villager,
            BlockPos workMinRel,
            BlockPos workMaxRel,
            String reason) {
        HiredWorkSession session = HiredWorkSession.active(level, villager);
        HiredWorkerBrain.Snapshot snapshot = HiredWorkerBrain.snapshot(session.state(), level.getGameTime());
        BlockPos navTarget = villager.getNavigation().getTargetPos();
        BlockPos entryTarget = MiningWorker.excavationEntryTarget(level, session.context());
        BlockPos returnTarget = MiningWorker.excavationReturnTarget(level, villager, session.context());
        return new GameTestAssertException(reason
                + "; task=" + snapshot.taskState()
                + ", failure=" + snapshot.failureReason()
                + ", scan=" + snapshot.lastTargetScanResult()
                + ", status=" + session.state().getString("Status")
                + ", layer=" + MiningBlockRules.currentExcavationLayer(level, session.context())
                + ", pos=" + villager.blockPosition()
                + ", precise=(" + String.format(java.util.Locale.ROOT, "%.2f", villager.getX())
                + "," + String.format(java.util.Locale.ROOT, "%.2f", villager.getY())
                + "," + String.format(java.util.Locale.ROOT, "%.2f", villager.getZ()) + ")"
                + ", nav=" + navTarget
                + ", storage=" + snapshot.storageTargetPos()
                + ", target=" + snapshot.targetPos()
                + ", entry=" + entryTarget
                + ", return=" + returnTarget
                + ", remainingBlocks=" + remainingMixedExcavationBlocks(level, helper, workMinRel, workMaxRel)
                + ", remainingOutput=" + session.inventory().hasOutputItems()
                + ", ladders=" + ladderSummary(level, helper, workMinRel, workMaxRel));
    }

    private static void pinHiredWorkServicePhase(ServerLevel level, Villager villager) {
        int interval = Math.max(10, VillagerRetaliationConfig.HIRED_WORK_TICK_INTERVAL.get());
        long least = Math.floorMod(-level.getGameTime(), interval);
        villager.setUUID(new UUID(0x564d696e6572544cL, least));
    }

    private static void fillMixedExcavationBox(GameTestHelper helper, BlockPos min, BlockPos max) {
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    BlockState state;
                    if (y == max.getY()) {
                        state = Blocks.GRASS_BLOCK.defaultBlockState();
                    } else if (y == max.getY() - 1) {
                        state = Blocks.DIRT.defaultBlockState();
                    } else if (Math.floorMod(x + y + z, 5) == 0) {
                        state = Blocks.IRON_ORE.defaultBlockState();
                    } else {
                        state = Blocks.STONE.defaultBlockState();
                    }
                    setBlock(helper, new BlockPos(x, y, z), state);
                }
            }
        }
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                setBlock(helper, new BlockPos(x, min.getY() - 1, z), Blocks.BEDROCK.defaultBlockState());
                setBlock(helper, new BlockPos(x, max.getY() + 1, z), Blocks.AIR.defaultBlockState());
                setBlock(helper, new BlockPos(x, max.getY() + 2, z), Blocks.AIR.defaultBlockState());
            }
        }
    }

    private static void buildStoragePlatform(GameTestHelper helper, int minX, int maxX, int minZ, int maxZ, int floorY) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                setBlock(helper, new BlockPos(x, floorY, z), Blocks.STONE.defaultBlockState());
                setBlock(helper, new BlockPos(x, floorY + 1, z), Blocks.AIR.defaultBlockState());
                setBlock(helper, new BlockPos(x, floorY + 2, z), Blocks.AIR.defaultBlockState());
            }
        }
    }

    private static void buildTallLadderFixture(GameTestHelper helper, int x, int minY, int z, int maxY) {
        setBlock(helper, new BlockPos(x + 1, minY - 1, z), Blocks.STONE.defaultBlockState());
        setBlock(helper, new BlockPos(x + 1, minY, z), Blocks.AIR.defaultBlockState());
        setBlock(helper, new BlockPos(x + 1, minY + 1, z), Blocks.AIR.defaultBlockState());
        setBlock(helper, new BlockPos(x + 1, maxY, z), Blocks.STONE.defaultBlockState());
        setBlock(helper, new BlockPos(x + 1, maxY + 1, z), Blocks.AIR.defaultBlockState());
        setBlock(helper, new BlockPos(x + 1, maxY + 2, z), Blocks.AIR.defaultBlockState());
        for (int y = minY; y <= maxY; y++) {
            setBlock(helper, new BlockPos(x, y, z - 1), Blocks.STONE.defaultBlockState());
            setBlock(
                    helper,
                    new BlockPos(x, y, z),
                    Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
            if (y > minY && y < maxY) {
                setBlock(helper, new BlockPos(x + 1, y, z), Blocks.AIR.defaultBlockState());
                setBlock(helper, new BlockPos(x + 1, y + 1, z), Blocks.AIR.defaultBlockState());
            }
        }
        setBlock(helper, new BlockPos(x + 1, maxY, z), Blocks.STONE.defaultBlockState());
        setBlock(helper, new BlockPos(x + 1, maxY + 1, z), Blocks.AIR.defaultBlockState());
        setBlock(helper, new BlockPos(x + 1, maxY + 2, z), Blocks.AIR.defaultBlockState());
    }

    private static void moveVillagerToBlock(Villager villager, BlockPos pos) {
        VillagerTaskNavigationUtil.clearRuntimeState(villager);
        VillagerTaskNavigationUtil.stopHiredNavigation(villager);
        villager.setNoGravity(false);
        villager.setDeltaMovement(0.0D, 0.0D, 0.0D);
        villager.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
    }

    private static void runLadderNavigationUntil(
            GameTestHelper helper,
            ServerLevel level,
            Villager villager,
            BlockPos target,
            int maxTicks,
            String message) {
        for (int tick = 0; tick < maxTicks && !villager.blockPosition().equals(target); tick++) {
            VillagerTaskNavigationUtil.moveTowardNearbyLadderThenClimb(level, villager, target, 0.55D);
            VillagerTaskNavigationUtil.tickPathLadders(level, villager);
            level.tickNonPassenger(villager);
        }
        helper.assertValueEqual(villager.blockPosition(), target, message);
    }

    private static boolean mixedExcavationBoxCleared(ServerLevel level, GameTestHelper helper, BlockPos min, BlockPos max) {
        return remainingMixedExcavationBlocks(level, helper, min, max) == 0;
    }

    private static int remainingMixedExcavationBlocks(ServerLevel level, GameTestHelper helper, BlockPos min, BlockPos max) {
        int remaining = 0;
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    if (isOriginalMixedExcavationMaterial(level.getBlockState(helper.absolutePos(new BlockPos(x, y, z))))) {
                        remaining++;
                    }
                }
            }
        }
        return remaining;
    }

    private static String ladderSummary(ServerLevel level, GameTestHelper helper, BlockPos min, BlockPos max) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        StringBuilder builder = new StringBuilder("[");
        int count = 0;
        for (int x = min.getX() - 1; x <= max.getX() + 1; x++) {
            for (int y = min.getY() - 1; y <= max.getY() + 1; y++) {
                for (int z = min.getZ() - 1; z <= max.getZ() + 1; z++) {
                    BlockPos absolute = helper.absolutePos(new BlockPos(x, y, z));
                    if (!level.getBlockState(absolute).is(Blocks.LADDER)) {
                        continue;
                    }
                    if (count++ > 0) {
                        builder.append(",");
                    }
                    builder.append("(")
                            .append(absolute.getX() - origin.getX()).append(",")
                            .append(absolute.getY() - origin.getY()).append(",")
                            .append(absolute.getZ() - origin.getZ()).append(")");
                }
            }
        }
        builder.append("]");
        return builder.toString();
    }

    private static boolean isOriginalMixedExcavationMaterial(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.DIRT)
                || state.is(Blocks.STONE)
                || state.is(Blocks.IRON_ORE);
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

    private static void seedBuilderTask(CompoundTag state, int paidCurrency, int placedIndex) {
        CompoundTag task = new CompoundTag();
        task.putString("JobId", UUID.randomUUID().toString());
        task.putString("StructureId", "villagerretaliation:test_structure");
        task.putString("StructureLabel", "Test Structure");
        task.putLong("Origin", BlockPos.ZERO.asLong());
        task.putString("Rotation", "NONE");
        task.putString("Phase", "building");
        task.putInt("PlacedIndex", Math.max(0, placedIndex));
        task.putInt("TotalBlocks", 8);
        task.putInt("PaidCurrency", Math.max(0, paidCurrency));
        task.putLong("StartedGameTime", 1L);
        state.put(BuilderTaskState.TASK_TAG, task);
    }

    private static CompoundTag persistentWorkState(Villager villager) {
        CompoundTag data = villager.getPersistentData();
        if (!data.contains(WORK_STATE_TAG)) {
            data.put(WORK_STATE_TAG, new CompoundTag());
        }
        CompoundTag state = data.getCompound(WORK_STATE_TAG);
        HiredWorkerBrain.initialize(state);
        return state;
    }

    private static int countCurrency(ServerPlayer player) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (VillagerCurrencyResources.isCurrency(player.serverLevel().getServer(), stack)) {
                count += stack.getCount();
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (VillagerCurrencyResources.isCurrency(player.serverLevel().getServer(), stack)) {
                count += stack.getCount();
            }
        }
        return count;
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
