package com.jvn.villagerretaliation.villager;

import com.jvn.villagerretaliation.combat.VillagerRetaliationRetaliationUtil;
import com.jvn.villagerretaliation.interaction.HiredMiningMode;
import com.jvn.villagerretaliation.interaction.HiredVillagerContractService;
import com.jvn.villagerretaliation.interaction.HiredVillagerWorkService;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerBrain;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerTaskState;
import com.jvn.villagerretaliation.util.TickThrottle;
import com.jvn.villagerretaliation.interaction.work.HiredWorkContext;
import com.jvn.villagerretaliation.interaction.work.VillagerWorkerGameTests;
import com.jvn.villagerretaliation.interaction.work.mining.MiningWorker;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.PathType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("villagerretaliation_navigation")
@PrefixGameTestTemplate(false)
public final class VillagerNavigationGameTests {
    private VillagerNavigationGameTests() {
    }

    @GameTestGenerator
    public static List<TestFunction> establishedNavigationScenarios() {
        return List.of(
                scenario("traffic", VillagerBehaviorSuppressionGameTests::villagerTrafficUsesStableRightOfWayAndSafePassing),
                scenario("adjacent_target", VillagerWorkerGameTests::pathingFindsAdjacentReachableTargetAndRejectsBlockedOrOutOfAreaTargets),
                scenario("obstacle", VillagerWorkerGameTests::pathingCanRouteAroundSimpleObstaclesWithoutLeavingTheWorkArea),
                scenario("failure_memory", VillagerWorkerGameTests::pathMemoryAvoidsRepeatedFailuresReservationsAndNavigationStalls),
                scenario("ladder_heights", VillagerWorkerGameTests::ladderNavigationMovesFromEveryColumnHeightToTopAndBottom),
                scenario("ladder_landing", VillagerWorkerGameTests::ladderNavigationBottomDismountClearsClimbStateAndStaysLanded),
                scenario("lower_landing", VillagerWorkerGameTests::ladderNavigationBottomRungSnapsToLowerLandingWithoutFalling),
                scenario("ladder_retarget", VillagerWorkerGameTests::ladderNavigationRetargetsPathDuringActiveClimb),
                scenario("ladder_entry", VillagerWorkerGameTests::ladderNavigationChoosesReachableRungWhenBottomEntryIsBlocked),
                scenario("blocked_corner", VillagerWorkerGameTests::ladderNavigationRejectsBlockedDirectEntryCorner));
    }

    private static TestFunction scenario(String name, Consumer<GameTestHelper> test) {
        return new TestFunction("navigation_regressions", "villagernavigationgametests.regression_" + name,
                "villagerretaliation_navigation:empty", 1400, 0L, true, test);
    }

