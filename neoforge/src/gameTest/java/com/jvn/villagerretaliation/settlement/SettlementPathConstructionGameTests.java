package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredWorkSession;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderConstructionCost;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderSitePlanner;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureScanner;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderWorker;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("settlement_path_construction")
@PrefixGameTestTemplate(false)
public final class SettlementPathConstructionGameTests {
    public record ClientReview(com.jvn.villagerretaliation.allegiance.VillageAllegianceId town, java.util.Set<UUID> projects,
                               net.minecraft.nbt.CompoundTag order, java.util.Map<BlockPos, net.minecraft.world.level.block.state.BlockState> terrain) { }

    public static ClientReview reviewClient(net.minecraft.server.level.ServerPlayer player, SettlementPath path) {
        var level = player.serverLevel();
        var town = SettlementSavedData.get(level).at(level.dimension().location(), BlockPos.containing(path.nodes().getFirst().position())).orElseThrow();
        var preview = SettlementPathTerrain.preview(level, path, town::contains);
        if (!preview.error().isEmpty()) throw new IllegalStateException(preview.error());
        var terrain = new java.util.LinkedHashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
        for (var cell : preview.cells()) {
            terrain.put(cell.position(), level.getBlockState(cell.position()));
            terrain.put(cell.position().above(), level.getBlockState(cell.position().above()));
        }
        var order = SettlementPathTerrain.order(preview);
        if (order.protectedSite(level, town)) throw new IllegalStateException("Smoke path must stay clear of protected worksites");
        return new ClientReview(town.id(), java.util.Set.copyOf(town.projects().keySet()), order.save(), java.util.Map.copyOf(terrain));
    }

