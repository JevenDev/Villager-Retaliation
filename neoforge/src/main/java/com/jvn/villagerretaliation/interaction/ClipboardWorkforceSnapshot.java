package com.jvn.villagerretaliation.interaction;

import java.util.List;
import java.util.UUID;

public record ClipboardWorkforceSnapshot(
        int totalHired,
        int maxHired,
        int workingCount,
        int idleCount,
        int warningCount,
        int assignedStorageCount,
        int paymentContainerCount,
        int dailyWages,
        List<JobSummary> jobs,
        List<WorkerRow> workers,
        List<WarningSummary> warnings) {
    public ClipboardWorkforceSnapshot {
        jobs = List.copyOf(jobs);
        workers = List.copyOf(workers);
        warnings = List.copyOf(warnings);
    }

    public static ClipboardWorkforceSnapshot empty() {
        return new ClipboardWorkforceSnapshot(0, -1, 0, 0, 0, 0, 0, 0, List.of(), List.of(), List.of());
    }

    public record JobSummary(HiredVillagerRole role, int count) {
    }

    public record WorkerRow(
            UUID villagerId,
            String displayName,
            HiredVillagerRole role,
            WorkerStatus status,
            String target,
            String diagnostic,
            boolean storageAssigned,
            int storageCount,
            int workRadius,
            boolean hasWorkArea,
            String workAreaCenter,
            int horizontalRadius,
            int verticalRadius,
            String areaStatus,
            String workMode,
            int dailyWage,
            boolean inventoryFull,
            boolean unpaid,
            boolean noStorage,
            boolean noWorkArea,
            boolean noTargets,
            boolean tooFar,
            boolean missingTools) {
    }

    public record WarningSummary(WarningType type, HiredVillagerRole role, int count) {
    }

    public enum WorkerStatus {
        WORKING,
        PATHING,
        MINING,
        LOGGING,
        FARMING,
        BREWING,
        COOKING,
        SMELTING,
        BUILDING,
        DEPOSITING,
        WAITING,
        WAITING_FOR_CROPS,
        NO_WORK_AREA,
        NO_TARGETS,
        NO_STORAGE,
        STORAGE_FULL,
        INVENTORY_FULL,
        MISSING_TOOLS,
        UNPAID,
        TOO_FAR,
        MISSING_MATERIALS,
        MATERIAL_STORAGE_UNREACHABLE,
        MATERIAL_INVENTORY_FULL,
        BUILD_SITE_UNREACHABLE,
        UNKNOWN
    }

    public enum WarningType {
        NO_WORK_AREA,
        NO_STORAGE,
        STORAGE_FULL,
        INVENTORY_FULL,
        MISSING_TOOLS,
        UNPAID,
        NO_TARGETS,
        TOO_FAR,
        MISSING_MATERIALS,
        MATERIAL_STORAGE_UNREACHABLE,
        MATERIAL_INVENTORY_FULL,
        BUILD_SITE_UNREACHABLE
    }
}
