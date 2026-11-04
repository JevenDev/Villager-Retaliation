package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredWorkSession;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderBuildPhase;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderWorker;
import com.jvn.villagerretaliation.network.SettlementManagementActionPayload;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("settlement_construction_pause")
@PrefixGameTestTemplate(false)
public final class SettlementConstructionPauseGameTests {
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void pausedProjectRetainsResourcesAndStopsNativeAndBackgroundWork(GameTestHelper helper) {
        var f = SettlementConstructionGameTests.fixture(helper);
        try {
            f.town().advance();
            var project = SettlementProject.create(f.town(), f.area().id(), f.builder().getUUID(), f.plan(), f.origin(),
                    UUID.randomUUID(), helper.getLevel().getGameTime());
            helper.assertTrue(f.town().startProject(f.leader().getUUID(), project), "project starts");
            SettlementConstructionService.reconcile(f.builder());
            var state = HiredWorkStateStore.state(f.builder());
            com.jvn.villagerretaliation.interaction.work.builder.BuilderTaskState.setPlacedIndex(state, 1);
            SettlementConstructionService.observe(f.builder(), state);
            var escrow = project.materialEscrow().copy();
            var funds = f.town().economy().balance();
            var outsider = net.neoforged.neoforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                    new com.mojang.authlib.GameProfile(UUID.randomUUID(), "PauseOutsider"));
            helper.assertFalse(SettlementConstructionService.pause(outsider, f.town(), project.id(), true), "outsiders cannot pause");
            var command = new SettlementManagementActionPayload(f.town().id().value(),
                    new SettlementManagementActionPayload.PauseProject(project.id(), true));
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
            try {
                SettlementManagementActionPayload.STREAM_CODEC.encode(buffer, command);
                helper.assertValueEqual(SettlementManagementActionPayload.STREAM_CODEC.decode(buffer), command, "pause command round trips");
            } finally { buffer.release(); }
            helper.assertTrue(SettlementManagementService.execute(f.leader(), command), "build authority pauses");
            helper.assertValueEqual(project.phase(), BuilderBuildPhase.PAUSED, "snapshot phase exposes pause");
            var before = com.jvn.villagerretaliation.interaction.work.builder.BuilderTaskState.task(state).copy();
            var session = HiredWorkSession.create(helper.getLevel(), f.builder(), HiredVillagerRole.BUILDER);
            new BuilderWorker().tick(helper.getLevel(), f.builder(), null, session.context());
            helper.assertValueEqual(com.jvn.villagerretaliation.interaction.work.builder.BuilderTaskState.task(state), before, "native work preserves project progress");
            var worker = SettlementWorkerState.capture(helper.getLevel(), f.builder(),
                    SettlementWorkforceService.duty(f.builder()).orElseThrow(), helper.getLevel().getGameTime());
            SettlementBackgroundConstruction.tick(helper.getLevel(), f.town(), worker, 200, 1.0);
            helper.assertValueEqual(worker.status(), "construction_paused", "background work also stops");
            helper.assertValueEqual(project.placed(), 1, "pause retains existing progress without creating blocks");
            helper.assertValueEqual(project.materialEscrow(), escrow, "pause retains material escrow");
            helper.assertValueEqual(f.town().economy().balance(), funds, "pause does not settle payment");
            helper.assertTrue(project.reservesFootprint(), "paused construction keeps its site");
            helper.assertValueEqual(f.town().assignedProject(f.builder().getUUID()), project, "crew remains assigned");
            var restored = SettlementProject.load(project.save());
            helper.assertTrue(restored.paused(), "pause survives saving");
            helper.assertTrue(SettlementConstructionService.pause(f.leader(), f.town(), project.id(), false), "project resumes");
            helper.assertTrue(SettlementConstructionService.allows(f.builder(), state, f.plan()), "resumed work is allowed");
            helper.assertTrue(SettlementConstructionService.pause(f.leader(), f.town(), project.id(), true), "project pauses again");
            helper.assertTrue(SettlementConstructionService.cancel(f.leader(), f.town(), project.id()), "paused projects can cancel");
            helper.assertFalse(project.paused(), "finished projects clear pause state");
        } finally { f.builder().discard(); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void pendingPausePersistsAndIsSkippedByDispatch(GameTestHelper helper) {
        var f = SettlementConstructionGameTests.fixture(helper);
        try {
            f.town().advance();
            var project = SettlementProject.create(f.town(), f.area().id(), f.builder().getUUID(), f.plan(), f.origin(),
                    UUID.randomUUID(), helper.getLevel().getGameTime());
            project.queue(f.leader().getUUID());
            helper.assertTrue(f.town().queueProject(f.leader().getUUID(), project), "project queues");
            helper.assertTrue(SettlementConstructionService.pause(f.leader(), f.town(), project.id(), true), "pending project pauses");
            SettlementConstructionService.tickQueue(helper.getLevel(), f.town());
            helper.assertTrue(project.pending() && project.paused(), "dispatch leaves paused requests alone");
            helper.assertTrue(project.materialEscrow().isEmpty(), "paused queue does not reserve supplies");
            var restored = SettlementRecord.load(f.town().save(helper.getLevel().registryAccess()), helper.getLevel().registryAccess(), () -> {});
            helper.assertTrue(restored.projects().get(project.id()).paused(), "settlement reload retains pending pause");
            helper.assertTrue(SettlementConstructionService.pause(f.leader(), f.town(), project.id(), false), "pending request resumes");
            SettlementConstructionService.tickQueue(helper.getLevel(), f.town());
            helper.assertFalse(project.blocker().equals("paused"), "dispatch diagnoses resumed requests normally");
        } finally { f.builder().discard(); }
        helper.succeed();
    }
}
