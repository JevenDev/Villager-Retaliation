package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.interaction.ClipboardWorkforceSnapshot.WarningSummary;
import com.jvn.villagerretaliation.interaction.ClipboardWorkforceSnapshot.WarningType;
import com.jvn.villagerretaliation.interaction.ClipboardWorkforceSnapshot.WorkerRow;
import com.jvn.villagerretaliation.interaction.ClipboardWorkforceSnapshot.WorkerStatus;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerBrain;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerTaskState;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.network.ClipboardWorkforceSyncPayload;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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
                boolean noWorkArea = !session.context().hasWorkArea() || brain.taskState() == HiredWorkerTaskState.NO_WORK_AREA;
                boolean noTargets = !noWorkArea && isNoTargetState(brain);
                boolean tooFar = !noWorkArea && !session.context().isInsideWorkArea(villager.blockPosition());
                int dailyWage = HiredVillagerContractService.getContractDailyCost(level, villager, player);
                boolean unpaid = false;
                WorkerStatus status = status(role, brain.taskState(), inventoryFull, noStorage, noWorkArea, noTargets, unpaid, tooFar);

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
                addWarning(warningCounts, WarningType.INVENTORY_FULL, role, inventoryFull);
                addWarning(warningCounts, WarningType.NO_WORK_AREA, role, noWorkArea);
                addWarning(warningCounts, WarningType.NO_TARGETS, role, noTargets);
                addWarning(warningCounts, WarningType.TOO_FAR, role, tooFar);
                workers.add(new WorkerRow(
                        villager.getUUID(),
                        VillagerPresetNameRegistry.resolveDisplayName(villager).getString(),
                        role,
                        status,
                        targetText(brain),
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
                        tooFar
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
            case IDLE, AWAITING_INSTRUCTION, NO_WORK_AREA, FAILED_COOLDOWN, PAUSED_FULL_INVENTORY, PAUSED_STORAGE_FULL, PAUSED_NO_STORAGE, PAUSED_MISSING_TOOL -> false;
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
            boolean tooFar) {
        if (unpaid) {
            return WorkerStatus.UNPAID;
        }
        if (noWorkArea) {
            return WorkerStatus.NO_WORK_AREA;
        }
        if (tooFar) {
            return WorkerStatus.TOO_FAR;
        }
        if (inventoryFull) {
            return WorkerStatus.INVENTORY_FULL;
        }
        if (noStorage) {
            return WorkerStatus.NO_STORAGE;
        }
        if (noTargets) {
            return WorkerStatus.NO_TARGETS;
        }
        return switch (taskState) {
            case MOVING_TO_TARGET, RETURNING_TO_WORK_AREA, SELECTING_TARGET, FINDING_CHAIN_TARGET, VALIDATING_TARGET -> WorkerStatus.PATHING;
            case MOVING_TO_STORAGE, DEPOSITING, PAUSED_STORAGE_FULL -> WorkerStatus.DEPOSITING;
            case WORKING, COLLECTING_OUTPUT -> switch (role) {
                case MINING -> WorkerStatus.MINING;
                case LOGGING -> WorkerStatus.LOGGING;
                case FARMING -> WorkerStatus.FARMING;
                case BREWING -> WorkerStatus.BREWING;
                default -> WorkerStatus.WORKING;
            };
            case IDLE, AWAITING_INSTRUCTION, FAILED_COOLDOWN, PAUSED_MISSING_TOOL -> WorkerStatus.WAITING;
            default -> WorkerStatus.UNKNOWN;
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
