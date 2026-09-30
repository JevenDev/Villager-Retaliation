package com.jvn.villagerretaliation.interaction.work.production;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.work.AbstractBlockWorker;
import com.jvn.villagerretaliation.interaction.work.HiredProcessingRecipeFilter;
import com.jvn.villagerretaliation.interaction.work.HiredProductionMaterials;
import com.jvn.villagerretaliation.interaction.work.HiredWorkContext;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerBrain;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerTaskState;
import com.jvn.villagerretaliation.interaction.work.WorkResult;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.settlement.SettlementFurnaceState;
import com.jvn.villagerretaliation.skill.HiredWorkPractice;
import com.jvn.villagerretaliation.skill.VillagerSkill;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlastFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.entity.SmokerBlockEntity;

public final class HiredQueuedFurnaceWorker extends AbstractBlockWorker {
    private final HiredVillagerRole role;

    public HiredQueuedFurnaceWorker(HiredVillagerRole role) {
        if (role != HiredVillagerRole.SMELTER && role != HiredVillagerRole.COOK) throw new IllegalArgumentException("Unsupported furnace role");
        this.role = role;
    }

    @Override
    public HiredVillagerRole role() {
        return this.role;
    }

    @Override
    public WorkResult tick(ServerLevel level, Villager villager, ServerPlayer hirer, HiredWorkContext context) {
        var access = HiredProductionService.access(level, villager, role());
        if (!access.managed() || access.queue() == null || !access.blocker().isEmpty()) {
            return waiting(level, context, access.blocker().isEmpty() ? "production_unavailable" : access.blocker());
        }
        var queue = access.queue();
        if (!context.hasWorkArea()) {
            releasePlanned(level, villager, queue);
            return waitForWorkAreaAssignment(level, villager, context);
        }
        var deposit = depositOutputsOrMoveToStorage(level, context, villager, 0.45D);
        if (deposit == DepositResult.MOVING) return WorkResult.progressed(status("depositing_outputs"));
        if (deposit == DepositResult.STORAGE_FULL) {
            releasePlanned(level, villager, queue);
            return WorkResult.idle(storageFullStatus(context));
        }
        var stations = HiredProductionStations.get(level);
        if (stations.readOnly()) return waiting(level, context, "production_station_recovery_required");
        var allocation = queue.allocation(villager.getUUID());
        if (allocation == null) {
            for (var orphan : queue.allocations()) {
                if (orphan.started() && orphan.worker() == null && orphan.station() != null
                        && context.isInsideWorkArea(orphan.station()) && context.permitsStation(orphan.station())
                        && context.isLoaded(level, orphan.station()) && queue.resume(orphan.id(), villager.getUUID())) {
                    allocation = queue.allocation(villager.getUUID());
                    break;
                }
            }
        }
        if (allocation != null && allocation.started()) return workBatch(level, villager, context, queue, allocation, ItemStack.EMPTY);
        if (allocation == null) {
            var legacy = role() == HiredVillagerRole.SMELTER
                    ? new com.jvn.villagerretaliation.interaction.work.SmeltingWorker().finishExisting(level, villager, hirer, context)
                    : new com.jvn.villagerretaliation.interaction.work.CookingWorker().finishExisting(level, villager, hirer, context);
            if (legacy != null && legacy.progressed()) return legacy;
        }
        var positions = stationPositions(level, villager, context);
        for (var order : queue.executionOrders()) {
            if (!order.recipe().supports(level, role()) || !ItemStack.matches(order.output(), order.recipe().output(level))) {
                queue.blocker(order.id(), "recipe_unavailable");
                continue;
            }
            long stock = HiredProductionStock.count(level, villager, queue, order);
            int own = allocation != null && allocation.order().equals(order.id()) ? allocation.remaining() : 0;
            if (stock < 0L || queue.available(order.id(), stock) + own <= 0L) {
                queue.blocker(order.id(), stock < 0L ? "output_storage_unavailable" : "quantity_reserved_or_stock_met");
                continue;
            }
            if (context.inventory().hasOutput(ignored -> true)
                    && !Objects.equals(HiredProductionStock.destination(context.state()), order.storage())) {
                queue.blocker(order.id(), "awaiting_output_delivery");
                continue;
            }
            if (role() == HiredVillagerRole.COOK
                    && level.getRecipeManager().byKey(order.recipe().recipe()).orElseThrow().value() instanceof net.minecraft.world.item.crafting.CraftingRecipe) {
                var result = new com.jvn.villagerretaliation.interaction.work.CookingWorker().tickProductionCraft(level, villager, context, queue, order, stock);
                if (result.progressed()) return result;
                releasePlanned(level, villager, queue);
                allocation = null;
                continue;
            }
            boolean foundStation = false;
            int pathAttempts = 0;
            for (var position : positions) {
                if (!context.permitsStation(position) || !context.isLoaded(level, position)
                        || !(level.getBlockEntity(position) instanceof AbstractFurnaceBlockEntity furnace)
                        || !supports(furnace, order, level)) continue;
                var claim = stations.reconcile(level, position, SettlementFurnaceState.identity(furnace));
                if (claim != null && (allocation == null || !claim.allocation().equals(allocation.id()))) continue;
                if (!furnace.getItem(0).isEmpty() || !furnace.getItem(2).isEmpty()) continue;
                if (++pathAttempts > 12) break;
                if (bestWorkTarget(level, villager, context, position) == null) continue;
                foundStation = true;
                var input = input(level, villager, context, furnace, order);
                var fuel = fuel(villager, context, furnace);
                if (input.isEmpty() || needsFuel(furnace) && fuel.isEmpty()) continue;
                int available = input.getCount();
                if (!fuel.isEmpty() && HiredProductionStock.matches(fuel, input) && needsFuel(furnace)) available--;
                int batches = Math.min(context.transferLimit(16), available);
                if (batches <= 0) continue;
                var consumed = new LinkedHashMap<Item, Integer>();
                consumed.put(input.getItem(), Math.min(batches, (int) ((queue.available(order.id(), stock) + own + order.output().getCount() - 1L) / order.output().getCount())));
                if (!fuel.isEmpty()) consumed.merge(fuel.getItem(), 1, Integer::sum);
                if (!context.canStoreOutputs(List.of(order.output()))
                        && !context.inventory().canTransformSuppliesToOutputs(consumed, List.of(order.output()))) {
                    queue.blocker(order.id(), "output_full");
                    continue;
                }
                if (allocation != null && (!allocation.order().equals(order.id()) || !position.equals(allocation.station()))) {
                    stations.release(allocation.id());
                    queue.release(allocation.id());
                    allocation = null;
                }
                if (allocation == null) allocation = queue.reserve(villager.getUUID(), order.id(), order.output().getCount(), batches, stock, position);
                if (allocation == null) continue;
                if (!stations.reserve(level, queue, allocation, SettlementFurnaceState.identity(furnace))) {
                    queue.release(allocation.id());
                    allocation = null;
                    continue;
                }
                HiredProductionStock.prepareDestination(context.state(), context.inventory().hasOutput(ignored -> true), order);
                var result = workBatch(level, villager, context, queue, allocation, input);
                if (result.progressed()) return result;
                releasePlanned(level, villager, queue);
                allocation = null;
            }
            if (!queue.order(order.id()).blocker().equals("output_full")) {
                queue.blocker(order.id(), foundStation ? "missing_materials_or_fuel" : "no_available_station");
            }
        }
        releasePlanned(level, villager, queue);
        return waiting(level, context, "no_runnable_production");
    }

