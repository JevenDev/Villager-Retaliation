package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.HiredWorkSession;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureScanner;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderWorker;
import com.jvn.villagerretaliation.network.SettlementInfrastructureRequestPayload;
import com.jvn.villagerretaliation.network.SettlementInfrastructureResultPayload;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("settlement_infrastructure")
@PrefixGameTestTemplate(false)
public final class SettlementInfrastructureGameTests {
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void routesValidateConnectionsHeadroomAndStairGrade(GameTestHelper helper) {
        var f = SettlementConstructionOrderGameTests.fixture(helper);
        try {
            var a = f.origin().offset(2, -1, 4);
            var b = a.east(6);
            for (int x = 1; x < 6; x++) for (int z = -1; z <= 1; z++)
                helper.getLevel().setBlock(a.offset(x, 0, z), Blocks.AIR.defaultBlockState(), 2);
            var bridge = new SettlementInfrastructure(SettlementInfrastructure.Type.BRIDGE, a, b);
            var preview = SettlementInfrastructureTerrain.preview(helper.getLevel(), bridge, f.town()::contains);
            helper.assertTrue(preview.error().isEmpty(), "bridge spans a gap between banks: " + preview.error());
            helper.assertTrue(preview.cells().stream().anyMatch(c -> c.target().is(Blocks.OAK_FENCE)), "bridge includes railings");
            helper.getLevel().setBlock(b.east(), Blocks.AIR.defaultBlockState(), 2);
            helper.assertFalse(SettlementInfrastructureTerrain.preview(helper.getLevel(), bridge, f.town()::contains).error().isEmpty(),
                    "missing exit approach rejects a bridge");
            helper.getLevel().setBlock(b.east(), Blocks.DIRT.defaultBlockState(), 2);
            helper.getLevel().setBlock(a.east(3).above(2), Blocks.STONE.defaultBlockState(), 2);
            helper.assertFalse(SettlementInfrastructureTerrain.preview(helper.getLevel(), bridge, f.town()::contains).error().isEmpty(),
                    "blocked headroom rejects a bridge");
            helper.getLevel().setBlock(a.east(3).above(2), Blocks.AIR.defaultBlockState(), 2);
            var high = b.above(2);
            for (int x = 0; x <= 7; x++) for (int z = -1; z <= 1; z++)
                helper.getLevel().setBlock(a.offset(x, Math.min(2, x / 3), z), Blocks.DIRT.defaultBlockState(), 2);
            var stairs = new SettlementInfrastructure(SettlementInfrastructure.Type.STAIRS, a, high);
            var steps = SettlementInfrastructureTerrain.preview(helper.getLevel(), stairs, f.town()::contains);
            helper.assertTrue(steps.error().isEmpty(), "stairs follow a walkable grade: " + steps.error());
            helper.assertTrue(steps.cells().stream().anyMatch(c -> c.target().is(Blocks.OAK_STAIRS)), "stairs include actual stair blocks");
            helper.assertFalse(SettlementInfrastructureTerrain.preview(helper.getLevel(),
                    new SettlementInfrastructure(SettlementInfrastructure.Type.STAIRS, a, a.east(2).above(4)), f.town()::contains).error().isEmpty(),
                    "stairs cannot exceed one block of rise per step");
        } finally { f.builder().discard(); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void earthworkClearsTreesLevelsAndFillsWithoutTouchingContainers(GameTestHelper helper) {
        var f = SettlementConstructionOrderGameTests.fixture(helper);
        try {
            var a = f.origin().offset(2, -1, 4);
            for (var pos : BlockPos.betweenClosed(a.above(), a.offset(1, 48, 0)))
                helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            helper.getLevel().setBlock(a.above(), Blocks.OAK_LOG.defaultBlockState(), 2);
            helper.getLevel().setBlock(a.above(2), Blocks.OAK_LEAVES.defaultBlockState(), 2);
            var clear = new SettlementInfrastructure(SettlementInfrastructure.Type.CLEAR, a, a.east());
            var preview = SettlementInfrastructureTerrain.preview(helper.getLevel(), clear, f.town()::contains);
            helper.assertTrue(preview.error().isEmpty(), preview.error() + " at " + a + " ground " + SettlementInfrastructureTerrain.ground(helper.getLevel(), a.getX(), a.getZ()) + " adjacent " + SettlementInfrastructureTerrain.ground(helper.getLevel(), a.getX() + 1, a.getZ()));
            helper.assertValueEqual(preview.cells().size(), 2, "clearing starts below the tree canopy and preserves soil");
            helper.getLevel().setBlock(a.east().above(), Blocks.CHEST.defaultBlockState(), 2);
            helper.assertFalse(SettlementInfrastructureTerrain.preview(helper.getLevel(), clear, f.town()::contains).error().isEmpty(),
                    "clearing rejects containers");
            helper.getLevel().setBlock(a.east().above(), Blocks.AIR.defaultBlockState(), 2);
            helper.getLevel().setBlock(a.above(), Blocks.AIR.defaultBlockState(), 2);
            helper.getLevel().setBlock(a.above(2), Blocks.AIR.defaultBlockState(), 2);
            helper.getLevel().setBlock(a.east().above(), Blocks.DIRT.defaultBlockState(), 2);
            var level = SettlementInfrastructureTerrain.preview(helper.getLevel(),
                    new SettlementInfrastructure(SettlementInfrastructure.Type.LEVEL, a, a.east()), f.town()::contains);
            helper.assertValueEqual(level.cells().getFirst().target(), Blocks.AIR.defaultBlockState(), "leveling cuts to the first point height");
            var fill = SettlementInfrastructureTerrain.preview(helper.getLevel(),
                    new SettlementInfrastructure(SettlementInfrastructure.Type.FILL, a.east().above(), a), f.town()::contains);
            helper.assertTrue(fill.cells().stream().anyMatch(c -> c.target().is(Blocks.DIRT)), "fill quotes dirt for lower ground");
            helper.assertFalse(SettlementInfrastructureTerrain.preview(helper.getLevel(), clear, pos -> false).error().isEmpty(),
                    "unowned land cannot be cleared");
        } finally { f.builder().discard(); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void confirmationRefreshesChangedTerrainAndQueuesIdempotently(GameTestHelper helper) {
        var f = SettlementConstructionOrderGameTests.fixture(helper);
        try {
            f.town().removeDuty(f.builder().getUUID());
            var a = f.origin().offset(2, -1, 4);
            var draft = new SettlementInfrastructure(SettlementInfrastructure.Type.BRIDGE, a, a.east(6));
            var session = new SettlementInfrastructureSession();
            var id = UUID.randomUUID();
            var preview = session.request(f.leader(), f.town(), id, true, f.town()::contains,
                    new SettlementInfrastructureRequestPayload(id, 1, SettlementInfrastructureRequestPayload.Action.PREVIEW, id, draft));
            helper.assertValueEqual(preview.status(), SettlementInfrastructureResultPayload.Status.PREVIEW, preview.message());
            helper.getLevel().setBlock(a.east(2), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
            var refreshed = session.request(f.leader(), f.town(), id, true, f.town()::contains,
                    new SettlementInfrastructureRequestPayload(id, 1, SettlementInfrastructureRequestPayload.Action.CONFIRM, preview.token(), null));
            helper.assertValueEqual(refreshed.status(), SettlementInfrastructureResultPayload.Status.PREVIEW, "changed terrain requires review");
            var confirm = new SettlementInfrastructureRequestPayload(id, 1, SettlementInfrastructureRequestPayload.Action.CONFIRM, refreshed.token(), null);
            helper.assertValueEqual(session.request(f.leader(), f.town(), id, false, f.town()::contains, confirm).status(),
                    SettlementInfrastructureResultPayload.Status.REJECTED, "revoked permission cannot queue");
            var accepted = session.request(f.leader(), f.town(), id, true, f.town()::contains, confirm);
            helper.assertValueEqual(accepted.status(), SettlementInfrastructureResultPayload.Status.APPLIED, accepted.message());
            helper.assertValueEqual(session.request(f.leader(), f.town(), id, true, f.town()::contains, confirm), accepted, "retry does not queue twice");
            var project = f.town().projects().get(refreshed.token());
            helper.assertTrue(project.pending(), "construction waits for a builder");
            helper.assertTrue(project.order() != null && SettlementProject.load(project.save()).order() != null, "infrastructure plans survive reload");
            helper.assertTrue(helper.getLevel().getBlockState(a).is(Blocks.DIRT), "confirmation never places the bridge instantly");
        } finally { f.builder().discard(); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 1200, batch = "infrastructure_native")
    public static void nativeBuilderCompletesBridgeWithEscrow(GameTestHelper helper) {
        var f = SettlementConstructionOrderGameTests.fixture(helper);
        var a = f.origin().offset(2, -1, 4);
        build(helper, f, new SettlementInfrastructure(SettlementInfrastructure.Type.BRIDGE, a, a.east(6)), false);
    }

    @GameTest(template = "empty", timeoutTicks = 1800, batch = "infrastructure_pen")
    public static void completedPenRegistersAnimalHandlingWorksite(GameTestHelper helper) {
        var f = SettlementConstructionOrderGameTests.fixture(helper);
        helper.assertTrue(f.town().putArea(f.leader().getUUID(), new SettlementArea(f.area().id(), SettlementAreaType.CONSTRUCTION,
                "Builder base", f.origin().offset(-4, -1, -4), f.origin(), 5, true)), "the builder base is separate from the pen");
        var a = f.origin().offset(2, -1, 4);
        build(helper, f, new SettlementInfrastructure(SettlementInfrastructure.Type.PEN, a, a.offset(6, 0, 6)), true);
    }

    private static void build(GameTestHelper helper, SettlementConstructionGameTests.Fixture f, SettlementInfrastructure draft, boolean pen) {
        SettlementConstructionCrewGameTests.adopt(helper, f);
        var preview = SettlementInfrastructureTerrain.preview(helper.getLevel(), draft, f.town()::contains);
        helper.assertTrue(preview.error().isEmpty(), "valid plan: " + preview.error());
        var id = UUID.randomUUID();
        var order = SettlementInfrastructureTerrain.order(draft, preview, id);
        helper.assertTrue(order.commission(f.leader(), f.town(), id).isEmpty(), "order queued");
        var project = f.town().projects().get(id);
        var plan = order.plan(id);
        helper.assertTrue(project.pending() && !project.blocker().isEmpty(), "missing supplies retain the queued project: " + project.blocker());
        SettlementConstructionGameTests.fundAndDispatch(helper, f.town(), plan, f.origin(), id);
        SettlementConstructionMaterials.collect(f.town(), project, f.builder().getUUID(), SettlementConstructionMaterials.collectionTarget(project));
        var worker = new BuilderWorker();
        helper.startSequence().thenWaitUntil(() -> {
            SettlementConstructionService.reconcile(f.builder());
            int index = SettlementConstructionCoordination.select(f.town(), project, f.builder().getUUID(), plan, helper.getLevel(), helper.getLevel().getGameTime());
            if (index >= 0 && index < project.total()) {
                var target = plan.worldPos(project.origin(), plan.blocks().get(index));
                f.builder().setPos(target.north(2).atY(f.origin().getY()).getBottomCenter());
            }
            var session = HiredWorkSession.create(helper.getLevel(), f.builder(), HiredVillagerRole.BUILDER);
            worker.tick(helper.getLevel(), f.builder(), null, session.context());
            SettlementConstructionService.observe(f.builder(), HiredWorkStateStore.state(f.builder()));
            helper.assertValueEqual(project.status(), SettlementProject.Status.COMPLETED, "native construction finishes: " + project.blocker());
        }).thenExecute(() -> {
            SettlementConstructionService.tickQueue(helper.getLevel(), f.town());
            for (var block : plan.blocks()) helper.assertTrue(BuilderStructureScanner.sameSchematicState(
                    helper.getLevel().getBlockState(plan.worldPos(project.origin(), block)), block.state()), "finished output matches preview");
            if (pen) helper.assertTrue(f.town().areas().get(id) != null && f.town().areas().get(id).type() == SettlementAreaType.ANIMAL_PEN,
                    "completed pen becomes an animal-handling workplace");
            f.builder().discard();
        }).thenSucceed();
    }
}
