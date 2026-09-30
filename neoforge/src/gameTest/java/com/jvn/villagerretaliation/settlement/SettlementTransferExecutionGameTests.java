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
            helper.assertValueEqual(queue.order(order.id()).picked(), 7L, "the exact finite quantity is picked up");
            helper.assertValueEqual(fixture.source().getItem(0).getCount(), 20, "different components are untouched");
            helper.assertValueEqual(fixture.source().getItem(1).getCount(), 13, "pickup consumes real matching stock");
            helper.assertTrue(fixture.destination().isEmpty(), "pickup does not teleport cargo");
            queue.edit(order.id(), HiredTransferQueue.Edit.CANCEL, 0);
            helper.getLevel().setBlockAndUpdate(fixture.source().position(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            fixture.courier().setPos(fixture.destination().position().north().getCenter());
            tick(helper, fixture, session);
            helper.assertValueEqual(queue.order(order.id()).delivered(), 7L, "cancellation still delivers collected cargo");
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
            helper.assertValueEqual(queue.order(unavailable.id()).picked(), 0L, "unavailable inputs cannot be fabricated");
            helper.assertValueEqual(queue.order(unavailable.id()).blocker(), "transfer_supply_or_capacity", "the skipped entry explains its blocker");
            helper.assertValueEqual(queue.order(ready.id()).picked(), 6L, "the later runnable transfer is selected");
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
            helper.assertValueEqual(queue.order(order.id()).picked(), 7L, "new capacity permits the finite pickup");
            fixture.destination().setItem(0, new ItemStack(Items.IRON_INGOT, 62));
            fixture.courier().setPos(fixture.destination().position().north().getCenter());
            tick(helper, fixture, session);
            helper.assertValueEqual(queue.order(order.id()).delivered(), 2L, "only accepted items are credited");
            helper.assertValueEqual(queue.order(order.id()).pending(), 5, "blocked cargo retains its receipt");
            var restored = SettlementRecord.load(fixture.town().save(helper.getLevel().registryAccess()), helper.getLevel().registryAccess(), () -> {});
            helper.assertValueEqual(restored.transferQueues().get(fixture.area().id()).order(order.id()).pending(), 5, "partial delivery survives saving");
            fixture.destination().setItem(1, ItemStack.EMPTY);
            tick(helper, fixture, session);
            helper.assertValueEqual(queue.order(order.id()).delivered(), 7L, "the remaining cargo completes exactly once");
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
            helper.assertValueEqual(queue.order(order.id()).delivered(), 7L, "repeated work stops at the finite quantity");
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
            helper.assertValueEqual(restored.transferQueues().get(fixture.area().id()).order(order.id()).delivered(), 7L, "saved cargo finishes its cancelled order");
            helper.assertTrue(worker.restore(fixture.courier()), "the updated inventory returns to the entity");
            helper.assertTrue(com.jvn.villagerretaliation.inventory.HiredJobInventory.getJobInventory(fixture.courier()).collectOutputItems().isEmpty(),
                    "handoff removes delivered cargo");
            helper.assertFalse(worker.restore(fixture.courier()), "the same inventory handoff cannot replay");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "transfer_execution")
    public static void personalCourierTransfersRunWithoutASettlementOrRoute(GameTestHelper helper) {
        personalTransfer(helper, HiredTransferQueue.Mode.FINITE);
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "transfer_execution")
    public static void personalRecurringTransfersReplenishTheirAssignedStorage(GameTestHelper helper) {
        personalTransfer(helper, HiredTransferQueue.Mode.MAINTAIN_STOCK);
    }

    private static void personalTransfer(GameTestHelper helper, HiredTransferQueue.Mode mode) {
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
                    new HiredTransferQueue.Endpoint(destination, null), sample, 1, mode);
            helper.assertTrue(HiredTransferService.appendPersonal(owner, villager, 0L, order).isEmpty(), "the owner creates a private transfer");
            var session = HiredWorkSession.active(level, villager);
            helper.assertFalse(session.route().usableForNavigation(), "explicit endpoints do not require a route");
            session.worker().tick(level, villager, owner, session.context());
            helper.assertValueEqual(HiredTransferService.access(level, villager).queue().order(order.id()).picked(), 1L, "private pickup credits real cargo");
            villager.setPos(destination.north().getCenter());
            session.worker().tick(level, villager, owner, session.context());
            helper.assertValueEqual(HiredTransferService.access(level, villager).queue().order(order.id()).delivered(), 1L, "bookkeeping tags do not change the requested product");
            helper.assertTrue(order.matches(output.getItem(0)), "private delivery preserves the requested item components");
            session.worker().tick(level, villager, owner, session.context());
            helper.assertValueEqual(input.getItem(1).getCount(), 1, "completed private orders stop before another pickup");
            helper.assertValueEqual(input.getItem(0).getCount(), 2, "plain iron is not interchangeable with the named shipment");
            if (mode == HiredTransferQueue.Mode.MAINTAIN_STOCK) {
                output.removeItem(0, 1);
                villager.setPos(source.north().getCenter());
                session.worker().tick(level, villager, owner, session.context());
                villager.setPos(destination.north().getCenter());
                session.worker().tick(level, villager, owner, session.context());
                helper.assertValueEqual(HiredTransferService.access(level, villager).queue().order(order.id()).delivered(), 2L,
                        "personal recurring orders replenish consumed goods under the same contract");
            }
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
                helper.assertValueEqual(queue.order(first.id()).picked(), 5L, "the first shared entry is allocated once");
                helper.assertValueEqual(queue.order(next.id()).picked(), 2L, "the next courier reserves only remaining destination capacity");
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
    public static void backgroundHaulersRespectAnotherItemsReservedDestinationSlot(GameTestHelper helper) {
        SettlementHaulingGameTests.withFixture(helper, fixture -> {
            var level = helper.getLevel();
            fixture.source().setItem(0, new ItemStack(Items.IRON_INGOT, 7));
            for (int slot = 1; slot < fixture.destination().getContainerSize(); slot++) fixture.destination().setItem(slot, new ItemStack(Items.STONE, 64));
            var queue = adopt(fixture);
            var order = order(fixture.source(), fixture.destination(), new ItemStack(Items.IRON_INGOT), 7);
            queue.append(order);
            tick(helper, fixture, HiredWorkSession.active(level, fixture.courier()));
            helper.assertValueEqual(queue.order(order.id()).picked(), 7L, "a native courier reserves the last empty slot");
            var other = SettlementHaulingGameTests.courier(helper, fixture.town(), fixture.area(), fixture.leader());
            try {
                fixture.destination().observeRules(List.of());
                fixture.town().setDepotStockTarget(fixture.leader().getUUID(), fixture.destination().id(), new ItemStack(Items.GOLD_INGOT), 1);
                var worker = fixture.town().workers().get(other.getUUID());
                var items = worker.items(level.registryAccess());
                items.set(worker.outputSlots().getFirst(), new ItemStack(Items.GOLD_INGOT));
                worker.updateInventory(level.registryAccess(), items);
                SettlementBackgroundHauling.depositCarried(level, fixture.town(), worker, 1000, 1.0D, List.of(fixture.destination()), true);
                helper.assertValueEqual(worker.status(), "storage_full", "different cargo waits for the claimed slot");
                helper.assertTrue(fixture.destination().getItem(0).isEmpty(), "background delivery cannot consume another shipment's reserved capacity");
                helper.assertValueEqual(worker.items(level.registryAccess()).get(worker.outputSlots().getFirst()).getCount(), 1,
                        "the waiting cargo remains with its worker");
                AssignedStorageService.releaseCourierClaims(fixture.courier());
                SettlementBackgroundHauling.depositCarried(level, fixture.town(), worker, 1000, 1.0D, List.of(fixture.destination()), true);
                helper.assertValueEqual(fixture.destination().getItem(0).getCount(), 1, "released capacity becomes usable");
                helper.assertTrue(fixture.destination().getItem(0).is(Items.GOLD_INGOT), "the background shipment preserves its product");
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
            helper.assertValueEqual(queue.order(order.id()).delivered(), 7L, "the original order receives the delivery credit");
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
            helper.assertValueEqual(receipts.order(order.id()).delivered(), 0L, "new worksite permissions still apply to old cargo");
            helper.assertValueEqual(worker.status(), "transfer_storage_changed", "revoked delivery access is visible");
            restored.setWorksitePlan(restored.leader(), workshop.id(), SettlementWorksitePlan.defaults(HiredVillagerRole.CRAFTSMAN));
            restored.setWorksitePlan(restored.leader(), fixture.area().id(),
                    new SettlementWorksitePlan(SettlementWorksitePlan.defaults(HiredVillagerRole.COURIER).settings(), 1, blockedLinks));
            SettlementBackgroundService.update(level, restored, 400L);
            helper.assertValueEqual(receipts.order(order.id()).delivered(), 0L, "original worksite permissions still apply");
            restored.setWorksitePlan(restored.leader(), fixture.area().id(), SettlementWorksitePlan.defaults(HiredVillagerRole.COURIER));
            SettlementBackgroundService.update(level, restored, 600L);
            helper.assertValueEqual(receipts.order(order.id()).delivered(), 7L, "the restored worker settles the original shipment");
            helper.assertValueEqual(restored.depots().get(fixture.destination().id()).count(order::matches), 7, "real goods reach their original destination");
            SettlementBackgroundService.update(level, restored, 800L);
            helper.assertValueEqual(receipts.order(order.id()).delivered(), 7L, "subsequent production cannot replay delivery credit");
            helper.assertTrue(worker.items(level.registryAccess()).stream().noneMatch(order::matches), "the saved inventory no longer carries delivered goods");
            helper.assertFalse(level.hasChunkAt(town.center()), "recovery does not load the settlement");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "transfer_execution")
    public static void recurringNativeDeliveryReplenishesAfterConsumption(GameTestHelper helper) {
        SettlementHaulingGameTests.withFixture(helper, fixture -> {
            fixture.source().setItem(0, new ItemStack(Items.IRON_INGOT, 20));
            fixture.destination().setItem(0, new ItemStack(Items.IRON_INGOT, 2));
            var queue = adopt(fixture);
            var finite = order(fixture.source(), fixture.destination(), new ItemStack(Items.IRON_INGOT), 7);
            var order = HiredTransferQueue.Order.create(finite.source(), finite.destination(), finite.sample(), 7, HiredTransferQueue.Mode.MAINTAIN_STOCK);
            queue.append(order);
            var session = HiredWorkSession.active(helper.getLevel(), fixture.courier());
            tick(helper, fixture, session);
            helper.assertValueEqual(queue.order(order.id()).picked(), 5L, "initial stock reduces the first pickup");
            fixture.courier().setPos(fixture.destination().position().north().getCenter());
            tick(helper, fixture, session);
            tick(helper, fixture, session);
            helper.assertValueEqual(queue.order(order.id()).delivered(), 5L, "satisfaction cannot duplicate delivery");
            fixture.destination().removeItem(0, 3);
            fixture.courier().setPos(fixture.source().position().north().getCenter());
            tick(helper, fixture, session);
            helper.assertValueEqual(queue.order(order.id()).picked(), 8L, "consumption reserves exactly the new deficit");
            queue.edit(order.id(), HiredTransferQueue.Edit.PAUSE, 0);
            fixture.courier().setPos(fixture.destination().position().north().getCenter());
            tick(helper, fixture, session);
            helper.assertValueEqual(queue.order(order.id()).delivered(), 8L, "paused recurring work settles started cargo");
            helper.assertValueEqual(fixture.destination().count(order::matches), 7, "replenishment restores the target");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "transfer_execution")
    public static void recurringPickupRechecksStockBeforeTakingCargo(GameTestHelper helper) {
        SettlementHaulingGameTests.withFixture(helper, fixture -> {
            fixture.source().setItem(0, new ItemStack(Items.IRON_INGOT, 20));
            var queue = adopt(fixture);
            var finite = order(fixture.source(), fixture.destination(), new ItemStack(Items.IRON_INGOT), 7);
            var order = HiredTransferQueue.Order.create(finite.source(), finite.destination(), finite.sample(), 7, HiredTransferQueue.Mode.MAINTAIN_STOCK);
            queue.append(order);
            queue.reserve(fixture.courier().getUUID(), order.id(), 7, 0);
            fixture.destination().setItem(0, new ItemStack(Items.IRON_INGOT, 7));
            tick(helper, fixture, HiredWorkSession.active(helper.getLevel(), fixture.courier()));
            helper.assertTrue(queue.allocation(fixture.courier().getUUID()) == null, "newly satisfied stock releases the unstarted allocation");
            helper.assertValueEqual(queue.order(order.id()).picked(), 0L, "satisfied recurring work does not pick up excess cargo");
            helper.assertValueEqual(fixture.source().count(order::matches), 20, "the source remains unchanged");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "transfer_background")
    public static void recurringBackgroundDeliveryRespectsConsumptionAndReload(GameTestHelper helper) {
        SettlementBackgroundHaulingGameTests.fixture(helper, fixture -> {
            var town = fixture.town();
            town.setWorksitePlan(town.leader(), fixture.area().id(), SettlementWorksitePlan.defaults(HiredVillagerRole.COURIER));
            fixture.source().setItem(0, new ItemStack(Items.IRON_INGOT, 20));
            var queue = town.beginTransfers(town.leader(), fixture.area().id());
            var finite = order(fixture.source(), fixture.destination(), new ItemStack(Items.IRON_INGOT), 7);
            var order = HiredTransferQueue.Order.create(finite.source(), finite.destination(), finite.sample(), 7, HiredTransferQueue.Mode.MAINTAIN_STOCK);
            queue.append(order);
            for (int tick = 0; tick < 4; tick++) SettlementBackgroundTransfers.tick(helper.getLevel(), town, fixture.worker(), 200, 1);
            helper.assertValueEqual(queue.order(order.id()).delivered(), 7L, "background trips stop at destination stock");
            var restored = SettlementRecord.load(town.save(helper.getLevel().registryAccess()), helper.getLevel().registryAccess(), () -> {});
            restored.depots().get(fixture.destination().id()).removeItem(0, 4);
            for (int tick = 0; tick < 4; tick++) SettlementBackgroundTransfers.tick(helper.getLevel(), restored, restored.workers().get(fixture.worker().id()), 200, 1);
            helper.assertValueEqual(restored.transferQueues().get(fixture.area().id()).order(order.id()).delivered(), 11L, "reload replenishes consumed stock once");
            helper.assertFalse(helper.getLevel().hasChunkAt(town.center()), "recurring background work never loads the town");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "transfer_execution")
    public static void civicRecurringTransfersUseOrdinaryDestinations(GameTestHelper helper) {
        SettlementHaulingGameTests.withFixture(helper, fixture -> {
            var level = helper.getLevel();
            var queue = adopt(fixture);
            var chest = fixture.source().position().south(2);
            level.setBlockAndUpdate(chest, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
            var links = new SettlementStorageLinks(false, List.of(
                    new SettlementStorageLinks.Link(fixture.source().position(), SettlementStorageLinks.Purpose.SUPPLY, 0),
                    new SettlementStorageLinks.Link(chest, SettlementStorageLinks.Purpose.OUTPUT, 1)));
            var defaults = SettlementWorksitePlan.defaults(HiredVillagerRole.COURIER);
            helper.assertTrue(SettlementWorksiteService.configure(fixture.leader(), fixture.town(), fixture.area().id(),
                    new SettlementWorksitePlan(defaults.settings().withStorage(links), 1), false), "civic couriers adopt mixed endpoints");
            fixture.source().setItem(0, new ItemStack(Items.IRON_INGOT, 20));
            var output = (net.minecraft.world.Container) level.getBlockEntity(chest);
            output.setItem(0, new ItemStack(Items.IRON_INGOT, 2));
            var order = HiredTransferQueue.Order.create(new HiredTransferQueue.Endpoint(fixture.source().position(), fixture.source().id()),
                    new HiredTransferQueue.Endpoint(chest, null), new ItemStack(Items.IRON_INGOT), 7, HiredTransferQueue.Mode.MAINTAIN_STOCK);
            helper.assertValueEqual(SettlementTransferService.append(fixture.leader(), fixture.town(), fixture.area().id(), queue.revision(), order), "",
                    "ordinary civic destinations pass live permission validation");
            helper.assertFalse(SettlementTransferService.endpoints(level, fixture.town(), fixture.area().id(), order, fixture.courier().getUUID()),
                    "adopted worker storage overrides remain authoritative");
            helper.assertTrue(SettlementWorksiteService.reset(fixture.leader(), fixture.town(), fixture.courier().getUUID(), fixture.area().id(), SettlementStorageLinks.OVERRIDE),
                    "the courier explicitly adopts shared ordinary storage");
            var session = HiredWorkSession.active(level, fixture.courier());
            tick(helper, fixture, session);
            helper.assertValueEqual(queue.order(order.id()).picked(), 5L, "ordinary stock reduces the recurring deficit");
            fixture.courier().setPos(chest.north().getCenter());
            tick(helper, fixture, session);
            helper.assertValueEqual(output.getItem(0).getCount(), 7, "real cargo arrives at the ordinary chest");
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
