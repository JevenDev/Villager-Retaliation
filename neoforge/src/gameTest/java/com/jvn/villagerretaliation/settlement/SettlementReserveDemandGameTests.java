package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.interaction.work.courier.HiredTransferQueue;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionRecipe;
import com.jvn.villagerretaliation.interaction.work.production.WorkSupplyDemand;
import com.jvn.villagerretaliation.interaction.work.production.WorkSupplyRequirement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SettlementReserveDemandGameTests {
    private SettlementReserveDemandGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_reserve_demand")
    public static void semanticRequirementsIgnoreOrderingAndQuantityButRetainComponents(GameTestHelper helper) {
        var first = new ItemStack(Items.OAK_PLANKS);
        var second = new ItemStack(Items.BIRCH_PLANKS);
        var requirement = new WorkSupplyRequirement("ingredient", List.of(first, second), 4,
                WorkSupplyRequirement.Unit.ITEMS, stack -> stack.is(Items.OAK_PLANKS) || stack.is(Items.BIRCH_PLANKS), null);
        var reordered = new WorkSupplyRequirement("ingredient", List.of(second, first), 20,
                WorkSupplyRequirement.Unit.ITEMS, requirement.accepts(), null);
        var consumer = UUID.randomUUID();
        var registry = helper.getLevel().registryAccess();
        var id = WorkSupplyDemand.identity(registry, consumer, WorkSupplyDemand.Kind.PRODUCTION, requirement);
        helper.assertValueEqual(WorkSupplyDemand.identity(registry, consumer, WorkSupplyDemand.Kind.PRODUCTION, reordered), id,
                "alternative order and changing demand quantity preserve the semantic identity");
        helper.assertTrue(requirement.sameGroup(reordered), "equivalent alternative groups can merge in either order");
        var named = first.copy();
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Reserved planks"));
        var exact = WorkSupplyRequirement.exact("material", named, 1);
        helper.assertValueEqual(exact.value(first), 0L, "component-sensitive requirements reject ordinary versions");
        helper.assertFalse(WorkSupplyDemand.identity(registry, consumer, WorkSupplyDemand.Kind.PRODUCTION, exact).equals(
                WorkSupplyDemand.identity(registry, consumer, WorkSupplyDemand.Kind.PRODUCTION, WorkSupplyRequirement.exact("material", first, 1))),
                "different components cannot share a policy or selection identity");
        var recipe = HiredProductionRecipe.exact(ResourceLocation.withDefaultNamespace("stick"), Map.of());
        var empty = com.jvn.villagerretaliation.interaction.work.production.WorkProductionDemand.describe(helper.getLevel(), recipe, 4, List.of());
        var partial = com.jvn.villagerretaliation.interaction.work.production.WorkProductionDemand.describe(helper.getLevel(), recipe, 4, List.of(first));
        var stocked = com.jvn.villagerretaliation.interaction.work.production.WorkProductionDemand.describe(helper.getLevel(), recipe, 4, List.of(first.copyWithCount(2)));
        helper.assertValueEqual(partial.requirements().size(), 1, "available and missing alternatives retain one semantic input group");
        helper.assertValueEqual(WorkSupplyDemand.identity(registry, consumer, WorkSupplyDemand.Kind.PRODUCTION, empty.requirements().getFirst()),
                WorkSupplyDemand.identity(registry, consumer, WorkSupplyDemand.Kind.PRODUCTION, partial.requirements().getFirst()),
                "partial resupply does not replace a selected requirement");
        helper.assertValueEqual(WorkSupplyDemand.identity(registry, consumer, WorkSupplyDemand.Kind.PRODUCTION, empty.requirements().getFirst()),
                WorkSupplyDemand.identity(registry, consumer, WorkSupplyDemand.Kind.PRODUCTION, stocked.requirements().getFirst()),
                "a fully supplied recipe keeps its flexible requirement identity");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_reserve_demand")
    public static void nutritionHasOneReserveAndResidentsCanEatItsProtectedFood(GameTestHelper helper) {
        var fixture = fixture(helper, 2);
        var town = fixture.town();
        fixture.source().setItem(0, new ItemStack(Items.BREAD));
        fixture.source().setItem(1, new ItemStack(Items.APPLE));
        fixture.source().setItem(2, new ItemStack(Items.ROTTEN_FLESH, 32));
        var before = food(helper, town);
        long target = SettlementFoodService.reserveNutrition(helper.getLevel(), town);
        helper.assertValueEqual(before.supply().availableNow(), 9L, "eligible meals contribute nutrition rather than item count");
        helper.assertValueEqual(before.requirement().quantity(), target, "the population shares one reserve-days target");
        helper.assertValueEqual(before.supply().protectedMinimum(), target, "the existing reserve-days setting initializes the protected minimum");
        helper.assertValueEqual(before.requirement().value(new ItemStack(Items.GOLDEN_APPLE)), 0L, "valuable special food is not a routine meal");
        helper.assertValueEqual(SettlementDemandService.inspect(helper.getLevel(), town).sellable(fixture.source(), new ItemStack(Items.BREAD)), 0,
                "the protected meal is excluded from surplus sales");
        var resident = EntityType.VILLAGER.create(helper.getLevel());
        resident.setPos(town.center().getCenter());
        VillageAllegianceApi.assignKnown(helper.getLevel(), resident, town.id(), AllegianceAssignmentSource.EXPLICIT_API);
        SettlementTestPlots.planSupplies(helper.getLevel(), town);
        helper.assertTrue(SettlementFoodService.takeMeal(resident).is(Items.BREAD), "the intended resident can consume protected nutrition");
        var after = food(helper, town);
        helper.assertValueEqual(after.supply().availableNow(), 4L, "the consumed meal leaves actual stock exactly once");
        helper.assertValueEqual(after.supply().id(), before.supply().id(), "population and stock changes preserve the shared food identity");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_reserve_demand")
    public static void internalMealDeliveriesDoNotIncreaseTownNutrition(GameTestHelper helper) {
        var fixture = fixture(helper, 4);
        fixture.source().setItem(0, new ItemStack(Items.BREAD, 5));
        var transfers = fixture.town().beginTransfers(fixture.town().leader(), fixture.storage().id());
        var order = HiredTransferQueue.Order.create(
                new HiredTransferQueue.Endpoint(fixture.source().position(), fixture.source().id()),
                new HiredTransferQueue.Endpoint(fixture.destination().position(), fixture.destination().id()), new ItemStack(Items.BREAD), 4);
        transfers.append(order);
        var allocation = transfers.reserve(UUID.randomUUID(), order.id(), 4);
        helper.assertTrue(allocation != null, "a real internal delivery has an allocation");
        var reserved = food(helper, fixture.town());
        helper.assertValueEqual(reserved.supply().availableNow() + reserved.supply().incoming(), 25L,
                "reserved pickup is removed from available stock before its incoming destination is counted");
        helper.assertValueEqual(reserved.supply().availableNow(), 5L, "reserved cargo is not available now");
        helper.assertValueEqual(reserved.supply().incoming(), 20L, "the same cargo is shown separately as incoming nutrition");
        fixture.source().removeItem(0, 4);
        transfers.picked(allocation.id(), 4);
        var picked = food(helper, fixture.town());
        helper.assertValueEqual(picked.supply().availableNow() + picked.supply().incoming(), 25L, "pickup preserves the town-wide total");
        var restored = SettlementRecord.load(fixture.town().save(helper.getLevel().registryAccess()), helper.getLevel().registryAccess(), () -> {});
        var reloaded = food(helper, restored);
        helper.assertValueEqual(reloaded.supply().incoming(), 20L, "the saved receipt is credited once after reload");
        fixture.destination().setItem(0, new ItemStack(Items.BREAD, 4));
        transfers.delivered(allocation.id(), 4);
        var delivered = food(helper, fixture.town());
        helper.assertValueEqual(delivered.supply().availableNow(), 25L, "delivery converts incoming supply to available stock");
        helper.assertValueEqual(delivered.supply().incoming(), 0L, "completed receipts cannot remain incoming");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_reserve_demand")
    public static void independentConsumersShareIncomingIngredientsOnce(GameTestHelper helper) {
        var fixture = fixture(helper, 0);
        var first = bakery(helper, fixture, "First bakery", 5);
        var second = bakery(helper, fixture, "Second bakery", 10);
        fixture.source().setItem(0, new ItemStack(Items.WHEAT, 3));
        var transfers = fixture.town().beginTransfers(fixture.town().leader(), fixture.storage().id());
        var delivery = HiredTransferQueue.Order.create(
                new HiredTransferQueue.Endpoint(fixture.source().position(), fixture.source().id()),
                new HiredTransferQueue.Endpoint(fixture.destination().position(), fixture.destination().id()), new ItemStack(Items.WHEAT), 3);
        transfers.append(delivery);
        transfers.reserve(UUID.randomUUID(), delivery.id(), 3);
        var rows = SettlementDemandService.inspect(helper.getLevel(), fixture.town()).demands();
        helper.assertValueEqual(rows.size(), 2, "both production consumers retain their independent requirements");
        helper.assertValueEqual(rows.stream().mapToLong(row -> row.supply().availableNow()).sum(), 0L, "reserved source stock is unavailable to both bakers");
        helper.assertValueEqual(rows.stream().mapToLong(row -> row.supply().incoming()).sum(), 3L, "only one consumer receives the shared incoming wheat");
        helper.assertValueEqual(rows.stream().mapToLong(SettlementDemandService.Demand::missing).sum(), 3L, "the second baker still has a genuine shortage");
        helper.assertTrue(rows.stream().map(SettlementDemandService.Demand::consumer).toList().containsAll(List.of(first.id(), second.id())),
                "accounting preserves both manually controlled orders");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_reserve_demand")
    public static void proposedOutputNeverCountsAsCommittedFood(GameTestHelper helper) {
        var fixture = fixture(helper, 4);
        var order = bakery(helper, fixture, "Bakery", 5);
        var unstarted = food(helper, fixture.town());
        helper.assertValueEqual(unstarted.supply().incoming(), 0L, "a configured production order is not a physical commitment");
        var queue = fixture.town().productionQueues().values().stream().filter(value -> value.order(order.id()) != null).findFirst().orElseThrow();
        var allocation = queue.reserve(UUID.randomUUID(), order.id(), 1, 1, helper.getLevel().getGameTime(), null);
        helper.assertTrue(allocation != null, "a producer can reserve a real batch");
        var incoming = food(helper, fixture.town());
        helper.assertValueEqual(incoming.supply().incoming(), 5L, "the real batch contributes its native output nutrition");
        helper.assertValueEqual(incoming.supply().availableNow(), 0L, "future bread is not edible stock");
        helper.assertValueEqual(incoming.missing(), unstarted.missing() - 5L, "only real incoming output reduces the reserve shortage");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_reserve_demand")
    public static void stockFloorsAndActiveConsumersShareTheirCoveredItems(GameTestHelper helper) {
        var fixture = fixture(helper, 0);
        bakery(helper, fixture, "Bakery", 5);
        fixture.source().setItem(0, new ItemStack(Items.WHEAT, 10));
        fixture.source().setStockTarget(new ItemStack(Items.WHEAT), 5);
        var snapshot = SettlementDemandService.inspect(helper.getLevel(), fixture.town());
        var floor = snapshot.demands().stream().filter(row -> row.kind() == WorkSupplyDemand.Kind.STOCK_RESERVE).findFirst().orElseThrow();
        helper.assertValueEqual(floor.supply().targetReserve(), 5L, "the configured stock floor remains visible");
        helper.assertValueEqual(floor.requirement().quantity(), 2L, "three wheat already protected for baking count toward the overlapping floor");
        helper.assertValueEqual(snapshot.demands().stream().mapToLong(SettlementDemandService.Demand::supplied).sum(), 5L,
                "the shared stock protection never assigns an item twice");
        helper.assertValueEqual(snapshot.sellable(fixture.source(), new ItemStack(Items.WHEAT)), 5, "only the surplus above both obligations can be sold");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_reserve_demand")
    public static void typedFoodDemandAndIncomingQuantitiesSurviveTheManagementWire(GameTestHelper helper) {
        var fixture = fixture(helper, 4);
        var order = bakery(helper, fixture, "Bakery", 5);
        var queue = fixture.town().productionQueues().values().stream().filter(value -> value.order(order.id()) != null).findFirst().orElseThrow();
        queue.reserve(UUID.randomUUID(), order.id(), 1, 1, helper.getLevel().getGameTime(), null);
        fixture.source().setItem(0, new ItemStack(Items.APPLE));
        var leader = net.neoforged.neoforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(fixture.town().leader(), "ReserveViewer"));
        var page = SettlementManagementService.snapshot(leader, new com.jvn.villagerretaliation.network.SettlementManagementRequestPayload(
                fixture.town().id().value(), com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page.DEMAND, 0, "", 3, false));
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            com.jvn.villagerretaliation.network.SettlementManagementPayload.STREAM_CODEC.encode(buffer, page);
            var decoded = com.jvn.villagerretaliation.network.SettlementManagementPayload.STREAM_CODEC.decode(buffer);
            var demand = decoded.entries().stream().map(com.jvn.villagerretaliation.network.SettlementManagementPayload.Demand.class::cast)
                    .filter(row -> row.kind() == WorkSupplyDemand.Kind.FOOD).findFirst().orElseThrow();
            helper.assertValueEqual(demand.availableNow(), 4L, "the management snapshot preserves available nutrition");
            helper.assertValueEqual(demand.incoming(), 5L, "the management snapshot preserves future nutrition separately");
            helper.assertValueEqual(demand.proposed(), 0L, "inspection proposes no automatic work");
            helper.assertValueEqual(demand.protectedMinimum(), demand.required(), "the wire preserves essential protection");
            helper.assertValueEqual(demand.id(), food(helper, fixture.town()).supply().id(), "management and server demand share a semantic identity");
        } finally { buffer.release(); }
        helper.succeed();
    }

    private static SettlementDemandService.Demand food(GameTestHelper helper, SettlementRecord town) {
        var rows = SettlementDemandService.inspect(helper.getLevel(), town).demands().stream()
                .filter(row -> row.kind() == WorkSupplyDemand.Kind.FOOD).toList();
        helper.assertValueEqual(rows.size(), 1, "the settlement has exactly one food reserve");
        return rows.getFirst();
    }

    private static HiredProductionOrder bakery(GameTestHelper helper, Fixture fixture, String name, int offset) {
        var min = fixture.town().center().east(offset);
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.KITCHEN, name, min, min.offset(3, 2, 3), 5, true);
        helper.assertTrue(fixture.town().putArea(fixture.town().leader(), area), "the bakery is registered");
        fixture.town().setWorksitePlan(fixture.town().leader(), area.id(), SettlementWorksitePlan.defaults(area.type().role()));
        var order = HiredProductionOrder.create(helper.getLevel(), HiredProductionRecipe.exact(ResourceLocation.withDefaultNamespace("bread"), Map.of()),
                HiredProductionOrder.Mode.BATCH, 1, fixture.destination().position());
        fixture.town().beginProduction(fixture.town().leader(), area.id()).append(order);
        return order;
    }

    private static Fixture fixture(GameTestHelper helper, int residents) {
        var level = helper.getLevel();
        var origin = helper.absolutePos(BlockPos.ZERO);
        var center = new BlockPos(22_000_000 + origin.getX() * 8, 64, 22_000_000 + origin.getZ() * 8);
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(level.getGameTime(), level.dimension().location(), center, "Reserve test");
        var town = SettlementSavedData.get(level).establish(registry, id, UUID.randomUUID(), center, level.getGameTime()).orElseThrow();
        SettlementTestPlots.claim(level, town, center, center.offset(15, 3, 12));
        var storage = new SettlementArea(UUID.randomUUID(), SettlementAreaType.DEPOT, "Stores", center, center.offset(3, 2, 3), 5, true);
        helper.assertTrue(town.putArea(town.leader(), storage), "the storage worksite is registered");
        town.setWorksitePlan(town.leader(), storage.id(), SettlementWorksitePlan.defaults(storage.type().role()));
        var source = town.registerDepot(town.leader(), storage.id(), center, SettlementDepotPurpose.GENERAL);
        var destination = town.registerDepot(town.leader(), storage.id(), center.east(2), SettlementDepotPurpose.GENERAL);
        source.observeRules(List.of());
        destination.observeRules(List.of());
        for (int index = 0; index < residents; index++) registry.addOrUpdateResident(id, UUID.randomUUID(), true, level.getGameTime());
        return new Fixture(town, storage, source, destination);
    }

    private record Fixture(SettlementRecord town, SettlementArea storage, SettlementDepot source, SettlementDepot destination) { }
}
