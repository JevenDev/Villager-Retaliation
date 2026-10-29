package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.network.SettlementViewBatchPayload;
import com.jvn.villagerretaliation.network.SettlementViewBatchResultPayload.Status;
import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("settlement_move")
@PrefixGameTestTemplate(false)
public final class SettlementMoveGameTests {
    private SettlementMoveGameTests() { }

    @GameTest(template = "empty", batch = "settlement_move", timeoutTicks = 240)
    public static void residentsWalkToDestinationAndKeepTheirJobs(GameTestHelper helper) {
        var fixture = fixture(helper);
        var level = helper.getLevel();
        var civilian = resident(helper, fixture, 0);
        var worker = resident(helper, fixture, 2);
        worker.setVillagerData(worker.getVillagerData().setProfession(net.minecraft.world.entity.npc.VillagerProfession.FARMER));
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.FARM, "Farm",
                fixture.base().below(), fixture.base().offset(4, 2, 4), 5, true);
        helper.assertTrue(fixture.town().putArea(fixture.player().getUUID(), area), "farm is registered");
        fixture.town().assignDuty(worker.getUUID(), area.id());
        SettlementWorkforceService.reconcile(level, worker);
        var destination = fixture.base().east(10);
        helper.assertTrue(SettlementViewService.eligible(fixture.player(), fixture.town()), "leader has an eligible view");
        helper.assertTrue(level.getEntity(civilian.getUUID()) == civilian, "civilian is loaded");
        helper.assertTrue(SettlementWorkforceService.home(level, civilian).filter(home -> home.id().equals(fixture.town().id())).isPresent(), "civilian belongs to the settlement");
        helper.assertFalse(SettlementWorkforceService.hasPersonalCommitment(civilian), "civilian has no personal commitment");
        helper.assertFalse(com.jvn.villagerretaliation.interaction.VillagerAiArbitration.interruptsStudy(level, civilian), "civilian is available for movement");
        helper.assertTrue(fixture.town().contains(destination) && level.hasChunkAt(destination), "destination is loaded settlement ground");
        helper.assertTrue(com.jvn.villagerretaliation.interaction.work.HiredMoveToBlockFaceJob.isValidApproachPosition(level, destination), "destination is standable");
        helper.assertTrue(SettlementMoveService.command(fixture.player(), fixture.town(), civilian.getUUID(), destination), "unassigned resident accepts Move to");
        helper.assertTrue(SettlementMoveService.command(fixture.player(), fixture.town(), worker.getUUID(), destination.south(2)), "non-guard worker accepts Move to");
        helper.assertValueEqual(SettlementMoveService.destination(civilian), destination, "active orders expose their destination");
        var page = com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page.RESIDENTS;
        var details = SettlementManagementService.snapshot(fixture.player(), new com.jvn.villagerretaliation.network.SettlementManagementRequestPayload(
                fixture.town().id().value(), page, 0, "", 1, false));
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), level.registryAccess());
        try {
            com.jvn.villagerretaliation.network.SettlementManagementPayload.STREAM_CODEC.encode(buffer, details);
            helper.assertValueEqual(com.jvn.villagerretaliation.network.SettlementManagementPayload.STREAM_CODEC.decode(buffer), details,
                    "detailed resident payload preserves movement destinations");
            var rows = SettlementManagementService.overview(fixture.player(), fixture.town()).get(page);
            var overview = new com.jvn.villagerretaliation.network.SettlementViewOverviewPayload(UUID.randomUUID(), 1, 1,
                    details.header(), page, 0, rows.size(), rows);
            com.jvn.villagerretaliation.network.SettlementViewOverviewPayload.STREAM_CODEC.encode(buffer, overview);
            var decoded = com.jvn.villagerretaliation.network.SettlementViewOverviewPayload.STREAM_CODEC.decode(buffer);
            helper.assertValueEqual(decoded, overview, "world view snapshots preserve movement destinations");
            var row = decoded.entries().stream().filter(entry -> entry.id().equals(civilian.getUUID()))
                    .map(com.jvn.villagerretaliation.network.SettlementManagementPayload.Resident.class::cast).findFirst().orElseThrow();
            helper.assertValueEqual(row.moveDestination(), destination, "server publishes the actual move target");
            helper.assertValueEqual(buffer.readableBytes(), 0, "movement fields consume exactly their packet bytes");
        } finally { buffer.release(); }
        helper.assertTrue(com.jvn.villagerretaliation.villager.VillagerBehaviorSuppressionPolicy.shouldSuppressVanillaBrainTick(level, civilian),
                "ordinary wandering yields to the movement order");
        helper.succeedWhen(() -> {
            helper.assertTrue(civilian.blockPosition().distSqr(destination) <= 4, "civilian reaches the requested location");
            helper.assertTrue(worker.blockPosition().distSqr(destination.south(2)) <= 4, "worker reaches the requested location");
            SettlementMoveService.tick(civilian);
            SettlementMoveService.tick(worker);
            helper.assertFalse(SettlementMoveService.controls(civilian) || SettlementMoveService.controls(worker), "arrival releases movement control");
            helper.assertTrue(SettlementMoveService.destination(civilian) == null && SettlementMoveService.destination(worker) == null,
                    "arrival removes both destinations");
            helper.assertTrue(SettlementManagementService.overview(fixture.player(), fixture.town()).get(page).stream()
                    .map(com.jvn.villagerretaliation.network.SettlementManagementPayload.Resident.class::cast)
                    .allMatch(row -> row.moveDestination() == null), "refreshed snapshots clear completed markers");
            helper.assertValueEqual(fixture.town().duties().get(worker.getUUID()), area.id(), "movement preserves the farm assignment");
            civilian.discard();
            worker.discard();
        });
    }

    @GameTest(template = "empty", batch = "settlement_move")
    public static void movementRevalidatesTargetsAndYieldsToDanger(GameTestHelper helper) {
        var fixture = fixture(helper);
        var town = fixture.town();
        var villager = resident(helper, fixture, 0);
        var destination = fixture.base().east(8);
        try {
            helper.assertFalse(SettlementMoveService.command(fixture.player(), town, villager.getUUID(), destination.above(1000)), "world bounds are enforced");
            helper.assertFalse(SettlementMoveService.command(fixture.player(), town, villager.getUUID(), destination.east(1000)), "settlement bounds are enforced");
            villager.setBaby(true);
            helper.assertFalse(SettlementMoveService.command(fixture.player(), town, villager.getUUID(), destination), "children cannot receive work commands");
            villager.setBaby(false);
            var outsider = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "MoveGuest"));
            town.setPermissions(fixture.player().getUUID(), outsider.getUUID(), Set.of(SettlementPermission.VIEW));
            helper.assertFalse(SettlementMoveService.command(outsider, town, villager.getUUID(), destination), "view permission alone cannot move residents");
            helper.assertTrue(SettlementViewService.open(fixture.player(), town.id()), "view session opens");
            var session = SettlementViewService.session(fixture.player().getUUID());
            var stale = new SettlementViewBatchPayload.Target(villager.getUUID(), UUID.randomUUID());
            var request = new SettlementViewBatchPayload(session.id, SettlementViewBatchPayload.operation(session.id, 1),
                    SettlementViewAction.MOVE_TO, List.of(stale), null, new SettlementGuardOrder(SettlementGuardOrder.Mode.MOVE, List.of(destination)));
            helper.assertValueEqual(SettlementViewCommands.apply(session, request, stale).status(), Status.FAILED, "stale job snapshots cannot issue movement");
            var target = new SettlementViewBatchPayload.Target(villager.getUUID(), null);
            request = new SettlementViewBatchPayload(session.id, SettlementViewBatchPayload.operation(session.id, 2),
                    SettlementViewAction.MOVE_TO, List.of(target), null, request.order());
            helper.assertValueEqual(SettlementViewCommands.apply(session, request, target).status(), Status.APPLIED, "validated batch routes a civilian movement order");
            villager.igniteForSeconds(3);
            helper.assertFalse(SettlementMoveService.controls(villager), "danger immediately releases the vanilla brain");
            helper.assertTrue(SettlementMoveService.destination(villager) == null, "interrupted orders no longer advertise a target");
            var escape = new net.minecraft.world.entity.ai.memory.WalkTarget(fixture.base().west(), 1.0F, 1);
            villager.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET, escape);
            helper.assertFalse(SettlementMoveService.tick(villager), "danger cancels movement without blocking emergency behavior");
            helper.assertTrue(villager.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET)
                    .filter(targetMemory -> targetMemory == escape).isPresent(), "cancelling an order preserves emergency movement");
            villager.clearFire();
            helper.assertTrue(SettlementMoveService.command(fixture.player(), town, villager.getUUID(), destination), "another order can be issued after cancellation");
            var reassigned = new SettlementArea(UUID.randomUUID(), SettlementAreaType.FARM, "New farm",
                    fixture.base().below(), fixture.base().offset(4, 2, 4), 5, true);
            helper.assertTrue(town.putArea(fixture.player().getUUID(), reassigned), "new assignment area exists");
            helper.assertTrue(town.assignDuty(villager.getUUID(), reassigned.id()), "assignment changes during movement");
            helper.assertFalse(SettlementMoveService.tick(villager), "changed assignments cancel movement orders");
            helper.assertTrue(SettlementMoveService.destination(villager) == null, "cancelled movement clears the destination");
        } finally {
            SettlementMoveService.clear(villager);
            villager.discard();
            SettlementViewService.close(fixture.player().getUUID(), false);
        }
        helper.succeed();
    }

    private static Villager resident(GameTestHelper helper, Fixture fixture, int z) {
        var villager = EntityType.VILLAGER.create(helper.getLevel());
        villager.setPos(fixture.base().south(z).getBottomCenter());
        villager.setOnGround(true);
        VillageAllegianceApi.assignKnown(helper.getLevel(), villager, fixture.town().id(), AllegianceAssignmentSource.EXPLICIT_API);
        helper.getLevel().addFreshEntity(villager);
        return villager;
    }

    private static Fixture fixture(GameTestHelper helper) {
        var level = helper.getLevel();
        var base = helper.absolutePos(new BlockPos(4, 40, 4));
        for (var pos : BlockPos.betweenClosed(base.offset(-2, -1, -2), base.offset(15, 4, 5)))
            level.setBlock(pos, pos.getY() < base.getY() ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
        level.setDayTime(6000);
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "MoveOwner"));
        player.setPos(base.getCenter());
        player.setLastSectionPos(net.minecraft.core.SectionPos.of(player));
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(level.getGameTime(), level.dimension().location(), base, "Move town");
        var town = SettlementSavedData.get(level).establish(registry, id, player.getUUID(), base, level.getGameTime()).orElseThrow();
        SettlementTestPlots.claim(level, town, base.offset(-2, -1, -2), base.offset(15, 4, 5));
        return new Fixture(player, town, base);
    }

    private record Fixture(ServerPlayer player, SettlementRecord town, BlockPos base) { }
}
