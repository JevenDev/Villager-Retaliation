package com.jvn.villagerretaliation.villager;

import com.jvn.villagerretaliation.combat.VillagerRetaliationRetaliationUtil;
import com.jvn.villagerretaliation.interaction.HiredMiningMode;
import com.jvn.villagerretaliation.interaction.work.HiredWorkContext;
import com.jvn.villagerretaliation.interaction.work.VillagerWorkerGameTests;
import com.jvn.villagerretaliation.interaction.work.mining.MiningWorker;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
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
        private CountingVillager(Level level) {
            super(EntityType.VILLAGER, level);
        }

        @Override
        protected PathNavigation createNavigation(Level level) {
            return new CountingNavigation(this, level);
        }

        private CountingNavigation navigation() {
            return (CountingNavigation) getNavigation();
        }
    }

    private static final class CountingNavigation extends GroundPathNavigation {
        private int entitySearches;
        private int blockSearches;
        private int reachableFromSearch = Integer.MAX_VALUE;

        private CountingNavigation(Villager villager, Level level) {
            super(villager, level);
        }

        @Override
        public Path createPath(BlockPos target, int accuracy) {
            blockSearches++;
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
