package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.FluidState;

public final class BrewingWorker extends AbstractBlockWorker {
    private static final String TARGET_ITEM_TAG = "BrewingTargetItem";
    private static final String TARGET_POTION_TAG = "BrewingTargetPotion";
    private static final String REMAINING_TAG = "BrewingRemaining";
    private static final String CONTINUOUS_TAG = "BrewingContinuous";
    private static final String FUEL_USES_TAG = "BrewingFuelUses";
    private static final int FUEL_USES_PER_BLAZE_POWDER = 20;
    private static final int WATER_SOURCE_SEARCH_RADIUS = 4;
    private static final int BREW_PROGRESS_TICKS = 20;

    @Override
    public HiredVillagerRole role() {
        return HiredVillagerRole.BREWING;
    }

    public static void setOrder(CompoundTag state, ResourceLocation itemId, ResourceLocation potionId, int amount, boolean continuous) {
        state.putString(TARGET_ITEM_TAG, itemId.toString());
        state.putString(TARGET_POTION_TAG, potionId.toString());
        state.putInt(REMAINING_TAG, Math.max(0, amount));
        state.putBoolean(CONTINUOUS_TAG, continuous);
        state.remove("NextWorkGameTime");
    }

    public static boolean hasOrder(CompoundTag state) {
        return state.contains(TARGET_ITEM_TAG, Tag.TAG_STRING)
                && state.contains(TARGET_POTION_TAG, Tag.TAG_STRING)
                && (state.getBoolean(CONTINUOUS_TAG) || state.getInt(REMAINING_TAG) > 0);
    }

    public static String orderSummary(ServerLevel level, CompoundTag state) {
        return targetRoute(level, state)
                .map(route -> {
                    String amount = state.getBoolean(CONTINUOUS_TAG) ? "continuously" : Integer.toString(state.getInt(REMAINING_TAG));
                    return "Brewing " + amount + " x " + route.output().getHoverName().getString() + ".";
                })
                .orElse("No brewing order selected.");
    }

    @Override
    public WorkResult tick(ServerLevel level, Villager villager, ServerPlayer hirer, HiredWorkContext context) {
        if (!context.hasWorkArea()) {
            return waitForWorkAreaAssignment(level, villager, context);
        }
        if (!hasOrder(context.state())) {
            context.setProgressTicks(0);
            setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION);
            return WorkResult.idle("Choose a potion and amount before I start brewing.");
        }