    private static List<BlockPos> stationPositions(ServerLevel level, Villager villager, HiredWorkContext context) {
        var candidates = new java.util.LinkedHashSet<BlockPos>();
        java.util.Arrays.stream(context.state().getLongArray("ProductionFurnacePositions")).limit(128).mapToObj(BlockPos::of).forEach(candidates::add);
        Predicate<BlockPos> valid = position -> context.isInsideWorkArea(position) && context.permitsStation(position)
                && context.isLoaded(level, position) && level.getBlockEntity(position) instanceof AbstractFurnaceBlockEntity;
        candidates.removeIf(valid.negate());
        candidates.addAll(com.jvn.villagerretaliation.interaction.work.HiredWorkAreaScan.collect(
                context, "ProductionFurnaceScan", 4096, valid).candidates());
        var positions = candidates.stream().sorted(Comparator.comparingDouble(pos -> villager.distanceToSqr(pos.getCenter()))).limit(128).toList();
        context.state().putLongArray("ProductionFurnacePositions", positions.stream().mapToLong(BlockPos::asLong).toArray());
        return positions;
    }

    private WorkResult workBatch(ServerLevel level, Villager villager, HiredWorkContext context, HiredProductionQueue queue,
            HiredProductionQueue.Allocation allocation, ItemStack chosenInput) {
        var order = queue.order(allocation.order());
        var position = allocation.station();
        if (position == null || !context.isInsideWorkArea(position) || !context.permitsStation(position)
                || !context.isLoaded(level, position)) return blocked(level, context, queue, order, "station_unavailable");
        var stations = HiredProductionStations.get(level);
        if (!(level.getBlockEntity(position) instanceof AbstractFurnaceBlockEntity furnace)) {
            stations.release(allocation.id());
            queue.lost(allocation.id());
            return blocked(level, context, queue, order, "station_removed");
        }
        if (!stations.reserve(level, queue, allocation, SettlementFurnaceState.identity(furnace))) {
            return blocked(level, context, queue, order, "station_claim_changed");
        }
        if (!HiredProductionStock.prepareDestination(context.state(), context.inventory().hasOutput(ignored -> true), order)) {
            return blocked(level, context, queue, order, "awaiting_output_delivery");
        }
        var output = furnace.getItem(2);
        if (!output.isEmpty() && !HiredProductionStock.matches(order.output(), output)) {
            return blocked(level, context, queue, order, "station_output_changed");
        }
        if (allocation.started() && furnace.getItem(0).isEmpty() && output.isEmpty()) {
            stations.release(allocation.id());
            queue.lost(allocation.id());
            return blocked(level, context, queue, order, "station_batch_missing");
        }
        var input = allocation.started() ? furnace.getItem(0) : chosenInput;
        if (!input.isEmpty() && !allowedInput(level, furnace, order, input)) {
            return blocked(level, context, queue, order, "station_input_changed");
        }
        var fuel = fuel(villager, context, furnace);
        var required = new java.util.ArrayList<HiredProductionMaterials.Need>();
        int inputCount = allocation.started() ? 0 : allocation.remaining() / order.output().getCount();
        boolean fuelNeeded = output.isEmpty() && needsFuel(furnace);
        if (fuelNeeded && fuel.isEmpty()) return blocked(level, context, queue, order, "missing_fuel");
        if (inputCount > 0 && fuelNeeded && HiredProductionStock.matches(input, fuel)) {
            inputCount++;
            fuelNeeded = false;
        }
        if (inputCount > 0) required.add(exactNeed(input, inputCount));
        if (fuelNeeded) required.add(exactNeed(fuel, 1));
        var missing = new java.util.ArrayList<HiredProductionMaterials.Need>();
        for (var need : required) {
            int carried = context.inventory().supplySlots().stream().map(context.inventory()::getItem)
                    .filter(need.predicate()).mapToInt(ItemStack::getCount).sum();
            if (carried < need.count()) missing.add(new HiredProductionMaterials.Need(need.predicate(), need.count() - carried, need.label()));
        }
        if (!AssignedStorageService.reserveProductionNeeds(villager, missing)) {
            return blocked(level, context, queue, order, "materials_reserved");
        }
        if (!missing.isEmpty()) {
            var gathered = HiredProductionMaterials.acquireFromAssignedStorage(level, villager, context,
                    missing, 0.45D, 16,
                    HiredProductionMaterials.StorageFilterPolicy.IGNORE_INPUT_FILTER);
            queue.blocker(order.id(), gathered.status() == HiredProductionMaterials.Status.MISSING ? "missing_materials" : "");
            return gathered.moved() > 0 || gathered.status() == HiredProductionMaterials.Status.MOVING
                    ? WorkResult.progressed(status("collecting_materials")) : blocked(level, context, queue, order, "materials_unreachable");
        }
        var target = bestWorkTarget(level, villager, context, position);
        if (target == null) return blocked(level, context, queue, order, "station_unreachable");
        prepareBreakingTarget(level, context, villager, target);
        if (!canWorkFromCurrentPosition(level, villager, context, target)) {
            setTaskState(context, HiredWorkerTaskState.MOVING_TO_TARGET, position);
            moveToTarget(level, villager, context, target, 0.45D);
            return WorkResult.progressed(status("moving_to_station"));
        }
        holdWorkPosition(villager, target);
        faceBlock(villager, position);
        setTaskState(context, HiredWorkerTaskState.WORKING, position);
        HiredWorkerBrain.clearFailure(context);
        queue.blocker(order.id(), "");
        if (!output.isEmpty()) {
            int amount = Math.min(allocation.remaining(), Math.min(output.getCount(), context.transferLimit(16)));
            var planned = output.copyWithCount(amount);
            if (!context.canStoreOutputs(List.of(planned))) return blocked(level, context, queue, order, "output_full");
            var removed = furnace.removeItem(2, amount);
            var remainder = context.inventory().insertOutput(removed.copy());
            int moved = removed.getCount() - remainder.getCount();
            if (!remainder.isEmpty()) furnace.setItem(2, remainder.copyWithCount(furnace.getItem(2).getCount() + remainder.getCount()));
            if (moved <= 0) return blocked(level, context, queue, order, "output_full");
            if (!queue.complete(allocation.id(), HiredProductionStock.product(removed.copyWithCount(moved)))) {
                throw new IllegalStateException("Furnace output did not match its production allocation");
            }
            if (queue.allocation(villager.getUUID()) == null) stations.release(allocation.id());
            furnace.setChanged();
            AssignedStorageService.releaseProductionInputs(villager);
            boolean cooking = role() == HiredVillagerRole.COOK;
            return WorkResult.completedWithPractice(status("collected_output"),
                    Map.of("count", Integer.toString(moved), "item", removed.getHoverName().getString()),
                    HiredWorkPractice.batch(cooking ? VillagerSkill.COOKING : VillagerSkill.SMITHING,
                            cooking ? "hired:cooking:batch" : "hired:smelting:batch", moved, removed.getItem().hashCode()));
        }
        var remainder = furnace.getItem(1);
        if (!remainder.isEmpty() && remainder.getBurnTime(recipeType(furnace)) <= 0) {
            if (!context.canStoreOutputs(List.of(remainder))) return blocked(level, context, queue, order, "output_full");
            var leftover = context.inventory().insertOutput(remainder.copy());
            furnace.setItem(1, leftover);
            furnace.setChanged();
            return WorkResult.progressed(status("collected_fuel_remainder"));
        }
        var snapshot = context.inventory().captureSnapshot();
        ItemStack loadedInput = ItemStack.EMPTY;
        ItemStack loadedFuel = ItemStack.EMPTY;
        if (!allocation.started()) {
            loadedInput = take(context, input, allocation.remaining() / order.output().getCount());
            if (loadedInput.isEmpty()) return blocked(level, context, queue, order, "missing_materials");
        }
        if (needsFuel(furnace)) {
            loadedFuel = take(context, fuel, 1);
            if (loadedFuel.isEmpty()) {
                context.inventory().restoreSnapshot(snapshot);
                return blocked(level, context, queue, order, "missing_fuel");
            }
        }
        if (!queue.start(allocation.id())) {
            context.inventory().restoreSnapshot(snapshot);
            return blocked(level, context, queue, order, "production_changed");
        }
        if (!loadedInput.isEmpty()) furnace.setItem(0, loadedInput);
        if (!loadedFuel.isEmpty()) furnace.setItem(1, loadedFuel);
        stations.reserve(level, queue, queue.allocation(villager.getUUID()), SettlementFurnaceState.identity(furnace));
        furnace.setChanged();
        AssignedStorageService.releaseProductionInputs(villager);
        return WorkResult.progressed(status("waiting_station"));
    }

