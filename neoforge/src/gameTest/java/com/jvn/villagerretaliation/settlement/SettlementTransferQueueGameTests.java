package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.work.courier.HiredTransferQueue;
import com.jvn.villagerretaliation.interaction.work.courier.HiredTransferService;
import java.util.List;
import java.util.Set;
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
public final class SettlementTransferQueueGameTests {
    private SettlementTransferQueueGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "transfer_queue")
    public static void couriersShareOneFinitePickupAndDeliveryLedger(GameTestHelper helper) {
        var queue = queue();
        var order = order(10);
        helper.assertTrue(queue.append(order), "a finite transfer is accepted");
        var first = queue.reserve(UUID.randomUUID(), order.id(), 8);
        var second = queue.reserve(UUID.randomUUID(), order.id(), 8);
        helper.assertValueEqual(first.amount(), 8, "the first courier reserves its pickup");
        helper.assertValueEqual(second.amount(), 2, "another courier cannot repeat the same remaining quantity");
        helper.assertValueEqual(queue.available(order.id()), 0, "all finite quantities are allocated before pickup");
        long revision = queue.revision();
        helper.assertTrue(queue.picked(first.id(), 5), "a partial pickup commits only actual cargo");
        helper.assertValueEqual(queue.available(order.id()), 3, "unused pickup capacity returns to the order");
        helper.assertFalse(queue.picked(first.id(), 5), "the same pickup cannot be credited twice");
        helper.assertValueEqual(queue.revision(), revision, "work progress does not invalidate an open editing form");
        helper.assertTrue(queue.edit(order.id(), HiredTransferQueue.Edit.CANCEL, 0), "cancellation stops future pickups");
        helper.assertTrue(queue.allocation(second.worker()) == null, "cancellation releases planned work");
        helper.assertValueEqual(queue.order(order.id()).pending(), 5, "carried cargo remains accounted for");
        helper.assertFalse(queue.edit(order.id(), HiredTransferQueue.Edit.REMOVE, 0), "an order with cargo cannot disappear");
        helper.assertTrue(queue.delivered(first.id(), 2), "partial delivery credits only accepted items");
        var restored = HiredTransferQueue.load(queue.save(helper.getLevel().registryAccess()), helper.getLevel().registryAccess(), ignored -> {});
        helper.assertValueEqual(restored.order(order.id()).pending(), 3, "saving retains the remaining cargo receipt");
        helper.assertTrue(restored.delivered(first.id(), 3), "cancelled orders still accept the cargo already picked up");
        helper.assertFalse(restored.delivered(first.id(), 3), "a delivery receipt cannot replay");
        helper.assertValueEqual(restored.order(order.id()).delivered(), 5L, "only the real collected quantity was delivered");
        helper.assertTrue(restored.edit(order.id(), HiredTransferQueue.Edit.REMOVE, 0), "the settled cancelled order can be removed");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "transfer_queue")
    public static void transferOrderingAndExactComponentsSurviveSaving(GameTestHelper helper) {
        var queue = queue();
        var first = order(4);
        var sample = new ItemStack(Items.IRON_INGOT);
        sample.set(DataComponents.CUSTOM_NAME, Component.literal("Workshop iron"));
        var named = HiredTransferQueue.Order.create(first.source(), first.destination(), sample, 6);
        queue.append(first);
        queue.append(named);
        sample.set(DataComponents.CUSTOM_NAME, Component.literal("Changed outside"));
        helper.assertTrue(named.matches(named.sample()) && !named.matches(sample) && !named.matches(first.sample()), "order components are immutable and exact");
        helper.assertTrue(queue.edit(first.id(), HiredTransferQueue.Edit.PAUSE, 0), "a blocked first entry can be paused");
        helper.assertValueEqual(queue.available(first.id()), 0, "paused work reserves no quantities");
        helper.assertTrue(queue.reserve(UUID.randomUUID(), named.id(), 6) != null, "a later compatible order can run");
        helper.assertTrue(queue.edit(named.id(), HiredTransferQueue.Edit.MOVE, 0), "priorities can be edited");
        var restored = HiredTransferQueue.load(queue.save(helper.getLevel().registryAccess()), helper.getLevel().registryAccess(), ignored -> {});
        helper.assertValueEqual(restored.orders().getFirst().id(), named.id(), "priority order survives saving");
        helper.assertValueEqual(restored.orders().getFirst().sample().getHoverName().getString(), "Workshop iron", "exact names survive saving");
        helper.assertTrue(restored.orders().getLast().paused(), "paused flags survive saving");
        var pending = restored.pending(named.sample(), named.destination().position());
        helper.assertValueEqual(pending, 6, "planned inbound cargo is visible to stock accounting");
        helper.assertValueEqual(restored.pending(first.sample(), first.destination().position()), 0, "different components are not interchangeable stock");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "transfer_queue")
    public static void damagedTransferReceiptsAreRejectedWithoutChangingTheirSource(GameTestHelper helper) {
        var queue = queue();
        var order = order(10);
        queue.append(order);
        var allocation = queue.reserve(UUID.randomUUID(), order.id(), 6);
        queue.picked(allocation.id(), 6);
        var saved = queue.save(helper.getLevel().registryAccess());
        var before = saved.copy();
        saved.getList("Allocations", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0).putInt("Amount", 7);
        invalid(helper, () -> HiredTransferQueue.load(saved, helper.getLevel().registryAccess(), ignored -> {}));
        helper.assertValueEqual(saved.getList("Allocations", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0).getInt("Amount"), 7,
                "rejected data is preserved for recovery");
        before.putInt("Version", 3);
        invalid(helper, () -> HiredTransferQueue.load(before, helper.getLevel().registryAccess(), ignored -> {}));
        queue.detach(allocation.worker());
        helper.assertValueEqual(queue.order(order.id()).blocker(), "transfer_worker_changed", "a reassignment exposes the unsettled cargo");
        helper.assertValueEqual(queue.order(order.id()).pending(), 6, "reassignment cannot recreate already collected goods");
        invalid(helper, () -> HiredTransferQueue.Order.create(order.source(), order.source(), order.sample(), 1));
        invalid(helper, () -> HiredTransferQueue.Order.create(order.source(), order.destination(), order.sample(), 4097));
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "transfer_queue")
    public static void transferQueuesRetainTheirExistingEntryAndPersonalWorkerLimits(GameTestHelper helper) {
        var queue = queue();
        for (int index = 0; index < HiredTransferQueue.MAX_ORDERS; index++) helper.assertTrue(queue.append(order(1)), "bounded transfer entry is accepted");
        helper.assertFalse(queue.append(order(1)), "transfer queues are limited to 32 entries");
        var personal = new HiredTransferQueue(UUID.randomUUID(), HiredTransferQueue.Scope.PERSONAL, ignored -> {});
        var order = HiredTransferQueue.Order.create(new HiredTransferQueue.Endpoint(BlockPos.ZERO, null),
                new HiredTransferQueue.Endpoint(BlockPos.ZERO.east(), null), new ItemStack(Items.BREAD), 4);
        helper.assertTrue(personal.append(order), "private transfers use assigned storage positions");
        helper.assertFalse(personal.append(order(1)), "private queues cannot acquire civic depot ownership");
        helper.assertTrue(personal.reserve(UUID.randomUUID(), order.id(), 2) != null, "the private courier reserves work");
        helper.assertTrue(personal.reserve(UUID.randomUUID(), order.id(), 2) == null, "a personal order has only one worker");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "transfer_authority")
    public static void sharedTransfersRequireAdoptionAndBothStaffingAndLogistics(GameTestHelper helper) {
        SettlementHaulingGameTests.withFixture(helper, fixture -> {
            var level = helper.getLevel();
            var order = HiredTransferQueue.Order.create(new HiredTransferQueue.Endpoint(fixture.source().position(), fixture.source().id()),
                    new HiredTransferQueue.Endpoint(fixture.destination().position(), fixture.destination().id()), new ItemStack(Items.IRON_INGOT), 7);
            helper.assertFalse(SettlementTransferService.append(fixture.leader(), fixture.town(), fixture.area().id(), 0L, order).isEmpty(),
                    "old worksites retain their behavior until explicit adoption");
            helper.assertTrue(SettlementWorksiteService.configure(fixture.leader(), fixture.town(), fixture.area().id(),
                    SettlementWorksitePlan.defaults(HiredVillagerRole.COURIER), true), "the leader adopts the depot worksite");
            var delegate = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,
                    new com.mojang.authlib.GameProfile(UUID.randomUUID(), "TransferDelegate"));
            fixture.town().setPermissions(fixture.leader().getUUID(), delegate.getUUID(), Set.of(SettlementPermission.STAFF));
            helper.assertFalse(SettlementTransferService.append(delegate, fixture.town(), fixture.area().id(), 0L, order).isEmpty(),
                    "staff authority alone cannot move communal stock");
            fixture.town().setPermissions(fixture.leader().getUUID(), delegate.getUUID(), Set.of(SettlementPermission.STAFF, SettlementPermission.LOGISTICS));
            helper.assertTrue(SettlementTransferService.append(delegate, fixture.town(), fixture.area().id(), 0L, order).isEmpty(),
                    "authorized logistics staff can request a transfer");
            helper.assertFalse(SettlementTransferService.append(delegate, fixture.town(), fixture.area().id(), 0L, order(1)).isEmpty(),
                    "stale edits cannot append over a changed queue");
            var queue = fixture.town().transferQueues().get(fixture.area().id());
            var reservation = queue.reserve(fixture.courier().getUUID(), order.id(), 7);
            queue.picked(reservation.id(), 7);
            var restored = SettlementRecord.load(fixture.town().save(level.registryAccess()), level.registryAccess(), () -> {});
            helper.assertValueEqual(restored.transferQueues().get(fixture.area().id()).order(order.id()).pending(), 7,
                    "town saves retain one shared pending quantity");
            fixture.town().setPermissions(fixture.leader().getUUID(), delegate.getUUID(), Set.of());
            helper.assertFalse(SettlementTransferService.edit(delegate, fixture.town(), fixture.area().id(), queue.revision(), order.id(),
                    HiredTransferQueue.Edit.CANCEL, 0).isEmpty(), "revoked permissions take effect immediately");
            helper.assertTrue(fixture.town().productionQueues().isEmpty(), "courier transfers do not create production recipes");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "transfer_authority")
    public static void personalCourierOrdersNeedOnlyTheContractAndAssignedStorage(GameTestHelper helper) {
        var level = helper.getLevel();
        var owner = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "TransferOwner"));
        var visitor = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "TransferVisitor"));
        var villager = net.minecraft.world.entity.EntityType.VILLAGER.create(level);
        villager.setPos(helper.absolutePos(new BlockPos(2, 2, 1)).getCenter());
        villager.setNoAi(true);
        level.addFreshEntity(villager);
        try {
            helper.assertTrue(com.jvn.villagerretaliation.interaction.HiredVillagerContractService.startHireContract(
                    level, villager, owner, 2, 8, HiredVillagerRole.COURIER), "the courier can be hired without a settlement");
            var source = helper.absolutePos(new BlockPos(2, 2, 2));
            var destination = helper.absolutePos(new BlockPos(5, 2, 2));
            level.setBlockAndUpdate(source, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
            level.setBlockAndUpdate(destination, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
            com.jvn.villagerretaliation.inventory.AssignedStorageService.assign(owner, villager,
                    List.of(new com.jvn.villagerretaliation.inventory.AssignedStorageService.StoragePosition(level.dimension(), source)), "supply");
            com.jvn.villagerretaliation.inventory.AssignedStorageService.assign(owner, villager,
                    List.of(new com.jvn.villagerretaliation.inventory.AssignedStorageService.StoragePosition(level.dimension(), destination)), "output");
            var order = HiredTransferQueue.Order.create(new HiredTransferQueue.Endpoint(source, null),
                    new HiredTransferQueue.Endpoint(destination, null), new ItemStack(Items.BREAD), 5);
            helper.assertFalse(HiredTransferService.appendPersonal(visitor, villager, 0L, order).isEmpty(), "another player cannot direct private cargo");
            helper.assertTrue(HiredTransferService.appendPersonal(owner, villager, 0L, order).isEmpty(), "the hire owner can request a private transfer");
            var queue = HiredTransferService.access(level, villager).queue();
            helper.assertValueEqual(queue.scope(), HiredTransferQueue.Scope.PERSONAL, "personal progress stays private");
            helper.assertFalse(HiredTransferService.editPersonal(owner, villager, 0L, order.id(), HiredTransferQueue.Edit.CANCEL, 0).isEmpty(),
                    "private edits enforce the same revision checks");
            var unassigned = HiredTransferQueue.Order.create(new HiredTransferQueue.Endpoint(source.west(), null), order.destination(), order.sample(), 1);
            helper.assertFalse(HiredTransferService.appendPersonal(owner, villager, queue.revision(), unassigned).isEmpty(),
                    "explicit orders cannot bypass existing storage assignment");
        } finally {
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "transfer_queue")
    public static void recurringReceiptsExceedFiniteLimitsAndMigrateOldOrders(GameTestHelper helper) {
        var queue = queue();
        var endpoints = order(4);
        var order = HiredTransferQueue.Order.create(endpoints.source(), endpoints.destination(), endpoints.sample(), 4096, HiredTransferQueue.Mode.MAINTAIN_STOCK);
        queue.append(order);
        for (int trip = 0; trip < 3; trip++) {
            var allocation = queue.reserve(UUID.randomUUID(), order.id(), 4096, 0);
            helper.assertTrue(allocation != null, "consumption makes a recurring order runnable again");
            queue.picked(allocation.id(), 4096);
            queue.delivered(allocation.id(), 4096);
        }
        var saved = queue.save(helper.getLevel().registryAccess());
        var restored = HiredTransferQueue.load(saved, helper.getLevel().registryAccess(), ignored -> {});
        helper.assertValueEqual(restored.order(order.id()).delivered(), 12288L, "cumulative receipts exceed the finite-order cap");
        helper.assertValueEqual(restored.available(order.id(), 4096), 0, "stored stock satisfies the target");
        helper.assertValueEqual(restored.available(order.id(), 4090), 6, "only the destination deficit is allocated");
        helper.assertTrue(restored.order(order.id()).runnable() && !restored.order(order.id()).complete(), "satisfied recurring orders remain enabled");
        var legacy = queue();
        var finite = order(10);
        legacy.append(finite);
        var picked = legacy.reserve(UUID.randomUUID(), finite.id(), 6);
        legacy.picked(picked.id(), 6);
        legacy.delivered(picked.id(), 2);
        var old = legacy.save(helper.getLevel().registryAccess());
        old.putInt("Version", 1);
        var entry = old.getList("Orders", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0);
        entry.remove("Mode");
        entry.putInt("Picked", 6);
        entry.putInt("Delivered", 2);
        var migrated = HiredTransferQueue.load(old, helper.getLevel().registryAccess(), ignored -> {});
        helper.assertValueEqual(migrated.order(finite.id()).mode(), HiredTransferQueue.Mode.FINITE, "old saves default to finite deliveries");
        helper.assertValueEqual(migrated.allocation(picked.worker()).id(), picked.id(), "migration preserves the allocation identity");
        helper.assertValueEqual(migrated.order(finite.id()).pending(), 4, "migration preserves unsettled cargo");
        helper.succeed();
    }

    private static HiredTransferQueue queue() {
        return new HiredTransferQueue(UUID.randomUUID(), HiredTransferQueue.Scope.WORKSITE, ignored -> {});
    }

    private static HiredTransferQueue.Order order(int amount) {
        return HiredTransferQueue.Order.create(new HiredTransferQueue.Endpoint(new BlockPos(1, 64, 1), UUID.randomUUID()),
                new HiredTransferQueue.Endpoint(new BlockPos(2, 64, 1), UUID.randomUUID()), new ItemStack(Items.IRON_INGOT), amount);
    }

    private static void invalid(GameTestHelper helper, Runnable action) {
        boolean rejected = false;
        try {
            action.run();
        } catch (IllegalArgumentException invalid) {
            rejected = true;
        }
        helper.assertTrue(rejected, "damaged or oversized transfers are rejected");
    }
}