    public static void verifyClientQueued(net.minecraft.server.level.ServerPlayer player, ClientReview review) {
        var level = player.serverLevel();
        var town = SettlementSavedData.get(level).get(review.town()).orElseThrow();
        var added = town.projects().values().stream().filter(project -> !review.projects().contains(project.id())).toList();
        if (added.size() != 1) throw new IllegalStateException("Path confirmation must queue exactly one project: " + added.size());
        var project = added.getFirst();
        if (!project.pending() || project.blocker().isBlank() || project.order() == null || !project.order().save().equals(review.order()))
            throw new IllegalStateException("Queued path must retain the reviewed construction order and report its missing builder");
        review.terrain().forEach((pos, state) -> {
            if (!level.getBlockState(pos).equals(state)) throw new IllegalStateException("Queued path changed terrain before construction at " + pos);
        });
        if (!SettlementConstructionService.cancel(player, town, project.id())) throw new IllegalStateException("Smoke path project cancels");
        review.terrain().forEach((pos, state) -> {
            if (!level.getBlockState(pos).equals(state)) throw new IllegalStateException("Cancelling queued path changed terrain at " + pos);
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void savedPathPreservesSoilsAndRejectsLaterTerrainChanges(GameTestHelper helper) {
        var f = fixture(helper);
        try {
            var base = f.origin().offset(2, -1, 4);
            var level = helper.getLevel();
            var soils = List.of(Blocks.DIRT, Blocks.GRASS_BLOCK, Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT, Blocks.PODZOL, Blocks.MYCELIUM);
            for (int i = 0; i < soils.size(); i++) level.setBlock(base.east(i), soils.get(i).defaultBlockState(), 2);
            level.setBlock(base.above(), Blocks.LARGE_FERN.defaultBlockState(), 2);
            level.setBlock(base.above(2), Blocks.LARGE_FERN.defaultBlockState().setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER), 2);
            var preview = SettlementPathTerrain.preview(level, path(base, base.east(5)), f.town()::contains);
            helper.assertTrue(preview.error().isEmpty(), preview.error());
            var order = SettlementPathTerrain.order(preview);
            var id = UUID.randomUUID();
            var plan = SettlementConstructionOrder.load(order.save()).plan(id);
            helper.assertValueEqual(plan.blocks().size(), 8, "six soils and both fern halves are saved");
            helper.assertTrue(BuilderConstructionCost.requirements(plan).isEmpty(), "flattening requires no block materials");
            helper.assertValueEqual(plan.blocks().getFirst().state(), Blocks.AIR.defaultBlockState(), "upper fern clears before soil work");
            helper.assertValueEqual(plan.blocks().stream().filter(block -> block.toolAction() == BuilderStructureScanner.BuilderToolAction.SHOVEL_FLATTEN).count(),
                    6L, "every soil conversion requires a shovel");
            helper.assertTrue(BuilderSitePlanner.validateStartedSite(level, f.builder(), plan, order.origin()).valid(), "saved path accepts its original terrain");
            for (var state : List.of(Blocks.CHEST.defaultBlockState(), Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState())) {
                level.setBlock(base.east(3), state, 2);
                helper.assertFalse(BuilderSitePlanner.validateStartedSite(level, f.builder(), plan, order.origin()).valid(),
                        "changed or missing ground blocks work instead of creating free soil");
            }
        } finally { f.builder().discard(); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 800, batch = "path_native")
    public static void nativeBuilderWaitsForShovelThenBuildsWithToolWear(GameTestHelper helper) {
        var f = fixture(helper);
        SettlementConstructionCrewGameTests.adopt(helper, f);
        var base = f.origin().offset(2, -1, 4);
        helper.getLevel().setBlock(base.east().above(), Blocks.SHORT_GRASS.defaultBlockState(), 2);
        var order = SettlementPathTerrain.order(SettlementPathTerrain.preview(helper.getLevel(), path(base, base.east(4)), f.town()::contains));
        var id = UUID.randomUUID();
        String error = order.commission(f.leader(), f.town(), id);
        helper.assertTrue(error.isEmpty(), error);
        var project = f.town().projects().get(id);
        var plan = order.plan(id);
        SettlementConstructionGameTests.fundAndDispatch(helper, f.town(), plan, f.origin(), id);
        var worker = new BuilderWorker();
        var inventory = HiredJobInventory.getJobInventory(f.builder());
        helper.startSequence().thenWaitUntil(() -> {
            work(helper, f, project, worker);
            helper.assertValueEqual(project.blocker(), "missing_placement_tool", "missing shovel is a visible project blocker");
            helper.assertTrue(helper.getLevel().getBlockState(base).is(Blocks.DIRT), "no shovel means no flattening");
        }).thenExecute(() -> inventory.setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_SHOVEL)))
                .thenWaitUntil(() -> {
                    work(helper, f, project, worker);
                    helper.assertTrue(helper.getLevel().getBlockState(base).is(Blocks.DIRT_PATH), "builder physically flattens the first cell");
                }).thenExecute(() -> {
                    var restored = SettlementProject.load(project.save());
                    helper.assertValueEqual(restored.save(), project.save(), "partial work and its crew progress survive reload");
                    helper.assertValueEqual(BuilderStructureScanner.fingerprint(restored.order().plan(id)), BuilderStructureScanner.fingerprint(plan),
                            "reloaded path keeps the approved work order");
                }).thenWaitUntil(() -> {
                    work(helper, f, project, worker);
                    helper.assertValueEqual(project.status(), SettlementProject.Status.COMPLETED, "path completes: " + project.blocker());
                }).thenExecute(() -> {
                    for (int i = 0; i < 5; i++) helper.assertTrue(helper.getLevel().getBlockState(base.east(i)).is(Blocks.DIRT_PATH), "all path cells are built");
                    helper.assertTrue(helper.getLevel().getBlockState(base.east().above()).isAir(), "approved grass is cleared");
                    helper.assertValueEqual(inventory.findTool(stack -> stack.is(Items.IRON_SHOVEL)).getDamageValue(), 5, "each soil conversion wears the shovel once");
                    helper.assertTrue(BuilderConstructionCost.requirements(plan).isEmpty(), "no materials are manufactured or charged for dirt paths");
                    f.builder().discard();
                }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 600, batch = "path_cancel")
    public static void cancellationKeepsBuiltPathAndStopsRemainingWork(GameTestHelper helper) {
        var f = fixture(helper);
        SettlementConstructionCrewGameTests.adopt(helper, f);
        var base = f.origin().offset(2, -1, 4);
        var order = SettlementPathTerrain.order(SettlementPathTerrain.preview(helper.getLevel(), path(base, base.east(8)), f.town()::contains));
        var id = UUID.randomUUID();
        String error = order.commission(f.leader(), f.town(), id);
        helper.assertTrue(error.isEmpty(), error);
        var project = f.town().projects().get(id);
        SettlementConstructionGameTests.fundAndDispatch(helper, f.town(), order.plan(id), f.origin(), id);
        HiredJobInventory.getJobInventory(f.builder()).setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_SHOVEL));
        var worker = new BuilderWorker();
        helper.startSequence().thenWaitUntil(() -> {
            work(helper, f, project, worker);
            helper.assertTrue(helper.getLevel().getBlockState(base).is(Blocks.DIRT_PATH), "first cell is completed");
        }).thenExecute(() -> {
            helper.assertTrue(SettlementConstructionService.cancel(f.leader(), f.town(), id), "partial path cancels");
            SettlementConstructionService.reconcile(f.builder());
            helper.assertTrue(helper.getLevel().getBlockState(base).is(Blocks.DIRT_PATH), "cancellation preserves finished path");
            helper.assertTrue(helper.getLevel().getBlockState(base.east(8)).is(Blocks.DIRT), "unbuilt path remains unchanged");
            helper.assertValueEqual(SettlementProject.load(project.save()).status(), SettlementProject.Status.CANCELLED, "cancellation survives reload");
            helper.assertTrue(f.town().assignedProject(f.builder().getUUID()) == null, "cancelled worker has no active project");
            f.builder().discard();
        }).thenSucceed();
    }

