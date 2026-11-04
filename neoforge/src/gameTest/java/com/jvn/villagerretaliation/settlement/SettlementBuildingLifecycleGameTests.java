package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureScanner;
import com.jvn.villagerretaliation.network.SettlementBuildingRequestPayload;
import com.jvn.villagerretaliation.network.SettlementBuildingRequestPayload.Action;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;

@GameTestHolder("vr_building_lifecycle")
@net.neoforged.neoforge.gametest.PrefixGameTestTemplate(false)
public final class SettlementBuildingLifecycleGameTests {
    private SettlementBuildingLifecycleGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void repairsQuoteOnlyMissingWorkAndSurviveReload(GameTestHelper helper) {
        var f = SettlementConstructionGameTests.fixture(helper);
        try {
            var source = completed(helper, f);
            var block = f.plan().blocks().stream().filter(b -> b.state().is(Blocks.COBBLESTONE)).findFirst().orElseThrow();
            helper.getLevel().setBlock(f.origin().offset(block.localPos()), Blocks.AIR.defaultBlockState(), 2);
            var request = request(f, source, Action.REPAIR);
            var quote = SettlementBuildingManagement.inspect(f.leader(), request);
            helper.assertFalse(quote.fingerprint().isEmpty(), "repair has a reviewed quote: " + quote.feedback());
            helper.assertTrue(quote.lines().stream().anyMatch(line -> line.startsWith("Work: 1 blocks")), "only the missing block is charged");
            var confirmation = new SettlementBuildingRequestPayload(request.town(), request.building(), request.request(), request.action(), true, quote.fingerprint());
            var queued = SettlementBuildingManagement.inspect(f.leader(), confirmation);
            helper.assertTrue(queued.queued(), "repair queues: " + queued.feedback());
            var project = f.town().project(request.request());
            var plan = SettlementConstructionService.scan(helper.getLevel(), project);
            helper.assertValueEqual(plan.blocks().size(), 1, "repair contains one change");
            var loaded = SettlementProject.load(project.save());
            helper.assertValueEqual(BuilderStructureScanner.fingerprint(SettlementConstructionService.scan(helper.getLevel(), loaded)), quote.fingerprint(), "work survives reload");
            helper.assertTrue(SettlementBuildingManagement.inspect(f.leader(), confirmation).queued(), "confirmation replay is idempotent");
            helper.assertValueEqual(f.town().projects().size(), 2, "replay cannot queue twice");
        } finally { f.builder().discard(); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void staleQuotesAndProtectedContainersCannotBeOverwritten(GameTestHelper helper) {
        var f = SettlementConstructionGameTests.fixture(helper);
        try {
            var source = completed(helper, f);
            var block = f.plan().blocks().stream().filter(b -> b.state().is(Blocks.COBBLESTONE)).findFirst().orElseThrow();
            var pos = f.origin().offset(block.localPos());
            helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            var request = request(f, source, Action.REPAIR);
            var quote = SettlementBuildingManagement.inspect(f.leader(), request);
            helper.getLevel().setBlock(pos, Blocks.GOLD_BLOCK.defaultBlockState(), 2);
            var reply = SettlementBuildingManagement.inspect(f.leader(), new SettlementBuildingRequestPayload(request.town(), request.building(), request.request(), Action.RENOVATE, true, quote.fingerprint()));
            helper.assertFalse(reply.queued(), "a changed building and action reject the old quote");
            helper.getLevel().setBlock(pos, Blocks.CHEST.defaultBlockState(), 2);
            helper.assertTrue(SettlementBuildingManagement.inspect(f.leader(), request(f, source, Action.RENOVATE)).fingerprint().isEmpty(), "renovation preserves containers");
            helper.assertTrue(SettlementBuildingManagement.inspect(f.leader(), request(f, source, Action.DISMANTLE)).fingerprint().isEmpty(), "dismantling requires container removal");
            helper.assertTrue(helper.getLevel().getBlockState(pos).is(Blocks.CHEST), "review never edits the world");
        } finally { f.builder().discard(); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void renovationRequiresReviewAndDismantlingRetiresOnlyOnCompletion(GameTestHelper helper) {
        var f = SettlementConstructionGameTests.fixture(helper);
        try {
            var source = completed(helper, f);
            for (var block : f.plan().blocks()) if (block.state().hasBlockEntity()) helper.getLevel().setBlock(f.origin().offset(block.localPos()), Blocks.AIR.defaultBlockState(), 2);
            var request = request(f, source, Action.DISMANTLE);
            var quote = SettlementBuildingManagement.inspect(f.leader(), request);
            helper.assertFalse(quote.fingerprint().isEmpty(), "empty authored building can be dismantled: " + quote.feedback());
            var reply = SettlementBuildingManagement.inspect(f.leader(), new SettlementBuildingRequestPayload(request.town(), request.building(), request.request(), request.action(), true, quote.fingerprint()));
            helper.assertTrue(reply.queued(), "dismantling queues");
            var project = f.town().project(request.request());
            helper.assertFalse(source.retired(), "queued dismantling keeps the original building");
            project.finish(SettlementProject.Status.COMPLETED);
            project.buildingWork().record(f.town(), project);
            helper.assertTrue(source.retired(), "completed dismantling releases the building footprint");
            helper.assertTrue(SettlementProject.load(source.save()).retired(), "retirement survives reload");
        } finally { f.builder().discard(); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void completedBuildingsRemainManageableBeyondTheRecentProjectHistory(GameTestHelper helper) {
        var f = SettlementConstructionGameTests.fixture(helper);
        try {
            var state = new SettlementConstructionState(); UUID first = null;
            for (int i = 0; i < 40; i++) {
                var project = SettlementProject.create(f.town(), f.area().id(), f.builder().getUUID(), f.plan(), f.origin(), UUID.randomUUID(), helper.getLevel().getGameTime());
                if (first == null) first = project.id();
                project.finish(SettlementProject.Status.COMPLETED); state.add(project); state.maintainProjects(f.town());
            }
            helper.assertTrue(state.project(first) != null, "completed building identities outlive the bounded activity history");
            var tag = new net.minecraft.nbt.CompoundTag(); state.save(tag);
            var restored = new SettlementConstructionState(); restored.load(tag, f.town(), f.town().radius());
            helper.assertTrue(restored.project(first).completedBuilding(), "building management survives save and history trimming");
        } finally { f.builder().discard(); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void repairUsesReservedMaterialsAndTheNativePlacementJournal(GameTestHelper helper) {
        var f = SettlementConstructionGameTests.fixture(helper);
        try {
            var source = completed(helper, f);
            var missing = f.plan().blocks().stream().filter(b -> b.state().is(Blocks.COBBLESTONE)).findFirst().orElseThrow();
            var position = f.origin().offset(missing.localPos());
            helper.getLevel().setBlock(position, Blocks.AIR.defaultBlockState(), 2);
            var request = request(f, source, Action.REPAIR); var quote = SettlementBuildingManagement.inspect(f.leader(), request);
            helper.assertTrue(SettlementBuildingManagement.inspect(f.leader(), new SettlementBuildingRequestPayload(request.town(), request.building(),
                    request.request(), request.action(), true, quote.fingerprint())).queued(), "repair queues");
            var project = f.town().project(request.request()); var plan = SettlementConstructionService.scan(helper.getLevel(), project);
            SettlementConstructionGameTests.fundAndDispatch(helper, f.town(), plan, project.origin(), project.id());
            SettlementConstructionMaterials.collect(f.town(), project, f.builder().getUUID(), SettlementConstructionMaterials.collectionTarget(project));
            SettlementConstructionService.reconcile(f.builder());
            var group = com.jvn.villagerretaliation.interaction.work.builder.BuilderWorker.placementGroup(plan, project.origin(), plan.blocks().getFirst());
            for (int i = 0; i < 64 && !project.site().observed(position); i++)
                SettlementConstructionSiteService.observeArea(helper.getLevel(), f.town(), f.town().workplace(project.area()), null);
            helper.assertTrue(SettlementConstructionMaterials.place(f.builder(), com.jvn.villagerretaliation.interaction.HiredWorkStateStore.state(f.builder()), group), "repair uses native journaled placement");
            helper.assertTrue(helper.getLevel().getBlockState(position).is(Blocks.COBBLESTONE), "the missing block is physically repaired");
            helper.assertTrue(project.materialEscrow().getList("Quote", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0).getInt("Spent") == 1, "the repair spends exactly one reserved material");
        } finally { f.builder().discard(); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void maintenanceQuotesIncludeWaterAndEveryStackedMaterial(GameTestHelper helper) {
        var state = Blocks.OAK_STAIRS.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, true);
        var block = new BuilderStructureScanner.BuildBlock(net.minecraft.core.BlockPos.ZERO, state, null,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.OAK_STAIRS), BuilderStructureScanner.BuilderToolAction.NONE, Blocks.AIR.defaultBlockState());
        var work = new SettlementBuildingWork(UUID.randomUUID(), Action.REPAIR, net.minecraft.core.BlockPos.ZERO, java.util.List.of(block), 1);
        var id = UUID.randomUUID(); var plan = work.plan(id);
        helper.assertValueEqual(plan.materials().size(), 2, "waterlogged output charges the stairs and water bucket");
        helper.assertValueEqual(BuilderStructureScanner.fingerprint(SettlementBuildingWork.load(work.save()).plan(id)), BuilderStructureScanner.fingerprint(plan), "exact maintenance bill survives reload");
        helper.succeed();
    }

    public static com.jvn.villagerretaliation.network.SettlementManagementPayload.Project prepareCompletedSmoke(net.minecraft.server.level.ServerPlayer player,
            com.jvn.villagerretaliation.allegiance.VillageAllegianceId id) {
        var level = player.serverLevel(); var town = SettlementSavedData.get(level).get(id).orElseThrow();
        var project = town.projects().values().stream().filter(SettlementProject::active).findFirst().orElseThrow();
        var plan = SettlementConstructionService.scan(level, project);
        for (var block : plan.blocks()) level.setBlock(plan.worldPos(project.origin(), block), block.state(), 2);
        var missing = plan.blocks().stream().filter(block -> block.state().is(Blocks.COBBLESTONE)).findFirst().orElseThrow();
        level.setBlock(plan.worldPos(project.origin(), missing), Blocks.AIR.defaultBlockState(), 2);
        town.finishProject(project.id(), SettlementProject.Status.COMPLETED);
        return (com.jvn.villagerretaliation.network.SettlementManagementPayload.Project) SettlementManagementService.snapshot(player,
                new com.jvn.villagerretaliation.network.SettlementManagementRequestPayload(id.value(),
                        com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page.PROJECTS, 0, project.id().toString(), 1, false)).entries().getFirst();
    }

    static SettlementProject completed(GameTestHelper helper, SettlementConstructionGameTests.Fixture f) {
        f.town().advance();
        for (var block : f.plan().blocks()) helper.getLevel().setBlock(f.origin().offset(block.localPos()), block.state(), 2);
        var source = SettlementProject.create(f.town(), f.area().id(), f.builder().getUUID(), f.plan(), f.origin(), UUID.randomUUID(), helper.getLevel().getGameTime());
        helper.assertTrue(f.town().startProject(f.leader().getUUID(), source), "original building is registered");
        f.town().finishProject(source.id(), SettlementProject.Status.COMPLETED);
        return source;
    }

    private static SettlementBuildingRequestPayload request(SettlementConstructionGameTests.Fixture f, SettlementProject source, Action action) {
        return new SettlementBuildingRequestPayload(f.town().id().value(), source.id(), UUID.randomUUID(), action, false, "");
    }
}