    private static HiredProductionMaterials.Need exactNeed(ItemStack sample, int count) {
        return new HiredProductionMaterials.Need(stack -> HiredProductionStock.matches(sample, stack), count, sample.getHoverName().getString());
    }

    private static ItemStack take(HiredWorkContext context, ItemStack sample, int count) {
        var material = HiredProductionStock.product(sample);
        return !material.isEmpty() && context.inventory().consumeSupplyExactly(stack -> HiredProductionStock.matches(material, stack), count)
                ? material.copyWithCount(count) : ItemStack.EMPTY;
    }

    private static ItemStack fuel(Villager villager, HiredWorkContext context, AbstractFurnaceBlockEntity furnace) {
        if (!needsFuel(furnace)) return ItemStack.EMPTY;
        Predicate<ItemStack> usable = stack -> stack.getBurnTime(recipeType(furnace)) > 0;
        var carried = context.inventory().findSupply(usable);
        if (!carried.isEmpty()) return carried;
        return context.useAssignedStorageForSupplies() ? AssignedStorageService.findProductionSupply(villager, usable) : ItemStack.EMPTY;
    }

    private static boolean needsFuel(AbstractFurnaceBlockEntity furnace) {
        return furnace.getItem(1).isEmpty() && furnace.saveWithoutMetadata(furnace.getLevel().registryAccess()).getInt("BurnTime") <= 0;
    }

