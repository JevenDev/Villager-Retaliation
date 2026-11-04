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
