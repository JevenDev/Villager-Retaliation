package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.interaction.HiredCombatMode;
import com.jvn.villagerretaliation.interaction.HiredRoute;
import com.jvn.villagerretaliation.interaction.HiredWorkSession;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerTaskState;
import com.jvn.villagerretaliation.network.SettlementManagementActionPayload;
import com.jvn.villagerretaliation.network.SettlementManagementPayload;
import com.jvn.villagerretaliation.network.SettlementManagementRequestPayload;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SettlementGuardGameTests {
    private SettlementGuardGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_guards")
    public static void guardOrdersRequireStaffAndStayInsideTheirSettlement(GameTestHelper helper) {
        var fixture = fixture(helper);
        var delegate = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "GuardStaff"));
        var move = new SettlementGuardOrder(SettlementGuardOrder.Mode.MOVE, List.of(fixture.town().center().east(8)));
        try {
            fixture.town().setPermissions(fixture.leader().getUUID(), delegate.getUUID(), Set.of(SettlementPermission.LOGISTICS));
            helper.assertFalse(command(delegate, fixture, move), "logistics access cannot issue guard orders");
            fixture.town().setPermissions(fixture.leader().getUUID(), delegate.getUUID(), Set.of(SettlementPermission.STAFF));
            helper.assertTrue(command(delegate, fixture, move), "staff can order the settlement's civic guard");
            long revision = fixture.town().revision();
            helper.assertFalse(command(delegate, fixture, new SettlementGuardOrder(SettlementGuardOrder.Mode.MOVE,
                    List.of(fixture.town().center().east(fixture.town().stage().radius() + 1)))), "the camera border does not enlarge guard territory");
            helper.assertFalse(command(delegate, fixture, new SettlementGuardOrder(SettlementGuardOrder.Mode.MOVE,
                    List.of(new BlockPos(fixture.town().center().getX(), helper.getLevel().getMaxBuildHeight(), fixture.town().center().getZ())))),
                    "destinations cannot leave the world's build height");
            helper.assertFalse(SettlementGuardService.command(delegate, fixture.town(), UUID.randomUUID(), move),
                    "a foreign resident cannot be commanded");
            helper.assertValueEqual(fixture.town().revision(), revision, "denied orders preserve the accepted order");
            fixture.town().setPermissions(fixture.leader().getUUID(), delegate.getUUID(), Set.of());
            helper.assertFalse(command(delegate, fixture, null), "revoked staff cannot clear an order");
        } finally {
            fixture.guard().discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_guards")
    public static void guardOrdersSurviveSaveAndClearWithTheirDuty(GameTestHelper helper) {
        var fixture = fixture(helper);
        try {
            var patrol = new SettlementGuardOrder(SettlementGuardOrder.Mode.PATROL,
                    List.of(fixture.town().center(), fixture.town().center().east(8), fixture.town().center().south(8)));
            helper.assertTrue(command(fixture.leader(), fixture, patrol), "patrol is accepted");
            var saved = fixture.town().save(helper.getLevel().registryAccess());
            var restored = SettlementRecord.load(saved, helper.getLevel().registryAccess(), () -> {});
            helper.assertValueEqual(restored.guardOrders().get(fixture.guard().getUUID()), patrol, "save retains the ordered patrol points");
            var legacy = saved.copy();
            legacy.remove("GuardOrders");
            helper.assertTrue(SettlementRecord.load(legacy, helper.getLevel().registryAccess(), () -> {}).guardOrders().isEmpty(),
                    "existing saves load with guards at their posts");
            helper.assertTrue(fixture.town().removeArea(fixture.leader().getUUID(), fixture.area().id()), "the guard post can be removed");
            helper.assertTrue(fixture.town().guardOrders().isEmpty(), "removing the duty also removes its remote order");
        } finally {
            fixture.guard().discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_guards")
    public static void moveHoldsAndPatrolAdvancesThroughTheExistingWorker(GameTestHelper helper) {
        var fixture = fixture(helper);
        try {
            var guard = fixture.guard();
            var center = fixture.town().center();
            var legacyRoute = new HiredRoute(List.of(center.east(100)), false);
            legacyRoute.save(HiredWorkStateStore.state(guard));
            helper.assertTrue(HiredWorkSession.active(helper.getLevel(), guard).route().isEmpty(),
                    "civic guards do not inherit private routes");
            helper.assertTrue(command(fixture.leader(), fixture,
                    new SettlementGuardOrder(SettlementGuardOrder.Mode.MOVE, List.of(center))), "move is accepted");
            var session = HiredWorkSession.active(helper.getLevel(), guard);
            session.worker().maintain(helper.getLevel(), guard, session.context());
            helper.assertValueEqual(session.state().getString("WorkerTaskState"), HiredWorkerTaskState.AWAITING_INSTRUCTION.id(),
                    "a guard at the move destination holds position");
            helper.assertValueEqual(SettlementGuardService.mode(guard, HiredCombatMode.ROAMING), HiredCombatMode.GUARD,
                    "move does not proactively hunt targets");
            var next = center.east(8);
            helper.assertTrue(command(fixture.leader(), fixture,
                    new SettlementGuardOrder(SettlementGuardOrder.Mode.PATROL, List.of(center, next))), "patrol is accepted");
            session = HiredWorkSession.active(helper.getLevel(), guard);
            session.worker().maintain(helper.getLevel(), guard, session.context());
            helper.assertValueEqual(session.state().getInt("SettlementGuardPoint"), 1, "arrival advances to the next patrol point");
            helper.assertValueEqual(session.context().route().nodes(), List.of(center, next), "native worker geometry follows the civic route");
            helper.assertValueEqual(SettlementGuardService.mode(guard, HiredCombatMode.GUARD), HiredCombatMode.ROAMING,
                    "patrol uses the existing hostile-only combat policy");
            helper.assertFalse(SettlementGuardService.allowsTarget(guard, center.east(fixture.town().stage().radius() + 1)),
                    "guards cannot acquire targets beyond the settlement");
            helper.assertTrue(command(fixture.leader(), fixture, null), "return to post clears the remote order");
            helper.assertTrue(HiredWorkSession.active(helper.getLevel(), guard).route().isEmpty(), "the old private route remains inactive");
            helper.assertTrue(SettlementWorkforceService.release(helper.getLevel(), fixture.leader(), fixture.town().id(), guard.getUUID()),
                    "the leader can release the guard");
            helper.assertTrue(fixture.town().guardOrders().isEmpty(), "release retains no guard order");
        } finally {
            fixture.guard().discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_guards")
    public static void distantGuardDestinationsUseShortLoadedNavigationSteps(GameTestHelper helper) {
        var fixture = fixture(helper);
        try {
            var destination = fixture.town().center().east(60);
            var step = SettlementGuardService.nextStep(helper.getLevel(), fixture.guard(), destination);
            helper.assertTrue(step != null && step.getX() > fixture.guard().getX(), "the guard makes a forward step toward a distant order");
            helper.assertTrue(step.distSqr(fixture.guard().blockPosition()) < 400.0D, "a city-sized order does not request one enormous path");
            helper.assertFalse(step.equals(destination), "the final destination remains separate from the intermediate step");
        } finally {
            fixture.guard().discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_guards")
    public static void guardOrderPacketsAndResidentDetailsRetainTheSameOrder(GameTestHelper helper) {
        var fixture = fixture(helper);
        try {
            var order = new SettlementGuardOrder(SettlementGuardOrder.Mode.DEFEND, List.of(fixture.town().center()));
            var action = new SettlementManagementActionPayload(fixture.town().id().value(),
                    new SettlementManagementActionPayload.Guard(fixture.guard().getUUID(), order));
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
            try {
                SettlementManagementActionPayload.STREAM_CODEC.encode(buffer, action);
                helper.assertValueEqual(SettlementManagementActionPayload.STREAM_CODEC.decode(buffer), action, "the command retains its destination");
                helper.assertTrue(SettlementManagementService.execute(fixture.leader(), action), "the decoded command uses normal management authority");
                var snapshot = SettlementManagementService.snapshot(fixture.leader(), new SettlementManagementRequestPayload(
                        fixture.town().id().value(), SettlementManagementRequestPayload.Page.RESIDENTS, 0, fixture.guard().getUUID().toString(), 1, false));
                SettlementManagementPayload.STREAM_CODEC.encode(buffer, snapshot);
                var decoded = SettlementManagementPayload.STREAM_CODEC.decode(buffer);
                helper.assertValueEqual(((SettlementManagementPayload.Resident) decoded.entries().getFirst()).guardOrder(), order,
                        "the selected resident shows the persisted order");
                helper.assertValueEqual(buffer.readableBytes(), 0, "all guard fields are consumed");
            } finally {
                buffer.release();
            }
            boolean invalid = false;
            try {
                new SettlementGuardOrder(SettlementGuardOrder.Mode.PATROL, List.of(fixture.town().center()));
            } catch (IllegalArgumentException expected) {
                invalid = true;
            }
            helper.assertTrue(invalid, "patrol requires at least two distinct points");
        } finally {
            fixture.guard().discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_guards")
    public static void civicMoveUsesARealPathBeyondTheGuardPost(GameTestHelper helper) {
        var fixture = fixture(helper);
        var level = helper.getLevel();
        var floor = new java.util.LinkedHashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
        try {
            var center = fixture.town().center();
            for (BlockPos raw : BlockPos.betweenClosed(center.offset(-1, -1, -1), center.offset(10, -1, 1))) {
                BlockPos position = raw.immutable();
                floor.put(position, level.getBlockState(position));
                level.setBlockAndUpdate(position, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            }
            var destination = center.east(8);
            helper.assertTrue(command(fixture.leader(), fixture,
                    new SettlementGuardOrder(SettlementGuardOrder.Mode.MOVE, List.of(destination))), "the guard receives the move order");
            helper.assertTrue(SettlementWorkforceService.onDuty(level, fixture.guard()), "the guard is assigned to the current shift");
            com.jvn.villagerretaliation.interaction.HiredVillagerWorkService.onVillagerTickPost(fixture.guard());
            var navigation = fixture.guard().getNavigation();
            helper.assertTrue(!navigation.isDone() && navigation.getTargetPos() != null
                    && navigation.getTargetPos().distSqr(destination) <= 4.0D, "civic work creates a real path to the ordered destination");
            helper.assertFalse(fixture.area().contains(destination), "the order can leave the guard post without changing its area");
        } finally {
            floor.forEach(level::setBlockAndUpdate);
            fixture.guard().discard();
        }
        helper.succeed();
    }

    private static boolean command(ServerPlayer player, Fixture fixture, SettlementGuardOrder order) {
        return SettlementManagementService.execute(player, new SettlementManagementActionPayload(fixture.town().id().value(),
                new SettlementManagementActionPayload.Guard(fixture.guard().getUUID(), order)));
    }

    private static Fixture fixture(GameTestHelper helper) {
        var level = helper.getLevel();
        var center = helper.absolutePos(new BlockPos(1, 2, 1));
        level.getChunkAt(center.east(12));
        var leader = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "GuardLeader"));
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(level.getGameTime(), level.dimension().location(), center, "");
        var town = SettlementSavedData.get(level).establish(registry, id, leader.getUUID(), center, level.getGameTime()).orElseThrow();
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.GUARD_POST, "Guard post",
                center.offset(-2, -1, -2), center.offset(2, 2, 2), 5, true);
        helper.assertTrue(town.putArea(leader.getUUID(), area), "the guard post is registered");
        var guard = EntityType.VILLAGER.create(level);
        long shift = SettlementWorkforceService.workingHours(level.getDayTime(), new UUID(0L, 0L),
                com.jvn.villagerretaliation.interaction.HiredVillagerRole.COMBAT) ? 0L : 1L;
        guard.setUUID(new UUID(UUID.randomUUID().getMostSignificantBits(), UUID.randomUUID().getLeastSignificantBits() & ~1L | shift));
        guard.setPos(net.minecraft.world.phys.Vec3.atBottomCenterOf(center));
        guard.setNoAi(true);
        guard.setOnGround(true);
        guard.setVillagerData(guard.getVillagerData().setProfession(VillagerProfession.WEAPONSMITH));
        VillageAllegianceApi.assignKnown(level, guard, id, AllegianceAssignmentSource.EXPLICIT_API);
        helper.assertTrue(level.addFreshEntity(guard), "the guard is loaded");
        helper.assertTrue(SettlementWorkforceService.assign(level, leader, id, guard, area.id()), "the resident receives civic guard duty");
        return new Fixture(town, area, guard, leader);
    }

    private record Fixture(SettlementRecord town, SettlementArea area, Villager guard, ServerPlayer leader) {
    }
}
