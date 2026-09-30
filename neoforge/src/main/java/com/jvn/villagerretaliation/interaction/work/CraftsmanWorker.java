package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionQueue;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionService;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionStock;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.inventory.VillagerItemFilterService;
import com.jvn.villagerretaliation.item.VillagerFilterMatcher;
import com.jvn.villagerretaliation.item.VillagerItemFilterData;
import com.jvn.villagerretaliation.item.VillagerRetaliationItems;
import com.jvn.villagerretaliation.skill.HiredWorkPractice;
import com.jvn.villagerretaliation.skill.VillagerSkill;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;

public final class CraftsmanWorker extends AbstractBlockWorker {
    private static final String MODE_TAG = "CraftsmanMode";
    private static final String CURSOR_TAG = "CraftsmanCursor";
    private static final String ALLOCATION_TAG = "CraftsmanProductionAllocation";
    private static final int CRAFT_TICKS = 20;
    private static final int MAX_MATERIAL_PULL = 32;

    @Override
    public HiredVillagerRole role() {
        return HiredVillagerRole.CRAFTSMAN;
    }

    @Override
    public WorkResult tick(ServerLevel level, Villager villager, ServerPlayer hirer, HiredWorkContext context) {
        if (!context.hasWorkArea()) {
            HiredProductionService.releasePlanned(level, villager, role());
            return waitForWorkAreaAssignment(level, villager, context);
        }
        if (AssignedStorageService.hasAssignedStorage(level, villager)) {
            DepositResult deposit = depositOutputsOrMoveToStorage(level, context, villager, 0.45D);
            if (deposit == DepositResult.MOVING) {
                return WorkResult.progressed("interaction.work.craftsman.depositing_outputs");
            }
            if (deposit == DepositResult.STORAGE_FULL) {
                HiredProductionService.releasePlanned(level, villager, role());
                return WorkResult.idle(storageFullStatus(context));
            }
        }

        var production = HiredProductionService.access(level, villager, role());
        if (production.managed()) return tickProduction(level, villager, context, production);

        ItemStack filter = VillagerItemFilterService.assignedFilter(villager);
        List<Target> targets = targets(level, filter);
        if (targets.isEmpty()) {
            context.setProgressTicks(0);
            HiredWorkerBrain.setFailure(context, "craftsman_filter_empty", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION);
            return WorkResult.idle("interaction.work.craftsman.no_filter");
        }
        BlockPos table = nearestCraftingTable(level, villager, context);
        if (table == null) {
            context.setProgressTicks(0);
            HiredWorkerBrain.setFailure(context, "no_craftsman_table", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.SELECTING_TARGET);
            return WorkResult.idle("interaction.work.craftsman.no_table");
        }

        Selection selection = select(level, villager, context, targets, filter);
        if (selection == null) {
            context.setProgressTicks(0);
            setTaskState(context, HiredWorkerTaskState.WAITING_FOR_MATERIALS, table);
            HiredWorkerBrain.setFailure(context, "missing_craftsman_materials", level.getGameTime() + 100L);
            return WorkResult.idle("interaction.work.craftsman.missing_materials");
        }
        return craft(level, villager, context, table, selection, targets, null, null);
    }

    private WorkResult craft(ServerLevel level, Villager villager, HiredWorkContext context, BlockPos table,
            Selection selection, List<Target> targets, HiredProductionQueue queue, HiredProductionQueue.Allocation allocation) {
        List<HiredProductionMaterials.Need> missing =
                HiredProductionMaterials.missingItemNeeds(context, selection.materials());
        if (!missing.isEmpty()) {
            context.setProgressTicks(0);
            WorkResult gathering = gatherMaterials(level, villager, context, table, missing);
            return gathering != null ? gathering : WorkResult.idle("interaction.work.craftsman.missing_materials");
        }

        HiredWorkerBrain.clearFailure(context);
        HiredPathTarget pathTarget = bestWorkTarget(level, villager, context, table);
        if (pathTarget == null) {
            context.setProgressTicks(0);
            setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, table);
            HiredWorkerBrain.setFailure(context, "craftsman_table_unreachable", level.getGameTime() + 100L);
            return WorkResult.idle("interaction.work.craftsman.table_unreachable");
        }
        prepareBreakingTarget(level, context, villager, pathTarget);
        if (!canWorkFromCurrentPosition(level, villager, context, pathTarget)) {
            context.setProgressTicks(0);
            setTaskState(context, HiredWorkerTaskState.MOVING_TO_TARGET, table);
            moveToTarget(level, villager, context, pathTarget, 0.45D);
            return WorkResult.progressed("interaction.work.craftsman.moving_to_table");
        }