        HiredBrewingRecipeCatalog.BrewingRoute route = targetRoute(level, context.state()).orElse(null);
        if (route == null) {
            context.setProgressTicks(0);
            HiredWorkerBrain.setFailure(context, "unknown_brewing_target", 0L);
            setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION);
            return WorkResult.idle("That potion recipe is no longer available.");
        }

        BlockPos stand = nearestBrewingStand(level, villager, context);
        if (stand == null) {
            context.setProgressTicks(0);
            HiredWorkerBrain.setFailure(context, "no_brewing_stand", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.SELECTING_TARGET);
            return WorkResult.idle("I need a brewing stand inside the assigned job site.");
        }

        boolean hasAssignedStorage = AssignedStorageService.hasAssignedStorage(level, villager);
        if (hasAssignedStorage) {
            DepositResult depositResult = depositOutputsOrMoveToStorage(level, context, villager, 0.45D);
            if (depositResult == DepositResult.MOVING) {
                return WorkResult.progressed("I am putting finished potions away before brewing more.");
            }
            if (depositResult == DepositResult.STORAGE_FULL) {
                return WorkResult.idle(storageFullStatus(context));
            }
        }

        HiredPathTarget target = bestWorkTarget(level, villager, context, stand);
        if (target == null) {
            HiredWorkerBrain.setFailure(context, "brewing_stand_unreachable", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, stand);
            return WorkResult.idle("I cannot reach the assigned brewing stand.");
        }
        prepareBreakingTarget(level, context, villager, target);
        if (!canWorkFromCurrentPosition(level, villager, context, target)) {
            context.setProgressTicks(0);
            setTaskState(context, HiredWorkerTaskState.MOVING_TO_TARGET, stand);
            if (!moveToTarget(level, villager, context, target, 0.45D)) {
                if (recordWorkPathFailure(level, villager, stand)) {
                    HiredWorkerBrain.setFailure(context, "brewing_stand_path_failed", level.getGameTime() + 20L * 30L);
                    setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, stand);
                    return WorkResult.idle("The brewing stand is blocked off, so I am waiting for a clear path.");
                }
                return WorkResult.progressed("I am moving into position at the brewing stand.");
            }
            return WorkResult.progressed("I am heading to the brewing stand.");
        }
        clearWorkPathFailure(villager, stand);
        holdMiningPosition(villager, target);
        HiredWorkerBrain.clearFailure(context);
        setTaskState(context, HiredWorkerTaskState.WORKING, stand);

        int batchSize = nextBatchSize(context.state());
        if (batchSize <= 0) {
            clearOrder(context.state());
            return WorkResult.completed("The brewing order is complete.");
        }
        boolean waterSource = hasWaterSource(level, stand);
        MaterialPlan materials = MaterialPlan.create(route, batchSize, waterSource, context.state().getInt(FUEL_USES_TAG));
        if (!materials.hasEverything(villager, context)) {
            context.setProgressTicks(0);
            HiredWorkerBrain.setFailure(context, materials.missingStatus(), level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION, stand);
            return WorkResult.idle(materials.missingStatus());
        }
        List<ItemStack> outputs = outputStacks(route, batchSize);
        if (!context.canStoreOutputs(outputs)) {
            if (hasAssignedStorage) {
                DepositResult fullDepositResult = depositOutputsForFullInventory(level, context, villager, 0.45D);
                if (fullDepositResult == DepositResult.MOVING) {
                    return WorkResult.progressed("I need room for the potions, so I am going to storage first.");
                }
                if (fullDepositResult == DepositResult.STORAGE_FULL) {
                    return WorkResult.idle(storageFullStatus(context));
                }
            }
            HiredWorkerBrain.setFailure(context, "brewing_output_full", 0L);
            setTaskState(context, HiredWorkerTaskState.PAUSED_FULL_INVENTORY, stand);
            return WorkResult.idle("I do not have enough room for the finished potions.");
        }

        int neededProgress = Math.max(4, BREW_PROGRESS_TICKS * 100 / Math.max(25, context.efficiency()));
        int progress = context.progressTicks() + 1;
        if (progress < neededProgress) {
            context.setProgressTicks(progress);
            swingWorkTool(villager);
            return WorkResult.progressed("I am brewing " + route.output().getHoverName().getString() + ".");
        }

        context.setProgressTicks(0);
        setTaskState(context, HiredWorkerTaskState.COLLECTING_OUTPUT, stand);
        materials.consume(villager, context);
        for (ItemStack output : outputs) {
            if (!context.storeOutputAfterDepositIfFull(villager, output).isEmpty()) {
                HiredWorkerBrain.setFailure(context, "brewing_output_full_after_brew", 0L);
                setTaskState(context, HiredWorkerTaskState.PAUSED_FULL_INVENTORY, stand);
                return WorkResult.idle("I brewed the potion, but I have no room left to carry it.");
            }
        }
        decrementOrder(context.state(), batchSize);
        level.playSound(null, stand, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 1.0F, 1.0F);
        return WorkResult.completed("I finished a brewing batch of " + route.output().getHoverName().getString() + ".");
    }

    @Override
    public String status(ServerLevel level, Villager villager, HiredWorkContext context) {
        return orderSummary(level, context.state()) + " " + context.status();
    }

    private static java.util.Optional<HiredBrewingRecipeCatalog.BrewingRoute> targetRoute(ServerLevel level, CompoundTag state) {
        ResourceLocation itemId = ResourceLocation.tryParse(state.getString(TARGET_ITEM_TAG));
        ResourceLocation potionId = ResourceLocation.tryParse(state.getString(TARGET_POTION_TAG));
        return HiredBrewingRecipeCatalog.find(level, itemId, potionId);
    }

    private static BlockPos nearestBrewingStand(ServerLevel level, Villager villager, HiredWorkContext context) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos raw : context.workAreaPositions()) {
            BlockPos pos = raw.immutable();
            if (!context.isLoaded(level, pos) || !level.getBlockState(pos).is(Blocks.BREWING_STAND)) {
                continue;
            }
            double distance = villager.distanceToSqr(pos.getCenter());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = pos;
            }
        }
        return best;
    }

    private static boolean hasWaterSource(ServerLevel level, BlockPos stand) {
        for (BlockPos raw : BlockPos.betweenClosed(
                stand.offset(-WATER_SOURCE_SEARCH_RADIUS, -1, -WATER_SOURCE_SEARCH_RADIUS),
                stand.offset(WATER_SOURCE_SEARCH_RADIUS, 1, WATER_SOURCE_SEARCH_RADIUS))) {
            BlockPos pos = raw.immutable();
            if (!level.hasChunkAt(pos)) {
                continue;
            }
            FluidState fluid = level.getFluidState(pos);
            if (fluid.is(FluidTags.WATER) && fluid.isSource()) {
                return true;
            }
        }
        return false;
    }

    private static int nextBatchSize(CompoundTag state) {
        if (state.getBoolean(CONTINUOUS_TAG)) {
            return 3;
        }
        return Math.min(3, Math.max(0, state.getInt(REMAINING_TAG)));
    }

    private static void decrementOrder(CompoundTag state, int brewed) {
        if (state.getBoolean(CONTINUOUS_TAG)) {
            return;
        }
        int remaining = Math.max(0, state.getInt(REMAINING_TAG) - brewed);
        state.putInt(REMAINING_TAG, remaining);
        if (remaining <= 0) {
            clearOrder(state);
        }
    }

    private static void clearOrder(CompoundTag state) {
        state.remove(TARGET_ITEM_TAG);
        state.remove(TARGET_POTION_TAG);
        state.remove(REMAINING_TAG);
        state.remove(CONTINUOUS_TAG);
    }

    private static List<ItemStack> outputStacks(HiredBrewingRecipeCatalog.BrewingRoute route, int count) {
        List<ItemStack> outputs = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            outputs.add(route.output().copyWithCount(1));
        }
        return outputs;
    }

    private record MaterialPlan(Map<Item, Integer> items, int batchSize, boolean waterSource, int fuelAfterUse, String missingStatus) {
        static MaterialPlan create(
                HiredBrewingRecipeCatalog.BrewingRoute route,
                int batchSize,
                boolean waterSource,
                int fuelUses) {
            Map<Item, Integer> items = new HashMap<>();
            for (Item ingredient : route.ingredients()) {
                items.merge(ingredient, 1, Integer::sum);
            }
            int fuelAfterUse = fuelUses;
            if (fuelAfterUse <= 0) {
                items.merge(Items.BLAZE_POWDER, 1, Integer::sum);
                fuelAfterUse = FUEL_USES_PER_BLAZE_POWDER;
            }
            fuelAfterUse--;
            return new MaterialPlan(items, batchSize, waterSource, fuelAfterUse, "");
        }

        boolean hasEverything(Villager villager, HiredWorkContext context) {
            for (Map.Entry<Item, Integer> entry : this.items.entrySet()) {
                if (countItem(villager, context, entry.getKey()) < entry.getValue()) {
                    return false;
                }
            }
            return canProvideWaterBottles(villager, context);
        }

        public String missingStatus() {
            return this.missingStatus.isBlank()
                    ? "I need more brewing supplies: bottles or bottled water, ingredients, and blaze powder."
                    : this.missingStatus;
        }

        void consume(Villager villager, HiredWorkContext context) {
            for (Map.Entry<Item, Integer> entry : this.items.entrySet()) {
                consumeItem(villager, context, entry.getKey(), entry.getValue());
            }
            if (this.waterSource && countItem(villager, context, Items.GLASS_BOTTLE) >= this.batchSize) {
                consumeItem(villager, context, Items.GLASS_BOTTLE, this.batchSize);
            } else {
                consumeWaterBottles(villager, context, this.batchSize);
            }
            context.state().putInt(FUEL_USES_TAG, Math.max(0, this.fuelAfterUse));
        }

        private boolean canProvideWaterBottles(Villager villager, HiredWorkContext context) {
            int reservedGlassBottles = this.items.getOrDefault(Items.GLASS_BOTTLE, 0);
            if (this.waterSource && countItem(villager, context, Items.GLASS_BOTTLE) >= reservedGlassBottles + this.batchSize) {
                return true;
            }
            return countWaterBottles(villager, context) >= this.batchSize;
        }

        private static int countItem(Villager villager, HiredWorkContext context, Item item) {
            int count = 0;
            for (int slot : context.inventory().supplySlots()) {
                ItemStack stack = context.inventory().getItem(slot);
                if (!stack.isEmpty() && stack.is(item)) {
                    count += stack.getCount();
                }
            }
            count += AssignedStorageService.countItems(villager, stack -> stack.is(item));
            return count;
        }

        private static int countWaterBottles(Villager villager, HiredWorkContext context) {
            int count = 0;
            for (int slot : context.inventory().supplySlots()) {
                ItemStack stack = context.inventory().getItem(slot);
                if (HiredBrewingRecipeCatalog.isWaterPotion(stack)) {
                    count += stack.getCount();
                }
            }
            count += AssignedStorageService.countItems(villager, HiredBrewingRecipeCatalog::isWaterPotion);
            return count;
        }

        private static void consumeItem(Villager villager, HiredWorkContext context, Item item, int count) {
            int consumed = context.inventory().consumeSupply(stack -> stack.is(item), count);
            int remaining = Math.max(0, count - consumed);
            if (remaining > 0) {
                AssignedStorageService.consumeItems(villager, stack -> stack.is(item), remaining);
            }
        }

        private static void consumeWaterBottles(Villager villager, HiredWorkContext context, int count) {
            int consumed = context.inventory().consumeSupply(HiredBrewingRecipeCatalog::isWaterPotion, count);
            int remaining = Math.max(0, count - consumed);
            if (remaining > 0) {
                AssignedStorageService.consumeItems(villager, HiredBrewingRecipeCatalog::isWaterPotion, remaining);
            }
        }
    }
}
