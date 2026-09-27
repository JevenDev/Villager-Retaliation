package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.interaction.work.HiredWorkContext;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderTaskState;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderWorker;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SettlementBuilderClearanceGameTests {
    private SettlementBuilderClearanceGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_builder_clearance")
    public static void aCompletedBuilderLeavesItsPendingWorldPlacementWithoutRestartingWork(GameTestHelper helper) {
        fixture(helper, SettlementProject.Status.COMPLETED, Blocks.OAK_PLANKS.defaultBlockState(),
                fixture -> assertClearance(helper, fixture));
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_builder_clearance")
    public static void aCancelledBuilderLeavesItsPendingWorldPlacementWithoutRestartingWork(GameTestHelper helper) {
        fixture(helper, SettlementProject.Status.CANCELLED, Blocks.OAK_PLANKS.defaultBlockState(),
                fixture -> assertClearance(helper, fixture));
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_builder_clearance")
    public static void anEnclosedRetiredBuilderWaitsWithoutTeleportingOrReopeningItsProject(GameTestHelper helper) {
        fixture(helper, SettlementProject.Status.CANCELLED, Blocks.OAK_PLANKS.defaultBlockState(), fixture -> {
            var level = helper.getLevel();
            for (var position : BlockPos.betweenClosed(fixture.origin().offset(-1, 0, -1), fixture.origin().offset(1, 2, 1))) {
                if (position.getX() != fixture.origin().getX() || position.getZ() != fixture.origin().getZ()) {
                    level.setBlock(position, Blocks.STONE.defaultBlockState(), 2);
                }
            }
            var before = fixture.builder().position();
            var result = new BuilderWorker().tick(level, fixture.builder(), null, context(fixture));
            helper.assertFalse(result.progressed(), "an enclosed builder cannot invent a movement step");
            helper.assertValueEqual(result.status(), "interaction.work.builder.pending_world", "the builder reports waiting for world delivery");
            helper.assertValueEqual(fixture.builder().position(), before, "blocked clearance never teleports");
            helper.assertTrue(fixture.site().pending() && level.getBlockState(fixture.origin()).isAir(), "the occupied placement stays pending");
            helper.assertFalse(BuilderTaskState.hasTask(HiredWorkStateStore.state(fixture.builder())), "waiting does not recreate the cancelled job");
            helper.assertValueEqual(fixture.project().status(), SettlementProject.Status.CANCELLED, "the terminal project stays cancelled");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_builder_clearance")
    public static void anotherResidentDoesNotInheritARetiredBuildersClearance(GameTestHelper helper) {
        fixture(helper, SettlementProject.Status.COMPLETED, Blocks.OAK_PLANKS.defaultBlockState(), fixture -> {
            var level = helper.getLevel();
            var other = EntityType.VILLAGER.create(level);
            other.setPos(fixture.origin().getBottomCenter());
            VillageAllegianceApi.assignKnown(level, other, fixture.town().id(), AllegianceAssignmentSource.EXPLICIT_API);
            fixture.town().assignDuty(other.getUUID(), fixture.area().id());
            SettlementWorkforceService.reconcile(level, other);
            helper.assertTrue(SettlementConstructionService.pendingClearance(other) == null,
                    "another resident does not acquire movement instructions from this project's history");
            helper.assertTrue(SettlementConstructionService.pendingClearance(fixture.builder()) != null,
                    "the original builder still recognizes its unfinished delivery");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_builder_clearance")
    public static void nonCollidingPlacementsDoNotMoveAWaitingBuilder(GameTestHelper helper) {
        fixture(helper, SettlementProject.Status.CANCELLED, Blocks.TORCH.defaultBlockState(), fixture -> {
            helper.assertTrue(SettlementConstructionService.pendingClearance(fixture.builder()) == null,
                    "a torch can be delivered without moving the builder's body");
            var result = new BuilderWorker().tick(helper.getLevel(), fixture.builder(), null, context(fixture));
            helper.assertValueEqual(result.status(), "interaction.work.builder.choose_structure", "the builder remains available for a new instruction");
            helper.assertTrue(fixture.site().pending(), "checking movement does not mutate the paid world operation");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_builder_clearance")
    public static void deliveredReceiptsDoNotMoveTheBuilderOrReplayAnOldPlacement(GameTestHelper helper) {
        fixture(helper, SettlementProject.Status.CANCELLED, Blocks.OAK_PLANKS.defaultBlockState(), fixture -> {
            var level = helper.getLevel();
            fixture.builder().setPos(fixture.origin().north(3).getBottomCenter());
            var placement = fixture.site().placements().values().iterator().next();
            helper.assertTrue(placement.reconcile(level, fixture.project().id()), "the original operation reaches the vacated block");
            level.setBlock(fixture.origin(), Blocks.AIR.defaultBlockState(), 2);
            fixture.builder().setPos(fixture.origin().getBottomCenter());
            helper.assertTrue(fixture.site().pending(), "the saved journal still awaits its cleanup");
            helper.assertTrue(SettlementConstructionService.pendingClearance(fixture.builder()) == null,
                    "the native receipt prevents stale history from requesting another evacuation");
            var result = new BuilderWorker().tick(level, fixture.builder(), null, context(fixture));
            helper.assertValueEqual(result.status(), "interaction.work.builder.choose_structure", "the builder stays available on the player's later edit");
            SettlementConstructionSiteService.refresh(level, fixture.site());
            helper.assertFalse(fixture.site().pending(), "receipt reconciliation retires the old journal");
            helper.assertTrue(level.getBlockState(fixture.origin()).isAir(), "the old paid placement cannot overwrite the later edit");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_builder_clearance")
    public static void checkingRetiredClearanceDoesNotLoadAnAbsentSite(GameTestHelper helper) {
        SettlementBackgroundConstructionGameTests.fixture(helper, false, blocks(), fixture -> {
            fixture.site().queue(placement(fixture, Blocks.OAK_PLANKS.defaultBlockState()));
            fixture.town().finishProject(fixture.project().id(), SettlementProject.Status.CANCELLED);
            fixture.builder().setPos(fixture.origin().getBottomCenter());
            helper.assertFalse(helper.getLevel().hasChunkAt(fixture.origin()), "the remote construction chunk begins absent");
            helper.assertTrue(SettlementConstructionService.pendingClearance(fixture.builder()) == null,
                    "physical clearance waits until its actual world is present");
            helper.assertFalse(helper.getLevel().hasChunkAt(fixture.origin()), "checking old projects cannot force a chunk load");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 160, batch = "settlement_builder_clearance_walk")
    public static void aFinishedBuilderPhysicallyWalksClearAndAllowsNativeDelivery(GameTestHelper helper) {
        var level = helper.getLevel();
        var origin = helper.absolutePos(new BlockPos(2, 2, 2));
        var prepared = new SettlementBackgroundConstructionGameTests.Fixture[1];
        SettlementBackgroundConstructionGameTests.fixture(helper, origin, blocks(), false, fixture -> {
            prepared[0] = fixture;
            for (var position : BlockPos.betweenClosed(fixture.site().min(), fixture.site().max())) {
                level.setBlock(position, position.getY() < origin.getY()
                        ? Blocks.DIRT.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
            }
            var builder = fixture.builder();
            builder.setPos(origin.getBottomCenter());
            builder.setOnGround(true);
            builder.setNoAi(false);
            fixture.site().queue(placement(fixture, Blocks.OAK_PLANKS.defaultBlockState()));
            fixture.town().finishProject(fixture.project().id(), SettlementProject.Status.COMPLETED);
        });
        var fixture = prepared[0];
        var builder = fixture.builder();
        var before = builder.position();
        long day = level.getDayTime();
        helper.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
            @Override
            public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo test) {
            }

            @Override
            public void testPassed(net.minecraft.gametest.framework.GameTestInfo test,
                                   net.minecraft.gametest.framework.GameTestRunner runner) {
                builder.discard();
                level.setDayTime(day);
            }

            @Override
            public void testFailed(net.minecraft.gametest.framework.GameTestInfo test,
                                   net.minecraft.gametest.framework.GameTestRunner runner) {
                builder.discard();
                level.setDayTime(day);
            }

            @Override
            public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo previous,
                                         net.minecraft.gametest.framework.GameTestInfo next,
                                         net.minecraft.gametest.framework.GameTestRunner runner) {
            }
        });
        level.setDayTime(6_000L);
        helper.assertTrue(level.addFreshEntity(builder), "the finished builder returns to native ticking");
        SettlementBackgroundService.onActive(builder);
        var result = new BuilderWorker().tick(level, builder, null, context(fixture));
        helper.assertTrue(result.progressed(), "the idle builder starts clearing its paid placement");
        helper.assertValueEqual(builder.position(), before, "starting the route never changes physical position");
        helper.succeedWhen(() -> {
            SettlementConstructionSiteService.update(level, fixture.town());
            helper.assertTrue(level.getBlockState(origin).is(Blocks.OAK_PLANKS), "native movement must vacate the block before it can be delivered");
            helper.assertFalse(fixture.project().reservesFootprint(), "successful world delivery releases the completed footprint");
            helper.assertTrue(builder.position().distanceToSqr(before) > 0.25D, "native entity ticks actually move the builder");
            helper.assertFalse(BuilderTaskState.hasTask(HiredWorkStateStore.state(builder)), "walking cannot recreate the completed task");
            helper.assertValueEqual(fixture.project().status(), SettlementProject.Status.COMPLETED, "the finished project remains complete");
        });
    }

    private static void assertClearance(GameTestHelper helper, SettlementBackgroundConstructionGameTests.Fixture fixture) {
        var level = helper.getLevel();
        var builder = fixture.builder();
        var before = builder.position();
        var inventory = HiredJobInventory.getJobInventory(builder).captureSnapshot();
        var status = fixture.project().status();
        var result = new BuilderWorker().tick(level, builder, null, context(fixture));
        helper.assertTrue(result.progressed(), "the retired builder starts a real native path");
        helper.assertValueEqual(result.status(), "interaction.work.builder.moving_to_site", "clearance uses the existing movement status");
        helper.assertValueEqual(builder.position(), before, "starting clearance does not teleport the worker");
        helper.assertTrue(fixture.site().pending() && level.getBlockState(fixture.origin()).isAir(), "the occupied paid placement still waits");
        helper.assertFalse(BuilderTaskState.hasTask(HiredWorkStateStore.state(builder)), "clearance never restores the retired build task");
        helper.assertValueEqual(fixture.project().status(), status, "movement cannot reopen completed or cancelled work");
        helper.assertValueEqual(HiredJobInventory.getJobInventory(builder).captureSnapshot(), inventory, "clearance neither spends nor refunds materials");
        var path = builder.getNavigation().getPath();
        helper.assertTrue(path != null && path.canReach(), "the builder has a reachable native escape route");
        helper.assertFalse(path.getTarget().equals(fixture.origin()), "the destination leaves the pending block");
        builder.setPos(path.getTarget().getBottomCenter());
        SettlementConstructionSiteService.update(level, fixture.town());
        helper.assertTrue(level.getBlockState(fixture.origin()).is(Blocks.OAK_PLANKS), "the paid block applies after the builder leaves");
        helper.assertFalse(fixture.project().reservesFootprint(), "world delivery releases the retired reservation");
        helper.assertTrue(SettlementConstructionService.pendingClearance(builder) == null, "finished delivery leaves no evacuation request");
    }

    private static HiredWorkContext context(SettlementBackgroundConstructionGameTests.Fixture fixture) {
        return new HiredWorkContext(HiredJobInventory.getJobInventory(fixture.builder()), HiredWorkStateStore.state(fixture.builder()),
                fixture.origin(), fixture.area().min(), fixture.area().max(), 8, 5, true, 100, true, true);
    }

    private static void fixture(GameTestHelper helper, SettlementProject.Status status, BlockState after,
                                Consumer<SettlementBackgroundConstructionGameTests.Fixture> check) {
        SettlementBackgroundConstructionGameTests.fixture(helper, true, blocks(), fixture -> {
            var level = helper.getLevel();
            for (var position : BlockPos.betweenClosed(fixture.site().min(), fixture.site().max())) {
                level.setBlock(position, position.getY() < fixture.origin().getY()
                        ? Blocks.DIRT.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
            }
            var builder = fixture.builder();
            builder.setPos(fixture.origin().getBottomCenter());
            builder.setOnGround(true);
            helper.assertTrue(level.addFreshEntity(builder), "the returning builder is present in its native world");
            fixture.site().queue(placement(fixture, after));
            fixture.town().finishProject(fixture.project().id(), status);
            SettlementBackgroundService.onActive(builder);
            helper.assertFalse(BuilderTaskState.hasTask(HiredWorkStateStore.state(builder)), "native reconciliation retires the old task");
            check.accept(fixture);
        });
    }

    private static SettlementConstructionPlacement placement(SettlementBackgroundConstructionGameTests.Fixture fixture, BlockState after) {
        return new SettlementConstructionPlacement(List.of(new SettlementConstructionPlacement.Part(
                fixture.origin(), Blocks.AIR.defaultBlockState(), after, null)));
    }

    private static Map<BlockPos, BlockState> blocks() {
        return Map.of(BlockPos.ZERO, Blocks.OAK_PLANKS.defaultBlockState(), BlockPos.ZERO.east(), Blocks.OAK_PLANKS.defaultBlockState());
    }
}
