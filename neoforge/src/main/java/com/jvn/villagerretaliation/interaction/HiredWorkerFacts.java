package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.interaction.ClipboardWorkforceSnapshot.WorkerStatus;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerBrain;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerTaskState;
import com.jvn.villagerretaliation.interaction.work.brewing.BrewingWorker;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderBuildPhase;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderTaskState;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import net.minecraft.nbt.CompoundTag;

public record HiredWorkerFacts(WorkerStatus status, boolean inventoryFull, boolean noStorage,
        boolean missingMaterials, boolean materialStorageUnreachable, boolean materialInventoryFull,
        boolean buildSiteUnreachable, boolean waitingForCrops, boolean noTargets, boolean canBeBlocked,
        String blocker) {
    public static HiredWorkerFacts observe(HiredWorkSession session, HiredWorkerBrain.Snapshot brain) {
        var role = session.role();
        var state = session.state();
        boolean storageUnreachable = isMaterialStorageUnreachable(role, brain);
        boolean materialFull = isMaterialInventoryFull(role, brain);
        boolean siteUnreachable = isBuilderBuildSiteUnreachable(role, brain, state);
        boolean noStorage = brain.taskState() == HiredWorkerTaskState.PAUSED_NO_STORAGE && !storageUnreachable;
        boolean missingMaterials = isMissingMaterials(role, brain, state, noStorage, storageUnreachable);
        boolean inventoryFull = brain.taskState() == HiredWorkerTaskState.PAUSED_FULL_INVENTORY
                && !materialFull && !storageUnreachable && session.inventory().isCapacityBlockedForFailure(brain.failureReason());
        WorkerStatus status = status(role, brain.taskState(), brain.taskState() == HiredWorkerTaskState.PAUSED_STORAGE_FULL,
                inventoryFull, noStorage, brain.taskState() == HiredWorkerTaskState.NO_WORK_AREA,
                isNoTargetState(brain), false, false, brain.taskState() == HiredWorkerTaskState.PAUSED_MISSING_TOOL,
                missingMaterials, storageUnreachable, materialFull, siteUnreachable, isWaitingForCrops(role, brain));
        boolean canBeBlocked = switch (brain.taskState()) {
            case IDLE, AWAITING_INSTRUCTION, WAITING_FOR_MATERIALS, FAILED_COOLDOWN, NO_WORK_AREA,
                    PAUSED_MISSING_TOOL, PAUSED_FULL_INVENTORY, PAUSED_NO_STORAGE, PAUSED_STORAGE_FULL -> true;
            case SELECTING_TARGET -> brain.status().contains(".no_") || brain.status().contains(".missing_")
                    || brain.status().contains("unreachable");
            default -> false;
        };
        String blocker = brain.failureReason();
        if (blocker.isBlank() && role == HiredVillagerRole.BUILDER) blocker = BuilderTaskState.blockedReason(state);
        if (blocker.isBlank() && role == HiredVillagerRole.BREWING) blocker = BrewingWorker.blockedReason(state);
        return new HiredWorkerFacts(status, inventoryFull, noStorage, missingMaterials, storageUnreachable,
                materialFull, siteUnreachable, isWaitingForCrops(role, brain), isNoTargetState(brain),
                canBeBlocked, blocker);
    }

    public static WorkerStatus previewStatus(
            HiredVillagerRole role,
            HiredWorkerBrain.Snapshot brain,
            HiredJobInventory inventory) {
        if (role == null || brain == null || inventory == null) {
            return WorkerStatus.UNKNOWN;
        }
        return switch (brain.taskState()) {
            case MOVING_TO_TARGET, RETURNING_TO_WORK_AREA -> WorkerStatus.PATHING;
            case SELECTING_TARGET, FINDING_CHAIN_TARGET, VALIDATING_TARGET, WORKING, COLLECTING_OUTPUT ->
                    activeWorkStatus(role);
            case MOVING_TO_STORAGE, DEPOSITING -> WorkerStatus.DEPOSITING;
            case PAUSED_OUTPUT_BACKPRESSURE -> WorkerStatus.OUTPUT_BACKPRESSURE;
            case PAUSED_STORAGE_FULL -> WorkerStatus.STORAGE_FULL;
            case PAUSED_NO_STORAGE -> WorkerStatus.NO_STORAGE;
            case PAUSED_MISSING_TOOL -> WorkerStatus.MISSING_TOOLS;
            case NO_WORK_AREA -> WorkerStatus.NO_WORK_AREA;
            case PAUSED_FULL_INVENTORY -> inventory.isCapacityBlockedForFailure(brain.failureReason())
                    ? WorkerStatus.INVENTORY_FULL
                    : WorkerStatus.WAITING;
            case IDLE, WAITING_FOR_MATERIALS, FAILED_COOLDOWN, AWAITING_INSTRUCTION -> WorkerStatus.WAITING;
        };
    }

    private static WorkerStatus status(
            HiredVillagerRole role,
            HiredWorkerTaskState taskState,
            boolean storageFull,
            boolean inventoryFull,
            boolean noStorage,
            boolean noWorkArea,
            boolean noTargets,
            boolean unpaid,
            boolean tooFar,
            boolean missingTools,
            boolean missingMaterials,
            boolean materialStorageUnreachable,
            boolean materialInventoryFull,
            boolean buildSiteUnreachable,
            boolean waitingForCrops) {
        if (unpaid) {
            return WorkerStatus.UNPAID;
        }
        if (noWorkArea) {
            return WorkerStatus.NO_WORK_AREA;
        }
        if (tooFar) {
            return WorkerStatus.TOO_FAR;
        }
        if (materialInventoryFull) {
            return WorkerStatus.MATERIAL_INVENTORY_FULL;
        }
        if (storageFull) {
            return WorkerStatus.STORAGE_FULL;
        }
        if (inventoryFull) {
            return WorkerStatus.INVENTORY_FULL;
        }
        if (missingTools) {
            return WorkerStatus.MISSING_TOOLS;
        }
        if (materialStorageUnreachable) {
            return WorkerStatus.MATERIAL_STORAGE_UNREACHABLE;
        }
        if (noStorage) {
            return WorkerStatus.NO_STORAGE;
        }
        if (missingMaterials) {
            return WorkerStatus.MISSING_MATERIALS;
        }
        if (buildSiteUnreachable) {
            return WorkerStatus.BUILD_SITE_UNREACHABLE;
        }
        if (waitingForCrops) {
            return WorkerStatus.WAITING_FOR_CROPS;
        }
        if (noTargets) {
            return WorkerStatus.NO_TARGETS;
        }
        return switch (taskState) {
            case MOVING_TO_TARGET, RETURNING_TO_WORK_AREA -> WorkerStatus.PATHING;
            case SELECTING_TARGET, FINDING_CHAIN_TARGET, VALIDATING_TARGET -> activeWorkStatus(role);
            case MOVING_TO_STORAGE, DEPOSITING -> WorkerStatus.DEPOSITING;
            case WAITING_FOR_MATERIALS -> WorkerStatus.WAITING;
            case PAUSED_OUTPUT_BACKPRESSURE -> WorkerStatus.OUTPUT_BACKPRESSURE;
            case WORKING, COLLECTING_OUTPUT -> activeWorkStatus(role);
            case IDLE, AWAITING_INSTRUCTION, FAILED_COOLDOWN, PAUSED_MISSING_TOOL, PAUSED_FULL_INVENTORY ->
                    WorkerStatus.WAITING;
            default -> WorkerStatus.UNKNOWN;
        };
    }

    private static WorkerStatus activeWorkStatus(HiredVillagerRole role) {
        return switch (role) {
            case MINING -> WorkerStatus.MINING;
            case LOGGING -> WorkerStatus.LOGGING;
            case FARMING -> WorkerStatus.FARMING;
            case FISHING -> WorkerStatus.WORKING;
            case BREWING -> WorkerStatus.BREWING;
            case COOK -> WorkerStatus.COOKING;
            case SMELTER -> WorkerStatus.SMELTING;
            case COURIER -> WorkerStatus.COURIERING;
            case BUILDER -> WorkerStatus.BUILDING;
            default -> WorkerStatus.WORKING;
        };
    }

    private static boolean isNoTargetState(HiredWorkerBrain.Snapshot brain) {
        String failure = lower(brain.failureReason());
        String scan = lower(brain.lastTargetScanResult());
        if (brain.taskState() == HiredWorkerTaskState.FAILED_COOLDOWN) {
            return failure.contains("target_unreachable") || failure.contains("no_target");
        }
        if (brain.taskState() != HiredWorkerTaskState.IDLE
                && brain.taskState() != HiredWorkerTaskState.SELECTING_TARGET
                && brain.taskState() != HiredWorkerTaskState.AWAITING_INSTRUCTION) {
            return false;
        }
        return scan.contains("no_reachable_targets") || scan.contains("no_targets");
    }

    private static boolean isWaitingForCrops(HiredVillagerRole role, HiredWorkerBrain.Snapshot brain) {
        return role == HiredVillagerRole.FARMING
                && (brain.taskState() == HiredWorkerTaskState.IDLE
                || brain.taskState() == HiredWorkerTaskState.SELECTING_TARGET
                || brain.taskState() == HiredWorkerTaskState.AWAITING_INSTRUCTION)
                && lower(brain.lastTargetScanResult()).contains("waiting_for_crops");
    }

    private static boolean isMissingMaterials(
            HiredVillagerRole role,
            HiredWorkerBrain.Snapshot brain,
            CompoundTag state,
            boolean noStorage,
            boolean materialStorageUnreachable) {
        if (brain.taskState() != HiredWorkerTaskState.WAITING_FOR_MATERIALS) {
            return false;
        }
        if (role == HiredVillagerRole.CRAFTSMAN) {
            return !noStorage
                    && !materialStorageUnreachable
                    && lower(brain.failureReason()).contains("missing_craftsman_materials");
        }
        if (role == HiredVillagerRole.BREWING) {
            if (noStorage || materialStorageUnreachable) {
                return false;
            }
            String reason = lower(brain.failureReason() + " " + BrewingWorker.blockedReason(state));
            return reason.contains("missing_brewing_materials")
                    || reason.contains("interaction.work.brewing.missing_materials");
        }
        if (role == HiredVillagerRole.COOK) {
            if (noStorage || materialStorageUnreachable) {
                return false;
            }
            String reason = lower(brain.failureReason());
            return reason.contains("missing_cooking_raw_food")
                    || reason.contains("missing_cooking_fuel")
                    || reason.contains("missing_cooking_crafting_materials")
                    || reason.contains("interaction.work.cooking.missing_raw_food")
                    || reason.contains("interaction.work.cooking.missing_fuel")
                    || reason.contains("interaction.work.cooking.missing_crafting_materials");
        }
        if (role == HiredVillagerRole.SMELTER) {
            if (noStorage || materialStorageUnreachable) {
                return false;
            }
            String reason = lower(brain.failureReason());
            return reason.contains("missing_smelting_raw_ore")
                    || reason.contains("missing_smelting_fuel")
                    || reason.contains("interaction.work.smelting.missing_raw_ore")
                    || reason.contains("interaction.work.smelting.missing_fuel");
        }
        if (role == HiredVillagerRole.COURIER) {
            return !noStorage && lower(brain.failureReason()).contains("courier_input_empty");
        }
        if (role == HiredVillagerRole.MINING) {
            return !noStorage
                    && !materialStorageUnreachable
                    && (lower(brain.failureReason()).contains("missing_ladders")
                    || lower(brain.failureReason()).contains("missing_hazard_fill_blocks"));
        }
        if (role != HiredVillagerRole.BUILDER || noStorage || materialStorageUnreachable) {
            return false;
        }
        String failure = lower(brain.failureReason());
        return BuilderTaskState.phase(state) == BuilderBuildPhase.WAITING_FOR_MATERIALS
                && !failure.contains("storage_too_far")
                && !failure.contains("storage_unreachable")
                && !failure.contains("output_storage_unreachable")
                && (failure.isBlank() || failure.equals("missing_builder_materials"));
    }

    private static boolean isMaterialStorageUnreachable(
            HiredVillagerRole role,
            HiredWorkerBrain.Snapshot brain) {
        String failure = lower(brain.failureReason());
        if (role == HiredVillagerRole.BUILDER
                && brain.taskState() == HiredWorkerTaskState.WAITING_FOR_MATERIALS
                && failure.equals("missing_builder_materials_storage_too_far")) {
            return true;
        }
        if (brain.taskState() != HiredWorkerTaskState.FAILED_COOLDOWN
                && brain.taskState() != HiredWorkerTaskState.PAUSED_FULL_INVENTORY
                && brain.taskState() != HiredWorkerTaskState.PAUSED_NO_STORAGE) {
            return false;
        }
        return switch (role) {
            case BREWING -> failure.contains("brewing_storage_path_failed");
            case CRAFTSMAN -> failure.contains("craftsman_storage_path_failed");
            case COOK -> failure.contains("cooking_storage_path_failed");
            case SMELTER -> failure.contains("smelting_storage_path_failed");
            case COURIER -> failure.contains("courier_input_unreachable")
                    || failure.contains("courier_output_unreachable");
            case BUILDER -> failure.equals("builder_material_storage_unreachable")
                    || failure.equals("missing_builder_materials_storage_unreachable")
                    || failure.equals("builder_material_output_storage_unreachable");
            case MINING -> failure.contains("mining_support_storage_path_failed")
                    || failure.contains("hazard_fill_storage_unreachable");
            default -> false;
        };
    }

    private static boolean isMaterialInventoryFull(
            HiredVillagerRole role,
            HiredWorkerBrain.Snapshot brain) {
        if (brain.taskState() != HiredWorkerTaskState.PAUSED_FULL_INVENTORY) {
            return false;
        }
        String failure = lower(brain.failureReason());
        return switch (role) {
            case BREWING -> failure.contains("brewing_material_inventory_full")
                    || failure.contains("brewing_water_bottle_space")
                    || failure.contains("brewing_output_full_after_brew");
            case CRAFTSMAN -> failure.contains("craftsman_material_inventory_full");
            case COOK -> failure.contains("cooking_material_inventory_full");
            case SMELTER -> failure.contains("smelting_material_inventory_full");
            case BUILDER -> failure.contains("builder_material_inventory_full")
                    || failure.contains("builder_material_output_slot_full");
            case MINING -> failure.contains("hazard_fill_inventory_full");
            default -> false;
        };
    }

    private static boolean isBuilderBuildSiteUnreachable(
            HiredVillagerRole role,
            HiredWorkerBrain.Snapshot brain,
            CompoundTag state) {
        if (role != HiredVillagerRole.BUILDER) {
            return false;
        }
        if (brain.taskState() != HiredWorkerTaskState.FAILED_COOLDOWN) {
            return false;
        }
        String failure = lower(brain.failureReason());
        String blocked = lower(BuilderTaskState.blockedReason(state));
        return failure.contains("path_blocked") || blocked.contains("path_blocked");
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(java.util.Locale.ROOT);
    }

}
