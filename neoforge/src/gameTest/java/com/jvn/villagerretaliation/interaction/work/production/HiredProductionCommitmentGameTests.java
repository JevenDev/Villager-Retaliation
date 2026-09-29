package com.jvn.villagerretaliation.interaction.work.production;

import com.jvn.villagerretaliation.interaction.HireContract;
import com.jvn.villagerretaliation.interaction.HireContractStore;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.interaction.work.courier.HiredTransferQueue;
import com.jvn.villagerretaliation.interaction.work.courier.HiredTransferService;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("villagerretaliation")
@PrefixGameTestTemplate(false)
public final class HiredProductionCommitmentGameTests {
    private HiredProductionCommitmentGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 100, batch = "personal_stock")
    public static void privateQueuesCountEachOthersPendingProductionAndCarriedOutput(GameTestHelper helper) {
        var fixture = fixture(helper);
        try {
            var firstOrder = production(helper, fixture, fixture.first());
            var secondOrder = production(helper, fixture, fixture.second());
            var first = queue(helper, fixture.first());
            var second = queue(helper, fixture.second());
            var allocation = first.reserve(fixture.first().getUUID(), firstOrder.id(), 4, 2, 0L, null);
            helper.assertValueEqual(HiredProductionStock.count(helper.getLevel(), fixture.first(), first, firstOrder), 0L,
                    "the private queue accounts for its own allocation only once");
            helper.assertValueEqual(HiredProductionStock.count(helper.getLevel(), fixture.second(), second, secondOrder), 8L,
                    "another private producer sees the incoming recipe batches");
            helper.assertTrue(second.reserve(fixture.second().getUUID(), secondOrder.id(), 4, 1,
                    HiredProductionStock.count(helper.getLevel(), fixture.second(), second, secondOrder), null) == null,
                    "independent private queues cannot overproduce their shared stock target");
            first.start(allocation.id());
            HiredProductionStock.prepareDestination(HiredWorkStateStore.state(fixture.first()), false, firstOrder);
            HiredJobInventory.getJobInventory(fixture.first()).insertOutput(new ItemStack(Items.STICK, 4));
            first.complete(allocation.id(), new ItemStack(Items.STICK, 4));
            helper.assertValueEqual(HiredProductionStock.count(helper.getLevel(), fixture.second(), second, secondOrder), 8L,
                    "completed goods replace their pending quantity without disappearing or being counted twice");
            var stored = HiredProductionCommitments.get(helper.getLevel()).save(new CompoundTag(), helper.getLevel().registryAccess());
            var restored = HiredProductionCommitments.load(stored, helper.getLevel().registryAccess());
            helper.assertValueEqual(restored.count(fixture.destination(), new ItemStack(Items.STICK), fixture.second().getUUID()), 8L,
                    "pending batches and carried output survive save loading without live claims");
            fixture.first().remove(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
            helper.assertValueEqual(HiredProductionStock.count(helper.getLevel(), fixture.second(), second, secondOrder), 8L,
                    "an unloaded producer's incoming goods still cover the target");
            helper.assertFalse(first.owner().equals(second.owner()), "sharing stock visibility does not transfer contract ownership");
        } finally {
            cleanup(fixture);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "personal_stock")
    public static void personalCourierReceiptsCountAfterClaimsExpireAndKeepComponents(GameTestHelper helper) {
        var fixture = fixture(helper);
        try {
            var order = production(helper, fixture, fixture.first());
            var production = queue(helper, fixture.first());
            var courier = fixture.second();
            HireContractStore.save(courier, HireContract.regular(fixture.owner().getUUID(), helper.getLevel().getGameTime(), 1, 1, 1, HiredVillagerRole.COURIER));
            var transfer = HiredTransferQueue.Order.create(new HiredTransferQueue.Endpoint(fixture.source(), null),
                    new HiredTransferQueue.Endpoint(fixture.destination(), null), new ItemStack(Items.STICK), 7);
            helper.assertTrue(HiredTransferService.appendPersonal(fixture.owner(), courier, 0L, transfer).isEmpty(), "the private transfer is authorized");
            var queue = HiredTransferService.access(helper.getLevel(), courier).queue();
            var allocation = queue.reserve(courier.getUUID(), transfer.id(), 7);
            helper.assertValueEqual(HiredProductionStock.count(helper.getLevel(), fixture.first(), production, order), 7L,
                    "planned private transfers cover destination stock before pickup");
            HiredJobInventory.getJobInventory(courier).insertOutput(new ItemStack(Items.STICK, 7));
            queue.picked(allocation.id(), 7);
            AssignedStorageService.reconcileCourierClaims(courier, List.of(new ItemStack(Items.STICK, 7)));
            helper.assertValueEqual(HiredProductionStock.count(helper.getLevel(), fixture.first(), production, order), 7L,
                    "a live capacity claim does not duplicate the persistent receipt");
            AssignedStorageService.releaseCourierClaims(courier);
            helper.assertValueEqual(HiredProductionStock.count(helper.getLevel(), fixture.first(), production, order), 7L,
                    "expiring claims cannot hide the carried shipment");
            var named = new ItemStack(Items.STICK);
            named.set(DataComponents.CUSTOM_NAME, Component.literal("Different cargo"));
            helper.assertValueEqual(HiredProductionCommitments.get(helper.getLevel()).count(fixture.destination(), named, null), 0L,
                    "component variants cannot satisfy a different stock target");
            courier.discard();
            helper.assertValueEqual(HiredProductionStock.count(helper.getLevel(), fixture.first(), production, order), 0L,
                    "terminal worker removal releases its incoming stock");
        } finally {
            cleanup(fixture);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "personal_stock")
    public static void corruptCommitmentsStayReadOnlyAndRetainTheirOriginalPayload(GameTestHelper helper) {
        var fixture = fixture(helper);
        try {
            var order = production(helper, fixture, fixture.first());
            queue(helper, fixture.first()).reserve(fixture.first().getUUID(), order.id(), 4, 1, 0L, null);
            var tag = HiredProductionCommitments.get(helper.getLevel()).save(new CompoundTag(), helper.getLevel().registryAccess());
            tag.getList("Workers", Tag.TAG_COMPOUND).getCompound(0).getList("Incoming", Tag.TAG_COMPOUND).getCompound(0).putInt("Amount", -1);
            var rejected = HiredProductionCommitments.load(tag, helper.getLevel().registryAccess());
            helper.assertFalse(rejected.available(fixture.first().getUUID()), "corrupt stock data cannot authorize new incoming quantities");
            helper.assertValueEqual(rejected.count(fixture.destination(), new ItemStack(Items.STICK), null), -1L, "unknown stock pauses maintain-stock decisions");
            helper.assertValueEqual(rejected.save(new CompoundTag(), helper.getLevel().registryAccess()), tag, "the original damaged payload remains available for recovery");
        } finally {
            cleanup(fixture);
        }
        helper.succeed();
    }

    private static HiredProductionOrder production(GameTestHelper helper, Fixture fixture, Villager worker) {
        var order = HiredProductionOrder.create(helper.getLevel(),
                HiredProductionRecipe.exact(ResourceLocation.withDefaultNamespace("stick"), Map.of()), HiredProductionOrder.Mode.MAINTAIN_STOCK, 8, fixture.destination());
        helper.assertValueEqual(HiredProductionService.appendPersonal(fixture.owner(), worker, 0L, order), "", "personal production needs no civic authority");
        return order;
    }

    private static HiredProductionQueue queue(GameTestHelper helper, Villager worker) {
        return HiredProductionService.access(helper.getLevel(), worker, HiredVillagerRole.CRAFTSMAN).queue();
    }

    private static Fixture fixture(GameTestHelper helper) {
        var level = helper.getLevel();
        var source = helper.absolutePos(new BlockPos(1, 2, 2));
        var destination = helper.absolutePos(new BlockPos(5, 2, 2));
        level.setBlockAndUpdate(source, Blocks.CHEST.defaultBlockState());
        level.setBlockAndUpdate(destination, Blocks.CHEST.defaultBlockState());
        var owner = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "PrivateStockOwner"));
        var workers = new java.util.ArrayList<Villager>();
        for (int index = 0; index < 2; index++) {
            var worker = EntityType.VILLAGER.create(level);
            worker.setPos(destination.north().getCenter());
            worker.setNoAi(true);
            level.addFreshEntity(worker);
            HireContractStore.save(worker, HireContract.regular(owner.getUUID(), level.getGameTime(), 1, 1, 1, HiredVillagerRole.CRAFTSMAN));
            AssignedStorageService.assign(owner, worker, List.of(new AssignedStorageService.StoragePosition(level.dimension(), source)), "supply");
            AssignedStorageService.assign(owner, worker, List.of(new AssignedStorageService.StoragePosition(level.dimension(), destination)), "output");
            workers.add(worker);
        }
        return new Fixture(owner, workers.getFirst(), workers.getLast(), source, destination);
    }

    private static void cleanup(Fixture fixture) {
        for (var worker : List.of(fixture.first(), fixture.second())) {
            AssignedStorageService.releaseCourierClaims(worker);
            worker.discard();
            HiredProductionCommitments.get((net.minecraft.server.level.ServerLevel) worker.level()).workerRemoved(worker.getUUID());
        }
    }

    private record Fixture(ServerPlayer owner, Villager first, Villager second, BlockPos source, BlockPos destination) {}
}
