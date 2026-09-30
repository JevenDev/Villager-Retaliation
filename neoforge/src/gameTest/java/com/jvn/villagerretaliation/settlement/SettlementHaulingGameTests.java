package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.block.SettlementDepotBlockEntity;
import com.jvn.villagerretaliation.block.VillagerRetaliationBlocks;
import com.jvn.villagerretaliation.interaction.HiredWorkSession;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.inventory.ContainerFilterResolver;
import com.jvn.villagerretaliation.item.VillagerFilterPolicy;
import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SettlementHaulingGameTests {
    private SettlementHaulingGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hauling")
    public static void stockTargetsRequireLogisticsAndPreserveExactItemsAcrossSaving(GameTestHelper helper) {
        withFixture(helper, fixture -> {
            ItemStack sample = new ItemStack(Items.IRON_INGOT);
            sample.set(DataComponents.CUSTOM_NAME, Component.literal("Workshop iron"));
            ServerPlayer stranger = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "DepotGuest"));
            helper.assertFalse(SettlementLogisticsService.setStockTarget(stranger, fixture.town().id(), fixture.destination().id(), sample, 16),
                    "visitors cannot configure a depot's stock targets");
            helper.assertTrue(SettlementLogisticsService.setStockTarget(fixture.leader(), fixture.town().id(), fixture.destination().id(), sample, 16),
                    "logistics authority can configure stock targets");
            sample.set(DataComponents.CUSTOM_NAME, Component.literal("Changed outside"));
            ItemStack configured = fixture.destination().stockTargets().getFirst().sample();
            helper.assertValueEqual(configured.getHoverName().getString(), "Workshop iron", "stock targets copy the caller's item");
            configured.set(DataComponents.CUSTOM_NAME, Component.literal("Changed through accessor"));
            helper.assertValueEqual(fixture.destination().stockTargets().getFirst().sample().getHoverName().getString(), "Workshop iron",
                    "stock target access cannot mutate persistent data");
            var restored = SettlementRecord.load(fixture.town().save(helper.getLevel().registryAccess()), helper.getLevel().registryAccess(), () -> {});
            helper.assertValueEqual(restored.depots().get(fixture.destination().id()).stockTargets().getFirst().count(), 16,
                    "local stock targets survive saving");
            helper.assertValueEqual(fixture.destination().stockTarget(new ItemStack(Items.IRON_INGOT)), -1,
                    "named and plain items remain distinct");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hauling")
    public static void civicCourierMovesRealCargoWithoutAPlayerAuthoredRoute(GameTestHelper helper) {
        withFixture(helper, fixture -> {
            fixture.source().setItem(0, new ItemStack(Items.IRON_INGOT, 20));
            fixture.town().setDepotStockTarget(fixture.leader().getUUID(), fixture.source().id(), new ItemStack(Items.IRON_INGOT), 13);
            fixture.town().setDepotStockTarget(fixture.leader().getUUID(), fixture.destination().id(), new ItemStack(Items.IRON_INGOT), 7);
            var session = HiredWorkSession.active(helper.getLevel(), fixture.courier());
            helper.assertFalse(session.route().usableForNavigation(), "civic hauling needs no clipboard route");
            helper.assertTrue(session.context().isInsideWorkArea(fixture.destination().position()), "hauling can reach workplaces across its own town");
            tick(helper, fixture, session);
            int carried = cargo(session);
            helper.assertTrue(carried > 0 && carried <= 7, "the courier physically picks up only the needed surplus");
            helper.assertValueEqual(fixture.source().count(stack -> stack.is(Items.IRON_INGOT)), 20 - carried, "pickup removes real source stock");
            helper.assertTrue(fixture.destination().isEmpty(), "cargo does not arrive before the courier");
            fixture.courier().setPos(fixture.destination().position().north().getCenter());
            tick(helper, fixture, session);
            helper.assertValueEqual(cargo(session), 0, "delivery empties the carried stock");
            helper.assertValueEqual(fixture.destination().count(stack -> stack.is(Items.IRON_INGOT)), carried, "delivery credits exactly what was carried");
            helper.assertValueEqual(fixture.source().count(stack -> stack.is(Items.IRON_INGOT))
                    + fixture.destination().count(stack -> stack.is(Items.IRON_INGOT)), 20, "hauling conserves the item ledger");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hauling")
    public static void competingCouriersReserveDifferentUnfilledTargets(GameTestHelper helper) {
        withFixture(helper, fixture -> {
            fixture.source().setItem(0, new ItemStack(Items.IRON_INGOT, 40));
            fixture.town().setDepotStockTarget(fixture.leader().getUUID(), fixture.destination().id(), new ItemStack(Items.IRON_INGOT), 7);
            SettlementDepot secondDestination = depot(helper, fixture, new BlockPos(5, 2, 5), SettlementDepotPurpose.MATERIALS, 4);
            fixture.town().setDepotStockTarget(fixture.leader().getUUID(), secondDestination.id(), new ItemStack(Items.IRON_INGOT), 7);
            SettlementTestPlots.planSupplies(helper.getLevel(), fixture.town());
            var first = SettlementLogisticsService.prepare(helper.getLevel(), fixture.courier(), fixture.town());
            helper.assertValueEqual(first.destination(), fixture.destination().id(), "the higher priority destination is served first");
            helper.assertValueEqual(AssignedStorageService.reservedItemCount(helper.getLevel(), fixture.destination().position(), null,
                    VillagerFilterPolicy.TransferOperation.RECEIVE, new ItemStack(Items.IRON_INGOT)), 7, "destination capacity is reserved");
            Villager second = courier(helper, fixture.town(), fixture.area(), fixture.leader());
            try {
                SettlementTestPlots.planSupplies(helper.getLevel(), fixture.town());
                var secondTask = SettlementLogisticsService.prepare(helper.getLevel(), second, fixture.town());
                helper.assertValueEqual(secondTask.destination(), secondDestination.id(), "another courier serves the next unmet target");
                helper.assertValueEqual(AssignedStorageService.reservedItemCount(helper.getLevel(), fixture.source().position(), null,
                        VillagerFilterPolicy.TransferOperation.PROVIDE, new ItemStack(Items.IRON_INGOT)), 14, "source reservations are counted once per courier");
            } finally {
                AssignedStorageService.releaseCourierClaims(second);
                second.discard();
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hauling")
    public static void fullDestinationsAndSourceReservesPreventPickup(GameTestHelper helper) {
        withFixture(helper, fixture -> {
            fixture.source().setItem(0, new ItemStack(Items.IRON_INGOT, 10));
            fixture.town().setDepotStockTarget(fixture.leader().getUUID(), fixture.destination().id(), new ItemStack(Items.IRON_INGOT), 10);
            fixture.town().setDepotStockTarget(fixture.leader().getUUID(), fixture.source().id(), new ItemStack(Items.IRON_INGOT), 10);
            helper.assertTrue(SettlementLogisticsService.prepare(helper.getLevel(), fixture.courier(), fixture.town()) == null,
                    "a source's own required stock is protected");
            fixture.town().setDepotStockTarget(fixture.leader().getUUID(), fixture.source().id(), new ItemStack(Items.IRON_INGOT), 0);
            for (int slot = 0; slot < fixture.destination().getContainerSize(); slot++) {
                fixture.destination().setItem(slot, new ItemStack(Items.STONE, 64));
            }
            helper.assertTrue(SettlementLogisticsService.prepare(helper.getLevel(), fixture.courier(), fixture.town()) == null,
                    "full destinations apply backpressure before pickup");
            helper.assertValueEqual(fixture.source().getItem(0).getCount(), 10, "failed plans do not consume supplies");
            helper.assertFalse(HiredJobInventory.getJobInventory(fixture.courier()).hasOutputItems(), "failed plans create no cargo");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hauling")
    public static void foodDepotsRequestRealMealsAndSurplusOutputsReachGeneralStorage(GameTestHelper helper) {
        withFixture(helper, fixture -> {
            fixture.town().configureDepot(fixture.leader().getUUID(), fixture.destination().id(), SettlementDepotPurpose.FOOD, 9);
            fixture.source().setItem(0, new ItemStack(Items.BREAD, 20));
            var task = SettlementLogisticsService.prepare(helper.getLevel(), fixture.courier(), fixture.town());
            helper.assertValueEqual(task.target(), 3, "a food depot buffers enough meal nutrition for the resident");
            helper.assertFalse(task.surplus(), "resident food is handled as a local need");
            SettlementLogisticsService.clear(fixture.courier());
            fixture.destination().setItem(0, new ItemStack(Items.BREAD, 3));
            fixture.source().setItem(0, new ItemStack(Items.COBBLESTONE, 20));
            SettlementDepot general = depot(helper, fixture, new BlockPos(5, 2, 5), SettlementDepotPurpose.GENERAL, 4);
            task = SettlementLogisticsService.prepare(helper.getLevel(), fixture.courier(), fixture.town());
            helper.assertValueEqual(task.destination(), general.id(), "surplus workplace output can clear into general storage");
            helper.assertTrue(task.surplus(), "surplus hauling is separate from a specific stock request");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hauling")
    public static void cargoSurvivesSavingAndIsReroutedWhenItsDestinationBreaks(GameTestHelper helper) {
        withFixture(helper, fixture -> {
            ItemStack iron = new ItemStack(Items.IRON_INGOT, 20);
            iron.set(DataComponents.CUSTOM_NAME, Component.literal("Foundry batch"));
            fixture.source().setItem(0, iron);
            fixture.town().setDepotStockTarget(fixture.leader().getUUID(), fixture.destination().id(), iron, 7);
            var session = HiredWorkSession.active(helper.getLevel(), fixture.courier());
            tick(helper, fixture, session);
            int carried = cargo(session);
            helper.assertTrue(carried > 0, "cargo is in transit before the workplace changes");
            CompoundTag saved = new CompoundTag();
            fixture.courier().saveWithoutId(saved);
            Villager restored = EntityType.VILLAGER.create(helper.getLevel());
            restored.load(saved);
            helper.assertValueEqual(HiredJobInventory.getJobInventory(restored).collectOutputItems().stream()
                    .mapToInt(output -> output.stack().getCount()).sum(), carried, "the real worker inventory persists in transit");
            helper.assertTrue(SettlementLogisticsService.task(restored).matches(iron), "the hauling task retains item components");
            helper.getLevel().setBlockAndUpdate(fixture.destination().position(), Blocks.AIR.defaultBlockState());
            SettlementDepot fallback = depot(helper, fixture, new BlockPos(5, 2, 5), SettlementDepotPurpose.GENERAL, 4);
            fixture.courier().setPos(fallback.position().north().getCenter());
            tick(helper, fixture, session);
            helper.assertValueEqual(cargo(session), 0, "stranded cargo is delivered to available general storage");
            helper.assertValueEqual(fallback.getItem(0).getHoverName().getString(), "Foundry batch", "rerouting preserves item components");
            helper.assertValueEqual(fallback.getItem(0).getCount() + fixture.source().getItem(0).getCount(), 20,
                    "breaking a destination neither duplicates nor loses the shipment");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hauling")
    public static void releasingACourierClearsTownReservationsWithoutDestroyingCargo(GameTestHelper helper) {
        withFixture(helper, fixture -> {
            fixture.source().setItem(0, new ItemStack(Items.IRON_INGOT, 20));
            fixture.town().setDepotStockTarget(fixture.leader().getUUID(), fixture.destination().id(), new ItemStack(Items.IRON_INGOT), 7);
            var session = HiredWorkSession.active(helper.getLevel(), fixture.courier());
            tick(helper, fixture, session);
            int carried = cargo(session);
            helper.assertTrue(SettlementWorkforceService.release(helper.getLevel(), fixture.leader(), fixture.town().id(), fixture.courier().getUUID()),
                    "the leader can release the civic courier");
            helper.assertTrue(SettlementLogisticsService.task(fixture.courier()) == null, "release clears the town hauling task");
            helper.assertValueEqual(cargo(session), carried, "release preserves real carried items");
            helper.assertValueEqual(AssignedStorageService.reservedItemCount(helper.getLevel(), fixture.destination().position(), null,
                    VillagerFilterPolicy.TransferOperation.RECEIVE, new ItemStack(Items.IRON_INGOT)), 0, "release removes stale destination reservations");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hauling")
    public static void deniedFramedStorageDoesNotBlockOtherHaulingRequests(GameTestHelper helper) {
        withFixture(helper, fixture -> {
            fixture.source().setItem(0, new ItemStack(Items.IRON_INGOT, 20));
            fixture.town().setDepotStockTarget(fixture.leader().getUUID(), fixture.destination().id(), new ItemStack(Items.IRON_INGOT), 7);
            SettlementDepot alternate = depot(helper, fixture, new BlockPos(5, 2, 5), SettlementDepotPurpose.OUTPUT, 4);
            alternate.setItem(0, new ItemStack(Items.IRON_INGOT, 20));
            ItemFrame sourceRule = attachRule(helper, fixture.source().position());
            try {
                var task = SettlementLogisticsService.prepare(helper.getLevel(), fixture.courier(), fixture.town());
                helper.assertValueEqual(task.source(), alternate.id(), "the planner skips a source whose framed rule denies the item");
                SettlementLogisticsService.clear(fixture.courier());
            } finally {
                sourceRule.discard();
                ContainerFilterResolver.invalidateContainer(helper.getLevel(), fixture.source().position());
            }
            alternate.clearContent();
            fixture.town().configureDepot(fixture.leader().getUUID(), alternate.id(), SettlementDepotPurpose.MATERIALS, 4);
            fixture.town().setDepotStockTarget(fixture.leader().getUUID(), alternate.id(), new ItemStack(Items.IRON_INGOT), 7);
            ItemFrame destinationRule = attachRule(helper, fixture.destination().position());
            try {
                var task = SettlementLogisticsService.prepare(helper.getLevel(), fixture.courier(), fixture.town());
                helper.assertValueEqual(task.destination(), alternate.id(), "the planner skips a destination whose framed rule denies the item");
            } finally {
                destinationRule.discard();
                ContainerFilterResolver.invalidateContainer(helper.getLevel(), fixture.destination().position());
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hauling")
    public static void priorityEditsReleaseUnstartedHaulingClaimsAndPreserveCargo(GameTestHelper helper) {
        withFixture(helper, fixture -> {
            var level = helper.getLevel();
            var town = fixture.town();
            var sample = new ItemStack(Items.IRON_INGOT);
            fixture.source().setItem(0, sample.copyWithCount(20));
            town.setDepotStockTarget(town.leader(), fixture.destination().id(), sample, 7);
            SettlementTestPlots.planSupplies(level, town);
            helper.assertTrue(SettlementLogisticsService.prepare(level, fixture.courier(), town) != null, "the courier plans a real shipment");
            helper.assertValueEqual(AssignedStorageService.reservedItemCount(level, fixture.source().position(), null,
                    VillagerFilterPolicy.TransferOperation.PROVIDE, sample), 7, "the pending shipment reserves its actual source");
            town.configureDepot(town.leader(), fixture.destination().id(), fixture.destination().purpose(), 0);
            SettlementTestPlots.planSupplies(level, town);
            helper.assertValueEqual(AssignedStorageService.reservedItemCount(level, fixture.source().position(), null,
                    VillagerFilterPolicy.TransferOperation.PROVIDE, sample), 0, "priority changes release an unstarted hauling claim");
            helper.assertTrue(SettlementLogisticsService.task(fixture.courier()) == null, "the unstarted route is reconsidered");
            var session = HiredWorkSession.active(level, fixture.courier());
            tick(helper, fixture, session);
            int carried = cargo(session);
            helper.assertTrue(carried > 0, "the reconsidered shipment physically starts");
            town.configureDepot(town.leader(), fixture.destination().id(), fixture.destination().purpose(), 9);
            SettlementTestPlots.planSupplies(level, town);
            helper.assertValueEqual(cargo(session), carried, "later priority edits retain the courier's cargo");
            helper.assertTrue(SettlementLogisticsService.task(fixture.courier()) != null, "started cargo retains its delivery destination");
        });
    }

    private static ItemFrame attachRule(GameTestHelper helper, BlockPos container) {
        ItemFrame frame = new ItemFrame(helper.getLevel(), container.south(), Direction.SOUTH);
        frame.setItem(new ItemStack(Items.BREAD));
        helper.assertTrue(helper.getLevel().addFreshEntity(frame), "the storage rule is present");
        ContainerFilterResolver.invalidateFrame(helper.getLevel(), frame);
        return frame;
    }

    private static void tick(GameTestHelper helper, Fixture fixture, HiredWorkSession session) {
        SettlementTestPlots.planSupplies(helper.getLevel(), fixture.town());
        session.worker().tick(helper.getLevel(), fixture.courier(), null, session.context());
    }

    private static int cargo(HiredWorkSession session) {
        return session.inventory().collectOutputItems().stream().mapToInt(output -> output.stack().getCount()).sum();
    }

    private static SettlementDepot depot(GameTestHelper helper, Fixture fixture, BlockPos relative,
                                          SettlementDepotPurpose purpose, int priority) {
        BlockPos position = helper.absolutePos(relative);
        helper.getLevel().setBlockAndUpdate(position, VillagerRetaliationBlocks.SETTLEMENT_DEPOT.get().defaultBlockState());
        helper.assertTrue(SettlementDepotService.register(fixture.leader(), position), "the physical depot registers");
        SettlementDepot depot = ((SettlementDepotBlockEntity) helper.getLevel().getBlockEntity(position)).inventory();
        fixture.town().configureDepot(fixture.leader().getUUID(), depot.id(), purpose, priority);
        return depot;
    }

    static Villager courier(GameTestHelper helper, SettlementRecord town, SettlementArea area, ServerPlayer leader) {
        Villager courier = EntityType.VILLAGER.create(helper.getLevel());
        courier.setPos(helper.absolutePos(new BlockPos(2, 2, 1)).getCenter());
        courier.setNoAi(true);
        courier.setVillagerData(courier.getVillagerData().setProfession(VillagerProfession.FARMER));
        helper.getLevel().addFreshEntity(courier);
        VillageAllegianceApi.assignKnown(helper.getLevel(), courier, town.id(), AllegianceAssignmentSource.EXPLICIT_API);
        helper.assertTrue(SettlementWorkforceService.assign(helper.getLevel(), leader, town.id(), courier, area.id()), "the resident becomes a civic hauler");
        return courier;
    }

    static void withFixture(GameTestHelper helper, Consumer<Fixture> check) {
        var level = helper.getLevel();
        long dayTime = level.getDayTime();
        BlockPos center = helper.absolutePos(new BlockPos(1, 2, 1));
        ServerPlayer leader = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "HaulSteward"));
        leader.setPos(helper.absolutePos(new BlockPos(3, 2, 3)).getCenter());
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(level.getGameTime(), level.dimension().location(), center, "");
        var town = SettlementSavedData.get(level).establish(registry, id, leader.getUUID(), center, level.getGameTime()).orElseThrow();
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.DEPOT, "Distribution",
                helper.absolutePos(new BlockPos(0, 2, 0)), helper.absolutePos(new BlockPos(3, 5, 6)), 5, true);
        var workplace = new SettlementArea(UUID.randomUUID(), SettlementAreaType.WORKSHOP, "Workshop",
                helper.absolutePos(new BlockPos(4, 2, 0)), helper.absolutePos(new BlockPos(6, 5, 6)), 5, true);
        town.putArea(leader.getUUID(), area);
        town.putArea(leader.getUUID(), workplace);
        for (BlockPos position : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, 1, 0)), helper.absolutePos(new BlockPos(6, 1, 6)))) {
            level.setBlockAndUpdate(position, Blocks.STONE.defaultBlockState());
        }
        Villager courier = courier(helper, town, area, leader);
        Fixture seed = new Fixture(town, area, null, null, leader, courier);
        SettlementDepot source = depot(helper, seed, new BlockPos(2, 2, 2), SettlementDepotPurpose.OUTPUT, 5);
        SettlementDepot destination = depot(helper, seed, new BlockPos(5, 2, 2), SettlementDepotPurpose.MATERIALS, 9);
        Fixture fixture = new Fixture(town, area, source, destination, leader, courier);
        try {
            level.setDayTime(6_000L);
            check.accept(fixture);
        } finally {
            AssignedStorageService.releaseCourierClaims(courier);
            courier.discard();
            level.setDayTime(dayTime);
        }
        helper.succeed();
    }

    record Fixture(SettlementRecord town, SettlementArea area, SettlementDepot source, SettlementDepot destination,
                           ServerPlayer leader, Villager courier) {
    }
}