        holdWorkPosition(villager, pathTarget);
        faceBlock(villager, table);
        setTaskState(context, HiredWorkerTaskState.WORKING, table);
        int neededTicks = CRAFT_TICKS;
        int progress = context.progressTicks() + 1;
        context.setProgressTicks(progress);
        if (progress < neededTicks) {
            return WorkResult.progressed("interaction.work.craftsman.crafting");
        }
        context.setProgressTicks(0);
        var plan = HiredSupplyCrafting.prepareCarriedRecipe(level, context, selection.recipe(), selection.narrowedIngredients());
        if (plan == null || queue != null && (!queue.order(allocation.order()).matches(plan.result())
                || plan.result().getCount() > allocation.remaining())) {
            if (queue != null) {
                queue.blocker(allocation.order(), "recipe_unavailable");
                releaseProduction(villager, context, queue);
            }
            HiredWorkerBrain.setFailure(context, "missing_craftsman_materials", level.getGameTime() + 100L);
            return WorkResult.idle("interaction.work.craftsman.missing_materials");
        }
        if (queue != null && !queue.start(allocation.id())) {
            releaseProduction(villager, context, queue);
            return WorkResult.idle("interaction.work.craftsman.no_filter");
        }
        if (!HiredSupplyCrafting.applyCarriedRecipe(context, plan, true, true)) {
            if (queue != null) {
                queue.lost(allocation.id());
                queue.blocker(allocation.order(), "output_full");
                releaseProduction(villager, context, queue);
            }
            setTaskState(context, HiredWorkerTaskState.PAUSED_FULL_INVENTORY, table);
            HiredWorkerBrain.setFailure(context, "craftsman_output_full", level.getGameTime() + 100L);
            return WorkResult.idle("interaction.work.craftsman.output_full");
        }
        ItemStack result = plan.result();
        if (queue == null) advanceCursor(context.state(), targets, selection.target().slot());
        else {
            if (!queue.complete(allocation.id(), result)) throw new IllegalStateException("Crafted output did not match its production allocation");
            releaseProduction(villager, context, queue);
        }
        useWorkItem(level, villager, result);
        HiredWorkerBrain.clearFailure(context);
        return WorkResult.completedWithPractice(
                "interaction.work.craftsman.crafted",
                itemReplacements(result),
                HiredWorkPractice.batch(VillagerSkill.CRAFTING, "hired:craftsman:recipe",
                        result.getCount(), result.getItem().hashCode()));
    }

    private WorkResult tickProduction(ServerLevel level, Villager villager, HiredWorkContext context, HiredProductionService.Access access) {
        var queue = access.queue();
        if (!access.blocker().isEmpty()) {
            AssignedStorageService.releaseProductionInputs(villager);
            context.setProgressTicks(0);
            HiredWorkerBrain.setFailure(context, access.blocker(), level.getGameTime() + 100L);
            return WorkResult.idle("interaction.work.craftsman.no_filter");
        }
        var table = nearestCraftingTable(level, villager, context);
        if (table == null) {
            releaseProduction(villager, context, queue);
            for (var order : queue.executionOrders()) queue.blocker(order.id(), "no_crafting_table");
            return WorkResult.idle("interaction.work.craftsman.no_table");
        }
        var current = queue.allocation(villager.getUUID());
        for (var order : queue.executionOrders()) {
            long stock = HiredProductionStock.count(level, villager, queue, order);
            if (stock < 0L) {
                queue.blocker(order.id(), "output_storage_unavailable");
                continue;
            }
            long available = queue.available(order.id(), stock);
            if (current != null && current.order().equals(order.id())) available += current.remaining();
            if (available <= 0L) {
                queue.blocker(order.id(), "quantity_reserved_or_stock_met");
                continue;
            }
            if (context.inventory().hasOutput(ignored -> true)
                    && !java.util.Objects.equals(HiredProductionStock.destination(context.state()), order.storage())) {
                queue.blocker(order.id(), "awaiting_output_delivery");
                continue;
            }
            if (!order.recipe().supports(level, role()) || !ItemStack.matches(order.output(), order.recipe().output(level))) {
                queue.blocker(order.id(), "recipe_unavailable");
                continue;
            }
            var selection = select(level, villager, context, List.of(new Target(0, order.output())), order.recipe().filter(level));
            if (selection == null) {
                queue.blocker(order.id(), "missing_materials");
                continue;
            }
            var missing = new LinkedHashMap<Item, Integer>();
            selection.materials().forEach((item, count) -> {
                int needed = count - HiredSupplyCrafting.countCarried(context, item);
                if (needed > 0) missing.put(item, needed);
            });
            if (missing.isEmpty() && !context.inventory().canTransformSuppliesToOutputs(selection.materials(), List.of(order.output()))) {
                queue.blocker(order.id(), "output_full");
                continue;
            }
            if (!AssignedStorageService.reserveProductionInputs(villager, missing, order.id())) {
                queue.blocker(order.id(), "materials_reserved");
                continue;
            }
            if (current != null && !current.order().equals(order.id())) {
                queue.release(current.id());
                current = null;
            }
            if (current == null) current = queue.reserve(villager.getUUID(), order.id(), order.output().getCount(), 1, stock, null);
            if (current == null) {
                AssignedStorageService.releaseProductionInputs(villager);
                queue.blocker(order.id(), "quantity_reserved");
                continue;
            }
            if (!context.state().hasUUID(ALLOCATION_TAG) || !context.state().getUUID(ALLOCATION_TAG).equals(current.id())) {
                context.setProgressTicks(0);
                context.state().putUUID(ALLOCATION_TAG, current.id());
            }
            HiredProductionStock.prepareDestination(context.state(), context.inventory().hasOutput(ignored -> true), order);
            queue.blocker(order.id(), "");
            return craft(level, villager, context, table, selection, List.of(), queue, current);
        }
        releaseProduction(villager, context, queue);
        setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION);
        return WorkResult.idle("interaction.work.craftsman.no_filter");
    }

    private static void releaseProduction(Villager villager, HiredWorkContext context, HiredProductionQueue queue) {
        var allocation = queue.allocation(villager.getUUID());
        if (allocation != null) queue.release(allocation.id());
        AssignedStorageService.releaseProductionInputs(villager);
        context.state().remove(ALLOCATION_TAG);
        context.setProgressTicks(0);
    }

    private static Selection select(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            List<Target> targets,
            ItemStack filter) {
        Mode mode = mode(context.state());
        int start = mode == Mode.PREFER_FIRST ? 0 : indexAtOrAfter(targets, context.state().getInt(CURSOR_TAG));
        int attempts = mode == Mode.FORCED_ROUND_ROBIN ? 1 : targets.size();
        for (int offset = 0; offset < attempts; offset++) {
            Target target = targets.get((start + offset) % targets.size());
            for (RecipeHolder<CraftingRecipe> holder : level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
                CraftingRecipe recipe = holder.value();
                ItemStack result = recipe.getResultItem(level.registryAccess());
                if (recipe.isSpecial() || result.isEmpty() || !result.is(target.stack().getItem())
                        || !HiredProcessingRecipeFilter.allowsCraftingRecipe(level, filter, holder, result)
                        || !HiredSupplyCrafting.canUseRecipe(level, context, recipe)) {
                    continue;
                }
                Map<Item, Integer> materials = new LinkedHashMap<>();
                HiredSupplyCrafting.MaterialPlanner planner =
                        new HiredSupplyCrafting.MaterialPlanner(level, villager, context, false, true);
                var narrowed = HiredProcessingRecipeFilter.narrowedCraftingIngredients(level, filter, holder);
                if (planner.planRecipe(recipe, 1, materials, narrowed)) {
                    return new Selection(target, recipe, Map.copyOf(materials), narrowed);
                }
            }
        }
        return null;
    }

    private WorkResult gatherMaterials(ServerLevel level, Villager villager, HiredWorkContext context,
            BlockPos table, List<HiredProductionMaterials.Need> needs) {
        HiredWorkerBrain.setFailure(context, "missing_craftsman_materials", level.getGameTime() + 100L);
        if (!context.useAssignedStorageForSupplies() || !AssignedStorageService.hasAssignedStorage(level, villager)) {
            setTaskState(context, HiredWorkerTaskState.WAITING_FOR_MATERIALS, table);
            return null;
        }
        HiredProductionMaterials.Acquisition acquisition = HiredProductionMaterials.acquireFromAssignedStorage(
                level,
                villager,
                context,
                needs,
                0.45D,
                MAX_MATERIAL_PULL,
                HiredProductionMaterials.StorageFilterPolicy.IGNORE_INPUT_FILTER);
        BlockPos storage = acquisition.storagePos();
        return switch (acquisition.status()) {
            case NO_NEEDS -> null;
            case MISSING -> {
                setTaskState(context, HiredWorkerTaskState.WAITING_FOR_MATERIALS, table);
                yield null;
            }
            case MOVING -> {
                setTaskState(context, HiredWorkerTaskState.MOVING_TO_STORAGE, storage);
                HiredWorkerBrain.clearFailure(context);
                yield WorkResult.progressed("interaction.work.craftsman.collecting_materials");
            }
            case UNREACHABLE -> {
                setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, storage);
                HiredWorkerBrain.setFailure(context, "craftsman_storage_path_failed", level.getGameTime() + 100L);
                yield WorkResult.idle("interaction.work.craftsman.materials_unreachable");
            }
            case INVENTORY_FULL -> {
                faceBlock(villager, storage);
                setTaskState(context, HiredWorkerTaskState.PAUSED_FULL_INVENTORY, storage);
                HiredWorkerBrain.setFailure(context, "craftsman_material_inventory_full", level.getGameTime() + 100L);
                yield WorkResult.idle("interaction.work.craftsman.material_inventory_full");
            }
            case COLLECTED -> {
                faceBlock(villager, storage);
                HiredWorkerBrain.clearFailure(context);
                yield WorkResult.progressed("interaction.work.craftsman.collected_materials");
            }
        };
    }

    private static BlockPos nearestCraftingTable(ServerLevel level, Villager villager, HiredWorkContext context) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : context.workAreaPositions()) {
            if (!context.permitsStation(pos) || !context.isLoaded(level, pos) || !level.getBlockState(pos).is(Blocks.CRAFTING_TABLE)) {
                continue;
            }
            double distance = villager.distanceToSqr(pos.getCenter());
            if (distance < bestDistance) {
                best = pos.immutable();
                bestDistance = distance;
            }
        }
        return best;
    }

    public static List<Target> targets(ServerLevel level, ItemStack filter) {
        if (filter.isEmpty()) {
            return List.of();
        }
        if (VillagerRetaliationItems.isItemFilter(filter)
                && VillagerItemFilterData.mode(filter) == VillagerItemFilterData.Mode.ALLOWLIST) {
            List<ItemStack> entries = VillagerItemFilterData.entries(filter);
            List<Target> configuredTargets = new ArrayList<>();
            for (int slot = 0; slot < entries.size(); slot++) {
                ItemStack entry = entries.get(slot);
                if (!entry.isEmpty() && !VillagerRetaliationItems.isFilter(entry)) {
                    configuredTargets.add(new Target(slot, entry));
                }
            }
            if (!configuredTargets.isEmpty()) {
                return List.copyOf(configuredTargets);
            }
        }

        Map<Item, ItemStack> discovered = new LinkedHashMap<>();
        for (RecipeHolder<CraftingRecipe> holder
                : level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            CraftingRecipe recipe = holder.value();
            ItemStack result = recipe.getResultItem(level.registryAccess());
            if (!recipe.isSpecial()
                    && !result.isEmpty()
                    && VillagerFilterMatcher.matches(level, filter, result)) {
                discovered.putIfAbsent(result.getItem(), result.copy());
            }
        }
        List<Target> targets = new ArrayList<>(discovered.size());
        int cursor = 0;
        for (ItemStack result : discovered.values()) {
            targets.add(new Target(cursor++, result));
        }
        return List.copyOf(targets);
    }

    public static int indexAtOrAfter(List<Target> targets, int cursor) {
        for (int index = 0; index < targets.size(); index++) {
            if (targets.get(index).slot() >= Math.max(0, cursor)) {
                return index;
            }
        }
        return 0;
    }

    public static void advanceCursor(CompoundTag state, List<Target> targets, int completedSlot) {
        if (mode(state) == Mode.PREFER_FIRST) {
            return;
        }
        for (Target target : targets) {
            if (target.slot() > completedSlot) {
                state.putInt(CURSOR_TAG, target.slot());
                return;
            }
        }
        state.putInt(CURSOR_TAG, targets.getFirst().slot());
    }

    public static Mode mode(CompoundTag state) {
        return Mode.byId(state == null ? "" : state.getString(MODE_TAG));
    }

    public static Mode cycleMode(CompoundTag state) {
        Mode next = mode(state).next();
        state.putString(MODE_TAG, next.id());
        state.putInt(CURSOR_TAG, 0);
        return next;
    }

    private static Map<String, String> itemReplacements(ItemStack stack) {
        return Map.of(
                "item", stack.getHoverName().getString(),
                "count", Integer.toString(stack.getCount()));
    }

    public record Target(int slot, ItemStack stack) {}
    private record Selection(Target target, CraftingRecipe recipe, Map<Item, Integer> materials, Map<Integer, Item> narrowedIngredients) {}

    public enum Mode {
        PREFER_FIRST("prefer_first"),
        ROUND_ROBIN("round_robin"),
        FORCED_ROUND_ROBIN("forced_round_robin");

        private final String id;
        Mode(String id) { this.id = id; }
        public String id() { return this.id; }
        public String label() {
            return switch (this) {
                case PREFER_FIRST -> "Prefer First";
                case ROUND_ROBIN -> "Round Robin";
                case FORCED_ROUND_ROBIN -> "Forced Round Robin";
            };
        }
        private Mode next() { return values()[(ordinal() + 1) % values().length]; }
        private static Mode byId(String id) {
            if (id != null) {
                for (Mode mode : values()) {
                    if (mode.id.equals(id.toLowerCase(Locale.ROOT))) {
                        return mode;
                    }
                }
            }
            return PREFER_FIRST;
        }
    }
}