    private static void work(GameTestHelper helper, SettlementConstructionGameTests.Fixture f, SettlementProject project, BuilderWorker worker) {
        var plan = project.order().plan(project.id());
        if (project.site() != null) SettlementConstructionSiteService.refresh(helper.getLevel(), project.site());
        SettlementConstructionService.reconcile(f.builder());
        int index = SettlementConstructionCoordination.select(f.town(), project, f.builder().getUUID(), plan,
                helper.getLevel(), helper.getLevel().getGameTime());
        if (index >= 0 && index < project.total())
            f.builder().setPos(plan.worldPos(project.origin(), plan.blocks().get(index)).north(2).atY(f.origin().getY()).getBottomCenter());
        var session = HiredWorkSession.create(helper.getLevel(), f.builder(), HiredVillagerRole.BUILDER);
        worker.tick(helper.getLevel(), f.builder(), null, session.context());
        SettlementConstructionService.observe(f.builder(), HiredWorkStateStore.state(f.builder()));
    }

    static SettlementConstructionGameTests.Fixture fixture(GameTestHelper helper) {
        var f = SettlementConstructionGameTests.fixture(helper);
        f.town().advance();
        var origin = f.origin().above(20);
        var area = new SettlementArea(f.area().id(), SettlementAreaType.CONSTRUCTION, "Path builders",
                f.area().min().above(20), f.area().max().above(20), 5, true);
        SettlementTestPlots.claim(helper.getLevel(), f.town(), area.min(), area.max());
        helper.assertTrue(f.town().putArea(f.leader().getUUID(), area), "path worksite is above the test structure roof");
        for (var pos : BlockPos.betweenClosed(area.min(), area.max()))
            helper.getLevel().setBlock(pos, pos.getY() < origin.getY() ? Blocks.DIRT.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
        f.builder().setPos(origin.north(2).getBottomCenter());
        f.leader().setPos(origin.getCenter());
        SettlementWorkforceService.reconcile(helper.getLevel(), f.builder());
        return new SettlementConstructionGameTests.Fixture(f.town(), area, f.leader(), f.builder(), origin, f.plan());
    }

    private static SettlementPath path(BlockPos first, BlockPos last) {
        return new SettlementPath(List.of(new SettlementPath.Node(first.getCenter()), new SettlementPath.Node(last.getCenter())),
                1, SettlementPath.Style.CLEAN, SettlementPath.Material.DIRT, 42, false);
    }
}
