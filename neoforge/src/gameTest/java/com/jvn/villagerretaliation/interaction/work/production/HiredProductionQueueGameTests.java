package com.jvn.villagerretaliation.interaction.work.production;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("villagerretaliation")
@PrefixGameTestTemplate(false)
public final class HiredProductionQueueGameTests {
    private HiredProductionQueueGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "production_queue")
    public static void sharedBatchAllocationsFinishIndivisibleRecipesExactlyOnce(GameTestHelper helper) {
        var queue = queue(HiredVillagerRole.CRAFTSMAN);
        var order = sticks(helper, HiredProductionOrder.Mode.BATCH, 5, null);
        helper.assertTrue(queue.append(order), "the shared batch is accepted");
        var first = queue.reserve(UUID.randomUUID(), order.id(), 4, 1, 0L, BlockPos.ZERO);
        var second = queue.reserve(UUID.randomUUID(), order.id(), 4, 1, 0L, BlockPos.ZERO.east());
        helper.assertTrue(first != null && second != null, "two workers reserve the two required recipe batches");
        helper.assertTrue(queue.reserve(UUID.randomUUID(), order.id(), 4, 1, 0L, null) == null, "a third worker cannot duplicate the shared quantity");
        helper.assertValueEqual(queue.pending(order.id()), 8, "the final recipe batch is indivisible");
        helper.assertTrue(queue.start(first.id()), "the first recipe starts after reservation");
        helper.assertTrue(queue.complete(first.id(), new ItemStack(Items.STICK, 4)), "the real first batch is counted");
        helper.assertFalse(queue.complete(first.id(), new ItemStack(Items.STICK, 4)), "a repeated completion cannot increment progress");
        helper.assertTrue(queue.reserve(UUID.randomUUID(), order.id(), 4, 1, 0L, null) == null, "in-flight production still covers the remainder");
        queue.start(second.id());
        queue.complete(second.id(), new ItemStack(Items.STICK, 4));
        helper.assertValueEqual(queue.order(order.id()).produced(), 8L, "only the two completed batches are counted");
        helper.assertTrue(queue.order(order.id()).complete(), "the finite order is complete");
        helper.assertValueEqual(queue.pending(order.id()), 0, "finished allocations are released");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "production_queue")
    public static void cancellingAndReplacingStaffRetainsStartedBatches(GameTestHelper helper) {
        var queue = queue(HiredVillagerRole.CRAFTSMAN);
        var order = sticks(helper, HiredProductionOrder.Mode.BATCH, 12, null);
        queue.append(order);
        var worker = UUID.randomUUID();
        var started = queue.reserve(worker, order.id(), 4, 1, 0L, BlockPos.ZERO);
        var waiting = queue.reserve(UUID.randomUUID(), order.id(), 4, 1, 0L, BlockPos.ZERO.east());
        queue.start(started.id());
        helper.assertTrue(queue.pause(order.id(), true), "the order can pause");
        helper.assertValueEqual(queue.allocations().size(), 1, "pausing releases only unstarted work");
        helper.assertFalse(queue.start(waiting.id()), "released work cannot start from a stale worker action");
        helper.assertTrue(queue.cancel(order.id()), "the paused order can be cancelled");
        helper.assertFalse(queue.remove(order.id()), "an unfinished native batch keeps its receipt");
        helper.assertFalse(queue.release(started.id()), "ordinary release cannot erase consumed materials");
        queue.detach(worker);
        helper.assertTrue(queue.allocations().getFirst().worker() == null, "departure releases the worker while retaining production");
        helper.assertTrue(queue.resume(started.id(), UUID.randomUUID()), "another worker can finish the existing batch");
        helper.assertFalse(queue.complete(started.id(), new ItemStack(Items.BREAD)), "different goods cannot advance the order");
        helper.assertTrue(queue.complete(started.id(), new ItemStack(Items.STICK, 2)), "part of a completed station output can be collected");
        helper.assertValueEqual(queue.pending(order.id()), 2, "uncollected goods remain pending");
        helper.assertTrue(queue.complete(started.id(), new ItemStack(Items.STICK, 2)), "the rest can be collected once");
        helper.assertValueEqual(queue.order(order.id()).produced(), 4L, "cancellation preserves actual completed production");
        helper.assertTrue(queue.remove(order.id()), "the settled cancelled entry can be removed");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "production_queue")
    public static void stockOrdersCountMatchingPendingPotionsAndTheirDestination(GameTestHelper helper) {
        var queue = queue(HiredVillagerRole.BREWING);
        var recipe = HiredProductionRecipe.brewing(ResourceLocation.withDefaultNamespace("potion"), ResourceLocation.withDefaultNamespace("healing"));
        var first = HiredProductionOrder.create(helper.getLevel(), recipe, HiredProductionOrder.Mode.MAINTAIN_STOCK, 10, BlockPos.ZERO);
        var same = HiredProductionOrder.create(helper.getLevel(), recipe, HiredProductionOrder.Mode.MAINTAIN_STOCK, 10, BlockPos.ZERO);
        var otherStorage = HiredProductionOrder.create(helper.getLevel(), recipe, HiredProductionOrder.Mode.MAINTAIN_STOCK, 10, BlockPos.ZERO.east());
        var otherPotion = HiredProductionOrder.create(helper.getLevel(),
                HiredProductionRecipe.brewing(ResourceLocation.withDefaultNamespace("potion"), ResourceLocation.withDefaultNamespace("regeneration")),
                HiredProductionOrder.Mode.MAINTAIN_STOCK, 10, BlockPos.ZERO);
        queue.append(first);
        queue.append(same);
        queue.append(otherStorage);
        queue.append(otherPotion);
        var allocation = queue.reserve(UUID.randomUUID(), first.id(), 3, 1, 7L, null);
        helper.assertTrue(allocation != null, "stored and incoming goods leave one native brewing batch");
        helper.assertValueEqual(queue.available(same.id(), 7L), 0L, "another matching stock order includes pending production");
        helper.assertValueEqual(queue.available(otherStorage.id(), 7L), 3L, "goods reserved for another storage do not satisfy this target");
        helper.assertValueEqual(queue.available(otherPotion.id(), 7L), 3L, "different potion components remain distinct");
        queue.start(allocation.id());
        helper.assertFalse(queue.complete(allocation.id(), otherPotion.output()), "the wrong potion cannot be counted");
        helper.assertTrue(queue.complete(allocation.id(), first.output().copyWithCount(3)), "the exact potions complete the batch");
        helper.assertValueEqual(queue.available(first.id(), 10L), 0L, "completed carried deliveries count before reaching storage");
        helper.assertValueEqual(queue.available(first.id(), 8L), 2L, "consumed stock makes the order runnable again");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "production_queue")
    public static void queueOrderPauseCapacityAndPersonalOwnershipRemainIndependent(GameTestHelper helper) {
        var queue = queue(HiredVillagerRole.CRAFTSMAN);
        for (int index = 0; index < HiredProductionQueue.MAX_ORDERS; index++) {
            helper.assertTrue(queue.append(sticks(helper, HiredProductionOrder.Mode.CONTINUOUS, 1, null)), "a bounded queue entry is accepted");
        }
        helper.assertFalse(queue.append(sticks(helper, HiredProductionOrder.Mode.BATCH, 1, null)), "the queue cannot exceed 32 entries");
        var last = queue.orders().getLast();
        helper.assertTrue(queue.reorder(last.id(), 0), "orders can move to the front");
        helper.assertValueEqual(queue.orders().getFirst().id(), last.id(), "execution order follows the new priority");
        queue.blocker(last.id(), "missing_materials");
        queue.pause(last.id(), true);
        helper.assertValueEqual(queue.available(last.id(), 0L), 0L, "a paused entry cannot reserve work");
        helper.assertTrue(queue.available(queue.orders().get(1).id(), 0L) > 0L, "lower orders remain runnable");
        var personal = new HiredProductionQueue(UUID.randomUUID(), HiredProductionQueue.Scope.PERSONAL, HiredVillagerRole.CRAFTSMAN, ignored -> {});
        var order = sticks(helper, HiredProductionOrder.Mode.BATCH, 8, null);
        personal.append(order);
        helper.assertTrue(personal.reserve(UUID.randomUUID(), order.id(), 4, 1, 0L, null) != null, "a personal hire owns its private quantity");
        helper.assertTrue(personal.reserve(UUID.randomUUID(), order.id(), 4, 1, 0L, null) == null, "personal orders cannot acquire civic staffing");
        helper.assertTrue(queue.order(order.id()) == null, "personal orders are absent from the civic queue");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "production_queue")
    public static void productionQueueSaveRetainsClaimsAndRejectsDamagedRecords(GameTestHelper helper) {
        var queue = queue(HiredVillagerRole.CRAFTSMAN);
        var order = sticks(helper, HiredProductionOrder.Mode.BATCH, 12, null);
        queue.append(order);
        var worker = UUID.randomUUID();
        var allocation = queue.reserve(worker, order.id(), 4, 2, 0L, BlockPos.ZERO);
        queue.start(allocation.id());
        queue.complete(allocation.id(), new ItemStack(Items.STICK, 4));
        var provider = helper.getLevel().registryAccess();
        var saved = queue.save(provider);
        var loaded = HiredProductionQueue.load(saved, provider, ignored -> {});
        helper.assertValueEqual(loaded.save(provider), saved, "ownership, exact recipes, progress and allocation state round trip");
        helper.assertValueEqual(loaded.allocation(worker).remaining(), 4, "the returning worker retains only its unfinished quantity");
        helper.assertValueEqual(loaded.order(order.id()).produced(), 4L, "completed production is not replayed");
        CompoundTag damaged = saved.copy();
        damaged.putInt("Version", 2);
        reject(helper, damaged);
        damaged = saved.copy();
        damaged.put("Orders", new ListTag());
        reject(helper, damaged);
        damaged = saved.copy();
        damaged.getList("Allocations", Tag.TAG_COMPOUND).getCompound(0).putInt("Remaining", -1);
        reject(helper, damaged);
        damaged = saved.copy();
        damaged.getList("Orders", Tag.TAG_COMPOUND).add(damaged.getList("Orders", Tag.TAG_COMPOUND).getCompound(0).copy());
        reject(helper, damaged);
        damaged = saved.copy();
        damaged.getList("Orders", Tag.TAG_COMPOUND).getCompound(0).putString("Output", "lost");
        reject(helper, damaged);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "production_queue")
    public static void productionRecipeRetainsExactIdentityAndValidatedAlternatives(GameTestHelper helper) {
        var recipe = HiredProductionRecipe.exact(ResourceLocation.withDefaultNamespace("stick"),
                Map.of(0, ResourceLocation.withDefaultNamespace("oak_planks"), 1, ResourceLocation.withDefaultNamespace("birch_planks")));
        helper.assertTrue(recipe.supports(helper.getLevel(), HiredVillagerRole.CRAFTSMAN), "the exact crafting recipe is supported");
        helper.assertFalse(recipe.supports(helper.getLevel(), HiredVillagerRole.COOK), "non-food recipes cannot become cooking orders");
        helper.assertValueEqual(HiredProductionRecipe.load(recipe.save()), recipe, "exact ingredient positions and choices persist");
        var invalid = HiredProductionRecipe.exact(ResourceLocation.withDefaultNamespace("stick"),
                Map.of(0, ResourceLocation.withDefaultNamespace("stone")));
        helper.assertTrue(invalid.output(helper.getLevel()).isEmpty(), "an invalid narrowed choice cannot silently revert to any ingredient");
        helper.assertTrue(HiredProductionRecipe.exact(ResourceLocation.withDefaultNamespace("iron_ingot_from_smelting_raw_iron"), Map.of())
                .supports(helper.getLevel(), HiredVillagerRole.SMELTER), "native raw ore processing remains supported");
        helper.assertFalse(HiredProductionRecipe.exact(ResourceLocation.withDefaultNamespace("charcoal"), Map.of())
                .supports(helper.getLevel(), HiredVillagerRole.SMELTER), "queues preserve the smelter's supported input restrictions");
        helper.succeed();
    }

    private static HiredProductionQueue queue(HiredVillagerRole role) {
        return new HiredProductionQueue(UUID.randomUUID(), HiredProductionQueue.Scope.WORKSITE, role, ignored -> {});
    }

    private static HiredProductionOrder sticks(GameTestHelper helper, HiredProductionOrder.Mode mode, int amount, BlockPos storage) {
        return HiredProductionOrder.create(helper.getLevel(),
                HiredProductionRecipe.exact(ResourceLocation.withDefaultNamespace("stick"), Map.of()), mode, amount, storage);
    }

    private static void reject(GameTestHelper helper, CompoundTag tag) {
        boolean rejected = false;
        try {
            HiredProductionQueue.load(tag, helper.getLevel().registryAccess(), ignored -> {});
        } catch (IllegalArgumentException exception) {
            rejected = true;
        }
        helper.assertTrue(rejected, "damaged production state must be rejected");
    }
}