    private static ItemStack input(ServerLevel level, Villager villager, HiredWorkContext context,
            AbstractFurnaceBlockEntity furnace, HiredProductionOrder order) {
        Predicate<ItemStack> allowed = stack -> allowedInput(level, furnace, order, stack);
        var carried = context.inventory().findSupply(allowed);
        if (!carried.isEmpty()) return carried;
        return context.useAssignedStorageForSupplies() ? AssignedStorageService.findProductionSupply(villager, allowed) : ItemStack.EMPTY;
    }

    private static boolean allowedInput(ServerLevel level, AbstractFurnaceBlockEntity furnace, HiredProductionOrder order, ItemStack input) {
        if (input.isEmpty()) return false;
        return level.getRecipeManager().getRecipeFor(recipeType(furnace), new SingleRecipeInput(input), level)
                .filter(recipe -> recipe.id().equals(order.recipe().recipe())
                        && HiredProcessingRecipeFilter.allows(level, order.recipe().filter(level), recipe, input,
                            recipe.value().assemble(new SingleRecipeInput(input), level.registryAccess()))).isPresent();
    }

    private static boolean supports(AbstractFurnaceBlockEntity furnace, HiredProductionOrder order, ServerLevel level) {
        return (furnace.getClass() == FurnaceBlockEntity.class || furnace.getClass() == BlastFurnaceBlockEntity.class
                || furnace.getClass() == SmokerBlockEntity.class)
                && level.getRecipeManager().byKey(order.recipe().recipe()).filter(recipe -> recipe.value().getType() == recipeType(furnace)).isPresent()
                && (furnace.getItem(1).isEmpty() || furnace.getItem(1).getBurnTime(recipeType(furnace)) > 0);
    }

