package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderSitePlanner;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureScanner;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderWorker;
import com.jvn.villagerretaliation.network.SettlementWallRequestPayload;
import com.jvn.villagerretaliation.network.SettlementWallResultPayload;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("settlement_construction_orders")
@PrefixGameTestTemplate(false)
public final class SettlementConstructionOrderGameTests {
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void repairQueuesOnlyMissingBlocksAndSurvivesReload(GameTestHelper helper) {
        var f = fixture(helper);
        try {
            var entry = wall(helper, f);
            var missing = f.origin().offset(4, 1, 4);
            helper.getLevel().setBlock(missing, Blocks.AIR.defaultBlockState(), 2);
            f.town().removeDuty(f.builder().getUUID());
            var session = new SettlementWallSession();
            var id = UUID.randomUUID();
            var selected = session.request(helper.getLevel(), id, true, f.town()::contains,
                    new SettlementWallRequestPayload(id, 1, SettlementWallRequestPayload.Action.SELECT_REPAIR, id, null,
                            SettlementWallRequestPayload.NONE, missing), f.town().id(), f.leader());
            helper.assertValueEqual(selected.status(), SettlementWallResultPayload.Status.SELECTED, "missing wall blocks remain selectable");
            var preview = session.request(helper.getLevel(), id, true, f.town()::contains,
                    new SettlementWallRequestPayload(id, 2, SettlementWallRequestPayload.Action.PREVIEW, id, entry.wall(), entry.id(), missing),
                    f.town().id(), f.leader());
            helper.assertValueEqual(preview.status(), SettlementWallResultPayload.Status.PREVIEW, "repair previews missing blocks");
            var confirm = new SettlementWallRequestPayload(id, 2, SettlementWallRequestPayload.Action.CONFIRM, preview.token(), null);
            var accepted = session.request(helper.getLevel(), id, true, f.town()::contains, confirm, f.town().id(), f.leader());
            helper.assertValueEqual(accepted.status(), SettlementWallResultPayload.Status.APPLIED, accepted.message());
            helper.assertValueEqual(session.request(helper.getLevel(), id, true, f.town()::contains, confirm, f.town().id(), f.leader()),
                    accepted, "retries return the same receipt");
            var project = f.town().projects().get(preview.token());
            helper.assertValueEqual(project.total(), 1, "intact blocks are excluded from repair labor and materials");
            helper.assertTrue(helper.getLevel().getBlockState(missing).isAir(), "confirmation does not repair instantly");
            var restored = SettlementProject.load(project.save());
            helper.assertValueEqual(BuilderStructureScanner.fingerprint(restored.order().plan(project.id())),
                    BuilderStructureScanner.fingerprint(project.order().plan(project.id())), "approved repairs persist exactly");
            helper.getLevel().setBlock(missing, Blocks.CHEST.defaultBlockState(), 2);
            helper.assertFalse(BuilderSitePlanner.validateStartedSite(helper.getLevel(), f.builder(), restored.order().plan(project.id()),
                    project.origin()).valid(), "a new container blocks an approved repair");
        } finally { f.builder().discard(); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void cancellationRetainsFinishedChangesWithoutRecreatingDemolishedBlocks(GameTestHelper helper) {
        var f = fixture(helper);
        try {
            var entry = wall(helper, f);
            var preview = SettlementWallTerrain.demolition(helper.getLevel(), entry.blocks(), f.town()::contains);
            var order = SettlementConstructionOrder.wall("Wall demolition", preview, SettlementWallMaterials.Cost.ZERO, List.of(entry), List.of());
            var id = UUID.randomUUID();
            f.town().removeDuty(f.builder().getUUID());
            helper.assertTrue(order.commission(f.leader(), f.town(), id).isEmpty(), "demolition queues");
            var project = f.town().projects().get(id);
            var top = f.origin().offset(4, 1, 4);
            helper.getLevel().setBlock(top, Blocks.AIR.defaultBlockState(), 2);
            helper.assertTrue(SettlementConstructionService.cancel(f.leader(), f.town(), id), "demolition cancels");
            order.record(helper.getLevel(), f.town(), project);
            var retained = SettlementWallsSavedData.get(helper.getLevel()).get(entry.id());
            helper.assertFalse(retained.blocks().containsKey(top), "cancelled removal is retained in wall ownership");
            helper.assertTrue(retained.blocks().containsKey(top.below()), "unfinished removal remains a recorded wall");
            order.record(helper.getLevel(), f.town(), project);
            helper.assertTrue(helper.getLevel().getBlockState(top).isAir(), "recording never recreates removed blocks");
        } finally { f.builder().discard(); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 600, batch = "orders_native")
    public static void nativeBuilderDemolishesRecordedBlocksThroughTheProject(GameTestHelper helper) {
        var f = fixture(helper);
        SettlementConstructionCrewGameTests.adopt(helper, f);
        var entry = wall(helper, f);
        var preview = SettlementWallTerrain.demolition(helper.getLevel(), entry.blocks(), f.town()::contains);
        var order = SettlementConstructionOrder.wall("Wall demolition", preview, SettlementWallMaterials.Cost.ZERO, List.of(entry), List.of());
        var id = UUID.randomUUID();
        helper.assertTrue(order.commission(f.leader(), f.town(), id).isEmpty(), "demolition commissioned");
        var project = f.town().projects().get(id);
        var plan = order.plan(id);
        SettlementConstructionGameTests.fundAndDispatch(helper, f.town(), plan, f.origin(), id);
        var worker = new BuilderWorker();
        helper.startSequence().thenWaitUntil(() -> {
            SettlementConstructionService.reconcile(f.builder());
            int index = SettlementConstructionCoordination.select(f.town(), project, f.builder().getUUID(), plan,
                    helper.getLevel(), helper.getLevel().getGameTime());
            if (index >= 0 && index < project.total())
                f.builder().setPos(plan.worldPos(project.origin(), plan.blocks().get(index)).north(2).getBottomCenter());
            var session = com.jvn.villagerretaliation.interaction.HiredWorkSession.create(helper.getLevel(), f.builder(),
                    com.jvn.villagerretaliation.interaction.HiredVillagerRole.BUILDER);
            worker.tick(helper.getLevel(), f.builder(), null, session.context());
            SettlementConstructionService.observe(f.builder(), HiredWorkStateStore.state(f.builder()));
            helper.assertValueEqual(project.status(), SettlementProject.Status.COMPLETED, "builder completes demolition: " + project.blocker());
        }).thenExecute(() -> {
            SettlementConstructionService.tickQueue(helper.getLevel(), f.town());
            helper.assertTrue(SettlementWallsSavedData.get(helper.getLevel()).get(entry.id()) == null, "completed demolition removes its record");
            entry.blocks().keySet().forEach(pos -> helper.assertTrue(helper.getLevel().getBlockState(pos).isAir(), "all approved blocks removed"));
            f.builder().discard();
        }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 1800, batch = "orders_upgrade_repair")
    public static void nativeBuilderUpgradesThenRepairsRecordedDesign(GameTestHelper helper) {
        var f = fixture(helper);
        SettlementConstructionCrewGameTests.adopt(helper, f);
        var old = wall(helper, f);
        var design = new SettlementWall(SettlementWall.Type.LOW_STONE, old.wall().nodes(), false, 0,
                old.wall().nodeTowers(), old.wall().part());
        var upgrade = new SettlementWallUpgrade(SettlementWallsSavedData.get(helper.getLevel()), old, design);
        var preview = upgrade.preview(helper.getLevel(), f.town()::contains);
        helper.assertTrue(preview.error().isEmpty(), "upgrade is valid: " + preview.error());
        var order = SettlementConstructionOrder.wall("Wall upgrade", preview, upgrade.cost(preview), upgrade.originals(), upgrade.replacements(preview));
        var id = UUID.randomUUID();
        helper.assertTrue(order.commission(f.leader(), f.town(), id).isEmpty(), "upgrade queues");
        var current = new SettlementProject[]{f.town().projects().get(id)};
        fund(helper, f, current[0]);
        var worker = new BuilderWorker();
        helper.startSequence().thenWaitUntil(() -> work(helper, f, current[0], worker)).thenExecute(() -> {
            SettlementConstructionService.tickQueue(helper.getLevel(), f.town());
            var upgraded = SettlementWallsSavedData.get(helper.getLevel()).get(old.id());
            helper.assertValueEqual(upgraded.wall().type(), SettlementWall.Type.LOW_STONE, "completion records the upgraded design");
            upgraded.blocks().forEach((pos, state) -> helper.assertTrue(BuilderStructureScanner.sameSchematicState(
                    helper.getLevel().getBlockState(pos), state), "upgrade matches its recorded blocks"));
            var missing = upgraded.blocks().keySet().stream().max(java.util.Comparator.comparingInt(BlockPos::getY)).orElseThrow();
            helper.getLevel().setBlock(missing, Blocks.AIR.defaultBlockState(), 2);
            var repairPreview = SettlementWallTerrain.repair(helper.getLevel(), upgraded.blocks(), f.town()::contains);
            var repair = SettlementConstructionOrder.wall("Wall repair", repairPreview,
                    SettlementWallMaterials.cost(upgraded.wall(), repairPreview, null), List.of(upgraded), List.of(upgraded));
            var cancelled = UUID.randomUUID();
            String repairError = repair.commission(f.leader(), f.town(), cancelled);
            helper.assertTrue(repairError.isEmpty(), "repair queues: " + repairError);
            helper.assertTrue(SettlementConstructionService.cancel(f.leader(), f.town(), cancelled), "repair cancels");
            repair.record(helper.getLevel(), f.town(), f.town().projects().get(cancelled));
            helper.assertTrue(SettlementWallsSavedData.get(helper.getLevel()).get(old.id()).blocks().containsKey(missing),
                    "cancelling an unbuilt repair preserves the missing design block");
            var retry = UUID.randomUUID();
            var resumed = SettlementConstructionOrder.wall("Wall repair", repairPreview,
                    SettlementWallMaterials.cost(upgraded.wall(), repairPreview, null), List.of(upgraded), List.of(upgraded));
            String retryError = resumed.commission(f.leader(), f.town(), retry);
            helper.assertTrue(retryError.isEmpty(), "repair can be commissioned again: " + retryError);
            current[0] = f.town().projects().get(retry);
            helper.assertValueEqual(current[0].total(), 1, "repair rebuilds only the missing block");
            fund(helper, f, current[0]);
        }).thenWaitUntil(() -> work(helper, f, current[0], worker)).thenExecute(() -> {
            var repaired = SettlementWallsSavedData.get(helper.getLevel()).get(old.id());
            repaired.blocks().forEach((pos, state) -> helper.assertTrue(BuilderStructureScanner.sameSchematicState(
                    helper.getLevel().getBlockState(pos), state), "repair restores " + pos + " expected " + state + " actual " + helper.getLevel().getBlockState(pos)));
            f.builder().discard();
        }).thenSucceed();
    }

    private static void fund(GameTestHelper helper, SettlementConstructionGameTests.Fixture f, SettlementProject project) {
        SettlementConstructionGameTests.fundAndDispatch(helper, f.town(), project.order().plan(project.id()), f.origin(), project.id());
        SettlementConstructionMaterials.collect(f.town(), project, f.builder().getUUID(), SettlementConstructionMaterials.collectionTarget(project));
    }

    private static void work(GameTestHelper helper, SettlementConstructionGameTests.Fixture f, SettlementProject project, BuilderWorker worker) {
        var plan = project.order().plan(project.id());
        if (project.site() != null) SettlementConstructionSiteService.refresh(helper.getLevel(), project.site());
        SettlementConstructionService.reconcile(f.builder());
        int index = SettlementConstructionCoordination.select(f.town(), project, f.builder().getUUID(), plan,
                helper.getLevel(), helper.getLevel().getGameTime());
        if (index >= 0 && index < project.total())
            f.builder().setPos(plan.worldPos(project.origin(), plan.blocks().get(index)).north(2).getBottomCenter());
        var session = com.jvn.villagerretaliation.interaction.HiredWorkSession.create(helper.getLevel(), f.builder(),
                com.jvn.villagerretaliation.interaction.HiredVillagerRole.BUILDER);
        worker.tick(helper.getLevel(), f.builder(), null, session.context());
        SettlementConstructionService.observe(f.builder(), HiredWorkStateStore.state(f.builder()));
        helper.assertValueEqual(project.status(), SettlementProject.Status.COMPLETED, "builder completes the order: " + project.blocker());
        helper.assertTrue(project.site() == null || !project.site().unsettled(), "completed work is saved before another order starts");
        for (var block : plan.blocks()) helper.assertTrue(BuilderStructureScanner.sameSchematicState(
                helper.getLevel().getBlockState(plan.worldPos(project.origin(), block)), block.state()),
                "completed work matches the planned change at " + plan.worldPos(project.origin(), block));
    }

    static SettlementConstructionGameTests.Fixture fixture(GameTestHelper helper) {
        var f = SettlementConstructionGameTests.fixture(helper);
        f.town().advance();
        for (var pos : BlockPos.betweenClosed(f.area().min(), f.area().max()))
            helper.getLevel().setBlock(pos, pos.getY() < f.origin().getY() ? Blocks.DIRT.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
        return f;
    }

    private static SettlementWallsSavedData.Entry wall(GameTestHelper helper, SettlementConstructionGameTests.Fixture f) {
        var bottom = f.origin().offset(4, 0, 4);
        var design = new SettlementWall(SettlementWall.Type.PALISADE, List.of(bottom, bottom.east()), false, 0,
                List.of(SettlementWall.Tower.DEFAULT, SettlementWall.Tower.NONE), SettlementWall.Part.TOWER);
        var blocks = Map.of(bottom, Blocks.OAK_LOG.defaultBlockState(), bottom.above(), Blocks.OAK_LOG.defaultBlockState());
        blocks.forEach((pos, state) -> helper.getLevel().setBlock(pos, state, 2));
        var id = UUID.randomUUID();
        var entry = new SettlementWallsSavedData.Entry(id, id, f.town().id(), f.town().dimension(), design, blocks);
        SettlementWallsSavedData.get(helper.getLevel()).replace(List.of(entry));
        return entry;
    }
}