    @GameTest(template = "empty")
    public static void suppressedBrainStillExpiresMemoriesAndSensesHostiles(GameTestHelper helper) {
        CountingVillager villager = new CountingVillager(helper.getLevel());
        BlockPos origin = helper.absolutePos(new BlockPos(2, 3, 2));
        villager.setPos(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 0.5D);
        Zombie hostile = new Zombie(EntityType.ZOMBIE, helper.getLevel());
        hostile.setPos(villager.getX() + 2, villager.getY(), villager.getZ());
        hostile.setNoAi(true);
        helper.getLevel().addFreshEntity(hostile);
        try {
            villager.setTarget(hostile);
            helper.assertTrue(VillagerRetaliationVillagerBrainUtil.shouldSuppressVanillaBrainTickForCombat(villager),
                    "combat owns the fixture's behavior");
            villager.getBrain().setMemoryWithExpiry(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(origin), 1);
            WalkTarget movement = new WalkTarget(origin.east(5), 0.5F, 0);
            villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, movement);
            for (int tick = 0; tick < 60; tick++) {
                villager.runBrainAiStep();
            }
            helper.assertFalse(villager.getBrain().hasMemoryValue(MemoryModuleType.LOOK_TARGET),
                    "temporary memories expire while behavior is suppressed");
            helper.assertTrue(villager.getBrain().getMemory(MemoryModuleType.NEAREST_HOSTILE).orElse(null) == hostile,
                    "sensors discover a new hostile while behavior is suppressed");
            helper.assertTrue(villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null) == movement,
                    "vanilla movement behaviors do not replace the controlled walk target");
        } finally {
            hostile.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void hiredWorkerSensesNewHostileAndReleasesBrainSuppression(GameTestHelper helper) {
        var level = helper.getLevel();
        long previousDayTime = level.getDayTime();
        level.setDayTime(1000L);
        var hirer = helper.makeMockServerPlayerInLevel();
        CountingVillager villager = new CountingVillager(level);
        BlockPos origin = helper.absolutePos(new BlockPos(2, 3, 2));
        villager.setPos(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 0.5D);
        Zombie hostile = new Zombie(EntityType.ZOMBIE, level);
        hostile.setPos(villager.getX() + 2, villager.getY(), villager.getZ());
        hostile.setNoAi(true);
        try {
            helper.assertTrue(HiredVillagerContractService.startHireContract(level, villager, hirer, 1, 8),
                    "the fixture accepts its hire contract");
            HiredVillagerWorkService.initializeWorkArea(level, villager);
            var state = HiredWorkStateStore.state(villager);
            state.putBoolean("Enabled", true);
            HiredWorkerBrain.setState(state, HiredWorkerTaskState.SELECTING_TARGET, null);
            helper.assertTrue(VillagerBehaviorSuppressionPolicy.shouldSuppressVanillaBrainTick(level, villager),
                    "hired work initially owns the brain's behavior");
            level.addFreshEntity(hostile);
            for (int tick = 0; tick < 60 && !villager.getBrain().hasMemoryValue(MemoryModuleType.NEAREST_HOSTILE); tick++) {
                villager.runBrainAiStep();
            }
            helper.assertTrue(villager.getBrain().getMemory(MemoryModuleType.NEAREST_HOSTILE).orElse(null) == hostile,
                    "an unarmed worker notices the approaching zombie before being hit");
            helper.assertFalse(VillagerBehaviorSuppressionPolicy.shouldSuppressVanillaBrainTick(level, villager),
                    "new threat memory releases the brain to handle danger");
        } finally {
            hostile.discard();
            HiredVillagerContractService.endHireContract(level, villager, hirer);
            level.setDayTime(previousDayTime);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void unreachableGearYieldsCombatAndDoesNotHideOtherItems(GameTestHelper helper) {
        CountingVillager villager = new CountingVillager(helper.getLevel());
        BlockPos origin = helper.absolutePos(new BlockPos(2, 3, 2));
        villager.setPos(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 0.5D);
        ItemEntity weapon = new ItemEntity(helper.getLevel(), villager.getX() + 3, villager.getY(), villager.getZ(), new ItemStack(Items.IRON_SWORD));
        ItemEntity armor = new ItemEntity(helper.getLevel(), villager.getX() + 4, villager.getY(), villager.getZ(), new ItemStack(Items.IRON_HELMET));
        ItemEntity alternative = new ItemEntity(helper.getLevel(), villager.getX() + 5, villager.getY(), villager.getZ(), new ItemStack(Items.STONE_SWORD));
        for (ItemEntity item : List.of(weapon, armor, alternative)) {
            item.setNoGravity(true);
            item.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            item.setNeverPickUp();
            helper.getLevel().addFreshEntity(item);
        }
        helper.assertFalse(VillagerRetaliationRetaliationUtil.tryAcquireGroundWeapon(villager, weapon, 0.5D, () -> {}),
                "a failed weapon path immediately yields combat");
        helper.assertFalse(VillagerRetaliationRetaliationUtil.tryAcquireGroundArmor(villager, armor, 0.5D),
                "a failed armor path immediately yields combat");
        for (int call = 0; call < 10; call++) {
            VillagerRetaliationRetaliationUtil.tryAcquireGroundWeapon(villager, weapon, 0.5D, () -> {});
            VillagerRetaliationRetaliationUtil.tryAcquireGroundArmor(villager, armor, 0.5D);
        }
        helper.assertValueEqual(villager.navigation().entitySearches, 2, "failed items are not repeatedly pathfound");
        helper.assertTrue(VillagerRetaliationVillagerWeapons.findNearestWeapon(villager).orElse(null) == alternative,
                "another weapon remains eligible when the preferred weapon is inaccessible");
        helper.assertTrue(VillagerRetaliationVillagerArmor.findNearestUpgrade(villager).isEmpty(),
                "the inaccessible armor is excluded from selection");
        helper.runAfterDelay(201, () -> {
            try {
                VillagerRetaliationRetaliationUtil.tryAcquireGroundWeapon(villager, weapon, 0.5D, () -> {});
                helper.assertValueEqual(villager.navigation().entitySearches, 3, "failed items become retryable after the cooldown");
                helper.succeed();
            } finally {
                weapon.discard();
                armor.discard();
                alternative.discard();
                VillagerRetaliationRetaliationUtil.clearPathingState(villager);
            }
        });
    }

    @GameTest(template = "empty")
    public static void failedCombatPathsWaitBeforeRetryingAndRetargetImmediately(GameTestHelper helper) {
        CountingVillager villager = new CountingVillager(helper.getLevel());
        Villager target = new Villager(EntityType.VILLAGER, helper.getLevel());
        BlockPos origin = helper.absolutePos(new BlockPos(2, 2, 2));
        villager.setPos(origin.getX(), origin.getY(), origin.getZ());
        target.setPos(origin.getX() + 5, origin.getY(), origin.getZ());
        try {
            VillagerRetaliationRetaliationUtil.moveTowardReachableRetaliationTarget(villager, target, 0.5D);
            for (int tick = 0; tick < 18; tick++) {
                VillagerRetaliationRetaliationUtil.moveTowardReachableRetaliationTarget(villager, target, 0.5D);
            }
            helper.assertValueEqual(villager.navigation().entitySearches, 1, "failed paths respect the minimum retry delay");
            for (int tick = 0; tick < 8; tick++) {
                VillagerRetaliationRetaliationUtil.moveTowardReachableRetaliationTarget(villager, target, 0.5D);
            }
            helper.assertValueEqual(villager.navigation().entitySearches, 2, "failed paths retry after the delay");
            Villager replacement = new Villager(EntityType.VILLAGER, helper.getLevel());
            replacement.setPos(target.getX(), target.getY(), target.getZ());
            VillagerRetaliationRetaliationUtil.moveTowardReachableRetaliationTarget(villager, replacement, 0.5D);
            helper.assertValueEqual(villager.navigation().entitySearches, 3, "a different enemy bypasses the old retry delay");
        } finally {
            VillagerRetaliationRetaliationUtil.clearPathingState(villager);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void ladderDismountAvoidsMagmaAndLava(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos rung = helper.absolutePos(new BlockPos(3, 3, 3));
        for (BlockPos pos : BlockPos.betweenClosed(rung.offset(-2, -2, -2), rung.offset(2, 3, 2))) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        }
        level.setBlock(rung.north(), Blocks.STONE.defaultBlockState(), 2);
        level.setBlock(rung, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH), 2);
        BlockPos safe = rung.west().below();
        BlockPos dangerous = rung.east().below();
        level.setBlock(safe.below(), Blocks.STONE.defaultBlockState(), 2);
        level.setBlock(dangerous.below(), Blocks.MAGMA_BLOCK.defaultBlockState(), 2);
        Villager villager = new Villager(EntityType.VILLAGER, level);
        try {
            for (boolean lava : new boolean[] { false, true }) {
                VillagerTaskNavigationUtil.clearRuntimeState(villager);
                villager.setPos(rung.getX() + 0.5D, rung.getY(), rung.getZ() + 0.5D);
                if (lava) {
                    level.setBlock(dangerous.below(), Blocks.STONE.defaultBlockState(), 2);
                    level.setBlock(dangerous, Blocks.LAVA.defaultBlockState(), 2);
                }
                helper.assertTrue(VillagerTaskNavigationUtil.moveOnLadderToward(level, villager, dangerous, 0.5D),
                        "the ladder still has a safe dismount");
                helper.assertValueEqual(villager.blockPosition(), safe, "execution chooses the safe landing despite the hazardous target");
            }
        } finally {
            VillagerTaskNavigationUtil.clearRuntimeState(villager);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void waterEscapeRejectsLavaAndDamagingFloors(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 3, 2));
        level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 2);
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 2);
        level.setBlock(pos, Blocks.WATER.defaultBlockState(), 2);
        helper.assertTrue(VillagerWaterTraversal.isWaterEscapeTarget(level, pos), "open water remains usable");
        level.setBlock(pos, Blocks.LAVA.defaultBlockState(), 2);
        helper.assertFalse(VillagerWaterTraversal.isWaterEscapeTarget(level, pos), "lava is not swimming water");
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        level.setBlock(pos.below(), Blocks.MAGMA_BLOCK.defaultBlockState(), 2);
        helper.assertFalse(VillagerWaterTraversal.isWaterEscapeTarget(level, pos), "a dry escape cannot stand on magma");
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 2);
        helper.assertTrue(VillagerWaterTraversal.isWaterEscapeTarget(level, pos), "a safe dry shore remains usable");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void miningConfigurationResetPreservesOriginalNavigationPolicy(GameTestHelper helper) {
        var level = helper.getLevel();
        Villager villager = new Villager(EntityType.VILLAGER, level);
        BlockPos origin = helper.absolutePos(new BlockPos(2, 2, 2));
        villager.setPos(origin.getX(), origin.getY(), origin.getZ());
        HiredWorkContext context = new HiredWorkContext(HiredJobInventory.getJobInventory(villager),
                new CompoundTag(), origin, origin, origin, 4, 4, true, 100, false, false);
        try {
            villager.setPathfindingMalus(PathType.WATER, 3.0F);
            villager.setPathfindingMalus(PathType.FENCE, 9.0F);
            ((GroundPathNavigation) villager.getNavigation()).setCanFloat(false);
            for (int change = 0; change < 3; change++) {
                VillagerTaskNavigationUtil.enableHiredWaterTraversal(villager);
                switch (change) {
                    case 0 -> MiningWorker.resetForModeChange(level, villager, context, HiredMiningMode.EXCAVATE_AREA);
                    case 1 -> MiningWorker.resetForOptionChange(level, villager, context, HiredMiningMode.EXCAVATE_AREA);
                    default -> MiningWorker.resetForWorkAreaChange(level, villager, context, HiredMiningMode.EXCAVATE_AREA);
                }
                VillagerTaskNavigationUtil.enableHiredWaterTraversal(villager);
                VillagerTaskNavigationUtil.restoreHiredWaterTraversal(villager);
                helper.assertValueEqual(villager.getPathfindingMalus(PathType.WATER), 3.0F, "reset preserves original water cost");
                helper.assertValueEqual(villager.getPathfindingMalus(PathType.FENCE), 9.0F, "reset preserves original fence cost");
                helper.assertFalse(((GroundPathNavigation) villager.getNavigation()).canFloat(), "reset preserves original flotation");
            }
        } finally {
            VillagerTaskNavigationUtil.clearRuntimeState(villager);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void ladderSearchResumesBeyondItsPerTickPathBudget(GameTestHelper helper) {
        CountingVillager villager = ladderSearchFixture(helper);
        BlockPos target = helper.absolutePos(new BlockPos(4, 7, 7));
        villager.navigation().reachableFromSearch = 5;
        helper.assertTrue(VillagerLadderRoutePlanner.findBest(helper.getLevel(), villager, target, 6, 6) == null,
                "the first batch cannot reach an entry");
        helper.assertValueEqual(villager.navigation().blockSearches, 4, "one batch performs at most four path searches");
        helper.assertTrue(VillagerLadderRoutePlanner.isSearching(villager), "untried entries stay pending");
        for (int call = 0; call < 5; call++) {
            VillagerLadderRoutePlanner.findBest(helper.getLevel(), villager, target, 6, 6);
        }
        helper.assertValueEqual(villager.navigation().blockSearches, 4, "repeated calls cannot replenish the tick budget");
        helper.runAfterDelay(1, () -> finishLadderSearch(helper, villager, target, 0));
    }

    private static void finishLadderSearch(GameTestHelper helper, CountingVillager villager, BlockPos target, int retries) {
        int before = villager.navigation().blockSearches;
        var route = VillagerLadderRoutePlanner.findBest(helper.getLevel(), villager, target, 6, 6);
        helper.assertTrue(villager.navigation().blockSearches - before <= 4, "resuming preserves the per-tick budget");
        if (route == null) {
            helper.assertTrue(retries < 10 && VillagerLadderRoutePlanner.isSearching(villager), "pending entries eventually complete");
            helper.runAfterDelay(1, () -> finishLadderSearch(helper, villager, target, retries + 1));
            return;
        }
        helper.assertTrue(villager.navigation().blockSearches >= 5, "a reachable entry after the first batch is not dropped");
        helper.assertFalse(VillagerLadderRoutePlanner.isSearching(villager), "the completed search releases its candidates");
        VillagerTaskNavigationUtil.clearRuntimeState(villager);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void ladderRetargetingCannotBypassTheBudgetAndCleanupCancelsPendingSearch(GameTestHelper helper) {
        CountingVillager villager = ladderSearchFixture(helper);
        BlockPos target = helper.absolutePos(new BlockPos(4, 7, 7));
        try {
            VillagerLadderRoutePlanner.findBest(helper.getLevel(), villager, target, 6, 6);
            VillagerLadderRoutePlanner.findBest(helper.getLevel(), villager, target.east(), 6, 6);
            helper.assertValueEqual(villager.navigation().blockSearches, 4, "changing targets keeps the consumed budget");
            helper.assertTrue(VillagerLadderRoutePlanner.isSearching(villager), "the replacement target is pending");
            VillagerLadderRoutePlanner.findBest(helper.getLevel(), villager, villager.blockPosition().east(), 6, 6);
            helper.assertFalse(VillagerLadderRoutePlanner.isSearching(villager), "a level target cancels the old vertical search");
            VillagerLadderRoutePlanner.findBest(helper.getLevel(), villager, target, 6, 6);
            helper.assertTrue(VillagerLadderRoutePlanner.isSearching(villager), "a new vertical search remains budgeted");
            VillagerTaskNavigationUtil.clearRuntimeState(villager);
            helper.assertFalse(VillagerLadderRoutePlanner.isSearching(villager), "cleanup releases pending searches");
        } finally {
            VillagerTaskNavigationUtil.clearRuntimeState(villager);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void ladderSearchKeepsPendingCandidatesAfterSmallMovement(GameTestHelper helper) {
        CountingVillager villager = ladderSearchFixture(helper);
        BlockPos target = helper.absolutePos(new BlockPos(4, 7, 7));
        villager.navigation().reachableFromSearch = 5;
        VillagerLadderRoutePlanner.findBest(helper.getLevel(), villager, target, 6, 6);
        var tried = new HashSet<>(villager.navigation().searchedBlocks);
        helper.assertValueEqual(villager.navigation().blockSearches, 4, "the first batch exhausts its search budget");
        helper.runAfterDelay(1, () -> {
            villager.setPos(villager.getX() + 0.05D, villager.getY(), villager.getZ());
            var route = VillagerLadderRoutePlanner.findBest(helper.getLevel(), villager, target, 6, 6);
            helper.assertTrue(villager.navigation().blockSearches > 4, "the next tick advances the search");
            helper.assertFalse(tried.contains(villager.navigation().searchedBlocks.get(4)),
                    "a small push does not restart previously rejected approaches");
            if (route != null) {
                VillagerTaskNavigationUtil.clearRuntimeState(villager);
                helper.succeed();
            } else {
                helper.runAfterDelay(1, () -> finishLadderSearch(helper, villager, target, 0));
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 140)
    public static void blockedLadderClimbsTimeOutAndCanRetry(GameTestHelper helper) {
        CountingVillager villager = ladderSearchFixture(helper);
        BlockPos rung = helper.absolutePos(new BlockPos(4, 3, 6));
        BlockPos target = helper.absolutePos(new BlockPos(4, 7, 7));
        villager.setPos(rung.getX() + 0.5D, rung.getY(), rung.getZ() + 0.5D);
        villager.blockMovement = true;
        long started = helper.getLevel().getGameTime();
        checkBlockedClimb(helper, villager, target, started);
    }

    private static void checkBlockedClimb(GameTestHelper helper, CountingVillager villager, BlockPos target, long started) {
        boolean moving = VillagerTaskNavigationUtil.moveOnLadderToward(helper.getLevel(), villager, target, 0.5D);
        VillagerTaskNavigationUtil.tickPathLadders(helper.getLevel(), villager);
        if (helper.getLevel().getGameTime() - started < 80) {
            helper.assertTrue(moving, "a climb may attempt movement before the stall timeout");
            helper.runAfterDelay(1, () -> checkBlockedClimb(helper, villager, target, started));
            return;
        }
        helper.assertFalse(moving, "repeated climb requests cannot renew a stalled climb forever");
        helper.assertFalse(villager.isNoGravity(), "a timed-out climb restores gravity");
        helper.assertFalse(VillagerTaskNavigationUtil.moveOnLadderToward(helper.getLevel(), villager, target, 0.5D),
                "the failed route cannot restart in the same tick");
        helper.runAfterDelay(21, () -> {
            villager.blockMovement = false;
            double before = villager.getY();
            helper.assertTrue(VillagerTaskNavigationUtil.moveOnLadderToward(helper.getLevel(), villager, target, 0.5D),
                    "a cleared obstruction can be retried after the cooldown");
            helper.assertTrue(villager.getY() > before, "the retried climb makes vertical progress");
            VillagerTaskNavigationUtil.clearRuntimeState(villager);
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void ladderLandingYieldsToNewMovementOwners(GameTestHelper helper) {
        CountingVillager villager = ladderSearchFixture(helper);
        BlockPos rung = helper.absolutePos(new BlockPos(4, 6, 6));
        BlockPos target = helper.absolutePos(new BlockPos(4, 7, 7));
        try {
            for (int owner = 0; owner < 3; owner++) {
                VillagerTaskNavigationUtil.clearRuntimeState(villager);
                VillagerTaskNavigationUtil.stopHiredNavigation(villager);
                villager.setTarget(null);
                villager.setPos(rung.getX() + 0.5D, rung.getY(), rung.getZ() + 0.5D);
                helper.assertTrue(VillagerTaskNavigationUtil.moveOnLadderToward(helper.getLevel(), villager, target, 0.5D),
                        "the fixture dismounts at the top of the ladder");
                BlockPos landing = villager.blockPosition();
                helper.assertFalse(landing.equals(rung), "the villager has landed off the ladder");
                BlockPos destination = landing.east(3);
                WalkTarget walkTarget = new WalkTarget(destination, 0.5F, 0);
                Path path = simplePath(destination);
                if (owner == 0) {
                    villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, walkTarget);
                } else if (owner == 1) {
                    villager.getNavigation().moveTo(path, 0.5D);
                } else {
                    villager.setTarget(new Villager(EntityType.VILLAGER, helper.getLevel()));
                }
                villager.setPos(villager.getX() + 0.15D, villager.getY(), villager.getZ());
                Vec3 position = villager.position();
                VillagerTaskNavigationUtil.tickPathLadders(helper.getLevel(), villager);
                helper.assertValueEqual(villager.position(), position, "landing stabilization does not pull movement owner " + owner + " back");
                if (owner == 0) {
                    helper.assertTrue(villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null) == walkTarget,
                            "a new vanilla walk target survives landing stabilization");
                } else if (owner == 1) {
                    helper.assertFalse(villager.getNavigation().isDone(), "a new ordinary path survives landing stabilization");
                }
            }
        } finally {
            VillagerTaskNavigationUtil.clearRuntimeState(villager);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void trafficSidestepsRejectMagmaAndFire(GameTestHelper helper) {
        CountingVillager villager = ladderSearchFixture(helper);
        BlockPos origin = villager.blockPosition();
        helper.getLevel().setBlock(origin.north().below(), Blocks.MAGMA_BLOCK.defaultBlockState(), 2);
        helper.getLevel().setBlock(origin.south().below(), Blocks.MAGMA_BLOCK.defaultBlockState(), 2);
        helper.assertTrue(VillagerTrafficService.safeSidestep(helper.getLevel(), villager, new Vec3(1, 0, 0)) == null,
                "traffic holds rather than steering onto magma on either side");
        helper.getLevel().setBlock(origin.north().below(), Blocks.STONE.defaultBlockState(), 2);
        Vec3 safe = VillagerTrafficService.safeSidestep(helper.getLevel(), villager, new Vec3(1, 0, 0));
        helper.assertTrue(safe != null && safe.z < villager.getZ(), "traffic chooses the safe side");
        helper.getLevel().setBlock(origin.north(), Blocks.FIRE.defaultBlockState(), 2);
        helper.assertTrue(VillagerTrafficService.safeSidestep(helper.getLevel(), villager, new Vec3(1, 0, 0)) == null,
                "non-solid fire cannot be a passing position");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void trafficScansAreStaggeredWithoutInterruptingActiveYield(GameTestHelper helper) {
        CountingVillager trailing = ladderSearchFixture(helper);
        Villager leading = new Villager(EntityType.VILLAGER, helper.getLevel());
        leading.setNoAi(true);
        leading.setNoGravity(true);
        leading.setPos(trailing.getX() + 1, trailing.getY(), trailing.getZ());
        helper.getLevel().addFreshEntity(leading);
        BlockPos destination = trailing.blockPosition().east(4);
        trailing.getNavigation().moveTo(simplePath(destination), 0.5D);
        leading.getNavigation().moveTo(simplePath(destination), 0.5D);
        checkTrafficScan(helper, trailing, leading, 0);
    }

    private static void checkTrafficScan(GameTestHelper helper, Villager trailing, Villager leading, int checks) {
        trailing.setOnGround(true);
        leading.setOnGround(true);
        boolean scanTick = TickThrottle.isSpreadTick(trailing.getUUID(), helper.getLevel().getGameTime(), 3);
        boolean yielding = VillagerTrafficService.controlNavigation(trailing);
        helper.assertValueEqual(yielding, scanTick, "new conflict detection only runs on the villager's scan tick");
        if (!yielding) {
            helper.assertTrue(checks < 3, "a new conflict is detected within three ticks");
            helper.runAfterDelay(1, () -> checkTrafficScan(helper, trailing, leading, checks + 1));
            return;
        }
        helper.runAfterDelay(1, () -> {
            try {
                trailing.setOnGround(true);
                leading.setOnGround(true);
                helper.assertFalse(TickThrottle.isSpreadTick(trailing.getUUID(), helper.getLevel().getGameTime(), 3),
                        "the tick after detection is not another scan tick");
                helper.assertTrue(VillagerTrafficService.controlNavigation(trailing), "an active yield is still applied on non-scan ticks");
                helper.succeed();
            } finally {
                leading.discard();
            }
        });
    }

    @GameTest(template = "empty")
    public static void surfaceEscapeDoesNotSteerIntoAnUnreachableLedge(GameTestHelper helper) {
        CountingVillager villager = new CountingVillager(helper.getLevel());
        BlockPos origin = helper.absolutePos(new BlockPos(2, 2, 2));
        villager.setPos(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 0.5D);
        BlockPos ledge = origin.above(3);
        helper.getLevel().setBlock(ledge.below(), Blocks.STONE.defaultBlockState(), 2);
        helper.assertFalse(VillagerSurfaceEscapePlanner.moveToSurfaceEscapeTarget(helper.getLevel(), villager, ledge, 0.5D),
                "a nearby ledge without a path or ladder reports failure");
        helper.assertFalse(villager.getMoveControl().hasWanted(), "failure does not steer into the ledge");
        helper.assertFalse(VillagerSurfaceEscapePlanner.moveToSurfaceEscapeTarget(helper.getLevel(), villager, origin.east(3).above(), 0.5D),
                "the ladder fallback cannot bypass a failed path to a lower ledge");
        helper.assertFalse(villager.getMoveControl().hasWanted(), "the lower ledge does not receive unchecked direct steering");
        villager.navigation().reachableFromSearch = 1;
        helper.assertTrue(VillagerSurfaceEscapePlanner.moveToSurfaceEscapeTarget(helper.getLevel(), villager, ledge, 0.5D),
                "a reachable surface path is still accepted");
        VillagerTaskNavigationUtil.clearRuntimeState(villager);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void lastDoorUserClosesGateAfterOpenerLeaves(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos gate = helper.absolutePos(new BlockPos(3, 2, 3));
        level.setBlock(gate.below(), Blocks.STONE.defaultBlockState(), 2);
        level.setBlock(gate, Blocks.OAK_FENCE_GATE.defaultBlockState(), 2);
        Villager first = new Villager(EntityType.VILLAGER, level);
        Villager last = new Villager(EntityType.VILLAGER, level);
        first.setNoAi(true);
        last.setNoAi(true);
        first.setPos(gate.getX() + 0.5D, gate.getY(), gate.getZ() - 0.5D);
        last.setPos(gate.getX() + 0.5D, gate.getY(), gate.getZ() - 1.0D);
        level.addFreshEntity(first);
        level.addFreshEntity(last);
        try {
            first.getNavigation().moveTo(simplePath(gate), 0.5D);
            VillagerTaskNavigationUtil.tickPathDoors(level, first);
            helper.assertTrue(level.getBlockState(gate).getValue(BlockStateProperties.OPEN), "the opener opens the gate");
            first.getNavigation().stop();
            VillagerTaskNavigationUtil.tickPathDoors(level, first);
            helper.assertTrue(level.getBlockState(gate).getValue(BlockStateProperties.OPEN), "the gate stays open for the next user");
            first.setPos(gate.getX() + 0.5D, gate.getY(), gate.getZ() + 4.5D);
            VillagerTaskNavigationUtil.tickPathDoors(level, first);
            last.setPos(gate.getX() + 0.5D, gate.getY(), gate.getZ() + 1.5D);
            VillagerTaskNavigationUtil.tickPathDoors(level, last);
            helper.assertFalse(level.getBlockState(gate).getValue(BlockStateProperties.OPEN), "the last user inherits closing responsibility");
            level.setBlock(gate, level.getBlockState(gate).setValue(BlockStateProperties.OPEN, true), 2);
            last.getNavigation().moveTo(simplePath(gate), 0.5D);
            VillagerTaskNavigationUtil.tickPathDoors(level, last);
            last.getNavigation().stop();
            VillagerTaskNavigationUtil.tickPathDoors(level, last);
            helper.assertTrue(level.getBlockState(gate).getValue(BlockStateProperties.OPEN), "a gate left open by a player remains open");
        } finally {
            VillagerTaskNavigationUtil.clearRuntimeState(first);
            VillagerTaskNavigationUtil.clearRuntimeState(last);
            first.discard();
            last.discard();
        }
        helper.succeed();
    }

    private static Path simplePath(BlockPos target) {
        return new Path(List.of(new Node(target.getX(), target.getY(), target.getZ())), target, true);
    }

    private static CountingVillager ladderSearchFixture(GameTestHelper helper) {
        var level = helper.getLevel();
        for (BlockPos relative : BlockPos.betweenClosed(new BlockPos(0, 1, 0), new BlockPos(8, 9, 8))) {
            level.setBlock(helper.absolutePos(relative), relative.getY() == 1
                    ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
        }
        for (int x : new int[] { 2, 4, 6 }) {
            for (int y = 2; y <= 6; y++) {
                level.setBlock(helper.absolutePos(new BlockPos(x, y, 5)), Blocks.STONE.defaultBlockState(), 2);
                level.setBlock(helper.absolutePos(new BlockPos(x, y, 6)),
                        Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH), 2);
            }
            level.setBlock(helper.absolutePos(new BlockPos(x, 6, 7)), Blocks.STONE.defaultBlockState(), 2);
        }
        CountingVillager villager = new CountingVillager(level);
        BlockPos origin = helper.absolutePos(new BlockPos(4, 2, 1));
        villager.setPos(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 0.5D);
        return villager;
    }

    private static final class CountingVillager extends Villager {
        private boolean blockMovement;
        private CountingVillager(Level level) {
            super(EntityType.VILLAGER, level);
        }

        @Override
        protected PathNavigation createNavigation(Level level) {
            return new CountingNavigation(this, level);
        }

        @Override
        public void move(MoverType type, Vec3 movement) {
            if (!blockMovement) {
                super.move(type, movement);
            }
        }

        private void runBrainAiStep() {
            super.customServerAiStep();
        }

        private CountingNavigation navigation() {
            return (CountingNavigation) getNavigation();
        }
    }

    private static final class CountingNavigation extends GroundPathNavigation {
        private int entitySearches;
        private int blockSearches;
        private final List<BlockPos> searchedBlocks = new ArrayList<>();
        private int reachableFromSearch = Integer.MAX_VALUE;

        private CountingNavigation(Villager villager, Level level) {
            super(villager, level);
        }

        @Override
        public Path createPath(BlockPos target, int accuracy) {
            blockSearches++;
            searchedBlocks.add(target.immutable());
            return blockSearches >= reachableFromSearch
                    ? new Path(List.of(new Node(target.getX(), target.getY(), target.getZ())), target, true) : null;
        }

        @Override
        public Path createPath(Entity target, int accuracy) {
            entitySearches++;
            return null;
        }
    }
}
