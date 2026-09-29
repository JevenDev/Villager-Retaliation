package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.interaction.HiredRoute;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredWorkSession;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.interaction.work.HuntingWorker;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.network.SettlementManagementActionPayload;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("villagerretaliation")
@PrefixGameTestTemplate(false)
public final class SettlementWorksiteRouteGameTests {
    private SettlementWorksiteRouteGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "worksite_routes")
    public static void sharedRoutesRetainIndividualOrdersAndReturnRestoresInheritance(GameTestHelper helper) {
        var fixture = fixture(helper, SettlementAreaType.GUARD_POST, false);
        var town = fixture.town();
        var guard = fixture.worker();
        var second = worker(helper, fixture);
        try {
            var individual = new SettlementGuardOrder(SettlementGuardOrder.Mode.MOVE, List.of(town.center()));
            helper.assertTrue(SettlementGuardService.command(fixture.leader(), town, guard.getUUID(), individual), "the guard has an individual order");
            var shared = new SettlementGuardOrder(SettlementGuardOrder.Mode.PATROL, List.of(town.center(), town.center().east(8)));
            var plan = SettlementWorksitePlan.defaults(HiredVillagerRole.COMBAT).withRoute(shared);
            helper.assertTrue(SettlementWorksiteService.configure(fixture.leader(), town, fixture.area().id(), plan, true), "explicit adoption accepts the shared route");
            helper.assertValueEqual(SettlementGuardService.order(guard).orElseThrow(), individual, "adoption preserves the individual order");
            helper.assertValueEqual(SettlementGuardService.order(second).orElseThrow(), shared, "the other guard inherits the route");
            helper.assertTrue(SettlementGuardService.command(fixture.leader(), town, guard.getUUID(), null), "return clears the individual override");
            helper.assertValueEqual(HiredWorkSession.active(helper.getLevel(), guard).route().nodes(), shared.points(), "return resumes the current shared route");
            var defend = new SettlementGuardOrder(SettlementGuardOrder.Mode.DEFEND, List.of(town.center().west(3)));
            helper.assertTrue(SettlementWorksiteService.configure(fixture.leader(), town, fixture.area().id(), plan.withRoute(defend), false), "shared defense can replace patrol");
            helper.assertValueEqual(SettlementGuardService.order(guard).orElseThrow(), defend, "inherited guards follow edits immediately");
            helper.assertValueEqual(SettlementGuardService.order(second).orElseThrow(), defend, "all inherited staff share the new destination");
            helper.assertValueEqual(town.duties().get(guard.getUUID()), fixture.area().id(), "route commands retain the permanent workplace");
            var legacyPrivate = new HiredRoute(List.of(town.center().east(2), town.center().east(4)), false);
            legacyPrivate.save(HiredWorkStateStore.state(guard));
            helper.assertTrue(SettlementWorksiteService.configure(fixture.leader(), town, fixture.area().id(), plan.withRoute(null), false), "shared routes can be cleared");
            helper.assertTrue(HiredWorkSession.active(helper.getLevel(), guard).route().isEmpty(), "clearing a civic route does not expose a personal route");
            town.removeDuty(second.getUUID());
            helper.assertTrue(SettlementGuardService.order(second).isEmpty(), "departed workers no longer inherit the route");
        } finally {
            guard.discard();
            second.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "worksite_routes")
    public static void sharedRoutesValidateAuthorityTerritoryAndTheirSavedSchema(GameTestHelper helper) {
        var fixture = fixture(helper, SettlementAreaType.HUNTING_GROUNDS, false);
        try {
            var town = fixture.town();
            var shared = new SettlementGuardOrder(SettlementGuardOrder.Mode.PATROL, List.of(town.center(), town.center().east(8)));
            var plan = SettlementWorksitePlan.defaults(HiredVillagerRole.HUNTING).withRoute(shared);
            var delegate = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "RouteDelegate"));
            town.setPermissions(town.leader(), delegate.getUUID(), Set.of(SettlementPermission.VIEW));
            helper.assertFalse(SettlementWorksiteService.configure(delegate, town, fixture.area().id(), plan, true), "viewers cannot change shared orders");
            town.setPermissions(town.leader(), delegate.getUUID(), Set.of(SettlementPermission.STAFF));
            helper.assertTrue(SettlementWorksiteService.configure(delegate, town, fixture.area().id(), plan, true), "staff can adopt a hunting patrol");
            var outside = new SettlementGuardOrder(SettlementGuardOrder.Mode.PATROL, List.of(town.center(), town.center().east(town.radius() + 40)));
            helper.assertFalse(SettlementWorksiteService.configure(delegate, town, fixture.area().id(), plan.withRoute(outside), false), "route points cannot enlarge civic territory");
            var high = new BlockPos(town.center().getX(), helper.getLevel().getMaxBuildHeight(), town.center().getZ());
            helper.assertFalse(SettlementWorksiteService.configure(delegate, town, fixture.area().id(),
                    plan.withRoute(new SettlementGuardOrder(SettlementGuardOrder.Mode.PATROL, List.of(town.center(), high))), false), "routes respect world height");
            town.setPermissions(town.leader(), delegate.getUUID(), Set.of());
            helper.assertFalse(SettlementWorksiteService.configure(delegate, town, fixture.area().id(), plan.withRoute(null), false), "revoked staff cannot clear shared orders");
            var restored = SettlementRecord.load(town.save(helper.getLevel().registryAccess()), helper.getLevel().registryAccess(), () -> {});
            helper.assertValueEqual(restored.worksitePlans().get(fixture.area().id()), plan, "shared orders survive town reload");
            var legacy = plan.withRoute(null).save();
            legacy.putInt("Version", 2);
            helper.assertTrue(SettlementWorksitePlan.load(legacy).route() == null, "old plans invent no route");
            var damaged = plan.save();
            damaged.putString("Route", "invalid");
            boolean rejected = false;
            try { SettlementWorksitePlan.load(damaged); } catch (IllegalArgumentException expected) { rejected = true; }
            helper.assertTrue(rejected, "damaged route data is rejected");
            rejected = false;
            try { plan.withRoute(new SettlementGuardOrder(SettlementGuardOrder.Mode.DEFEND, List.of(town.center()))); }
            catch (IllegalArgumentException expected) { rejected = true; }
            helper.assertTrue(rejected, "hunting plans only accept patrol routes");
            var packet = new SettlementManagementActionPayload(town.id().value(),
                    new SettlementManagementActionPayload.SaveWorksite(fixture.area().id(), plan, false));
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
            try {
                SettlementManagementActionPayload.STREAM_CODEC.encode(buffer, packet);
                helper.assertValueEqual(SettlementManagementActionPayload.STREAM_CODEC.decode(buffer), packet, "wire format preserves exact route points");
                helper.assertValueEqual(buffer.readableBytes(), 0, "the route packet has no unread fields");
            } finally { buffer.release(); }
        } finally { fixture.worker().discard(); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "worksite_routes")
    public static void loadedHuntersUseTheSharedCorridorAndTemporaryWorkReturnsToIt(GameTestHelper helper) {
        var fixture = fixture(helper, SettlementAreaType.HUNTING_GROUNDS, false);
        var hunter = fixture.worker();
        var level = helper.getLevel();
        var center = fixture.town().center();
        var cow = EntityType.COW.create(level);
        cow.setPos(center.east(5).getBottomCenter());
        cow.setNoAi(true);
        level.addFreshEntity(cow);
        try {
            var route = new SettlementGuardOrder(SettlementGuardOrder.Mode.PATROL, List.of(center, center.east(8)));
            helper.assertTrue(SettlementWorksiteService.configure(fixture.leader(), fixture.town(), fixture.area().id(),
                    SettlementWorksitePlan.defaults(HiredVillagerRole.HUNTING).withRoute(route), true), "shared hunting route is adopted");
            helper.assertFalse(fixture.area().contains(cow.blockPosition()), "the prey is beyond the home hunting area");
            var session = HiredWorkSession.active(level, hunter);
            session.worker().maintain(level, hunter, session.context());
            helper.assertTrue(HuntingWorker.activeTarget(hunter).map(target -> target.getUUID().equals(cow.getUUID())).orElse(false),
                    "the native hunter can acquire prey along the inherited route");
            helper.assertFalse(SettlementGuardService.allowsTarget(hunter, center.east(fixture.town().radius() + 1)), "pursuit remains inside civic territory");
            var task = new com.jvn.villagerretaliation.interaction.work.HiredTemporaryTask(UUID.randomUUID(),
                    com.jvn.villagerretaliation.interaction.work.HiredTemporaryTask.Kind.HUNT, cow.blockPosition(), cow.blockPosition(), 1, null, cow.getUUID());
            helper.assertValueEqual(com.jvn.villagerretaliation.interaction.HiredTemporaryWorkService.submit(fixture.leader(), hunter, task, false), "", "a temporary hunt is accepted");
            helper.assertTrue(HiredWorkSession.active(level, hunter).route().isEmpty(), "temporary work suspends the route");
            com.jvn.villagerretaliation.interaction.HiredTemporaryWorkService.submit(fixture.leader(), hunter, null, false);
            helper.assertValueEqual(HiredWorkSession.active(level, hunter).route().nodes(), route.points(), "cancelling resumes the shared patrol");
        } finally {
            hunter.discard();
            cow.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "worksite_routes")
    public static void backgroundPatrolUsesPhysicalTimeAndRestoresItsCursor(GameTestHelper helper) {
        var fixture = fixture(helper, SettlementAreaType.HUNTING_GROUNDS, true);
        var town = fixture.town();
        var level = helper.getLevel();
        var hunter = fixture.worker();
        var center = town.center();
        var route = new SettlementGuardOrder(SettlementGuardOrder.Mode.PATROL, List.of(center, center.east(12)));
        helper.assertTrue(town.setWorksitePlan(town.leader(), fixture.area().id(), SettlementWorksitePlan.defaults(HiredVillagerRole.HUNTING).withRoute(route)), "the hunting route is persisted");
        SettlementExperienceTestTerrain.flat(town);
        var worker = SettlementWorkerState.capture(level, hunter, SettlementWorkforceService.duty(hunter).orElseThrow(), Math.max(0L, level.getGameTime() - 200L));
        town.recordWorker(worker);
        SettlementBackgroundHunting.tick(level, town, worker, 10, 1.0D);
        var position = worker.context(level, town).position();
        helper.assertTrue(position.x > hunter.getX() + 2.0D && position.x < hunter.getX() + 10.0D, "patrol advances with bounded native movement");
        helper.assertValueEqual(worker.status(), "patrolling_hunting_route", "the saved worker reports active patrol");
        helper.assertValueEqual(worker.settings().getInt("SettlementGuardPoint"), 1, "the first point advances to the next destination");
        SettlementBackgroundHunting.tick(level, town, worker, 200, 1.0D);
        helper.assertValueEqual(worker.context(level, town).position(), position, "the same game time cannot advance twice");
        var restored = SettlementWorkerState.load(worker.save());
        restored.restore(hunter);
        helper.assertValueEqual(hunter.position(), position, "the returning native body receives the patrol movement");
        helper.assertValueEqual(HiredWorkStateStore.state(hunter).getInt("SettlementGuardPoint"), 1, "the returning worker resumes the same route point");
        helper.assertFalse(level.hasChunkAt(center), "background patrol never loads remote terrain");
        town.removeDuty(hunter.getUUID());
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "worksite_routes")
    public static void unknownPatrolTerrainBlocksWithoutInventingMovement(GameTestHelper helper) {
        var fixture = fixture(helper, SettlementAreaType.HUNTING_GROUNDS, true);
        var town = fixture.town();
        var level = helper.getLevel();
        var hunter = fixture.worker();
        var center = town.center();
        town.setWorksitePlan(town.leader(), fixture.area().id(), SettlementWorksitePlan.defaults(HiredVillagerRole.HUNTING)
                .withRoute(new SettlementGuardOrder(SettlementGuardOrder.Mode.PATROL, List.of(center, center.east(12)))));
        var worker = SettlementWorkerState.capture(level, hunter, SettlementWorkforceService.duty(hunter).orElseThrow(), Math.max(0L, level.getGameTime() - 200L));
        town.recordWorker(worker);
        SettlementBackgroundHunting.tick(level, town, worker, 200, 1.0D);
        helper.assertValueEqual(worker.position(), hunter.blockPosition(), "unobserved ground cannot advance the worker");
        helper.assertTrue(worker.status().equals("hunting_visibility_unobserved") || worker.status().equals("hunting_path_blocked"), "unknown access has a visible blocker");
        helper.assertFalse(level.hasChunkAt(center.east(12)), "failed navigation does not load the destination");
        town.removeDuty(hunter.getUUID());
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "worksite_routes")
    public static void routeEditsRejectStaleInheritanceAndResetOnlyTheSelectedOverride(GameTestHelper helper) {
        var fixture = fixture(helper, SettlementAreaType.HUNTING_GROUNDS, false);
        var town = fixture.town();
        var worker = fixture.worker();
        var area = fixture.area();
        var first = new SettlementGuardOrder(SettlementGuardOrder.Mode.PATROL, List.of(town.center(), town.center().east(8)));
        var second = new SettlementGuardOrder(SettlementGuardOrder.Mode.PATROL, List.of(town.center(), town.center().south(8)), true,
                List.of(new HiredRoute.Branch(town.center(), town.center().west(4))));
        try {
            town.setWorksitePlan(town.leader(), area.id(), SettlementWorksitePlan.defaults(HiredVillagerRole.HUNTING).withRoute(first));
            helper.assertValueEqual(SettlementGuardService.configure(fixture.leader(), town,
                    new SettlementManagementActionPayload.SaveRoute(area.id(), worker.getUUID(), null, second)), "state_changed", "stale inherited edits are rejected");
            helper.assertValueEqual(SettlementGuardService.configure(fixture.leader(), town,
                    new SettlementManagementActionPayload.SaveRoute(area.id(), worker.getUUID(), first, second)), "", "hunter overrides are editable");
            helper.assertValueEqual(HiredWorkSession.active(helper.getLevel(), worker).route(), second.sharedRoute(), "native execution receives loops and branches");
            helper.assertValueEqual(town.worksitePlans().get(area.id()).route(), first, "individual edits preserve the worksite default");
            var saved = SettlementRecord.load(town.save(helper.getLevel().registryAccess()), helper.getLevel().registryAccess(), () -> {});
            helper.assertValueEqual(saved.guardOrders().get(worker.getUUID()), second, "individual hunter routes survive reload");
            helper.assertValueEqual(SettlementGuardService.configure(fixture.leader(), town,
                    new SettlementManagementActionPayload.SaveRoute(area.id(), worker.getUUID(), second, null)), "", "reset removes only the route override");
            helper.assertValueEqual(HiredWorkSession.active(helper.getLevel(), worker).route(), first.sharedRoute(), "reset restores inheritance");
            var cleared = new SettlementGuardOrder(SettlementGuardOrder.Mode.PATROL, List.of());
            helper.assertValueEqual(SettlementGuardService.configure(fixture.leader(), town,
                    new SettlementManagementActionPayload.SaveRoute(area.id(), worker.getUUID(), first, cleared)), "", "explicit empty routes can override an inherited route");
            helper.assertTrue(HiredWorkSession.active(helper.getLevel(), worker).route().isEmpty(), "clearing does not accidentally inherit the shared route");
            var packet = new SettlementManagementActionPayload(town.id().value(), new SettlementManagementActionPayload.SaveRoute(area.id(), worker.getUUID(), cleared, second));
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
            try {
                SettlementManagementActionPayload.STREAM_CODEC.encode(buffer, packet);
                helper.assertValueEqual(SettlementManagementActionPayload.STREAM_CODEC.decode(buffer), packet, "route edits preserve scope and expected state on the wire");
                helper.assertValueEqual(buffer.readableBytes(), 0, "all route fields are consumed");
            } finally { buffer.release(); }
        } finally { worker.discard(); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "worksite_routes")
    public static void temporaryMapOrdersPreservePersistentRoutesAcrossReloadAndReturn(GameTestHelper helper) {
        var fixture = fixture(helper, SettlementAreaType.GUARD_POST, false);
        var town = fixture.town();
        var guard = fixture.worker();
        var route = new SettlementGuardOrder(SettlementGuardOrder.Mode.PATROL, List.of(town.center(), town.center().east(8)));
        var move = new SettlementGuardOrder(SettlementGuardOrder.Mode.MOVE, List.of(town.center().south(3)));
        try {
            town.setGuardOrder(town.leader(), guard.getUUID(), route);
            helper.assertTrue(SettlementViewAction.MOVE.execute(fixture.leader(), town, guard.getUUID(), fixture.area().id(), move), "map move accepts a temporary command");
            helper.assertValueEqual(town.guardOrders().get(guard.getUUID()), route, "temporary commands preserve the persistent override");
            helper.assertValueEqual(SettlementGuardService.order(guard).orElseThrow(), move, "temporary commands take execution precedence");
            var saved = SettlementRecord.load(town.save(helper.getLevel().registryAccess()), helper.getLevel().registryAccess(), () -> {});
            helper.assertValueEqual(saved.temporaryGuardOrders().get(guard.getUUID()), move, "temporary commands survive reload independently");
            helper.assertValueEqual(saved.guardOrders().get(guard.getUUID()), route, "the persistent route also survives reload");
            helper.assertTrue(SettlementViewAction.RETURN.execute(fixture.leader(), town, guard.getUUID(), fixture.area().id(), null), "return cancels the temporary command");
            helper.assertValueEqual(SettlementGuardService.order(guard).orElseThrow(), route, "return restores the individual persistent assignment");
            town.removeDuty(guard.getUUID());
            helper.assertTrue(town.guardOrders().isEmpty() && town.temporaryGuardOrders().isEmpty(), "release removes both route scopes");
        } finally { guard.discard(); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "worksite_routes")
    public static void courierRoutesUseNativeTraversalWithoutBackgroundDepotFallback(GameTestHelper helper) {
        var fixture = fixture(helper, SettlementAreaType.DEPOT, false);
        var town = fixture.town();
        var courier = fixture.worker();
        var route = new SettlementGuardOrder(SettlementGuardOrder.Mode.PATROL, List.of(town.center(), town.center().east(8)), true,
                List.of(new HiredRoute.Branch(town.center(), town.center().south(4))));
        try {
            town.setWorksitePlan(town.leader(), fixture.area().id(), SettlementWorksitePlan.defaults(HiredVillagerRole.COURIER));
            helper.assertValueEqual(SettlementGuardService.configure(fixture.leader(), town,
                    new SettlementManagementActionPayload.SaveRoute(fixture.area().id(), null, null, route)), "", "courier worksite routes are accepted");
            helper.assertValueEqual(HiredWorkSession.active(helper.getLevel(), courier).route(), route.sharedRoute(), "couriers use the same branch traversal as personal hires");
            helper.assertFalse(SettlementLogisticsService.isHauler(courier), "explicit courier routes replace automatic depot hauling");
            var distant = new SettlementGuardOrder(SettlementGuardOrder.Mode.PATROL, List.of(town.center(), town.center().east(20)));
            helper.assertValueEqual(SettlementGuardService.configure(fixture.leader(), town,
                    new SettlementManagementActionPayload.SaveRoute(fixture.area().id(), null, route, distant)), "guard_order", "couriers retain the shared connection limit");
            var worker = SettlementWorkerState.capture(helper.getLevel(), courier, SettlementWorkforceService.duty(courier).orElseThrow(), 0L);
            SettlementBackgroundHauling.tick(helper.getLevel(), town, worker, 200, 1.0D);
            helper.assertValueEqual(worker.status(), "route_requires_loaded_worker", "unobserved routes never fall back to unrelated simulated depot transfers");
            helper.assertValueEqual(SettlementGuardService.configure(fixture.leader(), town,
                    new SettlementManagementActionPayload.SaveRoute(fixture.area().id(), null, route, null)), "", "automatic hauling can be restored");
            helper.assertTrue(SettlementLogisticsService.isHauler(courier), "legacy automatic behavior resumes when the explicit route is removed");
        } finally { courier.discard(); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "worksite_routes")
    public static void routeDraftDraggingInsertionAndCancellationPreserveRecoverableState(GameTestHelper helper) {
        var start = new BlockPos(-8, 70, -4);
        var original = new SettlementGuardOrder(SettlementGuardOrder.Mode.PATROL, List.of(start, start.east(8)));
        var draft = new com.jvn.villagerretaliation.client.settlement.SettlementRouteDraft(HiredVillagerRole.COURIER, original);
        draft.beginDrag(1);
        draft.dragTo(start.east(30));
        helper.assertFalse(draft.finishDrag(position -> true), "invalid long connections reject the drag");
        helper.assertValueEqual(draft.route(), original, "invalid drops restore the exact previous position");
        draft.beginDrag(1);
        draft.dragTo(start.east(10));
        draft.cancelDrag();
        helper.assertValueEqual(draft.route(), original, "escape restores the node without discarding the route");
        draft.select(0);
        draft.insertAfterSelection();
        draft.place(start.east(4));
        helper.assertValueEqual(draft.nodes(), List.of(start, start.east(4), start.east(8)), "insertion retains node order");
        draft.reorder(1);
        helper.assertValueEqual(draft.nodes(), List.of(start, start.east(8), start.east(4)), "reordering changes traversal order");
        draft.remove();
        helper.assertValueEqual(draft.route(), original, "removing the inserted node restores the original route");
        draft.clear();
        helper.assertTrue(draft.route().points().isEmpty(), "clear is an explicit empty draft");
        helper.assertValueEqual(original.points(), List.of(start, start.east(8)), "draft changes never mutate authoritative state");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "worksite_routes")
    public static void routeDraggingAcrossAnotherAnchorPreservesIndependentBranches(GameTestHelper helper) {
        var start = new BlockPos(0, 70, 0);
        var other = start.east(8);
        var route = new SettlementGuardOrder(SettlementGuardOrder.Mode.PATROL, List.of(start, other), false,
                List.of(new HiredRoute.Branch(start, start.south(4)), new HiredRoute.Branch(other, other.south(4))));
        var draft = new com.jvn.villagerretaliation.client.settlement.SettlementRouteDraft(HiredVillagerRole.COURIER, route);
        draft.beginDrag(0);
        draft.dragTo(other);
        helper.assertFalse(draft.finishDrag(position -> true), "dropping on another anchor is invalid");
        helper.assertValueEqual(draft.route(), route, "invalid drops restore every original branch attachment");
        draft.beginDrag(0);
        draft.dragTo(other);
        draft.dragTo(start.north());
        helper.assertTrue(draft.finishDrag(position -> true), "moving past another anchor can end on a valid point");
        helper.assertValueEqual(draft.branches().getLast(), route.branches().getLast(), "crossing an anchor never moves the other node's branch");
        draft.beginDrag(0);
        draft.dragTo(start.south());
        draft.select(1);
        helper.assertFalse(draft.dragging(), "changing the selected handle cancels its previous drag");
        helper.assertValueEqual(draft.nodes().getFirst(), start.north(), "selection changes restore the previous node location");
        var defend = new com.jvn.villagerretaliation.client.settlement.SettlementRouteDraft(HiredVillagerRole.COMBAT,
                new SettlementGuardOrder(SettlementGuardOrder.Mode.DEFEND, List.of(start)));
        defend.select(0);
        defend.branchFromSelection();
        defend.place(start.south());
        helper.assertFalse(defend.validation().isEmpty(), "defend points cannot save an unsupported branch graph");
        helper.succeed();
    }

    private static Fixture fixture(GameTestHelper helper, SettlementAreaType type, boolean remote) {
        var level = helper.getLevel();
        var origin = helper.absolutePos(new BlockPos(1, 2, 1));
        var center = remote ? new BlockPos(((18_000_000 + origin.getX() * 4) & ~15) + 2, 80,
                ((18_000_000 + origin.getZ() * 4) & ~15) + 2) : origin;
        var leader = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "RouteLeader"));
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(0L, level.dimension().location(), center, "");
        var town = SettlementSavedData.get(level).establish(registry, id, leader.getUUID(), center, 0L).orElseThrow();
        var area = new SettlementArea(UUID.randomUUID(), type, "Shared route post", center.offset(-2, -1, -2), center.offset(2, 3, 2), 5, true);
        helper.assertTrue(town.putArea(leader.getUUID(), area), "the worksite is registered");
        var fixture = new Fixture(town, area, null, leader, remote);
        return new Fixture(town, area, worker(helper, fixture), leader, remote);
    }

    private static Villager worker(GameTestHelper helper, Fixture fixture) {
        var level = helper.getLevel();
        var worker = EntityType.VILLAGER.create(level);
        worker.setPos(fixture.town().center().getBottomCenter());
        worker.setNoAi(true);
        worker.setNoGravity(true);
        worker.setVillagerData(worker.getVillagerData().setProfession(fixture.area().type() == SettlementAreaType.GUARD_POST
                ? VillagerProfession.WEAPONSMITH : VillagerProfession.FLETCHER));
        VillageAllegianceApi.assignKnown(level, worker, fixture.town().id(), AllegianceAssignmentSource.EXPLICIT_API);
        if (!fixture.remote()) level.addFreshEntity(worker);
        fixture.town().assignDuty(worker.getUUID(), fixture.area().id());
        SettlementWorkforceService.reconcile(level, worker);
        var state = HiredWorkStateStore.state(worker);
        state.putBoolean("AutoDepositOutputs", false);
        state.putBoolean("UseAssignedStorageForSupplies", false);
        state.putBoolean("HuntAnimals", true);
        state.putBoolean("HuntHostiles", false);
        state.putBoolean("HuntPlayers", false);
        HiredJobInventory.getJobInventory(worker).setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_SWORD));
        return worker;
    }

    private record Fixture(SettlementRecord town, SettlementArea area, Villager worker, ServerPlayer leader, boolean remote) {
    }
}
