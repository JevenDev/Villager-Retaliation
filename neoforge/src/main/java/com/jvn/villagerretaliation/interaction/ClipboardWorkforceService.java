package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.interaction.ClipboardWorkforceSnapshot.WarningSummary;
import com.jvn.villagerretaliation.interaction.ClipboardWorkforceSnapshot.WarningType;
import com.jvn.villagerretaliation.interaction.ClipboardWorkforceSnapshot.WorkerRow;
import com.jvn.villagerretaliation.interaction.ClipboardWorkforceSnapshot.WorkerStatus;
import com.jvn.villagerretaliation.interaction.work.BrewingWorker;
import com.jvn.villagerretaliation.interaction.work.BuilderBuildPhase;
import com.jvn.villagerretaliation.interaction.work.BuilderTaskState;
import com.jvn.villagerretaliation.interaction.work.HiredWorkContext;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerBrain;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerTaskState;
import com.jvn.villagerretaliation.interaction.work.LoggingWorker;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.network.ClipboardWorkforceSyncPayload;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
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

        for (ServerLevel level : player.server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof Villager villager) || !villager.isAlive() || villager.isBaby()) {
                    continue;
                }
                if (!HiredVillagerContractService.isHiredBy(level, villager, player)) {
                    continue;
                }

                HiredWorkSession session = HiredWorkSession.active(level, villager);
                HiredVillagerRole role = session.role();
                HiredWorkerBrain.Snapshot brain = HiredWorkerBrain.snapshot(session.state(), level.getGameTime());
                int storageCount = AssignedStorageService.assignedStorage(level, villager).size();
                int paymentStorageCount = AssignedStorageService.assignedPaymentStorage(level, villager).size();
                boolean storageAssigned = storageCount > 0;
                boolean inventoryFull = brain.taskState() == HiredWorkerTaskState.PAUSED_FULL_INVENTORY
                        || !session.inventory().hasOutputSpace();
                boolean noStorage = brain.taskState() == HiredWorkerTaskState.PAUSED_NO_STORAGE || !storageAssigned;
                boolean noWorkArea = role != HiredVillagerRole.BUILDER
                        && (!session.context().hasWorkArea() || brain.taskState() == HiredWorkerTaskState.NO_WORK_AREA);
                boolean noTargets = !noWorkArea && isNoTargetState(brain);
                boolean tooFar = role != HiredVillagerRole.BUILDER
                        && !noWorkArea
                        && !session.context().isInsideWorkArea(villager.blockPosition());
                boolean missingTools = brain.taskState() == HiredWorkerTaskState.PAUSED_MISSING_TOOL;
                boolean materialStorageUnreachable = isMaterialStorageUnreachable(role, brain);
                boolean materialInventoryFull = isMaterialInventoryFull(role, brain);
                boolean buildSiteUnreachable = isBuilderBuildSiteUnreachable(role, brain, session.state());
                boolean missingMaterials = isMissingMaterials(role, brain, session.state(), noStorage, materialStorageUnreachable);
                int dailyWage = HiredVillagerContractService.getContractDailyCost(level, villager, player);
                boolean unpaid = HiredVillagerContractService.isAwaitingAutoPayment(level, villager);
                WorkerStatus status = status(
                        role,
                        brain.taskState(),
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
                        buildSiteUnreachable);
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
                if (isWorking(brain.taskState(), session.state().getBoolean("Enabled"))) {
                    working++;
                } else {
                    idle++;
                }
                addWarning(warningCounts, WarningType.NO_STORAGE, role, noStorage);
                addWarning(warningCounts, WarningType.INVENTORY_FULL, role, inventoryFull && !materialInventoryFull);
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
                        session.area().horizontalRadius(),
                        session.area().usable(),
                        session.area().centerDescription(),
                        session.area().horizontalRadius(),
                        session.area().verticalRadius(),
                        session.area().usable() ? "center" : "missing",
                        workModeText(role, session.state()),
                        dailyWage,
                        inventoryFull,
                        unpaid,
                        noStorage,
                        noWorkArea,
                        noTargets,
                        tooFar,
                        missingTools
                ));
            }
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
        return switch (state) {
            case IDLE, AWAITING_INSTRUCTION, NO_WORK_AREA, FAILED_COOLDOWN, WAITING_FOR_MATERIALS, PAUSED_FULL_INVENTORY, PAUSED_STORAGE_FULL, PAUSED_NO_STORAGE, PAUSED_MISSING_TOOL -> false;
            default -> true;
        };
    }

    private static WorkerStatus status(
            HiredVillagerRole role,
            HiredWorkerTaskState taskState,
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
            boolean buildSiteUnreachable) {
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
        if (noTargets) {
            return WorkerStatus.NO_TARGETS;
        }
        return switch (taskState) {
            case MOVING_TO_TARGET, RETURNING_TO_WORK_AREA -> WorkerStatus.PATHING;
            case SELECTING_TARGET, FINDING_CHAIN_TARGET, VALIDATING_TARGET -> activeWorkStatus(role);
            case MOVING_TO_STORAGE, DEPOSITING, PAUSED_STORAGE_FULL -> WorkerStatus.DEPOSITING;
            case WAITING_FOR_MATERIALS -> WorkerStatus.WAITING;
            case WORKING, COLLECTING_OUTPUT -> activeWorkStatus(role);
            case IDLE, AWAITING_INSTRUCTION, FAILED_COOLDOWN, PAUSED_MISSING_TOOL -> WorkerStatus.WAITING;
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
            case BUILDER -> WorkerStatus.BUILDING;
            default -> WorkerStatus.WORKING;
        };
    }

    private static boolean isNoTargetState(HiredWorkerBrain.Snapshot brain) {
        String failure = lower(brain.failureReason());
        String scan = lower(brain.lastTargetScanResult());
        return failure.contains("target_unreachable")
                || failure.contains("no_target")
                || scan.contains("no_reachable_targets")
                || scan.contains("no_targets");
    }

    private static boolean isMissingMaterials(
            HiredVillagerRole role,
            HiredWorkerBrain.Snapshot brain,
            CompoundTag state,
            boolean noStorage,
            boolean materialStorageUnreachable) {
        if (role == HiredVillagerRole.BREWING) {
            if (noStorage || materialStorageUnreachable) {
                return false;
            }
            String reason = lower(brain.failureReason() + " " + BrewingWorker.blockedReason(state));
            return reason.contains("missing_brewing_materials")
                    || reason.contains("interaction.work.brewing.missing_materials");
        }
        if (role != HiredVillagerRole.BUILDER || noStorage || materialStorageUnreachable) {
            return false;
        }
        return brain.taskState() == HiredWorkerTaskState.WAITING_FOR_MATERIALS
                && BuilderTaskState.phase(state) == BuilderBuildPhase.WAITING_FOR_MATERIALS
                || lower(brain.failureReason()).contains("missing_builder_materials");
    }

    private static boolean isMaterialStorageUnreachable(
            HiredVillagerRole role,
            HiredWorkerBrain.Snapshot brain) {
        String failure = lower(brain.failureReason());
        return switch (role) {
            case BREWING -> failure.contains("brewing_storage_path_failed");
            case BUILDER -> failure.contains("builder_material_storage_unreachable");
            default -> false;
        };
    }

    private static boolean isMaterialInventoryFull(
            HiredVillagerRole role,
            HiredWorkerBrain.Snapshot brain) {
        String failure = lower(brain.failureReason());
        return switch (role) {
            case BREWING -> failure.contains("brewing_material_inventory_full")
                    || failure.contains("brewing_water_bottle_space")
                    || failure.contains("brewing_output_full_after_brew");
            case BUILDER -> failure.contains("builder_material_inventory_full")
                    || failure.contains("builder_material_output_slot_full")
                    || failure.contains("builder_material_output_storage_unreachable");
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
        if (role == HiredVillagerRole.LOGGING) {
            return loggingDiagnostic(brain, context, inventoryFull, noStorage);
        }
        if (role != HiredVillagerRole.BUILDER) {
            return "";
        }

        String missing = BuilderTaskState.missingMaterials(state);
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

        String reason = lower(brain.failureReason() + " " + BuilderTaskState.blockedReason(state));
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
            return "Logger found a tool in storage, but its job inventory has no tool slot free.";
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
            default -> "";
        };
    }

    private static void addWarning(Map<WarningKey, Integer> warnings, WarningType type, HiredVillagerRole role, boolean active) {
        if (active) {
            warnings.merge(new WarningKey(type, role), 1, Integer::sum);
        }
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(java.util.Locale.ROOT);
    }

    private record WarningKey(WarningType type, HiredVillagerRole role) {
    }
}