    @SuppressWarnings("unchecked")
    public static RecipeType<AbstractCookingRecipe> recipeType(AbstractFurnaceBlockEntity furnace) {
        RecipeType<?> type = furnace instanceof BlastFurnaceBlockEntity ? RecipeType.BLASTING
                : furnace instanceof SmokerBlockEntity ? RecipeType.SMOKING : RecipeType.SMELTING;
        return (RecipeType<AbstractCookingRecipe>) type;
    }

    private void releasePlanned(ServerLevel level, Villager villager, HiredProductionQueue queue) {
        var allocation = queue.allocation(villager.getUUID());
        if (allocation != null && !allocation.started()) {
            HiredProductionStations.get(level).release(allocation.id());
            queue.release(allocation.id());
        }
        AssignedStorageService.releaseProductionInputs(villager);
    }

    private WorkResult blocked(ServerLevel level, HiredWorkContext context, HiredProductionQueue queue, HiredProductionOrder order, String reason) {
        queue.blocker(order.id(), reason);
        return waiting(level, context, reason);
    }

    private WorkResult waiting(ServerLevel level, HiredWorkContext context, String reason) {
        HiredWorkerBrain.setFailure(context, reason, level.getGameTime() + 100L);
        setTaskState(context, HiredWorkerTaskState.WAITING_FOR_MATERIALS);
        return WorkResult.idle(status("waiting_station"));
    }

    private String status(String suffix) {
        return "interaction.work." + (role() == HiredVillagerRole.COOK ? "cooking." : "smelting.") + suffix;
    }
}
