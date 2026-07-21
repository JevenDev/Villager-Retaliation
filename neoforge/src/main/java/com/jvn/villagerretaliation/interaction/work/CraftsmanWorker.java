package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.inventory.VillagerItemFilterService;
import com.jvn.villagerretaliation.item.VillagerItemFilterData;
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

/** Crafts the ordered outputs configured in the villager's assigned item filter. */
public final class CraftsmanWorker extends AbstractBlockWorker {
    private static final String MODE_TAG = "CraftsmanMode";
    private static final String CURSOR_TAG = "CraftsmanCursor";
    private static final int CRAFT_TICKS = 20;
    private static final int MAX_MATERIAL_PULL = 32;

    @Override
    public HiredVillagerRole role() {
        return HiredVillagerRole.CRAFTSMAN;
    }

    @Override
    public WorkResult tick(ServerLevel level, Villager villager, ServerPlayer hirer, HiredWorkContext context) {
        if (!context.hasWorkArea()) {
            return waitForWorkAreaAssignment(level, villager, context);
        }
        if (AssignedStorageService.hasAssignedStorage(level, villager)) {
            DepositResult deposit = depositOutputsOrMoveToStorage(level, context, villager, 0.45D);
            if (deposit == DepositResult.MOVING) {
                return WorkResult.progressed("interaction.work.craftsman.depositing_outputs");
            }
            if (deposit == DepositResult.STORAGE_FULL) {
                return WorkResult.idle(storageFullStatus(context));
            }
        }

        List<Target> targets = targets(VillagerItemFilterService.assignedFilter(villager));
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

        Selection selection = select(level, villager, context, targets);
        if (selection == null) {
            context.setProgressTicks(0);
            setTaskState(context, HiredWorkerTaskState.WAITING_FOR_MATERIALS, table);
            HiredWorkerBrain.setFailure(context, "missing_craftsman_materials", level.getGameTime() + 100L);
            return WorkResult.idle("interaction.work.craftsman.missing_materials");
        }
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
        int neededTicks = Math.max(1, Math.round(CRAFT_TICKS * 100.0F / context.skillWorkSpeedPercent()));
        int progress = context.progressTicks() + 1;
        context.setProgressTicks(progress);
        if (progress < neededTicks) {
            return WorkResult.progressed("interaction.work.craftsman.crafting");
        }
        context.setProgressTicks(0);
        if (!HiredSupplyCrafting.craftCarriedRecipeToOutputsWithStations(level, context, selection.recipe())) {
            setTaskState(context, HiredWorkerTaskState.PAUSED_FULL_INVENTORY, table);
            HiredWorkerBrain.setFailure(context, "craftsman_output_full", level.getGameTime() + 100L);
            return WorkResult.idle("interaction.work.craftsman.output_full");
        }
        advanceCursor(context.state(), targets, selection.target().slot());
        ItemStack result = selection.recipe().getResultItem(level.registryAccess());
        useWorkItem(level, villager, result);
        HiredWorkerBrain.clearFailure(context);
        return WorkResult.completedWithPractice(
                "interaction.work.craftsman.crafted",
                itemReplacements(result),
                HiredWorkPractice.batch(VillagerSkill.CRAFTING, "hired:craftsman:recipe",
                        result.getCount(), result.getItem().hashCode()));
    }

    private static Selection select(ServerLevel level, Villager villager, HiredWorkContext context, List<Target> targets) {
        Mode mode = mode(context.state());
        int start = mode == Mode.PREFER_FIRST ? 0 : indexAtOrAfter(targets, context.state().getInt(CURSOR_TAG));
        int attempts = mode == Mode.FORCED_ROUND_ROBIN ? 1 : targets.size();
        for (int offset = 0; offset < attempts; offset++) {
            Target target = targets.get((start + offset) % targets.size());
            for (RecipeHolder<CraftingRecipe> holder : level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
                CraftingRecipe recipe = holder.value();
                ItemStack result = recipe.getResultItem(level.registryAccess());
                if (recipe.isSpecial() || result.isEmpty() || !result.is(target.stack().getItem())
                        || !HiredSupplyCrafting.canUseRecipe(level, context, recipe)) {
                    continue;
                }
                Map<Item, Integer> materials = new LinkedHashMap<>();
                HiredSupplyCrafting.MaterialPlanner planner =
                        new HiredSupplyCrafting.MaterialPlanner(level, villager, context, false, true);
                if (planner.planRecipe(recipe, 1, materials)) {
                    return new Selection(target, recipe, Map.copyOf(materials));
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
            if (!context.isLoaded(level, pos) || !level.getBlockState(pos).is(Blocks.CRAFTING_TABLE)) {
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

    private static List<Target> targets(ItemStack filter) {
        if (filter.isEmpty()) {
            return List.of();
        }
        List<ItemStack> entries = VillagerItemFilterData.entries(filter);
        List<Target> targets = new ArrayList<>();
        for (int slot = 0; slot < entries.size(); slot++) {
            if (!entries.get(slot).isEmpty()) {
                targets.add(new Target(slot, entries.get(slot)));
            }
        }
        return List.copyOf(targets);
    }

    private static int indexAtOrAfter(List<Target> targets, int cursor) {
        for (int index = 0; index < targets.size(); index++) {
            if (targets.get(index).slot() >= Math.clamp(cursor, 0, VillagerItemFilterData.ENTRY_COUNT - 1)) {
                return index;
            }
        }
        return 0;
    }

    private static void advanceCursor(CompoundTag state, List<Target> targets, int completedSlot) {
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

    private record Target(int slot, ItemStack stack) {}
    private record Selection(Target target, CraftingRecipe recipe, Map<Item, Integer> materials) {}

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
