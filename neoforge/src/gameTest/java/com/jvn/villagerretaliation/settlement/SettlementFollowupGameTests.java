package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.*;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.network.SettlementViewBatchPayload;
import com.jvn.villagerretaliation.network.SettlementViewBatchResultPayload.Status;
import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("settlement_followup")
@PrefixGameTestTemplate(false)
public final class SettlementFollowupGameTests {
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void mapAcceptsEligibleCivicBuilders(GameTestHelper helper) {
        var level = helper.getLevel();
        var center = helper.absolutePos(new BlockPos(1, 2, 1));
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "BuilderReviewer"));
        player.setPos(center.getCenter());
        var town = town(helper, center, player.getUUID());
        town.advance();
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.CONSTRUCTION, "Build",
                center, center.offset(2, 2, 2), 1, true);
        helper.assertTrue(town.putArea(player.getUUID(), area), "construction area exists");
        var villager = EntityType.VILLAGER.create(level);
        villager.setPos(center.getCenter());
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));
        VillageAllegianceApi.assignKnown(level, villager, town.id(), AllegianceAssignmentSource.EXPLICIT_API);
        level.addFreshEntity(villager);
        try {
            helper.assertTrue(SettlementViewService.open(player, town.id()), "view opens");
            var session = SettlementViewService.session(player.getUUID());
            var target = new SettlementViewBatchPayload.Target(villager.getUUID(), null);
            var request = new SettlementViewBatchPayload(session.id, UUID.randomUUID(), SettlementViewAction.ASSIGN,
                    List.of(target), area.id(), null);
            var outcome = SettlementViewCommands.apply(session, request, target);
            helper.assertTrue(SettlementWorkforceService.assignResident(level, player, town.id(), villager.getUUID(), area.id()),
                    "control: workforce service accepts this farmer as a civic builder");
            helper.assertValueEqual(outcome.status(), Status.APPLIED, "map should accept the same builder: " + outcome.reason());
        } finally {
            player.setLastSectionPos(SectionPos.of(player));
            SettlementViewService.close(player.getUUID(), false);
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void simulationDiagnosticExcludesAutonomousTowns(GameTestHelper helper) {
        var level = helper.getLevel();
        var first = town(helper, new BlockPos(4_000_000, 80, 4_000_000), UUID.randomUUID());
        var second = town(helper, new BlockPos(4_000_096, 80, 4_000_000), UUID.randomUUID());
        int oldCap = VillagerRetaliationConfig.SETTLEMENT_MAX_BACKGROUND_VILLAGES.get();
        try {
            VillagerRetaliationConfig.SETTLEMENT_MAX_BACKGROUND_VILLAGES.set(1);
            var background = SettlementSavedData.get(level).settlements().stream()
                    .filter(t -> t.territory().active() && !SettlementSimulationService.foreground(level, t)).toList();
            long day = Math.floorDiv(level.getServer().overworld().getGameTime(), 24_000L);
            var excluded = SettlementSimulationService.select(background, day, 1).getFirst();
            var state = new AutonomousVillageState();
            state.sections.add(SectionPos.asLong(excluded.center()));
            excluded.autonomous(state);
            var eligible = background.stream().filter(t -> t.autonomous() == null).toList();
            var selected = SettlementSimulationService.select(eligible, day, 1).getFirst();
            helper.assertTrue(!SettlementSimulationService.foreground(level, first)
                    && !SettlementSimulationService.foreground(level, second), "test towns are unloaded");
            helper.assertTrue(!"simulation_capped".equals(SettlementSimulationService.suspension(level, selected)),
                    "a town selected by the real scheduler must not be reported as simulation_capped");
        } finally {
            VillagerRetaliationConfig.SETTLEMENT_MAX_BACKGROUND_VILLAGES.set(oldCap);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void commandHistoryStaysBoundedWithoutReplayingEvictedOperations(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "CommandReviewer"));
        var town = town(helper, helper.absolutePos(new BlockPos(1, 2, 1)), player.getUUID());
        try {
            helper.assertTrue(SettlementViewService.open(player, town.id()), "view opens");
            var session = SettlementViewService.session(player.getUUID());
            var targets = List.of(new SettlementViewBatchPayload.Target(UUID.randomUUID(), null));
            var first = new SettlementViewBatchPayload(session.id, SettlementViewBatchPayload.operation(session.id, 1),
                    SettlementViewAction.RETURN, targets, null, null);
            for (int sequence = 1; sequence <= 300; sequence++) {
                session.commands.submit(session, new SettlementViewBatchPayload(session.id,
                        SettlementViewBatchPayload.operation(session.id, sequence), first.action(), targets, null, null));
                helper.assertValueEqual(session.commands.tick(session, 16), 1, "commands continue after the history fills");
            }
            session.commands.submit(session, first);
            helper.assertValueEqual(session.commands.tick(session, 16), 0, "evicted commands cannot execute again");
            var pending = new SettlementViewBatchPayload(session.id, SettlementViewBatchPayload.operation(session.id, 301),
                    first.action(), targets, null, null);
            var denied = new SettlementViewBatchPayload(session.id, SettlementViewBatchPayload.operation(session.id, 302),
                    first.action(), targets, null, null);
            session.commands.submit(session, pending);
            session.commands.submit(session, pending);
            session.commands.submit(session, denied);
            helper.assertValueEqual(session.commands.tick(session, 16), 1, "duplicate pending requests execute once");
            session.commands.submit(session, denied);
            helper.assertValueEqual(session.commands.tick(session, 16), 0, "a rejected operation cannot later execute");
            session.commands.submit(session, new SettlementViewBatchPayload(session.id,
                    SettlementViewBatchPayload.operation(session.id, 303), first.action(), targets, null, null));
            helper.assertValueEqual(session.commands.tick(session, 16), 1, "new commands still work after rejection");
        } finally {
            player.setLastSectionPos(SectionPos.of(player));
            SettlementViewService.close(player.getUUID(), false);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void duplicateSurveyFactoriesAreNeverConstructed(GameTestHelper helper) {
        var simulation = new SettlementSimulationService();
        int[] built = {0};
        int[] executed = {0};
        java.util.function.Supplier<Runnable> factory = () -> {
            built[0]++;
            return () -> executed[0]++;
        };
        simulation.enqueueLazy("site", factory);
        simulation.enqueueLazy("site", factory);
        helper.assertValueEqual(built[0], 0, "queued surveys are constructed within the work budget");
        drain(helper, simulation);
        helper.assertValueEqual(built[0], 1, "only one duplicate survey is constructed");
        helper.assertValueEqual(executed[0], 1, "the survey runs once");
        simulation.enqueueLazy("site", factory);
        drain(helper, simulation);
        helper.assertValueEqual(executed[0], 2, "completed sites can be surveyed again");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void managementRequestsCoalesceAndRecheckPermissions(GameTestHelper helper) {
        var level = helper.getLevel();
        var leader = UUID.randomUUID();
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "PageReviewer"));
        var town = town(helper, helper.absolutePos(new BlockPos(1, 2, 1)), leader);
        town.setPermissions(leader, player.getUUID(), java.util.Set.of(SettlementPermission.VIEW));
        var simulation = new SettlementSimulationService();
        var responses = new java.util.ArrayList<com.jvn.villagerretaliation.network.SettlementManagementPayload>();
        var summary = new com.jvn.villagerretaliation.network.SettlementManagementRequestPayload(town.id().value(),
                com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page.SUMMARY, 0, "", 1, true);
        var history = new com.jvn.villagerretaliation.network.SettlementManagementRequestPayload(town.id().value(),
                com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page.HISTORY, 0, "", 2, false);
        simulation.requests.submit(player, summary, responses::add);
        simulation.runPending();
        simulation.requests.submit(player, history, responses::add);
        helper.assertTrue(responses.isEmpty(), "request handling does not drain planning synchronously");
        drain(helper, simulation);
        helper.assertValueEqual(responses.size(), 1, "page navigation shares the pending assessment");
        helper.assertValueEqual(responses.getFirst(), SettlementManagementService.snapshot(player, history),
                "the latest page matches a fresh synchronous assessment");
        responses.clear();
        simulation.requests.submit(player, summary, responses::add);
        simulation.runPending();
        town.setPermissions(leader, player.getUUID(), java.util.Set.of());
        drain(helper, simulation);
        helper.assertValueEqual(responses.size(), 1, "revocation returns the available town list");
        helper.assertTrue(responses.getFirst().header() == null, "revoked access cannot expose an in-progress assessment");
        town.setPermissions(leader, player.getUUID(), java.util.Set.of(SettlementPermission.VIEW));
        responses.clear();
        simulation.requests.submit(player, summary, responses::add);
        var towns = new com.jvn.villagerretaliation.network.SettlementManagementRequestPayload(null,
                com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page.TOWNS, 0, "Review", 3, false);
        simulation.requests.submit(player, towns, responses::add);
        drain(helper, simulation);
        helper.assertValueEqual(responses.size(), 1, "leaving the town cancels its pending assessment");
        helper.assertValueEqual(responses.getFirst(), SettlementManagementService.snapshot(player, towns),
                "town list searches preserve the latest request");
        helper.succeed();
    }

    private static void drain(GameTestHelper helper, SettlementSimulationService simulation) {
        int steps = 0;
        while (simulation.runPending()) {
            helper.assertTrue(++steps < 10_000, "pending work completes");
        }
    }

    private static SettlementRecord town(GameTestHelper helper, BlockPos center, UUID leader) {
        var level = helper.getLevel();
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(level.getGameTime(), level.dimension().location(), center, "Review town");
        return SettlementSavedData.get(level).establish(registry, id, leader, center, level.getGameTime()).orElseThrow();
    }
}
