package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.network.SettlementManagementActionPayload;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("settlement_construction_queue")
@PrefixGameTestTemplate(false)
public final class SettlementConstructionQueueGameTests {
    private SettlementConstructionQueueGameTests() {}

    public static String rejectSmokeCommand(net.minecraft.server.level.ServerPlayer player, SettlementManagementActionPayload command) {
        return SettlementManagementService.executeFailure(player, command);
    }

    public static com.jvn.villagerretaliation.allegiance.VillageAllegianceId prepareSmoke(net.minecraft.server.level.ServerPlayer player) {
        var level = player.serverLevel();
        var center = new net.minecraft.core.BlockPos(1024, -60, 1024);
        level.setDayTime(6000L);
        level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(false, level.getServer());
        for (var pos : net.minecraft.core.BlockPos.betweenClosed(center.offset(-36, -1, -36), center.offset(36, 15, 36))) {
            level.setBlockAndUpdate(pos, pos.getY() == center.getY() - 1
                    ? net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState() : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        }
        player.teleportTo(center.getX() + .5, center.getY(), center.getZ() + .5);
        var registry = com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(level.getGameTime(), level.dimension().location(), center, "Construction smoke");
        var town = SettlementSavedData.get(level).establish(registry, id, player.getUUID(), center, level.getGameTime()).orElseThrow();
        town.advance();
        SettlementTestPlots.claim(level, town, center.offset(-48, -1, -48), center.offset(48, 16, 48));
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.CONSTRUCTION, "Builders",
                center.offset(-30, -1, -30), center.offset(30, 14, 30), 5, true);
        if (!town.putArea(player.getUUID(), area)) throw new IllegalStateException("Smoke construction assignment area rejected");
        var plan = SettlementConstructionService.scan(level,
                net.minecraft.resources.ResourceLocation.withDefaultNamespace("village/plains/houses/plains_small_house_1"),
                net.minecraft.world.level.block.Rotation.NONE);
        for (int index = 0; index < 2; index++) {
            var project = SettlementProject.create(town, area.id(), SettlementProject.AUTOMATIC_BUILDER, plan,
                    center.offset(-24 + index * 16, 0, -24), UUID.randomUUID(), level.getGameTime());
            project.reservePayment(plan.price());
            project.queue(player.getUUID());
            if (!town.queueProject(player.getUUID(), project)) throw new IllegalStateException("Smoke queue request rejected");
        }
        var lead = UUID.randomUUID();
        town.assignDuty(lead, area.id());
        var active = SettlementProject.create(town, area.id(), lead, plan, center.offset(14, 0, -24), UUID.randomUUID(), level.getGameTime());
        if (!town.startProject(player.getUUID(), active)) throw new IllegalStateException("Smoke active project rejected");
        SettlementManagementService.open(player, id);
        return id;
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "construction_queue")
    public static void pendingQueueSurvivesReloadAndRejectsStaleOrUnauthorizedMoves(GameTestHelper helper) {
        var fixture = SettlementConstructionGameTests.fixture(helper);
        try {
            var town = fixture.town();
            town.advance();
            var first = queued(helper, fixture, 0);
            var second = queued(helper, fixture, 10);
            long revision = town.constructionQueueRevision();
            helper.assertFalse(town.reorderProject(UUID.randomUUID(), second.id(), first.id(), revision), "outsiders cannot reorder construction");
            helper.assertTrue(town.reorderProject(fixture.leader().getUUID(), second.id(), first.id(), revision), "a pending project can move to the head");
            helper.assertFalse(town.reorderProject(fixture.leader().getUUID(), first.id(), second.id(), revision), "stale queue revisions cannot overwrite newer ordering");
            town.maintainProjects();
            helper.assertTrue(town.projects().containsKey(first.id()), "maintenance cannot retire pending work");
            var restored = SettlementRecord.load(town.save(helper.getLevel().registryAccess()), helper.getLevel().registryAccess(), () -> {});
            helper.assertValueEqual(List.copyOf(restored.projects().keySet()), List.of(second.id(), first.id()), "pending order survives reload");
            helper.assertValueEqual(restored.constructionQueueRevision(), town.constructionQueueRevision(), "the stale-write guard survives reload");
            helper.assertTrue(restored.projects().get(first.id()).pending(), "reload cannot turn pending work active");
            var command = new SettlementManagementActionPayload(town.id().value(),
                    new SettlementManagementActionPayload.ReorderProject(first.id(), second.id(), town.constructionQueueRevision()));
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
            try {
                SettlementManagementActionPayload.STREAM_CODEC.encode(buffer, command);
                helper.assertValueEqual(SettlementManagementActionPayload.STREAM_CODEC.decode(buffer), command, "queue moves round trip with identity and revision");
            } finally { buffer.release(); }
        } finally { fixture.builder().discard(); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "construction_queue")
    public static void activeProjectCannotMoveAndPendingCancellationCannotMintRefunds(GameTestHelper helper) {
        var fixture = SettlementConstructionGameTests.fixture(helper);
        try {
            var town = fixture.town();
            town.advance();
            var first = queued(helper, fixture, 0);
            var second = queued(helper, fixture, 10);
            var before = town.economy().balance();
            helper.assertTrue(town.activateProject(first, fixture.builder().getUUID()), "activation claims the fee and assigned builder");
            helper.assertFalse(town.reorderProject(fixture.leader().getUUID(), first.id(), second.id(), town.constructionQueueRevision()), "active construction cannot move");
            helper.assertFalse(town.reorderProject(fixture.leader().getUUID(), second.id(), first.id(), town.constructionQueueRevision()), "an active destination cannot reorder pending work");
            var afterActivation = town.economy().balance();
            town.finishProject(second.id(), SettlementProject.Status.CANCELLED);
            helper.assertValueEqual(town.economy().balance(), afterActivation, "pending cancellation refunds no unreserved fee");
            town.finishProject(first.id(), SettlementProject.Status.CANCELLED);
            helper.assertValueEqual(town.economy().balance(), before, "unworked active cancellation returns the fee once");
            town.finishProject(first.id(), SettlementProject.Status.CANCELLED);
            helper.assertValueEqual(town.economy().balance(), before, "repeated cancellation cannot mint funds");
        } finally { fixture.builder().discard(); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "construction_queue")
    public static void dispatchRequiresAnEnabledDedicatedConstructionAssignment(GameTestHelper helper) {
        var fixture = SettlementConstructionGameTests.fixture(helper);
        try {
            var town = fixture.town();
            town.advance();
            helper.assertTrue(SettlementConstructionService.eligible(helper.getLevel(), town, fixture.builder()), "an assigned construction resident is eligible");
            var project = queued(helper, fixture, 0);
            town.removeDuty(fixture.builder().getUUID());
            helper.assertFalse(SettlementConstructionService.eligible(helper.getLevel(), town, fixture.builder()), "an unassigned resident is never an implicit builder");
            SettlementConstructionService.tickQueue(helper.getLevel(), town);
            helper.assertTrue(project.pending(), "an explicit request also waits after its builder loses the dedicated duty");
            helper.assertValueEqual(project.blocker(), "builder", "missing duty reports a builder wait");
            helper.assertFalse(SettlementConstructionMaterials.reserved(project), "missing duty cannot reserve project materials");
            helper.assertTrue(town.assignDuty(fixture.builder().getUUID(), fixture.area().id()), "the construction assignment can be restored while pending");
            var area = fixture.area();
            helper.assertTrue(town.putArea(fixture.leader().getUUID(), new SettlementArea(area.id(), area.type(), area.name(),
                    area.min(), area.max(), area.priority(), false)), "an idle construction worksite can be disabled");
            helper.assertFalse(SettlementConstructionService.eligible(helper.getLevel(), town, fixture.builder()), "disabled construction assignments cannot dispatch");
        } finally { fixture.builder().discard(); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "construction_queue")
    public static void pendingSnapshotsExposeDetailedEscrowBlockersAndMissingMaterials(GameTestHelper helper) {
        var fixture = SettlementConstructionGameTests.fixture(helper);
        try {
            fixture.town().advance();
            var project = queued(helper, fixture, 0);
            project.waitFor("materials");
            project.materialEscrow().putString("Blocker", "material_transfer_pending");
            project.materialEscrow().putString("Missing", "Oak Planks x12");
            var request = new com.jvn.villagerretaliation.network.SettlementManagementRequestPayload(fixture.town().id().value(),
                    com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page.PROJECTS, 0, project.id().toString(), 1, false);
            var snapshot = SettlementManagementService.snapshot(fixture.leader(), request);
            var row = (com.jvn.villagerretaliation.network.SettlementManagementPayload.Project) snapshot.entries().getFirst();
            helper.assertValueEqual(row.blocker(), "material_transfer_pending", "the UI receives the actual escrow transfer blocker");
            helper.assertValueEqual(row.missingMaterials(), "Oak Planks x12", "the UI receives the actual missing material detail");
            project.materialEscrow().putString("Blocker", "requires_transactional_storage");
            helper.assertValueEqual(project.blocker(), "requires_transactional_storage", "updated escrow diagnostics replace the generic material wait");
            project.waitFor("builder");
            helper.assertValueEqual(project.blocker(), "builder", "a newer assignment wait supersedes stale escrow diagnostics");
        } finally { fixture.builder().discard(); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "construction_queue")
    public static void queuedTemplateFingerprintSurvivesReloadAndRejectsChangedBill(GameTestHelper helper) {
        var fixture = SettlementConstructionGameTests.fixture(helper);
        try {
            fixture.town().advance();
            var project = queued(helper, fixture, 0);
            var plan = fixture.plan();
            var changed = new com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureScanner.StructurePlan(
                    plan.entry(), plan.rotation(), plan.templateSize(), plan.localMin(), plan.localMax(), plan.blocks(),
                    plan.materials(), plan.price() + 1, plan.clearingBlocks());
            helper.assertTrue(project.matchesPlan(plan), "the accepted template matches its frozen fingerprint");
            helper.assertFalse(project.matchesPlan(changed), "same-size templates with changed fees cannot execute under an old quote");
            var restored = SettlementProject.load(project.save());
            helper.assertTrue(restored.matchesPlan(plan), "reload retains the accepted template");
            helper.assertFalse(restored.matchesPlan(changed), "reload cannot discard the quote fingerprint");
        } finally { fixture.builder().discard(); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "construction_queue")
    public static void builderAssignmentPreservesVanillaProfessionAndWaitsWithoutResources(GameTestHelper helper) {
        var fixture = SettlementConstructionGameTests.fixture(helper);
        try {
            var town = fixture.town();
            town.advance();
            fixture.builder().setVillagerData(fixture.builder().getVillagerData().setProfession(VillagerProfession.FARMER));
            helper.assertTrue(SettlementWorkforceService.assign(helper.getLevel(), fixture.leader(), town.id(), fixture.builder(), fixture.area().id()),
                    "an adult farmer can explicitly accept a settlement construction duty");
            helper.assertValueEqual(fixture.builder().getVillagerData().getProfession(), VillagerProfession.FARMER,
                    "dedicated builder duty never changes a vanilla profession");
            helper.assertFalse(com.jvn.villagerretaliation.interaction.HiredVillagerContractService.hasActiveOrPendingContract(fixture.builder()),
                    "settlement builder assignment creates no personal contract");
            var project = queued(helper, fixture, 0);
            var balance = town.economy().balance();
            SettlementConstructionService.tickQueue(helper.getLevel(), town);
            helper.assertTrue(project.pending(), "missing materials or site readiness keeps the project pending");
            helper.assertValueEqual(project.placed(), 0, "waiting cannot place blocks");
            helper.assertValueEqual(town.economy().balance(), balance, "waiting cannot debit the fee");
        } finally { fixture.builder().discard(); }
        helper.succeed();
    }

    private static SettlementProject queued(GameTestHelper helper, SettlementConstructionGameTests.Fixture fixture, int east) {
        var project = SettlementProject.create(fixture.town(), fixture.area().id(), fixture.builder().getUUID(), fixture.plan(),
                fixture.origin().east(east), UUID.randomUUID(), helper.getLevel().getGameTime());
        project.reservePayment(fixture.plan().price());
        project.queue(fixture.leader().getUUID());
        helper.assertTrue(fixture.town().queueProject(fixture.leader().getUUID(), project), "nonoverlapping pending request is accepted without reserving resources");
        return project;
    }
}
