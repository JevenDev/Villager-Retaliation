package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredWorkSession;
import com.jvn.villagerretaliation.interaction.work.courier.HiredTransferQueue;
import com.jvn.villagerretaliation.interaction.work.courier.HiredTransferService;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("villagerretaliation")
@PrefixGameTestTemplate(false)
public final class SettlementTransferExecutionGameTests {
    private SettlementTransferExecutionGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "transfer_execution")
    public static void nativeTransfersPreserveExactCargoAndFinishCancelledDeliveries(GameTestHelper helper) {
        SettlementHaulingGameTests.withFixture(helper, fixture -> {
            var sample = new ItemStack(Items.IRON_INGOT);
            sample.set(DataComponents.CUSTOM_NAME, Component.literal("Foundry delivery"));
            fixture.source().setItem(0, new ItemStack(Items.IRON_INGOT, 20));
            fixture.source().setItem(1, sample.copyWithCount(20));
            var queue = adopt(fixture);
            var order = order(fixture.source(), fixture.destination(), sample, 7);
            queue.append(order);
            var session = HiredWorkSession.active(helper.getLevel(), fixture.courier());
            tick(helper, fixture, session);
            helper.assertValueEqual(queue.order(order.id()).picked(), 7, "the exact finite quantity is picked up");
            helper.assertValueEqual(fixture.source().getItem(0).getCount(), 20, "different components are untouched");
            helper.assertValueEqual(fixture.source().getItem(1).getCount(), 13, "pickup consumes real matching stock");
            helper.assertTrue(fixture.destination().isEmpty(), "pickup does not teleport cargo");
            queue.edit(order.id(), HiredTransferQueue.Edit.CANCEL, 0);
            helper.getLevel().setBlockAndUpdate(fixture.source().position(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            fixture.courier().setPos(fixture.destination().position().north().getCenter());
            tick(helper, fixture, session);
            helper.assertValueEqual(queue.order(order.id()).delivered(), 7, "cancellation still delivers collected cargo");
            helper.assertValueEqual(fixture.destination().count(order::matches), 7, "the destination receives exact components");
            helper.assertTrue(queue.allocations().isEmpty(), "the delivery receipt settles once");
            helper.assertTrue(session.inventory().collectOutputItems().isEmpty(), "delivered cargo leaves the worker");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "transfer_execution")
    public static void blockedFirstTransferAllowsALaterRunnableEntry(GameTestHelper helper) {
        SettlementHaulingGameTests.withFixture(helper, fixture -> {
            fixture.source().setItem(0, new ItemStack(Items.IRON_INGOT, 20));
            var queue = adopt(fixture);
            var unavailable = order(fixture.source(), fixture.destination(), new ItemStack(Items.DIAMOND), 5);
            var ready = order(fixture.source(), fixture.destination(), new ItemStack(Items.IRON_INGOT), 6);
            queue.append(unavailable);
            queue.append(ready);
            var session = HiredWorkSession.active(helper.getLevel(), fixture.courier());
            tick(helper, fixture, session);
            helper.assertValueEqual(queue.order(unavailable.id()).picked(), 0, "unavailable inputs cannot be fabricated");
            helper.assertValueEqual(queue.order(unavailable.id()).blocker(), "transfer_supply_or_capacity", "the skipped entry explains its blocker");
            helper.assertValueEqual(queue.order(ready.id()).picked(), 6, "the later runnable transfer is selected");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "transfer_execution")
    public static void fullDestinationsReserveNothingAndPartialDeliveryRetainsItsReceipt(GameTestHelper helper) {
        SettlementHaulingGameTests.withFixture(helper, fixture -> {
            fixture.source().setItem(0, new ItemStack(Items.IRON_INGOT, 20));
            var queue = adopt(fixture);
            var order = order(fixture.source(), fixture.destination(), new ItemStack(Items.IRON_INGOT), 7);
            queue.append(order);
            for (int slot = 0; slot < fixture.destination().getContainerSize(); slot++) fixture.destination().setItem(slot, new ItemStack(Items.STONE, 64));
            var session = HiredWorkSession.active(helper.getLevel(), fixture.courier());
            tick(helper, fixture, session);
            helper.assertTrue(queue.allocations().isEmpty(), "full output releases unstarted quantities");
            helper.assertValueEqual(fixture.source().getItem(0).getCount(), 20, "backpressure leaves source goods intact");
            fixture.destination().setItem(0, ItemStack.EMPTY);
            tick(helper, fixture, session);
            helper.assertValueEqual(queue.order(order.id()).picked(), 7, "new capacity permits the finite pickup");
            fixture.destination().setItem(0, new ItemStack(Items.IRON_INGOT, 62));
            fixture.courier().setPos(fixture.destination().position().north().getCenter());
            tick(helper, fixture, session);
            helper.assertValueEqual(queue.order(order.id()).delivered(), 2, "only accepted items are credited");
            helper.assertValueEqual(queue.order(order.id()).pending(), 5, "blocked cargo retains its receipt");
            var restored = SettlementRecord.load(fixture.town().save(helper.getLevel().registryAccess()), helper.getLevel().registryAccess(), () -> {});
            helper.assertValueEqual(restored.transferQueues().get(fixture.area().id()).order(order.id()).pending(), 5, "partial delivery survives saving");
            fixture.destination().setItem(1, ItemStack.EMPTY);
            tick(helper, fixture, session);
            helper.assertValueEqual(queue.order(order.id()).delivered(), 7, "the remaining cargo completes exactly once");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "transfer_execution")
    public static void plannedAndCarriedTransfersCountOnceTowardMaintainedStock(GameTestHelper helper) {
        SettlementHaulingGameTests.withFixture(helper, fixture -> {
            fixture.town().configureDepot(fixture.leader().getUUID(), fixture.destination().id(), SettlementDepotPurpose.GENERAL, 9);
            fixture.destination().observeRules(List.of());
            fixture.source().setItem(0, new ItemStack(Items.STICK, 20));
            var transfers = adopt(fixture);
            var transfer = order(fixture.source(), fixture.destination(), new ItemStack(Items.STICK), 7);
            transfers.append(transfer);
            var workshop = fixture.town().areas().values().stream().filter(area -> area.type() == SettlementAreaType.WORKSHOP).findFirst().orElseThrow();
            fixture.town().setWorksitePlan(fixture.leader().getUUID(), workshop.id(), SettlementWorksitePlan.defaults(HiredVillagerRole.CRAFTSMAN));
            var production = fixture.town().beginProduction(fixture.leader().getUUID(), workshop.id());
            var maintained = com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder.create(helper.getLevel(),
                    com.jvn.villagerretaliation.interaction.work.production.HiredProductionRecipe.exact(net.minecraft.resources.ResourceLocation.withDefaultNamespace("stick"), Map.of()),
                    com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder.Mode.MAINTAIN_STOCK, 10, fixture.destination().position());
            var session = HiredWorkSession.active(helper.getLevel(), fixture.courier());
            tick(helper, fixture, session);
            helper.assertValueEqual(SettlementProductionStock.count(helper.getLevel(), fixture.town(), production, maintained), 7L,
                    "live claims and persistent cargo count once");
            AssignedStorageService.releaseCourierClaims(fixture.courier());
            helper.assertValueEqual(SettlementProductionStock.count(helper.getLevel(), fixture.town(), production, maintained), 7L,
                    "in-transit goods remain stock after short-lived claims expire");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "transfer_background")
    public static void backgroundTransfersShareFiniteQuantitiesWithoutLoadingChunks(GameTestHelper helper) {
        SettlementBackgroundHaulingGameTests.fixture(helper, fixture -> {
            var town = fixture.town();
            town.setWorksitePlan(town.leader(), fixture.area().id(), SettlementWorksitePlan.defaults(HiredVillagerRole.COURIER));
            var queue = town.beginTransfers(town.leader(), fixture.area().id());
            fixture.source().setItem(0, new ItemStack(Items.IRON_INGOT, 20));
            var order = order(fixture.source(), fixture.destination(), new ItemStack(Items.IRON_INGOT), 7);
            queue.append(order);
            SettlementBackgroundHauling.tick(helper.getLevel(), town, fixture.worker(), 200, 1.0D);
            SettlementBackgroundHauling.tick(helper.getLevel(), town, fixture.worker(), 200, 1.0D);
            helper.assertValueEqual(queue.order(order.id()).delivered(), 7, "repeated work stops at the finite quantity");
            helper.assertValueEqual(fixture.source().getItem(0).getCount(), 13, "one exact shipment leaves its source");
            helper.assertValueEqual(fixture.destination().getItem(0).getCount(), 7, "one exact shipment arrives");
            helper.assertFalse(helper.getLevel().hasChunkAt(town.center()), "background transfers do not load chunks");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "transfer_background")
    public static void backgroundDeliveryResumesSavedNativeCargoAfterCancellation(GameTestHelper helper) {
        SettlementBackgroundHaulingGameTests.fixture(helper, fixture -> {
            var town = fixture.town();
            town.setWorksitePlan(town.leader(), fixture.area().id(), SettlementWorksitePlan.defaults(HiredVillagerRole.COURIER));
            var queue = town.beginTransfers(town.leader(), fixture.area().id());
            var order = order(fixture.source(), fixture.destination(), new ItemStack(Items.IRON_INGOT), 7);
            queue.append(order);
            var allocation = queue.reserve(fixture.worker().id(), order.id(), 7);
            queue.picked(allocation.id(), 7);
            com.jvn.villagerretaliation.inventory.HiredJobInventory.getJobInventory(fixture.courier()).insertOutput(new ItemStack(Items.IRON_INGOT, 7));
            fixture.worker().observe(helper.getLevel(), fixture.courier(), SettlementWorkforceService.duty(fixture.courier()).orElseThrow(), 0L);
            queue.edit(order.id(), HiredTransferQueue.Edit.CANCEL, 0);
            var restored = SettlementRecord.load(town.save(helper.getLevel().registryAccess()), helper.getLevel().registryAccess(), () -> {});
            var worker = restored.workers().get(fixture.worker().id());
            SettlementBackgroundHauling.tick(helper.getLevel(), restored, worker, 200, 1.0D);
            helper.assertValueEqual(restored.transferQueues().get(fixture.area().id()).order(order.id()).delivered(), 7, "saved cargo finishes its cancelled order");
            helper.assertTrue(worker.restore(fixture.courier()), "the updated inventory returns to the entity");
            helper.assertTrue(com.jvn.villagerretaliation.inventory.HiredJobInventory.getJobInventory(fixture.courier()).collectOutputItems().isEmpty(),
                    "handoff removes delivered cargo");
            helper.assertFalse(worker.restore(fixture.courier()), "the same inventory handoff cannot replay");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "transfer_execution")
    public static void personalCourierTransfersRunWithoutASettlementOrRoute(GameTestHelper helper) {
        var level = helper.getLevel();
        var owner = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "PrivateCourierOwner"));
        var villager = net.minecraft.world.entity.EntityType.VILLAGER.create(level);
        var source = helper.absolutePos(new BlockPos(2, 2, 2));
        var destination = helper.absolutePos(new BlockPos(5, 2, 2));
        villager.setPos(source.north().getCenter());
        villager.setNoAi(true);
        level.addFreshEntity(villager);
        try {
            helper.assertTrue(com.jvn.villagerretaliation.interaction.HiredVillagerContractService.startHireContract(
                    level, villager, owner, 2, 8, HiredVillagerRole.COURIER), "standalone hiring is sufficient");
            level.setBlockAndUpdate(source, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
            level.setBlockAndUpdate(destination, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
            AssignedStorageService.assign(owner, villager, List.of(new AssignedStorageService.StoragePosition(level.dimension(), source)), "supply");
            AssignedStorageService.assign(owner, villager, List.of(new AssignedStorageService.StoragePosition(level.dimension(), destination)), "output");
            var sample = new ItemStack(Items.IRON_INGOT);
            sample.set(DataComponents.CUSTOM_NAME, Component.literal("Private shipment"));
            var input = (net.minecraft.world.Container) level.getBlockEntity(source);
            var output = (net.minecraft.world.Container) level.getBlockEntity(destination);
            input.setItem(0, new ItemStack(Items.IRON_INGOT, 2));
            input.setItem(1, sample.copyWithCount(2));
            var order = HiredTransferQueue.Order.create(new HiredTransferQueue.Endpoint(source, null),
                    new HiredTransferQueue.Endpoint(destination, null), sample, 1);
            helper.assertTrue(HiredTransferService.appendPersonal(owner, villager, 0L, order).isEmpty(), "the owner creates a private transfer");
            var session = HiredWorkSession.active(level, villager);
            helper.assertFalse(session.route().usableForNavigation(), "explicit endpoints do not require a route");
            session.worker().tick(level, villager, owner, session.context());
            helper.assertValueEqual(HiredTransferService.access(level, villager).queue().order(order.id()).picked(), 1, "private pickup credits real cargo");
            villager.setPos(destination.north().getCenter());
            session.worker().tick(level, villager, owner, session.context());
            helper.assertValueEqual(HiredTransferService.access(level, villager).queue().order(order.id()).delivered(), 1, "bookkeeping tags do not change the requested product");
            helper.assertTrue(order.matches(output.getItem(0)), "private delivery preserves the requested item components");
            session.worker().tick(level, villager, owner, session.context());
            helper.assertValueEqual(input.getItem(1).getCount(), 1, "completed private orders stop before another pickup");
            helper.assertValueEqual(input.getItem(0).getCount(), 2, "plain iron is not interchangeable with the named shipment");
        } finally {
            AssignedStorageService.releaseCourierClaims(villager);
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "transfer_execution")
    public static void nativeCouriersSharePickupQuantityAndDestinationCapacity(GameTestHelper helper) {
        SettlementHaulingGameTests.withFixture(helper, fixture -> {
            fixture.source().setItem(0, new ItemStack(Items.IRON_INGOT, 20));
            for (int slot = 0; slot < fixture.destination().getContainerSize(); slot++) fixture.destination().setItem(slot, new ItemStack(Items.STONE, 64));
            fixture.destination().setItem(0, new ItemStack(Items.IRON_INGOT, 57));
            var queue = adopt(fixture);
            var first = order(fixture.source(), fixture.destination(), new ItemStack(Items.IRON_INGOT), 5);
            var next = order(fixture.source(), fixture.destination(), new ItemStack(Items.IRON_INGOT), 5);
            queue.append(first);
            queue.append(next);
            var other = SettlementHaulingGameTests.courier(helper, fixture.town(), fixture.area(), fixture.leader());
            try {
                var profile = com.jvn.villagerretaliation.profile.VillagerProfileSavedData.get(helper.getLevel()).get(other.getUUID());
                for (var skill : com.jvn.villagerretaliation.skill.VillagerSkill.values()) profile.setSkill(skill, 60, helper.getLevel().getGameTime());
                HiredWorkSession.invalidate(other);
                var firstSession = HiredWorkSession.active(helper.getLevel(), fixture.courier());
                tick(helper, fixture, firstSession);
                var secondSession = HiredWorkSession.active(helper.getLevel(), other);
                secondSession.worker().tick(helper.getLevel(), other, null, secondSession.context());
                helper.assertValueEqual(queue.order(first.id()).picked(), 5, "the first shared entry is allocated once");
                helper.assertValueEqual(queue.order(next.id()).picked(), 2, "the next courier reserves only remaining destination capacity");
                helper.assertValueEqual(fixture.source().getItem(0).getCount(), 13, "pickup claims conserve the shared source");
                fixture.courier().setPos(fixture.destination().position().north().getCenter());
                other.setPos(fixture.destination().position().south().getCenter());
                tick(helper, fixture, firstSession);
                secondSession.worker().tick(helper.getLevel(), other, null, secondSession.context());
                helper.assertValueEqual(fixture.destination().getItem(0).getCount(), 64, "the two deliveries fill the shared capacity once");
                helper.assertTrue(queue.allocations().isEmpty(), "both receipts settle without duplicate progress");
            } finally {
                AssignedStorageService.releaseCourierClaims(other);
                other.discard();
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "transfer_execution")
    public static void reassignedWorkersFinishOriginalCargoBeforeTheirNewRole(GameTestHelper helper) {
        SettlementHaulingGameTests.withFixture(helper, fixture -> {
            var level = helper.getLevel();
            fixture.source().setItem(0, new ItemStack(Items.IRON_INGOT, 20));
            var queue = adopt(fixture);
            var order = order(fixture.source(), fixture.destination(), new ItemStack(Items.IRON_INGOT), 7);
            queue.append(order);
            tick(helper, fixture, HiredWorkSession.active(level, fixture.courier()));
            fixture.town().workers().get(fixture.courier().getUUID()).observe(level, fixture.courier(),
                    SettlementWorkforceService.duty(fixture.courier()).orElseThrow(), level.getGameTime());
            var workshop = fixture.town().areas().values().stream().filter(area -> area.type() == SettlementAreaType.WORKSHOP).findFirst().orElseThrow();
            helper.assertTrue(SettlementWorkforceService.assign(level, fixture.leader(), fixture.town().id(), fixture.courier(), workshop.id()),
                    "an explicit role change preserves the collected shipment");
            var session = HiredWorkSession.active(level, fixture.courier());
            helper.assertValueEqual(session.role(), HiredVillagerRole.CRAFTSMAN, "the new assignment stays in place");
            helper.assertTrue(HiredTransferService.access(level, fixture.courier()).queue() == queue, "the old worksite retains receipt ownership");
            fixture.courier().setPos(fixture.destination().position().north().getCenter());
            var result = com.jvn.villagerretaliation.interaction.work.CourierWorker.resumePending(level, fixture.courier(), session.context());
            helper.assertTrue(result != null && result.completed(), "the original delivery runs before new production");
            helper.assertValueEqual(queue.order(order.id()).delivered(), 7, "the original order receives the delivery credit");
            helper.assertValueEqual(fixture.destination().count(order::matches), 7, "all original cargo arrives once");
            helper.assertValueEqual(fixture.source().getItem(0).getCount(), 13, "recovery never repeats pickup");
            helper.assertTrue(com.jvn.villagerretaliation.interaction.work.CourierWorker.resumePending(level, fixture.courier(), session.context()) == null,
                    "completed receipts return control to the new job");
            helper.assertValueEqual(fixture.town().duties().get(fixture.courier().getUUID()), workshop.id(), "delivery does not undo the explicit assignment");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "transfer_background")
    public static void savedReassignedCargoRespectsBothWorksitesAndFinishesOnce(GameTestHelper helper) {
        SettlementBackgroundHaulingGameTests.fixture(helper, fixture -> {
            var level = helper.getLevel();
            var town = fixture.town();
            town.setWorksitePlan(town.leader(), fixture.area().id(), SettlementWorksitePlan.defaults(HiredVillagerRole.COURIER));
            var queue = town.beginTransfers(town.leader(), fixture.area().id());
            var order = order(fixture.source(), fixture.destination(), new ItemStack(Items.IRON_INGOT), 7);
            queue.append(order);
            queue.picked(queue.reserve(fixture.worker().id(), order.id(), 7).id(), 7);
            com.jvn.villagerretaliation.inventory.HiredJobInventory.getJobInventory(fixture.courier()).insertOutput(new ItemStack(Items.IRON_INGOT, 7));
            var workshop = new SettlementArea(UUID.randomUUID(), SettlementAreaType.WORKSHOP, "New workshop",
                    town.center().east(6), town.center().offset(9, 2, 3), 5, true);
            town.putArea(town.leader(), workshop);
            town.assignDuty(fixture.worker().id(), workshop.id());
            SettlementWorkforceService.reconcile(level, fixture.courier());
            town.recordWorker(SettlementWorkerState.capture(level, fixture.courier(), SettlementWorkforceService.duty(fixture.courier()).orElseThrow(), 0L));
            var restored = SettlementRecord.load(town.save(level.registryAccess()), level.registryAccess(), () -> {});
            var worker = restored.workers().get(fixture.worker().id());
            var receipts = restored.transferQueues().get(fixture.area().id());
            var blockedLinks = new SettlementWorksiteLinks(java.util.Set.of(), java.util.Set.of(fixture.source().id()), java.util.Set.of());
            restored.setWorksitePlan(restored.leader(), workshop.id(),
                    new SettlementWorksitePlan(SettlementWorksitePlan.defaults(HiredVillagerRole.CRAFTSMAN).settings(), 1, blockedLinks));
            SettlementBackgroundService.update(level, restored, 200L);
            helper.assertValueEqual(receipts.order(order.id()).delivered(), 0, "new worksite permissions still apply to old cargo");
            helper.assertValueEqual(worker.status(), "transfer_storage_changed", "revoked delivery access is visible");
            restored.setWorksitePlan(restored.leader(), workshop.id(), SettlementWorksitePlan.defaults(HiredVillagerRole.CRAFTSMAN));
            restored.setWorksitePlan(restored.leader(), fixture.area().id(),
                    new SettlementWorksitePlan(SettlementWorksitePlan.defaults(HiredVillagerRole.COURIER).settings(), 1, blockedLinks));
            SettlementBackgroundService.update(level, restored, 400L);
            helper.assertValueEqual(receipts.order(order.id()).delivered(), 0, "original worksite permissions still apply");
            restored.setWorksitePlan(restored.leader(), fixture.area().id(), SettlementWorksitePlan.defaults(HiredVillagerRole.COURIER));
            SettlementBackgroundService.update(level, restored, 600L);
            helper.assertValueEqual(receipts.order(order.id()).delivered(), 7, "the restored worker settles the original shipment");
            helper.assertValueEqual(restored.depots().get(fixture.destination().id()).count(order::matches), 7, "real goods reach their original destination");
            SettlementBackgroundService.update(level, restored, 800L);
            helper.assertValueEqual(receipts.order(order.id()).delivered(), 7, "subsequent production cannot replay delivery credit");
            helper.assertTrue(worker.items(level.registryAccess()).stream().noneMatch(order::matches), "the saved inventory no longer carries delivered goods");
            helper.assertFalse(level.hasChunkAt(town.center()), "recovery does not load the settlement");
        });
    }

    private static HiredTransferQueue adopt(SettlementHaulingGameTests.Fixture fixture) {
        if (!SettlementWorksiteService.configure(fixture.leader(), fixture.town(), fixture.area().id(),
                SettlementWorksitePlan.defaults(HiredVillagerRole.COURIER), true)) throw new IllegalStateException("Cannot adopt courier worksite");
        var profile = com.jvn.villagerretaliation.profile.VillagerProfileSavedData.get(fixture.leader().serverLevel()).get(fixture.courier().getUUID());
        for (var skill : com.jvn.villagerretaliation.skill.VillagerSkill.values()) profile.setSkill(skill, 60, fixture.leader().serverLevel().getGameTime());
        HiredWorkSession.invalidate(fixture.courier());
        return fixture.town().beginTransfers(fixture.leader().getUUID(), fixture.area().id());
    }

    private static HiredTransferQueue.Order order(SettlementDepot source, SettlementDepot destination, ItemStack sample, int amount) {
        return HiredTransferQueue.Order.create(new HiredTransferQueue.Endpoint(source.position(), source.id()),
                new HiredTransferQueue.Endpoint(destination.position(), destination.id()), sample, amount);
    }

    private static void tick(GameTestHelper helper, SettlementHaulingGameTests.Fixture fixture, HiredWorkSession session) {
        session.worker().tick(helper.getLevel(), fixture.courier(), null, session.context());
    }
}
