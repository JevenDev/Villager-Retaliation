package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.interaction.ClipboardWorkforceSnapshot.WarningSummary;
import com.jvn.villagerretaliation.interaction.ClipboardWorkforceSnapshot.WarningType;
import com.jvn.villagerretaliation.interaction.ClipboardWorkforceSnapshot.WorkerRow;
import com.jvn.villagerretaliation.interaction.ClipboardWorkforceSnapshot.WorkerStatus;
import com.jvn.villagerretaliation.interaction.work.brewing.BrewingWorker;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderBuildPhase;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderTaskState;
import com.jvn.villagerretaliation.interaction.work.HiredRangedAmmo;
import com.jvn.villagerretaliation.interaction.work.HiredWorkContext;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerBrain;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerTaskState;
import com.jvn.villagerretaliation.interaction.work.HiredHuntingTargets;
import com.jvn.villagerretaliation.interaction.work.logging.LoggingWorker;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.network.ClipboardWorkforceSyncPayload;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import com.jvn.villagerretaliation.util.WorldLocation;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ClipboardWorkforceService {
    private ClipboardWorkforceService() {
    }

    public static void openClipboard(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new ClipboardWorkforceSyncPayload(snapshot(player)));
    }

    public static ClipboardWorkforceSnapshot snapshot(ServerPlayer player) {
        if (player == null || player.server == null) {
            return ClipboardWorkforceSnapshot.empty();
        }

        Map<HiredVillagerRole, Integer> jobCounts = new EnumMap<>(HiredVillagerRole.class);
        Map<WarningKey, Integer> warningCounts = new java.util.LinkedHashMap<>();
        List<WorkerRow> workers = new ArrayList<>();
        int working = 0;
        int idle = 0;
        int assignedStorage = 0;
        int paymentContainers = 0;
        int dailyWages = 0;

        HiredVillagerIndex.reconcileLoadedFor(player);
        for (HiredVillagerIndex.Target target : HiredVillagerIndex.targetsFor(player)) {
            ServerLevel level = target.level();
            Villager villager = target.villager();

                HiredWorkSession session = HiredWorkSession.active(level, villager);
                HiredVillagerRole role = session.role();
                HiredWorkerBrain.Snapshot brain = HiredWorkerBrain.snapshot(session.state(), level.getGameTime());
                boolean routeAssigned = HiredVillagerWorkService.usesRouteAssignment(role, session.context());
                boolean hasEffectiveWorkAssignment = HiredVillagerWorkService.hasEffectiveWorkArea(level, villager, session);
                int storageCount = AssignedStorageService.assignedStorage(level, villager).size();
                int paymentStorageCount = AssignedStorageService.assignedPaymentStorage(level, villager).size();
                boolean storageAssigned = storageCount > 0;
                boolean storageFull = brain.taskState() == HiredWorkerTaskState.PAUSED_STORAGE_FULL;
                boolean taskInventoryFull = brain.taskState() == HiredWorkerTaskState.PAUSED_FULL_INVENTORY;
                boolean taskHasNoStorage = brain.taskState() == HiredWorkerTaskState.PAUSED_NO_STORAGE;
                boolean requiresWorkAssignment = requiresWorkAssignment(role);
                boolean noWorkArea = requiresWorkAssignment
                        && !hasEffectiveWorkAssignment;
                boolean waitingForCrops = !noWorkArea && isWaitingForCrops(role, brain);
                boolean noTargets = !noWorkArea && !waitingForCrops && isNoTargetState(brain);
                boolean tooFar = requiresWorkAssignment
                        && !noWorkArea
                        && !isExpectedWorkExcursion(brain.taskState())
                        && !HiredVillagerWorkService.isInsideEffectiveWorkArea(level, villager, role, session.context(), villager.blockPosition());
                boolean missingTools = brain.taskState() == HiredWorkerTaskState.PAUSED_MISSING_TOOL;
                boolean materialStorageUnreachable = isMaterialStorageUnreachable(role, brain);
                boolean materialInventoryFull = isMaterialInventoryFull(role, brain);
                boolean buildSiteUnreachable = isBuilderBuildSiteUnreachable(role, brain, session.state());
                boolean noStorage = taskHasNoStorage && !materialStorageUnreachable;
                boolean missingMaterials = isMissingMaterials(role, brain, session.state(), noStorage, materialStorageUnreachable);
                boolean inventoryFull = taskInventoryFull
                        && !materialInventoryFull
                        && !materialStorageUnreachable
                        && session.inventory().isCapacityBlockedForFailure(brain.failureReason());
                int dailyWage = HiredVillagerContractService.getContractDailyCost(level, villager, player);
                int contractDays = HiredVillagerContractService.getRemainingHireDays(level, villager);
                boolean recurringPayment = HiredVillagerContractService.isAutoPaymentEnabled(level, villager);
                boolean unpaid = HiredVillagerContractService.isAwaitingAutoPayment(level, villager);
                boolean workerIsWorking = isWorking(brain.taskState(), session.state().getBoolean("Enabled"));
                WorkerStatus status = status(
                        role,
                        brain.taskState(),
                        storageFull,
                        inventoryFull,
                        noStorage,
                        noWorkArea,
                        noTargets,
                        unpaid,
                        tooFar,
                        missingTools,
                        missingMaterials,
                        materialStorageUnreachable,
                        materialInventoryFull,
                        buildSiteUnreachable,
                        waitingForCrops);
                String diagnostic = workerDiagnostic(
                        role,
                        brain,
                        session.context(),
                        session.state(),
                        inventoryFull,
                        noStorage,
                        missingMaterials,
                        materialStorageUnreachable,
                        materialInventoryFull,
                        buildSiteUnreachable);

                jobCounts.merge(role, 1, Integer::sum);
                assignedStorage += storageCount;
                paymentContainers += paymentStorageCount;
                dailyWages += dailyWage;
                if (workerIsWorking) {
                    working++;
                } else {
                    idle++;
                }
                addWarning(warningCounts, WarningType.NO_STORAGE, role, noStorage);
                addWarning(warningCounts, WarningType.STORAGE_FULL, role, storageFull);
                addWarning(warningCounts, WarningType.INVENTORY_FULL, role, inventoryFull);
                addWarning(warningCounts, WarningType.NO_WORK_AREA, role, noWorkArea);
                addWarning(warningCounts, WarningType.NO_TARGETS, role, noTargets);
                addWarning(warningCounts, WarningType.TOO_FAR, role, tooFar);
                addWarning(warningCounts, WarningType.MISSING_TOOLS, role, missingTools);
                addWarning(warningCounts, WarningType.MISSING_MATERIALS, role, missingMaterials);
                addWarning(warningCounts, WarningType.MATERIAL_STORAGE_UNREACHABLE, role, materialStorageUnreachable);
                addWarning(warningCounts, WarningType.MATERIAL_INVENTORY_FULL, role, materialInventoryFull);
                addWarning(warningCounts, WarningType.BUILD_SITE_UNREACHABLE, role, buildSiteUnreachable);
                addWarning(warningCounts, WarningType.UNPAID, role, unpaid);
                workers.add(new WorkerRow(
                        villager.getUUID(),
                        VillagerPresetNameRegistry.resolveDisplayName(villager).getString(),
                        role,
                        status,
                        targetText(brain),
                        diagnostic,
                        storageAssigned,
                        storageCount,
                        routeAssigned ? session.route().nodes().size() : session.area().horizontalRadius(),
                        routeAssigned || session.area().usable(),
                        routeAssigned ? routeDescription(session.route()) : session.area().centerDescription(),
                        routeAssigned ? session.route().nodes().size() : session.area().horizontalRadius(),
                        routeAssigned ? 0 : session.area().verticalRadius(),
                        routeAssigned ? "route" : session.area().usable() ? session.jobSite().sourceId() : "missing",
                        workModeText(role, session.state()),
                        dailyWage,
                        VillagerCurrencyResources.format(level.getServer(), dailyWage),
                        VillagerCurrencyResources.text(level.getServer()).textColor(),
                        contractDays,
                        recurringPayment,
                        workerIsWorking,
                        WorldLocation.of(level.dimension(), villager.blockPosition()),
                        storageFull,
                        missingMaterials,
                        materialStorageUnreachable,
                        materialInventoryFull,
                        buildSiteUnreachable,
                        inventoryFull,
                        unpaid,
                        noStorage,
                        noWorkArea,
                        noTargets,
                        tooFar,
                        missingTools
                ));
        }

        List<ClipboardWorkforceSnapshot.JobSummary> jobs = new ArrayList<>();
        for (HiredVillagerRole role : HiredVillagerRole.values()) {
            jobs.add(new ClipboardWorkforceSnapshot.JobSummary(role, jobCounts.getOrDefault(role, 0)));
        }
        List<WarningSummary> warnings = warningCounts.entrySet().stream()
                .map(entry -> new WarningSummary(entry.getKey().type(), entry.getKey().role(), entry.getValue()))
                .toList();
        return new ClipboardWorkforceSnapshot(
                workers.size(),
                -1,
                working,
                idle,
                warnings.stream().mapToInt(WarningSummary::count).sum(),
                assignedStorage,
                paymentContainers,
                dailyWages,
                jobs,
                workers,
                warnings
        );
    }

    private static boolean isWorking(HiredWorkerTaskState state, boolean enabled) {
        if (!enabled) {
            return false;
        }
        return !state.isWaitingState();
    }

    private static boolean isExpectedWorkExcursion(HiredWorkerTaskState state) {
        return state.keepsStorageTarget() || state == HiredWorkerTaskState.RETURNING_TO_WORK_AREA;
    }

    private static boolean requiresWorkAssignment(HiredVillagerRole role) {
        return role != HiredVillagerRole.BUILDER && role != HiredVillagerRole.NITWIT;
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

    /** Lightweight status used by persistent world markers between clipboard snapshots. */
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

    private static String workerDiagnostic(
            HiredVillagerRole role,
            HiredWorkerBrain.Snapshot brain,
            HiredWorkContext context,
            CompoundTag state,
            boolean inventoryFull,
            boolean noStorage,
            boolean missingMaterials,
            boolean materialStorageUnreachable,
            boolean materialInventoryFull,
            boolean buildSiteUnreachable) {
        if (role == HiredVillagerRole.BREWING) {
            return brewingDiagnostic(
                    brain,
                    state,
                    inventoryFull,
                    noStorage,
                    missingMaterials,
                    materialStorageUnreachable,
                    materialInventoryFull);
        }
        if (role == HiredVillagerRole.COOK) {
            return cookingDiagnostic(
                    brain,
                    inventoryFull,
                    noStorage,
                    missingMaterials,
                    materialStorageUnreachable,
                    materialInventoryFull);
        }
        if (role == HiredVillagerRole.SMELTER) {
            return smeltingDiagnostic(
                    brain,
                    inventoryFull,
                    noStorage,
                    missingMaterials,
                    materialStorageUnreachable,
                    materialInventoryFull);
        }
        if (role == HiredVillagerRole.COURIER) {
            return courierDiagnostic(brain, inventoryFull, noStorage);
        }
        if (role == HiredVillagerRole.LOGGING) {
            return loggingDiagnostic(brain, context, inventoryFull, noStorage);
        }
        if (role == HiredVillagerRole.MINING) {
            return miningDiagnostic(brain, inventoryFull, noStorage, missingMaterials, materialStorageUnreachable);
        }
        if (role == HiredVillagerRole.FARMING) {
            return farmingDiagnostic(brain);
        }
        if (role == HiredVillagerRole.FISHING) {
            return fishingDiagnostic(brain);
        }
        String rangedAmmo = rangedAmmoDiagnostic(brain, role);
        if (!rangedAmmo.isBlank()) {
            return rangedAmmo;
        }
        if (role == HiredVillagerRole.HUNTING) {
            return huntingDiagnostic(brain);
        }
        if (role != HiredVillagerRole.BUILDER) {
            return "";
        }

        String missing = BuilderTaskState.missingMaterials(state);
        String reason = lower(brain.failureReason() + " " + BuilderTaskState.blockedReason(state));
        String tool = toolLabelFromFailure(reason, "tool");
        if (materialInventoryFull) {
            if (lower(brain.failureReason()).contains("builder_material_output_storage_unreachable")) {
                BlockPos storagePos = diagnosticStoragePos(brain);
                return limitDiagnostic(storagePos == null
                        ? "Builder needs to dump excavation output before restocking, but cannot reach assigned output storage."
                        : "Builder needs to dump excavation output before restocking, but cannot path to output storage at "
                                + HiredWorkerBrain.formatPos(storagePos) + ".");
            }
            if (lower(brain.failureReason()).contains("builder_material_output_slot_full")) {
                return limitDiagnostic(missing.isBlank()
                        ? "Required construction blocks are in output slots, but the builder cannot free a supply slot for them."
                        : "Required construction blocks are in output slots, but the builder cannot free a supply slot for: "
                                + missing + ".");
            }
            return limitDiagnostic(missing.isBlank()
                    ? "Builder cannot fit the next construction batch after dumping removable excavation output."
                    : "Builder cannot fit the next construction batch after dumping output. Still needs room for: "
                            + missing + ".");
        }
        if (materialStorageUnreachable) {
            BlockPos storagePos = diagnosticStoragePos(brain);
            return limitDiagnostic(storagePos == null
                    ? "Assigned storage contains the needed construction blocks, but the builder cannot path to that container."
                    : "Assigned storage at " + HiredWorkerBrain.formatPos(storagePos)
                            + " contains the needed construction blocks, but the builder cannot path to it.");
        }
        if (missingMaterials) {
            if (lower(brain.failureReason()).contains("missing_builder_materials_storage_too_far")) {
                int radius = Math.max(1, VillagerRetaliationConfig.HIRED_BUILDER_MATERIAL_STORAGE_RADIUS.get());
                return limitDiagnostic(missing.isBlank()
                        ? "Builder needs assigned construction-block storage within " + radius + " blocks of the build site."
                        : "Builder needs assigned construction-block storage within " + radius + " blocks of the build site for: "
                                + missing + ".");
            }
            if (lower(brain.failureReason()).contains("missing_builder_materials_storage_unreachable")) {
                return limitDiagnostic(missing.isBlank()
                        ? missingMaterialStorageWaitDiagnostic(brain)
                        : "No reachable assigned storage contains the next construction batch: " + missing
                                + ". " + missingMaterialStorageWaitDiagnostic(brain));
            }
            return limitDiagnostic(missing.isBlank()
                    ? "No assigned storage currently contains the next construction batch."
                    : "No assigned storage currently contains the next construction batch: " + missing + ".");
        }
        if (buildSiteUnreachable) {
            return limitDiagnostic(brain.targetPos() == null
                    ? "Builder has construction work queued but cannot path back to the build site."
                    : "Builder cannot path back to the next construction spot at "
                            + HiredWorkerBrain.formatPos(brain.targetPos()) + ".");
        }
        if (reason.contains("tool_storage_unreachable")) {
            BlockPos storagePos = diagnosticStoragePos(brain);
            return limitDiagnostic(storagePos == null
                    ? "Builder found the " + tool + " in assigned tool storage, but cannot path to that container."
                    : "Builder found the " + tool + " in assigned tool storage at "
                            + HiredWorkerBrain.formatPos(storagePos) + ", but cannot path to it.");
        }
        if (reason.contains("tool_inventory_full")) {
            return "Builder found the " + tool + ", but cannot fit it in job gear.";
        }
        if (reason.contains("missing_clear_tool")) {
            return limitDiagnostic(brain.targetPos() == null
                    ? "Builder needs a suitable " + tool + " to clear an obstructing block."
                    : "Builder needs a suitable " + tool + " to clear the block at "
                            + HiredWorkerBrain.formatPos(brain.targetPos()) + ".");
        }
        if (reason.contains("missing_placement_tool")) {
            return limitDiagnostic(brain.targetPos() == null
                    ? "Builder needs a suitable " + tool + " to prepare the next construction block."
                    : "Builder needs a suitable " + tool + " to prepare the construction block at "
                            + HiredWorkerBrain.formatPos(brain.targetPos()) + ".");
        }
        if (isBuilderRestockingBatch(brain, state) && !missing.isBlank()) {
            BlockPos storagePos = diagnosticStoragePos(brain);
            return limitDiagnostic(storagePos == null
                    ? "Builder is restocking the current construction batch. Still needs: " + missing + "."
                    : "Builder is restocking the current construction batch at "
                            + HiredWorkerBrain.formatPos(storagePos) + ". Still needs: " + missing + ".");
        }
        if (noStorage) {
            return limitDiagnostic(missing.isBlank()
                    ? "Builder needs assigned storage before it can restock missing construction blocks."
                    : "Builder needs assigned storage before it can restock: " + missing + ".");
        }
        if (inventoryFull) {
            return "Inventory is full and there is no output room for builder leftovers.";
        }

        if (reason.contains("blocked_entity")) {
            return "A mob, player, or villager is standing inside the next planned block space.";
        }
        if (reason.contains("blocked_existing")) {
            return "The next planned block space contains something the builder will not replace.";
        }
        if (reason.contains("blocked_support")) {
            return "The next planned block needs support before it can be placed.";
        }
        if (reason.contains("placement_failed")) {
            return "The next construction block failed to place; check collision, support, or protection at the target.";
        }
        return "";
    }

    private static String miningDiagnostic(
            HiredWorkerBrain.Snapshot brain,
            boolean inventoryFull,
            boolean noStorage,
            boolean missingMaterials,
            boolean materialStorageUnreachable) {
        String reason = lower(brain.failureReason());
        if (reason.contains("hazard_fill_storage_unreachable")) {
            BlockPos storagePos = diagnosticStoragePos(brain);
            return storagePos == null
                    ? "Assigned storage contains sturdy hazard-control blocks, but the miner cannot path to it."
                    : "Assigned storage at " + HiredWorkerBrain.formatPos(storagePos)
                            + " contains hazard-control blocks, but the miner cannot path to it.";
        }
        if (materialStorageUnreachable) {
            BlockPos storagePos = diagnosticStoragePos(brain);
            return limitDiagnostic(storagePos == null
                    ? "Assigned storage contains excavation supports, but the miner cannot path to that container."
                    : "Assigned storage at " + HiredWorkerBrain.formatPos(storagePos)
                            + " contains excavation supports, but the miner cannot path to it.");
        }
        if (reason.contains("missing_hazard_fill_blocks")) {
            return "Miner needs sturdy non-falling blocks in job supplies or reachable assigned storage to contain lava, water, or a drop.";
        }
        if (reason.contains("hazard_fill_inventory_full")) {
            return "Miner has no inventory room for the sturdy blocks needed to contain a hazard.";
        }
        if (reason.contains("hazard_placement_unreachable") || reason.contains("hazard_chunk_unloaded")) {
            return "Miner found a dangerous excavation face but cannot reach a safe position to contain it.";
        }
        if (missingMaterials || reason.contains("missing_ladders")) {
            return "Miner needs ladders in job supplies or reachable assigned storage before digging lower excavation layers.";
        }
        if (reason.contains("tool_storage_unreachable")) {
            return "Miner cannot path to assigned tool storage for a suitable " + toolLabelFromFailure(reason, "pickaxe") + ".";
        }
        if (reason.contains("tool_inventory_full")) {
            return "Miner found the " + toolLabelFromFailure(reason, "pickaxe") + ", but cannot fit it in job gear.";
        }
        if (reason.contains("missing_")) {
            return "Miner needs a suitable " + toolLabelFromFailure(reason, "pickaxe") + " before mining can continue.";
        }
        if (inventoryFull) {
            return noStorage
                    ? "Miner inventory is full and no assigned output storage is available."
                    : "Miner inventory is full and it is trying to deposit mined output.";
        }
        return "";
    }

    private static String loggingDiagnostic(
            HiredWorkerBrain.Snapshot brain,
            HiredWorkContext context,
            boolean inventoryFull,
            boolean noStorage) {
        String reason = lower(brain.failureReason());
        String scan = lower(brain.lastTargetScanResult());
        String summary = LoggingWorker.debugSummary(context);
        if (reason.contains("missing_axe")) {
            return "Logger needs an axe before it can cut trees.";
        }
        if (reason.contains("tool_storage_unreachable")) {
            return "Logger cannot path to assigned tool storage for an axe.";
        }
        if (reason.contains("tool_inventory_full")) {
            return "Logger found an axe in storage, but its job inventory has no tool slot free.";
        }
        if (reason.contains("pending_tree_unreachable")) {
            return limitDiagnostic("Logger has a tree harvest queued but cannot path back to the remaining blocks. " + summary);
        }
        if (reason.contains("access_leaf_unreachable") || reason.contains("access_leaf_blocked") || reason.contains("leaf_blocked_target")) {
            return limitDiagnostic("Logger is clearing leaves toward a tree but cannot reach the next blocking leaf. " + summary);
        }
        if (reason.contains("target_changed")) {
            return "Logger's selected tree changed before it could finish; it will choose another target shortly.";
        }
        if (reason.contains("decay_drop_unreachable")) {
            return "Logger found tree drops on the ground but cannot path to them.";
        }
        if (inventoryFull) {
            return noStorage
                    ? "Logger inventory is full and no assigned output storage is available."
                    : "Logger inventory is full and it is trying to deposit timber.";
        }
        if (scan.contains("tree_access_leaf")) {
            return limitDiagnostic("Logger is looking for reachable leaves to clear a path to a tree. " + summary);
        }
        if (scan.contains("tree_scan_full_no_reachable_targets") || scan.contains("tree_scan_cooldown")) {
            return limitDiagnostic("Logger did not find a reachable valid tree in the assigned area. " + summary);
        }
        if (summary.contains("pending logs=")) {
            return limitDiagnostic(summary);
        }
        return "";
    }

    private static String farmingDiagnostic(HiredWorkerBrain.Snapshot brain) {
        String reason = lower(brain.failureReason());
        if (reason.contains("farming_hoe_storage_unreachable")) {
            BlockPos storagePos = diagnosticStoragePos(brain);
            return storagePos == null
                    ? "Farmer found a hoe in assigned tool storage, but cannot path to that container."
                    : "Farmer found a hoe in assigned tool storage at "
                            + HiredWorkerBrain.formatPos(storagePos) + ", but cannot path to it.";
        }
        if (reason.contains("farming_hoe_inventory_full")) {
            return "Farmer found a hoe, but cannot fit it in job gear.";
        }
        if (reason.contains("missing_hoe")) {
            return "Farmer needs a hoe before field work can continue.";
        }
        return "";
    }

    private static String fishingDiagnostic(HiredWorkerBrain.Snapshot brain) {
        String reason = lower(brain.failureReason());
        if (reason.contains("fishing_rod_storage_unreachable")) {
            BlockPos storagePos = diagnosticStoragePos(brain);
            return storagePos == null
                    ? "Fisher found a fishing rod in assigned tool storage, but cannot path to that container."
                    : "Fisher found a fishing rod in assigned tool storage at "
                            + HiredWorkerBrain.formatPos(storagePos) + ", but cannot path to it.";
        }
        if (reason.contains("fishing_rod_inventory_full")) {
            return "Fisher found a fishing rod, but cannot fit it in job gear.";
        }
        if (reason.contains("missing_fishing_rod")) {
            return "Fisher needs a fishing rod before fishing can continue.";
        }
        return "";
    }

    private static String rangedAmmoDiagnostic(HiredWorkerBrain.Snapshot brain, HiredVillagerRole role) {
        if (role != HiredVillagerRole.COMBAT && role != HiredVillagerRole.HUNTING) {
            return "";
        }
        String reason = lower(brain.failureReason());
        String worker = role == HiredVillagerRole.HUNTING ? "Hunter" : "Guard";
        if (reason.contains(HiredRangedAmmo.FAILURE_STORAGE_PATH)) {
            BlockPos storagePos = diagnosticStoragePos(brain);
            return storagePos == null
                    ? worker + " found arrows in assigned storage, but cannot path to that container."
                    : worker + " found arrows in assigned storage at "
                            + HiredWorkerBrain.formatPos(storagePos) + ", but cannot path to it.";
        }
        if (reason.contains(HiredRangedAmmo.FAILURE_INVENTORY_FULL)) {
            return worker + " found arrows, but cannot fit them in job supplies.";
        }
        if (reason.contains(HiredRangedAmmo.FAILURE_MISSING)) {
            return worker + " needs arrows before using a bow or crossbow for hired work.";
        }
        return "";
    }

    private static String huntingDiagnostic(HiredWorkerBrain.Snapshot brain) {
        String reason = lower(brain.failureReason());
        if (reason.contains("hunting_loot_unreachable")) {
            return "Hunter found loot from the hunt but cannot path to it.";
        }
        if (reason.contains("output_inventory_full")) {
            return "Hunter found hunting loot to collect, but its job output is full.";
        }
        if (reason.contains("tool_storage_unreachable_hunting_weapon")) {
            BlockPos storagePos = diagnosticStoragePos(brain);
            return storagePos == null
                    ? "Hunter found a bow, crossbow, axe, or sword in assigned tool storage, but cannot path to that container."
                    : "Hunter found a bow, crossbow, axe, or sword in assigned tool storage at "
                            + HiredWorkerBrain.formatPos(storagePos) + ", but cannot path to it.";
        }
        if (reason.contains("tool_inventory_full_hunting_weapon")) {
            return "Hunter found a bow, crossbow, axe, or sword, but cannot fit it in job gear.";
        }
        if (reason.contains("hunting_arrow_storage_path_failed")) {
            BlockPos storagePos = diagnosticStoragePos(brain);
            return storagePos == null
                    ? "Hunter found arrows in assigned storage, but cannot path to that container."
                    : "Hunter found arrows in assigned storage at "
                            + HiredWorkerBrain.formatPos(storagePos) + ", but cannot path to it.";
        }
        if (reason.contains("hunting_arrow_inventory_full")) {
            return "Hunter found arrows, but cannot fit them in job supplies.";
        }
        if (reason.contains("missing_hunting_arrows")) {
            return "Hunter needs arrows before hunting with a bow or crossbow.";
        }
        if (reason.contains("missing_hunting_weapon")) {
            return "Hunter needs a bow, crossbow, axe, or sword before hunting can continue.";
        }
        return "";
    }

    private static String brewingDiagnostic(
            HiredWorkerBrain.Snapshot brain,
            CompoundTag state,
            boolean inventoryFull,
            boolean noStorage,
            boolean missingMaterials,
            boolean materialStorageUnreachable,
            boolean materialInventoryFull) {
        String reason = lower(brain.failureReason() + " " + BrewingWorker.blockedReason(state));
        String missing = BrewingWorker.missingMaterials(state);
        if (materialInventoryFull) {
            return limitDiagnostic(missing == null || missing.isBlank()
                    ? "Brewer needs room in the job inventory before it can carry brewing materials."
                    : "Brewer needs room in the job inventory for: " + missing + ".");
        }
        if (materialStorageUnreachable) {
            BlockPos storagePos = diagnosticStoragePos(brain);
            return limitDiagnostic(storagePos == null
                    ? "Brewer found required materials in assigned input storage, but cannot path to that container."
                    : "Brewer found required materials in assigned input storage at "
                            + HiredWorkerBrain.formatPos(storagePos) + ", but cannot path to it.");
        }
        if (missingMaterials) {
            return limitDiagnostic(missing == null || missing.isBlank()
                    ? "Brewer is missing materials for the current potion order."
                    : "Brewer is missing brewing materials: " + missing + ".");
        }
        if (noStorage && reason.contains("missing_brewing")) {
            return "Brewer needs assigned input storage or carried supplies for the current potion order.";
        }
        if (reason.contains("no_brewing_stand")) {
            return "Brewer needs a brewing stand inside the assigned work area.";
        }
        if (reason.contains("brewing_stand_path_failed") || reason.contains("brewing_stand_unreachable")) {
            return "Brewer cannot path to the brewing stand inside the assigned work area.";
        }
        if (reason.contains("brewing_water_source_path_failed") || reason.contains("brewing_water_source_unreachable")) {
            return "Brewer cannot path to the water source for filling bottles.";
        }
        if (reason.contains("brewing_stand_wrong_ingredient")) {
            return "Brewing stand has an ingredient that does not match the active order.";
        }
        if (reason.contains("brewing_stand_wrong_fuel")) {
            return "Brewing stand fuel slot has something other than blaze powder.";
        }
        if (reason.contains("brewing_stand_blocked")) {
            return "Brewing stand bottle slots contain potions from another recipe.";
        }
        if (inventoryFull) {
            return "Brewer inventory is full and output storage cannot take more items right now.";
        }
        return "";
    }

    private static String cookingDiagnostic(
            HiredWorkerBrain.Snapshot brain,
            boolean inventoryFull,
            boolean noStorage,
            boolean missingMaterials,
            boolean materialStorageUnreachable,
            boolean materialInventoryFull) {
        String reason = lower(brain.failureReason());
        if (materialInventoryFull) {
            return "Cook found food or fuel in assigned storage, but the job inventory has no room for it.";
        }
        if (materialStorageUnreachable) {
            BlockPos storagePos = diagnosticStoragePos(brain);
            return limitDiagnostic(storagePos == null
                    ? "Cook found food or fuel in assigned input storage, but cannot path to that container."
                    : "Cook found food or fuel in assigned input storage at "
                            + HiredWorkerBrain.formatPos(storagePos) + ", but cannot path to it.");
        }
        if (missingMaterials) {
            if (reason.contains("missing_cooking_fuel")) {
                return "Cook needs furnace fuel in job supplies or assigned input storage.";
            }
            if (reason.contains("missing_cooking_crafting_materials")) {
                return "Cook needs recipe ingredients for the filtered food in job supplies or assigned input storage.";
            }
            return "Cook needs raw food in job supplies or assigned input storage.";
        }
        if (noStorage && (reason.contains("missing_cooking_raw_food")
                || reason.contains("missing_cooking_fuel")
                || reason.contains("missing_cooking_crafting_materials"))) {
            return "Cook needs assigned input storage or carried supplies for food and fuel.";
        }
        if (reason.contains("no_cooking_crafting_table")) {
            return "Cook needs a crafting table inside the assigned work area for the filtered food recipe.";
        }
        if (reason.contains("cooking_crafting_table_unreachable")) {
            return "Cook cannot path to the crafting table inside the assigned work area.";
        }
        if (reason.contains("no_cooking_station")) {
            return "Cook needs a furnace or smoker inside the assigned work area.";
        }
        if (reason.contains("cooking_station_path_failed") || reason.contains("cooking_station_unreachable")) {
            return "Cook cannot path to the furnace or smoker inside the assigned work area.";
        }
        if (reason.contains("cooking_wrong_output")) {
            return "Cooking station output slot contains something that is not food.";
        }
        if (reason.contains("cooking_wrong_input")) {
            return "Cooking station input slot contains something the cook cannot cook into food.";
        }
        if (reason.contains("cooking_wrong_fuel")) {
            return "Cooking station fuel slot contains something that will not burn.";
        }
        if (inventoryFull) {
            return "Cook inventory is full and output storage cannot take more cooked food right now.";
        }
        return "";
    }

    private static String smeltingDiagnostic(
            HiredWorkerBrain.Snapshot brain,
            boolean inventoryFull,
            boolean noStorage,
            boolean missingMaterials,
            boolean materialStorageUnreachable,
            boolean materialInventoryFull) {
        String reason = lower(brain.failureReason());
        if (materialInventoryFull) {
            return "Smelter found raw ore or fuel in assigned storage, but the job inventory has no room for it.";
        }
        if (materialStorageUnreachable) {
            BlockPos storagePos = diagnosticStoragePos(brain);
            return limitDiagnostic(storagePos == null
                    ? "Smelter found raw ore or fuel in assigned input storage, but cannot path to that container."
                    : "Smelter found raw ore or fuel in assigned input storage at "
                            + HiredWorkerBrain.formatPos(storagePos) + ", but cannot path to it.");
        }
        if (missingMaterials) {
            return reason.contains("missing_smelting_fuel")
                    ? "Smelter needs furnace fuel in job supplies or assigned input storage."
                    : "Smelter needs raw iron, copper, or gold in job supplies or assigned input storage.";
        }
        if (noStorage && (reason.contains("missing_smelting_raw_ore") || reason.contains("missing_smelting_fuel"))) {
            return "Smelter needs assigned input storage or carried supplies for raw ore and fuel.";
        }
        if (reason.contains("no_smelting_station")) {
            return "Smelter needs a furnace or blast furnace inside the assigned work area.";
        }
        if (reason.contains("smelting_station_path_failed") || reason.contains("smelting_station_unreachable")) {
            return "Smelter cannot path to the furnace or blast furnace inside the assigned work area.";
        }
        if (reason.contains("smelting_wrong_output")) {
            return "Smelting station output slot contains something other than a supported metal ingot.";
        }
        if (reason.contains("smelting_wrong_input")) {
            return "Smelting station input slot must contain raw iron, copper, or gold.";
        }
        if (reason.contains("smelting_wrong_fuel")) {
            return "Smelting station fuel slot contains something that will not burn.";
        }
        if (inventoryFull) {
            return "Smelter inventory is full and output storage cannot take more ingots right now.";
        }
        return "";
    }

    private static String courierDiagnostic(
            HiredWorkerBrain.Snapshot brain,
            boolean inventoryFull,
            boolean noStorage) {
        String reason = lower(brain.failureReason());
        if (reason.contains("courier_missing_route")) {
            return "Courier needs an assigned route before deliveries can begin.";
        }
        if (reason.contains("courier_missing_input_storage")) {
            return "Courier needs at least one assigned input container.";
        }
        if (reason.contains("courier_missing_output_storage") || reason.contains("courier_output_unavailable")) {
            return "Courier needs at least one loaded assigned output container.";
        }
        if (reason.contains("courier_input_empty")) {
            return "Courier is waiting for items in assigned input storage.";
        }
        if (reason.contains("courier_input_unreachable")) {
            return "Courier cannot reach the selected input container.";
        }
        if (reason.contains("courier_output_unreachable")) {
            return "Courier cannot reach the selected output container.";
        }
        if (reason.contains("courier_route_unreachable")) {
            return "Courier cannot reach the next node on the assigned route.";
        }
        if (reason.contains("courier_output_full")) {
            return "Courier's assigned output storage is full.";
        }
        if (noStorage) {
            return "Courier needs assigned input and output storage.";
        }
        if (inventoryFull) {
            return "Courier inventory is full and the delivery cannot be deposited.";
        }
        return "";
    }

    private static String limitDiagnostic(String diagnostic) {
        if (diagnostic == null || diagnostic.length() <= 180) {
            return diagnostic == null ? "" : diagnostic;
        }
        return diagnostic.substring(0, 177) + "...";
    }

    private static String missingMaterialStorageWaitDiagnostic(HiredWorkerBrain.Snapshot brain) {
        BlockPos storagePos = diagnosticStoragePos(brain);
        if (storagePos == null) {
            return "The builder also cannot reach assigned storage to wait for delivery.";
        }
        return "The builder also cannot reach assigned storage at "
                + HiredWorkerBrain.formatPos(storagePos)
                + " to wait for delivery.";
    }

    private static BlockPos diagnosticStoragePos(HiredWorkerBrain.Snapshot brain) {
        return brain.storageTargetPos() == null ? brain.targetPos() : brain.storageTargetPos();
    }

    private static boolean isBuilderRestockingBatch(HiredWorkerBrain.Snapshot brain, CompoundTag state) {
        return BuilderTaskState.phase(state) == BuilderBuildPhase.COLLECTING_MATERIALS
                && (brain.taskState() == HiredWorkerTaskState.MOVING_TO_STORAGE
                || brain.taskState() == HiredWorkerTaskState.WAITING_FOR_MATERIALS);
    }

    private static String targetText(HiredWorkerBrain.Snapshot brain) {
        if (brain.targetPos() != null) {
            return HiredWorkerBrain.formatPos(brain.targetPos());
        }
        if (brain.storageTargetPos() != null) {
            return HiredWorkerBrain.formatPos(brain.storageTargetPos());
        }
        return "";
    }

    private static String workModeText(HiredVillagerRole role, net.minecraft.nbt.CompoundTag state) {
        return switch (role) {
            case MINING -> HiredMiningMode.fromState(state).label();
            case COMBAT -> HiredCombatMode.fromState(state).label();
            case HUNTING -> HiredHuntingTargets.selectionLabel(state);
            default -> "";
        };
    }

    private static String routeDescription(HiredRoute route) {
        int count = route == null ? 0 : route.nodes().size();
        return count + " node" + (count == 1 ? "" : "s") + (route != null && route.loop() ? ", loop" : ", back-and-forth");
    }

    private static void addWarning(Map<WarningKey, Integer> warnings, WarningType type, HiredVillagerRole role, boolean active) {
        if (active) {
            warnings.merge(new WarningKey(type, role), 1, Integer::sum);
        }
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(java.util.Locale.ROOT);
    }

    private static String toolLabelFromFailure(String reason, String fallback) {
        String safeReason = lower(reason);
        if (safeReason.contains("fishing_rod")) {
            return "fishing rod";
        }
        if (safeReason.contains("pickaxe")) {
            return "pickaxe";
        }
        if (safeReason.contains("shovel")) {
            return "shovel";
        }
        if (safeReason.contains("axe")) {
            return "axe";
        }
        if (safeReason.contains("hoe")) {
            return "hoe";
        }
        if (safeReason.contains("hunting_weapon")) {
            return "bow, crossbow, axe, or sword";
        }
        return fallback == null || fallback.isBlank() ? "tool" : fallback;
    }

    private record WarningKey(WarningType type, HiredVillagerRole role) {
    }
}
