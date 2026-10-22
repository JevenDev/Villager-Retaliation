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

    private static SettlementRecord town(GameTestHelper helper, BlockPos center, UUID leader) {
        var level = helper.getLevel();
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(level.getGameTime(), level.dimension().location(), center, "Review town");
        return SettlementSavedData.get(level).establish(registry, id, leader, center, level.getGameTime()).orElseThrow();
    }
}
