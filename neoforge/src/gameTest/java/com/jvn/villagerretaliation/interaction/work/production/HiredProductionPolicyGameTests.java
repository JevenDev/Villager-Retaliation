package com.jvn.villagerretaliation.interaction.work.production;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("production_policy")
@PrefixGameTestTemplate(false)
public final class HiredProductionPolicyGameTests {
    private HiredProductionPolicyGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "production_queue")
    public static void craftingLifecyclePreservesStartedWorkAndResetsOnlyChangedProgress(GameTestHelper helper) {
        var queue = queue(HiredVillagerRole.CRAFTSMAN);
        var first = sticks(helper, HiredProductionOrder.Mode.BATCH, 4, null);
        var second = sticks(helper, HiredProductionOrder.Mode.BATCH, 4, BlockPos.ZERO);
        queue.append(first);
        queue.append(second);
        var worker = UUID.randomUUID();
        var resets = new java.util.concurrent.atomic.AtomicInteger();
        var state = new CompoundTag();
        var allocation = HiredProductionCrafting.reserve(helper.getLevel(), queue, worker, first, 4, 0L, resets::incrementAndGet);
        HiredProductionCrafting.trackProgress(state, allocation, resets::incrementAndGet);
        HiredProductionCrafting.trackProgress(state, allocation, resets::incrementAndGet);
        helper.assertValueEqual(resets.get(), 1, "continuing the same allocation preserves work");
        var replacement = HiredProductionCrafting.reserve(helper.getLevel(), queue, worker, second, 4, 0L, resets::incrementAndGet);
        helper.assertFalse(replacement.id().equals(allocation.id()), "planned work can change orders");
        helper.assertValueEqual(queue.pending(first.id()), 0, "the old planned quantity is released");
        queue.start(replacement.id());
        helper.assertTrue(HiredProductionCrafting.reserve(helper.getLevel(), queue, worker, first, 4, 0L,
                resets::incrementAndGet) == null, "started work cannot be replaced by another order");
        HiredProductionCrafting.releasePlanned(helper.getLevel(), queue, queue.allocation(worker));
        helper.assertValueEqual(queue.pending(second.id()), 4, "release preserves consumed inputs");
        helper.assertValueEqual(resets.get(), 2, "a refused replacement does not reset progress");
        var restored = HiredProductionQueue.load(queue.save(helper.getLevel().registryAccess()), helper.getLevel().registryAccess(), ignored -> {});
        helper.assertTrue(restored.allocation(worker).started(), "started ownership survives reload");
        HiredProductionCrafting.clearProgress(state, resets::incrementAndGet);
        helper.assertFalse(state.hasUUID("CraftsmanProductionAllocation"), "clearing uses the existing saved progress tag");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "production_queue")
    public static void eligibilityRetainsOwnReservationAndDeliveryPriority(GameTestHelper helper) {
        var queue = queue(HiredVillagerRole.CRAFTSMAN);
        var order = sticks(helper, HiredProductionOrder.Mode.BATCH, 4, BlockPos.ZERO);
        queue.append(order);
        var worker = UUID.randomUUID();
        queue.reserve(worker, order.id(), 4, 1, 0L, null);
        helper.assertValueEqual(HiredProductionEligibility.blocker(helper.getLevel(), HiredVillagerRole.CRAFTSMAN,
                queue, order, worker, 0L, false, null), "", "the worker can continue its own reserved batch");
        helper.assertValueEqual(HiredProductionEligibility.blocker(helper.getLevel(), HiredVillagerRole.CRAFTSMAN,
                queue, order, UUID.randomUUID(), 0L, false, null), "quantity_reserved_or_stock_met", "other workers cannot duplicate the batch");
        helper.assertValueEqual(HiredProductionEligibility.blocker(helper.getLevel(), HiredVillagerRole.CRAFTSMAN,
                queue, order, worker, -1L, true, null), "output_storage_unavailable", "unavailable storage takes precedence");
        helper.assertValueEqual(HiredProductionEligibility.blocker(helper.getLevel(), HiredVillagerRole.CRAFTSMAN,
                queue, order, worker, 0L, true, null), "awaiting_output_delivery", "carried goods keep their destination");
        helper.assertValueEqual(HiredProductionEligibility.blocker(helper.getLevel(), HiredVillagerRole.COOK,
                queue, order, worker, 0L, true, BlockPos.ZERO), "recipe_unavailable", "the worker role must support the recipe");
        helper.succeed();
    }

    private static HiredProductionQueue queue(HiredVillagerRole role) {
        return new HiredProductionQueue(UUID.randomUUID(), HiredProductionQueue.Scope.WORKSITE, role, ignored -> {});
    }

    private static HiredProductionOrder sticks(GameTestHelper helper, HiredProductionOrder.Mode mode, int amount, BlockPos storage) {
        return HiredProductionOrder.create(helper.getLevel(),
                HiredProductionRecipe.exact(ResourceLocation.withDefaultNamespace("stick"), Map.of()), mode, amount, storage);
    }
}
